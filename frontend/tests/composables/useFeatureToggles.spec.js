/**
 * useFeatureToggles 组合式 API 测试
 *
 * 覆盖：
 *   - isEnabled 默认启用（未配置 / 显式 true / 字符串 "true"）
 *   - isEnabled 显式禁用（false / 字符串 "false"）
 *   - load 正常加载并写入 sessionStorage 缓存
 *   - load 命中缓存时不再调用 API
 *   - invalidate 清除缓存
 *   - API 失败时降级为全启用
 *
 * 运行：npx vitest run tests/composables/useFeatureToggles.spec.js
 */

import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'

// Mock API 层
const mockGetFeatureToggles = vi.fn()
const mockGetPersonalFeatureToggles = vi.fn()
vi.mock('@/api/systemConfig', () => ({
  getFeatureToggles: (...args) => mockGetFeatureToggles(...args),
  getPersonalFeatureToggles: (...args) => mockGetPersonalFeatureToggles(...args),
}))

const CACHE_KEY = 'feature_toggles_cache_v1'

describe('useFeatureToggles', () => {
  let useFeatureToggles

  beforeEach(async () => {
    sessionStorage.clear()
    vi.clearAllMocks()
    // 重置模块状态（composable 使用顶层 let loaded 变量）
    vi.resetModules()
    const mod = await import('@/composables/useFeatureToggles.js')
    useFeatureToggles = mod.useFeatureToggles
  })

  afterEach(() => {
    sessionStorage.clear()
  })

  describe('isEnabled', () => {
    it('未配置的 key 默认启用（避免意外隐藏新功能）', async () => {
      const { isEnabled } = useFeatureToggles()
      expect(isEnabled('unknown_key')).toBe(true)
    })

    it('空 key 返回 true（安全兜底）', () => {
      const { isEnabled } = useFeatureToggles()
      expect(isEnabled('')).toBe(true)
      expect(isEnabled(null)).toBe(true)
      expect(isEnabled(undefined)).toBe(true)
    })

    it('加载后，true / "true" 均视为启用', async () => {
      mockGetFeatureToggles.mockResolvedValueOnce({
        code: 200,
        data: { feat_a: true, feat_b: 'true', feat_c: false, feat_d: 'false' },
      })
      const { isEnabled, load } = useFeatureToggles()
      await load()
      expect(isEnabled('feat_a')).toBe(true)
      expect(isEnabled('feat_b')).toBe(true)
      expect(isEnabled('feat_c')).toBe(false)
      expect(isEnabled('feat_d')).toBe(false)
    })
  })

  describe('load', () => {
    it('首次 load 调用 API 并写入 sessionStorage 缓存', async () => {
      mockGetFeatureToggles.mockResolvedValueOnce({
        code: 200,
        data: { page_dashboard: true },
      })
      const { load } = useFeatureToggles()
      await load()
      expect(mockGetFeatureToggles).toHaveBeenCalledTimes(1)
      const cached = JSON.parse(sessionStorage.getItem(CACHE_KEY) || '{}')
      expect(cached.data).toEqual({ page_dashboard: true })
      expect(typeof cached.ts).toBe('number')
    })

    it('命中缓存时不再调用 API', async () => {
      sessionStorage.setItem(
        CACHE_KEY,
        JSON.stringify({ data: { cached_key: true }, ts: Date.now() }),
      )
      const { load, isEnabled } = useFeatureToggles()
      const res = await load()
      expect(mockGetFeatureToggles).not.toHaveBeenCalled()
      expect(res).toEqual({ cached_key: true })
      expect(isEnabled('cached_key')).toBe(true)
    })

    it('personal=true 调用 getPersonalFeatureToggles（应用三层覆盖）', async () => {
      mockGetPersonalFeatureToggles.mockResolvedValueOnce({
        code: 200,
        data: { page_courses: false },
      })
      const { load } = useFeatureToggles()
      await load({ personal: true })
      expect(mockGetPersonalFeatureToggles).toHaveBeenCalledTimes(1)
      expect(mockGetFeatureToggles).not.toHaveBeenCalled()
    })

    it('API 失败时降级为空 toggles（isEnabled 默认启用）', async () => {
      mockGetFeatureToggles.mockRejectedValueOnce(new Error('network'))
      const { load, isEnabled } = useFeatureToggles()
      await load()
      expect(isEnabled('any_key')).toBe(true)
    })

    it('过期缓存（>5min）不命中', async () => {
      const expiredTs = Date.now() - 6 * 60 * 1000
      sessionStorage.setItem(
        CACHE_KEY,
        JSON.stringify({ data: { old_key: true }, ts: expiredTs }),
      )
      mockGetFeatureToggles.mockResolvedValueOnce({
        code: 200,
        data: { new_key: true },
      })
      const { load } = useFeatureToggles()
      await load()
      expect(mockGetFeatureToggles).toHaveBeenCalledTimes(1)
    })
  })

  describe('invalidate', () => {
    it('清除 sessionStorage 缓存并重置 loaded 标志', async () => {
      mockGetFeatureToggles.mockResolvedValueOnce({
        code: 200,
        data: { k: true },
      })
      const { load, invalidate, isLoaded } = useFeatureToggles()
      await load()
      expect(isLoaded()).toBe(true)
      expect(sessionStorage.getItem(CACHE_KEY)).not.toBeNull()

      invalidate()
      expect(isLoaded()).toBe(false)
      expect(sessionStorage.getItem(CACHE_KEY)).toBeNull()
    })
  })
})
