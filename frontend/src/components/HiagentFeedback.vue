<template>
  <Teleport to="body">
    <div ref="containerRef">
      <button
        v-if="isMobileMode"
        ref="mobileBubbleRef"
        class="hiagent-mobile-bubble"
        :class="{ 'is-dragging': dragging }"
        type="button"
        @click="handleMobileBubbleClick"
      >
        <span class="hiagent-mobile-bubble__icon">AI</span>
        <span class="hiagent-mobile-bubble__label">问问助手</span>
      </button>
    </div>
  </Teleport>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'

const props = defineProps({
  appKey: {
    type: String,
    default: 'd83urt96rdh3v9agk58g'
  },
  baseUrl: {
    type: String,
    default: 'https://agent.imau.edu.cn:32300'
  },
  mobileTargetRoute: {
    type: String,
    required: true
  }
})

const router = useRouter()
const containerRef = ref(null)
const mobileBubbleRef = ref(null)

const LITE_SCRIPT_PATH = '/resources/product/llm/public/sdk/embedLite.js'
const DESKTOP_MEDIA_QUERY = '(min-width: 768px)'
const POSITION_KEY_DESKTOP = 'hiagent_bubble_position_desktop'
const POSITION_KEY_MOBILE = 'hiagent_bubble_position_mobile'
const DRAG_THRESHOLD = 5
const DRAG_DURATION_THRESHOLD = 300

let liteClient = null
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

const isMobileMode = computed(() => mounted && !isDesktopMode)
const dragging = ref(false)

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
  const margin = mode === 'mobile' ? 16 : 24
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

function findSdkElement() {
  const candidates = document.querySelectorAll(
    '[class*="hiagent"], [class*="Hiagent"], [class*="web-client"], [class*="chat"], [class*="float"], [class*="bubble"]'
  )

  for (const el of candidates) {
    if (el === mobileBubbleRef.value || el.id === 'app' || el.closest('#app')) continue
    const style = window.getComputedStyle(el)
    if (style.position !== 'fixed') continue

    sdkEl = el
    applyStoredOrDefaultPosition(el, 'desktop')
    attachPointerDrag(el, 'desktop')
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

async function initDesktopBubble() {
  if (!mounted || !isDesktop() || liteClient) return

  try {
    const sdk = await loadSdkScript(LITE_SCRIPT_PATH)
    if (!mounted || !isDesktop() || liteClient) return

    if (!sdk?.WebLiteClient) {
      throw new Error('HiAgent WebLiteClient 不可用')
    }

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

function destroyMobileBubbleDrag() {
  if (activeDragElement === mobileBubbleRef.value) {
    finishPointerDrag()
  }
}

function setupMobileBubble() {
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
  router.push(props.mobileTargetRoute)
}

watch(isMobileMode, (value) => {
  if (value) {
    setupMobileBubble()
  }
})

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
  resetSdkState()
})
</script>

<style scoped>
.hiagent-mobile-bubble {
  position: fixed;
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
