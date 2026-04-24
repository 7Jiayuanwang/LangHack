import { describe, it, expect } from 'vitest'
import {
  calculateEbbinghaus,
  calculateRetentionRate,
  calculateReviewPriority,
  scheduleReviewWords,
  getEbbinghausStage,
  shouldReviewWord,
} from '@/features/VocabularyFeatures/ebbinghaus'

describe('Ebbinghaus Forgetting Curve Algorithm', () => {
  describe('calculateEbbinghaus', () => {
    it('should increase interval when quality >= 3 (successful review)', () => {
      const result = calculateEbbinghaus(4, {
        reviewCount: 0,
        interval: 0,
        easinessFactor: 2.5,
      })

      expect(result.interval).toBe(20) // 第一次复习：20分钟
      expect(result.repetition).toBe(1)
      expect(result.easinessFactor).toBeGreaterThanOrEqual(1.3)
      expect(result.easinessFactor).toBeLessThanOrEqual(2.5)
    })

    it('should reset interval when quality < 3 (failed review)', () => {
      const result = calculateEbbinghaus(2, {
        reviewCount: 3,
        interval: 2880,
        easinessFactor: 2.5,
      })

      expect(result.interval).toBe(20) // 失败后重新开始
      expect(result.repetition).toBe(0)
      expect(result.easinessFactor).toBeLessThan(2.5)
    })

    it('should follow Ebbinghaus intervals progression', () => {
      const intervals = []
      let state = { reviewCount: 0, interval: 0, easinessFactor: 2.5 }

      for (let i = 0; i < 6; i++) {
        const result = calculateEbbinghaus(4, state)
        intervals.push(result.interval)
        state = { ...result, reviewCount: result.repetition }
      }

      // 标准艾宾浩斯间隔：20分钟 -> 1小时 -> 24小时 -> 48小时 -> 6天 -> 31天
      expect(intervals[0]).toBe(20)
      expect(intervals[1]).toBe(60)
      expect(intervals[2]).toBe(1440)
      expect(intervals[3]).toBe(2880)
      expect(intervals[4]).toBe(8640)
      expect(intervals[5]).toBe(44640)
    })

    it('should keep easiness factor within bounds', () => {
      const result = calculateEbbinghaus(5, {
        reviewCount: 0,
        interval: 0,
        easinessFactor: 2.5,
      })

      expect(result.easinessFactor).toBeGreaterThanOrEqual(1.3)
      expect(result.easinessFactor).toBeLessThanOrEqual(2.5)
    })
  })

  describe('calculateRetentionRate', () => {
    it('should return value between 0 and 1', () => {
      const rate = calculateRetentionRate(1440, 2.5)
      expect(rate).toBeGreaterThanOrEqual(0)
      expect(rate).toBeLessThanOrEqual(1)
    })

    it('should return 1 for very short intervals (clamped)', () => {
      // Very short intervals produce high retention that gets clamped to 1
      const rate = calculateRetentionRate(20, 2.5)
      expect(rate).toBe(1)
    })

    it('should return lower retention for long intervals', () => {
      // Long intervals (e.g., 31 days) should have lower retention
      const rate = calculateRetentionRate(44640, 2.5) // 31 days in minutes
      expect(rate).toBeLessThan(1)
      expect(rate).toBeGreaterThan(0)
    })

    it('should be affected by easiness factor for long intervals', () => {
      const easy = calculateRetentionRate(44640, 2.5)
      const hard = calculateRetentionRate(44640, 1.3)

      expect(easy).toBeGreaterThan(hard)
    })
  })

  describe('calculateReviewPriority', () => {
    it('should return higher priority for overdue words', () => {
      const now = new Date()
      const overdueWord = {
        nextReviewTime: new Date(now.getTime() - 24 * 60 * 60 * 1000).toISOString(),
        interval: 1440,
        easinessFactor: 2.5,
        repetition: 2,
      }
      const futureWord = {
        nextReviewTime: new Date(now.getTime() + 24 * 60 * 60 * 1000).toISOString(),
        interval: 1440,
        easinessFactor: 2.5,
        repetition: 2,
      }

      const overduePriority = calculateReviewPriority(overdueWord)
      const futurePriority = calculateReviewPriority(futureWord)

      expect(overduePriority).toBeGreaterThan(futurePriority)
    })

    it('should return 0 for null word', () => {
      expect(calculateReviewPriority(null)).toBe(0)
      expect(calculateReviewPriority(undefined)).toBe(0)
    })
  })

  describe('scheduleReviewWords', () => {
    it('should return empty array for empty input', () => {
      expect(scheduleReviewWords([])).toEqual([])
      expect(scheduleReviewWords(null)).toEqual([])
    })

    it('should respect batch size limit', () => {
      const words = Array.from({ length: 50 }, (_, i) => ({
        id: i,
        nextReviewTime: new Date().toISOString(),
        interval: 1440,
        easinessFactor: 2.5,
        repetition: 2,
      }))

      const result = scheduleReviewWords(words, 20)
      expect(result).toHaveLength(20)
    })

    it('should sort by priority (higher first)', () => {
      const now = new Date()
      const words = [
        {
          id: 1,
          nextReviewTime: new Date(now.getTime() + 24 * 60 * 60 * 1000).toISOString(),
          interval: 1440,
          easinessFactor: 2.5,
          repetition: 5,
        },
        {
          id: 2,
          nextReviewTime: new Date(now.getTime() - 48 * 60 * 60 * 1000).toISOString(),
          interval: 1440,
          easinessFactor: 1.3,
          repetition: 1,
        },
      ]

      const result = scheduleReviewWords(words, 10)
      expect(result[0].id).toBe(2) // Overdue word should be first
      expect(result[0].priority).toBeGreaterThan(result[1].priority)
    })
  })

  describe('getEbbinghausStage', () => {
    it('should return correct stage names', () => {
      expect(getEbbinghausStage(0).name).toBe('初次学习')
      expect(getEbbinghausStage(1).name).toBe('第一次复习')
      expect(getEbbinghausStage(2).name).toBe('第二次复习')
      expect(getEbbinghausStage(3).name).toBe('第三次复习')
      expect(getEbbinghausStage(4).name).toBe('第四次复习')
      expect(getEbbinghausStage(5).name).toBe('第五次复习')
      expect(getEbbinghausStage(6).name).toBe('巩固期')
      expect(getEbbinghausStage(10).name).toBe('巩固期') // 超出范围返回最后一个
    })

    it('should include description for each stage', () => {
      const stage = getEbbinghausStage(0)
      expect(stage.description).toBeDefined()
      expect(typeof stage.description).toBe('string')
    })
  })

  describe('shouldReviewWord', () => {
    it('should return true when no review time is set', () => {
      const word = { id: 1 }
      expect(shouldReviewWord(word)).toBe(true)
    })

    it('should return true when review time has passed', () => {
      const now = new Date()
      const word = {
        id: 1,
        nextReviewTime: new Date(now.getTime() - 1000).toISOString(),
      }
      expect(shouldReviewWord(word)).toBe(true)
    })

    it('should return false when review time is in the future', () => {
      const now = new Date()
      const word = {
        id: 1,
        nextReviewTime: new Date(now.getTime() + 24 * 60 * 60 * 1000).toISOString(),
      }
      expect(shouldReviewWord(word)).toBe(false)
    })

    it('should return false for null word', () => {
      expect(shouldReviewWord(null)).toBe(false)
      expect(shouldReviewWord(undefined)).toBe(false)
    })
  })
})
