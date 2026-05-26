<template>
  <component :is="layout" :role="layoutRole" :hideHeader="hideHeader" />
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
