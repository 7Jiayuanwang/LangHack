import { describe, it, expect } from 'vitest'
import {
  calculateOverloadPriority,
  batchDueWords,
  deferWordReview,
  getOverloadMessage
} from '@/utils/sm2-overload-protection'

describe('SM-2 过载保护', () => {
  describe('calculateOverloadPriority', () => {
    it('should return 0 for invalid input', () => {
      expect(calculateOverloadPriority(null)).toBe(0)
      expect(calculateOverloadPriority({})).toBe(0)
      expect(calculateOverloadPriority({ word: 'test' })).toBe(0)
    })

    it('should prioritize overdue words', () => {
      const now = new Date()
      const oneDayAgo = new Date(now.getTime() - 24 * 60 * 60 * 1000)
      const threeDaysAgo = new Date(now.getTime() - 3 * 24 * 60 * 60 * 1000)

      const word1 = { nextReviewTime: oneDayAgo.toISOString(), easinessFactor: 2.5, repetition: 2 }
      const word2 = { nextReviewTime: threeDaysAgo.toISOString(), easinessFactor: 2.5, repetition: 2 }

      const priority1 = calculateOverloadPriority(word1)
      const priority2 = calculateOverloadPriority(word2)

      expect(priority2).toBeGreaterThan(priority1)
    })

    it('should prioritize difficult words (low EF)', () => {
      const now = new Date()
      const yesterday = new Date(now.getTime() - 24 * 60 * 60 * 1000)

      const easyWord = { nextReviewTime: yesterday.toISOString(), easinessFactor: 4.0, repetition: 5 }
      const hardWord = { nextReviewTime: yesterday.toISOString(), easinessFactor: 1.5, repetition: 5 }

      const easyPriority = calculateOverloadPriority(easyWord)
      const hardPriority = calculateOverloadPriority(hardWord)

      expect(hardPriority).toBeGreaterThan(easyPriority)
    })

    it('should prioritize less-practiced words', () => {
      const now = new Date()
      const yesterday = new Date(now.getTime() - 24 * 60 * 60 * 1000)

      const newWord = { nextReviewTime: yesterday.toISOString(), easinessFactor: 2.5, repetition: 0 }
      const matureWord = { nextReviewTime: yesterday.toISOString(), easinessFactor: 2.5, repetition: 10 }

      const newPriority = calculateOverloadPriority(newWord)
      const maturePriority = calculateOverloadPriority(matureWord)

      expect(newPriority).toBeGreaterThan(maturePriority)
    })
  })

  describe('batchDueWords', () => {
    it('should return empty result for no words', () => {
      const result = batchDueWords([], { maxDailyReview: 50 })

      expect(result.todayBatch).toEqual([])
      expect(result.deferredBatch).toEqual([])
      expect(result.isOverloaded).toBe(false)
      expect(result.stats.total).toBe(0)
    })

    it('should not batch when under threshold', () => {
      const words = Array.from({ length: 30 }, (_, i) => ({
        id: i,
        word: `word${i}`,
        nextReviewTime: new Date().toISOString(),
        easinessFactor: 2.5,
        repetition: 2
      }))

      const result = batchDueWords(words, { maxDailyReview: 50 })

      expect(result.todayBatch.length).toBe(30)
      expect(result.deferredBatch.length).toBe(0)
      expect(result.isOverloaded).toBe(false)
    })

    it('should batch when over threshold', () => {
      const words = Array.from({ length: 70 }, (_, i) => ({
        id: i,
        word: `word${i}`,
        nextReviewTime: new Date().toISOString(),
        easinessFactor: 2.5,
        repetition: 2
      }))

      const result = batchDueWords(words, { maxDailyReview: 50 })

      expect(result.todayBatch.length).toBe(50)
      expect(result.deferredBatch.length).toBe(20)
      expect(result.isOverloaded).toBe(true)
      expect(result.stats.total).toBe(70)
    })

    it('should prioritize high-priority words in today batch', () => {
      const now = new Date()
      const threeDaysAgo = new Date(now.getTime() - 3 * 24 * 60 * 60 * 1000)
      const oneDayAgo = new Date(now.getTime() - 24 * 60 * 60 * 1000)

      const words = [
       { id: 1, word: 'low', nextReviewTime: oneDayAgo.toISOString(), easinessFactor: 3.0, repetition: 5 },
       { id: 2, word: 'high', nextReviewTime: threeDaysAgo.toISOString(), easinessFactor: 1.5, repetition: 1 }
      ].concat(Array.from({ length: 60 }, (_, i) => ({
       id: i + 3,
       word: `word${i}`,
       nextReviewTime: oneDayAgo.toISOString(),
       easinessFactor: 2.5,
       repetition: 3
      })))

      const result = batchDueWords(words, { maxDailyReview: 50 })

      // 高优先级单词应该在今日批次中
      const highPriorityWord = result.todayBatch.find(w => w.id === 2)
      expect(highPriorityWord).toBeDefined()
    })

    it('should reduce batch size in emergency overload', () => {
      const words = Array.from({ length: 100 }, (_, i) => ({
        id: i,
        word: `word${i}`,
        nextReviewTime: new Date().toISOString(),
        easinessFactor: 2.5,
        repetition: 2
      }))

      const result = batchDueWords(words, {
        maxDailyReview: 50,
        emergencyThreshold: 75
      })

      // 100 > 75 (emergencyThreshold)，应该只取 50 * 0.8 = 40
      expect(result.todayBatch.length).toBe(40)
      expect(result.deferredBatch.length).toBe(60)
    })

    it('should not include internal _priority field in result', () => {
      const words = Array.from({ length: 60 }, (_, i) => ({
        id: i,
        word: `word${i}`,
        nextReviewTime: new Date().toISOString(),
        easinessFactor: 2.5,
        repetition: 2
      }))

      const result = batchDueWords(words, { maxDailyReview: 50 })

      result.todayBatch.forEach(word => {
        expect(word).not.toHaveProperty('_priority')
      })

      result.deferredBatch.forEach(word => {
        expect(word).not.toHaveProperty('_priority')
      })
    })
  })

  describe('deferWordReview', () => {
    it('should return original word for invalid input', () => {
      expect(deferWordReview(null)).toBe(null)
      expect(deferWordReview({})).toEqual({})
    })

    it('should defer review by 1 day by default', () => {
      const now = new Date()
      const word = {
        word: 'test',
        nextReviewTime: now.toISOString(),
        easinessFactor: 2.5
      }

      const deferred = deferWordReview(word)

      const originalTime = new Date(word.nextReviewTime)
      const deferredTime = new Date(deferred.nextReviewTime)

      const diffDays = (deferredTime - originalTime) / (24 * 60 * 60 * 1000)
      expect(diffDays).toBeCloseTo(1, 0)
    })

    it('should defer by custom days', () => {
      const now = new Date()
      const word = {
        word: 'test',
        nextReviewTime: now.toISOString()
      }

      const deferred = deferWordReview(word, 3)

      const originalTime = new Date(word.nextReviewTime)
      const deferredTime = new Date(deferred.nextReviewTime)

      const diffDays = (deferredTime - originalTime) / (24 * 60 * 60 * 1000)
      expect(diffDays).toBeCloseTo(3, 0)
    })

    it('should mark word as deferred', () => {
      const word = {
        word: 'test',
        nextReviewTime: new Date().toISOString()
      }

      const deferred = deferWordReview(word)

      expect(deferred._deferred).toBe(true)
      expect(deferred._deferredAt).toBeDefined()
    })

    it('should preserve original word properties', () => {
      const word = {
        id: 123,
        word: 'test',
        nextReviewTime: new Date().toISOString(),
        easinessFactor: 2.5,
        repetition: 3,
        interval: 15
      }

      const deferred = deferWordReview(word)

      expect(deferred.id).toBe(123)
      expect(deferred.word).toBe('test')
      expect(deferred.easinessFactor).toBe(2.5)
      expect(deferred.repetition).toBe(3)
      expect(deferred.interval).toBe(15)
    })
  })

  describe('getOverloadMessage', () => {
    it('should return simple message when not overloaded', () => {
      const result = {
        isOverloaded: false,
        stats: { today: 20, deferred: 0, total: 20 }
      }

      const message = getOverloadMessage(result)

      expect(message).toContain('今日复习 20 个单词')
      expect(message).not.toContain('分批')
    })

    it('should return detailed message when overloaded', () => {
      const result = {
        isOverloaded: true,
        stats: { today: 50, deferred: 30, total: 80 }
      }

      const message = getOverloadMessage(result)

      expect(message).toContain('80 个到期单词')
      expect(message).toContain('今日复习 50 个')
      expect(message).toContain('明日复习 30 个')
      expect(message).toContain('优先')
    })
  })

  describe('integration scenario', () => {
    it('should handle full overload protection workflow', () => {
      const now = new Date()
      const yesterday = new Date(now.getTime() - 24 * 60 * 60 * 1000)

      // 模拟 70 个到期单词（避免触发 emergencyThreshold）
      const dueWords = Array.from({ length: 70 }, (_, i) => ({
        id: i,
        word: `word${i}`,
        nextReviewTime: yesterday.toISOString(),
        easinessFactor: 2.0 + Math.random(),
        repetition: Math.floor(Math.random() * 5)
      }))

      // 1. 分批
      const batchResult = batchDueWords(dueWords, { maxDailyReview: 50 })

      expect(batchResult.isOverloaded).toBe(true)
      expect(batchResult.todayBatch.length).toBe(50)
      expect(batchResult.deferredBatch.length).toBe(20)

      // 2. 推迟延期批次
      const deferredWords = batchResult.deferredBatch.map(w => deferWordReview(w))

      deferredWords.forEach(word => {
        expect(word._deferred).toBe(true)
        const deferredTime = new Date(word.nextReviewTime)
        expect(deferredTime).toBeInstanceOf(Date)
      })

      // 3. 生成提示信息
      const message = getOverloadMessage(batchResult)

      expect(message).toContain('70')
      expect(message).toContain('50')
      expect(message).toContain('20')
    })
  })
})
