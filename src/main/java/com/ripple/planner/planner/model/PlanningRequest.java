package com.ripple.planner.planner.model;

import com.ripple.planner.model.TaskParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 规划请求。
 * <p>
 * 作为 RippleTaskPlanner.plan() 方法的输入，封装一次规划会话所需的全部初始参数。
 * 这些参数在规划过程中通常保持不变（除了 scoutTime 会随 currentTime 推进而更新）。
 * </p>
 * <p>
 * 字段说明：
 * - centerLon / centerLat: 搜索中心点坐标，通常是目标最后已知位置。
 * - entityID: 目标实体标识，用于涟漪模型区分不同目标。
 * - targetLastFindTime: 目标最后被发现的时刻，用于涟漪模型计算扩散时间。
 * - targetSpeed: 目标估计速度，影响涟漪扩散范围。
 * - startTime: 规划起始时间，即 PlannerState.currentTime 的初始值。
 * - candidateTasks: 初始候选任务池，包含所有可供 Planner 选择的任务。
 * </p>
 * <p>
 * 设计说明：
 * 1. 与 LianyiQueryParam 的区别：
 *    - PlanningRequest 是 Planner 模块的输入，包含规划控制参数。
 *    - LianyiQueryParam 是涟漪模型的输入，包含动态更新的 scoutTime 和 taskIDs。
 *    RippleTaskPlanner 负责在每次循环中根据 PlanningRequest 和 PlannerState 构造 LianyiQueryParam。
 * 2. candidateTasks 在构造函数中初始化为 ArrayList，避免 NPE。
 *    传入后 Planner 会从中移除已选任务，因此传入的列表会被修改，建议调用方传入副本。
 * 3. 所有时间字段使用 LocalDateTime（Java 8 时间 API），仅在构造 LianyiQueryParam 时格式化为 String。
 * </p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "规划请求参数，封装一次规划会话所需的全部初始参数")
public class PlanningRequest {

    /**
     * 搜索中心点经度。
     */
    @Schema(description = "搜索中心点经度，范围 -180.0 到 180.0", example = "116.4", requiredMode = Schema.RequiredMode.REQUIRED)
    private double centerLon;

    /**
     * 搜索中心点纬度。
     */
    @Schema(description = "搜索中心点纬度，范围 -90.0 到 90.0", example = "39.9", requiredMode = Schema.RequiredMode.REQUIRED)
    private double centerLat;

    /**
     * 目标实体标识。
     */
    @Schema(description = "目标实体标识，用于涟漪模型区分不同目标", example = "TARGET_001", requiredMode = Schema.RequiredMode.REQUIRED)
    private String entityID;

    /**
     * 目标最后被发现的时刻。
     * <p>
     * 格式与涟漪模型约定一致（通常为 yyyy-MM-dd HH:mm:ss）。
     * </p>
     */
    @Schema(description = "目标最后被发现的时刻，格式: yyyy-MM-dd HH:mm:ss", example = "2026-07-01 08:00:00", requiredMode = Schema.RequiredMode.REQUIRED)
    private String targetLastFindTime;

    /**
     * 目标估计速度。
     */
    @Schema(description = "目标估计速度，影响涟漪扩散范围", example = "10.0", requiredMode = Schema.RequiredMode.REQUIRED)
    private double targetSpeed;

    /**
     * 规划起始时间。
     * <p>
     * PlannerState.currentTime 的初始值。
     * 也是第一轮循环中调用涟漪模型时的 scoutTime。
     * </p>
     */
    @Schema(description = "规划起始时间，格式: yyyy-MM-dd HH:mm:ss", example = "2026-07-01 09:00:00", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime startTime;

    /**
     * 初始候选任务池。
     * <p>
     * 包含所有尚未执行、可供 Planner 选择的任务。
     * Planner 会从中移除已选中的任务，因此该列表会被修改。
     * </p>
     */
    @Schema(description = "初始候选任务池，包含所有可供 Planner 选择的任务", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<TaskParam> candidateTasks = new ArrayList<>();

}
