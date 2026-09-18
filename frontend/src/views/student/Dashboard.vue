<template>
  <div class="trend-dashboard">
    <XyPageHeader class="dashboard-intro" variant="hero" eyebrow="GROW WITH XIAOYA" title="今天也从一点好奇开始" description="每一次阅读、练习和提问，都会让你离目标更近一点。">
      <template #visual><XyMascot :size="88" /></template>
    </XyPageHeader>
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

      <div class="stat-card trend-reading">
        <div class="stat-icon-wrapper">
          <div class="stat-icon">
            <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M2 3h6a4 4 0 0 1 4 4v14a3 3 0 0 0-3-3H2z"/>
              <path d="M22 3h-6a4 4 0 0 0-4 4v14a3 3 0 0 1 3-3h7z"/>
            </svg>
          </div>
          <div class="stat-glow"></div>
        </div>
        <div class="stat-content">
          <div class="stat-value">{{ readingStats.totalCount }}</div>
          <div class="stat-label">阅读次数</div>
        </div>
      </div>

      <div class="stat-card trend-score" v-if="dashboardData.averageScore !== undefined && dashboardData.averageScore > 0">
        <div class="stat-icon-wrapper">
          <div class="stat-icon">
            <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M12 2L15.09 8.26L22 9.27L17 14.14L18.18 21.02L12 17.77L5.82 21.02L7 14.14L2 9.27L8.91 8.26L12 2Z"/>
            </svg>
          </div>
          <div class="stat-glow"></div>
        </div>
        <div class="stat-content">
          <div class="stat-value">{{ formatScore(dashboardData.averageScore) }}</div>
          <div class="stat-label">平均分数</div>
        </div>
      </div>
    </div>

    <FollowSummary />

    <!-- 主要内容区域 -->
    <div class="content-section">
      <!-- 我的课程 - 现代卡片设计（受 page_courses 开关控制） -->
      <div v-if="isEnabled('page_courses')" class="trend-section">
        <div class="section-header">
          <h2 class="section-title">我的课程</h2>
          <button class="trend-btn secondary" @click="$router.push('/student/courses')">
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
                <div style="position: relative; width: 32px; height: 32px; display: flex; align-items: center; justify-content: center;">
                  <div class="course-icon"></div>
                  <div class="course-icon-inner">
                    <svg class="course-icon-svg" viewBox="0 0 24 24" fill="none" stroke="currentColor">
                      <path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20"/>
                      <path d="M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2z"/>
                    </svg>
                  </div>
                </div>
                <div class="course-progress">{{ getCourseProgress(course) }}%</div>
              </div>

              <div class="course-body">
                <h3 class="course-name">{{ course.courseName }}</h3>
                <p class="course-meta">{{ course.className }} · {{ course.teacherName }}</p>

                <div class="progress-wrapper">
                  <div class="progress-bar">
                    <div class="progress-fill" :style="{ width: getCourseProgress(course) + '%' }"></div>
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
            <button v-if="isEnabled('page_classes')" class="trend-btn primary" @click="$router.push('/student/classes')">加入班级</button>
          </div>
        </div>
      </div>

      <!-- 每日单词背诵任务（受 page_word_recite 开关控制） -->
      <div v-if="isEnabled('page_word_recite')" class="trend-section">
        <div class="section-header">
          <h2 class="section-title">每日单词背诵</h2>
          <button class="trend-btn secondary" @click="$router.push('/student/word-recite')">
            <span>开始背诵</span>
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <polyline points="9,18 15,12 9,6"/>
            </svg>
          </button>
        </div>

        <div class="daily-word-recite-card" @click="$router.push('/student/word-recite')">
          <div class="recite-icon-wrapper">
            <div class="recite-icon">
              <svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20"/>
                <path d="M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2z"/>
                <line x1="8" y1="8" x2="16" y2="8"/>
                <line x1="8" y1="12" x2="14" y2="12"/>
              </svg>
            </div>
          </div>
          <div class="recite-content">
            <h3 class="recite-title">今日单词背诵任务</h3>
            <p class="recite-desc">完成每日单词背诵和复习任务</p>
            <div class="recite-progress">
              <div class="progress-bar-mini">
                <div class="progress-fill-mini" :style="{ width: wordReciteProgress + '%' }"></div>
              </div>
              <span class="progress-text-mini">已背诵 {{ todayReviewed }} 个</span>
            </div>
          </div>
          <div class="recite-status">
            <span class="status-badge" :class="{ 'completed': wordReciteCompleted }">
              {{ wordReciteCompleted ? '已完成' : '进行中' }}
            </span>
          </div>
        </div>
      </div>

      <!-- 单词本背诵任务（受 page_word_recite 开关控制） -->
      <div v-if="isEnabled('page_word_recite')" class="trend-section">
        <div class="section-header">
          <h2 class="section-title">单词本背诵</h2>
          <button class="trend-btn secondary" @click="$router.push('/student/vocabulary')">
            <span>开始复习</span>
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <polyline points="9,18 15,12 9,6"/>
            </svg>
          </button>
        </div>

        <div class="daily-word-recite-card vocabulary-card" @click="$router.push('/student/vocabulary')">
          <div class="recite-icon-wrapper vocabulary-icon">
            <div class="recite-icon">
              <svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M12 6.253v13m0-13C10.832 5.477 9.246 5 7.5 5S4.168 5.477 3 6.253v13C4.168 18.477 5.754 18 7.5 18s3.332.477 4.5 1.253m0-13C13.168 5.477 14.754 5 16.5 5c1.747 0 3.332.477 4.5 1.253v13C19.832 18.477 18.247 18 16.5 18c-1.746 0-3.332.477-4.5 1.253"/>
              </svg>
            </div>
          </div>
          <div class="recite-content">
            <h3 class="recite-title">生词本复习任务</h3>
            <p class="recite-desc">复习单词本中收藏的单词</p>
            <div class="recite-progress">
              <div class="progress-bar-mini">
                <div class="progress-fill-mini vocabulary-progress" :style="{ width: vocabularyProgress + '%' }"></div>
              </div>
              <span class="progress-text-mini">待复习 {{ vocabularyStats.reviewableCount }} 个</span>
            </div>
          </div>
          <div class="recite-status">
            <span class="status-badge" :class="{ 'completed': vocabularyStats.reviewableCount === 0 }">
              {{ vocabularyStats.reviewableCount === 0 ? '已完成' : '进行中' }}
            </span>
          </div>
        </div>
      </div>

      <!-- 待办任务（受 page_assignments 开关控制） -->
      <div v-if="isEnabled('page_assignments')" class="trend-section">
        <div class="section-header">
          <h2 class="section-title">待办任务</h2>
          <button class="trend-btn secondary" @click="$router.push('/student/assignments')">
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

      <!-- 成就展示 -->
      <div class="trend-section">
        <div class="section-header">
          <h2 class="section-title">
            <span class="section-icon">🏆</span> 学习成就
          </h2>
          <span class="achievement-summary" v-if="achievementStats.totalAchievements">
            已解锁 {{ achievementStats.unlockedCount }} / {{ achievementStats.totalAchievements }}
          </span>
        </div>

        <div class="achievement-grid" v-loading="achievementLoading">
          <div
            v-for="ach in achievementList"
            :key="ach.key"
            class="achievement-card"
            :class="{ unlocked: ach.unlocked, locked: !ach.unlocked }"
          >
            <div class="ach-icon">{{ ach.icon }}</div>
            <div class="ach-info">
              <h4 class="ach-title">{{ ach.title }}</h4>
              <p class="ach-desc">{{ ach.desc }}</p>
            </div>
            <div class="ach-status">
              <span v-if="ach.unlocked" class="ach-badge unlocked-badge">已解锁</span>
              <span v-else class="ach-badge locked-badge">未解锁</span>
            </div>
          </div>
        </div>

        <div v-if="!achievementLoading && achievementList.length === 0" class="empty-state">
          <p class="empty-text">开始学习，解锁你的第一个成就吧！</p>
        </div>
      </div>

      <!-- 学习推荐和今日统计 -->
      <div class="trend-section">
        <div class="dual-content">
          <!-- 学习推荐 -->
          <div class="recommendation-card">
            <div class="rec-header">
              <h3 class="rec-title">学习推荐</h3>
              <button class="refresh-btn" @click="loadDashboardData">
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
import { ref, reactive, onMounted, computed } from "vue"
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/store/user'
import FollowSummary from '@/components/FollowSummary.vue'
import XyPageHeader from '@/components/ui/XyPageHeader.vue'
import XyMascot from '@/components/ui/XyMascot.vue'
import { useFeatureToggles } from '@/composables/useFeatureToggles'
import {
  getStudentDashboard,
  getTodayLearningStats,
  getVocabularyStats,
  getReadingHistory,
  getAchievementStats
} from '@/api/student'

const router = useRouter()

// 功能开关：发现页的每个快捷入口 section 都受对应 page_* 开关控制
// 开关关闭时，对应区块整体隐藏，并禁止推荐卡片跳转到该页面
const { isEnabled, load: loadFeatureToggles, isLoaded } = useFeatureToggles()

const loading = ref(false)
const dashboardData = reactive({
  enrolledCourses: 0,
  vocabularyCount: 0,
  pendingAssignments: 0,
  todayStudyTime: 0,
  myClasses: [],
  pendingAssignmentsList: [],
  learningRecommendation: null,
  averageScore: 0,
  scoreStats: null
})

const learningStats = reactive({
  wordsLearned: 0,
  reviewCount: 0,
  studyTime: 0,
  accuracy: 0
})

// 每日单词背诵数据
const dailyWordCount = ref(10)
const todayReviewed = ref(0)
const wordReciteCompleted = ref(false)
const wordReciteProgress = computed(() => {
  if (dailyWordCount.value === 0) return 0
  return Math.min(Math.round((todayReviewed.value / dailyWordCount.value) * 100), 100)
})

// 单词本数据
const vocabularyStats = reactive({
  totalWords: 0,
  reviewableCount: 0,
  newWords: 0,
  learningWords: 0,
  masteredWords: 0
})

// 阅读统计数据
const readingStats = reactive({
  totalCount: 0,      // 总阅读次数
  totalArticles: 0    // 总阅读篇数
})

// 成就数据
const achievementList = ref([])
const achievementStats = reactive({
  totalAchievements: 0,
  unlockedCount: 0
})
const achievementLoading = ref(false)

const vocabularyProgress = computed(() => {
  if (vocabularyStats.totalWords === 0) return 0
  const completed = vocabularyStats.masteredWords
  return Math.min(Math.round((completed / vocabularyStats.totalWords) * 100), 100)
})

// 加载每日单词背诵进度
const loadDailyWordReciteProgress = () => {
  const userStore = useUserStore()
  const currentUserId = userStore.userInfo?.id || 'anonymous'
  
  // 从 localStorage 读取今日背诵进度
  const savedCount = localStorage.getItem(`todayReviewed_${currentUserId}`)
  const savedDate = localStorage.getItem(`lastReviewDate_${currentUserId}`)
  const today = new Date().toDateString()
  
  if (savedDate === today && savedCount) {
    todayReviewed.value = parseInt(savedCount)
    wordReciteCompleted.value = todayReviewed.value >= dailyWordCount.value
  } else {
    // 新的一天，重置进度
    todayReviewed.value = 0
    wordReciteCompleted.value = false
    localStorage.setItem(`lastReviewDate_${currentUserId}`, today)
    localStorage.setItem(`todayReviewed_${currentUserId}`, '0')
  }
  
  // 读取每日背诵数量设置
  const savedDailyCount = localStorage.getItem(`daily_count_${currentUserId}_cet4`)
  if (savedDailyCount) {
    dailyWordCount.value = parseInt(savedDailyCount)
  }
}

// 加载单词本统计
const loadVocabularyStats = async () => {
  try {
    const response = await getVocabularyStats()
    if (response?.code === 200 && response?.data) {
      Object.assign(vocabularyStats, {
        totalWords: response.data.totalWords || 0,
        reviewableCount: response.data.reviewableCount || response.data.learningWords || 0,
        newWords: response.data.newWords || 0,
        learningWords: response.data.learningWords || 0,
        masteredWords: response.data.masteredWords || 0
      })
    }
  } catch (error) {
    console.error('加载单词本统计失败:', error)
  }
}

// 加载阅读统计
const loadReadingStats = async () => {
  try {
    const response = await getReadingHistory({ page: 1, size: 1 })
    if (response?.code === 200 && response?.data?.overview) {
      const overview = response.data.overview
      readingStats.totalCount = overview.totalReadingTimes || 0
      readingStats.totalArticles = overview.totalArticlesRead || 0
    }
  } catch (error) {
    console.error('加载阅读统计失败:', error)
  }
}

// 加载成就数据
const loadAchievements = async () => {
  achievementLoading.value = true
  try {
    const response = await getAchievementStats()
    if (response?.code === 200 && response?.data) {
      achievementList.value = response.data.achievementList || []
      achievementStats.totalAchievements = response.data.totalAchievements || 0
      achievementStats.unlockedCount = response.data.unlockedCount || 0

      // 检查是否有新解锁的成就需要弹窗
      checkNewAchievements(response.data)
    }
  } catch (error) {
    console.error('加载成就数据失败:', error)
  } finally {
    achievementLoading.value = false
  }
}

// 检查新成就并触发弹窗
const checkNewAchievements = (data) => {
  const achievementList = data.achievementList || []
  // 只对已解锁的成就检查是否需要弹窗
  const unlockedList = achievementList.filter(a => a.unlocked)

  // 从 localStorage 获取已弹窗过的成就
  let shownAchievements = []
  try {
    shownAchievements = JSON.parse(localStorage.getItem('shownAchievements') || '[]')
  } catch (e) {
    shownAchievements = []
  }

  // 找出还没弹窗过的成就
  const newAchievements = unlockedList.filter(a => !shownAchievements.includes(a.key))

  if (newAchievements.length > 0 && typeof window !== 'undefined') {
    // 只弹出第一个新成就
    const firstNew = newAchievements[0]
    if (window[firstNew.key]) {
      window[firstNew.key]()
    }

    // 标记所有新成就为已弹窗
    const updatedShown = [...new Set([...shownAchievements, ...newAchievements.map(a => a.key)])]
    try {
      localStorage.setItem('shownAchievements', JSON.stringify(updatedShown))
    } catch (e) {
      // ignore
    }
  }
}

// 加载仪表板数据
const loadDashboardData = async () => {
  loading.value = true
  try {
    const response = await getStudentDashboard()
    if (response?.code === 200 && response?.data) {
      Object.assign(dashboardData, {
        ...response.data,
        myClasses: Array.isArray(response.data.myClasses) ? response.data.myClasses : [],
        pendingAssignmentsList: Array.isArray(response.data.pendingAssignmentsList) ? response.data.pendingAssignmentsList : []
      })
    } else {
      ElMessage.error('获取数据异常')
    }
  } catch (error) {
    console.error('加载仪表板失败:', error)
    ElMessage.error('加载数据失败，请稍后重试')
  } finally {
    loading.value = false
  }
}

// 加载今日学习统计
const loadLearningStats = async () => {
  try {
    const response = await getTodayLearningStats()
    if (response?.code === 200 && response?.data) {
      // 防御性编程：验证数据结构
      const stats = response.data
      if (typeof stats === 'object' && stats !== null) {
        Object.assign(learningStats, {
          wordsLearned: stats.wordsLearned ?? 0,
          reviewCount: stats.reviewCount ?? 0,
          studyTime: stats.studyTime ?? 0,
          accuracy: stats.accuracy ?? 0
        })
      }
    }
  } catch (error) {
    console.error('加载学习统计失败:', error)
    // 异常时重置为默认值，防止显示脏数据
    Object.assign(learningStats, {
      wordsLearned: 0,
      reviewCount: 0,
      studyTime: 0,
      accuracy: 0
    })
  }
}

// 进入课程
const enterCourse = (courseClass) => {
  router.push(`/student/courses?courseId=${courseClass.courseId}`)
}

// 开始任务
const startAssignment = (assignment) => {
  router.push(`/student/assignments?id=${assignment.id}`)
}

// 处理推荐内容点击
const handleRecommendationClick = (recommendation) => {
  if (!recommendation) return
  // 防御：推荐默认跳转到课程页，若 page_courses 关闭则不响应点击，避免路由守卫重定向
  if (!isEnabled('page_courses')) {
    ElMessage.info('课程页已被管理员关闭')
    return
  }
  // 跳转到课程学习页面，定位到对应课程
  const query = {
    courseId: recommendation.courseId,
    focus: recommendation.type,
    id: recommendation.id
  }
  if (recommendation.unitId) {
    query.unitId = recommendation.unitId
  }
  router.push({
    path: '/student/courses',
    query
  })
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
  const now = new Date()
  const deadlineDate = new Date(deadline)
  const diffHours = (deadlineDate - now) / (1000 * 60 * 60)

  if (diffHours < 0) return 'overdue'
  if (diffHours < 24) return 'urgent'
  if (diffHours < 72) return 'soon'
  return 'normal'
}

// 获取紧急程度文本
const getUrgencyText = (deadline) => {
  if (!deadline) return '无截止时间'
  const now = new Date()
  const deadlineDate = new Date(deadline)
  const diffHours = (deadlineDate - now) / (1000 * 60 * 60)

  if (diffHours < 0) return '已逾期'
  if (diffHours < 24) return '紧急'
  if (diffHours < 72) return '尽快'
  return '正常'
}

// 获取课程进度
const getCourseProgress = (course) => {
  // 优先使用后端返回的progress字段
  if (course.progress !== undefined && course.progress !== null) {
    const progress = typeof course.progress === 'number' ? course.progress : parseFloat(course.progress)
    if (!isNaN(progress)) {
      // 后端返回 0.0-1.0 范围，转换为 0-100 百分比
      const percentage = progress <= 1 ? progress * 100 : progress
      return Math.round(percentage)
    }
  }
  // 如果没有progress字段，使用learnedUnits计算
  if (course.totalUnits && course.totalUnits > 0 && course.learnedUnits !== undefined && course.learnedUnits !== null) {
    const calculated = Math.round((course.learnedUnits / course.totalUnits) * 100)
    return calculated
  }
  // 如果都没有，返回0
  return 0
}

// 格式化分数（保留1位小数，如果是整数则不显示小数）
const formatScore = (score) => {
  if (score === null || score === undefined || score === 0) return '0'
  const numScore = typeof score === 'number' ? score : parseFloat(score)
  if (isNaN(numScore)) return '0'
  // 如果是整数，直接返回整数；否则保留1位小数
  return numScore % 1 === 0 ? numScore.toString() : numScore.toFixed(1)
}

onMounted(() => {
  loadDashboardData()
  loadLearningStats()
  loadDailyWordReciteProgress()
  loadVocabularyStats()
  loadReadingStats()
  loadAchievements()
  // 共享 feature-toggles 缓存（路由守卫与 Layout 已在加载；此处仅兜底）
  try {
    if (!isLoaded()) {
      const userStore = useUserStore()
      const usePersonal = !!userStore.token && (userStore.userRole === 'STUDENT' || userStore.userRole === 'USER')
      loadFeatureToggles({ personal: usePersonal }).catch(() => {})
    }
  } catch { /* 测试环境或无 Pinia 时静默跳过 */ }
})
</script>

<style scoped>
/* 现代化Dashboard样式 */
.trend-dashboard {
  min-height: calc(100vh - 120px);
  background: var(--xy-gradient-page);
  padding: 16px;
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif;
  overflow-x: hidden;
}
.dashboard-intro { margin-bottom: 18px; }

/* 现代化统计网格 */
.stats-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 16px;
  margin-bottom: 24px;
}

.stat-card {
  background: rgba(255, 255, 255, 0.94);
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  border-radius: var(--xy-radius-md);
  padding: 20px;
  box-shadow: var(--xy-shadow-card);
  border: 1px solid var(--xy-border);
  position: relative;
  overflow: hidden;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  display: flex;
  flex-direction: column;
  gap: 12px;
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
  opacity: 0.25;
  z-index: 0;
  filter: brightness(1.2);
}

.stat-icon {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--primary-color);
  filter: drop-shadow(0 2px 4px rgba(0, 0, 0, 0.1));
}

.stat-icon svg {
  stroke-width: 2.5;
  filter: brightness(0.95) contrast(1.2);
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
  font-size: 28px;
  font-weight: 700;
  color: var(--text-primary);
  line-height: 1;
  letter-spacing: -0.5px;
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

.trend-reading {
  border-left: 3px solid #8b5cf6;
}

.trend-reading .stat-icon,
.trend-reading .stat-glow {
  background: linear-gradient(135deg, #8b5cf6, #a78bfa);
}

/* 每日单词背诵任务卡片 */
.daily-word-recite-card {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 16px;
  background: linear-gradient(135deg, var(--xy-brand-50), #f8fdf9);
  border: 1px solid var(--border-light);
  border-radius: var(--xy-radius-md);
  cursor: pointer;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}

.daily-word-recite-card:hover {
  transform: translateY(-2px);
  box-shadow: var(--shadow-md);
  border-color: var(--primary-color);
}

.recite-icon-wrapper {
  width: 56px;
  height: 56px;
  background: linear-gradient(135deg, var(--primary-color), var(--accent-color));
  border-radius: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.recite-icon {
  color: white;
}

.recite-content {
  flex: 1;
  min-width: 0;
}

.recite-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--text-primary);
  margin: 0 0 6px 0;
}

.recite-desc {
  font-size: 13px;
  color: var(--text-secondary);
  margin: 0 0 10px 0;
}

.recite-progress {
  display: flex;
  align-items: center;
  gap: 10px;
}

.progress-bar-mini {
  flex: 1;
  height: 6px;
  background: var(--bg-tertiary);
  border-radius: 3px;
  overflow: hidden;
}

.progress-fill-mini {
  height: 100%;
  background: linear-gradient(90deg, var(--primary-color), var(--accent-color));
  border-radius: 3px;
  transition: width 0.3s ease;
}

.progress-text-mini {
  font-size: 12px;
  color: var(--text-secondary);
  font-weight: 500;
  white-space: nowrap;
}

.recite-status {
  flex-shrink: 0;
}

.status-badge {
  display: inline-block;
  padding: 4px 12px;
  border-radius: 20px;
  font-size: 12px;
  font-weight: 500;
  background: rgba(255, 159, 10, 0.1);
  color: var(--warning-color);
}

.status-badge.completed {
  background: rgba(0, 200, 150, 0.1);
  color: var(--success-color);
}

/* 单词本卡片样式 */
.vocabulary-card {
  background: linear-gradient(135deg, rgba(255, 159, 10, 0.05) 0%, rgba(255, 127, 80, 0.05) 100%);
}

.vocabulary-card:hover {
  border-color: var(--warning-color);
}

.vocabulary-icon {
  background: linear-gradient(135deg, var(--warning-color), #FF7F50);
}

.vocabulary-progress {
  background: linear-gradient(90deg, var(--warning-color), #FF7F50);
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
  background: rgba(255, 255, 255, 0.8);
  backdrop-filter: blur(12px);
  border: 1px solid var(--border-light);
  border-radius: 16px;
  padding: 16px;
  cursor: pointer;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  position: relative;
  overflow: hidden;
  scroll-snap-align: start;
  box-shadow: 0 2px 8px rgba(0,0,0,0.04);
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
  background: var(--primary-color);
  opacity: 0.15;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--primary-color);
  position: absolute;
}
.course-icon-inner {
  color: var(--primary-color);
  z-index: 1;
  position: relative;
  display: flex;
}
.course-icon-svg {
  stroke-width: 2;
  width: 20px;
  height: 20px;
}

.course-icon svg {
  filter: drop-shadow(0 1px 2px rgba(0, 0, 0, 0.2));
  stroke-width: 2.5;
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

.assignment-urgency.overdue { color: var(--error-color); }
.assignment-urgency.urgent { color: #FF6B35; }
.assignment-urgency.soon { color: var(--warning-color); }
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

.status-tag.overdue {
  background: rgba(255, 59, 48, 0.1);
  color: var(--error-color);
}

.status-tag.urgent {
  background: rgba(255, 107, 53, 0.1);
  color: #FF6B35;
}

.status-tag.soon {
  background: rgba(255, 159, 10, 0.1);
  color: var(--warning-color);
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
  width: 44px;
  height: 44px;
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
  background: rgba(23, 110, 81, 0.1);
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
  line-clamp: 3;
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

/* 成就展示区域 */
.achievement-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 12px;
}

.achievement-summary {
  font-size: 13px;
  color: var(--text-tertiary);
  font-weight: 500;
}

.achievement-card {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 14px;
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.7);
  backdrop-filter: blur(8px);
  border: 1px solid var(--border-light);
  transition: all 0.3s ease;
}

.achievement-card.unlocked {
  border-color: #ffc800;
  background: linear-gradient(135deg, rgba(255, 200, 0, 0.08), rgba(255, 255, 255, 0.9));
  box-shadow: 0 2px 12px rgba(255, 200, 0, 0.15);
}

.achievement-card.locked {
  opacity: 0.5;
  filter: grayscale(1);
}

.achievement-card:hover {
  transform: translateY(-2px);
  box-shadow: var(--shadow-md);
}

.ach-icon {
  font-size: 28px;
  flex-shrink: 0;
  width: 40px;
  height: 40px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.ach-info {
  flex: 1;
  min-width: 0;
}

.ach-info .ach-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-primary);
  margin: 0 0 2px 0;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.ach-info .ach-desc {
  font-size: 11px;
  color: var(--text-tertiary);
  margin: 0;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.ach-status {
  flex-shrink: 0;
}

.ach-badge {
  font-size: 11px;
  padding: 2px 8px;
  border-radius: 10px;
  font-weight: 500;
}

.unlocked-badge {
  background: linear-gradient(135deg, #ffc800, #ff8c00);
  color: #fff;
}

.locked-badge {
  background: var(--bg-tertiary);
  color: var(--text-quaternary);
}

.section-icon {
  font-size: 18px;
  margin-right: 4px;
}

@media (max-width: 640px) {
  .achievement-grid {
    grid-template-columns: 1fr;
  }
}
</style>
