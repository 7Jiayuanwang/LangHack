<template>
  <div class="classes-management">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>班级管理</span>
          <el-button type="primary" @click="showCreateDialog">创建班级</el-button>
        </div>
      </template>

      <!-- 筛选栏 -->
      <div class="filter-bar">
        <el-form :inline="true" :model="filterForm">
          <el-form-item label="班级名称">
            <el-input v-model="filterForm.keyword" placeholder="请输入班级名称" clearable @keyup.enter="loadClasses" />
          </el-form-item>
          <el-form-item label="所属课程">
            <el-select v-model="filterForm.courseId" placeholder="请选择课程" clearable @change="handleSearch" style="width: 160px">
              <el-option
                v-for="course in courses"
                :key="course.id"
                :label="course.name"
                :value="course.id"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="任课教师">
            <el-select v-model="filterForm.teacherId" placeholder="请选择教师" clearable @change="handleSearch" style="width: 160px">
              <el-option
                v-for="teacher in teachers"
                :key="teacher.id"
                :label="teacher.nickname || teacher.realName || teacher.username"
                :value="teacher.id"
              />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" @click="handleSearch">查询</el-button>
            <el-button @click="resetFilter">重置</el-button>
          </el-form-item>
        </el-form>
      </div>

      <!-- 班级列表 -->
      <div class="table-wrapper">
      <el-table v-loading="loading" :data="classes" stripe>
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="name" label="班级名称" min-width="150" />
        <el-table-column label="所属课程" min-width="150">
          <template #default="{ row }">
            {{ row.course?.name || '-' }}
          </template>
        </el-table-column>
        <el-table-column label="任课教师" min-width="120">
          <template #default="{ row }">
            {{ row.teacher?.nickname || row.teacher?.realName || row.teacher?.username || '-' }}
          </template>
        </el-table-column>
        <el-table-column prop="description" label="班级描述" min-width="200" show-overflow-tooltip />
        <el-table-column prop="createdTime" label="创建时间" width="180">
          <template #default="{ row }">
            {{ formatDateTime(row.createdTime) }}
          </template>
        </el-table-column>
        <el-table-column label="学生数量" width="100">
          <template #default="{ row }">
            <el-tag size="small">{{ getStudentCount(row.id) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="190" fixed="right">
          <template #default="{ row }">
            <div class="action-buttons">
              <el-button 
                size="small" 
                type="primary" 
                :icon="View" 
                @click="showDetailDialog(row)"
                title="查看详情"
              >
                查看
              </el-button>
              <el-dropdown trigger="click" @command="(cmd) => handleDropdownCommand(cmd, row)">
                <el-button size="small" :icon="MoreFilled" circle />
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item :icon="Edit" command="edit">编辑班级</el-dropdown-item>
                    <el-dropdown-item :icon="Delete" command="delete" divided>删除班级</el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </div>
          </template>
        </el-table-column>
      </el-table>
      </div>

      <!-- 分页 -->
      <div class="pagination-wrapper">
        <el-pagination
          v-model:current-page="pagination.current"
          v-model:page-size="pagination.size"
          :page-sizes="[10, 20, 50, 100]"
          :total="pagination.total"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="loadClasses"
          @current-change="loadClasses"
        />
      </div>
    </el-card>

    <!-- 创建/编辑班级对话框 -->
    <el-dialog
      v-model="dialogVisible"
      :title="isEdit ? '编辑班级' : '创建班级'"
      width="min(600px, 90%)"
      @close="resetForm"
    >
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="100px">
        <el-form-item label="班级名称" prop="name">
          <el-input v-model="form.name" placeholder="请输入班级名称" />
        </el-form-item>
        <el-form-item label="所属课程" prop="courseId">
          <el-select v-model="form.courseId" placeholder="请选择课程" style="width: 100%">
            <el-option
              v-for="course in courses"
              :key="course.id"
              :label="course.name"
              :value="course.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="任课教师" prop="teacherId">
          <el-select v-model="form.teacherId" placeholder="请选择教师" style="width: 100%">
            <el-option
              v-for="teacher in teachers"
              :key="teacher.id"
              :label="teacher.nickname || teacher.realName || teacher.username"
              :value="teacher.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="班级描述" prop="description">
          <el-input
            v-model="form.description"
            type="textarea"
            :rows="4"
            placeholder="请输入班级描述"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitForm">
          {{ isEdit ? '更新' : '创建' }}
        </el-button>
      </template>
    </el-dialog>

    <!-- 班级详情对话框 -->
    <el-dialog v-model="detailVisible" title="班级详情" width="min(800px, 90%)">
      <div v-if="currentClass">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="班级ID">{{ currentClass.id }}</el-descriptions-item>
          <el-descriptions-item label="班级名称">{{ currentClass.name }}</el-descriptions-item>
          <el-descriptions-item label="所属课程">{{ currentClass.course?.name || '-' }}</el-descriptions-item>
          <el-descriptions-item label="任课教师">
            {{ currentClass.teacher?.nickname || currentClass.teacher?.realName || currentClass.teacher?.username || '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="创建时间">{{ formatDateTime(currentClass.createdTime) }}</el-descriptions-item>
          <el-descriptions-item label="学生数量">{{ getStudentCount(currentClass.id) }}人</el-descriptions-item>
          <el-descriptions-item label="班级描述" :span="2">{{ currentClass.description || '-' }}</el-descriptions-item>
        </el-descriptions>

        <!-- 学生列表 -->
        <div style="margin-top: 20px;">
          <h3>班级学生</h3>
          <div class="table-wrapper">
          <el-table v-loading="studentsLoading" :data="classStudents" stripe>
            <el-table-column prop="id" label="ID" width="80" />
            <el-table-column label="学号" prop="student.username" />
            <el-table-column label="姓名">
              <template #default="{ row }">
                {{ row.student?.nickname || row.student?.realName || '-' }}
              </template>
            </el-table-column>
            <el-table-column prop="student.email" label="邮箱" />
            <el-table-column prop="joinTime" label="加入时间">
              <template #default="{ row }">
                {{ formatDateTime(row.joinTime) }}
              </template>
            </el-table-column>
            <el-table-column label="操作" width="100">
              <template #default="{ row }">
                <el-button type="danger" size="small" @click="removeStudent(row)">移除</el-button>
              </template>
            </el-table-column>
          </el-table>
          </div>
        </div>

        <!-- 画像摘要 -->
        <div style="margin-top: 20px;">
          <h3>
            学习者画像摘要
            <el-button size="small" type="primary" plain @click="loadClassProfile" :loading="profileLoading" style="margin-left: 12px">
              加载画像
            </el-button>
          </h3>
          <div v-if="classProfileSummary.length > 0">
            <el-table :data="classProfileSummary" stripe size="small">
              <el-table-column prop="realName" label="姓名" width="100">
                <template #default="{ row }">{{ row.realName || row.username }}</template>
              </el-table-column>
              <el-table-column prop="riskLevel" label="风险" width="80">
                <template #default="{ row }">
                  <el-tag :type="row.riskLevel === 'high' ? 'danger' : row.riskLevel === 'medium' ? 'warning' : 'success'" size="small">
                    {{ { low: '低', medium: '中', high: '高' }[row.riskLevel] || '-' }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="activeDays7d" label="7天活跃" width="80" />
              <el-table-column label="14天时长" width="100">
                <template #default="{ row }">{{ row.studyMinutes14d }}分钟</template>
              </el-table-column>
              <el-table-column label="标签">
                <template #default="{ row }">
                  <LearnerRiskTagList v-if="row.tags" :tags="row.tags" />
                </template>
              </el-table-column>
            </el-table>
          </div>
          <div v-else-if="profileLoading" style="padding: 20px; text-align: center;">
            加载中...
          </div>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { View, MoreFilled, Edit, Delete } from '@element-plus/icons-vue'
import { getAuthHeader } from '@/utils/auth'
import { getClassLearnerProfileSummary } from '@/api/learnerProfile'
import LearnerRiskTagList from '@/components/learner/LearnerRiskTagList.vue'

// 数据状态
const loading = ref(false)
const classes = ref([])
const courses = ref([])
const teachers = ref([])
const dialogVisible = ref(false)
const detailVisible = ref(false)
const isEdit = ref(false)
const submitting = ref(false)
const studentsLoading = ref(false)
const classStudents = ref([])
const currentClass = ref(null)
const classProfileSummary = ref([])
const profileLoading = ref(false)

// 分页
const pagination = reactive({
  current: 1,
  size: 10,
  total: 0
})

// 筛选表单
const filterForm = reactive({
  keyword: '',
  courseId: null,
  teacherId: null
})

// 表单
const form = reactive({
  id: null,
  name: '',
  courseId: null,
  teacherId: null,
  description: ''
})

const formRef = ref(null)

// 表单验证规则
const formRules = {
  name: [
    { required: true, message: '请输入班级名称', trigger: 'blur' }
  ],
  courseId: [
    { required: true, message: '请选择所属课程', trigger: 'change' }
  ],
  teacherId: [
    { required: true, message: '请选择任课教师', trigger: 'change' }
  ]
}

// 学生数量缓存（使用 reactive Map 保证响应式）
const studentCountCache = ref(new Map())

// 批量加载班级学生数量
const batchLoadStudentCounts = async (classIds) => {
  for (const classId of classIds) {
    if (studentCountCache.value.has(classId)) continue
    try {
      const resp = await fetch(`/api/classes/${classId}/students`, {
        headers: {
          'Authorization': getAuthHeader() || ''
        }
      })
      if (resp.ok) {
        const d = await resp.json()
        if (d.code === 200) {
          const newMap = new Map(studentCountCache.value)
          newMap.set(classId, Array.isArray(d.data) ? d.data.length : 0)
          studentCountCache.value = newMap
        }
      }
    } catch (error) {
      console.error('获取学生数量失败:', error)
    }
  }
}

// 加载班级列表
const loadClasses = async () => {
  loading.value = true
  try {
    const params = new URLSearchParams({
      page: pagination.current,
      size: pagination.size
    })

    if (filterForm.keyword) params.append('keyword', filterForm.keyword)
    if (filterForm.courseId) params.append('courseId', filterForm.courseId)
    if (filterForm.teacherId) params.append('teacherId', filterForm.teacherId)

    const response = await fetch(`/api/classes?${params}`, {
      headers: {
        'Authorization': getAuthHeader() || ''
      }
    })

    if (response.ok) {
      const data = await response.json()
      if (data.code === 200) {
        classes.value = Array.isArray(data.data?.records) ? data.data.records : []
        pagination.total = data.data?.total || 0
        // 班级列表加载完后，批量获取学生数量
        const classIds = classes.value.map(c => c.id)
        batchLoadStudentCounts(classIds)
      } else {
        ElMessage.error('加载班级列表失败')
      }
    } else {
      ElMessage.error('加载班级列表失败')
    }
  } catch (error) {
    console.error('加载班级列表失败:', error)
    ElMessage.error('加载班级列表失败')
  } finally {
    loading.value = false
  }
}

const handleSearch = () => {
  pagination.current = 1
  loadClasses()
}

// 加载课程列表
const loadCourses = async () => {
  try {
    const response = await fetch('/api/courses?size=100', {
      headers: {
        'Authorization': getAuthHeader() || ''
      }
    })

    if (response.ok) {
      const data = await response.json()
      if (data.code === 200) {
        courses.value = Array.isArray(data.data?.records) ? data.data.records : []
      }
    }
  } catch (error) {
    console.error('加载课程列表失败:', error)
  }
}

// 加载教师列表
const loadTeachers = async () => {
  try {
    const response = await fetch('/api/admin/users?role=TEACHER&size=100', {
      headers: {
        'Authorization': getAuthHeader() || ''
      }
    })

    if (response.ok) {
      const data = await response.json()
      if (data.code === 200) {
        teachers.value = Array.isArray(data.data?.records) ? data.data.records : []
      }
    }
  } catch (error) {
    console.error('加载教师列表失败:', error)
  }
}

// 显示创建对话框
const showCreateDialog = () => {
  isEdit.value = false
  resetForm()
  dialogVisible.value = true
}

// 显示编辑对话框
const showEditDialog = (row) => {
  isEdit.value = true
  Object.assign(form, row)
  dialogVisible.value = true
}

// 显示详情对话框
const showDetailDialog = async (row) => {
  currentClass.value = row
  detailVisible.value = true
  classProfileSummary.value = []
  await loadClassStudents(row.id)
}

// 加载班级学生
const loadClassStudents = async (classId) => {
  studentsLoading.value = true
  try {
    const response = await fetch(`/api/classes/${classId}/students`, {
      headers: {
        'Authorization': getAuthHeader() || ''
      }
    })

    if (response.ok) {
      const data = await response.json()
      if (data.code === 200) {
        classStudents.value = Array.isArray(data.data) ? data.data : []
      }
    }
  } catch (error) {
    console.error('加载班级学生失败:', error)
  } finally {
    studentsLoading.value = false
  }
}

const loadClassProfile = async () => {
  if (!currentClass.value?.id) return
  profileLoading.value = true
  try {
    const res = await getClassLearnerProfileSummary(currentClass.value.id)
    classProfileSummary.value = res?.data || []
  } catch (e) {
    console.error('加载画像摘要失败:', e)
    classProfileSummary.value = []
  } finally {
    profileLoading.value = false
  }
}

// 获取学生数量（纯读缓存，不再在渲染函数中发起HTTP请求）
const getStudentCount = (classId) => {
  if (studentCountCache.value.has(classId)) {
    return studentCountCache.value.get(classId)
  }
  return '-'
}

// 提交表单
const submitForm = async () => {
  if (!formRef.value) return

  try {
    await formRef.value.validate()
    submitting.value = true

    const url = isEdit.value ? `/api/classes/${form.id}` : '/api/classes'
    const method = isEdit.value ? 'PUT' : 'POST'

    const response = await fetch(url, {
      method,
      headers: {
        'Content-Type': 'application/json',
        'Authorization': getAuthHeader() || ''
      },
      body: JSON.stringify(form)
    })

    if (response.ok) {
      const data = await response.json()
      if (data.code === 200) {
        ElMessage.success(isEdit.value ? '班级更新成功' : '班级创建成功')
        dialogVisible.value = false
        // 清理学生数量缓存
        studentCountCache.value.clear()
        // 删除班级后重置分页到第1页，防止页码超出范围
        pagination.current = 1
        loadClasses()
      } else {
        ElMessage.error('操作失败，请稍后重试')
      }
    } else {
      ElMessage.error('操作失败')
    }
  } catch (error) {
    console.error('提交失败:', error)
    ElMessage.error('操作失败')
  } finally {
    submitting.value = false
  }
}

// 删除班级
const deleteClass = async (row) => {
  try {
    await ElMessageBox.confirm(
      `确定要删除班级"${row.name}"吗？此操作不可恢复。`,
      '删除确认',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )

    const response = await fetch(`/api/classes/${row.id}`, {
      method: 'DELETE',
      headers: {
        'Authorization': getAuthHeader() || ''
      }
    })

    if (response.ok) {
      const data = await response.json()
      if (data.code === 200) {
        ElMessage.success('班级删除成功')

        // 清理学生数量缓存
        studentCountCache.value.clear()

        // 删除班级后重置分页到第1页，防止页码超出范围
        pagination.current = 1
        loadClasses()
      } else {
        ElMessage.error('删除失败，请稍后重试')
      }
    } else {
      ElMessage.error('删除失败')
    }
  } catch (error) {
    if (error !== 'cancel') {
      console.error('删除失败:', error)
      ElMessage.error('删除失败')
    }
  }
}

// 移除学生
const removeStudent = async (row) => {
  try {
    await ElMessageBox.confirm(
      `确定要将学生"${row.student?.nickname || row.student?.username}"移出班级吗？`,
      '移除确认',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )

    const response = await fetch(`/api/classes/${currentClass.value.id}/students/${row.student?.id}`, {
      method: 'DELETE',
      headers: {
        'Authorization': getAuthHeader() || ''
      }
    })

    if (response.ok) {
      const data = await response.json()
      if (data.code === 200) {
        ElMessage.success('学生移除成功')

        // 清理学生数量缓存
        studentCountCache.value.clear()

        loadClassStudents(currentClass.value.id)
      } else {
        ElMessage.error('移除失败，请稍后重试')
      }
    } else {
      ElMessage.error('移除失败')
    }
  } catch (error) {
    if (error !== 'cancel') {
      console.error('移除失败:', error)
      ElMessage.error('移除失败')
    }
  }
}

// 重置筛选
const resetFilter = () => {
  Object.assign(filterForm, {
    keyword: '',
    courseId: null,
    teacherId: null
  })
  pagination.current = 1
  loadClasses()
}

// 重置表单
const resetForm = () => {
  Object.assign(form, {
    id: null,
    name: '',
    courseId: null,
    teacherId: null,
    description: ''
  })
  if (formRef.value) {
    formRef.value.resetFields()
  }
}

// 格式化日期时间
const formatDateTime = (dateTime) => {
  if (!dateTime) return '-'
  return new Date(dateTime).toLocaleString('zh-CN')
}

// 处理下拉菜单命令
const handleDropdownCommand = (command, row) => {
  switch (command) {
    case 'edit':
      showEditDialog(row)
      break
    case 'delete':
      deleteClass(row)
      break
  }
}

// 初始化
onMounted(() => {
  // 初始化时清理缓存，确保数据是最新的
  studentCountCache.value.clear()

  loadClasses()
  loadCourses()
  loadTeachers()
})
</script>

<style scoped>
.classes-management {
  max-width: 1400px;
  margin: 0 auto;
  padding: 20px;
}

/* PC端大字号适配 */
@media (min-width: 1200px) {
  .classes-management {
    padding: 30px;
  }
  
  .card-header span {
    font-size: 24px;
  }

  :deep(.el-table) {
    font-size: 16px;
  }

  :deep(.el-table .cell) {
    line-height: 1.6;
    padding: 12px 0;
  }

  .filter-bar {
    margin-bottom: 25px;
    padding: 24px;
  }
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.card-header span {
  font-size: 20px;
  font-weight: 800;
  color: #1e293b;
  position: relative;
  padding-left: 16px;
}

.card-header span::before {
  content: '';
  position: absolute;
  left: 0;
  top: 50%;
  transform: translateY(-50%);
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #6366f1;
}

.filter-bar {
  background: #f8fafc;
  padding: 20px;
  border-radius: 16px;
  margin-bottom: 20px;
  border: 1px solid #f1f5f9;
}

.filter-bar :deep(.el-form-item) {
  margin-bottom: 0;
  margin-right: 16px;
}

.table-wrapper {
  background: white;
  border-radius: 16px;
  border: 1px solid #f1f5f9;
  overflow: hidden;
  box-shadow: 0 2px 12px rgba(0,0,0,0.05);
}

:deep(.el-table) {
  --el-table-header-bg-color: #f8fafc;
  font-size: 15px;
}

.action-buttons {
  display: flex;
  align-items: center;
  gap: 12px;
}

.pagination-wrapper {
  margin-top: 24px;
  display: flex;
  justify-content: center;
}

h3 {
  margin: 24px 0 16px;
  font-size: 18px;
  font-weight: 700;
  color: #1e293b;
}

@media (max-width: 768px) {
  .classes-management {
    padding: 12px;
  }

  .filter-bar {
    padding: 16px;
  }

  .filter-bar .el-form {
    display: flex;
    flex-direction: column;
    gap: 12px;
  }

  .filter-bar .el-form-item {
    margin-right: 0;
  }

  .filter-bar :deep(.el-select) {
    width: 100% !important;
  }
}
</style>