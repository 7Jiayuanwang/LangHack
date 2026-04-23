# 学校数据同步 API 参考

> 版本: v1.0
> 创建日期: 2026-04-12
> 前缀: `/api/admin/sync`
> 权限: 所有接口需 ADMIN 或 OPS 角色 (`@PreAuthorize("hasAnyRole('ADMIN', 'OPS')")`)

---

## 目录

1. [同步操作](#同步操作)
2. [状态查询](#状态查询)
3. [日志与告警](#日志与告警)
4. [字段映射管理](#字段映射管理)
5. [数据模型](#数据模型)
6. [错误码](#错误码)

---

## 同步操作

### POST /full/{dataSource}

全量同步指定数据源。

**请求参数**:

| 参数 | 类型 | 位置 | 必填 | 默认值 | 说明 |
|------|------|------|------|--------|------|
| dataSource | String | Path | 是 | - | 数据源名称 |
| triggerType | String | Query | 否 | MANUAL | 触发类型 |

**响应**:

```json
{
  "code": 200,
  "message": "全量同步完成",
  "data": {
    "id": 1,
    "syncType": "FULL",
    "dataSource": "administrative_class",
    "syncMethod": "REPLACE_ALL",
    "status": "SUCCESS",
    "totalCount": 150,
    "successCount": 148,
    "failCount": 2,
    "skipCount": 0,
    "startTime": "2026-04-12T02:00:00",
    "endTime": "2026-04-12T02:00:15",
    "durationMs": 15000,
    "errorMessage": null,
    "triggerType": "MANUAL",
    "operator": "admin"
  }
}
```

**示例**:

```bash
# 全量同步行政班
curl -X POST "http://localhost:8080/api/admin/sync/full/administrative_class?triggerType=MANUAL" \
  -H "Authorization: Bearer <token>"
```

---

### POST /incremental/{dataSource}

增量同步指定数据源。自动查询上次同步时间作为 `updatedSince` 参数。

**请求参数**:

| 参数 | 类型 | 位置 | 必填 | 默认值 | 说明 |
|------|------|------|------|--------|------|
| dataSource | String | Path | 是 | - | 数据源名称 |
| triggerType | String | Query | 否 | MANUAL | 触发类型 |

**响应**: 同全量同步

**示例**:

```bash
# 增量同步选课记录
curl -X POST "http://localhost:8080/api/admin/sync/incremental/course_selection" \
  -H "Authorization: Bearer <token>"
```

---

### POST /full-all

全量同步所有7个数据源。单个数据源失败不影响其他数据源。

**请求参数**:

| 参数 | 类型 | 位置 | 必填 | 默认值 | 说明 |
|------|------|------|------|--------|------|
| triggerType | String | Query | 否 | MANUAL | 触发类型 |

**响应**:

```json
{
  "code": 200,
  "message": "全量同步所有数据源完成",
  "data": [
    { "id": 1, "dataSource": "administrative_class", "status": "SUCCESS", ... },
    { "id": 2, "dataSource": "school_course", "status": "SUCCESS", ... },
    { "id": 3, "dataSource": "teaching_class", "status": "FAILED", ... },
    { "id": 4, "dataSource": "course_schedule", "status": "SUCCESS", ... },
    { "id": 5, "dataSource": "course_selection", "status": "SUCCESS", ... },
    { "id": 6, "dataSource": "level_exam_scores", "status": "SUCCESS", ... },
    { "id": 7, "dataSource": "teaching_plans", "status": "SUCCESS", ... }
  ]
}
```

---

### POST /incremental-all

增量同步所有7个数据源。

**请求参数**: 同 full-all

**响应**: 同 full-all

---

## 状态查询

### GET /status

获取同步状态概览，包含7个数据源的本地数据量和最后同步状态。

**响应**:

```json
{
  "code": 200,
  "data": {
    "administrative_class": {
      "localCount": 150,
      "lastSyncStatus": "SUCCESS",
      "lastSyncTime": "2026-04-12T02:00:15"
    },
    "school_course": {
      "localCount": 320,
      "lastSyncStatus": "NEVER",
      "lastSyncTime": null
    }
  }
}
```

---

### GET /health

获取同步健康检查结果，包含4级告警汇总。

**响应**:

```json
{
  "code": 200,
  "data": {
    "summary": {
      "totalSources": 7,
      "healthyCount": 5,
      "warningCount": 1,
      "errorCount": 1,
      "criticalCount": 0,
      "overallStatus": "ERROR",
      "checkedAt": "2026-04-12T10:30:00"
    },
    "sources": {
      "administrative_class": {
        "dataSource": "administrative_class",
        "status": "HEALTHY",
        "alertLevel": "INFO",
        "message": "同步正常",
        "lastSyncTime": "2026-04-12T02:00:15",
        "lastSyncStatus": "SUCCESS",
        "totalCount": 150,
        "successCount": 148,
        "failCount": 2
      },
      "course_selection": {
        "dataSource": "course_selection",
        "status": "SYNC_FAILED",
        "alertLevel": "ERROR",
        "message": "最近一次同步失败: 连接超时",
        "lastSyncTime": "2026-04-11T06:00:00"
      }
    },
    "alerts": [
      {
        "dataSource": "course_selection",
        "level": "ERROR",
        "status": "SYNC_FAILED",
        "message": "最近一次同步失败: 连接超时",
        "lastSyncTime": "2026-04-11T06:00:00",
        "detectedAt": "2026-04-12T10:30:00"
      }
    ]
  }
}
```

---

### GET /validate

数据校验，检查字段映射配置和同步状态。

**响应**:

```json
{
  "code": 200,
  "data": {
    "totalIssues": 2,
    "overallHealthy": false,
    "validatedAt": "2026-04-12T04:00:00",
    "sources": {
      "administrative_class": {
        "hasMappings": true,
        "mappingCount": 12,
        "lastSyncStatus": "SUCCESS",
        "issues": []
      },
      "course_schedule": {
        "hasMappings": false,
        "mappingCount": 0,
        "lastSyncStatus": "NEVER",
        "issues": ["缺少字段映射配置", "从未执行过同步"]
      }
    }
  }
}
```

---

### GET /test-connection

测试学校教务API连通性。

**响应**:

```json
{
  "code": 200,
  "message": "API连接正常",
  "data": true
}
```

---

## 日志与告警

### GET /logs/{dataSource}

获取指定数据源的同步历史记录。

**请求参数**:

| 参数 | 类型 | 位置 | 必填 | 默认值 | 说明 |
|------|------|------|------|--------|------|
| dataSource | String | Path | 是 | - | 数据源名称 |
| limit | Integer | Query | 否 | 20 | 返回记录数量 |

**响应**:

```json
{
  "code": 200,
  "data": [
    {
      "id": 1,
      "syncType": "FULL",
      "dataSource": "administrative_class",
      "syncMethod": "REPLACE_ALL",
      "status": "SUCCESS",
      "totalCount": 150,
      "successCount": 148,
      "failCount": 2,
      "skipCount": 0,
      "startTime": "2026-04-12T02:00:00",
      "endTime": "2026-04-12T02:00:15",
      "durationMs": 15000,
      "errorMessage": null,
      "triggerType": "SCHEDULED",
      "operator": "SYSTEM"
    }
  ]
}
```

---

### GET /alerts

获取最近告警列表，按严重程度排序 (CRITICAL > ERROR > WARNING)。

**请求参数**:

| 参数 | 类型 | 位置 | 必填 | 默认值 | 说明 |
|------|------|------|------|--------|------|
| limit | Integer | Query | 否 | 10 | 返回告警数量 |

**响应**:

```json
{
  "code": 200,
  "data": [
    {
      "dataSource": "level_exam_scores",
      "level": "CRITICAL",
      "status": "NEVER_SYNCED",
      "message": "该数据源从未执行过同步",
      "lastSyncTime": null,
      "detectedAt": "2026-04-12T10:30:00"
    },
    {
      "dataSource": "course_selection",
      "level": "ERROR",
      "status": "SYNC_FAILED",
      "message": "最近一次同步失败: 连接超时",
      "lastSyncTime": "2026-04-11T06:00:00",
      "detectedAt": "2026-04-12T10:30:00"
    }
  ]
}
```

---

## 字段映射管理

### GET /field-mappings/{dataSource}

获取指定数据源的字段映射配置。

**请求参数**:

| 参数 | 类型 | 位置 | 必填 | 说明 |
|------|------|------|------|------|
| dataSource | String | Path | 是 | 数据源名称 |

**响应**:

```json
{
  "code": 200,
  "data": [
    {
      "id": 1,
      "dataSource": "administrative_class",
      "sourceField": "XZBH",
      "targetField": "classCode",
      "targetType": "STRING",
      "converter": "NONE",
      "converterConfig": null,
      "defaultValue": null,
      "isRequired": false,
      "isActive": true,
      "sortOrder": 1
    },
    {
      "id": 2,
      "dataSource": "school_course",
      "sourceField": "SFSYJX",
      "targetField": "isBilingual",
      "targetType": "STRING",
      "converter": "BOOLEAN",
      "converterConfig": "{\"trueValues\":[\"是\",\"1\",\"true\"],\"falseValues\":[\"否\",\"0\",\"false\"]}",
      "defaultValue": null,
      "isRequired": false,
      "isActive": true,
      "sortOrder": 5
    }
  ]
}
```

---

### PUT /field-mappings

更新字段映射配置。

**请求体**:

```json
{
  "id": 2,
  "converter": "BOOLEAN",
  "converterConfig": "{\"trueValues\":[\"是\",\"1\",\"true\"],\"falseValues\":[\"否\",\"0\",\"false\"]}",
  "defaultValue": "false"
}
```

**校验规则**:
- `id` 不能为空，否则抛出 `BusinessException`
- 对应记录必须存在，否则抛出 `BusinessException`
- `converter` 必须为 `NONE`/`DATE`/`BOOLEAN`/`SPLIT`/`JSON` 之一，否则抛出 `BusinessException`

**响应**:

```json
{
  "code": 200,
  "message": "字段映射更新成功",
  "data": { ... }
}
```

---

## 数据模型

### SyncLog

| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | 主键 |
| syncType | String | 同步类型: FULL / INCREMENTAL |
| dataSource | String | 数据源名称 |
| syncMethod | String | 同步方法: REPLACE_ALL / UPSERT |
| status | String | 状态: RUNNING / SUCCESS / FAILED |
| totalCount | Integer | 总记录数 |
| successCount | Integer | 成功记录数 |
| failCount | Integer | 失败记录数 |
| skipCount | Integer | 跳过记录数 |
| startTime | LocalDateTime | 开始时间 |
| endTime | LocalDateTime | 结束时间 |
| durationMs | Long | 耗时(毫秒) |
| errorMessage | String | 错误信息(截断至500字符) |
| triggerType | String | 触发类型: MANUAL / SCHEDULED |
| operator | String | 操作人 |
| createdAt | LocalDateTime | 创建时间 |

### FieldMapping

| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | 主键 |
| dataSource | String | 数据源名称 |
| sourceField | String | 源字段名 |
| targetField | String | 目标字段名 |
| targetType | String | 目标类型: STRING / INTEGER / DECIMAL / DATE / DATETIME / BOOLEAN |
| converter | String | 转换器: NONE / DATE / BOOLEAN / SPLIT / JSON |
| converterConfig | String | 转换器配置(JSON格式) |
| defaultValue | String | 默认值 |
| isRequired | Boolean | 是否必填 |
| isActive | Boolean | 是否启用 |
| sortOrder | Integer | 排序序号 |

### 转换器配置格式

**DATE转换器**:
```json
{
  "pattern": "yyyy-MM-dd",
  "fallbackPatterns": ["yyyy/MM/dd", "yyyyMMdd"]
}
```

**BOOLEAN转换器**:
```json
{
  "trueValues": ["是", "1", "true"],
  "falseValues": ["否", "0", "false"]
}
```

**SPLIT转换器**:
```json
{
  "delimiter": ","
}
```

**JSON转换器**:
```json
{
  "key": "fieldName"
}
```

---

## 错误码

| HTTP状态 | code | 说明 |
|----------|------|------|
| 200 | 200 | 操作成功 |
| 200 | 500 | 业务异常（同步失败、校验失败等） |
| 401 | - | 未认证（需要登录） |
| 403 | - | 无权限（需要ADMIN或OPS角色） |

### 常见错误场景

| 场景 | 返回message |
|------|-----------|
| 不支持的数据源 | `全量同步失败: 不支持的数据源: xxx` |
| API连接失败 | `全量同步失败: API连接失败` |
| 同步正在运行 | 被AtomicBoolean拒绝，上一次同步结果会返回 |
| 字段映射ID为空 | `BusinessException: 映射ID不能为空` |
| 字段映射不存在 | `BusinessException: 映射记录不存在` |
| 无效转换器类型 | `BusinessException: 不支持的转换器类型: XXX` |

---

**文档结束**
