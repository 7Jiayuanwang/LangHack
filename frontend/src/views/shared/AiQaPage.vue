<template>
  <div class="ai-qa-page">
    <header class="qa-header">
      <div class="qa-header-inner">
        <button class="qa-back-btn" @click="$router.back()">
          <el-icon><ArrowLeft /></el-icon>
          <span>{{ backLabel }}</span>
        </button>
        <div class="qa-avatar">
          <el-icon :size="22"><Monitor /></el-icon>
        </div>
      </div>
    </header>

    <div class="qa-context-bar" v-if="selectedText">
      <div class="context-card">
        <div class="context-accent"></div>
        <div class="context-body">
          <div class="context-label">
            <el-icon><ChatLineSquare /></el-icon>
            <span>选中段落</span>
          </div>
          <p class="context-text">{{ selectedText }}</p>
          <p class="context-source" v-if="articleTitle">
            <el-icon :size="14"><Reading /></el-icon>
            来自文章：{{ articleTitle }}
          </p>
        </div>
      </div>
    </div>

    <main class="qa-chat-area" ref="chatContainer" id="chat-container">
      <div class="empty-hint" v-if="messages.length === 0">
        <p>点击下方快捷提问或输入你的问题</p>
      </div>

      <template v-for="(msg, index) in messages" :key="index">
        <div class="msg-row" :class="msg.role === 'user' ? 'msg-user' : 'msg-ai'">
          <div class="msg-avatar" v-if="msg.role !== 'user'">
            <el-icon :size="18"><Monitor /></el-icon>
          </div>
          <div class="msg-bubble" :class="msg.role === 'user' ? 'user-bubble' : 'ai-bubble'">
            <div v-if="msg.role === 'user'">{{ msg.content }}</div>
            <div v-else v-html="msg.content"></div>
            <div v-if="msg.role === 'assistant' && msg.callLogId" class="feedback-row">
              <button
                class="feedback-btn"
                :class="{ active: msg.feedback === true }"
                :disabled="msg.feedback !== undefined"
                @click="submitFeedback(msg, true)"
                title="有帮助"
              >👍</button>
              <button
                class="feedback-btn"
                :class="{ active: msg.feedback === false }"
                :disabled="msg.feedback !== undefined"
                @click="submitFeedback(msg, false)"
                title="没帮助"
              >👎</button>
              <span v-if="msg.feedback !== undefined" class="feedback-thanks">已记录，感谢反馈</span>
            </div>
          </div>
        </div>
      </template>

      <div class="msg-row msg-ai" v-if="loading">
        <div class="msg-avatar">
          <el-icon :size="18"><Monitor /></el-icon>
        </div>
        <div class="msg-bubble ai-bubble loading-bubble">
          <div class="typing-dots">
            <span></span><span></span><span></span>
          </div>
          <span class="loading-text">思考中...</span>
        </div>
      </div>
    </main>

    <div class="qa-bottom-bar">
      <div class="quick-actions">
        <button
          v-for="(q, i) in candidateQuestions"
          :key="i"
          class="action-pill"
          @click="fillQuestion(q)"
          :disabled="loading"
        >
          <span class="action-title">{{ q.label }}</span>
          <el-icon class="action-arrow"><ArrowRight /></el-icon>
        </button>
      </div>

      <div class="qa-input-wrap">
        <input
          v-model="inputMessage"
          class="qa-input"
          placeholder="输入你的问题..."
          @keyup.enter="sendMessage"
          :disabled="loading"
        />
        <button
          class="qa-send-btn"
          @click="sendMessage"
          :disabled="loading || !inputMessage.trim()"
        >
          <el-icon :size="20"><Top /></el-icon>
        </button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ArrowLeft, ArrowRight, Reading, Monitor, Top, ChatLineSquare } from '@element-plus/icons-vue'
import { marked } from 'marked'
import DOMPurify from 'dompurify'
import { getAuthHeader } from '@/utils/auth'
import { submitAiFeedback } from '@/api/student'

const props = defineProps({
  apiPath: {
    type: String,
    required: true
  },
  backLabel: {
    type: String,
    default: '返回阅读'
  }
})

const route = useRoute()

const selectedText = ref('')
const articleTitle = ref('')
const messages = ref([])
const inputMessage = ref('')
const loading = ref(false)
const chatContainer = ref(null)
let abortController = null

const candidateQuestions = computed(() => selectedText.value
  ? [
      { label: '结合文章内容解释本段意思' },
      { label: '深度解读' }
    ]
  : [
      { label: '英语语法疑问咨询' },
      { label: '单词用法与辨析' }
    ]
)

onMounted(() => {
  selectedText.value = route.query.text || ''
  articleTitle.value = route.query.articleTitle || ''
})

const fillQuestion = (q) => {
  inputMessage.value = q.label
}

const sendMessage = async () => {
  if (!inputMessage.value.trim() || loading.value) return

  const userMsg = inputMessage.value
  messages.value.push({ role: 'user', content: userMsg })
  inputMessage.value = ''
  loading.value = true
  scrollToBottom()

  try {
    const authHeaderValue = getAuthHeader()
    abortController = new AbortController()
    const response = await fetch(props.apiPath, {
      method: 'POST',
      signal: abortController.signal,
      headers: {
        'Content-Type': 'application/json',
        ...(authHeaderValue ? { Authorization: authHeaderValue } : {})
      },
      body: JSON.stringify({
        userQuestion: userMsg,
        selectedText: selectedText.value,
        articleContext: route.query.context || ''
      })
    })

    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`)
    }

    let data
    try {
      data = await response.json()
    } catch (parseError) {
      console.error('JSON parse error:', parseError)
      messages.value.push({ role: 'assistant', content: '抱歉，AI服务暂时不可用。请稍后再试。' })
      return
    }

    if (data?.code === 200 && data?.data?.response && typeof data.data.response === 'string') {
      const htmlResponse = DOMPurify.sanitize(marked(data.data.response))
      messages.value.push({
        role: 'assistant',
        content: htmlResponse,
        callLogId: data.data.callLogId || null,
        feedback: undefined
      })
    } else {
      messages.value.push({
        role: 'assistant',
        content: data?.message || '抱歉，未收到有效响应。'
      })
    }
  } catch (error) {
    if (error?.name !== 'AbortError') {
      console.error('AI API error:', error)
      messages.value.push({ role: 'assistant', content: '抱歉，AI服务暂时不可用。请稍后再试。' })
    }
  } finally {
    loading.value = false
    try {
      if (abortController) {
        abortController.abort()
      }
    } catch (e) {
      console.error('Abort cleanup error:', e)
    } finally {
      abortController = null
    }
    scrollToBottom()
  }
}

const scrollToBottom = () => {
  nextTick(() => {
    const container = document.getElementById('chat-container')
    if (container) {
      container.scrollTop = container.scrollHeight
    }
  })
}

const submitFeedback = (msg, isHelpful) => {
  msg.feedback = isHelpful
  submitAiFeedback({ callLogId: msg.callLogId, isHelpful })
}

onBeforeUnmount(() => {
  if (abortController) {
    try {
      abortController.abort()
    } catch (e) {
      console.error('Abort error:', e)
    }
    abortController = null
  }
  loading.value = false
})
</script>

<style scoped>
.ai-qa-page {
  display: flex;
  flex-direction: column;
  height: 100%;
  background: #f6f7f8;
  font-family: 'Inter', -apple-system, BlinkMacSystemFont, sans-serif;
  color: #1e293b;
  overflow: hidden;
}

.qa-header {
  flex-shrink: 0;
  background: rgba(255, 255, 255, 0.85);
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  border-bottom: 1px solid rgba(226, 232, 240, 0.6);
  z-index: 50;
}

.qa-header-inner {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  padding-top: max(env(safe-area-inset-top, 12px), 12px);
}

.qa-back-btn {
  display: flex;
  align-items: center;
  gap: 4px;
  background: none;
  border: none;
  color: #64748b;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: color 0.2s;
}

.qa-back-btn:hover { color: #197fe6; }

.qa-avatar {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  background: rgba(25, 127, 230, 0.1);
  display: flex;
  align-items: center;
  justify-content: center;
  color: #197fe6;
  border: 1px solid white;
  box-shadow: 0 1px 4px rgba(0,0,0,0.06);
}

.qa-context-bar {
  flex-shrink: 0;
  padding: 12px 16px;
  background: #f6f7f8;
  border-bottom: 1px solid rgba(226, 232, 240, 0.4);
}

.context-card {
  display: flex;
  background: rgba(255, 255, 255, 0.85);
  backdrop-filter: blur(12px);
  border-radius: 14px;
  overflow: hidden;
  box-shadow: 0 2px 12px rgba(0,0,0,0.04);
  border: 1px solid rgba(226, 232, 240, 0.6);
}

.context-accent {
  width: 4px;
  background: linear-gradient(180deg, #197fe6, #3b82f6);
  flex-shrink: 0;
}

.context-body {
  padding: 10px 14px;
  flex: 1;
  min-width: 0;
}

.context-label {
  display: flex;
  align-items: center;
  gap: 6px;
  color: #197fe6;
  font-size: 11px;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  margin-bottom: 6px;
}

.context-text {
  font-size: 13px;
  line-height: 1.5;
  color: #475569;
  margin: 0 0 6px;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.context-source {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 11px;
  color: #64748b;
  margin: 0;
}

.qa-chat-area {
  flex: 1;
  overflow-y: auto;
  padding: 16px 16px;
  -webkit-overflow-scrolling: touch;
  scrollbar-width: none;
}

.qa-chat-area::-webkit-scrollbar { display: none; }

.empty-hint {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
  color: #94a3b8;
  font-size: 14px;
}

.empty-hint p { margin: 0; }

.msg-row {
  display: flex;
  align-items: flex-end;
  gap: 10px;
  margin-bottom: 12px;
}

.msg-row.msg-user {
  justify-content: flex-end;
}

.msg-avatar {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  background: linear-gradient(135deg, #197fe6, #3b82f6);
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  flex-shrink: 0;
  box-shadow: 0 2px 8px rgba(25, 127, 230, 0.25);
}

.msg-bubble {
  padding: 12px 16px;
  border-radius: 18px;
  max-width: 85%;
  font-size: 15px;
  line-height: 1.6;
  word-break: break-word;
  overflow-wrap: break-word;
}

.ai-bubble {
  background: white;
  color: #1e293b;
  border: 1px solid #f1f5f9;
  border-bottom-left-radius: 4px;
  box-shadow: 0 2px 8px rgba(0,0,0,0.03);
}

.feedback-row {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 10px;
  padding-top: 8px;
  border-top: 1px solid #f1f5f9;
}

.feedback-btn {
  background: none;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  padding: 3px 10px;
  font-size: 15px;
  cursor: pointer;
  transition: all 0.2s;
  line-height: 1.4;
}

.feedback-btn:hover:not(:disabled) {
  background: #f8fafc;
  border-color: #6366f1;
}

.feedback-btn.active {
  background: #ede9fe;
  border-color: #6366f1;
}

.feedback-btn:disabled {
  cursor: default;
  opacity: 0.6;
}

.feedback-thanks {
  font-size: 12px;
  color: #94a3b8;
}

.ai-bubble :deep(h1),
.ai-bubble :deep(h2),
.ai-bubble :deep(h3),
.ai-bubble :deep(h4),
.ai-bubble :deep(h5),
.ai-bubble :deep(h6) {
  margin-top: 16px;
  margin-bottom: 8px;
  font-weight: 700;
  line-height: 1.3;
  color: #0f172a;
}

.ai-bubble :deep(h1) { font-size: 1.5em; }
.ai-bubble :deep(h2) { font-size: 1.3em; }
.ai-bubble :deep(h3) { font-size: 1.15em; }
.ai-bubble :deep(h4) { font-size: 1em; }

.ai-bubble :deep(p) {
  margin: 8px 0;
  line-height: 1.7;
}

.ai-bubble :deep(p:first-child) { margin-top: 0; }
.ai-bubble :deep(p:last-child) { margin-bottom: 0; }

.ai-bubble :deep(ul),
.ai-bubble :deep(ol) {
  margin: 8px 0;
  padding-left: 24px;
}

.ai-bubble :deep(li) {
  margin: 4px 0;
  line-height: 1.6;
}

.ai-bubble :deep(code) {
  padding: 2px 6px;
  background: #f1f5f9;
  border-radius: 4px;
  font-family: 'Fira Code', 'Cascadia Code', monospace;
  font-size: 0.9em;
  color: #dc2626;
}

.ai-bubble :deep(pre) {
  margin: 12px 0;
  padding: 12px 16px;
  background: #1e293b;
  border-radius: 8px;
  overflow-x: auto;
  -webkit-overflow-scrolling: touch;
}

.ai-bubble :deep(pre code) {
  padding: 0;
  background: transparent;
  color: #e2e8f0;
  font-size: 0.85em;
  line-height: 1.6;
}

.ai-bubble :deep(blockquote) {
  margin: 12px 0;
  padding: 8px 16px;
  border-left: 4px solid #197fe6;
  background: #f8fafc;
  color: #475569;
}

.ai-bubble :deep(table) {
  width: 100%;
  border-collapse: collapse;
  margin: 12px 0;
  font-size: 0.9em;
}

.ai-bubble :deep(th),
.ai-bubble :deep(td) {
  border: 1px solid #e2e8f0;
  padding: 8px 12px;
  text-align: left;
}

.ai-bubble :deep(th) {
  background: #f8fafc;
  font-weight: 600;
}

.user-bubble {
  background: linear-gradient(135deg, #197fe6, #3b82f6);
  color: white;
  border-bottom-right-radius: 4px;
  box-shadow: 0 4px 12px rgba(25, 127, 230, 0.22);
}

.loading-bubble {
  display: inline-flex;
  align-items: center;
  gap: 10px;
}

.typing-dots {
  display: inline-flex;
  gap: 4px;
}

.typing-dots span {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #94a3b8;
  animation: typing-bounce 1.2s infinite ease-in-out;
}

.typing-dots span:nth-child(2) {
  animation-delay: 0.15s;
}

.typing-dots span:nth-child(3) {
  animation-delay: 0.3s;
}

.loading-text {
  color: #64748b;
  font-size: 14px;
}

.qa-bottom-bar {
  flex-shrink: 0;
  background: rgba(255, 255, 255, 0.92);
  backdrop-filter: blur(14px);
  -webkit-backdrop-filter: blur(14px);
  border-top: 1px solid rgba(226, 232, 240, 0.7);
  padding: 12px 16px calc(12px + env(safe-area-inset-bottom, 0px));
}

.quick-actions {
  display: flex;
  gap: 10px;
  overflow-x: auto;
  padding-bottom: 10px;
  scrollbar-width: none;
}

.quick-actions::-webkit-scrollbar { display: none; }

.action-pill {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  border: 1px solid rgba(59, 130, 246, 0.15);
  background: rgba(248, 250, 252, 0.92);
  color: #1e293b;
  border-radius: 999px;
  padding: 10px 14px;
  white-space: nowrap;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
}

.action-pill:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

.action-title {
  max-width: 180px;
  overflow: hidden;
  text-overflow: ellipsis;
}

.action-arrow {
  color: #197fe6;
}

.qa-input-wrap {
  display: flex;
  align-items: center;
  gap: 10px;
}

.qa-input {
  flex: 1;
  min-width: 0;
  border: 1px solid rgba(148, 163, 184, 0.28);
  border-radius: 999px;
  background: #fff;
  padding: 12px 16px;
  font-size: 15px;
  color: #1e293b;
  outline: none;
}

.qa-input:focus {
  border-color: rgba(25, 127, 230, 0.45);
  box-shadow: 0 0 0 4px rgba(25, 127, 230, 0.08);
}

.qa-input:disabled {
  background: #f8fafc;
  color: #94a3b8;
}

.qa-send-btn {
  width: 46px;
  height: 46px;
  flex-shrink: 0;
  border: none;
  border-radius: 50%;
  background: linear-gradient(135deg, #197fe6, #3b82f6);
  color: white;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  box-shadow: 0 8px 18px rgba(25, 127, 230, 0.24);
}

.qa-send-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
  box-shadow: none;
}

@keyframes typing-bounce {
  0%, 80%, 100% {
    transform: translateY(0);
    opacity: 0.45;
  }
  40% {
    transform: translateY(-4px);
    opacity: 1;
  }
}
</style>
