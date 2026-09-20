# 推荐系统文档

> 基于用户-文章交互评分与余弦相似度的个性化推荐引擎

> 注：本文记录保留的旧协同过滤能力与原页面方案。当前文章首页已改用轻量、可解释的 [文章首页 Feed V1](ARTICLE_FEED_V1.md)；下文“推荐 Tab”前端描述仅供历史参考。

---

## 1. 概述

推荐系统为学生端 `/student/articles` 页面提供个性化文章推荐功能。系统通过分析用户与文章的交互行为（阅读、收藏、完成学习记录等），利用余弦相似度算法计算文章间和用户间的相似关系，生成个性化推荐列表。

---

## 2. 技术架构

### 2.1 核心组件

| 组件 | 文件路径 | 说明 |
|------|---------|------|
| 交互评分视图 | `db/migration/V12__similarity_matrix.sql` | `v_user_text_interaction_scores` 视图，聚合用户-文章交互特征 |
| 相似度矩阵表 | `db/migration/V12__similarity_matrix.sql` | `text_similarity_matrix` / `user_similarity_matrix` 及各自 tmp / old 表 |
| Mapper | `mapper/SimilarityMatrixMapper.java` | 矩阵重算 SQL 与推荐查询 SQL |
| 定时任务 | `config/SimilarityMatrixScheduleTask.java` | Shadow Table 策略的零停机矩阵重算 |
| 推荐接口 | `controller/PublicTextController.java` | `GET /api/public/texts/recommended` |

### 2.2 数据流

```
用户行为数据
  ├── user_text_reads        (阅读记录)
  ├── text_learning_records  (学习记录，含完成状态、时长)
  ├── article_favorites      (收藏)
  ├── underlines             (划线次数)
  └── score_records         (习题正确率)
       ↓
  v_user_text_interaction_scores (加权评分视图)
       │
       ├──→ text_similarity_matrix  (文章-文章相似度)
       └──→ user_similarity_matrix  (用户-用户相似度)
            ↓
       推荐查询 API → 前端"推荐" Tab
```

---

## 3. 交互评分算法

`v_user_text_interaction_scores` 视图为每个用户-文章对计算加权综合评分：

| 特征 | 权重 | 说明 |
|------|------|------|
| `w_read` | 0.2 | 是否已读（0/1） |
| `w_complete` | 0.2 | 是否完成阅读（0/1） |
| `w_favorite` | 0.3 | 是否收藏（0/1） |
| `w_duration` | 0.1 | 阅读时长得分（满分 600 秒） |
| `w_accuracy` | 0.1 | 习题正确率 |
| `w_underline` | 0.1 | 划线次数得分（满分 10 次） |

最终分数取所有特征加权求和，封顶 1.0：

```
interaction_score = LEAST(w_read×0.2 + w_complete×0.2 + w_favorite×0.3 +
                        w_duration×0.1 + w_accuracy×0.1 + w_underline×0.1, 1.0)
```

---

## 4. 余弦相似度算法

### 4.1 文章相似度

```sql
cos(t1, t2) = Σ(s1_i × s2_i) / (√Σs1_i² × √Σs2_i²)
```

- 只在两篇文章**有共同评分用户**时计算（`s1.user_id = s2.user_id`）
- 通过窗口函数 `SQRT(SUM(POW(score, 2)) OVER (PARTITION BY text_id))` 计算向量模长
- 过滤 `similarity > 0.01` 的低相似度记录

### 4.2 用户相似度

```sql
cos(u1, u2) = Σ(s1_i × s2_i) / (√Σs1_i² × √Σs2_i²)
```

- 只在两用户**对同一篇文章都有评分**时计算（`s1.text_id = s2.text_id`）
- 同理使用窗口函数计算用户向量模长

---

## 5. Shadow Table 零停机策略

矩阵重算采用 **Shadow Table** 策略（`SimilarityMatrixScheduleTask`），确保线上服务不中断：

```
Step 1: TRUNCATE text_similarity_matrix_tmp     (清空临时表)
Step 2: INSERT INTO text_similarity_matrix_tmp   (重算并插入新数据)
Step 3: RENAME TABLE                             (原子切换: 正式表↔临时表)
Step 4: DROP old + CREATE tmp                   (清理旧表，重建空 tmp 表备下次)
```

- 定时任务 cron 表达式：`0 0 3 * * ?`（每日凌晨 3:00），目前注释掉供开发测试手动调用
- 手动触发接口：`POST /api/admin/similarity/recompute/{text|user|all}`

---

## 6. API 接口

### 6.1 获取推荐文章

```
GET /api/public/texts/recommended
```

**权限**: `hasAnyRole('USER', 'STUDENT')` — 需要学生/用户登录

**请求参数**:
| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| count | int | 12 | 返回推荐文章数量，最大 50 |

**推荐逻辑**:
1. 未登录 → 返回空列表
2. 已登录但无阅读记录 → 回退到 `is_featured=1` 的文章（featured）
3. 已登录有阅读记录 → 查询 `text_similarity_matrix`，返回相似且未读的文章，按相似度降序

**响应示例**:
```json
{
  "code": 200,
  "data": [
    {
      "id": 421,
      "title": "New start, new challenges",
      "difficulty": "MEDIUM",
      "content": "...",
      "isFeatured": 1
    }
  ],
  "message": "success"
}
```

### 6.2 手动触发矩阵重算（Admin）

```
POST /api/admin/similarity/recompute/text
POST /api/admin/similarity/recompute/user
POST /api/admin/similarity/recompute/all
```

**权限**: `hasRole('ADMIN') or hasRole('OPS')`

---

## 7. 数据库表结构

### 7.1 核心表

| 表名 | 用途 |
|------|------|
| `text_similarity_matrix` | 文章相似度矩阵（正式表） |
| `text_similarity_matrix_tmp` | 文章相似度矩阵（临时表/Shadow Table） |
| `text_similarity_matrix_old` | 重算时旧表（临时） |
| `user_similarity_matrix` | 用户相似度矩阵（正式表） |
| `user_similarity_matrix_tmp` | 用户相似度矩阵（临时表/Shadow Table） |
| `user_similarity_matrix_old` | 重算时旧表（临时） |

**表结构（`text_similarity_matrix`）**:
```sql
CREATE TABLE text_similarity_matrix (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    text_id BIGINT NOT NULL COMMENT '文章ID',
    similar_text_id BIGINT NOT NULL COMMENT '相似文章ID',
    similarity DECIMAL(5,4) NOT NULL COMMENT '相似度(0.0000~0.9999)',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_text_pair (text_id, similar_text_id),
    INDEX idx_text_id (text_id),
    INDEX idx_similarity (similarity DESC)
)
```

### 7.2 视图

`v_user_text_interaction_scores` — 用户-文章交互评分视图，字段见上方第 3 节。

---

## 8. 前端集成

### 8.1 "推荐" Tab

`frontend/src/views/student/Articles.vue` 中 categories 数组添加了"推荐" tab：

```js
const categories = [
  { label: '全部', value: '', icon: 'House' },
  { label: '推荐', value: 'recommendation', icon: 'Star' },  // 新增
  { label: '科技', value: 'technology', icon: 'Monitor' },
  // ...
]
```

### 8.2 加载逻辑

- 切换到"推荐" tab 时调用 `GET /api/public/texts/recommended?count=12`
- 推荐数据通过 `recommendedArticles` ref 独立管理，不受分类筛选影响
- 未登录或获取失败时显示空状态

### 8.3 权限处理

推荐接口要求登录，前端需要确保在未登录状态下：
- 若用户已登录（`useUserStore` 有 token）→ 显示"推荐" tab
- 若用户未登录 → "推荐" tab 仍然可见，但调用 API 会收到 403，前端显示空状态或提示登录

---

## 9. 定时任务配置

生产环境启用定时重算，取消 `SimilarityMatrixScheduleTask` 中 `@Scheduled` 注解的注释：

```java
@Scheduled(cron = "0 0 3 * * ?")  // 每日凌晨 3:00
public void recomputeTextSimilarityMatrix() { ... }
```

建议配合运维监控（如 Prometheus + Grafana）告警矩阵重算失败情况。

---

## 10. 注意事项

1. **MySQL JDBC `allowMultiQueries=true`**: RENAME TABLE 等 DDL 语句需要开启多语句支持，已在 `application.yml` 中配置
2. **INSERT 返回值**: MySQL JDBC 对 `INSERT ... WITH ... SELECT` 返回 0 行影响，需要用 `COUNT(*)` 查询实际插入数量
3. **矩阵稀疏性**: 如果用户阅读量少（共同评分用户少），相似度矩阵记录会很少，此时推荐结果有限，建议回退到 featured
4. **数据量增长**: 矩阵表按 O(n²) 增长，文章数和用户数增长时需关注性能，必要时考虑分批计算或增量更新
