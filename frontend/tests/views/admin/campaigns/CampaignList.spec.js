import { describe, it, expect, beforeEach, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import CampaignList from '@/views/admin/campaigns/CampaignList.vue'
import * as campaignApi from '@/api/campaign'

// Mock API
vi.mock('@/api/campaign', () => ({
  getCampaigns: vi.fn(),
  deleteCampaign: vi.fn(),
  startCampaign: vi.fn(),
  endCampaign: vi.fn()
}))

// Mock router
const mockRouter = {
  push: vi.fn()
}

// Mock ElMessageBox
const mockElMessageBox = {
  confirm: vi.fn()
}

// Mock ElMessage
const mockElMessage = {
  success: vi.fn(),
  error: vi.fn()
}

describe('CampaignList.vue', () => {
  let wrapper

  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  const createWrapper = () => {
    return mount(CampaignList, {
      global: {
        mocks: {
          $router: mockRouter,
          $route: { query: {} }
        },
        stubs: {
          ElTable: {
            template: '<div><slot /></div>'
          },
          ElTableColumn: {
            template: '<div><slot /></div>'
          },
          ElPagination: true,
          ElButton: true,
          ElSelect: true,
          ElOption: true,
          ElInput: true,
          ElTag: true
        }
      }
    })
  }

  it('renders correctly', () => {
    wrapper = createWrapper()
    expect(wrapper.exists()).toBe(true)
  })

  it('loads campaigns on mount', async () => {
    const mockCampaigns = {
      records: [
        {
          id: 1,
          title: '测试活动',
          status: 'ACTIVE',
          enrollmentMode: 'MANUAL',
          startTime: '2024-03-01T00:00:00',
          endTime: '2024-05-31T23:59:59'
        }
      ],
      total: 1,
      current: 1,
      size: 10
    }

    campaignApi.getCampaigns.mockResolvedValue({ data: mockCampaigns })

    wrapper = createWrapper()
    await flushPromises()

    expect(campaignApi.getCampaigns).toHaveBeenCalledWith({
      current: 1,
      size: 10,
      status: '',
      keyword: ''
    })
  })

  it('filters campaigns by status', async () => {
    campaignApi.getCampaigns.mockResolvedValue({
      data: { records: [], total: 0 }
    })

    wrapper = createWrapper()
    await flushPromises()

    // Simulate status filter change
    await wrapper.vm.handleSearch()

    expect(campaignApi.getCampaigns).toHaveBeenCalled()
  })

  it('navigates to create page', async () => {
    wrapper = createWrapper()

    await wrapper.vm.handleCreate()

    expect(mockRouter.push).toHaveBeenCalledWith('/admin/campaigns/create')
  })

  it('navigates to edit page', async () => {
    wrapper = createWrapper()

    await wrapper.vm.handleEdit({ id: 1 })

    expect(mockRouter.push).toHaveBeenCalledWith('/admin/campaigns/1/edit')
  })

  it('navigates to detail page', async () => {
    wrapper = createWrapper()

    await wrapper.vm.handleView({ id: 1 })

    expect(mockRouter.push).toHaveBeenCalledWith('/admin/campaigns/1')
  })

  it('navigates to stats page', async () => {
    wrapper = createWrapper()

    await wrapper.vm.handleStats({ id: 1 })

    expect(mockRouter.push).toHaveBeenCalledWith('/admin/campaigns/1/stats')
  })

  it('starts campaign successfully', async () => {
    mockElMessageBox.confirm.mockResolvedValue(true)
    campaignApi.startCampaign.mockResolvedValue({ data: {} })
    campaignApi.getCampaigns.mockResolvedValue({
      data: { records: [], total: 0 }
    })

    wrapper = createWrapper()
    wrapper.vm.$confirm = mockElMessageBox.confirm
    wrapper.vm.$message = mockElMessage

    await wrapper.vm.handleStart({ id: 1, title: '测试活动' })
    await flushPromises()

    expect(mockElMessageBox.confirm).toHaveBeenCalled()
    expect(campaignApi.startCampaign).toHaveBeenCalledWith(1)
    expect(mockElMessage.success).toHaveBeenCalledWith('活动已启动')
  })

  it('ends campaign successfully', async () => {
    mockElMessageBox.confirm.mockResolvedValue(true)
    campaignApi.endCampaign.mockResolvedValue({ data: {} })
    campaignApi.getCampaigns.mockResolvedValue({
      data: { records: [], total: 0 }
    })

    wrapper = createWrapper()
    wrapper.vm.$confirm = mockElMessageBox.confirm
    wrapper.vm.$message = mockElMessage

    await wrapper.vm.handleEnd({ id: 1, title: '测试活动' })
    await flushPromises()

    expect(mockElMessageBox.confirm).toHaveBeenCalled()
    expect(campaignApi.endCampaign).toHaveBeenCalledWith(1)
    expect(mockElMessage.success).toHaveBeenCalledWith('活动已结束')
  })

  it('deletes campaign successfully', async () => {
    mockElMessageBox.confirm.mockResolvedValue(true)
    campaignApi.deleteCampaign.mockResolvedValue({ data: {} })
    campaignApi.getCampaigns.mockResolvedValue({
      data: { records: [], total: 0 }
    })

    wrapper = createWrapper()
    wrapper.vm.$confirm = mockElMessageBox.confirm
    wrapper.vm.$message = mockElMessage

    await wrapper.vm.handleDelete({ id: 1, title: '测试活动' })
    await flushPromises()

    expect(mockElMessageBox.confirm).toHaveBeenCalled()
    expect(campaignApi.deleteCampaign).toHaveBeenCalledWith(1)
    expect(mockElMessage.success).toHaveBeenCalledWith('删除成功')
  })

  it('handles pagination change', async () => {
    campaignApi.getCampaigns.mockResolvedValue({
      data: { records: [], total: 0 }
    })

    wrapper = createWrapper()
    await flushPromises()

    await wrapper.vm.handlePageChange(2)

    expect(campaignApi.getCampaigns).toHaveBeenCalledWith(
      expect.objectContaining({ current: 2 })
    )
  })

  it('handles status tag display', () => {
    wrapper = createWrapper()

    expect(wrapper.vm.getStatusTag('DRAFT')).toEqual({ type: 'info', label: '草稿' })
    expect(wrapper.vm.getStatusTag('ACTIVE')).toEqual({ type: 'success', label: '进行中' })
    expect(wrapper.vm.getStatusTag('ENDED')).toEqual({ type: 'warning', label: '已结束' })
    expect(wrapper.vm.getStatusTag('ARCHIVED')).toEqual({ type: '', label: '已归档' })
  })

  it('handles enrollment mode display', () => {
    wrapper = createWrapper()

    expect(wrapper.vm.getEnrollmentModeLabel('AUTO')).toBe('自动报名')
    expect(wrapper.vm.getEnrollmentModeLabel('MANUAL')).toBe('学生自主报名')
    expect(wrapper.vm.getEnrollmentModeLabel('INVITE_ONLY')).toBe('仅邀请')
  })
})
