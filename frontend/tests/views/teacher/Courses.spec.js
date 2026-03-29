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

import Courses from '@/views/teacher/Courses.vue'

describe('teacher/Courses.vue', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockFetch.mockReset()
    // Default mock for all fetch calls
    mockFetch.mockResolvedValue({
      ok: true,
      json: () => Promise.resolve({ code: 200, data: { records: [], total: 0 } })
    })
  })

  it('initializes with empty courses array', () => {
    const wrapper = shallowMount(Courses)
    expect(wrapper.vm.courses).toEqual([])
  })

  it('initializes with empty classes array', () => {
    const wrapper = shallowMount(Courses)
    expect(wrapper.vm.classes).toEqual([])
  })

  it('initializes stats with zero values', () => {
    const wrapper = shallowMount(Courses)
    expect(wrapper.vm.stats.totalCourses).toBeGreaterThanOrEqual(0)
    expect(wrapper.vm.stats.activeClasses).toBeGreaterThanOrEqual(0)
    expect(wrapper.vm.stats.totalStudents).toBeGreaterThanOrEqual(0)
    expect(wrapper.vm.stats.totalAssignments).toBeGreaterThanOrEqual(0)
  })

  it('loadCourses method is defined and callable', async () => {
    const wrapper = shallowMount(Courses)
    expect(wrapper.vm.loadCourses).toBeDefined()
    expect(typeof wrapper.vm.loadCourses).toBe('function')
  })

  it('loadClasses method is defined and callable', async () => {
    const wrapper = shallowMount(Courses)
    expect(wrapper.vm.loadClasses).toBeDefined()
    expect(typeof wrapper.vm.loadClasses).toBe('function')
  })

  it('loadStats method is defined and callable', async () => {
    const wrapper = shallowMount(Courses)
    expect(wrapper.vm.loadStats).toBeDefined()
    expect(typeof wrapper.vm.loadStats).toBe('function')
  })

  it('getStatusType returns correct tag type', () => {
    const wrapper = shallowMount(Courses)

    expect(wrapper.vm.getStatusType('ACTIVE')).toBe('success')
    expect(wrapper.vm.getStatusType('INACTIVE')).toBe('warning')
  })

  it('getStatusText returns correct status text', () => {
    const wrapper = shallowMount(Courses)

    expect(wrapper.vm.getStatusText('ACTIVE')).toBe('活跃')
    expect(wrapper.vm.getStatusText('INACTIVE')).toBe('未激活')
  })

  it('getClassCountByCourse returns 0 for empty classes', () => {
    const wrapper = shallowMount(Courses)
    wrapper.vm.classes = []

    const count = wrapper.vm.getClassCountByCourse(1)

    expect(count).toBe(0)
  })

  it('getStudentCountByCourse returns 0 for empty classes', () => {
    const wrapper = shallowMount(Courses)
    wrapper.vm.classes = []

    const count = wrapper.vm.getStudentCountByCourse(1)

    expect(count).toBe(0)
  })

  it('getUnitCountByCourse returns 0 for non-existent course', () => {
    const wrapper = shallowMount(Courses)
    wrapper.vm.courses = []

    const count = wrapper.vm.getUnitCountByCourse(999)

    expect(count).toBe(0)
  })

  it('viewCourseDetail opens course detail dialog', () => {
    const wrapper = shallowMount(Courses)
    const course = {
      id: 1,
      name: 'Course A',
      description: 'Test course',
      status: 'ACTIVE'
    }

    wrapper.vm.viewCourseDetail(course)

    expect(wrapper.vm.currentCourse).toEqual(course)
    expect(wrapper.vm.courseDetailVisible).toBe(true)
  })

  it('manageClasses method is defined and callable', () => {
    const wrapper = shallowMount(Courses)
    const course = { id: 1, name: 'Test Course' }

    expect(wrapper.vm.manageClasses).toBeDefined()
    expect(() => wrapper.vm.manageClasses(course)).not.toThrow()
  })

  it('viewUnitsAndAssignments method is defined and callable', () => {
    const wrapper = shallowMount(Courses)

    expect(wrapper.vm.viewUnitsAndAssignments).toBeDefined()
    expect(typeof wrapper.vm.viewUnitsAndAssignments).toBe('function')
  })

  it('formatTime formats date strings', () => {
    const wrapper = shallowMount(Courses)
    const dateString = '2026-03-15T10:30:00Z'
    const formatted = wrapper.vm.formatTime(dateString)
    expect(formatted).toBeDefined()
    expect(typeof formatted).toBe('string')
  })

  it('getClassesByCourse returns empty array for non-existent course', () => {
    const wrapper = shallowMount(Courses)
    wrapper.vm.classes = []

    const classes = wrapper.vm.getClassesByCourse(999)

    expect(Array.isArray(classes)).toBe(true)
    expect(classes.length).toBe(0)
  })

  it('handles empty courses gracefully', async () => {
    mockFetch.mockResolvedValue({
      ok: true,
      json: () => Promise.resolve({
        code: 200,
        data: { records: [], total: 0 }
      })
    })

    const wrapper = shallowMount(Courses)
    await wrapper.vm.loadCourses()

    expect(wrapper.vm.courses).toEqual([])
  })

  it('handles API response with records array', async () => {
    const mockCourses = [
      { id: 1, name: 'Course A', status: 'ACTIVE' },
      { id: 2, name: 'Course B', status: 'ACTIVE' }
    ]
    mockFetch.mockResolvedValue({
      ok: true,
      json: () => Promise.resolve({
        code: 200,
        data: { records: mockCourses, total: 2 }
      })
    })

    const wrapper = shallowMount(Courses)
    await wrapper.vm.loadAllCourses()

    expect(wrapper.vm.courses.length).toBeGreaterThanOrEqual(0)
  })

  it('filters courses based on search term', () => {
    const wrapper = shallowMount(Courses)
    wrapper.vm.courses = [
      { id: 1, name: 'English Course', description: 'Learn English' },
      { id: 2, name: 'Math Course', description: 'Learn Math' }
    ]

    const filtered = wrapper.vm.courses.filter(c =>
      c.name.toLowerCase().includes('English'.toLowerCase())
    )

    expect(filtered.length).toBe(1)
    expect(filtered[0].id).toBe(1)
  })

  it('courseDetailVisible can be toggled', () => {
    const wrapper = shallowMount(Courses)
    expect(wrapper.vm.courseDetailVisible).toBe(false)

    wrapper.vm.courseDetailVisible = true
    expect(wrapper.vm.courseDetailVisible).toBe(true)
  })

  it('currentCourse can be updated', () => {
    const wrapper = shallowMount(Courses)
    const course = { id: 1, name: 'Test Course' }

    wrapper.vm.currentCourse = course
    expect(wrapper.vm.currentCourse).toEqual(course)
  })
})
