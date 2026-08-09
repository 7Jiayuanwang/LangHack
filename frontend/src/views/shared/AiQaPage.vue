<template>
  <div class="ai-qa-page" :class="{ 'sidebar-open': sidebarOpen }">
    <!-- 历史对话侧边栏 -->
    <aside class="history-sidebar" v-if="sidebarOpen">
      <div class="sidebar-header">
        <span class="sidebar-title">历史对话</span>
        <button class="sidebar-close" @click="sidebarOpen = false">✕</button>
      </div>
      <button class="new-conv-btn" @click="startNewConversation">
        <span>＋ 新建对话</span>
      </button>
      <div class="conv-list">
        <div
          v-for="conv in conversations"
          :key="conv.id"
          class="conv-item"
          :class="{ active: currentConversationId === conv.id }"
          @click="loadConversation(conv)"
        >
          <div class="conv-title">{{ conv.title || '未命名对话' }}</div>
          <div class="conv-meta">{{ formatTime(conv.updatedTime) }} · {{ conv.messageCount }}条</div>
          <button class="conv-delete" @click.stop="confirmDeleteConversation(conv.id)">🗑</button>
        </div>
        <div v-if="!conversations.length" class="conv-empty">暂无历史对话</div>
      </div>
    </aside>

    <div class="qa-main">
      <header class="qa-header">
        <div class="qa-header-inner">
          <div class="header-left-group">
            <button class="qa-sidebar-btn" @click="toggleSidebar" title="历史对话">
              <el-icon><List /></el-icon>
            </button>
            <button class="qa-back-btn" @click="$router.back()">
              <el-icon><ArrowLeft /></el-icon>
              <span>{{ backLabel }}</span>
            </button>
          </div>
          <div class="header-right-group">
            <button
              v-if="currentConversationId"
              class="qa-share-btn"
              @click="showShareDialog = true"
              title="分享对话"
            >
              <el-icon><Share /></el-icon>
            </button>
            <div class="qa-avatar">
              <el-icon :size="22"><Monitor /></el-icon>
            </div>
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
          <p>输入问题开始对话，或点击左上角 <strong>☰</strong> 查看历史对话</p>
        </div>

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

    <!-- 分享对话框 -->
    <el-dialog v-model="showShareDialog" title="分享对话" width="360px" :close-on-click-modal="true">
      <div class="share-form">
        <div class="share-field">
          <label>过期时间</label>
          <el-select v-model="shareOptions.expiresInDays" style="width:100%">
            <el-option label="7 天" :value="7" />
            <el-option label="30 天" :value="30" />
            <el-option label="永久" :value="null" />
          </el-select>
        </div>
        <div class="share-field">
          <label>访问密码（可选）</label>
          <el-input v-model="shareOptions.password" type="password" placeholder="留空则无需密码" show-password />
        </div>
        <div v-if="shareLink" class="share-link-box">
          <div class="share-link-label">分享链接</div>
          <div class="share-link-text">{{ shareLink }}</div>
          <el-button size="small" @click="copyShareLink">复制链接</el-button>
        </div>
      </div>
      <template #footer>
        <el-button @click="showShareDialog = false">取消</el-button>
        <el-button type="primary" @click="doShare" :loading="sharing">生成分享链接</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ArrowLeft, ArrowRight, Reading, Monitor, Top, ChatLineSquare, List, Share } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { marked } from 'marked'
import DOMPurify from 'dompurify'
import { getAuthHeader } from '@/utils/auth'
import {
  getConversations, getConversationDetail, deleteConversation, shareConversation
} from '@/api/conversation'

const props = defineProps({
  apiPath: { type: String, required: true },
  backLabel: { type: String, default: '返回阅读' }
})

const route = useRoute()

const selectedText = ref('')
const articleTitle = ref('')
const messages = ref([])
const inputMessage = ref('')
const loading = ref(false)
const chatContainer = ref(null)
let abortController = null

// 历史对话
const sidebarOpen = ref(false)
const conversations = ref([])
const currentConversationId = ref(null)

// 分享
const showShareDialog = ref(false)
const sharing = ref(false)
const shareLink = ref('')
const shareOptions = ref({ expiresInDays: 7, password: '' })

const candidateQuestions = computed(() => selectedText.value
  ? [{ label: '结合文章内容解释本段意思' }, { label: '深度解读' }]
  : [{ label: '英语语法疑问咨询' }, { label: '单词用法与辨析' }]
)

onMounted(() => {
  selectedText.value = route.query.text || ''
  articleTitle.value = route.query.articleTitle || ''
  loadConversations()
})

const loadConversations = async () => {
  try {
    const res = await getConversations(1, 50)
    if (res?.code === 200) {
      conversations.value = res.data?.records || []
      if (conversations.value.length > 0 && !selectedText.value) {
        sidebarOpen.value = true
      }
    }
  } catch (e) {
    // 静默失败
  }
}

const toggleSidebar = async () => {
  sidebarOpen.value = !sidebarOpen.value
  if (sidebarOpen.value) await loadConversations()
}

const startNewConversation = () => {
  currentConversationId.value = null
  messages.value = []
  sidebarOpen.value = false
}

const loadConversation = async (conv) => {
  try {
    const res = await getConversationDetail(conv.id)
    if (res?.code === 200) {
      currentConversationId.value = conv.id
      messages.value = (res.data?.messages || []).map(m => ({
        role: m.role,
        content: m.role === 'assistant'
          ? DOMPurify.sanitize(marked(m.content))
          : m.content
      }))
      sidebarOpen.value = false
      scrollToBottom()
    }
  } catch (e) {
    ElMessage.error('加载对话失败')
  }
}

const confirmDeleteConversation = async (id) => {
  try {
    await ElMessageBox.confirm('确定删除此对话？', '提示', {
      confirmButtonText: '删除', cancelButtonText: '取消', type: 'warning'
    })
    await deleteConversation(id)
    if (currentConversationId.value === id) {
      currentConversationId.value = null
      messages.value = []
    }
    await loadConversations()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error('删除失败')
  }
}

const doShare = async () => {
  if (!currentConversationId.value) return
  sharing.value = true
  try {
    const res = await shareConversation(currentConversationId.value, {
      expiresInDays: shareOptions.value.expiresInDays,
      password: shareOptions.value.password || null
    })
    if (res?.code === 200) {
      const token = res.data?.shareToken
      shareLink.value = `${window.location.origin}/shared-conversation/${token}`
    }
  } catch (e) {
    ElMessage.error('生成分享链接失败')
  } finally {
    sharing.value = false
  }
}

const copyShareLink = () => {
  navigator.clipboard.writeText(shareLink.value).then(() => {
    ElMessage.success('链接已复制')
  })
}

const formatTime = (t) => {
  if (!t) return ''
  const d = new Date(t)
  return `${d.getMonth() + 1}/${d.getDate()} ${d.getHours()}:${String(d.getMinutes()).padStart(2, '0')}`
}

const fillQuestion = (q) => { inputMessage.value = q.label }

// ============================================================
// 核心修改：sendMessage 支持 SSE 流式输出
// ============================================================
const sendMessage = async () => {
  if (!inputMessage.value.trim() || loading.value) return

  const userMsg = inputMessage.value
  messages.value.push({ role: 'user', content: userMsg })
  inputMessage.value = ''
  loading.value = true
  scrollToBottom()

  // 添加空的 AI 消息占位，后续流式拼接
  const aiMsgIndex = messages.value.length
  messages.value.push({ role: 'assistant', content: '' })

  let rawText = ''

  try {
    const authHeaderValue = getAuthHeader()
    abortController = new AbortController()
    const response = await fetch(props.apiPath, {
      method: 'POST',
      signal: abortController.signal,
      headers: {
        'Content-Type': 'application/json',
        'Accept': 'text/event-stream',
        ...(authHeaderValue ? { Authorization: authHeaderValue } : {})
      },
      body: JSON.stringify({
        userQuestion: userMsg,
        selectedText: selectedText.value,
        articleContext: route.query.context || '',
        conversationId: currentConversationId.value,
        ResponseMode: 'streaming'
      })
    })

    if (!response.ok) throw new Error(`HTTP error! status: ${response.status}`)

    const contentType = response.headers.get('content-type') || ''

    if (contentType.includes('text/event-stream') || contentType.includes('stream')) {
      // ===== SSE 流式处理 =====
      const reader = response.body.getReader()
      const decoder = new TextDecoder('utf-8')
      let buffer = ''

      while (true) {
        const { done, value } = await reader.read()
        if (done) break

        buffer += decoder.decode(value, { stream: true })
        const lines = buffer.split('\n')
        buffer = lines.pop()

        for (const line of lines) {
          if (!line.trim()) continue

          if (line.startsWith('data:')) {
            const data = line.slice(5).trim()
            if (!data || data === '[DONE]') continue

            try {
              const json = JSON.parse(data)
              const event = json.event

              if (event === 'message' && json.answer) {
                rawText += json.answer
                messages.value[aiMsgIndex].content = DOMPurify.sanitize(marked(rawText))
                scrollToBottom()
              } else if (event === 'message_end') {
                if (json.conversation_id && !currentConversationId.value) {
                  currentConversationId.value = json.conversation_id
                  await loadConversations()
                }
              }
            } catch (e) {
              if (data) {
                rawText += data
                messages.value[aiMsgIndex].content = DOMPurify.sanitize(marked(rawText))
                scrollToBottom()
              }
            }
          }
        }
      }

      if (!rawText) {
        messages.value[aiMsgIndex].content = '抱歉，未收到有效响应。'
      }

    } else {
      // ===== 兼容：普通 JSON 响应 =====
      let jsonData
      try {
        jsonData = await response.json()
      } catch (parseError) {
        messages.value[aiMsgIndex].content = '抱歉，AI服务暂时不可用。请稍后再试。'
        return
      }
      if (jsonData?.code === 200 && jsonData?.data?.response) {
        messages.value[aiMsgIndex].content = DOMPurify.sanitize(marked(jsonData.data.response))
        if (jsonData.data.conversationId && !currentConversationId.value) {
          currentConversationId.value = jsonData.data.conversationId
          await loadConversations()
        }
      } else {
        messages.value[aiMsgIndex].content = jsonData?.message || '抱歉，未收到有效响应。'
      }
    }
  } catch (error) {
    if (error?.name !== 'AbortError') {
      if (rawText) {
        messages.value[aiMsgIndex].content = DOMPurify.sanitize(marked(rawText + '\n\n---\n*（连接中断）*'))
      } else {
        messages.value[aiMsgIndex].content = '抱歉，AI服务暂时不可用。请稍后再试。'
      }
    }
  } finally {
    loading.value = false
    abortController = null
    scrollToBottom()
  }
}

const scrollToBottom = () => {
  nextTick(() => {
    const container = document.getElementById('chat-container')
    if (container) container.scrollTop = container.scrollHeight
  })
}

onBeforeUnmount(() => {
  if (abortController) {
    try { abortController.abort() } catch (e) { /* ignore */ }
    abortController = null
  }
  loading.value = false
})
</script>

<style scoped>
.ai-qa-page {
  display: flex;
  height: 100%;
  background: #f6f7f8;
  font-family: 'Inter', -apple-system, BlinkMacSystemFont, sans-serif;
  color: #1e293b;
  overflow: hidden;
  position: relative;
}

.history-sidebar {
  width: 280px;
  flex-shrink: 0;
  background: white;
  border-right: 1px solid #e2e8f0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.sidebar-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px;
  border-bottom: 1px solid #f1f5f9;
}

.sidebar-title { font-weight: 700; font-size: 15px; color: #1e293b; }
.sidebar-close { background: none; border: none; color: #94a3b8; cursor: pointer; font-size: 16px; padding: 4px; }

.new-conv-btn {
  margin: 12px;
  padding: 10px;
  background: #f0f9ff;
  border: 1px dashed #93c5fd;
  border-radius: 10px;
  color: #3b82f6;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  text-align: center;
}
.new-conv-btn:hover { background: #dbeafe; }

.conv-list { flex: 1; overflow-y: auto; padding: 4px 8px; }
.conv-item { padding: 10px 12px; border-radius: 10px; cursor: pointer; position: relative; margin-bottom: 4px; }
.conv-item:hover { background: #f8fafc; }
.conv-item.active { background: #eff6ff; }
.conv-title { font-size: 13px; font-weight: 600; color: #1e293b; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; padding-right: 24px; }
.conv-meta { font-size: 11px; color: #94a3b8; margin-top: 2px; }
.conv-delete { position: absolute; right: 8px; top: 50%; transform: translateY(-50%); background: none; border: none; cursor: pointer; opacity: 0; font-size: 14px; padding: 2px; }
.conv-item:hover .conv-delete { opacity: 1; }
.conv-empty { text-align: center; color: #94a3b8; font-size: 13px; padding: 24px; }

.qa-main { flex: 1; display: flex; flex-direction: column; overflow: hidden; }

.qa-header {
  flex-shrink: 0;
  background: rgba(255, 255, 255, 0.85);
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  border-bottom: 1px solid rgba(226, 232, 240, 0.6);
  z-index: 50;
}

.qa-header-inner { display: flex; align-items: center; justify-content: space-between; padding: 12px 16px; padding-top: max(env(safe-area-inset-top, 12px), 12px); }
.header-left-group, .header-right-group { display: flex; align-items: center; gap: 8px; }

.qa-sidebar-btn, .qa-share-btn { display: flex; align-items: center; justify-content: center; width: 36px; height: 36px; border-radius: 50%; background: #f1f5f9; border: none; color: #64748b; cursor: pointer; font-size: 18px; }
.qa-sidebar-btn:hover, .qa-share-btn:hover { background: #e2e8f0; color: #197fe6; }

.qa-back-btn { display: flex; align-items: center; gap: 4px; background: none; border: none; color: #64748b; font-size: 14px; font-weight: 500; cursor: pointer; transition: color 0.2s; }
.qa-back-btn:hover { color: #197fe6; }

.qa-avatar { width: 36px; height: 36px; border-radius: 50%; background: rgba(25, 127, 230, 0.1); display: flex; align-items: center; justify-content: center; color: #197fe6; border: 1px solid white; box-shadow: 0 1px 4px rgba(0,0,0,0.06); }

.share-form { display: flex; flex-direction: column; gap: 16px; }
.share-field label { display: block; font-size: 13px; font-weight: 600; color: #475569; margin-bottom: 6px; }
.share-link-box { background: #f8fafc; border-radius: 8px; padding: 12px; }
.share-link-label { font-size: 12px; color: #64748b; margin-bottom: 6px; }
.share-link-text { font-size: 12px; color: #1e293b; word-break: break-all; margin-bottom: 8px; }

.qa-context-bar { flex-shrink: 0; padding: 12px 16px; background: #f6f7f8; border-bottom: 1px solid rgba(226, 232, 240, 0.4); }
.context-card { display: flex; background: rgba(255, 255, 255, 0.85); backdrop-filter: blur(12px); border-radius: 14px; overflow: hidden; box-shadow: 0 2px 12px rgba(0,0,0,0.04); border: 1px solid rgba(226, 232, 240, 0.6); }
.context-accent { width: 4px; background: linear-gradient(180deg, #197fe6, #3b82f6); flex-shrink: 0; }
.context-body { padding: 10px 14px; flex: 1; min-width: 0; }
.context-label { display: flex; align-items: center; gap: 6px; color: #197fe6; font-size: 11px; font-weight: 700; text-transform: uppercase; letter-spacing: 0.05em; margin-bottom: 6px; }
.context-text { font-size: 13px; line-height: 1.5; color: #475569; margin: 0 0 6px; display: -webkit-box; -webkit-line-clamp: 2; line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden; }
.context-source { display: flex; align-items: center; gap: 4px; font-size: 11px; color: #64748b; margin: 0; }

.qa-chat-area { flex: 1; overflow-y: auto; padding: 16px; -webkit-overflow-scrolling: touch; scrollbar-width: none; }
.qa-chat-area::-webkit-scrollbar { display: none; }
.empty-hint { display: flex; align-items: center; justify-content: center; height: 100%; color: #94a3b8; font-size: 14px; }
.empty-hint p { margin: 0; }

.msg-row { display: flex; align-items: flex-end; gap: 10px; margin-bottom: 12px; }
.msg-row.msg-user { justify-content: flex-end; }
.msg-avatar { width: 32px; height: 32px; border-radius: 50%; background: linear-gradient(135deg, #197fe6, #3b82f6); display: flex; align-items: center; justify-content: center; color: white; flex-shrink: 0; box-shadow: 0 2px 8px rgba(25, 127, 230, 0.25); }
.msg-bubble { padding: 12px 16px; border-radius: 18px; max-width: 85%; font-size: 15px; line-height: 1.6; word-break: break-word; overflow-wrap: break-word; }
.ai-bubble { background: white; color: #1e293b; border: 1px solid #f1f5f9; border-bottom-left-radius: 4px; box-shadow: 0 2px 8px rgba(0,0,0,0.03); }
.ai-bubble :deep(h1),.ai-bubble :deep(h2),.ai-bubble :deep(h3),.ai-bubble :deep(h4),.ai-bubble :deep(h5),.ai-bubble :deep(h6) { margin-top: 16px; margin-bottom: 8px; font-weight: 700; line-height: 1.3; color: #0f172a; }
.ai-bubble :deep(h1) { font-size: 1.5em; } .ai-bubble :deep(h2) { font-size: 1.3em; } .ai-bubble :deep(h3) { font-size: 1.15em; } .ai-bubble :deep(h4) { font-size: 1em; }
.ai-bubble :deep(p) { margin: 8px 0; line-height: 1.7; }
.ai-bubble :deep(p:first-child) { margin-top: 0; } .ai-bubble :deep(p:last-child) { margin-bottom: 0; }
.ai-bubble :deep(ul),.ai-bubble :deep(ol) { margin: 8px 0; padding-left: 24px; }
.ai-bubble :deep(li) { margin: 4px 0; line-height: 1.6; }
.ai-bubble :deep(code) { padding: 2px 6px; background: #f1f5f9; border-radius: 4px; font-family: 'Fira Code', monospace; font-size: 0.9em; color: #dc2626; }
.ai-bubble :deep(pre) { margin: 12px 0; padding: 12px 16px; background: #1e293b; border-radius: 8px; overflow-x: auto; }
.ai-bubble :deep(pre code) { padding: 0; background: transparent; color: #e2e8f0; font-size: 0.85em; line-height: 1.6; }
.ai-bubble :deep(blockquote) { margin: 12px 0; padding: 8px 16px; border-left: 4px solid #197fe6; background: #f8fafc; color: #475569; }
.ai-bubble :deep(table) { width: 100%; border-collapse: collapse; margin: 12px 0; font-size: 0.9em; }
.ai-bubble :deep(th),.ai-bubble :deep(td) { border: 1px solid #e2e8f0; padding: 8px 12px; text-align: left; }
.ai-bubble :deep(th) { background: #f8fafc; font-weight: 600; }
.user-bubble { background: linear-gradient(135deg, #197fe6, #3b82f6); color: white; border-bottom-right-radius: 4px; box-shadow: 0 4px 12px rgba(25, 127, 230, 0.22); }
.loading-bubble { display: inline-flex; align-items: center; gap: 10px; }
.typing-dots { display: inline-flex; gap: 4px; }
.typing-dots span { width: 6px; height: 6px; border-radius: 50%; background: #94a3b8; animation: typing-bounce 1.2s infinite ease-in-out; }
.typing-dots span:nth-child(2) { animation-delay: 0.15s; }
.typing-dots span:nth-child(3) { animation-delay: 0.3s; }
.loading-text { color: #64748b; font-size: 14px; }

.qa-bottom-bar { flex-shrink: 0; background: rgba(255, 255, 255, 0.92); backdrop-filter: blur(14px); -webkit-backdrop-filter: blur(14px); border-top: 1px solid rgba(226, 232, 240, 0.6); padding: 14px 16px; }
.quick-actions { display: flex; gap: 10px; overflow-x: auto; padding-bottom: 10px; scrollbar-width: none; }
.quick-actions::-webkit-scrollbar { display: none; }
.action-pill { display: inline-flex; align-items: center; gap: 8px; border: 1px solid rgba(59, 130, 246, 0.15); background: rgba(248, 250, 252, 0.92); color: #1e293b; border-radius: 999px; padding: 10px 14px; white-space: nowrap; font-size: 13px; font-weight: 600; cursor: pointer; }
.action-pill:disabled { opacity: 0.55; cursor: not-allowed; }
.action-title { max-width: 180px; overflow: hidden; text-overflow: ellipsis; }
.action-arrow { color: #197fe6; }
.qa-input-wrap { display: flex; align-items: center; gap: 10px; }
.qa-input { flex: 1; min-width: 0; border: 1px solid rgba(148, 163, 184, 0.28); border-radius: 999px; background: #fff; padding: 12px 16px; font-size: 15px; color: #1e293b; outline: none; }
.qa-input:focus { border-color: rgba(25, 127, 230, 0.45); box-shadow: 0 0 0 4px rgba(25, 127, 230, 0.08); }
.qa-input:disabled { background: #f8fafc; color: #94a3b8; }
.qa-send-btn { width: 46px; height: 46px; flex-shrink: 0; border: none; border-radius: 50%; background: linear-gradient(135deg, #197fe6, #3b82f6); color: white; display: inline-flex; align-items: center; justify-content: center; cursor: pointer; box-shadow: 0 8px 18px rgba(25, 127, 230, 0.24); }
.qa-send-btn:disabled { opacity: 0.5; cursor: not-allowed; box-shadow: none; }

@keyframes typing-bounce {
  0%, 80%, 100% { transform: translateY(0); opacity: 0.45; }
  40% { transform: translateY(-4px); opacity: 1; }
}

@media (max-width: 640px) {
  .history-sidebar { position: absolute; left: 0; top: 0; bottom: 0; z-index: 200; box-shadow: 4px 0 16px rgba(0,0,0,0.1); }
}
</style>                                                                                                                                                                    
