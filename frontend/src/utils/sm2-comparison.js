/**
 * SM-2 不同模式对比分析
 *
 * 在 A/B 测试场景下，对比不同 SM-2 变体在同一组输入下的表现差异
 */

import { calculateSM2 } from './sm2'
import { batchDueWords } from './sm2-overload-protection'

/**
 * 对比配置：4 种模式
 */
export const VARIANTS = {
 standard: {
 id: 'standard',
 name: '标准 SM-2',
 description: 'EF 下限 1.3，标准间隔算法',
 sm2Options: { hardWordMode: false },
 overload: false
 },
 hard: {
 id: 'hard',
 name: '困难词模式',
 description: 'EF 下限 1.0，困难单词复习更频繁',
 sm2Options: { hardWordMode: true },
 overload: false
 },
 overload: {
 id: 'overload',
 name: '过载保护',
 description: '标准 EF，但单日复习量限制 50',
 sm2Options: { hardWordMode: false },
 overload: true
 },
 hybrid: {
 id: 'hybrid',
 name: '混合模式',
 description: 'EF 1.0 + 过载保护（双管齐下）',
 sm2Options: { hardWordMode: true },
 overload: true
 }
}

/**
 * 模拟 N 次复习后各变体的指标
 *
 * @param {Array} words - 单词初始状态数组 [{ word, quality, easinessFactor, repetition, interval }]
 * @param {Object} options - 配置
 * @param {number} options.iterations - 模拟复习轮数（默认 30）
 * @returns {Object} 各变体的对比数据
 */
export function simulateVariants(words, options = {}) {
 const iterations = options.iterations || 30
 const results = {}

 for (const [id, variant] of Object.entries(VARIANTS)) {
 results[id] = simulateVariant(words, variant, iterations, options)
 }

 return results
}

/**
 * 模拟单个变体的复习表现
 */
function simulateVariant(words, variant, iterations, options) {
 const stateMap = new Map()
 const detailRecords = []

 // 初始化状态
 for (const w of words) {
 stateMap.set(w.word, {
 word: w.word,
 quality: w.quality || 3,
 easinessFactor: w.easinessFactor || 2.5,
 repetition: w.repetition || 0,
 interval: w.interval || 0,
 lastInterval: w.interval || 0,
 nextReviewTime: null
 })
 }

 let totalReviews = 0
 let totalSuccesses = 0
 let totalFailures = 0
 let sumEF = 0
 let sumInterval = 0
 let totalOverdue = 0
 let overloadDeferrals = 0

 for (let i = 0; i < iterations; i++) {
 // 收集当前到期单词
 const dueWords = []
 for (const [word, state] of stateMap) {
 if (state.nextReviewTime === null || i >= state.nextReviewTime) {
 dueWords.push(word)
 }
 }

 if (dueWords.length === 0) continue

 // 过载保护分批
 let toReview = dueWords
 if (variant.overload) {
 const batched = batchDueWords(
 dueWords.map(w => ({ word: w, nextReviewTime: new Date().toISOString() })),
 { maxDailyReview: options.maxDailyReview || 50, emergencyThreshold: options.emergencyThreshold || 75 }
 )
 toReview = batched.todayBatch.map(w => w.word || w)
 overloadDeferrals += batched.deferredBatch.length
 }

 // 模拟复习每个单词
 for (const word of toReview) {
 const state = stateMap.get(word)
 if (!state) continue

 const before = {
 repetition: state.repetition,
 interval: state.interval,
 easinessFactor: state.easinessFactor
 }

 const result = calculateSM2(state.quality, state, variant.sm2Options)
 state.repetition = result.repetition
 state.interval = result.interval
 state.easinessFactor = result.easinessFactor
 state.lastInterval = before.interval
 state.nextReviewTime = i + result.interval

 totalReviews++
 if (state.quality >= 3) totalSuccesses++
 else totalFailures++

 sumEF += result.easinessFactor
 sumInterval += result.interval

 if (i + result.interval <= i) totalOverdue++
 }
 }

 // 计算汇总指标
 const totalWords = stateMap.size
 const successRate = totalReviews > 0 ? totalSuccesses / totalReviews : 0
 const avgEF = totalReviews > 0 ? sumEF / totalReviews : 0
 const avgInterval = totalReviews > 0 ? sumInterval / totalReviews : 0

 // 困难词占比
 const hardWords = Array.from(stateMap.values())
 .filter(s => s.easinessFactor < 1.8).length

 // 已掌握数量（repetition >= 5）
 const masteredWords = Array.from(stateMap.values())
 .filter(s => s.repetition >= 5).length

 return {
 variant: variant.id,
 name: variant.name,
 description: variant.description,
 iterations,
 totalReviews,
 totalSuccesses,
 totalFailures,
 successRate: parseFloat((successRate * 100).toFixed(1)),
 avgEF: parseFloat(avgEF.toFixed(2)),
 avgInterval: parseFloat(avgInterval.toFixed(1)),
 hardWords,
 hardWordRatio: totalWords > 0 ? parseFloat((hardWords / totalWords * 100).toFixed(1)) : 0,
 masteredWords,
 masteryRatio: totalWords > 0 ? parseFloat((masteredWords / totalWords * 100).toFixed(1)) : 0,
 overloadDeferrals,
 totalOverdue,
 stateMap: Object.fromEntries(stateMap)
 }
}

/**
 * 生成对比报告（人类可读）
 */
export function generateComparisonReport(simulationResults) {
 const lines = ['# SM-2 模式对比报告\n']

 for (const [id, result] of Object.entries(simulationResults)) {
 lines.push(`## ${result.name}\n`)
 lines.push(`> ${result.description}\n`)
 lines.push(`- **总复习次数**: ${result.totalReviews}`)
 lines.push(`- **成功率**: ${result.successRate}% (${result.totalSuccesses}/${result.totalReviews})`)
 lines.push(`- **平均 EF**: ${result.avgEF}`)
 lines.push(`- **平均间隔**: ${result.avgInterval} 天`)
 lines.push(`- **困难词占比**: ${result.hardWordRatio}% (${result.hardWords} 个)`)
 lines.push(`- **已掌握**: ${result.masteryRatio}% (${result.masteredWords} 个)`)
 if (result.overloadDeferrals > 0) {
 lines.push(`- **延期单词数**: ${result.overloadDeferrals}`)
 }
 lines.push('')
 }

 // 胜出者分析
 const winner = findWinner(simulationResults)
 if (winner) {
 lines.push(`## 推荐模式\n`)
 lines.push(`综合考虑成功率、EF 稳定性和掌握速度，**${winner.name}** 表现最佳。\n`)
 }

 return lines.join('\n')
}

/**
 * 找出综合表现最好的变体
 * 评分维度：成功率 (40%) + 掌握率 (40%) + 平均 EF 稳定性 (20%)
 */
function findWinner(results) {
 let best = null
 let bestScore = -Infinity

 for (const result of Object.values(results)) {
 if (result.totalReviews === 0) continue

 const successScore = result.successRate // 0-100
 const masteryScore = result.masteryRatio // 0-100
 // EF 越接近 2.5 (标准值) 越稳定；偏差大说明波动大
 const efStability = 100 - Math.abs(result.avgEF - 2.5) * 20

 const totalScore = successScore * 0.4 + masteryScore * 0.4 + efStability * 0.2

 if (totalScore > bestScore) {
 bestScore = totalScore
 best = result
 }
 }

 return best
}

/**
 * 生成对比指标表格（用于 UI 展示）
 */
export function buildComparisonTable(simulationResults) {
 const variants = Object.values(simulationResults)
 if (variants.length === 0) return []

 const metrics = [
 { key: 'successRate', label: '成功率', unit: '%', highlight: true },
 { key: 'masteryRatio', label: '掌握率', unit: '%', highlight: true },
 { key: 'avgEF', label: '平均 EF', unit: '', highlight: false },
 { key: 'avgInterval', label: '平均间隔', unit: ' 天', highlight: false },
 { key: 'hardWordRatio', label: '困难词占比', unit: '%', highlight: false },
 { key: 'totalReviews', label: '总复习次数', unit: '', highlight: false },
 { key: 'overloadDeferrals', label: '延期次数', unit: '', highlight: false }
 ]

 return metrics.map(metric => {
 const row = { metric: metric.label, unit: metric.unit, highlight: metric.highlight }
 let bestValue = -Infinity
 let bestVariant = null

 for (const v of variants) {
 const value = v[metric.key] || 0
 if (value > bestValue) {
 bestValue = value
 bestVariant = v.variant
 }
 }

 for (const v of variants) {
 row[v.variant] = {
 value: v[metric.key] || 0,
 isBest: v.variant === bestVariant && metric.highlight
 }
 }
 return row
 })
}

export { VARIANTS as COMPARISON_VARIANTS }
