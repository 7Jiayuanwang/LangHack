# ECDICT 数据库导入指南

## 概述

为了避免每次启动后端服务都要加载 770,000+ 条 ECDICT 词汇数据（耗时约 30-60 秒），我们已将数据持久化到数据库中。

## 架构变更

### 之前（CSV 模式）
- 每次启动时从 `ECDICT-master/ecdict.csv` 文件加载所有词汇到内存
- 启动时间长
- 内存占用大

### 现在（数据库模式）
- 词汇数据存储在 `ecdict_words` 表中
- 启动时仅验证数据库连接，无需加载数据
- 查询时直接从数据库读取
- 启动时间快（< 5 秒）
- 内存占用小

## 使用步骤

### 1. 创建数据库表

数据库迁移文件已自动创建：`src/main/resources/db/migration/V4__create_ecdict_words_table.sql`

如果使用 Flyway，表会自动创建。否则，手动执行以下 SQL：

```sql
CREATE TABLE IF NOT EXISTS `ecdict_words` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `word` VARCHAR(100) NOT NULL COMMENT '单词',
  `phonetic` VARCHAR(200) DEFAULT NULL COMMENT '音标',
  `definition` TEXT DEFAULT NULL COMMENT '英文定义',
  `translation` TEXT DEFAULT NULL COMMENT '中文翻译',
  `pos` VARCHAR(50) DEFAULT NULL COMMENT '词性',
  `collins` VARCHAR(10) DEFAULT NULL COMMENT '柯林斯星级',
  `oxford` VARCHAR(10) DEFAULT NULL COMMENT '牛津词典标记',
  `tag` VARCHAR(100) DEFAULT NULL COMMENT '标签',
  `bnc` VARCHAR(20) DEFAULT NULL COMMENT 'BNC词频',
  `frq` VARCHAR(20) DEFAULT NULL COMMENT '当代语料库词频',
  `exchange` VARCHAR(500) DEFAULT NULL COMMENT '时态复数等变换',
  `created_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_word` (`word`),
  KEY `idx_word_lower` ((LOWER(`word`))),
  KEY `idx_pos` (`pos`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='ECDICT词汇表';
```

### 2. 导入 ECDICT 数据

**重要**: 这是一次性操作，只需执行一次！

#### 方法一：使用 Maven 命令（推荐）

```bash
cd backend
mvn spring-boot:run -Dspring-boot.run.mainClass=com.english.util.ECDICTImporter
```

#### 方法二：使用 Java 命令

```bash
cd backend
mvn clean package -DskipTests
java -cp target/learn-english-backend-1.0.0.jar com.english.util.ECDICTImporter
```

#### 导入过程

- 导入工具会自动查找 ECDICT CSV 文件
- 支持的路径：
  - `ECDICT-master/ecdict.csv` (项目根目录)
  - `../ECDICT-master/ecdict.csv` (上级目录)
  - `/usr/local/ecdict/ecdict.csv` (系统路径)
- 批量插入，每批 1000 条
- 显示进度，每 10,000 条输出一次
- 预计耗时：5-10 分钟（取决于数据库性能）

#### 导入输出示例

```
=== ECDICT数据导入工具启动 ===
找到ECDICT文件: /home/user/projects/learn-english/ECDICT-master/ecdict.csv
开始导入数据...
已处理 10000 行，成功插入 9998 条
已处理 20000 行，成功插入 19995 条
...
已处理 770000 行，成功插入 769850 条
导入统计:
  总行数: 770610
  成功: 770537
  失败: 73
=== ECDICT数据导入完成 ===
数据库中共有 770537 条ECDICT词汇数据
```

### 3. 启动后端服务

导入完成后，正常启动后端服务：

```bash
cd backend
mvn spring-boot:run
```

启动日志应显示：

```
ECDICT词典服务初始化成功（数据库模式），词汇总数: 770537
```

## 降级机制

如果数据库连接失败或表不存在，系统会自动降级到 CSV 模式：

```
从数据库加载ECDICT失败，尝试从CSV文件加载: ...
开始加载ECDICT数据文件: ...
ECDICT词典服务初始化成功（CSV模式），已加载 770537 个词汇
```

## 重新导入数据

如果需要重新导入数据，先清空表：

```sql
TRUNCATE TABLE ecdict_words;
```

然后重新执行步骤 2。

## 性能对比

| 模式 | 启动时间 | 内存占用 | 查询速度 |
|------|---------|---------|---------|
| CSV 模式 | ~60 秒 | ~500 MB | 极快（内存） |
| 数据库模式 | ~3 秒 | ~50 MB | 快（索引优化） |

## 故障排查

### 问题：导入时提示"找不到ECDICT数据文件"

**解决方案**：
- 确保 `ECDICT-master/ecdict.csv` 文件存在
- 检查文件路径是否正确
- 尝试使用绝对路径

### 问题：导入失败，提示数据库错误

**解决方案**：
- 检查数据库连接配置（`application.yml`）
- 确保数据库用户有创建表和插入数据的权限
- 检查表是否已创建

### 问题：启动时仍然加载 CSV 文件

**解决方案**：
- 检查数据库中是否有数据：`SELECT COUNT(*) FROM ecdict_words;`
- 检查 `ECDICTWordMapper` 是否正确注入
- 查看启动日志中的错误信息

## 文件说明

- `ECDICTWord.java` - 实体类（数据库表映射）
- `ECDICTWordDTO.java` - 数据传输对象（CSV 模式使用）
- `ECDICTWordMapper.java` - MyBatis Mapper 接口
- `ECDICTImporter.java` - 数据导入工具
- `ECDICTService.java` - 服务类（支持双模式）
- `V4__create_ecdict_words_table.sql` - 数据库迁移文件

## 注意事项

1. **一次性导入**：数据导入只需执行一次，不要重复导入
2. **数据库备份**：导入完成后建议备份数据库
3. **索引优化**：表已创建必要的索引，无需额外优化
4. **字符集**：使用 `utf8mb4` 字符集，支持所有 Unicode 字符
