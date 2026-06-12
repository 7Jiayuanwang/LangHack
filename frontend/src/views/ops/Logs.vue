<template>
  <div class="logs-container">
    <el-card class="header-card">
      <div class="header-content">
        <h2>日志管理</h2>
        <div class="actions">
          <el-tag v-if="autoRefresh && activeTab === 'app'" type="success" size="small" class="poll-tag">
            实时刷新 (5s)
          </el-tag>
          <el-button type="primary" @click="refreshCurrentTab" :loading="loading">
            <el-icon><Refresh /></el-icon>
            刷新
          </el-button>
          <el-button v-if="activeTab === 'app'" type="success" @click="downloadLogs" :loading="downloading">
            <el-icon><Download /></el-icon>
            下载日志
          </el-button>
        </div>
      </div>
    </el-card>

    <el-tabs v-model="activeTab" @tab-change="handleTabChange" class="logs-tabs">
      <el-tab-pane label="应用日志" name="app">
        <ApplicationLogs
          :loading="loading"
          :entries="entries"
          :levelCounts="levelCounts"
          :filterLevel="filterLevel"
          :limit="limit"
          :searchText="searchText"
          :dateRange="dateRange"
          :autoRefresh="autoRefresh"
          :lastUpdate="lastUpdate"
          @update:filterLevel="filterLevel = $event"
          @update:limit="limit = $event"
          @update:searchText="searchText = $event"
          @update:dateRange="dateRange = $event"
          @update:autoRefresh="toggleAutoRefresh"
          @refresh="refreshLogs"
        />
      </el-tab-pane>

      <el-tab-pane label="访问日志" name="access">
        <AccessLogs ref="accessLogsRef" :loading="loading" />
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh, Download } from '@element-plus/icons-vue'
import { getApplicationLogs, downloadApplicationLogs } from '@/api/ops'
import { normalizeDownloadResponse, extractBlobErrorMessage, triggerBlobDownload } from '@/utils/blobDownload'
import ApplicationLogs from './components/ApplicationLogs.vue'
import AccessLogs from './components/AccessLogs.vue'

// ── 状态 ──────────────────────────────────────────────────────────────────────
const activeTab = ref('app')
const loading = ref(false)
const downloading = ref(false)
const entries = ref([])
const levelCounts = ref({})
const filterLevel = ref('')
const limit = ref(500)
const searchText = ref('')
const dateRange = ref(null)
const autoRefresh = ref(true)
const lastUpdate = ref('')
const accessLogsRef = ref(null)
let pollTimer = null

// ── 应用日志API ───────────────────────────────────────────────────────────────
const refreshLogs = async () => {
  loading.value = true
  try {
    const params = { limit: limit.value }
    if (filterLevel.value) params.level = filterLevel.value
    const res = await getApplicationLogs(params)
    if (res.code === 200) {
      // 给每条日志加稳定 id，让 Vue diff 复用 DOM 节点
      const raw = res.data.entries || []
      raw.forEach((e, i) => { e._id = `${e.timestamp}-${i}` })
      entries.value = raw
      levelCounts.value = res.data.levelCounts || {}
      lastUpdate.value = new Date().toLocaleTimeString()
    } else {
      ElMessage.error(res.message || '获取日志失败')
    }
  } catch {
    ElMessage.error('获取日志失败')
  } finally {
    loading.value = false
  }
}

function startPolling() {
  stopPolling()
  pollTimer = setInterval(() => {
    if (autoRefresh.value && !loading.value) refreshLogs()
  }, 5000)
}

function stopPolling() {
  if (pollTimer) { clearInterval(pollTimer); pollTimer = null }
}

function toggleAutoRefresh() {
  if (autoRefresh.value) startPolling()
  else stopPolling()
}

const downloadLogs = async () => {
  downloading.value = true
  try {
    const params = { limit: 2000 }
    if (filterLevel.value) params.level = filterLevel.value
    if (dateRange.value && dateRange.value[0]) {
      params.startDate = dateRange.value[0]
      params.endDate = dateRange.value[1]
    }
    const response = await downloadApplicationLogs(params)
    const normalized = await normalizeDownloadResponse(
      response,
      `langhack-${new Date().toISOString().slice(0, 10)}.log`,
      'text/plain;charset=utf-8'
    )
    if ((normalized.blob.type || '').includes('application/json')) {
      ElMessage.error(await extractBlobErrorMessage(normalized.blob, '下载日志失败'))
      return
    }
    if (normalized.blob.size === 0) {
      ElMessage.error('下载日志失败：日志内容为空')
      return
    }
    triggerBlobDownload(normalized.blob, normalized.filename)
    ElMessage.success('日志下载成功')
  } catch {
    ElMessage.error('下载日志失败')
  } finally {
    downloading.value = false
  }
}

// ── Tab切换 ────────────────────────────────────────────────────────────────────
const handleTabChange = (tabName) => {
  if (tabName === 'access') {
    stopPolling()
  } else if (tabName === 'app' && autoRefresh.value) {
    startPolling()
  }
}

const refreshCurrentTab = () => {
  if (activeTab.value === 'app') {
    refreshLogs()
  } else if (activeTab.value === 'access' && accessLogsRef.value) {
    accessLogsRef.value.refreshAll()
  }
}

onMounted(async () => {
  await refreshLogs()
  startPolling()
})

onUnmounted(() => {
  stopPolling()
})
</script>

<style scoped>
.logs-container {
  padding: 20px;
  max-width: 1400px;
  margin: 0 auto;
}

.header-card { margin-bottom: 20px; }

.header-content {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.header-content h2 { margin: 0; font-size: 24px; }

.actions { display: flex; gap: 10px; align-items: center; }

.poll-tag { font-size: 11px; }

.logs-tabs { margin-top: 0; }

@media (max-width: 768px) {
  .header-content { flex-direction: column; gap: 15px; align-items: flex-start; }
  .actions { width: 100%; }
}
</style>
