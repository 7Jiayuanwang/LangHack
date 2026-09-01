<template>
  <AiQaPage
    api-path="/api/student/ai-qa/chat/stream"
    :back-label="agentMode ? '返回学习伙伴' : '返回阅读'"
    :agent-mode="agentMode"
  />
</template>

<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import AiQaPage from '@/views/shared/AiQaPage.vue'

const route = useRoute()
const agentMode = computed(() => route.query.mode === 'agent')
</script>
