import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'

const state = vi.hoisted(() => ({
  user: { isLoggedIn: true, userRole: 'STUDENT', token: 'test-token' },
  route: { path: '/articles', fullPath: '/articles', meta: {} },
  push: vi.fn(),
  getUnread: vi.fn(),
  markRead: vi.fn()
}))

vi.mock('vue-router', () => ({
  useRoute: () => state.route,
  useRouter: () => ({ push: state.push })
}))
vi.mock('@/store/user', () => ({ useUserStore: () => state.user }))
vi.mock('@/api/notification', () => ({
  getUnreadNotifications: state.getUnread,
  markNotificationRead: state.markRead
}))
vi.mock('@/views/shared/AiQaPage.vue', () => ({
  __esModule: true,
  default: {
    name: 'AiQaPage',
    props: { apiPath: String, agentMode: Boolean, embedded: Boolean, contextText: String, contextTitle: String },
    template: '<div class="chat-stub" />'
  }
}))
vi.mock('@/components/AgentMascot.vue', () => ({
  default: { name: 'AgentMascot', props: ['size'], template: '<div class="mascot-stub" />' }
}))

import AgentCompanionWidget from '@/components/AgentCompanionWidget.vue'

let wrapper
const mountWidget = () => {
  wrapper = mount(AgentCompanionWidget)
  return wrapper
}

describe('AgentCompanionWidget', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    state.user.isLoggedIn = true
    state.user.userRole = 'STUDENT'
    state.route.path = '/articles'
    state.route.fullPath = '/articles'
    state.route.meta = {}
    state.getUnread.mockResolvedValue({ code: 200, data: [] })
    state.markRead.mockResolvedValue({ code: 200, data: '已标记为已读' })
  })

  afterEach(() => {
    wrapper?.unmount()
    wrapper = null
  })

  it('shows 小芽 on the articles page and opens an inline agent chat', async () => {
    const widget = mountWidget()
    expect(widget.find('.companion-launcher').exists()).toBe(true)
    await widget.find('.companion-launcher').trigger('click')
    await flushPromises()
    expect(widget.find('.companion-panel').isVisible()).toBe(true)
    expect(widget.findComponent({ name: 'AiQaPage' }).props()).toMatchObject({
      apiPath: '/api/student/ai-qa/chat/stream', agentMode: true, embedded: true
    })
  })

  it('shows unread count and only marks a message read after an explicit click', async () => {
    state.getUnread.mockResolvedValue({ code: 200, data: [{ id: 7, title: '学习提醒', content: '今天读一篇英文文章' }] })
    const widget = mountWidget()
    await flushPromises()
    expect(widget.find('.unread-badge').text()).toBe('1')
    expect(state.markRead).not.toHaveBeenCalled()

    await widget.find('.companion-launcher').trigger('click')
    await widget.findAll('.companion-tabs button')[2].trigger('click')
    await widget.find('.message-title').trigger('click')
    expect(widget.find('.message-card p').text()).toBe('今天读一篇英文文章')
    expect(state.markRead).not.toHaveBeenCalled()
    await widget.find('.mark-read').trigger('click')
    await flushPromises()
    expect(state.markRead).toHaveBeenCalledWith(7)
    expect(widget.find('.unread-badge').exists()).toBe(false)
  })

  it('links the companion to the existing learning tools', async () => {
    const widget = mountWidget()
    await widget.find('.companion-launcher').trigger('click')
    await widget.findAll('.companion-tabs button')[1].trigger('click')
    await widget.findAll('.companion-tools button')[0].trigger('click')
    expect(state.push).toHaveBeenCalledWith('/student/learning-agent')
  })

  it('passes the current article title into 小芽 chat', async () => {
    state.route.path = '/article/42'
    state.route.fullPath = '/article/42'
    const title = document.createElement('h2')
    title.className = 'header-title'
    title.textContent = 'Learning with English'
    document.body.appendChild(title)
    try {
      const widget = mountWidget()
      await widget.find('.companion-launcher').trigger('click')
      await flushPromises()
      expect(widget.findComponent({ name: 'AiQaPage' }).props('contextTitle')).toBe('Learning with English')
    } finally {
      title.remove()
    }
  })

  it('does not show a student companion to staff accounts', () => {
    state.user.userRole = 'TEACHER'
    expect(mountWidget().find('.companion-launcher').exists()).toBe(false)
  })
})
