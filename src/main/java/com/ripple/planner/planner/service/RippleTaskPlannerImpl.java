package com.ripple.planner.planner.service;

import com.ripple.planner.model.LianyiQueryParam;
import com.ripple.planner.model.LianyiResultNew;
import com.ripple.planner.model.TaskParam;
import com.ripple.planner.planner.model.*;
import com.ripple.planner.planner.util.JtsGeometryUtil;
import com.ripple.planner.service.LianyiModelService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.locationtech.jts.geom.Geometry;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 涟漪任务规划器实现类。
 * <p>
 * 基于贪心策略实现动态卫星搜索任务规划的核心闭环逻辑。
 * 规划循环包含以下步骤：
 * 1. 根据 historyTasks 构造 LianyiQueryParam，调用已有涟漪模型。
 * 2. 将涟漪结果转换为 JTS Geometry。
 * 3. 获取与 Ripple 相交的 Grid 列表。
 * 4. 计算每个 Grid 的概率。
 * 5. 计算所有候选任务（AccessService）。
 * 6. 对候选任务评分（TaskScoreService）。
 * 7. 选择 Score 最高的任务。
 * 8. 如果最高 Score == 0，终止规划。
 * 9. 更新 PlannerState（historyTasks、candidateTasks、taskSequence、currentTime）。
 * 10. 继续循环，直到满足终止条件。
 * </p>
 * <p>
 * 设计说明：
 * 1. 所有依赖通过构造函数注入，便于单元测试时 Mock。
 * 2. 规划循环使用 while(true)，但有明确的终止条件：
 *    - candidateTasks 为空
 *    - 所有候选任务 score == 0
 *    - 达到最大循环次数（安全保护）
 * 3. 每轮循环的 Ripple 区域都是基于更新后的 historyTasks 重新计算，体现"动态"特性。
 * 4. 使用 PlannerState 维护可变状态，TaskSequenceResult 收集最终结果。
 * 5. 日志详细记录每轮循环的关键数据，便于问题定位。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RippleTaskPlannerImpl implements RippleTaskPlanner {

    /**
     * 时间格式化器，用于将 LocalDateTime 转换为涟漪模型要求的 String 格式。
     */
    private static final DateTimeFormatter DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * 最大规划循环次数。
     * <p>
     * 安全保护机制，防止因代码缺陷或异常数据导致无限循环。
     * 默认值 10000 足够应对大多数场景（候选任务数通常在数百到数千级别）。
     * 后续可通过配置化方式动态调整。
     * </p>
     */
    private static final int MAX_PLANNING_ITERATIONS = 10000;

    // ==================== 依赖注入 ====================

    private final LianyiModelService lianyiModelService;
    private final GeometryService geometryService;
    private final GridService gridService;
    private final ProbabilityService probabilityService;
    private final AccessService accessService;
    private final TaskScoreService taskScoreService;

    /**
     * 执行任务规划。
     * <p>
     * 实现步骤：
     * 1. 初始化 PlannerState 和 TaskSequenceResult。
     * 2. 进入 while 规划循环。
     * 3. 每轮循环执行 Step1 ~ Step9。
     * 4. 满足终止条件时退出循环，封装结果并返回。
     * </p>
     *
     * @param request 规划请求
     * @return 规划结果
     */
    @Override
    public TaskSequenceResult plan(PlanningRequest request) {
        // ========== 步骤 0：参数校验与初始化 ==========
        if (request == null) {
            log.error("规划请求为空");
            return createEmptyResult("规划请求为空");
        }
        if (request.getCandidateTasks() == null || request.getCandidateTasks().isEmpty()) {
            log.warn("候选任务池为空，无需规划");
            return createEmptyResult("候选任务池为空");
        }
        if (request.getStartTime() == null) {
            log.error("规划起始时间为空");
            return createEmptyResult("规划起始时间为空");
        }

        // 初始化规划状态
        PlannerState state = new PlannerState();
        state.setCurrentTime(request.getStartTime());
        state.setHistoryTasks(new ArrayList<>());
        state.setCandidateTasks(new ArrayList<>(request.getCandidateTasks()));
        state.setTaskSequence(new ArrayList<>());

        // 初始化结果对象
        TaskSequenceResult result = new TaskSequenceResult();
        result.setTaskSequence(new ArrayList<>());
        result.setRecords(new ArrayList<>());
        result.setTotalScore(0.0);
        result.setExecutionCount(0);

        log.info("开始任务规划：中心=({}, {}), 目标={}, 起始时间={}, 候选任务数={}",
                request.getCenterLon(), request.getCenterLat(), request.getEntityID(),
                request.getStartTime(), state.getCandidateTasks().size());

        int iteration = 0;

        // ========== 规划主循环 ==========
        while (iteration < MAX_PLANNING_ITERATIONS) {
            iteration++;
            log.debug("===== 规划循环第 {} 轮开始 =====", iteration);

            // ---------- Step 1：调用已有涟漪模型 ----------
            LianyiQueryParam queryParam = buildLianyiQueryParam(request, state);
            List<LianyiResultNew> rippleResults;
            try {
                rippleResults = lianyiModelService.calculate(queryParam);
            } catch (Exception e) {
                log.error("涟漪模型调用异常，终止规划。error={}", e.getMessage(), e);
                result.setMessage("规划异常：涟漪模型调用失败 - " + e.getMessage());
                break;
            }

            if (rippleResults == null || rippleResults.isEmpty()) {
                log.info("涟漪模型返回空结果，终止规划");
                result.setMessage("规划完成：涟漪模型返回空区域");
                break;
            }

            // ---------- Step 2：将 Ripple 区域转换为 JTS Geometry ----------
            Geometry rippleGeometry = geometryService.convertRippleResultsToGeometry(rippleResults);
            if (JtsGeometryUtil.isEmptyOrInvalid(rippleGeometry)) {
                log.info("Ripple 几何区域为空，终止规划");
                result.setMessage("规划完成：Ripple 几何区域为空");
                break;
            }

            // 计算 Ripple 总面积（使用涟漪模型返回的 area 累加，与模型内部保持一致）
            double rippleArea = rippleResults.stream()
                    .filter(r -> r != null)
                    .mapToDouble(LianyiResultNew::getArea)
                    .sum();
            if (rippleArea <= 0) {
                // 如果涟漪模型未返回有效面积，退化为几何计算
                rippleArea = geometryService.calculateArea(rippleGeometry);
                log.debug("涟漪模型返回面积无效，使用几何计算面积：{}", rippleArea);
            }

            log.debug("第 {} 轮 Ripple 结果：结果数={}, 几何类型={}, 面积={}",
                    iteration, rippleResults.size(), rippleGeometry.getGeometryType(), rippleArea);

            // ---------- Step 3：获取 Ripple 覆盖的 Grid ----------
            List<Grid> rippleGrids = gridService.getGridsIntersectingRipple(rippleGeometry);
            if (rippleGrids.isEmpty()) {
                log.info("Ripple 区域无相交网格，终止规划");
                result.setMessage("规划完成：Ripple 区域无相交网格");
                break;
            }
            log.debug("第 {} 轮相交网格数：{}", iteration, rippleGrids.size());

            // ---------- Step 4：计算每个 Grid 的概率 ----------
            probabilityService.calculateProbabilities(rippleGrids, rippleGeometry, rippleArea);

            // ---------- Step 5：计算所有候选 Task ----------
            List<TaskCandidate> candidates = accessService.calculateAccess(
                    state.getCandidateTasks(),
                    rippleGrids,
                    rippleGeometry,
                    state.getCurrentTime(),
                    geometryService
            );
            if (candidates.isEmpty()) {
                log.info("第 {} 轮无候选任务，终止规划", iteration);
                result.setMessage("规划完成：无可用候选任务");
                break;
            }
            log.debug("第 {} 轮候选任务数：{}", iteration, candidates.size());

            // ---------- Step 6：任务评分 ----------
            taskScoreService.scoreTasks(candidates, state.getCurrentTime());

            // ---------- Step 7：选择 Score 最高的任务 ----------
            TaskCandidate bestCandidate = candidates.stream()
                    .max(Comparator.comparingDouble(TaskCandidate::getScore))
                    .orElse(null);

            if (bestCandidate == null) {
                log.info("第 {} 轮无法确定最优候选，终止规划", iteration);
                result.setMessage("规划完成：无法确定最优候选任务");
                break;
            }

            double bestScore = bestCandidate.getScore();
            log.debug("第 {} 轮最优候选：taskID={}, score={}",
                    iteration,
                    bestCandidate.getTask() != null ? bestCandidate.getTask().getTaskID() : "null",
                    bestScore);

            // ---------- Step 8：如果最高 Score == 0，结束规划 ----------
            if (bestScore <= 0.0) {
                log.info("第 {} 轮最优任务评分不大于 0（score={}），终止规划", iteration, bestScore);
                result.setMessage("规划完成：所有候选任务评分均为 0，无法继续优化");
                break;
            }

            // ---------- Step 9：更新 PlannerState ----------
            TaskParam selectedTask = bestCandidate.getTask();
            if (selectedTask == null) {
                log.error("最优候选任务为 null，终止规划");
                result.setMessage("规划异常：最优候选任务为空");
                break;
            }

            // 更新状态
            state.getHistoryTasks().add(selectedTask);
            state.getCandidateTasks().remove(selectedTask);
            state.getTaskSequence().add(selectedTask);
            state.setCurrentTime(bestCandidate.getAccessTime());

            // 更新结果
            result.getTaskSequence().add(selectedTask);
            result.getRecords().add(new SearchRecord(selectedTask, bestCandidate.getAccessTime()));
            result.setTotalScore(result.getTotalScore() + bestScore);

            log.info("第 {} 轮选中任务：taskID={}, satellite={}, accessTime={}, score={}, 剩余候选={}",
                    iteration, selectedTask.getTaskID(), selectedTask.getSatellite(),
                    bestCandidate.getAccessTime(), bestScore, state.getCandidateTasks().size());

            // 额外终止条件：候选任务池已耗尽
            if (state.getCandidateTasks().isEmpty()) {
                log.info("候选任务池已耗尽，规划完成");
                result.setMessage("规划完成：候选任务池已耗尽");
                break;
            }
        }

        // 检查是否因达到最大循环次数而终止
        if (iteration >= MAX_PLANNING_ITERATIONS) {
            log.warn("达到最大规划循环次数（{}），强制终止", MAX_PLANNING_ITERATIONS);
            if (result.getMessage() == null) {
                result.setMessage("规划完成：达到最大循环次数限制");
            }
        }

        // 封装最终统计信息
        result.setExecutionCount(result.getTaskSequence().size());
        if (result.getMessage() == null) {
            result.setMessage("规划成功：共选出 " + result.getExecutionCount() + " 个任务");
        }

        log.info("规划结束：共执行 {} 轮，选出 {} 个任务，总评分={}",
                iteration, result.getExecutionCount(), result.getTotalScore());

        return result;
    }

    /**
     * 构造涟漪模型查询参数。
     * <p>
     * 根据 PlanningRequest 中的静态参数和 PlannerState 中的动态参数，
     * 构造每轮循环所需的 LianyiQueryParam。
     * </p>
     *
     * @param request 规划请求（静态参数）
     * @param state   规划状态（动态参数）
     * @return 涟漪模型查询参数
     */
    private LianyiQueryParam buildLianyiQueryParam(PlanningRequest request, PlannerState state) {
        LianyiQueryParam param = new LianyiQueryParam();
        param.setCenterLon(request.getCenterLon());
        param.setCenterLat(request.getCenterLat());
        param.setEntityID(request.getEntityID());
        param.setTargetLastFindTime(request.getTargetLastFindTime());
        param.setSpeed(request.getTargetSpeed());

        // scoutTime 使用当前规划时间格式化后的字符串
        param.setScoutTime(state.getCurrentTime().format(DATE_TIME_FORMATTER));

        // taskIDs 使用当前已执行任务列表的副本
        // 使用 new ArrayList 创建副本，避免涟漪模型修改我们的内部状态
        param.setTaskIDs(new ArrayList<>(state.getHistoryTasks()));

        return param;
    }

    /**
     * 创建空的规划结果。
     * <p>
     * 辅助方法，在规划因参数错误无法开始时返回一个合法的空结果对象，
     * 避免返回 null 引发调用方 NPE。
     * </p>
     *
     * @param message 结果描述信息
     * @return 空的 TaskSequenceResult
     */
    private TaskSequenceResult createEmptyResult(String message) {
        TaskSequenceResult result = new TaskSequenceResult();
        result.setTaskSequence(new ArrayList<>());
        result.setRecords(new ArrayList<>());
        result.setTotalScore(0.0);
        result.setExecutionCount(0);
        result.setMessage(message);
        return result;
    }

}
