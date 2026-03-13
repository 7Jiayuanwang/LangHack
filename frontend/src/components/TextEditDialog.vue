<template>
  <el-dialog
    v-model="visible"
    :title="isEdit ? '编辑课文' : '添加课文'"
    width="90%"
    :close-on-click-modal="false"
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
      <el-form-item v-if="formData.contentType === 'MARKDOWN'" label="Markdown内容">
        <MarkdownEditor :modelValue="markdownContent" @update:modelValue="markdownContent = $event" />
      </el-form-item>

      <!-- 其他字段 -->
      <el-form-item label="标签" prop="tags">
        <el-input v-model="formData.tags" placeholder="多个标签用逗号分隔" />
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
      <el-button type="primary" @click="handleSubmit" :loading="submitting">
        {{ isEdit ? '更新' : '创建' }}
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, reactive, computed, watch, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
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

const formData = reactive({
  id: null,
  title: '',
  content: '',
  contentType: 'TEXT',
  difficulty: 'MEDIUM',
  tags: '',
  audioUrl: '',
  isPublic: 1,
  courseId: null,
  unitId: props.unitId
})

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
    formData.audioUrl = props.textData.audioUrl || ''
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
    formData.audioUrl = ''
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
        audioUrl: formData.audioUrl,
        isPublic: formData.isPublic,
        unitId: formData.unitId
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

// 组件挂载时加载课程列表
onMounted(() => {
  if (!props.unitId) {
    loadCourses()
  }
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
</style>
