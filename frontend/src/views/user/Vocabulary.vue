<template>
  <div class="vocabulary">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>我的生词本</span>
          <div class="header-actions">
            <el-input
              v-model="searchWord"
              placeholder="搜索单词"
              style="width: 200px; margin-right: 10px"
              @keyup.enter="loadVocabulary"
              clearable
            />
            <el-button type="primary" @click="loadVocabulary">搜索</el-button>
          </div>
        </div>
      </template>

      <!-- 统计信息 -->
      <div class="stats-bar">
        <div class="stat-item">
          <span class="label">总词汇量：</span>
          <span class="value">{{ stats.totalWords }}</span>
        </div>
        <div class="stat-item">
          <span class="label">新词：</span>
          <span class="value new">{{ stats.newWords }}</span>
        </div>
        <div class="stat-item">
          <span class="label">学习中：</span>
          <span class="value learning">{{ stats.learningWords }}</span>
        </div>
        <div class="stat-item">
          <span class="label">已掌握：</span>
          <span class="value mastered">{{ stats.masteredWords }}</span>
        </div>
      </div>

      <!-- 筛选栏 -->
      <div class="filter-bar">
        <el-form :inline="true" :model="filterForm">
          <el-form-item label="掌握程度">
            <el-select v-model="filterForm.masteryLevel" placeholder="请选择" clearable @change="loadVocabulary">
              <el-option label="全部" value="" />
              <el-option label="新词" value="NEW" />
              <el-option label="学习中" value="LEARNING" />
              <el-option label="已掌握" value="MASTERED" />
            </el-select>
          </el-form-item>
          <el-form-item label="排序">
            <el-select v-model="filterForm.sortBy" placeholder="请选择" @change="loadVocabulary">
              <el-option label="添加时间" value="addedTime" />
              <el-option label="复习次数" value="reviewCount" />
              <el-option label="最后复习" value="lastReviewed" />
              <el-option label="单词" value="word" />
            </el-select>
          </el-form-item>
          <el-form-item label="难度">
            <el-select v-model="filterForm.difficulty" placeholder="请选择" clearable @change="loadVocabulary">
              <el-option label="全部" value="" />
              <el-option label="简单" value="EASY" />
              <el-option label="中等" value="MEDIUM" />
              <el-option label="困难" value="HARD" />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" @click="startReview" :disabled="vocabulary.length === 0">
              开始复习
            </el-button>
            <el-button @click="startWordTest" :disabled="vocabulary.length === 0">
              单词测试
            </el-button>
            <el-button type="danger" @click="batchDelete" :disabled="selectedWords.length === 0">
              批量删除
            </el-button>
          </el-form-item>
        </el-form>
      </div>

      <!-- 词汇列表 -->
      <div class="vocabulary-list" v-loading="loading">
        <div class="list-header">
          <el-checkbox
            v-model="selectAll"
            @change="handleSelectAll"
            :indeterminate="isIndeterminate"
          >
            全选
          </el-checkbox>
          <span class="selected-count" v-if="selectedWords.length > 0">
            已选择 {{ selectedWords.length }} 个单词
          </span>
        </div>

        <div
          v-for="item in vocabulary"
          :key="item.id"
          class="vocabulary-item"
          :class="{ selected: selectedWords.includes(item.id) }"
          @click="showWordDetail(item)"
        >
          <div class="word-checkbox">
            <el-checkbox
              v-model="selectedWords"
              :value="item.id"
              @click.stop
            />
          </div>

          <div class="word-main">
            <div class="word-header">
              <h3 class="word">{{ item.word }}</h3>
              <div class="word-meta">
                <el-tag
                  :type="getMasteryType(item.masteryLevel)"
                  size="small"
                >
                  {{ getMasteryText(item.masteryLevel) }}
                </el-tag>
                <el-tag
                  :type="getDifficultyType(item.difficulty)"
                  size="small"
                >
                  {{ getDifficultyText(item.difficulty) }}
                </el-tag>
                <span class="review-count">复习{{ item.reviewCount }}次</span>
              </div>
            </div>

            <div class="pronunciation" v-if="item.pronunciation">
              {{ item.pronunciation }}
            </div>

            <div class="definition">{{ item.definition }}</div>

            <div class="example" v-if="item.exampleSentence">
              <span class="example-label">例句：</span>
              {{ item.exampleSentence }}
            </div>

            <div class="word-info">
              <span class="added-time">添加时间：{{ formatDate(item.addedTime) }}</span>
              <span class="last-reviewed" v-if="item.lastReviewed">
                最后复习：{{ formatDate(item.lastReviewed) }}
              </span>
            </div>
          </div>

          <div class="word-actions">
            <el-button size="small" @click.stop="playAudio(item)" :disabled="!item.audioUrl">
              <el-icon><VideoPlay /></el-icon>
            </el-button>
            <el-button size="small" @click.stop="markAsLearned(item)" v-if="item.masteryLevel !== 'MASTERED'">
              <el-icon><Check /></el-icon>
            </el-button>
            <el-button size="small" @click.stop="deleteWord(item)">
              <el-icon><Delete /></el-icon>
            </el-button>
          </div>
        </div>
      </div>

      <!-- 分页 -->
      <div class="pagination" v-if="total > 0">
        <el-pagination
          v-model:current-page="currentPage"
          v-model:page-size="pageSize"
          :page-sizes="[10, 20, 50, 100]"
          :total="total"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="loadVocabulary"
          @current-change="loadVocabulary"
        />
      </div>

      <!-- 空状态 -->
      <el-empty v-if="!loading && vocabulary.length === 0" description="暂无生词">
        <el-button type="primary" @click="$router.push('/user')">去学习</el-button>
      </el-empty>
    </el-card>

    <!-- 单词详情弹窗 -->
    <el-dialog v-model="wordDialogVisible" title="单词详解" width="500px">
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

        <div class="learning-stats" v-if="selectedWord.learningStats">
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
    <el-dialog v-model="reviewDialogVisible" title="生词复习" width="800px" :close-on-click-modal="false">
      <div class="review-session" v-if="reviewWords.length > 0">
        <div class="review-progress">
          <span>进度：{{ currentReviewIndex + 1 }} / {{ reviewWords.length }}</span>
          <el-progress :percentage="reviewProgress" />
        </div>

        <div class="word-card" :class="{ flipped: showAnswer }">
          <div class="card-front" v-show="!showAnswer">
            <h2 class="word">{{ currentReviewWord.word }}</h2>
            <div class="card-actions">
              <el-button @click="playAudio(currentReviewWord)" :disabled="!currentReviewWord.audioUrl">
                <el-icon><VideoPlay /></el-icon> 播放发音
              </el-button>
              <el-button type="primary" @click="showAnswer = true">显示答案</el-button>
            </div>
          </div>

          <div class="card-back" v-show="showAnswer">
            <h2 class="word">{{ currentReviewWord.word }}</h2>
            <p class="pronunciation">{{ currentReviewWord.pronunciation }}</p>
            <div class="definition">{{ currentReviewWord.definition }}</div>
            <div class="example" v-if="currentReviewWord.exampleSentence">
              <span class="example-label">例句：</span>
              {{ currentReviewWord.exampleSentence }}
            </div>
            <div class="review-actions">
              <el-button type="danger" @click="submitReview('UNKNOWN')">不认识</el-button>
              <el-button type="warning" @click="submitReview('UNCERTAIN')">模糊</el-button>
              <el-button type="success" @click="submitReview('KNOWN')">认识</el-button>
            </div>
          </div>
        </div>
      </div>

      <div class="review-complete" v-else>
        <h3>复习完成！</h3>
        <div class="review-summary">
          <div class="summary-item">
            <span>总复习单词：</span>
            <strong>{{ reviewSummary.totalWords }}</strong>
          </div>
          <div class="summary-item">
            <span>认识：</span>
            <strong class="known">{{ reviewSummary.knownWords }}</strong>
          </div>
          <div class="summary-item">
            <span>模糊：</span>
            <strong class="uncertain">{{ reviewSummary.uncertainWords }}</strong>
          </div>
          <div class="summary-item">
            <span>不认识：</span>
            <strong class="unknown">{{ reviewSummary.unknownWords }}</strong>
          </div>
        </div>
        <el-button type="primary" @click="reviewDialogVisible = false">完成</el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, computed, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'

const router = useRouter()

const loading = ref(false)
const searchWord = ref('')
const vocabulary = ref([])
const total = ref(0)
const currentPage = ref(1)
const pageSize = ref(20)
const selectedWords = ref([])

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
const showAnswer = ref(false)
const reviewSummary = reactive({
  totalWords: 0,
  knownWords: 0,
  uncertainWords: 0,
  unknownWords: 0
})

// 全选相关
const selectAll = ref(false)
const isIndeterminate = computed(() => {
  const selectedCount = selectedWords.value.length
  const totalCount = vocabulary.value.length
  return selectedCount > 0 && selectedCount < totalCount
})

// 加载生词本列表（模拟数据，普通用户端没有真实数据）
const loadVocabulary = async () => {
  loading.value = true
  try {
    // 普通用户端使用模拟数据
    const mockData = {
      records: [],
      total: 0
    }

    vocabulary.value = mockData.records || []
    total.value = mockData.total || 0

    // 更新统计信息
    updateStats()
  } catch (error) {
    console.error('加载生词本失败:', error)
    ElMessage.error('加载生词本失败')
  } finally {
    loading.value = false
  }
}

// 更新统计信息
const updateStats = () => {
  stats.totalWords = total.value
  stats.newWords = vocabulary.value.filter(w => w.masteryLevel === 'NEW').length
  stats.learningWords = vocabulary.value.filter(w => w.masteryLevel === 'LEARNING').length
  stats.masteredWords = vocabulary.value.filter(w => w.masteryLevel === 'MASTERED').length
}

// 显示单词详情
const showWordDetail = async (word) => {
  selectedWord.value = word
  wordDialogVisible.value = true
}

// 标记为已掌握
const markAsLearned = async (word) => {
  ElMessage.info('请先登录学生账号使用此功能')
}

// 重置为学习中
const resetToLearning = async (word) => {
  ElMessage.info('请先登录学生账号使用此功能')
}

// 删除单词
const deleteWord = async (word) => {
  try {
    await ElMessageBox.confirm(
      `确定要删除单词"${word.word}"吗？`,
      '删除确认',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )

    ElMessage.info('请先登录学生账号使用此功能')
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

    ElMessage.info('请先登录学生账号使用此功能')
  } catch (error) {
    if (error !== 'cancel') {
      console.error('批量删除失败:', error)
      ElMessage.error('批量删除失败')
    }
  }
}

// 全选处理
const handleSelectAll = (checked) => {
  selectedWords.value = checked ? vocabulary.value.map(item => item.id) : []
}

// 开始复习
const startReview = async () => {
  try {
    // 筛选需要复习的单词（排除已掌握的）
    const reviewableWords = vocabulary.value.filter(word => word.masteryLevel !== 'MASTERED')

    if (reviewableWords.length === 0) {
      ElMessage.info('没有需要复习的单词')
      return
    }

    reviewWords.value = reviewableWords
    currentReviewIndex.value = 0
    showAnswer.value = false

    // 重置复习统计
    Object.assign(reviewSummary, {
      totalWords: reviewWords.value.length,
      knownWords: 0,
      uncertainWords: 0,
      unknownWords: 0
    })

    reviewDialogVisible.value = true
  } catch (error) {
    console.error('开始复习失败:', error)
    ElMessage.error('开始复习失败')
  }
}

// 提交复习结果
const submitReview = async (result) => {
  const currentWord = reviewWords.value[currentReviewIndex.value]

  // 更新复习统计
  switch (result) {
    case 'KNOWN':
      reviewSummary.knownWords++
      break
    case 'UNCERTAIN':
      reviewSummary.uncertainWords++
      break
    case 'UNKNOWN':
      reviewSummary.unknownWords++
      break
  }

  // 更新单词掌握程度
  try {
    // 普通用户端只做本地更新
    ElMessage.success('复习记录已保存')
  } catch (error) {
    console.error('更新单词状态失败:', error)
  }

  // 进入下一个单词
  currentReviewIndex.value++
  showAnswer.value = false

  // 检查是否完成复习
  if (currentReviewIndex.value >= reviewWords.value.length) {
    reviewWords.value = []
    loadVocabulary() // 刷新列表
  }
}

// 开始单词测试
const startWordTest = () => {
  const testWords = vocabulary.value.filter(word => word.masteryLevel !== 'MASTERED')
  if (testWords.length === 0) {
    ElMessage.info('没有需要测试的单词')
    return
  }
  ElMessage.info('请先登录学生账号使用此功能')
}

let currentAudio = null

// 播放音频
const playAudio = (word) => {
  if (!word || !word.audioUrl) {
    ElMessage.warning('暂无音频可播放')
    return
  }

  // 停止播放当前音频
  if (currentAudio) {
    currentAudio.pause()
    currentAudio.src = ''
    currentAudio = null
  }

  try {
    currentAudio = new Audio(word.audioUrl)
    currentAudio.play().catch(error => {
      console.error('播放音频失败:', error)
      ElMessage.error('播放音频失败')
      currentAudio = null
    })
  } catch (error) {
    console.error('创建音频播放器失败:', error)
    ElMessage.error('播放音频失败')
    currentAudio = null
  }
}

// 计算属性
const currentReviewWord = computed(() => {
  return reviewWords.value[currentReviewIndex.value] || {}
})

const reviewProgress = computed(() => {
  if (reviewWords.value.length === 0) return 100
  return Math.round((currentReviewIndex.value / reviewWords.value.length) * 100)
})

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
})

onBeforeUnmount(() => {
  if (currentAudio) {
    currentAudio.pause()
    currentAudio.src = ''
    currentAudio = null
  }
})
</script>

<style scoped>
/* 完全复制学生端Vocabulary.vue的样式 */
.vocabulary {
  padding: 20px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.header-actions {
  display: flex;
  align-items: center;
}

.stats-bar {
  display: flex;
  gap: 30px;
  margin-bottom: 20px;
  padding: 15px;
  background: #f8f9fa;
  border-radius: 8px;
}

.stat-item {
  display: flex;
  align-items: center;
  gap: 8px;
}

.stat-item .label {
  color: #666;
  font-size: 14px;
}

.stat-item .value {
  font-weight: bold;
  font-size: 16px;
}

.stat-item .value.new {
  color: #409EFF;
}

.stat-item .value.learning {
  color: #E6A23C;
}

.stat-item .value.mastered {
  color: #67C23A;
}

.filter-bar {
  margin-bottom: 20px;
  padding-bottom: 15px;
  border-bottom: 1px solid #f0f0f0;
}

.vocabulary-list {
  min-height: 400px;
}

.list-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 0;
  border-bottom: 1px solid #f0f0f0;
  margin-bottom: 10px;
}

.selected-count {
  color: #409EFF;
  font-size: 14px;
}

.vocabulary-item {
  padding: 15px;
  border-bottom: 1px solid #f0f0f0;
  display: flex;
  align-items: flex-start;
  cursor: pointer;
  transition: all 0.3s;
}

.vocabulary-item:hover {
  background-color: #f8f9fa;
}

.vocabulary-item.selected {
  background-color: #e6f7ff;
  border-color: #409EFF;
}

.word-checkbox {
  margin-right: 15px;
  padding-top: 5px;
}

.word-main {
  flex: 1;
}

.word-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

.word-header h3 {
  margin: 0;
  color: #333;
  font-size: 18px;
}

.word-meta {
  display: flex;
  align-items: center;
  gap: 10px;
}

.review-count {
  font-size: 12px;
  color: #666;
}

.pronunciation {
  color: #666;
  font-style: italic;
  margin: 8px 0;
}

.definition {
  color: #333;
  line-height: 1.5;
  margin: 8px 0;
}

.example {
  color: #666;
  font-size: 14px;
  margin-top: 8px;
}

.example-label {
  font-weight: bold;
}

.word-info {
  display: flex;
  gap: 20px;
  margin-top: 10px;
  font-size: 12px;
  color: #888;
}

.word-actions {
  display: flex;
  flex-direction: column;
  gap: 5px;
  margin-left: 15px;
}

.pagination {
  margin-top: 20px;
  text-align: center;
}

/* 单词详情弹窗样式 */
.word-detail {
  padding: 20px 0;
}

.word-detail .word-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
  padding-bottom: 15px;
  border-bottom: 1px solid #f0f0f0;
}

.word-detail h2 {
  margin: 0;
  color: #333;
}

.word-content {
  margin-bottom: 20px;
}

.word-content .pronunciation {
  color: #666;
  font-style: italic;
  font-size: 16px;
  margin-bottom: 10px;
}

.word-content .definition {
  color: #333;
  font-size: 14px;
  line-height: 1.5;
  margin-bottom: 15px;
}

.example-sentence {
  color: #666;
  font-size: 14px;
  margin-bottom: 15px;
}

.example-label {
  font-weight: bold;
}

.word-meta-info {
  display: flex;
  gap: 10px;
  margin-top: 10px;
}

.learning-stats {
  margin: 20px 0;
  padding: 15px;
  background: #f8f9fa;
  border-radius: 8px;
}

.learning-stats h4 {
  margin: 0 0 15px 0;
  color: #333;
}

.stats-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
}

.stats-grid .stat-item {
  display: flex;
  justify-content: space-between;
  padding: 8px 0;
}

.stats-grid .label {
  color: #666;
}

.stats-grid .value {
  color: #333;
  font-weight: bold;
}

.word-actions {
  display: flex;
  gap: 10px;
  justify-content: center;
}

/* 复习对话框样式 */
.review-session {
  padding: 20px;
}

.review-progress {
  margin-bottom: 30px;
  text-align: center;
}

.review-progress span {
  display: block;
  margin-bottom: 10px;
  font-size: 16px;
  color: #666;
}

.word-card {
  height: 300px;
  position: relative;
  transform-style: preserve-3d;
  transition: transform 0.6s;
  cursor: pointer;
}

.word-card.flipped {
  transform: rotateY(180deg);
}

.card-front,
.card-back {
  position: absolute;
  width: 100%;
  height: 100%;
  backface-visibility: hidden;
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  padding: 40px;
  box-sizing: border-box;
  border: 2px solid #e4e7ed;
  border-radius: 8px;
}

.card-back {
  transform: rotateY(180deg);
  background: #f8f9fa;
}

.card-front .word {
  font-size: 36px;
  color: #333;
  margin-bottom: 30px;
  text-align: center;
}

.card-back .word {
  font-size: 24px;
  color: #333;
  margin-bottom: 15px;
  text-align: center;
}

.card-back .pronunciation {
  color: #666;
  font-style: italic;
  margin-bottom: 15px;
}

.card-back .definition {
  color: #333;
  text-align: center;
  line-height: 1.6;
  margin-bottom: 15px;
  font-size: 16px;
}

.card-back .example {
  color: #666;
  text-align: center;
  font-size: 14px;
}

.example-label {
  font-weight: bold;
}

.card-actions,
.review-actions {
  display: flex;
  gap: 15px;
  margin-top: 30px;
}

.review-actions {
  position: absolute;
  bottom: 30px;
  width: 100%;
  justify-content: center;
}

.review-complete {
  text-align: center;
  padding: 40px;
}

.review-complete h3 {
  margin-bottom: 30px;
  color: #333;
  font-size: 24px;
}

.review-summary {
  margin-bottom: 30px;
}

.summary-item {
  display: flex;
  justify-content: space-between;
  padding: 10px 0;
  font-size: 16px;
}

.summary-item .known {
  color: #67C23A;
}

.summary-item .uncertain {
  color: #E6A23C;
}

.summary-item .unknown {
  color: #F56C6C;
}
</style>