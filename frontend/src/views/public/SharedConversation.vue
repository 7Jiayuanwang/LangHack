<template>
  <div class="shared-page">
    <div class="shared-header">
      <div class="brand">LangHack AI 对话</div>
      <a href="/login" class="open-btn">在 LangHack 中打开</a>
    </div>

    <div v-if="loading" class="center-state">
      <el-icon class="is-loading" :size="40"><Loading /></el-icon>
    </div>

    <div v-else-if="requiresPassword && !unlocked" class="center-state">
      <div class="password-card">
        <div class="lock-icon">🔒</div>
        <h3>此对话需要访问密码</h3>
        <el-input
          v-model="inputPassword"
          type="password"
          placeholder="请输入访问密码"
          show-password
          @keyup.enter="unlock"
          style="margin-bottom:12px"
        />
        <el-button type="primary" @click="unlock" :loading="unlocking" style="width:100%">
          访问
        </el-button>
        <p v-if="passwordError" class="error-text">{{ passwordError }}</p>
      </div>
    </div>

    <div v-else-if="conversation" class="shared-content">
      <div class="conv-title-bar">
        <h2>{{ conversation.title || 'AI 对话' }}</h2>
        <div class="conv-meta">{{ conversation.messageCount }} 条消息 · 分享自 LangHack</div>
      </div>

      <div class="messages-container">
        <div
          v-for="(msg, i) in messages"
          :key="i"
          class="msg-row"
          :class="msg.role === 'user' ? 'msg-user' : 'msg-ai'"
        >
          <div class="msg-avatar" v-if="msg.role !== 'user'">AI</div>
          <div class="msg-bubble" :class="msg.role === 'user' ? 'user-bubble' : 'ai-bubble'">
            <div v-if="msg.role === 'user'">{{ msg.content }}</div>
            <div v-else v-html="msg.renderedContent"></div>
          </div>
        </div>
      </div>

      <div class="watermark">分享自 LangHack 英语学习平台</div>
    </div>

    <div v-else-if="errorMsg" class="center-state">
      <div class="error-card">
        <div style="font-size:48px">😕</div>
        <h3>{{ errorMsg }}</h3>
        <a href="/" class="home-link">返回首页</a>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { Loading } from '@element-plus/icons-vue'
import { marked } from 'marked'
import DOMPurify from 'dompurify'
import { getShareMeta, getSharedConversation } from '@/api/conversation'

const route = useRoute()
const token = route.params.token

const loading = ref(true)
const requiresPassword = ref(false)
const unlocked = ref(false)
const inputPassword = ref('')
const passwordError = ref('')
const unlocking = ref(false)
const conversation = ref(null)
const messages = ref([])
const errorMsg = ref('')

onMounted(async () => {
  try {
    const metaRes = await getShareMeta(token)
    if (metaRes?.code === 200) {
      requiresPassword.value = metaRes.data?.requiresPassword || false
      if (!requiresPassword.value) {
        await fetchConversation()
      }
    } else {
      errorMsg.value = '分享链接不存在或已失效'
    }
  } catch (e) {
    errorMsg.value = '加载失败，请稍后重试'
  } finally {
    loading.value = false
  }
})

const unlock = async () => {
  unlocking.value = true
  passwordError.value = ''
  try {
    await fetchConversation(inputPassword.value)
    unlocked.value = true
  } catch (e) {
    passwordError.value = e.message || '访问密码错误，请重试'
  } finally {
    unlocking.value = false
  }
}

const fetchConversation = async (password) => {
  const res = await getSharedConversation(token, password)
  if (res?.code === 200) {
    conversation.value = res.data?.conversation
    messages.value = (res.data?.messages || []).map(m => ({
      ...m,
      renderedContent: m.role === 'assistant'
        ? DOMPurify.sanitize(marked(m.content))
        : m.content
    }))
  } else {
    throw new Error(res?.message || '访问失败')
  }
}
</script>

<style scoped>
.shared-page {
  min-height: 100vh;
  background: #f6f7f8;
  font-family: 'Inter', -apple-system, sans-serif;
}

.shared-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 24px;
  background: white;
  border-bottom: 1px solid #e2e8f0;
  position: sticky;
  top: 0;
  z-index: 10;
}

.brand {
  font-size: 18px;
  font-weight: 800;
  background: linear-gradient(135deg, #197fe6, #3b82f6);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

.open-btn {
  padding: 8px 16px;
  background: #197fe6;
  color: white;
  border-radius: 8px;
  text-decoration: none;
  font-size: 14px;
  font-weight: 600;
}

.center-state {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: calc(100vh - 60px);
}

.password-card, .error-card {
  background: white;
  border-radius: 16px;
  padding: 32px;
  width: 320px;
  text-align: center;
  box-shadow: 0 4px 24px rgba(0,0,0,0.08);
}

.lock-icon { font-size: 48px; margin-bottom: 12px; }
.password-card h3, .error-card h3 { margin: 0 0 16px; color: #1e293b; }
.error-text { color: #ef4444; font-size: 13px; margin-top: 8px; }
.home-link { color: #197fe6; text-decoration: none; font-weight: 600; }

.shared-content {
  max-width: 800px;
  margin: 0 auto;
  padding: 24px 16px;
}

.conv-title-bar {
  margin-bottom: 24px;
}

.conv-title-bar h2 {
  font-size: 22px;
  font-weight: 800;
  color: #1e293b;
  margin: 0 0 4px;
}

.conv-meta {
  font-size: 13px;
  color: #94a3b8;
}

.messages-container {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.msg-row { display: flex; align-items: flex-end; gap: 10px; }
.msg-row.msg-user { justify-content: flex-end; }
.msg-avatar { width: 32px; height: 32px; border-radius: 50%; background: linear-gradient(135deg, #197fe6, #3b82f6); display: flex; align-items: center; justify-content: center; color: white; font-size: 11px; font-weight: 700; flex-shrink: 0; }
.msg-bubble { padding: 12px 16px; border-radius: 18px; max-width: 80%; font-size: 15px; line-height: 1.6; word-break: break-word; }
.ai-bubble { background: white; color: #1e293b; border: 1px solid #f1f5f9; border-bottom-left-radius: 4px; box-shadow: 0 2px 8px rgba(0,0,0,0.03); }
.ai-bubble :deep(p) { margin: 8px 0; } .ai-bubble :deep(p:first-child) { margin-top: 0; } .ai-bubble :deep(p:last-child) { margin-bottom: 0; }
.ai-bubble :deep(code) { padding: 2px 6px; background: #f1f5f9; border-radius: 4px; font-size: 0.9em; color: #dc2626; }
.user-bubble { background: linear-gradient(135deg, #197fe6, #3b82f6); color: white; border-bottom-right-radius: 4px; }

.watermark {
  text-align: center;
  color: #cbd5e1;
  font-size: 12px;
  margin-top: 32px;
  padding: 16px;
}
</style>
