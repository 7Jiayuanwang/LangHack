<template>
  <div
    class="mobile-layout"
    :class="`role-${role}`"
    :style="{
      '--nav-accent':        theme.accent,
      '--nav-accent-bg':     theme.accentBg,
      '--nav-accent-shadow': theme.accentShadow,
    }"
  >
    <!-- 顶部标题栏 -->
    <header class="mobile-header">
      <div class="header-left">
        <h1 class="page-title">{{ pageTitle }}</h1>
      </div>
    </header>

    <!-- 主内容区域 -->
    <main class="mobile-main">
      <div class="content-container">
        <transition name="fade" mode="out-in">
          <slot><router-view /></slot>
        </transition>
      </div>
    </main>

    <!-- 底部导航栏 -->
    <nav class="mobile-nav">
      <div class="nav-container">
        <router-link
          v-for="item in navItems"
          :key="item.path"
          :to="item.path"
          class="nav-item"
          :class="{ active: $route.path === item.path || $route.path.startsWith(item.path + '/') }"
        >
          <div class="nav-icon">
            <component :is="item.icon" />
          </div>
          <span class="nav-label">{{ item.label }}</span>
          <div class="nav-badge" v-if="item.badge">{{ item.badge }}</div>
        </router-link>

        <!-- admin 专属：二级"管理"下拉菜单 -->
        <div
          v-if="role === 'admin'"
          class="nav-item management-menu-item"
          :class="{ active: isManagementRouteActive }"
        >
          <el-dropdown trigger="click" placement="top">
            <div class="nav-management-trigger">
              <div class="nav-icon">
                <component :is="Setting" />
              </div>
              <span class="nav-label">管理</span>
              <div class="nav-badge" v-if="pendingReviewCount > 0">{{ pendingReviewCount }}</div>
            </div>
            <template #dropdown>
              <el-dropdown-menu>
                <router-link
                  v-for="item in moreItems"
                  :key="item.path"
                  :to="item.path"
                  class="dropdown-link"
                >
                  <el-dropdown-item>
                    <div class="dropdown-item-content">
                      <div class="dropdown-item-left">
                        <component :is="item.icon" style="margin-right: 8px; width: 16px; height: 16px;" />
                        <span>{{ item.label }}</span>
                      </div>
                      <el-badge v-if="item.path === '/admin/content' && pendingReviewCount > 0" :value="pendingReviewCount" class="menu-badge" />
                    </div>
                  </el-dropdown-item>
                </router-link>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>

        <!-- 用户菜单 -->
        <div class="nav-item user-menu-item">
          <el-dropdown
            @command="handleCommand"
            @visible-change="handleDropdownVisible"
            placement="top-end"
          >
            <div class="nav-user-avatar">
              <div class="nav-icon">
                <img
                  :src="userAvatar"
                  :alt="userStore.userInfo?.realName || '用户'"
                  class="avatar-img"
                />
              </div>
              <span class="nav-label">我的</span>
            </div>
            <template #dropdown>
              <el-dropdown-menu>
                <!-- ops 专属：切换到管理端 -->
                <template v-if="role === 'ops'">
                  <el-dropdown-item command="elevate" v-if="userStore.userRole === 'OPS'">
                    切换到管理端
                  </el-dropdown-item>
                  <el-dropdown-item command="elevate" v-if="userStore.userRole === 'ADMIN'">
                    返回管理端
                  </el-dropdown-item>
                  <el-dropdown-item command="logout" divided>退出登录</el-dropdown-item>
                </template>
                <!-- 其他角色通用菜单 -->
                <template v-else>
                  <el-dropdown-item command="profile">个人资料</el-dropdown-item>
                  <el-dropdown-item command="settings">设置</el-dropdown-item>
                  <!-- admin 专属：系统设置 -->
                  <el-dropdown-item v-if="role === 'admin'" command="system">系统设置</el-dropdown-item>
                  <el-dropdown-item command="logout" divided>退出登录</el-dropdown-item>
                </template>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </div>
    </nav>

    <!-- ops 专属：提权对话框 -->
    <ElevationDialog
      v-if="role === 'ops'"
      v-model="showElevationDialog"
      :target-role="elevationTarget"
      @success="onElevationSuccess"
    />

    <!-- HiAgent 问题反馈气泡（仅教师端显示） -->
    <HiagentFeedback v-if="role === 'teacher'" mobile-target-route="/teacher/ai-qa" />
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useUserStore } from '@/store/user'
import { getSsoLogoutUrl, getSsoStatus } from '@/api/sso'
import { useFeatureToggles } from '@/composables/useFeatureToggles'
import { ElMessage, ElMessageBox } from 'element-plus'
import ElevationDialog from '@/components/ElevationDialog.vue'
import HiagentFeedback from '@/components/HiagentFeedback.vue'
import {
  DataBoard, School, Edit, Reading,
  Upload, Document, House,
  User, EditPen, Setting,
  DataLine, FolderOpened, Connection, Refresh, Switch, Coin, Notebook,
  VideoPlay,
} from '@element-plus/icons-vue'
import { getReviewTexts, getReviewVideos } from '@/api/admin'

const props = defineProps({
  role: {
    type: String,
    required: true,
    validator: (v) => ['teacher', 'user', 'ops', 'admin'].includes(v),
  },
})

const router    = useRouter()
const route     = useRoute()
const userStore = useUserStore()

// ops 专属状态
const showElevationDialog = ref(false)
const elevationTarget     = ref('ADMIN')

// 待审核计数
const pendingReviewCount = ref(0)
let pendingPollTimer = null

// 功能开关状态（与路由守卫 + 其他 layout 共享缓存）
const { toggles: featureToggles, load: loadFeatureToggles } = useFeatureToggles()

// ── 主题（accent 颜色）────────────────────────────────────────────────────────

const THEME = {
  teacher: { accent: 'var(--secondary-color)', accentBg: 'rgba(78, 205, 196, 0.1)',  accentShadow: 'rgba(78, 205, 196, 0.3)'  },
  user:    { accent: 'var(--info-color)',       accentBg: 'rgba(0, 122, 255, 0.1)',   accentShadow: 'rgba(0, 122, 255, 0.3)'   },
  ops:     { accent: '#f97316',                 accentBg: 'rgba(249, 115, 22, 0.1)',  accentShadow: 'rgba(249, 115, 22, 0.3)'  },
  admin:   { accent: 'var(--accent-color)',     accentBg: 'rgba(69, 183, 209, 0.1)',  accentShadow: 'rgba(69, 183, 209, 0.3)'  },
}

const theme = computed(() => THEME[props.role])

// ── 导航项 ────────────────────────────────────────────────────────────────────

const NAV_ITEMS = {
  teacher: [
    { path: '/teacher/dashboard',        label: '首页', icon: DataBoard, badge: null },
    { path: '/teacher/classes',          label: '班级', icon: School,    badge: null },
    { path: '/teacher/assignments',      label: '任务', icon: Edit,      badge: null },
    { path: '/teacher/my-courses',       label: '授课', icon: Notebook,  badge: null },
    { path: '/teacher/courses',          label: '课程', icon: Reading,   badge: null },
    { path: '/teacher/my-students',      label: '学生', icon: User,      badge: null, featureKey: 'teacher.school_data_enabled' },
    { path: '/teacher/my-schedule',      label: '课表', icon: DataBoard, badge: null, featureKey: 'teacher.school_data_enabled' },
  ],
  user: [
    { path: '/user',            label: '首页', icon: House,    badge: null },
    { path: '/user/user-texts', label: '我的', icon: Upload,   badge: null },
    { path: '/articles',        label: '文章', icon: Reading,  badge: null },
    { path: '/user/vocabulary', label: '生词', icon: Document, badge: null },
  ],
  ops: [
    { path: '/ops/monitoring',      label: '监控', icon: DataLine    },
    { path: '/ops/backups',         label: '备份', icon: FolderOpened },
    { path: '/ops/users',           label: '用户', icon: User        },
    { path: '/ops/ai-providers',    label: 'AI',   icon: Connection  },
    { path: '/ops/feature-toggles', label: '开关', icon: Switch      },
    { path: '/ops/school-data',         label: '数据', icon: Coin,        featureKey: 'ops.school_data_config' },
    { path: '/ops/school-data-browser',  label: '浏览', icon: DataLine,    featureKey: 'ops.school_data_browser' },
    { path: '/ops/logs',            label: '日志', icon: Document    },
    { path: '/ops/update',          label: '更新', icon: Refresh     },
  ],
  admin: [
    { path: '/admin/dashboard', label: '概览', icon: DataBoard, badge: null, featureKey: 'admin.dashboard' },
    { path: '/admin/courses',   label: '课程', icon: Reading,   badge: null, featureKey: 'admin.courses' },
    { path: '/admin/classes',   label: '班级', icon: School,    badge: null, featureKey: 'admin.classes' },
    { path: '/admin/users',     label: '用户', icon: User,      badge: null, featureKey: 'admin.users' },
  ],
}

const navItems = computed(() => {
  const items = NAV_ITEMS[props.role] || []
  if (props.role === 'ops' || props.role === 'admin' || props.role === 'teacher') {
    return items.filter(item => {
      if (!item.featureKey) return true
      const val = featureToggles.value[item.featureKey]
      return val === undefined || val === true || val === 'true'
    })
  }
  return items
})

// admin 专属：二级管理菜单
const MORE_ITEMS = {
  admin: [
    { path: '/admin/texts',          label: '课文管理', icon: Reading,  featureKey: 'admin.texts' },
    { path: '/admin/units',          label: '单元管理', icon: Reading,  featureKey: 'admin.units' },
    { path: '/admin/words',          label: '单词管理', icon: EditPen,  featureKey: 'admin.words' },
    { path: '/admin/content',        label: '内容管理', icon: Document },
    { path: '/admin/assignments',    label: '作业管理', icon: Document, featureKey: 'admin.assignments' },
    { path: '/admin/sso-pending',    label: 'SSO审批', icon: User,     featureKey: 'admin.sso_approval' },
  ],
}

const moreItems = computed(() => {
  const items = MORE_ITEMS[props.role] || []
  if (props.role === 'admin') {
    return items.filter(item => {
      if (!item.featureKey) return true
      const val = featureToggles.value[item.featureKey]
      return val === undefined || val === true || val === 'true'
    })
  }
  return items
})

const isManagementRouteActive = computed(() => {
  if (props.role !== 'admin') return false
  return MORE_ITEMS.admin.some(
    (item) => route.path === item.path || route.path.startsWith(item.path + '/'),
  )
})

// ── 页面标题 ──────────────────────────────────────────────────────────────────

const ROUTE_MAP = {
  teacher: {
    '/teacher/dashboard':   '教学首页',
    '/teacher/classes':     '班级管理',
    '/teacher/assignments': '任务管理',
    '/teacher/my-courses':  '我的授课',
    '/teacher/courses':     '课程浏览',
  },
  user: {
    '/user':            '首页',
    '/user/user-texts': '我的上传',
    '/articles':        '文章',
    '/user/vocabulary': '我的生词',
  },
  ops: {
    '/ops/monitoring':      '系统监控',
    '/ops/backups':         '数据备份',
    '/ops/users':           '用户管理',
    '/ops/ai-providers':    'AI 服务管理',
    '/ops/feature-toggles': '功能开关',
    '/ops/school-data':         '学校数据',
    '/ops/school-data-browser':  '数据浏览',
    '/ops/update':          '系统更新',
  },
  admin: {
    '/admin/dashboard':   '管理概览',
    '/admin/courses':     '课程管理',
    '/admin/classes':     '班级管理',
    '/admin/users':       '用户管理',
    '/admin/units':       '单元管理',
    '/admin/words':       '单词管理',
    '/admin/videos':      '视频管理',
    '/admin/content':     '内容管理',
    '/admin/assignments': '作业管理',
    '/admin/sso-pending': 'SSO 用户审批',
    '/admin/content-review': '内容审核',
  },
}

const DEFAULT_TITLE = { teacher: '教师端', user: '用户端', ops: '运维面板', admin: '管理员端' }

const pageTitle = computed(
  () => route.meta?.title || ROUTE_MAP[props.role]?.[route.path] || DEFAULT_TITLE[props.role],
)

// ── 用户头像 ──────────────────────────────────────────────────────────────────

const DEFAULT_AVATAR = 'https://cube.elemecdn.com/0/88/03b0d39583f48206768a7534e55bcpng.png'

const userAvatar = computed(() => userStore.userInfo?.avatar || DEFAULT_AVATAR)

// ── 用户菜单项（供测试访问，与模板保持一致）────────────────────────────────────

const userMenuItems = computed(() => {
  if (props.role === 'ops') {
    return [
      { command: 'elevate', label: userStore.userRole === 'ADMIN' ? '返回管理端' : '切换到管理端' },
      { command: 'logout',  label: '退出登录' },
    ]
  }
  const items = [
    { command: 'profile',  label: '个人资料' },
    { command: 'settings', label: '设置' },
  ]
  if (props.role === 'admin') {
    items.push({ command: 'system', label: '系统设置' })
  }
  items.push({ command: 'logout', label: '退出登录' })
  return items
})

// ── 键盘支持（Escape 关闭下拉时防止浏览器默认行为） ──────────────────────────

const handleKeyDown = (event) => {
  if (event.key === 'Escape') event.preventDefault()
}

const handleDropdownVisible = (visible) => {
  if (visible) {
    document.addEventListener('keydown', handleKeyDown)
  } else {
    document.removeEventListener('keydown', handleKeyDown)
  }
}

// ── 用户菜单命令 ───────────────────────────────────────────────────────────────

const handleCommand = async (command) => {
  switch (command) {
    case 'profile':
      ElMessage.info('个人资料功能开发中')
      break
    case 'settings':
      ElMessage.info('设置功能开发中')
      break
    case 'system':  // admin 专属
      ElMessage.info('系统设置功能开发中')
      break
    case 'elevate':  // ops 专属
      if (userStore.userRole === 'ADMIN') {
        router.push('/admin')
      } else {
        elevationTarget.value = 'ADMIN'
        showElevationDialog.value = true
      }
      break
    case 'logout':
      try {
        await ElMessageBox.confirm('确认退出登录？', '提示', {
          confirmButtonText: '确定',
          cancelButtonText: '取消',
          type: 'warning',
        })
        try {
          await userStore.logout()
        } catch (logoutError) {
          console.error('Logout API failed:', logoutError)
          ElMessage.error('退出登录失败，请稍后重试')
          return
        }
        ElMessage.success('退出登录成功')
        // SSO 启用时跳转到 CAS 登出页（统一身份认证要求：退出后必须回到 CAS 登出页）
        if (ssoEnabled.value) {
          try {
            const res = await getSsoLogoutUrl()
            if (res?.code === 200 && res.data?.logoutUrl) {
              window.location.href = res.data.logoutUrl
              return
            }
          } catch (e) {
            console.error('获取 CAS 登出 URL 失败:', e)
          }
        }
        // 非 SSO 或获取 CAS 登出 URL 失败时，回退到本地登录页
        if (['ops', 'admin'].includes(props.role)) {
          router.replace('/login')
        } else {
          await router.push('/login')
        }
      } catch (error) {
        if (error?.message === 'Cancel') return
        console.error('Logout process failed:', error)
        ElMessage.error('操作失败，请稍后重试')
      }
      break
  }
}

// ops 专属：提权成功后跳转
const onElevationSuccess = () => {
  router.push('/admin')
}

// ── 生命周期 ──────────────────────────────────────────────────────────────────

// SSO 状态（用于 CAS 登出重定向）
const ssoEnabled = ref(false)

onMounted(async () => {
  // 权限守卫（同步执行，确保测试和首次渲染立即可用）
  if (props.role === 'teacher' && !userStore.isTeacher) {
    console.warn('Unauthorized: non-teacher user attempted to access teacher layout')
    ElMessage.error('您没有权限访问此区域')
    router.push('/login')
  }
  if (props.role === 'ops' && (!userStore.isLoggedIn || !userStore.hasOpsAccess)) {
    ElMessage.error('权限不足，请以运维人员身份登录')
    router.replace('/login')
  }
  if (props.role === 'admin' && (!userStore.isLoggedIn || !userStore.hasAdminAccess)) {
    ElMessage.error('权限不足，请以管理员身份登录')
    router.replace('/login')
  }

  // 加载功能开关（admin/ops 走新的 /api/user/feature-toggles，能享受用户级覆盖）
  if (props.role === 'ops' || props.role === 'admin') {
    try {
      await loadFeatureToggles({ authenticated: userStore.isLoggedIn })
    } catch (e) {
      console.error('Failed to load feature toggles:', e)
    }
  }

  // admin 角色：加载待审核计数并定时轮询
  if (props.role === 'admin') {
    loadPendingReviewCount()
    pendingPollTimer = setInterval(loadPendingReviewCount, 60000)
  }

  // 检查 SSO 是否启用（静默失败，不影响主流程）
  try {
    const ssoRes = await getSsoStatus()
    if (ssoRes?.code === 200) {
      ssoEnabled.value = ssoRes.data?.enabled || false
      sessionStorage.setItem('sso_enabled', ssoEnabled.value ? 'true' : 'false')
    }
  } catch {
    ssoEnabled.value = false
  }
})

const loadPendingReviewCount = async () => {
  try {
    const [textRes, videoRes] = await Promise.all([
      getReviewTexts({ page: 1, size: 1, status: 'PENDING' }),
      getReviewVideos({ page: 1, size: 1, status: 'PENDING' })
    ])
    const tp = textRes?.data?.pendingCount || 0
    const vp = videoRes?.data?.pendingCount || 0
    pendingReviewCount.value = tp + vp
  } catch (e) {
    // 静默失败
  }
}

onUnmounted(() => {
  document.removeEventListener('keydown', handleKeyDown)
  if (pendingPollTimer) {
    clearInterval(pendingPollTimer)
    pendingPollTimer = null
  }
})
</script>

<style scoped>
.mobile-layout {
  height: 100dvh;
  width: 100vw;
  display: flex;
  flex-direction: column;
  background: var(--bg-secondary);
}

/* 顶部标题栏 */
.mobile-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0 16px;
  height: 56px;
  background: var(--surface-color);
  border-bottom: 1px solid var(--border-light);
  position: relative;
  z-index: 100;
  box-shadow: var(--shadow-light);
}

.header-left { flex: 1; }

.page-title {
  font-size: 18px;
  font-weight: 600;
  color: var(--text-primary);
  margin: 0;
  background: var(--gradient-secondary);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

/* ops/admin 各自的标题渐变色 */
.role-ops .page-title {
  background: linear-gradient(135deg, #f97316 0%, #ea580c 100%);
  -webkit-background-clip: text;
  background-clip: text;
}
.role-admin .page-title {
  background: var(--gradient-accent);
  -webkit-background-clip: text;
  background-clip: text;
}

/* 主内容区域 */
.mobile-main {
  flex: 1;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  padding-bottom: calc(60px + max(8px, env(safe-area-inset-bottom)));
}

.content-container {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 16px;
  background: var(--bg-secondary);
}

/* 底部导航栏 */
.mobile-nav {
  background: var(--surface-color);
  border-top: 1px solid var(--border-light);
  box-shadow: 0 -2px 12px rgba(0, 0, 0, 0.08);
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  z-index: 100;
  padding-bottom: max(8px, env(safe-area-inset-bottom));
}

/* ops 导航栏特殊样式（毛玻璃效果）*/
.role-ops .mobile-nav {
  background: linear-gradient(to top, var(--surface-color) 0%, rgba(255, 255, 255, 0.98) 100%);
  box-shadow: 0 -4px 20px rgba(0, 0, 0, 0.06);
  backdrop-filter: blur(10px);
  padding-bottom: max(10px, env(safe-area-inset-bottom));
}

.nav-container {
  display: flex;
  justify-content: space-around;
  align-items: center;
  padding: 8px 0 max(8px, env(safe-area-inset-bottom));
}

.role-ops .nav-container {
  padding: 10px 8px max(10px, env(safe-area-inset-bottom));
  gap: 4px;
}

.nav-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 8px 12px;
  text-decoration: none;
  color: var(--text-tertiary);
  transition: all 0.3s ease;
  position: relative;
  min-width: 44px;
  border-radius: 12px;
  margin: 0 4px;
}

/* ops 导航项特殊样式 */
.role-ops .nav-item {
  padding: 6px 10px;
  min-width: 50px;
  max-width: 80px;
  border-radius: 10px;
  margin: 0 2px;
  flex: 1;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}

.nav-item:hover {
  color: var(--text-secondary);
  background-color: var(--bg-tertiary);
}

.role-ops .nav-item:hover {
  color: var(--nav-accent);
  background-color: var(--nav-accent-bg);
  transform: translateY(-2px);
}

.nav-item.active {
  color: var(--nav-accent);
  background: linear-gradient(135deg, var(--nav-accent-bg) 0%, transparent 100%);
}

.role-ops .nav-item.active {
  box-shadow: 0 2px 8px var(--nav-accent-shadow);
}

.nav-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  margin-bottom: 4px;
  transition: transform 0.3s ease;
}

.role-ops .nav-icon { width: 26px; height: 26px; margin-bottom: 3px; }

.nav-item.active .nav-icon { transform: scale(1.1); }

.nav-label {
  font-size: 11px;
  font-weight: 500;
  text-align: center;
  line-height: 1.2;
  transition: font-size 0.3s ease;
}

.role-ops .nav-label { line-height: 1.3; white-space: nowrap; }

.nav-item.active .nav-label { font-size: 12px; font-weight: 600; }

.nav-badge {
  position: absolute;
  top: 6px;
  right: 8px;
  background: var(--nav-accent);
  color: white;
  font-size: 10px;
  font-weight: 600;
  padding: 2px 6px;
  border-radius: 10px;
  min-width: 16px;
  text-align: center;
  line-height: 1;
  box-shadow: 0 2px 4px var(--nav-accent-shadow);
  animation: pulse 2s infinite;
}

@keyframes pulse {
  0%   { box-shadow: 0 2px 4px var(--nav-accent-shadow); }
  50%  { box-shadow: 0 2px 8px var(--nav-accent-shadow); }
  100% { box-shadow: 0 2px 4px var(--nav-accent-shadow); }
}

/* 用户菜单 & 头像 */
.user-menu-item,
.management-menu-item { cursor: pointer; }

.nav-user-avatar,
.nav-management-trigger {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
}

.avatar-img {
  width: 24px;
  height: 24px;
  border-radius: 50%;
  object-fit: cover;
  border: 2px solid var(--nav-accent);
}

.user-menu-item.active .avatar-img,
.user-menu-item:hover .avatar-img {
  border-color: var(--nav-accent);
  box-shadow: 0 0 0 2px var(--nav-accent-shadow);
}

.role-admin .user-menu-item:hover .avatar-img {
  transform: scale(1.05);
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}

/* admin 管理菜单下拉 router-link */
.dropdown-link {
  display: block;
  text-decoration: none;
  color: inherit;
}

.dropdown-item-content {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
}

.dropdown-item-left {
  display: flex;
  align-items: center;
}

.menu-badge {
  margin-left: 8px;
}

.management-menu-item {
  position: relative;
}

/* 触摸体验 */
.nav-item,
.user-menu-item,
.management-menu-item {
  -webkit-tap-highlight-color: transparent;
  touch-action: manipulation;
}

/* 自定义滚动条 */
.content-container::-webkit-scrollbar       { width: 4px; }
.content-container::-webkit-scrollbar-track { background: transparent; }
.content-container::-webkit-scrollbar-thumb { background: var(--border-medium); border-radius: 2px; }
.content-container::-webkit-scrollbar-thumb:hover { background: var(--text-tertiary); }

/* 过渡动画 */
.fade-enter-active,
.fade-leave-active  { transition: opacity 0.3s ease, transform 0.3s ease; }
.fade-enter-from    { opacity: 0; transform: translateY(10px); }
.fade-leave-to      { opacity: 0; transform: translateY(-10px); }

/* 安全区域 */
@supports (padding: max(0px)) {
  .mobile-header {
    padding-left:  max(16px, env(safe-area-inset-left));
    padding-right: max(16px, env(safe-area-inset-right));
    padding-top:   max(0px,  env(safe-area-inset-top));
  }
  .content-container {
    padding-left:  max(16px, env(safe-area-inset-left));
    padding-right: max(16px, env(safe-area-inset-right));
  }
}

/* 响应式 */
@media (max-width: 480px) {
  .mobile-header     { padding: 0 12px; height: 52px; }
  .page-title        { font-size: 16px; }
  .content-container { padding: 12px; }
  .nav-item          { padding: 6px 8px; min-width: 40px; margin: 0 2px; }
  .nav-icon          { width: 22px; height: 22px; margin-bottom: 3px; }
  .nav-label         { font-size: 10px; }
  .nav-item.active .nav-label { font-size: 11px; }
}

@media (max-width: 360px) {
  .mobile-header     { padding: 0 8px; height: 48px; }
  .page-title        { font-size: 15px; }
  .content-container { padding: 8px; }
  .nav-item          { padding: 4px 6px; margin: 0 1px; }
  .nav-icon          { width: 20px; height: 20px; margin-bottom: 2px; }
  .nav-label         { font-size: 9px; }
  .nav-item.active .nav-label { font-size: 10px; }
}

@media (min-width: 768px) and (max-width: 1024px) {
  .mobile-header     { padding: 0 20px; height: 64px; }
  .page-title        { font-size: 20px; }
  .content-container { padding: 20px; }
  .nav-item          { padding: 10px 16px; min-width: 48px; }
  .nav-icon          { width: 28px; height: 28px; margin-bottom: 6px; }
  .nav-label         { font-size: 12px; }
  .nav-item.active .nav-label { font-size: 13px; }
}

@media (max-height: 600px) and (orientation: landscape) {
  .mobile-header  { height: 44px; }
  .page-title     { font-size: 16px; }
  .nav-container  { padding: 4px 0; }
  .nav-item       { padding: 4px 8px; }
  .nav-icon       { width: 20px; height: 20px; margin-bottom: 2px; }
  .nav-label      { font-size: 9px; }
}
</style>
