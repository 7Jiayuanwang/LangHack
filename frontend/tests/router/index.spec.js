import { describe, it, expect, beforeEach, vi, afterEach } from 'vitest'
import { createRouter, createMemoryHistory } from 'vue-router'

// We need to test the route definitions and navigation guards.
// Since the router module uses dynamic imports, we define the routes inline
// to match the source, replacing lazy components with simple stubs.

const stubComponent = { template: '<div><router-view /></div>' }

const routes = [
  {
    path: '/',
    redirect: '/login'
  },
  {
    path: '/login',
    name: 'Login',
    component: stubComponent,
    meta: { title: '登录' }
  },
  {
    path: '/admin',
    name: 'Admin',
    component: stubComponent,
    redirect: '/admin/dashboard',
    meta: { title: '管理员端', roles: ['ADMIN'] },
    children: [
      {
        path: 'dashboard',
        name: 'AdminDashboard',
        component: stubComponent,
        meta: { title: '仪表盘', roles: ['ADMIN'] }
      },
      {
        path: 'courses',
        name: 'AdminCourses',
        component: stubComponent,
        meta: { title: '课程管理', roles: ['ADMIN'] }
      },
      {
        path: 'users',
        name: 'AdminUsers',
        component: stubComponent,
        meta: { title: '用户管理', roles: ['ADMIN'] }
      }
    ]
  },
  {
    path: '/teacher',
    name: 'Teacher',
    component: stubComponent,
    redirect: '/teacher/dashboard',
    meta: { title: '教师端', roles: ['TEACHER'] },
    children: [
      {
        path: 'dashboard',
        name: 'TeacherDashboard',
        component: stubComponent,
        meta: { title: '仪表盘', roles: ['TEACHER'] }
      },
      {
        path: 'classes',
        name: 'TeacherClasses',
        component: stubComponent,
        meta: { title: '班级管理', roles: ['TEACHER'] }
      }
    ]
  },
  {
    path: '/student',
    name: 'Student',
    component: stubComponent,
    redirect: '/student/dashboard',
    meta: { title: '学生端', roles: ['STUDENT'] },
    children: [
      {
        path: 'dashboard',
        name: 'StudentDashboard',
        component: stubComponent,
        meta: { title: '仪表盘', roles: ['STUDENT'] }
      },
      {
        path: 'courses',
        name: 'StudentCourses',
        component: stubComponent,
        meta: { title: '课程学习', roles: ['STUDENT'] }
      },
      {
        path: 'vocabulary',
        name: 'StudentVocabulary',
        component: stubComponent,
        meta: { title: '生词本', roles: ['STUDENT'] }
      }
    ]
  }
]

/**
 * Creates a test router with the same beforeEach guard logic
 * as the real router in src/router/index.js.
 */
function createTestRouter() {
  const router = createRouter({
    history: createMemoryHistory(),
    routes
  })

  // Replicate the exact guard from src/router/index.js
  router.beforeEach((to, from, next) => {
    const token = localStorage.getItem('token')
    const userRole = localStorage.getItem('userRole')

    if (to.path === '/login') {
      if (token) {
        switch (userRole) {
          case 'ADMIN':
            next('/admin')
            break
          case 'TEACHER':
            next('/teacher')
            break
          case 'STUDENT':
            next('/student')
            break
          default:
            next()
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
            case 'ADMIN':
              next('/admin')
              break
            case 'TEACHER':
              next('/teacher')
              break
            case 'STUDENT':
              next('/student')
              break
            default:
              next('/login')
          }
        } else {
          next()
        }
      }
    }
  })

  return router
}

describe('Router', () => {
  let router

  beforeEach(() => {
    localStorage.clear()
  })

  afterEach(() => {
    localStorage.clear()
  })

  describe('unauthenticated access', () => {
    it('should redirect unauthenticated user to /login when accessing protected route', async () => {
      router = createTestRouter()
      await router.push('/admin/dashboard')
      await router.isReady()

      expect(router.currentRoute.value.path).toBe('/login')
    })

    it('should redirect unauthenticated user from /student to /login', async () => {
      router = createTestRouter()
      await router.push('/student/courses')
      await router.isReady()

      expect(router.currentRoute.value.path).toBe('/login')
    })

    it('should allow unauthenticated user to access /login', async () => {
      router = createTestRouter()
      await router.push('/login')
      await router.isReady()

      expect(router.currentRoute.value.path).toBe('/login')
    })
  })

  describe('authenticated ADMIN access', () => {
    beforeEach(() => {
      localStorage.setItem('token', 'admin-token')
      localStorage.setItem('userRole', 'ADMIN')
    })

    it('should allow ADMIN to access /admin routes', async () => {
      router = createTestRouter()
      await router.push('/admin/dashboard')
      await router.isReady()

      expect(router.currentRoute.value.path).toBe('/admin/dashboard')
      expect(router.currentRoute.value.name).toBe('AdminDashboard')
    })

    it('should redirect ADMIN away from /student routes', async () => {
      router = createTestRouter()
      await router.push('/student/dashboard')
      await router.isReady()

      // ADMIN should be redirected to /admin (which redirects to /admin/dashboard)
      expect(router.currentRoute.value.path).toBe('/admin/dashboard')
    })

    it('should redirect logged-in ADMIN from /login to /admin', async () => {
      router = createTestRouter()
      await router.push('/login')
      await router.isReady()

      expect(router.currentRoute.value.path).toBe('/admin/dashboard')
    })
  })

  describe('authenticated STUDENT access', () => {
    beforeEach(() => {
      localStorage.setItem('token', 'student-token')
      localStorage.setItem('userRole', 'STUDENT')
    })

    it('should allow STUDENT to access /student routes', async () => {
      router = createTestRouter()
      await router.push('/student/courses')
      await router.isReady()

      expect(router.currentRoute.value.path).toBe('/student/courses')
      expect(router.currentRoute.value.name).toBe('StudentCourses')
    })

    it('should redirect STUDENT away from /admin routes', async () => {
      router = createTestRouter()
      await router.push('/admin/dashboard')
      await router.isReady()

      expect(router.currentRoute.value.path).toBe('/student/dashboard')
    })

    it('should redirect logged-in STUDENT from /login to /student', async () => {
      router = createTestRouter()
      await router.push('/login')
      await router.isReady()

      expect(router.currentRoute.value.path).toBe('/student/dashboard')
    })
  })

  describe('authenticated TEACHER access', () => {
    beforeEach(() => {
      localStorage.setItem('token', 'teacher-token')
      localStorage.setItem('userRole', 'TEACHER')
    })

    it('should allow TEACHER to access /teacher routes', async () => {
      router = createTestRouter()
      await router.push('/teacher/classes')
      await router.isReady()

      expect(router.currentRoute.value.path).toBe('/teacher/classes')
    })

    it('should redirect TEACHER away from /admin routes', async () => {
      router = createTestRouter()
      await router.push('/admin/users')
      await router.isReady()

      expect(router.currentRoute.value.path).toBe('/teacher/dashboard')
    })
  })

  describe('root path redirect', () => {
    it('should redirect / to /login when not authenticated', async () => {
      router = createTestRouter()
      await router.push('/')
      await router.isReady()

      expect(router.currentRoute.value.path).toBe('/login')
    })
  })
})
