# 系统优化文档

## 更新日期
2025-12-26

## 概述
本文档记录了系统的最新优化内容，包括课程进度计算优化、分数计算逻辑改进、任务权重功能、成绩导出功能等。

---

## 1. 课程进度条优化

### 问题描述
- 学生界面课程进度条一直不变，无法反映实际学习进度
- 进度计算不准确，只考虑单词学习，未考虑课文学习

### 解决方案

#### 1.1 后端优化 (`StudentClassesServiceImpl.java`)

**改进的进度计算逻辑**：
- **单元进度计算**：综合考虑单词和课文学习情况
  - 单词进度：已学习单词数 / 总单词数
  - 课文进度：已阅读课文数 / 总课文数（阅读时长≥10分钟）
  - 综合进度：单词进度 × 50% + 课文进度 × 50%

- **课程进度计算**：
  - 单元进度权重：60%
  - 单词进度权重：40%
  - 综合进度 = 单元进度 × 0.6 + 单词进度 × 0.4

**关键代码位置**：
```java
// 计算学生已学习的单元数量（基于综合进度）
private int calculateLearnedUnits(Long studentId, Long courseId) {
    // 综合考虑单词和课文的学习情况
    // 如果单元进度超过50%，认为已学习该单元
}
```

#### 1.2 前端优化 (`Courses.vue`)

**改进的进度显示**：
- 优先使用后端返回的 `progress` 字段
- 如果没有 `progress` 字段，使用 `learnedUnits / totalUnits` 计算

**关键代码**：
```javascript
const getCourseProgress = (course) => {
  // 优先使用后端返回的progress字段
  if (course.progress !== undefined && course.progress !== null) {
    return Math.round(course.progress)
  }
  // 如果没有progress字段，使用learnedUnits计算
  if (!course.totalUnits) return 0
  return Math.round((course.learnedUnits / course.totalUnits) * 100)
}
```

#### 1.3 课程进度条更新规则说明

**更新触发条件**：
1. **学生完成单词学习**：当学生在单元中学习单词并达到掌握状态（mastery_level != 'NEW'）时，会触发进度更新
2. **学生完成课文学习**：当学生阅读课文时长达到10分钟（600秒）时，会触发进度更新
3. **手动刷新**：学生进入课程页面或点击刷新按钮时，会重新计算并更新进度

**进度计算规则**：
- **单元进度判断标准**：单元综合进度 ≥ 50% 时，该单元被视为"已学习"
- **单元综合进度计算**：
  - 如果单元同时包含单词和课文：`(单词进度 × 50%) + (课文进度 × 50%)`
  - 如果单元只有单词：直接使用单词进度
  - 如果单元只有课文：直接使用课文进度
- **课程进度计算**：
  - `(已学习单元数 / 总单元数 × 60%) + (已学习单词数 / 总单词数 × 40%)`

**进度更新时机**：
- 实时更新：学生完成学习活动后，前端会触发 `refreshCourseProgress` 方法
- 页面加载：进入课程列表页面时，自动加载最新进度
- 单元学习后：完成单元学习后，自动刷新课程进度

**注意事项**：
- 进度计算基于 `vocabulary_books` 表和 `learning_records` 表的数据
- 课文学习记录通过 `record_type` 为 `TEXT_VIEW` 或 `TEXT_COMPLETE` 且 `word_id=1` 来识别
- 进度更新可能存在延迟，建议在关键操作后手动刷新

---

## 2. 分数计算逻辑优化

### 问题描述
- 学生只有全部完成学习才有100分，不够灵活
- 没有考虑学习时长因素
- **学生端主页显示累加分数（如161分）不合理**，应该显示加权平均分

### 解决方案

#### 2.1 新的分数计算算法 (`ScoreRecordServiceImpl.java`)

**任务分数计算公式**：
```
基础分数 = 完成率 × 60（最高60分）
学习时长加分 = (学习时长 / 理想学习时长) × 40（最高40分）
总分数 = 基础分数 + 时长分数（最高100分）
```

**特点**：
- 完成率占60%权重
- 学习时长占40%权重
- 理想学习时长由教师设置（默认60分钟）
- 超过理想学习时长按理想时长计算

**关键代码**：
```java
// 计算分数：综合考虑完成率和学习时长
double baseScore = completionRate * 0.6;  // 基础分数（最高60分）
int normalizedStudyTime = Math.min(studyTime, idealStudyTime);
double timeScore = (normalizedStudyTime / (double) idealStudyTime) * 40.0;  // 时长分数（最高40分）
int score = (int) Math.round(baseScore + timeScore);
```

#### 2.2 学生平均分数计算优化

**问题**：之前返回的 `totalScore` 是累加分数（如完成2个任务，每个80分，显示160分），这不合理。

**解决方案**：
- **简单平均分**：所有任务分数的算术平均值
- **加权平均分**：考虑任务权重的加权平均值（推荐使用）

**加权平均分计算公式**：
```
加权平均分 = Σ(任务分数 × 任务权重) / Σ(任务权重)
```

**API返回字段**：
- `averageScore`：简单平均分（所有任务分数的算术平均值）
- `weightedAverageScore`：加权平均分（考虑任务权重，推荐使用）
- `totalScore`：累加分数（仅用于统计，不应作为主要显示指标）
- `totalCount`：任务总数

**使用建议**：
- **学生端显示**：应使用 `weightedAverageScore`（加权平均分）
- **教师端统计**：可以使用 `averageScore` 或 `weightedAverageScore`
- **不应使用**：`totalScore`（累加分数）仅用于统计，不应作为主要显示指标

**关键代码**：
```java
// 计算加权平均分（考虑任务权重）
double weightedTotalScore = 0.0;
double totalWeight = 0.0;
for (ScoreRecord record : records) {
    if (record.getAssignmentId() != null) {
        Assignment assignment = assignmentService.getById(record.getAssignmentId());
        double weight = (assignment != null && assignment.getWeight() != null) 
            ? assignment.getWeight() : 1.0;
        weightedTotalScore += record.getScore() * weight;
        totalWeight += weight;
    }
}
double weightedAverageScore = totalWeight > 0 ? weightedTotalScore / totalWeight : 0.0;
```

---

## 3. 任务权重功能

### 功能描述
允许教师为每个任务设置权重，用于计算加权平均分。

### 实现细节

#### 3.1 数据库变更

**新增字段**：
```sql
ALTER TABLE assignments 
ADD COLUMN weight DECIMAL(5,2) DEFAULT 1.00 COMMENT '任务权重（用于计算加权平均分，默认1.00）' 
AFTER deadline;

CREATE INDEX idx_weight ON assignments(weight);
```

**迁移脚本位置**：
- `backend/src/main/resources/db/migration/add_assignment_weight.sql`
- `backend/execute_migration.sh`

#### 3.2 后端实现

**实体类更新** (`Assignment.java`)：
```java
@TableField("weight")
private Double weight; // 任务权重，默认1.00

public Double getWeight() {
    return weight != null ? weight : 1.0; // 默认权重为1.0
}
```

**前端表单** (`Assignments.vue`)：
- 添加了权重输入框（0.1 - 10，步长0.1，精度2位小数）
- 提供权重说明文字

#### 3.3 加权平均分计算

**计算公式**：
```
加权平均分 = Σ(任务分数 × 任务权重) / Σ(任务权重)
```

**实现位置** (`ScoreRecordServiceImpl.java`)：
```java
double totalWeightedScore = 0.0;
double totalWeight = 0.0;

for (Assignment assignment : assignments) {
    ScoreRecord scoreRecord = getAssignmentScore(assignment.getId(), studentId);
    double weight = assignment.getWeight() != null ? assignment.getWeight() : 1.0;
    
    if (scoreRecord != null) {
        totalWeightedScore += scoreRecord.getScore() * weight;
        totalWeight += weight;
    }
}

double weightedAverage = totalWeight > 0 ? totalWeightedScore / totalWeight : 0.0;
```

---

## 4. 班级成绩导出功能

### 功能描述
教师可以导出班级所有学生的任务成绩，生成Excel文件。

### 实现细节

#### 4.1 后端API (`ScoreRecordController.java`)

**接口**：
```
GET /api/scores/class/{classId}/export
```

**权限**：
- ADMIN、TEACHER
- 教师只能导出自己管理的班级

**返回**：
- Excel文件（.xlsx格式）
- 文件名格式：`{班级名称}_成绩_{日期}.xlsx`

#### 4.2 Excel内容

**列结构**：
1. 学号
2. 姓名
3. 任务数
4. 已完成
5. 加权平均分
6. 各任务成绩（任务标题 + 权重）

**数据格式**：
- 任务成绩显示为：`分数/满分`（如：85/100）
- 加权平均分保留2位小数

#### 4.3 前端实现 (`Classes.vue`)

**导出按钮**：
- 位置：班级管理页面的操作列
- 功能：点击后下载Excel文件

**关键代码**：
```javascript
const exportClassScores = async (classItem) => {
  const response = await fetch(`/api/scores/class/${classItem.id}/export`, {
    headers: {
      'Authorization': `Bearer ${localStorage.getItem('token')}`
    }
  })
  
  const blob = await response.blob()
  const url = window.URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `班级成绩_${classItem.name}_${new Date().toISOString().split('T')[0]}.xlsx`
  a.click()
}
```

#### 4.4 依赖

**新增Maven依赖** (`pom.xml`)：
```xml
<dependency>
    <groupId>org.apache.poi</groupId>
    <artifactId>poi-ooxml</artifactId>
    <version>5.2.3</version>
</dependency>
```

---

## 5. 教师界面优化

### 5.1 班级卡片大小调整 (`Dashboard.vue`)

**改进内容**：
- 卡片内边距：15px → 20px
- 最小高度：120px
- 字体大小：16px → 18px（标题）
- 行高：1.6
- 列表最大高度：300px → 500px

---

## 6. Bug修复

### 6.1 修复 `text_id` 字段错误

**问题**：
- `learning_records` 表中没有 `text_id` 字段
- 查询时使用了不存在的字段导致SQL错误

**修复** (`StudentClassesServiceImpl.java`)：
```java
// 修复前：使用不存在的text_id字段
.eq("text_id", text.getId())

// 修复后：使用record_type和word_id查询
.in("record_type", Arrays.asList("TEXT_VIEW", "TEXT_COMPLETE"))
.eq("word_id", 1L) // 课文学习记录使用word_id=1作为占位符
```

### 6.2 修复删除任务后学生端仍显示

**问题**：
- 教师删除任务后，学生端仍能看到该任务

**修复**：
- 在查询任务列表时过滤已删除的任务
- 使用 `deleted = 0` 条件过滤

---

## 7. 数据库迁移

### 7.1 执行迁移脚本

```
Flyway 已成功集成到项目中！

变更内容：

依赖添加：在 pom.xml 中添加了 flyway-core 和 flyway-mysql。
配置更新：在 application.yml 中开启了 Flyway，并配置了 baseline-on-migrate: true 以兼容现有数据库。
迁移文件：已重新命名并确认顺序。
如何运行： 你只需像往常一样启动 Spring Boot 应用，Flyway 就会自动检测并按顺序（V5 -> V6 -> V7）执行迁移操作。你可以在控制台日志中看到迁移进度。
```

**方法1：使用脚本**：
```bash
cd /home/luosc/LangHack/backend
./execute_migration.sh
```

**方法2：手动执行**：
```bash
mysql -u root -p english_learning < backend/src/main/resources/db/migration/add_assignment_weight.sql
```

**方法3：MySQL客户端**：
```sql
ALTER TABLE assignments 
ADD COLUMN weight DECIMAL(5,2) DEFAULT 1.00 COMMENT '任务权重（用于计算加权平均分，默认1.00）' 
AFTER deadline;

CREATE INDEX idx_weight ON assignments(weight);
```

---

## 8. API变更

### 8.1 新增接口

#### 导出班级成绩
- **URL**: `GET /api/scores/class/{classId}/export`
- **权限**: ADMIN, TEACHER
- **返回**: Excel文件

### 8.2 接口变更

#### 创建/更新任务
- **新增字段**: `weight` (Double, 可选，默认1.0)
- **位置**: `POST /api/assignments`, `POST /api/assignments/create-with-content`, `PUT /api/assignments/{id}`

#### 获取课程列表（学生端）
- **新增字段**: `progress` (Double) - 综合进度百分比
- **位置**: `GET /api/student/classes/courses`

---

## 9. 前端变更

### 9.1 新增组件/功能

1. **任务权重输入** (`Assignments.vue`)
   - 位置：任务创建/编辑表单
   - 类型：数字输入框（0.1-10，步长0.1）

2. **导出成绩按钮** (`Classes.vue`)
   - 位置：班级管理页面操作列
   - 功能：导出Excel成绩单

### 9.2 样式调整

1. **教师仪表板** (`Dashboard.vue`)
   - 班级卡片尺寸优化
   - 字体和间距调整

---

## 10. 测试建议

### 10.1 功能测试

1. **课程进度测试**
   - 学习单词后检查进度是否更新
   - 学习课文后检查进度是否更新
   - 验证综合进度计算是否正确

2. **分数计算测试**
   - 测试不同完成率下的分数
   - 测试不同学习时长下的分数
   - 验证总分不超过100分

3. **任务权重测试**
   - 创建不同权重的任务
   - 验证加权平均分计算
   - 导出Excel验证权重显示

4. **成绩导出测试**
   - 导出不同班级的成绩
   - 验证Excel格式和内容
   - 验证加权平均分计算

### 10.2 兼容性测试

1. **数据库兼容性**
   - 确认迁移脚本执行成功
   - 验证旧数据兼容性（weight字段默认值）

2. **前端兼容性**
   - 验证旧版本浏览器支持
   - 验证移动端显示

---

## 11. 教师自定义学习时长功能

### 11.1 功能描述
允许教师为每个任务设置理想学习时长，用于更准确地计算学生分数。

### 11.2 实现细节

#### 数据库变更
```sql
ALTER TABLE assignments 
ADD COLUMN ideal_study_time_minutes INT DEFAULT 60 COMMENT '理想学习时长（分钟），用于计算分数，默认60分钟' 
AFTER weight;

CREATE INDEX idx_ideal_study_time ON assignments(ideal_study_time_minutes);
```

**迁移脚本位置**：
- `backend/src/main/resources/db/migration/add_assignment_ideal_study_time.sql`

#### 后端实现

**实体类更新** (`Assignment.java`)：
```java
@TableField("ideal_study_time_minutes")
private Integer idealStudyTimeMinutes; // 理想学习时长（分钟），默认60分钟

public Integer getIdealStudyTimeMinutes() {
    return idealStudyTimeMinutes != null ? idealStudyTimeMinutes : 60;
}
```

**分数计算更新** (`ScoreRecordServiceImpl.java`)：
```java
// 获取任务的理想学习时长（如果未设置，默认60分钟）
var assignment = assignmentService.getById(assignmentId);
int idealStudyTime = assignment != null && assignment.getIdealStudyTimeMinutes() != null 
    ? assignment.getIdealStudyTimeMinutes() : 60;

// 使用教师设置的理想学习时长计算分数
int normalizedStudyTime = Math.min(studyTime, idealStudyTime);
double timeScore = idealStudyTime > 0 ? (normalizedStudyTime / (double) idealStudyTime) * 40.0 : 0.0;
```

#### 前端实现 (`Assignments.vue`)

**表单字段**：
- 位置：任务创建/编辑表单
- 类型：数字输入框（5-300分钟，步长5分钟）
- 默认值：60分钟
- 说明：设置学生完成此任务的理想学习时长

**关键代码**：
```vue
<el-form-item label="理想学习时长" prop="idealStudyTimeMinutes">
  <el-input-number
    v-model="assignmentForm.idealStudyTimeMinutes"
    :min="5"
    :max="300"
    :step="5"
    placeholder="理想学习时长（分钟），用于计算分数，默认60分钟"
    style="width: 100%"
  />
  <div style="font-size: 12px; color: #999; margin-top: 4px;">
    设置学生完成此任务的理想学习时长。分数计算中，学习时长占40%权重，达到理想时长可获得满分。
  </div>
</el-form-item>
```

### 11.3 分数计算逻辑

**更新后的计算公式**：
```
基础分数 = 完成率 × 60（最高60分）
学习时长加分 = (实际学习时长 / 理想学习时长) × 40（最高40分）
总分数 = 基础分数 + 时长分数（最高100分）
```

**特点**：
- 教师可以为不同任务设置不同的理想学习时长
- 例如：简单任务30分钟，复杂任务90分钟
- 学生达到理想时长即可获得时长部分的满分（40分）

---

## 12. 已知问题

2. **课文学习记录**
   - 使用 `word_id=1L` 作为占位符
   - 无法直接通过课文ID查询学习记录

---

## 12. 后续优化建议

1. **允许教师自定义学习时长**
   - 在任务创建时设置理想学习时长
   - 根据任务类型设置不同的时长要求

2. **学习记录优化**
   - 考虑在 `learning_records` 表中添加 `text_id` 字段
   - 或创建专门的课文学习记录表

3. **进度计算优化**
   - 考虑添加更多权重配置选项
   - 支持按课程自定义权重比例

---

## 13. 导出成绩功能优化

### 13.1 问题描述
导出班级成绩时，所有学生的成绩都显示为0。

### 13.2 问题原因
- 分数记录只有在调用 `updateAssignmentScore` 时才会被创建
- 如果学生完成学习后没有触发分数更新，分数记录就不存在
- 导出时查询不到分数记录，就显示为0

### 13.3 解决方案

**自动计算分数机制**：
- 在导出成绩时，如果发现某个任务的分数记录不存在，自动调用 `updateAssignmentScore` 计算并创建分数记录
- 添加了详细的日志输出，便于调试
- 增强了类型转换的健壮性，避免数据类型不匹配导致的错误

**关键代码** (`ScoreRecordServiceImpl.java`)：
```java
// 如果分数记录不存在，尝试自动计算并创建
if (scoreRecord == null) {
    try {
        System.out.println("导出成绩：为任务 " + assignment.getId() + " 的学生 " + studentId + " 自动计算分数");
        updateAssignmentScore(assignment.getId(), studentId);
        scoreRecord = getAssignmentScore(assignment.getId(), studentId);
    } catch (Exception e) {
        System.err.println("自动计算任务分数失败: " + e.getMessage());
    }
}
```

**类型安全处理**：
- 改进了 `updateAssignmentScore` 方法中的类型转换
- 使用 `instanceof` 检查类型，避免类型转换异常
- 添加了空值检查和异常处理

### 13.4 使用说明

1. **导出成绩时**：
   - 系统会自动为所有学生计算所有任务的分数
   - 如果学生有学习记录，会根据完成率和学习时长计算分数
   - 如果学生没有学习记录，分数为0（这是正常的）

2. **查看日志**：
   - 导出时会输出详细的日志信息
   - 可以通过日志查看分数计算过程
   - 如果出现错误，日志会显示具体的错误信息

3. **注意事项**：
   - 导出大量数据时可能需要一些时间（因为需要计算分数）
   - 建议在导出前确保学生已经完成学习，这样分数会更准确

---

## 14. 更新日志

### 2025-12-26
- ✅ 修复课程进度条不更新问题
- ✅ 优化分数计算逻辑（考虑学习时长）
- ✅ 添加任务权重功能
- ✅ 实现班级成绩导出功能
- ✅ 优化教师界面班级卡片大小
- ✅ 修复 `text_id` 字段错误
- ✅ 修复删除任务后学生端仍显示问题
- ✅ 添加教师自定义学习时长功能
- ✅ 修复导出成绩全部为0的问题（添加自动计算分数功能）

---

## 14. 联系方式

如有问题或建议，请联系开发团队。

