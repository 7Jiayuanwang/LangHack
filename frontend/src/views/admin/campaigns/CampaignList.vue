<template>
  <div class="campaign-list-container">
    <div class="header">
      <h2>活动管理</h2>
      <el-button type="primary" @click="handleCreate">创建活动</el-button>
    </div>

    <el-card>
      <el-form :inline="true" :model="queryParams" class="query-form">
        <el-form-item label="活动状态">
          <el-select v-model="queryParams.status" placeholder="全部" clearable @change="handleQuery">
            <el-option label="草稿" value="DRAFT" />
            <el-option label="进行中" value="ACTIVE" />
            <el-option label="已结束" value="ENDED" />
            <el-option label="已归档" value="ARCHIVED" />
          </el-select>
        </el-form-item>
        <el-form-item label="关键词">
          <el-input v-model="queryParams.keyword" placeholder="活动标题" clearable @keyup.enter="handleQuery" />
        </el-form-item>
        <el-form-item label="排序方式">
          <el-select v-model="queryParams.orderBy" placeholder="默认排序" clearable @change="handleQuery" style="width: 160px;">
            <el-option label="创建时间（新→旧）" value="created_time_desc" />
            <el-option label="创建时间（旧→新）" value="created_time_asc" />
            <el-option label="活动时间（近→远）" value="start_time_desc" />
            <el-option label="活动时间（远→近）" value="start_time_asc" />
            <el-option label="更新时间（新→旧）" value="updated_time_desc" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleQuery">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>

      <el-table :data="campaigns" v-loading="loading" stripe>
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="title" label="活动标题" min-width="200" />
        <el-table-column label="活动时间" min-width="200">
          <template #default="{ row }">
            {{ formatDate(row.startTime) }} ~ {{ formatDate(row.endTime) }}
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="getStatusType(row.status)">{{ getStatusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="报名模式" width="120">
          <template #default="{ row }">
            {{ getEnrollmentModeLabel(row.enrollmentMode) }}
          </template>
        </el-table-column>
        <el-table-column prop="createdTime" label="创建时间" width="180">
          <template #default="{ row }">
            {{ formatDate(row.createdTime) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="300" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="handleView(row)">详情</el-button>
            <el-button link type="primary" @click="handleEdit(row)" v-if="row.status === 'DRAFT'">编辑</el-button>
            <el-button link type="success" @click="handleStart(row)" v-if="row.status === 'DRAFT'">启动</el-button>
            <el-button link type="warning" @click="handleEnd(row)" v-if="row.status === 'ACTIVE'">结束</el-button>
            <el-button link type="danger" @click="handleDelete(row)" v-if="row.status === 'DRAFT'">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="pagination.current"
        v-model:page-size="pagination.size"
        :total="pagination.total"
        :page-sizes="[10, 20, 50, 100]"
        layout="total, sizes, prev, pager, next, jumper"
        @size-change="handleQuery"
        @current-change="handleQuery"
        class="pagination"
      />
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getCampaigns, deleteCampaign, startCampaign, endCampaign } from '@/api/campaign'

const router = useRouter()
const loading = ref(false)
const campaigns = ref([])

const queryParams = reactive({
  status: '',
  keyword: '',
  orderBy: 'created_time_desc'
})

const pagination = reactive({
  current: 1,
  size: 10,
  total: 0
})

const loadData = async () => {
  loading.value = true
  try {
    const params = {
      current: pagination.current,
      size: pagination.size,
      ...queryParams
    }
    const res = await getCampaigns(params)
    if (res.code === 200) {
      campaigns.value = res.data.records || []
      pagination.total = res.data.total || 0
    } else {
      ElMessage.error(res.message || '加载失败')
    }
  } catch (error) {
    ElMessage.error('加载失败: ' + error.message)
  } finally {
    loading.value = false
  }
}

const handleQuery = () => {
  pagination.current = 1
  loadData()
}

const handleReset = () => {
  queryParams.status = ''
  queryParams.keyword = ''
  queryParams.orderBy = 'created_time_desc'
  handleQuery()
}

const handleCreate = () => {
  router.push('/admin/campaigns/create')
}

const handleView = (row) => {
  router.push(`/admin/campaigns/${row.id}`)
}

const handleEdit = (row) => {
  router.push(`/admin/campaigns/${row.id}/edit`)
}

const handleStart = async (row) => {
  try {
    await ElMessageBox.confirm('确认启动该活动？启动后将开始接受报名和数据采集。', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    const res = await startCampaign(row.id)
    if (res.code === 200) {
      ElMessage.success('启动成功')
      loadData()
    } else {
      ElMessage.error(res.message || '启动失败')
    }
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('启动失败: ' + error.message)
    }
  }
}

const handleEnd = async (row) => {
  try {
    await ElMessageBox.confirm('确认结束该活动？结束后将停止报名和数据采集。', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    const res = await endCampaign(row.id)
    if (res.code === 200) {
      ElMessage.success('结束成功')
      loadData()
    } else {
      ElMessage.error(res.message || '结束失败')
    }
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('结束失败: ' + error.message)
    }
  }
}

const handleDelete = async (row) => {
  try {
    await ElMessageBox.confirm('确认删除该活动？删除后无法恢复。', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    const res = await deleteCampaign(row.id)
    if (res.code === 200) {
      ElMessage.success('删除成功')
      loadData()
    } else {
      ElMessage.error(res.message || '删除失败')
    }
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('删除失败: ' + error.message)
    }
  }
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

const getEnrollmentModeLabel = (mode) => {
  const labels = {
    AUTO: '自动报名',
    MANUAL: '自主报名',
    INVITE_ONLY: '仅邀请'
  }
  return labels[mode] || mode
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
.campaign-list-container {
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

.query-form {
  margin-bottom: 20px;
}

.pagination {
  margin-top: 20px;
  display: flex;
  justify-content: flex-end;
}
</style>
