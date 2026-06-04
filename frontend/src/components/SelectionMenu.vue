<template>
  <div
    v-if="visible"
    class="selection-menu"
    :style="menuStyle"
    @mousedown.prevent
  >
    <div class="menu-item" @click.stop="$emit('copy')" @touchend.stop.prevent="$emit('copy')">
      <el-icon><CopyDocument /></el-icon> 复制
    </div>
    <div class="menu-item" @click.stop="$emit('underline')" @touchend.stop.prevent="$emit('underline')">
      <el-icon><EditPen /></el-icon> {{ underlineLabel }}
    </div>
    <div class="menu-item" @click.stop="$emit('translate')" @touchend.stop.prevent="$emit('translate')">
      <el-icon><MagicStick /></el-icon> 翻译
    </div>
    <div class="menu-item" @click.stop="$emit('annotate')" @touchend.stop.prevent="$emit('annotate')">
      <el-icon><Comment /></el-icon> 批注
    </div>
    <div class="menu-item" @click.stop="$emit('ai-qa')" @touchend.stop.prevent="$emit('ai-qa')">
      <el-icon><ChatLineRound /></el-icon> AI 问答
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { CopyDocument, EditPen, Comment, ChatLineRound, MagicStick } from '@element-plus/icons-vue'

const props = defineProps({
  visible: {
    type: Boolean,
    required: true
  },
  position: {
    type: Object,
    default: () => ({ x: 0, y: 0 }),
    validator: (value) => {
      return value && typeof value.x === 'number' && typeof value.y === 'number'
    }
  },
  underlineLabel: {
    type: String,
    default: '划线'
  }
})
defineEmits(['copy', 'underline', 'translate', 'annotate', 'ai-qa'])

// 防止菜单超出视口右边界：菜单自身宽度约 220px，水平居中后需保留一半安全边距
const menuStyle = computed(() => {
  const MENU_HALF_WIDTH = 110
  const SAFE_MARGIN = 8
  const isMobile = typeof window !== 'undefined' && window.innerWidth <= 768
  const viewportWidth = typeof window !== 'undefined' ? window.innerWidth : 375

  const x = props.position?.x ?? 0
  const y = props.position?.y ?? 0

  const clampedX = Math.min(
    Math.max(x, MENU_HALF_WIDTH + SAFE_MARGIN),
    viewportWidth - MENU_HALF_WIDTH - SAFE_MARGIN
  )

  // 移动端菜单放在选区上方，避免和系统菜单重叠
  if (isMobile) {
    return {
      left: clampedX + 'px',
      bottom: `calc(100vh - ${y - 12}px)`,
      transform: 'translateX(-50%)'
    }
  }

  return {
    left: clampedX + 'px',
    top: y + 'px'
  }
})
</script>

<style scoped>
.selection-menu {
  position: fixed;
  transform: translateX(-50%);
  z-index: 2000;
  display: flex;
  flex-direction: row;
  max-width: calc(100vw - 16px);
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 10px;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.15);
  animation: menu-fade-in 0.15s ease-out;
  touch-action: none;
  /* 手机端紧凑布局 */
  overflow-x: auto;
  overflow-y: hidden;
  scrollbar-width: none; /* Firefox 隐藏滚动条 */
}

.selection-menu::-webkit-scrollbar {
  display: none; /* Chrome/Safari 隐藏滚动条 */
}

@media (max-width: 480px) {
  .selection-menu {
    border-radius: 20px;
  }
}

@keyframes menu-fade-in {
  from {
    opacity: 0;
    transform: translateX(-50%) translateY(4px);
  }
  to {
    opacity: 1;
    transform: translateX(-50%) translateY(0);
  }
}

.menu-item {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 0 14px;
  min-height: 44px; /* iOS/Android 触摸目标最小高度 */
  cursor: pointer;
  font-size: 14px;
  color: #374151;
  white-space: nowrap;
  transition: background-color 0.15s, color 0.15s;
  user-select: none;
  -webkit-tap-highlight-color: transparent; /* 消除移动端点击高亮闪烁 */
}

.menu-item:hover {
  background-color: #f3f4f6;
  color: #197fe6;
}

.menu-item:active {
  background-color: #e8f0fe;
  color: #197fe6;
}

.menu-item + .menu-item {
  border-left: 1px solid #f0f0f0;
}

.menu-item.disabled {
  color: #9ca3af;
  cursor: default;
}

.menu-item.disabled:hover {
  background-color: transparent;
  color: #9ca3af;
}
</style>
