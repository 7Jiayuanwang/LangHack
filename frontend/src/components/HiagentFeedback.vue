<template>
  <Teleport to="body">
    <div ref="containerRef"></div>
  </Teleport>
</template>

<script setup>
import { onMounted, onBeforeUnmount, ref } from 'vue'

const containerRef = ref(null)
let hiagentClient = null
let initialized = false
let observer = null
let bubbleEl = null
let activeDragHandlers = null

const props = defineProps({
  appKey: {
    type: String,
    default: 'd83urt96rdh3v9agk58g'
  },
  baseUrl: {
    type: String,
    default: 'https://agent.imau.edu.cn:32300'
  }
})

const POSITION_STORAGE_KEY = 'hiagent_bubble_position'

function loadPosition() {
  try {
    const raw = localStorage.getItem(POSITION_STORAGE_KEY)
    if (raw) return JSON.parse(raw)
  } catch { /* localStorage 不可用 */ }
  return null
}

function savePosition(pos) {
  try {
    localStorage.setItem(POSITION_STORAGE_KEY, JSON.stringify(pos))
  } catch { /* localStorage 不可用 */ }
}

function applyPosition(el) {
  const saved = loadPosition()
  if (!saved) return
  el.style.left = saved.x + 'px'
  el.style.top = saved.y + 'px'
  el.style.right = 'auto'
  el.style.bottom = 'auto'
}

// ---- 拖拽（沿用 InteractiveText.vue 模式） ----
function attachDrag(el) {
  const startDrag = (event) => {
    if (activeDragHandlers) {
      document.removeEventListener('mousemove', activeDragHandlers.move)
      document.removeEventListener('mouseup', activeDragHandlers.end)
      document.removeEventListener('touchmove', activeDragHandlers.move)
      document.removeEventListener('touchend', activeDragHandlers.end)
      activeDragHandlers = null
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
      activeDragHandlers = null

      if (hasMoved) {
        const finalRect = el.getBoundingClientRect()
        savePosition({ x: finalRect.left, y: finalRect.top })
      }
      // 未移动且短按（< 300ms）→ 视为 SDK 自己的点击，不干预
    }

    document.addEventListener('mousemove', handleMove)
    document.addEventListener('mouseup', handleEnd)
    document.addEventListener('touchmove', handleMove, { passive: false })
    document.addEventListener('touchend', handleEnd)
    activeDragHandlers = { move: handleMove, end: handleEnd }
  }

  el.addEventListener('mousedown', startDrag)
  el.addEventListener('touchstart', startDrag, { passive: false })
}

// ---- 定位 SDK 创建的气泡元素 ----
function findAndAttachBubble() {
  // SDK（WebClient）初始化后会在 body 下创建 fixed 定位元素
  const candidates = document.querySelectorAll(
    '[class*="hiagent"], [class*="Hiagent"], [class*="web-client"], [class*="chat"], [class*="float"], [class*="bubble"]'
  )
  for (const el of candidates) {
    const style = window.getComputedStyle(el)
    if (style.position === 'fixed' && parseInt(style.zIndex) > 100) {
      bubbleEl = el
      applyPosition(el)
      attachDrag(el)
      return
    }
  }
}

function setupBubbleObserver() {
  // SDK 可能在稍后才创建 DOM，用 MutationObserver 兜底
  observer = new MutationObserver((mutations) => {
    for (const mutation of mutations) {
      for (const node of mutation.addedNodes) {
        if (node.nodeType !== 1) continue
        const el = node
        // 只观察 fixed 定位的高 z-index 元素
        const style = window.getComputedStyle(el)
        if (style.position === 'fixed' && parseInt(style.zIndex) > 100) {
          bubbleEl = el
          applyPosition(el)
          attachDrag(el)
          observer.disconnect()
          observer = null
          return
        }
        // 也可能是子树中有目标元素
        const children = el.querySelectorAll?.('[class*="hiagent"], [class*="Hiagent"], [class*="chat"], [class*="float"], [class*="bubble"], [class*="web-client"]')
        if (children?.length) {
          for (const child of children) {
            const cs = window.getComputedStyle(child)
            if (cs.position === 'fixed' && parseInt(cs.zIndex) > 100) {
              bubbleEl = child
              applyPosition(child)
              attachDrag(child)
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

// ---- SDK 初始化 ----
function initHiagent() {
  if (initialized) return
  if (window.HiagentWebSDK && window.HiagentWebSDK.WebClient) {
    try {
      hiagentClient = new window.HiagentWebSDK.WebClient({
        appKey: props.appKey,
        baseUrl: props.baseUrl
      })
      initialized = true
      console.log('HiAgent 问题反馈已初始化（WebClient）')

      // 等待 SDK 渲染 DOM，然后定位气泡
      requestAnimationFrame(() => {
        setTimeout(() => {
          findAndAttachBubble()
          // 如果还没找到，启动 MutationObserver 等待
          if (!bubbleEl) {
            setupBubbleObserver()
          }
        }, 500)
      })
    } catch (error) {
      console.error('HiAgent 初始化失败:', error)
    }
  } else {
    setTimeout(initHiagent, 100)
  }
}

onMounted(() => {
  initHiagent()
})

onBeforeUnmount(() => {
  // 清理拖拽监听
  if (activeDragHandlers) {
    document.removeEventListener('mousemove', activeDragHandlers.move)
    document.removeEventListener('mouseup', activeDragHandlers.end)
    document.removeEventListener('touchmove', activeDragHandlers.move)
    document.removeEventListener('touchend', activeDragHandlers.end)
    activeDragHandlers = null
  }
  // 清理 MutationObserver
  if (observer) {
    observer.disconnect()
    observer = null
  }
  // 销毁 SDK 实例
  if (hiagentClient && typeof hiagentClient.destroy === 'function') {
    try { hiagentClient.destroy() } catch { /* 静默 */ }
  }
  hiagentClient = null
  bubbleEl = null
  initialized = false
})
</script>

<style scoped>
/* HiAgent 气泡样式由 SDK 自动注入 */
</style>
