/**
 * SM-2 过载保护 (Overload Protection)
 *
 * 当单日到期单词过多时自动分批，避免学习者负担过重
 */

/**
 * 计算单词的过载优先级
 * 优先级越高，越应该今天复习
 *
 * @param {Object} word - 单词对象
 * @param {string} word.nextReviewTime - 下次复习时间 (ISO string)
 * @param {number} word.easinessFactor - 难度因子
 * @param {number} word.repetition - 复习次数
 * @returns {number} 优先级分数（越高越优先）
 */
export function calculateOverloadPriority(word) {
  if (!word || !word.nextReviewTime) return 0

  const now = new Date()
  const nextReview = new Date(word.nextReviewTime)
  const daysOverdue = (now - nextReview) / (24 * 60 * 60 * 1000)

  // 优先级 = 逾期天数权重 + 难度权重 + 熟练度权重
  const overdueScore = Math.max(0, daysOverdue) * 10  // 逾期1天 = +10分
  const difficultyScore = (5.0 - (word.easinessFactor || 2.5)) * 5  // EF越低（越难）= 越高分
  const proficiencyScore = Math.max(0, 5 - (word.repetition || 0))  // 复习次数越少 = 越高分

  return overdueScore + difficultyScore + proficiencyScore
}

/**
 * 分批到期单词（过载保护）
 *
 * @param {Array} dueWords - 到期单词列表
 * @param {Object} options - 配置选项
 * @param {number} options.maxDailyReview - 单日最大复习量（默认 50）
 * @param {number} options.emergencyThreshold - 紧急阈值（默认 1.5x maxDailyReview）
 * @returns {Object} { todayBatch, deferredBatch, isOverloaded }
 */
export function batchDueWords(dueWords, options = {}) {
  const {
    maxDailyReview = 50,
    emergencyThreshold = maxDailyReview * 1.5
  } = options

  if (!dueWords || dueWords.length === 0) {
    return {
      todayBatch: [],
      deferredBatch: [],
      isOverloaded: false,
      stats: { total: 0, today: 0, deferred: 0 }
    }
  }

  // 未过载：全部返回
  if (dueWords.length <= maxDailyReview) {
    return {
      todayBatch: dueWords,
      deferredBatch: [],
      isOverloaded: false,
      stats: {
        total: dueWords.length,
        today: dueWords.length,
        deferred: 0
      }
    }
  }

  // 过载：按优先级排序，取前 N 个
  const sortedWords = [...dueWords].map(word => ({
    ...word,
    _priority: calculateOverloadPriority(word)
  })).sort((a, b) => b._priority - a._priority)

  const batchSize = dueWords.length > emergencyThreshold
    ? Math.ceil(maxDailyReview * 0.8)  // 严重过载：只取 80%，留缓冲
    : maxDailyReview

  const todayBatch = sortedWords.slice(0, batchSize).map(w => {
    const { _priority, ...word } = w
    return word
  })

  const deferredBatch = sortedWords.slice(batchSize).map(w => {
    const { _priority, ...word } = w
    return word
  })

  return {
    todayBatch,
    deferredBatch,
    isOverloaded: true,
    stats: {
      total: dueWords.length,
      today: todayBatch.length,
      deferred: deferredBatch.length
    }
  }
}

/**
 * 推迟单词复习时间（用于过载保护的延期批次）
 *
 * @param {Object} word - 单词对象
 * @param {number} deferDays - 推迟天数（默认 1 天）
 * @returns {Object} 更新后的单词对象（含新的 nextReviewTime）
 */
export function deferWordReview(word, deferDays = 1) {
  if (!word || !word.nextReviewTime) return word

  const currentReviewTime = new Date(word.nextReviewTime)
  const newReviewTime = new Date(currentReviewTime.getTime() + deferDays * 24 * 60 * 60 * 1000)

  return {
    ...word,
    nextReviewTime: newReviewTime.toISOString(),
    _deferred: true,  // 标记为已推迟
    _deferredAt: new Date().toISOString()
  }
}

/**
 * 生成过载保护提示信息
 *
 * @param {Object} batchResult - batchDueWords 的返回结果
 * @returns {string} 用户友好的提示信息
 */
export function getOverloadMessage(batchResult) {
  if (!batchResult.isOverloaded) {
    return `今日复习 ${batchResult.stats.today} 个单词`
  }

  const { today, deferred, total } = batchResult.stats

  return `检测到 ${total} 个到期单词，已智能分批：\n` +
    `✓ 今日复习 ${today} 个（高优先级）\n` +
    `⏰ 明日复习 ${deferred} 个（已自动延期）\n\n` +
    `提示：优先复习逾期久、难度高、复习次数少的单词`
}
