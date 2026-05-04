<template>
  <div class="teacher-course-selections">
    <div class="filter-bar">
      <el-select v-model="academicYear" placeholder="年份" size="small" style="width: 130px" @change="loadData">
        <el-option v-for="y in academicYears" :key="y" :label="y" :value="y" />
      </el-select>
      <el-select v-model="semester" placeholder="学期" size="small" style="width: 96px" @change="loadData">
        <el-option value="秋" label="秋季学期" />
        <el-option value="春" label="春季学期" />
      </el-select>
    </div>

    <div v-loading="loading" class="selection-list">
      <div v-if="selections.length === 0 && !loading" class="empty-state">
        <el-empty description="暂无选课记录" />
      </div>

      <div v-for="item in selections" :key="item.id" class="selection-card">
        <div class="card-header">
          <span class="student-name">{{ item.studentName || item.studentId }}</span>
          <el-tag size="small" type="info">{{ item.studentId }}</el-tag>
        </div>
        <div class="card-body">
          <div class="info-row">
            <span class="info-label">课程</span>
            <span class="info-value">{{ item.courseName || item.courseCode }}</span>
          </div>
          <div class="info-row">
            <span class="info-label">教学班</span>
            <span class="info-value">{{ item.classId || '-' }}</span>
          </div>
          <div class="info-row">
            <span class="info-label">学分</span>
            <span class="info-value">{{ item.credits ?? '-' }}</span>
          </div>
          <div class="info-row">
            <span class="info-label">年份学期</span>
            <span class="info-value">{{ item.academicYear || '-' }} {{ item.semester === 1 ? '秋季' : item.semester === 2 ? '春季' : (item.semester ?? '-') }}学期</span>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { getMyCourseSelections } from '@/api/teacher'

const loading = ref(false)
const selections = ref([])

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

async function loadData() {
  loading.value = true
  try {
    const res = await getMyCourseSelections({
      academicYear: academicYear.value,
      semester: semester.value
    })
    selections.value = res?.data || []
  } catch (e) {
    console.error('获取选课记录失败:', e)
    selections.value = []
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  loadData()
})
</script>

<style scoped>
.teacher-course-selections {
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

.selection-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.selection-card {
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

.student-name {
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
