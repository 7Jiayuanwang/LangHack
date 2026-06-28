# 活动发布与统计功能 - API 接口文档

## 概述

本文档描述了活动发布与统计系统的所有 REST API 接口，包括管理员端和学生端的接口规范。

---

## 认证与权限

所有 API 接口需要 JWT 令牌认证，通过 HTTP Header 传递：

```
Authorization: Bearer <token>
```

### 权限要求

| 接口前缀 | 所需角色 |
|---------|---------|
| `/api/admin/campaigns/**` | ADMIN |
| `/api/student/campaigns/**` | USER 或 STUDENT |

---

## 管理员端 API

### 1. 活动管理

#### 1.1 创建活动

**请求**
```http
POST /api/admin/campaigns
Content-Type: application/json

{
  "title": "2024春季学习竞赛",
  "description": "春季学期学习活动竞赛",
  "startTime": "2024-03-01T00:00:00",
  "endTime": "2024-05-31T23:59:59",
  "completionRule": "THRESHOLD",
  "completionThreshold": 3,
  "enrollmentMode": "MANUAL",
  "enrollmentScope": "ALL",
  "enrollmentTargetIds": "[1,2,3]",
  "maxParticipants": 100,
  "showScores": true,
  "showRanking": true
}
```

**响应**
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "id": 1,
    "title": "2024春季学习竞赛",
    "status": "DRAFT",
    "createdTime": "2024-02-28T10:00:00"
  }
}
```

#### 1.2 更新活动

**请求**
```http
PUT /api/admin/campaigns/{id}
Content-Type: application/json

{
  "title": "2024春季学习竞赛（更新）",
  "description": "更新后的描述"
}
```

**响应**
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "id": 1,
    "title": "2024春季学习竞赛（更新）",
    "updatedTime": "2024-02-28T11:00:00"
  }
}
```

#### 1.3 删除活动

**请求**
```http
DELETE /api/admin/campaigns/{id}
```

**响应**
```json
{
  "code": 200,
  "message": "删除成功"
}
```

#### 1.4 获取活动详情

**请求**
```http
GET /api/admin/campaigns/{id}
```

**响应**
```json
{
  "code": 200,
  "data": {
    "id": 1,
    "title": "2024春季学习竞赛",
    "description": "春季学期学习活动竞赛",
    "status": "ACTIVE",
    "enrollmentMode": "MANUAL",
    "enrollmentScope": "ALL",
    "enrollmentTargetIdList": [1, 2, 3],
    "maxParticipants": 100,
    "showScores": true,
    "showRanking": true
  }
}
```

#### 1.5 分页查询活动列表

**请求**
```http
GET /api/admin/campaigns?current=1&size=10&status=ACTIVE&keyword=春季
```

**查询参数**
- `current`: 页码（默认1）
- `size`: 每页条数（默认10）
- `status`: 活动状态（可选：DRAFT/ACTIVE/ENDED/ARCHIVED）
- `keyword`: 关键词搜索（标题或描述）
- `createdBy`: 创建者ID（可选）

**响应**
```json
{
  "code": 200,
  "data": {
    "records": [
      {
        "id": 1,
        "title": "2024春季学习竞赛",
        "status": "ACTIVE",
        "startTime": "2024-03-01T00:00:00",
        "endTime": "2024-05-31T23:59:59"
      }
    ],
    "total": 1,
    "current": 1,
    "size": 10
  }
}
```

#### 1.6 启动活动

**请求**
```http
PUT /api/admin/campaigns/{id}/start
```

**响应**
```json
{
  "code": 200,
  "message": "活动已启动"
}
```

#### 1.7 结束活动

**请求**
```http
PUT /api/admin/campaigns/{id}/end
```

**响应**
```json
{
  "code": 200,
  "message": "活动已结束"
}
```

---

### 2. 任务管理

#### 2.1 创建任务

**请求**
```http
POST /api/admin/campaigns/{campaignId}/tasks
Content-Type: application/json

{
  "taskName": "阅读时长任务",
  "taskType": "READING_TIME",
  "taskUnit": "MINUTES",
  "targetValue": 60,
  "points": 10,
  "isRequired": true,
  "autoCollect": true,
  "sortOrder": 1
}
```

**任务类型**
- `READING_TIME`: 文章阅读时长
- `WORD_LEARNING`: 单词学习数量
- `TEXT_UPLOAD`: 文章上传数量
- `FEEDBACK`: 问题反馈数量
- `AI_QA`: AI问答使用次数
- `MANUAL`: 手动录入

**响应**
```json
{
  "code": 200,
  "data": {
    "id": 1,
    "campaignId": 1,
    "taskName": "阅读时长任务",
    "taskType": "READING_TIME"
  }
}
```

#### 2.2 更新任务

**请求**
```http
PUT /api/admin/campaigns/{campaignId}/tasks/{taskId}
Content-Type: application/json

{
  "targetValue": 90,
  "points": 15
}
```

#### 2.3 删除任务

**请求**
```http
DELETE /api/admin/campaigns/{campaignId}/tasks/{taskId}
```

#### 2.4 获取任务列表

**请求**
```http
GET /api/admin/campaigns/{campaignId}/tasks
```

**响应**
```json
{
  "code": 200,
  "data": [
    {
      "id": 1,
      "taskName": "阅读时长任务",
      "taskType": "READING_TIME",
      "targetValue": 60,
      "points": 10,
      "isRequired": true
    }
  ]
}
```

---

### 3. 统计与排名

#### 3.1 活动概览

**请求**
```http
GET /api/admin/campaigns/{campaignId}/stats/overview
```

**响应**
```json
{
  "code": 200,
  "data": {
    "totalParticipants": 150,
    "enrolledCount": 120,
    "taskCount": 5,
    "completionRate": 0.68
  }
}
```

#### 3.2 个人排名

**请求**
```http
GET /api/admin/campaigns/{campaignId}/stats/ranking/individual?current=1&size=20
```

**响应**
```json
{
  "code": 200,
  "data": {
    "records": [
      {
        "userId": 100,
        "userName": "张三",
        "className": "计算机2101",
        "totalScore": 85,
        "completedTasks": 5,
        "ranking": 1
      }
    ],
    "total": 120,
    "current": 1,
    "size": 20
  }
}
```

#### 3.3 班级排名

**请求**
```http
GET /api/admin/campaigns/{campaignId}/stats/ranking/class
```

**响应**
```json
{
  "code": 200,
  "data": [
    {
      "classId": 1,
      "className": "计算机2101",
      "totalScore": 2580,
      "avgScore": 85.67,
      "participantCount": 30
    }
  ]
}
```

#### 3.4 学院排名

**请求**
```http
GET /api/admin/campaigns/{campaignId}/stats/ranking/college
```

**响应**
```json
{
  "code": 200,
  "data": [
    {
      "collegeId": 1,
      "collegeName": "计算机学院",
      "totalScore": 8560,
      "avgScore": 82.45,
      "participantCount": 104
    }
  ]
}
```

#### 3.5 手动触发数据采集

**请求**
```http
POST /api/admin/campaigns/{campaignId}/stats/collect
```

**响应**
```json
{
  "code": 200,
  "message": "数据采集已触发"
}
```

---

### 4. 数据录入与导出

#### 4.1 手动录入数据

**请求**
```http
POST /api/admin/campaigns/{campaignId}/data/manual
Content-Type: application/json

{
  "taskId": 1,
  "userId": 100,
  "recordValue": 45.5,
  "remark": "手动补录数据"
}
```

**响应**
```json
{
  "code": 200,
  "message": "数据录入成功"
}
```

#### 4.2 Excel批量导入

**请求**
```http
POST /api/admin/campaigns/{campaignId}/data/import?taskId=1
Content-Type: multipart/form-data

file: <Excel文件>
```

**Excel格式**
| 学号 | 姓名 | 数值 | 备注 |
|------|------|------|------|
| 2021001 | 张三 | 60 | 完成阅读任务 |
| 2021002 | 李四 | 55 | |

**响应**
```json
{
  "code": 200,
  "data": {
    "successCount": 48,
    "failCount": 2,
    "skipCount": 0,
    "errors": [
      {
        "row": 5,
        "studentNumber": "2021005",
        "reason": "学号不存在"
      }
    ]
  }
}
```

#### 4.3 导出活动数据

**请求**
```http
GET /api/admin/campaigns/{campaignId}/data/export?format=xlsx
```

**查询参数**
- `format`: 导出格式（xlsx/csv，默认xlsx）

**响应**
- Content-Type: application/vnd.openxmlformats-officedocument.spreadsheetml.sheet
- 文件下载

#### 4.4 导出排名数据

**请求**
```http
GET /api/admin/campaigns/{campaignId}/data/export-ranking?dimension=individual&format=xlsx
```

**查询参数**
- `dimension`: 排名维度（individual/class/college）
- `format`: 导出格式（xlsx/csv，默认xlsx）

**响应**
- Content-Type: application/vnd.openxmlformats-officedocument.spreadsheetml.sheet
- 文件下载

---

### 5. 报名管理

#### 5.1 获取待审核报名

**请求**
```http
GET /api/admin/campaigns/{campaignId}/enrollments/pending
```

**响应**
```json
{
  "code": 200,
  "data": [
    {
      "id": 1,
      "userId": 100,
      "userName": "张三",
      "enrollmentTime": "2024-03-01T10:00:00",
      "enrollmentStatus": "PENDING"
    }
  ]
}
```

#### 5.2 批准报名

**请求**
```http
PUT /api/admin/campaigns/{campaignId}/enrollments/{participationId}/approve
```

**响应**
```json
{
  "code": 200,
  "message": "已批准报名"
}
```

#### 5.3 驳回报名

**请求**
```http
PUT /api/admin/campaigns/{campaignId}/enrollments/{participationId}/reject
Content-Type: application/json

{
  "reason": "不符合参与条件"
}
```

**响应**
```json
{
  "code": 200,
  "message": "已驳回报名"
}
```

#### 5.4 批量添加参与者

**请求**
```http
POST /api/admin/campaigns/{campaignId}/enrollments/batch-enroll
Content-Type: application/json

{
  "userIds": [100, 101, 102]
}
```

**响应**
```json
{
  "code": 200,
  "message": "已添加3名参与者"
}
```

---

## 学生端 API

### 1. 活动浏览

#### 1.1 获取可报名活动列表

**请求**
```http
GET /api/student/campaigns?current=1&size=10
```

**响应**
```json
{
  "code": 200,
  "data": {
    "records": [
      {
        "id": 1,
        "title": "2024春季学习竞赛",
        "description": "春季学期学习活动竞赛",
        "startTime": "2024-03-01T00:00:00",
        "endTime": "2024-05-31T23:59:59",
        "status": "ACTIVE",
        "canEnroll": true,
        "isEnrolled": false
      }
    ],
    "total": 5,
    "current": 1,
    "size": 10
  }
}
```

#### 1.2 获取我参与的活动

**请求**
```http
GET /api/student/campaigns/enrolled?current=1&size=10
```

**响应**
```json
{
  "code": 200,
  "data": {
    "records": [
      {
        "id": 1,
        "title": "2024春季学习竞赛",
        "totalScore": 45,
        "completedTasks": 3,
        "myRanking": 15,
        "enrollmentTime": "2024-03-01T10:00:00"
      }
    ],
    "total": 2
  }
}
```

#### 1.3 获取活动详情

**请求**
```http
GET /api/student/campaigns/{id}
```

**响应**
```json
{
  "code": 200,
  "data": {
    "id": 1,
    "title": "2024春季学习竞赛",
    "description": "春季学期学习活动竞赛",
    "startTime": "2024-03-01T00:00:00",
    "endTime": "2024-05-31T23:59:59",
    "status": "ACTIVE",
    "canEnroll": true,
    "isEnrolled": false,
    "enrollmentRestrictions": []
  }
}
```

---

### 2. 报名操作

#### 2.1 报名参加活动

**请求**
```http
POST /api/student/campaigns/{id}/enroll
```

**响应**
```json
{
  "code": 200,
  "message": "报名成功"
}
```

**可能的错误**
```json
{
  "code": 400,
  "message": "您不在活动参与范围内"
}
```

#### 2.2 退出活动

**请求**
```http
DELETE /api/student/campaigns/{id}/withdraw
```

**响应**
```json
{
  "code": 200,
  "message": "已退出活动"
}
```

---

### 3. 进度查询

#### 3.1 获取我的进度

**请求**
```http
GET /api/student/campaigns/{id}/progress
```

**响应**
```json
{
  "code": 200,
  "data": {
    "enrolled": true,
    "totalScore": 45,
    "completedTasks": 3,
    "ranking": 15,
    "totalParticipants": 120
  }
}
```

#### 3.2 获取我的任务进度

**请求**
```http
GET /api/student/campaigns/{id}/tasks
```

**响应**
```json
{
  "code": 200,
  "data": [
    {
      "taskId": 1,
      "taskName": "阅读时长任务",
      "targetValue": 60,
      "currentValue": 45,
      "isCompleted": false,
      "progress": 0.75
    }
  ]
}
```

#### 3.3 获取我的排名

**请求**
```http
GET /api/student/campaigns/{id}/ranking?range=5
```

**查询参数**
- `range`: 显示周围排名范围（默认5，即前后各5名）

**响应**
```json
{
  "code": 200,
  "data": {
    "myRanking": {
      "ranking": 15,
      "totalScore": 45,
      "completedTasks": 3,
      "userName": "张三"
    },
    "aroundMe": [
      {
        "ranking": 13,
        "userName": "李四",
        "totalScore": 48
      },
      {
        "ranking": 14,
        "userName": "王五",
        "totalScore": 47
      },
      {
        "ranking": 16,
        "userName": "赵六",
        "totalScore": 44
      }
    ]
  }
}
```

---

## 错误码说明

| 错误码 | 说明 |
|--------|------|
| 200 | 成功 |
| 400 | 请求参数错误 |
| 401 | 未认证 |
| 403 | 无权限 |
| 404 | 资源不存在 |
| 500 | 服务器内部错误 |

---

## 完成规则说明

### CompletionRule 枚举

- **ANY**: 完成任意一个任务即算完成
- **ALL**: 必须完成所有任务
- **THRESHOLD**: 完成指定数量的任务（由 `completionThreshold` 字段指定）

### EnrollmentMode 枚举

- **AUTO**: 自动报名（符合条件即自动加入）
- **MANUAL**: 学生自主报名（手动提交报名申请）
- **INVITE_ONLY**: 仅邀请（需要管理员审核批准）

### EnrollmentScope 枚举

- **ALL**: 全部用户
- **COLLEGE**: 指定学院
- **GRADE**: 指定年级
- **CLASS**: 指定班级
- **CUSTOM**: 自定义用户白名单

---

## 附录：Excel 导入模板

### 模板格式

| 学号 | 姓名 | 数值 | 备注 |
|------|------|------|------|
| 2021001 | 张三 | 60 | 完成阅读任务 |
| 2021002 | 李四 | 55 | |

### 字段说明

- **学号**: 必填，学生学号
- **姓名**: 选填，用于数据校验
- **数值**: 必填，任务完成数值（如阅读60分钟、学习50个单词）
- **备注**: 选填，备注信息

### 注意事项

1. 第一行为表头，从第二行开始为数据
2. 学号必须在系统中存在
3. 数值必须为正数
4. 单次导入建议不超过1000行
5. 支持 .xlsx 和 .xls 格式
