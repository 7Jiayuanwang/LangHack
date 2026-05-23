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
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { User, Reading, School, Document, Loading } from '@element-plus/icons-vue'
import request from '@/utils/request'
import VChart from 'vue-echarts'
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { BarChart } from 'echarts/charts'
import { GridComponent, TooltipComponent, LegendComponent } from 'echarts/components'

use([CanvasRenderer, BarChart, GridComponent, TooltipComponent, LegendComponent])

const stats = reactive({
  totalUsers: 0,
  totalCourses: 0,
  totalClasses: 0,
  totalWords: 0
})

// 用户分布统计（初始为0，等待API返回）
const userStats = reactive({
  admin: 0,
  teacher: 0,
  student: 0
})

const recentActivities = ref([])

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

const cetLoading = ref(false)
const cetData = ref(null)

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
        name: '使用平台',
        type: 'bar',
        data: [
          cetData.value.used?.passRate || 0,
          cetData.value.used?.passed || 0,
          cetData.value.used?.totalStudents || 0
        ],
        itemStyle: { color: '#10b981' }
      },
      {
        name: '未使用平台',
        type: 'bar',
        data: [
          cetData.value.unused?.passRate || 0,
          cetData.value.unused?.passed || 0,
          cetData.value.unused?.totalStudents || 0
        ],
        itemStyle: { color: '#94a3b8' }
      }
    ]
  }
})

const loadCetData = async () => {
  cetLoading.value = true
  try {
    const res = await request({ url: '/api/admin/metrics/cet-comparison', method: 'get' })
    if (res?.code === 200 && res?.data) {
      const d = res.data
      if (d.totalScoreRecords > 0) {
        cetData.value = d
      }
    }
  } catch (e) {
    console.error('加载四六级对比数据失败:', e)
  } finally {
    cetLoading.value = false
  }
}

onMounted(() => {
  loadDashboardData()
  loadCetData()
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

.cet-comparison {
  padding: 8px 0;
}

.cet-summary {
  display: flex;
  gap: 24px;
  margin-bottom: 24px;
  flex-wrap: wrap;
}

.cet-stat-item {
  flex: 1;
  min-width: 100px;
  text-align: center;
  padding: 16px;
  background: #f8fafc;
  border-radius: 12px;
}

.cet-stat-item.highlight {
  background: linear-gradient(135deg, #10b981, #059669);
  color: white;
}

.cet-stat-item.highlight .cet-stat-value,
.cet-stat-item.highlight .cet-stat-label {
  color: white;
}

.cet-stat-value {
  font-size: 28px;
  font-weight: 800;
  color: #1e293b;
  line-height: 1;
}

.cet-stat-label {
  font-size: 13px;
  color: #64748b;
  margin-top: 6px;
}

.cet-bars {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.cet-bar-row {
  display: flex;
  align-items: center;
  gap: 16px;
}

.cet-bar-label {
  width: 90px;
  font-size: 14px;
  font-weight: 600;
  color: #475569;
  flex-shrink: 0;
}

.mt-32 { margin-top: 32px; }

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