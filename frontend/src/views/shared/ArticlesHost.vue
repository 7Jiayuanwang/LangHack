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
import PublicArticles from '@/views/public/Articles.vue'
import StudentArticles from '@/views/student/Articles.vue'

const userStore = useUserStore()

// 所有已登录角色统一使用 StudentArticles，保证文章列表体验一致
const content = computed(() =>
  userStore.token ? StudentArticles : PublicArticles
)

const layoutConfig = computed(() => {
  const role = userStore.userRole
  if (!userStore.token) return { layout: null, layoutRole: null }
  // 所有已登录角色统一使用 StudentLayout
  if (['STUDENT', 'USER', 'TEACHER', 'ADMIN', 'OPS'].includes(role)) {
    return { layout: StudentLayout, layoutRole: null }
  }
  return { layout: null, layoutRole: null }
})

const layout = computed(() => layoutConfig.value.layout)
const layoutRole = computed(() => layoutConfig.value.layoutRole)
</script>
