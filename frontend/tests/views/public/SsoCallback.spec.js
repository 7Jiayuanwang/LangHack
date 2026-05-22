import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import SsoCallback from '@/views/public/SsoCallback.vue'
import PermissionDenied from '@/components/PermissionDenied.vue'
import { useUserStore } from '@/store/user'
import * as ssoApi from '@/api/sso'

// Mock API
vi.mock('@/api/sso', () => ({
  exchangeSsoToken: vi.fn()
}))

// Mock router
const mockPush = vi.fn()
const mockReplace = vi.fn()
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
      push: mockPush,
      replace: mockReplace
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
    mockReplace.mockClear()
    vi.clearAllMocks()
    sessionStorage.clear()
    mockRoute.query = { code: 'test-one-time-code' }
  })

  // ===== 成功路径 =====

  it('成功兑换 token 时，STUDENT 跳转 /student', async () => {
    const fakeJwt = createFakeJwt('STUDENT', '2023122066164')
    ssoApi.exchangeSsoToken.mockResolvedValue({ code: 200, data: { token: fakeJwt } })

    wrapper = mount(SsoCallback)
    await flushPromises()

    expect(ssoApi.exchangeSsoToken).toHaveBeenCalledWith('test-one-time-code')
    expect(userStore.token).toBe(fakeJwt)
    expect(userStore.userRole).toBe('STUDENT')
    expect(mockReplace).toHaveBeenCalledWith('/student')
  })

  it('ADMIN 角色跳转 /admin', async () => {
    const fakeJwt = createFakeJwt('ADMIN', 'admin')
    ssoApi.exchangeSsoToken.mockResolvedValue({ code: 200, data: { token: fakeJwt } })

    wrapper = mount(SsoCallback)
    await flushPromises()

    expect(mockReplace).toHaveBeenCalledWith('/admin')
  })

  it('OPS 角色跳转 /ops', async () => {
    const fakeJwt = createFakeJwt('OPS', 'ops')
    ssoApi.exchangeSsoToken.mockResolvedValue({ code: 200, data: { token: fakeJwt } })

    wrapper = mount(SsoCallback)
    await flushPromises()

    expect(mockReplace).toHaveBeenCalledWith('/ops')
  })

  it('TEACHER 角色跳转 /teacher', async () => {
    const fakeJwt = createFakeJwt('TEACHER', 'teacher')
    ssoApi.exchangeSsoToken.mockResolvedValue({ code: 200, data: { token: fakeJwt } })

    wrapper = mount(SsoCallback)
    await flushPromises()

    expect(mockReplace).toHaveBeenCalledWith('/teacher')
  })

  it('USER 角色在校园环境中跳转 /student', async () => {
    const fakeJwt = createFakeJwt('USER', 'user')
    ssoApi.exchangeSsoToken.mockResolvedValue({ code: 200, data: { token: fakeJwt } })

    wrapper = mount(SsoCallback)
    await flushPromises()

    expect(mockReplace).toHaveBeenCalledWith('/student')
  })

  // ===== 错误路径：统一走 PermissionDenied =====

  it('加载中显示 spinner', () => {
    wrapper = mount(SsoCallback)
    expect(wrapper.find('.loading').exists()).toBe(true)
    expect(wrapper.find('.spinner').exists()).toBe(true)
  })

  it('后端 errorCode=USER_NOT_FOUND 时显示无权使用 + 联系方式', async () => {
    mockRoute.query = {
      error: encodeURIComponent('SSO 认证失败: 用户不存在'),
      errorCode: 'USER_NOT_FOUND'
    }

    wrapper = mount(SsoCallback)
    await flushPromises()

    const denied = wrapper.findComponent(PermissionDenied)
    expect(denied.exists()).toBe(true)
    expect(denied.props('mainMessage')).toContain('无权使用')
    expect(denied.props('contactPhone')).toBe('43019235')
    expect(denied.props('contactDepartment')).toBe('计算机与信息工程学院')
    expect(denied.props('theme')).toBe('warning')
  })

  it('后端 errorCode=PENDING_APPROVAL 时使用 info 主题', async () => {
    mockRoute.query = { errorCode: 'PENDING_APPROVAL' }

    wrapper = mount(SsoCallback)
    await flushPromises()

    const denied = wrapper.findComponent(PermissionDenied)
    expect(denied.props('theme')).toBe('info')
    expect(denied.props('mainMessage')).toContain('审批')
  })

  it('未提供 errorCode 时回退到关键字匹配', async () => {
    mockRoute.query = {
      error: encodeURIComponent('SSO 认证失败: 用户不存在')
    }

    wrapper = mount(SsoCallback)
    await flushPromises()

    const denied = wrapper.findComponent(PermissionDenied)
    expect(denied.exists()).toBe(true)
    // 关键字匹配到 USER_NOT_FOUND
    expect(denied.props('mainMessage')).toContain('无权使用')
  })

  it('缺少 code 参数时显示 MISSING_PARAM 错误', async () => {
    mockRoute.query = {}

    wrapper = mount(SsoCallback)
    await flushPromises()

    const denied = wrapper.findComponent(PermissionDenied)
    expect(denied.exists()).toBe(true)
    expect(denied.props('theme')).toBe('error')
  })

  it('token 兑换返回业务错误时透传后端 errorCode', async () => {
    ssoApi.exchangeSsoToken.mockResolvedValue({
      code: 500,
      message: '授权码无效或已过期，请重新登录',
      data: { errorCode: 'CODE_EXPIRED' }
    })

    wrapper = mount(SsoCallback)
    await flushPromises()

    const denied = wrapper.findComponent(PermissionDenied)
    expect(denied.exists()).toBe(true)
    expect(denied.props('theme')).toBe('info')
    expect(denied.props('mainMessage')).toContain('过期')
  })

  it('网络异常时显示 SYSTEM_ERROR 的友好提示', async () => {
    ssoApi.exchangeSsoToken.mockRejectedValue(new Error('Network error'))

    wrapper = mount(SsoCallback)
    await flushPromises()

    const denied = wrapper.findComponent(PermissionDenied)
    expect(denied.exists()).toBe(true)
    expect(denied.props('theme')).toBe('error')
    expect(denied.props('contactPhone')).toBe('43019235')
  })

  it('登录成功但角色非法时显示系统错误', async () => {
    const fakeJwt = createFakeJwt('NOT_A_ROLE', 'bob')
    ssoApi.exchangeSsoToken.mockResolvedValue({ code: 200, data: { token: fakeJwt } })

    wrapper = mount(SsoCallback)
    await flushPromises()

    const denied = wrapper.findComponent(PermissionDenied)
    expect(denied.exists()).toBe(true)
    expect(denied.props('theme')).toBe('error')
  })

  // ===== SSO 登录后返回原始受保护页面 =====

  it('有保存的重定向路径时，STUDENT 优先跳转到保存的路径', async () => {
    const fakeJwt = createFakeJwt('STUDENT', '2023122066164')
    ssoApi.exchangeSsoToken.mockResolvedValue({ code: 200, data: { token: fakeJwt } })

    sessionStorage.setItem('sso_redirect_after_login', '/articles')
    wrapper = mount(SsoCallback)
    await flushPromises()

    expect(mockReplace).toHaveBeenCalledWith('/articles')
    expect(sessionStorage.getItem('sso_redirect_after_login')).toBeNull()
  })

  it('有保存的重定向路径时，ADMIN 优先跳转到保存的路径而非 /admin', async () => {
    const fakeJwt = createFakeJwt('ADMIN', 'admin')
    ssoApi.exchangeSsoToken.mockResolvedValue({ code: 200, data: { token: fakeJwt } })

    sessionStorage.setItem('sso_redirect_after_login', '/admin/users')
    wrapper = mount(SsoCallback)
    await flushPromises()

    expect(mockReplace).toHaveBeenCalledWith('/admin/users')
    expect(mockReplace).not.toHaveBeenCalledWith('/admin')
  })

  it('无保存路径时按原角色逻辑跳转', async () => {
    const fakeJwt = createFakeJwt('TEACHER', 'teacher')
    ssoApi.exchangeSsoToken.mockResolvedValue({ code: 200, data: { token: fakeJwt } })

    // 确保 sessionStorage 为空
    sessionStorage.removeItem('sso_redirect_after_login')
    wrapper = mount(SsoCallback)
    await flushPromises()

    expect(mockReplace).toHaveBeenCalledWith('/teacher')
  })

  it('重定向路径包含查询参数时仍正确跳转', async () => {
    const fakeJwt = createFakeJwt('STUDENT', '2023122066164')
    ssoApi.exchangeSsoToken.mockResolvedValue({ code: 200, data: { token: fakeJwt } })

    sessionStorage.setItem('sso_redirect_after_login', '/article/42?tab=vocabulary')
    wrapper = mount(SsoCallback)
    await flushPromises()

    expect(mockReplace).toHaveBeenCalledWith('/article/42?tab=vocabulary')
  })

  it('恶意路径（绝对 URL）被安全过滤，回退到角色首页', async () => {
    const fakeJwt = createFakeJwt('STUDENT', '2023122066164')
    ssoApi.exchangeSsoToken.mockResolvedValue({ code: 200, data: { token: fakeJwt } })

    sessionStorage.setItem('sso_redirect_after_login', 'https://evil.com')
    wrapper = mount(SsoCallback)
    await flushPromises()

    expect(mockReplace).toHaveBeenCalledWith('/student')
  })

  it('恶意路径（伪协议）被安全过滤，回退到角色首页', async () => {
    const fakeJwt = createFakeJwt('STUDENT', '2023122066164')
    ssoApi.exchangeSsoToken.mockResolvedValue({ code: 200, data: { token: fakeJwt } })

    sessionStorage.setItem('sso_redirect_after_login', 'javascript:alert(1)')
    wrapper = mount(SsoCallback)
    await flushPromises()

    expect(mockReplace).toHaveBeenCalledWith('/student')
  })

  it('恶意路径（空字符串）被安全过滤，回退到角色首页', async () => {
    const fakeJwt = createFakeJwt('STUDENT', '2023122066164')
    ssoApi.exchangeSsoToken.mockResolvedValue({ code: 200, data: { token: fakeJwt } })

    sessionStorage.setItem('sso_redirect_after_login', '')
    wrapper = mount(SsoCallback)
    await flushPromises()

    expect(mockReplace).toHaveBeenCalledWith('/student')
  })

  it('登录成功后 sessionStorage 中的重定向路径被清除', async () => {
    const fakeJwt = createFakeJwt('OPS', 'ops')
    ssoApi.exchangeSsoToken.mockResolvedValue({ code: 200, data: { token: fakeJwt } })

    sessionStorage.setItem('sso_redirect_after_login', '/ops/monitoring')
    wrapper = mount(SsoCallback)
    await flushPromises()

    expect(sessionStorage.getItem('sso_redirect_after_login')).toBeNull()
  })

  // ===== 错误路径 =====

  it('点击"返回登录"按钮跳转 /login', async () => {
    ssoApi.exchangeSsoToken.mockRejectedValue(new Error('Login failed'))

    wrapper = mount(SsoCallback)
    await flushPromises()

    const backBtn = wrapper.find('.btn-back')
    expect(backBtn.exists()).toBe(true)
    await backBtn.trigger('click')
    expect(mockPush).toHaveBeenCalledWith('/login')
  })
})
