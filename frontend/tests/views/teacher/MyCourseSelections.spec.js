import { describe, it, expect, vi, beforeEach } from 'vitest'
import { shallowMount, flushPromises } from '@vue/test-utils'

vi.mock('vue-router', () => ({
  useRouter: () => ({ push: vi.fn() }),
  useRoute: () => ({ params: {}, query: {} })
}))

vi.mock('element-plus', () => ({
  ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn() }
}))

const mockGetMyCourseSelections = vi.fn()
vi.mock('@/api/teacher', () => ({
  getMyCourseSelections: (...args) => mockGetMyCourseSelections(...args)
}))

import MyCourseSelections from '@/views/teacher/MyCourseSelections.vue'

const SELECTION_FIXTURE = [
  {
    id: 1,
    studentId: '2023122066164',
    studentName: '张三',
    courseName: '大学英语',
    courseCode: 'ENG101',
    classId: 'CS2301',
    credits: 3,
    academicYear: '2025',
    semester: 1
  },
  {
    id: 2,
    studentId: '2023122066165',
    studentName: null,
    courseName: null,
    courseCode: 'ENG102',
    classId: null,
    credits: null,
    academicYear: '2025',
    semester: 1
  }
]

describe('Teacher MyCourseSelections.vue', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockGetMyCourseSelections.mockResolvedValue({ data: [] })
  })

  const createWrapper = () => shallowMount(MyCourseSelections)

  // --- 挂载 ---
  it('mounts without error', () => {
    const wrapper = createWrapper()
    expect(wrapper.exists()).toBe(true)
  })

  it('加载完成后 selections 为空数组，loading 为 false', async () => {
    const wrapper = createWrapper()
    await flushPromises()
    expect(wrapper.vm.selections).toEqual([])
    expect(wrapper.vm.loading).toBe(false)
  })

  // --- onMounted 调用 API ---
  it('挂载时调用 getMyCourseSelections', async () => {
    createWrapper()
    await flushPromises()
    expect(mockGetMyCourseSelections).toHaveBeenCalledTimes(1)
  })

  it('挂载时传入 academicYear 和 semester 参数', async () => {
    createWrapper()
    await flushPromises()
    const [params] = mockGetMyCourseSelections.mock.calls[0]
    expect(params).toHaveProperty('academicYear')
    expect(params).toHaveProperty('semester')
  })

  // --- 数据展示 ---
  it('API 返回数据后 selections 被填充', async () => {
    mockGetMyCourseSelections.mockResolvedValue({ data: SELECTION_FIXTURE })
    const wrapper = createWrapper()
    await flushPromises()
    expect(wrapper.vm.selections).toHaveLength(2)
    expect(wrapper.vm.selections[0].studentName).toBe('张三')
  })

  it('API 返回 null data 时 selections 为空数组', async () => {
    mockGetMyCourseSelections.mockResolvedValue({ data: null })
    const wrapper = createWrapper()
    await flushPromises()
    expect(wrapper.vm.selections).toEqual([])
  })

  it('API 抛出异常时 selections 为空数组', async () => {
    mockGetMyCourseSelections.mockRejectedValue(new Error('network error'))
    const wrapper = createWrapper()
    await flushPromises()
    expect(wrapper.vm.selections).toEqual([])
  })

  // --- 筛选参数 ---
  it('academicYear 默认值为当前年份字符串', () => {
    const wrapper = createWrapper()
    expect(wrapper.vm.academicYear).toBe(String(new Date().getFullYear()))
  })

  it('academicYears 包含 5 个年份', () => {
    const wrapper = createWrapper()
    expect(wrapper.vm.academicYears).toHaveLength(5)
  })

  it('academicYears 最大值为当前年份', () => {
    const wrapper = createWrapper()
    const currentYear = String(new Date().getFullYear())
    const years = wrapper.vm.academicYears
    expect(years[years.length - 1]).toBe(currentYear)
  })

  it('修改 academicYear 后 loadData 传入新参数', async () => {
    const wrapper = createWrapper()
    await flushPromises()
    mockGetMyCourseSelections.mockClear()

    wrapper.vm.academicYear = '2024'
    await wrapper.vm.loadData()
    await flushPromises()

    expect(mockGetMyCourseSelections).toHaveBeenCalledWith(
      expect.objectContaining({ academicYear: '2024' })
    )
  })

  it('修改 semester 后 loadData 传入新参数', async () => {
    const wrapper = createWrapper()
    await flushPromises()
    mockGetMyCourseSelections.mockClear()

    wrapper.vm.semester = 2
    await wrapper.vm.loadData()
    await flushPromises()

    expect(mockGetMyCourseSelections).toHaveBeenCalledWith(
      expect.objectContaining({ semester: 2 })
    )
  })

  // --- 空数据状态 ---
  it('无数据时 selections 长度为 0', async () => {
    mockGetMyCourseSelections.mockResolvedValue({ data: [] })
    const wrapper = createWrapper()
    await flushPromises()
    expect(wrapper.vm.selections).toHaveLength(0)
  })

  // --- 字段降级展示（studentName 为 null 时用 studentId）---
  it('studentName 为 null 时 studentId 字段存在', async () => {
    mockGetMyCourseSelections.mockResolvedValue({ data: [SELECTION_FIXTURE[1]] })
    const wrapper = createWrapper()
    await flushPromises()
    expect(wrapper.vm.selections[0].studentId).toBe('2023122066165')
    expect(wrapper.vm.selections[0].studentName).toBeNull()
  })
})
