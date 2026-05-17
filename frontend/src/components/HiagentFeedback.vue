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
const POSITION_KEY_DESKTOP = 'hiagent_bubble_position_desktop'
const POSITION_KEY_MOBILE = 'hiagent_bubble_position_mobile'
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
let activeMode = null

function isDesktop() {
  return window.matchMedia(DESKTOP_MEDIA_QUERY).matches
}

function getCurrentMode() {
  return isDesktop() ? 'desktop' : 'mobile'
}

function getPositionKey() {
  return getCurrentMode() === 'desktop' ? POSITION_KEY_DESKTOP : POSITION_KEY_MOBILE
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
    const raw = localStorage.getItem(getPositionKey())
    return raw ? JSON.parse(raw) : null
  } catch {
    return null
  }
}

function savePosition(pos) {
  try {
    localStorage.setItem(getPositionKey(), JSON.stringify(pos))
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

function getPoint(event) {
  if (event.type === 'touchend' || event.type === 'touchcancel') {
    const touch = event.changedTouches?.[0]
    return touch ? { x: touch.clientX, y: touch.clientY } : null
  }
  if (event.type === 'touchstart' || event.type === 'touchmove') {
    const touch = event.touches?.[0]
    return touch ? { x: touch.clientX, y: touch.clientY } : null
  }
  if (typeof event.clientX === 'number' && typeof event.clientY === 'number') {
    return { x: event.clientX, y: event.clientY }
  }
  return null
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
  el.style.setProperty('touch-action', 'none', 'important')

  const startDrag = (event) => {
    if (dragging) return
    if (dragHandlers) cleanupDrag()

    const isTouch = event.type === 'touchstart'
    if (!isTouch && event.button !== 0) return
    if (isTouch) event.preventDefault()

    const startPoint = getPoint(event)
    if (!startPoint) return

    dragging = true

    const startX = startPoint.x
    const startY = startPoint.y
    const startTime = Date.now()
    const rect = el.getBoundingClientRect()
    const offsetX = startX - rect.left
    const offsetY = startY - rect.top
    const originalTransition = el.style.transition
    let hasMoved = false

    el.style.transition = 'none'
    el.style.left = `${rect.left}px`
    el.style.top = `${rect.top}px`
    el.style.right = 'auto'
    el.style.bottom = 'auto'

    const updatePosition = (point) => {
      if (!point) return
      if (Math.abs(point.x - startX) > 5 || Math.abs(point.y - startY) > 5) {
        hasMoved = true
      }
      el.style.left = `${point.x - offsetX}px`
      el.style.top = `${point.y - offsetY}px`
    }

    const handleMove = (moveEvent) => {
      if (!dragging) return
      if (moveEvent.type === 'touchmove') {
        moveEvent.preventDefault()
      }
      updatePosition(getPoint(moveEvent))
    }

    const handleEnd = (endEvent) => {
      if (!dragging) return

      updatePosition(getPoint(endEvent))

      const duration = Date.now() - startTime
      dragging = false
      cleanupDrag()
      el.style.transition = originalTransition

      if (hasMoved || duration >= 300) {
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
    if (!mounted) return
    if (sdkEl && document.body.contains(sdkEl)) return

    const found = findSdkElement()
    if (found && observer) {
      observer.disconnect()
      observer = null
    }
  })

  observer.observe(document.body, { childList: true, subtree: true })
}

function afterClientReady() {
  requestAnimationFrame(() => {
    window.setTimeout(() => {
      if (!findSdkElement()) {
        observeSdkElement()
      }
    }, 400)
  })
}

function disconnectObserver() {
  if (!observer) return
  observer.disconnect()
  observer = null
}

function resetSdkState() {
  cleanupDrag()
  dragging = false
  disconnectObserver()
  sdkEl = null
}

function destroyLiteClient() {
  if (liteClient && typeof liteClient.destroy === 'function') {
    try {
      liteClient.destroy()
    } catch {
      // ignore
    }
  }
  liteClient = null
}

function destroyFullClient() {
  if (fullClient && typeof fullClient.destroy === 'function') {
    try {
      fullClient.destroy()
    } catch {
      // ignore
    }
  }
  fullClient = null
}

function destroyAllClients() {
  resetSdkState()
  destroyLiteClient()
  destroyFullClient()
  activeMode = null
}

async function initDesktopBubble() {
  if (!mounted || activeMode === 'desktop' || liteClient) return

  try {
    const sdk = await loadSdkScript(LITE_SCRIPT_PATH)
    if (!mounted || getCurrentMode() !== 'desktop' || activeMode === 'desktop' || liteClient) return

    if (!sdk?.WebLiteClient) {
      throw new Error('HiAgent WebLiteClient 不可用')
    }

    resetSdkState()
    destroyFullClient()

    liteClient = new sdk.WebLiteClient({
      appKey: props.appKey,
      baseUrl: props.baseUrl
    })
    activeMode = 'desktop'
    afterClientReady()
  } catch (error) {
    console.error('HiAgent 桌面气泡初始化失败:', error)
  }
}

async function initMobileBubble() {
  if (!mounted || activeMode === 'mobile' || fullClient) return

  try {
    const sdk = await loadSdkScript(FULL_SCRIPT_PATH)
    if (!mounted || getCurrentMode() !== 'mobile' || activeMode === 'mobile' || fullClient) return

    if (!sdk?.WebClient) {
      throw new Error('HiAgent WebClient 不可用')
    }

    resetSdkState()
    destroyLiteClient()

    fullClient = new sdk.WebClient({
      appKey: props.appKey,
      baseUrl: props.baseUrl
    })
    activeMode = 'mobile'
    afterClientReady()
  } catch (error) {
    console.error('HiAgent 手机端气泡初始化失败:', error)
  }
}

async function openFullClient() {
  await initMobileBubble()
  return fullClient
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
  const mode = getCurrentMode()
  if (mode === activeMode) return

  destroyAllClients()

  if (mode === 'desktop') {
    initDesktopBubble()
    return
  }

  initMobileBubble()
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
  destroyAllClients()
})
</script>

<style scoped>
</style>
