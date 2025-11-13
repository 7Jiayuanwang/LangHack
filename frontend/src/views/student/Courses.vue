<template>
  <div class="courses-learning">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>课程学习</span>
          <div class="header-actions">
            <el-input
              v-model="searchKeyword"
              placeholder="搜索课程"
              style="width: 200px; margin-right: 10px"
              clearable
              @input="loadCourses"
            />
          </div>
        </div>
      </template>

      <!-- 课程列表 -->
      <el-row :gutter="20">
        <el-col
          v-for="course in courses"
          :key="course.id"
          :xs="24"
          :sm="12"
          :md="8"
          :lg="6"
        >
          <el-card class="course-card" @click="enterCourse(course)">
            <div class="course-cover">
              <img
                v-if="course.coverImage"
                :src="course.coverImage"
                :alt="course.name"
                class="cover-image"
              />
              <div v-else class="cover-placeholder">
                <el-icon size="48"><Reading /></el-icon>
              </div>
            </div>
            <div class="course-info">
              <h3>{{ course.name }}</h3>
              <p class="description">{{ course.description }}</p>
              <div class="course-stats">
                <div class="stat-item">
                  <span class="label">单元数：</span>
                  <span class="value">{{ course.unitCount }}</span>
                </div>
                <div class="stat-item">
                  <span class="label">学习进度：</span>
                  <span class="value">{{ course.completedUnits }}/{{ course.unitCount }}</span>
                </div>
              </div>
              <div class="progress-bar">
                <el-progress
                  :percentage="getCourseProgress(course)"
                  :color="getProgressColor(getCourseProgress(course))"
                  :stroke-width="6"
                />
              </div>
              <div class="course-meta">
                <span class="enroll-date">加入时间：{{ formatDate(course.enrollDate) }}</span>
              </div>
            </div>
          </el-card>
        </el-col>
      </el-row>

      <!-- 空状态 -->
      <el-empty v-if="courses.length === 0" description="暂无课程">
        <el-button type="primary" @click="loadCourses">刷新课程</el-button>
      </el-empty>
    </el-card>

    <!-- 课程单元对话框 -->
    <el-dialog
      v-model="unitsDialogVisible"
      :title="currentCourse.name + ' - 单元列表'"
      width="800px"
      top="5vh"
    >
      <div class="units-container">
        <el-row :gutter="20">
          <el-col
            v-for="unit in currentCourseUnits"
            :key="unit.id"
            :xs="24"
            :sm="12"
            :md="8"
          >
            <el-card class="unit-card" :class="{ completed: unit.completed }" @click="enterUnit(unit)">
              <div class="unit-header">
                <h4>{{ unit.name }}</h4>
                <el-tag v-if="unit.completed" type="success" size="small">已完成</el-tag>
              </div>
              <p class="unit-description">{{ unit.description }}</p>
              <div class="unit-content">
                <div class="content-item">
                  <el-icon><Document /></el-icon>
                  <span>课文：{{ unit.textCount }}篇</span>
                </div>
                <div class="content-item">
                  <el-icon><Reading /></el-icon>
                  <span>单词：{{ unit.wordCount }}个</span>
                </div>
              </div>
              <div class="unit-progress">
                <span>学习进度：</span>
                <el-progress
                  :percentage="unit.progress"
                  :color="getProgressColor(unit.progress)"
                  :stroke-width="4"
                  :show-text="false"
                  style="flex: 1; margin-left: 10px;"
                />
                <span class="progress-text">{{ unit.progress }}%</span>
              </div>
            </el-card>
          </el-col>
        </el-row>
      </div>
    </el-dialog>

    <!-- 单元学习对话框 -->
    <el-dialog
      v-model="studyDialogVisible"
      :title="currentUnit.name"
      width="90%"
      top="2vh"
      fullscreen
    >
      <div class="study-container">
        <el-tabs v-model="activeTab" type="border-card">
          <el-tab-pane label="课文学习" name="texts">
            <div class="texts-section">
              <el-table :data="currentUnitTexts" stripe>
                <el-table-column prop="title" label="课文标题" />
                <el-table-column prop="difficulty" label="难度" width="100">
                  <template #default="scope">
                    <el-tag :type="getDifficultyType(scope.row.difficulty)">
                      {{ getDifficultyText(scope.row.difficulty) }}
                    </el-tag>
                  </template>
                </el-table-column>
                <el-table-column prop="completed" label="状态" width="100">
                  <template #default="scope">
                    <el-tag :type="scope.row.completed ? 'success' : 'info'">
                      {{ scope.row.completed ? '已完成' : '未学习' }}
                    </el-tag>
                  </template>
                </el-table-column>
                <el-table-column label="操作" width="150">
                  <template #default="scope">
                    <el-button size="small" @click="studyText(scope.row)">
                      {{ scope.row.completed ? '复习' : '学习' }}
                    </el-button>
                    <el-button size="small" type="success" @click="addWordsToVocabulary(scope.row)">
                      收藏单词
                    </el-button>
                  </template>
                </el-table-column>
              </el-table>
            </div>
          </el-tab-pane>

          <el-tab-pane label="单词学习" name="words">
            <div class="words-section">
              <div class="words-grid">
                <div
                  v-for="word in currentUnitWords"
                  :key="word.id"
                  class="word-card"
                  @click="viewWordDetail(word)"
                >
                  <div class="word-header">
                    <h3>{{ word.word }}</h3>
                    <el-tag :type="getDifficultyType(word.difficulty)" size="small">
                      {{ getDifficultyText(word.difficulty) }}
                    </el-tag>
                  </div>
                  <div class="pronunciation" v-if="word.pronunciation">
                    {{ word.pronunciation }}
                  </div>
                  <div class="definition">{{ word.definition }}</div>
                  <div class="word-actions">
                    <el-button size="small" circle @click.stop="playAudio(word)">
                      <el-icon><VideoPlay /></el-icon>
                    </el-button>
                    <el-button size="small" circle type="success" @click.stop="addToVocabulary(word)">
                      <el-icon><Star /></el-icon>
                    </el-button>
                  </div>
                </div>
              </div>
            </div>
          </el-tab-pane>

          <el-tab-pane label="单词测试" name="test">
            <div class="test-section">
              <div class="test-header">
                <h3>单词测试</h3>
                <div class="test-info">
                  <span>总题数：{{ testWords.length }}</span>
                  <span>当前进度：{{ currentTestIndex + 1 }}/{{ testWords.length }}</span>
                  <span>正确率：{{ getTestAccuracy() }}%</span>
                </div>
              </div>

              <div v-if="testWords.length > 0" class="test-content">
                <div class="test-question">
                  <h4>{{ currentTestWord.definition }}</h4>
                  <p class="example" v-if="currentTestWord.exampleSentence">
                    例句：{{ currentTestWord.exampleSentence }}
                  </p>
                </div>

                <div class="test-options">
                  <el-radio-group v-model="selectedAnswer">
                    <el-radio
                      v-for="option in testOptions"
                      :key="option.id"
                      :label="option.word"
                      class="test-option"
                    >
                      {{ option.word }}
                    </el-radio>
                  </el-radio-group>
                </div>

                <div class="test-actions">
                  <el-button @click="skipQuestion">跳过</el-button>
                  <el-button type="primary" @click="submitAnswer" :disabled="!selectedAnswer">
                    {{ currentTestIndex < testWords.length - 1 ? '下一题' : '完成测试' }}
                  </el-button>
                </div>

                <div v-if="showResult" class="test-result">
                  <el-alert
                    :title="isCorrect ? '回答正确！' : '回答错误'"
                    :type="isCorrect ? 'success' : 'error'"
                    :closable="false"
                    show-icon
                  >
                    <template v-if="!isCorrect">
                      <p>正确答案：<strong>{{ currentTestWord.word }}</strong></p>
                    </template>
                  </el-alert>
                </div>
              </div>

              <div v-else class="no-test-data">
                <el-empty description="暂无测试题目">
                  <el-button type="primary" @click="startTest">开始测试</el-button>
                </el-empty>
              </div>
            </div>
          </el-tab-pane>
        </el-tabs>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'

// 响应式数据
const courses = ref([])
const searchKeyword = ref('')
const unitsDialogVisible = ref(false)
const studyDialogVisible = ref(false)
const activeTab = ref('texts')

// 当前选中的课程和单元
const currentCourse = ref({})
const currentCourseUnits = ref([])
const currentUnit = ref({})
const currentUnitTexts = ref([])
const currentUnitWords = ref([])

// 测试相关
const testWords = ref([])
const currentTestIndex = ref(0)
const currentTestWord = ref({})
const testOptions = ref([])
const selectedAnswer = ref('')
const showResult = ref(false)
const isCorrect = ref(false)
const testResults = ref([])

// 方法
const loadCourses = async () => {
  try {
    // 模拟API调用
    await new Promise(resolve => setTimeout(resolve, 500))

    const mockCourses = [
      {
        id: 1,
        name: '新概念英语第一册',
        description: '适合英语初学者的经典教材，包含基础语法和常用词汇',
        coverImage: null,
        unitCount: 12,
        completedUnits: 8,
        enrollDate: '2024-01-10T00:00:00'
      },
      {
        id: 2,
        name: '大学英语四级',
        description: '大学英语四级考试备考课程，包含听力、阅读、写作和翻译',
        coverImage: null,
        unitCount: 10,
        completedUnits: 3,
        enrollDate: '2024-01-12T00:00:00'
      },
      {
        id: 3,
        name: '商务英语',
        description: '商务环境下的英语沟通技能训练',
        coverImage: null,
        unitCount: 8,
        completedUnits: 2,
        enrollDate: '2024-01-15T00:00:00'
      }
    ]

    // 应用搜索过滤
    if (searchKeyword.value) {
      courses.value = mockCourses.filter(course =>
        course.name.toLowerCase().includes(searchKeyword.value.toLowerCase()) ||
        course.description.toLowerCase().includes(searchKeyword.value.toLowerCase())
      )
    } else {
      courses.value = mockCourses
    }
  } catch (error) {
    ElMessage.error('加载课程列表失败')
  }
}

const enterCourse = async (course) => {
  try {
    currentCourse.value = course

    // 模拟加载单元列表
    await new Promise(resolve => setTimeout(resolve, 300))

    currentCourseUnits.value = [
      {
        id: 1,
        name: '第一单元',
        description: '基础问候语和自我介绍',
        textCount: 3,
        wordCount: 25,
        progress: 100,
        completed: true
      },
      {
        id: 2,
        name: '第二单元',
        description: '日常对话和时间表达',
        textCount: 4,
        wordCount: 30,
        progress: 75,
        completed: false
      },
      {
        id: 3,
        name: '第三单元',
        description: '方位和地点描述',
        textCount: 3,
        wordCount: 28,
        progress: 50,
        completed: false
      }
    ]

    unitsDialogVisible.value = true
  } catch (error) {
    ElMessage.error('加载单元列表失败')
  }
}

const enterUnit = async (unit) => {
  try {
    currentUnit.value = unit

    // 模拟加载课文列表
    await new Promise(resolve => setTimeout(resolve, 300))

    currentUnitTexts.value = [
      {
        id: 1,
        title: 'Greetings and Introductions',
        difficulty: 'EASY',
        completed: true,
        content: 'Hello! My name is Tom. What is your name? Nice to meet you!'
      },
      {
        id: 2,
        title: 'Daily Conversations',
        difficulty: 'MEDIUM',
        completed: false,
        content: 'How are you today? I\'m fine, thank you. And you?'
      },
      {
        id: 3,
        title: 'Time and Dates',
        difficulty: 'MEDIUM',
        completed: false,
        content: 'What time is it? It\'s 3 o\'clock in the afternoon.'
      }
    ]

    // 模拟加载单词列表
    currentUnitWords.value = [
      {
        id: 1,
        word: 'hello',
        pronunciation: '/həˈləʊ/',
        definition: '你好；喂',
        exampleSentence: 'Hello, how are you?',
        difficulty: 'EASY'
      },
      {
        id: 2,
        word: 'name',
        pronunciation: '/neɪm/',
        definition: '名字；名称',
        exampleSentence: 'What is your name?',
        difficulty: 'EASY'
      },
      {
        id: 3,
        word: 'conversation',
        pronunciation: '/ˌkɒnvəˈseɪʃn/',
        definition: '对话；会话',
        exampleSentence: 'We had a long conversation.',
        difficulty: 'MEDIUM'
      }
    ]

    activeTab.value = 'texts'
    studyDialogVisible.value = true
  } catch (error) {
    ElMessage.error('加载单元内容失败')
  }
}

const studyText = (text) => {
  ElMessage.info(`学习课文：${text.title}`)
}

const addWordsToVocabulary = (text) => {
  ElMessage.success(`已将 ${text.title} 中的单词添加到生词本`)
}

const viewWordDetail = (word) => {
  ElMessage.info(`查看单词详情：${word.word}`)
}

const playAudio = (word) => {
  ElMessage.info(`播放发音：${word.word}`)
}

const addToVocabulary = (word) => {
  ElMessage.success(`已将单词 "${word.word}" 添加到生词本`)
}

const startTest = () => {
  if (currentUnitWords.value.length === 0) {
    ElMessage.warning('该单元暂无单词，无法进行测试')
    return
  }

  testWords.value = [...currentUnitWords.value]
  currentTestIndex.value = 0
  testResults.value = []
  loadTestQuestion()
}

const loadTestQuestion = () => {
  currentTestWord.value = testWords.value[currentTestIndex.value]

  // 生成选项（正确答案 + 3个错误答案）
  const allWords = [...currentUnitWords.value].filter(w => w.id !== currentTestWord.value.id)
  const wrongAnswers = []
  for (let i = 0; i < 3 && i < allWords.length; i++) {
    const randomIndex = Math.floor(Math.random() * allWords.length)
    wrongAnswers.push(allWords.splice(randomIndex, 1)[0])
  }

  testOptions.value = [currentTestWord.value, ...wrongAnswers].sort(() => Math.random() - 0.5)
  selectedAnswer.value = ''
  showResult.value = false
}

const submitAnswer = () => {
  isCorrect.value = selectedAnswer.value === currentTestWord.value.word
  testResults.value.push({
    word: currentTestWord.value.word,
    correct: isCorrect.value,
    userAnswer: selectedAnswer.value
  })

  showResult.value = true

  setTimeout(() => {
    if (currentTestIndex.value < testWords.value.length - 1) {
      currentTestIndex.value++
      loadTestQuestion()
    } else {
      // 测试完成
      const accuracy = getTestAccuracy()
      ElMessage.success(`测试完成！正确率：${accuracy}%`)
      activeTab.value = 'words'
    }
  }, 2000)
}

const skipQuestion = () => {
  testResults.value.push({
    word: currentTestWord.value.word,
    correct: false,
    userAnswer: '跳过'
  })

  if (currentTestIndex.value < testWords.value.length - 1) {
    currentTestIndex.value++
    loadTestQuestion()
  } else {
    const accuracy = getTestAccuracy()
    ElMessage.success(`测试完成！正确率：${accuracy}%`)
    activeTab.value = 'words'
  }
}

const getTestAccuracy = () => {
  if (testResults.value.length === 0) return 0
  const correctCount = testResults.value.filter(r => r.correct).length
  return Math.round((correctCount / testResults.value.length) * 100)
}

// 工具方法
const getCourseProgress = (course) => {
  return Math.round((course.completedUnits / course.unitCount) * 100)
}

const getProgressColor = (percentage) => {
  if (percentage >= 80) return '#67c23a'
  if (percentage >= 50) return '#e6a23c'
  return '#f56c6c'
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

const formatDate = (dateString) => {
  return new Date(dateString).toLocaleDateString()
}

// 生命周期
onMounted(() => {
  loadCourses()
})
</script>

<style scoped>
.courses-learning {
  padding: 20px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.course-card {
  margin-bottom: 20px;
  cursor: pointer;
  transition: all 0.3s;
}

.course-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
}

.course-cover {
  height: 120px;
  display: flex;
  align-items: center;
  justify-content: center;
  background-color: #f5f7fa;
  border-radius: 4px;
  margin-bottom: 15px;
}

.cover-image {
  width: 100%;
  height: 100%;
  object-fit: cover;
  border-radius: 4px;
}

.cover-placeholder {
  color: #c0c4cc;
}

.course-info h3 {
  margin: 0 0 8px;
  font-size: 16px;
  color: #303133;
}

.description {
  margin: 0 0 12px;
  color: #606266;
  font-size: 14px;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.course-stats {
  margin-bottom: 12px;
}

.stat-item {
  display: flex;
  justify-content: space-between;
  margin-bottom: 4px;
  font-size: 13px;
}

.stat-item .label {
  color: #909399;
}

.stat-item .value {
  color: #409EFF;
  font-weight: bold;
}

.progress-bar {
  margin-bottom: 12px;
}

.course-meta {
  font-size: 12px;
  color: #909399;
}

.units-container {
  max-height: 60vh;
  overflow-y: auto;
}

.unit-card {
  margin-bottom: 15px;
  cursor: pointer;
  transition: all 0.3s;
}

.unit-card:hover {
  transform: translateY(-1px);
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
}

.unit-card.completed {
  border-left: 4px solid #67c23a;
}

.unit-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

.unit-header h4 {
  margin: 0;
  color: #303133;
}

.unit-description {
  margin: 0 0 12px;
  color: #606266;
  font-size: 14px;
}

.unit-content {
  margin-bottom: 12px;
}

.content-item {
  display: flex;
  align-items: center;
  margin-bottom: 4px;
  font-size: 13px;
  color: #606266;
}

.content-item .el-icon {
  margin-right: 5px;
  color: #409EFF;
}

.unit-progress {
  display: flex;
  align-items: center;
  font-size: 13px;
  color: #606266;
}

.progress-text {
  margin-left: 10px;
  min-width: 35px;
}

.study-container {
  max-height: 80vh;
  overflow-y: auto;
}

.words-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(250px, 1fr));
  gap: 15px;
}

.word-card {
  padding: 15px;
  border: 1px solid #e4e7ed;
  border-radius: 4px;
  cursor: pointer;
  transition: all 0.3s;
  position: relative;
}

.word-card:hover {
  border-color: #409EFF;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
}

.word-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

.word-header h3 {
  margin: 0;
  color: #303133;
  font-size: 18px;
}

.pronunciation {
  color: #606266;
  font-style: italic;
  margin-bottom: 8px;
  font-size: 14px;
}

.definition {
  color: #303133;
  margin-bottom: 12px;
  line-height: 1.5;
}

.word-actions {
  position: absolute;
  top: 10px;
  right: 10px;
  display: flex;
  gap: 5px;
}

.test-section {
  padding: 20px;
}

.test-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.test-info {
  display: flex;
  gap: 20px;
  font-size: 14px;
  color: #606266;
}

.test-question {
  text-align: center;
  margin-bottom: 30px;
}

.test-question h4 {
  font-size: 18px;
  color: #303133;
  margin-bottom: 10px;
}

.test-question .example {
  color: #606266;
  font-style: italic;
}

.test-options {
  margin-bottom: 30px;
}

.test-option {
  display: block;
  margin-bottom: 10px;
  padding: 10px;
  border: 1px solid #e4e7ed;
  border-radius: 4px;
  cursor: pointer;
  transition: all 0.3s;
}

.test-option:hover {
  border-color: #409EFF;
  background-color: #f0f9ff;
}

.test-actions {
  text-align: center;
  margin-bottom: 20px;
}

.test-result {
  margin-bottom: 20px;
}
</style>