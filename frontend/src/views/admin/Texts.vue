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
              {{ getContentPreview(row.content) }}
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
          <el-button type="success" @click="editText(currentText)">编辑</el-button>
        </span>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, onBeforeUnmount } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Search, RefreshLeft, Refresh, Edit, Delete, VideoPlay } from '@element-plus/icons-vue'
import * as adminApi from '@/api/admin'
import TextEditDialog from '@/components/TextEditDialog.vue'

// 响应式数据
const texts = ref([])
const stats = ref({})
const courses = ref([])
const units = ref([])
const loading = ref(false)
const viewDialogVisible = ref(false)
const currentText = ref({})
const editDialogVisible = ref(false)
const selectedText = ref(null)
const selectedUnitId = ref(null)

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
      courses.value = Array.isArray(response.data?.records) ? response.data.records : (Array.isArray(response.data) ? response.data : [])
    }
  } catch (error) {
    ElMessage.error('加载课程列表失败')
  }
}

const loadUnits = async (courseId) => {
  if (!courseId) {
    units.value = []
    return
  }

  try {
    const response = await adminApi.getUnitsByCourseId(courseId)
    if (response.code === 200) {
      units.value = response.data || []
    } else {
      units.value = []
      ElMessage.error(response.message || '加载单元列表失败')
    }
  } catch (error) {
    units.value = []
    ElMessage.error('加载单元列表失败')
  }
}

const loadStats = async () => {
  try {
    // 获取所有课文的统计信息（不分页）
    const response = await adminApi.getTexts({ page: 1, size: 10000 })
    
    if (response.code === 200) {
      const allTexts = response.data?.records || []
      
      // 计算各难度的课文数量
      stats.value = {
        totalEasy: allTexts.filter(t => t.difficulty === 'EASY').length,
        totalMedium: allTexts.filter(t => t.difficulty === 'MEDIUM').length,
        totalHard: allTexts.filter(t => t.difficulty === 'HARD').length,
        totalTexts: allTexts.length
      }
    }
  } catch (error) {
    console.error('加载统计信息失败:', error)
    // 出错时显示0
    stats.value = {
      totalEasy: 0,
      totalMedium: 0,
      totalHard: 0,
      totalTexts: 0
    }
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
      texts.value = response.data?.records || []
      pagination.total = response.data?.total || 0
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

const showAddDialog = () => {
  selectedText.value = null
  selectedUnitId.value = null  // 清空 unitId，确保显示课程和单元选择器
  editDialogVisible.value = true
}

const viewText = (text) => {
  currentText.value = { ...text }
  viewDialogVisible.value = true
}

const editText = (text) => {
  selectedText.value = { ...text }
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
  padding: 20px;
  max-width: 1400px;
  margin: 0 auto;
}

.header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
  padding-bottom: 16px;
  border-bottom: 1px solid #e4e7ed;
}

.header h2 {
  margin: 0;
  color: #303133;
  font-weight: 500;
}

.actions {
  display: flex;
  gap: 12px;
}

.search-bar {
  margin-bottom: 20px;
  padding: 20px;
  background-color: #f8f9fa;
  border-radius: 8px;
}

.search-form {
  margin: 0;
}

.stats-cards {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: 20px;
  margin-bottom: 20px;
}

.stat-card {
  text-align: center;
  padding: 20px;
  background-color: #fff;
  border: 1px solid #e4e7ed;
  border-radius: 8px;
  transition: all 0.3s ease;
}

.stat-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
}

.stat-value {
  font-size: 32px;
  font-weight: bold;
  color: #409eff;
  margin-bottom: 8px;
}

.stat-label {
  font-size: 14px;
  color: #909399;
}

.texts-table {
  background-color: #fff;
  border-radius: 8px;
  overflow-x: auto;
  -webkit-overflow-scrolling: touch;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
}

.content-preview {
  font-size: 13px;
  color: #606266;
  line-height: 1.4;
}

.pagination {
  margin-top: 20px;
  display: flex;
  justify-content: center;
}

.no-audio {
  color: #909399;
  font-size: 12px;
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
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

@media (max-width: 768px) {
  .texts-management {
    padding: 16px;
  }

  .header {
    flex-direction: column;
    gap: 16px;
    align-items: flex-start;
  }

  .actions {
    width: 100%;
    justify-content: flex-start;
  }

  .search-form {
    display: block;
  }

  .search-form .el-form-item {
    margin-right: 0;
    margin-bottom: 16px;
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
}

.exercise-section {
  margin-top: 20px;
}

.section-title {
  font-weight: bold;
  color: #333;
}
</style>