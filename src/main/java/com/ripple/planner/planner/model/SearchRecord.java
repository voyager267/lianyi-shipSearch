package com.ripple.planner.planner.model;

import com.ripple.planner.model.TaskParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 搜索记录。
 * <p>
 * 用于记录每一次已执行任务的详细信息，包括任务本身、执行时间、以及该任务产生的覆盖效果。
 * 在规划结束后，SearchRecord 列表可以作为任务序列的详细日志，用于复盘和审计。
 * </p>
 * <p>
 * 核心作用：
 * 1. 追踪历史：精确记录每个任务是何时被选中的、何时执行的。
 * 2. 效果评估：后续可以扩展字段（如 actualCoverage、detectedTarget）来评估任务的实际效果。
 * 3. 结果输出：TaskSequenceResult 包含 SearchRecord 列表，为上层系统提供完整的规划过程数据。
 * </p>
 * <p>
 * 设计说明：
 * 1. 当前版本保持最小化，只记录 task 和 executedAt。
 *    后续可以扩展字段（如 scoreAtSelectionTime、selectedRippleArea）以支持更详细的分析。
 * 2. executedAt 使用 LocalDateTime，表示 Planner 模块内部使用的标准时间类型。
 *    与 TaskParam.scoutTime（String）不同，LocalDateTime 便于时间运算和比较。
 * 3. 在 RippleTaskPlanner 的规划循环中，每次选中任务后创建 SearchRecord，
 *    并加入 TaskSequenceResult 的 records 列表。
 * </p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "搜索记录，记录每一次已执行任务的详细信息")
public class SearchRecord {

    /**
     * 被执行的任务。
     * <p>
     * 包含 taskID、satellite、scoutTime、catas 等完整信息。
     * 通过引用 TaskParam 而非复制字段，保持数据一致性并减少冗余。
     * </p>
     */
    @Schema(description = "被执行的任务信息")
    private TaskParam task;

    /**
     * 任务执行时间。
     * <p>
     * 使用 LocalDateTime 表示，通常为该任务的 scoutTime 解析后的时间。
     * 也是 PlannerState.currentTime 在该轮循环更新后的值。
     * 该字段标识了任务在规划序列中的实际执行时刻，用于时间线分析和冲突检测。
     * </p>
     */
    @Schema(description = "任务执行时间", example = "2026-07-01T10:00:00")
    private LocalDateTime executedAt;

}
