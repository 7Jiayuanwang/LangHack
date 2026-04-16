import { createRouter, createWebHistory } from "vue-router";
import { useUserStore } from "@/store/user";
import { isValidToken } from "@/utils/auth";

const routes = [
  {
    path: "/",
    redirect: "/articles", // 默认导向 articles 页面
  },
  {
    path: "/articles",
    name: "Articles",
    component: () => import("@/views/public/Articles.vue"),
    meta: { title: "文章列表", public: true }, // 标记为公开页面
  },
  {
    path: "/article/:id",
    name: "ArticleDetail",
    component: () => import("@/views/user/SimpleTextView.vue"),
    meta: { title: "文章详情", public: true }, // 公开的文章详情页
  },
  {
    path: "/login",
    name: "Login",
    component: () => import("@/views/Login.vue"),
    meta: { title: "登录", public: true },
  },
  {
    path: "/" + (import.meta.env.VITE_ADMIN_PORTAL_PATH || "admin-portal"),
    name: "AdminLogin",
    component: () => import("@/views/AdminLogin.vue"),
    meta: { title: "管理员登录", public: true },
  },
  {
    path: "/sso/callback",
    name: "SsoCallback",
    component: () => import("@/views/public/SsoCallback.vue"),
    meta: { title: "SSO 登录回调", public: true },
  },
  {
    path: "/admin",
    name: "Admin",
    component: () => import("@/views/admin/Layout.vue"),
    redirect: "/admin/dashboard",
    meta: { title: "管理员端", roles: ["ADMIN"] },
    children: [
      {
        path: "dashboard",
        name: "AdminDashboard",
        component: () => import("@/views/admin/Dashboard.vue"),
        meta: { title: "仪表盘", roles: ["ADMIN"] },
      },
      {
        path: "courses",
        name: "AdminCourses",
        component: () => import("@/views/admin/Courses.vue"),
        meta: { title: "课程管理", roles: ["ADMIN"] },
      },
      {
        path: "classes",
        name: "AdminClasses",
        component: () => import("@/views/admin/Classes.vue"),
        meta: { title: "班级管理", roles: ["ADMIN"] },
      },
      {
        path: "users",
        name: "AdminUsers",
        component: () => import("@/views/admin/Users.vue"),
        meta: { title: "用户管理", roles: ["ADMIN"] },
      },
      {
        path: "units",
        name: "AdminUnits",
        component: () => import("@/views/admin/Units.vue"),
        meta: { title: "单元管理", roles: ["ADMIN"] },
      },
      {
        path: "units/:unitId/words",
        name: "AdminUnitWords",
        component: () => import("@/views/admin/UnitWords.vue"),
        meta: { title: "单元单词管理", roles: ["ADMIN"] },
        props: true,
      },
      {
        path: "units/:unitId/texts",
        name: "AdminUnitTexts",
        component: () => import("@/views/admin/UnitTexts.vue"),
        meta: { title: "单元课文管理", roles: ["ADMIN"] },
        props: true,
      },
      {
        path: "words",
        name: "AdminWords",
        component: () => import("@/views/admin/Words.vue"),
        meta: { title: "单词管理", roles: ["ADMIN"] },
      },
      {
        path: "texts",
        name: "AdminTexts",
        component: () => import("@/views/admin/Texts.vue"),
        meta: { title: "课文管理", roles: ["ADMIN"] },
      },
      {
        path: "assignments",
        name: "AdminAssignments",
        component: () => import("@/views/admin/Assignments.vue"),
        meta: { title: "作业管理", roles: ["ADMIN"] },
      },
      {
        path: "videos",
        name: "AdminVideos",
        component: () => import("@/views/admin/Videos.vue"),
        meta: { title: "视频管理", roles: ["ADMIN"] },
      },
      {
        path: "sso-pending",
        name: "AdminSsoPending",
        component: () => import("@/views/admin/SsoPendingApprovals.vue"),
        meta: { title: "SSO 用户审批", roles: ["ADMIN"] },
      },
    ],
  },
  {
    path: "/ops",
    name: "Ops",
    component: () => import("@/views/ops/Layout.vue"),
    redirect: "/ops/monitoring",
    meta: { title: "运维面板", roles: ["OPS", "ADMIN"] },
    children: [
      {
        path: "monitoring",
        name: "OpsMonitoring",
        component: () => import("@/views/admin/Monitoring.vue"),
        meta: { title: "系统监控", roles: ["OPS", "ADMIN"] },
      },
      {
        path: "backups",
        name: "OpsBackups",
        component: () => import("@/views/admin/Backups.vue"),
        meta: { title: "数据备份", roles: ["OPS", "ADMIN"] },
      },
      {
        path: "users",
        name: "OpsUsers",
        component: () => import("@/views/admin/Users.vue"),
        meta: { title: "用户管理", roles: ["OPS", "ADMIN"] },
      },
      {
        path: "ai-providers",
        name: "OpsAiProviders",
        component: () => import("@/views/ops/AiProviders.vue"),
        meta: { title: "AI 服务管理", roles: ["OPS", "ADMIN"] },
      },
      {
        path: "update",
        name: "OpsUpdate",
        component: () => import("@/views/ops/Update.vue"),
        meta: { title: "系统更新", roles: ["OPS", "ADMIN"] },
      },
      {
        path: "feature-toggles",
        name: "OpsFeatureToggles",
        component: () => import("@/views/ops/FeatureToggles.vue"),
        meta: { title: "功能开关", roles: ["OPS", "ADMIN"] },
      },
    ],
  },
  {
    path: "/teacher",
    name: "Teacher",
    component: () => import("@/views/teacher/Layout.vue"),
    redirect: "/teacher/dashboard",
    meta: { title: "教师端", roles: ["TEACHER"] },
    children: [
      {
        path: "dashboard",
        name: "TeacherDashboard",
        component: () => import("@/views/teacher/Dashboard.vue"),
        meta: { title: "仪表盘", roles: ["TEACHER"] },
      },
      {
        path: "classes",
        name: "TeacherClasses",
        component: () => import("@/views/teacher/Classes.vue"),
        meta: { title: "班级管理", roles: ["TEACHER"] },
      },
      {
        path: "courses",
        name: "TeacherCourses",
        component: () => import("@/views/teacher/Courses.vue"),
        meta: { title: "课程预览", roles: ["TEACHER"] },
      },
      {
        path: "assignments",
        name: "TeacherAssignments",
        component: () => import("@/views/teacher/Assignments.vue"),
        meta: { title: "任务管理", roles: ["TEACHER"] },
      },
    ],
  },
  {
    path: "/student",
    name: "Student",
    component: () => import("@/views/student/Layout.vue"),
    redirect: "/student/articles",
    meta: { title: "文章", roles: ["STUDENT", "USER"] },
    children: [
      {
        path: "dashboard",
        name: "StudentDashboard",
        component: () => import("@/views/student/Dashboard.vue"),
        meta: { title: "仪表盘", roles: ["STUDENT", "USER"] },
      },
      {
        path: "courses",
        name: "StudentCourses",
        component: () => import("@/views/student/Courses.vue"),
        meta: { title: "课程学习", roles: ["STUDENT", "USER"] },
      },
      {
        path: "assignments",
        name: "StudentAssignments",
        component: () => import("@/views/student/Assignments.vue"),
        meta: { title: "我的任务", roles: ["STUDENT", "USER"] },
      },
      {
        path: "vocabulary",
        name: "StudentVocabulary",
        component: () => import("@/views/student/Vocabulary.vue"),
        meta: { title: "生词本", roles: ["STUDENT", "USER"] },
      },
      {
        path: "classes",
        name: "StudentClasses",
        component: () => import("@/views/student/Classes.vue"),
        meta: { title: "我的班级", roles: ["STUDENT", "USER"] },
      },
      {
        path: "ai-qa",
        name: "StudentAiQa",
        component: () => import("@/views/student/AiQa.vue"),
        meta: { title: "AI问答", roles: ["STUDENT", "USER"] },
      },
      {
        path: "text-learning",
        name: "StudentTextLearning",
        component: () => import("@/views/student/InteractiveText.vue"),
        meta: { title: "课文学习", roles: ["STUDENT", "USER"] },
      },
      {
        path: "ai-video",
        name: "StudentAiVideo",
        component: () => import("@/views/student/VideoSubtitlePlayer.vue"),
        meta: { title: "AI视频字幕", roles: ["STUDENT", "USER"] },
      },
      {
        path: "videos",
        name: "StudentVideos",
        component: () => import("@/views/student/Videos.vue"),
        meta: { title: "视频", roles: ["STUDENT", "USER"] },
      },
      {
        path: "video-player",
        name: "StudentVideoPlayer",
        component: () => import("@/views/student/VideoPlayer.vue"),
        meta: { title: "视频播放", roles: ["STUDENT", "USER"] },
      },
      {
        path: "user-texts",
        name: "StudentUserTexts",
        component: () => import("@/views/student/UserTexts.vue"),
        meta: { title: "我的上传", roles: ["STUDENT", "USER"] },
      },
      {
        path: "articles",
        name: "StudentArticles",
        component: () => import("@/views/student/Articles.vue"),
        meta: { roles: ["STUDENT", "USER"] },
      },
      {
        path: "my-study",
        name: "StudentMyStudy",
        component: () => import("@/views/student/MyStudy.vue"),
        meta: { title: "我的学习", roles: ["STUDENT", "USER"] },
      },
      {
        path: "word-test",
        redirect: "/student/vocabulary",
      },
      {
        path: "word-learning",
        redirect: "/student/vocabulary",
      },
    ],
  },
  {
    path: "/user",
    name: "User",
    component: () => import("@/views/user/Layout.vue"),
    redirect: { name: "UserHome" },
    meta: { title: "普通用户端", roles: ["USER"] },
    children: [
      {
        path: "",
        name: "UserHome",
        component: () => import("@/views/user/Home.vue"),
        meta: { title: "随机课文", roles: ["USER"] },
      },
      {
        path: "user-texts",
        name: "UserUserTexts",
        component: () => import("@/views/user/UserTexts.vue"),
        meta: { title: "我的上传", roles: ["USER"] },
      },
      {
        path: "text-learning",
        name: "UserTextLearning",
        component: () => import("@/views/user/SimpleTextView.vue"),
        meta: { title: "课文学习", roles: ["USER"] },
      },
      {
        path: "vocabulary",
        name: "UserVocabulary",
        component: () => import("@/views/user/Vocabulary.vue"),
        meta: { title: "生词本", roles: ["USER"] },
      },
      {
        path: "courses",
        name: "UserCourses",
        component: () => import("@/views/user/Courses.vue"),
        meta: { title: "课程学习", roles: ["USER"] },
      },
      {
        path: "units",
        name: "UserUnits",
        component: () => import("@/views/user/Units.vue"),
        meta: { title: "单元学习", roles: ["USER"] },
      },
      {
        path: "texts",
        name: "UserTexts",
        component: () => import("@/views/user/Texts.vue"),
        meta: { title: "课文列表", roles: ["USER"] },
      },
    ],
  },
  // 仅在开发环境注册测试路由 - 防止生产环境暴露测试页面
  ...(import.meta.env.DEV
    ? [
        {
          path: "/test/translate",
          name: "TranslateTest",
          component: () => import("@/views/test/TranslateTest.vue"),
          meta: { title: "翻译功能测试", public: true },
        },
      ]
    : []),
  // 404 兜底路由
  {
    path: "/:pathMatch(.*)*",
    name: "NotFound",
    redirect: "/",
  },
];

const router = createRouter({
  history: createWebHistory(),
  routes,
});

// 路由守卫
// 注意: 此守卫仅用于 UX 优化（避免用户看到无权限页面再被拒绝），
// 真实权限控制由后端 JWT + @PreAuthorize 实现。本地 userRole 可被篡改，
// 但不会造成安全风险，因为后端会独立验证每个请求的 Token 和角色。
router.beforeEach((to, from, next) => {
  const userStore = useUserStore();

  // 验证路由参数（防止注入攻击）
  if (to.params.id && !/^\d+$/.test(to.params.id)) {
    console.error("Invalid ID parameter:", to.params.id);
    next("/");
    return;
  }
  if (to.params.unitId && !/^\d+$/.test(to.params.unitId)) {
    console.error("Invalid unitId parameter:", to.params.unitId);
    next("/");
    return;
  }
  if (to.params.courseId && !/^\d+$/.test(to.params.courseId)) {
    console.error("Invalid courseId parameter:", to.params.courseId);
    next("/");
    return;
  }

  // 从 store 读取状态作为真实数据源，而非 localStorage
  const token = userStore.token;
  const hasValidToken = isValidToken(token);
  const userRole = userStore.userRole;

  // 公开页面，无需登录即可访问
  if (to.meta.public) {
    if (hasValidToken && to.path === "/login") {
      // 已登录用户访问登录页，重定向到对应主页
      const roleRoutes = {
        ADMIN: "/admin",
        OPS: "/ops",
        TEACHER: "/teacher",
        STUDENT: "/student",
        USER: "/user",
      };
      next(roleRoutes[userRole] || "/");
    } else {
      next();
    }
    return;
  }

  // 需要登录的页面
  if (!hasValidToken) {
    // 防止无限重定向循环
    if (to.path !== "/login") {
      next("/login");
    } else {
      next();
    }
  } else {
    // 检查角色权限，防止 meta.roles 为 undefined 导致的 TypeError
    const requiredRoles = to.meta.roles;
    if (
      requiredRoles &&
      Array.isArray(requiredRoles) &&
      !requiredRoles.includes(userRole)
    ) {
      // 检查提权状态：ADMIN 可通过提权访问 OPS 路由，OPS 可通过提权访问 ADMIN 路由
      const elevatedRole = userStore.elevatedRole;
      const expiresAt = userStore.elevationExpiresAt;
      const isElevationValid =
        elevatedRole && expiresAt && new Date(expiresAt) > new Date();
      if (isElevationValid && requiredRoles.includes(elevatedRole)) {
        next();
        return;
      }

      // 无权限访问，跳转到对应主页
      const roleRoutes = {
        ADMIN: "/admin",
        OPS: "/ops",
        TEACHER: "/teacher",
        STUDENT: "/student",
        USER: "/user",
      };
      console.warn(
        `权限不足: 尝试访问 ${to.path}，当前角色 ${userRole}，需要角色 ${requiredRoles}`,
      );

      // 防止无限重定向循环 - 如果目标路由就是用户主页，直接放行
      const targetRoute = roleRoutes[userRole];
      if (targetRoute && to.path.startsWith(targetRoute)) {
        next();
      } else {
        next(targetRoute || "/login");
      }
    } else {
      next();
    }
  }
});

export default router;
