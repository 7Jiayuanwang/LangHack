# 🎓 LangHack — 英语学习平台

基于 Spring Boot + Vue 3 的现代化英语学习管理系统，支持管理员 / 教师 / 学生多角色，课程管理、任务驱动学习、交互式阅读、AI 助教、SM-2 间隔复习等功能。

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

> **前置要求**：服务器已安装 [Docker](https://docs.docker.com/get-docker/) 和 [Docker Compose](https://docs.docker.com/compose/install/)。

```bash
# 1. 克隆项目
git clone <repository-url> && cd LangHack

# 2. 创建环境变量文件并填入真实密码和 API Key
cp .env.example .env
vim .env   # ⚠️ 务必修改 DB_PASSWORD / JWT_SECRET / OPENROUTER_API_KEY 等

# 3. 一键部署
chmod +x deploy.sh && ./deploy.sh
```

部署完成后访问 `http://<服务器IP>` 即可使用（端口 80, 由 Nginx 处理反向代理）。

> 💡 **部署提示**：
> - 部署脚本支持 MySQL 主从复制架构，自动配置读写分离
> - 首次部署会自动初始化数据库并导入 ECDICT 词典数据
> - 遇到问题请参考 [`docs/DEPLOYMENT-LESSONS-LEARNED.md`](docs/DEPLOYMENT-LESSONS-LEARNED.md) 排查

### 方式二：本地开发环境

```bash
# 环境要求: JDK 17+, Node.js 18+, MySQL 8.0+, Maven 3.6+

# 1. 初始化数据库
mysql -u root -p -e "CREATE DATABASE english_learning DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci;"
mysql -u root -p english_learning < database/schema/schema.sql

# 2. (可选) 配置环境变量
cp .env.example .env
vim .env   # 填入你的数据库密码和 API 密钥

# 3. 一键启动后端 + 前端 (会自动加载 .env)
./scripts/start.sh

# 或者手动分别启动:
# source .env 无法直接在 shell 中使用，手动启动时请先 export:
# export $(grep -v '^#' .env | xargs)
# cd backend && mvn spring-boot:run    # 启动后端 (端口 8080)
# cd frontend && npm install && npm run dev  # 启动前端 (端口 3000)
```

> 💡 `scripts/check-env.sh` 可用于检查 JDK、Node、Maven、MySQL 是否就绪。

---

## 🔐 环境变量说明

项目通过环境变量管理所有敏感配置。**切勿将密码和密钥提交到 Git 仓库。**

| 变量名 | 说明 | 默认值 |
|---|---|---|
| `DB_URL` | MySQL JDBC 连接串 | `jdbc:mysql://localhost:3306/english_learning?...` |
| `DB_USERNAME` | 数据库用户名 | `root` |
| `DB_PASSWORD` | 数据库密码 | *(必须修改)* |
| `JWT_SECRET` | JWT 签名密钥 | *(必须修改)* |
| `OPENROUTER_API_KEY` | OpenRouter AI API 密钥 | *(必须修改)* |
| `TENCENT_SECRET_ID` | 腾讯机器翻译 SecretId | — |
| `TENCENT_SECRET_KEY` | 腾讯机器翻译 SecretKey | — |
| `VITE_API_BASE_URL` | 前端 API 路径 | `/api` |
| `VITE_TUTOR_API_BASE_URL` | AI 助教 API 完整地址 | `http://101.42.92.220/tutor` |

- **后端**：`application.yml` 使用 `${VAR:default}` 语法读取环境变量，本地开发时可不设置。
- **前端**：Vite 通过 `frontend/.env.development` / `.env.production` 加载 `VITE_*` 变量。
- **模板文件**：`.env.example` 包含所有后端变量的模板，复制为 `.env` 即可被 `start.sh` 和 Docker Compose 使用。

---

## 📁 项目结构

```
LangHack/
├── backend/              # Spring Boot 后端
│   ├── Dockerfile        # 后端 Docker 镜像 (多阶段构建)
│   └── src/main/resources/
│       └── application.yml
├── frontend/             # Vue 3 前端
│   ├── Dockerfile        # 前端 Docker 镜像 (Node 构建 + Nginx 托管)
│   ├── nginx.conf        # Nginx 反向代理配置
│   ├── .env.development  # 开发环境变量
│   └── .env.production   # 生产环境变量
├── database/             # SQL 初始化脚本和种子数据
├── ECDICT-master/        # 英汉词典数据 (74 万词)
├── docs/                 # 详细文档 (API 文档、功能说明、故障排除等)
├── scripts/              # 开发工具脚本 (启动、环境检查、测试)
├── docker-compose.yml    # Docker Compose 编排文件
├── deploy.sh             # 一键部署脚本
├── .env.example           # 环境变量模板 (后端密钥等)
├── CLAUDE.md             # AI 开发助手上下文
└── TODO.md               # 开发待办事项
```

---

## 👤 默认测试账号

| 角色 | 用户名 | 密码 |
|---|---|---|
| 管理员 | `admin` | `admin123` |
| 运维 | `ops` | `ops123` |
| 教师 | `teacher` | `teacher123` |
| 学生 | `2023122066164` | `123456` |
| 普通用户 | `user` | `user123` |

---

## 🎯 核心功能

- **多角色系统**：管理员 / 教师 / 学生 / 普通用户 / 运维，各有独立入口和权限
- **课程与任务管理**：课程 → 单元 → 课文层级结构，教师可发布带截止日期的任务
- **交互式阅读**：点击任意英文单词即可查看音标、释义、同义词等，支持 Markdown 格式课文
- **SM-2 间隔复习**：基于科学记忆算法的生词本复习系统
- **AI 助教**：选中文本后可一键向 AI 提问，支持长难句解析、上下文解读
- **协作标注**：学生的划线标注对同课文的所有读者可见
- **学习数据分析**：多维度统计学习时长、词汇掌握、任务完成率
- **读写分离架构**：MySQL 主从复制 + 自动路由，提升并发性能和数据可靠性

---

## 📖 详细文档

更多技术文档位于 [`docs/`](docs/) 目录：

| 文档 | 内容 |
|---|---|
| `API接口文档.md` | REST API 端点详细说明 |
| `ASSIGNMENT_FEATURES.md` | 任务系统功能文档 |
| `TROUBLESHOOTING.md` | 常见问题与故障排除 |
| `DATABASE_SYNC_GUIDE.md` | 数据库同步与迁移指南 |
| `DEPLOYMENT.md` | 部署指南与配置说明 |
| `DEPLOYMENT-LESSONS-LEARNED.md` | 部署问题排查案例库 |
| `OPTIMIZATION_DOCUMENTATION.md` | 性能优化文档 |
| `spec.md` | 功能规格说明（已实现功能清单） |

---

## 🤝 贡献指南

### 分支管理策略

项目采用双分支工作流：

- **`master`** - 生产分支，部署到生产环境，保持稳定
- **`develop`** - 开发分支，日常开发和测试

**工作流程**：

```bash
# 1. 从 develop 创建功能分支
git checkout develop
git pull origin develop
git checkout -b feature/your-feature

# 2. 开发并提交
git add .
git commit -m "feat: 添加新功能"

# 3. 推送功能分支
git push origin feature/your-feature

# 4. 创建 Pull Request 到 develop 分支
# 5. 代码审查通过后合并到 develop
# 6. 定期将 develop 合并到 master 发布
```

**紧急修复（Hotfix）流程**：

```bash
# 1. 从 master 创建修复分支
git checkout master
git checkout -b hotfix/critical-bug

# 2. 修复并提交
git commit -m "fix: 修复紧急问题"

# 3. 合并回 master
git checkout master
git merge hotfix/critical-bug
git push origin master

# 4. 同步到 develop（重要！）
git checkout develop
git merge master
git push origin develop
```

**重要原则**：
- ✅ 所有新功能从 `develop` 分支创建
- ✅ 紧急修复从 `master` 创建，修复后**必须**同步到 `develop`
- ✅ 使用 `git merge --no-ff` 保留完整历史
- ❌ 避免使用 `cherry-pick`，除非明确需要选择性合并
- ❌ 禁止直接在 `master` 或 `develop` 上开发

### 代码规范

**后端**：遵循 Google Java Style，使用 MyBatis Plus，所有数据库变更必须通过 Flyway 迁移脚本 (`backend/src/main/resources/db/migration/`)。

**前端**：使用 Vue 3 Composition API (`<script setup>`) + Scoped SCSS，组件命名采用 PascalCase。

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

提交 PR 前请确保所有测试通过。

---

**🎓 让英语学习变得更智能、更高效、更有趣！**
