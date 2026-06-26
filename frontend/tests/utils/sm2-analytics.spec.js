import { describe, it, expect } from 'vitest'
import {
  calculateEFDistribution,
  calculateIntervalDistribution,
  estimateRetentionRate,
  calculateRetentionStats,
  generateLearningReport,
  exportToCSV
} from '@/utils/sm2-analytics'

describe('SM-2 数据监控', () => {
  describe('calculateEFDistribution', () => {
    it('should return empty stats for no words', () => {
      const result = calculateEFDistribution([])

      expect(result.total).toBe(0)
      expect(result.average).toBe(0)
      expect(result.hardWords).toBe(0)
    })

    it('should calculate EF distribution correctly', () => {
      const words = [
        { easinessFactor: 1.5 },
        { easinessFactor: 2.0 },
        { easinessFactor: 2.5 },
        { easinessFactor: 3.0 },
        { easinessFactor: 4.0 }
      ]

      const result = calculateEFDistribution(words)

      expect(result.total).toBe(5)
      expect(result.average).toBeCloseTo(2.6, 1)
      expect(result.ranges['1.5-2.0']).toBe(1)
      expect(result.ranges['2.0-2.5']).toBe(1)
    })

    it('should classify words as hard/normal/easy', () => {
      const words = [
        { easinessFactor: 1.5 },  // hard
        { easinessFactor: 1.7 },  // hard
        { easinessFactor: 2.0 },  // normal
        { easinessFactor: 2.5 },  // easy
        { easinessFactor: 3.0 }   // easy
      ]

      const result = calculateEFDistribution(words)

      expect(result.hardWords).toBe(2)
      expect(result.normalWords).toBe(2)
      expect(result.easyWords).toBe(1)
      expect(result.hardWordRatio).toBeCloseTo(40, 0)
    })

    it('should handle missing EF values with default 2.5', () => {
      const words = [
        { word: 'test1' },
        { word: 'test2', easinessFactor: 3.0 }
      ]

      const result = calculateEFDistribution(words)

      expect(result.total).toBe(2)
      expect(result.average).toBeCloseTo(2.75, 1)
    })

    it('should filter out invalid EF values', () => {
      const words = [
        { easinessFactor: 2.5 },
        { easinessFactor: -1.0 },  // invalid
        { easinessFactor: 10.0 },  // invalid
        { easinessFactor: 3.0 }
      ]

      const result = calculateEFDistribution(words)

      expect(result.total).toBe(2)
    })
  })

  describe('calculateIntervalDistribution', () => {
    it('should return empty stats for no words', () => {
      const result = calculateIntervalDistribution([])

      expect(result.total).toBe(0)
      expect(result.average).toBe(0)
      expect(result.maxInterval).toBe(0)
    })

    it('should calculate interval distribution correctly', () => {
      const words = [
        { interval: 1 },
        { interval: 5 },
        { interval: 15 },
        { interval: 60 },
        { interval: 120 }
      ]

      const result = calculateIntervalDistribution(words)

      expect(result.total).toBe(5)
      expect(result.average).toBeCloseTo(40.2, 1)
      expect(result.maxInterval).toBe(120)
      expect(result.ranges['0-1']).toBe(1)
      expect(result.ranges['2-7']).toBe(1)
      expect(result.ranges['8-30']).toBe(1)
      expect(result.ranges['31-90']).toBe(1)
      expect(result.ranges['91-180']).toBe(1)
    })

    it('should handle missing interval values with default 0', () => {
      const words = [
        { word: 'test1' },
        { word: 'test2', interval: 10 }
      ]

      const result = calculateIntervalDistribution(words)

      expect(result.total).toBe(2)
      expect(result.ranges['0-1']).toBe(1)
      expect(result.ranges['8-30']).toBe(1)
    })
  })

  describe('estimateRetentionRate', () => {
    it('should return 1.0 for invalid input', () => {
      expect(estimateRetentionRate(null)).toBe(1.0)
      expect(estimateRetentionRate({})).toBe(1.0)
    })

    it('should return 1.0 for words not yet due', () => {
      const tomorrow = new Date(Date.now() + 24 * 60 * 60 * 1000)
      const word = {
        nextReviewTime: tomorrow.toISOString(),
        easinessFactor: 2.5,
        interval: 7
      }

      const retention = estimateRetentionRate(word)

      expect(retention).toBe(1.0)
    })

    it('should return lower retention for overdue words', () => {
      const yesterday = new Date(Date.now() - 24 * 60 * 60 * 1000)
      const word = {
        nextReviewTime: yesterday.toISOString(),
        easinessFactor: 2.5,
        interval: 7
      }

      const retention = estimateRetentionRate(word)

      expect(retention).toBeGreaterThan(0)
      expect(retention).toBeLessThan(1.0)
    })

    it('should consider EF in retention estimation', () => {
      const threeDaysAgo = new Date(Date.now() - 3 * 24 * 60 * 60 * 1000)

      const easyWord = {
        nextReviewTime: threeDaysAgo.toISOString(),
        easinessFactor: 4.0,
        interval: 15
      }

      const hardWord = {
        nextReviewTime: threeDaysAgo.toISOString(),
        easinessFactor: 1.5,
        interval: 15
      }

      const easyRetention = estimateRetentionRate(easyWord)
      const hardRetention = estimateRetentionRate(hardWord)

      expect(easyRetention).toBeGreaterThan(hardRetention)
    })
  })

  describe('calculateRetentionStats', () => {
    it('should return empty stats for no words', () => {
      const result = calculateRetentionStats([])

      expect(result.total).toBe(0)
      expect(result.averageRetention).toBe(0)
      expect(result.atRisk).toBe(0)
    })

    it('should classify words by retention level', () => {
      const now = new Date()
      const words = [
        {
          word: 'stable',
          nextReviewTime: new Date(now.getTime() + 24 * 60 * 60 * 1000).toISOString(),
          easinessFactor: 2.5,
          interval: 7
        },
        {
          word: 'needReview',
          nextReviewTime: new Date(now.getTime() - 24 * 60 * 60 * 1000).toISOString(),
          easinessFactor: 2.5,
          interval: 7
        },
        {
          word: 'atRisk',
          nextReviewTime: new Date(now.getTime() - 10 * 24 * 60 * 60 * 1000).toISOString(),
          easinessFactor: 1.5,
          interval: 7
        }
      ]

      const result = calculateRetentionStats(words)

      expect(result.total).toBe(3)
      expect(result.stable).toBeGreaterThan(0)
      expect(result.atRisk).toBeGreaterThan(0)
    })

    it('should calculate average retention as percentage', () => {
      const tomorrow = new Date(Date.now() + 24 * 60 * 60 * 1000)
      const words = [
        {
          nextReviewTime: tomorrow.toISOString(),
          easinessFactor: 2.5,
          interval: 7
        }
      ]

      const result = calculateRetentionStats(words)

      expect(result.averageRetention).toBeCloseTo(100, 0)
    })
  })

  describe('generateLearningReport', () => {
    it('should generate comprehensive report', () => {
      const now = new Date()
      const words = [
        {
          word: 'new',
          repetition: 0,
          interval: 0,
          easinessFactor: 2.5,
          masteryLevel: 'NEW',
          nextReviewTime: now.toISOString()
        },
        {
          word: 'learning',
          repetition: 3,
          interval: 7,
          easinessFactor: 2.0,
          masteryLevel: 'LEARNING',
          nextReviewTime: new Date(now.getTime() - 24 * 60 * 60 * 1000).toISOString()
        },
        {
          word: 'mature',
          repetition: 8,
          interval: 30,
          easinessFactor: 3.0,
          masteryLevel: 'MASTERED',
          nextReviewTime: new Date(now.getTime() + 24 * 60 * 60 * 1000).toISOString()
        }
      ]

      const report = generateLearningReport(words)

      expect(report.timestamp).toBeDefined()
      expect(report.totalWords).toBe(3)
      expect(report.efDistribution).toBeDefined()
      expect(report.intervalDistribution).toBeDefined()
      expect(report.retentionStats).toBeDefined()
      expect(report.repetitionStats).toBeDefined()
      expect(report.dueStats).toBeDefined()
      expect(report.recommendations).toBeDefined()

      expect(report.repetitionStats.new).toBe(1)
      expect(report.repetitionStats.learning).toBe(1)
      expect(report.repetitionStats.mature).toBe(1)
    })

    it('should categorize due status correctly', () => {
      const now = new Date()
      const words = [
        {
          word: 'overdue',
          nextReviewTime: new Date(now.getTime() - 2 * 24 * 60 * 60 * 1000).toISOString(),
          repetition: 2,
          interval: 7,
          easinessFactor: 2.5
        },
        {
          word: 'dueToday',
          nextReviewTime: now.toISOString(),
          repetition: 2,
          interval: 7,
          easinessFactor: 2.5
        },
        {
          word: 'upcoming',
          nextReviewTime: new Date(now.getTime() + 5 * 24 * 60 * 60 * 1000).toISOString(),
          repetition: 2,
          interval: 7,
          easinessFactor: 2.5
        }
      ]

      const report = generateLearningReport(words)

      expect(report.dueStats.overdue).toBeGreaterThan(0)
      expect(report.dueStats.dueToday).toBeGreaterThan(0)
      expect(report.dueStats.upcoming).toBeGreaterThan(0)
    })

    it('should generate recommendations based on data', () => {
      // 困难词过多场景
      const hardWords = Array.from({ length: 10 }, (_, i) => ({
        word: `word${i}`,
        repetition: 1,
        interval: 1,
        easinessFactor: 1.5,  // 低 EF
        nextReviewTime: new Date().toISOString()
      }))

      const report = generateLearningReport(hardWords)

      expect(report.recommendations.length).toBeGreaterThan(0)
      const hardWordWarning = report.recommendations.find(r => r.type === 'warning' && r.title.includes('困难词'))
      expect(hardWordWarning).toBeDefined()
    })
  })

  describe('exportToCSV', () => {
    it('should return header only for empty input', () => {
      const csv = exportToCSV([])

      expect(csv).toContain('word,repetition,interval,easinessFactor,nextReviewTime,estimatedRetention')
      expect(csv.split('\n').length).toBe(2)  // header + empty line
    })

    it('should export words to CSV format', () => {
      const words = [
        {
          word: 'test',
          repetition: 3,
          interval: 15,
          easinessFactor: 2.5,
          nextReviewTime: '2026-06-06T00:00:00.000Z'
        },
        {
          word: 'example',
          repetition: 1,
          interval: 1,
          easinessFactor: 2.0,
          nextReviewTime: '2026-06-05T00:00:00.000Z'
        }
      ]

      const csv = exportToCSV(words)

      expect(csv).toContain('test,3,15,2.50,2026-06-06T00:00:00.000Z')
      expect(csv).toContain('example,1,1,2.00,2026-06-05T00:00:00.000Z')
      const lines = csv.split('\n').filter(line => line.trim())
      expect(lines.length).toBe(3)  // header + 2 rows
    })

    it('should include estimated retention in export', () => {
      const tomorrow = new Date(Date.now() + 24 * 60 * 60 * 1000)
      const words = [
        {
          word: 'test',
          repetition: 2,
          interval: 7,
          easinessFactor: 2.5,
          nextReviewTime: tomorrow.toISOString()
        }
      ]

      const csv = exportToCSV(words)

      expect(csv).toContain('100.0%')  // 未到期，保持率 100%
    })

    it('should handle missing fields with defaults', () => {
      const words = [
        { word: 'incomplete' }
      ]

      const csv = exportToCSV(words)

      expect(csv).toContain('incomplete,0,0,2.50,,100.0%')
    })
  })

  describe('integration: full monitoring workflow', () => {
    it('should provide complete analytics for a typical vocabulary', () => {
      const now = new Date()
      const words = [
        // 新词
        ...Array.from({ length: 10 }, (_, i) => ({
          word: `new${i}`,
          repetition: 0,
          interval: 0,
          easinessFactor: 2.5,
          masteryLevel: 'NEW',
          nextReviewTime: now.toISOString()
        })),
        // 学习中
        ...Array.from({ length: 15 }, (_, i) => ({
          word: `learning${i}`,
          repetition: 2 + (i % 3),
          interval: 5 + (i % 10),
          easinessFactor: 2.0 + Math.random() * 0.5,
          masteryLevel: 'LEARNING',
          nextReviewTime: new Date(now.getTime() - (i % 3) * 24 * 60 * 60 * 1000).toISOString()
        })),
        // 已掌握
        ...Array.from({ length: 25 }, (_, i) => ({
          word: `mature${i}`,
          repetition: 5 + i,
          interval: 30 + i * 2,
          easinessFactor: 2.5 + Math.random() * 1.0,
          masteryLevel: 'MASTERED',
          nextReviewTime: new Date(now.getTime() + (i + 1) * 24 * 60 * 60 * 1000).toISOString()
        }))
      ]

      const report = generateLearningReport(words)

      expect(report.totalWords).toBe(50)
      expect(report.repetitionStats.new).toBe(10)
      expect(report.repetitionStats.learning).toBeGreaterThan(0)
      expect(report.repetitionStats.mature).toBe(25)
      expect(report.recommendations).toBeInstanceOf(Array)

      // CSV 导出
      const csv = exportToCSV(words)
      const lines = csv.split('\n').filter(line => line.trim())
      expect(lines.length).toBe(51)  // header + 50 words
    })
  })
})
