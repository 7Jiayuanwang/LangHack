<template>
  <div class="teacher-my-students">
    <div class="filter-bar">
      <el-select v-model="academicYear" placeholder="年份" size="small" style="width: 130px" @change="loadData">
        <el-option v-for="y in academicYears" :key="y" :label="y" :value="y" />
      </el-select>
      <el-select v-model="semester" placeholder="学期" size="small" style="width: 96px" @change="loadData">
        <el-option value="秋" label="秋季学期" />
        <el-option value="春" label="春季学期" />
      </el-select>
    </div>

    <div v-loading="loading" class="student-list">
      <div v-if="students.length === 0 && !loading" class="empty-state">
        <el-empty description="暂无学生数据，请先通过运维面板同步学校数据" />
      </div>

      <div v-for="item in students" :key="item.studentId" class="student-card">
        <div class="card-header">
          <span class="student-name">{{ item.studentName }}</span>
          <div class="card-actions">
            <el-button size="small" type="primary" plain @click="openProfile(item.studentId)">画像</el-button>
            <el-tag size="small" type="info">{{ item.studentId }}</el-tag>
          </div>
        </div>
        <div class="card-body">
          <div class="info-row">
            <span class="info-label">班级</span>
            <span class="info-value">{{ item.className || '-' }}</span>
          </div>
          <div class="info-row">
            <span class="info-label">专业</span>
            <span class="info-value">{{ item.majorName || '-' }}</span>
          </div>
          <div class="info-row">
            <span class="info-label">年级</span>
            <span class="info-value">{{ item.grade || '-' }}</span>
          </div>
          <div class="info-row">
            <span class="info-label">学院</span>
            <span class="info-value">{{ item.collegeName || '-' }}</span>
          </div>
        </div>
      </div>
    </div>

    <!-- 画像抽屉 -->
    <el-drawer v-model="profileDrawerVisible" title="学习者画像" size="420px">
      <LearnerProfileCard :profile="currentProfile" :loading="profileLoading" :error="profileError" />
    </el-drawer>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { getMyStudents } from '@/api/teacher'
import { getTeacherStudentProfile } from '@/api/learnerProfile'
import LearnerProfileCard from '@/components/learner/LearnerProfileCard.vue'

const loading = ref(false)
const students = ref([])
const profileDrawerVisible = ref(false)
const currentProfile = ref(null)
const profileLoading = ref(false)
const profileError = ref(false)

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
    const res = await getMyStudents({
      academicYear: academicYear.value,
      semester: semester.value
    })
    students.value = res?.data || []
  } catch (e) {
    console.error('获取学生列表失败:', e)
    students.value = []
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  loadData()
})

async function openProfile(studentId) {
  profileDrawerVisible.value = true
  profileLoading.value = true
  profileError.value = false
  currentProfile.value = null
  try {
    const res = await getTeacherStudentProfile(studentId)
    currentProfile.value = res?.data || null
  } catch (e) {
    console.error('获取画像失败:', e)
    profileError.value = true
  } finally {
    profileLoading.value = false
  }
}
</script>

<style scoped>
.teacher-my-students {
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

.student-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.student-card {
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

.card-actions {
  display: flex;
  align-items: center;
  gap: 8px;
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
