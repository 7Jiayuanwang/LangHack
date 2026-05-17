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

const LITE_SCRIPT_PATH = '/resources/product/llm/public/sdk/embedLite.js'
const FULL_SCRIPT_PATH = '/resources/product/llm/public/sdk/embedFull.js'
const DESKTOP_MEDIA_QUERY = '(min-width: 768px)'
const POSITION_KEY = 'hiagent_bubble_position'
const OPEN_FULL_METHOD = 'openHiagentFullClient'

let liteClient = null
let fullClient = null
let sdkEl = null
let observer = null
let dragHandlers = null
let dragging = false
let resizeHandler = null
let mounted = false
let mobileApiRegistered = false

function isDesktop() {
  return window.matchMedia(DESKTOP_MEDIA_QUERY).matches
}

function getScriptUrl(path) {
  return `${props.baseUrl}${path}`
}

function getGlobalLoadKey(path) {
  return `__hiagentScriptLoad__:${path}`
}

function loadSdkScript(path) {
  const loadKey = getGlobalLoadKey(path)
  if (window[loadKey]) return window[loadKey]

  const existing = document.querySelector(`script[src="${getScriptUrl(path)}"]`)
  if (existing) {
    window[loadKey] = new Promise((resolve, reject) => {
      if (window.HiagentWebSDK) {
        resolve(window.HiagentWebSDK)
        return
      }
      existing.addEventListener('load', () => resolve(window.HiagentWebSDK), { once: true })
      existing.addEventListener('error', reject, { once: true })
    })
    return window[loadKey]
  }

  window[loadKey] = new Promise((resolve, reject) => {
    const script = document.createElement('script')
    script.src = getScriptUrl(path)
    script.async = true
    script.onload = () => resolve(window.HiagentWebSDK)
    script.onerror = reject
    document.body.appendChild(script)
  })

  return window[loadKey]
}

function loadPosition() {
  try {
    const raw = localStorage.getItem(POSITION_KEY)
    return raw ? JSON.parse(raw) : null
  } catch {
    return null
  }
}

function savePosition(pos) {
  try {
    localStorage.setItem(POSITION_KEY, JSON.stringify(pos))
  } catch {
    // ignore
  }
}

function applyPosition(el) {
  const saved = loadPosition()
  if (!saved) return
  el.style.left = `${saved.x}px`
  el.style.top = `${saved.y}px`
  el.style.right = 'auto'
  el.style.bottom = 'auto'
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

function attachDrag(el) {
  if (el.dataset.hiagentDragBound === 'true') return

  el.style.setProperty('z-index', '99999', 'important')
  el.style.setProperty('pointer-events', 'auto', 'important')

  const startDrag = (event) => {
    if (!isDesktop() || dragging) return
    if (dragHandlers) cleanupDrag()

    const isTouch = event.type === 'touchstart'
    if (!isTouch && event.button !== 0) return
    if (isTouch) event.preventDefault()

    dragging = true

    const startX = isTouch ? event.touches[0].clientX : event.clientX
    const startY = isTouch ? event.touches[0].clientY : event.clientY
    const rect = el.getBoundingClientRect()
    const offsetX = startX - rect.left
    const offsetY = startY - rect.top
    const originalTransition = el.style.transition
    let moved = false

    el.style.transition = 'none'
    el.style.left = `${rect.left}px`
    el.style.top = `${rect.top}px`
    el.style.right = 'auto'
    el.style.bottom = 'auto'

    const handleMove = (moveEvent) => {
      if (!dragging) return
      const moveX = moveEvent.type === 'touchmove' ? moveEvent.touches[0].clientX : moveEvent.clientX
      const moveY = moveEvent.type === 'touchmove' ? moveEvent.touches[0].clientY : moveEvent.clientY

      if (Math.abs(moveX - startX) > 5 || Math.abs(moveY - startY) > 5) {
        moved = true
      }

      el.style.left = `${moveX - offsetX}px`
      el.style.top = `${moveY - offsetY}px`
    }

    const handleEnd = () => {
      if (!dragging) return
      dragging = false
      cleanupDrag()
      el.style.transition = originalTransition

      if (moved) {
        const finalRect = el.getBoundingClientRect()
        savePosition({ x: finalRect.left, y: finalRect.top })
      }
    }

    document.addEventListener('mousemove', handleMove)
    document.addEventListener('mouseup', handleEnd)
    document.addEventListener('touchmove', handleMove, { passive: false })
    document.addEventListener('touchend', handleEnd)
    document.addEventListener('touchcancel', handleEnd)
    dragHandlers = { move: handleMove, end: handleEnd }
  }

  el.addEventListener('mousedown', startDrag)
  el.addEventListener('touchstart', startDrag, { passive: false })
  el.dataset.hiagentDragBound = 'true'
}

function findSdkElement() {
  const candidates = document.querySelectorAll(
    '[class*="hiagent"], [class*="Hiagent"], [class*="web-client"], [class*="chat"], [class*="float"], [class*="bubble"]'
  )

  for (const el of candidates) {
    if (el.id === 'app' || el.closest('#app')) continue
    const style = window.getComputedStyle(el)
    if (style.position !== 'fixed') continue

    sdkEl = el
    applyPosition(el)
    attachDrag(el)
    return el
  }

  return null
}

function observeSdkElement() {
  if (observer) return

  observer = new MutationObserver(() => {
    if (!mounted || !isDesktop()) return
    if (sdkEl && document.body.contains(sdkEl)) return

    const found = findSdkElement()
    if (found && observer) {
      observer.disconnect()
      observer = null
    }
  })

  observer.observe(document.body, { childList: true, subtree: true })
}

function destroyLiteClient() {
  cleanupDrag()
  dragging = false

  if (observer) {
    observer.disconnect()
    observer = null
  }

  if (liteClient && typeof liteClient.destroy === 'function') {
    try {
      liteClient.destroy()
    } catch {
      // ignore
    }
  }

  liteClient = null
  sdkEl = null
}

async function initDesktopBubble() {
  if (!mounted || !isDesktop() || liteClient) return

  try {
    const sdk = await loadSdkScript(LITE_SCRIPT_PATH)
    if (!mounted || !isDesktop() || liteClient) return

    if (!sdk?.WebLiteClient) {
      throw new Error('HiAgent WebLiteClient 不可用')
    }

    liteClient = new sdk.WebLiteClient({
      appKey: props.appKey,
      baseUrl: props.baseUrl
    })

    requestAnimationFrame(() => {
      window.setTimeout(() => {
        if (!findSdkElement()) {
          observeSdkElement()
        }
      }, 400)
    })
  } catch (error) {
    console.error('HiAgent 桌面气泡初始化失败:', error)
  }
}

async function openFullClient() {
  try {
    const sdk = await loadSdkScript(FULL_SCRIPT_PATH)
    if (!sdk?.WebClient) {
      throw new Error('HiAgent WebClient 不可用')
    }

    if (!fullClient) {
      fullClient = new sdk.WebClient({
        appKey: props.appKey,
        baseUrl: props.baseUrl
      })
    }

    return fullClient
  } catch (error) {
    console.error('HiAgent 手机端全量客户端初始化失败:', error)
    throw error
  }
}

function registerMobileApi() {
  if (mobileApiRegistered) return
  window[OPEN_FULL_METHOD] = openFullClient
  mobileApiRegistered = true
}

function unregisterMobileApi() {
  if (!mobileApiRegistered) return
  if (window[OPEN_FULL_METHOD] === openFullClient) {
    delete window[OPEN_FULL_METHOD]
  }
  mobileApiRegistered = false
}

function handleViewportChange() {
  if (isDesktop()) {
    initDesktopBubble()
    return
  }

  destroyLiteClient()
}

defineExpose({
  openFullClient
})

onMounted(() => {
  mounted = true
  registerMobileApi()
  handleViewportChange()

  resizeHandler = () => {
    handleViewportChange()
  }

  window.addEventListener('resize', resizeHandler)
})

onBeforeUnmount(() => {
  mounted = false

  if (resizeHandler) {
    window.removeEventListener('resize', resizeHandler)
    resizeHandler = null
  }

  unregisterMobileApi()
  destroyLiteClient()
})
</script>

<style scoped>
</style>
