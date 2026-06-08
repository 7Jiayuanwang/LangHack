/**
 * SM-2 数据监控工具
 *
 * 收集和分析生词本学习数据，用于评估学习效果和算法表现
 */

// 后端 nextReviewTime 格式为 "yyyy-MM-dd HH:mm:ss"，需转为 ISO 8601 保证跨浏览器兼容
function parseDate(str) {
  if (!str) return null
  // 将 "2024-06-01 12:00:00" → "2024-06-01T12:00:00"
  return new Date(String(str).replace(' ', 'T'))
}

/**
 * 计算 EF 分布统计
 *
 * @param {Array} words - 单词列表
 * @returns {Object} EF 分布数据
 */
export function calculateEFDistribution(words) {
  if (!words || words.length === 0) {
    return {
      total: 0,
      ranges: {},
      average: 0,
      median: 0,
      hardWords: 0,  // EF < 2.0
      normalWords: 0,  // 2.0 ≤ EF < 3.0
      easyWords: 0  // EF ≥ 3.0
    }
  }

  const efValues = words
    .map(w => w.easinessFactor || 2.5)
    .filter(ef => ef >= 1.0 && ef <= 5.0)
    .sort((a, b) => a - b)

  const total = efValues.length
  const sum = efValues.reduce((acc, ef) => acc + ef, 0)
  const average = total > 0 ? sum / total : 0
  const median = total > 0 ? efValues[Math.floor(total / 2)] : 0

  // 按范围统计
  const ranges = {
    '1.0-1.5': efValues.filter(ef => ef >= 1.0 && ef < 1.5).length,
    '1.5-2.0': efValues.filter(ef => ef >= 1.5 && ef < 2.0).length,
    '2.0-2.5': efValues.filter(ef => ef >= 2.0 && ef < 2.5).length,
    '2.5-3.0': efValues.filter(ef => ef >= 2.5 && ef < 3.0).length,
    '3.0-4.0': efValues.filter(ef => ef >= 3.0 && ef < 4.0).length,
    '4.0-5.0': efValues.filter(ef => ef >= 4.0 && ef <= 5.0).length
  }

  // 阈值与 EF bar 色块一致：红 < 2.0，黄 2.0-3.0，绿 >= 3.0
  const hardWords = efValues.filter(ef => ef < 2.0).length
  const normalWords = efValues.filter(ef => ef >= 2.0 && ef < 3.0).length
  const easyWords = efValues.filter(ef => ef >= 3.0).length

  return {
    total,
    ranges,
    average: parseFloat(average.toFixed(2)),
    median: parseFloat(median.toFixed(2)),
    hardWords,
    normalWords,
    easyWords,
    hardWordRatio: total > 0 ? parseFloat((hardWords / total * 100).toFixed(1)) : 0
  }
}

/**
 * 计算平均间隔统计
 *
 * @param {Array} words - 单词列表
 * @returns {Object} 间隔分布数据
 */
export function calculateIntervalDistribution(words) {
  if (!words || words.length === 0) {
    return {
      total: 0,
      ranges: {},
      average: 0,
      median: 0,
      maxInterval: 0
    }
  }

  const intervals = words
    .map(w => w.interval || 0)
    .filter(i => i >= 0)
    .sort((a, b) => a - b)

  const total = intervals.length
  const sum = intervals.reduce((acc, i) => acc + i, 0)
  const average = total > 0 ? sum / total : 0
  const median = total > 0 ? intervals[Math.floor(total / 2)] : 0
  const maxInterval = total > 0 ? intervals[intervals.length - 1] : 0

  // 按范围统计（天）
  const ranges = {
    '0-1': intervals.filter(i => i >= 0 && i <= 1).length,
    '2-7': intervals.filter(i => i >= 2 && i <= 7).length,
    '8-30': intervals.filter(i => i >= 8 && i <= 30).length,
    '31-90': intervals.filter(i => i >= 31 && i <= 90).length,
    '91-180': intervals.filter(i => i >= 91 && i <= 180).length,
    '181+': intervals.filter(i => i >= 181).length
  }

  return {
    total,
    ranges,
    average: parseFloat(average.toFixed(1)),
    median: parseFloat(median.toFixed(1)),
    maxInterval
  }
}

/**
 * 估算记忆保持率
 *
 * 基于 SM-2 算法的简化估算（非精确计算，仅供参考）
 *
 * @param {Object} word - 单词对象
 * @returns {number} 估算保持率 (0-1)
 */
export function estimateRetentionRate(word) {
  if (!word || !word.nextReviewTime) return 1.0

  const now = new Date()
  const nextReview = parseDate(word.nextReviewTime)
  const daysUntilReview = (nextReview - now) / (24 * 60 * 60 * 1000)

  // 已经过期：保持率下降
  if (daysUntilReview < 0) {
    const daysOverdue = Math.abs(daysUntilReview)
    const ef = word.easinessFactor || 2.5
    // 简化模型：retention = e^(-overdue / (interval * EF))
    const interval = word.interval || 1
    const decayRate = daysOverdue / (interval * ef)
    return Math.max(0, Math.exp(-decayRate))
  }

  // 未到期：保持率较高
  return 1.0
}

/**
 * 计算记忆保持率统计
 *
 * @param {Array} words - 单词列表
 * @returns {Object} 保持率数据
 */
export function calculateRetentionStats(words) {
  if (!words || words.length === 0) {
    return {
      total: 0,
      averageRetention: 0,
      atRisk: 0,  // retention < 0.5
      needReview: 0,  // retention < 0.7
      stable: 0  // retention ≥ 0.7
    }
  }

  const retentions = words.map(w => ({
    word: w.word,
    retention: estimateRetentionRate(w)
  }))

  const total = retentions.length
  const sum = retentions.reduce((acc, r) => acc + r.retention, 0)
  const averageRetention = total > 0 ? sum / total : 0

  const atRisk = retentions.filter(r => r.retention < 0.5).length
  const needReview = retentions.filter(r => r.retention >= 0.5 && r.retention < 0.7).length
  const stable = retentions.filter(r => r.retention >= 0.7).length

  return {
    total,
    averageRetention: parseFloat((averageRetention * 100).toFixed(1)),  // 转为百分比
    atRisk,
    needReview,
    stable,
    atRiskRatio: total > 0 ? parseFloat((atRisk / total * 100).toFixed(1)) : 0
  }
}

/**
 * 生成学习报告
 *
 * @param {Array} words - 单词列表
 * @param {Object} options - 可选配置
 * @returns {Object} 完整学习报告
 */
export function generateLearningReport(words, options = {}) {
  const efDistribution = calculateEFDistribution(words)
  const intervalDistribution = calculateIntervalDistribution(words)
  const retentionStats = calculateRetentionStats(words)

  // 掌握状态统计（按 masteryLevel，与筛选 chip 含义一致）
  const repetitionStats = {
    new: words.filter(w => !w.masteryLevel || w.masteryLevel === 'NEW').length,
    learning: words.filter(w => w.masteryLevel === 'LEARNING' || w.masteryLevel === 'FAMILIAR').length,
    mature: words.filter(w => w.masteryLevel === 'MASTERED').length
  }

  // 到期状态统计
  const now = new Date()
  const dueStats = {
    overdue: words.filter(w => w.nextReviewTime && parseDate(w.nextReviewTime) < now).length,
    dueToday: words.filter(w => {
      if (!w.nextReviewTime) return false
      const nextReview = parseDate(w.nextReviewTime)
      const today = new Date(now.getFullYear(), now.getMonth(), now.getDate())
      const tomorrow = new Date(today.getTime() + 24 * 60 * 60 * 1000)
      return nextReview >= today && nextReview < tomorrow
    }).length,
    upcoming: words.filter(w => w.nextReviewTime && parseDate(w.nextReviewTime) > now).length
  }

  return {
    timestamp: new Date().toISOString(),
    totalWords: words.length,
    efDistribution,
    intervalDistribution,
    retentionStats,
    repetitionStats,
    dueStats,
    recommendations: generateRecommendations({
      efDistribution,
      retentionStats,
      dueStats,
      totalWords: words.length
    })
  }
}

/**
 * 生成学习建议
 *
 * @param {Object} reportData - 报告数据
 * @returns {Array} 建议列表
 */
function generateRecommendations(reportData) {
  const recommendations = []
  const { efDistribution, retentionStats, dueStats, totalWords } = reportData

  // 词库太小，给新手引导
  if (totalWords < 10) {
    recommendations.push({
      type: 'info',
      title: '词库较少',
      message: `当前生词本共 ${totalWords} 个单词。建议积累到 20 个以上，SM-2 间隔算法将逐渐发挥效果。`,
      action: null
    })
    return recommendations
  }

  // 困难词过多（EF < 2.0 超过 30%）
  if (efDistribution.hardWordRatio > 30) {
    recommendations.push({
      type: 'warning',
      title: '困难词较多',
      message: `${efDistribution.hardWordRatio}% 的单词 EF < 2.0，复习频率偏高。建议开启"困难词加强模式"，或减少每日新词量。`,
      action: 'enableHardWordMode'
    })
  }

  // 记忆保持率低（超过 20% 的词保持率 < 50%）
  if (retentionStats.atRiskRatio > 20) {
    recommendations.push({
      type: 'error',
      title: '遗忘风险高',
      message: `${retentionStats.atRiskRatio}% 的单词保持率不足 50%，面临遗忘。请优先复习逾期单词。`,
      action: 'reviewOverdueWords'
    })
  }

  // 逾期积压过多
  if (dueStats.overdue > 50) {
    recommendations.push({
      type: 'warning',
      title: '复习积压较重',
      message: `当前有 ${dueStats.overdue} 个逾期单词。建议开启"过载保护"，系统将自动分批安排复习。`,
      action: 'enableOverloadProtection'
    })
  }

  // 全是新词（interval=0），还没开始复习
  const allNew = efDistribution.total > 0 && efDistribution.average >= 2.4 && dueStats.overdue === 0 && dueStats.upcoming === 0
  if (allNew) {
    recommendations.push({
      type: 'info',
      title: '开始第一轮复习',
      message: `词库共 ${totalWords} 个单词，尚未开始 SM-2 复习。点击"开始复习"完成第一轮后，系统将自动安排个性化间隔。`,
      action: null
    })
    return recommendations
  }

  // 即将到期的词较多
  if (dueStats.dueToday > 10) {
    recommendations.push({
      type: 'info',
      title: '今日复习任务',
      message: `今天有 ${dueStats.dueToday} 个单词需要复习，建议尽快完成以保持记忆曲线。`,
      action: null
    })
  }

  // 学习状态良好
  if (
    recommendations.length === 0 &&
    efDistribution.hardWordRatio < 15 &&
    retentionStats.atRiskRatio < 10 &&
    dueStats.overdue < 20
  ) {
    recommendations.push({
      type: 'success',
      title: '学习状态良好',
      message: `记忆保持率稳定，平均 EF ${efDistribution.average}，可适当增加每日新词量。`,
      action: null
    })
  }

  return recommendations
}

/**
 * 导出学习数据（CSV 格式）
 *
 * @param {Array} words - 单词列表
 * @returns {string} CSV 字符串
 */
export function exportToCSV(words) {
  if (!words || words.length === 0) {
    return 'word,repetition,interval,easinessFactor,nextReviewTime,estimatedRetention\n'
  }

  const header = 'word,repetition,interval,easinessFactor,nextReviewTime,estimatedRetention\n'
  const rows = words.map(w => {
    const retention = estimateRetentionRate(w)
    return [
      w.word || '',
      w.repetition || 0,
      w.interval || 0,
      (w.easinessFactor || 2.5).toFixed(2),
      w.nextReviewTime || '',
      (retention * 100).toFixed(1) + '%'
    ].join(',')
  }).join('\n')

  return header + rows
}
