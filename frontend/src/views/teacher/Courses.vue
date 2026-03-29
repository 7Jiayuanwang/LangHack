<template>
  <div class="courses-preview">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>课程预览</span>
          <el-button type="primary" @click="$router.push('/teacher/classes')">管理班级</el-button>
        </div>
      </template>

      <!-- 课程统计 -->
      <el-row :gutter="20" class="mb-20">
        <el-col :xs="12" :sm="12" :md="6">
          <el-card class="stat-card">
            <div class="stat-content">
              <div class="stat-icon total">
                <el-icon><Reading /></el-icon>
              </div>
              <div class="stat-info">
                <h3>{{ stats.totalCourses }}</h3>
                <p>总课程数</p>
              </div>
            </div>
          </el-card>
        </el-col>
        <el-col :xs="12" :sm="12" :md="6">
          <el-card class="stat-card">
            <div class="stat-content">
              <div class="stat-icon active">
                <el-icon><School /></el-icon>
              </div>
              <div class="stat-info">
                <h3>{{ stats.activeClasses }}</h3>
                <p>活跃班级</p>
              </div>
            </div>
          </el-card>
        </el-col>
        <el-col :xs="12" :sm="12" :md="6">
          <el-card class="stat-card">
            <div class="stat-content">
              <div class="stat-icon students">
                <el-icon><User /></el-icon>
              </div>
              <div class="stat-info">
                <h3>{{ stats.totalStudents }}</h3>
                <p>学生总数</p>
              </div>
            </div>
          </el-card>
        </el-col>
        <el-col :xs="12" :sm="12" :md="6">
          <el-card class="stat-card">
            <div class="stat-content">
              <div class="stat-icon assignments">
                <el-icon><Edit /></el-icon>
              </div>
              <div class="stat-info">
                <h3>{{ stats.totalAssignments }}</h3>
                <p>布置任务</p>
              </div>
            </div>
          </el-card>
        </el-col>
      </el-row>

      <!-- 课程列表 -->
      <div class="courses-section">
        <h3>我的课程</h3>
        <el-row :gutter="20">
          <el-col
            v-for="course in courses"
            :key="course.id"
            :xs="24"
            :sm="12"
            :md="8"
            class="mb-20"
          >
            <el-card class="course-card" shadow="hover">
              <div class="course-header">
                <h4>{{ course.name }}</h4>
                <el-tag :type="getStatusType(course.status)">{{ getStatusText(course.status) }}</el-tag>
              </div>
              <div class="course-description">
                <p>{{ course.description || '暂无描述' }}</p>
              </div>
              <div class="course-stats">
                <div class="stat-item">
                  <span class="stat-label">班级数量：</span>
                  <span class="stat-value">{{ getClassCountByCourse(course.id) }}</span>
                </div>
                <div class="stat-item">
                  <span class="stat-label">学生人数：</span>
                  <span class="stat-value">{{ getStudentCountByCourse(course.id) }}</span>
                </div>
                <div class="stat-item">
                  <span class="stat-label">单元数量：</span>
                  <span class="stat-value">{{ getUnitCountByCourse(course.id) }}</span>
                </div>
              </div>
              <div class="course-actions">
                <el-button size="small" @click="viewCourseDetail(course)">查看详情</el-button>
                <el-button size="small" type="primary" @click="manageClasses(course)">管理班级</el-button>
                <el-button size="small" type="success" @click="viewUnitsAndAssignments(course)">布置任务</el-button>
              </div>
            </el-card>
          </el-col>
        </el-row>
      </div>

      <!-- 空状态 -->
      <el-empty v-if="courses.length === 0" description="暂无课程数据">
        <el-button type="primary" @click="loadCourses">刷新数据</el-button>
      </el-empty>
    </el-card>

    <!-- 课程详情对话框 -->
    <el-dialog
      v-model="courseDetailVisible"
      :title="currentCourse?.name || '课程详情'"
      width="min(800px, 90%)"
    >
      <div class="course-detail" v-if="currentCourse">
        <div class="detail-section">
          <h4>课程信息</h4>
          <el-descriptions :column="2" border>
            <el-descriptions-item label="课程名称">{{ currentCourse.name }}</el-descriptions-item>
            <el-descriptions-item label="课程状态">
              <el-tag :type="getStatusType(currentCourse.status)">{{ getStatusText(currentCourse.status) }}</el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="班级数量">{{ getClassCountByCourse(currentCourse.id) }}</el-descriptions-item>
            <el-descriptions-item label="学生总数">{{ getStudentCountByCourse(currentCourse.id) }}</el-descriptions-item>
          </el-descriptions>
        </div>

        <div class="detail-section">
          <h4>课程描述</h4>
          <p>{{ currentCourse.description || '暂无描述' }}</p>
        </div>

        <div class="detail-section">
          <h4>关联班级</h4>
          <div class="table-wrapper">
          <el-table :data="getClassesByCourse(currentCourse.id)" stripe>
            <el-table-column prop="name" label="班级名称" />
            <el-table-column prop="studentCount" label="学生人数" width="100">
              <template #default="scope">
                <el-tag type="info">{{ scope.row.studentCount }}人</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="createdTime" label="创建时间" width="180">
              <template #default="scope">
                {{ formatTime(scope.row.createdTime) }}
              </template>
            </el-table-column>
          </el-table>
          </div>
        </div>
      </div>
    </el-dialog>

    <!-- 单元和任务管理对话框 -->
    <el-dialog
      v-model="unitsAssignmentsVisible"
      :title="(currentCourse?.name ?? '课程') + ' - 单元与任务'"
      width="min(1000px, 90%)"
    >
      <el-tabs v-model="unitsTab">
        <el-tab-pane label="单元列表" name="units">
          <div class="table-wrapper">
          <el-table :data="courseUnits" stripe v-loading="unitsLoading">
            <el-table-column prop="name" label="单元名称" />
            <el-table-column prop="description" label="描述" />
            <el-table-column label="操作" width="200">
              <template #default="scope">
                <el-button size="small" type="primary" @click="createAssignmentForUnit(scope.row)">布置任务</el-button>
              </template>
            </el-table-column>
          </el-table>
          </div>
        </el-tab-pane>
        <el-tab-pane label="已布置任务" name="assignments">
          <div class="table-wrapper">
          <el-table :data="courseAssignments" stripe v-loading="assignmentsLoading">
            <el-table-column prop="title" label="任务标题" />
            <el-table-column prop="className" label="班级" />
            <el-table-column prop="unitName" label="单元" />
            <el-table-column prop="deadline" label="截止时间" width="180">
              <template #default="scope">
                {{ formatTime(scope.row.deadline) }}
              </template>
            </el-table-column>
            <el-table-column label="操作" width="150">
              <template #default="scope">
                <el-button size="small" @click="editAssignment(scope.row)">编辑</el-button>
                <el-button size="small" type="danger" @click="deleteAssignment(scope.row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
          </div>
        </el-tab-pane>
      </el-tabs>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getAuthHeader } from '@/utils/auth'

const router = useRouter()

// 响应式数据
const courses = ref([])
const classes = ref([])
const courseDetailVisible = ref(false)
const currentCourse = ref(null)

const unitCountCache = ref(new Map())

// 单元和任务管理
const unitsAssignmentsVisible = ref(false)
const unitsTab = ref('units')
const courseUnits = ref([])
const courseAssignments = ref([])
const unitsLoading = ref(false)
const assignmentsLoading = ref(false)

// 统计数据
const stats = reactive({
  totalCourses: 0,
  activeClasses: 0,
  totalStudents: 0,
  totalAssignments: 0
})

// 公共方法：获取认证头
const getAuthHeaders = () => {
  const headers = {}
  const auth = getAuthHeader()
  if (auth) {
    headers['Authorization'] = auth
  }
  return headers
}

// 方法
const loadCourses = async () => {
  try {
    // 首先尝试加载活跃课程
    const headers = getAuthHeaders()

    let response = await fetch('/api/courses/active', { headers })

    if (response.ok) {
      try {
        const data = await response.json()

        if (data.code === 200) {
          courses.value = Array.isArray(data.data) ? data.data : []
          stats.totalCourses = courses.value.length
          if (courses.value.length === 0) {
            // 如果活跃课程为空，尝试加载所有课程
            await loadAllCourses()
          }
        } else {
          console.error('活跃课程API返回错误:', data)
          await loadAllCourses() // 回退到加载所有课程
        }
      } catch (parseError) {
        console.error('活跃课程解析失败:', parseError)
        await loadAllCourses() // 回退到加载所有课程
      }
    } else {
      console.error('活跃课程HTTP请求失败:', response.status)
      await loadAllCourses() // 回退到加载所有课程
    }
  } catch (error) {
    console.error('加载课程列表异常:', error)
    ElMessage.error('加载课程列表失败')
  }
}

const loadAllCourses = async () => {
  try {
    const headers = getAuthHeaders()

    const response = await fetch('/api/courses?size=100', { headers })

    if (response.ok) {
      try {
        const data = await response.json()

        if (data.code === 200) {
          courses.value = data.data?.records || []
          stats.totalCourses = courses.value.length

          if (courses.value.length === 0) {
            ElMessage.warning('暂无课程数据，请先创建课程')
          } else {
            ElMessage.success(`成功加载 ${courses.value.length} 个课程`)
          }
        } else {
          console.error('所有课程API返回错误:', data)
          ElMessage.error(data.message || '加载课程列表失败')
        }
      } catch (parseError) {
        console.error('所有课程解析失败:', parseError)
        ElMessage.error('加载课程列表失败')
      }
    } else {
      console.error('所有课程HTTP请求失败:', response.status, response.statusText)
      ElMessage.error(`加载课程列表失败: ${response.status}`)
    }
  } catch (error) {
    console.error('加载所有课程异常:', error)
    ElMessage.error('加载课程列表失败')
  }
}

const loadClasses = async () => {
  try {
    const headers = getAuthHeaders()

    const response = await fetch('/api/classes?size=100', { headers })

    if (response.ok) {
      try {
        const data = await response.json()

        if (data.code === 200) {
          classes.value = data.data?.records || []
          stats.activeClasses = classes.value.length

          // 计算学生总数
          stats.totalStudents = classes.value.reduce((total, cls) => {
            return total + (cls.studentCount || 0)
          }, 0)
        } else {
          console.error('班级API返回错误:', data)
        }
      } catch (parseError) {
        console.error('班级列表解析失败:', parseError)
      }
    } else {
      console.error('班级HTTP请求失败:', response.status)
    }
  } catch (error) {
    console.error('加载班级列表失败:', error)
  }
}

const loadStats = async () => {
  try {
    // 加载任务统计
    const headers = getAuthHeaders()

    const response = await fetch('/api/assignments/teacher/stats', { headers })

    if (response.ok) {
      try {
        const data = await response.json()
        if (data.code === 200) {
          stats.totalAssignments = data.data?.total_assignments || 0
        }
      } catch (parseError) {
        console.error('统计数据解析失败:', parseError)
      }
    }
  } catch (error) {
    console.error('加载统计数据失败:', error)
  }
}


const viewCourseDetail = (course) => {
  currentCourse.value = course
  courseDetailVisible.value = true
}

const manageClasses = (course) => {
  router.push(`/teacher/classes?courseId=${course.id}`)
}

const viewUnitsAndAssignments = async (course) => {
  currentCourse.value = course
  unitsAssignmentsVisible.value = true
  unitsTab.value = 'units'
  await loadCourseUnits(course.id)
  await loadCourseAssignments(course.id)
}

const loadCourseUnits = async (courseId) => {
  unitsLoading.value = true
  try {
    const headers = getAuthHeaders()

    const response = await fetch(`/api/units/course/${courseId}`, { headers })
    if (response.ok) {
      try {
        const data = await response.json()
        if (data.code === 200) {
          courseUnits.value = Array.isArray(data.data) ? data.data : []
          unitCountCache.value.set(courseId, courseUnits.value.length)
        }
      } catch (parseError) {
        console.error('单元列表解析失败:', parseError)
      }
    }
  } catch (error) {
    console.error('加载单元列表失败:', error)
    ElMessage.error('加载单元列表失败')
  } finally {
    unitsLoading.value = false
  }
}

const loadCourseAssignments = async (courseId) => {
  assignmentsLoading.value = true
  try {
    // 获取该课程下所有班级的任务
    const courseClasses = getClassesByCourse(courseId)
    const allAssignments = []

    for (const cls of courseClasses) {
      const headers = getAuthHeaders()

      const response = await fetch(`/api/assignments?classId=${cls.id}`, { headers })
      if (response.ok) {
        try {
          const data = await response.json()
          if (data.code === 200) {
            // 兼容分页响应 {records:[]} 和直接数组两种格式
            let assignments = []
            if (data.data?.records) {
              assignments = Array.isArray(data.data.records) ? data.data.records : []
            } else if (Array.isArray(data.data)) {
              assignments = data.data
            }
            allAssignments.push(...assignments.map(a => ({
              ...a,
              className: cls.name
            })))
          }
        } catch (parseError) {
          console.error('任务列表解析失败:', parseError)
        }
      }
    }

    courseAssignments.value = allAssignments
  } catch (error) {
    console.error('加载任务列表失败:', error)
    ElMessage.error('加载任务列表失败')
  } finally {
    assignmentsLoading.value = false
  }
}

const createAssignmentForUnit = (unit) => {
  if (!currentCourse.value) return
  // 跳转到任务管理页面，并预填单元信息
  router.push({
    path: '/teacher/assignments',
    query: {
      unitId: unit.id,
      courseId: currentCourse.value.id
    }
  })
  unitsAssignmentsVisible.value = false
}

const editAssignment = (assignment) => {
  router.push({
    path: '/teacher/assignments',
    query: {
      assignmentId: assignment.id
    }
  })
  unitsAssignmentsVisible.value = false
}

const deleteAssignment = async (assignment) => {
  try {
    await ElMessageBox.confirm('确定要删除这个任务吗？', '确认删除', {
      type: 'warning'
    })

    const headers = getAuthHeaders()

    const response = await fetch(`/api/assignments/${assignment.id}`, {
      method: 'DELETE',
      headers
    })

    if (response.ok) {
      try {
        const data = await response.json()
        if (data.code === 200) {
          ElMessage.success('删除成功')
          if (currentCourse.value?.id) {
            await loadCourseAssignments(currentCourse.value.id)
          }
        } else {
          ElMessage.error(data.message || '删除失败')
        }
      } catch (parseError) {
        console.error('删除响应解析失败:', parseError)
        ElMessage.error('删除失败')
      }
    } else {
      ElMessage.error('删除失败')
    }
  } catch (error) {
    if (error !== 'cancel') {
      console.error('删除任务失败:', error)
      ElMessage.error('删除任务失败')
    }
  }
}

const getStatusType = (status) => {
  const types = {
    'ACTIVE': 'success',
    'INACTIVE': 'warning',
    'DELETED': 'danger'
  }
  return types[status] || 'info'
}

const getStatusText = (status) => {
  const texts = {
    'ACTIVE': '活跃',
    'INACTIVE': '未激活',
    'DELETED': '已删除'
  }
  return texts[status] || status
}

const getClassCountByCourse = (courseId) => {
  if (!courseId) return 0
  return classes.value.filter(cls => cls.courseId === courseId).length
}

const getStudentCountByCourse = (courseId) => {
  if (!courseId) return 0
  return classes.value
    .filter(cls => cls.courseId === courseId)
    .reduce((total, cls) => total + (cls.studentCount || 0), 0)
}

const getUnitCountByCourse = (courseId) => {
  if (!courseId) return 0
  // 优先从缓存获取，避免重复计算
  if (unitCountCache.value.has(courseId)) {
    return unitCountCache.value.get(courseId)
  }
  // 缓存未命中，返回 0（将在 loadCourseUnits 时更新）
  return 0
}

const getClassesByCourse = (courseId) => {
  if (!courseId) return []
  return classes.value.filter(cls => cls.courseId === courseId)
}

const formatTime = (time) => {
  if (!time) return ''
  return new Date(time).toLocaleString()
}

// 生命周期
onMounted(() => {
  loadCourses()
  loadClasses()
  loadStats()
})
</script>

<style scoped>
.courses-preview {
  padding: 20px;
}

.table-wrapper {
  overflow-x: auto;
  -webkit-overflow-scrolling: touch;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.mb-20 {
  margin-bottom: 20px;
}

.stat-card {
  height: 120px;
}

.stat-content {
  display: flex;
  align-items: center;
  height: 100%;
}

.stat-icon {
  width: 60px;
  height: 60px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-right: 20px;
  font-size: 24px;
  color: white;
}

.stat-icon.total {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
}

.stat-icon.active {
  background: linear-gradient(135deg, #f093fb 0%, #f5576c 100%);
}

.stat-icon.students {
  background: linear-gradient(135deg, #4facfe 0%, #00f2fe 100%);
}

.stat-icon.assignments {
  background: linear-gradient(135deg, #43e97b 0%, #38f9d7 100%);
}

.stat-info h3 {
  margin: 0;
  font-size: 24px;
  font-weight: bold;
  color: #333;
}

.stat-info p {
  margin: 5px 0 0;
  color: #666;
  font-size: 14px;
}

.courses-section h3 {
  margin: 20px 0 15px;
  color: #333;
  font-size: 18px;
}

.course-card {
  height: 280px;
  display: flex;
  flex-direction: column;
}

.course-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
}

.course-header h4 {
  margin: 0;
  font-size: 16px;
  color: #333;
  font-weight: bold;
}

.course-description {
  flex: 1;
  margin-bottom: 15px;
}

.course-description p {
  margin: 0;
  color: #666;
  font-size: 14px;
  line-height: 1.5;
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.course-stats {
  margin-bottom: 15px;
}

.stat-item {
  display: flex;
  justify-content: space-between;
  margin-bottom: 5px;
  font-size: 14px;
}

.stat-label {
  color: #666;
}

.stat-value {
  color: #333;
  font-weight: bold;
}

.course-actions {
  display: flex;
  justify-content: space-between;
}

.detail-section {
  margin-bottom: 20px;
}

.detail-section h4 {
  margin: 0 0 10px;
  color: #333;
  font-size: 16px;
}

@media (max-width: 768px) {
  .courses-preview {
    padding: 12px;
  }

  .course-card {
    height: auto;
    min-height: 200px;
  }

  .stat-card {
    height: auto;
    margin-bottom: 12px;
  }
}
</style>
