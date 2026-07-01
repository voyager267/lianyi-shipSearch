package com.ripple.planner.service;

import com.ripple.planner.model.LianyiQueryParam;
import com.ripple.planner.model.LianyiResultNew;

import java.util.List;

/**
 * 涟漪模型服务接口。
 * <p>
 * 这是已有涟漪模型对外暴露的唯一接口，Planner 模块通过该接口调用涟漪模型，
 * 获取在当前搜索历史条件下目标可能存在的概率分布区域。
 * </p>
 * <p>
 * 核心契约：
 * - 输入：LianyiQueryParam，包含搜索中心、目标信息、当前时间、已执行的任务列表（historyTasks）
 * - 输出：List&lt;LianyiResultNew&gt;，表示目标可能存在的概率区域列表。
 *         使用列表而非单个对象，是因为在某些场景下涟漪模型可能返回多个分离的概率区域（MultiPolygon 语义）。
 * </p>
 * <p>
 * 设计原则：
 * 1. 接口隔离：Planner 只依赖此接口，不依赖涟漪模型的任何内部实现类。
 *    这使得涟漪模型可以独立演进（例如更换算法、优化性能），只要保持该接口契约即可。
 * 2. 只读调用：Planner 将 LianyiQueryParam 传入后，涟漪模型负责计算并返回结果。
 *    Planner 不修改 LianyiResultNew 的任何字段，仅读取 lianyiPoints、excludeGeos、area。
 * 3. 无状态：该接口的实现应该是无状态的（或线程安全的），因为 Planner 会在规划循环中高频调用。
 *    每次调用都基于全新的 LianyiQueryParam，不依赖上次调用的内部状态。
 * </p>
 * <p>
 * 使用位置：
 * - RippleTaskPlanner 在规划循环的 Step1 中调用此接口：
 *   List&lt;LianyiResultNew&gt; results = lianyiModelService.calculate(param);
 * </p>
 */
public interface LianyiModelService {

    /**
     * 执行涟漪模型计算。
     * <p>
     * 根据查询参数中的搜索历史、目标信息和当前时间，计算目标可能存在的概率分布区域。
     * </p>
     *
     * @param param 涟漪模型查询参数，包含 centerLon、centerLat、entityID、
     *              targetLastFindTime、scoutTime、speed、taskIDs（已执行的任务列表）
     * @return 涟漪模型计算结果列表。每个 LianyiResultNew 表示一个概率区域（Polygon With Holes）。
     *         列表为空表示在当前条件下目标不存在任何可能区域（理论上不应发生，但需防御性处理）。
     */
    List<LianyiResultNew> calculate(LianyiQueryParam param);

}
