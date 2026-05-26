<template>
  <div class="student-layout-host">
    <component :is="layout" :role="layoutRole" :hideHeader="hideHeader" />
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useUserStore } from '@/store/user'
import StudentLayout from '@/views/student/Layout.vue'
import MobileLayout from '@/views/shared/MobileLayout.vue'

const userStore = useUserStore()

const layoutConfig = computed(() => {
  const role = userStore.userRole
  if (role === 'TEACHER') {
    return { layout: MobileLayout, layoutRole: 'teacher', hideHeader: true }
  }
  return { layout: StudentLayout, layoutRole: null, hideHeader: false }
})

const layout = computed(() => layoutConfig.value.layout)
const layoutRole = computed(() => layoutConfig.value.layoutRole)
const hideHeader = computed(() => layoutConfig.value.hideHeader)
</script>

<style scoped>
/* MobileLayout 在 hideHeader=true 时把 .content-container 的 overflow 锁成 hidden，
   适合 InteractiveText/StudentArticles 这类自带滚动容器的页面，
   但 /student/videos、/student/video-player、/student/ai-video、/student/ai-qa 等
   依赖外层滚动的页面会被锁死。此处仅在 StudentLayoutHost 内局部放开。 */
.student-layout-host {
  height: 100dvh;
  display: flex;
  flex-direction: column;
}
.student-layout-host :deep(.content-container.no-padding) {
  overflow-y: auto;
  -webkit-overflow-scrolling: touch;
}
</style>
