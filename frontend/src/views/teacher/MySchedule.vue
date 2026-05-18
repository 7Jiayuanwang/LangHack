<template>
  <div class="teacher-my-schedule">
    <div class="filter-bar">
      <el-select v-model="academicYear" placeholder="年份" size="small" style="width: 130px" @change="loadData">
        <el-option v-for="y in academicYears" :key="y" :label="y" :value="y" />
      </el-select>
      <el-select v-model="semester" placeholder="学期" size="small" style="width: 96px" @change="loadData">
        <el-option value="秋" label="秋季学期" />
        <el-option value="春" label="春季学期" />
      </el-select>
    </div>

    <div v-loading="loading" class="schedule-list">
      <div v-if="schedules.length === 0 && !loading" class="empty-state">
        <el-empty description="暂无课表数据" />
      </div>

      <div v-for="item in schedules" :key="item.id" class="schedule-card">
        <div class="card-header">
          <span class="day-label">{{ dayLabel(item.dayOfWeek) }}</span>
          <el-tag size="small">{{ item.period }}</el-tag>
        </div>
        <div class="card-body">
          <div class="info-row">
            <span class="info-label">教学班</span>
            <span class="info-value">{{ item.classId }}</span>
          </div>
          <div class="info-row">
            <span class="info-label">地点</span>
            <span class="info-value">{{ item.classLocation || '-' }}</span>
          </div>
          <div class="info-row">
            <span class="info-label">周次</span>
            <span class="info-value">{{ item.weekRange || '-' }}</span>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { getMySchedule } from '@/api/teacher'

const loading = ref(false)
const schedules = ref([])

const now = new Date()
const currentMonth = now.getMonth() + 1
const currentYear = now.getFullYear()
const defaultSemester = (currentMonth >= 2 && currentMonth <= 7) ? '春' : '秋'
// 2025-2026学年第一学期=2025秋(xn=2025), 第二学期=2026春(xn=2026)
// 1月仍属上一学年秋季学期
const academicYear = ref(currentMonth === 1 ? String(currentYear - 1) : String(currentYear))
const semester = ref(defaultSemester)

const academicYears = computed(() => {
  const years = []
  for (let y = currentYear - 4; y <= currentYear; y++) {
    years.push(String(y))
  }
  return years
})

const DAY_LABELS = ['', '周一', '周二', '周三', '周四', '周五', '周六', '周日']
function dayLabel(day) {
  return DAY_LABELS[day] || `周${day}`
}

async function loadData() {
  loading.value = true
  try {
    const res = await getMySchedule({
      academicYear: academicYear.value,
      semester: semester.value
    })
    const data = res?.data || []
    // 按星期、节次排序
    schedules.value = data.sort((a, b) => {
      if (a.dayOfWeek !== b.dayOfWeek) return (a.dayOfWeek || 0) - (b.dayOfWeek || 0)
      return (a.period || '').localeCompare(b.period || '')
    })
  } catch (e) {
    console.error('获取课表失败:', e)
    schedules.value = []
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  loadData()
})
</script>

<style scoped>
.teacher-my-schedule {
  padding: 12px;
}

.filter-bar {
  display: flex;
  gap: 8px;
  margin-bottom: 12px;
}

.empty-state {
  padding: 40px 0;
}

.schedule-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.schedule-card {
  background: var(--bg-primary);
  border-radius: 10px;
  padding: 14px;
  border: 1px solid var(--border-light);
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

.day-label {
  font-size: 15px;
  font-weight: 600;
  color: var(--text-primary);
}

.card-body {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.info-row {
  display: flex;
  justify-content: space-between;
  font-size: 13px;
}

.info-label {
  color: var(--text-tertiary);
}

.info-value {
  color: var(--text-secondary);
}
</style>
