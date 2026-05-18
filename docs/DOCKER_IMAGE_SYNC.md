# Docker 镜像同步工具

## 概述

`scripts/sync-docker-images.sh` 用于将 LangHack 项目依赖的公共 Docker 镜像同步到本地私有 registry，解决以下场景：

- **内网部署**：生产环境无法访问 Docker Hub
- **加速构建**：避免重复从公网拉取镜像
- **版本锁定**：确保团队使用统一的镜像版本
- **离线部署**：支持完全离线的部署流程

## 快速开始

### 基础用法

```bash
# 同步到默认 registry (localhost:5000)
bash scripts/sync-docker-images.sh

# 指定 registry 地址
bash scripts/sync-docker-images.sh --registry 192.168.1.10:5000

# 预览模式（不实际执行）
bash scripts/sync-docker-images.sh --dry-run
```

### 环境变量配置

```bash
# 方式 1: 环境变量
LOCAL_REGISTRY=192.168.1.10:5000 bash scripts/sync-docker-images.sh

# 方式 2: 修改 docker/.env
echo "LOCAL_REGISTRY=192.168.1.10:5000" >> docker/.env
bash scripts/sync-docker-images.sh
```

## 同步的镜像清单

| 镜像 | 用途 | 大小（约） |
|------|------|-----------|
| `mysql:8.0` | 数据库 | 500 MB |
| `redis:6-alpine` | 缓存 | 30 MB |
| `maven:3.8-openjdk-17` | 后端构建 | 400 MB |
| `eclipse-temurin:17-jre-alpine` | 后端运行时 | 180 MB |
| `node:20-alpine` | 前端构建 | 120 MB |
| `nginx:alpine` | 前端服务器 | 40 MB |

**总计**：约 1.3 GB

## 工作流程

```
┌─────────────────┐
│ 公共 Docker Hub │
└────────┬────────┘
         │ pull
         ▼
┌─────────────────┐
│   本地 Docker   │
└────────┬────────┘
         │ tag + push
         ▼
┌─────────────────┐
│ 本地 Registry   │ ← deploy.sh 从这里拉取
└─────────────────┘
```

1. **Pull**：从 Docker Hub 拉取公共镜像
2. **Tag**：重新标记为 `<registry>/<image>:<tag>`
3. **Push**：推送到本地 registry
4. **Verify**：验证镜像清单可读

## 本地 Registry 自动管理

脚本会自动检测并启动本地 registry（仅限 `localhost` / `127.0.0.1`）：

```bash
# 检查 Registry 是否运行
curl -sf http://localhost:5000/v2/_catalog

# 如果未运行，自动执行：
docker run -d \
  -p 5000:5000 \
  --name local-registry \
  --restart always \
  -v /data/registry:/var/lib/registry \
  registry:2
```

### 数据持久化

默认存储路径：`/data/registry`

自定义路径：
```bash
REGISTRY_DATA_PATH=/custom/path bash scripts/sync-docker-images.sh
```

## 与部署流程集成

### 标准部署流程

```bash
# 步骤 1: 同步镜像到本地 registry
bash scripts/sync-docker-images.sh --registry 192.168.1.10:5000

# 步骤 2: 使用本地镜像部署
IMAGE_SOURCE_MODE=local \
LOCAL_REGISTRY=192.168.1.10:5000 \
bash scripts/deploy.sh
```

### docker-compose.yml 配置

部署脚本会自动修改 `docker-compose.yml` 中的镜像引用：

```yaml
# 原始配置
image: mysql:8.0

# 自动替换为
image: 192.168.1.10:5000/mysql:8.0
```

## 故障排查

### 问题 1: Registry 连接失败

**症状**：
```
[ERROR] Registry 启动失败或无法连接: localhost:5000
```

**排查步骤**：
```bash
# 1. 检查 Docker daemon
docker info

# 2. 检查端口占用
sudo netstat -tlnp | grep 5000

# 3. 检查容器状态
docker ps -a | grep local-registry

# 4. 查看容器日志
docker logs local-registry
```

**解决方案**：
- 端口被占用 → 修改端口：`--registry localhost:5001`
- 容器异常退出 → 删除重建：`docker rm -f local-registry`
- 权限问题 → 检查数据目录权限：`sudo chown -R $(id -u):$(id -g) /data/registry`

### 问题 2: 推送失败 (HTTP 403/500)

**症状**：
```
Error response from daemon: received unexpected HTTP status: 500 Internal Server Error
```

**原因**：Registry 存储空间不足或配置错误

**解决方案**：
```bash
# 检查磁盘空间
df -h /data/registry

# 清理未使用的镜像
docker exec local-registry registry garbage-collect /etc/docker/registry/config.yml

# 重启 Registry
docker restart local-registry
```

### 问题 3: 镜像拉取超时

**症状**：
```
Error response from daemon: Get https://registry-1.docker.io/v2/: net/http: TLS handshake timeout
```

**解决方案**：
```bash
# 配置 Docker Hub 镜像加速器（阿里云示例）
sudo mkdir -p /etc/docker
sudo tee /etc/docker/daemon.json <<EOF
{
  "registry-mirrors": ["https://your-mirror.aliyuncs.com"]
}
EOF
sudo systemctl restart docker
```

### 问题 4: 部署时仍从公网拉取

**症状**：部署日志显示 `Pulling from library/mysql`

**原因**：未正确传递 `IMAGE_SOURCE_MODE` 或 `LOCAL_REGISTRY`

**解决方案**：
```bash
# 检查环境变量
echo $IMAGE_SOURCE_MODE
echo $LOCAL_REGISTRY

# 确保传递给 deploy.sh
IMAGE_SOURCE_MODE=local LOCAL_REGISTRY=192.168.1.10:5000 bash scripts/deploy.sh
```

## 高级用法

### 同步到远程 Registry

```bash
# Harbor 示例
bash scripts/sync-docker-images.sh --registry harbor.example.com/langhack

# 需要先登录
docker login harbor.example.com
```

### 批量同步多个项目

```bash
#!/bin/bash
REGISTRIES=(
  "192.168.1.10:5000"
  "192.168.1.11:5000"
  "harbor.example.com/langhack"
)

for reg in "${REGISTRIES[@]}"; do
  echo "同步到 $reg"
  bash scripts/sync-docker-images.sh --registry "$reg"
done
```

### 定期同步（Cron）

```bash
# 每周日凌晨 2 点同步
0 2 * * 0 cd /path/to/LangHack && bash scripts/sync-docker-images.sh >> /var/log/docker-sync.log 2>&1
```

## 安全建议

1. **Registry 认证**：生产环境启用 HTTP Basic Auth 或 TLS
   ```bash
   docker run -d \
     -p 5000:5000 \
     -v /path/to/auth:/auth \
     -e "REGISTRY_AUTH=htpasswd" \
     -e "REGISTRY_AUTH_HTPASSWD_REALM=Registry Realm" \
     -e "REGISTRY_AUTH_HTPASSWD_PATH=/auth/htpasswd" \
     registry:2
   ```

2. **网络隔离**：Registry 仅监听内网 IP
   ```bash
   docker run -d -p 192.168.1.10:5000:5000 ...
   ```

3. **定期备份**：备份 `/data/registry` 目录

## 性能优化

### 并行同步

修改脚本，使用 GNU Parallel：
```bash
parallel -j 4 'docker pull {} && docker tag {} $REGISTRY/{} && docker push $REGISTRY/{}' ::: "${IMAGES[@]}"
```

### 镜像清理

定期清理旧版本镜像：
```bash
# 列出所有镜像
curl -s http://localhost:5000/v2/_catalog | jq -r '.repositories[]'

# 删除指定镜像
curl -X DELETE http://localhost:5000/v2/<name>/manifests/<digest>
```

## 相关文档

- [部署文档](DEPLOYMENT.md) - 完整部署流程
- [Docker Compose 配置](../docker-compose.yml) - 服务编排配置
- [环境变量说明](../docker/.env.example) - 环境变量模板

## 常见问题

**Q: 是否支持多架构镜像（ARM64/AMD64）？**  
A: 是的，脚本会保留原始镜像的 manifest，支持多架构。

**Q: 同步后原始镜像可以删除吗？**  
A: 可以，但建议保留以便快速回滚：`docker image prune -a`

**Q: Registry 数据可以迁移吗？**  
A: 可以，直接复制 `/data/registry` 目录到新服务器。

**Q: 如何验证同步成功？**  
A: 访问 `http://<registry>/v2/_catalog` 查看镜像列表。
