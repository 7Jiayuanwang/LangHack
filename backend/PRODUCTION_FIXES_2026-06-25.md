# 生产环境问题修复报告 (2026-06-25)

## 问题来源
根据生产日志 `langhack-20260625-165659.log` 分析发现的严重问题

---

## 修复清单

### 🔴 P0-01: 活动数据采集失败 - texts 表列名错误

**症状**:
- 36+ 次重复错误: `Unknown column 'create_time' in 'where clause'`
- SQL: `SELECT COUNT(*) FROM texts WHERE uploaded_by = ? AND status = ? AND deleted = ? AND create_time >= ? AND create_time <= ?`

**根因**:
- `CampaignStatsServiceImpl.java` 中使用了错误的列名 `create_time`
- 实际数据库列名为 `created_time`

**影响**:
- 活动任务 #8 (课文上传统计) 数据采集完全失败
- 影响所有参与用户的积分统计

**修复**:
```java
// Before
textWrapper.ge("create_time", campaign.getStartTime());
textWrapper.le("create_time", campaign.getEndTime());

// After
textWrapper.ge("created_time", campaign.getStartTime());
textWrapper.le("created_time", campaign.getEndTime());
```

**修复文件**: `backend/src/main/java/com/english/service/impl/CampaignStatsServiceImpl.java`

---

### 🟡 P1-01: 日统计聚合重复键错误

**症状**:
```
Duplicate entry '2026-06-24' for key 'access_stats_daily.uk_stat_date'
```

**根因**:
- `AccessStatsAggregationService.aggregateDaily()` 直接执行 `insert` 操作
- 未检查记录是否已存在
- 当任务重复执行或恢复时会触发唯一键冲突

**影响**:
- 日统计聚合任务失败
- 数据完整性问题

**修复**:
```java
// Before
dailyMapper.insert(daily);

// After
QueryWrapper<AccessStatsDaily> dailyWrapper = new QueryWrapper<>();
dailyWrapper.eq("stat_date", yesterday);
AccessStatsDaily existing = dailyMapper.selectOne(dailyWrapper);

if (existing != null) {
    daily.setId(existing.getId());
    dailyMapper.updateById(daily);
    logger.info("Updated existing daily access stats for {}", yesterday);
} else {
    dailyMapper.insert(daily);
    logger.info("Aggregated daily access stats for {}", yesterday);
}
```

**修复文件**: `backend/src/main/java/com/english/service/impl/AccessStatsAggregationService.java`

---

### 🟡 P1-02: 相似度矩阵 RENAME TABLE 失败

**症状**:
```
Table 'text_similarity_matrix_old' already exists
RENAME TABLE text_similarity_matrix TO text_similarity_matrix_old, 
             text_similarity_matrix_tmp TO text_similarity_matrix
```

**根因**:
- 上一次执行失败后残留 `_old` 表
- MySQL RENAME TABLE 是原子操作，如果目标表已存在则失败
- 没有清理机制

**影响**:
- 每日凌晨 3:00 相似度矩阵重算任务失败
- 推荐系统数据无法更新

**修复**:
1. 添加清理方法:
```java
// SimilarityMatrixMapper.java
@Update("DROP TABLE IF EXISTS text_similarity_matrix_old")
int dropOldTextIfExists();

@Update("DROP TABLE IF EXISTS user_similarity_matrix_old")
int dropOldUserIfExists();
```

2. 在 RENAME 前执行清理:
```java
// SimilarityMatrixScheduleTask.java
log.info("[SimilarityMatrix] Step2.5: 清理残留的旧表（防止 RENAME 失败）...");
similarityMatrixMapper.dropOldTextIfExists();

log.info("[SimilarityMatrix] Step3: 执行原子 RENAME TABLE 切换...");
similarityMatrixMapper.atomicRenameToShadowText();
```

**修复文件**:
- `backend/src/main/java/com/english/mapper/SimilarityMatrixMapper.java`
- `backend/src/main/java/com/english/config/SimilarityMatrixScheduleTask.java`

---

## 部署验证

### 编译测试
- ✅ Maven 编译成功
- ✅ Docker 镜像构建成功
- ✅ 容器启动成功（11.14 秒）

### 运行时验证
- ✅ 后端服务健康运行
- ✅ 无 `create_time` 相关错误
- ⏳ 等待下次数据采集任务验证 (每小时一次)
- ⏳ 等待下次日统计聚合验证 (00:15)
- ⏳ 等待下次相似度矩阵重算验证 (03:00)

---

## 提交记录

### 主要修复
- `0b34825c` - fix(backend): 修复生产环境三个严重问题
- `16735381` - fix(backend): 修复 AccessStatsAggregationService 变量名冲突

### 关联修复 (feature/campaign-system 分支)
- `3d1f6403` - fix(campaign): 修复 CampaignStatsServiceImpl 编译错误
- `92887744` - docs(campaign): 更新 P0 修复报告 - 添加部署验证结果
- `64cf05c4` - fix(campaign): 全面修复 P0 严重问题（10/10）

---

## 后续监控

### 关键日志监控
1. **活动数据采集** (每小时 XX:02:59)
   - 关注: `采集用户.*任务8数据失败`
   - 预期: 不再出现 `create_time` 错误

2. **日统计聚合** (每天 00:15:02)
   - 关注: `Duplicate entry.*uk_stat_date`
   - 预期: 改为 "Updated existing" 或 "Aggregated daily"

3. **相似度矩阵重算** (每天 03:00:00)
   - 关注: `Table.*_old.*already exists`
   - 预期: 改为 "清理残留的旧表" → "RENAME TABLE 执行成功"

### 性能指标
- 活动数据采集成功率目标: 100%
- 日统计聚合成功率目标: 100%
- 相似度矩阵重算成功率目标: 100%

---

## 风险评估

### 低风险
- ✅ texts 列名修复: 纯 bug 修复，无副作用
- ✅ 日统计聚合: 幂等性改进，兼容现有数据

### 中风险
- ⚠️ 相似度矩阵清理: 
  - 风险: 如果 `_old` 表被手动用于其他目的，会被删除
  - 缓解: 该表仅用于 Shadow Table 策略，不应有外部依赖
  - 验证: 首次执行时观察日志确认流程正常

---

## 测试建议

### 手动测试
1. 创建一个包含"课文上传"任务的测试活动
2. 上传课文并等待数据采集任务运行
3. 检查用户进度统计是否正确

### 压力测试
1. 模拟日统计聚合任务重复执行
2. 验证不会产生 duplicate key 错误
3. 验证数据更新而非重复插入

### 定时任务验证
1. 观察 03:00 相似度矩阵重算任务
2. 确认清理步骤正常执行
3. 确认 RENAME 操作成功

---

**修复完成时间**: 2026-06-25 17:09  
**验证状态**: 编译通过 + 运行时部署成功  
**负责人**: Dev Team  
**下一步**: 等待生产环境运行验证 (24-48 小时)
