package com.ripple.planner.model;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 单个搜索任务的参数定义。
 * <p>
 * 该类描述一次卫星搜索任务所需的所有输入参数，包括：
 * - taskID: 任务唯一标识
 * - satellite: 执行任务的卫星标识
 * - scoutTime: 侦察时间（通常表示任务开始或执行的时间点）
 * - catas: 该任务的覆盖区域列表，由多个四边形（Cata）组成
 * </p>
 * <p>
 * 设计说明：
 * 1. 一个 TaskParam 可以包含多个 Cata，表示该卫星任务可以覆盖多个不连续的区域。
 *    这在实际卫星任务中很常见，例如一次过境可以拍摄多个相邻或不相邻的目标区域。
 * 2. scoutTime 使用 String 类型而非 LocalDateTime。
 *    原因：与已有涟漪模型的接口契约保持一致，避免时间格式解析差异。
 *    Planner 模块在需要时会将其解析为 LocalDateTime 进行时间比较。
 * 3. 该类在 Planner 模块中扮演重要角色：
 *    - 作为 LianyiQueryParam.taskIDs 的元素，输入给涟漪模型
 *    - 作为 PlannerState.historyTasks / candidateTasks / taskSequence 的元素，维护规划状态
 * </p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "单个搜索任务的参数定义，描述一次卫星搜索任务所需的全部输入参数")
public class TaskParam {

    /**
     * 任务唯一标识符。
     * <p>
     * 在同一规划会话内必须唯一，用于区分不同任务。
     * Planner 模块通过 taskID 追踪任务的执行历史和候选状态。
     * </p>
     */
    @Schema(description = "任务唯一标识符，在同一规划会话内必须唯一", example = "TASK_001", requiredMode = Schema.RequiredMode.REQUIRED)
    private String taskID;

    /**
     * 执行该任务的卫星标识。
     * <p>
     * 例如卫星编号、卫星名称等。
     * 后续 AccessService 会根据卫星标识计算访问窗口。
     * </p>
     */
    @Schema(description = "执行该任务的卫星标识，例如卫星编号或名称", example = "SAT_01", requiredMode = Schema.RequiredMode.REQUIRED)
    private String satellite;

    /**
     * 侦察时间。
     * <p>
     * 表示该任务的计划执行时间，格式由上层系统约定（通常为 yyyy-MM-dd HH:mm:ss 或 ISO-8601）。
     * Planner 模块在规划循环中会将当前时间推进到已选任务的 scoutTime，确保时间顺序合理。
     * </p>
     */
    @Schema(description = "侦察时间，格式: yyyy-MM-dd HH:mm:ss", example = "2026-07-01 10:00:00", requiredMode = Schema.RequiredMode.REQUIRED)
    private String scoutTime;

    /**
     * 该任务的覆盖区域列表。
     * <p>
     * 每个 Cata 描述一个四边形覆盖范围。
     * 多个 Cata 支持一次任务覆盖多个分离的区域。
     * GeometryService 会将这些 Cata 转换为 JTS Polygon 或 MultiPolygon，
     * 用于与 Ripple 区域计算相交面积。
     * </p>
     */
    @Schema(description = "该任务的覆盖区域列表，由多个四边形（Cata）组成", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<Cata> catas;

}
