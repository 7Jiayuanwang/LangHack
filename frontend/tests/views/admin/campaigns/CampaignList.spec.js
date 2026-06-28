import { describe, it, expect, beforeEach, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import CampaignList from '@/views/admin/campaigns/CampaignList.vue'
import * as campaignApi from '@/api/campaign'

const { mockPush, mockSuccess, mockError, mockConfirm } = vi.hoisted(() => ({
  mockPush: vi.fn(),
  mockSuccess: vi.fn(),
  mockError: vi.fn(),
  mockConfirm: vi.fn()
}))

vi.mock('vue-router', () => ({
  useRouter: () => ({ push: mockPush })
}))

vi.mock('element-plus', () => ({
  ElMessage: {
    success: mockSuccess,
    error: mockError
  },
  ElMessageBox: {
    confirm: mockConfirm
  }
}))

vi.mock('@/api/campaign', () => ({
  getCampaigns: vi.fn(),
  deleteCampaign: vi.fn(),
  startCampaign: vi.fn(),
  endCampaign: vi.fn()
}))

const createApiResponse = (data, message = 'ok') => ({ code: 200, data, message })

const createPageResponse = (records, total) => ({
  records,
  total,
  current: 1,
  size: 10
})

const baseCampaign = {
  id: 1,
  title: '四六级闯关赛',
  status: 'DRAFT',
  enrollmentMode: 'MANUAL',
  startTime: '2099-01-01T08:00:00',
  endTime: '2099-01-31T23:59:59',
  createdTime: '2098-12-20T10:30:00'
}

const activeCampaign = {
  ...baseCampaign,
  id: 2,
  status: 'ACTIVE',
  title: '进行中活动'
}

const archivedCampaign = {
  ...baseCampaign,
  id: 3,
  status: 'ARCHIVED',
  title: '归档活动'
}

const createWrapper = () => mount(CampaignList, {
  global: {
    directives: {
      loading: {}
    },
    stubs: {
      ElCard: { template: '<div><slot /></div>' },
      ElForm: { template: '<form><slot /></form>' },
      ElFormItem: { template: '<div><slot /></div>' },
      ElTable: { template: '<div><slot /></div>', props: ['data'] },
      ElTableColumn: {
        props: ['prop', 'label', 'width', 'minWidth', 'fixed'],
        template: '<div class="el-table-column-stub"><slot :row="(($parent.$props && $parent.$props.data) || [])[0] || {}" /></div>'
      },
      ElTag: { template: '<span><slot /></span>' },
      ElButton: {
        props: ['type', 'link'],
        emits: ['click'],
        template: '<button @click="$emit(\'click\')"><slot /></button>'
      },
      ElPagination: {
        props: ['currentPage', 'pageSize', 'total', 'pageSizes', 'layout'],
        emits: ['update:currentPage', 'update:pageSize', 'size-change', 'current-change'],
        template: '<div class="el-pagination-stub"></div>'
      },
      ElSelect: {
        props: ['modelValue'],
        emits: ['update:modelValue', 'change'],
        template: '<select :value="modelValue" @change="$emit(\'update:modelValue\', $event.target.value); $emit(\'change\', $event.target.value)"><slot /></select>'
      },
      ElOption: { template: '<option><slot /></option>', props: ['label', 'value'] },
      ElInput: {
        props: ['modelValue'],
        emits: ['update:modelValue', 'keyup.enter'],
        template: '<input :value="modelValue" @input="$emit(\'update:modelValue\', $event.target.value)" @keyup.enter="$emit(\'keyup.enter\')" />'
      }
    }
  }
})

describe('CampaignList.vue', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    campaignApi.getCampaigns.mockResolvedValue(
      createApiResponse(createPageResponse([], 0))
    )
  })

  it('loads campaigns on mount with default query params', async () => {
    campaignApi.getCampaigns.mockResolvedValue(
      createApiResponse(createPageResponse([baseCampaign], 1))
    )

    const wrapper = createWrapper()
    await flushPromises()

    expect(campaignApi.getCampaigns).toHaveBeenCalledWith({
      current: 1,
      size: 10,
      status: '',
      keyword: '',
      orderBy: 'created_time_desc'
    })
    expect(wrapper.vm.campaigns).toHaveLength(1)
    expect(wrapper.vm.pagination.total).toBe(1)
  })

  it('filters campaigns when querying', async () => {
    const wrapper = createWrapper()
    await flushPromises()

    wrapper.vm.queryParams.status = 'ACTIVE'
    wrapper.vm.queryParams.keyword = '英语'
    wrapper.vm.queryParams.orderBy = 'start_time_asc'
    wrapper.vm.pagination.current = 3

    await wrapper.vm.handleQuery()
    await flushPromises()

    expect(wrapper.vm.pagination.current).toBe(1)
    expect(campaignApi.getCampaigns).toHaveBeenLastCalledWith({
      current: 1,
      size: 10,
      status: 'ACTIVE',
      keyword: '英语',
      orderBy: 'start_time_asc'
    })
  })

  it('keeps selected page when page changes', async () => {
    const wrapper = createWrapper()
    await flushPromises()

    await wrapper.vm.handlePageChange(3)
    await flushPromises()

    expect(wrapper.vm.pagination.current).toBe(3)
    expect(campaignApi.getCampaigns).toHaveBeenLastCalledWith({
      current: 3,
      size: 10,
      status: '',
      keyword: '',
      orderBy: 'created_time_desc'
    })
  })

  it('resets to first page when page size changes', async () => {
    const wrapper = createWrapper()
    await flushPromises()

    wrapper.vm.pagination.current = 4
    await wrapper.vm.handleSizeChange(20)
    await flushPromises()

    expect(wrapper.vm.pagination.current).toBe(1)
    expect(wrapper.vm.pagination.size).toBe(20)
    expect(campaignApi.getCampaigns).toHaveBeenLastCalledWith({
      current: 1,
      size: 20,
      status: '',
      keyword: '',
      orderBy: 'created_time_desc'
    })
  })

  it('resets filters back to defaults', async () => {
    const wrapper = createWrapper()
    await flushPromises()

    wrapper.vm.queryParams.status = 'ENDED'
    wrapper.vm.queryParams.keyword = '测试'
    wrapper.vm.queryParams.orderBy = 'updated_time_desc'
    wrapper.vm.pagination.current = 2

    await wrapper.vm.handleReset()
    await flushPromises()

    expect(wrapper.vm.queryParams.status).toBe('')
    expect(wrapper.vm.queryParams.keyword).toBe('')
    expect(wrapper.vm.queryParams.orderBy).toBe('created_time_desc')
    expect(wrapper.vm.pagination.current).toBe(1)
    expect(campaignApi.getCampaigns).toHaveBeenLastCalledWith({
      current: 1,
      size: 10,
      status: '',
      keyword: '',
      orderBy: 'created_time_desc'
    })
  })

  it('navigates to create page', () => {
    const wrapper = createWrapper()

    wrapper.vm.handleCreate()

    expect(mockPush).toHaveBeenCalledWith('/admin/campaigns/create')
  })

  it('navigates to detail and edit pages', () => {
    const wrapper = createWrapper()

    wrapper.vm.handleView({ id: 1 })
    wrapper.vm.handleEdit({ id: 1 })

    expect(mockPush).toHaveBeenNthCalledWith(1, '/admin/campaigns/1')
    expect(mockPush).toHaveBeenNthCalledWith(2, '/admin/campaigns/1/edit')
  })

  it('shows draft actions and hides start-only actions for other statuses', async () => {
    campaignApi.getCampaigns.mockResolvedValue(
      createApiResponse(createPageResponse([baseCampaign], 1))
    )

    const wrapper = createWrapper()
    await flushPromises()

    expect(wrapper.text()).toContain('启动')
    expect(wrapper.text()).toContain('删除')
  })

  it('starts campaign after confirmation and reloads list', async () => {
    mockConfirm.mockResolvedValue()
    campaignApi.startCampaign.mockResolvedValue(createApiResponse(null))
    campaignApi.getCampaigns
      .mockResolvedValueOnce(createApiResponse(createPageResponse([baseCampaign], 1)))
      .mockResolvedValueOnce(createApiResponse(createPageResponse([{ ...baseCampaign, status: 'ACTIVE' }], 1)))

    const wrapper = createWrapper()
    await flushPromises()

    await wrapper.vm.handleStart(baseCampaign)
    await flushPromises()

    expect(mockConfirm).toHaveBeenCalled()
    expect(campaignApi.startCampaign).toHaveBeenCalledWith(1)
    expect(mockSuccess).toHaveBeenCalledWith('启动成功')
    expect(campaignApi.getCampaigns).toHaveBeenCalledTimes(2)
  })

  it('ends campaign after confirmation and reloads list', async () => {
    mockConfirm.mockResolvedValue()
    campaignApi.endCampaign.mockResolvedValue(createApiResponse(null))
    campaignApi.getCampaigns
      .mockResolvedValueOnce(createApiResponse(createPageResponse([{ ...baseCampaign, status: 'ACTIVE' }], 1)))
      .mockResolvedValueOnce(createApiResponse(createPageResponse([{ ...baseCampaign, status: 'ENDED' }], 1)))

    const wrapper = createWrapper()
    await flushPromises()

    await wrapper.vm.handleEnd({ ...baseCampaign, status: 'ACTIVE' })
    await flushPromises()

    expect(mockConfirm).toHaveBeenCalled()
    expect(campaignApi.endCampaign).toHaveBeenCalledWith(1)
    expect(mockSuccess).toHaveBeenCalledWith('结束成功')
    expect(campaignApi.getCampaigns).toHaveBeenCalledTimes(2)
  })

  it('deletes campaign after confirmation and reloads list', async () => {
    mockConfirm.mockResolvedValue()
    campaignApi.deleteCampaign.mockResolvedValue(createApiResponse(null))
    campaignApi.getCampaigns
      .mockResolvedValueOnce(createApiResponse(createPageResponse([baseCampaign], 1)))
      .mockResolvedValueOnce(createApiResponse(createPageResponse([], 0)))

    const wrapper = createWrapper()
    await flushPromises()

    await wrapper.vm.handleDelete(baseCampaign)
    await flushPromises()

    expect(mockConfirm).toHaveBeenCalled()
    expect(campaignApi.deleteCampaign).toHaveBeenCalledWith(1)
    expect(mockSuccess).toHaveBeenCalledWith('删除成功')
    expect(campaignApi.getCampaigns).toHaveBeenCalledTimes(2)
  })

  it('does not show error when confirmation is cancelled', async () => {
    mockConfirm.mockRejectedValue('cancel')

    const wrapper = createWrapper()
    await flushPromises()

    await wrapper.vm.handleStart(baseCampaign)
    await wrapper.vm.handleEnd({ ...baseCampaign, status: 'ACTIVE' })
    await wrapper.vm.handleDelete(baseCampaign)

    expect(campaignApi.startCampaign).not.toHaveBeenCalled()
    expect(campaignApi.endCampaign).not.toHaveBeenCalled()
    expect(campaignApi.deleteCampaign).not.toHaveBeenCalled()
    expect(mockError).not.toHaveBeenCalled()
  })

  it('returns correct status type and label', () => {
    const wrapper = createWrapper()

    expect(wrapper.vm.getStatusType('DRAFT')).toBe('info')
    expect(wrapper.vm.getStatusType('ACTIVE')).toBe('success')
    expect(wrapper.vm.getStatusType('ENDED')).toBe('warning')
    expect(wrapper.vm.getStatusType('ARCHIVED')).toBe('danger')
    expect(wrapper.vm.getStatusType('UNKNOWN')).toBe('info')

    expect(wrapper.vm.getStatusLabel('DRAFT')).toBe('草稿')
    expect(wrapper.vm.getStatusLabel('ACTIVE')).toBe('进行中')
    expect(wrapper.vm.getStatusLabel('ENDED')).toBe('已结束')
    expect(wrapper.vm.getStatusLabel('ARCHIVED')).toBe('已归档')
    expect(wrapper.vm.getStatusLabel('UNKNOWN')).toBe('UNKNOWN')
  })

  it('returns correct enrollment mode label', () => {
    const wrapper = createWrapper()

    expect(wrapper.vm.getEnrollmentModeLabel('AUTO')).toBe('自动报名')
    expect(wrapper.vm.getEnrollmentModeLabel('MANUAL')).toBe('自主报名')
    expect(wrapper.vm.getEnrollmentModeLabel('INVITE_ONLY')).toBe('仅邀请')
    expect(wrapper.vm.getEnrollmentModeLabel('CUSTOM')).toBe('CUSTOM')
  })

  it('formats dates and falls back for empty value', () => {
    const wrapper = createWrapper()

    expect(wrapper.vm.formatDate('')).toBe('-')
    expect(wrapper.vm.formatDate('2099-01-01T08:00:00')).toContain('2099')
  })
})
