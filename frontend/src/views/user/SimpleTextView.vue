<template>
  <div class="simple-text-view">
    <!-- 页面头部 -->
    <div class="page-header">
      <el-breadcrumb separator="/">
        <el-breadcrumb-item>
          <a :href="breadcrumbParent.path">{{ breadcrumbParent.label }}</a>
        </el-breadcrumb-item>
        <el-breadcrumb-item>{{ textTitle }}</el-breadcrumb-item>
      </el-breadcrumb>

      <div class="header-actions">
        <el-button @click="goBack" type="default">
          <el-icon><ArrowLeft /></el-icon>
          返回
        </el-button>

        <el-select
          v-model="fontSize"
          placeholder="字号"
          style="width: 100px; margin-left: 10px;"
          @change="updateFontSize"
        >
          <el-option label="小" value="small" />
          <el-option label="中" value="medium" />
          <el-option label="大" value="large" />
        </el-select>
      </div>
    </div>

    <!-- 课文信息卡片 -->
    <el-card class="text-info-card">
      <div class="text-info-header">
        <div class="text-info">
          <h1 class="text-title">{{ textTitle }}</h1>
          <div class="text-meta">
            <el-tag :type="getDifficultyType(difficulty)" size="small">
              {{ getDifficultyText(difficulty) }}
            </el-tag>
            <span class="word-count">
              <el-icon><Document /></el-icon> {{ wordCount }} 个单词
            </span>
            <span class="view-count">
              <el-icon><View /></el-icon> {{ viewCount || 0 }} 次浏览
            </span>
          </div>
        </div>
      </div>
    </el-card>

    <!-- 版本选择器 -->
    <VersionSelector
      v-if="versions && Object.keys(versions).length > 1"
      :versions="versions"
      :selected-version="selectedVersion"
      @version-change="handleVersionChange"
    />

    <!-- 课文内容 -->
    <el-card class="text-content-card">
      <div
        class="text-content"
        :class="`font-size-${fontSize}`"
        v-html="processedContent"
        @click="handleTextClick"
      ></div>
    </el-card>

    <!-- 翻译结果弹窗 -->
    <el-dialog
      v-model="showTranslateDialog"
      title="翻译结果"
      :width="dialogWidth"
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

    <!-- 单词弹窗 -->
    <el-dialog
      v-model="showWordDialog"
      title="单词详情"
      :width="dialogWidth"
      custom-class="word-dialog"
      @closed="resetWordDialog"
    >
      <div class="word-content" v-if="selectedWordInfo">
        <div class="word-header">
          <h2 class="word-title">{{ selectedWordInfo?.word || '未知单词' }}</h2>
          <div class="pronunciation" v-if="selectedWordInfo?.phonetic">
            {{ selectedWordInfo?.phonetic }}
          </div>
        </div>

        <div class="word-body">
          <!-- 主要释义：优先显示中文翻译，其次英文定义 -->
          <div class="main-meaning" v-if="selectedWordInfo?.translation || selectedWordInfo?.definition">
            <div class="meaning-content">
              {{ selectedWordInfo?.translation || selectedWordInfo?.definition }}
            </div>
          </div>

          <!-- 词性标签 -->
          <div class="pos-info" v-if="selectedWordInfo?.pos && getPosText(selectedWordInfo?.pos)">
            <el-tag size="small" type="info">{{ getPosText(selectedWordInfo?.pos) }}</el-tag>
          </div>

          <!-- 同义词 -->
          <div class="synonyms-section" v-if="Array.isArray(selectedWordInfo?.synonyms) && selectedWordInfo?.synonyms.length > 0">
            <div class="synonyms">
              <span
                v-for="synonym in selectedWordInfo?.synonyms.slice(0, 6)"
                :key="synonym"
                class="synonym-tag"
                @click="handleSynonymClick(synonym)"
              >
                {{ synonym }}
              </span>
            </div>
          </div>
        </div>

        <div class="word-footer">
          <el-button @click="showWordDialog = false">关闭</el-button>
        </div>
      </div>

      <div class="loading-content" v-else-if="loadingWord">
        <div style="text-align: center; padding: 40px 0;">
          <el-icon class="is-loading" style="font-size: 24px;">
            <Loading />
          </el-icon>
          <p style="margin-top: 10px; color: #666;">加载中...</p>
        </div>
      </div>

      <div class="error-content" v-else>
        <el-result
          icon="error"
          title="加载失败"
          sub-title="请稍后重试"
        ></el-result>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, onUnmounted, computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowLeft, Document, View, VideoPlay, Loading } from '@element-plus/icons-vue'
import request from '@/utils/request'
import VersionSelector from '@/components/VersionSelector.vue'
import { marked } from 'marked'
import DOMPurify from 'dompurify'
import { getAuthHeader } from '@/utils/auth'
import { getArticlesPath } from '@/config/site'

void VideoPlay

const router = useRouter()
const route = useRoute()

const textId = ref(route.params.id || route.query.textId)

// 面包屑导航：根据来源决定父级链接
const breadcrumbParent = computed(() => {
  if (route.query.from === 'articles') {
    return { path: getArticlesPath(), label: '文章推荐', external: true }
  }
  return { path: getArticlesPath(), label: '文章', external: true }
})

const textTitle = ref('')
const textContent = ref('')
const difficulty = ref('')
const wordCount = ref(0)
const viewCount = ref(0)
const fontSize = ref('medium')
const versions = ref({})
const selectedVersion = ref('original')

const showWordDialog = ref(false)
const selectedWord = ref(null)
const selectedWordInfo = ref(null)
const loadingWord = ref(false)

// 翻译相关
const showTranslateDialog = ref(false)
const selectedTextForTranslate = ref('')
const translatedText = ref('')
const translating = ref(false)
const isSelectingMode = ref(false)

// 浮动按钮位置和拖动
const floatingBtnPosition = reactive({ x: window.innerWidth - 80, y: window.innerHeight / 2 })
const isDragging = ref(false)
const dragOffset = reactive({ x: 0, y: 0 })

// 响应式窗口宽度
const windowWidth = ref(typeof window !== 'undefined' ? window.innerWidth : 1024)

// 判断是否为移动端
const isMobile = computed(() => windowWidth.value < 480)

// 响应式弹窗宽度
const dialogWidth = computed(() => {
  if (windowWidth.value < 768) return '92vw'
  return '400px'
})

// 监听窗口大小变化
const handleResize = () => {
  windowWidth.value = window.innerWidth
}

// 声明全局事件处理器变量，避免内存泄漏
let activeDragHandlers = { move: null, end: null }
let synonymClickTimers = []


// 处理文本选择
// 浮动按钮拖动（支持触摸）
const startDrag = (event) => {
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

    // 如果没有移动，且时间很短（<300ms），认为是点击
    if (!hasMoved && duration < 300) {
      isDragging.value = false
      // 触发点击事件
      handleFloatingBtnClick(e)
    } else {
      // 立即重置，避免导致其他问题
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
const handleFloatingBtnClick = () => {
  // 如果刚拖动过，不触发点击
  if (isDragging.value) {
    return
  }
  
  // 获取当前选中的文本
  const selection = window.getSelection()
  const text = selection?.toString()?.trim() || ''
  
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
      if (result?.code === 200) {
        translatedText.value = result?.data
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

// 获取课文详情
const loadTextDetail = async () => {
  if (!textId.value) {
    ElMessage.error('缺少课文 ID，无法加载')
    router.back()
    return
  }
  try {
    const response = await request({
      url: `/api/public/texts/${textId.value}`,
      method: 'get'
    })

    if (response.code === 200) {
      const data = response.data
      if (!data) {
        ElMessage.error('课文数据为空')
        return
      }
      textTitle.value = data?.title || ''
      difficulty.value = data?.difficulty || ''
      viewCount.value = data.viewCount ?? 0
      wordCount.value = data.wordCount || 0
      
      // 根据内容类型处理内容
      if (data.contentType === 'MARKDOWN' && data?.markdownContent) {
        // Markdown 格式，直接渲染 Markdown，不需要处理可点击单词
        textContent.value = DOMPurify.sanitize(marked(data?.markdownContent))
      } else {
        // 纯文本格式
        // 保存所有版本
        versions.value = data?.versions || {}
        
        // 设置默认显示原文版本
        if (versions.value.original) {
          textContent.value = versions.value.original
        } else {
          // 如果没有原文，使用第一个可用版本
          const firstVersion = Object.keys(versions.value)[0]
          if (firstVersion) {
            selectedVersion.value = firstVersion
            textContent.value = versions.value?.[firstVersion]
          }
        }
        
        // 只有纯文本格式才处理可点击单词
        processTextContent()
      }
    } else {
      ElMessage.error(response.message || '获取课文详情失败')
    }
  } catch (error) {
    ElMessage.error('获取课文详情失败')
    console.error(error)
  }
}

// 处理课文内容，添加可点击单词（对原始文本做 HTML 转义后再处理）
const processTextContent = () => {
  try {
    // 让所有英文单词都可点击，不依赖于本地单词列表
    let content = textContent.value
    if (!content) return

    // XSS防御：对原始文本先做HTML实体转义，再处理可点击单词
    const escapeHtml = (str) => str
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&#39;')

    let processedContent = []
    let lastIndex = 0

    // 使用正则表达式找到所有英文单词
    const wordRegex = /\b([a-zA-Z]+)\b/g
    let match

    while ((match = wordRegex.exec(content)) !== null) {
      // 添加单词前的文本（HTML转义）
      if (match.index > lastIndex) {
        processedContent.push(escapeHtml(content.substring(lastIndex, match.index)))
      }

      const wordText = match[1]
      const cleanWord = wordText.toLowerCase()

      // 所有长度大于1的英文单词都可点击
      if (cleanWord && cleanWord.length > 1) {
        processedContent.push(
          `<span class="clickable-word" data-word="${encodeURIComponent(cleanWord)}">${escapeHtml(wordText)}</span>`
        )
      } else {
        processedContent.push(escapeHtml(wordText))
      }

      lastIndex = match.index + wordText.length
    }

    // 添加剩余文本（HTML转义）
    if (lastIndex < content.length) {
      processedContent.push(escapeHtml(content.substring(lastIndex)))
    }

    textContent.value = processedContent.join('')
  } catch (error) {
    console.error('处理课文内容失败:', error)
  }
}

// 处理后的内容（支持点击单词）
const processedContent = computed(() => {
  return textContent.value
})

// 处理单词点击 - 使用类似学生端的API
const handleWordClick = async (wordText) => {
  if (!wordText) return
  
  const cleanWord = wordText.toLowerCase()
  selectedWord.value = { word: cleanWord, cleanWord: cleanWord }

  // 显示弹窗并开始加载
  showWordDialog.value = true
  loadingWord.value = true

  try {
    // 使用WordNet API获取单词详细信息，需要带上认证token
    const authHeaderValue = getAuthHeader()
    const response = await fetch(`/api/wordnet/word/${cleanWord}`, {
      headers: {
        ...(authHeaderValue ? { 'Authorization': authHeaderValue } : {}),
        'Content-Type': 'application/json'
      }
    })

    if (response.ok) {
      const result = await response.json()
      if (result?.code === 200) {
        const data = result?.data || {}
        // 修复：API 返回的 synonyms/antonyms 可能是 JSON 字符串而非数组
        let synonyms = data.synonymsList || data.synonyms || []
        if (typeof synonyms === 'string') {
          try { synonyms = JSON.parse(synonyms) } catch { synonyms = [] }
        }
        if (Array.isArray(synonyms)) {
          synonyms = synonyms.map(s => String(s).replace(/^s:/, '')).filter(s => s)
        } else {
          synonyms = []
        }
        // 过滤无效的 pos 和 phonetic
        const pos = (data.pos && data.pos.toLowerCase() !== 'unknown') ? data.pos : (data.partOfSpeech && data.partOfSpeech.toLowerCase() !== 'unknown' ? data.partOfSpeech : '')
        const phonetic = data.pronunciation || data.phonetic || ''
        selectedWordInfo.value = {
          ...data,
          synonyms,
          pos,
          phonetic: phonetic && phonetic.toLowerCase() !== 'unknown' ? phonetic : '',
        }
      } else {
        // 创建基本的单词信息作为备用
        selectedWordInfo.value = {
          word: cleanWord,
          definition: "暂无详细释义",
          found: false,
          synonyms: [],
          antonyms: [],
          definitions: []
        }
      }
    } else {
      // 创建基本的单词信息作为备用
      selectedWordInfo.value = {
        word: cleanWord,
        definition: "网络请求失败，请稍后重试",
        found: false,
        synonyms: [],
        antonyms: [],
        definitions: []
      }
    }
  } catch (error) {
    console.error('获取单词信息失败:', error)
    selectedWordInfo.value = {
      word: cleanWord,
      definition: "网络错误，未找到该单词",
      found: false,
      synonyms: [],
      antonyms: [],
      definitions: []
    }
  } finally {
    loadingWord.value = false
  }
}

// 事件委托：处理文本区域内的单词点击，替代 window.handleWordClick 全局函数
const handleTextClick = (event) => {
  const target = event?.target
  if (target && target.classList?.contains('clickable-word')) {
    const word = decodeURIComponent(target.getAttribute('data-word') || '')
    if (word) handleWordClick(word)
  }
}

// 为全局访问创建handleWordClick函数，确保在window上正确绑定
// 事件委托处理单词点击，避免 window 全局污染
onMounted(() => {
  // 监听窗口大小变化
  window.addEventListener('resize', handleResize)

  // 加载课文详情
  loadTextDetail()
})

// 处理同义词点击
const handleSynonymClick = async (synonym) => {
  if (!synonym) return
  
  // 关闭当前弹窗
  showWordDialog.value = false

  // 延迟一下再打开新弹窗，确保动画效果
  const timer = setTimeout(() => {
    selectedWord.value = {
      word: synonym,
      cleanWord: synonym.toLowerCase()
    }
    handleWordClick(synonym)
  }, 300)

  // 将 timer ID 保存以便清理
  if (!synonymClickTimers) {
    synonymClickTimers = []
  }
  synonymClickTimers.push(timer)
}

// 格式化词性显示
const getPosText = (pos) => {
  if (!pos || pos.toLowerCase() === 'unknown') return ''

  const posMap = {
    'n': '名词', 'v': '动词', 'adj': '形容词', 'adv': '副词',
    'prep': '介词', 'conj': '连词', 'pron': '代词', 'int': '感叹词'
  }

  const posList = pos.toLowerCase().split(/[\s,;]+/).filter(p => p && p !== 'unknown')
  if (posList.length === 0) return ''
  return posList.map(p => posMap[p] || p.toUpperCase()).join(' / ')
}

// 播放音频
let currentAudio = null

const playAudio = (audioUrl) => {
  if (!audioUrl) {
    ElMessage.warning('暂无音频可播放')
    return
  }

  // 停止播放当前音频
  if (currentAudio) {
    currentAudio.pause()
    currentAudio = null
  }

  try {
    currentAudio = new Audio(audioUrl)
    currentAudio.play().catch(error => {
      ElMessage.error('音频播放失败')
      console.error('音频播放错误:', error)
      currentAudio = null
    })
  } catch (error) {
    ElMessage.error('创建音频播放器失败')
    console.error('创建音频错误:', error)
    currentAudio = null
  }
}

void playAudio

// 重置单词弹窗
const resetWordDialog = () => {
  selectedWord.value = null
  selectedWordInfo.value = null
  loadingWord.value = false
}

// 更新字号
const updateFontSize = () => {
  // 字号变化会自动通过class应用
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

// 获取难度文本
const getDifficultyText = (difficulty) => {
  const texts = {
    'EASY': '简单',
    'MEDIUM': '中等',
    'HARD': '困难'
  }
  return texts[difficulty] || ''
}

// 返回上一页 — 优先跳转到来源页，避免跳回登录页
const goBack = () => {
  const from = route.query.from
  if (from === 'articles') {
    window.location.href = getArticlesPath()
  } else if (window.history.length > 1) {
    router.back()
  } else {
    // 无历史记录时，跳转到公开文章页
    window.location.href = getArticlesPath()
  }
}

// 处理版本切换
const handleVersionChange = (versionKey) => {
  selectedVersion.value = versionKey
  textContent.value = versions.value?.[versionKey] || ''
  // 重新处理内容
  processTextContent()
  // 上报版本阅读记录（仅登录用户，fire-and-forget）
  if (textId.value && versionKey !== 'original') {
    request({
      url: `/api/public/texts/${textId.value}/version-read`,
      method: 'post',
      params: { version: versionKey }
    }).catch(() => {})
  }
}

// 清理事件监听
onUnmounted(() => {
  // 清理拖动事件监听
  if (activeDragHandlers?.move && activeDragHandlers?.end) {
    document.removeEventListener('mousemove', activeDragHandlers.move)
    document.removeEventListener('mouseup', activeDragHandlers.end)
    document.removeEventListener('touchmove', activeDragHandlers.move)
    document.removeEventListener('touchend', activeDragHandlers.end)
    activeDragHandlers = { move: null, end: null }
  }

  // 清理同义词点击的延迟计时器
  if (Array.isArray(synonymClickTimers) && synonymClickTimers.length > 0) {
    synonymClickTimers.forEach(timerId => {
      if (timerId !== null && timerId !== undefined) {
        clearTimeout(timerId)
      }
    })
    synonymClickTimers = []
  }

  // 清理窗口大小变化监听
  window.removeEventListener('resize', handleResize)

  // 清理音频对象
  if (currentAudio) {
    currentAudio.pause()
    currentAudio.onended = null
    currentAudio.onerror = null
    currentAudio = null
  }
})
</script>

<style scoped>
.simple-text-view {
  padding: 0;
  background-color: #f5f5f5;
  height: 100vh;
  overflow-y: auto;
  -webkit-overflow-scrolling: touch;
}

.page-header {
  background: white;
  padding: 20px;
  border-bottom: 1px solid #e6e6e6;
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

.text-info-card {
  margin-bottom: 20px;
}

.text-info-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
}

.text-title {
  margin: 0 0 10px 0;
  color: #333;
  font-size: 24px;
  font-weight: 600;
}

.text-meta {
  display: flex;
  gap: 15px;
  align-items: center;
  flex-wrap: wrap;
}

.word-count, .view-count {
  display: flex;
  align-items: center;
  gap: 4px;
  color: #666;
  font-size: 14px;
}

.text-content-card {
  background: white;
}

.text-content {
  line-height: 2;
  color: #333;
  padding: 20px;
}

.text-content :deep(ul),
.text-content :deep(ol) {
  margin: 0 0 24px 0;
  padding-left: 24px;
}

.text-content :deep(li) {
  margin-bottom: 8px;
}

.text-content :deep(li p) {
  margin-bottom: 8px;
}

.text-content.font-size-small {
  font-size: 14px;
}

.text-content.font-size-medium {
  font-size: 16px;
}

.text-content.font-size-large {
  font-size: 18px;
}

.clickable-word {
  background-color: #e6f7ff;
  border: 1px solid #91d5ff;
  border-radius: 3px;
  padding: 2px 4px;
  margin: 0 2px;
  cursor: pointer;
  color: #1890ff;
  font-weight: 500;
  transition: all 0.3s;
}

.clickable-word:hover {
  background-color: #1890ff;
  color: white;
  border-color: #1890ff;
}

.word-detail {
  padding: 10px 0;
}

.word-header {
  text-align: center;
  margin-bottom: 20px;
  padding-bottom: 20px;
  border-bottom: 1px solid #f0f0f0;
}

.word-text {
  margin: 0;
  color: #333;
  font-size: 28px;
  font-weight: 600;
}

.word-phonetic {
  margin: 10px 0 0;
  color: #666;
  font-size: 16px;
  font-style: italic;
}

.word-body {
  space-y: 20px;
}

.word-section {
  margin-bottom: 20px;
}

.word-section h3 {
  margin: 0 0 10px;
  color: #333;
  font-size: 16px;
  font-weight: 600;
}

.word-definition,
.word-translation,
.word-example {
  margin: 0;
  color: #666;
  line-height: 1.6;
  padding: 10px;
  background-color: #f8f9fa;
  border-radius: 4px;
}

/* 单词弹窗样式 */
.word-header {
  text-align: center;
  padding: 16px 0 8px 0;
  border-bottom: 1px solid #f0f0f0;
  margin-bottom: 16px;
}

.word-title {
  margin: 0 0 6px 0;
  font-size: 24px;
  font-weight: 600;
  color: #1890ff;
}

.pronunciation {
  font-size: 16px;
  color: #666;
  font-family: 'Lucida Sans Unicode', 'Arial Unicode MS', Arial;
}

.word-body {
  max-height: 300px;
  overflow-y: auto;
  padding: 0 8px;
}

/* 主要释义区域 */
.main-meaning {
  text-align: center;
  margin-bottom: 16px;
}

.meaning-content {
  font-size: 16px;
  line-height: 1.5;
  color: #2c3e50;
  background: #f8f9fa;
  padding: 12px 16px;
  border-radius: 8px;
  border-left: 4px solid #1890ff;
  display: inline-block;
  max-width: 100%;
  text-align: left;
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
  padding-top: 20px;
  border-top: 1px solid #f0f0f0;
  margin-top: 20px;
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
  .page-header {
    flex-direction: column;
    gap: 15px;
    align-items: stretch;
  }

  .text-info-header {
    flex-direction: column;
    gap: 15px;
  }

  .text-title {
    font-size: 20px;
  }

  .text-meta {
    justify-content: flex-start;
  }

  .header-actions {
    justify-content: center;
  }

  .word-title {
    font-size: 22px;
  }

  .pronunciation {
    font-size: 14px;
  }

  .word-body {
    max-height: 40vh;
    padding: 0 4px;
  }

  .meaning-content {
    font-size: 15px;
    padding: 10px 12px;
  }

  .synonym-tag {
    font-size: 12px;
    padding: 3px 8px;
  }
}

/* 超小屏幕适配 */
@media (max-width: 480px) {
  .word-title {
    font-size: 20px;
  }

  .meaning-content {
    font-size: 14px;
    padding: 8px 10px;
  }

  .word-body {
    max-height: 50vh;
    overflow-y: auto;
  }

  .synonym-tag {
    font-size: 11px;
    padding: 2px 6px;
  }

  /* 移动端弹窗优化 */
  .word-header {
    padding: 8px 0;
  }

  .word-footer {
    padding-top: 12px;
  }

  .word-footer .el-button {
    padding: 10px 24px;
  }
}

/* 浮动翻译按钮样式（与 Markdown 模式对齐） */
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

.floating-translate-btn:active {
  cursor: grabbing;
}

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

  .original-text h4, .translated-text h4 {
    font-size: 13px;
  }

  .original-text p, .translated-text p {
    font-size: 14px;
    padding: 10px;
    margin: 0;
  }
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
</style>
