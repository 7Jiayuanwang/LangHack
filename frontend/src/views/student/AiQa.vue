<template>
  <div class="ai-qa-page">
    <!-- Glassmorphic Sticky Header -->
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

    <!-- Scrollable Chat Area -->
    <main class="qa-chat-area" ref="chatContainer" id="chat-container">

      <!-- Selected Context Card -->
      <div class="context-card" v-if="selectedText">
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

      <!-- Candidate Question Pills -->
      <div class="quick-actions" v-if="messages.length === 0">
        <button 
          v-for="(q, i) in candidateQuestions" 
          :key="i" 
          class="action-pill"
          @click="fillQuestion(q)"
        >
          <div class="action-text">
            <span class="action-title">{{ q.label }}</span>
            <span class="action-sub">{{ q.sub }}</span>
          </div>
          <el-icon class="action-arrow"><ArrowRight /></el-icon>
        </button>
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

      <div class="scroll-spacer"></div>
    </main>

    <!-- Sticky Bottom Input -->
    <div class="qa-input-area">
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
const inputMessage = ref('长难句解析')
const loading = ref(false)
const chatContainer = ref(null)
const threadId = ref('')
let abortController = null

const candidateQuestions = reactive([
  { label: '结合文章内容解释本段意思', sub: 'Explain meaning in context' },
  { label: '深度解读', sub: 'Deep dive interpretation' },
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
    const tutorApiUrl = import.meta.env.VITE_TUTOR_API_BASE_URL || '/tutor'
    const authHeaderValue = getAuthHeader()
    abortController = new AbortController()
    const response = await fetch(tutorApiUrl, {
      method: 'POST',
      signal: abortController.signal,
      headers: {
        'Content-Type': 'application/json',
        ...(authHeaderValue ? { 'Authorization': authHeaderValue } : {})
      },
      body: JSON.stringify({
        user_question: userMsg,
        article_text: route.query.context || '',
        selected_text: selectedText.value,
        thread_id: threadId.value
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

    // FIX: 验证响应数据 + 完整的 XSS 防护
    if (data?.response && typeof data.response === 'string') {
      // 先用 marked 转换 markdown，再用 DOMPurify 清理恶意标签
      const htmlResponse = DOMPurify.sanitize(marked(data.response))
      messages.value.push({
        role: 'assistant',
        content: htmlResponse
      })
    } else {
      messages.value.push({
        role: 'assistant',
        content: '抱歉，未收到有效响应。'
      })
    }

    // Update thread_id for next turns
    if (data?.thread_id) {
      threadId.value = data.thread_id
    }
  } catch (error) {
    if (error?.name !== 'AbortError') {
      console.error('AI API error:', error)
      messages.value.push({ role: 'assistant', content: '抱歉，AI服务暂时不可用。请稍后再试。' })
    }
  } finally {
    loading.value = false
    // FIX: 正确处理 abortController 清理
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
  // 中止任何进行中的请求
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
   AI Q&A Page — Mobile-First Premium Design
   ============================================ */

:root {
  --qa-primary: #197fe6;
  --qa-bg: #f6f7f8;
  --qa-text: #1e293b;
  --qa-muted: #64748b;
  --qa-glass: rgba(255, 255, 255, 0.85);
}

.ai-qa-page {
  display: flex;
  flex-direction: column;
  height: 100vh;
  height: 100dvh;
  background: var(--qa-bg);
  font-family: 'Inter', -apple-system, BlinkMacSystemFont, sans-serif;
  color: var(--qa-text);
  max-width: 480px;
  margin: 0 auto;
  position: relative;
  overflow: hidden;
}

/* --- Header --- */
.qa-header {
  position: sticky;
  top: 0;
  z-index: 50;
  background: var(--qa-glass);
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  border-bottom: 1px solid rgba(226, 232, 240, 0.6);
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
  color: var(--qa-muted);
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: color 0.2s;
}
.qa-back-btn:hover { color: var(--qa-primary); }

.qa-title {
  position: absolute;
  left: 50%;
  transform: translateX(-50%);
  font-size: 17px;
  font-weight: 700;
  color: var(--qa-text);
}

.qa-avatar {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  background: rgba(25, 127, 230, 0.1);
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--qa-primary);
  border: 1px solid white;
  box-shadow: 0 1px 4px rgba(0,0,0,0.06);
}

/* --- Chat Area --- */
.qa-chat-area {
  flex: 1;
  overflow-y: auto;
  padding: 20px 16px 180px; /* Extra bottom padding for input + nav */
  -webkit-overflow-scrolling: touch;
  scrollbar-width: none;
}
.qa-chat-area::-webkit-scrollbar { display: none; }

.scroll-spacer { height: 20px; }

/* --- Context Card --- */
.context-card {
  display: flex;
  background: var(--qa-glass);
  backdrop-filter: blur(12px);
  border-radius: 16px;
  overflow: hidden;
  box-shadow: 0 4px 20px rgba(0,0,0,0.04);
  margin-bottom: 24px;
  border: 1px solid rgba(226, 232, 240, 0.6);
}

.context-accent {
  width: 5px;
  background: linear-gradient(180deg, var(--qa-primary), #3b82f6);
  flex-shrink: 0;
}

.context-body {
  padding: 14px 16px;
  flex: 1;
  min-width: 0;
}

.context-label {
  display: flex;
  align-items: center;
  gap: 6px;
  color: var(--qa-primary);
  font-size: 11px;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  margin-bottom: 8px;
}

.context-text {
  font-size: 14px;
  line-height: 1.6;
  color: #475569;
  margin: 0 0 8px;
  display: -webkit-box;
  -webkit-line-clamp: 3;
  line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.context-source {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: var(--qa-muted);
  margin: 0;
}

/* --- Messages --- */
.msg-group {
  display: flex;
  flex-direction: column;
  gap: 12px;
  margin-bottom: 16px;
}

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
  background: linear-gradient(135deg, var(--qa-primary), #3b82f6);
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
  color: var(--qa-text);
  border: 1px solid #f1f5f9;
  border-bottom-left-radius: 4px;
  box-shadow: 0 2px 8px rgba(0,0,0,0.03);
}

.user-bubble {
  background: linear-gradient(135deg, var(--qa-primary), #3b82f6);
  color: white;
  border-bottom-right-radius: 4px;
  box-shadow: 0 2px 10px rgba(25, 127, 230, 0.25);
}

/* --- Quick Action Pills --- */
.quick-actions {
  position: fixed;
  bottom: calc(env(safe-area-inset-bottom, 0px) + 140px); /* Above the input bar + nav */
  left: 50%;
  transform: translateX(-50%);
  width: calc(100% - 32px);
  max-width: 448px;
  display: flex;
  flex-direction: column;
  gap: 8px;
  z-index: 41;
}

.action-pill {
  display: flex;
  align-items: center;
  gap: 12px;
  background: white;
  border: 1px solid #e2e8f0;
  border-radius: 14px;
  padding: 12px 14px;
  cursor: pointer;
  transition: all 0.2s;
  box-shadow: 0 1px 4px rgba(0,0,0,0.03);
  text-align: left;
  width: 100%;
}

.action-pill:hover {
  background: #f8fafc;
  box-shadow: 0 4px 12px rgba(0,0,0,0.06);
  border-color: rgba(25, 127, 230, 0.3);
}

.action-pill:active {
  transform: scale(0.98);
}

.action-emoji {
  font-size: 20px;
  flex-shrink: 0;
}

.action-text {
  flex: 1;
  min-width: 0;
}

.action-title {
  display: block;
  font-size: 14px;
  font-weight: 600;
  color: var(--qa-text);
}

.action-sub {
  display: block;
  font-size: 11px;
  color: var(--qa-muted);
  margin-top: 2px;
}

.action-arrow {
  color: #cbd5e1;
  transition: color 0.2s;
  flex-shrink: 0;
}

.action-pill:hover .action-arrow {
  color: var(--qa-primary);
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
  color: var(--qa-muted);
}

/* --- Sticky Input --- */
.qa-input-area {
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  padding: 12px 16px;
  padding-bottom: calc(env(safe-area-inset-bottom, 0px) + 70px); /* Account for bottom nav */
  background: linear-gradient(to top, var(--qa-bg) 80%, transparent);
  z-index: 40;
}

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
  color: var(--qa-text);
  height: 40px;
  font-family: inherit;
}

.qa-input::placeholder {
  color: #94a3b8;
}

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
  font-size: 22px;
}

.qa-send-btn:hover { opacity: 0.9; transform: scale(1.05); }
.qa-send-btn:active { transform: scale(0.93); }
.qa-send-btn:disabled { opacity: 0.35; cursor: default; transform: none; }

/* --- Desktop: Widen the chat container --- */
@media (min-width: 768px) {
  .ai-qa-page {
    max-width: 640px;
    box-shadow: 0 0 40px rgba(0,0,0,0.08);
    border-radius: 24px;
    margin: 20px auto;
    height: calc(100vh - 40px);
    height: calc(100dvh - 40px);
  }
}
</style>
