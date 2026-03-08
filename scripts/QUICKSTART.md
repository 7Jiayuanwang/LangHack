# 快速开始 - 导入 iExplore 1 阅读材料

## 步骤 1: 安装依赖

```bash
cd /home/teruun/projects/learn-english
./scripts/install_dependencies.sh
```

或者手动安装：
```bash
pip3 install mysql-connector-python
```

## 步骤 2: 运行导入脚本

```bash
python3 scripts/import_book1_readings.py
```

## 预期输出

```
============================================================
  iExplore 1 阅读材料导入工具
============================================================

✓ 成功连接到数据库: english_learning

创建课程...
------------------------------------------------------------
✓ 创建课程: iExplore 1 (ID: 1)

找到 16 个阅读文件
============================================================

处理单元 1: Unit 1
------------------------------------------------------------
  ✓ 创建单元: Unit 1 (ID: 1)
    ✓ 创建文本: Brave new worlds (ID: 1)
    ✓ 创建文本: ... (ID: 2)

...

============================================================
  导入完成！
============================================================

统计信息:
  课程: iExplore 1 (ID: 1)
  单元数: 8
  文本数: 16

✓ 数据库连接已关闭
```

## 验证导入结果

### 方法 1: 使用 MySQL 命令行

```bash
mysql -u root -p english_learning
```

```sql
-- 查看课程
SELECT * FROM courses WHERE name = 'iExplore 1';

-- 查看单元
SELECT u.* FROM units u
JOIN courses c ON u.course_id = c.id
WHERE c.name = 'iExplore 1'
ORDER BY u.order_num;

-- 查看文本
SELECT t.id, u.name as unit, t.title, t.order_num
FROM texts t
JOIN units u ON t.unit_id = u.id
JOIN courses c ON u.course_id = c.id
WHERE c.name = 'iExplore 1' AND t.deleted = 0
ORDER BY u.order_num, t.order_num;
```

### 方法 2: 使用后端 API

启动后端服务：
```bash
cd backend
mvn spring-boot:run
```

访问 API：
- 获取所有课程: `http://localhost:8080/api/courses`
- 获取课程详情: `http://localhost:8080/api/courses/{courseId}`
- 获取单元列表: `http://localhost:8080/api/courses/{courseId}/units`

## 重新导入

如果需要重新导入，先删除现有数据：

```sql
-- 删除文本
DELETE t FROM texts t
JOIN units u ON t.unit_id = u.id
JOIN courses c ON u.course_id = c.id
WHERE c.name = 'iExplore 1';

-- 删除单元
DELETE u FROM units u
JOIN courses c ON u.course_id = c.id
WHERE c.name = 'iExplore 1';

-- 删除课程
DELETE FROM courses WHERE name = 'iExplore 1';
```

然后重新运行导入脚本。

## 故障排查

### 问题: ModuleNotFoundError: No module named 'mysql'

**解决方案**:
```bash
pip3 install mysql-connector-python
```

### 问题: 数据库连接失败

**解决方案**:
1. 检查 MySQL 服务是否运行: `sudo systemctl status mysql`
2. 检查数据库密码是否正确
3. 检查数据库是否存在: `mysql -u root -p -e "SHOW DATABASES;"`

### 问题: 找不到 JSON 文件

**解决方案**:
1. 确保在项目根目录执行脚本
2. 检查路径: `ls -la book1/readings/`

## 数据结构

### 课程 (courses)
- id: 课程ID
- name: "iExplore 1"
- description: 课程描述
- status: "active"

### 单元 (units)
- id: 单元ID
- course_id: 所属课程ID
- name: "Unit 1", "Unit 2", ...
- order_num: 排序号 (1, 2, 3, ...)

### 文本 (texts)
- id: 文本ID
- unit_id: 所属单元ID
- title: 文章标题
- content: 文章内容
- order_num: 排序号 (2, 4, ...)
- tags: "reading,iexplore"
- status: "active"
