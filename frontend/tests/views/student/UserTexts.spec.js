import { describe, it, expect, vi, beforeEach } from 'vitest'
import { shallowMount } from '@vue/test-utils'

vi.mock('vue-router', () => ({
  useRouter: () => ({ push: vi.fn() }),
  useRoute: () => ({ params: {}, query: {} })
}))

vi.mock('element-plus', () => ({
  ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn() },
  ElMessageBox: { confirm: vi.fn().mockResolvedValue('confirm') }
}))

vi.mock('@/utils/request', () => ({
  default: vi.fn().mockResolvedValue({ code: 200, data: [] })
}))

vi.mock('dompurify', () => ({
  default: {
    sanitize: vi.fn((content) => content)
  }
}))

vi.mock('marked', () => ({
  marked: vi.fn().mockReturnValue('<p>test</p>')
}))

import UserTexts from '@/views/student/UserTexts.vue'

describe('UserTexts.vue', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('mounts without error', () => {
    const wrapper = shallowMount(UserTexts)
    expect(wrapper.exists()).toBe(true)
  })

  it('component is defined', () => {
    const wrapper = shallowMount(UserTexts)
    expect(wrapper.vm).toBeDefined()
  })

  it('cleanup on unmount works', () => {
    const wrapper = shallowMount(UserTexts)
    expect(wrapper.exists()).toBe(true)
    wrapper.unmount()
    expect(wrapper.exists()).toBe(false)
  })

  it('has the expected component structure', () => {
    const wrapper = shallowMount(UserTexts)
    expect(wrapper.html().length).toBeGreaterThan(0)
  })

  it('renders without throwing errors', () => {
    expect(() => {
      shallowMount(UserTexts)
    }).not.toThrow()
  })
})
