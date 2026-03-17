import { describe, it, expect, vi, beforeEach } from 'vitest'
import { shallowMount } from '@vue/test-utils'

const { mockPush, mockGetCourses, mockCreateCourse, mockUpdateCourse, mockDeleteCourseApi } = vi.hoisted(() => ({
  mockPush: vi.fn(),
  mockGetCourses: vi.fn(),
  mockCreateCourse: vi.fn(),
  mockUpdateCourse: vi.fn(),
  mockDeleteCourseApi: vi.fn()
}))

vi.mock('vue-router', () => ({
  useRouter: () => ({ push: mockPush }),
  useRoute: () => ({ params: {}, query: {} })
}))

vi.mock('element-plus', () => ({
  ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn() },
  ElMessageBox: { confirm: vi.fn() }
}))

vi.mock('@/api/admin', () => ({
  getCourses: (...args) => mockGetCourses(...args),
  createCourse: (...args) => mockCreateCourse(...args),
  updateCourse: (...args) => mockUpdateCourse(...args),
  deleteCourse: (...args) => mockDeleteCourseApi(...args)
}))

import Courses from '@/views/admin/Courses.vue'
import { ElMessage, ElMessageBox } from 'element-plus'

describe('Courses.vue', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockGetCourses.mockResolvedValue({
      data: { records: [], total: 0 }
    })
  })

  const createWrapper = () => shallowMount(Courses)

  it('initializes with empty courses array', () => {
    const wrapper = createWrapper()
    expect(wrapper.vm.courses).toEqual([])
  })

  it('initializes with dialogVisible false', () => {
    const wrapper = createWrapper()
    expect(wrapper.vm.dialogVisible).toBe(false)
  })

  it('initializes with isEdit false', () => {
    const wrapper = createWrapper()
    expect(wrapper.vm.isEdit).toBe(false)
  })

  it('initializes with saving false', () => {
    const wrapper = createWrapper()
    expect(wrapper.vm.saving).toBe(false)
  })

  it('initializes pagination with default values', () => {
    const wrapper = createWrapper()
    expect(wrapper.vm.pagination.current).toBe(1)
    expect(wrapper.vm.pagination.size).toBe(10)
    expect(wrapper.vm.pagination.total).toBe(0)
  })

  it('initializes courseForm with default values', () => {
    const wrapper = createWrapper()
    expect(wrapper.vm.courseForm.id).toBeNull()
    expect(wrapper.vm.courseForm.name).toBe('')
    expect(wrapper.vm.courseForm.description).toBe('')
    expect(wrapper.vm.courseForm.coverImage).toBe('')
    expect(wrapper.vm.courseForm.status).toBe('ACTIVE')
  })

  it('loadCourses fetches and populates courses', async () => {
    const mockRecords = [
      { id: 1, name: 'Course A', status: 'ACTIVE' },
      { id: 2, name: 'Course B', status: 'INACTIVE' }
    ]
    mockGetCourses.mockResolvedValue({
      data: { records: mockRecords, total: 2 }
    })

    const wrapper = createWrapper()
    await wrapper.vm.loadCourses()

    expect(wrapper.vm.courses).toEqual(mockRecords)
    expect(wrapper.vm.pagination.total).toBe(2)
  })

  it('loadCourses shows error message on failure', async () => {
    mockGetCourses.mockRejectedValue(new Error('Network error'))

    const wrapper = createWrapper()
    await wrapper.vm.loadCourses()

    expect(ElMessage.error).toHaveBeenCalledWith('加载课程列表失败')
  })

  it('loadCourses handles null response data safely', async () => {
    mockGetCourses.mockResolvedValue({ data: null })

    const wrapper = createWrapper()
    await wrapper.vm.loadCourses()

    expect(wrapper.vm.courses).toEqual([])
    expect(wrapper.vm.pagination.total).toBe(0)
  })

  it('showAddDialog sets isEdit to false and opens dialog', () => {
    const wrapper = createWrapper()
    wrapper.vm.showAddDialog()

    expect(wrapper.vm.isEdit).toBe(false)
    expect(wrapper.vm.dialogVisible).toBe(true)
  })

  it('editCourse sets isEdit to true and populates form', () => {
    const wrapper = createWrapper()
    const course = {
      id: 1,
      name: 'Test Course',
      description: 'A test course',
      coverImage: 'http://example.com/img.jpg',
      status: 'ACTIVE'
    }

    wrapper.vm.editCourse(course)

    expect(wrapper.vm.isEdit).toBe(true)
    expect(wrapper.vm.dialogVisible).toBe(true)
    expect(wrapper.vm.courseForm.id).toBe(1)
    expect(wrapper.vm.courseForm.name).toBe('Test Course')
  })

  it('resetCourseForm resets all fields to defaults', () => {
    const wrapper = createWrapper()
    wrapper.vm.courseForm.id = 5
    wrapper.vm.courseForm.name = 'Some Name'

    wrapper.vm.resetCourseForm()

    expect(wrapper.vm.courseForm.id).toBeNull()
    expect(wrapper.vm.courseForm.name).toBe('')
  })

  it('resetSearch clears search form and resets pagination', async () => {
    const wrapper = createWrapper()
    wrapper.vm.searchForm.status = 'ACTIVE'
    wrapper.vm.searchForm.keyword = 'test'
    wrapper.vm.pagination.current = 3

    await wrapper.vm.resetSearch()

    expect(wrapper.vm.searchForm.status).toBe('')
    expect(wrapper.vm.searchForm.keyword).toBe('')
    expect(wrapper.vm.pagination.current).toBe(1)
  })

  it('manageUnits navigates to units page with course info', () => {
    const wrapper = createWrapper()
    const course = { id: 42, name: 'Test Course' }

    wrapper.vm.manageUnits(course)

    expect(mockPush).toHaveBeenCalledWith({
      path: '/admin/units',
      query: { courseId: 42, courseName: 'Test Course' }
    })
  })

  it('formatTime returns empty string for falsy value', () => {
    const wrapper = createWrapper()
    expect(wrapper.vm.formatTime(null)).toBe('')
    expect(wrapper.vm.formatTime(undefined)).toBe('')
    expect(wrapper.vm.formatTime('')).toBe('')
  })

  it('formatTime formats a valid date string', () => {
    const wrapper = createWrapper()
    const result = wrapper.vm.formatTime('2024-01-15T10:30:00')
    expect(result).toBeTruthy()
    expect(typeof result).toBe('string')
  })

  it('deleteCourse calls ElMessageBox.confirm and API on confirmation', async () => {
    ElMessageBox.confirm.mockResolvedValue('confirm')
    mockDeleteCourseApi.mockResolvedValue({ code: 200 })

    const wrapper = createWrapper()
    const course = { id: 1, name: 'Test' }

    await wrapper.vm.deleteCourse(course)

    expect(ElMessageBox.confirm).toHaveBeenCalled()
    expect(mockDeleteCourseApi).toHaveBeenCalledWith(1)
    expect(ElMessage.success).toHaveBeenCalledWith('课程删除成功')
  })

  it('deleteCourse does not show error when user cancels', async () => {
    ElMessageBox.confirm.mockRejectedValue('cancel')

    const wrapper = createWrapper()
    await wrapper.vm.deleteCourse({ id: 1, name: 'Test' })

    expect(ElMessage.error).not.toHaveBeenCalled()
  })
})
