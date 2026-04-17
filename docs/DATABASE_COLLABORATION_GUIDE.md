# 数据库协作规范

> 适用场景：LangHack 团队协作开发，规范数据库设计、变更和日常协作流程，避免表结构冲突、数据覆盖和迁移混乱。

---

## 一、核心原则

```
1. 先登记再建表 — 任何人建表前必须先查登记表
2. 变更走 Flyway — 结构变更必须通过迁移脚本，禁止直接改线上库
3. 谁创建谁维护 — 表的负责人对表结构和数据质量负责
4. 读写分离感知 — 记住写走主库、读走从库，写入后立刻读取需强制走主库
```

---

## 二、模块划分与表归属

### 2.1 模块定义

| 模块 | 涉及表 | 负责人 | 说明 |
|------|--------|--------|------|
| 用户 | `users` | dctx479 | 注册、登录、角色、权限、SSO绑定 |
| 课程 | `courses`, `units` | dctx479 | 课程、单元管理 |
| 课文 | `texts` | dctx479 | 课文内容、多难度版本、推荐 |
| 班级 | `classes`, `student_classes` | dctx479 | 班级管理、学生加入 |
| 任务 | `assignments` | dctx479 | 作业布置、截止时间、权重 |
| 词汇 | `words`, `vocabulary_books` | dctx479 | 单词管理、生词本、SM2复习 |
| 学习 | `learning_records` | dctx479 | 学习记录、进度追踪 |
| 媒体 | `media` | dctx479 | 图片/视频BLOB存储 |
| 系统 | `system_config`, `feature_toggles` | dctx479 | 系统配置、功能开关 |
| SSO | `sso_login_log` | dctx479 | CAS单点登录日志 |
| 学校 | `school_*` | dctx479 | 学校数据同步 |

> 模块和负责人在项目初期由团队讨论确定，后续调整需在群里通知。

### 2.2 表命名规范（LangHack 现有约定）

**现有项目使用无前缀复数名词风格：**

```sql
-- ✅ 项目现有风格（已落地，保持一致）
users                 -- 用户表
courses               -- 课程表
units                 -- 单元表
texts                 -- 课文表
classes               -- 班级表
assignments           -- 任务表
words                 -- 单词表
vocabulary_books      -- 生词本表
learning_records      -- 学习记录表
student_classes       -- 学生-班级关联表

-- ❌ 禁止（与现有风格不一致）
user_info             -- 项目不用此前缀
t_users               -- 无意义前缀
Users                 -- 大驼峰
```

**新增表规则：**
- 全小写，单词间用下划线 `_` 连接
- 使用复数名词，与现有表风格保持一致
- 关联表格式：`{实体A}_{实体B}`，如 `student_classes`
- 禁止使用数据库保留字（`order`、`group`、`key` 等，如需使用加前缀区分）

### 2.3 字段命名规范

```sql
-- ✅ 正确（项目现有风格）
created_time          -- 创建时间（项目用 created_time，非 created_at）
updated_time          -- 更新时间（项目用 updated_time，非 updated_at）
deleted               -- 逻辑删除（项目用 deleted，非 is_deleted）
is_public             -- 布尔字段用 is_ 前缀
is_featured           -- 布尔字段用 is_ 前缀
sso_bound             -- 布尔字段（TINYINT(1)）

-- ❌ 错误
uid                   -- 缩写不直观
createTime            -- 大驼峰
del_flag              -- 含义模糊
f1, f2                -- 无意义命名
created_at            -- 与项目现有风格不一致（项目用 created_time）
is_deleted            -- 与项目现有风格不一致（项目用 deleted）
```

**关键约定（与通用规范不同）：**
- 时间字段：`created_time` / `updated_time`（非 `created_at` / `updated_at`）
- 逻辑删除：`deleted`（非 `is_deleted`），值为 `0`=正常 / `1`=已删除
- MyBatis Plus `@TableLogic` 注解对应 `deleted` 字段

---

## 三、表登记制度

### 3.1 表登记文件

在仓库中维护 `docs/database-tables.md`，所有表必须登记：

```markdown
# 数据库表登记

## 用户模块（负责人：dctx479）

| 表名 | 说明 | 创建日期 | 状态 |
|------|------|----------|------|
| users | 用户基本信息 | 2026-03-15 | 正常 |
| sso_login_log | SSO登录日志 | 2026-04-10 | 正常 |

## 课程模块（负责人：dctx479）

| 表名 | 说明 | 创建日期 | 状态 |
|------|------|----------|------|
| courses | 课程信息 | 2026-03-15 | 正常 |
| units | 单元信息 | 2026-03-15 | 正常 |

## 已废弃

| 表名 | 原模块 | 废弃日期 | 原因 | 替代表 |
|------|--------|----------|------|--------|
| （暂无） | | | | |
```

### 3.2 建表流程

```
1. 查阅 docs/database-tables.md，确认表名未被占用
2. 确认命名符合项目现有风格（复数名词 + 小写下划线）
3. 编写 Flyway 迁移脚本（含注释）
4. 同步更新 docker/mysql/init/04-apply-all-migrations.sql（Docker 环境不运行 Flyway）
5. 更新 docs/database-tables.md，登记新表
6. 提交 PR，Review 中确认表名和结构合理
7. 合并后 Flyway 自动执行迁移脚本
```

> **重要**：LangHack 使用 Flyway 管理迁移，但 Docker 部署环境中 Flyway 被禁用，所有迁移 SQL
> 必须同步到 `docker/mysql/init/04-apply-all-migrations.sql`，否则 `docker compose down -v` 后重建会丢失变更。

### 3.3 建表 SQL 模板

```sql
-- ============================================
-- 表名：teaching_plans
-- 模块：课程
-- 负责人：dctx479
-- 创建日期：2026-04-16
-- 说明：教学计划表
-- ============================================
CREATE TABLE IF NOT EXISTS teaching_plans (
    id              BIGINT          NOT NULL AUTO_INCREMENT COMMENT '主键',
    teacher_id      BIGINT          NOT NULL COMMENT '教师ID（关联 users.id）',
    class_id        BIGINT          NULL     COMMENT '班级ID（关联 classes.id）',
    title           VARCHAR(200)    NOT NULL COMMENT '计划标题',
    description     TEXT            NULL     COMMENT '计划描述',
    status          TINYINT         NOT NULL DEFAULT 0 COMMENT '状态: 0-草稿 1-已发布 2-已归档',
    deleted         TINYINT         NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0-正常 1-删除',
    created_time    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_time    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    INDEX idx_teacher_id (teacher_id),
    INDEX idx_class_id (class_id),
    INDEX idx_status (status),
    INDEX idx_deleted (deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='教学计划表';
```

**必填要素：**
- 每个字段加 `COMMENT`
- 表加 `COMMENT`
- `created_time` / `updated_time` 时间戳（注意字段名）
- `deleted` 逻辑删除标记（注意字段名）
- `CREATE TABLE IF NOT EXISTS`（幂等）
- 合适的索引（主键 + 外键索引 + 业务查询索引）
- `ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci`
- 外键关联用注释说明依赖关系，**不加物理外键**（项目约定，代码层保证完整性）

---

## 四、数据库版本管理（Flyway 迁移）

### 4.1 目录结构

```
backend/src/main/resources/db/migration/
├── V1__scoring_and_assignments.sql
├── V2__text_and_learning.sql
├── V3__media_and_annotations.sql
├── V4__backup_and_update_system.sql
├── V5__monitoring_and_access.sql
├── V6__system_config_and_ai_providers.sql
├── V7__feature_toggles.sql
├── V8__sso_integration.sql
├── V9__school_data_sync.sql
├── V10__user_nickname_and_avatar_rename.sql
├── V11__text_featured_and_vocab_sm2.sql
└── V12__fix_role_mapping_cleanup.sql

docker/mysql/init/                    # Docker 初始化脚本
├── 00-base-tables.sql                # 基础表结构
├── 01-create-replication-user.sql    # 主从复制用户
├── 04-apply-all-migrations.sql       # Flyway 迁移合并（必须同步！）
└── 08-v16-school-tables.sql          # 学校相关表
```

### 4.2 迁移脚本命名规范

**格式：** `V{编号}__{操作描述}.sql`（双下划线 `__` 分隔）

```bash
# 当前最新编号为 V12，下一个为 V13
V13__create_teaching_plan_tables.sql
V14__add_user_last_login_column.sql
V15__alter_texts_add_source_field.sql
V16__add_index_users_inner_uid.sql
```

**规则：**
- 编号必须递增，不能跳号
- 双下划线 `__` 分隔编号和描述
- 描述用小写 + 下划线，简洁说明做了什么
- **已合并的迁移脚本禁止修改**，只能追加新的
- **新建迁移后必须同步** `docker/mysql/init/04-apply-all-migrations.sql`

### 4.3 迁移脚本模板

```sql
-- ============================================
-- 迁移编号：V13
-- 作者：dctx479
-- 日期：2026-04-16
-- 说明：用户表增加最后登录时间字段
-- ============================================

-- 变更
ALTER TABLE users
    ADD COLUMN last_login_time DATETIME DEFAULT NULL COMMENT '最后登录时间' AFTER updated_time;

-- 回滚（注释形式保留，手动执行）
-- ALTER TABLE users DROP COLUMN last_login_time;
```

### 4.4 迁移执行流程

```
开发者在本地编写迁移脚本（backend/src/main/resources/db/migration/）
    ↓
同步到 docker/mysql/init/04-apply-all-migrations.sql
    ↓
提交 PR（迁移脚本 + 更新表登记文档）
    ↓
Review 检查：字段命名 / 索引合理性 / 是否影响现有功能 / Docker同步
    ↓
合并到 master
    ↓
Flyway 自动执行迁移（本地开发环境）
    ↓
Docker 部署通过 04-apply-all-migrations.sql 执行
```

---

## 五、常见冲突场景与解决方案

### 5.1 表名冲突

```
场景：两人都想建一个 config 表

解决方案：加业务限定词
  → system_config（系统配置）
  → course_config（课程配置）
```

### 5.2 字段名冲突（同一表）

```
场景：两人都要在 users 表加 type 字段

解决方案：字段名加业务限定
  → user_type（用户类型：普通/VIP）
  → account_type（账号类型：手机/邮箱/微信）
```

### 5.3 同一张表多人修改

```
场景：A 加了 avatar 字段，B 加了 phone 字段

解决方案：通过 Flyway 迁移脚本顺序执行
  → V13__add_user_avatar_column.sql
  → V14__add_user_phone_column.sql
  → 两个 ALTER 互不影响，顺序执行即可
```

### 5.4 同一张表同字段冲突

```
场景：两人都在 users 加了 role 字段，类型不同

解决方案：
  1. 先到先得：先合并的 PR 生效
  2. 后到的人必须适配已有字段
  3. 如有分歧，在群里讨论确定最终方案
  4. 需要修改已合并字段时，追加迁移脚本
```

### 5.5 外键关联跨模块

```
场景：learning_records 需要引用 courses 的 id

解决方案：
  1. 在代码层保证引用完整性（推荐，项目现有约定）
  2. 不建物理外键，字段注释说明依赖关系
  3. 修改被引用表结构时，通知关联模块负责人
```

```sql
-- ✅ 项目约定：不加物理外键，注释说明
CREATE TABLE learning_records (
    student_id BIGINT NOT NULL COMMENT '学生ID（关联 users.id）',
    ...
);

-- ❌ 不推荐：物理外键增加耦合
-- FOREIGN KEY (student_id) REFERENCES users(id)
```

### 5.6 测试数据冲突

```
场景：多人同时往同一张表插测试数据，主键或唯一约束冲突

解决方案：
  1. 测试数据使用 INSERT ... SELECT ... FROM DUAL WHERE NOT EXISTS 模式（项目现有风格）
  2. 使用自增主键，不手动指定 ID
  3. 每人使用独立的测试数据区间
```

```sql
-- ✅ 项目现有幂等插入模式
INSERT INTO users (username, password, role, real_name, status, deleted, created_time, updated_time)
SELECT 'test_user', '$2a$10$...', 'STUDENT', '测试用户', 'ACTIVE', 0, NOW(), NOW()
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM users WHERE username = 'test_user');
```

---

## 六、数据库变更分类与审批

### 6.1 变更分级

| 级别 | 变更类型 | 审批要求 | 示例 |
|------|----------|----------|------|
| 🟢 低风险 | 新增表、新增字段、新增索引 | PR Review 通过即可 | 加 `last_login_time` 字段 |
| 🟡 中风险 | 修改字段类型、改索引、加唯一约束 | PR Review + 负责人确认 | `VARCHAR(50)` 改 `VARCHAR(200)` |
| 🔴 高风险 | 删表、删字段、改主键、改ENUM值 | PR Review + 团队讨论 | 删除废弃表 |

### 6.2 高风险变更流程

```
1. 在群里发起讨论，说明变更原因和影响范围
2. 确认无模块受影响，或已通知相关模块负责人
3. 编写迁移脚本（含回滚脚本）
4. 同步 04-apply-all-migrations.sql
5. 提交 PR，标题标注 [高风险]
6. 至少 2 人 Review 通过
7. 在测试环境验证
8. 生产环境执行时，负责人在场
```

### 6.3 废弃表处理

```sql
-- 第一步：标记废弃（不立即删除，保留观察期）
-- 在 docs/database-tables.md 中将状态改为"废弃中"

-- 第二步：确认无代码引用（全局搜索表名、Entity、Mapper）

-- 第三步：观察期（至少1周）后，执行删除迁移
-- V0XX__drop_deprecated_xxx.sql
DROP TABLE IF EXISTS xxx;

-- 第四步：同步 04-apply-all-migrations.sql
-- 第五步：更新表登记文档，移入"已废弃"分区
```

---

## 七、本地开发环境管理

### 7.1 环境配置

```
开发环境（本地 Docker）：每人独立，自由操作
测试环境（共享）：合并到 develop 后自动部署
生产环境：仅负责人可操作
```

### 7.2 本地数据库同步

```bash
# 从主库导出全量数据
docker exec -i langhack-mysql-master mysqldump -uroot -proot english_learning > backup.sql

# 恢复本地数据
docker exec -i langhack-mysql-master mysql -uroot -proot english_learning < backup.sql

# 只同步结构（不含数据）
docker exec -i langhack-mysql-master mysqldump -uroot -proot --no-data english_learning > schema.sql

# 重新构建（清理所有数据卷）
docker compose down -v && docker compose up -d
```

### 7.3 本地操作规则

| 操作 | 是否允许 | 说明 |
|------|----------|------|
| 建表/改表（通过 Flyway 迁移脚本） | ✅ | 必须在迁移脚本中操作 |
| 同步 04-apply-all-migrations.sql | ✅ | Docker 环境必需 |
| 直接在本地建临时表 | ✅ | 表名加 `tmp_` 前缀，不提交到仓库 |
| 修改其他模块的表结构 | ❌ | 必须通知该模块负责人 |
| 直接修改测试/生产库 | ❌ | 必须通过迁移脚本 + PR |
| 手动插入/修改测试数据 | ✅ | 仅限本地开发环境 |

---

## 八、索引规范

### 8.1 索引命名

```sql
-- 主键索引（自动创建，无需命名）
PRIMARY KEY (id)

-- 唯一键
UNIQUE KEY username (username)
UNIQUE KEY uk_student_word (student_id, word_id)

-- 普通索引
KEY idx_course_id (course_id)
KEY idx_status (status)

-- 联合索引
KEY idx_user_status_created (status, created_time)
```

**命名规则：**
- 普通索引：`idx_{字段名}`
- 唯一索引：`uk_{字段名}` 或直接用字段名（如 `username`）
- 联合索引：`idx_{字段A}_{字段B}`
- 禁止使用数据库自动生成的索引名

### 8.2 索引设计原则

```
1. WHERE 条件字段建索引
2. JOIN 关联字段建索引
3. ORDER BY / GROUP BY 字段考虑建索引
4. 联合索引遵循最左前缀原则
5. 单表索引数不超过 6 个
6. 区分度低的字段（如 deleted）不单独建索引（除非高频查询过滤）
```

---

## 九、数据安全

### 9.1 敏感数据处理

```sql
-- ✅ 密码哈希存储（项目现有风格）
password VARCHAR(255) NOT NULL COMMENT '密码'

-- ✅ SSO标识加密存储
sso_subject VARCHAR(255) DEFAULT NULL COMMENT 'SSO唯一标识'

-- ❌ 明文存储
password VARCHAR(50) NOT NULL COMMENT '密码明文'
```

**项目现有安全规范：**
- 密码使用 BCrypt 哈希存储
- `e.getMessage()` 禁止直接返回客户端，使用 `log.error()` + `Result.error("通用信息")`
- `/api/admin/**` 需 ADMIN 角色
- `/api/student/**` 需 `hasAnyRole('USER','STUDENT')`（非 hasRole）

### 9.2 逻辑删除

```sql
-- 项目所有业务表使用逻辑删除
deleted TINYINT DEFAULT 0 COMMENT '是否删除 0-未删除 1-已删除'

-- MyBatis Plus @TableLogic 自动处理
-- SELECT 自动加 WHERE deleted = 0
-- DELETE 自动转为 UPDATE deleted = 1
```

**重要陷阱：**
- ❌ `setDeleted(1); updateById()` 是无操作
- ✅ `removeById(id)` 才会触发逻辑删除
- `selectBatchIds()` 不走 `@TableLogic`，须后置 `.filter(w -> w.getDeleted() == 0)`
- Mapper XML 原生 SQL 不走 `@TableLogic`，手动加 `AND deleted = 0`

### 9.3 SQL 注入防护

```java
// ✅ 参数化查询（MyBatis Plus / @Select 注解）
@Select("SELECT * FROM users WHERE username = #{username}")
User getByUsername(@Param("username") String username);

// ❌ 字符串拼接
@Select("SELECT * FROM users WHERE username = '" + username + "'")
```

---

## 十、跨模块协作规范

### 10.1 跨模块查询

```sql
-- ✅ 允许：JOIN 查询（只读）
SELECT u.username, lr.duration_seconds
FROM users u
JOIN learning_records lr ON u.id = lr.student_id

-- ❌ 禁止：直接修改其他模块的表
-- 学习模块的开发者不能直接 UPDATE users
-- 应该调用用户模块提供的 Service 接口
```

### 10.2 跨模块变更通知

当变更可能影响其他模块时：

```
1. 在 PR 描述中标注影响范围
2. 在群里 @ 相关模块负责人
3. 等待受影响方确认后再合并
```

**需通知的变更：**

| 变更类型 | 示例 | 通知对象 |
|----------|------|----------|
| 修改被引用的字段 | `users.id` 类型变更 | 所有引用该字段的模块 |
| 删除字段 | 删除 `users.nickname` | 所有使用该字段的模块 |
| 修改ENUM值 | `role` 新增值 | 依赖该状态的模块 |
| 改表名 | 重命名 `courses` | 所有关联模块 |

### 10.3 公共表约定

系统配置表为公共表，任何模块可读：

```sql
-- 系统配置（只读，仅负责人可修改）
system_config

-- 功能开关（只读）
feature_toggles

-- SSO 登录日志（只写，不能 UPDATE/DELETE）
sso_login_log
```

---

## 十一、读写分离注意事项

LangHack 使用 MySQL 主从架构 + 动态数据源切换：

```
写操作（INSERT/UPDATE/DELETE）→ 主库
读操作（SELECT）              → 从库
```

**常见问题：写入后立刻读取，读不到数据**

```
场景：管理员修改了推荐状态，刷新页面后数据没变

根因：写走主库，读走从库，异步复制有延迟

解决方案：
  1. Admin 接口自动强制走主库（AdminMasterInterceptor）
  2. 关键查询方法加 @UseMaster 注解
  3. 排查顺序：① 确认数据库有值 → ② 确认 nginx 收到请求 → ③ 确认后端响应 → ④ 检查读写分离路由
```

---

## 十二、常见问题 Q&A

**Q：两个人同时提交了修改同一张表的迁移脚本，执行顺序怎么办？**

> 按 Flyway 编号顺序执行。后合并的 PR 如果产生冲突，在 rebase 时解决迁移脚本冲突，调整编号确保顺序正确。

**Q：不小心在生产库直接改了表结构，怎么补救？**

> 1. 立即写一个回滚迁移脚本。2. 补一个正向迁移脚本，让 Flyway 版本记录保持一致。3. 同步 04-apply-all-migrations.sql。4. 在群里通报，避免再次发生。

**Q：需要给现有表加一个字段，但不确定影响范围怎么办？**

> 1. 全局搜索表名，找到所有引用点（Entity、Mapper、Service、Controller、前端 API）。2. 新字段设为 `NULL` 或带 `DEFAULT`，不影响现有逻辑。3. 注意 MyBatis Plus SELECT 会自动包含新字段，确认前端不受影响。

**Q：Docker 重建后表结构丢失怎么办？**

> 确认迁移已同步到 `docker/mysql/init/04-apply-all-migrations.sql`。Flyway 在 Docker 部署中被禁用，所有结构变更必须手动同步到此文件。

**Q：@TableField 注解新增了字段但数据库没有这个列？**

> 必须同步创建 Flyway 迁移脚本。仅添加 @TableField 不创建对应列会导致 SQL 错误。之前的教训：Text 实体有 9 个 @TableField 字段从未在任何迁移中创建，导致 `docker compose down -v` 后重建报错。

**Q：修改构造函数后要注意什么？**

> grep 所有调用点（含 `src/test/`），同步更新测试文件中的构造函数调用。

---

## 十三、规范速查表

| 场景 | 操作 |
|------|------|
| 建新表 | 编写 Flyway 迁移 → 同步 04-apply-all-migrations.sql → 更新登记文档 → 提 PR |
| 加字段 | 编写 Flyway 迁移 → 同步 04-apply-all-migrations.sql → 提 PR（字段允许 NULL 或有默认值） |
| 改字段类型 | 🟡 负责人确认 → Flyway 迁移 → 同步 04 → 提 PR |
| 删表/删字段 | 🔴 团队讨论 → 确认无引用 → Flyway 迁移 → 同步 04 → 观察期 → 删除 |
| 加索引 | Flyway 迁移 → 同步 04 → 提 PR |
| 插测试数据 | 仅本地，用幂等 INSERT ... WHERE NOT EXISTS 模式 |
| 改其他模块的表 | 先通知负责人 → 负责人编写迁移或授权 |
| 本地乱搞了 | `docker compose down -v && docker compose up -d` 重建 |
| 写入后读不到 | 检查读写分离路由，关键方法加 @UseMaster |
