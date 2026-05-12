import { describe, it, expect, vi, beforeEach } from 'vitest'
import { shallowMount, flushPromises } from '@vue/test-utils'

vi.mock('vue-router', () => ({
  useRouter: () => ({ push: vi.fn() }),
  useRoute: () => ({ params: {}, query: {} })
}))

vi.mock('element-plus', () => ({
  ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn() }
}))

const mockGetMySchedule = vi.fn()
vi.mock('@/api/teacher', () => ({
  getMySchedule: (...args) => mockGetMySchedule(...args)
}))

import MySchedule from '@/views/teacher/MySchedule.vue'

const SCHEDULE_FIXTURE = [
  { id: 1, dayOfWeek: 1, period: '1-2节', classId: 'CS2301', classLocation: 'A101', weekRange: '1-16周' },
  { id: 2, dayOfWeek: 3, period: '3-4节', classId: 'CS2302', classLocation: 'B202', weekRange: '1-8周' },
  { id: 3, dayOfWeek: 2, period: '1-2节', classId: 'CS2303', classLocation: null, weekRange: null }
]

describe('Teacher MySchedule.vue', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockGetMySchedule.mockResolvedValue({ data: [] })
  })

  const createWrapper = () => shallowMount(MySchedule)

  // --- 挂载 ---
  it('mounts without error', () => {
    const wrapper = createWrapper()
    expect(wrapper.exists()).toBe(true)
  })

  it('加载完成后 schedules 为空数组，loading 为 false', async () => {
    const wrapper = createWrapper()
    await flushPromises()
    expect(wrapper.vm.schedules).toEqual([])
    expect(wrapper.vm.loading).toBe(false)
  })

  // --- onMounted 调用 API ---
  it('挂载时调用 getMySchedule', async () => {
    createWrapper()
    await flushPromises()
    expect(mockGetMySchedule).toHaveBeenCalledTimes(1)
  })

  it('挂载时传入 academicYear 和 semester 参数', async () => {
    createWrapper()
    await flushPromises()
    const [params] = mockGetMySchedule.mock.calls[0]
    expect(params).toHaveProperty('academicYear')
    expect(params).toHaveProperty('semester')
  })

  // --- 数据展示与排序 ---
  it('API 返回数据后 schedules 被填充', async () => {
    mockGetMySchedule.mockResolvedValue({ data: SCHEDULE_FIXTURE })
    const wrapper = createWrapper()
    await flushPromises()
    expect(wrapper.vm.schedules).toHaveLength(3)
  })

  it('schedules 按 dayOfWeek 升序排序', async () => {
    mockGetMySchedule.mockResolvedValue({ data: SCHEDULE_FIXTURE })
    const wrapper = createWrapper()
    await flushPromises()
    const days = wrapper.vm.schedules.map(s => s.dayOfWeek)
    expect(days).toEqual([...days].sort((a, b) => a - b))
  })

  it('API 返回 null data 时 schedules 为空数组', async () => {
    mockGetMySchedule.mockResolvedValue({ data: null })
    const wrapper = createWrapper()
    await flushPromises()
    expect(wrapper.vm.schedules).toEqual([])
  })

  it('API 抛出异常时 schedules 为空数组', async () => {
    mockGetMySchedule.mockRejectedValue(new Error('network error'))
    const wrapper = createWrapper()
    await flushPromises()
    expect(wrapper.vm.schedules).toEqual([])
  })

  // --- dayLabel 辅助函数 ---
  it('dayLabel(1) 返回 周一', () => {
    const wrapper = createWrapper()
    expect(wrapper.vm.dayLabel(1)).toBe('周一')
  })

  it('dayLabel(7) 返回 周日', () => {
    const wrapper = createWrapper()
    expect(wrapper.vm.dayLabel(7)).toBe('周日')
  })

  it('dayLabel(0) 返回 周0（边界值）', () => {
    const wrapper = createWrapper()
    expect(wrapper.vm.dayLabel(0)).toBe('周0')
  })

  it('dayLabel(8) 返回 周8（超出范围）', () => {
    const wrapper = createWrapper()
    expect(wrapper.vm.dayLabel(8)).toBe('周8')
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

  it('修改 academicYear 后 loadData 传入新参数', async () => {
    const wrapper = createWrapper()
    await flushPromises()
    mockGetMySchedule.mockClear()

    wrapper.vm.academicYear = '2023'
    await wrapper.vm.loadData()
    await flushPromises()

    expect(mockGetMySchedule).toHaveBeenCalledWith(
      expect.objectContaining({ academicYear: '2023' })
    )
  })

  it('修改 semester 后 loadData 传入新参数', async () => {
    const wrapper = createWrapper()
    await flushPromises()
    mockGetMySchedule.mockClear()

    wrapper.vm.semester = 2
    await wrapper.vm.loadData()
    await flushPromises()

    expect(mockGetMySchedule).toHaveBeenCalledWith(
      expect.objectContaining({ semester: 2 })
    )
  })

  // --- 空数据状态 ---
  it('无数据时 schedules 长度为 0', async () => {
    mockGetMySchedule.mockResolvedValue({ data: [] })
    const wrapper = createWrapper()
    await flushPromises()
    expect(wrapper.vm.schedules).toHaveLength(0)
  })
})
