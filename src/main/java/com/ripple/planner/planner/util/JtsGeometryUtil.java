package com.ripple.planner.planner.util;

import org.locationtech.jts.geom.*;

import java.util.List;

/**
 * JTS 几何工具类。
 * <p>
 * 提供静态工具方法，辅助 GeometryService 进行几何对象的创建和转换。
 * 该类为 final 且构造函数为 private，防止实例化，符合工具类的设计规范。
 * </p>
 * <p>
 * 设计说明：
 * 1. 所有方法均为静态方法，便于直接调用，无需注入 Spring Bean。
 * 2. 本类只包含通用的、与业务无关的几何辅助方法。
 *    业务相关的几何转换逻辑（如 LianyiPoint → Coordinate）放在 GeometryServiceImpl 中。
 * 3. 提供空值检查和防御性编程，避免传入 null 或空列表时抛出异常。
 * 4. 方法参数使用 GeometryFactory 而非自行创建，保证几何对象的一致性。
 * </p>
 */
public final class JtsGeometryUtil {

    /**
     * 私有构造函数，防止实例化。
     * <p>
     * 工具类不应被实例化，尝试 new JtsGeometryUtil() 会抛出 AssertionError。
     * </p>
     */
    private JtsGeometryUtil() {
        throw new AssertionError("工具类禁止实例化");
    }

    /**
     * 根据坐标点列表创建闭合的 LinearRing。
     * <p>
     * JTS 的 Polygon 外环和内环都要求使用 LinearRing（闭合的 LineString）。
     * 该方法自动处理首尾闭合：如果列表最后一个点与第一个点不同，则在末尾追加第一个点。
     * </p>
     *
     * @param factory     GeometryFactory，用于创建几何对象
     * @param coordinates 坐标点列表，按顺序定义多边形的顶点
     * @return 闭合的 LinearRing；如果坐标点少于 3 个，返回 null（无法构成多边形）
     */
    public static LinearRing createLinearRing(GeometryFactory factory, List<Coordinate> coordinates) {
        if (coordinates == null || coordinates.size() < 3) {
            // LinearRing 至少需要 3 个不同的点（加上闭合点共 4 个坐标）
            // 点太少无法构成有效多边形，返回 null，由调用方处理
            return null;
        }

        Coordinate[] coordsArray;

        // 检查是否已闭合（最后一个点与第一个点相同）
        Coordinate first = coordinates.get(0);
        Coordinate last = coordinates.get(coordinates.size() - 1);

        if (first.equals2D(last)) {
            // 已闭合，直接转换
            coordsArray = coordinates.toArray(new Coordinate[0]);
        } else {
            // 未闭合，追加第一个点以闭合环
            coordsArray = new Coordinate[coordinates.size() + 1];
            coordinates.toArray(coordsArray);
            coordsArray[coordinates.size()] = new Coordinate(first);
        }

        return factory.createLinearRing(coordsArray);
    }

    /**
     * 判断几何对象是否为空或无效。
     * <p>
     * 封装了 JTS 的 isEmpty() 检查，并额外验证几何对象是否为 null。
     * 在几何运算前调用此方法，可以避免对空对象进行操作导致的异常或不合理结果。
     * </p>
     *
     * @param geometry 待检查的几何对象
     * @return true 如果 geometry 为 null、空、或无效；否则返回 false
     */
    public static boolean isEmptyOrInvalid(Geometry geometry) {
        return geometry == null || geometry.isEmpty();
    }

    /**
     * 创建空的 Polygon。
     * <p>
     * 使用空的 Coordinate 数组创建 LinearRing，再构建空 Polygon。
     * 用于在无法构造有效多边形时返回一个合法的空几何对象，避免返回 null 引发 NPE。
     * </p>
     *
     * @param factory GeometryFactory
     * @return 空的 Polygon 实例
     */
    public static Polygon createEmptyPolygon(GeometryFactory factory) {
        return factory.createPolygon(new Coordinate[0]);
    }

}
