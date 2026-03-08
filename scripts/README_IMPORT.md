# 班级和学生导入脚本使用说明

## 功能说明

这个脚本用于：
1. 创建一个新班级
2. 从Excel文件中读取学生名单
3. 为每个学生自动创建用户账号（如果不存在）
4. 将学生关联到指定班级

## Excel文件格式要求

Excel文件应该包含以下列（第2行为表头）：
- **学号**: 学生的学号
- **姓名**: 学生的真实姓名
- **班级**: 学生所在班级（可选，仅用于参考）

示例格式：
```
| 序号 | 班级    | 学号          | 姓名   |
|------|---------|---------------|--------|
| 1    | 25土管3 | 2025112250360 | 陈子硕 |
| 2    | 25土管3 | 2025112250603 | 韩悦   |
```

## 使用方法

### 基本用法

```bash
python3 scripts/import_class_students.py \
  --excel "25土管名单新.xls" \
  --class-name "25土管3班" \
  --course-id 1 \
  --teacher-id 1
```

### 完整参数说明

```bash
python3 scripts/import_class_students.py \
  --excel "Excel文件路径" \
  --class-name "班级名称" \
  --course-id 课程ID \
  --teacher-id 教师ID \
  --description "班级描述（可选）" \
  --db-host localhost \
  --db-port 3306 \
  --db-user root \
  --db-password 数据库密码 \
  --db-name english_learning
```

### 参数说明

| 参数 | 必需 | 默认值 | 说明 |
|------|------|--------|------|
| `--excel` | 是 | - | Excel文件路径 |
| `--class-name` | 是 | - | 要创建的班级名称 |
| `--course-id` | 是 | - | 课程ID（需要在数据库中已存在） |
| `--teacher-id` | 是 | - | 教师ID（需要在数据库中已存在） |
| `--description` | 否 | "" | 班级描述 |
| `--db-host` | 否 | localhost | 数据库主机地址 |
| `--db-port` | 否 | 3306 | 数据库端口 |
| `--db-user` | 否 | root | 数据库用户名 |
| `--db-password` | 否 | Dao030916 | 数据库密码 |
| `--db-name` | 否 | english_learning | 数据库名称 |

## 使用示例

### 示例1：导入25土管名单

```bash
python3 scripts/import_class_students.py \
  --excel "25土管名单新.xls" \
  --class-name "2025级土地资源管理3班" \
  --course-id 1 \
  --teacher-id 1 \
  --description "2025级土地资源管理专业3班"
```

### 示例2：导入25动医名单

```bash
python3 scripts/import_class_students.py \
  --excel "25动医7新.xls" \
  --class-name "2025级动物医学7班" \
  --course-id 1 \
  --teacher-id 1 \
  --description "2025级动物医学专业7班"
```

## 用户账号规则

### 用户名生成规则
- 格式：`student_{学号}`
- 示例：学号为 `2025112250360` 的学生，用户名为 `student_2025112250360`

### 默认密码规则
- 使用学号的后6位作为默认密码
- 示例：学号为 `2025112250360`，默认密码为 `250360`
- 如果学号少于6位，则使用完整学号作为密码

### 用户角色
- 所有导入的用户角色为：`student`
- 状态为：`active`

## 注意事项

1. **课程ID和教师ID必须存在**：在运行脚本前，确保数据库中已经存在对应的课程和教师记录

2. **班级名称唯一性**：如果班级名称已存在，脚本会使用现有班级而不是创建新班级

3. **学生去重**：如果学生已经存在（根据用户名判断），脚本会使用现有用户而不是创建新用户

4. **密码安全**：
   - 当前脚本使用SHA256哈希密码（简化版）
   - 生产环境建议使用BCrypt（与Spring Security一致）
   - 建议学生首次登录后修改密码

5. **数据备份**：运行脚本前建议备份数据库

## 查看导入结果

### 查看创建的班级
```sql
SELECT * FROM classes WHERE name = '班级名称';
```

### 查看班级中的学生
```sql
SELECT u.id, u.username, u.real_name, sc.join_time
FROM users u
JOIN student_classes sc ON u.id = sc.student_id
WHERE sc.class_id = 班级ID;
```

### 查看学生数量
```sql
SELECT COUNT(*) as student_count
FROM student_classes
WHERE class_id = 班级ID;
```

## 常见问题

### Q: 如何获取课程ID？
A: 执行SQL查询：
```sql
SELECT id, name FROM courses;
```

### Q: 如何获取教师ID？
A: 执行SQL查询：
```sql
SELECT id, username, real_name FROM users WHERE role = 'teacher';
```

### Q: 脚本执行失败怎么办？
A: 
1. 检查数据库连接参数是否正确
2. 确认课程ID和教师ID存在
3. 检查Excel文件格式是否正确
4. 查看错误信息，根据提示修复问题

### Q: 如何批量导入多个班级？
A: 可以编写一个shell脚本循环调用：
```bash
#!/bin/bash
python3 scripts/import_class_students.py --excel "25土管名单新.xls" --class-name "25土管3班" --course-id 1 --teacher-id 1
python3 scripts/import_class_students.py --excel "25动医7新.xls" --class-name "25动医7班" --course-id 1 --teacher-id 1
```

## 依赖安装

运行脚本前需要安装以下Python包：

```bash
pip3 install pandas openpyxl xlrd pymysql
```

## 技术支持

如有问题，请检查：
1. Python版本（建议3.7+）
2. 数据库连接
3. Excel文件格式
4. 依赖包是否正确安装
