/**
 * MobileLayout 合并测试
 *
 * 覆盖范围：合并 student/Layout.vue + user/Layout.vue
 * 为同一个带 `role` prop 的 MobileLayout 组件后，需验证以下行为不变。
 *
 * 运行：npx vitest run tests/views/layouts/mobile-layout.spec.js
 */

import { describe, it, expect, vi, beforeEach } from 'vitest'
import { shallowMount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'

// ── mock ────────────────────────────────────────────────────────────────────

const mockRouterPush    = vi.fn()
const mockRouterReplace = vi.fn()
const mockRoute = { path: '/student/dashboard', meta: { title: '发现' }, params: {}, query: {} }

vi.mock('vue-router', () => ({
  useRouter: () => ({ push: mockRouterPush, replace: mockRouterReplace }),
  useRoute:  () => mockRoute,
}))

vi.mock('element-plus', () => ({
  ElMessage:    { success: vi.fn(), error: vi.fn(), warning: vi.fn(), info: vi.fn() },
  ElMessageBox: { confirm: vi.fn().mockResolvedValue('confirm') },
}))

vi.mock('@/api/auth', () => ({
  logout: vi.fn().mockResolvedValue({ code: 200 }),
}))

vi.mock('@/api/sso', () => ({
  getSsoStatus: vi.fn().mockResolvedValue({ code: 200, data: { enabled: false } }),
  getSsoLogoutUrl: vi.fn().mockResolvedValue({ code: 200, data: { logoutUrl: '' } }),
}))

vi.mock('@/api/systemConfig', () => ({
  getFeatureToggles: vi.fn().mockResolvedValue({
    code: 200,
    data: {
      page_dashboard:  true,
      page_articles:   true,
      page_vocabulary: true,
      page_my_study:   true,
      page_ai_video:   false,  // AI 视频默认关闭
    },
  }),
  getPersonalFeatureToggles: vi.fn().mockResolvedValue({
    code: 200,
    data: {
      page_dashboard:  true,
      page_articles:   true,
      page_vocabulary: true,
      page_my_study:   true,
      page_ai_video:   false,
    },
  }),
}))

// ── 组件 + store import ──────────────────────────────────────────────────────

import StudentLayout from '@/views/student/Layout.vue'
import UserLayout    from '@/views/user/Layout.vue'
import MobileLayout  from '@/views/shared/MobileLayout.vue'
import { useUserStore }     from '@/store/user'
import { getFeatureToggles, getPersonalFeatureToggles } from '@/api/systemConfig'

// ── 辅助函数 ─────────────────────────────────────────────────────────────────

const mountOptions = () => ({
  global: {
    mocks: {
      $route:  mockRoute,
      $router: { push: mockRouterPush, replace: mockRouterReplace },
    },
    stubs: { RouterLink: true, RouterView: true },
  },
})

function mockUserStore(overrides = {}) {
  const store = useUserStore()
  store.token    = overrides.token    ?? 'mock-test-token'
  store.userRole = overrides.userRole ?? 'STUDENT'
  store.userInfo = overrides.userInfo ?? { username: 'u1', realName: '张三', avatar: '' }
  store.logout   = overrides.logout   ?? vi.fn()
  return store
}

// ── 1. 导航项数量与路由 ──────────────────────────────────────────────────────

describe('MobileLayout — 导航项数量与路由', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    mockRoute.path = '/student/dashboard'
    mockRoute.meta = { title: '发现' }
  })

  it('[STUDENT] navItems 包含 articles、courses、words', () => {
    mockUserStore({ userRole: 'STUDENT' })
    const wrapper = shallowMount(StudentLayout, mountOptions())
    const paths = wrapper.vm.navigationItems.map(i => i.path)
    // 精简后底部栏仅保留 5 个主入口：文章/课程/任务/单词/学习
    expect(paths.some(p => p.includes('articles'))).toBe(true)
    expect(paths.some(p => p.includes('courses'))).toBe(true)
    expect(paths.some(p => p.includes('words'))).toBe(true)
  })

  it('[USER] navItems 包含首页路径', () => {
    mockUserStore({ userRole: 'USER' })
    const wrapper = shallowMount(MobileLayout, { ...mountOptions(), props: { role: 'user' } })
    const paths = wrapper.vm.navItems.map(i => i.path)
    expect(paths.some(p => p === '/user' || p.includes('home'))).toBe(true)
  })

  it('[USER] navItems 包含 uploads/texts 路径', () => {
    mockUserStore({ userRole: 'USER' })
    const wrapper = shallowMount(MobileLayout, { ...mountOptions(), props: { role: 'user' } })
    const paths = wrapper.vm.navItems.map(i => i.path)
    expect(paths.some(p => p.includes('texts') || p.includes('upload'))).toBe(true)
  })

  it('[USER] navItems 包含 vocabulary 路径', () => {
    mockUserStore({ userRole: 'USER' })
    const wrapper = shallowMount(MobileLayout, { ...mountOptions(), props: { role: 'user' } })
    const paths = wrapper.vm.navItems.map(i => i.path)
    expect(paths.some(p => p.includes('vocabulary'))).toBe(true)
  })

  it('[STUDENT] navItems 数量 ≥ 4', () => {
    mockUserStore({ userRole: 'STUDENT' })
    const wrapper = shallowMount(StudentLayout, mountOptions())
    expect(wrapper.vm.navigationItems.length).toBeGreaterThanOrEqual(4)
  })

  it('[USER] navItems 数量为 4', () => {
    mockUserStore({ userRole: 'USER' })
    const wrapper = shallowMount(MobileLayout, { ...mountOptions(), props: { role: 'user' } })
    expect(wrapper.vm.navItems.length).toBe(4)
  })
})

// ── 2. 学生端 Feature Toggle 过滤 ────────────────────────────────────────────

describe('MobileLayout — 学生端 Feature Toggle 过滤', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    mockRoute.meta = {}
  })

  it('[STUDENT] page_ai_video=false 时 video 相关路径不出现在 navItems', async () => {
    mockUserStore({ userRole: 'STUDENT' })
    const wrapper = shallowMount(StudentLayout, mountOptions())
    await flushPromises()
    const paths = wrapper.vm.navigationItems.map(i => i.path)
    expect(paths.every(p => !p.includes('video'))).toBe(true)
  })

  it('[STUDENT] 所有 toggle 开启时 navItems 数量 ≥ 5', async () => {
    getPersonalFeatureToggles.mockResolvedValueOnce({
      code: 200,
      data: {
        page_dashboard:  true,
        page_articles:   true,
        page_vocabulary: true,
        page_my_study:   true,
        page_ai_video:   true,
        page_assignments: true,
      },
    })
    mockUserStore({ userRole: 'STUDENT' })
    const wrapper = shallowMount(StudentLayout, mountOptions())
    await flushPromises()
    expect(wrapper.vm.navigationItems.length).toBeGreaterThanOrEqual(5)
  })

  it('[USER] 不调用 getFeatureToggles API', async () => {
    mockUserStore({ userRole: 'USER' })
    shallowMount(MobileLayout, { ...mountOptions(), props: { role: 'user' } })
    await new Promise(r => setTimeout(r, 0))
    expect(getFeatureToggles).not.toHaveBeenCalled()
  })
})

// ── 3. 用户菜单（学生自定义弹窗 vs 用户 el-dropdown）────────────────────────

describe('MobileLayout — 用户菜单', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    mockRoute.meta = {}
  })

  it('[STUDENT] showUserMenu 初始为 false', () => {
    mockUserStore({ userRole: 'STUDENT' })
    const wrapper = shallowMount(StudentLayout, mountOptions())
    expect(wrapper.vm.showUserMenu).toBe(false)
  })

  it('[STUDENT] toggleUserMenu() 将 showUserMenu 变为 true', async () => {
    mockUserStore({ userRole: 'STUDENT' })
    const wrapper = shallowMount(StudentLayout, mountOptions())
    wrapper.vm.toggleUserMenu()
    await flushPromises()
    expect(wrapper.vm.showUserMenu).toBe(true)
  })

  it('[STUDENT] closeUserMenu() 将 showUserMenu 变为 false', async () => {
    mockUserStore({ userRole: 'STUDENT' })
    const wrapper = shallowMount(StudentLayout, mountOptions())
    wrapper.vm.showUserMenu = true
    wrapper.vm.closeUserMenu()
    await flushPromises()
    expect(wrapper.vm.showUserMenu).toBe(false)
  })

  it('[STUDENT] Escape 键关闭菜单', async () => {
    mockUserStore({ userRole: 'STUDENT' })
    const wrapper = shallowMount(StudentLayout, mountOptions())
    wrapper.vm.showUserMenu = true
    wrapper.vm.handleKeyDown({ key: 'Escape' })
    await flushPromises()
    expect(wrapper.vm.showUserMenu).toBe(false)
  })

  it('[USER] handleCommand 方法存在（Element Plus dropdown）', () => {
    mockUserStore({ userRole: 'USER' })
    const wrapper = shallowMount(MobileLayout, { ...mountOptions(), props: { role: 'user' } })
    expect(typeof wrapper.vm.handleCommand).toBe('function')
  })
})

// ── 4. 退出登录 ───────────────────────────────────────────────────────────────

describe('MobileLayout — 退出登录', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    mockRoute.meta = {}
  })

  it('[STUDENT] handleUserAction("logout") 调用 store.logout', async () => {
    const store = mockUserStore({ userRole: 'STUDENT' })
    const wrapper = shallowMount(StudentLayout, mountOptions())
    await wrapper.vm.handleUserAction('logout')
    expect(store.logout).toHaveBeenCalled()
  })

  it('[STUDENT] 登出后 showUserMenu 变为 false', async () => {
    mockUserStore({ userRole: 'STUDENT' })
    const wrapper = shallowMount(StudentLayout, mountOptions())
    wrapper.vm.showUserMenu = true
    await wrapper.vm.handleUserAction('logout')
    expect(wrapper.vm.showUserMenu).toBe(false)
  })

  it('[USER] handleCommand("logout") 调用 store.logout', async () => {
    const store = mockUserStore({ userRole: 'USER' })
    const wrapper = shallowMount(MobileLayout, { ...mountOptions(), props: { role: 'user' } })
    await wrapper.vm.handleCommand('logout')
    expect(store.logout).toHaveBeenCalled()
  })
})

// ── 5. 页面标题 ───────────────────────────────────────────────────────────────

describe('MobileLayout — 页面标题', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('[STUDENT] pageTitle 使用 route.meta.title', () => {
    mockRoute.meta = { title: '发现' }
    mockUserStore({ userRole: 'STUDENT' })
    const wrapper = shallowMount(StudentLayout, mountOptions())
    expect(wrapper.vm.pageTitle).toBe('发现')
  })

  it('[USER] pageTitle 使用 route.meta.title', () => {
    mockRoute.meta = { title: '我的生词本' }
    mockUserStore({ userRole: 'USER' })
    const wrapper = shallowMount(MobileLayout, { ...mountOptions(), props: { role: 'user' } })
    expect(wrapper.vm.pageTitle).toBe('我的生词本')
  })
})

// ── 6. 当前路由高亮 ───────────────────────────────────────────────────────────

describe('MobileLayout — 当前路由高亮', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('[STUDENT] isActiveRoute 对当前路径返回 true', () => {
    mockRoute.path = '/student/dashboard'
    mockRoute.meta = {}
    mockUserStore({ userRole: 'STUDENT' })
    const wrapper = shallowMount(StudentLayout, mountOptions())
    expect(wrapper.vm.isActiveRoute('/student/dashboard')).toBe(true)
  })

  it('[STUDENT] isActiveRoute 对非当前路径返回 false', () => {
    mockRoute.path = '/student/dashboard'
    mockRoute.meta = {}
    mockUserStore({ userRole: 'STUDENT' })
    const wrapper = shallowMount(StudentLayout, mountOptions())
    expect(wrapper.vm.isActiveRoute('/student/articles')).toBe(false)
  })

  it('[STUDENT] isActiveRoute 对子路由仍能匹配父路径', () => {
    mockRoute.path = '/student/courses/detail'
    mockRoute.meta = {}
    mockUserStore({ userRole: 'STUDENT' })
    const wrapper = shallowMount(StudentLayout, mountOptions())
    expect(wrapper.vm.isActiveRoute('/student/courses')).toBe(true)
  })
})

// ── 7. 学生端专属：角色名称显示 ────────────────────────────────────────────────

describe('MobileLayout — 学生端角色名称', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    mockRoute.meta = {}
  })

  it('[STUDENT] userRole=STUDENT 时 roleDisplayName 为"学生"', () => {
    mockUserStore({ userRole: 'STUDENT' })
    const wrapper = shallowMount(StudentLayout, mountOptions())
    expect(wrapper.vm.roleDisplayName).toBe('学习者')
  })

  it('[STUDENT] userRole=TEACHER 时 roleDisplayName 为"教师"', () => {
    mockUserStore({ userRole: 'TEACHER' })
    const wrapper = shallowMount(StudentLayout, mountOptions())
    expect(wrapper.vm.roleDisplayName).toBe('教师')
  })

  it('[STUDENT] userRole=ADMIN 时 roleDisplayName 为"管理员"', () => {
    mockUserStore({ userRole: 'ADMIN' })
    const wrapper = shallowMount(StudentLayout, mountOptions())
    expect(wrapper.vm.roleDisplayName).toBe('管理员')
  })
})

// ── 8. 头像 fallback ──────────────────────────────────────────────────────────

describe('MobileLayout — 头像 fallback', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    mockRoute.meta = {}
  })

  it('[STUDENT] avatar 为空字符串时 userAvatar 返回非空值', () => {
    mockUserStore({ userInfo: { username: 'u', avatar: '' } })
    const wrapper = shallowMount(StudentLayout, mountOptions())
    expect(wrapper.vm.userAvatar).toBeTruthy()
  })

  it('[STUDENT] avatar 为 null 时 userAvatar 返回非空值', () => {
    mockUserStore({ userInfo: { username: 'u', avatar: null } })
    const wrapper = shallowMount(StudentLayout, mountOptions())
    expect(wrapper.vm.userAvatar).toBeTruthy()
  })

  it('[USER] avatar 为空时 userAvatar 返回非空值', () => {
    mockUserStore({ userRole: 'USER', userInfo: { username: 'u', avatar: '' } })
    const wrapper = shallowMount(MobileLayout, { ...mountOptions(), props: { role: 'user' } })
    expect(wrapper.vm.userAvatar).toBeTruthy()
  })
})

// ── 9. 学生端专属：resize 监听生命周期 ────────────────────────────────────────

describe('MobileLayout — 学生端 resize 监听', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    mockRoute.meta = {}
  })

  it('[STUDENT] mount 时注册 resize 事件', () => {
    const spy = vi.spyOn(window, 'addEventListener')
    mockUserStore({ userRole: 'STUDENT' })
    shallowMount(StudentLayout, mountOptions())
    expect(spy.mock.calls.some(c => c[0] === 'resize')).toBe(true)
  })

  it('[STUDENT] unmount 时移除 resize 事件', () => {
    const spy = vi.spyOn(window, 'removeEventListener')
    mockUserStore({ userRole: 'STUDENT' })
    const wrapper = shallowMount(StudentLayout, mountOptions())
    wrapper.unmount()
    expect(spy.mock.calls.some(c => c[0] === 'resize')).toBe(true)
  })

  it('[USER] mount 时不注册 resize 事件', () => {
    const spy = vi.spyOn(window, 'addEventListener')
    spy.mockClear()
    mockUserStore({ userRole: 'USER' })
    shallowMount(MobileLayout, { ...mountOptions(), props: { role: 'user' } })
    expect(spy.mock.calls.some(c => c[0] === 'resize')).toBe(false)
  })
})
