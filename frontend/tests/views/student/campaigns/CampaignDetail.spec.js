import { describe, it, expect, beforeEach, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import CampaignDetail from '@/views/student/campaigns/CampaignDetail.vue'
import * as campaignApi from '@/api/campaign'

const { mockPush, mockSuccess, mockError } = vi.hoisted(() => ({
  mockPush: vi.fn(),
  mockSuccess: vi.fn(),
  mockError: vi.fn()
}))

vi.mock('vue-router', () => ({
  useRouter: () => ({ push: mockPush }),
  useRoute: () => ({ params: { id: '1' } })
}))

vi.mock('element-plus', () => ({
  ElMessage: {
    success: mockSuccess,
    error: mockError
  }
}))

vi.mock('@/api/campaign', () => ({
  getStudentCampaignDetail: vi.fn(),
  enrollCampaign: vi.fn()
}))

const baseCampaign = {
  id: 1,
  title: '英语闯关活动',
  description: '活动介绍内容',
  status: 'ACTIVE',
  participantCount: 18,
  completionRule: 'THRESHOLD',
  maxParticipants: 100,
  startTime: '2099-01-01T08:00:00',
  endTime: '2099-01-31T23:59:59'
}

const baseTasks = [
  {
    id: 1,
    taskName: '',
    taskType: 'READING_TIME',
    taskUnit: 'MINUTES',
    targetValue: 60,
    points: 10,
    isRequired: true
  },
  {
    id: 2,
    taskName: '单词学习任务',
    taskType: 'WORD_LEARNING',
    taskUnit: 'COUNT',
    targetValue: 50,
    points: 20,
    isRequired: false
  }
]

const createApiResponse = (data, message = 'ok') => ({ code: 200, data, message })

const createDetailPayload = (overrides = {}) => ({
  campaign: { ...baseCampaign, ...(overrides.campaign || {}) },
  tasks: overrides.tasks || baseTasks,
  isEnrolled: overrides.isEnrolled || false,
  enrollmentStatus: overrides.enrollmentStatus || '',
  canEnroll: overrides.canEnroll !== false,
  enrollmentMessage: overrides.enrollmentMessage || ''
})

const createWrapper = () => mount(CampaignDetail, {
  global: {
    directives: {
      loading: {}
    },
    stubs: {
      ElPageHeader: { template: '<div><slot name="content" /></div>' },
      ElCard: { template: '<div><slot /></div>' },
      ElTag: { template: '<span><slot /></span>' },
      ElIcon: { template: '<i><slot /></i>' },
      ElResult: { template: '<div class="el-result-stub"><slot name="sub-title" /><slot name="extra" /></div>' },
      ElButton: {
        props: ['disabled', 'loading'],
        emits: ['click'],
        template: '<button :disabled="disabled || loading" @click="$emit(\'click\')"><slot /></button>'
      },
      Calendar: true,
      User: true,
      Trophy: true,
      Connection: true,
      Document: true,
      Checked: true,
      InfoFilled: true
    }
  }
})

describe('CampaignDetail.vue', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    campaignApi.getStudentCampaignDetail.mockResolvedValue(
      createApiResponse(createDetailPayload())
    )
    campaignApi.enrollCampaign.mockResolvedValue(createApiResponse('报名成功'))
  })

  it('loads student campaign detail on mount', async () => {
    const wrapper = createWrapper()
    await flushPromises()

    expect(campaignApi.getStudentCampaignDetail).toHaveBeenCalledWith('1')
    expect(wrapper.vm.campaign.title).toBe('英语闯关活动')
    expect(wrapper.vm.tasks).toHaveLength(2)
    expect(wrapper.html()).toContain('活动介绍内容')
    expect(wrapper.html()).toContain('完成指定数量任务')
  })

  it('uses task type fallback when task name is empty', async () => {
    const wrapper = createWrapper()
    await flushPromises()

    expect(wrapper.html()).toContain('阅读时长')
    expect(wrapper.html()).toContain('单词学习任务')
    expect(wrapper.html()).toContain('必做')
  })

  it('shows restriction message and disables enroll button when user cannot enroll', async () => {
    campaignApi.getStudentCampaignDetail.mockResolvedValue(
      createApiResponse(createDetailPayload({
        canEnroll: false,
        enrollmentMessage: '仅限指定班级报名'
      }))
    )

    const wrapper = createWrapper()
    await flushPromises()

    expect(wrapper.html()).toContain('不符合报名条件')
    expect(wrapper.html()).toContain('仅限指定班级报名')
    expect(wrapper.find('button').attributes('disabled')).toBeDefined()
  })

  it('reloads detail after successful enrollment', async () => {
    campaignApi.getStudentCampaignDetail
      .mockResolvedValueOnce(createApiResponse(createDetailPayload({ isEnrolled: false })))
      .mockResolvedValueOnce(createApiResponse(createDetailPayload({ isEnrolled: true, enrollmentStatus: 'ENROLLED' })))

    const wrapper = createWrapper()
    await flushPromises()

    await wrapper.find('button').trigger('click')
    await flushPromises()

    expect(campaignApi.enrollCampaign).toHaveBeenCalledWith('1')
    expect(campaignApi.getStudentCampaignDetail).toHaveBeenCalledTimes(2)
    expect(mockSuccess).toHaveBeenCalledWith('报名成功')
    expect(wrapper.html()).toContain('您已报名此活动')
    expect(wrapper.html()).toContain('查看我的进度')
  })

  it('navigates to progress page for enrolled users', async () => {
    campaignApi.getStudentCampaignDetail.mockResolvedValue(
      createApiResponse(createDetailPayload({ isEnrolled: true, enrollmentStatus: 'ENROLLED' }))
    )

    const wrapper = createWrapper()
    await flushPromises()

    await wrapper.find('button').trigger('click')

    expect(mockPush).toHaveBeenCalledWith('/student/campaigns/1/progress')
  })
})
