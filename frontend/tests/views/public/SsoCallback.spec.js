import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createRouter, createMemoryHistory } from 'vue-router'
import { createPinia, setActivePinia } from 'pinia'
import SsoCallback from '@/views/public/SsoCallback.vue'
import { useUserStore } from '@/store/user'
import * as ssoApi from '@/api/sso'

// Mock API
vi.mock('@/api/sso', () => ({
  ssoCallback: vi.fn()
}))

// Mock router
const mockPush = vi.fn()
const mockRoute = {
  query: {
    ticket: 'ST-123-abc'
  }
}

vi.mock('vue-router', async () => {
  const actual = await vi.importActual('vue-router')
  return {
    ...actual,
    useRouter: () => ({
      push: mockPush
    }),
    useRoute: () => mockRoute
  }
})

describe('SsoCallback.vue', () => {
  let wrapper
  let userStore

  beforeEach(() => {
    setActivePinia(createPinia())
    userStore = useUserStore()
    mockPush.mockClear()
    vi.clearAllMocks()
    // Reset mockRoute to default
    mockRoute.query = { ticket: 'ST-123-abc' }
  })

  it('should handle successful SSO callback with CAS ticket', async () => {
    const mockResponse = {
      code: 200,
      data: {
        token: 'mock-jwt-token',
        user: {
          id: 1,
          username: '2023122066164',
          role: 'STUDENT',
          realName: '张三'
        }
      }
    }

    ssoApi.ssoCallback.mockResolvedValue(mockResponse)

    wrapper = mount(SsoCallback)
    await flushPromises()

    expect(ssoApi.ssoCallback).toHaveBeenCalledWith({ ticket: 'ST-123-abc' })
    expect(userStore.token).toBe('mock-jwt-token')
    expect(userStore.userRole).toBe('STUDENT')
    expect(mockPush).toHaveBeenCalledWith('/student')
  })

  it('should redirect to admin page for ADMIN role', async () => {
    const mockResponse = {
      code: 200,
      data: {
        token: 'admin-token',
        user: {
          id: 1,
          username: 'admin',
          role: 'ADMIN'
        }
      }
    }

    ssoApi.ssoCallback.mockResolvedValue(mockResponse)

    wrapper = mount(SsoCallback)
    await flushPromises()

    expect(mockPush).toHaveBeenCalledWith('/admin')
  })

  it('should redirect to ops page for OPS role', async () => {
    const mockResponse = {
      code: 200,
      data: {
        token: 'ops-token',
        user: {
          id: 1,
          username: 'ops',
          role: 'OPS'
        }
      }
    }

    ssoApi.ssoCallback.mockResolvedValue(mockResponse)

    wrapper = mount(SsoCallback)
    await flushPromises()

    expect(mockPush).toHaveBeenCalledWith('/ops')
  })

  it('should redirect to teacher page for TEACHER role', async () => {
    const mockResponse = {
      code: 200,
      data: {
        token: 'teacher-token',
        user: {
          id: 1,
          username: 'teacher',
          role: 'TEACHER'
        }
      }
    }

    ssoApi.ssoCallback.mockResolvedValue(mockResponse)

    wrapper = mount(SsoCallback)
    await flushPromises()

    expect(mockPush).toHaveBeenCalledWith('/teacher')
  })

  it('should redirect to user page for USER role', async () => {
    const mockResponse = {
      code: 200,
      data: {
        token: 'user-token',
        user: {
          id: 1,
          username: 'user',
          role: 'USER'
        }
      }
    }

    ssoApi.ssoCallback.mockResolvedValue(mockResponse)

    wrapper = mount(SsoCallback)
    await flushPromises()

    expect(mockPush).toHaveBeenCalledWith('/user')
  })

  it('should display error message when callback fails', async () => {
    const mockError = {
      code: 400,
      message: '您的账号正在审批中，请联系管理员'
    }

    ssoApi.ssoCallback.mockResolvedValue(mockError)

    wrapper = mount(SsoCallback)
    await flushPromises()

    expect(wrapper.find('.error').exists()).toBe(true)
    expect(wrapper.find('.error p').text()).toBe('您的账号正在审批中，请联系管理员')
  })

  it('should display error when missing authentication parameters', async () => {
    mockRoute.query = {}

    wrapper = mount(SsoCallback)
    await flushPromises()

    expect(wrapper.find('.error').exists()).toBe(true)
    expect(wrapper.find('.error p').text()).toContain('缺少认证参数')
  })

  it('should handle network errors gracefully', async () => {
    ssoApi.ssoCallback.mockRejectedValue(new Error('Network error'))

    wrapper = mount(SsoCallback)
    await flushPromises()

    expect(wrapper.find('.error').exists()).toBe(true)
  })

  it('should show loading spinner initially', () => {
    wrapper = mount(SsoCallback)
    expect(wrapper.find('.loading').exists()).toBe(true)
    expect(wrapper.find('.spinner').exists()).toBe(true)
  })

  it('should allow user to return to login page on error', async () => {
    ssoApi.ssoCallback.mockRejectedValue(new Error('Login failed'))

    wrapper = mount(SsoCallback)
    await flushPromises()

    const button = wrapper.find('.error button')
    expect(button.exists()).toBe(true)

    await button.trigger('click')
    expect(mockPush).toHaveBeenCalledWith('/login')
  })
})
