import { describe, it, expect } from 'vitest'
import { calculateSM2 } from '@/utils/sm2'

describe('SM2 Algorithm - calculateSM2', () => {
  // ============================================================
  // Input Validation
  // ============================================================

  describe('input validation', () => {
    it('throws when quality is not a number', () => {
      expect(() => calculateSM2('3')).toThrow('Quality must be a finite number between 0 and 5')
      expect(() => calculateSM2(null)).toThrow('Quality must be a finite number between 0 and 5')
      expect(() => calculateSM2(undefined)).toThrow('Quality must be a finite number between 0 and 5')
    })

    it('throws when quality is below 0', () => {
      expect(() => calculateSM2(-1)).toThrow('Quality must be a finite number between 0 and 5')
      expect(() => calculateSM2(-0.1)).toThrow('Quality must be a finite number between 0 and 5')
    })

    it('throws when quality is above 5', () => {
      expect(() => calculateSM2(6)).toThrow('Quality must be a finite number between 0 and 5')
      expect(() => calculateSM2(5.1)).toThrow('Quality must be a finite number between 0 and 5')
    })

    it('throws when quality is NaN or Infinity', () => {
      expect(() => calculateSM2(NaN)).toThrow('Quality must be a finite number between 0 and 5')
      expect(() => calculateSM2(Infinity)).toThrow('Quality must be a finite number between 0 and 5')
      expect(() => calculateSM2(-Infinity)).toThrow('Quality must be a finite number between 0 and 5')
    })
  })

  // ============================================================
  // Default Initial State
  // ============================================================

  describe('default initial state (no previousState)', () => {
    it('uses default values when previousState is omitted', () => {
      const result = calculateSM2(4)
      // First success: repetition 0 -> interval=1, repetition becomes 1
      expect(result.repetition).toBe(1)
      expect(result.interval).toBe(1)
      // EF = 2.5 + (0.1 - (5-4)*(0.08 + (5-4)*0.02)) = 2.5 + (0.1 - 0.1) = 2.5
      expect(result.easinessFactor).toBe(2.5)
      expect(result.nextReviewTime).toBeDefined()
    })

    it('uses default values when previousState is empty object', () => {
      const result = calculateSM2(3, {})
      expect(result.repetition).toBe(1)
      expect(result.interval).toBe(1)
    })
  })

  // ============================================================
  // Previous State Validation / Sanitization
  // ============================================================

  describe('previousState sanitization', () => {
    it('resets invalid repetition to 0', () => {
      const result = calculateSM2(4, { repetition: -1, interval: 6, easinessFactor: 2.5 })
      // repetition reset to 0 -> first success -> interval=1, rep=1
      expect(result.repetition).toBe(1)
      expect(result.interval).toBe(1)
    })

    it('resets non-numeric repetition to 0', () => {
      const result = calculateSM2(4, { repetition: 'abc', interval: 6, easinessFactor: 2.5 })
      expect(result.repetition).toBe(1)
      expect(result.interval).toBe(1)
    })

    it('resets invalid interval to 0', () => {
      const result = calculateSM2(4, { repetition: 2, interval: -5, easinessFactor: 2.5 })
      // interval reset to 0, rep=2 -> interval = round(0 * 2.5) = 0, rep becomes 3
      expect(result.repetition).toBe(3)
      expect(result.interval).toBe(0)
    })

    it('resets easinessFactor below 1.3 to 2.5', () => {
      const result = calculateSM2(4, { repetition: 0, interval: 0, easinessFactor: 1.0 })
      // EF reset to 2.5, then updated by quality=4: 2.5 + 0 = 2.5
      expect(result.easinessFactor).toBe(2.5)
    })

    it('resets easinessFactor above 5.0 to 2.5', () => {
      const result = calculateSM2(4, { repetition: 0, interval: 0, easinessFactor: 6.0 })
      expect(result.easinessFactor).toBe(2.5)
    })

    it('resets non-finite easinessFactor to 2.5', () => {
      const result = calculateSM2(4, { repetition: 0, interval: 0, easinessFactor: Infinity })
      expect(result.easinessFactor).toBe(2.5)
    })
  })

  // ============================================================
  // Quality Boundary Values (0, 1, 2, 3, 4, 5)
  // ============================================================

  describe('quality boundary values', () => {
    const defaultState = { repetition: 2, interval: 6, easinessFactor: 2.5 }

    it('quality=0: resets repetition and interval (complete blackout)', () => {
      const result = calculateSM2(0, defaultState)
      expect(result.repetition).toBe(0)
      expect(result.interval).toBe(1)
      // EF = 2.5 + (0.1 - 5*(0.08 + 5*0.02)) = 2.5 + (0.1 - 0.9) = 1.7
      expect(result.easinessFactor).toBeCloseTo(1.7, 5)
    })

    it('quality=1: resets repetition and interval', () => {
      const result = calculateSM2(1, defaultState)
      expect(result.repetition).toBe(0)
      expect(result.interval).toBe(1)
      // EF = 2.5 + (0.1 - 4*(0.08 + 4*0.02)) = 2.5 + (0.1 - 0.64) = 1.96
      expect(result.easinessFactor).toBeCloseTo(1.96, 5)
    })

    it('quality=2: resets repetition and interval', () => {
      const result = calculateSM2(2, defaultState)
      expect(result.repetition).toBe(0)
      expect(result.interval).toBe(1)
      // EF = 2.5 + (0.1 - 3*(0.08 + 3*0.02)) = 2.5 + (0.1 - 0.42) = 2.18
      expect(result.easinessFactor).toBeCloseTo(2.18, 5)
    })

    it('quality=3: counts as success, advances repetition', () => {
      const result = calculateSM2(3, defaultState)
      // rep=2, interval=6, EF=2.5 -> interval = round(6*2.5) = 15, rep=3
      expect(result.repetition).toBe(3)
      expect(result.interval).toBe(15)
      // EF = 2.5 + (0.1 - 2*(0.08 + 2*0.02)) = 2.5 + (0.1 - 0.24) = 2.36
      expect(result.easinessFactor).toBeCloseTo(2.36, 5)
    })

    it('quality=4: counts as success, advances repetition', () => {
      const result = calculateSM2(4, defaultState)
      // rep=2, interval=6, EF=2.5 -> interval = round(6*2.5) = 15, rep=3
      expect(result.repetition).toBe(3)
      expect(result.interval).toBe(15)
      // EF = 2.5 + (0.1 - 1*(0.08 + 1*0.02)) = 2.5 + 0.0 = 2.5
      expect(result.easinessFactor).toBeCloseTo(2.5, 5)
    })

    it('quality=5: counts as success (perfect response), advances repetition', () => {
      const result = calculateSM2(5, defaultState)
      // rep=2, interval=6, EF=2.5 -> interval = round(6*2.5) = 15, rep=3
      expect(result.repetition).toBe(3)
      expect(result.interval).toBe(15)
      // EF = 2.5 + (0.1 - 0*(0.08 + 0*0.02)) = 2.5 + 0.1 = 2.6
      expect(result.easinessFactor).toBeCloseTo(2.6, 5)
    })
  })

  // ============================================================
  // Interval Calculation for Successive Successes
  // ============================================================

  describe('interval progression on consecutive successes', () => {
    it('first success (rep=0): interval becomes 1', () => {
      const result = calculateSM2(4, { repetition: 0, interval: 0, easinessFactor: 2.5 })
      expect(result.interval).toBe(1)
      expect(result.repetition).toBe(1)
    })

    it('second success (rep=1): interval becomes 6', () => {
      const result = calculateSM2(4, { repetition: 1, interval: 1, easinessFactor: 2.5 })
      expect(result.interval).toBe(6)
      expect(result.repetition).toBe(2)
    })

    it('third success (rep=2): interval = round(previous_interval * old_EF)', () => {
      const result = calculateSM2(4, { repetition: 2, interval: 6, easinessFactor: 2.5 })
      // interval = round(6 * 2.5) = 15
      expect(result.interval).toBe(15)
      expect(result.repetition).toBe(3)
    })

    it('fourth success (rep=3): interval grows with old EF', () => {
      const result = calculateSM2(4, { repetition: 3, interval: 15, easinessFactor: 2.5 })
      // interval = round(15 * 2.5) = 38
      expect(result.interval).toBe(38)
      expect(result.repetition).toBe(4)
    })

    it('uses OLD EF value for interval calculation, not updated EF', () => {
      // With quality=5, EF increases from 2.5 to 2.6
      // But interval should use the OLD EF (2.5), not the new EF (2.6)
      const result = calculateSM2(5, { repetition: 2, interval: 6, easinessFactor: 2.5 })
      // interval = round(6 * 2.5) = 15, NOT round(6 * 2.6) = 16
      expect(result.interval).toBe(15)
      expect(result.easinessFactor).toBeCloseTo(2.6, 5)
    })
  })

  // ============================================================
  // EF Minimum Clamping
  // ============================================================

  describe('easinessFactor minimum clamping at 1.3', () => {
    it('clamps EF to 1.3 when quality=0 drives it below', () => {
      // Start with EF near minimum
      const result = calculateSM2(0, { repetition: 0, interval: 0, easinessFactor: 1.3 })
      // EF = 1.3 + (0.1 - 5*(0.08 + 5*0.02)) = 1.3 + (0.1 - 0.9) = 0.5 -> clamped to 1.3
      expect(result.easinessFactor).toBe(1.3)
    })

    it('EF stays at 1.3 after repeated low-quality responses', () => {
      let state = { repetition: 3, interval: 15, easinessFactor: 1.4 }
      // quality=0 three times in a row
      for (let i = 0; i < 3; i++) {
        state = calculateSM2(0, state)
      }
      expect(state.easinessFactor).toBe(1.3)
      expect(state.repetition).toBe(0)
      expect(state.interval).toBe(1)
    })

    it('EF recovers when quality improves after being at minimum', () => {
      const result = calculateSM2(5, { repetition: 0, interval: 0, easinessFactor: 1.3 })
      // EF = 1.3 + 0.1 = 1.4
      expect(result.easinessFactor).toBeCloseTo(1.4, 5)
    })
  })

  // ============================================================
  // Failure Path (quality < 3)
  // ============================================================

  describe('failure path (quality < 3)', () => {
    it('resets repetition to 0 and interval to 1 on failure', () => {
      const state = { repetition: 5, interval: 100, easinessFactor: 2.5 }
      const result = calculateSM2(2, state)
      expect(result.repetition).toBe(0)
      expect(result.interval).toBe(1)
    })

    it('consecutive failures keep repetition at 0 and interval at 1', () => {
      let state = { repetition: 3, interval: 15, easinessFactor: 2.5 }
      state = calculateSM2(0, state)
      expect(state.repetition).toBe(0)
      expect(state.interval).toBe(1)

      state = calculateSM2(1, state)
      expect(state.repetition).toBe(0)
      expect(state.interval).toBe(1)

      state = calculateSM2(2, state)
      expect(state.repetition).toBe(0)
      expect(state.interval).toBe(1)
    })

    it('EF degrades with consecutive failures but stays above 1.3', () => {
      let state = { repetition: 0, interval: 1, easinessFactor: 2.5 }

      state = calculateSM2(0, state) // EF: 2.5 -> 1.7
      expect(state.easinessFactor).toBeCloseTo(1.7, 5)

      state = calculateSM2(0, state) // EF: 1.7 -> 0.9 -> clamped to 1.3
      expect(state.easinessFactor).toBe(1.3)

      state = calculateSM2(0, state) // EF: 1.3 -> 0.5 -> clamped to 1.3
      expect(state.easinessFactor).toBe(1.3)
    })
  })

  // ============================================================
  // Full Learning Cycle Simulation
  // ============================================================

  describe('full learning cycle simulation', () => {
    it('simulates a realistic learning sequence', () => {
      let state = {}

      // First review: quality=4 (good)
      state = calculateSM2(4, state)
      expect(state.repetition).toBe(1)
      expect(state.interval).toBe(1)
      expect(state.easinessFactor).toBeCloseTo(2.5, 5)

      // Second review: quality=5 (perfect)
      state = calculateSM2(5, state)
      expect(state.repetition).toBe(2)
      expect(state.interval).toBe(6)
      expect(state.easinessFactor).toBeCloseTo(2.6, 5)

      // Third review: quality=3 (barely correct)
      state = calculateSM2(3, state)
      expect(state.repetition).toBe(3)
      // interval = round(6 * 2.6) = 16
      expect(state.interval).toBe(16)
      // EF = 2.6 + (0.1 - 2*(0.08+2*0.02)) = 2.6 - 0.14 = 2.46
      expect(state.easinessFactor).toBeCloseTo(2.46, 5)

      // Fourth review: quality=2 (fail) -> reset
      state = calculateSM2(2, state)
      expect(state.repetition).toBe(0)
      expect(state.interval).toBe(1)
      // EF = 2.46 + (0.1 - 3*(0.08 + 3*0.02)) = 2.46 - 0.32 = 2.14
      expect(state.easinessFactor).toBeCloseTo(2.14, 5)

      // Fifth review: recovery quality=5
      state = calculateSM2(5, state)
      expect(state.repetition).toBe(1)
      expect(state.interval).toBe(1)
      // EF = 2.14 + 0.1 = 2.24
      expect(state.easinessFactor).toBeCloseTo(2.24, 5)
    })
  })

  // ============================================================
  // nextReviewTime Output
  // ============================================================

  describe('nextReviewTime output', () => {
    it('returns a valid ISO 8601 date string', () => {
      const result = calculateSM2(4)
      expect(result.nextReviewTime).toMatch(/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}/)
    })

    it('nextReviewTime is in the future for interval >= 1', () => {
      const result = calculateSM2(4, { repetition: 2, interval: 6, easinessFactor: 2.5 })
      const reviewDate = new Date(result.nextReviewTime)
      const now = new Date()
      expect(reviewDate.getTime()).toBeGreaterThan(now.getTime())
    })

    it('nextReviewTime accounts for interval days', () => {
      const result = calculateSM2(4, { repetition: 2, interval: 6, easinessFactor: 2.5 })
      // interval = 15 days
      const reviewDate = new Date(result.nextReviewTime)
      const today = new Date()
      const todayMidnight = new Date(today.getFullYear(), today.getMonth(), today.getDate(), 0, 0, 0, 0)
      const expectedDate = new Date(todayMidnight.getTime() + 15 * 24 * 60 * 60 * 1000)
      expect(reviewDate.toISOString()).toBe(expectedDate.toISOString())
    })
  })

  // ============================================================
  // Return Value Shape
  // ============================================================

  describe('return value shape', () => {
    it('returns object with all required fields', () => {
      const result = calculateSM2(3)
      expect(result).toHaveProperty('repetition')
      expect(result).toHaveProperty('interval')
      expect(result).toHaveProperty('easinessFactor')
      expect(result).toHaveProperty('nextReviewTime')
      expect(typeof result.repetition).toBe('number')
      expect(typeof result.interval).toBe('number')
      expect(typeof result.easinessFactor).toBe('number')
      expect(typeof result.nextReviewTime).toBe('string')
    })
  })

  // ============================================================
  // EF Upper Bound
  // ============================================================

  describe('easinessFactor upper bound at 5.0', () => {
    it('clamps EF to 5.0 when it would exceed the upper limit', () => {
      // EF at 4.95, quality=5 -> EF = 4.95 + 0.1 = 5.05 -> clamped to 5.0
      const result = calculateSM2(5, { repetition: 0, interval: 0, easinessFactor: 4.95 })
      expect(result.easinessFactor).toBe(5.0)
    })
  })

  // ============================================================
  // Fractional Quality Values
  // ============================================================

  describe('fractional quality values within valid range', () => {
    it('accepts quality=2.5 (fractional, below success threshold)', () => {
      const result = calculateSM2(2.5, { repetition: 2, interval: 6, easinessFactor: 2.5 })
      // quality < 3 -> failure path
      expect(result.repetition).toBe(0)
      expect(result.interval).toBe(1)
    })

    it('accepts quality=3.5 (fractional, success path)', () => {
      const result = calculateSM2(3.5, { repetition: 0, interval: 0, easinessFactor: 2.5 })
      // quality >= 3 -> success path
      expect(result.repetition).toBe(1)
      expect(result.interval).toBe(1)
    })
  })
})
