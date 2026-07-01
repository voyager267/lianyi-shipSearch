package com.ripple.planner.planner.service;

import com.ripple.planner.planner.model.Grid;
import org.locationtech.jts.geom.Geometry;

import java.util.List;

/**
 * 概率服务接口。
 * <p>
 * 负责计算每个网格（Grid）在当前 Ripple 区域中的概率值。
 * 概率表示目标存在于该网格的可能性，是任务评分（TaskScoreService）的核心输入之一。
 * </p>
 * <p>
 * 核心职责：
 * 1. 根据 Ripple 几何区域和网格的相交面积，计算每个网格的概率。
 * 2. 保证概率的归一化：所有与 Ripple 相交的网格概率之和应接近 1.0（考虑浮点误差）。
 * 3. 将计算结果写回 Grid.probability 字段。
 * </p>
 * <p>
 * 设计原则：
 * 1. 接口抽象：ProbabilityService 只定义计算契约，不绑定具体算法。
 *    第一版使用简单的面积比例法，后续可以替换为更复杂的概率模型（如核密度估计、贝叶斯更新）。
 * 2. 批量计算：一次处理整个 Grid 列表，减少重复的几何运算。
 * 3. 不可变的 Ripple 输入：rippleGeometry 和 rippleArea 在计算过程中不会被修改。
 * </p>
 */
public interface ProbabilityService {

    /**
     * 计算并更新每个网格的概率。
     * <p>
     * 计算逻辑（第一版）：
     * 遍历 gridList 中的每个 Grid：
     * 1. 计算 Grid.geometry 与 rippleGeometry 的相交面积（intersectionArea）。
     * 2. probability = intersectionArea / rippleArea。
     * 3. 将 probability 赋值给 Grid.probability。
     * </p>
     * <p>
     * 边界情况处理：
     * - 如果 rippleArea <= 0，所有 Grid 的 probability 设为 0。
     * - 如果 intersectionArea <= 0，该 Grid 的 probability 设为 0。
     * - 如果 gridList 为空，直接返回，不做任何操作。
     * </p>
     *
     * @param gridList       与 Ripple 相交的网格列表（由 GridService 提供）
     * @param rippleGeometry Ripple 区域的 JTS Geometry
     * @param rippleArea     Ripple 区域的总面积（由涟漪模型返回或 GeometryService 计算）
     */
    void calculateProbabilities(List<Grid> gridList, Geometry rippleGeometry, double rippleArea);

}
