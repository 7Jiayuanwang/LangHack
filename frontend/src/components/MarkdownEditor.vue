<template>
  <div class="markdown-editor">
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
        <el-button size="small" :type="previewMode ? 'primary' : ''" @click="togglePreview">
          预览
        </el-button>
        <el-button size="small" @click="clearContent">清空</el-button>
      </el-button-group>
    </div>

    <!-- 编辑区域 -->
    <div class="editor-container">
      <!-- 编辑器 -->
      <div class="editor-pane" v-if="!previewMode">
        <textarea
          v-model="content"
          class="markdown-textarea"
          placeholder="在此输入Markdown内容..."
          @input="handleInput"
        ></textarea>
      </div>

      <!-- 预览区域 -->
      <div class="preview-pane" v-if="previewMode">
        <div class="markdown-preview" v-html="renderedMarkdown"></div>
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
import { ref, computed, watch, onBeforeUnmount, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import { marked } from 'marked'
import DOMPurify from 'dompurify'
import { uploadMedia } from '@/api/media'

const props = defineProps({
  modelValue: {
    type: String,
    default: ''
  }
})

const emit = defineEmits(['update:modelValue'])

const content = ref(props.modelValue)
const previewMode = ref(false)
const uploadDialogVisible = ref(false)
const uploadProgress = ref(0)
const uploadStatus = ref('')
const imageInput = ref(null)
const videoInput = ref(null)

// 追踪上传相关的 setTimeout timers，防止内存泄漏
let uploadTimer = null

// 监听props变化
watch(() => props.modelValue, (newVal) => {
  content.value = newVal
})

// 计算渲染后的Markdown
const renderedMarkdown = computed(() => {
  try {
    if (!content.value) return ''
    const html = marked(content.value)
    // DOMPurify 配置：允许必要的 Markdown 标签和属性
    const config = {
      ALLOWED_TAGS: ['p', 'h1', 'h2', 'h3', 'h4', 'h5', 'h6', 'ul', 'ol', 'li', 'a', 'strong', 'em', 'code', 'pre', 'blockquote', 'img', 'br', 'hr', 'table', 'thead', 'tbody', 'tr', 'th', 'td', 'video', 'source'],
      ALLOWED_ATTR: ['href', 'src', 'alt', 'title', 'width', 'height', 'target', 'controls', 'type'],
      KEEP_CONTENT: true
    }
    return DOMPurify.sanitize(html, config)
  } catch (e) {
    console.error('Markdown 渲染错误:', e)
    // 返回已清理的错误提示（不包含用户输入内容）
    return DOMPurify.sanitize('<p style="color:#f56c6c;">Markdown 渲染失败，请检查语法</p>', {
      ALLOWED_TAGS: ['p'],
      ALLOWED_ATTR: ['style']
    })
  }
})

// 处理输入
const handleInput = () => {
  emit('update:modelValue', content.value)
}

// 插入Markdown语法
const insertMarkdown = async (syntax) => {
  const textarea = document.querySelector('.markdown-textarea')
  if (!textarea || !(textarea instanceof HTMLTextAreaElement)) return

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

  // 等待 DOM 更新后重新获取 textarea 引用并设置光标位置
  await nextTick()
  const updatedTextarea = document.querySelector('.markdown-textarea')
  if (updatedTextarea && updatedTextarea instanceof HTMLTextAreaElement) {
    updatedTextarea.focus()
    updatedTextarea.selectionStart = start + syntax.length
    updatedTextarea.selectionEnd = start + syntax.length + 2
  }
}

// 插入链接
const insertLink = async () => {
  const textarea = document.querySelector('.markdown-textarea')
  if (!textarea || !(textarea instanceof HTMLTextAreaElement)) return

  const start = textarea.selectionStart
  const beforeText = content.value.substring(0, start)
  const afterText = content.value.substring(start)

  content.value = beforeText + '[链接文本](https://example.com)' + afterText
  emit('update:modelValue', content.value)

  // 等待 DOM 更新后重新获取 textarea 引用并设置光标位置
  await nextTick()
  const updatedTextarea = document.querySelector('.markdown-textarea')
  if (updatedTextarea && updatedTextarea instanceof HTMLTextAreaElement) {
    updatedTextarea.focus()
    updatedTextarea.selectionStart = start + 1  // 选中 '[' 后
    updatedTextarea.selectionEnd = start + 5    // 选中 '链接文本'
  }
}

// 插入代码
const insertCode = async () => {
  const textarea = document.querySelector('.markdown-textarea')
  if (!textarea || !(textarea instanceof HTMLTextAreaElement)) return

  const start = textarea.selectionStart
  const beforeText = content.value.substring(0, start)
  const afterText = content.value.substring(start)

  content.value = beforeText + '```\n代码\n```\n' + afterText
  emit('update:modelValue', content.value)

  // 等待 DOM 更新后重新获取 textarea 引用并设置光标位置
  await nextTick()
  const updatedTextarea = document.querySelector('.markdown-textarea')
  if (updatedTextarea && updatedTextarea instanceof HTMLTextAreaElement) {
    updatedTextarea.focus()
    updatedTextarea.selectionStart = start + 4  // 代码块开始后
    updatedTextarea.selectionEnd = start + 6    // 代码文字位置
  }
}

// 插入表格
const insertTable = async () => {
  const textarea = document.querySelector('.markdown-textarea')
  if (!textarea || !(textarea instanceof HTMLTextAreaElement)) return

  const start = textarea.selectionStart
  const beforeText = content.value.substring(0, start)
  const afterText = content.value.substring(start)

  const table = '| 列1 | 列2 | 列3 |\n|-----|-----|-----|\n| 内容 | 内容 | 内容 |\n'
  content.value = beforeText + table + afterText
  emit('update:modelValue', content.value)

  // 等待 DOM 更新后重新获取 textarea 引用并设置光标位置
  await nextTick()
  const updatedTextarea = document.querySelector('.markdown-textarea')
  if (updatedTextarea && updatedTextarea instanceof HTMLTextAreaElement) {
    updatedTextarea.focus()
    updatedTextarea.selectionStart = start  // 表格起始位置
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
  // 清空 input，允许重复上传同一文件
  if (imageInput.value) {
    imageInput.value.value = ''
  }
}

// 处理视频上传
const handleVideoUpload = async (event) => {
  const file = event.target.files?.[0]
  if (!file) return
  await uploadMediaFile(file, 'VIDEO')
  // 清空 input，允许重复上传同一文件
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
      const textarea = document.querySelector('.markdown-textarea')
      if (textarea && textarea instanceof HTMLTextAreaElement) {
        const start = textarea.selectionStart
        const beforeText = content.value.substring(0, start)
        const afterText = content.value.substring(start)

        let markdown = ''
        if (mediaType === 'IMAGE') {
          markdown = `![${fileName}](/api/media/${mediaId}/download)\n`
        } else {
          markdown = `<video src="/api/media/${mediaId}/download" controls width="100%"></video>\n`
        }

        content.value = beforeText + markdown + afterText
        emit('update:modelValue', content.value)
      }

      // 1秒后关闭上传对话框
      // 清理前一个计时器
      if (uploadTimer) {
        clearTimeout(uploadTimer)
      }
      // 设置新计时器
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

// 切换预览模式
const togglePreview = () => {
  previewMode.value = !previewMode.value
}

// 清空内容
const clearContent = () => {
  if (confirm('确定要清空所有内容吗？')) {
    content.value = ''
    emit('update:modelValue', '')
  }
}

// 清理 setTimeout timers 防止内存泄漏
onBeforeUnmount(() => {
  if (uploadTimer) {
    clearTimeout(uploadTimer)
    uploadTimer = null
  }
})
</script>

<style scoped>
.markdown-editor {
  display: flex;
  flex-direction: column;
  height: 100%;
  border: 1px solid #dcdfe4;
  border-radius: 4px;
  overflow: hidden;
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

.editor-pane,
.preview-pane {
  flex: 1;
  overflow: auto;
}

.markdown-textarea {
  width: 100%;
  height: 100%;
  padding: 12px;
  border: none;
  font-family: 'Monaco', 'Menlo', 'Ubuntu Mono', monospace;
  font-size: 14px;
  line-height: 1.6;
  resize: none;
  outline: none;
  box-sizing: border-box; /* 防止 padding 撑出容器宽度 */
}

.markdown-preview {
  padding: 12px;
  font-size: 14px;
  line-height: 1.6;
  overflow-x: auto; /* 防止宽表格/代码块横向溢出 */
  word-break: break-word; /* 长 URL / 单词自动换行 */
}

.markdown-preview h1,
.markdown-preview h2,
.markdown-preview h3 {
  margin: 16px 0 8px 0;
  font-weight: bold;
}

.markdown-preview h1 {
  font-size: 28px;
}

.markdown-preview h2 {
  font-size: 24px;
}

.markdown-preview h3 {
  font-size: 20px;
}

.markdown-preview p {
  margin: 8px 0;
}

.markdown-preview ul,
.markdown-preview ol {
  margin: 8px 0;
  padding-left: 24px;
}

.markdown-preview li {
  margin: 4px 0;
}

.markdown-preview code {
  background-color: #f5f7fa;
  padding: 2px 6px;
  border-radius: 3px;
  font-family: 'Monaco', 'Menlo', 'Ubuntu Mono', monospace;
}

.markdown-preview pre {
  background-color: #f5f7fa;
  padding: 12px;
  border-radius: 4px;
  overflow-x: auto;
  margin: 8px 0;
}

.markdown-preview blockquote {
  border-left: 4px solid #409eff;
  padding-left: 12px;
  margin: 8px 0;
  color: #666;
}

.markdown-preview table {
  border-collapse: collapse;
  width: 100%;
  margin: 8px 0;
}

.markdown-preview table th,
.markdown-preview table td {
  border: 1px solid #dcdfe4;
  padding: 8px;
  text-align: left;
}

.markdown-preview table th {
  background-color: #f5f7fa;
  font-weight: bold;
}

/* 移动端适配 */
@media (max-width: 768px) {
  .editor-toolbar {
    padding: 8px;
    gap: 4px;
  }

  /* 按钮组间距收紧 */
  .editor-toolbar .el-button-group {
    margin-left: 4px !important;
  }

  .editor-toolbar .el-button-group:first-child {
    margin-left: 0 !important;
  }

  /* 工具栏按钮最小触摸高度 */
  .editor-toolbar .el-button {
    min-height: 36px;
    padding: 0 8px;
    font-size: 12px;
  }

  .markdown-preview {
    font-size: 13px;
  }

  /* 移动端代码块横向滚动 */
  .markdown-preview pre {
    max-width: 100%;
    overflow-x: auto;
    -webkit-overflow-scrolling: touch;
  }

  /* 移动端表格横向滚动 */
  .markdown-preview table {
    display: block;
    overflow-x: auto;
    -webkit-overflow-scrolling: touch;
  }
}
</style>
