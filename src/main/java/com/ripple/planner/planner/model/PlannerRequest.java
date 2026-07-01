package com.ripple.planner.planner.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 规划请求。
 * <p>
 * 作为 RippleTaskPlanner.plan() 方法的输入，封装一次规划会话所需的全部初始参数。
 * 与旧版 PlanningRequest 的核心区别：<strong>Planner 不接收任何候选任务</strong>，
 * 所有任务由 AccessService 在规划过程中根据 Ripple 区域和轨道数据动态生成。
 * </p>
 * <p>
 * 字段说明：
 * - centerLon / centerLat: 搜索中心点坐标，通常是目标最后已知位置。
 * - entityID: 目标实体标识，用于涟漪模型区分不同目标。
 * - targetLastFindTime: 目标最后被发现的时刻（LocalDateTime），涟漪模型据此计算扩散时间。
 * - currentTime: 当前规划起始时间，即 PlannerState.currentTime 的初始值。
 * - speed: 目标估计最大航速（km/h），影响涟漪扩散范围。
 * - planningHour: 规划时间窗口（小时），AccessService 在此时间范围内搜索卫星访问机会。
 * </p>
 * <p>
 * 设计原则：
 * 1. 解耦：PlannerRequest 不包含任何 Task 相关信息，Planner 完全不依赖外部任务输入。
 * 2. 时间驱动：所有时间字段使用 LocalDateTime，仅在构造 LianyiQueryParam 时格式化为 String。
 * 3. 窗口化：planningHour 定义每轮规划的时间范围，AccessService 只在此范围内生成访问机会。
 * </p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PlannerRequest {

    /**
     * 搜索中心点经度（目标最后发现位置）。
     * <p>
     * 单位为度，范围 -180.0 到 180.0。
     * </p>
     */
    private double centerLon;

    /**
     * 搜索中心点纬度（目标最后发现位置）。
     * <p>
     * 单位为度，范围 -90.0 到 90.0。
     * </p>
     */
    private double centerLat;

    /**
     * 目标实体标识。
     * <p>
     * 用于涟漪模型区分不同目标，不同目标可能有不同的运动模型参数。
     * </p>
     */
    private String entityID;

    /**
     * 目标最后被发现的时刻。
     * <p>
     * 使用 LocalDateTime 类型，便于时间运算。
     * 构造 LianyiQueryParam 时格式化为 yyyy-MM-dd HH:mm:ss 字符串。
     * </p>
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime targetLastFindTime;

    /**
     * 当前规划起始时间。
     * <p>
     * PlannerState.currentTime 的初始值。
     * 也是第一轮循环中调用涟漪模型时的 scoutTime 基准。
     * </p>
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime currentTime;

    /**
     * 目标估计最大航速。
     * <p>
     * 单位：km/h。
     * 涟漪模型根据此速度计算目标在时间差内可能移动的最大距离，从而确定 Ripple 半径。
     * </p>
     */
    private double speed;

    /**
     * 规划时间窗口（小时）。
     * <p>
     * 定义每轮规划中 AccessService 搜索卫星访问机会的时间范围。
     * 例如：planningHour = 6 表示在当前时间起 6 小时内寻找可用访问窗口。
     * </p>
     * <p>
     * 设计说明：
     * 1. 使用相对小时数而非绝对结束时间，简化调用方配置。
     * 2. Planner 内部计算 endTime = currentTime + planningHour。
     * 3. 后续可扩展为支持自定义时间窗口（如非均匀窗口、多段窗口）。
     * </p>
     */
    private int planningHour;

}
