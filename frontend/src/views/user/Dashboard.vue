<template>
  <div class="user-dashboard">
    <!-- 欢迎信息 -->
    <el-card class="welcome-card">
      <div class="welcome-content">
        <h2>欢迎回来，{{ userStore.userInfo?.realName || userStore.userInfo?.username }}！</h2>
        <p>在英语学习平台上继续您的学习之旅</p>
      </div>
    </el-card>

    <!-- 统计卡片 -->
    <el-row :gutter="20" class="stats-row">
      <el-col :span="6">
        <el-card class="stat-card">
          <div class="stat-item">
            <div class="stat-icon courses">
              <el-icon><Reading /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-number">{{ stats.totalCourses }}</div>
              <div class="stat-label">可用课程</div>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card class="stat-card">
          <div class="stat-item">
            <div class="stat-icon texts">
              <el-icon><Document /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-number">{{ stats.totalTexts }}</div>
              <div class="stat-label">课文总数</div>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card class="stat-card">
          <div class="stat-item">
            <div class="stat-icon uploads">
              <el-icon><Upload /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-number">{{ stats.myUploads }}</div>
              <div class="stat-label">我的上传</div>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card class="stat-card">
          <div class="stat-item">
            <div class="stat-icon words">
              <el-icon><Notebook /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-number">{{ stats.learnedWords }}</div>
              <div class="stat-label">学习单词</div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 快速操作 -->
    <el-card class="quick-actions-card">
      <template #header>
        <span>快速操作</span>
      </template>
      <el-row :gutter="20">
        <el-col :span="6">
          <el-card class="action-card" @click="goToCourses">
            <div class="action-content">
              <el-icon class="action-icon"><Reading /></el-icon>
              <span>浏览课程</span>
            </div>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card class="action-card" @click="goToTexts">
            <div class="action-content">
              <el-icon class="action-icon"><Document /></el-icon>
              <span>学习课文</span>
            </div>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card class="action-card" @click="goToUploads">
            <div class="action-content">
              <el-icon class="action-icon"><Upload /></el-icon>
              <span>上传内容</span>
            </div>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card class="action-card" @click="goToVocabulary">
            <div class="action-content">
              <el-icon class="action-icon"><Notebook /></el-icon>
              <span>生词本</span>
            </div>
          </el-card>
        </el-col>
      </el-row>
    </el-card>

    <!-- 最近学习内容 -->
    <el-row :gutter="20" class="recent-content">
      <el-col :span="12">
        <el-card>
          <template #header>
            <div class="card-header">
              <span>最近上传</span>
              <el-button type="text" @click="goToUploads">查看更多</el-button>
            </div>
          </template>
          <div v-if="recentUploads.length === 0" class="empty-content">
            <el-empty description="暂无上传内容" />
            <el-button type="primary" @click="goToUploads" style="margin-top: 10px">
              立即上传
            </el-button>
          </div>
          <div v-else class="recent-list">
            <div
              v-for="upload in recentUploads"
              :key="upload.id"
              class="recent-item"
              @click="viewText(upload)"
            >
              <div class="item-title">{{ upload.title }}</div>
              <div class="item-meta">
                <el-tag :type="getDifficultyType(upload.difficulty)" size="small">
                  {{ getDifficultyText(upload.difficulty) }}
                </el-tag>
                <span class="item-time">{{ formatDate(upload.createdAt) }}</span>
              </div>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card>
          <template #header>
            <div class="card-header">
              <span>推荐课文</span>
              <el-button type="text" @click="goToTexts">查看更多</el-button>
            </div>
          </template>
          <div v-if="recommendedTexts.length === 0" class="empty-content">
            <el-empty description="暂无推荐内容" />
          </div>
          <div v-else class="recent-list">
            <div
              v-for="text in recommendedTexts"
              :key="text.id"
              class="recent-item"
              @click="studyText(text)"
            >
              <div class="item-title">{{ text.title }}</div>
              <div class="item-meta">
                <el-tag :type="getDifficultyType(text.difficulty)" size="small">
                  {{ getDifficultyText(text.difficulty) }}
                </el-tag>
                <span class="item-time">第{{ text.unitId }}单元</span>
              </div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/store/user'
import { ElMessage } from 'element-plus'
import { Reading, Document, Upload, Notebook } from '@element-plus/icons-vue'
import request from '@/utils/request'

const router = useRouter()
const userStore = useUserStore()

const stats = reactive({
  totalCourses: 0,
  totalTexts: 0,
  myUploads: 0,
  learnedWords: 0
})

const recentUploads = ref([])
const recommendedTexts = ref([])

const loadDashboardData = async () => {
  // 用 allSettled 保证单个请求失败不影响其他数据加载
  const [coursesResult, uploadsResult, vocabResult] = await Promise.allSettled([
    request({
      url: '/api/student/learning/courses',
      method: 'get'
    }),
    request({
      url: '/api/user/texts/my-uploads',
      method: 'get'
    }),
    request({
      url: '/api/student/learning/vocabulary/stats',
      method: 'get'
    })
  ])

  if (coursesResult.status === 'fulfilled' && coursesResult.value?.code === 200) {
    const data = coursesResult.value.data
    stats.totalCourses = (Array.isArray(data) ? data : (Array.isArray(data?.records) ? data.records : [])).length
  }

  // 设置默认课文数量
  stats.totalTexts = 0

  if (uploadsResult.status === 'fulfilled' && uploadsResult.value?.code === 200) {
    const uploads = uploadsResult.value.data
    const uploadsList = Array.isArray(uploads) ? uploads : (Array.isArray(uploads?.records) ? uploads.records : [])
    stats.myUploads = uploadsList.length
    // 获取最近3个上传
    recentUploads.value = uploadsList.slice(0, 3)
  }

  if (vocabResult.status === 'fulfilled' && vocabResult.value?.code === 200) {
    const vocabData = vocabResult.value.data
    stats.learnedWords = vocabData?.totalWords || vocabData?.total || 0
  }
}

const loadRecommendedTexts = async () => {
  try {
    const response = await request({
      url: '/api/public/texts/random',
      method: 'get',
      params: { count: 3 }
    })

    if (response?.code === 200 && response.data) {
      const data = response.data
      let textList = []
      
      if (Array.isArray(data)) {
        textList = data
      } else if (data?.records && Array.isArray(data.records)) {
        textList = data.records
      }
      
      recommendedTexts.value = textList.filter(t => t && t.id)
    } else {
      recommendedTexts.value = []
    }
  } catch (error) {
    console.error('加载推荐课文失败:', error)
    recommendedTexts.value = []
  }
}

const goToCourses = () => {
  router.push('/user/courses')
}

const goToTexts = () => {
  router.push('/user/texts')
}

const goToUploads = () => {
  router.push('/user/user-texts')
}

const goToVocabulary = () => {
  router.push('/user/vocabulary')
}

const viewText = (text) => {
  if (!text?.id) {
    ElMessage.error('文本信息不完整')
    return
  }
  router.push({
    path: '/user/text-learning',
    query: {
      textId: text.id,
      from: 'user-upload'
    }
  })
}

const studyText = (text) => {
  if (!text?.id) {
    ElMessage.error('文本信息不完整')
    return
  }
  router.push({
    path: '/user/text-learning',
    query: {
      textId: text.id,
      unitId: text?.unitId || ''
    }
  })
}

const getDifficultyType = (difficulty) => {
  const types = {
    'EASY': 'success',
    'MEDIUM': 'warning',
    'HARD': 'danger'
  }
  return types[difficulty] || 'info'
}

const getDifficultyText = (difficulty) => {
  const texts = {
    'EASY': '简单',
    'MEDIUM': '中等',
    'HARD': '困难'
  }
  return texts[difficulty] || '未知'
}

const formatDate = (dateString) => {
  if (!dateString) return ''
  try {
    const date = new Date(dateString)
    if (isNaN(date.getTime())) return ''
    return date.toLocaleDateString()
  } catch (error) {
    console.error('日期格式化错误:', error)
    return ''
  }
}

onMounted(() => {
  loadDashboardData()
  loadRecommendedTexts()
})
</script>

<style scoped>
.user-dashboard {
  padding: 0;
}

.welcome-card {
  margin-bottom: 20px;
}

.welcome-content {
  text-align: center;
  padding: 20px 0;
}

.welcome-content h2 {
  margin: 0 0 10px;
  color: #333;
  font-size: 28px;
}

.welcome-content p {
  margin: 0;
  color: #666;
  font-size: 16px;
}

.stats-row {
  margin-bottom: 20px;
}

.stat-card {
  cursor: default;
}

.stat-item {
  display: flex;
  align-items: center;
  padding: 10px;
}

.stat-icon {
  width: 60px;
  height: 60px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-right: 15px;
  font-size: 24px;
  color: white;
}

.stat-icon.courses {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
}

.stat-icon.texts {
  background: linear-gradient(135deg, #f093fb 0%, #f5576c 100%);
}

.stat-icon.uploads {
  background: linear-gradient(135deg, #4facfe 0%, #00f2fe 100%);
}

.stat-icon.words {
  background: linear-gradient(135deg, #43e97b 0%, #38f9d7 100%);
}

.stat-info {
  flex: 1;
}

.stat-number {
  font-size: 32px;
  font-weight: bold;
  color: #333;
  line-height: 1;
}

.stat-label {
  font-size: 14px;
  color: #666;
  margin-top: 5px;
}

.quick-actions-card {
  margin-bottom: 20px;
}

.action-card {
  cursor: pointer;
  transition: all 0.3s;
  text-align: center;
}

.action-card:hover {
  transform: translateY(-5px);
  box-shadow: 0 8px 25px rgba(0, 0, 0, 0.1);
}

.action-content {
  padding: 20px;
}

.action-icon {
  font-size: 32px;
  color: #409EFF;
  margin-bottom: 10px;
  display: block;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.recent-content {
  margin-bottom: 20px;
}

.empty-content {
  text-align: center;
  padding: 20px;
}

.recent-list {
  max-height: 300px;
  overflow-y: auto;
}

.recent-item {
  padding: 12px 0;
  border-bottom: 1px solid #f0f0f0;
  cursor: pointer;
  transition: all 0.3s;
}

.recent-item:last-child {
  border-bottom: none;
}

.recent-item:hover {
  background-color: #f8f9fa;
  padding-left: 10px;
  margin: 0 -10px;
}

.item-title {
  font-size: 14px;
  font-weight: 500;
  color: #333;
  margin-bottom: 5px;
  line-height: 1.4;
}

.item-meta {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 12px;
  color: #999;
}

.item-time {
  color: #999;
}

/* 响应式设计 */
@media (max-width: 768px) {
  .stats-row {
    .el-col {
      margin-bottom: 10px;
    }
  }

  .quick-actions-card .el-row .el-col {
    margin-bottom: 10px;
  }

  .recent-content .el-col {
    margin-bottom: 20px;
  }
}
</style>