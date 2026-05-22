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
import { ref, reactive, onMounted, computed } from 'vue'
import { User, Reading, School, Document, Loading } from '@element-plus/icons-vue'
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
  getAiUsageMetrics, getRecommendationMetrics
} from '@/api/admin'

use([CanvasRenderer, LineChart, BarChart, PieChart,
  GridComponent, TooltipComponent, LegendComponent, TitleComponent])

const stats = reactive({
  totalUsers: 0,
  totalCourses: 0,
  totalClasses: 0,
  totalWords: 0
})

const userStats = reactive({ admin: 0, teacher: 0, student: 0 })
const recentActivities = ref([])

// 指标数据
const activityMetrics = reactive({ dau: 0, mau: 0, totalUsers: 0 })
const learningMetrics = reactive({ totalReads: 0, totalRecords: 0 })
const aiMetrics = reactive({ totalCalls: 0, todayCalls: 0, successRate: 0, avgResponseMs: 0 })
const recMetrics = reactive({ totalViews: 0, totalClicks: 0, totalRefreshes: 0, clickRate: 0 })

// ECharts options
const dauChartOption = ref(null)
const learningPieOption = ref(null)
const learningBarOption = ref(null)
const aiLineOption = ref(null)
const aiPieOption = ref(null)
const recLineOption = ref(null)

const CHART_COLORS = ['#6366f1', '#ec4899', '#06b6d4', '#10b981', '#f59e0b', '#ef4444', '#8b5cf6']

const RECORD_TYPE_LABELS = {
  TEXT_VIEW: '课文阅读',
  VIEW: '内容浏览',
  REVIEW: '复习',
  TEST: '测验',
  WORD_COMPLETE: '单词学习',
  RECOMMENDATION_VIEW: '推荐曝光',
  RECOMMENDATION_CLICK: '推荐点击',
  RECOMMENDATION_REFRESH: '换一批'
}

const AI_SERVICE_LABELS = {
  HIAGENT: 'HiAgent 问答',
  OPENROUTER: 'OpenRouter',
  TRANSLATE: 'AI 翻译',
  VERSION_GEN: '版本生成',
  QA: 'AI 问答'
}

const calcPercent = (val) => {
  if (!stats.totalUsers) return 0
  return Math.round((val / stats.totalUsers) * 100)
}

const loadDashboardData = async () => {
  try {
    const res = await request({ url: '/api/admin/stats', method: 'get' })
    if (res?.code === 200 && res?.data) {
      const result = res.data
      stats.totalUsers = result.totalUsers || 0
      stats.totalCourses = result.totalCourses || 0
      stats.totalClasses = result.totalClasses || 0
      stats.totalWords = result.totalWords || 0
      userStats.admin = result.totalAdmins || 0
      userStats.teacher = result.totalTeachers || 0
      userStats.student = result.totalStudents || 0
    }
  } catch (error) {
    console.error('加载仪表板数据失败:', error)
  }
}

const loadMetrics = async () => {
  // 并行加载4个指标
  const [actRes, learnRes, aiRes, recRes] = await Promise.allSettled([
    getUserActivityMetrics(),
    getLearningBehaviorMetrics(),
    getAiUsageMetrics(),
    getRecommendationMetrics()
  ])

  // 用户活跃度
  if (actRes.status === 'fulfilled' && actRes.value?.code === 200) {
    const d = actRes.value.data
    activityMetrics.dau = d.dau || 0
    activityMetrics.mau = d.mau || 0
    activityMetrics.totalUsers = d.totalUsers || 0
    const trend = d.dauTrend || []
    dauChartOption.value = {
      tooltip: { trigger: 'axis' },
      grid: { left: 40, right: 20, top: 20, bottom: 30 },
      xAxis: { type: 'category', data: trend.map(t => t.date.slice(5)), axisLabel: { fontSize: 11 } },
      yAxis: { type: 'value', minInterval: 1 },
      series: [{
        name: '日活用户', type: 'line', smooth: true,
        data: trend.map(t => t.count),
        areaStyle: { color: 'rgba(99,102,241,0.12)' },
        lineStyle: { color: '#6366f1', width: 2 },
        itemStyle: { color: '#6366f1' }
      }]
    }
  }

  // 学习行为
  if (learnRes.status === 'fulfilled' && learnRes.value?.code === 200) {
    const d = learnRes.value.data
    learningMetrics.totalReads = d.totalReads || 0
    learningMetrics.totalRecords = d.totalRecords || 0
    const dist = d.recordTypeDistribution || []
    learningPieOption.value = {
      tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
      legend: { bottom: 0, type: 'scroll', textStyle: { fontSize: 11 } },
      series: [{
        type: 'pie', radius: ['40%', '70%'], center: ['50%', '45%'],
        data: dist.map((item, i) => ({
          name: RECORD_TYPE_LABELS[item.type] || item.type, value: item.count,
          itemStyle: { color: CHART_COLORS[i % CHART_COLORS.length] }
        }))
      }]
    }
    const daily = d.dailyTrend || []
    learningBarOption.value = {
      tooltip: { trigger: 'axis' },
      grid: { left: 40, right: 20, top: 20, bottom: 30 },
      xAxis: { type: 'category', data: daily.map(t => t.date.slice(5)), axisLabel: { fontSize: 11 } },
      yAxis: { type: 'value', minInterval: 1 },
      series: [{
        name: '学习记录', type: 'bar', barMaxWidth: 20,
        data: daily.map(t => t.count),
        itemStyle: { color: '#06b6d4', borderRadius: [4, 4, 0, 0] }
      }]
    }
  }

  // AI 调用
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
      series: [{
        name: 'AI调用', type: 'line', smooth: true,
        data: daily.map(t => t.count),
        areaStyle: { color: 'rgba(236,72,153,0.1)' },
        lineStyle: { color: '#ec4899', width: 2 },
        itemStyle: { color: '#ec4899' }
      }]
    }
    const svc = d.serviceDistribution || []
    aiPieOption.value = {
      tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
      legend: { bottom: 0, type: 'scroll', textStyle: { fontSize: 11 } },
      series: [{
        type: 'pie', radius: ['35%', '65%'], center: ['50%', '45%'],
        data: svc.map((item, i) => ({
          name: AI_SERVICE_LABELS[item.service] || item.service, value: item.count,
          itemStyle: { color: CHART_COLORS[i % CHART_COLORS.length] }
        }))
      }]
    }
  }

  // 推荐效果
  if (recRes.status === 'fulfilled' && recRes.value?.code === 200) {
    const d = recRes.value.data
    recMetrics.totalViews = d.totalViews || 0
    recMetrics.totalClicks = d.totalClicks || 0
    recMetrics.totalRefreshes = d.totalRefreshes || 0
    recMetrics.clickRate = d.clickRate || 0
    const daily = d.dailyTrend || []
    recLineOption.value = {
      tooltip: { trigger: 'axis', formatter: (params) => {
        const p = params[0]
        return `${p.name}<br/>点击率: ${(p.value * 100).toFixed(1)}%`
      }},
      grid: { left: 50, right: 20, top: 20, bottom: 30 },
      xAxis: { type: 'category', data: daily.map(t => t.date.slice(5)), axisLabel: { fontSize: 11 } },
      yAxis: { type: 'value', min: 0, max: 1, axisLabel: { formatter: v => (v * 100).toFixed(0) + '%' } },
      series: [{
        name: '点击率', type: 'line', smooth: true,
        data: daily.map(t => t.clickRate),
        areaStyle: { color: 'rgba(16,185,129,0.1)' },
        lineStyle: { color: '#10b981', width: 2 },
        itemStyle: { color: '#10b981' }
      }]
    }
  }
}

onMounted(() => {
  loadDashboardData()
  loadMetrics()
})
</script>

<style scoped>
.dashboard-container {
  max-width: 1440px;
  margin: 0 auto;
  padding: 32px;
}

.stats-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(260px, 1fr));
  gap: 24px;
}

.modern-stat-card {
  height: 160px;
  background: white;
  border-radius: 24px;
  padding: 24px;
  position: relative;
  overflow: hidden;
  box-shadow: 0 4px 6px -1px rgba(0, 0, 0, 0.05), 0 2px 4px -1px rgba(0, 0, 0, 0.03);
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  display: flex;
  flex-direction: column;
  justify-content: center;
}

.modern-stat-card:hover {
  transform: translateY(-4px);
  box-shadow: 0 20px 25px -5px rgba(0, 0, 0, 0.1), 0 10px 10px -5px rgba(0, 0, 0, 0.04);
}

.card-bg-icon {
  position: absolute;
  right: -20px;
  top: -10px;
  font-size: 120px;
  opacity: 0.05;
  transform: rotate(15deg);
  transition: all 0.5s;
}

.modern-stat-card:hover .card-bg-icon {
  transform: rotate(0deg) scale(1.1);
  opacity: 0.1;
}

.modern-stat-card .label {
  font-size: 16px;
  font-weight: 600;
  color: #64748b;
  margin-bottom: 8px;
}

.modern-stat-card .value {
  font-size: 36px;
  font-weight: 800;
  color: #1e293b;
  line-height: 1;
}

.card-footer {
  margin-top: 16px;
}

.trend {
  font-size: 13px;
  padding: 4px 10px;
  border-radius: 100px;
  background: #f1f5f9;
  color: #64748b;
}

/* 颜色变体 */
.users { border-bottom: 4px solid #6366f1; }
.courses { border-bottom: 4px solid #ec4899; }
.classes { border-bottom: 4px solid #06b6d4; }
.words { border-bottom: 4px solid #10b981; }

.glass-card {
  border-radius: 24px;
  border: 1px solid #e2e8f0;
}

.title-with-dot {
  font-size: 18px;
  font-weight: 700;
  position: relative;
  padding-left: 16px;
}

.title-with-dot::before {
  content: '';
  position: absolute;
  left: 0;
  top: 50%;
  transform: translateY(-50%);
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #6366f1;
}

.dist-container {
  height: 280px;
  display: flex;
  align-items: center;
}

.dist-chart {
  width: 100%;
}

.dist-item {
  margin-bottom: 24px;
}

.dist-info {
  display: flex;
  justify-content: space-between;
  margin-bottom: 8px;
}

.dist-info .name {
  font-weight: 600;
  color: #475569;
}

.dist-info .val {
  font-weight: 800;
  color: #1e293b;
}

.timeline-container {
  height: 280px;
  overflow-y: auto;
  padding-right: 12px;
}

.activity-box {
  background: #f8fafc;
  padding: 12px 16px;
  border-radius: 12px;
  border: 1px solid #f1f5f9;
}

.activity-box p {
  margin: 0;
  color: #334155;
  font-size: 14px;
  line-height: 1.5;
}

.mt-32 { margin-top: 32px; }

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 8px;
}

.metric-summary {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.metric-badge {
  font-size: 12px;
  padding: 3px 10px;
  border-radius: 100px;
  background: #f1f5f9;
  color: #64748b;
}

.metric-badge b {
  color: #1e293b;
  font-weight: 700;
}

.chart-loading {
  height: 220px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #94a3b8;
  gap: 8px;
  font-size: 14px;
}

/* PC端大字号适配 */
@media (min-width: 1200px) {
  .dashboard-container {
    padding: 40px;
  }
  
  .modern-stat-card {
    height: 180px;
    padding: 32px;
  }

  .modern-stat-card .label {
    font-size: 18px;
  }

  .modern-stat-card .value {
    font-size: 42px;
  }

  .title-with-dot {
    font-size: 22px;
  }

  .dist-info .name, .dist-info .val {
    font-size: 16px;
  }

  .activity-box p {
    font-size: 15px;
  }
}

@media (max-width: 768px) {
  .dashboard-container {
    padding: 16px;
  }
  
  .modern-stat-card {
    height: 140px;
    padding: 20px;
  }
  
  .modern-stat-card .value {
    font-size: 28px;
  }
}
</style>