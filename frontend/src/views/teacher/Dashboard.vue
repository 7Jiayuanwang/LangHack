<template>
  <div class="dashboard">
    <XyPageHeader class="teacher-intro" title="教学概览" eyebrow="TEACHING OVERVIEW" description="班级、任务与学习进度，一眼看清需要关注的地方。" variant="compact" />
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

    <el-card class="exemption-entry" shadow="never" @click="goExemption">
      <div class="exemption-inner">
        <div class="exemption-icon">
          <el-icon><Stamp /></el-icon>
        </div>
        <div class="exemption-text">
          <div class="exemption-title">
            大学英语免修审批
            <span v-if="exemptionStats.pending > 0" class="exemption-badge">
              {{ exemptionStats.pending }} 条待审批
            </span>
            <span v-else class="exemption-badge badge-clear">暂无待审批</span>
          </div>
          <div class="exemption-desc">
            大学英语 2 / 3 免修免考、免修不免考申请审批 · 内农大教字〔2025〕18 号
          </div>
        </div>
        <el-button class="exemption-btn" size="large" round>
          进入审批
          <el-icon class="btn-arrow"><ArrowRight /></el-icon>
        </el-button>
      </div>
    </el-card>

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
import XyPageHeader from '@/components/ui/XyPageHeader.vue'

const router = useRouter()

const exemptionStats = reactive({ pending: 0, approved: 0, rejected: 0, total: 0 })

const goExemption = () => {
  router.push('/teacher/exemption-approvals')
}

const loadExemptionStats = async () => {
  try {
    const headers = {}
    const authHeader = getAuthHeader()
    if (authHeader) headers['Authorization'] = authHeader
    const res = await fetch('/api/teacher/exemption/stats', { headers })
    if (res.ok) {
      const data = await res.json()
      if (data.code === 200 && data.data) {
        exemptionStats.pending = Number(data.data.pending || 0)
        exemptionStats.approved = Number(data.data.approved || 0)
        exemptionStats.rejected = Number(data.data.rejected || 0)
        exemptionStats.total = Number(data.data.total || 0)
      }
    }
  } catch (e) {
    console.error('加载免修审批统计失败:', e)
  }
}

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
  router.push(`/teacher/classes/${classItem.id}`)
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
  loadExemptionStats()
})
</script>

<style scoped>
.dashboard {
  max-width: 1400px;
  margin: 0 auto;
  padding: 24px;
  background: var(--xy-gradient-page);
  min-height: calc(100vh - 56px);
}
.teacher-intro { margin-bottom: 20px; }

/* 大学英语免修审批入口 */
.exemption-entry {
  margin-top: 0;
  border-radius: 16px;
  border: 1px solid rgba(23, 110, 81, 0.25);
  background: linear-gradient(120deg, #0f5f49 0%, #17836a 55%, #1f9d7d 100%);
  cursor: pointer;
  overflow: hidden;
  transition: transform 0.28s cubic-bezier(0.4, 0, 0.2, 1), box-shadow 0.28s ease;
}

.exemption-entry:hover {
  transform: translateY(-3px);
  box-shadow: 0 14px 32px rgba(23, 110, 81, 0.32);
}

.exemption-entry :deep(.el-card__body) {
  padding: 20px 24px;
}

.exemption-inner {
  display: flex;
  align-items: center;
  gap: 18px;
}

.exemption-icon {
  width: 58px;
  height: 58px;
  border-radius: 16px;
  flex-shrink: 0;
  background: rgba(255, 255, 255, 0.16);
  border: 1px solid rgba(255, 255, 255, 0.28);
  color: #fff;
  font-size: 28px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.exemption-entry:hover .exemption-icon {
  transform: scale(1.06) rotate(-4deg);
}

.exemption-text {
  flex: 1;
  min-width: 0;
}

.exemption-title {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
  color: #fff;
  font-size: 20px;
  font-weight: 700;
  letter-spacing: 0.5px;
}

.exemption-badge {
  background: #ffd54a;
  color: #5c4300;
  font-size: 12px;
  font-weight: 600;
  padding: 3px 10px;
  border-radius: 999px;
  letter-spacing: 0.3px;
}

.exemption-badge.badge-clear {
  background: rgba(255, 255, 255, 0.2);
  color: #eafaf4;
}

.exemption-desc {
  margin-top: 6px;
  color: rgba(255, 255, 255, 0.82);
  font-size: 13.5px;
  letter-spacing: 0.2px;
}

.exemption-btn {
  flex-shrink: 0;
  background: #fff;
  border: none;
  color: #0f5f49;
  font-weight: 700;
  padding: 12px 22px;
}

.exemption-btn:hover {
  background: #f2fbf7;
  color: #0b4a39;
}

.btn-arrow { margin-left: 6px; }

@media (max-width: 768px) {
  .exemption-inner { flex-direction: column; align-items: flex-start; gap: 12px; }
  .exemption-btn { width: 100%; }
  .exemption-title { font-size: 18px; }
}

/* PC端大字号适配 */
@media (min-width: 1200px) {
  .stat-info h3 {
    font-size: 42px !important;
  }

  .stat-info p {
    font-size: 16px !important;
  }

  .card-header span {
    font-size: 18px;
  }

  .class-info h4, .assignment-info h4 {
    font-size: 20px !important;
  }

  .class-info p, .assignment-info p {
    font-size: 15px !important;
  }
}

.stat-card {
  margin-bottom: 20px;
  border: none;
  box-shadow: var(--xy-shadow-card);
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  border-radius: var(--xy-radius-md);
  overflow: hidden;
  border: 1px solid var(--xy-border);
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
  margin-top: 20px;
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
  border-color: var(--xy-brand-600);
  transform: translateX(4px);
  box-shadow: 0 2px 8px rgba(23, 110, 81, 0.15);
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
  color: var(--xy-brand-600);
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
  box-shadow: 0 4px 12px rgba(23, 110, 81, 0.3);
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
    margin-top: 16px;
  }

  .stat-card {
    margin-bottom: 16px;
  }
}
</style>
