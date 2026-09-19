import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, shallowMount } from '@vue/test-utils'

vi.mock('vue-router', () => ({ useRoute: () => ({ query: {} }) }))
vi.mock('@/utils/auth', () => ({ getAuthHeader: () => 'Bearer test-token' }))
vi.mock('@/api/conversation', () => ({
  getConversations: vi.fn().mockResolvedValue({ code: 200, data: { records: [] } }),
  getConversationDetail: vi.fn(),
  deleteConversation: vi.fn(),
  shareConversation: vi.fn()
}))
vi.mock('element-plus', () => ({
  ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn() },
  ElMessageBox: { confirm: vi.fn() }
}))

import AiQaPage from '@/views/shared/AiQaPage.vue'

describe('Xiaoya provider history consent', () => {
  beforeEach(() => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({
      ok: true,
      headers: { get: () => 'application/json' },
      json: async () => ({ code: 200, data: { response: 'OK', conversationId: 42 } })
    }))
  })

  afterEach(() => vi.unstubAllGlobals())

  it('defaults off and resets consent when starting another conversation', async () => {
    const wrapper = shallowMount(AiQaPage, {
      props: { apiPath: '/api/student/ai-qa/chat/stream', agentMode: true }
    })
    await flushPromises()

    wrapper.vm.inputMessage = 'First question'
    await wrapper.vm.sendMessage()
    expect(JSON.parse(fetch.mock.calls[0][1].body).allowFallbackHistory).toBe(false)

    await wrapper.find('.fallback-history-consent input').setValue(true)
    wrapper.vm.inputMessage = 'Second question'
    await wrapper.vm.sendMessage()
    expect(JSON.parse(fetch.mock.calls[1][1].body).allowFallbackHistory).toBe(true)

    wrapper.vm.startNewConversation()
    expect(wrapper.vm.allowFallbackHistory).toBe(false)
    wrapper.unmount()
  })
})
