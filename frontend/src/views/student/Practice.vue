<template>
  <div class="practice-page">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>在线练习</span>
          <div class="header-actions">
            <el-button type="primary" @click="startNewPractice">开始新练习</el-button>
            <el-button @click="showHistory">练习历史</el-button>
          </div>
        </div>
      </template>

      <div v-if="!practiceStarted" class="practice-intro">
        <el-empty description="点击"开始新练习"开始答题">
          <el-button type="primary" @click="startNewPractice">开始新练习</el-button>
        </el-empty>
      </div>

      <div v-else class="practice-content">
        <!-- 练习进度 -->
        <div class="practice-header">
          <div class="progress-info">
            <span class="current-question">题目 {{ currentQuestionIndex + 1 }} / {{ questions.length }}</span>
            <el-progress
              :percentage="getProgress()"
              :color="progressColor"
              :stroke-width="8"
            />
          </div>
          <div class="timer">
            <el-icon><Clock /></el-icon>
            <span>{{ formatTime(elapsedTime) }}</span>
          </div>
        </div>

        <!-- 题目内容 -->
        <div v-if="currentQuestion" class="question-container">
          <div class="question-header">
            <el-tag :type="getDifficultyType(currentQuestion.difficulty)" size="large">
              {{ getDifficultyText(currentQuestion.difficulty) }}
            </el-tag>
            <el-tag v-if="currentQuestion.knowledgePoint" type="info" size="large">
              {{ currentQuestion.knowledgePoint }}
            </el-tag>
          </div>

          <div class="question-content">
            <h3>{{ currentQuestion.question }}</h3>
            <p v-if="currentQuestion.context" class="context">{{ currentQuestion.context }}</p>
          </div>

          <!-- 选项 -->
          <div class="question-options">
            <el-radio-group
              v-if="currentQuestion.type === 'SINGLE_CHOICE'"
              v-model="selectedAnswer"
              :disabled="answerSubmitted"
            >
              <el-radio
                v-for="(option, index) in currentQuestion.options"
                :key="index"
                :label="option.key"
                class="option-item"
                :class="{
                  'correct-answer': answerSubmitted && option.key === currentQuestion.correctAnswer,
                  'wrong-answer': answerSubmitted && option.key === selectedAnswer && option.key !== currentQuestion.correctAnswer
                }"
              >
                <span class="option-label">{{ option.key }}.</span>
                <span class="option-text">{{ option.value }}</span>
              </el-radio>
            </el-radio-group>

            <el-checkbox-group
              v-if="currentQuestion.type === 'MULTIPLE_CHOICE'"
              v-model="selectedAnswers"
              :disabled="answerSubmitted"
            >
              <el-checkbox
                v-for="(option, index) in currentQuestion.options"
                :key="index"
                :label="option.key"
                class="option-item"
              >
                <span class="option-label">{{ option.key }}.</span>
                <span class="option-text">{{ option.value }}</span>
              </el-checkbox>
            </el-checkbox-group>
          </div>

          <!-- 答案解析 -->
          <div v-if="answerSubmitted" class="answer-analysis">
            <el-alert
              :title="isCorrect ? '回答正确！' : '回答错误'"
              :type="isCorrect ? 'success' : 'error'"
              :closable="false"
              show-icon
            >
              <div class="analysis-content">
                <p v-if="!isCorrect">
                  <strong>正确答案：</strong>{{ currentQuestion.correctAnswer }}
                </p>
                <p v-if="currentQuestion.analysis">
                  <strong>解析：</strong>{{ currentQuestion.analysis }}
                </p>
                <p v-if="currentQuestion.knowledgePoint">
                  <strong>知识点：</strong>
                  <el-tag type="info" size="small">{{ currentQuestion.knowledgePoint }}</el-tag>
                </p>
              </div>
            </el-alert>
          </div>

          <!-- 操作按钮 -->
          <div class="question-actions">
            <el-button @click="previousQuestion" :disabled="currentQuestionIndex === 0">
              <el-icon><ArrowLeft /></el-icon>
              上一题
            </el-button>
            <el-button
              v-if="!answerSubmitted"
              type="primary"
              @click="submitAnswer"
              :disabled="!hasAnswer()"
            >
              提交答案
            </el-button>
            <el-button
              v-else
              type="primary"
              @click="nextQuestion"
            >
              {{ currentQuestionIndex < questions.length - 1 ? '下一题' : '完成练习' }}
              <el-icon><ArrowRight /></el-icon>
            </el-button>
            <el-button @click="favoriteQuestion" :disabled="!answerSubmitted">
              <el-icon><Star /></el-icon>
              收藏
            </el-button>
          </div>
        </div>

        <!-- 题目导航 -->
        <div class="question-navigation">
          <h4>题目导航</h4>
          <div class="nav-grid">
            <div
              v-for="(question, index) in questions"
              :key="index"
              class="nav-item"
              :class="{
                'active': index === currentQuestionIndex,
                'answered': question.answered,
                'correct': question.isCorrect,
                'wrong': question.answered && !question.isCorrect
              }"
              @click="jumpToQuestion(index)"
            >
              {{ index + 1 }}
            </div>
          </div>
        </div>
      </div>
    </el-card>

    <!-- 练习历史对话框 -->
    <el-dialog
      v-model="historyDialogVisible"
      title="练习历史"
      width="800px"
    >
      <el-table :data="historyList" stripe>
        <el-table-column prop="date" label="日期" width="180">
          <template #default="scope">
            {{ formatDate(scope.row.date) }}
          </template>
        </el-table-column>
        <el-table-column prop="questionCount" label="题目数量" width="100" />
        <el-table-column prop="correctCount" label="正确数量" width="100" />
        <el-table-column label="正确率" width="100">
          <template #default="scope">
            {{ getAccuracy(scope.row) }}%
          </template>
        </el-table-column>
        <el-table-column prop="duration" label="用时" width="100">
          <template #default="scope">
            {{ formatDuration(scope.row.duration) }}
          </template>
        </el-table-column>
        <el-table-column label="操作">
          <template #default="scope">
            <el-button size="small" @click="viewHistoryDetail(scope.row)">
              查看详情
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, onUnmounted, computed } from 'vue'
import { ElMessage } from 'element-plus'
import {
  getPracticeQuestions,
  startPracticeSession,
  submitAnswer as submitAnswerApi,
  completePracticeSession,
  getPracticeHistory,
  favoriteQuestion as favoriteQuestionApi
} from '@/api/practice'

// 响应式数据
const practiceStarted = ref(false)
const questions = ref([])
const currentQuestionIndex = ref(0)
const currentQuestion = computed(() => questions.value[currentQuestionIndex.value])
const selectedAnswer = ref('')
const selectedAnswers = ref([])
const answerSubmitted = ref(false)
const isCorrect = ref(false)
const elapsedTime = ref(0)
const timer = ref(null)
const sessionId = ref(null)
const historyDialogVisible = ref(false)
const historyList = ref([])

const progressColor = computed(() => {
  const progress = getProgress()
  if (progress >= 80) return '#67c23a'
  if (progress >= 50) return '#e6a23c'
  return '#409EFF'
})

// 方法
const startNewPractice = async () => {
  try {
    // 模拟获取练习题目
    const mockQuestions = [
      {
        id: 1,
        type: 'SINGLE_CHOICE',
        difficulty: 'EASY',
        question: 'What is the past tense of "go"?',
        context: null,
        knowledgePoint: '动词过去式',
        options: [
          { key: 'A', value: 'goed' },
          { key: 'B', value: 'went' },
          { key: 'C', value: 'gone' },
          { key: 'D', value: 'goes' }
        ],
        correctAnswer: 'B',
        analysis: '"go"的过去式是不规则变化，为"went"。',
        answered: false,
        isCorrect: false
      },
      {
        id: 2,
        type: 'SINGLE_CHOICE',
        difficulty: 'MEDIUM',
        question: 'Choose the correct sentence:',
        context: null,
        knowledgePoint: '主谓一致',
        options: [
          { key: 'A', value: 'He have a book.' },
          { key: 'B', value: 'He has a book.' },
          { key: 'C', value: 'He having a book.' },
          { key: 'D', value: 'He are having a book.' }
        ],
        correctAnswer: 'B',
        analysis: '第三人称单数主语后面的动词have要变为has。',
        answered: false,
        isCorrect: false
      },
      {
        id: 3,
        type: 'MULTIPLE_CHOICE',
        difficulty: 'HARD',
        question: 'Which of the following are correct? (Multiple answers)',
        context: null,
        knowledgePoint: '时态',
        options: [
          { key: 'A', value: 'I am studying English now.' },
          { key: 'B', value: 'I study English every day.' },
          { key: 'C', value: 'I studied English yesterday.' },
          { key: 'D', value: 'I am study English.' }
        ],
        correctAnswer: 'A,B,C',
        analysis: '选项A、B、C分别使用了现在进行时、一般现在时和一般过去时，都是正确的。选项D缺少be动词后的-ing形式。',
        answered: false,
        isCorrect: false
      }
    ]

    questions.value = mockQuestions
    currentQuestionIndex.value = 0
    practiceStarted.value = true
    elapsedTime.value = 0
    startTimer()

    // 创建练习会话
    // const response = await startPracticeSession({ questionIds: mockQuestions.map(q => q.id) })
    // sessionId.value = response.data.sessionId

    ElMessage.success('练习开始！')
  } catch (error) {
    ElMessage.error('开始练习失败')
  }
}

const submitAnswer = async () => {
  try {
    const answer = currentQuestion.value.type === 'SINGLE_CHOICE'
      ? selectedAnswer.value
      : selectedAnswers.value.join(',')

    if (!answer) {
      ElMessage.warning('请选择答案')
      return
    }

    // 提交答案到服务器
    // await submitAnswerApi({
    //   questionId: currentQuestion.value.id,
    //   answer: answer,
    //   sessionId: sessionId.value
    // })

    // 检查答案是否正确
    isCorrect.value = answer === currentQuestion.value.correctAnswer
    answerSubmitted.value = true

    // 更新题目状态
    questions.value[currentQuestionIndex.value].answered = true
    questions.value[currentQuestionIndex.value].isCorrect = isCorrect.value
  } catch (error) {
    ElMessage.error('提交答案失败')
  }
}

const nextQuestion = () => {
  if (currentQuestionIndex.value < questions.value.length - 1) {
    currentQuestionIndex.value++
    resetAnswerState()
  } else {
    completePractice()
  }
}

const previousQuestion = () => {
  if (currentQuestionIndex.value > 0) {
    currentQuestionIndex.value--
    resetAnswerState()
  }
}

const jumpToQuestion = (index) => {
  currentQuestionIndex.value = index
  resetAnswerState()
}

const resetAnswerState = () => {
  selectedAnswer.value = ''
  selectedAnswers.value = []
  answerSubmitted.value = false
  isCorrect.value = false
}

const hasAnswer = () => {
  if (currentQuestion.value.type === 'SINGLE_CHOICE') {
    return selectedAnswer.value !== ''
  } else {
    return selectedAnswers.value.length > 0
  }
}

const completePractice = async () => {
  try {
    stopTimer()

    const correctCount = questions.value.filter(q => q.isCorrect).length
    const accuracy = Math.round((correctCount / questions.value.length) * 100)

    // await completePracticeSession(sessionId.value, {
    //   correctCount,
    //   totalCount: questions.value.length,
    //   duration: elapsedTime.value
    // })

    ElMessage.success(`练习完成！正确率：${accuracy}%`)
    practiceStarted.value = false
  } catch (error) {
    ElMessage.error('完成练习失败')
  }
}

const favoriteQuestion = async () => {
  try {
    // await favoriteQuestionApi(currentQuestion.value.id)
    ElMessage.success('收藏成功')
  } catch (error) {
    ElMessage.error('收藏失败')
  }
}

const showHistory = async () => {
  try {
    // const response = await getPracticeHistory()
    // historyList.value = response.data

    // 模拟数据
    historyList.value = [
      {
        id: 1,
        date: new Date(Date.now() - 1 * 24 * 60 * 60 * 1000).toISOString(),
        questionCount: 10,
        correctCount: 8,
        duration: 600
      },
      {
        id: 2,
        date: new Date(Date.now() - 2 * 24 * 60 * 60 * 1000).toISOString(),
        questionCount: 15,
        correctCount: 12,
        duration: 900
      }
    ]

    historyDialogVisible.value = true
  } catch (error) {
    ElMessage.error('获取练习历史失败')
  }
}

const viewHistoryDetail = (history) => {
  ElMessage.info(`查看练习详情：${history.id}`)
}

const startTimer = () => {
  timer.value = setInterval(() => {
    elapsedTime.value++
  }, 1000)
}

const stopTimer = () => {
  if (timer.value) {
    clearInterval(timer.value)
    timer.value = null
  }
}

// 工具方法
const getProgress = () => {
  if (questions.value.length === 0) return 0
  return Math.round(((currentQuestionIndex.value + 1) / questions.value.length) * 100)
}

const formatTime = (seconds) => {
  const mins = Math.floor(seconds / 60)
  const secs = seconds % 60
  return `${mins.toString().padStart(2, '0')}:${secs.toString().padStart(2, '0')}`
}

const formatDate = (dateString) => {
  return new Date(dateString).toLocaleString()
}

const formatDuration = (seconds) => {
  const mins = Math.floor(seconds / 60)
  const secs = seconds % 60
  return `${mins}分${secs}秒`
}

const getAccuracy = (history) => {
  return Math.round((history.correctCount / history.questionCount) * 100)
}

const getDifficultyType = (difficulty) => {
  const types = {
    'EASY': 'success',
    'MEDIUM': 'warning',
    'HARD': 'danger'
  }
  return types[difficulty] || ''
}

const getDifficultyText = (difficulty) => {
  const texts = {
    'EASY': '简单',
    'MEDIUM': '中等',
    'HARD': '困难'
  }
  return texts[difficulty] || difficulty
}

// 生命周期
onUnmounted(() => {
  stopTimer()
})
</script>

<style scoped>
.practice-page {
  padding: 20px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.header-actions {
  display: flex;
  gap: 10px;
}

.practice-intro {
  padding: 60px 0;
}

.practice-content {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.practice-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 20px;
  background-color: #f5f7fa;
  border-radius: 4px;
}

.progress-info {
  flex: 1;
  margin-right: 40px;
}

.current-question {
  display: block;
  margin-bottom: 8px;
  font-size: 14px;
  color: #606266;
}

.timer {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 18px;
  font-weight: bold;
  color: #409EFF;
}

.question-container {
  padding: 20px;
  border: 1px solid #e4e7ed;
  border-radius: 4px;
}

.question-header {
  display: flex;
  gap: 10px;
  margin-bottom: 20px;
}

.question-content h3 {
  margin: 0 0 15px;
  font-size: 18px;
  color: #303133;
  line-height: 1.6;
}

.context {
  padding: 12px;
  background-color: #f5f7fa;
  border-left: 4px solid #409EFF;
  margin-bottom: 20px;
  color: #606266;
  line-height: 1.6;
}

.question-options {
  margin: 20px 0;
}

.option-item {
  display: flex;
  align-items: flex-start;
  padding: 15px;
  margin-bottom: 12px;
  border: 1px solid #e4e7ed;
  border-radius: 4px;
  cursor: pointer;
  transition: all 0.3s;
}

.option-item:hover {
  border-color: #409EFF;
  background-color: #f0f9ff;
}

.option-item.correct-answer {
  border-color: #67c23a;
  background-color: #f0f9ff;
}

.option-item.wrong-answer {
  border-color: #f56c6c;
  background-color: #fef0f0;
}

.option-label {
  font-weight: bold;
  margin-right: 10px;
  color: #409EFF;
}

.option-text {
  flex: 1;
  color: #303133;
}

.answer-analysis {
  margin: 20px 0;
}

.analysis-content p {
  margin: 8px 0;
  line-height: 1.6;
}

.question-actions {
  display: flex;
  justify-content: center;
  gap: 15px;
  margin-top: 20px;
  padding-top: 20px;
  border-top: 1px solid #e4e7ed;
}

.question-navigation {
  padding: 20px;
  background-color: #f5f7fa;
  border-radius: 4px;
}

.question-navigation h4 {
  margin: 0 0 15px;
  color: #303133;
}

.nav-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(50px, 1fr));
  gap: 10px;
}

.nav-item {
  width: 50px;
  height: 50px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: 2px solid #e4e7ed;
  border-radius: 4px;
  cursor: pointer;
  transition: all 0.3s;
  background-color: white;
  font-weight: bold;
  color: #909399;
}

.nav-item:hover {
  border-color: #409EFF;
  color: #409EFF;
}

.nav-item.active {
  border-color: #409EFF;
  background-color: #409EFF;
  color: white;
}

.nav-item.answered {
  border-color: #e6a23c;
  background-color: #fdf6ec;
  color: #e6a23c;
}

.nav-item.correct {
  border-color: #67c23a;
  background-color: #f0f9ff;
  color: #67c23a;
}

.nav-item.wrong {
  border-color: #f56c6c;
  background-color: #fef0f0;
  color: #f56c6c;
}
</style>
