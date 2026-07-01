package com.ripple.planner.planner.service;

import com.ripple.planner.planner.model.Grid;
import org.locationtech.jts.geom.Geometry;

import java.util.List;

/**
 * 网格服务接口。
 * <p>
 * 负责管理全球（或关注区域）网格，并提供基于空间关系的网格查询能力。
 * 每个 Grid 是一个空间单元，携带 geometry 和 probability 信息。
 * </p>
 * <p>
 * 核心职责：
 * 1. 网格数据管理：维护全局网格集合的加载、初始化和存储。
 * 2. 空间查询：根据给定的 Ripple 几何区域，快速筛选出与之相交的所有 Grid。
 * 3. 网格访问：提供按 ID 查询单个 Grid 的能力（可选，当前版本主要用于后续扩展）。
 * </p>
 * <p>
 * 设计原则：
 * 1. 网格数据源的解耦：本接口不关心网格数据从何而来（内存生成、数据库、文件、外部服务）。
 *    实现类负责具体的加载逻辑。
 * 2. 空间查询使用 JTS Geometry.intersects()，确保几何精度。
 * 3. 返回的 Grid 列表是副本或新列表，避免调用方修改内部网格状态。
 *    但 Grid 对象本身是可变的（probability 字段会被 ProbabilityService 修改）。
 * </p>
 */
public interface GridService {

    /**
     * 获取与给定 Ripple 区域相交的所有网格。
     * <p>
     * 实现逻辑：
     * 1. 遍历全局网格集合（或空间索引）。
     * 2. 对每个 Grid，使用 Geometry.intersects(rippleGeometry) 判断是否相交。
     * 3. 收集所有相交的 Grid 并返回。
     * </p>
     * <p>
     * 性能说明：
     * 当前第一版采用全量遍历 + JTS intersects 判断。
     * 如果网格数量很大（如全球 0.01 度分辨率，约 3600 万网格），遍历性能会成为瓶颈。
     * 后续优化方向：
     * - 引入 JTS STRtree 空间索引，将查询复杂度从 O(n) 降为 O(log n + k)。
     * - 使用 H3 等离散全局网格系统，通过哈希索引快速定位。
     * </p>
     *
     * @param rippleGeometry Ripple 区域的 JTS Geometry，由 GeometryService 转换得到
     * @return 与 Ripple 相交的 Grid 列表。如果无相交网格或 rippleGeometry 为空，返回空列表。
     */
    List<Grid> getGridsIntersectingRipple(Geometry rippleGeometry);

    /**
     * 获取系统中所有网格的列表。
     * <p>
     * 主要用于调试、统计和测试。在正式规划流程中，应优先使用
     * {@link #getGridsIntersectingRipple(Geometry)} 进行空间过滤，减少计算量。
     * </p>
     *
     * @return 全局网格列表的副本或只读视图
     */
    List<Grid> getAllGrids();

}
