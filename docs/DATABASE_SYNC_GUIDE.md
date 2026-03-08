# 数据库同步和分享指南

## 📋 概述

本文档提供了将本地数据库同步到服务器以及与团队成员分享数据的多种方案。

## 🎯 推荐方案对比

| 方案 | 适用场景 | 优点 | 缺点 |
|------|----------|------|------|
| **方案1: SQL导出文件** | 团队协作、版本控制 | 可追踪变更、易于分享 | 文件可能较大 |
| **方案2: 数据迁移脚本** | 生产环境部署 | 可重复执行、安全 | 需要编写脚本 |
| **方案3: 直接数据库复制** | 服务器同步 | 快速、完整 | 需要服务器访问权限 |
| **方案4: Docker数据卷** | 容器化部署 | 环境一致、易迁移 | 需要Docker环境 |

---

## 🚀 方案1: SQL导出文件（推荐用于团队协作）

### 优势
- ✅ 可以提交到Git进行版本控制
- ✅ 团队成员可以轻松导入
- ✅ 可以选择性导出数据
- ✅ 便于审查和回滚

### 步骤

#### 1.1 导出完整数据库

```bash
# 导出完整数据库（包含结构和数据）
mysqldump -u root -p english_learning > database_backup_$(date +%Y%m%d_%H%M%S).sql

# 或者导出到指定目录
mysqldump -u root -p english_learning > database/english_learning_full.sql
```

#### 1.2 导出仅数据（推荐）

```bash
# 只导出数据，不包含CREATE TABLE语句
mysqldump -u root -p --no-create-info english_learning > database/data_only.sql

# 导出特定表的数据
mysqldump -u root -p --no-create-info english_learning \
  users classes student_classes courses units texts > database/core_data.sql
```

#### 1.3 导出仅结构

```bash
# 只导出表结构，不包含数据
mysqldump -u root -p --no-data english_learning > database/schema.sql
```

#### 1.4 分表导出（推荐用于大型数据库）

```bash
# 创建导出目录
mkdir -p database/exports

# 导出用户和班级数据
mysqldump -u root -p --no-create-info english_learning \
  users classes student_classes > database/exports/users_and_classes.sql

# 导出课程和教学内容
mysqldump -u root -p --no-create-info english_learning \
  courses units texts text_exercises exercise_options > database/exports/course_content.sql

# 导出ECDICT词典数据
mysqldump -u root -p --no-create-info english_learning \
  ecdict_words > database/exports/ecdict_words.sql
```

### 导入方法

```bash
# 团队成员导入数据
mysql -u root -p english_learning < database/english_learning_full.sql

# 或分步导入
mysql -u root -p english_learning < database/schema.sql
mysql -u root -p english_learning < database/data_only.sql
```

---

## 🔄 方案2: 数据迁移脚本（推荐用于生产环境）

### 优势
- ✅ 可以处理数据转换和清理
- ✅ 支持增量更新
- ✅ 更安全，可以验证数据
- ✅ 适合自动化部署

### 创建迁移脚本

我们已经有了导入脚本，可以创建一个统一的部署脚本：

```bash
#!/bin/bash
# database/deploy.sh

echo "开始部署数据库..."

# 1. 导入课程数据
echo "导入课程数据..."
python3 scripts/import_book1_readings.py

# 2. 导入学生数据
echo "导入学生数据..."
python3 scripts/import_class_students.py \
  --excel "25土管名单新.xls" \
  --class-name "2025级土地资源管理3班" \
  --course-id 1 \
  --teacher-id 1 \
  --filter-class "25土管3"

# 3. 导入ECDICT词典（如果需要）
# python3 scripts/import_ecdict.py

echo "部署完成！"
```

---

## 🌐 方案3: 直接数据库复制到服务器

### 3.1 通过SSH隧道同步

```bash
# 从本地导出并直接导入到远程服务器
mysqldump -u root -p english_learning | \
  ssh user@your-server.com "mysql -u root -p remote_database_name"
```

### 3.2 使用rsync同步SQL文件

```bash
# 先导出到本地
mysqldump -u root -p english_learning > /tmp/english_learning.sql

# 传输到服务器
rsync -avz /tmp/english_learning.sql user@your-server.com:/tmp/

# SSH到服务器并导入
ssh user@your-server.com "mysql -u root -p english_learning < /tmp/english_learning.sql"
```

### 3.3 使用SCP传输

```bash
# 导出数据库
mysqldump -u root -p english_learning > english_learning.sql

# 传输到服务器
scp english_learning.sql user@your-server.com:/path/to/destination/

# SSH到服务器导入
ssh user@your-server.com
mysql -u root -p english_learning < /path/to/destination/english_learning.sql
```

---

## 📦 方案4: 使用Git管理数据库变更

### 4.1 创建数据库版本控制结构

```bash
# 创建数据库目录结构
mkdir -p database/{schema,migrations,seeds,backups}

# schema: 表结构
# migrations: 迁移脚本
# seeds: 种子数据
# backups: 备份文件（不提交到Git）
```

### 4.2 导出并提交到Git

```bash
# 导出表结构
mysqldump -u root -p --no-data english_learning > database/schema/schema.sql

# 导出核心数据（用户、班级等）
mysqldump -u root -p --no-create-info english_learning \
  users classes student_classes courses > database/seeds/core_data.sql

# 添加到Git
git add database/schema/schema.sql
git add database/seeds/core_data.sql
git commit -m "Add database schema and core data"
git push
```

### 4.3 团队成员同步

```bash
# 拉取最新代码
git pull

# 导入数据库
mysql -u root -p english_learning < database/schema/schema.sql
mysql -u root -p english_learning < database/seeds/core_data.sql
```

---

## 🎯 具体实施建议

### 对于你的情况，我推荐以下组合方案：

#### 第一步：创建数据库目录结构

```bash
mkdir -p database/{schema,seeds,exports,backups}
echo "database/backups/" >> .gitignore
echo "database/exports/" >> .gitignore
```

#### 第二步：导出当前数据

```bash
# 1. 导出表结构（提交到Git）
mysqldump -u root -p --no-data english_learning > database/schema/schema.sql

# 2. 导出核心配置数据（提交到Git）
mysqldump -u root -p --no-create-info english_learning \
  courses units > database/seeds/courses_and_units.sql

# 3. 导出用户和班级数据（可选择是否提交）
mysqldump -u root -p --no-create-info english_learning \
  users classes student_classes > database/seeds/users_and_classes.sql

# 4. 导出文本内容（提交到Git）
mysqldump -u root -p --no-create-info english_learning \
  texts text_exercises exercise_options > database/seeds/texts_and_exercises.sql

# 5. 完整备份（不提交到Git，用于服务器同步）
mysqldump -u root -p english_learning > database/backups/full_backup_$(date +%Y%m%d).sql
```

#### 第三步：提交到Git

```bash
git add database/schema/
git add database/seeds/
git add scripts/
git commit -m "Add database schema, seeds and import scripts"
git push
```

#### 第四步：服务器部署

```bash
# 方式A: 使用完整备份（快速）
scp database/backups/full_backup_*.sql user@server:/tmp/
ssh user@server "mysql -u root -p english_learning < /tmp/full_backup_*.sql"

# 方式B: 使用Git + 导入脚本（推荐）
# 在服务器上
git pull
mysql -u root -p english_learning < database/schema/schema.sql
mysql -u root -p english_learning < database/seeds/courses_and_units.sql
mysql -u root -p english_learning < database/seeds/texts_and_exercises.sql
python3 scripts/import_class_students.py --excel "25土管名单新.xls" ...
```

---

## 📝 最佳实践

### 1. 数据分类管理

- **结构数据** (`schema/`): 表结构，总是提交到Git
- **配置数据** (`seeds/`): 课程、单元等配置，提交到Git
- **用户数据** (`seeds/`): 根据隐私政策决定是否提交
- **大型数据** (`exports/`): ECDICT等大型数据，不提交到Git，使用其他方式分享
- **备份文件** (`backups/`): 完整备份，不提交到Git

### 2. .gitignore 配置

```bash
# 添加到 .gitignore
database/backups/
database/exports/
*.sql.gz
*.sql.zip
```

### 3. 自动化脚本

创建便捷的导出脚本：

```bash
#!/bin/bash
# database/export.sh

DATE=$(date +%Y%m%d_%H%M%S)

echo "导出数据库..."

# 导出表结构
mysqldump -u root -p --no-data english_learning > database/schema/schema.sql

# 导出种子数据
mysqldump -u root -p --no-create-info english_learning \
  courses units > database/seeds/courses_and_units.sql

mysqldump -u root -p --no-create-info english_learning \
  texts text_exercises exercise_options > database/seeds/texts_and_exercises.sql

# 完整备份
mysqldump -u root -p english_learning > database/backups/full_backup_$DATE.sql

echo "导出完成！"
echo "- 结构: database/schema/schema.sql"
echo "- 种子: database/seeds/"
echo "- 备份: database/backups/full_backup_$DATE.sql"
```

### 4. 团队协作流程

```bash
# 开发者A更新数据库
./database/export.sh
git add database/schema/ database/seeds/
git commit -m "Update database schema and seeds"
git push

# 开发者B同步数据库
git pull
./database/import.sh  # 导入脚本
```

---

## 🔒 安全注意事项

1. **敏感数据**: 不要将包含真实用户密码的SQL文件提交到Git
2. **大文件**: 使用Git LFS或其他方式处理大型SQL文件
3. **访问控制**: 确保服务器数据库有适当的访问控制
4. **备份**: 在同步前始终备份服务器数据库

---

## 📞 常见问题

### Q: SQL文件太大怎么办？
A: 
- 使用压缩: `gzip database.sql`
- 分表导出
- 使用Git LFS
- 大型数据（如ECDICT）提供下载链接而不是提交到Git

### Q: 如何处理数据冲突？
A: 
- 使用时间戳字段
- 实施数据版本控制
- 使用迁移脚本而不是直接SQL导入

### Q: 如何增量更新？
A: 
- 编写迁移脚本
- 使用版本号管理
- 记录已执行的迁移

---

## 🎯 快速开始

```bash
# 1. 创建目录结构
mkdir -p database/{schema,seeds,backups}

# 2. 导出数据
./database/export.sh  # 使用我们创建的脚本

# 3. 提交到Git
git add database/schema/ database/seeds/ scripts/
git commit -m "Add database and import scripts"
git push

# 4. 团队成员同步
git pull
./database/import.sh
```
