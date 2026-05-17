<template>
  <Teleport to="body">
    <button
      v-if="showMobileBubble"
      ref="mobileBubbleRef"
      class="hiagent-mobile-bubble"
      :class="{ 'is-dragging': dragging }"
      type="button"
      @click="handleMobileBubbleClick"
    >
      <span class="hiagent-mobile-bubble__icon">AI</span>
      <span class="hiagent-mobile-bubble__label">问题反馈</span>
    </button>
  </Teleport>
</template>

<script setup>
import { nextTick, onBeforeUnmount, onMounted, ref } from 'vue'

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

const mobileBubbleRef = ref(null)
const showMobileBubble = ref(false)
const dragging = ref(false)

const LITE_SCRIPT_PATH = '/resources/product/llm/public/sdk/embedLite.js'
const FULL_SCRIPT_PATH = '/resources/product/llm/public/sdk/embedFull.js'
const FULL_PAGE_PATH = '/product/llm/chat'
const DESKTOP_MEDIA_QUERY = '(min-width: 768px)'
const POSITION_KEY_DESKTOP = 'hiagent_bubble_position_desktop'
const POSITION_KEY_MOBILE = 'hiagent_bubble_position_mobile'
const DRAG_THRESHOLD = 5
const DRAG_DURATION_THRESHOLD = 300
const MOBILE_BUBBLE_MARGIN = 16
const FULL_CLIENT_RENDER_TIMEOUT = 1200

let liteClient = null
let fullClient = null
let sdkEl = null
let observer = null
let resizeHandler = null
let mounted = false
let isDesktopMode = true
let suppressNextClick = false
let activePointerId = null
let activeDragElement = null
let releasePointerCaptureFn = null
let dragState = null

function isDesktop() {
  return window.matchMedia(DESKTOP_MEDIA_QUERY).matches
}

function getPositionKeyForMode(mode) {
  return mode === 'desktop' ? POSITION_KEY_DESKTOP : POSITION_KEY_MOBILE
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

function getFullPageUrl() {
  return `${props.baseUrl}${FULL_PAGE_PATH}/${props.appKey}`
}

function openFullPageUrl() {
  window.location.href = getFullPageUrl()
}

function loadPosition(mode) {
  try {
    const raw = localStorage.getItem(getPositionKeyForMode(mode))
    return raw ? JSON.parse(raw) : null
  } catch {
    return null
  }
}

function savePosition(mode, pos) {
  try {
    localStorage.setItem(getPositionKeyForMode(mode), JSON.stringify(pos))
  } catch {
    // ignore
  }
}

function clampPosition(el, x, y) {
  const rect = el.getBoundingClientRect()
  const maxX = Math.max(window.innerWidth - rect.width, 0)
  const maxY = Math.max(window.innerHeight - rect.height, 0)

  return {
    x: Math.min(Math.max(x, 0), maxX),
    y: Math.min(Math.max(y, 0), maxY)
  }
}

function setElementPosition(el, x, y) {
  const next = clampPosition(el, x, y)
  el.style.left = `${next.x}px`
  el.style.top = `${next.y}px`
  el.style.right = 'auto'
  el.style.bottom = 'auto'
}

function getDefaultPosition(el, mode) {
  const rect = el.getBoundingClientRect()
  const margin = mode === 'mobile' ? MOBILE_BUBBLE_MARGIN : 24
  return clampPosition(el, window.innerWidth - rect.width - margin, window.innerHeight - rect.height - margin)
}

function applyStoredOrDefaultPosition(el, mode) {
  const saved = loadPosition(mode)
  const position = saved ? clampPosition(el, saved.x, saved.y) : getDefaultPosition(el, mode)
  setElementPosition(el, position.x, position.y)
}

function clearPointerDragState() {
  dragging.value = false
  activePointerId = null
  activeDragElement = null
  releasePointerCaptureFn = null
  dragState = null
}

function finishPointerDrag(event) {
  if (!activeDragElement || !dragState) {
    clearPointerDragState()
    return
  }

  const el = activeDragElement
  const state = dragState
  const pointerId = activePointerId

  if (event && pointerId !== null && event.pointerId !== pointerId) {
    return
  }

  if (event) {
    const nextX = event.clientX - state.offsetX
    const nextY = event.clientY - state.offsetY
    setElementPosition(el, nextX, nextY)
  }

  const duration = Date.now() - state.startTime
  const didDrag = state.hasMoved || duration >= DRAG_DURATION_THRESHOLD
  if (didDrag) {
    const finalRect = el.getBoundingClientRect()
    savePosition(state.mode, { x: finalRect.left, y: finalRect.top })
    suppressNextClick = true
    window.setTimeout(() => {
      suppressNextClick = false
    }, 0)
  }

  el.style.transition = state.originalTransition

  if (releasePointerCaptureFn) {
    releasePointerCaptureFn()
  }

  clearPointerDragState()
}

function attachPointerDrag(el, mode) {
  if (!el || el.dataset.hiagentDragBound === 'true') return

  el.style.setProperty('z-index', '99999', 'important')
  el.style.setProperty('pointer-events', 'auto', 'important')
  el.style.setProperty('touch-action', 'none', 'important')

  const handlePointerDown = (event) => {
    if (event.button !== undefined && event.button !== 0) return
    if (activePointerId !== null) return

    const rect = el.getBoundingClientRect()
    activePointerId = event.pointerId
    activeDragElement = el
    dragging.value = true
    dragState = {
      mode,
      startX: event.clientX,
      startY: event.clientY,
      offsetX: event.clientX - rect.left,
      offsetY: event.clientY - rect.top,
      startTime: Date.now(),
      hasMoved: false,
      originalTransition: el.style.transition
    }

    el.style.transition = 'none'
    setElementPosition(el, rect.left, rect.top)

    if (typeof el.setPointerCapture === 'function') {
      el.setPointerCapture(event.pointerId)
      releasePointerCaptureFn = () => {
        try {
          if (el.hasPointerCapture?.(event.pointerId)) {
            el.releasePointerCapture(event.pointerId)
          }
        } catch {
          // ignore
        }
      }
    } else {
      releasePointerCaptureFn = null
    }
  }

  const handlePointerMove = (event) => {
    if (!dragState || event.pointerId !== activePointerId) return

    const deltaX = Math.abs(event.clientX - dragState.startX)
    const deltaY = Math.abs(event.clientY - dragState.startY)
    if (deltaX > DRAG_THRESHOLD || deltaY > DRAG_THRESHOLD) {
      dragState.hasMoved = true
    }

    setElementPosition(el, event.clientX - dragState.offsetX, event.clientY - dragState.offsetY)
  }

  const handlePointerUp = (event) => {
    finishPointerDrag(event)
  }

  const handlePointerCancel = (event) => {
    finishPointerDrag(event)
  }

  const handleLostPointerCapture = (event) => {
    finishPointerDrag(event)
  }

  const handleClick = (event) => {
    if (!suppressNextClick) return
    event.preventDefault()
    event.stopPropagation()
    suppressNextClick = false
  }

  el.addEventListener('pointerdown', handlePointerDown)
  el.addEventListener('pointermove', handlePointerMove)
  el.addEventListener('pointerup', handlePointerUp)
  el.addEventListener('pointercancel', handlePointerCancel)
  el.addEventListener('lostpointercapture', handleLostPointerCapture)
  el.addEventListener('click', handleClick, true)
  el.dataset.hiagentDragBound = 'true'
}

function isHiagentDesktopElement(el) {
  if (!el || el === mobileBubbleRef.value) return false
  if (el.id === 'app' || el.closest('#app')) return false

  const className = typeof el.className === 'string' ? el.className.toLowerCase() : ''
  const id = typeof el.id === 'string' ? el.id.toLowerCase() : ''
  const text = (el.textContent || '').trim().toLowerCase()

  if (id.includes('hiagent')) return true
  if (className.includes('hiagent')) return true
  if (className.includes('web-lite')) return true
  if (className.includes('web-client')) return true
  if (className.includes('embed-lite')) return true
  if (className.includes('embedfull')) return true
  if (text.includes('问题反馈') || text.includes('问问助手') || text.includes('hiagent')) return true

  return false
}

function findDesktopDragRoot(el) {
  let current = el
  let dragRoot = null

  while (current && current !== document.body) {
    if (current === mobileBubbleRef.value) return null
    if (current.id === 'app' || current.closest?.('#app')) return dragRoot

    const style = window.getComputedStyle(current)
    if (style.position === 'fixed') {
      dragRoot = current
    }

    current = current.parentElement
  }

  return dragRoot
}

function isUsableDesktopDragRoot(el) {
  if (!el) return false

  const rect = el.getBoundingClientRect()
  if (rect.width <= 0 || rect.height <= 0) return false

  return true
}

function findSdkElement() {
  const seen = new Set()
  const candidates = Array.from(document.body.querySelectorAll('*'))

  for (const el of candidates) {
    if (!isHiagentDesktopElement(el)) continue

    const dragRoot = findDesktopDragRoot(el)
    if (!isUsableDesktopDragRoot(dragRoot) || seen.has(dragRoot)) continue

    seen.add(dragRoot)
    sdkEl = dragRoot
    applyStoredOrDefaultPosition(dragRoot, 'desktop')
    attachPointerDrag(dragRoot, 'desktop')
    return dragRoot
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
  disconnectObserver()
  sdkEl = null
  if (activeDragElement && activeDragElement !== mobileBubbleRef.value) {
    finishPointerDrag()
  } else {
    clearPointerDragState()
  }
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

async function initDesktopBubble() {
  if (!mounted || !isDesktop() || liteClient) return

  try {
    const sdk = await loadSdkScript(LITE_SCRIPT_PATH)
    if (!mounted || !isDesktop() || liteClient) return

    if (!sdk?.WebLiteClient) {
      throw new Error('HiAgent WebLiteClient 不可用')
    }

    showMobileBubble.value = false
    destroyFullClient()
    resetSdkState()
    liteClient = new sdk.WebLiteClient({
      appKey: props.appKey,
      baseUrl: props.baseUrl
    })
    afterClientReady()
  } catch (error) {
    console.error('HiAgent 桌面气泡初始化失败:', error)
  }
}

async function openMobileFullClient() {
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

    window.setTimeout(() => {
      const hasRenderedFullClient = Array.from(document.body.querySelectorAll('*')).some((el) => {
        if (el === mobileBubbleRef.value) return false

        const className = typeof el.className === 'string' ? el.className.toLowerCase() : ''
        const id = typeof el.id === 'string' ? el.id.toLowerCase() : ''
        const text = (el.textContent || '').trim().toLowerCase()

        return (
          className.includes('hiagent') ||
          className.includes('web-client') ||
          className.includes('embedfull') ||
          id.includes('hiagent') ||
          text.includes('hiagent') ||
          text.includes('问题反馈') ||
          text.includes('问问助手')
        )
      })

      if (!hasRenderedFullClient) {
        openFullPageUrl()
      }
    }, FULL_CLIENT_RENDER_TIMEOUT)
  } catch (error) {
    console.error('HiAgent 移动端全页面初始化失败:', error)
    openFullPageUrl()
  }
}

function destroyMobileBubbleDrag() {
  if (activeDragElement === mobileBubbleRef.value) {
    finishPointerDrag()
  }
}

function setupMobileBubble() {
  showMobileBubble.value = true

  nextTick(() => {
    const el = mobileBubbleRef.value
    if (!mounted || !el || isDesktop()) return

    applyStoredOrDefaultPosition(el, 'mobile')
    attachPointerDrag(el, 'mobile')
  })
}

function syncMode() {
  isDesktopMode = isDesktop()

  if (isDesktopMode) {
    initDesktopBubble()
    return
  }

  destroyLiteClient()
  resetSdkState()
  setupMobileBubble()
}

function handleMobileBubbleClick() {
  if (dragging.value || suppressNextClick) return
  openMobileFullClient()
}

onMounted(() => {
  mounted = true
  syncMode()

  resizeHandler = () => {
    syncMode()
  }

  window.addEventListener('resize', resizeHandler)
})

onBeforeUnmount(() => {
  mounted = false

  if (resizeHandler) {
    window.removeEventListener('resize', resizeHandler)
    resizeHandler = null
  }

  destroyMobileBubbleDrag()
  destroyLiteClient()
  destroyFullClient()
  showMobileBubble.value = false
  resetSdkState()
})
</script>

<style scoped>
.hiagent-mobile-bubble {
  position: fixed;
  right: 16px;
  bottom: calc(86px + env(safe-area-inset-bottom, 0px));
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 12px 14px;
  border: none;
  border-radius: 999px;
  background: linear-gradient(135deg, #197fe6, #3b82f6);
  color: #fff;
  box-shadow: 0 12px 32px rgba(25, 127, 230, 0.28);
  font-size: 14px;
  font-weight: 600;
  line-height: 1;
  cursor: pointer;
  user-select: none;
  -webkit-user-select: none;
}

.hiagent-mobile-bubble.is-dragging {
  cursor: grabbing;
}

.hiagent-mobile-bubble__icon {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  background: rgba(255, 255, 255, 0.18);
  font-size: 12px;
  font-weight: 700;
}

.hiagent-mobile-bubble__label {
  white-space: nowrap;
}
</style>
