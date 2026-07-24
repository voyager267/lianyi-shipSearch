---
name: task-scoring-v2-formula
description: 第二版评分公式：交集面积占比 + 时间权重 + 碎片化惩罚
metadata:
  type: project
---

# 第二版任务评分公式

## 公式
```
Score = (Area(Ripple ∩ Coverage) / Area(Ripple)) × TimeWeight × FragmentationPenalty
```

## 三个因子

### 1. 交集面积占比 (ratio)
- `ratio = intersectionArea / rippleArea`
- 替代了旧版的 Probability（grids 概率均值）和 EffectiveCoverageArea（coverage.getArea()）
- 直接衡量任务覆盖了多少比例的涟漪搜索热点区域

### 2. 时间权重 (TimeWeight)
- `timeWeight = 1.0 / (1.0 + waitHours)`
- 与旧版相同，未变

### 3. 碎片化惩罚 (FragmentationPenalty) — 新增
- `penalty = 1.0 / (1.0 + α × max(0, N_after - N_before))`
- 使用 JTS `difference(rippleGeometry, coverage)` 预估任务执行后涟漪是否分裂
- `N_before` = rippleGeometry.getNumGeometries()
- `N_after` = remainder.getNumGeometries()
- `α = 0.5`（可在 FRAGMENTATION_ALPHA 常量调整）
- 不增加独立区域 → penalty = 1.0（不惩罚）
- 多分裂出 1 个区域 → penalty = 0.67
- 多分裂出 2 个区域 → penalty = 0.5

## 涉及文件
- TaskScoreService.java — 接口签名增加 rippleGeometry + rippleArea 参数
- TaskScoreServiceImpl.java — 实现新公式 + calculateFragmentationPenalty 方法
- RippleTaskPlannerImpl.java — 调用处传递 rippleGeometry + rippleArea

**Why:** 用户要求取消 probability 和 effectiveCoverageArea，改用交集面积占比；并额外要求考虑任务是否会切碎涟漪区域（碎片化惩罚）。

**How to apply:** 所有评分相关修改已应用到三个文件中。如需调整碎片化惩罚强度，修改 TaskScoreServiceImpl.FRAGMENTATION_ALPHA 常量。