<template>
  <div class="texts-management">
    <div class="header">
      <h2>课文管理</h2>
      <div class="actions">
        <el-button type="primary" @click="showAddDialog">
          <el-icon><Plus /></el-icon>
          添加课文
        </el-button>
        <el-button @click="loadTexts">
          <el-icon><Refresh /></el-icon>
          刷新
        </el-button>
      </div>
    </div>

    <!-- 搜索栏 -->
    <div class="search-bar">
      <el-form :inline="true" :model="searchForm" class="search-form">
        <el-form-item label="关键词">
          <el-input
            v-model="searchForm.keyword"
            placeholder="搜索标题或内容"
            clearable
            style="width: 200px"
            @keyup.enter="handleSearch"
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

    <!-- 统计信息 -->
    <div class="stats-cards">
      <div class="stat-card">
        <div class="stat-value">{{ stats.totalEasy || 0 }}</div>
        <div class="stat-label">简单课文</div>
      </div>
      <div class="stat-card">
        <div class="stat-value">{{ stats.totalMedium || 0 }}</div>
        <div class="stat-label">中等课文</div>
      </div>
      <div class="stat-card">
        <div class="stat-value">{{ stats.totalHard || 0 }}</div>
        <div class="stat-label">困难课文</div>
      </div>
      <div class="stat-card">
        <div class="stat-value">{{ stats.totalFeatured || 0 }}</div>
        <div class="stat-label">推荐课文</div>
      </div>
      <div class="stat-card">
        <div class="stat-value">{{ stats.totalTexts || 0 }}</div>
        <div class="stat-label">总课文数</div>
      </div>
    </div>

    <!-- 课文列表 -->
    <div class="texts-table">
      <el-table
        v-loading="loading"
        :data="texts"
        stripe
        border
        style="width: 100%"
        empty-text="暂无课文数据"
      >
        <el-table-column prop="title" label="标题" min-width="200" show-overflow-tooltip />
        <el-table-column prop="content" label="内容预览" min-width="300" show-overflow-tooltip>
          <template #default="{ row }">
            <div class="content-preview">
              {{ getContentPreview(row.contentType === 'MARKDOWN' ? (row.markdownContent || row.content) : row.content) }}
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="difficulty" label="难度" width="100">
          <template #default="{ row }">
            <el-tag :type="getDifficultyType(row.difficulty)">
              {{ getDifficultyText(row.difficulty) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="isFeatured" label="推荐" width="80" align="center">
          <template #default="{ row }">
            <el-switch
              :model-value="isTextFeatured(row)"
              @change="toggleFeatured(row, $event)"
              :loading="row._featuredLoading"
            />
          </template>
        </el-table-column>
        <el-table-column label="优先级" width="110" align="center">
          <template #default="{ row }">
            <el-input-number
              v-if="isTextFeatured(row)"
              v-model="row.featuredPriority"
              :min="0"
              :max="100"
              :step="1"
              size="small"
              controls-position="right"
              style="width: 90px"
              :disabled="row._priorityLoading"
              @change="(val) => updatePriority(row, val)"
              @blur="() => updatePriority(row, row.featuredPriority)"
            />
            <span v-else class="priority-na">—</span>
          </template>
        </el-table-column>
        <el-table-column prop="audioUrl" label="音频" width="120">
          <template #default="{ row }">
            <el-button
              v-if="row.audioUrl"
              type="primary"
              size="small"
              text
              @click="playAudio(row.audioUrl)"
            >
              播放
            </el-button>
            <span v-else class="no-audio">无音频</span>
          </template>
        </el-table-column>
        <el-table-column prop="createdTime" label="创建时间" width="180">
          <template #default="{ row }">
            {{ formatTime(row.createdTime) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" size="small" @click="viewText(row)">
              查看
            </el-button>
            <el-button type="success" size="small" @click="editText(row)">
              编辑
            </el-button>
            <el-button type="danger" size="small" @click="deleteText(row)">
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
      <div class="pagination">
        <el-pagination
          v-model:current-page="pagination.current"
          v-model:page-size="pagination.size"
          :page-sizes="[10, 20, 50, 100]"
          :total="pagination.total"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="handleSizeChange"
          @current-change="handleCurrentChange"
        />
      </div>
    </div>

    <!-- 添加/编辑课文对话框 -->
    <TextEditDialog
      v-model="editDialogVisible"
      :text-data="selectedText"
      :unit-id="selectedUnitId"
      @success="loadTexts"
    />

    <!-- 查看课文对话框 -->
    <el-dialog
      v-model="viewDialogVisible"
      title="课文详情"
      width="min(700px, 90%)"
    >
      <div class="text-detail">
        <h3>{{ currentText.title }}</h3>
        <div v-if="currentText.coverUrl" class="text-cover-preview">
          <img :src="currentText.coverUrl" alt="封面图预览" />
        </div>
        <div class="text-meta">
          <el-tag :type="getDifficultyType(currentText.difficulty)">
            {{ getDifficultyText(currentText.difficulty) }}
          </el-tag>
          <span class="create-time">创建时间：{{ formatTime(currentText.createdTime) }}</span>
        </div>
        <div class="text-content">
          <MarkdownRenderer v-if="currentText.contentType === 'MARKDOWN'" :markdown="currentText.markdownContent || currentText.content || ''" />
          <pre v-else>{{ currentText.content }}</pre>
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
          <el-button type="success" @click="editText(currentText)">编辑</el-button>
        </span>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Search, RefreshLeft, Refresh, VideoPlay } from '@element-plus/icons-vue'
import * as adminApi from '@/api/admin'
import TextEditDialog from '@/components/TextEditDialog.vue'
import MarkdownRenderer from '@/components/MarkdownRenderer.vue'

// 响应式数据
const texts = ref([])
const stats = ref({})
const loading = ref(false)
const viewDialogVisible = ref(false)
const currentText = ref({})
const editDialogVisible = ref(false)
const selectedText = ref(null)
const selectedUnitId = ref(null)
const router = useRouter()

// 搜索表单
const searchForm = reactive({
  keyword: '',
  difficulty: ''
})

// 分页数据
const pagination = reactive({
  current: 1,
  size: 20,
  total: 0
})

// 方法定义
const loadCourses = async () => {
  try {
    const response = await adminApi.getCourses({
      page: 1,
      size: 100 // 获取所有课程
    })
    if (response.code === 200) {
      // FIX: 增强响应数据防御
      return Array.isArray(response.data?.records) ? response.data.records : (Array.isArray(response.data) ? response.data : [])
    }
  } catch (error) {
    ElMessage.error('加载课程列表失败')
  }
}

const loadStats = async () => {
  try {
    const response = await adminApi.getTextStats()
    if (response.code === 200) {
      stats.value = (response.data && typeof response.data === 'object') ? response.data : {}
    }
  } catch (error) {
    console.error('Load stats error:', error)
    stats.value = {}
  }
}

const loadTexts = async () => {
  loading.value = true
  try {
    const params = {
      page: pagination.current,
      size: pagination.size
    }

    if (searchForm.keyword) {
      params.keyword = searchForm.keyword
    }
    if (searchForm.difficulty) {
      params.difficulty = searchForm.difficulty
    }

    const response = await adminApi.getTexts(params)

    if (response.code === 200) {
      // FIX: 增强响应数据防御
      texts.value = ((response.data?.records && Array.isArray(response.data.records)) ? response.data.records : [])
      pagination.total = (typeof response.data?.total === 'number') ? response.data.total : 0
    }
  } catch (error) {
    ElMessage.error('加载课文列表失败')
  } finally {
    loading.value = false
  }
}

const handleSearch = () => {
  pagination.current = 1
  loadTexts()
}

const resetSearch = () => {
  searchForm.keyword = ''
  searchForm.difficulty = ''
  pagination.current = 1
  loadTexts()
}

const handleSizeChange = (size) => {
  pagination.size = size
  pagination.current = 1
  loadTexts()
}

const handleCurrentChange = (page) => {
  pagination.current = page
  loadTexts()
}

const normalizeFeaturedValue = (value) => {
  if (value === true || value === 1 || value === '1') return 1
  if (typeof value === 'string' && value.trim().toLowerCase() === 'true') return 1
  return 0
}

const isTextFeatured = (row) => normalizeFeaturedValue(row?.isFeatured) === 1

const toggleFeatured = async (row, nextValue) => {
  const targetFeatured = nextValue === true || nextValue === 1 ? 1 : 0
  row._featuredLoading = true
  try {
    await adminApi.toggleTextFeatured(row.id, targetFeatured)
    row.isFeatured = targetFeatured
    ElMessage.success(targetFeatured === 1 ? '已设为推荐' : '已取消推荐')
    loadStats()
  } catch (error) {
    // 拦截器已展示错误弹窗，此处回滚 UI 状态
    row.isFeatured = 1 - targetFeatured
  } finally {
    row._featuredLoading = false
  }
}

const updatePriority = async (row, val) => {
  // val 可能来自 @change（数字）或 @blur（通过 row.featuredPriority 传入的数字）
  // 统一转为数字，防止非数字类型混入
  const rawVal = (val !== null && val !== undefined && typeof val === 'number') ? val : row.featuredPriority
  const priority = (rawVal == null || isNaN(rawVal)) ? 0 : Math.max(0, Math.min(100, rawVal))

  // 防止 @change 和 @blur 在点击加减按钮时双重触发
  if (row._lastSavedPriority === priority) return
  row._lastSavedPriority = priority

  row._priorityLoading = true
  try {
    const response = await adminApi.setTextFeaturedPriority(row.id, priority)
    if (response.code === 200) {
      row.featuredPriority = priority
      ElMessage.success('优先级已更新')
    } else {
      ElMessage.error(response.message || '更新失败')
    }
  } catch (error) {
    ElMessage.error('更新失败')
  } finally {
    row._priorityLoading = false
  }
}

const showAddDialog = () => {
  selectedText.value = null
  selectedUnitId.value = null  // 清空 unitId，确保显示课程和单元选择器
  editDialogVisible.value = true
}

const viewText = (text) => {
  currentText.value = { ...text, isFeatured: normalizeFeaturedValue(text?.isFeatured) }
  viewDialogVisible.value = true
}

const editText = (text) => {
  selectedText.value = { ...text, isFeatured: normalizeFeaturedValue(text?.isFeatured) }
  selectedUnitId.value = text.unitId
  editDialogVisible.value = true
}

const deleteText = (text) => {
  ElMessageBox.confirm(
    `确定要删除课文 "${text.title}" 吗？此操作不可恢复！`,
    '删除确认',
    {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    }
  )
    .then(async () => {
      try {
        const response = await adminApi.deleteText(text.id)

        if (response.code === 200) {
          ElMessage.success('删除成功')
          loadTexts()
          loadStats()
        } else {
          ElMessage.error(response.message || '删除失败')
        }
      } catch (error) {
        ElMessage.error('删除失败')
      }
    })
    .catch(() => {
      // 用户取消删除
    })
}

let currentAudio = null

const playAudio = (audioUrl) => {
  if (!audioUrl) return
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
  loadCourses()
  loadStats()
  loadTexts()
})
</script>

<style scoped>
.texts-management {
  max-width: 1400px;
  margin: 0 auto;
  padding: 20px;
}

/* PC端大字号适配 */
@media (min-width: 1200px) {
  .texts-management {
    padding: 30px;
  }
  
  .header h2 {
    font-size: 28px;
  }

  .stat-value {
    font-size: 42px;
  }

  .stat-label {
    font-size: 16px;
  }

  :deep(.el-table) {
    font-size: 16px;
  }

  :deep(.el-table .cell) {
    line-height: 1.6;
    padding: 12px 0;
  }

  .search-bar {
    margin-bottom: 25px;
    padding: 24px;
  }
}

.header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24px;
}

.header h2 {
  margin: 0;
  font-size: 24px;
  font-weight: 800;
  color: #1e293b;
}

.search-bar {
  background: #f8fafc;
  padding: 20px;
  border-radius: 16px;
  margin-bottom: 24px;
  border: 1px solid #f1f5f9;
}

.search-form :deep(.el-form-item) {
  margin-bottom: 0;
  margin-right: 16px;
}

.stats-cards {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: 20px;
  margin-bottom: 32px;
}

.stat-card {
  background: white;
  border: 1px solid #e2e8f0;
  border-radius: 20px;
  padding: 24px;
  text-align: center;
  transition: all 0.3s;
  box-shadow: 0 4px 6px -1px rgba(0, 0, 0, 0.05);
}

.stat-card:hover {
  transform: translateY(-4px);
  box-shadow: 0 20px 25px -5px rgba(0, 0, 0, 0.1);
  border-color: #6366f1;
}

.stat-value {
  font-size: 36px;
  font-weight: 800;
  color: #1e293b;
  margin-bottom: 8px;
  line-height: 1;
}

.stat-label {
  font-size: 14px;
  font-weight: 600;
  color: #64748b;
}

.texts-table {
  background: white;
  border-radius: 16px;
  border: 1px solid #f1f5f9;
  overflow: hidden;
  box-shadow: 0 2px 12px rgba(0,0,0,0.05);
}

:deep(.el-table) {
  --el-table-header-bg-color: #f8fafc;
  font-size: 15px;
}

.content-preview {
  font-size: 14px;
  color: #64748b;
  line-height: 1.6;
}

.pagination {
  margin-top: 24px;
  display: flex;
  justify-content: center;
}

.no-audio {
  color: #94a3b8;
  font-size: 12px;
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}

.text-detail {
  padding: 10px 0;
}

.text-detail h3 {
  margin: 0 0 16px 0;
  color: #1e293b;
  font-size: 22px;
  font-weight: 700;
  text-align: center;
}

.text-meta {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 20px;
  margin-bottom: 24px;
  padding-bottom: 16px;
  border-bottom: 1px solid #f1f5f9;
}

.create-time {
  font-size: 13px;
  color: #94a3b8;
}

.text-cover-preview {
  width: 100%;
  max-height: 300px;
  overflow: hidden;
  border-radius: 12px;
  margin-bottom: 24px;
  box-shadow: 0 4px 12px rgba(0,0,0,0.1);
}

.text-cover-preview img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.text-content {
  margin-bottom: 24px;
  max-height: 60vh;
  overflow-y: auto;
  padding: 24px;
  background-color: #f8fafc;
  border-radius: 12px;
  border: 1px solid #f1f5f9;
  line-height: 1.8;
  font-size: 16px;
}

.text-content pre {
  white-space: pre-wrap;
  word-wrap: break-word;
  line-height: 1.8;
  font-family: inherit;
  color: #334155;
  margin: 0;
}

.text-audio {
  background: #f1f5f9;
  padding: 16px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  gap: 20px;
}

.text-audio h4 {
  margin: 0;
  color: #475569;
  font-size: 14px;
  font-weight: 600;
}

.priority-na {
  color: #cbd5e1;
  font-size: 14px;
}

@media (max-width: 768px) {
  .texts-management {
    padding: 12px;
  }

  .header {
    flex-direction: column;
    align-items: flex-start;
    gap: 16px;
  }

  .actions {
    width: 100%;
    display: flex;
    gap: 8px;
  }

  .search-form {
    display: flex;
    flex-direction: column;
    gap: 12px;
  }

  .search-form :deep(.el-form-item) {
    margin-right: 0;
  }

  .stats-cards {
    grid-template-columns: repeat(2, 1fr);
    gap: 12px;
  }

  .stat-card {
    padding: 16px;
  }

  .stat-value {
    font-size: 24px;
  }

  .text-content {
    padding: 16px;
    font-size: 14px;
  }
}
</style>
