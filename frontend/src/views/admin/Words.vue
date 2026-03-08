<template>
  <div class="words-management">
    <div class="header">
      <h2>单词管理</h2>
      <div class="actions">
        <el-button type="primary" @click="showAddDialog">
          <el-icon><Plus /></el-icon>
          添加单词
        </el-button>
        <el-button @click="loadWords">
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
            placeholder="搜索单词、发音或释义"
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
        <div class="stat-label">简单单词</div>
      </div>
      <div class="stat-card">
        <div class="stat-value">{{ stats.totalMedium || 0 }}</div>
        <div class="stat-label">中等单词</div>
      </div>
      <div class="stat-card">
        <div class="stat-value">{{ stats.totalHard || 0 }}</div>
        <div class="stat-label">困难单词</div>
      </div>
      <div class="stat-card">
        <div class="stat-value">{{ stats.totalWords || 0 }}</div>
        <div class="stat-label">总单词数</div>
      </div>
    </div>

    <!-- 单词列表 -->
    <div class="words-table">
      <el-table
        v-loading="loading"
        :data="words"
        stripe
        border
        style="width: 100%"
        empty-text="暂无单词数据"
      >
        <el-table-column prop="word" label="单词" width="150" />
        <el-table-column prop="pronunciation" label="发音" width="180" />
        <el-table-column prop="definition" label="释义" min-width="200" show-overflow-tooltip />
        <el-table-column prop="partOfSpeech" label="词性" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="getPartOfSpeechType(row.partOfSpeech)">
              {{ getPartOfSpeechText(row.partOfSpeech) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="difficulty" label="难度" width="100">
          <template #default="{ row }">
            <el-tag :type="getDifficultyType(row.difficulty)">
              {{ getDifficultyText(row.difficulty) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="exampleSentence" label="例句" min-width="250" show-overflow-tooltip />
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
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" size="small" @click="editWord(row)">
              编辑
            </el-button>
            <el-button type="danger" size="small" @click="deleteWord(row)">
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

    <!-- 添加/编辑单词对话框 -->
    <el-dialog
      v-model="dialogVisible"
      :title="isEdit ? '编辑单词' : '添加单词'"
      width="min(600px, 90%)"
      @close="resetForm"
    >
      <el-form
        ref="formRef"
        :model="wordForm"
        :rules="formRules"
        label-width="80px"
        label-position="right"
      >
        <el-form-item label="单词" prop="word">
          <el-input
            v-model="wordForm.word"
            placeholder="请输入英文单词"
            clearable
          />
        </el-form-item>
        <el-form-item label="发音" prop="pronunciation">
          <el-input
            v-model="wordForm.pronunciation"
            placeholder="请输入音标或发音"
            clearable
          />
        </el-form-item>
        <el-form-item label="释义" prop="definition">
          <el-input
            v-model="wordForm.definition"
            type="textarea"
            placeholder="请输入单词释义"
            :rows="2"
          />
        </el-form-item>
        <el-form-item label="词性" prop="partOfSpeech">
          <el-select v-model="wordForm.partOfSpeech" placeholder="选择词性" style="width: 100%">
            <el-option label="名词 (noun)" value="noun" />
            <el-option label="动词 (verb)" value="verb" />
            <el-option label="形容词 (adjective)" value="adjective" />
            <el-option label="副词 (adverb)" value="adverb" />
            <el-option label="代词 (pronoun)" value="pronoun" />
            <el-option label="介词 (preposition)" value="preposition" />
            <el-option label="连词 (conjunction)" value="conjunction" />
            <el-option label="感叹词 (interjection)" value="interjection" />
          </el-select>
        </el-form-item>
        <el-form-item label="难度" prop="difficulty">
          <el-select v-model="wordForm.difficulty" placeholder="选择难度" style="width: 100%">
            <el-option label="简单" value="EASY" />
            <el-option label="中等" value="MEDIUM" />
            <el-option label="困难" value="HARD" />
          </el-select>
        </el-form-item>
        <el-form-item label="例句" prop="exampleSentence">
          <el-input
            v-model="wordForm.exampleSentence"
            type="textarea"
            placeholder="请输入例句"
            :rows="3"
          />
        </el-form-item>
        <el-form-item label="音频URL">
          <el-input
            v-model="wordForm.audioUrl"
            placeholder="请输入音频文件URL（可选）"
            clearable
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="dialogVisible = false">取消</el-button>
          <el-button type="primary" :loading="saving" @click="handleSubmit">
            {{ isEdit ? '更新' : '添加' }}
          </el-button>
        </span>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, onBeforeUnmount } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '@/utils/request'

// 响应式数据
const words = ref([])
const stats = ref({})
const loading = ref(false)
const dialogVisible = ref(false)
const isEdit = ref(false)
const saving = ref(false)
const formRef = ref()

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

// 单词表单
const wordForm = reactive({
  word: '',
  pronunciation: '',
  definition: '',
  partOfSpeech: 'noun',
  difficulty: 'EASY',
  exampleSentence: '',
  audioUrl: ''
})

// 表单验证规则
const formRules = {
  word: [
    { required: true, message: '请输入单词', trigger: 'blur' },
    { min: 1, max: 100, message: '单词长度为1-100个字符', trigger: 'blur' }
  ],
  definition: [
    { required: true, message: '请输入释义', trigger: 'blur' },
    { min: 1, max: 500, message: '释义长度为1-500个字符', trigger: 'blur' }
  ],
  partOfSpeech: [
    { required: true, message: '请选择词性', trigger: 'change' }
  ],
  difficulty: [
    { required: true, message: '请选择难度', trigger: 'change' }
  ]
}

// 方法定义
const loadStats = async () => {
  try {
    const response = await request({
      url: '/api/admin/stats',
      method: 'get'
    })
    if (response.code === 200) {
      stats.value = response.data || {}
    }
  } catch (error) {
    console.error('加载统计信息失败:', error)
  }
}

const loadWords = async () => {
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

    const response = await request({
      url: '/api/admin/words',
      method: 'get',
      params
    })

    if (response.code === 200) {
      words.value = response.data?.records || []
      pagination.total = response.data?.total || 0
    }
  } catch (error) {
    ElMessage.error('加载单词列表失败')
  } finally {
    loading.value = false
  }
}

const handleSearch = () => {
  pagination.current = 1
  loadWords()
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
  isEdit.value = false
  dialogVisible.value = true
}

const editWord = (word) => {
  isEdit.value = true
  Object.assign(wordForm, {
    ...word,
    pronunciation: word.pronunciation || '',
    definition: word.definition || '',
    partOfSpeech: word.partOfSpeech || 'noun',
    difficulty: word.difficulty || 'EASY',
    exampleSentence: word.exampleSentence || '',
    audioUrl: word.audioUrl || ''
  })
  dialogVisible.value = true
}

const deleteWord = (word) => {
  ElMessageBox.confirm(
    `确定要删除单词 "${word.word}" 吗？此操作不可恢复！`,
    '删除确认',
    {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    }
  )
    .then(async () => {
      try {
        const response = await request({
          url: `/api/admin/words/${word.id}`,
          method: 'delete'
        })

        if (response.code === 200) {
          ElMessage.success('删除成功')
          loadWords()
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

const handleSubmit = async () => {
  try {
    await formRef.value.validate()
    saving.value = true

    const wordData = {
      word: wordForm.word.trim(),
      pronunciation: wordForm.pronunciation?.trim() || '',
      definition: wordForm.definition.trim(),
      partOfSpeech: wordForm.partOfSpeech,
      difficulty: wordForm.difficulty,
      exampleSentence: wordForm.exampleSentence?.trim() || '',
      audioUrl: wordForm.audioUrl?.trim() || ''
    }

    const response = await request({
      url: isEdit.value ? `/api/admin/words/${wordForm.id}` : '/api/admin/words',
      method: isEdit.value ? 'put' : 'post',
      data: wordData
    })

    if (response.code === 200) {
      ElMessage.success(isEdit.value ? '更新成功' : '添加成功')
      dialogVisible.value = false
      loadWords()
      loadStats()
    } else {
      ElMessage.error(response.message || '操作失败')
    }
  } catch (error) {
    if (error.constructor.name !== 'Error') {
      // 表单验证错误
      return
    }
    ElMessage.error('操作失败')
  } finally {
    saving.value = false
  }
}

const resetForm = () => {
  Object.assign(wordForm, {
    word: '',
    pronunciation: '',
    definition: '',
    partOfSpeech: 'noun',
    difficulty: 'EASY',
    exampleSentence: '',
    audioUrl: ''
  })
  formRef.value?.clearValidate()
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

const getPartOfSpeechType = (partOfSpeech) => {
  const types = {
    noun: '',
    verb: 'success',
    adjective: 'warning',
    adverb: 'info',
    pronoun: 'primary',
    preposition: 'danger',
    conjunction: 'warning',
    interjection: 'success'
  }
  return types[partOfSpeech] || ''
}

const getPartOfSpeechText = (partOfSpeech) => {
  const texts = {
    noun: '名词',
    verb: '动词',
    adjective: '形容词',
    adverb: '副词',
    pronoun: '代词',
    preposition: '介词',
    conjunction: '连词',
    interjection: '感叹词'
  }
  return texts[partOfSpeech] || partOfSpeech
}

const formatTime = (time) => {
  if (!time) return ''
  const date = new Date(time)
  return date.toLocaleString('zh-CN')
}

// 生命周期
onMounted(() => {
  loadStats()
  loadWords()
})
</script>

<style scoped>
.words-management {
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

.words-table {
  background-color: #fff;
  border-radius: 8px;
  overflow-x: auto;
  -webkit-overflow-scrolling: touch;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
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

@media (max-width: 768px) {
  .words-management {
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
</style>