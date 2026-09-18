<template>
  <div v-if="visible" class="companion-widget" :class="{ 'is-immersive': isImmersive, 'is-chat-page': isChatPage }">
    <div v-if="opened" v-show="expanded" class="companion-scrim" @click="closePanel"></div>

    <section
      v-if="opened"
      v-show="expanded"
      id="agent-companion-panel"
      class="companion-panel"
      aria-label="小芽英语学习伙伴"
    >
      <header class="companion-header">
        <AgentMascot :size="42" />
        <div class="companion-title">
          <strong>小芽</strong>
          <span>你的英语学习伙伴</span>
        </div>
        <button type="button" class="close-button" aria-label="关闭小芽" @click="closePanel">×</button>
      </header>

      <nav class="companion-tabs" aria-label="小芽功能">
        <button type="button" :class="{ active: activeTab === 'chat' }" @click="activeTab = 'chat'">对话</button>
        <button type="button" :class="{ active: activeTab === 'tools' }" @click="activeTab = 'tools'">学习工具</button>
        <button type="button" :class="{ active: activeTab === 'messages' }" @click="openMessages">
          消息<span v-if="unreadCount" class="tab-count">{{ badgeLabel }}</span>
        </button>
      </nav>

      <div v-show="activeTab === 'chat'" class="companion-chat">
        <p v-if="isArticlePage && articleTitle" class="article-context">正在阅读：{{ articleTitle }}</p>
        <AiQaPage
          api-path="/api/student/ai-qa/chat/stream"
          :context-text="selectedText"
          :context-title="articleTitle"
          agent-mode
          embedded
        />
      </div>

      <div v-show="activeTab === 'tools'" class="companion-tools">
        <p class="section-intro">让小芽从你的目标、课表和学习记录出发，帮你找到下一步。</p>
        <button type="button" @click="navigate('/student/learning-agent')">
          <strong>学习档案与推荐</strong><span>目标、兴趣、课表、英文内容和记忆</span>
        </button>
        <button type="button" @click="focusArticleTool('.version-pill-selector')">
          <strong>阅读与难度适配</strong><span>{{ isArticlePage ? '查看当前文章的难度版本' : '找到文章后，使用阅读页的不同难度版本' }}</span>
        </button>
        <button type="button" @click="focusArticleTool('.ai-quiz-section')">
          <strong>文章出题</strong><span>{{ isArticlePage ? '在当前文章生成练习题' : '打开一篇文章，在阅读页让 AI 为你出题' }}</span>
        </button>
        <button type="button" @click="navigate('/student/my-study')">
          <strong>学习任务与记录</strong><span>继续课程、复习错题并查看学习进度</span>
        </button>
        <button type="button" @click="navigate('/student/user-texts')">
          <strong>我的写作</strong><span>查看自己上传的内容，带着作文问题来对话</span>
        </button>
      </div>

      <div v-show="activeTab === 'messages'" class="companion-messages">
        <div class="messages-heading">
          <strong>待读消息</strong>
          <button type="button" @click="refreshUnread">刷新</button>
        </div>
        <p v-if="notifications.length === 0" class="empty-messages">暂时没有待读消息。继续保持好奇心，今天也和英语见个面吧！</p>
        <article v-for="item in notifications" :key="item.id" class="message-card">
          <button type="button" class="message-title" :aria-expanded="selectedMessageId === item.id" @click="selectedMessageId = selectedMessageId === item.id ? null : item.id">
            <span>{{ item.title }}</span><span aria-hidden="true">{{ selectedMessageId === item.id ? '−' : '+' }}</span>
          </button>
          <template v-if="selectedMessageId === item.id">
            <p>{{ item.content }}</p>
            <button type="button" class="mark-read" @click="markRead(item.id)">我已读过</button>
          </template>
        </article>
      </div>
    </section>

    <button
      type="button"
      class="companion-launcher"
      :aria-label="unreadCount ? `打开小芽，有 ${unreadCount} 条未读消息` : '打开小芽，开始英语学习对话'"
      aria-controls="agent-companion-panel"
      :aria-expanded="expanded"
      @click="togglePanel"
    >
      <AgentMascot :size="62" />
      <span class="launcher-name">小芽</span>
      <span v-if="unreadCount" class="unread-badge" aria-label="未读消息">{{ badgeLabel }}</span>
    </button>
  </div>
</template>

<script setup>
import { computed, defineAsyncComponent, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/store/user'
import { getUnreadNotifications, markNotificationRead } from '@/api/notification'
import AgentMascot from '@/components/AgentMascot.vue'

// The full chat interface is loaded only when the student first opens 小芽.
const AiQaPage = defineAsyncComponent(() => import('@/views/shared/AiQaPage.vue'))

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const visible = computed(() => userStore.isLoggedIn && ['STUDENT', 'USER'].includes(userStore.userRole) && !route.meta.public)
const isArticlePage = computed(() => route.path.startsWith('/article/'))
const isImmersive = computed(() => isArticlePage.value || route.path === '/student/video-player')
const isChatPage = computed(() => route.path === '/student/ai-qa')
const opened = ref(false)
const expanded = ref(false)
const activeTab = ref('chat')
const notifications = ref([])
const selectedMessageId = ref(null)
const articleTitle = ref('')
const selectedText = ref('')
const unreadCount = computed(() => notifications.value.length)
const badgeLabel = computed(() => unreadCount.value > 99 ? '99+' : String(unreadCount.value))
let refreshTimer = null

const refreshUnread = async () => {
  if (!visible.value) return
  try {
    const response = await getUnreadNotifications()
    if (response?.code === 200) notifications.value = Array.isArray(response.data) ? response.data : []
  } catch {
    // A temporary notification failure must not interrupt reading or chat.
  }
}

const startRefreshing = () => {
  if (refreshTimer) clearInterval(refreshTimer)
  if (!visible.value) return
  refreshUnread()
  refreshTimer = setInterval(refreshUnread, 60 * 1000)
}

const stopRefreshing = () => {
  if (refreshTimer) clearInterval(refreshTimer)
  refreshTimer = null
}

const togglePanel = () => {
  if (isArticlePage.value && !expanded.value) {
    articleTitle.value = document.querySelector('.header-title')?.textContent?.trim() || ''
    selectedText.value = window.getSelection?.()?.toString()?.trim().slice(0, 2000) || ''
  }
  opened.value = true
  expanded.value = !expanded.value
  if (expanded.value) refreshUnread()
}

const closePanel = () => { expanded.value = false }

const openMessages = () => {
  activeTab.value = 'messages'
  refreshUnread()
}

const markRead = async (id) => {
  try {
    const response = await markNotificationRead(id)
    if (response?.code === 200) {
      notifications.value = notifications.value.filter(item => item.id !== id)
      selectedMessageId.value = null
    }
  } catch {
    // Keep the message unread so it can be retried.
  }
}

const navigate = (path) => {
  closePanel()
  router.push(path)
}

const focusArticleTool = async (selector) => {
  closePanel()
  if (isArticlePage.value) {
    await nextTick()
    const target = document.querySelector(selector)
    if (target) {
      target.scrollIntoView({ behavior: 'smooth', block: 'center' })
      return
    }
  }
  router.push('/articles')
}

const onVisibilityChange = () => {
  if (!document.hidden) refreshUnread()
}

const onKeyDown = (event) => {
  if (event.key === 'Escape' && expanded.value) closePanel()
}

watch(visible, (show) => {
  if (show) startRefreshing()
  else {
    stopRefreshing()
    closePanel()
    notifications.value = []
  }
}, { immediate: true })

watch(() => userStore.token, () => {
  opened.value = false
  activeTab.value = 'chat'
  selectedMessageId.value = null
})

watch(() => route.fullPath, () => {
  closePanel()
  articleTitle.value = ''
  selectedText.value = ''
})

onMounted(() => {
  document.addEventListener('visibilitychange', onVisibilityChange)
  document.addEventListener('keydown', onKeyDown)
})

onUnmounted(() => {
  stopRefreshing()
  document.removeEventListener('visibilitychange', onVisibilityChange)
  document.removeEventListener('keydown', onKeyDown)
})
</script>

<style scoped>
.companion-widget { position: relative; z-index: 3000; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', 'PingFang SC', sans-serif; }
.companion-scrim { position: fixed; inset: 0; background: rgba(14, 38, 31, .18); z-index: 0; }
.companion-launcher { position: fixed; right: max(14px, env(safe-area-inset-right, 0px)); bottom: calc(88px + env(safe-area-inset-bottom, 0px)); z-index: 2; display: flex; flex-direction: column; align-items: center; justify-content: center; width: 78px; min-height: 84px; padding: 4px 4px 6px; border: 1px solid #d4f0e3; border-radius: 26px; background: rgba(255, 255, 255, .97); box-shadow: 0 8px 28px rgba(24, 94, 69, .2); cursor: pointer; color: #176e51; }
.companion-launcher:hover { transform: translateY(-3px); box-shadow: 0 12px 30px rgba(24, 94, 69, .27); }
.companion-launcher:focus-visible, .companion-panel button:focus-visible { outline: 3px solid #21a474; outline-offset: 2px; }
.launcher-name { margin-top: -4px; font-size: 12px; font-weight: 800; }
.unread-badge { position: absolute; top: -7px; right: -6px; display: grid; place-items: center; min-width: 23px; height: 23px; padding: 0 5px; border: 2px solid white; border-radius: 14px; background: #ef4967; color: white; font-size: 11px; font-weight: 800; }
.companion-panel { position: fixed; right: max(14px, env(safe-area-inset-right, 0px)); bottom: calc(186px + env(safe-area-inset-bottom, 0px)); z-index: 1; display: flex; flex-direction: column; width: min(420px, calc(100vw - 28px)); height: min(620px, calc(100dvh - 210px)); min-height: 280px; overflow: hidden; border: 1px solid #d3eadf; border-radius: 24px; background: white; box-shadow: 0 22px 55px rgba(15, 66, 49, .26); }
.companion-header { display: flex; align-items: center; gap: 10px; flex: 0 0 auto; padding: 10px 14px; background: linear-gradient(115deg, #e8fff5, #f7fffb); border-bottom: 1px solid #e0f1e9; }
.companion-title { display: grid; flex: 1; line-height: 1.25; color: #185e47; }
.companion-title strong { font-size: 16px; }
.companion-title span { font-size: 11px; color: #658476; }
.close-button { width: 32px; height: 32px; border: 0; border-radius: 50%; background: #fff; color: #48705e; font-size: 24px; cursor: pointer; }
.companion-tabs { display: flex; flex: 0 0 auto; border-bottom: 1px solid #e6eee9; }
.companion-tabs button { flex: 1; padding: 10px 4px; border: 0; background: white; color: #668275; font-size: 13px; font-weight: 700; cursor: pointer; }
.companion-tabs button.active { color: #147a56; box-shadow: inset 0 -3px #29ad78; }
.tab-count { margin-left: 4px; padding: 1px 5px; border-radius: 9px; background: #fce4e9; color: #ce314e; font-size: 10px; }
.companion-chat { display: flex; flex: 1; flex-direction: column; min-height: 0; }
.companion-chat :deep(.ai-qa-page) { flex: 1; min-height: 0; }
.article-context { flex: 0 0 auto; overflow: hidden; padding: 7px 12px; background: #f2faf5; color: #3d6f55; font-size: 11px; white-space: nowrap; text-overflow: ellipsis; }
.companion-tools, .companion-messages { flex: 1; min-height: 0; overflow-y: auto; padding: 16px; }
.section-intro { margin-bottom: 12px; color: #61776c; font-size: 13px; line-height: 1.6; }
.companion-tools > button { display: grid; width: 100%; gap: 4px; margin-bottom: 9px; padding: 13px 15px; border: 1px solid #deeee5; border-radius: 14px; background: #f8fdf9; text-align: left; cursor: pointer; }
.companion-tools > button:hover { border-color: #57bd90; background: #eefaf2; }
.companion-tools strong { color: #1b634c; font-size: 14px; }
.companion-tools span { color: #698276; font-size: 12px; }
.messages-heading { display: flex; align-items: center; justify-content: space-between; margin-bottom: 12px; color: #205b46; }
.messages-heading button { border: 0; background: none; color: #15845b; cursor: pointer; }
.empty-messages { padding: 28px 14px; border-radius: 14px; background: #f7fcf9; color: #638171; font-size: 13px; line-height: 1.7; text-align: center; }
.message-card { margin-bottom: 9px; padding: 12px; border: 1px solid #e0eee6; border-radius: 13px; background: #fbfefb; }
.message-title { display: flex; justify-content: space-between; width: 100%; border: 0; background: none; color: #245d48; font-weight: 700; text-align: left; cursor: pointer; }
.message-card p { margin: 10px 0; color: #526b5d; font-size: 13px; line-height: 1.6; white-space: pre-line; overflow-wrap: anywhere; }
.mark-read { padding: 6px 12px; border: 0; border-radius: 8px; background: #e4f5eb; color: #137e54; font-weight: 700; cursor: pointer; }
.is-immersive .companion-launcher { bottom: calc(18px + env(safe-area-inset-bottom, 0px)); }
.is-immersive .companion-panel { bottom: calc(114px + env(safe-area-inset-bottom, 0px)); height: min(620px, calc(100dvh - 138px)); }
.is-chat-page .companion-launcher { top: calc(70px + env(safe-area-inset-top, 0px)); bottom: auto; }
.is-chat-page .companion-panel { top: calc(158px + env(safe-area-inset-top, 0px)); bottom: auto; height: min(620px, calc(100dvh - 176px)); }
@media (max-width: 480px) { .companion-panel { height: calc(100dvh - 195px); } .is-immersive .companion-panel { height: calc(100dvh - 125px); } }
@media (prefers-reduced-motion: reduce) { .companion-launcher:hover { transform: none; } }
</style>
