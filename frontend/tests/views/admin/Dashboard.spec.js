import { describe, it, expect, vi, beforeEach } from 'vitest'
import { shallowMount } from '@vue/test-utils'

// Mock fetch globally
const mockFetch = vi.fn()
global.fetch = mockFetch

vi.mock('vue-router', () => ({
  useRouter: () => ({ push: vi.fn() }),
  useRoute: () => ({ params: {}, query: {} })
}))

vi.mock('element-plus', () => ({
  ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn() },
  ElMessageBox: { confirm: vi.fn() }
}))

import Dashboard from '@/views/admin/Dashboard.vue'

describe('Dashboard.vue', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockFetch.mockReset()
  })

  it('has initial stats all set to zero', () => {
    mockFetch.mockResolvedValue({ ok: false })
    const wrapper = shallowMount(Dashboard)
    expect(wrapper.vm.stats.totalUsers).toBe(0)
    expect(wrapper.vm.stats.totalCourses).toBe(0)
    expect(wrapper.vm.stats.totalClasses).toBe(0)
    expect(wrapper.vm.stats.totalWords).toBe(0)
  })

  it('has initial userStats all set to zero', () => {
    mockFetch.mockResolvedValue({ ok: false })
    const wrapper = shallowMount(Dashboard)
    expect(wrapper.vm.userStats.admin).toBe(0)
    expect(wrapper.vm.userStats.teacher).toBe(0)
    expect(wrapper.vm.userStats.student).toBe(0)
  })

  it('has predefined recentActivities with 3 items', () => {
    mockFetch.mockResolvedValue({ ok: false })
    const wrapper = shallowMount(Dashboard)
    expect(wrapper.vm.recentActivities).toHaveLength(3)
    expect(wrapper.vm.recentActivities[0]).toHaveProperty('id')
    expect(wrapper.vm.recentActivities[0]).toHaveProperty('description')
    expect(wrapper.vm.recentActivities[0]).toHaveProperty('time')
  })

  it('loadDashboardData updates stats on successful API response', async () => {
    mockFetch.mockResolvedValue({
      ok: true,
      json: () => Promise.resolve({
        code: 200,
        data: {
          totalUsers: 50,
          totalCourses: 10,
          totalClasses: 5,
          totalWords: 1000,
          totalAdmins: 2,
          totalTeachers: 8,
          totalStudents: 40
        }
      })
    })

    const wrapper = shallowMount(Dashboard)
    await wrapper.vm.loadDashboardData()

    expect(wrapper.vm.stats.totalUsers).toBe(50)
    expect(wrapper.vm.stats.totalCourses).toBe(10)
    expect(wrapper.vm.stats.totalClasses).toBe(5)
    expect(wrapper.vm.stats.totalWords).toBe(1000)
    expect(wrapper.vm.userStats.admin).toBe(2)
    expect(wrapper.vm.userStats.teacher).toBe(8)
    expect(wrapper.vm.userStats.student).toBe(40)
  })

  it('loadDashboardData handles non-ok response gracefully', async () => {
    mockFetch.mockResolvedValue({ ok: false })

    const wrapper = shallowMount(Dashboard)
    await wrapper.vm.loadDashboardData()

    // Stats remain at initial values (0) since non-ok response doesn't update them
    expect(wrapper.vm.stats.totalUsers).toBe(0)
    expect(wrapper.vm.stats.totalCourses).toBe(0)
  })

  it('loadDashboardData handles fetch error and resets stats to zero', async () => {
    mockFetch.mockRejectedValue(new Error('Network error'))

    const wrapper = shallowMount(Dashboard)
    // Set stats to non-zero to verify reset
    wrapper.vm.stats.totalUsers = 99
    wrapper.vm.userStats.admin = 5

    await wrapper.vm.loadDashboardData()

    expect(wrapper.vm.stats.totalUsers).toBe(0)
    expect(wrapper.vm.stats.totalCourses).toBe(0)
    expect(wrapper.vm.stats.totalClasses).toBe(0)
    expect(wrapper.vm.stats.totalWords).toBe(0)
    expect(wrapper.vm.userStats.admin).toBe(0)
    expect(wrapper.vm.userStats.teacher).toBe(0)
    expect(wrapper.vm.userStats.student).toBe(0)
  })

  it('loadDashboardData handles response with non-200 code', async () => {
    mockFetch.mockResolvedValue({
      ok: true,
      json: () => Promise.resolve({ code: 500, data: null })
    })

    const wrapper = shallowMount(Dashboard)
    await wrapper.vm.loadDashboardData()

    // code !== 200, so stats should not be updated
    expect(wrapper.vm.stats.totalUsers).toBe(0)
  })

  it('loadDashboardData handles missing data fields with defaults', async () => {
    mockFetch.mockResolvedValue({
      ok: true,
      json: () => Promise.resolve({
        code: 200,
        data: {
          totalUsers: 10
          // other fields missing
        }
      })
    })

    const wrapper = shallowMount(Dashboard)
    await wrapper.vm.loadDashboardData()

    expect(wrapper.vm.stats.totalUsers).toBe(10)
    expect(wrapper.vm.stats.totalCourses).toBe(0)
    expect(wrapper.vm.stats.totalClasses).toBe(0)
    expect(wrapper.vm.stats.totalWords).toBe(0)
    expect(wrapper.vm.userStats.admin).toBe(0)
    expect(wrapper.vm.userStats.teacher).toBe(0)
    expect(wrapper.vm.userStats.student).toBe(0)
  })

  it('sends Authorization header with localStorage token', async () => {
    localStorage.setItem('token', 'test-jwt-token')
    mockFetch.mockResolvedValue({ ok: false })

    const wrapper = shallowMount(Dashboard)
    await wrapper.vm.loadDashboardData()

    expect(mockFetch).toHaveBeenCalledWith('/api/admin/stats', {
      headers: {
        'Authorization': 'Bearer test-jwt-token'
      }
    })
  })

  it('calls loadDashboardData on mount', () => {
    mockFetch.mockResolvedValue({ ok: false })
    const wrapper = shallowMount(Dashboard)
    // fetch should have been called during mount
    expect(mockFetch).toHaveBeenCalled()
  })

  it('renders the dashboard root element', () => {
    mockFetch.mockResolvedValue({ ok: false })
    const wrapper = shallowMount(Dashboard)
    expect(wrapper.find('.dashboard').exists()).toBe(true)
  })

  it('each recentActivity has a unique id', () => {
    mockFetch.mockResolvedValue({ ok: false })
    const wrapper = shallowMount(Dashboard)
    const ids = wrapper.vm.recentActivities.map(a => a.id)
    expect(new Set(ids).size).toBe(ids.length)
  })
})
