import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { defineComponent } from 'vue'
import { useSsoLogin } from '@/composables/useSsoLogin'
import * as ssoApi from '@/api/sso'

// Mock API
vi.mock('@/api/sso', () => ({
  getSsoStatus: vi.fn(),
  getSsoLoginUrl: vi.fn()
}))

// Mock window.location
delete window.location
window.location = { href: '' }

// Mock alert
global.alert = vi.fn()

// Helper to create a test component that uses the composable
const createTestComponent = () => {
  return defineComponent({
    setup() {
      return useSsoLogin()
    },
    template: '<div></div>'
  })
}

describe('useSsoLogin', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    window.location.href = ''
  })

  it('should check SSO status on mount', async () => {
    ssoApi.getSsoStatus.mockResolvedValue({
      code: 200,
      data: { enabled: true }
    })

    const TestComponent = createTestComponent()
    const wrapper = mount(TestComponent)
    await flushPromises()

    expect(ssoApi.getSsoStatus).toHaveBeenCalled()
    expect(wrapper.vm.ssoEnabled).toBe(true)
  })

  it('should set ssoEnabled to false when SSO is disabled', async () => {
    ssoApi.getSsoStatus.mockResolvedValue({
      code: 200,
      data: { enabled: false }
    })

    const TestComponent = createTestComponent()
    const wrapper = mount(TestComponent)
    await flushPromises()

    expect(wrapper.vm.ssoEnabled).toBe(false)
  })

  it('should handle SSO status check failure', async () => {
    ssoApi.getSsoStatus.mockRejectedValue(new Error('Network error'))

    const TestComponent = createTestComponent()
    const wrapper = mount(TestComponent)
    await flushPromises()

    expect(wrapper.vm.ssoEnabled).toBe(false)
  })

  it('should redirect to SSO login URL on handleSsoLogin', async () => {
    ssoApi.getSsoStatus.mockResolvedValue({
      code: 200,
      data: { enabled: true }
    })
    ssoApi.getSsoLoginUrl.mockResolvedValue({
      code: 200,
      data: { loginUrl: 'https://ids.imau.edu.cn/authserver/login?service=...' }
    })

    const TestComponent = createTestComponent()
    const wrapper = mount(TestComponent)
    await flushPromises()

    await wrapper.vm.handleSsoLogin()
    await flushPromises()

    expect(ssoApi.getSsoLoginUrl).toHaveBeenCalled()
    expect(window.location.href).toBe('https://ids.imau.edu.cn/authserver/login?service=...')
  })

  it('should show alert when SSO login URL fetch fails', async () => {
    ssoApi.getSsoStatus.mockResolvedValue({
      code: 200,
      data: { enabled: true }
    })
    ssoApi.getSsoLoginUrl.mockResolvedValue({
      code: 400,
      message: '获取登录链接失败'
    })

    const TestComponent = createTestComponent()
    const wrapper = mount(TestComponent)
    await flushPromises()

    await wrapper.vm.handleSsoLogin()
    await flushPromises()

    expect(global.alert).toHaveBeenCalledWith('获取登录链接失败')
  })

  it('should handle network error during SSO login', async () => {
    ssoApi.getSsoStatus.mockResolvedValue({
      code: 200,
      data: { enabled: true }
    })
    ssoApi.getSsoLoginUrl.mockRejectedValue(new Error('Network error'))

    const TestComponent = createTestComponent()
    const wrapper = mount(TestComponent)
    await flushPromises()

    await wrapper.vm.handleSsoLogin()
    await flushPromises()

    expect(global.alert).toHaveBeenCalledWith('获取登录链接失败')
  })

  it('should set ssoLoading during SSO login', async () => {
    ssoApi.getSsoStatus.mockResolvedValue({
      code: 200,
      data: { enabled: true }
    })
    ssoApi.getSsoLoginUrl.mockImplementation(() => {
      return new Promise(resolve => {
        setTimeout(() => {
          resolve({
            code: 200,
            data: { loginUrl: 'https://ids.imau.edu.cn/authserver/login?service=...' }
          })
        }, 100)
      })
    })

    const TestComponent = createTestComponent()
    const wrapper = mount(TestComponent)
    await flushPromises()

    const loginPromise = wrapper.vm.handleSsoLogin()
    expect(wrapper.vm.ssoLoading).toBe(true)

    await loginPromise
    await flushPromises()

    expect(wrapper.vm.ssoLoading).toBe(false)
  })
})
