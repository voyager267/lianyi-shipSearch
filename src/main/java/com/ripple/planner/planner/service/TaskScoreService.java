package com.ripple.planner.planner.service;

import com.ripple.planner.planner.model.AccessTask;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 任务评分服务接口。
 * <p>
 * 负责为每个 AccessService 动态生成的访问任务（AccessTask）计算综合评分。
 * 评分是规划器选择最优任务的核心依据，直接决定任务序列的质量。
 * </p>
 * <p>
 * 第一版评分公式：
 *     Score = Probability × EffectiveCoverageArea × TimeWeight
 * 其中：
 * - Probability = AccessTask.grids 中各 Grid 的 probability 平均值（或聚合值）。
 *                 表示目标存在于该访问任务覆盖区域的整体概率。
 * - EffectiveCoverageArea = AccessTask.coverage.getArea()（访问任务的有效覆盖面积）。
 * - TimeWeight = 1 / (1 + 等待时间秒数)（等待时间越长，权重越低）。
 * </p>
 * <p>
 * 与旧版的区别：
 * - 旧版输入 TaskCandidate（包装 TaskParam + Grid + coverage）。
 * - 新版输入 AccessTask（AccessService 动态生成的访问机会，自带 coverage 和 grids）。
 * 这体现了"Task 由 AccessService 动态生成"的核心设计思想。
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
     * 为单个访问任务计算评分。
     * <p>
     * 根据当前规划时间、访问任务的概率、覆盖面积和时间因素，计算综合得分。
     * 计算结果直接写回 accessTask 的扩展字段或通过返回值传递。
     * </p>
     * <p>
     * 注意：当前版本将 score 作为方法的返回值，而非写入 AccessTask。
     * 原因：AccessTask 是 AccessService 生成的数据对象，不应被评分服务修改。
     *      后续如需在 AccessTask 中增加 score 字段，可以调整为写回模式。
     * </p>
     *
     * @param accessTask  访问任务，包含 grids（probability）、coverage、accessTime
     * @param currentTime 当前规划时间，用于计算等待时间和 TimeWeight
     * @return 该访问任务的综合评分，score <= 0 表示不可行
     */
    double scoreTask(AccessTask accessTask, LocalDateTime currentTime);

    /**
     * 批量为访问任务列表计算评分。
     * <p>
     * 遍历 accessTasks 列表，逐个调用 scoreTask 方法。
     * 提供批量接口是为了方便后续引入并行计算（如使用 Stream.parallel() 或 CompletableFuture）。
     * </p>
     *
     * @param accessTasks 访问任务列表
     * @param currentTime 当前规划时间
     * @return 评分结果列表，与输入列表一一对应（index 相同）
     */
    List<Double> scoreTasks(List<AccessTask> accessTasks, LocalDateTime currentTime);

}
