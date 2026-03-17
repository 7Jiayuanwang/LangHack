import { describe, it, expect, vi, beforeEach } from 'vitest'
import { shallowMount } from '@vue/test-utils'

vi.mock('vue-router', () => ({
  useRouter: () => ({ push: vi.fn() }),
  useRoute: () => ({ params: {}, query: {} })
}))

vi.mock('element-plus', () => ({
  ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn() },
  ElMessageBox: { confirm: vi.fn().mockResolvedValue('confirm') }
}))

vi.mock('@/utils/request', () => ({ default: vi.fn().mockResolvedValue({ data: [] }) }))

vi.mock('@/api/student', () => ({
  getStudentAssignments: vi.fn().mockResolvedValue({ data: { assignments: [] } })
}))

import Assignments from '@/views/student/Assignments.vue'

describe('Student Assignments.vue', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  const createWrapper = () => shallowMount(Assignments)

  it('mounts without error', () => {
    const wrapper = createWrapper()
    expect(wrapper.exists()).toBe(true)
  })

  it('calculateAssignmentStatus returns completed for 100% progress', () => {
    const wrapper = createWrapper()
    const result = wrapper.vm.calculateAssignmentStatus(100, null)
    expect(result).toBe('completed')
  })

  it('calculateAssignmentStatus returns expired for past deadline', () => {
    const wrapper = createWrapper()
    const pastDate = new Date(Date.now() - 86400000).toISOString()
    const result = wrapper.vm.calculateAssignmentStatus(50, pastDate)
    expect(result).toBe('expired')
  })

  it('calculateAssignmentStatus returns in-progress for valid deadline', () => {
    const wrapper = createWrapper()
    const futureDate = new Date(Date.now() + 7 * 86400000).toISOString()
    const result = wrapper.vm.calculateAssignmentStatus(50, futureDate)
    expect(result).toBe('in-progress')
  })

  it('isMobile computed based on windowWidth', () => {
    const wrapper = createWrapper()
    expect(typeof wrapper.vm.isMobile).toBe('boolean')
  })
})
