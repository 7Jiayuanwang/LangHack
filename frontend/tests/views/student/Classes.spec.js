import { describe, it, expect, vi, beforeEach } from 'vitest'
import { shallowMount } from '@vue/test-utils'

vi.mock('element-plus', () => ({
  ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn() },
  ElMessageBox: { confirm: vi.fn().mockResolvedValue('confirm') }
}))

global.fetch = vi.fn()

import Classes from '@/views/student/Classes.vue'

describe('Classes.vue', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    global.fetch.mockClear()
    localStorage.setItem('token', 'test-token')
  })

  it('mounts without error', () => {
    const wrapper = shallowMount(Classes)
    expect(wrapper.exists()).toBe(true)
  })

  it('component is defined', () => {
    const wrapper = shallowMount(Classes)
    expect(wrapper.vm).toBeDefined()
  })

  it('cleanup on unmount works', () => {
    const wrapper = shallowMount(Classes)
    expect(wrapper.exists()).toBe(true)
    wrapper.unmount()
    expect(wrapper.exists()).toBe(false)
  })

  it('has the expected component structure', () => {
    const wrapper = shallowMount(Classes)
    expect(wrapper.html().length).toBeGreaterThan(0)
  })

  it('renders without throwing errors', () => {
    expect(() => {
      shallowMount(Classes)
    }).not.toThrow()
  })
})
