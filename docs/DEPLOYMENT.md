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

以下 5 步完成首次部署：

```bash
# 1. 克隆仓库
git clone <repo-url> LangHack
cd LangHack

# 2. 复制并编辑环境变量文件
cp docker/.env.example docker/.env
# 用编辑器打开并填写所有必填变量（见第 3 节）
nano docker/.env

# 3. 执行一键部署脚本
bash scripts/deploy.sh

# 4. （首次部署）配置 MySQL 主从复制（见第 4 节）
bash scripts/setup-replication.sh   # 或手动执行第 4 节命令

# 5. 访问应用
#    应用首页:  http://<服务器IP>:8080
#    健康检查:  http://<服务器IP>:8080/actuator/health
```

---

## 3. 环境变量说明

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

### 认证与 AI

| 变量名 | 说明 | 示例值 | 必填 |
|--------|------|--------|------|
| `JWT_SECRET` | JWT 签名密钥（至少 32 位随机串） | `openssl rand -base64 48` 生成 | 是 |
| `OPENROUTER_API_KEY` | OpenRouter API Key（AI 问答功能） | `sk-or-xxx` | 是 |
| `TUTOR_API_BASE_URL` | AI Tutor 服务地址 | `http://10.0.0.5:8000/api/tutor` | 是 |

### 腾讯翻译（可选）

| 变量名 | 说明 | 示例值 | 必填 |
|--------|------|--------|------|
| `TENCENT_SECRET_ID` | 腾讯云 SecretId | `AKIDxxx` | 否 |
| `TENCENT_SECRET_KEY` | 腾讯云 SecretKey | `xxx` | 否 |

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

---

> 最后更新：2026-03-04 | 版本：v1.0
