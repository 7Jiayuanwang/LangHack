import { describe, it, expect } from 'vitest'
import { calculateSM2 } from '@/utils/sm2'

describe('SM-2 困难词加强模式', () => {
  describe('hardWordMode option', () => {
    it('should use EF min 1.0 when hardWordMode is enabled', () => {
      // 连续失败导致 EF 降低
      let state = { repetition: 0, interval: 0, easinessFactor: 1.3 }

      // 普通模式：EF 钳制在 1.3
      const normal = calculateSM2(0, state)
      expect(normal.easinessFactor).toBeGreaterThanOrEqual(1.3)

      // 困难词模式：EF 可以降到 1.0
      const hardWord = calculateSM2(0, state, { hardWordMode: true })
      expect(hardWord.easinessFactor).toBeLessThan(1.3)
      expect(hardWord.easinessFactor).toBeGreaterThanOrEqual(1.0)
    })

    it('should allow EF to drop below 1.3 in hardWordMode', () => {
      // 从 1.5 开始，连续失败
      let state = { repetition: 3, interval: 15, easinessFactor: 1.5 }

      const result = calculateSM2(1, state, { hardWordMode: true })

      // 失败后 EF 下降，可能低于 1.3
      expect(result.easinessFactor).toBeLessThanOrEqual(1.5)
      expect(result.easinessFactor).toBeGreaterThanOrEqual(1.0)
      expect(result.repetition).toBe(0)
      expect(result.interval).toBe(1)
    })

    it('should result in shorter intervals for hard words', () => {
      const state = { repetition: 2, interval: 6, easinessFactor: 1.2 }

      // 普通模式：EF 会被上调到 1.3
      const normal = calculateSM2(3, state)

      // 困难词模式：保持低 EF
      const hardWord = calculateSM2(3, state, { hardWordMode: true })

      // 困难词模式下，低 EF 保持，间隔增长更慢
      expect(hardWord.easinessFactor).toBeLessThanOrEqual(normal.easinessFactor)
    })

    it('should not affect EF upper bound', () => {
      const state = { repetition: 5, interval: 30, easinessFactor: 4.9 }

      const result = calculateSM2(5, state, { hardWordMode: true })

      // 上限仍然是 5.0
      expect(result.easinessFactor).toBeLessThanOrEqual(5.0)
    })

    it('should be backward compatible when option is not provided', () => {
      const state = { repetition: 0, interval: 0, easinessFactor: 1.2 }

      // 不传 options，默认行为不变
      const result = calculateSM2(3, state)

      expect(result.easinessFactor).toBeGreaterThanOrEqual(1.3)
    })
  })

  describe('integration with standard SM-2 flow', () => {
    it('should work correctly with quality >= 3 (success)', () => {
      const state = { repetition: 0, interval: 0, easinessFactor: 1.1 }

      const result = calculateSM2(4, state, { hardWordMode: true })

      expect(result.repetition).toBe(1)
      expect(result.interval).toBe(1)
      expect(result.easinessFactor).toBeGreaterThanOrEqual(1.1)  // 可能保持不变或略增
    })

    it('should work correctly with quality < 3 (failure)', () => {
      const state = { repetition: 3, interval: 15, easinessFactor: 1.5 }

      const result = calculateSM2(2, state, { hardWordMode: true })

      expect(result.repetition).toBe(0)
      expect(result.interval).toBe(1)
      expect(result.easinessFactor).toBeLessThan(1.5)
    })
  })

  describe('real-world scenario: extremely difficult word', () => {
    it('should handle word that fails repeatedly', () => {
      let state = { repetition: 0, interval: 0, easinessFactor: 2.5 }

      // 连续 5 次失败
      for (let i = 0; i < 5; i++) {
        state = calculateSM2(1, state, { hardWordMode: true })
      }

      // EF 应该大幅下降
      expect(state.easinessFactor).toBeLessThan(1.5)
      expect(state.easinessFactor).toBeGreaterThanOrEqual(1.0)

      // repetition 应该重置为 0
      expect(state.repetition).toBe(0)

      // interval 应该是 1
      expect(state.interval).toBe(1)
    })

    it('should allow recovery from low EF', () => {
      let state = { repetition: 0, interval: 1, easinessFactor: 1.0 }

      // 连续 3 次高质量复习
      state = calculateSM2(5, state, { hardWordMode: true })
      state = calculateSM2(5, state, { hardWordMode: true })
      state = calculateSM2(5, state, { hardWordMode: true })

      // EF 应该逐步回升
      expect(state.easinessFactor).toBeGreaterThan(1.0)
      expect(state.repetition).toBe(3)
    })
  })
})
