<template>
  <el-dialog
    v-model="visible"
    :title="isEdit ? '编辑课文' : '添加课文'"
    width="90%"
    :close-on-click-modal="false"
    class="editor-dialog"
    @close="handleClose"
  >
    <el-form
      ref="formRef"
      :model="formData"
      :rules="rules"
      label-width="120px"
      class="text-form"
    >
      <!-- 基本信息 -->
      <el-form-item label="课文标题" prop="title">
        <el-input v-model="formData.title" placeholder="请输入课文标题" />
      </el-form-item>

      <!-- 封面图（优先展示） -->
      <el-form-item label="封面图" prop="coverUrl">
        <div class="cover-section">
          <el-upload
            class="cover-uploader"
            :class="{ 'has-cover': formData.coverUrl }"
            action="/api/media/upload?mediaType=IMAGE"
            :headers="uploadHeaders"
            :show-file-list="false"
            :on-success="handleCoverSuccess"
            :before-upload="beforeCoverUpload"
            :on-error="handleCoverError"
            drag
          >
            <div v-if="formData.coverUrl" class="cover-preview-wrap">
              <img :src="formData.coverUrl" class="cover-preview" />
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
          <div v-if="formData.coverUrl" class="cover-actions">
            <el-button type="primary" link @click="previewCover" size="small">
              <el-icon><ZoomIn /></el-icon> 预览大图
            </el-button>
            <el-button type="danger" link @click="removeCover" size="small">
              <el-icon><Delete /></el-icon> 删除封面
            </el-button>
          </div>
        </div>
      </el-form-item>

      <!-- 课程和单元选择（仅在没有传入unitId时显示） -->
      <el-form-item label="所属课程" v-if="!props.unitId">
        <el-select 
          v-model="formData.courseId" 
          placeholder="请选择课程（可选）"
          clearable
          @change="handleCourseChange"
          style="width: 100%"
        >
          <el-option
            v-for="course in courses"
            :key="course.id"
            :label="course.name"
            :value="course.id"
          />
        </el-select>
      </el-form-item>

      <el-form-item label="所属单元" v-if="!props.unitId">
        <el-select 
          v-model="formData.unitId" 
          placeholder="请选择单元（可选）"
          clearable
          :disabled="!formData.courseId"
          style="width: 100%"
        >
          <el-option
            v-for="unit in units"
            :key="unit.id"
            :label="unit.name"
            :value="unit.id"
          />
        </el-select>
      </el-form-item>

      <el-form-item label="难度" prop="difficulty">
        <el-select v-model="formData.difficulty" placeholder="请选择难度">
          <el-option label="简单" value="EASY" />
          <el-option label="中等" value="MEDIUM" />
          <el-option label="困难" value="HARD" />
        </el-select>
      </el-form-item>

      <!-- 课内/课外分类 -->
      <el-form-item label="文章分类" prop="articleType">
        <el-select v-model="formData.articleType" placeholder="请选择分类">
          <el-option label="课外读物" value="OUTER" />
          <el-option label="课内教材课文" value="INNER" />
        </el-select>
      </el-form-item>

      <!-- 册号（仅课内显示） -->
      <el-form-item v-if="formData.articleType === 'INNER'" label="册号" prop="bookVolume">
        <el-select v-model="formData.bookVolume" placeholder="请选择册号">
          <el-option v-for="v in bookVolumes" :key="v" :label="v" :value="v" />
        </el-select>
      </el-form-item>

      <!-- 单元（仅课内显示） -->
      <el-form-item v-if="formData.articleType === 'INNER'" label="单元" prop="unitNum">
        <el-select v-model="formData.unitNum" placeholder="请选择单元">
          <el-option v-for="n in unitNumbers" :key="n" :label="'Unit ' + n" :value="n" />
        </el-select>
      </el-form-item>

      <el-form-item label="内容格式" prop="contentType">
        <el-radio-group v-model="formData.contentType">
          <el-radio value="TEXT">纯文本</el-radio>
          <el-radio value="MARKDOWN">Markdown</el-radio>
        </el-radio-group>
      </el-form-item>

      <!-- 纯文本内容 -->
      <el-form-item v-if="formData.contentType === 'TEXT'" label="课文内容" prop="content">
        <el-input
          v-model="formData.content"
          type="textarea"
          rows="10"
          placeholder="请输入课文内容"
        />
      </el-form-item>

      <!-- Markdown内容 -->
      <el-form-item v-if="formData.contentType === 'MARKDOWN'" label="Markdown内容" class="md-editor-item">
        <MarkdownEditor :modelValue="markdownContent" @update:modelValue="markdownContent = $event" />
      </el-form-item>

      <!-- 其他字段 -->
      <el-form-item label="标签" prop="tags">
        <el-input v-model="formData.tags" placeholder="多个标签用逗号分隔" />
      </el-form-item>

      <el-form-item label="类别" prop="category">
        <el-select v-model="formData.category" placeholder="请选择类别（可选）" clearable style="width: 100%">
          <el-option label="科技" value="technology" />
          <el-option label="商务" value="business" />
          <el-option label="教育" value="education" />
          <el-option label="真题" value="exam" />
          <el-option label="生活" value="lifestyle" />
          <el-option label="文化" value="culture" />
        </el-select>
      </el-form-item>

      <el-form-item label="音频URL" prop="audioUrl">
        <el-input v-model="formData.audioUrl" placeholder="请输入音频URL" />
      </el-form-item>

      <el-form-item label="是否公开" prop="isPublic">
        <el-switch v-model="formData.isPublic" :active-value="1" :inactive-value="0" />
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="handleClose">取消</el-button>
      <el-button type="info" v-if="isEdit" @click="handlePreview">预览</el-button>
      <el-button type="primary" @click="handleSubmit" :loading="submitting">
        {{ isEdit ? '更新' : '创建' }}
      </el-button>
    </template>
  </el-dialog>

  <!-- 封面大图预览 -->
  <el-dialog
    v-model="coverPreviewVisible"
    title="封面预览"
    width="auto"
    :modal-append-to-body="true"
    :append-to-body="true"
    class="cover-preview-dialog"
  >
    <div class="preview-image-wrap">
      <img :src="formData.coverUrl" alt="封面预览" class="preview-full-image" />
    </div>
  </el-dialog>

  <!-- iframe内容预览 -->
  <el-dialog
    v-model="previewDialogVisible"
    title="课程页预览"
    fullscreen
    :append-to-body="true"
    destroy-on-close
  >
    <iframe v-if="previewUrl" :src="previewUrl" style="width:100%; height:calc(100vh - 80px); border:none;"></iframe>
  </el-dialog>
</template>

<script setup>
import { ref, reactive, computed, watch, onMounted, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Upload, Picture, ZoomIn, Delete } from '@element-plus/icons-vue'
import { getAuthHeader } from '@/utils/auth'
import MarkdownEditor from './MarkdownEditor.vue'
import { createText, updateText, getCourses, getUnitsByCourseId } from '@/api/admin'

const props = defineProps({
  modelValue: {
    type: Boolean,
    required: true
  },
  textData: {
    type: Object,
    default: null,
    validator: (value) => {
      return value === null || (typeof value === 'object' && !Array.isArray(value))
    }
  },
  unitId: {
    type: Number,
    default: null
  }
})

const emit = defineEmits(['update:modelValue', 'success'])

const router = useRouter()

const visible = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val)
})

const isEdit = computed(() => !!props.textData?.id)

const formRef = ref(null)
const submitting = ref(false)
const markdownContent = ref('')
const courses = ref([])
const units = ref([])

const previewDialogVisible = ref(false)
const previewUrl = ref('')

const formData = reactive({
  id: null,
  title: '',
  content: '',
  contentType: 'TEXT',
  difficulty: 'MEDIUM',
  tags: '',
  category: '',
  audioUrl: '',
  coverUrl: '',
  isPublic: 1,
  courseId: null,
  unitId: props.unitId,
  articleType: 'OUTER',
  bookVolume: '',
  unitNum: null
})

const bookVolumes = ['综合教程1', '综合教程2', '综合教程3', '理解当代中国']
const unitNumbers = [1, 2, 3, 4, 5, 6]

const rules = {
  title: [
    { required: true, message: '请输入课文标题', trigger: 'blur' },
    { min: 1, max: 200, message: '标题长度应在1-200字符之间', trigger: 'blur' }
  ],
  difficulty: [
    { required: true, message: '请选择难度', trigger: 'change' }
  ],
  contentType: [
    { required: true, message: '请选择内容格式', trigger: 'change' }
  ]
}

// 监听visible变化
watch(() => visible.value, (newVal) => {
  if (newVal && props.textData) {
    // 对话框打开时，加载数据
    // 确保 textData 存在且有有效的 id
    formData.id = props.textData.id || null
    formData.title = props.textData.title || ''
    formData.content = props.textData.content || ''
    markdownContent.value = props.textData.markdownContent || ''
    formData.contentType = props.textData.contentType || 'TEXT'
    formData.difficulty = props.textData.difficulty || 'MEDIUM'
    formData.tags = props.textData.tags || ''
    formData.category = props.textData.category || ''
    formData.audioUrl = props.textData.audioUrl || ''
    formData.coverUrl = props.textData.coverUrl || ''
    formData.isPublic = props.textData.isPublic != null ? props.textData.isPublic : 1
    formData.courseId = props.textData.courseId || null
    formData.unitId = props.textData.unitId || props.unitId

    // 如果有courseId，加载对应的单元
    if (props.textData.courseId) {
      loadUnitsByCourse(props.textData.courseId)
    }
  } else if (newVal && !props.textData) {
    // 新增模式，清空数据
    formData.id = null
    formData.title = ''
    formData.content = ''
    markdownContent.value = ''
    formData.contentType = 'TEXT'
    formData.difficulty = 'MEDIUM'
    formData.tags = ''
    formData.category = ''
    formData.audioUrl = ''
    formData.coverUrl = ''
    formData.isPublic = 1
    formData.courseId = null
    formData.unitId = props.unitId
  }

  // 加载课程列表（仅在没有传入unitId时）
  if (newVal && !props.unitId) {
    loadCourses()
  }
})

// 加载课程列表
// FIX #1: 完整的响应对象null检查和response.data结构验证
const loadCourses = async () => {
  try {
    const response = await getCourses({ page: 1, size: 100 })

    // 验证响应对象有效性
    if (!response || typeof response !== 'object') {
      console.warn('getCourses 返回无效响应')
      courses.value = []
      return
    }

    if (response && response.code === 200) {
      if (Array.isArray(response.data)) {
        courses.value = response.data
      } else if (response.data && typeof response.data === 'object' && response.data.records) {
        courses.value = Array.isArray(response.data.records) ? response.data.records : []
      } else {
        courses.value = []
      }
    } else {
      courses.value = []
    }
  } catch (error) {
    console.error('加载课程列表失败:', error)
    courses.value = []
  }
}

// 处理课程变化
const handleCourseChange = async (courseId) => {
  formData.unitId = null
  units.value = []
  
  if (courseId) {
    await loadUnitsByCourse(courseId)
  }
}

// 根据课程ID加载单元
// FIX #2: 添加 response.data 的null检查和完整的对象验证
const loadUnitsByCourse = async (courseId) => {
  try {
    const response = await getUnitsByCourseId(courseId)
    
    // 验证响应对象有效性
    if (!response || typeof response !== 'object') {
      console.warn('getUnitsByCourseId 返回无效响应')
      units.value = []
      return
    }

    // 仅在响应成功且 response.data 存在时才处理
    if (response && response.code === 200 && response.data) {
      units.value = Array.isArray(response.data) ? response.data : []
    } else {
      units.value = []
    }
  } catch (error) {
    console.error('加载单元列表失败:', error)
    units.value = []
  }
}

// 上传相关
const uploadHeaders = computed(() => {
  const headers = {}
  const auth = getAuthHeader()
  if (auth) {
    headers['Authorization'] = auth
  }
  return headers
})

const beforeCoverUpload = (rawFile) => {
  const allowedTypes = ['image/jpeg', 'image/png', 'image/gif', 'image/webp']
  if (!allowedTypes.includes(rawFile.type)) {
    ElMessage.error('仅支持 JPG/PNG/GIF/WebP 格式的图片')
    return false
  }
  if (rawFile.size / 1024 / 1024 > 10) {
    ElMessage.error('图片大小不能超过 10MB')
    return false
  }
  return true
}

const handleCoverSuccess = (response) => {
  if (response.code === 200) {
    const mediaId = response.data.mediaId
    formData.coverUrl = `/api/media/${mediaId}/download`
    ElMessage.success('封面上传成功')
  } else {
    ElMessage.error(response.message || '上传失败')
  }
}

const handleCoverError = (err) => {
  console.error('Upload error:', err)
  ElMessage.error('文件上传失败，请检查网络或登录状态')
}

const removeCover = () => {
  formData.coverUrl = ''
}

const coverPreviewVisible = ref(false)
const previewCover = () => {
  coverPreviewVisible.value = true
}

// 提交表单
const handleSubmit = async () => {
  if (!formRef.value) return

  await formRef.value.validate(async (valid) => {
    if (!valid) return

    // 验证内容不为空
    if (formData.contentType === 'TEXT' && (!formData.content || !formData.content.trim())) {
      ElMessage.error('请输入课文内容')
      return
    }
    if (formData.contentType === 'MARKDOWN' && (!markdownContent.value || !markdownContent.value.trim())) {
      ElMessage.error('请输入Markdown内容')
      return
    }

    submitting.value = true
    try {
      const data = {
        title: formData.title,
        content: formData.contentType === 'TEXT' ? formData.content : '',
        markdownContent: formData.contentType === 'MARKDOWN' ? markdownContent.value : '',
        contentType: formData.contentType,
        difficulty: formData.difficulty,
        tags: formData.tags,
        category: formData.category,
        audioUrl: formData.audioUrl,
        coverUrl: formData.coverUrl,
        isPublic: formData.isPublic,
        unitId: formData.unitId,
        articleType: formData.articleType,
        bookVolume: formData.bookVolume,
        unitNum: formData.unitNum
      }

      if (isEdit.value) {
        await updateText(formData.id, data)
        ElMessage.success('课文更新成功')
      } else {
        await createText(data)
        ElMessage.success('课文创建成功')
      }

      emit('success')
      visible.value = false
    } catch (error) {
      ElMessage.error('操作失败，请稍后重试')
    } finally {
      submitting.value = false
    }
  })
}

// 关闭对话框
const handleClose = () => {
  visible.value = false
}

// 预览课文
const handlePreview = () => {
  if (formData.id) {
    previewUrl.value = router.resolve({ path: '/admin/text-preview', query: { textId: formData.id, from: 'admin' } }).href
    previewDialogVisible.value = true
  }
}

const handleMessage = (e) => {
  if (e.data && e.data.type === 'close-preview') {
    previewDialogVisible.value = false
  }
}

// 组件挂载时加载课程列表
onMounted(() => {
  if (!props.unitId) {
    loadCourses()
  }
  window.addEventListener('message', handleMessage)
})

onBeforeUnmount(() => {
  window.removeEventListener('message', handleMessage)
})
</script>

<style scoped>
.text-form {
  max-height: 70vh;
  overflow-y: auto;
}

:deep(.el-form-item) {
  margin-bottom: 20px;
}

:deep(.markdown-editor) {
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

/* 占位符（未上传时） */
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

:deep(.cover-uploader .el-upload-dragger:hover) .upload-icon {
  color: var(--el-color-primary);
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

/* 已上传预览 */
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

/* 操作按钮行 */
.cover-actions {
  display: flex;
  gap: 12px;
}

/* 大图预览弹窗 */
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
