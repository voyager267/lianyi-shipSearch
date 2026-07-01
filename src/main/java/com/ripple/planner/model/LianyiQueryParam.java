package com.ripple.planner.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 涟漪模型查询参数。
 * <p>
 * 该类是调用已有涟漪模型时所需的全部输入参数。
 * 涟漪模型根据这些参数计算当前时刻目标可能存在的概率分布区域（Ripple）。
 * </p>
 * <p>
 * 参数说明：
 * - centerLon / centerLat: 搜索中心点坐标，通常是目标最后已知位置或搜索区域的中心。
 * - entityID: 目标实体标识，涟漪模型可能根据目标特征（速度、类型等）调整扩散模型参数。
 * - targetLastFindTime: 目标最后被发现的时刻，用于计算时间衰减和扩散范围。
 * - scoutTime: 当前侦察时刻，即 Ripple 计算的时间基准点。
 * - speed: 目标估计速度，影响涟漪扩散的半径和形态。
 * - taskIDs: 已执行的搜索任务列表（即 historyTasks）。
 *            涟漪模型根据这些任务的覆盖区域和侦察时间，剔除已搜索区域，计算剩余概率分布。
 * </p>
 * <p>
 * 设计说明：
 * 1. 本类属于已有涟漪模型，Planner 模块负责在每次规划循环中构造该对象并传入。
 * 2. Planner 每次循环更新 historyTasks 后，都需要重新构造 LianyiQueryParam 并调用涟漪模型，
 *    因为目标概率分布会随着搜索历史动态变化。
 * 3. 时间字段使用 String 类型，与已有接口保持一致。
 * </p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LianyiQueryParam {

    /**
     * 搜索中心点经度 (Longitude)。
     * <p>
     * 单位为度，范围 -180.0 到 180.0。
     * 通常是目标最后已知位置的经度，或搜索任务区域的中心经度。
     * </p>
     */
    private double centerLon;

    /**
     * 搜索中心点纬度 (Latitude)。
     * <p>
     * 单位为度，范围 -90.0 到 90.0。
     * </p>
     */
    private double centerLat;

    /**
     * 目标实体标识。
     * <p>
     * 用于涟漪模型区分不同目标，不同目标可能有不同的运动模型参数。
     * 例如：船舶、飞机、车辆等目标的扩散模型可能不同。
     * </p>
     */
    private String entityID;

    /**
     * 目标最后被发现的时刻。
     * <p>
     * 格式与上层系统约定一致（通常为 yyyy-MM-dd HH:mm:ss）。
     * 涟漪模型根据当前时间与该时间的差值，计算目标可能移动的最大距离，从而确定 Ripple 半径。
     * </p>
     */
    private String targetLastFindTime;

    /**
     * 当前侦察时刻。
     * <p>
     * 涟漪模型以此时间为基准计算概率分布。
     * Planner 模块在规划循环中会将此值更新为已选任务的执行时间，模拟时间推进。
     * </p>
     */
    private String scoutTime;

    /**
     * 目标估计速度。
     * <p>
     * 单位通常为米/秒或公里/小时，具体单位由涟漪模型内部定义。
     * 速度越大，涟漪扩散范围越广；速度为 0 时，概率集中在中心点附近。
     * </p>
     */
    private double speed;

    /**
     * 已执行的搜索任务列表。
     * <p>
     * 即 PlannerState.historyTasks 的副本。
     * 涟漪模型根据这些任务的覆盖区域和侦察时间，计算已搜索区域，
     * 并从概率分布中剔除这些区域，得到剩余 Ripple。
     * 这是 Planner 与涟漪模型交互的核心数据：Planner 不断将新选中的任务加入此列表，
     * 涟漪模型据此动态更新概率分布，形成闭环。
     * </p>
     */
    private List<TaskParam> taskIDs;

}
