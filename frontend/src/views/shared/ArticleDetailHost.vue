<template>
  <component :is="layout" :role="layoutRole" :hideHeader="hideHeader" v-if="layout">
    <component :is="content" />
  </component>
  <component :is="content" v-else />
</template>

<script setup>
import { computed } from 'vue'
import { useUserStore } from '@/store/user'
import StudentLayout from '@/views/student/Layout.vue'
import MobileLayout from '@/views/shared/MobileLayout.vue'
import InteractiveText from '@/views/student/InteractiveText.vue'
import SimpleTextView from '@/views/user/SimpleTextView.vue'

const userStore = useUserStore()

const content = computed(() =>
  userStore.token ? InteractiveText : SimpleTextView
)

const layoutConfig = computed(() => {
  const role = userStore.userRole
  if (!userStore.token) return { layout: null, layoutRole: null }
  if (role === 'STUDENT') return { layout: StudentLayout, layoutRole: null }
  if (['USER', 'TEACHER', 'ADMIN', 'OPS'].includes(role)) {
    return {
      layout: MobileLayout,
      layoutRole: role.toLowerCase(),
      hideHeader: role === 'TEACHER'
    }
  }
  return { layout: null, layoutRole: null }
})

const layout = computed(() => layoutConfig.value.layout)
const layoutRole = computed(() => layoutConfig.value.layoutRole)
const hideHeader = computed(() => layoutConfig.value.hideHeader || false)
</script>
