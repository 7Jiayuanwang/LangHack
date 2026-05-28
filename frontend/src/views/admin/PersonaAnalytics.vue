<template>
  <div class="persona-analytics">
    <div class="page-header">
      <h2>学习者画像分析</h2>
      <el-radio-group v-model="period" size="small" @change="loadData">
        <el-radio-button :value="7">7天</el-radio-button>
        <el-radio-button :value="14">14天</el-radio-button>
        <el-radio-button :value="30">30天</el-radio-button>
      </el-radio-group>
    </div>

    <!-- 趋势 + 分布图表 -->
    <div class="chart-row" v-loading="loading">
      <div class="chart-card">
        <h3>风险等级趋势</h3>
        <v-chart v-if="trendChartOption.series" :option="trendChartOption" style="height:280px" autoresize />
        <div v-else class="empty-hint">暂无快照趋势数据</div>
      </div>
      <div class="chart-card chart-card-small">
        <h3>风险分布（最新）</h3>
        <v-chart v-if="riskPieOption.series" :option="riskPieOption" style="height:240px" autoresize />
        <div v-else class="empty-hint">暂无数据</div>
      </div>
    </div>

    <div class="chart-row" v-loading="loading">
      <div class="chart-card">
        <h3>活跃度 & 逾期率趋势</h3>
        <v-chart v-if="activityChartOption.series" :option="activityChartOption" style="height:260px" autoresize />
        <div v-else class="empty-hint">暂无数据</div>
      </div>
      <div class="chart-card chart-card-small">
        <h3>标签命中次数</h3>
        <v-chart v-if="tagBarOption.series" :option="tagBarOption" style="height:240px" autoresize />
        <div v-else class="empty-hint">暂无数据</div>
      </div>
    </div>

    <!-- 推荐效果 -->
    <div class="section">
      <h3>推荐效果</h3>
      <div v-loading="loading">
        <div v-if="ctrBySource.length === 0 && !loading" class="empty-hint">
          暂无推荐事件数据，请先开启事件追踪功能
        </div>
        <el-table v-else :data="ctrBySource" stripe size="small">
          <el-table-column prop="source" label="推荐来源" width="140">
            <template #default="{ row }">{{ sourceLabel(row.source) }}</template>
          </el-table-column>
          <el-table-column prop="impressions" label="展示次数" width="100" />
          <el-table-column prop="clicks" label="点击次数" width="100" />
          <el-table-column label="点击率">
            <template #default="{ row }">
              {{ row.impressions > 0 ? ((row.clicks / row.impressions) * 100).toFixed(1) : '0.0' }}%
            </template>
          </el-table-column>
        </el-table>
      </div>
    </div>

    <!-- 标签有效性 -->
    <div class="section">
      <h3>标签有效性</h3>
      <div v-loading="loading">
        <div v-if="tagEffectiveness.length === 0 && !loading" class="empty-hint">
          暂无标签效果数据
        </div>
        <el-table v-else :data="tagEffectiveness" stripe size="small">
          <el-table-column prop="personaTags" label="标签组合">
            <template #default="{ row }">{{ formatTags(row.personaTags) }}</template>
          </el-table-column>
          <el-table-column prop="impressions" label="展示次数" width="100" />
          <el-table-column prop="clicks" label="点击次数" width="100" />
          <el-table-column label="点击率" width="100">
            <template #default="{ row }">
              {{ (row.ctr * 100).toFixed(1) }}%
            </template>
          </el-table-column>
        </el-table>
      </div>
    </div>

    <!-- 校准建议 -->
    <div class="section">
      <h3>校准建议</h3>
      <div v-loading="loading">
        <div v-if="proposals.length === 0 && !loading" class="empty-hint">
          当前无校准建议
        </div>
        <div v-for="(p, i) in proposals" :key="i" class="proposal-card">
          <div class="proposal-header">
            <el-tag :type="p.proposal === 'tighten' ? 'warning' : 'info'" size="small">
              {{ p.proposal === 'tighten' ? '收紧' : '放宽' }}
            </el-tag>
            <span class="tag-code">{{ tagLabel(p.tagCode) }}</span>
          </div>
          <div class="proposal-reason">{{ p.reason }}</div>
          <div class="proposal-rate">命中率: {{ (p.hitRate * 100).toFixed(1) }}%</div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import VChart from 'vue-echarts'
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { PieChart, BarChart, LineChart } from 'echarts/charts'
import {
  GridComponent, TooltipComponent, LegendComponent, TitleComponent
} from 'echarts/components'
import {
  getAnalyticsOverview,
  getAnalyticsTrends,
  getTagEffectiveness,
  getRecalibrationProposals
} from '@/api/learnerProfile'

use([CanvasRenderer, PieChart, BarChart, LineChart, GridComponent, TooltipComponent, LegendComponent, TitleComponent])

const loading = ref(false)
const period = ref(30)
const ctrBySource = ref([])
const tagEffectiveness = ref([])
const proposals = ref([])
const trendData = ref({ dailyPoints: [], tagHitCounts: {} })

const TAG_NAME_MAP = {
  INACTIVE_RISK: '流失风险', VOCAB_WEAK: '词汇弱势', REVIEW_OVERDUE: '复习逾期',
  CONSISTENT_LEARNER: '学习稳定', CONTENT_LEVEL_STUCK: '版本停滞', CONTENT_LEVEL_JUMP: '版本跳跃',
  IMPROVING_TREND: '进步趋势', DECLINING_TREND: '下滑趋势', REVIEW_CHAMPION: '复习达人',
}
const tagLabel = (code) => TAG_NAME_MAP[code] || code

const SOURCE_NAME_MAP = {
  collaborative: '协同过滤', content_based: '内容推荐', popularity: '热门推荐',
  persona: '画像推荐', random: '随机推荐', manual: '人工推荐', RECOMMEND: '混合推荐',
}
const sourceLabel = (source) => SOURCE_NAME_MAP[source] || source
const formatTags = (tags) => {
  if (!tags) return ''
  return tags.split(',').map(t => tagLabel(t.trim())).join('、')
}

const RISK_COLORS = { high: '#F56C6C', medium: '#E6A23C', low: '#67C23A' }

// ── 风险趋势折线图 ──
const trendChartOption = computed(() => {
  const pts = trendData.value.dailyPoints
  if (!pts || pts.length === 0) return {}
  const dates = pts.map(p => p.date)
  return {
    tooltip: { trigger: 'axis' },
    legend: { data: ['高风险', '中风险', '低风险'], bottom: 0 },
    grid: { left: 40, right: 16, top: 12, bottom: 36 },
    xAxis: { type: 'category', data: dates, axisLabel: { fontSize: 11 } },
    yAxis: { type: 'value', minInterval: 1 },
    series: [
      { name: '高风险', type: 'line', data: pts.map(p => p.riskHigh), smooth: true, itemStyle: { color: RISK_COLORS.high } },
      { name: '中风险', type: 'line', data: pts.map(p => p.riskMedium), smooth: true, itemStyle: { color: RISK_COLORS.medium } },
      { name: '低风险', type: 'line', data: pts.map(p => p.riskLow), smooth: true, itemStyle: { color: RISK_COLORS.low } },
    ],
  }
})

// ── 风险分布饼图（最新一天） ──
const riskPieOption = computed(() => {
  const pts = trendData.value.dailyPoints
  if (!pts || pts.length === 0) return {}
  const latest = pts[pts.length - 1]
  const data = [
    { value: latest.riskHigh, name: '高风险', itemStyle: { color: RISK_COLORS.high } },
    { value: latest.riskMedium, name: '中风险', itemStyle: { color: RISK_COLORS.medium } },
    { value: latest.riskLow, name: '低风险', itemStyle: { color: RISK_COLORS.low } },
  ].filter(d => d.value > 0)
  if (data.length === 0) return {}
  return {
    tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
    legend: { bottom: 0, itemWidth: 10, itemHeight: 10, textStyle: { fontSize: 11 } },
    series: [{
      type: 'pie', radius: ['40%', '70%'],
      label: { show: false },
      data,
    }],
  }
})

// ── 活跃度 + 逾期率趋势 ──
const activityChartOption = computed(() => {
  const pts = trendData.value.dailyPoints
  if (!pts || pts.length === 0) return {}
  const dates = pts.map(p => p.date)
  return {
    tooltip: { trigger: 'axis' },
    legend: { data: ['平均活跃天数(7d)', '平均学习时长(14d)', '平均逾期率'], bottom: 0 },
    grid: { left: 50, right: 50, top: 12, bottom: 36 },
    xAxis: { type: 'category', data: dates, axisLabel: { fontSize: 11 } },
    yAxis: [
      { type: 'value', name: '天/分钟', nameTextStyle: { fontSize: 11 } },
      { type: 'value', name: '逾期率', nameTextStyle: { fontSize: 11 }, max: 1, axisLabel: { formatter: v => (v * 100).toFixed(0) + '%' } },
    ],
    series: [
      { name: '平均活跃天数(7d)', type: 'line', data: pts.map(p => p.avgActiveDays7d), smooth: true, itemStyle: { color: '#409EFF' } },
      { name: '平均学习时长(14d)', type: 'bar', data: pts.map(p => p.avgStudyMinutes14d), itemStyle: { color: '#b3d8ff' }, barMaxWidth: 16 },
      { name: '平均逾期率', type: 'line', yAxisIndex: 1, data: pts.map(p => p.avgOverdueReviewRatio), smooth: true, itemStyle: { color: '#F56C6C' }, lineStyle: { type: 'dashed' } },
    ],
  }
})

// ── 标签命中次数柱状图 ──
const tagBarOption = computed(() => {
  const counts = trendData.value.tagHitCounts
  if (!counts || Object.keys(counts).length === 0) return {}
  const sorted = Object.entries(counts).sort((a, b) => b[1] - a[1])
  return {
    tooltip: { trigger: 'axis' },
    grid: { left: 80, right: 16, top: 8, bottom: 8 },
    xAxis: { type: 'value', minInterval: 1 },
    yAxis: { type: 'category', data: sorted.map(([k]) => tagLabel(k)), axisLabel: { fontSize: 11 }, inverse: true },
    series: [{
      type: 'bar', data: sorted.map(([, v]) => v),
      barMaxWidth: 18,
      itemStyle: { color: '#409EFF', borderRadius: [0, 4, 4, 0] },
      label: { show: true, position: 'right', fontSize: 11 },
    }],
  }
})

async function loadData() {
  loading.value = true
  try {
    const [overviewRes, trendsRes, tagRes, proposalRes] = await Promise.all([
      getAnalyticsOverview(),
      getAnalyticsTrends(period.value),
      getTagEffectiveness(period.value),
      getRecalibrationProposals()
    ])
    ctrBySource.value = overviewRes?.data?.ctrBySource || []
    trendData.value = trendsRes?.data || { dailyPoints: [], tagHitCounts: {} }
    tagEffectiveness.value = tagRes?.data || []
    proposals.value = proposalRes?.data || []
  } catch (e) {
    console.error('加载分析数据失败:', e)
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  loadData()
})
</script>

<style scoped>
.persona-analytics {
  padding: 16px;
}
.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}
.page-header h2 {
  margin: 0;
  font-size: 18px;
  color: var(--text-primary);
}
.chart-row {
  display: flex;
  gap: 16px;
  margin-bottom: 16px;
}
.chart-card {
  flex: 1;
  background: var(--bg-primary);
  border: 1px solid var(--border-light);
  border-radius: 8px;
  padding: 12px;
}
.chart-card-small {
  flex: 0 0 320px;
}
.chart-card h3 {
  margin: 0 0 8px 0;
  font-size: 14px;
  color: var(--text-primary);
}
.section {
  margin-bottom: 24px;
}
.section h3 {
  margin: 0 0 12px 0;
  font-size: 15px;
  color: var(--text-primary);
  border-bottom: 1px solid var(--border-light);
  padding-bottom: 8px;
}
.empty-hint {
  padding: 20px;
  text-align: center;
  color: var(--text-tertiary);
  font-size: 13px;
}
.proposal-card {
  background: var(--bg-primary);
  border: 1px solid var(--border-light);
  border-radius: 8px;
  padding: 12px;
  margin-bottom: 8px;
}
.proposal-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
}
.tag-code {
  font-weight: 600;
  font-size: 14px;
}
.proposal-reason {
  font-size: 13px;
  color: var(--text-secondary);
  margin-bottom: 4px;
}
.proposal-rate {
  font-size: 12px;
  color: var(--text-tertiary);
}

@media (max-width: 768px) {
  .chart-row {
    flex-direction: column;
  }
  .chart-card-small {
    flex: 1;
  }
}
</style>
