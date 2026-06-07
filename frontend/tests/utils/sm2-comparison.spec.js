import { describe, it, expect } from 'vitest'
import {
 VARIANTS,
 simulateVariants,
 buildComparisonTable,
 generateComparisonReport
} from '@/utils/sm2-comparison'

function makeWords(count, quality, ef, rep, interval) {
 const out = []
 for (let i = 0; i < count; i++) {
 out.push({
 word: 'w' + i,
 quality,
 easinessFactor: ef,
 repetition: rep,
 interval
 })
 }
 return out
}

function concatWords(arr1, arr2, arr3) {
 return arr1.concat(arr2).concat(arr3)
}

describe('SM-2 模式对比分析', () => {
 describe('VARIANTS 配置', () => {
 it('should have 4 variants', () => {
 expect(Object.keys(VARIANTS)).toHaveLength(4)
 expect(VARIANTS.standard).toBeDefined()
 expect(VARIANTS.hard).toBeDefined()
 expect(VARIANTS.overload).toBeDefined()
 expect(VARIANTS.hybrid).toBeDefined()
 })

 it('should have correct sm2Options for each variant', () => {
 expect(VARIANTS.standard.sm2Options.hardWordMode).toBe(false)
 expect(VARIANTS.hard.sm2Options.hardWordMode).toBe(true)
 expect(VARIANTS.hybrid.sm2Options.hardWordMode).toBe(true)
 })

 it('should mark overload variants correctly', () => {
 expect(VARIANTS.standard.overload).toBe(false)
 expect(VARIANTS.overload.overload).toBe(true)
 expect(VARIANTS.hybrid.overload).toBe(true)
 })
 })

 describe('simulateVariants', () => {
 it('should return results for all 4 variants', () => {
 const words = makeWords(2, 3, 2.5, 0, 0)
 const results = simulateVariants(words, { iterations: 5 })
 expect(Object.keys(results)).toHaveLength(4)
 })

 it('should produce different EF for hard vs standard mode', () => {
 const words = makeWords(1, 1, 1.5, 2, 5)
 const results = simulateVariants(words, { iterations: 10 })
 expect(results.hard.avgEF).toBeLessThanOrEqual(results.standard.avgEF + 0.1)
 })

 it('should generate overloadDeferrals for overload variants', () => {
 const words = makeWords(100, 3, 2.5, 0, 0)
 const results = simulateVariants(words, {
 iterations: 5,
 maxDailyReview: 50,
 emergencyThreshold: 75
 })
 expect(results.standard.overloadDeferrals).toBe(0)
 expect(results.overload.overloadDeferrals).toBeGreaterThan(0)
 expect(results.hybrid.overloadDeferrals).toBeGreaterThan(0)
 })

 it('should track total reviews and success rate', () => {
 const words = makeWords(2, 5, 2.5, 0, 0)
 const results = simulateVariants(words, { iterations: 5 })
 for (const result of Object.values(results)) {
 expect(result.totalReviews).toBeGreaterThan(0)
 expect(result.successRate).toBeGreaterThanOrEqual(0)
 expect(result.successRate).toBeLessThanOrEqual(100)
 }
 })

 it('should handle empty input', () => {
 const results = simulateVariants([], { iterations: 5 })
 for (const result of Object.values(results)) {
 expect(result.totalReviews).toBe(0)
 expect(result.successRate).toBe(0)
 }
 })
 })

 describe('buildComparisonTable', () => {
 it('should build a table with metrics for each variant', () => {
 const words = makeWords(1, 3, 2.5, 0, 0)
 const results = simulateVariants(words, { iterations: 3 })
 const table = buildComparisonTable(results)
 expect(table.length).toBeGreaterThan(0)
 for (const row of table) {
 expect(row.metric).toBeDefined()
 expect(row.standard).toBeDefined()
 expect(row.hard).toBeDefined()
 expect(row.overload).toBeDefined()
 expect(row.hybrid).toBeDefined()
 }
 })

 it('should mark best value for highlight metrics', () => {
 const words = makeWords(1, 5, 2.5, 0, 0)
 const results = simulateVariants(words, { iterations: 3 })
 const table = buildComparisonTable(results)
 const successRow = table.find(r => r.metric === '成功率')
 const bestFlags = [
 successRow.standard.isBest,
 successRow.hard.isBest,
 successRow.overload.isBest,
 successRow.hybrid.isBest
 ]
 expect(bestFlags.some(Boolean)).toBe(true)
 })
 })

 describe('generateComparisonReport', () => {
 it('should generate human-readable report', () => {
 const words = makeWords(1, 3, 2.5, 0, 0)
 const results = simulateVariants(words, { iterations: 3 })
 const report = generateComparisonReport(results)
 expect(report).toContain('SM-2 模式对比报告')
 expect(report).toContain('标准 SM-2')
 expect(report).toContain('困难词模式')
 expect(report).toContain('成功率')
 expect(report).toContain('推荐模式')
 })
 })

 describe('integration: real-world scenario', () => {
 it('should produce reasonable results for mixed-quality vocabulary', () => {
 const words = concatWords(
 makeWords(5, 5, 2.8, 2, 10),
 makeWords(10, 3, 2.3, 1, 3),
 makeWords(5, 1, 1.6, 0, 1)
 )
 const results = simulateVariants(words, { iterations: 20 })
 for (const result of Object.values(results)) {
 expect(result.totalReviews).toBeGreaterThan(0)
 expect(result.successRate).toBeGreaterThan(0)
 expect(result.masteryRatio).toBeGreaterThanOrEqual(0)
 expect(result.avgEF).toBeGreaterThanOrEqual(1.0)
 expect(result.avgEF).toBeLessThanOrEqual(5.0)
 }
 })
 })
})
