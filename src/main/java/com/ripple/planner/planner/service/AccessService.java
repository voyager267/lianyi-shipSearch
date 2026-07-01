package com.ripple.planner.planner.service;

import com.ripple.planner.planner.model.AccessTask;
import com.ripple.planner.planner.model.Grid;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 卫星访问服务接口。
 * <p>
 * 负责根据给定的网格集合和时间窗口，动态计算卫星访问机会。
 * 这是 Planner 与卫星轨道计算模块之间的核心契约，Planner 永远不应该直接依赖任何具体实现。
 * </p>
 * <p>
 * 核心职责：
 * 1. 接收 Ripple 覆盖的 Grid 集合和规划时间窗口 [startTime, endTime]。
 * 2. 基于轨道力学（当前为 Mock，后续接入 SGP4）计算每颗卫星对每个 Grid 的访问机会。
 * 3. 返回 List&lt;AccessTask&gt;，每个 AccessTask 描述一次具体的卫星过境覆盖。
 * </p>
 * <p>
 * 设计原则：
 * 1. 输入是 Grid 集合，不是 Task：体现"Task 由 AccessService 动态生成"的核心思想。
 * 2. 输出是 AccessTask，不是 TaskParam：AccessTask 携带精确的 JTS Geometry 和 LocalDateTime，
 *    比 TaskParam 的 String 时间和 Cata 四边形更精确，更适合轨道计算结果。
 * 3. 无状态：实现类应该是无状态的，每次调用基于全新的输入参数计算。
 * 4. 接口隔离：Planner 只依赖此接口，不依赖任何轨道计算库的具体类型。
 * </p>
 * <p>
 * 后续接入真实轨道计算时的替换方式：
 * 1. 新建 RealAccessService implements AccessService。
 * 2. 在 RealAccessService 中注入 SGP4 计算库、TLE 数据服务等。
 * 3. 使用 @Primary 或 Spring Profile 切换 Bean，RippleTaskPlanner 代码零修改。
 * </p>
 */
public interface AccessService {

    /**
     * 计算给定网格集合在时间窗口内的卫星访问机会。
     * <p>
     * 实现逻辑（真实版本）：
     * 1. 遍历输入的 grids 列表。
     * 2. 对每个 Grid，查询哪些卫星在 [startTime, endTime] 内会过境覆盖该 Grid 的区域。
     * 3. 使用 SGP4 传播轨道，精确计算卫星星下点轨迹。
     * 4. 结合传感器视场（FOV）和卫星姿态，生成地面覆盖多边形（coverage）。
     * 5. 对每个访问机会创建 AccessTask，包含 accessId、satellite、accessTime、coverage、grids。
     * 6. 聚合所有 AccessTask 并返回。
     * </p>
     * <p>
     * 实现逻辑（Mock 版本）：
     * 1. 遍历输入的 grids 列表。
     * 2. 对每个 Grid 随机生成 2~5 个访问机会。
     * 3. accessTime 在 [startTime, endTime] 内均匀随机分布。
     * 4. satellite 从预定义列表中随机选择。
     * 5. coverage 使用 Grid.geometry（简化处理）。
     * 6. 返回模拟的 AccessTask 列表。
     * </p>
     *
     * @param grids     Ripple 区域覆盖的网格集合（已计算概率）
     * @param startTime 规划时间窗口起始时间（包含）
     * @param endTime   规划时间窗口结束时间（包含）
     * @return 该时间窗口内的所有卫星访问机会列表。如果无访问机会或输入为空，返回空列表。
     */
    List<AccessTask> calculateAccess(List<Grid> grids, LocalDateTime startTime, LocalDateTime endTime);

}
