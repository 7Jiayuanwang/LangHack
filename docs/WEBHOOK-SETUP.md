# GitHub Webhook 自动部署指南

## 架构概览

```
GitHub Push
    ├──→ GitHub Actions (CI)     ← 自动测试 + 构建验证
    └──→ GitHub Webhook (POST)   ← 即时触发部署
            │
         Nginx (IP白名单 + 限流)
            │  /webhook/github
         Python Listener (:9000, localhost only)
            │  HMAC-SHA256 签名验证
         scripts/auto-update.sh
            │  git pull → 变更检测 → docker-compose build → 健康检查
         服务更新完成
```

## 服务器部署步骤

### 1. 配置环境变量

```bash
cd /home/deploy/LangHack/deploy
cp webhook.env.example webhook.env

# 生成 Webhook Secret
python3 -c "import secrets; print(secrets.token_hex(32))"
# 将输出填入 webhook.env 的 WEBHOOK_SECRET

# 编辑配置
vim webhook.env
```

### 2. 安装 systemd 服务

```bash
# 复制服务文件
sudo cp deploy/langhack-webhook.service /etc/systemd/system/

# 根据实际路径修改服务文件中的路径（如果不同）
sudo vim /etc/systemd/system/langhack-webhook.service

# 启用并启动
sudo systemctl daemon-reload
sudo systemctl enable langhack-webhook
sudo systemctl start langhack-webhook

# 查看状态
sudo systemctl status langhack-webhook
```

### 3. 配置 Nginx + GitHub IP 白名单自动更新

生产环境 `nginx/nginx.conf` 通过 `include /etc/nginx/github-ips.conf` 加载 IP 白名单。

```bash
# 首次：复制初始 IP 白名单文件
sudo cp nginx/github-ips.conf /etc/nginx/github-ips.conf

# 首次：手动执行一次更新脚本，从 GitHub API 获取最新 IP
sudo bash scripts/update-github-ips.sh

# 安装 systemd timer（每天凌晨 3 点自动更新）
sudo cp deploy/github-ip-update.service /etc/systemd/system/
sudo cp deploy/github-ip-update.timer /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable --now github-ip-update.timer

# 确认 timer 已激活
sudo systemctl list-timers | grep github

# 测试 nginx 配置
sudo nginx -t && sudo nginx -s reload
```

**自动更新机制**：`scripts/update-github-ips.sh` 会：
1. 从 `https://api.github.com/meta` 获取最新 hooks IP 段
2. 与现有配置比较，无变化则跳过
3. 有变化时先 `nginx -t` 验证，通过后 reload
4. 验证失败自动回滚到旧配置

Docker 开发环境使用 `frontend/nginx.conf`（已配置），通过 `host.docker.internal` 代理到宿主机。

### 4. 配置 GitHub Webhook

1. 进入仓库 → **Settings** → **Webhooks** → **Add webhook**
2. 配置：
   - **Payload URL**: `https://your-domain.com/webhook/github`
   - **Content type**: `application/json`
   - **Secret**: 与 `webhook.env` 中 `WEBHOOK_SECRET` 一致
   - **Events**: Just the push event
3. 点击 **Add webhook**
4. GitHub 会自动发送 ping 事件，检查 Recent Deliveries 确认返回 200

### 5. 验证

```bash
# 健康检查
curl http://localhost:9000/webhook/health
# 期望: {"status": "ok", "branch": "master"}

# 无签名请求应被拒绝
curl -X POST http://localhost:9000/webhook/github
# 期望: {"error": "invalid signature"}

# 查看日志
sudo journalctl -u langhack-webhook -f

# 推送测试
git commit --allow-empty -m "test: webhook trigger"
git push
# 查看服务器日志确认部署触发
```

## GitHub Actions CI

CI 流水线（`.github/workflows/ci.yml`）在以下情况自动运行：

- **Push** 到 `master` 或 `develop` 分支
- **PR** 目标为 `master` 分支

### Jobs

| Job | 内容 | 并行 |
|-----|------|------|
| `backend-test` | JDK 17 + MySQL 8.0 + `mvn test` | 是 |
| `frontend-test` | Node 18 + `vitest run` + ESLint | 是 |
| `docker-build` | 构建 Docker 镜像验证 | 仅 main，依赖前两个 |

## 安全模型

| 层 | 措施 |
|---|------|
| **网络层** (Nginx) | GitHub IP 白名单（每日自动更新）+ 限流 (1r/s burst=3) + listener 仅绑定 localhost |
| **应用层** (Python) | HMAC-SHA256 签名验证 + 10MB payload 限制 + 事件/分支过滤 |
| **执行层** (Bash) | 锁文件防并发 + `--ff-only` 防冲突覆盖 + 健康检查 |

## 故障排除

### Webhook 返回 403
- 检查 `WEBHOOK_SECRET` 是否与 GitHub 设置一致
- 确认 `X-Hub-Signature-256` header 正常传递
- GitHub 可能更新了 IP 段 → 手动执行 `sudo bash scripts/update-github-ips.sh`
- 确认 `/etc/nginx/github-ips.conf` 存在且内容正确

### Webhook 返回 502/504
- `sudo systemctl status langhack-webhook` 检查服务是否运行
- 检查 Nginx 配置中 `proxy_pass` 地址是否正确

### 部署未触发
- 确认推送分支与 `DEPLOY_BRANCH` 一致
- 查看日志: `tail -f logs/webhook.log`
- 检查 GitHub Webhook Recent Deliveries 中的响应

### Docker 环境中 webhook 不可达
- 确认 `docker-compose.yml` 中 frontend 服务有 `extra_hosts: ["host.docker.internal:host-gateway"]`
- 在容器内测试: `curl http://host.docker.internal:9000/webhook/health`

## 文件清单

| 文件 | 用途 |
|------|------|
| `scripts/webhook-listener.py` | Webhook 接收服务 |
| `deploy/webhook.env.example` | 环境变量模板 |
| `deploy/webhook.env` | 实际环境变量（已 gitignore） |
| `deploy/langhack-webhook.service` | systemd 服务文件 |
| `.github/workflows/ci.yml` | GitHub Actions CI 流水线 |
| `frontend/nginx.conf` | 开发环境 webhook 代理 |
| `nginx/nginx.conf` | 生产环境 webhook 代理（include 白名单） |
| `nginx/github-ips.conf` | GitHub IP 白名单初始文件 |
| `scripts/update-github-ips.sh` | IP 白名单自动更新脚本 |
| `deploy/github-ip-update.service` | IP 更新 systemd 服务 |
| `deploy/github-ip-update.timer` | IP 更新定时器（每天凌晨 3 点） |
