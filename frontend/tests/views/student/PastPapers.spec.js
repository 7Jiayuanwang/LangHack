import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'

vi.mock('element-plus', () => ({
  ElMessage: { success: vi.fn(), error: vi.fn() },
  ElMessageBox: { confirm: vi.fn().mockResolvedValue('confirm') }
}))

vi.mock('@/api/pastPapers', () => ({
  getSharedPastPapers: vi.fn().mockResolvedValue({ data: [] }),
  deleteSharedPastPaper: vi.fn()
}))

vi.mock('@/store/user', () => ({
  useUserStore: () => ({ userRole: 'STUDENT' })
}))

import PastPapers from '@/views/student/PastPapers.vue'

describe('Student PastPapers.vue', () => {
  beforeEach(() => localStorage.clear())

  it('shows three exam categories', () => {
    const wrapper = mount(PastPapers, { global: { stubs: { ElIcon: true } } })
    expect(wrapper.text()).toContain('四级')
    expect(wrapper.text()).toContain('六级')
    expect(wrapper.text()).toContain('考研')
  })

  it('starts a ready paper and records an answer', async () => {
    const wrapper = mount(PastPapers, { global: { stubs: { ElIcon: true } } })
    await wrapper.find('.start-button:not([disabled])').trigger('click')
    expect(wrapper.find('.question-card').exists()).toBe(true)
    await wrapper.find('.option-button').trigger('click')
    expect(wrapper.find('.option-button').classes()).toContain('selected')
  })

  it('keeps scanned papers disabled until content is verified', () => {
    const wrapper = mount(PastPapers, { global: { stubs: { ElIcon: true } } })
    expect(wrapper.findAll('.start-button[disabled]').length).toBe(2)
  })
})
