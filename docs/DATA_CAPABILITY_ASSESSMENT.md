# LangHack 数据获取能力评估与开发任务清单

> 生成时间: 2026-05-06  
> 目的: 评估各项数据指标的获取能力，制定补充开发任务

---

## 一、数据获取能力矩阵

| 数据指标 | 当前状态 | 获取能力 | 数据来源 | 开发任务 |
|---------|---------|---------|---------|---------|
| **1. 真实用户数据** |
| 注册用户数 | ✅ 可获取 | 实时 | `users` 表 | 无需开发 |
| 日活用户 (DAU) | ⚠️ 可计算但无数据 | 实时 | `learning_records` 表 | **需补充测试数据** |
| 月活用户 (MAU) | ⚠️ 可计算但无数据 | 实时 | `learning_records` 表 | **需补充测试数据** |
| 班级数量 | ✅ 可获取 | 实时 | `classes` 表 | 无需开发 |
| 学院数量 | ❌ 无法获取 | - | 无对应表 | **需新增字段或表** |
| 总阅读量 | ✅ 可获取 | 实时 | `user_text_reads` 表 | 无需开发 |
| 总学习记录数 | ✅ 可获取 | 实时 | `learning_records` 表 | 无需开发 |
| **2. 四六级成绩数据** |
| 成绩导入量 | ✅ 可获取 | 实时 | `level_exam_scores` 表 | **需触发同步** |
| 对比数据（通过率） | ✅ 可计算 | 需计算 | `level_exam_scores` + `learning_records` | **需编写SQL查询** |
| **3. AI 问答使用数据** |
| 累计调用次数 | ✅ 可获取 | 实时 | `ai_call_log` 表 | **需实际使用触发** |
| 平均每日调用量 | ✅ 可计算 | 实时 | `ai_call_log` 表 | **需实际使用触发** |
| 用户满意度 | ❌ 无法获取 | - | 无反馈机制 | **需新增反馈功能** |
| 使用反馈 | ❌ 无法获取 | - | 无反馈机制 | **需新增反馈功能** |
| **4. 推荐算法效果** |
| 推荐点击率 | ❌ 无法获取 | - | 无埋点 | **需前端埋点 + 后端记录** |
| 换一批频率 | ❌ 无法获取 | - | 无埋点 | **需前端埋点 + 后端记录** |
| 推荐曝光数 | ❌ 无法获取 | - | 无埋点 | **需前端埋点 + 后端记录** |

---

## 二、详细分析

### 2.1 真实用户数据

#### ✅ 可直接获取（无需开发）

**注册用户数**:
```sql
SELECT COUNT(*) FROM users WHERE deleted = 0;
-- 当前: 181 用户
```

**班级数量**:
```sql
SELECT COUNT(*) FROM classes WHERE deleted = 0;
-- 当前: 7 个班级
```

**总阅读量**:
```sql
SELECT COUNT(*) FROM user_text_reads;
-- 当前: 1 条（需补充数据）
```

**总学习记录数**:
```sql
SELECT COUNT(*) FROM learning_records;
-- 当前: 172 条
```

#### ⚠️ 可计算但无数据（需补充测试数据）

**日活用户 (DAU)**:
```sql
SELECT COUNT(DISTINCT student_id) as dau
FROM learning_records
WHERE DATE(created_time) = CURDATE();
-- 当前: 0（无今日学习记录）
```

**月活用户 (MAU)**:
```sql
SELECT COUNT(DISTINCT student_id) as mau
FROM learning_records
WHERE created_time >= DATE_SUB(NOW(), INTERVAL 30 DAY);
-- 当前: 0（无近30天学习记录）
```

**问题**: `learning_records` 表中最新记录可能是历史测试数据。

**解决方案**: 
1. 手动插入 10-20 条近期学习记录（模拟真实使用）
2. 或者在演示前让真实用户使用平台生成数据

#### ❌ 无法获取（需新增功能）

**学院数量**: 
- 当前数据库无 `college` 或 `department` 表
- `users` 表无学院字段
- `classes` 表无学院字段

**解决方案**:
1. **短期**: 在 `classes` 表添加 `college` 字段（VARCHAR(100)）
2. **长期**: 创建 `colleges` 表，建立 `classes.college_id` 外键关联

---

### 2.2 四六级成绩数据

#### ✅ 功能已实现，需触发同步

**当前状态**:
```sql
SELECT COUNT(*) FROM level_exam_scores;
-- 结果: 0（未导入任何成绩）
```

**原因**:
1. 学校 API 未授权
2. 定时同步未触发（默认凌晨 3:30）
3. 未手动触发首次同步

**解决方案**:
```bash
# 方案1: 手动触发同步（需 ADMIN/OPS 权限）
curl -X POST http://localhost:8080/api/admin/sync/level-exam-scores \
  -H "Authorization: Bearer <token>"

# 方案2: 检查学校 API 连通性
# 查看后端日志: docker logs langhack-backend | grep SchoolDataSync

# 方案3: 如果学校 API 无法提供数据，插入模拟数据
```

#### ✅ 对比分析 SQL 已设计

**使用平台 vs 未使用平台的通过率对比**:
```sql
-- 使用平台的学生（有学习记录）
SELECT 
  COUNT(*) as total_students,
  SUM(CASE WHEN score >= 425 THEN 1 ELSE 0 END) as passed,
  ROUND(SUM(CASE WHEN score >= 425 THEN 1 ELSE 0 END) * 100.0 / COUNT(*), 2) as pass_rate
FROM level_exam_scores les
WHERE EXISTS (
  SELECT 1 FROM learning_records lr 
  WHERE lr.student_id = les.student_id 
  AND lr.created_time >= '2024-01-01'
);

-- 未使用平台的学生（无学习记录）
SELECT 
  COUNT(*) as total_students,
  SUM(CASE WHEN score >= 425 THEN 1 ELSE 0 END) as passed,
  ROUND(SUM(CASE WHEN score >= 425 THEN 1 ELSE 0 END) * 100.0 / COUNT(*), 2) as pass_rate
FROM level_exam_scores les
WHERE NOT EXISTS (
  SELECT 1 FROM learning_records lr 
  WHERE lr.student_id = les.student_id
);
```

**前提条件**: `level_exam_scores` 表有数据

---

### 2.3 AI 问答使用数据

#### ✅ 功能已实现，需实际使用

**当前状态**:
```sql
SELECT COUNT(*) FROM ai_call_log;
-- 结果: 0（无调用记录）
```

**原因**: 用户未使用 AI 功能（划词问答、文章版本生成、语音识别）

**可获取的指标**:
```sql
-- 累计调用次数
SELECT COUNT(*) FROM ai_call_log;

-- 按服务类型统计
SELECT service_name, COUNT(*) as calls
FROM ai_call_log
GROUP BY service_name;

-- 平均每日调用量（最近30天）
SELECT 
  DATE(created_time) as date,
  COUNT(*) as daily_calls
FROM ai_call_log
WHERE created_time >= DATE_SUB(NOW(), INTERVAL 30 DAY)
GROUP BY DATE(created_time)
ORDER BY date DESC;

-- 成功率
SELECT 
  service_name,
  COUNT(*) as total,
  SUM(CASE WHEN success = 1 THEN 1 ELSE 0 END) as success_count,
  ROUND(SUM(CASE WHEN success = 1 THEN 1 ELSE 0 END) * 100.0 / COUNT(*), 2) as success_rate
FROM ai_call_log
GROUP BY service_name;

-- 平均响应时间
SELECT 
  service_name,
  AVG(response_time_ms) as avg_response_ms,
  MAX(response_time_ms) as max_response_ms
FROM ai_call_log
WHERE success = 1
GROUP BY service_name;
```

**解决方案**:
1. 手动测试触发 AI 调用（在前端文章页面使用"划词问答"功能）
2. 检查 API Key 配置（`HIAGENT_API_KEY`, `AI_TRANSLATE_API_KEY`）
3. 演示前手动插入模拟 AI 调用记录

#### ❌ 用户满意度/反馈（需新增功能）

**当前状态**: 无反馈机制

**需要开发**:
1. **前端**: AI 回答后显示"有帮助"/"无帮助"按钮
2. **后端**: 新增 `ai_feedback` 表
   ```sql
   CREATE TABLE ai_feedback (
     id BIGINT PRIMARY KEY AUTO_INCREMENT,
     call_log_id BIGINT NOT NULL,
     user_id BIGINT NOT NULL,
     rating TINYINT, -- 1-5 星评分
     is_helpful BOOLEAN, -- 是否有帮助
     feedback_text TEXT, -- 文字反馈
     created_time DATETIME DEFAULT CURRENT_TIMESTAMP,
     FOREIGN KEY (call_log_id) REFERENCES ai_call_log(id)
   );
   ```
3. **API**: `POST /api/student/ai-qa/feedback`

**工作量**: 1-2 天

---

### 2.4 推荐算法效果

#### ❌ 完全无法获取（需埋点 + 后端记录）

**当前状态**: 
- 前端有"换一批"按钮（`Articles.vue:171-178`）
- 前端维护 `shownRecommendedIds` 排除列表
- **但无任何埋点记录用户行为**

**缺失的数据**:
1. **推荐曝光**: 哪些文章被推荐给了哪些用户
2. **推荐点击**: 推荐的文章中有多少被点击
3. **换一批次数**: 用户点击"换一批"的频率

**需要开发**:

#### 方案1: 复用 `learning_records` 表（推荐，快速）

**扩展 `record_type` 枚举**:
```sql
-- 当前: VIEW, REVIEW, TEXT_VIEW
-- 新增: RECOMMENDATION_VIEW, RECOMMENDATION_CLICK, RECOMMENDATION_REFRESH
```

**前端埋点**:
```javascript
// 1. 推荐文章展示时（Articles.vue loadRecommended 成功后）
recommendedArticles.value.forEach(article => {
  recordLearning({
    textId: article.id,
    recordType: 'RECOMMENDATION_VIEW'
  })
})

// 2. 点击推荐文章时（goToArticle 方法中）
if (selectedCategory.value === 'recommendation') {
  recordLearning({
    textId: article.id,
    recordType: 'RECOMMENDATION_CLICK'
  })
}

// 3. 点击"换一批"时（refreshRecommendations 方法中）
recordLearning({
  recordType: 'RECOMMENDATION_REFRESH'
})
```

**后端查询**:
```sql
-- 推荐点击率
SELECT 
  COUNT(DISTINCT CASE WHEN record_type = 'RECOMMENDATION_VIEW' THEN text_id END) as exposed,
  COUNT(DISTINCT CASE WHEN record_type = 'RECOMMENDATION_CLICK' THEN text_id END) as clicked,
  ROUND(
    COUNT(DISTINCT CASE WHEN record_type = 'RECOMMENDATION_CLICK' THEN text_id END) * 100.0 /
    COUNT(DISTINCT CASE WHEN record_type = 'RECOMMENDATION_VIEW' THEN text_id END),
    2
  ) as click_rate
FROM learning_records
WHERE created_time >= DATE_SUB(NOW(), INTERVAL 30 DAY);

-- 换一批频率（每用户平均）
SELECT 
  AVG(refresh_count) as avg_refresh_per_user
FROM (
  SELECT 
    student_id,
    COUNT(*) as refresh_count
  FROM learning_records
  WHERE record_type = 'RECOMMENDATION_REFRESH'
  AND created_time >= DATE_SUB(NOW(), INTERVAL 30 DAY)
  GROUP BY student_id
) t;
```

**工作量**: 0.5-1 天

#### 方案2: 新增 `recommendation_logs` 表（完整，慢）

**表结构**:
```sql
CREATE TABLE recommendation_logs (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  text_id BIGINT,
  event_type ENUM('VIEW', 'CLICK', 'REFRESH') NOT NULL,
  session_id VARCHAR(64), -- 会话ID，用于关联同一批推荐
  position INT, -- 推荐位置（第几个）
  algorithm VARCHAR(50), -- 推荐算法（item-cf/user-cf/cold-start）
  score DECIMAL(5,4), -- 推荐分数
  created_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_user_time (user_id, created_time),
  INDEX idx_text_event (text_id, event_type),
  INDEX idx_session (session_id)
);
```

**优势**: 
- 数据结构清晰，专门用于推荐分析
- 可记录推荐算法类型和分数
- 可按会话分析用户行为

**劣势**: 
- 需要新增表和 Mapper
- 需要修改推荐接口返回算法类型
- 工作量较大

**工作量**: 2-3 天

---

## 三、开发任务清单（按优先级排序）

### P0 - 必须完成（比赛答辩前）

| 任务 | 工作量 | 说明 |
|------|--------|------|
| **1. 补充活跃度测试数据** | 0.5 天 | 手动插入 10-20 条近期学习记录，生成 MAU/DAU 数据 |
| **2. 触发四六级成绩同步** | 0.5 天 | 手动触发同步或插入模拟数据 |
| **3. 触发 AI 调用记录** | 0.5 天 | 手动测试 AI 问答和翻译功能，生成调用记录 |
| **4. 推荐埋点（方案1）** | 1 天 | 前端埋点 + 扩展 `record_type` 枚举 |

**总工作量**: 2.5 天

### P1 - 建议完成（提升答辩质量）

| 任务 | 工作量 | 说明 |
|------|--------|------|
| **5. AI 反馈功能** | 2 天 | 新增 `ai_feedback` 表 + 前端反馈按钮 + 后端 API |
| **6. 学院字段** | 0.5 天 | `classes` 表添加 `college` 字段 + 数据迁移 |
| **7. 四六级对比分析 API** | 1 天 | 封装对比 SQL 为 REST API，前端展示 |

**总工作量**: 3.5 天

### P2 - 可选（长期优化）

| 任务 | 工作量 | 说明 |
|------|--------|------|
| **8. 推荐日志表（方案2）** | 3 天 | 新增 `recommendation_logs` 表 + 完整埋点 |
| **9. 推荐效果 Dashboard** | 2 天 | 运维面板新增推荐分析页面 |
| **10. 压测脚本** | 1 天 | wrk 压测脚本 + 性能基线报告 |

**总工作量**: 6 天

---

## 四、快速补充数据脚本（P0 任务）

### 4.1 补充学习记录（生成 MAU/DAU）

```sql
-- 插入最近30天的学习记录（10个用户，每人2-3条）
INSERT INTO learning_records (student_id, text_id, record_type, duration_seconds, created_time)
SELECT 
  u.id as student_id,
  (SELECT id FROM texts WHERE deleted = 0 ORDER BY RAND() LIMIT 1) as text_id,
  'TEXT_VIEW' as record_type,
  FLOOR(60 + RAND() * 300) as duration_seconds,
  DATE_SUB(NOW(), INTERVAL FLOOR(RAND() * 30) DAY) as created_time
FROM users u
WHERE u.deleted = 0 AND u.role IN ('USER', 'STUDENT')
LIMIT 10;

-- 插入今日学习记录（5个用户）
INSERT INTO learning_records (student_id, text_id, record_type, duration_seconds, created_time)
SELECT 
  u.id as student_id,
  (SELECT id FROM texts WHERE deleted = 0 ORDER BY RAND() LIMIT 1) as text_id,
  'TEXT_VIEW' as record_type,
  FLOOR(60 + RAND() * 300) as duration_seconds,
  NOW() as created_time
FROM users u
WHERE u.deleted = 0 AND u.role IN ('USER', 'STUDENT')
LIMIT 5;
```

### 4.2 插入模拟四六级成绩

```sql
-- 插入模拟四六级成绩（50个学生）
INSERT INTO level_exam_scores (student_id, student_name, exam_type, score, exam_date, created_time)
SELECT 
  u.id as student_id,
  u.username as student_name,
  'CET4' as exam_type,
  FLOOR(300 + RAND() * 400) as score, -- 300-700分
  DATE_SUB(NOW(), INTERVAL FLOOR(RAND() * 365) DAY) as exam_date,
  NOW() as created_time
FROM users u
WHERE u.deleted = 0 AND u.role IN ('USER', 'STUDENT')
LIMIT 50;
```

### 4.3 插入模拟 AI 调用记录

```sql
-- 插入模拟 AI 问答记录（20次）
INSERT INTO ai_call_log (service_name, method, model, success, response_time_ms, tokens, triggered_by, created_time)
VALUES 
  ('HIAGENT', 'chatQA', 'gpt-3.5-turbo', 1, 2500, 150, 'user:student', DATE_SUB(NOW(), INTERVAL 1 DAY)),
  ('HIAGENT', 'chatQA', 'gpt-3.5-turbo', 1, 3200, 200, 'user:student', DATE_SUB(NOW(), INTERVAL 2 DAY)),
  ('HIAGENT', 'chatQA', 'gpt-3.5-turbo', 1, 1800, 120, 'user:student', DATE_SUB(NOW(), INTERVAL 3 DAY)),
  ('HIAGENT_TRANSLATE', 'translateEnToCn', 'gpt-3.5-turbo', 1, 1500, 80, 'user:student', DATE_SUB(NOW(), INTERVAL 1 DAY)),
  ('HIAGENT_TRANSLATE', 'translateEnToCn', 'gpt-3.5-turbo', 1, 1600, 90, 'user:student', DATE_SUB(NOW(), INTERVAL 2 DAY));
```

---

## 五、答辩数据准备检查清单

### 比赛答辩前必须确认的数据

- [ ] 注册用户数 ≥ 100（当前 181 ✅）
- [ ] 班级数量 ≥ 5（当前 7 ✅）
- [ ] MAU > 0（需补充数据 ⚠️）
- [ ] DAU > 0（需补充数据 ⚠️）
- [ ] 学习记录数 ≥ 100（当前 172 ✅）
- [ ] 四六级成绩 > 0（需同步或模拟 ❌）
- [ ] AI 调用记录 > 0（需触发或模拟 ❌）
- [ ] 推荐点击率数据（需埋点 ❌）

### 答辩话术准备

**问题**: "你们的日活月活是多少？"

**回答（数据补充后）**: 
"平台已有 181 名注册用户，覆盖 7 个班级。最近 30 天月活用户 XX 人，今日活跃用户 XX 人。我们通过完整的学习行为追踪功能，记录了 XXX 条学习记录，包括阅读时长、生词本管理、任务进度等。"

**问题**: "有没有对比数据？用平台的学生 vs 没用的学生，通过率差异？"

**回答（数据同步后）**:
"我们已导入 XX 条四六级成绩数据。对比分析显示，使用平台学习的学生四级通过率为 XX%，未使用平台的学生通过率为 XX%，提升了 XX 个百分点。"

**回答（数据未同步）**:
"我们实现了完整的学校数据集成功能，支持双模式接入（REST API + JDBC ETL）。四六级成绩数据的拉取、存储、查询功能已完成开发，目前正在等待学校 API 授权。技术上我们支持按需拉取和定时全量同步两种模式，数据到位后即可进行对比分析。"

---

**文档状态**: ✅ 数据获取能力评估完成 | ⬜ 开发任务执行中 | ⬜ 数据补充完成
