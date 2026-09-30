<template>
  <div class="content-review">
    <!-- 内容类型切换 -->
    <el-tabs v-model="activeTab" @tab-change="handleTabChange" class="review-tabs">
      <el-tab-pane label="课文审核" name="texts">
        <template #label>
          <span>课文审核</span>
          <el-badge v-if="textStats.pendingCount > 0" :value="textStats.pendingCount" class="tab-badge" />
        </template>
      </el-tab-pane>
      <el-tab-pane label="视频审核" name="videos">
        <template #label>
          <span>视频审核</span>
          <el-badge v-if="videoStats.pendingCount > 0" :value="videoStats.pendingCount" class="tab-badge" />
        </template>
      </el-tab-pane>
    </el-tabs>

    <!-- 免审核开关 -->
    <div class="auto-approve-bar">
      <span class="auto-approve-label">课文免审核</span>
      <el-switch
        v-model="textAutoApprove"
        @change="handleTextAutoApproveChange"
        active-text="开"
        inactive-text="关"
      />
      <span class="auto-approve-label" style="margin-left: 24px">视频免审核</span>
      <el-switch
        v-model="videoAutoApprove"
        @change="handleVideoAutoApproveChange"
        active-text="开"
        inactive-text="关"
      />
      <span class="auto-approve-hint">开启后，新上传的内容自动通过审核</span>
    </div>

    <!-- 统计卡片 -->
    <div class="stats-cards">
      <div class="stat-card stat-pending" @click="filterByStatus('PENDING')">
        <div class="stat-value">{{ currentStats.pendingCount || 0 }}</div>
        <div class="stat-label">待审核</div>
      </div>
      <div class="stat-card stat-approved" @click="filterByStatus('APPROVED')">
        <div class="stat-value">{{ currentStats.approvedCount || 0 }}</div>
        <div class="stat-label">已通过</div>
      </div>
      <div class="stat-card stat-rejected" @click="filterByStatus('REJECTED')">
        <div class="stat-value">{{ currentStats.rejectedCount || 0 }}</div>
        <div class="stat-label">已驳回</div>
      </div>
    </div>

    <!-- 搜索和筛选 -->
    <div class="filter-bar">
      <el-form :inline="true" class="filter-form">
        <el-form-item>
          <el-input
            v-model="searchKeyword"
            placeholder="搜索标题或内容"
            clearable
            style="width: 200px"
            @keyup.enter="loadData"
          >
            <template #prefix>
              <el-icon><Search /></el-icon>
            </template>
          </el-input>
        </el-form-item>
        <el-form-item>
          <el-select v-model="statusFilter" placeholder="审核状态" clearable style="width: 130px" @change="handleSearch">
            <el-option label="全部" value="" />
            <el-option label="待审核" value="PENDING" />
            <el-option label="已通过" value="APPROVED" />
            <el-option label="已驳回" value="REJECTED" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-select v-model="tagFilter" multiple collapse-tags placeholder="标签筛选" clearable style="width: 200px" @change="handleSearch">
            <el-option v-for="t in availableTags" :key="t.id" :label="t.name" :value="t.name" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">
            <el-icon><Search /></el-icon>
            搜索
          </el-button>
        </el-form-item>
      </el-form>
      <div class="filter-actions">
        <el-button @click="loadData">
          <el-icon><Refresh /></el-icon>
          刷新
        </el-button>
      </div>
    </div>

    <!-- 批量操作 -->
    <div class="batch-actions" v-if="selectedRows.length > 0">
      <span class="selected-count">已选 {{ selectedRows.length }} 项</span>
      <el-button type="success" size="small" @click="batchApprove">
        <el-icon><Select /></el-icon>
        批量通过
      </el-button>
      <el-button type="danger" size="small" @click="batchReject">
        <el-icon><CloseBold /></el-icon>
        批量驳回
      </el-button>
    </div>

    <!-- 课文列表 -->
    <div class="review-table" v-if="activeTab === 'texts'">
      <el-table
        v-loading="loading"
        :data="records"
        stripe
        border
        style="width: 100%"
        empty-text="暂无待审核课文"
        @selection-change="handleSelectionChange"
      >
        <el-table-column type="selection" width="50" />
        <el-table-column prop="title" label="标题" min-width="180" show-overflow-tooltip />
        <el-table-column label="作者" width="150">
          <template #default="{ row }">
            <div class="author-info">
              <span class="author-name">{{ row.uploaderRealName || row.uploaderName || '未知' }}</span>
              <span v-if="row.uploaderRealName && row.uploaderName" class="author-username">@{{ row.uploaderName }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="内容预览" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">
            {{ getContentPreview(row.contentType === 'MARKDOWN' ? row.markdownContent : row.content) }}
          </template>
        </el-table-column>
        <el-table-column prop="difficulty" label="难度" width="90">
          <template #default="{ row }">
            <el-tag :type="getDifficultyType(row.difficulty)" size="small">
              {{ getDifficultyText(row.difficulty) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="上传时间" width="170">
          <template #default="{ row }">
            {{ formatTime(row.createdTime) }}
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="getStatusType(row.status)" size="small">
              {{ getStatusText(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" size="small" text @click="viewDetail(row)">查看</el-button>
            <el-button type="success" size="small" text @click="handleApprove(row)" :disabled="row.status === 'APPROVED'">通过</el-button>
            <el-button type="danger" size="small" text @click="handleReject(row)" :disabled="row.status === 'REJECTED'">驳回</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- 视频列表 -->
    <div class="review-table" v-if="activeTab === 'videos'">
      <el-table
        v-loading="loading"
        :data="records"
        stripe
        border
        style="width: 100%"
        empty-text="暂无待审核视频"
        @selection-change="handleSelectionChange"
      >
        <el-table-column type="selection" width="50" />
        <el-table-column prop="title" label="标题" min-width="180" show-overflow-tooltip />
        <el-table-column label="作者" width="150">
          <template #default="{ row }">
            <div class="author-info">
              <span class="author-name">{{ row.uploaderRealName || row.uploaderName || '未知' }}</span>
              <span v-if="row.uploaderRealName && row.uploaderName" class="author-username">@{{ row.uploaderName }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="描述" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">
            {{ getContentPreview(row.description) }}
          </template>
        </el-table-column>
        <el-table-column prop="tags" label="标签" width="130" show-overflow-tooltip />
        <el-table-column label="上传时间" width="170">
          <template #default="{ row }">
            {{ formatTime(row.createdTime) }}
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="getStatusType(row.status)" size="small">
              {{ getStatusText(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" size="small" text @click="viewVideoDetail(row)">查看</el-button>
            <el-button type="success" size="small" text @click="handleApprove(row)" :disabled="row.status === 'APPROVED'">通过</el-button>
            <el-button type="danger" size="small" text @click="handleReject(row)" :disabled="row.status === 'REJECTED'">驳回</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- 分页 -->
    <div class="pagination">
      <el-pagination
        v-model:current-page="pagination.current"
        v-model:page-size="pagination.size"
        :page-sizes="[10, 20, 50]"
        :total="pagination.total"
        layout="total, sizes, prev, pager, next"
        @size-change="handleSizeChange"
        @current-change="handleCurrentChange"
      />
    </div>

    <!-- 课文详情弹窗 -->
    <el-dialog
      v-model="textDetailVisible"
      title="课文详情"
      width="min(750px, 92%)"
      :close-on-click-modal="true"
    >
      <div class="detail-content" v-if="currentDetail">
        <div class="detail-meta">
          <el-descriptions :column="2" border size="small">
            <el-descriptions-item label="标题">{{ currentDetail.title }}</el-descriptions-item>
            <el-descriptions-item label="作者">{{ currentDetail.uploaderRealName || currentDetail.uploaderName || '未知' }}</el-descriptions-item>
            <el-descriptions-item label="难度">
              <el-tag :type="getDifficultyType(currentDetail.difficulty)" size="small">
                {{ getDifficultyText(currentDetail.difficulty) }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="状态">
              <el-tag :type="getStatusType(currentDetail.status)" size="small">
                {{ getStatusText(currentDetail.status) }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="上传时间">{{ formatTime(currentDetail.createdTime) }}</el-descriptions-item>
            <el-descriptions-item label="标签">{{ currentDetail.tags || '无' }}</el-descriptions-item>
          </el-descriptions>
        </div>

        <div v-if="currentDetail.coverUrl" class="detail-cover">
          <img :src="currentDetail.coverUrl" alt="封面图" />
        </div>

        <div class="detail-body">
          <h4>内容</h4>
          <div class="text-content-box">
            <MarkdownRenderer v-if="currentDetail.contentType === 'MARKDOWN'" :markdown="currentDetail.markdownContent || currentDetail.content || ''" />
            <pre v-else>{{ currentDetail.content }}</pre>
          </div>
        </div>
      </div>

      <template #footer>
        <el-button @click="textDetailVisible = false">关闭</el-button>
        <el-button type="primary" @click="previewText(currentDetail)">
          <el-icon><View /></el-icon>
          预览文章
        </el-button>
        <el-button type="success" @click="handleApprove(currentDetail)" :disabled="currentDetail?.status === 'APPROVED'">
          <el-icon><Select /></el-icon>
          通过
        </el-button>
        <el-button type="danger" @click="handleReject(currentDetail)" :disabled="currentDetail?.status === 'REJECTED'">
          <el-icon><CloseBold /></el-icon>
          驳回
        </el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="videoDetailVisible"
      title="视频详情"
      width="min(750px, 92%)"
      :close-on-click-modal="true"
    >
      <div class="detail-content" v-if="currentDetail">
        <div class="detail-meta">
          <el-descriptions :column="2" border size="small">
            <el-descriptions-item label="标题">{{ currentDetail.title }}</el-descriptions-item>
            <el-descriptions-item label="作者">{{ currentDetail.uploaderRealName || currentDetail.uploaderName || '未知' }}</el-descriptions-item>
            <el-descriptions-item label="状态">
              <el-tag :type="getStatusType(currentDetail.status)" size="small">
                {{ getStatusText(currentDetail.status) }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="上传时间">{{ formatTime(currentDetail.createdTime) }}</el-descriptions-item>
            <el-descriptions-item label="标签" :span="2">{{ currentDetail.tags || '无' }}</el-descriptions-item>
            <el-descriptions-item label="字幕" :span="2">
              <div class="subtitle-info">
                <el-tag v-if="hasEnglishSubtitle(currentDetail.subtitleContent)" type="success" size="small" style="margin-right: 6px">英文字幕</el-tag>
                <el-tag v-if="currentDetail.subtitleContentZh" type="warning" size="small" style="margin-right: 6px">中文字幕</el-tag>
                <span v-if="!currentDetail.subtitleContent && !currentDetail.subtitleContentZh" style="color: #999">无字幕</span>
              </div>
            </el-descriptions-item>
          </el-descriptions>
        </div>

        <div v-if="currentDetail.coverUrl" class="detail-cover">
          <img :src="currentDetail.coverUrl" alt="视频封面" style="max-width: 100%; border-radius: 8px; margin-top: 12px" />
        </div>

        <div v-if="currentDetail.description" class="detail-body">
          <h4>描述</h4>
          <p>{{ currentDetail.description }}</p>
        </div>
      </div>

      <template #footer>
        <el-button @click="videoDetailVisible = false">关闭</el-button>
        <el-button type="primary" @click="previewVideo(currentDetail)">
          <el-icon><VideoPlay /></el-icon>
          预览视频
        </el-button>
        <el-button type="success" @click="handleApprove(currentDetail)" :disabled="currentDetail?.status === 'APPROVED'">
          <el-icon><Select /></el-icon>
          通过
        </el-button>
        <el-button type="danger" @click="handleReject(currentDetail)" :disabled="currentDetail?.status === 'REJECTED'">
          <el-icon><CloseBold /></el-icon>
          驳回
        </el-button>
      </template>
    </el-dialog>


  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh, Search, Select, CloseBold, View, VideoPlay } from '@element-plus/icons-vue'
import * as adminApi from '@/api/admin'
import { getContentTags, getAutoApproveSettings, setAutoApproveSettings } from '@/api/admin'
import MarkdownRenderer from '@/components/MarkdownRenderer.vue'

const router = useRouter()
const emit = defineEmits(['pending-changed'])

// 响应式状态
const activeTab = ref('texts')
const loading = ref(false)
const records = ref([])
const selectedRows = ref([])
const searchKeyword = ref('')
const statusFilter = ref('')
const tagFilter = ref([])
const availableTags = ref([])
const textDetailVisible = ref(false)
const videoDetailVisible = ref(false)
const currentDetail = ref(null)

const textStats = reactive({ pendingCount: 0, approvedCount: 0, rejectedCount: 0 })
const videoStats = reactive({ pendingCount: 0, approvedCount: 0, rejectedCount: 0 })

const textAutoApprove = ref(false)
const videoAutoApprove = ref(false)

const pagination = reactive({
  current: 1,
  size: 20,
  total: 0
})

const currentStats = computed(() => activeTab.value === 'texts' ? textStats : videoStats)

// 加载数据
const loadData = async () => {
  loading.value = true
  try {
    const params = {
      page: pagination.current,
      size: pagination.size
    }
    if (statusFilter.value) params.status = statusFilter.value
    if (searchKeyword.value) params.keyword = searchKeyword.value
    if (tagFilter.value && tagFilter.value.length > 0) params.tag = tagFilter.value.join(',')

    let response
    if (activeTab.value === 'texts') {
      response = await adminApi.getReviewTexts(params)
    } else {
      response = await adminApi.getReviewVideos(params)
    }

    if (response.code === 200 && response.data) {
      records.value = response.data.records || []
      pagination.total = response.data.total || 0

      const stats = activeTab.value === 'texts' ? textStats : videoStats
      stats.pendingCount = response.data.pendingCount || 0
      stats.approvedCount = response.data.approvedCount || 0
      stats.rejectedCount = response.data.rejectedCount || 0
    }
  } catch (error) {
    ElMessage.error('加载数据失败')
    console.error(error)
  } finally {
    loading.value = false
  }
}

// 同时加载另一个tab的统计（用于badge显示）
const loadOtherStats = async () => {
  try {
    const params = { page: 1, size: 1 }
    let response
    if (activeTab.value === 'texts') {
      response = await adminApi.getReviewVideos(params)
      if (response.code === 200 && response.data) {
        videoStats.pendingCount = response.data.pendingCount || 0
        videoStats.approvedCount = response.data.approvedCount || 0
        videoStats.rejectedCount = response.data.rejectedCount || 0
      }
    } else {
      response = await adminApi.getReviewTexts(params)
      if (response.code === 200 && response.data) {
        textStats.pendingCount = response.data.pendingCount || 0
        textStats.approvedCount = response.data.approvedCount || 0
        textStats.rejectedCount = response.data.rejectedCount || 0
      }
    }
  } catch (e) {
    // 静默失败
  }
}

const handleTabChange = () => {
  pagination.current = 1
  selectedRows.value = []
  loadData()
}

const handleSearch = () => {
  pagination.current = 1
  loadData()
}

const filterByStatus = (status) => {
  statusFilter.value = statusFilter.value === status ? '' : status
  pagination.current = 1
  loadData()
}

const handleSizeChange = () => {
  pagination.current = 1
  loadData()
}

const handleCurrentChange = () => {
  loadData()
}

const handleSelectionChange = (rows) => {
  selectedRows.value = rows
}

// 审核操作
const handleApprove = async (row) => {
  if (!row) return

  try {
    let response
    if (activeTab.value === 'texts') {
      response = await adminApi.approveText(row.id)
    } else {
      response = await adminApi.approveVideo(row.id)
    }

    if (response.code === 200) {
      ElMessage.success('审核通过')
      loadData()
      loadOtherStats()
      emit('pending-changed')
    } else {
      ElMessage.error(response.message || '操作失败')
    }
  } catch (e) {
    ElMessage.error('操作失败')
  }
}

const handleReject = async (row) => {
  if (!row) return
  try {
    await ElMessageBox.confirm(`确定驳回「${row.title}」？`, '驳回确认', {
      confirmButtonText: '确定驳回',
      cancelButtonText: '取消',
      type: 'warning'
    })

    let response
    if (activeTab.value === 'texts') {
      response = await adminApi.rejectText(row.id)
    } else {
      response = await adminApi.rejectVideo(row.id)
    }

    if (response.code === 200) {
      ElMessage.success('已驳回')
      loadData()
      loadOtherStats()
      emit('pending-changed')
    } else {
      ElMessage.error(response.message || '操作失败')
    }
  } catch (e) {
    if (e !== 'cancel' && e?.message !== 'Cancel') {
      ElMessage.error('操作失败')
    }
  }
}

// 批量操作
const batchApprove = async () => {
  try {
    await ElMessageBox.confirm(`确定批量通过 ${selectedRows.value.length} 项内容？`, '批量审核', {
      confirmButtonText: '全部通过',
      cancelButtonText: '取消',
      type: 'success'
    })

    const apiFn = activeTab.value === 'texts' ? adminApi.approveText : adminApi.approveVideo
    let successCount = 0
    for (const row of selectedRows.value) {
      try {
        const res = await apiFn(row.id)
        if (res.code === 200) successCount++
      } catch (e) {
        // 继续处理下一个
      }
    }

    ElMessage.success(`已通过 ${successCount} 项`)
    selectedRows.value = []
    loadData()
    loadOtherStats()
    emit('pending-changed')
  } catch (e) {
    // 用户取消
  }
}

const batchReject = async () => {
  try {
    await ElMessageBox.confirm(`确定批量驳回 ${selectedRows.value.length} 项内容？`, '批量驳回', {
      confirmButtonText: '全部驳回',
      cancelButtonText: '取消',
      type: 'warning'
    })

    const apiFn = activeTab.value === 'texts' ? adminApi.rejectText : adminApi.rejectVideo
    let successCount = 0
    for (const row of selectedRows.value) {
      try {
        const res = await apiFn(row.id)
        if (res.code === 200) successCount++
      } catch (e) {
        // 继续处理下一个
      }
    }

    ElMessage.success(`已驳回 ${successCount} 项`)
    selectedRows.value = []
    loadData()
    loadOtherStats()
    emit('pending-changed')
  } catch (e) {
    // 用户取消
  }
}

// 查看详情 — 已通过的内容跳转到展示页，未通过的用弹窗预览
const viewDetail = (row) => {
  currentDetail.value = { ...row }
  textDetailVisible.value = true
}

const viewVideoDetail = (row) => {
  currentDetail.value = { ...row }
  videoDetailVisible.value = true
}

const previewText = (row) => {
  if (row?.id) {
    const routeData = router.resolve({ name: 'ArticleDetail', params: { id: row.id }, query: { from: 'admin-review' } })
    window.open(routeData.href, '_blank')
  }
}

const previewVideo = (row) => {
  if (row?.id) {
    const routeData = router.resolve({
      name: 'StudentVideoPlayer',
      query: { id: row.id, preview: 'admin', from: 'admin-review' }
    })
    window.open(routeData.href, '_blank')
  }
}

// 字幕检测
const hasEnglishSubtitle = (content) => {
  if (!content) return false
  return /[a-zA-Z]{2,}/.test(content)
}

// 工具方法
const getContentPreview = (content) => {
  if (!content) return ''
  return content.length > 80 ? content.substring(0, 80) + '...' : content
}

const getDifficultyType = (difficulty) => {
  return { EASY: 'success', MEDIUM: 'warning', HARD: 'danger' }[difficulty] || 'info'
}

const getDifficultyText = (difficulty) => {
  return { EASY: '简单', MEDIUM: '中等', HARD: '困难' }[difficulty] || difficulty
}

const getStatusType = (status) => {
  return { PENDING: 'warning', APPROVED: 'success', REJECTED: 'danger' }[status] || 'info'
}

const getStatusText = (status) => {
  return { PENDING: '待审核', APPROVED: '已通过', REJECTED: '已驳回' }[status] || status || '未知'
}

const formatTime = (time) => {
  if (!time) return ''
  return new Date(time).toLocaleString('zh-CN')
}

// 免审核开关
const loadAutoApproveSettings = async () => {
  try {
    const res = await getAutoApproveSettings()
    if (res.code === 200 && res.data) {
      textAutoApprove.value = res.data.textAutoApprove || false
      videoAutoApprove.value = res.data.videoAutoApprove || false
    }
  } catch (e) {
    // 静默
  }
}

const handleTextAutoApproveChange = async (val) => {
  try {
    const res = await setAutoApproveSettings({ textAutoApprove: val })
    if (res.code === 200) {
      ElMessage.success(val ? '课文免审核已开启' : '课文免审核已关闭')
    } else {
      textAutoApprove.value = !val
      ElMessage.error(res.message || '设置失败')
    }
  } catch (e) {
    textAutoApprove.value = !val
    ElMessage.error('设置失败')
  }
}

const handleVideoAutoApproveChange = async (val) => {
  try {
    const res = await setAutoApproveSettings({ videoAutoApprove: val })
    if (res.code === 200) {
      ElMessage.success(val ? '视频免审核已开启' : '视频免审核已关闭')
    } else {
      videoAutoApprove.value = !val
      ElMessage.error(res.message || '设置失败')
    }
  } catch (e) {
    videoAutoApprove.value = !val
    ElMessage.error('设置失败')
  }
}

// 生命周期
onMounted(() => {
  loadData()
  loadOtherStats()
  loadAvailableTags()
  loadAutoApproveSettings()
})

const loadAvailableTags = async () => {
  try {
    const res = await getContentTags()
    if (res.code === 200) {
      availableTags.value = res.data || []
    }
  } catch (e) {
    // 静默
  }
}

// 暴露方法给父组件
defineExpose({ loadData })
</script>

<style scoped>
.content-review {
  padding: 20px;
  max-width: 1400px;
  margin: 0 auto;
}

.header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
  padding-bottom: 16px;
  border-bottom: 1px solid #e4e7ed;
}

.header h2 {
  margin: 0;
  color: #303133;
  font-weight: 500;
}

.review-tabs {
  margin-bottom: 16px;
}

.auto-approve-bar {
  display: flex;
  align-items: center;
  padding: 12px 16px;
  background: #f0f9ff;
  border: 1px solid #b3d8ff;
  border-radius: 8px;
  margin-bottom: 16px;
}

.auto-approve-label {
  font-size: 14px;
  font-weight: 500;
  color: #303133;
  margin-right: 8px;
}

.auto-approve-hint {
  margin-left: auto;
  font-size: 12px;
  color: #909399;
}

.tab-badge {
  margin-left: 6px;
}

.stats-cards {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16px;
  margin-bottom: 20px;
}

.stat-card {
  text-align: center;
  padding: 20px;
  background: #fff;
  border: 1px solid #e4e7ed;
  border-radius: 10px;
  cursor: pointer;
  transition: all 0.3s ease;
}

.stat-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.08);
}

.stat-pending {
  border-left: 4px solid #e6a23c;
}

.stat-approved {
  border-left: 4px solid #67c23a;
}

.stat-rejected {
  border-left: 4px solid #f56c6c;
}

.stat-value {
  font-size: 32px;
  font-weight: bold;
  margin-bottom: 6px;
}

.stat-pending .stat-value { color: #e6a23c; }
.stat-approved .stat-value { color: #67c23a; }
.stat-rejected .stat-value { color: #f56c6c; }

.stat-label {
  font-size: 14px;
  color: #909399;
}

.filter-bar {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  flex-wrap: wrap;
  gap: 12px;
  margin-bottom: 20px;
  padding: 16px 20px;
  background: #f8f9fa;
  border-radius: 8px;
}

.filter-form {
  margin: 0;
}

.filter-actions {
  display: flex;
  gap: 12px;
  flex-shrink: 0;
}

.batch-actions {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
  padding: 12px 16px;
  background: var(--xy-brand-50);
  border-radius: 8px;
  border: 1px solid #b3d8ff;
}

.selected-count {
  font-size: 14px;
  color: var(--xy-brand-600);
  font-weight: 500;
}

.review-table {
  background: #fff;
  border-radius: 8px;
  overflow-x: auto;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
}

.author-info {
  display: flex;
  flex-direction: column;
}

.author-name {
  font-weight: 500;
  color: #303133;
  font-size: 13px;
}

.author-username {
  font-size: 12px;
  color: #909399;
  margin-top: 2px;
}

.pagination {
  margin-top: 20px;
  display: flex;
  justify-content: center;
}

/* 详情弹窗 */
.detail-meta {
  margin-bottom: 20px;
}

.detail-cover {
  margin-bottom: 20px;
  border-radius: 8px;
  overflow: hidden;
  max-height: 200px;
}

.detail-cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.detail-video {
  margin: 20px 0;
}

.detail-body h4 {
  margin: 0 0 12px;
  color: #303133;
  font-size: 15px;
}

.text-content-box {
  max-height: 50vh;
  overflow-y: auto;
  padding: 16px;
  background: #f8f9fa;
  border-radius: 8px;
  line-height: 1.7;
}

.text-content-box pre {
  white-space: pre-wrap;
  word-wrap: break-word;
  font-family: inherit;
  color: #303133;
  margin: 0;
}

/* 响应式 */
@media (max-width: 768px) {
  .content-review {
    padding: 16px;
  }

  .header {
    flex-direction: column;
    gap: 12px;
    align-items: flex-start;
  }

  .stats-cards {
    grid-template-columns: repeat(3, 1fr);
    gap: 10px;
  }

  .stat-card {
    padding: 14px 8px;
  }

  .stat-value {
    font-size: 24px;
  }

  .filter-form {
    display: block;
  }

  .filter-form .el-form-item {
    margin-right: 0;
    margin-bottom: 12px;
  }
}

/* 分配单元弹窗 */
.assign-hint {
  margin-bottom: 20px;
}

.assign-form {
  padding: 0 10px;
}

.assign-form .el-form-item {
  margin-bottom: 18px;
}
</style>
