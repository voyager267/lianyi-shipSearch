package com.ripple.planner.planner.service;

import com.ripple.planner.model.LianyiResultNew;
import com.ripple.planner.model.TaskParam;
import org.locationtech.jts.geom.Geometry;

import java.util.List;

/**
 * 几何服务接口。
 * <p>
 * 负责将涟漪模型的输出和任务参数转换为 JTS Geometry 对象，
 * 以及执行基本的几何运算（相交、面积计算等）。
 * </p>
 * <p>
 * 核心职责：
 * 1. 将 List&lt;LianyiResultNew&gt; 转换为单一的 JTS Geometry（Polygon With Holes 或 MultiPolygon）。
 * 2. 将 TaskParam（含多个 Cata）转换为 JTS Geometry（MultiPolygon 或 Polygon）。
 * 3. 提供几何相交、面积计算等通用运算的封装。
 * </p>
 * <p>
 * 设计原则：
 * 1. 所有 Geometry 创建都通过注入的 GeometryFactory，确保坐标精度一致。
 * 2. 正确处理 Polygon With Holes：外轮廓（exterior ring）+ 洞列表（interior rings）。
 * 3. 支持 MultiPolygon：当涟漪模型返回多个分离区域时，合并为 MultiPolygon。
 * 4. 接口与实现分离，便于后续替换几何引擎（如升级 JTS 版本或引入 GeoTools）。
 * </p>
 */
public interface GeometryService {

    /**
     * 将涟漪模型计算结果列表转换为统一的 JTS Geometry。
     * <p>
     * 处理逻辑：
     * 1. 遍历每个 LianyiResultNew：
     *    - 外轮廓（lianyiPoints）→ Polygon 的外环
     *    - 洞列表（excludeGeos）→ Polygon 的内环列表
     *    - 组合为带洞的 Polygon
     * 2. 如果结果列表只有一个元素，返回该 Polygon。
     * 3. 如果结果列表有多个元素，将所有 Polygon 合并为 MultiPolygon。
     * 4. 如果结果列表为空，返回空的 Geometry（GeometryCollection.EMPTY）。
     * </p>
     *
     * @param lianyiResults 涟漪模型计算结果列表
     * @return 统一的 JTS Geometry，可能是 Polygon、MultiPolygon 或空 Geometry
     */
    Geometry convertRippleResultsToGeometry(List<LianyiResultNew> lianyiResults);

    /**
     * 将单个任务参数转换为 JTS Geometry。
     * <p>
     * 处理逻辑：
     * 1. 遍历 TaskParam.catas，每个 Cata 的四个角点构成一个四边形 Polygon。
     * 2. 如果只有一个 Cata，返回单个 Polygon。
     * 3. 如果有多个 Cata，返回 MultiPolygon。
     * 4. 如果 catas 为空，返回空的 Geometry。
     * </p>
     * <p>
     * 角点顺序处理：
     * Cata 提供四个角点（lb, rt, lt, rb），需要按正确的顺序（顺时针或逆时针）构造 Polygon。
     * 通常顺序为：lb → rb → rt → lt → lb（顺时针）。
     * 首尾点自动闭合，无需调用方确保。
     * </p>
     *
     * @param taskParam 任务参数
     * @return 该任务覆盖区域的 JTS Geometry
     */
    Geometry convertTaskToGeometry(TaskParam taskParam);

    /**
     * 计算两个几何图形的相交区域。
     * <p>
     * 封装 JTS Geometry.intersection()，提供空值检查和类型安全。
     * 如果任一输入为 null 或空，返回空 Geometry。
     * </p>
     *
     * @param g1 第一个几何图形
     * @param g2 第二个几何图形
     * @return 相交区域，可能为空 Geometry
     */
    Geometry intersect(Geometry g1, Geometry g2);

    /**
     * 计算几何图形的面积。
     * <p>
     * 封装 JTS Geometry.getArea()，提供空值安全。
     * 如果输入为 null 或空，返回 0.0。
     * </p>
     *
     * @param geometry 几何图形
     * @return 面积，null 或空时返回 0.0
     */
    double calculateArea(Geometry geometry);

    /**
     * 判断两个几何图形是否相交。
     * <p>
     * 封装 JTS Geometry.intersects()，提供空值安全。
     * 如果任一输入为 null 或空，返回 false。
     * </p>
     *
     * @param g1 第一个几何图形
     * @param g2 第二个几何图形
     * @return 是否相交
     */
    boolean isIntersect(Geometry g1, Geometry g2);

}
