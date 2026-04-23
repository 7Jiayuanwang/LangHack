import { describe, it, expect, vi, beforeEach } from 'vitest'
import { shallowMount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'

vi.mock('vue-router', () => ({
  useRouter: () => ({ push: vi.fn() }),
  useRoute: () => ({ params: {}, query: {} })
}))

vi.mock('element-plus', () => ({
  ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn() }
}))

vi.mock('@/api/student', () => ({
  getStudentDashboard: vi.fn().mockResolvedValue({ data: {} }),
  getTodayLearningStats: vi.fn().mockResolvedValue({ data: {} }),
  getVocabularyStats: vi.fn().mockResolvedValue({ data: {} })
}))

import Dashboard from '@/views/student/Dashboard.vue'

describe('Student Dashboard.vue', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    setActivePinia(createPinia())
  })

  const createWrapper = () => shallowMount(Dashboard)

  it('mounts without error', () => {
    const wrapper = createWrapper()
    expect(wrapper.exists()).toBe(true)
  })

  it('formatTime returns empty string for falsy value', () => {
    const wrapper = createWrapper()
    expect(wrapper.vm.formatTime(0)).toBe('')
    expect(wrapper.vm.formatTime(null)).toBe('')
    expect(wrapper.vm.formatTime('')).toBe('')
  })

  it('formatTime returns locale string for valid timestamp', () => {
    const wrapper = createWrapper()
    const result = wrapper.vm.formatTime('2024-06-15T10:30:00')
    expect(typeof result).toBe('string')
    expect(result).toBeTruthy()
  })

  it('getUrgencyClass returns overdue for past deadline', () => {
    const wrapper = createWrapper()
    const pastDate = new Date(Date.now() - 86400000).toISOString()
    expect(wrapper.vm.getUrgencyClass(pastDate)).toBe('overdue')
    expect(wrapper.vm.getUrgencyText(pastDate)).toBe('已逾期')
  })

  it('getUrgencyClass returns soon for deadline 24-72h away', () => {
    const wrapper = createWrapper()
    const soonDate = new Date(Date.now() + 48 * 3600000).toISOString()
    expect(wrapper.vm.getUrgencyClass(soonDate)).toBe('soon')
    expect(wrapper.vm.getUrgencyText(soonDate)).toBe('尽快')
  })

  it('getUrgencyClass returns normal for deadline more than 72h away', () => {
    const wrapper = createWrapper()
    const farDate = new Date(Date.now() + 7 * 86400000).toISOString()
    expect(wrapper.vm.getUrgencyClass(farDate)).toBe('normal')
    expect(wrapper.vm.getUrgencyText(farDate)).toBe('正常')
  })

  it('urgency helpers handle null deadline', () => {
    const wrapper = createWrapper()
    expect(wrapper.vm.getUrgencyClass(null)).toBe('normal')
    expect(wrapper.vm.getUrgencyText(null)).toBe('无截止时间')
  })

  it('getCourseProgress handles number progress 0-1 range', () => {
    const wrapper = createWrapper()
    expect(wrapper.vm.getCourseProgress({ progress: 0.5 })).toBe(50)
  })

  it('getCourseProgress calculates from units', () => {
    const wrapper = createWrapper()
    expect(wrapper.vm.getCourseProgress({ learnedUnits: 3, totalUnits: 10 })).toBe(30)
  })

  it('getCourseProgress returns 0 for invalid data', () => {
    const wrapper = createWrapper()
    expect(wrapper.vm.getCourseProgress({})).toBe(0)
  })

  it('formatScore handles valid number', () => {
    const wrapper = createWrapper()
    expect(wrapper.vm.formatScore(85.5)).toBe('85.5')
  })

  it('formatScore returns 0 for NaN string', () => {
    const wrapper = createWrapper()
    expect(wrapper.vm.formatScore('invalid')).toBe('0')
  })

  it('formatScore returns 0 for zero', () => {
    const wrapper = createWrapper()
    expect(wrapper.vm.formatScore(0)).toBe('0')
  })
})
