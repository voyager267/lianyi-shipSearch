package com.ripple.planner.planner.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 搜索记录。
 * <p>
 * 用于记录每一次已执行访问任务的详细信息，包括任务本身、执行时间和评分。
 * 在规划结束后，SearchRecord 列表作为任务序列的详细日志，用于复盘和审计。
 * </p>
 * <p>
 * 与旧版的区别：
 * - 旧版引用 TaskParam（外部输入的静态任务定义）。
 * - 新版引用 AccessTask（AccessService 动态生成的访问机会）。
 * 这体现了"Task 由 AccessService 动态生成"的核心设计思想。
 * </p>
 * <p>
 * 设计说明：
 * 1. 当前版本保持最小化，记录 accessTask 和 executedAt。
 *    后续可扩展字段（如 scoreAtSelectionTime、rippleAreaAtSelectionTime）以支持更详细的分析。
 * 2. executedAt 使用 LocalDateTime，表示 Planner 模块内部使用的标准时间类型。
 * 3. 在 RippleTaskPlanner 的规划循环中，每次选中 AccessTask 后创建 SearchRecord，
 *    并加入 TaskSequenceResult 的 records 列表。
 * </p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SearchRecord {

    /**
     * 被执行的访问任务。
     * <p>
     * 包含 accessId、satellite、accessTime、coverage、grids 等完整信息。
     * 通过引用 AccessTask 而非复制字段，保持数据一致性并减少冗余。
     * </p>
     */
    private AccessTask accessTask;

    /**
     * 任务执行时间。
     * <p>
     * 使用 LocalDateTime 表示，即该 AccessTask 的 accessTime。
     * 也是 PlannerState.currentTime 在该轮循环更新后的值。
     * 该字段标识了任务在规划序列中的实际执行时刻，用于时间线分析和冲突检测。
     * </p>
     */
    private LocalDateTime executedAt;

}
