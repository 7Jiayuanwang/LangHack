<template>
  <div class="teacher-my-courses">
    <div class="filter-bar">
      <el-select v-model="academicYear" placeholder="年份" size="small" style="width: 130px" @change="loadData">
        <el-option v-for="y in academicYears" :key="y" :label="y" :value="y" />
      </el-select>
      <el-select v-model="semester" placeholder="学期" size="small" style="width: 96px" @change="loadData">
        <el-option :value="1" label="秋季学期" />
        <el-option :value="2" label="春季学期" />
      </el-select>
    </div>

    <div v-loading="loading" class="course-list">
      <div v-if="courses.length === 0 && !loading" class="empty-state">
        <el-empty description="暂无授课信息" />
      </div>

      <div v-for="item in courses" :key="item.id" class="course-card">
        <div class="card-header">
          <span class="course-name">{{ item.courseName }}</span>
          <el-tag size="small" type="info">{{ item.classId }}</el-tag>
        </div>
        <div class="card-body">
          <div class="info-row">
            <span class="info-label">教学班</span>
            <span class="info-value">{{ item.className || '-' }}</span>
          </div>
          <div class="info-row">
            <span class="info-label">选课人数</span>
            <span class="info-value">{{ item.enrolledCount || 0 }} / {{ item.capacity || '-' }}</span>
          </div>
          <div class="info-row">
            <span class="info-label">年份学期</span>
            <span class="info-value">{{ item.academicYear }} 第{{ item.semester }}学期</span>
          </div>
          <div class="info-row">
            <span class="info-label">状态</span>
            <span class="info-value">{{ item.classStatus }}</span>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { getMyTeachingClasses } from '@/api/teacher'

const loading = ref(false)
const courses = ref([])

const now = new Date()
const currentMonth = now.getMonth() + 1
const currentYear = now.getFullYear()
const defaultAcademicYear = String(currentYear)
const defaultSemester = (currentMonth >= 2 && currentMonth <= 7) ? 2 : 1

const academicYear = ref(defaultAcademicYear)
const semester = ref(defaultSemester)

const academicYears = computed(() => {
  const years = []
  for (let year = currentYear - 4; year <= currentYear; year++) {
    years.push(String(year))
  }
  return years
})

async function loadData() {
  loading.value = true
  try {
    const res = await getMyTeachingClasses({
      academicYear: academicYear.value,
      semester: semester.value
    })
    courses.value = res?.data || []
  } catch (e) {
    console.error('获取授课信息失败:', e)
    courses.value = []
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  loadData()
})
</script>

<style scoped>
.teacher-my-courses {
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

.course-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.course-card {
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

.course-name {
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
