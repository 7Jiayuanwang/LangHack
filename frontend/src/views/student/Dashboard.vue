<template>
  <div class="dashboard">
    <el-row :gutter="20">
      <el-col :span="6">
        <el-card class="stat-card">
          <div class="stat-content">
            <div class="stat-icon courses">
              <el-icon><Reading /></el-icon>
            </div>
            <div class="stat-info">
              <h3>{{ stats.enrolledCourses }}</h3>
              <p>在学课程</p>
            </div>
          </div>
        </el-card>
      </el-col>

      <el-col :span="6">
        <el-card class="stat-card">
          <div class="stat-content">
            <div class="stat-icon vocabulary">
              <el-icon><Document /></el-icon>
            </div>
            <div class="stat-info">
              <h3>{{ stats.vocabularyCount }}</h3>
              <p>生词数量</p>
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
              <h3>{{ stats.pendingAssignments }}</h3>
              <p>待完成任务</p>
            </div>
          </div>
        </el-card>
      </el-col>

      <el-col :span="6">
        <el-card class="stat-card">
          <div class="stat-content">
            <div class="stat-icon studytime">
              <el-icon><Clock /></el-icon>
            </div>
            <div class="stat-info">
              <h3>{{ stats.studyHours }}</h3>
              <p>学习时长(小时)</p>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="20" class="mt-20">
      <el-col :span="16">
        <el-card>
          <template #header>
            <div class="card-header">
              <span>我的课程</span>
              <el-button type="primary" size="small" @click="$router.push('/student/courses')">
                查看全部课程
              </el-button>
            </div>
          </template>
          <div class="course-list">
            <div v-for="course in courses" :key="course.id" class="course-item">
              <div class="course-info">
                <h4>{{ course.name }}</h4>
                <p>{{ course.description }}</p>
                <div class="progress-info">
                  <span>学习进度：{{ course.completedUnits }}/{{ course.totalUnits }} 单元</span>
                  <el-progress
                    :percentage="Math.round((course.completedUnits / course.totalUnits) * 100)"
                  />
                </div>
              </div>
              <div class="course-actions">
                <el-button type="primary" size="small" @click="continueStudy(course)">
                  继续学习
                </el-button>
              </div>
            </div>
          </div>
        </el-card>
      </el-col>

      <el-col :span="8">
        <el-card>
          <template #header>
            <div class="card-header">
              <span>待办任务</span>
              <el-button type="primary" size="small" @click="$router.push('/student/assignments')">
                查看全部
              </el-button>
            </div>
          </template>
          <div class="assignment-list">
            <div v-for="assignment in pendingAssignments" :key="assignment.id" class="assignment-item">
              <div class="assignment-info">
                <h4>{{ assignment.title }}</h4>
                <p>课程：{{ assignment.courseName }}</p>
                <p class="deadline">截止时间：{{ formatTime(assignment.deadline) }}</p>
                <div class="urgency">
                  <el-tag
                    :type="getUrgencyType(assignment.deadline)"
                    size="small"
                  >
                    {{ getUrgencyText(assignment.deadline) }}
                  </el-tag>
                </div>
              </div>
              <div class="assignment-actions">
                <el-button size="small" @click="startAssignment(assignment)">
                  开始任务
                </el-button>
              </div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="20" class="mt-20">
      <el-col :span="12">
        <el-card>
          <template #header>
            <span>最近学习的单词</span>
          </template>
          <div class="recent-words">
            <el-tag
              v-for="word in recentWords"
              :key="word.id"
              class="word-tag"
              @click="viewWordDetail(word)"
            >
              {{ word.word }}
            </el-tag>
          </div>
        </el-card>
      </el-col>

      <el-col :span="12">
        <el-card>
          <template #header>
            <span>学习统计</span>
          </template>
          <div class="study-stats">
            <div class="stat-item">
              <span>今日学习：</span>
              <span class="value">{{ studyStats.todayWords }}个单词</span>
            </div>
            <div class="stat-item">
              <span>本周学习：</span>
              <span class="value">{{ studyStats.weekWords }}个单词</span>
            </div>
            <div class="stat-item">
              <span>连续学习：</span>
              <span class="value">{{ studyStats.streakDays }}天</span>
            </div>
            <div class="stat-item">
              <span>总学习天数：</span>
              <span class="value">{{ studyStats.totalDays }}天</span>
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
  enrolledCourses: 0,
  vocabularyCount: 0,
  pendingAssignments: 0,
  studyHours: 0
})

const courses = ref([])
const pendingAssignments = ref([])
const recentWords = ref([])
const studyStats = reactive({
  todayWords: 0,
  weekWords: 0,
  streakDays: 0,
  totalDays: 0
})

const loadDashboardData = async () => {
  // 模拟API调用
  stats.enrolledCourses = 3
  stats.vocabularyCount = 125
  stats.pendingAssignments = 5
  stats.studyHours = 42

  courses.value = [
    {
      id: 1,
      name: '新概念英语第一册',
      description: '适合英语初学者的经典教材',
      completedUnits: 8,
      totalUnits: 12
    },
    {
      id: 2,
      name: '大学英语四级',
      description: '大学英语四级考试备考课程',
      completedUnits: 3,
      totalUnits: 10
    }
  ]

  pendingAssignments.value = [
    {
      id: 1,
      title: '第一单元单词学习',
      courseName: '新概念英语第一册',
      deadline: new Date(Date.now() + 2 * 24 * 60 * 60 * 1000).toISOString()
    },
    {
      id: 2,
      title: '第二单元课文阅读',
      courseName: '新概念英语第一册',
      deadline: new Date(Date.now() + 5 * 24 * 60 * 60 * 1000).toISOString()
    },
    {
      id: 3,
      title: '听力练习',
      courseName: '大学英语四级',
      deadline: new Date(Date.now() + 1 * 24 * 60 * 60 * 1000).toISOString()
    }
  ]

  recentWords.value = [
    { id: 1, word: 'hello', definition: '你好' },
    { id: 2, word: 'world', definition: '世界' },
    { id: 3, word: 'beautiful', definition: '美丽的' },
    { id: 4, word: 'learn', definition: '学习' },
    { id: 5, word: 'study', definition: '研究' },
    { id: 6, word: 'language', definition: '语言' },
    { id: 7, word: 'practice', definition: '练习' },
    { id: 8, word: 'improve', definition: '提高' }
  ]

  studyStats.todayWords = 15
  studyStats.weekWords = 68
  studyStats.streakDays = 7
  studyStats.totalDays = 25
}

const continueStudy = (course) => {
  router.push(`/student/courses?id=${course.id}`)
}

const startAssignment = (assignment) => {
  router.push(`/student/assignments?id=${assignment.id}`)
}

const viewWordDetail = (word) => {
  router.push(`/student/vocabulary?word=${word.word}`)
}

const formatTime = (time) => {
  if (!time) return ''
  return new Date(time).toLocaleString()
}

const getUrgencyType = (deadline) => {
  const now = new Date()
  const deadlineDate = new Date(deadline)
  const diffHours = (deadlineDate - now) / (1000 * 60 * 60)

  if (diffHours < 24) return 'danger'
  if (diffHours < 72) return 'warning'
  return 'success'
}

const getUrgencyText = (deadline) => {
  const now = new Date()
  const deadlineDate = new Date(deadline)
  const diffHours = (deadlineDate - now) / (1000 * 60 * 60)

  if (diffHours < 24) return '紧急'
  if (diffHours < 72) return '尽快'
  return '正常'
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

.stat-icon.courses {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
}

.stat-icon.vocabulary {
  background: linear-gradient(135deg, #f093fb 0%, #f5576c 100%);
}

.stat-icon.assignments {
  background: linear-gradient(135deg, #4facfe 0%, #00f2fe 100%);
}

.stat-icon.studytime {
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

.course-list {
  max-height: 400px;
  overflow-y: auto;
}

.course-item {
  padding: 15px;
  border-bottom: 1px solid #f0f0f0;
}

.course-item:last-child {
  border-bottom: none;
}

.course-info h4 {
  margin: 0 0 8px 0;
  color: #333;
  font-size: 16px;
}

.course-info p {
  margin: 4px 0;
  color: #666;
  font-size: 14px;
}

.progress-info {
  margin-top: 10px;
}

.progress-info span {
  font-size: 12px;
  color: #666;
  display: block;
  margin-bottom: 4px;
}

.course-actions {
  margin-top: 10px;
}

.assignment-list {
  max-height: 400px;
  overflow-y: auto;
}

.assignment-item {
  padding: 12px;
  border-bottom: 1px solid #f0f0f0;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.assignment-item:last-child {
  border-bottom: none;
}

.assignment-info h4 {
  margin: 0 0 4px 0;
  color: #333;
  font-size: 14px;
}

.assignment-info p {
  margin: 2px 0;
  color: #666;
  font-size: 12px;
}

.assignment-info .deadline {
  color: #f56c6c;
  font-weight: bold;
}

.urgency {
  margin-top: 4px;
}

.assignment-actions {
  flex-shrink: 0;
  margin-left: 10px;
}

.recent-words {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.word-tag {
  cursor: pointer;
  transition: all 0.3s;
}

.word-tag:hover {
  transform: scale(1.05);
}

.study-stats .stat-item {
  display: flex;
  justify-content: space-between;
  padding: 8px 0;
  border-bottom: 1px solid #f0f0f0;
}

.study-stats .stat-item:last-child {
  border-bottom: none;
}

.study-stats .value {
  font-weight: bold;
  color: #409EFF;
}
</style>