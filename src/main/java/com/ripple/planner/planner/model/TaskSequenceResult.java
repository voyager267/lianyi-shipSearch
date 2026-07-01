package com.ripple.planner.planner.model;

import com.ripple.planner.model.TaskParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 任务序列规划结果。
 * <p>
 * 这是 Planner 模块的最终输出，包含完整的规划结果信息，供上层系统（Controller、前端、调度系统）使用。
 * </p>
 * <p>
 * 核心字段说明：
 * - taskSequence: 按执行时间排序的任务序列。每个元素是 TaskParam，表示推荐执行的任务及其参数。
 * - records: 详细的搜索记录列表。每个 SearchRecord 包含任务和执行时间，用于审计和复盘。
 * - totalScore: 规划总得分的汇总（可选）。当前版本保留字段，后续可扩展为各任务 score 之和或加权平均。
 * - executionCount: 实际规划出的任务数量。等于 taskSequence.size()，提供便捷的访问方式。
 * - message: 规划过程的描述信息或终止原因。例如："规划完成：所有候选任务评分均为0" 或 "规划完成：候选任务池耗尽"。
 * </p>
 * <p>
 * 设计说明：
 * 1. 与 PlannerState 的区别：
 *    - PlannerState 是规划过程中的可变状态，包含 candidateTasks（未执行任务）。
 *    - TaskSequenceResult 是规划结束后的不可变成果，只包含已选中的任务和记录。
 * 2. 在构造函数中初始化列表，避免 NPE。
 * 3. 提供 executionCount 作为便捷字段，避免上层系统重复计算 size()。
 * 4. message 字段用于传递规划终止原因，帮助上层系统理解为什么规划在特定时刻停止。
 *    这对于调试和优化非常重要（例如：是因为没有候选任务？还是评分机制导致？）。
 * 5. 该类作为 RippleTaskPlanner.plan() 的返回值，也是 TaskPlanningController 的响应体核心数据。
 * </p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "任务序列规划结果，Planner 模块的最终输出")
public class TaskSequenceResult {

    /**
     * 规划出的任务序列。
     * <p>
     * 按执行时间升序排列的任务列表。
     * 每个任务包含完整的 TaskParam 信息，上层系统可以直接使用该序列进行任务下发和调度。
     * </p>
     */
    @Schema(description = "按执行时间升序排列的规划任务序列")
    private List<TaskParam> taskSequence = new ArrayList<>();

    /**
     * 详细的搜索记录。
     * <p>
     * 与 taskSequence 一一对应（通常 index 相同），但包含更丰富的执行时间信息。
     * 用于生成规划报告、甘特图、时间线分析等。
     * </p>
     */
    @Schema(description = "详细的搜索记录列表，用于审计和复盘")
    private List<SearchRecord> records = new ArrayList<>();

    /**
     * 规划总得分（可选）。
     * <p>
     * 当前版本保留，可用于后续评估不同规划策略的效果。
     * 例如：totalScore = sum(selectedTask.score)，得分越高表示规划质量越好。
     * 第一版可设为 0.0，或计算各选中任务 score 的总和。
     * </p>
     */
    @Schema(description = "规划总得分，可用于评估不同规划策略的效果", example = "0.85")
    private double totalScore;

    /**
     * 实际执行的任务数量。
     * <p>
     * 等于 taskSequence.size()，提供便捷的只读访问。
     * 如果 planning 因无可用任务而提前终止，executionCount 可能小于初始候选任务数。
     * </p>
     */
    @Schema(description = "实际规划出的任务数量", example = "5")
    private int executionCount;

    /**
     * 规划结果描述信息。
     * <p>
     * 用于说明规划结果的状态和终止原因。
     * 示例：
     * - "规划成功：共选出 5 个任务"
     * - "规划完成：所有候选任务评分均为 0，无法继续优化"
     * - "规划完成：候选任务池已耗尽"
     * </p>
     */
    @Schema(description = "规划结果描述信息或终止原因", example = "规划成功：共选出 5 个任务")
    private String message;

}
