<template>
  <div class="monitoring-dashboard">
    <div class="header">
      <h2>系统监控</h2>
      <el-button @click="refreshAll" :loading="refreshing">
        <el-icon><Refresh /></el-icon> 刷新
      </el-button>
    </div>

    <!-- 区域1: 实时指标卡片 -->
    <div class="stats-cards">
      <div class="stat-card">
        <div class="stat-value">
          <span class="pulse-dot" v-if="realtime.activeUsers > 0"></span>
          {{ realtime.activeUsers || 0 }}
        </div>
        <div class="stat-label">活跃用户(5min)</div>
      </div>
      <div class="stat-card">
        <div class="stat-value">{{ realtime.todayRequests || 0 }}</div>
        <div class="stat-label">今日请求</div>
      </div>
      <div class="stat-card">
        <div class="stat-value">{{ realtime.todayUniqueUsers || 0 }}</div>
        <div class="stat-label">独立用户</div>
      </div>
      <div class="stat-card">
        <div class="stat-value">{{ realtime.concurrentRequests || 0 }}</div>
        <div class="stat-label">当前并发</div>
      </div>
      <div class="stat-card">
        <div class="stat-value">{{ realtime.peakConcurrentToday || 0 }}</div>
        <div class="stat-label">峰值并发</div>
      </div>
      <div class="stat-card">
        <div class="stat-value" :style="{ color: errorRateColor }">
          {{ errorRate }}%
        </div>
        <div class="stat-label">错误率</div>
      </div>
      <div class="stat-card">
        <div class="stat-value">{{ formatUptime(system.uptimeMs) }}</div>
        <div class="stat-label">运行时长</div>
      </div>
    </div>

    <!-- 区域2: 服务器健康 -->
    <div class="section-title">服务器健康</div>
    <div class="health-bars">
      <div class="health-item">
        <span class="health-label">JVM 堆内存</span>
        <el-progress :percentage="system.jvm?.heapUsagePercent || 0"
                     :color="getProgressColor(system.jvm?.heapUsagePercent || 0)" />
        <span class="health-detail">{{ formatBytes(system.jvm?.heapUsed) }} / {{ formatBytes(system.jvm?.heapMax) }}</span>
      </div>
      <div class="health-item">
        <span class="health-label">CPU 使用率</span>
        <el-progress :percentage="system.cpu?.processCpuLoad >= 0 ? system.cpu.processCpuLoad : 0"
                     :color="getProgressColor(system.cpu?.processCpuLoad || 0)" />
      </div>
      <div class="health-item">
        <span class="health-label">数据库连接池</span>
        <el-progress :percentage="dbPoolPercent"
                     :color="getProgressColor(dbPoolPercent)"
                     :format="() => dbPoolActive + ' 活跃 / ' + dbPoolMax + ' 最大'" />
        <span class="health-detail">主库 {{ system.dbPool?.master?.active || 0 }}/{{ system.dbPool?.master?.max || 0 }} 从库 {{ system.dbPool?.slave?.active || 0 }}/{{ system.dbPool?.slave?.max || 0 }}</span>
      </div>
      <div class="health-item">
        <span class="health-label">
          Redis 缓存
          <span class="redis-dot" :class="system.redis?.connected ? 'status-green' : 'status-red'"></span>
        </span>
        <el-progress :percentage="system.redis?.usagePercent || 0"
                     :color="getProgressColor(system.redis?.usagePercent || 0)" />
        <span class="health-detail">已用 {{ formatBytes(system.redis?.usedMemory) }} / 最大 {{ formatBytes(system.redis?.maxMemory) }}</span>
      </div>
      <div class="health-item">
        <span class="health-label">磁盘使用</span>
        <el-progress :percentage="system.disk?.usagePercent || 0"
                     :color="getProgressColor(system.disk?.usagePercent || 0)" />
        <span class="health-detail">剩余 {{ formatBytes(system.disk?.free) }}</span>
      </div>
    </div>

    <!-- 区域3: AI 服务监控 -->
    <div class="section-title">AI / 外部服务</div>
    <div class="ai-service-cards">
      <div class="ai-card" v-for="(svc, name) in aiStats" :key="name">
        <div class="ai-card-header">
          <span class="service-indicator" :class="getServiceStatusClass(svc.successRate)"></span>
          <span class="ai-service-name">{{ formatServiceName(name) }}</span>
        </div>
        <div class="ai-card-body">
          <div class="ai-metric">
            <span class="ai-metric-value">{{ svc.totalCalls || 0 }}</span>
            <span class="ai-metric-label">今日调用</span>
          </div>
          <div class="ai-metric">
            <span class="ai-metric-value" :style="{ color: getSuccessRateColor(svc.successRate) }">
              {{ svc.successRate ?? 100 }}%
            </span>
            <span class="ai-metric-label">成功率</span>
          </div>
          <div class="ai-metric">
            <span class="ai-metric-value">{{ svc.avgResponseTimeMs || 0 }}ms</span>
            <span class="ai-metric-label">平均耗时</span>
          </div>
        </div>
      </div>
    </div>

    <!-- AI调用趋势图 -->
    <div class="charts-row" v-if="aiDailyData.length > 0">
      <div class="chart-container">
        <div class="chart-title">AI 服务每日调用量</div>
        <v-chart :option="aiCallsChartOption" style="height: 300px;" autoresize />
      </div>
    </div>

    <!-- 最近AI调用日志 -->
    <div class="section-title" style="margin-top: 16px;">
      最近 AI 调用日志
      <el-select v-model="aiLogFilter" placeholder="全部服务" clearable size="small"
                 style="width: 160px; margin-left: 12px;" @change="loadAiCallsRecent">
        <el-option v-for="s in serviceNames" :key="s" :label="formatServiceName(s)" :value="s" />
      </el-select>
      <el-select v-model="aiLogStatusFilter" placeholder="全部状态" clearable size="small"
                 style="width: 120px; margin-left: 8px;">
        <el-option label="仅失败" :value="false" />
        <el-option label="仅成功" :value="true" />
      </el-select>
    </div>
    <el-table :data="filteredAiLogs" size="small" stripe border max-height="350" style="margin-bottom: 16px;">
      <el-table-column prop="createdTime" label="时间" width="160" />
      <el-table-column prop="serviceName" label="服务" width="130">
        <template #default="{ row }">{{ formatServiceName(row.serviceName) }}</template>
      </el-table-column>
      <el-table-column prop="methodName" label="方法" width="180" show-overflow-tooltip />
      <el-table-column prop="responseTimeMs" label="耗时" width="80">
        <template #default="{ row }">{{ row.responseTimeMs }}ms</template>
      </el-table-column>
      <el-table-column prop="success" label="状态" width="80">
        <template #default="{ row }">
          <el-tag :type="row.success ? 'success' : 'danger'" size="small">
            {{ row.success ? '成功' : '失败' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="triggeredBy" label="用户" width="100" />
      <el-table-column prop="errorMessage" label="错误信息" show-overflow-tooltip />
    </el-table>

    <!-- 区域4: 访问趋势 -->
    <div class="section-title">
      访问趋势
      <el-radio-group v-model="chartTimeRange" size="small" style="margin-left: 12px;" @change="loadChartData">
        <el-radio-button label="24h">24小时</el-radio-button>
        <el-radio-button label="30d">30天</el-radio-button>
      </el-radio-group>
    </div>
    <div class="charts-row">
      <div class="chart-container">
        <div class="chart-title">请求量</div>
        <v-chart :option="requestChartOption" style="height: 280px;" autoresize />
      </div>
      <div class="chart-container">
        <div class="chart-title">响应时间</div>
        <v-chart :option="responseTimeChartOption" style="height: 280px;" autoresize />
      </div>
    </div>

    <!-- 区域5: 热门内容 -->
    <div class="section-title" style="margin-top: 16px;">热门页面</div>
    <el-table :data="topPages" size="small" stripe border max-height="300">
      <el-table-column type="index" label="#" width="50" />
      <el-table-column prop="uri" label="URI" show-overflow-tooltip />
      <el-table-column prop="count" label="访问次数" width="120" />
    </el-table>

    <!-- 区域6: 后端应用日志 -->
    <div class="section-title" style="margin-top: 16px;">
      后端日志
      <div class="log-toolbar">
        <div class="log-level-badges">
          <el-badge :value="appLogLevelCounts.ERROR || 0" :type="appLogLevelCounts.ERROR > 0 ? 'danger' : 'info'" :hidden="!appLogLevelCounts.ERROR">
            <el-button size="small" :type="appLogLevelFilter === 'ERROR' ? 'danger' : ''"
                       @click="appLogLevelFilter = appLogLevelFilter === 'ERROR' ? '' : 'ERROR'">ERROR</el-button>
          </el-badge>
          <el-badge :value="appLogLevelCounts.WARN || 0" :type="appLogLevelCounts.WARN > 0 ? 'warning' : 'info'" :hidden="!appLogLevelCounts.WARN">
            <el-button size="small" :type="appLogLevelFilter === 'WARN' ? 'warning' : ''"
                       @click="appLogLevelFilter = appLogLevelFilter === 'WARN' ? '' : 'WARN'">WARN</el-button>
          </el-badge>
          <el-button size="small" :type="appLogLevelFilter === 'INFO' ? 'primary' : ''"
                     @click="appLogLevelFilter = appLogLevelFilter === 'INFO' ? '' : 'INFO'">INFO</el-button>
        </div>
        <el-input v-model="appLogKeyword" placeholder="搜索关键词" clearable size="small"
                  style="width: 200px;" @keyup.enter="loadAppLogs" @clear="loadAppLogs" />
        <el-button size="small" @click="loadAppLogs" :loading="appLogsLoading">
          <el-icon><Refresh /></el-icon>
        </el-button>
      </div>
    </div>
    <div class="app-log-container" v-loading="appLogsLoading">
      <div v-if="appLogEntries.length === 0" class="log-empty">暂无日志</div>
      <div v-for="(entry, idx) in appLogEntries" :key="idx" class="log-entry" :class="'log-' + (entry.level || '').toLowerCase()">
        <span class="log-time">{{ entry.timestamp }}</span>
        <span class="log-level-tag" :class="'level-' + (entry.level || '').toLowerCase()">{{ entry.level }}</span>
        <span class="log-logger">{{ entry.logger }}</span>
        <span class="log-msg">{{ entry.message }}</span>
        <div v-if="entry.exception" class="log-exception">{{ entry.exception }}</div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, watch, onMounted, onBeforeUnmount } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import VChart from 'vue-echarts'
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { LineChart, BarChart } from 'echarts/charts'
import {
  TitleComponent, TooltipComponent, LegendComponent,
  GridComponent, DataZoomComponent
} from 'echarts/components'
import { getToken } from '@/utils/auth'
import { environment } from '@/utils/environment'
import {
  getRealTimeMetrics, getSystemMetrics, getHourlyStats, getDailyStats,
  getTopPages, getAiCallsRealtime, getAiCallsDaily, getAiCallsRecent,
  getApplicationLogs
} from '@/api/monitoring'

use([
  CanvasRenderer, LineChart, BarChart,
  TitleComponent, TooltipComponent, LegendComponent, GridComponent, DataZoomComponent
])

// State
const refreshing = ref(false)
const realtime = reactive({ activeUsers: 0, todayRequests: 0, todayUniqueUsers: 0, concurrentRequests: 0, peakConcurrentToday: 0, todayErrorCount: 0 })
const system = reactive({ jvm: {}, cpu: {}, dbPool: {}, redis: {}, disk: {}, uptimeMs: 0 })
const aiStats = ref({})
const aiDailyData = ref([])
const aiRecentLogs = ref([])
const aiLogFilter = ref('')
const aiLogStatusFilter = ref('')
const chartTimeRange = ref('24h')
const hourlyData = ref([])
const dailyData = ref([])
const topPages = ref([])
const appLogEntries = ref([])
const appLogLevelCounts = ref({})
const appLogLevelFilter = ref('')
const appLogKeyword = ref('')
const appLogsLoading = ref(false)
let eventSource = null
let reconnectTimer = null

const serviceNames = computed(() => Object.keys(aiStats.value))

const filteredAiLogs = computed(() => {
  let logs = aiRecentLogs.value
  if (aiLogFilter.value) {
    logs = logs.filter(l => l.serviceName === aiLogFilter.value)
  }
  if (aiLogStatusFilter.value !== '' && aiLogStatusFilter.value !== null) {
    logs = logs.filter(l => l.success === aiLogStatusFilter.value)
  }
  return logs
})

const normalizeTopPageRows = (rows) => {
  if (!Array.isArray(rows)) return []

  return rows
    .map((row) => {
      if (!row || typeof row !== 'object') return null

      const uri = row.uri || row.path || ''
      const countValue = row.count ?? row.visitCount ?? row.visit_count ?? 0
      const count = Number.isFinite(Number(countValue)) ? Number(countValue) : 0

      if (!uri) return null

      return {
        ...row,
        uri,
        count
      }
    })
    .filter(Boolean)
}

// DB pool computed
const dbPoolActive = computed(() =>
  (system.dbPool?.master?.active || 0) + (system.dbPool?.slave?.active || 0)
)
const dbPoolMax = computed(() =>
  (system.dbPool?.master?.max || 0) + (system.dbPool?.slave?.max || 0)
)
const dbPoolPercent = computed(() => {
  const max = dbPoolMax.value
  return max > 0 ? Math.round(dbPoolActive.value / max * 100) : 0
})

// Error rate computed
const errorRate = computed(() => {
  const total = realtime.todayRequests || 0
  const errors = realtime.todayErrorCount || 0
  return total > 0 ? (errors / total * 100).toFixed(2) : '0.00'
})
const errorRateColor = computed(() => {
  const rate = parseFloat(errorRate.value)
  if (rate > 5) return '#F56C6C'
  if (rate > 1) return '#E6A23C'
  return '#67C23A'
})

// Chart options
const requestChartOption = computed(() => {
  const data = chartTimeRange.value === '24h' ? hourlyData.value : dailyData.value
  const xField = chartTimeRange.value === '24h' ? 'statHour' : 'statDate'
  return {
    tooltip: { trigger: 'axis' },
    grid: { left: 50, right: 20, top: 10, bottom: 30 },
    xAxis: { type: 'category', data: data.map(d => formatTimeLabel(d[xField])) },
    yAxis: { type: 'value' },
    series: [
      { name: '请求数', type: 'line', data: data.map(d => d.totalRequests), smooth: true, areaStyle: { opacity: 0.15 } },
      { name: '错误数', type: 'line', data: data.map(d => d.errorCount), smooth: true, lineStyle: { color: '#F56C6C' }, itemStyle: { color: '#F56C6C' } }
    ]
  }
})

const responseTimeChartOption = computed(() => {
  const data = chartTimeRange.value === '24h' ? hourlyData.value : dailyData.value
  const xField = chartTimeRange.value === '24h' ? 'statHour' : 'statDate'
  return {
    tooltip: { trigger: 'axis' },
    grid: { left: 50, right: 20, top: 10, bottom: 30 },
    xAxis: { type: 'category', data: data.map(d => formatTimeLabel(d[xField])) },
    yAxis: { type: 'value', name: 'ms' },
    series: [
      { name: '平均', type: 'line', data: data.map(d => d.avgResponseTimeMs), smooth: true, areaStyle: { opacity: 0.1 } },
      { name: '最大', type: 'line', data: data.map(d => d.maxResponseTimeMs), smooth: true, lineStyle: { type: 'dashed' } }
    ]
  }
})

const aiCallsChartOption = computed(() => {
  if (!aiDailyData.value.length) return {}
  // Group by service
  const dateSet = new Set()
  const serviceData = {}
  for (const row of aiDailyData.value) {
    dateSet.add(row.statDate)
    if (!serviceData[row.serviceName]) serviceData[row.serviceName] = {}
    serviceData[row.serviceName][row.statDate] = row.totalCalls || 0
  }
  const dates = [...dateSet].sort()
  const palette = ['#409EFF', '#67C23A', '#E6A23C', '#F56C6C', '#909399', '#9B59B6', '#1ABC9C', '#E67E22']
  const allSvcs = [...new Set(aiDailyData.value.map(r => r.serviceName))]
  const colors = Object.fromEntries(allSvcs.map((s, i) => [s, palette[i % palette.length]]))
  const series = Object.keys(serviceData).map(svc => ({
    name: formatServiceName(svc),
    type: 'bar',
    stack: 'total',
    data: dates.map(d => serviceData[svc][d] || 0),
    itemStyle: { color: colors[svc] || '#409EFF' }
  }))
  return {
    tooltip: { trigger: 'axis' },
    legend: { bottom: 0 },
    grid: { left: 50, right: 20, top: 10, bottom: 40 },
    xAxis: { type: 'category', data: dates },
    yAxis: { type: 'value' },
    series
  }
})

// SSE connection
const connectSSE = () => {
  const token = getToken()
  if (!token) return

  const streamUrl = environment.resolveApiUrl('/api/ops/monitoring/realtime/stream')
  eventSource = new EventSource(`${streamUrl}?token=${encodeURIComponent(token)}`)

  eventSource.onmessage = (event) => {
    try {
      const data = JSON.parse(event.data)
      if (data.realtime) {
        Object.assign(realtime, data.realtime)
      }
      if (data.system) {
        Object.assign(system, data.system)
      }
    } catch (e) {
      console.debug('SSE parse error', e)
    }
  }

  eventSource.onerror = () => {
    if (eventSource) {
      eventSource.close()
      eventSource = null
    }
    if (reconnectTimer) {
      clearTimeout(reconnectTimer)
    }
    // Reconnect after 5 seconds
    reconnectTimer = setTimeout(() => {
      if (!eventSource) connectSSE()
      reconnectTimer = null
    }, 5000)
  }
}

// Data loading
const loadChartData = async () => {
  try {
    if (chartTimeRange.value === '24h') {
      const res = await getHourlyStats(24)
      hourlyData.value = res.code === 200 ? (res.data || []) : []
    } else {
      const res = await getDailyStats(30)
      dailyData.value = res.code === 200 ? (res.data || []) : []
    }
  } catch { /* ignore */ }
}

const loadTopPages = async () => {
  try {
    const res = await getTopPages()
    if (res.code === 200) {
      const data = res.data
      if (typeof data === 'string') {
        try {
          topPages.value = normalizeTopPageRows(JSON.parse(data))
        } catch {
          topPages.value = []
        }
      } else {
        topPages.value = normalizeTopPageRows(data)
      }
    }
  } catch { topPages.value = [] }
}

const loadAiStats = async () => {
  try {
    const res = await getAiCallsRealtime()
    if (res.code === 200) aiStats.value = res.data || {}
  } catch { /* ignore */ }
}

const loadAiDailyData = async () => {
  try {
    const res = await getAiCallsDaily(30)
    if (res.code === 200) aiDailyData.value = res.data || []
  } catch { /* ignore */ }
}

const loadAiCallsRecent = async () => {
  try {
    const res = await getAiCallsRecent(50)
    if (res.code === 200) aiRecentLogs.value = res.data || []
  } catch { /* ignore */ }
}

const loadAppLogs = async () => {
  appLogsLoading.value = true
  try {
    const params = { limit: 500 }
    if (appLogLevelFilter.value) params.level = appLogLevelFilter.value
    if (appLogKeyword.value) params.keyword = appLogKeyword.value
    const res = await getApplicationLogs(params)
    if (res.code === 200 && res.data) {
      appLogEntries.value = res.data.entries || []
      appLogLevelCounts.value = res.data.levelCounts || {}
    }
  } catch { /* ignore */ }
  finally { appLogsLoading.value = false }
}

// 切换日志级别过滤时重新加载
watch(appLogLevelFilter, () => loadAppLogs())

const refreshAll = async () => {
  refreshing.value = true
  try {
    await Promise.all([
      loadChartData(), loadTopPages(), loadAiStats(),
      loadAiDailyData(), loadAiCallsRecent(), loadAppLogs()
    ])
    // Always fetch a snapshot to avoid empty cards before the first SSE message arrives.
    try {
      const [rtRes, sysRes] = await Promise.all([getRealTimeMetrics(), getSystemMetrics()])
      if (rtRes.code === 200) Object.assign(realtime, rtRes.data)
      if (sysRes.code === 200) Object.assign(system, sysRes.data)
    } catch { /* ignore */ }
  } catch {
    ElMessage.error('刷新失败')
  } finally {
    refreshing.value = false
  }
}

// Formatters
const formatServiceName = (name) => {
  const map = { HIAGENT: 'HiAgent', OPENROUTER: 'OpenRouter', TENCENT_TRANSLATE: '腾讯翻译', TENCENT_ASR: '腾讯ASR', BILIBILI: 'Bilibili' }
  return map[name] || name
}

const formatBytes = (bytes) => {
  if (!bytes || bytes <= 0) return '0 B'
  const units = ['B', 'KB', 'MB', 'GB']
  let i = 0
  let val = bytes
  while (val >= 1024 && i < units.length - 1) { val /= 1024; i++ }
  return val.toFixed(i > 0 ? 1 : 0) + ' ' + units[i]
}

const formatUptime = (ms) => {
  if (!ms) return '-'
  const hours = Math.floor(ms / 3600000)
  const minutes = Math.floor((ms % 3600000) / 60000)
  if (hours >= 24) return Math.floor(hours / 24) + 'd ' + (hours % 24) + 'h'
  return hours + 'h ' + minutes + 'm'
}

const formatTimeLabel = (val) => {
  if (!val) return ''
  if (Array.isArray(val)) {
    if (val.length >= 5) {
      const hour = String(val[3] ?? 0).padStart(2, '0')
      const minute = String(val[4] ?? 0).padStart(2, '0')
      return `${hour}:${minute}`
    }
    if (val.length >= 3) {
      const month = String(val[1] ?? 0).padStart(2, '0')
      const day = String(val[2] ?? 0).padStart(2, '0')
      return `${month}-${day}`
    }
  }
  if (typeof val === 'string' && val.length >= 13) return val.substring(11, 16)
  if (typeof val === 'string' && val.length === 10) return val.substring(5)
  return String(val)
}

const getProgressColor = (pct) => {
  if (pct >= 90) return '#F56C6C'
  if (pct >= 70) return '#E6A23C'
  return '#67C23A'
}

const getServiceStatusClass = (rate) => {
  if (rate == null || rate >= 90) return 'status-green'
  if (rate >= 70) return 'status-yellow'
  return 'status-red'
}

const getSuccessRateColor = (rate) => {
  if (rate == null || rate >= 90) return '#67C23A'
  if (rate >= 70) return '#E6A23C'
  return '#F56C6C'
}

// Lifecycle
onMounted(() => {
  connectSSE()
  refreshAll()
})

onBeforeUnmount(() => {
  if (eventSource) {
    eventSource.close()
    eventSource = null
  }
  if (reconnectTimer) {
    clearTimeout(reconnectTimer)
    reconnectTimer = null
  }
})
</script>

<style scoped>
.monitoring-dashboard {
  padding: 0;
}

.header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.section-title {
  font-size: 16px;
  font-weight: 600;
  margin: 16px 0 12px;
  display: flex;
  align-items: center;
}

.stats-cards {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(140px, 1fr));
  gap: 12px;
  margin-bottom: 16px;
}

.stat-card {
  background: #fff;
  border: 1px solid #ebeef5;
  border-radius: 8px;
  padding: 16px;
  text-align: center;
}

.stat-value {
  font-size: 24px;
  font-weight: 700;
  color: #303133;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
}

.stat-label {
  font-size: 13px;
  color: #909399;
  margin-top: 4px;
}

.pulse-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #67C23A;
  animation: pulse 1.5s infinite;
}

@keyframes pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.3; }
}

.health-bars {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
  gap: 12px;
  margin-bottom: 16px;
}

.health-item {
  background: #fff;
  border: 1px solid #ebeef5;
  border-radius: 6px;
  padding: 12px 16px;
}

.health-label {
  font-size: 13px;
  font-weight: 500;
  color: #606266;
  display: block;
  margin-bottom: 6px;
}

.health-detail {
  font-size: 12px;
  color: #909399;
  margin-top: 4px;
  display: block;
}

.ai-service-cards {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: 12px;
  margin-bottom: 16px;
}

.ai-card {
  background: #fff;
  border: 1px solid #ebeef5;
  border-radius: 8px;
  padding: 14px;
}

.ai-card-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
}

.service-indicator {
  width: 10px;
  height: 10px;
  border-radius: 50%;
}

.status-green { background: #67C23A; }
.status-yellow { background: #E6A23C; }
.status-red { background: #F56C6C; }

.redis-dot {
  display: inline-block;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  margin-left: 6px;
  vertical-align: middle;
}

.ai-service-name {
  font-weight: 600;
  font-size: 14px;
  color: #303133;
}

.ai-card-body {
  display: flex;
  justify-content: space-between;
}

.ai-metric {
  text-align: center;
}

.ai-metric-value {
  font-size: 16px;
  font-weight: 700;
  display: block;
}

.ai-metric-label {
  font-size: 11px;
  color: #909399;
}

.charts-row {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(400px, 1fr));
  gap: 16px;
}

.chart-container {
  background: #fff;
  border: 1px solid #ebeef5;
  border-radius: 8px;
  padding: 12px;
}

.chart-title {
  font-size: 14px;
  font-weight: 500;
  color: #606266;
  margin-bottom: 8px;
}

@media (max-width: 768px) {
  .stats-cards {
    grid-template-columns: repeat(3, 1fr);
  }
  .charts-row {
    grid-template-columns: 1fr;
  }
  .ai-service-cards {
    grid-template-columns: repeat(2, 1fr);
  }
}

/* 后端日志区域 */
.log-toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-left: auto;
}

.log-level-badges {
  display: flex;
  gap: 6px;
}

.app-log-container {
  background: #1e1e1e;
  border-radius: 8px;
  padding: 12px;
  max-height: 500px;
  overflow-y: auto;
  font-family: 'Cascadia Code', 'JetBrains Mono', 'Fira Code', Consolas, monospace;
  font-size: 12px;
  line-height: 1.6;
  margin-bottom: 16px;
}

.log-empty {
  color: #909399;
  text-align: center;
  padding: 24px;
}

.log-entry {
  padding: 2px 4px;
  border-radius: 2px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.log-entry:hover {
  white-space: normal;
  word-break: break-all;
  background: rgba(255, 255, 255, 0.05);
}

.log-entry.log-error {
  color: #F56C6C;
}

.log-entry.log-warn {
  color: #E6A23C;
}

.log-entry.log-info {
  color: #d4d4d4;
}

.log-time {
  color: #6A9955;
  margin-right: 8px;
}

.log-level-tag {
  display: inline-block;
  width: 44px;
  text-align: center;
  font-weight: 600;
  margin-right: 8px;
  font-size: 11px;
}

.level-error {
  color: #F56C6C;
}

.level-warn {
  color: #E6A23C;
}

.level-info {
  color: #569CD6;
}

.log-logger {
  color: #9CDCFE;
  margin-right: 8px;
}

.log-msg {
  color: #d4d4d4;
}

.log-exception {
  color: #F56C6C;
  padding-left: 120px;
  font-style: italic;
}
</style>
