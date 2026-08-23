<template>
  <div class="words-tabs-container">
    <el-tabs v-model="activeTab" class="words-tabs" @tab-change="onTabChange">
      <el-tab-pane label="每日背诵" name="recite">
        <WordRecite v-if="activeTab === 'recite'" />
      </el-tab-pane>
      <el-tab-pane label="生词本" name="list">
        <Vocabulary v-if="activeTab === 'list'" />
      </el-tab-pane>
      <el-tab-pane label="联想" name="association">
        <WordAssociation v-if="activeTab === 'association'" />
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup>
import { ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import WordRecite from '@/features/VocabularyFeatures/WordRecite.vue'
import Vocabulary from '@/views/student/Vocabulary.vue'
import WordAssociation from '@/views/student/WordAssociation.vue'

const route = useRoute()
const router = useRouter()

const validTabs = ['recite', 'list', 'association']
const initialTab = validTabs.includes(route.query.tab) ? route.query.tab : 'recite'
const activeTab = ref(initialTab)

const onTabChange = (name) => {
  if (route.query.tab !== name) {
    router.replace({ path: route.path, query: { ...route.query, tab: name } })
  }
}

watch(() => route.query.tab, (val) => {
  if (validTabs.includes(val) && val !== activeTab.value) {
    activeTab.value = val
  }
})
</script>

<style scoped>
.words-tabs-container {
  padding: 0 16px;
  background-color: #f6f8fa;
  min-height: 100vh;
}

.words-tabs {
  --el-tabs-header-height: 48px;
}

.words-tabs :deep(.el-tabs__header) {
  margin: 0;
  padding: 8px 8px 0;
  background: white;
  border-radius: 0 0 16px 16px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.04);
  position: sticky;
  top: 0;
  z-index: 10;
}

.words-tabs :deep(.el-tabs__nav-wrap)::after {
  display: none;
}

.words-tabs :deep(.el-tabs__item) {
  font-size: 16px;
  font-weight: 600;
  height: 44px;
  padding: 0 24px;
}

.words-tabs :deep(.el-tabs__active-bar) {
  height: 3px;
  border-radius: 3px;
}

.words-tabs :deep(.el-tabs__content) {
  padding: 0;
  overflow: visible;
}

@media (max-width: 768px) {
  .words-tabs-container {
    padding: 0;
  }
  .words-tabs :deep(.el-tabs__item) {
    flex: 1;
    text-align: center;
    padding: 0 12px;
    font-size: 15px;
  }
  .words-tabs :deep(.el-tabs__nav) {
    width: 100%;
    display: flex;
  }
}
</style>
