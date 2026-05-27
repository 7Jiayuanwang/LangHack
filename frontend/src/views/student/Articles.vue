<template>
  <div class="articles-mobile-page">
    
    <!-- Sticky Header -->
    <header class="articles-toolbar">
      <div></div>
      <div class="header-actions">
        <span v-if="!filterVisible" class="current-filter-label">{{ currentCategoryLabel }}</span>
        <button class="icon-btn" @click="toggleFilter" :title="filterVisible ? '收起筛选' : '展开筛选'">
          <el-icon><component :is="filterVisible ? ArrowUp : ArrowDown" /></el-icon>
        </button>
        <button class="icon-btn" @click="toggleSearch">
          <el-icon><Search /></el-icon>
        </button>
        <button class="icon-btn notification-btn">
          <el-icon><Bell /></el-icon>
          <span class="notification-dot"></span>
        </button>
      </div>
    </header>

    <!-- Filter Pills -->
    <transition name="filter-collapse">
      <div v-show="filterVisible" class="filter-bar">
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
    </transition>

    <!-- Search Bar -->
    <transition name="filter-collapse">
      <div v-show="searchVisible" class="search-bar">
        <el-input
          v-model="searchQuery"
          placeholder="搜索文章标题或内容..."
          clearable
          @keyup.enter="currentPage = 1"
        >
          <template #prefix>
            <el-icon><Search /></el-icon>
          </template>
        </el-input>
      </div>
    </transition>

    <!-- Main Content Area -->
    <main class="page-main">
      <div class="page-content-wrapper">

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
            <el-icon :size="48"><DocumentRemove /></el-icon>
          </div>
          <h3 class="empty-title">{{ loadError ? '加载失败' : '暂无文章内容' }}</h3>
          <p class="empty-desc">{{ loadError ? '网络异常，请检查连接后重试' : '换个分类试试，或者稍后再来看看' }}</p>
          <button class="refresh-btn" @click="loadFeed">重新加载</button>
        </div>

        <!-- Article / Video Cards -->
        <template v-else>
          <article
            v-for="item in articles"
            :key="item._type + '-' + item.id"
            class="article-card"
            :class="{ 'is-video': item._type === 'video' }"
            @click="handleItemClick(item)"
          >
            <!-- Image Header -->
            <div class="article-cover">
              <div class="cover-gradient"></div>
              <div
                class="cover-image"
                :style="{
                  background: item.coverUrl
                    ? `url(${item.coverUrl})`
                    : item.coverMediaId
                      ? `url(${apiBase}/api/media/${item.coverMediaId}/download)`
                      : item.coverGradient,
                  backgroundSize: 'cover',
                  backgroundPosition: 'center'
                }"
              ></div>
              <!-- Video play icon badge -->
              <div v-if="item._type === 'video'" class="video-badge">
                <el-icon :size="14"><VideoPlay /></el-icon>
                <span>视频</span>
              </div>
              <div class="category-badge">
                <el-icon :size="14"><component :is="getCategoryIcon(item.category)" /></el-icon>
                <span>{{ getCategoryText(item.category) }}</span>
              </div>
            </div>

            <!-- Content Area -->
            <div class="article-content">
              <h2 class="article-title">{{ item.title }}</h2>
              <p class="article-snippet">{{ getSnippet(item._type === 'video' ? item.description : item.content) }}</p>
            </div>

            <!-- Footer Area -->
            <div class="article-footer">
              <div v-if="item._type === 'article'" class="difficulty-pill" :class="item.difficulty">
                <el-icon :size="14"><component :is="getDifficultyIcon(item.difficulty)" /></el-icon>
                <span>{{ getDifficultyText(item.difficulty) }}</span>
              </div>
              <div v-if="item._type === 'video' && item.tags" class="video-tag-pill">
                {{ item.tags.split(',')[0] }}
              </div>
              <div class="reading-time">
                <el-icon v-if="item._type === 'article'" :size="16"><Timer /></el-icon>
                <el-icon v-else :size="16"><View /></el-icon>
                <span>{{ item._type === 'video' ? `${item.viewCount || 0} 次观看` : `${item.readingTime} min` }}</span>
              </div>
            </div>
          </article>
        </template>
      </div>

      <!-- Pagination -->
      <div class="pagination" :style="{ visibility: totalPages > 1 ? 'visible' : 'hidden' }">
        <button class="page-btn" :disabled="currentPage === 1" @click="prevPage">
          <el-icon><ArrowLeft /></el-icon>
        </button>
        <button
          v-for="page in totalPages"
          :key="page"
          class="page-btn page-number"
          :class="{ active: currentPage === page }"
          @click="goToPage(page)"
        >
          {{ page }}
        </button>
        <button class="page-btn" :disabled="currentPage === totalPages" @click="nextPage">
          <el-icon><ArrowRight /></el-icon>
        </button>
      </div>

      <!-- Bottom Safe Area Spacer -->
      <div class="bottom-spacer"></div>
      </div>
    </main>

    <!-- FAB: 换一批推荐 -->
    <button
      v-if="selectedCategory === 'recommendation'"
      class="fab-refresh"
      :class="{ 'is-loading': loading }"
      :disabled="loading"
      @click="() => { recordRecommendationEvent('RECOMMENDATION_REFRESH', null); loadRecommended() }"
      title="换一批推荐"
    >
      <el-icon :size="22"><Refresh /></el-icon>
    </button>

  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  Search, Bell, DocumentRemove, Timer, VideoPlay, View,
  TrendCharts, Connection, Reading, Medal,
  ArrowUp, ArrowDown, ArrowLeft, ArrowRight, Refresh
} from '@element-plus/icons-vue'
import { getAuthHeader } from '@/utils/auth'
import { getFeaturedVideos } from '@/api/video'
import { getRecommendedTexts } from '@/api/texts'
import { recordRecommendationEvent } from '@/api/student'

const router = useRouter()
const apiBase = import.meta.env.VITE_API_BASE_URL || ''

const loading = ref(true)
const allItems = ref([])
const recommendedArticles = ref([])
const selectedCategory = ref('')
const filterVisible = ref(true)
const loadError = ref(false)
const currentPage = ref(1)
const pageSize = 5
const searchQuery = ref('')
const searchVisible = ref(false)
// 本会话中已展示过的推荐文章 ID — 传给后端用于"换一批"时排除
const shownRecommendedIds = ref([])


const totalPages = computed(() => Math.ceil(filteredItems.value.length / pageSize))
const filteredItems = computed(() => {
  if (selectedCategory.value === 'recommendation') {
    return recommendedArticles.value
  }
  let result = allItems.value
  if (selectedCategory.value) {
    if (selectedCategory.value === 'video') {
      result = result.filter(item => item._type === 'video')
    } else {
      result = result.filter(item => item.category === selectedCategory.value)
    }
  }
  if (searchQuery.value.trim()) {
    const q = searchQuery.value.trim().toLowerCase()
    result = result.filter(item =>
      item.title?.toLowerCase().includes(q) ||
      item.content?.toLowerCase().includes(q) ||
      item.description?.toLowerCase().includes(q)
    )
  }
  return result
})
const articles = computed(() => {
  const start = (currentPage.value - 1) * pageSize
  return filteredItems.value.slice(start, start + pageSize)
})

// 筛选栏开关
const toggleFilter = () => {
  filterVisible.value = !filterVisible.value
}

// Predefined categories based on design
const categories = [
  { label: '全部', value: '', icon: 'House' },
  { label: '推荐', value: 'recommendation', icon: 'Star' },
  { label: '视频', value: 'video' },
  { label: '科技', value: 'technology', icon: 'Monitor' },
  { label: '商务', value: 'business', icon: 'TrendCharts' },
  { label: '教育', value: 'education', icon: 'School' },
  { label: '生活', value: 'lifestyle', icon: 'Coffee' },
  { label: '文化', value: 'culture', icon: 'Compass' }
]

// 当前选中分类的显示名称（折叠时在 header 中显示）
const currentCategoryLabel = computed(() => {
  const cat = categories.find(c => c.value === selectedCategory.value)
  return cat ? cat.label : '全部'
})

// Cover gradient palettes (no external images)
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
  if (val === 'recommendation') {
    shownRecommendedIds.value = []
    loadRecommended()
  }
}

const goToPage = (page) => {
  if (page < 1 || page > totalPages.value) return
  currentPage.value = page
  window.scrollTo({ top: 0, behavior: 'smooth' })
}

const prevPage = () => goToPage(currentPage.value - 1)
const nextPage = () => goToPage(currentPage.value + 1)

const toggleSearch = () => {
  searchVisible.value = !searchVisible.value
  if (!searchVisible.value) {
    searchQuery.value = ''
  }
}

const getRandomCategory = () => {
  const cats = categories.filter(c => c.value !== '' && c.value !== 'video' && c.value !== 'recommendation')
  return cats[Math.floor(Math.random() * cats.length)].value
}

const COUNT_PER_BATCH = 5

const mapRecommendedText = (text) => ({
  ...text,
  _type: 'article',
  category: 'recommendation',
  coverGradient: getRandomGradient(),
  readingTime: Math.max(1, Math.ceil((text.content?.length || text.markdownContent?.length || 0) / 800))
})

const loadRecommended = async ({ resetExclusions = false } = {}) => {
  loading.value = true
  loadError.value = false
  try {
    if (resetExclusions) {
      shownRecommendedIds.value = []
    }
    let data = await getRecommendedTexts(COUNT_PER_BATCH, shownRecommendedIds.value)
    if (data?.code === 200) {
      let list = Array.isArray(data.data) ? data.data : []
      // 如果返回空且已有排除列表，重置排除列表重新拉取
      if (list.length === 0 && shownRecommendedIds.value.length > 0) {
        shownRecommendedIds.value = []
        data = await getRecommendedTexts(COUNT_PER_BATCH, [])
        list = Array.isArray(data.data) ? data.data : []
      }
      recommendedArticles.value = list.map(mapRecommendedText)
      // 记录本次展示的 ID
      list.forEach(t => {
        if (t?.id != null && !shownRecommendedIds.value.includes(t.id)) {
          shownRecommendedIds.value.push(t.id)
        }
      })
      // 推荐曝光埋点
      if (list.length > 0) recordRecommendationEvent('RECOMMENDATION_VIEW', null)
    } else {
      loadError.value = true
    }
  } catch (err) {
    console.error('加载推荐失败:', err)
    loadError.value = true
    recommendedArticles.value = []
  } finally {
    loading.value = false
  }
}

const loadFeed = async () => {
  loading.value = true
  loadError.value = false
  try {
    const authHeader = getAuthHeader()
    const headers = authHeader ? { Authorization: authHeader } : {}

    const [articlesRes, videosRes] = await Promise.all([
      fetch(`${apiBase}/api/public/texts/featured`, { headers }).then(r => r.json()),
      getFeaturedVideos({}).catch(() => ({ code: 0, data: [] }))
    ])

    let articleItems = []
    if (articlesRes?.code === 200) {
      articleItems = (Array.isArray(articlesRes.data) ? articlesRes.data : []).map(text => {
        const cat = getRandomCategory()
        const contentLength = typeof text.content === 'string' && text.content.length > 0
          ? text.content.length
          : (typeof text.markdownContent === 'string' ? text.markdownContent.length : 0)
        return {
          ...text,
          _type: 'article',
          category: cat,
          coverGradient: getRandomGradient(),
          readingTime: Math.max(1, Math.ceil(contentLength / 800))
        }
      })
    }

    let videoItems = []
    if (videosRes?.code === 200) {
      videoItems = (Array.isArray(videosRes.data) ? videosRes.data : []).map(v => ({
        ...v,
        _type: 'video',
        category: 'video',
        coverGradient: getRandomGradient()
      }))
    }

    // Interleave: every 3 articles insert a video
    const merged = []
    let vi = 0
    articleItems.forEach((a, i) => {
      merged.push(a)
      if ((i + 1) % 3 === 0 && vi < videoItems.length) {
        merged.push(videoItems[vi++])
      }
    })
    while (vi < videoItems.length) merged.push(videoItems[vi++])

    allItems.value = merged
    if (merged.length === 0) loadError.value = true
  } catch (error) {
    console.error('加载推荐内容失败:', error)
    allItems.value = []
  } finally {
    loading.value = false
  }
}

const handleItemClick = (item) => {
  if (item._type === 'video') {
    router.push({ path: '/student/video-player', query: { videoId: item.id } })
    return
  }
  if (!item?.id) {
    console.warn('readArticle: 文章ID不存在', item)
    return
  }
  // 推荐点击埋点
  if (selectedCategory.value === 'recommendation') {
    recordRecommendationEvent('RECOMMENDATION_CLICK', item.id)
  }
  // 验证 ID 是否为有效数字
  const numericId = typeof item.id === 'number' ? item.id : parseInt(item.id, 10)
  if (isNaN(numericId) || numericId <= 0) {
    console.error('readArticle: 无效的文章ID', item.id, item)
    ElMessage.error('文章ID无效，无法打开')
    return
  }
  // Fire-and-forget: 标记文章为已读
  const authHeader = getAuthHeader()
  const headers = authHeader ? { Authorization: authHeader } : {}
  fetch(`${apiBase}/api/public/texts/${numericId}/read`, { method: 'POST', headers }).catch(() => {})
  router.push({
    path: `/article/${numericId}`,
    query: {
      from: 'articles',
      _title: encodeURIComponent(item.title || '')
    }
  })
}

const getSnippet = (rawOrArticle) => {
  // 支持直接传入字符串（视频 description）或文章对象
  let raw = typeof rawOrArticle === 'string'
    ? rawOrArticle
    : (rawOrArticle?.content || rawOrArticle?.markdownContent || '')
  if (typeof raw !== 'string' || !raw) return ''

  // 清理 Markdown 语法标记，提取纯文字
  let clean = raw
    .replace(/!\[.*?\]\(.*?\)/g, '')          // 删除图片 ![alt](url)
    .replace(/\[(.*?)\]\(.*?\)/g, '$1')       // 链接保留文字 [text](url) → text
    .replace(/^#{1,6}\s+/gm, '')              // 删除标题符 # ## ###
    .replace(/[*_]{1,3}(.*?)[*_]{1,3}/g, '$1') // 加粗/斜体保留文字
    .replace(/`{1,3}[^`]*`{1,3}/g, '')        // 删除行内代码和代码块
    .replace(/^>\s?/gm, '')                   // 删除引用 >
    .replace(/^[-*+]\s+/gm, '')               // 删除无序列表标记
    .replace(/^\d+\.\s+/gm, '')               // 删除有序列表标记
    .replace(/---+|\*\*\*+|___+/g, '')        // 删除分割线
    .replace(/[\n\r]+/g, ' ')                  // 换行转空格
    .replace(/\s+/g, ' ')                      // 合并多余空格
    .trim()

  return clean.length > 120 ? clean.substring(0, 120) + '...' : clean
}

const getCategoryText = (val) => {
  const cat = categories.find(c => c.value === val)
  return cat ? cat.label : '综合'
}

const getCategoryIcon = (val) => {
  const cat = categories.find(c => c.value === val)
  return cat ? cat.icon : Reading
}

const getDifficultyText = (diff) => {
  const map = { 'EASY': '简单 EASY', 'MEDIUM': '中等 INT', 'HARD': '困难 HARD' }
  return map[diff] || diff
}

const getDifficultyIcon = (diff) => {
  const map = { 'EASY': Medal, 'MEDIUM': Connection, 'HARD': TrendCharts }
  return map[diff] || Medal
}

onMounted(() => {
  loadFeed()
})
</script>

<style scoped>
/* =========================================
   Mobile-First Articles Feed
   Inspired by Stitch Premium Design
   ========================================= */

:root {
  --app-bg: #f6f7f8;
  --app-surface: #ffffff;
  --app-primary: #137fec;
  --app-text: #0f172a;
  --app-text-muted: #64748b;
  --app-border: #e2e8f0;
}

.articles-mobile-page {
  height: 100%;
  overflow: hidden;
  background-color: var(--app-bg);
  font-family: 'Inter', -apple-system, BlinkMacSystemFont, sans-serif;
  color: var(--app-text);
  position: relative;
  display: flex;
  flex-direction: column;
}

/* --- Responsive Layout for Desktop --- */
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
    grid-template-rows: repeat(3, 320px);
    gap: 24px;
    padding: 0;
    align-content: start;
  }

  .bottom-spacer,
  .pagination {
    display: none;
  }

  .page-main {
    padding-bottom: 0;
  }

  .article-card {
    height: 320px;
  }

  .article-cover {
    height: 140px;
  }

  .article-content {
    flex: 1;
  }

  .article-content {
    padding: 12px 16px 10px;
  }
}

@media (min-width: 1200px) {
  .articles-feed {
    grid-template-columns: repeat(3, 1fr);
    grid-template-rows: repeat(2, 320px);
  }
}

/* --- Header --- */
.articles-toolbar {
  flex-shrink: 0;
  z-index: 50;
  background: rgba(255, 255, 255, 0.85);
  backdrop-filter: blur(16px);
  -webkit-backdrop-filter: blur(16px);
  border-bottom: 1px solid rgba(226, 232, 240, 0.6);
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 12px;
  padding-top: max(env(safe-area-inset-top, 12px), 12px);
}


.header-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

/* 折叠时显示的当前分类标签 */
.current-filter-label {
  font-size: 12px;
  font-weight: 600;
  color: var(--app-primary);
  background: rgba(19, 127, 236, 0.1);
  padding: 4px 10px;
  border-radius: 999px;
  white-space: nowrap;
}

.icon-btn {
  width: 44px;
  height: 44px;
  border-radius: 50%;
  border: none;
  background: transparent;
  color: #475569;
  font-size: 20px;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: background-color 0.2s;
}

.icon-btn:hover { background: #f1f5f9; color: #0f172a; }

.notification-btn {
  position: relative;
}

.notification-dot {
  position: absolute;
  top: 8px;
  right: 8px;
  width: 8px;
  height: 8px;
  background-color: #ef4444;
  border: 2px solid white;
  border-radius: 50%;
}

/* --- Filter Bar --- */
.filter-bar {
  position: sticky;
  top: 0;
  z-index: 40;
  background: rgba(246, 247, 248, 0.95);
  backdrop-filter: blur(8px);
  -webkit-backdrop-filter: blur(8px);
  border-bottom: 1px solid rgba(226, 232, 240, 0.6);
  padding: 12px 0;
}

.search-bar {
  position: sticky;
  top: 0;
  padding: 12px 12px;
  background: rgba(246, 247, 248, 0.95);
  backdrop-filter: blur(8px);
  -webkit-backdrop-filter: blur(8px);
  border-bottom: 1px solid rgba(226, 232, 240, 0.6);
}

.filter-scroll {
  display: flex;
  gap: 10px;
  overflow-x: auto;
  padding: 0 12px;
  scrollbar-width: none;
  -webkit-overflow-scrolling: touch;
}
@media (min-width: 768px) {
  .filter-scroll { justify-content: center; gap: 16px; }
  .filter-pill { padding: 12px 36px; font-size: 16px; font-weight: 600; }
}
.filter-scroll::-webkit-scrollbar { display: none; }

.filter-pill {
  flex-shrink: 0;
  padding: 8px 14px;
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

.filter-pill,
.article-card,
.page-btn {
  -webkit-tap-highlight-color: transparent;
  touch-action: manipulation;
}

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
}

.page-content-wrapper {
  display: flex;
  flex-direction: column;
  min-height: 100%;
}

.articles-feed {
  padding: 12px 12px;
  display: flex;
  flex-direction: column;
  gap: 12px;
  flex: 1;
}

/* --- Article Card --- */
.article-card {
  background: white;
  border-radius: 12px;
  overflow: hidden;
  box-shadow: 0 4px 20px -2px rgba(0,0,0,0.05);
  transition: transform 0.2s, box-shadow 0.2s;
  cursor: pointer;
  display: flex;
  flex-direction: column;
  border: 1px solid #f1f5f9;
  width: 100%;
  min-width: 0;
  height: 340px;
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

/* Image Header */
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

.category-badge {
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
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 11px;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.05em;
}

/* Content */
.article-content {
  padding: 12px 12px 10px;
  flex: 1;
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
  /* Fixed 2-line height for consistent card size */
  height: calc(2 * 1.3em);
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
  /* Fixed 2-line height for consistent card size */
  height: calc(2 * 1.5em);
}

/* Footer Insights */
.article-footer {
  padding: 0 12px 12px;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.difficulty-pill {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 4px 8px;
  border-radius: 6px;
  font-size: 10px;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.05em;
}

.difficulty-pill.EASY { background: rgba(16, 185, 129, 0.1); color: #047857; }
.difficulty-pill.MEDIUM { background: rgba(245, 158, 11, 0.1); color: #b45309; }
.difficulty-pill.HARD { background: rgba(239, 68, 68, 0.1); color: #b91c1c; }

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
.skeleton-body { padding: 12px; }
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

.page-number {
  font-weight: 600;
}

.bottom-spacer {
  height: calc(80px + env(safe-area-inset-bottom, 0px)); /* Match Layout nav + safe area */
}

/* --- Floating Refresh Button --- */
.fab-refresh {
  position: fixed;
  right: 20px;
  bottom: calc(96px + env(safe-area-inset-bottom, 0px)); /* 避开底部导航栏 */
  z-index: 60;
  width: 52px;
  height: 52px;
  border-radius: 50%;
  border: none;
  background: linear-gradient(135deg, #1d4ed8 0%, #137fec 100%);
  color: white;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  box-shadow: 0 8px 24px rgba(29, 78, 216, 0.35), 0 2px 6px rgba(0, 0, 0, 0.08);
  transition: transform 0.2s ease, box-shadow 0.2s ease;
  -webkit-tap-highlight-color: transparent;
  touch-action: manipulation;
}

.fab-refresh:hover:not(:disabled) {
  transform: translateY(-2px) scale(1.05);
  box-shadow: 0 12px 32px rgba(29, 78, 216, 0.45), 0 4px 10px rgba(0, 0, 0, 0.1);
}

.fab-refresh:active:not(:disabled) {
  transform: scale(0.94);
}

.fab-refresh:disabled {
  cursor: wait;
  opacity: 0.85;
}

.fab-refresh.is-loading .el-icon {
  animation: fab-spin 0.9s linear infinite;
}

@keyframes fab-spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

@media (min-width: 768px) {
  .fab-refresh {
    right: 32px;
    bottom: calc(64px + env(safe-area-inset-bottom, 0px));
  }
}

/* --- Filter Collapse Transition --- */
.filter-collapse-enter-active,
.filter-collapse-leave-active {
  transition: all 0.3s ease;
  overflow: hidden;
  max-height: 60px;
}

.filter-collapse-enter-from,
.filter-collapse-leave-to {
  max-height: 0;
  opacity: 0;
  padding-top: 0;
  padding-bottom: 0;
}
/* --- Video Card Styles --- */
.article-card.is-video {
  border: 1px solid rgba(99, 102, 241, 0.15);
}

.video-badge {
  position: absolute;
  top: 12px;
  right: 12px;
  z-index: 2;
  background: rgba(99, 102, 241, 0.85);
  backdrop-filter: blur(8px);
  -webkit-backdrop-filter: blur(8px);
  color: white;
  padding: 4px 10px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 11px;
  font-weight: 600;
}

.video-tag-pill {
  display: inline-flex;
  align-items: center;
  padding: 4px 10px;
  border-radius: 6px;
  font-size: 10px;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  background: rgba(99, 102, 241, 0.1);
  color: #4f46e5;
}

/* --- FAB: 换一批推荐 --- */
@media (min-width: 768px) {
  .fab-refresh {
    right: 32px;
    bottom: calc(64px + env(safe-area-inset-bottom, 0px));
  }
}
</style>
