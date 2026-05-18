# 学生课表显示问题修复方案

## 问题分析

根据用户提供的截图和代码检查，发现以下问题：

### 1. 学生课表显示学号而非姓名
- **现象**：课表中显示的是学号（如 `2023122066164`）而不是学生姓名
- **根因**：`StudentSchedule` 实体有 `student_name` 字段，但数据同步时可能未正确映射或为空

### 2. 课表信息展示不完整
- **现象**：课表只显示课程名和教室，缺少教师、周次等信息
- **根因**：前端 `Schedule.vue` 的课表网格只显示了 `courseName` 和 `classLocation`

### 3. 课表应以表格形式呈现
- **现象**：当前是列表形式，不够直观
- **根因**：前端已经实现了网格布局，但需要优化显示内容

### 4. 师生绑定关系管理
- **需求**：管理员需要按学院统一管理师生绑定关系
- **现状**：已有 `teacher_student_binding` 表和链式拉取功能

### 5. 链式目录映射逻辑
- **问题**：链式拉取的学生和教师目录可能存在空数据
- **根因**：字段映射或数据源问题

### 6. 备份恢复增量逻辑
- **需求**：备份恢复时不删除新增数据，实现增量恢复
- **现状**：当前备份恢复是全量覆盖

## 修复方案

### 任务 1：修复学生课表数据同步（优先级：P0）

#### 1.1 检查字段映射
- 确保 `student_schedule` 的 `student_name` 字段正确映射
- 检查 CDSP API 返回的学生姓名字段（可能是 `XM` 或 `xm`）

#### 1.2 修复数据同步逻辑
```java
// SchoolDataSyncServiceImpl.java
// 在 getOrSyncStudentSchedule 方法中，确保 student_name 正确填充
```

#### 1.3 添加数据验证
- 同步后检查 `student_name` 是否为空
- 如果为空，从 `school_student` 表补充

### 任务 2：优化前端课表显示（优先级：P0）

#### 2.1 增强课表网格显示
```vue
<!-- Schedule.vue -->
<div class="cell-item">
  <div class="cell-course">{{ item.courseName }}</div>
  <div class="cell-teacher">{{ item.teacherName }}</div>
  <div class="cell-location">{{ item.classLocation }}</div>
  <div class="cell-weeks">{{ item.weekRange }}</div>
</div>
```

#### 2.2 优化详情弹窗
- 显示完整的课程信息
- 添加学生姓名显示（如果是管理员查看）

### 任务 3：师生绑定关系管理（优先级：P1）

#### 3.1 新增管理员接口
```java
// TeacherStudentBindingController.java
@GetMapping("/api/admin/teacher-student-bindings")
@PreAuthorize("hasAnyRole('ADMIN', 'OPS')")
public Result<Page<TeacherStudentBinding>> listBindings(
    @RequestParam(required = false) String college,
    @RequestParam(defaultValue = "1") int page,
    @RequestParam(defaultValue = "20") int size
)
```

#### 3.2 新增前端管理页面
- 路径：`/admin/teacher-student-bindings`
- 功能：按学院筛选、查看、删除绑定关系

### 任务 4：修复链式目录映射（优先级：P1）

#### 4.1 检查字段映射配置
- 确保 `school_student` 和 `school_teacher` 的字段映射完整
- 检查 `field_mappings` 表中的配置

#### 4.2 添加数据验证日志
- 在链式拉取时记录空数据情况
- 提供详细的错误信息

### 任务 5：实现增量备份恢复（优先级：P2）

#### 5.1 新增增量恢复选项
```java
// BackupService.java
public void restoreBackupIncremental(Long backupId) {
    // 使用 INSERT IGNORE 或 ON DUPLICATE KEY UPDATE
    // 保留新增数据，只更新已存在的记录
}
```

#### 5.2 前端添加恢复模式选择
```vue
<!-- Backups.vue -->
<el-radio-group v-model="restoreMode">
  <el-radio label="full">全量恢复（覆盖所有数据）</el-radio>
  <el-radio label="incremental">增量恢复（保留新增数据）</el-radio>
</el-radio-group>
```

## 实施步骤

### 第一阶段：紧急修复（1-2天）
1. 修复学生课表 `student_name` 显示问题
2. 优化前端课表网格显示
3. 测试课表功能完整性

### 第二阶段：功能增强（2-3天）
1. 实现师生绑定关系管理界面
2. 修复链式目录映射逻辑
3. 添加数据验证和错误提示

### 第三阶段：备份优化（1-2天）
1. 实现增量备份恢复功能
2. 添加备份恢复模式选择
3. 测试备份恢复完整性

## 验收标准

### 学生课表
- [ ] 课表显示学生姓名而非学号
- [ ] 课表网格显示完整信息（课程、教师、教室、周次）
- [ ] 课表以表格形式呈现，直观易读
- [ ] 点击课程可查看详细信息

### 师生绑定
- [ ] 管理员可按学院查看师生绑定关系
- [ ] 可以删除或修改绑定关系
- [ ] 支持批量操作

### 数据同步
- [ ] 链式拉取不再产生空数据
- [ ] 数据映射正确，字段完整
- [ ] 同步失败有明确错误提示

### 备份恢复
- [ ] 支持全量恢复模式
- [ ] 支持增量恢复模式（保留新增数据）
- [ ] 恢复后数据完整性验证

## 风险评估

### 高风险
- 数据库结构变更可能影响现有功能
- 备份恢复逻辑变更需要充分测试

### 中风险
- 字段映射修改可能影响其他数据源
- 前端显示优化可能影响移动端适配

### 低风险
- 新增管理界面不影响现有功能
- 数据验证增强只是日志记录

## 测试计划

### 单元测试
- [ ] `StudentSchedule` 数据同步测试
- [ ] 字段映射正确性测试
- [ ] 备份恢复逻辑测试

### 集成测试
- [ ] 学生课表端到端测试
- [ ] 师生绑定管理测试
- [ ] 链式拉取完整流程测试

### 用户验收测试
- [ ] 学生查看课表功能
- [ ] 管理员管理师生绑定
- [ ] 备份恢复功能验证
