<template>
  <div class="knowledge-points-container">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>知识点管理</span>
          <el-button type="primary" @click="handleAdd">
            <el-icon><Plus /></el-icon>
            添加知识点
          </el-button>
        </div>
      </template>

      <!-- 搜索表单 -->
      <el-form :inline="true" :model="searchForm" class="search-form">
        <el-form-item label="考试类型">
          <el-select v-model="searchForm.examType" placeholder="请选择考试类型" @change="handleSearch" style="width: 150px">
            <el-option label="全部" value="" />
            <el-option label="四六级" value="CET" />
            <el-option label="考研" value="POSTGRADUATE" />
            <el-option label="雅思" value="IELTS" />
          </el-select>
        </el-form-item>
        <el-form-item label="分类">
          <el-select v-model="searchForm.category" placeholder="请选择分类" clearable style="width: 150px">
            <el-option label="词汇" value="词汇" />
            <el-option label="语法" value="语法" />
            <el-option label="听力" value="听力" />
            <el-option label="阅读" value="阅读" />
            <el-option label="写作" value="写作" />
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

      <!-- 知识点树形表格 -->
      <el-table
        :data="knowledgePointList"
        v-loading="loading"
        row-key="id"
        border
        default-expand-all
        :tree-props="{ children: 'children', hasChildren: 'hasChildren' }"
        style="width: 100%"
      >
        <el-table-column prop="name" label="知识点名称" min-width="200" />
        <el-table-column prop="code" label="编码" width="180" />
        <el-table-column prop="category" label="分类" width="100">
          <template #default="{ row }">
            <el-tag :type="getCategoryTag(row.category)">
              {{ row.category }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="examType" label="考试类型" width="120">
          <template #default="{ row }">
            <el-tag type="info">{{ getExamTypeLabel(row.examType) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="level" label="层级" width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="row.level === 1 ? 'danger' : 'success'" size="small">
              {{ row.level === 1 ? '一级' : '二级' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="importance" label="重要程度" width="120" align="center">
          <template #default="{ row }">
            <el-progress :percentage="row.importance" :color="getImportanceColor(row.importance)" />
          </template>
        </el-table-column>
        <el-table-column prop="description" label="描述" min-width="200" show-overflow-tooltip />
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" size="small" @click="handleEdit(row)">编辑</el-button>
            <el-button type="danger" size="small" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

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
      width="600px"
      :close-on-click-modal="false"
    >
      <el-form :model="kpForm" :rules="kpRules" ref="kpFormRef" label-width="120px">
        <el-form-item label="知识点名称" prop="name">
          <el-input v-model="kpForm.name" placeholder="请输入知识点名称" />
        </el-form-item>

        <el-form-item label="知识点编码" prop="code">
          <el-input v-model="kpForm.code" placeholder="如: CET_VOCAB_CORE" />
        </el-form-item>

        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="分类" prop="category">
              <el-select v-model="kpForm.category" placeholder="请选择分类" style="width: 100%">
                <el-option label="词汇" value="词汇" />
                <el-option label="语法" value="语法" />
                <el-option label="听力" value="听力" />
                <el-option label="阅读" value="阅读" />
                <el-option label="写作" value="写作" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="考试类型" prop="examType">
              <el-select v-model="kpForm.examType" placeholder="请选择考试类型" style="width: 100%">
                <el-option label="四六级" value="CET" />
                <el-option label="考研" value="POSTGRADUATE" />
                <el-option label="雅思" value="IELTS" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="层级" prop="level">
              <el-select v-model="kpForm.level" placeholder="请选择层级" style="width: 100%" @change="handleLevelChange">
                <el-option label="一级分类" :value="1" />
                <el-option label="二级分类" :value="2" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="重要程度" prop="importance">
              <el-slider v-model="kpForm.importance" :min="1" :max="100" show-input />
            </el-form-item>
          </el-col>
        </el-row>

        <el-form-item label="父级知识点" prop="parentId" v-if="kpForm.level === 2">
          <el-select v-model="kpForm.parentId" placeholder="请选择父级知识点" filterable style="width: 100%">
            <el-option
              v-for="parent in parentKnowledgePoints"
              :key="parent.id"
              :label="parent.name"
              :value="parent.id"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="描述" prop="description">
          <el-input
            v-model="kpForm.description"
            type="textarea"
            :rows="3"
            placeholder="请输入知识点描述"
          />
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
import { ref, reactive, onMounted, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Search, Refresh } from '@element-plus/icons-vue'
import {
  getKnowledgePointList,
  createKnowledgePoint,
  updateKnowledgePoint,
  deleteKnowledgePoint
} from '@/api/knowledgePoint'

const loading = ref(false)
const submitting = ref(false)
const knowledgePointList = ref([])
const allKnowledgePoints = ref([])

const searchForm = reactive({
  examType: 'CET',
  category: ''
})

const pagination = reactive({
  page: 1,
  size: 100,
  total: 0
})

const dialogVisible = ref(false)
const dialogTitle = ref('添加知识点')
const kpFormRef = ref()

const kpForm = reactive({
  id: null,
  name: '',
  code: '',
  category: '',
  examType: 'CET',
  level: 1,
  parentId: null,
  description: '',
  importance: 50
})

const kpRules = {
  name: [{ required: true, message: '请输入知识点名称', trigger: 'blur' }],
  code: [{ required: true, message: '请输入知识点编码', trigger: 'blur' }],
  category: [{ required: true, message: '请选择分类', trigger: 'change' }],
  examType: [{ required: true, message: '请选择考试类型', trigger: 'change' }],
  level: [{ required: true, message: '请选择层级', trigger: 'change' }],
  parentId: [{ required: true, message: '请选择父级知识点', trigger: 'change' }]
}

// 父级知识点列表（仅一级）
const parentKnowledgePoints = computed(() => {
  return allKnowledgePoints.value.filter(kp => kp.level === 1)
})

onMounted(() => {
  fetchKnowledgePoints()
})

const fetchKnowledgePoints = async () => {
  loading.value = true
  try {
    const params = {
      page: 1,
      size: 1000,
      ...searchForm
    }
    const res = await getKnowledgePointList(params)
    allKnowledgePoints.value = res.data.records || res.data || []

    // 构建树形结构
    knowledgePointList.value = buildTree(allKnowledgePoints.value)
    pagination.total = allKnowledgePoints.value.length
  } catch (error) {
    console.error('获取知识点列表失败，使用空列表', error)
    allKnowledgePoints.value = []
    knowledgePointList.value = []
    pagination.total = 0
    // 不显示错误消息，避免后端API未实现时频繁提示
  } finally {
    loading.value = false
  }
}

// 构建树形结构
const buildTree = (list) => {
  if (!list || list.length === 0) return []

  const tree = []
  const map = {}

  // 先创建映射
  list.forEach(item => {
    map[item.id] = { ...item, children: [] }
  })

  // 构建树
  list.forEach(item => {
    if (item.parentId && map[item.parentId]) {
      map[item.parentId].children.push(map[item.id])
    } else if (item.level === 1) {
      tree.push(map[item.id])
    }
  })

  return tree
}

const handleSearch = () => {
  pagination.page = 1
  fetchKnowledgePoints()
}

const handleReset = () => {
  searchForm.examType = 'CET'
  searchForm.category = ''
  handleSearch()
}

const handleSizeChange = () => {
  fetchKnowledgePoints()
}

const handlePageChange = () => {
  fetchKnowledgePoints()
}

const handleAdd = () => {
  dialogTitle.value = '添加知识点'
  resetForm()
  dialogVisible.value = true
}

const handleEdit = (row) => {
  dialogTitle.value = '编辑知识点'
  kpForm.id = row.id
  kpForm.name = row.name
  kpForm.code = row.code
  kpForm.category = row.category
  kpForm.examType = row.examType
  kpForm.level = row.level
  kpForm.parentId = row.parentId
  kpForm.description = row.description
  kpForm.importance = row.importance
  dialogVisible.value = true
}

const handleDelete = async (row) => {
  try {
    await ElMessageBox.confirm(
      row.children && row.children.length > 0
        ? '该知识点下有子知识点，删除后子知识点也会被删除，确认删除？'
        : '确认删除该知识点？',
      '提示',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )
    await deleteKnowledgePoint(row.id)
    ElMessage.success('删除成功')
    fetchKnowledgePoints()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('删除失败')
    }
  }
}

const handleLevelChange = () => {
  if (kpForm.level === 1) {
    kpForm.parentId = null
  }
}

const handleSubmit = async () => {
  await kpFormRef.value.validate(async (valid) => {
    if (valid) {
      submitting.value = true
      try {
        const data = { ...kpForm }

        if (kpForm.id) {
          await updateKnowledgePoint(kpForm.id, data)
          ElMessage.success('更新成功')
        } else {
          await createKnowledgePoint(data)
          ElMessage.success('添加成功')
        }

        dialogVisible.value = false
        fetchKnowledgePoints()
      } catch (error) {
        ElMessage.error(kpForm.id ? '更新失败' : '添加失败')
      } finally {
        submitting.value = false
      }
    }
  })
}

const resetForm = () => {
  kpForm.id = null
  kpForm.name = ''
  kpForm.code = ''
  kpForm.category = ''
  kpForm.examType = 'CET'
  kpForm.level = 1
  kpForm.parentId = null
  kpForm.description = ''
  kpForm.importance = 50
}

const getCategoryTag = (category) => {
  const map = {
    '词汇': 'success',
    '语法': 'primary',
    '听力': 'warning',
    '阅读': 'info',
    '写作': 'danger'
  }
  return map[category] || ''
}

const getExamTypeLabel = (examType) => {
  const map = {
    'CET': '四六级',
    'POSTGRADUATE': '考研',
    'IELTS': '雅思'
  }
  return map[examType] || examType
}

const getImportanceColor = (importance) => {
  if (importance >= 90) return '#f56c6c'
  if (importance >= 70) return '#e6a23c'
  if (importance >= 50) return '#409eff'
  return '#67c23a'
}
</script>

<style scoped>
.knowledge-points-container {
  padding: 20px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.search-form {
  margin-bottom: 20px;
}
</style>
