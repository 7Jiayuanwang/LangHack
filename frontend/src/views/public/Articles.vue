<template>
  <div class="trend-articles">
  
      <div class="filter-section">
        <div class="trend-select-wrapper">
          <select
            v-model="selectedDifficulty"
            @change="loadArticles"
            class="trend-select"
          >
            <option value="">全部难度</option>
            <option value="EASY">简单</option>
            <option value="MEDIUM">中等</option>
            <option value="HARD">困难</option>
          </select>
        </div>

        <div class="trend-select-wrapper">
          <select
            v-model="selectedCategory"
            @change="loadArticles"
            class="trend-select"
          >
            <option value="">全部分类</option>
            <option value="technology">科技</option>
            <option value="business">商务</option>
            <option value="education">教育</option>
            <option value="lifestyle">生活</option>
            <option value="culture">文化</option>
          </select>
        </div>

        <button class="trend-btn secondary" @click="loadArticles" :disabled="loading">
          <svg v-if="!loading" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <polyline points="23,4 23,10 17,10"/>
            <path d="M20.49 15a9 9 0 1 1-2.12-9.36L23 10"/>
          </svg>
          <svg v-else class="spinning" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M21 12a9 9 0 1 1-6.219-8.56"/>
          </svg>
          <span>{{ loading ? '加载中...' : '刷新' }}</span>
        </button>
      </div>

    <!-- 加载状态 -->
    <div v-if="loading" class="loading-section">
      <div class="skeleton-grid">
        <div v-for="n in 6" :key="n" class="skeleton-card">
          <div class="skeleton-header"></div>
          <div class="skeleton-content"></div>
          <div class="skeleton-meta"></div>
        </div>
      </div>
    </div>

    <!-- 空状态 -->
    <div v-else-if="articles.length === 0" class="empty-section">
      <div class="empty-icon">
        <svg width="64" height="64" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
          <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/>
          <polyline points="14,2 14,8 20,8"/>
          <line x1="16" y1="13" x2="8" y2="13"/>
          <line x1="16" y1="17" x2="8" y2="17"/>
          <polyline points="10,9 9,9 8,9"/>
        </svg>
      </div>
      <h3 class="empty-title">暂无文章内容</h3>
      <p class="empty-description">系统正在准备精彩的学习文章</p>
      <button class="trend-btn primary" @click="loadArticles">
        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <polyline points="23,4 23,10 17,10"/>
          <path d="M20.49 15a9 9 0 1 1-2.12-9.36L23 10"/>
        </svg>
        <span>重新加载</span>
      </button>
    </div>

    <!-- 文章列表 -->
    <div v-else class="articles-section">
      <div class="trend-articles-grid">
        <div
          v-for="article in articles"
          :key="article.id"
          class="trend-article-card"
          @click="readArticle(article)"
        >
          <div class="article-gradient"></div>

          <div class="article-body">
            <h3 class="article-title">{{ article.title }}</h3>
            <p class="article-content">{{ getLongContentPreview(article.content) }}</p>
          </div>

          <div class="article-meta">
            <div class="meta-left">
              <div class="difficulty-badge" :class="article.difficulty">
                <span class="difficulty-dot"></span>
                <span>{{ getDifficultyText(article.difficulty) }}</span>
              </div>
              <span v-if="article.category" class="category-tag">{{ getCategoryText(article.category) }}</span>
            </div>
            <div class="meta-right">
              <div class="reading-time">
                <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <circle cx="12" cy="12" r="10"/>
                  <polyline points="12,6 12,12 16,14"/>
                </svg>
                <span>{{ getReadingTime(article.content) }}分钟</span>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 底部导航栏 -->
    <nav class="trend-nav">
      <div class="nav-wrapper">
<!--        <router-link-->
<!--          to="/"-->
<!--          class="nav-item"-->
<!--          :class="{ 'nav-item&#45;&#45;active': $route.path === '/' }"-->
<!--        >-->
<!--          <div class="nav-icon-wrapper">-->
<!--            <svg class="nav-icon" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">-->
<!--              <path d="M3 9l9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z"/>-->
<!--              <polyline points="9,22 9,12 15,12 15,22"/>-->
<!--            </svg>-->
<!--            <div class="nav-icon-ripple"></div>-->
<!--          </div>-->
<!--          <span class="nav-label">首页</span>-->
<!--        </router-link>-->

        <router-link
          to="/articles"
          class="nav-item"
          :class="{ 'nav-item--active': $route.path === '/articles' }"
        >
          <div class="nav-icon-wrapper">
            <svg class="nav-icon" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/>
              <polyline points="14,2 14,8 20,8"/>
              <line x1="16" y1="13" x2="8" y2="13"/>
              <line x1="16" y1="17" x2="8" y2="17"/>
            </svg>
            <div class="nav-icon-ripple"></div>
          </div>
          <span class="nav-label">文章</span>
        </router-link>

        <div
          class="nav-item"
          @click="handleLogin"
        >
          <div class="nav-icon-wrapper">
            <svg class="nav-icon" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/>
              <circle cx="12" cy="7" r="4"/>
            </svg>
            <div class="nav-icon-ripple"></div>
          </div>
          <span class="nav-label">登录</span>
        </div>
      </div>
    </nav>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'

const router = useRouter()
const route = useRoute()

const loading = ref(false)
const articles = ref([])
const selectedDifficulty = ref('')
const selectedCategory = ref('')

const loadArticles = async () => {
  loading.value = true
  try {
    // 获取随机课文作为文章内容
    const params = {
      count: 12 // 获取更多文章
    }

    if (selectedDifficulty.value) {
      params.difficulty = selectedDifficulty.value
    }

    // 使用公开API获取课文
    const response = await fetch(`${import.meta.env.VITE_API_BASE_URL || ''}/api/public/texts/random?${new URLSearchParams(params)}`)
    const data = await response.json()

    if (response.ok && data.code === 200) {
      // 添加一些额外的文章属性来模拟分类等功能
      articles.value = (Array.isArray(data.data) ? data.data : []).map(text => ({
        ...text,
        category: getRandomCategory(),
        readingTime: Math.ceil((text.content || '').length / 1000), // 估算阅读时间
      }))
    } else {
      ElMessage.error(data.message || '获取文章失败')
    }
  } catch (error) {
    ElMessage.error('获取文章失败')
    console.error(error)
  } finally {
    loading.value = false
  }
}

const readArticle = (article) => {
  if (!article?.id) {
    ElMessage.warning('文章数据异常，无法打开')
    return
  }
  router.push({
    path: `/article/${article.id}`,
    query: {
      from: 'articles'
    }
  })
}


const getContentPreview = (content) => {
  if (!content) return ''
  return content.length > 150 ? content.substring(0, 150) + '...' : content
}

const getLongContentPreview = (content) => {
  if (!content) return ''
  return content.length > 300 ? content.substring(0, 300) + '...' : content
}

const getReadingTime = (content) => {
  if (!content) return 1
  // 假设阅读速度为每分钟1000字符
  return Math.ceil(content.length / 1000)
}

const getDifficultyText = (difficulty) => {
  const texts = {
    'EASY': '简单',
    'MEDIUM': '中等',
    'HARD': '困难'
  }
  return texts[difficulty] || difficulty
}

const getCategoryText = (category) => {
  const categoryMap = {
    'technology': '科技',
    'business': '商务',
    'education': '教育',
    'lifestyle': '生活',
    'culture': '文化'
  }
  return categoryMap[category] || category
}

const getRandomCategory = () => {
  const categories = ['technology', 'business', 'education', 'lifestyle', 'culture']
  return categories[Math.floor(Math.random() * categories.length)]
}

const formatDate = (dateString) => {
  if (!dateString) return ''
  return new Date(dateString).toLocaleDateString('zh-CN', {
    month: '2-digit',
    day: '2-digit'
  })
}

const handleLogin = () => {
  router.push('/login')
}

onMounted(() => {
  loadArticles()
})
</script>

<style scoped>
/* 文章页面样式 - 基于用户首页样式 */
.trend-articles {
  background: var(--bg-secondary);
  padding: 8px;
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif;
  overflow-x: hidden;
  overflow-y: auto;
  height: 100vh;
  -webkit-overflow-scrolling: touch;
}

/* 复用用户首页的样式，调整文章特定的部分 */
.trend-header-section {
  background: var(--bg-primary);
  border-radius: 20px;
  padding: 24px;
  margin-bottom: 20px;
  box-shadow: var(--shadow-sm);
  border: 1px solid var(--border-light);
}

.header-content {
  text-align: center;
  margin-bottom: 20px;
}

.page-title {
  font-size: 24px;
  font-weight: 700;
  color: var(--text-primary);
  margin: 0 0 8px 0;
  background: var(--gradient-brand);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

.page-subtitle {
  font-size: 14px;
  color: var(--text-tertiary);
  margin: 0;
  font-weight: 500;
}

.filter-section {
  display: flex;
  gap: 12px;
  justify-content: center;
  align-items: center;
  flex-wrap: wrap;
  margin-bottom: 16px;
  margin-top: 0;
}

.trend-select-wrapper {
  position: relative;
}

.trend-select {
  background: var(--bg-primary);
  border: 1px solid var(--border-color);
  border-radius: 12px;
  padding: 8px 16px;
  font-size: 14px;
  color: var(--text-primary);
  outline: none;
  cursor: pointer;
  transition: all 0.3s ease;
  min-width: 120px;
  appearance: none;
  background-image: url("data:image/svg+xml,%3Csvg width='12' height='8' viewBox='0 0 12 8' fill='none' xmlns='http://www.w3.org/2000/svg'%3E%3Cpath d='M1 1.5L6 6.5L11 1.5' stroke='%236B7280' stroke-width='1.5' stroke-linecap='round' stroke-linejoin='round'/%3E%3C/svg%3E");
  background-repeat: no-repeat;
  background-position: right 16px center;
  padding-right: 40px;
}

.trend-select:focus {
  border-color: var(--primary-color);
  box-shadow: 0 0 0 3px rgba(0, 102, 204, 0.1);
}

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
  white-space: nowrap;
}

.trend-btn:hover:not(:disabled) {
  background: var(--bg-tertiary);
  border-color: var(--primary-color);
  color: var(--primary-color);
  transform: translateY(-1px);
}

.trend-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.trend-btn.primary {
  background: var(--gradient-brand);
  color: white;
  border: none;
}

.trend-btn.secondary {
  background: var(--bg-secondary);
  color: var(--text-secondary);
}

.spinning {
  animation: spin 1s linear infinite;
}

@keyframes spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

/* 骨架屏 */
.loading-section {
  margin-top: 8px;
}

.skeleton-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  gap: 16px;
}

.skeleton-card {
  background: var(--bg-primary);
  border-radius: 16px;
  padding: 20px;
  border: 1px solid var(--border-light);
}

.skeleton-header {
  height: 20px;
  background: var(--bg-tertiary);
  border-radius: 8px;
  margin-bottom: 12px;
  animation: skeleton-pulse 1.5s ease-in-out infinite alternate;
}

.skeleton-content {
  height: 80px;
  background: var(--bg-tertiary);
  border-radius: 8px;
  margin-bottom: 12px;
  animation: skeleton-pulse 1.5s ease-in-out infinite alternate;
  animation-delay: 0.2s;
}

.skeleton-meta {
  height: 16px;
  background: var(--bg-tertiary);
  border-radius: 8px;
  width: 40%;
  animation: skeleton-pulse 1.5s ease-in-out infinite alternate;
  animation-delay: 0.4s;
}

@keyframes skeleton-pulse {
  from { opacity: 0.6; }
  to { opacity: 1; }
}

/* 空状态 */
.empty-section {
  text-align: center;
  padding: 40px 20px;
  background: var(--bg-primary);
  border-radius: 20px;
  margin-top: 8px;
  border: 1px solid var(--border-light);
}

.empty-icon {
  margin-bottom: 20px;
  opacity: 0.3;
}

.empty-title {
  font-size: 18px;
  font-weight: 600;
  color: var(--text-primary);
  margin: 0 0 8px 0;
}

.empty-description {
  font-size: 14px;
  color: var(--text-tertiary);
  margin: 0 0 24px 0;
}

/* 文章网格 */
.articles-section {
  margin-top: 8px;
}

.trend-articles-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: 16px;
}

.trend-article-card {
  background: var(--bg-primary);
  border: 1px solid var(--border-light);
  border-radius: 16px;
  padding: 20px;
  cursor: pointer;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  position: relative;
  overflow: hidden;
}

.trend-article-card:hover {
  transform: translateY(-4px);
  box-shadow: var(--shadow-lg);
  border-color: var(--primary-color);
}

.article-gradient {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 3px;
  background: var(--gradient-hero);
}

.article-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.difficulty-badge {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 4px 10px;
  border-radius: 12px;
  font-size: 12px;
  font-weight: 500;
}

.difficulty-badge.EASY {
  background: rgba(0, 200, 150, 0.1);
  color: var(--success-color);
}

.difficulty-badge.MEDIUM {
  background: rgba(255, 159, 10, 0.1);
  color: var(--warning-color);
}

.difficulty-badge.HARD {
  background: rgba(255, 59, 48, 0.1);
  color: var(--error-color);
}

.difficulty-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: currentColor;
}

.reading-time {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: var(--text-quaternary);
}

.article-body {
  margin-bottom: 12px;
  text-align: left;
}

.article-title {
  font-size: 18px;
  font-weight: 600;
  color: var(--text-primary);
  margin: 0 0 12px 0;
  line-height: 1.3;
}

.article-content {
  font-size: 15px;
  color: var(--text-secondary);
  line-height: 1.6;
  margin: 0;
  display: -webkit-box;
  -webkit-line-clamp: 4;
  -webkit-box-orient: vertical;
  overflow: hidden;
  letter-spacing: 0.3px;
}

.article-meta {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-top: 10px;
  border-top: 1px solid var(--separator-color);
}

.meta-left {
  display: flex;
  align-items: center;
  gap: 8px;
}

.meta-right {
  display: flex;
  align-items: center;
}

.difficulty-badge {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 3px 8px;
  border-radius: 10px;
  font-size: 11px;
  font-weight: 500;
}

.difficulty-badge.EASY {
  background: rgba(0, 200, 150, 0.1);
  color: var(--success-color);
}

.difficulty-badge.MEDIUM {
  background: rgba(255, 159, 10, 0.1);
  color: var(--warning-color);
}

.difficulty-badge.HARD {
  background: rgba(255, 59, 48, 0.1);
  color: var(--error-color);
}

.difficulty-dot {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: currentColor;
}

.category-tag {
  background: rgba(0, 102, 204, 0.1);
  color: var(--primary-color);
  padding: 3px 8px;
  border-radius: 10px;
  font-size: 11px;
  font-weight: 500;
}

.reading-time {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 11px;
  color: var(--text-quaternary);
}

/* 响应式设计 */
@media (max-width: 768px) {
  .trend-articles {
    padding: 12px;
  }

  .trend-header-section {
    padding: 20px 16px;
  }

  .page-title {
    font-size: 20px;
  }

  .page-subtitle {
    font-size: 13px;
  }

  .filter-section {
    flex-direction: row;
    flex-wrap: wrap;
    gap: 8px;
    padding: 8px;
    background: var(--bg-primary);
    border-radius: 12px;
    margin-bottom: 12px;
  }

  .trend-select-wrapper {
    flex: 1;
    min-width: 90px;
  }

  .trend-select {
    width: 100%;
    min-width: unset;
    padding: 6px 28px 6px 10px;
    font-size: 13px;
    border-radius: 8px;
    background-position: right 8px center;
  }

  .trend-btn {
    padding: 6px 12px;
    font-size: 13px;
    border-radius: 8px;
  }

  .trend-btn span {
    display: none;
  }

  .trend-articles-grid {
    grid-template-columns: 1fr;
    gap: 12px;
  }

  .trend-article-card {
    padding: 16px;
  }

  .article-header {
    margin-bottom: 12px;
  }

  .article-title {
    font-size: 15px;
  }

  .article-content {
    font-size: 13px;
  }

  .article-stats {
    gap: 8px;
  }

  .skeleton-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 360px) {
  .trend-articles {
    padding: 8px;
  }

  .trend-header-section {
    padding: 16px 12px;
  }

  .filter-section {
    gap: 6px;
    padding: 6px;
  }

  .trend-select-wrapper {
    min-width: 80px;
  }

  .trend-select {
    padding: 5px 24px 5px 8px;
    font-size: 12px;
  }

  .trend-btn {
    padding: 5px 10px;
    font-size: 12px;
  }

  .trend-article-card {
    padding: 14px;
  }

  .page-title {
    font-size: 18px;
  }
}

/* 触摸优化 */
.trend-article-card,
.trend-btn,
.trend-select {
  -webkit-tap-highlight-color: transparent;
  touch-action: manipulation;
}

/* 底部导航栏样式 */
.trend-nav {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  background: var(--bg-primary);
  border-top: 1px solid var(--separator-color);
  padding-bottom: env(safe-area-inset-bottom, 0);
  z-index: 100;
  backdrop-filter: blur(20px);
  -webkit-backdrop-filter: blur(20px);
}

.nav-wrapper {
  display: flex;
  justify-content: space-around;
  align-items: center;
  padding: 8px 0 12px 0;
  max-width: 600px;
  margin: 0 auto;
}

.nav-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  padding: 6px 12px;
  color: var(--text-tertiary);
  text-decoration: none;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  position: relative;
  cursor: pointer;
  border-radius: 12px;
}

.nav-item:hover {
  transform: translateY(-1px);
}

.nav-item--active {
  color: var(--primary-color);
  background: rgba(0, 102, 204, 0.08);
}

.nav-icon-wrapper {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  margin-bottom: 4px;
}

.nav-icon {
  width: 20px;
  height: 20px;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  z-index: 2;
  position: relative;
}

.nav-item--active .nav-icon {
  transform: scale(1.2);
  filter: drop-shadow(0 2px 4px rgba(0, 102, 204, 0.3));
}

.nav-icon-ripple {
  position: absolute;
  top: 50%;
  left: 50%;
  width: 0;
  height: 0;
  border-radius: 50%;
  background: var(--primary-color);
  opacity: 0.2;
  transform: translate(-50%, -50%);
  transition: all 0.4s ease;
}

.nav-item--active .nav-icon-ripple {
  width: 32px;
  height: 32px;
  opacity: 0.1;
}

.nav-label {
  font-size: 10px;
  line-height: 1;
  text-align: center;
  font-weight: 600;
  transition: font-size 0.2s ease;
}

.nav-item--active .nav-label {
  font-size: 11px;
}

/* 为底部导航留出空间 */
.trend-articles {
  padding-bottom: 80px;
}

@media (max-width: 480px) {
  .nav-wrapper {
    padding: 6px 0 10px 0;
  }

  .nav-item {
    padding: 4px 6px;
  }

  .nav-icon-wrapper {
    width: 22px;
    height: 22px;
    margin-bottom: 3px;
  }

  .nav-icon {
    width: 18px;
    height: 18px;
  }

  .nav-label {
    font-size: 9px;
  }

  .nav-item--active .nav-label {
    font-size: 10px;
  }
}
</style>