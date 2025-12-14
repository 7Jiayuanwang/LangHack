<template>
  <div class="texts-management">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>课文管理 - 三级导航</span>
        </div>
      </template>

      <!-- 三级导航结构 -->
      <el-row :gutter="20">
        <!-- 左侧：课程列表 -->
        <el-col :span="6">
          <div class="nav-section">
            <h3>课程列表</h3>
            <el-menu
              :default-active="String(selectedCourseId)"
              @select="handleCourseSelect"
            >
              <el-menu-item
                v-for="course in courses"
                :key="course.id"
                :index="String(course.id)"
              >
                <span>{{ course.name }}</span>
              </el-menu-item>
            </el-menu>
          </div>
        </el-col>

        <!-- 中间：单元列表 -->
        <el-col :span="6">
          <div class="nav-section">
            <div class="content-header">
              <h3>单元列表</h3>
              <el-button
                v-if="selectedCourseId"
                type="primary"
                size="small"
                @click="showAddUnitDialog"
              >
                添加单元
              </el-button>
            </div>
            <el-empty v-if="!selectedCourseId" description="请先选择课程" :image-size="80" />
            <div v-else class="unit-list">
              <div
                v-for="unit in units"
                :key="unit.id"
                class="unit-item"
                :class="{ active: unit.id === selectedUnitId }"
                @click="handleUnitSelect(String(unit.id))"
              >
                <div class="unit-info">
                  <div class="unit-name">{{ unit.name }}</div>
                  <div class="unit-desc">{{ unit.description }}</div>
                </div>
                <div class="unit-actions" @click.stop>
                  <el-button
                    type="primary"
                    size="small"
                    @click="handleEditUnit(unit)"
                    :icon="Edit"
                    link
                  />
                  <el-button
                    type="danger"
                    size="small"
                    @click="handleDeleteUnit(unit)"
                    :icon="Delete"
                    link
                  />
                </div>
              </div>
              <el-empty v-if="units.length === 0" description="暂无单元，请添加" :image-size="80" />
            </div>
          </div>
        </el-col>

        <!-- 右侧：内容列表 -->
        <el-col :span="12">
          <div class="nav-section">
            <div class="content-header">
              <h3>内容列表</h3>
              <el-button
                v-if="selectedUnitId"
                type="primary"
                size="small"
                @click="showUploadDialog"
              >
                导入内容
              </el-button>
            </div>
            <el-empty v-if="!selectedUnitId" description="请先选择单元" :image-size="80" />
            <div v-else class="content-list">
              <div
                v-for="text in texts"
                :key="text.id"
                class="content-item"
                @click="openReader(text)"
              >
                <div class="content-info">
                  <h4>{{ text.title }}</h4>
                  <div class="content-meta">
                    <el-tag size="small" :type="getContentTypeTag(text.contentType).type">
                      {{ getContentTypeTag(text.contentType).label }}
                    </el-tag>
                    <el-tag size="small" type="info">{{ text.difficulty }}</el-tag>
                    <span class="order-num">序号: {{ text.orderNum }}</span>
                  </div>
                </div>
                <div class="content-actions">
                  <el-button
                    type="primary"
                    size="small"
                    @click.stop="handleEditText(text)"
                    :icon="Edit"
                  >
                    编辑
                  </el-button>
                  <el-button
                    type="danger"
                    size="small"
                    @click.stop="handleDeleteText(text)"
                    :icon="Delete"
                  >
                    删除
                  </el-button>
                </div>
              </div>
              <el-empty v-if="texts.length === 0" description="暂无内容，请导入" :image-size="80" />
            </div>
          </div>
        </el-col>
      </el-row>
    </el-card>

    <!-- 单元编辑对话框 -->
    <el-dialog
      v-model="unitDialogVisible"
      :title="isEditUnit ? '编辑单元' : '添加单元'"
      width="500px"
    >
      <el-form :model="unitForm" label-width="100px">
        <el-form-item label="单元名称">
          <el-input v-model="unitForm.name" placeholder="请输入单元名称" />
        </el-form-item>
        <el-form-item label="单元描述">
          <el-input
            v-model="unitForm.description"
            type="textarea"
            :rows="4"
            placeholder="请输入单元描述"
          />
        </el-form-item>
        <el-form-item label="排序序号">
          <el-input-number v-model="unitForm.orderNum" :min="1" :max="100" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="unitDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSaveUnit" :loading="savingUnit">
          保存
        </el-button>
      </template>
    </el-dialog>

    <!-- 上传对话框 -->
    <el-dialog v-model="uploadDialogVisible" title="导入内容" width="500px">
      <el-form :model="uploadForm" label-width="100px">
        <el-form-item label="内容类型">
          <el-radio-group v-model="uploadForm.contentType">
            <el-radio label="ARTICLE">课文</el-radio>
            <el-radio label="EXERCISE">练习题</el-radio>
            <el-radio label="VOCABULARY">词汇</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="自定义标题">
          <el-input
            v-model="uploadForm.customTitle"
            placeholder="留空则使用文件名"
            clearable
          />
        </el-form-item>
        <el-form-item label="排序序号">
          <el-input-number v-model="uploadForm.orderNum" :min="1" :max="100" />
        </el-form-item>
        <el-form-item label="选择文件">
          <el-upload
            ref="uploadRef"
            :auto-upload="false"
            :limit="1"
            :on-change="handleFileChange"
            :file-list="fileList"
            accept=".txt,.doc,.docx"
          >
            <el-button type="primary">选择文档</el-button>
            <template #tip>
              <div class="el-upload__tip">支持 .txt, .doc, .docx 文件</div>
            </template>
          </el-upload>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="uploadDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleUpload" :loading="uploading">
          确定上传
        </el-button>
      </template>
    </el-dialog>

    <!-- 阅读器对话框 -->
    <el-dialog
      v-model="readerVisible"
      :title="currentText.title"
      width="80%"
      class="reader-dialog"
      :fullscreen="readerFullscreen"
    >
      <template #header>
        <div class="reader-header">
          <h2>{{ currentText.title }}</h2>
          <div class="reader-controls">
            <el-button-group>
              <el-button size="small" @click="changeFontSize('small')">小</el-button>
              <el-button size="small" @click="changeFontSize('medium')">中</el-button>
              <el-button size="small" @click="changeFontSize('large')">大</el-button>
            </el-button-group>
            <el-switch
              v-model="nightMode"
              active-text="夜间"
              inactive-text="日间"
              style="margin-left: 10px"
            />
            <el-button
              size="small"
              :icon="readerFullscreen ? 'Minimize' : 'FullScreen'"
              @click="readerFullscreen = !readerFullscreen"
              style="margin-left: 10px"
            >
              {{ readerFullscreen ? '退出全屏' : '全屏' }}
            </el-button>
          </div>
        </div>
      </template>

      <div class="reader-container" :class="{ 'night-mode': nightMode }">
        <div class="reader-content-wrapper">
          <div
            class="reader-content"
            :class="['font-' + fontSize]"
            @click="handleTextClick"
          >
            <p
              v-for="(paragraph, index) in getFormattedContent(currentText.content)"
              :key="index"
            >
              {{ paragraph }}
            </p>
          </div>
          <div class="word-detail-panel" v-if="selectedWord">
            <el-card shadow="never">
              <template #header>
                <h3>{{ selectedWord.word }}</h3>
                <span class="pronunciation">{{ selectedWord.pronunciation }}</span>
              </template>
              <div v-if="loadingWord" v-loading="true" style="height: 100px"></div>
              <div v-else-if="selectedWord.definition">
                <p><strong>词性：</strong>{{ selectedWord.partOfSpeech }}</p>
                <p><strong>释义：</strong>{{ selectedWord.definition }}</p>
                <p v-if="selectedWord.exampleSentence">
                  <strong>例句：</strong>{{ selectedWord.exampleSentence }}
                </p>
              </div>
              <el-empty v-else description="词典中暂无此单词" :image-size="60" />
            </el-card>
          </div>
        </div>
      </div>
    </el-dialog>

    <!-- 编辑对话框 -->
    <el-dialog v-model="editDialogVisible" title="编辑内容" width="70%">
      <el-form :model="editForm" label-width="100px">
        <el-form-item label="标题">
          <el-input v-model="editForm.title" placeholder="请输入标题" />
        </el-form-item>
        <el-form-item label="内容类型">
          <el-radio-group v-model="editForm.contentType">
            <el-radio label="ARTICLE">课文</el-radio>
            <el-radio label="EXERCISE">练习题</el-radio>
            <el-radio label="VOCABULARY">词汇</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="难度">
          <el-radio-group v-model="editForm.difficulty">
            <el-radio label="EASY">简单</el-radio>
            <el-radio label="MEDIUM">中等</el-radio>
            <el-radio label="HARD">困难</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="排序序号">
          <el-input-number v-model="editForm.orderNum" :min="1" :max="100" />
        </el-form-item>
        <el-form-item label="内容">
          <el-input
            v-model="editForm.content"
            type="textarea"
            :rows="15"
            placeholder="请输入内容"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSaveEdit" :loading="saving">
          保存
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Delete, Edit } from '@element-plus/icons-vue'
import { useRoute } from 'vue-router'
import { getCourseList } from '@/api/admin'
import { getUnitsByCourse, createUnit, updateUnit, deleteUnit } from '@/api/unit'
import { getTextsByCourse, importTextFromWord, deleteText, updateText } from '@/api/text'
import { getWordByWord } from '@/api/word'

const route = useRoute()

// 数据
const courses = ref([])
const units = ref([])
const texts = ref([])
const selectedCourseId = ref(null)
const selectedUnitId = ref(null)
const uploadDialogVisible = ref(false)
const uploading = ref(false)
const fileList = ref([])
const uploadForm = reactive({
  file: null,
  contentType: 'ARTICLE',
  orderNum: 1,
  customTitle: ''
})

// 编辑相关
const editDialogVisible = ref(false)
const saving = ref(false)
const editForm = reactive({
  id: null,
  title: '',
  content: '',
  contentType: 'ARTICLE',
  difficulty: 'MEDIUM',
  orderNum: 1,
  unitId: null
})

// 单元编辑相关
const unitDialogVisible = ref(false)
const isEditUnit = ref(false)
const savingUnit = ref(false)
const unitForm = reactive({
  id: null,
  name: '',
  description: '',
  orderNum: 1,
  courseId: null
})

// 阅读器相关
const readerVisible = ref(false)
const readerFullscreen = ref(false)
const currentText = ref({})
const fontSize = ref('medium')
const nightMode = ref(false)
const selectedWord = ref(null)
const loadingWord = ref(false)

// 加载课程列表
const loadCourses = async () => {
  try {
    const res = await getCourseList({ page: 1, size: 100 })
    courses.value = res.data.records
  } catch (error) {
    ElMessage.error('加载课程失败')
  }
}

// 选择课程
const handleCourseSelect = async (courseId) => {
  selectedCourseId.value = parseInt(courseId)
  selectedUnitId.value = null
  texts.value = []
  await loadUnits()
}

// 加载单元列表
const loadUnits = async () => {
  if (!selectedCourseId.value) return
  try {
    const res = await getUnitsByCourse(selectedCourseId.value)
    units.value = res.data
  } catch (error) {
    ElMessage.error('加载单元失败')
  }
}

// 选择单元
const handleUnitSelect = async (unitId) => {
  selectedUnitId.value = parseInt(unitId)
  await loadTexts()
}

// 加载内容列表
const loadTexts = async () => {
  if (!selectedCourseId.value) return
  try {
    const res = await getTextsByCourse(selectedCourseId.value)
    // 过滤当前单元的内容并排序
    texts.value = res.data
      .filter(t => t.unitId === selectedUnitId.value)
      .sort((a, b) => a.orderNum - b.orderNum)
  } catch (error) {
    ElMessage.error('加载内容失败')
  }
}

// 显示上传对话框
const showUploadDialog = () => {
  fileList.value = []
  uploadForm.file = null
  uploadForm.contentType = 'ARTICLE'
  uploadForm.orderNum = texts.value.length + 1
  uploadForm.customTitle = ''
  uploadDialogVisible.value = true
}

// 文件选择
const handleFileChange = (file) => {
  uploadForm.file = file.raw
}

// 上传文件
const handleUpload = async () => {
  if (!uploadForm.file) {
    ElMessage.warning('请选择文件')
    return
  }

  const formData = new FormData()
  formData.append('file', uploadForm.file)
  formData.append('unitId', selectedUnitId.value)
  formData.append('courseId', selectedCourseId.value)
  formData.append('contentType', uploadForm.contentType)
  formData.append('orderNum', uploadForm.orderNum)

  // 添加自定义标题
  if (uploadForm.customTitle && uploadForm.customTitle.trim()) {
    formData.append('customTitle', uploadForm.customTitle.trim())
  }

  uploading.value = true
  try {
    await importTextFromWord(formData)
    ElMessage.success('导入成功')
    uploadDialogVisible.value = false
    loadTexts()
  } catch (error) {
    ElMessage.error(error.message || '导入失败')
  } finally {
    uploading.value = false
  }
}

// 打开阅读器
const openReader = (text) => {
  currentText.value = text
  selectedWord.value = null
  readerVisible.value = true
  fontSize.value = 'medium'
  nightMode.value = false
}

// 修改字体大小
const changeFontSize = (size) => {
  fontSize.value = size
}

// 格式化内容为段落
const getFormattedContent = (content) => {
  if (!content) return []
  return content.split('\n').filter(p => p.trim())
}

// 获取内容类型标签
const getContentTypeTag = (contentType) => {
  const types = {
    'ARTICLE': { label: '课文', type: 'primary' },
    'EXERCISE': { label: '练习题', type: 'success' },
    'VOCABULARY': { label: '词汇', type: 'warning' }
  }
  return types[contentType] || { label: '未知', type: 'info' }
}

// 处理文本点击（查词）
const handleTextClick = async (event) => {
  const selection = window.getSelection()
  const word = selection.toString().trim()

  if (!word) return

  if (!/^[a-zA-Z]+$/.test(word)) {
    ElMessage.warning('请选择一个有效的英文单词')
    return
  }

  loadingWord.value = true
  selectedWord.value = { word: word.toLowerCase() }

  try {
    const res = await getWordByWord(word.toLowerCase())
    selectedWord.value = res.data
  } catch (error) {
    selectedWord.value = {
      word: word.toLowerCase(),
      definition: null
    }
  } finally {
    loadingWord.value = false
  }
}

// 编辑内容
const handleEditText = (text) => {
  editForm.id = text.id
  editForm.title = text.title
  editForm.content = text.content
  editForm.contentType = text.contentType || 'ARTICLE'
  editForm.difficulty = text.difficulty || 'MEDIUM'
  editForm.orderNum = text.orderNum || 1
  editForm.unitId = text.unitId
  editDialogVisible.value = true
}

// 保存编辑
const handleSaveEdit = async () => {
  if (!editForm.title || !editForm.title.trim()) {
    ElMessage.warning('请输入标题')
    return
  }
  if (!editForm.content || !editForm.content.trim()) {
    ElMessage.warning('请输入内容')
    return
  }

  saving.value = true
  try {
    await updateText(editForm.id, {
      title: editForm.title.trim(),
      content: editForm.content.trim(),
      contentType: editForm.contentType,
      difficulty: editForm.difficulty,
      orderNum: editForm.orderNum,
      unitId: editForm.unitId
    })
    ElMessage.success('保存成功')
    editDialogVisible.value = false
    await loadTexts()
  } catch (error) {
    ElMessage.error('保存失败: ' + (error.message || error))
  } finally {
    saving.value = false
  }
}

// 删除内容
const handleDeleteText = async (text) => {
  try {
    await ElMessageBox.confirm(
      `确定要删除《${text.title}》吗？删除后无法恢复！`,
      '删除确认',
      {
        confirmButtonText: '确定删除',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )

    await deleteText(text.id)
    ElMessage.success('删除成功')
    await loadTexts()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('删除失败: ' + (error.message || error))
    }
  }
}

// ========== 单元管理方法 ==========

// 显示添加单元对话框
const showAddUnitDialog = () => {
  isEditUnit.value = false
  unitForm.id = null
  unitForm.name = ''
  unitForm.description = ''
  unitForm.orderNum = units.value.length + 1
  unitForm.courseId = selectedCourseId.value
  unitDialogVisible.value = true
}

// 编辑单元
const handleEditUnit = (unit) => {
  isEditUnit.value = true
  unitForm.id = unit.id
  unitForm.name = unit.name
  unitForm.description = unit.description
  unitForm.orderNum = unit.orderNum
  unitForm.courseId = unit.courseId
  unitDialogVisible.value = true
}

// 保存单元
const handleSaveUnit = async () => {
  if (!unitForm.name || !unitForm.name.trim()) {
    ElMessage.warning('请输入单元名称')
    return
  }

  savingUnit.value = true
  try {
    if (isEditUnit.value) {
      // 更新单元
      await updateUnit(unitForm.id, {
        name: unitForm.name.trim(),
        description: unitForm.description?.trim() || '',
        orderNum: unitForm.orderNum,
        courseId: unitForm.courseId
      })
      ElMessage.success('单元更新成功')
    } else {
      // 创建单元
      await createUnit({
        name: unitForm.name.trim(),
        description: unitForm.description?.trim() || '',
        orderNum: unitForm.orderNum,
        courseId: selectedCourseId.value
      })
      ElMessage.success('单元创建成功')
    }
    unitDialogVisible.value = false
    await loadUnits()
  } catch (error) {
    ElMessage.error('保存失败: ' + (error.message || error))
  } finally {
    savingUnit.value = false
  }
}

// 删除单元
const handleDeleteUnit = async (unit) => {
  try {
    await ElMessageBox.confirm(
      `确定要删除单元《${unit.name}》吗？删除后该单元下的所有内容也会被删除！`,
      '删除确认',
      {
        confirmButtonText: '确定删除',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )

    await deleteUnit(unit.id)
    ElMessage.success('删除成功')
    // 如果删除的是当前选中的单元，清空选择
    if (selectedUnitId.value === unit.id) {
      selectedUnitId.value = null
      texts.value = []
    }
    await loadUnits()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('删除失败: ' + (error.message || error))
    }
  }
}

// 初始化
onMounted(async () => {
  await loadCourses()

  // 检查路由参数，如果有 courseId 则自动选中该课程
  const courseIdParam = route.query.courseId
  if (courseIdParam) {
    const courseId = parseInt(courseIdParam)
    selectedCourseId.value = courseId
    await loadUnits()
  }
})
</script>

<style scoped>
.texts-management {
  height: 100%;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.nav-section {
  border: 1px solid #ebeef5;
  border-radius: 4px;
  padding: 15px;
  height: 600px;
  overflow-y: auto;
}

.nav-section h3 {
  margin: 0 0 15px 0;
  padding-bottom: 10px;
  border-bottom: 2px solid #409eff;
  color: #303133;
}

/* 单元列表样式 */
.unit-list {
  margin-top: 10px;
}

.unit-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px;
  margin-bottom: 8px;
  background: #f5f7fa;
  border-radius: 4px;
  cursor: pointer;
  transition: all 0.3s;
  border: 2px solid transparent;
}

.unit-item:hover {
  background: #e6f7ff;
  transform: translateX(3px);
}

.unit-item.active {
  background: #e6f7ff;
  border-color: #409eff;
}

.unit-info {
  flex: 1;
  min-width: 0;
}

.unit-name {
  font-weight: 600;
  color: #303133;
  margin-bottom: 4px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.unit-desc {
  font-size: 12px;
  color: #909399;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.unit-actions {
  display: flex;
  gap: 5px;
  opacity: 0;
  transition: opacity 0.3s;
}

.unit-item:hover .unit-actions {
  opacity: 1;
}

.content-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 15px;
}

.content-header h3 {
  margin: 0;
}

.content-list {
  margin-top: 10px;
}

.content-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 15px;
  margin-bottom: 10px;
  background: #f5f7fa;
  border-radius: 4px;
  cursor: pointer;
  transition: all 0.3s;
}

.content-item:hover {
  background: #e6f7ff;
  transform: translateX(5px);
}

.content-actions {
  display: flex;
  gap: 10px;
}

.content-info h4 {
  margin: 0 0 8px 0;
  color: #409eff;
}

.content-meta {
  display: flex;
  gap: 10px;
  align-items: center;
}

.order-num {
  font-size: 12px;
  color: #909399;
}

/* 阅读器样式 */
.reader-dialog :deep(.el-dialog__body) {
  padding: 0;
}

.reader-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  width: 100%;
}

.reader-header h2 {
  margin: 0;
  font-size: 20px;
}

.reader-controls {
  display: flex;
  align-items: center;
}

.reader-container {
  min-height: 500px;
  background: #fff;
  transition: all 0.3s;
}

.reader-container.night-mode {
  background: #1e1e1e;
  color: #d4d4d4;
}

.reader-content-wrapper {
  display: flex;
  gap: 20px;
  padding: 30px;
}

.reader-content {
  flex: 1;
  line-height: 2;
  user-select: text;
  cursor: text;
  transition: all 0.3s;
}

.reader-content p {
  margin-bottom: 20px;
  text-indent: 2em;
}

.reader-content.font-small {
  font-size: 14px;
}

.reader-content.font-medium {
  font-size: 16px;
}

.reader-content.font-large {
  font-size: 20px;
}

.night-mode .reader-content {
  color: #d4d4d4;
}

.word-detail-panel {
  width: 300px;
  position: sticky;
  top: 0;
}

.word-detail-panel h3 {
  margin: 0 0 5px 0;
  color: #409eff;
}

.pronunciation {
  color: #909399;
  font-size: 14px;
}

.word-detail-panel p {
  margin: 10px 0;
  line-height: 1.6;
}

.night-mode .word-detail-panel {
  background: #252526;
  color: #d4d4d4;
}
</style>
