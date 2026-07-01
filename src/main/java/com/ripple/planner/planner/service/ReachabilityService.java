package com.ripple.planner.planner.service;

import com.ripple.planner.planner.model.Grid;

import java.time.LocalDateTime;

/**
 * 可达性服务接口。
 * <p>
 * 负责判断目标在任务执行时刻是否仍然可能到达指定的网格区域。
 * 如果目标无法到达该网格，则该任务的评分为 0，不会被选中。
 * </p>
 * <p>
 * 核心职责：
 * 1. 基于目标的运动模型（速度、最大机动能力等），计算从当前时刻到任务执行时刻，
 *    目标可能到达的最大范围。
 * 2. 判断该范围是否与目标 Grid 相交。
 * 3. 为 RippleTaskPlanner 提供任务可行性过滤。
 * </p>
 * <p>
 * 设计原则：
 * 1. 接口占位：当前版本暂时返回 true（所有任务都可达），保证规划流程完整运行。
 *    后续接入真实运动模型后，再实现具体逻辑。
 * 2. 与 ProbabilityService 的区别：
 *    - ProbabilityService 回答"目标在网格中的概率是多少"（量化）。
 *    - ReachabilityService 回答"目标是否可能到达网格"（二值化）。
 *    两者配合使用：先判断可达性（硬性约束），再计算概率（软性评分）。
 * 3. 时间驱动：输入 currentTime（当前时间）和 accessTime（任务执行时间），
 *    计算时间差内目标可能移动的距离。
 * </p>
 * <p>
 * TODO（后续实现）：
 * 1. 接入目标运动模型，根据目标速度、最大加速度、环境约束（洋流、风向等），
 *    计算时间差内的可达区域（通常为一个圆或椭圆）。
 * 2. 与涟漪模型的扩散模型联动，避免重复计算。
 * 3. 支持多目标场景，区分不同目标的可达性。
 * </p>
 */
public interface ReachabilityService {

    /**
     * 判断目标在任务执行时间是否可能到达指定网格。
     * <p>
     * 当前版本（占位实现）：直接返回 true，表示所有任务都可达。
     * 这样规划流程可以完整运行，不会因为可达性判断而过滤掉所有候选。
     * </p>
     * <p>
     * 后续真实实现逻辑示例：
     * 1. 计算时间差 deltaTime = accessTime - currentTime。
     * 2. 根据目标最大速度 maxSpeed，计算最大可达距离 maxDistance = maxSpeed × deltaTime。
     * 3. 以目标最后已知位置为中心，maxDistance 为半径，构造可达圆（JTS Polygon 近似）。
     * 4. 判断可达圆是否与 grid.geometry 相交。
     * 5. 返回相交结果（true = 可达，false = 不可达）。
     * </p>
     *
     * @param grid        目标网格
     * @param currentTime 当前规划时间
     * @param accessTime  任务的计划执行时间
     * @param targetSpeed 目标估计速度（单位与涟漪模型一致）
     * @return true 如果目标可能到达该网格；false 如果不可能到达
     */
    boolean isReachable(Grid grid, LocalDateTime currentTime, LocalDateTime accessTime, double targetSpeed);

}
