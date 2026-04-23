# Scripts 目录说明

本目录包含用于部署、更新、开发启动和数据导入的脚本。

## 📌 核心脚本职责

| 脚本 | 适用环境 | 用途 |
|------|----------|------|
| `deploy.sh` | Docker | 交互式部署、生成 `docker/.env`、启动容器与部署配置 |
| `auto-update.sh` | Docker / Local | 一键拉取更新；Docker 模式重建容器，本地模式重启 dev 进程 |
| `start.sh` | Local | 本地开发一键启动后端 + 前端 |
| `check-health.sh` | Docker | 检查后端、Redis、MySQL 主从与复制状态 |

## 📂 文件列表

### 班级和学生导入工具

| 文件 | 类型 | 说明 |
|------|------|------|
| `import_class_students.py` | Python | 从Excel导入班级和学生数据 ⭐ |
| `test_import.py` | Python | 测试Excel文件格式和预览数据 |
| `import_example.sh` | Bash | 批量导入示例脚本 |
| `QUICK_START.md` | 文档 | 快速开始指南 ⭐ **从这里开始** |
| `README_IMPORT.md` | 文档 | 详细使用文档 |
| `IMPORT_SUMMARY.md` | 文档 | 功能总结和技术说明 |

### 课程数据导入

| 文件 | 类型 | 说明 |
|------|------|------|
| `import_book1_readings.py` | Python | 导入book1阅读材料到数据库 |

---

## 🚀 班级和学生导入工具

### 快速开始

1. **安装依赖**
   ```bash
   pip3 install pandas openpyxl xlrd pymysql bcrypt
   ```

2. **测试Excel文件**
   ```bash
   python3 scripts/test_import.py "你的Excel文件.xls"
   ```

3. **执行导入**
   ```bash
   # 使用批量导入脚本（推荐）
   ./scripts/import_example.sh
   
   # 或手动导入
   python3 scripts/import_class_students.py \
     --excel "Excel文件.xls" \
     --class-name "班级名称" \
     --course-id 1 \
     --teacher-id 1 \
     --filter-class "25土管3"
   ```

### 主要功能

- ✅ 创建班级
- ✅ 从Excel读取学生名单
- ✅ 自动创建用户账号
- ✅ 关联学生到班级
- ✅ 支持班级过滤
- ✅ BCrypt密码哈希

### 详细文档

- **快速开始**: `QUICK_START.md` - 三步快速导入
- **详细文档**: `README_IMPORT.md` - 完整参数说明
- **功能总结**: `IMPORT_SUMMARY.md` - 技术细节

---

## 📚 课程数据导入

### 脚本列表

| 文件 | 类型 | 说明 |
|------|------|------|
| `import_book1_readings.py` | Python | 导入book1阅读材料到数据库 ⭐ |
| `import_book1_vocabulary.py` | Python | 导入book1词汇到数据库 ⭐ |

### 使用方法

#### 1. 导入阅读材料

```bash
python3 scripts/import_book1_readings.py
```

**功能**:
- 创建课程 "大学英语1"
- 创建 8 个单元 (Unit 1-8)
- 导入 16 个阅读文本

**结果**:
- 课程: 大学英语1
- 单元数: 8
- 文本数: 16

#### 2. 导入词汇

```bash
python3 scripts/import_book1_vocabulary.py
```

**功能**:
- 读取 book1/vocabulary 目录下的 JSON 文件
- 根据文件名匹配对应的阅读材料
- 导入词汇到 words 表
- 创建单元和词汇的关联

**结果**:
- 词汇总数: 642 个
- 分布在 8 个单元中
- 每个单元 56-95 个词汇不等

### 数据结构

#### 阅读材料 JSON 格式
```json
{
    "unit": "1-2 iExplore 1: Reading",
    "unit_number": "1",
    "title": "Brave new worlds",
    "content": "文章内容...",
    "url": "https://..."
}
```

#### 词汇 JSON 格式
```json
{
    "unit": "1-2 iExplore 1: Reading",
    "count": 47,
    "words": [
        {
            "word": "campus",
            "explanation": "n. [C] the land and buildings... 校园",
            "example": "We have rooms for 900 students on campus.",
            "phonetic": "campus.mp3"
        }
    ]
}
```

### 文件命名规则

- 阅读材料: `{单元号}-{阅读序号} {名称}.json`
  - 例: `1-2 iExplore 1 Reading.json`
- 词汇文件: `{单元号}-{阅读序号} {名称}_vocabulary.json`
  - 例: `1-2 iExplore 1 Reading_vocabulary.json`

### 导入顺序

**重要**: 必须先导入阅读材料，再导入词汇！

1. 先运行 `import_book1_readings.py` 创建课程、单元和文本
2. 再运行 `import_book1_vocabulary.py` 导入词汇并关联到单元

### 验证导入结果

```sql
-- 查看课程
SELECT * FROM courses WHERE name = '大学英语1';

-- 查看单元和词汇数
SELECT 
    u.name AS 单元,
    COUNT(DISTINCT t.id) AS 文本数,
    COUNT(DISTINCT uw.word_id) AS 词汇数
FROM units u
LEFT JOIN texts t ON u.id = t.unit_id AND t.deleted = 0
LEFT JOIN unit_words uw ON u.id = uw.unit_id
JOIN courses c ON u.course_id = c.id
WHERE c.name = '大学英语1'
GROUP BY u.id, u.name
ORDER BY u.order_num;
```

---

### 1. import_book1_readings.py

**功能**: 导入 book1/readings 目录下的 JSON 阅读材料到数据库

**使用方法**:
```bash
# 在项目根目录执行
python3 scripts/import_book1_readings.py
```

**功能说明**:
1. 创建课程 "iExplore 1"
2. 根据文件名自动创建单元（Unit 1, Unit 2, ...）
3. 将每个 JSON 文件导入为文本（Text）

**文件命名规则**:
- 格式: `{单元号}-{阅读序号} {阅读名称}.json`
- 示例: `1-2 iExplore 1 Reading.json`
  - 单元号: 1
  - 阅读序号: 2
  - 阅读名称: iExplore 1 Reading

**数据结构**:
```json
{
    "unit": "1-2 iExplore 1: Reading",
    "unit_number": "1",
    "title": "Brave new worlds",
    "content": "文章内容...",
    "url": "https://..."
}
```

**导入流程**:
1. 连接数据库
2. 创建或查找课程 "iExplore 1"
3. 遍历 book1/readings 目录下的所有 JSON 文件
4. 按单元号分组
5. 为每个单元创建 Unit 记录
6. 为每个阅读文件创建 Text 记录
7. 提交事务并显示统计信息

**输出示例**:
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
    ✓ 创建文本: Another reading (ID: 2)

处理单元 2: Unit 2
------------------------------------------------------------
  ✓ 创建单元: Unit 2 (ID: 2)
    ✓ 创建文本: Reading title (ID: 3)
    ✓ 创建文本: Another reading (ID: 4)

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

**注意事项**:
1. 脚本会自动检查重复数据，避免重复导入
2. 如果课程或单元已存在，会复用现有记录
3. 如果文本已存在（相同 unit_id 和 title），会跳过
4. 所有操作在一个事务中完成，失败会自动回滚

**数据库配置**:
- 默认连接本地 `english_learning`
- 用户名、密码等请以脚本当前 `DB_CONFIG` 或运行参数为准
- 不要在文档中写入真实凭据

如需修改配置，请编辑脚本中的 `DB_CONFIG` 变量。

## 依赖

### Python 依赖
```bash
pip3 install mysql-connector-python
```

### 数据库表结构
- courses: 课程表
- units: 单元表
- texts: 文本表

## 故障排查

### 问题: 数据库连接失败
**解决方案**:
- 检查 MySQL 服务是否运行
- 检查数据库配置是否正确
- 检查用户权限

### 问题: 找不到 JSON 文件
**解决方案**:
- 确保在项目根目录执行脚本
- 检查 book1/readings 目录是否存在
- 检查 JSON 文件是否存在

### 问题: 文件名解析失败
**解决方案**:
- 检查文件名格式是否符合规则
- 文件名应为: `{数字}-{数字} {名称}.json`

## 扩展

如需导入其他课程数据，可以：
1. 复制 `import_book1_readings.py`
2. 修改 `COURSE_NAME`、`COURSE_DESCRIPTION` 等配置
3. 修改 `READINGS_DIR` 指向新的数据目录
4. 根据需要调整 `parse_filename` 函数
