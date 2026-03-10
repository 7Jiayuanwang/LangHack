<template>
  <div class="dashboard">
    <el-row :gutter="20">
      <el-col :xs="12" :sm="12" :md="6">
        <el-card class="stat-card">
          <div class="stat-content">
            <div class="stat-icon users">
              <el-icon><User /></el-icon>
            </div>
            <div class="stat-info">
              <h3>{{ stats.totalUsers }}</h3>
              <p>总用户数</p>
            </div>
          </div>
        </el-card>
      </el-col>

      <el-col :xs="12" :sm="12" :md="6">
        <el-card class="stat-card">
          <div class="stat-content">
            <div class="stat-icon courses">
              <el-icon><Reading /></el-icon>
            </div>
            <div class="stat-info">
              <h3>{{ stats.totalCourses }}</h3>
              <p>课程数量</p>
            </div>
          </div>
        </el-card>
      </el-col>

      <el-col :xs="12" :sm="12" :md="6">
        <el-card class="stat-card">
          <div class="stat-content">
            <div class="stat-icon classes">
              <el-icon><School /></el-icon>
            </div>
            <div class="stat-info">
              <h3>{{ stats.totalClasses }}</h3>
              <p>班级数量</p>
            </div>
          </div>
        </el-card>
      </el-col>

      <el-col :xs="12" :sm="12" :md="6">
        <el-card class="stat-card">
          <div class="stat-content">
            <div class="stat-icon words">
              <el-icon><Document /></el-icon>
            </div>
            <div class="stat-info">
              <h3>{{ stats.totalWords }}</h3>
              <p>单词数量</p>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="20" class="mt-20">
      <el-col :xs="24" :sm="24" :md="12">
        <el-card>
          <template #header>
            <div class="card-header">
              <span>用户分布</span>
            </div>
          </template>
          <div class="chart-container">
            <div class="user-stats">
              <div class="user-stat-item">
                <span class="label">管理员：</span>
                <span class="value">{{ userStats.admin }}人</span>
              </div>
              <div class="user-stat-item">
                <span class="label">教师：</span>
                <span class="value">{{ userStats.teacher }}人</span>
              </div>
              <div class="user-stat-item">
                <span class="label">学生：</span>
                <span class="value">{{ userStats.student }}人</span>
              </div>
            </div>
          </div>
        </el-card>
      </el-col>

      <el-col :xs="24" :sm="24" :md="12">
        <el-card>
          <template #header>
            <div class="card-header">
              <span>最新活动</span>
            </div>
          </template>
          <div class="activity-list">
            <div v-for="activity in recentActivities" :key="activity.id" class="activity-item">
              <div class="activity-content">
                <p>{{ activity.description }}</p>
                <span class="activity-time">{{ activity.time }}</span>
              </div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { User, Reading, School, Document } from '@element-plus/icons-vue'

const stats = reactive({
  totalUsers: 0,
  totalCourses: 0,
  totalClasses: 0,
  totalWords: 0
})

const userStats = reactive({
  admin: 1,
  teacher: 0,
  student: 0
})

const recentActivities = ref([
  {
    id: 1,
    description: '新用户 "张三" 注册成功',
    time: '2024-01-15 10:30:00'
  },
  {
    id: 2,
    description: '创建新课程 "商务英语"',
    time: '2024-01-15 09:15:00'
  },
  {
    id: 3,
    description: '用户 "李四" 完成单元学习',
    time: '2024-01-14 16:45:00'
  }
])

const loadDashboardData = async () => {
  try {
    // 调用管理员统计API
    const response = await fetch('/api/admin/stats', {
      headers: {
        'Authorization': `Bearer ${localStorage.getItem('token') || ''}`
      }
    })

    if (response.ok) {
      const data = await response.json()
      if (data.code === 200 && data.data) {
        const result = data.data

        // 更新统计数据
        stats.totalUsers = result.totalUsers || 0
        stats.totalCourses = result.totalCourses || 0
        stats.totalClasses = result.totalClasses || 0
        stats.totalWords = result.totalWords || 0

        // 更新用户统计
        userStats.admin = result.totalAdmins || 0
        userStats.teacher = result.totalTeachers || 0
        userStats.student = result.totalStudents || 0
      }
    }
  } catch (error) {
    console.error('加载仪表板数据失败:', error)
    // 如果API调用失败，显示默认值
    stats.totalUsers = 0
    stats.totalCourses = 0
    stats.totalClasses = 0
    stats.totalWords = 0

    userStats.admin = 0
    userStats.teacher = 0
    userStats.student = 0
  }
}

onMounted(() => {
  loadDashboardData()
})
</script>

<style scoped>
.dashboard {
  padding: 20px;
}

.stat-card {
  margin-bottom: 20px;
}

.stat-content {
  display: flex;
  align-items: center;
}

.stat-icon {
  width: 60px;
  height: 60px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-right: 20px;
  font-size: 24px;
  color: white;
}

.stat-icon.users {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
}

.stat-icon.courses {
  background: linear-gradient(135deg, #f093fb 0%, #f5576c 100%);
}

.stat-icon.classes {
  background: linear-gradient(135deg, #4facfe 0%, #00f2fe 100%);
}

.stat-icon.words {
  background: linear-gradient(135deg, #43e97b 0%, #38f9d7 100%);
}

.stat-info h3 {
  margin: 0;
  font-size: 28px;
  font-weight: bold;
  color: #333;
}

.stat-info p {
  margin: 5px 0 0;
  color: #666;
  font-size: 14px;
}

.mt-20 {
  margin-top: 20px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.chart-container {
  height: 200px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.user-stats {
  width: 100%;
}

.user-stat-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 0;
  border-bottom: 1px solid #f0f0f0;
}

.user-stat-item:last-child {
  border-bottom: none;
}

.label {
  color: #666;
}

.value {
  font-weight: bold;
  color: #333;
}

.activity-list {
  max-height: 200px;
  overflow-y: auto;
}

.activity-item {
  padding: 10px 0;
  border-bottom: 1px solid #f0f0f0;
}

.activity-item:last-child {
  border-bottom: none;
}

.activity-content p {
  margin: 0 0 5px;
  color: #333;
}

.activity-time {
  font-size: 12px;
  color: #999;
}

@media (max-width: 768px) {
  .dashboard {
    padding: 12px;
  }

  .stat-card {
    margin-bottom: 12px;
  }

  .mt-20 {
    margin-top: 12px;
  }
}
</style>