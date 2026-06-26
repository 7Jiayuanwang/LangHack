# Flyway 迁移整合计划：47 → 19 文件

> 文档版本: v1.0 | 创建日期: 2026-06-19
> 目的: 跨会话执行参考，避免方向漂移
> 备份: `/tmp/migration-backup-20260619_150806/`

---

## 1. 目标

将 `backend/src/main/resources/db/migration/` 下 47 个 V*.sql 文件（含 2 个版本号冲突）按**业务域**整合为 19 个文件，预留 4 个空迁移用于未来扩展。

---

## 2. 业务域映射表（不可修改）

| 新版本 | 业务域 | 包含原文件 | 说明 |
|--------|-------|----------|------|
| **V0** | 基础表 | V0__base_tables.sql | 不动 |
| **V1** | 用户/课文/学习 | V1, V2, V10, V11, V17, V21, V22 | users/texts/learning_records 全部扩展 |
| **V2** | 媒体/标注 | V3, V28, V29 | media + SM-2 + annotations + 可见性 |
| **V3** | 备份/更新系统 | V4, V39, V40 | backup_records + update_config |
| **V4** | 监控/访问 | V5 | monitoring_logs/access_logs |
| **V5** | 系统配置/AI providers | V6, V13(system_config 部分) | system_config + ai_provider |
| **V6** | Feature toggles | V7, V37, V47 | system_config toggle 相关 + 配置清理 |
| **V7** | SSO/CAS | V8, V16 | sso_user_bindings/sso_config |
| **V8** | 学校数据 CDSP | V9, V12, V14, V42 | school_* + teaching_plans + binding |
| **V9** | 媒体转码/大小限制 | V44, V46 | media 转码 + 上传限制配置 |
| **V10** | AI 出题 | V23, V24, V25, V26, V30 | text_exercises + ai_quiz_session + records |
| **V11** | AI 对话/反馈 | V27, V31, V43 | ai_conversations/messages + vocabulary_books.source_type + text_learning_records.content_version + ai_feedback |
| **V12** | AI 问答 | V45 | ai_call_log 索引 + school_student 扩展 + field_mappings + sso_user_bindings 扩展 |
| **V13** | 学习者画像 | V32, V33, V35, V36 | recommendation_events + snapshots + summaries + composite_tags |
| **V14** | 标签管理 | V34, V38 | tag_recalibration_log + base tag seed |
| **V15** | 预留：词表扩展 | (空) | 仅占位，header 注释用途 |
| **V16** | 预留：学校数据扩展 | (空) | 同上 |
| **V17** | 预留：AI 模型路由 | (空) | 同上 |
| **V18** | 预留：内容运营 | (空) | 同上 |
| **V19** | 预留：通用扩展 | (空) | 同上 |

---

## 3. 跨迁移注意事项

### 3.1 操作同一张表的迁移必须按顺序合并
- `texts` 表: V2 → V17 → V22（ADD COLUMN 顺序敏感）
- `users` 表: V10 → V17 → V22 → V42
- `vocabulary_books` 表: V0 → V3(SM-2) → V31(source_type)
- `text_annotations` 表: V28 → V29
- `media` 表: V3 → V44
- `school_student` 表: V14 → V45
- `sso_user_bindings` 表: V8 → V45

### 3.2 INSERT IGNORE 必须保留
所有 `system_config` / `field_mappings` / `learner_composite_tags` 的 seed 必须用 `INSERT IGNORE`，保证重跑幂等。

### 3.3 IF NOT EXISTS 模式
所有 `ADD COLUMN` / `CREATE INDEX` / `CREATE TABLE` 必须包在存储过程中检测 `information_schema`，保证幂等。

### 3.4 关键预设参数
```sql
-- DELIMITER 禁止使用（与预处理脚本冲突）
-- 必须使用 $$ 终止符
```

---

## 4. V26 NO-OP 处理

原 `V26__text_exercises_table.sql` 是 NO-OP。但 V23 已经 CREATE TABLE text_exercises，所以 V26 在新结构中**改为真实生效内容**：
- 检查 ai_provider 表是否需要 protocol 列（V13 已有）
- 检查缺失的 ai_quiz_session 字段补充

实际查看 V26 后决定，发现 NO-OP 时：
- **方案 A**：删除 V26，将 text_exercises 完整定义在 V10（AI 出题）
- **方案 B**：把 V26 改为实际补充（如 seed 默认配置）

**首选方案 A**，更清晰。

---

## 5. 重复版本号处理（已完成）

| 文件 | 处理 |
|------|------|
| V31__add_content_version_to_text_learning_records.sql | 合并两个 V31 内容 |
| V31__add_vocabulary_source_type.sql | **已删除** |
| V41__add_vocabulary_source_type.sql | **已删除**（与 V31 重复） |
| V45__ai_qa_and_student_basic_info.sql | 保留主文件 |
| V45__ai_qa_usage_index.sql | **已删除**（内容已在主文件 Part 1） |
| V8__sso_integration.sql | 注释修正 V15 → V8 |
| V43__ai_feedback.sql | 注释修正 V27 → V43 |

---

## 6. 执行步骤

### Phase 1：精读（Agent 并行）
对每个业务域派发一个 subagent 精读所有相关原文件，提取：
- CREATE TABLE 完整 DDL
- ALTER TABLE 完整语句
- INSERT IGNORE 完整 seed 数据
- 存储过程包裹模式
- 注释和说明

### Phase 2：写出 19 个新文件
按业务域对应写出 V0-V19 新文件。

### Phase 3：删除旧文件
保留 V0 不动，删除其余 28 个旧 V*.sql。

### Phase 4：同步 04-apply-all-migrations.sql
按新 V0-V19 顺序重排。

### Phase 5：本地端到端测试
`docker compose down -v && docker compose up -d`，观察 Flyway 输出。

### Phase 6：多 Agent 交叉验证
- Agent 1：核对每个新文件包含的 DDL 与原文件等价
- Agent 2：核对列顺序 / IF NOT EXISTS 包裹
- Agent 3：核对 04-apply-all-migrations.sql 同步

---

## 7. 验证清单（必须 100% 通过）

- [ ] 19 个文件全部能 Flyway 解析（无版本号冲突）
- [ ] 每个新文件包含原对应文件所有 DDL
- [ ] 所有操作幂等（IF NOT EXISTS / INSERT IGNORE）
- [ ] DELETE 操作无遗漏
- [ ] DROP TABLE 仅限临时表
- [ ] 没有 DELIMITER 关键字
- [ ] 04-apply-all-migrations.sql 与新 Flyway 文件一一对应
- [ ] docker compose down -v → up 全部成功
- [ ] flyway_schema_history 包含 V0-V19 全部记录
- [ ] 表数据保留（users/texts/words 等核心表行数不变）

---

## 8. 风险与回滚

- **风险 1**：flyway_schema_history 被清空 → 通过 `docker compose down -v` 重置
- **风险 2**：DDL 漏合并导致字段缺失 → Agent 交叉验证 + 端到端测试
- **风险 3**：数据丢失 → 当前 production 数据集有 40 条 media + 用户数据，建议先 `mysqldump` 全量备份再执行

**回滚方案**：从 `/tmp/migration-backup-20260619_150806/` 恢复 Flyway 目录，并重新部署。

---

## 9. 文档同步清单

完成后需更新：
- `backend/src/main/resources/db/migration/README.md`（如存在）
- `docs/DATABASE_COLLABORATION_GUIDE.md`（表结构变更流程）
- `memory/MEMORY.md` 添加"整合版本"记录