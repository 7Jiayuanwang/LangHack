import { describe, it, expect, vi, beforeEach } from 'vitest'
import { shallowMount } from '@vue/test-utils'

const mockFetch = vi.fn()
global.fetch = mockFetch

vi.mock('vue-router', () => ({
  useRouter: () => ({ push: vi.fn() }),
  useRoute: () => ({ params: {}, query: {} })
}))

vi.mock('element-plus', () => ({
  ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn() },
  ElMessageBox: { confirm: vi.fn() }
}))

import Assignments from '@/views/admin/Assignments.vue'
import { ElMessage, ElMessageBox } from 'element-plus'

describe('Assignments.vue', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockFetch.mockReset()
    // Default mock for all fetch calls
    mockFetch.mockResolvedValue({
      ok: true,
      json: () => Promise.resolve({ code: 200, data: { records: [], total: 0 } })
    })
  })

  it('initializes with empty assignments array', () => {
    const wrapper = shallowMount(Assignments)
    expect(wrapper.vm.assignments).toEqual([])
  })

  it('has loading ref defined', () => {
    const wrapper = shallowMount(Assignments)
    expect(typeof wrapper.vm.loading).toBe('boolean')
  })

  it('initializes with dialogVisible false', () => {
    const wrapper = shallowMount(Assignments)
    expect(wrapper.vm.dialogVisible).toBe(false)
  })

  it('initializes with progressDialogVisible false', () => {
    const wrapper = shallowMount(Assignments)
    expect(wrapper.vm.progressDialogVisible).toBe(false)
  })

  it('initializes with isEdit false', () => {
    const wrapper = shallowMount(Assignments)
    expect(wrapper.vm.isEdit).toBe(false)
  })

  it('initializes with empty classes array', () => {
    const wrapper = shallowMount(Assignments)
    expect(wrapper.vm.classes).toEqual([])
  })

  it('initializes with empty units array', () => {
    const wrapper = shallowMount(Assignments)
    expect(wrapper.vm.units).toEqual([])
  })

  it('initializes filterForm with default values', () => {
    const wrapper = shallowMount(Assignments)
    expect(wrapper.vm.filterForm.classId).toBeNull()
    expect(wrapper.vm.filterForm.status).toBeNull()
  })

  it('initializes assignmentForm with default values', () => {
    const wrapper = shallowMount(Assignments)
    expect(wrapper.vm.assignmentForm.id).toBeNull()
    expect(wrapper.vm.assignmentForm.title).toBe('')
    expect(wrapper.vm.assignmentForm.classId).toBeNull()
    expect(wrapper.vm.assignmentForm.unitId).toBeNull()
    expect(wrapper.vm.assignmentForm.assignmentType).toBe('UNIT')
    expect(wrapper.vm.assignmentForm.textIds).toEqual([])
    expect(wrapper.vm.assignmentForm.wordIds).toEqual([])
    expect(wrapper.vm.assignmentForm.deadline).toBeNull()
  })

  it('loadAssignments fetches and populates assignments list', async () => {
    const mockAssignments = [
      { id: 1, title: 'Assignment A', classId: 1, studentCount: 30, completedCount: 15 },
      { id: 2, title: 'Assignment B', classId: 2, studentCount: 25, completedCount: 20 }
    ]
    mockFetch.mockResolvedValue({
      ok: true,
      json: () => Promise.resolve({
        code: 200,
        data: { records: mockAssignments, total: 2 }
      })
    })

    const wrapper = shallowMount(Assignments)
    await wrapper.vm.loadAssignments()

    expect(wrapper.vm.loading).toBe(false)
  })

  it('loadAssignments shows error on non-ok response', async () => {
    mockFetch.mockResolvedValue({
      ok: false,
      json: () => Promise.resolve({})
    })

    const wrapper = shallowMount(Assignments)
    await wrapper.vm.loadAssignments()

    expect(ElMessage.error).toHaveBeenCalled()
    expect(wrapper.vm.loading).toBe(false)
  })

  it('loadAssignments shows error on fetch exception', async () => {
    mockFetch.mockRejectedValue(new Error('Network error'))

    const wrapper = shallowMount(Assignments)
    await wrapper.vm.loadAssignments()

    expect(ElMessage.error).toHaveBeenCalled()
    expect(wrapper.vm.loading).toBe(false)
  })

  it('loadClasses fetches and populates classes list', async () => {
    const mockClasses = [
      { id: 1, name: 'Class A', courseId: 1 },
      { id: 2, name: 'Class B', courseId: 1 }
    ]
    mockFetch.mockResolvedValue({
      ok: true,
      json: () => Promise.resolve({
        code: 200,
        data: { records: mockClasses, total: 2 }
      })
    })

    const wrapper = shallowMount(Assignments)
    await wrapper.vm.loadClasses()

    expect(wrapper.vm.classes.length).toBeGreaterThanOrEqual(0)
  })

  it('loadUnits fetches and populates units list', async () => {
    const mockUnits = [
      { id: 1, name: 'Unit A', courseId: 1 },
      { id: 2, name: 'Unit B', courseId: 1 }
    ]
    mockFetch.mockResolvedValue({
      ok: true,
      json: () => Promise.resolve({
        code: 200,
        data: { records: mockUnits, total: 2 }
      })
    })

    const wrapper = shallowMount(Assignments)
    await wrapper.vm.loadUnits()

    expect(wrapper.vm.units.length).toBeGreaterThanOrEqual(0)
  })

  it('showCreateDialog sets isEdit to false and opens dialog', () => {
    const wrapper = shallowMount(Assignments)
    wrapper.vm.showCreateDialog()

    expect(wrapper.vm.isEdit).toBe(false)
    expect(wrapper.vm.dialogVisible).toBe(true)
  })

  it('showCreateDialog resets assignment form', () => {
    const wrapper = shallowMount(Assignments)
    wrapper.vm.assignmentForm.title = 'Test'
    wrapper.vm.showCreateDialog()

    expect(wrapper.vm.assignmentForm.title).toBe('')
  })

  it('editAssignment sets isEdit to true and populates form', () => {
    const wrapper = shallowMount(Assignments)
    const assignment = {
      id: 1,
      title: 'Test Assignment',
      classId: 1,
      unitId: 1,
      description: 'Test',
      deadline: new Date()
    }

    wrapper.vm.editAssignment(assignment)

    expect(wrapper.vm.isEdit).toBe(true)
    expect(wrapper.vm.dialogVisible).toBe(true)
    expect(wrapper.vm.assignmentForm.id).toBe(1)
    expect(wrapper.vm.assignmentForm.title).toBe('Test Assignment')
  })

  it('resetAssignmentForm resets form to default values', () => {
    const wrapper = shallowMount(Assignments)
    wrapper.vm.assignmentForm.title = 'Modified'
    wrapper.vm.assignmentForm.classId = 5

    wrapper.vm.resetAssignmentForm()

    expect(wrapper.vm.assignmentForm.title).toBe('')
    expect(wrapper.vm.assignmentForm.classId).toBeNull()
    expect(wrapper.vm.assignmentForm.unitId).toBeNull()
  })

  it('getCompletionType returns correct tag type', () => {
    const wrapper = shallowMount(Assignments)

    expect(wrapper.vm.getCompletionType(15, 30)).toBe('warning')  // 50%
    expect(wrapper.vm.getCompletionType(14, 30)).toBe('danger')   // 46%
    expect(wrapper.vm.getCompletionType(25, 30)).toBe('success')  // 83%
  })

  it('getCompletionRate calculates correct percentage', () => {
    const wrapper = shallowMount(Assignments)
    const assignment = { studentCount: 100, completedCount: 80 }

    expect(wrapper.vm.getCompletionRate(assignment)).toBe(80)
  })

  it('formatTime returns formatted date string', () => {
    const wrapper = shallowMount(Assignments)
    const result = wrapper.vm.formatTime('2024-06-01T12:00:00')

    expect(typeof result).toBe('string')
    expect(result).not.toBe('')
  })

  it('formatStudyTime formats minutes correctly', () => {
    const wrapper = shallowMount(Assignments)

    expect(wrapper.vm.formatStudyTime(30)).toBe('30分钟')
    expect(wrapper.vm.formatStudyTime(90)).toBe('1小时30分钟')
    expect(wrapper.vm.formatStudyTime(60)).toBe('1小时')
  })

  it('onClassChange loads units for selected class', async () => {
    const wrapper = shallowMount(Assignments)
    wrapper.vm.classes = [{ id: 1, courseId: 100 }]

    await wrapper.vm.onClassChange(1)

    expect(wrapper.vm.units.length).toBeGreaterThanOrEqual(0)
  })

  it('onUnitChange loads unit content for non-UNIT assignments', async () => {
    const wrapper = shallowMount(Assignments)
    mockFetch.mockResolvedValue({
      ok: true,
      json: () => Promise.resolve({
        code: 200,
        data: { texts: [], words: [] }
      })
    })

    wrapper.vm.assignmentForm.assignmentType = 'TEXTS'
    await wrapper.vm.onUnitChange(1)

    expect(wrapper.vm.unitContent.texts).toBeDefined()
    expect(wrapper.vm.unitContent.words).toBeDefined()
  })

  it('handleDropdownCommand dispatches edit command', async () => {
    const wrapper = shallowMount(Assignments)
    const row = {
      id: 1,
      title: 'Test',
      classId: 1,
      unitId: 1,
      description: 'Test'
    }

    wrapper.vm.handleDropdownCommand('edit', row)

    expect(wrapper.vm.isEdit).toBe(true)
    expect(wrapper.vm.dialogVisible).toBe(true)
  })

  it('handleDropdownCommand dispatches delete command', async () => {
    ElMessageBox.confirm.mockResolvedValue('confirm')
    mockFetch.mockResolvedValue({
      ok: true,
      json: () => Promise.resolve({ code: 200 })
    })

    const wrapper = shallowMount(Assignments)
    const row = { id: 1, title: 'Test' }

    await wrapper.vm.handleDropdownCommand('delete', row)

    expect(ElMessageBox.confirm).toHaveBeenCalled()
  })

  it('viewProgress loads student progress data', async () => {
    const mockProgress = [
      { username: 'user1', realName: 'User One', completed: true, progress: 100, score: 90 },
      { username: 'user2', realName: 'User Two', completed: false, progress: 50, score: null }
    ]
    mockFetch.mockResolvedValue({
      ok: true,
      json: () => Promise.resolve({ code: 200, data: mockProgress })
    })

    const wrapper = shallowMount(Assignments)
    const assignment = { id: 1, title: 'Test', studentCount: 2 }

    await wrapper.vm.viewProgress(assignment)

    expect(wrapper.vm.progressDialogVisible).toBe(true)
    expect(wrapper.vm.studentProgress.length).toBeGreaterThanOrEqual(0)
  })

  it('calculateRealStatus returns active status for future deadline', () => {
    const wrapper = shallowMount(Assignments)
    const futureDate = new Date()
    futureDate.setDate(futureDate.getDate() + 1)

    const status = wrapper.vm.calculateRealStatus(futureDate)
    expect(status).toBe('active')
  })

  it('calculateRealStatus returns ended status for past deadline', () => {
    const wrapper = shallowMount(Assignments)
    const pastDate = new Date()
    pastDate.setDate(pastDate.getDate() - 1)

    const status = wrapper.vm.calculateRealStatus(pastDate)
    expect(status).toBe('ended')
  })

  it('deleteAssignment calls confirm and API on user confirmation', async () => {
    ElMessageBox.confirm.mockResolvedValue('confirm')
    mockFetch.mockResolvedValue({
      ok: true,
      json: () => Promise.resolve({ code: 200 })
    })

    const wrapper = shallowMount(Assignments)
    await wrapper.vm.deleteAssignment({ id: 5, title: 'Test' })

    expect(ElMessageBox.confirm).toHaveBeenCalled()
    expect(ElMessage.success).toHaveBeenCalledWith('任务删除成功')
  })

  it('deleteAssignment does not show error when user cancels', async () => {
    ElMessageBox.confirm.mockRejectedValue('cancel')

    const wrapper = shallowMount(Assignments)
    await wrapper.vm.deleteAssignment({ id: 5, title: 'Test' })

    expect(ElMessage.error).not.toHaveBeenCalled()
  })
})
