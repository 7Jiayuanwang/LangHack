<template>
  <div class="interactive-text-page premium-reader" :class="{ 'fullscreen-mode': isFullscreen }">
    
    <!-- Premium Sticky Header -->
    <header class="glass-header" :class="{ 'header-hidden': headerHidden }" ref="pageHeader">
      <div class="header-inner">
        <div class="header-left">
          <button class="minimal-back-btn" @click="goBack">
            <el-icon><ArrowLeft /></el-icon>
            <span>{{ fromSource === 'assignment' ? '返回任务' : '返回' }}</span>
          </button>
          <div class="header-divider"></div>
          <h2 class="header-title" :title="textTitle">{{ textTitle }}</h2>
        </div>
        
        <div class="header-right">
          <div class="difficulty-pill" :class="difficultyClass">
            <span class="pill-label">难度</span>
            <span class="pill-value">{{ difficultyText }}</span>
          </div>
          <button class="favorite-btn" :class="{ 'is-favorite': isFavorite }" @click="addToFavorite">
            <el-icon><Star v-if="!isFavorite" /><StarFilled v-else /></el-icon>
          </button>
        </div>
      </div>
    </header>

    <main class="reader-container">
      <!-- Article Header Section -->
      <header class="article-header">
        <div class="article-meta">
          <span class="meta-item">
            <el-icon><Clock /></el-icon>
            {{ estimatedTime }} min read
          </span>
          <span class="meta-dot">•</span>
          <span class="meta-item">
            <el-icon><Tickets /></el-icon>
            {{ wordCount }} words
          </span>
        </div>
        
        <h1 class="article-title">{{ textTitle }}</h1>

        <!-- Redesigned Version Selector -->
        <div class="version-pill-selector" v-if="availableVersions.length > 1">
          <div class="pill-container">
            <button
              v-for="version in availableVersions"
              :key="version"
              class="version-pill"
              :class="{ 'active': currentVersion === version }"
              @click="switchVersion(version)"
            >
              <el-icon v-if="version === 'original'"><CircleCheck /></el-icon>
              <el-icon v-else-if="version === 'simplified'"><MagicStick /></el-icon>
              {{ getVersionLabel(version) }}
            </button>
          </div>
        </div>
      </header>

      <!-- Main Content Area -->
      <section class="article-body">
        <InteractiveText
          v-if="!isMarkdown"
          :text="textContent"
          :text-id="textId"
          :unit-id="unitId"
          @word-click="handleWordClick"
          @learning-record="handleLearningRecord"
        />
        
        <div 
          v-else 
          class="markdown-content" 
          v-html="textContent"
          @click="handleMarkdownClick"
        ></div>
      </section>

      <!-- Completion Section -->
      <footer class="reader-footer" v-if="!isCompleted">
        <div class="footer-divider"></div>
        <button 
          class="premium-completion-btn"
          @click="handleCompleteReading"
          :disabled="completing"
        >
          <span class="btn-content" v-if="!completing">
            <el-icon><Checked /></el-icon>
            完成阅读并提交
          </span>
          <span class="btn-content" v-else>
            <el-icon class="is-loading"><Loading /></el-icon>
            正在提交...
          </span>
          <div class="btn-glow"></div>
        </button>
        <p class="completion-hint">完成阅读以记录您的学习进度</p>
      </footer>
      
      <div class="reader-footer completed-notif" v-else>
        <el-result
          icon="success"
          title="阅读已完成"
          sub-title="您已成功记录此学习记录"
        >
        </el-result>
      </div>
    </main>

    <!-- 翻译结果弹窗 -->
    <el-dialog
      v-model="showTranslateDialog"
      title="翻译结果"
      :width="translateDialogWidth"
      class="translate-dialog"
    >
      <div class="translate-content" v-if="!translating">
        <div class="original-text">
          <h4>原文：</h4>
          <p>{{ selectedTextForTranslate }}</p>
        </div>
        <el-divider />
        <div class="translated-text">
          <h4>译文：</h4>
          <p>{{ translatedText }}</p>
        </div>
      </div>
      <div class="loading-content" v-else>
        <el-icon class="is-loading" :size="40"><Loading /></el-icon>
        <p style="margin-top: 10px; color: #666;">翻译中...</p>
      </div>
      <template #footer>
        <el-button @click="showTranslateDialog = false">关闭</el-button>
      </template>
    </el-dialog>

    <!-- 学习建议弹窗 -->
    <el-dialog v-model="showSuggestionDialog" title="学习建议" width="min(500px, 92vw)">
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

    <!-- 单词详情对话框 -->
    <el-dialog 
      v-model="showWordDialog" 
      title="单词详情" 
      width="350px"
      :class="{ 'mobile-word-dialog': true }"
    >
      <div class="word-content" v-if="selectedWordInfo && !loadingWord">
        <div class="word-header">
          <div class="word-title-row">
            <h2 class="word-title">{{ selectedWordInfo.word || '未知单词' }}</h2>
            <el-button 
              v-if="selectedWordInfo.audioUrl || selectedWordInfo.audio" 
              circle 
              size="small" 
              @click="playWordAudio"
            >
              <el-icon><VideoPlay /></el-icon>
            </el-button>
          </div>
          <div class="pronunciation" v-if="selectedWordInfo.phonetic || selectedWordInfo.pronunciation">
            [{{ selectedWordInfo.phonetic || selectedWordInfo.pronunciation }}]
          </div>
        </div>

        <div class="word-body">
          <div class="main-meaning">
            <div class="section-title">释义</div>
            <div class="meaning-content">
              {{ selectedWordInfo.translation || selectedWordInfo.definition }}
            </div>
          </div>
          
          <div class="example-sentences" v-if="selectedWordInfo.exampleSentence || selectedWordInfo.examples">
            <div class="section-title">例句</div>
            <div class="example-item">
              {{ selectedWordInfo.exampleSentence || selectedWordInfo.examples }}
            </div>
          </div>
        </div>
      </div>

      <div class="loading-content" v-else-if="loadingWord">
        <el-icon class="is-loading" :size="40"><Loading /></el-icon>
        <p style="margin-top: 10px; color: #666;">加载中...</p>
      </div>

      <div class="error-content" v-else>
        <el-result icon="error" title="加载失败" sub-title="请稍后重试"></el-result>
      </div>

      <template #footer>
        <div class="dialog-footer">
          <el-button 
            type="primary" 
            :loading="addingToVocabulary" 
            @click="handleAddToVocabulary"
            v-if="selectedWordInfo && selectedWordInfo.found !== false"
          >
            <el-icon><Star /></el-icon> 收藏到生词本
          </el-button>
          <el-button @click="showWordDialog = false">关闭</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 图片预览对话框 -->
    <el-dialog 
      v-model="showImagePreview" 
      title="图片预览" 
      width="95%"
      :class="{ 'image-preview-dialog': true }"
      @close="closeImagePreview"
    >
      <div class="image-preview-content">
        <!-- 缩放控制工具栏 -->
        <div class="image-toolbar">
          <el-button-group>
            <el-button @click="zoomOut" :disabled="imageScale <= 0.1" size="small">
              <el-icon><ZoomOut /></el-icon>
            </el-button>
            <el-button @click="resetZoom" size="small">
              {{ Math.round(imageScale * 100) }}%
            </el-button>
            <el-button @click="zoomIn" :disabled="imageScale >= 5" size="small">
              <el-icon><ZoomIn /></el-icon>
            </el-button>
          </el-button-group>
          <div class="toolbar-tips">
            <span>滚轮缩放 | 拖拽移动 | 双击重置</span>
          </div>
        </div>

        <!-- 图片容器 -->
        <div 
          class="image-container"
          @wheel="handleWheel"
          @mousedown="startDrag"
          @dblclick="handleDoubleClick"
        >
          <img 
            :src="previewImageSrc" 
            :alt="previewImageAlt"
            class="preview-image"
            :style="{
              transform: `scale(${imageScale}) translate(${imageTranslateX}px, ${imageTranslateY}px)`,
              cursor: imageScale > 1 ? (isDragging ? 'grabbing' : 'grab') : 'zoom-in'
            }"
            @load="onImageLoad"
            @error="onImageError"
            @dragstart.prevent
          />
        </div>

        <div class="image-loading" v-if="imageLoading">
          <el-icon class="is-loading" :size="40"><Loading /></el-icon>
          <p>加载中...</p>
        </div>
        <div class="image-error" v-if="imageError">
          <el-icon :size="40"><Picture /></el-icon>
          <p>图片加载失败</p>
        </div>
      </div>
      <template #footer>
        <el-button @click="showImagePreview = false">关闭</el-button>
      </template>
    </el-dialog>

    <!-- 文本选择菜单 -->
    <SelectionMenu
      :visible="showSelectionMenu"
      :position="selectionPosition"
      :underline-label="selectionUnderlineLabel"
      @copy="handleCopy"
      @underline="handleUnderline"
      @share="handleShare"
      @ai-qa="handleAiQa"
    />
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onBeforeUnmount } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowLeft, View, Clock, InfoFilled, Check, Timer, Loading, Picture, ZoomIn, ZoomOut, Star, StarFilled, Tickets, CircleCheck, MagicStick, Checked, VideoPlay } from '@element-plus/icons-vue'
import InteractiveText from '@/components/InteractiveText.vue'
import studentApi, { getVocabularyStats, createUnderline, getUnderlines, deleteUnderline } from '@/api/student'
import { marked } from 'marked'
import DOMPurify from 'dompurify'
import { getAuthHeader } from '@/utils/auth'

// 配置 DOMPurify：允许 img 标签及其属性，允许 data-word 自定义属性
const SANITIZE_CONFIG = {
  ADD_TAGS: ['img'],
  ADD_ATTR: ['src', 'alt', 'title', 'width', 'height', 'loading', 'data-word', 'class', 'target', 'rel'],
  ALLOW_DATA_ATTR: true
}

// 配置 marked：自定义图片渲染，支持 lazy loading
const renderer = new marked.Renderer()
renderer.image = function({ href, title, text }) {
  const titleAttr = title ? ` title="${title}"` : ''
  return `<img src="${href}" alt="${text || ''}"${titleAttr} loading="lazy" />`
}
marked.setOptions({ renderer })
import SelectionMenu from '@/components/SelectionMenu.vue'
import rangy from 'rangy'
import 'rangy/lib/rangy-classapplier'
import 'rangy/lib/rangy-highlighter'
import 'rangy/lib/rangy-serializer'

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
const isMarkdown = ref(false)
const difficulty = ref('MEDIUM')
const learningProgress = ref(0) // 原有的学习进度
const learnedWordsCount = ref(0)
const studyTime = ref(0)
const isCompleted = ref(false)
const isFavorite = ref(false)
const recording = ref(false)
const isFullscreen = ref(true) // 默认开启全屏模式
const showSuggestionDialog = ref(false)
const learningSuggestions = ref([])
const currentRecordId = ref(null)
const completing = ref(false)

// 头部滚动隐藏相关
const headerHidden = ref(false)
const lastScrollY = ref(0)
const pageHeader = ref(null)

// 课文版本相关数据
const textVersions = ref({}) // 存储所有版本内容
const availableVersions = ref([]) // 可用版本列表
const currentVersion = ref('original') // 当前选择的版本


// 单词对话框相关数据
const showWordDialog = ref(false)
const selectedWord = ref(null)
const selectedWordInfo = ref(null)
const loadingWord = ref(false)
const addingToVocabulary = ref(false)

// 图片预览相关数据
const showImagePreview = ref(false)
const previewImageSrc = ref('')
const previewImageAlt = ref('')
const imageLoading = ref(false)
const imageError = ref(false)

// 图片缩放和拖拽相关数据
const imageScale = ref(1)
const imageTranslateX = ref(0)
const imageTranslateY = ref(0)
const isDragging = ref(false)
const dragStartX = ref(0)
const dragStartY = ref(0)
const dragStartTranslateX = ref(0)
const dragStartTranslateY = ref(0)

// 学习计时器
let studyTimer = null
let studyStartTime = null
let accumulatedTime = 0 // 累计学习时间（秒）

// 翻译相关
const showTranslateDialog = ref(false)
const selectedTextForTranslate = ref('')
const translatedText = ref('')
const translating = ref(false)
const isSelectingMode = ref(false)

// 响应式弹窗宽度
const windowWidth = ref(typeof window !== 'undefined' ? window.innerWidth : 1024)
const handleWindowResize = () => { windowWidth.value = window.innerWidth }
let autoStartTimerId = null
let textContentWatchTimerId = null
let floatingBtnResetTimerId = null
let imagePreviewCleanupTimerId = null
let versionSwitchTimerId = null
let selectionMenuHideTimerId = null
let fetchUnderlineTimerId = null
let imageDragMoveListener = null
let imageDragEndListener = null

const translateDialogWidth = computed(() => {
  if (windowWidth.value < 768) return '230px'
  return '400px'
})

// 浮动按钮位置和拖动
const floatingBtnPosition = reactive({ x: typeof window !== 'undefined' ? window.innerWidth - 80 : 800, y: typeof window !== 'undefined' ? window.innerHeight / 2 : 400 })
const isTranslateBtnDragging = ref(false)
const translateBtnDragOffset = reactive({ x: 0, y: 0 })

// 选择菜单相关
const showSelectionMenu = ref(false)
const selectionPosition = reactive({ x: 0, y: 0 })
const selectedTextContent = ref('')
const sharedUnderlines = ref([])

/**
 * 判断当前选区文本是否已完全被某条划线记录覆盖。
 * 用于决定菜单按钮显示"划线"还是"取消划线"。
 */
const selectionIsUnderlined = computed(() => {
  if (!selectedTextContent.value || sharedUnderlines.value.length === 0) return false
  const trimmed = selectedTextContent.value.trim()
  return sharedUnderlines.value.some(u => u.selectedText && u.selectedText.trim() === trimmed)
})

/** SelectionMenu 中划线按钮的动态标签 */
const selectionUnderlineLabel = computed(() => selectionIsUnderlined.value ? '取消划线' : '划线')

// 练习和学习统计相关
const isStudyTimeCompleted = ref(false)
const exercises = ref([])
const completedExercises = ref(0)
const allExercisesCompleted = ref(false)
const exerciseAccuracy = ref(0)
const totalExerciseTime = ref(0)
const vocabularyStats = ref(null)


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
  let text = textContent.value
  // Markdown 模式下 textContent 含 HTML 标签，需要先用原始文本或去掉标签再计算
  if (isMarkdown.value) {
    // 优先使用原始版本的纯文本
    const rawText = textVersions.value?.[currentVersion.value] || ''
    if (rawText) {
      text = rawText
    } else {
      // fallback: 去除 HTML 标签
      text = textContent.value.replace(/<[^>]*>/g, ' ')
    }
  }
  return text.split(/\s+/).filter(word => word.length > 0).length
})

const estimatedTime = computed(() => {
  // 预计每分钟阅读100个单词
  return Math.ceil(wordCount.value / 100)
})

// 规范化纯文本内容：修复双序列化导致的反斜杠转义残留
const normalizeTextContent = (text) => {
  if (!text) return text
  return text
    .replace(/\\n/g, '\n')
    .replace(/\\r/g, '')
    .replace(/\\t/g, '\t')
    .replace(/\\"/g, '"')
    .replace(/\\'/g, "'")
    .replace(/\\\\/g, '\\')
}

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
      textTitle.value = data.title
      difficulty.value = data.difficulty || 'MEDIUM'
      courseId.value = data.courseId
      unitId.value = data.unitId
      courseName.value = data.courseName || '未知课程'
      unitName.value = data.unitName || '未知单元'

      // 从后端获取学习进度和时长
      learningProgress.value = data.learningProgress || 0
      learnedWordsCount.value = data.learnedWordsCount || 0
      isCompleted.value = data.isCompleted || false

      // 检查是否为 Markdown 格式
      if (data.contentType === 'MARKDOWN') {
        isMarkdown.value = true
        
        // 处理 Markdown 版本数据
        if (data.versions && typeof data.versions === 'object') {
          textVersions.value = data.versions
          // 设置可用版本列表（用于显示按钮）
          availableVersions.value = Object.keys(data.versions).sort((a, b) => {
            // 按优先级排序：original > cet4 > cet6 > academic > chinese_parallel
            const order = { 'original': 0, 'cet4': 1, 'cet6': 2, 'academic': 3, 'chinese_parallel': 4 }
            return (order[a] || 999) - (order[b] || 999)
          })
          // 默认选择原始版本
          currentVersion.value = 'original'
          const originalMarkdown = textVersions.value['original'] || data.markdownContent || ''
          // 渲染 Markdown 并添加点击功能
          let renderedHtml = DOMPurify.sanitize(marked(originalMarkdown), SANITIZE_CONFIG)
          renderedHtml = addClickableWordsToHtml(renderedHtml)
          textContent.value = renderedHtml
        } else {
          // 备用方案：如果没有版本数据，直接使用 markdownContent
          const markdownContent = data.markdownContent || ''
          textVersions.value = { 'original': markdownContent }
          availableVersions.value = ['original']
          currentVersion.value = 'original'
          let renderedHtml = DOMPurify.sanitize(marked(markdownContent), SANITIZE_CONFIG)
          renderedHtml = addClickableWordsToHtml(renderedHtml)
          textContent.value = renderedHtml
        }
      } else {
        // 纯文本格式，获取所有课文版本
        isMarkdown.value = false
        if (data.versions && typeof data.versions === 'object') {
          textVersions.value = data.versions
          // 设置可用版本列表（用于显示按钮）
          availableVersions.value = Object.keys(data.versions).sort((a, b) => {
            // 按优先级排序：original > cet4 > cet6 > academic > chinese_parallel
            const order = { 'original': 0, 'cet4': 1, 'cet6': 2, 'academic': 3, 'chinese_parallel': 4 }
            return (order[a] || 999) - (order[b] || 999)
          })
          // 默认选择原始版本
          currentVersion.value = 'original'
          textContent.value = normalizeTextContent(textVersions.value['original'] || data.content)
        } else {
          // 备用方案：如果没有版本数据，直接使用 content
          const rawContent = data.content
          textVersions.value = { 'original': rawContent }
          availableVersions.value = ['original']
          currentVersion.value = 'original'
          textContent.value = normalizeTextContent(rawContent)
        }
      }

      // 如果是从任务页面跳转来的，显示任务信息
      if (fromSource.value === 'assignment' && assignmentId.value) {
        ElMessage.info('正在完成任务中的课文学习')
      }

      // 显示可用版本提示
      if (availableVersions.value.length > 1) {
        const versionNames = availableVersions.value.map(v => getVersionLabel(v)).join('、')
        ElMessage.success(`本课文有多个学习版本：${versionNames}，可点击切换`)
      }

      // 恢复已学习时长（转换为秒）
      if (data.readTime && data.readTime > 0) {
        studyTime.value = data.readTime
        accumulatedTime = data.readTime // 将已学习时长作为累计时间
      } else {
        // 确保初始值为0
        studyTime.value = 0
        accumulatedTime = 0
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

const startReading = async () => {
  try {
    const response = await studentApi.startReading(textId.value, assignmentId.value)
    if (response.code === 200) {
      currentRecordId.value = response.data
      recording.value = true
      // 重新设置开始时间（用于计算当前会话时长）
      studyStartTime = Date.now()
      startStudyTimer()
    }
  } catch (error) {
    console.error('开始阅读失败:', error)
  }
}

// 自动开始阅读方法
const autoStartReading = async () => {
  // 如果已经在学习中，不重复开始
  if (recording.value) return
  
  // 如果学习时长已经满足要求，不自动开始
  if (isCompleted.value) return
  
  // 自动开始阅读
  await startReading()
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

  // 保存当前学习时长
  await saveLearningRecord()

  ElMessage.info('暂停阅读计时')
}

const saveLearningRecord = async () => {
  try {
    if (studyTime.value > 0) {
      // 计算这次要保存的时间增量
      // 这里我们保存总时间，让后端处理增量逻辑
      const recordPayload = {
        type: 'TEXT_VIEW',
        duration: studyTime.value, // 保存总学习时间
        textId: textId.value,
        // 如果是从任务来的，包含任务ID
        ...(assignmentId.value && { assignmentId: assignmentId.value })
      }

      await studentApi.recordLearning(recordPayload)
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

// 浮动翻译按钮拖动
let floatingBtnDragHandlers = null

const startTranslateBtnDrag = (event) => {
  // 支持鼠标和触摸
  const isTouch = event.type === 'touchstart'
  if (!isTouch && event.button !== 0) return // 鼠标只响应左键

  const startX = isTouch ? event.touches[0].clientX : event.clientX
  const startY = isTouch ? event.touches[0].clientY : event.clientY
  const startTime = Date.now()
  let hasMoved = false

  isTranslateBtnDragging.value = true

  translateBtnDragOffset.x = startX - floatingBtnPosition.x
  translateBtnDragOffset.y = startY - floatingBtnPosition.y

  if (isTouch) {
    event.preventDefault()
  }

  const handleMove = (e) => {
    if (isTranslateBtnDragging.value) {
      const moveX = e.type === 'touchmove' ? e.touches[0].clientX : e.clientX
      const moveY = e.type === 'touchmove' ? e.touches[0].clientY : e.clientY

      // 如果移动距离超过5px，认为是拖动
      const deltaX = Math.abs(moveX - startX)
      const deltaY = Math.abs(moveY - startY)
      if (deltaX > 5 || deltaY > 5) {
        hasMoved = true
      }

      floatingBtnPosition.x = moveX - translateBtnDragOffset.x
      floatingBtnPosition.y = moveY - translateBtnDragOffset.y
    }
  }

  const handleEnd = (e) => {
    const endTime = Date.now()
    const duration = endTime - startTime

    // 移除事件监听
    document.removeEventListener('mousemove', handleMove)
    document.removeEventListener('mouseup', handleEnd)
    document.removeEventListener('touchmove', handleMove)
    document.removeEventListener('touchend', handleEnd)

    // 如果没有移动，且时间很短（<300ms），认为是点击
    if (!hasMoved && duration < 300) {
      isTranslateBtnDragging.value = false
      // 触发点击事件
      handleFloatingBtnClick(e)
    } else {
      // 延迟重置，避免立即触发点击
      setTimeout(() => {
        isTranslateBtnDragging.value = false
      }, 100)
    }
  }

  document.addEventListener('mousemove', handleMove)
  document.addEventListener('mouseup', handleEnd)
  document.addEventListener('touchmove', handleMove, { passive: false })
  document.addEventListener('touchend', handleEnd)
  floatingBtnDragHandlers = { move: handleMove, end: handleEnd }
}

// 处理浮动按钮点击
const handleFloatingBtnClick = (event) => {
  // 如果刚拖动过，不触发点击
  if (isTranslateBtnDragging.value) {
    return
  }
  
  // 获取当前选中的文本
  const selection = window.getSelection()
  if (!selection) {
    ElMessage.warning('请先选择要翻译的文本')
    return
  }
  const text = selection.toString().trim()
  
  if (text && text.length > 0) {
    // 如果已经选中了文本，直接翻译
    selectedTextForTranslate.value = text
    translateSelectedText()
  } else {
    // 如果没有选中文本，提示用户
    ElMessage.warning('请先选择要翻译的文本')
  }
}

// 翻译选中的文本
const translateSelectedText = async () => {
  if (!selectedTextForTranslate.value) return

  showTranslateDialog.value = true
  translating.value = true
  translatedText.value = ''

  try {
    const authHeaderValue = getAuthHeader()
    const response = await fetch('/api/translate/auto', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        ...(authHeaderValue ? { 'Authorization': authHeaderValue } : {})
      },
      body: JSON.stringify({
        text: selectedTextForTranslate.value
      })
    })

    if (response.ok) {
      const result = await response.json()
      if (result.code === 200) {
        translatedText.value = typeof result.data === 'string' ? result.data : '翻译失败'
        ElMessage.success('翻译成功')
      } else {
        ElMessage.error('翻译失败，请稍后重试')
        translatedText.value = '翻译失败，请稍后重试'
      }
    } else {
      throw new Error('网络请求失败')
    }
  } catch (error) {
    console.error('翻译失败:', error)
    ElMessage.error('翻译失败')
    translatedText.value = '翻译服务暂时不可用'
  } finally {
    translating.value = false
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

const handleCompleteReading = async () => {
  try {
    completing.value = true

    if (currentRecordId.value) {
      // 正常路径：通过 recordId 完成阅读会话
      const response = await studentApi.completeReading(currentRecordId.value)
      if (response.code === 200) {
        isCompleted.value = true
        recording.value = false
        stopStudyTimer()
        ElMessage.success('恭喜！阅读已标记为完成')
      } else {
        ElMessage.error('完成阅读失败')
        return
      }
    } else {
      // 容错路径：直接调用 markTextCompleted 接口
      const response = await studentApi.markTextCompleted(textId.value, assignmentId.value)
      if (response.code === 200) {
        isCompleted.value = true
        recording.value = false
        stopStudyTimer()
        ElMessage.success('恭喜！阅读已标记为完成')
      } else {
        ElMessage.error('完成阅读失败')
        return
      }
    }

    // 如果是从任务页面来的，可以考虑返回
    if (fromSource.value === 'assignment') {
      setTimeout(() => {
        ElMessageBox.confirm('任务已完成，是否返回任务列表？', '阅读完成', {
          confirmButtonText: '确定',
          cancelButtonText: '留在此页',
          type: 'success'
        }).then(() => {
          router.push('/student/assignments')
        })
      }, 500)
    }
  } catch (error) {
    console.error('完成阅读失败:', error)
    ElMessage.error('完成阅读失败')
  } finally {
    completing.value = false
  }
}

const markAsCompleted = async () => {
  await handleCompleteReading()
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

const formatTotalTime = (seconds) => {
  return formatStudyTime(seconds)
}

const formatRemainingTime = () => {
  return '0分0秒'
}

// 处理习题提交
const handleExerciseSubmit = (answerData) => {
  // 更新对应习题的状态
  const exercise = exercises.value.find(ex => ex.id === answerData.exerciseId)
  if (exercise) {
    exercise.answered = true
    exercise.correct = answerData.isCorrect
    exercise.userAnswer = answerData.answer
    exercise.timeSpent = answerData.timeSpent
    exercise.attemptCount = (exercise.attemptCount || 0) + 1
  }

  // 显示提交结果反馈
  if (answerData.isCorrect) {
    ElMessage.success('回答正确！')
  } else {
    ElMessage.info('继续努力！')
  }

  // 检查是否所有习题都已完成
  checkExerciseCompletion()
}

// 检查习题完成状态
const checkExerciseCompletion = () => {
  if (exercises.value.length === 0) return

  const answeredCount = exercises.value.filter(ex => ex.answered).length
  completedExercises.value = answeredCount

  if (answeredCount === exercises.value.length) {
    allExercisesCompleted.value = true

    // 计算正确率
    const correctCount = exercises.value.filter(ex => ex.correct).length
    exerciseAccuracy.value = Math.round((correctCount / exercises.value.length) * 100)

    // 计算总用时
    totalExerciseTime.value = exercises.value.reduce((total, ex) => total + (ex.timeSpent || 0), 0)

    ElMessage({
      message: `恭喜！你已完成所有${exercises.value.length}道习题，正确率${exerciseAccuracy.value}%`,
      type: 'success',
      duration: 3000
    })
  }
}

const goBack = () => {
  // 检查来源，智能返回
  const from = route.query.from
  if (from === 'assignment') {
    // 从任务页面跳转来的，返回任务页面
    router.push('/student/assignments')
  } else {
    // 其他情况，使用默认返回
    router.go(-1)
  }
}

// 为 HTML 内容中的英文单词添加点击功能
// 注意：此函数的输入必须是已经过 DOMPurify.sanitize() 处理的 HTML
const addClickableWordsToHtml = (sanitizedHtml) => {
  // 使用正则表达式为英文单词添加可点击的 span 标签
  // 避免处理 HTML 标签内的内容
  const withSpans = sanitizedHtml.replace(/(<[^>]+>)|(\b[a-zA-Z]{2,}\b)/g, (match, tag, word) => {
    if (tag) {
      // 如果是 HTML 标签，保持不变
      return tag
    } else if (word) {
      // 如果是单词，添加可点击的 span（word 为纯字母，encodeURIComponent 防止属性注入）
      return `<span class="clickable-word" data-word="${encodeURIComponent(word.toLowerCase())}">${word}</span>`
    }
    return match
  })
  // 再次净化，防止正则替换引入意外的不安全内容
  return DOMPurify.sanitize(withSpans, SANITIZE_CONFIG)
}

// 处理 Markdown 内容的点击事件（事件委托）
const handleMarkdownClick = (event) => {
  const target = event.target
  
  // 检查点击的是否是图片
  if (target.tagName === 'IMG') {
    handleImageClick(target)
    return
  }
  
  // 检查点击的是否是可点击的单词
  if (target.classList.contains('clickable-word')) {
    const word = target.getAttribute('data-word')
    if (word) {
      handleMarkdownWordClick(word)
    }
  }
}

// 处理图片点击
const handleImageClick = (imgElement) => {
  previewImageSrc.value = imgElement.src
  previewImageAlt.value = imgElement.alt || '图片预览'
  imageLoading.value = true
  imageError.value = false
  showImagePreview.value = true
  resetImageTransform()
}

// 重置图片变换
const resetImageTransform = () => {
  imageScale.value = 1
  imageTranslateX.value = 0
  imageTranslateY.value = 0
  isDragging.value = false
}

// 图片缩放
const zoomIn = () => {
  imageScale.value = Math.min(imageScale.value * 1.2, 5)
}

const zoomOut = () => {
  imageScale.value = Math.max(imageScale.value / 1.2, 0.1)
}

const resetZoom = () => {
  imageScale.value = 1
  imageTranslateX.value = 0
  imageTranslateY.value = 0
}

// 鼠标滚轮缩放
const handleWheel = (event) => {
  event.preventDefault()
  const delta = event.deltaY > 0 ? 0.9 : 1.1
  imageScale.value = Math.max(0.1, Math.min(5, imageScale.value * delta))
}

// 开始拖拽
const startDrag = (event) => {
  if (imageScale.value <= 1) return
  
  isDragging.value = true
  dragStartX.value = event.clientX
  dragStartY.value = event.clientY
  dragStartTranslateX.value = imageTranslateX.value
  dragStartTranslateY.value = imageTranslateY.value
  
  document.addEventListener('mousemove', onDrag)
  document.addEventListener('mouseup', endDrag)
}

// 拖拽中
const onDrag = (event) => {
  if (!isDragging.value) return
  
  const deltaX = event.clientX - dragStartX.value
  const deltaY = event.clientY - dragStartY.value
  
  imageTranslateX.value = dragStartTranslateX.value + deltaX
  imageTranslateY.value = dragStartTranslateY.value + deltaY
}

// 结束拖拽
const endDrag = () => {
  isDragging.value = false
  document.removeEventListener('mousemove', onDrag)
  document.removeEventListener('mouseup', endDrag)
}

// 双击重置
const handleDoubleClick = () => {
  if (imageScale.value === 1) {
    imageScale.value = 2
  } else {
    resetZoom()
  }
}

// 图片加载完成
const onImageLoad = () => {
  imageLoading.value = false
  imageError.value = false
}

// 图片加载失败
const onImageError = () => {
  imageLoading.value = false
  imageError.value = true
}

// 播放单词音频
let currentAudio = null
const playWordAudio = () => {
  if (selectedWordInfo.value) {
    const audioUrl = selectedWordInfo.value.audioUrl || selectedWordInfo.value.audio
    // 检查audioUrl非空且为字符串类型
    if (audioUrl && typeof audioUrl === 'string' && audioUrl.trim()) {
      if (currentAudio) {
        currentAudio.pause()
        currentAudio = null
      }
      currentAudio = new Audio(audioUrl)
      currentAudio.play().catch(err => {
        console.error('播放音频失败:', err)
        ElMessage.error('播放音频失败')
      })
    }
  }
}

// 收藏单词到生词本
const handleAddToVocabulary = async () => {
  if (!selectedWordInfo.value) {
    ElMessage.warning('单词信息正在加载中，请稍后再试')
    return
  }
  
  try {
    addingToVocabulary.value = true
    
    // 确保从单词详情或原始点击中获取正确的单词文本
    const wordText = selectedWordInfo.value.word || selectedWord.value?.word || selectedWord.value?.cleanWord
    
    if (!wordText) {
      ElMessage.error('无法确定要收藏的单词')
      return
    }

    const response = await studentApi.addWordToVocabulary({
      word: wordText,
      unitId: unitId.value, // 发送当前单元ID，便于后端关联
      definition: selectedWordInfo.value.translation || selectedWordInfo.value.definition || '暂无释义',
      pronunciation: selectedWordInfo.value.phonetic || selectedWordInfo.value.pronunciation || '',
      exampleSentence: selectedWordInfo.value.exampleSentence || selectedWordInfo.value.examples || '',
      audioUrl: selectedWordInfo.value.audioUrl || selectedWordInfo.value.audio || ''
    })

    if (response.code === 200) {
      ElMessage.success('已添加到生词本')
    } else {
      ElMessage.warning('添加失败')
    }
  } catch (error) {
    console.error('添加生词失败:', error)
    ElMessage.error('添加生词失败，请稍后重试')
  } finally {
    addingToVocabulary.value = false
  }
}

// 关闭图片预览
const closeImagePreview = () => {
  showImagePreview.value = false
  if (imagePreviewCleanupTimerId) clearTimeout(imagePreviewCleanupTimerId)
  imagePreviewCleanupTimerId = setTimeout(() => {
    previewImageSrc.value = ''
    previewImageAlt.value = ''
    imageLoading.value = false
    imageError.value = false
    resetImageTransform()
    imagePreviewCleanupTimerId = null
  }, 300)
}

// 监听内容变化重新应用划线
import { watch } from 'vue'
watch(textContent, () => {
  if (textContentWatchTimerId) clearTimeout(textContentWatchTimerId)
  textContentWatchTimerId = setTimeout(() => {
    applySharedUnderlines()
    textContentWatchTimerId = null
  }, 500)
})

// 处理 Markdown 中的单词点击
const handleMarkdownWordClick = async (word) => {
  // 显示加载状态
  showWordDialog.value = true
  loadingWord.value = true
  selectedWord.value = { cleanWord: word.toLowerCase(), word: word }
  selectedWordInfo.value = null

  try {
    // 使用 WordNet API 获取单词详细信息
    const authHeaderValue = getAuthHeader()
    const response = await fetch(`/api/wordnet/word/${word.toLowerCase()}`, {
      headers: {
        ...(authHeaderValue ? { 'Authorization': authHeaderValue } : {}),
        'Content-Type': 'application/json'
      }
    })

    if (response.ok) {
      const result = await response.json()
      if (result.code === 200) {
        selectedWordInfo.value = result.data
      } else {
        selectedWordInfo.value = {
          word: word.toLowerCase(),
          definition: "未找到该单词",
          found: false
        }
      }
    } else {
      selectedWordInfo.value = {
        word: word.toLowerCase(),
        definition: "网络请求失败，请稍后重试",
        found: false
      }
    }
  } catch (error) {
    console.error('获取单词信息失败:', error)
    selectedWordInfo.value = {
      word: word.toLowerCase(),
      definition: "服务暂时不可用，请稍后重试",
      found: false
    }
  } finally {
    loadingWord.value = false
  }
}

// 版本管理方法
const switchVersion = (version) => {
  currentVersion.value = version
  const versionContent = textVersions.value[version] || ''
  
  if (isMarkdown.value) {
    // Markdown 格式，需要渲染
    let renderedHtml = DOMPurify.sanitize(marked(versionContent), SANITIZE_CONFIG)
    renderedHtml = addClickableWordsToHtml(renderedHtml)
    textContent.value = renderedHtml
  } else {
    // 纯文本格式，直接显示（应用规范化去除转义残留）
    textContent.value = normalizeTextContent(versionContent)
  }
  
  // 切换版本后重新加载划线
  if (versionSwitchTimerId) clearTimeout(versionSwitchTimerId)
  versionSwitchTimerId = setTimeout(() => {
    applySharedUnderlines()
    versionSwitchTimerId = null
  }, 300)

  ElMessage.info(`已切换到${getVersionLabel(version)}版本`)
}

const getVersionLabel = (version) => {
  const labels = {
    'original': '原始版本',
    'cet4': 'CET-4版本',
    'cet6': 'CET-6版本',
    'academic': '学术版本',
    'chinese_parallel': '中英对照'
  }
  return labels[version] || version
}

// 滚动处理函数
const handleScroll = () => {
  const currentScrollY = window.scrollY
  
  // 向下滚动超过100px时隐藏头部
  if (currentScrollY > lastScrollY.value && currentScrollY > 100) {
    headerHidden.value = true
  }
  // 向上滚动时显示头部，或者滚动到顶部时也显示
  else if (currentScrollY < lastScrollY.value || currentScrollY <= 50) {
    headerHidden.value = false
  }
  
  lastScrollY.value = currentScrollY
}

// 生命周期
onMounted(async () => {
  await loadTextDetail()
  // 添加页面可见性变化监听
  document.addEventListener('visibilitychange', handleVisibilityChange)
  // 添加滚动监听
  window.addEventListener('scroll', handleScroll, { passive: true })
  // 添加窗口大小变化监听
  window.addEventListener('resize', handleWindowResize)
  // 默认启用全屏模式
  document.body.classList.add('fullscreen-reading')

  // 页面加载完成后自动开始阅读
  autoStartTimerId = setTimeout(() => {
    autoStartReading()
  }, 1000) // 延迟1秒确保页面完全加载

  // 监听文本选择
  document.addEventListener('selectionchange', handleSelectionChange)
  // 获取共享划线
  fetchSharedUnderlines()
})

onBeforeUnmount(() => {
  // 移除事件监听
  document.removeEventListener('visibilitychange', handleVisibilityChange);
  window.removeEventListener('scroll', handleScroll);
  document.removeEventListener('selectionchange', handleSelectionChange);
  window.removeEventListener('resize', handleWindowResize);

  // 防御性清理：如果组件在拖拽过程中卸载，确保 document 级监听器被移除
  document.removeEventListener('mousemove', onDrag);
  document.removeEventListener('mouseup', endDrag);
  // 清理浮动按钮拖拽事件监听
  if (floatingBtnDragHandlers) {
    document.removeEventListener('mousemove', floatingBtnDragHandlers.move);
    document.removeEventListener('mouseup', floatingBtnDragHandlers.end);
    document.removeEventListener('touchmove', floatingBtnDragHandlers.move);
    document.removeEventListener('touchend', floatingBtnDragHandlers.end);
    floatingBtnDragHandlers = null
  }

  // 清理图片预览缩放拖拽事件监听（如果组件在拖拽图片时卸载）
  if (isDragging.value) {
    document.removeEventListener('mousemove', onDrag);
    document.removeEventListener('mouseup', endDrag);
    isDragging.value = false;
  }

  // 清理定时器
  if (autoStartTimerId) {
    clearTimeout(autoStartTimerId)
    autoStartTimerId = null
  }
  if (textContentWatchTimerId) {
    clearTimeout(textContentWatchTimerId)
    textContentWatchTimerId = null
  }
  if (imagePreviewCleanupTimerId) {
    clearTimeout(imagePreviewCleanupTimerId)
    imagePreviewCleanupTimerId = null
  }
  if (versionSwitchTimerId) {
    clearTimeout(versionSwitchTimerId)
    versionSwitchTimerId = null
  }
  if (selectionMenuHideTimerId) {
    clearTimeout(selectionMenuHideTimerId)
    selectionMenuHideTimerId = null
  }
  if (fetchUnderlineTimerId) {
    clearTimeout(fetchUnderlineTimerId)
    fetchUnderlineTimerId = null
  }

  // 退出全屏模式
  document.body.classList.remove('fullscreen-reading')

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
    // 使用 fetch keepalive 确保页面卸载时请求可靠发送
    const keepaliveAuth = getAuthHeader()
    const payload = {
      type: 'TEXT_VIEW',
      duration: studyTime.value,
      textId: textId.value,
      ...(assignmentId.value && { assignmentId: assignmentId.value })
    }
    const baseUrl = import.meta.env.VITE_API_BASE_URL || ''
    fetch(`${baseUrl}/api/student/learning/record/learning`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        ...(keepaliveAuth ? { 'Authorization': keepaliveAuth } : {})
      },
      body: JSON.stringify(payload),
      keepalive: true
    }).catch(() => {})
  }
  stopStudyTimer()
  // 清理音频资源
  if (currentAudio) {
    currentAudio.pause()
    currentAudio.onended = null
    currentAudio.onerror = null
    currentAudio = null
  }
})

// 处理页面可见性变化（切换标签页时自动暂停）
const handleVisibilityChange = () => {
  if (document.hidden && recording.value) {
    // 页面隐藏时自动暂停学习并保存
    pauseReading()
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
// 处理文本选择
const handleSelectionChange = () => {
  if (isTranslateBtnDragging.value) return

  const selection = window.getSelection()
  if (!selection || !selection.rangeCount || selection.isCollapsed) {
    // Delay hiding to allow menu click events to fire first
    if (selectionMenuHideTimerId) clearTimeout(selectionMenuHideTimerId)
    selectionMenuHideTimerId = setTimeout(() => {
      const sel = window.getSelection()
      if (!sel || !sel.rangeCount || sel.isCollapsed) {
        showSelectionMenu.value = false
      }
      selectionMenuHideTimerId = null
    }, 200)
    return
  }

  const text = selection.toString().trim()
  if (text) {
    // 检查选择是否在文本内容区域内 (class="article-body")
    const anchorNode = selection.anchorNode
    const focusNode = selection.focusNode
    const articleBody = document.querySelector('.article-body')
    
    if (articleBody && (articleBody.contains(anchorNode) || articleBody.contains(focusNode))) {
      selectedTextContent.value = text
      const range = selection.getRangeAt(0)
      const rect = range.getBoundingClientRect()
      
      selectionPosition.x = rect.left + rect.width / 2
      selectionPosition.y = rect.top - 60 // 显示在上方
      showSelectionMenu.value = true
    } else {
       showSelectionMenu.value = false
    }
  } else {
    showSelectionMenu.value = false
  }
}

const handleCopy = () => {
  navigator.clipboard.writeText(selectedTextContent.value)
  ElMessage.success('已复制到剪贴板')
  showSelectionMenu.value = false
  window.getSelection().removeAllRanges()
}

const handleUnderline = async () => {
  if (!selectedTextContent.value) return

  // 检查当前选区是否已经划线，若已划线则执行取消操作
  const trimmed = selectedTextContent.value.trim()
  const existingUnderline = sharedUnderlines.value.find(u => u.selectedText && u.selectedText.trim() === trimmed)

  if (existingUnderline) {
    // 取消划线：删除后端记录，移除前端高亮，更新本地状态
    try {
      await deleteUnderline(existingUnderline.id)
      // 从本地列表中移除
      sharedUnderlines.value = sharedUnderlines.value.filter(u => u.id !== existingUnderline.id)
      // 重新渲染划线（先清除所有高亮，再重新应用剩余划线）
      removeAllHighlights()
      applySharedUnderlines()
      ElMessage.success('已取消划线')
    } catch (error) {
      console.error('取消划线失败:', error)
      ElMessage.error('取消划线失败')
    } finally {
      showSelectionMenu.value = false
      window.getSelection().removeAllRanges()
    }
    return
  }

  try {
    // 使用 Rangy 序列化选区，以 article-body 为根节点以增强稳定性
    let serializedRange = null
    let startIndex = 0
    let endIndex = 0

    const articleBody = document.querySelector('.article-body')
    const selection = rangy.getSelection()
    if (selection.rangeCount > 0) {
      try {
        serializedRange = rangy.serializeSelection(selection, true, articleBody || undefined)
      } catch (e) {
        console.error('Rangy serialization failed:', e)
      }

      // 计算字符偏移量，用于文本搜索回退
      try {
        const nativeSelection = window.getSelection()
        if (nativeSelection && nativeSelection.rangeCount > 0 && articleBody) {
          const range = nativeSelection.getRangeAt(0)
          const preRange = document.createRange()
          preRange.setStart(articleBody, 0)
          preRange.setEnd(range.startContainer, range.startOffset)
          startIndex = preRange.toString().length
          endIndex = startIndex + selectedTextContent.value.length
        }
      } catch (e) {
        console.error('Failed to compute character offset:', e)
      }
    }

    // 立即应用高亮样式 (Optimistic UI)
    const userHighlighter = rangy.createClassApplier('user-underline', {
      ignoreWhiteSpace: true,
      tagNames: ['span', 'mark']
    })
    userHighlighter.applyToSelection()

    const res = await createUnderline({
      textId: textId.value,
      selectedText: selectedTextContent.value,
      startIndex,
      endIndex,
      serializedRange
    })

    if (res && res.code === 200) {
      // 重新从后端拉取划线列表，以获取新记录的 id（用于后续取消划线）
      await fetchSharedUnderlines()
      ElMessage.success('划线成功')
    } else {
      ElMessage.success('划线成功')
    }
    showSelectionMenu.value = false
    window.getSelection().removeAllRanges()
  } catch (error) {
    console.error(error)
    ElMessage.error('划线失败')
  }
}

const handleShare = () => {
  ElMessage.info('分享功能即将上线')
}

const handleAiQa = () => {
  // 获取上下文 (前后 100 字符)
  let context = ''
  try {
    const selection = window.getSelection()
    if (selection && selection.rangeCount > 0) {
      const range = selection.getRangeAt(0)
      const container = range.commonAncestorContainer
      const fullText = container.textContent || ''
      // 简单截取，实际可能需要更复杂的逻辑
      context = fullText.substring(Math.max(0, range.startOffset - 100), Math.min(fullText.length, range.endOffset + 100))
    }
  } catch (e) {
    console.error('Failed to get context:', e)
  }

  router.push({
    path: '/student/ai-qa',
    query: {
      textId: textId.value,
      text: selectedTextContent.value,
      context: context,
      articleTitle: textTitle.value
    }
  })
}

const loadVocabularyStats = async () => {
  try {
    const response = await getVocabularyStats()
    if (response.code === 200) {
      vocabularyStats.value = response.data
    }
  } catch (error) {
    console.error('获取生词本统计失败:', error)
  }
}

const fetchSharedUnderlines = async () => {
  try {
    if(!textId.value) return
    const res = await getUnderlines(textId.value)
    if (res.code === 200) {
      sharedUnderlines.value = res.data

      // 在下一次 DOM 更新后应用划线
      if (fetchUnderlineTimerId) clearTimeout(fetchUnderlineTimerId)
      fetchUnderlineTimerId = setTimeout(() => {
        applySharedUnderlines()
        fetchUnderlineTimerId = null
      }, 500)
    }
  } catch(e) {
    console.error(e)
  }
}

/**
 * 移除文章区域内所有由 Rangy 添加的高亮 class span，
 * 用于取消划线后重新渲染剩余划线。
 */
const removeAllHighlights = () => {
  const articleBody = document.querySelector('.article-body')
  if (!articleBody) return

  try {
    rangy.init()
    const classNames = ['user-underline', 'shared-underline']
    classNames.forEach(cls => {
      const applier = rangy.createClassApplier(cls, {
        ignoreWhiteSpace: true,
        tagNames: ['span', 'mark']
      })
      // undoToRange 针对整个 article-body 所有选区移除高亮
      const allRange = rangy.createRange()
      allRange.selectNodeContents(articleBody)
      const sel = rangy.getSelection()
      sel.setSingleRange(allRange)
      applier.undoToSelection()
      sel.removeAllRanges()
    })
  } catch (e) {
    console.error('removeAllHighlights failed:', e)
  }
}

const applySharedUnderlines = () => {
  if (!sharedUnderlines.value || sharedUnderlines.value.length === 0) return

  const articleBody = document.querySelector('.article-body')
  if (!articleBody) return

  // 初始化 Rangy
  rangy.init()
  const sharedApplier = rangy.createClassApplier('shared-underline', {
    ignoreWhiteSpace: true,
    tagNames: ['span', 'mark']
  })

  sharedUnderlines.value.forEach(underline => {
    let applied = false

    // 方法一：Rangy 序列化范围恢复（以 article-body 为根节点）
    if (underline.serializedRange) {
      try {
        rangy.deserializeSelection(underline.serializedRange, articleBody)
        sharedApplier.applyToSelection()
        window.getSelection().removeAllRanges()
        applied = true
      } catch (e) {
        console.error('Rangy deserialization failed, trying fallback:', e)
      }
    }

    // 方法二：字符偏移量 + 文本搜索回退
    if (!applied && underline.selectedText) {
      applied = applyHighlightByOffset(
        articleBody,
        underline.selectedText,
        underline.startIndex,
        'shared-underline'
      )
    }
  })
}

// 通过字符偏移量和文本搜索定位高亮范围（跨 span 边界安全）
const applyHighlightByOffset = (container, searchText, startOffset, highlightClass) => {
  if (!searchText || !container) return false

  try {
    // 收集所有文本节点
    const walker = document.createTreeWalker(container, NodeFilter.SHOW_TEXT, null)
    const textNodes = []
    let node
    let totalLen = 0
    while ((node = walker.nextNode())) {
      textNodes.push({ node, start: totalLen, len: node.textContent.length })
      totalLen += node.textContent.length
    }

    const fullText = textNodes.map(n => n.node.textContent).join('')

    // 定位搜索文本（优先使用 startOffset，否则从头搜索）
    let idx = -1
    if (startOffset !== undefined && startOffset !== null && startOffset >= 0) {
      // 先在 startOffset 附近精确匹配
      const near = fullText.indexOf(searchText, Math.max(0, startOffset - 5))
      if (near !== -1 && Math.abs(near - startOffset) <= 20) {
        idx = near
      }
    }
    if (idx === -1) {
      idx = fullText.indexOf(searchText)
    }
    if (idx === -1) return false

    const endIdx = idx + searchText.length

    // 找起始文本节点和偏移
    const findNodeAt = (charPos) => {
      for (const t of textNodes) {
        if (charPos >= t.start && charPos <= t.start + t.len) {
          return { node: t.node, offset: charPos - t.start }
        }
      }
      return null
    }

    const startPos = findNodeAt(idx)
    const endPos = findNodeAt(endIdx)
    if (!startPos || !endPos) return false

    const range = document.createRange()
    range.setStart(startPos.node, startPos.offset)
    range.setEnd(endPos.node, endPos.offset)

    // 使用 Rangy ClassApplier 以安全处理跨元素边界
    rangy.init()
    const sel = rangy.getSelection()
    sel.setSingleRange(range)
    const applier = rangy.createClassApplier(highlightClass, {
      ignoreWhiteSpace: true,
      tagNames: ['span', 'mark']
    })
    applier.applyToSelection()
    window.getSelection().removeAllRanges()
    return true
  } catch (e) {
    console.error('applyHighlightByOffset failed:', e)
    return false
  }
}
</script>

<style scoped>
/* Premium Theme Variables */
:root {
  --primary-color: #197fe6;
  --bg-soft: #f6f7f8;
  --text-main: #1e293b;
  --text-muted: #64748b;
  --glass-bg: rgba(255, 255, 255, 0.7);
  --border-glass: rgba(25, 127, 230, 0.1);
}

.premium-reader {
  background-color: var(--bg-soft);
  min-height: 100vh;
  font-family: 'Inter', -apple-system, sans-serif;
  color: var(--text-main);
  padding-bottom: 80px;
}

/* Glassmorphic Header */
.glass-header {
  position: sticky;
  top: 0;
  z-index: 1000;
  background: var(--glass-bg);
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  border-bottom: 1px solid var(--border-glass);
  transition: transform 0.3s ease;
}

.header-hidden {
  transform: translateY(-100%);
}

.header-inner {
  max-width: 1200px;
  margin: 0 auto;
  padding: 0 24px;
  height: 64px;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.header-left, .header-right {
  display: flex;
  align-items: center;
  gap: 16px;
}

.minimal-back-btn {
  display: flex;
  align-items: center;
  gap: 8px;
  background: none;
  border: none;
  color: var(--text-muted);
  font-weight: 500;
  font-size: 14px;
  cursor: pointer;
  transition: color 0.2s;
}

.minimal-back-btn:hover {
  color: var(--primary-color);
}

.header-divider {
  width: 1px;
  height: 16px;
  background: #e2e8f0;
}

.header-title {
  font-size: 16px;
  font-weight: 700;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 300px;
}

/* Difficulty Pill */
.difficulty-pill {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 4px 12px;
  border-radius: 8px;
  font-weight: 700;
  font-size: 12px;
}

.difficulty-easy { background: rgba(82, 196, 26, 0.1); color: #52c41a; }
.difficulty-medium { background: rgba(250, 140, 22, 0.1); color: #fa8c16; }
.difficulty-hard { background: rgba(250, 84, 28, 0.1); color: #fa541c; }

.pill-label {
  text-transform: uppercase;
  font-size: 10px;
  letter-spacing: 0.05em;
  opacity: 0.8;
}

.favorite-btn {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  border: none;
  background: none;
  color: #cbd5e1;
  font-size: 20px;
  cursor: pointer;
  transition: all 0.2s;
  display: flex;
  align-items: center;
  justify-content: center;
}

.favorite-btn:hover {
  background: #f1f5f9;
  color: #f59e0b;
}

.favorite-btn.is-favorite {
  color: #f59e0b;
}

/* Reader Container */
.reader-container {
  max-width: 800px;
  margin: 0 auto;
  padding: 60px 24px;
}

.article-header {
  margin-bottom: 48px;
}

.article-meta {
  display: flex;
  align-items: center;
  gap: 12px;
  color: var(--text-muted);
  font-size: 14px;
  margin-bottom: 24px;
}

.meta-item {
  display: flex;
  align-items: center;
  gap: 6px;
}

.meta-dot {
  opacity: 0.3;
}

.article-title {
  font-size: 48px;
  font-weight: 800;
  line-height: 1.15;
  letter-spacing: -0.02em;
  margin-bottom: 32px;
  word-break: break-word;
  overflow-wrap: break-word;
}

/* Version Pill Selector */
.version-pill-selector {
  margin-top: 32px;
}

.pill-container {
  display: inline-flex;
  padding: 6px;
  background: rgba(226, 232, 240, 0.5);
  border-radius: 12px;
  gap: 4px;
}

.version-pill {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 16px;
  border: none;
  background: none;
  border-radius: 8px;
  color: var(--text-muted);
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s;
}

.version-pill:hover {
  background: rgba(255, 255, 255, 0.6);
}

.version-pill.active {
  background: white;
  color: var(--primary-color);
  font-weight: 600;
  box-shadow: 0 1px 3px rgba(0,0,0,0.1);
}

.version-pill-selector {
  margin-top: 32px;
  width: 100%;
}

/* Article Body Typography */
.article-body {
  font-size: 18px;
  line-height: 1.8;
  color: var(--text-main);
}

.article-body :deep(.interactive-text),
.markdown-content {
  background: transparent !important;
  padding: 0 !important;
  color: var(--text-main) !important;
  word-break: break-word;
  overflow-wrap: break-word;
}

.article-body :deep(img),
.markdown-content :deep(img) {
  max-width: 100% !important;
  height: auto !important;
  border-radius: 12px;
  margin: 24px 0;
  display: block;
}

/* Completion Footer */
.reader-footer {
  margin-top: 80px;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.footer-divider {
  width: 64px;
  height: 4px;
  background: #e2e8f0;
  border-radius: 2px;
  margin-bottom: 48px;
}

.premium-completion-btn {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 16px 40px;
  background: linear-gradient(135deg, var(--primary-color), #3b82f6);
  border: none;
  border-radius: 16px;
  color: white;
  font-size: 18px;
  font-weight: 700;
  cursor: pointer;
  transition: transform 0.2s, box-shadow 0.2s;
}

.premium-completion-btn:hover {
  transform: scale(1.02);
  box-shadow: 0 10px 25px -5px rgba(25, 127, 230, 0.4);
}

.premium-completion-btn:active {
  transform: scale(0.98);
}

.btn-content {
  display: flex;
  align-items: center;
  gap: 12px;
  z-index: 1;
}

.btn-glow {
  position: absolute;
  inset: 0;
  background: white;
  opacity: 0;
  border-radius: 16px;
  transition: opacity 0.2s;
}

.premium-completion-btn:hover .btn-glow {
  opacity: 0.1;
}

.completion-hint {
  margin-top: 16px;
  color: var(--text-muted);
  font-size: 14px;
}

/* Mobile Adjustments */
@media (max-width: 768px) {
  .article-title {
    font-size: 32px;
  }
  
  .reader-container {
    padding: 40px 20px;
  }
  
  .header-title {
    display: none;
  }
  
  .version-pill-selector {
    margin-top: 24px;
    width: 100%;
  }
  
  .pill-container {
    display: flex;
    flex-wrap: wrap;
    justify-content: center;
    gap: 8px;
    padding: 4px;
    background: transparent;
  }
  
  .version-pill {
    flex: 1 1 calc(33.33% - 8px); /* Try to fit 3 per row to keep it to 2 lines */
    max-width: calc(50% - 8px); /* But don't let them get too wide */
    min-width: calc(30% - 8px);
    justify-content: center;
    padding: 8px 10px;
    font-size: 12px;
    white-space: nowrap;
    background: rgba(226, 232, 240, 0.8);
    display: flex;
    align-items: center;
    gap: 4px;
  }
}

/* Markdown Content Styling Overrides */
.markdown-content :deep(h2) {
  font-size: 24px;
  font-weight: 700;
  margin: 48px 0 16px;
}

.markdown-content :deep(p) {
  margin-bottom: 24px;
}

.markdown-content :deep(strong) {
  color: var(--text-main);
  font-weight: 600;
}

/* Word Dialog Enhancements */
.word-title-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.word-title {
  margin: 0;
  font-size: 24px;
  font-weight: 800;
  color: #1e293b; /* text-main */
}

.pronunciation {
  margin-top: 4px;
  color: #197fe6; /* primary-color */
  font-family: 'Lucida Sans Unicode', sans-serif;
  font-size: 14px;
}

.section-title {
  font-size: 12px;
  text-transform: uppercase;
  letter-spacing: 0.1em;
  color: #64748b; /* text-muted */
  margin: 16px 0 8px;
  font-weight: 700;
}

.meaning-content {
  font-size: 16px;
  line-height: 1.6;
  color: #1e293b; /* text-main */
}

.example-item {
  font-size: 14px;
  line-height: 1.5;
  color: #64748b; /* text-muted */
  font-style: italic;
  padding: 8px 12px;
  background: rgba(226, 232, 240, 0.3);
  border-radius: 8px;
  border-left: 3px solid #197fe6; /* primary-color */
}

.dialog-footer {
  display: flex;
  justify-content: space-between;
  width: 100%;
}

.mobile-word-dialog :deep(.el-dialog__body) {
  padding-top: 10px;
}
/* Underline Styles */
:deep(.user-underline) {
  border-bottom: 2px solid #197fe6;
  background-color: rgba(25, 127, 230, 0.1);
}

:deep(.shared-underline) {
  border-bottom: 2px dashed #10b981; /* Green dashed for shared */
  background-color: rgba(16, 185, 129, 0.05);
}
</style>
