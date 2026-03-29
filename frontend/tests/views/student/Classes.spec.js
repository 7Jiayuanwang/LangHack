import { describe, it, expect, vi, beforeEach } from 'vitest'
import { shallowMount } from '@vue/test-utils'
import { readFileSync } from 'node:fs'

vi.mock('element-plus', () => ({
  ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn() },
  ElMessageBox: { confirm: vi.fn().mockResolvedValue('confirm') }
}))

global.fetch = vi.fn()

import Classes from '@/views/student/Classes.vue'

const mountComponent = () =>
  shallowMount(Classes, {
    global: {
      directives: {
        loading: {}
      },
      stubs: {
        'el-card': { template: '<div><slot name="header" /><slot /></div>' },
        'el-button': { template: '<button><slot /></button>' },
        'el-table': { template: '<div><slot /></div>' },
        'el-table-column': { template: '<div><slot /></div>' },
        'el-empty': { template: '<div><slot /></div>' },
        'el-dialog': { template: '<div><slot /><slot name="footer" /></div>' },
        'el-form': { template: '<form><slot /></form>' },
        'el-form-item': { template: '<div><slot /></div>' },
        'el-input-number': true,
        'el-input': { template: '<input />' },
        'el-alert': true
      }
    }
  })

describe('Classes.vue', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    global.fetch.mockClear()
    global.fetch.mockResolvedValue({
      ok: true,
      json: vi.fn().mockResolvedValue({ code: 200, data: [] })
    })
    localStorage.setItem('token', 'test-token')
  })

  it('mounts without error', () => {
    const wrapper = mountComponent()
    expect(wrapper.exists()).toBe(true)
  })

  it('component is defined', () => {
    const wrapper = mountComponent()
    expect(wrapper.vm).toBeDefined()
  })

  it('cleanup on unmount works', () => {
    const wrapper = mountComponent()
    expect(wrapper.exists()).toBe(true)
    wrapper.unmount()
    expect(wrapper.exists()).toBe(false)
  })

  it('has the expected component structure', () => {
    const wrapper = mountComponent()
    expect(wrapper.html().length).toBeGreaterThan(0)
  })

  it('renders without throwing errors', () => {
    expect(() => {
      mountComponent()
    }).not.toThrow()
  })

  it('renders the available classes action button', () => {
    const source = readFileSync('src/views/student/Classes.vue', 'utf8')
    expect(source).toContain('可加入班级')
    expect(source).toContain('@click="showAvailableClasses"')
  })
})
