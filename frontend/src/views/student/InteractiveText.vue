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
          <div class="header-title-scroll" ref="headerTitleRef">
            <h2 class="header-title" :class="{ 'scrolling': isTitleOverflow }" :title="textTitle">{{ textTitle }}</h2>
          </div>
        </div>
        
        <div class="header-right">
          <div class="difficulty-pill" :class="difficultyClass">
            <span class="pill-label">难度</span>
            <span class="pill-value">{{ difficultyText }}</span>
          </div>
          <button class="annotation-header-btn" @click="showAnnotationSidebar = true" :title="`批注 (${annotations.length})`">
            <el-icon><Comment /></el-icon>
            <span v-if="annotations.length > 0" class="ann-header-count">{{ annotations.length }}</span>
          </button>
          <button v-if="canFavorite" class="favorite-btn" :class="{ 'is-favorite': isFavorite }" @click="addToFavorite">
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
        <div
          class="markdown-content"
          v-html="textContent"
          @click="handleMarkdownClick"
        ></div>
      </section>

      <!-- AI Quiz Section -->
      <section class="ai-quiz-section">
        <button
          class="ai-quiz-btn"
          @click="handleAiQuiz"
          :disabled="quizLoading"
          v-if="quizQuestions.length === 0"
        >
          <span class="btn-content" v-if="!quizLoading">
            <el-icon><Edit /></el-icon>
            AI 出题
          </span>
          <span class="btn-content" v-else>
            <el-icon class="is-loading"><Loading /></el-icon>
            正在生成题目...
          </span>
        </button>

        <!-- 出题内容（内嵌展示，不影响阅读）-->
        <div class="quiz-inline" v-if="quizQuestions.length > 0">
          <div class="quiz-inline-header">
            <span class="quiz-inline-title">
              <el-icon><Edit /></el-icon>
              AI 出题（{{ quizQuestions.length }} 题）
            </span>
            <div class="quiz-inline-actions-top">
              <el-button size="small" @click="closeQuiz">收起</el-button>
            </div>
          </div>

          <div class="quiz-content">
            <div
              v-for="(q, index) in quizQuestions"
              :key="index"
              class="quiz-question"
            >
              <div class="question-header">
                <span class="question-number">{{ index + 1 }}</span>
                <span class="question-type">{{ q.questionType || '选择题' }}</span>
              </div>
              <div class="question-text">{{ q.question }}</div>
              <div class="question-options">
                <div
                  v-for="opt in q.options"
                  :key="opt.optionKey"
                  class="option-item"
                  :class="{
                    'selected': q.userAnswer === opt.optionKey,
                    'correct': q.showAnswer && opt.isCorrect === 1,
                    'wrong': q.showAnswer && q.userAnswer === opt.optionKey && opt.isCorrect !== 1
                  }"
                  @click="selectQuizAnswer(index, opt.optionKey)"
                >
                  <span class="option-key">{{ opt.optionKey }}</span>
                  <span class="option-content">{{ opt.optionContent }}</span>
                </div>
              </div>
              <div class="question-analysis" v-if="q.showAnswer && q.analysis">
                <div class="analysis-label">解析：</div>
                <div class="analysis-text">{{ q.analysis }}</div>
              </div>
            </div>
          </div>

          <div class="quiz-actions">
            <el-button
              v-if="!allAnswered"
              type="primary"
              @click="submitQuiz"
              :disabled="!hasAnyAnswer"
            >
              提交答案
            </el-button>
            <el-button
              v-if="allAnswered"
              @click="resetQuizAnswers"
            >
              重新作答
            </el-button>
          </div>
        </div>
      </section>

      <!-- Related Articles Section -->
      <section class="related-articles" v-if="relatedTexts.length > 0">
        <h3 class="related-title">相关推荐</h3>
        <div class="related-cards">
          <article
            class="article-card"
            v-for="(item, idx) in relatedTexts"
            :key="item.id"
            @click="goToRelatedArticle(item.id)"
          >
            <div class="article-cover">
              <div class="cover-gradient"></div>
              <div
                class="cover-image"
                :style="{
                  background: item._gradient || relatedGradients[idx % relatedGradients.length],
                  backgroundSize: 'cover',
                  backgroundPosition: 'center'
                }"
              ></div>
              <div class="category-badge">
                <el-icon :size="14"><Reading /></el-icon>
                <span>文章</span>
              </div>
            </div>
            <div class="article-content">
              <h2 class="article-title">{{ item.title }}</h2>
              <p class="article-snippet">{{ item.wordCount }} words</p>
            </div>
            <div class="article-footer">
              <div class="difficulty-pill" :class="item.difficulty">
                <el-icon :size="14"><component :is="getDiffIcon(item.difficulty)" /></el-icon>
                <span>{{ getDiffText(item.difficulty) }}</span>
              </div>
              <div class="reading-time">
                <el-icon :size="16"><Timer /></el-icon>
                <span>{{ Math.ceil((item.wordCount || 0) / 100) }} min</span>
              </div>
            </div>
          </article>
        </div>
      </section>

      <!-- Completion Section -->
      <footer class="reader-footer" v-if="!isCompleted && !isPreviewMode">
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
      
      <div class="reader-footer completed-notif" v-else-if="isCompleted && !isPreviewMode">
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
      width="min(400px, 92vw)"
      class="translate-dialog"
    >
      <div class="translate-content" v-if="!translating">
        <div class="original-text">
          <h4>原文：</h4>
          <div class="text-scroll-area">
            <p>{{ selectedTextForTranslate }}</p>
          </div>
        </div>
        <div class="translated-text">
          <h4>译文：</h4>
          <div class="text-scroll-area">
            <p>{{ translatedText }}</p>
          </div>
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
      title="词汇详情"
      width="min(350px, 92vw)"
      class="word-dialog"
      align-center
    >
      <div class="word-content" v-if="selectedWordInfo && !loadingWord">
        <div class="word-header">
          <h2 class="word-title">{{ selectedWordInfo.word || '未知单词' }}</h2>
          <div class="pronunciation" v-if="selectedWordInfo.phonetic || selectedWordInfo.pronunciation">
            {{ selectedWordInfo.pronunciation || selectedWordInfo.phonetic }}
          </div>
        </div>

        <div class="word-body">
          <div class="main-meaning" v-if="selectedWordInfo.definition || selectedWordInfo.translation">
            <div class="meaning-content">
              {{ selectedWordInfo.definition || selectedWordInfo.translation }}
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
        <el-button
          type="primary"
          :loading="addingToVocabulary"
          :disabled="isInVocabulary"
          @click="handleAddToVocabulary"
          v-if="selectedWordInfo && selectedWordInfo.found !== false"
        >
          {{ isInVocabulary ? '已在生词本' : '添加到生词本' }}
        </el-button>
        <el-button @click="showWordDialog = false">关闭</el-button>
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

    <!-- 浮动翻译按钮（Markdown 模式下由父页面提供，样式与子组件一致） -->
    <div
      v-if="isMarkdown"
      class="floating-translate-btn"
      :class="{ 'selecting': isSelectingMode }"
      :style="{ top: floatingBtnPosition.y + 'px', left: floatingBtnPosition.x + 'px' }"
      @mousedown="startTranslateBtnDrag"
      @touchstart.prevent="startTranslateBtnDrag"
    >
      <el-tooltip :content="isSelectingMode ? '选择文本后点击翻译' : '点击开始翻译'" placement="left">
        <button
          class="translate-fab"
          :class="isSelectingMode ? 'fab-active' : 'fab-default'"
        >
          <span v-if="isSelectingMode" class="fab-icon">✓</span>
          <span v-else class="fab-icon">译</span>
          <span class="fab-label">{{ isSelectingMode ? '翻译' : '划词翻译' }}</span>
        </button>
      </el-tooltip>
    </div>

    <!-- 文本选择菜单 -->
    <SelectionMenu
      :visible="showSelectionMenu"
      :position="selectionPosition"
      :underline-label="selectionUnderlineLabel"
      @copy="handleCopy"
      @underline="handleUnderline"
      @annotate="handleAnnotate"
      @ai-qa="handleAiQa"
    />

    <!-- 批注输入弹窗 -->
    <el-dialog v-model="showAnnotationDialog" title="添加批注" width="min(480px, 92vw)" align-center>
      <div class="annotation-dialog-body">
        <div class="ann-selected-text">
          <span class="ann-quote-mark">"</span>{{ annotationSelectedText }}<span class="ann-quote-mark">"</span>
        </div>
        <el-input
          v-model="annotationNote"
          type="textarea"
          :rows="4"
          placeholder="写下你的批注..."
          maxlength="500"
          show-word-limit
        />
        <div class="ann-visibility-row">
          <span class="ann-visibility-label">可见性</span>
          <el-radio-group v-model="annotationVisibility" size="small">
            <el-radio-button :value="1">🌐 公开</el-radio-button>
            <el-radio-button :value="0">🔒 仅自己</el-radio-button>
          </el-radio-group>
        </div>
      </div>
      <template #footer>
        <el-button @click="showAnnotationDialog = false">取消</el-button>
        <el-button type="primary" :loading="submittingAnnotation" @click="submitAnnotation">发布批注</el-button>
      </template>
    </el-dialog>

    <!-- 批注侧边栏 -->
    <div class="annotation-sidebar" :class="{ open: showAnnotationSidebar }">
      <div class="ann-sidebar-header">
        <span class="ann-sidebar-title">批注 ({{ annotations.length }})</span>
        <el-button text @click="showAnnotationSidebar = false">
          <el-icon><Close /></el-icon>
        </el-button>
      </div>
      <div class="ann-sidebar-body">
        <div v-if="annotations.length === 0" class="ann-empty">
          暂无批注，选中文本后点击"批注"添加
        </div>
        <div v-for="ann in annotations" :key="ann.id" class="ann-card">
          <div class="ann-card-quote">"{{ ann.selectedText }}"</div>
          <div class="ann-card-note">{{ ann.note }}</div>
          <div class="ann-card-footer">
            <span class="ann-author">{{ ann.username }}</span>
            <div class="ann-actions">
              <button
                v-if="ann.isMine"
                class="ann-visibility-btn"
                :title="ann.visibility === 1 ? '当前公开，点击改为仅自己可见' : '当前仅自己可见，点击改为公开'"
                @click="handleToggleVisibility(ann)"
              >{{ ann.visibility === 1 ? '🌐' : '🔒' }}</button>
              <button class="ann-upvote-btn" :class="{ active: ann.upvotedByMe }" @click="handleUpvote(ann)">
                👍 {{ ann.upvoteCount }}
              </button>
              <button v-if="ann.isMine" class="ann-delete-btn" @click="handleDeleteAnnotation(ann)">删除</button>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 批注侧边栏触发按钮 -->
    <button
      v-if="!showAnnotationSidebar && annotations.length > 0"
      class="ann-fab"
      @click="showAnnotationSidebar = true"
      :title="`查看 ${annotations.length} 条批注`"
    >
      💬 {{ annotations.length }}
    </button>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowLeft, View, Clock, InfoFilled, Check, Timer, Loading, Picture, ZoomIn, ZoomOut, Star, StarFilled, Tickets, CircleCheck, MagicStick, Checked, VideoPlay, Edit, Close, Comment, Reading, Medal, Connection, TrendCharts } from '@element-plus/icons-vue'
import studentApi, { getVocabularyStats, createUnderline, getUnderlines, deleteUnderline, toggleArticleFavorite, checkArticleFavorite, getRelatedTexts } from '@/api/student'
import { getExercisesByTextId, submitQuizAnswers } from '@/api/exercise'
import { generateAiQuiz } from '@/api/aiQuiz'
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
import { getAnnotations, createAnnotation, deleteAnnotation, toggleUpvote, updateAnnotationVisibility } from '@/api/annotation'
import { getArticlesPath } from '@/config/site'
import rangy from '@/utils/rangy'

const route = useRoute()
const router = useRouter()

// 响应式数据
const textId = ref(Number(route.params.id || route.query.textId) || 0)
const assignmentId = ref(route.query.assignmentId || null)
const fromSource = ref(route.query.from || null)
const courseId = ref(null)
const unitId = ref(null)
const courseName = ref('')
const unitName = ref('')
const textTitle = ref('')
const headerTitleRef = ref(null)
const isTitleOverflow = ref(false)

const checkTitleOverflow = () => {
  const el = headerTitleRef.value
  if (el) {
    isTitleOverflow.value = el.scrollWidth > el.clientWidth
  }
}

const textContent = ref('')
const isMarkdown = ref(false)
const difficulty = ref('MEDIUM')
const learningProgress = ref(0) // 原有的学习进度
const learnedWordsCount = ref(0)
const studyTime = ref(0)
const isCompleted = ref(false)
const isFavorite = ref(false)
const isPublic = ref(0)
const isFeatured = ref(0)
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
const isPlainText = ref(false) // 原始内容是否为纯文本（用于 switchVersion 转换）


// 单词对话框相关数据
const showWordDialog = ref(false)
const selectedWord = ref(null)
const selectedWordInfo = ref(null)
const loadingWord = ref(false)
const addingToVocabulary = ref(false)
const isInVocabulary = ref(false)

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

// AI 出题相关数据
const quizLoading = ref(false)
const quizQuestions = ref([])
const quizStartedAt = ref(null) // 进入做题界面的时间戳，用于计算每题用时
let accumulatedTime = 0 // 累计学习时间（秒）

// 相关文章推荐
const relatedTexts = ref([])

// 相关文章卡片渐变色
const relatedGradients = [
  'linear-gradient(135deg, #667eea 0%, #764ba2 100%)',
  'linear-gradient(135deg, #f093fb 0%, #f5576c 100%)',
  'linear-gradient(135deg, #4facfe 0%, #00f2fe 100%)',
  'linear-gradient(135deg, #43e97b 0%, #38f9d7 100%)',
  'linear-gradient(135deg, #fa709a 0%, #fee140 100%)',
  'linear-gradient(135deg, #a18cd1 0%, #fbc2eb 100%)',
  'linear-gradient(135deg, #fccb90 0%, #d57eeb 100%)',
  'linear-gradient(135deg, #e0c3fc 0%, #8ec5fc 100%)',
  'linear-gradient(135deg, #f5576c 0%, #ff6a00 100%)',
  'linear-gradient(135deg, #0ba360 0%, #3cba92 100%)',
]
const getDiffText = (diff) => {
  const map = { 'EASY': '简单 EASY', 'MEDIUM': '中等 INT', 'HARD': '困难 HARD' }
  return map[diff] || diff
}
const getDiffIcon = (diff) => {
  const map = { 'EASY': Medal, 'MEDIUM': Connection, 'HARD': TrendCharts }
  return map[diff] || Medal
}

// 翻译相关
const showTranslateDialog = ref(false)
const selectedTextForTranslate = ref('')
const translatedText = ref('')
const translating = ref(false)
const isSelectingMode = ref(false)

// 响应式弹窗宽度
const windowWidth = ref(typeof window !== 'undefined' ? window.innerWidth : 1024)
const handleWindowResize = () => {
  windowWidth.value = window.innerWidth
  checkTitleOverflow()
}
let autoStartTimerId = null
let textContentWatchTimerId = null
let floatingBtnResetTimerId = null
let imagePreviewCleanupTimerId = null
let versionSwitchTimerId = null
let selectionDebounceTimerId = null
let selectionMenuHideTimerId = null
let fetchUnderlineTimerId = null
let isUserInteracting = false

const onDragStart = () => { isUserInteracting = true }
const onDragEnd = () => {
  isUserInteracting = false
}
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

// 协作批注
const annotations = ref([])
const showAnnotationSidebar = ref(false)
const showAnnotationDialog = ref(false)
const annotationNote = ref('')
const annotationSelectedText = ref('')
const submittingAnnotation = ref(false)
const annotationVisibility = ref(1)

/**
 * 判断当前选区文本是否已完全被某条划线记录覆盖。
 * 用于决定菜单按钮显示"划线"还是"取消划线"。
 */
const selectionIsUnderlined = computed(() => {
  if (!selectedTextContent.value || sharedUnderlines.value.length === 0) return false
  const trimmed = selectedTextContent.value.trim()
  return sharedUnderlines.value.some(u => u.selectedText && u.selectedText.trim() === trimmed)
})

/**
 * 判断是否为预览模式（管理员预览或用户自己查看上传页面）
 */
const isPreviewMode = computed(() => {
  return fromSource.value === 'user-upload' || route.path.includes('/admin/text-preview')
})

/**
 * 判断当前文章是否可以收藏（仅限公开文章或系统课程文章）
 */
const canFavorite = computed(() => {
  // 预览模式下不显示收藏按钮
  if (isPreviewMode.value) return false

  // 如果是学生自己上传的文章（通常unitId为999且不是公开文章），不显示收藏按钮
  if (unitId.value === 999 && isPublic.value !== 1) return false
  
  // 推荐文章、公开文章、或属于正规课程单元的文章均可收藏
  return isPublic.value === 1 || isFeatured.value === 1 || (unitId.value && unitId.value !== 999)
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
  if (isMarkdown.value) {
    const rawText = textVersions.value?.[currentVersion.value] || ''
    if (rawText) {
      text = rawText
    } else {
      text = textContent.value.replace(/<[^>]*>/g, ' ')
    }
  }

  return text.split(/\s+/).filter(word => word.length > 0).length
})

const estimatedTime = computed(() => {
  // 预计每分钟阅读100个单词
  return Math.ceil(wordCount.value / 100)
})

// AI 出题相关计算属性
const hasAnyAnswer = computed(() => {
  return quizQuestions.value.some(q => q.userAnswer)
})

const allAnswered = computed(() => {
  return quizQuestions.value.length > 0 && quizQuestions.value.every(q => q.showAnswer)
})

const allShown = computed(() => {
  return quizQuestions.value.length > 0 && quizQuestions.value.every(q => q.showAnswer)
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

// 纯文本转 HTML：段落拆分 + 转义 + 添加可点击单词
const plainTextToHtml = (rawText) => {
  if (!rawText) return ''
  const text = normalizeTextContent(rawText)
  const escaped = text
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
  const html = escaped
    .split(/\n\s*\n|\n/)
    .filter(p => p.trim().length > 0)
    .map(p => `<p>${p.trim().replace(/\n/g, '<br>')}</p>`)
    .join('')
  return addClickableWordsToHtml(DOMPurify.sanitize(html, SANITIZE_CONFIG))
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
      nextTick(checkTitleOverflow)
      difficulty.value = data.difficulty || 'MEDIUM'
      courseId.value = data.courseId
      unitId.value = data.unitId
      courseName.value = data.courseName || '未知课程'
      unitName.value = data.unitName || '未知单元'

      // 检查收藏状态
      checkArticleFavorite(textId.value).then(res => {
        if (res?.code === 200) isFavorite.value = !!res.data?.isFavorite
      }).catch(() => {})

      // 从后端获取学习进度和时长
      learningProgress.value = data.learningProgress || 0
      learnedWordsCount.value = data.learnedWordsCount || 0
      isCompleted.value = data.isCompleted || false

      // 权限和标记位
      isPublic.value = data.isPublic || 0
      isFeatured.value = data.isFeatured || 0

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
        // 纯文本格式：转为 HTML 渲染，与 Markdown 保持一致的视觉效果
        isMarkdown.value = true
        isPlainText.value = true
        if (data.versions && typeof data.versions === 'object') {
          textVersions.value = data.versions
          availableVersions.value = Object.keys(data.versions).sort((a, b) => {
            const order = { 'original': 0, 'cet4': 1, 'cet6': 2, 'academic': 3, 'chinese_parallel': 4 }
            return (order[a] || 999) - (order[b] || 999)
          })
          currentVersion.value = 'original'
          textContent.value = plainTextToHtml(textVersions.value['original'] || data.content)
        } else {
          const rawContent = data.content
          textVersions.value = { 'original': rawContent }
          availableVersions.value = ['original']
          currentVersion.value = 'original'
          textContent.value = plainTextToHtml(rawContent)
        }
      }

      // 如果是从任务页面跳转来的，显示任务信息（延迟显示，避免阻塞内容交互）
      if (fromSource.value === 'assignment' && assignmentId.value) {
        setTimeout(() => ElMessage.info('正在完成任务中的课文学习'), 500)
      }

      // 显示可用版本提示（延迟显示，避免页面初始化时 ElMessage 叠加层干扰文本选中）
      if (availableVersions.value.length > 1) {
        const versionNames = availableVersions.value.map(v => getVersionLabel(v)).join('、')
        setTimeout(() => ElMessage.success(`本课文有多个学习版本：${versionNames}，可点击切换`), 800)
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

      // 异步加载相关文章推荐（不阻塞主流程）
      getRelatedTexts(textId.value).then(res => {
        if (res?.code === 200 && Array.isArray(res.data)) {
          relatedTexts.value = res.data
        }
      }).catch(() => {})
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
  // 预览模式不记录阅读进度
  if (isPreviewMode.value) return
  
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
        translatedText.value = '翻译服务暂时不可用，请稍后重试'
      }
    } else {
      throw new Error('网络请求失败')
    }
  } catch (error) {
    console.error('翻译失败:', error)
    translatedText.value = '翻译服务暂时不可用'
  } finally {
    translating.value = false
  }
}

const handleWordClick = (wordData) => {
  // 单词点击导致 Vue 重新渲染 span 时会抹掉 Rangy 注入的划线 wrapper。
  // 等 DOM 更新后重新应用划线以恢复高亮。
  nextTick(() => {
    applySharedUnderlines()
  })
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
    if (!textId.value) return
    const response = await toggleArticleFavorite(textId.value)
    if (response.code === 200) {
      isFavorite.value = response.data.isFavorite
      ElMessage.success(response.data.message || (isFavorite.value ? '已添加到收藏' : '已取消收藏'))
    }
  } catch (error) {
    console.error('收藏课文失败:', error)
    ElMessage.error('收藏操作失败')
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
  if (from === 'admin') {
    if (window.parent && window.parent !== window) {
      window.parent.postMessage({ type: 'close-preview' }, '*')
      return
    }
  }
  
  if (from === 'admin-review') {
    // 从管理员审核页来的，返回内容管理页
    router.push('/admin/content')
  } else if (from === 'user-texts') {
    // 从学生上传页来的，返回上传页
    router.push('/student/user-texts')
  } else if (from === 'assignment') {
    // 从任务页面跳转来的，返回任务页面
    router.push('/student/assignments')
  } else if (from === 'articles') {
    // 从文章列表来的，跳转到外部文章页
    window.location.href = getArticlesPath()
  } else if (window.history.length > 1) {
    // 其他情况，使用默认返回
    router.go(-1)
  } else {
    // 无历史记录时，跳转到外部文章列表
    window.location.href = getArticlesPath()
  }
}

// 跳转到相关文章
const goToRelatedArticle = (id) => {
  const from = route.query.from
  const query = from ? { from } : {}
  router.push({ path: `/article/${id}`, query }).then(() => {
    const scrollEl = document.querySelector('.content-wrapper')
    if (scrollEl) scrollEl.scrollTop = 0
  })
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
  // 用户正在拖选文本时不处理单词点击
  const selection = window.getSelection()
  if (selection && !selection.isCollapsed) return

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
      audioUrl: selectedWordInfo.value.audioUrl || selectedWordInfo.value.audio || '',
      sourceType: 'ARTICLE' // 文章阅读页加入，明确标记来源为文章
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

// 监听内容变化重新应用划线（防抖优化：延长到 800ms，减少频繁触发）
import { watch } from 'vue'
watch(textContent, () => {
  if (textContentWatchTimerId) clearTimeout(textContentWatchTimerId)
  textContentWatchTimerId = setTimeout(() => {
    applySharedUnderlines()
    textContentWatchTimerId = null
  }, 800) // 从 500ms 延长到 800ms，减少重复渲染
})

// 监听路由参数变化（相关文章点击时复用组件，需重新加载数据）
watch(() => route.params.id, (newId, oldId) => {
  if (newId && newId !== oldId) {
    textId.value = newId
    relatedTexts.value = []
    quizQuestions.value = []
    isCompleted.value = false
    const scrollEl = document.querySelector('.content-wrapper')
    if (scrollEl) scrollEl.scrollTop = 0
    loadTextDetail()
  }
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

// 版本管理方法（优化：缓存渲染结果）
const renderedVersionCache = new Map()

const switchVersion = (version) => {
  currentVersion.value = version
  const versionContent = textVersions.value[version] || ''

  // 检查缓存
  const cacheKey = `${version}-${isPlainText.value}`
  if (renderedVersionCache.has(cacheKey)) {
    textContent.value = renderedVersionCache.get(cacheKey)
  } else {
    let renderedHtml
    if (isPlainText.value) {
      // 纯文本格式：转为 HTML
      renderedHtml = plainTextToHtml(versionContent)
    } else {
      // Markdown 格式，需要渲染
      renderedHtml = DOMPurify.sanitize(marked(versionContent), SANITIZE_CONFIG)
      renderedHtml = addClickableWordsToHtml(renderedHtml)
    }
    textContent.value = renderedHtml
    renderedVersionCache.set(cacheKey, renderedHtml)
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

// 滚动处理函数（节流优化：使用 requestAnimationFrame）
let scrollTicking = false
const handleScroll = () => {
  if (!scrollTicking) {
    window.requestAnimationFrame(() => {
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
      scrollTicking = false
    })
    scrollTicking = true
  }
}

// 生命周期
onMounted(async () => {
  await loadTextDetail()
  loadAnnotations()
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
  // 选区交互监听
  document.addEventListener('mousedown', onDragStart)
  document.addEventListener('mouseup', onDragEnd)
  document.addEventListener('touchstart', onDragStart, { passive: true })
  document.addEventListener('touchend', onDragEnd)
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

  // 清理选区交互监听
  document.removeEventListener('mousedown', onDragStart)
  document.removeEventListener('mouseup', onDragEnd)
  document.removeEventListener('touchstart', onDragStart)
  document.removeEventListener('touchend', onDragEnd)

  // 清理图片预览缩放拖拽事件监听（如果组件在拖拽图片时卸载）
  if (isDragging.value) {
    document.removeEventListener('mousemove', onDrag);
    document.removeEventListener('mouseup', endDrag);
    isDragging.value = false;
  }

  // 清理所有定时器（统一清理）
  const timers = [
    autoStartTimerId,
    textContentWatchTimerId,
    imagePreviewCleanupTimerId,
    versionSwitchTimerId,
    selectionMenuHideTimerId,
    selectionDebounceTimerId,
    fetchUnderlineTimerId
  ]
  timers.forEach(timer => {
    if (timer) {
      clearTimeout(timer)
    }
  })
  // 重置所有定时器变量
  autoStartTimerId = null
  textContentWatchTimerId = null
  imagePreviewCleanupTimerId = null
  versionSwitchTimerId = null
  selectionMenuHideTimerId = null
  selectionDebounceTimerId = null
  fetchUnderlineTimerId = null

  // 清理学习计时器
  stopStudyTimer()

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

  // 清理音频资源
  if (currentAudio) {
    currentAudio.pause()
    currentAudio.onended = null
    currentAudio.onerror = null
    currentAudio = null
  }

  // 清理缓存
  renderedVersionCache.clear()
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
const isNodeInsideArticleBody = (node) => {
  const articleBody = document.querySelector('.article-body')
  if (!articleBody || !node) return false
  const target = node.nodeType === Node.TEXT_NODE ? node.parentElement : node
  return target && articleBody.contains(target)
}

// 检测并裁切过大的选区（如 iOS/安卓长按自动扩展到末尾）
const clampLargeSelection = (selection) => {
  if (!selection || !selection.rangeCount || selection.isCollapsed) return false
  const articleBody = document.querySelector('.article-body')
  if (!articleBody) return false

  const allText = articleBody.innerText || ''
  const selectedText = selection.toString()
  if (allText.length === 0 || selectedText.length === 0) return false

  // 如果选中文本超过正文 70%，认为是长按到末尾的漂移选区，需要裁切
  if (selectedText.length <= allText.length * 0.7) return false

  // 裁切起点到正文开头，保留终点
  const range = selection.getRangeAt(0)
  const newRange = document.createRange()
  newRange.setStart(articleBody, 0)

  const walker = document.createTreeWalker(articleBody, NodeFilter.SHOW_TEXT, null)
  let lastNode = null
  while (walker.nextNode()) {
    if (walker.currentNode.textContent.trim()) lastNode = walker.currentNode
  }
  if (lastNode) {
    newRange.setEnd(lastNode, lastNode.textContent.length)
  } else {
    newRange.setEnd(range.endContainer, range.endOffset)
  }

  selection.removeAllRanges()
  selection.addRange(newRange)
  return true
}

// 处理文本选择（防抖：50ms 延迟防止拖动时频繁触发）
const handleSelectionChange = () => {
  if (isTranslateBtnDragging.value) return

  const selection = window.getSelection()
  if (!selection || !selection.rangeCount || selection.isCollapsed) {
    if (selectionMenuHideTimerId) clearTimeout(selectionMenuHideTimerId)
    selectionMenuHideTimerId = setTimeout(() => {
      const sel = window.getSelection()
      if (!sel || !sel.rangeCount || sel.isCollapsed) showSelectionMenu.value = false
      selectionMenuHideTimerId = null
    }, 200)
    return
  }

  if (selectionDebounceTimerId) clearTimeout(selectionDebounceTimerId)
  selectionDebounceTimerId = setTimeout(() => {
    const sel = window.getSelection()
    if (!sel || !sel.rangeCount || sel.isCollapsed) {
      showSelectionMenu.value = false
      return
    }

    const trimmedText = sel.toString().trim()
    if (!trimmedText) {
      showSelectionMenu.value = false
      return
    }

    if (isNodeInsideArticleBody(sel.anchorNode) && isNodeInsideArticleBody(sel.focusNode)) {
      selectedTextContent.value = trimmedText
      const range = sel.getRangeAt(0)
      const rect = range.getBoundingClientRect()

      selectionPosition.x = rect.left + rect.width / 2
      selectionPosition.y = rect.top - 60
      showSelectionMenu.value = true
    } else {
      showSelectionMenu.value = false
    }
  }, 50)
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
    // 裁切过大选区（如 iOS/安卓长按自动扩展到末尾）
    clampLargeSelection(window.getSelection())
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
      removeAllHighlights()
      applySharedUnderlines()
      ElMessage.error(res?.message || '划线失败，请重试')
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

const loadAnnotations = async () => {
  if (!textId.value) return
  try {
    const res = await getAnnotations(textId.value)
    if (res?.code === 200) annotations.value = res.data || []
  } catch (e) {
    console.error('Failed to load annotations', e)
  }
}

const handleAnnotate = () => {
  if (!selectedTextContent.value?.trim()) return
  annotationSelectedText.value = selectedTextContent.value.trim()
  annotationNote.value = ''
  annotationVisibility.value = 1
  showAnnotationDialog.value = true
  showSelectionMenu.value = false
}

const submitAnnotation = async () => {
  if (!annotationNote.value.trim()) {
    ElMessage.warning('请输入批注内容')
    return
  }
  submittingAnnotation.value = true
  try {
    const res = await createAnnotation({
      textId: textId.value,
      selectedText: annotationSelectedText.value,
      note: annotationNote.value.trim(),
      visibility: annotationVisibility.value
    })
    if (res?.code === 200) {
      annotations.value.unshift(res.data)
      showAnnotationDialog.value = false
      showAnnotationSidebar.value = true
      ElMessage.success('批注已添加')
    }
  } catch (e) {
    ElMessage.error('添加批注失败')
  } finally {
    submittingAnnotation.value = false
  }
}

const handleDeleteAnnotation = async (ann) => {
  try {
    await deleteAnnotation(ann.id)
    annotations.value = annotations.value.filter(a => a.id !== ann.id)
    ElMessage.success('已删除')
  } catch (e) {
    ElMessage.error('删除失败')
  }
}

const handleToggleVisibility = async (ann) => {
  const next = ann.visibility === 1 ? 0 : 1
  try {
    await updateAnnotationVisibility(ann.id, next)
    ann.visibility = next
    ElMessage.success(next === 1 ? '已改为公开' : '已改为仅自己可见')
  } catch (e) {
    ElMessage.error('修改失败')
  }
}

const handleUpvote = async (ann) => {
  try {
    const res = await toggleUpvote(ann.id)
    if (res?.code === 200) {
      ann.upvoteCount = res.data.upvoteCount
      ann.upvotedByMe = !ann.upvotedByMe
    }
  } catch (e) {
    ElMessage.error('操作失败')
  }
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

// AI 出题相关方法
const handleAiQuiz = async () => {
  if (!textId.value) {
    ElMessage.warning('文章ID无效')
    return
  }

  // 优先恢复本地保存的做题进度
  if (restoreQuizProgress()) {
    ElMessage.success('已恢复上次做题进度')
    return
  }

  quizLoading.value = true
  try {
    // 先检查数据库中是否已有题目
    const existResponse = await getExercisesByTextId(textId.value)
    const existData = existResponse?.data || existResponse
    if (existData && Array.isArray(existData) && existData.length > 0) {
      // 数据库中已有题目，直接使用
      quizQuestions.value = existData.map(q => ({
        ...q,
        userAnswer: null,
        showAnswer: false,
        options: q.options || []
      }))
      quizStartedAt.value = Date.now()
      ElMessage.success(`已加载 ${existData.length} 道题目`)
      return
    }

    // 数据库中没有题目，调用 AI 生成
    ElMessage.info('正在调用 AI 生成题目，请稍候...')
    const response = await generateAiQuiz(textId.value, 5)
    const quizData = response?.data || response
    if (quizData && quizData.questionCount > 0) {
      // 生成成功，重新从数据库加载题目
      const newExercises = await getExercisesByTextId(textId.value)
      const newData = newExercises?.data || newExercises
      if (newData && Array.isArray(newData) && newData.length > 0) {
        quizQuestions.value = newData.map(q => ({
          ...q,
          userAnswer: null,
          showAnswer: false,
          options: q.options || []
        }))
        quizStartedAt.value = Date.now()
        ElMessage.success(`AI 成功生成 ${quizData.questionCount} 道题目`)
      }
    } else {
      ElMessage.error('AI 出题失败，请稍后重试')
    }
  } catch (error) {
    console.error('AI quiz error:', error)
    ElMessage.error(error.message || 'AI 出题失败')
  } finally {
    quizLoading.value = false
  }
}

const selectQuizAnswer = (questionIndex, optionKey) => {
  if (quizQuestions.value[questionIndex].showAnswer) return
  quizQuestions.value[questionIndex].userAnswer = optionKey
  quizQuestions.value[questionIndex]._answeredAt = Date.now()
}

const submitQuiz = async () => {
  const unanswered = quizQuestions.value.filter(q => !q.userAnswer)
  if (unanswered.length > 0) {
    ElMessage.warning(`还有 ${unanswered.length} 题未作答`)
    return
  }

  try {
    let prevTs = quizStartedAt.value || Date.now()
    const answers = quizQuestions.value.map(q => {
      const ts = q._answeredAt || Date.now()
      const timeSpentSeconds = Math.max(0, Math.round((ts - prevTs) / 1000))
      prevTs = ts
      return {
        exerciseId: q.id,
        userAnswer: q.userAnswer,
        timeSpentSeconds
      }
    })
    const res = await submitQuizAnswers({ answers })
    const data = res?.data || res

    if (data && data.results) {
      const resultMap = {}
      data.results.forEach(r => { resultMap[r.exerciseId] = r })

      quizQuestions.value.forEach(q => {
        const r = resultMap[q.id]
        if (r) {
          q.showAnswer = true
          q._isCorrect = r.isCorrect
          q._correctAnswer = r.correctAnswer
          if (r.analysis) q.analysis = r.analysis
          // 标记选项对错
          q.options.forEach(opt => {
            if (r.correctAnswer && opt.optionKey === r.correctAnswer) {
              opt.isCorrect = 1
            }
          })
        }
      })
      ElMessage.success(`答对 ${data.correctCount}/${data.total} 题`)
    } else {
      // fallback: 仅显示答案
      quizQuestions.value.forEach(q => { q.showAnswer = true })
    }
  } catch (e) {
    // fallback: 仅显示答案
    quizQuestions.value.forEach(q => { q.showAnswer = true })
  }
}

const showAllAnswers = () => {
  quizQuestions.value.forEach(q => {
    q.showAnswer = true
  })
}

const closeQuiz = () => {
  // 保存做题进度到 localStorage
  if (quizQuestions.value.length > 0 && textId.value) {
    const key = `quiz_progress_${textId.value}`
    const saved = quizQuestions.value.map(q => ({
      id: q.id,
      question: q.question,
      questionType: q.questionType,
      options: q.options,
      userAnswer: q.userAnswer,
      showAnswer: q.showAnswer,
      analysis: q.analysis
    }))
    try {
      localStorage.setItem(key, JSON.stringify(saved))
    } catch (e) {
      console.warn('保存做题进度失败:', e)
    }
  }
  quizQuestions.value = []
}

const restoreQuizProgress = () => {
  if (!textId.value) return false
  const key = `quiz_progress_${textId.value}`
  try {
    const raw = localStorage.getItem(key)
    if (raw) {
      const saved = JSON.parse(raw)
      if (Array.isArray(saved) && saved.length > 0) {
        quizQuestions.value = saved
        quizStartedAt.value = Date.now()
        localStorage.removeItem(key)
        return true
      }
    }
  } catch (e) {
    console.warn('恢复做题进度失败:', e)
  }
  return false
}

const resetQuizAnswers = () => {
  quizQuestions.value.forEach(q => {
    q.userAnswer = null
    q.showAnswer = false
  })
}

const regenerateQuiz = async () => {
  // 删除旧题目并重新生成
  quizLoading.value = true
  try {
    // 直接调用 AI 生成（绕过已有题目检查）
    ElMessage.info('正在重新生成题目...')
    const response = await generateAiQuiz(textId.value, 5, true)
    const quizData = response?.data || response
    if (quizData && quizData.questionCount > 0) {
      const newExercises = await getExercisesByTextId(textId.value)
      const newData = newExercises?.data || newExercises
      if (newData && Array.isArray(newData) && newData.length > 0) {
        // 取最新生成的 N 道（按 createdTime 排序后取最后 quizData.questionCount 道）
        const sorted = [...newData].sort((a, b) =>
          new Date(b.createdTime || 0) - new Date(a.createdTime || 0)
        )
        const latest = sorted.slice(0, quizData.questionCount).reverse()
        quizQuestions.value = latest.map(q => ({
          ...q,
          userAnswer: null,
          showAnswer: false,
          options: q.options || []
        }))
        quizStartedAt.value = Date.now()
        ElMessage.success(`已重新生成 ${quizData.questionCount} 道题目`)
      }
    } else {
      ElMessage.error('AI 出题失败，请稍后重试')
    }
  } catch (error) {
    console.error('AI quiz error:', error)
    ElMessage.error(error.message || 'AI 出题失败')
  } finally {
    quizLoading.value = false
  }
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
        // 反序列化失败是正常回退流程（DOM 结构变化后序列化失效），静默处理
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
  z-index: 100;
  background: var(--glass-bg);
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  border-bottom: 1px solid var(--border-glass);
  transition: transform 0.3s ease;
  /* 移除 contain: paint — 该属性创建新的 stacking context，
     与 ElMessage(z-index:2031) 交互时可能导致下方内容区域无法接收 pointer events */
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

.header-title-scroll {
  max-width: 300px;
  overflow: hidden;
  position: relative;
}

.header-title {
  font-size: 16px;
  font-weight: 700;
  white-space: nowrap;
  display: inline-block;
  padding-right: 24px;
}

.header-title.scrolling {
  animation: header-title-scroll 12s linear infinite;
}

@keyframes header-title-scroll {
  0%, 10% { transform: translateX(0); }
  90%, 100% { transform: translateX(calc(-100% + 300px + 24px)); }
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

.annotation-header-btn {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border-radius: 50%;
  background: none;
  border: none;
  color: #64748b;
  cursor: pointer;
  font-size: 18px;
  transition: background 0.2s, color 0.2s;
}
.annotation-header-btn:hover { background: #f1f5f9; color: #197fe6; }
.ann-header-count {
  position: absolute;
  top: 2px;
  right: 2px;
  background: #197fe6;
  color: white;
  font-size: 10px;
  font-weight: 700;
  min-width: 14px;
  height: 14px;
  border-radius: 7px;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 0 3px;
  line-height: 1;
}

/* Reader Container */
.reader-container {
  max-width: 1200px;
  margin: 0 auto;
  padding: 60px 24px;
  position: relative;
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
  user-select: text;
  -webkit-user-select: text;
  -webkit-tap-highlight-color: transparent;
  cursor: text;
  position: relative;
  z-index: 1;
}

.article-body :deep(.interactive-text),
.article-body :deep(.text-content),
.article-body :deep(.text-paragraph),
.article-body :deep(.word),
.article-body :deep(.clickable),
.article-body :deep(.clickable-word),
.markdown-content,
.markdown-content * {
  user-select: text;
  -webkit-user-select: text;
  -webkit-touch-callout: default;
}

.article-title,
.article-title *,
.article-header,
.article-header *,
.article-meta,
.article-meta *,
.version-pill-selector,
.version-pill-selector *,
.reader-footer,
.reader-footer *,
.floating-translate-btn,
.floating-translate-btn * {
  user-select: none !important;
  -webkit-user-select: none !important;
  -webkit-touch-callout: none !important;
}

.floating-translate-btn,
.floating-translate-btn button,
.floating-translate-btn .translate-fab,
.floating-translate-btn .fab-icon,
.floating-translate-btn .fab-label,
.floating-translate-btn span {
  user-select: none !important;
  -webkit-user-select: none !important;
  -webkit-touch-callout: none !important;
  -webkit-tap-highlight-color: transparent;
}

.article-body :deep(.interactive-text),
.markdown-content {
  background: #fff;
  padding: 24px;
  border-radius: 8px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.1);
  font-family: 'Georgia', serif;
  line-height: 1.8;
  color: #333;
  word-break: break-word;
  overflow-wrap: break-word;
}

.markdown-content :deep(p) {
  text-align: justify;
  margin-bottom: 24px;
}

.markdown-content :deep(ul),
.markdown-content :deep(ol) {
  margin: 0 0 24px 0;
  padding-left: 24px;
}

.markdown-content :deep(li) {
  margin-bottom: 8px;
  line-height: 1.8;
}

.markdown-content :deep(li p) {
  margin-bottom: 8px;
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
.ai-quiz-section {
  margin-top: 40px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 24px;
}

.quiz-inline {
  width: 100%;
  background: #fefefe;
  border: 1px solid #e2e8f0;
  border-radius: 16px;
  padding: 20px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
}

.quiz-inline-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-bottom: 16px;
  margin-bottom: 16px;
  border-bottom: 1px solid #e2e8f0;
}

.quiz-inline-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 16px;
  font-weight: 600;
  color: #8b5cf6;
}

.quiz-inline-actions-top {
  display: flex;
  gap: 8px;
}

.quiz-actions {
  display: flex;
  justify-content: center;
  gap: 12px;
  margin-top: 20px;
  padding-top: 16px;
  border-top: 1px solid #e2e8f0;
}

.ai-quiz-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 14px 36px;
  background: linear-gradient(135deg, #8b5cf6, #a78bfa);
  border: none;
  border-radius: 12px;
  color: white;
  font-size: 16px;
  font-weight: 600;
  cursor: pointer;
  transition: transform 0.2s, box-shadow 0.2s;
}

.ai-quiz-btn:hover:not(:disabled) {
  transform: scale(1.02);
  box-shadow: 0 8px 20px -4px rgba(139, 92, 246, 0.4);
}

.ai-quiz-btn:active:not(:disabled) {
  transform: scale(0.98);
}

.ai-quiz-btn:disabled {
  opacity: 0.7;
  cursor: not-allowed;
}

.ai-quiz-btn .btn-content {
  display: flex;
  align-items: center;
  gap: 8px;
}

.quiz-content {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.quiz-question {
  padding: 16px;
  background: #f8fafc;
  border-radius: 12px;
  border: 1px solid #e2e8f0;
}

.question-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
}

.question-number {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  background: linear-gradient(135deg, #8b5cf6, #a78bfa);
  color: white;
  border-radius: 50%;
  font-size: 14px;
  font-weight: 600;
}

.question-type {
  font-size: 12px;
  color: #64748b;
  padding: 2px 8px;
  background: #e2e8f0;
  border-radius: 4px;
}

.question-text {
  font-size: 15px;
  line-height: 1.6;
  color: #1e293b;
  margin-bottom: 16px;
}

.question-options {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.option-item {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 10px 14px;
  background: white;
  border: 2px solid #e2e8f0;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.2s;
}

.option-item:hover:not(.correct):not(.wrong) {
  border-color: #a78bfa;
  background: #faf5ff;
}

.option-item.selected {
  border-color: #8b5cf6;
  background: #f5f3ff;
}

.option-item.correct {
  border-color: #10b981;
  background: #ecfdf5;
}

.option-item.wrong {
  border-color: #ef4444;
  background: #fef2f2;
}

.option-key {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  background: #e2e8f0;
  border-radius: 50%;
  font-size: 12px;
  font-weight: 600;
  color: #475569;
  flex-shrink: 0;
}

.option-item.selected .option-key {
  background: #8b5cf6;
  color: white;
}

.option-item.correct .option-key {
  background: #10b981;
  color: white;
}

.option-item.wrong .option-key {
  background: #ef4444;
  color: white;
}

.option-content {
  font-size: 14px;
  line-height: 1.5;
  color: #334155;
}

.question-analysis {
  margin-top: 16px;
  padding: 12px;
  background: #fffbeb;
  border-radius: 8px;
  border: 1px solid #fde68a;
}

.analysis-label {
  font-size: 13px;
  font-weight: 600;
  color: #92400e;
  margin-bottom: 6px;
}

.analysis-text {
  font-size: 13px;
  line-height: 1.6;
  color: #78350f;
}

.quiz-empty {
  text-align: center;
  padding: 40px;
  color: #94a3b8;
}

/* Related Articles */
.related-articles {
  margin-top: 48px;
  padding: 0 8px;
}

.related-title {
  font-size: 18px;
  font-weight: 600;
  color: #1e293b;
  margin-bottom: 16px;
  padding-left: 4px;
}

.related-cards {
  display: flex;
  gap: 12px;
  overflow-x: auto;
  padding-bottom: 8px;
  -webkit-overflow-scrolling: touch;
  scrollbar-width: thin;
}

.related-cards::-webkit-scrollbar {
  height: 4px;
}

.related-cards::-webkit-scrollbar-thumb {
  background: #cbd5e1;
  border-radius: 2px;
}

.related-cards .article-card {
  background: white;
  border-radius: 12px;
  overflow: hidden;
  box-shadow: 0 4px 20px -2px rgba(0,0,0,0.05);
  transition: transform 0.2s, box-shadow 0.2s;
  cursor: pointer;
  display: flex;
  flex-direction: column;
  border: 1px solid #f1f5f9;
  flex: 0 0 200px;
  height: 280px;
}

@media (hover: hover) {
  .related-cards .article-card:hover {
    transform: translateY(-4px);
    box-shadow: 0 12px 30px -4px rgba(0,0,0,0.08);
  }
}

.related-cards .article-card:active {
  transform: scale(0.98);
}

.related-cards .article-cover {
  position: relative;
  width: 100%;
  height: 120px;
  overflow: hidden;
}

.related-cards .cover-image {
  width: 100%;
  height: 100%;
  background-size: cover;
  background-position: center;
  transition: transform 0.7s ease;
}

.related-cards .article-card:hover .cover-image {
  transform: scale(1.05);
}

.related-cards .cover-gradient {
  position: absolute;
  inset: 0;
  background: linear-gradient(to top, rgba(0,0,0,0.5) 0%, transparent 60%);
  z-index: 1;
}

.related-cards .category-badge {
  position: absolute;
  bottom: 8px;
  left: 8px;
  z-index: 2;
  background: rgba(255, 255, 255, 0.2);
  backdrop-filter: blur(8px);
  -webkit-backdrop-filter: blur(8px);
  border: 1px solid rgba(255,255,255,0.2);
  color: white;
  padding: 3px 8px;
  border-radius: 6px;
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 10px;
  font-weight: 600;
}

.related-cards .article-content {
  padding: 10px 12px 6px;
  flex: 1;
}

.related-cards .article-title {
  margin: 0 0 6px 0;
  font-size: 14px;
  font-weight: 700;
  line-height: 1.3;
  color: #0f172a;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  word-break: break-word;
  height: calc(2 * 1.3em);
}

.related-cards .article-snippet {
  margin: 0;
  font-size: 12px;
  line-height: 1.4;
  color: var(--app-text-muted, #94a3b8);
  display: -webkit-box;
  -webkit-line-clamp: 1;
  line-clamp: 1;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.related-cards .article-footer {
  padding: 0 12px 10px;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.related-cards .difficulty-pill {
  display: flex;
  align-items: center;
  gap: 3px;
  padding: 3px 6px;
  border-radius: 6px;
  font-size: 10px;
  font-weight: 700;
}

.related-cards .difficulty-pill.EASY {
  background: #d1fae5;
  color: #065f46;
}

.related-cards .difficulty-pill.MEDIUM {
  background: #fef3c7;
  color: #92400e;
}

.related-cards .difficulty-pill.HARD {
  background: #fee2e2;
  color: #991b1b;
}

.related-cards .reading-time {
  display: flex;
  align-items: center;
  gap: 3px;
  font-size: 11px;
  color: var(--app-text-muted, #94a3b8);
}

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
  .premium-reader {
    padding-bottom: 60px;
  }

  .glass-header .header-inner {
    height: 48px;
    padding: 0 8px;
    flex-wrap: nowrap;
  }

  .glass-header .header-left,
  .glass-header .header-right {
    gap: 8px;
  }

  .glass-header .header-title-scroll {
    max-width: 150px;
  }

  .glass-header .header-title {
    font-size: 13px;
  }

  .glass-header .header-title.scrolling {
    animation: header-title-scroll-mobile 12s linear infinite;
  }

  @keyframes header-title-scroll-mobile {
    0%, 10% { transform: translateX(0); }
    90%, 100% { transform: translateX(calc(-100% + 150px + 24px)); }
  }

  .article-title {
    font-size: 24px;
    margin-bottom: 12px;
  }

  .reader-container {
    padding: 16px 12px;
  }

  .article-header {
    margin-bottom: 16px;
  }

  .article-meta {
    margin-bottom: 12px;
    font-size: 13px;
  }

  .article-body {
    font-size: 16px;
    line-height: 1.75;
  }


  .article-body :deep(.interactive-text),
  .markdown-content {
    padding: 12px;
  }

  .article-body :deep(.text-content) {
    padding: 12px !important;
  }

  .header-inner {
    padding: 0 12px;
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

  .related-articles {
    margin-top: 32px;
  }

  .related-title {
    font-size: 16px;
  }

  .related-cards .article-card {
    flex: 0 0 160px;
    height: 240px;
  }

  .related-cards .article-cover {
    height: 100px;
  }

  .related-cards .article-title {
    font-size: 13px;
  }

  .related-cards .article-snippet {
    font-size: 11px;
  }

  .reader-footer {
    margin-top: 40px;
  }

  .footer-divider {
    margin-bottom: 24px;
  }

  .markdown-content :deep(h2) {
    font-size: 20px;
    margin: 24px 0 12px;
  }

  .markdown-content :deep(p) {
    margin-bottom: 16px;
  }
}

/* Markdown Content Styling Overrides */
.markdown-content :deep(h2) {
  font-size: 24px;
  font-weight: 700;
  margin: 48px 0 16px;
}

.markdown-content :deep(strong) {
  color: var(--text-main);
  font-weight: 600;
}

/* 弹窗样式 - 与纯文本文章路径 components/InteractiveText.vue 保持一致 */
.word-dialog :deep(.el-dialog__body) {
  padding: 0 20px 20px 20px;
}

.word-dialog :deep(.el-dialog__header) {
  padding: 16px 20px 0 20px;
}

.word-header {
  text-align: center;
  padding: 6px 0 2px 0;
  border-bottom: 1px solid #f0f0f0;
  margin-bottom: 8px;
}

.word-title {
  margin: 0 0 2px 0;
  font-size: 18px;
  font-weight: 600;
  color: #1890ff;
  line-height: 1.0;
}

.pronunciation {
  font-size: 12px;
  color: #666;
  font-family: 'Lucida Sans Unicode', 'Arial Unicode MS', Arial;
  line-height: 1.0;
}

.word-body {
  max-height: 200px;
  overflow-y: auto;
  padding: 0 4px;
}

.main-meaning {
  text-align: center;
  margin-bottom: 8px;
}

.meaning-content {
  font-size: 13px;
  line-height: 1.3;
  color: #2c3e50;
  background: #f8f9fa;
  padding: 6px 8px;
  border-radius: 4px;
  border-left: 2px solid #1890ff;
  display: inline-block;
  max-width: 100%;
  text-align: left;
  white-space: pre-line;
  word-break: break-word;
  box-sizing: border-box;
}

.loading-content {
  text-align: center;
  padding: 40px 0;
}

.error-content {
  text-align: center;
  padding: 20px 0;
}

@media (max-width: 768px) {
  .word-title {
    font-size: 16px;
  }

  .pronunciation {
    font-size: 11px;
  }

  .word-dialog :deep(.el-dialog__body) {
    padding: 0 8px 8px 8px;
  }

  .word-dialog :deep(.el-dialog__header) {
    padding: 12px 8px 0 8px;
  }
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

:global(.el-message) {
  top: 55px !important;
}

/* 浮动翻译按钮（Markdown 模式，样式与 components/InteractiveText.vue 保持一致） */
.floating-translate-btn {
  position: fixed;
  z-index: 1000;
  cursor: move;
  touch-action: none;
  filter: drop-shadow(0 4px 12px rgba(0, 0, 0, 0.2));
  transition: filter 0.2s;
}

.floating-translate-btn:hover {
  filter: drop-shadow(0 6px 20px rgba(24, 144, 255, 0.4));
}

.floating-translate-btn .translate-fab {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  width: 56px;
  height: 56px;
  border-radius: 16px;
  border: none;
  cursor: pointer;
  padding: 0;
  gap: 2px;
  transition: transform 0.2s, box-shadow 0.2s;
  position: relative;
  overflow: hidden;
}

.floating-translate-btn .translate-fab::before {
  content: '';
  position: absolute;
  inset: 0;
  border-radius: inherit;
  opacity: 0;
  transition: opacity 0.2s;
  background: rgba(255, 255, 255, 0.15);
}

.floating-translate-btn .translate-fab:hover::before { opacity: 1; }
.floating-translate-btn .translate-fab:active { transform: scale(0.94) !important; }

.floating-translate-btn .fab-default {
  background: linear-gradient(135deg, #1890ff 0%, #096dd9 100%);
  box-shadow: 0 4px 14px rgba(24, 144, 255, 0.5), 0 0 0 3px rgba(24, 144, 255, 0.15);
}

.floating-translate-btn .fab-active {
  background: linear-gradient(135deg, #52c41a 0%, #389e0d 100%);
  box-shadow: 0 4px 14px rgba(82, 196, 26, 0.5), 0 0 0 3px rgba(82, 196, 26, 0.15);
  animation: md-pulse-active 1.5s ease-in-out infinite;
}

.floating-translate-btn .fab-icon {
  font-size: 20px;
  font-weight: 800;
  color: #fff;
  line-height: 1;
  letter-spacing: -0.5px;
}

.floating-translate-btn .fab-label {
  font-size: 9px;
  font-weight: 600;
  color: rgba(255, 255, 255, 0.9);
  line-height: 1;
  white-space: nowrap;
  letter-spacing: 0.3px;
}

.floating-translate-btn:hover .translate-fab {
  transform: scale(1.08) translateY(-2px);
}

@keyframes md-pulse-active {
  0%, 100% {
    box-shadow: 0 4px 14px rgba(82, 196, 26, 0.5), 0 0 0 3px rgba(82, 196, 26, 0.15);
  }
  50% {
    box-shadow: 0 6px 20px rgba(82, 196, 26, 0.65), 0 0 0 6px rgba(82, 196, 26, 0.1);
  }
}

@media (max-width: 768px) {
  .floating-translate-btn .translate-fab {
    width: 52px;
    height: 52px;
    border-radius: 14px;
  }
  .floating-translate-btn .fab-icon { font-size: 18px; }
  .floating-translate-btn .fab-label { font-size: 8px; }
}

@media (max-width: 480px) {
  .floating-translate-btn .translate-fab {
    width: 48px;
    height: 48px;
    border-radius: 12px;
  }
  .floating-translate-btn .fab-icon { font-size: 17px; }
}

</style>

<style>
/* 全局弹窗样式 - 不受 scoped 限制，与 components/InteractiveText.vue 保持一致 */
.word-dialog.el-dialog,
.word-dialog.el-dialog__wrapper {
  margin: 0 !important;
  position: absolute !important;
  top: 50% !important;
  left: 50% !important;
  transform: translate(-50%, -50%) !important;
  width: 85% !important;
  max-width: 320px !important;
  max-height: 60vh;
}

@media (max-width: 480px) {
  .word-dialog.el-dialog,
  .word-dialog.el-dialog__wrapper {
    width: 90% !important;
    max-width: 300px !important;
    max-height: 50vh;
  }
}

/* 可点击单词样式 - v-html 注入的元素必须在全局样式中才能生效 */
/* 与 components/InteractiveText.vue 中 .word.clickable:hover 保持一致 */
.clickable-word {
  cursor: pointer;
  border-radius: 3px;
  transition: background-color 0.2s ease;
}

.clickable-word:hover {
  background: rgba(24, 144, 255, 0.1);
}

/* 翻译弹窗样式 - el-dialog teleport 到 body，必须在全局样式中 */
/* 与 components/InteractiveText.vue 保持一致 */
.translate-dialog .translate-content {
  padding: 16px 0;
}

.translate-dialog .original-text,
.translate-dialog .translated-text {
  margin-bottom: 12px;
}

.translate-dialog .original-text h4,
.translate-dialog .translated-text h4 {
  margin: 0 0 8px 0;
  color: #666;
  font-size: 14px;
  font-weight: 600;
}

.translate-dialog .original-text p,
.translate-dialog .translated-text p {
  max-height: 350px;
  overflow-y: auto;
  margin: 0;
  padding: 12px;
  background: #f8f9fa;
  border-radius: 6px;
  line-height: 1.6;
  color: #333;
}

.translate-dialog .translated-text p {
  background: #e6f7ff;
  border-left: 3px solid #1890ff;
}

@media (max-width: 768px) {
  .translate-dialog.el-dialog {
    width: 92vw !important;
    max-width: 92vw !important;
    margin: 0 auto;
  }
  .translate-dialog .el-dialog__body {
    padding: 8px 12px 12px;
  }
  .translate-dialog .translate-content {
    padding: 0;
    display: flex;
    flex-direction: column;
    gap: 8px;
    max-height: 65vh;
  }
  .translate-dialog .original-text,
  .translate-dialog .translated-text {
    flex: 1;
    min-height: 0;
    display: flex;
    flex-direction: column;
    margin-bottom: 0;
  }
  .translate-dialog .text-scroll-area {
    flex: 1;
    min-height: 0;
    overflow-y: auto;
    -webkit-overflow-scrolling: touch;
  }
  .translate-dialog .original-text p,
  .translate-dialog .translated-text p {
    padding: 8px;
    font-size: 14px;
    word-break: break-word;
    margin: 0;
  }
}

/* ── 协作批注 ── */
.annotation-sidebar {
  position: fixed;
  top: 0;
  right: -340px;
  width: 320px;
  height: 100vh;
  background: #fff;
  box-shadow: -4px 0 20px rgba(0,0,0,0.12);
  z-index: 1500;
  display: flex;
  flex-direction: column;
  transition: right 0.3s ease;
}
.annotation-sidebar.open { right: 0; }

.ann-sidebar-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 16px 12px;
  border-bottom: 1px solid #f0f0f0;
}
.ann-sidebar-title { font-weight: 700; font-size: 15px; color: #1e293b; }

.ann-sidebar-body {
  flex: 1;
  overflow-y: auto;
  padding: 12px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.ann-empty { color: #94a3b8; font-size: 13px; text-align: center; padding: 32px 0; }

.ann-card {
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  padding: 12px;
}
.ann-card-quote {
  font-size: 12px;
  color: #64748b;
  font-style: italic;
  margin-bottom: 6px;
  line-height: 1.5;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.ann-card-note { font-size: 14px; color: #1e293b; line-height: 1.6; margin-bottom: 8px; }
.ann-card-footer { display: flex; align-items: center; justify-content: space-between; }
.ann-author { font-size: 12px; color: #94a3b8; }
.ann-actions { display: flex; gap: 8px; align-items: center; }
.ann-upvote-btn {
  background: none;
  border: 1px solid #e2e8f0;
  border-radius: 20px;
  padding: 2px 10px;
  font-size: 12px;
  cursor: pointer;
  color: #64748b;
  transition: all 0.15s;
}
.ann-upvote-btn.active { border-color: #197fe6; color: #197fe6; background: #eff6ff; }
.ann-upvote-btn:hover { border-color: #197fe6; color: #197fe6; }
.ann-delete-btn {
  background: none;
  border: none;
  color: #ef4444;
  font-size: 12px;
  cursor: pointer;
  padding: 2px 4px;
}
.ann-private-badge { font-size: 13px; opacity: 0.7; cursor: default; }
.ann-visibility-btn {
  background: none;
  border: none;
  font-size: 14px;
  cursor: pointer;
  padding: 2px 4px;
  border-radius: 4px;
  opacity: 0.7;
  transition: opacity 0.15s;
}
.ann-visibility-btn:hover { opacity: 1; background: #f1f5f9; }
.ann-visibility-row {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 12px;
}
.ann-visibility-label { font-size: 13px; color: #64748b; white-space: nowrap; }
.annotation-dialog-body { display: flex; flex-direction: column; gap: 0; }

.ann-fab {
  position: fixed;
  bottom: 80px;
  right: 16px;
  background: #197fe6;
  color: white;
  border: none;
  border-radius: 24px;
  padding: 10px 16px;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  box-shadow: 0 4px 12px rgba(25,127,230,0.4);
  z-index: 1400;
  transition: transform 0.15s;
}
.ann-fab:hover { transform: scale(1.05); }

.annotation-dialog-body { display: flex; flex-direction: column; gap: 12px; }
.ann-selected-text {
  background: #f8fafc;
  border-left: 3px solid #197fe6;
  padding: 8px 12px;
  border-radius: 0 6px 6px 0;
  font-size: 13px;
  color: #475569;
  font-style: italic;
  line-height: 1.5;
  max-height: 80px;
  overflow-y: auto;
}
.ann-quote-mark { color: #197fe6; font-size: 16px; font-style: normal; }
</style>
