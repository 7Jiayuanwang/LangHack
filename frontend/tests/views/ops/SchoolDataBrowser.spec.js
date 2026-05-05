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

const {
  mockGetSchoolDataOverview,
  mockGetSchoolDataFilterFields,
  mockQuerySchoolData,
  mockChainFetchStudentsByCollege,
  mockGetFeatureToggles
} = vi.hoisted(() => ({
  mockGetSchoolDataOverview: vi.fn(),
  mockGetSchoolDataFilterFields: vi.fn(),
  mockQuerySchoolData: vi.fn(),
  mockChainFetchStudentsByCollege: vi.fn(),
  mockGetFeatureToggles: vi.fn()
}))

vi.mock('@/api/schoolData', () => ({
  getSchoolDataOverview: mockGetSchoolDataOverview,
  getSchoolDataFilterFields: mockGetSchoolDataFilterFields,
  querySchoolData: mockQuerySchoolData,
  fullSyncSchoolDataFiltered: vi.fn(),
  incrementalSyncSchoolDataFiltered: vi.fn(),
  chainFetchStudentsByCollege: mockChainFetchStudentsByCollege
}))

vi.mock('@/api/systemConfig', () => ({
  getFeatureToggles: mockGetFeatureToggles
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
        courseCount: 5,
        studentCount: 120,
        teacherCount: 8,
        bindingCount: 600,
        upstreamKcbhSupported: true
      }
    })
    mockGetFeatureToggles.mockResolvedValue({ code: 200, data: {} })
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

  it('handleChainFetch trims collegeName and calls the current API', async () => {
    mockGetFeatureToggles.mockResolvedValue({
      code: 200,
      data: { 'ops.school_data_chain_fetch': true }
    })

    const wrapper = shallowMount(SchoolDataBrowser)
    await flushPromises()

    wrapper.vm.chainCollegeName = '  外国语学院  '
    wrapper.vm.chainMatchMode = 'eq'
    await wrapper.vm.handleChainFetch()

    expect(mockChainFetchStudentsByCollege).toHaveBeenCalledWith({
      collegeName: '外国语学院',
      matchMode: 'eq',
      persist: true
    })
    expect(wrapper.vm.chainResult).toEqual({
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
