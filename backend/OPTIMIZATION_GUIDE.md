# 性能优化和功能增强文档

## 概述
本文档记录了对英语学习平台后端系统进行的性能优化和功能增强。

---

## 1. 事务管理优化

### 1.1 WordController 批量导入添加事务
**位置**: `com.english.controller.WordController.importWords()`

**优化内容**:
- 添加 `@Transactional(rollbackFor = Exception.class)` 注解
- 确保批量导入操作的原子性
- 如果任何步骤失败，所有操作都会回滚

**代码变更**:
```java
@PostMapping("/import")
@Transactional(rollbackFor = Exception.class)
public Result<Map<String, Object>> importWords(@RequestParam("file") MultipartFile file) {
    // ... 批量导入逻辑
}
```

**优势**:
- 数据一致性：确保批量操作要么全部成功，要么全部回滚
- 避免部分数据导入导致的数据不一致问题
- 提高数据完整性

---

## 2. 分页限制优化

### 2.1 AdminStatsController 添加分页限制
**位置**: `com.english.controller.AdminStatsController.getQuestionStats()`

**优化内容**:
- 添加 `pageSize` 参数（默认100条）
- 添加 `maxSize` 参数（最大1000条）
- 防止一次性加载过多数据造成性能问题

**代码变更**:
```java
@GetMapping("/questions")
public Result<QuestionStatsDTO> getQuestionStats(
        @RequestParam(defaultValue = "100") int pageSize,
        @RequestParam(defaultValue = "1000") int maxSize) {

    pageSize = Math.min(pageSize, maxSize);
    pageSize = Math.max(pageSize, 1);

    // 添加LIMIT限制
    baseQuery.last("LIMIT " + pageSize);
    // ...
}
```

**优势**:
- 减少内存占用
- 提高查询响应速度
- 防止数据量过大导致的性能问题

---

## 3. 学习统计查询优化

### 3.1 活跃学生排行榜优化
**位置**: `com.english.service.impl.AnswerRecordServiceImpl.getActiveStudentRanking()`

**优化内容**:
- 限制最大查询数量（不超过100条）
- 添加索引建议注释
- 优化查询逻辑

**代码变更**:
```java
@Override
public List<LearningStatsDTO.ActiveStudentDTO> getActiveStudentRanking(int limit) {
    // 限制最大查询数量
    limit = Math.min(limit, 100);

    // 建议添加索引（见下文）
    // ...
}
```

### 3.2 数据库索引优化
**位置**: `src/main/resources/db/migration/V003__add_performance_indexes.sql`

**新增索引**:

#### 答题记录表索引
```sql
-- 优化学生答题统计查询
CREATE INDEX idx_answer_record_student ON answer_records(student_id, is_correct);

-- 优化按时间查询答题记录
CREATE INDEX idx_answer_record_time ON answer_records(answer_time);

-- 优化学生答题历史查询
CREATE INDEX idx_answer_record_student_time ON answer_records(student_id, answer_time DESC);
```

#### 题目表索引
```sql
-- 优化按难度查询
CREATE INDEX idx_question_difficulty ON questions(difficulty, deleted);

-- 优化按题型查询
CREATE INDEX idx_question_type ON questions(question_type, deleted);

-- 优化按考试类型查询
CREATE INDEX idx_question_exam_type ON questions(exam_type, deleted);
```

#### 单词表索引
```sql
-- 优化单词搜索
CREATE INDEX idx_word_word ON words(word, deleted);

-- 优化按难度查询
CREATE INDEX idx_word_difficulty ON words(difficulty, deleted);
```

#### 用户表索引
```sql
-- 优化按角色查询
CREATE INDEX idx_user_role ON users(role, deleted);

-- 优化按注册时间查询
CREATE INDEX idx_user_created_time ON users(created_time DESC, deleted);
```

**优势**:
- 大幅提升查询性能
- 减少全表扫描
- 优化统计分析查询

---

## 4. JWT Token 刷新机制

### 4.1 功能概述
实现了完整的 JWT Token 刷新机制，包括 Access Token 和 Refresh Token 的双 Token 策略。

### 4.2 实现细节

#### 4.2.1 JwtUtil 增强
**位置**: `com.english.util.JwtUtil`

**新增方法**:
```java
// 生成 Refresh Token
public String generateRefreshToken(String username, String role)

// 检查 Token 是否可刷新
public Boolean canTokenBeRefreshed(String token)

// 刷新 Token
public String refreshToken(String token)
```

**Token 类型标识**:
- Access Token: `type=access`，有效期24小时
- Refresh Token: `type=refresh`，有效期30天

#### 4.2.2 配置更新
**位置**: `application.yml`

```yaml
spring:
  security:
    jwt:
      secret: ${JWT_SECRET:englishLearningPlatformSecretKey2023}
      expiration: ${JWT_EXPIRATION:86400000}              # 24小时
      refresh-expiration: ${JWT_REFRESH_EXPIRATION:2592000000} # 30天
```

#### 4.2.3 登录接口更新
**位置**: `com.english.service.impl.UserServiceImpl.login()`

**返回数据**:
```json
{
  "token": "eyJhbGciOiJIUzI1...",           // Access Token
  "refreshToken": "eyJhbGciOiJIUzI1...",   // Refresh Token
  "user": { ... },
  "expiresIn": 86400000
}
```

#### 4.2.4 新增刷新接口
**位置**: `com.english.controller.AuthController.refreshToken()`

**接口信息**:
- **URL**: `POST /api/auth/refresh`
- **请求体**:
```json
{
  "token": "refresh_token_here"
}
```
- **响应**:
```json
{
  "code": 200,
  "message": "Token刷新成功",
  "data": {
    "token": "new_access_token",
    "refreshToken": "original_refresh_token",
    "expiresIn": 86400000
  }
}
```

#### 4.2.5 新增 DTO
**位置**: `com.english.dto.RefreshTokenRequest`

```java
public class RefreshTokenRequest {
    private String token;
    // getter, setter
}
```

### 4.3 使用流程

1. **用户登录**
   - 调用 `/api/auth/login`
   - 获取 `token` (Access Token) 和 `refreshToken` (Refresh Token)
   - 客户端保存两个 Token

2. **正常请求**
   - 使用 Access Token 访问受保护的 API
   - 在请求头中添加: `Authorization: Bearer {access_token}`

3. **Token 过期**
   - Access Token 过期后（24小时后）
   - 使用 Refresh Token 调用 `/api/auth/refresh`
   - 获取新的 Access Token

4. **Refresh Token 过期**
   - Refresh Token 过期后（30天后）
   - 用户需要重新登录

### 4.4 安全考虑

- **分离存储**: 建议 Access Token 存储在内存，Refresh Token 存储在 HttpOnly Cookie
- **Token 类型验证**: 系统会验证 Token 类型，防止使用 Access Token 刷新
- **过期检查**: 刷新时会检查 Refresh Token 是否过期
- **用户验证**: 刷新时会验证用户是否存在

### 4.5 优势

- **用户体验**: 用户无需频繁登录
- **安全性**: Access Token 有效期短，即使泄露影响有限
- **灵活性**: 可以实现"记住我"功能
- **可控性**: 可以通过撤销 Refresh Token 强制用户重新登录

---

## 5. 应用索引的方法

执行以下 SQL 脚本创建索引：

```bash
# 方法1: 使用 Flyway/Liquibase 自动迁移
# 脚本位置: src/main/resources/db/migration/V003__add_performance_indexes.sql

# 方法2: 手动执行 SQL
mysql -u root -p english_learning < V003__add_performance_indexes.sql
```

---

## 6. 测试建议

### 6.1 事务测试
- 测试批量导入失败时的回滚机制
- 测试部分数据错误时的完整回滚

### 6.2 分页测试
- 测试不同 pageSize 参数
- 测试超过 maxSize 的限制

### 6.3 索引性能测试
```sql
-- 测试前
EXPLAIN SELECT * FROM answer_records WHERE student_id = 1;

-- 创建索引后
EXPLAIN SELECT * FROM answer_records WHERE student_id = 1;
-- 应该看到 type=ref, key=idx_answer_record_student
```

### 6.4 Token 刷新测试
1. 登录获取 Access Token 和 Refresh Token
2. 等待 Access Token 过期
3. 使用 Refresh Token 刷新
4. 验证新 Access Token 可用
5. 测试 Refresh Token 过期场景
6. 测试使用 Access Token 刷新（应失败）

---

## 7. 监控建议

### 7.1 性能监控
- 监控批量导入操作的耗时
- 监控统计查询的响应时间
- 监控数据库连接池状态

### 7.2 业务监控
- 统计 Token 刷新频率
- 监控异常的刷新请求（可能的攻击）
- 记录刷新失败的原因

---

## 8. 后续优化建议

### 8.1 短期优化
- 为学习统计查询实现 Redis 缓存
- 实现 Token 黑名单机制（用于强制登出）
- 添加 Token 刷新频率限制（防止滥用）

### 8.2 长期优化
- 实现分布式 Session 管理
- 使用 Redis 存储 Refresh Token
- 实现 Token 自动续期机制
- 添加 Token 使用审计日志

### 8.3 架构优化
- 考虑使用数据库读写分离
- 实现查询结果缓存
- 使用消息队列处理批量操作
- 实现定时预计算统计数据

---

## 9. 变更文件清单

### 新增文件
- `RefreshTokenRequest.java` - Refresh Token 请求 DTO
- `V003__add_performance_indexes.sql` - 数据库索引迁移脚本

### 修改文件
- `WordController.java` - 添加事务注解
- `AdminStatsController.java` - 添加分页限制
- `AnswerRecordServiceImpl.java` - 优化查询逻辑
- `JwtUtil.java` - 增加 Token 刷新功能
- `UserServiceImpl.java` - 返回 Refresh Token
- `AuthController.java` - 添加刷新接口
- `application.yml` - 添加 Refresh Token 配置

---

## 10. 兼容性说明

### 10.1 向后兼容
- 所有现有 API 保持兼容
- 客户端可以选择性使用 Refresh Token
- 旧的登录流程仍然有效

### 10.2 升级注意事项
- 需要更新客户端以支持 Refresh Token
- 需要执行数据库索引迁移脚本
- 建议在低峰期执行索引创建

---

## 联系与支持

如有问题或建议，请联系开发团队。
