package com.ripple.planner.planner.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 候选任务（评分后的访问任务）。
 * <p>
 * 用于在规划过程中记录每轮所有候选任务及其评分，
 * 方便前端展示对比"哪些任务被考虑过、各自的得分、最终选中了哪个"。
 * </p>
 * <p>
 * 设计说明：
 * 1. 不修改 AccessTask 本身（AccessTask 是 AccessService 的输出，保持纯净）。
 * 2. score 是归一化后的评分（Min-Max, [0, 1]），可直接用于前端排序和对比。
 * 3. selected 标识该任务是否被本轮规划选中。
 *    每轮最多只有一个 selected=true，其余为 false。
 * </p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CandidateTask {

    /**
     * 候选访问任务。
     * <p>
     * 包含 accessId、satellite、accessTime、coverage（GeoJSON）、grids 等完整信息。
     * coverage 由 {@link com.ripple.planner.jackson.GeometryJsonSerializer} 序列化为 GeoJSON。
     * </p>
     */
    private AccessTask accessTask;

    /**
     * 归一化后的评分。
     * <p>
     * 经过 Min-Max 归一化，范围 [0.0, 1.0]。
     * 每轮中得分最高的任务 score = 1.0，其余按比例缩放。
     * 如果所有任务原始评分均为 0，则所有 score 均为 0。
     * </p>
     */
    private double score;

    /**
     * 是否被本轮规划选中。
     * <p>
     * 每轮规划循环中，Planner 选择 score 最高的任务执行。
     * 该任务 selected = true，其余候选任务 selected = false。
     * </p>
     */
    private boolean selected;

}