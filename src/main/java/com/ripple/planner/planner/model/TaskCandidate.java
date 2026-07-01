package com.ripple.planner.planner.model;

import com.ripple.planner.model.TaskParam;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.locationtech.jts.geom.Geometry;

import java.time.LocalDateTime;

/**
 * 候选任务。
 * <p>
 * 表示一个尚未执行、但在当前规划状态下可行的任务候选。
 * TaskCandidate 是 AccessService 的输出，也是 TaskScoreService 的输入，
 * 在规划循环中承担"任务 + 时空上下文 + 评分"的聚合角色。
 * </p>
 * <p>
 * 核心字段说明：
 * - task: 原始任务参数，包含 taskID、satellite、scoutTime、catas 等。
 * - grid: 该任务所覆盖的目标 Grid。一个任务可能覆盖多个 Grid，但当前版本为简化评分，
 *         将每个 (task, grid) 对作为一个独立的 TaskCandidate。
 *         后续可以扩展为支持一个 TaskCandidate 覆盖多个 Grid。
 * - coverage: 任务覆盖区域与当前 Ripple 的相交几何。
 *             由 GeometryService 计算，表示该任务实际能有效搜索的 Ripple 区域。
 * - accessTime: 任务的访问时间。当前版本使用 TaskParam.scoutTime 解析后的时间，
 *               后续接入 SGP4 轨道计算后，可以精确到秒级的窗口开始时间。
 * - score: 综合评分。由 TaskScoreService 根据 probability、coverage area、time weight 计算得出。
 *          score 为 0 表示该任务在当前状态下不可行或无效。
 * </p>
 * <p>
 * 设计说明：
 * 1. 将 TaskParam、Grid、Geometry、score 聚合在一个对象中，避免在多个 Service 之间传递大量分散参数。
 * 2. coverage 使用 JTS Geometry，便于后续进行面积计算、相交判断等空间运算。
 * 3. score 初始为 0.0，由 TaskScoreService 赋值。RippleTaskPlanner 根据 score 排序选择最优任务。
 * 4. 该类是规划循环中 Step5（计算候选任务）到 Step7（选择最优任务）的核心数据载体。
 * </p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TaskCandidate {

    /**
     * 原始任务参数。
     * <p>
     * 包含 taskID、satellite、scoutTime、catas 等完整信息。
     * 选中该候选后，RippleTaskPlanner 会将此 task 加入 historyTasks 和 taskSequence。
     * </p>
     */
    private TaskParam task;

    /**
     * 该候选任务关联的目标网格。
     * <p>
     * Grid 包含 geometry 和 probability，是评分的重要依据。
     * 当前版本一个 TaskCandidate 对应一个 Grid，简化评分逻辑。
     * 如果任务覆盖多个 Grid，AccessService 应生成多个 TaskCandidate。
     * </p>
     */
    private Grid grid;

    /**
     * 任务覆盖区域与当前 Ripple 的相交几何。
     * <p>
     * 计算方式：GeometryService.intersection(taskGeometry, rippleGeometry)
     * 表示该任务在当前 Ripple 中实际能搜索到的有效区域。
     * 如果 coverage 为空或面积为零，说明该任务对当前 Ripple 无贡献，score 应为 0。
     * </p>
     */
    private Geometry coverage;

    /**
     * 任务的访问/执行时间。
     * <p>
     * 由 TaskParam.scoutTime 解析得到，或后续由 SGP4 精确计算。
     * 用于计算 TimeWeight（等待时间越短，权重越高）和时序约束检查。
     * </p>
     */
    private LocalDateTime accessTime;

    /**
     * 综合评分。
     * <p>
     * 计算公式（第一版）：
     *     score = probability × effectiveCoverageArea × timeWeight
     * 其中：
     * - probability = Grid.probability（目标在该网格的概率）
     * - effectiveCoverageArea = coverage.getArea()（任务在 Ripple 中的有效覆盖面积）
     * - timeWeight = 1 / (1 + 等待时间秒数)（等待时间越长，权重越低）
     * </p>
     * <p>
     * score 为 0 表示该候选任务不可行（例如不可达、无覆盖、或概率为零）。
     * RippleTaskPlanner 选择 score 最高的任务，如果所有候选的 score 均为 0，则终止规划。
     * </p>
     */
    private double score;

}
