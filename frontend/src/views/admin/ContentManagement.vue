<template>
  <div class="content-management">
    <!-- 顶部标题 -->
    <div class="header">
      <h2>内容管理</h2>
      <el-button @click="refreshCurrent" :icon="Refresh">刷新</el-button>
    </div>

    <!-- 一级 Tab：文章 / 视频 / 内容审核 -->
    <el-tabs v-model="activeTab" class="main-tabs" @tab-change="handleTabChange">
      <el-tab-pane name="texts">
        <template #label>
          <el-icon><Document /></el-icon>
          <span style="margin-left: 4px">文章管理</span>
        </template>
      </el-tab-pane>
      <el-tab-pane name="videos">
        <template #label>
          <el-icon><VideoPlay /></el-icon>
          <span style="margin-left: 4px">视频管理</span>
        </template>
      </el-tab-pane>
      <el-tab-pane name="review">
        <template #label>
          <el-icon><Edit /></el-icon>
          <span style="margin-left: 4px">内容审核</span>
          <el-badge v-if="totalPending > 0" :value="totalPending" class="tab-badge" />
        </template>
      </el-tab-pane>
      <el-tab-pane name="tags">
        <template #label>
          <el-icon><PriceTag /></el-icon>
          <span style="margin-left: 4px">标签管理</span>
        </template>
      </el-tab-pane>
    </el-tabs>

    <!-- 文章管理 -->
    <div v-show="activeTab === 'texts'" class="tab-content">
      <TextsPanel ref="textsPanel" />
    </div>

    <!-- 视频管理 -->
    <div v-show="activeTab === 'videos'" class="tab-content">
      <VideosPanel ref="videosPanel" />
    </div>

    <!-- 内容审核 -->
    <div v-show="activeTab === 'review'" class="tab-content">
      <ReviewPanel ref="reviewPanel" @pending-changed="loadPendingCount" />
    </div>

    <!-- 标签管理 -->
    <div v-show="activeTab === 'tags'" class="tab-content">
      <TagsPanel ref="tagsPanel" />
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { Refresh, Document, VideoPlay, Edit, PriceTag } from '@element-plus/icons-vue'
import TextsPanel from '@/views/admin/Texts.vue'
import VideosPanel from '@/views/admin/Videos.vue'
import ReviewPanel from '@/views/admin/ContentReview.vue'
import TagsPanel from '@/views/admin/ContentTags.vue'
import { getReviewTexts, getReviewVideos } from '@/api/admin'

const activeTab = ref('texts')
const totalPending = ref(0)

const textsPanel = ref(null)
const videosPanel = ref(null)
const reviewPanel = ref(null)
const tagsPanel = ref(null)

const loadPendingCount = async () => {
  try {
    const [textRes, videoRes] = await Promise.all([
      getReviewTexts({ page: 1, size: 1, status: 'PENDING' }),
      getReviewVideos({ page: 1, size: 1, status: 'PENDING' })
    ])
    const tp = (textRes?.data?.pendingCount || 0)
    const vp = (videoRes?.data?.pendingCount || 0)
    totalPending.value = tp + vp
  } catch (e) {
    // 静默
  }
}

const handleTabChange = () => {
  // tab 切换时不需要重新加载，v-show 保持状态
}

const refreshCurrent = () => {
  if (activeTab.value === 'texts' && textsPanel.value?.loadTexts) {
    textsPanel.value.loadTexts()
  } else if (activeTab.value === 'videos' && videosPanel.value?.loadVideos) {
    videosPanel.value.loadVideos()
  } else if (activeTab.value === 'review' && reviewPanel.value?.loadData) {
    reviewPanel.value.loadData()
  } else if (activeTab.value === 'tags' && tagsPanel.value?.loadTags) {
    tagsPanel.value.loadTags()
  }
}

onMounted(() => {
  loadPendingCount()
})
</script>

<style scoped>
.content-management {
  padding: 0;
}

.header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
  padding: 16px 20px 0;
}

.header h2 {
  margin: 0;
  color: #303133;
  font-weight: 500;
  font-size: 18px;
}

.main-tabs {
  padding: 0 20px;
}

.main-tabs :deep(.el-tabs__item) {
  font-size: 15px;
  padding: 0 20px;
}

.tab-badge {
  margin-left: 6px;
}

.tab-content {
  min-height: 300px;
}

/* 去除子组件的重复 header */
.tab-content :deep(.texts-management > .header),
.tab-content :deep(.videos-management > .header),
.tab-content :deep(.content-review > .header) {
  display: none;
}

@media (max-width: 768px) {
  .header {
    padding: 12px 16px 0;
  }
  .main-tabs {
    padding: 0 16px;
  }
}
</style>
