import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, shallowMount } from '@vue/test-utils'

const getClassLearningOverview = vi.hoisted(() => vi.fn())
const routerPush = vi.hoisted(() => vi.fn())

vi.mock('@/api/teacher', () => ({ getClassLearningOverview }))
vi.mock('vue-router', () => ({
  useRoute: () => ({ params: { classId: '8' } }),
  useRouter: () => ({ push: routerPush })
}))
vi.mock('vue-echarts', () => ({ default: { name: 'VChart', render: () => null } }))

import ClassLearningOverview from '@/views/teacher/ClassLearningOverview.vue'

const overview = {
  classId: 8,
  className: '学术英语一班',
  windowStart: '2026-09-01',
  windowEnd: '2026-09-19',
  totalStudents: 3,
  activeStudents: 2,
  readingStudents: 2,
  quizStudents: 1,
  noRecordStudents: 1,
  readingSessions: 4,
  practiceAttempts: 2,
  answeredQuestions: 5,
  correctRate: 80,
  weeklyActivity: [
    { weekStart: '2026-09-07', readingStudents: 1, quizStudents: 0, correctRate: null },
    { weekStart: '2026-09-14', readingStudents: 2, quizStudents: 1, correctRate: 80 }
  ],
  accuracyDistribution: [
    { key: 'NO_QUIZ', label: '未答题', studentCount: 2 },
    { key: 'AT_LEAST_80', label: '80%及以上', studentCount: 1 }
  ],
  studentProgress: [
    { studentId: 1, username: 's1', displayName: '小李', readingSessions: 2, textsRead: 1,
      practiceAttempts: 2, correctRate: 80, lastActivityAt: '2026-09-19T12:00:00' }
  ]
}

describe('Teacher class learning overview', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    getClassLearningOverview.mockResolvedValue({ code: 200, data: structuredClone(overview) })
  })

  it('loads only the selected class and shows evidence-based metrics', async () => {
    const wrapper = shallowMount(ClassLearningOverview)
    await flushPromises()

    expect(getClassLearningOverview).toHaveBeenCalledWith(8)
    expect(wrapper.text()).toContain('在册学生')
    expect(wrapper.text()).toContain('阅读题正确率')
    expect(wrapper.text()).toContain('不读取学生与小芽的对话')
    expect(wrapper.vm.activePercent).toBe(67)
    expect(wrapper.vm.participationOption.series[0].data).toEqual([1, 2])
    expect(wrapper.vm.accuracyTrendOption.series[0].data).toEqual([null, 80])
    expect(wrapper.vm.pagedStudents[0].displayName).toBe('小李')
  })

  it('keeps the empty state separate from a zero-percent score', async () => {
    getClassLearningOverview.mockResolvedValue({ code: 200, data: {
      ...overview, totalStudents: 0, activeStudents: 0, studentProgress: [], correctRate: null
    } })
    const wrapper = shallowMount(ClassLearningOverview)
    await flushPromises()

    expect(wrapper.find('.metric-grid').exists()).toBe(false)
    expect(wrapper.vm.overview.correctRate).toBeNull()
  })

  it('does not render stale class data after an access error', async () => {
    getClassLearningOverview.mockRejectedValue({ code: 403 })
    const wrapper = shallowMount(ClassLearningOverview)
    await flushPromises()

    expect(wrapper.vm.overview).toBeNull()
    expect(wrapper.vm.error).toBe('只能查看自己负责的教学班')
    expect(wrapper.find('.metric-grid').exists()).toBe(false)
  })
})
