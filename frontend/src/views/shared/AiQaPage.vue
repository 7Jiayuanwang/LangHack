<template>
  <div class="ai-qa-page" :class="{ 'sidebar-open': sidebarOpen, embedded, 'is-companion': isCompanion }">
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
            <button v-if="!embedded" class="qa-back-btn" @click="$router.back()">
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
            <div v-if="isCompanion" class="agent-identity">
              <XyMascot :size="36" />
              <div>
                <strong>小芽</strong>
                <span>{{ articleTitle ? '伴读这篇文章' : '英语学习伙伴' }}</span>
              </div>
            </div>
            <div v-else class="qa-avatar">
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

      <main class="qa-chat-area" ref="chatContainer">
        <div class="empty-hint" v-if="messages.length === 0">
          <p v-if="articleTitle">可以用英语提问或说说你的理解，小芽会结合这篇文章陪你练习。</p>
          <div v-else-if="agentMode" class="agent-empty">
            <XyMascot :size="92" />
            <h2>今天想让英语帮你做什么？</h2>
            <p>可以聊目标、学习安排，也可以带一个具体问题来。</p>
          </div>
          <p v-else>输入问题开始对话，或点击左上角 <strong>☰</strong> 查看历史对话</p>
        </div>

        <template v-for="(msg, index) in messages" :key="index">
          <div class="msg-row" :class="msg.role === 'user' ? 'msg-user' : 'msg-ai'">
            <div v-if="msg.role !== 'user'" class="msg-avatar" :class="{ 'is-companion': isCompanion }">
              <XyMascot v-if="isCompanion" :size="28" />
              <el-icon v-else :size="18"><Monitor /></el-icon>
            </div>
            <div class="msg-bubble" :class="msg.role === 'user' ? 'user-bubble' : 'ai-bubble'">
              <div v-if="msg.role === 'user'">{{ msg.content }}</div>
              <div v-else v-html="msg.content"></div>
            </div>
          </div>
        </template>

        <div class="msg-row msg-ai" v-if="loading">
          <div class="msg-avatar" :class="{ 'is-companion': isCompanion }">
            <XyMascot v-if="isCompanion" :size="28" />
            <el-icon v-else :size="18"><Monitor /></el-icon>
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
            :placeholder="articleTitle ? '用英语提问或回答小芽...' : agentMode ? '告诉小芽你现在想学什么…' : '输入你的问题...'"
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
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ArrowLeft, ArrowRight, Reading, Monitor, Top, ChatLineSquare, List, Share } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { marked } from 'marked'
import DOMPurify from 'dompurify'
import { getAuthHeader } from '@/utils/auth'
import XyMascot from '@/components/ui/XyMascot.vue'
import { createSseParser } from '@/utils/sse'
import {
  getConversations, getConversationDetail, deleteConversation, shareConversation
} from '@/api/conversation'

const props = defineProps({
  apiPath: { type: String, required: true },
  backLabel: { type: String, default: '返回阅读' },
  agentMode: { type: Boolean, default: false },
  embedded: { type: Boolean, default: false },
  contextText: { type: String, default: '' },
  contextTitle: { type: String, default: '' }
})

const route = useRoute()

const selectedText = ref('')
const articleTitle = ref('')
const isCompanion = computed(() => props.agentMode || Boolean(articleTitle.value))
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

const candidateQuestions = computed(() => {
  if (selectedText.value) {
    return [{ label: '结合文章内容解释本段意思' }, { label: '深度解读' }]
  }
  if (props.agentMode) {
    return [
      { label: '帮我安排今天的英语学习' },
      { label: '怎样用英语探索我的兴趣？' },
      { label: '分析一下我遇到的学习困难' }
    ]
  }
  return [{ label: '英语语法疑问咨询' }, { label: '单词用法与辨析' }]
})

onMounted(() => {
  selectedText.value = props.embedded ? props.contextText : route.query.text || ''
  articleTitle.value = props.embedded ? props.contextTitle : route.query.articleTitle || ''
  // 阅读页的小芽伴读只预填问题；仅用户主动点击“试试看”时自动发出自测请求。
  inputMessage.value = !props.embedded && typeof route.query.question === 'string' ? route.query.question : ''
  if (!props.embedded && route.query.autoSend === '1' && inputMessage.value) {
    nextTick(() => { void sendMessage() })
  }
  loadConversations()
})

watch(() => [props.contextText, props.contextTitle], ([text, title]) => {
  if (!props.embedded) return
  selectedText.value = text
  articleTitle.value = title
})

const loadConversations = async () => {
  try {
    const res = await getConversations(1, 50)
    if (res?.code === 200) {
      conversations.value = res.data?.records || []
      if (conversations.value.length > 0 && !selectedText.value && !props.embedded) {
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
          ? DOMPurify.sanitize(marked(sanitizeLearnerText(m.content)))
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

// Keep old persisted answers readable after server-side answer sanitization.
const sanitizeLearnerText = (value) => {
  if (!value || typeof value !== 'string') return value
  let cleaned = value
    .replace(/<(think|thinking|analysis|reasoning)>[\s\S]*?<\/\1>/gi, '')
    .replace(/\[(think|thinking|analysis|reasoning)\][\s\S]*?\[\/\1\]/gi, '')
    .trim()
  const paragraphs = cleaned.split(/\n\s*\n/)
  const meta = /^(用户|这位用户|学生)(现在|当前|想要|需要|希望|正在|是在)/i
  const planning = /^(首先|然后|接下来|我需要|让我先|好的[,， ]*我|先要|需要先)/i
  const planningTerms = /(用户|学生|分析|计划|步骤|结合.{0,12}情况|对吧|下一步|提问)/i
  while (paragraphs.length > 1 && (meta.test(paragraphs[0].trim())
    || (planning.test(paragraphs[0].trim()) && planningTerms.test(paragraphs[0])))) {
    paragraphs.shift()
  }
  cleaned = paragraphs.join('\n\n').trim()
  cleaned = cleaned.replace(/^(用户|这位用户|学生)(现在|当前|想要|需要|希望|正在).*?[。！？!?]+\s*/is, '')
  const seen = new Set()
  cleaned = cleaned.split(/\n\s*\n/).map((paragraph) => {
    let current = paragraph.trim()
    const reviewIndex = current.search(/(?:不对[,，]|等等[,，]|不过用户|用户要详细|专业对吧|这样就可以|等下有没有|符合限制|都是中文|必要的英文标注)/i)
    if (reviewIndex >= 0) current = current.slice(0, reviewIndex).trim()
    if (!current) return ''
    const fingerprint = current.replace(/\s+/g, ' ').trim()
    if (fingerprint.length > 3 && seen.has(fingerprint)) return ''
    if (fingerprint.length > 3) seen.add(fingerprint)
    return current
  }).filter(Boolean).join('\n\n').trim()
  const sentences = cleaned.split(/(?<=[。！？!?])\s*/)
  const review = /这样就可以|检查(?:一下|下)|符合(?:要求|.*需求)|首先直接回答|只有一个追问|有没有涉及其他无关|都是英语学习|下一步|对吧/i
  let cut = 0
  let removed = false
  for (const sentence of sentences) {
    if (!review.test(sentence)) break
    cut += sentence.length
    removed = true
  }
  if (removed && cut < cleaned.length) cleaned = cleaned.slice(cut).trim()
  return cleaned || value.trim()
}

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
        articleContext: props.embedded ? articleTitle.value : route.query.context || '',
        conversationId: currentConversationId.value,
        // Article Q&A and the standalone learning companion have different
        // prompts. An article title changes the visual identity, not the scene.
        scene: props.agentMode ? 'LEARNING_AGENT' : 'QA'
      })
    })

    if (!response.ok) throw new Error(`HTTP error! status: ${response.status}`)

    const contentType = response.headers.get('content-type') || ''

    if (contentType.includes('text/event-stream') || contentType.includes('stream')) {
      // ===== SSE 流式处理 =====
      const reader = response.body.getReader()
      const decoder = new TextDecoder('utf-8')
      let streamError = ''
      let completedConversationId = null
      const parser = createSseParser(({ event, data }) => {
        // message/message_end remain supported during rolling deployments.
        if (event === 'delta' || event === 'message') {
          const content = typeof data === 'string'
            ? data
            : data?.content || data?.answer || data?.Answer || ''
          if (content) {
            rawText += content
            // The backend SSE bridge already buffers and removes the model's
            // planning preface. Do not re-sanitize an incomplete stream here:
            // changing the decision on every chunk makes earlier HTML vanish.
            messages.value[aiMsgIndex].content = DOMPurify.sanitize(marked(rawText))
            scrollToBottom()
          }
        } else if (event === 'error') {
          streamError = typeof data === 'string' ? data : data?.message || 'AI服务暂时不可用'
        } else if (event === 'done') {
          completedConversationId = data?.conversationId || null
        }
      })

      for (;;) {
        const { done, value } = await reader.read()
        if (done) break
        parser.feed(decoder.decode(value, { stream: true }))
      }
      parser.feed(decoder.decode())
      parser.end()

      if (streamError) throw new Error(streamError)
      if (completedConversationId) {
        if (!currentConversationId.value) {
          currentConversationId.value = completedConversationId
        }
        await loadConversations()
      }

      // Apply the compatibility sanitizer once, after the complete stream is known.
      if (rawText) {
        messages.value[aiMsgIndex].content = DOMPurify.sanitize(marked(rawText))
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
        messages.value[aiMsgIndex].content = DOMPurify.sanitize(marked(sanitizeLearnerText(jsonData.data.response)))
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
        const detail = typeof error?.message === 'string' && error.message.trim()
          ? error.message.trim()
          : 'AI服务暂时不可用，请稍后再试。'
        messages.value[aiMsgIndex].content = DOMPurify.sanitize(marked(`抱歉，${detail}`))
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
    const container = chatContainer.value
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

.ai-qa-page.embedded .history-sidebar { position: absolute; inset: 0 auto 0 0; z-index: 80; width: min(280px, 82%); box-shadow: 12px 0 28px rgba(0, 0, 0, .13); }
.ai-qa-page.embedded .qa-header-inner { padding: 7px 12px; }
.ai-qa-page.embedded .qa-chat-area { padding: 12px; }
.ai-qa-page.embedded .msg-bubble { font-size: 13px; }
.ai-qa-page.embedded .agent-empty h2 { font-size: 18px; }

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
.conv-item.active { background: var(--xy-brand-50); }
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
.agent-identity { display: flex; align-items: center; gap: 8px; }
.agent-identity > div:last-child { display: grid; line-height: 1.15; }
.agent-identity strong { color: var(--xy-brand-700); font-size: 13px; }
.agent-identity span { color: var(--xy-ink-muted); font-size: 10px; }
.msg-avatar.is-companion { background: transparent; border: 0; box-shadow: none; overflow: visible; }

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
.agent-empty { display: flex; flex-direction: column; align-items: center; max-width: 420px; text-align: center; }
.agent-empty h2 { margin: 14px 0 6px; color: #255f4b; font-size: 22px; }
.agent-empty p { color: #7c9189; line-height: 1.6; }

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

.ai-qa-page.is-companion { background: var(--xy-brand-50); }
.is-companion .qa-context-bar { background: var(--xy-brand-50); }
.is-companion .context-accent { background: linear-gradient(180deg, var(--xy-brand-500), var(--xy-brand-700)); }
.is-companion .context-label, .is-companion .action-arrow { color: var(--xy-brand-700); }
.is-companion .action-pill { border-color: var(--xy-brand-200); background: #fff; }
.is-companion .qa-input:focus { border-color: var(--xy-brand-500); box-shadow: 0 0 0 4px var(--xy-brand-100); }
.is-companion .qa-send-btn, .is-companion .user-bubble { background: linear-gradient(135deg, var(--xy-brand-500), var(--xy-brand-700)); }
.is-companion .qa-send-btn { box-shadow: 0 8px 18px rgba(34, 139, 103, 0.2); }
.is-companion .ai-bubble :deep(blockquote) { border-left-color: var(--xy-brand-500); }

@keyframes typing-bounce {
  0%, 80%, 100% { transform: translateY(0); opacity: 0.45; }
  40% { transform: translateY(-4px); opacity: 1; }
}

@media (max-width: 640px) {
  .history-sidebar { position: absolute; left: 0; top: 0; bottom: 0; z-index: 200; box-shadow: 4px 0 16px rgba(0,0,0,0.1); }
  .agent-identity > div:last-child { display: none; }
}
</style>                                                                                                                                                                    
