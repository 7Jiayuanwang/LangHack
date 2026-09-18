<template>
  <div class="vocab-stats-page">

    <div class="page-header">
      <h2>生词本学习统计</h2>
      <el-button :loading="loading" @click="loadAll" :icon="Refresh">刷新</el-button>
    </div>

    <!-- 概览卡片 -->
    <div class="overview-cards" v-loading="loading">
      <div class="ov-card">
        <div class="ov-icon" style="background:#ecf5ff;color:#409eff">👥</div>
        <div class="ov-body">
          <div class="ov-value">{{ fmt(platform.active_students) }}</div>
          <div class="ov-label">活跃学生</div>
        </div>
      </div>
      <div class="ov-card">
        <div class="ov-icon" style="background:#f0f9eb;color:#67c23a">📚</div>
        <div class="ov-body">
          <div class="ov-value">{{ fmt(platform.total_entries) }}</div>
          <div class="ov-label">总词条数</div>
        </div>
      </div>
      <div class="ov-card">
        <div class="ov-icon" style="background:#fdf6ec;color:#e6a23c">🔄</div>
        <div class="ov-body">
          <div class="ov-value">{{ fmt(platform.total_reviews) }}</div>
          <div class="ov-label">累计复习</div>
        </div>
      </div>
      <div class="ov-card highlight">
        <div class="ov-icon" style="background:#d9ecff;color:#3375e0">🎯</div>
        <div class="ov-body">
          <div class="ov-value">{{ masteryRate }}%</div>
          <div class="ov-label">平台掌握率</div>
        </div>
      </div>
      <div class="ov-card">
        <div class="ov-icon" style="background:#f0f9eb;color:#52c41a">⚡</div>
        <div class="ov-body">
          <div class="ov-value">{{ platform.avg_ef ?? '—' }}</div>
          <div class="ov-label">平均 EF</div>
        </div>
      </div>
      <div class="ov-card">
        <div class="ov-icon" style="background:#f5f0ff;color:#722ed1">📅</div>
        <div class="ov-body">
          <div class="ov-value">{{ platform.avg_interval ?? '—' }}<span class="unit"> 天</span></div>
          <div class="ov-label">平均复习间隔</div>
        </div>
      </div>
      <div class="ov-card" :class="{ warn: platform.overdue_count > 0 }">
        <div class="ov-icon" :style="platform.overdue_count > 0 ? 'background:#fef0f0;color:#f56c6c' : 'background:#f5f7fa;color:#c0c4cc'">⏰</div>
        <div class="ov-body">
          <div class="ov-value">{{ fmt(platform.overdue_count) }}</div>
          <div class="ov-label">逾期待复习</div>
        </div>
      </div>
    </div>

    <!-- 图表行 -->
    <div class="charts-row" v-loading="loading">

      <!-- 掌握度分布 -->
      <div class="chart-card">
        <div class="card-title">掌握度分布</div>
        <div class="mastery-bars">
          <div v-for="item in masteryItems" :key="item.key" class="mastery-row">
            <span class="mastery-label">{{ item.label }}</span>
            <div class="mastery-bar-wrap">
              <div class="mastery-bar-fill" :class="item.cls"
                :style="{ width: pct(item.count, platform.total_entries) + '%' }"></div>
            </div>
            <span class="mastery-count">{{ fmt(item.count) }}</span>
            <span class="mastery-pct">{{ pct(item.count, platform.total_entries) }}%</span>
          </div>
        </div>
      </div>

      <!-- EF 分布 -->
      <div class="chart-card">
        <div class="card-title">EF 难度分布</div>
        <div class="ef-bar-wrap">
          <div class="ef-bar">
            <template v-for="item in efItems" :key="item.range">
              <div v-if="item.count > 0" class="ef-seg" :class="getEFCls(item.range)"
                :style="{ width: pct(item.count, platform.total_entries) + '%' }"
                :title="`EF ${item.range}: ${item.count} 词`"></div>
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
          <span class="dot hard"></span>困难(&lt;2.0): {{ efHard }}
          <span class="dot normal"></span>普通(2-3): {{ efNormal }}
          <span class="dot easy"></span>简单(≥3): {{ efEasy }}
        </div>
      </div>

      <!-- 来源分布 -->
      <div class="chart-card">
        <div class="card-title">词汇来源分布</div>
        <div class="source-pie-wrap">
          <div class="source-pie">
            <template v-for="item in sourceItems.filter(i => i.count > 0)" :key="item.type">
              <div class="pie-seg" :class="item.cls"
                :style="{ flex: item.count }">
                <span class="pie-label">{{ item.label }}<br>{{ fmt(item.count) }}</span>
              </div>
            </template>
          </div>
          <div class="source-legend">
            <div v-for="item in sourceItems" :key="item.type" class="sl-item" v-show="item.count > 0">
              <span class="sl-dot" :class="item.cls"></span>
              <span class="sl-text">{{ item.label }}</span>
              <span class="sl-pct">{{ pct(item.count, platform.total_entries) }}%</span>
            </div>
          </div>
        </div>
      </div>

      <!-- 复习间隔分布 -->
      <div class="chart-card">
        <div class="card-title">复习间隔分布</div>
        <div class="interval-bars">
          <div v-for="item in intervalItems" :key="item.bucket" class="interval-row"
            :class="{ empty: item.count === 0 }">
            <span class="ir-label">{{ item.bucket }}</span>
            <div class="ir-bar-wrap">
              <div class="ir-bar-fill" :style="{ width: pct(item.count, maxIntervalCount) + '%' }"></div>
            </div>
            <span class="ir-count">{{ fmt(item.count) }}</span>
          </div>
        </div>
      </div>
    </div>

    <!-- 学习趋势 -->
    <div class="trend-card" v-loading="trendLoading">
      <div class="card-title-row">
        <span class="card-title">学习趋势</span>
        <div class="trend-controls">
          <el-button-group size="small">
            <el-button :type="trendPreset === 7  ? 'primary' : ''" @click="applyPreset(7)">近7天</el-button>
            <el-button :type="trendPreset === 14 ? 'primary' : ''" @click="applyPreset(14)">近14天</el-button>
            <el-button :type="trendPreset === 30 ? 'primary' : ''" @click="applyPreset(30)">近30天</el-button>
            <el-button :type="trendPreset === 90 ? 'primary' : ''" @click="applyPreset(90)">近90天</el-button>
          </el-button-group>
          <el-date-picker
            v-model="trendDateRange"
            type="daterange"
            size="small"
            range-separator="至"
            start-placeholder="开始"
            end-placeholder="结束"
            :disabled-date="d => d > new Date()"
            value-format="YYYY-MM-DD"
            style="width:220px"
            @change="onDateRangeChange"
          />
          <div class="trend-legend">
            <span class="tl-item new-line">新增词汇</span>
            <span class="tl-item review-line">复习次数</span>
          </div>
        </div>
      </div>

      <!-- SVG 容器：响应式宽度 -->
      <div ref="trendRef" class="trend-wrap" @mousemove="onTrendMouseMove" @mouseleave="onTrendMouseLeave">
        <svg class="trend-svg" :viewBox="`0 0 ${svgVW} ${SVG_VH}`" preserveAspectRatio="none">
          <defs>
            <linearGradient id="newAreaGrad" x1="0" y1="0" x2="0" y2="1">
              <stop offset="0%"   stop-color="#409eff" stop-opacity="0.25"/>
              <stop offset="100%" stop-color="#409eff" stop-opacity="0.02"/>
            </linearGradient>
            <linearGradient id="reviewAreaGrad" x1="0" y1="0" x2="0" y2="1">
              <stop offset="0%"   stop-color="#e6a23c" stop-opacity="0.2"/>
              <stop offset="100%" stop-color="#e6a23c" stop-opacity="0.02"/>
            </linearGradient>
          </defs>

          <!-- Y 网格线 + 标签（标签用固定字号，不受 none 拉伸，所以用 foreignObject 不合适；用 textLength 固定） -->
          <template v-for="g in yGridLines" :key="g.y">
            <line :x1="PAD_L" :y1="g.y" :x2="svgVW - PAD_R" :y2="g.y"
              stroke="#ebeef5" :stroke-dasharray="g.dash ? '5,4' : ''" stroke-width="1"/>
          </template>

          <!-- 面积填充 -->
          <path v-if="newAreaPath"    :d="newAreaPath"    fill="url(#newAreaGrad)"/>
          <path v-if="reviewAreaPath" :d="reviewAreaPath" fill="url(#reviewAreaGrad)"/>

          <!-- 折线 -->
          <path v-if="newLinePath"    :d="newLinePath"
            fill="none" stroke="#409eff" stroke-width="2.5"
            stroke-linecap="round" stroke-linejoin="round"/>
          <path v-if="reviewLinePath" :d="reviewLinePath"
            fill="none" stroke="#e6a23c" stroke-width="2.5"
            stroke-linecap="round" stroke-linejoin="round"/>

          <!-- 数据点（≤14天显示圆点，≤30天显示数值） -->
          <template v-for="(p, i) in newDots" :key="'n'+i">
            <circle v-if="trendDays <= 14" :cx="p.x" :cy="p.y" r="4" fill="#fff" stroke="#409eff" stroke-width="2.5"/>
          </template>
          <template v-for="(p, i) in reviewDots" :key="'r'+i">
            <circle v-if="trendDays <= 14" :cx="p.x" :cy="p.y" r="4" fill="#fff" stroke="#e6a23c" stroke-width="2.5"/>
          </template>

          <!-- hover 竖线 -->
          <line v-if="tooltip.visible"
            :x1="tooltip.svgX" y1="0" :x2="tooltip.svgX" :y2="SVG_VH - PAD_B"
            stroke="#dcdfe6" stroke-width="1" stroke-dasharray="4,3"/>
          <circle v-if="tooltip.visible && tooltip.newY != null"
            :cx="tooltip.svgX" :cy="tooltip.newY" r="5" fill="#409eff"/>
          <circle v-if="tooltip.visible && tooltip.reviewY != null"
            :cx="tooltip.svgX" :cy="tooltip.reviewY" r="5" fill="#e6a23c"/>
        </svg>

        <!-- Y 轴标签（HTML，避免 preserveAspectRatio:none 拉伸字形） -->
        <div class="y-labels">
          <span v-for="g in yGridLines" :key="g.label"
            class="y-label" :style="{ top: g.y + 'px' }">{{ g.label }}</span>
        </div>

        <!-- X 轴标签 -->
        <div class="x-labels">
          <span v-for="tick in xTicks" :key="tick.i"
            class="x-label" :style="{ left: ((tick.x - PAD_L) / chartW * 100) + '%' }">{{ tick.label }}</span>
        </div>

        <!-- Tooltip -->
        <div v-if="tooltip.visible" class="trend-tooltip"
          :style="{ left: tooltip.elX + 'px', top: tooltip.elY + 'px',
                    transform: tooltip.elX > containerW * 0.7 ? 'translateX(-110%)' : 'translateX(10px)' }">
          <div class="tt-date">{{ tooltip.date }}</div>
          <div class="tt-row new-dot">新增词汇 <strong>{{ tooltip.newVal }}</strong></div>
          <div class="tt-row review-dot">复习次数 <strong>{{ tooltip.reviewVal }}</strong></div>
        </div>
      </div>
    </div>

    <!-- 学生学习明细 -->
    <div class="table-card" v-loading="studentsLoading">
      <div class="card-title-row">
        <span class="card-title">学生学习明细</span>
        <el-input v-model="searchText" placeholder="搜索用户名…" clearable
          style="width:180px" :prefix-icon="Search" />
      </div>
      <el-table :data="filteredStudents" stripe size="small"
        :default-sort="{ prop: 'total_words', order: 'descending' }">
        <el-table-column prop="username" label="用户名" width="110" sortable />
        <el-table-column prop="nickname" label="昵称" width="90" show-overflow-tooltip />
        <el-table-column prop="total_words" label="总词数" width="75" sortable align="center" />
        <el-table-column label="掌握度分布" min-width="150">
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
        <el-table-column prop="avg_ef" label="均EF" width="80" sortable align="center">
          <template #default="{ row }">
            <el-tag :type="efTagType(row.avg_ef)" size="small" effect="plain">
              {{ row.avg_ef ?? '—' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="avg_interval" label="均间隔" width="75" sortable align="center">
          <template #default="{ row }">{{ row.avg_interval ?? '—' }}天</template>
        </el-table-column>
        <el-table-column prop="total_reviews" label="总复习" width="75" sortable align="center" />
        <el-table-column prop="overdue_count" label="逾期" width="65" sortable align="center">
          <template #default="{ row }">
            <el-tag v-if="row.overdue_count > 0" type="danger" size="small" effect="plain">
              {{ row.overdue_count }}
            </el-tag>
            <span v-else class="text-muted">0</span>
          </template>
        </el-table-column>
        <el-table-column label="最后活跃" width="100" align="center">
          <template #default="{ row }">
            <span class="last-active" :class="activeAgo(row.last_active).cls">
              {{ activeAgo(row.last_active).text }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="学习健康度" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="healthType(row)" size="small">{{ healthLabel(row) }}</el-tag>
          </template>
        </el-table-column>
      </el-table>
    </div>

  </div>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import { Refresh, Search } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import request from '@/utils/request'

const loading = ref(false)
const trendLoading = ref(false)
const studentsLoading = ref(false)
const platform = ref({})
const masteryDistribution = ref([])
const efDistribution = ref([])
const intervalDistribution = ref([])
const sourceDistribution = ref([])
const dailyNewTrend = ref([])
const dailyReviewTrend = ref([])
const students = ref([])
const searchText = ref('')

// 趋势筛选状态
const trendPreset = ref(7)
const trendDateRange = ref(null)   // [start, end] strings or null

const fmt = (n) => (n == null ? 0 : Number(n).toLocaleString())
const pct = (a, b) => (!b ? 0 : Math.round(a / b * 100))

const masteryRate = computed(() => {
  const t = platform.value.total_entries || 0
  return t ? pct(platform.value.mastered_count || 0, t) : 0
})

// 掌握度
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
const maxIntervalCount = computed(() => Math.max(...intervalItems.value.map(i => i.count), 1))

// 来源分布
const SOURCE_LABELS = { ARTICLE: '文章阅读', TEXT: '文章阅读', MANUAL: '手动添加' }
const SOURCE_CLS    = { ARTICLE: 'article', TEXT: 'article', MANUAL: 'manual' }
const sourceItems = computed(() => {
  const m = {}
  sourceDistribution.value.forEach(r => {
    const key = r.source_type === 'TEXT' ? 'ARTICLE' : (r.source_type || 'MANUAL')
    m[key] = (m[key] || 0) + Number(r.cnt)
  })
  return ['ARTICLE', 'MANUAL'].map(t => ({
    type: t,
    label: SOURCE_LABELS[t],
    cls: SOURCE_CLS[t],
    count: m[t] || 0,
  }))
})

// 趋势 SVG — 响应式宽度
const SVG_VH = 200
const PAD_L = 0, PAD_R = 0, PAD_T = 20, PAD_B = 30
// svgVW 跟随容器像素宽度（用 ResizeObserver 更新）
const trendRef  = ref(null)
const svgVW     = ref(760)
const containerW = ref(760)

const CHART_H = SVG_VH - PAD_T - PAD_B
const chartW  = computed(() => svgVW.value - PAD_L - PAD_R)

// 当前区间所有日期（ISO 字符串数组）
const trendDatesISO = computed(() => {
  const end   = trendDateRange.value ? trendDateRange.value[1] : isoToday()
  const start = trendDateRange.value ? trendDateRange.value[0] : offsetDate(end, -(trendPreset.value - 1))
  const dates = []
  let cur = new Date(start + 'T00:00:00')
  const last = new Date(end + 'T00:00:00')
  while (cur <= last) {
    dates.push(cur.toISOString().slice(0, 10))
    cur.setDate(cur.getDate() + 1)
  }
  return dates
})

const trendDays = computed(() => trendDatesISO.value.length)

const isoToday = () => new Date().toISOString().slice(0, 10)
const offsetDate = (iso, delta) => {
  const d = new Date(iso + 'T00:00:00')
  d.setDate(d.getDate() + delta)
  return d.toISOString().slice(0, 10)
}

const buildTrendValues = (raw, key) => {
  const m = {}
  raw.forEach(r => { m[String(r.day).slice(0, 10)] = Number(r[key]) })
  return trendDatesISO.value.map(d => m[d] || 0)
}

const newValues    = computed(() => buildTrendValues(dailyNewTrend.value, 'new_words'))
const reviewValues = computed(() => buildTrendValues(dailyReviewTrend.value, 'review_count'))

const trendMaxY = computed(() => {
  const all = [...newValues.value, ...reviewValues.value]
  const raw = Math.max(...all, 1)
  if (raw <= 5) return 5
  if (raw <= 10) return 10
  return Math.ceil(raw / 5) * 5
})

// 将数据索引/值映射到 SVG 坐标
const chartX = (i) => {
  const n = trendDays.value
  return PAD_L + (n <= 1 ? chartW.value / 2 : (i / (n - 1)) * chartW.value)
}
const chartY = (v) => PAD_T + CHART_H - (v / trendMaxY.value) * CHART_H

// Y 轴网格线
const yGridLines = computed(() => {
  const n = 4
  return Array.from({ length: n + 1 }, (_, i) => {
    const ratio = i / n
    const v = Math.round(trendMaxY.value * ratio)
    return { y: chartY(v), label: v, dash: i > 0 && i < n }
  }).reverse()
})

// X 轴刻度：每 ~70px 一个标签，并按区间设最小 step 防止过密
const xTicks = computed(() => {
  const n = trendDays.value
  if (n === 0) return []
  const maxTicks = Math.max(7, Math.floor(containerW.value / 70))
  let step = Math.ceil(n / maxTicks)
  // 各区间最小 step：保证标签密度合理
  if (n >= 60) step = Math.max(step, 7)  // 60天以上每周一标
  else if (n >= 20) step = Math.max(step, 3)  // 20-59天最少3天一标
  else if (n >= 10) step = Math.max(step, 2)  // 10-19天最少2天一标
  return trendDatesISO.value
    .map((iso, i) => {
      const d = new Date(iso + 'T00:00:00')
      return { i, x: chartX(i), label: `${d.getMonth() + 1}/${d.getDate()}` }
    })
    .filter((_, i) => i % step === 0 || i === n - 1)
})

// 贝塞尔平滑路径
const smoothPath = (pts) => {
  if (pts.length < 2) return ''
  let d = `M ${pts[0].x} ${pts[0].y}`
  for (let i = 1; i < pts.length; i++) {
    const prev = pts[i - 1], cur = pts[i]
    const cpx = (prev.x + cur.x) / 2
    d += ` C ${cpx} ${prev.y} ${cpx} ${cur.y} ${cur.x} ${cur.y}`
  }
  return d
}

const buildDots = (values) => values.map((v, i) => ({ x: chartX(i), y: chartY(v) }))

const newDots    = computed(() => buildDots(newValues.value))
const reviewDots = computed(() => buildDots(reviewValues.value))

const newLinePath    = computed(() => smoothPath(newDots.value))
const reviewLinePath = computed(() => smoothPath(reviewDots.value))

const areaPath = (pts) => {
  if (pts.length < 2) return ''
  const bottom = PAD_T + CHART_H
  return `${smoothPath(pts)} L ${pts[pts.length - 1].x} ${bottom} L ${pts[0].x} ${bottom} Z`
}
const newAreaPath    = computed(() => areaPath(newDots.value))
const reviewAreaPath = computed(() => areaPath(reviewDots.value))

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

const activeAgo = (ts) => {
  if (!ts) return { text: '从未', cls: 'ago-never' }
  const diff = Date.now() - new Date(ts).getTime()
  const days = Math.floor(diff / 86400000)
  if (days === 0) return { text: '今天', cls: 'ago-today' }
  if (days <= 3) return { text: `${days}天前`, cls: 'ago-recent' }
  if (days <= 7) return { text: `${days}天前`, cls: 'ago-week' }
  return { text: `${days}天前`, cls: 'ago-old' }
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
      platform.value             = res.data.platform || {}
      masteryDistribution.value  = res.data.masteryDistribution || []
      efDistribution.value       = res.data.efDistribution || []
      intervalDistribution.value = res.data.intervalDistribution || []
      sourceDistribution.value   = res.data.sourceDistribution || []
      // overview 默认返回近7天趋势，与 trendPreset 初始值一致
      dailyNewTrend.value        = res.data.dailyNewTrend || []
      dailyReviewTrend.value     = res.data.dailyReviewTrend || []
    }
  } catch (e) {
    ElMessage.error('加载概览失败')
  } finally {
    loading.value = false
  }
}

const loadTrend = async (start, end) => {
  trendLoading.value = true
  try {
    const res = await request({
      url: '/api/admin/vocab-stats/trend',
      method: 'get',
      params: { start, end },
    })
    if (res?.code === 200) {
      dailyNewTrend.value    = res.data.dailyNewTrend    || []
      dailyReviewTrend.value = res.data.dailyReviewTrend || []
    }
  } catch (e) {
    ElMessage.error('加载趋势失败')
  } finally {
    trendLoading.value = false
  }
}

const applyPreset = (days) => {
  trendPreset.value = days
  trendDateRange.value = null
  const end   = isoToday()
  const start = offsetDate(end, -(days - 1))
  loadTrend(start, end)
}

const onDateRangeChange = (val) => {
  if (!val) return
  trendPreset.value = 0   // 清除预设高亮
  loadTrend(val[0], val[1])
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

// ── ResizeObserver：让 svgVW 跟随容器真实像素宽度 ──
let ro = null
onMounted(() => {
  loadAll()
  ro = new ResizeObserver(entries => {
    const w = entries[0].contentRect.width
    if (w > 0) { svgVW.value = w; containerW.value = w }
  })
  if (trendRef.value) ro.observe(trendRef.value)
})
onBeforeUnmount(() => ro?.disconnect())

// ── Tooltip ──
const tooltip = ref({ visible: false, date: '', newVal: 0, reviewVal: 0,
                       svgX: 0, newY: null, reviewY: null, elX: 0, elY: 0 })

const onTrendMouseMove = (e) => {
  const rect = trendRef.value?.getBoundingClientRect()
  if (!rect) return
  const elX = e.clientX - rect.left
  const elY = e.clientY - rect.top
  // elX 对应 svgVW 中的坐标（1:1 因为 SVG 宽 == 容器宽）
  const svgX = elX
  const n = trendDays.value
  if (n === 0) return
  // 找最近数据点索引
  const step = chartW.value / Math.max(n - 1, 1)
  const idx = Math.min(Math.max(Math.round((svgX - PAD_L) / step), 0), n - 1)
  const date = trendDatesISO.value[idx]
  if (!date) return
  const d = new Date(date + 'T00:00:00')
  const snappedX = chartX(idx)
  tooltip.value = {
    visible: true,
    date: `${d.getFullYear()}/${d.getMonth() + 1}/${d.getDate()}`,
    newVal: newValues.value[idx] ?? 0,
    reviewVal: reviewValues.value[idx] ?? 0,
    svgX: snappedX,
    newY: newDots.value[idx]?.y,
    reviewY: reviewDots.value[idx]?.y,
    elX,
    elY: Math.min(elY, SVG_VH - 20),
  }
}

const onTrendMouseLeave = () => { tooltip.value.visible = false }
</script>

<style scoped lang="scss">
.vocab-stats-page {
  padding: 20px;
  max-width: 100%;
}

.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 20px;
  h2 { font-size: 20px; font-weight: 600; color: #303133; margin: 0; }
}

/* ── 概览卡片 ── */
.overview-cards {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin-bottom: 16px;
}
.ov-card {
  background: white;
  border-radius: 12px;
  padding: 14px 18px;
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 130px;
  flex: 1;
  box-shadow: 0 1px 4px rgba(0,0,0,.06);
  border: 1px solid #ebeef5;
  &.highlight { border-color: var(--xy-brand-600); background: linear-gradient(135deg,var(--xy-brand-50) 0%,#f0f7ff 100%); }
  &.warn { border-color: #f56c6c; background: #fef0f0; }
}
.ov-icon {
  width: 40px; height: 40px;
  border-radius: 10px;
  display: flex; align-items: center; justify-content: center;
  font-size: 18px;
  flex-shrink: 0;
}
.ov-value { font-size: 22px; font-weight: 700; color: #303133; line-height: 1; }
.ov-label { font-size: 12px; color: #909399; margin-top: 4px; }
.unit { font-size: 13px; font-weight: 400; color: #606266; }

/* ── 图表行 ── */
.charts-row {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 14px;
  margin-bottom: 16px;
  @media (max-width: 768px) {
    grid-template-columns: 1fr;
  }
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
.mastery-label { width: 48px; font-size: 13px; color: #606266; }
.mastery-bar-wrap { flex: 1; height: 12px; background: #f5f7fa; border-radius: 6px; overflow: hidden; }
.mastery-bar-fill {
  height: 100%; border-radius: 6px; transition: width .4s;
  &.new      { background: #c0ccda; }
  &.learning { background: #e6a23c; }
  &.mastered { background: #67c23a; }
}
.mastery-count { width: 36px; text-align: right; font-size: 13px; color: #303133; }
.mastery-pct   { width: 36px; text-align: right; font-size: 12px; color: #909399; }

.ef-bar-wrap {}
.ef-bar {
  display: flex; height: 20px; border-radius: 6px; overflow: hidden;
  background: #f5f7fa; margin-bottom: 8px;
}
.ef-seg {
  height: 100%;
  &.ef-hard   { background: linear-gradient(90deg,#fca5a5,#f87171); }
  &.ef-normal { background: linear-gradient(90deg,#fde68a,#fbbf24); }
  &.ef-easy   { background: linear-gradient(90deg,#86efac,#4ade80); }
}
.ef-legend { display: flex; flex-wrap: wrap; gap: 6px; font-size: 12px; }
.ef-legend-item {
  padding: 2px 6px; border-radius: 4px;
  &.ef-hard   { background: #fee2e2; color: #b91c1c; }
  &.ef-normal { background: #fef9c3; color: #92400e; }
  &.ef-easy   { background: #dcfce7; color: #15803d; }
}
.ef-hint {
  margin-top: 10px; font-size: 12px; color: #606266;
  display: flex; align-items: center; gap: 8px; flex-wrap: wrap;
}
.dot {
  display: inline-block; width: 8px; height: 8px; border-radius: 50%;
  &.hard   { background: #f87171; }
  &.normal { background: #fbbf24; }
  &.easy   { background: #4ade80; }
}

/* 来源分布 */
.source-pie-wrap { padding: 8px 0; }
.source-pie {
  display: flex;
  height: 80px;
  border-radius: 10px;
  overflow: hidden;
  margin-bottom: 12px;
  box-shadow: 0 2px 8px rgba(0,0,0,.08);
}
.pie-seg {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  font-weight: 600;
  font-size: 13px;
  text-align: center;
  transition: all .3s;
  &:hover { transform: scale(1.05); filter: brightness(1.1); }
  &.article { background: linear-gradient(135deg,#667eea 0%,#764ba2 100%); }
  &.manual  { background: linear-gradient(135deg,#f093fb 0%,#f5576c 100%); }
}
.pie-label { z-index: 1; text-shadow: 0 1px 2px rgba(0,0,0,.3); line-height: 1.4; }
.source-legend {
  display: flex;
  gap: 16px;
  font-size: 13px;
  justify-content: center;
}
.sl-item { display: flex; align-items: center; gap: 6px; }
.sl-dot {
  width: 10px; height: 10px; border-radius: 50%;
  &.article { background: #667eea; }
  &.manual  { background: #f5576c; }
}
.sl-text { color: #606266; }
.sl-pct  { color: #909399; font-weight: 600; }

/* 复习间隔分布 */
.interval-bars { display: flex; flex-direction: column; gap: 8px; }
.interval-row {
  display: flex; align-items: center; gap: 8px;
  &.empty { opacity: .4; }
}
.ir-label { width: 65px; font-size: 12px; color: #606266; white-space: nowrap; }
.ir-bar-wrap { flex: 1; height: 16px; background: #f5f7fa; border-radius: 8px; overflow: hidden; }
.ir-bar-fill {
  height: 100%; background: linear-gradient(90deg,#a0d8f1,var(--xy-brand-600)); border-radius: 8px; transition: width .4s;
}
.ir-count { width: 32px; text-align: right; font-size: 13px; font-weight: 600; color: #303133; }

/* ── 趋势图 ── */
.trend-card {
  background: white;
  border-radius: 12px;
  padding: 16px;
  margin-bottom: 16px;
  box-shadow: 0 1px 4px rgba(0,0,0,.06);
  border: 1px solid #ebeef5;
}
.card-title-row {
  display: flex; justify-content: space-between; align-items: center; margin-bottom: 14px;
}
.trend-controls {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}
.trend-legend { display: flex; gap: 16px; font-size: 12px; }
.tl-item {
  display: flex; align-items: center; gap: 5px;
  &::before { content: ''; display: inline-block; width: 20px; height: 2.5px; border-radius: 2px; }
  &.new-line::before    { background: var(--xy-brand-600); }
  &.review-line::before { background: #e6a23c; }
}
/* 趋势图包裹层 */
.trend-wrap {
  position: relative;
  padding-left: 38px;   /* 为 Y 轴标签留空 */
  padding-bottom: 24px; /* 为 X 轴标签留空 */
}
.trend-svg { width: 100%; height: 200px; display: block; overflow: visible; }

/* Y 轴标签（HTML 叠加，位置由 top 驱动，left 固定在左边） */
.y-labels { position: absolute; left: 0; top: 0; pointer-events: none; }
.y-label {
  position: absolute;
  right: 6px;
  transform: translateY(-50%);
  font-size: 11px;
  color: #c0c4cc;
  white-space: nowrap;
}

/* X 轴标签（相对于图表区域 left 百分比） */
.x-labels {
  position: absolute;
  bottom: 2px;
  left: 0;
  right: 0;
  pointer-events: none;
}
.x-label {
  position: absolute;
  transform: translateX(-50%);
  font-size: 11px;
  color: #909399;
  white-space: nowrap;
}

/* Tooltip */
.trend-tooltip {
  position: absolute;
  background: rgba(40, 44, 52, 0.88);
  backdrop-filter: blur(4px);
  color: #fff;
  border-radius: 8px;
  padding: 8px 12px;
  font-size: 12px;
  pointer-events: none;
  z-index: 10;
  min-width: 120px;
  box-shadow: 0 4px 12px rgba(0,0,0,.2);
  line-height: 1.8;
}
.tt-date { font-size: 11px; color: #bdc3c7; margin-bottom: 4px; }
.tt-row { display: flex; align-items: center; gap: 6px; justify-content: space-between; }
.tt-row::before { content: ''; display: inline-block; width: 8px; height: 8px; border-radius: 50%; flex-shrink: 0; }
.tt-row.new-dot::before    { background: var(--xy-brand-600); }
.tt-row.review-dot::before { background: #e6a23c; }

/* ── 学生表 ── */
.table-card {
  background: white; border-radius: 12px; padding: 16px;
  box-shadow: 0 1px 4px rgba(0,0,0,.06); border: 1px solid #ebeef5;
}

.inline-bar {
  display: flex; height: 6px; border-radius: 3px; overflow: hidden; background: #f5f7fa;
  .ib-seg {
    height: 100%;
    &.new      { background: #c0ccda; }
    &.learning { background: #e6a23c; }
    &.mastered { background: #67c23a; }
  }
}
.ib-counts { font-size: 11px; margin-top: 2px; display: flex; gap: 6px; }
.c-new      { color: #909399; }
.c-learning { color: #e6a23c; }
.c-mastered { color: #67c23a; }
.text-muted { color: #c0c4cc; }

.last-active {
  font-size: 12px;
  &.ago-today  { color: #67c23a; font-weight: 600; }
  &.ago-recent { color: var(--xy-brand-600); }
  &.ago-week   { color: #e6a23c; }
  &.ago-old    { color: #c0c4cc; }
  &.ago-never  { color: #dcdfe6; }
}
</style>
