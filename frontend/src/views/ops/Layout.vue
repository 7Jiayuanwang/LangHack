<template>
  <div class="mobile-layout">
    <!-- 顶部标题栏 -->
    <header class="mobile-header ops-header">
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
        </router-link>

        <!-- 用户菜单 -->
        <div class="nav-item user-menu-item">
          <el-dropdown @command="handleCommand" placement="top-end">
            <div class="nav-user-avatar">
              <div class="nav-icon">
                <img :src="userAvatar" :alt="userStore.userInfo?.realName || '运维'" class="avatar-img" />
              </div>
              <span class="nav-label">我的</span>
            </div>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="elevate" v-if="userStore.userRole === 'OPS'">切换到管理端</el-dropdown-item>
                <el-dropdown-item command="elevate" v-if="userStore.userRole === 'ADMIN'">返回管理端</el-dropdown-item>
                <el-dropdown-item command="logout" divided>退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </div>
    </nav>

    <!-- 提权对话框 -->
    <ElevationDialog
      v-model="showElevationDialog"
      :target-role="elevationTarget"
      @success="onElevationSuccess"
    />
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useUserStore } from '@/store/user'
import { ElMessage, ElMessageBox } from 'element-plus'
import ElevationDialog from '@/components/ElevationDialog.vue'
import {
  DataLine,
  FolderOpened,
  User,
  Connection
} from '@element-plus/icons-vue'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()
const showElevationDialog = ref(false)
const elevationTarget = ref('ADMIN')

const navItems = ref([
  {
    path: '/ops/monitoring',
    label: '监控',
    icon: DataLine
  },
  {
    path: '/ops/backups',
    label: '备份',
    icon: FolderOpened
  },
  {
    path: '/ops/users',
    label: '用户',
    icon: User
  },
  {
    path: '/ops/ai-providers',
    label: 'AI',
    icon: Connection
  }
])

const pageTitle = computed(() => {
  const routeMap = {
    '/ops/monitoring': '系统监控',
    '/ops/backups': '数据备份',
    '/ops/users': '用户管理',
    '/ops/ai-providers': 'AI 服务管理'
  }
  return route.meta?.title || routeMap[route.path] || '运维面板'
})

const userAvatar = computed(() => {
  return userStore.userInfo?.avatar || 'https://cube.elemecdn.com/0/88/03b0d39583f48206768a7534e55bcpng.png'
})

const handleCommand = async (command) => {
  switch (command) {
    case 'elevate':
      if (userStore.userRole === 'ADMIN') {
        router.push('/admin')
      } else {
        // OPS 用户需要提权才能进入管理端
        elevationTarget.value = 'ADMIN'
        showElevationDialog.value = true
      }
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
        router.replace('/login')
      } catch (error) {
        // 用户取消操作
      }
      break
  }
}

const onElevationSuccess = () => {
  router.push('/admin')
}

// 权限守卫 - 确保只有 OPS 角色（或提权到 OPS）可以访问运维面板
onMounted(() => {
  if (!userStore.isLoggedIn || !userStore.hasOpsAccess) {
    ElMessage.error('权限不足，请以运维人员身份登录')
    router.replace('/login')
  }
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

.mobile-header.ops-header {
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
  background: linear-gradient(135deg, #f97316 0%, #ea580c 100%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

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

.mobile-nav {
  background: linear-gradient(to top, var(--surface-color) 0%, rgba(255, 255, 255, 0.98) 100%);
  border-top: 1px solid var(--border-light);
  box-shadow: 0 -4px 20px rgba(0, 0, 0, 0.06);
  position: relative;
  z-index: 100;
  padding-bottom: max(10px, env(safe-area-inset-bottom));
  backdrop-filter: blur(10px);
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
  color: #f97316;
  background-color: rgba(249, 115, 22, 0.08);
  transform: translateY(-2px);
}

.nav-item.active {
  color: #f97316;
  background: linear-gradient(135deg, rgba(249, 115, 22, 0.15) 0%, rgba(249, 115, 22, 0.08) 100%);
  box-shadow: 0 2px 8px rgba(249, 115, 22, 0.2);
}

.nav-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 26px;
  height: 26px;
  margin-bottom: 3px;
}

.nav-label {
  font-size: 11px;
  font-weight: 500;
  text-align: center;
  line-height: 1.3;
  white-space: nowrap;
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
  border: 2px solid #f97316;
}

.nav-item, .user-menu-item {
  -webkit-tap-highlight-color: transparent;
  touch-action: manipulation;
}

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
</style>
