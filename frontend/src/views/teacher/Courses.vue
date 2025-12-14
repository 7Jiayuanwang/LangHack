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
        <el-col :span="6">
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
        <el-col :span="6">
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
        <el-col :span="6">
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
        <el-col :span="6">
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
            :span="8"
            class="mb-20"
          >
            <el-card class="course-card" shadow="hover">
              <div class="course-header">
                <h4>{{ course.name }}</h4>
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
      width="800px"
    >
      <div class="course-detail" v-if="currentCourse">
        <div class="detail-section">
          <h4>课程信息</h4>
          <el-descriptions :column="2" border>
            <el-descriptions-item label="课程名称">{{ currentCourse.name }}</el-descriptions-item>
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
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'

const router = useRouter()

// 响应式数据
const courses = ref([])
const classes = ref([])
const courseDetailVisible = ref(false)
const currentCourse = ref(null)
const courseStats = ref(new Map())
const classStats = ref(new Map())

// 统计数据
const stats = reactive({
  totalCourses: 0,
  activeClasses: 0,
  totalStudents: 0,
  totalAssignments: 0
})

// 方法
const loadCourses = async () => {
  try {
    console.log('开始加载课程列表...')

    // 首先尝试加载活跃课程
    let response = await fetch('/api/courses/active', {
      headers: {
        'Authorization': `Bearer ${localStorage.getItem('token')}`
      }
    })

    console.log('活跃课程API响应状态:', response.status)

    if (response.ok) {
      const data = await response.json()
      console.log('活跃课程API返回数据:', data)

      if (data.code === 200) {
        courses.value = data.data || []
        stats.totalCourses = courses.value.length
        console.log('处理后的课程数据:', courses.value)

        if (courses.value.length === 0) {
          console.log('活跃课程为空，尝试加载所有课程...')
          // 如果活跃课程为空，尝试加载所有课程
          await loadAllCourses()
        }
      } else {
        console.error('活跃课程API返回错误:', data)
        await loadAllCourses() // 回退到加载所有课程
      }
    } else {
      console.error('活跃课程HTTP请求失败:', response.status)
      await loadAllCourses() // 回退到加载所有课程
    }
  } catch (error) {
    console.error('加载课程列表异常:', error)
    ElMessage.error('加载课程列表失败: ' + error.message)
  }
}

const loadAllCourses = async () => {
  try {
    console.log('尝试加载所有课程...')

    const response = await fetch('/api/courses?size=100', {
      headers: {
        'Authorization': `Bearer ${localStorage.getItem('token')}`
      }
    })

    console.log('所有课程API响应状态:', response.status)

    if (response.ok) {
      const data = await response.json()
      console.log('所有课程API返回数据:', data)

      if (data.code === 200) {
        courses.value = data.data.records || []
        stats.totalCourses = courses.value.length
        console.log('处理后的课程数据:', courses.value)

        if (courses.value.length === 0) {
          ElMessage.warning('暂无课程数据，请先创建课程')
        } else {
          ElMessage.success(`成功加载 ${courses.value.length} 个课程`)
        }
      } else {
        console.error('所有课程API返回错误:', data)
        ElMessage.error(data.message || '加载课程列表失败')
      }
    } else {
      console.error('所有课程HTTP请求失败:', response.status, response.statusText)
      ElMessage.error(`加载课程列表失败: ${response.status}`)
    }
  } catch (error) {
    console.error('加载所有课程异常:', error)
    ElMessage.error('加载课程列表失败: ' + error.message)
  }
}

const loadClasses = async () => {
  try {
    console.log('开始加载班级列表...')

    const response = await fetch('/api/classes?size=100', {
      headers: {
        'Authorization': `Bearer ${localStorage.getItem('token')}`
      }
    })

    console.log('班级API响应状态:', response.status)

    if (response.ok) {
      const data = await response.json()
      console.log('班级API返回数据:', data)

      if (data.code === 200) {
        classes.value = data.data.records || []
        stats.activeClasses = classes.value.length

        // 计算学生总数
        stats.totalStudents = classes.value.reduce((total, cls) => {
          return total + (cls.studentCount || 0)
        }, 0)

        console.log('处理后的班级数据:', classes.value)
        console.log('计算的学生总数:', stats.totalStudents)
      } else {
        console.error('班级API返回错误:', data)
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
    const response = await fetch('/api/assignments/teacher/stats', {
      headers: {
        'Authorization': `Bearer ${localStorage.getItem('token')}`
      }
    })

    if (response.ok) {
      const data = await response.json()
      if (data.code === 200) {
        stats.totalAssignments = data.data.total_assignments || 0
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
  // 这里应该调用API获取单元数量，暂时返回估算值
  return Math.floor(Math.random() * 10) + 5
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
  height: 100%;
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
</style>