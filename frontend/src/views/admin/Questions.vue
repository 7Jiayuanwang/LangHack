<template>
  <div class="questions-container">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>题库管理</span>
          <el-button type="primary" @click="handleAdd">
            <el-icon><Plus /></el-icon>
            添加题目
          </el-button>
        </div>
      </template>

      <!-- 搜索表单 -->
      <el-form :inline="true" :model="searchForm" class="search-form">
        <el-form-item label="题型">
          <el-select v-model="searchForm.questionType" placeholder="请选择题型" clearable style="width: 150px">
            <el-option label="听力" value="听力" />
            <el-option label="阅读" value="阅读" />
            <el-option label="完形填空" value="完形填空" />
            <el-option label="翻译" value="翻译" />
            <el-option label="写作" value="写作" />
          </el-select>
        </el-form-item>
        <el-form-item label="考试类型">
          <el-select v-model="searchForm.examType" placeholder="请选择考试类型" clearable style="width: 150px">
            <el-option label="四六级" value="CET" />
            <el-option label="考研" value="POSTGRADUATE" />
            <el-option label="雅思" value="IELTS" />
          </el-select>
        </el-form-item>
        <el-form-item label="难度">
          <el-select v-model="searchForm.difficulty" placeholder="请选择难度" clearable style="width: 120px">
            <el-option label="简单" value="EASY" />
            <el-option label="中等" value="MEDIUM" />
            <el-option label="困难" value="HARD" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">
            <el-icon><Search /></el-icon>
            查询
          </el-button>
          <el-button @click="handleReset">
            <el-icon><Refresh /></el-icon>
            重置
          </el-button>
        </el-form-item>
      </el-form>

      <!-- 题目列表 -->
      <el-table
        :data="questionList"
        v-loading="loading"
        border
        style="width: 100%"
        @selection-change="handleSelectionChange"
      >
        <el-table-column type="selection" width="55" />
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="title" label="题目" min-width="300" show-overflow-tooltip>
          <template #default="{ row }">
            <div class="question-title">{{ row.title }}</div>
          </template>
        </el-table-column>
        <el-table-column prop="questionType" label="题型" width="100">
          <template #default="{ row }">
            <el-tag :type="getQuestionTypeTag(row.questionType)">
              {{ row.questionType }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="examType" label="考试类型" width="100">
          <template #default="{ row }">
            <el-tag type="info">{{ getExamTypeLabel(row.examType) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="difficulty" label="难度" width="100">
          <template #default="{ row }">
            <el-tag :type="getDifficultyTag(row.difficulty)">
              {{ getDifficultyLabel(row.difficulty) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="score" label="分值" width="80" />
        <el-table-column prop="createdTime" label="创建时间" width="180" />
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" size="small" @click="handleEdit(row)">编辑</el-button>
            <el-button type="danger" size="small" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 批量操作 -->
      <div class="batch-operations" v-if="selectedQuestions.length > 0">
        <el-button type="danger" @click="handleBatchDelete">
          批量删除 ({{ selectedQuestions.length }})
        </el-button>
      </div>

      <!-- 分页 -->
      <el-pagination
        v-model:current-page="pagination.page"
        v-model:page-size="pagination.size"
        :total="pagination.total"
        :page-sizes="[10, 20, 50, 100]"
        layout="total, sizes, prev, pager, next, jumper"
        @size-change="handleSizeChange"
        @current-change="handlePageChange"
        style="margin-top: 20px; justify-content: flex-end"
      />
    </el-card>

    <!-- 添加/编辑对话框 -->
    <el-dialog
      v-model="dialogVisible"
      :title="dialogTitle"
      width="70%"
      :close-on-click-modal="false"
    >
      <el-form :model="questionForm" :rules="questionRules" ref="questionFormRef" label-width="120px">
        <el-form-item label="题目内容" prop="title">
          <el-input
            v-model="questionForm.title"
            type="textarea"
            :rows="4"
            placeholder="请输入题目内容"
          />
        </el-form-item>

        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="题型" prop="questionType">
              <el-select v-model="questionForm.questionType" placeholder="请选择题型" style="width: 100%">
                <el-option label="听力" value="听力" />
                <el-option label="阅读" value="阅读" />
                <el-option label="完形填空" value="完形填空" />
                <el-option label="翻译" value="翻译" />
                <el-option label="写作" value="写作" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="考试类型" prop="examType">
              <el-select v-model="questionForm.examType" placeholder="请选择考试类型" style="width: 100%">
                <el-option label="四六级" value="CET" />
                <el-option label="考研" value="POSTGRADUATE" />
                <el-option label="雅思" value="IELTS" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="难度" prop="difficulty">
              <el-select v-model="questionForm.difficulty" placeholder="请选择难度" style="width: 100%">
                <el-option label="简单" value="EASY" />
                <el-option label="中等" value="MEDIUM" />
                <el-option label="困难" value="HARD" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="考试级别" prop="examLevel">
              <el-input v-model="questionForm.examLevel" placeholder="如: CET4, CET6" />
            </el-form-item>
          </el-col>
        </el-row>

        <!-- 选项（仅选择题使用） -->
        <el-form-item label="题目选项" v-if="questionForm.questionType !== '翻译' && questionForm.questionType !== '写作'">
          <el-card v-for="(option, index) in questionForm.options" :key="index" class="option-card">
            <el-row :gutter="10">
              <el-col :span="20">
                <el-input v-model="questionForm.options[index]" :placeholder="`选项 ${String.fromCharCode(65 + index)}`" />
              </el-col>
              <el-col :span="4">
                <el-button type="danger" size="small" @click="removeOption(index)" v-if="questionForm.options.length > 2">删除</el-button>
              </el-col>
            </el-row>
          </el-card>
          <el-button type="primary" size="small" @click="addOption" style="margin-top: 10px">
            <el-icon><Plus /></el-icon>
            添加选项
          </el-button>
        </el-form-item>

        <el-form-item label="正确答案" prop="correctAnswer">
          <el-input v-model="questionForm.correctAnswer" placeholder="请输入正确答案，如: A 或具体答案内容" />
        </el-form-item>

        <el-form-item label="答案解析" prop="explanation">
          <el-input
            v-model="questionForm.explanation"
            type="textarea"
            :rows="3"
            placeholder="请输入答案解析"
          />
        </el-form-item>

        <el-form-item label="关联知识点" prop="knowledgePointIds">
          <el-select
            v-model="questionForm.knowledgePointIds"
            multiple
            filterable
            placeholder="请选择关联知识点"
            style="width: 100%"
          >
            <el-option
              v-for="kp in knowledgePoints"
              :key="kp.id"
              :label="`${kp.name} (${kp.category})`"
              :value="kp.id"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="分值" prop="score">
          <el-input-number v-model="questionForm.score" :min="1" :max="100" />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSubmit" :loading="submitting">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Search, Refresh } from '@element-plus/icons-vue'
import {
  getQuestionList,
  createQuestion,
  updateQuestion,
  deleteQuestion,
  batchDeleteQuestions
} from '@/api/question'
import { getKnowledgePointList } from '@/api/knowledgePoint'

const loading = ref(false)
const submitting = ref(false)
const questionList = ref([])
const selectedQuestions = ref([])
const knowledgePoints = ref([])

const searchForm = reactive({
  questionType: '',
  examType: '',
  difficulty: ''
})

const pagination = reactive({
  page: 1,
  size: 10,
  total: 0
})

const dialogVisible = ref(false)
const dialogTitle = ref('添加题目')
const questionFormRef = ref()

const questionForm = reactive({
  id: null,
  title: '',
  questionType: '',
  difficulty: '',
  examType: 'CET',
  examLevel: '',
  options: ['', '', '', ''], // A, B, C, D
  correctAnswer: '',
  explanation: '',
  knowledgePointIds: [],
  score: 1
})

const questionRules = {
  title: [{ required: true, message: '请输入题目内容', trigger: 'blur' }],
  questionType: [{ required: true, message: '请选择题型', trigger: 'change' }],
  difficulty: [{ required: true, message: '请选择难度', trigger: 'change' }],
  examType: [{ required: true, message: '请选择考试类型', trigger: 'change' }],
  correctAnswer: [{ required: true, message: '请输入正确答案', trigger: 'blur' }],
  score: [{ required: true, message: '请输入分值', trigger: 'blur' }]
}

onMounted(() => {
  fetchQuestions()
  fetchKnowledgePoints()
})

const fetchQuestions = async () => {
  loading.value = true
  try {
    const params = {
      page: pagination.page,
      size: pagination.size,
      ...searchForm
    }
    const res = await getQuestionList(params)
    questionList.value = res.data.records || res.data || []
    pagination.total = res.data.total || (res.data ? res.data.length : 0)
  } catch (error) {
    console.error('获取题目列表失败，使用空列表', error)
    questionList.value = []
    pagination.total = 0
    // 不显示错误消息，避免后端API未实现时频繁提示
  } finally {
    loading.value = false
  }
}

const fetchKnowledgePoints = async () => {
  try {
    const res = await getKnowledgePointList({ page: 1, size: 1000 })
    knowledgePoints.value = res.data.records || res.data || []
  } catch (error) {
    console.error('获取知识点列表失败，使用空列表', error)
    knowledgePoints.value = []
  }
}

const handleSearch = () => {
  pagination.page = 1
  fetchQuestions()
}

const handleReset = () => {
  searchForm.questionType = ''
  searchForm.examType = ''
  searchForm.difficulty = ''
  handleSearch()
}

const handleSizeChange = () => {
  fetchQuestions()
}

const handlePageChange = () => {
  fetchQuestions()
}

const handleAdd = () => {
  dialogTitle.value = '添加题目'
  resetForm()
  dialogVisible.value = true
}

const handleEdit = (row) => {
  dialogTitle.value = '编辑题目'
  questionForm.id = row.id
  questionForm.title = row.title
  questionForm.questionType = row.questionType
  questionForm.difficulty = row.difficulty
  questionForm.examType = row.examType
  questionForm.examLevel = row.examLevel
  questionForm.options = row.options ? JSON.parse(row.options) : ['', '', '', '']
  questionForm.correctAnswer = row.correctAnswer
  questionForm.explanation = row.explanation
  questionForm.knowledgePointIds = row.knowledgePointIds ? row.knowledgePointIds.split(',').map(Number) : []
  questionForm.score = row.score
  dialogVisible.value = true
}

const handleDelete = async (row) => {
  try {
    await ElMessageBox.confirm('确认删除该题目？', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await deleteQuestion(row.id)
    ElMessage.success('删除成功')
    fetchQuestions()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('删除失败')
    }
  }
}

const handleSelectionChange = (selection) => {
  selectedQuestions.value = selection
}

const handleBatchDelete = async () => {
  try {
    await ElMessageBox.confirm(`确认删除选中的 ${selectedQuestions.value.length} 个题目？`, '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    const ids = selectedQuestions.value.map(item => item.id)
    await batchDeleteQuestions(ids)
    ElMessage.success('批量删除成功')
    fetchQuestions()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('批量删除失败')
    }
  }
}

const addOption = () => {
  questionForm.options.push('')
}

const removeOption = (index) => {
  questionForm.options.splice(index, 1)
}

const handleSubmit = async () => {
  await questionFormRef.value.validate(async (valid) => {
    if (valid) {
      submitting.value = true
      try {
        const data = {
          ...questionForm,
          options: JSON.stringify(questionForm.options.filter(opt => opt.trim() !== '')),
          knowledgePointIds: questionForm.knowledgePointIds.join(',')
        }

        if (questionForm.id) {
          await updateQuestion(questionForm.id, data)
          ElMessage.success('更新成功')
        } else {
          await createQuestion(data)
          ElMessage.success('添加成功')
        }

        dialogVisible.value = false
        fetchQuestions()
      } catch (error) {
        ElMessage.error(questionForm.id ? '更新失败' : '添加失败')
      } finally {
        submitting.value = false
      }
    }
  })
}

const resetForm = () => {
  questionForm.id = null
  questionForm.title = ''
  questionForm.questionType = ''
  questionForm.difficulty = ''
  questionForm.examType = 'CET'
  questionForm.examLevel = ''
  questionForm.options = ['', '', '', '']
  questionForm.correctAnswer = ''
  questionForm.explanation = ''
  questionForm.knowledgePointIds = []
  questionForm.score = 1
}

const getQuestionTypeTag = (type) => {
  const typeMap = {
    '听力': 'success',
    '阅读': 'primary',
    '完形填空': 'warning',
    '翻译': 'info',
    '写作': 'danger'
  }
  return typeMap[type] || ''
}

const getDifficultyTag = (difficulty) => {
  const map = {
    'EASY': 'success',
    'MEDIUM': 'warning',
    'HARD': 'danger'
  }
  return map[difficulty] || 'info'
}

const getDifficultyLabel = (difficulty) => {
  const map = {
    'EASY': '简单',
    'MEDIUM': '中等',
    'HARD': '困难'
  }
  return map[difficulty] || difficulty
}

const getExamTypeLabel = (examType) => {
  const map = {
    'CET': '四六级',
    'POSTGRADUATE': '考研',
    'IELTS': '雅思'
  }
  return map[examType] || examType
}
</script>

<style scoped>
.questions-container {
  height: 100%;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.search-form {
  margin-bottom: 20px;
}

.question-title {
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.batch-operations {
  margin-top: 10px;
  padding: 10px;
  background-color: #f5f7fa;
  border-radius: 4px;
}

.option-card {
  margin-bottom: 10px;
}

:deep(.el-card__body) {
  padding: 10px;
}
</style>
