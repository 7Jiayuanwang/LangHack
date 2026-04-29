<template>
  <div class="interactive-text">
    <!-- 可拖动的翻译按钮 -->
    <div
      class="floating-translate-btn"
      :class="{ 'selecting': isSelectingMode }"
      :style="{ top: floatingBtnPosition.y + 'px', left: floatingBtnPosition.x + 'px' }"
      @mousedown="startDrag"
      @touchstart.prevent="startDrag"
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

    <div
      class="text-content"
      v-if="text"
    >
      <div class="text-paragraph" v-for="(paragraph, pIndex) in paragraphs" :key="`p-${pIndex}-${paragraph.words.length}`">
        <template v-for="(word, wIndex) in paragraph.words" :key="`p${pIndex}-w${wIndex}-${word.cleanWord || word.text}`">
          <span
            v-if="word.clickable"
            :class="['word', 'clickable', { 'clicked': word.isClicked }]"
            @click="handleWordClick(word)"
          >{{ word.text }}</span>
          <template v-else>{{ word.text }}</template>
        </template>
      </div>
    </div>

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
            <p>{{ selectedText }}</p>
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

    <!-- 词汇信息弹窗 -->
    <el-dialog
      v-model="showWordDialog"
      title="词汇详情"
      width="min(350px, 92vw)"
      class="word-dialog"
      align-center
      :before-close="handleDialogClose"
    >
      <div class="word-content" v-if="selectedWordInfo">
        <div class="word-header">
          <div class="word-title-row">
            <h2 class="word-title">{{ selectedWordInfo.word || '未知单词' }}</h2>
            <el-button 
              circle 
              size="small" 
              @click="speakText(selectedWordInfo.word)"
              :disabled="ttsLoading"
              class="word-speak-btn"
            >
              <el-icon v-if="ttsLoading && ttsCurrentText === (selectedWordInfo?.word || '')" class="is-loading"><Loading /></el-icon>
              <el-icon v-else><Headset /></el-icon>
            </el-button>
          </div>
          <div class="pronunciation" v-if="selectedWordInfo.pronunciation || selectedWordInfo.phonetic">
            {{ selectedWordInfo.pronunciation || selectedWordInfo.phonetic }}
          </div>
        </div>

        <div class="word-body">
          <!-- 主要释义：优先显示后端处理后的 definition（中文），其次原始 translation，最后英文定义 -->
          <div class="main-meaning" v-if="selectedWordInfo.definition || selectedWordInfo.translation">
            <div class="meaning-content">
              {{ selectedWordInfo.definition || selectedWordInfo.translation }}
            </div>
          </div>

<!--          &lt;!&ndash; 词性标签 &ndash;&gt;-->
<!--          <div class="pos-info" v-if="selectedWordInfo.pos">-->
<!--            <el-tag size="small" type="info">{{ getPosText(selectedWordInfo.pos) }}</el-tag>-->
<!--          </div>-->

<!--          &lt;!&ndash; 同义词 &ndash;&gt;-->
<!--          <div class="synonyms-section" v-if="selectedWordInfo.synonyms && selectedWordInfo.synonyms.length > 0">-->
<!--            <div class="synonyms">-->
<!--              <span-->
<!--                v-for="synonym in selectedWordInfo.synonyms.slice(0, 6)"-->
<!--                :key="synonym"-->
<!--                class="synonym-tag"-->
<!--                @click="handleSynonymClick(synonym)"-->
<!--              >-->
<!--                {{ synonym }}-->
<!--              </span>-->
<!--            </div>-->
<!--          </div>-->
        </div>

        <div class="word-footer">
          <el-button
            type="primary"
            :loading="addingToVocabulary"
            :disabled="isInVocabulary"
            @click="addToVocabulary"
          >
            {{ isInVocabulary ? '已在生词本' : '添加到生词本' }}
          </el-button>
          <el-button @click="handleDialogClose">关闭</el-button>
        </div>
      </div>

      <div class="loading-content" v-else-if="loadingWord">
        <el-icon class="is-loading" :size="40"><Loading /></el-icon>
        <p style="margin-top: 10px; color: #666;">加载中...</p>
      </div>

      <div class="error-content" v-else>
        <el-result
          icon="error"
          title="加载失败"
          :sub-title="errorMessage"
        ></el-result>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, watch, onMounted, onBeforeUnmount } from 'vue'
import { ElMessage } from 'element-plus'
import { Loading } from '@element-plus/icons-vue'
import request from '@/utils/request'

// Props
const props = defineProps({
  text: {
    type: String,
    default: ''
  },
  // 课文ID，用于记录学习进度
  textId: {
    type: Number,
    default: 0
  },
  // 单元ID，用于记录学习进度
  unitId: {
    type: Number,
    default: 0
  }
})

// Emits
const emit = defineEmits(['wordAssist', 'learningRecord', 'word-click'])

// Reactive data
const paragraphs = ref([])
const showWordDialog = ref(false)
const selectedWord = ref(null)
const selectedWordInfo = ref(null)
const loadingWord = ref(false)
const errorMessage = ref('')
const addingToVocabulary = ref(false)
const isInVocabulary = ref(false)

// 翻译相关
const showTranslateDialog = ref(false)
const selectedText = ref('')
const translatedText = ref('')
const translating = ref(false)
const isSelectingMode = ref(false)

// TTS 语音合成相关
const ttsLoading = ref(false)
const ttsCurrentText = ref('')
let ttsAudio = null

// 浮动按钮位置和拖动（避免 SSR/初始化阶段访问 window 导致错误）
const floatingBtnPosition = reactive({
  x: typeof window !== 'undefined' ? window.innerWidth - 80 : 295,
  y: typeof window !== 'undefined' ? window.innerHeight / 2 : 300
})
const isDragging = ref(false)
const dragOffset = reactive({ x: 0, y: 0 })

// 生词本状态缓存
const vocabularyStatusCache = reactive(new Map())

// 追踪活跃的拖动事件处理器，防止内存泄漏
let activeDragHandlers = null

// 追踪同义词点击的延迟计时器，防止内存泄漏
let synonymClickTimers = []

// 追踪 handleDialogClose 的 setTimeout timer
let dialogCloseTimer = null


// 监听文本变化，更新段落和单词
watch(() => props.text, (newText) => {
  if (newText) {
    processText(newText)
  }
}, { immediate: true })

// 处理文本，分割为段落和单词
const processText = async (text) => {
  if (!text) return

  // 规范化文本：处理双序列化转义字符（存储时的 JSON 转义残留）
  text = text
    .replace(/\\n/g, '\n')
    .replace(/\\r/g, '')
    .replace(/\\t/g, '\t')
    .replace(/\\"/g, '"')
    .replace(/\\'/g, "'")
    .replace(/\\\\/g, '\\')

  // 简单处理：直接按段落分割，并让所有英文单词都可点击
  const processedParagraphs = text.split('\n\n').map(paragraph => {
    const words = []
    let position = 0
    const wordRegex = /\b([a-zA-Z']+)\b/g
    let match

    while ((match = wordRegex.exec(paragraph)) !== null) {
      const wordText = match[0]
      
      // FIX #1: 增强字符串验证，防止特殊字符导致问题
      if (!wordText || typeof wordText !== 'string' || wordText.trim().length === 0) {
        continue
      }
      const cleanWord = wordText.toLowerCase().replaceAll("'", "")

      // 添加单词前的非单词文本
      if (match.index > position) {
        const nonWordText = paragraph.substring(position, match.index)
        if (nonWordText) {
          words.push({
            text: nonWordText,
            clickable: false,
            cleanWord: '',
            position: position,
            isClicked: false
          })
        }
      }

      // 添加单词
      words.push({
        text: wordText,
        cleanWord: cleanWord,
        clickable: cleanWord.length > 1, // 长度大于1的英文单词都可点击
        position: match.index,
        isClicked: false
      })

      position = match.index + wordText.length
    }

    // 添加剩余的非单词文本
    if (position < paragraph.length) {
      const remainingText = paragraph.substring(position)
      if (remainingText) {
        words.push({
          text: remainingText,
          clickable: false,
          cleanWord: '',
          position: position,
          isClicked: false
        })
      }
    }

    return { words }
  })

  paragraphs.value = processedParagraphs
}


// 浮动按钮拖动
const startDrag = (event) => {
  // 清理旧的事件监听器（如果存在）
  if (activeDragHandlers) {
    document.removeEventListener('mousemove', activeDragHandlers.move)
    document.removeEventListener('mouseup', activeDragHandlers.end)
    document.removeEventListener('touchmove', activeDragHandlers.move)
    document.removeEventListener('touchend', activeDragHandlers.end)
    activeDragHandlers = null
  }

  // 支持鼠标和触摸
  const isTouch = event.type === 'touchstart'
  if (!isTouch && event.button !== 0) return // 鼠标只响应左键

  const startX = isTouch ? event.touches[0].clientX : event.clientX
  const startY = isTouch ? event.touches[0].clientY : event.clientY
  const startTime = Date.now()
  let hasMoved = false

  isDragging.value = true

  dragOffset.x = startX - floatingBtnPosition.x
  dragOffset.y = startY - floatingBtnPosition.y

  if (isTouch) {
    event.preventDefault()
  }

  const handleMove = (e) => {
    if (isDragging.value) {
      const moveX = e.type === 'touchmove' ? e.touches[0].clientX : e.clientX
      const moveY = e.type === 'touchmove' ? e.touches[0].clientY : e.clientY

      // 如果移动距离超过5px，认为是拖动
      const deltaX = Math.abs(moveX - startX)
      const deltaY = Math.abs(moveY - startY)
      if (deltaX > 5 || deltaY > 5) {
        hasMoved = true
      }

      floatingBtnPosition.x = moveX - dragOffset.x
      floatingBtnPosition.y = moveY - dragOffset.y
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
    activeDragHandlers = null

    // 如果没有移动，且时间很短（<300ms），认为是点击
    if (!hasMoved && duration < 300) {
      isDragging.value = false
      // 触发点击事件
      handleFloatingBtnClick(e)
    } else {
      // 延迟重置，避免立即触发点击
      isDragging.value = false
    }
  }

  document.addEventListener('mousemove', handleMove)
  document.addEventListener('mouseup', handleEnd)
  document.addEventListener('touchmove', handleMove, { passive: false })
  document.addEventListener('touchend', handleEnd)
  activeDragHandlers = { move: handleMove, end: handleEnd }
}

// 处理浮动按钮点击
// 处理浮动按钮点击
const handleFloatingBtnClick = () => {
  // 如果刚拖动过，不触发点击
  if (isDragging.value) {
    return
  }
  
  // 获取当前选中的文本
  const selection = window.getSelection()
  const text = selection.toString().trim()
  
  if (text && text.length > 0) {
    // 如果已经选中了文本，直接翻译
    selectedText.value = text
    translateSelectedText()
  } else {
    // 如果没有选中文本，提示用户
    ElMessage.warning('请先选择要翻译的文本')
  }
}

// 翻译选中的文本
const translateSelectedText = async () => {
  if (!selectedText.value) return

  showTranslateDialog.value = true
  translating.value = true
  translatedText.value = ''

  try {
    const token = localStorage.getItem('token')
    const response = await fetch('/api/translate/auto', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        ...(token ? { 'Authorization': `Bearer ${token}` } : {})
      },
      body: JSON.stringify({ text: selectedText.value })
    })

    if (response.ok) {
      const result = await response.json()
      if (result.code === 200) {
        translatedText.value = result.data || '翻译结果为空'
      } else {
        translatedText.value = '翻译服务暂时不可用，请稍后重试'
      }
    } else {
      translatedText.value = '翻译服务暂时不可用'
    }
  } catch (error) {
    console.error('翻译失败:', error)
    translatedText.value = '翻译服务暂时不可用'
  } finally {
    translating.value = false
  }
}

// TTS 语音合成
const speakText = async (text) => {
  if (!text || text.trim().length === 0) return
  stopTtsPlayback()
  ttsLoading.value = true
  ttsCurrentText.value = text
  try {
    const token = localStorage.getItem('token')
    const response = await fetch('/api/tts/synthesize', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        ...(token ? { 'Authorization': `Bearer ${token}` } : {})
      },
      body: JSON.stringify({ text: text.trim() })
    })
    if (response.ok) {
      const result = await response.json()
      if (result.code === 200 && result.data?.audioUrl) {
        ttsAudio = new Audio(result.data.audioUrl)
        ttsAudio.addEventListener('ended', () => { ttsCurrentText.value = '' })
        ttsAudio.addEventListener('error', () => { ttsCurrentText.value = ''; ElMessage.error('音频播放失败') })
        await ttsAudio.play()
      } else {
        ElMessage.error(result.message || '语音合成失败')
      }
    } else {
      ElMessage.error('语音合成服务暂不可用')
    }
  } catch (error) {
    console.error('TTS failed:', error)
  } finally {
    ttsLoading.value = false
  }
}

const stopTtsPlayback = () => {
  if (ttsAudio) { ttsAudio.pause(); ttsAudio = null }
  ttsCurrentText.value = ''
}

// 处理单词点击
const handleWordClick = async (word) => {
  if (!word || !word.clickable) {
    return
  }

  // 用户正在拖选文本时不弹词义弹窗
  const selection = window.getSelection()
  if (selection && !selection.isCollapsed) return

  // 标记单词为已点击
  word.isClicked = true
  selectedWord.value = word

  // 显示弹窗并开始加载
  showWordDialog.value = true
  loadingWord.value = true
  errorMessage.value = ''

  try {
    // 获取单词信息 - 使用 request 拦截器
    const result = await request({
      url: `/api/wordnet/word/${word.cleanWord}`,
      method: 'get'
    })

    if (result && typeof result === 'object' && result.code === 200) {
      selectedWordInfo.value = result.data || {}
      // console.log('Selected word info:', selectedWordInfo.value)

      // 检查是否已在生词本中
      await checkVocabularyStatus(word.cleanWord)

      // 发送单词辅助学习事件（不记录到任务系统）
      handleWordAssist(word)
    } else {
      console.error('WordNet API returned non-200 code:', result)
      // 创建基本的单词信息作为备用
      selectedWordInfo.value = {
        word: word.cleanWord,
        definition: "正在获取词汇信息...",
        found: false,
        synonyms: [],
        antonyms: [],
        definitions: []
      }
    }
  } catch (error) {
    console.error('获取单词信息失败:', error)
    errorMessage.value = typeof error === 'string' ? error : error?.message || '获取单词信息失败'
    // 创建基本的单词信息作为备用
    selectedWordInfo.value = {
      word: word.cleanWord,
      definition: "服务暂时不可用，请稍后重试",
      found: false,
      synonyms: [],
      antonyms: [],
      definitions: []
    }
  } finally {
    loadingWord.value = false
  }
}

// 检查生词本状态
const checkVocabularyStatus = async (word) => {
  if (vocabularyStatusCache.has(word)) {
    isInVocabulary.value = vocabularyStatusCache.get(word)
    return
  }

  try {
    const result = await request({
      url: `/api/student/learning/vocabulary/check`,
      method: 'get',
      params: { word: word }
    })

    if (result && typeof result === 'object' && result.code === 200) {
      isInVocabulary.value = !!result.data
      vocabularyStatusCache.set(word, isInVocabulary.value)
    }
  } catch (error) {
    console.error('检查生词本状态失败:', error)
    isInVocabulary.value = false
  }
}

// 发送单词学习记录
const handleWordAssist = (word) => {
  emit('wordAssist', {
    word: word.cleanWord,
    position: word.position,
    textId: props.textId,
    unitId: props.unitId
  })
  emit('word-click', { word: word.cleanWord })

  // 单词辅助学习不记录到任务系统，只作为学习辅助功能
  // emit('learningRecord', {
  //   type: 'WORD_VIEW',
  //   wordId: null,
  //   word: word.cleanWord,
  //   duration: 0
  // })
}

// 添加到生词本（支持自动创建新词汇）
const addToVocabulary = async () => {
  if (!selectedWordInfo.value || addingToVocabulary.value) return

  addingToVocabulary.value = true

  try {
    // 使用新的自动创建API
    const result = await request({
      url: '/api/student/words/vocabulary/by-word',
      method: 'POST',
      data: {
        word: selectedWord.value.cleanWord
      }
    })

    if (result.code === 200) {
      const wordData = result.data

      // 根据返回的消息显示不同的提示
      if (wordData.vocabularyAction === 'added') {
        if (wordData.created) {
          ElMessage.success(wordData.message || '新词汇已创建并添加到生词本')
        } else {
          ElMessage.success(wordData.message || '已添加到生词本')
        }
      } else if (wordData.vocabularyAction === 'updated') {
        ElMessage.info(wordData.message || '单词学习状态已更新')
      }

      isInVocabulary.value = true
      vocabularyStatusCache.set(selectedWord.value.cleanWord, true)

      // 如果是新创建的词汇，更新显示的词汇信息
      if (wordData.created) {
        selectedWordInfo.value = wordData
      }

    } else {
      ElMessage.warning(result.message || '添加失败')
    }
  } catch (error) {
    console.error('添加到生词本失败:', error)
    ElMessage.error('添加到生词本失败')
  } finally {
    addingToVocabulary.value = false
  }
}

// 处理弹窗关闭
const handleDialogClose = () => {
  showWordDialog.value = false
  // 清理旧的 timer
  if (dialogCloseTimer) {
    clearTimeout(dialogCloseTimer)
  }
  dialogCloseTimer = setTimeout(() => {
    // 重置被点击单词的 isClicked 状态，恢复正常样式
    if (selectedWord.value) {
      selectedWord.value.isClicked = false
    }
    selectedWord.value = null
    selectedWordInfo.value = null
    errorMessage.value = ''
    dialogCloseTimer = null
  }, 300)
}

// 初始化
onMounted(() => {
  if (props.text) {
    processText(props.text)
  }
})

// 清理事件监听器防止内存泄漏
onBeforeUnmount(() => {
  // 清理 TTS
  stopTtsPlayback()
  // 保证所有拖动相关事件监听器被清理
  if (activeDragHandlers) {
    document.removeEventListener('mousemove', activeDragHandlers.move)
    document.removeEventListener('mouseup', activeDragHandlers.end)
    document.removeEventListener('touchmove', activeDragHandlers.move)
    document.removeEventListener('touchend', activeDragHandlers.end)
    activeDragHandlers = null
  }

  // 清理同义词点击的延迟计时器
  if (Array.isArray(synonymClickTimers)) {
    synonymClickTimers.forEach(timerId => clearTimeout(timerId))
    synonymClickTimers = []
  }

  // 清理弹窗关闭的延迟计时器
  if (dialogCloseTimer) {
    clearTimeout(dialogCloseTimer)
    dialogCloseTimer = null
  }

  // 确保不处于拖动状态
  if (isDragging.value) {
    isDragging.value = false
  }
})
</script>

<style>
/* 全局弹窗样式 - 不受 scoped 限制 */
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
</style>

<style scoped>
.interactive-text {
  font-family: 'Georgia', serif;
  line-height: 1.8;
  color: #333;
  /* Safari: prevent text selection quirks */
  -webkit-text-size-adjust: 100%;
}

.text-content {
  background: #fff;
  padding: 24px;
  border-radius: 8px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.1);
  overflow-x: auto; /* 防止超长单词/行溢出容器 */
  word-break: break-word; /* 英语长单词自动换行 */
  /* 确保文本始终可选中 */
  user-select: text;
  -webkit-user-select: text;
  -webkit-touch-callout: default;
  -webkit-tap-highlight-color: transparent;
  touch-action: manipulation;
}

.text-paragraph {
  margin-bottom: 16px;
  text-align: justify;
  user-select: text;
  -webkit-user-select: text;
  -webkit-touch-callout: default;
  -webkit-tap-highlight-color: transparent;
}

.text-paragraph:last-child {
  margin-bottom: 0;
}

.word {
  /* 使用 inline 而非 inline-block，保持与纯文本节点相同的基线对齐，
     避免 span 和文本节点在划线时出现高低不一的问题 */
  display: inline;
  border-radius: 3px;
  transition: background-color 0.2s ease, color 0.2s ease;
  /* 不设置 margin/padding，保持自然文本流 */
}

.word.clickable {
  cursor: pointer;
}

.word.clickable:hover {
  background: rgba(24, 144, 255, 0.1);
  /* 移除 transform: translateY，inline 元素无法垂直位移且会破坏行高 */
}

.word.clicked {
  background: rgba(24, 144, 255, 0.2);
  color: #0050b3;
  font-weight: 500;
}

/* 弹窗样式 */
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

/* 主要释义区域 */
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

/* 词性信息 */
.pos-info {
  text-align: center;
  margin-bottom: 12px;
}

/* 同义词区域 */
.synonyms-section {
  margin-bottom: 16px;
}

.synonyms {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  justify-content: center;
}

.synonym-tag {
  background: #e6f7ff;
  color: #1890ff;
  padding: 4px 10px;
  border-radius: 16px;
  font-size: 13px;
  cursor: pointer;
  transition: all 0.2s ease;
  border: 1px solid #91d5ff;
}

.synonym-tag:hover {
  background: #1890ff;
  color: white;
  transform: scale(1.05);
}


.word-footer {
  text-align: center;
  padding-top: 8px;
  border-top: 1px solid #f0f0f0;
  margin-top: 8px;
}

.loading-content {
  text-align: center;
  padding: 40px 0;
}

.error-content {
  text-align: center;
  padding: 20px 0;
}

/* 响应式设计 */
@media (max-width: 768px) {
  .text-content {
    padding: 16px;
  }

  .word-title {
    font-size: 16px;
  }

  .pronunciation {
    font-size: 11px;
  }

  .pronunciation {
    font-size: 11px;
  }

  .word-dialog :deep(.el-dialog__body) {
    padding: 0 8px 8px 8px;
  }

  .word-dialog :deep(.el-dialog__header) {
    padding: 6px 10px 2px 10px;
  }

  .word-dialog :deep(.el-dialog__title) {
    font-size: 13px;
  }

  .word-header {
    padding: 4px 0 0 0;
    margin-bottom: 6px;
  }

  .word-body {
    max-height: 18vh;
    padding: 0;
  }

  .meaning-content {
    font-size: 12px;
    padding: 4px 6px;
    line-height: 1.2;
  }

  .synonym-tag {
    font-size: 9px;
    padding: 1px 3px;
  }

  .word-footer {
    padding-top: 6px;
    margin-top: 6px;
  }

  .word-footer .el-button {
    font-size: 11px;
    padding: 3px 6px;
  }
}

/* 超小屏幕适配 */
@media (max-width: 480px) {
  .word-title {
    font-size: 14px;
  }

  .pronunciation {
    font-size: 10px;
  }

  .pronunciation {
    font-size: 10px;
  }

  .word-dialog :deep(.el-dialog__body) {
    padding: 0 6px 6px 6px;
  }

  .word-dialog :deep(.el-dialog__header) {
    padding: 4px 8px 0 8px;
  }

  .word-dialog :deep(.el-dialog__title) {
    font-size: 12px;
  }

  .word-header {
    padding: 2px 0 0 0;
    margin-bottom: 4px;
  }

  .meaning-content {
    font-size: 11px;
    padding: 3px 4px;
    line-height: 1.1;
  }

  .word-body {
    max-height: 15vh;
    padding: 0;
  }

  .synonym-tag {
    font-size: 8px;
    padding: 1px 2px;
  }

  .word-footer {
    padding-top: 4px;
    margin-top: 4px;
  }

  .word-footer .el-button {
    font-size: 10px;
    padding: 2px 4px;
  }
}

/* 浮动翻译按钮样式 */
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

/* 自定义 FAB 按钮 */
.translate-fab {
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

.translate-fab::before {
  content: '';
  position: absolute;
  inset: 0;
  border-radius: inherit;
  opacity: 0;
  transition: opacity 0.2s;
  background: rgba(255, 255, 255, 0.15);
}

.translate-fab:hover::before { opacity: 1; }
.translate-fab:active { transform: scale(0.94) !important; }

.fab-default {
  background: linear-gradient(135deg, #1890ff 0%, #096dd9 100%);
  box-shadow: 0 4px 14px rgba(24, 144, 255, 0.5), 0 0 0 3px rgba(24, 144, 255, 0.15);
}

.fab-active {
  background: linear-gradient(135deg, #52c41a 0%, #389e0d 100%);
  box-shadow: 0 4px 14px rgba(82, 196, 26, 0.5), 0 0 0 3px rgba(82, 196, 26, 0.15);
  animation: pulse-active 1.5s ease-in-out infinite;
}

.fab-icon {
  font-size: 20px;
  font-weight: 800;
  color: #fff;
  line-height: 1;
  letter-spacing: -0.5px;
}

.fab-label {
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

@keyframes pulse-active {
  0%, 100% {
    box-shadow: 0 4px 14px rgba(82, 196, 26, 0.5), 0 0 0 3px rgba(82, 196, 26, 0.15);
  }
  50% {
    box-shadow: 0 6px 20px rgba(82, 196, 26, 0.65), 0 0 0 6px rgba(82, 196, 26, 0.1);
  }
}

/* 移动端适配 */
@media (max-width: 768px) {
  .translate-fab {
    width: 52px;
    height: 52px;
    border-radius: 14px;
  }
  .fab-icon { font-size: 18px; }
  .fab-label { font-size: 8px; }
}

@media (max-width: 480px) {
  .translate-fab {
    width: 48px;
    height: 48px;
    border-radius: 12px;
  }
  .fab-icon { font-size: 17px; }
  .fab-label { display: none; }
}

/* 翻译弹窗样式 */
.translate-content {
  padding: 16px 0;
}

.original-text, .translated-text {
  margin-bottom: 12px;
}

.original-text h4, .translated-text h4 {
  margin: 0 0 8px 0;
  color: #666;
  font-size: 14px;
  font-weight: 600;
}

.original-text p, .translated-text p {
  max-height: 350px;
  overflow-y: auto;
  margin: 0;
  padding: 12px;
  background: #f8f9fa;
  border-radius: 6px;
  line-height: 1.6;
  color: #333;
}

.translated-text p {
  background: #e6f7ff;
  border-left: 3px solid #1890ff;
}

@media (max-width: 768px) {
  .translate-dialog :deep(.el-dialog) {
    width: 92vw !important;
    max-width: 92vw !important;
    margin: 0 auto;
  }
  .translate-dialog :deep(.el-dialog__body) {
    padding: 8px 12px 12px;
  }
  .translate-content {
    padding: 0;
    display: flex;
    flex-direction: column;
    gap: 8px;
    max-height: 65vh;
  }
  .original-text, .translated-text {
    flex: 1;
    min-height: 0;
    display: flex;
    flex-direction: column;
    margin-bottom: 0;
  }
  .text-scroll-area {
    flex: 1;
    min-height: 0;
    overflow-y: auto;
    -webkit-overflow-scrolling: touch;
  }
  .original-text p, .translated-text p {
    padding: 8px;
    font-size: 14px;
    word-break: break-word;
    margin: 0;
  }
}

/* TTS 内联按钮 */
.text-with-tts {
  display: flex;
  align-items: center;
  gap: 8px;
}
.text-with-tts h4 { margin: 0; }

/* 单词弹窗标题行（单词 + 播放按钮） */
.word-title-row {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
}
.word-speak-btn {
  flex-shrink: 0;
}

.tts-inline-btn {
  width: 28px; height: 28px;
  border-radius: 50%;
  border: 1px solid #e2e8f0;
  background: #f8fafc;
  color: #64748b;
  cursor: pointer;
  display: flex; align-items: center; justify-content: center;
  font-size: 14px;
  transition: all 0.2s;
  flex-shrink: 0;
}
.tts-inline-btn:hover {
  background: #eff6ff;
  border-color: #3b82f6;
  color: #3b82f6;
}
.tts-inline-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
</style>