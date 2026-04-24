/**
 * FeatureGate 组件测试
 *
 * 覆盖：
 *   - featureKey 为空 → 始终渲染默认 slot（向后兼容）
 *   - feature toggle 启用 → 渲染默认 slot
 *   - feature toggle 禁用 → 渲染 fallback slot
 *   - feature toggle 禁用 + 无 fallback → 不渲染任何内容
 *
 * 运行：npx vitest run tests/components/FeatureGate.spec.js
 */

import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'

// Mock useFeatureToggles composable
const mockIsEnabled = vi.hoisted(() => vi.fn())
vi.mock('@/composables/useFeatureToggles', () => ({
  useFeatureToggles: () => ({
    isEnabled: mockIsEnabled,
  }),
}))

import FeatureGate from '@/components/FeatureGate.vue'

describe('FeatureGate', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('featureKey 为空时渲染默认 slot（向后兼容）', () => {
    const wrapper = mount(FeatureGate, {
      props: { featureKey: '' },
      slots: {
        default: '<div class="content">visible</div>',
        fallback: '<div class="fallback">hidden</div>',
      },
    })
    expect(wrapper.html()).toContain('visible')
    expect(wrapper.html()).not.toContain('hidden')
    // 空 key 不应查询 isEnabled
    expect(mockIsEnabled).not.toHaveBeenCalled()
  })

  it('feature toggle 启用时渲染默认 slot', () => {
    mockIsEnabled.mockReturnValue(true)
    const wrapper = mount(FeatureGate, {
      props: { featureKey: 'page_vocabulary' },
      slots: {
        default: '<div class="content">enabled content</div>',
        fallback: '<div class="fallback">disabled content</div>',
      },
    })
    expect(wrapper.html()).toContain('enabled content')
    expect(wrapper.html()).not.toContain('disabled content')
    expect(mockIsEnabled).toHaveBeenCalledWith('page_vocabulary')
  })

  it('feature toggle 禁用时渲染 fallback slot', () => {
    mockIsEnabled.mockReturnValue(false)
    const wrapper = mount(FeatureGate, {
      props: { featureKey: 'page_vocabulary' },
      slots: {
        default: '<div class="content">enabled content</div>',
        fallback: '<div class="fallback">disabled content</div>',
      },
    })
    expect(wrapper.html()).not.toContain('enabled content')
    expect(wrapper.html()).toContain('disabled content')
    expect(mockIsEnabled).toHaveBeenCalledWith('page_vocabulary')
  })

  it('feature toggle 禁用且无 fallback 时不渲染任何内容', () => {
    mockIsEnabled.mockReturnValue(false)
    const wrapper = mount(FeatureGate, {
      props: { featureKey: 'page_vocabulary' },
      slots: {
        default: '<div class="content">enabled content</div>',
      },
    })
    expect(wrapper.html()).not.toContain('enabled content')
    expect(wrapper.text()).toBe('')
  })

  it('默认 featureKey 为空字符串时渲染默认 slot', () => {
    const wrapper = mount(FeatureGate, {
      slots: {
        default: '<div>visible</div>',
      },
    })
    expect(wrapper.html()).toContain('visible')
    expect(mockIsEnabled).not.toHaveBeenCalled()
  })
})
