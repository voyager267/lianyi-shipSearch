package com.ripple.planner.planner.service;

import com.ripple.planner.planner.model.AccessTask;
import com.ripple.planner.planner.model.Grid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 卫星访问服务模拟实现（Mock）。
 * <p>
 * 为每个输入的 Grid 随机生成 2~5 个模拟的卫星访问机会，
 * 用于在真实轨道计算模块完成前，保证 Planner 可以完整运行和测试。
 * </p>
 * <p>
 * 模拟规则：
 * 1. 遍历每个输入的 Grid。
 * 2. 对每个 Grid 随机生成 2~5 个 AccessTask（随机数范围：[2, 5]）。
 * 3. accessTime 在 [startTime, endTime] 内均匀随机分布。
 * 4. satellite 从预定义的中国遥感卫星列表中随机选择。
 * 5. coverage 直接使用 Grid.geometry（简化：假设访问精确覆盖该网格）。
 * 6. grids 列表包含当前 Grid（简化：每个 AccessTask 只覆盖一个 Grid）。
 * 7. accessId 格式：{gridId}_ACCESS_{序号}，确保同一规划会话内唯一。
 * </p>
 * <p>
 * 重要说明：
 * 1. 本类仅用于开发和集成测试阶段，生产环境必须替换为真实轨道计算实现。
 * 2. 类上标注了 TODO 注释，明确提示后续替换点。
 * 3. 模拟数据尽量贴近真实接口返回的数据结构，避免为了演示而过度简化。
 *    例如：AccessTask 包含完整的 coverage Geometry 和 grids 列表，与真实实现保持一致。
 * 4. Spring 的 @Service 注解自动注册为 AccessService 的 Bean。
 *    后续接入真实实现时，可使用 @Primary 或 Profile 切换，RippleTaskPlanner 无需修改代码。
 * </p>
 */
// TODO Replace with real access calculation service
@Slf4j
@Service
public class MockAccessService implements AccessService {

    /**
     * 模拟卫星名称池。
     * <p>
     * 包含中国常见遥感卫星名称，用于随机分配。
     * 后续真实实现将从卫星资源数据库中动态查询可用卫星。
     * </p>
     */
    private static final String[] SATELLITE_POOL = {
            "GF1", "GF2", "GF3", "GF4", "GF5", "GF6", "GF7",
            "ZY1", "ZY3", "HJ2A", "HJ2B", "CB4A", "CB4B",
            "SV1", "SV2", "LC1", "LC2"
    };

    /**
     * 每个 Grid 的最小访问次数。
     */
    private static final int MIN_ACCESS_PER_GRID = 2;

    /**
     * 每个 Grid 的最大访问次数。
     */
    private static final int MAX_ACCESS_PER_GRID = 5;

    /**
     * 计算模拟的卫星访问机会。
     * <p>
     * 对每个输入的 Grid 生成随机数量的 AccessTask，
     * 访问时间均匀分布在给定的时间窗口内。
     * </p>
     *
     * @param grids     Ripple 区域覆盖的网格集合
     * @param startTime 规划时间窗口起始时间
     * @param endTime   规划时间窗口结束时间
     * @return 模拟的访问任务列表
     */
    @Override
    public List<AccessTask> calculateAccess(List<Grid> grids, LocalDateTime startTime, LocalDateTime endTime) {
        if (grids == null || grids.isEmpty() || startTime == null || endTime == null) {
            log.debug("MockAccessService 收到空输入，返回空列表");
            return Collections.emptyList();
        }

        if (!endTime.isAfter(startTime)) {
            log.warn("MockAccessService：结束时间 {} 不晚于开始时间 {}，返回空列表", endTime, startTime);
            return Collections.emptyList();
        }

        List<AccessTask> accessTasks = new ArrayList<>();
        long windowSeconds = Duration.between(startTime, endTime).getSeconds();

        for (Grid grid : grids) {
            if (grid == null || grid.getGeometry() == null) {
                continue;
            }

            // 为当前 Grid 随机生成 2~5 个访问机会
            int accessCount = ThreadLocalRandom.current().nextInt(MIN_ACCESS_PER_GRID, MAX_ACCESS_PER_GRID + 1);

            for (int i = 0; i < accessCount; i++) {
                // 生成随机访问时间（均匀分布在时间窗口内）
                long randomOffsetSeconds = ThreadLocalRandom.current().nextLong(windowSeconds + 1);
                LocalDateTime accessTime = startTime.plusSeconds(randomOffsetSeconds);

                // 随机选择卫星
                String satellite = SATELLITE_POOL[ThreadLocalRandom.current().nextInt(SATELLITE_POOL.length)];

                // 构造 AccessTask
                AccessTask task = new AccessTask();
                task.setAccessId(grid.getId() + "_ACCESS_" + i);
                task.setSatellite(satellite);
                task.setAccessTime(accessTime);
                // 简化：coverage 直接使用 Grid 的几何形状
                // TODO Replace with real access calculation service：真实实现中应使用 SGP4 计算的精确覆盖多边形
                task.setCoverage(grid.getGeometry());
                // 简化：每个 AccessTask 只覆盖一个 Grid
                // TODO Replace with real access calculation service：真实实现中一个 AccessTask 可能覆盖多个相邻 Grid
                task.setGrids(List.of(grid));

                accessTasks.add(task);
            }
        }

        log.debug("MockAccessService 生成访问机会：输入网格数={}, 生成任务数={}, 时间窗口=[{}, {}]",
                grids.size(), accessTasks.size(), startTime, endTime);

        return accessTasks;
    }

}
