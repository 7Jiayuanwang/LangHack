<template>
  <div class="classes-management">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>班级管理</span>
          <el-button type="primary" @click="showCreateDialog">创建班级</el-button>
        </div>
      </template>

      <!-- 班级列表 -->
      <el-table :data="classes" stripe>
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="name" label="班级名称" min-width="150" />
        <el-table-column label="所属课程" min-width="150">
          <template #default="scope">
            {{ scope.row.course?.name || '-' }}
          </template>
        </el-table-column>
        <el-table-column label="任课教师" min-width="120">
          <template #default="scope">
            {{ scope.row.teacher?.nickname || scope.row.teacher?.realName || scope.row.teacher?.username || '-' }}
          </template>
        </el-table-column>
        <el-table-column label="学生人数" width="100">
          <template #default="scope">
            <el-tag type="info">{{ getStudentCount(scope.row.id) }}人</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="description" label="班级描述" min-width="200" show-overflow-tooltip />
        <el-table-column prop="createdTime" label="创建时间" width="180">
          <template #default="scope">
            {{ formatTime(scope.row.createdTime) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200">
          <template #default="scope">
            <el-button size="small" @click="viewStudents(scope.row)">查看学生</el-button>
            <el-button size="small" @click="editClass(scope.row)">编辑</el-button>
            <el-button size="small" type="danger" @click="deleteClass(scope.row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 空状态 -->
      <el-empty v-if="classes.length === 0" description="暂无班级">
        <el-button type="primary" @click="showCreateDialog">创建第一个班级</el-button>
      </el-empty>
    </el-card>

    <!-- 创建/编辑班级对话框 -->
    <el-dialog
      v-model="dialogVisible"
      :title="isEdit ? '编辑班级' : '创建班级'"
      width="500px"
    >
      <el-form
        ref="classFormRef"
        :model="classForm"
        :rules="classRules"
        label-width="80px"
      >
        <el-form-item label="班级名称" prop="name">
          <el-input v-model="classForm.name" placeholder="请输入班级名称" />
        </el-form-item>
        <el-form-item label="所属课程" prop="courseId">
          <el-select v-model="classForm.courseId" placeholder="请选择课程" style="width: 100%">
            <el-option
              v-for="course in courses"
              :key="course.id"
              :label="course.name"
              :value="course.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="班级描述" prop="description">
          <el-input
            v-model="classForm.description"
            type="textarea"
            :rows="3"
            placeholder="请输入班级描述"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="dialogVisible = false">取消</el-button>
          <el-button type="primary" @click="saveClass" :loading="saving">确定</el-button>
        </span>
      </template>
    </el-dialog>

    <!-- 学生列表对话框 -->
    <el-dialog
      v-model="studentsDialogVisible"
      title="班级学生列表"
      width="600px"
    >
      <el-table :data="currentClassStudents" stripe>
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column label="用户名">
          <template #default="scope">
            {{ scope.row.student?.username || '-' }}
          </template>
        </el-table-column>
        <el-table-column label="真实姓名">
          <template #default="scope">
            {{ scope.row.student?.realName || scope.row.student?.nickname || '-' }}
          </template>
        </el-table-column>
        <el-table-column label="邮箱">
          <template #default="scope">
            {{ scope.row.student?.email || '-' }}
          </template>
        </el-table-column>
        <el-table-column prop="joinTime" label="加入时间" width="180">
          <template #default="scope">
            {{ formatTime(scope.row.joinTime) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="100">
          <template #default="scope">
            <el-button size="small" type="danger" @click="removeStudent(scope.row)">移除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'

// 响应式数据
const classes = ref([])
const courses = ref([])
const dialogVisible = ref(false)
const studentsDialogVisible = ref(false)
const isEdit = ref(false)
const saving = ref(false)
const currentClassStudents = ref([])
const classFormRef = ref()
const studentCountCache = ref(new Map())

// 班级表单
const classForm = reactive({
  id: null,
  name: '',
  courseId: null,
  description: ''
})

// 表单验证规则
const classRules = {
  name: [
    { required: true, message: '请输入班级名称', trigger: 'blur' }
  ],
  courseId: [
    { required: true, message: '请选择课程', trigger: 'change' }
  ]
}

// 方法
const loadClasses = async () => {
  try {
    const response = await fetch('/api/classes?size=100', {
      headers: {
        'Authorization': `Bearer ${localStorage.getItem('token')}`
      }
    })

    if (response.ok) {
      const data = await response.json()
      if (data.code === 200) {
        classes.value = data.data.records
      } else {
        ElMessage.error(data.message || '加载班级列表失败')
      }
    } else {
      ElMessage.error('加载班级列表失败')
    }
  } catch (error) {
    console.error('加载班级列表失败:', error)
    ElMessage.error('加载班级列表失败')
  }
}

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

const showCreateDialog = () => {
  isEdit.value = false
  dialogVisible.value = true
  resetClassForm()
}

const editClass = (classItem) => {
  isEdit.value = true
  dialogVisible.value = true
  Object.assign(classForm, {
    id: classItem.id,
    name: classItem.name,
    courseId: classItem.courseId || 1,
    description: classItem.description
  })
}

const resetClassForm = () => {
  Object.assign(classForm, {
    id: null,
    name: '',
    courseId: null,
    description: ''
  })
}

const saveClass = async () => {
  if (!classFormRef.value) return

  await classFormRef.value.validate(async (valid) => {
    if (valid) {
      saving.value = true
      try {
        const url = isEdit.value ? `/api/classes/${classForm.id}` : '/api/classes'
        const method = isEdit.value ? 'PUT' : 'POST'

        const response = await fetch(url, {
          method,
          headers: {
            'Content-Type': 'application/json',
            'Authorization': `Bearer ${localStorage.getItem('token')}`
          },
          body: JSON.stringify(classForm)
        })

        if (response.ok) {
          const data = await response.json()
          if (data.code === 200) {
            ElMessage.success(isEdit.value ? '班级更新成功' : '班级创建成功')
            dialogVisible.value = false

            // 清理学生数量缓存
            studentCountCache.value.clear()

            // 重新加载班级列表
            loadClasses()
          } else {
            ElMessage.error(data.message || '操作失败')
          }
        } else {
          ElMessage.error('操作失败')
        }
      } catch (error) {
        console.error('保存班级失败:', error)
        ElMessage.error('操作失败')
      } finally {
        saving.value = false
      }
    }
  })
}

const viewStudents = async (classItem) => {
  try {
    const response = await fetch(`/api/classes/${classItem.id}/students`, {
      headers: {
        'Authorization': `Bearer ${localStorage.getItem('token')}`
      }
    })

    if (response.ok) {
      const data = await response.json()
      if (data.code === 200) {
        currentClassStudents.value = data.data
        studentsDialogVisible.value = true
      } else {
        ElMessage.error(data.message || '加载学生列表失败')
      }
    } else {
      ElMessage.error('加载学生列表失败')
    }
  } catch (error) {
    console.error('加载学生列表失败:', error)
    ElMessage.error('加载学生列表失败')
  }
}

const deleteClass = async (classItem) => {
  try {
    await ElMessageBox.confirm(`确认删除班级 "${classItem.name}" 吗？`, '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })

    const response = await fetch(`/api/classes/${classItem.id}`, {
      method: 'DELETE',
      headers: {
        'Authorization': `Bearer ${localStorage.getItem('token')}`
      }
    })

    if (response.ok) {
      const data = await response.json()
      if (data.code === 200) {
        ElMessage.success('班级删除成功')
        loadClasses()
      } else {
        ElMessage.error(data.message || '删除失败')
      }
    } else {
      ElMessage.error('删除失败')
    }
  } catch (error) {
    if (error !== 'cancel') {
      console.error('删除班级失败:', error)
      ElMessage.error('删除失败')
    }
  }
}

const removeStudent = async (student) => {
  try {
    await ElMessageBox.confirm(`确认移除学生 "${student.student?.realName || student.student?.username}" 吗？`, '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })

    // 获取当前班级ID
    const currentClassId = currentClassStudents.value[0]?.classId
    if (!currentClassId) return

    const response = await fetch(`/api/classes/${currentClassId}/students/${student.student.id}`, {
      method: 'DELETE',
      headers: {
        'Authorization': `Bearer ${localStorage.getItem('token')}`
      }
    })

    if (response.ok) {
      const data = await response.json()
      if (data.code === 200) {
        ElMessage.success('学生移除成功')
        // 重新加载学生列表
        viewStudents({ id: currentClassId })
      } else {
        ElMessage.error(data.message || '移除失败')
      }
    } else {
      ElMessage.error('移除失败')
    }
  } catch (error) {
    if (error !== 'cancel') {
      console.error('移除学生失败:', error)
      ElMessage.error('移除失败')
    }
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

const formatTime = (time) => {
  if (!time) return ''
  return new Date(time).toLocaleString()
}

// 生命周期
onMounted(() => {
  // 初始化时清理缓存，确保数据是最新的
  studentCountCache.value.clear()

  loadClasses()
  loadCourses()
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
</style>