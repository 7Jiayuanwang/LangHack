<template>
  <div class="exercise-builder">
    <div class="exercise-header">
      <h3>课文习题设置</h3>
      <el-switch
        v-model="hasExercises"
        active-text="添加习题"
        inactive-text="无需习题"
        @change="handleExerciseToggle"
      />
    </div>

    <div v-if="hasExercises" class="exercise-content">
      <div class="exercise-toolbar">
        <el-button type="primary" @click="addExercise" :icon="Plus">
          添加习题
        </el-button>
        <el-button @click="clearAllExercises" :icon="Delete">
          清空所有习题
        </el-button>
      </div>

      <div class="exercise-list">
        <div
          v-for="(exercise, index) in exercises"
          :key="index"
          class="exercise-item"
        >
          <div class="exercise-header-bar">
            <span class="exercise-number">习题 {{ index + 1 }}</span>
            <div class="exercise-controls">
              <el-select
                v-model="exercise.exerciseType"
                placeholder="题型"
                @change="handleTypeChange(index)"
                class="exercise-type-select"
              >
                <el-option label="选择题" value="MULTIPLE_CHOICE" />
                <el-option label="判断题" value="TRUE_FALSE" />
                <el-option label="填空题" value="FILL_BLANK" />
                <el-option label="简答题" value="SHORT_ANSWER" />
              </el-select>
              <el-button
                type="danger"
                size="small"
                :icon="Delete"
                @click="removeExercise(index)"
                circle
              />
            </div>
          </div>

          <div class="exercise-form">
            <el-form-item label="题目内容" required>
              <el-input
                v-model="exercise.question"
                type="textarea"
                :rows="2"
                placeholder="请输入题目内容"
                maxlength="1000"
                show-word-limit
              />
            </el-form-item>

            <!-- 选择题和判断题的选项 -->
            <div
              v-if="exercise.exerciseType === 'MULTIPLE_CHOICE' || exercise.exerciseType === 'TRUE_FALSE'"
              class="options-section"
            >
              <div class="options-header">
                <span>选项设置</span>
                <el-button
                  v-if="exercise.exerciseType === 'MULTIPLE_CHOICE'"
                  type="primary"
                  size="small"
                  @click="addOption(index)"
                  :icon="Plus"
                >
                  添加选项
                </el-button>
              </div>

              <div class="options-list">
                <div
                  v-for="(option, optionIndex) in exercise.options"
                  :key="optionIndex"
                  class="option-item"
                >
                  <div class="option-content">
                    <el-input
                      v-model="option.optionContent"
                      :placeholder="`选项 ${String.fromCharCode(65 + optionIndex)}`"
                    />
                    <el-radio
                      v-model="exercise.correctAnswer"
                      :label="option.optionKey"
                      @change="setCorrectAnswer(index, option.optionKey)"
                    >
                      正确答案
                    </el-radio>
                  </div>
                  <el-button
                    v-if="exercise.exerciseType === 'MULTIPLE_CHOICE' && exercise.options.length > 2"
                    type="danger"
                    size="small"
                    :icon="Delete"
                    @click="removeOption(index, optionIndex)"
                    circle
                  />
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>

      <div v-if="exercises.length === 0" class="empty-exercises">
        <el-empty description="暂无习题，请点击添加习题按钮创建">
          <el-button type="primary" @click="addExercise">立即添加</el-button>
        </el-empty>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, watch } from 'vue'
import { Plus, Delete } from '@element-plus/icons-vue'

// Props
const props = defineProps({
  modelValue: {
    type: Array,
    default: () => []
  }
})

// Emits
const emit = defineEmits(['update:modelValue'])

// Reactive data
const hasExercises = ref(false)
const exercises = ref([])

// Initialize exercises from props
const initializeExercises = () => {
  if (Array.isArray(props.modelValue) && props.modelValue.length > 0) {
    hasExercises.value = true
    exercises.value = JSON.parse(JSON.stringify(props.modelValue))
  } else {
    hasExercises.value = false
    exercises.value = []
  }
}

// Watch for external changes
watch(() => props.modelValue, initializeExercises, { immediate: true })

// Watch for internal changes and emit
watch(exercises, (newVal) => {
  emit('update:modelValue', newVal)
}, { deep: true })

// Methods
const handleExerciseToggle = (value) => {
  if (!value) {
    exercises.value = []
  } else {
    addExercise()
  }
}

const addExercise = () => {
  const newExercise = {
    question: '',
    exerciseType: 'MULTIPLE_CHOICE',
    orderNum: exercises.value.length + 1,
    options: [],
    correctAnswer: null
  }

  if (newExercise.exerciseType === 'MULTIPLE_CHOICE') {
    // 默认添加4个选择题选项
    newExercise.options = [
      { optionContent: '', optionKey: 'A', isCorrect: false, orderNum: 1 },
      { optionContent: '', optionKey: 'B', isCorrect: false, orderNum: 2 },
      { optionContent: '', optionKey: 'C', isCorrect: false, orderNum: 3 },
      { optionContent: '', optionKey: 'D', isCorrect: false, orderNum: 4 }
    ]
  } else if (newExercise.exerciseType === 'TRUE_FALSE') {
    // 判断题只有两个选项
    newExercise.options = [
      { optionContent: '正确', optionKey: 'A', isCorrect: false, orderNum: 1 },
      { optionContent: '错误', optionKey: 'B', isCorrect: false, orderNum: 2 }
    ]
  }

  exercises.value.push(newExercise)
}

const removeExercise = (index) => {
  exercises.value.splice(index, 1)
  // 更新序号
  exercises.value.forEach((exercise, i) => {
    exercise.orderNum = i + 1
  })
}

const handleTypeChange = (index) => {
  const exercise = exercises.value[index]
  exercise.options = []
  exercise.correctAnswer = null

  if (exercise.exerciseType === 'MULTIPLE_CHOICE') {
    exercise.options = [
      { optionContent: '', optionKey: 'A', isCorrect: false, orderNum: 1 },
      { optionContent: '', optionKey: 'B', isCorrect: false, orderNum: 2 },
      { optionContent: '', optionKey: 'C', isCorrect: false, orderNum: 3 },
      { optionContent: '', optionKey: 'D', isCorrect: false, orderNum: 4 }
    ]
  } else if (exercise.exerciseType === 'TRUE_FALSE') {
    exercise.options = [
      { optionContent: '正确', optionKey: 'A', isCorrect: false, orderNum: 1 },
      { optionContent: '错误', optionKey: 'B', isCorrect: false, orderNum: 2 }
    ]
  }
}

const addOption = (exerciseIndex) => {
  const exercise = exercises.value[exerciseIndex]
  const nextKey = String.fromCharCode(65 + exercise.options.length)
  exercise.options.push({
    optionContent: '',
    optionKey: nextKey,
    isCorrect: false,
    orderNum: exercise.options.length + 1
  })
}

const removeOption = (exerciseIndex, optionIndex) => {
  const exercise = exercises.value[exerciseIndex]
  exercise.options.splice(optionIndex, 1)

  // 重新设置选项标识
  exercise.options.forEach((option, i) => {
    option.optionKey = String.fromCharCode(65 + i)
    option.orderNum = i + 1
  })

  // 如果删除的是正确答案，清除正确答案
  // 使用可选链和空值合并，防止 correctAnswer 为 undefined 时 .find 报错
  if (exercise.correctAnswer && !exercise.options.find(opt => opt.optionKey === exercise.correctAnswer)) {
    exercise.correctAnswer = null
  }
}

const setCorrectAnswer = (exerciseIndex, optionKey) => {
  const exercise = exercises.value[exerciseIndex]
  exercise.options.forEach(option => {
    option.isCorrect = option.optionKey === optionKey
  })
}

const clearAllExercises = () => {
  exercises.value = []
}

// Validation method
const validateExercises = () => {
  const errors = []

  if (hasExercises.value) {
    exercises.value.forEach((exercise, index) => {
      if (!exercise.question || !exercise.question.trim()) {
        errors.push(`习题${index + 1}：题目内容不能为空`)
      }

      if ((exercise.exerciseType === 'FILL_BLANK' || exercise.exerciseType === 'SHORT_ANSWER') &&
          !exercise.correctAnswer?.trim()) {
        errors.push(`习题${index + 1}：参考答案不能为空`)
      }

      if ((exercise.exerciseType === 'MULTIPLE_CHOICE' || exercise.exerciseType === 'TRUE_FALSE') &&
          (exercise.options || []).some(opt => !opt.optionContent?.trim())) {
        errors.push(`习题${index + 1}：选项内容不能为空`)
      }

      if ((exercise.exerciseType === 'MULTIPLE_CHOICE' || exercise.exerciseType === 'TRUE_FALSE') &&
          !(exercise.options || []).some(opt => opt.isCorrect)) {
        errors.push(`习题${index + 1}：必须设置一个正确答案`)
      }
    })
  }

  return errors
}

// Expose validation method
defineExpose({
  validateExercises
})
</script>

<style scoped>
.exercise-builder {
  border: 1px solid #e0e0e0;
  border-radius: 8px;
  padding: 20px;
  background: #fafafa;
}

.exercise-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
  padding-bottom: 15px;
  border-bottom: 1px solid #e0e0e0;
}

.exercise-header h3 {
  margin: 0;
  color: #333;
}

.exercise-content {
  margin-top: 20px;
}

.exercise-toolbar {
  display: flex;
  flex-wrap: wrap; /* 窄屏时自动换行 */
  gap: 10px;
  margin-bottom: 20px;
}

.exercise-list {
  space-y: 15px;
}

.exercise-item {
  background: white;
  border: 1px solid #e0e0e0;
  border-radius: 8px;
  padding: 20px;
  margin-bottom: 15px;
}

.exercise-header-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 15px;
}

.exercise-number {
  font-weight: bold;
  color: #409EFF;
  font-size: 16px;
}

.exercise-controls {
  display: flex;
  align-items: center;
  gap: 10px;
}

.exercise-form {
  space-y: 15px;
}

.options-section {
  margin-top: 15px;
  padding: 15px;
  background: #f8f9fa;
  border-radius: 6px;
}

.options-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 15px;
  font-weight: bold;
  color: #333;
}

.options-list {
  space-y: 10px;
}

.option-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px;
  min-height: 44px; /* 移动端触摸目标最小高度 */
  background: white;
  border-radius: 4px;
  border: 1px solid #e0e0e0;
  box-sizing: border-box;
}

.option-content {
  display: flex;
  align-items: center;
  gap: 15px;
  flex: 1;
}

.option-content .el-input {
  flex: 1;
}

.empty-exercises {
  text-align: center;
  padding: 40px 20px;
}

/* 题型下拉框：桌面端固定宽度，移动端自适应 */
.exercise-type-select {
  width: 120px;
}

/* 移动端适配 */
@media (max-width: 768px) {
  .exercise-builder {
    padding: 14px;
  }

  .exercise-header {
    flex-direction: column;
    align-items: flex-start;
    gap: 10px;
  }

  .exercise-item {
    padding: 14px;
  }

  .exercise-header-bar {
    flex-wrap: wrap;
    gap: 8px;
  }

  .exercise-controls {
    flex-wrap: wrap;
    gap: 8px;
  }

  /* 移动端题型选择器撑满可用宽度 */
  .exercise-type-select {
    width: 100%;
  }

  .option-content {
    flex-direction: column;
    align-items: flex-start;
    gap: 8px;
  }
}
</style>