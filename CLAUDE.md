# CLAUDE.md — AI 开发助手上下文

> Claude Code 在每次会话开始时自动读取此文件。请保持此文件与代码库同步。

---

## 项目概况

**LangHack** 是一个多角色英语学习平台，支持管理员 / 教师 / 学生 / 普通用户 / 运维五种角色。

- **后端**：Spring Boot REST API（端口 8080）
- **前端**：Vue 3 单页应用（开发端口 3000，生产通过 Nginx 端口 80 服务）
- **部署**：Docker Compose（`docker-compose.yml`）

---

## 技术栈

### 后端

| 项目 | 版本 / 说明 |
|------|------------|
| Java | 17 |
| Spring Boot | 2.7.14 |
| ORM | MyBatis Plus |
| 数据库 | MySQL 8.0 |
| 迁移 | Flyway（必须使用，禁止手动 DDL） |
| 认证 | Spring Security + JWT |
| 词典 | ECDICT（74 万词）+ WordNet (JWI) |

### 前端

| 项目 | 版本 / 说明 |
|------|------------|
| 框架 | Vue 3（Composition API，`<script setup>`） |
| 构建工具 | Vite |
| UI | Element Plus |
| 状态管理 | **Pinia**（已确认，非 Vuex） |
| 路由 | Vue Router |
| HTTP | Axios，封装于 `src/api/*.js` |
| 样式 | Scoped SCSS |
| 测试 | **Vitest + @vue/test-utils**，运行：`cd frontend && npx vitest run` |

---

## 用户角色与路由

| 角色常量 | 前端入口路径 | 后端 API 前缀 |
|---------|------------|-------------|
| `ADMIN` | `/admin` | `/api/admin/**` |
| `TEACHER` | `/teacher` | `/api/teacher/**` |
| `STUDENT` | `/student` | `/api/student/**` |
| `USER` | `/student` | `/api/public/**` + `/api/student/**`（校园环境中 USER 视同学生） |
| `OPS` | `/ops` | `/api/admin/**`（可提权） |
| —（公开） | `/articles`, `/login` | `/api/public/**` |

---

## 前端关键架构

### Layout 结构（重要）

所有已登录状态下的布局均为**移动端底部导航栏**设计，不是传统 PC 侧边栏。

```
src/views/
├── shared/
│   └── MobileLayout.vue     ← 共享布局，admin / teacher / user / ops 均使用
│                              通过 `role` prop 区分（role="admin" | "teacher" | "user" | "ops"）
├── admin/
│   └── Layout.vue           ← 3 行薄包装：<MobileLayout role="admin" />
├── teacher/
│   └── Layout.vue           ← 3 行薄包装：<MobileLayout role="teacher" />
├── user/
│   └── Layout.vue           ← 3 行薄包装：<MobileLayout role="user" />
├── ops/
│   └── Layout.vue           ← 3 行薄包装：<MobileLayout role="ops" />
└── student/
    └── Layout.vue           ← 独立布局（feature toggle 过滤导航项，自定义弹窗菜单）
```

MobileLayout 的角色差异：
- `admin`：含二级"管理"下拉菜单（`moreItems`），`system` 命令，权限守卫用 `router.replace`
- `ops`：含 `ElevationDialog` 提权对话框，`elevate` 命令，权限守卫用 `router.replace`，7个导航项（含"数据"学校数据配置）
- `teacher`：权限守卫用 `router.push`，keyboard Escape 监听
- `user`：最简，无权限守卫

### Pinia Store 使用规范

```js
const store = useUserStore()
// ✅ 正确：修改 state
store.token    = 'xxx'
store.userRole = 'ADMIN'
store.userInfo = { username: 'admin' }

// ❌ 错误：不能直接赋值给 getter
// store.isLoggedIn = true     → isLoggedIn 是 getter，由 token 派生
// store.hasAdminAccess = true → 同上
```

主要 getter（只读，由 state 派生）：
- `isLoggedIn` ← 由 `token` 决定
- `hasAdminAccess` ← 由 `userRole` 决定
- `isTeacher` ← 由 `userRole` 决定
- `hasOpsAccess` ← 由 `userRole` 决定

### 重要 CSS 规则

flex 容器内的滚动元素必须同时设置三个属性，缺一不可：

```css
.content-area {
  flex: 1;
  min-height: 0;      /* ← 关键，缺少此项会导致内容撑开容器无法滚动 */
  overflow-y: auto;
}
```

### Feature Toggle（学生端）

学生端 `student/Layout.vue` 的导航项受后端功能开关控制：
- API：`GET /api/public/system/feature-toggles`（封装在 `src/api/systemConfig.js`）
- 返回值为 JSON boolean，过滤条件：`val === undefined || val === true || val === 'true'`

---

## 后端关键规范

### 分层架构

```
Controller → Service → Mapper → Database
```

- Entity：`com.english.entity`
- 统一响应：`Result<T>`（code + data + message）
- 管理员接口用 `@PreAuthorize` 注解做权限控制

### 数据库变更（强制）

```bash
# 所有 schema 变更必须创建 Flyway 脚本
backend/src/main/resources/db/migration/V{n}__{description}.sql
# 禁止手动执行 DDL 或直接修改数据库
```

### 媒体文件

图片/视频存为 BLOB（`media` 表），通过 `/api/media/{id}/download` 访问。

### SSO 集成

- 协议：CAS（内蒙古农业大学统一身份认证）
- 后端配置：`application.yml` 的 `sso.*` / `app.base-url` 属性
- 前端回调：`public/SsoCallback.vue`
- API 文档：`docs/sso/` 目录（5 份文档）
- 角色映射：CAS 属性 → 系统角色（`SsoAuthController` + `SsoUserService`）

### 学校数据集成

- **双模式**：`api`（REST API，默认）或 `etl`（JDBC 直连）
  - 配置：`SCHOOL_DATA_SOURCE=api` 或 `etl`
  - 路由：`SchoolDataApiClientDelegate` 根据配置切换实现
  
#### API 模式（默认）
- **认证方式**：`appId + appSecret + timestamp` 签名（2026-04-18 从旧 Bearer Token 迁移）
  - 签名公式：`sign = Base64(MD5(appId + appSecret + timestamp))`
  - 请求头：`appId`, `timestamp`（毫秒）, `sign`
  - 环境变量：`SCHOOL_API_APP_ID` + `SCHOOL_API_APP_SECRET`（旧 `SCHOOL_API_TOKEN` 已废弃）
  - 9 个数据源 endpoint URL 代码内置（`SchoolDataProperties.java:97-107`），无需配置
- **实现**：`SchoolDataApiClientImpl`

#### ETL 模式
- **前置条件**：学校提供数据库直连权限 + 视图/表结构
- **配置**：`SCHOOL_ETL_JDBC_URL`, `SCHOOL_ETL_USERNAME`, `SCHOOL_ETL_PASSWORD`
- **实现**：`SchoolDataEtlClientImpl`（独立 HikariCP 连接池）
- **查询配置**：`SchoolDataProperties.EtlProperties.queries`（8 个数据源，含 `teacher_courses`）
- **必需视图**：`v_administrative_class`, `v_school_course`, `v_teaching_class`, `v_course_schedule`, `v_course_selection`, `v_level_exam_scores`, `v_teaching_plans`, `v_teacher_courses`
- **必需字段**：每个视图必须包含 `updated_at` 字段（用于增量同步）
- **文档**：`docs/ETL_MODE_SETUP.md`（配置指南）, `docs/etl/create_views.sql`（视图创建脚本）, `docs/etl/field_mapping_guide.md`（字段映射）

#### 通用配置
- **部署配置入口**：`scripts/deploy.sh` 交互向导（SSO 之后、备份之前）或运维面板 → 学校数据配置
- 后端核心：`SchoolDataSyncServiceImpl` + `SchoolDataApiClientDelegate`
- 控制器：`SchoolDataQueryController` + `SchoolDataSyncController`
- 实体：`AdministrativeClass`, `SchoolCourse`, `TeachingClass`, `CourseSchedule` 等
- 功能开关：`GET /api/public/system/feature-toggles` 中的 school_data 相关项
- Flyway：V9 基础表 + V12 合并脚本（2026-04-19 将 V12.1/V13/V14/V16/V18/V19/V20 合并为单个 V12__school_data_integration.sql）

### 按学院链式拉取

- **链路**: 学院 → school_course(KKYX) → teacher_courses(kcdm) → course_selection(KCBH) → 学生/教师/绑定落库
- **核心方法**: `SchoolDataSyncServiceImpl.chainFetchStudentsByCollege(collegeName, matchMode, persist)`
- **REST 端点**: `POST /api/admin/sync/chain/students-by-college` (ADMIN/OPS)
- **学生端 Lazy**: `GET /api/student/level-exam-scores` (USER/STUDENT) — 按需拉取四六级，陈旧度阈值1天
- **Cron**: `SchoolDataSyncScheduler.executeCollegeChainRefresh` — 默认3:30，配置 `school.data.sync.college-chain.*`
- **新表**: `school_student` / `school_teacher` / `teacher_student_binding`（V13 Flyway）
- **SSO 自动授权**: `SsoUserMappingService.tryAutoGrantFromSchoolDirectory` — student > teacher > 原流程，feature toggle 控制（默认关）
- **Feature toggles**: `ops.school_data_chain_fetch` / `sso.auto_grant_student` / `sso.auto_grant_teacher` / `student.lazy_data_sync`
- **KCBH 降级**: CDSP 可能忽略 KCBH 参数，首条探测后切换客户端过滤（`probeKcbhSupport`）
- **字段映射注意**: teacher_courses 和 course_selection 映射后 kcbh 字段名为 `classId`（非 courseClassCode）

### AI 服务集成

- 多提供商路由：`AiProviderService` → HiAgent / OpenRouter / SiliconFlow
- 翻译：`TranslateController`（AI 翻译接口）
- AI 问答：`AiQaController`（文章划词 AI 问答）
- ASR：`AsrController`（腾讯云语音识别）
- 视频代理：`BilibiliProxyController`

---

## 测试

### 前端测试

```bash
cd frontend
npx vitest run                          # 运行所有测试（当前 69 文件 1199 测试全通过）
npx vitest run tests/views/layouts/    # 只运行布局测试
npx vitest run tests/components/       # 运行组件测试
```

测试文件组织：`frontend/tests/`，子目录对应 `src/` 结构。

布局测试关键模式（避免踩坑）：
- 使用 `shallowMount(MobileLayout, { ...mountOptions(), props: { role: 'admin' } })`
- Pinia getter 不能 mock，通过设置 `store.token` 和 `store.userRole` 间接控制
- 异步功能开关测试需要 `await flushPromises()`

### 后端测试

```bash
cd backend && mvn test
```

---

## 开发工作流

1. **理解需求**：查看 `TODO.md`（开发待办）、`docs/` 目录（功能文档）
2. **前端开发**：API 封装到 `src/api/`，视图按角色目录放置，共用逻辑提取到 `src/composables/` 或 `src/views/shared/`
3. **数据库变更**：创建 Flyway 脚本，不直接操作 DB
4. **测试**：前端跑 Vitest，后端跑 mvn test
5. **部署**：本地重构不需 rebuild Docker，直接 `npm run build` 后 `docker cp` 替换静态文件

### 文档索引

| 文档 | 路径 | 说明 |
|------|------|------|
| AI 模型提供商集成文档 | `docs/AI模型提供商集成文档.md` | HiAgent / OpenRouter / SiliconFlow+Qwen / 腾讯云ASR 配置、协议、独立请求样例、路由架构 |
| API 接口文档 | `docs/API接口文档.md` | 后端 REST API 接口说明 |
| 部署文档 | `docs/DEPLOYMENT.md` | Docker Compose 部署指南 |
| Docker 镜像同步工具 | `docs/DOCKER_IMAGE_SYNC.md` | 本地 registry 镜像同步、内网部署、故障排查 |
| 数据库协作规范 | `docs/DATABASE_COLLABORATION_GUIDE.md` | 数据库表登记与变更规范 |
| 故障排查 | `docs/TROUBLESHOOTING.md` | 常见问题排查 |
| SSO 集成文档 | `docs/sso/` | CAS 统一身份认证集成（5 份文档） |
| 学校数据修复记录 | `docs/SCHOOL_DATA_FIX.md` | 学校数据集成问题修复文档 |
| 学校数据 API 修复 | `docs/SCHOOL_DATA_API_FIX.md` | 学校数据 API 端点配置修复 |

---

## 端口速查

| 服务 | 开发 | 生产 |
|------|-----|------|
| 后端 API | 8080 | 8080（内网） |
| 前端 | 3000 | 80（Nginx 反代） |

---

## 默认测试账号

| 角色 | 用户名 | 密码 |
|------|--------|------|
| 管理员 | `admin` | `admin123` |
| 运维 | `ops` | `admin123` |
| 教师 | `teacher` | `admin123` |
| 学生（演示） | `student` | `admin123` |
| 学生（真实） | `2023122066164` | `123456` |

---

## 常见陷阱

| 问题 | 原因 | 解决 |
|------|------|------|
| flex 容器内内容不能滚动 | 缺少 `min-height: 0` | 加到 overflow 容器上 |
| 测试中 getter 无法 mock | Pinia getter 是只读计算属性 | 改 `store.token` / `store.userRole` |
| feature toggle 过滤行为异常 | `val === 'true'`（字符串）不匹配 boolean | 条件改为 `val === undefined \|\| val === true \|\| val === 'true'` |
| Docker 前端改动不生效 | 前端打包进镜像而非 volume | `npm run build` + `docker cp dist/ container:/usr/share/nginx/html/` |
| shallowMount 访问不到子组件属性 | 子组件被 stub 了 | 直接 mount 子组件，或 mount 时加 `props` |
