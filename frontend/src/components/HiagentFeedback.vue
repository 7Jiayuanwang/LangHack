<template>
  <Teleport to="body">
    <button
      v-if="showBubble"
      ref="bubbleRef"
      class="hiagent-bubble"
      :class="{ 'is-dragging': dragging }"
      type="button"
      @click="handleBubbleClick"
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
const showBubble = ref(false)
const dragging = ref(false)

const LITE_SCRIPT_PATH = '/resources/product/llm/public/sdk/embedLite.js'
const FULL_SCRIPT_PATH = '/resources/product/llm/public/sdk/embedFull.js'
const FULL_PAGE_PATH = '/product/llm/chat'
const DESKTOP_MEDIA_QUERY = '(min-width: 768px)'
const POSITION_KEY = 'hiagent_bubble_position'
const DRAG_THRESHOLD = 5
const DRAG_DURATION_THRESHOLD = 300
const BUBBLE_MARGIN = 16
const BOTTOM_NAV_HEIGHT = 70
const FULL_CLIENT_RENDER_TIMEOUT = 1200
const SDK_HIDE_STYLE_ID = 'hiagent-sdk-hide'

let liteClient = null
let fullClient = null
let resizeHandler = null
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

// ─── Position ──────────────────────────────────────────

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

function applyPosition(el) {
  const saved = loadPosition()
  const pos = saved
    ? clampPosition(el, saved.x, saved.y)
    : getDefaultPosition(el)
  setElementPosition(el, pos.x, pos.y)
}

// ─── Drag ──────────────────────────────────────────────

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

  const duration = Date.now() - state.startTime
  const didDrag = state.hasMoved || duration >= DRAG_DURATION_THRESHOLD
  if (didDrag) {
    const finalRect = el.getBoundingClientRect()
    savePosition({ x: finalRect.left, y: finalRect.top })
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

// ─── SDK bubble hiding ─────────────────────────────────
// WebLiteClient 会渲染自己的气泡，我们注入 CSS 隐藏它

function injectSdkHideStyle() {
  if (document.getElementById(SDK_HIDE_STYLE_ID)) return
  const style = document.createElement('style')
  style.id = SDK_HIDE_STYLE_ID
  style.textContent = `
    /* 隐藏 HiAgent SDK 自带的浮动气泡，我们使用自己的可拖拽气泡 */
    body > div[class*="web-lite"],
    body > div[class*="embed-lite"],
    body > div[class*="hiagent"][style*="fixed"] {
      opacity: 0 !important;
      pointer-events: none !important;
      width: 0 !important;
      height: 0 !important;
      overflow: hidden !important;
    }
  `
  document.head.appendChild(style)
}

function removeSdkHideStyle() {
  const el = document.getElementById(SDK_HIDE_STYLE_ID)
  if (el) el.remove()
}

// 查找 SDK 渲染的气泡元素（用于代理点击以打开聊天面板）
function findSdkClickTarget() {
  const candidates = Array.from(document.body.querySelectorAll('*'))
  for (const el of candidates) {
    if (el === bubbleRef.value) continue
    if (el.id === 'app' || el.closest('#app')) continue

    const cn = typeof el.className === 'string' ? el.className.toLowerCase() : ''
    const id = typeof el.id === 'string' ? el.id.toLowerCase() : ''

    if (
      cn.includes('web-lite') ||
      cn.includes('embed-lite') ||
      id.includes('hiagent')
    ) {
      return el
    }
  }
  return null
}

// ─── SDK init ──────────────────────────────────────────

function destroyLiteClient() {
  if (liteClient && typeof liteClient.destroy === 'function') {
    try { liteClient.destroy() } catch { /* ignore */ }
  }
  liteClient = null
}

function destroyFullClient() {
  if (fullClient && typeof fullClient.destroy === 'function') {
    try { fullClient.destroy() } catch { /* ignore */ }
  }
  fullClient = null
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

    // 等 SDK 渲染完再隐藏它的气泡
    window.setTimeout(() => {
      if (mounted) injectSdkHideStyle()
    }, 800)
  } catch (error) {
    console.warn('HiAgent SDK 加载失败，点击气泡将打开全页面:', error)
  }
}

async function openFullClient() {
  try {
    const sdk = await loadSdkScript(FULL_SCRIPT_PATH)
    if (!sdk?.WebClient) throw new Error('WebClient 不可用')

    if (!fullClient) {
      fullClient = new sdk.WebClient({
        appKey: props.appKey,
        baseUrl: props.baseUrl
      })
    }

    // 检查 SDK 是否成功渲染了全屏聊天界面
    window.setTimeout(() => {
      const rendered = Array.from(document.body.querySelectorAll('*')).some((el) => {
        if (el === bubbleRef.value) return false
        const cn = typeof el.className === 'string' ? el.className.toLowerCase() : ''
        return cn.includes('web-client') || cn.includes('embedfull')
      })

      if (!rendered) {
        window.open(getFullPageUrl(), '_blank')
      }
    }, FULL_CLIENT_RENDER_TIMEOUT)
  } catch {
    window.open(getFullPageUrl(), '_blank')
  }
}

// ─── Bubble setup ──────────────────────────────────────

function setupBubble() {
  showBubble.value = true

  nextTick(() => {
    const el = bubbleRef.value
    if (!mounted || !el) return

    applyPosition(el)
    attachDrag(el)
  })
}

function handleBubbleClick() {
  if (dragging.value || suppressNextClick) return

  if (isDesktop()) {
    // 桌面端：尝试代理点击 SDK 气泡以打开聊天面板
    // 先临时取消隐藏让 SDK 能接收点击
    removeSdkHideStyle()
    const sdkTarget = findSdkClickTarget()
    if (sdkTarget) {
      sdkTarget.dispatchEvent(new MouseEvent('click', { bubbles: true, cancelable: true }))
      // 延迟重新隐藏 SDK 气泡
      window.setTimeout(() => {
        if (mounted) injectSdkHideStyle()
      }, 300)
    } else {
      // SDK 未加载成功，打开全页面
      injectSdkHideStyle()
      window.open(getFullPageUrl(), '_blank')
    }
  } else {
    // 移动端：尝试嵌入全屏聊天，失败则打开全页面
    openFullClient()
  }
}

function handleResize() {
  const el = bubbleRef.value
  if (!el || !showBubble.value) return

  // 窗口大小变化时重新约束位置
  const saved = loadPosition()
  if (saved) {
    const pos = clampPosition(el, saved.x, saved.y)
    setElementPosition(el, pos.x, pos.y)
  }
}

// ─── Lifecycle ─────────────────────────────────────────

onMounted(() => {
  mounted = true

  // 桌面端加载 SDK（后台），但统一用我们自己的气泡
  if (isDesktop()) {
    initDesktopSdk()
  }

  setupBubble()

  resizeHandler = handleResize
  window.addEventListener('resize', resizeHandler)
})

onBeforeUnmount(() => {
  mounted = false

  if (resizeHandler) {
    window.removeEventListener('resize', resizeHandler)
    resizeHandler = null
  }

  if (activeDragElement) {
    finishDrag()
  }

  destroyLiteClient()
  destroyFullClient()
  removeSdkHideStyle()
  showBubble.value = false
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
