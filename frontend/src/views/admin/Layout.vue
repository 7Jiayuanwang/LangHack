<template>
  <div class="mobile-layout">
    <!-- 顶部标题栏 -->
    <header class="mobile-header admin-header">
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
        
        <!-- 管理菜单 - 下拉菜单形式 -->
        <div 
          class="nav-item management-menu-item"
          :class="{ active: isManagementRouteActive }"
        >
          <el-dropdown trigger="click" placement="top" @command="handleManagementCommand">
            <div class="nav-management-trigger">
              <div class="nav-icon">
                <component :is="Setting" />
              </div>
              <span class="nav-label">管理</span>
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
                    <component :is="item.icon" style="margin-right: 8px; width: 16px; height: 16px;" />
                    <span>{{ item.label }}</span>
                  </el-dropdown-item>
                </router-link>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
        
        <!-- 用户菜单 - 移到底部导航栏最右侧 -->
        <div class="nav-item user-menu-item">
          <el-dropdown @command="handleCommand" placement="top-end">
            <div class="nav-user-avatar">
              <div class="nav-icon">
                <img :src="userAvatar" :alt="userStore.userInfo?.realName || '管理员'" class="avatar-img" />
              </div>
              <span class="nav-label">我的</span>
            </div>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="profile">个人资料</el-dropdown-item>
                <el-dropdown-item command="settings">设置</el-dropdown-item>
                <el-dropdown-item command="system">系统设置</el-dropdown-item>
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
import { ref, computed, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useUserStore } from '@/store/user'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  DataBoard,
  Reading,
  School,
  User,
  Document,
  EditPen,
  Setting
} from '@element-plus/icons-vue'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

// 主要导航项配置
const navItems = ref([
  {
    path: '/admin/dashboard',
    label: '概览',
    icon: DataBoard,
    badge: null
  },
  {
    path: '/admin/courses',
    label: '课程',
    icon: Reading,
    badge: null
  },
  {
    path: '/admin/classes',
    label: '班级',
    icon: School,
    badge: null
  },
  {
    path: '/admin/users',
    label: '用户',
    icon: User,
    badge: null
  }
])

// 更多功能菜单项
const moreItems = ref([
  {
    path: '/admin/units',
    label: '单元管理',
    icon: Reading,
    badge: null
  },
  {
    path: '/admin/words',
    label: '单词管理',
    icon: EditPen,
    badge: null
  },
  {
    path: '/admin/texts',
    label: '课文管理',
    icon: Document,
    badge: null
  },
  {
    path: '/admin/assignments',
    label: '作业管理',
    icon: Document,
    badge: null
  }
])

// 页面标题
const pageTitle = computed(() => {
  const routeMap = {
    '/admin/dashboard': '管理概览',
    '/admin/courses': '课程管理',
    '/admin/classes': '班级管理',
    '/admin/users': '用户管理',
    '/admin/units': '单元管理',
    '/admin/words': '单词管理',
    '/admin/texts': '课文管理',
    '/admin/assignments': '作业管理'
  }
  return route.meta?.title || routeMap[route.path] || '管理员端'
})

// 用户头像
const userAvatar = computed(() => {
  return userStore.userInfo?.avatar || 'https://cube.elemecdn.com/0/88/03b0d39583f48206768a7534e55bcpng.png'
})

// 判断管理相关路由是否激活
const isManagementRouteActive = computed(() => {
  const managementPaths = ['/admin/units', '/admin/words', '/admin/texts', '/admin/assignments']
  return managementPaths.some(path => route.path === path || route.path.startsWith(path + '/'))
})

// 处理管理菜单命令（用于下拉菜单项点击后的处理）
const handleManagementCommand = (command) => {
  // 路由已经在router-link中处理，这里可以添加额外的逻辑
}

const handleCommand = async (command) => {
  switch (command) {
    case 'profile':
      ElMessage.info('个人资料功能开发中')
      break
    case 'settings':
      ElMessage.info('设置功能开发中')
      break
    case 'system':
      ElMessage.info('系统设置功能开发中')
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
        router.push('/login')
      } catch (error) {
        // 用户取消操作
      }
      break
  }
}

// 更新导航徽章（例如待审核用户数）
onMounted(() => {
  // 这里可以添加获取待审核用户数等逻辑
  // navItems.value[3].badge = '3' // 例如待审核用户数量
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

/* 顶部标题栏 - 管理员端特色 */
.mobile-header.admin-header {
  background: var(--surface-color);
  border-bottom: 1px solid var(--border-light);
  position: relative;
  z-index: 100;
  box-shadow: var(--shadow-light);
}

.mobile-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0 16px;
  height: 56px;
}

.header-left {
  flex: 1;
}

.page-title {
  font-size: 18px;
  font-weight: 600;
  color: var(--text-primary);
  margin: 0;
  background: var(--gradient-accent);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

/* 用户菜单项样式 - 在底部导航栏 */
.management-menu-item {
  cursor: pointer;
}

.nav-management-trigger {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  width: 100%;
  height: 100%;
}

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
  border: 2px solid var(--accent-color);
}

.user-menu-item.active .avatar-img,
.user-menu-item:hover .avatar-img {
  border-color: var(--accent-color);
  box-shadow: 0 0 0 2px rgba(69, 183, 209, 0.2);
  transform: scale(1.05);
}

.user-menu-item {
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}

.user-menu-item:hover {
  transform: translateY(-2px);
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
  overflow-y: auto;
  padding: 16px;
  background: var(--bg-secondary);
}

/* 底部导航栏 */
.mobile-nav {
  background: linear-gradient(to top, var(--surface-color) 0%, rgba(255, 255, 255, 0.98) 100%);
  border-top: 1px solid var(--border-light);
  box-shadow: 0 -4px 20px rgba(0, 0, 0, 0.06), 0 -1px 3px rgba(0, 0, 0, 0.04);
  position: relative;
  z-index: 100;
  padding-bottom: max(10px, env(safe-area-inset-bottom));
  backdrop-filter: blur(10px);
  -webkit-backdrop-filter: blur(10px);
}

.nav-container {
  display: flex;
  justify-content: space-around;
  align-items: center;
  padding: 10px 8px max(10px, env(safe-area-inset-bottom));
  gap: 4px;
}

.nav-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 6px 10px;
  text-decoration: none;
  color: var(--text-tertiary);
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  position: relative;
  min-width: 50px;
  border-radius: 10px;
  margin: 0 2px;
  flex: 1;
  max-width: 80px;
}

.nav-item:hover {
  color: var(--accent-color);
  background-color: rgba(69, 183, 209, 0.08);
  transform: translateY(-2px);
}

.nav-item.active {
  color: var(--accent-color);
  background: linear-gradient(135deg, rgba(69, 183, 209, 0.15) 0%, rgba(69, 183, 209, 0.08) 100%);
  box-shadow: 0 2px 8px rgba(69, 183, 209, 0.2);
}

.nav-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 26px;
  height: 26px;
  margin-bottom: 3px;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}

.nav-item.active .nav-icon {
  transform: scale(1.15);
  filter: drop-shadow(0 2px 4px rgba(69, 183, 209, 0.3));
}

.nav-label {
  font-size: 11px;
  font-weight: 500;
  text-align: center;
  line-height: 1.3;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  white-space: nowrap;
}

.nav-item.active .nav-label {
  font-size: 11.5px;
  font-weight: 600;
  letter-spacing: 0.3px;
}

.nav-badge {
  position: absolute;
  top: 6px;
  right: 8px;
  background: var(--accent-color);
  color: white;
  font-size: 10px;
  font-weight: 600;
  padding: 2px 6px;
  border-radius: 10px;
  min-width: 16px;
  text-align: center;
  line-height: 1;
  box-shadow: 0 2px 4px rgba(69, 183, 209, 0.3);
  animation: pulse 2s infinite;
}

@keyframes pulse {
  0% {
    box-shadow: 0 2px 4px rgba(69, 183, 209, 0.3);
  }
  50% {
    box-shadow: 0 2px 8px rgba(69, 183, 209, 0.6);
  }
  100% {
    box-shadow: 0 2px 4px rgba(69, 183, 209, 0.3);
  }
}

/* 管理菜单项样式 */
.management-menu-item .nav-management-trigger {
  width: 100%;
  height: 100%;
}

/* 下拉菜单链接样式 */
.dropdown-link {
  text-decoration: none;
  color: inherit;
  display: block;
}

.dropdown-link:hover {
  color: inherit;
}

.dropdown-link .el-dropdown-item {
  display: flex;
  align-items: center;
  padding: 12px 16px;
}

/* 响应式设计 */
@media (max-width: 480px) {
  .mobile-header {
    padding: 0 12px;
    height: 52px;
  }

  .page-title {
    font-size: 16px;
  }

  .content-container {
    padding: 12px;
  }

  .nav-item {
    padding: 6px 8px;
    min-width: 40px;
    margin: 0 2px;
  }

  .nav-icon {
    width: 22px;
    height: 22px;
    margin-bottom: 3px;
  }

  .nav-label {
    font-size: 10px;
  }

  .nav-item.active .nav-label {
    font-size: 11px;
  }



}

@media (max-width: 360px) {
  .mobile-header {
    padding: 0 8px;
    height: 48px;
  }

  .page-title {
    font-size: 15px;
  }

  .content-container {
    padding: 8px;
  }

  .nav-item {
    padding: 4px 6px;
    margin: 0 1px;
  }

  .nav-icon {
    width: 20px;
    height: 20px;
    margin-bottom: 2px;
  }

  .nav-label {
    font-size: 9px;
  }

  .nav-item.active .nav-label {
    font-size: 10px;
  }

}

/* 横屏适配 */
@media (max-height: 600px) and (orientation: landscape) {
  .mobile-header {
    height: 44px;
  }

  .page-title {
    font-size: 16px;
  }

  .nav-container {
    padding: 4px 0;
  }

  .nav-item {
    padding: 4px 8px;
  }

  .nav-icon {
    width: 20px;
    height: 20px;
    margin-bottom: 2px;
  }

  .nav-label {
    font-size: 9px;
  }

}

/* 过渡动画 */
.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.3s ease, transform 0.3s ease;
}

.fade-enter-from {
  opacity: 0;
  transform: translateY(10px);
}

.fade-leave-to {
  opacity: 0;
  transform: translateY(-10px);
}

/* 安全区域适配 */
@supports (padding: max(0px)) {
  .mobile-header {
    padding-left: max(16px, env(safe-area-inset-left));
    padding-right: max(16px, env(safe-area-inset-right));
    padding-top: max(0px, env(safe-area-inset-top));
  }

  .content-container {
    padding-left: max(16px, env(safe-area-inset-left));
    padding-right: max(16px, env(safe-area-inset-right));
  }

}

/* 提升触摸体验 */
.nav-item,
.user-menu-item,
.management-menu-item {
  -webkit-tap-highlight-color: transparent;
  touch-action: manipulation;
}

/* 自定义滚动条 */
.content-container::-webkit-scrollbar {
  width: 4px;
}

.content-container::-webkit-scrollbar-track {
  background: transparent;
}

.content-container::-webkit-scrollbar-thumb {
  background: var(--border-medium);
  border-radius: 2px;
}

.content-container::-webkit-scrollbar-thumb:hover {
  background: var(--text-tertiary);
}
</style>