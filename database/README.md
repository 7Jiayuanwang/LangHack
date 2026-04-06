# database/ — 历史数据目录

本目录存放历史数据库备份文件，使用 **Git LFS** 进行版本管理。

> **重要区分**
> - **数据库结构**（表/索引/配置）由 `docker/mysql/init/04-apply-all-migrations.sql` 负责，全幂等
> - **历史业务数据**（文章/单词/用户等）由本目录的 SQL 文件负责，按需导入

---

## 自动导入（首次启动）

首次 `docker compose up` 时，`03-import-backup.sh` 自动发现并导入数据：

| 优先级 | 来源 |
|--------|------|
| 1 | 环境变量 `DB_SEED_SQL` 指定的文件 |
| 2 | `database/` 目录下**最新**的 `.sql` 文件（按文件名排序） |

文件不存在 / 为 LFS 指针 / 文件过小时自动跳过，不影响启动。

---

## 手动导入（已运行环境）

```bash
# 将备份文件导入到运行中的主库
DB_PW=$(grep '^DB_PASSWORD=' docker/.env | cut -d= -f2-)
docker exec -i langhack-mysql-master \
  mysql -uroot -p"$DB_PW" english_learning \
  < database/<your-backup>.sql
```

---

## 导出当前数据

```bash
# 导出完整数据（含结构+数据）
DB_PW=$(grep '^DB_PASSWORD=' docker/.env | cut -d= -f2-)
docker exec langhack-mysql-master \
  mysqldump -uroot -p"$DB_PW" --single-transaction \
  --routines --triggers english_learning \
  > database/export_$(date +%Y%m%d_%H%M%S).sql

# 提交到 Git LFS
git add database/*.sql
git commit -m "chore: export db $(date +%Y%m%d)"
git push
```

---

## 运行时备份

Spring Boot 备份服务和 `auto-update.sh` 将备份写入 `backups/` 目录（绑定挂载至容器 `/app/backups`），**该目录已在 `.gitignore` 中忽略**，不会随 `docker compose down` 或项目更新而丢失。

---

## Git LFS 配置

```bash
# 首次克隆后拉取 LFS 内容
git lfs pull

# 检查 LFS 状态
git lfs ls-files
```

`.gitattributes` 已配置所有 `database/*.sql` 使用 LFS 存储。

---

## 数据库信息

- 数据库名: `english_learning`
- 字符集: `utf8mb4_unicode_ci`
- 时区: `GMT+8`
