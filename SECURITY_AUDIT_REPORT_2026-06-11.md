# LangHack 项目安全审计报告

**审计日期**: 2026-06-11  
**项目**: LangHack 智享英学英语学习平台  
**环境**: SpringBoot + Vue3 + MySQL主从 + Redis + Docker

---

## 📊 执行摘要

### 问题统计

| 严重程度 | 数量 | 说明 |
|---------|------|------|
| 🔴 高危 | 6 | 需立即修复 |
| 🟡 中危 | 8 | 2周内修复 |
| 🟢 低危 | 6 | 计划内优化 |
| **总计** | **20** | - |

### 关键发现

**✅ 良好实践**:
- 使用环境变量管理敏感配置
- Docker日志轮转配置完善
- MySQL主从复制高可用架构
- 完善的健康检查机制
- Flyway数据库版本控制（已配置）

**⚠️ 需要改进**:
- CORS配置过于宽松
- 数据库端口直接暴露
- 缺少API频率限制
- 容器以root用户运行
- 备份目录权限管理

---

## 🔴 高危问题

### 1. .env文件包含敏感信息（未加入Git）

**文件**: `.env`  
**风险**: 如果误提交到Git，所有密钥泄露

**当前状态**: ✅ `.env`已在`.gitignore`中，但需确认

**修复建议**:
```bash
# 1. 确认.env未被追踪
git ls-files | grep "\.env$"

# 2. 如果已提交，立即清理Git历史
git filter-branch --force --index-filter \
  "git rm --cached --ignore-unmatch .env" \
  --prune-empty --tag-name-filter cat -- --all

# 3. 轮换所有已泄露的密钥
```

---

### 2. CORS配置过于宽松

**文件**: `docker-compose.yml` (Line 221-222)  
**问题**: 允许所有局域网IP访问

```yaml
APP_CORS_ALLOWED_ORIGINS: http://localhost,http://192.168.81.1:3000,...
APP_CORS_ALLOWED_ORIGIN_PATTERNS: http://192.168.*.*:*,http://172.*.*.*:*
```

**风险**: 增加CSRF攻击面

**修复**:
```yaml
# 生产环境
APP_CORS_ALLOWED_ORIGINS: https://langhack.imau.edu.cn
APP_CORS_ALLOWED_ORIGIN_PATTERNS: ""

# 开发环境
APP_CORS_ALLOWED_ORIGINS: http://localhost:3000,http://127.0.0.1:3000
```

---

### 3. MySQL端口暴露到主机

**文件**: `docker-compose.yml` (Lines 57, 117)

```yaml
mysql-master:
  ports:
    - "3306:3306"  # ❌ 可从外部访问

mysql-slave:
  ports:
    - "3307:3306"  # ❌ 可从外部访问
```

**风险**: 数据库暴露在公网（如服务器有公网IP）

**修复**:
```yaml
# 方案1: 仅绑定本地
ports:
  - "127.0.0.1:3306:3306"

# 方案2: 完全不暴露（推荐）
expose:
  - "3306"
# 外部访问使用SSH隧道: ssh -L 3306:localhost:3306 user@server
```

---

### 4. Redis端口暴露且密码可能未设置

**文件**: `docker-compose.yml` (Line 159-163)

```yaml
redis:
  ports:
    - "6379:6379"
  command: >
    --requirepass ${REDIS_PASSWORD}  # 如果未设置则无密码
```

**风险**: Redis未授权访问 → 数据泄露 + RCE

**修复**:
```yaml
redis:
  ports:
    - "127.0.0.1:6379:6379"
  command: >
    --requirepass ${REDIS_PASSWORD:?REDIS_PASSWORD必须设置}
    --maxmemory 512mb

# .env 必须设置强密码
REDIS_PASSWORD=$(openssl rand -base64 32)
```

---

### 5. Docker容器以root用户运行

**文件**: `backend/Dockerfile`, `frontend/Dockerfile`  
**问题**: 未指定非root用户

**风险**: 容器逃逸后可能获得宿主机root权限

**修复**:
```dockerfile
# backend/Dockerfile
FROM openjdk:11-jre-slim
RUN groupadd -r appuser && useradd -r -g appuser appuser
WORKDIR /app
COPY --chown=appuser:appuser target/*.jar app.jar
USER appuser
EXPOSE 8080
CMD ["java", "-jar", "app.jar"]
```

---

### 6. 备份目录权限管理

**文件**: `./backups/` 目录  
**问题**: 备份文件包含敏感数据，权限可能不当

**修复**:
```bash
# 1. 设置严格权限
chmod 700 backups/
chown root:root backups/

# 2. 加密备份
mysqldump | gzip | openssl enc -aes-256-cbc -out backup.enc -k $PASSWORD

# 3. 定期清理
find ./backups -type f -mtime +30 -delete
```

---

## 🟡 中危问题

### 7. 缺少API频率限制

**问题**: 登录/注册等接口无限流保护

**风险**: 暴力破解、资源耗尽、DDoS

**修复**: 使用Spring Cloud Gateway或Bucket4j
```java
@Bean
public RateLimiter rateLimiter() {
    return RateLimiter.create(10.0); // 每秒10请求
}
```

---

### 8. JWT无刷新机制

**问题**: Token过期前无法撤销

**修复**: 实现Access Token(15分钟) + Refresh Token(7天)机制

---

### 9. 密码策略未强制

**问题**: 未发现密码复杂度验证

**修复**:
```java
String regex = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&])[A-Za-z\\d@$!%*?&]{8,}$";
if (!password.matches(regex)) {
    throw new IllegalArgumentException("密码必须8位以上，包含大小写字母、数字、特殊字符");
}
```

---

### 10. 异常信息可能暴露过多

**风险**: 堆栈信息泄露代码结构

**修复**:
```java
@ControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(Exception.class)
    public Result handleException(Exception e) {
        log.error("系统异常", e);
        return Result.error("操作失败，请稍后重试"); // 不暴露详细信息
    }
}
```

---

### 11. 日志可能包含敏感信息

**修复**: 脱敏处理
```java
log.info("用户登录: username={}", username); // ✅
log.info("用户登录: username={}, password={}", username, password); // ❌
```

---

### 12. 会话固定攻击

**修复**: Spring Security配置
```java
http.sessionManagement()
    .sessionFixation().migrateSession();
```

---

### 13. 文件上传大小限制

**检查**: `spring.servlet.multipart.max-file-size`
**建议**: 限制为10MB以内

---

### 14. SQL注入风险检查

**当前**: 使用MyBatis Plus，默认参数化查询
**注意**: 避免使用`${}`拼接SQL，应使用`#{}`

---

## 🟢 低危问题

### 15. Docker镜像体积优化

**建议**: 使用multi-stage build减小镜像大小

---

### 16. 日志级别生产环境检查

**检查**: 生产环境应为INFO或WARN级别

---

### 17. Flyway迁移文件版本冲突

**当前状态**:
```yaml
SPRING_FLYWAY_ENABLED: "false"  # 已禁用
```

**注释**: "仓库存在重复版本号 (V25, V27)，需先重命名后再启用"

**修复建议**:
```bash
# 1. 检查重复版本
ls backend/src/main/resources/db/migration/ | sort

# 2. 重命名重复文件
mv V25__duplicate.sql V25.1__duplicate.sql

# 3. 启用Flyway
SPRING_FLYWAY_ENABLED: "true"
SPRING_FLYWAY_BASELINE_ON_MIGRATE: "true"
```

---

### 18. 健康检查端点暴露

**当前**: `/api/health` 无认证

**建议**: 仅允许内网或监控系统访问

---

### 19. 定时任务安全

**检查**: 定时任务是否有权限控制

---

### 20. 依赖库版本检查

**建议**: 定期运行
```bash
mvn versions:display-dependency-updates
npm audit
```

---

## 📋 修复优先级

### P0 (立即修复，48小时内)
1. MySQL/Redis端口绑定到本地
2. CORS配置收紧为生产域名
3. Docker容器添加非root用户

### P1 (高优先级，1周内)
4. 备份目录权限加固
5. 添加API频率限制
6. JWT刷新机制实现

### P2 (中优先级，2周内)
7. 密码策略强化
8. 异常处理统一规范
9. 日志脱敏处理

### P3 (低优先级，1月内)
10. Flyway版本冲突解决
11. Docker镜像优化
12. 依赖库升级

---

## 🔧 快速修复脚本

```bash
#!/bin/bash
# quick-security-fix.sh

cd /g/GitHub_local/project/英语学习平台/LangHack

# 1. 确保.env不被追踪
if ! grep -q "^\.env$" .gitignore; then
    echo ".env" >> .gitignore
    echo "✅ .env 已添加到 .gitignore"
fi

# 2. 修复备份目录权限
chmod 700 backups/
echo "✅ 备份目录权限已设置为 700"

# 3. 生成强随机密码
echo "📝 生成新密码（请更新到.env）:"
echo "JWT_SECRET=$(openssl rand -base64 64)"
echo "REDIS_PASSWORD=$(openssl rand -base64 32)"
echo "MYSQL_ROOT_PASSWORD=$(openssl rand -base64 24)"

# 4. 检查暴露端口
echo "⚠️  当前暴露端口:"
grep -A2 "ports:" docker-compose.yml | grep -E "^\s+-\s+\"[0-9]"

echo ""
echo "✅ 快速修复完成，请手动处理以下事项:"
echo "1. 修改 docker-compose.yml 中的端口绑定"
echo "2. 收紧 CORS 配置"
echo "3. 在 Dockerfile 中添加非 root 用户"
echo "4. 轮换所有密钥"
```

---

## 📚 参考资料

- [OWASP Top 10 2021](https://owasp.org/Top10/)
- [Docker Security Best Practices](https://docs.docker.com/engine/security/)
- [Spring Security Documentation](https://spring.io/projects/spring-security)
- [MySQL Security](https://dev.mysql.com/doc/refman/8.0/en/security.html)

---

**审计完成时间**: 2026-06-11 12:00:00  
**下次审计建议**: 2026-09-11 (3个月后)
