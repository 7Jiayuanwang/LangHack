import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { shallowMount, flushPromises } from '@vue/test-utils'

const mockPush = vi.fn()
const api = vi.hoisted(() => ({
  getArticleFeed: vi.fn(),
  recordArticleFeedEvent: vi.fn(),
  searchArticles: vi.fn()
}))
const agentApi = vi.hoisted(() => ({
  getLearningAgentContentRecommendations: vi.fn()
}))
const user = vi.hoisted(() => ({ userRole: 'STUDENT' }))

vi.mock('vue-router', () => ({
  useRouter: () => ({ push: mockPush }),
  useRoute: () => ({ path: '/student/articles' }),
  onBeforeRouteLeave: vi.fn()
}))

vi.mock('element-plus', () => ({
  ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn() }
}))

vi.mock('@/api/texts', () => api)
vi.mock('@/api/learningAgent', () => agentApi)
vi.mock('@/store/user', () => ({ useUserStore: () => user }))
vi.mock('@/api/video', () => ({
  getFeaturedVideos: vi.fn().mockResolvedValue({ code: 200, data: [] })
}))

import Articles from '@/views/student/Articles.vue'

const article = (id, title = `Article ${id}`) => ({
  id, title, excerpt: 'An English preview', category: 'technology',
  difficulty: 'MEDIUM', estimatedMinutes: 3, position: id - 1,
  reason: '因为你关注科技'
})

const feedPage = (items = [], hasMore = false, nextPage = 2) => ({
  code: 200,
  data: { items, hasMore, nextPage, seed: 123, onboardingSuggested: false }
})

describe('Student Articles.vue', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    sessionStorage.clear()
    api.getArticleFeed.mockResolvedValue(feedPage())
    api.recordArticleFeedEvent.mockResolvedValue({ code: 200 })
    api.searchArticles.mockResolvedValue({ code: 200, data: { articles: [], total: 0 } })
    agentApi.getLearningAgentContentRecommendations.mockResolvedValue({ code: 200, data: [] })
    user.userRole = 'STUDENT'
    global.fetch = vi.fn().mockResolvedValue({ ok: true, json: () => Promise.resolve({ code: 200 }) })
  })

  afterEach(() => {
    delete global.IntersectionObserver
  })

  const createWrapper = () => shallowMount(Articles)

  it('defaults to the personalized feed, not the old featured list', async () => {
    const wrapper = createWrapper()
    await flushPromises()

    expect(wrapper.vm.selectedCategory).toBe('recommendation')
    expect(api.getArticleFeed).toHaveBeenCalledWith(expect.objectContaining({ mode: 'FOR_YOU', page: 1, size: 12 }))
    expect(global.fetch).not.toHaveBeenCalled()
    wrapper.unmount()
  })

  it('requests server-side topic filtering and preserves article topic labels', async () => {
    const wrapper = createWrapper()
    await flushPromises()
    api.getArticleFeed.mockResolvedValue(feedPage([article(8)]))

    wrapper.vm.selectCategory('technology')
    await flushPromises()

    expect(api.getArticleFeed).toHaveBeenLastCalledWith(expect.objectContaining({
      mode: 'EXPLORE', category: 'technology', page: 1
    }))
    expect(wrapper.vm.articles[0].category).toBe('technology')
    wrapper.unmount()
  })

  it('loads another server page instead of slicing a full article payload in the browser', async () => {
    api.getArticleFeed
      .mockResolvedValueOnce(feedPage([article(1), article(2)], true))
      .mockResolvedValueOnce(feedPage([article(3)], false, 3))
    const wrapper = createWrapper()
    await flushPromises()

    await wrapper.vm.loadMore()
    await flushPromises()

    expect(api.getArticleFeed).toHaveBeenLastCalledWith(expect.objectContaining({ page: 2, seed: 123 }))
    expect(wrapper.vm.articles.map(item => item.id)).toEqual([1, 2, 3])
    expect(wrapper.vm.hasMore).toBe(false)
    wrapper.unmount()
  })

  it('records an impression only when an actual card becomes visible', async () => {
    const observers = []
    global.IntersectionObserver = class {
      constructor(callback, options) { this.callback = callback; this.options = options; observers.push(this) }
      observe() {}
      unobserve() {}
      disconnect() {}
    }
    api.getArticleFeed.mockResolvedValue(feedPage([article(8)]))
    const wrapper = createWrapper()
    await flushPromises()
    const card = wrapper.find('.article-card[data-feed-id]').element
    const visibleObserver = observers.find(observer => observer.options.threshold === 0.5)

    expect(api.recordArticleFeedEvent).not.toHaveBeenCalled()
    visibleObserver.callback([{ isIntersecting: true, intersectionRatio: 0.8, target: card }])

    expect(api.recordArticleFeedEvent).toHaveBeenCalledWith({
      textId: 8, eventType: 'IMPRESSION', source: 'FEED_FOR_YOU', position: 7
    })
    wrapper.unmount()
  })

  it('clicks and dismissals are item-level and do not count as completed reading', async () => {
    api.getArticleFeed.mockResolvedValue(feedPage([article(8)]))
    const wrapper = createWrapper()
    await flushPromises()
    const item = wrapper.vm.articles[0]

    wrapper.vm.handleItemClick(item)
    expect(api.recordArticleFeedEvent).toHaveBeenCalledWith({
      textId: 8, eventType: 'CLICK', source: 'FEED_FOR_YOU', position: 7
    })
    expect(mockPush).toHaveBeenCalledWith(expect.objectContaining({ path: '/article/8' }))

    wrapper.vm.hideItem(item)
    expect(api.recordArticleFeedEvent).toHaveBeenCalledWith({
      textId: 8, eventType: 'DISMISS', source: 'FEED_FOR_YOU', position: 7
    })
    expect(wrapper.vm.articles).toEqual([])
    wrapper.unmount()
  })

  it('pull-to-refresh requests a fresh first page excluding old first-screen articles', async () => {
    api.getArticleFeed.mockResolvedValue(feedPage([article(1), article(2)]))
    const wrapper = createWrapper()
    await flushPromises()
    api.getArticleFeed.mockClear()
    api.getArticleFeed.mockResolvedValue(feedPage([article(3), article(2)]))

    wrapper.vm.onTouchStart({ touches: [{ clientX: 0, clientY: 0 }] })
    wrapper.vm.onTouchMove({ touches: [{ clientX: 0, clientY: 100 }], cancelable: false })
    await wrapper.vm.onTouchEnd()

    expect(api.getArticleFeed).toHaveBeenCalledWith(expect.objectContaining({
      mode: 'FOR_YOU', page: 1, exclude: '1,2'
    }))
    expect(wrapper.vm.articles[0].id).toBe(3)
    wrapper.unmount()
  })

  it('keeps external English suggestions visibly separate and opens only HTTPS on click', async () => {
    agentApi.getLearningAgentContentRecommendations.mockResolvedValue({ code: 200, data: [
      { id: 'external-1', title: 'Science Today', sourceName: 'Open Source', topic: 'science',
        reason: '与你的兴趣相关', external: true, url: 'https://example.org/science' },
      { id: 'external-2', title: 'Unsafe', external: true, url: 'javascript:alert(1)' }
    ] })
    const wrapper = createWrapper()
    await flushPromises()

    expect(agentApi.getLearningAgentContentRecommendations).toHaveBeenCalledWith(6)
    expect(wrapper.findAll('.external-item')).toHaveLength(1)
    const open = vi.spyOn(window, 'open').mockImplementation(() => null)
    await wrapper.find('.external-item').trigger('click')
    expect(open).toHaveBeenCalledWith('https://example.org/science', '_blank', 'noopener,noreferrer')
    expect(api.recordArticleFeedEvent).not.toHaveBeenCalled()
    open.mockRestore()
    wrapper.unmount()
  })

  it('does not call student-only external recommendations for teachers', async () => {
    user.userRole = 'TEACHER'
    const wrapper = createWrapper()
    await flushPromises()
    expect(agentApi.getLearningAgentContentRecommendations).not.toHaveBeenCalled()
    wrapper.unmount()
  })
})
