# 数据库初始化 - 简易指南

## 方法1：使用MySQL Workbench（推荐，最简单）

1. 打开MySQL Workbench
2. 连接到你的数据库（root / 962464Yyy）
3. 选择数据库：
   ```sql
   USE english_learning;
   ```
4. 点击菜单：File → Open SQL Script
5. 选择文件：
   ```
   C:\Users\COLORFUL\Desktop\english-learning-full\backend\src\main\resources\db\migration\weakness_analysis_system.sql
   ```
6. 点击闪电图标⚡执行
7. 完成！

## 方法2：使用MySQL命令行客户端

1. 打开"开始菜单" → 搜索"MySQL Command Line Client"
2. 输入密码：`962464Yyy`
3. 切换数据库：
   ```sql
   USE english_learning;
   ```
4. 执行source命令：
   ```sql
   source C:/Users/COLORFUL/Desktop/english-learning-full/backend/src/main/resources/db/migration/weakness_analysis_system.sql
   ```
5. 等待执行完成
6. 验证：
   ```sql
   SHOW TABLES;
   SELECT COUNT(*) FROM knowledge_points;
   ```

## 方法3：使用PowerShell（推荐）

打开PowerShell，执行：

```powershell
cd C:\Users\COLORFUL\Desktop\english-learning-full

Get-Content "backend\src\main\resources\db\migration\weakness_analysis_system.sql" | mysql -u root -p962464Yyy english_learning

# 验证
mysql -u root -p962464Yyy -e "USE english_learning; SELECT COUNT(*) as 知识点数量 FROM knowledge_points;"
```

## 方法4：分步手动创建（如果其他方法都失败）

如果以上方法都不行，可以手动复制SQL内容：

1. 用文本编辑器打开：
   ```
   backend\src\main\resources\db\migration\weakness_analysis_system.sql
   ```

2. 复制所有SQL语句

3. 在MySQL Workbench或命令行中粘贴并执行

## 验证是否成功

执行以下SQL查看结果：

```sql
USE english_learning;

-- 查看所有表
SHOW TABLES;

-- 查看知识点数量（应该是29个）
SELECT COUNT(*) FROM knowledge_points;

-- 查看知识点列表
SELECT id, name, category, exam_type, level FROM knowledge_points;
```

## 预期结果

成功后你应该看到：
- ✅ 5个新表被创建
- ✅ knowledge_points表有29条记录
- ✅ 包含5个一级分类、24个二级分类

一级分类：
1. 词汇基础
2. 语法基础
3. 听力技巧
4. 阅读理解
5. 写作翻译

## 如果遇到错误

### 错误1：Table already exists
**原因**：表已经存在
**解决**：
```sql
DROP TABLE IF EXISTS learning_suggestions;
DROP TABLE IF EXISTS weakness_analysis;
DROP TABLE IF EXISTS answer_records;
DROP TABLE IF EXISTS questions;
DROP TABLE IF EXISTS knowledge_points;
```
然后重新执行脚本

### 错误2：Access denied
**原因**：密码错误或权限不足
**解决**：确认root密码是 `962464Yyy`

### 错误3：Unknown database 'english_learning'
**原因**：数据库不存在
**解决**：
```sql
CREATE DATABASE IF NOT EXISTS english_learning DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
```

## 成功后的下一步

1. 启动后端服务
2. 刷新浏览器
3. 访问"知识点管理"页面，应该能看到29个知识点
4. 访问"题库管理"页面，可以开始添加题目
