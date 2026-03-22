import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { createRouter, createMemoryHistory } from 'vue-router'

// Mock the auth API
vi.mock('@/api/auth', () => ({
  login: vi.fn(),
  logout: vi.fn()
}))

// Mock element-plus
vi.mock('element-plus', () => ({
  ElMessage: vi.fn()
}))

import { login as loginApi, logout as logoutApi } from '@/api/auth'
import { useUserStore } from '@/store/user'

const stubComponent = { template: '<div><router-view /></div>' }

const routes = [
  { path: '/', redirect: '/login' },
  { path: '/login', name: 'Login', component: stubComponent, meta: { title: '登录' } },
  {
    path: '/admin',
    name: 'Admin',
    component: stubComponent,
    redirect: '/admin/dashboard',
    meta: { title: '管理员端', roles: ['ADMIN'] },
    children: [
      { path: 'dashboard', name: 'AdminDashboard', component: stubComponent, meta: { title: '仪表盘', roles: ['ADMIN'] } }
    ]
  },
  {
    path: '/student',
    name: 'Student',
    component: stubComponent,
    redirect: '/student/dashboard',
    meta: { title: '学生端', roles: ['STUDENT'] },
    children: [
      { path: 'dashboard', name: 'StudentDashboard', component: stubComponent, meta: { title: '仪表盘', roles: ['STUDENT'] } },
      { path: 'courses', name: 'StudentCourses', component: stubComponent, meta: { title: '课程', roles: ['STUDENT'] } }
    ]
  },
  {
    path: '/teacher',
    name: 'Teacher',
    component: stubComponent,
    redirect: '/teacher/dashboard',
    meta: { title: '教师端', roles: ['TEACHER'] },
    children: [
      { path: 'dashboard', name: 'TeacherDashboard', component: stubComponent, meta: { title: '仪表盘', roles: ['TEACHER'] } }
    ]
  }
]

function createTestRouter() {
  const router = createRouter({ history: createMemoryHistory(), routes })

  router.beforeEach((to, from, next) => {
    const token = localStorage.getItem('token')
    const userRole = localStorage.getItem('userRole')

    if (to.path === '/login') {
      if (token) {
        switch (userRole) {
          case 'ADMIN': next('/admin'); break
          case 'TEACHER': next('/teacher'); break
          case 'STUDENT': next('/student'); break
          default: next()
        }
      } else {
        next()
      }
    } else {
      if (!token) {
        next('/login')
      } else {
        if (to.meta.roles && !to.meta.roles.includes(userRole)) {
          switch (userRole) {
            case 'ADMIN': next('/admin'); break
            case 'TEACHER': next('/teacher'); break
            case 'STUDENT': next('/student'); break
            default: next('/login')
          }
        } else {
          next()
        }
      }
    }
  })

  return router
}

describe('Auth Flow Integration', () => {
  let store
  let router

  beforeEach(() => {
    localStorage.clear()
    setActivePinia(createPinia())
    store = useUserStore()
    vi.clearAllMocks()
  })

  afterEach(() => {
    localStorage.clear()
    vi.restoreAllMocks()
  })

  describe('Login → Navigation flow', () => {
    it('student login stores token then router allows student routes', async () => {
      loginApi.mockResolvedValue({
        data: {
          token: 'student-jwt-token',
          user: { id: 5, username: 'student1', role: 'STUDENT' }
        }
      })

      // Step 1: Login via store
      await store.login({ username: 'student1', password: 'pass123' })

      // Step 2: Verify store state
      expect(store.token).toBe('student-jwt-token')
      expect(store.userRole).toBe('STUDENT')
      expect(store.isLoggedIn).toBe(true)
      expect(store.isStudent).toBe(true)

      // Step 3: Verify localStorage persistence
      expect(localStorage.getItem('token')).toBe('student-jwt-token')
      expect(localStorage.getItem('userRole')).toBe('STUDENT')

      // Step 4: Router should now allow student routes
      router = createTestRouter()
      await router.push('/student/courses')
      await router.isReady()
      expect(router.currentRoute.value.path).toBe('/student/courses')
    })

    it('admin login redirects from /login to /admin', async () => {
      loginApi.mockResolvedValue({
        data: {
          token: 'admin-jwt-token',
          user: { id: 1, username: 'admin1', role: 'ADMIN' }
        }
      })

      await store.login({ username: 'admin1', password: 'admin123' })

      router = createTestRouter()
      await router.push('/login')
      await router.isReady()
      expect(router.currentRoute.value.path).toBe('/admin/dashboard')
    })

    it('teacher login gives access to teacher routes only', async () => {
      loginApi.mockResolvedValue({
        data: {
          token: 'teacher-jwt-token',
          user: { id: 10, username: 'teacher1', role: 'TEACHER' }
        }
      })

      await store.login({ username: 'teacher1', password: 'teach123' })

      router = createTestRouter()

      // Teacher can access teacher routes
      await router.push('/teacher/dashboard')
      await router.isReady()
      expect(router.currentRoute.value.path).toBe('/teacher/dashboard')

      // Teacher cannot access admin routes → redirected to teacher
      await router.push('/admin/dashboard')
      expect(router.currentRoute.value.path).toBe('/teacher/dashboard')
    })
  })

  describe('Logout → Navigation block flow', () => {
    it('after logout, router blocks protected routes', async () => {
      // Setup: login first
      loginApi.mockResolvedValue({
        data: {
          token: 'temp-token',
          user: { id: 5, username: 'user1', role: 'STUDENT' }
        }
      })
      await store.login({ username: 'user1', password: 'pass' })

      // Verify logged in
      expect(store.isLoggedIn).toBe(true)

      // Logout
      logoutApi.mockResolvedValue({})
      await store.logout()

      // Verify logged out
      expect(store.token).toBe('')
      expect(store.isLoggedIn).toBe(false)
      expect(localStorage.getItem('token')).toBeNull()

      // Router should now block access
      router = createTestRouter()
      await router.push('/student/courses')
      await router.isReady()
      expect(router.currentRoute.value.path).toBe('/login')
    })

    it('logout clears all storage even if API fails', async () => {
      // Login
      loginApi.mockResolvedValue({
        data: {
          token: 'jwt-token',
          user: { id: 1, username: 'user', role: 'ADMIN' }
        }
      })
      await store.login({ username: 'user', password: 'pass' })

      // Logout with API failure — store catches error internally, does not re-throw
      logoutApi.mockRejectedValue(new Error('Network error'))
      await store.logout()

      // State should still be cleared
      expect(store.token).toBe('')
      expect(localStorage.getItem('token')).toBeNull()
      expect(localStorage.getItem('userRole')).toBeNull()

      // Router should redirect to login
      router = createTestRouter()
      await router.push('/admin/dashboard')
      await router.isReady()
      expect(router.currentRoute.value.path).toBe('/login')
    })
  })

  describe('Role validation flow', () => {
    it('rejects login with invalid role from server', async () => {
      loginApi.mockResolvedValue({
        data: {
          token: 'jwt-token',
          user: { id: 1, username: 'user', role: 'SUPERADMIN' }
        }
      })

      await expect(store.login({ username: 'user', password: 'pass' }))
        .rejects.toThrow('登录响应角色异常')

      expect(store.token).toBe('')
      expect(store.isLoggedIn).toBe(false)
    })

    it('rejects login with missing token in response', async () => {
      loginApi.mockResolvedValue({
        data: { user: { id: 1, role: 'ADMIN' } }
      })

      await expect(store.login({ username: 'user', password: 'pass' }))
        .rejects.toThrow('登录响应数据异常')
    })

    it('rejects login with missing user in response', async () => {
      loginApi.mockResolvedValue({
        data: { token: 'jwt-token' }
      })

      await expect(store.login({ username: 'user', password: 'pass' }))
        .rejects.toThrow('登录响应数据异常')
    })
  })

  describe('State persistence across "page reload"', () => {
    it('login state survives pinia recreation', async () => {
      loginApi.mockResolvedValue({
        data: {
          token: 'persistent-token',
          user: { id: 5, username: 'persist', role: 'STUDENT' }
        }
      })

      await store.login({ username: 'persist', password: 'pass' })

      // Simulate page reload: recreate pinia
      setActivePinia(createPinia())
      const newStore = useUserStore()

      expect(newStore.token).toBe('persistent-token')
      expect(newStore.userRole).toBe('STUDENT')
      expect(newStore.isLoggedIn).toBe(true)
      expect(newStore.isStudent).toBe(true)

      // Router should still work with persisted state
      router = createTestRouter()
      await router.push('/student/dashboard')
      await router.isReady()
      expect(router.currentRoute.value.path).toBe('/student/dashboard')
    })
  })

  describe('Cross-role access prevention', () => {
    it('student cannot access admin routes', async () => {
      loginApi.mockResolvedValue({
        data: {
          token: 'student-token',
          user: { id: 5, username: 'student', role: 'STUDENT' }
        }
      })
      await store.login({ username: 'student', password: 'pass' })

      router = createTestRouter()
      await router.push('/admin/dashboard')
      await router.isReady()
      expect(router.currentRoute.value.path).toBe('/student/dashboard')
    })

    it('admin cannot access student routes', async () => {
      loginApi.mockResolvedValue({
        data: {
          token: 'admin-token',
          user: { id: 1, username: 'admin', role: 'ADMIN' }
        }
      })
      await store.login({ username: 'admin', password: 'pass' })

      router = createTestRouter()
      await router.push('/student/courses')
      await router.isReady()
      expect(router.currentRoute.value.path).toBe('/admin/dashboard')
    })

    it('teacher cannot access admin routes', async () => {
      loginApi.mockResolvedValue({
        data: {
          token: 'teacher-token',
          user: { id: 10, username: 'teacher', role: 'TEACHER' }
        }
      })
      await store.login({ username: 'teacher', password: 'pass' })

      router = createTestRouter()
      await router.push('/admin/dashboard')
      await router.isReady()
      expect(router.currentRoute.value.path).toBe('/teacher/dashboard')
    })
  })
})
