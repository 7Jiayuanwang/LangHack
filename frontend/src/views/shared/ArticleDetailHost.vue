<template>
  <component :is="layout" :role="layoutRole" v-if="layout">
    <component :is="content" />
  </component>
  <component :is="content" v-else />
</template>

<script setup>
import { computed } from 'vue'
import { useUserStore } from '@/store/user'
import StudentLayout from '@/views/student/Layout.vue'
import InteractiveText from '@/views/student/InteractiveText.vue'

const userStore = useUserStore()

const content = computed(() => InteractiveText)

const layoutConfig = computed(() => {
  const role = userStore.userRole
  if (!userStore.token) return { layout: null, layoutRole: null }
  // 所有已登录角色统一使用 StudentLayout，保证文章阅读体验一致
  if (['STUDENT', 'USER', 'TEACHER', 'ADMIN', 'OPS'].includes(role)) {
    return { layout: StudentLayout, layoutRole: null }
  }
  return { layout: null, layoutRole: null }
})

const layout = computed(() => layoutConfig.value.layout)
const layoutRole = computed(() => layoutConfig.value.layoutRole)
</script>
