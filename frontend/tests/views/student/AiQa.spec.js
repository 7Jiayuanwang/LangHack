import { describe, it, expect, vi, beforeEach } from 'vitest'
import { shallowMount } from '@vue/test-utils'

vi.mock('vue-router', () => ({
  useRoute: () => ({ params: {}, query: { text: 'Test text', title: 'Test title' } })
}))

vi.mock('element-plus', () => ({
  ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn(), info: vi.fn() }
}))

vi.mock('marked', () => ({
  marked: vi.fn(text => text || '')
}))

vi.mock('dompurify', () => ({
  default: { sanitize: vi.fn(text => text || '') }
}))

import AiQa from '@/views/student/AiQa.vue'

describe('Student AiQa.vue', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    global.fetch = vi.fn().mockResolvedValue({
      ok: true,
      json: () => Promise.resolve({ response: 'AI answer' })
    })
  })

  const createWrapper = () => shallowMount(AiQa)

  it('mounts without error', () => {
    const wrapper = createWrapper()
    expect(wrapper.exists()).toBe(true)
  })

  it('has candidateQuestions array', () => {
    const wrapper = createWrapper()
    expect(wrapper.vm.candidateQuestions).toBeDefined()
    expect(Array.isArray(wrapper.vm.candidateQuestions)).toBe(true)
  })

  it('has messages array', () => {
    const wrapper = createWrapper()
    expect(Array.isArray(wrapper.vm.messages)).toBe(true)
  })

  it('has inputMessage with default value', () => {
    const wrapper = createWrapper()
    expect(wrapper.vm.inputMessage).toBe('')
  })
})
