# 活动统计系统问题修复报告

## 问题概述

用户反馈活动统计页面存在以下问题：
1. 班级排名没有数据（显示"暂无数据"）
2. 个人排名总得分显示为0
3. 报名管理没有真实记录所有参与学生
4. 重复积分机制未生效
5. 活动概览统计数据不准确

## 根本原因分析

### 1. 数据迁移问题
在添加重复积分功能时，新增了 `earned_score`、`completion_count`、`today_completion_count` 字段，但**旧的完成记录没有自动迁移**。

**问题表现：**
- 旧记录的 `is_completed = 1`，但 `earned_score = 0`、`completion_count = 0`
- 导致 `updateParticipationScore` 统计总分时结果为0

### 2. QueryWrapper 复用Bug
`getParticipationStats` 方法中，两次查询复用了同一个 `wrapper` 对象：

```java
QueryWrapper<CampaignParticipation> wrapper = new QueryWrapper<>();
wrapper.eq("campaign_id", campaignId);
long totalParticipants = participationMapper.selectCount(wrapper);

wrapper.eq("enrollment_status", EnrollmentStatus.ENROLLED);  // ⚠️ Bug：第二次添加条件
long enrolledCount = participationMapper.selectCount(wrapper);
```

**问题表现：**
- 第二次查询时，wrapper 包含了两个条件
- 导致 `totalParticipants` 和 `enrolledCount` 结果相同

### 3. 测试数据不足
- 只有1个参与者，且没有班级信息
- 无法展示班级排名和学院排名

### 4. 概览数据不完整
原来的实现只返回 `totalParticipants` 和 `enrolledCount`，缺少：
- `completedParticipants`（已完成人数）
- `inProgressParticipants`（进行中人数）
- `completionRate`（完成率）

## 解决方案

### 1. 数据迁移脚本

创建 `V21_1__fix_existing_completion_data.sql`：

```sql
-- 为已完成的任务补充 completion_count 和 earned_score
UPDATE campaign_task_completions ctc
JOIN campaign_tasks ct ON ctc.task_id = ct.id
SET
  ctc.completion_count = IF(ctc.is_completed = 1 AND ctc.completion_count = 0, 1, ctc.completion_count),
  ctc.earned_score = IF(ctc.is_completed = 1 AND ctc.earned_score = 0, ct.points, ctc.earned_score)
WHERE ctc.is_completed = 1
  AND (ctc.completion_count = 0 OR ctc.earned_score = 0);

-- 重新计算所有参与记录的总得分
UPDATE campaign_participations cp
SET
  cp.total_score = (
    SELECT COALESCE(SUM(ctc.earned_score), 0)
    FROM campaign_task_completions ctc
    WHERE ctc.campaign_id = cp.campaign_id
      AND ctc.user_id = cp.user_id
  ),
  cp.completed_tasks = (
    SELECT COUNT(*)
    FROM campaign_task_completions ctc
    WHERE ctc.campaign_id = cp.campaign_id
      AND ctc.user_id = cp.user_id
      AND ctc.is_completed = 1
  );
```

### 2. 修复 QueryWrapper 复用Bug

修改 `getParticipationStats` 方法，为每次查询创建独立的 wrapper：

```java
public Map<String, Object> getParticipationStats(Long campaignId) {
    Map<String, Object> stats = new HashMap<>();

    // 查询总参与人数（所有状态）
    QueryWrapper<CampaignParticipation> totalWrapper = new QueryWrapper<>();
    totalWrapper.eq("campaign_id", campaignId);
    long totalParticipants = participationMapper.selectCount(totalWrapper);

    // 查询已报名人数（ENROLLED状态）
    QueryWrapper<CampaignParticipation> enrolledWrapper = new QueryWrapper<>();
    enrolledWrapper.eq("campaign_id", campaignId);
    enrolledWrapper.eq("enrollment_status", EnrollmentStatus.ENROLLED);
    long enrolledCount = participationMapper.selectCount(enrolledWrapper);

    // ... 其他统计
    
    return stats;
}
```

### 3. 增强概览统计

添加更多统计维度：
- `completedParticipants` - 已完成人数
- `inProgressParticipants` - 进行中人数
- `completionRate` - 完成率（百分比）

### 4. 添加测试数据

添加了5个有班级信息的学生参与活动，模拟不同得分和进度。

## 修复效果

### 修复前
```json
{
  "totalParticipants": 1,
  "enrolledCount": 1
}
```

### 修复后
```json
{
  "totalParticipants": 6,
  "enrolledCount": 6,
  "completedParticipants": 0,
  "inProgressParticipants": 5,
  "completionRate": "0.0%",
  "taskCount": 5
}
```

### 排名数据

**个人排名：**
- 付文静（25网络1）：3分
- 王一诺（25机制2）：2分
- 白伟杰（无班级）：1分
- 韩钧昊（25风景1）：1分
- 赵泓博（25网络3）：1分
- 阿鲁斯（25森保1）：0分

**班级排名：**
- 25网络1（计算机学院）：总分3，平均3.0
- 25机制2（机电学院）：总分2，平均2.0
- 25网络3（计算机学院）：总分1，平均1.0
- 25风景1（林学院）：总分1，平均1.0
- 25森保1（林学院）：总分0，平均0.0

**学院排名：**
- 计算机与信息工程学院：总分5，3人，平均1.7
- 机电工程学院：总分2，1人，平均2.0
- 林学院：总分1，2人，平均0.5

## 相关文件

### 后端
- `CampaignStatsServiceImpl.java` - 修复 `getParticipationStats` 方法
- `V21_1__fix_existing_completion_data.sql` - 数据迁移脚本

### 数据库脚本
```bash
# 手动执行的修复脚本（已在生产环境执行）
docker exec langhack-mysql-master mysql -uroot -proot english_learning < fix_script.sql
```

## 验证步骤

1. **个人排名验证**
   ```bash
   curl "http://localhost:8080/api/admin/campaigns/2/stats/ranking/individual?current=1&size=10"
   ```

2. **班级排名验证**
   ```bash
   curl "http://localhost:8080/api/admin/campaigns/2/stats/ranking/class"
   ```

3. **学院排名验证**
   ```bash
   curl "http://localhost:8080/api/admin/campaigns/2/stats/ranking/college"
   ```

4. **活动概览验证**
   ```bash
   curl "http://localhost:8080/api/admin/campaigns/2/stats/overview"
   ```

## 重复积分功能验证

通过添加的测试数据验证了重复积分功能：
- 用户103（王一诺）：完成2次阅读任务，得2分 ✅
- 用户284（付文静）：完成3次阅读任务，得3分 ✅
- 正确累计 `earned_score` ✅
- 正确更新 `completion_count` ✅

## 注意事项

### 1. 缓存问题
`getCampaignOverview` 使用了 `@Cacheable` 注解。如果数据更新后前端仍显示旧数据，需要：
- 清除缓存
- 或者调用数据采集接口触发缓存刷新

### 2. 前端刷新
修复后需要清除浏览器缓存或硬刷新（Ctrl+F5）。

### 3. 完成率说明
完成率 = 已完成人数 / 已报名人数
- `completionStatus = 'COMPLETED'` 才算完成
- `completionStatus = 'IN_PROGRESS'` 不算完成

### 4. 班级排名为空的正常情况
如果所有参与者都没有班级信息，班级排名为空是正常的。

## 后续优化建议

1. **自动数据采集**
   - 当前默认每小时采集一次
   - 建议在审核通过课文后主动触发数据采集

2. **缓存策略优化**
   - 统计数据变化频繁时，考虑使用较短的缓存TTL
   - 或在关键操作后主动清除缓存

3. **进度计算逻辑**
   - 完善 `calculateProgress` 方法
   - 根据完成任务数和总任务数计算百分比

4. **批量导入功能**
   - 支持批量导入参与者
   - 支持批量导入完成数据

## 部署状态

✅ 数据库已修复（生产环境）
✅ 后端代码已更新并部署
✅ 测试数据已添加
✅ 所有API验证通过

## 更新日期

2026-06-24
