import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, shallowMount } from '@vue/test-utils'

const getMyTeachingClasses = vi.hoisted(() => vi.fn())
const getMyPlatformClasses = vi.hoisted(() => vi.fn())
const routerPush = vi.hoisted(() => vi.fn())

vi.mock('@/api/teacher', () => ({ getMyTeachingClasses, getMyPlatformClasses }))
vi.mock('vue-router', () => ({ useRouter: () => ({ push: routerPush }) }))

import MyCourses from '@/views/teacher/MyCourses.vue'

describe('Teacher school teaching class to platform overview', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    getMyTeachingClasses.mockResolvedValue({ data: [{ id: 1, classId: 'KCBH-100', className: '大学英语一班' }] })
    getMyPlatformClasses.mockResolvedValue({ data: {
      records: [{ id: 42, schoolClassId: 'KCBH-100' }], pages: 1
    } })
  })

  it('opens the linked platform class when a teacher clicks their teaching class', async () => {
    const wrapper = shallowMount(MyCourses)
    await flushPromises()

    expect(getMyPlatformClasses).toHaveBeenCalledWith(1)
    expect(wrapper.vm.linkedClassId(wrapper.vm.courses[0])).toBe(42)
    wrapper.vm.openClassOverview(wrapper.vm.courses[0])
    expect(routerPush).toHaveBeenCalledWith('/teacher/classes/42')
  })

  it('does not guess a class link when school data has not been synced', async () => {
    getMyPlatformClasses.mockResolvedValue({ data: { records: [], pages: 1 } })
    const wrapper = shallowMount(MyCourses)
    await flushPromises()

    wrapper.vm.openClassOverview(wrapper.vm.courses[0])
    expect(routerPush).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('尚未同步到平台班级')
  })
})
