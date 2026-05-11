# LangHack 核心技术总结与改进方案

> 生成日期：2026-05-06 | 基于代码库实际状态
> 排除：渗透测试相关内容

---

## 一、智能分级转写

### 当前状态

**LangHack 代码库中不存在"分级转写"功能。** 代码库中未找到「转写」「语义保留率 ≥97%」等相关实现。实际存在的是 **AI 文章难度改写**（`TextRewriteService`），架构如下：

- **实现方式**：单步调用大模型 prompt 改写，非多步管道（无「词汇替换→句式简化→语义校验」pipeline）
- **多提供商路由**：`AiProviderService` → HiAgent / OpenRouter / SiliconFlow 三选一
- **无流式输出**：同步请求-响应模式，无 SSE/WebSocket

### 问题与风险

| 项目 | 风险等级 | 说明 |
|------|---------|------|
| 分级转写功能 | 🔴 严重 | 大赛材料声称的核心卖点，代码库中不存在 |
| 语义保留率 ≥97% | 🔴 严重 | 无任何测试脚本、评估方法或实测数据支撑 |
| 改写质量评估 | 🟡 中等 | 无 BLEU/ROUGE/BERTScore 等自动化评测机制 |

### 补齐方案

**方案一：最小可行实现（1-2天）**

```
输入文章 → 单步 Prompt 改写 → 输出分级文章
```

1. **难度分级定义**：基于 CEFR（A1-C2）或中国英语等级量表，定义 3-5 个级别
2. **Prompt 模板**：为每个级别设计专用 prompt，控制词汇量、句式复杂度、语速
3. **集成点**：在 `TextRewriteService` 中添加 `rewriteToLevel(text, targetLevel)` 方法

**方案二：多步管道实现（3-5天，含评测体系）**

```
输入文章
  → Step 1: 词汇分级替换（基于 ECDICT 74万词库的词频/难度标注）
  → Step 2: 句式简化（长句拆分、被动转主动、从句缩减）
  → Step 3: AI 润色（保持语义连贯性）
  → Step 4: 语义校验（BERTScore 计算相似度，阈值 ≥0.97）
  → 输出分级文章 + 语义保留率报告
```

**语义保留率评测方案**：

```python
# 使用 BERTScore 计算改写前后的语义相似度
from bert_score import score

def evaluate_retention(original, rewritten):
    P, R, F1 = score([rewritten], [original], lang='en')
    return F1.mean().item()  # F1 值即语义保留率
```

**评测数据集**：准备 100-200 篇不同难度、不同题材的英语文章，每篇人工标注改写质量，计算 BERTScore 均值作为「语义保留率 ≥97%」的依据。

---

## 二、推荐算法冷启动

### 当前实现

已实现**完整的多级兜底策略**（`RecommendationServiceImpl.java`）：

| 层级 | 条件 | 策略 |
|------|------|------|
| 常规推荐 | 已读 ≥ 阈值 | Item-CF 60% + User-CF 40% 加权合并 |
| 冷启动兜底 1 | 已读不足 N 篇 | Featured（精选）+ 最新文章 |
| 冷启动兜底 2 | 协同过滤结果不足 | Featured + 难度匹配 |
| 极端冷启动 | 零行为 | **纯热门/精选 + 难度分级匹配** |

### 当前缺陷

- **未利用注册信息**：注册表单不采集年级/专业/英语水平字段
- **冷启动仅靠热门兜底**：无法根据用户特征做个性化推荐
- **缺少 A/B 测试框架**：无法量化冷启动策略的点击率/完读率

### 改进方案

**短期（低投入）**：注册时增加一个字段

```
注册页增加：当前英语水平（下拉选择）
选项：初中 / 高中 / 大学四级 / 大学六级 / 雅思 / 托福
```

冷启动时按水平匹配对应难度的 Featured 文章，而非纯热门。

**中期（中投入）**：兴趣标签冷启动

```
注册后弹出兴趣选择页（3-5个标签）：
  - 科技 / 文学 / 商业 / 旅游 / 考试备考
  → 冷启动按兴趣标签过滤推荐池
```

**长期（高投入）**：混合冷启动

```
热门兜底 40% + 难度匹配 30% + 兴趣标签 20% + 探索随机 10%
随用户行为数据积累，逐步降低热门权重，过渡到协同过滤
```

---

## 三、学校数据库对接

### 当前对接范围

已对接 **9 个数据源**，覆盖学生学籍、课程、成绩、课表全维度：

| # | 数据源 | 内容 | 用途 |
|---|--------|------|------|
| 1 | `administrative_class` | 行政班级（学生-班级关系） | 学生分班 |
| 2 | `teaching_class` | 英语教学班 | 教学分组 |
| 3 | `school_course` | 学校开课信息（KKYX） | 课程目录 |
| 4 | `teacher_courses` | 教师-课程关联 | 教师授课关系 |
| 5 | `course_selection` | 学生选课记录（KCBH） | 学生-课程绑定 |
| 6 | `course_schedule` | 课程表（时间、地点） | 上课安排 |
| 7 | `student_schedule` | 学生个人课表 | 个人课表查询 |
| 8 | `level_exam_scores` | 四六级考试成绩 | 英语水平评估 |
| 9 | `teaching_plans` | 教学计划/大纲 | 教学内容参考 |

### 双模式接入架构

```
┌──────────────────────────────────────────────────┐
│              SchoolDataApiClientDelegate          │
│         (根据 SCHOOL_DATA_SOURCE 切换)            │
├────────────────────┬─────────────────────────────┤
│   API 模式 (默认)   │   ETL 模式 (JDBC 直连)       │
│                    │                             │
│  appId + appSecret │  HikariCP 连接池             │
│  + timestamp 签名   │  学校提供只读视图             │
│  CDSP REST API     │  SELECT * FROM v_xxx        │
│  cdsp.imau.edu.cn  │  增量字段: updated_at        │
└────────────────────┴─────────────────────────────┘
```

**切换方式**：环境变量 `SCHOOL_DATA_SOURCE=api`（默认）或 `etl`

### 数据同步机制

| 特性 | 实现 |
|------|------|
| 同步策略 | 全量 + 增量（基于 `updated_at`） |
| 调度方式 | Cron 定时任务（默认 3:30） |
| 链式拉取 | 学院 → 课程 → 教师 → 学生 → 绑定 |
| SSO 自动授权 | 学校目录已有学生 → 自动创建账号 |
| 学生端懒加载 | 四六级成绩按需拉取（陈旧度阈值 1 天） |
| 分页支持 | 1000 条/页，自动翻页 |

### 关键文件

| 文件 | 说明 |
|------|------|
| `SchoolDataProperties.java` | API/ETL 配置，9 个端点 URL |
| `SchoolDataSyncServiceImpl.java` | 同步核心逻辑 |
| `SchoolDataApiClientImpl.java` | API 模式客户端（签名认证） |
| `SchoolDataEtlClientImpl.java` | ETL 模式客户端（JDBC 直连） |
| `V12__school_data_integration.sql` | 学校数据表结构（~481 行） |

---

## 四、性能数据

### 当前状态

**无正式压测报告**，但有一些性能相关的基础设施：

| 项目 | 状态 | 说明 |
|------|------|------|
| Nginx 访问日志 | ✅ | 含 `$request_time` 响应时间字段 |
| 并发控制 | ✅ | AI 翻译 Semaphore(10)，Nginx 速率限制 |
| 数据库读写分离 | ✅ | 主库写 + 从库读，AOP 自动路由 |
| 健康检查 | ✅ | Docker healthcheck |
| 压测脚本 | ❌ | 无 JMeter/Locust/wrk 脚本 |
| TPS/QPS 基线 | ❌ | 无量化数据 |
| 缓存层 | ❌ | 无 Redis 缓存（Redis 仅用于 Session） |

### 快速建立性能基线

**推荐工具**：wrk（轻量、零配置）

```bash
# 1. 公开接口（无认证）
wrk -t4 -c100 -d30s http://localhost:8080/api/public/articles?page=1

# 2. 认证接口
wrk -t4 -c50 -d30s -s login.lua http://localhost:8080/api/student/texts

# 3. 写入接口
wrk -t2 -c20 -d30s -s post.lua http://localhost:8080/api/student/reading-progress
```

**建议产出**：

| 指标 | 目标值 | 说明 |
|------|--------|------|
| 公开接口 QPS | 记录当前值 | 作为基准 |
| 认证接口 QPS | 记录当前值 | 含 JWT 验签开销 |
| P50/P95/P99 延迟 | 记录当前值 | Nginx 日志可提取 |
| 并发 100 错误率 | <1% | 作为扩容决策依据 |

### 性能优化方向

| 优先级 | 方向 | 预期收益 |
|--------|------|---------|
| P0 | 热点数据加 Redis 缓存 | 读 QPS 提升 3-5x |
| P1 | 数据库连接池调优 | 并发能力提升 |
| P2 | Nginx gzip + 静态资源 CDN | 首屏加载减半 |
| P3 | API 响应分页默认值优化 | 减少不必要数据传输 |

---

## 五、数据库规模

### 表结构统计

| 类别 | 数量 | 说明 |
|------|------|------|
| Flyway 迁移文件 | ~15 个 | V0-V14（含合并） |
| Java Entity 类 | ~30 个 | `com.english.entity` 包 |
| 预估总表数 | **35-45 张** | 含系统表、中间表、字典表 |

### 主要数据表

| 表分类 | 代表表 | 说明 |
|--------|--------|------|
| 核心业务 | `users`, `texts`, `words`, `media` | 用户、课文、单词、媒体 |
| 学习系统 | `reading_progress`, `vocabulary_books`, `assignments` | 阅读进度、生词本、作业 |
| 学校数据 | `school_student`, `school_teacher`, `administrative_class`, `teaching_class`, `school_course`, `teacher_courses`, `course_selection`, `course_schedule` | 8 张学校数据表 |
| 字典 | `ecdict` | ECDICT 词典（74 万词条） |
| 系统配置 | `system_config`, `feature_toggles`, `ai_providers` | 系统配置 |
| 认证授权 | `sso_config`, `role_mapping` | SSO 集成 |

### 数据规模估计

| 数据项 | 规模 |
|--------|------|
| ECDICT 词典 | **74 万词条**（固定） |
| 测试/种子用户 | ~10 条 |
| 学校数据 | 取决于学校实际导入量（千级~万级） |
| 文章/课文 | 取决于运营导入量 |

> **获取精确数据**：`docker exec langhack-mysql mysql -uroot -p -e "SELECT table_name, table_rows FROM information_schema.tables WHERE table_schema='english_learning';"`

---

## 六、AI 问答链路

### 完整链路

```
┌──────────┐    ┌──────────┐    ┌──────────────┐    ┌──────────────┐
│ 前端选词  │───→│ Axios    │───→│ Controller   │───→│ AiProvider   │
│ 划词触发  │    │ POST请求 │    │ AiQaController│   │ Service      │
└──────────┘    └──────────┘    └──────────────┘    └──────┬───────┘
                                                           │
                                              ┌────────────┼────────────┐
                                              │            │            │
                                         HiAgent      OpenRouter   SiliconFlow
                                         (默认)        (备选)       (备选)
```

### 关键特性

| 特性 | 当前状态 | 说明 |
|------|---------|------|
| 输出模式 | **同步（非流式）** | 完整响应后一次返回，无 SSE |
| 超时控制 | HTTP 客户端默认 | ~30s，无自定义超时降级 |
| 并发限制 | Semaphore(10) | 翻译接口有并发控制 |
| 上下文窗口 | 按 provider 的 maxTokens | 无动态窗口管理 |
| 错误处理 | try-catch + 通用错误提示 | `e.getMessage()` 不直接返回客户端 |
| 提供商切换 | 配置化切换 | `application.yml` 中指定 `ai.provider` |

### 改进建议

| 方向 | 方案 | 收益 |
|------|------|------|
| 流式输出 | Spring Boot SSE（`SseEmitter`）→ 前端 `EventSource` | 用户体验显著提升，逐字展示 |
| 超时降级 | `CompletableFuture.orTimeout(15s)` + fallback 缓存结果 | 避免白屏等待 |
| 上下文管理 | 滑动窗口 + 摘要压缩（超限时自动截断+总结） | 支持长文章问答 |
| 结果缓存 | Redis 缓存相同 query 结果（TTL 1h） | 减少重复 API 调用成本 |

---

## 七、运维监控

### 当前状态

**基础级别，主要靠人工巡检。**

| 能力 | 状态 | 实现方式 |
|------|------|---------|
| 访问日志 | ✅ | Nginx `access.log`（含响应时间） |
| 错误日志 | ✅ | Nginx + Spring Boot 日志文件 |
| 健康检查 | ✅ | Docker `healthcheck` |
| 从库降级监测 | ✅ | `DynamicDataSource` 60s 探测 |
| 集中式日志 | ❌ | 无 ELK/Loki |
| 指标监控 | ❌ | 无 Prometheus/Grafana |
| 告警通知 | ❌ | 无邮件/钉钉/微信告警 |
| APM | ❌ | 无应用性能监控 |

### 最小监控栈方案

**新增 Docker Compose 服务**（2 个容器）：

```yaml
# docker-compose.yml 追加
prometheus:
  image: prom/prometheus
  volumes:
    - ./monitoring/prometheus.yml:/etc/prometheus/prometheus.yml
  ports:
    - "9090:9090"

grafana:
  image: grafana/grafana
  ports:
    - "3001:3000"
  depends_on:
    - prometheus
```

**Spring Boot 暴露指标**（`application.yml` 追加）：

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,metrics,prometheus
```

**产出**：一个 Grafana Dashboard 展示 QPS、延迟分布、错误率、JVM 内存、数据库连接池 5 个核心面板。

---

## 总结：当前核心卖点成熟度评估

| 卖点 | 成熟度 | 行动建议 |
|------|--------|---------|
| 智能分级转写 | 🔴 代码未实现 | **立即补齐**，至少单步 Prompt 改写 + 建立评测体系 |
| 语义保留率 ≥97% | 🔴 无测试数据 | 用 BERTScore 跑 100+ 篇评测，产出可引用的数字 |
| 推荐算法冷启动 | 🟡 已实现但简单 | 注册加英语水平字段，短期即可改善 |
| 学校数据库对接 | 🟢 完整 | 9 个数据源双模式，可直接作为亮点展示 |
| 性能数据 | 🔴 无基线 | wrk 跑一轮 30 分钟即可产出 TPS/P95 延迟 |
| AI 问答 | 🟡 基础可用 | 加流式输出 + 超时降级可显著提升体验 |
| 运维监控 | 🔴 基础级别 | 加 Prometheus+Grafana（2 个容器，半天工作量） |
| 数据库规模 | 🟢 中等 | 35-45 表 + 74 万词库，学生量取决于用户导入 |

> **优先级排序**：补齐分级转写 > 建立性能基线 > 加流式输出 > 监控 > 冷启动优化
