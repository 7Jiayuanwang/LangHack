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
        <span class="health-label">线程数</span>
        <el-progress :percentage="Math.min(100, Math.round((system.threads?.count || 0) / 500 * 100))"
                     :format="() => (system.threads?.count || 0) + ' / 峰值 ' + (system.threads?.peakCount || 0)" />
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
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onBeforeUnmount } from 'vue'
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
import {
  getRealTimeMetrics, getSystemMetrics, getHourlyStats, getDailyStats,
  getTopPages, getAiCallsRealtime, getAiCallsDaily, getAiCallsRecent
} from '@/api/monitoring'

use([
  CanvasRenderer, LineChart, BarChart,
  TitleComponent, TooltipComponent, LegendComponent, GridComponent, DataZoomComponent
])

// State
const refreshing = ref(false)
const realtime = reactive({ activeUsers: 0, todayRequests: 0, todayUniqueUsers: 0, concurrentRequests: 0, peakConcurrentToday: 0 })
const system = reactive({ jvm: {}, cpu: {}, threads: {}, disk: {}, uptimeMs: 0 })
const aiStats = ref({})
const aiDailyData = ref([])
const aiRecentLogs = ref([])
const aiLogFilter = ref('')
const chartTimeRange = ref('24h')
const hourlyData = ref([])
const dailyData = ref([])
const topPages = ref([])
let eventSource = null

const serviceNames = ['HIAGENT', 'OPENROUTER', 'TENCENT_TRANSLATE', 'TENCENT_ASR', 'BILIBILI']

const filteredAiLogs = computed(() => {
  if (!aiLogFilter.value) return aiRecentLogs.value
  return aiRecentLogs.value.filter(l => l.serviceName === aiLogFilter.value)
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
  const colors = { HIAGENT: '#409EFF', OPENROUTER: '#67C23A', TENCENT_TRANSLATE: '#E6A23C', TENCENT_ASR: '#F56C6C', BILIBILI: '#909399' }
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

  const baseUrl = import.meta.env.VITE_API_BASE_URL || ''
  eventSource = new EventSource(`${baseUrl}/api/ops/monitoring/realtime/stream?token=${token}`)

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
    // Reconnect after 5 seconds
    setTimeout(() => {
      if (!eventSource) connectSSE()
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
        try { topPages.value = JSON.parse(data) } catch { topPages.value = [] }
      } else {
        topPages.value = Array.isArray(data) ? data : []
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

const refreshAll = async () => {
  refreshing.value = true
  try {
    await Promise.all([
      loadChartData(), loadTopPages(), loadAiStats(),
      loadAiDailyData(), loadAiCallsRecent()
    ])
    // Also fetch current metrics if no SSE
    if (!eventSource) {
      try {
        const [rtRes, sysRes] = await Promise.all([getRealTimeMetrics(), getSystemMetrics()])
        if (rtRes.code === 200) Object.assign(realtime, rtRes.data)
        if (sysRes.code === 200) Object.assign(system, sysRes.data)
      } catch { /* ignore */ }
    }
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
</style>
