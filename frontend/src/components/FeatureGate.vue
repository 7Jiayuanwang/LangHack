<template>
  <slot v-if="enabled" />
  <slot v-else name="fallback" />
</template>

<script setup>
/**
 * FeatureGate — 功能开关门控组件
 *
 * 用法（控制页面内部元素的显隐，如 Tab、按钮、卡片）：
 *
 *   <FeatureGate feature-key="page_vocabulary">
 *     <el-button>进入生词本</el-button>
 *     <template #fallback>
 *       <div class="disabled-hint">该功能暂未开放</div>
 *     </template>
 *   </FeatureGate>
 *
 * 渲染逻辑：
 *   - feature toggle 为 true/未定义 → 渲染默认 slot
 *   - feature toggle 为 false → 渲染 fallback slot（如无 fallback 则不渲染）
 *   - featureKey 为空 → 始终渲染默认 slot（向后兼容）
 *
 * 内部复用 useFeatureToggles composable 的 sessionStorage 缓存，
 * 无需额外网络请求。变更通过 onChange 监听自动重算。
 */
import { computed } from 'vue'
import { useFeatureToggles } from '@/composables/useFeatureToggles'

const props = defineProps({
  featureKey: {
    type: String,
    default: ''
  }
})

const { isEnabled } = useFeatureToggles()

const enabled = computed(() => {
  if (!props.featureKey) return true
  return isEnabled(props.featureKey)
})
</script>
