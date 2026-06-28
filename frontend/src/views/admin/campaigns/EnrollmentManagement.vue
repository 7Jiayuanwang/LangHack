<template>
  <div class="enrollment-management-container">
    <div class="header">
      <h2>报名管理</h2>
      <div class="actions">
        <el-button @click="handleBack">返回</el-button>
        <el-button type="primary" @click="showBatchEnrollDialog = true">批量添加参与者</el-button>
      </div>
    </div>

    <el-tabs v-model="activeTab" @tab-change="handleTabChange">
      <el-tab-pane label="待审核" name="pending">
        <el-table :data="pendingList" v-loading="loading" stripe>
          <el-table-column prop="userName" label="姓名" width="120" />
          <el-table-column prop="studentId" label="学号" width="150" show-overflow-tooltip />
          <el-table-column prop="className" label="班级" min-width="150" />
          <el-table-column prop="collegeName" label="学院" min-width="150" />
          <el-table-column label="报名时间" width="180">
            <template #default="{ row }">
              {{ formatDate(row.enrollmentTime) }}
            </template>
          </el-table-column>
          <el-table-column label="报名来源" width="100">
            <template #default="{ row }">
              <el-tag size="small" :type="getSourceType(row.enrollmentSource)">
                {{ getSourceLabel(row.enrollmentSource) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="200" fixed="right">
            <template #default="{ row }">
              <el-button link type="success" @click="handleApprove(row)">批准</el-button>
              <el-button link type="danger" @click="handleReject(row)">驳回</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="已通过" name="enrolled">
        <el-table :data="enrolledList" v-loading="loading" stripe>
          <el-table-column prop="userName" label="姓名" width="120" />
          <el-table-column prop="studentId" label="学号" width="150" show-overflow-tooltip />
          <el-table-column prop="className" label="班级" min-width="150" />
          <el-table-column prop="collegeName" label="学院" min-width="150" />
          <el-table-column prop="totalScore" label="总得分" width="100" sortable />
          <el-table-column prop="completedTasks" label="完成任务数" width="120" sortable />
          <el-table-column label="参与时间" width="180">
            <template #default="{ row }">
              {{ formatDate(row.joinTime) }}
            </template>
          </el-table-column>
          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <el-tag size="small" :type="getCompletionStatusType(row.completionStatus)">
                {{ getCompletionStatusLabel(row.completionStatus) }}
              </el-tag>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="已驳回" name="rejected">
        <el-table :data="rejectedList" v-loading="loading" stripe>
          <el-table-column prop="userName" label="姓名" width="120" />
          <el-table-column prop="studentId" label="学号" width="150" show-overflow-tooltip />
          <el-table-column prop="className" label="班级" min-width="150" />
          <el-table-column prop="collegeName" label="学院" min-width="150" />
          <el-table-column label="报名时间" width="180">
            <template #default="{ row }">
              {{ formatDate(row.enrollmentTime) }}
            </template>
          </el-table-column>
          <el-table-column prop="rejectReason" label="驳回原因" min-width="200" show-overflow-tooltip />
        </el-table>
      </el-tab-pane>
    </el-tabs>

    <!-- 驳回原因对话框 -->
    <el-dialog
      v-model="showRejectDialog"
      title="驳回原因"
      width="500px"
      :close-on-click-modal="false"
    >
      <el-form :model="rejectForm" :rules="rejectRules" ref="rejectFormRef" label-width="80px">
        <el-form-item label="驳回原因" prop="reason">
          <el-input
            v-model="rejectForm.reason"
            type="textarea"
            :rows="4"
            placeholder="请输入驳回原因"
            maxlength="200"
            show-word-limit
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="showRejectDialog = false">取消</el-button>
          <el-button type="primary" @click="confirmReject" :loading="rejecting">确定</el-button>
        </span>
      </template>
    </el-dialog>

    <!-- 批量添加对话框 -->
    <el-dialog
      v-model="showBatchEnrollDialog"
      title="批量添加参与者"
      width="600px"
      :close-on-click-modal="false"
    >
      <el-form :model="batchForm" :rules="batchRules" ref="batchFormRef" label-width="100px">
        <el-form-item label="选择学生" prop="userIds">
          <el-select
            v-model="batchForm.userIds"
            multiple
            filterable
            remote
            :remote-method="searchUsers"
            :loading="searchLoading"
            placeholder="输入学号或姓名搜索"
            style="width: 100%"
          >
            <el-option
              v-for="user in userOptions"
              :key="user.id"
              :label="`${user.studentId} - ${user.username}`"
              :value="user.id"
            />
          </el-select>
        </el-form-item>
        <el-alert
          title="提示"
          type="info"
          :closable="false"
          show-icon
        >
          批量添加的学生将直接通过审核，无需手动批准
        </el-alert>
      </el-form>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="showBatchEnrollDialog = false">取消</el-button>
          <el-button type="primary" @click="handleBatchEnroll" :loading="batchEnrolling">
            确定添加
          </el-button>
        </span>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '@/utils/request'
import {
  getPendingEnrollments,
  getEnrolledList,
  getRejectedList,
  approveEnrollment,
  rejectEnrollment,
  batchEnrollUsers
} from '@/api/campaign'

const router = useRouter()
const route = useRoute()
const campaignId = route.params.id

const loading = ref(false)
const rejecting = ref(false)
const batchEnrolling = ref(false)
const searchLoading = ref(false)
const activeTab = ref('pending')
const showRejectDialog = ref(false)
const showBatchEnrollDialog = ref(false)

const pendingList = ref([])
const enrolledList = ref([])
const rejectedList = ref([])
const userOptions = ref([])

const rejectFormRef = ref(null)
const batchFormRef = ref(null)
const currentRejectRow = ref(null)

const rejectForm = reactive({
  reason: ''
})

const rejectRules = {
  reason: [{ required: true, message: '请输入驳回原因', trigger: 'blur' }]
}

const batchForm = reactive({
  userIds: []
})

const batchRules = {
  userIds: [{ required: true, message: '请选择学生', trigger: 'change' }]
}

const loadPendingEnrollments = async () => {
  loading.value = true
  try {
    const res = await getPendingEnrollments(campaignId)
    if (res.code === 200) {
      pendingList.value = res.data || []
    } else {
      ElMessage.error(res.message || '加载失败')
    }
  } catch (error) {
    ElMessage.error('加载失败: ' + error.message)
  } finally {
    loading.value = false
  }
}

const loadEnrolledList = async () => {
  loading.value = true
  try {
    const res = await getEnrolledList(campaignId)
    enrolledList.value = res.data || []
  } catch (error) {
    ElMessage.error('加载已通过列表失败: ' + error.message)
  } finally {
    loading.value = false
  }
}

const loadRejectedList = async () => {
  loading.value = true
  try {
    const res = await getRejectedList(campaignId)
    rejectedList.value = res.data || []
  } catch (error) {
    ElMessage.error('加载已驳回列表失败: ' + error.message)
  } finally {
    loading.value = false
  }
}

const handleTabChange = (tab) => {
  if (tab === 'pending') {
    loadPendingEnrollments()
  } else if (tab === 'enrolled') {
    loadEnrolledList()
  } else if (tab === 'rejected') {
    loadRejectedList()
  }
}

const handleApprove = async (row) => {
  try {
    await ElMessageBox.confirm('确认批准该学生的报名申请？', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })

    const res = await approveEnrollment(campaignId, row.id)
    if (res.code === 200) {
      ElMessage.success('已批准')
      loadPendingEnrollments()
    } else {
      ElMessage.error(res.message || '操作失败')
    }
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('操作失败: ' + error.message)
    }
  }
}

const handleReject = (row) => {
  currentRejectRow.value = row
  rejectForm.reason = ''
  showRejectDialog.value = true
}

const confirmReject = async () => {
  try {
    await rejectFormRef.value.validate()

    rejecting.value = true
    const res = await rejectEnrollment(
      campaignId,
      currentRejectRow.value.id,
      rejectForm.reason
    )

    if (res.code === 200) {
      ElMessage.success('已驳回')
      showRejectDialog.value = false
      loadPendingEnrollments()
    } else {
      ElMessage.error(res.message || '操作失败')
    }
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('操作失败: ' + error.message)
    }
  } finally {
    rejecting.value = false
  }
}

const searchUsers = async (query) => {
  if (!query || query.trim().length < 1) {
    userOptions.value = []
    return
  }

  searchLoading.value = true
  try {
    const res = await request.get('/api/auth/search', {
      params: { keyword: query.trim() }
    })
    if (res.code === 200) {
      userOptions.value = (res.data || []).map(u => ({
        id: u.id,
        studentId: u.username,
        username: u.realName || u.username
      }))
    } else {
      userOptions.value = []
    }
  } catch (error) {
    ElMessage.error('搜索用户失败: ' + error.message)
    userOptions.value = []
  } finally {
    searchLoading.value = false
  }
}

const handleBatchEnroll = async () => {
  try {
    await batchFormRef.value.validate()

    batchEnrolling.value = true
    const res = await batchEnrollUsers(campaignId, batchForm.userIds)

    if (res.code === 200) {
      ElMessage.success('批量添加成功')
      showBatchEnrollDialog.value = false
      batchForm.userIds = []
      loadPendingEnrollments()
    } else {
      ElMessage.error(res.message || '操作失败')
    }
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('操作失败: ' + error.message)
    }
  } finally {
    batchEnrolling.value = false
  }
}

const handleBack = () => {
  router.push(`/admin/campaigns/${campaignId}`)
}

const getSourceType = (source) => {
  const types = {
    MANUAL: 'primary',
    AUTO: 'success',
    ADMIN: 'warning'
  }
  return types[source] || 'info'
}

const getSourceLabel = (source) => {
  const labels = {
    MANUAL: '自主报名',
    AUTO: '自动报名',
    ADMIN: '管理员添加'
  }
  return labels[source] || source
}

const getCompletionStatusType = (status) => {
  const types = {
    NOT_STARTED: 'info',
    IN_PROGRESS: 'warning',
    COMPLETED: 'success'
  }
  return types[status] || 'info'
}

const getCompletionStatusLabel = (status) => {
  const labels = {
    NOT_STARTED: '未开始',
    IN_PROGRESS: '进行中',
    COMPLETED: '已完成'
  }
  return labels[status] || status
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
  loadPendingEnrollments()
})
</script>

<style scoped>
.enrollment-management-container {
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
</style>
