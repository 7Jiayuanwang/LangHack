# ETL 模式完善总结

**完成时间**: 2026-04-27

---

## 一、完成的工作

### 1.1 代码修复

✅ **补充缺失的 teacher_courses 查询配置**

**文件**: `backend/src/main/java/com/english/config/SchoolDataProperties.java:279-288`

**修改内容**:
```java
// 添加了缺失的 teacher_courses 查询配置
new QueryMapping("teacher_courses", "SELECT * FROM v_teacher_courses WHERE updated_at >= ?")
```

**影响**: 
- 修复了 ETL 模式下无法同步教师课程数据的问题
- 修复了按学院链式拉取功能在 ETL 模式下失败的问题

---

### 1.2 文档创建

✅ **ETL 模式配置指南**

**文件**: `docs/ETL_MODE_SETUP.md`

**内容**:
- 前置条件和必需信息
- 环境变量配置步骤
- 自定义查询配置方法
- 网络配置和白名单设置
- 测试验证步骤
- 故障排查指南
- 安全注意事项
- ETL vs API 模式对比
- 常见问题解答

---

✅ **视图创建脚本**

**文件**: `docs/etl/create_views.sql`

**内容**:
- 8 个数据源的视图创建 SQL（含索引）
- 只读用户创建和授权脚本
- 测试查询脚本
- 详细的注释和说明

**视图列表**:
1. `v_administrative_class` - 行政班信息
2. `v_school_course` - 课程信息
3. `v_teaching_class` - 教学班信息
4. `v_course_schedule` - 课表信息
5. `v_course_selection` - 选课记录
6. `v_level_exam_scores` - 等级考试成绩
7. `v_teaching_plans` - 教学计划
8. `v_teacher_courses` - 教师授课信息

---

✅ **字段映射配置指南**

**文件**: `docs/etl/field_mapping_guide.md`

**内容**:
- 字段映射机制说明
- 8 个数据源的默认字段映射表
- 自定义字段映射方法
- 字段转换器使用说明
- 字段映射验证步骤
- 常见问题和最佳实践
- 字段映射示例

---

✅ **更新 CLAUDE.md**

**文件**: `CLAUDE.md:160-179`

**修改内容**:
- 补充了 ETL 模式的详细说明
- 区分了 API 模式和 ETL 模式的配置
- 添加了 ETL 模式的文档引用

---

## 二、ETL 模式架构

### 2.1 核心组件

```
SchoolDataApiClientDelegate (路由层)
    ├─ SchoolDataApiClientImpl (API 模式)
    └─ SchoolDataEtlClientImpl (ETL 模式) ✅ 已完善
         ↓
SchoolDataFetchService (数据获取)
         ↓
SchoolDataSyncService (同步协调)
         ↓
SchoolDataPersistService (持久化)
         ↓
本地数据库表
```

### 2.2 数据流转

```
学校数据库视图
    ↓ (JDBC 查询)
SchoolDataEtlClientImpl
    ↓ (ResultSet → List<Map>)
FieldMappingService (字段映射)
    ↓ (中文/英文 → snake_case)
SchoolDataPersistService (UPSERT)
    ↓
本地数据库表
```

---

## 三、使用 ETL 模式的步骤

### 3.1 前置准备

1. **获取学校数据库连接信息**
   - JDBC URL
   - 用户名
   - 密码

2. **确认视图/表结构**
   - 8 个必需视图是否存在
   - 每个视图是否包含 `updated_at` 字段

3. **网络连通性**
   - 后端服务器能否访问学校数据库
   - 防火墙规则是否允许

### 3.2 配置步骤

1. **修改环境变量** (`docker/.env`)
   ```bash
   SCHOOL_DATA_SOURCE=etl
   SCHOOL_ETL_JDBC_URL=jdbc:mysql://school-db.imau.edu.cn:3306/school_db
   SCHOOL_ETL_USERNAME=your_username
   SCHOOL_ETL_PASSWORD=your_password
   ```

2. **创建视图**（如果学校数据库没有）
   - 使用 `docs/etl/create_views.sql` 脚本
   - 或根据实际表结构修改

3. **配置字段映射**（如果字段名不同）
   - 参考 `docs/etl/field_mapping_guide.md`
   - 在 `field_mappings` 表中添加映射

4. **重启应用**
   ```bash
   docker restart langhack-backend
   ```

5. **测试连接**
   - 运维面板 → 学校数据配置 → 测试连接
   - 或通过 API 测试

6. **执行同步**
   - 手动触发全量同步
   - 验证数据是否正确同步

### 3.3 验证步骤

```bash
# 1. 测试连接
curl -X POST http://localhost:8080/api/ops/school-data/test-connection \
  -H "Authorization: Bearer $ADMIN_JWT"

# 2. 全量同步
curl -X POST http://localhost:8080/api/ops/school-data/sync/full \
  -H "Authorization: Bearer $ADMIN_JWT"

# 3. 查看同步日志
docker logs langhack-backend | grep -i "school.*etl"

# 4. 查看数据库
docker exec langhack-mysql-master mysql -uroot -p${MYSQL_ROOT_PASSWORD} \
  -e "SELECT COUNT(*) FROM english_learning.administrative_class;"
```

---

## 四、关键改进点

### 4.1 修复的问题

1. ❌ **teacher_courses 查询配置缺失** → ✅ 已补充
2. ❌ **缺少 ETL 模式配置文档** → ✅ 已创建
3. ❌ **缺少视图创建脚本** → ✅ 已创建
4. ❌ **缺少字段映射指南** → ✅ 已创建

### 4.2 完善的功能

1. ✅ 8 个数据源的完整查询配置
2. ✅ 独立的 HikariCP 连接池
3. ✅ 增量同步支持（通过 `updated_at` 参数）
4. ✅ SQL 类型自动转换
5. ✅ 字段映射（中文 → 英文）
6. ✅ 连接测试和健康检查
7. ✅ 资源清理（@PreDestroy）
8. ✅ SSRF 防护（白名单）

---

## 五、注意事项

### 5.1 ETL 模式限制

1. **不支持动态参数查询**
   - `fetchTeachingPlanCourses(planId)` - 返回空列表
   - `fetchStudentSchedules(studentId, ...)` - 返回空列表
   - 如需支持，需在配置中添加专门的参数化查询

2. **依赖学校数据库结构**
   - 必须有对应的视图或表
   - 必须有 `updated_at` 字段
   - 字段名必须匹配或配置映射

3. **需要数据库权限**
   - 至少需要 SELECT 权限
   - 建议使用只读账号

### 5.2 安全建议

1. **使用只读账号**
   ```sql
   CREATE USER 'langhack_readonly'@'%' IDENTIFIED BY 'strong_password';
   GRANT SELECT ON school_db.v_* TO 'langhack_readonly'@'%';
   ```

2. **配置 IP 白名单**
   - 学校数据库只允许后端服务器 IP 访问

3. **使用环境变量**
   - 不要将密码硬编码在配置文件中

4. **定期更换密码**
   - 建议每季度更换一次

---

## 六、ETL vs API 模式选择

| 场景 | 推荐模式 | 原因 |
|------|---------|------|
| 学校提供 API 接口 | API | 维护成本低，学校负责维护 |
| API 性能不足 | ETL | 直连数据库，性能更好 |
| 需要高频同步 | ETL | 实时性更好 |
| 数据量大 | ETL | 查询效率更高 |
| 无数据库权限 | API | 只需 API 密钥 |
| 学校数据库不稳定 | API | 降低耦合度 |

**建议**：优先使用 API 模式，只有在 API 无法满足需求时才切换到 ETL 模式。

---

## 七、后续工作（可选）

### 7.1 功能增强

- [ ] 支持动态参数查询（`fetchTeachingPlanCourses`, `fetchStudentSchedules`）
- [ ] 支持其他数据库（Oracle, SQL Server, PostgreSQL）
- [ ] 支持自定义字段转换器
- [ ] 支持查询结果缓存

### 7.2 监控和告警

- [ ] 添加 ETL 连接池监控
- [ ] 添加查询性能监控
- [ ] 添加同步失败告警

### 7.3 文档完善

- [ ] 添加 ETL 模式性能调优指南
- [ ] 添加 ETL 模式故障排查案例
- [ ] 添加 ETL 模式最佳实践

---

## 八、相关文件清单

### 8.1 代码文件

- `backend/src/main/java/com/english/config/SchoolDataProperties.java` - 配置类（已修改）
- `backend/src/main/java/com/english/service/impl/SchoolDataEtlClientImpl.java` - ETL 客户端实现
- `backend/src/main/java/com/english/service/impl/SchoolDataApiClientDelegate.java` - 路由委托
- `backend/src/main/java/com/english/service/impl/SchoolDataSyncServiceImpl.java` - 同步服务
- `backend/src/main/java/com/english/service/impl/SchoolDataFetchServiceImpl.java` - 数据获取服务

### 8.2 文档文件

- `docs/ETL_MODE_SETUP.md` - ETL 模式配置指南（新建）
- `docs/etl/create_views.sql` - 视图创建脚本（新建）
- `docs/etl/field_mapping_guide.md` - 字段映射指南（新建）
- `CLAUDE.md` - 项目文档（已更新）

### 8.3 配置文件

- `docker/.env.example` - 环境变量示例（已有 ETL 配置）
- `backend/src/main/resources/application.yml` - 应用配置（可选配置 ETL）

---

## 九、测试建议

### 9.1 单元测试

建议添加以下测试：

```java
// SchoolDataEtlClientImplTest.java
@Test
public void testFetchTeacherCourses() {
    // 测试 teacher_courses 查询
}

@Test
public void testConnectionPool() {
    // 测试连接池管理
}

@Test
public void testIncrementalQuery() {
    // 测试增量查询
}
```

### 9.2 集成测试

```bash
# 1. 测试 ETL 连接
# 2. 测试全量同步
# 3. 测试增量同步
# 4. 测试字段映射
# 5. 测试错误处理
```

---

**完成状态**: ✅ ETL 模式实现已完善，可以投入使用

**下一步**: 根据学校实际提供的数据库结构，调整视图创建脚本和字段映射配置
