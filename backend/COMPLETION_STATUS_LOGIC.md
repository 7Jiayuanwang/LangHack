# 活动完成状态与完成率统计逻辑说明

## 一、完成状态（completion_status）

### 1.1 状态定义

| 状态 | 值 | 含义 | 触发条件 |
|------|---|------|---------|
| 未开始 | NOT_STARTED | 已报名但未开始任何任务 | `totalScore = 0` AND `completedTasks = 0` |
| 进行中 | IN_PROGRESS | 已开始但未达到完成条件 | 有进度但未满足完成规则 |
| 已完成 | COMPLETED | 满足活动完成条件 | 根据 `completion_rule` 判定 |

### 1.2 完成条件判定

根据活动的 `completion_rule` 字段判定：

#### ANY（完成任意任务）
```java
isCompleted = (completedTasks > 0)
```
只要完成任意一个任务即算完成。

#### ALL（完成所有必做任务）
```java
isCompleted = (completedTasks >= requiredTaskCount)
```
必须完成所有标记为 `is_required = true` 的任务。

#### THRESHOLD（完成指定数量任务）
```java
isCompleted = (completedTasks >= completion_threshold)
```
完成任务数达到或超过 `completion_threshold` 设定值。

**示例**：
- `completion_threshold = 6`：需要完成至少 6 个任务才算完成
- `completedTasks = 1`：状态为 IN_PROGRESS
- `completedTasks = 6`：状态为 COMPLETED

### 1.3 状态更新时机

**自动更新位置**：`CampaignStatsServiceImpl.updateParticipationScore()`

每次数据采集或任务完成后，系统会：
1. 统计用户的总得分（`totalScore`）
2. 统计用户的完成任务数（`completedTasks`）
3. 调用 `calculateCompletionStatus()` 计算新状态
4. 更新 `campaign_participations` 表的 `completion_status` 字段

**触发场景**：
- 定时数据采集任务执行（`collectAllCampaignData`）
- 单个任务数据采集（`collectTaskData`）
- 手动录入数据后

## 二、完成率（completionRate）统计

### 2.1 统一计算公式

**公式**：`completionRate = (completedParticipants / enrolledCount) * 100`

**返回格式**：字符串 `"%.1f%%"`（保留1位小数）

**示例**：
- 6人报名，0人完成 → `"0.0%"`
- 6人报名，3人完成 → `"50.0%"`
- 6人报名，1人完成 → `"16.7%"`

### 2.2 统计位置

#### 1. 活动概览（getCampaignOverview / getParticipationStats）
```java
// 已完成人数：completion_status = 'COMPLETED' 且 enrollment_status = 'ENROLLED'
long completedParticipants = count(completion_status = 'COMPLETED' AND enrollment_status = 'ENROLLED');

// 已报名人数：enrollment_status = 'ENROLLED'
long enrolledCount = count(enrollment_status = 'ENROLLED');

// 完成率
String completionRate = enrolledCount > 0
    ? String.format("%.1f%%", (double) completedParticipants / enrolledCount * 100)
    : "0.0%";
```

**返回字段**：
```json
{
  "enrolledCount": 6,
  "completedParticipants": 0,
  "inProgressParticipants": 5,
  "completionRate": "0.0%"
}
```

#### 2. 班级排名（getRankingByClass）
```java
// 统计每个班级的已完成人数
for (participation : participations) {
    if (participation.getCompletionStatus().equals("COMPLETED")) {
        stats.completedCount++;
    }
    stats.participantCount++;
}

// 完成率
String completionRate = stats.participantCount > 0
    ? String.format("%.1f%%", (double) stats.completedCount / stats.participantCount * 100)
    : "0.0%";
```

**返回字段**：
```json
{
  "className": "25网络1",
  "totalScore": 3,
  "participantCount": 1,
  "avgScore": 3.0,
  "completionRate": "0.0%"
}
```

#### 3. 学院排名（getRankingByCollege）
```java
// 统计每个学院的已完成人数
for (participation : participations) {
    if (participation.getCompletionStatus().equals("COMPLETED")) {
        stats.completedCount++;
    }
    stats.participantCount++;
}

// 完成率
String completionRate = stats.participantCount > 0
    ? String.format("%.1f%%", (double) stats.completedCount / stats.participantCount * 100)
    : "0.0%";
```

**返回字段**：
```json
{
  "collegeName": "计算机与信息工程学院",
  "totalScore": 5,
  "participantCount": 3,
  "avgScore": 1.7,
  "completionRate": "0.0%"
}
```

### 2.3 一致性保证

**核心原则**：
1. **分子**：始终使用 `completion_status = 'COMPLETED'` 的人数
2. **分母**：始终使用对应范围内的参与人数（已报名人数）
3. **格式**：统一返回字符串 `"%.1f%%"`（保留1位小数）
4. **空值处理**：当分母为0时，返回 `"0.0%"`

## 三、关键数据表字段

### campaign_participations 表
| 字段 | 类型 | 说明 |
|------|------|------|
| completion_status | VARCHAR(20) | 完成状态：NOT_STARTED / IN_PROGRESS / COMPLETED |
| completed_tasks | INT | 已完成任务数（`is_completed = 1` 的任务数） |
| total_score | INT | 总得分（所有任务的 `earned_score` 之和） |

### campaign_activities 表
| 字段 | 类型 | 说明 |
|------|------|------|
| completion_rule | ENUM | 完成规则：ANY / ALL / THRESHOLD |
| completion_threshold | INT | 完成阈值（仅 THRESHOLD 规则使用） |

### campaign_tasks 表
| 字段 | 类型 | 说明 |
|------|------|------|
| is_required | TINYINT(1) | 是否必做（仅 ALL 规则使用） |

## 四、测试验证

### 4.1 场景1：THRESHOLD 规则（活动ID=2）
- `completion_threshold = 6`
- 5个任务

**当前状态**：
| 用户ID | completed_tasks | total_score | completion_status | 预期状态 |
|--------|----------------|-------------|-------------------|---------|
| 6 | 1 | 2 | IN_PROGRESS | ✅ |
| 103 | 0 | 2 | IN_PROGRESS | ✅ |
| 284 | 0 | 3 | IN_PROGRESS | ✅ |
| 266 | 0 | 0 | NOT_STARTED | ✅ |

**完成率验证**：
- 已报名：6人
- 已完成：0人（无人 completedTasks ≥ 6）
- 完成率：`0.0%` ✅

### 4.2 场景2：模拟完成
假设用户6完成了6个任务：
```sql
UPDATE campaign_participations 
SET completed_tasks = 6, completion_status = 'COMPLETED' 
WHERE user_id = 6 AND campaign_id = 2;
```

预期结果：
- 已完成：1人
- 完成率：`16.7%` (1/6 * 100)

## 五、API 响应示例

### 5.1 活动概览
```bash
GET /api/admin/campaigns/2/stats/overview
```
```json
{
  "code": 200,
  "data": {
    "totalParticipants": 6,
    "enrolledCount": 6,
    "completedParticipants": 0,
    "inProgressParticipants": 5,
    "completionRate": "0.0%",
    "taskCount": 5
  }
}
```

### 5.2 班级排名
```bash
GET /api/admin/campaigns/2/stats/ranking/class
```
```json
{
  "code": 200,
  "data": [
    {
      "className": "25网络1",
      "collegeName": "计算机与信息工程学院",
      "totalScore": 3,
      "participantCount": 1,
      "avgScore": 3.0,
      "completionRate": "0.0%"
    }
  ]
}
```

### 5.3 学院排名
```bash
GET /api/admin/campaigns/2/stats/ranking/college
```
```json
{
  "code": 200,
  "data": [
    {
      "collegeName": "计算机与信息工程学院",
      "totalScore": 5,
      "participantCount": 3,
      "avgScore": 1.7,
      "completionRate": "0.0%"
    }
  ]
}
```

## 六、注意事项

### 6.1 数据一致性
- `completion_status` 由系统自动维护，禁止手动修改
- 每次任务完成后必须调用 `updateParticipationScore()` 更新状态
- 数据采集任务会批量更新所有参与者的状态

### 6.2 缓存问题
- 统计接口使用 `@Cacheable` 缓存
- 数据采集后使用 `@CacheEvict` 清除缓存
- 如发现数据不一致，手动清除 Redis 缓存：`campaign_overview:*` 和 `campaign_ranking:*`

### 6.3 完成任务数（completedTasks）的定义
- 对于一次性任务：`is_completed = 1` 才计入
- 对于可重复任务：`is_completed = 1` 计入（即使 `completion_count > 1`，也只算1个完成任务）
- **重要**：`completedTasks` 是任务维度的计数，不是完成次数

### 6.4 前端注意事项
- 完成率字段为字符串类型（包含 `%` 符号）
- 显示时直接使用，无需再格式化
- 进度条组件需解析百分比数值：`parseFloat(completionRate)` 

## 七、修复历史

### 2026-06-24 修复内容
1. **新增自动状态更新**：`updateParticipationScore()` 现在会自动更新 `completion_status`
2. **新增状态计算方法**：`calculateCompletionStatus()` 根据完成规则判定状态
3. **统一完成率格式**：三个统计接口（概览/班级/学院）完成率格式统一为 `"%.1f%%"`

### 修复前问题
- `completion_status` 只在报名时设置为 `IN_PROGRESS`，永远不会变为 `COMPLETED`
- 导致完成率永远是 `0.0%`，即使用户完成了所有任务

### 修复后效果
- 任务完成后自动更新状态
- 完成率正确反映实际完成情况
- 三个统计接口返回格式一致

---

**更新日期**：2026-06-24  
**负责人**：Claude
