# ETL 模式配置指南

> **适用场景**：学校提供了数据库直连权限，需要通过 JDBC 直接从学校数据库获取数据

---

## 一、前置条件

### 1.1 必需信息

从学校数据管理部门获取以下信息：

1. **数据库连接信息**
   - JDBC URL（如 `jdbc:mysql://school-db.imau.edu.cn:3306/school_db`）
   - 数据库用户名
   - 数据库密码

2. **数据库表/视图结构**
   - 确认学校数据库中存在以下视图或表
   - 每个视图/表必须包含 `updated_at` 字段（用于增量同步）

### 1.2 必需的视图/表

| 视图名称 | 用途 | 必须字段 |
|---------|------|---------|
| `v_administrative_class` | 行政班信息 | updated_at + 行政班相关字段 |
| `v_school_course` | 课程信息 | updated_at + 课程相关字段 |
| `v_teaching_class` | 教学班信息 | updated_at + 教学班相关字段 |
| `v_course_schedule` | 课表信息 | updated_at + 课表相关字段 |
| `v_course_selection` | 选课记录 | updated_at + 选课相关字段 |
| `v_level_exam_scores` | 等级考试成绩 | updated_at + 成绩相关字段 |
| `v_teaching_plans` | 教学计划 | updated_at + 计划相关字段 |
| `v_teacher_courses` | 教师课程 | updated_at + 教师课程字段 |
| `v_student_schedule` | 学生课表 | updated_at + 学生课表字段 |

---

## 二、配置步骤

### 2.1 环境变量配置

编辑 `docker/.env` 文件：

```bash
# 切换到 ETL 模式
SCHOOL_DATA_SOURCE=etl

# ETL 数据库连接配置
SCHOOL_ETL_JDBC_URL=jdbc:mysql://school-db.imau.edu.cn:3306/school_db
SCHOOL_ETL_USERNAME=your_username
SCHOOL_ETL_PASSWORD=your_password

# 同步配置
SCHOOL_SYNC_ENABLED=true
```

### 2.2 application.yml 配置（可选）

如果不使用环境变量，可以在 `backend/src/main/resources/application.yml` 中配置：

```yaml
school:
  data:
    source: etl  # 切换到 ETL 模式
    etl:
      jdbc-url: jdbc:mysql://school-db.imau.edu.cn:3306/school_db
      username: your_username
      password: your_password
      driver-class-name: com.mysql.cj.jdbc.Driver
      connection-timeout: 30000  # 连接超时 30 秒
      query-timeout: 60000       # 查询超时 60 秒
      queries:
        - data-source: administrative_class
          sql: SELECT * FROM v_administrative_class WHERE updated_at >= ?
        - data-source: school_course
          sql: SELECT * FROM v_school_course WHERE updated_at >= ?
        - data-source: teaching_class
          sql: SELECT * FROM v_teaching_class WHERE updated_at >= ?
        - data-source: course_schedule
          sql: SELECT * FROM v_course_schedule WHERE updated_at >= ?
        - data-source: course_selection
          sql: SELECT * FROM v_course_selection WHERE updated_at >= ?
        - data-source: level_exam_scores
          sql: SELECT * FROM v_level_exam_scores WHERE updated_at >= ?
        - data-source: teaching_plans
          sql: SELECT * FROM v_teaching_plans WHERE updated_at >= ?
        - data-source: teacher_courses
          sql: SELECT * FROM v_teacher_courses WHERE updated_at >= ?
        - data-source: student_schedule
          sql: SELECT * FROM v_student_schedule WHERE updated_at >= ?
    sync:
      enabled: true
      full-sync-cron: "0 0 2 * * ?"      # 全量同步：每天凌晨 2 点
      incremental-sync-cron: "0 0 * * * ?"  # 增量同步：每小时
```

---

## 三、自定义查询配置

### 3.1 如果学校数据库没有视图

如果学校数据库使用的是表而非视图，需要修改查询 SQL。

**示例**：假设学校数据库的行政班表名为 `t_administrative_class`

```yaml
school:
  data:
    etl:
      queries:
        - data-source: administrative_class
          sql: |
            SELECT 
              class_id,
              class_name,
              college_code,
              grade,
              updated_at
            FROM t_administrative_class 
            WHERE updated_at >= ?
```

### 3.2 如果没有 updated_at 字段

如果学校数据库表没有 `updated_at` 字段，需要修改为全量查询：

```yaml
school:
  data:
    etl:
      queries:
        - data-source: administrative_class
          sql: SELECT * FROM t_administrative_class
```

**注意**：全量查询会导致每次同步都拉取所有数据，性能较差。

### 3.3 自定义字段映射

如果学校数据库的字段名与系统不一致，需要在查询中使用别名：

```sql
SELECT 
  BJMC AS class_name,      -- 班级名称
  XSH AS college_code,     -- 学院代码
  NJ AS grade,             -- 年级
  GXSJ AS updated_at       -- 更新时间
FROM t_administrative_class 
WHERE GXSJ >= ?
```

---

## 四、网络配置

### 4.1 白名单配置

ETL 模式有 JDBC URL 白名单保护，默认只允许：
- `school-db.imau.edu.cn`
- `localhost`

如果学校数据库主机不在白名单中，需要修改 `SchoolDataProperties.java`：

```java
private static final List<String> ETL_ALLOWED_HOSTS = Arrays.asList(
    "school-db.imau.edu.cn",
    "your-school-db-host.edu.cn",  // 添加你的主机
    "localhost"
);
```

### 4.2 防火墙配置

确保后端服务器能访问学校数据库：

```bash
# 测试连接
telnet school-db.imau.edu.cn 3306

# 或使用 MySQL 客户端测试
mysql -h school-db.imau.edu.cn -P 3306 -u your_username -p
```

---

## 五、测试验证

### 5.1 连接测试

启动应用后，通过运维面板测试连接：

1. 登录运维账号（`ops` / `admin123`）
2. 进入 **运维** → **学校数据配置**
3. 点击 **测试连接** 按钮

或通过 API 测试：

```bash
# 获取管理员 Token
ADMIN_JWT=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"ops","password":"admin123"}' \
  | jq -r '.data.token')

# 测试连接
curl -X POST http://localhost:8080/api/ops/school-data/test-connection \
  -H "Authorization: Bearer $ADMIN_JWT"
```

### 5.2 手动同步测试

```bash
# 全量同步
curl -X POST http://localhost:8080/api/ops/school-data/sync/full \
  -H "Authorization: Bearer $ADMIN_JWT"

# 增量同步
curl -X POST http://localhost:8080/api/ops/school-data/sync/incremental \
  -H "Authorization: Bearer $ADMIN_JWT"
```

### 5.3 查看同步日志

```bash
# 查看同步状态
curl http://localhost:8080/api/ops/school-data/sync/status \
  -H "Authorization: Bearer $ADMIN_JWT"

# 查看后端日志
docker logs langhack-backend | grep -i "school.*etl"
```

---

## 六、故障排查

### 6.1 连接失败

**错误信息**：`学校 ETL 数据库连接测试失败`

**排查步骤**：
1. 检查 JDBC URL 格式是否正确
2. 检查用户名密码是否正确
3. 检查网络连通性（`telnet` 或 `ping`）
4. 检查数据库用户权限（是否有 SELECT 权限）
5. 检查防火墙规则

### 6.2 查询失败

**错误信息**：`学校 ETL 未配置数据源 SQL 映射`

**原因**：查询配置中缺少对应数据源的 SQL

**解决**：在 `application.yml` 或 `SchoolDataProperties.java` 中添加对应的 QueryMapping

### 6.3 字段映射错误

**错误信息**：`字段映射失败` 或数据为空

**排查步骤**：
1. 检查学校数据库的实际字段名
2. 查看 `field_mappings` 表的映射配置
3. 检查查询 SQL 是否返回了正确的字段
4. 查看后端日志中的字段映射详情

```sql
-- 查看字段映射配置
SELECT * FROM field_mappings WHERE data_source = 'administrative_class';
```

### 6.4 性能问题

**症状**：同步速度慢或超时

**优化方案**：
1. 调整查询超时时间（`query-timeout`）
2. 在学校数据库的 `updated_at` 字段上创建索引
3. 调整连接池大小（`SchoolDataEtlClientImpl.java:279`）
4. 使用增量同步而非全量同步

---

## 七、安全注意事项

### 7.1 数据库权限

建议学校数据库管理员创建只读账号：

```sql
-- 创建只读用户
CREATE USER 'langhack_readonly'@'%' IDENTIFIED BY 'secure_password';

-- 授予只读权限
GRANT SELECT ON school_db.v_administrative_class TO 'langhack_readonly'@'%';
GRANT SELECT ON school_db.v_school_course TO 'langhack_readonly'@'%';
GRANT SELECT ON school_db.v_teaching_class TO 'langhack_readonly'@'%';
GRANT SELECT ON school_db.v_course_schedule TO 'langhack_readonly'@'%';
GRANT SELECT ON school_db.v_course_selection TO 'langhack_readonly'@'%';
GRANT SELECT ON school_db.v_level_exam_scores TO 'langhack_readonly'@'%';
GRANT SELECT ON school_db.v_teaching_plans TO 'langhack_readonly'@'%';
GRANT SELECT ON school_db.v_teacher_courses TO 'langhack_readonly'@'%';

FLUSH PRIVILEGES;
```

### 7.2 密码管理

- **不要**将数据库密码硬编码在配置文件中
- **使用**环境变量或密钥管理服务
- **定期**更换数据库密码

### 7.3 网络隔离

- 学校数据库应配置 IP 白名单，只允许后端服务器访问
- 使用 VPN 或专线连接，避免公网暴露

---

## 八、ETL vs API 模式对比

| 特性 | ETL 模式 | API 模式 |
|------|---------|---------|
| **接入方式** | JDBC 直连数据库 | REST API |
| **性能** | 高（直接查询） | 中（HTTP 开销） |
| **实时性** | 高（秒级） | 中（分钟级） |
| **依赖** | 数据库权限 | API 密钥 |
| **安全性** | 需要数据库账号 | 仅需 API 密钥 |
| **维护成本** | 高（需维护视图） | 低（学校维护） |
| **适用场景** | 高频同步、大数据量 | 低频同步、小数据量 |

**建议**：
- 如果学校提供了 API 接口，优先使用 API 模式
- 如果 API 性能不足或不稳定，再考虑 ETL 模式

---

## 九、常见问题

### Q1: 可以同时使用 API 和 ETL 模式吗？

**A**: 不可以。系统通过 `school.data.source` 配置切换模式，同一时间只能使用一种模式。

### Q2: 切换模式需要重启应用吗？

**A**: 是的。切换模式需要修改配置并重启应用。

### Q3: ETL 模式支持哪些数据库？

**A**: 当前仅支持 MySQL 8.0+。如需支持其他数据库（如 Oracle、SQL Server），需要修改驱动配置。

### Q4: 如何验证数据同步是否成功？

**A**: 
1. 查看同步日志表 `sync_logs`
2. 查看对应的数据表（如 `administrative_class`）
3. 通过运维面板查看同步状态

```sql
-- 查看最近的同步日志
SELECT * FROM sync_logs ORDER BY created_at DESC LIMIT 10;

-- 查看行政班数据
SELECT COUNT(*) FROM administrative_class;
```

---

## 十、联系支持

如遇到问题，请提供以下信息：

1. 错误日志（`docker logs langhack-backend`）
2. 配置信息（脱敏后的 JDBC URL、用户名）
3. 学校数据库表结构（`SHOW CREATE TABLE v_administrative_class`）
4. 同步日志（`SELECT * FROM sync_logs WHERE status = 'FAILED'`）

---

**最后更新**: 2026-04-27
