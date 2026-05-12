# 学校数据同步问题修复总结

## 修复完成的问题

### 1. ✅ Credits 字段缺失问题（P0 严重）

**问题描述**：
- 同步选课记录时，3,138 次失败
- 错误：`Column 'credits' cannot be null`
- 根因：创建骨架 `school_course` 记录时未提供必填字段

**修复方案**：
```java
// backend/src/main/java/com/english/service/impl/SchoolDataPersistServiceImpl.java:715-741
courseData.put("credits", BigDecimal.ZERO);  // 添加默认学分
courseData.put("totalHours", 0);             // 添加默认学时
```

**影响范围**：
- 修复后，骨架记录可以正常创建
- 后续通过正式同步补全完整数据
- 不影响已有的完整课程数据

---

### 2. ✅ 备份下载超时问题（P0 严重）

**问题描述**：
- 600MB+ 备份文件下载失败
- 60 秒后 Nginx 超时断开连接

**修复方案**：

**生产环境** (`nginx/nginx.conf`):
```nginx
# 备份下载接口（大文件，长超时）
location ~ ^/api/ops/backup/[0-9]+/download$ {
    proxy_read_timeout 600s;       # 10 分钟超时
    proxy_buffering off;           # 流式传输
    proxy_request_buffering off;
}
```

**开发环境** (`frontend/nginx.conf`):
```nginx
# 备份下载接口（大文件，长超时，流式传输）
location ~ ^/api/ops/backup/[0-9]+/download$ {
    proxy_read_timeout 600s;
    proxy_send_timeout 600s;
    proxy_buffering off;
    proxy_request_buffering off;
}
```

**关键改进**：
- 超时时间：60s → 600s（10 分钟）
- 流式传输：禁用缓冲，边读边传
- 正则匹配：精确匹配备份下载接口

**部署步骤**：
```bash
# 开发环境
docker compose restart frontend

# 生产环境
sudo nginx -t && sudo nginx -s reload
```

---

### 3. ✅ 备份预览截断严重问题（P1 重要）

**问题描述**：
- 预览只显示前 80 行，全是注释和 SET 语句
- 看不到实际的数据内容（CREATE/INSERT）

**修复方案**：

**后端优化** (`backend/src/main/java/com/english/service/impl/BackupServiceImpl.java:583-622`):
```java
// 智能跳过无用的头部内容
if (!foundDataSection) {
    String trimmed = line.trim();
    // 跳过注释、空行、SET 语句、USE 语句
    if (trimmed.isEmpty()
        || trimmed.startsWith("--")
        || trimmed.startsWith("/*")
        || trimmed.startsWith("SET ")
        || trimmed.startsWith("USE ")
        || trimmed.startsWith("/*!")) {
        skippedLines++;
        continue;
    }
    // 找到第一个有效语句（CREATE/INSERT/DROP/ALTER）
    if (trimmed.startsWith("CREATE ")
        || trimmed.startsWith("INSERT ")
        || trimmed.startsWith("DROP ")
        || trimmed.startsWith("ALTER ")) {
        foundDataSection = true;
        preview.append("-- [跳过了前 ").append(skippedLines).append(" 行注释和配置]\n\n");
    }
}
```

**前端优化** (`frontend/src/views/admin/Backups.vue`):
```javascript
// 增加预览限制
maxLines: 200,    // 80 → 200
maxChars: 50000   // 12000 → 50000

// 显示跳过信息
<span v-if="previewMeta.skippedLines > 0" class="preview-hint">
  已跳过前 {{ previewMeta.skippedLines }} 行注释和配置
</span>
<span v-if="previewMeta.truncated" class="preview-hint">
  已截断显示（共读取 {{ previewMeta.totalLinesRead }} 行，显示 {{ previewMeta.lineCount }} 行）
</span>
```

**改进效果**：
- ✅ 自动跳过无用的头部（注释、SET、USE 语句）
- ✅ 直接显示有价值的数据（CREATE TABLE、INSERT INTO）
- ✅ 显示跳过的行数，用户知道省略了什么
- ✅ 增加预览容量（200 行 / 50KB）

---

## 待修复的问题

### 4. ⏳ 从库权限问题（P1 重要）

**问题描述**：
- 57 次从库连接失败
- 错误：`Access denied; you need (at least one of) the SUPER, REPLICATION CLIENT privilege(s)`
- 影响：读写分离失效，主库压力增大

**修复方案**：
```sql
-- 在主库执行
GRANT REPLICATION CLIENT ON *.* TO 'replication_user'@'%';
FLUSH PRIVILEGES;
```

**验证方法**：
```bash
# 检查从库状态
docker compose exec mysql-slave mysql -u root -p -e "SHOW SLAVE STATUS\G"

# 查看日志
grep "Cannot connect to slave" logs/application.log
```

---

### 5. ⏳ 优化同步顺序（P2 优化）

**问题描述**：
- 当前顺序：选课记录 → 发现 teaching_class 不存在 → 创建骨架记录 → 失败
- 正确顺序：课程信息 → 教学班 → 选课记录

**修复方案**：
```java
// 链式拉取中，先调用 teacher_courses 端点获取完整课程信息
// 再同步选课记录
```

**预期效果**：
- 减少骨架记录创建
- 提高数据完整性
- 降低失败率

---

## 性能改进建议

### 1. 批量插入优化

**当前**：逐条处理，速度 ~82 条/分钟

**建议**：
```java
// 每 100 条批量提交
List<CourseSelection> batch = new ArrayList<>();
for (Map<String, Object> record : records) {
    batch.add(convertToCourseSelection(record));
    if (batch.size() >= 100) {
        courseSelectionMapper.insertBatch(batch);
        batch.clear();
    }
}
```

**预期**：速度提升至 ~500 条/分钟

---

### 2. 监控和告警

**建议**：
```java
// 添加 Prometheus 指标
@Counted(value = "school_data_sync_errors", extraTags = {"type", "credits_null"})
public void persistMappedRecord(String dataSource, Map<String, Object> mappedData) {
    // ...
}
```

**告警规则**：
- 错误率 > 5% 时触发
- 记录失败的 courseCode 到专门的错误表

---

## 部署清单

### 开发环境

```bash
# 1. 重启后端（应用 credits 修复）
docker compose restart backend

# 2. 重启前端（应用 Nginx 配置）
docker compose restart frontend

# 3. 验证
docker compose logs -f backend | grep "credits"
docker compose logs -f frontend | grep "backup.*download"
```

### 生产环境

```bash
# 1. 备份当前配置
sudo cp /etc/nginx/nginx.conf /etc/nginx/nginx.conf.backup

# 2. 更新 Nginx 配置
sudo cp nginx/nginx.conf /etc/nginx/nginx.conf
sudo nginx -t && sudo nginx -s reload

# 3. 部署后端代码
cd /path/to/langhack
git pull origin master
cd backend && mvn clean package -DskipTests
sudo systemctl restart langhack-backend

# 4. 验证
sudo tail -f /var/log/nginx/access.log | grep "backup.*download"
sudo journalctl -u langhack-backend -f | grep "credits"
```

---

## 验证测试

### 1. Credits 修复验证

```bash
# 触发一次选课记录同步
curl -X POST "http://localhost:8080/api/admin/sync/course-selection?studentId=2023112101479" \
  -H "Authorization: Bearer YOUR_TOKEN"

# 检查日志，应该没有 "Column 'credits' cannot be null" 错误
docker compose logs backend | grep "credits cannot be null"
```

### 2. 备份下载验证

```bash
# 下载一个大备份文件（> 100MB）
# 观察下载是否能持续超过 60 秒

# 检查 Nginx 日志，rt（请求时间）应该 > 60
tail -f /var/log/nginx/access.log | grep "backup.*download"
# 示例：rt=120.5 表示请求耗时 120.5 秒
```

### 3. 备份预览验证

```bash
# 访问运维面板 → 数据备份 → 查看备份详情
# 应该看到：
# - "已跳过前 XXX 行注释和配置"
# - 直接显示 CREATE TABLE 或 INSERT INTO 语句
# - 不再是一堆 SET 语句
```

---

## 相关文档

- `LOG_ANALYSIS_20260510.md` - 日志分析报告
- `BACKUP_DOWNLOAD_FIX.md` - 备份下载修复详细说明
- `docs/SCHOOL_DATA_FIX.md` - 学校数据集成问题修复文档

---

**修复日期**：2026-05-10  
**修复人员**：Claude Code  
**验证状态**：待部署验证  
**下一步**：部署到开发环境测试，验证通过后部署到生产环境
