<template>
  <div class="campaign-detail-container">
    <div class="header">
      <h2>活动详情</h2>
      <div class="actions">
        <el-button @click="handleBack">返回</el-button>
        <el-button type="primary" @click="handleViewStats">查看统计</el-button>
      </div>
    </div>

    <el-card v-loading="loading">
      <template #header>
        <div class="card-header">
          <span class="title">{{ campaign.title }}</span>
          <el-tag :type="getStatusType(campaign.status)">{{ getStatusLabel(campaign.status) }}</el-tag>
        </div>
      </template>

      <el-descriptions :column="2" border>
        <el-descriptions-item label="活动描述" :span="2">
          {{ campaign.description || '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="开始时间">
          {{ formatDate(campaign.startTime) }}
        </el-descriptions-item>
        <el-descriptions-item label="结束时间">
          {{ formatDate(campaign.endTime) }}
        </el-descriptions-item>
        <el-descriptions-item label="完成规则">
          {{ getCompletionRuleLabel(campaign.completionRule) }}
        </el-descriptions-item>
        <el-descriptions-item label="完成阈值" v-if="campaign.completionRule === 'THRESHOLD'">
          {{ campaign.completionThreshold }} 个任务
        </el-descriptions-item>
        <el-descriptions-item label="报名模式">
          {{ getEnrollmentModeLabel(campaign.enrollmentMode) }}
        </el-descriptions-item>
        <el-descriptions-item label="报名范围">
          {{ getEnrollmentScopeLabel(campaign.enrollmentScope) }}
        </el-descriptions-item>
        <el-descriptions-item label="最大参与人数">
          {{ campaign.maxParticipants || '不限制' }}
        </el-descriptions-item>
        <el-descriptions-item label="显示设置">
          <el-tag size="small" v-if="campaign.showScores">显示分数</el-tag>
          <el-tag size="small" type="info" v-else>隐藏分数</el-tag>
          <el-tag size="small" v-if="campaign.showRanking" style="margin-left: 10px">显示排名</el-tag>
          <el-tag size="small" type="info" v-else style="margin-left: 10px">隐藏排名</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="创建时间">
          {{ formatDate(campaign.createdTime) }}
        </el-descriptions-item>
        <el-descriptions-item label="更新时间">
          {{ formatDate(campaign.updatedTime) }}
        </el-descriptions-item>
      </el-descriptions>
    </el-card>

    <el-card style="margin-top: 20px">
      <template #header>
        <div class="card-header">
          <span class="title">任务列表</span>
          <el-button type="primary" size="small" @click="handleManualEntry" v-if="campaign.status === 'ACTIVE'">
            手动录入数据
          </el-button>
        </div>
      </template>

      <el-table :data="tasks" border>
        <el-table-column prop="taskName" label="任务名称" min-width="150" />
        <el-table-column label="任务类型" width="180">
          <template #default="{ row }">
            {{ getTaskTypeLabel(row.taskType) }}
          </template>
        </el-table-column>
        <el-table-column label="目标值" width="120">
          <template #default="{ row }">
            {{ row.targetValue }} {{ getTaskUnitLabel(row.taskUnit) }}
          </template>
        </el-table-column>
        <el-table-column prop="points" label="完成得分" width="100" />
        <el-table-column label="可重复" width="90">
          <template #default="{ row }">
            <el-tag size="small" :type="row.isRepeatable ? 'success' : 'info'">
              {{ row.isRepeatable ? '是' : '否' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="总上限" width="90" v-if="tasks.some(t => t.isRepeatable)">
          <template #default="{ row }">
            {{ row.isRepeatable ? (row.maxCompletions || '无限') : '-' }}
          </template>
        </el-table-column>
        <el-table-column label="日上限" width="90" v-if="tasks.some(t => t.isRepeatable)">
          <template #default="{ row }">
            {{ row.isRepeatable ? (row.dailyLimit || '无限') : '-' }}
          </template>
        </el-table-column>
        <el-table-column label="必做" width="80">
          <template #default="{ row }">
            <el-tag size="small" :type="row.isRequired ? 'danger' : 'info'">
              {{ row.isRequired ? '是' : '否' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="自动采集" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="row.autoCollect ? 'success' : 'warning'">
              {{ row.autoCollect ? '是' : '否' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="sortOrder" label="排序" width="80" />
      </el-table>
    </el-card>

    <el-card style="margin-top: 20px" v-if="campaign.status !== 'DRAFT'">
      <template #header>
        <div class="card-header">
          <span class="title">参与概况</span>
          <el-button type="primary" size="small" @click="handleManageEnrollment">
            管理报名
          </el-button>
        </div>
      </template>

      <el-row :gutter="20">
        <el-col :span="6">
          <div class="stat-item">
            <div class="stat-value">{{ overview.totalParticipants || 0 }}</div>
            <div class="stat-label">总参与人数</div>
          </div>
        </el-col>
        <el-col :span="6">
          <div class="stat-item">
            <div class="stat-value">{{ overview.completedParticipants || 0 }}</div>
            <div class="stat-label">已完成人数</div>
          </div>
        </el-col>
        <el-col :span="6">
          <div class="stat-item">
            <div class="stat-value">{{ overview.inProgressParticipants || 0 }}</div>
            <div class="stat-label">进行中人数</div>
          </div>
        </el-col>
        <el-col :span="6">
          <div class="stat-item">
            <div class="stat-value">{{ overview.completionRate || '0%' }}</div>
            <div class="stat-label">完成率</div>
          </div>
        </el-col>
      </el-row>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getCampaignById, getTasks, getCampaignOverview } from '@/api/campaign'

const router = useRouter()
const route = useRoute()
const loading = ref(false)
const campaign = ref({})
const tasks = ref([])
const overview = reactive({
  totalParticipants: 0,
  completedParticipants: 0,
  inProgressParticipants: 0,
  completionRate: '0%'
})

const loadData = async () => {
  loading.value = true
  try {
    const [campaignRes, tasksRes] = await Promise.all([
      getCampaignById(route.params.id),
      getTasks(route.params.id)
    ])

    if (campaignRes.code === 200) {
      campaign.value = campaignRes.data
    } else {
      ElMessage.error(campaignRes.message || '加载活动信息失败')
    }

    if (tasksRes.code === 200) {
      tasks.value = tasksRes.data || []
    } else {
      ElMessage.error(tasksRes.message || '加载任务列表失败')
    }

    // 如果活动已启动，加载参与概况
    if (campaign.value.status !== 'DRAFT') {
      const overviewRes = await getCampaignOverview(route.params.id)
      if (overviewRes.code === 200) {
        Object.assign(overview, overviewRes.data)
      }
    }
  } catch (error) {
    ElMessage.error('加载失败: ' + error.message)
  } finally {
    loading.value = false
  }
}

const handleBack = () => {
  router.push('/admin/campaigns')
}

const handleViewStats = () => {
  router.push(`/admin/campaigns/${route.params.id}/stats`)
}

const handleManualEntry = () => {
  router.push(`/admin/campaigns/${route.params.id}/manual-entry`)
}

const handleManageEnrollment = () => {
  router.push(`/admin/campaigns/${route.params.id}/enrollments`)
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

const getEnrollmentModeLabel = (mode) => {
  const labels = {
    AUTO: '自动报名',
    MANUAL: '自主报名',
    INVITE_ONLY: '仅邀请'
  }
  return labels[mode] || mode
}

const getEnrollmentScopeLabel = (scope) => {
  const labels = {
    ALL: '全部学生',
    COLLEGE: '指定学院',
    GRADE: '指定年级',
    CLASS: '指定班级',
    CUSTOM: '指定学生'
  }
  return labels[scope] || scope
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
  return new Date(dateStr).toLocaleString('zh-CN', {
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
.campaign-detail-container {
  padding: 20px;
}

.header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.header h2 {
  margin: 0;
}

.actions {
  display: flex;
  gap: 10px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.card-header .title {
  font-weight: bold;
  font-size: 16px;
}

.stat-item {
  text-align: center;
  padding: 20px;
  background: #f5f7fa;
  border-radius: 4px;
}

.stat-value {
  font-size: 32px;
  font-weight: bold;
  color: #409eff;
  margin-bottom: 10px;
}

.stat-label {
  font-size: 14px;
  color: #909399;
}
</style>
