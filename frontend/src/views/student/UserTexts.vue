<template>
  <div class="user-texts-container">
    <!-- 页面头部 -->
    <div class="page-header">
      <h1>我的上传内容</h1>
      <div class="header-actions">
        <el-button type="success" @click="triggerMdImport">
          <el-icon><Document /></el-icon>
          批量导入 Markdown
        </el-button>
        <el-button type="primary" @click="showUploadDialog = true">
          <el-icon><Upload /></el-icon>
          上传课文
        </el-button>
        <!-- 隐藏的文件选择器 -->
        <input type="file" ref="mdFileInputRef" multiple accept=".md,image/*" style="display: none" @change="handleMdFileChange" />
      </div>
    </div>

    <!-- 筛选和搜索 -->
    <el-card class="filter-card">
      <el-row :gutter="20">
        <el-col :xs="24" :sm="12" :md="6">
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
        <el-col :xs="12" :sm="6" :md="4">
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
        <el-col :xs="12" :sm="6" :md="4">
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
          @click="studyText(text)"
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
            <p>{{ getContentPreview(text.contentType === 'MARKDOWN' ? text.markdownContent : text.content) }}</p>
          </div>

          <div class="text-footer">
            <div class="text-stats">
              <span><el-icon><View /></el-icon> {{ text.viewCount || 0 }}</span>
              <span><el-icon><Star /></el-icon> {{ text.likeCount || 0 }}</span>
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
      width="90%"
      :close-on-click-modal="false"
      class="editor-dialog"
      @close="resetUploadForm"
    >
      <el-form
        ref="uploadFormRef"
        :model="uploadForm"
        :rules="uploadRules"
        label-width="120px"
        class="text-form"
      >
        <!-- 标题 -->
        <el-form-item label="标题" prop="title">
          <el-input v-model="uploadForm.title" placeholder="请输入课文标题" maxlength="200" show-word-limit />
        </el-form-item>

        <!-- Task 3.1: 封面图上传区域 -->
        <el-form-item label="封面图">
          <div class="cover-section">
            <el-upload
              class="cover-uploader"
              :class="{ 'has-cover': uploadForm.coverUrl }"
              action="/api/media/upload?mediaType=IMAGE"
              :headers="uploadHeaders"
              :show-file-list="false"
              :on-success="handleUploadCoverSuccess"
              :before-upload="beforeCoverUpload"
              :on-error="handleCoverError"
              drag
            >
              <div v-if="uploadForm.coverUrl" class="cover-preview-wrap">
                <img :src="uploadForm.coverUrl" class="cover-preview" />
                <div class="cover-overlay">
                  <el-icon :size="20"><Upload /></el-icon>
                  <span>点击更换</span>
                </div>
              </div>
              <div v-else class="cover-placeholder">
                <el-icon class="upload-icon" :size="32"><Picture /></el-icon>
                <div class="upload-text">点击或拖拽上传封面图</div>
                <div class="upload-hint">支持 JPG / PNG / GIF / WebP，不超过 10MB</div>
              </div>
            </el-upload>
            <div v-if="uploadForm.coverUrl" class="cover-actions">
              <el-button type="primary" link @click="previewCover" size="small">
                <el-icon><ZoomIn /></el-icon> 预览大图
              </el-button>
              <el-button type="danger" link @click="removeCover(uploadForm)" size="small">
                <el-icon><Delete /></el-icon> 删除封面
              </el-button>
            </div>
          </div>
        </el-form-item>

        <!-- Task 3.2: 内容格式选择器 -->
        <el-form-item label="内容格式">
          <el-radio-group v-model="uploadForm.contentType">
            <el-radio value="TEXT">纯文本</el-radio>
            <el-radio value="MARKDOWN">Markdown</el-radio>
          </el-radio-group>
        </el-form-item>

        <!-- Task 3.3: 条件内容输入 -->
        <el-form-item v-if="uploadForm.contentType === 'TEXT'" label="内容" prop="content">
          <el-input
            v-model="uploadForm.content"
            type="textarea"
            placeholder="请输入课文内容"
            :rows="8"
            maxlength="22000"
            show-word-limit
          />
        </el-form-item>
        <el-form-item v-if="uploadForm.contentType === 'MARKDOWN'" label="Markdown内容" class="md-editor-item">
          <MarkdownEditor :modelValue="uploadMarkdownContent" @update:modelValue="uploadMarkdownContent = $event" />
        </el-form-item>

        <!-- 难度 -->
        <el-form-item label="难度" prop="difficulty">
          <el-select v-model="uploadForm.difficulty" placeholder="选择难度">
            <el-option label="简单" value="EASY" />
            <el-option label="中等" value="MEDIUM" />
            <el-option label="困难" value="HARD" />
          </el-select>
        </el-form-item>

        <!-- 标签 -->
        <el-form-item label="标签" prop="tags">
          <el-input v-model="uploadForm.tags" placeholder="请输入标签，多个用逗号分隔" maxlength="100" />
          <div class="form-tip">例如：日常对话, 商务英语, 科技文章</div>
        </el-form-item>

        <!-- Task 3.4: 音频 URL -->
        <el-form-item label="音频URL">
          <el-input v-model="uploadForm.audioUrl" placeholder="请输入音频URL（可选）" />
        </el-form-item>

        <!-- Task 3.5: 是否公开 -->
        <el-form-item label="是否公开">
          <el-switch v-model="uploadForm.isPublic" />
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
      width="90%"
      :close-on-click-modal="false"
      class="editor-dialog"
    >
      <el-form
        ref="editFormRef"
        :model="editForm"
        :rules="editRules"
        label-width="120px"
        class="text-form"
      >
        <!-- 标题 -->
        <el-form-item label="标题" prop="title">
          <el-input v-model="editForm.title" placeholder="请输入课文标题" maxlength="200" show-word-limit />
        </el-form-item>

        <!-- 封面图上传区域 -->
        <el-form-item label="封面图">
          <div class="cover-section">
            <el-upload
              class="cover-uploader"
              :class="{ 'has-cover': editForm.coverUrl }"
              action="/api/media/upload?mediaType=IMAGE"
              :headers="uploadHeaders"
              :show-file-list="false"
              :on-success="handleEditCoverSuccess"
              :before-upload="beforeCoverUpload"
              :on-error="handleCoverError"
              drag
            >
              <div v-if="editForm.coverUrl" class="cover-preview-wrap">
                <img :src="editForm.coverUrl" class="cover-preview" />
                <div class="cover-overlay">
                  <el-icon :size="20"><Upload /></el-icon>
                  <span>点击更换</span>
                </div>
              </div>
              <div v-else class="cover-placeholder">
                <el-icon class="upload-icon" :size="32"><Picture /></el-icon>
                <div class="upload-text">点击或拖拽上传封面图</div>
                <div class="upload-hint">支持 JPG / PNG / GIF / WebP，不超过 10MB</div>
              </div>
            </el-upload>
            <div v-if="editForm.coverUrl" class="cover-actions">
              <el-button type="primary" link @click="previewCover" size="small">
                <el-icon><ZoomIn /></el-icon> 预览大图
              </el-button>
              <el-button type="danger" link @click="removeCover(editForm)" size="small">
                <el-icon><Delete /></el-icon> 删除封面
              </el-button>
            </div>
          </div>
        </el-form-item>

        <!-- 内容格式选择器 -->
        <el-form-item label="内容格式">
          <el-radio-group v-model="editForm.contentType">
            <el-radio value="TEXT">纯文本</el-radio>
            <el-radio value="MARKDOWN">Markdown</el-radio>
          </el-radio-group>
        </el-form-item>

        <!-- 条件内容输入 -->
        <el-form-item v-if="editForm.contentType === 'TEXT'" label="内容" prop="content">
          <el-input
            v-model="editForm.content"
            type="textarea"
            placeholder="请输入课文内容"
            :rows="8"
            maxlength="22000"
            show-word-limit
          />
        </el-form-item>
        <el-form-item v-if="editForm.contentType === 'MARKDOWN'" label="Markdown内容" class="md-editor-item">
          <MarkdownEditor :modelValue="editMarkdownContent" @update:modelValue="editMarkdownContent = $event" />
        </el-form-item>

        <!-- 难度 -->
        <el-form-item label="难度" prop="difficulty">
          <el-select v-model="editForm.difficulty" placeholder="选择难度">
            <el-option label="简单" value="EASY" />
            <el-option label="中等" value="MEDIUM" />
            <el-option label="困难" value="HARD" />
          </el-select>
        </el-form-item>

        <!-- 标签 -->
        <el-form-item label="标签" prop="tags">
          <el-input v-model="editForm.tags" placeholder="请输入标签，多个用逗号分隔" maxlength="100" />
        </el-form-item>

        <!-- 音频 URL -->
        <el-form-item label="音频URL">
          <el-input v-model="editForm.audioUrl" placeholder="请输入音频URL（可选）" />
        </el-form-item>

        <!-- 是否公开 -->
        <el-form-item label="是否公开">
          <el-switch v-model="editForm.isPublic" />
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


    <!-- 封面大图预览弹窗 -->
    <el-dialog
      v-model="coverPreviewVisible"
      title="封面预览"
      width="auto"
      :append-to-body="true"
    >
      <div class="preview-image-wrap">
        <img
          :src="uploadForm.coverUrl || editForm.coverUrl"
          alt="封面预览"
          class="preview-full-image"
        />
      </div>
    </el-dialog>

    <!-- 智能导入进度对话框 -->
    <el-dialog
      v-model="importDialogVisible"
      title="智能导入处理中"
      width="500px"
      :close-on-click-modal="false"
      class="import-dialog"
      :show-close="false"
    >
      <div class="import-progress-container">
        <p class="current-file"><strong>正在处理:</strong> {{ currentImportFile }}</p>
        <el-progress :percentage="importProgress" :status="importComplete ? 'success' : ''" />
        
        <div class="import-stats" v-if="importLogs.length > 0">
          <div class="logs-title">处理日志:</div>
          <div class="log-list">
            <div v-for="(log, idx) in importLogs" :key="idx" :class="['log-item', `log-${log.type}`]">
              {{ log.msg }}
            </div>
          </div>
        </div>
      </div>
      <template #footer v-if="importComplete">
        <el-button type="primary" @click="closeImportDialog">确定并继续编辑</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '@/utils/request'
import DOMPurify from 'dompurify'
import { marked } from 'marked'
import { getAuthHeader } from '@/utils/auth'
import MarkdownEditor from '@/components/MarkdownEditor.vue'
import MarkdownRenderer from '@/components/MarkdownRenderer.vue'
import { isCoverFileValid } from '@/utils/coverUpload'
import { uploadMedia } from '@/api/media'

const router = useRouter()

// 响应式数据
const loading = ref(false)
const uploadLoading = ref(false)
const editLoading = ref(false)
const likeLoading = ref(false)
const showUploadDialog = ref(false)
const showEditDialog = ref(false)
const showDetailDialog = ref(false)
const searchKeyword = ref('')
const selectedDifficulty = ref('')
const myTexts = ref([])
const currentText = ref(null)

// 表单数据
const uploadFormRef = ref()
const editFormRef = ref()
const uploadForm = reactive({
  title: '',
  content: '',
  difficulty: 'MEDIUM',
  tags: '',
  coverUrl: '',
  contentType: 'TEXT',
  audioUrl: '',
  isPublic: true
})

const editForm = reactive({
  id: null,
  title: '',
  content: '',
  difficulty: 'MEDIUM',
  tags: '',
  coverUrl: '',
  contentType: 'TEXT',
  audioUrl: '',
  isPublic: true
})

const uploadMarkdownContent = ref('')
const editMarkdownContent = ref('')

const uploadHeaders = computed(() => {
  const headers = {}
  const auth = getAuthHeader()
  if (auth) headers['Authorization'] = auth
  return headers
})

const coverPreviewVisible = ref(false)

// 智能导入相关的响应式数据
const mdFileInputRef = ref(null)
const importDialogVisible = ref(false)
const importProgress = ref(0)
const currentImportFile = ref('')
const importLogs = ref([])
const importComplete = ref(false)

const triggerMdImport = () => {
  if (mdFileInputRef.value) {
    mdFileInputRef.value.click()
  }
}

const closeImportDialog = () => {
  importDialogVisible.value = false
}

const handleMdFileChange = async (event) => {
  const files = Array.from(event.target.files)
  if (files.length === 0) return

  const mdFile = files.find(f => f.name.toLowerCase().endsWith('.md'))
  if (!mdFile) {
    ElMessage.error('未找到Markdown文件，请确保选中了一个.md文件')
    event.target.value = ''
    return
  }

  // 准备弹窗
  importDialogVisible.value = true
  importProgress.value = 0
  importLogs.value = []
  importComplete.value = false
  currentImportFile.value = mdFile.name
  
  importLogs.value.push({ type: 'info', msg: `开始提取 ${mdFile.name} 的内容...` })
  
  const reader = new FileReader()
  reader.onload = async (e) => {
    let content = e.target.result
    
    // 打开上传弹窗并填充基础信息
    resetUploadForm()
    uploadForm.title = mdFile.name.replace(/\.md$/i, '')
    uploadForm.contentType = 'MARKDOWN'
    
    // 正则匹配图片
    const imgRegex = /!\[(.*?)\]\((.*?)\)/g
    const matches = [...content.matchAll(imgRegex)]
    const totalImages = matches.length
    
    if (totalImages === 0) {
      importLogs.value.push({ type: 'success', msg: '未检测到任何图片链接，提取完成。' })
      importProgress.value = 100
      uploadMarkdownContent.value = content
      showUploadDialog.value = true
      importComplete.value = true
      return
    }

    importLogs.value.push({ type: 'info', msg: `共检测到 ${totalImages} 个图片链接，进入混合搬运流水线...` })
    
    let processedCount = 0
    let successCount = 0
    let localMatchCount = 0
    let skipCount = 0
    
    for (const match of matches) {
      const fullMatch = match[0]
      const imgPath = match[2]
      
      currentImportFile.value = imgPath
      
      try {
        if (imgPath.startsWith('http://') || imgPath.startsWith('https://')) {
          // 云端地址处理
          try {
            const resp = await fetch(imgPath)
            if (!resp.ok) throw new Error(`HTTP ${resp.status}`)
            const blob = await resp.blob()
            const filename = imgPath.split('/').pop().split('?')[0] || 'cloud_image.jpg'
            const fileObj = new File([blob], filename, { type: blob.type || 'image/jpeg' })
            
            const uploadResp = await uploadMedia(fileObj, 'IMAGE')
            if (uploadResp && uploadResp.code === 200) {
              const mediaId = uploadResp.data.mediaId
              const newUrl = `/api/media/${mediaId}/download`
              content = content.replace(fullMatch, `![${match[1]}](${newUrl})`)
              successCount++
              importLogs.value.push({ type: 'success', msg: `[云端搬运] ${filename} 成功` })
            } else {
              throw new Error(uploadResp?.message || '上传接口返回失败')
            }
          } catch (fetchErr) {
            skipCount++
            importLogs.value.push({ type: 'warning', msg: `[云端搬运] 跨域受阻或失败，保留原链接: ${imgPath}` })
            console.warn('Fetch error:', fetchErr)
          }
        } else {
          // 本地引用处理
          const expectedFilename = imgPath.split('/').pop() // 取文件名
          const decodedFilename = decodeURIComponent(expectedFilename) // 处理URL编码
          const matchedFile = files.find(f => f.name === expectedFilename || f.name === decodedFilename)
          
          if (matchedFile) {
            const uploadResp = await uploadMedia(matchedFile, 'IMAGE')
            if (uploadResp && uploadResp.code === 200) {
              const mediaId = uploadResp.data.mediaId
              const newUrl = `/api/media/${mediaId}/download`
              content = content.replace(fullMatch, `![${match[1]}](${newUrl})`)
              localMatchCount++
              successCount++
              importLogs.value.push({ type: 'success', msg: `[本地匹配] ${matchedFile.name} 替换成功` })
            } else {
              skipCount++
              importLogs.value.push({ type: 'error', msg: `[本地匹配] ${expectedFilename} 上传失败: ${uploadResp?.message}` })
            }
          } else {
            skipCount++
            importLogs.value.push({ type: 'warning', msg: `[本地匹配] 未找到匹配文件，保留原链接: ${imgPath}` })
          }
        }
      } catch (err) {
        skipCount++
        importLogs.value.push({ type: 'error', msg: `[处理异常] ${imgPath} 发生错误: ${err.message}` })
      }
      
      processedCount++
      importProgress.value = Math.floor((processedCount / totalImages) * 100)
    }
    
    importLogs.value.push({ type: 'info', msg: `🎉 处理完成！成功搬运/匹配 ${successCount} 张（含本地 ${localMatchCount} 张），跳过/失败 ${skipCount} 张。` })
    importProgress.value = 100
    importComplete.value = true
    
    // 将最终的内容赋值给编辑器
    uploadMarkdownContent.value = content
    showUploadDialog.value = true
  }
  
  reader.onerror = () => {
    ElMessage.error('读取文件报错')
    importDialogVisible.value = false
  }
  
  reader.readAsText(mdFile)
  
  // 清空 value 允许重复选择同一批文件
  event.target.value = ''
}

// 封面图上传逻辑
const beforeCoverUpload = (rawFile) => {
  if (!isCoverFileValid(rawFile)) {
    const allowedTypes = ['image/jpeg', 'image/png', 'image/gif', 'image/webp']
    if (!allowedTypes.includes(rawFile.type)) {
      ElMessage.error('仅支持 JPG/PNG/GIF/WebP 格式的图片')
    } else {
      ElMessage.error('图片大小不能超过 10MB')
    }
    return false
  }
  return true
}

const handleCoverSuccess = (response, _file, _fileList, targetForm) => {
  if (response.code === 200) {
    const mediaId = response.data.mediaId
    targetForm.coverUrl = `/api/media/${mediaId}/download`
    ElMessage.success('封面上传成功')
  } else {
    ElMessage.error(response.message || '上传失败')
  }
}

const handleUploadCoverSuccess = (response) => handleCoverSuccess(response, null, null, uploadForm)
const handleEditCoverSuccess = (response) => handleCoverSuccess(response, null, null, editForm)

const handleCoverError = () => {
  ElMessage.error('文件上传失败，请检查网络或登录状态')
}

const removeCover = (form) => {
  form.coverUrl = ''
}

const previewCover = () => {
  coverPreviewVisible.value = true
}

// 表单验证规则 — content 规则根据 contentType 动态切换
const uploadRules = computed(() => ({
  title: [
    { required: true, message: '请输入课文标题', trigger: 'blur' },
    { min: 1, max: 200, message: '标题长度应在1-200字符之间', trigger: 'blur' }
  ],
  content: uploadForm.contentType === 'TEXT' ? [
    { required: true, message: '请输入课文内容', trigger: 'blur' },
    { min: 10, max: 22000, message: '内容长度应在10-22000字符之间', trigger: 'blur' }
  ] : [],
  difficulty: [
    { required: true, message: '请选择难度', trigger: 'change' }
  ]
}))

const editRules = computed(() => ({
  title: [
    { required: true, message: '请输入课文标题', trigger: 'blur' },
    { min: 1, max: 200, message: '标题长度应在1-200字符之间', trigger: 'blur' }
  ],
  content: editForm.contentType === 'TEXT' ? [
    { required: true, message: '请输入课文内容', trigger: 'blur' },
    { min: 10, max: 22000, message: '内容长度应在10-22000字符之间', trigger: 'blur' }
  ] : [],
  difficulty: [
    { required: true, message: '请选择难度', trigger: 'change' }
  ]
}))

// 方法
const loadMyUploads = async () => {
  loading.value = true
  try {
    const response = await request({
      url: '/api/user/texts/my-uploads',
      method: 'get'
    })

    if (response.code === 200) {
      myTexts.value = (response.data && Array.isArray(response.data)) ? response.data : []
    }
  } catch (error) {
    ElMessage.error('获取上传内容失败')
    console.error(error)
  } finally {
    loading.value = false
  }
}

const handleUpload = async () => {
  if (!uploadFormRef.value) return

  await uploadFormRef.value.validate(async (valid) => {
    if (valid) {
      // MARKDOWN 空内容校验
      if (uploadForm.contentType === 'MARKDOWN' && (!uploadMarkdownContent.value || !uploadMarkdownContent.value.trim())) {
        ElMessage.error('请输入Markdown内容')
        return
      }

      uploadLoading.value = true
      try {
        const data = {
          title: uploadForm.title,
          content: uploadForm.contentType === 'TEXT' ? uploadForm.content : '',
          markdownContent: uploadForm.contentType === 'MARKDOWN' ? uploadMarkdownContent.value : '',
          contentType: uploadForm.contentType,
          difficulty: uploadForm.difficulty,
          tags: uploadForm.tags,
          coverUrl: uploadForm.coverUrl,
          audioUrl: uploadForm.audioUrl,
          isPublic: uploadForm.isPublic
        }
        const response = await request({
          url: '/api/user/texts/upload',
          method: 'post',
          data
        })

        if (response.code === 200) {
          ElMessage.success('上传成功！')
          showUploadDialog.value = false
          resetUploadForm()
          loadMyUploads()
        } else {
          ElMessage.error('上传失败')
        }
      } catch (error) {
        ElMessage.error('上传失败，请重试')
        console.error(error)
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
    contentType: 'TEXT',
    difficulty: 'MEDIUM',
    tags: '',
    coverUrl: '',
    audioUrl: '',
    isPublic: true
  })
  uploadMarkdownContent.value = ''
  if (uploadFormRef.value) {
    uploadFormRef.value.clearValidate()
  }
}

const editText = (text) => {
  Object.assign(editForm, {
    id: text.id,
    title: text.title,
    content: text.content || '',
    contentType: text.contentType || 'TEXT',
    difficulty: text.difficulty,
    tags: text.tags || '',
    coverUrl: text.coverUrl || '',
    audioUrl: text.audioUrl || '',
    isPublic: text.isPublic != null ? text.isPublic : true
  })
  editMarkdownContent.value = text.markdownContent || ''
  showEditDialog.value = true
}

const handleEdit = async () => {
  if (!editFormRef.value) return

  await editFormRef.value.validate(async (valid) => {
    if (valid) {
      // MARKDOWN 空内容校验
      if (editForm.contentType === 'MARKDOWN' && (!editMarkdownContent.value || !editMarkdownContent.value.trim())) {
        ElMessage.error('请输入Markdown内容')
        return
      }

      editLoading.value = true
      try {
        const data = {
          title: editForm.title,
          content: editForm.contentType === 'TEXT' ? editForm.content : '',
          markdownContent: editForm.contentType === 'MARKDOWN' ? editMarkdownContent.value : '',
          contentType: editForm.contentType,
          difficulty: editForm.difficulty,
          tags: editForm.tags,
          coverUrl: editForm.coverUrl,
          audioUrl: editForm.audioUrl,
          isPublic: editForm.isPublic
        }
        const response = await request({
          url: `/api/user/texts/${editForm.id}`,
          method: 'put',
          data
        })

        if (response.code === 200) {
          ElMessage.success('修改成功！')
          showEditDialog.value = false
          loadMyUploads()
        } else {
          ElMessage.error('修改失败')
        }
      } catch (error) {
        ElMessage.error('修改失败，请重试')
        console.error(error)
      } finally {
        editLoading.value = false
      }
    }
  })
}

const deleteText = (text) => {
  if (!text?.id) {
    ElMessage.error('课文ID不存在')
    return
  }
  ElMessageBox.confirm(
    `确定要删除《${text.title || '该课文'}》吗？`,
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

      if (response.code === 200) {
        ElMessage.success('删除成功')
        loadMyUploads()
      } else {
        ElMessage.error('删除失败')
      }
    } catch (error) {
      ElMessage.error('删除失败，请重试')
      console.error(error)
    }
  }).catch(() => {
    // User cancelled
  })
}

const viewText = (text) => {
  // 直接跳转到阅读页面，不再弹出详情对话框
  studyText(text)
}

const likeText = async (text) => {
  if (!text?.id) {
    ElMessage.error('课文ID不存在')
    return
  }
  likeLoading.value = true
  try {
    const response = await request({
      url: `/api/user/texts/${text.id}/like`,
      method: 'post'
    })

    if (response.code === 200) {
      ElMessage.success('点赞成功')
      // 更新点赞数
      text.likeCount = (text.likeCount || 0) + 1
      if (currentText.value?.id === text.id) {
        currentText.value.likeCount = (currentText.value.likeCount || 0) + 1
      }
    } else {
      ElMessage.error('点赞失败')
    }
  } catch (error) {
    ElMessage.error('点赞失败，请重试')
    console.error(error)
  } finally {
    likeLoading.value = false
  }
}

const studyText = (text) => {
  if (!text?.id) {
    ElMessage.error('课文ID不存在')
    return
  }
  router.push({
    path: `/article/${text.id}`,
    query: {
      from: 'user-upload'
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
  // FIX: 先清理标签再转换换行符，避免 <br> 标签注入
  return DOMPurify.sanitize(marked(content))
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

.header-actions {
  display: flex;
  gap: 10px;
}

.import-progress-container {
  padding: 10px 0;
}

.current-file {
  margin-bottom: 20px;
  font-size: 14px;
  color: #606266;
  word-break: break-all;
}

.import-stats {
  margin-top: 25px;
  background-color: #f5f7fa;
  border-radius: 4px;
  padding: 15px;
  max-height: 250px;
  overflow-y: auto;
}

.logs-title {
  font-size: 14px;
  font-weight: 600;
  margin-bottom: 10px;
  color: #303133;
}

.log-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.log-item {
  font-size: 13px;
  line-height: 1.5;
  word-break: break-all;
}

.log-info { color: #909399; }
.log-success { color: #67c23a; }
.log-warning { color: #e6a23c; }
.log-error { color: #f56c6c; }

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

.text-detail {
  display: flex;
  flex-direction: column;
  max-height: 75vh;
}

.detail-header {
  flex-shrink: 0;
  background-color: var(--el-bg-color, #fff);
  padding: 0 0 16px 0;
  border-bottom: 2px solid #f5f7fa;
  z-index: 10;
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
  word-break: break-word;
  overflow-wrap: break-word;
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
  display: flex;
  flex-direction: column;
  max-height: 75vh;
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
  flex-grow: 1;
  overflow-y: auto;
  padding: 20px 0;
  margin-top: 0;
}

.content-text {
  line-height: 1.8;
  font-size: 15px;
  color: #333;
  white-space: pre-wrap;
  word-break: break-word;
  overflow-wrap: break-word;
}

.header-bottom {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px;
}

.detail-actions {
  display: flex;
  gap: 12px;
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

/* ── 上传/编辑表单 ── */
.text-form {
  max-height: 70vh;
  overflow-y: auto;
}

:deep(.text-form .el-form-item) {
  margin-bottom: 20px;
}

:deep(.text-form .markdown-editor) {
  height: 400px;
}

/* ── 封面上传区域 ── */
.cover-section {
  display: flex;
  flex-direction: column;
  gap: 10px;
  width: 100%;
}

.cover-uploader {
  width: 100%;
  max-width: 420px;
}

:deep(.cover-uploader .el-upload-dragger) {
  width: 100%;
  min-height: 160px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: 2px dashed #dcdfe6;
  border-radius: 12px;
  background: #fafbfc;
  transition: all 0.3s ease;
  padding: 0;
  overflow: hidden;
}

:deep(.cover-uploader .el-upload-dragger:hover) {
  border-color: var(--el-color-primary);
  background: #f0f5ff;
}

:deep(.cover-uploader.has-cover .el-upload-dragger) {
  border: none;
  padding: 0;
  min-height: auto;
}

.cover-placeholder {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 24px 16px;
  color: #8c939d;
}

.upload-icon {
  color: #c0c4cc;
  transition: color 0.3s;
}

.upload-text {
  font-size: 14px;
  font-weight: 500;
  color: #606266;
}

.upload-hint {
  font-size: 12px;
  color: #a8abb2;
}

.cover-preview-wrap {
  position: relative;
  width: 100%;
  line-height: 0;
}

.cover-preview {
  width: 100%;
  max-height: 220px;
  object-fit: cover;
  display: block;
  border-radius: 10px;
}

.cover-overlay {
  position: absolute;
  inset: 0;
  background: rgba(0, 0, 0, 0.45);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 6px;
  color: #fff;
  font-size: 13px;
  font-weight: 500;
  opacity: 0;
  transition: opacity 0.3s ease;
  border-radius: 10px;
}

.cover-preview-wrap:hover .cover-overlay {
  opacity: 1;
}

.cover-actions {
  display: flex;
  gap: 12px;
}

.preview-image-wrap {
  display: flex;
  justify-content: center;
  align-items: center;
  max-height: 70vh;
  overflow: auto;
}

.preview-full-image {
  max-width: 100%;
  max-height: 70vh;
  object-fit: contain;
  border-radius: 8px;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.1);
}
</style>

<style>
.editor-dialog .el-dialog.is-fullscreen {
  display: flex;
  flex-direction: column;
}
.editor-dialog .el-dialog.is-fullscreen .el-dialog__body {
  flex: 1;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}
.editor-dialog .el-dialog.is-fullscreen .text-form {
  max-height: none;
  height: 100%;
  display: flex;
  flex-direction: column;
  overflow-y: auto;
}
.editor-dialog .el-dialog.is-fullscreen .text-form .md-editor-item {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-height: 400px;
}
.editor-dialog .el-dialog.is-fullscreen .text-form .md-editor-item .el-form-item__content {
  flex: 1;
  display: flex;
  flex-direction: column;
}
.editor-dialog .el-dialog.is-fullscreen .text-form .markdown-editor {
  height: 100% !important;
  flex: 1;
}
</style>