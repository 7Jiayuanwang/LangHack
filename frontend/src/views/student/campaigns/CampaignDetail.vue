<template>
  <div class="student-campaign-detail">
    <el-page-header @back="handleBack" :title="campaign.title || '活动详情'">
      <template #content>
        <el-tag :type="getStatusType(campaign.status)">{{ getStatusLabel(campaign.status) }}</el-tag>
      </template>
    </el-page-header>

    <div class="detail-content" v-loading="loading">
      <!-- 活动基本信息 -->
      <el-card class="info-card">
        <h3>活动简介</h3>
        <p class="description">{{ campaign.description || '暂无描述' }}</p>

        <div class="info-grid">
          <div class="info-item">
            <div class="info-label">
              <el-icon><Calendar /></el-icon>
              活动时间
            </div>
            <div class="info-value">
              {{ formatDate(campaign.startTime) }} 至 {{ formatDate(campaign.endTime) }}
            </div>
          </div>

          <div class="info-item">
            <div class="info-label">
              <el-icon><User /></el-icon>
              参与人数
            </div>
            <div class="info-value">
              {{ campaign.participantCount || 0 }} 人
            </div>
          </div>

          <div class="info-item">
            <div class="info-label">
              <el-icon><Trophy /></el-icon>
              完成规则
            </div>
            <div class="info-value">
              {{ getCompletionRuleLabel(campaign.completionRule) }}
            </div>
          </div>

          <div class="info-item" v-if="campaign.maxParticipants">
            <div class="info-label">
              <el-icon><Connection /></el-icon>
              参与限制
            </div>
            <div class="info-value">
              最多 {{ campaign.maxParticipants }} 人
            </div>
          </div>
        </div>
      </el-card>

      <!-- 任务列表 -->
      <el-card class="tasks-card">
        <h3>活动任务</h3>
        <div class="task-list">
          <div
            v-for="task in tasks"
            :key="task.id"
            class="task-item"
            :class="{ required: task.isRequired }"
          >
            <div class="task-header">
              <div class="task-name">
                <el-icon><Checked v-if="task.isRequired" /><Document v-else /></el-icon>
                {{ task.taskName || getTaskTypeLabel(task.taskType) || '未命名任务' }}
                <el-tag v-if="task.isRequired" type="danger" size="small">必做</el-tag>
              </div>
              <div class="task-score">+{{ task.points }} 分</div>
            </div>
            <div class="task-body">
              <div class="task-type">
                <el-tag size="small">{{ getTaskTypeLabel(task.taskType) }}</el-tag>
              </div>
              <div class="task-target">
                目标: {{ task.targetValue }} {{ getTaskUnitLabel(task.taskUnit) }}
              </div>
            </div>
          </div>
        </div>
      </el-card>

      <!-- 报名状态和操作 -->
      <el-card class="action-card" v-if="!isEnrolled">
        <div class="enroll-section">
          <div class="enroll-info">
            <el-icon class="info-icon"><InfoFilled /></el-icon>
            <div>
              <p class="enroll-title">{{ canEnroll ? '符合报名条件' : '不符合报名条件' }}</p>
              <p class="enroll-desc" v-if="!canEnroll">{{ enrollmentMessage }}</p>
              <p class="enroll-desc" v-else>点击下方按钮即可报名参加此活动</p>
            </div>
          </div>
          <el-button
            type="primary"
            size="large"
            :disabled="!canEnroll"
            @click="handleEnroll"
            :loading="enrolling"
          >
            立即报名
          </el-button>
        </div>
      </el-card>

      <el-card class="action-card" v-else>
        <div class="enrolled-section">
          <el-result icon="success" title="您已报名此活动">
            <template #sub-title>
              <p v-if="enrollmentStatus === 'PENDING'">您的报名申请正在审核中，请耐心等待</p>
              <p v-else>开始完成任务，冲击排行榜吧！</p>
            </template>
            <template #extra>
              <el-button type="primary" @click="handleViewProgress" v-if="enrollmentStatus === 'ENROLLED'">
                查看我的进度
              </el-button>
            </template>
          </el-result>
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
  Calendar, User, Trophy, Connection, Document, Checked, InfoFilled
} from '@element-plus/icons-vue'
import { getStudentCampaignDetail, enrollCampaign } from '@/api/campaign'

const router = useRouter()
const route = useRoute()
const loading = ref(false)
const enrolling = ref(false)

const campaign = ref({})
const tasks = ref([])
const isEnrolled = ref(false)
const enrollmentStatus = ref('')
const canEnroll = ref(true)
const enrollmentMessage = ref('')

const loadData = async () => {
  loading.value = true
  try {
    const res = await getStudentCampaignDetail(route.params.id)
    if (res.code === 200) {
      campaign.value = res.data.campaign || {}
      tasks.value = res.data.tasks || []
      isEnrolled.value = res.data.isEnrolled || false
      enrollmentStatus.value = res.data.enrollmentStatus || ''
      canEnroll.value = res.data.canEnroll !== false
      enrollmentMessage.value = res.data.enrollmentMessage || ''
    } else {
      ElMessage.error(res.message || '加载失败')
    }
  } catch (error) {
    ElMessage.error('加载失败: ' + error.message)
  } finally {
    loading.value = false
  }
}

const handleEnroll = async () => {
  enrolling.value = true
  try {
    const res = await enrollCampaign(route.params.id)
    if (res.code === 200) {
      ElMessage.success('报名成功')
      loadData()
    }
  } catch (error) {
    if (error.message && error.message.includes('已报名')) {
      ElMessage.warning('您已报名该活动')
      isEnrolled.value = true
      enrollmentStatus.value = 'ENROLLED'
    }
  } finally {
    enrolling.value = false
  }
}

const handleViewProgress = () => {
  router.push(`/student/campaigns/${route.params.id}/progress`)
}

const handleBack = () => {
  router.push('/student/campaigns')
}

const getStatusType = (status) => {
  const types = {
    DRAFT: 'info',
    ACTIVE: 'success',
    ENDED: 'warning',
    ARCHIVED: 'danger'
  }
  return types[status] || 'info'
}

const getStatusLabel = (status) => {
  const labels = {
    DRAFT: '草稿',
    ACTIVE: '进行中',
    ENDED: '已结束',
    ARCHIVED: '已归档'
  }
  return labels[status] || status
}

const getCompletionRuleLabel = (rule) => {
  const labels = {
    ANY: '完成任意任务',
    ALL: '完成所有任务',
    THRESHOLD: '完成指定数量任务'
  }
  return labels[rule] || rule
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

const formatDate = (dateStr) => {
  if (!dateStr) return '-'
  return new Date(dateStr).toLocaleDateString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  })
}

onMounted(() => {
  loadData()
})
</script>

<style scoped>
.student-campaign-detail {
  padding: 20px;
  max-width: 900px;
  margin: 0 auto;
}

.el-page-header {
  margin-bottom: 20px;
}

.detail-content {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.info-card h3,
.tasks-card h3 {
  margin: 0 0 20px 0;
  font-size: 18px;
  color: #303133;
}

.description {
  color: #606266;
  line-height: 1.8;
  margin-bottom: 20px;
}

.info-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: 20px;
}

.info-item {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.info-label {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: #909399;
}

.info-value {
  font-size: 15px;
  color: #303133;
  font-weight: 500;
}

.task-list {
  display: flex;
  flex-direction: column;
  gap: 15px;
}

.task-item {
  padding: 15px;
  background: #f5f7fa;
  border-radius: 8px;
  border-left: 4px solid #409eff;
}

.task-item.required {
  border-left-color: #f56c6c;
  background: #fef0f0;
}

.task-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
}

.task-name {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 15px;
  font-weight: 500;
  color: #303133;
}

.task-score {
  color: #409eff;
  font-weight: bold;
}

.task-body {
  display: flex;
  gap: 20px;
  align-items: center;
  font-size: 14px;
  color: #606266;
}

.enroll-section {
  display: flex;
  flex-direction: column;
  gap: 20px;
  align-items: center;
  text-align: center;
  padding: 20px;
}

.enroll-info {
  display: flex;
  gap: 15px;
  align-items: flex-start;
  text-align: left;
}

.info-icon {
  font-size: 24px;
  color: #409eff;
  flex-shrink: 0;
}

.enroll-title {
  font-size: 16px;
  font-weight: 500;
  color: #303133;
  margin: 0 0 5px 0;
}

.enroll-desc {
  font-size: 14px;
  color: #606266;
  margin: 0;
}

.enrolled-section {
  padding: 20px;
}

@media (max-width: 768px) {
  .info-grid {
    grid-template-columns: 1fr;
  }
}
</style>
