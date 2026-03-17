import { describe, it, expect, vi, beforeEach } from 'vitest'
import { shallowMount } from '@vue/test-utils'

const mockPush = vi.fn()
const mockLogin = vi.fn()
const mockSetUserInfo = vi.fn()

vi.mock('vue-router', () => ({
  useRouter: () => ({ push: mockPush }),
  useRoute: () => ({ query: {} })
}))

vi.mock('@/store/user', () => ({
  useUserStore: () => ({
    login: mockLogin,
    setUserInfo: mockSetUserInfo
  })
}))

vi.mock('@/utils/request', () => ({
  default: vi.fn()
}))

vi.mock('element-plus', () => ({
  ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn(), info: vi.fn() }
}))

import Login from '@/views/Login.vue'
import request from '@/utils/request'
import { ElMessage } from 'element-plus'

describe('Login.vue', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('renders login form by default', () => {
    const wrapper = shallowMount(Login)
    expect(wrapper.find('.trend-login').exists()).toBe(true)
  })

  it('has login and register tabs', () => {
    const wrapper = shallowMount(Login)
    const tabs = wrapper.findAll('.trend-tab')
    expect(tabs.length).toBe(2)
  })

  it('switches to register tab', async () => {
    const wrapper = shallowMount(Login)
    const tabs = wrapper.findAll('.trend-tab')
    await tabs[1].trigger('click')
    expect(wrapper.vm.activeTab).toBe('register')
  })

  it('switches back to login tab', async () => {
    const wrapper = shallowMount(Login)
    wrapper.vm.activeTab = 'register'
    const tabs = wrapper.findAll('.trend-tab')
    await tabs[0].trigger('click')
    expect(wrapper.vm.activeTab).toBe('login')
  })

  it('tabIndicatorStyle changes with activeTab', () => {
    const wrapper = shallowMount(Login)
    expect(wrapper.vm.tabIndicatorStyle.transform).toContain('translateX(0)')
    wrapper.vm.activeTab = 'register'
    expect(wrapper.vm.tabIndicatorStyle.transform).toContain('translateX(100%)')
  })

  it('handleLogin calls store login on success', async () => {
    mockLogin.mockResolvedValue({ token: 'abc', role: 'USER', username: 'test' })
    const wrapper = shallowMount(Login)
    wrapper.vm.loginForm.username = 'testuser'
    wrapper.vm.loginForm.password = 'password123'
    await wrapper.vm.handleLogin()
    expect(mockLogin).toHaveBeenCalledWith({
      username: 'testuser',
      password: 'password123'
    })
  })

  it('handleLogin shows error on empty username', async () => {
    const wrapper = shallowMount(Login)
    wrapper.vm.loginForm.username = ''
    wrapper.vm.loginForm.password = 'password123'
    await wrapper.vm.handleLogin()
    expect(ElMessage.warning).toHaveBeenCalled()
  })

  it('handleLogin shows error on empty password', async () => {
    const wrapper = shallowMount(Login)
    wrapper.vm.loginForm.username = 'testuser'
    wrapper.vm.loginForm.password = ''
    await wrapper.vm.handleLogin()
    expect(ElMessage.warning).toHaveBeenCalled()
  })

  it('handleLogin navigates to dashboard on success', async () => {
    mockLogin.mockResolvedValue({ token: 'abc', role: 'USER', username: 'test' })
    const wrapper = shallowMount(Login)
    wrapper.vm.loginForm.username = 'testuser'
    wrapper.vm.loginForm.password = 'password123'
    await wrapper.vm.handleLogin()
    expect(mockPush).toHaveBeenCalled()
  })

  it('handleLogin shows error message on failure', async () => {
    mockLogin.mockRejectedValue(new Error('Invalid credentials'))
    const wrapper = shallowMount(Login)
    wrapper.vm.loginForm.username = 'testuser'
    wrapper.vm.loginForm.password = 'password123'
    await wrapper.vm.handleLogin()
    expect(ElMessage.error).toHaveBeenCalled()
  })

  it('handleRegister calls request with correct data', async () => {
    request.mockResolvedValue({ code: 200 })
    const wrapper = shallowMount(Login)
    wrapper.vm.activeTab = 'register'
    wrapper.vm.registerForm.username = 'newuser'
    wrapper.vm.registerForm.password = 'password123'
    wrapper.vm.registerForm.confirmPassword = 'password123'
    wrapper.vm.registerForm.realName = 'Test User'
    wrapper.vm.registerForm.email = 'test@example.com'
    await wrapper.vm.handleRegister()
    expect(request).toHaveBeenCalled()
  })

  it('handleRegister shows error on password mismatch', async () => {
    const wrapper = shallowMount(Login)
    wrapper.vm.registerForm.username = 'newuser'
    wrapper.vm.registerForm.password = 'password123'
    wrapper.vm.registerForm.confirmPassword = 'different'
    await wrapper.vm.handleRegister()
    expect(ElMessage.warning).toHaveBeenCalled()
  })

  it('fillDemo fills login form with demo credentials', () => {
    const wrapper = shallowMount(Login)
    wrapper.vm.fillDemo('admin')
    expect(wrapper.vm.loginForm.username).toBe('admin')
    expect(wrapper.vm.loginForm.password).toBeTruthy()
  })

  it('loginLoading is false initially', () => {
    const wrapper = shallowMount(Login)
    expect(wrapper.vm.loginLoading).toBe(false)
  })

  it('registerLoading is false initially', () => {
    const wrapper = shallowMount(Login)
    expect(wrapper.vm.registerLoading).toBe(false)
  })
})
