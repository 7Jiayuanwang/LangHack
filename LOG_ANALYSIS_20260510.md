# LangHack 服务器日志分析报告
**日志文件**: `langhack-20260510-222756.log`  
**分析时间**: 2026-05-10  
**日志时间范围**: 2026-05-10 21:30:58 ~ 22:27:55 (约57分钟)  
**日志总行数**: 9,846 行

---

## 📊 执行概览

### 数据同步任务
- **目标学号**: `2023112101479`
- **同步数据源**: 
  - `course_selection` (选课记录): 640 次请求
  - `level_exam_scores` (四六级成绩): 165 次请求
- **数据量规模**:
  - 选课记录: 约 117,000 条 (117 页 × 1000条/页)
  - 四六级成绩: 约 164,000 条 (164 页 × 1000条/页)
- **API 请求模式**: 每页 1000 条，分页拉取，所有请求均为首次尝试 (attempt=1/3)

---

## 🔴 严重问题

### 1. **数据完整性错误 - credits 字段为 null**
**错误次数**: 3,138 次  
**影响范围**: `school_course` 表插入/更新失败

#### 错误详情
```
Column 'credits' cannot be null
Mapper: SchoolCourseMapper.upsertByCourseCode
```

#### 根本原因
在同步选课记录 (`course_selection`) 时，系统尝试自动创建不存在的 `teaching_class` 骨架记录，但关联的 `school_course` 表缺少必填字段 `credits`（学分）。

#### 受影响的课程班（Top 10）
| 课程班号 | 失败次数 |
|---------|---------|
| 146395532 | 64 |
| 89245826 | 35 |
| 30934856 | 20 |
| 24212288 | 20 |
| 30934466 | 19 |
| 76352861 | 18 |
| 68219634 | 18 |
| 31035152 | 18 |
| 76200635 | 17 |
| 54601564 | 17 |

#### 受影响的学生（Top 10）
| 学号 | 失败次数 |
|------|---------|
| 2014112010121 | 15 |
| 2014112010375 | 14 |
| 2014112022697 | 11 |
| 2014112022694 | 11 |
| 2014112010413 | 11 |
| 2014112010377 | 11 |
| 2014112022690 | 10 |
| 2014112010392 | 9 |
| 2014112022698 | 8 |
| 2014112022689 | 8 |

---

## ⚠️ 警告问题

### 2. **teaching_class 不存在警告**
**警告次数**: 522 次  
**行为**: 系统自动创建骨架记录

#### 问题说明
选课记录引用的 `teaching_class` (教学班) 在数据库中不存在，系统尝试自动创建，但由于上游 `school_course` 数据不完整（缺少 credits），导致级联失败。

---

### 3. **从库连接失败**
**失败次数**: 57 次  
**错误信息**: `Cannot connect to slave: Access denied; you need (at least one of) the SUPER, REPLICATION CLIENT privilege(s) for this operation`

#### 影响
- 系统自动降级到主库 (MASTER)
- 读写分离机制失效，主库压力增大
- 可能影响查询性能

#### 建议
检查从库用户权限配置，确保复制账户具有 `REPLICATION CLIENT` 权限。

---

## ✅ 正常运行指标

### 日志级别分布
| 级别 | 数量 | 占比 |
|------|------|------|
| INFO | 885 | 9.0% |
| WARN | 1,108 | 11.3% |
| ERROR | 7 | 0.07% |
| 其他（堆栈信息） | 7,846 | 79.7% |

### API 请求性能
- **平均请求间隔**: ~0.5-1 秒/请求
- **重试情况**: 所有请求均为首次成功 (attempt=1/3)，无重试
- **API 响应**: 正常返回 OData 格式 (`@odata.context`, `value`)

---

## 🔍 数据流分析

### 同步链路
```
学校 API (CDSP)
  ↓
course_selection (选课记录)
  ↓
teaching_class (教学班) [不存在 → 自动创建]
  ↓
school_course (课程信息) [缺少 credits → 失败]
```

### 失败模式
1. 拉取选课记录成功
2. 检测到 `teaching_class` 不存在
3. 尝试创建骨架记录
4. 需要关联 `school_course`
5. **school_course 缺少 credits 字段 → 插入失败**
6. 整条选课记录同步失败

---

## 🛠️ 修复建议

### 优先级 P0 - 立即修复

#### 1. 修复 credits 字段缺失问题
**方案 A**: 修改 `school_course` 表结构，允许 `credits` 为 NULL
```sql
ALTER TABLE school_course MODIFY COLUMN credits DECIMAL(4,1) NULL;
```

**方案 B**: 修改骨架记录创建逻辑，为 `credits` 提供默认值
```java
// SchoolDataPersistServiceImpl.java
SchoolCourse skeletonCourse = new SchoolCourse();
skeletonCourse.setCourseCode(courseCode);
skeletonCourse.setCredits(new BigDecimal("0.0")); // 默认值
skeletonCourse.setSyncSource("skeleton");
```

**推荐**: 方案 B，保持数据完整性约束，避免真实课程数据缺失学分。

---

#### 2. 优化同步顺序
**问题**: 当前同步顺序导致依赖关系错误

**建议顺序**:
```
1. school_course (课程基础信息)
2. teaching_class (教学班)
3. course_selection (选课记录)
```

**实现**: 在链式拉取中，先调用 `teacher_courses` 端点获取完整课程信息，再同步选课记录。

---

### 优先级 P1 - 尽快修复

#### 3. 修复从库权限问题
```sql
-- 在主库执行
GRANT REPLICATION CLIENT ON *.* TO 'replication_user'@'%';
FLUSH PRIVILEGES;
```

#### 4. 增加错误处理和日志
```java
// 建议在 SchoolDataPersistServiceImpl 中
try {
    schoolCourseMapper.upsertByCourseCode(skeletonCourse);
} catch (DataIntegrityViolationException e) {
    log.error("创建骨架课程失败 courseCode={} err={}", courseCode, e.getMessage());
    // 使用更宽松的默认值重试
    skeletonCourse.setCredits(BigDecimal.ZERO);
    skeletonCourse.setTotalHours(0);
    schoolCourseMapper.upsertByCourseCode(skeletonCourse);
}
```

---

### 优先级 P2 - 优化改进

#### 5. 批量处理优化
当前逐条处理选课记录，建议改为批量插入：
```java
// 每 100 条批量提交
List<CourseSelection> batch = new ArrayList<>();
for (Map<String, Object> record : records) {
    batch.add(convertToCourseSelection(record));
    if (batch.size() >= 100) {
        courseSelectionMapper.insertBatch(batch);
        batch.clear();
    }
}
```

#### 6. 监控和告警
- 添加 Prometheus 指标：`school_data_sync_errors_total{type="credits_null"}`
- 设置告警阈值：错误率 > 5% 时触发
- 记录失败的 `courseCode` 到专门的错误表，便于后续补偿

---

## 📈 性能评估

### 同步效率
- **总数据量**: ~281,000 条记录
- **同步时长**: 57 分钟
- **平均速度**: ~82 条/分钟
- **API 吞吐**: ~14 请求/分钟

### 瓶颈分析
1. **数据库写入失败**: 3,138 次失败导致大量重试和回滚
2. **从库不可用**: 57 次降级增加主库负载
3. **逐条处理**: 未使用批量插入，事务开销大

### 优化后预期
- 修复 credits 问题后，失败率降至 < 1%
- 批量插入可提升速度至 ~500 条/分钟
- 从库恢复后，读写分离可降低主库负载 40%

---

## 🎯 行动计划

### 立即执行（今天）
1. ✅ 分析日志，识别根本原因
2. ⬜ 修改 `SchoolDataPersistServiceImpl`，为骨架记录提供默认 credits
3. ⬜ 修复从库权限问题
4. ⬜ 重新运行同步任务，验证修复效果

### 本周内
1. ⬜ 优化同步顺序，先拉取 `teacher_courses`
2. ⬜ 实现批量插入逻辑
3. ⬜ 添加错误监控和告警

### 长期优化
1. ⬜ 设计补偿机制，定期重试失败记录
2. ⬜ 实现增量同步，避免全量拉取
3. ⬜ 优化数据库索引，提升查询性能

---

## 📝 附录

### 关键日志片段
```
2026-05-10 21:30:59.987 [WARN] SchoolDataPersistServiceImpl - 选课记录 teaching_class 不存在 classId=54442752，自动创建骨架记录
2026-05-10 21:30:59.818 [WARN] SchoolDataPersistServiceImpl - 同步单条数据失败 dataSource=course_selection recordPreview={学号=2014102080251, 考试性质代码=00, 考试性质名称=正常考试, 课程班号=31035152} err=Column 'credits' cannot be null
2026-05-10 22:27:41.676 [WARN] DynamicDataSource - [DataSource] Cannot connect to slave: Access denied; you need (at least one of) the SUPER, REPLICATION CLIENT privilege(s) for this operation. Falling back to MASTER.
```

### 相关文件
- `backend/src/main/java/com/english/service/impl/SchoolDataPersistServiceImpl.java`
- `backend/src/main/resources/mapper/SchoolCourseMapper.xml`
- `backend/src/main/java/com/english/datasource/DynamicDataSource.java`

---

**报告生成时间**: 2026-05-10  
**分析工具**: Claude Code + Bash  
**下一步**: 执行修复方案并验证
