<template>
  <div class="manual-entry-container">
    <div class="header">
      <h2>手动录入数据</h2>
      <div class="actions">
        <el-button @click="handleBack">返回</el-button>
        <el-button type="primary" @click="showImportDialog = true">批量导入</el-button>
      </div>
    </div>

    <el-card>
      <el-form :model="formData" :rules="rules" ref="formRef" label-width="120px">
        <el-form-item label="选择任务" prop="taskId">
          <el-select v-model="formData.taskId" placeholder="请选择任务" style="width: 100%">
            <el-option
              v-for="task in tasks"
              :key="task.id"
              :label="`${task.taskName} (${getTaskTypeLabel(task.taskType)})`"
              :value="task.id"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="选择学生" prop="userId">
          <el-select
            v-model="formData.userId"
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

        <el-form-item label="数值" prop="value">
          <el-input-number
            v-model="formData.value"
            :min="0"
            :precision="2"
            placeholder="请输入数值"
            style="width: 100%"
          />
        </el-form-item>

        <el-form-item label="备注">
          <el-input
            v-model="formData.remark"
            type="textarea"
            :rows="3"
            placeholder="可选，记录数据来源或说明"
            maxlength="500"
            show-word-limit
          />
        </el-form-item>

        <el-form-item>
          <el-button type="primary" @click="handleSubmit" :loading="submitting">提交</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card style="margin-top: 20px">
      <template #header>
        <span>最近录入记录</span>
      </template>
      <el-table :data="recentRecords" v-loading="loading" stripe>
        <el-table-column label="录入时间" width="180">
          <template #default="{ row }">
            {{ formatDate(row.createdTime) }}
          </template>
        </el-table-column>
        <el-table-column prop="taskName" label="任务" min-width="150" />
        <el-table-column prop="userName" label="学生" width="120" />
        <el-table-column prop="recordValue" label="数值" width="100" />
        <el-table-column prop="recordType" label="类型" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="row.recordType === 'MANUAL' ? 'primary' : 'success'">
              {{ row.recordType === 'MANUAL' ? '手动' : '导入' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="200" show-overflow-tooltip />
        <el-table-column prop="createdByName" label="录入人" width="100" />
      </el-table>
    </el-card>

    <!-- 批量导入对话框 -->
    <import-data-dialog
      v-model="showImportDialog"
      :campaign-id="campaignId"
      :tasks="tasks"
      @success="handleImportSuccess"
    />
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getTasks, recordManualData } from '@/api/campaign'
import ImportDataDialog from './ImportDataDialog.vue'

const router = useRouter()
const route = useRoute()
const campaignId = route.params.id
const formRef = ref(null)
const loading = ref(false)
const submitting = ref(false)
const searchLoading = ref(false)
const showImportDialog = ref(false)

const tasks = ref([])
const userOptions = ref([])
const recentRecords = ref([])

const formData = reactive({
  taskId: null,
  userId: null,
  value: 0,
  remark: ''
})

const rules = {
  taskId: [{ required: true, message: '请选择任务', trigger: 'change' }],
  userId: [{ required: true, message: '请选择学生', trigger: 'change' }],
  value: [{ required: true, message: '请输入数值', trigger: 'blur' }]
}

const loadTasks = async () => {
  try {
    const res = await getTasks(campaignId)
    if (res.code === 200) {
      tasks.value = res.data || []
    }
  } catch (error) {
    ElMessage.error('加载任务失败: ' + error.message)
  }
}

const searchUsers = async (query) => {
  if (!query) {
    userOptions.value = []
    return
  }

  searchLoading.value = true
  try {
    // TODO: 实现用户搜索API
    // const res = await searchUsers({ keyword: query })
    // if (res.code === 200) {
    //   userOptions.value = res.data || []
    // }

    // 模拟数据
    userOptions.value = [
      { id: 1, studentId: '2021001', username: '张三' },
      { id: 2, studentId: '2021002', username: '李四' }
    ]
  } catch (error) {
    ElMessage.error('搜索用户失败: ' + error.message)
  } finally {
    searchLoading.value = false
  }
}

const loadRecentRecords = async () => {
  loading.value = true
  try {
    // TODO: 实现获取最近录入记录API
    // const res = await getRecentRecords(campaignId)
    // if (res.code === 200) {
    //   recentRecords.value = res.data || []
    // }

    // 模拟数据
    recentRecords.value = []
  } catch (error) {
    console.error('加载记录失败:', error)
  } finally {
    loading.value = false
  }
}

const handleSubmit = async () => {
  try {
    await formRef.value.validate()

    submitting.value = true
    const res = await recordManualData(campaignId, {
      taskId: formData.taskId,
      userId: formData.userId,
      value: formData.value,
      remark: formData.remark
    })

    if (res.code === 200) {
      ElMessage.success('录入成功')
      handleReset()
      loadRecentRecords()
    } else {
      ElMessage.error(res.message || '录入失败')
    }
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('录入失败: ' + error.message)
    }
  } finally {
    submitting.value = false
  }
}

const handleReset = () => {
  formRef.value.resetFields()
  formData.remark = ''
}

const handleBack = () => {
  router.push(`/admin/campaigns/${campaignId}`)
}

const handleImportSuccess = () => {
  loadRecentRecords()
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
  loadTasks()
  loadRecentRecords()
})
</script>

<style scoped>
.manual-entry-container {
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
