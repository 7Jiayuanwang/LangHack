<template>
  <div class="papers-page">
    <header class="papers-header">
      <button v-if="activePaper" class="icon-button" type="button" aria-label="返回试卷列表" @click="leavePaper">
        <el-icon><ArrowLeft /></el-icon>
      </button>
      <div>
        <h1>{{ activePaper ? activePaper.title : '历年真题' }}</h1>
        <p>{{ activePaper ? `${answeredCount}/${activePaper.questions.length} 题已作答` : '练习、交卷并查看逐题解析' }}</p>
      </div>
      <button v-if="!activePaper && canManagePapers" class="import-button" type="button" @click="importVisible = true">
        <el-icon><Upload /></el-icon>
        导入
      </button>
    </header>

    <main v-if="!activePaper" class="paper-library">
      <div class="category-tabs" role="tablist" aria-label="考试类别">
        <button
          v-for="category in examCategories"
          :key="category.key"
          type="button"
          role="tab"
          :aria-selected="selectedCategory === category.key"
          :class="{ active: selectedCategory === category.key }"
          @click="selectedCategory = category.key"
        >
          {{ category.shortLabel }}
        </button>
      </div>

      <section class="category-intro">
        <span class="category-mark">{{ currentCategory.shortLabel }}</span>
        <div>
          <h2>{{ currentCategory.label }}</h2>
          <p>{{ currentCategory.description }}</p>
        </div>
      </section>

      <section class="paper-list" aria-label="试卷列表">
        <article v-for="paper in filteredPapers" :key="paper.id" class="paper-item">
          <div class="paper-main">
            <div class="paper-title-row">
              <h3>{{ paper.title }}</h3>
              <span :class="['status', paper.status]">{{ paper.status === 'ready' ? '可作答' : '待校对导入' }}</span>
              <span v-if="paper.serverManaged" class="status custom">共享题库</span>
            </div>
            <p>{{ paper.status === 'ready' ? `${paper.questions.length} 道题 · 约 ${paper.duration} 分钟${paper.scope ? ` · ${paper.scope}` : ''}` : '扫描版资料已收到，题目正在结构化校对' }}</p>
            <div v-if="getSavedResult(paper.id)" class="saved-result">
              上次客观题得分 {{ getSavedResult(paper.id).score }}/{{ getSavedResult(paper.id).total }}
            </div>
          </div>
          <div class="paper-actions">
            <button v-if="paper.serverManaged && canManagePapers" class="delete-button" type="button" aria-label="删除共享试卷" @click="removePaper(paper)">
              <el-icon><Delete /></el-icon>
            </button>
            <button class="start-button" type="button" :disabled="paper.status !== 'ready'" @click="startPaper(paper)">
              {{ getSavedResult(paper.id) ? '再次练习' : '开始做题' }}
              <el-icon><ArrowRight /></el-icon>
            </button>
          </div>
        </article>
      </section>
    </main>

    <main v-else class="exam-area">
      <div class="progress-line" aria-hidden="true"><span :style="{ width: `${progress}%` }"></span></div>

      <div ref="splitRef" class="exam-split" :class="{ 'pane-hidden': !passagePaneVisible }">
        <aside v-show="passagePaneVisible" class="passage-pane" :style="paneStyle">
          <div class="passage-pane-head">
            <span>正文材料</span>
            <button class="pane-toggle" type="button" title="收起正文" aria-label="收起正文" @click="passagePaneVisible = false">
              <el-icon><DArrowLeft /></el-icon>
            </button>
          </div>
          <div ref="passageBody" class="passage-body">
            <template v-if="currentQuestion?.passage">{{ currentQuestion.passage }}</template>
            <template v-else-if="stemInPane">{{ currentQuestion.stem }}</template>
            <p v-else-if="isListeningQuestion" class="passage-empty">本题材料为听力录音，请在右侧播放并作答。</p>
            <p v-else class="passage-empty">本题没有正文材料。</p>
            <div v-if="questionType === 'matching'" class="option-list">
              <p v-for="(option, optionIndex) in currentQuestion.options" :key="optionIndex"><strong>{{ optionLetter(optionIndex) }}.</strong> {{ option }}</p>
            </div>
          </div>
        </aside>

        <div
          v-show="passagePaneVisible"
          class="split-handle"
          title="拖动调整宽度 · 双击恢复默认"
          @pointerdown="startResize"
          @dblclick="resetPaneWidth"
        ></div>

        <section ref="questionCardRef" class="question-card">
        <div class="question-meta">
          <span>{{ currentQuestion.section }}</span>
          <span class="meta-right">
            第 {{ currentIndex + 1 }} / {{ activePaper.questions.length }} 题
            <button
              v-if="!passagePaneVisible && (currentQuestion.passage || stemInPane)"
              class="pane-restore"
              type="button"
              title="展开正文"
              @click="passagePaneVisible = true"
            >
              <el-icon><DArrowRight /></el-icon>
              正文
            </button>
          </span>
        </div>
        <h2 v-if="!stemInPane">{{ currentQuestion.stem }}</h2>

        <div v-if="isListeningQuestion" class="listening-player">
          <div class="listening-label">听力材料</div>
          <div class="lp-controls">
            <button type="button" class="lp-btn" :aria-label="lpPlaying ? '暂停' : '播放'" @click="toggleLpPlay">{{ lpPlaying ? '❚❚' : '▶' }}</button>
            <span class="lp-time">{{ lpFmt(lpCurrent) }}</span>
            <input class="lp-seek" type="range" min="0" :max="lpDuration || 0" step="0.1" :value="lpCurrent" :disabled="!lpDuration" aria-label="播放进度" @input="onLpSeek($event)">
            <span class="lp-time">{{ lpFmt(lpDuration) }}</span>
            <label>倍速
              <select v-model.number="playbackRate" @change="syncPlaybackRate">
                <option :value="0.5">0.5x</option><option :value="0.75">0.75x</option><option :value="1">1x</option><option :value="1.25">1.25x</option><option :value="1.5">1.5x</option><option :value="2">2x</option>
              </select>
            </label>
          </div>
          <!-- 隐藏的媒体元素：不用原生 controls，避免手机浏览器再弹一个自带的"播放速度"菜单 -->
          <audio ref="audioElement" class="lp-audio" :src="currentQuestion.audioUrl || activePaper.audioUrl" preload="metadata"
            @play="lpPlaying = true" @pause="lpPlaying = false" @ended="lpPlaying = false"
            @timeupdate="lpCurrent = $event.target.currentTime"
            @loadedmetadata="lpDuration = $event.target.duration || 0"
            @durationchange="lpDuration = Number.isFinite($event.target.duration) ? $event.target.duration : 0"></audio>
        </div>

        <div v-if="questionType === 'single_choice' || questionType === 'listening'" class="options" role="radiogroup" :aria-label="currentQuestion.stem">
          <button
            v-for="(option, index) in currentQuestion.options"
            :key="option"
            type="button"
            :class="optionClass(index)"
            :disabled="submitted"
            @click="selectAnswer(index)"
          >
            <span class="option-letter">{{ optionLetter(index) }}</span>
            <span>{{ cleanOptionText(option) }}</span>
          </button>
        </div>

        <div v-else-if="questionType === 'cloze'" class="structured-answers">
          <label v-for="(_, index) in currentQuestion.answers" :key="index">
            第 {{ index + 1 }} 空
            <select :value="answerValue?.[index] ?? ''" :disabled="submitted" @change="setArrayAnswer(index, $event.target.value)">
              <option value="">请选择</option>
              <option v-for="(option, optionIndex) in currentQuestion.options" :key="optionIndex" :value="optionIndex">{{ optionLetter(optionIndex) }}. {{ option }}</option>
            </select>
          </label>
        </div>

        <div v-else-if="questionType === 'matching'" class="structured-answers matching-answers">
          <label v-for="item in currentQuestion.items" :key="item.id">
            <span>{{ item.prompt }}</span>
            <select :value="answerValue?.[item.id] ?? ''" :disabled="submitted" @change="setMatchingAnswer(item.id, $event.target.value)">
              <option value="">请选择对应段落</option>
              <option v-for="(option, optionIndex) in currentQuestion.options" :key="optionIndex" :value="optionIndex">{{ optionLetter(optionIndex) }}</option>
            </select>
          </label>
        </div>

        <textarea
          v-else
          class="subjective-answer"
          :value="answerValue || ''"
          :disabled="submitted"
          :placeholder="questionType === 'writing' ? '在这里完成作文' : '在这里输入译文'"
          @input="setTextAnswer($event.target.value)"
        ></textarea>

        <div v-if="questionType === 'writing'" class="writing-score">
          <button type="button" class="writing-score-btn" :disabled="writingScoring" @click="scoreWriting">
            {{ writingScoring ? '小芽正在批改…' : 'AI 智能评分' }}
          </button>
          <span class="writing-score-hint">按四六级 15 分制，从内容 / 结构 / 语言三维度评分</span>
          <p v-if="writingScoreError" class="writing-score-note">{{ writingScoreError }}</p>
          <div v-if="writingScore" class="writing-score-card">
            <p v-if="writingScore.rawFeedback" class="writing-feedback">{{ writingScore.rawFeedback }}</p>
            <template v-else>
              <div class="writing-total">
                <strong>{{ writingScore.total }}</strong> / 15
                <span v-if="writingScore.wordCount"> · {{ writingScore.wordCount }} 词</span>
              </div>
              <ul class="writing-dims">
                <li v-for="(label, key) in { content: '内容', structure: '结构', language: '语言' }" :key="key">
                  <span class="writing-dim-name">{{ label }}</span>
                  <span class="writing-dim-score">{{ writingScore.dimensions[key]?.score ?? '—' }}/5</span>
                  <span class="writing-dim-comment">{{ writingScore.dimensions[key]?.comment }}</span>
                </li>
              </ul>
              <div v-if="writingScore.strengths && writingScore.strengths.length" class="writing-list">
                <div class="writing-list-title">优点</div>
                <ul><li v-for="(item, i) in writingScore.strengths" :key="'s' + i">{{ item }}</li></ul>
              </div>
              <div v-if="writingScore.improvements && writingScore.improvements.length" class="writing-list">
                <div class="writing-list-title">改进建议</div>
                <ul><li v-for="(item, i) in writingScore.improvements" :key="'i' + i">{{ item }}</li></ul>
              </div>
              <p v-if="writingScore.revisedSample" class="writing-sample">修改示例：{{ writingScore.revisedSample }}</p>
            </template>
          </div>
        </div>

        <div v-if="submitted" class="analysis-panel">
          <div class="analysis-title">
            <template v-if="isSubjective(currentQuestion)">参考答案与评分要点</template>
            <template v-else>
              <el-icon><CircleCheck v-if="isCorrect(currentQuestion)" /><CircleClose v-else /></el-icon>
              {{ isCorrect(currentQuestion) ? '回答正确' : '答案需要订正' }}
            </template>
          </div>
          <p v-if="isSubjective(currentQuestion)" class="reference-answer">{{ currentQuestion.referenceAnswer }}</p>
          <p v-else-if="questionType === 'cloze'">正确答案：{{ currentQuestion.answers.map(index => optionLetter(index)).join('、') }}</p>
          <p v-else-if="questionType === 'matching'">正确答案：{{ currentQuestion.items.map(item => `${item.id}-${optionLetter(item.answer)}`).join('、') }}</p>
          <p v-else>正确答案：{{ optionLetter(currentQuestion.answer) }}</p>

          <div class="analysis-question">
            <div class="analysis-subtitle">本题解析</div>
            <template v-if="currentAnalysis">
              <div v-if="currentAnalysis.length <= 1100" class="analysis-body">
                <template v-if="formattedAnalysis">
                  <p v-if="formattedAnalysis.locate" class="analysis-locate">{{ formattedAnalysis.locate }}</p>
                  <section v-for="(block, bi) in formattedAnalysis.blocks" :key="bi" class="analysis-block">
                    <div v-if="block.title" class="analysis-block-title">{{ block.title }}</div>
                    <ul v-if="block.kind === 'list'" class="analysis-list">
                      <li v-for="(line, li) in block.lines" :key="li">{{ line }}</li>
                    </ul>
                    <template v-else>
                      <p v-for="(line, li) in block.lines" :key="li" :class="{ 'analysis-correct': block.kind === 'correct' }">{{ line }}</p>
                    </template>
                  </section>
                </template>
                <p v-else>{{ currentAnalysis }}</p>
              </div>
              <details v-else class="analysis-fold">
                <summary>解析较长，点击展开全文（{{ currentAnalysis.length }} 字）</summary>
                <div class="analysis-body">
                  <template v-if="formattedAnalysis">
                    <p v-if="formattedAnalysis.locate" class="analysis-locate">{{ formattedAnalysis.locate }}</p>
                    <section v-for="(block, bi) in formattedAnalysis.blocks" :key="bi" class="analysis-block">
                      <div v-if="block.title" class="analysis-block-title">{{ block.title }}</div>
                      <ul v-if="block.kind === 'list'" class="analysis-list">
                        <li v-for="(line, li) in block.lines" :key="li">{{ line }}</li>
                      </ul>
                      <template v-else>
                        <p v-for="(line, li) in block.lines" :key="li" :class="{ 'analysis-correct': block.kind === 'correct' }">{{ line }}</p>
                      </template>
                    </section>
                  </template>
                  <p v-else>{{ currentAnalysis }}</p>
                </div>
              </details>
            </template>
            <p v-else class="analysis-missing">本题暂无解析{{ currentShared ? `，可展开下方${currentShared.label}` : '' }}</p>
          </div>

          <details v-if="formattedAnalysis && formattedAnalysis.vocab" class="analysis-vocab" open>
            <summary>
              <span class="analysis-vocab-title">{{ formattedAnalysis.vocab.title }}</span>
              <span class="analysis-vocab-count">{{ formattedAnalysis.vocab.entries.length }} 词</span>
              <span class="analysis-vocab-hint">点击折叠</span>
            </summary>
            <ul class="analysis-vocab-list">
              <li v-for="(word, wi) in formattedAnalysis.vocab.entries" :key="wi">
                <span class="vocab-word">{{ word.word }}</span>
                <span v-if="word.phonetic" class="vocab-phonetic">{{ word.phonetic }}</span>
                <span v-if="word.pos" class="vocab-pos">{{ word.pos }}</span>
                <span class="vocab-meaning">{{ word.meaning }}</span>
              </li>
            </ul>
          </details>

          <details v-if="currentShared" class="analysis-fold analysis-source">
            <summary>{{ currentShared.label }}（点击展开）</summary>
            <p>{{ currentShared.text }}</p>
          </details>
        </div>
        </section>
      </div>

      <footer class="exam-actions">
        <button type="button" class="secondary-button" :disabled="currentIndex === 0" @click="currentIndex--">上一题</button>
        <button v-if="currentIndex < activePaper.questions.length - 1" type="button" class="primary-button" @click="currentIndex++">下一题</button>
        <button v-else-if="!submitted" type="button" class="primary-button" :disabled="answeredCount === 0 || submitting" @click="submitPaper">{{ submitting ? '保存中...' : '交卷' }}</button>
        <button v-else type="button" class="primary-button" @click="leavePaper">查看试卷列表</button>
      </footer>

      <div v-if="submitted" class="score-bar" aria-live="polite">
        客观题得分 <strong>{{ score }}/{{ objectiveCount }}</strong>
        <span v-if="subjectiveCount"> · {{ subjectiveCount }} 道主观题请对照参考答案自评</span>
      </div>
    </main>

    <PastPaperImportDialog v-model="importVisible" @imported="loadImportedPapers" />
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowLeft, ArrowRight, CircleCheck, CircleClose, DArrowLeft, DArrowRight, Delete, Upload } from '@element-plus/icons-vue'
import { examCategories, pastPapers } from '@/data/pastPapers'
import PastPaperImportDialog from '@/components/past-papers/PastPaperImportDialog.vue'
import { deleteSharedPastPaper, getSharedPastPapers, requestWritingScore, submitPastPaperAttempt } from '@/api/pastPapers'
import { buildAnalysisIndex, formatAnalysis } from '@/utils/pastPaperAnalysis'
import { localEssayScore, parseScorePayload } from '@/utils/writingScore'
import { useUserStore } from '@/store/user'

const STORAGE_KEY = 'langhack-past-paper-results'
const route = useRoute()
const selectedCategory = ref('cet4')
const activePaper = ref(null)
const currentIndex = ref(0)
const answers = ref({})
const submitted = ref(false)
const submitting = ref(false)
const importVisible = ref(false)
const sharedPapers = ref([])
const userStore = useUserStore()
const savedResults = ref(readSavedResults())

// ===== 正文/题目分栏 =====
// 桌面（>900px）：左右分栏，拖拽调整正文「宽度」
// 移动（<=900px）：上下堆叠，拖拽调整正文「高度」——手机上同样可以滑动
const PASSAGE_WIDTH_KEY = 'langhack-past-paper-passage-width-v2'
const PASSAGE_HEIGHT_KEY = 'langhack-past-paper-passage-height-v1'
const DEFAULT_PANE_WIDTH = 620
const DEFAULT_PANE_HEIGHT = 300
const COMPACT_QUERY = '(max-width: 900px)'
const passagePaneVisible = ref(true)
const passageWidth = ref(readPaneWidth())
const passageHeight = ref(readPaneHeight())
const splitRef = ref(null)
const passageBody = ref(null)
const questionCardRef = ref(null)
const resizing = ref(false)

const compactMedia = typeof window !== 'undefined' && window.matchMedia ? window.matchMedia(COMPACT_QUERY) : null
const isCompact = ref(compactMedia ? compactMedia.matches : false)

function syncCompactLayout(event) {
  isCompact.value = event.matches
}

function readStoredNumber(key, fallback, min) {
  try {
    const value = Number(localStorage.getItem(key))
    return Number.isFinite(value) && value >= min ? value : fallback
  } catch {
    return fallback
  }
}

function readPaneWidth() {
  return readStoredNumber(PASSAGE_WIDTH_KEY, DEFAULT_PANE_WIDTH, 260)
}

function readPaneHeight() {
  return readStoredNumber(PASSAGE_HEIGHT_KEY, DEFAULT_PANE_HEIGHT, 120)
}

function persistPaneWidth() {
  try {
    localStorage.setItem(PASSAGE_WIDTH_KEY, String(passageWidth.value))
  } catch {
    // localStorage 不可用时忽略
  }
}

function persistPaneHeight() {
  try {
    localStorage.setItem(PASSAGE_HEIGHT_KEY, String(passageHeight.value))
  } catch {
    // localStorage 不可用时忽略
  }
}

if (compactMedia) {
  if (compactMedia.addEventListener) compactMedia.addEventListener('change', syncCompactLayout)
  else compactMedia.addListener?.(syncCompactLayout)
}

// 拖拽起始快照：用位移量计算，避免布局变化导致抖动
let dragStart = null

function startResize(event) {
  event.preventDefault()
  if (!splitRef.value) return
  const rect = splitRef.value.getBoundingClientRect()
  resizing.value = true
  dragStart = {
    x: event.clientX,
    y: event.clientY,
    width: passageWidth.value,
    height: passageHeight.value,
    top: rect.top
  }
  try { event.currentTarget.setPointerCapture?.(event.pointerId) } catch { /* 忽略 */ }
  document.body.classList.add('pane-resizing')
  window.addEventListener('pointermove', onResize, { passive: false })
  window.addEventListener('pointerup', stopResize)
  window.addEventListener('pointercancel', stopResize)
}

function clamp(value, min, max) {
  return Math.round(Math.min(Math.max(value, min), max))
}

function onResize(event) {
  if (!resizing.value || !dragStart || !splitRef.value) return
  event.preventDefault?.()
  if (isCompact.value) {
    // 移动端：上下拖动 = 调整正文高度（留出足够空间给题目）
    const min = 120
    const max = Math.max(min + 80, window.innerHeight - 240)
    passageHeight.value = clamp(dragStart.height + (event.clientY - dragStart.y), min, max)
    return
  }
  const rect = splitRef.value.getBoundingClientRect()
  const max = Math.max(320, rect.width - 300)
  passageWidth.value = clamp(dragStart.width + (event.clientX - dragStart.x), 260, max)
}

function stopResize() {
  if (!resizing.value) return
  resizing.value = false
  dragStart = null
  document.body.classList.remove('pane-resizing')
  window.removeEventListener('pointermove', onResize)
  window.removeEventListener('pointerup', stopResize)
  window.removeEventListener('pointercancel', stopResize)
  persistPaneWidth()
  persistPaneHeight()
}

function resetPaneWidth() {
  passageWidth.value = DEFAULT_PANE_WIDTH
  passageHeight.value = DEFAULT_PANE_HEIGHT
  persistPaneWidth()
  persistPaneHeight()
}

const paneStyle = computed(() => (isCompact.value
  ? { height: `${passageHeight.value}px`, maxHeight: 'none' }
  : { flexBasis: `${passageWidth.value}px` }))

onBeforeUnmount(() => {
  if (compactMedia) {
    if (compactMedia.removeEventListener) compactMedia.removeEventListener('change', syncCompactLayout)
    else compactMedia.removeListener?.(syncCompactLayout)
  }
  window.removeEventListener('pointermove', onResize)
  window.removeEventListener('pointerup', stopResize)
})

const currentCategory = computed(() => examCategories.find(item => item.key === selectedCategory.value))
const canManagePapers = computed(() => userStore.userRole === 'ADMIN' && route.name !== 'AdminPastPapersPreview')
const allPapers = computed(() => [...sharedPapers.value, ...pastPapers])
const filteredPapers = computed(() => allPapers.value.filter(item => item.category === selectedCategory.value))
const currentQuestion = computed(() => activePaper.value?.questions[currentIndex.value])

// ===== 解析按题对应 =====
// 导入的真题数据里，每道题的 analysis 往往是一整块文档正文（听力原文 / 全文翻译
// + 从该题起的多题解析）。这里建立索引，把「本题解析」和「共享材料」拆开渲染。
const analysisIndex = computed(() => (activePaper.value ? buildAnalysisIndex(activePaper.value) : null))
const currentAnalysis = computed(() => (analysisIndex.value ? analysisIndex.value.analysisFor(currentQuestion.value) : ''))
const currentShared = computed(() => (analysisIndex.value ? analysisIndex.value.sharedFor(currentQuestion.value) : null))
// 解析结构化排版：定位/正确项/干扰项排除列表；无法结构化时模板回退纯文本
const formattedAnalysis = computed(() => (currentAnalysis.value ? formatAnalysis(currentAnalysis.value) : null))

// ===== 作文 AI 判分 =====
const writingScore = ref(null)
const writingScoring = ref(false)
const writingScoreError = ref('')
const resetWritingScore = () => {
  writingScore.value = null
  writingScoreError.value = ''
}

async function scoreWriting() {
  const question = currentQuestion.value
  if (!question || question.type !== 'writing') return
  const essay = String(answerValue.value || '').trim()
  if (!essay) {
    ElMessage.warning('请先写一段作文再评分')
    return
  }
  if (essay.length > 2000) {
    ElMessage.warning('作文过长（超过 2000 字），请精简后再评分')
    return
  }
  writingScoring.value = true
  writingScoreError.value = ''
  try {
    const response = await requestWritingScore(essay, question.stem)
    const payload = response?.data ?? response
    const parsed = parseScorePayload(payload)
    if (parsed) {
      writingScore.value = parsed
    } else {
      // 模型没按 JSON 返回时，退化为展示纯文本反馈
      const text = typeof payload === 'string'
        ? payload
        : payload?.answer || payload?.content || payload?.message || ''
      if (String(text).trim().length > 20) {
        writingScore.value = { rawFeedback: String(text).trim() }
      } else {
        throw new Error('empty')
      }
    }
  } catch (error) {
    // AI 通道不可用（未配 key / 校园网不通）时给出本地参考分，保证功能可用
    writingScore.value = { ...localEssayScore(essay, question.stem) }
    writingScoreError.value = 'AI 判分暂时不可用，以下为本地参考评分（按篇幅、结构、语言估算）'
    console.warn('作文 AI 判分失败，已降级为本地评分:', error)
  } finally {
    writingScoring.value = false
  }
}
const audioElement = ref(null)
const playbackRate = ref(1)
// 自定义听力播放器状态（原生 audio controls 的浏览器自带"播放速度"菜单与页面倍速下拉重复，故自制 UI）
const lpPlaying = ref(false)
const lpCurrent = ref(0)
const lpDuration = ref(0)
const toggleLpPlay = () => {
  const a = audioElement.value
  if (!a || !a.src) return
  if (a.paused) a.play().catch(() => {})
  else a.pause()
}
const onLpSeek = (e) => {
  const a = audioElement.value
  if (!a || !lpDuration.value) return
  const t = Number(e.target.value)
  a.currentTime = t
  lpCurrent.value = t
}
const lpFmt = (s) => {
  if (!Number.isFinite(s) || s <= 0) return '0:00'
  const m = Math.floor(s / 60)
  const sec = Math.floor(s % 60)
  return m + ':' + String(sec).padStart(2, '0')
}
// 是否听力题：兼容两种判断方式
// 1) type === 'listening'（后端正确标记的听力题）
// 2) section 包含"听力"（题库导入时即便 type 是 single_choice，section 标了"听力理解"也算）
// 注意：不靠 audioUrl 兜底——因为后端导入时把试卷级 audioUrl 复制到了每道题，会误判
const isListeningQuestion = computed(() => {
  const q = currentQuestion.value
  if (!q) return false
  if (q.type === 'listening') return true
  if (typeof q.section === 'string' && q.section.includes('听力')) return true
  return false
})
const questionType = computed(() => currentQuestion.value?.type || 'single_choice')
// 题干移入左栏：题目没有独立 passage、也不是听力，且题干是长文本（如选词填空的整段文章）时，
// 把题干当作"正文"放到左侧阅读栏，右栏只留题型/题号/选项
const STEM_PANE_THRESHOLD = 120
const stemInPane = computed(() => {
  const q = currentQuestion.value
  return !!q && !q.passage && !isListeningQuestion.value && typeof q.stem === 'string' && q.stem.length > STEM_PANE_THRESHOLD
})
const answerValue = computed(() => answers.value[currentQuestion.value?.id])
const answeredCount = computed(() => activePaper.value?.questions.filter(isAnswered).length || 0)
const progress = computed(() => activePaper.value ? (answeredCount.value / activePaper.value.questions.length) * 100 : 0)
const objectiveCount = computed(() => activePaper.value?.questions.filter(question => !isSubjective(question)).length || 0)
const subjectiveCount = computed(() => (activePaper.value?.questions.length || 0) - objectiveCount.value)
const score = computed(() => activePaper.value?.questions.reduce((total, question) => total + (!isSubjective(question) && isCorrect(question) ? 1 : 0), 0) || 0)

function readSavedResults() {
  try {
    return JSON.parse(localStorage.getItem(STORAGE_KEY) || '{}')
  } catch {
    return {}
  }
}

function getSavedResult(paperId) {
  return savedResults.value[paperId]
}

async function loadImportedPapers() {
  try {
    const response = await getSharedPastPapers()
    sharedPapers.value = response?.data || []
  } catch (error) {
    console.warn('无法读取共享题库:', error)
  }
}

async function removePaper(paper) {
  try {
    await ElMessageBox.confirm(`确定删除“${paper.title}”吗？所有学生将无法继续访问。`, '删除共享试卷', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await deleteSharedPastPaper(paper.id)
    await loadImportedPapers()
    ElMessage.success('试卷已删除')
  } catch {
    // User cancelled.
  }
}

function startPaper(paper) {
  if (paper.status !== 'ready') return
  activePaper.value = paper
  currentIndex.value = 0
  answers.value = {}
  submitted.value = false
  playbackRate.value = 1
  lpPlaying.value = false
  lpCurrent.value = 0
  lpDuration.value = 0
  resetWritingScore()
}

function syncPlaybackRate() {
  if (audioElement.value) audioElement.value.playbackRate = playbackRate.value
}

// 清洗选项里被错误拼接的"下一题引导语"——解决旧数据里把
// "Minimum base salary. Questions 22 to 25 are based on the passage you have just heard."
// 当作选项内容显示的问题。规则：选项末尾若出现 "Questions XX to YY are based on..."
// 或 "听下一段对话/独白" 这类指令，切掉。
function cleanOptionText(text) {
  if (!text) return text
  return text
    // 英文引导语：匹配 ". Questions 22 to 25 are based on ..." 或 "## Questions 19 to 21 are based on ..."
    .replace(/\.?\s*(?:##\s*)?Questions?\s+\d+\s+(?:to|and)\s+\d+\s+are\s+based\s+on.*$/is, '')
    .replace(/\.?\s*(?:##\s*)?Questions?\s+\d+\s+are\s+based\s+on.*$/is, '')
    .replace(/\.?\s*(?:#{1,3}\s*)?(?:(?:Part|PART)\s+(?:[IVX]+|\d+)|(?:Section|SECTION)\s+[A-C])\b.*$/s, '')
    .replace(/\.?\s*={2,}\s*第?\s*\d+\s*页\s*={2,}.*$/is, '')
    .replace(/[.。]?\s*(?:公众号|微信公众(?:号|平台)?)[：:]?\s*[^\s]+\s*$/i, '')
    // 中文引导语："听下一段对话/独白" 等
    .replace(/\.?\s*(?:##\s*)?听(?:下)?一?段?(?:对话|独白|短文)[^。]*[。]?\s*$/i, '')
    .replace(/\.?\s*听(?:下)?一?段?(?:对话|独白|短文)[^。]*[。]?\s*$/i, '')
    .trim()
}

// 切题后正文/题目两个面板都回到顶部
watch(() => [currentQuestion.value?.passage, currentQuestion.value?.stem, currentIndex.value], async () => {
  await nextTick()
  if (passageBody.value) passageBody.value.scrollTop = 0
  if (questionCardRef.value) questionCardRef.value.scrollTop = 0
})

watch([currentIndex, activePaper, isListeningQuestion], async () => {
  await nextTick()
  resetWritingScore()
  // 切换题目时，若当前不是听力题则自动暂停并停止播放，避免录音继续响
  if (!isListeningQuestion.value && audioElement.value) {
    audioElement.value.pause()
    audioElement.value.currentTime = 0
    audioElement.value.src = ''
    lpPlaying.value = false
    lpCurrent.value = 0
    lpDuration.value = 0
  } else {
    syncPlaybackRate()
  }
})

function selectAnswer(index) {
  answers.value = { ...answers.value, [currentQuestion.value.id]: index }
}

function setArrayAnswer(index, value) {
  const next = [...(answerValue.value || [])]
  next[index] = value === '' ? null : Number(value)
  answers.value = { ...answers.value, [currentQuestion.value.id]: next }
}

function setMatchingAnswer(itemId, value) {
  answers.value = { ...answers.value, [currentQuestion.value.id]: { ...(answerValue.value || {}), [itemId]: value === '' ? null : Number(value) } }
}

function setTextAnswer(value) {
  answers.value = { ...answers.value, [currentQuestion.value.id]: value }
}

function isSubjective(question) {
  return ['translation', 'writing'].includes(question.type)
}

function isAnswered(question) {
  const value = answers.value[question.id]
  if ((question.type || 'single_choice') === 'cloze') return Array.isArray(value) && value.length === question.answers.length && value.every(item => Number.isInteger(item))
  if (question.type === 'matching') return question.items.every(item => Number.isInteger(value?.[item.id]))
  if (isSubjective(question)) return typeof value === 'string' && value.trim().length > 0
  return Number.isInteger(value)
}

function isCorrect(question) {
  const value = answers.value[question.id]
  if (['single_choice', 'listening'].includes(question.type || 'single_choice')) return value === question.answer
  if (question.type === 'cloze') return question.answers.every((answer, index) => value?.[index] === answer)
  if (question.type === 'matching') return question.items.every(item => value?.[item.id] === item.answer)
  return false
}

function optionLetter(index) {
  return String.fromCharCode(65 + index)
}

function optionClass(index) {
  const selected = answers.value[currentQuestion.value.id] === index
  return {
    'option-button': true,
    selected,
    correct: submitted.value && index === currentQuestion.value.answer,
    wrong: submitted.value && selected && index !== currentQuestion.value.answer
  }
}

async function submitPaper() {
  if (answeredCount.value < activePaper.value.questions.length) {
    try {
      await ElMessageBox.confirm(`还有 ${activePaper.value.questions.length - answeredCount.value} 题未作答，仍要交卷吗？`, '确认交卷', {
        confirmButtonText: '交卷',
        cancelButtonText: '继续作答'
      })
    } catch {
      return
    }
  }
  submitting.value = true
  try {
    await submitPastPaperAttempt({
      paperKey: String(activePaper.value.id),
      pastPaperId: activePaper.value.serverManaged ? activePaper.value.id : null,
      category: activePaper.value.category,
      paperTitle: activePaper.value.title,
      answeredCount: answeredCount.value,
      questionCount: activePaper.value.questions.length,
      objectiveScore: score.value,
      objectiveTotal: objectiveCount.value,
      subjectiveCount: subjectiveCount.value,
      answers: answers.value
    })
    submitted.value = true
    savedResults.value = {
      ...savedResults.value,
      [activePaper.value.id]: { score: score.value, total: objectiveCount.value, subjectiveCount: subjectiveCount.value, submittedAt: Date.now() }
    }
    localStorage.setItem(STORAGE_KEY, JSON.stringify(savedResults.value))
    ElMessage.success(`已交卷并保存记录，客观题得分 ${score.value}/${objectiveCount.value}`)
    currentIndex.value = 0
  } catch (error) {
    console.error('保存真题练习记录失败:', error)
    ElMessage.error(error?.response?.data?.message || '交卷记录保存失败，请稍后重试')
  } finally {
    submitting.value = false
  }
}

async function leavePaper() {
  if (!submitted.value && answeredCount.value > 0) {
    try {
      await ElMessageBox.confirm('当前答案尚未交卷，离开后不会保存。', '离开试卷', {
        confirmButtonText: '确认离开',
        cancelButtonText: '继续作答'
      })
    } catch {
      return
    }
  }
  activePaper.value = null
  answers.value = {}
  submitted.value = false
}

onMounted(loadImportedPapers)
</script>

<style scoped>
.papers-page { height: 100%; min-height: 0; display: flex; flex-direction: column; background: #f5f7fa; color: #1f2937; }
.papers-header { flex: 0 0 auto; display: flex; align-items: center; gap: 12px; padding: 18px 20px 14px; background: #fff; border-bottom: 1px solid #e5e7eb; }
.papers-header h1 { margin: 0; font-size: 22px; line-height: 1.3; }
.papers-header p { margin: 3px 0 0; color: #6b7280; font-size: 13px; }
.import-button { margin-left: auto; min-height: 40px; padding: 0 14px; border: 1px solid var(--xy-brand-700); border-radius: 6px; background: #fff; color: var(--xy-brand-700); font-weight: 600; display: inline-flex; align-items: center; gap: 6px; cursor: pointer; }
.icon-button { width: 40px; height: 40px; border: 0; background: transparent; display: grid; place-items: center; font-size: 20px; cursor: pointer; }
.paper-library, .exam-area { flex: 1; min-height: 0; overflow-y: auto; padding: 16px; padding-bottom: 100px; }
.category-tabs { display: grid; grid-template-columns: repeat(3, 1fr); gap: 6px; padding: 4px; background: #e9edf3; border-radius: 8px; max-width: 620px; margin: 0 auto 18px; }
.category-tabs button { min-height: 42px; border: 0; border-radius: 6px; background: transparent; color: #64748b; font-weight: 600; cursor: pointer; }
.category-tabs button.active { background: #fff; color: var(--xy-brand-700); box-shadow: 0 1px 4px rgba(15,23,42,.12); }
.category-intro { max-width: 760px; margin: 0 auto 14px; display: flex; align-items: center; gap: 14px; }
.category-mark { width: 52px; height: 52px; display: grid; place-items: center; flex: 0 0 auto; background: var(--xy-brand-700); color: #fff; border-radius: 8px; font-weight: 700; }
.category-intro h2 { margin: 0 0 3px; font-size: 18px; }
.category-intro p { margin: 0; color: #64748b; font-size: 13px; }
.paper-list { max-width: 760px; margin: 0 auto; display: grid; gap: 10px; }
.paper-item { padding: 18px; background: #fff; border: 1px solid #e5e7eb; border-radius: 8px; display: flex; align-items: center; gap: 16px; }
.paper-main { flex: 1; min-width: 0; }
.paper-title-row { display: flex; align-items: flex-start; gap: 8px; flex-wrap: wrap; }
.paper-item h3 { margin: 0; font-size: 16px; }
.paper-item p { margin: 7px 0 0; color: #64748b; font-size: 13px; }
.status { font-size: 11px; padding: 3px 7px; border-radius: 999px; white-space: nowrap; }
.status.ready { background: #dcfce7; color: #166534; }
.status.pending { background: #f1f5f9; color: #64748b; }
.status.custom { background: var(--xy-brand-50); color: var(--xy-brand-700); }
.saved-result { margin-top: 8px; color: var(--xy-brand-700); font-size: 13px; font-weight: 600; }
.paper-actions { display: flex; align-items: center; gap: 8px; }
.delete-button { width: 42px; height: 42px; flex: 0 0 auto; border: 1px solid #fecaca; border-radius: 6px; background: #fff; color: #dc2626; display: grid; place-items: center; cursor: pointer; }
.start-button, .primary-button, .secondary-button { min-height: 42px; border-radius: 6px; padding: 0 16px; font-weight: 600; cursor: pointer; display: inline-flex; align-items: center; justify-content: center; gap: 6px; }
.start-button, .primary-button { border: 1px solid var(--xy-brand-700); background: var(--xy-brand-700); color: #fff; }
.secondary-button { border: 1px solid #cbd5e1; background: #fff; color: #334155; }
button:disabled { opacity: .45; cursor: not-allowed; }
.exam-area { max-width: 1400px; width: 100%; margin: 0 auto; box-sizing: border-box; }
.progress-line { height: 5px; background: #e2e8f0; border-radius: 3px; overflow: hidden; margin-bottom: 14px; }
.progress-line span { display: block; height: 100%; background: var(--xy-brand-700); transition: width .2s ease; }
.exam-split { display: flex; align-items: stretch; }
.passage-pane { flex: 0 0 auto; min-width: 0; display: flex; flex-direction: column; background: #fff; border: 1px solid #e5e7eb; border-radius: 8px; overflow: hidden; position: sticky; top: 12px; align-self: flex-start; width: auto; max-height: calc(100vh - 210px); min-height: 240px; }
.passage-pane-head { display: flex; justify-content: space-between; align-items: center; gap: 8px; padding: 10px 14px; border-bottom: 1px solid #e5e7eb; background: #f8fafc; color: #334155; font-weight: 700; font-size: 13px; flex: 0 0 auto; }
.pane-toggle { width: 28px; height: 28px; border: 1px solid #cbd5e1; border-radius: 6px; background: #fff; color: #64748b; display: grid; place-items: center; cursor: pointer; }
.pane-toggle:hover { border-color: var(--xy-brand-700); color: var(--xy-brand-700); }
.passage-body { flex: 1 1 auto; min-height: 0; overflow-y: auto; overscroll-behavior: contain; -webkit-overflow-scrolling: touch; padding: 16px; line-height: 1.8; white-space: pre-wrap; color: #1f2937; font-size: 15px; }
.passage-empty { color: #94a3b8; }
.split-handle { flex: 0 0 14px; margin: 0 5px; cursor: col-resize; position: relative; touch-action: none; user-select: none; border-radius: 6px; }
.split-handle::after { content: ''; position: absolute; top: 0; bottom: 0; left: 5px; width: 4px; border-radius: 2px; background: #dbe2ea; transition: background .15s ease; }
.split-handle:hover::after, .split-handle:active::after, body.pane-resizing .split-handle::after { background: var(--xy-brand-700); }
body.pane-resizing { cursor: col-resize; user-select: none; -webkit-user-select: none; }
.pane-restore { display: inline-flex; align-items: center; gap: 4px; min-height: 26px; margin-left: 10px; padding: 0 10px; border: 1px solid var(--xy-brand-700); border-radius: 999px; background: #fff; color: var(--xy-brand-700); font-size: 12px; font-weight: 600; cursor: pointer; }
.pane-restore:hover { background: var(--xy-brand-50); }
.question-card { background: #fff; border: 1px solid #e5e7eb; border-radius: 8px; padding: 22px; flex: 1 1 auto; min-width: 0; max-height: calc(100vh - 210px); overflow-y: auto; overscroll-behavior: contain; }
.question-card::-webkit-scrollbar { width: 8px; }
.question-card::-webkit-scrollbar-thumb { background: #d7dee6; border-radius: 4px; }
.question-card::-webkit-scrollbar-thumb:hover { background: #bcc7d2; }
.question-meta { display: flex; justify-content: space-between; gap: 12px; color: #64748b; font-size: 13px; }
.meta-right { display: inline-flex; align-items: center; }
.question-card h2 { margin: 18px 0; font-size: 18px; line-height: 1.6; }
.listening-player { display: flex; align-items: center; flex-wrap: wrap; gap: 6px 10px; margin: 10px 0 14px; padding: 8px 10px; background: var(--xy-brand-50); border: 1px solid #bfdbfe; border-radius: 6px; }
.listening-player .lp-audio { display: none; }
.lp-controls { display: flex; align-items: center; gap: 8px; flex: 1 1 340px; min-width: 240px; }
.lp-btn { flex: 0 0 auto; width: 34px; height: 34px; border: none; border-radius: 50%; background: var(--xy-brand-600, #2563eb); color: #fff; font-size: 11px; line-height: 1; cursor: pointer; display: inline-flex; align-items: center; justify-content: center; }
.lp-btn:hover { filter: brightness(1.08); }
.lp-btn:disabled { background: #94a3b8; cursor: default; }
.lp-time { flex: 0 0 auto; color: #475569; font-size: 12px; font-variant-numeric: tabular-nums; min-width: 34px; text-align: center; }
.lp-seek { flex: 1 1 110px; min-width: 70px; height: 4px; accent-color: var(--xy-brand-600, #2563eb); cursor: pointer; }
.listening-label { color: var(--xy-brand-700); font-weight: 700; font-size: 13px; }
.listening-player label { display: inline-flex; align-items: center; gap: 6px; color: #334155; font-size: 12px; }
.listening-player select { min-height: 28px; border: 1px solid #93c5fd; border-radius: 5px; padding: 2px 6px; background: #fff; font-size: 12px; }
@media (max-width: 900px) {
  .listening-player { gap: 4px 8px; margin: 8px 0 10px; padding: 6px 8px; }
  .listening-label { order: 1; }
  .listening-player label { order: 2; margin-left: auto; }
  .lp-controls { order: 3; flex: 1 1 100%; min-width: 0; gap: 6px; }
  .lp-btn { width: 32px; height: 32px; }
}
.options { display: grid; gap: 10px; }
.structured-answers { display: grid; gap: 12px; }
.structured-answers label { display: grid; grid-template-columns: minmax(90px, 1fr) minmax(180px, 260px); align-items: center; gap: 12px; line-height: 1.5; }
.structured-answers select { min-height: 42px; border: 1px solid #cbd5e1; border-radius: 6px; padding: 7px 10px; background: #fff; }
.structured-answers.matching-answers label { grid-template-columns: 1fr minmax(110px, 160px); }
.option-list { margin-top: 16px; padding-top: 14px; border-top: 1px dashed #cbd5e1; display: grid; gap: 10px; font-size: 14px; line-height: 1.7; color: #1f2937; }
.option-list p { margin: 0; }
.option-list strong { color: var(--xy-brand-700); margin-right: 4px; }
.subjective-answer { width: 100%; min-height: 220px; resize: vertical; box-sizing: border-box; border: 1px solid #cbd5e1; border-radius: 6px; padding: 14px; font: inherit; line-height: 1.7; }
.reference-answer { white-space: pre-wrap; padding: 10px; background: #fff; }
.option-button { width: 100%; min-height: 52px; text-align: left; display: flex; align-items: center; gap: 12px; padding: 10px 14px; border: 1px solid #dbe2ea; border-radius: 6px; background: #fff; color: #334155; cursor: pointer; line-height: 1.5; }
.option-button.selected { border-color: var(--xy-brand-700); background: var(--xy-brand-50); }
.option-button.correct { border-color: #16a34a; background: #f0fdf4; }
.option-button.wrong { border-color: #dc2626; background: #fef2f2; }
.option-letter { width: 28px; height: 28px; flex: 0 0 auto; display: grid; place-items: center; border-radius: 50%; background: #edf1f5; font-weight: 700; }
.analysis-panel { margin-top: 18px; padding: 15px; background: #f8fafc; border-left: 3px solid var(--xy-brand-700); }
.analysis-title { display: flex; align-items: center; gap: 6px; font-weight: 700; }
.analysis-panel p { margin: 8px 0 0; line-height: 1.65; color: #475569; }
.analysis-question { margin-top: 12px; padding-top: 12px; border-top: 1px dashed #cbd5e1; }
.analysis-subtitle { font-weight: 700; color: #334155; font-size: 13px; }
.analysis-missing { color: #94a3b8; font-size: 13px; }
.analysis-body { margin-top: 2px; }
.analysis-locate { margin-top: 10px; padding: 8px 10px; background: #f1f5f9; border-left: 3px solid #94a3b8; border-radius: 4px; font-size: 13px; color: #334155; }
.analysis-block { margin-top: 12px; }
.analysis-block-title { font-size: 12px; font-weight: 700; color: var(--xy-brand-700, #2563eb); margin-bottom: 2px; }
.analysis-correct { margin-top: 10px; padding: 8px 10px; background: #f0fdf4; border-left: 3px solid #86efac; border-radius: 4px; }
.analysis-list { list-style: none; margin: 0; padding: 0; }
.analysis-list li { position: relative; padding-left: 14px; margin-top: 6px; line-height: 1.7; }
.analysis-list li::before { content: ''; position: absolute; left: 2px; top: 0.66em; width: 5px; height: 5px; border-radius: 50%; background: #cbd5e1; }
.analysis-fold { margin-top: 12px; }
.analysis-fold > summary { cursor: pointer; font-weight: 600; font-size: 13px; color: var(--xy-brand-700); list-style: none; }
.analysis-fold > summary::-webkit-details-marker { display: none; }
.analysis-fold > summary::before { content: '▸ '; }
.analysis-fold[open] > summary::before { content: '▾ '; }
.analysis-fold p { white-space: pre-wrap; }
.analysis-source { background: #fff; border: 1px solid #e2e8f0; border-radius: 6px; padding: 10px 12px; }
.analysis-source p { max-height: 320px; overflow-y: auto; }
.analysis-vocab { margin-top: 12px; border: 1px solid #e2e8f0; border-radius: 8px; background: #fff; overflow: hidden; }
.analysis-vocab > summary { display: flex; align-items: center; gap: 8px; padding: 9px 12px; cursor: pointer; list-style: none; font-size: 13px; background: #eef2ff; }
.analysis-vocab > summary::-webkit-details-marker { display: none; }
.analysis-vocab-title { font-weight: 700; color: #1e3a8a; }
.analysis-vocab-title::before { content: '📖 '; }
.analysis-vocab-count { font-size: 12px; color: #64748b; }
.analysis-vocab-hint { margin-left: auto; font-size: 12px; color: #94a3b8; }
.analysis-vocab-hint::before { content: '▾ '; }
.analysis-vocab:not([open]) .analysis-vocab-hint::before { content: '▸ '; }
.analysis-vocab-list { margin: 0; padding: 4px 12px 10px; list-style: none; }
.analysis-vocab-list li { display: flex; flex-wrap: wrap; align-items: baseline; gap: 6px; padding: 6px 0; border-bottom: 1px dashed #eef2f7; font-size: 13px; line-height: 1.65; }
.analysis-vocab-list li:last-child { border-bottom: 0; }
.vocab-word { font-weight: 700; color: #1d4ed8; }
.vocab-phonetic { font-size: 12px; color: #64748b; }
.vocab-pos { font-size: 12px; font-style: italic; color: #7c3aed; }
.vocab-meaning { color: #334155; }
@media (max-width: 640px) {
  .analysis-vocab-list li { gap: 4px; }
  .vocab-meaning { flex-basis: 100%; }
}

.writing-score { margin-top: 12px; display: flex; flex-wrap: wrap; align-items: center; gap: 8px 12px; }
.writing-score-btn { border: none; border-radius: 6px; padding: 9px 18px; background: var(--xy-brand-600, #2563eb); color: #fff; font-weight: 700; cursor: pointer; }
.writing-score-btn:hover { filter: brightness(1.08); }
.writing-score-btn:disabled { background: #94a3b8; cursor: default; }
.writing-score-hint { color: #64748b; font-size: 12px; }
.writing-score-note { flex: 1 1 100%; margin: 0; color: #b45309; font-size: 12px; }
.writing-score-card { flex: 1 1 100%; padding: 14px; background: #fff; border: 1px solid #e2e8f0; border-radius: 8px; }
.writing-total { font-size: 15px; color: #334155; }
.writing-total strong { font-size: 28px; color: var(--xy-brand-700); }
.writing-dims { list-style: none; margin: 10px 0 0; padding: 0; display: grid; gap: 8px; }
.writing-dims li { display: grid; grid-template-columns: 48px 62px 1fr; align-items: baseline; gap: 8px; font-size: 13px; }
.writing-dim-name { font-weight: 700; color: #334155; }
.writing-dim-score { color: var(--xy-brand-700); font-weight: 700; font-variant-numeric: tabular-nums; }
.writing-dim-comment { color: #64748b; }
.writing-list { margin-top: 12px; }
.writing-list-title { font-weight: 700; font-size: 13px; color: #334155; }
.writing-list ul { margin: 6px 0 0; padding-left: 20px; color: #475569; font-size: 13px; line-height: 1.65; }
.writing-sample { margin-top: 12px; padding: 10px; background: #f1f5f9; border-radius: 6px; color: #475569; font-size: 13px; line-height: 1.65; }
.writing-feedback { white-space: pre-wrap; line-height: 1.7; color: #475569; }
@media (max-width: 900px) {
  .writing-dims li { grid-template-columns: 42px 56px 1fr; font-size: 12px; }
  .writing-score-btn { flex: 1 1 100%; }
}
.exam-actions { display: flex; justify-content: space-between; gap: 12px; margin-top: 14px; }
.score-bar { margin-top: 12px; text-align: center; color: #334155; }
.score-bar strong { color: var(--xy-brand-700); font-size: 20px; }
@media (max-width: 560px) {
  .papers-header { padding: 14px 12px 10px; }
  .paper-library, .exam-area { padding: 12px 12px 96px; }
  .paper-item { align-items: stretch; flex-direction: column; padding: 15px; }
  .start-button { width: 100%; }
  .paper-actions { width: 100%; }
  .paper-actions .start-button { flex: 1; }
  .question-card { padding: 16px; }
  .structured-answers label { grid-template-columns: 1fr; }
}

@media (max-width: 900px) {
  /* 移动端：上下堆叠，拖拽条变成「可上下滑动的把手」，调整正文高度 */
  .exam-split { flex-direction: column; }
  .split-handle {
    display: flex; align-items: center; justify-content: center;
    flex: 0 0 30px; margin: 0; cursor: row-resize;
    touch-action: none;
  }
  .split-handle::after {
    position: static; inset: auto;
    width: 56px; height: 5px; border-radius: 3px;
    background: #cbd5e1;
  }
  .split-handle:hover::after, .split-handle:active::after, body.pane-resizing .split-handle::after { background: var(--xy-brand-700); }
  body.pane-resizing { cursor: row-resize; }
  .passage-pane {
    position: static; max-height: none;
    flex-basis: auto !important; width: 100%; margin-bottom: 0;
    min-height: 120px;
  }
  .passage-body { padding: 14px; font-size: 15px; }
  /* 移动端：题目区也变成独立可滑动区域（拖正文把手可改变两区高度分配） */
  .question-card {
    max-height: calc(100dvh - 235px);
    overflow-y: auto;
    overscroll-behavior: contain;
    -webkit-overflow-scrolling: touch;
  }
}
</style>
