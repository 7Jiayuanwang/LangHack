<template>
  <div class="cet-analysis-container">
    <div class="page-header">
      <div>
        <h2 class="page-title">四六级成绩详细分析</h2>
        <p class="page-subtitle">使用平台 vs 未使用平台的成绩对比分析（仅展示汇总数据）</p>
      </div>
      <el-button @click="loadAll" :loading="loading">
        <el-icon><Refresh /></el-icon> 刷新
      </el-button>
    </div>

    <div v-if="loading" class="loading-center">
      <el-icon class="is-loading" :size="40"><Loading /></el-icon>
    </div>

    <template v-else-if="summary">
      <!-- 摘要卡片 -->
      <div class="summary-grid">
        <div class="summary-card green">
          <div class="summary-value">+{{ summary.improvement }}%</div>
          <div class="summary-label">通过率提升</div>
          <div class="summary-sub">使用平台学生高出未使用学生</div>
        </div>
        <div class="summary-card">
          <div class="summary-value">{{ summary.used?.passRate || 0 }}%</div>
          <div class="summary-label">使用平台通过率</div>
          <div class="summary-sub">{{ summary.used?.passed || 0 }} / {{ summary.used?.totalStudents || 0 }} 人通过</div>
        </div>
        <div class="summary-card">
          <div class="summary-value">{{ summary.unused?.passRate || 0 }}%</div>
          <div class="summary-label">未使用平台通过率</div>
          <div class="summary-sub">{{ summary.unused?.passed || 0 }} / {{ summary.unused?.totalStudents || 0 }} 人通过</div>
        </div>
        <div class="summary-card">
          <div class="summary-value">{{ summary.totalScoreRecords }}</div>
          <div class="summary-label">成绩记录总数</div>
          <div class="summary-sub">已同步的考试成绩</div>
        </div>
      </div>

      <!-- 按考试类型分组 -->
      <el-card class="glass-card mt-24" v-if="detailed?.byExamType?.length">
        <template #header>
          <span class="card-title">按考试类型分析</span>
        </template>
        <el-table :data="detailed.byExamType" stripe>
          <el-table-column prop="exam_type" label="考试类型" width="120" />
          <el-table-column prop="total" label="总人数" width="100" />
          <el-table-column prop="passed" label="通过人数" width="100" />
          <el-table-column label="通过率" width="200">
            <template #default="{ row }">
              <div style="display:flex;align-items:center;gap:8px">
                <el-progress :percentage="Number(row.pass_rate) || 0" :show-text="false" :stroke-width="8" style="flex:1" />
                <span style="font-weight:700;min-width:48px">{{ row.pass_rate }}%</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column prop="avg_score" label="平均分" width="100" />
          <el-table-column prop="min_score" label="最低分" width="100" />
          <el-table-column prop="max_score" label="最高分" width="100" />
        </el-table>
      </el-card>

      <!-- 分数段分布对比图 -->
      <el-card class="glass-card mt-24" v-if="detailed?.scoreDistUsed?.length || detailed?.scoreDistUnused?.length">
        <template #header>
          <span class="card-title">分数段分布对比</span>
        </template>
        <div style="height:360px">
          <v-chart :option="distChartOption" autoresize />
        </div>
      </el-card>

      <!-- 通过率趋势 -->
      <el-card class="glass-card mt-24" v-if="detailed?.trend?.length">
        <template #header>
          <span class="card-title">历年通过率趋势</span>
        </template>
        <div style="height:320px">
          <v-chart :option="trendChartOption" autoresize />
        </div>
      </el-card>
    </template>

    <el-empty v-else description="暂无四六级成绩数据，请先在运维面板同步学校数据" :image-size="120" />
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { Refresh, Loading } from '@element-plus/icons-vue'
import request from '@/utils/request'
import VChart from 'vue-echarts'
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { BarChart, LineChart } from 'echarts/charts'
import {
  GridComponent, TooltipComponent, LegendComponent, TitleComponent
} from 'echarts/components'

use([CanvasRenderer, BarChart, LineChart, GridComponent, TooltipComponent, LegendComponent, TitleComponent])

const loading = ref(false)
const summary = ref(null)
const detailed = ref(null)

const SCORE_RANGES = ['300以下', '300-349', '350-399', '400-424', '425-499', '500-549', '550以上']

const distChartOption = computed(() => {
  if (!detailed.value) return {}
  const toMap = (arr) => Object.fromEntries((arr || []).map(r => [r.score_range, r.count]))
  const usedMap = toMap(detailed.value.scoreDistUsed)
  const unusedMap = toMap(detailed.value.scoreDistUnused)
  return {
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    legend: { data: ['使用平台', '未使用平台'] },
    grid: { left: '3%', right: '4%', bottom: '3%', containLabel: true },
    xAxis: { type: 'category', data: SCORE_RANGES, axisLabel: { rotate: 30 } },
    yAxis: { type: 'value', name: '人数' },
    series: [
      {
        name: '使用平台',
        type: 'bar',
        data: SCORE_RANGES.map(r => usedMap[r] || 0),
        itemStyle: { color: '#10b981' }
      },
      {
        name: '未使用平台',
        type: 'bar',
        data: SCORE_RANGES.map(r => unusedMap[r] || 0),
        itemStyle: { color: '#94a3b8' }
      }
    ]
  }
})

const trendChartOption = computed(() => {
  if (!detailed.value?.trend?.length) return {}
  const years = [...new Set(detailed.value.trend.map(r => r.exam_year))].sort()
  const types = [...new Set(detailed.value.trend.map(r => r.exam_type))]
  const series = types.map(type => ({
    name: type,
    type: 'line',
    smooth: true,
    data: years.map(y => {
      const row = detailed.value.trend.find(r => r.exam_year === y && r.exam_type === type)
      return row ? Number(row.pass_rate) : null
    })
  }))
  return {
    tooltip: { trigger: 'axis' },
    legend: { data: types },
    grid: { left: '3%', right: '4%', bottom: '3%', containLabel: true },
    xAxis: { type: 'category', data: years.map(String) },
    yAxis: { type: 'value', name: '通过率 (%)', max: 100 },
    series
  }
})

const loadAll = async () => {
  loading.value = true
  try {
    const [r1, r2] = await Promise.all([
      request({ url: '/api/admin/metrics/cet-comparison', method: 'get' }),
      request({ url: '/api/admin/metrics/cet-detailed', method: 'get' })
    ])
    if (r1?.code === 200 && r1?.data?.totalScoreRecords > 0) {
      summary.value = r1.data
    }
    if (r2?.code === 200) {
      detailed.value = r2.data
    }
  } catch (e) {
    console.error('加载四六级分析数据失败:', e)
  } finally {
    loading.value = false
  }
}

onMounted(loadAll)
</script>

<style scoped>
.cet-analysis-container {
  max-width: 1200px;
  margin: 0 auto;
  padding: 32px;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 32px;
}

.page-title {
  font-size: 24px;
  font-weight: 800;
  color: #1e293b;
  margin: 0 0 4px;
}

.page-subtitle {
  font-size: 14px;
  color: #64748b;
  margin: 0;
}

.loading-center {
  display: flex;
  justify-content: center;
  padding: 80px;
}

.summary-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: 20px;
  margin-bottom: 8px;
}

.summary-card {
  background: white;
  border-radius: 16px;
  padding: 24px;
  box-shadow: 0 2px 8px rgba(0,0,0,0.06);
  border-bottom: 4px solid #e2e8f0;
}

.summary-card.green {
  background: linear-gradient(135deg, #10b981, #059669);
  border-bottom-color: #047857;
}

.summary-card.green .summary-value,
.summary-card.green .summary-label,
.summary-card.green .summary-sub {
  color: white;
}

.summary-value {
  font-size: 32px;
  font-weight: 800;
  color: #1e293b;
  line-height: 1;
}

.summary-label {
  font-size: 15px;
  font-weight: 600;
  color: #475569;
  margin-top: 8px;
}

.summary-sub {
  font-size: 12px;
  color: #94a3b8;
  margin-top: 4px;
}

.glass-card {
  border-radius: 16px;
  border: 1px solid #e2e8f0;
}

.card-title {
  font-size: 16px;
  font-weight: 700;
  color: #1e293b;
}

.mt-24 { margin-top: 24px; }
</style>
