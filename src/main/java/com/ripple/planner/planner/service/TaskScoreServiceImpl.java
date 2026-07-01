package com.ripple.planner.planner.service;

import com.ripple.planner.planner.model.AccessTask;
import com.ripple.planner.planner.model.Grid;
import com.ripple.planner.planner.util.JtsGeometryUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 任务评分服务实现类。
 * <p>
 * 基于第一版评分公式实现：
 *     Score = Probability × EffectiveCoverageArea × TimeWeight
 * </p>
 * <p>
 * 与旧版的适配说明：
 * 1. 输入从 TaskCandidate 改为 AccessTask。
 * 2. Probability 从 AccessTask.grids 中各 Grid 的 probability 计算得出（取平均值）。
 * 3. EffectiveCoverageArea 从 AccessTask.coverage 获取。
 * 4. TimeWeight 基于 AccessTask.accessTime 和 currentTime 计算。
 * 5. 评分框架本身未变，保持 Probability × Area × Time 的结构。
 * </p>
 * <p>
 * 实现要点：
 * 1. 使用 GeometryService 进行相交计算和面积计算（当前版本 coverage 已由 AccessService 计算好，直接使用 getArea）。
 * 2. 批量处理访问任务列表，逐个计算评分。
 * 3. 对边界情况进行防御性处理：coverage 为空、grids 为空、probability 为 0 时返回 0。
 * 4. 计算结果通过返回值传递，不修改 AccessTask 对象本身。
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
     * 为单个访问任务计算评分。
     * <p>
     * 实现步骤：
     * 1. 参数校验：如果 accessTask 为 null 或 coverage/grids 为空，返回 0。
     * 2. 判断可达性：调用 reachabilityService.isReachable()。
     *    如果不可达，返回 0。
     * 3. 提取 Probability：取 grids 中各 Grid 的 probability 平均值。
     *    如果一个 AccessTask 覆盖多个 Grid，平均概率能较好反映整体覆盖价值。
     * 4. 提取 EffectiveCoverageArea：coverage.getArea()。
     *    使用 JtsGeometryUtil 进行空值安全检查。
     * 5. 计算 TimeWeight：
     *    - 如果 accessTime 在 currentTime 之前（或相等），等待时间为 0。
     *    - 否则，等待时间 = Duration.between(currentTime, accessTime).getSeconds()。
     *    - timeWeight = 1.0 / (1.0 + waitSeconds)。
     * 6. 计算 Score：score = probability × area × timeWeight。
     * 7. 返回 score。
     * </p>
     *
     * @param accessTask  访问任务
     * @param currentTime 当前规划时间
     * @return 综合评分，<= 0 表示不可行
     */
    @Override
    public double scoreTask(AccessTask accessTask, LocalDateTime currentTime) {
        if (accessTask == null) {
            return 0.0;
        }

        // 步骤 1：参数校验
        if (JtsGeometryUtil.isEmptyOrInvalid(accessTask.getCoverage())) {
            log.debug("访问任务 {} 的 coverage 为空，评分为 0", accessTask.getAccessId());
            return 0.0;
        }
        if (accessTask.getGrids() == null || accessTask.getGrids().isEmpty()) {
            log.debug("访问任务 {} 的 grids 为空，评分为 0", accessTask.getAccessId());
            return 0.0;
        }

        // 步骤 2：可达性判断
        // 使用 AccessTask 覆盖的第一个 Grid 和 accessTime 进行可达性判断
        // TODO: 后续可达性判断可以基于 AccessTask 的整体 coverage 而非单个 Grid
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

        // 步骤 3：提取 Probability（取 grids 中各 Grid 的 probability 平均值）
        double probability = accessTask.getGrids().stream()
                .filter(g -> g != null)
                .mapToDouble(Grid::getProbability)
                .average()
                .orElse(0.0);

        if (probability <= 0.0) {
            log.debug("访问任务 {} 的平均概率为 0，评分为 0", accessTask.getAccessId());
            return 0.0;
        }

        // 步骤 4：提取 EffectiveCoverageArea
        double effectiveCoverageArea = accessTask.getCoverage().getArea();
        if (effectiveCoverageArea <= 0.0) {
            log.debug("访问任务 {} 的有效覆盖面积为 0，评分为 0", accessTask.getAccessId());
            return 0.0;
        }

        // 步骤 5：计算 TimeWeight
        double timeWeight = calculateTimeWeight(currentTime, accessTask.getAccessTime());
        if (timeWeight <= 0.0) {
            log.debug("访问任务 {} 的时间权重为 0，评分为 0", accessTask.getAccessId());
            return 0.0;
        }

        // 步骤 6：计算综合评分
        double score = probability * effectiveCoverageArea * timeWeight;

        // 防御：由于浮点计算，score 理论上非负，但做一层保护
        if (score < 0.0 || Double.isNaN(score) || Double.isInfinite(score)) {
            log.warn("访问任务 {} 计算出异常评分：{}，强制设为 0",
                    accessTask.getAccessId(), score);
            score = 0.0;
        }

        log.debug("访问任务 {} 评分详情：probability={}, area={}, timeWeight={}, score={}",
                accessTask.getAccessId(), probability, effectiveCoverageArea, timeWeight, score);

        return score;
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
     * 批量为访问任务列表计算评分。
     * <p>
     * 遍历 accessTasks 列表，逐个调用 scoreTask 方法。
     * 返回与输入列表一一对应的评分结果列表。
     * </p>
     * <p>
     * 当前版本使用顺序遍历，后续可以优化为并行流（parallelStream）以提升性能。
     * </p>
     *
     * @param accessTasks 访问任务列表
     * @param currentTime 当前规划时间
     * @return 评分结果列表，与输入列表一一对应（index 相同）
     */
    @Override
    public List<Double> scoreTasks(List<AccessTask> accessTasks, LocalDateTime currentTime) {
        if (accessTasks == null || accessTasks.isEmpty()) {
            return new ArrayList<>();
        }

        List<Double> scores = new ArrayList<>(accessTasks.size());
        int positiveCount = 0;

        for (AccessTask accessTask : accessTasks) {
            double score = scoreTask(accessTask, currentTime);
            scores.add(score);
            if (score > 0) {
                positiveCount++;
            }
        }

        log.debug("评分完成：访问任务总数={}, 正分任务数={}", accessTasks.size(), positiveCount);
        return scores;
    }

}
