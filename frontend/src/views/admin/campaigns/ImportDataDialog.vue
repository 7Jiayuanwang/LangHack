<template>
  <el-dialog
    v-model="visible"
    title="批量导入数据"
    width="600px"
    :close-on-click-modal="false"
    @close="handleClose"
  >
    <el-form :model="formData" :rules="rules" ref="formRef" label-width="100px">
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

      <el-form-item label="上传文件" prop="file">
        <el-upload
          ref="uploadRef"
          :auto-upload="false"
          :limit="1"
          :on-change="handleFileChange"
          :on-exceed="handleExceed"
          accept=".xlsx,.xls"
          drag
        >
          <el-icon class="el-icon--upload"><upload-filled /></el-icon>
          <div class="el-upload__text">
            将文件拖到此处，或<em>点击上传</em>
          </div>
          <template #tip>
            <div class="el-upload__tip">
              只能上传 xlsx/xls 文件，且不超过 10MB
            </div>
          </template>
        </el-upload>
      </el-form-item>

      <el-alert
        title="导入说明"
        type="info"
        :closable="false"
        style="margin-bottom: 20px"
      >
        <p>1. Excel 文件应包含以下列：学号、姓名、数值、备注（可选）</p>
        <p>2. 学号必须存在于系统中，否则该行数据将被忽略</p>
        <p>3. 数值必须为有效数字</p>
        <p>4. 导入前请确认数据格式正确</p>
      </el-alert>

      <el-form-item>
        <el-button type="primary" link @click="handleDownloadTemplate">
          下载导入模板
        </el-button>
      </el-form-item>
    </el-form>

    <!-- 导入结果 -->
    <el-card v-if="importResult" class="result-card">
      <template #header>
        <span>导入结果</span>
      </template>
      <el-descriptions :column="2" border>
        <el-descriptions-item label="总行数">
          {{ importResult.totalRows }}
        </el-descriptions-item>
        <el-descriptions-item label="成功行数">
          <el-tag type="success">{{ importResult.successRows }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="失败行数">
          <el-tag type="danger">{{ importResult.failedRows }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="跳过行数">
          <el-tag type="warning">{{ importResult.skippedRows }}</el-tag>
        </el-descriptions-item>
      </el-descriptions>

      <div v-if="importResult.errors && importResult.errors.length > 0" style="margin-top: 20px">
        <el-divider content-position="left">错误详情</el-divider>
        <el-table :data="importResult.errors" max-height="300" border>
          <el-table-column prop="row" label="行号" width="80" />
          <el-table-column prop="studentId" label="学号" width="120" />
          <el-table-column prop="error" label="错误原因" min-width="200" />
        </el-table>
      </div>
    </el-card>

    <template #footer>
      <span class="dialog-footer">
        <el-button @click="handleClose">取消</el-button>
        <el-button type="primary" @click="handleImport" :loading="importing">
          {{ importResult ? '重新导入' : '开始导入' }}
        </el-button>
      </span>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, reactive, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { UploadFilled } from '@element-plus/icons-vue'
import { importData } from '@/api/campaign'

const props = defineProps({
  modelValue: {
    type: Boolean,
    default: false
  },
  campaignId: {
    type: [String, Number],
    required: true
  },
  tasks: {
    type: Array,
    default: () => []
  }
})

const emit = defineEmits(['update:modelValue', 'success'])

const visible = ref(false)
const formRef = ref(null)
const uploadRef = ref(null)
const importing = ref(false)
const importResult = ref(null)

const formData = reactive({
  taskId: null,
  file: null
})

const rules = {
  taskId: [{ required: true, message: '请选择任务', trigger: 'change' }]
}

watch(() => props.modelValue, (val) => {
  visible.value = val
})

watch(visible, (val) => {
  emit('update:modelValue', val)
  if (!val) {
    handleClose()
  }
})

const handleFileChange = (file) => {
  // 验证文件大小
  const isLt10M = file.size / 1024 / 1024 < 10
  if (!isLt10M) {
    ElMessage.error('上传文件大小不能超过 10MB!')
    uploadRef.value.clearFiles()
    return
  }
  formData.file = file.raw
}

const handleExceed = () => {
  ElMessage.warning('只能上传一个文件，请删除后重新上传')
}

const handleDownloadTemplate = () => {
  // 创建模板数据
  const template = [
    ['学号', '姓名', '数值', '备注'],
    ['2021001', '张三', '60', '示例数据'],
    ['2021002', '李四', '50', '']
  ]

  // 转换为CSV格式
  const csvContent = template.map(row => row.join(',')).join('\n')
  const blob = new Blob(['﻿' + csvContent], { type: 'text/csv;charset=utf-8;' })
  const url = window.URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = '数据导入模板.csv'
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  window.URL.revokeObjectURL(url)
}

const handleImport = async () => {
  try {
    await formRef.value.validate()

    if (!formData.file) {
      ElMessage.warning('请选择要上传的文件')
      return
    }

    importing.value = true
    importResult.value = null

    const res = await importData(props.campaignId, formData.taskId, formData.file)

    if (res.code === 200) {
      importResult.value = res.data
      if (res.data.failedRows === 0) {
        ElMessage.success(`导入成功！共导入 ${res.data.successRows} 条数据`)
        emit('success')
      } else {
        ElMessage.warning(`导入完成，成功 ${res.data.successRows} 条，失败 ${res.data.failedRows} 条`)
      }
    } else {
      ElMessage.error(res.message || '导入失败')
    }
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('导入失败: ' + error.message)
    }
  } finally {
    importing.value = false
  }
}

const handleClose = () => {
  formRef.value?.resetFields()
  uploadRef.value?.clearFiles()
  formData.file = null
  importResult.value = null
  visible.value = false
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
</script>

<style scoped>
.result-card {
  margin-top: 20px;
}

.el-upload__tip p {
  margin: 5px 0;
  font-size: 12px;
  line-height: 1.5;
}

:deep(.el-upload-dragger) {
  padding: 30px;
}
</style>
