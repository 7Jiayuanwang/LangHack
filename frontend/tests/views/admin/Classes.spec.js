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

import Classes from '@/views/admin/Classes.vue'
import { ElMessage, ElMessageBox } from 'element-plus'

describe('Classes.vue', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockFetch.mockReset()
    // Default mock for all fetch calls (loadClasses, loadCourses, loadTeachers, batchLoadStudentCounts)
    mockFetch.mockResolvedValue({
      ok: true,
      json: () => Promise.resolve({ code: 200, data: { records: [], total: 0 } })
    })
  })

  it('initializes with empty classes array', () => {
    const wrapper = shallowMount(Classes)
    expect(wrapper.vm.classes).toEqual([])
  })

  it('has loading ref defined', () => {
    const wrapper = shallowMount(Classes)
    expect(typeof wrapper.vm.loading).toBe('boolean')
  })

  it('initializes with dialogVisible false', () => {
    const wrapper = shallowMount(Classes)
    expect(wrapper.vm.dialogVisible).toBe(false)
  })

  it('initializes with detailVisible false', () => {
    const wrapper = shallowMount(Classes)
    expect(wrapper.vm.detailVisible).toBe(false)
  })

  it('initializes with isEdit false', () => {
    const wrapper = shallowMount(Classes)
    expect(wrapper.vm.isEdit).toBe(false)
  })

  it('initializes with submitting false', () => {
    const wrapper = shallowMount(Classes)
    expect(wrapper.vm.submitting).toBe(false)
  })

  it('initializes with currentClass null', () => {
    const wrapper = shallowMount(Classes)
    expect(wrapper.vm.currentClass).toBeNull()
  })

  it('initializes with empty classStudents', () => {
    const wrapper = shallowMount(Classes)
    expect(wrapper.vm.classStudents).toEqual([])
  })

  it('initializes pagination with default values', () => {
    const wrapper = shallowMount(Classes)
    expect(wrapper.vm.pagination.current).toBe(1)
    expect(wrapper.vm.pagination.size).toBe(10)
    expect(wrapper.vm.pagination.total).toBe(0)
  })

  it('initializes filterForm with default values', () => {
    const wrapper = shallowMount(Classes)
    expect(wrapper.vm.filterForm.keyword).toBe('')
    expect(wrapper.vm.filterForm.courseId).toBeNull()
    expect(wrapper.vm.filterForm.teacherId).toBeNull()
  })

  it('initializes form with default values', () => {
    const wrapper = shallowMount(Classes)
    expect(wrapper.vm.form.id).toBeNull()
    expect(wrapper.vm.form.name).toBe('')
    expect(wrapper.vm.form.courseId).toBeNull()
    expect(wrapper.vm.form.teacherId).toBeNull()
    expect(wrapper.vm.form.description).toBe('')
  })

  it('loadClasses fetches and populates classes list', async () => {
    const mockClasses = [
      { id: 1, name: 'Class A' },
      { id: 2, name: 'Class B' }
    ]
    mockFetch.mockResolvedValue({
      ok: true,
      json: () => Promise.resolve({
        code: 200,
        data: { records: mockClasses, total: 2 }
      })
    })

    const wrapper = shallowMount(Classes)
    await wrapper.vm.loadClasses()

    expect(wrapper.vm.classes).toEqual(mockClasses)
    expect(wrapper.vm.pagination.total).toBe(2)
    expect(wrapper.vm.loading).toBe(false)
  })

  it('loadClasses shows error on non-ok response', async () => {
    mockFetch.mockResolvedValue({
      ok: false,
      json: () => Promise.resolve({})
    })

    const wrapper = shallowMount(Classes)
    await wrapper.vm.loadClasses()

    expect(ElMessage.error).toHaveBeenCalledWith('加载班级列表失败')
    expect(wrapper.vm.loading).toBe(false)
  })

  it('loadClasses shows error on fetch exception', async () => {
    mockFetch.mockRejectedValue(new Error('Network error'))

    const wrapper = shallowMount(Classes)
    await wrapper.vm.loadClasses()

    expect(ElMessage.error).toHaveBeenCalledWith('加载班级列表失败')
    expect(wrapper.vm.loading).toBe(false)
  })

  it('showCreateDialog sets isEdit to false and opens dialog', () => {
    const wrapper = shallowMount(Classes)
    wrapper.vm.showCreateDialog()

    expect(wrapper.vm.isEdit).toBe(false)
    expect(wrapper.vm.dialogVisible).toBe(true)
  })

  it('showEditDialog sets isEdit to true and populates form', () => {
    const wrapper = shallowMount(Classes)
    const row = { id: 3, name: 'Test Class', courseId: 1, teacherId: 2, description: 'Desc' }

    wrapper.vm.showEditDialog(row)

    expect(wrapper.vm.isEdit).toBe(true)
    expect(wrapper.vm.dialogVisible).toBe(true)
    expect(wrapper.vm.form.id).toBe(3)
    expect(wrapper.vm.form.name).toBe('Test Class')
    expect(wrapper.vm.form.courseId).toBe(1)
    expect(wrapper.vm.form.teacherId).toBe(2)
    expect(wrapper.vm.form.description).toBe('Desc')
  })

  it('showDetailDialog sets currentClass and opens detail dialog', async () => {
    mockFetch.mockResolvedValue({
      ok: true,
      json: () => Promise.resolve({ code: 200, data: [] })
    })

    const wrapper = shallowMount(Classes)
    const row = { id: 5, name: 'Detail Class' }

    await wrapper.vm.showDetailDialog(row)

    expect(wrapper.vm.currentClass).toEqual(row)
    expect(wrapper.vm.detailVisible).toBe(true)
  })

  it('resetFilter resets filterForm and pagination', async () => {
    const wrapper = shallowMount(Classes)
    wrapper.vm.filterForm.keyword = 'test'
    wrapper.vm.filterForm.courseId = 1
    wrapper.vm.filterForm.teacherId = 2
    wrapper.vm.pagination.current = 3

    await wrapper.vm.resetFilter()

    expect(wrapper.vm.filterForm.keyword).toBe('')
    expect(wrapper.vm.filterForm.courseId).toBeNull()
    expect(wrapper.vm.filterForm.teacherId).toBeNull()
    expect(wrapper.vm.pagination.current).toBe(1)
  })

  it('resetForm resets form to default values', () => {
    const wrapper = shallowMount(Classes)
    wrapper.vm.form.id = 10
    wrapper.vm.form.name = 'Modified'
    wrapper.vm.form.courseId = 5
    wrapper.vm.form.teacherId = 3
    wrapper.vm.form.description = 'Some desc'

    wrapper.vm.resetForm()

    expect(wrapper.vm.form.id).toBeNull()
    expect(wrapper.vm.form.name).toBe('')
    expect(wrapper.vm.form.courseId).toBeNull()
    expect(wrapper.vm.form.teacherId).toBeNull()
    expect(wrapper.vm.form.description).toBe('')
  })

  it('getStudentCount returns dash for uncached classId', () => {
    const wrapper = shallowMount(Classes)
    expect(wrapper.vm.getStudentCount(999)).toBe('-')
  })

  it('getStudentCount returns cached value when available', () => {
    const wrapper = shallowMount(Classes)
    const cache = new Map()
    cache.set(1, 25)
    wrapper.vm.studentCountCache = cache

    expect(wrapper.vm.getStudentCount(1)).toBe(25)
  })

  it('formatDateTime returns dash for falsy value', () => {
    const wrapper = shallowMount(Classes)
    expect(wrapper.vm.formatDateTime(null)).toBe('-')
    expect(wrapper.vm.formatDateTime(undefined)).toBe('-')
    expect(wrapper.vm.formatDateTime('')).toBe('-')
  })

  it('formatDateTime returns a formatted string for valid date', () => {
    const wrapper = shallowMount(Classes)
    const result = wrapper.vm.formatDateTime('2024-06-01T12:00:00')
    expect(typeof result).toBe('string')
    expect(result).not.toBe('-')
  })

  it('handleDropdownCommand dispatches edit command', () => {
    const wrapper = shallowMount(Classes)
    const row = { id: 1, name: 'C1', courseId: 1, teacherId: 1, description: '' }

    wrapper.vm.handleDropdownCommand('edit', row)

    expect(wrapper.vm.isEdit).toBe(true)
    expect(wrapper.vm.dialogVisible).toBe(true)
    expect(wrapper.vm.form.id).toBe(1)
  })

  it('handleDropdownCommand dispatches delete command', async () => {
    ElMessageBox.confirm.mockResolvedValue('confirm')
    mockFetch.mockResolvedValue({
      ok: true,
      json: () => Promise.resolve({ code: 200 })
    })

    const wrapper = shallowMount(Classes)
    await wrapper.vm.handleDropdownCommand('delete', { id: 1, name: 'Test' })

    expect(ElMessageBox.confirm).toHaveBeenCalled()
  })

  it('deleteClass calls confirm and API on user confirmation', async () => {
    ElMessageBox.confirm.mockResolvedValue('confirm')
    mockFetch.mockResolvedValue({
      ok: true,
      json: () => Promise.resolve({ code: 200 })
    })

    const wrapper = shallowMount(Classes)
    await wrapper.vm.deleteClass({ id: 5, name: 'Test Class' })

    expect(ElMessageBox.confirm).toHaveBeenCalled()
    expect(ElMessage.success).toHaveBeenCalledWith('班级删除成功')
  })

  it('deleteClass does not show error when user cancels', async () => {
    ElMessageBox.confirm.mockRejectedValue('cancel')

    const wrapper = shallowMount(Classes)
    await wrapper.vm.deleteClass({ id: 5, name: 'Test' })

    expect(ElMessage.error).not.toHaveBeenCalled()
  })

  it('deleteClass resets pagination to page 1 after deletion', async () => {
    ElMessageBox.confirm.mockResolvedValue('confirm')
    mockFetch.mockResolvedValue({
      ok: true,
      json: () => Promise.resolve({ code: 200, data: { records: [], total: 0 } })
    })

    const wrapper = shallowMount(Classes)
    wrapper.vm.pagination.current = 5

    await wrapper.vm.deleteClass({ id: 5, name: 'Test' })

    expect(wrapper.vm.pagination.current).toBe(1)
  })

  it('loadClassStudents fetches students for a class', async () => {
    const mockStudents = [
      { id: 1, student: { username: 'stu1' } },
      { id: 2, student: { username: 'stu2' } }
    ]
    mockFetch.mockResolvedValue({
      ok: true,
      json: () => Promise.resolve({ code: 200, data: mockStudents })
    })

    const wrapper = shallowMount(Classes)
    await wrapper.vm.loadClassStudents(1)

    expect(wrapper.vm.classStudents).toEqual(mockStudents)
    expect(wrapper.vm.studentsLoading).toBe(false)
  })

  it('removeStudent calls confirm and API on confirmation', async () => {
    ElMessageBox.confirm.mockResolvedValue('confirm')
    mockFetch.mockResolvedValue({
      ok: true,
      json: () => Promise.resolve({ code: 200, data: [] })
    })

    const wrapper = shallowMount(Classes)
    wrapper.vm.currentClass = { id: 10 }

    await wrapper.vm.removeStudent({ student: { id: 3, username: 'stu3' } })

    expect(ElMessageBox.confirm).toHaveBeenCalled()
    expect(ElMessage.success).toHaveBeenCalledWith('学生移除成功')
  })
})
