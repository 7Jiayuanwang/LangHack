<template>
  <div class="words-management">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>单词管理</span>
          <div class="header-actions">
            <el-button type="success" @click="showImportDialog">
              Excel批量导入
            </el-button>
            <el-button type="primary" @click="showAddDialog">添加单词</el-button>
          </div>
        </div>
      </template>

      <!-- 搜索栏 -->
      <div class="search-bar">
        <el-form :inline="true" :model="searchForm" class="search-form">
          <el-form-item label="关键词">
            <el-input
              v-model="searchForm.keyword"
              placeholder="单词/定义"
              clearable
              style="width: 200px"
            />
          </el-form-item>
          <el-form-item label="词性">
            <el-select v-model="searchForm.partOfSpeech" placeholder="请选择" clearable>
              <el-option label="名词" value="n." />
              <el-option label="动词" value="v." />
              <el-option label="形容词" value="adj." />
              <el-option label="副词" value="adv." />
              <el-option label="介词" value="prep." />
              <el-option label="连词" value="conj." />
              <el-option label="代词" value="pron." />
              <el-option label="冠词" value="art." />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" @click="loadWords">搜索</el-button>
            <el-button @click="resetSearch">重置</el-button>
          </el-form-item>
        </el-form>
      </div>

      <!-- 单词列表 -->
      <el-table :data="words" stripe v-loading="loading">
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="word" label="单词" width="150">
          <template #default="scope">
            <span class="word-text">{{ scope.row.word }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="pronunciation" label="发音" width="150">
          <template #default="scope">
            <span class="pronunciation-text">{{ scope.row.pronunciation }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="partOfSpeech" label="词性" width="100">
          <template #default="scope">
            <el-tag size="small" type="info">{{ scope.row.partOfSpeech }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="definition" label="定义" show-overflow-tooltip />
        <el-table-column prop="exampleSentence" label="例句" show-overflow-tooltip />
        <el-table-column prop="createdTime" label="创建时间" width="180">
          <template #default="scope">
            {{ formatTime(scope.row.createdTime) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="scope">
            <el-button size="small" @click="editWord(scope.row)">编辑</el-button>
            <el-button size="small" type="danger" @click="deleteWord(scope.row)">
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
      <div class="pagination">
        <el-pagination
          v-model:current-page="pagination.current"
          v-model:page-size="pagination.size"
          :total="pagination.total"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="loadWords"
          @current-change="loadWords"
        />
      </div>
    </el-card>

    <!-- 添加/编辑单词对话框 -->
    <el-dialog
      v-model="dialogVisible"
      :title="isEdit ? '编辑单词' : '添加单词'"
      width="600px"
    >
      <el-form
        ref="wordFormRef"
        :model="wordForm"
        :rules="wordRules"
        label-width="100px"
      >
        <el-form-item label="单词" prop="word">
          <el-input v-model="wordForm.word" placeholder="请输入单词" />
        </el-form-item>
        <el-form-item label="发音" prop="pronunciation">
          <el-input
            v-model="wordForm.pronunciation"
            placeholder="例如: /həˈləʊ/"
          />
        </el-form-item>
        <el-form-item label="词性" prop="partOfSpeech">
          <el-select v-model="wordForm.partOfSpeech" placeholder="请选择词性" style="width: 100%">
            <el-option label="名词 (n.)" value="n." />
            <el-option label="动词 (v.)" value="v." />
            <el-option label="形容词 (adj.)" value="adj." />
            <el-option label="副词 (adv.)" value="adv." />
            <el-option label="介词 (prep.)" value="prep." />
            <el-option label="连词 (conj.)" value="conj." />
            <el-option label="代词 (pron.)" value="pron." />
            <el-option label="冠词 (art.)" value="art." />
            <el-option label="其他" value="other" />
          </el-select>
        </el-form-item>
        <el-form-item label="定义" prop="definition">
          <el-input
            v-model="wordForm.definition"
            type="textarea"
            :rows="3"
            placeholder="请输入单词定义"
          />
        </el-form-item>
        <el-form-item label="例句" prop="exampleSentence">
          <el-input
            v-model="wordForm.exampleSentence"
            type="textarea"
            :rows="3"
            placeholder="请输入例句（可选）"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="dialogVisible = false">取消</el-button>
          <el-button type="primary" @click="saveWord" :loading="saving">确定</el-button>
        </span>
      </template>
    </el-dialog>

    <!-- Excel批量导入对话框 -->
    <el-dialog
      v-model="importDialogVisible"
      title="Excel批量导入单词"
      width="700px"
    >
      <div class="import-content">
        <!-- 模板下载区域 -->
        <el-alert
          title="使用说明"
          type="info"
          :closable="false"
          show-icon
          style="margin-bottom: 20px"
        >
          <div class="import-instructions">
            <p>1. 请先下载Excel模板</p>
            <p>2. 按照模板格式填写单词信息（必填：单词、词性、定义）</p>
            <p>3. 上传填写好的Excel文件进行导入</p>
            <p>4. 支持.xlsx和.xls格式文件</p>
          </div>
        </el-alert>

        <div class="template-download">
          <el-button type="primary" @click="downloadTemplate" :icon="Download">
            下载Excel模板
          </el-button>
        </div>

        <el-divider />

        <!-- 文件上传区域 -->
        <el-form label-width="100px">
          <el-form-item label="选择文件">
            <el-upload
              ref="uploadRef"
              class="upload-demo"
              drag
              :auto-upload="false"
              :limit="1"
              :on-change="handleFileChange"
              :on-exceed="handleExceed"
              :file-list="importFileList"
              accept=".xlsx,.xls"
            >
              <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
              <div class="el-upload__text">
                将文件拖到此处，或<em>点击上传</em>
              </div>
              <template #tip>
                <div class="el-upload__tip">
                  支持.xlsx和.xls格式，文件大小不超过10MB
                </div>
              </template>
            </el-upload>
          </el-form-item>
        </el-form>

        <!-- 导入结果显示 -->
        <div v-if="importResult" class="import-result">
          <el-divider />
          <el-result
            :icon="importResult.success ? 'success' : 'warning'"
            :title="importResult.success ? '导入完成' : '导入完成（部分失败）'"
          >
            <template #sub-title>
              <div class="result-stats">
                <p>成功导入：<span class="success-count">{{ importResult.successCount }}</span> 个单词</p>
                <p v-if="importResult.failCount > 0">
                  导入失败：<span class="fail-count">{{ importResult.failCount }}</span> 个单词
                </p>
              </div>
            </template>
            <template #extra>
              <div v-if="importResult.errors && importResult.errors.length > 0" class="error-details">
                <el-collapse>
                  <el-collapse-item title="查看错误详情" name="1">
                    <el-table :data="importResult.errors" max-height="300" size="small">
                      <el-table-column prop="row" label="行号" width="80" />
                      <el-table-column prop="word" label="单词" width="120" />
                      <el-table-column prop="error" label="错误信息" show-overflow-tooltip />
                    </el-table>
                  </el-collapse-item>
                </el-collapse>
              </div>
            </template>
          </el-result>
        </div>
      </div>

      <template #footer>
        <span class="dialog-footer">
          <el-button @click="closeImportDialog">
            {{ importResult ? '关闭' : '取消' }}
          </el-button>
          <el-button
            v-if="!importResult"
            type="primary"
            @click="handleImport"
            :loading="importing"
            :disabled="!importFile"
          >
            开始导入
          </el-button>
        </span>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Download, UploadFilled } from '@element-plus/icons-vue'
import {
  getWordList,
  createWord,
  updateWord,
  deleteWord as deleteWordApi
} from '@/api/word'
import request from '@/utils/request'

// 响应式数据
const words = ref([])
const dialogVisible = ref(false)
const importDialogVisible = ref(false)
const isEdit = ref(false)
const saving = ref(false)
const loading = ref(false)
const importing = ref(false)
const wordFormRef = ref()
const uploadRef = ref()
const importFile = ref(null)
const importFileList = ref([])
const importResult = ref(null)

// 搜索表单
const searchForm = reactive({
  keyword: '',
  partOfSpeech: ''
})

// 分页
const pagination = reactive({
  current: 1,
  size: 10,
  total: 0
})

// 单词表单
const wordForm = reactive({
  id: null,
  word: '',
  pronunciation: '',
  partOfSpeech: '',
  definition: '',
  exampleSentence: ''
})

// 表单验证规则
const wordRules = {
  word: [
    { required: true, message: '请输入单词', trigger: 'blur' },
    { min: 1, max: 100, message: '单词长度在 1 到 100 个字符', trigger: 'blur' }
  ],
  partOfSpeech: [
    { required: true, message: '请选择词性', trigger: 'change' }
  ],
  definition: [
    { required: true, message: '请输入定义', trigger: 'blur' },
    { max: 500, message: '定义不能超过 500 个字符', trigger: 'blur' }
  ],
  exampleSentence: [
    { max: 500, message: '例句不能超过 500 个字符', trigger: 'blur' }
  ]
}

// 方法
const loadWords = async () => {
  loading.value = true
  try {
    const params = {
      current: pagination.current,
      size: pagination.size,
      ...searchForm
    }
    const response = await getWordList(params)
    words.value = response.data.records || response.data
    pagination.total = response.data.total || 0
  } catch (error) {
    ElMessage.error('加载单词列表失败')
  } finally {
    loading.value = false
  }
}

const resetSearch = () => {
  searchForm.keyword = ''
  searchForm.partOfSpeech = ''
  pagination.current = 1
  loadWords()
}

const showAddDialog = () => {
  isEdit.value = false
  dialogVisible.value = true
  resetWordForm()
}

const editWord = (word) => {
  isEdit.value = true
  dialogVisible.value = true
  Object.assign(wordForm, {
    id: word.id,
    word: word.word,
    pronunciation: word.pronunciation || '',
    partOfSpeech: word.partOfSpeech,
    definition: word.definition,
    exampleSentence: word.exampleSentence || ''
  })
}

const resetWordForm = () => {
  Object.assign(wordForm, {
    id: null,
    word: '',
    pronunciation: '',
    partOfSpeech: '',
    definition: '',
    exampleSentence: ''
  })
  if (wordFormRef.value) {
    wordFormRef.value.clearValidate()
  }
}

const saveWord = async () => {
  if (!wordFormRef.value) return

  await wordFormRef.value.validate(async (valid) => {
    if (valid) {
      saving.value = true
      try {
        const data = {
          word: wordForm.word.trim(),
          pronunciation: wordForm.pronunciation.trim(),
          partOfSpeech: wordForm.partOfSpeech,
          definition: wordForm.definition.trim(),
          exampleSentence: wordForm.exampleSentence?.trim() || ''
        }

        if (isEdit.value) {
          await updateWord(wordForm.id, data)
          ElMessage.success('单词更新成功')
        } else {
          await createWord(data)
          ElMessage.success('单词添加成功')
        }
        dialogVisible.value = false
        loadWords()
      } catch (error) {
        ElMessage.error(error.message || '操作失败')
      } finally {
        saving.value = false
      }
    }
  })
}

const deleteWord = async (word) => {
  try {
    await ElMessageBox.confirm(`确认删除单词 "${word.word}" 吗？`, '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })

    await deleteWordApi(word.id)
    ElMessage.success('单词删除成功')
    loadWords()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error(error.message || '删除失败')
    }
  }
}

const formatTime = (time) => {
  if (!time) return ''
  return new Date(time).toLocaleString()
}

// Excel导入相关方法
const showImportDialog = () => {
  importDialogVisible.value = true
  importFile.value = null
  importFileList.value = []
  importResult.value = null
}

const closeImportDialog = () => {
  importDialogVisible.value = false
  importFile.value = null
  importFileList.value = []
  importResult.value = null
  if (importResult.value && importResult.value.successCount > 0) {
    loadWords()
  }
}

const handleFileChange = (file) => {
  importFile.value = file.raw
  importFileList.value = [file]
}

const handleExceed = () => {
  ElMessage.warning('只能上传一个文件')
}

const downloadTemplate = () => {
  // 创建一个简单的Excel模板数据
  const template = [
    ['单词*', '发音', '词性*', '定义*', '例句'],
    ['hello', '/həˈləʊ/', 'int.', '你好；问候', 'Hello, how are you?'],
    ['world', '/wɜːld/', 'n.', '世界；地球', 'The world is beautiful.'],
    ['example', '/ɪɡˈzɑːmpl/', 'n.', '例子；实例', 'This is an example.']
  ]

  // 创建CSV内容（简化版，实际应用中可以使用xlsx库生成真正的Excel）
  const csvContent = template.map(row => row.join(',')).join('\n')
  const blob = new Blob(['\ufeff' + csvContent], { type: 'text/csv;charset=utf-8;' })
  const link = document.createElement('a')
  const url = URL.createObjectURL(blob)

  link.setAttribute('href', url)
  link.setAttribute('download', '单词导入模板.csv')
  link.style.visibility = 'hidden'
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)

  ElMessage.success('模板下载成功')
}

const handleImport = async () => {
  if (!importFile.value) {
    ElMessage.warning('请选择要导入的文件')
    return
  }

  // 验证文件大小
  const maxSize = 10 * 1024 * 1024 // 10MB
  if (importFile.value.size > maxSize) {
    ElMessage.error('文件大小不能超过10MB')
    return
  }

  // 验证文件格式
  const fileName = importFile.value.name
  const fileExt = fileName.substring(fileName.lastIndexOf('.')).toLowerCase()
  if (fileExt !== '.xlsx' && fileExt !== '.xls') {
    ElMessage.error('只支持.xlsx和.xls格式的文件')
    return
  }

  importing.value = true
  const formData = new FormData()
  formData.append('file', importFile.value)

  try {
    const response = await request({
      url: '/api/words/import',
      method: 'post',
      data: formData,
      headers: {
        'Content-Type': 'multipart/form-data'
      }
    })

    // 处理导入结果
    const result = response.data
    importResult.value = {
      success: result.failCount === 0,
      successCount: result.successCount || 0,
      failCount: result.failCount || 0,
      errors: result.errors || []
    }

    if (result.failCount === 0) {
      ElMessage.success(`成功导入 ${result.successCount} 个单词`)
    } else {
      ElMessage.warning(`导入完成，成功 ${result.successCount} 个，失败 ${result.failCount} 个`)
    }

    // 重新加载单词列表
    if (result.successCount > 0) {
      loadWords()
    }
  } catch (error) {
    ElMessage.error(error.message || '导入失败，请检查文件格式是否正确')
    importResult.value = {
      success: false,
      successCount: 0,
      failCount: 0,
      errors: [{ row: '-', word: '-', error: error.message || '导入失败' }]
    }
  } finally {
    importing.value = false
  }
}

// 生命周期
onMounted(() => {
  loadWords()
})
</script>

<style scoped>
.words-management {
  height: 100%;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.header-actions {
  display: flex;
  gap: 10px;
}

.search-bar {
  margin-bottom: 20px;
}

.search-form {
  display: flex;
  flex-wrap: wrap;
}

.word-text {
  font-weight: 600;
  color: #409eff;
}

.pronunciation-text {
  color: #909399;
  font-style: italic;
}

.pagination {
  margin-top: 20px;
  text-align: right;
}

/* 导入对话框样式 */
.import-content {
  padding: 10px 0;
}

.import-instructions {
  line-height: 1.8;
}

.import-instructions p {
  margin: 5px 0;
}

.template-download {
  text-align: center;
  margin: 20px 0;
}

.upload-demo {
  width: 100%;
}

.import-result {
  margin-top: 20px;
}

.result-stats {
  font-size: 16px;
  line-height: 2;
}

.success-count {
  color: #67c23a;
  font-weight: bold;
  font-size: 18px;
}

.fail-count {
  color: #f56c6c;
  font-weight: bold;
  font-size: 18px;
}

.error-details {
  width: 100%;
  margin-top: 20px;
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}

/* 上传区域样式优化 */
:deep(.el-upload-dragger) {
  padding: 40px;
}

:deep(.el-icon--upload) {
  font-size: 67px;
  color: #409eff;
  margin-bottom: 16px;
}

:deep(.el-upload__text) {
  font-size: 14px;
  color: #606266;
}

:deep(.el-upload__text em) {
  color: #409eff;
  font-style: normal;
}

:deep(.el-upload__tip) {
  margin-top: 7px;
  color: #909399;
  font-size: 12px;
}
</style>
