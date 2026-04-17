/**
 * Property-based tests for isCoverFileValid
 * Validates: Requirements 1.2, 1.3
 *
 * Property 1-3: 封面文件校验
 *   isCoverFileValid(file) returns true if and only if
 *   file.type ∈ ALLOWED_TYPES AND file.size ≤ 10MB
 */
import { describe, it, expect } from 'vitest'
import * as fc from 'fast-check'
import { isCoverFileValid, ALLOWED_TYPES, MAX_SIZE_MB } from '@/utils/coverUpload'

const MAX_BYTES = MAX_SIZE_MB * 1024 * 1024

// Arbitrary: a file-like object with a given type and size
const fileArb = (typeArb, sizeArb) =>
  fc.record({ type: typeArb, size: sizeArb })

describe('isCoverFileValid — Property 1-3: 封面文件校验', () => {
  /**
   * Property 1: 非法 MIME 类型 → 返回 false
   * Validates: Requirements 1.2
   */
  it('Property 1: rejects files with disallowed MIME types', () => {
    const disallowedType = fc.string({ minLength: 1 }).filter(
      (t) => !ALLOWED_TYPES.includes(t)
    )
    fc.assert(
      fc.property(
        fileArb(disallowedType, fc.integer({ min: 0, max: MAX_BYTES })),
        (file) => {
          expect(isCoverFileValid(file)).toBe(false)
        }
      ),
      { numRuns: 100 }
    )
  })

  /**
   * Property 2: 超过 10MB → 返回 false
   * Validates: Requirements 1.3
   */
  it('Property 2: rejects files larger than 10MB', () => {
    const allowedType = fc.constantFrom(...ALLOWED_TYPES)
    const oversizedBytes = fc.integer({ min: MAX_BYTES + 1, max: MAX_BYTES * 10 })
    fc.assert(
      fc.property(
        fileArb(allowedType, oversizedBytes),
        (file) => {
          expect(isCoverFileValid(file)).toBe(false)
        }
      ),
      { numRuns: 100 }
    )
  })

  /**
   * Property 3: 合法类型 + 合法大小 → 返回 true
   * Validates: Requirements 1.2, 1.3
   */
  it('Property 3: accepts files with allowed type and size ≤ 10MB', () => {
    const allowedType = fc.constantFrom(...ALLOWED_TYPES)
    const validBytes = fc.integer({ min: 0, max: MAX_BYTES })
    fc.assert(
      fc.property(
        fileArb(allowedType, validBytes),
        (file) => {
          expect(isCoverFileValid(file)).toBe(true)
        }
      ),
      { numRuns: 100 }
    )
  })
})
