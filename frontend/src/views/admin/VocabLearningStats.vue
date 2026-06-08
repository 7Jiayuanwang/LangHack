<template>
  <div class="vocab-stats-page">

    <!-- 顶部操作栏 -->
    <div class="page-header">
      <h2>生词本学习统计</h2>
      <el-button :loading="loading" @click="loadAll" :icon="Refresh">刷新</el-button>
    </div>

    <!-- 全平台概览卡片 -->
    <div class="overview-cards" v-loading="loading">
      <div class="ov-card">
        <div class="ov-value">{{ fmt(platform.active_students) }}</div>
        <div class="ov-label">活跃学生数</div>
      </div>
      <div class="ov-card">
        <div class="ov-value">{{ fmt(platform.total_entries) }}</div>
        <div class="ov-label">总词条数</div>
      </div>
      <div class="ov-card">
        <div class="ov-value">{{ fmt(platform.total_reviews) }}</div>
        <div class="ov-label">累计复习次数</div>
      </div>
      <div class="ov-card highlight">
        <div class="ov-value">{{ masteryRate }}%</div>
        <div class="ov-label">平台掌握率</div>
      </div>
      <div class="ov-card">
        <div class="ov-value">{{ platform.avg_ef ?? '—' }}</div>
        <div class="ov-label">平均 EF</div>
      </div>
      <div class="ov-card">
        <div class="ov-value">{{ platform.avg_interval ?? '—' }} 天</div>
        <div class="ov-label">平均复习间隔</div>
      </div>
      <div class="ov-card warn" v-if="platform.overdue_count > 0">
        <div class="ov-value">{{ fmt(platform.overdue_count) }}</div>
        <div class="ov-label">逾期待复习</div>
      </div>
    </div>

    <div class="charts-row">
      <!-- 掌握度分布 -->
      <div class="chart-card">
        <div class="card-title">全平台掌握度分布</div>
        <div class="mastery-bars">
          <div v-for="item in masteryItems" :key="item.key" class="mastery-row">
            <span class="mastery-label">{{ item.label }}</span>
            <div class="mastery-bar-wrap">
              <div
                class="mastery-bar-fill"
                :class="item.cls"
                :style="{ width: pct(item.count, platform.total_entries) + '%' }"
              ></div>
            </div>
            <span class="mastery-count">{{ fmt(item.count) }}</span>
            <span class="mastery-pct">{{ pct(item.count, platform.total_entries) }}%</span>
          </div>
        </div>
      </div>

      <!-- EF 分布 -->
      <div class="chart-card">
        <div class="card-title">EF 难度因子分布</div>
        <div class="ef-bar-wrap">
          <div class="ef-bar">
            <template v-for="item in efItems" :key="item.range">
              <div
                v-if="item.count > 0"
                class="ef-seg"
                :class="getEFCls(item.range)"
                :style="{ width: pct(item.count, platform.total_entries) + '%' }"
                :title="`EF ${item.range}: ${item.count} 词`"
              ></div>
            </template>
          </div>
          <div class="ef-legend">
            <template v-for="item in efItems" :key="item.range">
              <span v-if="item.count > 0" class="ef-legend-item" :class="getEFCls(item.range)">
                {{ item.range }}: {{ item.count }}
              </span>
            </template>
          </div>
        </div>
        <div class="ef-hint">
          <span class="dot hard"></span>困难(EF&lt;2.0): {{ efHard }}
          <span class="dot normal"></span>普通(2.0–3.0): {{ efNormal }}
          <span class="dot easy"></span>简单(≥3.0): {{ efEasy }}
        </div>
      </div>

      <!-- 间隔分布 -->
      <div class="chart-card">
        <div class="card-title">复习间隔分布</div>
        <div class="interval-grid">
          <div v-for="item in intervalItems" :key="item.bucket" class="interval-cell" :class="{ active: item.count > 0 }">
            <div class="ic-bucket">{{ item.bucket }}</div>
            <div class="ic-count">{{ fmt(item.count) }}</div>
          </div>
        </div>
      </div>
    </div>

    <!-- 学生排行表 -->
    <div class="table-card" v-loading="studentsLoading">
      <div class="card-title-row">
        <span class="card-title">学生学习明细</span>
        <el-input v-model="searchText" placeholder="搜索用户名…" clearable style="width:180px" :prefix-icon="Search" />
      </div>
      <el-table
        :data="filteredStudents"
        stripe
        size="small"
        :default-sort="{ prop: 'total_words', order: 'descending' }"
      >
        <el-table-column prop="username" label="用户名" width="110" sortable />
        <el-table-column prop="nickname" label="昵称" width="100" show-overflow-tooltip />
        <el-table-column prop="total_words" label="总词数" width="80" sortable align="center" />
        <el-table-column label="掌握度分布" min-width="160">
          <template #default="{ row }">
            <div class="inline-bar">
              <div class="ib-seg new" :style="{ flex: row.new_count || 0 }"></div>
              <div class="ib-seg learning" :style="{ flex: row.learning_count || 0 }"></div>
              <div class="ib-seg mastered" :style="{ flex: row.mastered_count || 0 }"></div>
            </div>
            <div class="ib-counts">
              <span class="c-new">新:{{ row.new_count || 0 }}</span>
              <span class="c-learning">学:{{ row.learning_count || 0 }}</span>
              <span class="c-mastered">掌:{{ row.mastered_count || 0 }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="avg_ef" label="平均EF" width="85" sortable align="center">
          <template #default="{ row }">
            <el-tag
              :type="efTagType(row.avg_ef)"
              size="small"
              effect="plain"
            >{{ row.avg_ef ?? '—' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="avg_interval" label="均间隔(天)" width="90" sortable align="center" />
        <el-table-column prop="total_reviews" label="总复习" width="80" sortable align="center" />
        <el-table-column prop="overdue_count" label="逾期" width="70" sortable align="center">
          <template #default="{ row }">
            <el-tag v-if="row.overdue_count > 0" type="danger" size="small" effect="plain">
              {{ row.overdue_count }}
            </el-tag>
            <span v-else class="text-muted">0</span>
          </template>
        </el-table-column>
        <el-table-column label="学习健康度" width="110" align="center">
          <template #default="{ row }">
            <el-tag :type="healthType(row)" size="small">{{ healthLabel(row) }}</el-tag>
          </template>
        </el-table-column>
      </el-table>
    </div>

  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { Refresh, Search } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import request from '@/utils/request'

const loading = ref(false)
const studentsLoading = ref(false)
const platform = ref({})
const masteryDistribution = ref([])
const efDistribution = ref([])
const intervalDistribution = ref([])
const students = ref([])
const searchText = ref('')

const fmt = (n) => (n == null ? 0 : Number(n).toLocaleString())
const pct = (a, b) => (!b ? 0 : Math.round(a / b * 100))

const masteryRate = computed(() => {
  const t = platform.value.total_entries || 0
  return t ? pct(platform.value.mastered_count || 0, t) : 0
})

// 掌握度条数据
const masteryMap = computed(() => {
  const m = {}
  masteryDistribution.value.forEach(r => { m[r.mastery_level] = Number(r.cnt) })
  return m
})
const masteryItems = computed(() => [
  { key: 'NEW',      label: '新词',   cls: 'new',      count: masteryMap.value['NEW'] || 0 },
  { key: 'LEARNING', label: '学习中', cls: 'learning', count: (masteryMap.value['LEARNING'] || 0) + (masteryMap.value['FAMILIAR'] || 0) },
  { key: 'MASTERED', label: '已掌握', cls: 'mastered', count: masteryMap.value['MASTERED'] || 0 },
])

// EF 分布
const efItems = computed(() =>
  efDistribution.value.map(r => ({ range: r.ef_range, count: Number(r.cnt) }))
)
const efHard   = computed(() => efItems.value.filter(i => parseFloat(i.range) < 2.0).reduce((s, i) => s + i.count, 0))
const efNormal = computed(() => efItems.value.filter(i => { const v = parseFloat(i.range); return v >= 2.0 && v < 3.0 }).reduce((s, i) => s + i.count, 0))
const efEasy   = computed(() => efItems.value.filter(i => parseFloat(i.range) >= 3.0).reduce((s, i) => s + i.count, 0))

const getEFCls = (range) => {
  const v = parseFloat(range)
  if (v < 2.0) return 'ef-hard'
  if (v < 3.0) return 'ef-normal'
  return 'ef-easy'
}

// 间隔分布
const INTERVAL_ORDER = ['0（新词）', '1天', '2-7天', '8-30天', '31-90天', '90天+']
const intervalItems = computed(() => {
  const m = {}
  intervalDistribution.value.forEach(r => { m[r.bucket] = Number(r.cnt) })
  return INTERVAL_ORDER.map(b => ({ bucket: b, count: m[b] || 0 }))
})

// 学生表
const filteredStudents = computed(() => {
  const q = searchText.value.trim().toLowerCase()
  if (!q) return students.value
  return students.value.filter(s =>
    (s.username || '').toLowerCase().includes(q) || (s.nickname || '').toLowerCase().includes(q)
  )
})

const efTagType = (ef) => {
  if (ef == null) return 'info'
  if (ef < 2.0) return 'danger'
  if (ef < 2.5) return 'warning'
  return 'success'
}

const healthLabel = (row) => {
  const total = row.total_words || 0
  if (total === 0) return '未开始'
  const masteredPct = pct(row.mastered_count || 0, total)
  const overduePct  = pct(row.overdue_count || 0, total)
  if (overduePct > 30) return '积压严重'
  if ((row.avg_ef || 2.5) < 1.8) return '困难较多'
  if (masteredPct >= 60) return '学习优秀'
  if (masteredPct >= 30) return '稳步提升'
  return '初学阶段'
}
const healthType = (row) => {
  const l = healthLabel(row)
  if (l === '积压严重' || l === '困难较多') return 'danger'
  if (l === '学习优秀') return 'success'
  if (l === '稳步提升') return 'primary'
  return 'info'
}

const loadOverview = async () => {
  loading.value = true
  try {
    const res = await request({ url: '/api/admin/vocab-stats/overview', method: 'get' })
    if (res?.code === 200) {
      platform.value            = res.data.platform || {}
      masteryDistribution.value = res.data.masteryDistribution || []
      efDistribution.value      = res.data.efDistribution || []
      intervalDistribution.value= res.data.intervalDistribution || []
    }
  } catch (e) {
    ElMessage.error('加载概览失败')
  } finally {
    loading.value = false
  }
}

const loadStudents = async () => {
  studentsLoading.value = true
  try {
    const res = await request({ url: '/api/admin/vocab-stats/students', method: 'get' })
    if (res?.code === 200) students.value = res.data || []
  } catch (e) {
    ElMessage.error('加载学生数据失败')
  } finally {
    studentsLoading.value = false
  }
}

const loadAll = () => { loadOverview(); loadStudents() }
onMounted(loadAll)
</script>

<style scoped lang="scss">
.vocab-stats-page {
  padding: 20px;
  max-width: 1400px;
}

.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 20px;
  h2 { font-size: 20px; font-weight: 600; color: #303133; margin: 0; }
}

.overview-cards {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin-bottom: 20px;
}
.ov-card {
  background: white;
  border-radius: 12px;
  padding: 16px 20px;
  min-width: 120px;
  box-shadow: 0 1px 4px rgba(0,0,0,.06);
  border: 1px solid #ebeef5;
  text-align: center;
  &.highlight { border-color: #409eff; background: #ecf5ff; }
  &.warn { border-color: #f56c6c; background: #fef0f0; }
}
.ov-value { font-size: 24px; font-weight: 700; color: #303133; }
.ov-label { font-size: 12px; color: #909399; margin-top: 4px; }

.charts-row {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(300px, 1fr));
  gap: 16px;
  margin-bottom: 20px;
}

.chart-card {
  background: white;
  border-radius: 12px;
  padding: 16px;
  box-shadow: 0 1px 4px rgba(0,0,0,.06);
  border: 1px solid #ebeef5;
}
.card-title { font-size: 14px; font-weight: 600; color: #303133; margin-bottom: 14px; }

.mastery-bars { display: flex; flex-direction: column; gap: 10px; }
.mastery-row { display: flex; align-items: center; gap: 8px; }
.mastery-label { width: 50px; font-size: 13px; color: #606266; }
.mastery-bar-wrap { flex: 1; height: 12px; background: #f5f7fa; border-radius: 6px; overflow: hidden; }
.mastery-bar-fill {
  height: 100%;
  border-radius: 6px;
  transition: width .4s;
  &.new      { background: #c0ccda; }
  &.learning { background: #e6a23c; }
  &.mastered { background: #67c23a; }
}
.mastery-count { width: 40px; text-align: right; font-size: 13px; color: #303133; }
.mastery-pct   { width: 38px; text-align: right; font-size: 12px; color: #909399; }

.ef-bar-wrap {}
.ef-bar {
  display: flex;
  height: 20px;
  border-radius: 6px;
  overflow: hidden;
  background: #f5f7fa;
  margin-bottom: 8px;
}
.ef-seg {
  height: 100%;
  &.ef-hard   { background: linear-gradient(90deg, #fca5a5, #f87171); }
  &.ef-normal { background: linear-gradient(90deg, #fde68a, #fbbf24); }
  &.ef-easy   { background: linear-gradient(90deg, #86efac, #4ade80); }
}
.ef-legend { display: flex; flex-wrap: wrap; gap: 6px; font-size: 12px; }
.ef-legend-item {
  padding: 2px 6px; border-radius: 4px;
  &.ef-hard   { background: #fee2e2; color: #b91c1c; }
  &.ef-normal { background: #fef9c3; color: #92400e; }
  &.ef-easy   { background: #dcfce7; color: #15803d; }
}
.ef-hint {
  margin-top: 10px;
  font-size: 12px;
  color: #606266;
  display: flex;
  align-items: center;
  gap: 8px;
}
.dot {
  display: inline-block;
  width: 8px; height: 8px;
  border-radius: 50%;
  &.hard   { background: #f87171; }
  &.normal { background: #fbbf24; }
  &.easy   { background: #4ade80; }
}

.interval-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 8px;
}
.interval-cell {
  background: #f5f7fa;
  border-radius: 8px;
  padding: 8px;
  text-align: center;
  opacity: .5;
  &.active { opacity: 1; background: #ecf5ff; }
}
.ic-bucket { font-size: 11px; color: #909399; }
.ic-count  { font-size: 18px; font-weight: 600; color: #303133; }

.table-card {
  background: white;
  border-radius: 12px;
  padding: 16px;
  box-shadow: 0 1px 4px rgba(0,0,0,.06);
  border: 1px solid #ebeef5;
}
.card-title-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 14px;
}

.inline-bar {
  display: flex;
  height: 6px;
  border-radius: 3px;
  overflow: hidden;
  background: #f5f7fa;
  .ib-seg {
    height: 100%;
    &.new      { background: #c0ccda; }
    &.learning { background: #e6a23c; }
    &.mastered { background: #67c23a; }
  }
}
.ib-counts {
  font-size: 11px;
  margin-top: 2px;
  display: flex;
  gap: 6px;
}
.c-new      { color: #909399; }
.c-learning { color: #e6a23c; }
.c-mastered { color: #67c23a; }
.text-muted { color: #c0c4cc; }
</style>
