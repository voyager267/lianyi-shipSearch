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
 *     Score = (Area(Ripple ∩ Coverage) / Area(Ripple)) × TimeWeight × FragmentationPenalty
 * </p>
 * <p>
 * 与第一版的区别：
 * 1. 取消 Probability 因子（不再取 grids 的 probability 平均值）。
 * 2. 取消 EffectiveCoverageArea 因子（不再直接用 coverage.getArea()）。
 * 3. 改为计算"涟漪区域与任务覆盖区域交集的面积占涟漪面积的比例"。
 *    这个比例直接反映任务对当前搜索热点区域的覆盖效率——
 *    任务覆盖面积再大，如果不在涟漪区域内，评分也为 0；
 *    反之，任务即使覆盖面积很小，只要精确命中涟漪区域的核心，也能获得高分。
 * 4. 新增碎片化惩罚（FragmentationPenalty）：通过 JTS difference 运算预估
 *    加入任务后涟漪区域是否会分裂成更多独立区域。
 *    如果任务会把一个涟漪区域"切"成多个独立区域，评分将降低。
 * </p>
 * <p>
 * 实现要点：
 * 1. 使用 GeometryService 进行相交计算和面积计算。
 * 2. 使用 JTS difference 运算评估碎片化影响。
 * 3. 批量处理访问任务列表，逐个计算评分。
 * 4. 对边界情况进行防御性处理：ripple/coverage 为空、ratio 为 0 时返回 0。
 * 5. 计算结果通过返回值传递，不修改 AccessTask 对象本身。
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
     */
    private final GeometryService geometryService;

    /**
     * 碎片化惩罚权重 α。
     * <p>
     * 公式：FragmentationPenalty = 1.0 / (1.0 + α × max(0, N_after - N_before))
     * 其中 N_before 是任务前涟漪区域数量，N_after 是预估的任务后区域数量。
     * α 越大，碎片化惩罚越重。
     * </p>
     * <p>
     * 例：α=0.5 时，多分裂出 1 个区域 → penalty=0.67；多分裂出 2 个 → penalty=0.5。
     * </p>
     */
    private static final double FRAGMENTATION_ALPHA = 0.5;

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
     * 5. 计算碎片化惩罚（FragmentationPenalty）：
     *    - 使用 JTS difference 运算：remainder = rippleGeometry.difference(coverage)
     *    - 比较 N_before（rippleGeometry 区域数）和 N_after（remainder 区域数）
     *    - 如果区域数增加，说明任务会"切碎"涟漪，施加惩罚
     *    - penalty = 1.0 / (1.0 + α × max(0, N_after - N_before))
     * 6. 计算 TimeWeight：timeWeight = 1.0 / (1.0 + waitHours)
     * 7. 计算 Score：score = ratio × fragmentationPenalty × timeWeight
     * 8. 返回 score。
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
        if (accessTask.getGrids() != null && !accessTask.getGrids().isEmpty()) {
            Grid representativeGrid = accessTask.getGrids().get(0);
            boolean reachable = reachabilityService.isReachable(
                    representativeGrid,
                    currentTime,
                    accessTask.getAccessTime(),
                    0.0
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

        // 防御：ratio 理论上在 [0, 1] 范围内
        if (ratio < 0.0) {
            ratio = 0.0;
        } else if (ratio > 1.0) {
            ratio = 1.0;
        }

        // 步骤 5：计算碎片化惩罚
        double fragmentationPenalty = calculateFragmentationPenalty(rippleGeometry, accessTask.getCoverage());

        // 步骤 6：计算 TimeWeight
        double timeWeight = calculateTimeWeight(currentTime, accessTask.getAccessTime());
        if (timeWeight <= 0.0) {
            log.debug("访问任务 {} 的时间权重为 0，评分为 0", accessTask.getAccessId());
            return 0.0;
        }

        // 步骤 7：计算综合评分
        double score = ratio * fragmentationPenalty * timeWeight;

        // 防御：处理异常浮点值
        if (score < 0.0 || Double.isNaN(score) || Double.isInfinite(score)) {
            log.warn("访问任务 {} 计算出异常评分：{}，强制设为 0",
                    accessTask.getAccessId(), score);
            score = 0.0;
        }

        log.debug("访问任务 {} 评分详情：rippleArea={}, intersectionArea={}, ratio={}, " +
                        "fragmentationPenalty={}, timeWeight={}, score={}",
                accessTask.getAccessId(), rippleArea, intersectionArea, ratio,
                fragmentationPenalty, timeWeight, score);

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
     * 计算碎片化惩罚因子。
     * <p>
     * 通过 JTS difference 运算预估：如果从涟漪区域中"挖掉"任务覆盖区域后，
     * 剩余区域是否分裂成更多的独立区域。
     * </p>
     * <p>
     * 公式：FragmentationPenalty = 1.0 / (1.0 + α × max(0, N_after - N_before))
     * </p>
     * <p>
     * 原理：
     * - 如果任务覆盖区域完全包含在一个涟漪区域内部，difference 后区域数不变，
     *   惩罚因子 = 1.0（不惩罚）。
     * - 如果任务覆盖区域横跨涟漪区域的"瓶颈"位置，difference 后可能把一个区域
     *   切成两半或多块，N_after > N_before，惩罚因子 < 1.0。
     * - 分裂出的区域越多，惩罚越重。
     * </p>
     * <p>
     * 设计考量：
     * 1. 使用 JTS difference 而非调用涟漪模型，性能开销可控。
     * 2. 这是"预估"而非精确计算——实际上涟漪模型在加入 historyTask 后的输出
     *    可能与 difference 结果不完全一致，但 difference 能有效识别"切割"效应。
     * 3. 异常情况下返回 1.0（不惩罚），不阻塞评分流程。
     * </p>
     *
     * @param rippleGeometry 当前涟漪区域 JTS Geometry
     * @param coverage       任务覆盖区域 JTS Geometry
     * @return 碎片化惩罚因子，范围 (0, 1]
     */
    private double calculateFragmentationPenalty(Geometry rippleGeometry, Geometry coverage) {
        int beforeCount = rippleGeometry.getNumGeometries();

        try {
            Geometry remainder = rippleGeometry.difference(coverage);
            if (JtsGeometryUtil.isEmptyOrInvalid(remainder)) {
                // 任务完全覆盖了涟漪区域，这是好事（搜索完成），不惩罚
                return 1.0;
            }

            int afterCount = remainder.getNumGeometries();
            int newRegions = afterCount - beforeCount;

            if (newRegions <= 0) {
                // 没有产生新的独立区域，不惩罚
                return 1.0;
            }

            // 有碎片化：惩罚公式 1 / (1 + α × 新增区域数)
            double penalty = 1.0 / (1.0 + FRAGMENTATION_ALPHA * newRegions);

            log.debug("碎片化分析：beforeCount={}, afterCount={}, newRegions={}, penalty={}",
                    beforeCount, afterCount, newRegions, penalty);

            return penalty;
        } catch (Exception e) {
            // difference 运算异常时，不惩罚（不阻塞评分流程）
            log.warn("计算碎片化惩罚时发生异常，默认不惩罚。error={}", e.getMessage());
            return 1.0;
        }
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