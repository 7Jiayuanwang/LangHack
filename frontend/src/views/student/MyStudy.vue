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

    <!-- Reading History Tab -->
    <div class="tab-content">
      <!-- Overview Stats -->
      <div v-if="overview" class="stats-row">
        <div class="stat-item">
          <span class="stat-value">{{ overview.totalArticlesRead || 0 }}</span>
          <span class="stat-label">阅读篇数</span>
        </div>
        <div class="stat-item">
          <span class="stat-value">{{ overview.totalReadingTimes || 0 }}</span>
          <span class="stat-label">阅读次数</span>
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
          v-for="item in paginatedHistory"
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
      <!-- 分页 -->
      <div v-if="historyTotalPages > 1" class="pagination">
        <button class="page-btn" :disabled="historyPage === 1" @click="historyPage--">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="15,18 9,12 15,6"/></svg>
        </button>
        <button
          v-for="p in historyPageList"
          :key="p"
          class="page-btn page-number"
          :class="{ active: historyPage === p }"
          @click="historyPage = p"
        >{{ p }}</button>
        <button class="page-btn" :disabled="historyPage === historyTotalPages" @click="historyPage++">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="9,18 15,12 9,6"/></svg>
        </button>
        <div class="page-jump">
          <input
            v-model="historyJumpInput"
            type="number"
            min="1"
            :max="historyTotalPages"
            class="page-jump-input"
            @keyup.enter="jumpHistoryPage"
            placeholder="页"
          />
          <button class="page-btn page-jump-btn" @click="jumpHistoryPage">跳转</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, watch, computed, markRaw } from 'vue'
import { useRouter } from 'vue-router'
import {
  Clock, Timer, Reading, Loading,
  House, Calendar, Trophy, Tickets, Notebook,
  VideoPlay, MessageBox, Edit, User
} from '@element-plus/icons-vue'
import { getReadingHistory } from '@/api/student'
import { useFeatureToggles } from '@/composables/useFeatureToggles'

const router = useRouter()

// 聚合被移出主导航的子页面入口（按优先级排序）
// 注：发现页已在底部导航栏首位强制显示，不再重复列出
const QUICK_ENTRIES = [
  { key: 'assignments',       path: '/student/assignments',       label: '任务',      icon: markRaw(Edit),      color: '#f97316', toggleKey: 'page_assignments' },
  { key: 'classes',           path: '/student/classes',           label: '社区',      icon: markRaw(House),     color: '#14b8a6', toggleKey: 'page_classes' },
  { key: 'schedule',          path: '/student/schedule',          label: '课表',      icon: markRaw(Calendar),  color: '#6366f1', toggleKey: 'page_schedule' },
  { key: 'scores',            path: '/student/scores',            label: '成绩',      icon: markRaw(Trophy),    color: '#f59e0b', toggleKey: 'page_scores' },
  { key: 'course_selections', path: '/student/course-selections', label: '选课',      icon: markRaw(Tickets),   color: '#8b5cf6', toggleKey: 'page_course_selections' },
  { key: 'user_texts',        path: '/student/user-texts',        label: '我的创作',   icon: markRaw(Notebook),  color: '#ec4899', toggleKey: 'page_user_texts' },
  { key: 'ai_video',          path: '/student/ai-video',          label: 'AI 视频',   icon: markRaw(VideoPlay), color: '#ef4444', toggleKey: 'page_ai_video' },
  { key: 'ai_qa',             path: '/student/ai-qa',             label: 'AI 问答',   icon: markRaw(MessageBox),color: '#10b981', toggleKey: 'page_ai_qa' },
  { key: 'my_profile',        path: '/student/my-profile',        label: '我的画像',  icon: markRaw(User),      color: '#7c3aed', toggleKey: 'student.learner_profile_enabled' },
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

const PAGE_SIZE = 5

const history = ref([])
const historyLoading = ref(false)
const historyPage = ref(1)
const historyTotal = ref(0)
const overview = ref(null)

const historyTotalPages = computed(() => Math.ceil(historyTotal.value / PAGE_SIZE) || 1)
const PAGE_WINDOW = 5
const buildPageList = (total, current) => {
  if (total <= PAGE_WINDOW) return Array.from({ length: total }, (_, i) => i + 1)
  let start = current - Math.floor(PAGE_WINDOW / 2)
  start = Math.max(1, Math.min(start, total - PAGE_WINDOW + 1))
  return Array.from({ length: PAGE_WINDOW }, (_, i) => start + i)
}
const historyPageList = computed(() => buildPageList(historyTotalPages.value, historyPage.value))
const historyJumpInput = ref('')
const jumpHistoryPage = () => {
  const n = parseInt(historyJumpInput.value, 10)
  if (!Number.isFinite(n)) { historyJumpInput.value = ''; return }
  historyPage.value = Math.max(1, Math.min(n, historyTotalPages.value))
  historyJumpInput.value = ''
}

const paginatedHistory = computed(() => history.value)

const loadHistory = async () => {
  historyLoading.value = true
  try {
    const res = await getReadingHistory({ page: historyPage.value, size: PAGE_SIZE })
    if (res?.code === 200 && res?.data) {
      history.value = res.data.records || []
      historyTotal.value = res.data.total || 0
      overview.value = res.data.overview || null
    }
  } catch (e) {
    console.error('加载阅读记录失败:', e)
  } finally {
    historyLoading.value = false
  }
}

const readArticle = (textId) => {
  router.push({ path: `/article/${textId}`, query: { from: 'my-study' } })
}

const getDifficultyText = (difficulty) => {
  return ({ EASY: '简单', MEDIUM: '中等', HARD: '困难' })[difficulty] || difficulty || '未知'
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

watch(historyPage, () => loadHistory())

onMounted(() => {
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
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 12px;
  margin-bottom: 20px;
}

@media (min-width: 640px) {
  .stats-row {
    grid-template-columns: repeat(4, 1fr);
  }
}

.stat-item {
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

/* 分页 */
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
  background: #fff;
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
  color: #fff;
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

.page-number { font-weight: 600; }
</style>
