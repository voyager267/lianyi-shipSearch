package com.ripple.planner.planner.service;

import com.ripple.planner.planner.model.AccessTask;
import com.ripple.planner.planner.model.Grid;
import com.ripple.planner.planner.util.JtsGeometryUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.locationtech.jts.geom.Geometry;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 任务评分服务实现类（第二版）。
 * <p>
 * 基于第二版评分公式实现：
 *     Score = (Area(Ripple ∩ Coverage) / Area(Ripple)) × TimeWeight
 * </p>
 * <p>
 * 与第一版的区别：
 * 1. 取消 Probability 因子（不再取 grids 的 probability 平均值）。
 * 2. 取消 EffectiveCoverageArea 因子（不再直接用 coverage.getArea()）。
 * 3. 改为计算"涟漪区域与任务覆盖区域交集的面积占涟漪面积的比例"。
 *    这个比例直接反映任务对当前搜索热点区域的覆盖效率——
 *    任务覆盖面积再大，如果不在涟漪区域内，评分也为 0；
 *    反之，任务即使覆盖面积很小，只要精确命中涟漪区域的核心，也能获得高分。
 * </p>
 * <p>
 * 实现要点：
 * 1. 使用 GeometryService 进行相交计算和面积计算。
 * 2. 批量处理访问任务列表，逐个计算评分。
 * 3. 对边界情况进行防御性处理：ripple/covarge 为空、ratio 为 0 时返回 0。
 * 4. 计算结果通过返回值传递，不修改 AccessTask 对象本身。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TaskScoreServiceImpl implements TaskScoreService {

    /**
     * 可达性服务，用于判断任务是否可行。
     */
    private final ReachabilityService reachabilityService;

    /**
     * 几何服务，用于计算涟漪区域与任务覆盖区域的交集及面积。
     * <p>
     * 新增依赖：第二版评分需要计算 rippleGeometry ∩ coverage 的面积占比。
     * </p>
     */
    private final GeometryService geometryService;

    /**
     * 为单个访问任务计算评分。
     * <p>
     * 实现步骤：
     * 1. 参数校验：如果 accessTask 为 null 或 coverage 为空，返回 0。
     * 2. 涟漪区域校验：如果 rippleGeometry 为空或 rippleArea <= 0，返回 0。
     * 3. 可达性判断：调用 reachabilityService.isReachable()。
     * 4. 计算交集面积占比：
     *    - intersection = geometryService.intersect(rippleGeometry, coverage)
     *    - intersectionArea = geometryService.calculateArea(intersection)
     *    - ratio = intersectionArea / rippleArea（clamped to [0, 1]）
     * 5. 计算 TimeWeight：timeWeight = 1.0 / (1.0 + waitHours)
     * 6. 计算 Score：score = ratio × timeWeight
     * 7. 返回 score。
     * </p>
     *
     * @param accessTask     访问任务
     * @param currentTime    当前规划时间
     * @param rippleGeometry 当前轮次的涟漪区域 JTS Geometry
     * @param rippleArea     当前轮次的涟漪区域总面积
     * @return 综合评分，<= 0 表示不可行
     */
    @Override
    public double scoreTask(AccessTask accessTask, LocalDateTime currentTime,
                            Geometry rippleGeometry, double rippleArea) {
        if (accessTask == null) {
            return 0.0;
        }

        // 步骤 1：参数校验 — coverage
        if (JtsGeometryUtil.isEmptyOrInvalid(accessTask.getCoverage())) {
            log.debug("访问任务 {} 的 coverage 为空，评分为 0", accessTask.getAccessId());
            return 0.0;
        }

        // 步骤 2：涟漪区域校验
        if (JtsGeometryUtil.isEmptyOrInvalid(rippleGeometry)) {
            log.debug("涟漪区域为空，评分为 0");
            return 0.0;
        }
        if (rippleArea <= 0.0) {
            log.debug("涟漪区域面积 <= 0（{}），评分为 0", rippleArea);
            return 0.0;
        }

        // 步骤 3：可达性判断
        // 使用 AccessTask 覆盖的第一个 Grid 进行可达性判断；如果 grids 为空则跳过此检查
        if (accessTask.getGrids() != null && !accessTask.getGrids().isEmpty()) {
            Grid representativeGrid = accessTask.getGrids().get(0);
            boolean reachable = reachabilityService.isReachable(
                    representativeGrid,
                    currentTime,
                    accessTask.getAccessTime(),
                    0.0 // targetSpeed 当前版本在 ReachabilityService 中未使用
            );
            if (!reachable) {
                log.debug("访问任务 {} 不可达，评分为 0", accessTask.getAccessId());
                return 0.0;
            }
        }

        // 步骤 4：计算交集面积占比（替代原来的 probability 和 effectiveCoverageArea）
        // ratio = Area(Ripple ∩ Coverage) / Area(Ripple)
        Geometry intersection = geometryService.intersect(rippleGeometry, accessTask.getCoverage());
        double intersectionArea = geometryService.calculateArea(intersection);

        if (intersectionArea <= 0.0) {
            log.debug("访问任务 {} 与涟漪区域无交集（intersectionArea={}），评分为 0",
                    accessTask.getAccessId(), intersectionArea);
            return 0.0;
        }

        double ratio = intersectionArea / rippleArea;

        // 防御：ratio 理论上在 [0, 1] 范围内，但浮点误差可能导致微小超出
        if (ratio < 0.0) {
            ratio = 0.0;
        } else if (ratio > 1.0) {
            ratio = 1.0;
        }

        // 步骤 5：计算 TimeWeight
        double timeWeight = calculateTimeWeight(currentTime, accessTask.getAccessTime());
        if (timeWeight <= 0.0) {
            log.debug("访问任务 {} 的时间权重为 0，评分为 0", accessTask.getAccessId());
            return 0.0;
        }

        // 步骤 6：计算综合评分
        double score = ratio * timeWeight;

        // 防御：处理异常浮点值
        if (score < 0.0 || Double.isNaN(score) || Double.isInfinite(score)) {
            log.warn("访问任务 {} 计算出异常评分：{}，强制设为 0",
                    accessTask.getAccessId(), score);
            score = 0.0;
        }

        log.debug("访问任务 {} 评分详情：rippleArea={}, intersectionArea={}, ratio={}, timeWeight={}, score={}",
                accessTask.getAccessId(), rippleArea, intersectionArea, ratio, timeWeight, score);

        return score;
    }

    /**
     * 计算时间权重。
     * <p>
     * 公式：timeWeight = 1.0 / (1.0 + waitHours)
     * </p>
     * <p>
     * 边界情况：
     * - 如果 accessTime 为 null，视为立即执行（等待时间 0），返回 1.0。
     * - 如果 accessTime 在 currentTime 之前（计划时间已过去），视为等待时间 0，返回 1.0。
     * - 如果 currentTime 为 null，视为等待时间 0，返回 1.0。
     * </p>
     *
     * @param currentTime 当前规划时间
     * @param accessTime  任务访问/执行时间
     * @return 时间权重，范围 (0, 1]
     */
    private double calculateTimeWeight(LocalDateTime currentTime, LocalDateTime accessTime) {
        if (currentTime == null || accessTime == null) {
            return 1.0;
        }

        if (!accessTime.isAfter(currentTime)) {
            // 任务时间已到达或已过去，等待时间为 0
            return 1.0;
        }

        // 计算等待时间（小时），避免以秒为单位导致权重过极小
        Duration waitDuration = Duration.between(currentTime, accessTime);
        long waitHours = waitDuration.toHours();

        // 防止等待时间为负数（虽然前面已判断，但做双重保护）
        if (waitHours < 0) {
            waitHours = 0;
        }

        return 1.0 / (1.0 + waitHours);
    }

    /**
     * 批量为访问任务列表计算评分。
     * <p>
     * 遍历 accessTasks 列表，逐个调用 scoreTask 方法。
     * 返回与输入列表一一对应的评分结果列表。
     * 最后应用 Min-Max 归一化，让最高分为 1.0，其余按比例缩放。
     * </p>
     *
     * @param accessTasks    访问任务列表
     * @param currentTime    当前规划时间
     * @param rippleGeometry 当前轮次的涟漪区域 JTS Geometry
     * @param rippleArea     当前轮次的涟漪区域总面积
     * @return 评分结果列表，与输入列表一一对应（index 相同）
     */
    @Override
    public List<Double> scoreTasks(List<AccessTask> accessTasks, LocalDateTime currentTime,
                                   Geometry rippleGeometry, double rippleArea) {
        if (accessTasks == null || accessTasks.isEmpty()) {
            return new ArrayList<>();
        }

        List<Double> scores = new ArrayList<>(accessTasks.size());
        int positiveCount = 0;

        for (AccessTask accessTask : accessTasks) {
            double score = scoreTask(accessTask, currentTime, rippleGeometry, rippleArea);
            scores.add(score);
            if (score > 0) {
                positiveCount++;
            }
        }

        // 标准化：Min-Max 归一化，让最高分为 1.0，其余按比例缩放
        double maxScore = scores.stream().mapToDouble(Double::doubleValue).max().orElse(0.0);
        if (maxScore > 0) {
            for (int i = 0; i < scores.size(); i++) {
                scores.set(i, scores.get(i) / maxScore);
            }
            log.debug("评分标准化完成：原始最高分={}, 标准化后范围 [0.0, 1.0]", maxScore);
        }

        log.debug("评分完成：访问任务总数={}, 正分任务数={}", accessTasks.size(), positiveCount);
        return scores;
    }

}