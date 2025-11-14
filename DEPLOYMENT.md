# 部署指南

## 前提条件
- MySQL 8.0已安装并运行
- JDK 11+已安装
- Node.js 16+已安装

## 数据库初始化

### 1. 执行薄弱点分析系统数据库脚本

```bash
# 在本地或云服务器上执行
mysql -u root -p english_learning < backend/src/main/resources/db/migration/weakness_analysis_system.sql
```

这个脚本会创建以下表：
- `knowledge_points` - 知识点表（已包含29个CET知识点）
- `questions` - 题目表
- `answer_records` - 答题记录表
- `weakness_analysis` - 薄弱点分析结果表
- `learning_suggestions` - 学习建议表

### 2. 验证表是否创建成功

```sql
USE english_learning;
SHOW TABLES;

-- 查看知识点数据
SELECT COUNT(*) FROM knowledge_points;
-- 应该返回 29（预置的CET知识点）
```

## 后端部署

### 本地开发

```bash
cd backend
mvn spring-boot:run
```

### 云服务器部署

```bash
# 1. 构建项目
cd backend
mvn clean package -DskipTests

# 2. 上传jar包到服务器
scp target/english-learning-platform-1.0.0.jar root@101.42.92.220:/root/

# 3. 在服务器上运行
java -jar english-learning-platform-1.0.0.jar

# 或者使用systemd服务（推荐）
# 参考之前创建的 /etc/systemd/system/english-learning.service
sudo systemctl restart english-learning
```

## 前端部署

### 本地开发

```bash
cd frontend
npm install
npm run dev
# 访问 http://localhost:3000
```

### 云服务器部署

```bash
# 1. 构建
cd frontend
npm install
npm run build

# 2. 上传dist目录到服务器
scp -r dist/* root@101.42.92.220:/usr/share/nginx/html/

# 3. 重启Nginx
# 在服务器上执行
sudo systemctl reload nginx
```

## 验证部署

### 1. 检查后端API

```bash
# 测试知识点API
curl http://101.42.92.220:8080/api/knowledge-points

# 测试题目API
curl http://101.42.92.220:8080/api/questions?page=1&size=10
```

### 2. 访问前端页面

打开浏览器访问：
- 本地：http://localhost:3000
- 云服务器：http://101.42.92.220

登录后访问管理员端：
- 题库管理：/admin/questions
- 知识点管理：/admin/knowledge-points

## 常见问题

### Q1: 题库管理页面显示"请求地址不存在"

**原因**：数据库表未初始化

**解决**：
```bash
mysql -u root -p962464Yyy english_learning < backend/src/main/resources/db/migration/weakness_analysis_system.sql
```

### Q2: 页面空白或报错

**原因**：后端服务未运行

**解决**：
```bash
# 检查后端是否运行
ps aux | grep java

# 如果没运行，启动它
cd backend
mvn spring-boot:run
```

### Q3: 知识点列表为空

**原因**：SQL脚本中的INSERT语句未执行成功

**验证**：
```sql
SELECT * FROM knowledge_points LIMIT 5;
```

**解决**：重新执行SQL脚本

## 数据库表结构

### knowledge_points（知识点表）
```sql
id, name, code, category, exam_type, level, parent_id, description, importance, created_time
```

### questions（题目表）
```sql
id, title, question_type, difficulty, exam_type, exam_level, options,
correct_answer, explanation, knowledge_point_ids, score, created_by,
created_time, updated_time, deleted
```

### answer_records（答题记录表）
```sql
id, student_id, question_id, student_answer, is_correct, time_spent, answer_time, error_type
```

## 快速初始化脚本

```bash
#!/bin/bash
# 一键部署脚本

echo "=== 初始化数据库 ==="
mysql -u root -p962464Yyy english_learning < backend/src/main/resources/db/migration/weakness_analysis_system.sql

echo "=== 构建后端 ==="
cd backend
mvn clean package -DskipTests

echo "=== 构建前端 ==="
cd ../frontend
npm install
npm run build

echo "=== 部署完成 ==="
echo "请手动启动后端服务和Nginx"
```

保存为 `deploy.sh`，然后执行：
```bash
chmod +x deploy.sh
./deploy.sh
```
