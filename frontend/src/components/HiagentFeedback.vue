<template>
  <div ref="hiagentContainer"></div>
</template>

<script setup>
import { onMounted, onUnmounted, ref } from 'vue'

const hiagentContainer = ref(null)
let hiagentClient = null

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

onMounted(() => {
  // 等待 SDK 加载完成
  const initHiagent = () => {
    if (window.HiagentWebSDK && window.HiagentWebSDK.WebLiteClient) {
      try {
        hiagentClient = new window.HiagentWebSDK.WebLiteClient({
          appKey: props.appKey,
          baseUrl: props.baseUrl
        })
        console.log('HiAgent 问题反馈已初始化')
      } catch (error) {
        console.error('HiAgent 初始化失败:', error)
      }
    } else {
      // SDK 未加载完成，延迟重试
      setTimeout(initHiagent, 100)
    }
  }

  initHiagent()
})

onUnmounted(() => {
  // 清理 HiAgent 实例（如果 SDK 提供了销毁方法）
  if (hiagentClient && typeof hiagentClient.destroy === 'function') {
    hiagentClient.destroy()
  }
})
</script>

<style scoped>
/* HiAgent 气泡样式由 SDK 自动注入，这里不需要额外样式 */
</style>
