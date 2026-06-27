<template>
  <div class="interactive-text-page">
    <div class="page-header">
      <el-breadcrumb separator="/">
        <el-breadcrumb-item :to="{ path: '/user/dashboard' }">仪表板</el-breadcrumb-item>
        <el-breadcrumb-item :to="{ path: '/user/courses' }">我的课程</el-breadcrumb-item>
        <el-breadcrumb-item :to="{ path: `/user/courses/${courseId}/units` }">{{ courseName }}</el-breadcrumb-item>
        <el-breadcrumb-item :to="{ path: `/user/units/${unitId}/content` }">{{ unitName }}</el-breadcrumb-item>
        <el-breadcrumb-item>{{ textTitle }}</el-breadcrumb-item>
      </el-breadcrumb>

      <div class="header-actions">
        <el-button @click="goBack" type="default" icon="ArrowLeft">
          {{ fromSource === 'assignment' ? '返回任务' : '返回' }}
        </el-button>
        <el-tag v-if="fromSource === 'assignment'" type="info" style="margin-left: 10px;">
          任务学习
        </el-tag>
        <el-button @click="startReading" type="primary" icon="View" :loading="recording">
          {{ recording ? '学习中...' : '开始学习' }}
        </el-button>
      </div>
    </div>

    <el-card class="text-card">
      <template #header>
        <div class="text-header">
          <div class="text-info">
            <h2 class="text-title">{{ textTitle }}</h2>
            <div class="text-meta">
              <span class="difficulty-tag" :class="difficultyClass">{{ difficultyText }}</span>
              <span class="word-count">{{ wordCount }} 个单词</span>
              <span class="estimated-time">预计学习时间：{{ estimatedTime }}分钟</span>
            </div>
          </div>
          <div class="learning-progress">
            <el-progress
              :percentage="timeBasedProgress"
              :format="formatTimeProgress"
              type="circle"
              :width="80"
              :stroke-width="6"
              :status="isStudyTimeCompleted ? 'success' : null"
            />
            <div class="progress-label">
              <span v-if="isStudyTimeCompleted" class="completed-label">已完成</span>
              <span v-else class="time-label">{{ formatRemainingTime() }}</span>
            </div>
          </div>
        </div>
      </template>

      <div class="text-body">
        <InteractiveText
          :text="textContent"
          :text-id="textId"
          :unit-id="unitId"
          @word-click="handleWordClick"
          @learning-record="handleLearningRecord"
        />
      </div>

      <div class="text-footer">
        <div class="learning-stats">
          <div class="stat-item" :class="{ 'active-timer': recording }">
            <el-icon><Clock /></el-icon>
            <span>阅读时长：<strong>{{ formatStudyTime(studyTime) }}</strong></span>
            <span v-if="recording" class="timer-indicator">●</span>
          </div>
          <div class="stat-item" v-if="isStudyTimeCompleted">
            <el-icon><Check /></el-icon>
            <span>学习要求已完成 ✓</span>
          </div>
          <div class="stat-item" v-else>
            <el-icon><Timer /></el-icon>
            <span>还需阅读：<strong>{{ formatRemainingTime() }}</strong></span>
          </div>
          <!-- 实时进度指示器 -->
          <div class="stat-item" v-if="recording">
            <div class="real-time-progress">
              <div class="progress-bar">
                <div
                  class="progress-fill"
                  :style="{ width: `${timeBasedProgress}%` }"
                ></div>
              </div>
              <span class="progress-text">{{ timeBasedProgress }}%</span>
            </div>
          </div>
        </div>

        <div class="action-buttons">
          <el-button
            v-if="isStudyTimeCompleted && !isCompleted"
            @click="markAsCompleted"
            type="success"
            icon="Check"
          >
            确认完成学习
          </el-button>
          <el-button v-else-if="isCompleted" type="success" disabled>
            已完成学习
          </el-button>
          <el-button
            v-else
            type="primary"
            :disabled="!recording"
            @click="pauseReading"
          >
            {{ recording ? '暂停阅读' : '继续阅读' }}
          </el-button>
          <el-button @click="addToFavorite" type="warning" icon="Star">
            {{ isFavorite ? '已收藏' : '收藏课文' }}
          </el-button>
        </div>
      </div>
    </el-card>

    <!-- 学习建议弹窗 -->
    <el-dialog v-model="showSuggestionDialog" title="学习建议" width="500px">
      <div class="suggestion-content">
        <div class="suggestion-item" v-for="(suggestion, index) in learningSuggestions" :key="index">
          <el-icon class="suggestion-icon"><InfoFilled /></el-icon>
          <span>{{ suggestion }}</span>
        </div>
      </div>
      <template #footer>
        <el-button @click="showSuggestionDialog = false">知道了</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onBeforeUnmount } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { View, Clock, InfoFilled, Check, Timer } from '@element-plus/icons-vue'
import InteractiveText from '@/components/InteractiveText.vue'
import studentApi, { getVocabularyStats } from '@/api/student'

const route = useRoute()
const router = useRouter()

// 响应式数据
const textId = ref(Number(route.query.textId) || 0)
const assignmentId = ref(route.query.assignmentId || null)
const fromSource = ref(route.query.from || null)
const courseId = ref(null)
const unitId = ref(null)
const courseName = ref('')
const unitName = ref('')
const textTitle = ref('')
const textContent = ref('')
const difficulty = ref('MEDIUM')
const learningProgress = ref(0) // 原有的学习进度
const learnedWordsCount = ref(0)
const studyTime = ref(0)
const isCompleted = ref(false)
const isFavorite = ref(false)
const recording = ref(false)
const showSuggestionDialog = ref(false)
const learningSuggestions = ref([])

// 学习计时器
let studyTimer = null
let completionTimeoutId = null
let studyStartTime = null
let accumulatedTime = 0 // 累计学习时间（秒）
let lastSavedStudyTime = 0 // 已保存到后端的学习时间（秒），用于计算增量避免重复累加
const REQUIRED_STUDY_TIME = 10 * 60 * 1000 // 10分钟，毫秒

// 计算属性
const isStudyTimeCompleted = computed(() => {
  return studyTime.value >= (REQUIRED_STUDY_TIME / 1000) // 转换为秒
})

const timeBasedProgress = computed(() => {
  const progress = Math.min(100, Math.round((studyTime.value / (REQUIRED_STUDY_TIME / 1000)) * 100))
  return progress
})

// 计算属性
const difficultyClass = computed(() => {
  return {
    'difficulty-easy': difficulty.value === 'EASY',
    'difficulty-medium': difficulty.value === 'MEDIUM',
    'difficulty-hard': difficulty.value === 'HARD'
  }
})

const difficultyText = computed(() => {
  const difficultyMap = {
    'EASY': '简单',
    'MEDIUM': '中等',
    'HARD': '困难'
  }
  return difficultyMap[difficulty.value] || '未知'
})

const wordCount = computed(() => {
  if (!textContent.value) return 0
  // 简单计算单词数量
  return textContent.value.split(/\s+/).filter(word => word.length > 0).length
})

const estimatedTime = computed(() => {
  // 预计每分钟阅读100个单词
  return Math.ceil(wordCount.value / 100)
})

// 方法
const loadTextDetail = async () => {
  try {
    // 构建请求参数，如果是在任务中学习，传递任务ID
    const params = { textId: textId.value }
    if (assignmentId.value) {
      params.assignmentId = assignmentId.value
    }

    const response = await studentApi.getTextDetail(params)
    if (response.code === 200) {
      const data = response.data
      textTitle.value = data?.title || '未知课文'
      textContent.value = data?.content || ''
      difficulty.value = data?.difficulty || 'MEDIUM'
      courseId.value = data?.courseId || null
      unitId.value = data?.unitId || null
      courseName.value = data?.courseName || '未知课程'
      unitName.value = data?.unitName || '未知单元'

      // 从后端获取学习进度和时长
      learningProgress.value = data?.learningProgress || 0
      learnedWordsCount.value = data?.learnedWordsCount || 0
      isCompleted.value = data?.isCompleted || false

      // 如果是从任务页面跳转来的，显示任务信息
      if (fromSource.value === 'assignment' && assignmentId.value) {
        ElMessage.info('正在完成任务中的课文学习')
      }

      // 恢复已学习时长（转换为秒）
      if (data?.readTime && data.readTime > 0) {
        studyTime.value = data.readTime
        accumulatedTime = data.readTime // 将已学习时长作为累计时间
        // 已保存的时长不应再次发送，同步 lastSavedStudyTime 避免首次保存重复累加
        lastSavedStudyTime = data.readTime
      } else {
        // 确保初始值为0
        studyTime.value = 0
        accumulatedTime = 0
        lastSavedStudyTime = 0
      }

      // 检查是否已经满足学习时长要求
      if (isStudyTimeCompleted.value) {
        recording.value = false
        studyStartTime = null // 重置开始时间
      } else {
        // 确保初始状态是未开始计时
        recording.value = false
        studyStartTime = null
      }

      // 生成学习建议
      generateLearningSuggestions()
    } else {
      ElMessage.error('获取课文详情失败')
    }
  } catch (error) {
    console.error('加载课文详情失败:', error)
    ElMessage.error('加载课文详情失败')
  }
}

const generateLearningSuggestions = () => {
  const suggestions = []

  if (difficulty.value === 'HARD') {
    suggestions.push('这是一篇较难的文章，建议先学习相关的单词')
  }

  if (wordCount.value > 500) {
    suggestions.push('文章较长，建议分段学习，注意休息')
  }

  if (learningProgress.value < 50) {
    suggestions.push('建议先通读全文，了解文章大意')
  }

  if (learnedWordsCount.value < wordCount.value * 0.3) {
    suggestions.push('建议多点击生词查看词义，加强词汇学习')
  }

  if (suggestions.length === 0) {
    suggestions.push('点击单词可以查看详细解释和例句')
    suggestions.push('遇到生词可以添加到生词本进行复习')
  }

  learningSuggestions.value = suggestions
}

const startReading = () => {
  recording.value = true
  // 如果没有开始时间或者是恢复学习的情况，重置开始时间
  if (!studyStartTime) {
    studyStartTime = Date.now()
  }
  startStudyTimer()

  ElMessage.success('开始阅读课文')
}

const pauseReading = async () => {
  recording.value = false

  // 累加当前会话的学习时间
  if (studyStartTime) {
    const currentSessionTime = Math.floor((Date.now() - studyStartTime) / 1000)
    accumulatedTime += currentSessionTime
    studyStartTime = null // 重置开始时间
  }

  stopStudyTimer()
  // 清理任务完成提示的延迟
  if (completionTimeoutId !== null) {
    clearTimeout(completionTimeoutId)
    completionTimeoutId = null
  }

  // 保存当前学习时长
  await saveLearningRecord()

  ElMessage.info('暂停阅读计时')
}

const saveLearningRecord = async () => {
  try {
    if (studyTime.value > 0) {
      // 计算自上次保存以来的增量，避免重复累加导致时长虚高
      const increment = studyTime.value - lastSavedStudyTime
      if (increment <= 0) return
      const recordPayload = {
        type: 'TEXT_VIEW',
        duration: increment,
        textId: textId.value,
        // 如果是从任务来的，包含任务ID
        ...(assignmentId.value && { assignmentId: assignmentId.value })
      }

      await studentApi.recordLearning(recordPayload)
      lastSavedStudyTime = studyTime.value
    }
  } catch (error) {
    console.error('保存学习记录失败:', error)
  }
}

const startStudyTimer = () => {
  // 清除现有的计时器
  if (studyTimer) {
    clearInterval(studyTimer)
  }

  studyTimer = setInterval(() => {
    if (recording.value && studyStartTime) {
      // 计算当前会话的时间 + 之前累计的时间
      const currentSessionTime = Math.floor((Date.now() - studyStartTime) / 1000)
      const totalTime = accumulatedTime + currentSessionTime
      studyTime.value = totalTime
    }
  }, 100) // 每100毫秒更新一次，提供更平滑的体验
}

const stopStudyTimer = () => {
  if (studyTimer) {
    clearInterval(studyTimer)
    studyTimer = null
  }
}

const handleWordClick = (wordData) => {
  // 单词点击事件已在InteractiveText组件中处理
}

const handleLearningRecord = async (recordData) => {
  try {
    // 只处理课文观看记录，忽略单词点击
    if (recordData.type === 'TEXT_VIEW') {
      // 使用saveLearningRecord统一处理
      await saveLearningRecord()
    }
    // WORD_VIEW 类型被忽略，因为单词点击只是辅助功能
  } catch (error) {
    console.error('记录学习失败:', error)
  }
}

const markAsCompleted = async () => {
  try {
    await ElMessageBox.confirm('确定要将此课文标记为已完成吗？', '确认操作', {
      type: 'warning'
    })

    // ⚠️ 标记完成前先暂停计时并 flush 最后一段增量到 learning_records，
    // 后端 markTextAsCompleted 只更新 text_learning_records，不写入 learning_records，
    // 所以此处必须提交未保存的增量，否则最后一段阅读时间会丢失。
    if (recording.value) {
      recording.value = false
      if (studyStartTime) {
        const currentSessionTime = Math.floor((Date.now() - studyStartTime) / 1000)
        accumulatedTime += currentSessionTime
        studyStartTime = null
      }
    }
    stopStudyTimer()
    await saveLearningRecord()

    const response = await studentApi.markTextAsCompleted(textId.value, assignmentId.value)
    if (response.code === 200) {
      isCompleted.value = true
      learningProgress.value = 100

      // 如果有任务进度信息，显示详细的完成状态
      if (response.data?.completionRate !== undefined) {
        const completionRate = Math.round(response.data.completionRate)
        ElMessage.success(`课文已标记为完成，任务进度: ${completionRate}%`)

        // 如果任务完成率>=100%，询问是否返回任务列表
        if (completionRate >= 100 && fromSource.value === 'assignment') {
          completionTimeoutId = setTimeout(() => {
            ElMessageBox.confirm('恭喜！任务已完成，是否返回首页？', '任务完成', {
              confirmButtonText: '返回首页',
              cancelButtonText: '继续学习',
              type: 'success'
            }).then(() => {
              router.push('/user')
            }).catch(() => {
              // 用户选择继续学习
            })
          }, 1000)
        }
      } else {
        ElMessage.success('课文已标记为完成')
      }
    } else {
      ElMessage.error('标记完成失败')
    }
  } catch (error) {
    if (error !== 'cancel') {
      console.error('标记课文完成失败:', error)
      ElMessage.error('标记课文完成失败')
    }
  }
}

const addToFavorite = async () => {
  try {
    // 这里需要实现收藏课文的API
    isFavorite.value = !isFavorite.value
    ElMessage.success(isFavorite.value ? '已添加到收藏' : '已取消收藏')
  } catch (error) {
    console.error('收藏课文失败:', error)
    ElMessage.error('收藏课文失败')
  }
}

const formatProgress = (percentage) => {
  return `${percentage}%`
}

const formatTimeProgress = (percentage) => {
  if (isStudyTimeCompleted.value) {
    return '完成'
  }
  return `${percentage}%`
}

const formatStudyTime = (seconds) => {
  if (!seconds || isNaN(seconds) || seconds < 0) return '0分0秒'
  const hours = Math.floor(seconds / 3600)
  const minutes = Math.floor((seconds % 3600) / 60)
  const remainingSeconds = Math.floor(seconds % 60)

  if (hours > 0) {
    return `${hours}小时${minutes}分${remainingSeconds}秒`
  } else {
    return `${minutes}分${remainingSeconds}秒`
  }
}

const formatRemainingTime = () => {
  const remainingSeconds = Math.max(0, (REQUIRED_STUDY_TIME / 1000) - studyTime.value)
  return formatStudyTime(remainingSeconds)
}

const goBack = () => {
  // 检查来源，智能返回
  const from = route.query.from
  if (from === 'assignment' || from === 'user-upload') {
    // 返回用户首页
    router.push('/user')
  } else {
    // 其他情况，使用默认返回
    router.go(-1)
  }
}

// 生命周期
onMounted(() => {
  loadTextDetail()
  // 添加页面可见性变化监听
  document.addEventListener('visibilitychange', handleVisibilityChange)
})

onBeforeUnmount(() => {
  // 移除事件监听
  document.removeEventListener('visibilitychange', handleVisibilityChange)

  // 页面卸载前自动保存学习记录
  if (studyTime.value > 0) {
    // 先暂停计时，确保时间计算正确
    if (recording.value) {
      recording.value = false
      if (studyStartTime) {
        const currentSessionTime = Math.floor((Date.now() - studyStartTime) / 1000)
        accumulatedTime += currentSessionTime
        studyStartTime = null
      }
    }
    saveLearningRecord()
  }
  stopStudyTimer()
  // 清理任务完成提示的延迟
  if (completionTimeoutId !== null) {
    clearTimeout(completionTimeoutId)
    completionTimeoutId = null
  }
})

// 处理页面可见性变化（切换标签页时自动暂停）
const handleVisibilityChange = async () => {
  if (document.hidden && recording.value) {
    // 页面隐藏时自动暂停学习并保存
    await pauseReading()
  }
}

const recordLearning = async (type, word, duration) => {
  try {
    const payload = { type, duration }
    if (type === 'TEXT_VIEW') {
      payload.textId = textId.value
    } else if (type === 'WORD_VIEW' && word) {
      payload.word = word
    }

    await studentApi.recordLearning(payload)
  } catch (error) {
    console.error('记录学习失败:', error)
  }
}
</script>

<style scoped>
.interactive-text-page {
  padding: 20px;
  max-width: 1200px;
  margin: 0 auto;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
  padding: 16px 20px;
  background: white;
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
}

.header-actions {
  display: flex;
  gap: 12px;
}

.text-card {
  background: white;
  border-radius: 12px;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.1);
}

.text-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
}

.text-info {
  flex: 1;
}

.text-title {
  margin: 0 0 12px 0;
  font-size: 24px;
  font-weight: 600;
  color: #333;
}

.text-meta {
  display: flex;
  align-items: center;
  gap: 16px;
  flex-wrap: wrap;
}

.difficulty-tag {
  padding: 4px 12px;
  border-radius: 16px;
  font-size: 12px;
  font-weight: 500;
}

.difficulty-easy {
  background: #f6ffed;
  color: #52c41a;
  border: 1px solid #b7eb8f;
}

.difficulty-medium {
  background: #fff7e6;
  color: #fa8c16;
  border: 1px solid #ffd591;
}

.difficulty-hard {
  background: #fff2e8;
  color: #fa541c;
  border: 1px solid #ffbb96;
}

.word-count,
.estimated-time {
  color: #666;
  font-size: 14px;
}

.learning-progress {
  margin-left: 20px;
}

.text-body {
  padding: 0;
}

.text-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 24px;
  padding-top: 20px;
  border-top: 1px solid #f0f0f0;
}

.learning-stats {
  display: flex;
  gap: 24px;
}

.stat-item {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #666;
  font-size: 14px;
}

.stat-item .el-icon {
  color: #1890ff;
}

.stat-item.active-timer {
  color: #52c41a;
  font-weight: 600;
}

.stat-item.active-timer .el-icon {
  color: #52c41a;
  animation: pulse 1.5s ease-in-out infinite;
}

.timer-indicator {
  color: #52c41a;
  font-size: 16px;
  margin-left: 8px;
  animation: blink 1s ease-in-out infinite;
}

@keyframes pulse {
  0%, 100% {
    transform: scale(1);
  }
  50% {
    transform: scale(1.1);
  }
}

@keyframes blink {
  0%, 100% {
    opacity: 1;
  }
  50% {
    opacity: 0.3;
  }
}

/* 实时进度条样式 */
.real-time-progress {
  display: flex;
  align-items: center;
  gap: 12px;
  flex: 1;
}

.progress-bar {
  flex: 1;
  height: 6px;
  background-color: #f0f0f0;
  border-radius: 3px;
  overflow: hidden;
  min-width: 100px;
}

.progress-fill {
  height: 100%;
  background: linear-gradient(90deg, #52c41a, #73d13d);
  transition: width 0.1s ease-out;
  border-radius: 3px;
}

.progress-text {
  font-size: 12px;
  font-weight: 600;
  color: #52c41a;
  min-width: 40px;
  text-align: right;
}

.progress-label {
  text-align: center;
  margin-top: 8px;
}

.completed-label {
  color: #67c23a;
  font-weight: 600;
  font-size: 14px;
}

.time-label {
  color: #666;
  font-size: 12px;
}

.action-buttons {
  display: flex;
  gap: 12px;
}

.suggestion-content {
  padding: 16px 0;
}

.suggestion-item {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  margin-bottom: 16px;
  padding: 12px 16px;
  background: #f8f9ff;
  border-radius: 8px;
  border-left: 3px solid #1890ff;
}

.suggestion-icon {
  color: #1890ff;
  margin-top: 2px;
  flex-shrink: 0;
}

.suggestion-item:last-child {
  margin-bottom: 0;
}

/* 响应式设计 */
@media (max-width: 768px) {
  .interactive-text-page {
    padding: 12px;
  }

  .page-header {
    flex-direction: column;
    gap: 16px;
    align-items: stretch;
  }

  .text-header {
    flex-direction: column;
    gap: 16px;
    align-items: stretch;
  }

  .learning-progress {
    margin-left: 0;
    align-self: center;
  }

  .text-meta {
    flex-direction: column;
    align-items: flex-start;
    gap: 8px;
  }

  .text-footer {
    flex-direction: column;
    gap: 16px;
    align-items: stretch;
  }

  .learning-stats {
    justify-content: center;
  }

  .action-buttons {
    justify-content: center;
  }
}
</style>