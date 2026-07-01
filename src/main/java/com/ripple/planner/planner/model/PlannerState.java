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
 * 这是整个 Planner 模块的核心状态容器，维护规划过程中的全部动态信息。
 * RippleTaskPlanner 在每次规划循环中读取和更新该状态，形成闭环决策。
 * </p>
 * <p>
 * 状态字段说明：
 * - currentTime: 当前规划时间。初始值为搜索起始时间，每轮循环更新为已选任务的执行时间，模拟时间推进。
 * - historyTasks: 已执行的任务列表。这些任务已经（在规划中假设）完成，涟漪模型会根据它们剔除已搜索区域。
 * - candidateTasks: 尚未执行的任务列表。每轮循环中，AccessService 基于这些任务和当前 Ripple 计算候选任务，
 *                    评分后选择最优任务并从 candidateTasks 中移除。
 * - taskSequence: 最终输出的任务序列。按执行时间排序，表示规划器推荐的完整搜索计划。
 * </p>
 * <p>
 * 设计说明：
 * 1. 使用 ArrayList 作为 List 的默认实现，保证 O(1) 的尾部追加和 O(n) 的遍历性能。
 * 2. 在构造函数中初始化列表，避免 NPE（空指针异常）。这是防御式编程的关键实践。
 * 3. PlannerState 是可变对象，RippleTaskPlanner 直接修改其字段。
 *    如果未来需要支持多线程或回滚功能，可以扩展为不可变对象 + 状态副本模式。
 * 4. historyTasks 和 taskSequence 在逻辑上通常一致（taskSequence 是 historyTasks 的时序视图），
 *    但分开存储便于后续扩展（例如在 taskSequence 中插入等待/转移任务，而 historyTasks 只记录搜索任务）。
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
     * 初始值为搜索任务的起始时间（由上层系统传入）。
     * 每轮规划循环中，选择最优任务后，currentTime 更新为该任务的 scoutTime，
     * 确保后续任务的时间不早于已执行任务（保持时序一致性）。
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
     * 重要：historyTasks 的顺序不影响涟漪模型计算，但建议按执行时间排序，便于调试和日志记录。
     * </p>
     */
    private List<TaskParam> historyTasks = new ArrayList<>();

    /**
     * 未执行的任务列表（候选任务池）。
     * <p>
     * 包含所有尚未被 Planner 选中的任务。
     * 每轮循环中，RippleTaskPlanner 从 candidateTasks 中筛选出在当前 Ripple 覆盖范围内、
     * 且时间可达的任务，生成 TaskCandidate 列表进行评分。
     * 选中任务后，将该任务从 candidateTasks 中移除，避免重复选择。
     * </p>
     * <p>
     * 终止条件：当 candidateTasks 为空，或所有候选任务的 score 均为 0 时，规划结束。
     * </p>
     */
    private List<TaskParam> candidateTasks = new ArrayList<>();

    /**
     * 最终任务序列。
     * <p>
     * 按执行时间排序的任务列表，表示 Planner 的完整输出。
     * 每轮循环将选中的任务追加到列表尾部，最终形成时序上合理的搜索计划。
     * </p>
     * <p>
     * 与 historyTasks 的区别：
     * - historyTasks 是输入给涟漪模型的"已搜索历史"，侧重空间覆盖。
     * - taskSequence 是输出给上层系统的"执行计划"，侧重时序调度。
     * 当前版本两者内容一致，但分离设计便于后续扩展（例如在 taskSequence 中插入等待任务）。
     * </p>
     */
    private List<TaskParam> taskSequence = new ArrayList<>();

}
