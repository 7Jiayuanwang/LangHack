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
    expect(mockPush).toHaveBeenCalledWith('/student')
  })

  it('ADMIN 角色跳转 /admin', async () => {
    const fakeJwt = createFakeJwt('ADMIN', 'admin')
    ssoApi.exchangeSsoToken.mockResolvedValue({ code: 200, data: { token: fakeJwt } })

    wrapper = mount(SsoCallback)
    await flushPromises()

    expect(mockPush).toHaveBeenCalledWith('/admin')
  })

  it('OPS 角色跳转 /ops', async () => {
    const fakeJwt = createFakeJwt('OPS', 'ops')
    ssoApi.exchangeSsoToken.mockResolvedValue({ code: 200, data: { token: fakeJwt } })

    wrapper = mount(SsoCallback)
    await flushPromises()

    expect(mockPush).toHaveBeenCalledWith('/ops')
  })

  it('TEACHER 角色跳转 /teacher', async () => {
    const fakeJwt = createFakeJwt('TEACHER', 'teacher')
    ssoApi.exchangeSsoToken.mockResolvedValue({ code: 200, data: { token: fakeJwt } })

    wrapper = mount(SsoCallback)
    await flushPromises()

    expect(mockPush).toHaveBeenCalledWith('/teacher')
  })

  it('USER 角色在校园环境中跳转 /student', async () => {
    const fakeJwt = createFakeJwt('USER', 'user')
    ssoApi.exchangeSsoToken.mockResolvedValue({ code: 200, data: { token: fakeJwt } })

    wrapper = mount(SsoCallback)
    await flushPromises()

    expect(mockPush).toHaveBeenCalledWith('/student')
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
    expect(denied.props('rawMessage')).toContain('缺少授权码')
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
    expect(denied.props('rawMessage')).toContain('角色异常')
  })

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
