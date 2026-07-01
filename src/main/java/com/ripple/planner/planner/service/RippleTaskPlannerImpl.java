package com.ripple.planner.planner.service;

import com.ripple.planner.model.Cata;
import com.ripple.planner.model.LianyiQueryParam;
import com.ripple.planner.model.LianyiResultNew;
import com.ripple.planner.model.TaskParam;
import com.ripple.planner.planner.model.*;
import com.ripple.planner.planner.util.JtsGeometryUtil;
import com.ripple.planner.service.LianyiModelService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 涟漪任务规划器实现类。
 * <p>
 * 基于贪心策略实现动态卫星搜索任务规划的核心闭环逻辑。
 * 与旧版的核心区别：<strong>Planner 不再接收 candidateTasks，所有任务由 AccessService 动态生成</strong>。
 * </p>
 * <p>
 * 规划循环步骤：
 * 1. 根据 historyTasks 构造 LianyiQueryParam，调用已有涟漪模型。
 * 2. 将涟漪结果转换为 JTS Geometry。
 * 3. 获取与 Ripple 相交的 Grid 列表。
 * 4. 计算每个 Grid 的概率。
 * 5. 调用 AccessService，输入 Grid 集合和时间窗口，动态生成 List&lt;AccessTask&gt;。
 * 6. 调用 TaskScoreService 对所有 AccessTask 评分。
 * 7. 选择 Score 最高的 AccessTask。
 * 8. 如果最高 Score == 0，终止规划。
 * 9. 将选中的 AccessTask 转换为 TaskParam，加入 historyTasks，更新 currentTime。
 * 10. 将 AccessTask 加入 TaskSequenceResult，继续循环。
 * </p>
 * <p>
 * 设计说明：
 * 1. 所有依赖通过构造函数注入，便于单元测试时 Mock。
 * 2. 规划循环使用 while(true)，但有明确的终止条件：
 *    - AccessService 返回空列表
 *    - 所有 AccessTask 评分 == 0
 *    - 达到最大循环次数（安全保护）
 * 3. 每轮循环的 Ripple 区域都是基于更新后的 historyTasks 重新计算，体现"动态"特性。
 * 4. AccessTask 转换为 TaskParam 时使用 coverage 的外接矩形构造 Cata，
 *    这是必要的适配，因为涟漪模型需要 Cata 四边形描述已搜索区域。
 * 5. 时间窗口：每轮从 currentTime 开始，向后延伸 planningHour 小时，
 *    AccessService 在此窗口内搜索卫星访问机会。
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
     * </p>
     */
    private static final int MAX_PLANNING_ITERATIONS = 10;

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
     * 1. 初始化 PlannerState（currentTime + historyTasks）和 TaskSequenceResult。
     * 2. 进入 while 规划循环。
     * 3. 每轮循环执行 Step1 ~ Step9。
     * 4. 满足终止条件时退出循环，封装结果并返回。
     * </p>
     *
     * @param request 规划请求
     * @return 规划结果
     */
    @Override
    public TaskSequenceResult plan(PlannerRequest request) {
        // ========== 步骤 0：参数校验与初始化 ==========
        if (request == null) {
            log.error("规划请求为空");
            return createEmptyResult("规划请求为空");
        }
        if (request.getCurrentTime() == null) {
            log.error("规划起始时间为空");
            return createEmptyResult("规划起始时间为空");
        }
        if (request.getPlanningHour() <= 0) {
            log.error("规划时间窗口必须大于 0 小时");
            return createEmptyResult("规划时间窗口必须大于 0 小时");
        }

        // 初始化规划状态（只保留 currentTime 和 historyTasks）
        PlannerState state = new PlannerState();
        state.setCurrentTime(request.getCurrentTime());
        state.setHistoryTasks(new ArrayList<>());

        // 初始化结果对象
        TaskSequenceResult result = new TaskSequenceResult();
        result.setTaskSequence(new ArrayList<>());
        result.setRecords(new ArrayList<>());
        result.setTotalScore(0.0);
        result.setExecutionCount(0);

        log.info("开始任务规划：中心=({}, {}), 目标={}, 起始时间={}, 规划窗口={}小时",
                request.getCenterLon(), request.getCenterLat(), request.getEntityID(),
                request.getCurrentTime(), request.getPlanningHour());

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

            // 计算 Ripple 总面积
            double rippleArea = rippleResults.stream()
                    .filter(r -> r != null)
                    .mapToDouble(LianyiResultNew::getArea)
                    .sum();
            if (rippleArea <= 0) {
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

            // ---------- Step 5：调用 AccessService 动态生成访问任务 ----------
            // 时间窗口：[currentTime, currentTime + planningHour]
            java.time.LocalDateTime windowStart = state.getCurrentTime();
            java.time.LocalDateTime windowEnd = windowStart.plusHours(request.getPlanningHour());

            List<AccessTask> accessTasks = accessService.calculateAccess(rippleGrids, windowStart, windowEnd);
            if (accessTasks == null || accessTasks.isEmpty()) {
                log.info("第 {} 轮时间窗口 [{} ~ {}] 内无卫星访问机会，终止规划",
                        iteration, windowStart, windowEnd);
                result.setMessage("规划完成：时间窗口内无卫星访问机会");
                break;
            }
            log.debug("第 {} 轮 AccessService 生成访问任务数：{}", iteration, accessTasks.size());

            // ---------- Step 6：任务评分 ----------
            List<Double> scores = taskScoreService.scoreTasks(accessTasks, state.getCurrentTime());

            // ---------- Step 7：选择 Score 最高的任务 ----------
            int bestIndex = -1;
            double bestScore = 0.0;
            for (int i = 0; i < scores.size(); i++) {
                double score = scores.get(i);
                if (score > bestScore) {
                    bestScore = score;
                    bestIndex = i;
                }
            }

            if (bestIndex < 0) {
                log.info("第 {} 轮无法确定最优候选，终止规划", iteration);
                result.setMessage("规划完成：无法确定最优候选任务");
                break;
            }

            AccessTask bestTask = accessTasks.get(bestIndex);
            log.debug("第 {} 轮最优候选：accessId={}, satellite={}, accessTime={}, score={}",
                    iteration, bestTask.getAccessId(), bestTask.getSatellite(),
                    bestTask.getAccessTime(), bestScore);

            // ---------- Step 8：如果最高 Score == 0，结束规划 ----------
            if (bestScore <= 0.0) {
                log.info("第 {} 轮最优任务评分不大于 0（score={}），终止规划", iteration, bestScore);
                result.setMessage("规划完成：所有访问任务评分均为 0，无法继续优化");
                break;
            }

            // ---------- Step 9：更新 PlannerState ----------
            // 将 AccessTask 转换为 TaskParam，加入 historyTasks（涟漪模型需要）
            TaskParam historyTask = convertAccessTaskToTaskParam(bestTask);
            state.getHistoryTasks().add(historyTask);
            state.setCurrentTime(bestTask.getAccessTime());

            // 更新结果
            result.getTaskSequence().add(bestTask);
            SearchRecord record = new SearchRecord();
            record.setAccessTask(bestTask);
            record.setExecutedAt(bestTask.getAccessTime());
            record.setRippleResults(rippleResults);
            result.getRecords().add(record);
            result.setTotalScore(result.getTotalScore() + bestScore);

            log.info("第 {} 轮选中任务：accessId={}, satellite={}, accessTime={}, score={}, 已执行任务数={}",
                    iteration, bestTask.getAccessId(), bestTask.getSatellite(),
                    bestTask.getAccessTime(), bestScore, state.getHistoryTasks().size());
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
     * 根据 PlannerRequest 中的静态参数和 PlannerState 中的动态参数，
     * 构造每轮循环所需的 LianyiQueryParam。
     * </p>
     *
     * @param request 规划请求（静态参数）
     * @param state   规划状态（动态参数）
     * @return 涟漪模型查询参数
     */
    private LianyiQueryParam buildLianyiQueryParam(PlannerRequest request, PlannerState state) {
        LianyiQueryParam param = new LianyiQueryParam();
        param.setCenterLon(request.getCenterLon());
        param.setCenterLat(request.getCenterLat());
        param.setEntityID(request.getEntityID());
        param.setSpeed(request.getSpeed());

        // targetLastFindTime 从 LocalDateTime 格式化为 String
        if (request.getTargetLastFindTime() != null) {
            param.setTargetLastFindTime(request.getTargetLastFindTime().format(DATE_TIME_FORMATTER));
        }

        // scoutTime 使用当前规划时间格式化后的字符串
        param.setScoutTime(state.getCurrentTime().format(DATE_TIME_FORMATTER));

        // taskIDs 使用当前已执行任务列表的副本
        param.setTaskIDs(new ArrayList<>(state.getHistoryTasks()));

        return param;
    }

    /**
     * 将 AccessTask 转换为 TaskParam。
     * <p>
     * 这是必要的适配层：涟漪模型需要 TaskParam（含 Cata 四边形）作为历史任务输入，
     * 而 AccessService 输出的是 AccessTask（含 JTS Geometry）。
     * </p>
     * <p>
     * 转换逻辑：
     * 1. taskID = accessTask.accessId
     * 2. satellite = accessTask.satellite
     * 3. scoutTime = accessTask.accessTime 格式化为 String
     * 4. catas：从 coverage Geometry 的外接矩形（Envelope）构造一个 Cata 四边形。
     *    这是一种近似，用外接矩形代表 AccessTask 的不规则覆盖区域。
     *    后续如果涟漪模型支持任意多边形输入，可以优化此转换。
     * </p>
     *
     * @param accessTask 访问任务
     * @return 可供涟漪模型使用的 TaskParam
     */
    private TaskParam convertAccessTaskToTaskParam(AccessTask accessTask) {
        TaskParam taskParam = new TaskParam();
        taskParam.setTaskID(accessTask.getAccessId());
        taskParam.setSatellite(accessTask.getSatellite());
        taskParam.setScoutTime(accessTask.getAccessTime().format(DATE_TIME_FORMATTER));

        // 从 coverage 的 Envelope 构造 Cata 四边形
        Envelope env = accessTask.getCoverage().getEnvelopeInternal();
        Cata cata = new Cata();
        // lb: left bottom (minX, minY)
        cata.setCatalbLongitude(env.getMinX());
        cata.setCatalbLatitude(env.getMinY());
        // rb: right bottom (maxX, minY)
        cata.setCatarbLongitude(env.getMaxX());
        cata.setCatarbLatitude(env.getMinY());
        // rt: right top (maxX, maxY)
        cata.setCatartLongitude(env.getMaxX());
        cata.setCatartLatitude(env.getMaxY());
        // lt: left top (minX, maxY)
        cata.setCataltLongitude(env.getMinX());
        cata.setCataltLatitude(env.getMaxY());

        taskParam.setCatas(List.of(cata));
        return taskParam;
    }

    /**
     * 创建空的规划结果。
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
