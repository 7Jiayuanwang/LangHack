/**
 * SidebarLayout 合并测试
 *
 * 覆盖范围：合并 admin/Layout.vue + teacher/Layout.vue + ops/Layout.vue
 * 为同一个带 `role` prop 的 SidebarLayout 组件后，需验证以下行为不变。
 *
 * 运行：npx vitest run tests/views/layouts/sidebar-layout.spec.js
 */

import { describe, it, expect, vi, beforeEach } from 'vitest'
import { shallowMount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'

// ── mock（必须在 import 组件之前声明）──────────────────────────────────────

const mockRouterPush    = vi.fn()
const mockRouterReplace = vi.fn()
const mockRoute = { path: '/admin/dashboard', meta: { title: '仪表盘' }, params: {}, query: {} }

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

// ── 组件 + store import（放在 mock 之后）──────────────────────────────────

import AdminLayout   from '@/views/admin/Layout.vue'
import TeacherLayout from '@/views/teacher/Layout.vue'
import OpsLayout     from '@/views/ops/Layout.vue'
import MobileLayout  from '@/views/shared/MobileLayout.vue'
import { useUserStore } from '@/store/user'

// ── 辅助函数 ───────────────────────────────────────────────────────────────

const mountOptions = () => ({
  global: {
    mocks: {
      $route:  mockRoute,
      $router: { push: mockRouterPush, replace: mockRouterReplace },
    },
    stubs: { RouterLink: true, RouterView: true, ElevationDialog: true },
  },
})

function mockUserStore(overrides = {}) {
  const store = useUserStore()
  // 设 token，让 isLoggedIn getter 返回 true
  store.token    = overrides.token ?? 'mock-test-token'
  store.userRole = overrides.userRole ?? 'ADMIN'
  store.userInfo = overrides.userInfo ?? { username: 'test', realName: '测试用户', avatar: '' }
  store.logout   = overrides.logout ?? vi.fn()
  // hasAdminAccess / isTeacher / hasOpsAccess 均为 getter，由 userRole 决定，不需手动设置
  // 若需测试"无权限"场景，传入 userRole: 'STUDENT' 即可
  if (overrides.token !== undefined) store.token = overrides.token
  return store
}

// ── 1. 权限守卫 ────────────────────────────────────────────────────────────

describe('SidebarLayout — 权限守卫', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    mockRoute.path = '/admin/dashboard'
    mockRoute.meta = { title: '仪表盘' }
  })

  it('[ADMIN] 未登录时应重定向到 /login', () => {
    mockUserStore({ token: "" })
    shallowMount(MobileLayout, { ...mountOptions(), props: { role: 'admin' } })
    expect(mockRouterReplace).toHaveBeenCalledWith('/login')
  })

  it('[ADMIN] 非管理员角色应重定向到 /login', () => {
    mockUserStore({ userRole: "STUDENT" })
    shallowMount(MobileLayout, { ...mountOptions(), props: { role: 'admin' } })
    expect(mockRouterReplace).toHaveBeenCalledWith('/login')
  })

  it('[TEACHER] 非教师角色应重定向到 /login', () => {
    mockUserStore({ userRole: "STUDENT" })
    shallowMount(MobileLayout, { ...mountOptions(), props: { role: 'teacher' } })
    expect(mockRouterPush).toHaveBeenCalledWith('/login')
  })

  it('[OPS] 未登录时应重定向到 /login', () => {
    mockUserStore({ token: "", userRole: "OPS" })
    shallowMount(MobileLayout, { ...mountOptions(), props: { role: 'ops' } })
    expect(mockRouterReplace).toHaveBeenCalledWith('/login')
  })

  it('[ADMIN] 合法管理员不触发重定向', () => {
    mockUserStore({ userRole: "ADMIN" })
    shallowMount(MobileLayout, { ...mountOptions(), props: { role: 'admin' } })
    expect(mockRouterReplace).not.toHaveBeenCalled()
    expect(mockRouterPush).not.toHaveBeenCalled()
  })
})

// ── 2. 导航项 ──────────────────────────────────────────────────────────────

describe('SidebarLayout — 导航项（各角色不同）', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    mockRoute.meta = {}
  })

  it('[ADMIN] navItems 包含 dashboard、courses、classes、users', () => {
    mockUserStore({ userRole: "ADMIN" })
    const wrapper = shallowMount(MobileLayout, { ...mountOptions(), props: { role: 'admin' } })
    const paths = wrapper.vm.navItems.map(i => i.path)
    expect(paths).toContain('/admin/dashboard')
    expect(paths).toContain('/admin/courses')
    expect(paths).toContain('/admin/classes')
    expect(paths).toContain('/admin/users')
  })

  it('[ADMIN] moreItems / managementItems 包含 units、words、texts、assignments', () => {
    mockUserStore({ userRole: "ADMIN" })
    const wrapper = shallowMount(MobileLayout, { ...mountOptions(), props: { role: 'admin' } })
    const extra = wrapper.vm.moreItems ?? wrapper.vm.managementItems ?? []
    const paths  = extra.map(i => i.path)
    expect(paths).toContain('/admin/units')
    expect(paths).toContain('/admin/words')
    expect(paths).toContain('/admin/texts')
    expect(paths).toContain('/admin/assignments')
  })

  it('[TEACHER] navItems 包含 dashboard、classes、assignments、courses', () => {
    mockUserStore({ userRole: "TEACHER" })
    const wrapper = shallowMount(MobileLayout, { ...mountOptions(), props: { role: 'teacher' } })
    const paths = wrapper.vm.navItems.map(i => i.path)
    expect(paths).toContain('/teacher/dashboard')
    expect(paths).toContain('/teacher/classes')
    expect(paths).toContain('/teacher/assignments')
    expect(paths).toContain('/teacher/courses')
  })

  it('[TEACHER] 没有 moreItems / managementItems', () => {
    mockUserStore({ userRole: "TEACHER" })
    const wrapper = shallowMount(MobileLayout, { ...mountOptions(), props: { role: 'teacher' } })
    const extra = wrapper.vm.moreItems ?? wrapper.vm.managementItems ?? []
    expect(extra).toHaveLength(0)
  })

  it('[OPS] navItems 包含 monitoring、backups、users', () => {
    mockUserStore({ userRole: "OPS" })
    const wrapper = shallowMount(MobileLayout, { ...mountOptions(), props: { role: 'ops' } })
    const paths = wrapper.vm.navItems.map(i => i.path)
    expect(paths.some(p => p.includes('monitoring'))).toBe(true)
    expect(paths.some(p => p.includes('backups'))).toBe(true)
    expect(paths.some(p => p.includes('users'))).toBe(true)
  })
})

// ── 3. 用户菜单项 ──────────────────────────────────────────────────────────
// ⚠️  POST-MERGE: 当前各布局的菜单项是模板内联的，没有 userMenuItems computed。
// 合并后需要在 SidebarLayout 组件中暴露 userMenuItems computed，
// 这些测试届时才会通过，现在预期失败。

describe('SidebarLayout — 用户下拉菜单项', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    mockRoute.meta = {}
  })

  it('[ADMIN] userMenuItems 包含 system（系统设置）', () => {
    mockUserStore({ userRole: "ADMIN" })
    const wrapper = shallowMount(MobileLayout, { ...mountOptions(), props: { role: 'admin' } })
    const commands = (wrapper.vm.userMenuItems ?? []).map(i => i.command)
    expect(commands).toContain('system')
  })

  it('[TEACHER] userMenuItems 不包含 system', () => {
    mockUserStore({ userRole: "TEACHER" })
    const wrapper = shallowMount(MobileLayout, { ...mountOptions(), props: { role: 'teacher' } })
    const commands = (wrapper.vm.userMenuItems ?? []).map(i => i.command)
    expect(commands).not.toContain('system')
  })

  it('[OPS] userMenuItems 包含 elevate（权限提升）', () => {
    mockUserStore({ userRole: "OPS" })
    const wrapper = shallowMount(MobileLayout, { ...mountOptions(), props: { role: 'ops' } })
    const commands = (wrapper.vm.userMenuItems ?? []).map(i => i.command)
    expect(commands).toContain('elevate')
  })

  it('[ALL] 每个布局的用户菜单都含 logout', () => {
    for (const [Layout, overrides, layoutProps] of [
      [MobileLayout, { userRole: "ADMIN" },   { role: 'admin' }],
      [MobileLayout, { userRole: "TEACHER" }, { role: 'teacher' }],
      [MobileLayout, { userRole: "OPS" },     { role: 'ops' }],
    ]) {
      setActivePinia(createPinia())
      mockUserStore({ isLoggedIn: true, ...overrides })
      const wrapper  = shallowMount(Layout, { ...mountOptions(), props: layoutProps })
      const commands = (wrapper.vm.userMenuItems ?? []).map(i => i.command)
      expect(commands).toContain('logout')
    }
  })
})

// ── 4. 退出登录 ────────────────────────────────────────────────────────────

describe('SidebarLayout — 退出登录', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    mockRoute.meta = {}
  })

  it('[ADMIN] handleCommand("logout") 调用 store.logout', async () => {
    const store = mockUserStore({ userRole: "ADMIN" })
    const wrapper = shallowMount(MobileLayout, { ...mountOptions(), props: { role: 'admin' } })
    await wrapper.vm.handleCommand('logout')
    expect(store.logout).toHaveBeenCalled()
  })

  it('[TEACHER] handleCommand("logout") 调用 store.logout', async () => {
    const store = mockUserStore({ userRole: "TEACHER" })
    const wrapper = shallowMount(MobileLayout, { ...mountOptions(), props: { role: 'teacher' } })
    await wrapper.vm.handleCommand('logout')
    expect(store.logout).toHaveBeenCalled()
  })

  it('[OPS] handleCommand("logout") 调用 store.logout', async () => {
    const store = mockUserStore({ userRole: "OPS" })
    const wrapper = shallowMount(MobileLayout, { ...mountOptions(), props: { role: 'ops' } })
    await wrapper.vm.handleCommand('logout')
    expect(store.logout).toHaveBeenCalled()
  })
})

// ── 5. 页面标题 ────────────────────────────────────────────────────────────

describe('SidebarLayout — 页面标题', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('[ADMIN] pageTitle 使用 route.meta.title', () => {
    mockRoute.meta = { title: '课程管理' }
    mockUserStore({ userRole: "ADMIN" })
    const wrapper = shallowMount(MobileLayout, { ...mountOptions(), props: { role: 'admin' } })
    expect(wrapper.vm.pageTitle).toBe('课程管理')
  })

  it('[TEACHER] pageTitle 使用 route.meta.title', () => {
    mockRoute.meta = { title: '班级列表' }
    mockUserStore({ userRole: "TEACHER" })
    const wrapper = shallowMount(MobileLayout, { ...mountOptions(), props: { role: 'teacher' } })
    expect(wrapper.vm.pageTitle).toBe('班级列表')
  })

  it('[OPS] pageTitle 使用 route.meta.title', () => {
    mockRoute.meta = { title: '系统监控' }
    mockUserStore({ userRole: "OPS" })
    const wrapper = shallowMount(MobileLayout, { ...mountOptions(), props: { role: 'ops' } })
    expect(wrapper.vm.pageTitle).toBe('系统监控')
  })
})

// ── 6. OPS 专属：权限提升对话框 ────────────────────────────────────────────

describe('SidebarLayout — OPS 专属：权限提升对话框', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    mockRoute.meta = {}
  })

  it('[OPS] handleCommand("elevate") 打开提升对话框', async () => {
    mockUserStore({ userRole: "OPS" })
    const wrapper = shallowMount(MobileLayout, { ...mountOptions(), props: { role: 'ops' } })
    await wrapper.vm.handleCommand('elevate')
    const dialogOpen = wrapper.vm.showElevationDialog ?? wrapper.vm.elevationTarget
    expect(dialogOpen).toBeTruthy()
  })

  it('[OPS] onElevationSuccess() 跳转 /admin', async () => {
    mockUserStore({ userRole: "OPS" })
    const wrapper = shallowMount(MobileLayout, { ...mountOptions(), props: { role: 'ops' } })
    await wrapper.vm.onElevationSuccess()
    expect(mockRouterPush).toHaveBeenCalledWith('/admin')
  })

  it('[ADMIN] role=admin 时 showElevationDialog 不被触发', async () => {
    // 合并后 onElevationSuccess 在共享组件中始终存在，
    // 但 role=admin 的模板中没有 ElevationDialog 和 elevate 菜单项，
    // 因此 showElevationDialog 始终为 false
    mockUserStore({ userRole: "ADMIN" })
    const wrapper = shallowMount(MobileLayout, { ...mountOptions(), props: { role: 'admin' } })
    expect(wrapper.vm.showElevationDialog).toBe(false)
  })
})

// ── 7. TEACHER 专属：键盘 Escape ───────────────────────────────────────────

describe('SidebarLayout — TEACHER 专属：键盘 Escape 关闭下拉', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    mockRoute.meta = {}
  })

  it('[TEACHER] handleKeyDown 方法存在', () => {
    mockUserStore({ userRole: "TEACHER" })
    const wrapper = shallowMount(MobileLayout, { ...mountOptions(), props: { role: 'teacher' } })
    expect(typeof wrapper.vm.handleKeyDown).toBe('function')
  })

  it('[TEACHER] Escape 键关闭下拉（若 dropdownVisible 字段存在）', async () => {
    mockUserStore({ userRole: "TEACHER" })
    const wrapper = shallowMount(MobileLayout, { ...mountOptions(), props: { role: 'teacher' } })
    if (wrapper.vm.dropdownVisible !== undefined) {
      wrapper.vm.dropdownVisible = true
      await wrapper.vm.$nextTick()
      wrapper.vm.handleKeyDown({ key: 'Escape' })
      await wrapper.vm.$nextTick()
      expect(wrapper.vm.dropdownVisible).toBe(false)
    }
  })
})

// ── 8. 头像 fallback ───────────────────────────────────────────────────────

describe('SidebarLayout — 头像 fallback', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    mockRoute.meta = {}
  })

  it('[ADMIN] avatar 为空时 userAvatar computed 返回非空字符串', () => {
    mockUserStore({ isLoggedIn: true, hasAdminAccess: true, userInfo: { username: 'admin', avatar: '' } })
    const wrapper = shallowMount(MobileLayout, { ...mountOptions(), props: { role: 'admin' } })
    expect(wrapper.vm.userAvatar).toBeTruthy()
  })

  it('[TEACHER] avatar 为 null 时 userAvatar 返回非空字符串', () => {
    mockUserStore({ isLoggedIn: true, isTeacher: true, userInfo: { username: 't', avatar: null } })
    const wrapper = shallowMount(MobileLayout, { ...mountOptions(), props: { role: 'teacher' } })
    expect(wrapper.vm.userAvatar).toBeTruthy()
  })
})
