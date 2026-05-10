<template>
  <div class="student-schedule">
    <!-- 学期选择器 -->
    <div class="semester-selector">
      <el-select v-model="academicYear" placeholder="年份" size="small" style="width: 130px" @change="loadSchedule">
        <el-option v-for="y in academicYears" :key="y" :label="y" :value="y" />
      </el-select>
      <el-select v-model="semester" placeholder="学期" size="small" style="width: 96px" @change="loadSchedule">
        <el-option :value="1" label="秋季学期" />
        <el-option :value="2" label="春季学期" />
      </el-select>
      <el-button size="small" :loading="refreshing" @click="handleRefresh">
        刷新
      </el-button>
    </div>

    <!-- 课表网格 -->
    <div v-loading="loading" class="schedule-container">
      <div v-if="scheduleList.length === 0 && !loading" class="empty-state">
        <el-empty description="暂无课表数据">
          <el-button type="primary" size="small" @click="handleRefresh">从教务系统同步</el-button>
        </el-empty>
      </div>

      <div v-else class="schedule-grid">
        <!-- 星期头 -->
        <div class="grid-header">
          <div class="grid-corner"></div>
          <div v-for="d in 7" :key="d" class="grid-day">
            {{ dayNames[d - 1] }}
          </div>
        </div>

        <!-- 课表行 -->
        <div v-for="period in periods" :key="period" class="grid-row">
          <div class="grid-period">{{ period }}</div>
          <div v-for="d in 7" :key="d" class="grid-cell" @click="showDetail(d, period)">
            <div
              v-for="item in getCellItems(d, period)"
              :key="item.id"
              class="cell-item"
              :style="{ background: getCourseColor(item.courseCode) }"
            >
              <div class="cell-course">{{ item.courseName }}</div>
              <div class="cell-teacher">{{ item.teacherName }}</div>
              <div class="cell-location">{{ item.classLocation }}</div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 课程详情弹窗 -->
    <el-dialog v-model="detailVisible" title="课程详情" width="90%" :append-to-body="true">
      <div v-if="detailItems.length > 0" class="detail-content">
        <div v-for="item in detailItems" :key="item.id" class="detail-item">
          <div class="detail-row">
            <span class="detail-label">课程</span>
            <span class="detail-value">{{ item.courseName }}</span>
          </div>
          <div v-if="item.courseCode" class="detail-row">
            <span class="detail-label">课程代码</span>
            <span class="detail-value">{{ item.courseCode }}</span>
          </div>
          <div class="detail-row">
            <span class="detail-label">教师</span>
            <span class="detail-value">{{ item.teacherName }}</span>
          </div>
          <div class="detail-row">
            <span class="detail-label">教室</span>
            <span class="detail-value">{{ item.classLocation }}</span>
          </div>
          <div class="detail-row">
            <span class="detail-label">节次</span>
            <span class="detail-value">{{ item.period }}</span>
          </div>
          <div class="detail-row">
            <span class="detail-label">周次</span>
            <span class="detail-value">{{ item.weekRange }}</span>
          </div>
          <div class="detail-row">
            <span class="detail-label">教学班</span>
            <span class="detail-value">{{ item.classId }}</span>
          </div>
          <div v-if="item.academicYear" class="detail-row">
            <span class="detail-label">学年</span>
            <span class="detail-value">{{ item.academicYear }}</span>
          </div>
          <div v-if="item.semester" class="detail-row">
            <span class="detail-label">学期</span>
            <span class="detail-value">{{ item.semester === 1 ? '秋季' : '春季' }}</span>
          </div>
        </div>
      </div>
      <div v-else class="detail-empty">该时段无课程</div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { getStudentSchedule, refreshStudentSchedule } from '@/api/student'
import { ElMessage } from 'element-plus'

const dayNames = ['周一', '周二', '周三', '周四', '周五', '周六', '周日']
const periods = [1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12]

const loading = ref(false)
const refreshing = ref(false)
const scheduleList = ref([])

// 自动计算当前学年学期
const now = new Date()
const currentMonth = now.getMonth() + 1
const currentYear = now.getFullYear()
const defaultAcademicYear = String(currentYear)
const defaultSemester = (currentMonth >= 2 && currentMonth <= 7) ? 2 : 1

const academicYear = ref(defaultAcademicYear)
const semester = ref(defaultSemester)

// 生成年份选项（当前年向前 4 年）
const academicYears = computed(() => {
  const years = []
  for (let year = currentYear - 4; year <= currentYear; year++) {
    years.push(String(year))
  }
  return years
})

// 课程颜色缓存
const colorCache = {}
const colorPalette = [
  '#e3f2fd', '#f3e5f5', '#e8f5e9', '#fff3e0', '#e0f2f1',
  '#fce4ec', '#f1f8e9', '#e8eaf6', '#fff8e1', '#e0f7fa'
]
let colorIndex = 0

function getCourseColor(courseCode) {
  if (!colorCache[courseCode]) {
    colorCache[courseCode] = colorPalette[colorIndex % colorPalette.length]
    colorIndex++
  }
  return colorCache[courseCode]
}

// 获取某个单元格的课程列表
function getCellItems(dayOfWeek, period) {
  return scheduleList.value.filter(item => {
    if (item.dayOfWeek !== dayOfWeek) return false
    // period 字段可能是 "1-2" 这样的范围，也可能是单数字
    const p = String(item.period)
    const match = p.match(/^(\d+)/)
    if (match) {
      return parseInt(match[1]) === period
    }
    return false
  })
}

// 详情弹窗
const detailVisible = ref(false)
const detailItems = ref([])

function showDetail(dayOfWeek, period) {
  detailItems.value = getCellItems(dayOfWeek, period)
  detailVisible.value = true
}

// 加载课表
async function loadSchedule() {
  loading.value = true
  try {
    const res = await getStudentSchedule({
      academicYear: academicYear.value,
      semester: semester.value
    })
    scheduleList.value = res?.data || []
  } catch (e) {
    console.error('获取课表失败:', e)
    scheduleList.value = []
  } finally {
    loading.value = false
  }
}

// 刷新（从学校API同步）
async function handleRefresh() {
  refreshing.value = true
  try {
    const res = await refreshStudentSchedule({
      academicYear: academicYear.value,
      semester: semester.value
    })
    scheduleList.value = res?.data || []
    ElMessage.success('课表同步成功')
  } catch (e) {
    console.error('同步课表失败:', e)
    ElMessage.error('同步课表失败，请稍后重试')
  } finally {
    refreshing.value = false
  }
}

onMounted(() => {
  loadSchedule()
})
</script>

<style scoped>
.student-schedule {
  padding: 12px;
  min-height: 100%;
  background: var(--bg-secondary);
}

.semester-selector {
  display: flex;
  gap: 8px;
  align-items: center;
  margin-bottom: 12px;
  flex-wrap: wrap;
}

.schedule-container {
  min-height: 200px;
}

.empty-state {
  padding: 40px 0;
}

/* 课表网格 */
.schedule-grid {
  overflow-x: auto;
  -webkit-overflow-scrolling: touch;
}

.grid-header {
  display: grid;
  grid-template-columns: 36px repeat(7, 1fr);
  gap: 1px;
  background: var(--border-light);
  border-radius: 8px 8px 0 0;
  overflow: hidden;
}

.grid-corner {
  background: var(--bg-tertiary);
  padding: 6px 2px;
  font-size: 10px;
  text-align: center;
  color: var(--text-tertiary);
}

.grid-day {
  background: var(--bg-tertiary);
  padding: 6px 2px;
  font-size: 11px;
  font-weight: 600;
  text-align: center;
  color: var(--text-secondary);
}

.grid-row {
  display: grid;
  grid-template-columns: 36px repeat(7, 1fr);
  gap: 1px;
  background: var(--border-light);
}

.grid-period {
  background: var(--bg-tertiary);
  padding: 4px 2px;
  font-size: 10px;
  text-align: center;
  color: var(--text-tertiary);
  display: flex;
  align-items: center;
  justify-content: center;
}

.grid-cell {
  background: var(--bg-primary);
  min-height: 40px;
  padding: 2px;
  cursor: pointer;
  transition: background 0.15s;
}

.grid-cell:hover {
  background: var(--bg-tertiary);
}

.cell-item {
  border-radius: 4px;
  padding: 2px 4px;
  margin-bottom: 1px;
  overflow: hidden;
}

.cell-course {
  font-size: 10px;
  font-weight: 600;
  color: var(--text-primary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.cell-teacher {
  font-size: 9px;
  color: var(--text-secondary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.cell-location {
  font-size: 9px;
  color: var(--text-tertiary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

/* 详情弹窗 */
.detail-content {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.detail-item {
  padding: 12px;
  background: var(--bg-secondary);
  border-radius: 8px;
}

.detail-row {
  display: flex;
  justify-content: space-between;
  padding: 4px 0;
  font-size: 14px;
}

.detail-label {
  color: var(--text-tertiary);
  flex-shrink: 0;
}

.detail-value {
  color: var(--text-primary);
  text-align: right;
  word-break: break-all;
}

.detail-empty {
  text-align: center;
  padding: 24px;
  color: var(--text-tertiary);
}

@media (max-width: 480px) {
  .student-schedule {
    padding: 8px;
  }

  .grid-day {
    font-size: 10px;
  }

  .cell-course {
    font-size: 9px;
  }

  .cell-teacher {
    font-size: 8px;
  }

  .cell-location {
    font-size: 8px;
  }
}
</style>
