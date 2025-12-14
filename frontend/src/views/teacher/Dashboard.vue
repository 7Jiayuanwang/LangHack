<template>
  <div class="dashboard">
    <el-row :gutter="20">
      <el-col :span="6">
        <el-card class="stat-card">
          <div class="stat-content">
            <div class="stat-icon classes">
              <el-icon><School /></el-icon>
            </div>
            <div class="stat-info">
              <h3>{{ stats.totalClasses }}</h3>
              <p>管理班级</p>
            </div>
          </div>
        </el-card>
      </el-col>

      <el-col :span="6">
        <el-card class="stat-card">
          <div class="stat-content">
            <div class="stat-icon students">
              <el-icon><User /></el-icon>
            </div>
            <div class="stat-info">
              <h3>{{ stats.totalStudents }}</h3>
              <p>学生总数</p>
            </div>
          </div>
        </el-card>
      </el-col>

      <el-col :span="6">
        <el-card class="stat-card">
          <div class="stat-content">
            <div class="stat-icon assignments">
              <el-icon><Edit /></el-icon>
            </div>
            <div class="stat-info">
              <h3>{{ stats.totalAssignments }}</h3>
              <p>布置任务</p>
            </div>
          </div>
        </el-card>
      </el-col>

      <el-col :span="6">
        <el-card class="stat-card">
          <div class="stat-content">
            <div class="stat-icon completions">
              <el-icon><Check /></el-icon>
            </div>
            <div class="stat-info">
              <h3>{{ stats.completedTasks }}</h3>
              <p>已完成任务</p>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="20" class="mt-20">
      <el-col :span="12">
        <el-card>
          <template #header>
            <div class="card-header">
              <span>我的班级</span>
              <el-button type="primary" size="small" @click="$router.push('/teacher/classes')">
                管理班级
              </el-button>
            </div>
          </template>
          <div class="class-list">
            <div v-for="classItem in classes" :key="classItem.id" class="class-item">
              <div class="class-info">
                <h4>{{ classItem.name }}</h4>
                <p>课程：{{ classItem.courseName }}</p>
                <p>学生数：{{ classItem.studentCount }}人</p>
              </div>
              <div class="class-actions">
                <el-button size="small" @click="viewClassDetail(classItem)">查看详情</el-button>
              </div>
            </div>
          </div>
        </el-card>
      </el-col>

      <el-col :span="12">
        <el-card>
          <template #header>
            <div class="card-header">
              <span>最近任务</span>
              <el-button type="primary" size="small" @click="$router.push('/teacher/assignments')">
                管理任务
              </el-button>
            </div>
          </template>
          <div class="assignment-list">
            <div v-for="assignment in recentAssignments" :key="assignment.id" class="assignment-item">
              <div class="assignment-info">
                <h4>{{ assignment.title }}</h4>
                <p>班级：{{ assignment.className }}</p>
                <p>截止时间：{{ formatTime(assignment.deadline) }}</p>
                <div class="progress-info">
                  <span>完成进度：{{ assignment.completedCount }}/{{ assignment.totalCount }}</span>
                  <el-progress
                    :percentage="Math.round((assignment.completedCount / assignment.totalCount) * 100)"
                    :color="getProgressColor(assignment.completedCount / assignment.totalCount)"
                  />
                </div>
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
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'

const router = useRouter()

const stats = reactive({
  totalClasses: 0,
  totalStudents: 0,
  totalAssignments: 0,
  completedTasks: 0
})

const classes = ref([])
const recentAssignments = ref([])

const loadDashboardData = async () => {
  try {
    // 加载统计数据
    const statsResponse = await fetch('/api/assignments/teacher/stats', {
      headers: {
        'Authorization': `Bearer ${localStorage.getItem('token')}`
      }
    })

    if (statsResponse.ok) {
      const statsData = await statsResponse.json()
      if (statsData.code === 200) {
        const result = statsData.data
        stats.totalClasses = result.total_classes || 0
        stats.totalStudents = result.total_students || 0
        stats.totalAssignments = result.total_assignments || 0
        stats.completedTasks = result.completed_tasks || 0
      }
    }

    // 加载班级列表
    const classesResponse = await fetch('/api/assignments/teacher/classes', {
      headers: {
        'Authorization': `Bearer ${localStorage.getItem('token')}`
      }
    })

    if (classesResponse.ok) {
      const classesData = await classesResponse.json()
      if (classesData.code === 200) {
        classes.value = classesData.data || []
      }
    }

    // 加载最近任务
    const assignmentsResponse = await fetch('/api/assignments/teacher/recent?limit=5', {
      headers: {
        'Authorization': `Bearer ${localStorage.getItem('token')}`
      }
    })

    if (assignmentsResponse.ok) {
      const assignmentsData = await assignmentsResponse.json()
      if (assignmentsData.code === 200) {
        recentAssignments.value = assignmentsData.data.map(assignment => ({
          id: assignment.id,
          title: assignment.title,
          className: assignment.className,
          deadline: assignment.deadline,
          completedCount: assignment.completedCount || 0,
          totalCount: assignment.studentCount || 0
        })) || []
      }
    }
  } catch (error) {
    console.error('加载仪表板数据失败:', error)
    ElMessage.error('加载数据失败')
  }
}

const viewClassDetail = (classItem) => {
  router.push(`/teacher/classes?id=${classItem.id}`)
}

const formatTime = (time) => {
  if (!time) return ''
  return new Date(time).toLocaleString()
}

const getProgressColor = (percentage) => {
  if (percentage >= 0.8) return '#67c23a'
  if (percentage >= 0.5) return '#e6a23c'
  return '#f56c6c'
}

onMounted(() => {
  loadDashboardData()
})
</script>

<style scoped>
.dashboard {
  height: 100%;
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

.stat-icon.classes {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
}

.stat-icon.students {
  background: linear-gradient(135deg, #f093fb 0%, #f5576c 100%);
}

.stat-icon.assignments {
  background: linear-gradient(135deg, #4facfe 0%, #00f2fe 100%);
}

.stat-icon.completions {
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

.class-list,
.assignment-list {
  max-height: 300px;
  overflow-y: auto;
}

.class-item,
.assignment-item {
  padding: 15px;
  border-bottom: 1px solid #f0f0f0;
}

.class-item:last-child,
.assignment-item:last-child {
  border-bottom: none;
}

.class-info h4,
.assignment-info h4 {
  margin: 0 0 8px 0;
  color: #333;
  font-size: 16px;
}

.class-info p,
.assignment-info p {
  margin: 4px 0;
  color: #666;
  font-size: 14px;
}

.class-actions {
  margin-top: 10px;
}

.progress-info {
  margin-top: 8px;
}

.progress-info span {
  font-size: 12px;
  color: #666;
  display: block;
  margin-bottom: 4px;
}
</style>