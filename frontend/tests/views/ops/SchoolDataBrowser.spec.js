import { describe, it, expect, vi, beforeEach } from 'vitest'
import { shallowMount, flushPromises } from '@vue/test-utils'

vi.mock('vue-router', () => ({
  useRouter: () => ({ push: vi.fn() }),
  useRoute: () => ({ params: {}, query: {} })
}))

vi.mock('element-plus', () => ({
  ElMessage: {
    success: vi.fn(),
    error: vi.fn(),
    warning: vi.fn(),
    info: vi.fn()
  },
  ElMessageBox: { confirm: vi.fn() }
}))

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

const {
  mockGetSchoolDataOverview,
  mockGetSchoolDataFilterFields,
  mockQuerySchoolData,
  mockChainFetchStudentsByCollege
} = vi.hoisted(() => ({
  mockGetSchoolDataOverview: vi.fn(),
  mockGetSchoolDataFilterFields: vi.fn(),
  mockQuerySchoolData: vi.fn(),
  mockChainFetchStudentsByCollege: vi.fn()
}))

vi.mock('@/api/schoolData', () => ({
  getSchoolDataOverview: mockGetSchoolDataOverview,
  getSchoolDataFilterFields: mockGetSchoolDataFilterFields,
  querySchoolData: mockQuerySchoolData,
  fullSyncSchoolDataFiltered: vi.fn(),
  incrementalSyncSchoolDataFiltered: vi.fn(),
  chainFetchStudentsByCollege: mockChainFetchStudentsByCollege
}))

import SchoolDataBrowser from '@/views/ops/SchoolDataBrowser.vue'

describe('SchoolDataBrowser.vue', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockGetSchoolDataOverview.mockResolvedValue({ code: 200, data: {} })
    mockGetSchoolDataFilterFields.mockResolvedValue({
      code: 200,
      data: [
        {
          field: 'collegeName',
          label: '学院',
          operators: ['contains', 'eq'],
          capabilities: { listQuery: true }
        }
      ]
    })
    mockQuerySchoolData.mockResolvedValue({
      code: 200,
      data: { records: [], total: 0 }
    })
    mockChainFetchStudentsByCollege.mockResolvedValue({
      code: 200,
      data: {
        collegeName: '外国语学院',
        courseCount: 5,
        studentCount: 120,
        teacherCount: 8,
        bindingCount: 600,
        upstreamKcbhSupported: true
      }
    })
  })

  it('mounts and loads filter metadata plus the first page', async () => {
    const wrapper = shallowMount(SchoolDataBrowser)
    await flushPromises()

    expect(wrapper.exists()).toBe(true)
    expect(mockGetSchoolDataFilterFields).toHaveBeenCalledWith('administrative_class')
    expect(mockQuerySchoolData).toHaveBeenCalledWith('administrative_class', {
      page: 1,
      size: 20,
      filterGroups: []
    })
  })

  it('chain fetch card remains visible in the ops browser', async () => {
    const wrapper = shallowMount(SchoolDataBrowser)
    await flushPromises()

    expect(wrapper.find('.chain-fetch-card').exists()).toBe(true)
  })

  it('handleChainFetch trims collegeName, refreshes overview, and switches to local student results', async () => {
    const wrapper = shallowMount(SchoolDataBrowser)
    await flushPromises()

    mockGetSchoolDataOverview.mockClear()
    mockGetSchoolDataFilterFields.mockClear()
    mockQuerySchoolData.mockClear()
    wrapper.vm.chainCollegeName = '  外国语学院  '
    wrapper.vm.chainMatchMode = 'eq'
    await wrapper.vm.handleChainFetch()

    expect(mockChainFetchStudentsByCollege).toHaveBeenCalledWith({
      collegeName: '外国语学院',
      matchMode: 'eq',
      persist: true
    })
    expect(mockGetSchoolDataOverview).toHaveBeenCalledTimes(1)
    expect(mockGetSchoolDataFilterFields).toHaveBeenCalledWith('school_student')
    expect(mockQuerySchoolData).toHaveBeenCalledWith('school_student', {
      page: 1,
      size: 20,
      filterGroups: [
        {
          relation: 'AND',
          conditions: [
            {
              field: 'collegeName',
              label: '学院名称',
              operator: 'eq',
              value: '外国语学院'
            }
          ]
        }
      ]
    })
    expect(wrapper.vm.activeTab).toBe('school_student')
    expect(wrapper.vm.chainResult).toEqual({
      collegeName: '外国语学院',
      courseCount: 5,
      studentCount: 120,
      teacherCount: 8,
      bindingCount: 600,
      upstreamKcbhSupported: true
    })
  })

  it('saveFilters stores valid conditions and reloads the list through querySchoolData', async () => {
    const wrapper = shallowMount(SchoolDataBrowser)
    await flushPromises()

    mockQuerySchoolData.mockClear()
    wrapper.vm.filterGroups = [
      {
        relation: 'AND',
        conditions: [
          {
            field: 'collegeName',
            label: '学院',
            operator: 'contains',
            value: '外国语学院'
          }
        ]
      }
    ]

    wrapper.vm.saveFilters()
    await flushPromises()

    expect(wrapper.vm.currentFilters).toEqual([
      {
        relation: 'AND',
        conditions: [
          {
            field: 'collegeName',
            label: '学院',
            operator: 'contains',
            value: '外国语学院'
          }
        ]
      }
    ])
    expect(mockQuerySchoolData).toHaveBeenCalledWith('administrative_class', {
      page: 1,
      size: 20,
      filterGroups: [
        {
          relation: 'AND',
          conditions: [
            {
              field: 'collegeName',
              label: '学院',
              operator: 'contains',
              value: '外国语学院'
            }
          ]
        }
      ]
    })
  })
})
