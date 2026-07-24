package com.ripple.planner.planner.service;

import com.ripple.planner.planner.model.AccessTask;
import org.locationtech.jts.geom.Geometry;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 任务评分服务接口。
 * <p>
 * 负责为每个 AccessService 动态生成的访问任务（AccessTask）计算综合评分。
 * 评分是规划器选择最优任务的核心依据，直接决定任务序列的质量。
 * </p>
 * <p>
 * 第二版评分公式：
 *     Score = (Area(Ripple ∩ Coverage) / Area(Ripple)) × TimeWeight
 * 其中：
 * - Area(Ripple ∩ Coverage) = 涟漪区域与任务覆盖区域的交集面积。
 * - Area(Ripple) = 当前轮次涟漪区域的总面积。
 * - TimeWeight = 1 / (1 + 等待时间小时数)，等待时间越长，权重越低。
 * </p>
 * <p>
 * 与旧版的区别：
 * - 旧版使用 Probability × EffectiveCoverageArea 作为空间因子。
 * - 新版改为"交集面积占比"，直接衡量任务覆盖了多少比例的涟漪搜索热点区域。
 *   这更精准地反映任务对当前搜索目标的贡献度。
 * </p>
 * <p>
 * 设计原则：
 * 1. 评分逻辑与选择逻辑分离：TaskScoreService 只负责计算分数，不负责选择任务。
 * 2. 可替换性：通过接口定义评分契约，后续可引入更复杂的评分模型。
 * 3. 纯函数倾向：评分计算基于输入参数，不依赖全局状态。
 * 4. 分数语义：score > 0 表示任务可行；score = 0 表示不可行；score 越高越优。
 * </p>
 */
public interface TaskScoreService {

    /**
     * 为单个访问任务计算评分。
     * <p>
     * 计算涟漪区域与任务覆盖区域的交集面积占涟漪面积的比例，再乘以时间权重。
     * </p>
     *
     * @param accessTask     访问任务，包含 coverage（JTS Geometry）、accessTime
     * @param currentTime    当前规划时间，用于计算等待时间和 TimeWeight
     * @param rippleGeometry 当前轮次的涟漪区域 JTS Geometry（可能为 Polygon 或 MultiPolygon）
     * @param rippleArea     当前轮次的涟漪区域总面积
     * @return 综合评分，score <= 0 表示不可行
     */
    double scoreTask(AccessTask accessTask, LocalDateTime currentTime,
                     Geometry rippleGeometry, double rippleArea);

    /**
     * 批量为访问任务列表计算评分。
     * <p>
     * 遍历 accessTasks 列表，逐个调用 scoreTask 方法。
     * 提供批量接口是为了方便后续引入并行计算。
     * </p>
     *
     * @param accessTasks    访问任务列表
     * @param currentTime    当前规划时间
     * @param rippleGeometry 当前轮次的涟漪区域 JTS Geometry
     * @param rippleArea     当前轮次的涟漪区域总面积
     * @return 评分结果列表，与输入列表一一对应（index 相同）
     */
    List<Double> scoreTasks(List<AccessTask> accessTasks, LocalDateTime currentTime,
                            Geometry rippleGeometry, double rippleArea);

}
