<template>
  <div class="my-study-page">
    <!-- Quick Entries Grid: 聚合被移出主导航的子页面 -->
    <section v-if="visibleEntries.length > 0" class="quick-entries">
      <h3 class="section-title">快捷入口</h3>
      <div class="entries-grid">
        <button
          v-for="entry in visibleEntries"
          :key="entry.key"
          class="entry-card"
          :style="{ '--entry-color': entry.color }"
          @click="router.push(entry.path)"
        >
          <div class="entry-icon">
            <el-icon :size="20"><component :is="entry.icon" /></el-icon>
          </div>
          <span class="entry-label">{{ entry.label }}</span>
        </button>
      </div>
    </section>

    <!-- Tab Selector -->
    <div class="tab-bar">
      <button
        v-for="tab in tabs"
        :key="tab.key"
        class="tab-btn"
        :class="{ active: activeTab === tab.key }"
        @click="activeTab = tab.key"
      >
        <el-icon><component :is="tab.icon" /></el-icon>
        <span>{{ tab.label }}</span>
        <span v-if="tab.count !== undefined" class="tab-count">{{ tab.count }}</span>
      </button>
    </div>

    <!-- Favorites Tab -->
    <div v-if="activeTab === 'favorites'" class="tab-content">
      <div v-if="favLoading" class="loading-state">
        <el-icon class="is-loading" :size="32"><Loading /></el-icon>
        <p>加载中...</p>
      </div>
      <div v-else-if="favorites.length === 0" class="empty-state">
        <el-icon :size="48"><StarFilled /></el-icon>
        <h3>暂无收藏</h3>
        <p>阅读文章时点击星标即可收藏</p>
      </div>
      <div v-else class="article-list">
        <div
          v-for="item in favorites"
          :key="item.id"
          class="article-card"
          @click="readArticle(item.textId)"
        >
          <div class="card-body">
            <h3 class="card-title">{{ item.title }}</h3>
            <p class="card-snippet">{{ item.snippet }}</p>
          </div>
          <div class="card-footer">
            <span class="difficulty-tag" :class="item.difficulty">
              {{ getDifficultyText(item.difficulty) }}
            </span>
            <span class="meta-info">
              <el-icon><Clock /></el-icon>
              {{ formatDate(item.favoritedAt) }}
            </span>
          </div>
        </div>
      </div>
    </div>

    <!-- Reading History Tab -->
    <div v-if="activeTab === 'history'" class="tab-content">
      <!-- Overview Stats -->
      <div v-if="overview" class="stats-row">
        <div class="stat-item">
          <span class="stat-value">{{ overview.totalArticlesRead || 0 }}</span>
          <span class="stat-label">阅读篇数</span>
        </div>
        <div class="stat-item">
          <span class="stat-value">{{ overview.completedArticles || 0 }}</span>
          <span class="stat-label">完成篇数</span>
        </div>
        <div class="stat-item">
          <span class="stat-value">{{ overview.favoriteCount || 0 }}</span>
          <span class="stat-label">收藏篇数</span>
        </div>
      </div>

      <div v-if="historyLoading" class="loading-state">
        <el-icon class="is-loading" :size="32"><Loading /></el-icon>
        <p>加载中...</p>
      </div>
      <div v-else-if="history.length === 0" class="empty-state">
        <el-icon :size="48"><Reading /></el-icon>
        <h3>暂无阅读记录</h3>
        <p>去文章页开始阅读吧</p>
      </div>
      <div v-else class="article-list">
        <div
          v-for="item in history"
          :key="item.textId"
          class="article-card"
          @click="readArticle(item.textId)"
        >
          <div class="card-body">
            <h3 class="card-title">
              {{ item.title }}
              <el-tag v-if="item.isCompleted" size="small" type="success" class="complete-tag">已完成</el-tag>
            </h3>
          </div>
          <div class="card-footer">
            <span class="difficulty-tag" :class="item.difficulty">
              {{ getDifficultyText(item.difficulty) }}
            </span>
            <span class="meta-info">
              <el-icon><Timer /></el-icon>
              {{ formatDuration(item.totalDuration) }}
            </span>
            <span class="meta-info">
              阅读{{ item.readCount || 1 }}次
            </span>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, watch, computed, markRaw } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  StarFilled, Clock, Timer, Reading, Loading,
  DataBoard, House, Calendar, Trophy, Tickets, Notebook,
  VideoPlay, MessageBox
} from '@element-plus/icons-vue'
import { getFavoriteArticles, getReadingHistory } from '@/api/student'
import { useFeatureToggles } from '@/composables/useFeatureToggles'

const router = useRouter()
const activeTab = ref('favorites')

// 聚合被移出主导航的子页面入口（按优先级排序）
const QUICK_ENTRIES = [
  { key: 'dashboard',         path: '/student/dashboard',         label: '发现',      icon: markRaw(DataBoard), color: '#0ea5e9', toggleKey: 'page_dashboard' },
  { key: 'classes',           path: '/student/classes',           label: '社区',      icon: markRaw(House),     color: '#14b8a6', toggleKey: 'page_classes' },
  { key: 'schedule',          path: '/student/schedule',          label: '课表',      icon: markRaw(Calendar),  color: '#6366f1', toggleKey: 'page_schedule' },
  { key: 'scores',            path: '/student/scores',            label: '成绩',      icon: markRaw(Trophy),    color: '#f59e0b', toggleKey: 'page_scores' },
  { key: 'course_selections', path: '/student/course-selections', label: '选课',      icon: markRaw(Tickets),   color: '#8b5cf6', toggleKey: 'page_course_selections' },
  { key: 'user_texts',        path: '/student/user-texts',        label: '我的创作',   icon: markRaw(Notebook),  color: '#ec4899', toggleKey: 'page_user_texts' },
  { key: 'ai_video',          path: '/student/ai-video',          label: 'AI 视频',   icon: markRaw(VideoPlay), color: '#ef4444', toggleKey: 'page_ai_video' },
  { key: 'ai_qa',             path: '/student/ai-qa',             label: 'AI 问答',   icon: markRaw(MessageBox),color: '#10b981', toggleKey: 'page_ai_qa' }
]

const { toggles: featureToggles, load: loadFeatureToggles, isLoaded } = useFeatureToggles()

const isEntryEnabled = (key) => {
  if (!key) return true
  const v = featureToggles.value[key]
  return v === undefined || v === true || v === 'true'
}

const visibleEntries = computed(() =>
  QUICK_ENTRIES.filter(e => isEntryEnabled(e.toggleKey))
)

const tabs = ref([
  { key: 'favorites', label: '我的收藏', icon: StarFilled, count: undefined },
  { key: 'history', label: '阅读记录', icon: Reading, count: undefined }
])

const favorites = ref([])
const favLoading = ref(false)
const history = ref([])
const historyLoading = ref(false)
const overview = ref(null)

const loadFavorites = async () => {
  favLoading.value = true
  try {
    const res = await getFavoriteArticles({ page: 1, size: 50 })
    if (res?.code === 200 && res?.data) {
      favorites.value = res.data.records || []
      tabs.value[0].count = res.data.total || 0
    }
  } catch (e) {
    console.error('加载收藏失败:', e)
  } finally {
    favLoading.value = false
  }
}

const loadHistory = async () => {
  historyLoading.value = true
  try {
    const res = await getReadingHistory({ page: 1, size: 50 })
    if (res?.code === 200 && res?.data) {
      history.value = res.data.records || []
      overview.value = res.data.overview || null
      if (overview.value) {
        tabs.value[1].count = overview.value.totalArticlesRead || 0
      }
    }
  } catch (e) {
    console.error('加载阅读记录失败:', e)
  } finally {
    historyLoading.value = false
  }
}

const readArticle = (textId) => {
  router.push({ path: '/student/text-learning', query: { textId, from: 'my-study' } })
}

const getDifficultyText = (diff) => {
  const map = { EASY: '简单', MEDIUM: '中等', HARD: '困难' }
  return map[diff] || diff || '未知'
}

const formatDate = (dateStr) => {
  if (!dateStr) return ''
  const d = new Date(dateStr)
  return `${d.getMonth() + 1}/${d.getDate()}`
}

const formatDuration = (seconds) => {
  if (!seconds || seconds <= 0) return '0分钟'
  const h = Math.floor(seconds / 3600)
  const m = Math.floor((seconds % 3600) / 60)
  if (h > 0) return `${h}小时${m}分钟`
  return `${m}分钟`
}

watch(activeTab, (tab) => {
  if (tab === 'favorites' && favorites.value.length === 0) loadFavorites()
  if (tab === 'history' && history.value.length === 0) loadHistory()
})

onMounted(() => {
  loadFavorites()
  loadHistory()
  // 共享 feature-toggles 缓存（路由守卫与 Layout 已在加载；此处仅兜底）
  if (!isLoaded()) {
    loadFeatureToggles({ personal: true }).catch(() => {})
  }
})
</script>

<style scoped>
.my-study-page {
  max-width: 1200px;
  margin: 0 auto;
  padding: 16px;
  min-height: 80vh;
}

.quick-entries {
  margin-bottom: 20px;
}

.section-title {
  margin: 0 0 10px;
  font-size: 15px;
  font-weight: 600;
  color: #0f172a;
  display: flex;
  align-items: center;
  gap: 6px;
}

.entries-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 10px;
}

.entry-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 14px 6px;
  background: #fff;
  border: 1px solid #f1f5f9;
  border-radius: 12px;
  cursor: pointer;
  transition: transform 0.15s, box-shadow 0.15s, border-color 0.15s;
}

.entry-card:hover {
  transform: translateY(-2px);
  border-color: var(--entry-color, #cbd5e1);
  box-shadow: 0 4px 14px rgba(0, 0, 0, 0.06);
}

.entry-card:active {
  transform: translateY(0);
}

.entry-icon {
  width: 40px;
  height: 40px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  background: color-mix(in srgb, var(--entry-color, #64748b) 12%, transparent);
  color: var(--entry-color, #64748b);
}

.entry-label {
  font-size: 12px;
  font-weight: 500;
  color: #334155;
}

@media (max-width: 480px) {
  .entries-grid {
    grid-template-columns: repeat(4, 1fr);
    gap: 8px;
  }
  .entry-card {
    padding: 12px 4px;
  }
  .entry-icon {
    width: 36px;
    height: 36px;
  }
  .entry-label {
    font-size: 11px;
  }
}

.tab-bar {
  display: flex;
  gap: 8px;
  margin-bottom: 20px;
  padding: 4px;
  background: #f1f5f9;
  border-radius: 12px;
}

.tab-btn {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 10px 16px;
  border: none;
  background: transparent;
  border-radius: 10px;
  font-size: 14px;
  font-weight: 500;
  color: #64748b;
  cursor: pointer;
  transition: all 0.2s;
}

.tab-btn.active {
  background: #fff;
  color: #0f172a;
  box-shadow: 0 1px 4px rgba(0,0,0,0.08);
}

.tab-count {
  font-size: 11px;
  background: rgba(0,0,0,0.06);
  padding: 1px 6px;
  border-radius: 10px;
}

.tab-btn.active .tab-count {
  background: rgba(19, 127, 236, 0.1);
  color: #137fec;
}

.stats-row {
  display: flex;
  gap: 12px;
  margin-bottom: 20px;
}

.stat-item {
  flex: 1;
  text-align: center;
  background: linear-gradient(135deg, #f0f9ff, #e0f2fe);
  padding: 16px 8px;
  border-radius: 12px;
}

.stat-value {
  display: block;
  font-size: 24px;
  font-weight: 700;
  color: #0369a1;
}

.stat-label {
  font-size: 12px;
  color: #64748b;
  margin-top: 4px;
}

.article-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.article-card {
  background: #fff;
  border-radius: 14px;
  padding: 16px;
  border: 1px solid #f1f5f9;
  cursor: pointer;
  transition: all 0.2s;
}

.article-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 16px rgba(0,0,0,0.06);
}

.card-title {
  margin: 0 0 6px;
  font-size: 16px;
  font-weight: 600;
  color: #0f172a;
  display: flex;
  align-items: center;
  gap: 8px;
}

.complete-tag {
  flex-shrink: 0;
}

.card-snippet {
  margin: 0;
  font-size: 13px;
  color: #64748b;
  line-height: 1.5;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.card-footer {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: 12px;
  padding-top: 10px;
  border-top: 1px solid #f8fafc;
}

.difficulty-tag {
  font-size: 11px;
  font-weight: 600;
  padding: 2px 8px;
  border-radius: 6px;
  text-transform: uppercase;
}

.difficulty-tag.EASY { background: rgba(16, 185, 129, 0.1); color: #047857; }
.difficulty-tag.MEDIUM { background: rgba(245, 158, 11, 0.1); color: #b45309; }
.difficulty-tag.HARD { background: rgba(239, 68, 68, 0.1); color: #b91c1c; }

.meta-info {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: #94a3b8;
}

.loading-state,
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 60px 20px;
  color: #94a3b8;
  text-align: center;
}

.empty-state h3 {
  margin: 16px 0 8px;
  font-size: 16px;
  color: #475569;
}

.empty-state p {
  margin: 0;
  font-size: 13px;
  color: #94a3b8;
}
</style>
