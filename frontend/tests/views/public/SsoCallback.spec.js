import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import SsoCallback from '@/views/public/SsoCallback.vue'
import { useUserStore } from '@/store/user'
import * as ssoApi from '@/api/sso'

// Mock API
vi.mock('@/api/sso', () => ({
  exchangeSsoToken: vi.fn()
}))

// Mock router
const mockPush = vi.fn()
const mockRoute = {
  query: {
    code: 'test-one-time-code'
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

// Helper: create a fake JWT with given role
function createFakeJwt(role, username = 'testuser') {
  const header = btoa(JSON.stringify({ alg: 'HS256', typ: 'JWT' }))
  const payload = btoa(JSON.stringify({ sub: username, role }))
  const signature = btoa('fake-signature')
  return `${header}.${payload}.${signature}`
}

describe('SsoCallback.vue', () => {
  let wrapper
  let userStore

  beforeEach(() => {
    setActivePinia(createPinia())
    userStore = useUserStore()
    mockPush.mockClear()
    vi.clearAllMocks()
    // Reset mockRoute to default
    mockRoute.query = { code: 'test-one-time-code' }
  })

  it('should handle successful SSO token exchange and redirect STUDENT', async () => {
    const fakeJwt = createFakeJwt('STUDENT', '2023122066164')
    const mockResponse = {
      code: 200,
      data: {
        token: fakeJwt
      }
    }

    ssoApi.exchangeSsoToken.mockResolvedValue(mockResponse)

    wrapper = mount(SsoCallback)
    await flushPromises()

    expect(ssoApi.exchangeSsoToken).toHaveBeenCalledWith('test-one-time-code')
    expect(userStore.token).toBe(fakeJwt)
    expect(userStore.userRole).toBe('STUDENT')
    expect(mockPush).toHaveBeenCalledWith('/student')
  })

  it('should redirect to admin page for ADMIN role', async () => {
    const fakeJwt = createFakeJwt('ADMIN', 'admin')
    const mockResponse = {
      code: 200,
      data: {
        token: fakeJwt
      }
    }

    ssoApi.exchangeSsoToken.mockResolvedValue(mockResponse)

    wrapper = mount(SsoCallback)
    await flushPromises()

    expect(mockPush).toHaveBeenCalledWith('/admin')
  })

  it('should redirect to ops page for OPS role', async () => {
    const fakeJwt = createFakeJwt('OPS', 'ops')
    const mockResponse = {
      code: 200,
      data: {
        token: fakeJwt
      }
    }

    ssoApi.exchangeSsoToken.mockResolvedValue(mockResponse)

    wrapper = mount(SsoCallback)
    await flushPromises()

    expect(mockPush).toHaveBeenCalledWith('/ops')
  })

  it('should redirect to teacher page for TEACHER role', async () => {
    const fakeJwt = createFakeJwt('TEACHER', 'teacher')
    const mockResponse = {
      code: 200,
      data: {
        token: fakeJwt
      }
    }

    ssoApi.exchangeSsoToken.mockResolvedValue(mockResponse)

    wrapper = mount(SsoCallback)
    await flushPromises()

    expect(mockPush).toHaveBeenCalledWith('/teacher')
  })

  it('should redirect to user page for USER role', async () => {
    const fakeJwt = createFakeJwt('USER', 'user')
    const mockResponse = {
      code: 200,
      data: {
        token: fakeJwt
      }
    }

    ssoApi.exchangeSsoToken.mockResolvedValue(mockResponse)

    wrapper = mount(SsoCallback)
    await flushPromises()

    expect(mockPush).toHaveBeenCalledWith('/user')
  })

  it('should display error message when token exchange fails (pending approval)', async () => {
    const mockError = {
      code: 400,
      message: '您的账号正在审批中，请联系管理员'
    }

    ssoApi.exchangeSsoToken.mockResolvedValue(mockError)

    wrapper = mount(SsoCallback)
    await flushPromises()

    expect(wrapper.find('.error').exists()).toBe(true)
    expect(wrapper.find('.error p').text()).toBe('您的账号正在审批中，请联系管理员')
  })

  it('should display error when missing code parameter', async () => {
    mockRoute.query = {}

    wrapper = mount(SsoCallback)
    await flushPromises()

    expect(wrapper.find('.error').exists()).toBe(true)
    expect(wrapper.find('.error p').text()).toContain('缺少授权码')
  })

  it('should display error from backend redirect (query.error)', async () => {
    mockRoute.query = { error: 'SSO%20%E8%AE%A4%E8%AF%81%E5%A4%B1%E8%B4%A5' }

    wrapper = mount(SsoCallback)
    await flushPromises()

    expect(wrapper.find('.error').exists()).toBe(true)
  })

  it('should handle network errors gracefully', async () => {
    ssoApi.exchangeSsoToken.mockRejectedValue(new Error('Network error'))

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
    ssoApi.exchangeSsoToken.mockRejectedValue(new Error('Login failed'))

    wrapper = mount(SsoCallback)
    await flushPromises()

    const button = wrapper.find('.error button')
    expect(button.exists()).toBe(true)

    await button.trigger('click')
    expect(mockPush).toHaveBeenCalledWith('/login')
  })
})
