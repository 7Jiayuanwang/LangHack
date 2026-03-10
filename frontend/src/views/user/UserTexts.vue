<template>
  <div class="user-texts-container">
    <!-- 页面头部 -->
    <div class="page-header">
      <h1>我的上传内容</h1>
      <el-button type="primary" @click="showUploadDialog = true">
        <el-icon><Upload /></el-icon>
        上传课文
      </el-button>
    </div>

    <!-- 筛选和搜索 -->
    <el-card class="filter-card">
      <el-row :gutter="20">
        <el-col :span="6">
          <el-input
            v-model="searchKeyword"
            placeholder="搜索课文标题或内容"
            clearable
            @keyup.enter="loadMyUploads"
          >
            <template #prefix>
              <el-icon><Search /></el-icon>
            </template>
          </el-input>
        </el-col>
        <el-col :span="4">
          <el-select
            v-model="selectedDifficulty"
            placeholder="选择难度"
            clearable
            @change="loadMyUploads"
          >
            <el-option label="简单" value="EASY" />
            <el-option label="中等" value="MEDIUM" />
            <el-option label="困难" value="HARD" />
          </el-select>
        </el-col>
        <el-col :span="4">
          <el-button @click="loadMyUploads">
            <el-icon><Refresh /></el-icon>
            刷新
          </el-button>
        </el-col>
      </el-row>
    </el-card>

    <!-- 课文列表 -->
    <el-card class="content-card">
      <div v-if="loading" class="loading-container">
        <el-skeleton :rows="3" animated />
      </div>

      <div v-else-if="myTexts.length === 0" class="empty-container">
        <el-empty description="暂无上传内容" />
        <el-button type="primary" @click="showUploadDialog = true" style="margin-top: 20px">
          立即上传
        </el-button>
      </div>

      <div v-else class="text-grid">
        <div
          v-for="text in myTexts"
          :key="text.id"
          class="text-item"
          @click="viewText(text)"
        >
          <div class="text-header">
            <h3 class="text-title">{{ text.title }}</h3>
            <div class="text-meta">
              <el-tag :type="getDifficultyType(text.difficulty)" size="small">
                {{ getDifficultyText(text.difficulty) }}
              </el-tag>
              <el-tag v-if="text.tags" size="small" type="info">
                {{ text.tags }}
              </el-tag>
            </div>
          </div>

          <div class="text-content">
            <p>{{ getContentPreview(text.content) }}</p>
          </div>

          <div class="text-footer">
            <div class="text-stats">
              <span><el-icon><View /></el-icon> {{ text.viewCount || 0 }}</span>
            </div>

            <div class="text-actions">
              <el-button size="small" @click.stop="editText(text)">
                <el-icon><Edit /></el-icon>
                编辑
              </el-button>
              <el-button size="small" type="danger" @click.stop="deleteText(text)">
                <el-icon><Delete /></el-icon>
                删除
              </el-button>
            </div>
          </div>

          <div class="text-time">
            {{ formatDate(text.createdAt) }}
          </div>
        </div>
      </div>
    </el-card>

    <!-- 上传对话框 -->
    <el-dialog
      v-model="showUploadDialog"
      title="上传课文"
      width="600px"
      @close="resetUploadForm"
    >
      <el-form
        ref="uploadFormRef"
        :model="uploadForm"
        :rules="uploadRules"
        label-width="80px"
      >
        <el-form-item label="标题" prop="title">
          <el-input
            v-model="uploadForm.title"
            placeholder="请输入课文标题"
            maxlength="200"
            show-word-limit
          />
        </el-form-item>

        <el-form-item label="内容" prop="content">
          <el-input
            v-model="uploadForm.content"
            type="textarea"
            placeholder="请输入课文内容"
            :rows="8"
            maxlength="22000"
            show-word-limit
          />
        </el-form-item>

        <el-form-item label="难度" prop="difficulty">
          <el-select v-model="uploadForm.difficulty" placeholder="选择难度">
            <el-option label="简单" value="EASY" />
            <el-option label="中等" value="MEDIUM" />
            <el-option label="困难" value="HARD" />
          </el-select>
        </el-form-item>

        <el-form-item label="标签" prop="tags">
          <el-input
            v-model="uploadForm.tags"
            placeholder="请输入标签，多个用逗号分隔"
            maxlength="100"
          />
          <div class="form-tip">
            例如：日常对话, 商务英语, 科技文章
          </div>
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="showUploadDialog = false">取消</el-button>
        <el-button
          type="primary"
          :loading="uploadLoading"
          @click="handleUpload"
        >
          确认上传
        </el-button>
      </template>
    </el-dialog>

    <!-- 编辑对话框 -->
    <el-dialog
      v-model="showEditDialog"
      title="编辑课文"
      width="600px"
    >
      <el-form
        ref="editFormRef"
        :model="editForm"
        :rules="uploadRules"
        label-width="80px"
      >
        <el-form-item label="标题" prop="title">
          <el-input
            v-model="editForm.title"
            placeholder="请输入课文标题"
            maxlength="200"
            show-word-limit
          />
        </el-form-item>

        <el-form-item label="内容" prop="content">
          <el-input
            v-model="editForm.content"
            type="textarea"
            placeholder="请输入课文内容"
            :rows="8"
            maxlength="22000"
            show-word-limit
          />
        </el-form-item>

        <el-form-item label="难度" prop="difficulty">
          <el-select v-model="editForm.difficulty" placeholder="选择难度">
            <el-option label="简单" value="EASY" />
            <el-option label="中等" value="MEDIUM" />
            <el-option label="困难" value="HARD" />
          </el-select>
        </el-form-item>

        <el-form-item label="标签" prop="tags">
          <el-input
            v-model="editForm.tags"
            placeholder="请输入标签，多个用逗号分隔"
            maxlength="100"
          />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="showEditDialog = false">取消</el-button>
        <el-button
          type="primary"
          :loading="editLoading"
          @click="handleEdit"
        >
          确认修改
        </el-button>
      </template>
    </el-dialog>

    <!-- 课文详情对话框 -->
    <el-dialog
      v-model="showDetailDialog"
      :title="currentText.title"
      width="800px"
    >
      <div class="text-detail">
        <div class="detail-header">
          <div class="detail-meta">
            <el-tag :type="getDifficultyType(currentText.difficulty)">
              {{ getDifficultyText(currentText.difficulty) }}
            </el-tag>
            <el-tag v-if="currentText.tags" type="info">
              {{ currentText.tags }}
            </el-tag>
          </div>

          <div class="detail-stats">
            <span><el-icon><View /></el-icon> 浏览：{{ currentText.viewCount || 0 }}</span>
            <span><el-icon><Calendar /></el-icon> {{ formatDate(currentText.createdAt) }}</span>
          </div>
        </div>

        <div class="detail-content">
          <div class="content-text" v-html="formatContent(currentText.content)"></div>
        </div>

        <div class="detail-actions">
          <el-button type="primary" @click="studyText(currentText)">
            <el-icon><Reading /></el-icon>
            学习课文
          </el-button>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '@/utils/request'
import DOMPurify from 'dompurify'

const router = useRouter()

// 响应式数据
const loading = ref(false)
const uploadLoading = ref(false)
const editLoading = ref(false)
const showUploadDialog = ref(false)
const showEditDialog = ref(false)
const showDetailDialog = ref(false)
const searchKeyword = ref('')
const selectedDifficulty = ref('')
const myTexts = ref([])
const currentText = ref({})

// 表单数据
const uploadFormRef = ref()
const editFormRef = ref()
const uploadForm = reactive({
  title: '',
  content: '',
  difficulty: 'MEDIUM',
  tags: ''
})

const editForm = reactive({
  id: null,
  title: '',
  content: '',
  difficulty: 'MEDIUM',
  tags: ''
})

// 表单验证规则
const uploadRules = {
  title: [
    { required: true, message: '请输入课文标题', trigger: 'blur' },
    { min: 1, max: 200, message: '标题长度应在1-200字符之间', trigger: 'blur' }
  ],
  content: [
    { required: true, message: '请输入课文内容', trigger: 'blur' },
    { min: 10, max: 22000, message: '内容长度应在10-22000字符之间', trigger: 'blur' }
  ],
  difficulty: [
    { required: true, message: '请选择难度', trigger: 'change' }
  ]
}

// 方法
const loadMyUploads = async () => {
  loading.value = true
  try {
    const response = await request({
      url: '/api/user/texts/my-uploads',
      method: 'get'
    })

    if (response && response.code === 200) {
      myTexts.value = response.data || []
    }
  } catch (error) {
    console.error('获取上传内容错误:', error)
    ElMessage.error('获取上传内容失败，请稍后重试')
  } finally {
    loading.value = false
  }
}

const handleUpload = async () => {
  if (!uploadFormRef.value) return

  await uploadFormRef.value.validate(async (valid) => {
    if (valid) {
      uploadLoading.value = true
      try {
        const response = await request({
          url: '/api/user/texts/upload',
          method: 'post',
          data: uploadForm
        })

        if (response && response.code === 200) {
          ElMessage.success('上传成功！')
          showUploadDialog.value = false
          resetUploadForm()
          loadMyUploads()
        } else {
          ElMessage.error(response?.message || '上传失败')
        }
      } catch (error) {
        console.error('上传错误:', error)
        ElMessage.error('上传失败，请重试')
      } finally {
        uploadLoading.value = false
      }
    }
  })
}

const resetUploadForm = () => {
  Object.assign(uploadForm, {
    title: '',
    content: '',
    difficulty: 'MEDIUM',
    tags: ''
  })
  if (uploadFormRef.value) {
    uploadFormRef.value.clearValidate()
  }
}

const editText = (text) => {
  Object.assign(editForm, {
    id: text.id,
    title: text.title,
    content: text.content,
    difficulty: text.difficulty,
    tags: text.tags || ''
  })
  showEditDialog.value = true
}

const handleEdit = async () => {
  if (!editFormRef.value) return

  await editFormRef.value.validate(async (valid) => {
    if (valid) {
      editLoading.value = true
      try {
        const response = await request({
          url: `/api/user/texts/${editForm.id}`,
          method: 'put',
          data: editForm
        })

        if (response && response.code === 200) {
          ElMessage.success('修改成功！')
          showEditDialog.value = false
          loadMyUploads()
        } else {
          ElMessage.error(response?.message || '修改失败')
        }
      } catch (error) {
        console.error('修改错误:', error)
        ElMessage.error('修改失败，请重试')
      } finally {
        editLoading.value = false
      }
    }
  })
}

const deleteText = (text) => {
  ElMessageBox.confirm(
    `确定要删除《${text.title}》吗？`,
    '确认删除',
    {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning',
    }
  ).then(async () => {
    try {
      const response = await request({
        url: `/api/user/texts/${text.id}`,
        method: 'delete'
      })

      if (response && response.code === 200) {
        ElMessage.success('删除成功')
        loadMyUploads()
      } else {
        ElMessage.error(response?.message || '删除失败')
      }
    } catch (error) {
      console.error('删除错误:', error)
      ElMessage.error('删除失败，请重试')
    }
  })
}

const viewText = (text) => {
  currentText.value = text
  showDetailDialog.value = true
}

const studyText = (text) => {
  // 跳转到课文学习页面
  if (!text?.id) {
    ElMessage.error('文本信息不完整')
    return
  }
  router.push({
    path: '/user/text-learning',
    query: {
      textId: text.id
    }
  })
}

// 工具方法
const getContentPreview = (content) => {
  if (!content) return ''
  return content.length > 100 ? content.substring(0, 100) + '...' : content
}

const getDifficultyType = (difficulty) => {
  const types = {
    'EASY': 'success',
    'MEDIUM': 'warning',
    'HARD': 'danger'
  }
  return types[difficulty] || 'info'
}

const getDifficultyText = (difficulty) => {
  const texts = {
    'EASY': '简单',
    'MEDIUM': '中等',
    'HARD': '困难'
  }
  return texts[difficulty] || difficulty
}

const formatDate = (dateString) => {
  if (!dateString) return ''
  return new Date(dateString).toLocaleString()
}

const formatContent = (content) => {
  if (!content || typeof content !== 'string') return ''
  return DOMPurify.sanitize(content.replace(/\n/g, '<br>'))
}

// 生命周期
onMounted(() => {
  loadMyUploads()
})
</script>

<style scoped>
.user-texts-container {
  padding: 20px;
  background-color: #f5f5f5;
  min-height: 100vh;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.page-header h1 {
  margin: 0;
  color: #333;
  font-size: 24px;
}

.filter-card {
  margin-bottom: 20px;
}

.content-card {
  background: white;
}

.loading-container {
  padding: 20px;
}

.empty-container {
  padding: 40px;
  text-align: center;
}

.text-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  gap: 20px;
}

.text-item {
  border: 1px solid #e0e0e0;
  border-radius: 8px;
  padding: 16px;
  cursor: pointer;
  transition: all 0.3s;
}

.text-item:hover {
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
  transform: translateY(-2px);
}

.text-header {
  margin-bottom: 12px;
}

.text-title {
  margin: 0 0 8px 0;
  font-size: 16px;
  font-weight: 600;
  color: #333;
  line-height: 1.4;
}

.text-meta {
  display: flex;
  gap: 8px;
  align-items: center;
  margin-bottom: 8px;
}

.text-content {
  margin-bottom: 12px;
}

.text-content p {
  margin: 0;
  color: #666;
  line-height: 1.5;
  font-size: 14px;
}

.text-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-top: 12px;
  border-top: 1px solid #f0f0f0;
}

.text-stats {
  display: flex;
  gap: 16px;
  font-size: 12px;
  color: #999;
}

.text-stats span {
  display: flex;
  align-items: center;
  gap: 4px;
}

.text-actions {
  display: flex;
  gap: 8px;
}

.text-time {
  font-size: 12px;
  color: #999;
}

.form-tip {
  font-size: 12px;
  color: #999;
  margin-top: 4px;
}

.text-detail {
  max-height: 70vh;
  overflow-y: auto;
}

.detail-header {
  padding-bottom: 16px;
  border-bottom: 1px solid #f0f0f0;
}

.detail-meta {
  display: flex;
  gap: 12px;
  margin-bottom: 12px;
  flex-wrap: wrap;
}

.detail-stats {
  display: flex;
  gap: 16px;
  font-size: 14px;
  color: #666;
}

.detail-stats span {
  display: flex;
  align-items: center;
  gap: 4px;
}

.detail-content {
  padding: 20px 0;
}

.content-text {
  line-height: 1.8;
  font-size: 15px;
  color: #333;
  white-space: pre-wrap;
}

.detail-actions {
  display: flex;
  gap: 12px;
  padding-top: 16px;
  border-top: 1px solid #f0f0f0;
}

/* 响应式设计 */
@media (max-width: 768px) {
  .text-grid {
    grid-template-columns: 1fr;
  }

  .page-header {
    flex-direction: column;
    gap: 16px;
    align-items: stretch;
  }
}
</style>