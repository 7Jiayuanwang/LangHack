<template>
  <teleport to="body">
    <transition name="notification-fade">
      <div v-if="visible" class="notification-overlay" @click.self="handleClose">
        <div class="notification-popup">
          <!-- 装饰顶部 -->
          <div class="popup-header">
            <div class="header-decoration"></div>
            <h3 class="popup-title">{{ currentNotification?.title }}</h3>
            <button class="close-btn" @click="handleClose">&times;</button>
          </div>

          <!-- 内容区域 -->
          <div class="popup-body">
            <div class="popup-content" v-html="formatContent(currentNotification?.content)"></div>
          </div>

          <!-- 底部翻页/关闭 -->
          <div class="popup-footer">
            <span class="page-indicator" v-if="notifications.length > 1">
              {{ currentIndex + 1 }} / {{ notifications.length }}
            </span>
            <div class="footer-buttons">
              <button
                v-if="currentIndex > 0"
                class="nav-btn prev-btn"
                @click="prevNotification"
              >上一条</button>
              <button
                v-if="currentIndex < notifications.length - 1"
                class="nav-btn next-btn"
                @click="nextNotification"
              >下一条</button>
              <button class="nav-btn confirm-btn" @click="handleClose">我知道了</button>
            </div>
          </div>
        </div>
      </div>
    </transition>
  </teleport>
</template>

<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import { getUnreadNotifications, markNotificationRead } from '@/api/notification'
import { useUserStore } from '@/store/user'

const userStore = useUserStore()
const visible = ref(false)
const notifications = ref([])
const currentIndex = ref(0)

const currentNotification = computed(() => notifications.value[currentIndex.value] || null)

function formatContent(content) {
  if (!content) return ''
  return content.replace(/\n/g, '<br>')
}

async function fetchUnread() {
  try {
    const res = await getUnreadNotifications()
    if (res.data?.code === 200 && res.data.data?.length > 0) {
      notifications.value = res.data.data
      currentIndex.value = 0
      visible.value = true
    }
  } catch (e) {
    // 静默失败
  }
}

function prevNotification() {
  if (currentIndex.value > 0) {
    markCurrentRead()
    currentIndex.value--
  }
}

function nextNotification() {
  if (currentIndex.value < notifications.value.length - 1) {
    markCurrentRead()
    currentIndex.value++
  }
}

async function markCurrentRead() {
  const n = currentNotification.value
  if (n) {
    try {
      await markNotificationRead(n.id)
    } catch (e) {
      // 静默
    }
  }
}

async function handleClose() {
  await markCurrentRead()
  visible.value = false
}

// 登录后自动检查
watch(() => userStore.token, (newToken) => {
  if (newToken) {
    setTimeout(() => fetchUnread(), 1000)
  }
}, { immediate: false })

onMounted(() => {
  if (userStore.token) {
    setTimeout(() => fetchUnread(), 800)
  }
})
</script>

<style scoped>
.notification-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.6);
  backdrop-filter: blur(4px);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 9999;
  padding: 20px;
}

.notification-popup {
  width: 90%;
  max-width: 480px;
  max-height: 80vh;
  background: linear-gradient(135deg, #1a237e 0%, #283593 50%, #1565c0 100%);
  border-radius: 16px;
  box-shadow:
    0 20px 60px rgba(0, 0, 0, 0.5),
    0 0 0 1px rgba(255, 255, 255, 0.1),
    inset 0 1px 0 rgba(255, 255, 255, 0.2);
  display: flex;
  flex-direction: column;
  overflow: hidden;
  animation: popupBounce 0.4s cubic-bezier(0.34, 1.56, 0.64, 1);
}

@keyframes popupBounce {
  from {
    opacity: 0;
    transform: scale(0.8) translateY(20px);
  }
  to {
    opacity: 1;
    transform: scale(1) translateY(0);
  }
}

.popup-header {
  position: relative;
  padding: 20px 24px 16px;
  text-align: center;
  border-bottom: 1px solid rgba(255, 255, 255, 0.1);
}

.header-decoration {
  position: absolute;
  top: 0;
  left: 50%;
  transform: translateX(-50%);
  width: 60px;
  height: 4px;
  background: linear-gradient(90deg, #ffd54f, #ffb300);
  border-radius: 0 0 4px 4px;
}

.popup-title {
  margin: 0;
  font-size: 20px;
  font-weight: 700;
  color: #fff;
  text-shadow: 0 2px 4px rgba(0, 0, 0, 0.3);
  line-height: 1.4;
}

.close-btn {
  position: absolute;
  top: 12px;
  right: 16px;
  width: 32px;
  height: 32px;
  border: none;
  background: rgba(255, 255, 255, 0.1);
  color: rgba(255, 255, 255, 0.8);
  font-size: 20px;
  border-radius: 50%;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.2s;
}

.close-btn:hover {
  background: rgba(255, 255, 255, 0.2);
  color: #fff;
}

.popup-body {
  flex: 1;
  padding: 20px 24px;
  overflow-y: auto;
  min-height: 120px;
  max-height: 400px;
}

.popup-content {
  font-size: 15px;
  line-height: 1.8;
  color: rgba(255, 255, 255, 0.9);
  word-break: break-word;
}

.popup-footer {
  padding: 16px 24px;
  border-top: 1px solid rgba(255, 255, 255, 0.1);
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.page-indicator {
  font-size: 13px;
  color: rgba(255, 255, 255, 0.6);
}

.footer-buttons {
  display: flex;
  gap: 8px;
}

.nav-btn {
  padding: 8px 16px;
  border: none;
  border-radius: 8px;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s;
}

.prev-btn,
.next-btn {
  background: rgba(255, 255, 255, 0.15);
  color: rgba(255, 255, 255, 0.9);
}

.prev-btn:hover,
.next-btn:hover {
  background: rgba(255, 255, 255, 0.25);
}

.confirm-btn {
  background: linear-gradient(135deg, #ffd54f, #ffb300);
  color: #1a237e;
  font-weight: 600;
  box-shadow: 0 4px 12px rgba(255, 183, 0, 0.3);
}

.confirm-btn:hover {
  transform: translateY(-1px);
  box-shadow: 0 6px 16px rgba(255, 183, 0, 0.4);
}

/* Transition */
.notification-fade-enter-active {
  transition: opacity 0.3s;
}

.notification-fade-leave-active {
  transition: opacity 0.2s;
}

.notification-fade-enter-from,
.notification-fade-leave-to {
  opacity: 0;
}

/* Scrollbar */
.popup-body::-webkit-scrollbar {
  width: 4px;
}

.popup-body::-webkit-scrollbar-thumb {
  background: rgba(255, 255, 255, 0.3);
  border-radius: 2px;
}
</style>
