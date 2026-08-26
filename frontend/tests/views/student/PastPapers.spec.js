import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'

const { sharedPapers } = vi.hoisted(() => ({ sharedPapers: [{
  id: 1,
  serverManaged: true,
  category: 'cet4',
  title: '已发布四级真题',
  duration: 30,
  status: 'ready',
  questions: [{ id: 'q1', type: 'single_choice', section: '阅读理解', stem: 'Question?', options: ['A', 'B', 'C', 'D'], answer: 0, analysis: 'Reason.' }]
}] }))

vi.mock('element-plus', () => ({
  ElMessage: { success: vi.fn(), error: vi.fn() },
  ElMessageBox: { confirm: vi.fn().mockResolvedValue('confirm') }
}))

vi.mock('@/api/pastPapers', () => ({
  getSharedPastPapers: vi.fn().mockResolvedValue({ data: sharedPapers }),
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

  it('starts a published paper and records an answer', async () => {
    const wrapper = mount(PastPapers, { global: { stubs: { ElIcon: true } } })
    await vi.waitFor(() => expect(wrapper.find('.start-button').exists()).toBe(true))
    await wrapper.find('.start-button').trigger('click')
    expect(wrapper.find('.question-card').exists()).toBe(true)
    await wrapper.find('.option-button').trigger('click')
    expect(wrapper.find('.option-button').classes()).toContain('selected')
  })

  it('does not show built-in demo or pending papers', async () => {
    const wrapper = mount(PastPapers, { global: { stubs: { ElIcon: true } } })
    await vi.waitFor(() => expect(wrapper.text()).toContain('已发布四级真题'))
    expect(wrapper.text()).not.toContain('功能样题')
    expect(wrapper.text()).not.toContain('待校对导入')
  })
})
