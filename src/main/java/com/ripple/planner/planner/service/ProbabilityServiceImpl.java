package com.ripple.planner.planner.service;

import com.ripple.planner.planner.model.Grid;
import com.ripple.planner.planner.util.JtsGeometryUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.locationtech.jts.geom.Geometry;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 概率服务实现类。
 * <p>
 * 基于面积比例法计算每个网格的概率。
 * 公式：probability = Area(Grid ∩ Ripple) / Area(Ripple)
 * </p>
 * <p>
 * 实现要点：
 * 1. 使用 GeometryService 进行相交计算和面积计算，复用已有的空值安全和异常处理逻辑。
 * 2. 批量处理网格列表，逐个计算相交面积。
 * 3. 对边界情况进行防御性处理：rippleArea <= 0 时，所有概率设为 0。
 * 4. 计算结果直接写回 Grid.probability 字段。
 * </p>
 * <p>
 * 后续替换方向：
 * 1. 核密度估计（KDE）：在 Ripple 区域内根据距离中心点的远近赋予不同概率密度。
 * 2. 贝叶斯更新：结合历史搜索结果，动态更新各网格的后验概率。
 * 3. 蒙特卡洛模拟：通过大量随机粒子模拟目标运动，统计粒子落在各网格的比例。
 * 这些替换只需提供新的 ProbabilityService 实现类，无需修改 RippleTaskPlanner。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProbabilityServiceImpl implements ProbabilityService {

    /**
     * 几何服务，用于计算相交区域和面积。
     * <p>
     * 通过构造函数注入，复用 GeometryService 的空值安全和拓扑异常处理。
     * </p>
     */
    private final GeometryService geometryService;

    /**
     * 计算并更新每个网格的概率。
     * <p>
     * 实现步骤：
     * 1. 如果 gridList 为空，直接返回。
     * 2. 如果 rippleArea <= 0，将所有 Grid 的 probability 设为 0 并返回。
     *    这防止除零错误，并处理涟漪模型返回异常面积的情况。
     * 3. 遍历 gridList 中的每个 Grid：
     *    a. 计算 Grid.geometry 与 rippleGeometry 的相交区域。
     *    b. 计算相交区域的面积。
     *    c. probability = intersectionArea / rippleArea。
     *    d. 将 probability 赋值给 grid.setProbability()。
     * 4. 记录概率分布统计信息（最大概率、概率总和），用于调试和验证归一化。
     * </p>
     *
     * @param gridList       与 Ripple 相交的网格列表
     * @param rippleGeometry Ripple 区域的 JTS Geometry
     * @param rippleArea     Ripple 区域的总面积
     */
    @Override
    public void calculateProbabilities(List<Grid> gridList, Geometry rippleGeometry, double rippleArea) {
        if (gridList == null || gridList.isEmpty()) {
            log.debug("网格列表为空，跳过概率计算");
            return;
        }

        if (rippleArea <= 0) {
            log.warn("Ripple 面积小于等于零（{}），所有网格概率设为 0", rippleArea);
            for (Grid grid : gridList) {
                grid.setProbability(0.0);
            }
            return;
        }

        if (JtsGeometryUtil.isEmptyOrInvalid(rippleGeometry)) {
            log.warn("Ripple 几何对象为空或无效，所有网格概率设为 0");
            for (Grid grid : gridList) {
                grid.setProbability(0.0);
            }
            return;
        }

        // 使用并行流加速概率计算（每个 Grid 独立计算，无共享可变状态）
        gridList.parallelStream().forEach(grid -> {
            if (grid == null || JtsGeometryUtil.isEmptyOrInvalid(grid.getGeometry())) {
                log.warn("grid 为空：{}", grid);
                grid.setProbability(0.0);
                return;
            }

            // 计算 Grid 与 Ripple 的相交区域
            Geometry intersection = geometryService.intersect(grid.getGeometry(), rippleGeometry);
            // 计算相交面积
            double intersectionArea = geometryService.calculateArea(intersection);

            // 计算概率 = 相交面积 / Ripple 总面积
            double probability = intersectionArea / rippleArea;
            log.warn("相交面积占总面积：{}", probability);

            // 防御：概率理论上应在 [0, 1] 范围内，但浮点误差可能导致微小超出
            if (probability < 0.0) {
                probability = 0.0;
            } else if (probability > 1.0) {
                probability = 1.0;
            }

            grid.setProbability(probability);
        });

        // 顺序遍历收集统计信息（避免并行累加的精度/竞态问题）
        double maxProbability = 0.0;
        double totalProbability = 0.0;
        for (Grid grid : gridList) {
            double probability = grid.getProbability();
            totalProbability += probability;
            if (probability > maxProbability) {
                maxProbability = probability;
            }
        }

        log.debug("概率计算完成：网格数={}, 最大概率={}, 概率总和={}",
                gridList.size(), maxProbability, totalProbability);
    }

}
