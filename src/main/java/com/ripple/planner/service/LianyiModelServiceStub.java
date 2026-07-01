package com.ripple.planner.service;

import com.ripple.planner.model.LianyiPoint;
import com.ripple.planner.model.LianyiQueryParam;
import com.ripple.planner.model.LianyiResultNew;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 涟漪模型服务模拟实现（Stub）。
 * <p>
 * 这是一个占位实现，用于在项目没有接入真实涟漪模型时，保证系统可以编译和运行。
 * 模拟实现返回一个简单的矩形 Ripple 区域，中心位于 LianyiQueryParam 的 centerLon/centerLat，
 * 边长随 historyTasks 数量增加而略微缩小，模拟"已搜索区域被剔除"的效果。
 * </p>
 * <p>
 * 重要说明：
 * 1. 本类仅用于开发和测试阶段，不应在生产环境使用。
 * 2. 真实环境中，应将本类替换为已有的涟漪模型实现类（实现 LianyiModelService 接口）。
 * 3. Spring 的 @Service 注解会自动将本类注册为 LianyiModelService 的 Bean。
 *    如果有多个实现，需要在主实现类上使用 @Primary，或在注入处使用 @Qualifier。
 * </p>
 * <p>
 * 模拟逻辑：
 * - 初始 Ripple 为以中心点为中心的 2°×2° 矩形。
 * - 每添加一个 historyTask，Ripple 面积缩小 10%（模拟已搜索区域被剔除）。
 * - 当 historyTasks 数量 >= 10 时，返回空区域（模拟目标已被完全搜索）。
 * - 支持洞（excludeGeos）的模拟：在 Ripple 中心创建一个 0.2°×0.2° 的洞。
 * </p>
 */
@Slf4j
@Service
public class LianyiModelServiceStub implements LianyiModelService {

    /**
     * 初始 Ripple 半宽/半高（度）。
     */
    private static final double INITIAL_HALF_SIZE = 1.0;

    /**
     * 每执行一个任务后的面积收缩比例。
     */
    private static final double SHRINK_RATIO = 0.9;

    /**
     * 最大支持的任务数量，超过后返回空区域。
     */
    private static final int MAX_TASKS = 10;

    /**
     * 模拟涟漪模型计算。
     * <p>
     * 返回一个以查询参数中心点为中心的矩形 Ripple 区域。
     * 区域大小根据已执行任务数量动态调整。
     * </p>
     *
     * @param param 涟漪模型查询参数
     * @return 模拟的涟漪结果列表
     */
    @Override
    public List<LianyiResultNew> calculate(LianyiQueryParam param) {
        if (param == null) {
            log.warn("涟漪模型 Stub 收到空参数，返回空结果");
            return new ArrayList<>();
        }

        int taskCount = param.getTaskIDs() != null ? param.getTaskIDs().size() : 0;

        // 如果已执行任务过多，返回空区域（模拟搜索完成）
        if (taskCount >= MAX_TASKS) {
            log.debug("Stub：已执行任务数 {} >= {}，返回空 Ripple", taskCount, MAX_TASKS);
            return new ArrayList<>();
        }

        double centerLon = param.getCenterLon();
        double centerLat = param.getCenterLat();

        // 计算当前 Ripple 半宽，随任务数增加而缩小
        double halfSize = INITIAL_HALF_SIZE * Math.pow(SHRINK_RATIO, taskCount);

        // 构造矩形外轮廓（顺时针顺序）
        List<LianyiPoint> exteriorPoints = new ArrayList<>();
        exteriorPoints.add(new LianyiPoint(centerLon - halfSize, centerLat - halfSize)); // lb
        exteriorPoints.add(new LianyiPoint(centerLon + halfSize, centerLat - halfSize)); // rb
        exteriorPoints.add(new LianyiPoint(centerLon + halfSize, centerLat + halfSize)); // rt
        exteriorPoints.add(new LianyiPoint(centerLon - halfSize, centerLat + halfSize)); // lt
        // 注意：首尾闭合由 GeometryService 处理，这里不需要重复添加第一个点

        // 构造一个中心小洞（模拟 excludeGeos）
        double holeHalfSize = halfSize * 0.1;
        List<LianyiPoint> holePoints = new ArrayList<>();
        holePoints.add(new LianyiPoint(centerLon - holeHalfSize, centerLat - holeHalfSize));
        holePoints.add(new LianyiPoint(centerLon + holeHalfSize, centerLat - holeHalfSize));
        holePoints.add(new LianyiPoint(centerLon + holeHalfSize, centerLat + holeHalfSize));
        holePoints.add(new LianyiPoint(centerLon - holeHalfSize, centerLat + holeHalfSize));

        var excludeGeo = new com.ripple.planner.model.ToClientGeo();
        excludeGeo.setLianyiPoints(holePoints);

        List<com.ripple.planner.model.ToClientGeo> excludeGeos = new ArrayList<>();
        excludeGeos.add(excludeGeo);

        // 计算面积（简化：矩形面积 - 洞面积）
        double area = (2 * halfSize) * (2 * halfSize) - (2 * holeHalfSize) * (2 * holeHalfSize);

        LianyiResultNew result = new LianyiResultNew();
        result.setLianyiPoints(exteriorPoints);
        result.setExcludeGeos(excludeGeos);
        result.setArea(area);

        List<LianyiResultNew> results = new ArrayList<>();
        results.add(result);

        log.debug("Stub 涟漪计算完成：center=({}, {}), tasks={}, halfSize={}, area={}",
                centerLon, centerLat, taskCount, halfSize, area);

        return results;
    }

}
