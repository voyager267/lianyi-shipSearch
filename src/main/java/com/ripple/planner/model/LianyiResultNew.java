package com.ripple.planner.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 涟漪模型计算结果。
 * <p>
 * 该类是已有涟漪模型的核心输出，表示在给定搜索历史（historyTasks）条件下，
 * 目标可能存在的概率分布区域。
 * </p>
 * <p>
 * 数据结构解析：
 * - lianyiPoints: 主 Polygon 的外轮廓顶点列表。按顺序连接形成闭合多边形，表示目标存在的概率区域。
 * - excludeGeos: 主 Polygon 内部的洞（Hole）列表。每个洞也是一个多边形，表示目标不可能存在的区域。
 *                  Planner 模块无需关心洞如何计算，只需将其传递给 GeometryService 进行正确的 JTS 转换。
 * - area: 该 Ripple 区域的总面积（单位由涟漪模型内部决定，通常为平方米或平方公里）。
 *         用于概率归一化和面积相关的评分计算。
 * </p>
 * <p>
 * 设计说明：
 * 1. 本类属于已有涟漪模型，Planner 模块只读取，绝不修改。
 * 2. GeometryService 负责将 LianyiResultNew 转换为 JTS 的 Polygon（带洞）或 MultiPolygon，
 *    供后续 GridService、ProbabilityService 进行空间运算。
 * 3. area 字段由涟漪模型预先计算好，Planner 可以直接使用，避免重复计算大面积复杂多边形的面积。
 * </p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LianyiResultNew {

    /**
     * 主 Polygon 外轮廓顶点列表。
     * <p>
     * 这些点按顺序定义了目标可能存在区域的外边界。
     * 在 JTS 转换时，这些点将构成 Polygon 的外环 (exterior ring)。
     * </p>
     */
    private List<LianyiPoint> lianyiPoints;

    /**
     * 主 Polygon 内部的洞列表。
     * <p>
     * 每个 ToClientGeo 表示一个需要从主区域中剔除的子区域。
     * 例如：已知目标不可能出现在某个已搜索过的区域，涟漪模型会将其标记为洞。
     * GeometryService 在构造 JTS Polygon 时，会将这些洞作为 interior rings 传入。
     * </p>
     */
    private List<ToClientGeo> excludeGeos;

    /**
     * 该涟漪区域的总面积。
     * <p>
     * 单位与涟漪模型内部保持一致（通常为平方米）。
     * ProbabilityService 在计算 Grid 概率时使用 area 作为分母进行归一化：
     *     Probability = Area(Grid ∩ Ripple) / Area(Ripple)
     * 直接使用涟漪模型返回的 area，避免重复计算，提升性能。
     * </p>
     */
    private double area;

}
