# 故障排除指南

## 常见问题

### 1. 前端无法连接后端 (ECONNREFUSED 127.0.0.1:8080)

**错误信息**:
```
http proxy error at /api/student/assignments:
Error: connect ECONNREFUSED 127.0.0.1:8080
```

**原因**: 后端服务未启动或运行在不同的端口

**解决方案**:

#### 方案1: 启动后端服务

1. **检查后端是否正在运行**:
```bash
# 检查8080端口是否被占用
lsof -i :8080
# 或
netstat -an | grep 8080
```

2. **启动后端服务**:
```bash
cd backend
mvn spring-boot:run
```

3. **验证后端服务**:
打开浏览器访问 `http://localhost:8080/api/health` (如果有健康检查接口)
或访问 `http://localhost:8080` 查看是否有响应

#### 方案2: 检查后端端口配置

如果后端运行在不同的端口，需要修改前端代理配置：

1. **检查后端端口**:
查看 `backend/src/main/resources/application.yml` 中的 `server.port` 配置

2. **修改前端代理**:
编辑 `frontend/vite.config.js`:
```javascript
server: {
  port: 3000,
  proxy: {
    '/api': {
      target: 'http://localhost:YOUR_BACKEND_PORT', // 修改为实际端口
      changeOrigin: true
    }
  }
}
```

#### 方案3: 使用启动脚本

如果项目有启动脚本，可以使用：
```bash
# 在项目根目录
./start.sh
```

### 2. 数据库连接失败

**错误信息**: 
- `Communications link failure`
- `Access denied for user`
- `Unknown database`

**解决方案**:

1. **检查数据库服务**:
```bash
# 检查MySQL是否运行
sudo systemctl status mysql
# 或
service mysql status
```

2. **检查数据库配置**:
编辑 `backend/src/main/resources/application.yml`:
```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/english_learning
    username: root
    password: your_password
```

3. **创建数据库**:
```bash
mysql -u root -p -e "CREATE DATABASE english_learning DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
```

### 3. 前端依赖安装失败

**解决方案**:

1. **清除缓存并重新安装**:
```bash
cd frontend
rm -rf node_modules package-lock.json
npm cache clean --force
npm install
```

2. **使用yarn** (如果npm有问题):
```bash
cd frontend
yarn install
```

### 4. 端口被占用

**解决方案**:

1. **查找占用端口的进程**:
```bash
# 查找占用3000端口的进程
lsof -i :3000
# 或
netstat -an | grep 3000
```

2. **杀死进程**:
```bash
kill -9 <PID>
```

3. **修改端口**:
编辑 `frontend/vite.config.js` 修改 `server.port`

### 5. CORS跨域问题

如果遇到CORS错误，检查后端配置：

1. **检查后端CORS配置**:
查看 `backend/src/main/java/com/english/config/` 中的CORS配置类

2. **确保允许前端域名**:
```java
@CrossOrigin(origins = "http://localhost:3000")
```

### 6. WSL使用主机maven

建议在wsl安装maven，并配置环境变量，然后使用wsl的maven命令。

```bash
which mvn
/usr/bin/mvn spring-boot:run
/usr/bin/mvn clean compile
```

如果需要使用主机maven，可以使用以下命令：

```bash
mvn -version
```

## 快速检查清单

在启动项目前，确保：

- [ ] MySQL数据库服务正在运行
- [ ] 数据库已创建并初始化
- [ ] 后端配置文件 `application.yml` 中的数据库连接信息正确
- [ ] 后端服务已启动在8080端口
- [ ] 前端依赖已安装 (`npm install`)
- [ ] 前端代理配置指向正确的后端地址

## 启动顺序

正确的启动顺序：

1. **启动MySQL数据库**
2. **启动后端服务** (`cd backend && mvn spring-boot:run`)
3. **等待后端完全启动** (看到 "Started Application" 日志)
4. **启动前端服务** (`cd frontend && npm run dev`)

## 获取帮助

如果问题仍然存在：

1. 检查后端日志: `backend/logs/` 目录
2. 检查前端控制台错误信息
3. 检查浏览器开发者工具的Network标签
4. 查看完整的错误堆栈信息

