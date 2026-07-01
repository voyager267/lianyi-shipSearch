package com.ripple.planner.controller;

import com.ripple.planner.planner.model.PlannerRequest;
import com.ripple.planner.planner.model.TaskSequenceResult;
import com.ripple.planner.planner.service.RippleTaskPlanner;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 任务规划 REST API 控制器。
 * <p>
 * 提供 HTTP 接口，供上层系统或前端调用以执行卫星搜索任务规划。
 * </p>
 * <p>
 * 接口设计：
 * - POST /api/v1/plan：接收 PlannerRequest，返回 TaskSequenceResult。
 * - GET /api/v1/health：健康检查端点，用于服务状态监控。
 * </p>
 * <p>
 * 与旧版的区别：
 * - 旧版接收 PlanningRequest（含 candidateTasks）。
 * - 新版接收 PlannerRequest（不含任何 Task 输入，所有任务由 AccessService 动态生成）。
 * </p>
 * <p>
 * 设计说明：
 * 1. 使用 @RestController 和 @RequestMapping 构建 RESTful API。
 * 2. 通过构造函数注入 RippleTaskPlanner，符合依赖注入最佳实践。
 * 3. 使用 ResponseEntity 包装响应，便于控制 HTTP 状态码和响应头。
 * 4. 所有异常由 Spring 的全局异常处理机制捕获（后续可扩展 @ControllerAdvice）。
 * 5. 日志记录每个请求的简要信息，便于审计和问题追踪。
 * </p>
 */
@Slf4j
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class TaskPlanningController {

    /**
     * 涟漪任务规划器，通过构造函数注入。
     */
    private final RippleTaskPlanner rippleTaskPlanner;

    /**
     * 执行任务规划。
     * <p>
     * 接收规划请求，调用 RippleTaskPlanner 执行贪心策略规划，返回 AccessTask 序列。
     * </p>
     * <p>
     * 请求示例（JSON）：
     * <pre>
     * {
     *   "centerLon": 116.4,
     *   "centerLat": 39.9,
     *   "entityID": "TARGET_001",
     *   "targetLastFindTime": "2026-07-01T08:00:00",
     *   "currentTime": "2026-07-01T09:00:00",
     *   "speed": 10.0,
     *   "planningHour": 6
     * }
     * </pre>
     * </p>
     * <p>
     * 注意：请求中不包含任何 Task 信息，所有任务由 Planner 内部通过 AccessService 动态生成。
     * </p>
     *
     * @param request 规划请求
     * @return 规划结果，HTTP 200
     */
    @PostMapping("/plan")
    public ResponseEntity<TaskSequenceResult> plan(@RequestBody PlannerRequest request) {
        log.info("收到规划请求：center=({}, {}), entityID={}, 起始时间={}, 窗口={}小时",
                request.getCenterLon(), request.getCenterLat(),
                request.getEntityID(), request.getCurrentTime(), request.getPlanningHour());

        TaskSequenceResult result = rippleTaskPlanner.plan(request);

        log.info("规划完成：选出 {} 个任务，消息：{}",
                result.getExecutionCount(), result.getMessage());

        return ResponseEntity.ok(result);
    }

    /**
     * 健康检查。
     * <p>
     * 用于服务存活探测和负载均衡健康检查。
     * 返回简单的 "UP" 字符串表示服务正常。
     * </p>
     *
     * @return "UP"
     */
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("UP");
    }

}
