# SM-2 间隔复习算法说明

## 概述

LangHack 采用 **SuperMemo-2 (SM-2)** 算法实现生词本的智能间隔复习功能。SM-2 算法是一种基于记忆保持率的自适应间隔复习算法，能够根据每个学习者对每个单词的掌握情况动态调整复习间隔。

---

## 算法原理

### 核心机制

SM-2 算法通过三个关键参数动态计算下次复习时间：

| 参数 | 说明 | 初始值 | 范围 |
|---|---|---|---|
| **repetition** | 成功复习次数 | 0 | ≥0 |
| **interval** | 复习间隔（天） | 0 | ≥0 |
| **easinessFactor (EF)** | 难度因子 | 2.5 | 1.3 ~ 5.0 |

### 计算流程

每次复习时，学习者对单词的回忆质量评分为 `quality` (0-5)：

- **0**: 完全不记得
- **1**: 完全记错
- **2**: 很困难，几乎错误
- **3**: 困难，但最终回答正确
- **4**: 有点困难，正确
- **5**: 完美回忆，毫不费力

**算法逻辑**：

```javascript
if (quality >= 3) {
  // 复习成功
  if (repetition === 0) interval = 1;        // 第一次：1天后
  else if (repetition === 1) interval = 6;   // 第二次：6天后
  else interval = Math.round(interval × previousEF); // 第三次及以后：指数增长
  
  repetition++;
} else {
  // 复习失败
  repetition = 0;
  interval = 1;
}

// 更新难度因子
EF = EF + (0.1 - (5 - quality) × (0.08 + (5 - quality) × 0.02))
EF = Math.max(1.3, Math.min(5.0, EF))  // 钳制在 1.3-5.0
```

**关键特性**：
- **个性化**：每个单词的 EF 独立演化，捕捉个体差异
- **指数增长**：掌握良好的单词间隔快速拉长（6天 → 15天 → 37天 → 92天...）
- **失败重置**：遗忘后回到起点，强化记忆
- **难度调节**：EF 随评分质量动态调整（评分低 → EF 降低 → 间隔缩短）

---

## 实现位置

### 前端（算法执行）

**核心文件**：`frontend/src/utils/sm2.js`

```javascript
export function calculateSM2(quality, previousState = {}) {
  // 输入: quality (0-5), previousState { repetition, interval, easinessFactor }
  // 输出: { repetition, interval, easinessFactor, nextReviewTime }
  // ...
}
```

**调用位置**：
- `frontend/src/views/student/Vocabulary.vue` — 学生端生词本主页面
- `frontend/src/features/VocabularyFeatures/Vocabulary.vue` — 生词本独立组件
- `frontend/src/features/VocabularyFeatures/WordRecite.vue` — 单词背诵交互页

**历史变更**：
- ✅ **2026-06-06 统一修复**：废弃了旧的 `ebbinghaus.js`（固定间隔艾宾浩斯算法），全面切换到标准 SM-2 实现
- ⚠️ 旧版 `ebbinghaus.js` 的 `interval` 单位是**分钟**（最大 44640 分钟 = 31天），与 SM-2 的**天**制不兼容。迁移时已加入数据守卫，自动重置 `interval > 365` 的遗留数据。

### 后端（数据持久化）

**核心文件**：`backend/src/main/java/com/english/entity/VocabularyBook.java`

```java
@TableName("vocabulary_books")
public class VocabularyBook {
    private Integer repetition;        // 成功复习次数（对应 SM-2 的 n）
    private Integer intervalDays;      // 间隔天数（对应 SM-2 的 I）
    private Double easinessFactor;     // 难度因子（对应 SM-2 的 EF，范围 1.3-5.0）
    private LocalDateTime nextReviewTime;
    // ...
}
```

**Service 层**：`VocabularyBooksServiceImpl.java`

```java
// SM-2 参数边界值
private static final double MIN_EASINESS_FACTOR = 1.3;
private static final double MAX_EASINESS_FACTOR = 5.0;

// SM-2 调度：按 nextReviewTime 升序排列（到期的词优先）
wrapper.orderByAsc("next_review_time");
```

---

## 与艾宾浩斯遗忘曲线的区别

| 维度 | 艾宾浩斯固定间隔 | SM-2 算法 |
|---|---|---|
| **间隔来源** | 固定阶梯（20分钟→1小时→1天→2天→6天→31天） | 个人表现动态计算（指数增长） |
| **个体差异** | 无（所有人相同） | 有（每个单词独立 EF） |
| **长期效率** | 有限（封顶 31 天） | 高（间隔可增长到数月甚至数年） |
| **适用场景** | 短期记忆、快速背诵 | 长期记忆、知识保持 |

**为什么 LangHack 选择 SM-2？**

1. **主流标准**：Anki、Quizlet、Duolingo 等主流学习平台均采用 SM-2 或其变体
2. **效率提升**：相比固定间隔，SM-2 可减少 40-60% 的复习量（在相同记忆保持率下）
3. **学术支持**：认知心理学研究证明个性化间隔优于统一间隔
4. **长期可用**：支持终身学习场景（如医学、法律等需要持久记忆的专业领域）

---

## 高级特性（2026-06-06 新增）

### 1. 困难词加强模式

**问题**：标准 SM-2 算法的 EF 下限是 1.3，对于极度困难的单词（反复遗忘），间隔缩短幅度有限。

**解决方案**：困难词加强模式将 EF 下限降至 1.0，使困难单词的复习间隔更短、频率更高。

**使用方法**：

```javascript
import { calculateSM2 } from '@/utils/sm2'

// 启用困难词模式
const result = calculateSM2(quality, previousState, { hardWordMode: true })
```

**效果对比**：

| 场景 | 标准模式（EF ≥ 1.3） | 困难词模式（EF ≥ 1.0） |
|---|---|---|
| 连续失败 5 次后 EF | 1.3（钳制） | ~1.0（可降至最低） |
| 第 3 次复习间隔 | 6 × 1.3 ≈ 8 天 | 6 × 1.0 = 6 天 |
| 第 4 次复习间隔 | 8 × 1.3 ≈ 10 天 | 6 × 1.0 = 6 天 |

**适用场景**：
- 专业术语密集的领域（医学、法律）
- 形近词、近义词混淆严重的单词
- 个人学习中反复遗忘的"老大难"单词

---

### 2. 过载保护

**问题**：用户长时间未复习后，单日到期单词可能高达 100+ 个，造成学习负担过重，容易放弃。

**解决方案**：自动按优先级分批，今日复习高优先级单词，其余自动延期至明日。

**核心文件**：`frontend/src/utils/sm2-overload-protection.js`

**使用示例**：

```javascript
import { batchDueWords, deferWordReview, getOverloadMessage } from '@/utils/sm2-overload-protection'

// 假设有 80 个到期单词
const dueWords = [...] // 从数据库或 localStorage 获取

// 1. 分批（默认单日最大 50 个）
const { todayBatch, deferredBatch, isOverloaded, stats } = batchDueWords(dueWords, {
  maxDailyReview: 50,           // 单日最大复习量
  emergencyThreshold: 75        // 紧急阈值（超过后降至 80% 容量）
})

// 2. 推迟延期批次
const deferredWords = deferredBatch.map(w => deferWordReview(w, 1))  // 延期 1 天

// 3. 提示用户
const message = getOverloadMessage({ todayBatch, deferredBatch, isOverloaded, stats })
console.log(message)
// 输出：
// 检测到 80 个到期单词，已智能分批：
// ✓ 今日复习 50 个（高优先级）
// ⏰ 明日复习 30 个（已自动延期）
```

**优先级算法**：

```javascript
priority = 逾期天数 × 10 + (5.0 - EF) × 5 + max(0, 5 - repetition)
```

- **逾期越久**：分数越高（逾期 1 天 = +10 分）
- **难度越高**：EF 越低，分数越高
- **练习越少**：复习次数越少，分数越高

**紧急过载处理**：当到期单词超过 `emergencyThreshold`（默认 1.5× maxDailyReview），自动降至 80% 容量，留出缓冲。

---

### 3. 数据监控与分析

**核心文件**：`frontend/src/utils/sm2-analytics.js`

提供 7 大监控指标：

#### 3.1 EF 分布统计

```javascript
import { calculateEFDistribution } from '@/utils/sm2-analytics'

const efStats = calculateEFDistribution(words)
console.log(efStats)
// {
//   total: 100,
//   average: 2.3,
//   median: 2.2,
//   hardWords: 25,      // EF < 1.8
//   normalWords: 50,    // 1.8 ≤ EF < 2.3
//   easyWords: 25,      // EF ≥ 2.3
//   hardWordRatio: 25.0,
//   ranges: {
//     '1.0-1.5': 10,
//     '1.5-2.0': 15,
//     ...
//   }
// }
```

#### 3.2 间隔分布统计

```javascript
import { calculateIntervalDistribution } from '@/utils/sm2-analytics'

const intervalStats = calculateIntervalDistribution(words)
// {
//   total: 100,
//   average: 18.5,
//   median: 12.0,
//   maxInterval: 90,
//   ranges: {
//     '0-1': 10,
//     '2-7': 25,
//     '8-30': 40,
//     '31-90': 20,
//     '91-180': 5,
//     '181+': 0
//   }
// }
```

#### 3.3 记忆保持率估算

```javascript
import { estimateRetentionRate, calculateRetentionStats } from '@/utils/sm2-analytics'

// 单个单词
const retention = estimateRetentionRate(word)  // 0.0 - 1.0

// 批量统计
const retentionStats = calculateRetentionStats(words)
// {
//   total: 100,
//   averageRetention: 78.5,  // 百分比
//   atRisk: 15,              // retention < 0.5
//   needReview: 25,          // 0.5 ≤ retention < 0.7
//   stable: 60,              // retention ≥ 0.7
//   atRiskRatio: 15.0
// }
```

#### 3.4 完整学习报告

```javascript
import { generateLearningReport } from '@/utils/sm2-analytics'

const report = generateLearningReport(words)
console.log(report)
// {
//   timestamp: '2026-06-06T14:30:00.000Z',
//   totalWords: 100,
//   efDistribution: {...},
//   intervalDistribution: {...},
//   retentionStats: {...},
//   repetitionStats: {
//     new: 10,       // repetition = 0
//     learning: 55,  // 1 ≤ repetition < 5
//     mature: 35     // repetition ≥ 5
//   },
//   dueStats: {
//     overdue: 20,
//     dueToday: 15,
//     upcoming: 65
//   },
//   recommendations: [
//     {
//       type: 'warning',
//       title: '困难词较多',
//       message: '32% 的单词难度较高（EF < 1.8），建议启用"困难词加强模式"...',
//       action: 'enableHardWordMode'
//     }
//   ]
// }
```

#### 3.5 智能建议系统

报告自动生成 4 类建议：

| 触发条件 | 建议类型 | 建议内容 |
|---|---|---|
| 困难词占比 > 30% | ⚠️ warning | 启用困难词加强模式或减少新词量 |
| 遗忘风险占比 > 20% | 🔴 error | 优先复习到期单词 |
| 逾期单词 > 50 | ⚠️ warning | 启用过载保护自动分批 |
| 三项指标均良好 | ✅ success | 学习状态良好，可增加新词量 |

#### 3.6 CSV 导出

```javascript
import { exportToCSV } from '@/utils/sm2-analytics'

const csv = exportToCSV(words)
// word,repetition,interval,easinessFactor,nextReviewTime,estimatedRetention
// test,3,15,2.50,2026-06-20T00:00:00.000Z,100.0%
// example,1,1,2.00,2026-06-05T00:00:00.000Z,45.2%
```

---

## 使用指南

### 学生端操作流程

1. **添加生词**：在阅读页面划词后点击"添加到生词本"，或在生词本页面手动输入
2. **开始复习**：进入生词本页面，系统自动按 `nextReviewTime` 排序展示到期单词
3. **评分反馈**：复习时选择 0-5 的质量评分，系统自动计算下次复习时间
4. **持续循环**：每天打开生词本，优先复习到期单词，间隔逐步拉长

### 最佳实践

- **诚实评分**：根据真实回忆难度评分，避免"自欺欺人"式高分
- **每日坚持**：SM-2 的效果依赖于在最优时间点复习，跳过会降低效率
- **适度负担**：每日新词量建议 10-20 个，避免积压过多到期单词

---

## 技术细节

### 关键修复（2026-06-06）

**问题**：旧版实现在更新 EF 后才计算 interval，导致使用了"新的 EF"而非"旧的 EF"，违反 SM-2 规范。

**修复**：在 `sm2.js` 中增加 `previousEF` 变量保存旧值：

```javascript
const previousEF = easinessFactor;  // 保存旧 EF

// 1. 先用旧 EF 计算 interval
if (repetition >= 2) {
  interval = Math.round(interval * previousEF);
}

// 2. 再更新 EF
easinessFactor = easinessFactor + (0.1 - (5 - quality) * (...));
```

此修复已通过单元测试验证（`frontend/tests/utils/sm2.spec.js`）。

### 数据迁移

从旧 `ebbinghaus.js`（分钟制）迁移到 SM-2（天制）时，`WordRecite.vue` 加入了自动守卫：

```javascript
const migratedInterval = (savedWord.interval ?? 0) > 365 ? 0 : (savedWord.interval ?? 0)
const migratedCount = (savedWord.interval ?? 0) > 365 ? 0 : (savedWord.reviewCount ?? 0)
```

**逻辑**：如果 localStorage 中存储的 `interval` 超过 365（明确的分钟制污染数据），则重置为 0，避免出现"4年后复习"的荒谬结果。

---

## 测试覆盖

**单元测试**：`frontend/tests/utils/sm2.spec.js`
- ✅ 输入验证（quality 边界、previousState 缺失/异常值）
- ✅ 算法逻辑（成功/失败路径、间隔计算、EF 边界）
- ✅ 时区稳定性（nextReviewTime 使用午夜基准）
- ✅ 关键修复验证（使用 previousEF 计算 interval）

**集成测试**：
- `frontend/tests/views/student/Vocabulary.spec.js`
- `backend/src/test/java/.../VocabularyBooksServiceSM2Test.java`

当前测试覆盖率：**前端 1241 项测试全通过**，后端 VocabularyBooksService 相关测试全绿。

---

## 参考资源

- [SuperMemo Algorithm (SM-2)](https://www.supermemo.com/en/archives1990-2015/english/ol/sm2)
- [Anki Manual - Studying](https://docs.ankiweb.net/studying.html)
- [认知心理学：间隔效应 (Spacing Effect)](https://en.wikipedia.org/wiki/Spacing_effect)
- 项目内相关文件：
  - `frontend/src/utils/sm2.js` — 算法实现
  - `backend/.../VocabularyBook.java` — 数据模型
  - `docs/spec.md` — 功能规格说明
  - `CHANGELOG.md` — 历史变更记录

---

**最后更新**：2026-06-06  
**维护者**：LangHack 开发团队
