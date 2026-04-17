<template>
  <div class="markdown-editor" :class="{ 'is-fullscreen': isFullscreen }">
    <!-- 工具栏 -->
    <div class="editor-toolbar">
      <el-button-group>
        <el-button size="small" @click="insertMarkdown('# ')">H1</el-button>
        <el-button size="small" @click="insertMarkdown('## ')">H2</el-button>
        <el-button size="small" @click="insertMarkdown('### ')">H3</el-button>
      </el-button-group>

      <el-button-group style="margin-left: 10px">
        <el-button size="small" @click="insertMarkdown('**')">加粗</el-button>
        <el-button size="small" @click="insertMarkdown('*')">斜体</el-button>
        <el-button size="small" @click="insertMarkdown('~~')">删除线</el-button>
      </el-button-group>

      <el-button-group style="margin-left: 10px">
        <el-button size="small" @click="insertMarkdown('- ')">列表</el-button>
        <el-button size="small" @click="insertMarkdown('1. ')">有序列表</el-button>
        <el-button size="small" @click="insertMarkdown('> ')">引用</el-button>
      </el-button-group>

      <el-button-group style="margin-left: 10px">
        <el-button size="small" @click="insertLink">链接</el-button>
        <el-button size="small" @click="insertCode">代码</el-button>
        <el-button size="small" @click="insertTable">表格</el-button>
      </el-button-group>

      <el-button-group style="margin-left: 10px">
        <el-button size="small" @click="showImageUpload">图片</el-button>
        <el-button size="small" @click="showVideoUpload">视频</el-button>
      </el-button-group>

      <el-button-group style="margin-left: 10px">
        <el-button size="small" @click="clearContent">清空</el-button>
        <el-button size="small" @click="toggleFullscreen" :type="isFullscreen ? 'primary' : ''">
          {{ isFullscreen ? '小窗' : '全屏' }}
        </el-button>
      </el-button-group>
    </div>

    <!-- 编辑区域：左右分栏 -->
    <div class="editor-container">
      <!-- 左侧：编辑器 -->
      <div class="editor-pane">
        <textarea
          ref="textareaRef"
          v-model="content"
          class="markdown-textarea"
          placeholder="在此输入Markdown内容..."
          @input="handleInput"
          @scroll="handleEditorScroll"
        ></textarea>
      </div>

      <!-- 分隔线 -->
      <div class="editor-divider"></div>

      <!-- 右侧：实时预览（直接调用学生端渲染组件） -->
      <div class="preview-pane" ref="previewPaneRef" @scroll="handlePreviewScroll">
        <div class="preview-header">📖 学生端预览</div>
        <MarkdownRenderer :markdown="content" :show-background="true" :clickable-images="true" />
      </div>
    </div>

    <!-- 文件上传隐藏input -->
    <input
      ref="imageInput"
      type="file"
      accept="image/*"
      style="display: none"
      @change="handleImageUpload"
    />
    <input
      ref="videoInput"
      type="file"
      accept="video/*"
      style="display: none"
      @change="handleVideoUpload"
    />

    <!-- 上传进度对话框 -->
    <el-dialog v-model="uploadDialogVisible" title="上传媒体" width="min(400px, 92vw)">
      <div class="upload-progress">
        <el-progress :percentage="uploadProgress" />
        <p>{{ uploadStatus }}</p>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, watch, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import { uploadMedia, deleteMedia } from '@/api/media'
import MarkdownRenderer from './MarkdownRenderer.vue'

const props = defineProps({
  modelValue: {
    type: String,
    default: ''
  }
})

const emit = defineEmits(['update:modelValue'])

const content = ref(props.modelValue)
const isFullscreen = ref(false)
const uploadDialogVisible = ref(false)
const uploadProgress = ref(0)
const uploadStatus = ref('')
const imageInput = ref(null)
const videoInput = ref(null)
const textareaRef = ref(null)
const previewPaneRef = ref(null)

// 滚动同步防循环标志
let isEditorScrolling = false
let isPreviewScrolling = false

const handleEditorScroll = () => {
  if (isPreviewScrolling) return
  isEditorScrolling = true
  const textarea = textareaRef.value
  const preview = previewPaneRef.value
  if (textarea && preview) {
    const scrollRatio = textarea.scrollTop / (textarea.scrollHeight - textarea.clientHeight || 1)
    preview.scrollTop = scrollRatio * (preview.scrollHeight - preview.clientHeight)
  }
  requestAnimationFrame(() => { isEditorScrolling = false })
}

const handlePreviewScroll = () => {
  if (isEditorScrolling) return
  isPreviewScrolling = true
  const textarea = textareaRef.value
  const preview = previewPaneRef.value
  if (textarea && preview) {
    const scrollRatio = preview.scrollTop / (preview.scrollHeight - preview.clientHeight || 1)
    textarea.scrollTop = scrollRatio * (textarea.scrollHeight - textarea.clientHeight)
  }
  requestAnimationFrame(() => { isPreviewScrolling = false })
}

// 追踪上传相关的 setTimeout timers，防止内存泄漏
let uploadTimer = null

// 追踪当前内容中的 media IDs，用于删除检测
let previousMediaIds = new Set()

// 从内容中提取所有 media IDs
const extractMediaIds = (text) => {
  const ids = new Set()
  if (!text) return ids
  // 匹配 ![...]( /api/media/{id}/download ) 和 src="/api/media/{id}/download"
  const regex = /\/api\/media\/(\d+)\/download/g
  let match
  while ((match = regex.exec(text)) !== null) {
    ids.add(match[1])
  }
  return ids
}

// 初始化已有的 media IDs
previousMediaIds = extractMediaIds(props.modelValue)

// 监听props变化
watch(() => props.modelValue, (newVal) => {
  content.value = newVal
  previousMediaIds = extractMediaIds(newVal)
})


// 处理输入，并检测被删除的媒体
const handleInput = () => {
  const newContent = content.value
  emit('update:modelValue', newContent)

  // 检测被移除的 media IDs 并自动删除
  const currentMediaIds = extractMediaIds(newContent)
  for (const id of previousMediaIds) {
    if (!currentMediaIds.has(id)) {
      deleteMedia(id).then(() => {
        console.log(`已删除媒体: ${id}`)
      }).catch(err => {
        console.warn(`删除媒体 ${id} 失败:`, err)
      })
    }
  }
  previousMediaIds = currentMediaIds
}

// 获取 textarea DOM 元素
const getTextarea = () => {
  return textareaRef.value
}

// 插入Markdown语法
const insertMarkdown = async (syntax) => {
  const textarea = getTextarea()
  if (!textarea) return

  const start = textarea.selectionStart
  const end = textarea.selectionEnd
  const selectedText = content.value.substring(start, end)
  const beforeText = content.value.substring(0, start)
  const afterText = content.value.substring(end)

  if (selectedText) {
    content.value = beforeText + syntax + selectedText + syntax + afterText
  } else {
    content.value = beforeText + syntax + '文本' + syntax + afterText
  }

  emit('update:modelValue', content.value)

  await nextTick()
  const updatedTextarea = getTextarea()
  if (updatedTextarea) {
    updatedTextarea.focus()
    updatedTextarea.selectionStart = start + syntax.length
    updatedTextarea.selectionEnd = start + syntax.length + 2
  }
}

// 插入链接
const insertLink = async () => {
  const textarea = getTextarea()
  if (!textarea) return

  const start = textarea.selectionStart
  const beforeText = content.value.substring(0, start)
  const afterText = content.value.substring(start)

  content.value = beforeText + '[链接文本](https://example.com)' + afterText
  emit('update:modelValue', content.value)

  await nextTick()
  const updatedTextarea = getTextarea()
  if (updatedTextarea) {
    updatedTextarea.focus()
    updatedTextarea.selectionStart = start + 1
    updatedTextarea.selectionEnd = start + 5
  }
}

// 插入代码
const insertCode = async () => {
  const textarea = getTextarea()
  if (!textarea) return

  const start = textarea.selectionStart
  const beforeText = content.value.substring(0, start)
  const afterText = content.value.substring(start)

  content.value = beforeText + '```\n代码\n```\n' + afterText
  emit('update:modelValue', content.value)

  await nextTick()
  const updatedTextarea = getTextarea()
  if (updatedTextarea) {
    updatedTextarea.focus()
    updatedTextarea.selectionStart = start + 4
    updatedTextarea.selectionEnd = start + 6
  }
}

// 插入表格
const insertTable = async () => {
  const textarea = getTextarea()
  if (!textarea) return

  const start = textarea.selectionStart
  const beforeText = content.value.substring(0, start)
  const afterText = content.value.substring(start)

  const table = '| 列1 | 列2 | 列3 |\n|-----|-----|-----|\n| 内容 | 内容 | 内容 |\n'
  content.value = beforeText + table + afterText
  emit('update:modelValue', content.value)

  await nextTick()
  const updatedTextarea = getTextarea()
  if (updatedTextarea) {
    updatedTextarea.focus()
    updatedTextarea.selectionStart = start
  }
}

// 显示图片上传
const showImageUpload = () => {
  if (!imageInput.value) {
    ElMessage.warning('图片上传组件未就绪，请重试')
    return
  }
  imageInput.value.click()
}

// 显示视频上传
const showVideoUpload = () => {
  if (!videoInput.value) {
    ElMessage.warning('视频上传组件未就绪，请重试')
    return
  }
  videoInput.value.click()
}

// 处理图片上传
const handleImageUpload = async (event) => {
  const file = event.target.files?.[0]
  if (!file) return
  await uploadMediaFile(file, 'IMAGE')
  if (imageInput.value) {
    imageInput.value.value = ''
  }
}

// 处理视频上传
const handleVideoUpload = async (event) => {
  const file = event.target.files?.[0]
  if (!file) return
  await uploadMediaFile(file, 'VIDEO')
  if (videoInput.value) {
    videoInput.value.value = ''
  }
}

// 上传媒体文件
const uploadMediaFile = async (file, mediaType) => {
  try {
    uploadDialogVisible.value = true
    uploadProgress.value = 0
    uploadStatus.value = `正在上传${mediaType === 'IMAGE' ? '图片' : '视频'}...`

    const response = await uploadMedia(file, mediaType)

    if (response && response.code === 200 && response.data) {
      uploadProgress.value = 100
      uploadStatus.value = '上传成功'

      const mediaId = response.data?.mediaId
      const fileName = response.data?.fileName
      if (!mediaId || !fileName) {
        ElMessage.error('上传返回数据异常')
        uploadDialogVisible.value = false
        return
      }

      // 插入Markdown语法
      const textarea = getTextarea()
      if (textarea) {
        const start = textarea.selectionStart
        const beforeText = content.value.substring(0, start)
        const afterText = content.value.substring(start)

        let markdown = ''
        if (mediaType === 'IMAGE') {
          markdown = `![${fileName}](/api/media/${mediaId}/download)\n`
        } else {
          markdown = `<video src="/api/media/${mediaId}/download" controls width="50%"></video>\n`
        }

        content.value = beforeText + markdown + afterText
        emit('update:modelValue', content.value)
        // 更新追踪的 media IDs
        previousMediaIds = extractMediaIds(content.value)
      }

      if (uploadTimer) {
        clearTimeout(uploadTimer)
      }
      uploadTimer = setTimeout(() => {
        uploadDialogVisible.value = false
        ElMessage.success('上传成功')
      }, 1000)
    } else {
      ElMessage.error((response?.message) || '上传失败')
      uploadDialogVisible.value = false
    }
  } catch (error) {
    ElMessage.error('上传失败，请稍后重试')
    uploadDialogVisible.value = false
  }
}

// 清空内容
const clearContent = () => {
  if (confirm('确定要清空所有内容吗？')) {
    // 删除所有已上传的媒体
    for (const id of previousMediaIds) {
      deleteMedia(id).catch(err => {
        console.warn(`删除媒体 ${id} 失败:`, err)
      })
    }
    previousMediaIds = new Set()
    content.value = ''
    emit('update:modelValue', '')
  }
}

// 切换全屏
const toggleFullscreen = () => {
  isFullscreen.value = !isFullscreen.value
  if (isFullscreen.value) {
    document.body.style.overflow = 'hidden'
  } else {
    document.body.style.overflow = ''
  }
}

// 清理
onBeforeUnmount(() => {
  if (uploadTimer) {
    clearTimeout(uploadTimer)
    uploadTimer = null
  }
  document.body.style.overflow = ''
})
</script>

<style scoped>
.markdown-editor {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 500px;
  border: 1px solid #dcdfe4;
  border-radius: 4px;
  overflow: hidden;
  background-color: white;
}

.markdown-editor.is-fullscreen {
  position: fixed;
  inset: 0;
  z-index: 9999;
  border-radius: 0;
  border: none;
  height: 100vh !important;
}

.editor-toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  padding: 12px;
  background-color: #f5f7fa;
  border-bottom: 1px solid #dcdfe4;
}

.editor-container {
  flex: 1;
  display: flex;
  overflow: hidden;
}

.editor-pane {
  flex: 1;
  overflow: auto;
  min-width: 0;
}

.editor-divider {
  width: 1px;
  background-color: #dcdfe4;
  flex-shrink: 0;
}

.preview-pane {
  flex: 1;
  overflow: auto;
  min-width: 0;
  display: flex;
  flex-direction: column;
}

.preview-header {
  padding: 6px 16px;
  font-size: 12px;
  color: #64748b;
  background-color: #f5f7fa;
  border-bottom: 1px solid #ebeef5;
  flex-shrink: 0;
  font-weight: 500;
}

.markdown-textarea {
  width: 100%;
  height: 100%;
  padding: 32px 24px;
  border: none;
  font-family: 'Inter', -apple-system, sans-serif;
  font-size: 18px;
  line-height: 1.8;
  color: #1e293b;
  resize: none;
  outline: none;
  box-sizing: border-box;
}

/* 移动端适配 */
@media (max-width: 768px) {
  .editor-container {
    flex-direction: column;
  }

  .editor-divider {
    width: 100%;
    height: 1px;
  }

  .editor-pane,
  .preview-pane {
    flex: none;
    height: 50%;
  }

  .editor-toolbar {
    padding: 8px;
    gap: 4px;
  }

  .editor-toolbar .el-button-group {
    margin-left: 4px !important;
  }

  .editor-toolbar .el-button-group:first-child {
    margin-left: 0 !important;
  }

  .editor-toolbar .el-button {
    min-height: 36px;
    padding: 0 8px;
    font-size: 12px;
  }
}
</style>
