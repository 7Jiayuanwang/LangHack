<template>
  <div class="assignments-management">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>作业管理</span>
          <el-button type="primary" @click="showCreateDialog">布置作业</el-button>
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
      <div class="table-wrapper">
      <el-table v-loading="loading" :data="assignments" stripe>
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="title" label="任务标题" min-width="150" />
        <el-table-column prop="className" label="班级名称" min-width="120">
          <template #default="scope">
            {{ scope.row.className || scope.row.class_name || '未知班级' }}
          </template>
        </el-table-column>
        <el-table-column prop="unitName" label="学习单元" min-width="120">
          <template #default="scope">
            {{ scope.row.unitName || scope.row.unit_name || '未知单元' }}
          </template>
        </el-table-column>
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
        <el-table-column label="操作" width="190" fixed="right">
          <template #default="scope">
            <div class="action-buttons">
              <el-button 
                size="small" 
                type="primary" 
                :icon="View" 
                @click="viewProgress(scope.row)"
                title="查看进度"
              >
                进度
              </el-button>
              <el-dropdown trigger="click" @command="(cmd) => handleDropdownCommand(cmd, scope.row)">
                <el-button size="small" :icon="MoreFilled" circle />
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item :icon="Edit" command="edit">编辑任务</el-dropdown-item>
                    <el-dropdown-item :icon="Delete" command="delete" divided>删除任务</el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </div>
          </template>
        </el-table-column>
      </el-table>
      </div>

      <!-- 空状态 -->
      <el-empty v-if="assignments.length === 0" description="暂无任务">
        <el-button type="primary" @click="showCreateDialog">布置第一个任务</el-button>
      </el-empty>
    </el-card>

    <!-- 创建/编辑任务对话框 -->
    <el-dialog
      v-model="dialogVisible"
      :title="isEdit ? '编辑任务' : '布置任务'"
      width="min(800px, 90%)"
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
          <el-select v-model="assignmentForm.classId" placeholder="请选择班级" style="width: 100%" @change="onClassChange">
            <el-option
              v-for="cls in classes"
              :key="cls.id"
              :label="cls.name"
              :value="cls.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="选择单元" prop="unitId">
          <el-select
            v-model="assignmentForm.unitId"
            placeholder="请选择单元"
            style="width: 100%"
            @change="onUnitChange"
          >
            <el-option
              v-for="unit in units"
              :key="unit.id"
              :label="unit.name"
              :value="unit.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="任务类型" prop="assignmentType">
          <el-radio-group v-model="assignmentForm.assignmentType" @change="onAssignmentTypeChange">
            <el-radio value="UNIT">整个单元</el-radio>
            <el-radio value="TEXTS">指定课文</el-radio>
            <el-radio value="WORDS">指定单词</el-radio>
          </el-radio-group>
        </el-form-item>

        <!-- 选择课文 -->
        <el-form-item v-if="assignmentForm.assignmentType === 'TEXTS'" label="选择课文" prop="textIds">
          <div v-loading="loadingUnitContent" class="content-selector">
            <el-checkbox-group v-model="assignmentForm.textIds">
              <div class="content-grid">
                <el-checkbox
                  v-for="text in unitContent.texts"
                  :key="text.id"
                  :value="text.id"
                  class="content-item"
                >
                  <div class="content-info">
                    <div class="content-title">{{ text.title }}</div>
                    <div class="content-meta">
                      难度：{{ text.difficulty }} |
                      字数：{{ text.content ? text.content.length : 0 }}
                    </div>
                  </div>
                </el-checkbox>
              </div>
            </el-checkbox-group>
            <el-empty v-if="!loadingUnitContent && unitContent.texts.length === 0" description="该单元暂无课文" />
          </div>
        </el-form-item>

        <!-- 选择单词 -->
        <el-form-item v-if="assignmentForm.assignmentType === 'WORDS'" label="选择单词" prop="wordIds">
          <div v-loading="loadingUnitContent" class="content-selector">
            <el-checkbox-group v-model="assignmentForm.wordIds">
              <div class="content-grid">
                <el-checkbox
                  v-for="word in unitContent.words"
                  :key="word.id"
                  :value="word.id"
                  class="content-item"
                >
                  <div class="content-info">
                    <div class="content-title">{{ word.word }}</div>
                    <div class="content-meta">
                      {{ word.pronunciation }} |
                      难度：{{ word.difficulty }}
                    </div>
                    <div class="content-desc">{{ word.definition }}</div>
                  </div>
                </el-checkbox>
              </div>
            </el-checkbox-group>
            <el-empty v-if="!loadingUnitContent && unitContent.words.length === 0" description="该单元暂无单词" />
          </div>
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
      width="min(700px, 90%)"
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
        <div class="table-wrapper">
        <el-table :data="studentProgress" stripe size="small">
          <el-table-column prop="username" label="用户名" width="120" />
          <el-table-column prop="realName" label="真实姓名" width="120" />
          <el-table-column prop="completedAt" label="完成时间" width="180">
            <template #default="scope">
              {{ scope.row.completedAt ? formatTime(scope.row.completedAt) : '未完成' }}
            </template>
          </el-table-column>
          <el-table-column prop="studyTime" label="学习时长" width="120">
            <template #default="scope">
              {{ formatStudyTime(scope.row.studyTime) }}
            </template>
          </el-table-column>
          <el-table-column prop="wordsLearned" label="已学单词" width="100">
            <template #default="scope">
              {{ scope.row.wordsLearned || 0 }}
            </template>
          </el-table-column>
          <el-table-column prop="textsRead" label="已读课文" width="100">
            <template #default="scope">
              {{ scope.row.textsRead || 0 }}
            </template>
          </el-table-column>
          <el-table-column prop="progress" label="学习进度" width="100">
            <template #default="scope">
              <el-progress :percentage="scope.row.progress || 0" :stroke-width="6" />
            </template>
          </el-table-column>
          <el-table-column prop="score" label="得分" width="80">
            <template #default="scope">
              <el-tag v-if="scope.row.score !== null && scope.row.score > 0" :type="getScoreType(scope.row.score)">
                {{ scope.row.score }}分
              </el-tag>
              <span v-else>-</span>
            </template>
          </el-table-column>
        </el-table>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { View, MoreFilled, Edit, Delete } from '@element-plus/icons-vue'

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
const loadingUnitContent = ref(false)
const unitContent = ref({
  texts: [],
  words: []
})

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
  assignmentType: 'UNIT',
  textIds: [],
  wordIds: [],
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
  assignmentType: [
    { required: true, message: '请选择任务类型', trigger: 'change' }
  ],
  textIds: [
    { required: true, message: '请至少选择一篇课文', trigger: 'change' }
  ],
  wordIds: [
    { required: true, message: '请至少选择一个单词', trigger: 'change' }
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
        'Authorization': `Bearer ${localStorage.getItem('token') || ''}`
      }
    })

    if (response.ok) {
      const data = await response.json()

      if (data.code === 200) {
        let assignmentsData = Array.isArray(data.data?.records) ? data.data.records : []

        if (assignmentsData.length === 0) {
          assignments.value = []
          return
        }

        // 为每个任务计算真实状态和统计信息
        assignmentsData = assignmentsData.map(assignment => {
          // 根据截止时间计算真实状态
          const realStatus = calculateRealStatus(assignment.deadline)

          // 获取班级学生数量
          const studentCount = getStudentCountForClass(assignment.classId)

          // 确保字段名称统一（处理可能的命名差异）
          return {
            ...assignment,
            className: assignment.className || assignment.class_name || '未知班级',
            unitName: assignment.unitName || assignment.unit_name || '未知单元',
            status: realStatus,
            studentCount: studentCount,
            completedCount: 0 // 先设置为0，后续异步获取
          }
        })

        // 批量获取完成人数
        assignmentsData = await batchGetCompletionCounts(assignmentsData)

        // 如果有状态筛选，进行过滤
        if (filterForm.status) {
          assignmentsData = assignmentsData.filter(assignment =>
            assignment.status === filterForm.status
          )
        }

        assignments.value = assignmentsData
      } else {
        console.error('API返回错误:', data)
        ElMessage.error(data.message || '加载任务列表失败')
      }
    } else {
      const errorText = await response.text()
      console.error('HTTP错误:', response.status, errorText)
      ElMessage.error('加载任务列表失败: ' + response.status)
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
        'Authorization': `Bearer ${localStorage.getItem('token') || ''}`
      }
    })

    if (response.ok) {
      const data = await response.json()
      if (data.code === 200) {
        classes.value = data.data?.records || []
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
  return Promise.resolve()
}

const loadUnits = async () => {
  try {
    const response = await fetch('/api/units?size=100', {
      headers: {
        'Authorization': `Bearer ${localStorage.getItem('token') || ''}`
      }
    })

    if (response.ok) {
      const data = await response.json()
      if (data.code === 200) {
        units.value = data.data?.records || []
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
  return Promise.resolve()
}

const loadUnitsForClass = async (classId) => {
  try {
    const classItem = classes.value.find(cls => cls.id === classId)
    if (!classItem || !classItem.courseId) {
      units.value = []
      return
    }

    const response = await fetch(`/api/units?courseId=${classItem.courseId}&size=100`, {
      headers: {
        'Authorization': `Bearer ${localStorage.getItem('token') || ''}`
      }
    })

    if (response.ok) {
      const data = await response.json()
      if (data.code === 200) {
        units.value = data.data?.records || []
      } else {
        ElMessage.error(data.message || '加载单元列表失败')
        units.value = []
      }
    } else {
      ElMessage.error('加载单元列表失败')
      units.value = []
    }
  } catch (error) {
    console.error('加载单元列表失败:', error)
    ElMessage.error('加载单元列表失败')
    units.value = []
  }
  return Promise.resolve()
}

const showCreateDialog = () => {
  isEdit.value = false
  dialogVisible.value = true
  resetAssignmentForm()
}

const editAssignment = async (assignment) => {
  isEdit.value = true
  dialogVisible.value = true

  Object.assign(assignmentForm, {
    id: assignment.id,
    title: assignment.title,
    classId: assignment.classId,
    unitId: assignment.unitId,
    description: assignment.description,
    deadline: new Date(assignment.deadline)
  })

  if (assignment.classId) {
    await loadUnitsForClass(assignment.classId)
  }
}

const resetAssignmentForm = () => {
  Object.assign(assignmentForm, {
    id: null,
    title: '',
    classId: null,
    unitId: null,
    assignmentType: 'UNIT',
    textIds: [],
    wordIds: [],
    description: '',
    deadline: null
  })
  unitContent.value = { texts: [], words: [] }
}

const loadUnitContent = async (unitId) => {
  if (!unitId) return

  loadingUnitContent.value = true
  try {
    const response = await fetch(`/api/assignments/unit/${unitId}/content`, {
      headers: {
        'Authorization': `Bearer ${localStorage.getItem('token') || ''}`
      }
    })

    if (response.ok) {
      const data = await response.json()
      if (data.code === 200) {
        unitContent.value = data.data || { texts: [], words: [] }
      } else {
        ElMessage.error(data.message || '加载单元内容失败')
        unitContent.value = { texts: [], words: [] }
      }
    } else {
      ElMessage.error('加载单元内容失败')
      unitContent.value = { texts: [], words: [] }
    }
  } catch (error) {
    console.error('加载单元内容失败:', error)
    ElMessage.error('加载单元内容失败')
    unitContent.value = { texts: [], words: [] }
  } finally {
    loadingUnitContent.value = false
  }
}

const onClassChange = async (classId) => {
  if (classId) {
    await loadUnitsForClass(classId)
  } else {
    units.value = []
  }
}

const onUnitChange = async (unitId) => {
  if (unitId && assignmentForm.assignmentType !== 'UNIT') {
    await loadUnitContent(unitId)
  } else {
    unitContent.value = { texts: [], words: [] }
  }
}

const onAssignmentTypeChange = async (assignmentType) => {
  if (assignmentType !== 'UNIT' && assignmentForm.unitId) {
    await loadUnitContent(assignmentForm.unitId)
  } else {
    unitContent.value = { texts: [], words: [] }
  }

  assignmentForm.textIds = []
  assignmentForm.wordIds = []
}

const saveAssignment = async () => {
  if (!assignmentFormRef.value) return

  await assignmentFormRef.value.validate(async (valid) => {
    if (valid) {
      saving.value = true
      try {
        if (assignmentForm.assignmentType === 'UNIT') {
          const url = isEdit.value ? `/api/assignments/${assignmentForm.id}` : '/api/assignments'
          const method = isEdit.value ? 'PUT' : 'POST'

          const response = await fetch(url, {
            method,
            headers: {
              'Content-Type': 'application/json',
              'Authorization': `Bearer ${localStorage.getItem('token') || ''}`
            },
            body: JSON.stringify({
              id: assignmentForm.id,
              title: assignmentForm.title,
              classId: assignmentForm.classId,
              unitId: assignmentForm.unitId,
              description: assignmentForm.description,
              deadline: assignmentForm.deadline
            })
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
        } else {
          const url = '/api/assignments/create-with-content'
          const response = await fetch(url, {
            method: 'POST',
            headers: {
              'Content-Type': 'application/json',
              'Authorization': `Bearer ${localStorage.getItem('token') || ''}`
            },
            body: JSON.stringify({
              title: assignmentForm.title,
              classId: assignmentForm.classId,
              unitId: assignmentForm.unitId,
              assignmentType: assignmentForm.assignmentType,
              textIds: assignmentForm.textIds,
              wordIds: assignmentForm.wordIds,
              description: assignmentForm.description,
              deadline: assignmentForm.deadline
            })
          })

          if (response.ok) {
            const data = await response.json()
            if (data.code === 200) {
              ElMessage.success('任务布置成功')
              dialogVisible.value = false
              loadAssignments()
            } else {
              ElMessage.error(data.message || '操作失败')
            }
          } else {
            ElMessage.error('操作失败')
          }
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
        'Authorization': `Bearer ${localStorage.getItem('token') || ''}`
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

const viewProgress = async (assignment) => {
  try {
    currentAssignment.value = assignment

    const response = await fetch(`/api/assignments/${assignment.id}/progress`, {
      headers: {
        'Authorization': `Bearer ${localStorage.getItem('token') || ''}`
      }
    })

    if (response.ok) {
      const data = await response.json()
      if (data.code === 200) {
        const progressData = Array.isArray(data.data) ? data.data : []
        studentProgress.value = progressData

        // 计算完成人数
        const completedCount = progressData.filter(student => student.completed).length
        currentAssignment.value = {
          ...assignment,
          completedCount: completedCount,
          studentCount: progressData.length
        }

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

const getCompletionType = (completed, total) => {
  if (!total || total === 0) return 'info'
  const rate = (completed / total) * 100
  if (rate >= 80) return 'success'
  if (rate >= 50) return 'warning'
  return 'danger'
}

// 处理下拉菜单命令
const handleDropdownCommand = (command, row) => {
  switch (command) {
    case 'edit':
      editAssignment(row)
      break
    case 'delete':
      deleteAssignment(row)
      break
  }
}

const getCompletionRate = (assignment) => {
  if (!assignment.studentCount) return 0
  return Math.round(((assignment.completedCount || 0) / assignment.studentCount) * 100)
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

// 计算任务真实状态
const calculateRealStatus = (deadline) => {
  if (!deadline) return 'active'

  const now = new Date()
  const deadlineDate = new Date(deadline)

  return now > deadlineDate ? 'ended' : 'active'
}

// 获取班级学生数量
const getStudentCountForClass = (classId) => {
  const classInfo = classes.value.find(cls => cls.id === classId)
  return classInfo ? (classInfo.studentCount || 0) : 0
}

// 任务完成人数缓存
const assignmentProgressCache = new Map()

// 获取任务完成人数
const getCompletedCountForAssignment = async (assignmentId) => {
  try {
    // 检查缓存
    if (assignmentProgressCache.has(assignmentId)) {
      const cached = assignmentProgressCache.get(assignmentId)
      // 缓存5分钟
      if (Date.now() - cached.timestamp < 5 * 60 * 1000) {
        return cached.completedCount
      }
    }

    const response = await fetch(`/api/assignments/${assignmentId}/progress`, {
      headers: {
        'Authorization': `Bearer ${localStorage.getItem('token') || ''}`
      }
    })

    if (response.ok) {
      const data = await response.json()
      if (data.code === 200 && Array.isArray(data.data)) {
        // 计算完成人数（进度>=80%）
        const completedCount = data.data.filter(student => student.completed).length

        // 缓存结果
        assignmentProgressCache.set(assignmentId, {
          completedCount: completedCount,
          timestamp: Date.now()
        })

        return completedCount
      }
    }
  } catch (error) {
    console.error(`获取任务${assignmentId}进度失败:`, error)
  }

  return 0
}

// 批量获取完成人数，控制并发数量
const batchGetCompletionCounts = async (assignmentsData) => {
  const BATCH_SIZE = 3
  const MAX_RETRIES = 2

  for (let i = 0; i < assignmentsData.length; i += BATCH_SIZE) {
    const batch = assignmentsData.slice(i, i + BATCH_SIZE)

    await Promise.all(batch.map(async (assignment) => {
      let retries = 0
      let completedCount = 0

      while (retries <= MAX_RETRIES) {
        try {
          completedCount = await getCompletedCountForAssignment(assignment.id)
          assignment.completedCount = completedCount
          break
        } catch (error) {
          retries++
          console.error(`获取任务${assignment.id}完成人数失败(第${retries}次重试):`, error)

          if (retries > MAX_RETRIES) {
            assignment.completedCount = 0
          } else {
            await new Promise(resolve => setTimeout(resolve, 500 * retries))
          }
        }
      }
    }))

    if (i + BATCH_SIZE < assignmentsData.length) {
      await new Promise(resolve => setTimeout(resolve, 100))
    }
  }

  return assignmentsData
}

const formatTime = (time) => {
  if (!time) return ''
  return new Date(time).toLocaleString()
}

const formatStudyTime = (minutes) => {
  if (!minutes || minutes === 0) return '0分钟'
  if (minutes < 60) {
    return `${minutes}分钟`
  }
  const hours = Math.floor(minutes / 60)
  const mins = minutes % 60
  return mins > 0 ? `${hours}小时${mins}分钟` : `${hours}小时`
}

// 监听班级选择变化，动态加载对应课程单元
watch(() => assignmentForm.classId, async (newClassId) => {
  if (newClassId) {
    await loadUnitsForClass(newClassId)
  } else {
    units.value = []
  }
})

// 生命周期
onMounted(async () => {
  await loadClasses()
  await loadUnits()
  await loadAssignments()
})
</script>

<style scoped>
.assignments-management {
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

.content-selector {
  max-height: 300px;
  overflow-y: auto;
  border: 1px solid #e4e7ed;
  border-radius: 4px;
  padding: 10px;
}

.content-grid {
  display: grid;
  gap: 10px;
}

.content-item {
  margin-bottom: 0 !important;
  padding: 10px;
  border: 1px solid #e4e7ed;
  border-radius: 4px;
  transition: all 0.3s;
}

.content-item:hover {
  border-color: #409eff;
  background-color: #f5f9ff;
}

.content-item.is-checked {
  border-color: #409eff;
  background-color: #f5f9ff;
}

.content-info {
  margin-left: 20px;
}

.content-title {
  font-weight: bold;
  font-size: 14px;
  color: #303133;
  margin-bottom: 5px;
}

.content-meta {
  font-size: 12px;
  color: #909399;
  margin-bottom: 3px;
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

.content-desc {
  font-size: 12px;
  color: #606266;
  line-height: 1.4;
  max-width: 500px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.content-selector::-webkit-scrollbar {
  width: 6px;
}

.content-selector::-webkit-scrollbar-track {
  background: #f1f1f1;
  border-radius: 3px;
}

.content-selector::-webkit-scrollbar-thumb {
  background: #c1c1c1;
  border-radius: 3px;
}

.content-selector::-webkit-scrollbar-thumb:hover {
  background: #a8a8a8;
}

@media (max-width: 768px) {
  .assignments-management {
    padding: 12px;
  }

  .filter-bar .el-form {
    display: block;
  }

  .filter-bar .el-form-item {
    margin-right: 0;
    margin-bottom: 12px;
  }
}
</style>

