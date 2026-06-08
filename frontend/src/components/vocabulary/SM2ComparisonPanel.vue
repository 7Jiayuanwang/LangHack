<template>
 <div class="ab-comparison">
 <div class="ab-header">
 <div class="ab-title">
 <el-icon><DataAnalysis /></el-icon>
 <span>SM-2 模式对比分析</span>
 <el-tag size="small" type="info" effect="plain">模拟实验</el-tag>
 </div>
 <el-button type="primary" size="small" @click="runSimulation" :loading="loading">
 <el-icon><VideoPlay /></el-icon>
 运行模拟
 </el-button>
 </div>

 <div v-if="!results" class="ab-intro">
 <el-alert type="info" :closable="false" show-icon>
 <template #title>基于您的真实生词本，在本地模拟不同 SM-2 策略的效果</template>
 <template #default>
 <p>以您当前 {{ props.words.length }} 个单词的 EF / 间隔 / 掌握度为起点，在内存中模拟 4 种模式 30 轮复习后的表现，包括：</p>
 <ul>
 <li>成功率 / 掌握率对比</li>
 <li>EF 难度因子演化趋势</li>
 <li>平均复习间隔与过载情况</li>
 </ul>
 <p class="ab-note">纯本地计算，不修改任何真实数据。结果越接近真实词库状态越准确。</p>
 </template>
 </el-alert>
 </div>

 <div v-else class="ab-results">
 <!-- 摘要卡片 -->
 <div class="summary-cards">
 <div
 v-for="(result, id) in results"
 :key="id"
 class="summary-card"
 :class="`variant-${id} ${winner?.variant === id ? 'is-winner' : ''}`"
 >
 <div class="card-header">
 <div class="variant-name">{{ result.name }}</div>
 <el-icon v-if="winner?.variant === id" class="winner-icon"><Trophy /></el-icon>
 </div>
 <div class="variant-desc">{{ result.description }}</div>
 <div class="card-stats">
 <div class="stat">
 <div class="stat-value">{{ result.successRate }}%</div>
 <div class="stat-label">成功率</div>
 </div>
 <div class="stat">
 <div class="stat-value">{{ result.masteryRatio }}%</div>
 <div class="stat-label">掌握率</div>
 </div>
 <div class="stat">
 <div class="stat-value">{{ result.avgEF }}</div>
 <div class="stat-label">平均 EF</div>
 </div>
 </div>
 </div>
 </div>

 <!-- 对比表格 -->
 <div class="comparison-table">
 <div class="table-title">详细指标对比</div>
 <el-table :data="tableData" stripe size="small" :show-header="true">
 <el-table-column prop="metric" label="指标" width="120" fixed />
 <el-table-column
 v-for="(result, id) in results"
 :key="id"
 :label="result.name"
 align="center"
 >
 <template #default="{ row }">
 <span :class="{ 'best-value': row[id]?.isBest }">
 {{ formatValue(row[id]?.value) }}{{ row.unit }}
 </span>
 </template>
 </el-table-column>
 </el-table>
 </div>

 <!-- 图表 -->
 <div class="chart-section" v-if="chartData.series.length > 0">
 <div class="table-title">成功率 vs 掌握率</div>
 <div ref="chartRef" class="chart-container"></div>
 </div>

 <!-- 推荐结论 -->
 <el-alert
 v-if="winner"
 :type="winner.variant === 'standard' ? 'success' : 'warning'"
 :closable="false"
 show-icon
 class="recommendation-alert"
 >
 <template #title>推荐模式: {{ winner.name }}</template>
 <template #default>
 <div class="rec-content">
 <p>在您的生词本特征下，{{ winner.description }}表现最优。</p>
 <div class="rec-actions">
 <el-button size="small" @click="applyRecommendation">
 应用此模式
 </el-button>
 <el-button size="small" type="primary" plain @click="openSettings">
 打开设置
 </el-button>
 </div>
 </div>
 </template>
 </el-alert>
 </div>
 </div>
</template>

<script setup>
import { ref, computed, nextTick, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import {
 DataAnalysis, VideoPlay, Trophy
} from '@element-plus/icons-vue'
import { simulateVariants, buildComparisonTable, generateComparisonReport } from '@/utils/sm2-comparison'
import { getSettings, saveSettings } from '@/utils/sm2-settings'

const props = defineProps({
 words: { type: Array, default: () => [] }
})
const emit = defineEmits(['apply', 'open-settings'])

const loading = ref(false)
const results = ref(null)
const tableData = ref([])
const winner = computed(() => {
 if (!results.value) return null
 const items = Object.values(results.value)
 return items.reduce((best, curr) => {
 if (!best) return curr
 // 综合评分：成功率 + 掌握率，越高越好
 const bestScore = (best.successRate + best.masteryRatio) / 2
 const currScore = (curr.successRate + curr.masteryRatio) / 2
 return currScore > bestScore ? curr : best
 }, null)
})

const chartData = computed(() => {
 if (!results.value) return { series: [] }
 const items = Object.values(results.value)
 return {
 categories: items.map(r => r.name),
 series: [
 {
 name: '成功率',
 data: items.map(r => r.successRate),
 color: '#67c23a'
 },
 {
 name: '掌握率',
 data: items.map(r => r.masteryRatio),
 color: '#409eff'
 }
 ]
 }
})

const runSimulation = async () => {
 if (props.words.length === 0) {
 ElMessage.warning('暂无生词本数据，无法模拟')
 return
 }

 loading.value = true
 await new Promise(r => setTimeout(r, 300)) // 让 UI 有机会更新

 try {
 const inputWords = props.words.map(w => ({
 word: w.word,
 quality: w.masteryLevel === 'MASTERED' ? 5
        : w.masteryLevel === 'FAMILIAR' ? 4
        : w.masteryLevel === 'LEARNING' ? 3
        : 2,
 easinessFactor: w.easinessFactor || 2.5,
 repetition: w.reviewCount || w.repetition || 0,
 interval: w.intervalDays || w.interval || 0
 }))

 const sim = simulateVariants(inputWords, { iterations: 30 })
 results.value = sim
 tableData.value = buildComparisonTable(sim)

 await nextTick()
 renderChart()

 ElMessage.success('模拟完成')
 } catch (e) {
 console.error('模拟失败:', e)
 ElMessage.error('模拟失败: ' + e.message)
 } finally {
 loading.value = false
 }
}

const chartRef = ref(null)
let chartInstance = null

const renderChart = () => {
 if (!chartRef.value) return

 if (chartInstance) {
 chartInstance.dispose()
 chartInstance = null
 }

 // 动态加载 echarts
 import('echarts').then(echarts => {
 if (!chartRef.value) return
 chartInstance = echarts.init(chartRef.value)
 const option = {
 tooltip: { trigger: 'axis' },
 legend: { data: chartData.value.series.map(s => s.name) },
 grid: { left: 40, right: 20, top: 40, bottom: 40 },
 xAxis: {
 type: 'category',
 data: chartData.value.categories
 },
 yAxis: {
 type: 'value',
 max: 100,
 axisLabel: { formatter: '{value}%' }
 },
 series: chartData.value.series.map(s => ({
 name: s.name,
 type: 'bar',
 data: s.data,
 itemStyle: { color: s.color }
 }))
 }
 chartInstance.setOption(option)
 }).catch(() => {
 // echarts 不可用，静默失败
 })
}

const formatValue = (val) => {
 if (typeof val !== 'number') return val
 if (val % 1 === 0) return val.toString()
 return val.toFixed(1)
}

const applyRecommendation = () => {
 if (!winner.value) return
 const current = getSettings()
 let patch = {}
 if (winner.value.variant === 'hard') {
 patch = Object.assign({}, current, { hardWordMode: true })
 } else if (winner.value.variant === 'overload') {
 patch = Object.assign({}, current, { overloadProtection: true })
 } else if (winner.value.variant === 'hybrid') {
 patch = Object.assign({}, current, { hardWordMode: true, overloadProtection: true })
 }
 saveSettings(patch)
 ElMessage.success(`已应用 ${winner.value.name}`)
 emit('apply', patch)
}

const openSettings = () => {
 emit('open-settings')
}

onMounted(() => {
 // 不自动运行，等用户主动点击"运行模拟"
})
</script>

<style scoped lang="scss">
.ab-comparison {
 background: white;
 border-radius: 12px;
 padding: 16px;
 box-shadow: 0 1px 4px rgba(0, 0, 0, 0.06);
 border: 1px solid #ebeef5;
}

.ab-header {
 display: flex;
 justify-content: space-between;
 align-items: center;
 margin-bottom: 16px;
}

.ab-title {
 display: flex;
 align-items: center;
 gap: 8px;
 font-size: 15px;
 font-weight: 600;
 color: #303133;
}

.ab-intro {
 :deep(p) {
 font-size: 13px;
 color: #606266;
 margin: 8px 0;
 }

 :deep(ul) {
 margin: 8px 0;
 padding-left: 20px;
 font-size: 13px;
 color: #606266;
 }
}

.ab-note {
 font-size: 12px;
 color: #909399;
 font-style: italic;
 margin-top: 12px !important;
}

.summary-cards {
 display: grid;
 grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
 gap: 12px;
 margin-bottom: 20px;
}

.summary-card {
 padding: 14px;
 border-radius: 10px;
 border: 2px solid #ebeef5;
 background: #fafafa;
 transition: all 0.3s;
 position: relative;

 &.is-winner {
 border-color: #fbbf24;
 background: linear-gradient(135deg, #fffbeb 0%, #fef3c7 100%);
 box-shadow: 0 2px 8px rgba(251, 191, 36, 0.2);
 }
}

.card-header {
 display: flex;
 justify-content: space-between;
 align-items: center;
 margin-bottom: 6px;
}

.variant-name {
 font-size: 14px;
 font-weight: 600;
 color: #303133;
}

.winner-icon {
 color: #f59e0b;
 font-size: 20px;
}

.variant-desc {
 font-size: 11px;
 color: #909399;
 margin-bottom: 12px;
 line-height: 1.4;
}

.card-stats {
 display: grid;
 grid-template-columns: repeat(3, 1fr);
 gap: 4px;
}

.stat {
 text-align: center;
 padding: 6px 0;
 background: rgba(255, 255, 255, 0.6);
 border-radius: 6px;
}

.stat-value {
 font-size: 16px;
 font-weight: 700;
 color: #303133;
}

.stat-label {
 font-size: 10px;
 color: #909399;
 margin-top: 2px;
}

.comparison-table {
 margin-bottom: 20px;
}

.table-title {
 font-size: 14px;
 font-weight: 600;
 color: #303133;
 margin-bottom: 8px;
}

.best-value {
 color: #f59e0b;
 font-weight: 700;
 background: #fffbeb;
 padding: 2px 6px;
 border-radius: 4px;
}

.chart-section {
 margin-bottom: 16px;
}

.chart-container {
 width: 100%;
 height: 280px;
}

.recommendation-alert {
 margin-top: 12px;
}

.rec-content {
 font-size: 13px;
 line-height: 1.6;
}

.rec-actions {
 margin-top: 8px;
 display: flex;
 gap: 8px;
}
</style>
