package com.ripple.planner.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 涟漪点模型。
 * <p>
 * 这是涟漪模型输出的最基础几何单元，表示二维平面上的一个坐标点。
 * 在地理空间上下文中，x 对应经度 (Longitude)，y 对应纬度 (Latitude)。
 * </p>
 * <p>
 * 设计说明：
 * 1. 采用 Lombok 的 @Data 注解，自动生成 getter、setter、equals、hashCode、toString，减少样板代码。
 * 2. 提供 @NoArgsConstructor 和 @AllArgsConstructor，满足序列化框架（如 Jackson）的无参构造需求，同时支持全参构造。
 * 3. 字段使用基本类型 double，因为坐标值不可能为 null，且基本类型在大量几何计算时性能更好。
 * 4. 该类被 LianyiResultNew 和 ToClientGeo 引用，构成 Polygon 外轮廓与洞的数据基础。
 * </p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LianyiPoint {

    /**
     * x 坐标。
     * <p>
     * 在卫星搜索任务场景下，x 表示经度 (Longitude)，单位为度。
     * 取值范围：-180.0 到 180.0。
     * 使用 double 保证足够的精度，避免大面积区域计算时产生明显误差。
     * </p>
     */
    private double x;

    /**
     * y 坐标。
     * <p>
     * 在卫星搜索任务场景下，y 表示纬度 (Latitude)，单位为度。
     * 取值范围：-90.0 到 90.0。
     * 使用 double 保证足够的精度。
     * </p>
     */
    private double y;

}
