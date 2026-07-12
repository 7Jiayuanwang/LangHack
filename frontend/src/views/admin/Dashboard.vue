<template>
  <div class="dashboard-container">
    <div class="stats-grid">
      <div class="modern-stat-card users">
        <div class="card-bg-icon"><el-icon><User /></el-icon></div>
        <div class="card-body">
          <div class="label">总用户数</div>
          <div class="value">{{ stats.totalUsers }}</div>
          <div class="card-footer">
            <span class="trend up">活跃状态</span>
          </div>
        </div>
      </div>

      <div class="modern-stat-card courses">
        <div class="card-bg-icon"><el-icon><Reading /></el-icon></div>
        <div class="card-body">
          <div class="label">课程数量</div>
          <div class="value">{{ stats.totalCourses }}</div>
          <div class="card-footer">
            <span class="trend">精品内容</span>
          </div>
        </div>
      </div>

      <div class="modern-stat-card classes">
        <div class="card-bg-icon"><el-icon><School /></el-icon></div>
        <div class="card-body">
          <div class="label">班级数量</div>
          <div class="value">{{ stats.totalClasses }}</div>
          <div class="card-footer">
            <span class="trend">教学单元</span>
          </div>
        </div>
      </div>

      <div class="modern-stat-card words">
        <div class="card-bg-icon"><el-icon><Document /></el-icon></div>
        <div class="card-body">
          <div class="label">单词数量</div>
          <div class="value">{{ stats.totalWords }}</div>
          <div class="card-footer">
            <span class="trend">核心词库</span>
          </div>
        </div>
      </div>
    </div>

    <el-row :gutter="24" class="mt-32">
      <el-col :xs="24" :lg="10">
        <el-card class="glass-card shadow-sm">
          <template #header>
            <div class="card-header">
              <span class="title-with-dot">用户分布</span>
            </div>
          </template>
          <div class="dist-container">
            <div class="dist-chart">
              <div class="dist-item">
                <div class="dist-info">
                  <span class="name">管理员</span>
                  <span class="val">{{ userStats.admin }}</span>
                </div>
                <el-progress :percentage="calcPercent(userStats.admin)" status="exception" :show-text="false" :stroke-width="8" />
              </div>
              <div class="dist-item">
                <div class="dist-info">
                  <span class="name">教师</span>
                  <span class="val">{{ userStats.teacher }}</span>
                </div>
                <el-progress :percentage="calcPercent(userStats.teacher)" status="warning" :show-text="false" :stroke-width="8" />
              </div>
              <div class="dist-item">
                <div class="dist-info">
                  <span class="name">学生</span>
                  <span class="val">{{ userStats.student }}</span>
                </div>
                <el-progress :percentage="calcPercent(userStats.student)" status="success" :show-text="false" :stroke-width="8" />
              </div>
            </div>
          </div>
        </el-card>
      </el-col>

      <el-col :xs="24" :lg="14">
        <el-card class="glass-card shadow-sm">
          <template #header>
            <div class="card-header">
              <span class="title-with-dot">最新活动</span>
            </div>
          </template>
          <div class="timeline-container">
            <template v-if="recentActivities.length">
              <el-timeline>
                <el-timeline-item
                  v-for="activity in recentActivities"
                  :key="activity.id"
                  :timestamp="activity.time"
                  placement="top"
                >
                  <div class="activity-box">
                    <p>{{ activity.description }}</p>
                  </div>
                </el-timeline-item>
              </el-timeline>
            </template>
            <el-empty v-else description="暂无活动数据" :image-size="100" />
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 四六级成绩对比卡片 -->
    <el-row :gutter="24" class="mt-32">
      <el-col :span="24">
        <el-card class="glass-card shadow-sm">
          <template #header>
            <div class="card-header" style="display:flex;align-items:center;justify-content:space-between">
              <span class="title-with-dot">四六级成绩对比分析</span>
              <el-button size="small" text @click="$router.push('/admin/cet-analysis')">
                详细分析 →
              </el-button>
            </div>
          </template>
          <div v-if="cetLoading" style="text-align:center;padding:40px">
            <el-icon class="is-loading"><Loading /></el-icon>
          </div>
          <div v-else-if="cetData" class="cet-comparison">
            <div class="cet-summary">
              <div class="cet-stat-item highlight">
                <div class="cet-stat-value">+{{ cetData.improvement }}%</div>
                <div class="cet-stat-label">通过率提升</div>
              </div>
              <div class="cet-stat-item">
                <div class="cet-stat-value">{{ cetData.totalScoreRecords }}</div>
                <div class="cet-stat-label">成绩记录总数</div>
              </div>
              <div class="cet-stat-item">
                <div class="cet-stat-value">{{ cetData.used?.totalStudents || 0 }}</div>
                <div class="cet-stat-label">使用平台学生</div>
              </div>
              <div class="cet-stat-item">
                <div class="cet-stat-value">{{ cetData.unused?.totalStudents || 0 }}</div>
                <div class="cet-stat-label">未使用平台学生</div>
              </div>
            </div>
            <div class="cet-bars">
              <div class="cet-bar-row">
                <span class="cet-bar-label">使用平台</span>
                <el-progress
                  :percentage="cetData.used?.passRate || 0"
                  :format="(p) => p + '%'"
                  status="success"
                  :stroke-width="18"
                  style="flex:1"
                />
              </div>
              <div class="cet-bar-row">
                <span class="cet-bar-label">未使用平台</span>
                <el-progress
                  :percentage="cetData.unused?.passRate || 0"
                  :format="(p) => p + '%'"
                  :stroke-width="18"
                  style="flex:1"
                />
              </div>
            </div>
            <div style="height:260px;margin-top:16px">
              <v-chart :option="cetChartOption" autoresize />
            </div>
          </div>
          <el-empty v-else description="暂无四六级成绩数据，请先同步学校数据" :image-size="80" />
        </el-card>
      </el-col>
    </el-row>

    <!-- 日期筛选工具栏 -->
    <div class="date-filter-bar mt-32">
      <div class="quick-btns">
        <button
          v-for="preset in datePresets"
          :key="preset.label"
          class="preset-btn"
          :class="{ active: activePreset === preset.label }"
          @click="applyPreset(preset)"
        >{{ preset.label }}</button>
      </div>
      <el-date-picker
        v-model="customRange"
        type="daterange"
        range-separator="至"
        start-placeholder="开始日期"
        end-placeholder="结束日期"
        value-format="YYYY-MM-DD"
        :clearable="false"
        size="small"
        @change="onCustomRangeChange"
      />
    </div>

    <!-- 用户增长趋势 -->
    <el-row :gutter="24" class="mt-32">
      <el-col :span="24">
        <el-card class="glass-card shadow-sm">
          <template #header>
            <div class="card-header">
              <span class="title-with-dot">用户增长趋势</span>
              <span class="metric-badge">当前总数 <b>{{ growthMetrics.currentTotal }}</b></span>
            </div>
          </template>
          <v-chart v-if="growthChartOption" :option="growthChartOption" style="height:240px" autoresize />
          <div v-else class="chart-loading"><el-icon class="is-loading"><Loading /></el-icon> 加载中...</div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 用户活跃度 -->
    <el-row :gutter="24" class="mt-32">
      <el-col :span="24">
        <el-card class="glass-card shadow-sm">
          <template #header>
            <div class="card-header">
              <span class="title-with-dot">用户活跃度</span>
              <div class="metric-summary">
                <span class="metric-badge">今日活跃 <b>{{ activityMetrics.dau }}</b></span>
                <span class="metric-badge">月活跃 <b>{{ activityMetrics.mau }}</b></span>
              </div>
            </div>
          </template>
          <v-chart v-if="dauChartOption" :option="dauChartOption" style="height:220px" autoresize />
          <div v-else class="chart-loading"><el-icon class="is-loading"><Loading /></el-icon> 加载中...</div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 学习行为 -->
    <el-row :gutter="24" class="mt-32">
      <el-col :xs="24" :lg="12">
        <el-card class="glass-card shadow-sm">
          <template #header>
            <div class="card-header">
              <span class="title-with-dot">学习记录类型分布</span>
              <span class="metric-badge">总记录 <b>{{ learningMetrics.totalRecords }}</b></span>
            </div>
          </template>
          <v-chart v-if="learningPieOption" :option="learningPieOption" style="height:240px" autoresize />
          <div v-else class="chart-loading"><el-icon class="is-loading"><Loading /></el-icon> 加载中...</div>
        </el-card>
      </el-col>
      <el-col :xs="24" :lg="12">
        <el-card class="glass-card shadow-sm">
          <template #header>
            <div class="card-header">
              <span class="title-with-dot">每日学习趋势</span>
              <span class="metric-badge">总阅读 <b>{{ learningMetrics.totalReads }}</b></span>
            </div>
          </template>
          <v-chart v-if="learningBarOption" :option="learningBarOption" style="height:240px" autoresize />
          <div v-else class="chart-loading"><el-icon class="is-loading"><Loading /></el-icon> 加载中...</div>
        </el-card>
      </el-col>
    </el-row>

    <!-- AI 调用统计 -->
    <el-row :gutter="24" class="mt-32">
      <el-col :xs="24" :lg="14">
        <el-card class="glass-card shadow-sm">
          <template #header>
            <div class="card-header">
              <span class="title-with-dot">AI 调用趋势（近30天）</span>
              <div class="metric-summary">
                <span class="metric-badge">总调用 <b>{{ aiMetrics.totalCalls }}</b></span>
                <span class="metric-badge">成功率 <b>{{ (aiMetrics.successRate * 100).toFixed(1) }}%</b></span>
                <span class="metric-badge">均响应 <b>{{ aiMetrics.avgResponseMs }}ms</b></span>
              </div>
            </div>
          </template>
          <v-chart v-if="aiLineOption" :option="aiLineOption" style="height:220px" autoresize />
          <div v-else class="chart-loading"><el-icon class="is-loading"><Loading /></el-icon> 加载中...</div>
        </el-card>
      </el-col>
      <el-col :xs="24" :lg="10">
        <el-card class="glass-card shadow-sm">
          <template #header>
            <div class="card-header">
              <span class="title-with-dot">AI 服务分布</span>
              <span class="metric-badge">今日 <b>{{ aiMetrics.todayCalls }}</b></span>
            </div>
          </template>
          <v-chart v-if="aiPieOption" :option="aiPieOption" style="height:220px" autoresize />
          <div v-else class="chart-loading"><el-icon class="is-loading"><Loading /></el-icon> 加载中...</div>
        </el-card>
      </el-col>
    </el-row>

    <!-- AI 问答使用次数（按用户） -->
    <el-row :gutter="24" class="mt-32">
      <el-col :span="24">
        <el-card class="glass-card shadow-sm">
          <template #header>
            <div class="card-header">
              <span class="title-with-dot">AI 问答使用次数</span>
              <div class="qa-usage-controls">
                <el-select
                  v-model="qaUsageUser"
                  filterable
                  remote
                  reserve-keyword
                  :remote-method="searchStudents"
                  :loading="studentLoading"
                  placeholder="输入学号或姓名搜索..."
                  size="small"
                  style="width: 220px"
                  @change="loadQaUsage"
                >
                  <el-option
                    v-for="s in studentList"
                    :key="s.username"
                    :label="`${s.realName} (${s.username})`"
                    :value="s.username"
                  />
                </el-select>
                <div class="metric-summary" v-if="qaUsageUser">
                  <span class="metric-badge">今日使用 <b>{{ qaUsage.todayCalls }}</b> 次</span>
                  <span class="metric-badge">累计使用 <b>{{ qaUsage.totalCalls }}</b> 次</span>
                </div>
              </div>
            </div>
          </template>
          <div v-if="!qaUsageUser" class="qa-usage-placeholder">
            <el-icon :size="32"><ChatLineSquare /></el-icon>
            <p>请选择一名学生查看其 AI 问答使用统计</p>
          </div>
          <div v-else class="qa-usage-detail">
            <div class="qa-usage-user">
              <span class="qa-usage-label">当前查看：</span>
              <strong>{{ qaUsageUser }}</strong>
            </div>
            <v-chart v-if="qaTrendOption" :option="qaTrendOption" style="height:200px" autoresize />
            <div v-else class="chart-loading"><el-icon class="is-loading"><Loading /></el-icon> 加载中...</div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 推荐效果 -->
    <el-row :gutter="24" class="mt-32">
      <el-col :span="24">
        <el-card class="glass-card shadow-sm">
          <template #header>
            <div class="card-header">
              <span class="title-with-dot">推荐算法效果</span>
              <div class="metric-summary">
                <span class="metric-badge">曝光 <b>{{ recMetrics.totalViews }}</b></span>
                <span class="metric-badge">点击 <b>{{ recMetrics.totalClicks }}</b></span>
                <span class="metric-badge">点击率 <b>{{ (recMetrics.clickRate * 100).toFixed(1) }}%</b></span>
                <span class="metric-badge">换一批 <b>{{ recMetrics.totalRefreshes }}</b></span>
              </div>
            </div>
          </template>
          <v-chart v-if="recLineOption" :option="recLineOption" style="height:200px" autoresize />
          <div v-else class="chart-loading"><el-icon class="is-loading"><Loading /></el-icon> 加载中...</div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { User, Reading, School, Document, Loading, ChatLineSquare } from '@element-plus/icons-vue'
import request from '@/utils/request'
import VChart from 'vue-echarts'
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { LineChart, BarChart, PieChart } from 'echarts/charts'
import {
  GridComponent, TooltipComponent, LegendComponent, TitleComponent
} from 'echarts/components'
import {
  getUserActivityMetrics, getLearningBehaviorMetrics,
  getAiUsageMetrics, getRecommendationMetrics, getUserGrowth,
  getAiQaUsage, getAiQaUsageTrend, getStudents
} from '@/api/admin'

use([CanvasRenderer, LineChart, BarChart, PieChart,
  GridComponent, TooltipComponent, LegendComponent, TitleComponent])

const today = new Date()
const fmt = (d) => d.toISOString().slice(0, 10)
const daysAgo = (n) => { const d = new Date(today); d.setDate(d.getDate() - n); return d }

const datePresets = [
  { label: '最近7天',  days: 6 },
  { label: '最近30天', days: 29 },
  { label: '最近90天', days: 89 }
]
const activePreset = ref('最近30天')
const dateRange = reactive({ startDate: fmt(daysAgo(29)), endDate: fmt(today) })
const customRange = ref([dateRange.startDate, dateRange.endDate])

const applyPreset = (preset) => {
  activePreset.value = preset.label
  dateRange.startDate = fmt(daysAgo(preset.days))
  dateRange.endDate = fmt(today)
  customRange.value = [dateRange.startDate, dateRange.endDate]
  loadMetrics()
}

const onCustomRangeChange = (val) => {
  if (!val) return
  activePreset.value = ''
  dateRange.startDate = val[0]
  dateRange.endDate = val[1]
  loadMetrics()
}

const stats = reactive({ totalUsers: 0, totalCourses: 0, totalClasses: 0, totalWords: 0 })
const userStats = reactive({ admin: 0, teacher: 0, student: 0 })
const recentActivities = ref([])

const activityMetrics = reactive({ dau: 0, mau: 0, totalUsers: 0 })
const learningMetrics = reactive({ totalReads: 0, totalRecords: 0 })
const aiMetrics = reactive({ totalCalls: 0, todayCalls: 0, successRate: 0, avgResponseMs: 0 })
const recMetrics = reactive({ totalViews: 0, totalClicks: 0, totalRefreshes: 0, clickRate: 0 })
const growthMetrics = reactive({ currentTotal: 0 })

const dauChartOption = ref(null)
const learningPieOption = ref(null)
const learningBarOption = ref(null)
const aiLineOption = ref(null)
const aiPieOption = ref(null)
const recLineOption = ref(null)
const growthChartOption = ref(null)

const cetLoading = ref(false)
const cetData = ref(null)

// AI 问答使用统计（按用户）
const qaUsageUser = ref('')
const studentList = ref([])
const qaUsage = reactive({ todayCalls: 0, totalCalls: 0 })
const qaTrendOption = ref(null)

const CHART_COLORS = ['#6366f1', '#ec4899', '#06b6d4', '#10b981', '#f59e0b', '#ef4444', '#8b5cf6']

const RECORD_TYPE_LABELS = {
  TEXT_VIEW: '课文阅读', VIEW: '单词浏览', REVIEW: '复习', TEST: '测验',
  WORD_COMPLETE: '单词学习', COMPLETE: '任务完成',
  RECOMMENDATION_VIEW: '推荐曝光', RECOMMENDATION_CLICK: '推荐点击', RECOMMENDATION_REFRESH: '换一批'
}

const AI_SERVICE_LABELS = {
  HIAGENT: 'HiAgent 问答', OPENROUTER: 'OpenRouter',
  TRANSLATE: 'AI 翻译', VERSION_GEN: '版本生成', QA: 'AI 问答'
}

const cetChartOption = computed(() => {
  if (!cetData.value) return {}
  return {
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    legend: { data: ['使用平台', '未使用平台'] },
    grid: { left: '3%', right: '4%', bottom: '3%', containLabel: true },
    xAxis: { type: 'category', data: ['通过率 (%)', '通过人数', '总人数'] },
    yAxis: { type: 'value' },
    series: [
      {
        name: '使用平台', type: 'bar',
        data: [cetData.value.used?.passRate || 0, cetData.value.used?.passed || 0, cetData.value.used?.totalStudents || 0],
        itemStyle: { color: '#10b981' }
      },
      {
        name: '未使用平台', type: 'bar',
        data: [cetData.value.unused?.passRate || 0, cetData.value.unused?.passed || 0, cetData.value.unused?.totalStudents || 0],
        itemStyle: { color: '#94a3b8' }
      }
    ]
  }
})

const calcPercent = (val) => {
  if (!stats.totalUsers) return 0
  return Math.round((val / stats.totalUsers) * 100)
}

const loadDashboardData = async () => {
  try {
    const res = await request({ url: '/api/admin/stats', method: 'get' })
    if (res?.code === 200 && res?.data) {
      const r = res.data
      stats.totalUsers = r.totalUsers || 0
      stats.totalCourses = r.totalCourses || 0
      stats.totalClasses = r.totalClasses || 0
      stats.totalWords = r.totalWords || 0
      userStats.admin = r.totalAdmins || 0
      userStats.teacher = r.totalTeachers || 0
      userStats.student = r.totalStudents || 0
    }
  } catch (e) {
    console.error('加载仪表板数据失败:', e)
  }
}

const loadCetData = async () => {
  cetLoading.value = true
  try {
    const res = await request({ url: '/api/admin/metrics/cet-comparison', method: 'get' })
    if (res?.code === 200 && res?.data?.totalScoreRecords > 0) {
      cetData.value = res.data
    }
  } catch (e) {
    console.error('加载四六级对比数据失败:', e)
  } finally {
    cetLoading.value = false
  }
}

const studentLoading = ref(false)
let studentSearchTimer = null
const searchStudents = (query) => {
  if (studentSearchTimer) clearTimeout(studentSearchTimer)
  studentSearchTimer = setTimeout(async () => {
    if (!query || query.length < 1) {
      studentList.value = []
      return
    }
    studentLoading.value = true
    try {
      const res = await getStudents({ keyword: query, limit: 20 })
      if (res?.code === 200 && res?.data) {
        studentList.value = res.data
      }
    } catch (e) {
      console.error('搜索学生失败:', e)
    } finally {
      studentLoading.value = false
    }
  }, 300)
}

const loadQaUsage = async () => {
  if (!qaUsageUser.value) {
    qaUsage.todayCalls = 0
    qaUsage.totalCalls = 0
    qaTrendOption.value = null
    return
  }
  qaTrendOption.value = null
  try {
    const [statsRes, trendRes] = await Promise.allSettled([
      getAiQaUsage(qaUsageUser.value),
      getAiQaUsageTrend(qaUsageUser.value)
    ])

    if (statsRes.status === 'fulfilled' && statsRes.value?.code === 200 && statsRes.value?.data) {
      const d = statsRes.value.data
      qaUsage.todayCalls = d.todayCalls ?? 0
      qaUsage.totalCalls = d.totalCalls ?? 0
    }

    if (trendRes.status === 'fulfilled' && trendRes.value?.code === 200 && trendRes.value?.data) {
      const trend = trendRes.value.data.trend || []
      const dateLabels = trend.map(t => t.date.slice(5))  // MM-DD
      const counts = trend.map(t => t.count)
      qaTrendOption.value = {
        grid: { left: 40, right: 16, top: 16, bottom: 24 },
        tooltip: { trigger: 'axis' },
        xAxis: {
          type: 'category',
          data: dateLabels,
          axisLabel: { fontSize: 11, color: '#94a3b8' },
          axisLine: { lineStyle: { color: '#e2e8f0' } },
          axisTick: { show: false }
        },
        yAxis: {
          type: 'value',
          minInterval: 1,
          axisLabel: { fontSize: 11, color: '#94a3b8' },
          splitLine: { lineStyle: { color: '#f1f5f9' } }
        },
        series: [{
          type: 'line',
          data: counts,
          smooth: true,
          symbol: 'circle',
          symbolSize: 6,
          lineStyle: { color: '#6366f1', width: 2 },
          itemStyle: { color: '#6366f1' },
          areaStyle: {
            color: {
              type: 'linear', x: 0, y: 0, x2: 0, y2: 1,
              colorStops: [
                { offset: 0, color: 'rgba(99, 102, 241, 0.2)' },
                { offset: 1, color: 'rgba(99, 102, 241, 0.02)' }
              ]
            }
          }
        }]
      }
    }
  } catch (e) {
    console.error('加载AI问答使用统计失败:', e)
  }
}

const loadMetrics = async () => {
  dauChartOption.value = null
  learningPieOption.value = null
  learningBarOption.value = null
  aiLineOption.value = null
  aiPieOption.value = null
  recLineOption.value = null
  growthChartOption.value = null

  const params = { startDate: dateRange.startDate, endDate: dateRange.endDate }
  const [actRes, learnRes, aiRes, recRes, growthRes] = await Promise.allSettled([
    getUserActivityMetrics(params),
    getLearningBehaviorMetrics(params),
    getAiUsageMetrics(params),
    getRecommendationMetrics(params),
    getUserGrowth(params)
  ])

  if (actRes.status === 'fulfilled' && actRes.value?.code === 200) {
    const d = actRes.value.data
    activityMetrics.dau = d.dau || 0
    activityMetrics.mau = d.mau || 0
    const trend = d.dauTrend || []
    dauChartOption.value = {
      tooltip: { trigger: 'axis' },
      grid: { left: 40, right: 20, top: 20, bottom: 30 },
      xAxis: { type: 'category', data: trend.map(t => t.date.slice(5)), axisLabel: { fontSize: 11 } },
      yAxis: { type: 'value', minInterval: 1 },
      series: [{ name: '日活用户', type: 'line', smooth: true, data: trend.map(t => t.count),
        areaStyle: { color: 'rgba(99,102,241,0.12)' }, lineStyle: { color: '#6366f1', width: 2 }, itemStyle: { color: '#6366f1' } }]
    }
  }

  if (learnRes.status === 'fulfilled' && learnRes.value?.code === 200) {
    const d = learnRes.value.data
    learningMetrics.totalReads = d.totalReads || 0
    learningMetrics.totalRecords = d.totalRecords || 0
    const dist = d.recordTypeDistribution || []
    learningPieOption.value = {
      tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
      legend: { bottom: 0, type: 'scroll', textStyle: { fontSize: 11 } },
      series: [{ type: 'pie', radius: ['40%', '70%'], center: ['50%', '45%'],
        data: dist.map((item, i) => ({ name: RECORD_TYPE_LABELS[item.type] || item.type, value: item.count,
          itemStyle: { color: CHART_COLORS[i % CHART_COLORS.length] } })) }]
    }
    const daily = d.dailyTrend || []
    learningBarOption.value = {
      tooltip: { trigger: 'axis' },
      grid: { left: 40, right: 20, top: 20, bottom: 30 },
      xAxis: { type: 'category', data: daily.map(t => t.date.slice(5)), axisLabel: { fontSize: 11 } },
      yAxis: { type: 'value', minInterval: 1 },
      series: [{ name: '学习记录', type: 'bar', barMaxWidth: 20, data: daily.map(t => t.count),
        itemStyle: { color: '#06b6d4', borderRadius: [4, 4, 0, 0] } }]
    }
  }

  if (aiRes.status === 'fulfilled' && aiRes.value?.code === 200) {
    const d = aiRes.value.data
    aiMetrics.totalCalls = d.totalCalls || 0
    aiMetrics.todayCalls = d.todayCalls || 0
    aiMetrics.successRate = d.successRate || 0
    aiMetrics.avgResponseMs = d.avgResponseMs || 0
    const daily = d.dailyTrend || []
    aiLineOption.value = {
      tooltip: { trigger: 'axis' },
      grid: { left: 40, right: 20, top: 20, bottom: 30 },
      xAxis: { type: 'category', data: daily.map(t => t.date.slice(5)), axisLabel: { fontSize: 11 } },
      yAxis: { type: 'value', minInterval: 1 },
      series: [{ name: 'AI调用', type: 'line', smooth: true, data: daily.map(t => t.count),
        areaStyle: { color: 'rgba(236,72,153,0.1)' }, lineStyle: { color: '#ec4899', width: 2 }, itemStyle: { color: '#ec4899' } }]
    }
    const svc = d.serviceDistribution || []
    aiPieOption.value = {
      tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
      legend: { bottom: 0, type: 'scroll', textStyle: { fontSize: 11 } },
      series: [{ type: 'pie', radius: ['35%', '65%'], center: ['50%', '45%'],
        data: svc.map((item, i) => ({ name: AI_SERVICE_LABELS[item.service] || item.service, value: item.count,
          itemStyle: { color: CHART_COLORS[i % CHART_COLORS.length] } })) }]
    }
  }

  if (recRes.status === 'fulfilled' && recRes.value?.code === 200) {
    const d = recRes.value.data
    recMetrics.totalViews = d.totalViews || 0
    recMetrics.totalClicks = d.totalClicks || 0
    recMetrics.totalRefreshes = d.totalRefreshes || 0
    recMetrics.clickRate = d.clickRate || 0
    const daily = d.dailyTrend || []
    recLineOption.value = {
      tooltip: { trigger: 'axis', formatter: (params) => `${params[0].name}<br/>点击率: ${(params[0].value * 100).toFixed(1)}%` },
      grid: { left: 50, right: 20, top: 20, bottom: 30 },
      xAxis: { type: 'category', data: daily.map(t => t.date.slice(5)), axisLabel: { fontSize: 11 } },
      yAxis: { type: 'value', min: 0, max: 1, axisLabel: { formatter: v => (v * 100).toFixed(0) + '%' } },
      series: [{ name: '点击率', type: 'line', smooth: true, data: daily.map(t => t.clickRate),
        areaStyle: { color: 'rgba(16,185,129,0.1)' }, lineStyle: { color: '#10b981', width: 2 }, itemStyle: { color: '#10b981' } }]
    }
  }

  if (growthRes.status === 'fulfilled' && growthRes.value?.code === 200) {
    const d = growthRes.value.data
    growthMetrics.currentTotal = d.currentTotal || 0
    const trend = d.trend || []
    growthChartOption.value = {
      tooltip: { trigger: 'axis', axisPointer: { type: 'cross' } },
      legend: { data: ['新增用户', '累计用户'], bottom: 0 },
      grid: { left: 40, right: 40, top: 20, bottom: 50 },
      xAxis: { type: 'category', data: trend.map(t => t.date.slice(5)), axisLabel: { fontSize: 11 } },
      yAxis: [
        { type: 'value', name: '新增', position: 'left', minInterval: 1 },
        { type: 'value', name: '累计', position: 'right', minInterval: 1 }
      ],
      series: [
        { name: '新增用户', type: 'bar', barMaxWidth: 30, data: trend.map(t => t.newUsers),
          itemStyle: { color: '#6366f1', borderRadius: [4, 4, 0, 0] } },
        { name: '累计用户', type: 'line', yAxisIndex: 1, smooth: true, data: trend.map(t => t.totalUsers),
          lineStyle: { color: '#ec4899', width: 2 }, itemStyle: { color: '#ec4899' } }
      ]
    }
  }
}

onMounted(() => {
  loadDashboardData()
  loadCetData()
  loadMetrics()
})
</script>

<style scoped>
.dashboard-container { max-width: 1440px; margin: 0 auto; padding: 32px; }

.stats-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(260px, 1fr)); gap: 24px; }

.modern-stat-card {
  height: 160px; background: white; border-radius: 24px; padding: 24px;
  position: relative; overflow: hidden;
  box-shadow: 0 4px 6px -1px rgba(0,0,0,0.05), 0 2px 4px -1px rgba(0,0,0,0.03);
  transition: all 0.3s cubic-bezier(0.4,0,0.2,1);
  display: flex; flex-direction: column; justify-content: center;
}
.modern-stat-card:hover { transform: translateY(-4px); box-shadow: 0 20px 25px -5px rgba(0,0,0,0.1), 0 10px 10px -5px rgba(0,0,0,0.04); }
.card-bg-icon { position: absolute; right: -20px; top: -10px; font-size: 120px; opacity: 0.05; transform: rotate(15deg); transition: all 0.5s; }
.modern-stat-card:hover .card-bg-icon { transform: rotate(0deg) scale(1.1); opacity: 0.1; }
.modern-stat-card .label { font-size: 16px; font-weight: 600; color: #64748b; margin-bottom: 8px; }
.modern-stat-card .value { font-size: 36px; font-weight: 800; color: #1e293b; line-height: 1; }
.card-footer { margin-top: 16px; }
.trend { font-size: 13px; padding: 4px 10px; border-radius: 100px; background: #f1f5f9; color: #64748b; }

.users { border-bottom: 4px solid #6366f1; }
.courses { border-bottom: 4px solid #ec4899; }
.classes { border-bottom: 4px solid #06b6d4; }
.words { border-bottom: 4px solid #10b981; }

.glass-card { border-radius: 24px; border: 1px solid #e2e8f0; }

.title-with-dot { font-size: 18px; font-weight: 700; position: relative; padding-left: 16px; }
.title-with-dot::before {
  content: ''; position: absolute; left: 0; top: 50%; transform: translateY(-50%);
  width: 6px; height: 6px; border-radius: 50%; background: #6366f1;
}

.dist-container { height: 280px; display: flex; align-items: center; }
.dist-chart { width: 100%; }
.dist-item { margin-bottom: 24px; }
.dist-info { display: flex; justify-content: space-between; margin-bottom: 8px; }
.dist-info .name { font-weight: 600; color: #475569; }
.dist-info .val { font-weight: 800; color: #1e293b; }

.timeline-container { height: 280px; overflow-y: auto; padding-right: 12px; }
.activity-box { background: #f8fafc; padding: 12px 16px; border-radius: 12px; border: 1px solid #f1f5f9; }
.activity-box p { margin: 0; color: #334155; font-size: 14px; line-height: 1.5; }

.mt-32 { margin-top: 32px; }

.card-header { display: flex; align-items: center; justify-content: space-between; flex-wrap: wrap; gap: 8px; }
.metric-summary { display: flex; gap: 8px; flex-wrap: wrap; }
.metric-badge { font-size: 12px; padding: 3px 10px; border-radius: 100px; background: #f1f5f9; color: #64748b; }
.metric-badge b { color: #1e293b; font-weight: 700; }
.qa-usage-controls { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; }
.qa-usage-placeholder { display: flex; flex-direction: column; align-items: center; justify-content: center; padding: 32px; color: #94a3b8; gap: 12px; }
.qa-usage-placeholder p { margin: 0; font-size: 14px; }
.qa-usage-detail { padding: 8px 0; }
.qa-usage-user { font-size: 14px; color: #475569; }
.qa-usage-label { color: #94a3b8; margin-right: 4px; }
.qa-usage-user strong { color: #1e293b; }
.chart-loading { height: 220px; display: flex; align-items: center; justify-content: center; color: #94a3b8; gap: 8px; font-size: 14px; }

.cet-comparison { padding: 8px 0; }
.cet-summary { display: flex; gap: 24px; margin-bottom: 24px; flex-wrap: wrap; }
.cet-stat-item { flex: 1; min-width: 100px; text-align: center; padding: 16px; background: #f8fafc; border-radius: 12px; }
.cet-stat-item.highlight { background: linear-gradient(135deg, #10b981, #059669); }
.cet-stat-item.highlight .cet-stat-value,
.cet-stat-item.highlight .cet-stat-label { color: white; }
.cet-stat-value { font-size: 28px; font-weight: 800; color: #1e293b; line-height: 1; }
.cet-stat-label { font-size: 13px; color: #64748b; margin-top: 6px; }
.cet-bars { display: flex; flex-direction: column; gap: 16px; }
.cet-bar-row { display: flex; align-items: center; gap: 16px; }
.cet-bar-label { width: 90px; font-size: 14px; font-weight: 600; color: #475569; flex-shrink: 0; }

.date-filter-bar { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; }
.quick-btns { display: flex; gap: 8px; flex-wrap: wrap; }
.preset-btn {
  padding: 6px 16px; border-radius: 100px; border: 1px solid #e2e8f0;
  background: #f8fafc; color: #475569; font-size: 13px; font-weight: 500;
  cursor: pointer; transition: all 0.2s;
}
.preset-btn:hover { border-color: #6366f1; color: #6366f1; }
.preset-btn.active { background: #6366f1; border-color: #6366f1; color: white; }

@media (min-width: 1200px) {
  .dashboard-container { padding: 40px; }
  .modern-stat-card { height: 180px; padding: 32px; }
  .modern-stat-card .label { font-size: 18px; }
  .modern-stat-card .value { font-size: 42px; }
  .title-with-dot { font-size: 22px; }
  .dist-info .name, .dist-info .val { font-size: 16px; }
  .activity-box p { font-size: 15px; }
}

@media (max-width: 768px) {
  .dashboard-container { padding: 16px; }
  .modern-stat-card { height: 140px; padding: 20px; }
  .modern-stat-card .value { font-size: 28px; }
}
</style>
