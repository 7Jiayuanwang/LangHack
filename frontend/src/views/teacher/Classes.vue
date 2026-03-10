<template>
  <div class="classes-management">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>班级管理</span>
<!--          <el-button type="primary" @click="showCreateDialog">创建班级</el-button>-->
        </div>
      </template>

      <!-- 班级列表 -->
      <div class="table-wrapper">
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
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="scope">
            <div class="action-buttons">
              <el-button 
                size="small" 
                type="primary" 
                :icon="User" 
                @click="viewStudents(scope.row)"
                title="查看学生"
              >
                学生
              </el-button>
              <el-button 
                size="small" 
                type="success" 
                :icon="Download" 
                @click="exportClassScores(scope.row)"
                title="导出成绩"
              >
                导出
              </el-button>
              <el-dropdown trigger="click" @command="(cmd) => handleDropdownCommand(cmd, scope.row)">
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

      <!-- 空状态 -->
      <el-empty v-if="classes.length === 0" description="暂无班级">
        <el-button type="primary" @click="showCreateDialog">创建第一个班级</el-button>
      </el-empty>
    </el-card>

    <!-- 创建/编辑班级对话框 -->
    <el-dialog
      v-model="dialogVisible"
      :title="isEdit ? '编辑班级' : '创建班级'"
      width="min(500px, 90%)"
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
      title="班级学生管理"
      width="min(700px, 90%)"
    >
      <div style="margin-bottom: 20px;">
        <el-button type="primary" @click="showAddStudentDialog">
          <el-icon><Plus /></el-icon>
          添加学生
        </el-button>
      </div>
      <div class="table-wrapper">
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
      </div>
    </el-dialog>

    <!-- 添加学生对话框 -->
    <el-dialog
      v-model="addStudentDialogVisible"
      title="添加学生到班级"
      width="min(500px, 90%)"
    >
      <el-form :model="addStudentForm" label-width="80px">
        <el-form-item label="学生账号">
          <el-select
            v-model="addStudentForm.studentId"
            placeholder="请选择学生"
            filterable
            remote
            :remote-method="searchStudents"
            :loading="searching"
            style="width: 100%"
          >
            <el-option
              v-for="student in availableStudents"
              :key="student.id"
              :label="`${student.username} (${student.realName || '未设置姓名'})`"
              :value="student.id"
            />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="addStudentDialogVisible = false">取消</el-button>
          <el-button type="primary" @click="addStudentToClass" :loading="adding">
            确定
          </el-button>
        </span>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { User, Download, MoreFilled, Edit, Delete, Plus } from '@element-plus/icons-vue'

// 响应式数据
const classes = ref([])
const courses = ref([])
const dialogVisible = ref(false)
const studentsDialogVisible = ref(false)
const isEdit = ref(false)
const saving = ref(false)
const currentClassStudents = ref([])
const classFormRef = ref()
const studentCountCache = reactive(new Map())

// 添加学生数量的响应式数据
const classStudentCounts = reactive(new Map())

// 添加学生相关数据
const addStudentDialogVisible = ref(false)
const searching = ref(false)
const adding = ref(false)
const availableStudents = ref([])
const currentClassId = ref(null)

// 添加学生表单
const addStudentForm = reactive({
  studentId: null
})

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
        'Authorization': `Bearer ${localStorage.getItem('token') || ''}`
      }
    })

    if (response.ok) {
      try {
        const data = await response.json()
        if (data.code === 200) {
          classes.value = data.data?.records || []
          // 加载完班级列表后，加载所有班级的学生数量
          await loadAllStudentCounts()
        } else {
          ElMessage.error(data.message || '加载班级列表失败')
        }
      } catch (parseError) {
        console.error('班级列表解析失败:', parseError)
        ElMessage.error('加载班级列表失败')
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
        'Authorization': `Bearer ${localStorage.getItem('token') || ''}`
      }
    })

    if (response.ok) {
      try {
        const data = await response.json()
        if (data.code === 200) {
          courses.value = data.data?.records || []
        }
      } catch (parseError) {
        console.error('课程列表解析失败:', parseError)
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
    courseId: classItem.courseId || null,
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
            'Authorization': `Bearer ${localStorage.getItem('token') || ''}`
          },
          body: JSON.stringify(classForm)
        })

        if (response.ok) {
          try {
            const data = await response.json()
            if (data.code === 200) {
              ElMessage.success(isEdit.value ? '班级更新成功' : '班级创建成功')
              dialogVisible.value = false

              // 清理学生数量缓存
              studentCountCache.clear()
              classStudentCounts.clear()

              // 重新加载班级列表
              loadClasses()
            } else {
              ElMessage.error(data.message || '操作失败')
            }
          } catch (parseError) {
            console.error('响应解析失败:', parseError)
            ElMessage.error('操作失败')
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
    currentClassId.value = classItem.id
    const response = await fetch(`/api/classes/${classItem.id}/students`, {
      headers: {
        'Authorization': `Bearer ${localStorage.getItem('token') || ''}`
      }
    })

    if (response.ok) {
      try {
        const data = await response.json()
        if (data.code === 200) {
          currentClassStudents.value = data.data || []
          studentsDialogVisible.value = true
        } else {
          ElMessage.error(data.message || '加载学生列表失败')
        }
      } catch (parseError) {
        console.error('学生列表解析失败:', parseError)
        ElMessage.error('加载学生列表失败')
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
        'Authorization': `Bearer ${localStorage.getItem('token') || ''}`
      }
    })

    if (response.ok) {
      try {
        const data = await response.json()
        if (data.code === 200) {
          ElMessage.success('班级删除成功')
          // 清理学生数量缓存
          classStudentCounts.clear()
          loadClasses()
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
      console.error('删除班级失败:', error)
      ElMessage.error('删除失败')
    }
  }
}

const exportClassScores = async (classItem) => {
  try {
    ElMessage.info('正在导出成绩，请稍候...')
    
    const response = await fetch(`/api/scores/class/${classItem.id}/export`, {
      headers: {
        'Authorization': `Bearer ${localStorage.getItem('token') || ''}`
      }
    })
    
    if (response.ok) {
      const blob = await response.blob()
      const url = window.URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url
      a.download = `班级成绩_${classItem.name}_${new Date().toISOString().split('T')[0]}.xlsx`
      document.body.appendChild(a)
      a.click()
      document.body.removeChild(a)
      window.URL.revokeObjectURL(url)
      ElMessage.success('成绩导出成功')
    } else {
      // response.json() on non-JSON response causes crash - wrap in try-catch
      try {
        const data = await response.json()
        ElMessage.error(data.message || '导出失败')
      } catch (parseError) {
        // response is not JSON (error page, redirect, etc.)
        ElMessage.error('导出失败')
      }
    }
  } catch (error) {
    console.error('导出成绩失败:', error)
    ElMessage.error('导出成绩失败')
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
    const classId = currentClassId.value
    if (!classId) return

    // 验证学生ID存在
    const studentId = student.student?.id
    if (!studentId) {
      ElMessage.error('学生信息不完整，无法移除')
      return
    }

    const response = await fetch(`/api/classes/${classId}/students/${studentId}`, {
      method: 'DELETE',
      headers: {
        'Authorization': `Bearer ${localStorage.getItem('token') || ''}`
      }
    })

    if (response.ok) {
      try {
        const data = await response.json()
        if (data.code === 200) {
          ElMessage.success('学生移除成功')
          // 重新加载学生列表
          viewStudents({ id: classId })
          // 更新当前班级的学生数量
          await loadStudentCount(classId)
        } else {
          ElMessage.error(data.message || '移除失败')
        }
      } catch (parseError) {
        console.error('移除响应解析失败:', parseError)
        ElMessage.error('移除失败')
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

// 显示添加学生对话框
const showAddStudentDialog = () => {
  addStudentForm.studentId = null
  availableStudents.value = []
  addStudentDialogVisible.value = true
}

// 搜索学生（使用真实API）
const searchStudents = async (query) => {
  if (!query) {
    availableStudents.value = []
    return
  }

  searching.value = true

  try {
    const response = await fetch(`/api/auth/search?keyword=${encodeURIComponent(query)}`, {
      headers: {
        'Authorization': `Bearer ${localStorage.getItem('token') || ''}`
      }
    })

    if (response.ok) {
      try {
        const data = await response.json()

        if (data.code === 200) {
          let filteredStudents = Array.isArray(data.data) ? data.data : []

          // 只保留 USER 和 STUDENT 角色的用户（排除 TEACHER 和 ADMIN）
          // 注: 后端返回 role 为 'USER' 或 'STUDENT'，前端需要同时接受两种角色
          filteredStudents = filteredStudents.filter(s => {
            const role = s.role?.toUpperCase()
            return role === 'USER' || role === 'STUDENT'
          })

          // 过滤掉已经在班级里的学生
          const classStudentIds = currentClassStudents.value
            .map(cs => cs.student?.id)
            .filter(id => id !== null && id !== undefined)

          availableStudents.value = filteredStudents.filter(student =>
            !classStudentIds.includes(student.id)
          )

          if (availableStudents.value.length === 0) {
            ElMessage.info('未找到可添加的学生')
          }
        } else {
          console.error('搜索API返回错误:', data)
          ElMessage.error(data.message || '搜索学生失败')
        }
      } catch (parseError) {
        console.error('搜索响应解析失败:', parseError)
        ElMessage.error('搜索学生失败')
      }
    } else {
      console.error('搜索API请求失败:', response.status)
      ElMessage.error('搜索学生失败')
    }
  } catch (error) {
    console.error('搜索学生异常:', error)
    ElMessage.error('搜索学生失败，请稍后重试')
  } finally {
    searching.value = false
  }
}

// 添加学生到班级
const addStudentToClass = async () => {
  if (!addStudentForm.studentId) {
    ElMessage.warning('请选择要添加的学生')
    return
  }

  adding.value = true
  try {
    const response = await fetch(`/api/classes/${currentClassId.value}/students`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${localStorage.getItem('token') || ''}`
      },
      body: JSON.stringify({
        studentId: addStudentForm.studentId
      })
    })

    if (response.ok) {
      try {
        const data = await response.json()

        if (data.code === 200) {
          ElMessage.success('学生添加成功')
          addStudentDialogVisible.value = false
          addStudentForm.studentId = null
          availableStudents.value = []
          // 重新加载学生列表
          viewStudents({ id: currentClassId.value })
          // 更新当前班级的学生数量
          await loadStudentCount(currentClassId.value)
        } else {
          console.error('添加学生API返回错误:', data)
          ElMessage.error(data.message || '添加失败')
        }
      } catch (parseError) {
        console.error('添加响应解析失败:', parseError)
        ElMessage.error('添加失败，请稍后重试')
      }
    } else {
      console.error('添加学生API请求失败:', response.status)
      ElMessage.error('添加失败')
    }
  } catch (error) {
    console.error('添加学生异常:', error)
    ElMessage.error('添加失败，请稍后重试')
  } finally {
    adding.value = false
  }
}

// 获取学生数量（同步返回）
const getStudentCount = (classId) => {
  return classStudentCounts.get(classId) || 0
}

// 异步加载单个班级的学生数量
const loadStudentCount = async (classId) => {
  try {
    const response = await fetch(`/api/classes/${classId}/students`, {
      headers: {
        'Authorization': `Bearer ${localStorage.getItem('token') || ''}`
      }
    })

    if (response.ok) {
      try {
        const data = await response.json()
        if (data.code === 200) {
          const count = Array.isArray(data.data) ? data.data.length : 0
          classStudentCounts.set(classId, count)
          studentCountCache.set(classId, count)
          return count
        }
      } catch (parseError) {
        console.error(`获取班级${classId}学生数量解析失败:`, parseError)
      }
    }
  } catch (error) {
    console.error(`获取班级${classId}学生数量失败:`, error)
  }
  return 0
}

// 加载所有班级的学生数量
const loadAllStudentCounts = async () => {
  for (const classItem of classes.value) {
    await loadStudentCount(classItem.id)
  }
}

const formatTime = (time) => {
  if (!time) return ''
  return new Date(time).toLocaleString()
}

// 处理下拉菜单命令
const handleDropdownCommand = (command, row) => {
  switch (command) {
    case 'edit':
      editClass(row)
      break
    case 'delete':
      deleteClass(row)
      break
  }
}

// 生命周期
onMounted(() => {
  // 初始化时清理缓存，确保数据是最新的
  studentCountCache.clear()
  classStudentCounts.clear()

  loadClasses()
  loadCourses()
})
</script>

<style scoped>
.classes-management {
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

.action-buttons {
  display: flex;
  align-items: center;
  gap: 6px;
  justify-content: flex-start;
}

.action-buttons .el-button {
  margin: 0;
  padding: 5px 12px;
  font-size: 13px;
  border-radius: 4px;
  transition: all 0.2s ease;
}

.action-buttons .el-button:hover {
  transform: translateY(-1px);
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
}

.action-buttons .el-button--circle {
  width: 28px;
  height: 28px;
  padding: 0;
  display: flex;
  align-items: center;
  justify-content: center;
}

.action-buttons .el-dropdown {
  margin-left: 2px;
}

@media (max-width: 768px) {
  .classes-management {
    padding: 12px;
  }
}
</style>