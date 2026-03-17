import { describe, it, expect, vi, beforeEach } from 'vitest'
import { shallowMount } from '@vue/test-utils'

vi.mock('element-plus', () => ({
  ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn() },
  ElMessageBox: { confirm: vi.fn().mockResolvedValue('confirm') }
}))

import Classes from '@/views/teacher/Classes.vue'

describe('Teacher Classes.vue', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.spyOn(Storage.prototype, 'getItem').mockReturnValue('mock-token')
    global.fetch = vi.fn().mockResolvedValue({
      ok: true,
      json: () => Promise.resolve({ code: 200, data: [] })
    })
  })

  const createWrapper = () => shallowMount(Classes)

  it('has empty classes array', () => {
    const wrapper = createWrapper()
    expect(wrapper.vm.classes).toEqual([])
  })

  it('has dialogVisible as false', () => {
    const wrapper = createWrapper()
    expect(wrapper.vm.dialogVisible).toBe(false)
  })

  it('has studentsDialogVisible as false', () => {
    const wrapper = createWrapper()
    expect(wrapper.vm.studentsDialogVisible).toBe(false)
  })

  it('has isEdit as false', () => {
    const wrapper = createWrapper()
    expect(wrapper.vm.isEdit).toBe(false)
  })

  it('has saving as false', () => {
    const wrapper = createWrapper()
    expect(wrapper.vm.saving).toBe(false)
  })

  it('has classForm reactive object', () => {
    const wrapper = createWrapper()
    expect(wrapper.vm.classForm).toBeDefined()
    expect(wrapper.vm.classForm.name).toBe('')
  })

  it('has classRules defined', () => {
    const wrapper = createWrapper()
    expect(wrapper.vm.classRules).toBeDefined()
    expect(wrapper.vm.classRules.name).toBeDefined()
  })

  it('has addStudentDialogVisible as false', () => {
    const wrapper = createWrapper()
    expect(wrapper.vm.addStudentDialogVisible).toBe(false)
  })

  it('has addStudentForm reactive object', () => {
    const wrapper = createWrapper()
    expect(wrapper.vm.addStudentForm).toBeDefined()
  })

  it('has courses as empty array', () => {
    const wrapper = createWrapper()
    expect(wrapper.vm.courses).toEqual([])
  })

  it('has currentClassStudents as empty array', () => {
    const wrapper = createWrapper()
    expect(wrapper.vm.currentClassStudents).toEqual([])
  })
})
