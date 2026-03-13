<template>
  <div class="trend-dashboard">
    <!-- 现代化统计网格 -->
    <div class="stats-grid">
      <div class="stat-card trend-courses">
        <div class="stat-icon-wrapper">
          <div class="stat-icon">
            <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20"/>
              <path d="M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2z"/>
            </svg>
          </div>
          <div class="stat-glow"></div>
        </div>
        <div class="stat-content">
          <div class="stat-value">{{ dashboardData.enrolledCourses }}</div>
          <div class="stat-label">在学课程</div>
        </div>
      </div>

      <div class="stat-card trend-vocabulary">
        <div class="stat-icon-wrapper">
          <div class="stat-icon">
            <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/>
              <polyline points="14,2 14,8 20,8"/>
              <line x1="16" y1="13" x2="8" y2="13"/>
              <line x1="16" y1="17" x2="8" y2="17"/>
              <polyline points="10,9 9,9 8,9"/>
            </svg>
          </div>
          <div class="stat-glow"></div>
        </div>
        <div class="stat-content">
          <div class="stat-value">{{ dashboardData.vocabularyCount }}</div>
          <div class="stat-label">生词数量</div>
        </div>
      </div>

      <div class="stat-card trend-assignments">
        <div class="stat-icon-wrapper">
          <div class="stat-icon">
            <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M20 14.66V20a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2h5.34"/>
              <polygon points="18,2 22,6 12,16 8,16 8,12 18,2"/>
            </svg>
          </div>
          <div class="stat-glow"></div>
        </div>
        <div class="stat-content">
          <div class="stat-value">{{ dashboardData.pendingAssignments }}</div>
          <div class="stat-label">待完成任务</div>
        </div>
      </div>

      <div class="stat-card trend-studytime">
        <div class="stat-icon-wrapper">
          <div class="stat-icon">
            <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <circle cx="12" cy="12" r="10"/>
              <polyline points="12,6 12,12 16,14"/>
            </svg>
          </div>
          <div class="stat-glow"></div>
        </div>
        <div class="stat-content">
          <div class="stat-value">{{ dashboardData.todayStudyTime }}</div>
          <div class="stat-label">今日学习(分)</div>
        </div>
      </div>
    </div>

    <!-- 主要内容区域 -->
    <div class="content-section">
      <!-- 我的课程 - 现代卡片设计 -->
      <div class="trend-section">
        <div class="section-header">
          <h2 class="section-title">我的课程</h2>
          <button class="trend-btn secondary" @click="$router.push('/user/courses')">
            <span>全部课程</span>
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <polyline points="9,18 15,12 9,6"/>
            </svg>
          </button>
        </div>

        <div class="course-scroll" v-loading="loading">
          <div v-if="dashboardData.myClasses.length > 0" class="course-list">
            <div v-for="course in dashboardData.myClasses" :key="course.id" class="trend-course-card" @click="enterCourse(course)">
              <div class="course-gradient"></div>
              <div class="course-header">
                <div class="course-icon">
                  <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20"/>
                    <path d="M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2z"/>
                  </svg>
                </div>
                <div class="course-progress">{{ Math.min((course.learnedWords / 100) * 100, 100).toFixed(0) }}%</div>
              </div>

              <div class="course-body">
                <h3 class="course-name">{{ course.courseName }}</h3>
                <p class="course-meta">{{ course.className }} · {{ course.teacherName }}</p>

                <div class="progress-wrapper">
                  <div class="progress-bar">
                    <div class="progress-fill" :style="{ width: Math.min((course.learnedWords / 100) * 100, 100) + '%' }"></div>
                  </div>
                  <span class="progress-text">{{ course.learnedWords }}个单词</span>
                </div>
              </div>

              <div class="course-action">
                <span class="action-text">继续学习</span>
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <polyline points="9,18 15,12 9,6"/>
                </svg>
              </div>
            </div>
          </div>

          <div v-else class="empty-state">
            <div class="empty-icon">
              <svg width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
                <path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20"/>
                <path d="M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2z"/>
              </svg>
            </div>
            <p class="empty-text">暂无课程</p>
            <button class="trend-btn primary" @click="$router.push('/user/courses')">浏览课程</button>
          </div>
        </div>
      </div>

      <!-- 待办任务 -->
      <div class="trend-section">
        <div class="section-header">
          <h2 class="section-title">待办任务</h2>
          <button class="trend-btn secondary" @click="$router.push('/user/assignments')">
            <span>查看全部</span>
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <polyline points="9,18 15,12 9,6"/>
            </svg>
          </button>
        </div>

        <div class="assignment-scroll" v-loading="loading">
          <div v-if="dashboardData.pendingAssignmentsList.length > 0" class="assignment-list">
            <div v-for="assignment in dashboardData.pendingAssignmentsList" :key="assignment.id" class="trend-assignment-card" @click="startAssignment(assignment)">
              <div class="assignment-urgency" :class="getUrgencyClass(assignment.deadline)">
                <span class="urgency-dot"></span>
              </div>

              <div class="assignment-content">
                <h3 class="assignment-title">{{ assignment.title }}</h3>
                <p class="assignment-meta">{{ assignment.className }}</p>
                <div class="assignment-deadline">
                  <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <circle cx="12" cy="12" r="10"/>
                    <polyline points="12,6 12,12 16,14"/>
                  </svg>
                  <span>{{ formatTime(assignment.deadline) }}</span>
                </div>

                <div class="assignment-status">
                  <span class="status-tag" :class="getUrgencyClass(assignment.deadline)">
                    {{ getUrgencyText(assignment.deadline) }}
                  </span>
                </div>
              </div>

              <div class="assignment-arrow">
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <polyline points="9,18 15,12 9,6"/>
                </svg>
              </div>
            </div>
          </div>

          <div v-else class="empty-state">
            <div class="empty-icon">
              <svg width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
                <path d="M20 14.66V20a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2h5.34"/>
                <polygon points="18,2 22,6 12,16 8,16 8,12 18,2"/>
              </svg>
            </div>
            <p class="empty-text">暂无待办任务</p>
          </div>
        </div>
      </div>

      <!-- 学习推荐和今日统计 -->
      <div class="trend-section">
        <div class="dual-content">
          <!-- 学习推荐 -->
          <div class="recommendation-card">
            <div class="rec-header">
              <h3 class="rec-title">学习推荐</h3>
              <button class="refresh-btn" @click="loadDashboard">
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <polyline points="23,4 23,10 17,10"/>
                  <path d="M20.49 15a9 9 0 1 1-2.12-9.36L23 10"/>
                </svg>
              </button>
            </div>

            <div class="rec-content" v-loading="loading">
              <div v-if="dashboardData.learningRecommendation" class="rec-item" @click="handleRecommendationClick(dashboardData.learningRecommendation)">
                <div class="rec-type" :class="dashboardData.learningRecommendation.type">
                  {{ dashboardData.learningRecommendation.type === 'word' ? '单词' : '课文' }}
                </div>
                <h4 class="rec-title-text">{{ dashboardData.learningRecommendation.title }}</h4>
                <p class="rec-description">{{ dashboardData.learningRecommendation.content }}</p>
                <div v-if="dashboardData.learningRecommendation.pronunciation" class="rec-pronunciation">
                  {{ dashboardData.learningRecommendation.pronunciation }}
                </div>
              </div>

              <div v-else class="rec-empty">
                <p>暂无推荐内容</p>
              </div>
            </div>
          </div>

          <!-- 今日统计 -->
          <div class="stats-card">
            <div class="stats-header">
              <h3 class="stats-title">今日统计</h3>
            </div>

            <div class="stats-content" v-loading="loading">
              <div class="stat-row">
                <span class="stat-label">学习单词</span>
                <span class="stat-value highlight">{{ learningStats.wordsLearned }}个</span>
              </div>
              <div class="stat-row">
                <span class="stat-label">复习次数</span>
                <span class="stat-value">{{ learningStats.reviewCount }}次</span>
              </div>
              <div class="stat-row">
                <span class="stat-label">学习时长</span>
                <span class="stat-value">{{ learningStats.studyTime }}分钟</span>
              </div>
              <div class="stat-row">
                <span class="stat-label">平均正确率</span>
                <span class="stat-value accuracy">{{ learningStats.accuracy }}%</span>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import request from '@/utils/request'

const router = useRouter()

const loading = ref(false)
const dashboardData = reactive({
  enrolledCourses: 0,
  vocabularyCount: 0,
  pendingAssignments: 0,
  todayStudyTime: 0,
  myClasses: [],
  pendingAssignmentsList: [],
  learningRecommendation: null
})

const learningStats = reactive({
  wordsLearned: 0,
  reviewCount: 0,
  studyTime: 0,
  accuracy: 0
})

// 加载仪表板数据
const loadDashboard = async () => {
  loading.value = true
  try {
    // 模拟数据，普通用户端没有学生数据
    const mockData = {
      enrolledCourses: 0,
      vocabularyCount: 0,
      pendingAssignments: 0,
      todayStudyTime: 0,
      myClasses: [],
      pendingAssignmentsList: [],
      learningRecommendation: null
    }

    Object.assign(dashboardData, mockData)

    // 加载随机推荐内容
    try {
      const response = await request({
        url: '/api/public/texts/random',
        method: 'get',
        params: { count: 1 }
      })

      if (response?.code === 200 && response.data) {
        const dataList = Array.isArray(response.data) 
          ? response.data 
          : (Array.isArray(response.data?.records) ? response.data.records : [])
        
        if (dataList.length > 0 && dataList[0]) {
          const randomText = dataList[0]
          dashboardData.learningRecommendation = {
            type: 'text',
            title: randomText?.title || '未知课文',
            content: (randomText?.content || '').substring(0, 100) + '...',
            id: randomText?.id,
            unitId: randomText?.unitId
          }
        }
      }
    } catch (error) {
      console.error('加载推荐内容失败:', error)
    }

  } catch (error) {
    console.error('加载仪表板数据失败:', error)
  } finally {
    loading.value = false
  }
}

// 加载今日学习统计
const loadLearningStats = async () => {
  try {
    // 模拟统计数据
    Object.assign(learningStats, {
      wordsLearned: 0,
      reviewCount: 0,
      studyTime: 0,
      accuracy: 0
    })
  } catch (error) {
    console.error('加载学习统计失败:', error)
  }
}

// 进入课程
const enterCourse = (courseClass) => {
  router.push(`/user/courses?courseId=${courseClass.courseId}`)
}

// 开始任务
const startAssignment = (assignment) => {
  ElMessage.info('请先登录学生账号查看任务详情')
}

// 处理推荐内容点击
const handleRecommendationClick = (recommendation) => {
  if (recommendation.type === 'text') {
    router.push(`/user/text-learning?textId=${recommendation.id}`)
  }
}

// 时间格式化
const formatTime = (time) => {
  if (!time) return ''
  return new Date(time).toLocaleString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  })
}

// 获取紧急程度样式类
const getUrgencyClass = (deadline) => {
  if (!deadline) return 'normal'
  return 'normal' // 普通用户默认正常状态
}

// 获取紧急程度文本
const getUrgencyText = (deadline) => {
  if (!deadline) return '无截止时间'
  return '正常'
}

onMounted(() => {
  loadDashboard()
  loadLearningStats()
})
</script>

<style scoped>
/* 复用学生端的Dashboard样式 */
.trend-dashboard {
  min-height: calc(100vh - 120px);
  background: var(--bg-secondary);
  padding: 16px;
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif;
  overflow-x: hidden;
}

/* 现代化统计网格 */
.stats-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 12px;
  margin-bottom: 24px;
}

.stat-card {
  background: var(--bg-primary);
  border-radius: 20px;
  padding: 20px;
  box-shadow: var(--shadow-sm);
  border: 1px solid var(--border-light);
  position: relative;
  overflow: hidden;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}

.stat-card:hover {
  transform: translateY(-2px);
  box-shadow: var(--shadow-md);
}

.stat-icon-wrapper {
  position: absolute;
  top: -10px;
  right: -10px;
  width: 80px;
  height: 80px;
  opacity: 0.1;
  z-index: 0;
}

.stat-icon {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--primary-color);
}

.stat-glow {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: var(--gradient-brand);
  opacity: 0.05;
  z-index: -1;
}

.stat-content {
  position: relative;
  z-index: 1;
}

.stat-value {
  font-size: 32px;
  font-weight: 700;
  color: var(--text-primary);
  line-height: 1;
  margin-bottom: 4px;
}

.stat-label {
  font-size: 13px;
  color: var(--text-tertiary);
  font-weight: 500;
}

/* 统计卡片颜色变体 */
.trend-courses {
  border-left: 3px solid var(--primary-color);
}

.trend-courses .stat-icon,
.trend-courses .stat-glow {
  background: linear-gradient(135deg, var(--primary-color), var(--accent-color));
}

.trend-vocabulary {
  border-left: 3px solid var(--secondary-color);
}

.trend-vocabulary .stat-icon,
.trend-vocabulary .stat-glow {
  background: linear-gradient(135deg, var(--secondary-color), var(--vibrant-teal));
}

.trend-assignments {
  border-left: 3px solid var(--warning-color);
}

.trend-assignments .stat-icon,
.trend-assignments .stat-glow {
  background: linear-gradient(135deg, var(--warning-color), #FF7F50);
}

.trend-studytime {
  border-left: 3px solid var(--success-color);
}

.trend-studytime .stat-icon,
.trend-studytime .stat-glow {
  background: linear-gradient(135deg, var(--success-color), #20C997);
}

/* 内容区域 */
.content-section {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.trend-section {
  background: var(--bg-primary);
  border-radius: 20px;
  padding: 20px;
  box-shadow: var(--shadow-sm);
  border: 1px solid var(--border-light);
}

.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.section-title {
  font-size: 18px;
  font-weight: 600;
  color: var(--text-primary);
  margin: 0;
}

/* 现代按钮 */
.trend-btn {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 16px;
  border-radius: 12px;
  border: 1px solid var(--border-color);
  background: var(--bg-primary);
  color: var(--text-secondary);
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.3s ease;
  text-decoration: none;
}

.trend-btn:hover {
  background: var(--bg-tertiary);
  border-color: var(--primary-color);
  color: var(--primary-color);
  transform: translateY(-1px);
}

.trend-btn.primary {
  background: var(--gradient-brand);
  color: white;
  border: none;
}

.trend-btn.primary:hover {
  background: var(--primary-dark);
  box-shadow: var(--shadow-brand);
}

.trend-btn.secondary {
  background: var(--bg-secondary);
  color: var(--text-secondary);
}

/* 课程卡片 */
.course-scroll {
  overflow-x: auto;
  scroll-snap-type: x mandatory;
  -webkit-overflow-scrolling: touch;
  scrollbar-width: none;
  -ms-overflow-style: none;
  padding: 4px 0;
}

.course-scroll::-webkit-scrollbar {
  display: none;
}

.course-list {
  display: flex;
  gap: 12px;
  min-width: fit-content;
}

.trend-course-card {
  min-width: 280px;
  background: var(--bg-primary);
  border: 1px solid var(--border-light);
  border-radius: 16px;
  padding: 16px;
  cursor: pointer;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  position: relative;
  overflow: hidden;
  scroll-snap-align: start;
}

.trend-course-card:hover {
  transform: translateY(-4px);
  box-shadow: var(--shadow-lg);
  border-color: var(--primary-color);
}

.course-gradient {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 4px;
  background: var(--gradient-brand);
}

.course-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.course-icon {
  width: 32px;
  height: 32px;
  background: var(--gradient-brand);
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
}

.course-progress {
  font-size: 14px;
  font-weight: 600;
  color: var(--primary-color);
}

.course-name {
  font-size: 16px;
  font-weight: 600;
  color: var(--text-primary);
  margin: 0 0 4px 0;
  line-height: 1.3;
}

.course-meta {
  font-size: 13px;
  color: var(--text-tertiary);
  margin: 0 0 12px 0;
}

.progress-wrapper {
  margin-top: 8px;
}

.progress-bar {
  height: 6px;
  background: var(--bg-tertiary);
  border-radius: 3px;
  overflow: hidden;
  margin-bottom: 4px;
}

.progress-fill {
  height: 100%;
  background: var(--gradient-brand);
  border-radius: 3px;
  transition: width 0.3s ease;
}

.progress-text {
  font-size: 11px;
  color: var(--text-quaternary);
}

.course-action {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px solid var(--separator-color);
}

.action-text {
  font-size: 14px;
  font-weight: 500;
  color: var(--primary-color);
}

/* 任务卡片 */
.assignment-scroll {
  overflow-x: auto;
  scroll-snap-type: x mandatory;
  -webkit-overflow-scrolling: touch;
  scrollbar-width: none;
  -ms-overflow-style: none;
  padding: 4px 0;
}

.assignment-scroll::-webkit-scrollbar {
  display: none;
}

.assignment-list {
  display: flex;
  gap: 12px;
  min-width: fit-content;
}

.trend-assignment-card {
  min-width: 320px;
  background: var(--bg-primary);
  border: 1px solid var(--border-light);
  border-radius: 16px;
  padding: 16px;
  cursor: pointer;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  display: flex;
  align-items: flex-start;
  gap: 12px;
  scroll-snap-align: start;
}

.trend-assignment-card:hover {
  transform: translateY(-2px);
  box-shadow: var(--shadow-md);
}

.assignment-urgency {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex-shrink: 0;
  margin-top: 8px;
}

.urgency-dot {
  width: 100%;
  height: 100%;
  border-radius: 50%;
  background: currentColor;
}

.assignment-urgency.normal { color: var(--success-color); }

.assignment-content {
  flex: 1;
}

.assignment-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--text-primary);
  margin: 0 0 4px 0;
  line-height: 1.3;
}

.assignment-meta {
  font-size: 13px;
  color: var(--text-tertiary);
  margin: 0 0 8px 0;
}

.assignment-deadline {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: var(--text-tertiary);
  margin-bottom: 8px;
}

.assignment-status {
  display: flex;
  justify-content: flex-end;
}

.status-tag {
  padding: 2px 8px;
  border-radius: 8px;
  font-size: 11px;
  font-weight: 500;
}

.status-tag.normal {
  background: rgba(52, 199, 89, 0.1);
  color: var(--success-color);
}

.assignment-arrow {
  display: flex;
  align-items: center;
  color: var(--text-quaternary);
  margin-top: 8px;
}

/* 双列内容 */
.dual-content {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}

.recommendation-card,
.stats-card {
  background: var(--bg-primary);
  border: 1px solid var(--border-light);
  border-radius: 16px;
  padding: 16px;
}

.rec-header,
.stats-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.rec-title,
.stats-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--text-primary);
  margin: 0;
}

.refresh-btn {
  width: 32px;
  height: 32px;
  border: none;
  background: var(--bg-tertiary);
  border-radius: 8px;
  color: var(--text-secondary);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.2s ease;
}

.refresh-btn:hover {
  background: var(--bg-secondary);
  color: var(--primary-color);
}

.rec-item {
  cursor: pointer;
  padding: 12px;
  border: 1px solid var(--border-light);
  border-radius: 12px;
  transition: all 0.3s ease;
}

.rec-item:hover {
  border-color: var(--primary-color);
  box-shadow: var(--shadow-sm);
}

.rec-type {
  display: inline-block;
  padding: 2px 8px;
  border-radius: 8px;
  font-size: 11px;
  font-weight: 500;
  margin-bottom: 8px;
}

.rec-type.word {
  background: rgba(0, 102, 204, 0.1);
  color: var(--primary-color);
}

.rec-type.text {
  background: rgba(0, 200, 150, 0.1);
  color: var(--success-color);
}

.rec-title-text {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-primary);
  margin: 8px 0;
  line-height: 1.3;
}

.rec-description {
  font-size: 13px;
  color: var(--text-secondary);
  line-height: 1.4;
  margin: 8px 0;
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.rec-pronunciation {
  font-size: 12px;
  color: var(--text-tertiary);
  font-family: 'Lucida Sans Unicode', 'Arial Unicode MS', Arial;
  font-style: italic;
  margin-top: 8px;
  padding-top: 8px;
  border-top: 1px solid var(--separator-color);
}

.rec-empty {
  text-align: center;
  color: var(--text-tertiary);
  font-size: 14px;
  padding: 20px;
}

/* 统计内容 */
.stats-content {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.stat-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 0;
  border-bottom: 1px solid var(--separator-color);
}

.stat-row:last-child {
  border-bottom: none;
}

.stat-row .stat-label {
  font-size: 13px;
  color: var(--text-secondary);
}

.stat-row .stat-value {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-primary);
}

.stat-row .stat-value.highlight {
  color: var(--primary-color);
}

.stat-row .stat-value.accuracy {
  color: var(--success-color);
}

/* 空状态 */
.empty-state {
  text-align: center;
  padding: 40px 20px;
  color: var(--text-tertiary);
}

.empty-icon {
  margin-bottom: 16px;
  opacity: 0.3;
}

.empty-text {
  font-size: 16px;
  color: var(--text-tertiary);
  margin: 0 0 20px 0;
}

/* 响应式设计 */
@media (max-width: 480px) {
  .trend-dashboard {
    padding: 12px;
  }

  .stats-grid {
    gap: 10px;
    margin-bottom: 20px;
  }

  .stat-card {
    padding: 16px;
  }

  .stat-value {
    font-size: 28px;
  }

  .stat-label {
    font-size: 12px;
  }

  .trend-section {
    padding: 16px;
  }

  .section-title {
    font-size: 16px;
  }

  .trend-course-card {
    min-width: 260px;
  }

  .trend-assignment-card {
    min-width: 300px;
  }

  .dual-content {
    grid-template-columns: 1fr;
    gap: 16px;
  }

  .recommendation-card,
  .stats-card {
    padding: 14px;
  }
}

@media (max-width: 360px) {
  .trend-dashboard {
    padding: 8px;
  }

  .stat-card {
    padding: 12px;
  }

  .stat-value {
    font-size: 24px;
  }

  .trend-course-card {
    min-width: 240px;
    padding: 14px;
  }

  .trend-assignment-card {
    min-width: 280px;
    padding: 14px;
  }

  .trend-section {
    padding: 12px;
  }
}

/* 横屏适配 */
@media (max-height: 700px) and (orientation: landscape) {
  .trend-dashboard {
    padding: 8px;
  }

  .stats-grid {
    gap: 8px;
    margin-bottom: 16px;
  }

  .stat-card {
    padding: 12px;
  }

  .stat-value {
    font-size: 22px;
  }

  .stat-label {
    font-size: 11px;
  }
}

/* 触摸优化 */
.stat-card,
.trend-course-card,
.trend-assignment-card,
.rec-item,
.trend-btn,
.refresh-btn {
  -webkit-tap-highlight-color: transparent;
  touch-action: manipulation;
}

/* 加载状态优化 */
:deep(.el-loading-mask) {
  border-radius: 16px;
}

:deep(.el-loading-spinner .circular) {
  width: 32px;
  height: 32px;
}
</style>