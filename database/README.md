# 数据库备份

本目录包含数据库备份文件，使用 Git LFS 进行版本管理。

## 文件说明

- `database_backup_YYYYMMDD_HHMMSS.sql`: 数据库完整备份文件

## 使用 Git LFS

### 首次克隆仓库

```bash
# 克隆仓库
git clone <repository-url>

# 拉取 LFS 文件
git lfs pull
```

### 添加新的数据库备份

```bash
# 导出数据库
mysqldump -u root -p<password> english_learning > database/database_backup_$(date +%Y%m%d_%H%M%S).sql

# 添加并提交
git add database/*.sql
git commit -m "Add database backup"
git push
```

### 恢复数据库

```bash
# 从备份文件恢复
mysql -u root -p<password> english_learning < database/database_backup_YYYYMMDD_HHMMSS.sql
```

## 注意事项

1. 数据库备份文件较大，已通过 Git LFS 管理
2. 确保已安装 Git LFS: `git lfs install`
3. 首次克隆后需要运行 `git lfs pull` 获取 LFS 文件
4. 备份文件包含敏感数据，请妥善保管

## 数据库信息

- 数据库名: `english_learning`
- 字符集: UTF-8
- 时区: GMT+8

