package com.ripple.planner.planner.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.locationtech.jts.geom.Geometry;

/**
 * 全球网格单元（Grid Cell）。
 * <p>
 * 将整个地球表面（或关注区域）划分为规则的网格单元，
 * 每个 Grid 是空间分析和概率计算的最小粒度单元。
 * </p>
 * <p>
 * 核心作用：
 * 1. 空间索引：通过 Grid ID 快速定位空间位置，避免直接对复杂多边形进行逐个像素运算。
 * 2. 概率载体：每个 Grid 关联一个 probability 字段，表示目标存在于该网格的概率。
 * 3. 任务覆盖计算：TaskCandidate 的 coverage 与 Grid.geometry 求交，可快速判断任务是否覆盖该网格。
 * </p>
 * <p>
 * 设计说明：
 * 1. geometry 使用 JTS Geometry 类型，可以是 Polygon 或 MultiPolygon。
 *    这样 Grid 可以支持非矩形网格（如根据地理边界裁剪的网格）。
 * 2. probability 初始值为 0.0，由 ProbabilityService 根据 Ripple 区域动态计算并赋值。
 * 3. id 采用 String 类型，推荐使用层级编码（如 H3 索引、Geohash、或自定义行列号），
 *    便于快速检索和持久化。
 * 4. 该类是 GridService 的核心输出元素，也是 ProbabilityService 和 TaskScoreService 的输入元素。
 * </p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Grid {

    /**
     * 网格唯一标识符。
     * <p>
     * 推荐使用空间索引编码，例如：
     * - H3 索引（十六进制字符串，如 "85283473fffffff"）
     * - Geohash（如 "wx4g0b"）
     * - 自定义行列号（如 "row_12_col_34"）
     * ID 的编码方式由 GridService 的实现决定，Planner 其他模块只将其作为标识使用。
     * </p>
     */
    private String id;

    /**
     * 网格的几何形状。
     * <p>
     * 使用 JTS Geometry 表示，通常为 Polygon（矩形或自定义形状）。
     * Geometry 的坐标系应与 Ripple 模型输出的坐标系一致（通常为 WGS-84，经纬度）。
     * 注意：JTS 默认使用平面几何计算，对于大尺度地理区域，需考虑投影转换或球面几何误差。
     * 第一版暂不考虑投影问题，直接在经纬度坐标系下使用 JTS 进行近似计算。
     * </p>
     */
    private Geometry geometry;

    /**
     * 目标存在于该网格的概率。
     * <p>
     * 取值范围：0.0 到 1.0。
     * 初始值为 0.0，由 ProbabilityService 根据当前 Ripple 区域与 Grid 的相交面积计算得出。
     * 计算方式（第一版）：
     *     probability = Area(Grid ∩ Ripple) / Area(Ripple)
     * 所有与 Ripple 相交的 Grid 的 probability 之和理论上应接近 1.0（归一化）。
     * </p>
     */
    private double probability;

}
