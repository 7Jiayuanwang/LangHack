import { describe, it, expect, vi, beforeEach } from 'vitest'
import { shallowMount } from '@vue/test-utils'

vi.mock('vue-router', () => ({
  useRouter: () => ({ push: vi.fn() })
}))

vi.mock('element-plus', () => ({
  ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn() },
  ElMessageBox: { confirm: vi.fn().mockResolvedValue('confirm') }
}))

vi.mock('@/api/student', () => ({
  getMyVocabulary: vi.fn().mockResolvedValue({ data: { records: [], total: 0 } }),
  reviewWord: vi.fn().mockResolvedValue({ data: {} }),
  removeFromVocabulary: vi.fn().mockResolvedValue({ data: {} }),
  batchRemoveFromVocabulary: vi.fn().mockResolvedValue({ data: {} }),
  getReviewWords: vi.fn().mockResolvedValue({ data: [] }),
  getVocabularyStats: vi.fn().mockResolvedValue({ data: {} })
}))

vi.mock('@/utils/sm2', () => ({
  calculateSM2: vi.fn(() => ({
    easeFactor: 2.5,
    interval: 1,
    repetitions: 1,
    nextReviewDate: new Date().toISOString()
  }))
}))

import Vocabulary from '@/views/student/Vocabulary.vue'

describe('Student Vocabulary.vue', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  const createWrapper = () => shallowMount(Vocabulary)

  it('has empty vocabulary array initially', () => {
    const wrapper = createWrapper()
    expect(wrapper.vm.vocabulary).toEqual([])
  })

  it('has total ref at 0', () => {
    const wrapper = createWrapper()
    expect(wrapper.vm.total).toBe(0)
  })

  it('has currentPage at 1', () => {
    const wrapper = createWrapper()
    expect(wrapper.vm.currentPage).toBe(1)
  })

  it('has pageSize based on screen width', () => {
    const wrapper = createWrapper()
    // pageSize is dynamic: mobile (<768px) = 10, desktop = 100
    expect([10, 100]).toContain(wrapper.vm.pageSize)
  })

  it('has stats reactive object', () => {
    const wrapper = createWrapper()
    expect(wrapper.vm.stats).toBeDefined()
    expect(wrapper.vm.stats.totalWords).toBe(0)
  })

  it('has filterForm reactive object', () => {
    const wrapper = createWrapper()
    expect(wrapper.vm.filterForm).toBeDefined()
    expect(wrapper.vm.filterForm.masteryLevel).toBe('')
  })

  it('reviewDialogVisible is false initially', () => {
    const wrapper = createWrapper()
    expect(wrapper.vm.reviewDialogVisible).toBe(false)
  })

  it('wordDialogVisible is false initially', () => {
    const wrapper = createWrapper()
    expect(wrapper.vm.wordDialogVisible).toBe(false)
  })

  it('showAnswer is false initially', () => {
    const wrapper = createWrapper()
    expect(wrapper.vm.showAnswer).toBe(false)
  })

  it('selectedWords is empty array', () => {
    const wrapper = createWrapper()
    expect(wrapper.vm.selectedWords).toEqual([])
  })

  it('has PHASE constants', () => {
    const wrapper = createWrapper()
    expect(wrapper.vm.PHASE).toBeDefined()
    expect(wrapper.vm.PHASE.RECOGNITION).toBeDefined()
  })

  it('reviewPhase defaults to RECOGNITION', () => {
    const wrapper = createWrapper()
    expect(wrapper.vm.reviewPhase).toBe(wrapper.vm.PHASE.RECOGNITION)
  })
})
