<template>
  <div>
    <!-- 统计卡片 -->
    <el-card class="stats-card">
      <div class="stats-grid">
        <div class="stat-item">
          <div class="stat-label">总请求数</div>
          <div class="stat-value">{{ stats.totalRequests || 0 }}</div>
        </div>
        <div class="stat-item error">
          <div class="stat-label">错误请求</div>
          <div class="stat-value">{{ stats.errorCount || 0 }}</div>
        </div>
        <div class="stat-item info">
          <div class="stat-label">独立用户</div>
          <div class="stat-value">{{ stats.uniqueUsers || 0 }}</div>
        </div>
        <div class="stat-item warn">
          <div class="stat-label">错误率</div>
          <div class="stat-value">{{ stats.errorRate || '0.00' }}%</div>
        </div>
        <div class="stat-item">
          <div class="stat-label">平均响应时间</div>
          <div class="stat-value">{{ stats.avgResponseTimeMs || 0 }}ms</div>
        </div>
      </div>
    </el-card>

    <!-- 筛选器 -->
    <el-card class="filter-card">
      <el-form :inline="true">
        <el-form-item label="用户名">
          <el-input v-model="filters.username" placeholder="输入用户名" clearable style="width: 160px" />
        </el-form-item>
        <el-form-item label="URI">
          <el-input v-model="filters.uri" placeholder="输入URI" clearable style="width: 200px" />
        </el-form-item>
        <el-form-item label="状态码">
          <el-select v-model="filters.statusCode" placeholder="全部" clearable style="width: 100px">
            <el-option label="全部" :value="null"></el-option>
            <el-option label="2xx 成功" :value="200"></el-option>
            <el-option label="4xx 客户端错误" :value="400"></el-option>
            <el-option label="5xx 服务器错误" :value="500"></el-option>
          </el-select>
        </el-form-item>
        <el-form-item label="时间范围">
          <el-date-picker
            v-model="filters.dateRange"
            type="daterange"
            range-separator="至"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            value-format="YYYY-MM-DD"
            style="width: 240px"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
          <el-button type="success" :loading="downloadingLogs" @click="handleDownloadLogs">
            <el-icon><Download /></el-icon> 下载访问日志
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 响应时间分布 -->
    <el-card class="chart-card" v-if="showCharts">
      <div class="chart-header">响应时间分布</div>

      <!-- 百分位摘要 -->
      <div class="percentile-row">
        <div class="percentile-item" v-for="p in percentiles" :key="p.label">
          <span class="percentile-label">{{ p.label }}</span>
          <span class="percentile-value">{{ p.value }}ms</span>
        </div>
      </div>

      <!-- 6档条形图 -->
      <div class="response-time-bars">
        <div v-for="bar in distBars" :key="bar.key" class="bar-row">
          <div class="bar-meta">
            <span class="bar-range">{{ bar.label }}</span>
            <span class="bar-count">{{ bar.count }}</span>
          </div>
          <div class="bar-track">
            <div
              class="bar-fill"
              :class="bar.cls"
              :style="{ width: getBarWidth(bar.count) + '%' }"
            ></div>
          </div>
          <span class="bar-pct">{{ getBarPct(bar.count) }}%</span>
        </div>
      </div>
    </el-card>

    <!-- 访问日志 + 登录统计 -->
    <el-card class="table-card">
      <el-tabs v-model="activeSubTab">
        <el-tab-pane label="访问日志" name="logs">
          <el-table
            :data="tableData"
            v-loading="loading"
            stripe
            border
            size="small"
            max-height="600"
          >
            <el-table-column type="index" label="#" width="60" />
            <el-table-column prop="createdTime" label="时间" width="160" show-overflow-tooltip />
            <el-table-column prop="username" label="用户" width="120" show-overflow-tooltip />
            <el-table-column prop="method" label="方法" width="80" />
            <el-table-column prop="uri" label="URI" min-width="200" show-overflow-tooltip />
            <el-table-column prop="statusCode" label="状态码" width="80">
              <template #default="{ row }">
                <el-tag
                  :type="row.statusCode < 300 ? 'success' : row.statusCode < 400 ? 'info' : row.statusCode < 500 ? 'warning' : 'danger'"
                  size="small"
                >
                  {{ row.statusCode }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="responseTimeMs" label="响应时间(ms)" width="110" sortable />
            <el-table-column prop="ipAddress" label="IP地址" width="130" show-overflow-tooltip />
          </el-table>

          <div class="pagination-container">
            <el-pagination
              v-model:current-page="pagination.current"
              v-model:page-size="pagination.size"
              :page-sizes="[20, 50, 100, 200]"
              :total="pagination.total"
              layout="total, sizes, prev, pager, next, jumper"
              @size-change="handleSizeChange"
              @current-change="handlePageChange"
            />
          </div>
        </el-tab-pane>

        <el-tab-pane name="login">
          <template #label>
            登录统计
          </template>

          <!-- 登录汇总 -->
          <div class="login-summary-header">
            <el-button type="success" size="small" :loading="downloadingLogin" @click="handleDownloadLogin">
              <el-icon><Download /></el-icon> 下载登录统计
            </el-button>
          </div>
          <div class="login-summary">
            <div class="login-stat-item success">
              <div class="login-stat-label">登录成功</div>
              <div class="login-stat-value">{{ loginStats.loginSuccess || 0 }}</div>
            </div>
            <div class="login-stat-item fail">
              <div class="login-stat-label">登录失败</div>
              <div class="login-stat-value">{{ loginStats.loginFail || 0 }}</div>
            </div>
            <div class="login-stat-item logout">
              <div class="login-stat-label">退出次数</div>
              <div class="login-stat-value">{{ loginStats.logoutCount || 0 }}</div>
            </div>
          </div>

          <!-- 登录记录表 -->
          <el-table
            :data="loginStats.records || []"
            v-loading="loginLoading"
            stripe
            border
            size="small"
            max-height="520"
          >
            <el-table-column prop="createdTime" label="时间" width="160" show-overflow-tooltip />
            <el-table-column prop="username" label="用户名" width="140" show-overflow-tooltip />
            <el-table-column prop="action" label="操作" width="80">
              <template #default="{ row }">
                <el-tag :type="row.action === '登录' ? 'primary' : 'info'" size="small">
                  {{ row.action }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="success" label="结果" width="80">
              <template #default="{ row }">
                <el-tag :type="row.success ? 'success' : 'danger'" size="small">
                  {{ row.success ? '成功' : '失败' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="ipAddress" label="IP地址" min-width="130" show-overflow-tooltip />
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { Download } from '@element-plus/icons-vue'
import { getAccessLogs, getAccessLogStats, getResponseTimeDistribution, getLoginStats,
         downloadAccessLogs, downloadLoginStats } from '@/api/ops'
import { normalizeDownloadResponse, extractBlobErrorMessage, triggerBlobDownload } from '@/utils/blobDownload'

// ── 状态 ──────────────────────────────────────────────────────────────────────
const loading = ref(false)
const loginLoading = ref(false)
const downloadingLogs = ref(false)
const downloadingLogin = ref(false)
const tableData = ref([])
const stats = ref({})
const distribution = ref({})
const loginStats = ref({ loginSuccess: 0, loginFail: 0, logoutCount: 0, records: [] })
const showCharts = ref(false)
const activeSubTab = ref('logs')

const filters = reactive({
  username: '',
  uri: '',
  statusCode: null,
  dateRange: null
})

const pagination = reactive({
  current: 1,
  size: 50,
  total: 0
})

// ── 响应时间分布条形图数据 ──────────────────────────────────────────────────
const distBars = computed(() => [
  { key: 'under50',   label: '< 50ms',        count: distribution.value.under50   || 0, cls: 'excellent' },
  { key: 's50to100',  label: '50 – 100ms',    count: distribution.value.s50to100  || 0, cls: 'good' },
  { key: 's100to200', label: '100 – 200ms',   count: distribution.value.s100to200 || 0, cls: 'normal' },
  { key: 's200to500', label: '200 – 500ms',   count: distribution.value.s200to500 || 0, cls: 'slow' },
  { key: 's500to2000',label: '500 – 2000ms',  count: distribution.value.s500to2000|| 0, cls: 'slower' },
  { key: 'over2000',  label: '> 2000ms',      count: distribution.value.over2000  || 0, cls: 'critical' },
])

const percentiles = computed(() => [
  { label: 'P50', value: distribution.value.p50 || 0 },
  { label: 'P90', value: distribution.value.p90 || 0 },
  { label: 'P95', value: distribution.value.p95 || 0 },
  { label: 'P99', value: distribution.value.p99 || 0 },
])

const distTotal = computed(() => distBars.value.reduce((s, b) => s + b.count, 0))

const getBarWidth = (count) => {
  if (distTotal.value === 0) return 0
  return Math.max(1, (count / distTotal.value) * 100)
}

const getBarPct = (count) => {
  if (distTotal.value === 0) return '0.0'
  return (count / distTotal.value * 100).toFixed(1)
}

// ── 查询参数 ────────────────────────────────────────────────────────────────
const buildTimeParams = () => {
  const params = {}
  if (filters.dateRange && filters.dateRange[0]) {
    params.startTime = filters.dateRange[0]
    params.endTime = filters.dateRange[1]
  }
  return params
}

const buildQueryParams = () => ({
  page: pagination.current,
  size: pagination.size,
  ...(filters.username ? { username: filters.username } : {}),
  ...(filters.uri ? { uri: filters.uri } : {}),
  ...(filters.statusCode ? { statusCode: filters.statusCode } : {}),
  ...buildTimeParams()
})

// ── API 调用 ────────────────────────────────────────────────────────────────
const loadAccessLogs = async () => {
  loading.value = true
  try {
    const res = await getAccessLogs(buildQueryParams())
    if (res.code === 200) {
      tableData.value = res.data.records || []
      pagination.total = res.data.total || 0
      pagination.current = res.data.current || 1
    } else {
      ElMessage.error(res.message || '查询访问日志失败')
    }
  } catch {
    ElMessage.error('查询访问日志失败')
  } finally {
    loading.value = false
  }
}

const loadStats = async () => {
  try {
    const res = await getAccessLogStats(buildTimeParams())
    if (res.code === 200) stats.value = res.data
  } catch (e) {
    console.error('获取统计数据失败', e)
  }
}

const loadDistribution = async () => {
  try {
    const res = await getResponseTimeDistribution(buildTimeParams())
    if (res.code === 200) {
      distribution.value = res.data
      showCharts.value = distTotal.value > 0
    }
  } catch (e) {
    console.error('获取响应时间分布失败', e)
  }
}

const loadLoginStats = async () => {
  loginLoading.value = true
  try {
    const res = await getLoginStats(buildTimeParams())
    if (res.code === 200) loginStats.value = res.data
  } catch (e) {
    console.error('获取登录统计失败', e)
  } finally {
    loginLoading.value = false
  }
}

const refreshAll = async () => {
  await Promise.all([loadAccessLogs(), loadStats(), loadDistribution(), loadLoginStats()])
}

// ── 事件处理 ────────────────────────────────────────────────────────────────
const handleSearch = () => {
  pagination.current = 1
  refreshAll()
}

const handleReset = () => {
  filters.username = ''
  filters.uri = ''
  filters.statusCode = null
  filters.dateRange = null
  pagination.current = 1
  refreshAll()
}

const handlePageChange = () => loadAccessLogs()
const handleSizeChange = () => { pagination.current = 1; loadAccessLogs() }

onMounted(refreshAll)

const handleDownloadLogs = async () => {
  downloadingLogs.value = true
  try {
    const raw = await downloadAccessLogs(buildTimeParams())
    const { blob, filename } = normalizeDownloadResponse(raw, 'access-logs.csv')
    if (blob.size === 0 || blob.type.startsWith('application/json')) {
      ElMessage.warning(await extractBlobErrorMessage(blob) || '暂无数据')
      return
    }
    triggerBlobDownload(blob, filename)
    ElMessage.success('下载成功')
  } catch {
    ElMessage.error('下载访问日志失败')
  } finally {
    downloadingLogs.value = false
  }
}

const handleDownloadLogin = async () => {
  downloadingLogin.value = true
  try {
    const raw = await downloadLoginStats(buildTimeParams())
    const { blob, filename } = normalizeDownloadResponse(raw, 'login-stats.csv')
    if (blob.size === 0 || blob.type.startsWith('application/json')) {
      ElMessage.warning(await extractBlobErrorMessage(blob) || '暂无数据')
      return
    }
    triggerBlobDownload(blob, filename)
    ElMessage.success('下载成功')
  } catch {
    ElMessage.error('下载登录统计失败')
  } finally {
    downloadingLogin.value = false
  }
}

defineExpose({ refreshAll })
</script>

<style scoped>
.stats-card { margin-bottom: 20px; }

.stats-grid {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 16px;
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

.stat-label { font-size: 13px; color: #909399; margin-bottom: 6px; }
.stat-value { font-size: 24px; font-weight: bold; color: #303133; }
.stat-item.error .stat-value { color: #f56c6c; }
.stat-item.warn  .stat-value { color: #e6a23c; }
.stat-item.info  .stat-value { color: #409eff; }

.filter-card { margin-bottom: 20px; }
.chart-card  { margin-bottom: 20px; }
.table-card  { margin-bottom: 20px; }

/* 百分位摘要 */
.chart-header { font-size: 15px; font-weight: 600; color: #303133; margin-bottom: 14px; }

.percentile-row {
  display: flex;
  gap: 24px;
  margin-bottom: 18px;
  flex-wrap: wrap;
}

.percentile-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  background: #f5f7fa;
  border-radius: 8px;
  padding: 8px 20px;
  min-width: 80px;
}

.percentile-label { font-size: 12px; color: #909399; }
.percentile-value { font-size: 18px; font-weight: 700; color: #303133; margin-top: 2px; }

/* 条形图 */
.response-time-bars { display: flex; flex-direction: column; gap: 8px; }

.bar-row {
  display: flex;
  align-items: center;
  gap: 10px;
}

.bar-meta {
  width: 130px;
  display: flex;
  justify-content: space-between;
  flex-shrink: 0;
}

.bar-range { font-size: 13px; color: #606266; }
.bar-count { font-size: 13px; font-weight: 600; color: #303133; }

.bar-track {
  flex: 1;
  height: 22px;
  background: #f0f2f5;
  border-radius: 4px;
  overflow: hidden;
}

.bar-fill {
  height: 100%;
  border-radius: 4px;
  transition: width 0.4s ease;
}

.bar-fill.excellent  { background: #67c23a; }
.bar-fill.good       { background: #95d475; }
.bar-fill.normal     { background: #409eff; }
.bar-fill.slow       { background: #e6a23c; }
.bar-fill.slower     { background: #f08a30; }
.bar-fill.critical   { background: #f56c6c; }

.bar-pct { width: 48px; font-size: 12px; color: #909399; text-align: right; flex-shrink: 0; }

/* 登录统计 */
.login-summary-header {
  display: flex;
  justify-content: flex-end;
  margin-bottom: 12px;
}

.login-summary {
  display: flex;
  gap: 16px;
  margin-bottom: 16px;
  flex-wrap: wrap;
}

.login-stat-item {
  flex: 1;
  min-width: 120px;
  text-align: center;
  padding: 14px 20px;
  border-radius: 8px;
  background: #f5f7fa;
}

.login-stat-item.success { background: #f0f9eb; }
.login-stat-item.fail    { background: #fef0f0; }
.login-stat-item.logout  { background: #f0f9ff; }

.login-stat-label { font-size: 13px; color: #909399; margin-bottom: 4px; }
.login-stat-value { font-size: 28px; font-weight: bold; color: #303133; }
.login-stat-item.success .login-stat-value { color: #67c23a; }
.login-stat-item.fail    .login-stat-value { color: #f56c6c; }
.login-stat-item.logout  .login-stat-value { color: #409eff; }

.pagination-container {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}

@media (max-width: 1200px) { .stats-grid { grid-template-columns: repeat(3, 1fr); } }
@media (max-width: 768px)  { .stats-grid { grid-template-columns: repeat(2, 1fr); } }
</style>
