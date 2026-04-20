<template>
  <div class="school-data-browser">
    <!-- 数据概览卡片 -->
    <div class="overview-cards" v-if="overview">
      <div v-for="(count, key) in overview" :key="key" class="overview-card" @click="activeTab = key">
        <div class="card-count">{{ count }}</div>
        <div class="card-label">{{ dataSourceLabels[key] || key }}</div>
      </div>
    </div>

    <!-- 数据源Tab -->
    <el-tabs v-model="activeTab" @tab-change="handleTabChange">
      <el-tab-pane label="行政班" name="administrative_class">
        <el-table v-loading="loading" :data="tableData" stripe size="small" max-height="500">
          <el-table-column prop="classCode" label="班级编码" width="120" />
          <el-table-column prop="className" label="班级名称" min-width="150" />
          <el-table-column prop="collegeName" label="学院" min-width="120" />
          <el-table-column prop="majorName" label="专业" min-width="120" />
          <el-table-column prop="grade" label="年级" width="80" />
          <el-table-column prop="studentCount" label="人数" width="70" />
          <el-table-column prop="classStatus" label="状态" width="80" />
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="课程" name="school_course">
        <el-table v-loading="loading" :data="tableData" stripe size="small" max-height="500">
          <el-table-column prop="courseCode" label="课程编码" width="120" />
          <el-table-column prop="courseName" label="课程名称" min-width="180" />
          <el-table-column prop="courseNature" label="性质" width="80" />
          <el-table-column prop="courseCategory" label="类别" width="80" />
          <el-table-column prop="credits" label="学分" width="70" />
          <el-table-column prop="totalHours" label="学时" width="70" />
          <el-table-column prop="collegeName" label="学院" min-width="120" />
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="教学班 / 教师授课" name="teaching_class">
        <div class="tab-note">
          <el-icon><InfoFilled /></el-icon>
          <span>官方 9 个数据源中「教师授课」(/api/jssk) 的字段并入教学班表存储，在此 Tab 统一展示。</span>
        </div>
        <el-table v-loading="loading" :data="tableData" stripe size="small" max-height="500">
          <el-table-column prop="classId" label="教学班ID" width="120" />
          <el-table-column prop="courseName" label="课程" min-width="180" />
          <el-table-column prop="teacherName" label="教师" width="100" />
          <el-table-column prop="academicYear" label="学年" width="110" />
          <el-table-column prop="semester" label="学期" width="70" />
          <el-table-column prop="enrolledCount" label="选课人数" width="90" />
          <el-table-column prop="classStatus" label="状态" width="80" />
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="课程安排" name="course_schedule">
        <el-table v-loading="loading" :data="tableData" stripe size="small" max-height="500">
          <el-table-column prop="classId" label="教学班ID" width="120" />
          <el-table-column prop="dayOfWeek" label="星期" width="70" />
          <el-table-column prop="period" label="节次" width="80" />
          <el-table-column prop="weekRange" label="周次" width="100" />
          <el-table-column prop="classLocation" label="教室" min-width="120" />
          <el-table-column prop="academicYear" label="学年" width="110" />
          <el-table-column prop="semester" label="学期" width="70" />
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="选课记录" name="course_selection">
        <el-table v-loading="loading" :data="tableData" stripe size="small" max-height="500">
          <el-table-column prop="studentId" label="学号" width="130" />
          <el-table-column prop="studentName" label="姓名" width="90" />
          <el-table-column prop="courseName" label="课程" min-width="180" />
          <el-table-column prop="teacherName" label="教师" width="90" />
          <el-table-column prop="credits" label="学分" width="70" />
          <el-table-column prop="totalGrade" label="总成绩" width="80" />
          <el-table-column prop="selectionStatus" label="状态" width="80" />
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="等级考试" name="level_exam_scores">
        <el-table v-loading="loading" :data="tableData" stripe size="small" max-height="500">
          <el-table-column prop="studentId" label="学号" width="130" />
          <el-table-column prop="studentName" label="姓名" width="90" />
          <el-table-column prop="examType" label="类型" width="80" />
          <el-table-column prop="score" label="分数" width="70" />
          <el-table-column prop="isPassed" label="是否通过" width="80">
            <template #default="{ row }">
              <el-tag :type="row.isPassed ? 'success' : 'danger'" size="small">
                {{ row.isPassed ? '通过' : '未通过' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="examDate" label="考试日期" width="110" />
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="教学计划" name="teaching_plans">
        <el-table v-loading="loading" :data="tableData" stripe size="small" max-height="500">
          <el-table-column prop="planId" label="计划编号" width="120" />
          <el-table-column prop="planName" label="计划名称" min-width="200" />
          <el-table-column prop="majorName" label="专业" width="120" />
          <el-table-column prop="grade" label="年级" width="80" />
          <el-table-column prop="totalCredits" label="总学分" width="80" />
          <el-table-column prop="planStatus" label="状态" width="80" />
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="学生课表" name="student_schedule">
        <el-table v-loading="loading" :data="tableData" stripe size="small" max-height="500">
          <el-table-column prop="studentId" label="学号" width="130" />
          <el-table-column prop="studentName" label="姓名" width="90" />
          <el-table-column prop="courseName" label="课程" min-width="180" />
          <el-table-column prop="teacherName" label="教师" width="90" />
          <el-table-column prop="dayOfWeek" label="星期" width="70" />
          <el-table-column prop="period" label="节次" width="80" />
          <el-table-column prop="classLocation" label="教室" width="120" />
        </el-table>
      </el-tab-pane>
    </el-tabs>

    <!-- 分页 -->
    <div class="pagination-wrapper" v-if="total > 0">
      <el-pagination
        v-model:current-page="currentPage"
        v-model:page-size="pageSize"
        :total="total"
        :page-sizes="[20, 50, 100]"
        layout="total, sizes, prev, pager, next"
        size="small"
        @current-change="loadData"
        @size-change="loadData"
      />
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { InfoFilled } from '@element-plus/icons-vue'
import {
  getSchoolDataOverview,
  getAdministrativeClasses,
  getSchoolCourses,
  getTeachingClasses,
  getCourseSchedules,
  getCourseSelections,
  getLevelExamScores,
  getTeachingPlans,
  getStudentSchedules
} from '@/api/schoolData'

const dataSourceLabels = {
  administrative_class: '行政班',
  school_course: '课程',
  teaching_class: '教学班 / 教师授课',
  course_schedule: '课程安排',
  course_selection: '选课记录',
  level_exam_scores: '等级考试',
  teaching_plans: '教学计划',
  student_schedule: '学生课表'
}

const activeTab = ref('administrative_class')
const loading = ref(false)
const tableData = ref([])
const total = ref(0)
const currentPage = ref(1)
const pageSize = ref(20)
const overview = ref(null)

const apiMap = {
  administrative_class: getAdministrativeClasses,
  school_course: getSchoolCourses,
  teaching_class: getTeachingClasses,
  course_schedule: getCourseSchedules,
  course_selection: getCourseSelections,
  level_exam_scores: getLevelExamScores,
  teaching_plans: getTeachingPlans,
  student_schedule: getStudentSchedules
}

async function loadOverview() {
  try {
    const res = await getSchoolDataOverview()
    overview.value = res?.data || null
  } catch (e) {
    console.error('获取数据概览失败:', e)
  }
}

async function loadData() {
  loading.value = true
  try {
    const apiFn = apiMap[activeTab.value]
    if (!apiFn) return
    const res = await apiFn({ page: currentPage.value, size: pageSize.value })
    const pageData = res?.data
    tableData.value = pageData?.records || []
    total.value = pageData?.total || 0
  } catch (e) {
    console.error('获取数据失败:', e)
    tableData.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function handleTabChange() {
  currentPage.value = 1
  loadData()
}

onMounted(() => {
  loadOverview()
  loadData()
})
</script>

<style scoped>
.school-data-browser {
  padding: 12px;
}

.overview-cards {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 16px;
}

.overview-card {
  background: var(--bg-primary);
  border: 1px solid var(--border-light);
  border-radius: 8px;
  padding: 12px 16px;
  min-width: 100px;
  cursor: pointer;
  transition: all 0.2s;
  text-align: center;
}

.overview-card:hover {
  border-color: var(--primary-color);
  box-shadow: 0 2px 8px rgba(0, 102, 204, 0.1);
}

.card-count {
  font-size: 24px;
  font-weight: 700;
  color: var(--primary-color);
}

.card-label {
  font-size: 12px;
  color: var(--text-tertiary);
  margin-top: 4px;
}

.pagination-wrapper {
  display: flex;
  justify-content: flex-end;
  margin-top: 12px;
}

.tab-note {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: var(--text-tertiary, #888);
  background: var(--bg-secondary, #f7f8fa);
  border-left: 3px solid var(--primary-color, #0066cc);
  padding: 8px 12px;
  margin-bottom: 8px;
  border-radius: 4px;
}
</style>
