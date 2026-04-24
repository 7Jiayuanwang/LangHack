import { describe, it, expect, vi, beforeEach } from 'vitest'
import { shallowMount, flushPromises } from '@vue/test-utils'

vi.mock('vue-router', () => ({
  useRouter: () => ({ push: vi.fn() }),
  useRoute: () => ({ params: {}, query: {} })
}))

vi.mock('element-plus', () => ({
  ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn() },
  ElMessageBox: { confirm: vi.fn() }
}))

// Mock API functions using vi.hoisted to avoid TDZ error
const {
  mockGetSchoolDataOverview,
  mockGetAdministrativeClasses,
  mockChainFetchStudentsByCollege,
  mockGetFeatureToggles
} = vi.hoisted(() => ({
  mockGetSchoolDataOverview: vi.fn(),
  mockGetAdministrativeClasses: vi.fn(),
  mockChainFetchStudentsByCollege: vi.fn(),
  mockGetFeatureToggles: vi.fn()
}))

vi.mock('@/api/schoolData', () => ({
  getSchoolDataOverview: mockGetSchoolDataOverview,
  getAdministrativeClasses: mockGetAdministrativeClasses,
  getSchoolCourses: vi.fn(),
  getTeachingClasses: vi.fn(),
  getCourseSchedules: vi.fn(),
  getCourseSelections: vi.fn(),
  getLevelExamScores: vi.fn(),
  getTeachingPlans: vi.fn(),
  getStudentSchedules: vi.fn(),
  fullSyncSchoolDataFiltered: vi.fn(),
  incrementalSyncSchoolDataFiltered: vi.fn(),
  chainFetchStudentsByCollege: mockChainFetchStudentsByCollege
}))

vi.mock('@/api/systemConfig', () => ({
  getFeatureToggles: mockGetFeatureToggles
}))

import SchoolDataBrowser from '@/views/ops/SchoolDataBrowser.vue'

describe('SchoolDataBrowser.vue - Chain Fetch Feature', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockGetSchoolDataOverview.mockResolvedValue({ code: 200, data: {} })
    mockGetAdministrativeClasses.mockResolvedValue({ code: 200, data: { records: [], total: 0 } })
    mockGetFeatureToggles.mockResolvedValue({ code: 200, data: {} })
  })

  it('chain fetch card is hidden when feature toggle is disabled', async () => {
    mockGetFeatureToggles.mockResolvedValue({
      code: 200,
      data: { 'ops.school_data_chain_fetch': false }
    })

    const wrapper = shallowMount(SchoolDataBrowser)
    await flushPromises()

    expect(wrapper.find('.chain-fetch-card').exists()).toBe(false)
  })

  it('chain fetch card is visible when feature toggle is enabled', async () => {
    mockGetFeatureToggles.mockResolvedValue({
      code: 200,
      data: { 'ops.school_data_chain_fetch': true }
    })

    const wrapper = shallowMount(SchoolDataBrowser)
    await flushPromises()

    expect(wrapper.find('.chain-fetch-card').exists()).toBe(true)
  })

  it('chain fetch card is visible when feature toggle is string "true"', async () => {
    mockGetFeatureToggles.mockResolvedValue({
      code: 200,
      data: { 'ops.school_data_chain_fetch': 'true' }
    })

    const wrapper = shallowMount(SchoolDataBrowser)
    await flushPromises()

    expect(wrapper.find('.chain-fetch-card').exists()).toBe(true)
  })

  it('handleChainFetch calls API with correct parameters', async () => {
    mockGetFeatureToggles.mockResolvedValue({
      code: 200,
      data: { 'ops.school_data_chain_fetch': true }
    })
    mockChainFetchStudentsByCollege.mockResolvedValue({
      code: 200,
      data: {
        courseCount: 5,
        studentCount: 120,
        teacherCount: 8,
        bindingCount: 600,
        upstreamKcbhSupported: true
      }
    })

    const wrapper = shallowMount(SchoolDataBrowser)
    await flushPromises()

    wrapper.vm.chainCollegeName = '外国语学院'
    wrapper.vm.chainMatchMode = 'eq'
    await wrapper.vm.handleChainFetch()

    expect(mockChainFetchStudentsByCollege).toHaveBeenCalledWith({
      collegeName: '外国语学院',
      matchMode: 'eq',
      persist: true
    })
  })

  it('handleChainFetch displays result summary after success', async () => {
    mockGetFeatureToggles.mockResolvedValue({
      code: 200,
      data: { 'ops.school_data_chain_fetch': true }
    })
    mockChainFetchStudentsByCollege.mockResolvedValue({
      code: 200,
      data: {
        courseCount: 5,
        studentCount: 120,
        teacherCount: 8,
        bindingCount: 600,
        upstreamKcbhSupported: true
      }
    })

    const wrapper = shallowMount(SchoolDataBrowser)
    await flushPromises()

    wrapper.vm.chainCollegeName = '外国语学院'
    await wrapper.vm.handleChainFetch()
    await flushPromises()

    expect(wrapper.vm.chainResult).toEqual({
      courseCount: 5,
      studentCount: 120,
      teacherCount: 8,
      bindingCount: 600,
      upstreamKcbhSupported: true
    })
  })

  it('handleChainFetch shows warning when collegeName is empty', async () => {
    mockGetFeatureToggles.mockResolvedValue({
      code: 200,
      data: { 'ops.school_data_chain_fetch': true }
    })

    const wrapper = shallowMount(SchoolDataBrowser)
    await flushPromises()

    wrapper.vm.chainCollegeName = ''
    await wrapper.vm.handleChainFetch()

    expect(mockChainFetchStudentsByCollege).not.toHaveBeenCalled()
  })

  it('handleChainFetch trims whitespace from collegeName', async () => {
    mockGetFeatureToggles.mockResolvedValue({
      code: 200,
      data: { 'ops.school_data_chain_fetch': true }
    })
    mockChainFetchStudentsByCollege.mockResolvedValue({
      code: 200,
      data: { courseCount: 0, studentCount: 0, teacherCount: 0, bindingCount: 0 }
    })

    const wrapper = shallowMount(SchoolDataBrowser)
    await flushPromises()

    wrapper.vm.chainCollegeName = '  外国语学院  '
    await wrapper.vm.handleChainFetch()

    expect(mockChainFetchStudentsByCollege).toHaveBeenCalledWith({
      collegeName: '外国语学院',
      matchMode: 'eq',
      persist: true
    })
  })

  it('handleChainFetch handles API error gracefully', async () => {
    mockGetFeatureToggles.mockResolvedValue({
      code: 200,
      data: { 'ops.school_data_chain_fetch': true }
    })
    mockChainFetchStudentsByCollege.mockRejectedValue(new Error('Network error'))

    const wrapper = shallowMount(SchoolDataBrowser)
    await flushPromises()

    wrapper.vm.chainCollegeName = '外国语学院'
    await wrapper.vm.handleChainFetch()
    await flushPromises()

    expect(wrapper.vm.chainResult).toBeNull()
    expect(wrapper.vm.chainFetching).toBe(false)
  })

  it('handleChainFetch handles non-200 response code', async () => {
    mockGetFeatureToggles.mockResolvedValue({
      code: 200,
      data: { 'ops.school_data_chain_fetch': true }
    })
    mockChainFetchStudentsByCollege.mockResolvedValue({
      code: 500,
      message: '服务器错误'
    })

    const wrapper = shallowMount(SchoolDataBrowser)
    await flushPromises()

    wrapper.vm.chainCollegeName = '外国语学院'
    await wrapper.vm.handleChainFetch()
    await flushPromises()

    expect(wrapper.vm.chainResult).toBeNull()
  })

  it('chainFetching flag is set during API call', async () => {
    mockGetFeatureToggles.mockResolvedValue({
      code: 200,
      data: { 'ops.school_data_chain_fetch': true }
    })

    let resolveChainFetch
    const chainFetchPromise = new Promise(resolve => { resolveChainFetch = resolve })
    mockChainFetchStudentsByCollege.mockReturnValue(chainFetchPromise)

    const wrapper = shallowMount(SchoolDataBrowser)
    await flushPromises()

    wrapper.vm.chainCollegeName = '外国语学院'
    const fetchPromise = wrapper.vm.handleChainFetch()

    expect(wrapper.vm.chainFetching).toBe(true)

    resolveChainFetch({ code: 200, data: { courseCount: 0, studentCount: 0, teacherCount: 0, bindingCount: 0 } })
    await fetchPromise
    await flushPromises()

    expect(wrapper.vm.chainFetching).toBe(false)
  })

  it('default chainMatchMode is "eq"', async () => {
    mockGetFeatureToggles.mockResolvedValue({
      code: 200,
      data: { 'ops.school_data_chain_fetch': true }
    })

    const wrapper = shallowMount(SchoolDataBrowser)
    await flushPromises()

    expect(wrapper.vm.chainMatchMode).toBe('eq')
  })

  it('default chainCollegeName is "外国语学院"', async () => {
    mockGetFeatureToggles.mockResolvedValue({
      code: 200,
      data: { 'ops.school_data_chain_fetch': true }
    })

    const wrapper = shallowMount(SchoolDataBrowser)
    await flushPromises()

    expect(wrapper.vm.chainCollegeName).toBe('外国语学院')
  })

  it('loadFeatureToggles is called on mount', async () => {
    const wrapper = shallowMount(SchoolDataBrowser)
    await flushPromises()

    expect(mockGetFeatureToggles).toHaveBeenCalled()
  })

  it('handleChainFetch reloads overview after success', async () => {
    mockGetFeatureToggles.mockResolvedValue({
      code: 200,
      data: { 'ops.school_data_chain_fetch': true }
    })
    mockChainFetchStudentsByCollege.mockResolvedValue({
      code: 200,
      data: { courseCount: 5, studentCount: 120, teacherCount: 8, bindingCount: 600 }
    })

    const wrapper = shallowMount(SchoolDataBrowser)
    await flushPromises()

    mockGetSchoolDataOverview.mockClear()
    wrapper.vm.chainCollegeName = '外国语学院'
    await wrapper.vm.handleChainFetch()
    await flushPromises()

    expect(mockGetSchoolDataOverview).toHaveBeenCalled()
  })
})
