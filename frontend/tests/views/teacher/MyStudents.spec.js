import { describe, it, expect, vi, beforeEach } from 'vitest'
import { shallowMount, flushPromises } from '@vue/test-utils'

vi.mock('vue-router', () => ({
  useRouter: () => ({ push: vi.fn() }),
  useRoute: () => ({ params: {}, query: {} })
}))

vi.mock('element-plus', () => ({
  ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn() }
}))

const mockGetMyStudents = vi.fn()
vi.mock('@/api/teacher', () => ({
  getMyStudents: (...args) => mockGetMyStudents(...args)
}))

import MyStudents from '@/views/teacher/MyStudents.vue'

const STUDENT_FIXTURE = {
  studentId: '2023122066164',
  studentName: '张三',
  className: '计算机2301',
  majorName: '计算机科学',
  grade: '2023',
  collegeName: '信息工程学院'
}

describe('Teacher MyStudents.vue', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockGetMyStudents.mockResolvedValue({ data: [] })
  })

  const createWrapper = () => shallowMount(MyStudents)

  // --- 挂载 ---
  it('mounts without error', () => {
    const wrapper = createWrapper()
    expect(wrapper.exists()).toBe(true)
  })

  it('加载完成后 loading 为 false，students 为空数组', async () => {
    const wrapper = createWrapper()
    await flushPromises()
    expect(wrapper.vm.students).toEqual([])
    expect(wrapper.vm.loading).toBe(false)
  })

  // --- onMounted 调用 API ---
  it('挂载时调用 getMyStudents', async () => {
    createWrapper()
    await flushPromises()
    expect(mockGetMyStudents).toHaveBeenCalledTimes(1)
  })

  it('挂载时传入 academicYear 和 semester 参数', async () => {
    createWrapper()
    await flushPromises()
    const [params] = mockGetMyStudents.mock.calls[0]
    expect(params).toHaveProperty('academicYear')
    expect(params).toHaveProperty('semester')
  })

  // --- 数据展示 ---
  it('API 返回数据后 students 被填充', async () => {
    mockGetMyStudents.mockResolvedValue({ data: [STUDENT_FIXTURE] })
    const wrapper = createWrapper()
    await flushPromises()
    expect(wrapper.vm.students).toHaveLength(1)
    expect(wrapper.vm.students[0].studentName).toBe('张三')
  })

  it('API 返回 null data 时 students 为空数组', async () => {
    mockGetMyStudents.mockResolvedValue({ data: null })
    const wrapper = createWrapper()
    await flushPromises()
    expect(wrapper.vm.students).toEqual([])
  })

  it('API 抛出异常时 students 为空数组', async () => {
    mockGetMyStudents.mockRejectedValue(new Error('network error'))
    const wrapper = createWrapper()
    await flushPromises()
    expect(wrapper.vm.students).toEqual([])
  })

  // --- 筛选参数 ---
  it('academicYear 默认值为当前年份字符串', () => {
    const wrapper = createWrapper()
    expect(wrapper.vm.academicYear).toBe(String(new Date().getFullYear()))
  })

  it('academicYears 计算属性包含当前年份', () => {
    const wrapper = createWrapper()
    const currentYear = String(new Date().getFullYear())
    expect(wrapper.vm.academicYears).toContain(currentYear)
  })

  it('academicYears 包含 5 个年份', () => {
    const wrapper = createWrapper()
    expect(wrapper.vm.academicYears).toHaveLength(5)
  })

  it('修改 academicYear 后调用 loadData 重新请求', async () => {
    const wrapper = createWrapper()
    await flushPromises()
    mockGetMyStudents.mockClear()

    wrapper.vm.academicYear = '2022'
    await wrapper.vm.loadData()
    await flushPromises()

    expect(mockGetMyStudents).toHaveBeenCalledWith(
      expect.objectContaining({ academicYear: '2022' })
    )
  })

  it('修改 semester 后调用 loadData 重新请求', async () => {
    const wrapper = createWrapper()
    await flushPromises()
    mockGetMyStudents.mockClear()

    wrapper.vm.semester = '2'
    await wrapper.vm.loadData()
    await flushPromises()

    expect(mockGetMyStudents).toHaveBeenCalledWith(
      expect.objectContaining({ semester: '2' })
    )
  })

  // --- 空数据状态 ---
  it('无数据时 students 长度为 0', async () => {
    mockGetMyStudents.mockResolvedValue({ data: [] })
    const wrapper = createWrapper()
    await flushPromises()
    expect(wrapper.vm.students).toHaveLength(0)
  })
})
