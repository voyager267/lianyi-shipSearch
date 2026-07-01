package com.ripple.planner.planner.service;

import com.ripple.planner.model.TaskParam;
import com.ripple.planner.planner.model.Grid;
import com.ripple.planner.planner.model.TaskCandidate;
import org.locationtech.jts.geom.Geometry;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 卫星访问服务接口。
 * <p>
 * 负责计算在给定时间、给定网格上，哪些卫星任务可以执行访问（即卫星过境覆盖该网格）。
 * 当前版本为占位接口，后续将接入 SGP4 轨道传播模型进行精确计算。
 * </p>
 * <p>
 * 核心职责：
 * 1. 根据候选任务池（candidateTasks）、当前 Ripple 覆盖的网格、以及当前时间，生成可行的任务候选列表。
 * 2. 判断每个候选任务是否能在指定时间访问指定网格。
 * 3. 计算任务的实际覆盖区域（coverage），即任务几何与 Ripple 几何的交集。
 * </p>
 * <p>
 * 设计原则：
 * 1. 接口占位：当前版本不实现真实的轨道力学计算，只提供框架和默认行为。
 *    后续接入 SGP4 时，只需替换实现类，无需修改 RippleTaskPlanner。
 * 2. 输入与输出解耦：输入是任务池和网格，输出是 TaskCandidate 列表。
 *    不直接修改 PlannerState，保持无状态设计。
 * 3. 时间驱动：所有访问计算都基于 currentTime，支持时间窗口筛选。
 * </p>
 * <p>
 * TODO（后续实现）：
 * 1. 集成 SGP4 轨道传播库，根据 TLE（Two-Line Element）数据计算卫星位置。
 * 2. 结合卫星传感器视场（FOV）和姿态，精确计算地面覆盖区域。
 * 3. 考虑卫星资源约束（电量、存储、数传窗口），过滤不可行的任务。
 * 4. 引入多卫星协同调度，避免任务冲突。
 * </p>
 */
public interface AccessService {

    /**
     * 计算所有候选任务的访问机会。
     * <p>
     * 当前版本的简化逻辑：
     * 1. 遍历 candidateTasks 中的每个 TaskParam。
     * 2. 遍历 rippleGrids 中的每个 Grid。
     * 3. 使用 GeometryService 判断任务覆盖区域（由 TaskParam.catas 转换）是否与 Grid.geometry 相交。
     * 4. 如果相交，创建 TaskCandidate，其中 coverage = intersection(taskGeometry, grid.geometry)。
     *    accessTime 为 TaskParam.scoutTime 解析后的 LocalDateTime。
     * 5. 返回所有 TaskCandidate。
     * </p>
     * <p>
     * 后续接入 SGP4 后的逻辑：
     * 1. 根据 satellite 标识获取对应的 TLE 数据。
     * 2. 使用 SGP4 计算卫星在 currentTime 之后的轨道位置。
     * 3. 结合传感器参数，计算地面覆盖条带（swath）。
     * 4. 判断 swath 是否与 Grid.geometry 相交，并计算精确的访问时间窗口。
     * </p>
     *
     * @param candidateTasks 候选任务池（来自 PlannerState.candidateTasks）
     * @param rippleGrids    当前 Ripple 覆盖的网格列表（已含 probability）
     * @param rippleGeometry 当前 Ripple 的 JTS Geometry
     * @param currentTime    当前规划时间
     * @param geometryService 几何服务，用于任务几何转换和相交计算
     * @return 可行的任务候选列表。如果无候选或输入为空，返回空列表。
     */
    List<TaskCandidate> calculateAccess(
            List<TaskParam> candidateTasks,
            List<Grid> rippleGrids,
            Geometry rippleGeometry,
            LocalDateTime currentTime,
            GeometryService geometryService
    );

}
