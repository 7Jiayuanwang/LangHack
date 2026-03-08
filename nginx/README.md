# LangHack Nginx 生产配置说明

本目录包含 LangHack 英语学习平台的 Nginx 反向代理生产配置，适用于以下部署架构：

- **应用服务器**：4 核 8 GB，运行 Nginx + Spring Boot（端口 8080）+ Vue 前端静态文件
- **算力服务器**：独立部署 AI 问答服务（端口 8000），由 Nginx 代理到 `/tutor` 路由

---

## 一、配置说明

### 1.1 限流区域设计意图

| Zone 名称 | 速率 | 覆盖接口 | 设计原因 |
|-----------|------|----------|----------|
| `api_general` | 20 req/s | `/api/*`（通用） | 防止单 IP 爬接口，允许正常用户高频操作 |
| `api_translate` | 5 req/s | `/api/translate` | 翻译接口调用外部付费 API，配合后端 50次/分钟双重限流 |
| `api_tutor` | 2 req/s | `/tutor` | AI 问答单次耗时长、占用 GPU，严格保护算力服务器 |
| `api_auth` | 1 req/s | `/api/auth` | 防止暴力破解登录，每 IP 每秒最多 1 次尝试 |

`burst` 参数说明：允许瞬时突发请求数，超出 burst 后才真正触发限流（返回 429）。`nodelay` 表示突发请求立即处理而非排队等待。

### 1.2 Upstream keepalive 说明

- `backend keepalive 32`：维持 Nginx 到 Spring Boot 的 32 个长连接，避免每次请求的 TCP 握手开销
- `tutor_api keepalive 8`：算力服务器连接数较少，8 个长连接足够

### 1.3 各接口超时设置

| 接口 | `proxy_read_timeout` | 原因 |
|------|----------------------|------|
| AI 问答 `/tutor` | 120s | LLM 推理耗时，流式响应可能较慢 |
| 媒体上传 `/api/media` | 120s | 大文件上传需要足够时间 |
| 通用 API `/api/` | 60s | 普通业务接口的合理超时 |
| 认证 `/api/auth` | 15s | 登录/注册应快速响应 |
| 翻译 `/api/translate` | 30s | 外部翻译 API 调用可能有延迟 |

### 1.4 错误响应 JSON 化

Nginx 默认的 429/502/503/504 错误页是 HTML，前端 `axios` 拦截器无法统一解析。本配置将这些错误统一转为与后端一致的 JSON 格式：

```json
{"code": 503, "message": "服务暂时不可用，请稍后重试", "data": null}
```

### 1.5 前端静态文件缓存策略

- `/assets/` 下的文件由 Vite 构建时注入内容 hash（如 `index-D3b8xK7v.js`），文件内容变化时文件名随之改变
- 因此可以安全地设置 `Cache-Control: public, immutable` + 30 天过期，浏览器强缓存命中率接近 100%
- `index.html` 不在 `/assets/` 下，走默认策略（不缓存），确保版本更新后用户能获取最新入口文件

---

## 二、部署步骤

### 2.1 安装 Nginx

```bash
# Ubuntu/Debian
sudo apt update && sudo apt install -y nginx

# CentOS/RHEL
sudo yum install -y nginx
# 或
sudo dnf install -y nginx
```

### 2.2 申请 SSL 证书（以 Let's Encrypt 为例）

```bash
sudo apt install -y certbot python3-certbot-nginx

# 申请证书（替换为实际域名）
sudo certbot certonly --nginx -d your-domain.com

# 证书路径（自动生成）
# /etc/letsencrypt/live/your-domain.com/fullchain.pem
# /etc/letsencrypt/live/your-domain.com/privkey.pem

# 配置自动续期
sudo systemctl enable certbot.timer
sudo systemctl start certbot.timer
```

### 2.3 部署前端构建产物

```bash
# 在本地执行前端构建
cd frontend
npm run build

# 将 dist/ 目录上传到服务器
scp -r dist/* user@your-server:/var/www/langhack/
# 或使用 rsync
rsync -avz --delete dist/ user@your-server:/var/www/langhack/

# 在服务器上设置权限
sudo mkdir -p /var/www/langhack
sudo chown -R nginx:nginx /var/www/langhack
```

### 2.4 替换配置文件中的占位符并部署

```bash
# 复制配置文件
sudo cp nginx.conf /etc/nginx/nginx.conf

# 创建 SSL 目录（如果使用自定义证书）
sudo mkdir -p /etc/nginx/ssl

# 手动编辑替换占位符（见第三节清单）
sudo vim /etc/nginx/nginx.conf

# 验证配置语法
sudo nginx -t

# 使配置生效
sudo systemctl reload nginx

# 若 Nginx 未启动则先启动
sudo systemctl enable nginx
sudo systemctl start nginx
```

---

## 三、需要替换的占位符清单

部署前必须替换以下 4 处占位符，否则 `nginx -t` 会失败（DNS 解析错误）或功能异常：

| 行号（参考） | 占位符 | 替换为 | 示例 |
|-------------|--------|--------|------|
| upstream tutor_api | `TUTOR_SERVER_IP` | 算力服务器的实际 IP 地址 | `192.168.1.100` |
| server_name | `your-domain.com` | 实际注册域名 | `langhack.example.com` |
| ssl_certificate | `/etc/nginx/ssl/fullchain.pem` | SSL 证书文件绝对路径 | `/etc/letsencrypt/live/langhack.example.com/fullchain.pem` |
| ssl_certificate_key | `/etc/nginx/ssl/privkey.pem` | SSL 私钥文件绝对路径 | `/etc/letsencrypt/live/langhack.example.com/privkey.pem` |

替换示例（使用 sed 批量替换）：

```bash
DOMAIN="langhack.example.com"
TUTOR_IP="192.168.1.100"

sudo sed -i \
  -e "s/your-domain.com/$DOMAIN/g" \
  -e "s/TUTOR_SERVER_IP/$TUTOR_IP/g" \
  -e "s|/etc/nginx/ssl/fullchain.pem|/etc/letsencrypt/live/$DOMAIN/fullchain.pem|g" \
  -e "s|/etc/nginx/ssl/privkey.pem|/etc/letsencrypt/live/$DOMAIN/privkey.pem|g" \
  /etc/nginx/nginx.conf
```

---

## 四、常用运维命令

### 4.1 配置管理

```bash
# 检查配置语法（部署前必做）
sudo nginx -t

# 平滑重载配置（不中断现有连接）
sudo systemctl reload nginx
# 或
sudo nginx -s reload

# 查看 Nginx 运行状态
sudo systemctl status nginx

# 查看 Nginx 版本和编译参数
nginx -V
```

### 4.2 日志查看

```bash
# 实时查看访问日志
sudo tail -f /var/log/nginx/access.log

# 实时查看错误日志
sudo tail -f /var/log/nginx/error.log

# 查看限流触发日志（error 日志中关键字为 limiting requests）
sudo grep "limiting requests" /var/log/nginx/error.log | tail -50

# 查看 429 响应数量（按接口统计）
sudo awk '$9 == 429 {print $7}' /var/log/nginx/access.log | sort | uniq -c | sort -rn | head -20

# 查看响应时间最慢的接口 TOP 10
sudo awk '{print $NF, $7}' /var/log/nginx/access.log | sort -rn | head -10

# 查看各状态码分布
sudo awk '{print $9}' /var/log/nginx/access.log | sort | uniq -c | sort -rn
```

### 4.3 日志轮转（logrotate）

Nginx 默认由系统 logrotate 管理。手动触发：

```bash
sudo logrotate -f /etc/logrotate.d/nginx
```

如需自定义轮转策略，编辑 `/etc/logrotate.d/nginx`：

```
/var/log/nginx/*.log {
    daily
    missingok
    rotate 30
    compress
    delaycompress
    notifempty
    sharedscripts
    postrotate
        nginx -s reopen
    endscript
}
```

---

## 五、压测后调参指南

### 5.1 确定当前流量基准

压测或运行一段时间后，从 access.log 提取各接口的实际 req/s：

```bash
# 统计每分钟请求数（以最近 1000 条日志为样本）
sudo awk '{print $4}' /var/log/nginx/access.log | \
  awk -F: '{print $1":"$2":"$3}' | sort | uniq -c | tail -20

# 统计 /api/translate 接口的平均 req/s（取某一分钟）
sudo grep "$(date '+%d/%b/%Y:%H:%M' --date='5 minutes ago')" /var/log/nginx/access.log | \
  grep '/api/translate' | wc -l
# 结果除以 60 即为 req/s
```

### 5.2 burst 值调整原则

`burst` 应设置为**正常用户瞬时行为的合理峰值**，而非限流速率的简单倍数：

| 场景 | 调整方向 | 示例 |
|------|----------|------|
| 前端页面初始化时批量请求较多 | 适当增大 `burst` | `api_general burst=80` |
| 频繁触发 429 但用户无感知异常 | 适当增大 `burst` | `api_auth burst=8` |
| 存在明显爬虫/滥用 | 减小 `rate` 和 `burst` | `api_translate rate=2r/s burst=5` |
| AI 接口用户并发量大 | 增大 `rate`，同步评估算力服务器负载 | `api_tutor rate=5r/s burst=10` |

### 5.3 worker_connections 调参

当前设置 `worker_connections 4096`，4 核 = 最大并发 16384。

监控当前连接数：

```bash
# 查看活跃连接数
ss -s

# Nginx 状态页（需在 server 块中启用 stub_status）
# 添加以下 location 到配置后 reload：
# location /nginx_status {
#     stub_status on;
#     allow 127.0.0.1;
#     deny all;
# }
curl http://127.0.0.1/nginx_status
```

如果 `Active connections` 长期接近 `worker_processes * worker_connections`，需要增大 `worker_connections` 或 `worker_processes`。

### 5.4 keepalive_timeout 调参

- 当前值 `65s` 适合大部分场景
- 如果后端是 Spring Boot 且启用了 HTTP/2，可适当增大到 `75s`
- 静态资源站点或 CDN 场景可减小到 `30s` 节省连接资源

### 5.5 Gzip 压缩调参

当前 `gzip_comp_level 5`（CPU/压缩率平衡点）：

- 压缩比优先（CPU 充足）：调整为 `6-7`
- CPU 优先（高并发压力大）：调整为 `3-4`
- 验证 Gzip 是否生效：

```bash
curl -H "Accept-Encoding: gzip" -I https://your-domain.com/assets/index.js | grep Content-Encoding
# 应输出: Content-Encoding: gzip
```

---

## 六、常见问题排查

### Q: nginx -t 报错 "host not found in upstream"

原因：`TUTOR_SERVER_IP` 占位符未替换，或算力服务器 IP 不可达。

```bash
# 检查网络连通性
ping TUTOR_SERVER_IP
curl http://TUTOR_SERVER_IP:8000/health
```

### Q: 前端访问 404（Vue Router history 模式）

确认 `location /` 块中有 `try_files $uri $uri/ /index.html;`，且 `/var/www/langhack/index.html` 文件存在。

### Q: 上传文件报 413 Request Entity Too Large

检查 `client_max_body_size` 设置，`/api/media` 块设置了 100M，全局设置了 50M。若仍超限，在对应 `location` 块中增大该值。

### Q: HTTPS 证书过期

```bash
# 手动续期
sudo certbot renew --dry-run   # 先测试
sudo certbot renew              # 正式续期
sudo systemctl reload nginx
```
