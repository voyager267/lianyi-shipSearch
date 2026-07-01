package com.ripple.planner.config;

import com.fasterxml.jackson.databind.module.SimpleModule;
import com.ripple.planner.jackson.GeometryJsonSerializer;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * JTS 拓扑套件配置类。
 * <p>
 * 负责创建和配置 JTS GeometryFactory，作为 Spring Bean 注入到各个 Service 中。
 * 统一 GeometryFactory 的创建，确保全系统使用一致的坐标精度和几何工厂实例。
 * </p>
 * <p>
 * 配置说明：
 * 1. PrecisionModel：使用 FIXED 精度模型，坐标精度为 1e-9（约等于经纬度的 1e-9 度，约 0.1 纳米级）。
 *    这对于卫星搜索任务的地理坐标来说足够精确，同时避免浮点精度问题。
 *    后续如果处理大范围区域，可以调整为更合适的精度。
 * 2. SRID：当前未显式设置 SRID（Spatial Reference System Identifier）。
 *    因为项目第一版在平面坐标系下使用 JTS（经纬度近似为平面坐标），
 *    如果需要支持精确的球面几何，后续可引入 GeoTools 并设置 SRID=4326（WGS-84）。
 * 3. 单例模式：GeometryFactory 是无状态且线程安全的，使用 @Bean 默认单例作用域，
 *    避免重复创建带来的性能开销。
 * </p>
 */
@Configuration
public class JtsConfig {

    /**
     * 创建 JTS GeometryFactory Bean。
     * <p>
     * GeometryFactory 是 JTS 中创建所有几何对象（Point、LineString、Polygon、MultiPolygon 等）的工厂类。
     * 所有 Service 中需要通过构造函数注入该 Bean，而不是自行 new GeometryFactory()，
     * 以保证坐标精度和几何行为的一致性。
     * </p>
     *
     * @return 配置好的 GeometryFactory 实例
     */
    @Bean
    public GeometryFactory geometryFactory() {
        // 使用 FIXED 精度模型，坐标值会被舍入到最接近的 1e-9 倍数
        // 这对于经纬度坐标足够精确，同时减少几何运算中的浮点误差
        PrecisionModel precisionModel = new PrecisionModel(PrecisionModel.FIXED);
        return new GeometryFactory(precisionModel);
    }

    /**
     * 注册 JTS Geometry 的 Jackson 序列化模块。
     * <p>
     * Spring Boot 会自动检测所有 {@link com.fasterxml.jackson.databind.Module} 类型的 Bean，
     * 并将其注册到 ObjectMapper 中。本模块将 JTS Geometry 对象序列化为标准 GeoJSON 格式，
     * 解决默认 Jackson 序列化输出大量内部属性（如 envelopeInternal、factory、SRID 等）的问题。
     * </p>
     *
     * @return 配置好的 Jackson SimpleModule 实例
     */
    @Bean
    public SimpleModule jtsGeometryModule() {
        SimpleModule module = new SimpleModule("JtsGeometryModule");
        module.addSerializer(Geometry.class, new GeometryJsonSerializer());
        return module;
    }

}
