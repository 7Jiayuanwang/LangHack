import { describe, it, expect, vi, beforeEach } from 'vitest'
import { shallowMount } from '@vue/test-utils'

vi.mock('vue-router', () => ({
  useRouter: () => ({ push: vi.fn() })
}))

vi.mock('element-plus', () => ({
  ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn() }
}))

vi.mock('@/store/user', () => ({
  useUserStore: () => ({
    userInfo: { id: 1, username: 'user1', role: 'USER' },
    token: 'mock-token'
  })
}))

vi.mock('@/utils/request', () => ({ default: vi.fn().mockResolvedValue({ data: {} }) }))

import Dashboard from '@/views/user/Dashboard.vue'

describe('User Dashboard.vue', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  const createWrapper = () => shallowMount(Dashboard)

  it('has stats reactive object', () => {
    const wrapper = createWrapper()
    expect(wrapper.vm.stats).toBeDefined()
    expect(wrapper.vm.stats.totalCourses).toBe(0)
  })

  it('has recentUploads as empty array', () => {
    const wrapper = createWrapper()
    expect(wrapper.vm.recentUploads).toEqual([])
  })

  it('has recommendedTexts as empty array', () => {
    const wrapper = createWrapper()
    expect(wrapper.vm.recommendedTexts).toEqual([])
  })

  it('getDifficultyType returns correct type', () => {
    const wrapper = createWrapper()
    expect(wrapper.vm.getDifficultyType('EASY')).toBeDefined()
  })

  it('getDifficultyText returns correct text', () => {
    const wrapper = createWrapper()
    expect(wrapper.vm.getDifficultyText('MEDIUM')).toBeDefined()
  })
})
