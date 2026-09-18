# 学习智能体与小芽设计系统合并修复记录

日期：2026-09-18

## 范围

将 `feat/learning-agent-mvp` 合入 `feat/xiaoya-design-system`。保留当前分支的小芽视觉规范、文章移动端阅读体验，同时接入智能体分支的全局入口、学习画像、记忆、内容推荐和上下文问答。

## 冲突与处理

| 位置 | 原因 | 处理 |
| --- | --- | --- |
| `frontend/src/App.vue` | 全局小芽入口与设计系统样式分别新增在两支 | 同时保留 `AgentCompanionWidget` 与 `xiaoya-theme.css` |
| `frontend/src/views/student/Dashboard.vue` | 首页视觉头图与个性化智能体入口占用同一位置 | 将学习目标、个性化文案和入口整合进 `XyPageHeader`，统一使用 `XyMascot` |
| `frontend/src/views/shared/AiQaPage.vue` | 文章伴读和智能体模式同时修改对话页 | 保留嵌入式对话、文章上下文、预填问题与一次性自测；文章伴读也发送 `LEARNING_AGENT` 场景以利用已有学习画像 |
| `docker/mysql/init/04-apply-all-migrations.sql` | 两支在文件末尾追加不同建表语句 | 同时保留例句收藏、作业模板和智能体数据表，删除冲突标记 |
| Flyway `V49` | 两支为不同表使用了同一迁移版本 | 保留主线 `V49__sentence_favorites.sql`；智能体迁移改为 `V54__learning_agent_context.sql` |

## 数据库兼容策略

`V54` 使用 `CREATE TABLE IF NOT EXISTS` 创建 `learner_agent_profiles`、`learner_goals`、`learner_interests`、`learner_memories`，并兜底创建 `sentence_favorites`。因此：

1. 全新数据库：`V49` 创建例句收藏，`V54` 创建智能体表。
2. 已运行当前分支 `V49` 的数据库：例句收藏已存在，`V54` 补建智能体表。
3. 已运行旧智能体分支 `V49` 的数据库：智能体表已存在，新的 `V49` 不会重跑；`V54` 补建例句收藏表。

第三种环境的 `flyway_schema_history` 中，`V49` 的旧校验和仍对应智能体脚本，而合并后的代码中 `V49` 是例句收藏脚本。当前配置 `validate-on-migrate: false` 可避免启动时因旧校验和中断，但这不是校验和已一致的证明。将来若恢复严格校验，应先备份数据库并核验上述五张表及历史记录，再按受控流程修复 Flyway 历史；不要直接手改历史表，也不要未经核验就运行 `repair`。

Docker 初始化 SQL 用于新库初始化；不要在已有业务数据库上把整个初始化脚本重新执行一遍。已有库应沿其现有迁移/部署流程升级，并在部署前做备份。

## 验证与限制

- 检查迁移文件版本唯一，`V49` 与 `V54` 均只有一个文件；Docker 初始化脚本无冲突标记。
- 新增 `MigrationVersionUniquenessTest`，从构建资源中检测重复 Flyway 版本。合并期间第一次增量测试发现 `backend/target/classes` 还残留旧智能体 `V49`；执行 Java 17 的 `mvn clean -Dtest=MigrationVersionUniquenessTest test` 后回归测试通过。发布构建必须从干净的 `target` 产物开始，避免旧迁移被打进新包。
- 对比 `V49` 与 `V54` 中的 `sentence_favorites` 建表语句，内容一致。
- Java 17 打包成功；检查生成的 Spring Boot JAR，迁移资源只含 `V49__sentence_favorites.sql` 和 `V54__learning_agent_context.sql`，没有旧智能体 `V49`。
- 前端构建通过；合并相关前端测试 38/38 通过。
- Java 17 后端编译通过；智能体相关后端单测 18/18 通过，后端全量测试通过。
- 前端全量测试 1411/1413 通过；未通过的 2 项是此前已有的后台作业表单 `classId` 默认值/重置断言，与本次合并无关。
- 本次未在现有数据库上实际执行迁移，因此上线前仍需在备份后的测试库分别验证新库和已有旧智能体 `V49` 的升级路径。
