import { describe, it, expect, beforeEach, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import MyProgress from '@/views/student/campaigns/MyProgress.vue'
import * as campaignApi from '@/api/campaign'

// Mock API
vi.mock('@/api/campaign', () => ({
  getMyProgress: vi.fn(),
  getMyTaskProgress: vi.fn(),
  getMyRanking: vi.fn(),
  getStudentCampaignDetail: vi.fn()
}))

// Mock router
const mockRoute = {
  params: { id: '1' }
}

describe('MyProgress.vue', () => {
  let wrapper

  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  const mockCampaign = {
    id: 1,
    title: '测试活动',
    showScores: true,
    showRanking: true
  }

  const mockProgress = {
    enrolled: true,
    totalScore: 85,
    completedTasks: 3,
    ranking: 15,
    totalParticipants: 120
  }

  const mockTaskProgress = [
    {
      taskId: 1,
      taskName: '阅读时长任务',
      targetValue: 60,
      currentValue: 45,
      isCompleted: false
    },
    {
      taskId: 2,
      taskName: '单词学习任务',
      targetValue: 50,
      currentValue: 50,
      isCompleted: true
    }
  ]

  const mockRanking = {
    myRanking: {
      ranking: 15,
      totalScore: 85,
      completedTasks: 3,
      userName: '张三'
    },
    aroundMe: [
      { ranking: 13, userName: '李四', totalScore: 88 },
      { ranking: 14, userName: '王五', totalScore: 87 },
      { ranking: 16, userName: '赵六', totalScore: 84 }
    ]
  }

  const createWrapper = () => {
    return mount(MyProgress, {
      global: {
        mocks: {
          $route: mockRoute
        },
        stubs: {
          ElCard: {
            template: '<div><slot /></div>'
          },
          ElProgress: true,
          ElTag: true
        }
      }
    })
  }

  it('renders correctly', () => {
    campaignApi.getStudentCampaignDetail.mockResolvedValue({ data: mockCampaign })
    campaignApi.getMyProgress.mockResolvedValue({ data: mockProgress })
    campaignApi.getMyTaskProgress.mockResolvedValue({ data: mockTaskProgress })
    campaignApi.getMyRanking.mockResolvedValue({ data: mockRanking })

    wrapper = createWrapper()
    expect(wrapper.exists()).toBe(true)
  })

  it('loads progress data on mount', async () => {
    campaignApi.getStudentCampaignDetail.mockResolvedValue({ data: mockCampaign })
    campaignApi.getMyProgress.mockResolvedValue({ data: mockProgress })
    campaignApi.getMyTaskProgress.mockResolvedValue({ data: mockTaskProgress })
    campaignApi.getMyRanking.mockResolvedValue({ data: mockRanking })

    wrapper = createWrapper()
    await flushPromises()

    expect(campaignApi.getStudentCampaignDetail).toHaveBeenCalledWith('1')
    expect(campaignApi.getMyProgress).toHaveBeenCalledWith('1')
    expect(campaignApi.getMyTaskProgress).toHaveBeenCalledWith('1')
    expect(campaignApi.getMyRanking).toHaveBeenCalledWith('1')
  })

  it('displays total score when showScores is true', async () => {
    campaignApi.getStudentCampaignDetail.mockResolvedValue({ data: mockCampaign })
    campaignApi.getMyProgress.mockResolvedValue({ data: mockProgress })
    campaignApi.getMyTaskProgress.mockResolvedValue({ data: mockTaskProgress })
    campaignApi.getMyRanking.mockResolvedValue({ data: mockRanking })

    wrapper = createWrapper()
    await flushPromises()

    const html = wrapper.html()
    expect(html).toContain('85')
  })

  it('hides total score when showScores is false', async () => {
    const campaignNoScores = { ...mockCampaign, showScores: false }
    campaignApi.getStudentCampaignDetail.mockResolvedValue({ data: campaignNoScores })
    campaignApi.getMyProgress.mockResolvedValue({ data: mockProgress })
    campaignApi.getMyTaskProgress.mockResolvedValue({ data: mockTaskProgress })
    campaignApi.getMyRanking.mockResolvedValue({ data: mockRanking })

    wrapper = createWrapper()
    await flushPromises()

    // Score should not be displayed when showScores is false
    expect(wrapper.vm.campaign.showScores).toBe(false)
  })

  it('displays ranking when showRanking is true', async () => {
    campaignApi.getStudentCampaignDetail.mockResolvedValue({ data: mockCampaign })
    campaignApi.getMyProgress.mockResolvedValue({ data: mockProgress })
    campaignApi.getMyTaskProgress.mockResolvedValue({ data: mockTaskProgress })
    campaignApi.getMyRanking.mockResolvedValue({ data: mockRanking })

    wrapper = createWrapper()
    await flushPromises()

    const html = wrapper.html()
    expect(html).toContain('15')
  })

  it('calculates task progress percentage correctly', async () => {
    campaignApi.getStudentCampaignDetail.mockResolvedValue({ data: mockCampaign })
    campaignApi.getMyProgress.mockResolvedValue({ data: mockProgress })
    campaignApi.getMyTaskProgress.mockResolvedValue({ data: mockTaskProgress })
    campaignApi.getMyRanking.mockResolvedValue({ data: mockRanking })

    wrapper = createWrapper()
    await flushPromises()

    // First task: 45/60 = 75%
    expect(wrapper.vm.getTaskProgress(mockTaskProgress[0])).toBe(75)

    // Second task: 50/50 = 100%
    expect(wrapper.vm.getTaskProgress(mockTaskProgress[1])).toBe(100)
  })

  it('displays rank badge for top 3', async () => {
    const topRankProgress = { ...mockProgress, ranking: 1 }
    campaignApi.getStudentCampaignDetail.mockResolvedValue({ data: mockCampaign })
    campaignApi.getMyProgress.mockResolvedValue({ data: topRankProgress })
    campaignApi.getMyTaskProgress.mockResolvedValue({ data: mockTaskProgress })
    campaignApi.getMyRanking.mockResolvedValue({ data: { ...mockRanking, myRanking: { ...mockRanking.myRanking, ranking: 1 } } })

    wrapper = createWrapper()
    await flushPromises()

    expect(wrapper.vm.getRankClass(1)).toBe('rank-gold')
    expect(wrapper.vm.getRankClass(2)).toBe('rank-silver')
    expect(wrapper.vm.getRankClass(3)).toBe('rank-bronze')
    expect(wrapper.vm.getRankClass(15)).toBe('')
  })

  it('handles not enrolled state', async () => {
    const notEnrolledProgress = { enrolled: false }
    campaignApi.getStudentCampaignDetail.mockResolvedValue({ data: mockCampaign })
    campaignApi.getMyProgress.mockResolvedValue({ data: notEnrolledProgress })
    campaignApi.getMyTaskProgress.mockResolvedValue({ data: [] })
    campaignApi.getMyRanking.mockResolvedValue({ data: { myRanking: null, aroundMe: [] } })

    wrapper = createWrapper()
    await flushPromises()

    expect(wrapper.vm.progress.enrolled).toBe(false)
  })

  it('displays completed and incomplete tasks separately', async () => {
    campaignApi.getStudentCampaignDetail.mockResolvedValue({ data: mockCampaign })
    campaignApi.getMyProgress.mockResolvedValue({ data: mockProgress })
    campaignApi.getMyTaskProgress.mockResolvedValue({ data: mockTaskProgress })
    campaignApi.getMyRanking.mockResolvedValue({ data: mockRanking })

    wrapper = createWrapper()
    await flushPromises()

    const completedTasks = wrapper.vm.taskProgress.filter(t => t.isCompleted)
    const incompleteTasks = wrapper.vm.taskProgress.filter(t => !t.isCompleted)

    expect(completedTasks.length).toBe(1)
    expect(incompleteTasks.length).toBe(1)
  })
})
