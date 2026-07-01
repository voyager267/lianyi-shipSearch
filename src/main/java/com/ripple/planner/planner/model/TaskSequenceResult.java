package com.ripple.planner.planner.model;

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
 * 与旧版的区别：
 * - 旧版输出 List&lt;TaskParam&gt;（外部输入的任务定义）。
 * - 新版输出 List&lt;AccessTask&gt;（AccessService 动态生成的访问机会）。
 * 这确保了 Planner 的输出是可直接用于卫星调度的、带精确时间和覆盖几何的任务序列。
 * </p>
 * <p>
 * 核心字段说明：
 * - taskSequence: 按执行时间排序的访问任务序列。每个元素是 AccessTask，表示推荐执行的卫星访问。
 * - records: 详细的搜索记录列表。每个 SearchRecord 包含 AccessTask 和执行时间，用于审计和复盘。
 * - totalScore: 规划总得分的汇总。当前版本为各选中任务 score 之和。
 * - executionCount: 实际规划出的任务数量。等于 taskSequence.size()，提供便捷的访问方式。
 * - message: 规划过程的描述信息或终止原因。
 * </p>
 * <p>
 * 设计说明：
 * 1. 与 PlannerState 的区别：
 *    - PlannerState 是规划过程中的可变状态，只包含 historyTasks（已转换的 TaskParam）。
 *    - TaskSequenceResult 是规划结束后的成果，包含原始的 AccessTask 序列和记录。
 * 2. 在构造函数中初始化列表，避免 NPE。
 * 3. message 字段用于传递规划终止原因，帮助上层系统理解为什么规划在特定时刻停止。
 * 4. 上层系统可以直接使用 taskSequence 进行卫星指令生成和任务下发，
 *    因为 AccessTask 已经包含了精确的 accessTime、coverage 和 satellite 信息。
 * </p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TaskSequenceResult {

    /**
     * 规划出的访问任务序列。
     * <p>
     * 按执行时间升序排列的 AccessTask 列表。
     * 每个任务包含 accessId、satellite、accessTime、coverage、grids 等完整信息，
     * 上层系统可以直接使用该序列进行卫星指令生成和任务调度。
     * </p>
     */
    private List<AccessTask> taskSequence = new ArrayList<>();

    /**
     * 详细的搜索记录。
     * <p>
     * 与 taskSequence 一一对应（通常 index 相同），但包含更丰富的执行时间信息。
     * 用于生成规划报告、甘特图、时间线分析等。
     * </p>
     */
    private List<SearchRecord> records = new ArrayList<>();

    /**
     * 规划总得分。
     * <p>
     * 各选中任务 score 的总和，得分越高表示规划质量越好。
     * 可用于评估不同规划策略或参数配置的效果。
     * </p>
     */
    private double totalScore;

    /**
     * 实际执行的任务数量。
     * <p>
     * 等于 taskSequence.size()，提供便捷的只读访问。
     * 如果 planning 因无可用任务而提前终止，executionCount 可能小于理论最大任务数。
     * </p>
     */
    private int executionCount;

    /**
     * 规划结果描述信息。
     * <p>
     * 用于说明规划结果的状态和终止原因。
     * 示例：
     * - "规划成功：共选出 5 个任务"
     * - "规划完成：所有候选任务评分均为 0，无法继续优化"
     * - "规划完成：达到最大循环次数限制"
     * </p>
     */
    private String message;

}
