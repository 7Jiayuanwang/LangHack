# 班级和学生导入工具 - 快速开始指南

## ✅ 已完成的工作

已创建完整的班级和学生导入工具，包括：

1. **主导入脚本** (`import_class_students.py`) - 支持班级过滤
2. **测试脚本** (`test_import.py`) - 验证Excel文件
3. **批量导入脚本** (`import_example.sh`) - 一键导入多个班级
4. **详细文档** (`README_IMPORT.md`) - 完整使用说明

## 🚀 三步快速开始

### 步骤1: 安装依赖

```bash
pip3 install pandas openpyxl xlrd pymysql bcrypt
```

### 步骤2: 测试Excel文件

```bash
python3 scripts/test_import.py "25土管名单新.xls"
```

**测试结果**:
- ✅ 共49条有效学生记录
- ✅ 25土管3班: 27人
- ✅ 25土管4班: 22人

### 步骤3: 执行导入

#### 方案A: 使用批量导入脚本（推荐）

```bash
./scripts/import_example.sh
```

这个脚本会自动：
- 导入25土管3班（27人）
- 导入25土管4班（22人）
- 显示验证命令

#### 方案B: 手动分别导入

**导入25土管3班**:
```bash
python3 scripts/import_class_students.py \
  --excel "25土管名单新.xls" \
  --class-name "2025级土地资源管理3班" \
  --course-id 1 \
  --teacher-id 1 \
  --description "2025级土地资源管理专业3班" \
  --filter-class "25土管3"
```

**导入25土管4班**:
```bash
python3 scripts/import_class_students.py \
  --excel "25土管名单新.xls" \
  --class-name "2025级土地资源管理4班" \
  --course-id 1 \
  --teacher-id 1 \
  --description "2025级土地资源管理专业4班" \
  --filter-class "25土管4"
```

## 📊 导入数据预览

### 25土管3班学生示例
| 学号 | 姓名 | 用户名 | 默认密码 |
|------|------|--------|----------|
| 2025112250360 | 陈子硕 | student_2025112250360 | 250360 |
| 2025112250603 | 韩悦 | student_2025112250603 | 250603 |
| 2025112250732 | 陈俊蕾 | student_2025112250732 | 250732 |

### 25土管4班学生示例
| 学号 | 姓名 | 用户名 | 默认密码 |
|------|------|--------|----------|
| 2025112250361 | 范晓蒙 | student_2025112250361 | 250361 |
| 2025112250362 | 赵祥瑞 | student_2025112250362 | 250362 |
| 2025112250364 | 高云帅 | student_2025112250364 | 250364 |

## 🔍 验证导入结果

### 查看创建的班级
```sql
SELECT * FROM classes WHERE name LIKE '%土地资源管理%';
```

### 查看班级学生数量
```sql
SELECT 
    c.name as class_name,
    COUNT(*) as student_count
FROM student_classes sc
JOIN classes c ON sc.class_id = c.id
WHERE c.name LIKE '%土地资源管理%'
GROUP BY c.name;
```

**预期结果**:
- 2025级土地资源管理3班: 27人
- 2025级土地资源管理4班: 22人

### 查看所有学生
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

## ⚙️ 新增功能：班级过滤

现在支持使用 `--filter-class` 参数从包含多个班级的Excel文件中只导入特定班级的学生。

### 示例

```bash
# 只导入25土管3班的学生
python3 scripts/import_class_students.py \
  --excel "25土管名单新.xls" \
  --class-name "2025级土地资源管理3班" \
  --course-id 1 \
  --teacher-id 1 \
  --filter-class "25土管3"
```

**输出示例**:
```
班级过滤: '25土管3'
  原始记录数: 49
  过滤后记录数: 27
  已过滤掉: 22 条记录

开始导入 27 个学生...
```

## ⚠️ 重要提示

### 1. 数据库准备

在运行导入前，确保：
- ✅ 数据库连接正常
- ✅ 课程ID存在（默认使用1）
- ✅ 教师ID存在（默认使用1）

**查询课程ID**:
```sql
SELECT id, name FROM courses;
```

**查询教师ID**:
```sql
SELECT id, username, real_name FROM users WHERE role = 'teacher';
```

### 2. 密码安全

- ✅ 使用BCrypt哈希（如果已安装bcrypt）
- ⚠️ 默认密码为学号后6位
- 📌 建议学生首次登录后修改密码

### 3. 数据备份

**强烈建议在导入前备份数据库**:
```bash
mysqldump -u root -p english_learning > backup_$(date +%Y%m%d_%H%M%S).sql
```

## 📁 文件说明

| 文件 | 说明 |
|------|------|
| `scripts/import_class_students.py` | 主导入脚本，支持班级过滤 |
| `scripts/test_import.py` | 测试脚本，验证Excel文件格式 |
| `scripts/import_example.sh` | 批量导入示例脚本 |
| `scripts/README_IMPORT.md` | 详细使用文档 |
| `scripts/IMPORT_SUMMARY.md` | 功能总结文档 |
| `scripts/QUICK_START.md` | 本快速开始指南 |

## 🎯 下一步操作

1. ✅ **测试**: 运行 `test_import.py` 验证数据
2. ✅ **备份**: 备份数据库
3. ✅ **导入**: 运行 `import_example.sh` 或手动导入
4. ✅ **验证**: 使用SQL查询验证结果
5. ✅ **通知**: 告知学生用户名和默认密码

## 💡 提示

- 脚本支持重复运行，已存在的用户和班级关系不会重复创建
- 如果导入失败，可以安全地重新运行脚本
- 使用 `--filter-class` 参数可以精确控制导入哪些学生

## 📞 需要帮助？

查看详细文档：
```bash
cat scripts/README_IMPORT.md
```

查看帮助信息：
```bash
python3 scripts/import_class_students.py --help
```

## 🎉 完成！

现在你可以开始导入学生数据了！
