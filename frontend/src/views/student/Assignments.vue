<template>
  <div class="assignments">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>我的任务</span>
          <el-select v-model="filterStatus" placeholder="筛选状态" clearable @change="loadAssignments">
            <el-option label="全部" value="" />
            <el-option label="未开始" value="not-started" />
            <el-option label="进行中" value="in-progress" />
            <el-option label="已完成" value="completed" />
            <el-option label="已过期" value="expired" />
          </el-select>
        </div>
      </template>

      <!-- 任务统计 -->
      <div class="stats-row">
        <div class="stat-card">
          <div class="stat-icon pending">
            <el-icon><Clock /></el-icon>
          </div>
          <div class="stat-info">
            <h3>{{ stats.pendingCount }}</h3>
            <p>待完成</p>
          </div>
        </div>
        <div class="stat-card">
          <div class="stat-icon progress">
            <el-icon><Loading /></el-icon>
          </div>
          <div class="stat-info">
            <h3>{{ stats.inProgressCount }}</h3>
            <p>进行中</p>
          </div>
        </div>
        <div class="stat-card">
          <div class="stat-icon completed">
            <el-icon><Check /></el-icon>
          </div>
          <div class="stat-info">
            <h3>{{ stats.completedCount }}</h3>
            <p>已完成</p>
          </div>
        </div>
        <div class="stat-card">
          <div class="stat-icon expired">
            <el-icon><Warning /></el-icon>
          </div>
          <div class="stat-info">
            <h3>{{ stats.expiredCount }}</h3>
            <p>已过期</p>
          </div>
        </div>
      </div>

      <!-- 任务列表 -->
      <el-table :data="assignments" stripe>
        <el-table-column prop="title" label="任务标题" />
        <el-table-column prop="className" label="班级" />
        <el-table-column prop="unitName" label="学习单元" />
        <el-table-column prop="deadline" label="截止时间" width="180">
          <template #default="scope">
            <span :class="getDeadlineClass(scope.row.deadline, scope.row.status)">
              {{ formatTime(scope.row.deadline) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="100">
          <template #default="scope">
            <el-tag :type="getStatusType(scope.row.status)">
              {{ getStatusText(scope.row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="progress" label="进度" width="150">
          <template #default="scope">
            <el-progress
              :percentage="scope.row.progress"
              :color="getProgressColor(scope.row.progress)"
              :stroke-width="6"
            />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200">
          <template #default="scope">
            <el-button
              v-if="scope.row.status === 'not-started'"
              size="small"
              type="primary"
              @click="startAssignment(scope.row)"
            >
              开始任务
            </el-button>
            <el-button
              v-if="scope.row.status === 'in-progress'"
              size="small"
              @click="continueAssignment(scope.row)"
            >
              继续学习
            </el-button>
            <el-button
              v-if="scope.row.status === 'completed'"
              size="small"
              @click="reviewAssignment(scope.row)"
            >
              复习
            </el-button>
            <el-button
              size="small"
              @click="viewAssignmentDetail(scope.row)"
            >
              查看详情
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 空状态 -->
      <el-empty v-if="assignments.length === 0" description="暂无任务">
        <el-button type="primary" @click="loadAssignments">刷新任务</el-button>
      </el-empty>
    </el-card>

    <!-- 任务详情对话框 -->
    <el-dialog
      v-model="detailDialogVisible"
      :title="currentAssignment.title"
      width="800px"
    >
      <div class="assignment-detail">
        <div class="detail-header">
          <div class="assignment-info">
            <p><strong>班级：</strong>{{ currentAssignment.className }}</p>
            <p><strong>单元：</strong>{{ currentAssignment.unitName }}</p>
            <p><strong>截止时间：</strong>{{ formatTime(currentAssignment.deadline) }}</p>
            <p><strong>任务描述：</strong>{{ currentAssignment.description }}</p>
          </div>
          <div class="progress-summary">
            <h4>学习进度</h4>
            <el-progress
              :percentage="currentAssignment.progress"
              :color="getProgressColor(currentAssignment.progress)"
              :stroke-width="8"
            />
            <div class="progress-stats">
              <span>已完成：{{ currentAssignment.completedItems }}/{{ currentAssignment.totalItems }}</span>
            </div>
          </div>
        </div>

        <div class="learning-content">
          <el-tabs type="border-card">
            <el-tab-pane label="课文学习" name="texts">
              <div class="content-list">
                <div
                  v-for="text in currentAssignment.texts"
                  :key="text.id"
                  class="content-item"
                  :class="{ completed: text.completed }"
                  @click="studyText(text)"
                >
                  <div class="item-header">
                    <span class="title">{{ text.title }}</span>
                    <el-tag v-if="text.completed" type="success" size="small">已完成</el-tag>
                    <el-tag v-else type="info" size="small">未学习</el-tag>
                  </div>
                  <div class="item-meta">
                    <span>难度：{{ getDifficultyText(text.difficulty) }}</span>
                    <span>学习时间：{{ text.studyTime || '-' }}分钟</span>
                  </div>
                </div>
              </div>
            </el-tab-pane>

            <el-tab-pane label="单词学习" name="words">
              <div class="words-grid">
                <div
                  v-for="word in currentAssignment.words"
                  :key="word.id"
                  class="word-item"
                  :class="{ completed: word.completed }"
                  @click="studyWord(word)"
                >
                  <div class="word-header">
                    <h4>{{ word.word }}</h4>
                    <el-tag v-if="word.completed" type="success" size="small">已掌握</el-tag>
                  </div>
                  <div class="pronunciation">{{ word.pronunciation }}</div>
                  <div class="definition">{{ word.definition }}</div>
                  <div class="review-count">复习次数：{{ word.reviewCount || 0 }}</div>
                </div>
              </div>
            </el-tab-pane>

            <el-tab-pane label="学习记录" name="records">
              <div class="records-list">
                <div
                  v-for="record in currentAssignment.records"
                  :key="record.id"
                  class="record-item"
                >
                  <div class="record-time">{{ formatTime(record.createdAt) }}</div>
                  <div class="record-content">
                    <span class="action-type">{{ getActionTypeText(record.actionType) }}</span>
                    <span class="content">{{ record.content }}</span>
                    <span class="duration">{{ record.duration }}分钟</span>
                  </div>
                </div>
              </div>
            </el-tab-pane>
          </el-tabs>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'

// 响应式数据
const assignments = ref([])
const filterStatus = ref('')
const detailDialogVisible = ref(false)
const currentAssignment = ref({})

// 统计数据
const stats = reactive({
  pendingCount: 0,
  inProgressCount: 0,
  completedCount: 0,
  expiredCount: 0
})

// 方法
const loadAssignments = async () => {
  try {
    // 模拟API调用
    await new Promise(resolve => setTimeout(resolve, 500))

    const mockAssignments = [
      {
        id: 1,
        title: '第一单元单词学习',
        className: '英语初级一班',
        unitName: '第一单元',
        description: '学习第一单元的25个核心单词，掌握基本词汇和用法',
        deadline: '2024-01-25T23:59:59',
        status: 'in-progress',
        progress: 60,
        completedItems: 15,
        totalItems: 25,
        texts: [
          { id: 1, title: 'Greetings and Introductions', difficulty: 'EASY', completed: true, studyTime: 15 },
          { id: 2, title: 'Daily Conversations', difficulty: 'MEDIUM', completed: true, studyTime: 20 },
          { id: 3, title: 'Time and Dates', difficulty: 'MEDIUM', completed: false, studyTime: 0 }
        ],
        words: [
          { id: 1, word: 'hello', pronunciation: '/həˈləʊ/', definition: '你好', completed: true, reviewCount: 3 },
          { id: 2, word: 'name', pronunciation: '/neɪm/', definition: '名字', completed: true, reviewCount: 2 },
          { id: 3, word: 'conversation', pronunciation: '/ˌkɒnvəˈseɪʃn/', definition: '对话', completed: false, reviewCount: 0 }
        ],
        records: [
          { id: 1, actionType: 'VIEW', content: '学习课文：Greetings and Introductions', duration: 15, createdAt: '2024-01-18T10:30:00' },
          { id: 2, actionType: 'REVIEW', content: '复习单词：hello, name', duration: 10, createdAt: '2024-01-18T14:20:00' }
        ]
      },
      {
        id: 2,
        title: '第二单元课文阅读',
        className: '英语初级一班',
        unitName: '第二单元',
        description: '阅读和理解第二单元的课文，提高阅读理解能力',
        deadline: '2024-01-30T23:59:59',
        status: 'not-started',
        progress: 0,
        completedItems: 0,
        totalItems: 20,
        texts: [],
        words: [],
        records: []
      },
      {
        id: 3,
        title: '听力练习作业',
        className: '英语进阶一班',
        unitName: '第三单元',
        description: '完成听力练习，提高听力理解能力',
        deadline: '2024-01-15T23:59:59',
        status: 'completed',
        progress: 100,
        completedItems: 10,
        totalItems: 10,
        texts: [],
        words: [],
        records: []
      }
    ]

    // 应用筛选
    let filteredAssignments = mockAssignments
    if (filterStatus.value) {
      filteredAssignments = mockAssignments.filter(assignment => assignment.status === filterStatus.value)
    }

    assignments.value = filteredAssignments

    // 更新统计数据
    stats.pendingCount = mockAssignments.filter(a => a.status === 'not-started').length
    stats.inProgressCount = mockAssignments.filter(a => a.status === 'in-progress').length
    stats.completedCount = mockAssignments.filter(a => a.status === 'completed').length
    stats.expiredCount = mockAssignments.filter(a => a.status === 'expired').length
  } catch (error) {
    ElMessage.error('加载任务列表失败')
  }
}

const startAssignment = (assignment) => {
  ElMessage.info(`开始任务：${assignment.title}`)
  viewAssignmentDetail(assignment)
}

const continueAssignment = (assignment) => {
  ElMessage.info(`继续学习：${assignment.title}`)
  viewAssignmentDetail(assignment)
}

const reviewAssignment = (assignment) => {
  ElMessage.info(`复习任务：${assignment.title}`)
  viewAssignmentDetail(assignment)
}

const viewAssignmentDetail = (assignment) => {
  currentAssignment.value = assignment
  detailDialogVisible.value = true
}

const studyText = (text) => {
  ElMessage.info(`学习课文：${text.title}`)
  // 标记为已完成
  text.completed = true
  text.studyTime = 15
}

const studyWord = (word) => {
  ElMessage.info(`学习单词：${word.word}`)
  // 标记为已完成
  word.completed = true
  word.reviewCount = (word.reviewCount || 0) + 1
}

// 工具方法
const getDeadlineClass = (deadline, status) => {
  if (status === 'completed') return 'completed'
  const now = new Date()
  const deadlineDate = new Date(deadline)
  const diffHours = (deadlineDate - now) / (1000 * 60 * 60)

  if (diffHours < 0) return 'expired'
  if (diffHours < 24) return 'urgent'
  return ''
}

const getStatusType = (status) => {
  const types = {
    'not-started': 'info',
    'in-progress': 'warning',
    'completed': 'success',
    'expired': 'danger'
  }
  return types[status] || ''
}

const getStatusText = (status) => {
  const texts = {
    'not-started': '未开始',
    'in-progress': '进行中',
    'completed': '已完成',
    'expired': '已过期'
  }
  return texts[status] || status
}

const getProgressColor = (percentage) => {
  if (percentage >= 80) return '#67c23a'
  if (percentage >= 50) return '#e6a23c'
  return '#f56c6c'
}

const getDifficultyText = (difficulty) => {
  const texts = {
    'EASY': '简单',
    'MEDIUM': '中等',
    'HARD': '困难'
  }
  return texts[difficulty] || difficulty
}

const getActionTypeText = (actionType) => {
  const texts = {
    'VIEW': '查看',
    'REVIEW': '复习',
    'TEST': '测试'
  }
  return texts[actionType] || actionType
}

const formatTime = (time) => {
  if (!time) return ''
  return new Date(time).toLocaleString()
}

// 生命周期
onMounted(() => {
  loadAssignments()
})
</script>

<style scoped>
.assignments {
  padding: 20px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.stats-row {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: 20px;
  margin-bottom: 20px;
}

.stat-card {
  padding: 20px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  background-color: #f8f9fa;
}

.stat-icon {
  width: 50px;
  height: 50px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-right: 15px;
  font-size: 20px;
  color: white;
}

.stat-icon.pending {
  background: linear-gradient(135deg, #409EFF 0%, #67C23A 100%);
}

.stat-icon.progress {
  background: linear-gradient(135deg, #E6A23C 0%, #F56C6C 100%);
}

.stat-icon.completed {
  background: linear-gradient(135deg, #67C23A 0%, #409EFF 100%);
}

.stat-icon.expired {
  background: linear-gradient(135deg, #F56C6C 0%, #E6A23C 100%);
}

.stat-info h3 {
  margin: 0;
  font-size: 24px;
  font-weight: bold;
  color: #303133;
}

.stat-info p {
  margin: 5px 0 0;
  color: #606266;
  font-size: 14px;
}

.expired {
  color: #f56c6c;
  text-decoration: line-through;
}

.urgent {
  color: #e6a23c;
  font-weight: bold;
}

.completed {
  color: #67c23a;
}

.assignment-detail {
  max-height: 60vh;
  overflow-y: auto;
}

.detail-header {
  margin-bottom: 20px;
  padding-bottom: 15px;
  border-bottom: 1px solid #e4e7ed;
}

.assignment-info p {
  margin: 5px 0;
  color: #606266;
}

.progress-summary h4 {
  margin: 15px 0 10px;
  color: #303133;
}

.progress-stats {
  display: flex;
  justify-content: space-between;
  margin-top: 8px;
  font-size: 14px;
  color: #606266;
}

.content-list {
  max-height: 300px;
  overflow-y: auto;
}

.content-item {
  padding: 15px;
  border: 1px solid #e4e7ed;
  border-radius: 4px;
  margin-bottom: 10px;
  cursor: pointer;
  transition: all 0.3s;
}

.content-item:hover {
  border-color: #409EFF;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
}

.content-item.completed {
  border-left: 4px solid #67c23a;
  background-color: #f0f9ff;
}

.item-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

.item-header .title {
  font-weight: bold;
  color: #303133;
}

.item-meta {
  font-size: 12px;
  color: #909399;
}

.item-meta span {
  margin-right: 15px;
}

.words-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
  gap: 15px;
}

.word-item {
  padding: 15px;
  border: 1px solid #e4e7ed;
  border-radius: 4px;
  cursor: pointer;
  transition: all 0.3s;
}

.word-item:hover {
  border-color: #409EFF;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
}

.word-item.completed {
  border-left: 4px solid #67c23a;
  background-color: #f0f9ff;
}

.word-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

.word-header h4 {
  margin: 0;
  color: #303133;
}

.pronunciation {
  color: #606266;
  font-style: italic;
  font-size: 14px;
  margin-bottom: 5px;
}

.definition {
  color: #303133;
  margin-bottom: 8px;
  line-height: 1.4;
}

.review-count {
  font-size: 12px;
  color: #909399;
}

.records-list {
  max-height: 300px;
  overflow-y: auto;
}

.record-item {
  padding: 10px;
  border-bottom: 1px solid #f0f0f0;
}

.record-time {
  font-size: 12px;
  color: #909399;
  margin-bottom: 5px;
}

.record-content {
  display: flex;
  align-items: center;
  gap: 10px;
}

.action-type {
  padding: 2px 8px;
  background-color: #409EFF;
  color: white;
  border-radius: 12px;
  font-size: 12px;
}

.content {
  flex: 1;
  color: #303133;
}

.duration {
  color: #606266;
  font-size: 12px;
}
</style>