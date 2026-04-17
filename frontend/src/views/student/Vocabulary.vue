<template>
  <div class="vocabulary-container">
    <!-- 顶部概览 -->
    <div class="page-header">
      <p class="page-subtitle">温故而知新，掌握更多词汇</p>
    </div>

    <!-- 统计卡片 - 移动端横向滚动 -->
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

    <!-- 功能操作栏 -->
    <div class="action-bar-v2">

      <div class="secondary-actions">
        <el-dropdown trigger="click" @command="handleSort">
          <el-button class="icon-btn-text">
            <el-icon><Sort /></el-icon> {{ getSortLabel(filterForm.sortBy) }}
          </el-button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="addedTime">按添加时间</el-dropdown-item>
              <el-dropdown-item command="reviewCount">按复习次数</el-dropdown-item>
              <el-dropdown-item command="word">按字母顺序</el-dropdown-item>
              <el-dropdown-item divided command="word-test" class="text-primary font-bold">
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

        <el-button type="primary" round class="start-btn" @click="startReview" :disabled="vocabulary.length === 0">
          <el-icon><VideoPlay /></el-icon> 开始复习
        </el-button>
        
        <el-button v-if="selectedWords.length > 0" type="danger" plain round @click="batchDelete">
          删除 ({{ selectedWords.length }})
        </el-button>
      </div>
    </div>

    <!-- 词汇列表 -->
    <div class="vocabulary-grid" v-loading="loading">
      <div
        v-for="item in vocabulary"
        :key="item.id"
        class="word-card-v2"
        :class="{ 'card-selected': selectedWords.includes(item.wordId || item.id) }"
        @click="showWordDetail(item)"
      >
        <div class="card-selection" @click.stop>
          <el-checkbox
            v-model="selectedWords"
            :label="item.wordId || item.id"
            @change="val => handleItemSelect(item.wordId || item.id, val)"
          ><span></span></el-checkbox>
        </div>

        <div class="card-body">
          <div class="word-row">
            <h3 class="word-text">{{ item.word }}</h3>
            <div class="word-tags">
              <span class="level-badge" :class="item.masteryLevel?.toLowerCase() || 'new'">
                {{ getMasteryText(item.masteryLevel) }}
              </span>
            </div>
          </div>
          
          <div class="phonetic" v-if="item.pronunciation">
            [{{ item.pronunciation }}]
            <el-button link class="audio-mini-btn" @click.stop="playAudio(item)">
              <el-icon><Microphone /></el-icon>
            </el-button>
          </div>

          <div class="translation">{{ item.definition }}</div>

          <div class="card-footer">
            <div class="review-info">
              <span>复习 {{ item.reviewCount }} 次</span>
              <span v-if="item.nextReviewTime && isWordOverdue(item)" class="due-badge">需复习</span>
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
              <el-button 
                circle 
                size="small" 
                plain
                @click.stop="deleteWord(item)"
              >
                <el-icon><Delete /></el-icon>
              </el-button>
            </div>
          </div>
        </div>
      </div>

      <!-- 分页：仅移动端显示 -->
      <div class="pagination-v2" v-if="isMobile && total > pageSize">
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

      <!-- 空状态 -->
      <div v-if="!loading && vocabulary.length === 0" class="empty-state-v2">
        <el-empty description="暂时没有生词哦" :image-size="120">
          <el-button type="primary" round @click="$router.push('/student/courses')">去探索新词</el-button>
        </el-empty>
      </div>
    </div>

    <!-- 单词详情弹窗 -->
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
            <span class="example-label">例句：</span>
            {{ selectedWord.exampleSentence }}
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
          <el-button @click="markAsLearned(selectedWord)" v-if="selectedWord.masteryLevel !== 'MASTERED'">
            标记为已掌握
          </el-button>
          <el-button type="warning" @click="resetToLearning(selectedWord)" v-if="selectedWord.masteryLevel === 'MASTERED'">
            重置为学习中
          </el-button>
          <el-button type="danger" @click="deleteWord(selectedWord)">
            删除单词
          </el-button>
        </div>
      </div>
    </el-dialog>

    <!-- 复习对话框 -->
    <el-dialog v-model="reviewDialogVisible" title="生词复习" width="min(800px, 96vw)" :close-on-click-modal="false" @close="onReviewDialogClose">
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
          <div class="recognition-phase" v-if="reviewPhase === PHASE.RECOGNITION && currentReviewWord">
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
              >
                {{ option }}
              </el-button>
              <el-button
                type="info"
                plain
                class="mcq-option-btn unknown-btn"
                @click="handleUnknown"
              >
                我不知道
              </el-button>
            </div>
          </div>

          <!-- 阶段二：强化记忆 & 调整 -->
          <div class="reinforcement-phase" v-else-if="currentReviewWord">
            <h2 class="word">{{ currentReviewWord.word }}</h2>
            <p class="pronunciation">{{ currentReviewWord.pronunciation }}</p>
            <div class="definition">{{ currentCorrectAnswer || currentReviewWord.definition }}</div>
            <div class="example" v-if="currentReviewWord.exampleSentence">
              <span class="example-label">例句：</span>
              {{ currentReviewWord.exampleSentence }}
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
            <strong :class="reviewSummary.incorrectCount > 0 ? 'get-score-unknown' : ''">{{ reviewSummary.incorrectCount }} 个</strong>
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
          <el-button :type="reviewFinishedAll ? 'primary' : 'default'"
                     @click="reviewDialogVisible = false">
            {{ reviewFinishedAll ? '完成' : '休息一下' }}
          </el-button>
        </div>
      </div>
    </el-dialog>
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
  ArrowRight
} from '@element-plus/icons-vue'

import { calculateSM2 } from '@/utils/sm2'
import {
  getVocabularyList,
  getVocabularyStats,
  getWordDetail,
  updateMasteryLevel,
  batchOperateVocabulary,
  getQuizData
} from '@/api/student'

const loading = ref(false)
const searchWord = ref('')
const vocabulary = ref([])
const total = ref(0)
const currentPage = ref(1)
const pageSize = ref(window.innerWidth < 768 ? 10 : 999)
const selectedWords = ref([])
const screenWidth = ref(window.innerWidth)

// 是否移动端
const isMobile = computed(() => screenWidth.value < 768)

// 统计数据
const stats = reactive({
  totalWords: 0,
  newWords: 0,
  learningWords: 0,
  masteredWords: 0
})

// 筛选表单
const filterForm = reactive({
  masteryLevel: '',
  sortBy: 'addedTime',
  difficulty: ''
})

// 单词详情弹窗
const wordDialogVisible = ref(false)
const selectedWord = ref(null)

// 复习相关
const reviewDialogVisible = ref(false)
const reviewWords = ref([])
const currentReviewIndex = ref(0)
const currentReviewWord = computed(() => reviewWords.value[currentReviewIndex.value] || null)
const showAnswer = ref(false)

// 批次复习相关
const reviewBatchSize = ref(20)
const allReviewableWords = ref([])
const currentBatchStart = ref(0)
const reviewFinishedAll = ref(false)

// 复习相关
const PHASE = {
  RECOGNITION: 'RECOGNITION',
  REINFORCEMENT: 'REINFORCEMENT'
}
const reviewPhase = ref(PHASE.RECOGNITION)
const mcqOptions = ref([])
const selectedOption = ref(null)
const isAnswering = ref(false)
const mcqWasWrong = ref(false)  // 记录本词 MCQ 阶段是否答错
const currentCorrectAnswer = ref(null)  // 当前题目的正确答案（来自ECDICT词库）
let answerTimeoutId = null  // 用于追踪答题延迟的 setTimeout


const reviewSummary = reactive({
  totalWords: 0,
  correctCount: 0,
  incorrectCount: 0
})

// 全选相关

// 加载生词本列表
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

    const response = await getVocabularyList(params)
    if (response?.code === 200 && response?.data) {
      vocabulary.value = Array.isArray(response.data?.records) ? response.data.records : []
      total.value = response.data?.total || 0

      // 更新统计信息
      updateStats()
    }
  } catch (error) {
    console.error('加载生词本失败:', error)
    ElMessage.error('加载生词本失败')
  } finally {
    loading.value = false
  }
}

// 更新统计信息（从后端获取全量统计，不依赖当前页数据）
const updateStats = async () => {
  try {
    const res = await getVocabularyStats()
    if (res?.code === 200 && res?.data) {
      stats.totalWords = res.data.totalCount || 0
      stats.newWords = res.data.newCount || 0
      stats.learningWords = res.data.learningCount || 0
      stats.masteredWords = res.data.masteredCount || 0
    } else {
      // 降级：用当前页数据统计（不准确但不崩溃）
      stats.totalWords = total.value
      stats.newWords = vocabulary.value.filter(w => w.masteryLevel === 'NEW').length
      stats.learningWords = vocabulary.value.filter(w => w.masteryLevel === 'LEARNING').length
      stats.masteredWords = vocabulary.value.filter(w => w.masteryLevel === 'MASTERED').length
    }
  } catch {
    stats.totalWords = total.value
    stats.newWords = vocabulary.value.filter(w => w.masteryLevel === 'NEW').length
    stats.learningWords = vocabulary.value.filter(w => w.masteryLevel === 'LEARNING').length
    stats.masteredWords = vocabulary.value.filter(w => w.masteryLevel === 'MASTERED').length
  }
}

// 显示单词详情
const showWordDetail = async (word) => {
  try {
    // 对于生词本记录，使用wordId字段；对于其他情况，使用id字段
    const wordId = word?.wordId || word?.id
    if (!wordId) {
      ElMessage.error('单词ID不存在')
      return
    }
    const response = await getWordDetail(wordId)
    if (response?.code === 200 && response?.data) {
      selectedWord.value = response.data
      wordDialogVisible.value = true
    }
  } catch (error) {
    console.error('获取单词详情失败:', error)
    ElMessage.error('获取单词详情失败')
  }
}

// 标记为已掌握
const markAsLearned = async (word) => {
  try {
    const wordId = word?.wordId || word?.id
    if (!wordId) {
      ElMessage.error('单词ID不存在')
      return
    }
    const response = await updateMasteryLevel(wordId, {
      masteryLevel: 'MASTERED',
      isCorrect: true
    })
    if (response?.code === 200) {
      ElMessage.success('已标记为已掌握')
      loadVocabulary() // 刷新列表
      if (wordDialogVisible.value) {
        wordDialogVisible.value = false
      }
    }
  } catch (error) {
    console.error('标记失败:', error)
    ElMessage.error('标记失败')
  }
}

// 重置为学习中
const resetToLearning = async (word) => {
  try {
    const wordId = word?.wordId || word?.id
    if (!wordId) {
      ElMessage.error('单词ID不存在')
      return
    }
    const response = await updateMasteryLevel(wordId, {
      masteryLevel: 'LEARNING'
    })
    if (response?.code === 200) {
      ElMessage.success('已重置为学习中')
      loadVocabulary() // 刷新列表
      wordDialogVisible.value = false
    }
  } catch (error) {
    console.error('重置失败:', error)
    ElMessage.error('重置失败')
  }
}

// 删除单词
const deleteWord = async (word) => {
  try {
    await ElMessageBox.confirm(
      `确定要删除单词"${word?.word}"吗？`,
      '删除确认',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )

    // 使用批量删除API删除单个单词
    const wordId = word?.wordId || word?.id
    if (!wordId) {
      ElMessage.error('单词ID不存在')
      return
    }
    const response = await batchOperateVocabulary({
      operation: 'delete',
      wordIds: [wordId]
    })

    if (response?.code === 200) {
      ElMessage.success('删除成功')
      loadVocabulary() // 刷新列表
      if (wordDialogVisible.value) {
        wordDialogVisible.value = false
      }
    }
  } catch (error) {
    if (error !== 'cancel') {
      console.error('删除失败:', error)
      ElMessage.error('删除失败')
    }
  }
}

// 批量删除
const batchDelete = async () => {
  try {
    await ElMessageBox.confirm(
      `确定要删除选中的 ${selectedWords.value.length} 个单词吗？`,
      '批量删除确认',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )

    // selectedWords 存储的是数字 ID 列表
    const wordIds = selectedWords.value
    if (!Array.isArray(wordIds) || wordIds.length === 0) {
      ElMessage.warning('请先选择要删除的单词')
      return
    }

    const response = await batchOperateVocabulary({
      operation: 'delete',
      wordIds: wordIds
    })

    if (response?.code === 200) {
      ElMessage.success('批量删除成功')
      selectedWords.value = []
      loadVocabulary() // 刷新列表
    }
  } catch (error) {
    if (error !== 'cancel') {
      console.error('批量删除失败:', error)
      ElMessage.error('批量删除失败')
    }
  }
}

// 全选处理

// 开始复习
const startReview = async () => {
  try {
    // 筛选需要复习的单词（排除已掌握的）
    const reviewableWords = vocabulary.value.filter(word => word.masteryLevel !== 'MASTERED')

    if (reviewableWords.length === 0) {
      ElMessage.info('没有需要复习的单词')
      return
    }

    // 按 SM-2 nextReviewTime 排序：到期/逾期的词优先（艾宾浩斯遗忘曲线调度）
    reviewableWords.sort((a, b) => {
      const timeA = a.nextReviewTime ? new Date(a.nextReviewTime) : new Date(0)
      const timeB = b.nextReviewTime ? new Date(b.nextReviewTime) : new Date(0)
      // nextReviewTime 为 null 或更早的排前面（最需要复习的优先）
      return timeA - timeB
    })

    allReviewableWords.value = reviewableWords
    currentBatchStart.value = 0
    reviewFinishedAll.value = false
    reviewWords.value = reviewableWords.slice(0, reviewBatchSize.value)
    currentReviewIndex.value = 0
    showAnswer.value = false
    reviewPhase.value = PHASE.RECOGNITION
    selectedOption.value = null
    isAnswering.value = false
    mcqWasWrong.value = false
    await generateMCQOptions()


    // 重置复习统计
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

// 生成多选题选项（正确答案和干扰项均来自ECDICT词库，确保风格一致）
const generateMCQOptions = async () => {
  const currentWord = reviewWords.value[currentReviewIndex.value]
  if (!currentWord) return

  try {
    // 从后端ECDICT获取正确答案和干扰项
    const res = await getQuizData(currentWord.word, 3)
    const quizData = res?.data || {}
    let correctAnswer = quizData.correctTranslation
    let distractors = (Array.isArray(quizData.distractors) ? quizData.distractors : [])
      .filter(d => d && d !== correctAnswer)

    // 如果ECDICT没有该词的翻译，降级使用words表的definition
    if (!correctAnswer) {
      correctAnswer = currentWord.definition
    }

    // 保存当前正确答案（用于答题判定）
    currentCorrectAnswer.value = correctAnswer

    // 如果后端返回不足3个干扰项，从本地生词本补充
    if (distractors.length < 3) {
      const localDistractors = vocabulary.value
        .filter(w => (w.wordId || w.id) !== (currentWord.wordId || currentWord.id) && w.definition)
        .map(w => w.definition)
        .filter(d => !distractors.includes(d) && d !== correctAnswer)
      // Fisher-Yates 抽样补充
      const available = [...localDistractors]
      for (let i = available.length - 1; i > 0; i--) {
        const j = Math.floor(Math.random() * (i + 1));
        [available[i], available[j]] = [available[j], available[i]]
      }
      distractors = distractors.concat(available.slice(0, 3 - distractors.length))
    }

    // 合并正确答案 + Fisher-Yates 打乱
    const options = [correctAnswer, ...distractors.slice(0, 3)]
    const shuffled = [...options]
    for (let i = shuffled.length - 1; i > 0; i--) {
      const j = Math.floor(Math.random() * (i + 1));
      [shuffled[i], shuffled[j]] = [shuffled[j], shuffled[i]]
    }
    mcqOptions.value = shuffled
  } catch (error) {
    console.warn('ECDICT选项获取失败，降级到本地生词本:', error)
    // 降级：使用本地生词本逻辑
    currentCorrectAnswer.value = currentWord.definition
    fallbackGenerateMCQOptions()
  }
}

// 降级方案：从本地生词本生成MCQ选项
const fallbackGenerateMCQOptions = () => {
  const currentWord = reviewWords.value[currentReviewIndex.value]
  if (!currentWord) return

  currentCorrectAnswer.value = currentWord.definition

  const distractors = vocabulary.value
    .filter(w => (w.wordId || w.id) !== (currentWord.wordId || currentWord.id) && w.definition)
    .map(w => w.definition)

  const shuffledDistractors = []
  const availableDistractors = [...distractors]
  for (let i = 0; i < Math.min(3, availableDistractors.length); i++) {
    const idx = Math.floor(Math.random() * availableDistractors.length)
    shuffledDistractors.push(availableDistractors[idx])
    availableDistractors.splice(idx, 1)
  }

  const options = [currentWord.definition, ...shuffledDistractors]
  const shuffled = [...options]
  for (let i = shuffled.length - 1; i > 0; i--) {
    const j = Math.floor(Math.random() * (i + 1));
    [shuffled[i], shuffled[j]] = [shuffled[j], shuffled[i]]
  }
  mcqOptions.value = shuffled
}

// 处理多选题选择
const handleMcqSelection = (option) => {
  if (isAnswering.value) return

  const currentWord = reviewWords.value[currentReviewIndex.value]
  selectedOption.value = option
  isAnswering.value = true

  // 清理之前的延迟
  if (answerTimeoutId) {
    clearTimeout(answerTimeoutId)
  }

  const correctAnswer = currentCorrectAnswer.value || currentWord.definition
  answerTimeoutId = setTimeout(() => {
    if (option === correctAnswer) {
      // 选对了
      mcqWasWrong.value = false
      reviewPhase.value = PHASE.REINFORCEMENT
      showAnswer.value = true
      playAudio(currentWord)
    } else {
      // 选错了
      submitReview(0)
    }
    answerTimeoutId = null
  }, 1000) // 停留1秒展示反馈
}

// 处理”我不知道”
const handleUnknown = () => {
  if (isAnswering.value) return
  isAnswering.value = true
  selectedOption.value = 'UNKNOWN'

  // 清理之前的延迟
  if (answerTimeoutId) {
    clearTimeout(answerTimeoutId)
  }

  answerTimeoutId = setTimeout(() => {
    submitReview(0)
    answerTimeoutId = null
  }, 500)
}


// 提交复习结果
const submitReview = async (quality) => {
  const currentWord = reviewWords.value[currentReviewIndex.value]

  // 如果是从练习阶段提交（选错或不知道），进入强化阶段
  if (quality === 0 && reviewPhase.value === PHASE.RECOGNITION) {
    mcqWasWrong.value = true
    reviewPhase.value = PHASE.REINFORCEMENT
    showAnswer.value = true
    playAudio(currentWord)

    // 更新后端（初次判定为失败）
    try {
      const sm2Result = calculateSM2(0, {
        repetition: currentWord?.repetition || 0,
        interval: currentWord?.interval || 0,
        easinessFactor: currentWord?.easinessFactor || 2.5
      })
      const wordId = currentWord?.wordId || currentWord?.id
      if (!wordId) {
        console.error('单词ID不存在')
        return
      }
      await updateMasteryLevel(wordId, {
        ...sm2Result,
        masteryLevel: 'LEARNING',
        isCorrect: false
      })
    } catch (error) {
      console.error('Update failed', error)
    }
    return
  }

  // 更新复习统计：以 MCQ 阶段的对错为准（选对=正确，选错/不知道=错误）
  if (mcqWasWrong.value) {
    reviewSummary.incorrectCount++
  } else {
    reviewSummary.correctCount++
  }

  // 使用 SM-2 算法计算
  const sm2Result = calculateSM2(quality, {
    repetition: currentWord?.repetition || 0,
    interval: currentWord?.interval || 0,
    easinessFactor: currentWord?.easinessFactor || 2.5
  })

  const wordId = currentWord?.wordId || currentWord?.id
  if (!wordId) {
    console.error('单词ID不存在')
    return
  }

  // 更新单词掌握程度：quality < 3 → 学习中；SM-2 成功复习 ≥ 3 次 → 已掌握；其余 → 学习中
  try {
    const newMasteryLevel = (quality < 3) ? 'LEARNING'
      : (sm2Result.repetition >= 3) ? 'MASTERED'
      : 'LEARNING'

    await updateMasteryLevel(wordId, {
      ...sm2Result,
      masteryLevel: newMasteryLevel,
      isCorrect: quality >= 3
    })
  } catch (error) {
    console.error('更新单词状态失败:', error)
  }

  // 进入下一个单词
  currentReviewIndex.value++
  showAnswer.value = false
  reviewPhase.value = PHASE.RECOGNITION
  selectedOption.value = null
  isAnswering.value = false
  mcqWasWrong.value = false
  currentCorrectAnswer.value = null

  // 检查是否完成复习
  if (currentReviewIndex.value < reviewWords.value.length) {
    await generateMCQOptions()
  } else {
    const remaining = allReviewableWords.value.length - (currentBatchStart.value + reviewWords.value.length)
    currentBatchStart.value += reviewWords.value.length
    reviewFinishedAll.value = remaining <= 0
    reviewWords.value = []
    loadVocabulary() // 刷新列表
  }
}


// 关闭复习对话框时清理挂起的 timeout，防止 submitReview 在过期状态下触发
const onReviewDialogClose = () => {
  if (answerTimeoutId) {
    clearTimeout(answerTimeoutId)
    answerTimeoutId = null
  }
  isAnswering.value = false
  selectedOption.value = null
}

// 排序处理
const handleSort = (command) => {
  if (command === 'word-test') {
    startWordTest()
    return
  }
  filterForm.sortBy = command
  loadVocabulary()
}

const getSortLabel = (sortBy) => {
  const labels = {
    'addedTime': '添加时间',
    'reviewCount': '复习次数',
    'word': '字母顺序'
  }
  return labels[sortBy] || '排序'
}

const handleItemSelect = () => {
  // Logic is handled by v-model="selectedWords"
}

// 开始单词测试（直接触发复习模式）
const startWordTest = () => {
  const testWords = vocabulary.value.filter(word => word.masteryLevel !== 'MASTERED')
  if (testWords.length === 0) {
    ElMessage.info('没有需要测试的单词')
    return
  }
  startReview()
}

// 播放音频
let currentAudio = null
let audioPlayingId = null  // 追踪当前正在播放的音频，防止竞态条件

const playAudio = (word) => {
  if (word?.audioUrl) {
    // 生成唯一ID用于追踪此次播放请求
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
      // 仅在这是当前播放请求时才清理
      if (audioPlayingId === playingId) {
        currentAudio = null
        audioPlayingId = null
      }
    }
    currentAudio.onerror = () => {
      if (audioPlayingId === playingId) {
        currentAudio = null
        audioPlayingId = null
      }
      ElMessage.error('音频加载失败')
    }
    currentAudio.play().catch(error => {
      console.error('播放音频失败:', error)
      if (audioPlayingId === playingId) {
        currentAudio = null
        audioPlayingId = null
      }
      ElMessage.error('播放音频失败')
    })
  }
}

// 计算属性

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
  if (!word?.nextReviewTime) return true
  return new Date(word.nextReviewTime) <= new Date()
}

const continueNextBatch = async () => {
  const nextBatch = allReviewableWords.value.slice(
    currentBatchStart.value,
    currentBatchStart.value + reviewBatchSize.value
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

// 工具函数
const getMasteryType = (level) => {
  const types = {
    'NEW': 'info',
    'LEARNING': 'warning',
    'MASTERED': 'success'
  }
  return types[level] || 'info'
}

const getMasteryText = (level) => {
  const texts = {
    'NEW': '新词',
    'LEARNING': '学习中',
    'MASTERED': '已掌握'
  }
  return texts[level] || '未知'
}

const getDifficultyType = (difficulty) => {
  const types = {
    'EASY': 'success',
    'MEDIUM': 'warning',
    'HARD': 'danger'
  }
  return types[difficulty] || 'info'
}

const getDifficultyText = (difficulty) => {
  const texts = {
    'EASY': '简单',
    'MEDIUM': '中等',
    'HARD': '困难'
  }
  return texts[difficulty] || '未知'
}

const getScoreClass = (score) => {
  if (score >= 80) return 'get-score-known'
  if (score >= 50) return 'get-score-uncertain'
  return 'get-score-unknown'
}

const formatDate = (date) => {
  if (!date) return ''
  return new Date(date).toLocaleString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  })
}

onMounted(() => {
  loadVocabulary()
  window.addEventListener('resize', handleScreenResize)
})

const handleScreenResize = () => {
  screenWidth.value = window.innerWidth
  pageSize.value = screenWidth.value < 768 ? 10 : 999
  currentPage.value = 1
  loadVocabulary()
}

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleScreenResize)
  // 清理 timeout
  if (answerTimeoutId) {
    clearTimeout(answerTimeoutId)
    answerTimeoutId = null
  }

  if (currentAudio) {
    currentAudio.pause()
    currentAudio.onended = null
    currentAudio.onerror = null
    currentAudio = null
  }
})
</script>

<style scoped>
:root {
  --primary-gradient: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  --success-gradient: linear-gradient(135deg, #43e97b 0%, #38f9d7 100%);
  --warning-gradient: linear-gradient(135deg, #f6d365 0%, #fda085 100%);
  --info-gradient: linear-gradient(135deg, #84fab0 0%, #8fd3f4 100%);
}

.vocabulary-container {
  padding: 24px;
  background-color: #f6f8fa;
  min-height: 100vh;
}

/* Page Header */
.page-header {
  margin-bottom: 32px;
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
  flex-wrap: wrap;
  gap: 20px;
}

.page-title {
  font-size: 28px;
  font-weight: 800;
  color: #1a1a1a;
  margin: 0 0 8px 0;
  letter-spacing: -0.5px;
}

.page-subtitle {
  color: #666;
  margin: 0;
  font-size: 14px;
}

.search-wrapper {
  width: 100%;
  max-width: 300px;
}

.custom-search :deep(.el-input__wrapper) {
  border-radius: 12px;
  box-shadow: 0 2px 10px rgba(0,0,0,0.05);
  border: none;
  padding: 8px 16px;
}

/* Stats Cards */
.stats-cards {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(140px, 1fr));
  gap: 16px;
  margin-bottom: 32px;
  overflow-x: auto;
  padding-bottom: 10px;
}

.stat-card {
  background: white;
  padding: 20px;
  border-radius: 20px;
  display: flex;
  align-items: center;
  gap: 12px;
  box-shadow: 0 4px 20px rgba(0,0,0,0.04);
  transition: transform 0.3s;
}

.stat-card:hover {
  transform: translateY(-4px);
}

.stat-icon {
  width: 44px;
  height: 44px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 20px;
  color: white;
}

.total .stat-icon { background: linear-gradient(135deg, #6366f1, #a855f7); }
.new .stat-icon { background: linear-gradient(135deg, #0ea5e9, #2dd4bf); }
.learning .stat-icon { background: linear-gradient(135deg, #f59e0b, #ef4444); }
.mastered .stat-icon { background: linear-gradient(135deg, #10b981, #3b82f6); }

.stat-info {
  display: flex;
  flex-direction: column;
}

.stat-label {
  font-size: 12px;
  color: #888;
  font-weight: 600;
}

.stat-value {
  font-size: 18px;
  font-weight: 800;
  color: #1a1a1a;
}

/* Action Bar */
.action-bar-v2 {
  background: rgba(255, 255, 255, 0.7);
  backdrop-filter: blur(10px);
  padding: 16px 24px;
  border-radius: 20px;
  margin-bottom: 24px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 16px;
  border: 1px solid rgba(255, 255, 255, 0.5);
}

.filter-group {
  display: flex;
  align-items: center;
  gap: 12px;
}

.chip-label {
  font-size: 13px;
  font-weight: 700;
  color: #666;
}

.chips-container {
  display: flex;
  gap: 8px;
}

.filter-chip {
  padding: 6px 16px;
  border-radius: 50px;
  background: white;
  font-size: 13px;
  cursor: pointer;
  transition: all 0.3s;
  color: #666;
  border: 1px solid #eee;
}

.filter-chip.active {
  background: #1a1a1a;
  color: white;
  border-color: #1a1a1a;
  box-shadow: 0 4px 12px rgba(0,0,0,0.15);
}

.secondary-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

.icon-btn-text {
  font-weight: 600;
  border: none;
  background: transparent;
  color: #1a1a1a;
}

.start-btn {
  padding: 10px 24px;
  font-weight: 700;
  box-shadow: 0 8px 16px rgba(99, 102, 241, 0.2);
}

/* Vocabulary Grid */
.vocabulary-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 20px;
}

.word-card-v2 {
  background: white;
  border-radius: 16px;
  padding: 12px;
  position: relative;
  transition: all 0.4s cubic-bezier(0.175, 0.885, 0.32, 1.275);
  border: 1px solid #f0f0f0;
  cursor: pointer;
  display: flex;
  flex-direction: column;
}

.word-card-v2:hover {
  transform: translateY(-8px);
  box-shadow: 0 12px 30px rgba(0,0,0,0.08);
  border-color: #6366f1;
}

.card-selected {
  border: 2px solid #6366f1;
  background: rgba(99, 102, 241, 0.02);
}

.card-selection {
  position: absolute;
  top: 15px;
  right: 15px;
}

.word-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 4px;
}

.word-tags {

  margin-left: -60px;
  margin-top: -30px;
}


.phonetic {
  font-family: monospace;
  color: #94a3b8;
  font-size: 13px;
  margin-bottom: 12px;
  display: flex;
  align-items: center;
  gap: 4px;
}

.word-text {
  font-size: 15px;
  font-weight: 800;
  margin: 0;
  color: #1a1a1a;
}

.level-badge {
  font-size: 8px;
  padding: 1px 5px;
  border-radius: 5px;
  font-weight: 700;
  text-transform: uppercase;
}

.level-badge.new { background: #e0f2fe; color: #0369a1; }
.level-badge.learning { background: #fef3c7; color: #b45309; margin-left: -15px; margin-bottom: 500px; }
.level-badge.mastered { background: #dcfce7; color: #15803d; }

.phonetic {
  font-family: monospace;
  color: #94a3b8;
  font-size: 10px;
  margin-bottom: 6px;
  display: flex;
  align-items: center;
  gap: 3px;
}

.translation {
  font-size: 11px;
  color: #4b5563;
  line-height: 1.4;
  margin-bottom: 8px;
  flex-grow: 1;
  word-break: break-word;
  overflow-wrap: break-word;
}

.card-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-top: 8px;
  border-top: 1px solid #f1f5f9;
}

.review-info {
  font-size: 9px;
  font-weight: 600;
  color: #94a3b8;
}

.footer-actions {
  display: flex;
  gap: 4px;
}

/* Pagination */
.pagination-v2 {
  grid-column: 1 / -1;
  display: flex;
  justify-content: center;
  margin-top: 40px;
  padding-bottom: 40px;
}

/* 分页按钮圆形 */
.pagination-v2 :deep(.el-pager li) {
  border-radius: 50% !important;
  min-width: 32px;
  height: 32px;
  line-height: 32px;
}

/* Mobile Responsive */
@media (max-width: 768px) {
  .vocabulary-container {
    padding: 16px;
  }

  .page-header {
    flex-direction: column;
    align-items: flex-start;
  }

  .search-wrapper {
    max-width: 100%;
  }

  .action-bar-v2 {
    flex-direction: column;
    align-items: flex-start;
    padding: 16px;
  }

  .chips-container {
    overflow-x: auto;
    width: 100%;
    padding-bottom: 4px;
  }

  .secondary-actions {
    width: 100%;
    justify-content: space-between;
  }

  .start-btn {
    flex-grow: 1;
  }

  .vocabulary-grid {
    grid-template-columns: repeat(2, 1fr);
    gap: 10px;
  }
}

/* Detail Modal Styles */
:deep(.el-dialog) {
  border-radius: 24px;
  overflow: hidden;
}

/* Reuse existing functional logic classes */
/* Review V3 Styles */
.word-card-v3 {
  background: white;
  border-radius: 32px;
  padding: 40px;
  min-height: 400px;
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  text-align: center;
  box-shadow: 0 20px 50px rgba(0,0,0,0.05);
  border: 1px solid rgba(0,0,0,0.03);
  margin: 20px 0;
}

.recognition-phase, .reinforcement-phase {
  width: 100%;
  animation: fadeIn 0.4s ease-out;
}

@keyframes fadeIn {
  from { opacity: 0; transform: translateY(10px); }
  to { opacity: 1; transform: translateY(0); }
}

.word-card-v3 .word {
  font-size: 48px;
  font-weight: 800;
  margin-bottom: 32px;
  background: var(--primary-gradient);
  -webkit-background-clip: text;
  background-clip: text;
  -webkit-text-fill-color: transparent;

  letter-spacing: -1px;
}

.mcq-options {
  display: grid;
  grid-template-columns: 1fr;
  gap: 12px;
  width: 100%;
  max-width: 500px;
  margin: 0 auto;
}

.mcq-option-btn {
  height: auto;
  padding: 16px 24px;
  border-radius: 16px;
  font-size: 16px;
  font-weight: 600;
  white-space: normal;
  line-height: 1.4;
  transition: all 0.2s;
  border: 2px solid #f0f0f0;
}

.mcq-option-btn:hover {
  border-color: #6366f1;
  background: #f8faff;
  transform: scale(1.01);
}

.mcq-option-btn.is-correct {
  background-color: #f0fdf4;
  border-color: #22c55e;
  color: #15803d;
  box-shadow: 0 0 15px rgba(34, 197, 94, 0.2);
}

.mcq-option-btn.is-incorrect {
  background-color: #fef2f2;
  border-color: #ef4444;
  color: #b91c1c;
  box-shadow: 0 0 15px rgba(239, 68, 68, 0.2);
}

.unknown-btn {
  margin-top: 12px;
  border-style: dashed;
}

.mastery-adjustment {
  margin-top: 24px;
  padding-top: 24px;
  border-top: 1px solid #f0f0f0;
  width: 100%;
}

.adjustment-tip {
  font-size: 14px;
  color: #94a3b8;
  margin-bottom: 16px;
  font-weight: 600;
}

.adjustment-actions {
  display: flex;
  justify-content: center;
  gap: 12px;
  margin-bottom: 20px;
  flex-wrap: wrap;
}

.next-btn {
  font-weight: 700;
  font-size: 15px;
}

.reinforcement-phase .pronunciation {
  font-size: 18px;
  margin-bottom: 12px;
}

.reinforcement-phase .definition {
  font-size: 22px;
  font-weight: 700;
  color: #1a1a1a;
  margin-bottom: 20px;
}

.reinforcement-phase .example {
  background: #f8faff;
  padding: 16px;
  border-radius: 16px;
  text-align: left;
  font-size: 14px;
  line-height: 1.6;
  color: #475569;
  word-break: break-word;
}

.example-label {
  font-weight: 800;
  color: #6366f1;
}

/* Mobile Review Dialog Enhancements */
@media (max-width: 768px) {
  :deep(.el-dialog) {
    width: 95% !important;
    margin-top: 5vh !important;
  }
  
  .word-card-v3 {
    padding: 24px 16px;
    min-height: 350px;
    border-radius: 20px;
  }
  
  .word-card-v3 .word {
    font-size: 32px;
    margin-bottom: 24px;
  }
  
  .mcq-option-btn {
    padding: 12px 16px;
    font-size: 14px;
  }
  
  .reinforcement-phase .definition {
    font-size: 20px;
  }
  
  .adjustment-actions .el-button {
    flex: 1;
    min-width: 80px;
    padding: 8px 12px;
    font-size: 13px;
  }
}

/* Original Score Classes */
.get-score-known { color: #10b981; }
.get-score-uncertain { color: #f59e0b; }
.get-score-unknown { color: #ef4444; }

/* Review schedule hints */
.progress-info {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 4px;
}

.review-schedule-hint {
  font-size: 12px;
  padding: 2px 8px;
  border-radius: 6px;
  font-weight: 600;
  background: #fef3c7;
  color: #b45309;
}

.due-badge {
  font-size: 10px;
  padding: 1px 6px;
  border-radius: 4px;
  background: #fee2e2;
  color: #dc2626;
  font-weight: 600;
  margin-left: 6px;
}

.review-info {
  display: flex;
  align-items: center;
}
</style>
