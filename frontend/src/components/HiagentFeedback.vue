<template>
  <!-- 移动端：自定义气泡，点击跳转 HiAgent 网页 -->
  <Teleport to="body">
    <button
      v-if="showMobileBubble"
      ref="bubbleRef"
      class="hiagent-bubble"
      :class="{ 'is-dragging': dragging }"
      type="button"
      @click="handleBubbleClick"
    >
      <span class="hiagent-bubble__text">问题反馈</span>
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

const FULL_PAGE_PATH = '/product/llm/chat'
const DESKTOP_MEDIA_QUERY = '(min-width: 768px)'
const POSITION_KEY_MOBILE = 'hiagent_bubble_position_mobile'
const POSITION_KEY_DESKTOP = 'hiagent_bubble_position_desktop'
const DRAG_THRESHOLD = 5
const BUBBLE_MARGIN = 16
const BOTTOM_NAV_HEIGHT = 70

let mediaQueryList = null
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

function getFullPageUrl() {
  return `${props.baseUrl}${FULL_PAGE_PATH}/${props.appKey}`
}

// ─── Bubble position ───────────────────────────────────

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

// ─── Unified bubble ────────────────────────────────────

function getMode() {
  return isDesktop() ? 'desktop' : 'mobile'
}

function getDefaultPosition(el, mode = getMode()) {
  const rect = el.getBoundingClientRect()
  const bottomOffset = mode === 'mobile' ? BOTTOM_NAV_HEIGHT : 0
  return clampPosition(
    el,
    window.innerWidth - rect.width - BUBBLE_MARGIN,
    window.innerHeight - rect.height - BUBBLE_MARGIN - bottomOffset
  )
}

function applyPosition(el, mode = getMode()) {
  const saved = loadPosition(mode)
  const pos = saved
    ? clampPosition(el, saved.x, saved.y)
    : getDefaultPosition(el, mode)
  setElementPosition(el, pos.x, pos.y)
}

function showBubble() {
  showMobileBubble.value = true

  nextTick(() => {
    const el = bubbleRef.value
    if (!mounted || !el) return

    applyPosition(el, getMode())
    attachDrag(el)
  })
}

// ─── Unified bubble drag ───────────────────────────────

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
      mode: getMode(),
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

function handleBubbleClick() {
  if (dragging.value || suppressNextClick) return
  window.open(getFullPageUrl(), '_blank')
}

function handleResize() {
  const el = bubbleRef.value
  if (!el || !showMobileBubble.value) return

  const mode = getMode()
  const saved = loadPosition(mode)
  if (saved) {
    const pos = clampPosition(el, saved.x, saved.y)
    setElementPosition(el, pos.x, pos.y)
    return
  }

  const pos = getDefaultPosition(el, mode)
  setElementPosition(el, pos.x, pos.y)
}

function handleModeChange() {
  if (!mounted) return
  showBubble()
}

// ─── Lifecycle ─────────────────────────────────────────

onMounted(() => {
  mounted = true
  showBubble()

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

  showMobileBubble.value = false
})
</script>

<style scoped>
.hiagent-bubble {
  position: fixed;
  right: 16px;
  bottom: calc(86px + env(safe-area-inset-bottom, 0px));
  width: 56px;
  height: 56px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 0;
  border: none;
  border-radius: 50%;
  background:
    radial-gradient(circle at 30% 30%, rgba(255, 255, 255, 0.42), rgba(255, 255, 255, 0) 38%),
    linear-gradient(135deg, #197fe6, #3b82f6);
  color: #fff;
  box-shadow: 0 12px 32px rgba(25, 127, 230, 0.28);
  font-size: 12px;
  font-weight: 600;
  line-height: 1.2;
  cursor: pointer;
  user-select: none;
  -webkit-user-select: none;
  text-align: center;
  transition: transform 0.2s ease, box-shadow 0.2s ease, filter 0.2s ease;
}

.hiagent-bubble:hover {
  transform: translateY(-2px) scale(1.05);
  box-shadow: 0 16px 36px rgba(25, 127, 230, 0.4);
  filter: brightness(1.08);
}

.hiagent-bubble::before {
  content: '';
  position: absolute;
  inset: -6px;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(59, 130, 246, 0.28) 0%, rgba(59, 130, 246, 0) 72%);
  opacity: 0;
  transform: scale(0.88);
  transition: opacity 0.2s ease, transform 0.2s ease;
  pointer-events: none;
}

.hiagent-bubble:hover::before {
  opacity: 1;
  transform: scale(1);
}

.hiagent-bubble.is-dragging {
  cursor: grabbing;
  transform: scale(1.03);
  box-shadow: 0 18px 38px rgba(25, 127, 230, 0.36);
}

.hiagent-bubble__text {
  position: relative;
  z-index: 1;
  width: 100%;
  padding: 0 6px;
  white-space: normal;
}
</style>
