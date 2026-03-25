<template>
  <div class="unit-words-management">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>
            单元单词管理
            <el-tag v-if="courseName" type="info" style="margin-left: 10px">{{ courseName }}</el-tag>
            <el-tag v-if="unitName" type="success" style="margin-left: 5px">{{ unitName }}</el-tag>
          </span>
          <div>
            <el-button @click="backToUnits">返回单元列表</el-button>
            <el-button type="primary" @click="showAddDialog">添加单词</el-button>
          </div>
        </div>
      </template>

      <!-- 搜索栏 -->
      <div class="search-bar">
        <el-form :inline="true" :model="searchForm" class="search-form">
          <el-form-item label="关键词">
            <el-input v-model="searchForm.keyword" placeholder="单词/释义" clearable />
          </el-form-item>
          <el-form-item label="难度">
            <el-select v-model="searchForm.difficulty" placeholder="请选择" clearable style="width: 120px">
              <el-option label="简单" value="EASY" />
              <el-option label="中等" value="MEDIUM" />
              <el-option label="困难" value="HARD" />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" @click="handleSearch">搜索</el-button>
            <el-button @click="resetSearch">重置</el-button>
          </el-form-item>
        </el-form>
      </div>

      <!-- 单词列表 -->
      <div class="table-wrapper">
      <el-table :data="words" stripe>
        <el-table-column prop="word" label="单词" width="120" />
        <el-table-column prop="pronunciation" label="音标" width="150" />
        <el-table-column prop="definition" label="释义" show-overflow-tooltip />
        <el-table-column prop="exampleSentence" label="例句" show-overflow-tooltip />
        <el-table-column prop="partOfSpeech" label="词性" width="100" />
        <el-table-column prop="difficulty" label="难度" width="100">
          <template #default="scope">
            <el-tag :type="getDifficultyType(scope.row.difficulty)">
              {{ getDifficultyText(scope.row.difficulty) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="添加时间" width="150">
          <template #default="scope">
            {{ formatTime(scope.row.addedTime) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150">
          <template #default="scope">
            <el-button size="small" type="danger" @click="removeWord(scope.row)">移除</el-button>
          </template>
        </el-table-column>
      </el-table>
      </div>

      <!-- 分页 -->
      <div class="pagination">
        <el-pagination
          v-model:current-page="pagination.current"
          v-model:page-size="pagination.size"
          :total="pagination.total"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="handleSizeChange"
          @current-change="handleCurrentChange"
        />
      </div>
    </el-card>

    <!-- 添加单词对话框 -->
    <el-dialog v-model="addDialogVisible" title="添加单词到单元" width="min(600px, 90%)">
      <el-form :model="addForm" label-width="80px">
        <el-form-item label="搜索单词">
          <el-select
            v-model="addForm.wordId"
            placeholder="请输入单词进行搜索"
            filterable
            remote
            :remote-method="searchWords"
            :loading="searching"
            style="width: 100%"
          >
            <el-option
              v-for="word in availableWords"
              :key="word.id"
              :label="`${word.word} - ${word.definition}`"
              :value="word.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="排序号">
          <el-input-number v-model="addForm.orderNum" :min="0" style="width: 100%" placeholder="可选，用于排序" />
        </el-form-item>
      </el-form>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="addDialogVisible = false">取消</el-button>
          <el-button type="primary" @click="addWordToUnit" :loading="adding">确定</el-button>
        </span>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, onBeforeUnmount } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '@/utils/request'

// 响应式数据
const words = ref([])
const availableWords = ref([])
const addDialogVisible = ref(false)
const searching = ref(false)
const adding = ref(false)
const loading = ref(false)
const router = useRouter()
const route = useRoute()

// 从路由参数获取信息
const unitId = ref(0)
const unitName = ref(route.query.unitName || '')
const courseId = ref(route.query.courseId ? Number(route.query.courseId) : null)
const courseName = ref(route.query.courseName || '')

// 搜索表单
const searchForm = reactive({
  keyword: '',
  difficulty: ''
})

// 分页
const pagination = reactive({
  current: 1,
  size: 10,
  total: 0
})

// 添加单词表单
const addForm = reactive({
  wordId: null,
  orderNum: 0
})

// 方法
const loadWords = async () => {
  // 检查unitId是否有效
  if (!unitId.value) {
    ElMessage.warning('请先选择一个单元')
    return
  }

  loading.value = true
  try {
    const response = await request({
      url: `/api/admin/units/${unitId.value}/words`,
      method: 'get'
    })

    if (response.code === 200) {
      let allWords = Array.isArray(response.data) ? response.data : (Array.isArray(response.data?.records) ? response.data.records : [])

      // 前端过滤（后端返回全量数据，不支持过滤参数）
      if (searchForm.keyword) {
        const kw = searchForm.keyword.toLowerCase()
        allWords = allWords.filter(w =>
          w.word?.toLowerCase().includes(kw) ||
          w.definition?.toLowerCase().includes(kw)
        )
      }

      if (searchForm.difficulty) {
        allWords = allWords.filter(w => w.difficulty === searchForm.difficulty)
      }

      // 更新总数
      pagination.total = allWords.length

      // 如果当前页超出范围，重置到第一页
      const maxPage = (pagination.total > 0) ? Math.ceil(pagination.total / pagination.size) : 1
      if (pagination.current > maxPage) {
        pagination.current = 1
      }

      // 前端分页
      const start = (pagination.current - 1) * pagination.size
      const end = start + pagination.size
      words.value = allWords.slice(start, end)
    }
  } catch (error) {
    ElMessage.error('加载单词列表失败')
    console.error('Load words error:', error)
  } finally {
    loading.value = false
  }
}

const handleSearch = () => {
  pagination.current = 1
  loadWords()
}

const backToUnits = () => {
  router.push({
    path: '/admin/units',
    query: {
      courseId: courseId.value,
      courseName: courseName.value
    }
  })
}

const resetSearch = () => {
  searchForm.keyword = ''
  searchForm.difficulty = ''
  pagination.current = 1
  loadWords()
}

const handleSizeChange = (size) => {
  pagination.size = size
  pagination.current = 1
  loadWords()
}

const handleCurrentChange = (page) => {
  pagination.current = page
  loadWords()
}

const showAddDialog = () => {
  addForm.wordId = null
  addForm.orderNum = 0
  availableWords.value = []
  addDialogVisible.value = true
}

// 搜索单词
const searchWords = async (query) => {
  if (!query) {
    availableWords.value = []
    return
  }

  searching.value = true
  try {
    const response = await request({
      url: '/api/admin/words',
      method: 'get',
      params: {
        keyword: query,
        size: 20
      }
    })

    if (response.code === 200) {
      // 过滤掉已经在单元中的单词
      const existingWordIds = words.value.map(w => w.id)
      const rawData = Array.isArray(response.data?.records) ? response.data.records : (Array.isArray(response.data) ? response.data : [])
      availableWords.value = rawData
        .filter(word => !existingWordIds.includes(word.id))
    }
  } catch (error) {
    ElMessage.error('搜索单词失败')
  } finally {
    searching.value = false
  }
}

// 添加单词到单元
const addWordToUnit = async () => {
  if (!addForm.wordId) {
    ElMessage.warning('请选择要添加的单词')
    return
  }

  adding.value = true
  try {
    const response = await request({
      url: `/api/admin/units/${unitId.value}/words/${addForm.wordId}`,
      method: 'post',
      data: {
        orderNum: addForm.orderNum
      }
    })

    if (response.code === 200) {
      ElMessage.success('单词添加成功')
      addDialogVisible.value = false
      loadWords()
    } else {
      ElMessage.error(response.message || '添加失败')
    }
  } catch (error) {
    ElMessage.error('添加失败')
  } finally {
    adding.value = false
  }
}

// 从单元移除单词
const removeWord = async (word) => {
  try {
    await ElMessageBox.confirm(`确认移除单词 "${word.word}" 吗？`, '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })

    const response = await request({
      url: `/api/admin/units/${unitId.value}/words/${word.id}`,
      method: 'delete'
    })

    if (response.code === 200) {
      ElMessage.success('单词移除成功')
      loadWords()
    } else {
      ElMessage.error(response.message || '移除失败')
    }
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('移除失败')
    }
  }
}

const getDifficultyType = (difficulty) => {
  switch (difficulty) {
    case 'EASY': return 'success'
    case 'MEDIUM': return 'warning'
    case 'HARD': return 'danger'
    default: return 'info'
  }
}

const getDifficultyText = (difficulty) => {
  switch (difficulty) {
    case 'EASY': return '简单'
    case 'MEDIUM': return '中等'
    case 'HARD': return '困难'
    default: return difficulty
  }
}

const formatTime = (time) => {
  if (!time) return ''
  return new Date(time).toLocaleString()
}

// 生命周期
onMounted(() => {
  // 从路由参数中获取unitId
  const routeUnitId = route.params.unitId || route.query.unitId
  if (routeUnitId) {
    unitId.value = Number(routeUnitId)
  }

  // 检查是否有有效的unitId
  if (!unitId.value) {
    ElMessage.warning('无效的单元ID，请从单元管理页面重新进入')
    router.push('/admin/units')
    return
  }
  loadWords()
})
</script>

<style scoped>
.unit-words-management {
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

.search-bar {
  margin-bottom: 20px;
}

.pagination {
  margin-top: 20px;
  text-align: right;
}

@media (max-width: 768px) {
  .unit-words-management {
    padding: 12px;
  }

  .search-bar .el-form {
    display: block;
  }

  .search-bar .el-form-item {
    margin-right: 0;
    margin-bottom: 12px;
  }
}
</style>