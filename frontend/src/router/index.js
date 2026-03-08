import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '@/store/user'

const routes = [
  {
    path: '/',
    redirect: '/articles'  // 默认导向 articles 页面
  },
  {
    path: '/articles',
    name: 'Articles',
    component: () => import('@/views/public/Articles.vue'),
    meta: { title: '文章列表', public: true }  // 标记为公开页面
  },
  {
    path: '/article/:id',
    name: 'ArticleDetail',
    component: () => import('@/views/user/SimpleTextView.vue'),
    meta: { title: '文章详情', public: true }  // 公开的文章详情页
  },
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/Login.vue'),
    meta: { title: '登录', public: true }
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
        path: 'classes',
        name: 'AdminClasses',
        component: () => import('@/views/admin/Classes.vue'),
        meta: { title: '班级管理', roles: ['ADMIN'] }
      },
      {
        path: 'users',
        name: 'AdminUsers',
        component: () => import('@/views/admin/Users.vue'),
        meta: { title: '用户管理', roles: ['ADMIN'] }
      },
      {
        path: 'units',
        name: 'AdminUnits',
        component: () => import('@/views/admin/Units.vue'),
        meta: { title: '单元管理', roles: ['ADMIN'] }
      },
      {
        path: 'units/:unitId/words',
        name: 'AdminUnitWords',
        component: () => import('@/views/admin/UnitWords.vue'),
        meta: { title: '单元单词管理', roles: ['ADMIN'] },
        props: true
      },
      {
        path: 'units/:unitId/texts',
        name: 'AdminUnitTexts',
        component: () => import('@/views/admin/UnitTexts.vue'),
        meta: { title: '单元课文管理', roles: ['ADMIN'] },
        props: true
      },
      {
        path: 'words',
        name: 'AdminWords',
        component: () => import('@/views/admin/Words.vue'),
        meta: { title: '单词管理', roles: ['ADMIN'] }
      },
      {
        path: 'texts',
        name: 'AdminTexts',
        component: () => import('@/views/admin/Texts.vue'),
        meta: { title: '课文管理', roles: ['ADMIN'] }
      },
      {
        path: 'assignments',
        name: 'AdminAssignments',
        component: () => import('@/views/admin/Assignments.vue'),
        meta: { title: '作业管理', roles: ['ADMIN'] }
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
    redirect: '/student/articles',
    meta: { title: '文章', roles: ['STUDENT', 'USER'] },
    children: [
      {
        path: 'dashboard',
        name: 'StudentDashboard',
        component: () => import('@/views/student/Dashboard.vue'),
        meta: { title: '仪表盘', roles: ['STUDENT', 'USER'] }
      },
      {
        path: 'courses',
        name: 'StudentCourses',
        component: () => import('@/views/student/Courses.vue'),
        meta: { title: '课程学习', roles: ['STUDENT', 'USER'] }
      },
      {
        path: 'assignments',
        name: 'StudentAssignments',
        component: () => import('@/views/student/Assignments.vue'),
        meta: { title: '我的任务', roles: ['STUDENT', 'USER'] }
      },
      {
        path: 'vocabulary',
        name: 'StudentVocabulary',
        component: () => import('@/views/student/Vocabulary.vue'),
        meta: { title: '生词本', roles: ['STUDENT', 'USER'] }
      },
      {
        path: 'classes',
        name: 'StudentClasses',
        component: () => import('@/views/student/Classes.vue'),
        meta: { title: '我的班级', roles: ['STUDENT', 'USER'] }
      },
      {
        path: 'ai-qa',
        name: 'StudentAiQa',
        component: () => import('@/views/student/AiQa.vue'),
        meta: { title: 'AI问答', roles: ['STUDENT', 'USER'] }
      },
      {
        path: 'text-learning',
        name: 'StudentTextLearning',
        component: () => import('@/views/student/InteractiveText.vue'),
        meta: { title: '课文学习', roles: ['STUDENT', 'USER'] }
      },
      {
        path: 'ai-video',
        name: 'StudentAiVideo',
        component: () => import('@/views/student/VideoSubtitlePlayer.vue'),
        meta: { title: 'AI视频字幕', roles: ['STUDENT', 'USER'] }
      },
      {
        path: 'user-texts',
        name: 'StudentUserTexts',
        component: () => import('@/views/student/UserTexts.vue'),
        meta: { title: '我的上传', roles: ['STUDENT', 'USER'] }
      },
      {
        path: 'articles',
        name: 'StudentArticles',
        component: () => import('@/views/student/Articles.vue'),
        meta: { roles: ['STUDENT', 'USER'] }
      },
      {
        path: 'word-test',
        redirect: '/student/vocabulary'
      }
    ]
  },
  {
    path: '/user',
    name: 'User',
    component: () => import('@/views/user/Layout.vue'),
    redirect: { name: 'UserHome' },
    meta: { title: '普通用户端', roles: ['USER'] },
    children: [
      {
        path: '',
        name: 'UserHome',
        component: () => import('@/views/user/Home.vue'),
        meta: { title: '随机课文', roles: ['USER'] }
      },
      {
        path: 'user-texts',
        name: 'UserUserTexts',
        component: () => import('@/views/user/UserTexts.vue'),
        meta: { title: '我的上传', roles: ['USER'] }
      },
      {
        path: 'text-learning',
        name: 'UserTextLearning',
        component: () => import('@/views/user/SimpleTextView.vue'),
        meta: { title: '课文学习', roles: ['USER'] }
      },
      {
        path: 'vocabulary',
        name: 'UserVocabulary',
        component: () => import('@/views/user/Vocabulary.vue'),
        meta: { title: '生词本', roles: ['USER'] }
      },
      {
        path: 'courses',
        name: 'UserCourses',
        component: () => import('@/views/user/Courses.vue'),
        meta: { title: '课程学习', roles: ['USER'] }
      },
      {
        path: 'units',
        name: 'UserUnits',
        component: () => import('@/views/user/Units.vue'),
        meta: { title: '单元学习', roles: ['USER'] }
      },
      {
        path: 'home',
        name: 'UserDashboard',
        component: () => import('@/views/user/Home.vue'),
        meta: { title: '用户首页', roles: ['USER'] }
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
  const userStore = useUserStore()

  // 从 store 读取状态作为真实数据源，而非 localStorage
  const token = userStore.token
  const userRole = userStore.userRole

  // 公开页面，无需登录即可访问
  if (to.meta.public) {
    if (token && to.path === '/login') {
      // 已登录用户访问登录页，重定向到对应主页
      const roleRoutes = {
        'ADMIN': '/admin',
        'TEACHER': '/teacher',
        'STUDENT': '/student',
        'USER': '/user'
      }
      next(roleRoutes[userRole] || '/')
    } else {
      next()
    }
    return
  }

  // 需要登录的页面
  if (!token) {
    next('/login')
  } else {
    // 检查角色权限，防止 meta.roles 为 undefined 导致的 TypeError
    const requiredRoles = to.meta.roles
    if (requiredRoles && Array.isArray(requiredRoles) && !requiredRoles.includes(userRole)) {
      // 无权限访问，跳转到对应主页
      const roleRoutes = {
        'ADMIN': '/admin',
        'TEACHER': '/teacher',
        'STUDENT': '/student',
        'USER': '/user'
      }
      console.warn(`权限不足: 尝试访问 ${to.path}，当前角色 ${userRole}，需要角色 ${requiredRoles}`)
      next(roleRoutes[userRole] || '/login')
    } else {
      next()
    }
  }
})

export default router