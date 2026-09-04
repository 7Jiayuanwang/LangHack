import { describe, it, expect, vi, beforeEach } from 'vitest'
import { shallowMount, flushPromises } from '@vue/test-utils'

const mockPush = vi.fn()
vi.mock('vue-router', () => ({
  useRouter: () => ({ push: mockPush }),
  useRoute: () => ({ path: '/student/articles' }),
  onBeforeRouteLeave: vi.fn()
}))

vi.mock('element-plus', () => ({
  ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn() }
}))

vi.mock('@/api/video', () => ({
  getFeaturedVideos: vi.fn().mockResolvedValue({ code: 200, data: [] })
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

  it('handleItemClick navigates to article', () => {
    const wrapper = createWrapper()
    wrapper.vm.handleItemClick({ id: 10, title: 'Test' })
    expect(mockPush).toHaveBeenCalled()
  })

  it('pulling down from the top triggers a real refresh request', async () => {
    const wrapper = createWrapper()
    await flushPromises()

    wrapper.vm.allItems = [
      { id: 1, _type: 'article', title: 'First' },
      { id: 2, _type: 'article', title: 'Second' }
    ]
    wrapper.vm.loading = false
    global.fetch.mockResolvedValue({
      ok: true,
      json: () => Promise.resolve({
        code: 200,
        data: [
          { id: 1, title: 'First', content: 'first' },
          { id: 2, title: 'Second', content: 'second' }
        ]
      })
    })
    global.fetch.mockClear()

    wrapper.vm.onTouchStart({ touches: [{ clientX: 0, clientY: 0 }] })
    const moveEvent = {
      touches: [{ clientX: 0, clientY: 100 }],
      cancelable: false,
      preventDefault: vi.fn()
    }
    expect(() => wrapper.vm.onTouchMove(moveEvent)).not.toThrow()
    expect(wrapper.vm.isPulling).toBe(true)

    await wrapper.vm.onTouchEnd()

    expect(global.fetch).toHaveBeenCalled()
    const [url] = global.fetch.mock.calls[0]
    expect(new URL(url, 'http://localhost').searchParams.get('refresh')).toBe('true')
    expect(wrapper.vm.articles[0].id).toBe(2)
    await wrapper.unmount()
  })
})
