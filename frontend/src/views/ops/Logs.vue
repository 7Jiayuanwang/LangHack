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
          <div class="stat-value">{{ filteredEntries.length }}</div>
        </div>
        <div class="stat-item error">
          <div class="stat-label">错误</div>
          <div class="stat-value">{{ levelCounts.ERROR || 0 }}</div>
        </div>
        <div class="stat-item warn">
          <div class="stat-label">警告</div>
          <div class="stat-value">{{ levelCounts.WARN || 0 }}</div>
        </div>
        <div class="stat-item info">
          <div class="stat-label">信息</div>
          <div class="stat-value">{{ levelCounts.INFO || 0 }}</div>
        </div>
      </div>
    </el-card>

    <el-card class="filter-card">
      <el-form :inline="true">
        <el-form-item label="日志级别">
          <el-select v-model="filterLevel" @change="refreshLogs" placeholder="全部" style="width: 100px">
            <el-option label="全部" value=""></el-option>
            <el-option label="ERROR" value="ERROR"></el-option>
            <el-option label="WARN" value="WARN"></el-option>
            <el-option label="INFO" value="INFO"></el-option>
          </el-select>
        </el-form-item>
        <el-form-item label="显示条数">
          <el-select v-model="limit" @change="refreshLogs" style="width: 100px">
            <el-option label="100 条" :value="100"></el-option>
            <el-option label="500 条" :value="500"></el-option>
            <el-option label="1000 条" :value="1000"></el-option>
            <el-option label="2000 条" :value="2000"></el-option>
          </el-select>
        </el-form-item>
        <el-form-item label="日期范围">
          <el-date-picker
            v-model="dateRange"
            type="daterange"
            range-separator="至"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            value-format="YYYY-MM-DD"
            style="width: 240px"
          />
        </el-form-item>
        <el-form-item label="搜索">
          <el-input
            v-model="searchText"
            placeholder="输入关键词过滤"
            clearable
            style="width: 240px"
          />
        </el-form-item>
        <el-form-item>
          <el-checkbox v-model="autoScroll">自动滚动到底部</el-checkbox>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card class="logs-card">
      <div class="logs-header">
        <span>日志内容 ({{ filteredEntries.length }} 条)</span>
        <span class="timestamp" v-if="lastUpdate">最后更新: {{ lastUpdate }}</span>
      </div>
      <div class="logs-content" ref="logsContent">
        <div
          v-for="(entry, index) in filteredEntries"
          :key="index"
          class="log-entry"
          :class="'log-' + (entry.level || '').toLowerCase()"
        >
          <span class="log-time">{{ entry.timestamp }}</span>
          <span class="log-level-tag" :class="'level-' + (entry.level || '').toLowerCase()">{{ entry.level }}</span>
          <span class="log-logger">{{ entry.logger }}</span>
          <span class="log-msg">{{ entry.message }}</span>
          <div v-if="entry.exception" class="log-exception">{{ entry.exception }}</div>
        </div>
        <div v-if="filteredEntries.length === 0" class="empty-logs">暂无日志数据</div>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh, Download } from '@element-plus/icons-vue'
import { getApplicationLogs, downloadApplicationLogs } from '@/api/ops'

const loading = ref(false)
const downloading = ref(false)
const entries = ref([])
const levelCounts = ref({})
const filterLevel = ref('')
const limit = ref(500)
const searchText = ref('')
const dateRange = ref(null)
const autoScroll = ref(true)
const lastUpdate = ref('')
const logsContent = ref(null)

const filteredEntries = computed(() => {
  let list = entries.value
  if (searchText.value) {
    const kw = searchText.value.toLowerCase()
    list = list.filter(e =>
      (e.message || '').toLowerCase().includes(kw) ||
      (e.logger || '').toLowerCase().includes(kw)
    )
  }
  if (dateRange.value && dateRange.value[0]) {
    const [start, end] = dateRange.value
    list = list.filter(e => {
      if (!e.timestamp) return true
      const d = e.timestamp.slice(0, 10)
      return d >= start && d <= end
    })
  }
  return list
})

const refreshLogs = async () => {
  loading.value = true
  try {
    const params = { limit: limit.value }
    if (filterLevel.value) params.level = filterLevel.value
    const res = await getApplicationLogs(params)
    if (res.code === 200) {
      entries.value = res.data.entries || []
      levelCounts.value = res.data.levelCounts || {}
      lastUpdate.value = new Date().toLocaleTimeString()
      if (autoScroll.value) {
        await nextTick()
        if (logsContent.value) logsContent.value.scrollTop = logsContent.value.scrollHeight
      }
    } else {
      ElMessage.error(res.message || '获取日志失败')
    }
  } catch {
    ElMessage.error('获取日志失败')
  } finally {
    loading.value = false
  }
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
    const res = await downloadApplicationLogs(params)
    const blob = new Blob([res], { type: 'text/plain' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `langhack-${new Date().toISOString().slice(0, 10)}.log`
    document.body.appendChild(a)
    a.click()
    document.body.removeChild(a)
    URL.revokeObjectURL(url)
    ElMessage.success('日志下载成功')
  } catch {
    ElMessage.error('下载日志失败')
  } finally {
    downloading.value = false
  }
}

onMounted(refreshLogs)
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

.actions { display: flex; gap: 10px; }

.stats-card { margin-bottom: 20px; }

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

.stat-item.error { background: #fef0f0; }
.stat-item.warn  { background: #fdf6ec; }
.stat-item.info  { background: #f0f9ff; }

.stat-label { font-size: 14px; color: #909399; margin-bottom: 8px; }

.stat-value { font-size: 28px; font-weight: bold; color: #303133; }
.stat-item.error .stat-value { color: #f56c6c; }
.stat-item.warn  .stat-value { color: #e6a23c; }
.stat-item.info  .stat-value { color: #409eff; }

.filter-card { margin-bottom: 20px; }
.logs-card   { margin-bottom: 20px; }

.logs-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 15px;
  padding-bottom: 10px;
  border-bottom: 1px solid #ebeef5;
  font-weight: bold;
}

.timestamp { font-size: 12px; color: #909399; font-weight: normal; }

.logs-content {
  background: #0f172a;
  border-radius: 8px;
  padding: 12px 16px;
  font-family: 'JetBrains Mono', 'Fira Code', 'Consolas', monospace;
  font-size: 13px;
  line-height: 1.7;
  max-height: 600px;
  overflow-y: auto;
}

.log-entry {
  padding: 3px 6px;
  border-radius: 4px;
  margin-bottom: 2px;
  word-break: break-all;
}

.log-entry:hover { background: rgba(255,255,255,0.05); }

.log-time {
  color: #6366f1;
  margin-right: 10px;
  opacity: 0.85;
  flex-shrink: 0;
}

.log-level-tag {
  display: inline-block;
  min-width: 48px;
  text-align: center;
  font-weight: 800;
  font-size: 11px;
  padding: 0 4px;
  border-radius: 3px;
  margin-right: 10px;
}

.level-error { color: #fca5a5; border: 1px solid rgba(248,113,113,0.3); background: rgba(248,113,113,0.1); }
.level-warn  { color: #fde68a; border: 1px solid rgba(251,191,36,0.3);  background: rgba(251,191,36,0.1);  }
.level-info  { color: #93c5fd; border: 1px solid rgba(59,130,246,0.3);  background: rgba(59,130,246,0.1);  }

.log-logger { color: #38bdf8; margin-right: 10px; opacity: 0.9; }
.log-msg    { color: #e2e8f0; }

.log-exception {
  color: #fca5a5;
  padding-left: 130px;
  font-size: 12px;
  margin-top: 2px;
  opacity: 0.9;
}

.empty-logs { text-align: center; color: #64748b; padding: 40px; }

@media (max-width: 768px) {
  .stats-grid { grid-template-columns: repeat(2, 1fr); }
  .header-content { flex-direction: column; gap: 15px; align-items: flex-start; }
  .actions { width: 100%; }
}
</style>
