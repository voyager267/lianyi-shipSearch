package com.ripple.planner.model;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 任务参数中的四边形区域定义（Cata = Catalogue Area）。
 * <p>
 * 该类描述一个卫星任务的覆盖范围，使用四个角点定义一个四边形。
 * 角点命名规则：
 * - lb: left bottom，左下角（西南角）
 * - rt: right top，右上角（东北角）
 * - lt: left top，左上角（西北角）
 * - rb: right bottom，右下角（东南角）
 * </p>
 * <p>
 * 设计说明：
 * 1. 每个角点使用独立的 latitude / longitude 字段，而非嵌套对象。
 *    原因：与已有涟漪模型的接口契约保持一致，避免引入额外的嵌套序列化层级。
 * 2. 字段使用 Double（包装类型）而非 double（基本类型）。
 *    原因：与已有数据模型保持一致，允许某些场景下字段为 null。
 * 3. 该类作为 TaskParam 的内部组成部分，描述单个任务的覆盖几何范围。
 * </p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "四边形区域定义（Cata = Catalogue Area），描述一个卫星任务的覆盖范围")
public class Cata {

    /**
     * 左下角纬度 (Left Bottom Latitude)。
     */
    @Schema(description = "左下角纬度 (Left Bottom Latitude)", example = "39.8", requiredMode = Schema.RequiredMode.REQUIRED)
    private Double catalbLatitude;

    /**
     * 左下角经度 (Left Bottom Longitude)。
     */
    @Schema(description = "左下角经度 (Left Bottom Longitude)", example = "116.3", requiredMode = Schema.RequiredMode.REQUIRED)
    private Double catalbLongitude;

    /**
     * 右上角纬度 (Right Top Latitude)。
     */
    @Schema(description = "右上角纬度 (Right Top Latitude)", example = "40.0", requiredMode = Schema.RequiredMode.REQUIRED)
    private Double catartLatitude;

    /**
     * 右上角经度 (Right Top Longitude)。
     */
    @Schema(description = "右上角经度 (Right Top Longitude)", example = "116.5", requiredMode = Schema.RequiredMode.REQUIRED)
    private Double catartLongitude;

    /**
     * 左上角纬度 (Left Top Latitude)。
     */
    @Schema(description = "左上角纬度 (Left Top Latitude)", example = "40.0", requiredMode = Schema.RequiredMode.REQUIRED)
    private Double cataltLatitude;

    /**
     * 左上角经度 (Left Top Longitude)。
     */
    @Schema(description = "左上角经度 (Left Top Longitude)", example = "116.3", requiredMode = Schema.RequiredMode.REQUIRED)
    private Double cataltLongitude;

    /**
     * 右下角纬度 (Right Bottom Latitude)。
     */
    @Schema(description = "右下角纬度 (Right Bottom Latitude)", example = "39.8", requiredMode = Schema.RequiredMode.REQUIRED)
    private Double catarbLatitude;

    /**
     * 右下角经度 (Right Bottom Longitude)。
     */
    @Schema(description = "右下角经度 (Right Bottom Longitude)", example = "116.5", requiredMode = Schema.RequiredMode.REQUIRED)
    private Double catarbLongitude;

}
