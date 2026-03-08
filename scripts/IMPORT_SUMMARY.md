# 班级和学生导入工具

## 📋 概述

已创建完整的班级和学生导入工具，用于从Excel文件批量导入学生数据到数据库。

## 📁 文件清单

### 1. 主导入脚本
**文件**: `scripts/import_class_students.py`

功能：
- ✅ 创建班级
- ✅ 从Excel读取学生名单
- ✅ 自动创建用户账号
- ✅ 关联学生到班级
- ✅ 支持BCrypt密码哈希（推荐）
- ✅ SHA256备选方案（如果bcrypt不可用）

### 2. 测试脚本
**文件**: `scripts/test_import.py`

功能：
- ✅ 验证Excel文件格式
- ✅ 预览将要导入的数据
- ✅ 显示生成的用户名和密码
- ✅ 不实际写入数据库（安全测试）

### 3. 快速导入示例
**文件**: `scripts/import_example.sh`

功能：
- ✅ 一键导入25土管3班
- ✅ 包含查询验证命令

### 4. 详细文档
**文件**: `scripts/README_IMPORT.md`

包含：
- ✅ 完整使用说明
- ✅ 参数详解
- ✅ 使用示例
- ✅ 常见问题解答

## 🚀 快速开始

### 步骤1: 安装依赖

```bash
pip3 install pandas openpyxl xlrd pymysql bcrypt
```

### 步骤2: 测试Excel文件

```bash
python3 scripts/test_import.py "25土管名单新.xls"
```

测试结果显示：
- ✅ 文件格式正确
- ✅ 共49条有效学生记录
- ✅ 包含25土管3班和25土管4班的学生

### 步骤3: 执行导入

#### 导入25土管3班（仅包含25土管3班的学生）

```bash
python3 scripts/import_class_students.py \
  --excel "25土管名单新.xls" \
  --class-name "2025级土地资源管理3班" \
  --course-id 1 \
  --teacher-id 1 \
  --description "2025级土地资源管理专业3班"
```

#### 导入25土管4班（仅包含25土管4班的学生）

```bash
python3 scripts/import_class_students.py \
  --excel "25土管名单新.xls" \
  --class-name "2025级土地资源管理4班" \
  --course-id 1 \
  --teacher-id 1 \
  --description "2025级土地资源管理专业4班"
```

**注意**: 脚本会自动识别Excel中的"班级"列，但会导入所有学生。如果需要按班级分别导入，需要先分别准备不同的Excel文件，或者修改脚本添加班级过滤功能。

## 📊 Excel文件分析

从`25土管名单新.xls`文件中检测到：

### 学生分布
- **23草学3班**: 3人
- **23草学4班**: 1人
- **24草学3班**: 1人
- **25土管3班**: 27人
- **25土管4班**: 22人

### 数据格式
```
| 序号 | 班级    | 学号          | 姓名   |
|------|---------|---------------|--------|
| 1    | 23草学3 | 2023122049005 | 李文辉 |
| 6    | 25土管3 | 2025112250360 | 陈子硕 |
```

## 🔐 用户账号规则

### 用户名生成
- 格式: `student_{学号}`
- 示例: `student_2025112250360`

### 默认密码
- 规则: 学号后6位
- 示例: 学号`2025112250360` → 密码`250360`

### 密码哈希
- **优先**: BCrypt（与Spring Security兼容）
- **备选**: SHA256（如果bcrypt不可用）

## ⚠️ 重要提示

### 1. 数据库准备
在运行导入前，确保：
- ✅ 数据库连接正常
- ✅ 课程ID存在（默认使用course_id=1）
- ✅ 教师ID存在（默认使用teacher_id=1）

查询现有课程：
```sql
SELECT id, name FROM courses;
```

查询现有教师：
```sql
SELECT id, username, real_name FROM users WHERE role = 'teacher';
```

### 2. 班级过滤
当前脚本会导入Excel中的**所有学生**。如果Excel包含多个班级的学生，需要：

**方案A**: 分别准备不同班级的Excel文件
**方案B**: 修改脚本添加班级过滤（见下方增强功能）

### 3. 数据备份
建议在导入前备份数据库：
```bash
mysqldump -u root -p english_learning > backup_$(date +%Y%m%d_%H%M%S).sql
```

## 🔧 可选增强功能

### 添加班级过滤功能

如果需要从包含多个班级的Excel中只导入特定班级的学生，可以添加`--filter-class`参数：

```python
# 在import_students_from_excel方法中添加过滤逻辑
if filter_class:
    df = df[df['班级'] == filter_class]
```

使用示例：
```bash
python3 scripts/import_class_students.py \
  --excel "25土管名单新.xls" \
  --class-name "2025级土地资源管理3班" \
  --filter-class "25土管3" \
  --course-id 1 \
  --teacher-id 1
```

## 📝 验证导入结果

### 查看创建的班级
```sql
SELECT * FROM classes WHERE name LIKE '%土地资源管理%';
```

### 查看班级学生
```sql
SELECT 
    c.name as class_name,
    u.username,
    u.real_name,
    sc.join_time
FROM users u
JOIN student_classes sc ON u.id = sc.student_id
JOIN classes c ON sc.class_id = c.id
WHERE c.name LIKE '%土地资源管理%'
ORDER BY c.name, u.real_name;
```

### 统计学生数量
```sql
SELECT 
    c.name as class_name,
    COUNT(*) as student_count
FROM student_classes sc
JOIN classes c ON sc.class_id = c.id
WHERE c.name LIKE '%土地资源管理%'
GROUP BY c.name;
```

## 🎯 下一步操作

1. **测试导入**: 先使用`test_import.py`验证数据
2. **备份数据库**: 确保数据安全
3. **执行导入**: 运行实际导入命令
4. **验证结果**: 使用SQL查询验证
5. **通知学生**: 告知学生用户名和默认密码
6. **要求修改密码**: 建议学生首次登录后修改密码

## 📞 技术支持

如遇问题，请检查：
1. Python版本（建议3.7+）
2. 依赖包是否安装完整
3. 数据库连接参数
4. Excel文件格式
5. 课程ID和教师ID是否存在

## 📄 相关文件

- `scripts/import_class_students.py` - 主导入脚本
- `scripts/test_import.py` - 测试脚本
- `scripts/import_example.sh` - 快速导入示例
- `scripts/README_IMPORT.md` - 详细文档
