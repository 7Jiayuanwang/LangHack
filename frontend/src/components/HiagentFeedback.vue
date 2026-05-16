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

// ---- 拖拽（沿用 InteractiveText.vue 模式） ----
function attachDrag(el) {
  const startDrag = (event) => {
    if (dragHandlers) {
      document.removeEventListener('mousemove', dragHandlers.move)
      document.removeEventListener('mouseup', dragHandlers.end)
      document.removeEventListener('touchmove', dragHandlers.move)
      document.removeEventListener('touchend', dragHandlers.end)
      dragHandlers = null
    }

    const isTouch = event.type === 'touchstart'
    if (!isTouch && event.button !== 0) return

    const startX = isTouch ? event.touches[0].clientX : event.clientX
    const startY = isTouch ? event.touches[0].clientY : event.clientY
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
      dragHandlers = null

      if (hasMoved) {
        const finalRect = el.getBoundingClientRect()
        savePosition({ x: finalRect.left, y: finalRect.top })
      }
      // 未移动 → SDK 自己的点击行为，放行
    }

    document.addEventListener('mousemove', handleMove)
    document.addEventListener('mouseup', handleEnd)
    document.addEventListener('touchmove', handleMove, { passive: false })
    document.addEventListener('touchend', handleEnd)
    dragHandlers = { move: handleMove, end: handleEnd }
  }

  el.addEventListener('mousedown', startDrag)
  el.addEventListener('touchstart', startDrag, { passive: false })
}

// ---- 定位 SDK 创建的浮动元素 ----
function findSdkElement() {
  const candidates = document.querySelectorAll(
    '[class*="hiagent"], [class*="Hiagent"], [class*="web-client"], [class*="chat"], [class*="float"], [class*="bubble"]'
  )
  for (const el of candidates) {
    const style = window.getComputedStyle(el)
    if (style.position === 'fixed' && parseInt(style.zIndex) > 100) {
      sdkEl = el
      applyPosition(el)
      attachDrag(el)
      return
    }
  }
}

function setupObserver() {
  observer = new MutationObserver((mutations) => {
    for (const mutation of mutations) {
      for (const node of mutation.addedNodes) {
        if (node.nodeType !== 1) continue
        const el = node
        const style = window.getComputedStyle(el)
        if (style.position === 'fixed' && parseInt(style.zIndex) > 100) {
          sdkEl = el
          applyPosition(el)
          attachDrag(el)
          observer.disconnect()
          observer = null
          return
        }
        const children = el.querySelectorAll?.('[class*="hiagent"], [class*="Hiagent"], [class*="chat"], [class*="float"], [class*="bubble"], [class*="web-client"]')
        if (children?.length) {
          for (const child of children) {
            const cs = window.getComputedStyle(child)
            if (cs.position === 'fixed' && parseInt(cs.zIndex) > 100) {
              sdkEl = child
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

function waitAndAttach() {
  requestAnimationFrame(() => {
    setTimeout(() => {
      findSdkElement()
      if (!sdkEl) setupObserver()
    }, 500)
  })
}

// ---- SDK 初始化 ----
function getClientClass() {
  if (isMobile.value) {
    // 手机端：完整嵌入的 WebClient
    return { name: 'WebClient', label: 'WebClient（手机端）' }
  }
  // 电脑端：轻量浮窗 WebLiteClient
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
  if (dragHandlers) {
    document.removeEventListener('mousemove', dragHandlers.move)
    document.removeEventListener('mouseup', dragHandlers.end)
    document.removeEventListener('touchmove', dragHandlers.move)
    document.removeEventListener('touchend', dragHandlers.end)
    dragHandlers = null
  }
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

  // 销毁旧的，重新初始化
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
