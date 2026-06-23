<template>
  <div class="student-campaign-list">
    <div class="header">
      <h2>学习活动</h2>
    </div>

    <el-tabs v-model="activeTab" @tab-change="handleTabChange">
      <el-tab-pane label="进行中活动" name="available">
        <div class="filter-bar">
          <el-input
            v-model="searchKeyword"
            placeholder="搜索活动"
            clearable
            @keyup.enter="loadAvailableCampaigns"
            style="width: 200px"
          >
            <template #prefix>
              <el-icon><Search /></el-icon>
            </template>
          </el-input>
        </div>

        <div class="campaign-grid">
          <el-card
            v-for="campaign in availableCampaigns"
            :key="campaign.id"
            class="campaign-card"
            shadow="hover"
            @click="handleViewDetail(campaign.id)"
          >
            <template #header>
              <div class="card-header">
                <span class="campaign-title">{{ campaign.title }}</span>
                <el-tag type="success">进行中</el-tag>
              </div>
            </template>

            <div class="campaign-info">
              <p class="description">{{ campaign.description || '暂无描述' }}</p>

              <div class="meta-info">
                <div class="meta-item">
                  <el-icon><Calendar /></el-icon>
                  <span>{{ formatDateRange(campaign.startTime, campaign.endTime) }}</span>
                </div>
                <div class="meta-item">
                  <el-icon><User /></el-icon>
                  <span>{{ campaign.participantCount || 0 }} 人参与</span>
                </div>
              </div>

              <div class="card-footer">
                <el-button
                  type="primary"
                  :disabled="!canEnroll(campaign)"
                  @click.stop="handleEnroll(campaign)"
                >
                  {{ getEnrollButtonText(campaign) }}
                </el-button>
              </div>
            </div>
          </el-card>

          <el-empty v-if="availableCampaigns.length === 0" description="暂无可报名活动" />
        </div>

        <el-pagination
          v-if="availablePagination.total > 0"
          v-model:current-page="availablePagination.current"
          v-model:page-size="availablePagination.size"
          :total="availablePagination.total"
          :page-sizes="[10, 20, 30]"
          layout="total, sizes, prev, pager, next"
          @size-change="loadAvailableCampaigns"
          @current-change="loadAvailableCampaigns"
          class="pagination"
        />
      </el-tab-pane>

      <el-tab-pane label="我的活动" name="enrolled">
        <div class="filter-bar">
          <el-select v-model="enrolledStatus" placeholder="全部状态" clearable @change="loadEnrolledCampaigns" style="width: 150px">
            <el-option label="进行中" value="IN_PROGRESS" />
            <el-option label="已完成" value="COMPLETED" />
          </el-select>
        </div>

        <div class="campaign-grid">
          <el-card
            v-for="campaign in enrolledCampaigns"
            :key="campaign.id"
            class="campaign-card enrolled"
            shadow="hover"
            @click="handleViewProgress(campaign.id)"
          >
            <template #header>
              <div class="card-header">
                <span class="campaign-title">{{ campaign.title }}</span>
                <el-tag :type="getStatusType(campaign.completionStatus)">
                  {{ getStatusLabel(campaign.completionStatus) }}
                </el-tag>
              </div>
            </template>

            <div class="campaign-info">
              <div class="progress-section">
                <div class="progress-label">
                  <span>完成进度</span>
                  <span class="score">{{ campaign.totalScore || 0 }} 分</span>
                </div>
                <el-progress
                  :percentage="calculateProgress(campaign)"
                  :color="getProgressColor(campaign)"
                />
                <div class="progress-detail">
                  已完成 {{ campaign.completedTasks || 0 }} / {{ campaign.totalTasks || 0 }} 个任务
                </div>
              </div>

              <div class="meta-info">
                <div class="meta-item">
                  <el-icon><TrophyBase /></el-icon>
                  <span>排名: {{ campaign.myRank || '-' }}</span>
                </div>
                <div class="meta-item">
                  <el-icon><Calendar /></el-icon>
                  <span>剩余: {{ getRemainingDays(campaign.endTime) }}</span>
                </div>
              </div>

              <div class="card-footer">
                <el-button type="primary" @click.stop="handleViewProgress(campaign.id)">
                  查看进度
                </el-button>
                <el-button @click.stop="handleWithdraw(campaign)" v-if="campaign.completionStatus !== 'COMPLETED'">
                  退出活动
                </el-button>
              </div>
            </div>
          </el-card>

          <el-empty v-if="enrolledCampaigns.length === 0" description="您还未参与任何活动" />
        </div>

        <el-pagination
          v-if="enrolledPagination.total > 0"
          v-model:current-page="enrolledPagination.current"
          v-model:page-size="enrolledPagination.size"
          :total="enrolledPagination.total"
          :page-sizes="[10, 20, 30]"
          layout="total, sizes, prev, pager, next"
          @size-change="loadEnrolledCampaigns"
          @current-change="loadEnrolledCampaigns"
          class="pagination"
        />
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Calendar, User, TrophyBase } from '@element-plus/icons-vue'
import { getEnrollableCampaigns, getMyEnrolledCampaigns, enrollCampaign, withdrawCampaign } from '@/api/campaign'

const router = useRouter()
const activeTab = ref('available')
const searchKeyword = ref('')
const enrolledStatus = ref('')

const availableCampaigns = ref([])
const enrolledCampaigns = ref([])

const availablePagination = reactive({
  current: 1,
  size: 10,
  total: 0
})

const enrolledPagination = reactive({
  current: 1,
  size: 10,
  total: 0
})

const loadAvailableCampaigns = async () => {
  try {
    const res = await getEnrollableCampaigns({
      current: availablePagination.current,
      size: availablePagination.size,
      keyword: searchKeyword.value
    })
    if (res.code === 200) {
      availableCampaigns.value = res.data.records || []
      availablePagination.total = res.data.total || 0
    } else {
      ElMessage.error(res.message || '加载失败')
    }
  } catch (error) {
    ElMessage.error('加载失败: ' + error.message)
  }
}

const loadEnrolledCampaigns = async () => {
  try {
    const res = await getMyEnrolledCampaigns({
      current: enrolledPagination.current,
      size: enrolledPagination.size,
      status: enrolledStatus.value
    })
    if (res.code === 200) {
      enrolledCampaigns.value = res.data.records || []
      enrolledPagination.total = res.data.total || 0
    } else {
      ElMessage.error(res.message || '加载失败')
    }
  } catch (error) {
    ElMessage.error('加载失败: ' + error.message)
  }
}

const handleTabChange = (tab) => {
  if (tab === 'available') {
    loadAvailableCampaigns()
  } else if (tab === 'enrolled') {
    loadEnrolledCampaigns()
  }
}

const handleViewDetail = (id) => {
  router.push(`/student/campaigns/${id}`)
}

const handleViewProgress = (id) => {
  router.push(`/student/campaigns/${id}/progress`)
}

const canEnroll = (campaign) => {
  // TODO: 根据实际的报名资格字段判断
  return campaign.canEnroll !== false
}

const getEnrollButtonText = (campaign) => {
  if (campaign.isEnrolled) return '已报名'
  if (!canEnroll(campaign)) return '不符合条件'
  return '立即报名'
}

const handleEnroll = async (campaign) => {
  if (campaign.isEnrolled || !canEnroll(campaign)) return

  try {
    await ElMessageBox.confirm('确认报名参加该活动？', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'info'
    })

    const res = await enrollCampaign(campaign.id)
    if (res.code === 200) {
      ElMessage.success('报名成功')
      loadAvailableCampaigns()
    } else {
      ElMessage.error(res.message || '报名失败')
    }
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('报名失败: ' + error.message)
    }
  }
}

const handleWithdraw = async (campaign) => {
  try {
    await ElMessageBox.confirm('确认退出该活动？退出后您的进度将被保留，但无法继续参与。', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })

    const res = await withdrawCampaign(campaign.id)
    if (res.code === 200) {
      ElMessage.success('已退出活动')
      loadEnrolledCampaigns()
    } else {
      ElMessage.error(res.message || '操作失败')
    }
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('操作失败: ' + error.message)
    }
  }
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

const calculateProgress = (campaign) => {
  const total = campaign.totalTasks || 1
  const completed = campaign.completedTasks || 0
  return Math.round((completed / total) * 100)
}

const getProgressColor = (campaign) => {
  const progress = calculateProgress(campaign)
  if (progress >= 100) return '#67c23a'
  if (progress >= 50) return '#409eff'
  return '#e6a23c'
}

const formatDateRange = (start, end) => {
  if (!start || !end) return '-'
  const startDate = new Date(start).toLocaleDateString('zh-CN', { month: 'short', day: 'numeric' })
  const endDate = new Date(end).toLocaleDateString('zh-CN', { month: 'short', day: 'numeric' })
  return `${startDate} - ${endDate}`
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

onMounted(() => {
  loadAvailableCampaigns()
})
</script>

<style scoped>
.student-campaign-list {
  padding: 20px;
  max-width: 1200px;
  margin: 0 auto;
}

.header h2 {
  margin: 0 0 20px 0;
}

.filter-bar {
  margin-bottom: 20px;
}

.campaign-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(350px, 1fr));
  gap: 20px;
  margin-bottom: 20px;
}

.campaign-card {
  cursor: pointer;
  transition: transform 0.2s;
}

.campaign-card:hover {
  transform: translateY(-4px);
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.campaign-title {
  font-weight: bold;
  font-size: 16px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  flex: 1;
  margin-right: 10px;
}

.campaign-info {
  display: flex;
  flex-direction: column;
  gap: 15px;
}

.description {
  color: #666;
  font-size: 14px;
  line-height: 1.5;
  overflow: hidden;
  text-overflow: ellipsis;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  margin: 0;
}

.meta-info {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.meta-item {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: #909399;
}

.meta-item .el-icon {
  font-size: 14px;
}

.progress-section {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.progress-label {
  display: flex;
  justify-content: space-between;
  font-size: 14px;
  color: #606266;
}

.score {
  font-weight: bold;
  color: #409eff;
}

.progress-detail {
  font-size: 12px;
  color: #909399;
}

.card-footer {
  display: flex;
  gap: 10px;
  padding-top: 10px;
  border-top: 1px solid #ebeef5;
}

.card-footer .el-button {
  flex: 1;
}

.pagination {
  display: flex;
  justify-content: center;
  margin-top: 20px;
}

@media (max-width: 768px) {
  .campaign-grid {
    grid-template-columns: 1fr;
  }
}
</style>
