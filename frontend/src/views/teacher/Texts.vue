<template>
  <div class="texts-management">
    <!-- 搜索栏 + 操作按钮 -->
    <div class="search-bar">
      <el-form :inline="true" :model="searchForm" class="search-form">
        <el-form-item label="关键词">
          <el-input
            v-model="searchForm.keyword"
            placeholder="搜索标题或内容"
            clearable
            style="width: 200px"
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="难度">
          <el-select v-model="searchForm.difficulty" placeholder="选择难度" clearable style="width: 120px">
            <el-option label="简单" value="EASY" />
            <el-option label="中等" value="MEDIUM" />
            <el-option label="困难" value="HARD" />
          </el-select>
        </el-form-item>
        <el-form-item label="分类">
          <el-select v-model="searchForm.articleType" placeholder="全部" clearable style="width: 130px" @change="handleSearch">
            <el-option label="课内教材" value="INNER" />
            <el-option label="课外读物" value="OUTER" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="searchForm.articleType === 'INNER'" label="册号">
          <el-select v-model="searchForm.bookVolume" placeholder="全部" clearable style="width: 130px" @change="handleSearch">
            <el-option v-for="v in bookVolumes" :key="v" :label="v" :value="v" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="searchForm.articleType === 'INNER'" label="单元">
          <el-select v-model="searchForm.unitNum" placeholder="全部" clearable style="width: 100px" @change="handleSearch">
            <el-option v-for="n in unitNumbers" :key="n" :label="'Unit ' + n" :value="n" />
          </el-select>
        </el-form-item>
        <el-form-item label="标签">
          <el-select v-model="tagFilter" multiple collapse-tags placeholder="标签筛选" clearable style="width: 200px" @change="handleSearch">
            <el-option v-for="t in availableTags" :key="t.id" :label="t.name" :value="t.name" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">
            <el-icon><Search /></el-icon>
            搜索
          </el-button>
        </el-form-item>
      </el-form>
      <div class="search-actions">
        <el-button type="primary" @click="showAddDialog">
          <el-icon><Plus /></el-icon>
          添加课文
        </el-button>
        <el-button @click="loadTexts">
          <el-icon><Refresh /></el-icon>
          刷新
        </el-button>
      </div>
    </div>

    <!-- 统计信息 -->
    <div class="stats-cards">
      <div class="stat-card" :class="{ active: activeStatFilter === 'EASY' }" @click="handleStatClick('EASY')">
        <div class="stat-value">{{ stats.totalEasy || 0 }}</div>
        <div class="stat-label">简单课文</div>
      </div>
      <div class="stat-card" :class="{ active: activeStatFilter === 'MEDIUM' }" @click="handleStatClick('MEDIUM')">
        <div class="stat-value">{{ stats.totalMedium || 0 }}</div>
        <div class="stat-label">中等课文</div>
      </div>
      <div class="stat-card" :class="{ active: activeStatFilter === 'HARD' }" @click="handleStatClick('HARD')">
        <div class="stat-value">{{ stats.totalHard || 0 }}</div>
        <div class="stat-label">困难课文</div>
      </div>
      <div class="stat-card" :class="{ active: activeStatFilter === 'FEATURED' }" @click="handleStatClick('FEATURED')">
        <div class="stat-value">{{ stats.totalFeatured || 0 }}</div>
        <div class="stat-label">推荐课文</div>
      </div>
      <div class="stat-card" :class="{ active: activeStatFilter === null }" @click="handleStatClick(null)">
        <div class="stat-value">{{ stats.totalTexts || 0 }}</div>
        <div class="stat-label">总课文数</div>
      </div>
    </div>

    <!-- 课文列表 -->
    <div class="texts-table">
      <el-table
        v-loading="loading"
        :data="texts"
        stripe
        border
        style="width: 100%"
        empty-text="暂无课文数据"
      >
        <el-table-column prop="title" label="标题" min-width="200" show-overflow-tooltip />
        <el-table-column prop="content" label="内容预览" min-width="300" show-overflow-tooltip>
          <template #default="{ row }">
            <div class="content-preview">
              {{ getContentPreview(row.contentType === 'MARKDOWN' ? (row.markdownContent || row.content) : row.content) }}
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="difficulty" label="难度" width="100">
          <template #default="{ row }">
            <el-tag :type="getDifficultyType(row.difficulty)">
              {{ getDifficultyText(row.difficulty) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="isFeatured" label="推荐" width="80" align="center">
          <template #default="{ row }">
            <el-switch
              :model-value="isTextFeatured(row)"
              @change="toggleFeatured(row, $event)"
              :loading="row._featuredLoading"
              :disabled="row.status !== 'APPROVED'"
            />
          </template>
        </el-table-column>
        <el-table-column label="优先级" width="110" align="center">
          <template #default="{ row }">
            <el-input-number
              v-if="isTextFeatured(row)"
              v-model="row.featuredPriority"
              :min="0"
              :max="100"
              :step="1"
              size="small"
              controls-position="right"
              style="width: 90px"
              :disabled="row._priorityLoading"
              @change="(val) => updatePriority(row, val)"
              @blur="() => updatePriority(row, row.featuredPriority)"
            />
            <span v-else class="priority-na">—</span>
          </template>
        </el-table-column>
        <el-table-column prop="audioUrl" label="音频" width="120">
          <template #default="{ row }">
            <el-button
              v-if="row.audioUrl"
              type="primary"
              size="small"
              text
              @click="playAudio(row.audioUrl)"
            >
              播放
            </el-button>
            <span v-else class="no-audio">无音频</span>
          </template>
        </el-table-column>
        <el-table-column prop="createdTime" label="创建时间" width="180">
          <template #default="{ row }">
            {{ formatTime(row.createdTime) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="240" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" size="small" @click="viewText(row)">
              查看
            </el-button>
            <el-button type="success" size="small" @click="editText(row)">
              编辑
            </el-button>
            <el-button type="danger" size="small" @click="deleteText(row)">
              删除
            </el-button>
            <el-dropdown
              trigger="click"
              :disabled="row._regenLoading || row._requizLoading"
              @command="(cmd) => handleMoreCommand(cmd, row)"
            >
              <el-button
                size="small"
                circle
                :icon="MoreFilled"
                :loading="row._regenLoading || row._requizLoading"
                class="more-action-btn"
                title="更多操作"
              />
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item command="regen-open" :icon="Refresh">
                    重新改写
                  </el-dropdown-item>
                  <el-dropdown-item command="requiz" :icon="Edit">
                    重新出题
                  </el-dropdown-item>
                  <el-dropdown-item command="edit-quiz" :icon="Document">
                    编辑题目
                  </el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
      <div class="pagination">
        <el-pagination
          v-model:current-page="pagination.current"
          v-model:page-size="pagination.size"
          :page-sizes="[10, 20, 50, 100]"
          :total="pagination.total"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="handleSizeChange"
          @current-change="handleCurrentChange"
        />
      </div>
    </div>

    <!-- 添加/编辑课文对话框 -->
    <TextEditDialog
      v-model="editDialogVisible"
      :text-data="selectedText"
      :unit-id="selectedUnitId"
      @success="loadTexts"
    />

    <!-- 查看课文对话框 -->
    <el-dialog
      v-model="viewDialogVisible"
      title="课文详情"
      width="min(700px, 90%)"
    >
      <div class="text-detail">
        <h3>{{ currentText.title }}</h3>
        <div v-if="currentText.coverUrl" class="text-cover-preview">
          <img :src="currentText.coverUrl" alt="封面图预览" />
        </div>
        <div class="text-meta">
          <el-tag :type="getDifficultyType(currentText.difficulty)">
            {{ getDifficultyText(currentText.difficulty) }}
          </el-tag>
          <span class="create-time">创建时间：{{ formatTime(currentText.createdTime) }}</span>
        </div>
        <div class="text-content">
          <MarkdownRenderer v-if="currentText.contentType === 'MARKDOWN'" :markdown="currentText.markdownContent || currentText.content || ''" />
          <pre v-else>{{ currentText.content }}</pre>
        </div>
        <div v-if="currentText.audioUrl" class="text-audio">
          <h4>音频</h4>
          <el-button type="primary" @click="playAudio(currentText.audioUrl)">
            <el-icon><VideoPlay /></el-icon>
            播放音频
          </el-button>
        </div>
      </div>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="viewDialogVisible = false">关闭</el-button>
          <el-button type="success" @click="editText(currentText)">编辑</el-button>
        </span>
      </template>
    </el-dialog>

    <!-- 重新生成改写文章对话框 -->
    <el-dialog
      v-model="regenDialogVisible"
      title="重新生成改写文章"
      width="min(520px, 92%)"
      :close-on-click-modal="false"
      append-to-body
    >
      <div class="regen-dialog">
        <div class="regen-target">
          <span class="regen-target-label">目标课文</span>
          <span class="regen-target-title">{{ regenTarget?.title }}</span>
        </div>
        <el-alert
          type="warning"
          :closable="false"
          show-icon
          title="该操作会调用 AI 重新生成所选版本并覆盖现有改写内容，且需要异步等待数分钟。"
        />
        <div class="regen-version">
          <div class="regen-section-title">选择要重新生成的版本</div>
          <el-radio-group v-model="regenVersion" class="regen-radio-group">
            <el-radio-button label="CET4">CET-4</el-radio-button>
            <el-radio-button label="CET6">CET-6</el-radio-button>
            <el-radio-button label="ACADEMIC">学术</el-radio-button>
            <el-radio-button label="PARALLEL">中英对照</el-radio-button>
            <el-radio-button label="ALL">全部</el-radio-button>
          </el-radio-group>
        </div>
      </div>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="regenDialogVisible = false" :disabled="regenSubmitting">取消</el-button>
          <el-button
            type="primary"
            :loading="regenSubmitting"
            @click="confirmRegenerate"
          >
            确认重新生成
          </el-button>
        </span>
      </template>
    </el-dialog>

    <!-- 编辑题目对话框 -->
    <el-dialog
      v-model="editQuizDialogVisible"
      title="编辑题目"
      width="min(900px, 95%)"
      :close-on-click-modal="false"
      append-to-body
      top="3vh"
    >
      <div class="edit-quiz-dialog">
        <div class="edit-quiz-header">
          <span class="edit-quiz-target">课文：{{ editQuizTarget?.title }}</span>
          <el-button type="primary" size="small" @click="addQuestion">
            <el-icon><Plus /></el-icon> 添加题目
          </el-button>
        </div>

        <div class="edit-quiz-empty" v-if="editQuizQuestions.length === 0">
          <el-empty description="暂无题目，点击上方按钮添加" :image-size="80" />
        </div>

        <div class="edit-quiz-list" v-else>
          <div
            v-for="(q, qIdx) in editQuizQuestions"
            :key="qIdx"
            class="edit-quiz-item"
          >
            <div class="edit-quiz-item-header">
              <span class="edit-quiz-item-num">题目 {{ qIdx + 1 }}</span>
              <el-button type="danger" size="small" text @click="removeQuestion(qIdx)">
                删除
              </el-button>
            </div>

            <el-form label-width="70px" size="small">
              <el-form-item label="题型">
                <el-input v-model="q.questionType" placeholder="如：主旨大意题、细节信息题" />
              </el-form-item>
              <el-form-item label="题目">
                <el-input v-model="q.question" type="textarea" :rows="2" placeholder="输入题目内容" />
              </el-form-item>
              <el-form-item label="解析">
                <el-input v-model="q.analysis" type="textarea" :rows="2" placeholder="输入题目解析" />
              </el-form-item>
            </el-form>

            <div class="edit-quiz-options">
              <div class="edit-quiz-options-header">
                <span>选项</span>
                <el-button size="small" text @click="addOption(q)">添加选项</el-button>
              </div>
              <div
                v-for="(opt, optIdx) in q.options"
                :key="optIdx"
                class="edit-quiz-option-row"
              >
                <el-input
                  v-model="opt.optionKey"
                  style="width: 60px"
                  placeholder="A"
                  maxlength="2"
                />
                <el-input
                  v-model="opt.optionContent"
                  style="flex: 1"
                  placeholder="选项内容"
                />
                <el-radio
                  v-model="q._correctKey"
                  :value="opt.optionKey"
                  @change="(val) => { q._correctKey = val }"
                >
                  正确
                </el-radio>
                <el-button type="danger" size="small" text @click="removeOption(q, optIdx)">
                  删除
                </el-button>
              </div>
            </div>
          </div>
        </div>
      </div>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="editQuizDialogVisible = false">取消</el-button>
          <el-button type="primary" :loading="editQuizSaving" @click="saveQuizQuestions">
            保存题目
          </el-button>
        </span>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
// 注意：此组件为 ContentManagement.vue 的嵌入式子组件，通过 v-show 在「内容管理 → 文章管理」Tab 中渲染。
// 原独立路由 /admin/texts 已移除，所有文章管理功能统一走 ContentManagement。
// 如需修改文章管理逻辑，编辑此文件即可，ContentManagement 会自动同步。
import { ref, reactive, onMounted, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Search, RefreshLeft, Refresh, VideoPlay, MoreFilled, Edit, Document } from '@element-plus/icons-vue'
import * as adminApi from '@/api/admin'
import { generateAiQuiz } from '@/api/aiQuiz'
import TextEditDialog from '@/components/TextEditDialog.vue'
import MarkdownRenderer from '@/components/MarkdownRenderer.vue'

// 响应式数据
const texts = ref([])
const stats = ref({})
const loading = ref(false)
const viewDialogVisible = ref(false)
const currentText = ref({})
const editDialogVisible = ref(false)
const selectedText = ref(null)
const selectedUnitId = ref(null)
const router = useRouter()

// 搜索表单
const searchForm = reactive({
  keyword: '',
  difficulty: '',
  articleType: '',
  bookVolume: '',
  unitNum: null
})
const tagFilter = ref([])
const availableTags = ref([])
const activeStatFilter = ref(null) // null | 'EASY' | 'MEDIUM' | 'HARD' | 'FEATURED'

const bookVolumes = ['综合教程1', '综合教程2', '综合教程3', '综合教程4']
const unitNumbers = [1, 2, 3, 4, 5, 6]

// 分页数据
const pagination = reactive({
  current: 1,
  size: 20,
  total: 0
})

// 方法定义
const loadCourses = async () => {
  try {
    const response = await adminApi.getCourses({
      page: 1,
      size: 100 // 获取所有课程
    })
    if (response.code === 200) {
      // FIX: 增强响应数据防御
      return Array.isArray(response.data?.records) ? response.data.records : (Array.isArray(response.data) ? response.data : [])
    }
  } catch (error) {
    ElMessage.error('加载课程列表失败')
  }
}

const loadStats = async () => {
  try {
    const response = await adminApi.getTextStats()
    if (response.code === 200) {
      stats.value = (response.data && typeof response.data === 'object') ? response.data : {}
    }
  } catch (error) {
    console.error('Load stats error:', error)
    stats.value = {}
  }
}

const loadTexts = async () => {
  loading.value = true
  try {
    const params = {
      page: pagination.current,
      size: pagination.size
    }

    if (searchForm.keyword) {
      params.keyword = searchForm.keyword
    }
    // 统计卡片筛选优先于搜索表单的难度筛选
    if (activeStatFilter.value === 'FEATURED') {
      params.featured = 1
    } else if (activeStatFilter.value) {
      params.difficulty = activeStatFilter.value
    } else if (searchForm.difficulty) {
      params.difficulty = searchForm.difficulty
    }
    if (tagFilter.value && tagFilter.value.length > 0) {
      params.tag = tagFilter.value.join(',')
    }
    if (searchForm.articleType) {
      params.articleType = searchForm.articleType
    }
    if (searchForm.bookVolume) {
      params.bookVolume = searchForm.bookVolume
    }
    if (searchForm.unitNum) {
      params.unitNum = searchForm.unitNum
    }

    const response = await adminApi.getTexts(params)

    if (response.code === 200) {
      // FIX: 增强响应数据防御
      texts.value = ((response.data?.records && Array.isArray(response.data.records)) ? response.data.records : [])
      pagination.total = (typeof response.data?.total === 'number') ? response.data.total : 0
    }
  } catch (error) {
    ElMessage.error('加载课文列表失败')
  } finally {
    loading.value = false
  }
  loadStats()
}

const handleStatClick = (filter) => {
  if (activeStatFilter.value === filter) {
    activeStatFilter.value = null
  } else {
    activeStatFilter.value = filter
  }
  pagination.current = 1
  loadTexts()
}

const handleSearch = () => {
  activeStatFilter.value = null
  pagination.current = 1
  loadTexts()
}

const resetSearch = () => {
  searchForm.keyword = ''
  searchForm.difficulty = ''
  pagination.current = 1
  loadTexts()
}

const handleSizeChange = (size) => {
  pagination.size = size
  pagination.current = 1
  loadTexts()
}

const handleCurrentChange = (page) => {
  pagination.current = page
  loadTexts()
}

const normalizeFeaturedValue = (value) => {
  if (value === true || value === 1 || value === '1') return 1
  if (typeof value === 'string' && value.trim().toLowerCase() === 'true') return 1
  return 0
}

const isTextFeatured = (row) => normalizeFeaturedValue(row?.isFeatured) === 1

const toggleFeatured = async (row, nextValue) => {
  const targetFeatured = nextValue === true || nextValue === 1 ? 1 : 0
  row._featuredLoading = true
  try {
    const response = await adminApi.toggleTextFeatured(row.id, targetFeatured)
    if (response.code === 200) {
      row.isFeatured = targetFeatured
      ElMessage.success(targetFeatured === 1 ? '已设为推荐' : '已取消推荐')
      loadStats()
    } else {
      row.isFeatured = 1 - targetFeatured
      ElMessage.error(response.message || '操作失败')
    }
  } catch (error) {
    row.isFeatured = 1 - targetFeatured
  } finally {
    row._featuredLoading = false
  }
}

const updatePriority = async (row, val) => {
  // val 可能来自 @change（数字）或 @blur（通过 row.featuredPriority 传入的数字）
  // 统一转为数字，防止非数字类型混入
  const rawVal = (val !== null && val !== undefined && typeof val === 'number') ? val : row.featuredPriority
  const priority = (rawVal == null || isNaN(rawVal)) ? 0 : Math.max(0, Math.min(100, rawVal))

  // 防止 @change 和 @blur 在点击加减按钮时双重触发
  if (row._lastSavedPriority === priority) return
  row._lastSavedPriority = priority

  row._priorityLoading = true
  try {
    const response = await adminApi.setTextFeaturedPriority(row.id, priority)
    if (response.code === 200) {
      row.featuredPriority = priority
      ElMessage.success('优先级已更新')
    } else {
      ElMessage.error(response.message || '更新失败')
    }
  } catch (error) {
    ElMessage.error('更新失败')
  } finally {
    row._priorityLoading = false
  }
}

const showAddDialog = () => {
  selectedText.value = null
  selectedUnitId.value = null  // 清空 unitId，确保显示课程和单元选择器
  editDialogVisible.value = true
}

const viewText = (text) => {
  currentText.value = { ...text, isFeatured: normalizeFeaturedValue(text?.isFeatured) }
  viewDialogVisible.value = true
}

const editText = (text) => {
  selectedText.value = { ...text, isFeatured: normalizeFeaturedValue(text?.isFeatured) }
  selectedUnitId.value = text.unitId
  editDialogVisible.value = true
}

const deleteText = (text) => {
  ElMessageBox.confirm(
    `确定要删除课文 "${text.title}" 吗？此操作不可恢复！`,
    '删除确认',
    {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    }
  )
    .then(async () => {
      try {
        const response = await adminApi.deleteText(text.id)

        if (response.code === 200) {
          ElMessage.success('删除成功')
          loadTexts()
          loadStats()
        } else {
          ElMessage.error(response.message || '删除失败')
        }
      } catch (error) {
        ElMessage.error('删除失败')
      }
    })
    .catch(() => {
      // 用户取消删除
    })
}

const VERSION_LABELS = {
  CET4: 'CET-4 版本',
  CET6: 'CET-6 版本',
  ACADEMIC: '学术版本',
  PARALLEL: '中英对照',
  ALL: '全部版本'
}

// "重新生成改写文章"弹窗状态
const regenDialogVisible = ref(false)
const regenTarget = ref(null)
const regenVersion = ref('ALL')
const regenSubmitting = ref(false)

// "编辑题目"弹窗状态
const editQuizDialogVisible = ref(false)
const editQuizTarget = ref(null)
const editQuizQuestions = ref([])
const editQuizSaving = ref(false)

const handleMoreCommand = async (command, row) => {
  if (command === 'regen-open') {
    openRegenDialog(row)
  } else if (command === 'requiz') {
    try {
      row._requizLoading = true
      const resp = await generateAiQuiz(row.id, 5, true)
      if (resp.code === 200) {
        ElMessage.success('已提交重新出题任务')
      } else {
        ElMessage.error(resp.message || '重新出题失败')
      }
    } catch (e) {
      ElMessage.error('重新出题失败：' + (e.message || '网络异常'))
    } finally {
      row._requizLoading = false
    }
  } else if (command === 'edit-quiz') {
    openEditQuizDialog(row)
  }
}

const openRegenDialog = (row) => {
  regenTarget.value = row
  regenVersion.value = 'ALL'
  regenDialogVisible.value = true
}

const confirmRegenerate = async () => {
  const row = regenTarget.value
  if (!row) return
  const version = regenVersion.value
  regenSubmitting.value = true
  row._regenLoading = true
  try {
    const response = await adminApi.regenerateTextVersions(row.id, version)
    if (response.code === 200) {
      ElMessage.success(response.message || `已提交「${VERSION_LABELS[version] || version}」重新生成，稍后刷新查看`)
      regenDialogVisible.value = false
    } else {
      ElMessage.error(response.message || '提交失败')
    }
  } catch (error) {
    ElMessage.error('提交失败')
  } finally {
    regenSubmitting.value = false
    row._regenLoading = false
  }
}

// 编辑题目相关函数
import { getExercisesByTextId } from '@/api/exercise'

const openEditQuizDialog = async (row) => {
  editQuizTarget.value = row
  editQuizDialogVisible.value = true
  editQuizQuestions.value = []

  try {
    const resp = await getExercisesByTextId(row.id)
    const data = resp?.data || resp
    if (Array.isArray(data) && data.length > 0) {
      editQuizQuestions.value = data.map(q => ({
        id: q.id,
        question: q.question || '',
        questionType: q.questionType || '选择题',
        analysis: q.analysis || '',
        _correctKey: (q.options || []).find(o => o.isCorrect === 1)?.optionKey || '',
        options: (q.options || []).map(o => ({
          id: o.id,
          optionKey: o.optionKey || '',
          optionContent: o.optionContent || '',
          isCorrect: o.isCorrect || 0
        }))
      }))
    }
  } catch (e) {
    console.warn('加载题目失败:', e)
  }
}

const addQuestion = () => {
  editQuizQuestions.value.push({
    id: null,
    question: '',
    questionType: '选择题',
    analysis: '',
    _correctKey: 'A',
    options: [
      { id: null, optionKey: 'A', optionContent: '', isCorrect: 0 },
      { id: null, optionKey: 'B', optionContent: '', isCorrect: 0 },
      { id: null, optionKey: 'C', optionContent: '', isCorrect: 0 },
      { id: null, optionKey: 'D', optionContent: '', isCorrect: 0 }
    ]
  })
}

const removeQuestion = (idx) => {
  editQuizQuestions.value.splice(idx, 1)
}

const addOption = (q) => {
  const keys = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ'
  const usedKeys = q.options.map(o => o.optionKey)
  const nextKey = keys.split('').find(k => !usedKeys.includes(k)) || String(q.options.length + 1)
  q.options.push({ id: null, optionKey: nextKey, optionContent: '', isCorrect: 0 })
}

const removeOption = (q, idx) => {
  q.options.splice(idx, 1)
}

const saveQuizQuestions = async () => {
  // 验证
  for (let i = 0; i < editQuizQuestions.value.length; i++) {
    const q = editQuizQuestions.value[i]
    if (!q.question.trim()) {
      ElMessage.warning(`题目 ${i + 1} 的题目内容不能为空`)
      return
    }
    if (!q._correctKey) {
      ElMessage.warning(`题目 ${i + 1} 请选择正确答案`)
      return
    }
    for (let j = 0; j < q.options.length; j++) {
      if (!q.options[j].optionContent.trim()) {
        ElMessage.warning(`题目 ${i + 1} 的选项 ${q.options[j].optionKey} 内容不能为空`)
        return
      }
    }
  }

  editQuizSaving.value = true
  try {
    const payload = {
      textId: editQuizTarget.value.id,
      questions: editQuizQuestions.value.map(q => ({
        id: q.id,
        question: q.question,
        questionType: q.questionType,
        analysis: q.analysis,
        options: q.options.map(o => ({
          id: o.id,
          optionKey: o.optionKey,
          optionContent: o.optionContent,
          isCorrect: o.optionKey === q._correctKey ? 1 : 0
        }))
      }))
    }

    const baseURL = import.meta.env.VITE_API_BASE_URL || ''
    const token = localStorage.getItem('token') || ''
    const resp = await fetch(`${baseURL}/api/admin/texts/${editQuizTarget.value.id}/exercises`, {
      method: 'PUT',
      headers: {
        'Content-Type': 'application/json',
        ...(token ? { Authorization: `Bearer ${token}` } : {})
      },
      body: JSON.stringify(payload)
    })
    const result = await resp.json()

    if (result.code === 200) {
      ElMessage.success('题目保存成功')
      editQuizDialogVisible.value = false
    } else {
      ElMessage.error(result.message || '保存失败')
    }
  } catch (e) {
    ElMessage.error('保存失败：' + (e.message || '网络异常'))
  } finally {
    editQuizSaving.value = false
  }
}

let currentAudio = null

const playAudio = (audioUrl) => {
  if (!audioUrl) return
  if (currentAudio) { currentAudio.pause(); currentAudio = null }
  currentAudio = new Audio(audioUrl)
  currentAudio.play().catch(error => {
    ElMessage.error('音频播放失败')
    console.error('Audio playback error:', error)
  })
}

onBeforeUnmount(() => {
  if (currentAudio) { currentAudio.pause(); currentAudio = null }
})

const getContentPreview = (content) => {
  if (!content) return ''
  const maxLength = 100
  return content.length > maxLength ? content.substring(0, maxLength) + '...' : content
}

const getDifficultyType = (difficulty) => {
  const types = {
    EASY: 'success',
    MEDIUM: 'warning',
    HARD: 'danger'
  }
  return types[difficulty] || 'info'
}

const getDifficultyText = (difficulty) => {
  const texts = {
    EASY: '简单',
    MEDIUM: '中等',
    HARD: '困难'
  }
  return texts[difficulty] || difficulty
}

const formatTime = (time) => {
  if (!time) return ''
  const date = new Date(time)
  return date.toLocaleString('zh-CN')
}

const loadAvailableTags = async () => {
  try {
    const res = await adminApi.getContentTags()
    if (res.code === 200) {
      availableTags.value = res.data || []
    }
  } catch (e) {
    // 静默失败
  }
}

// 生命周期
onMounted(() => {
  loadCourses()
  loadStats()
  loadTexts()
  loadAvailableTags()
})

defineExpose({ loadTexts })
</script>

<style scoped>
.texts-management {
  padding: 20px;
  max-width: 1400px;
  margin: 0 auto;
}

.header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
  padding-bottom: 16px;
  border-bottom: 1px solid #e4e7ed;
}

.header h2 {
  margin: 0;
  color: #303133;
  font-weight: 500;
}

.actions {
  display: flex;
  gap: 12px;
}

.search-bar {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  flex-wrap: wrap;
  gap: 12px;
  margin-bottom: 20px;
  padding: 20px;
  background-color: #f8f9fa;
  border-radius: 8px;
}

.search-form {
  margin: 0;
}

.search-actions {
  display: flex;
  gap: 12px;
  flex-shrink: 0;
}

.stats-cards {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: 20px;
  margin-bottom: 20px;
}

.stat-card {
  text-align: center;
  padding: 20px;
  background-color: #fff;
  border: 1px solid #e4e7ed;
  border-radius: 8px;
  transition: all 0.3s ease;
  cursor: pointer;
}

.stat-card.active {
  border-color: #409eff;
  background-color: #ecf5ff;
}

.stat-card.active .stat-value {
  color: #337ecc;
}

.stat-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
}

.stat-value {
  font-size: 32px;
  font-weight: bold;
  color: #409eff;
  margin-bottom: 8px;
}

.stat-label {
  font-size: 14px;
  color: #909399;
}

.texts-table {
  background-color: #fff;
  border-radius: 8px;
  overflow-x: auto;
  -webkit-overflow-scrolling: touch;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
}

.content-preview {
  font-size: 13px;
  color: #606266;
  line-height: 1.4;
}

.pagination {
  margin-top: 20px;
  display: flex;
  justify-content: center;
}

.no-audio {
  color: #909399;
  font-size: 12px;
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}

.text-detail {
  padding: 20px 0;
}

.text-detail h3 {
  margin: 0 0 16px 0;
  color: #303133;
  font-size: 18px;
  font-weight: 500;
}

.text-meta {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 20px;
  padding-bottom: 12px;
  border-bottom: 1px solid #e4e7ed;
}

.create-time {
  font-size: 13px;
  color: #94a3b8;
}

.text-cover-preview {
  width: 100%;
  max-height: 200px;
  overflow: hidden;
  border-radius: 8px;
  margin-bottom: 16px;
}

.text-cover-preview img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.text-content {
  margin-bottom: 20px;
  max-height: 50vh;
  overflow-y: auto;
  padding: 10px;
  background-color: #f8f9fa;
  border-radius: 8px;
}

.text-content pre {
  white-space: pre-wrap;
  word-wrap: break-word;
  line-height: 1.6;
  font-family: inherit;
  color: #303133;
  margin: 0;
}

.text-audio h4 {
  margin: 0 0 12px 0;
  color: #606266;
  font-size: 14px;
  font-weight: normal;
}

@media (max-width: 768px) {
  .texts-management {
    padding: 16px;
  }

  .header {
    flex-direction: column;
    gap: 16px;
    align-items: flex-start;
  }

  .actions {
    width: 100%;
    justify-content: flex-start;
  }

  .search-form {
    display: block;
  }

  .search-form .el-form-item {
    margin-right: 0;
    margin-bottom: 16px;
  }

  .stats-cards {
    grid-template-columns: repeat(2, 1fr);
    gap: 12px;
  }

  .stat-card {
    padding: 16px;
  }

  .stat-value {
    font-size: 24px;
  }
}

.exercise-section {
  margin-top: 20px;
}

.section-title {
  font-weight: bold;
  color: #333;
}
.priority-na {
  color: #c0c4cc;
  font-size: 16px;
}

/* "更多"操作按钮：圆形 + 柔和阴影 */
.more-action-btn {
  margin-left: 8px;
  vertical-align: middle;
  background-color: #f5f7fa;
  border-color: #dcdfe6;
  color: #606266;
  transition: all 0.2s ease;
}
.more-action-btn:hover {
  background-color: #ecf5ff;
  border-color: #409eff;
  color: #409eff;
}

/* "重新生成改写文章"弹窗内容 */
.regen-dialog {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.regen-target {
  display: flex;
  align-items: baseline;
  gap: 10px;
  padding: 12px 14px;
  background-color: #f8f9fa;
  border-radius: 6px;
  border-left: 3px solid #409eff;
}
.regen-target-label {
  font-size: 12px;
  color: #909399;
  flex-shrink: 0;
}
.regen-target-title {
  font-size: 14px;
  color: #303133;
  font-weight: 500;
  word-break: break-word;
}
.regen-section-title {
  font-size: 13px;
  color: #606266;
  margin-bottom: 10px;
  font-weight: 500;
}
.regen-radio-group {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.regen-radio-group :deep(.el-radio-button__inner) {
  border-radius: 4px;
  border-left: 1px solid #dcdfe6;
}

/* "编辑题目"弹窗 */
.edit-quiz-dialog {
  max-height: 70vh;
  display: flex;
  flex-direction: column;
}

.edit-quiz-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
  padding-bottom: 12px;
  border-bottom: 1px solid #ebeef5;
}

.edit-quiz-target {
  font-size: 15px;
  font-weight: 600;
  color: #303133;
}

.edit-quiz-empty {
  padding: 40px 0;
}

.edit-quiz-list {
  flex: 1;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding-right: 4px;
}

.edit-quiz-item {
  border: 1px solid #e4e7ed;
  border-radius: 8px;
  padding: 16px;
  background: #fafafa;
}

.edit-quiz-item-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.edit-quiz-item-num {
  font-size: 14px;
  font-weight: 600;
  color: #409eff;
}

.edit-quiz-options {
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px dashed #dcdfe6;
}

.edit-quiz-options-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
  font-size: 13px;
  color: #606266;
  font-weight: 500;
}

.edit-quiz-option-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}
</style>
