<template>
  <div class="vocabulary-container">

    <!-- ─── 统计卡片 ─── -->
    <div class="stats-cards">
      <div class="stat-card total">
        <div class="stat-icon"><el-icon><Collection /></el-icon></div>
        <div class="stat-info">
          <span class="stat-label">总词汇</span>
          <span class="stat-value">{{ stats.totalWords }}</span>
        </div>
      </div>
      <div class="stat-card new">
        <div class="stat-icon"><el-icon><Sunny /></el-icon></div>
        <div class="stat-info">
          <span class="stat-label">新词</span>
          <span class="stat-value">{{ stats.newWords }}</span>
        </div>
      </div>
      <div class="stat-card learning">
        <div class="stat-icon"><el-icon><Reading /></el-icon></div>
        <div class="stat-info">
          <span class="stat-label">学习中</span>
          <span class="stat-value">{{ stats.learningWords }}</span>
        </div>
      </div>
      <div class="stat-card mastered">
        <div class="stat-icon"><el-icon><Check /></el-icon></div>
        <div class="stat-info">
          <span class="stat-label">已掌握</span>
          <span class="stat-value">{{ stats.masteredWords }}</span>
        </div>
      </div>
    </div>

    <!-- ─── 操作栏 ─── -->
    <div class="action-bar">
      <!-- 筛选 chips + 搜索框 -->
      <div class="filter-row">
        <div class="filter-chips">
          <span
            class="filter-chip"
            :class="{ active: !filterForm.masteryLevel }"
            @click="setFilter('')"
          >全部</span>
          <span
            class="filter-chip chip-new"
            :class="{ active: filterForm.masteryLevel === 'NEW' }"
            @click="setFilter('NEW')"
          >新词</span>
          <span
            class="filter-chip chip-learning"
            :class="{ active: filterForm.masteryLevel === 'LEARNING' }"
            @click="setFilter('LEARNING')"
          >学习中</span>
          <span
            class="filter-chip chip-familiar"
            :class="{ active: filterForm.masteryLevel === 'FAMILIAR' }"
            @click="setFilter('FAMILIAR')"
          >熟悉</span>
          <span
            class="filter-chip chip-mastered"
            :class="{ active: filterForm.masteryLevel === 'MASTERED' }"
            @click="setFilter('MASTERED')"
          >已掌握</span>
        </div>
        <el-input
          v-model="searchWord"
          placeholder="搜索单词…"
          clearable
          class="search-input"
          @keyup.enter="handleSearch"
          @clear="handleSearch"
        >
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
      </div>

      <!-- 二级操作按钮行 -->
      <div class="actions-row">
        <el-button class="icon-btn-text" @click="showSettings = true">
          <el-icon><Setting /></el-icon> SM-2 设置
        </el-button>
        <el-button
          class="icon-btn-text"
          :type="showAnalytics ? 'primary' : ''"
          :plain="showAnalytics"
          @click="toggleAnalytics"
        >
          <el-icon><TrendCharts /></el-icon>
          {{ showAnalytics ? '隐藏分析' : '数据分析' }}
        </el-button>
        <el-dropdown trigger="click" @command="handleSort">
          <el-button class="icon-btn-text">
            <el-icon><Sort /></el-icon> {{ getSortLabel(filterForm.sortBy) }}
          </el-button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="addedTime">按添加时间</el-dropdown-item>
              <el-dropdown-item command="reviewCount">按复习次数</el-dropdown-item>
              <el-dropdown-item command="word">按字母顺序</el-dropdown-item>
              <el-dropdown-item divided command="word-test">
                <el-icon><Finished /></el-icon> 单词测试
              </el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
        <el-select v-model="reviewBatchSize" size="small" style="width: 100px">
          <el-option :value="10" label="10个/组" />
          <el-option :value="20" label="20个/组" />
          <el-option :value="30" label="30个/组" />
          <el-option :value="50" label="50个/组" />
        </el-select>
        <el-button
          type="primary"
          round
          class="start-btn"
          :disabled="vocabulary.length === 0"
          @click="startReview"
        >
          <el-icon><VideoPlay /></el-icon> 开始复习
        </el-button>
        <el-button
          v-if="selectedWords.length > 0"
          type="danger"
          plain
          round
          @click="batchDelete"
        >
          删除 ({{ selectedWords.length }})
        </el-button>
      </div>
    </div>

    <!-- ─── 数据分析面板（可收起） ─── -->
    <transition name="slide-fade">
      <div v-if="showAnalytics && stats.totalWords > 0" class="analytics-panels">
        <SM2AnalyticsDashboard :words="vocabularyForAnalytics" :show-actions="false" />
        <SM2ComparisonPanel
          :words="vocabularyForAnalytics"
          @apply="onComparisonApplied"
          @open-settings="showSettings = true"
        />
      </div>
    </transition>

    <!-- ─── 词汇卡片网格 ─── -->
    <div class="vocabulary-grid" v-loading="loading">
      <div
        v-for="item in vocabulary"
        :key="item.id"
        class="word-card"
        :class="{ 'card-selected': selectedWords.includes(item.wordId || item.id) }"
        @click="showWordDetail(item)"
      >
        <!-- 多选框 -->
        <div class="card-checkbox" @click.stop>
          <el-checkbox
            v-model="selectedWords"
            :label="item.wordId || item.id"
            @change="val => handleItemSelect(item.wordId || item.id, val)"
          ><span></span></el-checkbox>
        </div>

        <!-- 单词 + 掌握程度 -->
        <div class="card-top">
          <h3 class="word-text">{{ item.word }}</h3>
          <span
            class="level-badge"
            :class="(item.masteryLevel || 'NEW').toLowerCase()"
          >{{ getMasteryText(item.masteryLevel) }}</span>
        </div>

        <!-- 音标 -->
        <div class="phonetic" v-if="item.pronunciation">
          [{{ item.pronunciation }}]
          <el-button link size="small" class="audio-btn" @click.stop="playAudio(item)">
            <el-icon><Microphone /></el-icon>
          </el-button>
        </div>

        <!-- 释义 -->
        <p class="translation">{{ item.definition }}</p>

        <!-- 底部：复习信息 + 快捷操作 -->
        <div class="card-footer">
          <div class="footer-meta">
            <span class="review-count">复习 {{ item.reviewCount || 0 }} 次</span>
            <span v-if="isWordOverdue(item)" class="due-badge">需复习</span>
          </div>
          <div class="footer-actions">
            <el-button
              v-if="item.masteryLevel !== 'MASTERED'"
              circle
              size="small"
              type="success"
              @click.stop="markAsLearned(item)"
            >
              <el-icon><Check /></el-icon>
            </el-button>
            <el-button circle size="small" plain @click.stop="deleteWord(item)">
              <el-icon><Delete /></el-icon>
            </el-button>
          </div>
        </div>
      </div>

      <!-- 空状态 -->
      <div v-if="!loading && vocabulary.length === 0" class="empty-state">
        <el-empty description="暂时没有生词哦" :image-size="120">
          <el-button
            v-if="isEnabled('page_courses')"
            type="primary"
            round
            @click="$router.push('/student/courses')"
          >去探索新词</el-button>
        </el-empty>
      </div>
    </div>

    <!-- ─── 分页（移动端 or 总数超出 pageSize） ─── -->
    <div class="pagination-bar" v-if="total > pageSize">
      <el-pagination
        v-model:current-page="currentPage"
        v-model:page-size="pageSize"
        small
        background
        layout="prev, pager, next"
        :total="total"
        @current-change="loadVocabulary"
      />
    </div>

    <!-- ─── 单词详情弹窗 ─── -->
    <el-dialog v-model="wordDialogVisible" title="单词详解" width="min(500px, 92vw)">
      <div class="word-detail" v-if="selectedWord">
        <div class="word-header">
          <h2>{{ selectedWord.word }}</h2>
          <el-button @click="playAudio(selectedWord)" :disabled="!selectedWord.audioUrl">
            <el-icon><VideoPlay /></el-icon> 播放
          </el-button>
        </div>
        <div class="word-content">
          <p class="pronunciation">{{ selectedWord.pronunciation }}</p>
          <div class="definition">{{ selectedWord.definition }}</div>
          <div class="example-sentence" v-if="selectedWord.exampleSentence">
            <span class="example-label">例句：</span>{{ selectedWord.exampleSentence }}
          </div>
          <div class="word-meta-info">
            <el-tag>{{ selectedWord.partOfSpeech }}</el-tag>
            <el-tag :type="getDifficultyType(selectedWord.difficulty)">
              {{ getDifficultyText(selectedWord.difficulty) }}
            </el-tag>
            <el-tag :type="getMasteryType(selectedWord.masteryLevel)">
              {{ getMasteryText(selectedWord.masteryLevel) }}
            </el-tag>
          </div>
        </div>
        <div class="learning-stats" v-if="selectedWord.reviewCount !== undefined">
          <h4>学习统计</h4>
          <div class="stats-grid">
            <div class="stat-item">
              <span class="label">复习次数：</span>
              <span class="value">{{ selectedWord.reviewCount }}</span>
            </div>
            <div class="stat-item">
              <span class="label">添加时间：</span>
              <span class="value">{{ formatDate(selectedWord.addedTime) }}</span>
            </div>
            <div class="stat-item" v-if="selectedWord.lastReviewed">
              <span class="label">最后复习：</span>
              <span class="value">{{ formatDate(selectedWord.lastReviewed) }}</span>
            </div>
            <div class="stat-item" v-if="selectedWord.nextReviewTime">
              <span class="label">下次复习：</span>
              <span class="value">{{ formatDate(selectedWord.nextReviewTime) }}</span>
            </div>
          </div>
        </div>
        <div class="word-actions">
          <el-button
            v-if="selectedWord.masteryLevel !== 'MASTERED'"
            @click="markAsLearned(selectedWord)"
          >标记为已掌握</el-button>
          <el-button
            v-if="selectedWord.masteryLevel === 'MASTERED'"
            type="warning"
            @click="resetToLearning(selectedWord)"
          >重置为学习中</el-button>
          <el-button type="danger" @click="deleteWord(selectedWord)">删除单词</el-button>
        </div>
      </div>
    </el-dialog>

    <!-- ─── 复习对话框 ─── -->
    <el-dialog
      v-model="reviewDialogVisible"
      title="生词复习"
      width="min(800px, 96vw)"
      :close-on-click-modal="false"
      @close="onReviewDialogClose"
    >
      <div class="review-session" v-if="reviewWords.length > 0">
        <div class="review-progress">
          <div class="progress-info">
            <span>进度：{{ currentReviewIndex + 1 }} / {{ reviewWords.length }}</span>
            <span v-if="currentReviewWord?.nextReviewTime" class="review-schedule-hint">
              <template v-if="isWordOverdue(currentReviewWord)">需要复习</template>
              <template v-else>提前复习</template>
            </span>
            <span v-else class="review-schedule-hint">首次复习</span>
          </div>
          <el-progress :percentage="reviewProgress" />
        </div>

        <div class="word-card-v3">
          <!-- 阶段一：认读选择 -->
          <div
            class="recognition-phase"
            v-if="reviewPhase === PHASE.RECOGNITION && currentReviewWord"
          >
            <h2 class="word">{{ currentReviewWord.word }}</h2>
            <div class="mcq-options">
              <el-button
                v-for="(option, index) in mcqOptions"
                :key="index"
                class="mcq-option-btn"
                :class="{
                  'is-correct': isAnswering && option === (currentCorrectAnswer || currentReviewWord.definition),
                  'is-incorrect': isAnswering && selectedOption === option && option !== (currentCorrectAnswer || currentReviewWord.definition)
                }"
                @click="handleMcqSelection(option)"
              >{{ option }}</el-button>
              <el-button
                type="info"
                plain
                class="mcq-option-btn unknown-btn"
                @click="handleUnknown"
              >我不知道</el-button>
            </div>
          </div>

          <!-- 阶段二：强化记忆 & 调整 -->
          <div class="reinforcement-phase" v-else-if="currentReviewWord">
            <h2 class="word">{{ currentReviewWord.word }}</h2>
            <p class="pronunciation">{{ currentReviewWord.pronunciation }}</p>
            <div class="definition">{{ currentCorrectAnswer || currentReviewWord.definition }}</div>
            <div class="example" v-if="currentReviewWord.exampleSentence">
              <span class="example-label">例句：</span>{{ currentReviewWord.exampleSentence }}
            </div>
            <div class="mastery-adjustment">
              <p class="adjustment-tip">掌握程度（可选）：</p>
              <div class="adjustment-actions">
                <el-button type="warning" plain @click="submitReview(3)">有难度</el-button>
                <el-button type="primary" @click="submitReview(4)">一般</el-button>
                <el-button type="success" @click="submitReview(5)">太简单</el-button>
              </div>
              <el-button link class="next-btn" @click="submitReview(4)">
                跳过并继续 <el-icon><ArrowRight /></el-icon>
              </el-button>
            </div>
          </div>
        </div>
      </div>

      <!-- 复习完成 -->
      <div class="review-complete" v-else>
        <h3>{{ reviewFinishedAll ? '全部复习完成！' : '本组复习完成！' }}</h3>
        <div class="review-summary">
          <div class="summary-item">
            <span>本次复习：</span>
            <strong>{{ reviewSummary.totalWords }} 个</strong>
          </div>
          <div class="summary-item">
            <span>正确：</span>
            <strong class="get-score-known">{{ reviewSummary.correctCount }} 个</strong>
          </div>
          <div class="summary-item">
            <span>错误：</span>
            <strong :class="reviewSummary.incorrectCount > 0 ? 'get-score-unknown' : ''">
              {{ reviewSummary.incorrectCount }} 个
            </strong>
          </div>
          <div class="summary-item">
            <span>正确率：</span>
            <strong :class="getScoreClass(reviewAccuracy)">{{ reviewAccuracy }}%</strong>
          </div>
          <div class="summary-item" v-if="!reviewFinishedAll">
            <span>剩余待复习：</span>
            <strong>{{ remainingWords }} 个</strong>
          </div>
        </div>
        <div class="review-actions">
          <el-button v-if="!reviewFinishedAll" type="primary" @click="continueNextBatch">
            继续下一组
          </el-button>
          <el-button
            :type="reviewFinishedAll ? 'primary' : 'default'"
            @click="reviewDialogVisible = false"
          >{{ reviewFinishedAll ? '完成' : '休息一下' }}</el-button>
        </div>
      </div>
    </el-dialog>

    <!-- SM-2 设置抽屉 -->
    <SM2SettingsDrawer v-model="showSettings" @changed="onSettingsChanged" />
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, onBeforeUnmount, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  Collection,
  Sunny,
  Reading,
  Check,
  Sort,
  VideoPlay,
  Delete,
  Microphone,
  Finished,
  ArrowRight,
  Setting,
  Search,
  TrendCharts
} from '@element-plus/icons-vue'

import { calculateSM2 } from '@/utils/sm2'
import {
  getVocabularyList,
  getVocabularyStats,
  updateMasteryLevel,
  batchOperateVocabulary,
  getQuizData
} from '@/api/student'
import { useUserStore } from '@/store/user'
import { useFeatureToggles } from '@/composables/useFeatureToggles'
import { getSettings } from '@/utils/sm2-settings'
import SM2SettingsDrawer from '@/components/vocabulary/SM2SettingsDrawer.vue'
import SM2AnalyticsDashboard from '@/components/vocabulary/SM2AnalyticsDashboard.vue'
import SM2ComparisonPanel from '@/components/vocabulary/SM2ComparisonPanel.vue'

const { isEnabled, load: loadFeatureToggles, isLoaded: toggleLoaded } = useFeatureToggles()

const loading = ref(false)

// SM-2 设置面板
const showSettings = ref(false)
const onSettingsChanged = () => {
  loadVocabulary()
}
const onComparisonApplied = () => {
  showAnalytics.value = getSettings().showAnalytics
}

// 数据监控面板可见性
const showAnalytics = ref(getSettings().showAnalytics)

const sm2Settings = computed(() => getSettings())

const toggleAnalytics = () => {
  showAnalytics.value = !showAnalytics.value
}

// 供数据监控面板使用：转换字段名为统一格式
const vocabularyForAnalytics = computed(() =>
  (allVocabulary.value || []).map(w => ({
    word: w.word,
    masteryLevel: w.masteryLevel,
    easinessFactor: w.easinessFactor || 2.5,
    interval: w.intervalDays || w.interval || 0,
    reviewCount: w.reviewCount || w.repetition || 0,
    repetition: w.reviewCount || w.repetition || 0,
    nextReviewTime: w.nextReviewTime
  }))
)

const searchWord = ref('')
const vocabulary = ref([])
// 全量词汇缓存，仅供数据分析面板使用，不受筛选/搜索影响
const allVocabulary = ref([])
const total = ref(0)
const currentPage = ref(1)
const pageSize = ref(window.innerWidth < 768 ? 10 : 100)
const selectedWords = ref([])
const screenWidth = ref(window.innerWidth)

const isMobile = computed(() => screenWidth.value < 768)

const stats = reactive({
  totalWords: 0,
  newWords: 0,
  learningWords: 0,
  masteredWords: 0
})

const filterForm = reactive({
  masteryLevel: '',
  sortBy: 'addedTime',
  difficulty: ''
})

const wordDialogVisible = ref(false)
const selectedWord = ref(null)

const reviewDialogVisible = ref(false)
const reviewWords = ref([])
const currentReviewIndex = ref(0)
const currentReviewWord = computed(() => reviewWords.value[currentReviewIndex.value] || null)
const showAnswer = ref(false)

const reviewBatchSize = ref(20)
const allReviewableWords = ref([])
const currentBatchStart = ref(0)
const reviewFinishedAll = ref(false)

const PHASE = {
  RECOGNITION: 'RECOGNITION',
  REINFORCEMENT: 'REINFORCEMENT'
}
const reviewPhase = ref(PHASE.RECOGNITION)
const mcqOptions = ref([])
const selectedOption = ref(null)
const isAnswering = ref(false)
const mcqWasWrong = ref(false)
const currentCorrectAnswer = ref(null)
let answerTimeoutId = null

const reviewSummary = reactive({
  totalWords: 0,
  correctCount: 0,
  incorrectCount: 0
})

// ─── 数据加载 ───

const loadVocabulary = async () => {
  loading.value = true
  try {
    const params = {
      page: currentPage.value,
      size: pageSize.value,
      keyword: searchWord.value,
      masteryLevel: filterForm.masteryLevel,
      sortBy: filterForm.sortBy,
      difficulty: filterForm.difficulty
    }
    const [listResp, statsResp] = await Promise.allSettled([
      getVocabularyList(params),
      getVocabularyStats()
    ])
    if (listResp.status === 'fulfilled' && listResp.value?.code === 200) {
      vocabulary.value = Array.isArray(listResp.value.data?.records) ? listResp.value.data.records : []
      total.value = listResp.value.data?.total || 0
      // 无筛选条件时同步全量缓存
      if (!filterForm.masteryLevel && !searchWord.value && !filterForm.difficulty) {
        allVocabulary.value = vocabulary.value
      }
    }
    applyStats(statsResp.status === 'fulfilled' ? statsResp.value : null)
    // 有筛选时，单独拉全量数据供分析面板使用
    if (filterForm.masteryLevel || searchWord.value || filterForm.difficulty) {
      getVocabularyList({ page: 1, size: 100, sortBy: filterForm.sortBy }).then(r => {
        if (r?.code === 200) allVocabulary.value = Array.isArray(r.data?.records) ? r.data.records : []
      }).catch(() => {})
    }
  } catch (error) {
    console.error('加载生词本失败:', error)
    ElMessage.error('加载生词本失败')
  } finally {
    loading.value = false
  }
}

const applyStats = (res) => {
  if (res?.code === 200 && res?.data) {
    stats.totalWords = res.data.totalCount || 0
    stats.newWords = res.data.newCount || 0
    stats.learningWords = res.data.learningCount || 0
    stats.masteredWords = res.data.masteredCount || 0
  } else {
    stats.totalWords = total.value
    stats.newWords = vocabulary.value.filter(w => w.masteryLevel === 'NEW').length
    stats.learningWords = vocabulary.value.filter(w => w.masteryLevel === 'LEARNING' || w.masteryLevel === 'FAMILIAR').length
    stats.masteredWords = vocabulary.value.filter(w => w.masteryLevel === 'MASTERED').length
  }
}

// ─── 筛选 & 搜索 ───

const handleSearch = () => {
  currentPage.value = 1
  loadVocabulary()
}

const setFilter = (level) => {
  filterForm.masteryLevel = level
  currentPage.value = 1
  loadVocabulary()
}

// ─── 单词操作 ───

const showWordDetail = (word) => {
  if (!word) return
  selectedWord.value = word
  wordDialogVisible.value = true
}

const markAsLearned = async (word) => {
  try {
    const wordId = word?.wordId || word?.id
    if (!wordId) { ElMessage.error('单词ID不存在'); return }
    const response = await updateMasteryLevel(wordId, { masteryLevel: 'MASTERED', isCorrect: true })
    if (response?.code === 200) {
      ElMessage.success('已标记为已掌握')
      loadVocabulary()
      if (wordDialogVisible.value) wordDialogVisible.value = false
    }
  } catch (error) {
    console.error('标记失败:', error)
    ElMessage.error('标记失败')
  }
}

const resetToLearning = async (word) => {
  try {
    const wordId = word?.wordId || word?.id
    if (!wordId) { ElMessage.error('单词ID不存在'); return }
    const response = await updateMasteryLevel(wordId, {
      masteryLevel: 'LEARNING',
      upgradeSourceToArticle: true
    })
    if (response?.code === 200) {
      ElMessage.success('已重置为学习中')
      loadVocabulary()
      wordDialogVisible.value = false
    }
  } catch (error) {
    console.error('重置失败:', error)
    ElMessage.error('重置失败')
  }
}

const deleteWord = async (word) => {
  try {
    await ElMessageBox.confirm(`确定要删除单词"${word?.word}"吗？`, '删除确认', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    const wordId = word?.wordId || word?.id
    if (!wordId) { ElMessage.error('单词ID不存在'); return }
    const response = await batchOperateVocabulary({ operation: 'delete', wordIds: [wordId] })
    if (response?.code === 200) {
      ElMessage.success('删除成功')
      loadVocabulary()
      if (wordDialogVisible.value) wordDialogVisible.value = false
    }
  } catch (error) {
    if (error !== 'cancel') {
      console.error('删除失败:', error)
      ElMessage.error('删除失败')
    }
  }
}

const batchDelete = async () => {
  try {
    await ElMessageBox.confirm(
      `确定要删除选中的 ${selectedWords.value.length} 个单词吗？`,
      '批量删除确认',
      { confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning' }
    )
    const wordIds = selectedWords.value
    if (!Array.isArray(wordIds) || wordIds.length === 0) {
      ElMessage.warning('请先选择要删除的单词')
      return
    }
    const response = await batchOperateVocabulary({ operation: 'delete', wordIds })
    if (response?.code === 200) {
      ElMessage.success('批量删除成功')
      selectedWords.value = []
      loadVocabulary()
    }
  } catch (error) {
    if (error !== 'cancel') {
      console.error('批量删除失败:', error)
      ElMessage.error('批量删除失败')
    }
  }
}

const handleItemSelect = () => {}

// ─── 复习逻辑 ───

const startReview = async () => {
  try {
    const reviewableWords = vocabulary.value.filter(word => word.masteryLevel !== 'MASTERED')
    if (reviewableWords.length === 0) {
      ElMessage.info('没有需要复习的单词')
      return
    }
    reviewableWords.sort((a, b) => {
      const timeA = a.nextReviewTime ? new Date(a.nextReviewTime) : new Date(0)
      const timeB = b.nextReviewTime ? new Date(b.nextReviewTime) : new Date(0)
      return timeA - timeB
    })
    const settings = sm2Settings.value
    const dailyLimit = settings.overloadProtection
      ? Math.min(settings.maxDailyReview || 50, reviewBatchSize.value)
      : reviewBatchSize.value
    allReviewableWords.value = reviewableWords
    currentBatchStart.value = 0
    reviewFinishedAll.value = false
    reviewWords.value = reviewableWords.slice(0, dailyLimit)
    currentReviewIndex.value = 0
    showAnswer.value = false
    reviewPhase.value = PHASE.RECOGNITION
    selectedOption.value = null
    isAnswering.value = false
    mcqWasWrong.value = false
    await generateMCQOptions()
    Object.assign(reviewSummary, {
      totalWords: reviewWords.value.length,
      correctCount: 0,
      incorrectCount: 0
    })
    reviewDialogVisible.value = true
  } catch (error) {
    console.error('开始复习失败:', error)
    ElMessage.error('开始复习失败')
  }
}

const generateMCQOptions = async () => {
  const currentWord = reviewWords.value[currentReviewIndex.value]
  if (!currentWord) return
  try {
    const res = await getQuizData(currentWord.word, 3)
    const quizData = res?.data || {}
    let correctAnswer = quizData.correctTranslation
    let distractors = (Array.isArray(quizData.distractors) ? quizData.distractors : [])
      .filter(d => d && d !== correctAnswer)
    if (!correctAnswer) correctAnswer = currentWord.definition
    currentCorrectAnswer.value = correctAnswer
    if (distractors.length < 3) {
      const localDistractors = vocabulary.value
        .filter(w => (w.wordId || w.id) !== (currentWord.wordId || currentWord.id) && w.definition)
        .map(w => w.definition)
        .filter(d => !distractors.includes(d) && d !== correctAnswer)
      const available = [...localDistractors]
      for (let i = available.length - 1; i > 0; i--) {
        const j = Math.floor(Math.random() * (i + 1));
        [available[i], available[j]] = [available[j], available[i]]
      }
      distractors = distractors.concat(available.slice(0, 3 - distractors.length))
    }
    const options = [correctAnswer, ...distractors.slice(0, 3)]
    const shuffled = [...options]
    for (let i = shuffled.length - 1; i > 0; i--) {
      const j = Math.floor(Math.random() * (i + 1));
      [shuffled[i], shuffled[j]] = [shuffled[j], shuffled[i]]
    }
    mcqOptions.value = shuffled
  } catch (error) {
    console.warn('ECDICT选项获取失败，降级到本地生词本:', error)
    currentCorrectAnswer.value = currentWord.definition
    fallbackGenerateMCQOptions()
  }
}

const fallbackGenerateMCQOptions = () => {
  const currentWord = reviewWords.value[currentReviewIndex.value]
  if (!currentWord) return
  currentCorrectAnswer.value = currentWord.definition
  const distractors = vocabulary.value
    .filter(w => (w.wordId || w.id) !== (currentWord.wordId || currentWord.id) && w.definition)
    .map(w => w.definition)
  const available = [...distractors]
  const shuffledDistractors = []
  for (let i = 0; i < Math.min(3, available.length); i++) {
    const idx = Math.floor(Math.random() * available.length)
    shuffledDistractors.push(available[idx])
    available.splice(idx, 1)
  }
  const options = [currentWord.definition, ...shuffledDistractors]
  const shuffled = [...options]
  for (let i = shuffled.length - 1; i > 0; i--) {
    const j = Math.floor(Math.random() * (i + 1));
    [shuffled[i], shuffled[j]] = [shuffled[j], shuffled[i]]
  }
  mcqOptions.value = shuffled
}

const handleMcqSelection = (option) => {
  if (isAnswering.value) return
  const currentWord = reviewWords.value[currentReviewIndex.value]
  selectedOption.value = option
  isAnswering.value = true
  if (answerTimeoutId) clearTimeout(answerTimeoutId)
  const correctAnswer = currentCorrectAnswer.value || currentWord.definition
  answerTimeoutId = setTimeout(() => {
    if (option === correctAnswer) {
      mcqWasWrong.value = false
      reviewPhase.value = PHASE.REINFORCEMENT
      showAnswer.value = true
      playAudio(currentWord)
    } else {
      submitReview(0)
    }
    answerTimeoutId = null
  }, 1000)
}

const handleUnknown = () => {
  if (isAnswering.value) return
  isAnswering.value = true
  selectedOption.value = 'UNKNOWN'
  if (answerTimeoutId) clearTimeout(answerTimeoutId)
  answerTimeoutId = setTimeout(() => {
    submitReview(0)
    answerTimeoutId = null
  }, 500)
}

const submitReview = async (quality) => {
  const currentWord = reviewWords.value[currentReviewIndex.value]
  if (quality === 0 && reviewPhase.value === PHASE.RECOGNITION) {
    mcqWasWrong.value = true
    reviewPhase.value = PHASE.REINFORCEMENT
    showAnswer.value = true
    playAudio(currentWord)
    try {
      const sm2Result = calculateSM2(0, {
        repetition: currentWord?.repetition || 0,
        interval: currentWord?.interval || 0,
        easinessFactor: currentWord?.easinessFactor || 2.5
      }, { hardWordMode: sm2Settings.value.hardWordMode })
      const wordId = currentWord?.wordId || currentWord?.id
      if (!wordId) { console.error('单词ID不存在'); return }
      await updateMasteryLevel(wordId, { ...sm2Result, masteryLevel: 'LEARNING', isCorrect: false })
    } catch (error) {
      console.error('Update failed', error)
    }
    return
  }

  if (mcqWasWrong.value) {
    reviewSummary.incorrectCount++
  } else {
    reviewSummary.correctCount++
  }

  const sm2Result = calculateSM2(quality, {
    repetition: currentWord?.repetition || 0,
    interval: currentWord?.interval || 0,
    easinessFactor: currentWord?.easinessFactor || 2.5
  }, { hardWordMode: sm2Settings.value.hardWordMode })
  const wordId = currentWord?.wordId || currentWord?.id
  if (!wordId) { console.error('单词ID不存在'); return }

  try {
    const newMasteryLevel = quality < 3 ? 'LEARNING'
      : sm2Result.repetition >= 5 ? 'MASTERED'
      : sm2Result.repetition >= 3 ? 'FAMILIAR'
      : 'LEARNING'
    await updateMasteryLevel(wordId, { ...sm2Result, masteryLevel: newMasteryLevel, isCorrect: quality >= 3 })
  } catch (error) {
    console.error('更新单词状态失败:', error)
  }

  currentReviewIndex.value++
  showAnswer.value = false
  reviewPhase.value = PHASE.RECOGNITION
  selectedOption.value = null
  isAnswering.value = false
  mcqWasWrong.value = false
  currentCorrectAnswer.value = null

  if (currentReviewIndex.value < reviewWords.value.length) {
    await generateMCQOptions()
  } else {
    const remaining = allReviewableWords.value.length - (currentBatchStart.value + reviewWords.value.length)
    currentBatchStart.value += reviewWords.value.length
    reviewFinishedAll.value = remaining <= 0
    reviewWords.value = []
    loadVocabulary()
  }
}

const onReviewDialogClose = () => {
  if (answerTimeoutId) { clearTimeout(answerTimeoutId); answerTimeoutId = null }
  isAnswering.value = false
  selectedOption.value = null
}

const handleSort = (command) => {
  if (command === 'word-test') { startWordTest(); return }
  filterForm.sortBy = command
  loadVocabulary()
}

const getSortLabel = (sortBy) => {
  const labels = { addedTime: '添加时间', reviewCount: '复习次数', word: '字母顺序' }
  return labels[sortBy] || '排序'
}

const startWordTest = () => {
  const testWords = vocabulary.value.filter(word => word.masteryLevel !== 'MASTERED')
  if (testWords.length === 0) { ElMessage.info('没有需要测试的单词'); return }
  startReview()
}

const continueNextBatch = async () => {
  const settings = sm2Settings.value
  const dailyLimit = settings.overloadProtection
    ? Math.min(settings.maxDailyReview || 50, reviewBatchSize.value)
    : reviewBatchSize.value
  const nextBatch = allReviewableWords.value.slice(
    currentBatchStart.value,
    currentBatchStart.value + dailyLimit
  )
  reviewWords.value = nextBatch
  currentReviewIndex.value = 0
  reviewPhase.value = PHASE.RECOGNITION
  selectedOption.value = null
  isAnswering.value = false
  reviewFinishedAll.value = false
  Object.assign(reviewSummary, { totalWords: nextBatch.length, correctCount: 0, incorrectCount: 0 })
  await generateMCQOptions()
}

// ─── 音频 ───

let currentAudio = null
let audioPlayingId = null

const playAudio = (word) => {
  if (!word?.audioUrl) return
  const playingId = Symbol()
  audioPlayingId = playingId
  if (currentAudio) {
    currentAudio.pause()
    currentAudio.onended = null
    currentAudio.onerror = null
    currentAudio = null
  }
  currentAudio = new Audio(word.audioUrl)
  currentAudio.onended = () => {
    if (audioPlayingId === playingId) { currentAudio = null; audioPlayingId = null }
  }
  currentAudio.onerror = () => {
    if (audioPlayingId === playingId) { currentAudio = null; audioPlayingId = null }
    ElMessage.error('音频加载失败')
  }
  currentAudio.play().catch(error => {
    console.error('播放音频失败:', error)
    if (audioPlayingId === playingId) { currentAudio = null; audioPlayingId = null }
    ElMessage.error('播放音频失败')
  })
}

// ─── 计算属性 ───

const reviewProgress = computed(() => {
  if (reviewWords.value.length === 0) return 100
  return Math.round((currentReviewIndex.value / reviewWords.value.length) * 100)
})

const remainingWords = computed(() =>
  Math.max(0, allReviewableWords.value.length - currentBatchStart.value)
)

const reviewAccuracy = computed(() => {
  const total = reviewSummary.correctCount + reviewSummary.incorrectCount
  if (total === 0) return 0
  return Math.round((reviewSummary.correctCount / total) * 100)
})

const isWordOverdue = (word) => {
  if (!word?.nextReviewTime) return false  // 未设复习时间 = 新词，不是"需复习"
  return new Date(word.nextReviewTime) <= new Date()
}

// ─── 工具函数 ───

const getMasteryType = (level) =>
  ({ NEW: 'info', LEARNING: 'warning', FAMILIAR: 'primary', MASTERED: 'success' })[level] || 'info'

const getMasteryText = (level) =>
  ({ NEW: '新词', LEARNING: '学习中', FAMILIAR: '熟悉', MASTERED: '已掌握' })[level] || level || '新词'

const getDifficultyType = (difficulty) =>
  ({ EASY: 'success', MEDIUM: 'warning', HARD: 'danger' })[difficulty] || 'info'

const getDifficultyText = (difficulty) =>
  ({ EASY: '简单', MEDIUM: '中等', HARD: '困难' })[difficulty] || '未知'

const getScoreClass = (score) => {
  if (score >= 80) return 'get-score-known'
  if (score >= 50) return 'get-score-uncertain'
  return 'get-score-unknown'
}

const formatDate = (date) => {
  if (!date) return ''
  return new Date(date).toLocaleString('zh-CN', {
    year: 'numeric', month: '2-digit', day: '2-digit',
    hour: '2-digit', minute: '2-digit'
  })
}

// ─── 生命周期 ───

onMounted(() => {
  loadVocabulary()
  window.addEventListener('resize', handleScreenResize)
  try {
    if (!toggleLoaded()) {
      const userStore = useUserStore()
      const usePersonal = !!userStore.token && (userStore.userRole === 'STUDENT' || userStore.userRole === 'USER')
      loadFeatureToggles({ personal: usePersonal }).catch(() => {})
    }
  } catch { /* 测试环境或无 Pinia 时静默跳过 */ }
})

const handleScreenResize = () => {
  const newWidth = window.innerWidth
  const wasMobile = screenWidth.value < 768
  const isMobileNow = newWidth < 768
  screenWidth.value = newWidth
  if (wasMobile !== isMobileNow) {
    pageSize.value = isMobileNow ? 10 : 999
    currentPage.value = 1
    loadVocabulary()
  }
}

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleScreenResize)
  if (answerTimeoutId) { clearTimeout(answerTimeoutId); answerTimeoutId = null }
  if (currentAudio) {
    currentAudio.pause()
    currentAudio.onended = null
    currentAudio.onerror = null
    currentAudio = null
  }
})
</script>

<style scoped>
/* ─── 容器 ─── */
.vocabulary-container {
  padding: 20px 24px;
  background: #f6f8fa;
  min-height: 100vh;
}

/* ─── 统计卡片 ─── */
.stats-cards {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 14px;
  margin-bottom: 20px;
}

.stat-card {
  background: white;
  padding: 16px 18px;
  border-radius: 16px;
  display: flex;
  align-items: center;
  gap: 12px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.04);
  transition: transform 0.25s;
}

.stat-card:hover { transform: translateY(-3px); }

.stat-icon {
  width: 42px;
  height: 42px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 20px;
  color: white;
  flex-shrink: 0;
}

.total .stat-icon    { background: linear-gradient(135deg, #6366f1, #a855f7); }
.new .stat-icon      { background: linear-gradient(135deg, #0ea5e9, #2dd4bf); }
.learning .stat-icon { background: linear-gradient(135deg, #f59e0b, #ef4444); }
.mastered .stat-icon { background: linear-gradient(135deg, #10b981, #3b82f6); }

.stat-info { display: flex; flex-direction: column; }
.stat-label { font-size: 12px; color: #888; font-weight: 600; }
.stat-value { font-size: 20px; font-weight: 800; color: #1a1a1a; line-height: 1.2; }

/* ─── 操作栏 ─── */
.action-bar {
  background: rgba(255, 255, 255, 0.85);
  backdrop-filter: blur(12px);
  border: 1px solid rgba(255, 255, 255, 0.6);
  border-radius: 18px;
  padding: 14px 20px;
  margin-bottom: 20px;
  display: flex;
  flex-direction: column;
  gap: 12px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.04);
}

.filter-row {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.filter-chips {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  flex: 1;
}

.filter-chip {
  padding: 5px 14px;
  border-radius: 50px;
  background: white;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s;
  color: #555;
  border: 1.5px solid #e5e7eb;
  user-select: none;
  white-space: nowrap;
}

.filter-chip:hover { border-color: #6366f1; color: #6366f1; }

.filter-chip.active         { background: #1a1a1a; color: white; border-color: #1a1a1a; }
.filter-chip.chip-new.active      { background: #0ea5e9; border-color: #0ea5e9; }
.filter-chip.chip-learning.active { background: #f59e0b; border-color: #f59e0b; }
.filter-chip.chip-familiar.active { background: #8b5cf6; border-color: #8b5cf6; }
.filter-chip.chip-mastered.active { background: #10b981; border-color: #10b981; }

.search-input { width: 220px; flex-shrink: 0; }
.search-input :deep(.el-input__wrapper) { border-radius: 50px; }

.actions-row {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.icon-btn-text {
  font-weight: 600;
  border: none;
  background: transparent;
  color: #333;
}

.start-btn {
  padding: 9px 22px;
  font-weight: 700;
  box-shadow: 0 4px 12px rgba(99, 102, 241, 0.2);
}

/* ─── 分析面板过渡 ─── */
.analytics-panels {
  display: flex;
  flex-direction: column;
  gap: 16px;
  margin-bottom: 20px;
}

.slide-fade-enter-active { transition: all 0.3s ease-out; }
.slide-fade-leave-active { transition: all 0.2s ease-in; }
.slide-fade-enter-from,
.slide-fade-leave-to { transform: translateY(-8px); opacity: 0; }

/* ─── 词汇卡片网格 ─── */
.vocabulary-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
  gap: 16px;
}

.word-card {
  background: white;
  border-radius: 16px;
  padding: 14px 16px;
  position: relative;
  border: 1.5px solid #f0f2f5;
  cursor: pointer;
  display: flex;
  flex-direction: column;
  gap: 6px;
  transition: transform 0.25s cubic-bezier(0.25, 0.8, 0.25, 1),
              box-shadow 0.25s,
              border-color 0.25s;
}

.word-card:hover {
  transform: translateY(-5px);
  box-shadow: 0 10px 28px rgba(0, 0, 0, 0.08);
  border-color: #6366f1;
}

.card-selected {
  border-color: #6366f1 !important;
  background: rgba(99, 102, 241, 0.02);
}

.card-checkbox {
  position: absolute;
  top: 12px;
  right: 12px;
  z-index: 1;
}

.card-top {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 8px;
  padding-right: 24px;
}

.word-text {
  font-size: 17px;
  font-weight: 800;
  margin: 0;
  color: #1a1a1a;
  line-height: 1.3;
  word-break: break-all;
}

.level-badge {
  font-size: 10px;
  padding: 2px 8px;
  border-radius: 50px;
  font-weight: 700;
  text-transform: uppercase;
  white-space: nowrap;
  flex-shrink: 0;
  line-height: 1.6;
}

.level-badge.new      { background: #e0f2fe; color: #0369a1; }
.level-badge.learning { background: #fef3c7; color: #b45309; }
.level-badge.mastered { background: #dcfce7; color: #15803d; }
.level-badge.familiar { background: #f3e8ff; color: #7c3aed; }

.phonetic {
  font-family: monospace;
  font-size: 12px;
  color: #94a3b8;
  display: flex;
  align-items: center;
  gap: 3px;
}

.audio-btn { padding: 0 2px; color: #94a3b8; }

.translation {
  font-size: 13px;
  color: #4b5563;
  line-height: 1.5;
  flex: 1;
  word-break: break-word;
  margin: 0;
}

.card-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-top: 10px;
  border-top: 1px solid #f1f5f9;
  margin-top: auto;
}

.footer-meta {
  display: flex;
  align-items: center;
  gap: 6px;
}

.review-count {
  font-size: 11px;
  font-weight: 600;
  color: #94a3b8;
}

.due-badge {
  font-size: 10px;
  padding: 1px 6px;
  border-radius: 4px;
  background: #fee2e2;
  color: #dc2626;
  font-weight: 700;
}

.footer-actions { display: flex; gap: 4px; }

/* ─── 空状态 ─── */
.empty-state {
  grid-column: 1 / -1;
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 40vh;
}

/* ─── 分页 ─── */
.pagination-bar {
  display: flex;
  justify-content: center;
  margin-top: 28px;
  padding-bottom: 40px;
}

.pagination-bar :deep(.el-pager li) {
  border-radius: 50% !important;
  min-width: 32px;
  height: 32px;
  line-height: 32px;
}

/* ─── 弹窗 ─── */
:deep(.el-dialog) {
  border-radius: 24px;
  overflow: hidden;
}

.word-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.word-header h2 { margin: 0; font-size: 28px; font-weight: 800; }

.word-content .pronunciation { color: #888; font-style: italic; margin: 0 0 8px; }
.word-content .definition { font-size: 16px; color: #333; line-height: 1.6; margin-bottom: 12px; }

.example-sentence {
  background: #f8faff;
  border-left: 3px solid #6366f1;
  padding: 10px 14px;
  border-radius: 0 8px 8px 0;
  font-size: 14px;
  color: #555;
  margin-bottom: 12px;
}

.example-label { font-weight: 700; color: #6366f1; }

.word-meta-info { display: flex; gap: 8px; flex-wrap: wrap; margin-top: 8px; }

.learning-stats { margin-top: 20px; border-top: 1px solid #f0f0f0; padding-top: 16px; }
.learning-stats h4 { margin: 0 0 12px; font-size: 14px; color: #666; }

.stats-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 8px; }

.stat-item { font-size: 13px; color: #555; }
.stat-item .label { color: #888; }
.stat-item .value { font-weight: 600; color: #333; }

.word-actions {
  display: flex;
  gap: 10px;
  margin-top: 20px;
  justify-content: flex-end;
  flex-wrap: wrap;
}

/* ─── 复习对话框 ─── */
.review-session { padding: 8px 0; }

.review-progress { margin-bottom: 16px; }

.progress-info {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 6px;
  font-size: 14px;
  color: #555;
}

.review-schedule-hint {
  font-size: 12px;
  padding: 2px 8px;
  border-radius: 6px;
  font-weight: 600;
  background: #fef3c7;
  color: #b45309;
}

.word-card-v3 {
  background: white;
  border-radius: 28px;
  padding: 36px 40px;
  min-height: 380px;
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  text-align: center;
  box-shadow: 0 16px 48px rgba(0, 0, 0, 0.05);
  border: 1px solid rgba(0, 0, 0, 0.04);
  margin: 16px 0;
}

.recognition-phase,
.reinforcement-phase {
  width: 100%;
  animation: fadeSlideIn 0.35s ease-out;
}

@keyframes fadeSlideIn {
  from { opacity: 0; transform: translateY(12px); }
  to   { opacity: 1; transform: translateY(0); }
}

.word-card-v3 .word {
  font-size: 46px;
  font-weight: 800;
  margin-bottom: 28px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  -webkit-background-clip: text;
  background-clip: text;
  -webkit-text-fill-color: transparent;
  letter-spacing: -1px;
}

.mcq-options {
  display: grid;
  grid-template-columns: 1fr;
  gap: 10px;
  width: 100%;
  max-width: 480px;
  margin: 0 auto;
}

.mcq-option-btn {
  height: auto;
  padding: 14px 22px;
  border-radius: 14px;
  font-size: 15px;
  font-weight: 600;
  white-space: normal;
  line-height: 1.4;
  transition: all 0.2s;
  border: 2px solid #f0f0f0;
}

.mcq-option-btn:hover { border-color: #6366f1; background: #f8f8ff; transform: scale(1.01); }

.mcq-option-btn.is-correct {
  background: #f0fdf4;
  border-color: #22c55e;
  color: #15803d;
  box-shadow: 0 0 14px rgba(34, 197, 94, 0.18);
}

.mcq-option-btn.is-incorrect {
  background: #fef2f2;
  border-color: #ef4444;
  color: #b91c1c;
  box-shadow: 0 0 14px rgba(239, 68, 68, 0.18);
}

.unknown-btn { margin-top: 8px; border-style: dashed; }

.mastery-adjustment {
  margin-top: 20px;
  padding-top: 20px;
  border-top: 1px solid #f0f0f0;
  width: 100%;
}

.adjustment-tip {
  font-size: 13px;
  color: #94a3b8;
  margin-bottom: 14px;
  font-weight: 600;
}

.adjustment-actions {
  display: flex;
  justify-content: center;
  gap: 12px;
  margin-bottom: 16px;
  flex-wrap: wrap;
}

.next-btn { font-weight: 700; font-size: 14px; }

.reinforcement-phase .pronunciation {
  font-size: 17px;
  color: #94a3b8;
  margin-bottom: 10px;
}

.reinforcement-phase .definition {
  font-size: 22px;
  font-weight: 700;
  color: #1a1a1a;
  margin-bottom: 16px;
}

.reinforcement-phase .example {
  background: #f8faff;
  padding: 14px 18px;
  border-radius: 14px;
  text-align: left;
  font-size: 14px;
  line-height: 1.6;
  color: #475569;
  word-break: break-word;
}

.review-complete {
  text-align: center;
  padding: 32px 20px;
}

.review-complete h3 { font-size: 22px; font-weight: 800; margin-bottom: 20px; }

.review-summary {
  background: #f8f9fa;
  border-radius: 16px;
  padding: 16px 24px;
  margin-bottom: 24px;
  text-align: left;
  display: inline-block;
  min-width: 240px;
}

.summary-item {
  display: flex;
  justify-content: space-between;
  padding: 8px 0;
  font-size: 15px;
  border-bottom: 1px solid #f0f0f0;
}

.summary-item:last-child { border-bottom: none; }

.review-actions { display: flex; justify-content: center; gap: 12px; }

.get-score-known    { color: #10b981; font-weight: 700; }
.get-score-uncertain{ color: #f59e0b; font-weight: 700; }
.get-score-unknown  { color: #ef4444; font-weight: 700; }

/* ─── 响应式 ─── */
@media (max-width: 900px) {
  .stats-cards { grid-template-columns: repeat(2, 1fr); }
}

@media (max-width: 768px) {
  .vocabulary-container { padding: 12px 14px; }

  .stats-cards { gap: 10px; margin-bottom: 14px; }

  .stat-card { padding: 12px 14px; border-radius: 14px; }
  .stat-icon { width: 36px; height: 36px; font-size: 17px; border-radius: 10px; }
  .stat-value { font-size: 17px; }

  .action-bar { padding: 12px 14px; gap: 10px; border-radius: 14px; }

  .search-input { width: 100%; }

  .filter-chips { flex: unset; width: 100%; overflow-x: auto; flex-wrap: nowrap; padding-bottom: 2px; }

  .actions-row { flex-wrap: wrap; gap: 8px; }
  .start-btn { flex-grow: 1; }

  .vocabulary-grid { grid-template-columns: repeat(2, 1fr); gap: 10px; }

  :deep(.el-dialog) { width: 95% !important; margin-top: 5vh !important; }

  .word-card-v3 { padding: 24px 16px; min-height: 340px; border-radius: 20px; }
  .word-card-v3 .word { font-size: 32px; margin-bottom: 20px; }
  .mcq-option-btn { padding: 12px 14px; font-size: 14px; }
  .reinforcement-phase .definition { font-size: 19px; }
  .adjustment-actions .el-button { flex: 1; min-width: 76px; padding: 8px 10px; font-size: 13px; }
}

@media (max-width: 480px) {
  .vocabulary-grid { grid-template-columns: 1fr; }
}
</style>
