import { describe, it, expect, beforeEach, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import CampaignList from '@/views/student/campaigns/CampaignList.vue'
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
  getEnrollableCampaigns: vi.fn(),
  getMyEnrolledCampaigns: vi.fn(),
  enrollCampaign: vi.fn(),
  withdrawCampaign: vi.fn()
}))

const createApiResponse = (data, message = 'ok') => ({ code: 200, data, message })

const createPageResponse = (records, total) => ({
  records,
  total,
  current: 1,
  size: 10
})

const baseAvailableCampaign = {
  id: 1,
  title: '英语闯关活动',
  description: '提升英语水平',
  status: 'ACTIVE',
  startTime: '2099-01-01T08:00:00',
  endTime: '2099-01-31T23:59:59',
  participantCount: 50,
  canEnroll: true,
  isEnrolled: false
}

const baseEnrolledCampaign = {
  id: 2,
  title: '我的活动',
  completionStatus: 'IN_PROGRESS',
  totalScore: 80,
  completedTasks: 4,
  totalTasks: 10,
  myRank: 15,
  endTime: '2099-02-28T23:59:59'
}

const createWrapper = () => mount(CampaignList, {
  global: {
    stubs: {
      ElTabs: { template: '<div><slot /></div>' },
      ElTabPane: { template: '<div><slot /></div>' },
      ElInput: { template: '<input v-model="modelValue" @keyup.enter="$emit(\'keyup.enter\')" />', props: ['modelValue'] },
      ElCard: { template: '<div @click="$emit(\'click\')"><slot name="header" /><slot /></div>' },
      ElTag: { template: '<span><slot /></span>' },
      ElEmpty: { template: '<div class="el-empty-stub"><slot name="description" /></div>' },
      ElButton: {
        props: ['disabled'],
        emits: ['click'],
        template: '<button :disabled="disabled" @click="$emit(\'click\')"><slot /></button>'
      },
      ElPagination: {
        props: ['currentPage', 'pageSize', 'total'],
        emits: ['update:currentPage', 'update:pageSize', 'size-change', 'current-change'],
        template: '<div class="el-pagination-stub"></div>'
      },
      ElSelect: {
        props: ['modelValue'],
        emits: ['update:modelValue', 'change'],
        template: '<select :value="modelValue" @change="$emit(\'update:modelValue\', $event.target.value); $emit(\'change\', $event.target.value)"><slot /></select>'
      },
      ElOption: { template: '<option><slot /></option>' },
      ElProgress: { template: '<div class="el-progress-stub"></div>' },
      ElIcon: { template: '<i><slot /></i>' },
      Search: true,
      Calendar: true,
      User: true,
      TrophyBase: true
    }
  }
})

describe('CampaignList.vue', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    campaignApi.getEnrollableCampaigns.mockResolvedValue(
      createApiResponse(createPageResponse([], 0))
    )
    campaignApi.getMyEnrolledCampaigns.mockResolvedValue(
      createApiResponse(createPageResponse([], 0))
    )
  })

  it('loads available campaigns on mount', async () => {
    campaignApi.getEnrollableCampaigns.mockResolvedValue(
      createApiResponse(createPageResponse([baseAvailableCampaign], 1))
    )

    const wrapper = createWrapper()
    await flushPromises()

    expect(campaignApi.getEnrollableCampaigns).toHaveBeenCalledWith({
      current: 1,
      size: 10,
      keyword: ''
    })
    expect(wrapper.vm.availableCampaigns).toHaveLength(1)
    expect(wrapper.vm.availablePagination.total).toBe(1)
  })

  it('shows empty state when no available campaigns', async () => {
    const wrapper = createWrapper()
    await flushPromises()

    expect(wrapper.html()).toContain('暂无可报名活动')
  })

  it('filters by keyword when searching', async () => {
    const wrapper = createWrapper()
    await flushPromises()

    wrapper.vm.searchKeyword = '英语'
    await wrapper.vm.loadAvailableCampaigns()

    expect(campaignApi.getEnrollableCampaigns).toHaveBeenCalledWith({
      current: 1,
      size: 10,
      keyword: '英语'
    })
  })

  it('switches to enrolled tab and loads enrolled campaigns', async () => {
    campaignApi.getMyEnrolledCampaigns.mockResolvedValue(
      createApiResponse(createPageResponse([baseEnrolledCampaign], 1))
    )

    const wrapper = createWrapper()
    await flushPromises()

    wrapper.vm.activeTab = 'enrolled'
    await wrapper.vm.handleTabChange('enrolled')
    await flushPromises()

    expect(campaignApi.getMyEnrolledCampaigns).toHaveBeenCalledWith({
      current: 1,
      size: 10,
      status: ''
    })
    expect(wrapper.vm.enrolledCampaigns).toHaveLength(1)
  })

  it('filters enrolled campaigns by status', async () => {
    const wrapper = createWrapper()
    await flushPromises()

    wrapper.vm.activeTab = 'enrolled'
    wrapper.vm.enrolledStatus = 'COMPLETED'
    await wrapper.vm.loadEnrolledCampaigns()

    expect(campaignApi.getMyEnrolledCampaigns).toHaveBeenCalledWith({
      current: 1,
      size: 10,
      status: 'COMPLETED'
    })
  })

  it('navigates to detail page when clicking available campaign', async () => {
    campaignApi.getEnrollableCampaigns.mockResolvedValue(
      createApiResponse(createPageResponse([baseAvailableCampaign], 1))
    )

    const wrapper = createWrapper()
    await flushPromises()

    wrapper.vm.handleViewDetail(1)

    expect(mockPush).toHaveBeenCalledWith('/student/campaigns/1')
  })

  it('navigates to progress page when clicking enrolled campaign', async () => {
    const wrapper = createWrapper()

    wrapper.vm.handleViewProgress(2)

    expect(mockPush).toHaveBeenCalledWith('/student/campaigns/2/progress')
  })

  it('enrolls in campaign after confirmation', async () => {
    mockConfirm.mockResolvedValue()
    campaignApi.enrollCampaign.mockResolvedValue(createApiResponse('报名成功'))
    campaignApi.getEnrollableCampaigns
      .mockResolvedValueOnce(createApiResponse(createPageResponse([baseAvailableCampaign], 1)))
      .mockResolvedValueOnce(createApiResponse(createPageResponse([], 0)))

    const wrapper = createWrapper()
    await flushPromises()

    await wrapper.vm.handleEnroll(baseAvailableCampaign)
    await flushPromises()

    expect(mockConfirm).toHaveBeenCalled()
    expect(campaignApi.enrollCampaign).toHaveBeenCalledWith(1)
    expect(mockSuccess).toHaveBeenCalledWith('报名成功')
    expect(campaignApi.getEnrollableCampaigns).toHaveBeenCalledTimes(2)
  })

  it('does not enroll if user cancels confirmation', async () => {
    mockConfirm.mockRejectedValue('cancel')

    const wrapper = createWrapper()
    await wrapper.vm.handleEnroll(baseAvailableCampaign)

    expect(campaignApi.enrollCampaign).not.toHaveBeenCalled()
    expect(mockError).not.toHaveBeenCalled()
  })

  it('does not enroll if already enrolled', async () => {
    const enrolledCampaign = { ...baseAvailableCampaign, isEnrolled: true }

    const wrapper = createWrapper()
    await wrapper.vm.handleEnroll(enrolledCampaign)

    expect(mockConfirm).not.toHaveBeenCalled()
    expect(campaignApi.enrollCampaign).not.toHaveBeenCalled()
  })

  it('does not enroll if canEnroll is false', async () => {
    const restrictedCampaign = { ...baseAvailableCampaign, canEnroll: false }

    const wrapper = createWrapper()
    await wrapper.vm.handleEnroll(restrictedCampaign)

    expect(mockConfirm).not.toHaveBeenCalled()
    expect(campaignApi.enrollCampaign).not.toHaveBeenCalled()
  })

  it('withdraws from campaign after confirmation', async () => {
    mockConfirm.mockResolvedValue()
    campaignApi.withdrawCampaign.mockResolvedValue(createApiResponse('已退出活动'))
    campaignApi.getMyEnrolledCampaigns
      .mockResolvedValueOnce(createApiResponse(createPageResponse([], 0)))
      .mockResolvedValueOnce(createApiResponse(createPageResponse([], 0)))

    const wrapper = createWrapper()
    await flushPromises()

    await wrapper.vm.handleWithdraw(baseEnrolledCampaign)
    await flushPromises()

    expect(mockConfirm).toHaveBeenCalled()
    expect(campaignApi.withdrawCampaign).toHaveBeenCalledWith(2)
    expect(mockSuccess).toHaveBeenCalledWith('已退出活动')
  })

  it('computes progress percentage correctly', () => {
    const wrapper = createWrapper()

    expect(wrapper.vm.calculateProgress({ completedTasks: 5, totalTasks: 10 })).toBe(50)
    expect(wrapper.vm.calculateProgress({ completedTasks: 10, totalTasks: 10 })).toBe(100)
    expect(wrapper.vm.calculateProgress({ completedTasks: 0, totalTasks: 10 })).toBe(0)
  })

  it('returns correct progress color based on completion', () => {
    const wrapper = createWrapper()

    expect(wrapper.vm.getProgressColor({ completedTasks: 10, totalTasks: 10 })).toBe('#67c23a')
    expect(wrapper.vm.getProgressColor({ completedTasks: 6, totalTasks: 10 })).toBe('#409eff')
    expect(wrapper.vm.getProgressColor({ completedTasks: 2, totalTasks: 10 })).toBe('#e6a23c')
  })

  it('formats date range correctly', () => {
    const wrapper = createWrapper()

    const formatted = wrapper.vm.formatDateRange('2099-01-01T08:00:00', '2099-01-31T23:59:59')
    expect(formatted).toContain('1月')
    expect(formatted).toContain('1日')
    expect(formatted).toContain('31日')
  })

  it('calculates remaining days correctly', () => {
    const wrapper = createWrapper()

    const futureDate = new Date()
    futureDate.setDate(futureDate.getDate() + 5)
    expect(wrapper.vm.getRemainingDays(futureDate.toISOString())).toBe('5 天')

    const pastDate = new Date()
    pastDate.setDate(pastDate.getDate() - 1)
    expect(wrapper.vm.getRemainingDays(pastDate.toISOString())).toBe('已结束')
  })

  it('returns correct enrollment button text', () => {
    const wrapper = createWrapper()

    expect(wrapper.vm.getEnrollButtonText({ isEnrolled: true })).toBe('已报名')
    expect(wrapper.vm.getEnrollButtonText({ canEnroll: false })).toBe('不符合条件')
    expect(wrapper.vm.getEnrollButtonText({ canEnroll: true, isEnrolled: false })).toBe('立即报名')
  })

  it('returns correct status type and label', () => {
    const wrapper = createWrapper()

    expect(wrapper.vm.getStatusType('COMPLETED')).toBe('success')
    expect(wrapper.vm.getStatusType('IN_PROGRESS')).toBe('warning')
    expect(wrapper.vm.getStatusType('NOT_STARTED')).toBe('info')

    expect(wrapper.vm.getStatusLabel('COMPLETED')).toBe('已完成')
    expect(wrapper.vm.getStatusLabel('IN_PROGRESS')).toBe('进行中')
    expect(wrapper.vm.getStatusLabel('NOT_STARTED')).toBe('未开始')
  })
})
