# 活动任务重复积分功能说明

## 功能概述

活动系统现在支持任务重复积分机制，允许学生多次完成同一任务并重复获得积分。管理员可以灵活配置每类任务的得分上限。

## 核心特性

### 1. 重复积分机制
- **可重复任务**：每次达标都积分（如每天阅读30分钟得1分）
- **一次性任务**：传统模式，完成一次即标记完成（如上传课文）

### 2. 积分上限控制

#### 总次数上限（max_completions）
限制整个活动期间的最大完成次数。

**示例**：学习新单词任务
- 目标：学习50个单词
- 完成得分：2分
- 总上限：20次
- **效果**：学生最多可完成20次，总计获得40分

#### 单日上限（daily_limit）
限制每天的最大完成次数。

**示例**：每日阅读任务
- 目标：阅读30分钟
- 完成得分：1分
- 日上限：3次
- **效果**：学生每天最多完成3次，最多获得3分

#### 无限制
将上限设置为空（NULL）表示不限制。

### 3. 自动重置机制
- 系统每天0点后首次完成任务时，自动重置当日计数
- 不需要手动操作或定时任务

## 审核机制

### 课文上传任务特殊规则

**重要**：TEXT_UPLOAD 类型的任务只在**审核通过后**才计入积分。

#### 流程说明

1. **学生上传课文**
   - 学生在活动期间上传课文
   - 课文状态为 `PENDING`（待审核）
   - **此时不计入积分**

2. **管理员审核**
   - 审核通过：状态变更为 `APPROVED`
   - 审核拒绝：状态变更为 `REJECTED`

3. **数据采集与积分**
   - 系统采集数据时，只统计 `status = 'APPROVED'` 的课文
   - 审核通过的课文才会计入完成次数
   - 拒绝的课文不计入积分

4. **重复上传（如果配置为可重复）**
   - 学生可以继续上传新课文
   - 每篇审核通过的课文都计入一次完成
   - 受总上限和单日上限约束

#### 配置示例

**场景1：上传课文，一次性任务**
```
任务名称：上传课文
任务类型：文章上传
目标值：1 篇
完成得分：5
可重复：✗（关闭）
```
- 学生只需上传1篇课文
- 审核通过后得5分
- 不可重复上传获得更多分数

**场景2：鼓励多次上传，设置上限**
```
任务名称：积极分享课文
任务类型：文章上传
目标值：1 篇
完成得分：3
可重复：✓（开启）
总上限：5
日上限：2
```
- 学生每上传1篇审核通过的课文得3分
- 每天最多2篇（防止刷分）
- 整个活动最多5篇（总计15分）

## 数据库架构

### campaign_tasks 表新增字段
```sql
is_repeatable TINYINT(1) DEFAULT 0 -- 是否可重复完成
max_completions INT DEFAULT NULL   -- 最大完成次数（NULL=无限制）
daily_limit INT DEFAULT NULL       -- 单日最大完成次数（NULL=无限制）
```

### campaign_task_completions 表新增字段
```sql
earned_score INT DEFAULT 0              -- 已获得总积分
last_completion_date DATE               -- 最后一次完成日期
today_completion_count INT DEFAULT 0    -- 今日完成次数
```

## 使用场景

### 场景1：每日打卡类
```
任务：每日阅读
- 阅读30分钟得1分
- 每天最多3次
- 鼓励学生每天多次学习
配置：is_repeatable=1, daily_limit=3
```

### 场景2：累计目标类
```
任务：单词学习
- 学习50个单词得2分
- 整个活动最多完成20次
- 控制总得分上限为40分
配置：is_repeatable=1, max_completions=20
```

### 场景3：一次性任务
```
任务：上传课文
- 上传1篇课文得5分
- 审核通过后计入积分
- 只能完成一次
配置：is_repeatable=0
```

### 场景4：灵活结合
```
任务：AI问答
- 完成10次AI问答得1分
- 每天最多5次
- 整个活动最多50次
- 既限制单日刷分，又控制总上限
配置：is_repeatable=1, daily_limit=5, max_completions=50
```

## 前端界面

### 活动表单（创建/编辑活动）
管理员可以在任务配置表格中设置：
- **可重复**：开关按钮
- **总上限**：输入框（只在可重复时显示）
- **日上限**：输入框（只在可重复时显示）

### 活动详情
显示任务的重复积分配置：
- 是否可重复
- 总上限（如设置）
- 日上限（如设置）

## 技术细节

### 积分计算逻辑

```java
// 达标时检查是否可以继续积分
if (task.isRepeatable) {
    // 检查单日限制
    if (dailyLimit != null && todayCount >= dailyLimit) {
        return; // 今日已达上限
    }
    
    // 检查总次数限制
    if (maxCompletions != null && completionCount >= maxCompletions) {
        return; // 总次数已达上限
    }
    
    // 可以积分
    completionCount++;
    todayCount++;
    earnedScore += points;
}
```

### TEXT_UPLOAD 数据采集

```java
case TEXT_UPLOAD:
    // 只统计审核通过的课文
    QueryWrapper<Text> wrapper = new QueryWrapper<>();
    wrapper.eq("uploaded_by", userId);
    wrapper.eq("status", "APPROVED"); // 关键：只统计审核通过的
    wrapper.eq("deleted", 0);
    wrapper.ge("create_time", campaign.getStartTime());
    wrapper.le("create_time", campaign.getEndTime());
    long approvedCount = textMapper.selectCount(wrapper);
    return BigDecimal.valueOf(approvedCount);
```

## 注意事项

1. **审核时效性**：课文审核越及时，学生越快获得积分反馈
2. **数据采集周期**：默认每小时自动采集一次数据
3. **缓存更新**：积分统计使用缓存，手动录入或审核后建议触发数据采集
4. **日期边界**：单日上限以系统时间的日期（LocalDate）为准
5. **活动时间范围**：只统计活动开始到结束期间的数据

## 部署状态

✅ 数据库迁移：V21__campaign_repeatable_tasks.sql
✅ 后端实现：CampaignStatsServiceImpl
✅ 前端界面：CampaignForm.vue, CampaignDetail.vue
✅ 审核机制：TEXT_UPLOAD 只统计 APPROVED 状态

## 更新日期

2026-06-24
