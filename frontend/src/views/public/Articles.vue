<template>
  <div class="articles-mobile-page">

    <!-- Sticky Header -->
    <header class="mobile-header" v-if="!isLoggedIn">
      <div class="page-content-wrapper header-inner">
        <div class="header-content">
          <h1 class="header-title">发现文章</h1>
          <p class="header-subtitle">Daily Picks</p>
        </div>
        <div class="header-actions">
          <button class="icon-btn" @click="handleLogin">
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/>
              <circle cx="12" cy="7" r="4"/>
            </svg>
          </button>
        </div>
      </div>
    </header>

    <!-- Main Content Area -->
    <main class="page-main">
      <div class="page-content-wrapper">
        <!-- Filter Pills -->
        <div class="filter-bar">
          <div class="filter-scroll">
            <button
              v-for="cat in categories"
              :key="cat.value"
              class="filter-pill"
              :class="{ 'active': selectedCategory === cat.value }"
              @click="selectCategory(cat.value)"
            >
              {{ cat.label }}
            </button>
          </div>
        </div>

        <!-- Articles Feed -->
        <div class="articles-feed">
        <!-- Skeleton Loading State -->
        <template v-if="loading">
          <div v-for="n in 3" :key="'skel-' + n" class="article-card skeleton-card">
            <div class="skeleton-img shimmer"></div>
            <div class="skeleton-body">
              <div class="skeleton-title shimmer"></div>
              <div class="skeleton-title short shimmer"></div>
              <div class="skeleton-text shimmer mt"></div>
              <div class="skeleton-text short shimmer"></div>
              <div class="skeleton-footer">
                <div class="skeleton-badge shimmer"></div>
                <div class="skeleton-time shimmer"></div>
              </div>
            </div>
          </div>
        </template>

        <!-- Empty / Error State -->
        <div v-else-if="articles.length === 0" class="empty-state">
          <div class="empty-icon-wrap">
            <svg width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
              <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/>
              <polyline points="14,2 14,8 20,8"/>
              <line x1="16" y1="13" x2="8" y2="13"/>
              <line x1="16" y1="17" x2="8" y2="17"/>
            </svg>
          </div>
          <h3 class="empty-title">{{ loadError ? '加载失败' : '暂无文章内容' }}</h3>
          <p class="empty-desc">{{ loadError ? '网络异常，请检查连接后重试' : '换个分类试试，或者稍后再来看看' }}</p>
          <button class="refresh-btn" @click="loadArticles">重新加载</button>
        </div>

        <!-- Article Cards -->
        <template v-else>
          <article
            v-for="article in articles"
            :key="article.id"
            class="article-card"
            @click="readArticle(article)"
          >
            <!-- Cover -->
            <div class="article-cover">
              <div class="cover-gradient"></div>
              <div
                class="cover-image"
                :style="{
                  background: article.coverUrl ? `url(${article.coverUrl})` : article.coverGradient,
                  backgroundSize: 'cover',
                  backgroundPosition: 'center'
                }"
              ></div>
            </div>

            <!-- Content Area -->
            <div class="article-content">
              <h2 class="article-title">{{ article.title }}</h2>
              <p class="article-snippet">{{ getSnippet(article.content) }}</p>
              <p v-if="article.author" class="card-author">
                {{ article.author.displayName }}
                <span v-if="article.author.roleLabel && !article.author.official" class="card-author-role">{{ article.author.roleLabel }}</span>
              </p>
            </div>

            <!-- Footer Area -->
            <div class="article-footer">
              <div class="category-pill">
                <span>{{ getCategoryText(article.category) }}</span>
              </div>
              <div class="reading-time">
                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <circle cx="12" cy="12" r="10"/>
                  <polyline points="12,6 12,12 16,14"/>
                </svg>
                <span>{{ article.readingTime }} min</span>
              </div>
            </div>
          </article>
        </template>
      </div>

        <!-- Pagination -->
        <div class="pagination" :style="{ visibility: totalPages > 1 ? 'visible' : 'hidden' }">
          <button class="page-btn" :disabled="currentPage === 1" @click="prevPage">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <polyline points="15,18 9,12 15,6"/>
            </svg>
          </button>
          <button
            v-for="page in pageList"
            :key="page"
            class="page-btn page-number"
            :class="{ active: currentPage === page }"
            @click="goToPage(page)"
          >
            {{ page }}
          </button>
          <button class="page-btn" :disabled="currentPage === totalPages" @click="nextPage">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <polyline points="9,18 15,12 9,6"/>
            </svg>
          </button>
          <div class="page-jump">
            <input
              v-model="jumpInput"
              type="number"
              min="1"
              :max="totalPages"
              class="page-jump-input"
              @keyup.enter="jumpToPage"
              placeholder="页"
            />
            <button class="page-btn page-jump-btn" @click="jumpToPage">跳转</button>
          </div>
        </div>

        <!-- Bottom Safe Area Spacer -->
        <div class="bottom-spacer"></div>
      </div>
    </main>

    <!-- 底部导航栏 -->
    <nav class="bottom-nav" v-if="!isLoggedIn">
      <div class="nav-wrapper">
        <router-link
          to="/articles"
          class="nav-item"
          :class="{ 'nav-item--active': $route.path === '/articles' }"
        >
          <svg class="nav-icon" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/>
            <polyline points="14,2 14,8 20,8"/>
            <line x1="16" y1="13" x2="8" y2="13"/>
            <line x1="16" y1="17" x2="8" y2="17"/>
          </svg>
          <span class="nav-label">文章</span>
        </router-link>

        <div class="nav-item" @click="handleLogin">
          <svg class="nav-icon" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/>
            <circle cx="12" cy="7" r="4"/>
          </svg>
          <span class="nav-label">登录</span>
        </div>
      </div>
    </nav>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getAuthHeader } from '@/utils/auth'
import { useUserStore } from '@/store/user'

const router = useRouter()
const userStore = useUserStore()
// 登录用户由外层 Layout (ArticlesHost) 提供顶/底栏，隐藏组件自带的游客 chrome
const isLoggedIn = computed(() => !!userStore.token)
const loading = ref(true)
const allArticles = ref([])
const selectedCategory = ref('')
const loadError = ref(false)
const currentPage = ref(1)
const pageSize = 5


const totalPages = computed(() => Math.ceil(allArticles.value.length / pageSize))
const PAGE_WINDOW = 5
const pageList = computed(() => {
  const total = totalPages.value
  if (total <= PAGE_WINDOW) return Array.from({ length: total }, (_, i) => i + 1)
  let start = currentPage.value - Math.floor(PAGE_WINDOW / 2)
  start = Math.max(1, Math.min(start, total - PAGE_WINDOW + 1))
  return Array.from({ length: PAGE_WINDOW }, (_, i) => start + i)
})
const jumpInput = ref('')
const jumpToPage = () => {
  const n = parseInt(jumpInput.value, 10)
  if (!Number.isFinite(n)) { jumpInput.value = ''; return }
  goToPage(Math.max(1, Math.min(n, totalPages.value)))
  jumpInput.value = ''
}
const articles = computed(() => {
  const start = (currentPage.value - 1) * pageSize
  return allArticles.value.slice(start, start + pageSize)
})

const categories = [
  { label: '全部', value: '' },
  { label: '科技', value: 'technology' },
  { label: '商务', value: 'business' },
  { label: '教育', value: 'education' },
  { label: '生活', value: 'lifestyle' },
  { label: '文化', value: 'culture' }
]

const coverGradients = [
  'linear-gradient(135deg, #667eea 0%, #764ba2 100%)',
  'linear-gradient(135deg, #f093fb 0%, #f5576c 100%)',
  'linear-gradient(135deg, #4facfe 0%, #00f2fe 100%)',
  'linear-gradient(135deg, #43e97b 0%, #38f9d7 100%)',
  'linear-gradient(135deg, #fa709a 0%, #fee140 100%)',
  'linear-gradient(135deg, #a18cd1 0%, #fbc2eb 100%)',
  'linear-gradient(135deg, #fccb90 0%, #d57eeb 100%)',
  'linear-gradient(135deg, #e0c3fc 0%, #8ec5fc 100%)',
  'linear-gradient(135deg, #f5576c 0%, #ff9a9e 100%)',
  'linear-gradient(135deg, #13547a 0%, #80d0c7 100%)'
]

const getRandomGradient = () => {
  return coverGradients[Math.floor(Math.random() * coverGradients.length)]
}

const selectCategory = (val) => {
  selectedCategory.value = val
  currentPage.value = 1
  loadArticles()
}

const goToPage = (page) => {
  if (page < 1 || page > totalPages.value) return
  currentPage.value = page
  window.scrollTo({ top: 0, behavior: 'smooth' })
}

const prevPage = () => goToPage(currentPage.value - 1)
const nextPage = () => goToPage(currentPage.value + 1)

const getRandomCategory = () => {
  const cats = categories.filter(c => c.value !== '')
  return cats[Math.floor(Math.random() * cats.length)].value
}

const loadArticles = async () => {
  loading.value = true
  loadError.value = false
  try {
    const authHeader = getAuthHeader()
    const headers = authHeader ? { Authorization: authHeader } : {}
    const response = await fetch(`${import.meta.env.VITE_API_BASE_URL || ''}/api/public/texts/featured`, { headers })

    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`)
    }

    const data = await response.json()

    if (!data || typeof data !== 'object') {
      loadError.value = true
      return
    }

    if (data.code === 200) {
      const dataArray = Array.isArray(data.data) ? data.data : (Array.isArray(data.data?.records) ? data.data.records : [])
      let fetched = dataArray.map(text => {
        const contentLength = typeof text?.content === 'string' ? text.content.length : 0
        return {
          ...text,
          category: getRandomCategory(),
          coverGradient: getRandomGradient(),
          readingTime: Math.max(1, Math.ceil(contentLength / 800))
        }
      })

      if (selectedCategory.value) {
        fetched = fetched.filter(item => item.category === selectedCategory.value)
      }

      allArticles.value = fetched
    } else {
      loadError.value = true
    }
  } catch (error) {
    console.error('加载文章失败:', error)
    loadError.value = true
  } finally {
    loading.value = false
  }
}

const readArticle = (article) => {
  if (!article?.id) {
    ElMessage.warning('文章数据异常，无法打开')
    return
  }
  // 记录阅读，但不阻塞导航，同时正确处理错误
  const baseUrl = import.meta.env.VITE_API_BASE_URL || ''
  const authHeader = getAuthHeader()
  const headers = authHeader ? { Authorization: authHeader } : {}
  fetch(`${baseUrl}/api/public/texts/${article.id}/read`, { method: 'POST', headers })
    .then(response => {
      // 仅检查网络错误，不处理HTTP错误状态（后端已返回成功）
      if (!response.ok) {
        console.warn(`标记已读API返回 ${response.status}`)
      }
    })
    .catch(error => console.error('记录文章阅读失败:', error))
  router.push({
    path: `/article/${article.id}`,
    query: { from: 'articles' }
  })
}

const getSnippet = (content) => {
  if (typeof content !== 'string' || !content) return ''
  return content.replace(/[\n\r]+/g, ' ').substring(0, 120) + (content.length > 120 ? '...' : '')
}

const getCategoryText = (val) => {
  const cat = categories.find(c => c.value === val)
  return cat ? cat.label : '综合'
}

const handleLogin = () => {
  router.push('/login')
}

onMounted(() => {
  loadArticles()
})
</script>

<style scoped>
:root {
  --app-bg: #f6f7f8;
  --app-surface: #ffffff;
  --app-primary: #137fec;
  --app-text: #0f172a;
  --app-text-muted: #64748b;
  --app-border: #e2e8f0;
}

.articles-mobile-page {
  height: 100vh;
  height: 100dvh;
  overflow: hidden;
  background-color: var(--app-bg);
  font-family: 'Inter', -apple-system, BlinkMacSystemFont, sans-serif;
  color: var(--app-text);
  position: relative;
  display: flex;
  flex-direction: column;
}

/* PC layout */
@media (min-width: 768px) {
  .articles-mobile-page {
    max-width: 100%;
    height: 100vh;
  }

  .page-content-wrapper {
    max-width: 960px;
    margin: 0 auto;
    padding: 0 24px;
  }

  .articles-feed {
    display: grid;
    grid-template-columns: repeat(2, 1fr);
    gap: 24px;
    padding: 0;
  }

  .pagination {
    display: none;
  }

  .bottom-nav {
    border-radius: 0;
  }

  .bottom-spacer {
    display: block;
    height: 80px;
  }

  .bottom-spacer {
    display: none;
  }

  .page-main {
    padding-bottom: 100px
  }

  .article-card {
    min-height: 280px;
  }

  .article-cover {
    height: 140px;
  }

  .article-content {
    flex: 1;
    padding: 12px 16px 10px;
  }
}

@media (min-width: 1200px) {
  .articles-feed {
    grid-template-columns: repeat(3, 1fr);
  }
}


/* --- Header --- */
.mobile-header {
  position: sticky;
  top: 0;
  z-index: 50;
  background: rgba(255, 255, 255, 0.85);
  backdrop-filter: blur(16px);
  -webkit-backdrop-filter: blur(16px);
  border-bottom: 1px solid rgba(226, 232, 240, 0.6);
  padding: 12px 20px;
  padding-top: max(env(safe-area-inset-top, 12px), 12px);
}

.header-inner {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.header-content {
  flex: 1;
}

.header-title {
  margin: 0;
  font-size: 18px;
  font-weight: 700;
  color: #0f172a;
}

.header-subtitle {
  margin: 2px 0 0;
  font-size: 12px;
  color: #64748b;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.icon-btn {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  border: none;
  background: transparent;
  color: #475569;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: background-color 0.2s;
}

.icon-btn:hover {
  background: #f1f5f9;
  color: #0f172a;
}

/* --- Filter Bar --- */
.filter-bar {
  position: sticky;
  top: max(env(safe-area-inset-top, 0px), 0px);
  z-index: 40;
  background: rgba(246, 247, 248, 0.95);
  backdrop-filter: blur(8px);
  -webkit-backdrop-filter: blur(8px);
  border-bottom: 1px solid rgba(226, 232, 240, 0.6);
  padding: 12px 0;
}

.filter-scroll {
  display: flex;
  gap: 10px;
  overflow-x: auto;
  padding: 0 20px;
  scrollbar-width: none;
  -webkit-overflow-scrolling: touch;
}
.filter-scroll::-webkit-scrollbar { display: none; }

.filter-pill {
  flex-shrink: 0;
  padding: 8px 20px;
  border-radius: 999px;
  border: 1px solid #e2e8f0;
  background: white;
  color: #475569;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s;
}

.filter-pill:active { transform: scale(0.96); }

.filter-pill.active {
  background: #1d4ed8;
  color: white;
  border-color: #1d4ed8;
  border-radius: 50px;
  box-shadow: 0 4px 16px rgba(29, 78, 216, 0.45);
}

/* --- Feed --- */
.page-main {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  -webkit-overflow-scrolling: touch;
  scrollbar-gutter: stable;
}

.articles-feed {
  padding: 16px 20px;
  display: flex;
  flex-direction: column;
  gap: 20px;
}

/* --- Article Card --- */
.article-card {
  background: white;
  border-radius: 20px;
  overflow: hidden;
  box-shadow: 0 4px 20px -2px rgba(0,0,0,0.05);
  transition: transform 0.2s, box-shadow 0.2s;
  cursor: pointer;
  display: flex;
  flex-direction: column;
  border: 1px solid #f1f5f9;
}

@media (hover: hover) {
  .article-card:hover {
    transform: translateY(-4px);
    box-shadow: 0 12px 30px -4px rgba(0,0,0,0.08);
  }
}

.article-card:active {
  transform: scale(0.98);
}

/* Cover */
.article-cover {
  position: relative;
  width: 100%;
  height: 170px;
  overflow: hidden;
}

.cover-image {
  width: 100%;
  height: 100%;
  background-size: cover;
  background-position: center;
  transition: transform 0.7s ease;
}

.article-card:hover .cover-image {
  transform: scale(1.05);
}

.cover-gradient {
  position: absolute;
  inset: 0;
  background: linear-gradient(to top, rgba(0,0,0,0.5) 0%, transparent 60%);
  z-index: 1;
}

.difficulty-badge-cover {
  position: absolute;
  bottom: 12px;
  left: 12px;
  z-index: 2;
  background: rgba(255, 255, 255, 0.2);
  backdrop-filter: blur(8px);
  -webkit-backdrop-filter: blur(8px);
  border: 1px solid rgba(255,255,255,0.2);
  color: white;
  padding: 4px 10px;
  border-radius: 8px;
  font-size: 11px;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.05em;
}

/* Content */
.article-content {
  padding: 16px 16px 12px;
}

.article-title {
  margin: 0 0 8px 0;
  font-size: 18px;
  font-weight: 700;
  line-height: 1.3;
  color: #0f172a;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  word-break: break-word;
  overflow-wrap: break-word;
  /* Always occupy 2 lines of space for consistent card height */
  min-height: calc(2 * 1.3em);
}

.article-snippet {
  margin: 0;
  font-size: 14px;
  line-height: 1.5;
  color: var(--app-text-muted);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  word-break: break-word;
  overflow-wrap: break-word;
  /* Always occupy 2 lines of space for consistent card height */
  min-height: calc(2 * 1.5em);
}

.card-author {
  margin: 8px 0 0;
  font-size: 12px;
  color: var(--app-text-muted);
  display: flex;
  align-items: center;
  gap: 6px;
}

.card-author-role {
  padding: 0 6px;
  border-radius: 8px;
  font-size: 11px;
  color: #2563eb;
  background: #eff6ff;
}

/* Footer */
.article-footer {
  padding: 0 16px 16px;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.category-pill {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 4px 10px;
  border-radius: 6px;
  font-size: 11px;
  font-weight: 600;
  background: rgba(19, 127, 236, 0.1);
  color: #137fec;
}

.reading-time {
  display: flex;
  align-items: center;
  gap: 4px;
  color: #94a3b8;
  font-size: 12px;
  font-weight: 500;
}

/* --- Empty state & Loading --- */
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 60px 20px;
  text-align: center;
}

.empty-icon-wrap {
  width: 80px;
  height: 80px;
  background: white;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #cbd5e1;
  box-shadow: 0 4px 20px rgba(0,0,0,0.04);
  margin-bottom: 20px;
}

.empty-title { margin: 0 0 8px; font-size: 18px; color: #0f172a; }
.empty-desc { margin: 0 0 24px; font-size: 14px; color: #64748b; }

.refresh-btn {
  background: white;
  border: 1px solid #e2e8f0;
  color: #0f172a;
  padding: 10px 24px;
  border-radius: 999px;
  font-weight: 600;
  font-size: 14px;
  cursor: pointer;
  box-shadow: 0 2px 8px rgba(0,0,0,0.02);
  transition: all 0.2s;
}
.refresh-btn:hover { border-color: #cbd5e1; background: #f8fafc; }

/* Skeleton Shimmer */
.skeleton-card { cursor: default; }
.skeleton-card:hover { transform: none; box-shadow: 0 4px 20px -2px rgba(0,0,0,0.05); }

.skeleton-img { width: 100%; height: 170px; }
.skeleton-body { padding: 16px; }
.skeleton-title { height: 20px; width: 85%; border-radius: 6px; margin-bottom: 8px; }
.skeleton-title.short { width: 60%; margin-bottom: 16px; }
.skeleton-text { height: 14px; width: 100%; border-radius: 4px; margin-bottom: 6px; }
.skeleton-text.short { width: 80%; }
.mt { margin-top: 12px; }

.skeleton-footer {
  display: flex;
  justify-content: space-between;
  margin-top: 16px;
  padding-top: 16px;
  border-top: 1px solid #f1f5f9;
}
.skeleton-badge { height: 24px; width: 80px; border-radius: 6px; }
.skeleton-time { height: 20px; width: 60px; border-radius: 4px; }

.shimmer {
  background: #f1f5f9;
  background-image: linear-gradient(90deg, #f1f5f9 0px, #f8fafc 40px, #f1f5f9 80px);
  background-size: 600px;
  animation: shimmer 1.5s infinite linear;
}

@keyframes shimmer {
  0% { background-position: -200px 0; }
  100% { background-position: 400px 0; }
}

/* --- Pagination --- */
.pagination {
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 6px;
  padding: 16px 0;
  flex-wrap: wrap;
}

.page-btn {
  min-width: 36px;
  height: 36px;
  padding: 0 10px;
  border-radius: 50px;
  border: 1px solid #e2e8f0;
  background: white;
  color: #475569;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.2s;
}

.page-btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

.page-btn:not(:disabled):hover {
  border-color: #1d4ed8;
  color: #1d4ed8;
}

.page-btn.active {
  background: #1d4ed8;
  border-color: #1d4ed8;
  color: white;
  box-shadow: 0 2px 8px rgba(29, 78, 216, 0.35);
}

.page-btn.ellipsis {
  border-color: transparent;
  background: transparent;
  cursor: default;
  opacity: 1;
  color: #94a3b8;
}

.page-jump {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-left: 8px;
}

.page-jump-input {
  width: 56px;
  height: 32px;
  padding: 0 8px;
  border: 1px solid #e2e8f0;
  border-radius: 6px;
  font-size: 13px;
  text-align: center;
  outline: none;
  transition: border-color 0.2s;
}

.page-jump-input:focus {
  border-color: #1d4ed8;
}

.page-jump-input::-webkit-inner-spin-button,
.page-jump-input::-webkit-outer-spin-button {
  -webkit-appearance: none;
  margin: 0;
}

.page-jump-btn {
  padding: 0 10px;
  font-size: 13px;
}

.page-number {
  font-weight: 600;
}

.bottom-spacer {
  height: calc(80px + env(safe-area-inset-bottom, 0px));
}

/* --- Bottom Nav --- */
.bottom-nav {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  background: rgba(255, 255, 255, 0.9);
  backdrop-filter: blur(20px);
  -webkit-backdrop-filter: blur(20px);
  border-top: 1px solid rgba(226, 232, 240, 0.6);
  padding-bottom: env(safe-area-inset-bottom, 0);
  z-index: 100;
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
  padding: 6px 16px;
  color: #94a3b8;
  text-decoration: none;
  transition: all 0.2s;
  cursor: pointer;
  border-radius: 12px;
}

.nav-item:hover {
  color: #475569;
}

.nav-item--active {
  color: var(--app-primary);
}

.nav-icon {
  width: 20px;
  height: 20px;
}

.nav-item--active .nav-icon {
  filter: drop-shadow(0 2px 4px rgba(19, 127, 236, 0.3));
}

.nav-label {
  font-size: 10px;
  font-weight: 600;
}

/* Touch optimization */
.article-card,
.filter-pill,
.nav-item,
.article-card,
.page-btn {
  -webkit-tap-highlight-color: transparent;
  touch-action: manipulation;
}
</style>
