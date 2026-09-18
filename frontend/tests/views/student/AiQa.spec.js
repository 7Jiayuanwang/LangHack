import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'

vi.mock('vue-router', () => ({
  useRoute: () => ({ params: {}, query: { text: 'Test text', title: 'Test title' } })
}))

vi.mock('element-plus', () => ({
  ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn(), info: vi.fn() }
}))

vi.mock('@/views/shared/AiQaPage.vue', () => ({
  default: {
    name: 'AiQaPage',
    props: ['apiPath', 'backLabel', 'agentMode'],
    template: '<div class="ai-qa-page-stub">{{ apiPath }}|{{ backLabel }}</div>'
  }
}))

import AiQa from '@/views/student/AiQa.vue'

describe('Student AiQa.vue (wrapper)', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  const createWrapper = () => mount(AiQa)

  it('mounts without error', () => {
    const wrapper = createWrapper()
    expect(wrapper.exists()).toBe(true)
  })

  it('renders AiQaPage child component', () => {
    const wrapper = createWrapper()
    const child = wrapper.findComponent({ name: 'AiQaPage' })
    expect(child.exists()).toBe(true)
  })

  it('passes correct api-path to AiQaPage', () => {
    const wrapper = createWrapper()
    const child = wrapper.findComponent({ name: 'AiQaPage' })
    expect(child.props('apiPath')).toBe('/api/student/ai-qa/chat/stream')
  })

  it('passes correct back-label to AiQaPage', () => {
    const wrapper = createWrapper()
    const child = wrapper.findComponent({ name: 'AiQaPage' })
    expect(child.props('backLabel')).toBe('返回阅读')
  })

  it('uses normal QA mode when route does not request the agent', () => {
    const wrapper = createWrapper()
    const child = wrapper.findComponent({ name: 'AiQaPage' })
    expect(child.props('agentMode')).toBe(false)
  })
})
