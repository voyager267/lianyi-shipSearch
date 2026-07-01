package com.ripple.planner.planner.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.locationtech.jts.geom.Geometry;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 卫星访问任务。
 * <p>
 * 由 AccessService 根据轨道计算动态生成的访问机会。
 * 每个 AccessTask 表示一颗卫星在一次过境中对特定区域的覆盖访问。
 * </p>
 * <p>
 * 与 TaskParam 的区别：
 * - TaskParam 是外部系统输入的静态任务定义，使用 String 类型的时间和 Cata 四边形描述覆盖。
 * - AccessTask 是 AccessService 动态计算出的访问机会，使用 LocalDateTime 和精确的 JTS Geometry 描述覆盖。
 * - AccessTask 不直接用于涟漪模型，需要在 Planner 中被转换为 TaskParam 后写入 historyTasks。
 * </p>
 * <p>
 * 核心字段：
 * - accessId: 访问唯一标识，由 AccessService 生成。
 * - satellite: 执行访问的卫星名称（如 GF1, GF3 等）。
 * - accessTime: 精确到秒级的访问时间窗口起始时间。
 * - coverage: 该访问的实际地面覆盖区域（JTS Geometry），可能是不规则多边形。
 * - grids: 该访问覆盖的 Grid 列表，用于概率聚合和评分。
 * </p>
 * <p>
 * 设计原则：
 * 1. 一个 AccessTask 可以覆盖多个 Grid，体现卫星一次过境的广域覆盖能力。
 * 2. coverage 使用 JTS Geometry，支持任意形状，不局限于四边形。
 * 3. 该类是 AccessService 的输出，也是 TaskScoreService 和 RippleTaskPlanner 的核心输入。
 * </p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AccessTask {

    /**
     * 访问唯一标识符。
     * <p>
     * 由 AccessService 生成，在同一规划会话内必须唯一。
     * 格式由 AccessService 实现决定（如 UUID、卫星名_时间戳、或自定义编码）。
     * </p>
     */
    private String accessId;

    /**
     * 执行该访问的卫星标识。
     * <p>
     * 例如：GF1（高分一号）、GF3（高分三号）、HJ2（环境二号）、ZY1（资源一号）等。
     * 由 AccessService 根据轨道数据和卫星资源池动态选择。
     * </p>
     */
    private String satellite;

    /**
     * 访问时间窗口的起始时间。
     * <p>
     * 表示卫星开始覆盖该区域的精确时刻。
     * 使用 LocalDateTime 便于时间运算和比较。
     * </p>
     */
    private LocalDateTime accessTime;

    /**
     * 该访问的实际地面覆盖区域。
     * <p>
     * 由 AccessService 根据卫星轨道、传感器视场（FOV）和姿态计算得出的精确覆盖多边形。
     * 使用 JTS Geometry 表示，支持 Polygon、MultiPolygon 等任意形状。
     * </p>
     * <p>
     * 注意：coverage 的坐标系应与 Ripple 模型和 Grid 的坐标系一致（WGS-84 经纬度）。
     * </p>
     */
    private Geometry coverage;

    /**
     * 该访问覆盖的网格列表。
     * <p>
     * 包含与该访问 coverage 相交的所有 Grid。
     * TaskScoreService 根据这些 Grid 的 probability 计算任务的综合概率得分。
     * </p>
     * <p>
     * 为什么保留 grids 而非仅从 coverage 计算：
     * 1. Grid 携带 probability 信息，是评分的关键输入。
     * 2. 明确记录访问覆盖了哪些概率单元，便于后续分析和审计。
     * 3. 避免在评分阶段重复进行 Grid 与 coverage 的空间匹配计算。
     * </p>
     */
    private List<Grid> grids;

}
