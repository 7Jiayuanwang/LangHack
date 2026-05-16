<template>
  <Teleport to="body">
    <!-- 桌面端：SDK 容器（SDK 自行注入 UI） -->
    <div v-if="!isMobile" ref="containerRef"></div>
    <!-- 手机端：自定义可拖拽气泡 -->
    <div
      v-else
      ref="bubbleRef"
      class="hiagent-custom-bubble"
      :class="{ 'hiagent-custom-bubble--dragging': isDragging }"
      :style="bubbleStyle"
    >
      <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
        <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/>
        <line x1="9" y1="10" x2="15" y2="10"/>
        <line x1="12" y1="7" x2="12" y2="13"/>
      </svg>
    </div>
  </Teleport>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onBeforeUnmount } from 'vue'

const props = defineProps({
  appKey: {
    type: String,
    default: 'd83urt96rdh3v9agk58g'
  },
  baseUrl: {
    type: String,
    default: 'https://agent.imau.edu.cn:32300'
  },
  // 手机端点击气泡跳转的 HiAgent 网页地址
  chatUrl: {
    type: String,
    default: ''
  }
})

const chatPageUrl = computed(() => {
  return props.chatUrl || props.baseUrl
})

// ---- 响应式设备检测 ----
const isMobile = ref(false)
function checkMobile() {
  isMobile.value = window.innerWidth < 400 && window.innerHeight < 1000
}

// ---- 桌面端 SDK 逻辑 ----
const containerRef = ref(null)
let hiagentClient = null
let sdkInitialized = false
let observer = null
let sdkBubbleEl = null
let sdkDragHandlers = null
const SDK_POSITION_KEY = 'hiagent_bubble_position'

function loadPosition() {
  try {
    const raw = localStorage.getItem(SDK_POSITION_KEY)
    if (raw) return JSON.parse(raw)
  } catch { /* ignore */ }
  return null
}

function savePosition(pos) {
  try {
    localStorage.setItem(SDK_POSITION_KEY, JSON.stringify(pos))
  } catch { /* ignore */ }
}

function applyPosition(el) {
  const saved = loadPosition()
  if (!saved) return
  el.style.left = saved.x + 'px'
  el.style.top = saved.y + 'px'
  el.style.right = 'auto'
  el.style.bottom = 'auto'
}

function attachDrag(el, onTap) {
  const startDrag = (event) => {
    if (sdkDragHandlers) {
      document.removeEventListener('mousemove', sdkDragHandlers.move)
      document.removeEventListener('mouseup', sdkDragHandlers.end)
      document.removeEventListener('touchmove', sdkDragHandlers.move)
      document.removeEventListener('touchend', sdkDragHandlers.end)
      sdkDragHandlers = null
    }

    const isTouch = event.type === 'touchstart'
    if (!isTouch && event.button !== 0) return

    const startX = isTouch ? event.touches[0].clientX : event.clientX
    const startY = isTouch ? event.touches[0].clientY : event.clientY
    const startTime = Date.now()
    const rect = el.getBoundingClientRect()
    let hasMoved = false

    const offsetX = startX - rect.left
    const offsetY = startY - rect.top

    if (isTouch) event.preventDefault()

    const handleMove = (e) => {
      const moveX = e.type === 'touchmove' ? e.touches[0].clientX : e.clientX
      const moveY = e.type === 'touchmove' ? e.touches[0].clientY : e.clientY

      const deltaX = Math.abs(moveX - startX)
      const deltaY = Math.abs(moveY - startY)
      if (deltaX > 5 || deltaY > 5) hasMoved = true

      el.style.left = (moveX - offsetX) + 'px'
      el.style.top = (moveY - offsetY) + 'px'
      el.style.right = 'auto'
      el.style.bottom = 'auto'
    }

    const handleEnd = () => {
      document.removeEventListener('mousemove', handleMove)
      document.removeEventListener('mouseup', handleEnd)
      document.removeEventListener('touchmove', handleMove)
      document.removeEventListener('touchend', handleEnd)
      sdkDragHandlers = null

      if (hasMoved) {
        const finalRect = el.getBoundingClientRect()
        savePosition({ x: finalRect.left, y: finalRect.top })
      } else if (onTap) {
        onTap()
      }
    }

    document.addEventListener('mousemove', handleMove)
    document.addEventListener('mouseup', handleEnd)
    document.addEventListener('touchmove', handleMove, { passive: false })
    document.addEventListener('touchend', handleEnd)
    sdkDragHandlers = { move: handleMove, end: handleEnd }
  }

  el.addEventListener('mousedown', startDrag)
  el.addEventListener('touchstart', startDrag, { passive: false })
}

function findAndAttachSdkBubble() {
  const candidates = document.querySelectorAll(
    '[class*="hiagent"], [class*="Hiagent"], [class*="web-client"], [class*="chat"], [class*="float"], [class*="bubble"]'
  )
  for (const el of candidates) {
    const style = window.getComputedStyle(el)
    if (style.position === 'fixed' && parseInt(style.zIndex) > 100) {
      sdkBubbleEl = el
      applyPosition(el)
      attachDrag(el, null)
      return
    }
  }
}

function setupSdkBubbleObserver() {
  observer = new MutationObserver((mutations) => {
    for (const mutation of mutations) {
      for (const node of mutation.addedNodes) {
        if (node.nodeType !== 1) continue
        const el = node
        const style = window.getComputedStyle(el)
        if (style.position === 'fixed' && parseInt(style.zIndex) > 100) {
          sdkBubbleEl = el
          applyPosition(el)
          attachDrag(el, null)
          observer.disconnect()
          observer = null
          return
        }
        const children = el.querySelectorAll?.('[class*="hiagent"], [class*="Hiagent"], [class*="chat"], [class*="float"], [class*="bubble"], [class*="web-client"]')
        if (children?.length) {
          for (const child of children) {
            const cs = window.getComputedStyle(child)
            if (cs.position === 'fixed' && parseInt(cs.zIndex) > 100) {
              sdkBubbleEl = child
              applyPosition(child)
              attachDrag(child, null)
              observer.disconnect()
              observer = null
              return
            }
          }
        }
      }
    }
  })
  observer.observe(document.body, { childList: true, subtree: true })
}

function initSdk() {
  if (sdkInitialized) return
  if (window.HiagentWebSDK && window.HiagentWebSDK.WebClient) {
    try {
      hiagentClient = new window.HiagentWebSDK.WebClient({
        appKey: props.appKey,
        baseUrl: props.baseUrl
      })
      sdkInitialized = true
      console.log('HiAgent WebClient 已初始化')

      requestAnimationFrame(() => {
        setTimeout(() => {
          findAndAttachSdkBubble()
          if (!sdkBubbleEl) setupSdkBubbleObserver()
        }, 500)
      })
    } catch (error) {
      console.error('HiAgent 初始化失败:', error)
    }
  } else {
    setTimeout(initSdk, 100)
  }
}

function destroySdk() {
  if (sdkDragHandlers) {
    document.removeEventListener('mousemove', sdkDragHandlers.move)
    document.removeEventListener('mouseup', sdkDragHandlers.end)
    document.removeEventListener('touchmove', sdkDragHandlers.move)
    document.removeEventListener('touchend', sdkDragHandlers.end)
    sdkDragHandlers = null
  }
  if (observer) {
    observer.disconnect()
    observer = null
  }
  if (hiagentClient && typeof hiagentClient.destroy === 'function') {
    try { hiagentClient.destroy() } catch { /* ignore */ }
  }
  hiagentClient = null
  sdkBubbleEl = null
  sdkInitialized = false
}

// ---- 手机端自定义气泡 ----
const bubbleRef = ref(null)
const isDragging = ref(false)
const bubblePos = reactive({ x: -1, y: -1 }) // -1 = 使用 CSS 默认位置（右下角）
const BUBBLE_POS_KEY = 'hiagent_custom_bubble_position'
let bubbleDragHandlers = null

function loadBubblePosition() {
  try {
    const raw = localStorage.getItem(BUBBLE_POS_KEY)
    if (raw) return JSON.parse(raw)
  } catch { /* ignore */ }
  return null
}

function saveBubblePosition(pos) {
  try {
    localStorage.setItem(BUBBLE_POS_KEY, JSON.stringify(pos))
  } catch { /* ignore */ }
}

const bubbleStyle = computed(() => {
  if (bubblePos.x >= 0 && bubblePos.y >= 0) {
    return { left: bubblePos.x + 'px', top: bubblePos.y + 'px', right: 'auto', bottom: 'auto' }
  }
  return {}
})

function openChatPage() {
  window.open(chatPageUrl.value, '_blank')
}

function setupBubbleDrag() {
  const el = bubbleRef.value
  if (!el) return

  const startDrag = (event) => {
    if (bubbleDragHandlers) {
      document.removeEventListener('mousemove', bubbleDragHandlers.move)
      document.removeEventListener('mouseup', bubbleDragHandlers.end)
      document.removeEventListener('touchmove', bubbleDragHandlers.move)
      document.removeEventListener('touchend', bubbleDragHandlers.end)
      bubbleDragHandlers = null
    }

    const isTouch = event.type === 'touchstart'
    if (!isTouch && event.button !== 0) return

    const startX = isTouch ? event.touches[0].clientX : event.clientX
    const startY = isTouch ? event.touches[0].clientY : event.clientY
    const startTime = Date.now()
    const rect = el.getBoundingClientRect()
    let hasMoved = false

    const offsetX = startX - rect.left
    const offsetY = startY - rect.top

    isDragging.value = true
    if (isTouch) event.preventDefault()

    const handleMove = (e) => {
      const moveX = e.type === 'touchmove' ? e.touches[0].clientX : e.clientX
      const moveY = e.type === 'touchmove' ? e.touches[0].clientY : e.clientY

      const deltaX = Math.abs(moveX - startX)
      const deltaY = Math.abs(moveY - startY)
      if (deltaX > 5 || deltaY > 5) hasMoved = true

      bubblePos.x = moveX - offsetX
      bubblePos.y = moveY - offsetY
    }

    const handleEnd = () => {
      document.removeEventListener('mousemove', handleMove)
      document.removeEventListener('mouseup', handleEnd)
      document.removeEventListener('touchmove', handleMove)
      document.removeEventListener('touchend', handleEnd)
      bubbleDragHandlers = null

      if (hasMoved) {
        saveBubblePosition({ x: bubblePos.x, y: bubblePos.y })
      } else {
        // 未拖拽 → 点击行为
        openChatPage()
      }
      isDragging.value = false
    }

    document.addEventListener('mousemove', handleMove)
    document.addEventListener('mouseup', handleEnd)
    document.addEventListener('touchmove', handleMove, { passive: false })
    document.addEventListener('touchend', handleEnd)
    bubbleDragHandlers = { move: handleMove, end: handleEnd }
  }

  el.addEventListener('mousedown', startDrag)
  el.addEventListener('touchstart', startDrag, { passive: false })
}

function restoreBubblePosition() {
  const saved = loadBubblePosition()
  if (saved) {
    bubblePos.x = saved.x
    bubblePos.y = saved.y
  }
}

function destroyBubble() {
  if (bubbleDragHandlers) {
    document.removeEventListener('mousemove', bubbleDragHandlers.move)
    document.removeEventListener('mouseup', bubbleDragHandlers.end)
    document.removeEventListener('touchmove', bubbleDragHandlers.move)
    document.removeEventListener('touchend', bubbleDragHandlers.end)
    bubbleDragHandlers = null
  }
}

// ---- 模式切换 ----
let prevMobile = null
function handleResize() {
  checkMobile()
  if (prevMobile === isMobile.value) return
  prevMobile = isMobile.value

  if (isMobile.value) {
    // 桌面 → 手机：销毁 SDK，显示自定义气泡
    destroySdk()
    // 等下一帧自定义气泡 DOM 渲染后绑定拖拽
    requestAnimationFrame(() => {
      restoreBubblePosition()
      setupBubbleDrag()
    })
  } else {
    // 手机 → 桌面：清理自定义气泡，初始化 SDK
    destroyBubble()
    initSdk()
  }
}

// ---- 生命周期 ----
onMounted(() => {
  checkMobile()
  prevMobile = isMobile.value

  if (isMobile.value) {
    restoreBubblePosition()
    // 等 DOM 渲染
    requestAnimationFrame(() => {
      setupBubbleDrag()
    })
  } else {
    initSdk()
  }

  window.addEventListener('resize', handleResize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
  destroySdk()
  destroyBubble()
})
</script>

<style scoped>
/* 手机端自定义气泡 */
.hiagent-custom-bubble {
  position: fixed;
  right: 16px;
  bottom: 80px;
  z-index: 9999;
  width: 50px;
  height: 50px;
  border-radius: 50%;
  background: linear-gradient(135deg, #4f6ef6 0%, #6c5ce7 100%);
  box-shadow: 0 4px 16px rgba(79, 110, 246, 0.4), 0 0 0 2px rgba(255, 255, 255, 0.3);
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  cursor: pointer;
  touch-action: none;
  user-select: none;
  -webkit-tap-highlight-color: transparent;
  transition: transform 0.2s ease, box-shadow 0.2s ease;
}

.hiagent-custom-bubble:active {
  transform: scale(0.95);
}

.hiagent-custom-bubble--dragging {
  transform: scale(1.05);
  box-shadow: 0 8px 24px rgba(79, 110, 246, 0.5), 0 0 0 3px rgba(255, 255, 255, 0.4);
  transition: none;
}
</style>
