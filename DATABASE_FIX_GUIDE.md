# 数据库错误修复指南

## 🚨 错误描述

**错误信息**: `Unknown column 'a.deleted' in 'where clause'`

**错误原因**: 数据库中缺少 `assignments` 表或该表缺少 `deleted` 字段

## 🛠️ 快速解决方案

### 方法1: 执行快速修复脚本（推荐）

1. **连接到你的数据库**
   ```bash
   # MySQL命令行
   mysql -u root -p
   use english_learning_platform;

   # 或使用数据库工具如 Navicat、phpMyAdmin 等
   ```

2. **执行快速修复脚本**
   ```sql
   -- 复制 QUICK_DATABASE_FIX.sql 文件中的内容并执行
   -- 或者直接在命令行执行：
   source /path/to/QUICK_DATABASE_FIX.sql;
   ```

3. **验证修复结果**
   ```sql
   -- 检查表是否存在
   SHOW TABLES LIKE 'assignments';

   -- 检查表结构
   DESCRIBE assignments;
   ```

### 方法2: 手动执行SQL

如果快速脚本不工作，请手动执行以下SQL：

```sql
-- 1. 创建任务表
CREATE TABLE IF NOT EXISTS assignments (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    title VARCHAR(255) NOT NULL COMMENT '任务标题',
    description TEXT COMMENT '任务描述',
    class_id BIGINT NOT NULL COMMENT '班级ID',
    unit_id BIGINT COMMENT '单元ID',
    teacher_id BIGINT NOT NULL COMMENT '教师ID',
    deadline DATETIME COMMENT '截止时间',
    status VARCHAR(20) DEFAULT 'active' COMMENT '状态',
    deleted INT DEFAULT 0 COMMENT '删除标记',
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_class_id (class_id),
    INDEX idx_teacher_id (teacher_id),
    INDEX idx_status (status),
    INDEX idx_deleted (deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='任务表';

-- 2. 创建学生任务表
CREATE TABLE IF NOT EXISTS student_assignments (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    assignment_id BIGINT NOT NULL COMMENT '任务ID',
    student_id BIGINT NOT NULL COMMENT '学生ID',
    completed_at DATETIME COMMENT '完成时间',
    time_spent INT DEFAULT 0 COMMENT '用时（分钟）',
    score INT COMMENT '得分',
    status VARCHAR(20) DEFAULT 'not_started' COMMENT '状态',
    deleted INT DEFAULT 0 COMMENT '删除标记',
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_assignment_id (assignment_id),
    INDEX idx_student_id (student_id),
    INDEX idx_status (status),
    INDEX idx_deleted (deleted),
    UNIQUE KEY uk_assignment_student (assignment_id, student_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='学生任务表';

-- 3. 创建单元表（如果不存在）
CREATE TABLE IF NOT EXISTS units (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    course_id BIGINT NOT NULL COMMENT '课程ID',
    name VARCHAR(255) NOT NULL COMMENT '单元名称',
    description TEXT COMMENT '单元描述',
    order_num INT DEFAULT 0 COMMENT '排序号',
    deleted INT DEFAULT 0 COMMENT '删除标记',
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_course_id (course_id),
    INDEX idx_deleted (deleted),
    INDEX idx_order_num (order_num)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='学习单元表';
```

## 🔧 数据库连接信息

根据项目配置，你的数据库连接信息通常在：

```
backend/src/main/resources/application.yml
```

或

```
backend/src/main/resources/application.properties
```

常见配置示例：
```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/english_learning_platform
    username: root
    password: your_password
```

## 📋 执行步骤详解

### 步骤1: 找到数据库连接信息

1. 打开 `backend/src/main/resources/application.yml`
2. 查找 `spring.datasource` 配置
3. 记录数据库名称、用户名、密码

### 步骤2: 连接数据库

**使用MySQL命令行**:
```bash
mysql -u [用户名] -p
[输入密码]
use [数据库名称];
```

**使用图形界面工具**:
- Navicat
- phpMyAdmin
- MySQL Workbench
- DBeaver

### 步骤3: 执行SQL脚本

**选项A: 复制粘贴执行**
1. 打开 `QUICK_DATABASE_FIX.sql` 文件
2. 复制所有内容
3. 在数据库工具中粘贴并执行

**选项B: 导入SQL文件**
```bash
# MySQL命令行
source /path/to/QUICK_DATABASE_FIX.sql;

# 或在mysql命令中
mysql -u root -p english_learning_platform < /path/to/QUICK_DATABASE_FIX.sql
```

### 步骤4: 验证修复

执行以下查询验证表创建成功：

```sql
-- 检查表是否存在
SHOW TABLES LIKE 'assignments';
SHOW TABLES LIKE 'student_assignments';
SHOW TABLES LIKE 'units';

-- 检查表结构
DESCRIBE assignments;

-- 应该看到以下字段：
-- id, title, description, class_id, unit_id, teacher_id, deadline, status, deleted, created_time, updated_time
```

## 🚀 修复后测试

1. **重启Spring Boot应用**
   ```bash
   # 在backend目录下
   mvn spring-boot:run
   ```

2. **测试教师端任务管理**
   - 访问教师端任务管理页面
   - 应该不再出现数据库错误
   - 可以正常查看任务列表

3. **测试API接口**
   ```bash
   # 测试获取任务列表
   curl -H "Authorization: Bearer [token]" \
        http://localhost:8080/api/assignments
   ```

## 🔍 故障排除

### 问题1: 外键约束错误

如果遇到外键错误，可能需要先确保相关表存在：

```sql
-- 检查必要的表是否存在
SHOW TABLES LIKE 'classes';
SHOW TABLES LIKE 'users';
SHOW TABLES LIKE 'courses';

-- 如果不存在，需要先创建这些基础表
```

### 问题2: 权限错误

如果遇到权限错误：

```sql
-- 确保用户有足够权限
GRANT ALL PRIVILEGES ON english_learning_platform.* TO 'your_username'@'localhost';
FLUSH PRIVILEGES;
```

### 问题3: 表已存在但结构不对

如果表已存在但缺少字段：

```sql
-- 检查现有表结构
DESCRIBE assignments;

-- 如果缺少deleted字段，添加它
ALTER TABLE assignments ADD COLUMN deleted INT DEFAULT 0;
ALTER TABLE assignments ADD INDEX idx_deleted (deleted);
```

## 📞 如果仍然有问题

如果按照以上步骤仍然无法解决问题，请提供：

1. 数据库版本：`SELECT VERSION();`
2. 当前数据库中的表列表：`SHOW TABLES;`
3. 错误的完整日志信息
4. 项目配置文件内容（数据库连接部分）

---

**修复完成后**，教师端任务管理功能应该可以正常使用，不再出现"Unknown column 'a.deleted'"错误！