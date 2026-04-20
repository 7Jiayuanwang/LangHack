/**
 * PermissionDenied 组件测试
 *
 * 覆盖：
 *   - 默认 props 渲染（联系电话 43019235，部门计算机与信息工程学院）
 *   - 不同 theme 切换 CSS class 和图标颜色
 *   - rawMessage 按 showRawMessage 控制展示
 *   - 点击"返回登录"/"重新登录"按钮跳转 /login
 *
 * 运行：npx vitest run tests/components/PermissionDenied.spec.js
 */

import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import PermissionDenied from '@/components/PermissionDenied.vue'

const mockPush = vi.fn()

vi.mock('vue-router', async () => {
  const actual = await vi.importActual('vue-router')
  return {
    ...actual,
    useRouter: () => ({
      push: mockPush
    })
  }
})

describe('PermissionDenied', () => {
  beforeEach(() => {
    mockPush.mockClear()
    localStorage.clear()
  })

  it('使用默认 props 渲染温馨提示和主管部门联系方式', () => {
    const wrapper = mount(PermissionDenied)

    expect(wrapper.text()).toContain('温馨提示')
    expect(wrapper.text()).toContain('对不起，您无权使用LangHack')
    expect(wrapper.find('.contact-phone').text()).toBe('43019235')
    expect(wrapper.find('.contact-department').text()).toBe('计算机与信息工程学院')
  })

  it('theme=warning 应用 theme-warning 类', () => {
    const wrapper = mount(PermissionDenied, { props: { theme: 'warning' } })
    expect(wrapper.find('.permission-denied').classes()).toContain('theme-warning')
  })

  it('theme=info 应用 theme-info 类', () => {
    const wrapper = mount(PermissionDenied, { props: { theme: 'info' } })
    expect(wrapper.find('.permission-denied').classes()).toContain('theme-info')
  })

  it('theme=error 应用 theme-error 类', () => {
    const wrapper = mount(PermissionDenied, { props: { theme: 'error' } })
    expect(wrapper.find('.permission-denied').classes()).toContain('theme-error')
  })

  it('提供 rawMessage 时展示原始错误详情', () => {
    const wrapper = mount(PermissionDenied, {
      props: { rawMessage: 'SSO 认证失败: 用户不存在' }
    })
    expect(wrapper.find('.raw-message').exists()).toBe(true)
    expect(wrapper.find('.raw-message').text()).toContain('用户不存在')
  })

  it('showRawMessage=false 时不展示错误详情', () => {
    const wrapper = mount(PermissionDenied, {
      props: { rawMessage: 'Something', showRawMessage: false }
    })
    expect(wrapper.find('.raw-message').exists()).toBe(false)
  })

  it('rawMessage 为空时不渲染错误详情块', () => {
    const wrapper = mount(PermissionDenied, { props: { rawMessage: '' } })
    expect(wrapper.find('.raw-message').exists()).toBe(false)
  })

  it('点击"返回登录"跳转 /login 并 emit close', async () => {
    const wrapper = mount(PermissionDenied)
    await wrapper.find('.btn-back').trigger('click')

    expect(mockPush).toHaveBeenCalledWith('/login')
    expect(wrapper.emitted('close')).toBeTruthy()
  })

  it('点击"重新登录"清理 localStorage 并跳转 /login', async () => {
    localStorage.setItem('token', 'stale-token')
    localStorage.setItem('user', '{}')

    const wrapper = mount(PermissionDenied)
    await wrapper.find('.btn-retry').trigger('click')

    expect(localStorage.getItem('token')).toBeNull()
    expect(localStorage.getItem('user')).toBeNull()
    expect(mockPush).toHaveBeenCalledWith('/login')
  })

  it('自定义 contactPhone 和 contactDepartment 覆盖默认值', () => {
    const wrapper = mount(PermissionDenied, {
      props: {
        contactPhone: '12345678',
        contactDepartment: '其他学院'
      }
    })
    expect(wrapper.find('.contact-phone').text()).toBe('12345678')
    expect(wrapper.find('.contact-department').text()).toBe('其他学院')
  })
})
