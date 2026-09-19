<template>
  <div class="articles-mobile-page">

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

    <!-- Main Content Area -->
    <main
      ref="pageMain"
      class="page-main"
    >
      <!-- Pull-to-Refresh Indicator -->
      <div class="pull-refresh-indicator" :class="{ 'is-refreshing': isRefreshing, 'is-pulling': isPulling }" :style="{ transform: `translateY(${pullDistance}px)` }">
        <div class="pull-refresh-content">
          <span v-if="isRefreshing" class="pull-refresh-spinner"></span>
          <span v-else class="pull-refresh-arrow" :style="{ transform: pullDistance > pullThreshold ? 'rotate(180deg)' : 'rotate(0deg)' }">↓</span>
          <span class="pull-refresh-text">{{ pullHintText }}</span>
        </div>
      </div>

      <div class="page-content-wrapper" :style="{ transform: `translateY(${pullDistance}px)`, transition: isPulling ? 'none' : 'transform 0.3s ease' }">

      <XyPageHeader
        class="articles-intro"
        variant="hero"
        eyebrow="EXPLORE WITH XIAOYA"
        title="从好奇开始，用英语发现世界"
        description="读你感兴趣的内容，让英语成为认识世界的工具。"
      >
        <template #visual><XyMascot :size="90" /></template>
      </XyPageHeader>

      <div v-if="selectedCategory === 'recommendation' && !isSearchMode" class="learning-actions">
        <button v-if="continueReading" type="button" class="learning-action" @click="handleItemClick(continueReading)">
          <span>继续阅读</span><strong>{{ continueReading.title }}</strong><small>{{ continueReading.reason }}</small>
        </button>
        <button v-if="reviewItem" type="button" class="learning-action" @click="handleItemClick(reviewItem)">
          <span>知识点回顾</span><strong>{{ reviewItem.title }}</strong><small>{{ reviewItem.reason }}</small>
        </button>
        <button v-if="onboardingSuggested" type="button" class="learning-action onboarding-action" @click="router.push('/student/learning-agent')">
          <span>让小芽更懂你</span><strong>告诉我你关心什么</strong><small>兴趣和学习目标会帮助我找到更合适的英文内容</small>
        </button>
      </div>

      <!-- Search Bar -->
      <div class="search-bar">
        <el-input
          v-model="searchQuery"
          placeholder="搜索文章标题、内容、标签..."
          clearable
        >
          <template #suffix>
            <el-icon><Search /></el-icon>
          </template>
        </el-input>
        <div v-if="isSearchMode" class="search-result-hint">
          <span v-if="isSearching">搜索中...</span>
          <span v-else>找到 {{ searchTotal }} 篇文章（含全部文章）</span>
        </div>
      </div>

      <section v-if="selectedCategory === 'recommendation' && !isSearchMode && externalSuggestions.length" class="external-explore">
        <div class="external-heading"><strong>用英语发现世界</strong><span>站外英文原文 · 点击后离开本平台</span></div>
        <div class="external-items">
          <button v-for="item in externalSuggestions" :key="item.id" type="button" class="external-item" @click="openExternal(item)">
            <span>{{ item.sourceName }} · {{ item.topic }}</span>
            <strong>{{ item.title }}</strong>
            <small>{{ item.reason }}</small>
          </button>
        </div>
      </section>

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
          <button class="refresh-btn" @click="doRefresh">重新加载</button>
        </div>

        <!-- Article / Video Cards -->
        <template v-else>
          <article
            v-for="item in articles"
            :key="item._type + '-' + item.id"
            class="article-card"
            :class="{ 'is-video': item._type === 'video' }"
            :data-feed-id="item.feedSource ? item.id : undefined"
            :data-feed-source="item.feedSource || undefined"
            :data-feed-position="item.feedPosition"
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
              <p v-if="item.reason" class="feed-reason">{{ item.reason }}</p>
              <p v-if="item.author" class="card-author">
                {{ item.author.displayName }}
                <span v-if="item.author.roleLabel && !item.author.official" class="card-author-role">{{ item.author.roleLabel }}</span>
              </p>
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
              <button v-if="item.feedSource === 'FEED_FOR_YOU'" type="button" class="card-dismiss" @click.stop="hideItem(item)">不感兴趣</button>
            </div>
          </article>
        </template>
      </div>

      <!-- Infinite Scroll Sentinel -->
      <div ref="sentinel" class="scroll-sentinel">
        <div v-if="loadingMore" class="load-more-hint">
          <span class="load-more-spinner"></span>
          加载中...
        </div>
        <div v-else-if="hasMore" class="load-more-hint">
          上滑加载更多
        </div>
        <div v-else-if="filteredItems.length > 0" class="load-more-hint load-more-end">
          — 已经到底了 —
        </div>
      </div>

      <!-- Bottom Safe Area Spacer -->
      <div class="bottom-spacer"></div>
      </div>
    </main>

    <!-- FAB: 刷新当前信息流 -->
    <button
      v-if="!isSearchMode"
      class="fab-refresh"
      :class="{ 'is-loading': loading }"
      :disabled="loading"
      @click="doRefresh"
      title="刷新文章"
      aria-label="刷新文章"
    >
      <el-icon :size="22"><Refresh /></el-icon>
    </button>

  </div>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount, onActivated, onDeactivated, watch, nextTick } from 'vue'
import { useRouter, onBeforeRouteLeave } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  Search, Bell, DocumentRemove, Timer, VideoPlay, View,
  TrendCharts, Connection, Reading, Medal,
  ArrowUp, ArrowDown, Refresh
} from '@element-plus/icons-vue'
import { getAuthHeader } from '@/utils/auth'
import { getFeaturedVideos } from '@/api/video'
import { getArticleFeed, recordArticleFeedEvent, searchArticles } from '@/api/texts'
import { getLearningAgentContentRecommendations } from '@/api/learningAgent'
import { useUserStore } from '@/store/user'
import XyPageHeader from '@/components/ui/XyPageHeader.vue'
import XyMascot from '@/components/ui/XyMascot.vue'

const router = useRouter()
const userStore = useUserStore()
const apiBase = import.meta.env.VITE_API_BASE_URL || ''

// ── Scroll Position & Data Cache (恢复滚动位置) ──
const CACHE_KEY = 'student_articles_cache'
const SCROLL_KEY = 'student_articles_scroll'
const NAV_FLAG_KEY = 'student_articles_nav_to_detail'

// 实时节流保存滚动位置（每 300ms），确保导航前总有最新值
let scrollSaveTimer = null
const trackScroll = () => {
  if (scrollSaveTimer) return
  scrollSaveTimer = setTimeout(() => {
    scrollSaveTimer = null
    const s = pageMain.value?.scrollTop || 0
    if (s > 0) {
      sessionStorage.setItem(SCROLL_KEY, String(s))
    }
  }, 300)
}

const saveScrollState = () => {
  const scrollTop = pageMain.value?.scrollTop || 0
  try {
    // 只有 scrollTop > 0 时才覆盖（避免 DOM detach 后的 0 覆盖有效值）
    if (scrollTop > 0) {
      sessionStorage.setItem(SCROLL_KEY, String(scrollTop))
    }
    sessionStorage.setItem(NAV_FLAG_KEY, '1')
    sessionStorage.setItem(CACHE_KEY, JSON.stringify({
      allItems: allItems.value,
      selectedCategory: selectedCategory.value,
      searchQuery: searchQuery.value,
      serverSearchResults: serverSearchResults.value,
      searchTotal: searchTotal.value,
      searchPage: searchPage.value,
      feedSeed: feedSeed.value,
      feedNextPage: feedNextPage.value,
      feedHasMore: feedHasMore.value,
      continueReading: continueReading.value,
      reviewItem: reviewItem.value,
      onboardingSuggested: onboardingSuggested.value,
      externalSuggestions: externalSuggestions.value,
    }))
  } catch (e) { /* quota exceeded or private mode */ }
}

const restoreScrollState = () => {
  try {
    const cached = sessionStorage.getItem(CACHE_KEY)
    if (!cached) return false
    const data = JSON.parse(cached)
    if (!data || !Array.isArray(data.allItems)) return false
    // 如果缓存数据为空，不恢复，强制重新加载
    if (data.allItems.length === 0) return false
    allItems.value = data.allItems
    selectedCategory.value = data.selectedCategory ?? 'recommendation'
    searchQuery.value = data.searchQuery || ''
    serverSearchResults.value = data.serverSearchResults || []
    searchTotal.value = data.searchTotal || 0
    searchPage.value = data.searchPage || 1
    feedSeed.value = data.feedSeed || null
    feedNextPage.value = data.feedNextPage || 1
    feedHasMore.value = Boolean(data.feedHasMore)
    continueReading.value = data.continueReading || null
    reviewItem.value = data.reviewItem || null
    onboardingSuggested.value = Boolean(data.onboardingSuggested)
    externalSuggestions.value = Array.isArray(data.externalSuggestions) ? data.externalSuggestions : []
    return true
  } catch (e) { return false }
}

const restoreScrollPosition = () => {
  try {
    const saved = sessionStorage.getItem(SCROLL_KEY)
    if (!saved) return
    const target = parseInt(saved, 10) || 0
    if (target <= 0) return

    const tryRestore = (attempts = 0) => {
      if (!pageMain.value) return
      pageMain.value.scrollTop = target
      if (pageMain.value.scrollTop < target - 5 && attempts < 50) {
        requestAnimationFrame(() => tryRestore(attempts + 1))
      }
    }
    // 立即尝试 + rAF + setTimeout 兜底
    tryRestore()
    requestAnimationFrame(() => tryRestore())
    setTimeout(() => tryRestore(), 50)
  } catch (e) { /* ignore */ }
}

const clearCache = () => {
  try {
    sessionStorage.removeItem(CACHE_KEY)
    sessionStorage.removeItem(SCROLL_KEY)
    sessionStorage.removeItem(NAV_FLAG_KEY)
  } catch (e) { /* ignore */ }
}

// A browser reload must fetch fresh data instead of restoring the detail-page cache.
const isDocumentReload = () => {
  try {
    if (typeof performance === 'undefined') return false
    const navigationType = performance.getEntriesByType?.('navigation')?.[0]?.type
    const legacyNavigationType = performance.navigation?.type
    return navigationType === 'reload' || legacyNavigationType === 1
  } catch (e) {
    return false
  }
}

// ── Pull-to-Refresh State ──
const pageMain = ref(null)
const isPulling = ref(false)
const isRefreshing = ref(false)
const pullDistance = ref(0)
const pullThreshold = 40
let touchStartY = 0
let touchStartX = 0
let pullLocked = false
let pullGestureTarget = null

const pullHintText = computed(() => {
  if (isRefreshing.value) return '正在刷新...'
  if (pullDistance.value > pullThreshold) return '松开刷新'
  return '下拉刷新'
})

const loading = ref(true)
const allItems = ref([])
const continueReading = ref(null)
const reviewItem = ref(null)
const onboardingSuggested = ref(false)
const externalSuggestions = ref([])
const selectedCategory = ref('recommendation')
const filterVisible = ref(true)
const loadError = ref(false)
const BATCH_SIZE = 12
const feedSeed = ref(null)
const feedNextPage = ref(1)
const feedHasMore = ref(false)
let feedRequestId = 0
const loadingMore = ref(false)
const sentinel = ref(null)
let observer = null
let cardObserver = null
const seenImpressions = new Set()
const searchQuery = ref('')
// 服务端搜索结果（搜索全部已审核文章，不限精选）
const serverSearchResults = ref([])
const isSearching = ref(false)
const searchTotal = ref(0)
const searchPage = ref(1)
let searchTimer = null


const hasMore = computed(() => isSearchMode.value
  ? serverSearchResults.value.length < searchTotal.value
  : feedHasMore.value)

// 是否处于搜索模式
const isSearchMode = computed(() => searchQuery.value.trim().length > 0)

// 服务端搜索（防抖）
const doServerSearch = async (keyword, page = 1) => {
  if (!keyword.trim()) {
    serverSearchResults.value = []
    searchTotal.value = 0
    searchPage.value = 1
    isSearching.value = false
    return
  }
  isSearching.value = true
  try {
    const res = await searchArticles(keyword.trim(), page, 20)
    if (res?.code === 200 && res.data) {
      if (keyword.trim() !== searchQuery.value.trim()) return
      const results = (res.data.articles || []).map(text => ({
        ...text,
        _type: 'article',
        coverGradient: getRandomGradient(),
        readingTime: Math.max(1, Math.ceil((text.content?.length || 0) / 800))
      }))
      serverSearchResults.value = page === 1 ? results : [...serverSearchResults.value, ...results]
      searchTotal.value = res.data.total || 0
      searchPage.value = page
    }
  } catch (e) {
    console.error('搜索文章失败:', e)
    serverSearchResults.value = []
  } finally {
    isSearching.value = false
  }
}

watch(searchQuery, (val) => {
  clearTimeout(searchTimer)
  const kw = val.trim()
  if (!kw) {
    serverSearchResults.value = []
    searchTotal.value = 0
    searchPage.value = 1
    return
  }
  searchTimer = setTimeout(() => {
    doServerSearch(kw, 1)
  }, 400)
})

const filteredItems = computed(() => {
  return isSearchMode.value ? serverSearchResults.value : allItems.value
})
const articles = computed(() => filteredItems.value)

// 筛选栏开关
const toggleFilter = () => {
  filterVisible.value = !filterVisible.value
}

// Predefined categories based on design
const categories = [
  { label: '为你', value: 'recommendation', icon: 'Star' },
  { label: '发现', value: '', icon: 'House' },
  { label: '视频', value: 'video' },
  { label: '科技', value: 'technology', icon: 'Monitor' },
  { label: '商务', value: 'business', icon: 'TrendCharts' },
  { label: '教育', value: 'education', icon: 'School' },
  { label: '真题', value: 'exam', icon: 'Document' },
  { label: '生活', value: 'lifestyle', icon: 'Coffee' },
  { label: '文化', value: 'culture', icon: 'Compass' }
]

// 当前选中分类的显示名称（折叠时在 header 中显示）
const currentCategoryLabel = computed(() => {
  const cat = categories.find(c => c.value === selectedCategory.value)
  return cat ? cat.label : '为你'
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
  searchQuery.value = ''
  allItems.value = []
  feedSeed.value = null
  loadFeed()
}

const loadMore = async () => {
  if (loading.value || loadingMore.value || !hasMore.value) return
  if (isSearchMode.value) {
    loadingMore.value = true
    try {
      await doServerSearch(searchQuery.value.trim(), searchPage.value + 1)
    } finally {
      loadingMore.value = false
    }
    return
  }
  await loadFeed({ append: true })
}

const setupObserver = () => {
  if (observer) observer.disconnect()
  nextTick(() => {
    if (!sentinel.value || typeof IntersectionObserver === 'undefined') return
    observer = new IntersectionObserver((entries) => {
      if (entries[0].isIntersecting) loadMore()
    }, { root: pageMain.value, rootMargin: '200px' })
    observer.observe(sentinel.value)
  })
}

const mapFeedItem = (text, source = null) => ({
  ...text,
  _type: 'article',
  content: text.excerpt || '',
  coverGradient: getRandomGradient(),
  readingTime: text.estimatedMinutes || 1,
  feedSource: source,
  feedPosition: text.position || 0
})

const rotateFreshFirst = (items, previousIds) => {
  if (!Array.isArray(items) || items.length < 2 || !Array.isArray(previousIds) || previousIds.length === 0) {
    return items
  }
  const previousSet = new Set(previousIds.map(id => String(id)))
  const freshIndex = items.findIndex(item => item?._type === 'article' && item?.id != null && !previousSet.has(String(item.id)))
  if (freshIndex > 0) {
    return [items[freshIndex], ...items.slice(0, freshIndex), ...items.slice(freshIndex + 1)]
  }
  if (freshIndex < 0) {
    const articleIndices = items.map((item, index) => item?._type === 'article' ? index : -1).filter(index => index >= 0)
    if (articleIndices.length < 2) return items
    const rotated = [...items]
    const [first, second] = articleIndices
    ;[rotated[first], rotated[second]] = [rotated[second], rotated[first]]
    return rotated
  }
  return items
}

const mapVideo = (video) => ({
  ...video,
  _type: 'video',
  category: 'video',
  coverGradient: getRandomGradient()
})

const loadExternalSuggestions = async () => {
  if (!['STUDENT', 'USER'].includes(userStore.userRole)) return
  try {
    const result = await getLearningAgentContentRecommendations(6)
    externalSuggestions.value = result?.code === 200 && Array.isArray(result.data)
      ? result.data.filter(item => item.external && /^https:\/\//.test(item.url || '')).slice(0, 2)
      : []
  } catch {
    externalSuggestions.value = []
  }
}

const openExternal = (item) => {
  if (item?.external && /^https:\/\//.test(item.url || '')) {
    window.open(item.url, '_blank', 'noopener,noreferrer')
  }
}

const loadFeed = async ({ forceRefresh = false, append = false } = {}) => {
  const requestId = ++feedRequestId
  if (append) loadingMore.value = true
  else loading.value = true
  if (!append) loadError.value = false
  const oldFirstIds = forceRefresh
    ? allItems.value.filter(item => item._type === 'article').slice(0, 5).map(item => item.id)
    : []
  try {
    if (selectedCategory.value === 'video') {
      const res = await getFeaturedVideos({ count: 30 })
      if (requestId !== feedRequestId) return
      allItems.value = res?.code === 200 ? (res.data || []).map(mapVideo) : []
      feedHasMore.value = false
      return
    }
    const mode = selectedCategory.value === 'recommendation' ? 'FOR_YOU' : 'EXPLORE'
    const category = mode === 'EXPLORE' && selectedCategory.value ? selectedCategory.value : undefined
    const source = mode === 'FOR_YOU' ? 'FEED_FOR_YOU' : category ? 'FEED_CATEGORY' : 'FEED_EXPLORE'
    const params = {
      mode,
      category,
      page: append ? feedNextPage.value : 1,
      size: BATCH_SIZE,
      seed: append ? feedSeed.value : forceRefresh ? Date.now() : undefined,
      exclude: forceRefresh && mode === 'FOR_YOU' && oldFirstIds.length ? oldFirstIds.join(',') : undefined
    }
    let res = await getArticleFeed(params)
    if (requestId !== feedRequestId) return
    if (res?.code !== 200 || !res.data) throw new Error('Feed 接口未返回有效数据')
    if (!append && params.exclude && (res.data.items || []).length === 0) {
      res = await getArticleFeed({ ...params, exclude: undefined })
      if (requestId !== feedRequestId) return
      if (res?.code !== 200 || !res.data) throw new Error('Feed 刷新失败')
    }
    const data = res.data
    feedSeed.value = data.seed
    feedNextPage.value = data.nextPage
    feedHasMore.value = Boolean(data.hasMore)
    if (!append) {
      continueReading.value = data.continueReading ? mapFeedItem(data.continueReading) : null
      reviewItem.value = data.review ? mapFeedItem(data.review) : null
      onboardingSuggested.value = Boolean(data.onboardingSuggested)
    }
    let items = (data.items || []).map(item => mapFeedItem(item, source))
    if (!append && !category) {
      const videosRes = await getFeaturedVideos({ count: 2 }).catch(() => null)
      if (requestId !== feedRequestId) return
      const videos = videosRes?.code === 200 ? (videosRes.data || []).map(mapVideo) : []
      const merged = []
      items.forEach((item, index) => {
        merged.push(item)
        if ((index + 1) % 3 === 0 && videos.length) merged.push(videos.shift())
      })
      items = [...merged, ...videos]
    }
    if (forceRefresh) items = rotateFreshFirst(items, oldFirstIds)
    allItems.value = append ? [...allItems.value, ...items] : items
  } catch (error) {
    if (requestId !== feedRequestId) return
    console.error('加载文章 Feed 失败:', error)
    if (allItems.value.length === 0) loadError.value = true
  } finally {
    if (requestId === feedRequestId) {
      if (append) loadingMore.value = false
      else loading.value = false
    }
  }
}

const setupCardObserver = () => {
  if (cardObserver) cardObserver.disconnect()
  if (!pageMain.value || typeof IntersectionObserver === 'undefined') return
  cardObserver = new IntersectionObserver((entries) => {
    entries.forEach(entry => {
      if (!entry.isIntersecting || entry.intersectionRatio < 0.5) return
      const { feedId, feedSource, feedPosition } = entry.target.dataset
      const key = `${feedSource}:${feedId}`
      if (feedId && feedSource && !seenImpressions.has(key)) {
        seenImpressions.add(key)
        recordArticleFeedEvent({ textId: Number(feedId), eventType: 'IMPRESSION',
          source: feedSource, position: Number(feedPosition) || 0 }).catch(() => {})
      }
      cardObserver?.unobserve(entry.target)
    })
  }, { root: pageMain.value, threshold: 0.5 })
  pageMain.value.querySelectorAll('.article-card[data-feed-id]').forEach(card => cardObserver.observe(card))
}

watch(articles, () => nextTick(setupCardObserver), { flush: 'post' })

const hideItem = (item) => {
  allItems.value = allItems.value.filter(candidate => !(candidate.id === item.id && candidate._type === 'article'))
  if (item.feedSource) {
    recordArticleFeedEvent({ textId: item.id, eventType: 'DISMISS',
      source: item.feedSource, position: item.feedPosition || 0 }).catch(() => {})
  }
}

const handleItemClick = (item) => {
  if (item._type === 'video') {
    // 在导航前保存滚动位置到 sessionStorage
    saveScrollState()
    router.push({ path: '/student/video-player', query: { videoId: item.id } })
    return
  }
  if (!item?.id) {
    console.warn('readArticle: 文章ID不存在', item)
    return
  }
  if (item.feedSource) {
    recordArticleFeedEvent({ textId: item.id, eventType: 'CLICK',
      source: item.feedSource, position: item.feedPosition || 0 }).catch(() => {})
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
  // 在导航前保存滚动位置到 sessionStorage（此时 DOM 还在，scrollTop 有效）
  saveScrollState()
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
  // Register touchmove as non-passive so mobile browser overscroll cannot steal
  // the gesture before the pull-to-refresh threshold is reached.
  nextTick(() => {
    pullGestureTarget = pageMain.value
    pullGestureTarget?.addEventListener('touchstart', onTouchStart, { passive: true })
    pullGestureTarget?.addEventListener('touchmove', onTouchMove, { passive: false })
    pullGestureTarget?.addEventListener('touchend', onTouchEnd, { passive: true })
  })
  // 注册 scroll 实时追踪
  nextTick(() => {
    if (pageMain.value) pageMain.value.addEventListener('scroll', trackScroll, { passive: true })
  })
  // 仅在从文章详情/视频页返回时恢复缓存（NAV_FLAG_KEY 标记存在）
  // 浏览器刷新时 sessionStorage 虽保留，但 NAV_FLAG 不存在，强制重新加载
  const hasNavFlag = !isDocumentReload() && sessionStorage.getItem(NAV_FLAG_KEY)
  sessionStorage.removeItem(NAV_FLAG_KEY)

  if (hasNavFlag) {
    const restored = restoreScrollState()
    if (restored) {
      loading.value = false
      if (externalSuggestions.value.length === 0) loadExternalSuggestions()
      nextTick(() => {
        restoreScrollPosition()
        setupObserver()
        setupCardObserver()
      })
      return
    }
  }
  // 首次加载 / 浏览器刷新 / 无缓存：清除旧缓存并请求新数据
  clearCache()
  loadFeed()
  loadExternalSuggestions()
  setupObserver()
})

// keep-alive 激活时：浏览器不保留 detach/reattach 后的滚动位置，需手动恢复
onActivated(() => {
  setupObserver()
  nextTick(setupCardObserver)
  // 重新绑定 scroll 追踪
  if (pageMain.value) pageMain.value.addEventListener('scroll', trackScroll, { passive: true })

  const targetScroll = parseInt(sessionStorage.getItem(SCROLL_KEY) || '0', 10)
  if (targetScroll > 0 && pageMain.value) {
    const doRestore = (attempt = 0) => {
      if (!pageMain.value) return
      pageMain.value.scrollTop = targetScroll
      if (pageMain.value.scrollTop < targetScroll - 5 && attempt < 50) {
        requestAnimationFrame(() => doRestore(attempt + 1))
      }
    }
    // 立即尝试 + 两个延迟时间点兜底
    doRestore()
    requestAnimationFrame(() => doRestore())
    setTimeout(() => doRestore(), 50)
  }
})

// keep-alive 停用时：断开 observer 避免后台触发
// 注意：不在此处调用 saveScrollState()，因为 DOM detach 后 scrollTop 已被重置为 0
// 正确的保存时机是 onBeforeRouteLeave（DOM 还在时）和 trackScroll（实时节流保存）
onDeactivated(() => {
  if (observer) observer.disconnect()
  if (cardObserver) cardObserver.disconnect()
})

// 离开页面前保存滚动位置和已加载数据
onBeforeRouteLeave((to, from) => {
  // 只在前往文章详情页 or 视频播放页时保存（其他页面不保存，避免内存浪费）
  if (to.path.startsWith('/article/') || to.path.startsWith('/student/video-player')) {
    // 在 DOM 被 detach 之前保存滚动位置到 sessionStorage
    saveScrollState()
  } else {
    clearCache()
  }
})

// ── Pull-to-Refresh Touch Handlers ──
const onTouchStart = (e) => {
  if (isRefreshing.value) return
  const touch = e.touches[0]
  touchStartY = touch.clientY
  touchStartX = touch.clientX
  pullLocked = false
}

const onTouchMove = (e) => {
  if (isRefreshing.value) return
  const touch = e.touches[0]
  const deltaY = touch.clientY - touchStartY
  const deltaX = touch.clientX - touchStartX

  const contentWrapper = pageMain.value?.closest('.content-wrapper') || null
  const isAtTop = [pageMain.value, contentWrapper]
    .filter((element, index, items) => element && items.indexOf(element) === index)
    .every(element => element.scrollTop <= 2)
  if (!isAtTop || deltaY <= 0) {
    if (isPulling.value) {
      isPulling.value = false
      pullDistance.value = 0
    }
    return
  }

  // 首次判断方向：如果水平滑动明显大于垂直，则锁定为非下拉
  if (!pullLocked && Math.abs(deltaX) + Math.abs(deltaY) > 10) {
    if (Math.abs(deltaX) > Math.abs(deltaY) * 1.2) {
      pullLocked = true
      return
    }
  }
  if (pullLocked) return

  // 进入下拉状态，阻止浏览器默认滚动/overscroll
  if (deltaY > 5) {
    isPulling.value = true
    pullDistance.value = Math.min(deltaY * 0.55, 120)
    if (e.cancelable) e.preventDefault()
  }
}

const onTouchEnd = async () => {
  if (!isPulling.value) return
  isPulling.value = false

  if (pullDistance.value > pullThreshold) {
    isRefreshing.value = true
    pullDistance.value = pullThreshold
    await doRefresh()
    isRefreshing.value = false
  }
  pullDistance.value = 0
}

const doRefresh = async () => {
  if (loading.value) return
  if (isSearchMode.value) await doServerSearch(searchQuery.value.trim(), 1)
  else await loadFeed({ forceRefresh: true })
}

onBeforeUnmount(() => {
  if (observer) observer.disconnect()
  if (cardObserver) cardObserver.disconnect()
  if (searchTimer) clearTimeout(searchTimer)
  if (pullGestureTarget) {
    pullGestureTarget.removeEventListener('touchstart', onTouchStart)
    pullGestureTarget.removeEventListener('touchmove', onTouchMove)
    pullGestureTarget.removeEventListener('touchend', onTouchEnd)
  }
})
</script>

<style scoped>
/* =========================================
   Mobile-First Articles Feed
   Inspired by Stitch Premium Design
   ========================================= */

.articles-mobile-page {
  --app-bg: var(--xy-canvas);
  --app-surface: var(--xy-surface);
  --app-primary: var(--xy-brand-600);
  --app-text: var(--xy-ink);
  --app-text-muted: var(--xy-ink-muted);
  --app-border: var(--xy-border);
  height: 100%;
  overflow: hidden;
  background-color: var(--app-bg);
  font-family: inherit;
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
    width: 100%;
    max-width: 960px;
    margin: 0 auto;
    padding: 0 24px;
  }

  .search-bar {
    margin-bottom: 8px;
  }

  .articles-feed {
    display: grid;
    grid-template-columns: repeat(2, 1fr);
    grid-template-rows: repeat(3, 320px);
    gap: 24px;
    padding: 0;
    align-content: start;
  }

  .bottom-spacer {
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

.articles-intro { margin: 16px 12px 2px; }
@media (min-width: 768px) { .articles-intro { margin: 20px 0 6px; } }

.learning-actions { display: grid; grid-template-columns: repeat(auto-fit, minmax(220px, 1fr)); gap: 10px; padding: 12px; }
.learning-action { display: flex; flex-direction: column; gap: 5px; min-width: 0; padding: 14px 16px; border: 1px solid var(--xy-border); border-radius: 16px; background: var(--xy-surface); color: var(--xy-ink); text-align: left; cursor: pointer; }
.learning-action span { color: var(--xy-brand-700); font-size: 12px; font-weight: 700; }
.learning-action strong { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; font-size: 15px; }
.learning-action small { color: var(--xy-ink-muted); font-size: 12px; }
.onboarding-action { background: var(--xy-brand-50); }
.external-explore { margin: 12px; padding: 15px; border: 1px solid var(--xy-border); border-radius: 16px; background: var(--xy-surface); }
.external-heading { display: flex; justify-content: space-between; gap: 8px; align-items: baseline; margin-bottom: 10px; }
.external-heading strong { font-size: 15px; color: var(--xy-ink); }
.external-heading span { font-size: 11px; color: var(--xy-ink-muted); }
.external-items { display: grid; grid-template-columns: repeat(auto-fit, minmax(190px, 1fr)); gap: 9px; }
.external-item { display: flex; flex-direction: column; gap: 5px; min-width: 0; border: 1px solid var(--xy-border); border-radius: 11px; padding: 11px; background: var(--xy-canvas); text-align: left; cursor: pointer; }
.external-item span, .external-item small { color: var(--xy-ink-muted); font-size: 11px; }
.external-item strong { color: var(--xy-ink); font-size: 13px; line-height: 1.4; }


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

.icon-btn:hover { background: var(--xy-surface-tint); color: var(--xy-ink); }

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
  background: rgba(246, 250, 248, 0.95);
  backdrop-filter: blur(8px);
  -webkit-backdrop-filter: blur(8px);
  border-bottom: 1px solid var(--xy-border);
  padding: 6px 0;
}

.search-bar {
  padding: 12px 12px 0;
}

.search-bar :deep(.el-input__wrapper) {
  border-radius: 999px !important;
  box-shadow: 0 2px 8px rgba(0,0,0,0.04) !important;
}

.search-result-hint {
  text-align: center;
  font-size: 12px;
  color: #909399;
  padding: 6px 0 2px;
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
  .filter-scroll { justify-content: center; gap: 12px; }
  .filter-pill { padding: 6px 24px; font-size: 14px; font-weight: 500; }
}
.filter-scroll::-webkit-scrollbar { display: none; }

.filter-pill {
  flex-shrink: 0;
  padding: 5px 12px;
  border-radius: 999px;
  border: 1px solid var(--xy-border);
  background: var(--xy-surface);
  color: var(--xy-ink-muted);
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s;
}

.filter-pill:active { transform: scale(0.96); }

.filter-pill,
.article-card {
  -webkit-tap-highlight-color: transparent;
  touch-action: manipulation;
}

.filter-pill.active {
  background: var(--xy-brand-600);
  color: white;
  border-color: var(--xy-brand-600);
  border-radius: 50px;
  box-shadow: 0 4px 16px rgba(23, 110, 81, 0.24);
}

/* --- Feed --- */
.page-main {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  scrollbar-gutter: stable;
  -webkit-overflow-scrolling: touch;
  position: relative;
  overflow-x: hidden;
  overscroll-behavior-y: none;
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
  background: var(--xy-surface);
  border-radius: var(--xy-radius-md);
  overflow: hidden;
  box-shadow: var(--xy-shadow-card);
  transition: transform 0.2s, box-shadow 0.2s;
  cursor: pointer;
  display: flex;
  flex-direction: column;
  border: 1px solid var(--xy-border);
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
  color: var(--xy-ink);
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

.feed-reason { margin: 9px 0 0; color: var(--xy-brand-700); font-size: 12px; line-height: 1.4; }

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
  color: var(--xy-brand-700);
  background: var(--xy-brand-50);
}

/* Footer Insights */
.article-footer {
  padding: 0 12px 12px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.card-dismiss { border: 0; background: transparent; color: var(--xy-ink-muted); font-size: 11px; cursor: pointer; white-space: nowrap; }

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

.empty-title { margin: 0 0 8px; font-size: 18px; color: var(--xy-ink); }
.empty-desc { margin: 0 0 24px; font-size: 14px; color: var(--xy-ink-muted); }

.refresh-btn {
  background: white;
  border: 1px solid var(--xy-border);
  color: var(--xy-ink);
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

@keyframes spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

/* --- Infinite Scroll --- */
.scroll-sentinel {
  display: flex;
  justify-content: center;
  padding: 16px 0;
}

/* --- Pull-to-Refresh Indicator --- */
.pull-refresh-indicator {
  position: absolute;
  top: -50px;
  left: 0;
  right: 0;
  height: 50px;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: transform 0.3s ease;
  pointer-events: none;
  z-index: 10;
}

.pull-refresh-indicator.is-pulling {
  transition: none;
}

.pull-refresh-content {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--xy-ink-muted);
  font-size: 13px;
  font-weight: 500;
}

.pull-refresh-arrow {
  display: inline-block;
  font-size: 16px;
  transition: transform 0.3s ease;
}

.pull-refresh-spinner {
  width: 18px;
  height: 18px;
  border: 2px solid var(--xy-border);
  border-top-color: var(--xy-brand-600);
  border-radius: 50%;
  animation: spin 0.6s linear infinite;
}

.pull-refresh-text {
  user-select: none;
}

.load-more-hint {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #94a3b8;
  font-size: 13px;
}

.load-more-end {
  color: #cbd5e1;
}

.load-more-spinner {
  width: 16px;
  height: 16px;
  border: 2px solid var(--xy-border);
  border-top-color: var(--xy-brand-600);
  border-radius: 50%;
  animation: spin 0.6s linear infinite;
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
  background: var(--xy-gradient-brand);
  color: white;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  box-shadow: var(--shadow-brand);
  transition: transform 0.2s ease, box-shadow 0.2s ease;
  -webkit-tap-highlight-color: transparent;
  touch-action: manipulation;
}

.fab-refresh:hover:not(:disabled) {
  transform: translateY(-2px) scale(1.05);
  box-shadow: 0 16px 34px rgba(23, 110, 81, 0.25);
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
