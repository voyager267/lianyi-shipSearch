package com.ripple.planner.planner.service;

import com.ripple.planner.planner.model.TaskCandidate;
import com.ripple.planner.planner.util.JtsGeometryUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 任务评分服务实现类。
 * <p>
 * 基于第一版评分公式实现：
 *     Score = Probability × EffectiveCoverageArea × TimeWeight
 * </p>
 * <p>
 * 实现要点：
 * 1. Probability 直接从 candidate.getGrid().getProbability() 获取。
 *    ProbabilityService 必须在此方法调用之前完成计算。
 * 2. EffectiveCoverageArea 从 candidate.getCoverage().getArea() 获取。
 *    AccessService 在创建 TaskCandidate 时已计算 coverage。
 * 3. TimeWeight 基于当前时间与任务访问时间的差值（等待时间）计算。
 *    公式：timeWeight = 1.0 / (1.0 + waitSeconds)
 *    等待时间越短，TimeWeight 越接近 1；等待时间越长，TimeWeight 趋近于 0。
 * 4. 如果任一因子为 0（如 probability=0 或 coverageArea=0），最终 score 为 0。
 * 5. 如果任务不可达（ReachabilityService 返回 false），score 设为 0。
 * </p>
 * <p>
 * 设计说明：
 * 1. 使用 double 进行浮点计算，注意精度问题。对于大面积区域，area 可能很大，
 *    score 可能超出 double 的精确表示范围，但卫星搜索任务的 area 通常在可接受范围内。
 * 2. score 不强制归一化到 [0, 1]，允许大于 1，因为 EffectiveCoverageArea 可能很大。
 *    RippleTaskPlanner 只关心 score 的相对大小，不关心绝对值。
 * 3. 所有因子都是非负的，因此 score 也是非负的。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TaskScoreServiceImpl implements TaskScoreService {

    /**
     * 可达性服务，用于判断任务是否可行。
     * <p>
     * 如果任务不可达（isReachable 返回 false），直接将 score 设为 0。
     * </p>
     */
    private final ReachabilityService reachabilityService;

    /**
     * 为单个候选任务计算评分。
     * <p>
     * 实现步骤：
     * 1. 参数校验：如果 candidate 为 null 或 grid/coverage 为空，score 设为 0 并返回。
     * 2. 判断可达性：调用 reachabilityService.isReachable()。
     *    如果不可达，score = 0，直接返回。
     * 3. 提取 Probability：probability = grid.getProbability()。
     * 4. 提取 EffectiveCoverageArea：area = coverage.getArea()。
     *    使用 JtsGeometryUtil 进行空值安全检查。
     * 5. 计算 TimeWeight：
     *    - 如果 accessTime 在 currentTime 之前（或相等），等待时间为 0。
     *    - 否则，等待时间 = Duration.between(currentTime, accessTime).getSeconds()。
     *    - timeWeight = 1.0 / (1.0 + waitSeconds)。
     * 6. 计算 Score：score = probability × area × timeWeight。
     * 7. 将 score 写回 candidate.setScore()。
     * </p>
     *
     * @param candidate   候选任务
     * @param currentTime 当前规划时间
     */
    @Override
    public void scoreTask(TaskCandidate candidate, LocalDateTime currentTime) {
        if (candidate == null) {
            return;
        }

        // 步骤 1：参数校验
        if (candidate.getGrid() == null || JtsGeometryUtil.isEmptyOrInvalid(candidate.getCoverage())) {
            candidate.setScore(0.0);
            return;
        }

        // 步骤 2：可达性判断
        // 注意：currentTime 和 accessTime 可能为 null，需要防御
        LocalDateTime accessTime = candidate.getAccessTime();
        boolean reachable = reachabilityService.isReachable(
                candidate.getGrid(),
                currentTime,
                accessTime,
                0.0 // targetSpeed 当前版本在 ReachabilityService 中未使用
        );
        if (!reachable) {
            candidate.setScore(0.0);
            return;
        }

        // 步骤 3：提取 Probability
        double probability = candidate.getGrid().getProbability();
        if (probability <= 0.0) {
            candidate.setScore(0.0);
            return;
        }

        // 步骤 4：提取 EffectiveCoverageArea
        double effectiveCoverageArea = candidate.getCoverage().getArea();
        if (effectiveCoverageArea <= 0.0) {
            candidate.setScore(0.0);
            return;
        }

        // 步骤 5：计算 TimeWeight
        double timeWeight = calculateTimeWeight(currentTime, accessTime);
        if (timeWeight <= 0.0) {
            candidate.setScore(0.0);
            return;
        }

        // 步骤 6：计算综合评分
        double score = probability * effectiveCoverageArea * timeWeight;

        // 防御：由于浮点计算，score 理论上非负，但做一层保护
        if (score < 0.0 || Double.isNaN(score) || Double.isInfinite(score)) {
            log.warn("候选任务 {} 计算出异常评分：{}，强制设为 0",
                    candidate.getTask() != null ? candidate.getTask().getTaskID() : "unknown", score);
            score = 0.0;
        }

        // 步骤 7：写回评分
        candidate.setScore(score);
    }

    /**
     * 计算时间权重。
     * <p>
     * 公式：timeWeight = 1.0 / (1.0 + waitSeconds)
     * </p>
     * <p>
     * 边界情况：
     * - 如果 accessTime 为 null，视为立即执行（等待时间 0），返回 1.0。
     * - 如果 accessTime 在 currentTime 之前（计划时间已过去），视为等待时间 0，返回 1.0。
     *   这是简化处理，后续可以引入惩罚机制（过期任务降低权重）。
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

        // 计算等待时间（秒）
        Duration waitDuration = Duration.between(currentTime, accessTime);
        long waitSeconds = waitDuration.getSeconds();

        // 防止等待时间为负数（虽然前面已判断，但做双重保护）
        if (waitSeconds < 0) {
            waitSeconds = 0;
        }

        return 1.0 / (1.0 + waitSeconds);
    }

    /**
     * 批量为候选任务列表计算评分。
     * <p>
     * 遍历 candidates 列表，逐个调用 scoreTask 方法。
     * 当前版本使用顺序遍历，后续可以优化为并行流（parallelStream）以提升性能。
     * </p>
     *
     * @param candidates  候选任务列表
     * @param currentTime 当前规划时间
     */
    @Override
    public void scoreTasks(List<TaskCandidate> candidates, LocalDateTime currentTime) {
        if (candidates == null || candidates.isEmpty()) {
            return;
        }

        for (TaskCandidate candidate : candidates) {
            scoreTask(candidate, currentTime);
        }

        // 记录评分统计信息，便于调试
        long positiveCount = candidates.stream().filter(c -> c.getScore() > 0).count();
        log.debug("评分完成：候选总数={}, 正分候选数={}", candidates.size(), positiveCount);
    }

}
