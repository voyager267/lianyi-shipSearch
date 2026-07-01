package com.ripple.planner.planner.service;

import com.ripple.planner.planner.model.PlanningRequest;
import com.ripple.planner.planner.model.TaskSequenceResult;

/**
 * 涟漪任务规划器接口。
 * <p>
 * 这是 Planner 模块的核心入口，负责基于贪心策略生成动态卫星搜索任务序列。
 * 规划器通过不断调用已有涟漪模型，形成"调用涟漪 → 评估候选 → 选择最优 → 更新状态 → 再次调用"的闭环。
 * </p>
 * <p>
 * 核心契约：
 * - 输入：PlanningRequest，包含搜索中心、目标信息、起始时间和候选任务池。
 * - 输出：TaskSequenceResult，包含规划出的任务序列、执行记录和统计信息。
 * - 保证：不修改已有涟漪模型，只通过 LianyiModelService 接口调用。
 * </p>
 * <p>
 * 设计原则：
 * 1. 无状态：规划器本身不维护跨调用的状态，所有状态封装在 PlannerState 中。
 * 2. 可重入：多次调用 plan() 方法互不影响，便于并发请求处理（后续扩展）。
 * 3. 可观测：规划过程中的关键步骤（循环次数、选中任务、评分等）通过日志输出，便于调试。
 * 4. 终止保证：规划循环有明确的终止条件（无候选任务、所有评分均为 0、或达到最大循环次数），
 *    避免无限循环。
 * </p>
 */
public interface RippleTaskPlanner {

    /**
     * 执行任务规划。
     * <p>
     * 根据规划请求中的初始条件，执行贪心策略的规划循环，生成最优任务序列。
     * </p>
     *
     * @param request 规划请求，包含搜索中心、目标信息、起始时间和候选任务池
     * @return 规划结果，包含任务序列、执行记录和统计信息
     */
    TaskSequenceResult plan(PlanningRequest request);

}
