# LangHack 全栈零停机热更新 — 统一脚本自动识别架构设计文档

> 版本: v1.0 | 日期: 2026-05-06 | 状态: 设计中
> 核心: **一个脚本 (`scripts/hot-deploy.sh`) 自动识别变更类型，路由到三层热更新策略**

---

## 目录

1. [设计目标](#一设计目标)
2. [统一脚本架构](#二统一脚本架构)
3. [三层热更新策略](#三三层热更新策略)
4. [安全回滚机制](#四安全回滚机制)
5. [实施路线图](#五实施路线图)

---

## 一、设计目标

### 1.1 核心目标

**一条命令完成所有热更新**: `./scripts/hot-deploy.sh`

- 自动通过 `git diff` 识别变更类型
- 自动路由到对应热更新策略（蓝绿部署 / Shadow Table / 滚动升级）
- 自动编排执行顺序（混合变更时：DB 先行 → 蓝绿构建 → 流量切换）
- 自动验证健康状态，异常时自动回滚

### 1.2 覆盖的变更类型

```
scripts/hot-deploy.sh 自动检测:
  git diff HEAD~1 --name-only
    ├── frontend/** 变更              → 蓝绿部署 (frontend_blue)
    ├── backend/** 变更               → 蓝绿部署 (backend_blue)
    ├── docker-compose.yml / .env     → 蓝绿部署 (全量重建)
    ├── Flyway Create Table           → 直接执行 Flyway（不需要热更新）
    ├── Flyway Alter / Add Index      → Shadow Table 策略
    ├── MySQL 镜像版本变更            → 滚动升级 (从库先升)
    └── 混合变更                      → 按序编排: DB → 蓝绿构建 → 切换
```

---

## 二、统一脚本架构

### 2.1 单一入口流程

```
./scripts/hot-deploy.sh
│
├── 阶段0: 变更检测 & 自动分类
│   ├── git diff HEAD~1 --name-only
│   ├── 匹配规则表 → 分类变更
│   └── 无变更 → 退出 (无需更新)
│
├── 阶段1: 数据库变更 (如有)
│   ├── Flyway Create Table → 直接执行
│   └── Flyway Alter → Shadow Table 引擎
│       ├── 建影子表 → 开双写 → 执行DDL → 补齐 → RENAME → 清理
│       └── 回滚: RENAME 切回旧表
│
├── 阶段2: 蓝绿部署 (如有前端/后端/配置变更)
│   ├── docker compose -f docker-compose.blue.yml build
│   ├── docker compose -f docker-compose.blue.yml up -d
│   ├── 健康检查 + 冒烟测试
│   ├── Nginx upstream reload (毫秒级切换)
│   ├── 自动回滚监控 (5分钟内错误率>1% → 自动切回)
│   └── 清理绿环境 (保留24h)
│
├── 阶段3: 基础设施变更 (如有)
│   ├── MySQL版本升级 → 从库先升 → 切主 → 原主再升
│   ├── Redis配置变更 → 滚动重启
│   └── Nginx配置变更 → nginx -s reload
│
└── 阶段4: 回滚监督
    └── 切换后监控 (错误率/延迟/健康检查) → 自动回滚

用法:
  ./scripts/hot-deploy.sh              # 自动检测并执行
  ./scripts/hot-deploy.sh --dry-run    # 仅显示检测结果，不执行
  ./scripts/hot-deploy.sh --rollback   # 紧急回滚到上一版本
```

### 2.2 变更识别规则表（核心逻辑）

```bash
# hot-deploy.sh 核心检测逻辑

detect_and_classify() {
    local changes=$(git diff HEAD~1 --name-only)

    declare -A detected=()

    for file in $changes; do
        case "$file" in
            frontend/*)
                detected[frontend]=true ;;
            backend/src/main/java/*|backend/src/main/resources/*)
                detected[backend]=true ;;
            backend/src/main/resources/db/migration/V*__create_*)
                detected[flyway_create]=true ;;
            backend/src/main/resources/db/migration/V*__alter_*|\
            backend/src/main/resources/db/migration/V*__add_index*)
                detected[flyway_alter]=true ;;
            docker-compose.yml|docker/*)
                detected[docker]=true ;;
            docker/.env)
                detected[env]=true ;;
        esac
    done

    # 路由决策：数据库变更先执行
    if [[ -n "${detected[flyway_alter]}" ]]; then
        execute_shadow_table
    elif [[ -n "${detected[flyway_create]}" ]]; then
        execute_flyway
    fi

    if [[ -n "${detected[frontend]}" || -n "${detected[backend]}" ||
          -n "${detected[docker]}" || -n "${detected[env]}" ]]; then
        execute_blue_green
    fi
}
```

---

## 三、三层热更新策略

### 3.1 策略一：蓝绿部署（应用层）

覆盖: 前端代码、后端代码、application.yml、.env 配置

```
① 构建蓝环境 (5-15分钟，用户无感知)
   docker compose -f docker-compose.yml -f docker-compose.blue.yml build

② 启动蓝环境 (~30秒，用户无感知)
   docker compose -f docker-compose.blue.yml up -d

③ 健康检查 + 冒烟测试 (~10秒)
   curl /api/health (蓝环境)
   curl /api/public/articles?page=1 (冒烟)

④ 流量切换 (<1ms)
   Nginx upstream 指向蓝环境 → nginx -s reload

⑤ 清理绿环境 (~5秒)
   docker stop 旧容器 (保留24h便于回滚)
```

**Docker 环境关键考虑**：

| 因素 | 应对 |
|------|------|
| deploy.sh 在外部执行 | 蓝绿脚本独立，通过参数分步执行 |
| 端口 80/443 只能绑定一个 Nginx | 蓝绿共用同一 Nginx，仅 backend 端口区分 (8080/8081) |
| CORS 配置 | 验证阶段临时加入 `localhost:8081` |
| Redis 缓存冲突 | 切换后清缓存或 key 加版本前缀 |
| 自动回滚 | 切换后 5 分钟内错误率 >1% 自动切回 |

**回滚**: 绿容器从未删除，`nginx -s reload` 切回即可，<1ms

### 3.2 策略二：Shadow Table（数据库层）

仅针对 DDL 变更 (ALTER TABLE / ADD INDEX / MODIFY COLUMN)。新增表直接 Flyway 执行。

```
① CREATE TABLE texts_shadow LIKE texts;          -- 秒级，无锁
② 开启双写 (MyBatis拦截器同时写原表+影子表)
③ ALTER TABLE texts_shadow ADD INDEX ...;       -- 分钟级，用户无感知
④ 回调补齐 + RENAME TABLE 原子切换              -- 毫秒级锁
⑤ DROP TABLE texts_old;                          -- 低峰期清理
```

**关键实现**: `ShadowTableInterceptor.java` (MyBatis 插件)
- 拦截 `Executor.update` 方法
- 按 `system_config` 中 `shadow.{table}.active` 开关控制双写
- 双写失败不影响原表写入（catch 异常不抛出）

**Docker 特定考虑**：

| 因素 | 应对 |
|------|------|
| 从库 binlog 同步 | 等待 `Seconds_Behind_Master = 0` 后才 DROP 旧表 |
| 主从复制延迟 | 切换后在从库验证 `SHOW CREATE TABLE` 一致性 |
| 容器间 MySQL 连接 | 使用容器名 `mysql-master` 而非 localhost |

### 3.3 策略三：滚动升级（基础设施层）

| 变更类型 | 策略 | 停机窗口 |
|---------|------|---------|
| MySQL 版本升级 | 从库先升 → 切主 → 原主再升 | 0s (连接池自动降级) |
| Redis 配置变更 | `docker compose up -d redis` (自动重启) | <5s |
| Nginx 配置变更 | `nginx -s reload` | <1ms |

---

## 四、安全回滚机制

### 4.1 分层独立回滚

| 层级 | 回滚方式 | 回滚时间 | 前提条件 |
|------|---------|---------|---------|
| 蓝绿部署 | Nginx 切回绿环境 | <1ms | 绿容器未删除 (保留 24h) |
| Shadow Table | RENAME 切回旧表 | <1ms | 旧表保留 24h |
| 滚动升级 | 保留旧从库 | <1min | 升级前做快照 |

### 4.2 自动回滚触发

```
蓝绿部署:
  - 切换后 5 分钟内错误率 > 1%
  - P95 延迟 > 2x 基准
  - 健康检查连续 3 次失败

Shadow Table:
  - RENAME TABLE 执行失败 (MySQL 自动回滚)
  - 从库同步报错 (Seconds_Behind_Master 异常增长)

滚动升级:
  - 从库升级后复制失败
  - 从库健康检查不通过
```

---

## 五、实施路线图

| 阶段 | 内容 | 工作量 | 依赖 |
|------|------|--------|------|
| **Phase 1** | `hot-deploy.sh` 变更检测 + 蓝绿部署引擎 | 2-3 天 | 无 |
| **Phase 2** | Shadow Table 拦截器 + Flyway 分离约定 | 3-5 天 | 无 |
| **Phase 3** | 脚本集成（统一入口 + 编排） | 1-2 天 | Phase 1, 2 |
| **Phase 4** | 运维面板（调用 hot-deploy.sh + 实时状态） | 2-3 天 | Phase 3 |
| **Phase 5** | 自动化回归测试 + 全栈集成验证 | 2-3 天 | Phase 1-4 |

**MVP 优先**: Phase 1 即可覆盖 **80% 变更场景**（前后端代码 + 配置变更）。

---

## 附录：关键文件索引

| 文件 | 用途 | 状态 |
|------|------|------|
| `scripts/hot-deploy.sh` | **唯一入口**，自动识别 + 执行所有热更新 | 待实现 |
| `docker-compose.blue.yml` | 蓝环境容器定义 | 待实现 |
| `backend/.../interceptor/ShadowTableInterceptor.java` | MyBatis 双写插件 | 待实现 |
| `backend/.../service/ShadowConfigService.java` | 影子表配置服务 | 待实现 |
| `nginx/nginx.conf` | Nginx upstream 配置（蓝绿切换） | 需微调 |

---

> **文档版本**: v1.0 | **下次更新时机**: Phase 1 实施后
