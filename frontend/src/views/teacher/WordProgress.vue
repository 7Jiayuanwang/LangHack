<template>
  <div class="word-progress-page">
    <div class="page-content-wrapper">
      <div class="page-header">
        <div class="header-content">
          <h2>单词进度</h2>
          <p class="subtitle">教师查看学生词汇掌握与学习进度</p>
        </div>
        <div class="header-actions">
          <el-select v-model="selectedClassId" placeholder="选择班级" @change="loadProgress" style="min-width: 240px;">
            <el-option v-for="c in classes" :key="c.classId" :label="c.className" :value="c.classId" />
          </el-select>
        </div>
      </div>

      <div class="page-main">
        <!-- 柱状图：全宽，置顶 -->
        <div class="bar-chart-full" v-if="isMounted">
          <component :is="VChart" :option="barOption" style="height:320px;width:100%" />
        </div>

        <!-- 饼图 + 学生列表并排 -->
        <div class="class-chart">
            <div class="chart-left" v-if="isMounted">
              <component :is="VChart" :option="chartOption" style="height:260px;width:100%" />
            </div>
            <div class="chart-right">
              <div class="chart-right-controls">
                <el-input v-model="studentSearch" placeholder="搜索姓名" size="small" style="width:220px" />
              </div>
              <div class="class-avg-bar" v-if="classAvgMastered > 0">
                全班平均已掌握 <strong>{{ classAvgMastered }}</strong> 词
              </div>
              <div class="student-list">
                <div class="student-list-header">
                  <span class="stu-name">姓名</span>
                  <span class="stu-learning">
                    <span class="stu-val">已掌握</span>
                    <span class="stu-pct">掌握率</span>
                    <span class="stu-diff">差值</span>
                  </span>
                </div>
                <div v-for="s in studentMasteredList" :key="s.id || s.name" class="student-list-item">
                  <span class="stu-name">{{ s.name }}</span>
                  <span class="stu-learning">
                    <span class="stu-val" :class="{ zero: s.mastered === 0 }">{{ s.mastered === 0 ? '-' : s.mastered }}</span>
                    <span class="stu-pct" v-if="s.mastered > 0">{{ s.masteredPct }}%</span>
                    <span
                      v-if="s.mastered > 0"
                      class="stu-diff"
                      :class="{ above: s.diff > 0, below: s.diff < 0 }"
                      :title="`全班平均 ${s.avg}，${s.diff >= 0 ? '高出' : '低于'}平均 ${Math.abs(s.diff)} 词`">
                      {{ s.diff > 0 ? '+' : '' }}{{ s.diff || 0 }}
                    </span>
                  </span>
                </div>
              </div>
            </div>
          </div>
        <el-table :data="students" style="width: 100%" v-loading="loading">
        <el-table-column prop="studentId" label="学号/ID" width="120" />
        <el-table-column prop="username" label="用户名" width="140" />
        <el-table-column prop="realName" label="姓名" width="140" />
        <el-table-column label="单词统计" >
          <template #default="{ row }">
            <div v-if="row.wordStats">
              <div class="word-stats-top">总词数: {{ row.wordStats.totalWords ?? row.wordStats.total ?? '-' }}</div>
              <div class="word-stats-bars">
                <div class="stat-item">
                  <div class="stat-label">已掌握: {{ getPercents(row.wordStats).masteredCount }} &nbsp; 学习中: {{ getPercents(row.wordStats).learningCount }}</div>
                  <div class="multi-progress" role="progressbar" aria-valuemin="0" aria-valuemax="100">
                    <div class="segment mastered" :style="{ width: getPercents(row.wordStats).masteredPct + '%' }"></div>
                    <div class="segment learning" :style="{ width: getPercents(row.wordStats).learningPct + '%' }"></div>
                    <div class="segment remainder" :style="{ width: Math.max(0, 100 - (getPercents(row.wordStats).masteredPct + getPercents(row.wordStats).learningPct)) + '%' }"></div>
                  </div>
                  <div class="multi-progress-labels">
                    <span class="mp-label mastered-label">已掌握 {{ getPercents(row.wordStats).masteredPctText }}</span>
                    <span class="mp-label learning-label">学习中 {{ getPercents(row.wordStats).learningPctText }}</span>
                  </div>
                </div>
              </div>
            </div>
            <div v-else>-</div>
          </template>
        </el-table-column>
        <el-table-column label="加入时间" width="120">
          <template #default="{ row }">{{ formatTime(row.classJoinTime) }}</template>
        </el-table-column>
      </el-table>
      </div>

    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted, computed } from 'vue'
import VChart from 'vue-echarts'
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { PieChart, BarChart } from 'echarts/charts'
import { TooltipComponent, LegendComponent, TitleComponent, GridComponent } from 'echarts/components'
import { LegacyGridContainLabel } from 'echarts/features'

use([CanvasRenderer, PieChart, BarChart, TooltipComponent, LegendComponent, TitleComponent, GridComponent, LegacyGridContainLabel])
import request from '@/utils/request'
import { getMyTeachingClasses } from '@/api/teacher'
import { ElMessage } from 'element-plus'

const classes = ref([])
const selectedClassId = ref(null)
const students = ref([])
const loading = ref(false)
const isMounted = ref(false)

const loadClasses = async () => {
  try {
    const res = await getMyTeachingClasses()
    if (res && res.data && Array.isArray(res.data)) {
      classes.value = res.data
      if (classes.value.length > 0) {
        selectedClassId.value = classes.value[0].classId
        await loadProgress(selectedClassId.value)
      }
    }
  } catch (e) {
    console.error(e)
    ElMessage.error('加载授课班级失败')
  }
}

// 汇总班级统计用于图表展示
const chartOption = computed(() => {
  // 按学生计算掌握率与学习率的平均值（每个人的掌握率求和后除以人数）
  let masteredRateSum = 0
  let learningRateSum = 0
  let counted = 0

  students.value.forEach(s => {
    const ws = s.wordStats || {}
    const m = Number(ws.masteredWords ?? ws.mastered ?? 0) || 0
    const l = Number(ws.learningWords ?? ws.learning ?? 0) || 0
    const t = Number(ws.totalWords ?? ws.total ?? (m + l)) || (m + l)
    if (t > 0) {
      masteredRateSum += m / t
      learningRateSum += l / t
      counted += 1
    }
  })

  const avgMasteredPct = counted ? Math.round((masteredRateSum / counted) * 100) : 0
  const avgLearningPct = counted ? Math.round((learningRateSum / counted) * 100) : 0

  return {
    tooltip: { trigger: 'item' },
    legend: { bottom: 0 },
    series: [
      {
        name: '班级词汇占比',
        type: 'pie',
        radius: ['40%', '70%'],
        avoidLabelOverlap: false,
        label: { show: true, position: 'inside', formatter: '{d}%' },
        emphasis: { label: { show: true, fontSize: '16', fontWeight: 'bold' } },
        data: [
          { value: avgMasteredPct, name: '已掌握' },
          { value: avgLearningPct, name: '学习中' },
        ],
      },
    ],
  }
})

// 柱状图：显示班级掌握量排名
const barOption = computed(() => {
  // 根据单个学生的已掌握词数排序
  const rows = (students.value || []).map(s => {
    const ws = s.wordStats || {}
    const mastered = Number(ws.masteredWords ?? ws.mastered ?? 0) || 0
    const name = s.realName || s.username || s.studentId || '学生'
    return { name, mastered }
  }).sort((a, b) => b.mastered - a.mastered)

  const top = rows.slice(0, 10)
  const names = top.map(r => r.name)
  const values = top.map(r => r.mastered)

  // 回退：如果没有数据，显示占位信息以保持图表区域可见
  if (names.length === 0) {
    names.push('无数据')
    values.push(0)
  }

  return {
    tooltip: { trigger: 'axis' },
    grid: { left: '6%', right: '6%', bottom: '12%', containLabel: true },
    xAxis: { type: 'category', data: names, axisLabel: { rotate: 20, interval: 0 } },
    yAxis: { type: 'value', name: '掌握词数' },
    series: [
      { name: '已掌握词数', type: 'bar', data: values, itemStyle: { color: '#16a34a' } },
    ],
  }
})

const loadProgress = async (classId) => {
  if (!classId) return
  loading.value = true
  try {
    const res = await request.get('/api/teacher/word-progress', { params: { classId } })
    if (res && Array.isArray(res.data)) {
      students.value = res.data
    } else if (res && Array.isArray(res)) {
      // 当 request 拦截器直接返回数组时的兼容处理
      students.value = res
    } else if (res && res.data && Array.isArray(res.data.records)) {
      students.value = res.data.records
    }
  } catch (e) {
    console.error(e)
    ElMessage.error('加载学生单词进度失败')
  } finally {
    loading.value = false
  }
}

// 学生列表搜索
const studentSearch = ref('')

const studentMasteredList = computed(() => {
  const rows = (students.value || []).map(s => {
    const ws = s.wordStats || {}
    const mastered = Number(ws.masteredWords ?? ws.mastered ?? 0) || 0
    const total = Number(ws.totalWords ?? ws.total ?? 0) || 0
    const masteredPct = total > 0 ? Math.round((mastered / total) * 100) : 0
    const name = s.realName || s.username || s.studentId || '学生'
    return { id: s.studentId || s.username, name, mastered, masteredPct }
  }).sort((a, b) => b.mastered - a.mastered)

  // 全班平均（基于所有学生）
  const allMastered = rows.map(r => r.mastered)
  const sum = allMastered.reduce((s, v) => s + v, 0)
  const avg = rows.length > 0 ? Math.round(sum / rows.length) : 0

  let list = rows.map(r => ({ ...r, avg, diff: r.mastered - avg }))
  if (studentSearch.value && studentSearch.value.trim()) {
    const q = studentSearch.value.trim().toLowerCase()
    list = list.filter(r => (r.name || '').toLowerCase().includes(q))
  }
  return list
})

// 全班已掌握词数平均（用于页面展示）
const classAvgMastered = computed(() => {
  const rows = (students.value || []).map(s => {
    const ws = s.wordStats || {}
    return Number(ws.masteredWords ?? ws.mastered ?? 0) || 0
  })
  if (rows.length === 0) return 0
  return Math.round(rows.reduce((s, v) => s + v, 0) / rows.length)
})

// 计算已掌握 / 学习中 的数量与百分比（返回百分比为 0-100 的数字）
const getPercents = (ws) => {
  if (!ws || typeof ws !== 'object') {
    return { masteredCount: '-', learningCount: '-', masteredPct: 0, learningPct: 0, masteredPctText: '-', learningPctText: '-' }
  }

  const mastered = Number(ws.masteredWords ?? ws.mastered ?? 0) || 0
  const learning = Number(ws.learningWords ?? ws.learning ?? 0) || 0
  const total = Number(ws.totalWords ?? ws.total ?? (mastered + learning)) || (mastered + learning)

  if (!total || total <= 0) {
    return { masteredCount: mastered, learningCount: learning, masteredPct: 0, learningPct: 0, masteredPctText: '0%', learningPctText: '0%' }
  }

  const masteredPct = Math.round((mastered / total) * 100)
  const learningPct = Math.min(100, Math.round((learning / total) * 100))

  return {
    masteredCount: mastered,
    learningCount: learning,
    masteredPct,
    learningPct,
    masteredPctText: `${masteredPct}%`,
    learningPctText: `${learningPct}%`,
  }
}

const formatTime = (v) => {
  if (!v) return '-'
  try { return new Date(v).toLocaleDateString('zh-CN') } catch { return v }
}

let refreshTimer = null
const REFRESH_INTERVAL_MS = 30000 // 每30秒自动刷新排行榜

onMounted(() => {
  isMounted.value = true
  loadClasses()
  refreshTimer = setInterval(() => {
    if (selectedClassId.value) {
      loadProgress(selectedClassId.value)
    }
  }, REFRESH_INTERVAL_MS)
})

onUnmounted(() => {
  if (refreshTimer) {
    clearInterval(refreshTimer)
    refreshTimer = null
  }
})
</script>

<style scoped>
.page-header {
  display:flex;
  align-items:center;
  justify-content:space-between;
  gap:12px;
  margin-bottom: 20px;
}
.page-header h2 {
  margin: 0 0 6px;
  font-size: 20px;
  font-weight: 700;
}
.subtitle {
  margin: 0;
  font-size: 13px;
  color: #64748b;
}
.page-content-wrapper {
  max-width: 960px;
  margin: 0 auto;
  padding: 0 24px;
}
.page-main {
  padding-bottom: 100px;
}
.word-progress-page {
  padding-top: 12px;
}

.word-stats-top {
  margin-bottom: 8px;
  color: #334155;
  font-size: 13px;
}
.word-stats-bars {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.stat-item {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.stat-label {
  font-size: 12px;
  color: #475569;
}

.multi-progress {
  height: 12px;
  background: #e6edf3;
  border-radius: 8px;
  overflow: hidden;
  display: flex;
  width: 100%;
}
.multi-progress .segment {
  height: 100%;
}
.multi-progress .mastered {
  background: #16a34a; /* green */
}
.multi-progress .learning {
  background: #f59e0b; /* orange */
}
.multi-progress .remainder {
  background: transparent;
}
.multi-progress-labels {
  display:flex;
  gap:12px;
  margin-top:6px;
  font-size:12px;
  color:#475569;
}
.mp-label.mastered-label { color: #16a34a }
.mp-label.learning-label { color: #f59e0b }
/* 并排显示饼图与柱状图 */
.bar-chart-full { margin-bottom: 20px }
.class-chart { display:flex; gap:20px; align-items:flex-start; margin-bottom: 16px }
.chart-left { flex: 1; min-width: 220px }
.chart-right { width: 320px; display:flex; flex-direction:column; gap:8px }
.chart-right-controls { display:flex; align-items:center }
.class-avg-bar { padding:6px 8px; font-size:12px; color:#64748b; background:#f8fafc; border-radius:4px; text-align:center }
.class-avg-bar strong { color:#f59e0b; font-size:14px }
.student-list { max-height: 200px; overflow-y: auto; border-radius:6px; background:#fff; padding:6px; box-shadow: 0 0 0 1px rgba(0,0,0,0.02) }
.student-list-header { display:flex; justify-content:space-between; padding:4px 8px; font-size:11px; color:#94a3b8; border-bottom:1px solid #e2e8f0; background:#fafbfc }
.student-list-item { display:flex; justify-content:space-between; padding:6px 8px; border-bottom:1px solid #f1f5f9 }
.student-list-item:last-child { border-bottom: none }
.stu-name { color:#334155; flex:1; min-width:0; overflow:hidden; text-overflow:ellipsis; white-space:nowrap }
.stu-learning { display:flex; align-items:center; gap:6px; flex-shrink:0 }
.stu-val { color:#16a34a; font-weight:600; min-width:28px; text-align:right }
.stu-pct { font-size:11px; color:#64748b; min-width:32px; text-align:right }
.stu-diff { font-size:11px; font-weight:500; min-width:28px; text-align:right }
.stu-diff.above { color:#16a34a }
.stu-diff.below { color:#f59e0b }
</style>
