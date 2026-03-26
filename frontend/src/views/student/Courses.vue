<template>
  <div class="courses-learning">
    <!-- 课程列表 -->
    <el-card>
      <template #header>
        <div class="card-header">
          <span>我的课程</span>
          <div class="header-actions">
            <el-input
              v-model="searchKeyword"
              placeholder="搜索课程"
              class="course-search-input"
              clearable
              @input="loadCourses"
            />
            <el-button type="primary" @click="$router.push('/student/classes')">加入班级</el-button>
          </div>
        </div>
      </template>

      <el-row :gutter="20" v-loading="loading">
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
              <h3>{{ course.courseName }}</h3>
              <p class="description">{{ course.courseDescription }}</p>
              <div class="class-info">
                <span class="class-name">{{ course.className }}</span>
                <span class="teacher-name">{{ course.teacherName }}</span>
              </div>
              <div class="course-stats">
                <div class="stat-item">
                  <span class="label">单元数：</span>
                  <span class="value">{{ course.totalUnits }}</span>
                </div>
                <div class="stat-item">
                  <span class="label">已学习：</span>
                  <span class="value">{{ course.learnedWords }}个单词</span>
                </div>
                <div class="stat-item">
                  <span class="label">学习时长：</span>
                  <span class="value">{{ course.studyTime }}分钟</span>
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
                <span class="join-date">加入时间：{{ formatDate(course.joinTime) }}</span>
              </div>
            </div>
          </el-card>
        </el-col>
      </el-row>

      <!-- 空状态 -->
      <el-empty v-if="!loading && courses.length === 0" description="暂无课程">
        <el-button type="primary" @click="$router.push('/student/classes')">加入班级</el-button>
      </el-empty>
    </el-card>

    <!-- 课程单元学习对话框 -->
    <el-dialog
      v-model="unitDialogVisible"
      :title="`${currentCourse?.courseName || '课程'} - 学习内容`"
      width="min(1200px, 96vw)"
      top="5vh"
      :close-on-click-modal="false"
    >
      <div class="course-content">
        <el-tabs v-model="activeTab">
          <!-- 单元学习 -->
          <el-tab-pane label="单元学习" name="units">
            <el-row :gutter="20">
              <el-col
                v-for="unit in units"
                :key="unit.id"
                :xs="24"
                :sm="12"
                :md="8"
              >
                <el-card class="unit-card" :class="{ completed: unit.progress >= 100 }" @click="enterUnit(unit)">
                  <div class="unit-header">
                    <h4>{{ unit.name }}</h4>
                    <el-tag v-if="unit.progress >= 100" type="success" size="small">已完成</el-tag>
                    <el-tag v-else-if="unit.progress > 0" type="warning" size="small">学习中</el-tag>
                    <el-tag v-else type="info" size="small">未开始</el-tag>
                  </div>
                  <p class="unit-description">{{ unit.description }}</p>
                  <div class="unit-content">
                    <div class="content-item">
                      <el-icon><Document /></el-icon>
                      <span>课文：{{ unit.textCount || unit.texts?.length || 0 }}篇</span>
                    </div>
                    <div class="content-item">
                      <el-icon><Reading /></el-icon>
                      <span>单词：{{ unit.wordCount || unit.words?.length || 0 }}个</span>
                    </div>
                  </div>
                  <div class="learning-stats" v-if="unit.learnedWords > 0">
                    <div class="stat-row">
                      <span>已学习：{{ unit.learnedWords }}/{{ unit.wordCount }}个单词</span>
                    </div>
                    <div class="stat-row">
                      <span>学习时长：{{ unit.studyTime }}分钟</span>
                    </div>
                    <div class="progress-bar">
                      <el-progress :percentage="unit.progress" :show-text="false" stroke-width="4" />
                    </div>
                  </div>
                </el-card>
              </el-col>
            </el-row>
          </el-tab-pane>

          <!-- 生词复习 -->
          <el-tab-pane label="生词复习" name="vocabulary">
            <div class="vocabulary-review">
              <div class="review-header">
                <h3>课程生词本</h3>
                <div class="review-stats">
                  <span>新词：<strong>{{ vocabularyStats.newWords }}</strong></span>
                  <span>学习中：<strong>{{ vocabularyStats.learningWords }}</strong></span>
                  <span>已掌握：<strong>{{ vocabularyStats.masteredWords }}</strong></span>
                </div>
                <div class="review-actions">
                  <el-select v-model="masteryFilter" placeholder="筛选掌握程度" clearable @change="loadVocabulary" style="width: 150px; margin-right: 10px">
                    <el-option label="全部" value="" />
                    <el-option label="新词" value="NEW" />
                    <el-option label="学习中" value="LEARNING" />
                    <el-option label="已掌握" value="MASTERED" />
                  </el-select>
                  <el-button type="primary" @click="startReview" :disabled="vocabularyList.length === 0">
                    开始复习
                  </el-button>
                </div>
              </div>

              <div class="vocabulary-list" v-loading="vocabularyLoading">
                <div
                  v-for="item in vocabularyList"
                  :key="item.id"
                  class="vocabulary-item"
                  @click="showWordDetail(item)"
                >
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
                  </div>
                  <div class="word-actions">
                    <el-button size="small" @click.stop="playAudio(item)" :disabled="!item.audioUrl">
                      <el-icon><VideoPlay /></el-icon>
                    </el-button>
                    <el-button size="small" @click.stop="markAsLearned(item)" v-if="item.masteryLevel !== 'MASTERED'">
                      <el-icon><Check /></el-icon>
                    </el-button>
                  </div>
                </div>
              </div>

              <el-empty v-if="!vocabularyLoading && vocabularyList.length === 0" description="暂无生词" />
            </div>
          </el-tab-pane>

          <!-- 学习统计 -->
          <el-tab-pane label="学习统计" name="stats">
            <div class="course-stats" v-loading="statsLoading">
              <el-row :gutter="20">
                <el-col :span="8">
                  <el-card class="stat-card">
                    <div class="stat-content">
                      <div class="stat-number">{{ courseOverview.totalWords }}</div>
                      <div class="stat-label">总词汇量</div>
                    </div>
                  </el-card>
                </el-col>
                <el-col :span="8">
                  <el-card class="stat-card">
                    <div class="stat-content">
                      <div class="stat-number">{{ courseOverview.masteredWords }}</div>
                      <div class="stat-label">已掌握</div>
                    </div>
                  </el-card>
                </el-col>
                <el-col :span="8">
                  <el-card class="stat-card">
                    <div class="stat-content">
                      <div class="stat-number">{{ courseOverview.totalTime }}</div>
                      <div class="stat-label">学习时长(分)</div>
                    </div>
                  </el-card>
                </el-col>
              </el-row>

              <div class="progress-chart">
                <h4>单元学习进度</h4>
                <div class="unit-progress-list">
                  <div v-for="unit in units" :key="unit.id" class="unit-progress-item">
                    <span class="unit-name">{{ unit.name }}</span>
                    <el-progress :percentage="unit.progress" :stroke-width="8" />
                    <span class="progress-text">{{ unit.learnedWords }}/{{ unit.wordCount }}</span>
                  </div>
                </div>
              </div>
            </div>
          </el-tab-pane>
        </el-tabs>
      </div>
    </el-dialog>

    <!-- 单元内容学习对话框 -->
    <el-dialog
      v-model="contentDialogVisible"
      :title="(currentUnit?.name || '单元') + ' - 内容学习'"
      width="min(1000px, 96vw)"
      top="3vh"
      :close-on-click-modal="false"
    >
      <div class="unit-learning">
        <el-tabs v-model="contentTab">
          <!-- 课文学习 -->
          <el-tab-pane label="课文学习" name="texts">
            <div class="text-list">
              <div v-for="text in unitTexts" :key="text.id" class="text-item">
                <div class="text-header">
                  <h4>{{ text.title }}</h4>
                  <div class="text-meta">
                    <el-tag :type="getDifficultyType(text.difficulty)" size="small">
                      {{ text.difficulty }}
                    </el-tag>
                    <el-button size="small" @click="startTextLearning(text)">
                      开始学习
                    </el-button>
                  </div>
                </div>
                <p class="text-preview">{{ getTextPreview(text.content) }}</p>
              </div>
            </div>
          </el-tab-pane>

          <!-- 单词学习 -->
          <el-tab-pane label="单词学习" name="words">
            <div class="word-learning-control">
              <div class="learning-header">
                <h3>单词学习 - {{ currentUnit?.name || '单元' }}</h3>
                <div class="learning-actions">
                  <el-button type="primary" @click="startWordLearning" :disabled="unitWords.length === 0">
                    开始学习
                  </el-button>
                  <el-button @click="startWordTest" :disabled="unitWords.length === 0">
                    单词测试
                  </el-button>
                </div>
              </div>

              <div class="word-list">
                <el-row :gutter="10">
                  <el-col :span="6" v-for="word in unitWords" :key="word.id">
                    <el-card class="word-item" @click="showWordDetail(word)">
                      <div class="word-content">
                        <h4>{{ word.word }}</h4>
                        <p class="pronunciation">{{ word.pronunciation }}</p>
                        <p class="definition">{{ word.definition }}</p>
                      </div>
                      <div class="word-status">
                        <el-tag v-if="word.inVocabulary" :type="getMasteryType(word.masteryLevel)" size="small">
                          {{ getMasteryText(word.masteryLevel) }}
                        </el-tag>
                      </div>
                    </el-card>
                  </el-col>
                </el-row>
              </div>
            </div>
          </el-tab-pane>
        </el-tabs>
      </div>
    </el-dialog>

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
              {{ selectedWord.difficulty }}
            </el-tag>
          </div>
        </div>

        <div class="word-actions">
          <el-button
            type="primary"
            @click="addToVocabulary(selectedWord)"
            :disabled="selectedWord.inVocabulary"
          >
            {{ selectedWord.inVocabulary ? '已在生词本' : '加入生词本' }}
          </el-button>
          <el-button @click="markAsLearned(selectedWord)" v-if="selectedWord.masteryLevel !== 'MASTERED'">
            标记为已认识
          </el-button>
        </div>
      </div>
    </el-dialog>

    <!-- 复习对话框 -->
    <el-dialog v-model="reviewDialogVisible" title="生词复习" width="min(800px, 96vw)" :close-on-click-modal="false">
      <div class="review-session" v-if="reviewWords.length > 0 && currentReviewIndex < reviewWords.length">
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
        <div class="review-actions-complete">
          <el-button type="primary" @click="reviewDialogVisible = false">完成</el-button>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onUnmounted, onBeforeUnmount, watch, onActivated } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  getStudentCourses,
  getCourseUnits,
  getUnitContent,
  getTextDetail,
  getWordDetail,
  addToVocabulary as addToVocabularyAPI,
  getVocabularyList,
  updateMasteryLevel,
  startStudySession,
  submitStudyResult
} from '@/api/student'

const router = useRouter()
const route = useRoute()

const loading = ref(false)
const searchKeyword = ref('')
const courses = ref([])

// 课程单元对话框
const unitDialogVisible = ref(false)
const currentCourse = ref({})
const units = ref([])
const activeTab = ref('units')

// 生词复习相关
const vocabularyLoading = ref(false)
const vocabularyList = ref([])
const vocabularyStats = reactive({
  newWords: 0,
  learningWords: 0,
  masteredWords: 0
})
const masteryFilter = ref('')

// 学习统计相关
const statsLoading = ref(false)
const courseOverview = reactive({
  totalWords: 0,
  masteredWords: 0,
  totalTime: 0
})

// 单元内容学习对话框
const contentDialogVisible = ref(false)
const currentUnit = ref({})
const unitTexts = ref([])
const unitWords = ref([])
const contentTab = ref('texts')

// 单词详情弹窗
const wordDialogVisible = ref(false)
const selectedWord = ref(null)

// 复习模式相关
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

// setTimeout 清理追踪
const pendingTimers = []
const safeTimeout = (fn, delay) => {
  const id = setTimeout(fn, delay)
  pendingTimers.push(id)
  return id
}

// 加载课程列表
const loadCourses = async () => {
  loading.value = true
  try {
    const response = await getStudentCourses()
    if (response?.code === 200 && response?.data) {
      // 后端返回的是 { courses: [...] } 结构
      let courseList = Array.isArray(response.data?.courses) ? response.data.courses : []

      // 前端搜索过滤
      if (searchKeyword.value) {
        const keyword = searchKeyword.value.toLowerCase()
        courseList = courseList.filter(course =>
          course.courseName?.toLowerCase().includes(keyword) ||
          course.className?.toLowerCase().includes(keyword) ||
          course.teacherName?.toLowerCase().includes(keyword)
        )
      }

      courses.value = courseList
    }
  } catch (error) {
    console.error('加载课程失败:', error)
    ElMessage.error('加载课程失败')
  } finally {
    loading.value = false
  }
}

// 进入课程
const enterCourse = async (course) => {
  // 先校验 courseId 再打开对话框，避免空状态弹窗
  if (!course.courseId) {
    ElMessage.error('课程ID不存在')
    return
  }

  currentCourse.value = course
  unitDialogVisible.value = true
  activeTab.value = 'units'

  await loadUnits(course.courseId)
  await loadVocabulary()
  await loadCourseStats()

  // 刷新课程列表以更新进度
  await refreshCourseProgress(course.courseId)
}

// 刷新课程进度
const refreshCourseProgress = async (courseId) => {
  try {
    const response = await getStudentCourses()
    if (response?.code === 200 && response?.data) {
      const updatedCourses = response.data?.courses || []
      const updatedCourse = updatedCourses.find(c => c.courseId === courseId)
      if (updatedCourse) {
        // 更新课程列表中的对应课程
        const courseIndex = courses.value.findIndex(c => c.courseId === courseId)
        if (courseIndex !== -1) {
          courses.value[courseIndex] = { ...courses.value[courseIndex], ...updatedCourse }
        }
        // 如果当前打开的课程，也更新
        if (currentCourse.value.courseId === courseId) {
          currentCourse.value = { ...currentCourse.value, ...updatedCourse }
        }
      }
    }
  } catch (error) {
    console.error('刷新课程进度失败:', error)
  }
}

// 加载单元列表
const loadUnits = async (courseId) => {
  try {
    if (!courseId) {
      console.error('courseId 为空')
      return
    }

    const response = await getCourseUnits(courseId)
    if (response?.code === 200 && response?.data) {
      // 后端返回的是 { units: [...] } 结构
      units.value = Array.isArray(response.data?.units) ? response.data.units : []

      // 更新当前课程的进度信息
      if (currentCourse.value.courseId === courseId) {
        updateCourseProgress()
        // 同时刷新课程列表中的进度
        await refreshCourseProgress(courseId)
      }
    }
  } catch (error) {
    console.error('加载单元失败:', error)
    ElMessage.error('加载单元失败')
  }
}

// 更新课程进度
const updateCourseProgress = () => {
  if (!currentCourse.value.courseId) return
  
  // 计算已学习单元数
  const learnedUnits = units.value.filter(unit => unit.progress >= 80).length
  const totalUnits = units.value.length
  
  // 更新当前课程对象
  const courseIndex = courses.value.findIndex(c => c.courseId === currentCourse.value.courseId)
  if (courseIndex !== -1) {
    courses.value[courseIndex].learnedUnits = learnedUnits
    courses.value[courseIndex].totalUnits = totalUnits
    // 同步更新currentCourse
    currentCourse.value.learnedUnits = learnedUnits
    currentCourse.value.totalUnits = totalUnits
  }
}

// 加载课程生词本
const loadVocabulary = async () => {
  vocabularyLoading.value = true
  try {
    const params = {
      courseId: currentCourse.value.courseId,
      masteryLevel: masteryFilter.value
    }
    const response = await getVocabularyList(params)
    if (response?.code === 200 && response?.data) {
      vocabularyList.value = Array.isArray(response.data?.records) ? response.data.records : []

      // 统计生词数据
      vocabularyStats.newWords = vocabularyList.value.filter(w => w.masteryLevel === 'NEW').length
      vocabularyStats.learningWords = vocabularyList.value.filter(w => w.masteryLevel === 'LEARNING').length
      vocabularyStats.masteredWords = vocabularyList.value.filter(w => w.masteryLevel === 'MASTERED').length
    }
  } catch (error) {
    console.error('加载生词本失败:', error)
  } finally {
    vocabularyLoading.value = false
  }
}

// 加载课程统计
const loadCourseStats = async () => {
  statsLoading.value = true
  try {
    // 这里可以调用统计API
    // 暂时使用单元数据统计
    courseOverview.totalWords = units.value.reduce((sum, unit) => sum + (unit.wordCount || 0), 0)
    courseOverview.masteredWords = units.value.reduce((sum, unit) => sum + (unit.masteredWords || 0), 0)
    courseOverview.totalTime = units.value.reduce((sum, unit) => sum + (unit.studyTime || 0), 0)
  } catch (error) {
    console.error('加载统计数据失败:', error)
  } finally {
    statsLoading.value = false
  }
}

// 进入单元学习
const enterUnit = async (unit) => {
  currentUnit.value = unit
  contentDialogVisible.value = true
  contentTab.value = 'texts'

  await loadUnitContent(unit.id)
}

// 加载单元内容
const loadUnitContent = async (unitId) => {
  try {
    const response = await getUnitContent(unitId)

    if (response?.code === 200 && response?.data) {
      unitTexts.value = Array.isArray(response.data?.texts) ? response.data.texts : []
      unitWords.value = Array.isArray(response.data?.words) ? response.data.words : []
    } else {
      console.error('API响应错误:', response)
    }
  } catch (error) {
    console.error('加载单元内容失败:', error)
    ElMessage.error('加载单元内容失败')
  }
}

// 开始课文学习
const startTextLearning = (text) => {
  router.push({
    path: '/student/text-learning',
    query: {
      textId: text.id,
      courseId: currentCourse.value.courseId,
      unitId: currentUnit.value.id,
      from: 'course'
    }
  })
}

// 开始单词学习
const startWordLearning = () => {
  router.push(`/student/vocabulary?unitId=${currentUnit.value.id}`)
}

// 开始单词测试
const startWordTest = () => {
  router.push(`/student/vocabulary?unitId=${currentUnit.value.id}`)
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

// 添加到生词本
const addToVocabulary = async (word) => {
  try {
    const response = await addToVocabularyAPI({
      wordId: word?.wordId || word?.id
    })
    if (response?.code === 200) {
      // 根据响应结果显示不同的消息
      if (response.data && typeof response.data === 'string' && response.data.includes('已在生词本中')) {
        ElMessage.info('单词已在生词本中')
      } else {
        ElMessage.success('已添加到生词本')
      }
      word.inVocabulary = true
      word.masteryLevel = 'NEW'
      loadVocabulary() // 刷新生词本列表
    }
  } catch (error) {
    console.error('添加生词失败:', error)
    ElMessage.error('添加生词失败')
  }
}

// 标记为已认识
const markAsLearned = async (word) => {
  try {
    const wordId = word?.id || word?.wordId
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
      word.masteryLevel = 'MASTERED'
      loadVocabulary() // 刷新生词本列表
    }
  } catch (error) {
    console.error('标记失败:', error)
    ElMessage.error('标记失败')
  }
}

// 复习计算属性
const currentReviewWord = computed(() => {
  return reviewWords.value[currentReviewIndex.value] || {
    word: '', definition: '', pronunciation: '', exampleSentence: '', audioUrl: null
  }
})

const reviewProgress = computed(() => {
  if (reviewWords.value.length === 0) return 100
  return Math.round((currentReviewIndex.value / reviewWords.value.length) * 100)
})

// 提交复习结果
const submitReview = async (result) => {
  const currentWord = reviewWords.value[currentReviewIndex.value]
  if (!currentWord) return

  reviewSummary.totalWords++
  if (result === 'KNOWN') reviewSummary.knownWords++
  else if (result === 'UNCERTAIN') reviewSummary.uncertainWords++
  else reviewSummary.unknownWords++

  try {
    const wordId = currentWord?.id || currentWord?.wordId
    if (!wordId) {
      console.error('单词ID不存在')
      return
    }
    const masteryLevel = result === 'KNOWN' ? 'MASTERED' : 'LEARNING'
    await updateMasteryLevel(wordId, {
      masteryLevel,
      isCorrect: result === 'KNOWN'
    })
  } catch (error) {
    console.error('更新单词状态失败:', error)
  }

  currentReviewIndex.value++
  showAnswer.value = false
}

// 开始复习
const startReview = () => {
  if (vocabularyList.value.length === 0) {
    ElMessage.warning('当前课程暂无生词，无法开始复习')
    return
  }
  
  // 筛选需要复习的单词（排除已掌握的）
  const reviewableWords = vocabularyList.value.filter(word => word.masteryLevel !== 'MASTERED')
  
  if (reviewableWords.length === 0) {
    ElMessage.info('所有单词已掌握，无需复习')
    return
  }
  
  // 打开复习对话框
  reviewWords.value = reviewableWords
  currentReviewIndex.value = 0
  showAnswer.value = false
  reviewDialogVisible.value = true
  Object.assign(reviewSummary, {
    totalWords: 0,
    knownWords: 0,
    uncertainWords: 0,
    unknownWords: 0
  })
}

// 播放音频
let currentAudio = null

const playAudio = (word) => {
  if (word?.audioUrl) {
    if (currentAudio) {
      currentAudio.pause()
      currentAudio.onended = null
      currentAudio.onerror = null
      currentAudio = null
    }
    currentAudio = new Audio(word.audioUrl)
    currentAudio.onended = () => {
      currentAudio = null
    }
    currentAudio.onerror = () => {
      console.error('音频加载或播放出错')
      currentAudio = null
    }
    currentAudio.play().catch(error => {
      console.error('播放音频失败:', error)
      currentAudio = null
      ElMessage.error('播放音频失败')
    })
  }
}
// 获取课程进度
const getCourseProgress = (course) => {
  // 优先使用后端返回的progress字段
  if (course.progress !== undefined && course.progress !== null) {
    // 后端返回 0.0-1.0 范围，转换为 0-100 百分比
    const progress = course.progress
    const percentage = progress <= 1 ? progress * 100 : progress
    return Math.round(percentage)
  }
  // 如果没有progress字段，使用learnedUnits计算
  if (!course.totalUnits) return 0
  return Math.round(((course.learnedUnits || 0) / course.totalUnits) * 100)
}

// 获取进度条颜色
const getProgressColor = (percentage) => {
  if (percentage >= 80) return '#67c23a'
  if (percentage >= 50) return '#e6a23c'
  return '#f56c6c'
}

// 获取掌握程度类型
const getMasteryType = (level) => {
  const types = {
    'NEW': 'info',
    'LEARNING': 'warning',
    'MASTERED': 'success'
  }
  return types[level] || 'info'
}

// 获取掌握程度文本
const getMasteryText = (level) => {
  const texts = {
    'NEW': '新词',
    'LEARNING': '学习中',
    'MASTERED': '已掌握'
  }
  return texts[level] || '未知'
}

// 获取难度类型
const getDifficultyType = (difficulty) => {
  const types = {
    'EASY': 'success',
    'MEDIUM': 'warning',
    'HARD': 'danger'
  }
  return types[difficulty] || 'info'
}

// 获取课文预览
const getTextPreview = (content) => {
  if (!content) return ''
  return content.length > 100 ? content.substring(0, 100) + '...' : content
}

// 格式化日期
const formatDate = (date) => {
  if (!date) return ''
  return new Date(date).toLocaleDateString('zh-CN')
}

// 监听路由变化，从其他页面返回时刷新
watch(() => route.path, (newPath, oldPath) => {
  if (newPath === '/student/courses') {
    // 如果是从课文学习页面返回，强制刷新
    if (oldPath === '/student/text-learning') {
      loadCourses()
      // 如果打开了课程对话框，刷新单元数据
      if (unitDialogVisible.value && currentCourse.value.courseId) {
        safeTimeout(() => {
          loadUnits(currentCourse.value.courseId).then(() => {
            loadVocabulary()
            loadCourseStats()
            refreshCourseProgress(currentCourse.value.courseId)
          })
        }, 300)
      }
    } else {
      loadCourses()
    }
    
    // 检查是否有刷新参数
    if (route.query.refresh) {
      // 如果打开了课程对话框，刷新单元数据
      if (unitDialogVisible.value && currentCourse.value.courseId) {
        loadUnits(currentCourse.value.courseId).then(() => {
          loadVocabulary()
          loadCourseStats()
          refreshCourseProgress(currentCourse.value.courseId)
        })
      }
    }
    // 检查是否有课程ID参数，如果有则打开课程对话框
    if (route.query.courseId) {
      const courseId = Number(route.query.courseId)
      // 等待课程列表加载完成
      safeTimeout(() => {
        const course = courses.value.find(c => c.courseId === courseId)
        if (course) {
          enterCourse(course)
        }
      }, 300)
    }
  }
}, { immediate: false })

// 监听路由查询参数变化
watch(() => route.query, (newQuery) => {
  if (route.path === '/student/courses' && newQuery.refresh) {
    // 刷新课程列表
    loadCourses().then(() => {
      // 如果打开了课程对话框，刷新单元数据
      if (unitDialogVisible.value && currentCourse.value.courseId) {
        loadUnits(currentCourse.value.courseId).then(() => {
          loadVocabulary()
          loadCourseStats()
          refreshCourseProgress(currentCourse.value.courseId)
        })
      }
      // 如果有课程ID参数，打开课程对话框
      if (newQuery.courseId) {
        const courseId = Number(newQuery.courseId)
        const course = courses.value.find(c => c.courseId === courseId)
        if (course && !unitDialogVisible.value) {
          safeTimeout(() => {
            enterCourse(course)
          }, 300)
        }
      }
    })
  }
}, { deep: true })

// 监听自定义刷新事件（命名函数以便 onUnmounted 时正确移除）
const handleCourseRefresh = () => {
  if (route.path === '/student/courses') {
    loadCourses()
    if (unitDialogVisible.value && currentCourse.value.courseId) {
      loadUnits(currentCourse.value.courseId).then(() => {
        loadVocabulary()
        loadCourseStats()
        refreshCourseProgress(currentCourse.value.courseId)
      })
    }
  }
}
if (typeof window !== 'undefined') {
  window.addEventListener('course-refresh', handleCourseRefresh)
}

onUnmounted(() => {
  // 清理所有pending timers
  pendingTimers.forEach(id => clearTimeout(id))
  pendingTimers.length = 0

  if (typeof window !== 'undefined') {
    window.removeEventListener('course-refresh', handleCourseRefresh)
  }
  if (currentAudio) {
    currentAudio.pause()
    currentAudio.onended = null
    currentAudio.onerror = null
    currentAudio = null
  }
})

// 组件激活时刷新（用于keep-alive场景）
onActivated(() => {
  loadCourses()
  // 如果当前打开了课程对话框，刷新单元数据
  if (unitDialogVisible.value && currentCourse.value.courseId) {
    loadUnits(currentCourse.value.courseId)
    loadVocabulary()
    loadCourseStats()
  }
})

onMounted(() => {
  loadCourses()

  // 检查是否有传入的课程ID
  const courseId = route.query.courseId
  if (courseId) {
    // 找到对应课程并打开
    const timerId = safeTimeout(() => {
      if (Array.isArray(courses.value)) {
        const course = courses.value.find(c => c.courseId == courseId)
        if (course) {
          enterCourse(course)
        }
      }
    }, 1000)
  }
})
</script>

<style scoped>
.courses-learning {
  padding: 20px;
  background-color: var(--bg-secondary);
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', 'PingFang SC', sans-serif;
  min-height: calc(100vh - 120px);
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.course-search-input {
  width: 200px;
}

@media (max-width: 600px) {
  .course-search-input {
    width: 100%;
  }
  .card-header {
    flex-direction: column;
    align-items: flex-start;
    gap: 12px;
  }
  .header-actions {
    width: 100%;
  }
}

.course-card {
  margin-bottom: 20px;
  cursor: pointer;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  border-radius: 20px;
  border: 1px solid var(--border-light);
  background: rgba(255, 255, 255, 0.7);
  backdrop-filter: blur(16px);
  -webkit-backdrop-filter: blur(16px);
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.05);
  overflow: hidden;
}

.course-card:hover {
  transform: translateY(-4px);
  box-shadow: 0 12px 28px rgba(0, 0, 0, 0.1);
}

.course-cover {
  height: 140px;
  overflow: hidden;
  position: relative;
}

.cover-image {
  width: 100%;
  height: 100%;
  object-fit: cover;
  transition: transform 0.5s ease;
}

.course-card:hover .cover-image {
  transform: scale(1.05);
}

.cover-placeholder {
  width: 100%;
  height: 100%;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
}

.course-info {
  padding: 15px;
}

.course-info h3 {
  margin: 0 0 8px 0;
  color: #333;
  font-size: 16px;
}

.description {
  color: #666;
  font-size: 14px;
  margin: 8px 0;
  height: 40px;
  overflow: hidden;
}

.class-info {
  display: flex;
  justify-content: space-between;
  margin: 8px 0;
  font-size: 12px;
  color: #888;
}

.course-stats {
  margin: 10px 0;
}

.stat-item {
  display: flex;
  justify-content: space-between;
  margin: 4px 0;
  font-size: 12px;
}

.stat-item .label {
  color: #666;
}

.stat-item .value {
  color: #409EFF;
  font-weight: bold;
}

.progress-bar {
  margin: 10px 0;
}

.course-meta {
  margin-top: 10px;
  padding-top: 10px;
  border-top: 1px solid #f0f0f0;
}

.join-date {
  font-size: 12px;
  color: #888;
}

/* 单元卡片样式 */
.unit-card {
  margin-bottom: 15px;
  cursor: pointer;
  transition: all 0.3s;
}

.unit-card:hover {
  transform: translateY(-2px);
}

.unit-card.completed {
  border: 2px solid #67c23a;
}

.unit-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

.unit-header h4 {
  margin: 0;
  color: #333;
}

.unit-description {
  color: #666;
  font-size: 14px;
  margin: 8px 0;
}

.unit-content {
  margin: 10px 0;
}

.content-item {
  display: flex;
  align-items: center;
  margin: 5px 0;
  font-size: 12px;
  color: #666;
}

.content-item .el-icon {
  margin-right: 5px;
}

.learning-stats {
  margin-top: 10px;
  padding-top: 10px;
  border-top: 1px solid #f0f0f0;
}

.stat-row {
  display: flex;
  justify-content: space-between;
  font-size: 12px;
  color: #666;
  margin: 4px 0;
}

/* 生词复习样式 */
.vocabulary-review {
  max-height: 600px;
  overflow-y: auto;
}

.review-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
  padding-bottom: 15px;
  border-bottom: 1px solid #f0f0f0;
}

.review-stats {
  display: flex;
  gap: 20px;
}

.review-actions {
  display: flex;
  align-items: center;
}

.vocabulary-list {
  max-height: 500px;
  overflow-y: auto;
}

.vocabulary-item {
  padding: 15px;
  border-bottom: 1px solid #f0f0f0;
  display: flex;
  justify-content: space-between;
  align-items: center;
  cursor: pointer;
  transition: background-color 0.3s;
}

.vocabulary-item:hover {
  background-color: #f8f9fa;
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
  margin: 4px 0;
}

.definition {
  color: #333;
  margin: 8px 0;
  word-break: break-word;
  overflow-wrap: break-word;
}

.example {
  color: #666;
  font-size: 14px;
  margin-top: 8px;
  word-break: break-word;
  overflow-wrap: break-word;
}

.example-label {
  font-weight: bold;
}

.word-actions {
  display: flex;
  gap: 5px;
}

/* 课程统计样式 */
.course-stats {
  padding: 20px 0;
}

.stat-card {
  text-align: center;
}

.stat-number {
  font-size: 32px;
  font-weight: bold;
  color: #409EFF;
  margin-bottom: 5px;
}

.stat-label {
  color: #666;
  font-size: 14px;
}

.progress-chart {
  margin-top: 30px;
}

.progress-chart h4 {
  margin-bottom: 20px;
  color: #333;
}

.unit-progress-list {
  display: flex;
  flex-direction: column;
  gap: 15px;
}

.unit-progress-item {
  display: flex;
  align-items: center;
  gap: 15px;
  margin-bottom: 15px;
}

.unit-name {
  width: 120px;
  font-size: 14px;
  color: #333;
}

.unit-progress-item .el-progress {
  flex: 1;
}

.progress-text {
  width: 60px;
  text-align: right;
  font-size: 12px;
  color: #666;
}

/* 单元学习样式 */
.unit-learning {
  min-height: 500px;
}

.text-list {
  max-height: 500px;
  overflow-y: auto;
}

.text-item {
  padding: 15px;
  border-bottom: 1px solid #f0f0f0;
}

.text-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
}

.text-header h4 {
  margin: 0;
  color: #333;
}

.text-meta {
  display: flex;
  align-items: center;
  gap: 10px;
}

.text-preview {
  color: #666;
  line-height: 1.5;
}

.word-learning-control {
  min-height: 500px;
}

.learning-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
  padding-bottom: 15px;
  border-bottom: 1px solid #f0f0f0;
}

.learning-actions {
  display: flex;
  gap: 10px;
}

.word-list {
  max-height: 500px;
  overflow-y: auto;
}

.word-item {
  margin-bottom: 10px;
  cursor: pointer;
  transition: all 0.3s;
}

.word-item:hover {
  transform: translateY(-2px);
}

.word-content h4 {
  margin: 0 0 5px 0;
  color: #333;
}

.word-content .pronunciation {
  color: #666;
  font-style: italic;
  font-size: 12px;
  margin: 2px 0;
}

.word-content .definition {
  color: #333;
  font-size: 12px;
  margin: 5px 0;
}

/* 复习模式样式 */
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
  max-width: 600px;
  margin: 0 auto;
  min-height: 400px;
  position: relative;
  perspective: 1000px;
}

.card-front,
.card-back {
  width: 100%;
  height: 100%;
  padding: 40px;
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.1);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  text-align: center;
  transition: transform 0.6s;
  backface-visibility: hidden;
}

.card-back {
  transform: rotateY(180deg);
  position: absolute;
  top: 0;
  left: 0;
}

.word-card.flipped .card-front {
  transform: rotateY(-180deg);
}

.word-card.flipped .card-back {
  transform: rotateY(0);
}

.word-card .word {
  font-size: 48px;
  font-weight: bold;
  margin-bottom: 30px;
  color: #333;
}

.word-card .pronunciation {
  font-size: 20px;
  color: #666;
  margin-bottom: 20px;
}

.word-card .definition {
  font-size: 18px;
  line-height: 1.6;
  margin-bottom: 20px;
  color: #333;
}

.word-card .example {
  margin-top: 20px;
  padding: 15px;
  background: #f5f7fa;
  border-radius: 8px;
  font-size: 16px;
  line-height: 1.6;
}

.example-label {
  font-weight: bold;
  color: #409eff;
}

.card-actions {
  margin-top: 30px;
  display: flex;
  gap: 15px;
}

.review-actions {
  margin-top: 30px;
  display: flex;
  gap: 15px;
  justify-content: center;
}

.review-complete {
  text-align: center;
  padding: 40px;
}

.review-complete h3 {
  font-size: 28px;
  margin-bottom: 30px;
  color: #67c23a;
}

.review-summary {
  display: flex;
  flex-direction: column;
  gap: 15px;
  margin-bottom: 30px;
  max-width: 400px;
  margin-left: auto;
  margin-right: auto;
}

.summary-item {
  display: flex;
  justify-content: space-between;
  padding: 15px;
  background: #f5f7fa;
  border-radius: 8px;
  font-size: 16px;
}

.summary-item strong {
  font-size: 18px;
}

.summary-item .known {
  color: #67c23a;
}

.summary-item .uncertain {
  color: #e6a23c;
}

.summary-item .unknown {
  color: #f56c6c;
}

.review-actions-complete {
  margin-top: 30px;
}

.word-status {
  margin-top: 8px;
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

.word-actions {
  display: flex;
  gap: 10px;
  justify-content: center;
}
</style>