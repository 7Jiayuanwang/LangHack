<template>
  <div class="assignments-management">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>任务管理</span>
          <el-button type="primary" @click="showCreateDialog">布置任务</el-button>
        </div>
      </template>

      <!-- 筛选栏 -->
      <div class="filter-bar">
        <el-form :inline="true" :model="filterForm">
          <el-form-item label="班级">
            <el-select v-model="filterForm.classId" placeholder="请选择班级" clearable @change="loadAssignments">
              <el-option
                v-for="cls in classes"
                :key="cls.id"
                :label="cls.name"
                :value="cls.id"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="状态">
            <el-select v-model="filterForm.status" placeholder="请选择状态" clearable @change="loadAssignments">
              <el-option label="进行中" value="active" />
              <el-option label="已结束" value="ended" />
            </el-select>
          </el-form-item>
        </el-form>
      </div>

      <!-- 任务列表 -->
      <el-table v-loading="loading" :data="assignments" stripe>
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="title" label="任务标题" />
        <el-table-column prop="className" label="班级名称" />
        <el-table-column prop="unitName" label="学习单元" />
        <el-table-column prop="studentCount" label="学生总数" width="100" />
        <el-table-column prop="completedCount" label="完成人数" width="100">
          <template #default="scope">
            <el-tag :type="getCompletionType(scope.row.completedCount, scope.row.studentCount)">
              {{ scope.row.completedCount }}/{{ scope.row.studentCount }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="deadline" label="截止时间" width="180">
          <template #default="scope">
            {{ formatTime(scope.row.deadline) }}
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="100">
          <template #default="scope">
            <el-tag :type="scope.row.status === 'active' ? 'success' : 'info'">
              {{ scope.row.status === 'active' ? '进行中' : '已结束' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200">
          <template #default="scope">
            <el-button size="small" @click="viewProgress(scope.row)">查看进度</el-button>
            <el-button size="small" @click="editAssignment(scope.row)">编辑</el-button>
            <el-button size="small" type="danger" @click="deleteAssignment(scope.row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 空状态 -->
      <el-empty v-if="assignments.length === 0" description="暂无任务">
        <el-button type="primary" @click="showCreateDialog">布置第一个任务</el-button>
      </el-empty>
    </el-card>

    <!-- 创建/编辑任务对话框 -->
    <el-dialog
      v-model="dialogVisible"
      :title="isEdit ? '编辑任务' : '布置任务'"
      width="600px"
    >
      <el-form
        ref="assignmentFormRef"
        :model="assignmentForm"
        :rules="assignmentRules"
        label-width="80px"
      >
        <el-form-item label="任务标题" prop="title">
          <el-input v-model="assignmentForm.title" placeholder="请输入任务标题" />
        </el-form-item>
        <el-form-item label="选择班级" prop="classId">
          <el-select v-model="assignmentForm.classId" placeholder="请选择班级" style="width: 100%">
            <el-option
              v-for="cls in classes"
              :key="cls.id"
              :label="cls.name"
              :value="cls.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="选择单元" prop="unitId">
          <el-select v-model="assignmentForm.unitId" placeholder="请选择单元" style="width: 100%">
            <el-option
              v-for="unit in units"
              :key="unit.id"
              :label="unit.name"
              :value="unit.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="任务描述" prop="description">
          <el-input
            v-model="assignmentForm.description"
            type="textarea"
            :rows="4"
            placeholder="请输入任务描述"
          />
        </el-form-item>
        <el-form-item label="截止时间" prop="deadline">
          <el-date-picker
            v-model="assignmentForm.deadline"
            type="datetime"
            placeholder="选择截止时间"
            style="width: 100%"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="dialogVisible = false">取消</el-button>
          <el-button type="primary" @click="saveAssignment" :loading="saving">确定</el-button>
        </span>
      </template>
    </el-dialog>

    <!-- 任务进度对话框 -->
    <el-dialog
      v-model="progressDialogVisible"
      title="任务完成进度"
      width="700px"
    >
      <div class="progress-overview">
        <div class="stats-row">
          <div class="stat-item">
            <span class="label">总人数：</span>
            <span class="value">{{ currentAssignment.studentCount }}人</span>
          </div>
          <div class="stat-item">
            <span class="label">已完成：</span>
            <span class="value completed">{{ currentAssignment.completedCount }}人</span>
          </div>
          <div class="stat-item">
            <span class="label">完成率：</span>
            <span class="value">{{ getCompletionRate(currentAssignment) }}%</span>
          </div>
        </div>
        <el-progress
          :percentage="getCompletionRate(currentAssignment)"
          :color="getProgressColor(getCompletionRate(currentAssignment))"
          :stroke-width="8"
        />
      </div>

      <div class="students-progress">
        <h4>学生完成情况</h4>
        <el-table :data="studentProgress" stripe size="small">
          <el-table-column prop="username" label="用户名" width="120" />
          <el-table-column prop="realName" label="真实姓名" width="120" />
          <el-table-column prop="completedAt" label="完成时间" width="180">
            <template #default="scope">
              {{ scope.row.completedAt ? formatTime(scope.row.completedAt) : '未完成' }}
            </template>
          </el-table-column>
          <el-table-column prop="timeSpent" label="学习时长" width="100">
            <template #default="scope">
              {{ scope.row.timeSpent || '-' }}分钟
            </template>
          </el-table-column>
          <el-table-column prop="score" label="得分" width="80">
            <template #default="scope">
              <el-tag v-if="scope.row.score !== null" :type="getScoreType(scope.row.score)">
                {{ scope.row.score }}分
              </el-tag>
              <span v-else>-</span>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'

// 响应式数据
const loading = ref(false)
const assignments = ref([])
const classes = ref([])
const units = ref([])
const studentProgress = ref([])
const dialogVisible = ref(false)
const progressDialogVisible = ref(false)
const isEdit = ref(false)
const saving = ref(false)
const assignmentFormRef = ref()
const currentAssignment = ref({})

// 筛选表单
const filterForm = reactive({
  classId: null,
  status: null
})

// 任务表单
const assignmentForm = reactive({
  id: null,
  title: '',
  classId: null,
  unitId: null,
  description: '',
  deadline: null
})

// 表单验证规则
const assignmentRules = {
  title: [
    { required: true, message: '请输入任务标题', trigger: 'blur' }
  ],
  classId: [
    { required: true, message: '请选择班级', trigger: 'change' }
  ],
  unitId: [
    { required: true, message: '请选择单元', trigger: 'change' }
  ],
  deadline: [
    { required: true, message: '请选择截止时间', trigger: 'change' }
  ]
}

// 方法
const loadAssignments = async () => {
  loading.value = true
  try {
    const params = new URLSearchParams()

    if (filterForm.classId) {
      params.append('classId', filterForm.classId)
    }
    if (filterForm.status) {
      params.append('status', filterForm.status)
    }
    params.append('page', '1')
    params.append('size', '100')

    const response = await fetch(`/api/assignments?${params.toString()}`, {
      headers: {
        'Authorization': `Bearer ${localStorage.getItem('token')}`
      }
    })

    if (response.ok) {
      const data = await response.json()
      if (data.code === 200) {
        assignments.value = data.data.records || []
      } else {
        ElMessage.error(data.message || '加载任务列表失败')
      }
    } else {
      ElMessage.error('加载任务列表失败')
    }
  } catch (error) {
    console.error('加载任务列表失败:', error)
    ElMessage.error('加载任务列表失败')
  } finally {
    loading.value = false
  }
}

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
        classes.value = data.data.records || []
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

const loadUnits = async () => {
  try {
    const response = await fetch('/api/units?size=100', {
      headers: {
        'Authorization': `Bearer ${localStorage.getItem('token')}`
      }
    })

    if (response.ok) {
      const data = await response.json()
      if (data.code === 200) {
        units.value = data.data.records || []
      } else {
        ElMessage.error(data.message || '加载单元列表失败')
      }
    } else {
      ElMessage.error('加载单元列表失败')
    }
  } catch (error) {
    console.error('加载单元列表失败:', error)
    ElMessage.error('加载单元列表失败')
  }
}

const showCreateDialog = () => {
  isEdit.value = false
  dialogVisible.value = true
  resetAssignmentForm()
}

const editAssignment = (assignment) => {
  isEdit.value = true
  dialogVisible.value = true
  Object.assign(assignmentForm, {
    id: assignment.id,
    title: assignment.title,
    classId: 1,
    unitId: 1,
    description: assignment.description,
    deadline: new Date(assignment.deadline)
  })
}

const resetAssignmentForm = () => {
  Object.assign(assignmentForm, {
    id: null,
    title: '',
    classId: null,
    unitId: null,
    description: '',
    deadline: null
  })
}

const saveAssignment = async () => {
  if (!assignmentFormRef.value) return

  await assignmentFormRef.value.validate(async (valid) => {
    if (valid) {
      saving.value = true
      try {
        const url = isEdit.value ? `/api/assignments/${assignmentForm.id}` : '/api/assignments'
        const method = isEdit.value ? 'PUT' : 'POST'

        const response = await fetch(url, {
          method,
          headers: {
            'Content-Type': 'application/json',
            'Authorization': `Bearer ${localStorage.getItem('token')}`
          },
          body: JSON.stringify(assignmentForm)
        })

        if (response.ok) {
          const data = await response.json()
          if (data.code === 200) {
            ElMessage.success(isEdit.value ? '任务更新成功' : '任务布置成功')
            dialogVisible.value = false
            loadAssignments()
          } else {
            ElMessage.error(data.message || '操作失败')
          }
        } else {
          ElMessage.error('操作失败')
        }
      } catch (error) {
        console.error('保存任务失败:', error)
        ElMessage.error('操作失败')
      } finally {
        saving.value = false
      }
    }
  })
}

const viewProgress = async (assignment) => {
  try {
    currentAssignment.value = assignment

    const response = await fetch(`/api/assignments/${assignment.id}/progress`, {
      headers: {
        'Authorization': `Bearer ${localStorage.getItem('token')}`
      }
    })

    if (response.ok) {
      const data = await response.json()
      if (data.code === 200) {
        studentProgress.value = data.data || []
        progressDialogVisible.value = true
      } else {
        ElMessage.error(data.message || '加载进度信息失败')
      }
    } else {
      ElMessage.error('加载进度信息失败')
    }
  } catch (error) {
    console.error('加载进度信息失败:', error)
    ElMessage.error('加载进度信息失败')
  }
}

const deleteAssignment = async (assignment) => {
  try {
    await ElMessageBox.confirm(`确认删除任务 "${assignment.title}" 吗？`, '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })

    const response = await fetch(`/api/assignments/${assignment.id}`, {
      method: 'DELETE',
      headers: {
        'Authorization': `Bearer ${localStorage.getItem('token')}`
      }
    })

    if (response.ok) {
      const data = await response.json()
      if (data.code === 200) {
        ElMessage.success('任务删除成功')
        loadAssignments()
      } else {
        ElMessage.error(data.message || '删除失败')
      }
    } else {
      ElMessage.error('删除失败')
    }
  } catch (error) {
    if (error !== 'cancel') {
      console.error('删除任务失败:', error)
      ElMessage.error('删除失败')
    }
  }
}

const getCompletionType = (completed, total) => {
  const rate = (completed / total) * 100
  if (rate >= 80) return 'success'
  if (rate >= 50) return 'warning'
  return 'danger'
}

const getCompletionRate = (assignment) => {
  if (!assignment.studentCount) return 0
  return Math.round((assignment.completedCount / assignment.studentCount) * 100)
}

const getProgressColor = (percentage) => {
  if (percentage >= 80) return '#67c23a'
  if (percentage >= 50) return '#e6a23c'
  return '#f56c6c'
}

const getScoreType = (score) => {
  if (score >= 90) return 'success'
  if (score >= 80) return 'warning'
  if (score >= 60) return 'info'
  return 'danger'
}

const formatTime = (time) => {
  if (!time) return ''
  return new Date(time).toLocaleString()
}

// 生命周期
onMounted(() => {
  loadAssignments()
  loadClasses()
  loadUnits()
})
</script>

<style scoped>
.assignments-management {
  height: 100%;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.filter-bar {
  margin-bottom: 20px;
}

.progress-overview {
  margin-bottom: 20px;
  padding: 20px;
  background-color: #f8f9fa;
  border-radius: 4px;
}

.stats-row {
  display: flex;
  justify-content: space-between;
  margin-bottom: 15px;
}

.stat-item {
  text-align: center;
}

.stat-item .label {
  color: #666;
  font-size: 14px;
}

.stat-item .value {
  font-size: 18px;
  font-weight: bold;
  color: #333;
}

.stat-item .value.completed {
  color: #67c23a;
}

.students-progress h4 {
  margin: 20px 0 10px;
  color: #333;
}
</style>