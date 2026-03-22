<template>
  <div class="dashboard">
    <el-row :gutter="20">
      <el-col :xs="12" :sm="12" :md="6">
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

      <el-col :xs="12" :sm="12" :md="6">
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

      <el-col :xs="12" :sm="12" :md="6">
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

      <el-col :xs="12" :sm="12" :md="6">
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
      <el-col :xs="24" :sm="24" :md="12">
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

      <el-col :xs="24" :sm="24" :md="12">
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
                    :percentage="assignment.totalCount > 0 ? Math.round((assignment.completedCount / assignment.totalCount) * 100) : 0"
                    :color="assignment.totalCount > 0 ? getProgressColor(assignment.completedCount / assignment.totalCount) : '#909399'"
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
import { getAuthHeader } from '@/utils/auth'

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
    // 构建认证头
    const headers = {}
    const authHeader = getAuthHeader()
    if (authHeader) headers['Authorization'] = authHeader

    // 加载统计数据
    const statsResponse = await fetch('/api/assignments/teacher/stats', { headers })

    if (statsResponse.ok) {
      try {
        const statsData = await statsResponse.json()
        if (statsData.code === 200) {
          const result = statsData.data || {}
          stats.totalClasses = result.total_classes || 0
          stats.totalStudents = result.total_students || 0
          stats.totalAssignments = result.total_assignments || 0
          stats.completedTasks = result.completed_tasks || 0
        }
      } catch (parseError) {
        console.error('统计数据解析失败:', parseError)
      }
    }

    // 加载班级列表
    const classesResponse = await fetch('/api/assignments/teacher/classes', { headers })

    if (classesResponse.ok) {
      try {
        const classesData = await classesResponse.json()
        if (classesData.code === 200) {
          // 兼容分页响应 { records: [...] } 和直接数组两种格式
          const raw = classesData.data
          classes.value = Array.isArray(raw) ? raw : (Array.isArray(raw?.records) ? raw.records : [])
        }
      } catch (parseError) {
        console.error('班级列表解析失败:', parseError)
      }
    }

    // 加载最近任务
    const assignmentsResponse = await fetch('/api/assignments/teacher/recent?limit=5', { headers })

    if (assignmentsResponse.ok) {
      try {
        const assignmentsData = await assignmentsResponse.json()
        if (assignmentsData.code === 200) {
          recentAssignments.value = Array.isArray(assignmentsData.data) ? assignmentsData.data.map(assignment => ({
            id: assignment.id,
            title: assignment.title,
            className: assignment.className || '未知班级',
            deadline: assignment.deadline,
            completedCount: assignment.completedCount || 0,
            totalCount: assignment.studentCount || 0
          })) : []
        }
      } catch (parseError) {
        console.error('任务列表解析失败:', parseError)
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
  padding: 20px;
  background: linear-gradient(to bottom, #f5f7fa 0%, #ffffff 100%);
  min-height: calc(100vh - 56px);
}

.stat-card {
  margin-bottom: 20px;
  border: none;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.08);
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  border-radius: 12px;
  overflow: hidden;
}

.stat-card:hover {
  transform: translateY(-4px);
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.12);
}

.stat-content {
  display: flex;
  align-items: center;
  padding: 8px;
}

.stat-icon {
  width: 64px;
  height: 64px;
  border-radius: 16px;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-right: 20px;
  font-size: 28px;
  color: white;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  flex-shrink: 0;
}

.stat-card:hover .stat-icon {
  transform: scale(1.1) rotate(5deg);
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

.stat-info {
  flex: 1;
  min-width: 0;
}

.stat-info h3 {
  margin: 0;
  font-size: 32px;
  font-weight: 700;
  background: linear-gradient(135deg, #333 0%, #666 100%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
  line-height: 1.2;
  transition: all 0.3s ease;
}

.stat-card:hover .stat-info h3 {
  transform: scale(1.05);
}

.stat-info p {
  margin: 8px 0 0;
  color: #888;
  font-size: 14px;
  font-weight: 500;
  letter-spacing: 0.5px;
}

.mt-20 {
  margin-top: 24px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-weight: 600;
  font-size: 16px;
}

.class-list,
.assignment-list {
  max-height: 500px;
  overflow-y: auto;
  padding: 4px;
}

.class-list::-webkit-scrollbar,
.assignment-list::-webkit-scrollbar {
  width: 6px;
}

.class-list::-webkit-scrollbar-track,
.assignment-list::-webkit-scrollbar-track {
  background: #f1f1f1;
  border-radius: 3px;
}

.class-list::-webkit-scrollbar-thumb,
.assignment-list::-webkit-scrollbar-thumb {
  background: #c1c1c1;
  border-radius: 3px;
}

.class-list::-webkit-scrollbar-thumb:hover,
.assignment-list::-webkit-scrollbar-thumb:hover {
  background: #a8a8a8;
}

.class-item,
.assignment-item {
  padding: 18px 16px;
  border-bottom: 1px solid #f0f0f0;
  min-height: 120px;
  border-radius: 8px;
  margin-bottom: 8px;
  background: #fafafa;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  cursor: pointer;
}

.class-item:hover,
.assignment-item:hover {
  background: #f0f7ff;
  border-color: #409eff;
  transform: translateX(4px);
  box-shadow: 0 2px 8px rgba(64, 158, 255, 0.15);
}

.class-item:last-child,
.assignment-item:last-child {
  border-bottom: 1px solid #f0f0f0;
  margin-bottom: 0;
}

.class-info h4,
.assignment-info h4 {
  margin: 0 0 12px 0;
  color: #303133;
  font-size: 18px;
  font-weight: 600;
  line-height: 1.4;
  transition: color 0.3s ease;
}

.class-item:hover .class-info h4,
.assignment-item:hover .assignment-info h4 {
  color: #409eff;
}

.class-info p,
.assignment-info p {
  margin: 8px 0;
  color: #606266;
  font-size: 14px;
  line-height: 1.8;
  display: flex;
  align-items: center;
}

.class-info p:before,
.assignment-info p:before {
  content: '•';
  color: #909399;
  margin-right: 8px;
  font-weight: bold;
}

.class-actions {
  margin-top: 12px;
  display: flex;
  justify-content: flex-end;
}

.class-actions .el-button {
  border-radius: 6px;
  transition: all 0.3s ease;
}

.class-actions .el-button:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(64, 158, 255, 0.3);
}

.progress-info {
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px solid #ebeef5;
}

.progress-info span {
  font-size: 13px;
  color: #909399;
  display: block;
  margin-bottom: 8px;
  font-weight: 500;
}

.progress-info .el-progress {
  margin-top: 4px;
}

.el-card {
  border: none;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.08);
  border-radius: 12px;
  transition: all 0.3s ease;
}

.el-card:hover {
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.12);
}

/* 响应式优化 */
@media (max-width: 768px) {
  .dashboard {
    padding: 16px;
  }

  .stat-content {
    flex-direction: column;
    text-align: center;
  }

  .stat-icon {
    margin-right: 0;
    margin-bottom: 12px;
  }

  .stat-info h3 {
    font-size: 28px;
  }

  .mt-20 {
    margin-top: 12px;
  }

  .stat-card {
    margin-bottom: 12px;
  }
}
</style>