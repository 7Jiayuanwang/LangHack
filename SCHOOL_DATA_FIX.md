# 学校数据功能修复总结

## 问题诊断

### 1. 核心错误：接口地址配置错误

**症状**：
- 代码中配置的接口地址为 `/api/xzxx`, `/api/kcxx` 等相对路径
- 实际官方接口使用完全独立的 URL，每个接口有唯一的标识符

**根因**：
- 官方接口文档显示每个接口都有独立的完整 URL
- 格式：`https://cdsp.imau.edu.cn/cdsp/data-api/v2/{unique_id}`
- 不是 baseUrl + path 的组合模式

**官方接口地址（基于官方文档）**：
```
行政班基本信息:     https://cdsp.imau.edu.cn/cdsp/data-api/v2/cdsp_171094991198337
课程基本信息:       https://cdsp.imau.edu.cn/cdsp/data-api/v2/cdsp_171529094506435
教学班信息:         https://cdsp.imau.edu.cn/cdsp/data-api/v2/cdsp_172180403086015
课程安排:           https://cdsp.imau.edu.cn/cdsp/data-api/v2/cdsp_171529037780151
学生选课:           https://cdsp.imau.edu.cn/cdsp/data-api/v2/cdsp_171529480287192
等级考试成绩:       https://cdsp.imau.edu.cn/cdsp/data-api/v2/DS0076
教学计划:           https://cdsp.imau.edu.cn/cdsp/data-api/v2/cdsp_171528965502646
教师授课:           https://cdsp.imau.edu.cn/cdsp/data-api/v2/cdsp_171529464567780
学生课表:           https://cdsp.imau.edu.cn/cdsp/data-api/v2/cdsp_171529439727434
```

### 2. ETL 数据源配置为空导致 HikariCP 报错
**症状**：
```
java.lang.IllegalArgumentException: jdbcUrl is required with driverClassName.
```

### 2. ETL 数据源配置为空导致 HikariCP 报错
**症状**：
```
java.lang.IllegalArgumentException: jdbcUrl is required with driverClassName.
```

**根因**：
- 数据库中 `etl_jdbc_url`、`etl_username`、`etl_password` 初始化为空字符串
- 用户保存配置时（即使选择 `source=api`），后端也会尝试用空值重建 ETL DataSource
- HikariCP 要求 `jdbcUrl` 不能为空

### 3. API 连接测试失败（404）
**症状**：
```
School data API connection test failed
org.springframework.web.client.HttpClientErrorException$NotFound: 404 : [no body]
```

### 3. API 连接测试失败（404）
**症状**：
```
School data API connection test failed
org.springframework.web.client.HttpClientErrorException$NotFound: 404 : [no body]
```

**根因**：
- 测试连接时访问 `/api/health` 端点
- 学校 API 可能没有健康检查端点

### 4. 权限问题（Access Denied）
### 4. 权限问题（Access Denied）
**症状**：
- 学生角色访问 `/api/admin/school-data/*` 被拒绝（正常）
- 用户切换到 ADMIN 后仍然看到"拒绝访问"（前端缓存了旧 token）

### 5. 功能开关缺失
- 学校数据浏览和配置页面没有功能开关保护
- 运维端导航菜单无法根据功能开关动态显示/隐藏

---

## 修复方案

### 后端修复

#### 1. SchoolDataProperties.java - 修正接口地址配置
**修改**：将相对路径改为完整独立 URL

```java
// 修改前（错误）
private String baseUrl = "https://api.imau.edu.cn";
private List<EndpointMapping> endpoints = Arrays.asList(
    new EndpointMapping("administrative_class", "/api/xzxx"),
    new EndpointMapping("school_course", "/api/kcxx"),
    ...
);

// 修改后（正确）
@Deprecated
private String baseUrl = "https://cdsp.imau.edu.cn";  // 仅用于兼容性
private List<EndpointMapping> endpoints = Arrays.asList(
    new EndpointMapping("administrative_class", "https://cdsp.imau.edu.cn/cdsp/data-api/v2/cdsp_171094991198337"),
    new EndpointMapping("school_course", "https://cdsp.imau.edu.cn/cdsp/data-api/v2/cdsp_171529094506435"),
    new EndpointMapping("teaching_class", "https://cdsp.imau.edu.cn/cdsp/data-api/v2/cdsp_172180403086015"),
    new EndpointMapping("course_schedule", "https://cdsp.imau.edu.cn/cdsp/data-api/v2/cdsp_171529037780151"),
    new EndpointMapping("course_selection", "https://cdsp.imau.edu.cn/cdsp/data-api/v2/cdsp_171529480287192"),
    new EndpointMapping("level_exam_scores", "https://cdsp.imau.edu.cn/cdsp/data-api/v2/DS0076"),
    new EndpointMapping("teaching_plans", "https://cdsp.imau.edu.cn/cdsp/data-api/v2/cdsp_171528965502646"),
    new EndpointMapping("teacher_courses", "https://cdsp.imau.edu.cn/cdsp/data-api/v2/cdsp_171529464567780"),
    new EndpointMapping("student_schedule", "https://cdsp.imau.edu.cn/cdsp/data-api/v2/cdsp_171529439727434")
);
```

**白名单更新**：
```java
private static final List<String> API_ALLOWED_DOMAINS = Arrays.asList(
    "cdsp.imau.edu.cn",  // 新增
    "api.imau.edu.cn",
    "imau.edu.cn"
);
```

#### 2. SchoolDataApiClientImpl.java - 修改 URL 构建逻辑
**修改**：直接使用完整 URL，不再拼接 baseUrl + path

```java
// 修改前
private String buildUrl(String baseUrl, String path, Map<String, String> params) {
    StringBuilder url = new StringBuilder(baseUrl);
    if (!baseUrl.endsWith("/") && !path.startsWith("/")) {
        url.append("/");
    }
    url.append(path);
    // ...
}

// 修改后
private String buildUrl(String fullUrl, Map<String, String> params) {
    StringBuilder url = new StringBuilder(fullUrl);
    // 直接使用完整 URL，只添加查询参数
    // ...
}
```

#### 3. SchoolDataConfigRefreshService.java
**修改**：仅在 `source=etl` 且 `jdbcUrl` 非空时才重建 DataSource

```java
// 6. 如果 ETL 数据源配置变了 → 重建 DataSource（仅当 source=etl 且配置完整时）
boolean etlDsChanged = changedKeys.stream().anyMatch(ETL_DATASOURCE_KEYS::contains);
if (etlDsChanged && "etl".equalsIgnoreCase(properties.getSource())) {
    // 检查 ETL 配置是否完整
    String jdbcUrl = properties.getEtl().getJdbcUrl();
    if (jdbcUrl != null && !jdbcUrl.isEmpty() && !jdbcUrl.isBlank()) {
        logger.info("ETL datasource config changed, recreating DataSource");
        etlClient.recreateDataSource();
    } else {
        logger.warn("ETL datasource config changed but jdbcUrl is empty, skip DataSource recreation");
    }
}
```

#### 2. SchoolDataEtlClientImpl.java
**修改**：在创建 DataSource 前校验 `jdbcUrl` 必填

```java
private DataSource createSchoolDataSource() {
    SchoolDataProperties.EtlProperties etl = properties.getEtl();

    // 校验必填配置
    String jdbcUrl = etl.getJdbcUrl();
    if (jdbcUrl == null || jdbcUrl.isEmpty() || jdbcUrl.isBlank()) {
        throw new IllegalArgumentException("ETL jdbcUrl is required but not configured");
    }
    // ... 后续创建 HikariDataSource
}
```

#### 3. SchoolDataApiClientImpl.java
**修改**：测试连接时访问实际端点而非 `/api/health`

```java
@Override
public boolean testConnection() {
    String baseUrl = properties.getApi().getBaseUrl();
    if (baseUrl == null || baseUrl.isEmpty()) {
        logger.warn("School data API base URL is not configured");
        return false;
    }

    try {
        HttpHeaders headers = buildHeaders();
        HttpEntity<Void> entity = new HttpEntity<>(headers);
        // 尝试访问行政班端点而非 /api/health（学校API可能没有健康检查端点）
        String testEndpoint = properties.getApi().getEndpointPath("administrative_class");
        if (testEndpoint == null) {
            testEndpoint = "/api/xzxx"; // fallback
        }
        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl + testEndpoint + "?pageSize=1", HttpMethod.GET, entity, String.class);
        return response.getStatusCode().is2xxSuccessful();
    } catch (RestClientException e) {
        logger.error("School data API connection test failed", e);
        return false;
    }
}
```

### 前端修复

#### 1. 路由添加功能开关（frontend/src/router/index.js）
```javascript
{
  path: "school-data",
  name: "OpsSchoolData",
  component: () => import("@/views/ops/SchoolDataConfig.vue"),
  meta: { title: "学校数据", roles: ["OPS", "ADMIN"], featureKey: "ops.school_data_config" },
},
{
  path: "school-data-browser",
  name: "OpsSchoolDataBrowser",
  component: () => import("@/views/ops/SchoolDataBrowser.vue"),
  meta: { title: "数据浏览", roles: ["OPS", "ADMIN"], featureKey: "ops.school_data_browser" },
},
```

#### 2. 导航菜单添加功能开关（frontend/src/views/shared/MobileLayout.vue）
```javascript
ops: [
  { path: '/ops/monitoring',      label: '监控', icon: DataLine    },
  { path: '/ops/backups',         label: '备份', icon: FolderOpened },
  { path: '/ops/users',           label: '用户', icon: User        },
  { path: '/ops/ai-providers',    label: 'AI',   icon: Connection  },
  { path: '/ops/feature-toggles', label: '开关', icon: Switch      },
  { path: '/ops/school-data',         label: '数据', icon: Coin,        featureKey: 'ops.school_data_config' },
  { path: '/ops/school-data-browser',  label: '浏览', icon: DataLine,    featureKey: 'ops.school_data_browser' },
  { path: '/ops/update',          label: '更新', icon: Refresh     },
],
```

#### 3. 添加功能开关过滤逻辑
```javascript
// 导入功能开关 API
import { getFeatureToggles } from '@/api/systemConfig'

// 功能开关状态
const featureToggles = ref({})

// 过滤导航项
const navItems = computed(() => {
  const items = NAV_ITEMS[props.role] || []
  // 运维端需要根据功能开关过滤导航项
  if (props.role === 'ops') {
    return items.filter(item => {
      if (!item.featureKey) return true
      const val = featureToggles.value[item.featureKey]
      return val === undefined || val === true || val === 'true'
    })
  }
  return items
})

// 加载功能开关
onMounted(async () => {
  // ... 权限守卫 ...

  // 加载功能开关（运维端）
  if (props.role === 'ops') {
    try {
      const res = await getFeatureToggles()
      if (res?.code === 200 && res.data) {
        featureToggles.value = res.data
      }
    } catch (e) {
      console.error('Failed to load feature toggles:', e)
    }
  }

  // ... SSO 检查 ...
})
```

### 数据库迁移

#### V18__school_data_feature_toggle.sql
```sql
-- 运维端：学校数据浏览和配置功能开关（默认关闭，仅运维和管理员可见）
INSERT IGNORE INTO `feature_toggles` (`feature_key`, `feature_name`, `description`, `default_enabled`, `scope`, `updated_by`)
VALUES
('ops.school_data_browser', '学校数据浏览', '运维端学校数据浏览功能（行政班、课程、教学班等）', 0, 'global', 'system'),
('ops.school_data_config', '学校数据配置', '运维端学校数据源配置功能（API/ETL切换、连接测试）', 0, 'global', 'system');

-- 学生端：课表功能开关（默认关闭，需要学校数据源配置完成后启用）
INSERT IGNORE INTO `feature_toggles` (`feature_key`, `feature_name`, `description`, `default_enabled`, `scope`, `updated_by`)
VALUES
('page_schedule', '学生课表', '学生端课表查看功能（从教务系统同步课表数据）', 0, 'global', 'system');
```

**说明**：
- 运维端功能开关：控制学校数据配置和浏览页面的显示
- 学生端功能开关：控制学生课表页面的显示（`/student/schedule`）
- 所有开关默认关闭，需要在运维端手动启用

---

## 部署步骤

### 1. 后端部署
```bash
cd backend
mvn clean package -DskipTests
# 重启后端服务
```

### 2. 前端部署
```bash
cd frontend
npm run build
# 复制 dist/ 到 Nginx 静态目录
```

### 3. 数据库迁移
Flyway 会自动执行 V18 迁移脚本，添加功能开关配置。

### 4. 启用功能开关
登录运维端 → 功能开关 → 启用以下开关：
- `ops.school_data_config`（学校数据配置）
- `ops.school_data_browser`（学校数据浏览）
- `page_schedule`（学生课表）

**启用顺序建议**：
1. 先启用 `ops.school_data_config`，配置学校数据源（API 或 ETL）
2. 测试连接成功后，启用 `ops.school_data_browser`（可选，用于管理员查看数据）
3. 最后启用 `page_schedule`，学生端课表功能即可使用

---

## 学生端课表功能说明

### 功能概述
- **页面路径**：`/student/schedule`
- **功能开关**：`page_schedule`（默认关闭）
- **权限要求**：`USER` 或 `STUDENT` 角色
- **数据来源**：学校教务系统 API（通过后端 `/api/student/schedule` 接口）

### 使用流程
1. 学生登录后，点击底部导航栏"课表"（需要功能开关启用）
2. 选择学年和学期（默认为当前学年学期）
3. 点击"刷新"按钮从教务系统同步课表数据
4. 课表以网格形式展示（周一至周日 × 1-12 节）
5. 点击课程单元格查看详细信息（教师、教室、周次等）

### API 端点
- **GET** `/api/student/schedule?academicYear=2025-2026&semester=1`
  - 查询本地缓存的课表数据
- **GET** `/api/student/schedule?academicYear=2025-2026&semester=1&refresh=true`
  - 从学校 API 重新拉取并同步课表数据

### 数据流
```
学生端 → StudentScheduleController → SchoolDataSyncService
                                    ↓
                            SchoolDataApiClient (调用学校API)
                                    ↓
                            student_schedules 表（本地缓存）
```

### 前置条件
1. 运维端已配置学校数据源（`source=api`，填写 `api_base_url` 和 `api_token`）
2. 学校 API 的 `/api/xskb` 端点可访问
3. 学生的 `username` 字段为学号（用于查询课表）

---

## 验证步骤

### 1. 配置保存测试
1. 登录运维端
2. 进入"学校数据"配置页面
3. 选择 `source=api`，填写 API Base URL
4. 点击"保存配置"
5. **预期**：保存成功，不再报 `jdbcUrl is required` 错误

### 2. 连接测试
1. 在配置页面点击"测试连接"
2. **预期**：
   - 如果 API 可访问：显示"连接成功 (api)"
   - 如果 API 不可访问：显示"连接失败"（不再是 404）

### 3. 功能开关测试
1. 登录运维端
2. 进入"功能开关"页面
3. 关闭 `ops.school_data_config` 和 `ops.school_data_browser`
4. 刷新页面
5. **预期**：底部导航栏中"数据"和"浏览"按钮消失

### 4. 权限测试
1. 以学生角色登录
2. 尝试访问 `/ops/school-data`
3. **预期**：被重定向到登录页，提示"权限不足"

### 5. 学生端课表测试
1. 运维端启用 `page_schedule` 功能开关
2. 以学生角色登录
3. 点击底部导航栏"课表"按钮
4. 选择学年学期，点击"刷新"
5. **预期**：
   - 如果学校 API 可访问且有数据：显示课表网格
   - 如果学校 API 不可访问：显示错误提示"同步课表失败"
   - 如果没有课表数据：显示"暂无课表数据"

---

## 注意事项

1. **ETL 模式配置**：如果需要使用 ETL 模式，必须填写完整的 `etl_jdbc_url`、`etl_username`、`etl_password`
2. **API 端点**：确认学校 API 的实际端点路径，可能需要调整 `SchoolDataProperties.ApiProperties.endpoints`
3. **功能开关默认关闭**：新部署后需要手动启用功能开关才能看到学校数据菜单
4. **Token 刷新**：角色切换后建议退出重新登录，避免 token 缓存问题
5. **学生课表前置条件**：
   - 学生的 `username` 必须是学号（用于调用学校 API）
   - 学校 API 的 `/api/xskb` 端点必须可访问
   - 需要先在运维端配置学校数据源并测试连接成功
6. **功能开关启用顺序**：建议先启用运维端配置功能 → 配置数据源 → 测试连接 → 再启用学生端课表功能

---

## 相关文件

### 后端
- `backend/src/main/java/com/english/service/SchoolDataConfigRefreshService.java`
- `backend/src/main/java/com/english/service/impl/SchoolDataEtlClientImpl.java`
- `backend/src/main/java/com/english/service/impl/SchoolDataApiClientImpl.java`
- `backend/src/main/resources/db/migration/V18__school_data_feature_toggle.sql`

### 前端
- `frontend/src/router/index.js`
- `frontend/src/views/shared/MobileLayout.vue`

---

## 后续优化建议

1. **API 健康检查**：与学校 IT 部门确认是否有专用的健康检查端点
2. **错误提示优化**：前端显示更详细的错误信息（如"API 连接超时"、"认证失败"等）
3. **配置验证**：保存配置前在前端进行格式校验（如 JDBC URL 格式、API URL 格式）
4. **日志监控**：添加学校数据同步的监控告警，及时发现连接问题
