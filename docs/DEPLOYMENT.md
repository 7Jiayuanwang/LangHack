# LangHack 部署指南

本文档覆盖从零到生产的完整部署流程，包括 Docker 编排、MySQL 主从复制、Redis 调优及日常运维操作。

---

## 1. 环境要求

### 硬件最低配置

| 资源 | 最低 | 推荐 |
|------|------|------|
| CPU | 2 核 | 4 核 |
| 内存 | 4 GB | 8 GB |
| 磁盘 | 20 GB | 50 GB SSD |

### 软件依赖

| 软件 | 版本要求 | 说明 |
|------|----------|------|
| Docker | 20.10+ | 容器运行时 |
| Docker Compose | V2 (compose v2.x) | 多容器编排 |
| curl | 任意 | 健康检查脚本依赖 |
| bash | 4.0+ | 部署脚本运行环境 |

验证安装：

```bash
docker --version          # Docker version 20.x.x
docker compose version    # Docker Compose version v2.x.x
```

### 端口清单

| 端口 | 服务 | 说明 |
|------|------|------|
| 80 | Nginx | HTTP（可重定向至 HTTPS） |
| 443 | Nginx | HTTPS（如配置 SSL） |
| 8080 | Spring Boot | 应用 API，Actuator 健康检查 |
| 3306 | MySQL 主库 | 主库读写端口 |
| 3307 | MySQL 从库 | 从库只读端口 |
| 6379 | Redis | 缓存服务 |

> 生产环境建议通过防火墙/安全组仅对外暴露 80/443，其余端口仅内网可达。

---

## 2. 快速启动

以下 5 步完成首次 Docker 部署：

```bash
# 1. 克隆仓库
git clone <repo-url> LangHack
cd LangHack

# 2. 复制并编辑环境变量文件
cp docker/.env.example docker/.env
# 用编辑器打开并填写所有必填变量（见第 4 节）
nano docker/.env

# 3. 执行交互式部署脚本
bash scripts/deploy.sh

# 4. 校验容器与服务状态
docker compose ps
curl http://localhost:8080/actuator/health

# 5. 访问应用
#    应用首页:  http://<服务器IP>
#    健康检查:  http://<服务器IP>:8080/actuator/health
```

---

## 2.5. 学校数据集成配置

`scripts/deploy.sh` 向导在 SSO 配置之后、备份配置之前会交互式询问学校数据集成设置。

### 向导交互流程

```
启用学校数据集成？ [y/N]
  └─ y → 数据源类型: 1) API 接口 (推荐)  2) ETL 数据库
          ├─ API 模式：
          │    • App ID     （学校数据平台分配）
          │    • App Secret （学校数据平台分配，用于签名认证）
          │    9 个数据接口 URL 已内置，无需配置
          └─ ETL 模式：
               • JDBC URL (默认: jdbc:mysql://school-db.imau.edu.cn:3306/school_db)
               • 数据库用户名 / 密码
               7 个查询 SQL 已内置
          启用定时同步？ [Y/n]（全量 02:00 / 增量每小时 / 校验 04:00）
          启用功能开关？ [Y/n]（运维端数据浏览/配置 + 学生端课表入口）
```

### API 签名认证方式（官方规范）

```
每次请求生成：
    timestamp = System.currentTimeMillis()
    sign      = Base64( MD5( appId + appSecret + timestamp ) )

请求头：
    appId:     <App ID>
    timestamp: <毫秒时间戳>
    sign:      <Base64 编码签名>
```

### 手动配置方式

除向导外，也可直接编辑 `docker/.env`：

```ini
SCHOOL_DATA_SOURCE=api           # 或 etl
SCHOOL_API_APP_ID=<app-id>       # 学校数据平台分配
SCHOOL_API_APP_SECRET=<secret>   # 学校数据平台分配
SCHOOL_SYNC_ENABLED=true         # 启用定时同步
```

配置变更后重启后端：`docker compose restart backend`

### 运维面板配置

部署完成后，也可在运维面板管理（无需重启）：
- **配置页路径**：`/ops/school-data`
- **数据浏览路径**：`/ops/school-data-browser`
- **权限**：`OPS` 或 `ADMIN` 角色
- **支持**：数据源类型切换、appId/appSecret 修改、定时同步开关、连接测试

### App ID/Secret 获取方式

由学校数据平台（cdsp.imau.edu.cn）管理员分配，具体申请流程咨询学校信息化中心。

### 更新前端 UI（学校数据 App ID/App Secret）

**症状**：`/ops/school-data` 仍显示 "API Token" 字段而非 "App ID" / "App Secret"

**根因**：frontend docker image 未重建，生产使用的是旧镜像

**修复**：
```bash
cd /path/to/LangHack
git pull
docker compose build frontend
docker compose up -d frontend
```
重建后浏览器 `Ctrl+F5` 清缓存，即可看到新的 App ID / App Secret 输入框。

### 配置完成后的强制验证（关键）

保存 App ID / App Secret 后，**必须** 通过以下任一方式验证端到端链路：

1. **运维面板** — 在 `/ops/school-data` 点击“测试连接”，应显示连接成功
2. **后端 API** — `curl -H "Authorization: Bearer $ADMIN_JWT" -X POST http://localhost:8080/api/ops/school-data/test-connection`
   - 预期返回：`{"code":200,"data":{"success":true,"source":"api"}}`
3. **同步验证** — 在运维面板触发同步后，观察数据库 `SELECT COUNT(*) FROM administrative_class` > 0

**测试失败的排查步骤**：
1. 检查 `/ops/school-data` 顶部横幅是否消失（消失 = 配置已写入）
2. 后端日志：`docker logs langhack-backend | grep -i "school"` — 查找 "School API auth missing" 或 4xx/5xx
3. 网络：`curl -v 'https://cdsp.imau.edu.cn/cdsp/data-api/v2/cdsp_1710949911983374368?$top=1'` （从后端容器内执行）
4. App ID/Secret 正确性：向学校信息化中心确认凭证

---

## 2. 本地开发环境（非 Docker）

适用于本机安装了 JDK / Maven / Node / MySQL 的开发场景。

```bash
# 1. 创建数据库
mysql -u root -p -e "CREATE DATABASE english_learning DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci;"

# 2. 准备本地 .env
# scripts/start.sh 会从项目根目录读取 .env
cp backend/.env.example .env
nano .env

# 3. 启动前后端
bash scripts/start.sh
```

也可以手动启动：

```bash
cd backend && mvn spring-boot:run
cd frontend && npm install && npm run dev
```

> 本地开发主要使用 `backend/.env.example` 作为变量参考；Docker 部署使用 `docker/.env.example`。

---

## 2.6. 一键更新（Docker + 本地开发）

`scripts/auto-update.sh` 现支持两种运行模式：

### Docker 模式
- 适用于服务器部署、Docker 本地环境、容器化测试环境
- 能力：
  - git 拉取更新
  - 按 backend/frontend 变更范围重建镜像
  - 执行容器式增量迁移与 schema 校验
  - 健康检查
  - 自动回滚到旧版本

```bash
bash scripts/auto-update.sh
UPDATE_MODE=docker bash scripts/auto-update.sh
bash scripts/auto-update.sh -b master
```

### Local 模式
- 适用于本机直接运行 `bash scripts/start.sh` 的开发环境
- 能力：
  - git 拉取更新
  - 按变更范围重启本地 backend/frontend 进程
  - 生成 `logs/backend.pid` / `logs/frontend.pid`
  - 健康检查失败时回退代码并尝试恢复进程

```bash
UPDATE_MODE=local bash scripts/auto-update.sh
```

### 模式选择
- `UPDATE_MODE=auto`：自动识别（默认）
- `UPDATE_MODE=docker`：强制 Docker 模式
- `UPDATE_MODE=local`：强制本地模式

### 限制说明
- Local 模式不会执行 Docker 风格的维护模式切换、容器内 mysqldump、主从复制校验。
- Local 模式检测到迁移脚本变化时，只会告警提示，不会直接执行容器式迁移 SQL。

---

## 3. 配置方式（三选一）

项目支持三种配置方式，按需选择：

### 方式一：Spring Profile 配置文件（推荐非 Docker 部署）

```bash
# 1. 复制配置模板
cd backend/src/main/resources
cp application-prod.yml.example application-prod.yml

# 2. 编辑配置文件，填写实际值
nano application-prod.yml

# 3. 启动时指定 profile
java -jar app.jar --spring.profiles.active=prod
```

> `application-prod.yml` 已加入 `.gitignore`，不会被提交到版本控制。

### 方式二：环境变量文件（推荐 Docker 部署）

```bash
# Docker Compose 部署
cp docker/.env.example docker/.env
nano docker/.env
docker compose up -d

# 或直接 JAR 部署（使用 backend/.env.example 作为参考）
cp backend/.env.example backend/.env
# 加载 .env 后启动：
export $(grep -v '^#' backend/.env | xargs) && java -jar app.jar
```

### 方式三：命令行参数（临时覆盖）

```bash
java -jar app.jar \
  --app.cors.allowed-origins=https://your-domain.com \
  --spring.security.jwt.secret=your_jwt_secret \
  --spring.datasource.master.password=your_db_password
```

### 配置优先级

```
命令行参数 > application-prod.yml > 环境变量 > application.yml 默认值
```

> **关键**：无论选择哪种方式，务必配置 `CORS 允许的来源`（前端域名），否则前端会出现"拒绝访问"。

---

## 4. 环境变量说明

配置文件路径：`docker/.env`（从 `docker/.env.example` 复制而来）

### 数据库（主库）

| 变量名 | 说明 | 示例值 | 必填 |
|--------|------|--------|------|
| `DB_URL` | 主库 JDBC 连接串 | `jdbc:mysql://mysql-master:3306/english_learning?...` | 是 |
| `DB_USERNAME` | 主库用户名 | `root` | 是 |
| `DB_PASSWORD` | 主库密码 | `MyStr0ngP@ss` | 是 |

### 数据库（从库）

| 变量名 | 说明 | 示例值 | 必填 |
|--------|------|--------|------|
| `DB_SLAVE_URL` | 从库 JDBC 连接串 | `jdbc:mysql://mysql-slave:3306/english_learning?...` | 是（单机可与主库相同） |
| `DB_SLAVE_USERNAME` | 从库用户名 | `root` | 是 |
| `DB_SLAVE_PASSWORD` | 从库密码 | `MyStr0ngP@ss` | 是 |

### MySQL 容器内部

| 变量名 | 说明 | 示例值 | 必填 |
|--------|------|--------|------|
| `MYSQL_ROOT_PASSWORD` | MySQL root 密码（应与 DB_PASSWORD 一致） | `MyStr0ngP@ss` | 是 |
| `MYSQL_DATABASE` | 自动创建的数据库名 | `english_learning` | 是 |
| `MYSQL_REPLICATION_USER` | 主从复制账号 | `replicator` | 是 |
| `MYSQL_REPLICATION_PASSWORD` | 主从复制密码 | `Repl@SecretPwd` | 是 |

### Redis

| 变量名 | 说明 | 示例值 | 必填 |
|--------|------|--------|------|
| `REDIS_HOST` | Redis 主机（docker-compose 中固定为服务名） | `redis` | 是 |
| `REDIS_PORT` | Redis 端口 | `6379` | 是 |
| `REDIS_PASSWORD` | Redis 认证密码 | `Redis@SecretPwd` | 是 |

### CORS 跨域配置（⚠️ 部署必配）

> **这是生产环境部署后出现"拒绝访问"的首要原因！** 如果不配置此项，前端将无法访问后端 API。

| 变量名 | 说明 | 示例值 | 必填 |
|--------|------|--------|------|
| `APP_CORS_ALLOWED_ORIGINS` | 允许的前端域名（逗号分隔） | `https://your-frontend.com` | **是** |
| `APP_CORS_ALLOWED_ORIGIN_PATTERNS` | 通配符子域名匹配 | `https://*.example.com` | 否 |

配置示例：

```bash
# 单个域名
export APP_CORS_ALLOWED_ORIGINS=https://langhack.example.com

# 多个域名（逗号分隔）
export APP_CORS_ALLOWED_ORIGINS=https://www.example.com,https://admin.example.com

# IP + 端口（测试环境）
export APP_CORS_ALLOWED_ORIGINS=http://192.168.1.100:3000,http://192.168.1.100:5173
```

> 默认值 `http://localhost:3000,http://localhost:5173` 仅用于本地开发。部署到服务器后**必须**配置为实际的前端域名。

### 认证与 Token

| 变量名 | 说明 | 示例值 | 必填 |
|--------|------|--------|------|
| `JWT_SECRET` | JWT 签名密钥（至少 32 位随机串） | `openssl rand -base64 48` 生成 | **是** |

> Token 有效期为 30 天。用户退出登录时 Token 通过 Redis 黑名单机制立即失效。

### AI 服务

| 变量名 | 说明 | 示例值 | 必填 |
|--------|------|--------|------|
| `HIAGENT_API_KEY` | HiAgent API Key（主要 AI 提供商） | 联系管理员获取 | 至少配一个 |
| `HIAGENT_API_URL` | HiAgent API 地址 | `https://agent.imau.edu.cn:32300/api/proxy/api/v1` | 否（有默认值） |
| `HIAGENT_APP_ID` | HiAgent App ID | 联系管理员获取 | 与 API Key 配套 |
| `OPENROUTER_API_KEY` | OpenRouter API Key（备用 AI 提供商） | `sk-or-xxx` | 至少配一个 |

> HiAgent 和 OpenRouter 至少需要配置其中一个，否则 AI 问答功能不可用。

### 腾讯云服务（可选）

| 变量名 | 说明 | 示例值 | 必填 |
|--------|------|--------|------|
| `TENCENT_SECRET_ID` | 翻译服务 SecretId | `AKIDxxx` | 否 |
| `TENCENT_SECRET_KEY` | 翻译服务 SecretKey | `xxx` | 否 |
| `TENCENT_ASR_SECRET_ID` | 语音识别 SecretId | `AKIDxxx` | 否 |
| `TENCENT_ASR_SECRET_KEY` | 语音识别 SecretKey | `xxx` | 否 |

> 生成强随机 JWT 密钥：`openssl rand -base64 48`

---

## 4. MySQL 主从复制配置

**首次部署必须执行**，否则从库无法同步数据，读写分离功能不生效。

### 4.1 获取主库 Binlog 位置

```bash
# 进入主库容器
docker exec -it langhack-mysql-master mysql -uroot -p"${MYSQL_ROOT_PASSWORD}"

-- 在 MySQL 命令行执行：
SHOW MASTER STATUS;
-- 记录输出中的 File 和 Position，例如：
-- File: mysql-bin.000003
-- Position: 157
```

### 4.2 在从库执行复制配置

```bash
# 进入从库容器
docker exec -it langhack-mysql-slave mysql -uroot -p"${MYSQL_ROOT_PASSWORD}"
```

在 MySQL 命令行执行（将 `<FILE>` 和 `<POS>` 替换为上一步记录的值）：

```sql
-- MySQL 8.0.23+ 使用新语法（CHANGE REPLICATION SOURCE TO）
CHANGE REPLICATION SOURCE TO
    SOURCE_HOST='mysql-master',
    SOURCE_PORT=3306,
    SOURCE_USER='replicator',
    SOURCE_PASSWORD='<MYSQL_REPLICATION_PASSWORD>',
    SOURCE_LOG_FILE='<FILE>',
    SOURCE_LOG_POS=<POS>;

START REPLICA;

-- 验证复制状态（两项均为 Yes 则成功）
SHOW REPLICA STATUS\G
-- 关注：
--   Replica_IO_Running: Yes
--   Replica_SQL_Running: Yes
--   Seconds_Behind_Source: 0
```

> 注意：将 `<MYSQL_REPLICATION_PASSWORD>` 替换为 `docker/.env` 中 `MYSQL_REPLICATION_PASSWORD` 的实际值。

### 4.3 验证复制

```bash
# 也可通过健康检查脚本查看同步状态
bash scripts/check-health.sh
```

---

## 5. Redis 服务器调优

在 **宿主机（Linux）** 上执行以下配置，避免 Redis 启动警告并提升性能。

### 5.1 允许内存超量提交

```bash
# 临时生效
sudo sysctl vm.overcommit_memory=1

# 永久生效（重启后保留）
echo "vm.overcommit_memory = 1" | sudo tee -a /etc/sysctl.conf
sudo sysctl -p
```

### 5.2 禁用透明大页（THP）

透明大页会导致 Redis 延迟抖动：

```bash
# 临时禁用
sudo sh -c 'echo never > /sys/kernel/mm/transparent_hugepage/enabled'
sudo sh -c 'echo never > /sys/kernel/mm/transparent_hugepage/defrag'

# 永久禁用（通过 rc.local 或 systemd）
# 在 /etc/rc.local 的 exit 0 之前添加：
echo 'echo never > /sys/kernel/mm/transparent_hugepage/enabled' | sudo tee -a /etc/rc.local
echo 'echo never > /sys/kernel/mm/transparent_hugepage/defrag' | sudo tee -a /etc/rc.local
sudo chmod +x /etc/rc.local
```

验证：

```bash
cat /sys/kernel/mm/transparent_hugepage/enabled
# 期望输出：always madvise [never]
```

### 5.3 连接数限制

```bash
# 提高系统最大连接数（如果 Redis 报 "max clients reached"）
sudo sysctl -w net.core.somaxconn=65535
echo "net.core.somaxconn = 65535" | sudo tee -a /etc/sysctl.conf
```

---

## 6. Nginx 配置

项目已提供生产级 Nginx 配置文件，支持：
- 反向代理 Spring Boot API（`/api/` 前缀）
- 静态前端文件服务（Vue 3 构建产物）
- 限流（防刷 API）
- SSL/HTTPS（需自行配置证书路径）

详见：[nginx/README.md](../nginx/README.md) 和 [nginx/nginx.conf](../nginx/nginx.conf)

快速引用配置文件路径：

```
nginx/
├── nginx.conf        # 主配置（含限流、SSL、静态服务、反代规则）
└── README.md         # Nginx 部署说明
```

---

## 7. 常用运维命令

### 7.1 查看日志

```bash
# 查看所有服务日志（实时滚动）
cd docker && docker compose logs -f

# 查看指定服务日志
docker compose logs -f app          # Spring Boot 应用
docker compose logs -f mysql-master # MySQL 主库
docker compose logs -f redis        # Redis

# 查看最近 100 行
docker compose logs --tail=100 app
```

### 7.2 重启单个服务

```bash
cd docker

# 重启应用（不影响数据库和 Redis）
docker compose restart app

# 重建并重启应用（拉取最新代码后）
docker compose up -d --build app

# 重启所有服务
docker compose restart
```

### 7.3 数据库备份

```bash
# 备份主库（全量）
docker exec langhack-mysql-master \
    mysqldump -uroot -p"${MYSQL_ROOT_PASSWORD}" \
    --single-transaction --routines --triggers \
    english_learning > backup_$(date +%Y%m%d_%H%M%S).sql

# 定时备份（添加到 crontab）
# 每天凌晨 2:00 备份，保留 7 天
# 0 2 * * * docker exec langhack-mysql-master mysqldump -uroot -p"<PWD>" --single-transaction english_learning > /backup/langhack_$(date +\%Y\%m\%d).sql && find /backup -name "langhack_*.sql" -mtime +7 -delete
```

### 7.4 更新应用

```bash
# 1. 拉取最新代码
git pull origin main

# 2. 重建并热更新应用容器（零停机时间取决于健康检查设置）
cd docker && docker compose up -d --build app

# 3. 验证新版本正常运行
bash ../scripts/check-health.sh
```

### 7.5 查看容器状态

```bash
cd docker && docker compose ps

# 查看资源占用
docker stats langhack-app langhack-mysql-master langhack-redis
```

### 7.6 停止/清理

```bash
cd docker

# 停止所有服务（保留数据卷）
docker compose down

# 停止并删除数据卷（危险：会清空所有数据！）
docker compose down -v
```

---

## 8. 监控指标

### 8.1 Spring Boot Actuator 端点

应用启动后，以下端点可用（无需认证）：

| 端点 | 地址 | 说明 |
|------|------|------|
| 健康状态 | `GET /actuator/health` | 整体健康状态，`{"status":"UP"}` 表示正常 |
| 应用信息 | `GET /actuator/info` | 版本、构建信息 |

示例响应（健康检查）：

```json
{
  "status": "UP"
}
```

HikariCP 连接池指标（需在 `application.yml` 中额外暴露 `metrics` 端点）：

```
GET /actuator/metrics/hikaricp.connections.active
GET /actuator/metrics/hikaricp.connections.idle
GET /actuator/metrics/hikaricp.connections.pending
```

### 8.2 常见问题排查

**应用无法启动 / 健康检查一直 starting**

```bash
# 查看应用启动日志
docker compose logs app | tail -50

# 常见原因：
# 1. 数据库连接失败 → 检查 DB_URL / DB_PASSWORD 是否正确
# 2. Redis 连接失败 → 检查 REDIS_PASSWORD 是否与容器配置一致
# 3. JWT_SECRET 未设置 → 检查 .env 文件
```

**MySQL 主从同步延迟过高**

```bash
# 查看从库状态
docker exec langhack-mysql-slave mysql -uroot -p"${MYSQL_ROOT_PASSWORD}" \
    -e "SHOW REPLICA STATUS\G" | grep Seconds_Behind

# 如果 Seconds_Behind_Source 持续增大，检查从库磁盘 I/O 和网络
docker stats langhack-mysql-slave
```

**Redis 内存不足**

```bash
# 查看 Redis 内存使用
docker exec langhack-redis redis-cli -a "${REDIS_PASSWORD}" info memory | grep used_memory_human

# 当前配置最大内存为 512MB（docker-compose.yml 中 --maxmemory 512mb）
# 如需调整，修改 docker/docker-compose.yml 中 redis.command 的 --maxmemory 参数
```

**前端 API 请求 502 / 无法连接后端**

```bash
# 检查应用是否正在运行
docker compose ps app

# 检查端口映射
docker port langhack-app

# 直接测试健康端点
curl -v http://localhost:8080/actuator/health
```

**前端出现"拒绝访问"/ CORS 错误** ⚠️

这是部署后最常见的问题。浏览器 Console 会显示类似以下错误：

```
Access to XMLHttpRequest at 'http://backend:8080/api/...' from origin 'https://your-domain.com'
has been blocked by CORS policy
```

解决方法：

```bash
# 1. 确认前端实际的访问域名
# 打开浏览器 F12 → Console，查看报错中的 "from origin" 值

# 2. 设置 CORS 白名单
export APP_CORS_ALLOWED_ORIGINS=https://your-frontend-domain.com

# 3. 重启应用
docker compose restart app

# 4. 验证配置是否生效（查看启动日志）
docker compose logs app | grep "CORS allowed origins"
# 应看到: CORS allowed origins: [https://your-frontend-domain.com]
```

> 应用启动时会自动检查 CORS 配置。如果仅配置了 localhost，启动日志会打印 WARN 提示。

**Token 相关问题排查**

```bash
# Token 有效期: 30 天
# 退出登录: Token 通过 Redis 黑名单立即失效

# 1. 如果用户退出后仍能访问
#    → 检查 Redis 是否正常运行
docker exec langhack-redis redis-cli -a "${REDIS_PASSWORD}" ping
# 期望输出: PONG

# 2. 查看黑名单 key 数量
docker exec langhack-redis redis-cli -a "${REDIS_PASSWORD}" \
    keys "token:blacklist:*" | wc -l

# 3. 如果需要强制使所有已签发 Token 失效
#    → 修改 JWT_SECRET 环境变量并重启应用
#    → 所有旧 Token 都会因签名不匹配而失效
```

---

> 最后更新：2026-03-21 | 版本：v1.1
