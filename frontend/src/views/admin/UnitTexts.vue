<template>
  <div class="unit-texts-management">
    <el-card>
      <template #header>
        <div class="card-header">
          <div class="title-info">
            <h3>{{ unitName }} - 课文管理</h3>
            <el-breadcrumb separator="/">
              <el-breadcrumb-item :to="{ path: '/admin/courses', query: { courseId, courseName } }">
                {{ courseName }}
              </el-breadcrumb-item>
              <el-breadcrumb-item :to="{ path: '/admin/units', query: { courseId, courseName } }">
                单元管理
              </el-breadcrumb-item>
              <el-breadcrumb-item>{{ unitName }}</el-breadcrumb-item>
            </el-breadcrumb>
          </div>
          <div class="header-actions">
            <el-button type="primary" @click="showAddDialog">
              <el-icon><Plus /></el-icon>
              添加课文
            </el-button>
            <el-button @click="backToUnits">返回单元管理</el-button>
          </div>
        </div>
      </template>

      <!-- 搜索栏 -->
      <div class="search-bar">
        <el-form :inline="true" :model="searchForm">
          <el-form-item label="关键词">
            <el-input
              v-model="searchForm.keyword"
              placeholder="搜索标题或内容"
              clearable
              @keyup.enter="loadTexts"
            />
          </el-form-item>
          <el-form-item label="难度">
            <el-select v-model="searchForm.difficulty" placeholder="选择难度" clearable style="width: 120px">
              <el-option label="简单" value="EASY" />
              <el-option label="中等" value="MEDIUM" />
              <el-option label="困难" value="HARD" />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" @click="handleSearch">
              <el-icon><Search /></el-icon>
              搜索
            </el-button>
            <el-button @click="resetSearch">
              <el-icon><RefreshLeft /></el-icon>
              重置
            </el-button>
          </el-form-item>
        </el-form>
      </div>

      <!-- 课文列表 -->
      <div class="table-wrapper">
      <el-table v-loading="loading" :data="texts" stripe border>
        <el-table-column prop="title" label="标题" min-width="200" />
        <el-table-column prop="difficulty" label="难度" width="100">
          <template #default="scope">
            <el-tag :type="getDifficultyType(scope.row.difficulty)">
              {{ getDifficultyText(scope.row.difficulty) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="content" label="内容预览" min-width="300" show-overflow-tooltip>
          <template #default="scope">
            {{ getContentPreview(scope.row.content) }}
          </template>
        </el-table-column>
        <el-table-column prop="audioUrl" label="音频" width="100">
          <template #default="scope">
            <el-tag v-if="scope.row.audioUrl" type="success">有音频</el-tag>
            <span v-else style="color: #999">无音频</span>
          </template>
        </el-table-column>
        <el-table-column label="添加时间" width="150">
          <template #default="scope">
            {{ formatTime(scope.row.addedTime) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150">
          <template #default="scope">
            <el-button size="small" @click="viewText(scope.row)">查看</el-button>
            <el-button size="small" type="danger" @click="removeText(scope.row)">移除</el-button>
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
          @size-change="loadTexts"
          @current-change="loadTexts"
        />
      </div>
    </el-card>

    <!-- 添加课文对话框 -->
    <el-dialog v-model="addDialogVisible" title="添加课文到单元" width="min(600px, 90%)">
      <el-form :model="addForm" label-width="80px">
        <el-form-item label="选择课文">
          <el-select
            v-model="addForm.textId"
            placeholder="请选择要添加的课文"
            filterable
            remote
            :remote-method="searchAvailableTexts"
            :loading="searchingTexts"
            style="width: 100%"
          >
            <el-option
              v-for="text in availableTexts"
              :key="text.id"
              :label="`${text.title} (${getDifficultyText(text.difficulty)})`"
              :value="text.id"
            >
              <div class="text-option">
                <span class="text-title">{{ text.title }}</span>
                <span class="text-meta">{{ getDifficultyText(text.difficulty) }} | {{ getContentPreview(text.content) }}</span>
              </div>
            </el-option>
          </el-select>
        </el-form-item>
        <el-form-item label="排序号">
          <el-input-number
            v-model="addForm.orderNum"
            :min="0"
            style="width: 100%"
            placeholder="可选，用于排序"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="addDialogVisible = false">取消</el-button>
          <el-button type="primary" :loading="adding" @click="addTextToUnit">
            添加
          </el-button>
        </span>
      </template>
    </el-dialog>

    <!-- 查看课文对话框 -->
    <el-dialog v-model="viewDialogVisible" title="课文详情" width="min(700px, 90%)">
      <div class="text-detail">
        <h3>{{ currentText.title }}</h3>
        <div class="text-meta">
          <el-tag :type="getDifficultyType(currentText.difficulty)">
            {{ getDifficultyText(currentText.difficulty) }}
          </el-tag>
          <span class="create-time">创建时间：{{ formatTime(currentText.createdTime) }}</span>
        </div>
        <div class="text-content">
          <pre>{{ currentText.content }}</pre>
        </div>
        <div v-if="currentText.audioUrl" class="text-audio">
          <h4>音频</h4>
          <el-button type="primary" @click="playAudio(currentText.audioUrl)">
            <el-icon><VideoPlay /></el-icon>
            播放音频
          </el-button>
        </div>
      </div>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="viewDialogVisible = false">关闭</el-button>
        </span>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, onBeforeUnmount } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '@/utils/request'

// 响应式数据
const texts = ref([])
const availableTexts = ref([])
const loading = ref(false)
const searchingTexts = ref(false)
const adding = ref(false)
const addDialogVisible = ref(false)
const viewDialogVisible = ref(false)
const currentText = ref({})
const router = useRouter()
const route = useRoute()

// 从路由参数获取单元ID
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

// 添加课文表单
const addForm = reactive({
  textId: null,
  orderNum: 0
})

// 方法定义
const loadTexts = async () => {
  // 检查unitId是否有效
  if (!unitId.value) {
    ElMessage.warning('请先选择一个单元')
    return
  }

  loading.value = true
  try {
    const response = await request({
      url: `/api/admin/units/${unitId.value}/texts`,
      method: 'get'
    })

    if (response.code === 200) {
      // FIX: 增强响应数据防御
      let allTexts = Array.isArray(response.data) ? response.data : ((response.data?.records && Array.isArray(response.data.records)) ? response.data.records : [])

      // 前端过滤（如果有搜索条件）
      if (searchForm.keyword) {
        allTexts = allTexts.filter(text =>
          text.title?.toLowerCase().includes(searchForm.keyword.toLowerCase()) ||
          text.content?.toLowerCase().includes(searchForm.keyword.toLowerCase())
        )
      }

      if (searchForm.difficulty) {
        allTexts = allTexts.filter(text => text.difficulty === searchForm.difficulty)
      }

      // 更新总数
      pagination.total = allTexts.length

      // 如果当前页超出范围，重置到第一页
      const maxPage = (pagination.total > 0) ? Math.ceil(pagination.total / pagination.size) : 1
      if (pagination.current > maxPage) {
        pagination.current = 1
      }

      // 前端分页
      const start = (pagination.current - 1) * pagination.size
      const end = start + pagination.size
      texts.value = allTexts.slice(start, end)
    }
  } catch (error) {
    console.error('Load texts error:', error)
    ElMessage.error('加载课文列表失败')
  } finally {
    loading.value = false
  }
}

const handleSearch = () => {
  pagination.current = 1
  loadTexts()
}

const searchAvailableTexts = async (query) => {
  if (!query) {
    availableTexts.value = []
    return
  }

  searchingTexts.value = true
  try {
    const response = await request({
      url: '/api/admin/texts',
      method: 'get',
      params: {
        page: 1,
        size: 20,
        keyword: query
      }
    })

    if (response.code === 200) {
      // 过滤掉已经在单元中的课文和已分配给其他单元的课文
      const existingTextIds = texts.value.map(t => t.id)
      // FIX: 确保records是数组
      const records = (response.data?.records && Array.isArray(response.data.records)) ? response.data.records : (Array.isArray(response.data) ? response.data : [])
      availableTexts.value = records.filter(text =>
        !existingTextIds.includes(text.id) &&
        (text.unitId === 0 || text.unitId === null || text.unitId === undefined)
      )
    }
  } catch (error) {
    ElMessage.error('搜索课文失败')
    console.error('Search texts error:', error)
  } finally {
    searchingTexts.value = false
  }
}

const showAddDialog = () => {
  addForm.textId = null
  addForm.orderNum = 0
  availableTexts.value = []
  addDialogVisible.value = true
}

const addTextToUnit = async () => {
  if (!addForm.textId) {
    ElMessage.warning('请选择要添加的课文')
    return
  }

  adding.value = true
  try {
    const response = await request({
      url: `/api/admin/units/${unitId.value}/texts/${addForm.textId}`,
      method: 'post',
      data: {
        orderNum: addForm.orderNum
      }
    })

    if (response.code === 200) {
      ElMessage.success('课文添加成功')
      addDialogVisible.value = false
      loadTexts()
    } else {
      ElMessage.error(response.message || '添加失败')
    }
  } catch (error) {
    ElMessage.error('添加失败')
  } finally {
    adding.value = false
  }
}

const removeText = (text) => {
  ElMessageBox.confirm(
    `确定要从单元中移除课文 "${text.title}" 吗？`,
    '移除确认',
    {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    }
  )
    .then(async () => {
      try {
        const response = await request({
          url: `/api/admin/units/${unitId.value}/texts/${text.id}`,
          method: 'delete'
        })

        if (response.code === 200) {
          ElMessage.success('移除成功')
          // 重置分页到第一页并刷新
          pagination.current = 1
          await loadTexts()
        } else {
          ElMessage.error(response.message || '移除失败')
        }
      } catch (error) {
        console.error('移除课文失败:', error)
        ElMessage.error('移除失败')
      }
    })
    .catch(() => {
      // user cancelled
    })
}

const viewText = (text) => {
  currentText.value = { ...text }
  viewDialogVisible.value = true
}

const resetSearch = () => {
  searchForm.keyword = ''
  searchForm.difficulty = ''
  loadTexts()
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

let currentAudio = null

const playAudio = (audioUrl) => {
  if (currentAudio) { currentAudio.pause(); currentAudio = null }
  currentAudio = new Audio(audioUrl)
  currentAudio.play().catch(error => {
    ElMessage.error('音频播放失败')
    console.error('Audio playback error:', error)
  })
}

onBeforeUnmount(() => {
  if (currentAudio) { currentAudio.pause(); currentAudio = null }
})

const getContentPreview = (content) => {
  if (!content) return ''
  const maxLength = 100
  return content.length > maxLength ? content.substring(0, maxLength) + '...' : content
}

const getDifficultyType = (difficulty) => {
  const types = {
    EASY: 'success',
    MEDIUM: 'warning',
    HARD: 'danger'
  }
  return types[difficulty] || 'info'
}

const getDifficultyText = (difficulty) => {
  const texts = {
    EASY: '简单',
    MEDIUM: '中等',
    HARD: '困难'
  }
  return texts[difficulty] || difficulty
}

const formatTime = (time) => {
  if (!time) return ''
  const date = new Date(time)
  return date.toLocaleString('zh-CN')
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
  loadTexts()
})
</script>

<style scoped>
.unit-texts-management {
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

.title-info h3 {
  margin: 0 0 8px 0;
  color: #303133;
  font-size: 18px;
}

.title-info .el-breadcrumb {
  font-size: 13px;
}

.header-actions {
  display: flex;
  gap: 12px;
}

.search-bar {
  margin-bottom: 20px;
  padding: 20px;
  background-color: #f8f9fa;
  border-radius: 8px;
}

.search-bar .el-form {
  margin: 0;
}

.pagination {
  margin-top: 20px;
  text-align: right;
}

.text-option {
  display: flex;
  flex-direction: column;
  line-height: 1.4;
}

.text-title {
  font-weight: 500;
  color: #303133;
}

.text-meta {
  font-size: 12px;
  color: #909399;
}

.text-detail {
  padding: 20px 0;
}

.text-detail h3 {
  margin: 0 0 16px 0;
  color: #303133;
  font-size: 18px;
  font-weight: 500;
}

.text-meta {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 20px;
  padding-bottom: 12px;
  border-bottom: 1px solid #e4e7ed;
}

.create-time {
  font-size: 13px;
  color: #909399;
}

.text-content {
  margin-bottom: 20px;
}

.text-content pre {
  white-space: pre-wrap;
  word-wrap: break-word;
  line-height: 1.6;
  font-family: inherit;
  color: #303133;
  margin: 0;
}

.text-audio h4 {
  margin: 0 0 12px 0;
  color: #606266;
  font-size: 14px;
  font-weight: normal;
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}

@media (max-width: 768px) {
  .unit-texts-management {
    padding: 16px;
  }

  .card-header {
    flex-direction: column;
    gap: 16px;
    align-items: flex-start;
  }

  .header-actions {
    width: 100%;
    justify-content: flex-start;
  }

  .search-bar .el-form {
    display: block;
  }

  .search-bar .el-form-item {
    margin-right: 0;
    margin-bottom: 16px;
  }
}
</style>