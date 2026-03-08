# 课程进度计算与数据存储文档

## 📋 概述

本文档详细说明课程进度计算的机制、数据来源、以及相关数据在数据库中的存储方式。

---

## 🔍 课程进度是否存储到数据库？

**答案：不存储，课程进度是实时计算的。**

课程进度值不会直接存储到数据库表中，而是在每次请求时根据学生的学习记录实时计算得出。这样做的好处是：
- 保证数据的一致性和实时性
- 避免数据冗余
- 进度自动反映最新的学习情况

---

## 📊 数据来源与存储

课程进度的计算依赖于以下数据库表中的数据：

### 1. **单词学习记录** - `vocabulary_books` 表

**表结构**：
```sql
vocabulary_books
├── id (主键)
├── student_id (学生ID)
├── word_id (单词ID)
├── mastery_level (掌握程度) -- 关键字段
│   ├── NEW (新词，未学习)
│   ├── LEARNING (学习中)
│   └── MASTERED (已掌握)
├── review_count (复习次数)
├── created_time (创建时间)
└── updated_time (更新时间)
```

**数据记录方式**：
- 当学生将单词添加到生词本时，会创建一条记录，初始 `mastery_level = 'NEW'`
- 学生学习和复习单词后，系统会更新 `mastery_level` 字段
- **计算进度时的判断标准**：`mastery_level != 'NEW'` 即视为已学习

**数据更新时机**：
- 学生完成单词学习后
- 学生复习单词后
- 系统自动更新掌握程度时

### 2. **学习记录** - `learning_records` 表

**表结构**：
```sql
learning_records
├── id (主键)
├── student_id (学生ID)
├── word_id (单词ID或占位符，课文学习时使用1作为占位符)
├── record_type (记录类型)
│   ├── WORD_VIEW (单词浏览)
│   ├── WORD_COMPLETE (单词完成)
│   ├── TEXT_VIEW (课文浏览)
│   └── TEXT_COMPLETE (课文完成)
├── duration_seconds (学习时长，单位：秒) -- 关键字段
├── assignment_id (任务ID，可选)
├── created_time (创建时间)
└── updated_time (更新时间)
```

**数据记录方式**：
- 学生每次学习单词或课文时，系统会创建学习记录
- `duration_seconds` 字段记录本次学习时长（秒）
- 对于课文学习，使用 `record_type IN ('TEXT_VIEW', 'TEXT_COMPLETE')` 和 `word_id = 1` 来识别

**课文阅读判断标准**：
- 同一课文的所有学习记录时长累计 ≥ 600秒（10分钟）时，视为已阅读

**数据更新时机**：
- 学生开始学习单词/课文时
- 学生完成学习时
- 系统自动记录学习时长时

### 3. **基础数据表**

以下表存储课程的静态结构数据，不包含学生个人进度：

#### `courses` 表
```sql
courses
├── id (主键)
├── name (课程名称)
└── description (课程描述)
```

#### `units` 表
```sql
units
├── id (主键)
├── course_id (课程ID)
├── name (单元名称)
└── description (单元描述)
```

#### `unit_words` 表
```sql
unit_words
├── id (主键)
├── unit_id (单元ID)
└── word_id (单词ID)
```

#### `texts` 表
```sql
texts
├── id (主键)
├── unit_id (单元ID)
├── title (课文标题)
└── content (课文内容)
```

#### `student_classes` 表
```sql
student_classes
├── id (主键)
├── student_id (学生ID)
├── class_id (班级ID)
└── join_time (加入时间)
```

**注意**：`student_classes` 表中**没有** `progress` 字段，进度是实时计算的。

---

## 🧮 课程进度计算逻辑

### 计算公式

```
课程进度 = 单元进度 × 60% + 单词进度 × 40%
```

### 详细计算步骤

#### 第一步：计算单元进度（权重60%）

**1.1 单元进度百分比**
```
单元进度 = (已学习单元数 / 课程总单元数) × 100%
```

**1.2 判断单元是否已学习**

对于每个单元，计算其学习进度：

- **如果单元有单词和课文**：
  ```
  单元进度 = 单词进度 × 50% + 课文进度 × 50%
  ```
  
- **如果只有单词**：
  ```
  单元进度 = 单词进度
  ```
  
- **如果只有课文**：
  ```
  单元进度 = 课文进度
  ```

**1.3 单词进度计算（单元级别）**
```sql
-- 查询逻辑
SELECT COUNT(*) 
FROM vocabulary_books 
WHERE student_id = ? 
  AND word_id IN (单元的所有单词ID)
  AND mastery_level != 'NEW'
```
```
单词进度 = (已学习单词数 / 单元总单词数) × 100%
```

**1.4 课文进度计算（单元级别）**
```sql
-- 查询逻辑
SELECT SUM(duration_seconds)
FROM learning_records
WHERE student_id = ?
  AND record_type IN ('TEXT_VIEW', 'TEXT_COMPLETE')
  AND word_id = 1  -- 课文学习记录的占位符
```
```
课文进度 = (已阅读课文数 / 单元总课文数) × 100%
已阅读课文：累计学习时长 ≥ 600秒（10分钟）
```

**1.5 单元完成判断**
- 如果 `单元进度 ≥ 50%`，则认为该单元已学习

#### 第二步：计算单词进度（权重40%）

**2.1 课程总单词数**
- 查询课程下所有单元的单词，去重后的总数

**2.2 已学习单词数**
```sql
-- 查询逻辑
SELECT COUNT(DISTINCT word_id)
FROM vocabulary_books
WHERE student_id = ?
  AND word_id IN (课程的所有单词ID)
  AND mastery_level != 'NEW'
```

**2.3 单词进度百分比**
```
单词进度 = (已学习单词数 / 课程总单词数) × 100%
```

#### 第三步：综合计算

```
最终课程进度 = 单元进度 × 0.6 + 单词进度 × 0.4
```

---

## 📝 数据更新流程

### 场景1：学生学习单词

1. **学习记录创建**
   - 在 `learning_records` 表中创建记录
   - `record_type = 'WORD_VIEW'` 或 `'WORD_COMPLETE'`
   - 记录 `duration_seconds`

2. **生词本更新**
   - 如果单词不在生词本中，创建 `vocabulary_books` 记录，`mastery_level = 'NEW'`
   - 学习后更新 `mastery_level = 'LEARNING'` 或 `'MASTERED'`

3. **进度计算触发**
   - 下次请求课程列表或仪表板时，系统重新计算进度
   - 进度值通过 API 返回给前端

### 场景2：学生阅读课文

1. **学习记录创建**
   - 在 `learning_records` 表中创建记录
   - `record_type = 'TEXT_VIEW'` 或 `'TEXT_COMPLETE'`
   - `word_id = 1`（占位符）
   - 记录 `duration_seconds`

2. **进度计算**
   - 系统累计同一课文的所有学习记录时长
   - 如果总时长 ≥ 600秒，视为已阅读

3. **进度更新**
   - 下次请求时重新计算单元进度和课程进度

---

## 🔄 实时计算 vs 存储对比

| 特性 | 实时计算（当前方案） | 存储到数据库 |
|------|---------------------|-------------|
| 数据一致性 | ✅ 总是最新 | ⚠️ 需要同步更新 |
| 存储空间 | ✅ 节省空间 | ❌ 占用额外空间 |
| 查询性能 | ⚠️ 需要复杂计算 | ✅ 直接查询 |
| 维护成本 | ✅ 无需维护 | ❌ 需要更新逻辑 |
| 数据准确性 | ✅ 准确 | ⚠️ 可能不同步 |

**当前选择实时计算的原因**：
- 学习记录频繁更新，存储进度需要额外的同步逻辑
- 进度计算逻辑可能调整，实时计算更灵活
- 避免了数据不一致的问题

---

## 🛠️ 代码位置

### 后端计算逻辑

**文件**：`backend/src/main/java/com/english/service/impl/StudentClassesServiceImpl.java`

**关键方法**：
- `getStudentClassesWithDetails()` - 获取学生班级详情（包含进度计算）
- `calculateLearnedUnits()` - 计算已学习单元数（第318-396行）
- `calculateTotalWords()` - 计算课程总单词数（第401-423行）
- `calculateLearnedWordsInCourse()` - 计算课程中已学习单词数（第428-460行）

**API接口**：
- `GET /api/student/dashboard` - 获取仪表板数据（包含课程进度）
- `GET /api/student/courses` - 获取课程列表（包含课程进度）

### 前端显示逻辑

**文件**：`frontend/src/views/student/Dashboard.vue`

**关键方法**：
- `getCourseProgress(course)` - 获取课程进度（第390-402行）
- `loadDashboardData()` - 加载仪表板数据（第287-310行）

---

## 📌 关键字段说明

### mastery_level 字段值

- **NEW**: 新词，刚添加到生词本，还未学习
- **LEARNING**: 学习中，已经开始学习但未完全掌握
- **MASTERED**: 已掌握，达到掌握标准

**进度计算标准**：`mastery_level != 'NEW'` 的单词视为已学习

### record_type 字段值

- **WORD_VIEW**: 单词浏览记录
- **WORD_COMPLETE**: 单词学习完成记录
- **TEXT_VIEW**: 课文浏览记录
- **TEXT_COMPLETE**: 课文学习完成记录

### 课文学习记录的特殊处理

- 使用 `word_id = 1` 作为占位符（因为 `learning_records` 表没有 `text_id` 字段）
- 通过 `record_type IN ('TEXT_VIEW', 'TEXT_COMPLETE')` 识别课文学习记录

---

## ⚠️ 注意事项

1. **课文学习记录的局限性**
   - 当前使用 `word_id = 1` 作为占位符，无法区分不同课文
   - 所有课文的学习时长会累计计算，可能影响进度准确性

2. **单元完成阈值**
   - 单元进度 ≥ 50% 才视为已学习，阈值固定，无法配置

3. **进度计算性能**
   - 对于大量单元和单词的课程，实时计算可能较慢
   - 如果性能成为问题，可以考虑缓存或异步计算

4. **数据一致性**
   - 依赖于 `vocabulary_books` 和 `learning_records` 表的准确性
   - 如果这些表的数据不完整，进度计算可能不准确

---

## 🔮 未来优化建议

1. **添加进度缓存**
   - 在 `student_classes` 表中添加 `progress` 字段
   - 使用定时任务或触发器更新进度
   - 在数据变更时触发进度更新

2. **优化课文学习记录**
   - 在 `learning_records` 表中添加 `text_id` 字段
   - 或创建独立的 `text_learning_records` 表

3. **可配置的阈值**
   - 将单元完成阈值（50%）改为可配置参数
   - 允许不同课程使用不同的阈值

4. **进度历史记录**
   - 创建 `course_progress_history` 表
   - 定期记录进度快照，用于分析和趋势展示

---

## 📅 文档版本

- **版本**: 1.0
- **创建日期**: 2025-01-XX
- **最后更新**: 2025-01-XX
- **作者**: AI Assistant

---

## 📚 相关文档

- [API_DOCUMENTATION.md](./API_DOCUMENTATION.md) - API接口文档
- [OPTIMIZATION_DOCUMENTATION.md](./OPTIMIZATION_DOCUMENTATION.md) - 系统优化文档
- [ASSIGNMENT_FEATURES.md](./ASSIGNMENT_FEATURES.md) - 任务功能文档



-- 1. 查询courses表中的数据（检查课程是否存在）
SELECT * FROM courses WHERE id = 1;

-- 2. 查询users表中的教师数据（检查教师是否存在）
SELECT * FROM users WHERE id = 2 AND role = 'TEACHER';

-- 3. 查询所有assignments（查看deadline字段）
SELECT id, title, deadline, class_id, unit_id, created_by FROM assignments LIMIT 5;