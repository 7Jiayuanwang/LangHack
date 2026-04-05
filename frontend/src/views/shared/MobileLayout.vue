<template>
  <div
    class="mobile-layout"
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
          <router-view />
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
                <el-dropdown-item command="profile">个人资料</el-dropdown-item>
                <el-dropdown-item command="settings">设置</el-dropdown-item>
                <el-dropdown-item command="logout" divided>退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </div>
    </nav>
  </div>
</template>

<script setup>
import { computed, onMounted, onUnmounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useUserStore } from '@/store/user'
import { ElMessage, ElMessageBox } from 'element-plus'
import { DataBoard, School, Edit, Reading, Upload, Document, House } from '@element-plus/icons-vue'

const props = defineProps({
  role: {
    type: String,
    required: true,
    validator: (v) => ['teacher', 'user'].includes(v),
  },
})

const router = useRouter()
const route  = useRoute()
const userStore = useUserStore()

// ── 主题（accent 颜色）────────────────────────────────────────────────────────

const THEME = {
  teacher: {
    accent:       'var(--secondary-color)',
    accentBg:     'rgba(78, 205, 196, 0.1)',
    accentShadow: 'rgba(78, 205, 196, 0.3)',
  },
  user: {
    accent:       'var(--info-color)',
    accentBg:     'rgba(0, 122, 255, 0.1)',
    accentShadow: 'rgba(0, 122, 255, 0.3)',
  },
}

const theme = computed(() => THEME[props.role])

// ── 导航项 ────────────────────────────────────────────────────────────────────

const NAV_ITEMS = {
  teacher: [
    { path: '/teacher/dashboard',   label: '首页', icon: DataBoard, badge: null },
    { path: '/teacher/classes',     label: '班级', icon: School,    badge: null },
    { path: '/teacher/assignments', label: '任务', icon: Edit,      badge: null },
    { path: '/teacher/courses',     label: '课程', icon: Reading,   badge: null },
  ],
  user: [
    { path: '/user',              label: '首页', icon: House,    badge: null },
    { path: '/user/user-texts',   label: '我的', icon: Upload,   badge: null },
    { path: '/user/vocabulary',   label: '生词', icon: Document, badge: null },
  ],
}

const navItems = computed(() => NAV_ITEMS[props.role] || [])

// ── 页面标题 ──────────────────────────────────────────────────────────────────

const ROUTE_MAP = {
  teacher: {
    '/teacher/dashboard':   '教学首页',
    '/teacher/classes':     '班级管理',
    '/teacher/assignments': '任务管理',
    '/teacher/courses':     '课程浏览',
  },
  user: {
    '/user':             '首页',
    '/user/user-texts':  '我的上传',
    '/user/vocabulary':  '我的生词',
  },
}

const DEFAULT_TITLE = { teacher: '教师端', user: '用户端' }

const pageTitle = computed(
  () => route.meta?.title || ROUTE_MAP[props.role]?.[route.path] || DEFAULT_TITLE[props.role],
)

// ── 用户头像 ──────────────────────────────────────────────────────────────────

const DEFAULT_AVATAR = 'https://cube.elemecdn.com/0/88/03b0d39583f48206768a7534e55bcpng.png'

const userAvatar = computed(() => userStore.userInfo?.avatar || DEFAULT_AVATAR)

// ── 键盘支持（Escape 关闭下拉，el-dropdown 挂载期间） ─────────────────────────

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
        await router.push('/login')
      } catch (error) {
        if (error?.message === 'Cancel') return
        console.error('Logout process failed:', error)
        ElMessage.error('操作失败，请稍后重试')
      }
      break
  }
}

// ── 生命周期 ──────────────────────────────────────────────────────────────────

onMounted(() => {
  if (props.role === 'teacher' && !userStore.isTeacher) {
    console.warn('Unauthorized: non-teacher user attempted to access teacher layout')
    ElMessage.error('您没有权限访问此区域')
    router.push('/login')
  }
})

onUnmounted(() => {
  document.removeEventListener('keydown', handleKeyDown)
})
</script>

<style scoped>
.mobile-layout {
  height: 100vh;
  width: 100vw;
  display: flex;
  flex-direction: column;
  background: var(--bg-secondary);
  overflow: hidden;
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

.header-left {
  flex: 1;
}

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

/* 主内容区域 */
.mobile-main {
  flex: 1;
  overflow: hidden;
  display: flex;
  flex-direction: column;
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
  position: relative;
  z-index: 100;
  padding-bottom: max(8px, env(safe-area-inset-bottom));
}

.nav-container {
  display: flex;
  justify-content: space-around;
  align-items: center;
  padding: 8px 0 max(8px, env(safe-area-inset-bottom));
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

.nav-item:hover {
  color: var(--text-secondary);
  background-color: var(--bg-tertiary);
}

.nav-item.active {
  color: var(--nav-accent);
  background: linear-gradient(135deg, var(--nav-accent-bg) 0%, transparent 100%);
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

.nav-item.active .nav-icon {
  transform: scale(1.1);
}

.nav-label {
  font-size: 11px;
  font-weight: 500;
  text-align: center;
  line-height: 1.2;
  transition: font-size 0.3s ease;
}

.nav-item.active .nav-label {
  font-size: 12px;
  font-weight: 600;
}

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

/* 用户菜单项 */
.user-menu-item {
  cursor: pointer;
}

.nav-user-avatar {
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

/* 触摸体验 */
.nav-item,
.user-menu-item {
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
  .mobile-header    { padding: 0 12px; height: 52px; }
  .page-title       { font-size: 16px; }
  .content-container { padding: 12px; }
  .nav-item         { padding: 6px 8px; min-width: 40px; margin: 0 2px; }
  .nav-icon         { width: 22px; height: 22px; margin-bottom: 3px; }
  .nav-label        { font-size: 10px; }
  .nav-item.active .nav-label { font-size: 11px; }
}

@media (max-width: 360px) {
  .mobile-header    { padding: 0 8px; height: 48px; }
  .page-title       { font-size: 15px; }
  .content-container { padding: 8px; }
  .nav-item         { padding: 4px 6px; margin: 0 1px; }
  .nav-icon         { width: 20px; height: 20px; margin-bottom: 2px; }
  .nav-label        { font-size: 9px; }
  .nav-item.active .nav-label { font-size: 10px; }
}

@media (min-width: 768px) and (max-width: 1024px) {
  .mobile-header    { padding: 0 20px; height: 64px; }
  .page-title       { font-size: 20px; }
  .content-container { padding: 20px; }
  .nav-item         { padding: 10px 16px; min-width: 48px; }
  .nav-icon         { width: 28px; height: 28px; margin-bottom: 6px; }
  .nav-label        { font-size: 12px; }
  .nav-item.active .nav-label { font-size: 13px; }
}

@media (max-height: 600px) and (orientation: landscape) {
  .mobile-header    { height: 44px; }
  .page-title       { font-size: 16px; }
  .nav-container    { padding: 4px 0; }
  .nav-item         { padding: 4px 8px; }
  .nav-icon         { width: 20px; height: 20px; margin-bottom: 2px; }
  .nav-label        { font-size: 9px; }
}
</style>
