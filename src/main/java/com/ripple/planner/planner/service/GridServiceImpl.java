package com.ripple.planner.planner.service;

import com.ripple.planner.planner.model.Grid;
import com.ripple.planner.planner.util.JtsGeometryUtil;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.locationtech.jts.geom.*;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 网格服务实现类。
 * <p>
 * 基于内存中的全局网格列表，提供空间查询能力。
 * 第一版采用简单的矩形网格生成策略，后续可替换为数据库或外部网格服务。
 * </p>
 * <p>
 * 网格初始化策略：
 * 1. 在 @PostConstruct 阶段生成全球矩形网格。
 * 2. 网格分辨率（GRID_RESOLUTION_DEGREES）默认为 1.0 度，即每个网格覆盖 1°×1° 的区域。
 *    全球共 360（经度）× 180（纬度）= 64,800 个网格。
 *    对于现代 JVM 来说，这个数量的几何对象内存占用在可接受范围内。
 * 3. 每个网格的 geometry 为矩形 Polygon，id 格式为 "G_{经度索引}_{纬度索引}"。
 * 4. 后续可接入配置文件，动态调整分辨率、覆盖范围和网格形状。
 * </p>
 * <p>
 * TODO（后续优化）：
 * 1. 引入空间索引（JTS STRtree 或 H3 索引），将 intersects 查询从 O(n) 优化到 O(log n)。
 * 2. 支持从数据库（PostGIS、MySQL Spatial）或文件加载网格，替代内存生成。
 * 3. 支持非矩形网格（如根据海岸线裁剪的自定义网格）。
 * 4. 网格分辨率配置化，通过 application.yml 动态注入。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GridServiceImpl implements GridService {

    /**
     * 网格分辨率，单位为度。
     * <p>
     * 默认 1.0 度，全球共 360 × 180 = 64,800 个网格。
     * 减小该值会显著增加网格数量和内存占用：
     * - 0.5 度 → 259,200 个网格
     * - 0.1 度 → 6,480,000 个网格
     * 第一版保持 1.0 度以平衡精度和性能。
     * </p>
     */
    private static final double GRID_RESOLUTION_DEGREES = 1.0;

    /**
     * 全球网格数据缓存。
     * <p>
     * 在 @PostConstruct 中初始化，规划过程中只读（Grid 对象本身除外，probability 会被更新）。
     * 使用 Collections.unmodifiableList 包装返回，防止外部误修改列表结构。
     * </p>
     */
    private List<Grid> globalGrids = new ArrayList<>();

    /**
     * JTS 几何工厂，用于创建网格的 Polygon 几何对象。
     */
    private final GeometryFactory geometryFactory;

    /**
     * 初始化全球网格。
     * <p>
     * 在 Spring Bean 构造完成后自动执行，生成覆盖全球的矩形网格集合。
     * 使用 @PostConstruct 确保 geometryFactory 已注入后再执行初始化。
     * </p>
     */
    @PostConstruct
    public void initGlobalGrids() {
        log.info("开始初始化全球网格，分辨率：{} 度", GRID_RESOLUTION_DEGREES);

        List<Grid> grids = new ArrayList<>();
        int lonSegments = (int) (360.0 / GRID_RESOLUTION_DEGREES);
        int latSegments = (int) (180.0 / GRID_RESOLUTION_DEGREES);

        for (int lonIdx = 0; lonIdx < lonSegments; lonIdx++) {
            double lonMin = -180.0 + lonIdx * GRID_RESOLUTION_DEGREES;
            double lonMax = lonMin + GRID_RESOLUTION_DEGREES;

            for (int latIdx = 0; latIdx < latSegments; latIdx++) {
                double latMin = -90.0 + latIdx * GRID_RESOLUTION_DEGREES;
                double latMax = latMin + GRID_RESOLUTION_DEGREES;

                // 构造网格矩形 Polygon：lb → rb → rt → lt → lb
                Coordinate[] coords = new Coordinate[5];
                coords[0] = new Coordinate(lonMin, latMin); // left bottom
                coords[1] = new Coordinate(lonMax, latMin); // right bottom
                coords[2] = new Coordinate(lonMax, latMax); // right top
                coords[3] = new Coordinate(lonMin, latMax); // left top
                coords[4] = new Coordinate(lonMin, latMin); // close ring

                Polygon polygon = geometryFactory.createPolygon(coords);

                String gridId = "G_" + lonIdx + "_" + latIdx;
                Grid grid = new Grid(gridId, polygon, 0.0);
                grids.add(grid);
            }
        }

        this.globalGrids = grids;
        log.info("全球网格初始化完成，共 {} 个网格", globalGrids.size());
    }

    /**
     * 获取与给定 Ripple 区域相交的所有网格。
     * <p>
     * 实现逻辑：
     * 1. 如果 rippleGeometry 为空，直接返回空列表。
     * 2. 遍历 globalGrids 中的每个 Grid。
     * 3. 使用 Geometry.intersects() 判断网格是否与 Ripple 区域相交。
     *    intersects 比 intersection 计算量更小，适合筛选阶段。
     * 4. 对相交的 Grid 创建新对象（浅拷贝 geometry 引用，重置 probability 为 0），
     *    加入结果列表。
     * </p>
     * <p>
     * 为什么创建新 Grid 对象：
     * ProbabilityService 会为返回的 Grid 设置 probability。
     * 如果直接返回 globalGrids 中的对象，会污染全局缓存中的 probability 值，
     * 导致下一轮规划循环使用错误的概率数据。
     * </p>
     *
     * @param rippleGeometry Ripple 区域的 JTS Geometry
     * @return 与 Ripple 相交的 Grid 列表
     */
    @Override
    public List<Grid> getGridsIntersectingRipple(Geometry rippleGeometry) {
        if (JtsGeometryUtil.isEmptyOrInvalid(rippleGeometry)) {
            log.warn("Ripple 几何区域为空，返回空网格列表");
            return Collections.emptyList();
        }

        // 使用并行流加速 intersects 判断（JTS Geometry 只读操作线程安全）
        List<Grid> intersectingGrids = globalGrids.parallelStream()
                .filter(grid -> grid != null && grid.getGeometry() != null)
                .filter(grid -> grid.getGeometry().intersects(rippleGeometry))
                .map(grid -> new Grid(grid.getId(), grid.getGeometry(), 0.0))
                .collect(Collectors.toList());

        log.debug("Ripple 区域相交网格数：{}/{}" , intersectingGrids.size(), globalGrids.size());
        return intersectingGrids;
    }

    /**
     * 获取系统中所有网格的列表。
     * <p>
     * 返回全局网格列表的不可修改视图，防止调用方增删网格，
     * 但 Grid 对象本身的字段仍可被修改（这是设计意图，ProbabilityService 需要设置 probability）。
     * </p>
     *
     * @return 全局网格列表的只读视图
     */
    @Override
    public List<Grid> getAllGrids() {
        return Collections.unmodifiableList(globalGrids);
    }

}
