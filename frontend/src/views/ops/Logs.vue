<template>
  <div class="logs-container">
    <el-card class="header-card">
      <div class="header-content">
        <h2>日志管理</h2>
        <div class="actions">
          <el-button type="primary" @click="refreshLogs" :loading="loading">
            <el-icon><Refresh /></el-icon>
            刷新
          </el-button>
          <el-button type="success" @click="downloadLogs" :loading="downloading">
            <el-icon><Download /></el-icon>
            下载日志
          </el-button>
        </div>
      </div>
    </el-card>

    <el-card class="stats-card">
      <div class="stats-grid">
        <div class="stat-item">
          <div class="stat-label">总行数</div>
          <div class="stat-value">{{ stats.total || 0 }}</div>
        </div>
        <div class="stat-item error">
          <div class="stat-label">错误</div>
          <div class="stat-value">{{ stats.error || 0 }}</div>
        </div>
        <div class="stat-item warn">
          <div class="stat-label">警告</div>
          <div class="stat-value">{{ stats.warn || 0 }}</div>
        </div>
        <div class="stat-item info">
          <div class="stat-label">信息</div>
          <div class="stat-value">{{ stats.info || 0 }}</div>
        </div>
      </div>
    </el-card>

    <el-card class="filter-card">
      <el-form :inline="true">
        <el-form-item label="日志级别">
          <el-select v-model="filterLevel" @change="refreshLogs" placeholder="全部">
            <el-option label="全部" value=""></el-option>
            <el-option label="ERROR" value="ERROR"></el-option>
            <el-option label="WARN" value="WARN"></el-option>
            <el-option label="INFO" value="INFO"></el-option>
          </el-select>
        </el-form-item>
        <el-form-item label="显示行数">
          <el-select v-model="lines" @change="refreshLogs">
            <el-option label="100 行" :value="100"></el-option>
            <el-option label="500 行" :value="500"></el-option>
            <el-option label="1000 行" :value="1000"></el-option>
            <el-option label="2000 行" :value="2000"></el-option>
            <el-option label="5000 行" :value="5000"></el-option>
          </el-select>
        </el-form-item>
        <el-form-item label="搜索">
          <el-input
            v-model="searchText"
            placeholder="输入关键词过滤"
            clearable
            style="width: 300px"
          />
        </el-form-item>
        <el-form-item>
          <el-checkbox v-model="autoScroll">自动滚动到底部</el-checkbox>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card class="logs-card">
      <div class="logs-header">
        <span>日志内容 ({{ filteredLogs.length }} 行)</span>
        <span class="timestamp" v-if="lastUpdate">
          最后更新: {{ lastUpdate }}
        </span>
      </div>
      <div class="logs-content" ref="logsContent">
        <div
          v-for="(line, index) in filteredLogs"
          :key="index"
          :class="getLogLineClass(line)"
          class="log-line"
        >
          <span class="line-number">{{ index + 1 }}</span>
          <span class="line-content">{{ line }}</span>
        </div>
        <div v-if="filteredLogs.length === 0" class="empty-logs">
          暂无日志数据
        </div>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, nextTick, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh, Download } from '@element-plus/icons-vue'
import axios from 'axios'

const loading = ref(false)
const downloading = ref(false)
const logs = ref([])
const stats = ref({})
const filterLevel = ref('')
const lines = ref(500)
const searchText = ref('')
const autoScroll = ref(true)
const lastUpdate = ref('')
const logsContent = ref(null)

const filteredLogs = computed(() => {
  if (!searchText.value) return logs.value
  const keyword = searchText.value.toLowerCase()
  return logs.value.filter(line => line.toLowerCase().includes(keyword))
})

const getLogLineClass = (line) => {
  if (line.includes('ERROR')) return 'log-error'
  if (line.includes('WARN')) return 'log-warn'
  if (line.includes('INFO')) return 'log-info'
  return ''
}

const refreshLogs = async () => {
  loading.value = true
  try {
    const params = { lines: lines.value }
    if (filterLevel.value) {
      params.level = filterLevel.value
    }

    const response = await axios.get('/api/ops/logs/recent', { params })
    if (response.data.code === 200) {
      logs.value = response.data.data.lines || []
      lastUpdate.value = response.data.data.timestamp

      if (autoScroll.value) {
        await nextTick()
        scrollToBottom()
      }
    } else {
      ElMessage.error(response.data.message || '获取日志失败')
    }
  } catch (error) {
    console.error('获取日志失败:', error)
    ElMessage.error('获取日志失败')
  } finally {
    loading.value = false
  }
}

const loadStats = async () => {
  try {
    const response = await axios.get('/api/ops/logs/stats', {
      params: { lines: lines.value }
    })
    if (response.data.code === 200) {
      stats.value = response.data.data
    }
  } catch (error) {
    console.error('获取日志统计失败:', error)
  }
}

const downloadLogs = async () => {
  downloading.value = true
  try {
    const response = await axios.get('/api/ops/logs/download', {
      params: { lines: 5000 },
      responseType: 'blob'
    })

    const blob = new Blob([response.data], { type: 'text/plain' })
    const url = window.URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url

    const filename = response.headers['content-disposition']
      ?.split('filename=')[1]
      ?.replace(/"/g, '') || 'langhack-backend.log'

    link.download = filename
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    window.URL.revokeObjectURL(url)

    ElMessage.success('日志下载成功')
  } catch (error) {
    console.error('下载日志失败:', error)
    ElMessage.error('下载日志失败')
  } finally {
    downloading.value = false
  }
}

const scrollToBottom = () => {
  if (logsContent.value) {
    logsContent.value.scrollTop = logsContent.value.scrollHeight
  }
}

watch(lines, () => {
  loadStats()
})

onMounted(() => {
  refreshLogs()
  loadStats()
})
</script>

<style scoped>
.logs-container {
  padding: 20px;
  max-width: 1400px;
  margin: 0 auto;
}

.header-card {
  margin-bottom: 20px;
}

.header-content {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.header-content h2 {
  margin: 0;
  font-size: 24px;
}

.actions {
  display: flex;
  gap: 10px;
}

.stats-card {
  margin-bottom: 20px;
}

.stats-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 20px;
}

.stat-item {
  text-align: center;
  padding: 15px;
  border-radius: 8px;
  background: #f5f7fa;
}

.stat-item.error {
  background: #fef0f0;
}

.stat-item.warn {
  background: #fdf6ec;
}

.stat-item.info {
  background: #f0f9ff;
}

.stat-label {
  font-size: 14px;
  color: #909399;
  margin-bottom: 8px;
}

.stat-value {
  font-size: 28px;
  font-weight: bold;
  color: #303133;
}

.stat-item.error .stat-value {
  color: #f56c6c;
}

.stat-item.warn .stat-value {
  color: #e6a23c;
}

.stat-item.info .stat-value {
  color: #409eff;
}

.filter-card {
  margin-bottom: 20px;
}

.logs-card {
  margin-bottom: 20px;
}

.logs-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 15px;
  padding-bottom: 10px;
  border-bottom: 1px solid #ebeef5;
  font-weight: bold;
}

.timestamp {
  font-size: 12px;
  color: #909399;
  font-weight: normal;
}

.logs-content {
  background: #1e1e1e;
  color: #d4d4d4;
  padding: 15px;
  border-radius: 4px;
  font-family: 'Consolas', 'Monaco', 'Courier New', monospace;
  font-size: 13px;
  line-height: 1.6;
  max-height: 600px;
  overflow-y: auto;
}

.log-line {
  display: flex;
  padding: 2px 0;
  word-break: break-all;
}

.line-number {
  color: #858585;
  min-width: 50px;
  text-align: right;
  padding-right: 15px;
  user-select: none;
  flex-shrink: 0;
}

.line-content {
  flex: 1;
}

.log-error {
  color: #f48771;
  background: rgba(244, 135, 113, 0.1);
}

.log-warn {
  color: #dcdcaa;
  background: rgba(220, 220, 170, 0.1);
}

.log-info {
  color: #4ec9b0;
}

.empty-logs {
  text-align: center;
  color: #858585;
  padding: 40px;
}

@media (max-width: 768px) {
  .stats-grid {
    grid-template-columns: repeat(2, 1fr);
  }

  .header-content {
    flex-direction: column;
    gap: 15px;
    align-items: flex-start;
  }

  .actions {
    width: 100%;
  }

  .actions button {
    flex: 1;
  }
}
</style>
