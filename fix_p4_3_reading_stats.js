// P4-3: 学生端个人页面添加阅读次数汇总
// 文件: frontend/src/views/student/Dashboard.vue

// ========== 1. 在 script setup 中添加以下代码 ==========

// 导入API（在现有导入后添加）
import { getReadingHistory } from '@/api/student'

// 添加阅读统计响应式数据（在现有 reactive 定义后添加）
const readingStats = reactive({
  totalCount: 0,      // 总阅读次数
  weekCount: 0,       // 本周阅读次数
  monthCount: 0,      // 本月阅读次数
  totalArticles: 0    // 总阅读篇数
})

// 添加加载阅读统计的方法（在现有方法后添加）
const loadReadingStats = async () => {
  try {
    // 获取阅读历史概览（包含总阅读次数）
    const response = await getReadingHistory({ page: 1, size: 1 })
    if (response?.code === 200 && response?.data?.overview) {
      const overview = response.data.overview
      readingStats.totalCount = overview.totalReadingTimes || 0
      readingStats.totalArticles = overview.totalArticlesRead || 0
      
      // 注意：本周和本月统计需要后端支持，当前API可能不返回
      // 如果需要，可以调用 /api/student/stats/overview?period=week/month
    }
  } catch (error) {
    console.error('加载阅读统计失败:', error)
    // 失败时使用默认值，不影响页面其他功能
  }
}

// 在 onMounted 中调用（在现有调用后添加）
onMounted(() => {
  loadDashboardData()
  loadLearningStats()
  loadDailyWordReciteProgress()
  loadVocabularyStats()
  loadReadingStats()  // 新增
  // ... 其他代码
})

// ========== 2. 在 template 的 stats-grid 中添加阅读统计卡片 ==========

// 在现有的 stat-card 后添加（例如在 trend-studytime 后）：

/*
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
*/

// ========== 3. 在 style scoped 中添加样式 ==========

/*
.trend-reading {
  border-left: 3px solid #8b5cf6;
}

.trend-reading .stat-icon,
.trend-reading .stat-glow {
  background: linear-gradient(135deg, #8b5cf6, #a78bfa);
}
*/

// ========== 4. 如果需要显示更详细的统计（可选） ==========

// 可以在 "今日统计" 卡片中添加阅读相关数据：

/*
<div class="stat-row">
  <span class="stat-label">阅读篇数</span>
  <span class="stat-value">{{ readingStats.totalArticles }}篇</span>
</div>
<div class="stat-row">
  <span class="stat-label">阅读次数</span>
  <span class="stat-value highlight">{{ readingStats.totalCount }}次</span>
</div>
*/

