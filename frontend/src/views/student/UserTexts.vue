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
        <el-button type="warning" @click="showVideoUploadDialog = true">
          <el-icon><VideoPlay /></el-icon>
          上传视频
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

    <!-- 内容列表（课文 + 视频混合） -->
    <el-card class="content-card">
      <div v-if="loading && videoLoading" class="loading-container">
        <el-skeleton :rows="3" animated />
      </div>

      <div v-else-if="allItems.length === 0" class="empty-container">
        <el-empty description="暂无上传内容" />
        <div style="margin-top: 20px; display: flex; gap: 12px;">
          <el-button type="primary" @click="showUploadDialog = true">上传课文</el-button>
          <el-button type="warning" @click="showVideoUploadDialog = true">上传视频</el-button>
        </div>
      </div>

      <div v-else class="text-grid">
        <div
          v-for="item in allItems"
          :key="item._type + '-' + item.id"
          class="text-item"
          :class="{ 'video-item': item._type === 'video' }"
          @click="handleItemClick(item)"
        >
          <div class="text-header">
            <h3 class="text-title">
              <el-icon v-if="item._type === 'video'" style="color: #e6a23c; margin-right: 4px"><VideoPlay /></el-icon>
              {{ item.title }}
            </h3>
            <div class="text-meta">
              <el-tag v-if="item._type === 'video'" type="warning" size="small">视频</el-tag>
              <el-tag v-else :type="getDifficultyType(item.difficulty)" size="small">
                {{ getDifficultyText(item.difficulty) }}
              </el-tag>
              <el-tag v-if="item.tags" size="small" type="info">
                {{ item.tags.split(',')[0] }}
              </el-tag>
            </div>
          </div>

          <div class="text-content">
            <p>{{ getContentPreview(item._type === 'video' ? item.description : (item.contentType === 'MARKDOWN' ? item.markdownContent : item.content)) }}</p>
          </div>

          <div class="text-footer">
            <div class="text-stats">
              <span><el-icon><View /></el-icon> {{ item.viewCount || 0 }}</span>
              <span><el-icon><Star /></el-icon> {{ item.likeCount || 0 }}</span>
            </div>

            <div class="text-actions">
              <el-button size="small" @click.stop="item._type === 'video' ? editVideoItem(item) : editText(item)">
                <el-icon><Edit /></el-icon> 编辑
              </el-button>
              <el-button size="small" type="danger" @click.stop="item._type === 'video' ? deleteVideoItem(item) : deleteText(item)">
                <el-icon><Delete /></el-icon> 删除
              </el-button>
            </div>
          </div>

          <div class="text-time">
            {{ formatDate(item._type === 'video' ? item.createdTime : item.createdAt) }}
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

    <!-- 视频上传对话框 -->
    <el-dialog
      v-model="showVideoUploadDialog"
      :title="editingVideo ? '编辑视频' : '上传视频'"
      width="min(600px, 94vw)"
      class="custom-video-dialog"
      :close-on-click-modal="false"
      @close="resetVideoForm"
    >
      <el-form ref="videoFormRef" :model="videoForm" :rules="videoFormRules" label-width="80px">
        <el-form-item label="标题" prop="title">
          <el-input v-model="videoForm.title" placeholder="请输入视频标题" maxlength="200" show-word-limit />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="videoForm.description" type="textarea" :rows="3" placeholder="请输入视频描述" />
        </el-form-item>
        <el-form-item label="视频文件" v-if="!editingVideo">
          <el-upload
            class="video-uploader"
            :auto-upload="false"
            :show-file-list="true"
            :limit="1"
            accept="video/mp4,video/webm,video/ogg"
            :on-change="handleVideoFileSelect"
          >
            <el-button type="primary">选择视频文件</el-button>
            <template #tip>
              <div class="el-upload__tip">支持 MP4/WebM/OGG，最大 512MB</div>
            </template>
          </el-upload>
        </el-form-item>
        <el-form-item label="封面图">
          <el-upload
            class="cover-uploader"
            :auto-upload="false"
            :show-file-list="true"
            :limit="1"
            accept="image/jpeg,image/png,image/webp"
            :on-change="handleVideoCoverSelect"
          >
            <el-button>选择封面图</el-button>
            <template #tip>
              <div class="el-upload__tip">支持 JPG/PNG/WebP，最大 10MB</div>
            </template>
          </el-upload>
        </el-form-item>
        <el-form-item label="英文字幕">
          <el-upload
            class="subtitle-uploader"
            :auto-upload="false"
            :show-file-list="true"
            :limit="1"
            accept=".vtt,.srt,.txt"
            :on-change="handleSubtitleSelect"
          >
            <el-button>选择英文字幕</el-button>
            <template #tip>
              <div class="el-upload__tip">支持 VTT/SRT/TXT 格式（可选）</div>
            </template>
          </el-upload>
        </el-form-item>
        <el-form-item label="中文字幕">
          <el-upload
            class="subtitle-uploader"
            :auto-upload="false"
            :show-file-list="true"
            :limit="1"
            accept=".vtt,.srt,.txt"
            :on-change="handleSubtitleZhSelect"
          >
            <el-button>选择中文字幕</el-button>
            <template #tip>
              <div class="el-upload__tip">支持 VTT/SRT/TXT 格式（可选）</div>
            </template>
          </el-upload>
        </el-form-item>
        <el-form-item label="标签">
          <el-input v-model="videoForm.tags" placeholder="多个标签用逗号分隔" />
        </el-form-item>
        <el-form-item label="公开">
          <el-switch v-model="videoForm.isPublicBool" />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="showVideoUploadDialog = false">取消</el-button>
        <el-button type="primary" :loading="videoSubmitLoading" @click="handleVideoSubmit">
          {{ editingVideo ? '保存修改' : '确认上传' }}
        </el-button>
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
import * as videoApi from '@/api/video'
import { VideoPlay } from '@element-plus/icons-vue'

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

// 合并课文和视频到一个列表
const allItems = computed(() => {
  const textItems = myTexts.value.map(t => ({ ...t, _type: 'text', _sortTime: t.createdAt }))
  const videoItems = myVideos.value.map(v => ({ ...v, _type: 'video', _sortTime: v.createdTime }))
  return [...textItems, ...videoItems].sort((a, b) => {
    const ta = a._sortTime ? new Date(a._sortTime).getTime() : 0
    const tb = b._sortTime ? new Date(b._sortTime).getTime() : 0
    return tb - ta // 新的在前
  })
})

const handleItemClick = (item) => {
  if (item._type === 'video') {
    playVideo(item)
  } else {
    studyText(item)
  }
}

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

// ─── 视频上传相关 ─────────────────────────────────────────
const showVideoUploadDialog = ref(false)
const videoSubmitLoading = ref(false)
const videoLoading = ref(false)
const myVideos = ref([])
const editingVideo = ref(null)
const videoFormRef = ref()

const videoForm = reactive({
  title: '',
  description: '',
  mediaId: null,
  coverMediaId: null,
  tags: '',
  subtitleContent: '',
  subtitleContentZh: '',
  isPublicBool: true,
  videoUrl: '',
  coverUrl: ''
})

const videoFormRules = {
  title: [{ required: true, message: '请输入视频标题', trigger: 'blur' }]
}

let selectedVideoFile = null
let selectedVideoCoverFile = null

const handleVideoFileSelect = (file) => { selectedVideoFile = file.raw }
const handleVideoCoverSelect = (file) => { selectedVideoCoverFile = file.raw }
const handleSubtitleSelect = async (file) => {
  try {
    const text = await file.raw.text()
    videoForm.subtitleContent = text
  } catch (e) {
    ElMessage.error('英文字幕文件读取失败')
  }
}
const handleSubtitleZhSelect = async (file) => {
  try {
    const text = await file.raw.text()
    videoForm.subtitleContentZh = text
  } catch (e) {
    ElMessage.error('中文字幕文件读取失败')
  }
}

const resetVideoForm = () => {
  Object.assign(videoForm, { title: '', description: '', mediaId: null, coverMediaId: null, tags: '', subtitleContent: '', subtitleContentZh: '', isPublicBool: true, videoUrl: '', coverUrl: '' })
  selectedVideoFile = null
  selectedVideoCoverFile = null
  editingVideo.value = null
  videoFormRef.value?.clearValidate()
}

const loadMyVideos = async () => {
  videoLoading.value = true
  try {
    const res = await videoApi.getMyVideoUploads()
    if (res.code === 200) {
      myVideos.value = Array.isArray(res.data) ? res.data : []
    }
  } catch (e) {
    console.error('加载视频列表失败:', e)
  } finally {
    videoLoading.value = false
  }
}

const handleVideoSubmit = async () => {
  if (!videoFormRef.value) return
  try {
    const valid = await videoFormRef.value.validate()
    if (!valid) return
  } catch { return }

  videoSubmitLoading.value = true
  try {
    // 上传视频文件
    if (selectedVideoFile) {
      const uploadRes = await uploadMedia(selectedVideoFile, 'VIDEO')
      if (uploadRes.code === 200 && uploadRes.data?.mediaId) {
        videoForm.mediaId = uploadRes.data.mediaId
        videoForm.videoUrl = uploadRes.data.url || `/api/media/${uploadRes.data.mediaId}/download`
      } else {
        ElMessage.error('视频上传失败'); return
      }
    }
    // 上传封面
    if (selectedVideoCoverFile) {
      const coverRes = await uploadMedia(selectedVideoCoverFile, 'IMAGE')
      if (coverRes.code === 200 && coverRes.data?.mediaId) {
        videoForm.coverMediaId = coverRes.data.mediaId
        videoForm.coverUrl = coverRes.data.url || `/api/media/${coverRes.data.mediaId}/download`
      }
    }

    if (!editingVideo.value && !videoForm.mediaId) {
      ElMessage.error('请选择视频文件'); return
    }

    const data = {
      title: videoForm.title,
      description: videoForm.description,
      mediaId: videoForm.mediaId,
      coverMediaId: videoForm.coverMediaId,
      tags: videoForm.tags,
      subtitleContent: videoForm.subtitleContent || null,
      subtitleContentZh: videoForm.subtitleContentZh || null,
      isPublic: videoForm.isPublicBool ? 1 : 0,
      videoUrl: videoForm.videoUrl,
      coverUrl: videoForm.coverUrl
    }

    if (editingVideo.value) {
      const res = await videoApi.updateUserVideo(editingVideo.value.id, data)
      if (res.code === 200) {
        ElMessage.success('更新成功')
        showVideoUploadDialog.value = false
        loadMyVideos()
      } else {
        ElMessage.error(res.message || '更新失败')
      }
    } else {
      const res = await videoApi.uploadUserVideo(data)
      if (res.code === 200) {
        ElMessage.success('上传成功')
        showVideoUploadDialog.value = false
        loadMyVideos()
      } else {
        ElMessage.error(res.message || '上传失败')
      }
    }
  } catch (e) {
    console.error('视频提交失败:', e)
    ElMessage.error(e.message || '操作失败')
  } finally {
    videoSubmitLoading.value = false
  }
}

const editVideoItem = (video) => {
  editingVideo.value = video
  Object.assign(videoForm, {
    title: video.title,
    description: video.description || '',
    mediaId: video.mediaId,
    coverMediaId: video.coverMediaId,
    tags: video.tags || '',
    subtitleContent: video.subtitleContent || '',
    subtitleContentZh: video.subtitleContentZh || '',
    isPublicBool: video.isPublic === 1,
    videoUrl: video.videoUrl || '',
    coverUrl: video.coverUrl || ''
  })
  showVideoUploadDialog.value = true
}

const deleteVideoItem = (video) => {
  ElMessageBox.confirm(`确定要删除视频 "${video.title}" 吗？`, '删除确认', { type: 'warning' })
    .then(async () => {
      const res = await videoApi.deleteUserVideo(video.id)
      if (res.code === 200) {
        ElMessage.success('删除成功')
        loadMyVideos()
      } else {
        ElMessage.error(res.message || '删除失败')
      }
    }).catch(() => {})
}

const playVideo = (video) => {
  // isOwner=true 告知 VideoPlayer 用用户端 API 加载（支持预览待审核的自己的视频）
  router.push({ path: '/student/video-player', query: { videoId: video.id, isOwner: '1' } })
}

// 生命周期
onMounted(() => {
  loadMyUploads()
  loadMyVideos()
})
</script>

<style scoped>
.video-section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.video-item {
  border-left: 3px solid #e6a23c !important;
}

.video-item:hover {
  border-left-color: #f5a623 !important;
}
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

/* 修复视频弹窗内上传列表内容溢出问题并允许多行完整显示 */
.custom-video-dialog .el-form-item__content {
  min-width: 0 !important; /* 关键修复：防止 flex 子项无限自适应文本宽度撑开弹窗 */
}
.custom-video-dialog .video-uploader,
.custom-video-dialog .cover-uploader,
.custom-video-dialog .subtitle-uploader {
  width: 100%;
  max-width: 100%;
}
.custom-video-dialog .el-upload-list {
  width: 100%;
}
.custom-video-dialog .el-upload-list__item {
  height: auto !important;
  min-height: 32px;
  max-width: 100% !important;
  box-sizing: border-box;
}
.custom-video-dialog .el-upload-list__item-info {
  width: 100%;
  min-width: 0 !important;
}
.custom-video-dialog .el-upload-list__item-name {
  display: block !important;
  white-space: normal !important;
  word-wrap: break-word !important;
  word-break: break-all !important;
  line-height: 1.5 !important;
  height: auto !important;
  padding-right: 25px !important;
  margin-bottom: 2px !important;
}
.custom-video-dialog .el-upload-list__item-file-name {
  white-space: normal !important;
  word-wrap: break-word !important;
  word-break: break-all !important;
}
</style>