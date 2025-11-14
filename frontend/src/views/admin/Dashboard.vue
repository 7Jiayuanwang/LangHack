<template>
  <div class="dashboard">
    <!-- 统计卡片 -->
    <el-row :gutter="20">
      <el-col :span="6">
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

      <el-col :span="6">
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

      <el-col :span="6">
        <el-card class="stat-card">
          <div class="stat-content">
            <div class="stat-icon questions">
              <el-icon><EditPen /></el-icon>
            </div>
            <div class="stat-info">
              <h3>{{ stats.totalQuestions }}</h3>
              <p>题目数量</p>
            </div>
          </div>
        </el-card>
      </el-col>

      <el-col :span="6">
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

    <!-- 图表区域 -->
    <el-row :gutter="20" class="mt-20">
      <el-col :span="12">
        <el-card>
          <template #header>
            <div class="card-header">
              <span>用户角色分布</span>
            </div>
          </template>
          <div ref="userPieChart" class="chart-container"></div>
        </el-card>
      </el-col>

      <el-col :span="12">
        <el-card>
          <template #header>
            <div class="card-header">
              <span>题库分类统计</span>
            </div>
          </template>
          <div ref="questionBarChart" class="chart-container"></div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="20" class="mt-20">
      <el-col :span="12">
        <el-card>
          <template #header>
            <div class="card-header">
              <span>最近7天答题统计</span>
            </div>
          </template>
          <div ref="answerTrendChart" class="chart-container"></div>
        </el-card>
      </el-col>

      <el-col :span="12">
        <el-card>
          <template #header>
            <div class="card-header">
              <span>知识点分类统计</span>
            </div>
          </template>
          <div ref="knowledgePointChart" class="chart-container"></div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 数据表格 -->
    <el-row :gutter="20" class="mt-20">
      <el-col :span="12">
        <el-card>
          <template #header>
            <div class="card-header">
              <span>最近注册用户</span>
            </div>
          </template>
          <el-table :data="recentUsers" style="width: 100%" max-height="300">
            <el-table-column prop="username" label="用户名" width="120" />
            <el-table-column prop="realName" label="真实姓名" width="120" />
            <el-table-column prop="role" label="角色" width="100">
              <template #default="{ row }">
                <el-tag :type="getRoleTag(row.role)" size="small">
                  {{ getRoleLabel(row.role) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="createdTime" label="注册时间" width="180" />
          </el-table>
        </el-card>
      </el-col>

      <el-col :span="12">
        <el-card>
          <template #header>
            <div class="card-header">
              <span>学习活跃度排行</span>
            </div>
          </template>
          <el-table :data="activeStudents" style="width: 100%" max-height="300">
            <el-table-column type="index" label="排名" width="60" />
            <el-table-column prop="studentName" label="学生姓名" width="150" />
            <el-table-column prop="answerCount" label="答题数" width="100" />
            <el-table-column prop="accuracy" label="正确率" width="120">
              <template #default="{ row }">
                <el-progress :percentage="row.accuracy" :color="getAccuracyColor(row.accuracy)" />
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, nextTick } from 'vue'
import { User, Reading, EditPen, Document } from '@element-plus/icons-vue'
import * as echarts from 'echarts'
import { getPlatformStats, getUserStats, getLearningStats, getQuestionStats } from '@/api/stats'

const stats = reactive({
  totalUsers: 0,
  totalCourses: 0,
  totalQuestions: 0,
  totalWords: 0
})

const userPieChart = ref()
const questionBarChart = ref()
const answerTrendChart = ref()
const knowledgePointChart = ref()

const recentUsers = ref([])
const activeStudents = ref([])

let userPieInstance = null
let questionBarInstance = null
let answerTrendInstance = null
let knowledgePointInstance = null

onMounted(async () => {
  await loadDashboardData()
  await nextTick()
  initCharts()
})

const loadDashboardData = async () => {
  try {
    // 加载平台统计
    const platformRes = await getPlatformStats()
    if (platformRes.data) {
      stats.totalUsers = platformRes.data.totalUsers || 0
      stats.totalCourses = platformRes.data.totalCourses || 0
      stats.totalQuestions = platformRes.data.totalQuestions || 0
      stats.totalWords = platformRes.data.totalWords || 0
    }

    // 加载用户统计
    const userRes = await getUserStats()
    if (userRes.data) {
      renderUserPieChart(userRes.data)
      recentUsers.value = userRes.data.recentUsers || []
    }

    // 加载学习统计
    const learningRes = await getLearningStats({ days: 7 })
    if (learningRes.data) {
      renderAnswerTrendChart(learningRes.data.dailyStats || [])
      activeStudents.value = learningRes.data.activeStudents || []
    }

    // 加载题库统计
    const questionRes = await getQuestionStats()
    if (questionRes.data) {
      renderQuestionBarChart(questionRes.data.byType || {})
      renderKnowledgePointChart(questionRes.data.byCategory || {})
    }
  } catch (error) {
    console.error('加载仪表板数据失败:', error)
    // 使用模拟数据
    loadMockData()
  }
}

const loadMockData = () => {
  // 模拟数据
  stats.totalUsers = 156
  stats.totalCourses = 12
  stats.totalQuestions = 458
  stats.totalWords = 3200

  renderUserPieChart({
    adminCount: 2,
    teacherCount: 8,
    studentCount: 146
  })

  renderQuestionBarChart({
    '听力': 95,
    '阅读': 128,
    '完形填空': 87,
    '翻译': 76,
    '写作': 72
  })

  renderAnswerTrendChart([
    { date: '2024-01-09', count: 245 },
    { date: '2024-01-10', count: 312 },
    { date: '2024-01-11', count: 278 },
    { date: '2024-01-12', count: 421 },
    { date: '2024-01-13', count: 389 },
    { date: '2024-01-14', count: 456 },
    { date: '2024-01-15', count: 523 }
  ])

  renderKnowledgePointChart({
    '词汇': 145,
    '语法': 98,
    '听力': 87,
    '阅读': 95,
    '写作': 33
  })

  recentUsers.value = [
    { username: 'student01', realName: '张三', role: 'STUDENT', createdTime: '2024-01-15 10:30' },
    { username: 'teacher02', realName: '李老师', role: 'TEACHER', createdTime: '2024-01-14 15:20' },
    { username: 'student02', realName: '王五', role: 'STUDENT', createdTime: '2024-01-14 09:45' }
  ]

  activeStudents.value = [
    { studentName: '张三', answerCount: 523, accuracy: 87 },
    { studentName: '李四', answerCount: 456, accuracy: 92 },
    { studentName: '王五', answerCount: 389, accuracy: 78 },
    { studentName: '赵六', answerCount: 312, accuracy: 85 },
    { studentName: '钱七', answerCount: 278, accuracy: 90 }
  ]
}

const initCharts = () => {
  if (userPieChart.value) {
    userPieInstance = echarts.init(userPieChart.value)
  }
  if (questionBarChart.value) {
    questionBarInstance = echarts.init(questionBarChart.value)
  }
  if (answerTrendChart.value) {
    answerTrendInstance = echarts.init(answerTrendChart.value)
  }
  if (knowledgePointChart.value) {
    knowledgePointInstance = echarts.init(knowledgePointChart.value)
  }
}

const renderUserPieChart = (data) => {
  if (!userPieInstance) return

  const option = {
    tooltip: {
      trigger: 'item',
      formatter: '{a} <br/>{b}: {c} ({d}%)'
    },
    legend: {
      orient: 'vertical',
      left: 'left'
    },
    series: [
      {
        name: '用户角色',
        type: 'pie',
        radius: '60%',
        data: [
          { value: data.adminCount || 0, name: '管理员' },
          { value: data.teacherCount || 0, name: '教师' },
          { value: data.studentCount || 0, name: '学生' }
        ],
        emphasis: {
          itemStyle: {
            shadowBlur: 10,
            shadowOffsetX: 0,
            shadowColor: 'rgba(0, 0, 0, 0.5)'
          }
        }
      }
    ]
  }

  userPieInstance.setOption(option)
}

const renderQuestionBarChart = (data) => {
  if (!questionBarInstance) return

  const categories = Object.keys(data)
  const values = Object.values(data)

  const option = {
    tooltip: {
      trigger: 'axis',
      axisPointer: {
        type: 'shadow'
      }
    },
    xAxis: {
      type: 'category',
      data: categories
    },
    yAxis: {
      type: 'value'
    },
    series: [
      {
        name: '题目数量',
        type: 'bar',
        data: values,
        itemStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: '#83bff6' },
            { offset: 0.5, color: '#188df0' },
            { offset: 1, color: '#188df0' }
          ])
        }
      }
    ]
  }

  questionBarInstance.setOption(option)
}

const renderAnswerTrendChart = (data) => {
  if (!answerTrendInstance) return

  const dates = data.map(item => item.date)
  const counts = data.map(item => item.count)

  const option = {
    tooltip: {
      trigger: 'axis'
    },
    xAxis: {
      type: 'category',
      data: dates,
      boundaryGap: false
    },
    yAxis: {
      type: 'value'
    },
    series: [
      {
        name: '答题数',
        type: 'line',
        smooth: true,
        data: counts,
        areaStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: 'rgba(24, 144, 255, 0.3)' },
            { offset: 1, color: 'rgba(24, 144, 255, 0)' }
          ])
        },
        itemStyle: {
          color: '#1890ff'
        }
      }
    ]
  }

  answerTrendInstance.setOption(option)
}

const renderKnowledgePointChart = (data) => {
  if (!knowledgePointInstance) return

  const categories = Object.keys(data)
  const values = Object.values(data)

  const option = {
    tooltip: {
      trigger: 'item'
    },
    series: [
      {
        name: '知识点',
        type: 'pie',
        radius: ['40%', '70%'],
        avoidLabelOverlap: false,
        itemStyle: {
          borderRadius: 10,
          borderColor: '#fff',
          borderWidth: 2
        },
        label: {
          show: true,
          position: 'outside'
        },
        emphasis: {
          label: {
            show: true,
            fontSize: 14,
            fontWeight: 'bold'
          }
        },
        data: categories.map((cat, index) => ({
          value: values[index],
          name: cat
        }))
      }
    ]
  }

  knowledgePointInstance.setOption(option)
}

const getRoleTag = (role) => {
  const map = {
    'ADMIN': 'danger',
    'TEACHER': 'warning',
    'STUDENT': 'success'
  }
  return map[role] || 'info'
}

const getRoleLabel = (role) => {
  const map = {
    'ADMIN': '管理员',
    'TEACHER': '教师',
    'STUDENT': '学生'
  }
  return map[role] || role
}

const getAccuracyColor = (accuracy) => {
  if (accuracy >= 90) return '#67c23a'
  if (accuracy >= 70) return '#409eff'
  if (accuracy >= 60) return '#e6a23c'
  return '#f56c6c'
}
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

.stat-icon.questions {
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
  font-weight: bold;
}

.chart-container {
  height: 300px;
  width: 100%;
}
</style>
