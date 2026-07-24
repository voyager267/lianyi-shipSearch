package com.ripple.planner.planner.model;

import com.ripple.planner.model.LianyiResultNew;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 搜索记录。
 * <p>
 * 用于记录每一次已执行访问任务的详细信息，包括任务本身、执行时间、评分，
 * 以及本轮所有候选任务（用于前端展示对比）。
 * 在规划结束后，SearchRecord 列表作为任务序列的详细日志，用于复盘和审计。
 * </p>
 * <p>
 * 与旧版的区别：
 * - 旧版引用 TaskParam（外部输入的静态任务定义）。
 * - 新版引用 AccessTask（AccessService 动态生成的访问机会）。
 * 这体现了"Task 由 AccessService 动态生成"的核心设计思想。
 * </p>
 * <p>
 * 设计说明：
 * 1. 当前版本保持最小化，记录 accessTask 和 executedAt。
 *    后续可扩展字段（如 scoreAtSelectionTime、rippleAreaAtSelectionTime）以支持更详细的分析。
 * 2. executedAt 使用 LocalDateTime，表示 Planner 模块内部使用的标准时间类型。
 * 3. 在 RippleTaskPlanner 的规划循环中，每次选中 AccessTask 后创建 SearchRecord，
 *    并加入 TaskSequenceResult 的 records 列表。
 * 4. rippleResults 保存该轮涟漪模型计算出的区域范围，用于前端展示计算过程。
 * 5. candidateTasks 保存本轮所有候选任务及评分，前端可借此对比"选中 vs 未选中"的差异。
 * </p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SearchRecord {

    /**
     * 被执行的访问任务。
     * <p>
     * 包含 accessId、satellite、accessTime、coverage、grids 等完整信息。
     * 通过引用 AccessTask 而非复制字段，保持数据一致性并减少冗余。
     * </p>
     */
    private AccessTask accessTask;

    /**
     * 任务执行时间。
     * <p>
     * 使用 LocalDateTime 表示，即该 AccessTask 的 accessTime。
     * 也是 PlannerState.currentTime 在该轮循环更新后的值。
     * 该字段标识了任务在规划序列中的实际执行时刻，用于时间线分析和冲突检测。
     * </p>
     */
    private LocalDateTime executedAt;

    /**
     * 该轮涟漪模型计算出的区域范围。
     * <p>
     * 每轮规划循环调用涟漪模型后，返回的目标可能存在区域列表。
     * 用于前端展示每增加一个任务后的计算过程区域范围。
     * </p>
     */
    private List<LianyiResultNew> rippleResults = new ArrayList<>();

    /**
     * 未加入当前选中任务时的涟漪结果。
     * <p>
     * 表示在第 N 轮规划循环中，基于当前 historyTasks（不含本轮选中的访问任务）
     * 调用涟漪模型计算出的目标可能存在区域列表。
     * 用于与 {@link #afterAddTaskRippleResults} 对比，展示加入当前任务前后
     * 涟漪区域的变化情况，辅助分析单个任务对整体搜索覆盖的贡献。
     * </p>
     */
    private List<LianyiResultNew> beforeAddTaskRippleResults = new ArrayList<>();

    /**
     * 加入当前选中任务后的涟漪结果。
     * <p>
     * 表示在第 N 轮规划循环中，将本轮选中的访问任务加入 historyTasks 后，
     * 再次调用涟漪模型计算出的目标可能存在区域列表。
     * 与 {@link #beforeAddTaskRippleResults} 形成前后对比，
     * 直观展示当前任务对缩小搜索范围、提升定位精度的影响。
     * </p>
     */
    private List<LianyiResultNew> afterAddTaskRippleResults = new ArrayList<>();

    /**
     * 本轮规划中的所有候选任务及其评分。
     * <p>
     * 每轮规划循环中，AccessService 生成 N 个候选 AccessTask，
     * TaskScoreService 对每个候选评分，Planner 从中选出得分最高的一个。
     * 此列表记录了全部候选及其得分，前端可借此：
     * </p>
     * <ul>
     *   <li>展示"本轮共考虑了哪些任务"</li>
     *   <li>对比选中任务与未选中任务的得分差异</li>
     *   <li>在地图上同时渲染所有候选 coverage 和选中 coverage</li>
     *   <li>分析评分模型是否合理（如是否遗漏了直观上更好的任务）</li>
     * </ul>
     * <p>
     * 每个 CandidateTask 的 selected 字段标识该任务是否被本轮选中。
     * 每轮有且仅有一个 selected=true。
     * </p>
     */
    private List<CandidateTask> candidateTasks = new ArrayList<>();

}
