import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, shallowMount } from '@vue/test-utils'

const routerMocks = vi.hoisted(() => ({ push: vi.fn(), back: vi.fn() }))

vi.mock('vue-router', () => ({
  useRouter: () => routerMocks
}))

vi.mock('element-plus', () => ({
  ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn() },
  ElMessageBox: { confirm: vi.fn().mockResolvedValue(true) }
}))

const contextResponse = {
  code: 200,
  data: {
    onboardingCompleted: false,
    goals: [],
    interests: [],
    memories: [],
    preferredFormats: [],
    schedule: { totalCourses: 0, todayCourseCount: 0, todayCourseNames: [] },
    suggestedQuestions: [],
    greeting: '先认识一下吧'
  }
}

const apiMocks = vi.hoisted(() => ({
  getLearningAgentContext: vi.fn(),
  getLearningAgentContentRecommendations: vi.fn(),
  getLearningAgentStudyLoop: vi.fn(),
  saveLearningAgentOnboarding: vi.fn(),
  addLearningAgentMemory: vi.fn(),
  deleteLearningAgentMemory: vi.fn()
}))

vi.mock('@/api/learningAgent', () => apiMocks)

import LearningAgent from '@/views/student/LearningAgent.vue'

describe('Student LearningAgent.vue', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    sessionStorage.clear()
    apiMocks.getLearningAgentContext.mockResolvedValue(contextResponse)
    apiMocks.getLearningAgentContentRecommendations.mockResolvedValue({ code: 200, data: [] })
    apiMocks.getLearningAgentStudyLoop.mockResolvedValue({
      code: 200,
      data: { attemptCount: 0, practicedArticleCount: 0, answeredQuestionCount: 0,
        correctQuestionCount: 0, dueReviews: [], recentWrongQuestions: [] }
    })
    apiMocks.saveLearningAgentOnboarding.mockResolvedValue({
      code: 200,
      data: { ...contextResponse.data, onboardingCompleted: true, weeklyMinutes: 210 }
    })
  })

  it('loads the current learning-agent context', async () => {
    const wrapper = shallowMount(LearningAgent)
    await flushPromises()

    expect(apiMocks.getLearningAgentContext).toHaveBeenCalledOnce()
    expect(wrapper.vm.context.onboardingCompleted).toBe(false)
  })

  it('allows several learning goals and saves onboarding', async () => {
    const wrapper = shallowMount(LearningAgent)
    await flushPromises()

    wrapper.vm.toggleGoal(wrapper.vm.goalOptions[0])
    wrapper.vm.toggleGoal(wrapper.vm.goalOptions[3])
    await wrapper.vm.submitOnboarding()

    expect(apiMocks.saveLearningAgentOnboarding).toHaveBeenCalledWith(expect.objectContaining({
      goals: expect.arrayContaining([
        expect.objectContaining({ goalType: 'CET4' }),
        expect.objectContaining({ goalType: 'ACADEMIC_READING' })
      ])
    }))
  })

  it('opens chat in learning-agent mode', async () => {
    const wrapper = shallowMount(LearningAgent)
    await flushPromises()

    wrapper.vm.openChat('今天学什么？')

    expect(routerMocks.push).toHaveBeenCalledWith({
      path: '/student/ai-qa',
      query: { mode: 'agent', question: '今天学什么？' }
    })
  })

  it('loads locally matched content after onboarding', async () => {
    apiMocks.getLearningAgentContext.mockResolvedValue({
      code: 200,
      data: { ...contextResponse.data, onboardingCompleted: true }
    })
    const wrapper = shallowMount(LearningAgent)
    await flushPromises()

    expect(apiMocks.getLearningAgentContentRecommendations).toHaveBeenCalledWith(6)
    expect(wrapper.vm.contentRecommendations).toEqual([])
    expect(apiMocks.getLearningAgentStudyLoop).toHaveBeenCalledOnce()
  })

  it('links a due review back to the article quiz', async () => {
    const wrapper = shallowMount(LearningAgent)
    await flushPromises()
    wrapper.vm.reviewArticle(42)
    expect(routerMocks.push).toHaveBeenCalledWith({
      path: '/article/42', query: { from: 'agent-review', focus: 'quiz' }
    })
  })

  it('lets the learner explicitly ask Xiaoya about a wrong question', async () => {
    const wrapper = shallowMount(LearningAgent)
    await flushPromises()
    wrapper.vm.askAboutWrongQuestion({
      question: 'What does the sentence mean?', userAnswer: 'B', correctAnswer: 'A',
      analysis: 'The context supports A.'
    })
    expect(routerMocks.push).toHaveBeenCalledWith(expect.objectContaining({
      path: '/student/ai-qa',
      query: expect.not.objectContaining({ text: expect.anything() })
    }))
    expect(JSON.parse(sessionStorage.getItem('xiaoya:pending-question-context')).text)
      .toContain('我的答案：B')
  })

  it('keeps external excerpts in the browser until the learner opens chat', async () => {
    const wrapper = shallowMount(LearningAgent)
    await flushPromises()
    wrapper.vm.previewItem = { title: 'Research news', summary: 'An English summary', sourceName: 'Wikipedia' }
    wrapper.vm.askAboutSummary()
    expect(routerMocks.push).toHaveBeenCalledWith(expect.objectContaining({
      path: '/student/ai-qa',
      query: expect.objectContaining({ text: 'An English summary', articleTitle: 'Research news' })
    }))
  })
})
