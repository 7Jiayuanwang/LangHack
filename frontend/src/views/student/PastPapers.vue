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
      <span
        v-if="activePaper"
        :class="['mode-badge', examMode, { danger: examMode === 'exam' && remainingSeconds <= 300 }]"
      >
        <template v-if="examMode === 'exam'">⏱ 剩余 {{ remainingTimeText }}</template>
        <template v-else>📖 学习模式</template>
      </span>
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
            <button class="start-button" type="button" :disabled="paper.status !== 'ready'" @click="openModeDialog(paper)">
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
            <span>{{ readingPassageLabel ? `正文材料 · ${readingPassageLabel}` : '正文材料' }}</span>
            <button class="pane-toggle" type="button" title="收起正文" aria-label="收起正文" @click="passagePaneVisible = false">
              <el-icon><DArrowLeft /></el-icon>
            </button>
          </div>
          <div ref="passageBody" class="passage-body">
            <div v-if="currentQuestion?.passage && questionType === 'cloze'" class="passage-rich" v-html="clozePassageHtml"></div>
            <div v-else-if="cetClozeStemHtml" class="passage-rich" v-html="cetClozeStemHtml"></div>
            <template v-else-if="currentQuestion?.passage">
              <div v-if="passageDirections" class="passage-directions">
                <span class="pd-label">题目说明</span>
                <p>{{ passageDirections }}</p>
              </div>
              <div class="passage-content">{{ passageContent }}</div>
            </template>
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
          <span>{{ sectionLabel }}</span>
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
        <h2 v-if="!stemInPane"><span v-if="readingQuestionNumber" class="q-number">{{ readingQuestionNumber }}.</span>{{ clozePerBlankMode ? clozeDirections : currentQuestion.stem }}</h2>

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
            :disabled="answerLocked"
            @click="selectAnswer(index)"
          >
            <span class="option-letter">{{ optionLetter(index) }}</span>
            <span>{{ cleanOptionText(option) }}</span>
          </button>
        </div>

        <div v-else-if="questionType === 'cloze'" class="structured-answers">
          <!-- 考研完形：每空只有自己的 4 个选项（原版 A–D），不做跨空去重 -->
          <template v-if="clozePerBlankMode">
            <label v-for="(_, index) in currentQuestion.answers" :key="index">
              第 {{ index + 1 }} 空
              <select :value="answerValue?.[index] ?? ''" :disabled="answerLocked" @change="setArrayAnswer(index, $event.target.value)">
                <option value="">请选择</option>
                <option v-for="(option, oi) in clozeBlankOptions(index)" :key="oi" :value="oi">{{ optionLetter(oi) }}. {{ option }}</option>
              </select>
            </label>
          </template>
          <!-- 四级/六级选词填空：共享词库，同一选项只能被一个空选中 -->
          <template v-else>
            <label v-for="(_, index) in currentQuestion.answers" :key="index">
              第 {{ clozeBlankNumber(index) }} 空
              <select :value="answerValue?.[index] ?? ''" :disabled="answerLocked" @change="setArrayAnswer(index, $event.target.value)">
                <option value="">请选择</option>
                <option v-for="(option, optionIndex) in currentQuestion.options" :key="optionIndex" :value="optionIndex" :disabled="isOptionTaken(optionIndex, index)">{{ optionLetter(optionIndex) }}. {{ option }}{{ isOptionTaken(optionIndex, index) ? '（已被选）' : '' }}</option>
              </select>
            </label>
          </template>
        </div>

        <div v-else-if="questionType === 'matching'" class="structured-answers matching-answers">
          <label v-for="(item, index) in currentQuestion.items" :key="item.id">
            <span><span class="q-number">{{ matchingItemNumber(item, index) }}.</span>{{ item.prompt }}</span>
            <select :value="answerValue?.[item.id] ?? ''" :disabled="answerLocked" @change="setMatchingAnswer(item.id, $event.target.value)">
              <option value="">请选择对应段落</option>
              <option v-for="(option, optionIndex) in currentQuestion.options" :key="optionIndex" :value="optionIndex" :disabled="isOptionTaken(optionIndex, item.id)">{{ optionLetter(optionIndex) }}{{ isOptionTaken(optionIndex, item.id) ? '（已被选）' : '' }}</option>
            </select>
          </label>
        </div>

        <textarea
          v-else
          class="subjective-answer"
          :value="answerValue || ''"
          :disabled="answerLocked"
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

        <div v-if="showAnswers" class="analysis-panel">
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
          <div v-if="currentAiFeedback" class="ai-feedback">
            <strong>智能判分：{{ currentAiFeedback.score }}/100</strong>
            <p v-if="currentAiFeedback.summary">{{ currentAiFeedback.summary }}</p>
            <p v-if="currentAiFeedback.issues?.length">问题：{{ currentAiFeedback.issues.join('；') }}</p>
            <p v-if="currentAiFeedback.suggestions?.length">建议：{{ currentAiFeedback.suggestions.join('；') }}</p>
            <p v-if="currentAiFeedback.revisedAnswer">建议答案：{{ currentAiFeedback.revisedAnswer }}</p>
          </div>
        </div>
        </section>
      </div>

      <footer class="exam-actions">
        <div class="actions-group">
          <button type="button" class="secondary-button" :disabled="currentIndex === 0" @click="switchQuestion(currentIndex - 1)">上一题</button>
          <button v-if="examMode === 'study' && !submitted" type="button" class="secondary-button reveal-button" @click="revealed = !revealed">
            {{ revealed ? '收起解析' : '查看解析' }}
          </button>
        </div>
        <div class="actions-group">
          <button v-if="currentIndex < activePaper.questions.length - 1" type="button" class="primary-button" @click="switchQuestion(currentIndex + 1)">下一题</button>
          <button v-else-if="!submitted && !timeUp" type="button" class="primary-button" :disabled="answeredCount === 0 || submitting" @click="submitPaper">{{ submitting ? '保存中...' : '交卷' }}</button>
          <button v-else type="button" class="primary-button" @click="leavePaper">查看试卷列表</button>
        </div>
      </footer>

      <div v-if="submitted || timeUp" class="score-bar" aria-live="polite">
        客观题得分 <strong>{{ score }}/{{ objectiveCount }}</strong>
        <span v-if="gradingResult?.finalScorePercentage != null"> · 综合得分 <strong>{{ gradingResult.finalScorePercentage }}%</strong></span>
        <span v-if="gradingResult?.aiScorePercentage != null"> · 主观题 AI 得分 {{ gradingResult.aiScorePercentage }}/100</span>
        <span v-else-if="subjectiveCount"> · 主观题智能分析暂不可用</span>
      </div>
    </main>

    <div v-if="modeDialogVisible" class="mode-overlay" @click.self="closeModeDialog">
      <div class="mode-dialog" role="dialog" aria-modal="true" aria-label="选择做题模式">
        <button class="mode-close" type="button" aria-label="关闭" @click="closeModeDialog">✕</button>
        <h3>选择做题方式</h3>
        <p class="mode-sub">{{ pendingPaper?.title }}</p>
        <div class="mode-options">
          <button class="mode-option study" type="button" @click="startPaper(pendingPaper, 'study')">
            <span class="mode-icon">📖</span>
            <span class="mode-name">学习模式</span>
            <span class="mode-desc">不计时，可边做边查看解析</span>
          </button>
          <button class="mode-option exam" type="button" @click="startPaper(pendingPaper, 'exam')">
            <span class="mode-icon">⏱️</span>
            <span class="mode-name">测试模式</span>
            <span class="mode-desc">限时 {{ pendingPaper?.duration || 120 }} 分钟，时间到自动交卷并禁止继续作答</span>
          </button>
        </div>
      </div>
    </div>

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
const gradingResult = ref(null)
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
  stopTimer()
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

const currentAiFeedback = computed(() => {
  const rows = gradingResult.value?.grading?.questions
  if (!Array.isArray(rows) || !currentQuestion.value) return null
  return rows.find(item => String(item.questionId) === String(currentQuestion.value.id)) || null
})
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
function isListeningPaperQuestion(q) {
  if (!q) return false
  if (q.type === 'listening') return true
  if (typeof q.section === 'string' && q.section.includes('听力')) return true
  return false
}
const isListeningQuestion = computed(() => isListeningPaperQuestion(currentQuestion.value))
// 听力区 = 试卷里连续的一段听力题（六级卷：写作 → 听力 25 题 → 选词/阅读…，一段录音通常对多个小题）。
// 「离开听力区」= 当前题是听力题、而目标题不是听力题：向前=离开最后一道听力题，向后=离开第一道听力题。
// 弹窗与「听力自动结束」共用这个触发点；听力区内部切小题不打扰（录音继续播）。
function leavingListeningTo(target) {
  if (!isListeningQuestion.value) return false
  return !isListeningPaperQuestion(activePaper.value?.questions?.[target])
}
// 听力未听完：正在播放，或播到一半被暂停（录音一旦切题就会被丢弃，所以要提醒用户）
const listeningUnfinished = computed(() => {
  if (!isListeningQuestion.value) return false
  const a = audioElement.value
  if (!a || !a.src) return false
  if (lpPlaying.value) return true
  const duration = lpDuration.value
  if (!duration || a.ended) return false
  return a.currentTime > 0.5 && a.currentTime < duration - 0.5
})
function stopListening() {
  const a = audioElement.value
  if (a) {
    a.pause()
    a.currentTime = 0
    a.src = ''
  }
  lpPlaying.value = false
  lpCurrent.value = 0
  lpDuration.value = 0
}
// 切题入口：
// - 听力区内部切小题：直接切，录音继续播（不打扰）
// - 离开听力区：自动结束听力；若录音还没听完，先确认「关闭 / 取消」
let switchingQuestion = false
async function switchQuestion(target) {
  if (switchingQuestion) return
  const total = activePaper.value?.questions.length || 1
  const next = Math.max(0, Math.min(target, total - 1))
  if (next === currentIndex.value) return
  // 离开听力区：当前是听力题，而目标题已经不是听力题
  const leavingListening = leavingListeningTo(next)
  if (!(leavingListening && listeningUnfinished.value)) {
    // 听力自动结束：离开听力区（含录音已播完 / 还没开播的情况）直接收尾，不留后台播放
    if (leavingListening) stopListening()
    currentIndex.value = next
    return
  }
  switchingQuestion = true
  try {
    const direction = next > currentIndex.value ? '下一题' : '上一题'
    await ElMessageBox.confirm(`您还未听完听力，是否关闭听力并切换到${direction}？`, '听力播放中', {
      confirmButtonText: '关闭',
      cancelButtonText: '取消',
      type: 'warning',
    })
    stopListening()
    currentIndex.value = next
  } catch (error) {
    // 点「取消」或关闭弹窗：留在当前听力题，录音继续播放
  } finally {
    switchingQuestion = false
  }
}
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
// 锁题：交卷后或测试模式时间到后禁止作答
const answerLocked = computed(() => submitted.value || timeUp.value)
// 解析与答案高亮：交卷后、时间到后始终显示；学习模式可主动展开（切题后自动收起）
const showAnswers = computed(() => submitted.value || revealed.value || timeUp.value)

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

// ===== 学习模式 / 测试模式 =====
// 学习模式：不计时，可边做边展开解析；测试模式：按试卷时长倒计时，时间到自动交卷并锁题
const modeDialogVisible = ref(false)
const pendingPaper = ref(null)
const examMode = ref('study')
const remainingSeconds = ref(0)
const timeUp = ref(false)
const revealed = ref(false)
let timerId = null

const remainingTimeText = computed(() => {
  const total = Math.max(0, remainingSeconds.value)
  const h = Math.floor(total / 3600)
  const m = Math.floor((total % 3600) / 60)
  const s = total % 60
  const mm = String(m).padStart(2, '0')
  const ss = String(s).padStart(2, '0')
  return h > 0 ? `${h}:${mm}:${ss}` : `${mm}:${ss}`
})

function openModeDialog(paper) {
  if (paper.status !== 'ready') return
  pendingPaper.value = paper
  modeDialogVisible.value = true
}

function closeModeDialog() {
  modeDialogVisible.value = false
  pendingPaper.value = null
}

function startPaper(paper, mode = 'study') {
  if (!paper || paper.status !== 'ready') return
  stopTimer()
  examMode.value = mode
  timeUp.value = false
  revealed.value = false
  activePaper.value = paper
  currentIndex.value = 0
  answers.value = {}
  submitted.value = false
  gradingResult.value = null
  playbackRate.value = 1
  stopListening()
  resetWritingScore()
  closeModeDialog()
  if (mode === 'exam') {
    remainingSeconds.value = Math.max(60, Number(paper.duration || 120) * 60)
    timerId = window.setInterval(onTimerTick, 1000)
  } else {
    remainingSeconds.value = 0
  }
}

function onTimerTick() {
  if (examMode.value !== 'exam' || submitted.value || timeUp.value) {
    stopTimer()
    return
  }
  remainingSeconds.value -= 1
  if (remainingSeconds.value <= 0) {
    remainingSeconds.value = 0
    stopTimer()
    handleTimeUp()
  }
}

function stopTimer() {
  if (timerId) {
    clearInterval(timerId)
    timerId = null
  }
}

async function handleTimeUp() {
  timeUp.value = true
  // 停止正在播放的听力材料
  stopListening()
  if (answeredCount.value > 0) {
    ElMessage.warning('考试时间到，系统已自动交卷，禁止继续作答')
    await doSubmit()
  } else {
    ElMessage.warning('考试时间到，本次测试结束，禁止继续作答')
  }
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
  // 学习模式下切题后自动收起上一题的解析，重新作答
  revealed.value = false
  // 切换题目时，若当前不是听力题则自动暂停并停止播放，避免录音继续响
  if (!isListeningQuestion.value) stopListening()
  else syncPlaybackRate()
})

function selectAnswer(index) {
  answers.value = { ...answers.value, [currentQuestion.value.id]: index }
}

// ===== 考研完形（原版形态）=====
// 导入时把每空 4 个选项按空序拍平进 options（20 空 × 4 = 80），answers 每项是该空内部 0–3 的下标。
// 这种形态下每空只展示属于自己的 A–D 四个选项；四级/六级选词填空（共享词库 15 项）不受影响。
const CLOZE_OPTION_SIZE = 4
const clozePerBlankMode = computed(() => {
  const q = currentQuestion.value
  if (!q || questionType.value !== 'cloze') return false
  const answers = q.answers || []
  return answers.length > 0 && (q.options || []).length === answers.length * CLOZE_OPTION_SIZE
})

function clozeBlankOptions(index) {
  const options = currentQuestion.value?.options || []
  return options.slice(index * CLOZE_OPTION_SIZE, index * CLOZE_OPTION_SIZE + CLOZE_OPTION_SIZE)
}

// 考研完形的 stem 是 "Directions: ... 1. ____ 2. ____ ..." 的拼接，标题里只保留说明文字
const clozeDirections = computed(() => {
  const stem = currentQuestion.value?.stem || ''
  const cut = stem.search(new RegExp(`\\d{1,2}\\.\\s*_{${CLOZE_OPTION_SIZE - 1},}`))
  return (cut > 0 ? stem.slice(0, cut) : stem).trim()
})

function escapeHtml(text) {
  return String(text).replace(/[&<>"']/g, ch => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[ch]))
}

// ===== 翻译题：卷面说明（Directions）与待翻译正文分开 =====
// CET 翻译（109/111/112/114/115/116…）和考研翻译（139/156/158…）的 passage 把卷面说明和正文
// 挤在同一个字符串里 —— "For this part, you are allowed 30 minutes … Answer Sheet 2." 紧跟着中文段落，
// 考研则是 "Directions: Read the following text carefully … ANSWER SHEET. (10 points)" 紧跟着英文原文；
// 有的卷首还带 markdown 的 "## "。直接渲染两段会糊成一整块，所以在这里把开头的说明单独拆出来。
const DIRECTIONS_LEAD_RE = /^\s*(?:#{1,6}\s*)?((?:For this part|Directions)\b[\s\S]{0,600}?(?:Answer Sheet\s*\d*\s*\.|ANSWER SHEET\s*\d*\s*\.|points?\s*\)\s*\.?)(?:\s*\(\s*\d+\s*points?\s*\))?)\s*/i

const passageParts = computed(() => {
  const passage = String(currentQuestion.value?.passage || '')
  const match = passage.match(DIRECTIONS_LEAD_RE)
  const rest = match ? passage.slice(match[0].length).trim() : ''
  // 说明后面没剩正文时不做拆分，避免把整段说明吞掉后正文栏空掉
  if (!match || !rest) return { directions: '', content: passage }
  return { directions: match[1].trim(), content: rest }
})
const passageDirections = computed(() => passageParts.value.directions)
const passageContent = computed(() => passageParts.value.content)

// ===== 四六级阅读题号 =====
// 卷面上阅读三节固定编号：选词填空 26–35、段落匹配 36–45、仔细阅读 46–55。
// 导入数据里的 number 并不可靠（76/85/92 等卷的仔细阅读被写成 29–38、选词填空写成 27），
// 所以按「题型 + 在该题型内的次序」推导标准题号；段落匹配优先用条目自带 id（各卷均为 36–45）。
const CET_CLOZE_START = 26
const CET_MATCH_START = 36
const CET_READING_START = 46

// 一次遍历算出：每道仔细阅读题的标准题号（46 起）+ 它属于第几篇文章（第一篇 / 第二篇）。
// 文章分组按正文文本切换判定——section 有的卷子写 "仔细阅读 · Passage One/Two"，有的只写 "仔细阅读"，
// 靠 section 分不出来；正文文本一变就是下一篇。
const readingMeta = computed(() => {
  const map = new Map()
  const ranges = new Map()
  const questions = activePaper.value?.questions || []
  let number = CET_READING_START
  let passageIndex = 0
  let lastPassage = null
  for (const q of questions) {
    if ((q.type || 'single_choice') !== 'single_choice') continue
    if (typeof q.section !== 'string' || !q.section.includes('仔细阅读')) continue
    const passage = String(q.passage || '').trim()
    const key = passage || `@section:${q.section}`
    if (key !== lastPassage) {
      passageIndex += 1
      lastPassage = key
    }
    map.set(q.id, { number, passageIndex })
    const range = ranges.get(passageIndex) || { start: number, end: number }
    range.end = number
    ranges.set(passageIndex, range)
    number += 1
  }
  return { map, ranges }
})

// 本题在「仔细阅读」组里的序号 → 标准题号（46 起）
const readingQuestionNumber = computed(() => {
  const info = readingMeta.value.map.get(currentQuestion.value?.id)
  return info ? String(info.number) : ''
})

// 第几篇文章（含题号范围），如「第二篇（51–55 题）」
const readingPassageLabel = computed(() => {
  const info = readingMeta.value.map.get(currentQuestion.value?.id)
  if (!info) return ''
  const cn = ['一', '二', '三', '四', '五'][info.passageIndex - 1] || String(info.passageIndex)
  const range = readingMeta.value.ranges.get(info.passageIndex)
  return `第${cn}篇${range ? `（${range.start}–${range.end} 题）` : ''}`
})

// 题眉里的分节名：把 "仔细阅读 · Passage One" 统一成 "仔细阅读 · 第一篇（46–50 题）"
const sectionLabel = computed(() => {
  const section = currentQuestion.value?.section || ''
  const label = readingPassageLabel.value
  if (!label) return section
  const base = section.replace(/[·\-—\s]*Passage\s+(One|Two|Three|Four)/i, '').replace(/[·\-—]\s*$/, '').trim()
  return `${base || '仔细阅读'} · ${label}`
})

// 选词填空：第 index 个空的标准题号（26 起）
function clozeBlankNumber(index) {
  return CET_CLOZE_START + index
}

// 段落匹配：第 index 条陈述的标准题号（优先用条目自带的 id）
function matchingItemNumber(item, index) {
  const id = String(item?.id ?? '').trim()
  return /^\d+$/.test(id) ? id : String(CET_MATCH_START + index)
}

// 四六级选词填空的题干就是那篇文章，里面的 ____ 按顺序编号（26–35）显示。
// 下划线数量与空数对不上时返回空串，退回原文渲染，避免编错号。
const cetClozeStemHtml = computed(() => {
  const q = currentQuestion.value
  if (!q || questionType.value !== 'cloze' || clozePerBlankMode.value) return ''
  const text = q.stem || q.passage || ''
  const total = (q.answers || []).length
  const marks = text.match(/_{2,}/g) || []
  if (!total || marks.length !== total) return ''
  let blank = 0
  return escapeHtml(text).replace(/_{2,}/g, () => `<span class="cloze-blank">${CET_CLOZE_START + blank++}</span>`)
})

// 完形正文：把空格标记渲染成带编号的下划线空位。导入文本里标记形态不一（"1\_"、"1,"、裸数字），
// 也有 OCR 损伤（"10"→"l0"、编号丢失）。这里按 1→N 顺序严格匹配完整数字 token（"3,000"、"1650-1180"、
// "1960s" 整体识别不会被拆开误吃），匹配到就画线；源文本丢失的编号只能降级不画线。
const clozePassageHtml = computed(() => {
  const q = currentQuestion.value
  const passage = q?.passage || ''
  const total = (q?.answers || []).length
  if (!passage || !total) return ''
  const src = escapeHtml(passage)
  let expected = 1
  let out = ''
  let last = 0
  for (const m of src.matchAll(/\d{1,3}(?:,\d{3})+|\d+/g)) {
    if (Number(m[0]) === expected) {
      out += src.slice(last, m.index) + '<span class="cloze-blank">' + expected + '</span>'
      last = m.index + m[0].length
      expected += 1
      if (expected > total) break
    }
  }
  return out + src.slice(last)
})

// 选词填空 / 段落匹配：同一题内每个选项只能被一个空（或段落）选中，
// 已被其它空选中的选项在其它下拉里禁用（四级/六级/考研所有题通用，按题型生效）
function isOptionTaken(optionIndex, ownKey) {
  const value = answerValue.value
  if (questionType.value === 'cloze' && Array.isArray(value)) {
    return value.some((item, index) => index !== ownKey && item === optionIndex)
  }
  if (questionType.value === 'matching' && value && typeof value === 'object') {
    return Object.entries(value).some(([key, item]) => key !== String(ownKey) && item === optionIndex)
  }
  return false
}

function setArrayAnswer(index, value) {
  const next = [...(answerValue.value || [])]
  const picked = value === '' ? null : Number(value)
  // 兜底：即便下拉禁用被绕过，也不允许与其它空重复
  if (picked !== null && next.some((item, i) => i !== index && item === picked)) return
  next[index] = picked
  answers.value = { ...answers.value, [currentQuestion.value.id]: next }
}

function setMatchingAnswer(itemId, value) {
  const next = { ...(answerValue.value || {}) }
  const picked = value === '' ? null : Number(value)
  // 兜底：同上，段落不允许与其它段落重复
  if (picked !== null && Object.entries(next).some(([key, item]) => key !== String(itemId) && item === picked)) return
  next[itemId] = picked
  answers.value = { ...answers.value, [currentQuestion.value.id]: next }
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
    correct: showAnswers.value && index === currentQuestion.value.answer,
    wrong: showAnswers.value && selected && index !== currentQuestion.value.answer
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
  await doSubmit()
}

async function doSubmit() {
  if (submitted.value || submitting.value || !activePaper.value) return
  submitting.value = true
  try {
    const response = await submitPastPaperAttempt({
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
    gradingResult.value = response?.data || response || null
    submitted.value = true
    stopTimer()
    revealed.value = false
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
  stopTimer()
  stopListening()
  activePaper.value = null
  answers.value = {}
  submitted.value = false
  timeUp.value = false
  revealed.value = false
  gradingResult.value = null
}

onMounted(loadImportedPapers)
</script>

<style scoped>
.papers-page { height: 100%; min-height: 0; display: flex; flex-direction: column; background: #f5f7fa; color: #1f2937; }
.papers-header { flex: 0 0 auto; display: flex; align-items: center; gap: 12px; padding: 18px 20px 14px; background: #fff; border-bottom: 1px solid #e5e7eb; }
.papers-header h1 { margin: 0; font-size: 22px; line-height: 1.3; }
.papers-header p { margin: 3px 0 0; color: #6b7280; font-size: 13px; }
.import-button { margin-left: auto; min-height: 40px; padding: 0 14px; border: 1px solid var(--xy-brand-700); border-radius: 6px; background: #fff; color: var(--xy-brand-700); font-weight: 600; display: inline-flex; align-items: center; gap: 6px; cursor: pointer; }
.mode-badge { margin-left: auto; flex: 0 0 auto; display: inline-flex; align-items: center; gap: 6px; min-height: 32px; padding: 4px 14px; border-radius: 999px; font-weight: 700; font-size: 13px; white-space: nowrap; }
.mode-badge.study { background: #ecfdf5; color: #047857; border: 1px solid #a7f3d0; }
.mode-badge.exam { background: #eff6ff; color: var(--xy-brand-700); border: 1px solid #bfdbfe; font-variant-numeric: tabular-nums; }
.mode-badge.exam.danger { background: #fef2f2; color: #dc2626; border-color: #fecaca; animation: mode-pulse 1s ease-in-out infinite; }
@keyframes mode-pulse { 50% { opacity: .6; } }
.mode-overlay { position: fixed; inset: 0; z-index: 2000; display: grid; place-items: center; background: rgba(15, 23, 42, .55); padding: 16px; }
.mode-dialog { position: relative; width: 100%; max-width: 560px; background: #fff; border-radius: 12px; padding: 26px 24px 24px; box-shadow: 0 20px 50px rgba(15, 23, 42, .3); }
.mode-dialog h3 { margin: 0; font-size: 20px; text-align: center; }
.mode-sub { margin: 6px 0 18px; text-align: center; color: #64748b; font-size: 13px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.mode-close { position: absolute; top: 12px; right: 12px; width: 32px; height: 32px; border: 0; border-radius: 6px; background: transparent; color: #94a3b8; font-size: 16px; cursor: pointer; }
.mode-close:hover { background: #f1f5f9; color: #334155; }
.mode-options { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; }
.mode-option { display: flex; flex-direction: column; align-items: center; gap: 6px; padding: 22px 14px 18px; border: 1px solid #e2e8f0; border-radius: 10px; background: #fff; cursor: pointer; text-align: center; transition: border-color .15s ease, box-shadow .15s ease, transform .15s ease; }
.mode-option:hover { border-color: var(--xy-brand-700); box-shadow: 0 6px 18px rgba(29, 78, 216, .14); transform: translateY(-2px); }
.mode-option.study:hover { border-color: #059669; box-shadow: 0 6px 18px rgba(5, 150, 105, .14); }
.mode-icon { font-size: 30px; line-height: 1; }
.mode-name { font-size: 16px; font-weight: 700; color: #1f2937; }
.mode-option.study .mode-name { color: #047857; }
.mode-option.exam .mode-name { color: var(--xy-brand-700); }
.mode-desc { color: #64748b; font-size: 12px; line-height: 1.6; }
@media (max-width: 560px) {
  .mode-options { grid-template-columns: 1fr; }
  .mode-badge { padding: 4px 10px; font-size: 12px; }
}
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
/* 翻译题：卷面说明（Directions）与待翻译正文分开成两块 */
.passage-directions { margin: 0 0 14px; padding: 10px 12px; border-left: 3px solid #93b4f8; border-radius: 0 6px 6px 0; background: #f5f8ff; }
.passage-directions .pd-label { display: block; margin-bottom: 4px; font-size: 12px; font-weight: 700; color: var(--xy-brand-700); letter-spacing: .4px; }
.passage-directions p { margin: 0; color: #475569; font-size: 13px; line-height: 1.75; }
.passage-content { padding-top: 2px; }
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
/* 四六级阅读题号（选词填空 26–35 / 段落匹配 36–45 / 仔细阅读 46–55） */
.q-number { color: var(--xy-brand-700); font-weight: 700; margin-right: 6px; }
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
/* 完形正文里的编号空位：原版试卷的下划线填空样式。
   注意：passage 是 v-html 注入的，scoped 样式作用不到，必须用 :deep() 穿透 */
.passage-rich :deep(.cloze-blank) { display: inline-block; min-width: 3em; padding: 0 10px; margin: 0 4px; text-align: center; font-weight: 600; font-size: 13px; color: #475569; border-bottom: 1.5px solid #1f2937; line-height: 1.4; }
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
.ai-feedback { margin-top: 14px; padding-top: 12px; border-top: 1px dashed #cbd5e1; color: #0f766e; }
.ai-feedback p { color: #334155; }
.exam-actions { display: flex; justify-content: space-between; gap: 12px; margin-top: 14px; }
.actions-group { display: flex; gap: 12px; flex-wrap: wrap; }
.reveal-button { border-color: #a7f3d0; color: #047857; background: #ecfdf5; }
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
  .exam-actions { flex-direction: column-reverse; }
  .actions-group { width: 100%; }
  .actions-group button { flex: 1; }
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
