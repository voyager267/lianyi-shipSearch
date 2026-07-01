package com.ripple.planner.planner.service;

import com.ripple.planner.model.Cata;
import com.ripple.planner.model.LianyiPoint;
import com.ripple.planner.model.LianyiResultNew;
import com.ripple.planner.model.TaskParam;
import com.ripple.planner.planner.util.JtsGeometryUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.locationtech.jts.geom.*;
import org.locationtech.jts.geom.util.GeometryFixer;
import org.locationtech.jts.precision.GeometryPrecisionReducer;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 几何服务实现类。
 * <p>
 * 基于 JTS Topology Suite 实现 GeometryService 接口，
 * 负责涟漪模型输出和任务参数的几何转换，以及基础几何运算。
 * </p>
 * <p>
 * 核心实现要点：
 * 1. Polygon With Holes 的正确构造：外环（exterior ring）+ 内环列表（interior rings）。
 *    JTS 的 Polygon 构造函数为：Polygon(LinearRing shell, LinearRing[] holes, GeometryFactory factory)
 * 2. MultiPolygon 的构造：当存在多个分离的 Polygon 时，使用 GeometryFactory.createMultiPolygon()。
 * 3. 坐标顺序：使用 (longitude, latitude) 对应 JTS 的 (x, y)。
 * 4. 空值安全：所有方法都进行 null 和空列表检查，避免 NPE。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GeometryServiceImpl implements GeometryService {

    /**
     * JTS 几何工厂，通过构造函数注入（由 JtsConfig 提供 Bean）。
     * <p>
     * 使用 @RequiredArgsConstructor 自动生成包含 final 字段的构造函数，
     * Spring 会自动注入 GeometryFactory Bean。
     * </p>
     */
    private final GeometryFactory geometryFactory;

    /**
     * 将涟漪模型计算结果列表转换为统一的 JTS Geometry。
     * <p>
     * 实现步骤：
     * 1. 如果输入为空，返回空 GeometryCollection。
     * 2. 遍历每个 LianyiResultNew：
     *    a. 将外轮廓点（lianyiPoints）转换为 Coordinate 列表，创建外环 LinearRing。
     *    b. 将每个洞（excludeGeos 中的 ToClientGeo.lianyiPoints）转换为内环 LinearRing。
     *    c. 使用外环 + 内环数组创建带洞的 Polygon。
     *    d. 如果外环创建失败（点太少），跳过该结果并记录警告。
     * 3. 收集所有有效的 Polygon。
     * 4. 如果只有一个有效 Polygon，直接返回。
     * 5. 如果有多个，返回 MultiPolygon。
     * 6. 如果全部无效，返回空 GeometryCollection。
     * </p>
     *
     * @param lianyiResults 涟漪模型计算结果列表
     * @return 统一的 JTS Geometry
     */
    @Override
    public Geometry convertRippleResultsToGeometry(List<LianyiResultNew> lianyiResults) {
        if (lianyiResults == null || lianyiResults.isEmpty()) {
            log.warn("涟漪模型结果列表为空，返回空几何对象");
            return geometryFactory.createGeometryCollection(null);
        }

        List<Polygon> polygons = new ArrayList<>();

        for (LianyiResultNew result : lianyiResults) {
            Polygon polygon = convertSingleRippleResult(result);
            if (polygon != null && !polygon.isEmpty()) {
                polygons.add(polygon);
            }
        }

        if (polygons.isEmpty()) {
            log.warn("所有涟漪模型结果均无法构造有效多边形，返回空几何对象");
            return geometryFactory.createGeometryCollection(null);
        }

        if (polygons.size() == 1) {
            return polygons.get(0);
        }

        // 多个 Polygon 合并为 MultiPolygon
        Polygon[] polygonArray = polygons.toArray(new Polygon[0]);
        return geometryFactory.createMultiPolygon(polygonArray);
    }

    /**
     * 将单个 LianyiResultNew 转换为带洞的 Polygon。
     * <p>
     * 辅助方法，抽取单条结果的转换逻辑，便于单元测试和代码复用。
     * </p>
     *
     * @param result 单个涟漪模型结果
     * @return 带洞的 Polygon，如果无法构造则返回 null
     */
    private Polygon convertSingleRippleResult(LianyiResultNew result) {
        if (result == null) {
            return null;
        }

        // 步骤 1：构造外环（exterior ring）
        LinearRing shell = convertPointsToLinearRing(result.getLianyiPoints());
        if (shell == null) {
            log.warn("涟漪结果外轮廓点数量不足，跳过该结果。area={}", result.getArea());
            return null;
        }

        // 步骤 2：构造内环列表（interior rings / holes）
        List<LinearRing> holes = new ArrayList<>();
        if (result.getExcludeGeos() != null) {
            for (var excludeGeo : result.getExcludeGeos()) {
                if (excludeGeo == null || excludeGeo.getLianyiPoints() == null) {
                    continue;
                }
                LinearRing hole = convertPointsToLinearRing(excludeGeo.getLianyiPoints());
                if (hole != null) {
                    holes.add(hole);
                }
            }
        }

        // 步骤 3：创建带洞的 Polygon
        LinearRing[] holeArray = holes.toArray(new LinearRing[0]);
        return geometryFactory.createPolygon(shell, holeArray);
    }

    /**
     * 将 LianyiPoint 列表转换为 JTS LinearRing。
     * <p>
     * 辅助方法：将业务模型中的点列表转换为 JTS 几何环。
     * 使用 JtsGeometryUtil.createLinearRing 处理闭合逻辑。
     * </p>
     *
     * @param points LianyiPoint 列表
     * @return 闭合的 LinearRing，如果点太少则返回 null
     */
    private LinearRing convertPointsToLinearRing(List<LianyiPoint> points) {
        if (points == null || points.size() < 3) {
            return null;
        }

        List<Coordinate> coordinates = new ArrayList<>(points.size());
        for (LianyiPoint point : points) {
            if (point == null) {
                continue;
            }
            // JTS 中 Coordinate 的构造为 (x, y)，对应 (经度, 纬度)
            coordinates.add(new Coordinate(point.getX(), point.getY()));
        }

        return JtsGeometryUtil.createLinearRing(geometryFactory, coordinates);
    }

    /**
     * 将任务参数转换为 JTS Geometry。
     * <p>
     * 实现步骤：
     * 1. 如果 TaskParam 或 catas 为空，返回空 GeometryCollection。
     * 2. 遍历每个 Cata，按 lb → rb → rt → lt 的顺序构造四边形 Polygon。
     *    顺序说明：
     *    - lb: left bottom (catalbLongitude, catalbLatitude)
     *    - rb: right bottom (catarbLongitude, catarbLatitude)
     *    - rt: right top (catartLongitude, catartLatitude)
     *    - lt: left top (cataltLongitude, cataltLatitude)
     *    这种顺序形成顺时针闭合环。
     * 3. 收集所有有效的 Polygon。
     * 4. 如果只有一个，返回 Polygon；多个返回 MultiPolygon；无有效结果返回空 GeometryCollection。
     * </p>
     *
     * @param taskParam 任务参数
     * @return 该任务覆盖区域的 JTS Geometry
     */
    @Override
    public Geometry convertTaskToGeometry(TaskParam taskParam) {
        if (taskParam == null || taskParam.getCatas() == null || taskParam.getCatas().isEmpty()) {
            log.warn("任务参数为空或覆盖区域为空，返回空几何对象。taskID={}",
                    taskParam != null ? taskParam.getTaskID() : "null");
            return geometryFactory.createGeometryCollection(null);
        }

        List<Polygon> polygons = new ArrayList<>();

        for (Cata cata : taskParam.getCatas()) {
            if (cata == null) {
                continue;
            }
            Polygon polygon = convertCataToPolygon(cata);
            if (polygon != null && !polygon.isEmpty()) {
                polygons.add(polygon);
            }
        }

        if (polygons.isEmpty()) {
            log.warn("任务的所有 Cata 均无法构造有效多边形。taskID={}", taskParam.getTaskID());
            return geometryFactory.createGeometryCollection(null);
        }

        if (polygons.size() == 1) {
            return polygons.get(0);
        }

        Polygon[] polygonArray = polygons.toArray(new Polygon[0]);
        return geometryFactory.createMultiPolygon(polygonArray);
    }

    /**
     * 将单个 Cata 转换为四边形 Polygon。
     * <p>
     * 辅助方法，按顺时针顺序组织四个角点：
     * lb (左下) → rb (右下) → rt (右上) → lt (左上)
     * </p>
     *
     * @param cata 四边形区域定义
     * @return 四边形 Polygon，如果角点缺失则返回 null
     */
    private Polygon convertCataToPolygon(Cata cata) {
        // 检查四个角点是否都存在（使用 Double 包装类型，允许 null）
        if (cata.getCatalbLongitude() == null || cata.getCatalbLatitude() == null
                || cata.getCatarbLongitude() == null || cata.getCatarbLatitude() == null
                || cata.getCatartLongitude() == null || cata.getCatartLatitude() == null
                || cata.getCataltLongitude() == null || cata.getCataltLatitude() == null) {
            log.warn("Cata 角点数据不完整，跳过该 Cata");
            return null;
        }

        List<Coordinate> coordinates = new ArrayList<>(5);

        // lb: left bottom (左下)
        coordinates.add(new Coordinate(cata.getCatalbLongitude(), cata.getCatalbLatitude()));
        // rb: right bottom (右下)
        coordinates.add(new Coordinate(cata.getCatarbLongitude(), cata.getCatarbLatitude()));
        // rt: right top (右上)
        coordinates.add(new Coordinate(cata.getCatartLongitude(), cata.getCatartLatitude()));
        // lt: left top (左上)
        coordinates.add(new Coordinate(cata.getCataltLongitude(), cata.getCataltLatitude()));

        LinearRing ring = JtsGeometryUtil.createLinearRing(geometryFactory, coordinates);
        if (ring == null) {
            return null;
        }

        return geometryFactory.createPolygon(ring, null);
    }

    /**
     * 计算两个几何图形的相交区域。
     * <p>
     * 空值安全封装：任一输入为 null 或空时，返回空 Polygon。
     * </p>
     *
     * @param g1 第一个几何图形
     * @param g2 第二个几何图形
     * @return 相交区域
     */
    @Override
    public Geometry intersect(Geometry g1, Geometry g2) {
        if (JtsGeometryUtil.isEmptyOrInvalid(g1) || JtsGeometryUtil.isEmptyOrInvalid(g2)) {
            return JtsGeometryUtil.createEmptyPolygon(geometryFactory);
        }
        try {
            // 第一层：直接相交计算（大多数情况可正常工作）
            return g1.intersection(g2);
        } catch (TopologyException e) {
            // JTS 在处理复杂或自相交多边形时可能抛出 TopologyException
            // 常见原因是坐标精度过高导致线段在非节点处相交（found non-noded intersection）
            log.warn("几何相交计算发生拓扑异常，尝试用 GeometryFixer 修复后重试。error={}", e.getMessage());
            try {
                // 第二层：GeometryFixer 拓扑修复后重试
                Geometry fixedG1 = GeometryFixer.fix(g1);
                Geometry fixedG2 = GeometryFixer.fix(g2);
                return fixedG1.intersection(fixedG2);
            } catch (TopologyException e2) {
                log.warn("GeometryFixer 修复后仍发生拓扑异常，尝试用 GeometryPrecisionReducer 截断精度后重试。error={}", e2.getMessage());
                try {
                    // 第三层：截断坐标精度后重试（保留 7 位小数，约 1 厘米级精度）
                    PrecisionModel pm = new PrecisionModel(1e7);
                    GeometryPrecisionReducer reducer = new GeometryPrecisionReducer(pm);
                    Geometry reducedG1 = reducer.reduce(g1);
                    Geometry reducedG2 = reducer.reduce(g2);
                    return reducedG1.intersection(reducedG2);
                } catch (TopologyException e3) {
                    log.error("精度截断后仍无法计算相交，返回空几何。error={}", e3.getMessage());
                    return JtsGeometryUtil.createEmptyPolygon(geometryFactory);
                }
            }
        }
    }

    /**
     * 计算几何图形的面积。
     * <p>
     * 空值安全封装：输入为 null 或空时返回 0.0。
     * </p>
     *
     * @param geometry 几何图形
     * @return 面积
     */
    @Override
    public double calculateArea(Geometry geometry) {
        if (JtsGeometryUtil.isEmptyOrInvalid(geometry)) {
            return 0.0;
        }
        return geometry.getArea();
    }

    /**
     * 判断两个几何图形是否相交。
     * <p>
     * 空值安全封装：任一输入为 null 或空时返回 false。
     * </p>
     *
     * @param g1 第一个几何图形
     * @param g2 第二个几何图形
     * @return 是否相交
     */
    @Override
    public boolean isIntersect(Geometry g1, Geometry g2) {
        if (JtsGeometryUtil.isEmptyOrInvalid(g1) || JtsGeometryUtil.isEmptyOrInvalid(g2)) {
            return false;
        }
        return g1.intersects(g2);
    }

}
