# 活动系统全面修复优先级清单

## 执行摘要

通过多 Agent 深度审查，共发现 **42 个问题**：
- 🔴 **P0 严重**: 10 个（必须立即修复）
- 🟡 **P1 高危**: 11 个（本周修复）
- 🟠 **P2 中危**: 13 个（下次迭代）
- 🟢 **P3 低危**: 8 个（技术债务）

**预计修复时间**: P0/P1 = 3-5 天，P2/P3 = 1-2 周

---

## P0 严重问题（必须立即修复）

### 🔴 P0-01: 报名人数竞态条件
- **来源**: Security Audit - HIGH-06
- **文件**: `CampaignEnrollmentServiceImpl.java:77-85`
- **问题**: 并发报名可突破 maxParticipants 限制
- **影响**: 数据一致性破坏，业务逻辑失效
- **修复时间**: 4 小时
- **修复方案**:
```java
@Transactional(isolation = Isolation.READ_COMMITTED)
public void enrollCampaign(Long campaignId, Long userId) {
    // 1. 唯一约束防重复
    // 2. 乐观锁更新计数
    // 3. 回滚机制
}
```

### 🔴 P0-02: 管理员操作缺少活动归属验证
- **来源**: Security Audit - CRITICAL-01
- **文件**: `CampaignEnrollmentController.java:72-98`
- **问题**: 可操作其他活动的报名记录
- **影响**: 权限绕过，数据篡改
- **修复时间**: 2 小时
- **修复方案**:
```java
public void approveEnrollment(Long campaignId, Long participationId) {
    CampaignParticipation p = participationMapper.selectById(participationId);
    if (!p.getCampaignId().equals(campaignId)) {
        throw new BusinessException("报名记录不属于该活动");
    }
}
```

### 🔴 P0-03: 缺少关键数据库索引
- **来源**: Performance Analysis - P0-01,02,03
- **问题**: 排名查询 5 秒，10K 用户时 CPU 100%
- **影响**: 用户体验差，系统负载高
- **修复时间**: 1 小时
- **修复方案**:
```sql
ALTER TABLE campaign_participations
ADD INDEX idx_enrollment_score (campaign_id, enrollment_status, total_score DESC);

ALTER TABLE campaign_task_completions
ADD INDEX idx_campaign_task_user (campaign_id, task_id, user_id);

ALTER TABLE campaign_tasks
ADD INDEX idx_campaign_required (campaign_id, is_required, deleted);
```

### 🔴 P0-04: N+1 查询问题（排名用户信息填充）
- **来源**: Code Review - #7, Performance Analysis - P0-04
- **文件**: `CampaignStatsServiceImpl.java:317-384`
- **问题**: 100 个参与者 = 300+ 数据库查询
- **影响**: 响应慢（2-5 秒），数据库连接耗尽
- **修复时间**: 3 小时
- **修复方案**: 批量查询替代循环查询

### 🔴 P0-05: 全表扫描（班级/学院排名）
- **来源**: Performance Analysis - P0-06
- **文件**: `CampaignStatsServiceImpl.java:387-545`
- **问题**: 加载所有数据到内存分组
- **影响**: OOM 风险（10K+ 用户）
- **修复时间**: 4 小时
- **修复方案**: 使用 SQL GROUP BY 聚合

### 🔴 P0-06: 数据采集无边界（OOM 风险）
- **来源**: Performance Analysis - P0-07
- **文件**: `CampaignStatsServiceImpl.java:51-76`
- **问题**: 一次查询所有参与者数据
- **影响**: 100K 用户时内存溢出
- **修复时间**: 2 小时
- **修复方案**: 添加分页处理

### 🔴 P0-07: SQL 注入风险（关键字搜索）
- **来源**: Security Audit - HIGH-04
- **文件**: `CampaignEnrollmentServiceImpl.java:152-154`
- **问题**: 未转义 SQL 通配符
- **影响**: 性能攻击、数据泄露
- **修复时间**: 1 小时
- **修复方案**: 转义 `%` `_` `\` 字符

### 🔴 P0-08: 用户敏感信息批量暴露
- **来源**: Security Audit - HIGH-10
- **文件**: `CampaignStatsServiceImpl.java:300-312`
- **问题**: 学生可查看所有人真实姓名、学号
- **影响**: 隐私泄露，合规风险
- **修复时间**: 3 小时
- **修复方案**: 学生端脱敏处理

### 🔴 P0-09: 批量操作无限制
- **来源**: Security Audit - HIGH-12
- **文件**: `CampaignEnrollmentServiceImpl.java:315-333`
- **问题**: 可提交超大列表导致系统崩溃
- **影响**: DoS 攻击
- **修复时间**: 2 小时
- **修复方案**: 限制 1000 条/次

### 🔴 P0-10: 退出后可立即重新报名
- **来源**: Security Audit - HIGH-07
- **文件**: `CampaignEnrollmentServiceImpl.java:108-121`
- **问题**: 可刷取报名奖励
- **影响**: 业务逻辑漏洞
- **修复时间**: 2 小时
- **修复方案**: 检查 WITHDRAWN 历史记录

---

## P1 高危问题（本周修复）

### 🟡 P1-01: 批量操作缺少操作日志
- **来源**: Code Review - #12, Security Audit - HIGH-03
- **文件**: `CampaignEnrollmentServiceImpl.java:315-333`
- **修复时间**: 2 小时

### 🟡 P1-02: 缺少事务保护（批准/驳回）
- **来源**: Code Review - #6
- **文件**: `CampaignEnrollmentServiceImpl.java:274-301`
- **修复时间**: 1 小时

### 🟡 P1-03: 窗口查询效率低（我的排名）
- **来源**: Performance Analysis - P1-01
- **文件**: `CampaignStatsServiceImpl.java:711-714`
- **修复时间**: 3 小时

### 🟡 P1-04: 输入验证缺失（驳回原因）
- **来源**: Security Audit - MEDIUM-05
- **文件**: `CampaignEnrollmentController.java:86-98`
- **修复时间**: 1 小时

### 🟡 P1-05: 报名时间边界未验证
- **来源**: Security Audit - MEDIUM-08
- **文件**: `CampaignEnrollmentServiceImpl.java:59-105`
- **修复时间**: 1 小时

### 🟡 P1-06: 错误消息泄露内部信息
- **来源**: Security Audit - MEDIUM-11
- **文件**: 多个 Controller
- **修复时间**: 2 小时

### 🟡 P1-07: 数据采集无频率限制
- **来源**: Security Audit - MEDIUM-13
- **文件**: `CampaignStatsServiceImpl.java:51-76`
- **修复时间**: 2 小时

### 🟡 P1-08: 缺少 NOT NULL 约束
- **来源**: Performance Analysis - P1-03
- **文件**: 数据库 schema
- **修复时间**: 1 小时

### 🟡 P1-09: 状态转换验证不完整
- **来源**: Code Review - #8
- **文件**: `CampaignEnrollmentServiceImpl.java:274-301`
- **修复时间**: 2 小时

### 🟡 P1-10: 大结果集内存问题
- **来源**: Code Review - #9
- **文件**: `CampaignEnrollmentServiceImpl.java:167-198`
- **修复时间**: 3 小时

### 🟡 P1-11: 级联删除风险
- **来源**: Performance Analysis - P1-04
- **文件**: 数据库外键定义
- **修复时间**: 2 小时

---

## P2 中危问题（下次迭代）

### 🟠 P2-01: QueryWrapper 潜在重用风险
- **来源**: Code Review - #5
- **文件**: `CampaignEnrollmentServiceImpl.java`
- **修复**: 添加注释说明

### 🟠 P2-02: 硬编码字符串不一致
- **来源**: Code Review - #11
- **文件**: `CampaignEnrollmentServiceImpl.java:339,350`
- **修复**: 统一使用枚举常量

### 🟠 P2-03: 代码重复（用户信息填充）
- **来源**: Code Review - #15
- **文件**: 两个 ServiceImpl
- **修复**: 提取公共方法

### 🟠 P2-04: enrollment_target_ids 应为 JSON 类型
- **来源**: Performance Analysis - P2-01
- **修复**: 数据库迁移

### 🟠 P2-05: V21 迁移不幂等
- **来源**: Performance Analysis - P2-02
- **修复**: 添加条件检查

### 🟠 P2-06-13: 其他中危问题
- 缺少索引（非关键查询）
- 前端错误处理改进
- 缓存策略优化
- 日志格式统一

---

## P3 低危问题（技术债务）

### 🟢 P3-01-08: 代码质量改进
- 添加单元测试
- 改进代码注释
- 统一异常处理
- 性能监控埋点

---

## 修复计划

### 第 1 天（P0 安全问题）
- [ ] P0-02: 管理员权限验证（2h）
- [ ] P0-07: SQL 注入防护（1h）
- [ ] P0-08: 敏感信息脱敏（3h）
- [ ] P0-09: 批量操作限制（2h）

### 第 2 天（P0 并发问题）
- [ ] P0-01: 报名竞态条件（4h）
- [ ] P0-10: 重复报名防护（2h）
- [ ] P0-06: 数据采集分页（2h）

### 第 3 天（P0 性能问题）
- [ ] P0-03: 添加数据库索引（1h）
- [ ] P0-04: N+1 查询优化（3h）
- [ ] P0-05: 全表扫描优化（4h）

### 第 4-5 天（P1 问题）
- [ ] 所有 P1 问题修复
- [ ] 单元测试补充
- [ ] 集成测试验证

---

## 测试验证计划

### 安全测试
```bash
# 1. 权限绕过测试
curl -X PUT /api/admin/campaigns/1/enrollments/999/approve
# 预期: 403 或 "不属于该活动"

# 2. SQL 注入测试
curl /api/student/campaigns?keyword=%%%
# 预期: 正常返回，无性能问题

# 3. 并发报名测试
ab -n 200 -c 50 POST /api/student/campaigns/1/enroll
# 预期: maxParticipants 限制生效
```

### 性能测试
```bash
# 1. 排名查询压测
ab -n 1000 -c 10 GET /api/admin/campaigns/1/stats/ranking/individual
# 预期: 响应时间 < 100ms

# 2. 数据采集压测
# 预期: 10K 用户 < 2s，无 OOM
```

---

## 部署策略

### 分阶段发布
1. **阶段 1（P0 安全）**: 紧急热修复
   - 索引创建（无停机）
   - 权限验证补丁
   - SQL 注入防护

2. **阶段 2（P0 性能）**: 滚动更新
   - N+1 优化
   - 全表扫描改造
   - 并发控制

3. **阶段 3（P1）**: 常规发布
   - 事务优化
   - 输入验证
   - 日志审计

### 回滚方案
- 数据库索引：可直接 DROP INDEX
- 代码变更：Git revert + 重新部署
- 数据迁移：保留备份 7 天

---

## 资源需求

### 人力
- 后端开发: 2 人 × 5 天
- DBA: 0.5 天（索引优化）
- QA: 1 人 × 2 天（测试验证）

### 环境
- 测试环境：必需
- 压测环境：必需（10K+ 数据）
- 生产环境：分阶段

---

## 风险评估

| 风险 | 概率 | 影响 | 应对措施 |
|------|------|------|----------|
| 索引创建锁表 | 中 | 高 | 使用 ONLINE DDL |
| 并发控制死锁 | 低 | 高 | 添加超时 + 监控 |
| 性能回退 | 低 | 中 | A/B 测试 + 回滚 |
| 数据不一致 | 低 | 高 | 迁移前全量备份 |

---

## 成功指标

### 性能指标
- 排名查询: 5s → < 100ms（50x 提升）
- 班级排名: 8s → < 200ms（40x 提升）
- 数据采集: 10s → < 2s（5x 提升）

### 安全指标
- 权限绕过: 0 个
- SQL 注入: 0 个
- 信息泄露: 0 个

### 稳定性指标
- 并发报名: 100% 准确
- OOM 错误: 0 个
- 事务回滚: < 1%

---

**优先级顺序**: 安全 > 数据一致性 > 性能 > 代码质量
**目标完成时间**: P0/P1 = 1 周，P2 = 2 周
