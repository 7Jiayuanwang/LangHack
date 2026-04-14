<template>
  <div class="trending-app">
    <!-- 状态栏占位 -->
    <div class="status-bar"></div>

    <!-- 顶部导航栏 - 时尚设计（文章页有自带 header，此处隐藏避免重叠） -->
    <header class="trend-header" v-show="pageTitle">
      <div class="header-container">
        <div class="header-center">
          <h1 class="page-title">{{ pageTitle }}</h1>
        </div>
      </div>
    </header>

    <!-- 主内容区域 -->
    <main class="main-content">
      <div class="content-wrapper">
        <router-view />
      </div>
    </main>

    <!-- 底部导航栏 - 小红书风格 -->
    <nav class="trend-nav">
      <div class="nav-wrapper">
        <router-link
          v-for="(item, index) in navigationItems"
          :key="index"
          :to="item.path"
          class="nav-item"
          :class="{ 'nav-item--active': isActiveRoute(item.path) }"
        >
          <div class="nav-icon-wrapper">
            <component :is="item.icon" class="nav-icon" />
            <div class="nav-icon-ripple"></div>
          </div>
          <span class="nav-label">{{ item.label }}</span>
          <div class="nav-dot" v-if="item.badge"></div>
        </router-link>
        
        <!-- 用户菜单 - 移到底部导航栏最右侧 -->
        <div class="nav-item" :class="{ 'nav-item--active': showUserMenu }" @click="toggleUserMenu">
          <div class="nav-icon-wrapper">
            <img :src="userAvatar" class="nav-user-avatar" :alt="userStore.userInfo?.realName || '用户'" />
            <div class="nav-icon-ripple"></div>
          </div>
          <span class="nav-label">我的</span>
        </div>
      </div>
    </nav>

    <!-- 用户下拉菜单 - 从底部弹出 -->
    <div class="user-menu-simple" v-if="showUserMenu" @click.self="closeUserMenu" @keydown.escape="closeUserMenu">
      <div class="simple-menu">
        <div class="simple-menu-header">
          <div class="simple-avatar">
            <img :src="userAvatar" :alt="userStore.userInfo?.realName || '用户'" />
          </div>
          <span class="simple-username">{{ userStore.userInfo?.realName || userStore.userInfo?.username }}</span>
          <span class="simple-role">{{ roleDisplayName }}</span>
        </div>
        <div class="simple-menu-actions">
          <button class="simple-btn" @click="handleUserAction('logout')">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4"/>
              <polyline points="16,17 21,12 16,7"/>
              <line x1="21" y1="12" x2="9" y2="12"/>
            </svg>
            退出登录
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, markRaw } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useUserStore } from '@/store/user'
import { getSsoStatus, getSsoLogoutUrl } from '@/api/sso'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getFeatureToggles, getPersonalFeatureToggles } from '@/api/systemConfig'
import {
  DataBoard,
  Reading,
  House,
  Edit,
  Document,
  ChatLineRound,
  MessageBox,
  Notebook,
  VideoPlay
} from '@element-plus/icons-vue'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()
const showUserMenu = ref(false)
const screenWidth = ref(window.innerWidth)
const featureToggles = ref({})
const ssoEnabled = ref(false)

// 处理键盘事件关闭菜单
const handleKeyDown = (event) => {
  if (event.key === 'Escape' && showUserMenu.value) {
    closeUserMenu()
  }
}

// 所有导航项配置（含 toggleKey 用于功能开关过滤）
const allNavigationItems = [
  { path: '/student/dashboard', label: '发现', icon: markRaw(DataBoard), badge: null, toggleKey: 'page_dashboard' },
  { path: '/student/classes', label: '社区', icon: markRaw(House), badge: null, toggleKey: 'page_classes' },
  { path: '/student/assignments', label: '任务', icon: markRaw(Edit), badge: null, toggleKey: 'page_assignments' },
  { path: '/student/articles', label: '文章', icon: markRaw(ChatLineRound), badge: null, toggleKey: 'page_articles' },
  { path: '/student/vocabulary', label: '单词', icon: markRaw(Document), badge: null, toggleKey: 'page_vocabulary' },
  { path: '/student/my-study', label: '学习', icon: markRaw(Reading), badge: null, toggleKey: 'page_my_study' },
  { path: '/student/ai-video', label: '视频', icon: markRaw(VideoPlay), badge: null, toggleKey: 'page_ai_video' },
  { path: '/student/ai-qa', label: 'AI问答', icon: markRaw(MessageBox), badge: null, toggleKey: 'page_ai_qa' },
  { path: '/student/user-texts', label: '我的创作', icon: markRaw(Notebook), badge: null, toggleKey: 'page_user_texts' }
]

// 根据功能开关过滤导航项
const navigationItems = computed(() => {
  return allNavigationItems.filter(item => {
    if (!item.toggleKey) return true  // 无 toggleKey 的项始终显示
    const val = featureToggles.value[item.toggleKey]
    return val === undefined || val === true || val === 'true'  // 未配置或 true 时显示
  })
})

// 页面标题
const pageTitle = computed(() => {
  const routeMap = {
    '/student/dashboard': '发现',
    '/student/courses': '课程学习',
    '/student/classes': '学习社区',
    '/student/assignments': '学习任务',
    '/student/articles': '',
    '/student/vocabulary': '单词本',
    '/student/ai-video': 'AI 视频字幕',
    '/student/ai-qa': 'AI 问答',
    '/student/user-texts': '我的创作',
    '/student/my-study': '我的学习'
  }
  return routeMap[route.path] || route.meta?.title || '英语学习'
})

// 角色显示名称
const roleDisplayName = computed(() => {
  const roleMap = {
    'ADMIN': '管理员',
    'TEACHER': '教师',
    'STUDENT': '学习者',
    'USER': '创作者'
  }
  return roleMap[userStore.userRole] || '用户'
})

// 用户头像
const userAvatar = computed(() => {
  const avatar = userStore.userInfo?.avatar
  // 多层防御：检查 avatar 是否有效，如果不存在、为空或非字符串，使用默认头像
  if (avatar && typeof avatar === 'string' && avatar.trim().length > 0) {
    return avatar
  }
  return 'https://cube.elemecdn.com/0/88/03b0d39583f48206768a7534e55bcpng.png'
})

// 检查路由是否激活（支持动态路由参数）
const isActiveRoute = (path) => {
  const currentPath = route.path
  // 完整匹配 或 当前路由是该路径的子路由
  if (currentPath === path || currentPath.startsWith(path + '/')) {
    return true
  }
  // 处理 /student/courses?courseId=xxx 这样的带查询参数的情况
  if (path === '/student/courses' && currentPath === '/student/courses') {
    return true
  }
  return false
}

// 切换用户菜单
const toggleUserMenu = () => {
  showUserMenu.value = !showUserMenu.value
}

// 关闭用户菜单
const closeUserMenu = () => {
  showUserMenu.value = false
}

// 处理用户操作
const handleUserAction = async (action) => {
  closeUserMenu()

  switch (action) {
    case 'profile':
      ElMessage.info('个人资料功能开发中')
      break
    case 'settings':
      ElMessage.info('设置功能开发中')
      break
    case 'logout':
      try {
        await ElMessageBox.confirm('确认退出登录？', '提示', {
          confirmButtonText: '确定',
          cancelButtonText: '取消',
          type: 'warning'
        })
        await userStore.logout()
        ElMessage.success('退出登录成功')
        // SSO 启用时跳转到 CAS 登出页
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
        router.push('/login')
      } catch (error) {
        // 用户取消操作
      }
      break
  }
}

// 监听屏幕宽度变化
const handleResize = () => {
  screenWidth.value = window.innerWidth
}

onMounted(() => {
  window.addEventListener('resize', handleResize)
  window.addEventListener('keydown', handleKeyDown)
  // 检查 SSO 是否启用（静默失败，不影响主流程）
  getSsoStatus().then(res => {
    if (res?.code === 200) {
      ssoEnabled.value = res.data?.enabled || false
      sessionStorage.setItem('sso_enabled', ssoEnabled.value ? 'true' : 'false')
    }
  }).catch(() => {})
  // 加载功能开关配置（登录后优先使用个性化接口，失败时回退公开接口）
  getPersonalFeatureToggles().then(res => {
    if (res?.code === 200 && res?.data) {
      featureToggles.value = res.data
    }
  }).catch(() => {
    getFeatureToggles().then(res => {
      if (res?.code === 200 && res?.data) {
        featureToggles.value = res.data
      }
    }).catch(() => {})
  })
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  window.removeEventListener('keydown', handleKeyDown)
})
</script>

<style scoped>
/* 时尚应用容器 */
.trending-app {
  height: 100vh;
  width: 100vw;
  display: flex;
  flex-direction: column;
  background: var(--bg-secondary);
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', 'PingFang SC', 'Microsoft YaHei', sans-serif;
  overflow: hidden;
  position: relative;
}

/* 状态栏 */
.status-bar {
  height: env(safe-area-inset-top, 0);
  background: var(--bg-primary);
  position: relative;
  z-index: 1000;
}

/* 时尚顶部导航 */
.trend-header {
  background: var(--bg-primary);
  position: relative;
  z-index: 999;
  backdrop-filter: blur(20px);
  -webkit-backdrop-filter: blur(20px);
  border-bottom: 1px solid var(--border-light);
}

.header-container {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 56px;
  padding: 0 20px;
  max-width: 100%;
}

.header-center {
  flex: 1;
  text-align: center;
}

.page-title {
  font-size: 18px;
  font-weight: 700;
  color: var(--text-primary);
  margin: 0;
  letter-spacing: -0.5px;
}

/* 用户头像在底部导航栏 */
.nav-user-avatar {
  width: 20px;
  height: 20px;
  border-radius: 50%;
  object-fit: cover;
  border: 2px solid var(--primary-color);
  transition: all 0.3s ease;
}

.nav-item--active .nav-user-avatar {
  border-color: var(--primary-color);
  box-shadow: 0 0 0 2px rgba(0, 102, 204, 0.2);
  transform: scale(1.1);
}

/* 主内容区域 */
.main-content {
  flex: 1;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.content-wrapper {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  -webkit-overflow-scrolling: touch;
  background: var(--bg-secondary);
}

/* 时尚底部导航 */
.trend-nav {
  background: var(--glass-bg);
  backdrop-filter: blur(20px);
  -webkit-backdrop-filter: blur(20px);
  border-top: 1px solid var(--glass-border);
  position: relative;
  z-index: 998;
  padding-bottom: env(safe-area-inset-bottom, 0);
}

.nav-wrapper {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 2px 12px 2px;
  max-width: 100%;
  margin: 0 auto;
  width: 100%;
  flex-wrap: nowrap;
  overflow-x: auto;
  -webkit-overflow-scrolling: touch;
  scrollbar-width: none;
  gap: 2px;
}

.nav-wrapper::-webkit-scrollbar {
  display: none;
}

.nav-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  text-decoration: none;
  color: var(--text-tertiary);
  font-size: 9px;
  font-weight: 500;
  flex: 1 1 0;
  min-width: 0;
  max-width: none;
  padding: 4px 2px;
  position: relative;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  cursor: pointer;
  border-radius: 12px;
}

.nav-item:hover {
  transform: translateY(-1px);
}

.nav-item--active {
  color: var(--primary-color);
  background: rgba(0, 102, 204, 0.08);
}

.nav-icon-wrapper {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  margin-bottom: 4px;
}

.nav-icon {
  width: 20px;
  height: 20px;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  z-index: 2;
  position: relative;
}

.nav-item--active .nav-icon {
  transform: scale(1.2);
  filter: drop-shadow(0 2px 4px rgba(0, 102, 204, 0.3));
}

.nav-icon-ripple {
  position: absolute;
  top: 50%;
  left: 50%;
  width: 0;
  height: 0;
  border-radius: 50%;
  background: var(--primary-color);
  opacity: 0.2;
  transform: translate(-50%, -50%);
  transition: all 0.4s ease;
}

.nav-item--active .nav-icon-ripple {
  width: 32px;
  height: 32px;
  opacity: 0.1;
}

.nav-label {
  font-size: 8px;
  line-height: 1.2;
  text-align: center;
  font-weight: 600;
  transition: font-size 0.2s ease;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 100%;
  padding: 0 1px;
}

.nav-item--active .nav-label {
  font-size: 10px;
}

.nav-dot {
  position: absolute;
  top: 6px;
  right: calc(50% - 14px);
  width: 6px;
  height: 6px;
  background: var(--primary-color);
  border-radius: 50%;
  box-shadow: 0 0 0 2px var(--bg-primary), 0 0 8px rgba(0, 102, 204, 0.4);
  animation: pulse-dot 2s ease-in-out infinite;
}

@keyframes pulse-dot {
  0%, 100% { transform: scale(1); opacity: 1; }
  50% { transform: scale(1.2); opacity: 0.8; }
}

/* 简单的用户菜单 - 紧贴"我的"按钮上方弹出 */
.user-menu-simple {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  top: 0;
  z-index: 1001;
  background: rgba(0, 0, 0, 0.5);
  animation: fadeIn 0.2s ease;
  display: flex;
  align-items: flex-end;
  justify-content: center;
  padding-bottom: calc(env(safe-area-inset-bottom, 0) + 60px);
}

@keyframes fadeIn {
  from { opacity: 0; }
  to { opacity: 1; }
}

.simple-menu {
  background: var(--bg-primary);
  border-radius: 16px;
  box-shadow: var(--shadow-xl);
  border: 1px solid var(--border-light);
  overflow: hidden;
  min-width: 200px;
  max-width: 280px;
  margin-right: 20px;
  margin-bottom: 4px;
  animation: slideUpFromBottom 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}

@keyframes slideUpFromBottom {
  from {
    transform: translateY(20px);
    opacity: 0;
  }
  to {
    transform: translateY(0);
    opacity: 1;
  }
}

.simple-menu-header {
  padding: 16px;
  border-bottom: 1px solid var(--separator-color);
  text-align: center;
}

.simple-avatar {
  width: 50px;
  height: 50px;
  border-radius: 50%;
  margin: 0 auto 8px;
  overflow: hidden;
}

.simple-avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.simple-username {
  display: block;
  font-size: 14px;
  font-weight: 600;
  color: var(--text-primary);
  margin-bottom: 4px;
}

.simple-role {
  font-size: 12px;
  color: var(--text-tertiary);
  background: var(--bg-tertiary);
  padding: 2px 8px;
  border-radius: 8px;
}

.simple-menu-actions {
  padding: 8px;
}

.simple-btn {
  width: 100%;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 12px;
  background: none;
  border: none;
  border-radius: 8px;
  color: var(--text-primary);
  font-size: 14px;
  cursor: pointer;
  transition: all 0.2s ease;
}

.simple-btn:hover {
  background: var(--bg-tertiary);
  color: var(--error-color);
}

.simple-btn svg {
  flex-shrink: 0;
}

.menu-container {
  background: var(--bg-modal);
  backdrop-filter: blur(20px);
  -webkit-backdrop-filter: blur(20px);
  border-radius: 20px 20px 0 0;
  box-shadow: var(--shadow-xl);
  overflow: hidden;
  min-width: 300px;
  max-width: 100%;
  margin-bottom: env(safe-area-inset-bottom, 0);
  animation: slideUpMenu 0.4s cubic-bezier(0.4, 0, 0.2, 1);
}

@keyframes slideUpMenu {
  from {
    transform: translateY(100%);
    opacity: 0;
  }
  to {
    transform: translateY(0);
    opacity: 1;
  }
}

.slide-up-enter-active {
  animation: slideUpMenu 0.4s ease;
}

.slide-up-leave-active {
  animation: slideUpMenu 0.4s ease reverse;
}

.menu-header {
  display: flex;
  align-items: center;
  padding: 24px 24px 20px 24px;
  border-bottom: 1px solid var(--separator-color);
}

.profile-avatar {
  position: relative;
  width: 60px;
  height: 60px;
  margin-right: 16px;
}

.profile-avatar::before {
  content: '';
  position: absolute;
  top: -3px;
  left: -3px;
  right: -3px;
  bottom: -3px;
  border-radius: 50%;
  background: var(--gradient-brand);
  z-index: 0;
}

.profile-img {
  width: 100%;
  height: 100%;
  border-radius: 50%;
  object-fit: cover;
  position: relative;
  z-index: 1;
  background: white;
  border: 3px solid white;
}

.profile-info {
  flex: 1;
}

.profile-name {
  font-size: 18px;
  font-weight: 700;
  color: var(--text-primary);
  margin: 0 0 4px 0;
  letter-spacing: -0.5px;
}

.profile-role {
  font-size: 14px;
  color: var(--text-tertiary);
  margin: 0;
  background: var(--bg-tertiary);
  display: inline-block;
  padding: 4px 12px;
  border-radius: 12px;
  font-weight: 500;
}

.menu-actions {
  padding: 8px 0;
}

.menu-action {
  width: 100%;
  display: flex;
  align-items: center;
  padding: 16px 24px;
  background: none;
  border: none;
  text-align: left;
  font-size: 16px;
  color: var(--text-primary);
  cursor: pointer;
  transition: all 0.2s ease;
  gap: 16px;
  position: relative;
}

.menu-action:hover {
  background: var(--bg-tertiary);
}

.menu-action--danger {
  color: var(--error-color);
}

.menu-action--danger:hover {
  background: rgba(255, 59, 48, 0.08);
}

.action-icon {
  color: var(--text-tertiary);
  flex-shrink: 0;
  transition: color 0.2s ease;
}

.menu-action:hover .action-icon {
  color: var(--text-secondary);
}

.menu-action--danger .action-icon {
  color: var(--error-color);
}

/* 响应式设计 */
@media (max-width: 480px) {
  .header-container {
    padding: 0 16px;
  }

  .page-title {
    font-size: 17px;
  }

  /* 注意：.content-wrapper 不加 padding，避免破坏子页面（如 Articles）的 sticky 布局和 full-bleed 设计
     子页面自行管理内部 padding */

  .nav-wrapper {
    padding: 6px 2px 10px 2px;
  }

  .nav-item {
    padding: 3px 1px;
    min-width: 0;
    max-width: none;
    flex: 1 1 0;
  }

  .nav-icon-wrapper {
    width: 20px;
    height: 20px;
    margin-bottom: 2px;
  }

  .nav-icon {
    width: 18px;
    height: 18px;
  }

  .nav-label {
    font-size: 8px;
  }

  .nav-item--active .nav-label {
    font-size: 9px;
  }

  .user-menu-simple {
    padding-bottom: calc(env(safe-area-inset-bottom, 0) + 58px);
  }

  .simple-menu {
    max-width: 260px;
    margin-right: 16px;
    margin-bottom: 3px;
  }

  .menu-container {
    min-width: 100%;
    border-radius: 20px 20px 0 0;
  }

  .menu-header {
    padding: 20px 20px 16px 20px;
  }

  .profile-avatar {
    width: 50px;
    height: 50px;
  }

  .profile-name {
    font-size: 16px;
  }

  .profile-role {
    font-size: 12px;
  }

  .menu-action {
    padding: 14px 20px;
    font-size: 15px;
  }
}

@media (max-width: 360px) {
  .header-container {
    padding: 0 12px;
  }

  .page-title {
    font-size: 16px;
  }

  .nav-wrapper {
    padding: 4px 0 8px 0;
  }

  .nav-item {
    padding: 3px 1px;
    flex: 1 1 0;
    min-width: 0;
  }

  .nav-icon-wrapper {
    width: 20px;
    height: 20px;
    margin-bottom: 2px;
  }

  .nav-icon {
    width: 16px;
    height: 16px;
  }

  .nav-label {
    font-size: 8px;
  }
}

/* 横屏适配 */
@media (max-height: 600px) and (orientation: landscape) {
  .header-container {
    height: 48px;
  }

  .page-title {
    font-size: 16px;
  }

  .nav-wrapper {
    padding: 4px 0 6px 0;
  }

  .nav-icon-wrapper {
    width: 20px;
    height: 20px;
    margin-bottom: 2px;
  }

  .nav-icon {
    width: 16px;
    height: 16px;
  }

  .nav-label {
    font-size: 8px;
  }

  .menu-container {
    max-height: 60vh;
    overflow-y: auto;
  }
}

/* 触摸优化 */
.nav-item,
.user-section,
.menu-action {
  -webkit-tap-highlight-color: transparent;
  touch-action: manipulation;
}

/* 自定义滚动条 */
.content-wrapper::-webkit-scrollbar {
  width: 4px;
}

.content-wrapper::-webkit-scrollbar-track {
  background: transparent;
}

.content-wrapper::-webkit-scrollbar-thumb {
  background: var(--border-color);
  border-radius: 2px;
}

.content-wrapper::-webkit-scrollbar-thumb:hover {
  background: var(--text-tertiary);
}
</style>