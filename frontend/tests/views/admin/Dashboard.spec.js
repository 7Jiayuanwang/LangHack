import { describe, it, expect, vi, beforeEach } from 'vitest'
import { shallowMount } from '@vue/test-utils'

vi.mock('vue-router', () => ({
  useRouter: () => ({ push: vi.fn() }),
  useRoute: () => ({ params: {}, query: {} })
}))

vi.mock('element-plus', () => ({
  ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn() },
  ElMessageBox: { confirm: vi.fn() }
}))

// Mock request instance used by Dashboard (must use vi.hoisted to avoid TDZ error)
const { mockRequest } = vi.hoisted(() => ({ mockRequest: vi.fn() }))
vi.mock('@/utils/request', () => ({ default: mockRequest }))

import Dashboard from '@/views/admin/Dashboard.vue'

describe('Dashboard.vue', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockRequest.mockReset()
  })

  it('has initial stats all set to zero', () => {
    mockRequest.mockResolvedValue({ code: 200, data: {} })
    const wrapper = shallowMount(Dashboard)
    expect(wrapper.vm.stats.totalUsers).toBe(0)
    expect(wrapper.vm.stats.totalCourses).toBe(0)
    expect(wrapper.vm.stats.totalClasses).toBe(0)
    expect(wrapper.vm.stats.totalWords).toBe(0)
  })

  it('has initial userStats all set to zero', () => {
    mockRequest.mockResolvedValue({ code: 200, data: {} })
    const wrapper = shallowMount(Dashboard)
    expect(wrapper.vm.userStats.admin).toBe(0)
    expect(wrapper.vm.userStats.teacher).toBe(0)
    expect(wrapper.vm.userStats.student).toBe(0)
  })

  it('has recentActivities initialized as empty array', () => {
    mockRequest.mockResolvedValue({ code: 200, data: {} })
    const wrapper = shallowMount(Dashboard)
    expect(wrapper.vm.recentActivities).toHaveLength(0)
  })

  it('loadDashboardData updates stats on successful API response', async () => {
    mockRequest.mockResolvedValue({
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

  it('loadDashboardData handles non-200 code gracefully', async () => {
    mockRequest.mockResolvedValue({ code: 500, data: null })

    const wrapper = shallowMount(Dashboard)
    await wrapper.vm.loadDashboardData()

    expect(wrapper.vm.stats.totalUsers).toBe(0)
    expect(wrapper.vm.stats.totalCourses).toBe(0)
  })

  it('loadDashboardData handles request error and keeps stats unchanged', async () => {
    mockRequest.mockRejectedValue({ code: 500, message: 'Network error' })

    const wrapper = shallowMount(Dashboard)
    wrapper.vm.stats.totalUsers = 99
    wrapper.vm.userStats.admin = 5

    await wrapper.vm.loadDashboardData()

    // On error, catch block runs but doesn't reset stats (correct behavior)
    expect(wrapper.vm.stats.totalUsers).toBe(99)
  })

  it('loadDashboardData handles missing data fields with defaults', async () => {
    mockRequest.mockResolvedValue({
      code: 200,
      data: {
        totalUsers: 10
        // other fields missing
      }
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

  it('calls request with correct url on loadDashboardData', async () => {
    mockRequest.mockResolvedValue({ code: 200, data: {} })

    const wrapper = shallowMount(Dashboard)
    await wrapper.vm.loadDashboardData()

    expect(mockRequest).toHaveBeenCalledWith({ url: '/api/admin/stats', method: 'get' })
  })

  it('calls loadDashboardData on mount', async () => {
    mockRequest.mockResolvedValue({ code: 200, data: {} })
    shallowMount(Dashboard)
    // request should have been called during mount (async, but vi.fn() is registered immediately)
    expect(mockRequest).toHaveBeenCalled()
  })

  it('renders the dashboard root element', () => {
    mockRequest.mockResolvedValue({ code: 200, data: {} })
    const wrapper = shallowMount(Dashboard)
    expect(wrapper.find('.dashboard-container').exists()).toBe(true)
  })

  it('recentActivities is an array', () => {
    mockRequest.mockResolvedValue({ code: 200, data: {} })
    const wrapper = shallowMount(Dashboard)
    expect(Array.isArray(wrapper.vm.recentActivities)).toBe(true)
  })
})
