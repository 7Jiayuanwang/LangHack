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
            <el-button v-if="linkedClassId(item)" link type="primary" @click="openClassOverview(item)">
              {{ item.className || item.classId }}
            </el-button>
            <span v-else class="info-value">{{ item.className || '-' }}</span>
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
        <div class="card-actions">
          <el-button v-if="linkedClassId(item)" type="primary" plain @click="openClassOverview(item)">查看班级学情</el-button>
          <template v-else-if="!platformClassesLoading">
            <span class="sync-hint">{{ platformClassesError ? '暂时无法检查平台班级关联' : '尚未同步到平台班级' }}</span>
            <el-button @click="router.push('/teacher/classes')">前往班级管理</el-button>
          </template>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { getMyTeachingClasses, getMyPlatformClasses } from '@/api/teacher'

const router = useRouter()
const loading = ref(false)
const courses = ref([])
const platformClasses = ref([])
const platformClassesLoading = ref(false)
const platformClassesError = ref(false)

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

const linkedClassId = (course) => {
  if (!course.classId) return null
  return platformClasses.value.find(cls => cls.schoolClassId && String(cls.schoolClassId) === String(course.classId))?.id || null
}

const openClassOverview = (course) => {
  const classId = linkedClassId(course)
  if (classId) router.push(`/teacher/classes/${classId}`)
}

async function loadPlatformClasses() {
  platformClassesLoading.value = true
  platformClassesError.value = false
  try {
    const found = []
    let page = 1
    let pages = 1
    do {
      const res = await getMyPlatformClasses(page)
      found.push(...(res?.data?.records || []))
      const reportedPages = Number(res?.data?.pages) || 1
      if (reportedPages > 20) platformClassesError.value = true
      pages = Math.min(reportedPages, 20)
      page += 1
    } while (page <= pages)
    platformClasses.value = found
  } catch (e) {
    platformClasses.value = []
    platformClassesError.value = true
  } finally {
    platformClassesLoading.value = false
  }
}

onMounted(() => {
  loadData()
  loadPlatformClasses()
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

.card-actions { display: flex; align-items: center; justify-content: flex-end; gap: 10px; margin-top: 12px; padding-top: 10px; border-top: 1px solid var(--xy-border); }
.sync-hint { color: var(--xy-ink-muted); font-size: 13px; }
</style>
