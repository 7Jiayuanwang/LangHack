import { describe, it, expect, vi, beforeEach } from 'vitest'
import { shallowMount, flushPromises } from '@vue/test-utils'

const { pushMock, elMessage, schoolDataApi } = vi.hoisted(() => ({
  pushMock: vi.fn(),
  elMessage: {
    success: vi.fn(),
    error: vi.fn(),
    warning: vi.fn()
  },
  schoolDataApi: {
    getSchoolDataConfig: vi.fn(),
    updateSchoolDataConfig: vi.fn(),
    testSchoolDataConnection: vi.fn(),
    getSchoolDataConfigStatus: vi.fn(),
    fullSyncSchoolDataFiltered: vi.fn(),
    incrementalSyncSchoolDataFiltered: vi.fn(),
    fullSyncAllSchoolData: vi.fn(),
    incrementalSyncAllSchoolData: vi.fn(),
    chainFetchStudentsByCollege: vi.fn()
  }
}))

vi.mock('vue-router', () => ({
  useRouter: () => ({ push: pushMock })
}))

vi.mock('element-plus', () => ({
  ElMessage: elMessage
}))

vi.mock('@/api/schoolData', () => schoolDataApi)

vi.mock('@/composables/useFeatureToggles', () => ({
  useFeatureToggles: () => ({
    toggles: { value: {} },
    isEnabled: () => true,
    load: vi.fn().mockResolvedValue({}),
    invalidate: vi.fn(),
    isLoaded: () => true,
    startAutoRefresh: vi.fn(),
    stopAutoRefresh: vi.fn(),
    onChange: vi.fn(),
    watchToggle: vi.fn(),
    connectWebSocket: vi.fn(),
    disconnectWebSocket: vi.fn(),
    isWebSocketConnected: () => false
  }),
  invalidateFeatureToggleCache: vi.fn()
}))

import SchoolDataConfig from '@/views/ops/SchoolDataConfig.vue'

describe('SchoolDataConfig quick actions', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    schoolDataApi.getSchoolDataConfig.mockResolvedValue({
      code: 200,
      data: [
        { configKey: 'source', configValue: 'api' },
        { configKey: 'sync_enabled', configValue: 'true' },
        { configKey: 'sync_batch_size', configValue: '500' },
        { configKey: 'sync_timeout_minutes', configValue: '60' }
      ]
    })
    schoolDataApi.getSchoolDataConfigStatus.mockResolvedValue({
      code: 200,
      data: {
        source: 'api',
        authConfigured: true,
        etlConfigured: false,
        missingKeys: [],
        endpoints: []
      }
    })
    schoolDataApi.testSchoolDataConnection.mockResolvedValue({
      code: 200,
      data: { success: true, source: 'api' }
    })
  })

  it('runs quick single data source sync', async () => {
    schoolDataApi.incrementalSyncSchoolDataFiltered.mockResolvedValue({
      code: 200,
      data: {
        totalCount: 10,
        successCount: 10,
        failCount: 0,
        skipCount: 0,
        status: 'SUCCESS',
        paginationComplete: true,
        paginationStopReason: 'LAST_PAGE_LT_PAGE_SIZE',
        paginationPagesFetched: 2,
        paginationLastSkip: 1000,
        paginationPageSize: 1000,
        fetchedRowCount: 1010
      }
    })

    const wrapper = shallowMount(SchoolDataConfig)
    await flushPromises()

    await wrapper.vm.handleQuickSync()
    await flushPromises()

    expect(schoolDataApi.incrementalSyncSchoolDataFiltered).toHaveBeenCalledWith('school_course', {
      triggerType: 'MANUAL',
      filterGroups: []
    })
    expect(wrapper.vm.quickResult.totalCount).toBe(10)
    expect(wrapper.vm.quickResult.paginationComplete).toBe(true)
    expect(wrapper.vm.quickResult.paginationPagesFetched).toBe(2)
    expect(wrapper.vm.quickResult.paginationStopReason).toBe('LAST_PAGE_LT_PAGE_SIZE')
  })

  it('runs quick sync all', async () => {
    schoolDataApi.incrementalSyncAllSchoolData.mockResolvedValue({
      code: 200,
      data: [
        { totalCount: 10, successCount: 10, failCount: 0, skipCount: 0, status: 'SUCCESS' },
        { totalCount: 5, successCount: 4, failCount: 1, skipCount: 0, status: 'SUCCESS' }
      ]
    })

    const wrapper = shallowMount(SchoolDataConfig)
    await flushPromises()

    await wrapper.vm.handleQuickSyncAll('incremental')
    await flushPromises()

    expect(schoolDataApi.incrementalSyncAllSchoolData).toHaveBeenCalledWith({ triggerType: 'MANUAL' })
    expect(wrapper.vm.quickResult.totalCount).toBe(15)
    expect(wrapper.vm.quickResult.successCount).toBe(14)
  })

  it('runs quick chain fetch', async () => {
    schoolDataApi.chainFetchStudentsByCollege.mockResolvedValue({
      code: 200,
      data: { collegeName: '外国语学院', courseCount: 3, studentCount: 60, teacherCount: 5, bindingCount: 120 }
    })

    const wrapper = shallowMount(SchoolDataConfig)
    await flushPromises()

    wrapper.vm.chainFetchForm.collegeName = ' 外国语学院 '
    await wrapper.vm.handleQuickChainFetch()
    await flushPromises()

    expect(schoolDataApi.chainFetchStudentsByCollege).toHaveBeenCalledWith({
      collegeName: '外国语学院',
      matchMode: 'eq',
      persist: true
    })
    expect(wrapper.vm.chainResult.courseCount).toBe(3)
  })

  it('opens browser with local chain result query', async () => {
    const wrapper = shallowMount(SchoolDataConfig)
    await flushPromises()

    wrapper.vm.chainResult = {
      collegeName: '外国语学院',
      studentCount: 60,
      teacherCount: 5,
      bindingCount: 120,
      courseCount: 3
    }

    wrapper.vm.openChainResultInBrowser()

    expect(pushMock).toHaveBeenCalledWith({
      path: '/ops/school-data-browser',
      query: {
        tab: 'school_student',
        collegeName: '外国语学院'
      }
    })
  })
})
