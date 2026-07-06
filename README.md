# Ripple Satellite Planner

> 基于涟漪模型的动态卫星搜索任务规划系统

该项目是一个用于动态卫星搜索任务规划的 Spring Boot 后端服务。它基于"涟漪模型"动态计算目标可能存在的概率分布区域，并通过贪心策略规划卫星访问任务序列，实现对移动目标的持续跟踪搜索。

---

## 目录

- [系统简介](#系统简介)
- [核心架构](#核心架构)
- [技术栈](#技术栈)
- [环境要求](#环境要求)
- [快速开始](#快速开始)
- [配置说明](#配置说明)
- [API 接口](#api-接口)
- [项目结构](#项目结构)
- [规划算法详解](#规划算法详解)
- [扩展指南](#扩展指南)

---

## 系统简介

### 解决的问题

当海上移动目标（如船舶、搜救目标）在某一时刻被发现后，随着时间推移，目标可能移动到以最后已知位置为中心、随时间扩散的某个区域内。传统的固定搜索方案难以高效覆盖这个不断扩大的概率区域。

本系统采用**涟漪模型**动态计算目标可能存在的概率分布区域（类似水面涟漪向外扩散），并基于**贪心策略**在每轮规划中：

1. 调用涟漪模型获取目标当前的概率分布区域
2. 将概率区域离散化为网格（Grid）
3. 计算每个网格的目标存在概率
4. 动态生成时间窗口内的卫星访问机会
5. 对所有候选访问任务评分，选出最优任务执行
6. 将执行结果反馈给涟漪模型，更新概率分布，进入下一轮

### 核心特点

- **动态规划**：每轮基于最新的搜索历史重新计算概率区域，体现"边搜索边更新"的闭环思想
- **任务动态生成**：Planner 不接收外部候选任务，所有访问任务由 `AccessService` 根据轨道数据动态生成
- **接口解耦**：涟漪模型、轨道计算、评分逻辑均通过接口隔离，可独立替换实现
- **几何精确**：基于 JTS 拓扑套件进行空间运算，支持带洞多边形、MultiPolygon 等复杂几何

---

## 核心架构

```
┌─────────────────────────────────────────────────────────────┐
│                        REST API 层                          │
│                  TaskPlanningController                     │
│              POST /api/v1/plan  GET /api/v1/health          │
└─────────────────────────┬───────────────────────────────────┘
                          │
                          ▼
┌─────────────────────────────────────────────────────────────┐
│                    规划核心 (Planner)                        │
│                   RippleTaskPlannerImpl                     │
│                                                             │
│   while (迭代 < MAX) {                                      │
│     ① 调用涟漪模型 → 概率分布区域                            │
│     ② Geometry 转换   → JTS Geometry                        │
│     ③ 网格空间查询    → 相交 Grid 列表                       │
│     ④ 概率计算        → Grid.probability                    │
│     ⑤ 访问任务生成    → List<AccessTask>                    │
│     ⑥ 任务评分        → Score = P × Area × TimeWeight       │
│     ⑦ 选择最优任务    → max(Score)                          │
│     ⑧ 更新状态        → historyTasks + currentTime          │
│   }                                                         │
└───┬───────┬──────────┬──────────────┬──────────────┬───────┘
    │       │          │              │              │
    ▼       ▼          ▼              ▼              ▼
┌───────┐┌────────┐┌──────────┐┌────────────┐┌──────────────┐
│涟漪模型││Geometry││GridService││AccessService││TaskScoreService│
│Service││Service ││          ││            ││              │
└───┬───┘└────────┘└──────────┘└─────┬──────┘└──────────────┘
    │                                │
    ▼                                ▼
┌──────────────┐            ┌─────────────────┐
│远程涟漪服务   │            │ Mock / 真实轨道  │
│HTTP :12223   │            │ SGP4 计算       │
└──────────────┘            └─────────────────┘
```

---

## 技术栈

| 组件 | 技术 | 版本 | 用途 |
|------|------|------|------|
| 框架 | Spring Boot | 3.2.5 | 应用框架、嵌入式 Tomcat |
| 语言 | Java | 17 | 编程语言 |
| 构建 | Maven | 3.x | 依赖管理与构建 |
| 几何计算 | JTS Topology Suite | 1.19.0 | 空间几何运算 |
| API 文档 | Knife4j + SpringDoc | 4.4.0 / 2.3.0 | OpenAPI 文档与在线调试 |
| 工具库 | Lombok | - | 样板代码消除 |
| 校验 | Bean Validation | - | 接口入参校验 |

---

## 环境要求

- **JDK 17** 或更高版本（项目使用 Java 17 特性）
- **Maven 3.6+**（或使用 IDE 内置 Maven）
- **涟漪模型服务**（默认远程调用 `http://localhost:12223`，开发时可使用 Stub）

---

## 快速开始

### 1. 克隆项目

```bash
git clone <repository-url>
cd ripple-satellite-planner
```

### 2. 编译并启动

**方式一：Maven 命令行**

```bash
mvn spring-boot:run
```

**方式二：打包后运行**

```bash
mvn clean package
java -jar target/ripple-satellite-planner-1.0.0-SNAPSHOT.jar
```

**方式三：IDE 启动**

直接运行主类 `com.ripple.planner.RippleSatellitePlannerApplication` 的 `main` 方法。

> **注意**：如果本地默认 JDK 不是 17，需指定 `JAVA_HOME`：
> ```bash
> # PowerShell 示例
> $env:JAVA_HOME='C:\path\to\jdk-17'
> mvn spring-boot:run
> ```

### 3. 验证启动

启动成功后，访问以下地址：

| 服务 | 地址 |
|------|------|
| 健康检查 | http://localhost:8081/api/v1/health |
| API 文档（推荐） | http://localhost:8081/doc.html |
| Swagger UI | http://localhost:8081/swagger-ui.html |
| OpenAPI JSON | http://localhost:8081/v3/api-docs |

### 4. 调用规划接口

```bash
curl -X POST http://localhost:8081/api/v1/plan \
  -H "Content-Type: application/json" \
  -d '{
    "centerLon": 116.4,
    "centerLat": 39.9,
    "entityID": "TARGET_001",
    "targetLastFindTime": "2026-07-01 08:00:00",
    "currentTime": "2026-07-01 09:00:00",
    "speed": 10.0,
    "planningHour": 6
  }'
```

---

## 配置说明

主配置文件：`src/main/resources/application.yml`

### 核心配置项

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| `server.port` | 8081 | 服务端口 |
| `planner.max-iterations` | 10000 | 最大规划循环次数（安全保护） |
| `planner.grid-resolution` | 1.0 | 网格分辨率（度） |
| `lianyi.service.url` | http://localhost:12223 | 涟漪模型远程服务地址 |

### 跨域配置

项目内置了 CORS 跨域配置（`WebMvcConfig`），在非生产环境自动启用：

- **开发/调试**（默认）：CORS 完全开放，允许任意来源跨域访问
- **生产环境**：启动时指定 `--spring.profiles.active=prod`，CORS 配置不加载，由网关/Nginx 统一控制

```bash
# 生产环境启动（禁用内置 CORS）
java -jar ripple-satellite-planner.jar --spring.profiles.active=prod
```

### 日志级别

```yaml
logging:
  level:
    root: INFO
    com.ripple.planner.planner: DEBUG          # 规划循环过程
    com.ripple.planner.service.LianyiModelServiceStub: DEBUG  # 涟漪模型
```

---

## API 接口

### 1. 执行任务规划

```
POST /api/v1/plan
```

**请求参数（PlannerRequest）**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `centerLon` | double | 是 | 搜索中心点经度（目标最后发现位置），范围 [-180, 180] |
| `centerLat` | double | 是 | 搜索中心点纬度，范围 [-90, 90] |
| `entityID` | string | 是 | 目标实体标识 |
| `targetLastFindTime` | string | 是 | 目标最后被发现时刻，格式 `yyyy-MM-dd HH:mm:ss` |
| `currentTime` | string | 是 | 当前规划起始时间，格式 `yyyy-MM-dd HH:mm:ss` |
| `speed` | double | 是 | 目标估计最大航速（km/h） |
| `planningHour` | int | 是 | 规划时间窗口（小时） |

**请求示例**

```json
{
  "centerLon": 116.4,
  "centerLat": 39.9,
  "entityID": "TARGET_001",
  "targetLastFindTime": "2026-07-01 08:00:00",
  "currentTime": "2026-07-01 09:00:00",
  "speed": 10.0,
  "planningHour": 6
}
```

**响应参数（TaskSequenceResult）**

| 字段 | 类型 | 说明 |
|------|------|------|
| `taskSequence` | List&lt;AccessTask&gt; | 规划出的访问任务序列（按时间升序） |
| `records` | List&lt;SearchRecord&gt; | 详细搜索记录（含每轮 Ripple 结果） |
| `totalScore` | double | 规划总得分 |
| `executionCount` | int | 实际规划出的任务数量 |
| `message` | string | 规划结果描述 |

**响应示例**

```json
{
  "taskSequence": [
    {
      "accessId": "row_39_col_116_ACCESS_0",
      "satellite": "GF1",
      "accessTime": "2026-07-01 10:23:45",
      "coverage": { "...GeoJSON..." },
      "grids": [ { "id": "row_39_col_116", "probability": 0.35 } ]
    }
  ],
  "records": [ { "...SearchRecord..." } ],
  "totalScore": 12.56,
  "executionCount": 5,
  "message": "规划成功：共选出 5 个任务"
}
```

### 2. 健康检查

```
GET /api/v1/health
```

**响应**：`"UP"`

---

## 项目结构

```
ripple-satellite-planner/
├── src/main/java/com/ripple/planner/
│   ├── RippleSatellitePlannerApplication.java   # 启动类
│   ├── config/
│   │   ├── JtsConfig.java                        # JTS 几何工厂配置
│   │   ├── Knife4jConfig.java                    # API 文档配置
│   │   └── WebMvcConfig.java                     # 跨域配置（非生产环境）
│   ├── controller/
│   │   └── TaskPlanningController.java           # REST API 控制器
│   ├── jackson/
│   │   └── GeometryJsonSerializer.java           # JTS Geometry → GeoJSON 序列化
│   ├── model/                                    # 涟漪模型相关数据模型
│   │   ├── Cata.java                             # 搜索区域四边形
│   │   ├── LianyiPoint.java                      # 涟漪点
│   │   ├── LianyiQueryParam.java                 # 涟漪查询参数
│   │   ├── LianyiResultNew.java                  # 涟漪计算结果
│   │   ├── TaskParam.java                        # 任务参数
│   │   └── ToClientGeo.java                      # 客户端几何数据
│   ├── planner/                                  # 规划核心模块
│   │   ├── model/
│   │   │   ├── AccessTask.java                   # 卫星访问任务
│   │   │   ├── Grid.java                         # 网格单元
│   │   │   ├── PlannerRequest.java               # 规划请求
│   │   │   ├── PlannerState.java                 # 规划状态
│   │   │   ├── SearchRecord.java                 # 搜索记录
│   │   │   └── TaskSequenceResult.java           # 规划结果
│   │   ├── service/
│   │   │   ├── AccessService.java                # 卫星访问服务接口
│   │   │   ├── GeometryService.java              # 几何服务接口
│   │   │   ├── GridService.java                  # 网格服务接口
│   │   │   ├── ProbabilityService.java           # 概率服务接口
│   │   │   ├── TaskScoreService.java             # 任务评分服务接口
│   │   │   ├── RippleTaskPlanner.java            # 规划器接口
│   │   │   ├── *Impl.java                        # 各服务实现
│   │   │   └── MockAccessService.java            # Mock 访问服务
│   │   └── util/
│   │       └── JtsGeometryUtil.java              # JTS 几何工具类
│   └── service/
│       ├── LianyiModelService.java               # 涟漪模型服务接口
│       ├── LianyiModelServiceRemoteImpl.java     # 远程涟漪服务实现（@Primary）
│       └── LianyiModelServiceStub.java           # 本地 Stub（开发用）
├── src/main/resources/
│   └── application.yml                           # 主配置文件
├── doc.html                                      # Knife4j 文档入口
└── pom.xml                                       # Maven 构建文件
```

---

## 规划算法详解

### 核心循环

`RippleTaskPlannerImpl.plan()` 实现了一个贪心闭环规划算法，每轮迭代包含以下步骤：

```
Step 1  调用涟漪模型 → 获取目标概率分布区域（List<LianyiResultNew>）
Step 2  几何转换      → 将涟漪结果转为 JTS Geometry（Polygon/MultiPolygon）
Step 3  网格查询      → 获取与 Ripple 区域相交的所有 Grid
Step 4  概率计算      → probability = Area(Grid ∩ Ripple) / Area(Ripple)
Step 5  访问生成      → AccessService 在时间窗口内生成卫星访问机会
Step 6  任务评分      → Score = Probability × CoverageArea × TimeWeight
Step 7  选择最优      → 选取 Score 最高的 AccessTask
Step 8  终止判断      → 若最高 Score ≤ 0，结束规划
Step 9  状态更新      → 更新 historyTasks 和 currentTime，进入下一轮
```

### 评分公式

```
Score = Probability × EffectiveCoverageArea × TimeWeight
```

| 因子 | 计算方式 | 含义 |
|------|----------|------|
| Probability | AccessTask 覆盖 Grid 的概率均值 | 目标存在于覆盖区域的整体概率 |
| EffectiveCoverageArea | `coverage.getArea()` | 访问任务的有效覆盖面积 |
| TimeWeight | `1 / (1 + 等待秒数)` | 等待时间越长，权重越低 |

### 终止条件

规划循环在以下任一条件下终止：

1. 涟漪模型返回空结果（目标已无概率区域）
2. Ripple 几何区域为空或无相交网格
3. 时间窗口内无卫星访问机会
4. 所有访问任务评分均为 0（无法继续优化）
5. 达到最大循环次数（默认 10 次，安全保护）

---

## 扩展指南

### 替换涟漪模型实现

当前使用 `LianyiModelServiceRemoteImpl`（远程 HTTP 调用）作为主实现。如需替换：

1. 实现 `LianyiModelService` 接口
2. 使用 `@Primary` 或 `@Profile` 控制 Bean 优先级
3. `RippleTaskPlannerImpl` 无需任何修改

### 接入真实轨道计算

当前使用 `MockAccessService` 模拟卫星访问。接入真实 SGP4 轨道计算：

1. 新建 `RealAccessService implements AccessService`
2. 注入 SGP4 计算库、TLE 数据服务
3. 使用 `@Primary` 或 Spring Profile 切换 Bean
4. `RippleTaskPlannerImpl` 无需任何修改

### 自定义评分策略

1. 实现 `TaskScoreService` 接口
2. 可引入多目标优化、Pareto 前沿、强化学习等高级策略
3. 通过 `@Primary` 替换默认实现

### 优化网格查询性能

当前 `GridServiceImpl` 采用全量遍历。大数据量优化方向：

- 引入 JTS `STRtree` 空间索引（O(n) → O(log n + k)）
- 使用 H3 等离散全局网格系统进行哈希索引

---

## 许可证

Apache License 2.0
