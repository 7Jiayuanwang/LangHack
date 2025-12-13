<template>
  <div class="learning-suggestion-page">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>学习建议</span>
          <el-button type="primary" @click="generateSuggestions" :loading="generating">
            <el-icon><MagicStick /></el-icon>
            生成建议
          </el-button>
        </div>
      </template>

      <div v-if="suggestions.length === 0 && !loading" class="empty-state">
        <el-empty description="暂无学习建议">
          <el-button type="primary" @click="generateSuggestions">生成建议</el-button>
        </el-empty>
      </div>

      <div v-else class="suggestions-container" v-loading="loading">
        <el-row :gutter="20">
          <el-col
            v-for="suggestion in suggestions"
            :key="suggestion.id"
            :xs="24"
            :sm="12"
            :md="8"
            :lg="8"
          >
            <el-card class="suggestion-card" :class="getSuggestionClass(suggestion)">
              <div class="suggestion-header">
                <div class="icon-wrapper" :class="getTypeClass(suggestion.type)">
                  <el-icon :size="24">
                    <component :is="getTypeIcon(suggestion.type)" />
                  </el-icon>
                </div>
                <div class="header-info">
                  <h3>{{ suggestion.title }}</h3>
                  <el-tag :type="getPriorityType(suggestion.priority)" size="small">
                    {{ getPriorityText(suggestion.priority) }}
                  </el-tag>
                </div>
              </div>

              <div class="suggestion-content">
                <p>{{ suggestion.content }}</p>
              </div>

              <div class="suggestion-meta">
                <el-tag size="small" type="info">{{ getTypeText(suggestion.type) }}</el-tag>
                <span class="create-time">{{ formatDate(suggestion.createTime) }}</span>
              </div>

              <div class="suggestion-progress" v-if="suggestion.status === 'IN_PROGRESS'">
                <span>完成进度</span>
                <el-progress
                  :percentage="suggestion.progress"
                  :color="getProgressColor(suggestion.progress)"
                  :stroke-width="6"
                />
              </div>

              <div class="suggestion-actions">
                <el-button
                  v-if="!suggestion.expanded"
                  size="small"
                  @click="expandSuggestion(suggestion)"
                >
                  查看详情
                  <el-icon><ArrowDown /></el-icon>
                </el-button>
                <el-button
                  v-else
                  size="small"
                  @click="collapseSuggestion(suggestion)"
                >
                  收起
                  <el-icon><ArrowUp /></el-icon>
                </el-button>
                <el-button
                  v-if="suggestion.status === 'PENDING'"
                  type="primary"
                  size="small"
                  @click="startSuggestion(suggestion)"
                >
                  开始执行
                </el-button>
                <el-button
                  v-if="suggestion.status === 'IN_PROGRESS'"
                  type="success"
                  size="small"
                  @click="completeSuggestion(suggestion)"
                >
                  标记完成
                </el-button>
                <el-button
                  v-if="suggestion.status === 'PENDING'"
                  type="info"
                  size="small"
                  @click="ignoreSuggestion(suggestion)"
                >
                  忽略
                </el-button>
              </div>

              <!-- 展开的详细内容 -->
              <div v-if="suggestion.expanded" class="suggestion-detail">
                <el-divider />
                <div class="detail-section">
                  <h4>详细说明</h4>
                  <p>{{ suggestion.detailDescription }}</p>
                </div>
                <div class="detail-section" v-if="suggestion.steps && suggestion.steps.length > 0">
                  <h4>执行步骤</h4>
                  <el-timeline>
                    <el-timeline-item
                      v-for="(step, index) in suggestion.steps"
                      :key="index"
                      :timestamp="step.title"
                      :type="step.completed ? 'success' : 'primary'"
                    >
                      {{ step.description }}
                      <el-button
                        v-if="!step.completed"
                        size="small"
                        type="success"
                        @click="completeStep(suggestion, index)"
                        class="step-button"
                      >
                        完成
                      </el-button>
                    </el-timeline-item>
                  </el-timeline>
                </div>
                <div class="detail-section" v-if="suggestion.resources && suggestion.resources.length > 0">
                  <h4>推荐资源</h4>
                  <ul class="resource-list">
                    <li v-for="(resource, index) in suggestion.resources" :key="index">
                      <el-link :href="resource.url" target="_blank" type="primary">
                        {{ resource.title }}
                      </el-link>
                    </li>
                  </ul>
                </div>
              </div>
            </el-card>
          </el-col>
        </el-row>
      </div>
    </el-card>

    <!-- 历史建议对话框 -->
    <el-dialog
      v-model="historyDialogVisible"
      title="历史建议"
      width="900px"
    >
      <el-table :data="historySuggestions" stripe>
        <el-table-column prop="title" label="建议标题" min-width="200" />
        <el-table-column label="类型" width="120">
          <template #default="scope">
            <el-tag size="small">{{ getTypeText(scope.row.type) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="优先级" width="100">
          <template #default="scope">
            <el-tag :type="getPriorityType(scope.row.priority)" size="small">
              {{ getPriorityText(scope.row.priority) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="scope">
            <el-tag :type="getStatusType(scope.row.status)" size="small">
              {{ getStatusText(scope.row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" width="180">
          <template #default="scope">
            {{ formatDate(scope.row.createTime) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="100">
          <template #default="scope">
            <el-button size="small" @click="viewHistoryDetail(scope.row)">
              查看
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  Reading,
  Edit,
  TrendCharts,
  ChatLineRound,
  VideoPlay,
  Document,
  MagicStick,
  ArrowDown,
  ArrowUp
} from '@element-plus/icons-vue'
import {
  generateLearningSuggestions,
  getLearningSuggestions,
  completeSuggestion as completeSuggestionApi
} from '@/api/weakness'

// 响应式数据
const loading = ref(false)
const generating = ref(false)
const suggestions = ref([])
const historyDialogVisible = ref(false)
const historySuggestions = ref([])

// 方法
const generateSuggestions = async () => {
  try {
    generating.value = true
    // await generateLearningSuggestions()
    await loadSuggestions()
    ElMessage.success('建议生成成功')
  } catch (error) {
    ElMessage.error('生成建议失败')
  } finally {
    generating.value = false
  }
}

const loadSuggestions = async () => {
  try {
    loading.value = true
    // const response = await getLearningSuggestions({ status: 'PENDING,IN_PROGRESS' })
    // suggestions.value = response.data

    // 模拟数据
    suggestions.value = [
      {
        id: 1,
        title: '加强动词时态学习',
        type: 'GRAMMAR',
        priority: 'HIGH',
        status: 'PENDING',
        content: '您在动词时态相关的题目中正确率较低（45%），建议系统学习各种时态的用法和区别。',
        detailDescription: '动词时态是英语语法的基础，掌握好时态对于理解和表达都非常重要。建议从一般现在时、一般过去时、一般将来时开始，逐步学习现在进行时、过去进行时等更复杂的时态。',
        progress: 0,
        createTime: new Date().toISOString(),
        expanded: false,
        steps: [
          { title: '第一步', description: '复习一般现在时的用法和句型', completed: false },
          { title: '第二步', description: '学习一般过去时和不规则动词变化', completed: false },
          { title: '第三步', description: '完成时态专项练习20题', completed: false },
          { title: '第四步', description: '进行综合测试检验学习效果', completed: false }
        ],
        resources: [
          { title: '英语时态详解教程', url: '#' },
          { title: '时态练习题库', url: '#' }
        ]
      },
      {
        id: 2,
        title: '每日词汇积累计划',
        type: 'VOCABULARY',
        priority: 'MEDIUM',
        status: 'IN_PROGRESS',
        content: '建议每天学习10个新单词，并复习之前学过的单词，提高词汇量。',
        detailDescription: '词汇是语言学习的基石。建议采用艾宾浩斯记忆曲线，合理安排复习时间，提高记忆效果。',
        progress: 35,
        createTime: new Date(Date.now() - 2 * 24 * 60 * 60 * 1000).toISOString(),
        expanded: false,
        steps: [
          { title: '每天学习', description: '学习10个新单词', completed: true },
          { title: '每天复习', description: '复习昨天学的单词', completed: true },
          { title: '每周复习', description: '周末复习本周所有单词', completed: false },
          { title: '月度测试', description: '每月进行词汇测试', completed: false }
        ],
        resources: [
          { title: '高频词汇表', url: '#' },
          { title: '词汇记忆技巧', url: '#' }
        ]
      },
      {
        id: 3,
        title: '提升阅读理解能力',
        type: 'READING',
        priority: 'MEDIUM',
        status: 'PENDING',
        content: '每周完成2篇英语文章阅读，提高阅读速度和理解能力。',
        detailDescription: '阅读是提高英语综合能力的重要途径。建议选择适合自己水平的文章，从简单到复杂，循序渐进。',
        progress: 0,
        createTime: new Date().toISOString(),
        expanded: false,
        steps: [
          { title: '第一周', description: '阅读2篇简单文章', completed: false },
          { title: '第二周', description: '阅读2篇中等难度文章', completed: false },
          { title: '第三周', description: '阅读2篇较难文章', completed: false },
          { title: '第四周', description: '总结阅读技巧和心得', completed: false }
        ],
        resources: [
          { title: '英语阅读材料推荐', url: '#' },
          { title: '阅读理解技巧', url: '#' }
        ]
      },
      {
        id: 4,
        title: '听力训练强化',
        type: 'LISTENING',
        priority: 'LOW',
        status: 'PENDING',
        content: '每天听英语材料30分钟，提高听力理解能力。',
        detailDescription: '听力训练需要持续的练习和积累。建议从慢速英语开始，逐步过渡到正常语速。',
        progress: 0,
        createTime: new Date().toISOString(),
        expanded: false,
        steps: [
          { title: '准备阶段', description: '下载听力材料和APP', completed: false },
          { title: '初级阶段', description: '听慢速英语新闻', completed: false },
          { title: '进阶阶段', description: '听正常语速对话', completed: false },
          { title: '高级阶段', description: '听英语演讲和讲座', completed: false }
        ],
        resources: [
          { title: 'BBC Learning English', url: '#' },
          { title: 'VOA慢速英语', url: '#' }
        ]
      },
      {
        id: 5,
        title: '写作能力提升',
        type: 'WRITING',
        priority: 'MEDIUM',
        status: 'PENDING',
        content: '每周写一篇英语短文，提高写作表达能力。',
        detailDescription: '写作是输出能力的体现。建议从简单的日记开始，逐步尝试不同题材的写作。',
        progress: 0,
        createTime: new Date().toISOString(),
        expanded: false,
        steps: [
          { title: '第一周', description: '写英语日记', completed: false },
          { title: '第二周', description: '写记叙文', completed: false },
          { title: '第三周', description: '写议论文', completed: false },
          { title: '第四周', description: '总结写作技巧', completed: false }
        ],
        resources: [
          { title: '英语写作指南', url: '#' },
          { title: '优秀范文赏析', url: '#' }
        ]
      },
      {
        id: 6,
        title: '口语练习计划',
        type: 'SPEAKING',
        priority: 'HIGH',
        status: 'PENDING',
        content: '每天进行15分钟的英语口语练习，可以跟读或者自我对话。',
        detailDescription: '口语能力需要大量的练习。建议模仿native speaker的发音和语调，多进行实际对话练习。',
        progress: 0,
        createTime: new Date().toISOString(),
        expanded: false,
        steps: [
          { title: '发音练习', description: '学习音标和发音规则', completed: false },
          { title: '跟读练习', description: '跟读英语材料', completed: false },
          { title: '对话练习', description: '模拟日常对话场景', completed: false },
          { title: '表达练习', description: '就某个话题进行表达', completed: false }
        ],
        resources: [
          { title: '英语发音教程', url: '#' },
          { title: '日常对话素材', url: '#' }
        ]
      }
    ]
  } catch (error) {
    ElMessage.error('加载建议失败')
  } finally {
    loading.value = false
  }
}

const expandSuggestion = (suggestion) => {
  suggestion.expanded = true
}

const collapseSuggestion = (suggestion) => {
  suggestion.expanded = false
}

const startSuggestion = async (suggestion) => {
  try {
    suggestion.status = 'IN_PROGRESS'
    suggestion.progress = 0
    ElMessage.success('已开始执行该建议')
  } catch (error) {
    ElMessage.error('操作失败')
  }
}

const completeSuggestion = async (suggestion) => {
  try {
    await ElMessageBox.confirm('确认已完成该建议？', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'success'
    })

    // await completeSuggestionApi(suggestion.id)
    suggestion.status = 'COMPLETED'
    suggestion.progress = 100
    ElMessage.success('已标记为完成')
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('操作失败')
    }
  }
}

const ignoreSuggestion = async (suggestion) => {
  try {
    await ElMessageBox.confirm('确认忽略该建议？', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })

    // await ignoreSuggestionApi(suggestion.id)
    const index = suggestions.value.findIndex(s => s.id === suggestion.id)
    if (index !== -1) {
      suggestions.value.splice(index, 1)
    }
    ElMessage.success('已忽略该建议')
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('操作失败')
    }
  }
}

const completeStep = async (suggestion, stepIndex) => {
  try {
    suggestion.steps[stepIndex].completed = true
    const completedSteps = suggestion.steps.filter(s => s.completed).length
    const newProgress = Math.round((completedSteps / suggestion.steps.length) * 100)
    suggestion.progress = newProgress

    // await updateSuggestionProgress(suggestion.id, { progress: newProgress })
    ElMessage.success('步骤已完成')
  } catch (error) {
    ElMessage.error('操作失败')
  }
}

const viewHistoryDetail = (suggestion) => {
  ElMessage.info(`查看建议详情：${suggestion.title}`)
}

// 工具方法
const getSuggestionClass = (suggestion) => {
  return {
    'completed': suggestion.status === 'COMPLETED',
    'in-progress': suggestion.status === 'IN_PROGRESS'
  }
}

const getTypeClass = (type) => {
  return `type-${type.toLowerCase()}`
}

const getTypeIcon = (type) => {
  const icons = {
    'GRAMMAR': Edit,
    'VOCABULARY': Document,
    'READING': Reading,
    'LISTENING': VideoPlay,
    'WRITING': Edit,
    'SPEAKING': ChatLineRound,
    'PRACTICE': TrendCharts
  }
  return icons[type] || Document
}

const getTypeText = (type) => {
  const texts = {
    'GRAMMAR': '语法',
    'VOCABULARY': '词汇',
    'READING': '阅读',
    'LISTENING': '听力',
    'WRITING': '写作',
    'SPEAKING': '口语',
    'PRACTICE': '练习'
  }
  return texts[type] || type
}

const getPriorityType = (priority) => {
  const types = {
    'HIGH': 'danger',
    'MEDIUM': 'warning',
    'LOW': 'info'
  }
  return types[priority] || ''
}

const getPriorityText = (priority) => {
  const texts = {
    'HIGH': '高优先级',
    'MEDIUM': '中优先级',
    'LOW': '低优先级'
  }
  return texts[priority] || priority
}

const getStatusType = (status) => {
  const types = {
    'PENDING': 'info',
    'IN_PROGRESS': 'warning',
    'COMPLETED': 'success',
    'IGNORED': 'info'
  }
  return types[status] || ''
}

const getStatusText = (status) => {
  const texts = {
    'PENDING': '待执行',
    'IN_PROGRESS': '进行中',
    'COMPLETED': '已完成',
    'IGNORED': '已忽略'
  }
  return texts[status] || status
}

const getProgressColor = (progress) => {
  if (progress >= 80) return '#67c23a'
  if (progress >= 50) return '#e6a23c'
  return '#409EFF'
}

const formatDate = (dateString) => {
  if (!dateString) return '-'
  return new Date(dateString).toLocaleString()
}

// 生命周期
onMounted(() => {
  loadSuggestions()
})
</script>

<style scoped>
.learning-suggestion-page {
  padding: 20px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.empty-state {
  padding: 60px 0;
}

.suggestions-container {
  min-height: 400px;
}

.suggestion-card {
  margin-bottom: 20px;
  transition: all 0.3s;
  height: 100%;
  display: flex;
  flex-direction: column;
}

.suggestion-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
}

.suggestion-card.completed {
  opacity: 0.8;
  border-left: 4px solid #67c23a;
}

.suggestion-card.in-progress {
  border-left: 4px solid #e6a23c;
}

.suggestion-header {
  display: flex;
  align-items: flex-start;
  margin-bottom: 15px;
}

.icon-wrapper {
  width: 50px;
  height: 50px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-right: 15px;
  color: white;
  flex-shrink: 0;
}

.icon-wrapper.type-grammar {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
}

.icon-wrapper.type-vocabulary {
  background: linear-gradient(135deg, #f093fb 0%, #f5576c 100%);
}

.icon-wrapper.type-reading {
  background: linear-gradient(135deg, #4facfe 0%, #00f2fe 100%);
}

.icon-wrapper.type-listening {
  background: linear-gradient(135deg, #43e97b 0%, #38f9d7 100%);
}

.icon-wrapper.type-writing {
  background: linear-gradient(135deg, #fa709a 0%, #fee140 100%);
}

.icon-wrapper.type-speaking {
  background: linear-gradient(135deg, #30cfd0 0%, #330867 100%);
}

.icon-wrapper.type-practice {
  background: linear-gradient(135deg, #a8edea 0%, #fed6e3 100%);
}

.header-info {
  flex: 1;
}

.header-info h3 {
  margin: 0 0 8px;
  font-size: 16px;
  color: #303133;
}

.suggestion-content {
  margin-bottom: 15px;
  flex: 1;
}

.suggestion-content p {
  margin: 0;
  color: #606266;
  line-height: 1.6;
}

.suggestion-meta {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
  font-size: 12px;
  color: #909399;
}

.suggestion-progress {
  margin-bottom: 12px;
}

.suggestion-progress span {
  display: block;
  margin-bottom: 8px;
  font-size: 13px;
  color: #606266;
}

.suggestion-actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.suggestion-detail {
  margin-top: 15px;
}

.detail-section {
  margin-bottom: 20px;
}

.detail-section h4 {
  margin: 0 0 12px;
  color: #303133;
  font-size: 15px;
}

.detail-section p {
  margin: 0;
  color: #606266;
  line-height: 1.6;
}

.step-button {
  margin-left: 10px;
}

.resource-list {
  margin: 0;
  padding-left: 20px;
}

.resource-list li {
  margin-bottom: 8px;
}
</style>
