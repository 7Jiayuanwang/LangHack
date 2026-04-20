// 艾宾浩斯遗忘曲线算法实现

// 标准间隔（分钟）
// 20分钟 -> 1小时 -> 24小时 -> 48小时 -> 6天 -> 31天
const EBBINGHAUS_INTERVALS = [20, 60, 1440, 2880, 8640, 44640]

// 记忆保持率阈值
const MEMORY_RETENTION_THRESHOLD = 0.7

// 最小和最大难度因子
const MIN_EASINESS_FACTOR = 1.3
const MAX_EASINESS_FACTOR = 2.5

/**
 * 计算艾宾浩斯遗忘曲线的下次复习时间
 * @param {number} quality - 复习质量 (0-5)
 *   0: 完全失败
 *   1: 非常困难
 *   2: 困难
 *   3: 一般
 *   4: 容易
 *   5: 非常容易
 * @param {Object} params - 参数对象
 * @param {number} params.reviewCount - 复习次数
 * @param {number} params.interval - 当前间隔（分钟）
 * @param {number} params.easinessFactor - 难度因子
 * @returns {Object} 包含 interval, repetition, easinessFactor 的对象
 */
export function calculateEbbinghaus(quality, { reviewCount, interval, easinessFactor }) {
  let newInterval
  let newRepetition = reviewCount
  let newEasinessFactor = easinessFactor

  if (quality >= 3) {
    // 复习成功，按艾宾浩斯间隔增加
    const stageIndex = Math.min(reviewCount, EBBINGHAUS_INTERVALS.length - 1)
    newInterval = EBBINGHAUS_INTERVALS[stageIndex]
    newRepetition = reviewCount + 1
    
    // 调整难度因子
    newEasinessFactor = easinessFactor + (0.1 - (5 - quality) * (0.08 + (5 - quality) * 0.02))
    newEasinessFactor = Math.max(MIN_EASINESS_FACTOR, Math.min(MAX_EASINESS_FACTOR, newEasinessFactor))
  } else {
    // 复习失败，重新开始
    newInterval = 20 // 20分钟后重新复习
    newRepetition = 0
    
    // 降低难度因子
    newEasinessFactor = Math.max(MIN_EASINESS_FACTOR, easinessFactor - 0.2)
  }

  return {
    interval: newInterval,
    repetition: newRepetition,
    easinessFactor: newEasinessFactor
  }
}

/**
 * 计算记忆保持率
 * @param {number} interval - 间隔时间（分钟）
 * @param {number} easinessFactor - 难度因子
 * @returns {number} 记忆保持率 (0-1)
 */
export function calculateRetentionRate(interval, easinessFactor) {
  // 简化的记忆衰减模型
  const days = interval / 1440 // 转换为天
  const retention = Math.pow(days, -1 / easinessFactor)
  return Math.max(0, Math.min(1, retention))
}

/**
 * 计算复习优先级
 * @param {Object} word - 单词对象
 * @returns {number} 优先级分数（越高越应该优先复习）
 */
export function calculateReviewPriority(word) {
  if (!word) return 0

  const now = new Date()
  const nextReviewTime = word.nextReviewTime ? new Date(word.nextReviewTime) : new Date(0)
  const timeUntilReview = nextReviewTime - now
  const retentionRate = calculateRetentionRate(word.interval || 0, word.easinessFactor || 2.5)
  
  // 优先级 = (逾期时间 / 一天) + (1 - 记忆保持率) + 复习次数权重
  const overdueScore = Math.max(0, -timeUntilReview / (24 * 60 * 60 * 1000)) // 逾期天数
  const retentionScore = 1 - retentionRate
  const repetitionScore = Math.min(word.repetition || 0, 10) / 10 // 复习次数权重
  
  return overdueScore + retentionScore + repetitionScore
}

/**
 * 智能调度单词复习
 * @param {Array} words - 单词数组
 * @param {number} batchSize - 批次大小
 * @returns {Array} 调度后的单词数组
 */
export function scheduleReviewWords(words, batchSize = 20) {
  if (!words || words.length === 0) return []

  // 计算每个单词的复习优先级
  const wordsWithPriority = words.map(word => ({
    ...word,
    priority: calculateReviewPriority(word)
  }))

  // 按优先级排序
  wordsWithPriority.sort((a, b) => b.priority - a.priority)

  // 返回指定数量的单词
  return wordsWithPriority.slice(0, batchSize)
}

/**
 * 获取艾宾浩斯阶段信息
 * @param {number} repetition - 复习次数
 * @returns {Object} 阶段信息
 */
export function getEbbinghausStage(repetition) {
  const stages = [
    { name: '初次学习', description: '20分钟后复习' },
    { name: '第一次复习', description: '1小时后复习' },
    { name: '第二次复习', description: '1天后复习' },
    { name: '第三次复习', description: '2天后复习' },
    { name: '第四次复习', description: '6天后复习' },
    { name: '第五次复习', description: '31天后复习' },
    { name: '巩固期', description: '定期复习' }
  ]

  const stageIndex = Math.min(repetition, stages.length - 1)
  return stages[stageIndex]
}

/**
 * 检查单词是否应该复习
 * @param {Object} word - 单词对象
 * @returns {boolean} 是否应该复习
 */
export function shouldReviewWord(word) {
  if (!word) return false

  const now = new Date()
  const nextReviewTime = word.nextReviewTime ? new Date(word.nextReviewTime) : new Date(0)
  
  // 如果没有设置复习时间或已经过了复习时间，应该复习
  return !word.nextReviewTime || nextReviewTime <= now
}
