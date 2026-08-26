import { describe, it, expect, beforeEach, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import MyProgress from '@/views/student/campaigns/MyProgress.vue'
import * as campaignApi from '@/api/campaign'

const mockPush = vi.fn()

vi.mock('vue-router', () => ({
  useRouter: () => ({ push: mockPush }),
  useRoute: () => ({ params: { id: '1' } })
}))

vi.mock('@/api/campaign', () => ({
  getMyProgress: vi.fn(),
  getMyTaskProgress: vi.fn(),
  getMyRanking: vi.fn(),
  getStudentCampaignDetail: vi.fn(),
  getMyScoreDetails: vi.fn(),
  getMyScoreLogs: vi.fn(),
  getMyStreak: vi.fn(),
  getClassActivityToday: vi.fn(),
  getWeeklyAwards: vi.fn()
}))

const mockCampaign = {
  id: 1,
  title: '测试活动',
  endTime: '2099-12-31T23:59:59',
  showScores: true,
  showRanking: true
}

const mockProgress = {
  enrolled: true,
  totalScore: 85,
  completedTasks: 1,
  completionStatus: 'IN_PROGRESS',
  myRank: 15,
  enrollmentStatus: 'ENROLLED'
}

const mockTaskProgress = [
  {
    taskId: 1,
    taskName: '',
    taskType: 'READING_TIME',
    taskUnit: 'MINUTES',
    targetValue: 60,
    points: 10,
    isRequired: true,
    currentValue: 45,
    isCompleted: false,
    completionCount: 0
  },
  {
    taskId: 2,
    taskName: '单词学习任务',
    taskType: 'WORD_LEARNING',
    taskUnit: 'COUNT',
    targetValue: 50,
    points: 20,
    isRequired: false,
    currentValue: 50,
    isCompleted: true,
    completionCount: 1
  }
]

const mockRanking = {
  myRank: 15,
  totalParticipants: 120,
  totalScore: 85,
  completedTasks: 1,
  aroundMe: [
    { rank: 13, userId: 101, userName: '李四', className: '一班', totalScore: 88, isMe: false },
    { rank: 14, userId: 102, userName: '王五', className: '二班', totalScore: 87, isMe: false },
    { rank: 15, userId: 103, userName: '张三', className: '三班', totalScore: 85, isMe: true }
  ]
}

const createApiResponse = (data) => ({ code: 200, data })

const createWrapper = () => mount(MyProgress, {
  global: {
    directives: {
      loading: {}
    },
    stubs: {
      ElPageHeader: { template: '<div><slot name="content" /></div>' },
      ElCard: { template: '<div><slot /></div>' },
      ElTag: { template: '<span><slot /></span>' },
      ElProgress: { template: '<div class="el-progress-stub" />' },
      ElEmpty: { template: '<div class="el-empty-stub" />' },
      ElIcon: { template: '<i><slot /></i>' },
      Trophy: true,
      Checked: true,
      TrophyBase: true,
      Timer: true,
      CircleCheck: true,
      Clock: true,
      Medal: true
    }
  }
})

describe('MyProgress.vue', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    campaignApi.getStudentCampaignDetail.mockResolvedValue(createApiResponse({ campaign: mockCampaign }))
    campaignApi.getMyProgress.mockResolvedValue(createApiResponse(mockProgress))
    campaignApi.getMyTaskProgress.mockResolvedValue(createApiResponse(mockTaskProgress))
    campaignApi.getMyRanking.mockResolvedValue(createApiResponse(mockRanking))
    campaignApi.getMyScoreDetails.mockResolvedValue(createApiResponse([]))
    campaignApi.getMyScoreLogs.mockResolvedValue(createApiResponse([]))
    campaignApi.getMyStreak.mockResolvedValue(createApiResponse({}))
    campaignApi.getClassActivityToday.mockResolvedValue(createApiResponse({}))
    campaignApi.getWeeklyAwards.mockResolvedValue(createApiResponse({}))
  })

  it('loads current student campaign data on mount', async () => {
    const wrapper = createWrapper()
    await flushPromises()

    expect(campaignApi.getStudentCampaignDetail).toHaveBeenCalledWith('1')
    expect(campaignApi.getMyProgress).toHaveBeenCalledWith('1')
    expect(campaignApi.getMyTaskProgress).toHaveBeenCalledWith('1')
    expect(campaignApi.getMyRanking).toHaveBeenCalledWith('1', expect.any(Object))
    expect(wrapper.vm.campaign.title).toBe('测试活动')
    expect(wrapper.vm.progress.myRank).toBe(15)
    expect(wrapper.vm.ranking.totalParticipants).toBe(120)
  })

  it('uses task type fallback when task name is empty', async () => {
    const wrapper = createWrapper()
    await flushPromises()

    expect(wrapper.html()).toContain('阅读时长')
    expect(wrapper.html()).toContain('单词学习任务')
  })

  it('calculates task progress with current API payload', async () => {
    const wrapper = createWrapper()
    await flushPromises()

    expect(wrapper.vm.calculateTaskProgress(mockTaskProgress[0])).toBe(75)
    expect(wrapper.vm.calculateTaskProgress(mockTaskProgress[1])).toBe(100)
  })

  it('computes rank badge classes correctly', async () => {
    const wrapper = createWrapper()
    await flushPromises()

    expect(wrapper.vm.getRankClass(1)).toBe('rank-gold')
    expect(wrapper.vm.getRankClass(2)).toBe('rank-silver')
    expect(wrapper.vm.getRankClass(3)).toBe('rank-bronze')
    expect(wrapper.vm.getRankClass(15)).toBe('')
  })

  it('hides ranking card when showRanking is false', async () => {
    campaignApi.getStudentCampaignDetail.mockResolvedValue(createApiResponse({
      campaign: { ...mockCampaign, showRanking: false }
    }))

    const wrapper = createWrapper()
    await flushPromises()

    expect(wrapper.html()).not.toContain('周围排名')
  })
})
