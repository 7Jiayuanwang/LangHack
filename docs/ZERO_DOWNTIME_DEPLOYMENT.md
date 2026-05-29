# LangHack 零停机热部署 — Deployer Sidecar 架构

> 版本: v2.0 | 日期: 2026-05-29 | 状态: 已实现

---

## 一、架构概览

### 1.1 核心设计

通过 **Deployer Sidecar** 模式实现 Docker 环境下的零停机热部署：后端容器通过 Docker Socket 启动一个独立的 deployer 容器来执行蓝绿部署脚本。

```
浏览器 ──SSE──> 后端容器 ──docker run -d──> deployer 容器
                  │                            │
                  └── docker logs -f ──────────┘  (流式日志)
                                               │
                                    deployer 独立运行 hot-deploy.sh:
                                    build green → health check → switch nginx
                                    → stop blue → start new blue → cleanup
```

**为什么需要 Sidecar？**
`hot-deploy.sh` 在步骤 7 执行 `docker compose stop backend`，会杀死自身所在的容器。Deployer 容器生命周期独立于后端，可以安全地停止/重启后端。

### 1.2 两阶段执行

| 阶段 | 命令 | 说明 |
|------|------|------|
| Phase 1 | `docker run -d --rm deployer` | 启动 deployer（detached，后端被杀不影响） |
| Phase 2 | `docker logs -f <container-id>` | 流式读取日志到 SSE（可中断不影响 deployer） |

---

## 二、蓝绿部署流程

`scripts/hot-deploy.sh` (308 行) 执行以下步骤:

| Step | 操作 | 说明 |
|------|------|------|
| 1 | `git pull origin <branch>` | 拉取最新代码（可 `--skip-pull` 跳过） |
| 2 | `docker compose build backend` | 构建新镜像（可 `--skip-build` 跳过） |
| 3 | 生成 `docker-compose.green.yml` | Green 栈配置（端口 8081，同网络） |
| 4 | `docker compose -f green.yml up -d` | 启动 green 后端 |
| 5 | 健康检查 green | Sidecar 模式用容器名 `backend-green:8080`；CLI 模式用 `localhost:8081`（120s 超时） |
| 6 | Nginx upstream → green | `sed` 替换 + `nginx -s reload` |
| 7 | `docker compose stop backend` | 停止旧 blue |
| 8 | `docker compose up -d backend` | 启动新 blue（新镜像） |
| 9 | Nginx upstream → blue，停止 green | 切回 + 清理 |

失败时自动回滚：停止 green、切换 nginx 回 blue、确认 blue 健康。

---

## 三、关键文件

| 文件 | 用途 |
|------|------|
| `scripts/hot-deploy.sh` | 蓝绿部署脚本（deployer 容器内执行） |
| `docker/deployer/Dockerfile` | Deployer 镜像（docker:27-cli + git/bash/curl） |
| `backend/.../UpdateServiceImpl.java` | 后端核心 — 启动 deployer + 流式日志 |
| `backend/.../UpdateController.java` | SSE 端点 `/api/ops/update/trigger` |
| `backend/.../DynamicUpdateScheduler.java` | 定时触发调度器 |
| `frontend/src/views/ops/Update.vue` | 部署管理页面（配置 + 触发 + 日志 + 历史） |
| `docker-compose.yml` | Deployer 服务定义（`profiles: [deploy]`） |

---

## 四、环境配置

### 4.1 必需环境变量

| 变量 | 说明 | 示例 |
|------|------|------|
| `PROJECT_HOST_PATH` | 项目在宿主机的绝对路径 | `/home/ubuntu/LangHack` |
| `APP_UPDATE_SUPPORTED` | 启用热部署 | `true` |

> **双 `.env` 要求**: 根 `docker-compose.yml` 读取项目根目录的 `.env`，部署向导和脚本读取 `docker/.env`。两个文件都必须包含上述变量，否则后端会报 `supported: false`。

### 4.2 可选环境变量

| 变量 | 默认值 | 说明 |
|------|--------|------|
| `APP_UPDATE_DEPLOYER_IMAGE` | `langhack-deployer:latest` | Deployer 镜像名 |
| `APP_UPDATE_DOCKER_SOCKET` | `/var/run/docker.sock` | Docker Socket 路径 |

### 4.3 前置条件检查

`UpdateServiceImpl.resolveUnsupportedReason()` 按序检查：

1. `APP_UPDATE_SUPPORTED` 必须为 `true`
2. `PROJECT_HOST_PATH` 必须非空
3. Docker Socket 文件必须存在

任一条件不满足 → `supported: false`，前端触发按钮禁用。

---

## 五、部署步骤

```bash
# 1. 构建 deployer 镜像
docker compose --profile deploy build deployer

# 2. 在两个 .env 中都添加热部署配置
#    项目根 .env（docker-compose.yml 读取）:
PROJECT_HOST_PATH=/home/ubuntu/LangHack
APP_UPDATE_SUPPORTED=true
#    docker/.env（部署向导/脚本读取）: 同上

# 3. 重启后端（使新环境变量生效）
docker compose up -d backend

# 4. 验证
curl http://localhost:8080/api/ops/update/status
# → { "supported": true, "unsupportedReason": "" }
```

---

## 六、SSE 断连恢复

部署过程中后端容器会被重启（步骤 7），SSE 连接中断。前端处理逻辑：

1. `EventSource.onerror` 触发
2. 如果当前 `running=true` → 显示"后端正在重启，部署仍在进行中..."
3. 启动 5 秒间隔 polling `GET /api/ops/update/status`
4. 新后端上线后，polling 获取最终状态（SUCCESS/FAILED）
5. 展示结果，清除 polling

### 遗留状态清理

后端启动时 `@PostConstruct cleanupStaleStatus()` 检测数据库中残留的 `RUNNING` 状态，自动标记为 `INTERRUPTED`。

---

## 七、本地开发

Deployer 容器使用项目 Docker 网络 (`langhack_langhack-network`) 而非 `--network host`，因此在 Windows/Mac Docker Desktop 上同样可用：

- 根 `.env` 和 `docker/.env` 均配置 `PROJECT_HOST_PATH` 和 `APP_UPDATE_SUPPORTED=true`
- Deployer 通过容器名 DNS 解析访问后端进行健康检查（无需 `localhost` 端口映射）
- 可使用 `dryRun=true` 模式测试 UI 流程（不实际执行部署）

---

## 八、脚本 CLI 参数

```
./scripts/hot-deploy.sh [选项]

选项:
  --branch <branch>   部署分支（默认: 当前分支）
  --skip-build        跳过镜像构建
  --skip-pull         跳过 git pull
  --remote <url>      指定 git 远程仓库地址
  --dry-run           仅打印操作，不实际执行
```
