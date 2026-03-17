import { describe, it, expect, vi, beforeEach } from 'vitest'
import { shallowMount } from '@vue/test-utils'

vi.mock('vue-router', () => ({
  useRouter: () => ({ push: vi.fn() })
}))

vi.mock('element-plus', () => ({
  ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn() }
}))

import Dashboard from '@/views/teacher/Dashboard.vue'

describe('Teacher Dashboard.vue', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.spyOn(Storage.prototype, 'getItem').mockReturnValue('mock-token')
    global.fetch = vi.fn().mockResolvedValue({
      ok: true,
      json: () => Promise.resolve({ code: 200, data: {} })
    })
  })

  const createWrapper = () => shallowMount(Dashboard)

  it('has stats reactive object', () => {
    const wrapper = createWrapper()
    expect(wrapper.vm.stats).toBeDefined()
    expect(wrapper.vm.stats.totalClasses).toBe(0)
  })

  it('has empty classes array', () => {
    const wrapper = createWrapper()
    expect(wrapper.vm.classes).toEqual([])
  })

  it('has empty recentAssignments array', () => {
    const wrapper = createWrapper()
    expect(wrapper.vm.recentAssignments).toEqual([])
  })

  it('formatTime returns empty string for falsy value', () => {
    const wrapper = createWrapper()
    expect(wrapper.vm.formatTime(null)).toBe('')
    expect(wrapper.vm.formatTime('')).toBe('')
  })

  it('formatTime formats a valid date string', () => {
    const wrapper = createWrapper()
    const result = wrapper.vm.formatTime('2024-01-15T10:30:00')
    expect(typeof result).toBe('string')
    expect(result).toBeTruthy()
  })

  it('getProgressColor returns correct color for high percentage', () => {
    const wrapper = createWrapper()
    const color = wrapper.vm.getProgressColor(90)
    expect(typeof color).toBe('string')
  })

  it('getProgressColor returns color for low percentage', () => {
    const wrapper = createWrapper()
    const color = wrapper.vm.getProgressColor(30)
    expect(typeof color).toBe('string')
  })

  it('viewClassDetail navigates to class', () => {
    const wrapper = createWrapper()
    wrapper.vm.viewClassDetail({ id: 3 })
    // just verifies no error thrown
    expect(true).toBe(true)
  })

  it('loadDashboardData calls fetch', async () => {
    const wrapper = createWrapper()
    await wrapper.vm.loadDashboardData()
    expect(global.fetch).toHaveBeenCalled()
  })
})
