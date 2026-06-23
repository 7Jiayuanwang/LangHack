<template>
  <div class="my-progress">
    <el-page-header @back="handleBack" title="返回">
      <template #content>
        <span class="page-title">我的进度</span>
      </template>
    </el-page-header>

    <div class="progress-content" v-loading="loading">
      <!-- 总体进度卡片 -->
      <el-card class="overview-card">
        <div class="overview-header">
          <h3>{{ campaign.title }}</h3>
          <el-tag :type="getStatusType(progress.completionStatus)">
            {{ getStatusLabel(progress.completionStatus) }}
          </el-tag>
        </div>

        <div class="stats-grid">
          <div class="stat-item">
            <div class="stat-icon" style="background: linear-gradient(135deg, #667eea 0%, #764ba2 100%)">
              <el-icon><Trophy /></el-icon>
            </div>
            <div class="stat-content">
              <div class="stat-value">{{ progress.totalScore || 0 }}</div>
              <div class="stat-label">总得分</div>
            </div>
          </div>

          <div class="stat-item">
            <div class="stat-icon" style="background: linear-gradient(135deg, #f093fb 0%, #f5576c 100%)">
              <el-icon><Checked /></el-icon>
            </div>
            <div class="stat-content">
              <div class="stat-value">{{ progress.completedTasks || 0 }}</div>
              <div class="stat-label">完成任务</div>
            </div>
          </div>

          <div class="stat-item">
            <div class="stat-icon" style="background: linear-gradient(135deg, #4facfe 0%, #00f2fe 100%)">
              <el-icon><TrophyBase /></el-icon>
            </div>
            <div class="stat-content">
              <div class="stat-value">{{ ranking.myRank || '-' }}</div>
              <div class="stat-label">我的排名</div>
            </div>
          </div>

          <div class="stat-item">
            <div class="stat-icon" style="background: linear-gradient(135deg, #fa709a 0%, #fee140 100%)">
              <el-icon><Timer /></el-icon>
            </div>
            <div class="stat-content">
              <div class="stat-value">{{ getRemainingDays(campaign.endTime) }}</div>
              <div class="stat-label">剩余时间</div>
            </div>
          </div>
        </div>

        <div class="overall-progress">
          <div class="progress-header">
            <span>总体完成度</span>
            <span class="progress-percent">{{ calculateOverallProgress() }}%</span>
          </div>
          <el-progress
            :percentage="calculateOverallProgress()"
            :color="getProgressColors()"
            :stroke-width="12"
          />
        </div>
      </el-card>

      <!-- 任务进度列表 -->
      <el-card class="tasks-card">
        <h3>任务完成情况</h3>
        <div class="task-list">
          <div
            v-for="task in taskProgress"
            :key="task.taskId"
            class="task-progress-item"
            :class="{ completed: task.isCompleted }"
          >
            <div class="task-header">
              <div class="task-info">
                <div class="task-name">
                  <el-icon>
                    <CircleCheck v-if="task.isCompleted" />
                    <Clock v-else />
                  </el-icon>
                  {{ task.taskName || getTaskTypeLabel(task.taskType) || '未命名任务' }}
                  <el-tag v-if="task.isRequired" type="danger" size="small">必做</el-tag>
                </div>
                <div class="task-type">
                  <el-tag size="small" effect="plain">{{ getTaskTypeLabel(task.taskType) }}</el-tag>
                </div>
              </div>
              <div class="task-score">
                <span class="current-score">{{ task.currentValue || 0 }}</span>
                <span class="separator">/</span>
                <span class="target-score">{{ task.targetValue }}</span>
                <span class="unit">{{ getTaskUnitLabel(task.taskUnit) }}</span>
              </div>
            </div>

            <div class="task-progress">
              <el-progress
                :percentage="calculateTaskProgress(task)"
                :color="task.isCompleted ? '#67c23a' : '#409eff'"
                :stroke-width="8"
              />
            </div>

            <div class="task-footer">
              <span class="points">
                <el-icon><Medal /></el-icon>
                {{ task.isCompleted ? `已获得 ${task.points} 分` : `完成可得 ${task.points} 分` }}
              </span>
              <span class="completion-count" v-if="task.completionCount > 0">
                已完成 {{ task.completionCount }} 次
              </span>
            </div>
          </div>

          <el-empty v-if="taskProgress.length === 0" description="暂无任务数据" />
        </div>
      </el-card>

      <!-- 排名信息 -->
      <el-card class="ranking-card" v-if="campaign.showRanking">
        <h3>我的排名</h3>
        <div class="ranking-content">
          <div class="my-rank-box">
            <div class="rank-badge" :class="getRankClass(ranking.myRank)">
              {{ ranking.myRank || '-' }}
            </div>
            <div class="rank-info">
              <div class="rank-label">当前排名</div>
              <div class="rank-total">共 {{ ranking.totalParticipants || 0 }} 人参与</div>
            </div>
          </div>

          <div class="rank-comparison" v-if="ranking.aroundMe && ranking.aroundMe.length > 0">
            <h4>周围排名</h4>
            <div class="rank-list">
              <div
                v-for="item in ranking.aroundMe"
                :key="item.userId"
                class="rank-item"
                :class="{ 'is-me': item.isMe }"
              >
                <div class="rank-number">{{ item.rank }}</div>
                <div class="rank-user">
                  <div class="user-name">{{ item.userName }}</div>
                  <div class="user-class">{{ item.className }}</div>
                </div>
                <div class="rank-score" v-if="campaign.showScores">
                  {{ item.totalScore }} 分
                </div>
                <el-tag v-if="item.isMe" type="primary" size="small">我</el-tag>
              </div>
            </div>
          </div>
        </div>
      </el-card>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  Trophy, Checked, TrophyBase, Timer, CircleCheck, Clock, Medal
} from '@element-plus/icons-vue'
import {
  getStudentCampaignDetail,
  getMyProgress,
  getMyTaskProgress,
  getMyRanking
} from '@/api/campaign'

const router = useRouter()
const route = useRoute()
const loading = ref(false)

const campaign = ref({})
const progress = ref({})
const taskProgress = ref([])
const ranking = ref({})

const loadData = async () => {
  loading.value = true
  try {
    const [campaignRes, progressRes, tasksRes, rankingRes] = await Promise.all([
      getStudentCampaignDetail(route.params.id),
      getMyProgress(route.params.id),
      getMyTaskProgress(route.params.id),
      getMyRanking(route.params.id)
    ])

    if (campaignRes.code === 200) {
      campaign.value = campaignRes.data.campaign || {}
    }
    if (progressRes.code === 200) {
      progress.value = progressRes.data || {}
    }
    if (tasksRes.code === 200) {
      taskProgress.value = tasksRes.data || []
    }
    if (rankingRes.code === 200) {
      ranking.value = rankingRes.data || {}
    }
  } catch (error) {
    ElMessage.error('加载失败: ' + error.message)
  } finally {
    loading.value = false
  }
}

const handleBack = () => {
  router.push('/student/campaigns')
}

const getStatusType = (status) => {
  const types = {
    NOT_STARTED: 'info',
    IN_PROGRESS: 'warning',
    COMPLETED: 'success'
  }
  return types[status] || 'info'
}

const getStatusLabel = (status) => {
  const labels = {
    NOT_STARTED: '未开始',
    IN_PROGRESS: '进行中',
    COMPLETED: '已完成'
  }
  return labels[status] || status
}

const getTaskTypeLabel = (type) => {
  const labels = {
    READING_TIME: '阅读时长',
    WORD_LEARNING: '单词学习',
    TEXT_UPLOAD: '文章上传',
    FEEDBACK: '问题反馈',
    AI_QA: 'AI问答',
    MANUAL: '手动录入'
  }
  return labels[type] || type
}

const getTaskUnitLabel = (unit) => {
  const labels = {
    MINUTES: '分钟',
    COUNT: '个',
    TIMES: '次'
  }
  return labels[unit] || unit
}

const calculateOverallProgress = () => {
  const total = taskProgress.value.length || 1
  const completed = taskProgress.value.filter(t => t.isCompleted).length
  return Math.round((completed / total) * 100)
}

const calculateTaskProgress = (task) => {
  const current = task.currentValue || 0
  const target = task.targetValue || 1
  return Math.min(Math.round((current / target) * 100), 100)
}

const getProgressColors = () => {
  return [
    { color: '#f56c6c', percentage: 20 },
    { color: '#e6a23c', percentage: 40 },
    { color: '#409eff', percentage: 60 },
    { color: '#67c23a', percentage: 80 },
    { color: '#5cb87a', percentage: 100 }
  ]
}

const getRemainingDays = (endTime) => {
  if (!endTime) return '-'
  const end = new Date(endTime)
  const now = new Date()
  const days = Math.ceil((end - now) / (1000 * 60 * 60 * 24))
  if (days < 0) return '已结束'
  if (days === 0) return '今天'
  return `${days} 天`
}

const getRankClass = (rank) => {
  if (rank === 1) return 'rank-gold'
  if (rank === 2) return 'rank-silver'
  if (rank === 3) return 'rank-bronze'
  return ''
}

onMounted(() => {
  loadData()
})
</script>

<style scoped>
.my-progress {
  padding: 20px;
  max-width: 900px;
  margin: 0 auto;
}

.el-page-header {
  margin-bottom: 20px;
}

.page-title {
  font-size: 18px;
  font-weight: 500;
}

.progress-content {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.overview-card h3,
.tasks-card h3,
.ranking-card h3 {
  margin: 0 0 20px 0;
  font-size: 18px;
  color: #303133;
}

.overview-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.stats-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(150px, 1fr));
  gap: 15px;
  margin-bottom: 25px;
}

.stat-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 15px;
  background: #f5f7fa;
  border-radius: 8px;
}

.stat-icon {
  width: 48px;
  height: 48px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  font-size: 24px;
}

.stat-content {
  flex: 1;
}

.stat-value {
  font-size: 24px;
  font-weight: bold;
  color: #303133;
  line-height: 1;
  margin-bottom: 5px;
}

.stat-label {
  font-size: 13px;
  color: #909399;
}

.overall-progress {
  padding: 20px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  border-radius: 8px;
  color: white;
}

.progress-header {
  display: flex;
  justify-content: space-between;
  margin-bottom: 10px;
  font-size: 15px;
  font-weight: 500;
}

.progress-percent {
  font-size: 20px;
  font-weight: bold;
}

.task-list {
  display: flex;
  flex-direction: column;
  gap: 15px;
}

.task-progress-item {
  padding: 20px;
  border: 2px solid #e4e7ed;
  border-radius: 8px;
  transition: all 0.3s;
}

.task-progress-item.completed {
  border-color: #67c23a;
  background: #f0f9ff;
}

.task-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 15px;
}

.task-info {
  flex: 1;
}

.task-name {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 16px;
  font-weight: 500;
  color: #303133;
  margin-bottom: 8px;
}

.task-type {
  margin-bottom: 5px;
}

.task-score {
  display: flex;
  align-items: baseline;
  gap: 4px;
  font-size: 14px;
  color: #606266;
}

.current-score {
  font-size: 24px;
  font-weight: bold;
  color: #409eff;
}

.target-score {
  font-size: 18px;
  color: #909399;
}

.task-progress {
  margin-bottom: 10px;
}

.task-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 13px;
  color: #909399;
}

.points {
  display: flex;
  align-items: center;
  gap: 4px;
}

.ranking-content {
  display: flex;
  flex-direction: column;
  gap: 25px;
}

.my-rank-box {
  display: flex;
  align-items: center;
  gap: 20px;
  padding: 20px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  border-radius: 12px;
  color: white;
}

.rank-badge {
  width: 80px;
  height: 80px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 32px;
  font-weight: bold;
  background: rgba(255, 255, 255, 0.2);
  backdrop-filter: blur(10px);
}

.rank-badge.rank-gold {
  background: linear-gradient(135deg, #ffd700 0%, #ffed4e 100%);
  color: #fff;
  box-shadow: 0 4px 15px rgba(255, 215, 0, 0.4);
}

.rank-badge.rank-silver {
  background: linear-gradient(135deg, #c0c0c0 0%, #e8e8e8 100%);
  color: #fff;
  box-shadow: 0 4px 15px rgba(192, 192, 192, 0.4);
}

.rank-badge.rank-bronze {
  background: linear-gradient(135deg, #cd7f32 0%, #e6a055 100%);
  color: #fff;
  box-shadow: 0 4px 15px rgba(205, 127, 50, 0.4);
}

.rank-info {
  flex: 1;
}

.rank-label {
  font-size: 16px;
  margin-bottom: 5px;
}

.rank-total {
  font-size: 14px;
  opacity: 0.9;
}

.rank-comparison h4 {
  margin: 0 0 15px 0;
  font-size: 16px;
  color: #303133;
}

.rank-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.rank-item {
  display: flex;
  align-items: center;
  gap: 15px;
  padding: 12px 15px;
  background: #f5f7fa;
  border-radius: 8px;
  transition: all 0.3s;
}

.rank-item.is-me {
  background: linear-gradient(135deg, #e0f2ff 0%, #d0e7ff 100%);
  border: 2px solid #409eff;
}

.rank-number {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  background: #909399;
  color: white;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: bold;
  flex-shrink: 0;
}

.rank-user {
  flex: 1;
}

.user-name {
  font-size: 15px;
  font-weight: 500;
  color: #303133;
}

.user-class {
  font-size: 12px;
  color: #909399;
  margin-top: 2px;
}

.rank-score {
  font-size: 16px;
  font-weight: bold;
  color: #409eff;
}

@media (max-width: 768px) {
  .stats-grid {
    grid-template-columns: repeat(2, 1fr);
  }

  .task-header {
    flex-direction: column;
    gap: 10px;
  }
}
</style>
