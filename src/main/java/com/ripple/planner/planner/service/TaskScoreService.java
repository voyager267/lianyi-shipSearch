package com.ripple.planner.planner.service;

import com.ripple.planner.planner.model.TaskCandidate;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 任务评分服务接口。
 * <p>
 * 负责为每个候选任务（TaskCandidate）计算综合评分。
 * 评分是规划器选择最优任务的核心依据，直接决定任务序列的质量。
 * </p>
 * <p>
 * 第一版评分公式：
 *     Score = Probability × EffectiveCoverageArea × TimeWeight
 * 其中：
 * - Probability = Grid.probability（目标存在于该网格的概率，由 ProbabilityService 计算）
 * - EffectiveCoverageArea = coverage.getArea()（任务在 Ripple 中的有效覆盖面积）
 * - TimeWeight = 1 / (1 + 等待时间秒数)（等待时间越长，权重越低）
 * </p>
 * <p>
 * 设计原则：
 * 1. 评分逻辑与选择逻辑分离：TaskScoreService 只负责计算分数，不负责选择任务。
 *    选择逻辑由 RippleTaskPlanner 根据分数排序执行。
 * 2. 可替换性：通过接口定义评分契约，后续可以引入更复杂的评分模型
 *    （如多目标优化、Pareto 前沿、强化学习等），只需替换实现类。
 * 3. 纯函数倾向：评分计算应基于输入参数，避免依赖全局状态或副作用。
 *    这便于单元测试和并行计算。
 * 4. 分数语义：score > 0 表示任务可行；score = 0 表示任务不可行或无效；
 *    score 越高表示任务越优。
 * </p>
 * <p>
 * 后续扩展方向：
 * 1. 引入任务优先级权重（如高价值区域优先）。
 * 2. 引入卫星负载均衡（避免单颗卫星任务过多）。
 * 3. 引入任务间耦合惩罚（如两个任务覆盖区域高度重叠时降低分数）。
 * 4. 引入不确定性建模（如天气影响、传感器故障概率）。
 * </p>
 */
public interface TaskScoreService {

    /**
     * 为单个候选任务计算评分。
     * <p>
     * 根据当前规划时间、候选任务的概率、覆盖面积和时间因素，计算综合得分。
     * 计算结果直接写回 candidate.setScore(score)。
     * </p>
     *
     * @param candidate   候选任务，包含 grid（probability）、coverage、accessTime
     * @param currentTime 当前规划时间，用于计算等待时间和 TimeWeight
     */
    void scoreTask(TaskCandidate candidate, LocalDateTime currentTime);

    /**
     * 批量为候选任务列表计算评分。
     * <p>
     * 遍历 candidates 列表，逐个调用 scoreTask 方法。
     * 提供批量接口是为了方便后续引入并行计算（如使用 Stream.parallel() 或 CompletableFuture）。
     * </p>
     *
     * @param candidates  候选任务列表
     * @param currentTime 当前规划时间
     */
    void scoreTasks(List<TaskCandidate> candidates, LocalDateTime currentTime);

}
