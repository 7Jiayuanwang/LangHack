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
  getXiaoyaGrowth: vi.fn(),
  addXiaoyaExternalLearning: vi.fn(),
  deleteXiaoyaExternalLearning: vi.fn(),
  listXiaoyaExternalLearning: vi.fn(),
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
    apiMocks.getXiaoyaGrowth.mockResolvedValue({
      code: 200,
      data: { stage: 'SEED', stageLabel: '刚发芽的小芽', growthPoints: 0,
        nextStageAt: 10, meaningfulDays7d: 0, completedArticles: 0,
        practicedArticles: 0, externalDays: 0, todayExternalCount: 0,
        recentExternalLearning: [], encouragement: '从一句英文收获开始。' }
    })
    apiMocks.listXiaoyaExternalLearning.mockResolvedValue({ code: 200, data: { page: 1, total: 0, items: [] } })
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

  it('records an external English takeaway only after explicit submission', async () => {
    apiMocks.getLearningAgentContext.mockResolvedValue({
      code: 200, data: { ...contextResponse.data, onboardingCompleted: true }
    })
    apiMocks.addXiaoyaExternalLearning.mockResolvedValue({
      code: 200,
      data: { stage: 'SPROUT', growthPoints: 10, todayExternalCount: 1, recentExternalLearning: [] }
    })
    const wrapper = shallowMount(LearningAgent)
    await flushPromises()

    expect(apiMocks.addXiaoyaExternalLearning).not.toHaveBeenCalled()
    wrapper.vm.externalForm.sourceType = 'VIDEO'
    wrapper.vm.externalForm.sourceTitle = 'English science video'
    wrapper.vm.externalForm.takeawayEn = 'I learned how plants use sunlight to grow.'
    await wrapper.vm.submitExternalLearning()

    expect(apiMocks.addXiaoyaExternalLearning).toHaveBeenCalledWith({
      sourceType: 'VIDEO', sourceTitle: 'English science video',
      takeawayEn: 'I learned how plants use sunlight to grow.'
    })
    expect(wrapper.vm.growth.stage).toBe('SPROUT')
    expect(wrapper.vm.externalForm.takeawayEn).toBe('')
  })

  it('paginates private external notes for individual deletion', async () => {
    apiMocks.getLearningAgentContext.mockResolvedValue({
      code: 200, data: { ...contextResponse.data, onboardingCompleted: true }
    })
    apiMocks.listXiaoyaExternalLearning.mockResolvedValue({
      code: 200,
      data: { page: 1, total: 12, items: [{ id: 1, takeawayEn: 'I found a useful idea.' }] }
    })
    const wrapper = shallowMount(LearningAgent)
    await flushPromises()

    await wrapper.vm.loadMoreExternalLearning()

    expect(apiMocks.listXiaoyaExternalLearning).toHaveBeenCalledWith(1)
    expect(wrapper.vm.historyExpanded).toBe(true)
    expect(wrapper.vm.historyTotal).toBe(12)
  })
})
