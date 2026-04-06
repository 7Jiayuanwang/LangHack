<template>
  <div class="ai-qa-page">
    <!-- Fixed Header -->
    <header class="qa-header">
      <div class="qa-header-inner">
        <button class="qa-back-btn" @click="$router.back()">
          <el-icon><ArrowLeft /></el-icon>
          <span>返回阅读</span>
        </button>
        <h1 class="qa-title">AI 助教</h1>
        <div class="qa-avatar">
          <el-icon :size="22"><Monitor /></el-icon>
        </div>
      </div>
    </header>

    <!-- Fixed Context Card (below header) -->
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

    <!-- Scrollable Chat Messages Only -->
    <main class="qa-chat-area" ref="chatContainer" id="chat-container">
      <!-- Empty state hint -->
      <div class="empty-hint" v-if="messages.length === 0">
        <p>点击下方快捷提问或输入你的问题</p>
      </div>

      <!-- Chat Messages -->
      <template v-for="(msg, index) in messages" :key="index">
        <div class="msg-row" :class="msg.role === 'user' ? 'msg-user' : 'msg-ai'">
          <div class="msg-avatar" v-if="msg.role !== 'user'">
            <el-icon :size="18"><Monitor /></el-icon>
          </div>
          <div class="msg-bubble" :class="msg.role === 'user' ? 'user-bubble' : 'ai-bubble'">
            <div v-if="msg.role === 'user'">{{ msg.content }}</div>
            <div v-else v-html="msg.content"></div>
          </div>
        </div>
      </template>

      <!-- Loading Indicator -->
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

    <!-- Fixed Bottom: Quick Actions + Input -->
    <div class="qa-bottom-bar">
      <!-- Candidate Question Pills (always visible) -->
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

      <!-- Input -->
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
import { ref, onMounted, nextTick, reactive, onBeforeUnmount } from 'vue'
import { useRoute } from 'vue-router'
import { ArrowLeft, ArrowRight, Reading, Monitor, Top, ChatLineSquare } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { marked } from 'marked'
import DOMPurify from 'dompurify'
import { getAuthHeader } from '@/utils/auth'

const route = useRoute()

const selectedText = ref('')
const articleTitle = ref('')
const messages = ref([])
const inputMessage = ref('')
const loading = ref(false)
const chatContainer = ref(null)
let abortController = null

const candidateQuestions = reactive([
  { label: '结合文章内容解释本段意思' },
  { label: '深度解读' },
])

onMounted(() => {
  selectedText.value = route.query.text || ''
  articleTitle.value = route.query.articleTitle || ''

  if (!selectedText.value) {
    ElMessage.warning('未检测到选中文本')
  }
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
    const response = await fetch('/api/student/ai-qa/chat', {
      method: 'POST',
      signal: abortController.signal,
      headers: {
        'Content-Type': 'application/json',
        ...(authHeaderValue ? { 'Authorization': authHeaderValue } : {})
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
        content: htmlResponse
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
/* ============================================
   AI Q&A Page — Fixed Layout
   Header + Context: fixed top
   Quick Actions + Input: fixed bottom
   Only chat messages scroll
   ============================================ */

.ai-qa-page {
  display: flex;
  flex-direction: column;
  height: 100%;
  background: #f6f7f8;
  font-family: 'Inter', -apple-system, BlinkMacSystemFont, sans-serif;
  color: #1e293b;
  overflow: hidden;
}

/* --- Fixed Header --- */
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

.qa-title {
  position: absolute;
  left: 50%;
  transform: translateX(-50%);
  font-size: 17px;
  font-weight: 700;
  color: #1e293b;
}

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

/* --- Fixed Context Bar (below header) --- */
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

/* --- Scrollable Chat Area (middle) --- */
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

/* --- Messages --- */
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

/* Markdown content styles inside AI bubble */
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
  margin: 12px 0;
  border-collapse: collapse;
  width: 100%;
  font-size: 0.9em;
}

.ai-bubble :deep(th),
.ai-bubble :deep(td) {
  padding: 8px 12px;
  border: 1px solid #e2e8f0;
  text-align: left;
}

.ai-bubble :deep(th) {
  background: #f8fafc;
  font-weight: 600;
}

.ai-bubble :deep(strong) {
  font-weight: 700;
  color: #0f172a;
}

.ai-bubble :deep(em) {
  font-style: italic;
}

.user-bubble {
  background: linear-gradient(135deg, #197fe6, #3b82f6);
  color: white;
  border-bottom-right-radius: 4px;
  box-shadow: 0 2px 10px rgba(25, 127, 230, 0.25);
}

/* --- Loading Bubble --- */
.loading-bubble {
  display: flex;
  align-items: center;
  gap: 10px;
}

.typing-dots {
  display: flex;
  gap: 4px;
}

.typing-dots span {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #94a3b8;
  animation: bounce 1.4s ease-in-out infinite;
}

.typing-dots span:nth-child(2) { animation-delay: 0.2s; }
.typing-dots span:nth-child(3) { animation-delay: 0.4s; }

@keyframes bounce {
  0%, 80%, 100% { transform: scale(0.6); opacity: 0.4; }
  40% { transform: scale(1); opacity: 1; }
}

.loading-text {
  font-size: 14px;
  color: #64748b;
}

/* --- Fixed Bottom Bar --- */
.qa-bottom-bar {
  flex-shrink: 0;
  padding: 10px 16px 12px;
  background: #f6f7f8;
  border-top: 1px solid rgba(226, 232, 240, 0.6);
  z-index: 40;
}

/* --- Quick Action Pills --- */
.quick-actions {
  display: flex;
  gap: 8px;
  margin-bottom: 10px;
}

.action-pill {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  background: white;
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  padding: 10px 12px;
  cursor: pointer;
  transition: all 0.2s;
  box-shadow: 0 1px 4px rgba(0,0,0,0.03);
}

.action-pill:hover {
  background: #f8fafc;
  border-color: rgba(25, 127, 230, 0.3);
  box-shadow: 0 2px 8px rgba(0,0,0,0.06);
}

.action-pill:active { transform: scale(0.97); }
.action-pill:disabled { opacity: 0.5; cursor: default; transform: none; }

.action-title {
  font-size: 13px;
  font-weight: 600;
  color: #1e293b;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.action-arrow {
  color: #cbd5e1;
  flex-shrink: 0;
  font-size: 14px;
}

.action-pill:hover .action-arrow { color: #197fe6; }

/* --- Input --- */
.qa-input-wrap {
  display: flex;
  align-items: center;
  gap: 8px;
  background: white;
  border-radius: 999px;
  border: 1px solid #e2e8f0;
  padding: 5px 5px 5px 20px;
  box-shadow: 0 4px 16px rgba(0,0,0,0.06);
  transition: box-shadow 0.2s, border-color 0.2s;
}

.qa-input-wrap:focus-within {
  box-shadow: 0 6px 24px rgba(25, 127, 230, 0.12);
  border-color: rgba(25, 127, 230, 0.3);
}

.qa-input {
  flex: 1;
  border: none;
  outline: none;
  background: transparent;
  font-size: 16px;
  color: #1e293b;
  height: 40px;
  font-family: inherit;
}

.qa-input::placeholder { color: #94a3b8; }

.qa-send-btn {
  width: 44px;
  height: 44px;
  border-radius: 50%;
  border: none;
  background: linear-gradient(135deg, #197fe6, #3b82f6);
  color: white;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  flex-shrink: 0;
  transition: opacity 0.2s, transform 0.15s;
  box-shadow: 0 4px 14px rgba(25, 127, 230, 0.45);
}

.qa-send-btn:hover { opacity: 0.9; transform: scale(1.05); }
.qa-send-btn:active { transform: scale(0.93); }
.qa-send-btn:disabled { opacity: 0.35; cursor: default; transform: none; }

/* --- Desktop --- */
@media (min-width: 768px) {
  .ai-qa-page {
    max-width: 1200px;
    margin: 0 auto;
  }
}
</style>
