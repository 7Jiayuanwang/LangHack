<template>
  <div class="vocabulary">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>我的生词本</span>
          <div class="header-actions">
            <el-input
              v-model="searchWord"
              placeholder="搜索单词"
              style="width: 200px; margin-right: 10px"
              @keyup.enter="loadVocabulary"
              clearable
            />
            <el-button type="primary" @click="loadVocabulary">搜索</el-button>
          </div>
        </div>
      </template>

      <!-- 统计信息 -->
      <div class="stats-bar">
        <div class="stat-item">
          <span class="label">总词汇量：</span>
          <span class="value">{{ stats.totalWords }}</span>
        </div>
        <div class="stat-item">
          <span class="label">新词：</span>
          <span class="value new">{{ stats.newWords }}</span>
        </div>
        <div class="stat-item">
          <span class="label">学习中：</span>
          <span class="value learning">{{ stats.learningWords }}</span>
        </div>
        <div class="stat-item">
          <span class="label">已掌握：</span>
          <span class="value mastered">{{ stats.masteredWords }}</span>
        </div>
      </div>

      <!-- 筛选栏 -->
      <div class="filter-bar">
        <el-form :inline="true" :model="filterForm">
          <el-form-item label="掌握程度">
            <el-select v-model="filterForm.masteryLevel" placeholder="请选择" clearable @change="loadVocabulary">
              <el-option label="新词" value="NEW" />
              <el-option label="学习中" value="LEARNING" />
              <el-option label="已掌握" value="MASTERED" />
            </el-select>
          </el-form-item>
          <el-form-item label="排序">
            <el-select v-model="filterForm.sortBy" placeholder="请选择" @change="loadVocabulary">
              <el-option label="添加时间" value="addedTime" />
              <el-option label="复习次数" value="reviewCount" />
              <el-option label="单词" value="word" />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" @click="startReview" :disabled="vocabulary.length === 0">
              开始复习
            </el-button>
          </el-form-item>
        </el-form>
      </div>

      <!-- 词汇列表 -->
      <div class="vocabulary-list" v-loading="loading">
        <div
          v-for="item in vocabulary"
          :key="item.id"
          class="vocabulary-item"
          @click="showWordDetail(item)"
        >
          <div class="word-main">
            <div class="word-header">
              <h3 class="word">{{ item.word }}</h3>
              <div class="word-meta">
                <el-tag
                  :type="getMasteryType(item.masteryLevel)"
                  size="small"
                >
                  {{ getMasteryText(item.masteryLevel) }}
                </el-tag>
                <span class="review-count">复习{{ item.reviewCount }}次</span>
              </div>
            </div>
            <div class="pronunciation" v-if="item.pronunciation">
              {{ item.pronunciation }}
            </div>
            <div class="definition">{{ item.definition }}</div>
            <div class="example" v-if="item.exampleSentence">
              <span class="example-label">例句：</span>
              {{ item.exampleSentence }}
            </div>
          </div>
          <div class="word-actions">
            <el-button size="small" @click.stop="playAudio(item)">
              <el-icon><VideoPlay /></el-icon>
            </el-button>
            <el-button size="small" @click.stop="markAsLearned(item)">
              <el-icon><Check /></el-icon>
            </el-button>
            <el-button size="small" type="danger" @click.stop="removeWord(item)">
              <el-icon><Delete /></el-icon>
            </el-button>
          </div>
        </div>

        <!-- 空状态 -->
        <el-empty v-if="vocabulary.length === 0 && !loading" description="暂无生词">
          <el-button type="primary" @click="$router.push('/student/courses')">
            去学习添加生词
          </el-button>
        </el-empty>
      </div>

      <!-- 分页 -->
      <div class="pagination" v-if="vocabulary.length > 0">
        <el-pagination
          v-model:current-page="pagination.current"
          v-model:page-size="pagination.size"
          :total="pagination.total"
          :page-sizes="[20, 50, 100]"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="loadVocabulary"
          @current-change="loadVocabulary"
        />
      </div>
    </el-card>

    <!-- 单词详情对话框 -->
    <el-dialog
      v-model="detailDialogVisible"
      title="单词详情"
      width="600px"
    >
      <div v-if="selectedWord" class="word-detail">
        <div class="detail-header">
          <h2>{{ selectedWord.word }}</h2>
          <div class="pronunciation" v-if="selectedWord.pronunciation">
            {{ selectedWord.pronunciation }}
            <el-button size="small" circle @click="playAudio(selectedWord)">
              <el-icon><VideoPlay /></el-icon>
            </el-button>
          </div>
        </div>

        <div class="detail-content">
          <div class="section">
            <h4>词义</h4>
            <p>{{ selectedWord.definition }}</p>
          </div>

          <div class="section" v-if="selectedWord.exampleSentence">
            <h4>例句</h4>
            <p>{{ selectedWord.exampleSentence }}</p>
          </div>

          <div class="section">
            <h4>学习记录</h4>
            <div class="learning-info">
              <p>添加时间：{{ formatTime(selectedWord.addedTime) }}</p>
              <p>复习次数：{{ selectedWord.reviewCount }}次</p>
              <p>最后复习：{{ selectedWord.lastReviewed ? formatTime(selectedWord.lastReviewed) : '未复习' }}</p>
              <p>掌握程度：{{ getMasteryText(selectedWord.masteryLevel) }}</p>
            </div>
          </div>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'

// 响应式数据
const vocabulary = ref([])
const loading = ref(false)
const detailDialogVisible = ref(false)
const selectedWord = ref(null)
const searchWord = ref('')

// 统计信息
const stats = reactive({
  totalWords: 0,
  newWords: 0,
  learningWords: 0,
  masteredWords: 0
})

// 筛选表单
const filterForm = reactive({
  masteryLevel: '',
  sortBy: 'addedTime'
})

// 分页
const pagination = reactive({
  current: 1,
  size: 20,
  total: 0
})

// 方法
const loadVocabulary = async () => {
  loading.value = true
  try {
    // 模拟API调用
    await new Promise(resolve => setTimeout(resolve, 500))

    const mockData = [
      {
        id: 1,
        word: 'beautiful',
        pronunciation: '/ˈbjuːtɪfl/',
        definition: '美丽的；漂亮的',
        exampleSentence: 'She is a beautiful girl.',
        masteryLevel: 'LEARNING',
        reviewCount: 3,
        addedTime: '2024-01-15T10:30:00',
        lastReviewed: '2024-01-18T14:20:00'
      },
      {
        id: 2,
        word: 'important',
        pronunciation: '/ɪmˈpɔːtnt/',
        definition: '重要的',
        exampleSentence: 'This is an important meeting.',
        masteryLevel: 'NEW',
        reviewCount: 0,
        addedTime: '2024-01-16T09:15:00',
        lastReviewed: null
      },
      {
        id: 3,
        word: 'practice',
        pronunciation: '/ˈpræktɪs/',
        definition: '练习；实践',
        exampleSentence: 'You need to practice every day.',
        masteryLevel: 'MASTERED',
        reviewCount: 8,
        addedTime: '2024-01-10T16:45:00',
        lastReviewed: '2024-01-17T10:30:00'
      }
    ]

    vocabulary.value = mockData
    pagination.total = mockData.length

    // 更新统计信息
    stats.totalWords = mockData.length
    stats.newWords = mockData.filter(item => item.masteryLevel === 'NEW').length
    stats.learningWords = mockData.filter(item => item.masteryLevel === 'LEARNING').length
    stats.masteredWords = mockData.filter(item => item.masteryLevel === 'MASTERED').length

  } catch (error) {
    ElMessage.error('加载生词本失败')
  } finally {
    loading.value = false
  }
}

const showWordDetail = (word) => {
  selectedWord.value = word
  detailDialogVisible.value = true
}

const playAudio = (word) => {
  // 模拟播放音频
  ElMessage.info(`播放单词 "${word.word}" 的发音`)
}

const markAsLearned = async (word) => {
  try {
    // 模拟API调用
    word.masteryLevel = word.masteryLevel === 'MASTERED' ? 'LEARNING' : 'MASTERED'
    word.reviewCount += 1
    word.lastReviewed = new Date().toISOString()

    ElMessage.success('标记学习状态成功')
    loadVocabulary()
  } catch (error) {
    ElMessage.error('操作失败')
  }
}

const removeWord = async (word) => {
  try {
    await ElMessageBox.confirm(`确认删除单词 "${word.word}" 吗？`, '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })

    // 模拟API调用
    ElMessage.success('删除成功')
    loadVocabulary()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('删除失败')
    }
  }
}

const startReview = () => {
  ElMessage.info('开始复习功能开发中')
}

const getMasteryType = (level) => {
  const types = {
    'NEW': 'info',
    'LEARNING': 'warning',
    'MASTERED': 'success'
  }
  return types[level] || ''
}

const getMasteryText = (level) => {
  const texts = {
    'NEW': '新词',
    'LEARNING': '学习中',
    'MASTERED': '已掌握'
  }
  return texts[level] || level
}

const formatTime = (time) => {
  if (!time) return ''
  return new Date(time).toLocaleString()
}

// 生命周期
onMounted(() => {
  loadVocabulary()
})
</script>

<style scoped>
.vocabulary {
  padding: 20px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.header-actions {
  display: flex;
  align-items: center;
}

.stats-bar {
  display: flex;
  gap: 30px;
  padding: 15px;
  background-color: #f8f9fa;
  border-radius: 4px;
  margin-bottom: 20px;
}

.stat-item {
  display: flex;
  align-items: center;
  gap: 8px;
}

.stat-item .label {
  color: #666;
  font-size: 14px;
}

.stat-item .value {
  font-weight: bold;
  font-size: 16px;
}

.stat-item .value.new {
  color: #409EFF;
}

.stat-item .value.learning {
  color: #E6A23C;
}

.stat-item .value.mastered {
  color: #67C23A;
}

.filter-bar {
  margin-bottom: 20px;
}

.vocabulary-list {
  max-height: 600px;
  overflow-y: auto;
}

.vocabulary-item {
  padding: 15px;
  border: 1px solid #e4e7ed;
  border-radius: 4px;
  margin-bottom: 10px;
  cursor: pointer;
  transition: all 0.3s;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.vocabulary-item:hover {
  border-color: #409EFF;
  box-shadow: 0 2px 12px 0 rgba(64, 158, 255, 0.1);
}

.word-main {
  flex: 1;
}

.word-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

.word {
  margin: 0;
  color: #303133;
  font-size: 18px;
  font-weight: bold;
}

.word-meta {
  display: flex;
  align-items: center;
  gap: 10px;
}

.review-count {
  font-size: 12px;
  color: #909399;
}

.pronunciation {
  color: #606266;
  font-style: italic;
  margin-bottom: 5px;
}

.definition {
  color: #303133;
  margin-bottom: 8px;
}

.example {
  color: #606266;
  font-size: 14px;
}

.example-label {
  font-weight: bold;
  color: #303133;
}

.word-actions {
  display: flex;
  gap: 5px;
  margin-left: 15px;
}

.pagination {
  margin-top: 20px;
  text-align: right;
}

.word-detail .detail-header {
  text-align: center;
  margin-bottom: 20px;
  padding-bottom: 15px;
  border-bottom: 1px solid #e4e7ed;
}

.detail-header h2 {
  margin: 0 0 10px 0;
  color: #303133;
  font-size: 28px;
}

.detail-header .pronunciation {
  color: #606266;
  font-size: 16px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
}

.detail-content .section {
  margin-bottom: 20px;
}

.detail-content .section h4 {
  margin: 0 0 10px 0;
  color: #303133;
  font-size: 16px;
}

.detail-content .section p {
  margin: 5px 0;
  color: #606266;
  line-height: 1.6;
}

.learning-info p {
  margin: 5px 0;
  color: #606266;
}
</style>