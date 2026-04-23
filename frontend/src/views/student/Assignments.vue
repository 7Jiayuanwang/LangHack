<template>
  <div class="assignments">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>我的任务</span>
          <div style="display: flex; gap: 12px;">
            <el-select v-model="filterStatus" placeholder="筛选状态" clearable @change="loadAssignments">
              <el-option label="全部" value="" />
              <el-option label="未开始" value="not-started" />
              <el-option label="进行中" value="in-progress" />
              <el-option label="已完成" value="completed" />
              <el-option label="已过期" value="expired" />
            </el-select>
            <el-button type="primary" @click="showStats = !showStats">
              <el-icon><Histogram /></el-icon>
              进度
            </el-button>
          </div>
        </div>
      </template>

       <!-- 任务统计 -->
      <div class="stats-row" v-if="showStats">
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

      <!-- 移动端卡片列表 -->
      <div class="mobile-assignment-list" v-if="isMobile">
        <div 
          v-for="assignment in assignments" 
          :key="assignment.id" 
          class="mobile-assignment-card"
          :class="{ 'is-completed': assignment.status === 'completed', 'is-expired': assignment.status === 'expired' }"
          @click="handleTitleClick(assignment)"
        >
          <div class="card-top">
            <h3 class="card-title">{{ assignment.title }}</h3>
            <el-tag :type="getStatusType(assignment.status)" size="small">
              {{ getStatusText(assignment.status) }}
            </el-tag>
          </div>
          
          <div class="card-info">
            <div class="info-item">
              <el-icon><OfficeBuilding /></el-icon>
              <span>{{ assignment.className }}</span>
            </div>
            <div class="info-item">
              <el-icon><Notebook /></el-icon>
              <span>{{ assignment.unitName }}</span>
            </div>
            <div class="info-item" :class="getDeadlineClass(assignment.deadline, assignment.status)">
              <el-icon><Clock /></el-icon>
              <span>{{ formatTime(assignment.deadline) }}</span>
            </div>
          </div>
          
          <div class="card-progress">
            <span class="progress-label">学习进度</span>
            <el-progress
              :percentage="assignment.progress"
              :color="getProgressColor(assignment.progress)"
              :stroke-width="8"
            />
          </div>
          
          <div class="card-actions">
            <el-button
              v-if="assignment.status === 'not-started'"
              size="small"
              type="primary"
              @click.stop="startAssignment(assignment)"
            >
              开始任务
            </el-button>
            <el-button
              v-if="assignment.status === 'in-progress'"
              size="small"
              type="warning"
              @click.stop="continueAssignment(assignment)"
            >
              继续学习
            </el-button>
            <el-button
              v-if="assignment.status === 'completed'"
              size="small"
              type="success"
              @click.stop="reviewAssignment(assignment)"
            >
              复习
            </el-button>
            <el-button
              size="small"
              @click.stop="viewAssignmentDetail(assignment)"
            >
              详情
            </el-button>
          </div>
        </div>
      </div>

      <!-- 桌面端表格列表 -->
      <div class="table-scroll-wrapper" v-else>
        <el-table :data="assignments" stripe>
        <el-table-column label="任务标题">
          <template #default="scope">
            <span 
            class="clickable-title" 
            @click="handleTitleClick(scope.row)"
            :style="{ cursor: 'pointer', color: '#409EFF' }"
          >
            {{ scope.row.title }}
          </span>
        </template>
      </el-table-column>
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
            <el-tooltip :content="getStatusHelp(scope.row.status, scope.row.deadline, scope.row.progress)" placement="top">
              <el-tag :type="getStatusType(scope.row.status)">
                {{ getStatusText(scope.row.status) }}
              </el-tag>
            </el-tooltip>
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
      </div>
      <el-empty v-if="assignments.length === 0" description="暂无任务">
        <el-button type="primary" @click="loadAssignments">刷新任务</el-button>
      </el-empty>
    </el-card>

    <!-- 任务详情对话框 -->
    <el-dialog
      v-model="detailDialogVisible"
      :title="currentAssignment.title || '任务详情'"
      width="min(800px, 96vw)"
      :close-on-click-modal="false"
    >
      <div class="assignment-detail">
        <div class="detail-header">
          <div class="assignment-info">
            <p><strong>班级：</strong>{{ currentAssignment.className }}</p>
            <p><strong>单元：</strong>{{ currentAssignment.unitName }}</p>
            <p><strong>任务类型：</strong>{{ getAssignmentTypeText(currentAssignment.assignmentType) }}</p>
            <p><strong>完成标准：</strong>{{ getCompletionCriteriaText(currentAssignment.assignmentType) }}</p>
            <p><strong>截止时间：</strong>{{ formatTime(currentAssignment.deadline) }}</p>
            <p v-if="getAssignmentDescription(currentAssignment)"><strong>任务描述：</strong>{{ getAssignmentDescription(currentAssignment) }}</p>
          </div>
          <div class="progress-summary">
            <h4>学习进度</h4>
            <el-progress
              :percentage="currentAssignment.progress"
              :color="getProgressColor(currentAssignment.progress)"
              :stroke-width="8"
            />
<!--            <div class="progress-stats">-->
<!--              <div v-if="currentAssignment.totalWords > 0" class="stat-item">-->
<!--                <span>单词：{{ currentAssignment.learnedWords }}/{{ currentAssignment.totalWords }} ({{ Math.round((currentAssignment.learnedWords / currentAssignment.totalWords) * 100) }}%)</span>-->
<!--              </div>-->
<!--              <div v-if="currentAssignment.totalTexts > 0" class="stat-item">-->
<!--                <span>课文：{{ currentAssignment.readTexts }}/{{ currentAssignment.totalTexts }} ({{ Math.round((currentAssignment.readTexts / currentAssignment.totalTexts) * 100) }}%)</span>-->
<!--              </div>-->
<!--              <div class="stat-item">-->
<!--                <strong>完成状态：{{ currentAssignment.completed ? '✅ 已完成' : '⏳ 进行中' }}</strong>-->
<!--              </div>-->
<!--            </div>-->
          </div>
        </div>

        <div class="learning-content">
          <el-tabs type="border-card" v-model="activeTab">
             <!-- 根据任务类型动态显示课文标签 -->
                  <el-tab-pane
                  v-if="currentAssignment.assignmentType === 'UNIT' || currentAssignment.assignmentType === 'TEXTS'"
                  label="课文学习"
                  name="texts"
                >
              <div v-if="(currentAssignment.texts?.length ?? 0) > 0" class="content-list">
                <div
                  v-for="text in currentAssignment.texts"
                  :key="text.id"
                  class="content-item"
                  :class="{ completed: text.completed }"
                  @click="studyText(text)"
                >
                  <div class="item-header">
                    <span class="title">{{ text.title }}</span>
                    <el-tag v-if="currentAssignment.assignmentType === 'TEXTS'" :type="isTextTimeCompleted(text.studyTime) ? 'success' : 'warning'" size="small">
                      {{ isTextTimeCompleted(text.studyTime) ? '已达标' : '学习中' }}
                    </el-tag>
                    <el-tag v-else-if="text.completed" type="success" size="small">已完成</el-tag>
                    <el-tag v-else type="info" size="small">未学习</el-tag>
                  </div>
                  <div class="item-meta">
                    <span>难度：{{ getDifficultyText(text.difficulty) }}</span>
                    <span>学习时间：{{ formatTextTime(text, currentAssignment.assignmentType) }}</span>
                  </div>
                </div>
              </div>
              <el-empty v-else description="此任务不包含课文内容" />
            </el-tab-pane>

            <!-- 根据任务类型动态显示单词标签 -->
            <el-tab-pane
              v-if="currentAssignment.assignmentType === 'UNIT' || currentAssignment.assignmentType === 'WORDS'"
              label="单词学习"
              name="words"
            >
              <div v-if="(currentAssignment.words?.length ?? 0) > 0" class="words-grid">
                <div
                  v-for="word in currentAssignment.words"
                  :key="word.id"
                  class="word-item"
                  :class="{ completed: word.completed }"
                  @click="studyWord(word)"
                >
                  <div class="word-header">
                    <h4>{{ word.word }}</h4>
                    <div>
                      <el-tag v-if="word.completed" type="success" size="small">已掌握</el-tag>
                      <el-tag v-else-if="word.learnedByTask" type="warning" size="small">已学习</el-tag>
                    </div>
                  </div>
                  <div class="pronunciation">{{ word.pronunciation }}</div>
                  <div class="definition">{{ word.definition }}</div>
                  <div class="review-count">复习次数：{{ word.reviewCount || 0 }}</div>
                </div>
              </div>
              <el-empty v-else description="此任务不包含单词内容" />
            </el-tab-pane>

            <el-tab-pane label="学习记录" name="records">
              <div class="records-list">
                <div
                  v-for="record in (currentAssignment.records || [])"
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

    <!-- 单词卡片弹窗 -->
    <el-dialog
      v-model="wordCardDialogVisible"
      title="单词学习"
      width="min(600px, 94vw)"
      :close-on-click-modal="false"
    >
      <div v-if="currentWord.id" class="word-card">
        <div class="word-header">
          <h2 class="word-title">{{ currentWord.word }}</h2>
          <div class="pronunciation" v-if="currentWord.pronunciation">
            <el-icon><Microphone /></el-icon>
            {{ currentWord.pronunciation }}
          </div>
        </div>

        <div class="word-content">
          <div class="definition-section">
            <h3>释义</h3>
            <div class="definition">{{ currentWord.definition || '暂无释义' }}</div>
          </div>

          <div class="translation-section" v-if="currentWord.translation">
            <h3>翻译</h3>
            <div class="translation">{{ currentWord.translation }}</div>
          </div>

          <div class="example-section" v-if="currentWord.exampleSentence">
            <h3>例句</h3>
            <div class="example">{{ currentWord.exampleSentence }}</div>
            <div class="example-translation" v-if="currentWord.exampleTranslation">
              {{ currentWord.exampleTranslation }}
            </div>
          </div>

          <div class="word-meta">
            <el-tag v-if="currentWord.difficulty" :type="getDifficultyTagType(currentWord.difficulty)">
              {{ getDifficultyText(currentWord.difficulty) }}
            </el-tag>
            <el-tag v-if="currentWord.partOfSpeech" type="info">{{ currentWord.partOfSpeech }}</el-tag>
            <span class="study-time">建议学习时间：30秒</span>
          </div>
        </div>
      </div>

      <template #footer>
        <div class="word-card-footer">
          <el-button @click="wordCardDialogVisible = false">取消</el-button>
          <el-button
            type="primary"
            @click="markWordAsCompleted(currentWord)"
            :disabled="currentWord.completed"
          >
            {{ currentWord.completed ? '已完成' : (currentWord.learnedByTask ? '重新学习' : '标记完成') }}
          </el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, watch, onActivated, onUnmounted, computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import {Check, Clock, Histogram, Loading, Microphone, Warning, OfficeBuilding, Notebook} from '@element-plus/icons-vue'
import request from '@/utils/request'

// 响应式窗口宽度
const windowWidth = ref(typeof window !== 'undefined' ? window.innerWidth : 1024)
const isMobile = computed(() => windowWidth.value < 768)

// 窗口大小变化监听
const handleResize = () => {
  windowWidth.value = window.innerWidth
}

let resizeListener = null
let assignmentRefreshListener = null

const router = useRouter()
const route = useRoute()

//新增加功能通过点击标题开始学习任务
const handleTitleClick = (assignment) => {
  switch (assignment.status) {
    case 'not-started':
      startAssignment(assignment);
      break;
    case 'in-progress':
      continueAssignment(assignment);
      break;
    case 'completed':
      reviewAssignment(assignment);
      break;
    case 'expired':
      ElMessage.warning('任务已过期，无法继续学习');
      break;
    default:
      ElMessage.info('请使用操作列的按钮开始学习');
  }
}

// 响应式数据
const assignments = ref([])
const filterStatus = ref('')
const detailDialogVisible = ref(false)
const currentAssignment = ref({})
const wordCardDialogVisible = ref(false)
const currentWord = ref({})
const activeTab = ref('')
const showStats = ref(false)

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
    const response = await request({
      url: '/api/student/assignments',
      method: 'get',
      params: {
        status: filterStatus.value || undefined
      }
    })

    if (response && response.code === 200) {
      const assignmentData = Array.isArray(response.data?.assignments) ? response.data.assignments : []

      // 后端已经处理了状态筛选，直接使用数据
      const processedAssignments = assignmentData.map(assignment => ({
        id: assignment.id,
        title: assignment.title,
        className: assignment.className || `班级${assignment.classId}`,
        unitName: assignment.unitName || `单元${assignment.unitId}`,
        description: assignment.description,
        deadline: assignment.deadline,
        status: assignment.status, // 后端已计算
        progress: assignment.progress || 0,
        completedItems: assignment.learnedWords || 0, // 使用已学习的单词数
        totalItems: assignment.totalWords || 1, // 使用总单词数，避免为0
        texts: [], // 需要通过详情API获取
        words: [], // 需要通过详情API获取
        records: [], // 需要通过记录API获取
        // 额外统计信息
        totalWords: assignment.totalWords || 0,
        learnedWords: assignment.learnedWords || 0,
        totalTexts: assignment.totalTexts || 0,
        readTexts: assignment.readTexts || 0,
        studyTime: assignment.studyTime || 0
      }))

      // 如果前端还有状态筛选，再筛选一次（确保准确性）
      let filteredAssignments = processedAssignments
      if (filterStatus.value) {
        filteredAssignments = processedAssignments.filter(assignment => assignment.status === filterStatus.value)
      }

      assignments.value = filteredAssignments

      // 更新统计数据（基于所有任务，不只是筛选后的）
      updateStats(processedAssignments)
    } else {
      console.error('API返回错误:', response)
      ElMessage.error(response?.message || '加载任务列表失败')
    }
  } catch (error) {
    console.error('加载任务列表失败:', error)
    ElMessage.error('加载任务列表失败')
  }
}

// 计算任务状态
const calculateAssignmentStatus = (progress, deadline) => {
  const now = new Date()
  const deadlineDate = deadline ? new Date(deadline) : null

  if (deadlineDate && now > deadlineDate) {
    return 'expired'
  }

  if (progress >= 100) {
    return 'completed'
  } else if (progress > 0) {
    return 'in-progress'
  } else {
    return 'not-started'
  }
}

// 格式化学习时间显示
const formatTextTime = (text, assignmentType) => {
  void calculateAssignmentStatus
  if (assignmentType === 'TEXTS') {
    // 课文任务：显示阅读时间，判断是否达到要求
    const time = text.studyTime || 0
    const MIN_TIME = 600 // 10分钟 = 600秒
    const isCompleted = time >= MIN_TIME

    // 简单的时间格式化：转换为分钟
    const minutes = Math.floor(time / 60)
    const seconds = time % 60
    const timeStr = minutes > 0 ? `${minutes}分${seconds}秒` : `${seconds}秒`

    return `${timeStr} ${isCompleted ? '✅' : '⏳ (需要10分钟)'}`
  } else {
    // 单元任务：显示学习状态
    return text.completed ? '✅ 已完成' : '⏳ 未完成'
  }
}

// 判断课文学习时间是否达标
const isTextTimeCompleted = (studyTime) => {
  const MIN_TIME = 600 // 10分钟 = 600秒
  return (studyTime || 0) >= MIN_TIME
}

// 更新统计数据
const updateStats = (assignmentList) => {
  stats.pendingCount = assignmentList.filter(a => a.status === 'not-started').length
  stats.inProgressCount = assignmentList.filter(a => a.status === 'in-progress').length
  stats.completedCount = assignmentList.filter(a => a.status === 'completed').length
  stats.expiredCount = assignmentList.filter(a => a.status === 'expired').length
}
  
const startAssignment = async (assignment) => {
  try {
    // 先调用后端API标记任务开始
    try {
      const startResponse = await request({
        url: `/api/student/assignments/${assignment.id}/start`,
        method: 'post'
      })

      if (startResponse && startResponse.code !== 200) {
        // silent fail, continue opening detail
      }
    } catch (startError) {
      // 不阻止打开详情，即使开始API失败
    }
    
    // 打开任务详情对话框
    await viewAssignmentDetail(assignment)
  } catch (error) {
    console.error('开始任务失败:', error)
    ElMessage.error('开始任务失败')
  }
}

const continueAssignment = (assignment) => {
  ElMessage.info(`继续学习：${assignment.title}`)
  viewAssignmentDetail(assignment)
}

const reviewAssignment = (assignment) => {
  ElMessage.info(`复习任务：${assignment.title}`)
  viewAssignmentDetail(assignment)
}

const viewAssignmentDetail = async (assignment) => {
  try {
    if (!assignment || !assignment.id) {
      ElMessage.error('任务信息不完整，无法打开详情')
      return
    }

    const response = await request({
      url: `/api/student/assignments/${assignment.id}`,
      method: 'get'
    })

    if (response && response.code === 200) {
      const detailData = response.data && typeof response.data === 'object' ? response.data : {}

      // 合并基础信息和详情信息
      currentAssignment.value = {
        ...assignment,
        ...detailData,
        texts: [], // 需要通过内容API获取
        words: [], // 需要通过内容API获取
        records: [] // 需要通过记录API获取
      }

      // 加载任务内容
      await loadAssignmentContent(assignment.id)

      // 设置默认激活的标签页
      if (currentAssignment.value.assignmentType === 'UNIT' || currentAssignment.value.assignmentType === 'TEXTS') {
        activeTab.value = 'texts' // 默认显示课文学习标签页
      } else if (currentAssignment.value.assignmentType === 'WORDS') {
        activeTab.value = 'words' // 单词任务默认显示单词学习标签页
      } else {
        activeTab.value = 'records' // 默认显示学习记录标签页
      }

      // 确保对话框显示
      detailDialogVisible.value = true
    } else {
      console.error('获取任务详情失败:', response)
      const errorMsg = response?.message || '获取任务详情失败'
      ElMessage.error(errorMsg)
      
      // 即使API失败，也尝试显示基本信息
      if (assignment) {
        currentAssignment.value = {
          ...assignment,
          texts: [],
          words: [],
          records: [],
          progress: 0,
          completed: false
        }
        detailDialogVisible.value = true
        ElMessage.warning('任务详情加载不完整，部分功能可能不可用')
      }
    }
  } catch (error) {
    console.error('获取任务详情异常:', error)
    ElMessage.error('获取任务详情失败')
    
    // 即使出错，也尝试显示基本信息
    if (assignment) {
      currentAssignment.value = {
        ...assignment,
        texts: [],
        words: [],
        records: [],
        progress: 0,
        completed: false
      }
      detailDialogVisible.value = true
      ElMessage.warning('任务详情加载失败，显示基本信息')
    }
  }
}

// 加载任务内容
const loadAssignmentContent = async (assignmentId) => {
  try {
    const response = await request({
      url: `/api/student/assignments/${assignmentId}/content`,
      method: 'get'
    })

    if (response && response.code === 200) {
      const contentData = response.data || {}

      // 更新任务内容 - 安全的数组处理
      currentAssignment.value.texts = Array.isArray(contentData.texts) ? contentData.texts : []
      currentAssignment.value.words = Array.isArray(contentData.words) ? contentData.words : []
      currentAssignment.value.assignmentType = contentData.assignmentType || currentAssignment.value.assignmentType

      // 同时加载学习记录
      await loadAssignmentRecords(assignmentId)
    }
  } catch (error) {
    console.error('加载任务内容失败:', error)
    // 不阻止详情显示，只是内容为空
  }
}

// 加载学习记录
const loadAssignmentRecords = async (assignmentId) => {
  try {
    const response = await request({
      url: `/api/student/assignments/${assignmentId}/records`,
      method: 'get',
      params: {
        page: 1,
        size: 50
      }
    })

    if (response && response.code === 200) {
      const recordsData = response.data || {}
      currentAssignment.value.records = Array.isArray(recordsData.records) ? recordsData.records : []
    }
  } catch (error) {
    console.error('加载学习记录失败:', error)
    // 不阻止详情显示，只是记录为空
  }
}

const studyText = (text) => {
  try {
    // 验证必要数据存在
    if (!text || !text.id || !currentAssignment.value?.id) {
      ElMessage.error('无法获取必要信息，请重新打开任务详情')
      return
    }

    ElMessage.info(`正在跳转到课文学习：${text.title}`)

    // 跳转到课文学习页面
    // 注意：不在这里记录学习时长，学习时长由学习页面本身管理
    router.push({
      path: `/article/${text.id}`,
      query: {
        assignmentId: currentAssignment.value.id,
        from: 'assignment' // 标记来源，便于返回
      }
    })

  } catch (error) {
    console.error('跳转课文学习失败:', error)
    ElMessage.error('跳转到课文学习失败')
  }
}

const studyWord = async (word) => {
  try {
    // 验证数据完整性
    if (!word || !word.id || !currentAssignment.value?.id) {
      ElMessage.error('无法获取必要信息，请重新打开任务详情')
      return
    }

    // 设置当前单词数据
    currentWord.value = { ...word }

    // 显示单词卡片弹窗
    wordCardDialogVisible.value = true

    // 记录单词浏览行为
    const currentAssignmentData = currentAssignment.value
    await request({
      url: `/api/student/learning/record/word-view`,
      method: 'post',
      data: {
        wordId: word.id,
        assignmentId: currentAssignmentData.id,
        duration: 10 // 浏览记录10秒
      }
    })

  } catch (error) {
    console.error('显示单词卡片失败:', error)
    ElMessage.error('显示单词卡片失败')
  }
}

const markWordAsCompleted = async (word) => {
  try {
    // 验证数据完整性
    if (!word || !word.id || !currentAssignment.value?.id) {
      ElMessage.error('无法获取必要信息，请重新打开任务详情')
      return
    }

    const currentAssignmentData = currentAssignment.value

    // 记录单词学习完成
    await request({
      url: `/api/student/learning/words/${word.id}/complete`,
      method: 'post',
      params: {
        assignmentId: currentAssignmentData.id,
        duration: 30 // 完成学习30秒
      }
    })

    // 添加到生词本
    await request({
      url: `/api/student/learning/vocabulary/add`,
      method: 'post',
      data: {
        wordId: word.id,
        masteryLevel: 'LEARNING'
      }
    })

    // 更新本地状态
    word.completed = true
    word.reviewCount = (word.reviewCount || 0) + 1

    // 获取任务进度
    const progressResponse = await request({
      url: `/api/student/assignments/${currentAssignmentData.id}/progress`,
      method: 'get'
    })

    if (progressResponse.data) {
      // completionRate 从后端返回的是 0-1 比率，需要转换为 0-100 百分比
      const rawRate = progressResponse.data.completionRate || 0
      const completionRate = Math.round(rawRate < 1 ? rawRate * 100 : rawRate)

      // 更新当前任务的进度
      currentAssignment.value.progress = completionRate
      currentAssignment.value.completed = completionRate >= 100
      
      // 重新加载任务详情以获取最新状态
      await viewAssignmentDetail(currentAssignment.value)
      
      // 刷新任务列表以更新状态
      await loadAssignments()
      
      ElMessage.success(`单词学习完成！任务进度：${completionRate}%`)

      // 如果任务完成，询问是否返回
      if (completionRate >= 100) {
        safeTimeout(() => {
          ElMessage({
            message: '恭喜！任务已全部完成',
            type: 'success',
            duration: 3000
          })
        }, 1000)
      }
    } else {
      ElMessage.success('单词学习完成！')
      // 即使没有进度数据，也刷新任务列表
      await loadAssignments()
    }

    // 关闭单词卡片弹窗
    wordCardDialogVisible.value = false

  } catch (error) {
    console.error('标记单词完成失败:', error)
    ElMessage.error('标记单词完成失败')
  }
}

// 工具方法
const getDeadlineClass = (deadline, status) => {
  if (status === 'completed') return 'completed'
  if (!deadline) return ''
  const now = new Date()
  const deadlineDate = new Date(deadline)
  if (isNaN(deadlineDate.getTime())) return ''
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
    'in-progress': '学习中',
    'completed': '已完成',
    'expired': '已过期'
  }
  return texts[status] || status
}

const getStatusHelp = (status, deadline, progress = 0) => {
  const now = new Date()
  const deadlineDate = deadline ? new Date(deadline) : null
  const isExpired = deadlineDate && now > deadlineDate

  const helpTexts = {
    'not-started': isExpired ? '任务已过期，尚未开始学习' : '尚未开始学习此任务',
    'in-progress': isExpired ? `任务已过期，学习进度${Math.round(progress)}%` : `正在学习中，进度${Math.round(progress)}%`,
    'completed': '恭喜！任务已完成',
    'expired': '任务已过期，无法提交'
  }
  return helpTexts[status] || ''
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

const getDifficultyTagType = (difficulty) => {
  const types = {
    'EASY': 'success',
    'MEDIUM': 'warning',
    'HARD': 'danger'
  }
  return types[difficulty] || 'info'
}

const getActionTypeText = (actionType) => {
  const texts = {
    'VIEW': '查看',
    'REVIEW': '复习',
    'TEST': '测试'
  }
  return texts[actionType] || actionType
}

const getAssignmentTypeText = (assignmentType) => {
  const types = {
    'UNIT': '整个单元',
    'TEXTS': '指定课文',
    'WORDS': '指定单词'
  }
  return types[assignmentType] || '未知类型'
}

const getCompletionCriteriaText = (assignmentType) => {
  const criteria = {
    'UNIT': '课文和单词均需达到80%进度',
    'TEXTS': '每篇课文需学习至少10分钟',
    'WORDS': '需要掌握所有指定单词'
  }
  return criteria[assignmentType] || '未知完成标准'
}

const formatTime = (time) => {
  if (!time) return ''
  return new Date(time).toLocaleString()
}

// 获取任务描述（如果是JSON格式则返回空或解析后的描述）
const getAssignmentDescription = (assignment) => {
  if (!assignment || !assignment.description) return ''
  
  const desc = assignment.description
  
  // 检查是否是JSON格式
  try {
    const parsed = JSON.parse(desc)
    // 如果是JSON对象，返回空字符串或尝试提取描述
    if (typeof parsed === 'object') {
      // 如果是AssignmentContent格式，返回空（因为任务类型已经显示了）
      return ''
    }
    return desc
  } catch (e) {
    // 不是JSON，直接返回
    return desc
  }
}

// 监听路由变化，从其他页面返回时刷新列表
watch(() => route.path, (newPath) => {
  if (newPath === '/student/assignments') {
    loadAssignments()

    // 检查是否有任务ID参数，如果有则打开详情
    const assignmentId = route.query.id
    if (assignmentId) {
      safeTimeout(async () => {
        const assignment = assignments.value.find(a => a.id == assignmentId)
        if (assignment) {
          await viewAssignmentDetail(assignment)
        } else {
          // assignment not found in list, silently skip
        }
      }, 500)
    }
  }
})

// 监听路由查询参数变化
watch(() => route.query, (newQuery) => {
  if (route.path === '/student/assignments' && newQuery.refresh) {
    // 强制刷新任务列表
    loadAssignments()
  }
}, { deep: true })

// 监听自定义刷新事件（保存引用以便正确移除）
const handleAssignmentRefresh = () => {
  if (route.path === '/student/assignments') {
    loadAssignments()
  }
}

// 组件激活时刷新（用于keep-alive场景）
onActivated(() => {
  loadAssignments()
  
  // 检查是否有任务ID参数
  const assignmentId = route.query.id
  if (assignmentId) {
    safeTimeout(async () => {
      const assignment = assignments.value.find(a => a.id == assignmentId)
      if (assignment) {
        await viewAssignmentDetail(assignment)
      }
    }, 500)
  }
})

// 生命周期
const pendingTimers = []
const safeTimeout = (fn, delay) => {
  const id = setTimeout(fn, delay)
  pendingTimers.push(id)
  return id
}

onMounted(() => {
  // 初始化事件监听器
  if (typeof window !== 'undefined') {
    resizeListener = handleResize
    assignmentRefreshListener = handleAssignmentRefresh
    window.addEventListener('resize', resizeListener)
    window.addEventListener('assignment-refresh', assignmentRefreshListener)
  }

  loadAssignments()

  // 检查是否有任务ID参数（从仪表板跳转过来）
  const assignmentId = route.query.id
  if (assignmentId) {
    safeTimeout(async () => {
      // 等待任务列表加载完成
      if (assignments.value.length > 0) {
        const assignment = assignments.value.find(a => a.id == assignmentId)
        if (assignment) {
          await viewAssignmentDetail(assignment)
        } else {
          ElMessage.warning('未找到指定的任务')
        }
      } else {
        // 如果列表为空，尝试直接加载任务详情
        try {
          const response = await request({
            url: `/api/student/assignments/${assignmentId}`,
            method: 'get'
          })
          if (response && response.code === 200) {
            currentAssignment.value = response.data || {}
            await loadAssignmentContent(assignmentId)
            detailDialogVisible.value = true
          }
        } catch (error) {
          console.error('直接加载任务详情失败:', error)
        }
      }
    }, 1000)
  }
})

// 清理事件监听
onUnmounted(() => {
  // 清理所有pending timers
  pendingTimers.forEach(id => clearTimeout(id))
  pendingTimers.length = 0

  if (typeof window !== 'undefined') {
    if (resizeListener) {
      window.removeEventListener('resize', resizeListener)
    }
    if (assignmentRefreshListener) {
      window.removeEventListener('assignment-refresh', assignmentRefreshListener)
    }
  }
})
</script>

<style scoped>
.assignments {
  padding: 20px;
  background-color: var(--bg-secondary);
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', 'PingFang SC', sans-serif;
  min-height: calc(100vh - 120px);
}

.assignments :deep(.el-card) {
  border-radius: 20px;
  border: 1px solid var(--border-light);
  background: rgba(255, 255, 255, 0.7);
  backdrop-filter: blur(16px);
  -webkit-backdrop-filter: blur(16px);
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.05);
}

.table-scroll-wrapper {
  overflow-x: auto;
  -webkit-overflow-scrolling: touch;
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
  font-size: 22px;
  color: white;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.15);
  transition: all 0.3s ease;
}

.stat-icon:hover {
  transform: scale(1.05);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.2);
}

.stat-icon.pending {
  background: linear-gradient(135deg, #409EFF 0%, #67C23A 100%);
  filter: brightness(1.05) saturate(1.1);
}

.stat-icon.progress {
  background: linear-gradient(135deg, #E6A23C 0%, #F56C6C 100%);
  filter: brightness(1.05) saturate(1.1);
}

.stat-icon.completed {
  background: linear-gradient(135deg, #67C23A 0%, #409EFF 100%);
  filter: brightness(1.05) saturate(1.1);
}

.stat-icon.expired {
  background: linear-gradient(135deg, #F56C6C 0%, #E6A23C 100%);
  filter: brightness(1.05) saturate(1.1);
}

.stat-icon .el-icon {
  filter: drop-shadow(0 1px 2px rgba(0, 0, 0, 0.2));
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
  flex-direction: column;
  gap: 5px;
  margin-top: 8px;
  font-size: 14px;
  color: #606266;
}

.progress-stats .stat-item {
  display: flex;
  justify-content: space-between;
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

/* 单词卡片样式 */
.word-card {
  padding: 0;
}

.word-header {
  text-align: center;
  margin-bottom: 24px;
  padding-bottom: 16px;
  border-bottom: 1px solid #f0f0f0;
}

.word-title {
  font-size: 28px;
  font-weight: 600;
  color: #303133;
  margin: 0 0 8px 0;
}

.pronunciation {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  color: #606266;
  font-size: 16px;
}

.pronunciation .el-icon {
  color: #409EFF;
}

.word-content {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.definition-section,
.translation-section,
.example-section {
  margin-bottom: 20px;
}

.definition-section h3,
.translation-section h3,
.example-section h3 {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
  margin: 0 0 8px 0;
}

.definition,
.translation {
  font-size: 15px;
  line-height: 1.6;
  color: #606266;
  word-break: break-word;
  overflow-wrap: break-word;
}

.example {
  font-size: 15px;
  line-height: 1.6;
  color: #303133;
  font-style: italic;
  margin-bottom: 6px;
  padding: 12px;
  background-color: #f8f9ff;
  border-left: 3px solid #409EFF;
  border-radius: 4px;
  word-break: break-word;
  overflow-wrap: break-word;
}

.example-translation {
  font-size: 14px;
  color: #606266;
  padding-left: 15px;
}

.word-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 20px;
  padding-top: 16px;
  border-top: 1px solid #f0f0f0;
}

.study-time {
  color: #909399;
  font-size: 13px;
}

.word-card-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}

/* ===================== 移动端样式 ===================== */
.mobile-assignment-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.mobile-assignment-card {
  background: rgba(255, 255, 255, 0.7);
  backdrop-filter: blur(16px);
  -webkit-backdrop-filter: blur(16px);
  border-radius: 16px;
  padding: 16px;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.05);
  border: 1px solid var(--border-light);
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  cursor: pointer;
}

.mobile-assignment-card:hover {
  box-shadow: 0 12px 28px rgba(0, 0, 0, 0.1);
  transform: translateY(-4px);
}

.mobile-assignment-card.is-completed {
  border-left: 4px solid #67c23a;
  background: linear-gradient(to right, #f0f9eb, #fff);
}

.mobile-assignment-card.is-expired {
  border-left: 4px solid #f56c6c;
  background: linear-gradient(to right, #fef0f0, #fff);
}

.card-top {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 12px;
  gap: 12px;
}

.card-title {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  color: #303133;
  flex: 1;
  line-height: 1.4;
}

.card-info {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-bottom: 12px;
  padding: 12px;
  background: #f8f9fa;
  border-radius: 8px;
}

.card-info .info-item {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  color: #606266;
}

.card-info .info-item .el-icon {
  color: #909399;
  font-size: 16px;
}

.card-info .info-item.expired {
  color: #f56c6c;
}

.card-info .info-item.urgent {
  color: #e6a23c;
  font-weight: 600;
}

.card-progress {
  margin-bottom: 16px;
}

.card-progress .progress-label {
  display: block;
  font-size: 13px;
  color: #909399;
  margin-bottom: 8px;
}

.card-actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.card-actions .el-button {
  flex: 1;
  min-width: 80px;
}

/* 移动端响应式适配 */
@media (max-width: 768px) {
  .assignments {
    padding: 12px;
  }
  
  .card-header {
    flex-direction: column;
    gap: 12px;
    align-items: stretch;
  }
  
  .card-header > span {
    font-size: 18px;
    font-weight: 600;
  }
  
  .card-header > div {
    display: flex;
    gap: 8px;
    flex-wrap: wrap;
  }
  
  .card-header .el-select {
    flex: 1;
    min-width: 120px;
  }
  
  .card-header .el-button {
    flex-shrink: 0;
  }
  
  .stats-row {
    grid-template-columns: repeat(2, 1fr);
    gap: 12px;
  }
  
  .stat-card {
    padding: 14px;
  }
  
  .stat-icon {
    width: 40px;
    height: 40px;
    font-size: 18px;
    margin-right: 12px;
  }
  
  .stat-info h3 {
    font-size: 20px;
  }
  
  .stat-info p {
    font-size: 12px;
  }
  
  /* 空状态适配 */
  .el-empty {
    padding: 40px 20px;
  }
  
  /* 对话框移动端适配 */
  :deep(.el-dialog) {
    width: 95% !important;
    max-width: 95% !important;
    margin: 10px auto !important;
  }
  
  :deep(.el-dialog__body) {
    padding: 15px;
  }
  
  .assignment-detail {
    max-height: 70vh;
  }
  
  .detail-header {
    flex-direction: column;
  }
  
  .words-grid {
    grid-template-columns: 1fr;
  }
  
  .content-list {
    max-height: 250px;
  }
  
  /* 单词卡片弹窗适配 */
  .word-card .word-title {
    font-size: 24px;
  }
  
  .word-meta {
    flex-direction: column;
    gap: 10px;
    align-items: flex-start;
  }
}

/* 超小屏幕适配 */
@media (max-width: 480px) {
  .assignments {
    padding: 8px;
  }
  
  .mobile-assignment-card {
    padding: 14px;
  }
  
  .card-title {
    font-size: 15px;
  }
  
  .card-info {
    padding: 10px;
  }
  
  .card-info .info-item {
    font-size: 13px;
  }
  
  .card-actions .el-button {
    font-size: 13px;
    padding: 8px 12px;
  }
  
  .stats-row {
    grid-template-columns: 1fr 1fr;
    gap: 8px;
  }
  
  .stat-card {
    padding: 10px;
  }
  
  .stat-icon {
    width: 36px;
    height: 36px;
    font-size: 16px;
    margin-right: 10px;
  }
  
  .stat-info h3 {
    font-size: 18px;
  }
}
</style>
