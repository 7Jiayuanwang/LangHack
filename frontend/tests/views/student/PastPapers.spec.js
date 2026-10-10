import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'

const { sharedPapers, submitPastPaperAttempt } = vi.hoisted(() => ({
  sharedPapers: [],
  submitPastPaperAttempt: vi.fn()
}))

const basePaper = () => ({
  id: 1,
  serverManaged: true,
  category: 'cet4',
  title: '已发布四级真题',
  duration: 30,
  status: 'ready',
  questions: [{ id: 'q1', type: 'single_choice', section: '阅读理解', stem: 'Question?', options: ['A', 'B', 'C', 'D'], answer: 0, analysis: 'Reason.' }]
})

vi.mock('element-plus', () => ({
  ElMessage: { success: vi.fn(), error: vi.fn() },
  ElMessageBox: { confirm: vi.fn().mockResolvedValue('confirm') }
}))

vi.mock('@/api/pastPapers', () => ({
  getSharedPastPapers: vi.fn().mockResolvedValue({ data: sharedPapers }),
  submitPastPaperAttempt,
  requestWritingScore: vi.fn(),
  deleteSharedPastPaper: vi.fn()
}))

vi.mock('@/store/user', () => ({
  useUserStore: () => ({ userRole: 'STUDENT' })
}))

import PastPapers from '@/views/student/PastPapers.vue'

describe('Student PastPapers.vue', () => {
  beforeEach(() => {
    localStorage.clear()
    sharedPapers.splice(0, sharedPapers.length, basePaper())
    submitPastPaperAttempt.mockReset()
  })

  async function startStudyMode(wrapper) {
    await vi.waitFor(() => expect(wrapper.find('.start-button').exists()).toBe(true))
    await wrapper.find('.start-button').trigger('click')
    await wrapper.find('.mode-option.study').trigger('click')
  }

  it('shows three exam categories', () => {
    const wrapper = mount(PastPapers, { global: { stubs: { ElIcon: true } } })
    expect(wrapper.text()).toContain('四级')
    expect(wrapper.text()).toContain('六级')
    expect(wrapper.text()).toContain('考研')
  })

  it('starts a published paper and records an answer', async () => {
    const wrapper = mount(PastPapers, { global: { stubs: { ElIcon: true } } })
    await startStudyMode(wrapper)
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

  it('shows combined score and per-question AI feedback after submission', async () => {
    sharedPapers.splice(0, sharedPapers.length, {
      ...basePaper(),
      questions: [
        ...basePaper().questions,
        { id: 'q2', type: 'writing', section: '写作', stem: 'Write an essay.', referenceAnswer: 'Reference.', analysis: 'Rubric.' }
      ]
    })
    submitPastPaperAttempt.mockResolvedValue({
      data: {
        finalScorePercentage: 90,
        aiScorePercentage: 80,
        grading: {
          questions: [{
            questionId: 'q2',
            score: 80,
            summary: '内容完整',
            issues: ['衔接略弱'],
            suggestions: ['增加连接词'],
            revisedAnswer: 'Improved answer.'
          }]
        }
      }
    })

    const wrapper = mount(PastPapers, { global: { stubs: { ElIcon: true } } })
    await startStudyMode(wrapper)
    await wrapper.find('.option-button').trigger('click')
    await wrapper.find('.primary-button').trigger('click')
    await wrapper.find('textarea').setValue('My essay')
    await wrapper.find('.primary-button').trigger('click')

    await vi.waitFor(() => expect(wrapper.text()).toContain('综合得分 90%'))
    expect(wrapper.text()).toContain('主观题 AI 得分 80/100')
    await wrapper.find('.primary-button').trigger('click')
    expect(wrapper.text()).toContain('智能判分：80/100')
    expect(wrapper.text()).toContain('建议答案：Improved answer.')
  })
})
