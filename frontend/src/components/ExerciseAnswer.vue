<template>
  <div class="exercise-answer">
    <div class="exercise-header">
      <h4>{{ exercise.question }}</h4>
      <el-tag :type="getDifficultyType(exercise.exerciseType)" size="small">
        {{ getExerciseTypeText(exercise.exerciseType) }}
      </el-tag>
    </div>

    <!-- 选择题和判断题 -->
    <div
      v-if="exercise.exerciseType === 'MULTIPLE_CHOICE' || exercise.exerciseType === 'TRUE_FALSE'"
      class="options-container"
    >
      <el-radio-group
        v-model="userAnswer"
        :disabled="isAnswered"
        @change="handleAnswerChange"
      >
        <div
          v-for="option in (exercise.options || [])"
          :key="option.id || option.optionKey"
          class="option-item"
          :class="getOptionClass(option)"
        >
          <el-radio :label="option.optionKey">
            <div class="option-content">
              <span class="option-label">{{ option.optionKey }}.</span>
              <span class="option-text">{{ option.optionContent }}</span>
              <el-tag
                v-if="isAnswered && option.isCorrect"
                type="success"
                size="small"
                class="correct-tag"
              >
                正确答案
              </el-tag>
            </div>
          </el-radio>
        </div>
      </el-radio-group>
    </div>

    <!-- 填空题和简答题 -->
    <div
      v-else-if="exercise.exerciseType === 'FILL_BLANK' || exercise.exerciseType === 'SHORT_ANSWER'"
      class="answer-input-container"
    >
      <el-input
        v-model="userAnswer"
        type="textarea"
        :rows="3"
        placeholder="请输入你的答案"
        :disabled="isAnswered"
        @blur="handleAnswerChange"
      />
    </div>

    <!-- 答题状态和反馈 -->
    <div v-if="isAnswered" class="answer-feedback">
      <div class="feedback-header">
        <el-alert
          :title="isCorrect ? '回答正确！' : '回答错误'"
          :type="isCorrect ? 'success' : 'error'"
          :closable="false"
          show-icon
        />
        <div class="answer-stats">
          <span>答题用时：{{ formatTime(timeSpent) }}</span>
          <span>答题次数：{{ attemptCount }}</span>
        </div>
      </div>

      <!-- 答题解析 -->
      <div v-if="exercise.explanation" class="explanation">
        <h5>答案解析：</h5>
        <p>{{ exercise.explanation }}</p>
      </div>
    </div>

    <!-- 提交按钮 -->
    <div v-if="!isAnswered" class="answer-actions">
      <el-button
        type="primary"
        :loading="submitting"
        :disabled="!canSubmit"
        @click="submitAnswer"
      >
        提交答案
      </el-button>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch, onBeforeUnmount, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import request from '@/utils/request'

// Props
const props = defineProps({
  exercise: {
    type: Object,
    required: true,
    validator: (value) => value && typeof value === "object" && "id" in value && "question" in value
  },
  initialAnswer: {
    type: String,
    default: ''
  },
  answered: {
    type: Boolean,
    default: false
  },
  correct: {
    type: Boolean,
    default: false
  },
  assignmentId: {
    type: [Number, null],
    default: null
  }
})

// Emits
const emit = defineEmits(['answer-submit', 'answer-change'])

// Reactive data
const userAnswer = ref(props.initialAnswer || '')
const isCorrect = ref(props.correct || false)
const isAnswered = ref(props.answered || false)
const submitting = ref(false)
const timeSpent = ref(0)
const attemptCount = ref(0)
const startTime = ref(Date.now())

// Timer
let timer = null

// Helper to ensure timer is properly stopped
const ensureTimerCleared = () => {
  if (timer) {
    clearInterval(timer)
    timer = null
  }
}

// Computed properties
const canSubmit = computed(() => {
  if (props.exercise?.exerciseType === 'FILL_BLANK' || props.exercise?.exerciseType === 'SHORT_ANSWER') {
    return userAnswer.value != null && userAnswer.value.trim().length > 0
  } else {
    // 选择题：必须选了一个非空选项
    return userAnswer.value !== null && userAnswer.value !== undefined && userAnswer.value !== ''
  }
})

// Watch for prop changes
watch(() => props.initialAnswer, (newValue) => {
  userAnswer.value = newValue || ''
}, { immediate: true })

watch(() => props.answered, (newValue) => {
  isAnswered.value = newValue
  if (newValue) {
    stopTimer()
  }
}, { immediate: true })

watch(() => props.correct, (newValue) => {
  isCorrect.value = newValue || false
}, { immediate: true })

// Methods
const getExerciseTypeText = (type) => {
  const typeMap = {
    'MULTIPLE_CHOICE': '选择题',
    'TRUE_FALSE': '判断题',
    'FILL_BLANK': '填空题',
    'SHORT_ANSWER': '简答题'
  }
  return typeMap[type] || type
}

const getDifficultyType = (type) => {
  const typeMap = {
    'MULTIPLE_CHOICE': 'primary',
    'TRUE_FALSE': 'warning',
    'FILL_BLANK': 'info',
    'SHORT_ANSWER': 'success'
  }
  return typeMap[type] || 'info'
}

const getOptionClass = (option) => {
  if (!isAnswered.value) return ''

  const classes = ['option-item']

  if (option.isCorrect) {
    classes.push('correct')
  } else if (userAnswer.value === option.optionKey) {
    classes.push('incorrect')
  }

  return classes.join(' ')
}

const handleAnswerChange = () => {
  emit('answer-change', {
    exerciseId: props.exercise?.id,
    answer: userAnswer.value
  })
}

const submitAnswer = async () => {
  if (!canSubmit.value) {
    ElMessage.warning('请先完成答题')
    return
  }

  if (!props.exercise?.id) {
    ElMessage.error('题目信息不完整')
    return
  }

  submitting.value = true
  stopTimer()
  attemptCount.value++

  const answerData = {
    exerciseId: props.exercise.id,
    answerContent: userAnswer.value,
    timeSpentSeconds: timeSpent.value
  }

  if (props.assignmentId !== null && props.assignmentId !== undefined) {
    answerData.assignmentId = props.assignmentId
  }

  try {
    const result = await request({
      url: '/api/student/exercises/submit',
      method: 'post',
      data: answerData
    })

    if (result?.code === 200) {
      const responseData = result.data || {}
      isCorrect.value = Boolean(responseData.isCorrect)
      isAnswered.value = true
      ElMessage.success(responseData.message || '提交成功')

      emit('answer-submit', {
        exerciseId: props.exercise.id,
        answer: userAnswer.value,
        isCorrect: isCorrect.value,
        timeSpent: timeSpent.value,
        attemptCount: attemptCount.value
      })
    } else {
      ElMessage.error(result?.message || '提交失败')
    }
  } catch (error) {
    const errorMsg = error?.response?.data?.message || error?.message || '提交失败'
    console.error('提交答案失败:', errorMsg)
    ElMessage.error('提交失败，请重试')
  } finally {
    submitting.value = false
  }
}

const formatTime = (seconds) => {
  if (seconds < 60) {
    return `${seconds}秒`
  } else {
    const minutes = Math.floor(seconds / 60)
    const remainingSeconds = seconds % 60
    return `${minutes}分${remainingSeconds}秒`
  }
}

const startTimer = () => {
  ensureTimerCleared()
  timer = setInterval(() => {
    timeSpent.value = Math.floor((Date.now() - startTime.value) / 1000)
  }, 1000)
}

const stopTimer = () => {
  ensureTimerCleared()
}

// Start timer when component mounts (moved from module level)
onMounted(() => {
  if (!isAnswered.value) {
    startTimer()
  }
})

// Cleanup
onBeforeUnmount(() => {
  ensureTimerCleared()
})
</script>

<style scoped>
.exercise-answer {
  border: 1px solid #e0e0e0;
  border-radius: 8px;
  padding: 20px;
  margin-bottom: 20px;
  background: white;
}

.exercise-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 15px;
}

.exercise-header h4 {
  margin: 0;
  color: #333;
  line-height: 1.5;
  flex: 1;
}

.options-container {
  margin: 15px 0;
}

.option-item {
  margin-bottom: 10px;
  padding: 12px;
  min-height: 44px; /* 移动端触摸目标最小高度 */
  border: 1px solid #e0e0e0;
  border-radius: 6px;
  transition: all 0.3s;
  box-sizing: border-box;
}

.option-item:hover {
  border-color: #409EFF;
}

.option-item.correct {
  border-color: #67C23A;
  background-color: #f0f9ff;
}

.option-item.incorrect {
  border-color: #F56C6C;
  background-color: #fef0f0;
}

.option-content {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
}

.option-label {
  font-weight: bold;
  color: #409EFF;
  min-width: 30px;
}

.option-text {
  flex: 1;
  line-height: 1.5;
}

.correct-tag {
  margin-left: auto;
}

.answer-input-container {
  margin: 15px 0;
}

.answer-feedback {
  margin-top: 15px;
  padding: 15px;
  background-color: #f8f9fa;
  border-radius: 6px;
}

.feedback-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap; /* 窄屏时答题统计另起一行 */
  gap: 8px;
  margin-bottom: 10px;
}

.answer-stats {
  display: flex;
  gap: 20px;
  font-size: 14px;
  color: #666;
}

.explanation {
  margin-top: 15px;
  padding: 15px;
  background: #fff;
  border-left: 4px solid #409EFF;
  border-radius: 4px;
}

.explanation h5 {
  margin: 0 0 10px 0;
  color: #409EFF;
}

.answer-actions {
  margin-top: 15px;
  text-align: center;
}

/* 移动端适配 */
@media (max-width: 768px) {
  .exercise-answer {
    padding: 14px;
    margin-bottom: 14px;
  }

  .exercise-header {
    flex-direction: column;
    align-items: flex-start;
    gap: 8px;
  }

  .answer-stats {
    flex-direction: column;
    gap: 4px;
    font-size: 12px;
  }

  .option-item {
    padding: 10px;
  }

  /* 提交按钮在移动端撑满宽度，便于点击 */
  .answer-actions .el-button {
    width: 100%;
    min-height: 44px;
  }
}
</style>