package com.ripple.planner.controller;

import com.ripple.planner.planner.model.PlanningRequest;
import com.ripple.planner.planner.model.TaskSequenceResult;
import com.ripple.planner.planner.service.RippleTaskPlanner;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
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
 * - POST /api/v1/plan：接收 PlanningRequest，返回 TaskSequenceResult。
 * - GET /api/v1/health：健康检查端点，用于服务状态监控。
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
@Tag(name = "任务规划接口", description = "卫星搜索任务规划相关的 REST API，支持任务序列规划和健康检查")
public class TaskPlanningController {

    /**
     * 涟漪任务规划器，通过构造函数注入。
     */
    private final RippleTaskPlanner rippleTaskPlanner;

    /**
     * 执行任务规划。
     * <p>
     * 接收规划请求，调用 RippleTaskPlanner 执行贪心策略规划，返回任务序列。
     * </p>
     * <p>
     * 请求示例（JSON）：
     * <pre>
     * {
     *   "centerLon": 116.4,
     *   "centerLat": 39.9,
     *   "entityID": "TARGET_001",
     *   "targetLastFindTime": "2026-07-01 08:00:00",
     *   "targetSpeed": 10.0,
     *   "startTime": "2026-07-01 09:00:00",
     *   "candidateTasks": [
     *     {
     *       "taskID": "TASK_001",
     *       "satellite": "SAT_01",
     *       "scoutTime": "2026-07-01 10:00:00",
     *       "catas": [
     *         {
     *           "catalbLatitude": 39.8,
     *           "catalbLongitude": 116.3,
     *           "catartLatitude": 40.0,
     *           "catartLongitude": 116.5,
     *           "cataltLatitude": 40.0,
     *           "cataltLongitude": 116.3,
     *           "catarbLatitude": 39.8,
     *           "catarbLongitude": 116.5
     *         }
     *       ]
     *     }
     *   ]
     * }
     * </pre>
     * </p>
     *
     * @param request 规划请求
     * @return 规划结果，HTTP 200
     */
    @PostMapping("/plan")
    @Operation(
            summary = "执行任务规划",
            description = "接收规划请求，调用 RippleTaskPlanner 执行贪心策略规划，返回最优任务序列。" +
                    "系统会根据目标位置、速度、候选卫星任务等参数，计算最高效的搜索任务执行顺序。"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "规划成功",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = TaskSequenceResult.class)))
    })
    public ResponseEntity<TaskSequenceResult> plan(
            @Parameter(description = "规划请求参数，包含目标信息和候选任务列表", required = true)
            @RequestBody PlanningRequest request) {
        log.info("收到规划请求：center=({}, {}), entityID={}, 候选任务数={}",
                request.getCenterLon(), request.getCenterLat(),
                request.getEntityID(),
                request.getCandidateTasks() != null ? request.getCandidateTasks().size() : 0);

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
    @Operation(
            summary = "健康检查",
            description = "服务存活探测端点，用于负载均衡健康检查和监控系统。返回 UP 表示服务正常。"
    )
    @ApiResponse(responseCode = "200", description = "服务正常",
            content = @Content(mediaType = "text/plain", schema = @Schema(type = "string", example = "UP")))
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("UP");
    }

}
