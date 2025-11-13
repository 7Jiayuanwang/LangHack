import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  {
    path: '/',
    redirect: '/login'
  },
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/Login.vue'),
    meta: { title: '登录' }
  },
  {
    path: '/admin',
    name: 'Admin',
    component: () => import('@/views/admin/Layout.vue'),
    redirect: '/admin/dashboard',
    meta: { title: '管理员端', roles: ['ADMIN'] },
    children: [
      {
        path: 'dashboard',
        name: 'AdminDashboard',
        component: () => import('@/views/admin/Dashboard.vue'),
        meta: { title: '仪表盘', roles: ['ADMIN'] }
      },
      {
        path: 'courses',
        name: 'AdminCourses',
        component: () => import('@/views/admin/Courses.vue'),
        meta: { title: '课程管理', roles: ['ADMIN'] }
      },
      {
        path: 'users',
        name: 'AdminUsers',
        component: () => import('@/views/admin/Users.vue'),
        meta: { title: '用户管理', roles: ['ADMIN'] }
      },
      {
        path: 'texts',
        name: 'AdminTexts',
        component: () => import('@/views/admin/Texts.vue'),
        meta: { title: '课文管理', roles: ['ADMIN'] }
      },
      {
        path: 'words',
        name: 'AdminWords',
        component: () => import('@/views/admin/Words.vue'),
        meta: { title: '单词管理', roles: ['ADMIN'] }
      },
      {
        path: 'classes',
        name: 'AdminClasses',
        component: () => import('@/views/admin/Classes.vue'),
        meta: { title: '班级管理', roles: ['ADMIN'] }
      }
    ]
  },
  {
    path: '/teacher',
    name: 'Teacher',
    component: () => import('@/views/teacher/Layout.vue'),
    redirect: '/teacher/dashboard',
    meta: { title: '教师端', roles: ['TEACHER'] },
    children: [
      {
        path: 'dashboard',
        name: 'TeacherDashboard',
        component: () => import('@/views/teacher/Dashboard.vue'),
        meta: { title: '仪表盘', roles: ['TEACHER'] }
      },
      {
        path: 'classes',
        name: 'TeacherClasses',
        component: () => import('@/views/teacher/Classes.vue'),
        meta: { title: '班级管理', roles: ['TEACHER'] }
      },
      {
        path: 'courses',
        name: 'TeacherCourses',
        component: () => import('@/views/teacher/Courses.vue'),
        meta: { title: '课程预览', roles: ['TEACHER'] }
      },
      {
        path: 'assignments',
        name: 'TeacherAssignments',
        component: () => import('@/views/teacher/Assignments.vue'),
        meta: { title: '任务管理', roles: ['TEACHER'] }
      }
    ]
  },
  {
    path: '/student',
    name: 'Student',
    component: () => import('@/views/student/Layout.vue'),
    redirect: '/student/dashboard',
    meta: { title: '学生端', roles: ['STUDENT'] },
    children: [
      {
        path: 'dashboard',
        name: 'StudentDashboard',
        component: () => import('@/views/student/Dashboard.vue'),
        meta: { title: '仪表盘', roles: ['STUDENT'] }
      },
      {
        path: 'courses',
        name: 'StudentCourses',
        component: () => import('@/views/student/Courses.vue'),
        meta: { title: '课程学习', roles: ['STUDENT'] }
      },
      {
        path: 'assignments',
        name: 'StudentAssignments',
        component: () => import('@/views/student/Assignments.vue'),
        meta: { title: '我的任务', roles: ['STUDENT'] }
      },
      {
        path: 'vocabulary',
        name: 'StudentVocabulary',
        component: () => import('@/views/student/Vocabulary.vue'),
        meta: { title: '生词本', roles: ['STUDENT'] }
      },
      {
        path: 'classes',
        name: 'StudentClasses',
        component: () => import('@/views/student/Classes.vue'),
        meta: { title: '我的班级', roles: ['STUDENT'] }
      },
      {
        path: 'practice',
        name: 'StudentPractice',
        component: () => import('@/views/student/Practice.vue'),
        meta: { title: '在线练习', roles: ['STUDENT'] }
      },
      {
        path: 'weakness',
        name: 'StudentWeakness',
        component: () => import('@/views/student/WeaknessAnalysis.vue'),
        meta: { title: '薄弱点分析', roles: ['STUDENT'] }
      },
      {
        path: 'suggestions',
        name: 'StudentSuggestions',
        component: () => import('@/views/student/LearningSuggestion.vue'),
        meta: { title: '学习建议', roles: ['STUDENT'] }
      }
    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

// 路由守卫
router.beforeEach((to, from, next) => {
  const token = localStorage.getItem('token')
  const userRole = localStorage.getItem('userRole')

  if (to.path === '/login') {
    if (token) {
      // 已登录用户重定向到对应主页
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
      // 检查角色权限
      if (to.meta.roles && !to.meta.roles.includes(userRole)) {
        // 无权限访问，跳转到对应主页
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

export default router