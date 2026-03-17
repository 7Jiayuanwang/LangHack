import { describe, it, expect, vi, beforeEach } from 'vitest'
import { shallowMount } from '@vue/test-utils'

vi.mock('vue-router', () => ({
  useRouter: () => ({ push: vi.fn() })
}))

vi.mock('element-plus', () => ({
  ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn() }
}))

vi.mock('@/utils/request', () => ({ default: vi.fn().mockResolvedValue({ data: { records: [], total: 0 } }) }))

import Texts from '@/views/user/Texts.vue'

describe('User Texts.vue', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  const createWrapper = () => shallowMount(Texts)

  it('has loading ref defined', () => {
    const wrapper = createWrapper()
    expect(typeof wrapper.vm.loading).toBe('boolean')
  })

  it('has empty courses array', () => {
    const wrapper = createWrapper()
    expect(wrapper.vm.courses).toEqual([])
  })

  it('has empty texts array', () => {
    const wrapper = createWrapper()
    expect(wrapper.vm.texts).toEqual([])
  })

  it('has total at 0', () => {
    const wrapper = createWrapper()
    expect(wrapper.vm.total).toBe(0)
  })

  it('has currentPage at 1', () => {
    const wrapper = createWrapper()
    expect(wrapper.vm.currentPage).toBe(1)
  })

  it('has pageSize at 12', () => {
    const wrapper = createWrapper()
    expect(wrapper.vm.pageSize).toBe(12)
  })

  it('has selectedCourse as empty string', () => {
    const wrapper = createWrapper()
    expect(wrapper.vm.selectedCourse).toBe('')
  })

  it('has selectedDifficulty as empty string', () => {
    const wrapper = createWrapper()
    expect(wrapper.vm.selectedDifficulty).toBe('')
  })

  it('has searchKeyword as empty string', () => {
    const wrapper = createWrapper()
    expect(wrapper.vm.searchKeyword).toBe('')
  })
})
