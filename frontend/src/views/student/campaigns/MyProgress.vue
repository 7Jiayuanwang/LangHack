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
              <div class="stat-value">{{ formatScore(progress.totalScore) }}</div>
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

      <!-- 连续学习加成卡片 -->
      <el-card class="streak-card" v-if="streak.streakDays !== undefined">
        <div class="streak-header">
          <h3>连续学习加成</h3>
          <el-tag type="warning" v-if="streak.currentMultiplier > 1">
            x{{ streak.currentMultiplier }} 加成中
          </el-tag>
        </div>
        <div class="streak-content">
          <div class="streak-days">
            <span class="streak-number">{{ streak.streakDays }}</span>
            <span class="streak-label">天连续学习</span>
          </div>
          <div class="streak-week">
            <div
              v-for="day in 7"
              :key="day"
              class="week-dot"
              :class="{ active: day <= streak.streakDays }"
            ></div>
          </div>
          <div class="streak-tip">
            {{ streak.streakDays >= 7 ? '本周全勤！加成已封顶' : `再坚持${7 - streak.streakDays}天即可获得最高加成` }}
          </div>
        </div>
      </el-card>

      <!-- 班级活跃热力图 -->
      <el-card class="class-activity-card" v-if="classActivity.className">
        <div class="class-activity-header">
          <h3>{{ classActivity.className }}</h3>
          <span class="activity-label">今日班级动态</span>
        </div>
        <div class="class-activity-content">
          <div class="activity-stat">
            <span class="activity-number">{{ classActivity.activeToday }}</span>
            <span class="activity-total">/ {{ classActivity.totalStudents }} 人</span>
          </div>
          <div class="activity-desc">今日已有 {{ classActivity.activeToday }} 位同学完成了学习任务</div>
        </div>
      </el-card>

      <!-- 本周之星 -->
      <el-card class="weekly-awards-card" v-if="weeklyAwards.topIndividuals && weeklyAwards.topIndividuals.length">
        <h3>🏆 本周之星</h3>
        <div class="weekly-awards-content">
          <div class="award-section" v-if="weeklyAwards.topIndividuals && weeklyAwards.topIndividuals.length">
            <h4>优秀个人</h4>
            <div class="award-list">
              <div v-for="item in weeklyAwards.topIndividuals" :key="'ind-'+item.userId" class="award-item">
                <span class="award-rank" :class="'rank-'+item.rank">{{ item.rank }}</span>
                <span class="award-name">{{ item.userName }}</span>
                <span class="award-class" v-if="item.className">{{ item.className }}</span>
                <span class="award-score">{{ formatScore(item.score) }} 分</span>
              </div>
            </div>
          </div>
          <div class="award-section" v-if="weeklyAwards.topClasses && weeklyAwards.topClasses.length">
            <h4>优秀班级</h4>
            <div class="award-list">
              <div v-for="item in weeklyAwards.topClasses" :key="'cls-'+item.className" class="award-item">
                <span class="award-rank" :class="'rank-'+item.rank">{{ item.rank }}</span>
                <span class="award-name">{{ item.className }}</span>
                <span class="award-score">{{ formatScore(item.score) }} 分</span>
              </div>
            </div>
          </div>
        </div>
      </el-card>

      <!-- 积分流水账单 -->
      <el-card class="score-logs-card" v-if="scoreLogs.length > 0">
        <div class="score-logs-header">
          <h3>积分账单</h3>
          <div class="score-logs-total">
            <span class="total-label">累计</span>
            <span class="total-value">{{ formatScore(scoreLogsTotal) }}</span>
            <span class="total-label">分</span>
          </div>
        </div>
        <div class="score-logs-list">
          <div v-for="log in scoreLogs" :key="log.id" class="score-log-item">
            <div class="log-left">
              <div class="log-action">{{ log.taskName || '任务完成' }}</div>
              <div class="log-time">{{ formatLogTime(log.createdTime) }}</div>
            </div>
            <div class="log-right">
              <span class="log-score">+{{ formatScore(log.finalScore) }}</span>
              <span class="log-detail" v-if="log.multiplier > 1">
                ({{ log.baseScore }} × {{ log.multiplier }})
              </span>
            </div>
          </div>
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
                {{ task.isCompleted
                  ? `已获得 ${task.earnedScore != null ? task.earnedScore : task.points} 分`
                  : `完成可得 ${task.points} 分` }}
              </span>
              <span class="cumulative">
                累计{{ getCumulativeLabel(task) }}
              </span>
              <span class="completion-count" v-if="task.completionCount > 0">
                已完成 {{ task.completionCount }} 次
              </span>
              <span class="last-update" v-if="task.lastCompletionDate || task.lastUpdateTime">
                最近完成: {{ formatShortDate(task.lastCompletionDate || task.lastUpdateTime) }}
              </span>
            </div>
          </div>

          <el-empty v-if="taskProgress.length === 0" description="暂无任务数据" />
        </div>
      </el-card>

      <!-- 积分明细 -->
      <el-card class="score-details-card">
        <h3>积分明细</h3>
        <div v-if="scoreDetails.length === 0" class="score-empty">
          <el-empty description="暂无积分记录" :image-size="60" />
        </div>
        <div v-else class="score-detail-list">
          <div
            v-for="item in scoreDetails"
            :key="item.taskId"
            class="score-detail-item"
            :class="{ expanded: expandedTask === item.taskId }"
            @click="toggleExpand(item.taskId)"
          >
            <div class="score-detail-header">
              <div class="score-task-name">
                <el-icon><Medal /></el-icon>
                {{ item.taskName || getTaskTypeLabel(item.taskType) }}
              </div>
              <div class="score-earned" :class="{ 'has-score': item.earnedScore > 0 }">
                +{{ item.earnedScore }} 分
              </div>
            </div>
            <div class="score-detail-meta">
              <el-tag size="small" effect="plain">{{ getTaskTypeLabel(item.taskType) }}</el-tag>
              <span v-if="item.completionCount > 0" class="meta-item">完成 {{ item.completionCount }} 次</span>
              <span v-if="item.lastUpdateTime" class="meta-item">最近更新: {{ formatDate(item.lastCompletionDate || item.lastUpdateTime) }}</span>
            </div>
            <div v-if="expandedTask === item.taskId" class="score-detail-expand">
              <div class="expand-row">
                <span class="expand-label">总上限:</span>
                <span class="expand-value">{{ item.maxCompletions ? item.maxCompletions + ' 次' : '无限制' }}</span>
              </div>
              <div class="expand-row">
                <span class="expand-label">日上限:</span>
                <span class="expand-value">{{ item.dailyLimit ? item.dailyLimit + ' 次' : '无限制' }}</span>
              </div>
              <div class="expand-row">
                <span class="expand-label">今日完成:</span>
                <span class="expand-value">{{ item.todayCompletionCount || 0 }} 次</span>
              </div>
              <div class="expand-row">
                <span class="expand-label">累计完成:</span>
                <span class="expand-value">{{ item.completionCount || 0 }} 次</span>
              </div>
            </div>
            <div v-if="item.recentRecords && item.recentRecords.length > 0 && expandedTask === item.taskId" class="record-list">
              <div class="record-list-title">手动录入记录</div>
              <div
                v-for="(rec, idx) in item.recentRecords"
                :key="idx"
                class="record-entry"
              >
                <span class="record-value">+{{ rec.value }} {{ getTaskUnitLabel(item.taskUnit) }}</span>
                <span class="record-type">{{ rec.type === 'MANUAL' ? '手动' : '批量导入' }}</span>
                <span class="record-time">{{ formatDate(rec.createdTime) }}</span>
                <span v-if="rec.remark" class="record-remark">{{ rec.remark }}</span>
              </div>
            </div>
          </div>
        </div>
      </el-card>

      <!-- 排名信息 -->
      <el-card class="ranking-card" v-if="campaign.showRanking">
        <div class="ranking-header">
          <h3>我的排名</h3>
          <el-date-picker
            v-model="selectedWeek"
            type="week"
            format="YYYY 第 ww 周"
            placeholder="选择周"
            value-format="YYYY-MM-DD"
            :clearable="true"
            size="small"
            style="width: 180px"
            @change="onWeekChange"
          />
        </div>
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
                  {{ formatScore(item.totalScore) }} 分
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
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  Trophy, Checked, TrophyBase, Timer, CircleCheck, Clock, Medal
} from '@element-plus/icons-vue'
import {
  getStudentCampaignDetail,
  getMyProgress,
  getMyTaskProgress,
  getMyRanking,
  getMyScoreDetails,
  getMyScoreLogs,
  getMyStreak,
  getClassActivityToday,
  getWeeklyAwards
} from '@/api/campaign'

const router = useRouter()
const route = useRoute()
const loading = ref(false)

const campaign = ref({})
const progress = ref({})
const taskProgress = ref([])
const ranking = ref({})
const scoreDetails = ref([])
const scoreLogs = ref([])
const scoreLogsTotal = computed(() => scoreLogs.value.reduce((sum, log) => sum + (log.finalScore || 0), 0))
const streak = ref({})
const classActivity = ref({})

const formatScore = (val) => {
  if (val == null) return '0'
  const num = Number(val)
  if (isNaN(num)) return '0'
  return num % 1 === 0 ? num.toString() : num.toFixed(2)
}
const weeklyAwards = ref({})
const expandedTask = ref(null)

// 周选择器：默认当前周周一
const getCurrentMonday = () => {
  const d = new Date()
  const day = d.getDay()
  const diff = d.getDate() - day + (day === 0 ? -6 : 1)
  const monday = new Date(d.setDate(diff))
  return monday.toISOString().slice(0, 10)
}
const selectedWeek = ref(getCurrentMonday())

const onWeekChange = (val) => {
  loadRanking(val)
}

const loadRanking = async (weekStart) => {
  try {
    const params = weekStart ? { weekStart } : {}
    const rankingRes = await getMyRanking(route.params.id, params)
    if (rankingRes.code === 200) {
      ranking.value = rankingRes.data || {}
    }
  } catch (error) {
    console.error('加载排名失败', error)
  }
}

const toggleExpand = (taskId) => {
  expandedTask.value = expandedTask.value === taskId ? null : taskId
}

const loadData = async () => {
  loading.value = true
  try {
    const [campaignRes, progressRes, tasksRes, scoreRes, streakRes, activityRes, logsRes, awardsRes] = await Promise.all([
      getStudentCampaignDetail(route.params.id),
      getMyProgress(route.params.id),
      getMyTaskProgress(route.params.id),
      getMyScoreDetails(route.params.id),
      getMyStreak(route.params.id).catch(() => ({ code: 0 })),
      getClassActivityToday(route.params.id).catch(() => ({ code: 0 })),
      getMyScoreLogs(route.params.id).catch(() => ({ code: 0 })),
      getWeeklyAwards(route.params.id).catch(() => ({ code: 0 }))
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
    if (scoreRes.code === 200) {
      scoreDetails.value = scoreRes.data || []
    }
    if (streakRes.code === 200) {
      streak.value = streakRes.data || {}
    }
    if (activityRes.code === 200) {
      classActivity.value = activityRes.data || {}
    }
    if (logsRes.code === 200) {
      scoreLogs.value = logsRes.data || []
    }
    if (awardsRes.code === 200) {
      weeklyAwards.value = awardsRes.data || {}
    }
  } catch (error) {
    ElMessage.error('加载失败: ' + error.message)
  } finally {
    loading.value = false
  }
  // 加载初始排名（默认当周）
  loadRanking(selectedWeek.value)
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
  // 优先基于必做任务计算完成度（达到基本要求就算完成）
  const requiredTasks = taskProgress.value.filter(t => t.isRequired)
  if (requiredTasks.length > 0) {
    const requiredCompleted = requiredTasks.filter(t => t.isCompleted).length
    return Math.round((requiredCompleted / requiredTasks.length) * 100)
  }
  // 无必做任务时，基于所有任务计算
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

const formatDate = (dateStr) => {
  if (!dateStr) return '-'
  const str = String(dateStr)
  if (str.length === 10 && str.includes('-')) {
    const [y, m, d] = str.split('-')
    return `${y}/${m}/${d}`
  }
  return new Date(str).toLocaleString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  })
}

const formatShortDate = (dateStr) => {
  if (!dateStr) return '-'
  const str = String(dateStr)
  // 纯日期格式 (yyyy-MM-dd)，不带时间
  if (str.length === 10 && str.includes('-')) {
    const [, m, d] = str.split('-')
    return `${m}/${d}`
  }
  const d = new Date(str)
  const month = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  const hour = String(d.getHours()).padStart(2, '0')
  const minute = String(d.getMinutes()).padStart(2, '0')
  return `${month}/${day} ${hour}:${minute}`
}

const formatLogTime = (dateStr) => {
  if (!dateStr) return ''
  const d = new Date(dateStr)
  const now = new Date()
  const isToday = d.toDateString() === now.toDateString()
  const hour = String(d.getHours()).padStart(2, '0')
  const minute = String(d.getMinutes()).padStart(2, '0')
  if (isToday) return `今天 ${hour}:${minute}`
  const month = d.getMonth() + 1
  const day = d.getDate()
  return `${month}月${day}日 ${hour}:${minute}`
}

const getCumulativeLabel = (task) => {
  const val = task.currentValue || 0
  const labels = {
    READING_TIME: `阅读 ${val} 分钟`,
    WORD_LEARNING: `学习 ${val} 个`,
    TEXT_UPLOAD: `上传 ${val} 篇`,
    AI_QA: `问答 ${val} 次`,
    FEEDBACK: `反馈 ${val} 条`,
    MANUAL: `完成 ${val}`
  }
  return labels[task.taskType] || `完成 ${val}`
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
.tasks-card h3 {
  margin: 0 0 20px 0;
  font-size: 18px;
  color: #303133;
}

.ranking-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 20px;
}

.ranking-header h3 {
  margin: 0;
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
  flex-wrap: wrap;
  gap: 8px;
  font-size: 13px;
  color: #909399;
}

.last-update {
  font-size: 12px;
  color: #c0c4cc;
}

.cumulative {
  font-size: 12px;
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

.score-details-card h3 {
  margin: 0 0 20px 0;
  font-size: 18px;
  color: #303133;
}

.score-detail-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.score-detail-item {
  padding: 16px;
  border: 1px solid #e4e7ed;
  border-radius: 8px;
  background: #fafafa;
  cursor: pointer;
  transition: all 0.3s;
}

.score-detail-item:active {
  background: #f0f0f0;
}

.score-detail-item.expanded {
  border-color: #409eff;
  background: #f5f9ff;
}

.score-detail-expand {
  margin-top: 12px;
  padding: 12px;
  background: #fff;
  border-radius: 6px;
  border: 1px dashed #dcdfe6;
}

.expand-row {
  display: flex;
  justify-content: space-between;
  padding: 4px 0;
  font-size: 13px;
}

.expand-label {
  color: #909399;
}

.expand-value {
  color: #303133;
  font-weight: 500;
}

.score-detail-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

.score-task-name {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 15px;
  font-weight: 500;
  color: #303133;
}

.score-earned {
  font-size: 18px;
  font-weight: bold;
  color: #909399;
}

.score-earned.has-score {
  color: #67c23a;
}

.score-detail-meta {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 10px;
}

.meta-item {
  font-size: 12px;
  color: #909399;
}

.record-list {
  border-top: 1px solid #ebeef5;
  padding-top: 10px;
}

.record-list-title {
  font-size: 12px;
  color: #909399;
  margin-bottom: 8px;
}

.record-entry {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 4px 0;
  font-size: 13px;
  color: #606266;
  border-bottom: 1px dashed #f0f0f0;
}

.record-entry:last-child {
  border-bottom: none;
}

.record-value {
  color: #409eff;
  font-weight: 500;
  min-width: 60px;
}

.record-type {
  color: #909399;
  min-width: 60px;
}

.record-time {
  color: #c0c4cc;
  font-size: 12px;
}

.record-remark {
  color: #606266;
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.score-empty {
  padding: 20px 0;
}

.streak-card,
.class-activity-card {
  margin-bottom: 16px;
}

.weekly-awards-card {
  margin-bottom: 16px;
}

.weekly-awards-card h3 {
  margin: 0 0 16px 0;
  font-size: 16px;
}

.weekly-awards-content {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}

.award-section h4 {
  margin: 0 0 10px 0;
  font-size: 14px;
  color: #606266;
}

.award-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.award-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  background: #f8f9fa;
  border-radius: 8px;
}

.award-rank {
  width: 22px;
  height: 22px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  font-weight: bold;
  color: #fff;
  background: #909399;
}

.award-rank.rank-1 { background: #f56c6c; }
.award-rank.rank-2 { background: #e6a23c; }
.award-rank.rank-3 { background: #409eff; }

.award-name {
  flex: 1;
  font-size: 14px;
  color: #303133;
}

.award-class {
  font-size: 12px;
  color: #909399;
}

.award-score {
  font-size: 14px;
  font-weight: bold;
  color: #409eff;
}

.score-logs-card {
  margin-bottom: 16px;
}

.score-logs-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}

.score-logs-header h3 {
  margin: 0;
  font-size: 16px;
}

.score-logs-total {
  display: flex;
  align-items: baseline;
  gap: 4px;
}

.score-logs-total .total-label {
  font-size: 13px;
  color: #909399;
}

.score-logs-total .total-value {
  font-size: 24px;
  font-weight: bold;
  color: #67c23a;
}

.score-logs-list {
  max-height: 300px;
  overflow-y: auto;
}

.score-log-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 0;
  border-bottom: 1px solid #f0f0f0;
}

.score-log-item:last-child {
  border-bottom: none;
}

.log-left {
  flex: 1;
}

.log-action {
  font-size: 14px;
  color: #303133;
  margin-bottom: 2px;
}

.log-time {
  font-size: 12px;
  color: #909399;
}

.log-right {
  text-align: right;
}

.log-score {
  font-size: 16px;
  font-weight: bold;
  color: #67c23a;
}

.log-detail {
  display: block;
  font-size: 11px;
  color: #e6a23c;
}

.streak-header,
.class-activity-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}

.streak-header h3,
.class-activity-header h3 {
  margin: 0;
  font-size: 16px;
}

.streak-content {
  text-align: center;
}

.streak-days {
  margin-bottom: 12px;
}

.streak-number {
  font-size: 36px;
  font-weight: bold;
  color: #e6a23c;
}

.streak-label {
  font-size: 14px;
  color: #909399;
  margin-left: 4px;
}

.streak-week {
  display: flex;
  justify-content: center;
  gap: 8px;
  margin-bottom: 12px;
}

.week-dot {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  background: #ebeef5;
  transition: all 0.3s;
}

.week-dot.active {
  background: linear-gradient(135deg, #e6a23c 0%, #f7ba2a 100%);
  box-shadow: 0 2px 8px rgba(230, 162, 60, 0.4);
}

.streak-tip {
  font-size: 13px;
  color: #909399;
}

.class-activity-content {
  text-align: center;
}

.activity-stat {
  margin-bottom: 8px;
}

.activity-number {
  font-size: 32px;
  font-weight: bold;
  color: #409eff;
}

.activity-total {
  font-size: 16px;
  color: #909399;
  margin-left: 4px;
}

.activity-desc {
  font-size: 13px;
  color: #909399;
}

.activity-label {
  font-size: 12px;
  color: #909399;
}
</style>
