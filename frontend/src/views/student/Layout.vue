<template>
  <div class="student-layout">
    <el-container>
      <el-aside width="200px">
        <div class="logo">
          <h3>学生端</h3>
        </div>
        <el-menu
          :default-active="$route.path"
          router
          background-color="#304156"
          text-color="#bfcbd9"
          active-text-color="#409EFF"
        >
          <el-menu-item index="/student/dashboard">
            <el-icon><DataBoard /></el-icon>
            <span>仪表盘</span>
          </el-menu-item>
          <el-menu-item index="/student/courses">
            <el-icon><Reading /></el-icon>
            <span>课程学习</span>
          </el-menu-item>
          <el-menu-item index="/student/vocabulary">
            <el-icon><Document /></el-icon>
            <span>生词本</span>
          </el-menu-item>
          <el-menu-item index="/student/assignments">
            <el-icon><Edit /></el-icon>
            <span>我的任务</span>
          </el-menu-item>
          <el-menu-item index="/student/practice">
            <el-icon><EditPen /></el-icon>
            <span>在线练习</span>
          </el-menu-item>
          <el-menu-item index="/student/weakness">
            <el-icon><TrendCharts /></el-icon>
            <span>薄弱点分析</span>
          </el-menu-item>
          <el-menu-item index="/student/suggestions">
            <el-icon><Promotion /></el-icon>
            <span>学习建议</span>
          </el-menu-item>
        </el-menu>
      </el-aside>

      <el-container>
        <el-header>
          <div class="header-content">
            <div class="breadcrumb">
              <el-breadcrumb separator="/">
                <el-breadcrumb-item :to="{ path: '/student' }">首页</el-breadcrumb-item>
                <el-breadcrumb-item>{{ $route.meta.title }}</el-breadcrumb-item>
              </el-breadcrumb>
            </div>
            <div class="user-info">
              <el-dropdown @command="handleCommand">
                <span class="el-dropdown-link">
                  {{ userStore.userInfo.realName || userStore.userInfo.username }}
                  <el-icon class="el-icon--right"><arrow-down /></el-icon>
                </span>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item command="profile">个人资料</el-dropdown-item>
                    <el-dropdown-item command="logout" divided>退出登录</el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </div>
          </div>
        </el-header>

        <el-main>
          <router-view />
        </el-main>
      </el-container>
    </el-container>
  </div>
</template>

<script setup>
import { useRouter } from 'vue-router'
import { useUserStore } from '@/store/user'
import { ElMessage, ElMessageBox } from 'element-plus'
import { DataBoard, Reading, Document, Edit, EditPen, TrendCharts, Promotion, ArrowDown } from '@element-plus/icons-vue'

const router = useRouter()
const userStore = useUserStore()

const handleCommand = async (command) => {
  switch (command) {
    case 'profile':
      ElMessage.info('个人资料功能开发中')
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
</script>

<style scoped>
.student-layout {
  height: 100vh;
}

.logo {
  height: 60px;
  display: flex;
  align-items: center;
  justify-content: center;
  background-color: #2b2f3a;
  color: #fff;
  border-bottom: 1px solid #3a3f51;
}

.logo h3 {
  margin: 0;
  font-size: 16px;
}

.el-aside {
  background-color: #304156;
}

.el-header {
  background-color: #fff;
  border-bottom: 1px solid #e6e6e6;
  padding: 0 20px;
}

.header-content {
  display: flex;
  justify-content: space-between;
  align-items: center;
  height: 100%;
}

.breadcrumb {
  flex: 1;
}

.user-info {
  display: flex;
  align-items: center;
}

.el-dropdown-link {
  display: flex;
  align-items: center;
  cursor: pointer;
  color: #606266;
}

.el-main {
  background-color: #f0f2f5;
  padding: 20px;
  height: calc(100vh - 60px);
  overflow-y: auto;
}
</style>