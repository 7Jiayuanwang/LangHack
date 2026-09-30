<template>
  <main class="class-learning-overview">
    <XyPageHeader
      class="overview-header"
      :title="overview?.className || '班级学情'"
      eyebrow="CLASS LEARNING"
      description="查看本班学生的站内阅读与答题情况"
      variant="compact"
    >
      <template #actions>
        <el-button @click="goBack">返回班级</el-button>
        <el-button type="primary" plain :loading="loading" @click="loadOverview">刷新数据</el-button>
      </template>
    </XyPageHeader>

    <div v-if="loading && !overview" class="loading-panel" v-loading="loading" aria-label="正在加载班级学情" />
    <el-result v-else-if="error" icon="error" title="无法加载班级学情" :sub-title="error">
      <template #extra><el-button type="primary" @click="loadOverview">重试</el-button></template>
    </el-result>
    <template v-else-if="overview">
      <p class="data-scope">
        统计范围：{{ overview.windowStart }} 至 {{ overview.windowEnd }}，当前班级成员的全平台学习记录。
        不包含站外英文收获自述或线下学习，也不读取学生与小芽的对话或小芽成长状态。
      </p>

      <el-empty v-if="overview.totalStudents === 0" description="这个班级还没有在册学生，暂无可展示的学情数据" />
      <template v-else>
        <section class="metric-grid" aria-label="班级学习概况">
          <div class="metric-card">
            <span class="metric-label">在册学生</span>
            <strong>{{ overview.totalStudents }}<small>人</small></strong>
            <span class="metric-detail">当前班级成员</span>
          </div>
          <div class="metric-card">
            <span class="metric-label">有站内学习记录</span>
            <strong>{{ overview.activeStudents }}<small>人</small></strong>
            <span class="metric-detail">占全班 {{ activePercent }}%</span>
          </div>
          <div class="metric-card">
            <span class="metric-label">参与文章阅读</span>
            <strong>{{ overview.readingStudents }}<small>人</small></strong>
            <span class="metric-detail">共 {{ overview.readingSessions }} 次阅读会话</span>
          </div>
          <div class="metric-card">
            <span class="metric-label">参与阅读题</span>
            <strong>{{ overview.quizStudents }}<small>人</small></strong>
            <span class="metric-detail">共 {{ overview.practiceAttempts }} 次提交</span>
          </div>
          <div class="metric-card metric-card--accent">
            <span class="metric-label">阅读题正确率</span>
            <strong>{{ overview.correctRate === null ? '—' : `${overview.correctRate}%` }}</strong>
            <span class="metric-detail">按已答 {{ overview.answeredQuestions }} 题计算</span>
          </div>
        </section>

        <div v-if="overview.noRecordStudents > 0" class="attention-note">
          {{ overview.noRecordStudents }} 名学生在此时间段没有站内阅读或阅读题记录；可结合课堂情况了解原因，不能据此判断其没有学习。
        </div>

        <el-empty v-if="overview.activeStudents === 0" description="近四周暂无可用的站内阅读或答题记录" />
        <section v-else class="chart-grid" aria-label="班级学习图表">
          <article class="chart-card chart-card--wide">
            <div class="section-heading">
              <div>
                <h2>每周参与人数</h2>
                <p>阅读与答题分别按周去重，同一学生可能同时计入两组。</p>
              </div>
            </div>
            <VChart class="chart" :option="participationOption" autoresize />
          </article>

          <article class="chart-card">
            <div class="section-heading">
              <div>
                <h2>学生答题正确率分布</h2>
                <p>每位学生只计一次；未答题的学生单独列出。</p>
              </div>
            </div>
            <VChart class="chart" :option="distributionOption" autoresize />
          </article>

          <article class="chart-card chart-card--wide">
            <div class="section-heading">
              <div>
                <h2>每周阅读题正确率</h2>
                <p>按每周答对题数 ÷ 已答题数计算；没有答题的周不显示百分比。</p>
              </div>
            </div>
            <VChart v-if="overview.quizStudents > 0" class="chart chart--short" :option="accuracyTrendOption" autoresize />
            <el-empty v-else description="暂无阅读题记录" />
          </article>
        </section>

        <section class="student-section">
          <div class="section-heading">
            <div>
              <h2>学生学习明细</h2>
              <p>正确率为该学生在统计时间段内答对题数 ÷ 已答题数；“—”表示没有有效答题。</p>
            </div>
          </div>
          <div class="table-wrapper">
            <el-table :data="pagedStudents" stripe>
              <el-table-column prop="displayName" label="姓名" min-width="120" />
              <el-table-column prop="username" label="学号 / 账号" min-width="130" />
              <el-table-column label="阅读" min-width="140">
                <template #default="{ row }">{{ row.textsRead }} 篇 / {{ row.readingSessions }} 次</template>
              </el-table-column>
              <el-table-column label="阅读题" min-width="120">
                <template #default="{ row }">{{ row.practiceAttempts }} 次</template>
              </el-table-column>
              <el-table-column label="正确率" min-width="110">
                <template #default="{ row }">{{ row.correctRate === null ? '—' : `${row.correctRate}%` }}</template>
              </el-table-column>
              <el-table-column label="最近站内记录" min-width="160">
                <template #default="{ row }">{{ formatTime(row.lastActivityAt) }}</template>
              </el-table-column>
            </el-table>
          </div>
          <el-pagination
            v-if="overview.studentProgress.length > pageSize"
            v-model:current-page="page"
            class="student-pagination"
            background
            layout="prev, pager, next"
            :page-size="pageSize"
            :total="overview.studentProgress.length"
          />
        </section>

        <p class="method-note">数据口径：阅读取文章阅读会话，练习取已提交的 AI 阅读题；展示的是本班当前学生在平台上的行为，不等同于本课程作业完成率或英语综合能力。</p>
      </template>
    </template>
  </main>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import VChart from 'vue-echarts'
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { BarChart, LineChart, PieChart } from 'echarts/charts'
import { AriaComponent, GridComponent, LegendComponent, TooltipComponent } from 'echarts/components'
import { getClassLearningOverview } from '@/api/teacher'
import XyPageHeader from '@/components/ui/XyPageHeader.vue'

use([CanvasRenderer, BarChart, LineChart, PieChart, AriaComponent, GridComponent, LegendComponent, TooltipComponent])

const route = useRoute()
const router = useRouter()
const overview = ref(null)
const loading = ref(false)
const error = ref('')
const page = ref(1)
const pageSize = 10
let latestRequest = 0

const activePercent = computed(() => overview.value?.totalStudents
  ? Math.round(overview.value.activeStudents * 100 / overview.value.totalStudents)
  : 0)
const pagedStudents = computed(() => (overview.value?.studentProgress || [])
  .slice((page.value - 1) * pageSize, page.value * pageSize))
const weekLabels = computed(() => (overview.value?.weeklyActivity || [])
  .map(item => String(item.weekStart || '').slice(5)))

const participationOption = computed(() => ({
  aria: { enabled: true },
  color: ['#249e73', '#408bd2'],
  tooltip: { trigger: 'axis' },
  legend: { bottom: 0, data: ['阅读人数', '答题人数'] },
  grid: { left: 12, right: 16, top: 28, bottom: 52, containLabel: true },
  xAxis: { type: 'category', data: weekLabels.value, axisTick: { alignWithLabel: true } },
  yAxis: { type: 'value', minInterval: 1, name: '人' },
  series: [
    { name: '阅读人数', type: 'bar', barMaxWidth: 34, data: (overview.value?.weeklyActivity || []).map(item => item.readingStudents) },
    { name: '答题人数', type: 'bar', barMaxWidth: 34, data: (overview.value?.weeklyActivity || []).map(item => item.quizStudents) }
  ]
}))

const distributionOption = computed(() => ({
  aria: { enabled: true },
  color: ['#b8c8d0', '#e3a15b', '#408bd2', '#249e73'],
  tooltip: { trigger: 'item', formatter: '{b}：{c} 人' },
  legend: { bottom: 0, type: 'scroll' },
  series: [{
    type: 'pie',
    radius: ['43%', '67%'],
    center: ['50%', '45%'],
    label: { show: false },
    data: (overview.value?.accuracyDistribution || []).map(item => ({ name: item.label, value: item.studentCount }))
  }]
}))

const accuracyTrendOption = computed(() => ({
  aria: { enabled: true },
  color: ['#249e73'],
  tooltip: { trigger: 'axis', valueFormatter: value => value === null || value === undefined ? '无答题' : `${value}%` },
  grid: { left: 12, right: 18, top: 28, bottom: 22, containLabel: true },
  xAxis: { type: 'category', data: weekLabels.value },
  yAxis: { type: 'value', min: 0, max: 100, axisLabel: { formatter: '{value}%' } },
  series: [{ type: 'line', smooth: true, connectNulls: false, symbolSize: 9,
    data: (overview.value?.weeklyActivity || []).map(item => item.correctRate) }]
}))

const formatTime = (value) => value ? String(value).replace('T', ' ').slice(0, 16) : '—'
const goBack = () => router.push('/teacher/classes')

async function loadOverview() {
  const requestId = ++latestRequest
  overview.value = null
  loading.value = false
  const classId = Number(route.params.classId)
  if (!Number.isSafeInteger(classId) || classId <= 0) {
    error.value = '班级编号无效'
    return
  }
  loading.value = true
  error.value = ''
  try {
    const result = await getClassLearningOverview(classId)
    if (requestId !== latestRequest) return
    overview.value = result?.data || null
    page.value = 1
    if (!overview.value) error.value = '暂时没有可展示的数据'
  } catch (cause) {
    if (requestId !== latestRequest) return
    overview.value = null
    error.value = cause?.code === 403 ? '只能查看自己负责的教学班' : '请检查网络后重试'
  } finally {
    if (requestId === latestRequest) loading.value = false
  }
}

watch(() => route.params.classId, loadOverview, { immediate: true })
</script>

<style scoped>
.class-learning-overview { max-width: 1440px; margin: 0 auto; padding: 20px; color: var(--xy-ink); }
.overview-header { margin-bottom: 18px; }
.loading-panel { min-height: 240px; }
.data-scope, .method-note { margin: 0 0 18px; color: var(--xy-ink-muted); font-size: 14px; line-height: 1.65; }
.metric-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(170px, 1fr)); gap: 14px; margin-bottom: 16px; }
.metric-card { display: flex; flex-direction: column; gap: 9px; min-height: 130px; padding: 18px; background: var(--xy-surface); border: 1px solid var(--xy-border); border-radius: var(--xy-radius-md); box-shadow: var(--xy-shadow-card); }
.metric-card--accent { background: linear-gradient(140deg, #eefaf4, #fff); border-color: rgba(36, 158, 115, .25); }
.metric-label { color: var(--xy-ink-muted); font-size: 14px; }
.metric-card strong { color: var(--xy-ink); font-size: clamp(28px, 3vw, 36px); line-height: 1; }
.metric-card small { margin-left: 4px; font-size: 14px; font-weight: 500; }
.metric-detail { color: var(--xy-ink-muted); font-size: 13px; }
.attention-note { margin-bottom: 16px; padding: 12px 16px; border-left: 3px solid #d99a47; border-radius: var(--xy-radius-sm); background: #fff9ec; color: #68522f; font-size: 14px; line-height: 1.6; }
.chart-grid { display: grid; grid-template-columns: minmax(0, 1.6fr) minmax(290px, 1fr); gap: 16px; margin-bottom: 18px; }
.chart-card, .student-section { min-width: 0; padding: 20px; background: var(--xy-surface); border: 1px solid var(--xy-border); border-radius: var(--xy-radius-md); box-shadow: var(--xy-shadow-card); }
.chart-card--wide { grid-column: span 1; }
.chart-card:last-child { grid-column: 1 / -1; }
.section-heading { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; margin-bottom: 10px; }
.section-heading h2 { margin: 0; font-size: 18px; line-height: 1.4; }
.section-heading p { margin: 6px 0 0; color: var(--xy-ink-muted); font-size: 14px; line-height: 1.5; }
.chart { width: 100%; height: 280px; }
.chart--short { height: 220px; }
.table-wrapper { overflow-x: auto; -webkit-overflow-scrolling: touch; }
.student-section :deep(.el-table) { min-width: 740px; }
.student-pagination { display: flex; justify-content: flex-end; margin-top: 14px; }
.method-note { margin: 14px 0 0; }
@media (max-width: 800px) {
  .class-learning-overview { padding: 12px; }
  .chart-grid { grid-template-columns: 1fr; }
  .chart-card:last-child { grid-column: auto; }
}
</style>
