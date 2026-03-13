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
      <div class="table-wrapper">
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
            {{ scope.row.deadline ? formatTime(scope.row.deadline) : '-' }}
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
        <!-- 在截止时间字段后添加以下两个字段 -->
        <el-form-item label="任务权重" prop="weight">
          <el-input-number
              v-model="assignmentForm.weight"
              :min="0.1"
              :max="10"
              :step="0.1"
              :precision="2"
              placeholder="任务权重（用于计算加权平均分，默认1.00）"
              style="width: 100%"
          />
          <div style="font-size: 12px; color: #999; margin-top: 4px;">
            权重用于计算加权平均分，例如：权重1.5的任务分数会按1.5倍计入总分
          </div>
        </el-form-item>

        <el-form-item label="理想学习时长" prop="idealStudyTimeMinutes">
          <el-input-number
              v-model="assignmentForm.idealStudyTimeMinutes"
              :min="5"
              :max="300"
              :step="5"
              placeholder="理想学习时长（分钟），用于计算分数，默认60分钟"
              style="width: 100%"
          />
          <div style="font-size: 12px; color: #999; margin-top: 4px;">
            设置学生完成此任务的理想学习时长。分数计算中，学习时长占40%权重，达到理想时长可获得满分。
          </div>
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
          <el-table-column prop="studyTime" label="学习时长" width="100">
            <template #default="scope">
              {{ scope.row.studyTime || 0 }}分钟
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
  deadline: null,
  weight: 1.0,  // 添加权重字段，默认1.0
  idealStudyTimeMinutes: 60  // 添加理想学习时长字段，默认60分钟
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
    {
      validator: (rule, value, callback) => {
        if (assignmentForm.assignmentType === 'TEXTS' && (!Array.isArray(value) || value.length === 0)) {
          callback(new Error('请至少选择一篇课文'))
        } else {
          callback()
        }
      },
      trigger: 'change'
    }
  ],
  wordIds: [
    {
      validator: (rule, value, callback) => {
        if (assignmentForm.assignmentType === 'WORDS' && (!Array.isArray(value) || value.length === 0)) {
          callback(new Error('请至少选择一个单词'))
        } else {
          callback()
        }
      },
      trigger: 'change'
    }
  ],
  deadline: [
    { required: true, message: '请选择截止时间', trigger: 'change' },
    {
      validator: (rule, value, callback) => {
        if (!value) {
          callback()
          return
        }
        const deadline = new Date(value)
        const now = new Date()
        // 编辑模式下，如果截止时间未改变，允许保留原值（即使已过期）
        // 新建模式或修改了截止时间时，必须晚于当前时间
        if (deadline <= now && !isEdit.value) {
          callback(new Error('截止时间必须晚于当前时间'))
        } else {
          callback()
        }
      },
      trigger: 'change'
    }
  ],
  weight: [
    { required: true, message: '请输入任务权重', trigger: 'blur' },
    {
      validator: (rule, value, callback) => {
        if (value < 0.1 || value > 10) {
          callback(new Error('任务权重必须在0.1到10之间'))
        } else if (Math.abs(value - Math.round(value * 100) / 100) > 0.001) {
          callback(new Error('任务权重最多保留两位小数'))
        } else {
          callback()
        }
      },
      trigger: 'blur'
    }
  ],
  idealStudyTimeMinutes: [
    { required: true, message: '请输入理想学习时长', trigger: 'blur' },
    {
      validator: (rule, value, callback) => {
        if (value < 5) {
          callback(new Error('理想学习时长不能少于5分钟'))
        } else if (value > 300) {
          callback(new Error('理想学习时长不能超过300分钟'))
        } else if (value % 5 !== 0) {
          callback(new Error('理想学习时长必须是5的倍数'))
        } else {
          callback()
        }
      },
      trigger: 'blur'
    }
  ],
  description: [
    { max: 500, message: '任务描述不能超过500个字符', trigger: 'blur' }
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

    const token = localStorage.getItem('token')
    const headers = {}
    if (token) headers['Authorization'] = `Bearer ${token}`

    const response = await fetch(`/api/assignments?${params.toString()}`, { headers })

    if (response.ok) {
      try {
        const data = await response.json()
        if (data.code === 200) {
          let assignmentsData = data.data?.records || []

          // 为每个任务计算真实状态和统计信息
          assignmentsData = assignmentsData.map(assignment => {
            // 根据截止时间计算真实状态
            const realStatus = calculateRealStatus(assignment.deadline)

            // 获取班级学生数量（同步方法，从已加载的班级数据获取）
            const studentCount = getStudentCountForClass(assignment.classId)

            return {
              ...assignment,
              status: realStatus,
              studentCount: studentCount,
              completedCount: 0 // 先设置为0，后续异步获取
            }
          })

          // 批量获取完成人数，避免过多并发请求
          assignmentsData = await batchGetCompletionCounts(assignmentsData)

          // 如果有状态筛选，进行过滤
          if (filterForm.status) {
            assignmentsData = assignmentsData.filter(assignment =>
              assignment.status === filterForm.status
            )
          }

          assignments.value = assignmentsData
        } else {
          ElMessage.error(data.message || '加载任务列表失败')
        }
      } catch (parseError) {
        console.error('任务列表解析失败:', parseError)
        ElMessage.error('加载任务列表失败')
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
    const token = localStorage.getItem('token')
    const headers = {}
    if (token) headers['Authorization'] = `Bearer ${token}`

    const response = await fetch('/api/classes?size=100', { headers })

    if (response.ok) {
      try {
        const data = await response.json()
        if (data.code === 200) {
          classes.value = data.data?.records || []
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
  // 确保总是返回Promise，即使出错也不会阻塞
  return Promise.resolve()
}

const loadUnits = async () => {
  try {
    const token = localStorage.getItem('token')
    const headers = {}
    if (token) headers['Authorization'] = `Bearer ${token}`

    const response = await fetch('/api/units?size=100', { headers })

    if (response.ok) {
      try {
        const data = await response.json()
        if (data.code === 200) {
          units.value = data.data?.records || []
        } else {
          ElMessage.error(data.message || '加载单元列表失败')
        }
      } catch (parseError) {
        console.error('单元列表解析失败:', parseError)
        ElMessage.error('加载单元列表失败')
      }
    } else {
      ElMessage.error('加载单元列表失败')
    }
  } catch (error) {
    console.error('加载单元列表失败:', error)
    ElMessage.error('加载单元列表失败')
  }
  // 确保总是返回Promise，即使出错也不会阻塞
  return Promise.resolve()
}

const loadUnitsForClass = async (classId) => {
  try {
    // 根据班级ID获取对应的课程ID
    const classItem = classes.value.find(cls => cls.id === classId)
    if (!classItem || !classItem.courseId) {
      units.value = []
      return
    }

    const token = localStorage.getItem('token')
    const headers = {}
    if (token) headers['Authorization'] = `Bearer ${token}`

    const response = await fetch(`/api/units?courseId=${classItem.courseId}&size=100`, { headers })

    if (response.ok) {
      try {
        const data = await response.json()
        if (data.code === 200) {
          units.value = data.data?.records || []
        } else {
          ElMessage.error(data.message || '加载单元列表失败')
          units.value = []
        }
      } catch (parseError) {
        console.error('单元列表解析失败:', parseError)
        ElMessage.error('加载单元列表失败')
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

  // 设置表单数据（包含 assignmentType/textIds/wordIds 以便正确展示表单）
  Object.assign(assignmentForm, {
    id: assignment.id,
    title: assignment.title,
    classId: assignment.classId,
    unitId: assignment.unitId,
    assignmentType: assignment.assignmentType || 'UNIT',
    textIds: Array.isArray(assignment.textIds) ? [...assignment.textIds] : [],
    wordIds: Array.isArray(assignment.wordIds) ? [...assignment.wordIds] : [],
    description: assignment.description,
    deadline: assignment.deadline ? new Date(assignment.deadline) : null,
    weight: assignment.weight || 1.0,  // 添加权重
    idealStudyTimeMinutes: assignment.idealStudyTimeMinutes || 60  // 添加理想学习时长
  })

  // 加载对应班级的单元列表
  if (assignment.classId) {
    await loadUnitsForClass(assignment.classId)
  }

  // 如果是 TEXTS 或 WORDS 类型，加载单元内容以便编辑
  if (assignment.unitId && (assignment.assignmentType === 'TEXTS' || assignment.assignmentType === 'WORDS')) {
    await loadUnitContent(assignment.unitId)
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
    deadline: null,
    weight: 1.0,  // 重置权重
    idealStudyTimeMinutes: 60  // 重置理想学习时长
  })
  unitContent.value = { texts: [], words: [] }
}

// 加载单元内容
const loadUnitContent = async (unitId) => {
  if (!unitId) return

  loadingUnitContent.value = true
  try {
    const token = localStorage.getItem('token')
    const headers = {}
    if (token) headers['Authorization'] = `Bearer ${token}`

    const response = await fetch(`/api/assignments/unit/${unitId}/content`, { headers })

    if (response.ok) {
      try {
        const data = await response.json()
        if (data.code === 200) {
          unitContent.value = data.data || { texts: [], words: [] }
        } else {
          ElMessage.error(data.message || '加载单元内容失败')
          unitContent.value = { texts: [], words: [] }
        }
      } catch (parseError) {
        console.error('单元内容解析失败:', parseError)
        ElMessage.error('加载单元内容失败')
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

// 单元变化事件
const onUnitChange = async (unitId) => {
  if (unitId && assignmentForm.assignmentType !== 'UNIT') {
    await loadUnitContent(unitId)
  } else {
    unitContent.value = { texts: [], words: [] }
  }
}

// 任务类型变化事件
const onAssignmentTypeChange = async (assignmentType) => {
  if (assignmentType !== 'UNIT' && assignmentForm.unitId) {
    await loadUnitContent(assignmentForm.unitId)
  } else {
    unitContent.value = { texts: [], words: [] }
  }

  // 清空已选择的内容
  assignmentForm.textIds = []
  assignmentForm.wordIds = []
}

const saveAssignment = async () => {
  if (!assignmentFormRef.value) return

  await assignmentFormRef.value.validate(async (valid) => {
    if (valid) {
      saving.value = true
      try {
        // 修复：提取token和fetchHeaders到try块开始处，避免在if-else分支中重复声明
        const token = localStorage.getItem('token')
        const fetchHeaders = { 'Content-Type': 'application/json' }
        if (token) fetchHeaders['Authorization'] = `Bearer ${token}`

        // 根据任务类型选择不同的接口
        if (assignmentForm.assignmentType === 'UNIT') {
          // 兼容原有的单元任务接口
          const url = isEdit.value ? `/api/assignments/${assignmentForm.id}` : '/api/assignments'
          const method = isEdit.value ? 'PUT' : 'POST'

          const response = await fetch(url, {
            method,
            headers: fetchHeaders,
            body: JSON.stringify({
              id: assignmentForm.id,
              title: assignmentForm.title,
              classId: assignmentForm.classId,
              unitId: assignmentForm.unitId,
              description: assignmentForm.description,
              deadline: assignmentForm.deadline,
              weight: assignmentForm.weight || 1.0,  // 添加权重
              idealStudyTimeMinutes: assignmentForm.idealStudyTimeMinutes || 60  // 添加理想学习时长
            })
          })

          if (response.ok) {
            try {
              const data = await response.json()
              if (data.code === 200) {
                ElMessage.success(isEdit.value ? '任务更新成功' : '任务布置成功')
                dialogVisible.value = false
                loadAssignments()
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
        } else {
          // 使用新的内容任务接口
          const url = '/api/assignments/create-with-content'

          const response = await fetch(url, {
            method: 'POST',
            headers: fetchHeaders,
            body: JSON.stringify({
              title: assignmentForm.title,
              classId: assignmentForm.classId,
              unitId: assignmentForm.unitId,
              assignmentType: assignmentForm.assignmentType,
              textIds: assignmentForm.textIds,
              wordIds: assignmentForm.wordIds,
              description: assignmentForm.description,
              deadline: assignmentForm.deadline,
              weight: assignmentForm.weight || 1.0,  // 添加权重
              idealStudyTimeMinutes: assignmentForm.idealStudyTimeMinutes || 60  // 添加理想学习时长
            })
          })

          if (response.ok) {
            try {
              const data = await response.json()
              if (data.code === 200) {
                ElMessage.success('任务布置成功')
                dialogVisible.value = false
                loadAssignments()
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

    const token = localStorage.getItem('token')
    const headers = {}
    if (token) headers['Authorization'] = `Bearer ${token}`

    const response = await fetch(`/api/assignments/${assignment.id}/progress`, { headers })

    if (response.ok) {
      try {
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
      } catch (parseError) {
        console.error('进度信息解析失败:', parseError)
        ElMessage.error('加载进度信息失败')
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

    const token = localStorage.getItem('token')
    const headers = {}
    if (token) headers['Authorization'] = `Bearer ${token}`

    const response = await fetch(`/api/assignments/${assignment.id}`, {
      method: 'DELETE',
      headers
    })

    if (response.ok) {
      try {
        const data = await response.json()
        if (data.code === 200) {
          ElMessage.success('任务删除成功')
          loadAssignments()
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
      ElMessage.error('删除失败')
    }
  }
}

const getCompletionType = (completed, total) => {
  if (!total || completed == null) return 'info'
  const rate = (completed / total) * 100
  if (rate >= 80) return 'success'
  if (rate >= 50) return 'warning'
  return 'danger'
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
  // 从班级列表中获取学生数量
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

    const token = localStorage.getItem('token')
    const headers = {}
    if (token) headers['Authorization'] = `Bearer ${token}`

    const response = await fetch(`/api/assignments/${assignmentId}/progress`, { headers })

    if (response.ok) {
      try {
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
      } catch (parseError) {
        console.error(`任务${assignmentId}进度解析失败:`, parseError)
      }
    }
  } catch (error) {
    console.error(`获取任务${assignmentId}进度失败:`, error)
  }

  return 0
}

// 批量获取完成人数，控制并发数量
const batchGetCompletionCounts = async (assignmentsData) => {
  const BATCH_SIZE = 3 // 每批处理3个任务，避免过多并发请求
  const MAX_RETRIES = 2 // 最大重试次数

  for (let i = 0; i < assignmentsData.length; i += BATCH_SIZE) {
    const batch = assignmentsData.slice(i, i + BATCH_SIZE)

    await Promise.all(batch.map(async (assignment) => {
      let retries = 0
      let completedCount = 0

      while (retries <= MAX_RETRIES) {
        try {
          completedCount = await getCompletedCountForAssignment(assignment.id)
          assignment.completedCount = completedCount
          break // 成功则跳出重试循环
        } catch (error) {
          retries++
          console.error(`获取任务${assignment.id}完成人数失败(第${retries}次重试):`, error)

          if (retries > MAX_RETRIES) {
            console.error(`任务${assignment.id}完成人数获取失败，使用默认值0`)
            assignment.completedCount = 0
          } else {
            // 重试前等待（注意：此 setTimeout 会自动在 Promise resolve 后清理）
            await new Promise(resolve => {
              const timeoutId = setTimeout(resolve, 500 * retries)
              // 如果需要主动清理，可以在组件卸载时调用
            })
          }
        }
      }
    }))

    // 在批次之间添加小延迟，避免服务器压力
    if (i + BATCH_SIZE < assignmentsData.length) {
      await new Promise(resolve => {
        const timeoutId = setTimeout(resolve, 100)
        // 如果需要主动清理，可以在组件卸载时调用
      })
    }
  }

  return assignmentsData
}

const formatTime = (time) => {
  if (!time) return ''
  return new Date(time).toLocaleString()
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
  // 先加载班级数据，再加载任务数据
  await loadClasses()
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

.content-desc {
  font-size: 12px;
  color: #606266;
  line-height: 1.4;
  max-width: 500px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 美化复选框样式 */
.content-item .el-checkbox__input.is-checked .el-checkbox__inner {
  background-color: #409eff;
  border-color: #409eff;
}

/* 滚动条样式 */
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