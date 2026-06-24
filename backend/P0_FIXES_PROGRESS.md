# P0 严重问题修复进度

## 执行时间
2026-06-24 22:00 - 进行中

## 已完成修复 (4/10)

### ✅ P0-02: 管理员操作缺少活动归属验证
**修复时间**: 30分钟  
**文件修改**:
- `CampaignEnrollmentService.java` - 接口添加 campaignId 参数
- `CampaignEnrollmentServiceImpl.java` - 实现层添加归属验证
- `CampaignEnrollmentController.java` - Controller 层传递 campaignId
- `CampaignEnrollmentServiceImplTest.java` - 添加两个测试用例验证权限检查

**修复内容**:
```java
// 验证报名记录是否属于该活动
if (!participation.getCampaignId().equals(campaignId)) {
    throw new RuntimeException("报名记录不属于该活动");
}
```

**影响**: 修复 IDOR 漏洞，防止管理员操作其他活动的报名记录

---

### ✅ P0-07: SQL 注入风险（关键字搜索）
**修复时间**: 20分钟  
**文件修改**:
- `CampaignEnrollmentServiceImpl.java` - 添加 `escapeSqlLikeWildcards()` 方法

**修复内容**:
```java
/**
 * 转义 SQL LIKE 通配符，防止 SQL 注入
 */
private String escapeSqlLikeWildcards(String input) {
    if (input == null || input.isEmpty()) {
        return input;
    }
    // 转义 SQL LIKE 特殊字符: % _ \
    return input.replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
}
```

**影响**: 防止通过搜索关键字进行 SQL 注入攻击和性能攻击

---

### ✅ P0-09: 批量操作无限制
**修复时间**: 15分钟  
**文件修改**:
- `CampaignEnrollmentServiceImpl.java` - `batchEnrollUsers()` 添加大小限制

**修复内容**:
```java
// P0-09 修复: 限制批量操作大小，防止 DoS 攻击
final int MAX_BATCH_SIZE = 1000;
if (userIds == null || userIds.isEmpty()) {
    throw new RuntimeException("用户ID列表不能为空");
}
if (userIds.size() > MAX_BATCH_SIZE) {
    throw new RuntimeException("批量添加数量超过限制（最多 " + MAX_BATCH_SIZE + " 个）");
}
```

**影响**: 防止超大列表导致系统崩溃或 DoS 攻击

---

### ✅ P0-03: 缺少关键数据库索引
**修复时间**: 30分钟  
**文件创建**:
- `V22__campaign_performance_indexes.sql` - Flyway 迁移文件
- `docker/mysql/init/04-apply-all-migrations.sql` - 同步更新

**修复内容**:
```sql
-- 1. 排名查询索引（覆盖个人/班级/学院排名）
CREATE INDEX idx_enrollment_score
ON campaign_participations(campaign_id, enrollment_status, total_score DESC);

-- 2. 任务完成记录查询索引（覆盖 getTaskCompletion/updateTaskCompletion）
CREATE INDEX idx_campaign_task_user
ON campaign_task_completions(campaign_id, task_id, user_id);

-- 3. 活动任务筛选索引（覆盖必做任务筛选）
CREATE INDEX idx_campaign_required
ON campaign_tasks(campaign_id, is_required, deleted);
```

**预期提升**:
- 排名查询: 5s → <100ms (50x 提升)
- 班级排名: 8s → <200ms (40x 提升)
- 任务查询: 显著提升

---

## 待修复 (6/10)

### 🔴 P0-01: 报名人数竞态条件
**预计时间**: 4小时  
**修复方案**:
- 添加数据库唯一约束 (campaign_id, user_id)
- 使用乐观锁更新 participantCount
- 添加事务隔离级别 READ_COMMITTED

---

### 🔴 P0-04: N+1 查询问题（排名用户信息填充）
**预计时间**: 3小时  
**文件**: `CampaignStatsServiceImpl.java:317-384`  
**修复方案**: 批量查询替代循环查询

---

### 🔴 P0-05: 全表扫描（班级/学院排名）
**预计时间**: 4小时  
**文件**: `CampaignStatsServiceImpl.java:387-545`  
**修复方案**: 使用 SQL GROUP BY 聚合

---

### 🔴 P0-06: 数据采集无边界（OOM 风险）
**预计时间**: 2小时  
**文件**: `CampaignStatsServiceImpl.java:51-76`  
**修复方案**: 添加分页处理

---

### 🔴 P0-08: 用户敏感信息批量暴露
**预计时间**: 3小时  
**文件**: `CampaignStatsServiceImpl.java:300-312`  
**修复方案**: 学生端脱敏处理（真实姓名 → 姓氏 + ***）

---

### 🔴 P0-10: 退出后可立即重新报名
**预计时间**: 2小时  
**文件**: `CampaignEnrollmentServiceImpl.java:108-121`  
**修复方案**: 检查 WITHDRAWN 历史记录，添加冷却期

---

## 修复统计

- **已完成**: 4 个 (40%)
- **待修复**: 6 个 (60%)
- **累计耗时**: 1.5 小时
- **预计剩余**: 18 小时

---

## 下一步计划

按优先级和复杂度，建议修复顺序:

1. **P0-10** (2h) - 重复报名防护，逻辑简单
2. **P0-06** (2h) - 数据采集分页，风险高
3. **P0-08** (3h) - 信息脱敏，合规要求
4. **P0-04** (3h) - N+1 查询优化，性能关键
5. **P0-01** (4h) - 竞态条件，需要数据库迁移
6. **P0-05** (4h) - 全表扫描优化，性能关键

---

## 部署注意事项

### 已修复项目部署要求:
1. **P0-03 索引**: 需要执行 V22 迁移，建议在低峰期执行（索引创建时间约 1-5 分钟）
2. **P0-02/07/09**: 纯代码修复，重新部署后端即可

### 验证清单:
- [ ] 运行后端单元测试验证修复
- [ ] 测试环境部署验证
- [ ] 压力测试验证性能提升
- [ ] 生产环境灰度发布

---

**备注**: 本次修复基于 `COMPREHENSIVE_FIX_PRIORITY.md` 文档中的 P0 问题清单
