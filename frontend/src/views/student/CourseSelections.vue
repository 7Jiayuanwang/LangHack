<template>
  <div class="student-course-selections">
    <!-- 学期选择 -->
    <div class="filter-bar">
      <el-select v-model="academicYear" placeholder="年份" size="small" style="width: 130px" @change="loadData">
        <el-option v-for="y in academicYears" :key="y" :label="y" :value="y" />
      </el-select>
      <el-select v-model="semester" placeholder="学期" size="small" style="width: 96px" @change="loadData">
        <el-option value="秋" label="秋季学期" />
        <el-option value="春" label="春季学期" />
      </el-select>
      <el-button size="small" :loading="refreshing" @click="handleRefresh">刷新</el-button>
    </div>

    <!-- 选课列表 -->
    <div v-loading="loading" class="selection-list">
      <div v-if="selections.length === 0 && !loading" class="empty-state">
        <el-empty description="暂无选课记录" />
      </div>

      <div v-for="item in selections" :key="item.id" class="selection-card">
        <div class="card-header">
          <span class="course-name">{{ item.courseName || item.courseCode || '-' }}</span>
          <span class="credits">{{ item.credits }}学分</span>
        </div>
        <div class="card-body">
          <div class="info-row">
            <span class="info-label">教师</span>
            <span class="info-value">{{ item.teacherName }}</span>
          </div>
          <div class="info-row">
            <span class="info-label">时间</span>
            <span class="info-value">{{ item.classTime || '-' }}</span>
          </div>
          <div class="info-row">
            <span class="info-label">教室</span>
            <span class="info-value">{{ item.classLocation || '-' }}</span>
          </div>
        </div>
        <div class="card-footer">
          <span class="status-tag" :class="{ passed: item.totalGrade != null && item.totalGrade >= 60 }">
            {{ item.totalGrade != null ? item.totalGrade + '分' : '未出分' }}
          </span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getMyCourseSelections, refreshMyCourseSelections } from '@/api/student'

const loading = ref(false)
const refreshing = ref(false)
const selections = ref([])

const now = new Date()
const currentMonth = now.getMonth() + 1
const currentYear = now.getFullYear()
// CDSP 学期字段: "秋"(8月-次年1月) 或 "春"(2月-7月)
const defaultSemester = (currentMonth >= 2 && currentMonth <= 7) ? '春' : '秋'
// CDSP 学年字段: 该学期所在的日历年份
// 2025-2026学年第一学期=2025秋(xn=2025), 第二学期=2026春(xn=2026)
// 1月仍属上一学年秋季学期，故 xn = currentYear - 1
const defaultAcademicYear = currentMonth === 1 ? String(currentYear - 1) : String(currentYear)

const academicYear = ref(defaultAcademicYear)
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

async function handleRefresh() {
  refreshing.value = true
  try {
    const res = await refreshMyCourseSelections({
      academicYear: academicYear.value,
      semester: semester.value
    })
    selections.value = res?.data || []
  } catch (e) {
    console.error('刷新选课记录失败:', e)
    ElMessage.error('刷新失败，请稍后重试')
  } finally {
    refreshing.value = false
  }
}

onMounted(() => {
  loadData()
})
</script>

<style scoped>
.student-course-selections {
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

.course-name {
  font-size: 15px;
  font-weight: 600;
  color: var(--text-primary);
}

.credits {
  font-size: 13px;
  color: var(--primary-color);
  font-weight: 600;
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

.card-footer {
  margin-top: 8px;
  padding-top: 6px;
  border-top: 1px solid var(--border-light);
  text-align: right;
}

.status-tag {
  font-size: 13px;
  font-weight: 600;
  color: var(--text-tertiary);
}

.status-tag.passed {
  color: var(--success-color);
}
</style>
