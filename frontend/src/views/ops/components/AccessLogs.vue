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
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 响应时间分布 -->
    <el-card class="chart-card" v-if="showCharts">
      <div class="chart-header">响应时间分布</div>
      <div class="response-time-bars">
        <div class="time-bar" :style="{ width: getPercentage(distribution.fast) + '%' }">
          <span class="bar-label">快速 (&lt;100ms)</span>
          <span class="bar-value">{{ distribution.fast }}</span>
        </div>
        <div class="time-bar normal" :style="{ width: getPercentage(distribution.normal) + '%' }">
          <span class="bar-label">正常 (100-500ms)</span>
          <span class="bar-value">{{ distribution.normal }}</span>
        </div>
        <div class="time-bar slow" :style="{ width: getPercentage(distribution.slow) + '%' }">
          <span class="bar-label">慢速 (500-2000ms)</span>
          <span class="bar-value">{{ distribution.slow }}</span>
        </div>
        <div class="time-bar very-slow" :style="{ width: getPercentage(distribution.verySlow) + '%' }">
          <span class="bar-label">很慢 (&gt;2000ms)</span>
          <span class="bar-value">{{ distribution.verySlow }}</span>
        </div>
      </div>
    </el-card>

    <!-- 访问日志表格 -->
    <el-card class="table-card">
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

      <!-- 分页 -->
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
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getAccessLogs, getAccessLogStats, getResponseTimeDistribution } from '@/api/ops'

const props = defineProps({
  loading: Boolean
})

const emit = defineEmits(['refresh'])

// ── 状态 ──────────────────────────────────────────────────────────────────────
const loading = ref(false)
const tableData = ref([])
const stats = ref({})
const distribution = ref({ fast: 0, normal: 0, slow: 0, verySlow: 0 })
const showCharts = ref(false)

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

// ── 查询参数 ────────────────────────────────────────────────────────────────
const buildQueryParams = () => {
  const params = {
    page: pagination.current,
    size: pagination.size
  }
  if (filters.username) params.username = filters.username
  if (filters.uri) params.uri = filters.uri
  if (filters.statusCode) params.statusCode = filters.statusCode
  if (filters.dateRange && filters.dateRange[0]) {
    params.startTime = filters.dateRange[0]
    params.endTime = filters.dateRange[1]
  }
  return params
}

// ── API 调用 ────────────────────────────────────────────────────────────────
const loadAccessLogs = async () => {
  loading.value = true
  try {
    const params = buildQueryParams()
    const res = await getAccessLogs(params)
    if (res.code === 200) {
      tableData.value = res.data.records || []
      pagination.total = res.data.total || 0
      pagination.current = res.data.current || 1
    } else {
      ElMessage.error(res.message || '查询访问日志失败')
    }
  } catch (error) {
    ElMessage.error('查询访问日志失败')
  } finally {
    loading.value = false
  }
}

const loadStats = async () => {
  try {
    const params = {}
    if (filters.dateRange && filters.dateRange[0]) {
      params.startTime = filters.dateRange[0]
      params.endTime = filters.dateRange[1]
    }
    const res = await getAccessLogStats(params)
    if (res.code === 200) {
      stats.value = res.data
    }
  } catch (error) {
    console.error('获取统计数据失败', error)
  }
}

const loadDistribution = async () => {
  try {
    const params = {}
    if (filters.dateRange && filters.dateRange[0]) {
      params.startTime = filters.dateRange[0]
      params.endTime = filters.dateRange[1]
    }
    const res = await getResponseTimeDistribution(params)
    if (res.code === 200) {
      distribution.value = res.data
      const total = distribution.value.fast + distribution.value.normal +
                     distribution.value.slow + distribution.value.verySlow
      showCharts.value = total > 0
    }
  } catch (error) {
    console.error('获取响应时间分布失败', error)
  }
}

const refreshAll = async () => {
  await Promise.all([
    loadAccessLogs(),
    loadStats(),
    loadDistribution()
  ])
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

const handlePageChange = () => {
  loadAccessLogs()
}

const handleSizeChange = () => {
  pagination.current = 1
  loadAccessLogs()
}

// ── 响应时间分布百分比 ──────────────────────────────────────────────────────
const getPercentage = (value) => {
  const total = distribution.value.fast + distribution.value.normal +
                 distribution.value.slow + distribution.value.verySlow
  if (total === 0) return 0
  return Math.max(5, (value / total * 100))
}

// ── 生命周期 ────────────────────────────────────────────────────────────────
onMounted(() => {
  refreshAll()
})

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
.chart-card { margin-bottom: 20px; }
.table-card { margin-bottom: 20px; }

.chart-header {
  font-size: 15px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 16px;
}

.response-time-bars {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.time-bar {
  min-width: 120px;
  height: 40px;
  background: #67c23a;
  border-radius: 6px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 12px;
  color: #fff;
  font-weight: 600;
  transition: all 0.3s;
}

.time-bar.normal { background: #409eff; }
.time-bar.slow { background: #e6a23c; }
.time-bar.very-slow { background: #f56c6c; }

.bar-label { font-size: 13px; }
.bar-value { font-size: 16px; font-weight: 700; }

.pagination-container {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}

@media (max-width: 1200px) {
  .stats-grid { grid-template-columns: repeat(3, 1fr); }
}

@media (max-width: 768px) {
  .stats-grid { grid-template-columns: repeat(2, 1fr); }
}
</style>
