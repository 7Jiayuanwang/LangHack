import { describe, it, expect, vi, beforeEach } from 'vitest'
import { shallowMount } from '@vue/test-utils'

const mockPush = vi.fn()
vi.mock('vue-router', () => ({
  useRouter: () => ({ push: mockPush })
}))

vi.mock('element-plus', () => ({
  ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn() }
}))

import Articles from '@/views/student/Articles.vue'

describe('Student Articles.vue', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    global.fetch = vi.fn().mockResolvedValue({
      ok: true,
      json: () => Promise.resolve({ code: 200, data: [] })
    })
  })

  const createWrapper = () => shallowMount(Articles)

  it('has loading ref initially true', () => {
    const wrapper = createWrapper()
    expect(wrapper.vm.loading).toBe(true)
  })

  it('has empty articles array', () => {
    const wrapper = createWrapper()
    expect(wrapper.vm.articles).toEqual([])
  })

  it('has selectedCategory as empty string', () => {
    const wrapper = createWrapper()
    expect(wrapper.vm.selectedCategory).toBe('')
  })

  it('has categories array defined', () => {
    const wrapper = createWrapper()
    expect(wrapper.vm.categories).toBeDefined()
    expect(Array.isArray(wrapper.vm.categories)).toBe(true)
  })

  it('getRandomGradient returns a string', () => {
    const wrapper = createWrapper()
    const result = wrapper.vm.getRandomGradient()
    expect(typeof result).toBe('string')
  })

  it('selectCategory updates selectedCategory', () => {
    const wrapper = createWrapper()
    wrapper.vm.selectCategory('science')
    expect(wrapper.vm.selectedCategory).toBe('science')
  })

  it('getSnippet truncates long content', () => {
    const wrapper = createWrapper()
    const longText = 'a'.repeat(200)
    const result = wrapper.vm.getSnippet(longText)
    expect(result.length).toBeLessThanOrEqual(153)
  })

  it('getSnippet handles empty content', () => {
    const wrapper = createWrapper()
    expect(wrapper.vm.getSnippet('')).toBe('')
  })

  it('getCategoryText returns label for valid value', () => {
    const wrapper = createWrapper()
    const result = wrapper.vm.getCategoryText('')
    expect(typeof result).toBe('string')
  })

  it('readArticle navigates to article', () => {
    const wrapper = createWrapper()
    wrapper.vm.readArticle({ id: 10, title: 'Test' })
    expect(mockPush).toHaveBeenCalled()
  })
})
