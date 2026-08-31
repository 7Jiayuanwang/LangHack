<template>
  <div
    v-if="visible"
    ref="menuRef"
    class="selection-menu"
    :style="menuStyle"
    @mousedown.prevent.stop
    @touchstart.stop
  >
    <button class="menu-item" type="button" @click.stop="$emit('mark')">
      <el-icon><EditPen /></el-icon>
      {{ markLabel }}
    </button>
    <button
      class="more-trigger"
      type="button"
      title="更多操作"
      :aria-expanded="expanded"
      @click.stop="expanded = !expanded"
    >
      <span class="triangle" :class="{ open: expanded }" aria-hidden="true"></span>
    </button>
    <template v-if="expanded">
      <button class="menu-item" type="button" @click.stop="$emit('copy')">复制</button>
      <button class="menu-item" type="button" @click.stop="$emit('translate')">翻译</button>
      <button class="menu-item" type="button" @click.stop="$emit('favorite-sentence')">收藏例句</button>
      <button class="menu-item" type="button" @click.stop="$emit('ai-qa')">AI 问答</button>
    </template>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { EditPen } from '@element-plus/icons-vue'

const props = defineProps({
  visible: { type: Boolean, required: true },
  position: { type: Object, default: () => ({ x: 0, y: 0 }) },
  selectionText: { type: String, default: '' },
  markLabel: { type: String, default: '标注' }
})

defineEmits(['copy', 'mark', 'translate', 'favorite-sentence', 'ai-qa'])

const expanded = ref(false)
const menuRef = ref(null)
const collapse = () => { expanded.value = false }

const handleOutsidePointer = (event) => {
  if (!menuRef.value?.contains(event.target)) collapse()
}

onMounted(() => document.addEventListener('pointerdown', handleOutsidePointer))
onMounted(() => document.addEventListener('selectionchange', collapse))
onBeforeUnmount(() => {
  document.removeEventListener('pointerdown', handleOutsidePointer)
  document.removeEventListener('selectionchange', collapse)
})

// A new selection can keep the menu visible, so visibility alone is not enough
// to reset the secondary actions.
watch(
  () => [props.visible, props.position?.x, props.position?.y, props.selectionText],
  (next, previous) => {
    if (!next[0] || (previous && next.some((value, index) => value !== previous[index]))) collapse()
  }
)

const menuStyle = computed(() => {
  const viewportWidth = typeof window !== 'undefined' ? window.innerWidth : 375
  const x = Math.min(Math.max(props.position?.x ?? 0, 118), viewportWidth - 118)
  const y = props.position?.y ?? 0

  if (viewportWidth <= 768) {
    return {
      left: `${x}px`,
      bottom: `calc(100vh - ${y - 12}px)`,
      transform: 'translateX(-50%)'
    }
  }

  return { left: `${x}px`, top: `${y}px`, transform: 'translateX(-50%)' }
})
</script>

<style scoped>
.selection-menu {
  position: fixed;
  z-index: 2000;
  display: flex;
  max-width: calc(100vw - 16px);
  overflow-x: auto;
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 10px;
  box-shadow: 0 4px 16px #0002;
  touch-action: none;
  scrollbar-width: none;
}
.selection-menu::-webkit-scrollbar { display: none; }
.menu-item,
.more-trigger {
  border: 0;
  background: transparent;
  cursor: pointer;
  color: #374151;
  -webkit-tap-highlight-color: transparent;
}
.menu-item {
  display: flex;
  align-items: center;
  gap: 4px;
  min-height: 44px;
  padding: 0 14px;
  font: inherit;
  font-size: 14px;
  white-space: nowrap;
}
.menu-item:hover,
.menu-item:focus-visible,
.more-trigger:hover,
.more-trigger:focus-visible {
  outline: none;
  background: #f3f4f6;
  color: #197fe6;
}
.more-trigger {
  width: 34px;
  border-left: 1px solid #f0f0f0;
}
.triangle {
  display: inline-block;
  border-top: 6px solid #6b7280;
  border-right: 5px solid transparent;
  border-left: 5px solid transparent;
  transition: transform 0.15s;
}
.triangle.open { transform: rotate(180deg); }
@media (max-width: 480px) {
  .selection-menu { border-radius: 20px; }
}
</style>
