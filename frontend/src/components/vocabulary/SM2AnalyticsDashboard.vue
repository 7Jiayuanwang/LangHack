<template>
 <div class="analytics-dashboard" v-if="report">
 <!-- 顶部概览 -->
 <div class="overview-cards">
 <div class="overview-card">
 <div class="card-icon total"><el-icon><Collection /></el-icon></div>
 <div class="card-content">
 <div class="card-value">{{ report.totalWords }}</div>
 <div class="card-label">总词汇</div>
 </div>
 </div>
 <div class="overview-card">
 <div class="card-icon retention"><el-icon><Aim /></el-icon></div>
 <div class="card-content">
 <div class="card-value">{{ report.retentionStats.averageRetention }}%</div>
 <div class="card-label">平均保持率</div>
 </div>
 </div>
 <div class="overview-card">
 <div class="card-icon ef"><el-icon><TrendCharts /></el-icon></div>
 <div class="card-content">
 <div class="card-value">{{ report.efDistribution.average }}</div>
 <div class="card-label">平均 EF</div>
 </div>
 </div>
 <div class="overview-card">
 <div class="card-icon due"><el-icon><AlarmClock /></el-icon></div>
 <div class="card-content">
 <div class="card-value">{{ report.dueStats.overdue }}</div>
 <div class="card-label">逾期单词</div>
 </div>
 </div>
 </div>

 <!-- 掌握进度（按 masteryLevel，与筛选 chip 一致） -->
 <div class="section">
 <div class="section-title">掌握进度</div>
 <div class="progress-bars">
 <div class="progress-item">
 <div class="progress-label">
 <span>新词</span>
 <span class="progress-value">{{ report.repetitionStats.new }}</span>
 </div>
 <el-progress
 :percentage="getPercent(report.repetitionStats.new, report.totalWords)"
 :stroke-width="8"
 color="#909399"
 :show-text="false"
 />
 </div>
 <div class="progress-item">
 <div class="progress-label">
 <span>学习中</span>
 <span class="progress-value">{{ report.repetitionStats.learning }}</span>
 </div>
 <el-progress
 :percentage="getPercent(report.repetitionStats.learning, report.totalWords)"
 :stroke-width="8"
 color="#e6a23c"
 :show-text="false"
 />
 </div>
 <div class="progress-item">
 <div class="progress-label">
 <span>已掌握</span>
 <span class="progress-value">{{ report.repetitionStats.mature }}</span>
 </div>
 <el-progress
 :percentage="getPercent(report.repetitionStats.mature, report.totalWords)"
 :stroke-width="8"
 color="#67c23a"
 :show-text="false"
 />
 </div>
 </div>
 </div>

 <!-- EF 分布 -->
 <div class="section" v-if="report.totalWords > 0">
 <div class="section-title">EF 难度分布</div>
 <div class="ef-distribution">
 <div class="ef-bar">
 <template v-for="(count, range) in report.efDistribution.ranges" :key="range">
 <div
 v-if="count > 0"
 class="ef-segment"
 :style="{ width: getPercent(count, report.totalWords) + '%' }"
 :class="getEFClass(range)"
 :title="`EF ${range}: ${count} 个`"
 ></div>
 </template>
 </div>
 <div class="ef-legend">
 <div class="legend-item">
 <span class="legend-dot hard"></span>
 困难 (EF &lt; 2.0): {{ report.efDistribution.hardWords }}
 </div>
 <div class="legend-item">
 <span class="legend-dot normal"></span>
 普通 (2.0–3.0): {{ report.efDistribution.normalWords }}
 </div>
 <div class="legend-item">
 <span class="legend-dot easy"></span>
 简单 (EF ≥ 3.0): {{ report.efDistribution.easyWords }}
 </div>
 </div>
 </div>
 </div>

 <!-- 间隔分布 -->
 <div class="section" v-if="report.totalWords > 0">
 <div class="section-title">复习间隔分布</div>
 <div class="interval-summary">
 <span>平均 {{ report.intervalDistribution.average }} 天</span>
 <span>·</span>
 <span>最长 {{ report.intervalDistribution.maxInterval }} 天</span>
 </div>
 <template v-if="hasAnyInterval">
 <div class="interval-bar">
 <template v-for="(count, range) in report.intervalDistribution.ranges" :key="range">
 <div
 v-if="count > 0"
 class="interval-segment"
 :style="{ width: getPercent(count, report.intervalDistribution.total) + '%' }"
 :class="getIntervalClass(range)"
 :title="`${range}天: ${count} 个`"
 ></div>
 </template>
 </div>
 <div class="interval-legend">
 <template v-for="(count, range) in report.intervalDistribution.ranges" :key="range">
 <div v-if="count > 0" class="legend-item">
 <span class="legend-dot" :class="getIntervalClass(range)"></span>
 {{ range }}天: {{ count }}
 </div>
 </template>
 </div>
 </template>
 <div v-else class="interval-empty">所有单词均为新词，尚未生成复习间隔</div>
 </div>

 <!-- 智能建议 -->
 <div class="section recommendations" v-if="report.recommendations.length > 0">
 <div class="section-title">智能建议</div>
 <div class="recommendation-list">
 <div
 v-for="(rec, idx) in report.recommendations"
 :key="idx"
 class="recommendation-item"
 :class="`rec-${rec.type}`"
 >
 <el-icon class="rec-icon">
 <component :is="getRecIcon(rec.type)" />
 </el-icon>
 <div class="rec-content">
 <div class="rec-title">{{ rec.title }}</div>
 <div class="rec-message">{{ rec.message }}</div>
 </div>
 </div>
 </div>
 </div>

 <!-- 操作按钮 -->
 <div class="action-row" v-if="showActions">
 <el-button @click="exportData" :icon="Download">导出 CSV</el-button>
 <el-button @click="refresh" :icon="Refresh">刷新数据</el-button>
 </div>
</div>
<div v-else class="loading-state">
 <el-icon class="is-loading"><Loading /></el-icon>
 <span>正在分析学习数据..</span>
</div>
</template>

<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import {
 Collection, Aim, TrendCharts, AlarmClock,
 WarningFilled, CircleClose, SuccessFilled, InfoFilled,
 Download, Refresh, Loading
} from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import {
 calculateEFDistribution, calculateIntervalDistribution,
 calculateRetentionStats, generateLearningReport, exportToCSV
} from '@/utils/sm2-analytics'

const props = defineProps({
 words: { type: Array, required: true },
 showActions: { type: Boolean, default: true }
})
const emit = defineEmits(['export', 'refresh'])

const report = ref(null)

const computeReport = () => {
 report.value = generateLearningReport(props.words)
}

watch(() => props.words, () => computeReport(), { deep: true })
onMounted(() => computeReport())

const getPercent = (part, total) => {
 if (!total) return 0
 return Math.round((part / total) * 100)
}

const hasAnyInterval = computed(() => {
 if (!report.value) return false
 return Object.values(report.value.intervalDistribution.ranges).some(c => c > 0 && c !== report.value.intervalDistribution.ranges['0-1'])
 || (report.value.intervalDistribution.ranges['0-1'] < report.value.totalWords)
})

const getIntervalClass = (range) => {
 if (range === '0-1') return 'interval-new'
 if (range === '2-7') return 'interval-short'
 if (range === '8-30') return 'interval-medium'
 return 'interval-long'
}

const getEFClass = (range) => {
 // 困难: EF < 2.0 (ranges 1.0-1.5, 1.5-2.0)
 if (range === '1.0-1.5' || range === '1.5-2.0') return 'ef-hard'
 // 简单: EF >= 3.0 (ranges 3.0-4.0, 4.0-5.0)
 if (range === '3.0-4.0' || range === '4.0-5.0') return 'ef-easy'
 // 普通: 2.0-2.5, 2.5-3.0
 return 'ef-normal'
}

const getRecIcon = (type) => {
 const map = {
 error: WarningFilled,
 warning: WarningFilled,
 success: SuccessFilled,
 info: InfoFilled
 }
 return map[type] || InfoFilled
}

const exportData = () => {
 const csv = exportToCSV(props.words)
 const blob = new Blob([csv], { type: 'text/csv;charset=utf-8;' })
 const url = URL.createObjectURL(blob)
 const link = document.createElement('a')
 link.href = url
 link.download = `vocabulary_report_${new Date().toISOString().slice(0, 10)}.csv`
 document.body.appendChild(link)
 link.click()
 document.body.removeChild(link)
 URL.revokeObjectURL(url)
 ElMessage.success('CSV 已导出')
 emit('export', csv)
}

const refresh = () => {
 computeReport()
 ElMessage.success('数据已刷新')
 emit('refresh')
}
</script>

<style scoped lang="scss">
.analytics-dashboard {
 padding: 4px;
}

.loading-state {
 display: flex;
 align-items: center;
 justify-content: center;
 gap: 8px;
 padding: 40px;
 color: #909399;
 font-size: 14px;
}

.overview-cards {
 display: grid;
 grid-template-columns: repeat(4, 1fr);
 gap: 12px;
 margin-bottom: 20px;

 @media (max-width: 600px) {
 grid-template-columns: repeat(2, 1fr);
 }
}

.overview-card {
 background: white;
 border-radius: 12px;
 padding: 14px;
 display: flex;
 align-items: center;
 gap: 12px;
 box-shadow: 0 1px 4px rgba(0, 0, 0, 0.06);
 border: 1px solid #ebeef5;
}

.card-icon {
 width: 40px;
 height: 40px;
 border-radius: 10px;
 display: flex;
 align-items: center;
 justify-content: center;
 font-size: 20px;
 flex-shrink: 0;

 &.total { background: var(--xy-brand-50); color: #3b82f6; }
 &.retention { background: #ecfdf5; color: #10b981; }
 &.ef { background: #fef3c7; color: #f59e0b; }
 &.due { background: #fee2e2; color: #ef4444; }
}

.card-content {
 flex: 1;
 min-width: 0;
}

.card-value {
 font-size: 20px;
 font-weight: 700;
 color: #303133;
 line-height: 1.2;
}

.card-label {
 font-size: 11px;
 color: #909399;
 margin-top: 2px;
}

.section {
 background: white;
 border-radius: 12px;
 padding: 16px;
 margin-bottom: 12px;
 box-shadow: 0 1px 4px rgba(0, 0, 0, 0.06);
 border: 1px solid #ebeef5;
}

.section-title {
 font-size: 14px;
 font-weight: 600;
 color: #303133;
 margin-bottom: 12px;
}

.progress-bars {
 display: flex;
 flex-direction: column;
 gap: 12px;
}

.progress-label {
 display: flex;
 justify-content: space-between;
 font-size: 13px;
 color: #606266;
 margin-bottom: 4px;
}

.progress-value {
 font-weight: 600;
 color: #303133;
}

.ef-distribution {
}

.ef-bar {
 display: flex;
 height: 24px;
 border-radius: 6px;
 overflow: hidden;
 background: #f5f7fa;
}

.ef-segment {
 height: 100%;
 transition: all 0.3s;

 &.ef-hard { background: linear-gradient(90deg, #fca5a5 0%, #f87171 100%); }
 &.ef-normal { background: linear-gradient(90deg, #fde68a 0%, #fbbf24 100%); }
 &.ef-easy { background: linear-gradient(90deg, #86efac 0%, #4ade80 100%); }
}

.ef-legend {
 display: flex;
 flex-wrap: wrap;
 gap: 12px;
 margin-top: 8px;
 font-size: 12px;
 color: #606266;
}

.legend-item {
 display: flex;
 align-items: center;
 gap: 4px;
}

.legend-dot {
 width: 8px;
 height: 8px;
 border-radius: 50%;

 &.hard { background: #f87171; }
 &.normal { background: #fbbf24; }
 &.easy { background: #4ade80; }
 &.interval-new { background: #c0ccda; }
 &.interval-short { background: #79bbff; }
 &.interval-medium { background: var(--xy-brand-600); }
 &.interval-long { background: #067dff; }
}

.interval-summary {
 display: flex;
 gap: 8px;
 font-size: 12px;
 color: #909399;
 margin-bottom: 10px;
}

.interval-bar {
 display: flex;
 height: 20px;
 border-radius: 6px;
 overflow: hidden;
 background: #f5f7fa;
 margin-bottom: 8px;
}

.interval-segment {
 height: 100%;
 transition: all 0.3s;

 &.interval-new { background: #c0ccda; }
 &.interval-short { background: #79bbff; }
 &.interval-medium { background: var(--xy-brand-600); }
 &.interval-long { background: #067dff; }
}

.interval-legend {
 display: flex;
 flex-wrap: wrap;
 gap: 10px;
 font-size: 12px;
 color: #606266;
}

.interval-empty {
 font-size: 13px;
 color: #909399;
 text-align: center;
 padding: 12px 0;
}

.recommendation-list {
 display: flex;
 flex-direction: column;
 gap: 8px;
}

.recommendation-item {
 display: flex;
 align-items: flex-start;
 gap: 10px;
 padding: 10px 12px;
 border-radius: 8px;
 font-size: 13px;
 line-height: 1.5;

 &.rec-error {
 background: #fef2f2;
 border: 1px solid #fecaca;
 color: #991b1b;
 }

 &.rec-warning {
 background: #fffbeb;
 border: 1px solid #fed7aa;
 color: #9a3412;
 }

 &.rec-success {
 background: #f0fdf4;
 border: 1px solid #bbf7d0;
 color: #166534;
 }

 &.rec-info {
 background: var(--xy-brand-50);
 border: 1px solid #bfdbfe;
 color: #1e40af;
 }
}

.rec-icon {
 font-size: 18px;
 flex-shrink: 0;
 margin-top: 1px;
}

.rec-content {
 flex: 1;
 min-width: 0;
}

.rec-title {
 font-weight: 600;
 margin-bottom: 2px;
}

.rec-message {
 font-size: 12px;
 opacity: 0.9;
}

.action-row {
 display: flex;
 gap: 8px;
 justify-content: flex-end;
 margin-top: 8px;
}
</style>
