# 🎓 LangHack — 英语学习平台

基于 Spring Boot + Vue 3 的现代化英语学习管理系统，支持管理员 / 教师 / 学生 / 普通用户 / 运维多角色，涵盖课程管理、任务驱动学习、交互式阅读、AI 助教、SM-2 间隔复习、CAS 单点登录和学校数据集成等能力。

## 技术栈

| 层 | 技术 |
|---|---|
| 后端 | Java 17, Spring Boot 2.7, MyBatis Plus, MySQL 8 (主从复制), Flyway, JWT |
| 前端 | Vue 3 (Composition API), Vite, Element Plus, Pinia, Axios, SCSS |
| 词典 | ECDICT (74 万词) + WordNet |
| 部署 | Docker Compose, Nginx, Redis |

---

## 🚀 快速开始

### 方式一：Docker 一键部署（推荐）

> **前置要求**：服务器已安装 Docker 与 Docker Compose v2。

```bash
# 1. 克隆项目
git clone <repository-url> && cd LangHack

# 2. 复制部署环境变量模板
cp docker/.env.example docker/.env
vim docker/.env   # ⚠️ 务必修改数据库、JWT、Redis、AI、学校数据等真实配置

# 3. 执行部署向导
bash scripts/deploy.sh
```

部署完成后访问 `http://<服务器IP>` 即可使用；生产环境通常由 Nginx 暴露 80/443，后端 API 运行在 8080。

> 部署提示：
> - `scripts/deploy.sh` 会在向导中串行配置基础环境、SSO、学校数据、备份等参数。
> - 学校数据 API 模式已改为 `appId + appSecret + timestamp` 签名认证，不再使用 Bearer Token。
> - 遇到问题请参考 [`docs/DEPLOYMENT.md`](docs/DEPLOYMENT.md) 和 [`docs/DEPLOYMENT-LESSONS-LEARNED.md`](docs/DEPLOYMENT-LESSONS-LEARNED.md)。

### 方式二：本地开发环境

```bash
# 环境要求: JDK 17+, Node.js 18+, MySQL 8.0+, Maven 3.6+

# 1. 初始化数据库（至少创建数据库）
mysql -u root -p -e "CREATE DATABASE english_learning DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci;"

# 2. 本地环境变量（可选）
# scripts/start.sh 读取项目根目录 .env，而不是 backend/.env
cp backend/.env.example .env
vim .env

# 3. 启动前后端（推荐脚本方式）
bash scripts/start.sh

# 或手动启动：
# cd backend && mvn spring-boot:run
# cd frontend && npm install && npm run dev
```

> 本地开发优先使用 `backend/.env.example` 复制到项目根目录 `.env`，供 `scripts/start.sh` 读取；Docker 部署则使用 `docker/.env.example`。

---

## 🔐 环境变量说明

项目通过环境变量管理敏感配置，请勿将真实密码和密钥提交到仓库。

| 变量名 | 说明 |
|---|---|
| `DB_URL` / `DB_USERNAME` / `DB_PASSWORD` | 后端主库连接配置 |
| `DB_SLAVE_URL` / `DB_SLAVE_USERNAME` / `DB_SLAVE_PASSWORD` | 从库连接配置 |
| `JWT_SECRET` | JWT 签名密钥 |
| `REDIS_HOST` / `REDIS_PORT` / `REDIS_PASSWORD` | Redis 配置 |
| `HIAGENT_API_KEY` / `OPENROUTER_API_KEY` | AI 服务配置（至少配置一种） |
| `SCHOOL_DATA_SOURCE` | 学校数据接入模式：`api` 或 `etl` |
| `SCHOOL_API_APP_ID` / `SCHOOL_API_APP_SECRET` | 学校数据 API 签名认证配置 |
| `APP_BASE_URL` / `SSO_CAS_SERVER_NAME` | SSO 回调和应用基础地址 |
| `APP_CORS_ALLOWED_ORIGINS` | 生产环境前端域名白名单 |
| `VITE_API_BASE_URL` | 前端 API 基础路径 |

详细变量说明见 [`docs/DEPLOYMENT.md`](docs/DEPLOYMENT.md)。

---

## 📁 项目结构

```text
LangHack/
├── backend/              # Spring Boot 后端
│   ├── Dockerfile        # 后端 Docker 镜像（多阶段构建）
│   └── src/main/resources/
│       └── application.yml
├── frontend/             # Vue 3 前端
│   ├── Dockerfile        # 前端 Docker 镜像（Node 构建 + Nginx 托管）
│   ├── nginx.conf        # 前端 Nginx 配置
│   ├── .env.development  # 开发环境变量
│   └── .env.production   # 生产环境变量
├── docker/               # Docker 环境变量模板、MySQL/Nginx 构建与初始化文件
├── docs/                 # 项目文档
├── scripts/              # 部署和数据处理脚本
├── book1/                # 导入用阅读材料与词汇数据
├── ECDICT-master/        # 英汉词典数据
├── docker-compose.yml    # Docker Compose 编排文件
├── CLAUDE.md             # AI 开发助手上下文
└── README.md             # 项目总览
```

---

## 👤 默认测试账号

| 角色 | 用户名 | 密码 |
|---|---|---|
| 管理员 | `admin` | `admin123` |
| 运维 | `ops` | `admin123` |
| 教师 | `teacher` | `admin123` |
| 学生（演示） | `student` | `admin123` |
| 学生（真实） | `2023122066164` | `123456` |

---

## 🎯 核心功能

- **多角色系统**：管理员 / 教师 / 学生 / 普通用户 / 运维五类角色与差异化权限
- **课程与任务管理**：课程、单元、课文、作业与学习进度管理
- **交互式阅读**：划词释义、音标、同义词、Markdown 内容渲染
- **SM-2 间隔复习**：基于 SuperMemo-2 算法的生词本智能复习系统（详见 [`docs/SM2_ALGORITHM_GUIDE.md`](docs/SM2_ALGORITHM_GUIDE.md)）
- **AI 助教**：翻译、问答、长难句解析与上下文辅助
- **CAS 单点登录**：统一身份认证、角色映射、管理员审核与自动授权
- **学校数据集成**：支持 API / ETL 双模式、按学院链式拉取、Lazy 同步、运维配置页 `/ops/school-data` 与数据浏览页 `/ops/school-data-browser`
- **读写分离架构**：MySQL 主从复制 + 自动路由 + 故障降级
- **响应式 UI/UX**：针对移动端优先设计的底部导航布局，同时完美适配 PC 宽屏（大字号、沉浸式卡片、玻璃拟态风格、动态交互反馈）

---

## 🎨 UI/UX 优化特性

项目近期对**管理端 (Admin)** 与**运维端 (Ops)** 进行了深度美化与体验升级：

### 核心设计原则
- **移动优先**：所有管理功能均支持在手机浏览器中流畅操作，采用底部导航栏提升单手操作便利性。
- **PC 增强**：针对大屏幕进行了专项适配，自动增大字号与组件间距，解决文字显示拥挤问题。
- **现代化视觉**：引入玻璃拟态 (Glassmorphism)、渐变标题、悬浮阴影卡片与呼吸灯状态指示。
- **沉浸式反馈**：系统监控与更新日志采用深色终端风格，提升运维专业感。

### 重点优化页面
- **运维端**：系统监控、AI 服务管理、功能开关、学校数据配置、部署管理（零停机热部署）、数据浏览。
- **管理端**：管理仪表盘、课程/单元/课文管理、单词管理、作业布置与进度追踪、SSO 用户审批、数据备份。
- **交互组件**：权限拒绝页 (403)、SSO 重定向遮罩、运维提权对话框、加载动画。

---

## 📖 详细文档

更多技术文档位于 [`docs/`](docs/)：

| 文档 | 内容 |
|---|---|
| `API接口文档.md` | REST API 端点说明 |
| `DEPLOYMENT.md` | Docker/环境变量/部署运维指南 |
| `SM2_ALGORITHM_GUIDE.md` | SM-2 间隔复习算法原理与实现说明 |
| `SCHOOL_DATA_INTEGRATION.md` | 学校数据集成完整方案 |
| `SCHOOL_DATA_API_SPEC.md` | 学校数据 API 规范与字段约定 |
| `SCHOOL_DATA_FIX.md` | 学校数据修复记录 |
| `SCHOOL_DATA_API_FIX.md` | 学校数据 API 兼容性修复说明 |
| `TROUBLESHOOTING.md` | 常见问题排查 |
| `docs/sso/` | SSO 集成相关文档 |

---

## 🔄 一键更新

项目支持使用 `scripts/auto-update.sh` 进行一键更新，覆盖两种环境：

### Docker 环境
- 默认优先识别为 Docker 模式
- 执行内容包括：拉取代码、按变更范围重建 backend/frontend、执行容器式迁移、健康检查、失败自动回滚
- 常用命令：

```bash
bash scripts/auto-update.sh
bash scripts/auto-update.sh -b master
UPDATE_MODE=docker bash scripts/auto-update.sh
```

### 本地开发环境
- 当未检测到有效 Docker Compose 运行态时，可进入本地模式
- 执行内容包括：拉取代码、按变更范围重启本地 backend/frontend、写入 PID 文件、健康检查、失败时回退代码并尝试恢复进程
- 常用命令：

```bash
UPDATE_MODE=local bash scripts/auto-update.sh
```

### 模式说明
- `UPDATE_MODE=auto`：自动识别（默认）
- `UPDATE_MODE=docker`：强制 Docker 模式
- `UPDATE_MODE=local`：强制本地开发模式

> 注意：本地模式下不会执行 Docker 风格的数据库迁移、维护页切换和主从复制校验；若检测到迁移脚本变化，脚本会给出提示，请手动确认本地数据库 schema。

---

## 🤝 贡献指南

### 分支协作策略

当前仓库以 `master` 为主分支进行协作与发布，常见工作方式为从 `master` 切出功能/修复分支，完成后通过 Pull Request 合并回 `master`。

**推荐工作流程**：

```bash
# 1. 从 master 创建功能分支
git checkout master
git pull origin master
git checkout -b feature/your-feature

# 2. 开发并提交
git add .
git commit -m "feat: 添加新功能"

# 3. 推送功能分支
git push origin feature/your-feature

# 4. 创建 Pull Request 到 master 分支
# 5. 代码审查通过后合并到 master
```

**紧急修复（Hotfix）流程**：

```bash
# 1. 从 master 创建修复分支
git checkout master
git pull origin master
git checkout -b hotfix/critical-bug

# 2. 修复并提交
git commit -m "fix: 修复紧急问题"

# 3. 推送并发起 PR
git push origin hotfix/critical-bug
```

**重要原则**：
- ✅ 所有新功能和修复都从 `master` 切分支
- ✅ 优先使用 Pull Request 合并，保留清晰历史
- ✅ 使用规范提交信息，避免含糊描述
- ❌ 不建议直接在主分支上开发
- ❌ 避免无必要的 `cherry-pick`

### 代码规范

**后端**：遵循 Google Java Style，使用 MyBatis Plus，所有数据库变更必须通过 Flyway 迁移脚本 (`backend/src/main/resources/db/migration/`)。

**前端**：使用 Vue 3 Composition API (`<script setup>`) + Scoped SCSS，组件命名采用 PascalCase。

### 数据库迁移基线原则

- 当前 `backend/src/main/resources/db/migration/` 采用整合版迁移链，作为全新数据库的初始稳定版。
- 当前仓库中的正式迁移链已演进到 `V0` 到 `V16`，其中 `V15` / `V16` 为在整合基线之上继续追加的正式迁移。
- 该迁移链不兼容已经执行过旧版 `V24/V25/V26/V27/V28/V29/V30` 等历史脚本的 Flyway 数据库。
- 若已有生产数据需要切换到整合版，应先完整备份，再新建空库执行整合链，最后按业务数据迁移方案导入数据。
- 执行任何数据库迁移前必须先备份数据库。

**提交信息**：遵循 [Conventional Commits](https://www.conventionalcommits.org/)：
```
feat: 新功能
fix: Bug 修复
docs: 文档更新
refactor: 重构
test: 测试相关
chore: 构建/工具链更新
```

### 测试要求

**前端测试**：
```bash
cd frontend && npx vitest run          # 运行所有测试
cd frontend && npx vitest              # 监听模式
```

**后端测试**：
```bash
cd backend && mvn test                 # 运行单元测试
```

提交 PR 前请确保相关测试通过。

---

**🎓 让英语学习变得更智能、更高效、更有趣！**
