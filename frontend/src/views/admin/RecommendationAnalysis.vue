<template>
  <div class="rec-analysis-page">
    <div class="page-header">
      <h2>推荐算法分析</h2>
      <el-button @click="loadData" :loading="loading" size="small">刷新</el-button>
    </div>

    <div v-if="loading" class="center-loading">
      <el-icon class="is-loading" :size="40"><Loading /></el-icon>
    </div>

    <template v-else-if="data">
      <!-- 覆盖率卡片 -->
      <div class="stat-grid">
        <div class="stat-card">
          <div class="stat-value">{{ data.coverage?.total_users ?? 0 }}</div>
          <div class="stat-label">活跃用户</div>
        </div>
        <div class="stat-card highlight">
          <div class="stat-value">{{ data.coverage?.cf_users ?? 0 }}</div>
          <div class="stat-label">CF 推荐用户</div>
          <div class="stat-sub">已读 ≥ 3 篇</div>
        </div>
        <div class="stat-card">
          <div class="stat-value">{{ data.coverage?.cold_start_users ?? 0 }}</div>
          <div class="stat-label">冷启动用户</div>
          <div class="stat-sub">已读 &lt; 3 篇</div>
        </div>
        <div class="stat-card">
          <div class="stat-value">{{ cfCoverageRate }}%</div>
          <div class="stat-label">CF 覆盖率</div>
        </div>
        <div class="stat-card">
          <div class="stat-value">{{ data.similarityMatrix?.total_pairs ?? 0 }}</div>
          <div class="stat-label">相似度对数</div>
        </div>
        <div class="stat-card">
          <div class="stat-value">{{ data.similarityMatrix?.avg_similarity ?? '—' }}</div>
          <div class="stat-label">平均相似度</div>
        </div>
      </div>

      <div class="section-card" v-if="feedMetrics">
        <h3>新首页 Feed 学习效果（近 {{ feedMetrics.days }} 天）</h3>
        <div class="stat-grid">
          <div class="stat-card"><div class="stat-value">{{ feedMetrics.exposedPairs }}</div><div class="stat-label">实际看见的用户-文章对</div></div>
          <div class="stat-card"><div class="stat-value">{{ feedMetrics.clickedPairs }}</div><div class="stat-label">点击的用户-文章对</div></div>
          <div class="stat-card highlight"><div class="stat-value">{{ feedMetrics.completedPairs }}</div><div class="stat-label">点击后 7 天内完读</div></div>
          <div class="stat-card"><div class="stat-value">{{ Math.round(feedMetrics.completionPerClickRate * 100) }}%</div><div class="stat-label">点击后完读率</div></div>
        </div>
        <p class="metric-note">按用户与文章去重；曝光只在卡片实际进入视野时记录。点击不等于完成阅读。</p>
      </div>

      <!-- 算法配置 -->
      <div class="section-card">
        <h3>算法配置</h3>
        <div class="config-grid">
          <div class="config-item" v-for="(val, key) in data.config" :key="key">
            <span class="config-key">{{ CONFIG_LABELS[key] || key }}</span>
            <span class="config-val">{{ val }}</span>
          </div>
        </div>
      </div>

      <!-- 相似度分布 -->
      <div class="section-card" v-if="data.similarityMatrix?.total_pairs > 0">
        <h3>相似度分布</h3>
        <div class="sim-bars">
          <div class="sim-bar-row">
            <span class="sim-label">高相似 (≥0.7)</span>
            <div class="sim-bar-track">
              <div class="sim-bar high" :style="{ width: simPct('high_sim_pairs') + '%' }"></div>
            </div>
            <span class="sim-count">{{ data.similarityMatrix?.high_sim_pairs ?? 0 }}</span>
          </div>
          <div class="sim-bar-row">
            <span class="sim-label">中相似 (0.4-0.7)</span>
            <div class="sim-bar-track">
              <div class="sim-bar mid" :style="{ width: simPct('mid_sim_pairs') + '%' }"></div>
            </div>
            <span class="sim-count">{{ data.similarityMatrix?.mid_sim_pairs ?? 0 }}</span>
          </div>
          <div class="sim-bar-row">
            <span class="sim-label">低相似 (&lt;0.4)</span>
            <div class="sim-bar-track">
              <div class="sim-bar low" :style="{ width: simPct('low_sim_pairs') + '%' }"></div>
            </div>
            <span class="sim-count">{{ data.similarityMatrix?.low_sim_pairs ?? 0 }}</span>
          </div>
        </div>
      </div>

      <!-- 交互分数分布图 -->
      <div class="section-card" v-if="interactionChartOption">
        <h3>用户交互分数分布</h3>
        <VChart :option="interactionChartOption" style="height:220px" autoresize />
      </div>

      <!-- 高连接度文章 -->
      <div class="section-card" v-if="data.topLinkedTexts?.length">
        <h3>高相似度连接文章 Top 10</h3>
        <el-table :data="data.topLinkedTexts" size="small" stripe>
          <el-table-column prop="id" label="ID" width="70" />
          <el-table-column prop="title" label="标题" min-width="200" show-overflow-tooltip />
          <el-table-column prop="difficulty" label="难度" width="80">
            <template #default="{ row }">
              <el-tag :type="diffTagType(row.difficulty)" size="small">{{ row.difficulty }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="similarity_links" label="相似度链接数" width="120" />
        </el-table>
      </div>
    </template>

    <div v-else-if="error" class="error-state">
      <p>{{ error }}</p>
      <el-button @click="loadData">重试</el-button>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { Loading } from '@element-plus/icons-vue'
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { BarChart } from 'echarts/charts'
import { GridComponent, TooltipComponent } from 'echarts/components'
import VChart from 'vue-echarts'
import request from '@/utils/request'

use([CanvasRenderer, BarChart, GridComponent, TooltipComponent])

const loading = ref(false)
const data = ref(null)
const feedMetrics = ref(null)
const error = ref('')

const CONFIG_LABELS = {
  itemCfWeight: 'Item-CF 权重',
  userCfWeight: 'User-CF 权重',
  coldStartMinReads: '冷启动阈值（篇）',
  textSimilarityThreshold: '文章相似度阈值',
  userSimilarityThreshold: '用户相似度阈值'
}

const cfCoverageRate = computed(() => {
  const total = data.value?.coverage?.total_users
  const cf = data.value?.coverage?.cf_users
  if (!total) return 0
  return Math.round((cf / total) * 100)
})

const simPct = (key) => {
  const total = data.value?.similarityMatrix?.total_pairs
  if (!total) return 0
  return Math.round((data.value.similarityMatrix[key] / total) * 100)
}

const interactionChartOption = computed(() => {
  const dist = data.value?.interactionDist
  if (!dist?.length) return null
  return {
    tooltip: { trigger: 'axis' },
    xAxis: { type: 'category', data: dist.map(d => d.score_range) },
    yAxis: { type: 'value', name: '用户-文章对数' },
    series: [{
      type: 'bar',
      data: dist.map(d => d.count),
      itemStyle: { color: '#197fe6', borderRadius: [4, 4, 0, 0] }
    }],
    grid: { left: 60, right: 20, top: 20, bottom: 40 }
  }
})

const diffTagType = (d) => ({ EASY: 'success', MEDIUM: 'warning', HARD: 'danger' }[d] || 'info')

const loadData = async () => {
  loading.value = true
  error.value = ''
  try {
    const [res, feedRes] = await Promise.all([
      request({ url: '/api/admin/metrics/recommendation', method: 'get' }),
      request({ url: '/api/admin/metrics/feed-learning', method: 'get', params: { days: 30 } })
        .catch(() => null)
    ])
    feedMetrics.value = feedRes?.code === 200 ? feedRes.data : null
    if (res?.code === 200) {
      data.value = res.data
    } else {
      error.value = res?.message || '加载失败'
    }
  } catch (e) {
    error.value = '加载失败，请稍后重试'
  } finally {
    loading.value = false
  }
}

onMounted(loadData)
</script>

<style scoped>
.rec-analysis-page { padding: 24px; max-width: 1000px; margin: 0 auto; }
.page-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 24px; }
.page-header h2 { font-size: 20px; font-weight: 700; color: #1e293b; margin: 0; }

.center-loading { display: flex; justify-content: center; padding: 80px 0; }
.error-state { text-align: center; padding: 60px 0; color: #ef4444; }

.stat-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(150px, 1fr)); gap: 12px; margin-bottom: 20px; }
.stat-card {
  background: white;
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  padding: 16px;
  text-align: center;
}
.stat-card.highlight { border-color: #197fe6; background: var(--xy-brand-50); }
.stat-value { font-size: 28px; font-weight: 800; color: #1e293b; }
.stat-card.highlight .stat-value { color: #197fe6; }
.stat-label { font-size: 12px; color: #64748b; margin-top: 4px; }
.stat-sub { font-size: 11px; color: #94a3b8; margin-top: 2px; }
.metric-note { margin: 0; font-size: 12px; color: #64748b; }

.section-card { background: white; border: 1px solid #e2e8f0; border-radius: 12px; padding: 20px; margin-bottom: 16px; }
.section-card h3 { font-size: 15px; font-weight: 700; color: #1e293b; margin: 0 0 16px; }

.config-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(200px, 1fr)); gap: 8px; }
.config-item { display: flex; justify-content: space-between; padding: 8px 12px; background: #f8fafc; border-radius: 8px; }
.config-key { font-size: 13px; color: #64748b; }
.config-val { font-size: 13px; font-weight: 600; color: #1e293b; }

.sim-bars { display: flex; flex-direction: column; gap: 10px; }
.sim-bar-row { display: flex; align-items: center; gap: 10px; }
.sim-label { width: 130px; font-size: 13px; color: #64748b; flex-shrink: 0; }
.sim-bar-track { flex: 1; height: 12px; background: #f1f5f9; border-radius: 6px; overflow: hidden; }
.sim-bar { height: 100%; border-radius: 6px; transition: width 0.5s ease; }
.sim-bar.high { background: #22c55e; }
.sim-bar.mid { background: #f59e0b; }
.sim-bar.low { background: #94a3b8; }
.sim-count { width: 50px; text-align: right; font-size: 13px; color: #1e293b; font-weight: 600; }
</style>
