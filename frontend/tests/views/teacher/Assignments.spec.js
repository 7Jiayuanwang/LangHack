import { describe, it, expect, vi, beforeEach } from 'vitest'
import { shallowMount } from '@vue/test-utils'

vi.mock('element-plus', () => ({
  ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn() },
  ElMessageBox: { confirm: vi.fn().mockResolvedValue('confirm') }
}))

import Assignments from '@/views/teacher/Assignments.vue'

describe('Teacher Assignments.vue', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.spyOn(Storage.prototype, 'getItem').mockReturnValue('mock-token')
    global.fetch = vi.fn().mockResolvedValue({
      ok: true,
      json: () => Promise.resolve({ code: 200, data: [] })
    })
  })

  const createWrapper = () => shallowMount(Assignments)

  it('has loading as false', () => {
    const wrapper = createWrapper()
    expect(wrapper.vm.loading).toBe(false)
  })

  it('has empty assignments array', () => {
    const wrapper = createWrapper()
    expect(wrapper.vm.assignments).toEqual([])
  })

  it('has empty classes array', () => {
    const wrapper = createWrapper()
    expect(wrapper.vm.classes).toEqual([])
  })

  it('has dialogVisible as false', () => {
    const wrapper = createWrapper()
    expect(wrapper.vm.dialogVisible).toBe(false)
  })

  it('has progressDialogVisible as false', () => {
    const wrapper = createWrapper()
    expect(wrapper.vm.progressDialogVisible).toBe(false)
  })

  it('has isEdit as false', () => {
    const wrapper = createWrapper()
    expect(wrapper.vm.isEdit).toBe(false)
  })

  it('has saving as false', () => {
    const wrapper = createWrapper()
    expect(wrapper.vm.saving).toBe(false)
  })

  it('has assignmentForm reactive object', () => {
    const wrapper = createWrapper()
    expect(wrapper.vm.assignmentForm).toBeDefined()
    expect(wrapper.vm.assignmentForm.title).toBe('')
  })

  it('has assignmentRules defined', () => {
    const wrapper = createWrapper()
    expect(wrapper.vm.assignmentRules).toBeDefined()
    expect(wrapper.vm.assignmentRules.title).toBeDefined()
  })

  it('has filterForm reactive object', () => {
    const wrapper = createWrapper()
    expect(wrapper.vm.filterForm).toBeDefined()
  })

  it('has unitContent reactive object', () => {
    const wrapper = createWrapper()
    expect(wrapper.vm.unitContent).toBeDefined()
    expect(wrapper.vm.unitContent.texts).toEqual([])
  })

  it('has studentProgress as empty array', () => {
    const wrapper = createWrapper()
    expect(wrapper.vm.studentProgress).toEqual([])
  })
})
