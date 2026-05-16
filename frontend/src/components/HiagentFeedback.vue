<template>
  <Teleport to="body">
    <div ref="containerRef"></div>
  </Teleport>
</template>

<script setup>
import { onMounted, onBeforeUnmount, ref } from 'vue'

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

const containerRef = ref(null)

// ---- 设备检测 ----
const isMobile = ref(false)
function checkMobile() {
  isMobile.value = window.innerWidth < 400 && window.innerHeight < 1000
}

// ---- SDK 实例 & DOM 引用 ----
let client = null
let initialized = false
let sdkEl = null
let observer = null
let dragHandlers = null
let dragging = false
const POSITION_KEY = 'hiagent_bubble_position'

// ---- 位置持久化 ----
function loadPosition() {
  try {
    const raw = localStorage.getItem(POSITION_KEY)
    if (raw) return JSON.parse(raw)
  } catch { /* ignore */ }
  return null
}

function savePosition(pos) {
  try {
    localStorage.setItem(POSITION_KEY, JSON.stringify(pos))
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

// ---- 拖拽 ----
function attachDrag(el) {
  // 置顶，确保在所有页面都能点击和拖动
  el.style.setProperty('z-index', '99999', 'important')
  el.style.setProperty('pointer-events', 'auto', 'important')

  const startDrag = (event) => {
    if (dragging) return
    if (dragHandlers) cleanupDrag()

    const isTouch = event.type === 'touchstart'
    if (!isTouch && event.button !== 0) return

    // 阻止 touch → mouse 模拟事件，避免双重触发
    if (isTouch) event.preventDefault()

    dragging = true

    const startX = isTouch ? event.touches[0].clientX : event.clientX
    const startY = isTouch ? event.touches[0].clientY : event.clientY
    const rect = el.getBoundingClientRect()
    let hasMoved = false

    const offsetX = startX - rect.left
    const offsetY = startY - rect.top

    // 禁用 SDK 元素上的 CSS transition，避免拖拽时位置回弹
    const origTransition = el.style.transition
    el.style.transition = 'none'

    // 强制使用 left/top 定位
    el.style.left = rect.left + 'px'
    el.style.top = rect.top + 'px'
    el.style.right = 'auto'
    el.style.bottom = 'auto'

    const handleMove = (e) => {
      if (!dragging) return
      const moveX = e.type === 'touchmove' ? e.touches[0].clientX : e.clientX
      const moveY = e.type === 'touchmove' ? e.touches[0].clientY : e.clientY

      const deltaX = Math.abs(moveX - startX)
      const deltaY = Math.abs(moveY - startY)
      if (deltaX > 5 || deltaY > 5) hasMoved = true

      el.style.left = (moveX - offsetX) + 'px'
      el.style.top = (moveY - offsetY) + 'px'
    }

    const handleEnd = () => {
      if (!dragging) return
      dragging = false
      cleanupDrag()

      // 恢复 transition
      el.style.transition = origTransition

      if (hasMoved) {
        const finalRect = el.getBoundingClientRect()
        savePosition({ x: finalRect.left, y: finalRect.top })
      }
    }

    document.addEventListener('mousemove', handleMove)
    document.addEventListener('mouseup', handleEnd)
    document.addEventListener('touchmove', handleMove, { passive: false })
    document.addEventListener('touchend', handleEnd)
    // touchcancel 也要处理（手势被系统中断等场景）
    document.addEventListener('touchcancel', handleEnd)

    dragHandlers = { move: handleMove, end: handleEnd }
  }

  el.addEventListener('mousedown', startDrag)
  el.addEventListener('touchstart', startDrag, { passive: false })
}

function cleanupDrag() {
  if (!dragHandlers) return
  document.removeEventListener('mousemove', dragHandlers.move)
  document.removeEventListener('mouseup', dragHandlers.end)
  document.removeEventListener('touchmove', dragHandlers.move)
  document.removeEventListener('touchend', dragHandlers.end)
  document.removeEventListener('touchcancel', dragHandlers.end)
  dragHandlers = null
}

// ---- 定位 SDK 创建的浮动元素 ----
function findSdkElement() {
  const candidates = document.querySelectorAll(
    '[class*="hiagent"], [class*="Hiagent"], [class*="web-client"], [class*="chat"], [class*="float"], [class*="bubble"]'
  )
  for (const el of candidates) {
    if (el.id === 'app' || el.closest('#app')) continue // 跳过 Vue app 内元素
    const style = window.getComputedStyle(el)
    if (style.position === 'fixed' && parseInt(style.zIndex) > 0) {
      sdkEl = el
      applyPosition(el)
      attachDrag(el)
      return
    }
  }
}

function setupObserver() {
  observer = new MutationObserver(() => {
    if (sdkEl && document.body.contains(sdkEl)) return // 已找到且未被移除
    findSdkElement()
    if (sdkEl && observer) {
      observer.disconnect()
      observer = null
    }
  })
  observer.observe(document.body, { childList: true, subtree: true })
}

function waitAndAttach() {
  requestAnimationFrame(() => {
    setTimeout(() => {
      findSdkElement()
      if (!sdkEl) setupObserver()
    }, 800)
  })
}

// ---- SDK 初始化 ----
function getClientClass() {
  if (isMobile.value) {
    return { name: 'WebClient', label: 'WebClient（手机端）' }
  }
  return { name: 'WebLiteClient', label: 'WebLiteClient（电脑端）' }
}

function initSdk() {
  if (initialized) return
  const { name, label } = getClientClass()
  if (window.HiagentWebSDK && window.HiagentWebSDK[name]) {
    try {
      client = new window.HiagentWebSDK[name]({
        appKey: props.appKey,
        baseUrl: props.baseUrl
      })
      initialized = true
      console.log(`HiAgent ${label} 已初始化`)
      waitAndAttach()
    } catch (error) {
      console.error('HiAgent 初始化失败:', error)
    }
  } else {
    setTimeout(initSdk, 100)
  }
}

// ---- 清理 ----
function destroySdk() {
  cleanupDrag()
  dragging = false
  if (observer) {
    observer.disconnect()
    observer = null
  }
  if (client && typeof client.destroy === 'function') {
    try { client.destroy() } catch { /* ignore */ }
  }
  client = null
  sdkEl = null
  initialized = false
}

// ---- 模式切换 ----
let prevMobile = null
function handleResize() {
  checkMobile()
  if (prevMobile === isMobile.value) return
  prevMobile = isMobile.value
  destroySdk()
  initSdk()
}

// ---- 生命周期 ----
onMounted(() => {
  checkMobile()
  prevMobile = isMobile.value
  initSdk()
  window.addEventListener('resize', handleResize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
  destroySdk()
})
</script>

<style scoped>
/* HiAgent 气泡样式由 SDK 自动注入 */
</style>
