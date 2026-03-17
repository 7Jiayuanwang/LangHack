import { describe, it, expect, vi, beforeEach } from 'vitest'
import { shallowMount } from '@vue/test-utils'

vi.mock('vue-router', () => ({
  useRouter: () => ({ push: vi.fn() })
}))

vi.mock('element-plus', () => ({
  ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn() },
  ElMessageBox: { confirm: vi.fn().mockResolvedValue('confirm') }
}))

vi.mock('@/utils/request', () => ({ default: vi.fn().mockResolvedValue({ data: { records: [], total: 0 } }) }))

import Vocabulary from '@/views/user/Vocabulary.vue'

describe('User Vocabulary.vue', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  const createWrapper = () => shallowMount(Vocabulary)

  it('has loading ref defined', () => {
    const wrapper = createWrapper()
    expect(typeof wrapper.vm.loading).toBe('boolean')
  })

  it('has empty vocabulary array', () => {
    const wrapper = createWrapper()
    expect(wrapper.vm.vocabulary).toEqual([])
  })

  it('has total at 0', () => {
    const wrapper = createWrapper()
    expect(wrapper.vm.total).toBe(0)
  })

  it('has currentPage at 1', () => {
    const wrapper = createWrapper()
    expect(wrapper.vm.currentPage).toBe(1)
  })

  it('has pageSize at 20', () => {
    const wrapper = createWrapper()
    expect(wrapper.vm.pageSize).toBe(20)
  })

  it('has stats reactive object', () => {
    const wrapper = createWrapper()
    expect(wrapper.vm.stats).toBeDefined()
    expect(wrapper.vm.stats.totalWords).toBe(0)
  })

  it('has filterForm reactive object', () => {
    const wrapper = createWrapper()
    expect(wrapper.vm.filterForm).toBeDefined()
  })

  it('wordDialogVisible is false initially', () => {
    const wrapper = createWrapper()
    expect(wrapper.vm.wordDialogVisible).toBe(false)
  })

  it('reviewDialogVisible is false initially', () => {
    const wrapper = createWrapper()
    expect(wrapper.vm.reviewDialogVisible).toBe(false)
  })

  it('showAnswer is false initially', () => {
    const wrapper = createWrapper()
    expect(wrapper.vm.showAnswer).toBe(false)
  })

  it('selectedWords is empty array', () => {
    const wrapper = createWrapper()
    expect(wrapper.vm.selectedWords).toEqual([])
  })

  it('searchWord is empty string', () => {
    const wrapper = createWrapper()
    expect(wrapper.vm.searchWord).toBe('')
  })
})
