package com.ripple.planner.planner.model;

import com.ripple.planner.model.TaskParam;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 规划器状态。
 * <p>
 * 维护规划过程中的动态状态。与旧版的核心区别：
 * <strong>删除 candidateTasks 和 taskSequence</strong>。
 * Planner 不再维护候选任务池，所有任务由 AccessService 动态生成。
 * </p>
 * <p>
 * 当前状态字段：
 * - currentTime: 当前规划时间（虚拟时钟）。每轮选中任务后更新为该任务的 accessTime。
 * - historyTasks: 已执行的任务列表（List&lt;TaskParam&gt;）。
 *   每轮调用涟漪模型时作为 LianyiQueryParam.taskIDs 传入，用于剔除已搜索区域。
 * </p>
 * <p>
 * 设计说明：
 * 1. 状态最小化：只保留规划循环必需的状态，不存储候选任务或任务序列。
 *   任务序列由 RippleTaskPlanner 在规划过程中直接收集到 TaskSequenceResult 中。
 * 2. historyTasks 使用 ArrayList，保证 O(1) 尾部追加性能。
 * 3. PlannerState 是可变对象，RippleTaskPlanner 直接修改其字段。
 *   如果未来需要支持回溯或分支定界，可以扩展为不可变快照模式。
 * 4. historyTasks 的顺序不影响涟漪模型计算，但按执行时间排序便于调试和日志记录。
 * </p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PlannerState {

    /**
     * 当前规划时间。
     * <p>
     * 这是规划器的"虚拟时钟"，表示当前规划到的时间点。
     * 初始值为 PlannerRequest.currentTime。
     * 每轮规划循环中，选择最优 AccessTask 后，currentTime 更新为该任务的 accessTime，
     * 确保后续规划基于新的时间基准推进。
     * </p>
     */
    private LocalDateTime currentTime;

    /**
     * 已执行的任务列表（历史任务）。
     * <p>
     * 这些任务已经被 Planner 选中并"执行"（在规划层面）。
     * 在调用涟漪模型时，historyTasks 会被复制到 LianyiQueryParam.taskIDs 中，
     * 涟漪模型据此计算已搜索区域，并从概率分布中剔除。
     * </p>
     * <p>
     * 重要：每个历史任务由选中的 AccessTask 转换而来的 TaskParam，
     * 包含 taskID、satellite、scoutTime（格式化后的 accessTime）和 catas（从 coverage 提取的外接矩形）。
     * </p>
     */
    private List<TaskParam> historyTasks = new ArrayList<>();

}
