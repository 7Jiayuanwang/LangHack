# 默认任务计划系统

## 概述

默认任务计划系统允许管理员创建学期级别的学习目标计划，涵盖文章阅读、单词背诵、真题练习三个维度，并关联课程和班级。教师可以针对自己的班级修改目标参数或退出计划，学生可以查看自己的完成进度和得分。

## 数据库表

| 表名 | 说明 |
|------|------|
| `task_plans` | 任务计划主表，存储计划名称、课程、周期类型、目标值、权重和状态 |
| `task_plan_classes` | 计划与班级的多对多关联，支持教师覆盖目标值和退出机制 |
| `task_plan_records` | 学生按周期的完成记录（文章数/单词数/真题数） |

Flyway 迁移脚本：`V50__task_plan_system.sql`

## API 接口

### 管理员接口 (`/api/admin/task-plans`)

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/` | 创建计划（草稿） |
| GET | `/` | 列表查询（支持 status 筛选） |
| GET | `/{id}` | 获取详情（含关联班级） |
| PUT | `/{id}` | 更新计划（仅草稿可编辑） |
| POST | `/{id}/publish` | 发布计划（DRAFT -> ACTIVE） |
| POST | `/{id}/close` | 关闭计划 |
| GET | `/{id}/stats` | 获取完成统计 |
| GET | `/{id}/export` | 导出成绩 CSV |
| POST | `/{id}/refresh` | 手动刷新完成度数据 |

### 教师接口 (`/api/teacher/task-plans`)

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/` | 查看与自己班级关联的活跃计划 |
| PUT | `/{planId}/classes/{classId}` | 修改班级目标值覆盖 |
| POST | `/{planId}/classes/{classId}/opt-out` | 退出计划 |
| POST | `/{planId}/classes/{classId}/opt-in` | 重新加入计划 |
| GET | `/{planId}/classes/{classId}/progress` | 查看班级学生进度 |
| GET | `/{planId}/classes/{classId}/export` | 导出班级成绩 CSV |

### 学生接口 (`/api/student/task-plans`)

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/` | 查看自己参与的活跃计划及完成进度 |

## 平时分计算公式

```
单项完成率 = min(实际完成数 / 目标数, 1.0)
单项得分 = 完成率 × 权重% × 满分值
平时分 = 文章得分 + 单词得分 + 真题得分
```

示例：满分 100 分，权重 40/30/30，文章目标 10 篇完成 8 篇：
- 文章得分 = min(8/10, 1.0) × 40% × 100 = 32 分

## 词库选择

管理员创建计划时选择单词词库类型：
- **CET4**：四级词汇
- **CET6**：六级词汇
- **GAOKAO**：高考词汇

教师可为自己的班级覆盖词库类型。

## 教师权限

- 教师只能操作自己所属班级的计划关联
- 教师可修改的字段：文章目标、单词目标、词库类型、真题目标、各项权重
- 教师可选择退出计划（该班级不再参与统计），也可重新加入

## 前端入口

| 角色 | 路径 | 入口位置 |
|------|------|---------|
| 管理员 | `/admin/task-plans` | 管理下拉菜单 → 默认任务 |
| 教师 | `/teacher/task-plans` | 底部导航栏 → 默认任务 |
| 学生 | `/student/task-plans` | 学习页快捷入口 → 学期目标 |

## 多班级布置任务

同时改造了原有布置任务功能，支持一次向多个班级发布同一任务：
- 后端 `POST /api/assignments/create-with-content` 支持 `classIds` 数组参数
- 前端管理员端和教师端班级选择改为 `el-select multiple` 多选模式
- 兼容旧的单 `classId` 参数

## 相关文件

### 后端
- `db/migration/V50__task_plan_system.sql`
- `entity/TaskPlan.java`, `TaskPlanClass.java`, `TaskPlanRecord.java`
- `mapper/TaskPlanMapper.java`, `TaskPlanClassMapper.java`, `TaskPlanRecordMapper.java`
- `service/TaskPlanService.java`, `service/impl/TaskPlanServiceImpl.java`
- `controller/TaskPlanController.java`, `TeacherTaskPlanController.java`, `StudentTaskPlanController.java`

### 前端
- `api/taskPlan.js`
- `views/admin/TaskPlans.vue`
- `views/teacher/TaskPlans.vue`
- `views/student/TaskPlans.vue`
