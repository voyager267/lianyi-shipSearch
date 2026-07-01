package com.ripple.planner.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 客户端地理区域表示，用于描述 Polygon 内部的洞（Hole）或独立多边形区域。
 * <p>
 * 在涟漪模型的输出中，LianyiResultNew.excludeGeos 是一个 List&lt;ToClientGeo&gt;，
 * 每个 ToClientGeo 表示一个需要从主 Polygon 中剔除的洞区域。
 * 这种设计允许主 Polygon 包含多个互不连通的洞，适用于复杂地理边界场景。
 * </p>
 * <p>
 * 设计说明：
 * 1. 当前版本每个 ToClientGeo 只包含一组 lianyiPoints，表示一个简单多边形（无嵌套洞）。
 *    如果未来需要洞中再挖洞，可以扩展为递归结构，但当前需求不需要。
 * 2. 与 LianyiResultNew 配合使用：
 *    - lianyiPoints（主外轮廓） + excludeGeos（洞） = Polygon With Holes
 * 3. 使用 List&lt;LianyiPoint&gt; 存储顶点，顶点顺序通常为顺时针或逆时针。
 *    在转换为 JTS Geometry 时，需要确保顶点顺序符合 JTS 要求（通常不要求特定方向，但要求首尾闭合）。
 * </p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ToClientGeo {

    /**
     * 构成该区域外轮廓的顶点列表。
     * <p>
     * 每个点按顺序连接形成闭合多边形。
     * 作为洞时，这些点定义的区域将从主 Polygon 中剔除。
     * </p>
     */
    private List<LianyiPoint> lianyiPoints;

}
