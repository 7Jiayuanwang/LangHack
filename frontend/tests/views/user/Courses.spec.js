import { describe, it, expect, vi, beforeEach } from 'vitest'
import { shallowMount } from '@vue/test-utils'

vi.mock('vue-router', () => ({
  useRouter: () => ({ push: vi.fn() })
}))

vi.mock('element-plus', () => ({
  ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn() }
}))

vi.mock('@/utils/request', () => ({ default: vi.fn().mockResolvedValue({ data: [] }) }))

import Courses from '@/views/user/Courses.vue'

describe('User Courses.vue', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  const createWrapper = () => shallowMount(Courses)

  it('has loading ref defined', () => {
    const wrapper = createWrapper()
    expect(typeof wrapper.vm.loading).toBe('boolean')
  })

  it('has empty courses array', () => {
    const wrapper = createWrapper()
    expect(wrapper.vm.courses).toEqual([])
  })
})
