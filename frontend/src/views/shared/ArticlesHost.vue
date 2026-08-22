<template>
  <component :is="layout" :role="layoutRole" :hideHeader="hideHeader" v-if="layout">
    <component :is="content" />
  </component>
  <component :is="content" v-else />
</template>

<script setup>
import { computed } from 'vue'
import { useUserStore } from '@/store/user'
import MobileLayout from '@/views/shared/MobileLayout.vue'
import StudentLayout from '@/views/student/Layout.vue'
import PublicArticles from '@/views/public/Articles.vue'
import StudentArticles from '@/views/student/Articles.vue'

defineOptions({ name: 'ArticlesHost' })

const userStore = useUserStore()

// 所有已登录角色统一使用 StudentArticles，保证文章列表体验一致
const content = computed(() =>
  userStore.token ? StudentArticles : PublicArticles
)

const layoutConfig = computed(() => {
  const role = userStore.userRole
  if (!userStore.token) return { layout: null, layoutRole: null }
  // 教师保留教师端底部导航，隐藏标题栏（StudentArticles 自带工具栏）
  if (role === 'TEACHER') {
    return { layout: MobileLayout, layoutRole: 'teacher', hideHeader: true }
  }
  if (['STUDENT', 'USER', 'ADMIN', 'OPS'].includes(role)) {
    return { layout: StudentLayout, layoutRole: null }
  }
  return { layout: null, layoutRole: null }
})

const layout = computed(() => layoutConfig.value.layout)
const layoutRole = computed(() => layoutConfig.value.layoutRole)
const hideHeader = computed(() => layoutConfig.value.hideHeader || false)
</script>
