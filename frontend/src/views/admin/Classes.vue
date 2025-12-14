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
            <el-select v-model="filterForm.courseId" placeholder="请选择课程" clearable @change="loadClasses">
              <el-option
                v-for="course in courses"
                :key="course.id"
                :label="course.name"
                :value="course.id"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="任课教师">
            <el-select v-model="filterForm.teacherId" placeholder="请选择教师" clearable @change="loadClasses">
              <el-option
                v-for="teacher in teachers"
                :key="teacher.id"
                :label="teacher.nickname || teacher.realName || teacher.username"
                :value="teacher.id"
              />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" @click="loadClasses">查询</el-button>
            <el-button @click="resetFilter">重置</el-button>
          </el-form-item>
        </el-form>
      </div>

      <!-- 班级列表 -->
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
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" size="small" @click="showDetailDialog(row)">查看</el-button>
            <el-button type="warning" size="small" @click="showEditDialog(row)">编辑</el-button>
            <el-button type="danger" size="small" @click="deleteClass(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

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
      width="600px"
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
    <el-dialog v-model="detailVisible" title="班级详情" width="800px">
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
          <el-table v-loading="studentsLoading" :data="classStudents" stripe>
            <el-table-column prop="id" label="ID" width="80" />
            <el-table-column label="用户名" prop="student.username" />
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
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'

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

// 学生数量缓存
const studentCountCache = ref(new Map())

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
        'Authorization': `Bearer ${localStorage.getItem('token')}`
      }
    })

    if (response.ok) {
      const data = await response.json()
      if (data.code === 200) {
        classes.value = data.data.records
        pagination.total = data.data.total
      } else {
        ElMessage.error(data.message || '加载班级列表失败')
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

// 加载课程列表
const loadCourses = async () => {
  try {
    const response = await fetch('/api/courses?size=100', {
      headers: {
        'Authorization': `Bearer ${localStorage.getItem('token')}`
      }
    })

    if (response.ok) {
      const data = await response.json()
      if (data.code === 200) {
        courses.value = data.data.records
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
        'Authorization': `Bearer ${localStorage.getItem('token')}`
      }
    })

    if (response.ok) {
      const data = await response.json()
      if (data.code === 200) {
        teachers.value = data.data.records
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
  await loadClassStudents(row.id)
}

// 加载班级学生
const loadClassStudents = async (classId) => {
  studentsLoading.value = true
  try {
    const response = await fetch(`/api/classes/${classId}/students`, {
      headers: {
        'Authorization': `Bearer ${localStorage.getItem('token')}`
      }
    })

    if (response.ok) {
      const data = await response.json()
      if (data.code === 200) {
        classStudents.value = data.data
      }
    }
  } catch (error) {
    console.error('加载班级学生失败:', error)
  } finally {
    studentsLoading.value = false
  }
}

// 获取学生数量
const getStudentCount = (classId) => {
  if (studentCountCache.value.has(classId)) {
    return studentCountCache.value.get(classId)
  }

  // 异步获取学生数量
  fetch(`/api/classes/${classId}/students`, {
    headers: {
      'Authorization': `Bearer ${localStorage.getItem('token')}`
    }
  })
  .then(response => response.json())
  .then(data => {
    if (data.code === 200) {
      studentCountCache.value.set(classId, data.data.length)
    }
  })
  .catch(error => {
    console.error('获取学生数量失败:', error)
  })

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
        'Authorization': `Bearer ${localStorage.getItem('token')}`
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

        loadClasses()
      } else {
        ElMessage.error(data.message || '操作失败')
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
        'Authorization': `Bearer ${localStorage.getItem('token')}`
      }
    })

    if (response.ok) {
      const data = await response.json()
      if (data.code === 200) {
        ElMessage.success('班级删除成功')

        // 清理学生数量缓存
        studentCountCache.value.clear()

        loadClasses()
      } else {
        ElMessage.error(data.message || '删除失败')
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

    const response = await fetch(`/api/classes/${currentClass.value.id}/students/${row.student.id}`, {
      method: 'DELETE',
      headers: {
        'Authorization': `Bearer ${localStorage.getItem('token')}`
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
        ElMessage.error(data.message || '移除失败')
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
  height: 100%;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.filter-bar {
  margin-bottom: 20px;
  padding: 20px;
  background-color: #f5f7fa;
  border-radius: 4px;
}

.pagination-wrapper {
  margin-top: 20px;
  text-align: right;
}

h3 {
  margin: 20px 0 10px;
  color: #333;
}
</style>