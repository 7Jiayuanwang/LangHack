<template>
  <!-- 移动端：自定义气泡，点击跳转 HiAgent 网页 -->
  <Teleport to="body">
    <button
      v-if="showMobileBubble"
      ref="bubbleRef"
      class="hiagent-bubble"
      :class="{ 'is-dragging': dragging }"
      type="button"
      @click="handleMobileBubbleClick"
    >
      <span class="hiagent-bubble__icon">AI</span>
      <span class="hiagent-bubble__label">问题反馈</span>
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

const bubbleRef = ref(null)
const showMobileBubble = ref(false)
const dragging = ref(false)

const LITE_SCRIPT_PATH = '/resources/product/llm/public/sdk/embedLite.js'
const FULL_PAGE_PATH = '/product/llm/chat'
const DESKTOP_MEDIA_QUERY = '(min-width: 768px)'
const POSITION_KEY_MOBILE = 'hiagent_bubble_position_mobile'
const POSITION_KEY_DESKTOP = 'hiagent_bubble_position_desktop'
const DRAG_THRESHOLD = 5
const BUBBLE_MARGIN = 16
const BOTTOM_NAV_HEIGHT = 70
const SDK_ELEMENT_POLL_DELAYS = [500, 1000, 2000, 4000]

let liteClient = null
let mediaQueryList = null
let sdkObserver = null
let mounted = false
let suppressNextClick = false
let activePointerId = null
let activeDragElement = null
let releasePointerCaptureFn = null
let dragState = null

// ─── Helpers ───────────────────────────────────────────

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

function getFullPageUrl() {
  return `${props.baseUrl}${FULL_PAGE_PATH}/${props.appKey}`
}

// ─── Mobile bubble position ────────────────────────────

function loadPosition(mode) {
  try {
    const key = mode === 'desktop' ? POSITION_KEY_DESKTOP : POSITION_KEY_MOBILE
    const raw = localStorage.getItem(key)
    return raw ? JSON.parse(raw) : null
  } catch {
    return null
  }
}

function savePosition(mode, pos) {
  try {
    const key = mode === 'desktop' ? POSITION_KEY_DESKTOP : POSITION_KEY_MOBILE
    localStorage.setItem(key, JSON.stringify(pos))
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
  const pos = clampPosition(el, x, y)
  el.style.left = `${pos.x}px`
  el.style.top = `${pos.y}px`
  el.style.right = 'auto'
  el.style.bottom = 'auto'
}

function getDefaultPosition(el) {
  const rect = el.getBoundingClientRect()
  return clampPosition(
    el,
    window.innerWidth - rect.width - BUBBLE_MARGIN,
    window.innerHeight - rect.height - BUBBLE_MARGIN - BOTTOM_NAV_HEIGHT
  )
}

function applyPosition(el, mode = 'mobile') {
  const saved = loadPosition(mode)
  const pos = saved
    ? clampPosition(el, saved.x, saved.y)
    : getDefaultPosition(el)
  setElementPosition(el, pos.x, pos.y)
}

// ─── Mobile bubble drag ────────────────────────────────

function clearDragState() {
  dragging.value = false
  activePointerId = null
  activeDragElement = null
  releasePointerCaptureFn = null
  dragState = null
}

function finishDrag(event) {
  if (!activeDragElement || !dragState) {
    clearDragState()
    return
  }

  const el = activeDragElement
  const state = dragState
  const pointerId = activePointerId

  if (event && pointerId !== null && event.pointerId !== pointerId) {
    return
  }

  if (event) {
    setElementPosition(el, event.clientX - state.offsetX, event.clientY - state.offsetY)
  }

  if (state.hasMoved) {
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

  clearDragState()
}

function attachDrag(el) {
  if (!el || el.dataset.hiagentDragBound === 'true') return

  el.style.setProperty('z-index', '99999', 'important')
  el.style.setProperty('pointer-events', 'auto', 'important')
  el.style.setProperty('touch-action', 'none', 'important')

  const onPointerDown = (event) => {
    if (event.button !== undefined && event.button !== 0) return
    if (activePointerId !== null) return

    const rect = el.getBoundingClientRect()
    activePointerId = event.pointerId
    activeDragElement = el
    dragging.value = true
    dragState = {
      mode: el === bubbleRef.value ? 'mobile' : 'desktop',
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

  const onPointerMove = (event) => {
    if (!dragState || event.pointerId !== activePointerId) return

    const deltaX = Math.abs(event.clientX - dragState.startX)
    const deltaY = Math.abs(event.clientY - dragState.startY)
    if (deltaX > DRAG_THRESHOLD || deltaY > DRAG_THRESHOLD) {
      dragState.hasMoved = true
    }

    setElementPosition(el, event.clientX - dragState.offsetX, event.clientY - dragState.offsetY)
  }

  const onPointerUp = (event) => finishDrag(event)
  const onPointerCancel = (event) => finishDrag(event)
  const onLostCapture = (event) => finishDrag(event)

  const onClickCapture = (event) => {
    if (!suppressNextClick) return
    event.preventDefault()
    event.stopPropagation()
    suppressNextClick = false
  }

  el.addEventListener('pointerdown', onPointerDown)
  el.addEventListener('pointermove', onPointerMove)
  el.addEventListener('pointerup', onPointerUp)
  el.addEventListener('pointercancel', onPointerCancel)
  el.addEventListener('lostpointercapture', onLostCapture)
  el.addEventListener('click', onClickCapture, true)
  el.dataset.hiagentDragBound = 'true'
}

// ─── Desktop: SDK WebLiteClient ────────────────────────
// 桌面端直接使用 HiAgent SDK 的悬浮窗气泡，由 SDK 自己渲染和管理
// 我们不显示自己的气泡，不劫持 SDK 的 DOM

function destroyLiteClient() {
  if (sdkObserver) {
    sdkObserver.disconnect()
    sdkObserver = null
  }
  // 清除 SDK 元素上的拖拽标记，以便重新初始化时能重新绑定
  const sdkEl = findSdkBubbleElement?.()
  if (sdkEl) {
    delete sdkEl.dataset.hiagentDragBound
  }
  if (liteClient && typeof liteClient.destroy === 'function') {
    try { liteClient.destroy() } catch { /* ignore */ }
  }
  liteClient = null
}

async function initDesktopSdk() {
  if (!mounted || liteClient) return

  try {
    const sdk = await loadSdkScript(LITE_SCRIPT_PATH)
    if (!mounted || liteClient) return

    if (!sdk?.WebLiteClient) return

    liteClient = new sdk.WebLiteClient({
      appKey: props.appKey,
      baseUrl: props.baseUrl
    })

    // SDK 渲染完成后，找到它的气泡元素并加上拖拽功能
    waitForSdkElement()
  } catch (error) {
    console.warn('HiAgent SDK 加载失败:', error)
  }
}

// 查找 SDK 渲染的气泡/容器元素
function findSdkBubbleElement() {
  const candidates = Array.from(document.body.querySelectorAll('*'))

  for (const el of candidates) {
    if (el === bubbleRef.value) continue
    if (el.id === 'app' || el.closest?.('#app')) continue

    const cn = typeof el.className === 'string' ? el.className.toLowerCase() : ''
    const id = typeof el.id === 'string' ? el.id.toLowerCase() : ''
    const text = (el.textContent || '').trim().toLowerCase()
    const style = window.getComputedStyle(el)

    if (style.position !== 'fixed') continue

    const rect = el.getBoundingClientRect()
    if (rect.width <= 0 || rect.height <= 0) continue

    const isViewportShell = rect.width >= window.innerWidth * 0.9 && rect.height >= window.innerHeight * 0.9
    if (isViewportShell) continue

    if (
      cn.includes('web-lite') ||
      cn.includes('embed-lite') ||
      cn.includes('hiagent') ||
      cn.includes('webchat') ||
      id.includes('hiagent') ||
      text.includes('问题反馈') ||
      text.includes('问问助手') ||
      text.includes('hiagent')
    ) {
      return el
    }
  }

  return null
}

// 等待 SDK 元素渲染完成，通过轮询 + MutationObserver 双保险
function waitForSdkElement() {
  // 先尝试轮询
  let pollIndex = 0
  function poll() {
    if (!mounted) return
    const el = findSdkBubbleElement()
    if (el) {
      makeSdkElementDraggable(el)
      return
    }
    if (pollIndex < SDK_ELEMENT_POLL_DELAYS.length) {
      window.setTimeout(poll, SDK_ELEMENT_POLL_DELAYS[pollIndex++])
    }
    // 轮询结束后 MutationObserver 继续兜底
  }
  poll()

  // MutationObserver 兜底：监听 body 子节点变化
  if (sdkObserver) {
    sdkObserver.disconnect()
    sdkObserver = null
  }
  sdkObserver = new MutationObserver(() => {
    if (!mounted) return
    const el = findSdkBubbleElement()
    if (el) {
      makeSdkElementDraggable(el)
      sdkObserver?.disconnect()
      sdkObserver = null
    }
  })
  sdkObserver.observe(document.body, { childList: true, subtree: false })
}

// 给 SDK 气泡元素加上拖拽功能
function makeSdkElementDraggable(el) {
  if (el.dataset.hiagentDragBound === 'true') return

  el.style.setProperty('position', 'fixed', 'important')
  el.style.setProperty('touch-action', 'none', 'important')
  el.style.setProperty('z-index', '99999', 'important')

  const saved = loadPosition('desktop')
  if (saved) {
    const pos = clampPosition(el, saved.x, saved.y)
    setElementPosition(el, pos.x, pos.y)
  }

  attachDrag(el)
}

// ─── Mobile bubble ─────────────────────────────────────
// 移动端显示自定义气泡，点击跳转到 HiAgent 网页

function setupMobileBubble() {
  showMobileBubble.value = true

  nextTick(() => {
    const el = bubbleRef.value
    if (!mounted || !el) return

    applyPosition(el, 'mobile')
    attachDrag(el)
  })
}

function handleMobileBubbleClick() {
  if (dragging.value || suppressNextClick) return
  // 移动端：直接打开 HiAgent 全页面
  window.open(getFullPageUrl(), '_blank')
}

function handleResize() {
  const el = bubbleRef.value
  if (!el || !showMobileBubble.value) return

  const saved = loadPosition('mobile')
  if (saved) {
    const pos = clampPosition(el, saved.x, saved.y)
    setElementPosition(el, pos.x, pos.y)
  }
}

// ─── 模式切换处理 ──────────────────────────────────────

function handleModeChange() {
  if (!mounted) return

  if (isDesktop()) {
    // 切换到桌面模式：隐藏自定义气泡，启用 SDK
    showMobileBubble.value = false
    if (!liteClient) {
      initDesktopSdk()
    }
  } else {
    // 切换到移动模式：销毁 SDK，显示自定义气泡
    destroyLiteClient()
    setupMobileBubble()
  }
}

// ─── Lifecycle ─────────────────────────────────────────

onMounted(() => {
  mounted = true

  if (isDesktop()) {
    // 桌面端：只用 SDK 的悬浮窗，不显示自定义气泡
    initDesktopSdk()
  } else {
    // 移动端：显示自定义气泡
    setupMobileBubble()
  }

  // 监听窗口大小变化，切换模式
  mediaQueryList = window.matchMedia(DESKTOP_MEDIA_QUERY)
  mediaQueryList.addEventListener('change', handleModeChange)
  window.addEventListener('resize', handleResize)
})

onBeforeUnmount(() => {
  mounted = false

  if (mediaQueryList) {
    mediaQueryList.removeEventListener('change', handleModeChange)
    mediaQueryList = null
  }

  window.removeEventListener('resize', handleResize)

  if (activeDragElement) {
    finishDrag()
  }

  destroyLiteClient()
  showMobileBubble.value = false
})
</script>

<style scoped>
.hiagent-bubble {
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

.hiagent-bubble.is-dragging {
  cursor: grabbing;
}

.hiagent-bubble__icon {
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

.hiagent-bubble__label {
  white-space: nowrap;
}
</style>
