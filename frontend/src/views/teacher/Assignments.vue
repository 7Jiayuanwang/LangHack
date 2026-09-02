<template>
  <div class="assignments-management">
    <el-card>
      <template #header>
        <div class="card-header">
          <el-button size="small" @click="openTemplateManager">Template manager</el-button>
          <span>任务管理</span>
          <el-button type="primary" @click="showCreateDialog">布置任务</el-button>
        </div>
      </template>

      <!-- 筛选栏 -->
      <div class="filter-bar">
        <el-form :inline="true" :model="filterForm">
          <el-form-item label="班级">
            <el-select v-model="filterForm.classId" placeholder="请选择班级" clearable @change="loadAssignments">
              <el-option
                v-for="cls in classes"
                :key="cls.id"
                :label="cls.name"
                :value="cls.id"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="状态">
            <el-select v-model="filterForm.status" placeholder="请选择状态" clearable @change="loadAssignments">
              <el-option label="进行中" value="active" />
              <el-option label="已结束" value="ended" />
            </el-select>
          </el-form-item>
        </el-form>
      </div>

      <!-- 任务列表 -->
      <div class="table-wrapper">
      <el-table v-loading="loading" :data="assignments" stripe>
        <el-table-column prop="title" label="任务标题" />
        <el-table-column prop="className" label="班级名称" />
        <el-table-column prop="unitName" label="学习单元" />
        <el-table-column prop="studentCount" label="学生总数" width="100" />
        <el-table-column prop="completedCount" label="完成人数" width="100">
          <template #default="scope">
            <el-tag :type="getCompletionType(scope.row.completedCount, scope.row.studentCount)">
              {{ scope.row.completedCount }}/{{ scope.row.studentCount }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="deadline" label="截止时间" width="180">
          <template #default="scope">
            {{ scope.row.deadline ? formatTime(scope.row.deadline) : '-' }}
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="100">
          <template #default="scope">
            <el-tag :type="scope.row.status === 'active' ? 'success' : 'info'">
              {{ scope.row.status === 'active' ? '进行中' : '已结束' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200">
          <template #default="scope">
            <el-button size="small" @click="viewProgress(scope.row)">查看进度</el-button>
            <el-button size="small" @click="editAssignment(scope.row)">编辑</el-button>
            <el-button size="small" type="danger" @click="deleteAssignment(scope.row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      </div>

      <!-- 空状态 -->
      <el-empty v-if="assignments.length === 0" description="暂无任务">
        <el-button type="primary" @click="showCreateDialog">布置第一个任务</el-button>
      </el-empty>
    </el-card>

    <!-- 创建/编辑任务对话框 -->
    <el-dialog
      v-model="dialogVisible"
      :title="isEdit ? '编辑任务' : '布置任务'"
      width="min(800px, 90%)"
    >
      <el-form
        ref="assignmentFormRef"
        :model="assignmentForm"
        :rules="assignmentRules"
        label-width="80px"
      >
        <!-- 快捷模板 -->
        <el-form-item v-if="!isEdit" label="快捷模板">
          <div class="template-list">
            <div
              v-for="tpl in assignmentTemplates"
              :key="tpl.key || tpl.id"
              class="template-card"
              :class="{ active: activeTemplate === (tpl.key || tpl.id) }"
              @click="applyTemplate(tpl)"
            >
              <div class="template-icon">{{ tpl.icon || '📋' }}</div>
              <div class="template-name">{{ tpl.name }}</div>
              <div class="template-desc">{{ tpl.desc }}</div>
            </div>
          </div>
          <div class="template-actions">
            <el-button size="small" @click="saveCurrentAsTemplate">Save current as template</el-button>
            <el-button size="small" link type="primary" @click="openTemplateManager">Manage my templates</el-button>
          </div>
          <div class="template-tip">点击模板可自动填充任务类型和学习时长；指定课文只需选班级和文章，其他任务需选择单元</div>
        </el-form-item>

        <el-form-item label="任务标题" prop="title">
          <el-input v-model="assignmentForm.title" placeholder="请输入任务标题" />
        </el-form-item>
        <el-form-item label="选择班级" prop="classId">
          <el-select
            v-model="assignmentForm.classId"
            :multiple="!isEdit"
            collapse-tags
            collapse-tags-tooltip
            placeholder="请选择班级"
            style="width: 100%"
          >
            <el-option
              v-for="cls in classes"
              :key="cls.id"
              :label="cls.name"
              :value="cls.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item v-if="assignmentForm.assignmentType !== 'TEXTS'" label="选择单元" prop="unitId">
          <el-select
            v-model="assignmentForm.unitId"
            placeholder="请选择单元"
            style="width: 100%"
            @change="onUnitChange"
          >
            <el-option
              v-for="unit in units"
              :key="unit.id"
              :label="unit.name"
              :value="unit.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="任务类型" prop="assignmentType">
          <el-radio-group v-model="assignmentForm.assignmentType" @change="onAssignmentTypeChange">
            <el-radio value="UNIT">整个单元</el-radio>
            <el-radio value="TEXTS">指定课文</el-radio>
            <el-radio value="WORDS">指定单词</el-radio>
          </el-radio-group>
        </el-form-item>

        <el-form-item v-if="assignmentForm.assignmentType === 'TEXTS'" label="文章分类" prop="articleType">
          <el-radio-group v-model="assignmentForm.articleType" @change="onArticleTypeChange">
            <el-radio value="INNER">课内教材课文</el-radio>
            <el-radio value="OUTER">课外读物</el-radio>
          </el-radio-group>
        </el-form-item>
        <template v-if="assignmentForm.assignmentType === 'TEXTS' && assignmentForm.articleType === 'INNER'">
          <el-form-item label="册号">
            <el-select v-model="assignmentForm.bookVolume" placeholder="选择教材" clearable @change="onBookVolumeChange">
              <el-option v-for="volume in bookVolumes" :key="volume" :label="volume" :value="volume" />
            </el-select>
          </el-form-item>
          <el-form-item v-if="assignmentForm.bookVolume" label="内容">
            <el-select v-model="assignmentForm.contentSection" placeholder="选择内容" clearable @change="onContentSectionChange">
              <el-option
                v-for="section in contentSectionOptions"
                :key="section"
                :label="section"
                :value="section"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="Unit">
            <el-select v-model="assignmentForm.unitNum" placeholder="选择单元" clearable @change="loadArticles">
              <el-option v-for="unit in unitNumbers" :key="unit" :label="`Unit ${unit}`" :value="unit" />
            </el-select>
          </el-form-item>
        </template>

        <!-- 选择课文 -->
        <el-form-item v-if="assignmentForm.assignmentType === 'TEXTS'" label="选择课文" prop="textIds">
          <div v-loading="loadingUnitContent" class="content-selector">
            <div v-if="assignmentForm.articleType === 'OUTER'" class="outer-article-tools">
              <div class="outer-article-filters">
                <el-input
                  v-model="outerArticleKeyword"
                  clearable
                  placeholder="搜索文章标题或标签"
                  class="outer-article-search"
                />
                <el-select
                  v-model="outerArticleDifficulty"
                  clearable
                  placeholder="难度"
                  class="outer-article-difficulty"
                >
                  <el-option label="简单" value="EASY" />
                  <el-option label="中等" value="MEDIUM" />
                  <el-option label="困难" value="HARD" />
                </el-select>
              </div>
              <div v-if="outerArticleTags.length" class="outer-article-tag-filter">
                <span class="tag-filter-label">标签</span>
                <el-tag
                  v-for="tag in outerArticleTags"
                  :key="tag"
                  size="small"
                  :type="outerArticleTag === tag ? 'primary' : 'info'"
                  :effect="outerArticleTag === tag ? 'dark' : 'plain'"
                  class="tag-filter-item"
                  @click="outerArticleTag = outerArticleTag === tag ? '' : tag"
                >
                  {{ tag }}
                </el-tag>
                <el-button
                  v-if="outerArticleTag"
                  link
                  type="primary"
                  size="small"
                  @click="outerArticleTag = ''"
                >
                  清除标签
                </el-button>
              </div>
              <div class="outer-article-summary">
                显示 {{ filteredArticleTexts.length }} 篇，已选 {{ assignmentForm.textIds.length }} 篇
                <el-button link type="primary" size="small" @click="toggleAllFilteredArticles">
                  {{ allFilteredArticleIdsSelected ? '取消当前筛选' : '全选当前筛选' }}
                </el-button>
              </div>
            </div>
            <el-checkbox-group v-model="assignmentForm.textIds">
              <div class="content-grid">
                <el-checkbox
                  v-for="text in filteredArticleTexts"
                  :key="text.id"
                  :value="text.id"
                  class="content-item"
                  :class="{ 'outer-article-item': assignmentForm.articleType === 'OUTER' }"
                >
                  <div class="content-info">
                    <div class="content-title">{{ text.title }}</div>
                    <div class="content-meta">
                      <el-tag size="small" effect="plain" :type="getDifficultyTagType(text.difficulty)">
                        {{ getDifficultyLabel(text.difficulty) }}
                      </el-tag>
                      <span>字数：{{ getTextCharacterCount(text) }}</span>
                      <span v-if="assignmentForm.articleType === 'OUTER'">
                        预计 {{ getEstimatedReadingMinutes(text) }} 分钟
                      </span>
                    </div>
                    <div v-if="getTextTags(text).length" class="article-tags">
                      <el-tag
                        v-for="tag in getTextTags(text)"
                        :key="tag"
                        size="small"
                        effect="plain"
                        type="info"
                      >
                        {{ tag }}
                      </el-tag>
                    </div>
                    <div v-else class="content-category">{{ getTextClassificationLabel(text) }}</div>
                  </div>
                </el-checkbox>
              </div>
            </el-checkbox-group>
            <el-empty
              v-if="!loadingUnitContent && filteredArticleTexts.length === 0"
              :description="assignmentForm.articleType === 'OUTER' ? '没有匹配的课外文章' : '该单元暂无课文'"
            />
          </div>
        </el-form-item>

        <!-- 单词组数设置 -->
        <el-form-item v-if="assignmentForm.assignmentType === 'WORDS'" label="单词组数">
          <el-input-number
            v-model="assignmentForm.wordGroupCount"
            :min="1"
            :max="10"
            :step="1"
            @change="autoSelectWordsByGroup"
          />
          <span style="margin-left: 12px; font-size: 12px; color: #909399;">
            每组15个单词，共需 {{ assignmentForm.wordGroupCount * 15 }} 个
          </span>
        </el-form-item>

        <!-- 选择单词 -->
        <el-form-item v-if="assignmentForm.assignmentType === 'WORDS'" label="选择单词" prop="wordIds">
          <div v-loading="loadingUnitContent" class="content-selector">
            <el-checkbox-group v-model="assignmentForm.wordIds">
              <div class="content-grid">
                <el-checkbox
                  v-for="word in unitContent.words"
                  :key="word.id"
                  :value="word.id"
                  class="content-item"
                >
                  <div class="content-info">
                    <div class="content-title">{{ word.word }}</div>
                    <div class="content-meta">
                      {{ word.pronunciation }} |
                      难度：{{ word.difficulty }}
                    </div>
                    <div class="content-desc">{{ word.definition }}</div>
                  </div>
                </el-checkbox>
              </div>
            </el-checkbox-group>
            <el-empty v-if="!loadingUnitContent && unitContent.words.length === 0" description="该单元暂无单词" />
          </div>
        </el-form-item>

        <el-form-item label="任务描述" prop="description">
          <el-input
            v-model="assignmentForm.description"
            type="textarea"
            :rows="4"
            placeholder="请输入任务描述"
          />
        </el-form-item>
        <el-form-item label="截止时间" prop="deadline">
          <el-date-picker
            v-model="assignmentForm.deadline"
            type="datetime"
            placeholder="选择截止时间"
            style="width: 100%"
          />
        </el-form-item>
        <!-- 在截止时间字段后添加以下两个字段 -->
        <el-form-item label="任务权重" prop="weight">
          <el-input-number
              v-model="assignmentForm.weight"
              :min="0.1"
              :max="10"
              :step="0.1"
              :precision="2"
              placeholder="任务权重（用于计算加权平均分，默认1.00）"
              style="width: 100%"
          />
          <div style="font-size: 12px; color: #999; margin-top: 4px;">
            权重用于计算加权平均分，例如：权重1.5的任务分数会按1.5倍计入总分
          </div>
        </el-form-item>

        <el-form-item label="理想学习时长" prop="idealStudyTimeMinutes">
          <el-input-number
              v-model="assignmentForm.idealStudyTimeMinutes"
              :min="1"
              :max="300"
              :step="1"
              placeholder="理想学习时长（分钟），用于计算分数，默认60分钟"
              style="width: 100%"
          />
          <div style="font-size: 12px; color: #999; margin-top: 4px;">
            设置学生完成此任务的理想学习时长。分数计算中，学习时长占40%权重，达到理想时长可获得满分。
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <span class="dialog-footer">
          <el-button v-if="!isEdit" @click="saveCurrentAsTemplate">Save as template</el-button>
          <el-button @click="dialogVisible = false">取消</el-button>
          <el-button type="primary" @click="saveAssignment" :loading="saving">确定</el-button>
        </span>
      </template>
    </el-dialog>

    <!-- 任务进度对话框 -->
    <el-dialog v-model="templateManagerVisible" title="My assignment templates" width="min(680px, 92%)">
      <div class="template-manager-toolbar">
        <span class="template-manager-tip">Templates are reusable across classes. Editing one affects future assignments only.</span>
        <el-button type="primary" size="small" @click="startNewTemplate">New template</el-button>
      </div>
      <el-empty v-if="userTemplates.length === 0" description="No custom templates" />
      <div v-else class="template-manager-list">
        <div v-for="tpl in userTemplates" :key="tpl.id" class="template-manager-item">
          <div>
            <div class="template-manager-name">{{ tpl.name }}</div>
            <div class="template-manager-desc">{{ tpl.description || 'No description' }}</div>
          </div>
          <div class="template-manager-actions">
            <el-button link type="primary" @click="editTemplate(tpl)">Edit</el-button>
            <el-button link type="danger" @click="removeTemplate(tpl)">Delete</el-button>
          </div>
        </div>
      </div>
      <el-form v-if="templateEditorVisible" :model="templateDraft" label-width="90px" class="template-editor">
        <el-form-item label="Template name" required>
          <el-input v-model="templateDraft.name" maxlength="120" show-word-limit />
        </el-form-item>
        <el-form-item label="Description">
          <el-input v-model="templateDraft.description" maxlength="500" type="textarea" :rows="2" />
        </el-form-item>
        <div class="template-editor-actions">
          <el-button @click="templateEditorVisible = false">Cancel</el-button>
          <el-button type="primary" :loading="templateSaving" @click="submitTemplate">Save</el-button>
        </div>
      </el-form>
    </el-dialog>

    <el-dialog
      v-model="progressDialogVisible"
      title="任务完成进度"
      width="min(700px, 90%)"
    >
      <div class="progress-overview">
        <div class="stats-row">
          <div class="stat-item">
            <span class="label">总人数：</span>
            <span class="value">{{ currentAssignment.studentCount }}人</span>
          </div>
          <div class="stat-item">
            <span class="label">已完成：</span>
            <span class="value completed">{{ currentAssignment.completedCount }}人</span>
          </div>
          <div class="stat-item">
            <span class="label">完成率：</span>
            <span class="value">{{ getCompletionRate(currentAssignment) }}%</span>
          </div>
        </div>
        <el-progress
          :percentage="getCompletionRate(currentAssignment)"
          :color="getProgressColor(getCompletionRate(currentAssignment))"
          :stroke-width="8"
        />
      </div>

      <div class="students-progress">
        <h4>学生完成情况</h4>
        <div class="table-wrapper">
        <el-table :data="studentProgress" stripe size="small">
          <el-table-column prop="username" label="学号" width="120" />
          <el-table-column prop="realName" label="真实姓名" width="120" />
          <el-table-column prop="completedAt" label="完成时间" width="180">
            <template #default="scope">
              {{ scope.row.completedAt ? formatTime(scope.row.completedAt) : '未完成' }}
            </template>
          </el-table-column>
          <el-table-column prop="studyTime" label="学习时长" width="100">
            <template #default="scope">
              {{ formatStudyDuration(scope.row) }}
            </template>
          </el-table-column>
          <el-table-column prop="progress" label="学习进度" width="100">
            <template #default="scope">
              <el-progress :percentage="normalizeProgress(scope.row.progress)" :stroke-width="6" />
            </template>
          </el-table-column>
          <el-table-column prop="score" label="得分" width="80">
            <template #default="scope">
              <el-tag v-if="scope.row.score !== null && scope.row.score > 0" :type="getScoreType(scope.row.score)">
                {{ scope.row.score }}分
              </el-tag>
              <span v-else>-</span>
            </template>
          </el-table-column>
        </el-table>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onBeforeUnmount, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getAuthHeader } from '@/utils/auth'

// 响应式数据
const loading = ref(false)
const assignments = ref([])
const classes = ref([])
const units = ref([])
const studentProgress = ref([])
const dialogVisible = ref(false)
const progressDialogVisible = ref(false)
const isEdit = ref(false)
const saving = ref(false)
const assignmentFormRef = ref()
const currentAssignment = ref({})
let progressRefreshTimer = null
const loadingUnitContent = ref(false)
const unitContent = ref({
  texts: [],
  words: []
})
const bookVolumes = ['综合教程1', '综合教程2', '综合教程3', '理解当代中国']
const unitNumbers = Array.from({ length: 6 }, (_, index) => index + 1)
const contentSectionOptions = computed(() => (
  assignmentForm.bookVolume === '理解当代中国'
    ? ['Understanding China', 'Exploring the world']
    : ['iExplore1', 'iExplore2']
))

// 快捷任务模板
const activeTemplate = ref('')
const builtinAssignmentTemplates = [
  {
    key: 'reading',
    name: '读课文',
    icon: '📖',
    desc: '指定课文阅读，建议10分钟',
    assignmentType: 'TEXTS',
    articleType: 'OUTER',
    idealStudyTimeMinutes: 10,
    title: '课文阅读任务'
  },
  {
    key: 'words',
    name: '背单词',
    icon: '📝',
    desc: '分组背诵，每组不少于15个',
    assignmentType: 'WORDS',
    idealStudyTimeMinutes: 15,
    title: '单词背诵任务'
  }
]
const userTemplates = ref([])
const assignmentTemplates = computed(() => [...builtinAssignmentTemplates, ...userTemplates.value])
const templateManagerVisible = ref(false)
const templateEditorVisible = ref(false)
const templateSaving = ref(false)
const templateDraft = reactive({ id: null, name: '', description: '' })
const templateDraftConfig = ref({})

// 筛选表单
const filterForm = reactive({
  classId: null,
  status: null
})

// 任务表单
const assignmentForm = reactive({
  id: null,
  title: '',
  // 新建时为数组以支持一次给多个班级布置；编辑时保存单个班级 ID。
  classId: [],
  unitId: null,
  assignmentType: 'UNIT',
  articleType: 'OUTER',
  bookVolume: null,
  contentSection: null,
  unitNum: null,
  textIds: [],
  wordIds: [],
  description: '',
  deadline: null,
  weight: 1.0,  // 添加权重字段，默认1.0
  idealStudyTimeMinutes: 60,  // 添加理想学习时长字段，默认60分钟
  wordGroupCount: 1  // 单词组数，默认1组
})

// 表单验证规则
// 课外文章筛选只作用于当前弹窗中的显示列表，不改变接口返回的数据。
const outerArticleKeyword = ref('')
const outerArticleDifficulty = ref('')
const outerArticleTag = ref('')

const getTextTags = (text) => {
  const rawTags = text?.tags
  if (Array.isArray(rawTags)) return rawTags.map(tag => String(tag).trim()).filter(Boolean)
  if (typeof rawTags !== 'string') return []
  return rawTags
    .split(/[,，;；、|]/)
    .map(tag => tag.trim())
    .filter(Boolean)
}

const outerArticleTags = computed(() => {
  const tags = new Set()
  for (const text of unitContent.value.texts || []) {
    getTextTags(text).forEach(tag => tags.add(tag))
  }
  return [...tags]
})

const filteredArticleTexts = computed(() => {
  const keyword = outerArticleKeyword.value.trim().toLowerCase()
  const difficulty = outerArticleDifficulty.value
  const tag = outerArticleTag.value
  return (unitContent.value.texts || []).filter(text => {
    if (assignmentForm.articleType !== 'OUTER') return true
    const tags = getTextTags(text)
    const searchableText = [text?.title, text?.category, ...tags]
      .filter(Boolean)
      .join(' ')
      .toLowerCase()
    return (!keyword || searchableText.includes(keyword))
      && (!difficulty || String(text?.difficulty || '').toUpperCase() === difficulty)
      && (!tag || tags.includes(tag))
  })
})

const allFilteredArticleIdsSelected = computed(() => {
  const ids = filteredArticleTexts.value.map(text => text.id)
  return ids.length > 0 && ids.every(id => assignmentForm.textIds.includes(id))
})

const toggleAllFilteredArticles = () => {
  const visibleIds = filteredArticleTexts.value.map(text => text.id)
  if (allFilteredArticleIdsSelected.value) {
    assignmentForm.textIds = assignmentForm.textIds.filter(id => !visibleIds.includes(id))
  } else {
    assignmentForm.textIds = [...new Set([...assignmentForm.textIds, ...visibleIds])]
  }
}

const resetOuterArticleFilters = () => {
  outerArticleKeyword.value = ''
  outerArticleDifficulty.value = ''
  outerArticleTag.value = ''
}

const getDifficultyLabel = (difficulty) => ({ EASY: '简单', MEDIUM: '中等', HARD: '困难' }[
  String(difficulty || '').toUpperCase()
] || '未标注')

const getDifficultyTagType = (difficulty) => ({ EASY: 'success', MEDIUM: 'warning', HARD: 'danger' }[
  String(difficulty || '').toUpperCase()
] || 'info')

const getEstimatedReadingMinutes = (text) => {
  const explicitMinutes = Number(text?.readingTimeMinutes || text?.estimatedReadingMinutes)
  if (Number.isFinite(explicitMinutes) && explicitMinutes > 0) return Math.ceil(explicitMinutes)
  const content = typeof text?.content === 'string' && text.content.trim()
    ? text.content
    : (typeof text?.markdownContent === 'string' ? text.markdownContent : '')
  const plainText = content
    .replace(/```[\\s\\S]*?```/g, ' ')
    .replace(/[#*_>`~-]/g, ' ')
    .trim()
  const words = plainText ? plainText.split(/\\s+/).filter(Boolean).length : 0
  return Math.max(1, Math.ceil(words / 180))
}

const assignmentRules = {
  title: [
    { required: true, message: '请输入任务标题', trigger: 'blur' }
  ],
  classId: [
    {
      validator: (rule, value, callback) => {
        const hasClass = Array.isArray(value)
          ? value.length > 0
          : value !== null && value !== undefined && value !== ''
        callback(hasClass ? undefined : new Error('请选择班级'))
      },
      trigger: 'change'
    }
  ],
  unitId: [
    {
      validator: (rule, value, callback) => {
        if (assignmentForm.assignmentType !== 'TEXTS' && !value) {
          callback(new Error('请选择单元'))
        } else {
          callback()
        }
      },
      trigger: 'change'
    }
  ],
  assignmentType: [
    { required: true, message: '请选择任务类型', trigger: 'change' }
  ],
  textIds: [
    {
      validator: (rule, value, callback) => {
        if (assignmentForm.assignmentType === 'TEXTS' && (!Array.isArray(value) || value.length === 0)) {
          callback(new Error('请至少选择一篇课文'))
        } else {
          callback()
        }
      },
      trigger: 'change'
    }
  ],
  wordIds: [
    {
      validator: (rule, value, callback) => {
        if (assignmentForm.assignmentType === 'WORDS' && (!Array.isArray(value) || value.length === 0)) {
          callback(new Error('请至少选择一个单词'))
        } else {
          callback()
        }
      },
      trigger: 'change'
    }
  ],
  deadline: [
    { required: true, message: '请选择截止时间', trigger: 'change' },
    {
      validator: (rule, value, callback) => {
        if (!value) {
          callback()
          return
        }
        const deadline = new Date(value)
        const now = new Date()
        // 编辑模式下，如果截止时间未改变，允许保留原值（即使已过期）
        // 新建模式或修改了截止时间时，必须晚于当前时间
        if (deadline <= now && !isEdit.value) {
          callback(new Error('截止时间必须晚于当前时间'))
        } else {
          callback()
        }
      },
      trigger: 'change'
    }
  ],
  weight: [
    { required: true, message: '请输入任务权重', trigger: 'blur' },
    {
      validator: (rule, value, callback) => {
        if (value < 0.1 || value > 10) {
          callback(new Error('任务权重必须在0.1到10之间'))
        } else if (Math.abs(value - Math.round(value * 100) / 100) > 0.001) {
          callback(new Error('任务权重最多保留两位小数'))
        } else {
          callback()
        }
      },
      trigger: 'blur'
    }
  ],
  idealStudyTimeMinutes: [
    { required: true, message: '请输入理想学习时长', trigger: 'blur' },
    {
      validator: (rule, value, callback) => {
        if (value < 1) {
          callback(new Error('理想学习时长不能少于1分钟'))
        } else if (value > 300) {
          callback(new Error('理想学习时长不能超过300分钟'))
        } else {
          callback()
        }
      },
      trigger: 'blur'
    }
  ],
  description: [
    { max: 500, message: '任务描述不能超过500个字符', trigger: 'blur' }
  ]
}

// 方法
const loadAssignments = async () => {
  loading.value = true
  try {
    const params = new URLSearchParams()

    if (filterForm.classId) {
      params.append('classId', filterForm.classId)
    }
    if (filterForm.status) {
      params.append('status', filterForm.status)
    }
    params.append('page', '1')
    params.append('size', '100')

    const headers = {}
    const authHeader = getAuthHeader()
    if (authHeader) headers['Authorization'] = authHeader

    const response = await fetch(`/api/assignments?${params.toString()}`, { headers })

    if (response.ok) {
      try {
        const data = await response.json()
        if (data.code === 200) {
          let assignmentsData = data.data?.records || []

          // 为每个任务计算真实状态和统计信息
          assignmentsData = assignmentsData.map(assignment => {
            // 根据截止时间计算真实状态
            const realStatus = calculateRealStatus(assignment.deadline)

            // 获取班级学生数量（同步方法，从已加载的班级数据获取）
            const studentCount = getStudentCountForClass(assignment.classId)

            return {
              ...assignment,
              status: realStatus,
              studentCount: studentCount,
              completedCount: 0 // 先设置为0，后续异步获取
            }
          })

          // 批量获取完成人数，避免过多并发请求
          assignmentsData = await batchGetCompletionCounts(assignmentsData)

          // 如果有状态筛选，进行过滤
          if (filterForm.status) {
            assignmentsData = assignmentsData.filter(assignment =>
              assignment.status === filterForm.status
            )
          }

          assignments.value = assignmentsData
        } else {
          ElMessage.error(data.message || '加载任务列表失败')
        }
      } catch (parseError) {
        console.error('任务列表解析失败:', parseError)
        ElMessage.error('加载任务列表失败')
      }
    } else {
      ElMessage.error('加载任务列表失败')
    }
  } catch (error) {
    console.error('加载任务列表失败:', error)
    ElMessage.error('加载任务列表失败')
  } finally {
    loading.value = false
  }
}

const loadClasses = async () => {
  try {
    const headers = {}
    const authHeader = getAuthHeader()
    if (authHeader) headers['Authorization'] = authHeader

    const response = await fetch('/api/classes?size=100', { headers })

    if (response.ok) {
      try {
        const data = await response.json()
        if (data.code === 200) {
          classes.value = data.data?.records || []
        } else {
          ElMessage.error(data.message || '加载班级列表失败')
        }
      } catch (parseError) {
        console.error('班级列表解析失败:', parseError)
        ElMessage.error('加载班级列表失败')
      }
    } else {
      ElMessage.error('加载班级列表失败')
    }
  } catch (error) {
    console.error('加载班级列表失败:', error)
    ElMessage.error('加载班级列表失败')
  }
  // 确保总是返回Promise，即使出错也不会阻塞
  return Promise.resolve()
}

const loadUnits = async () => {
  try {
    const headers = {}
    const authHeader = getAuthHeader()
    if (authHeader) headers['Authorization'] = authHeader

    const response = await fetch('/api/units?size=100', { headers })

    if (response.ok) {
      try {
        const data = await response.json()
        if (data.code === 200) {
          units.value = data.data?.records || []
        } else {
          ElMessage.error(data.message || '加载单元列表失败')
        }
      } catch (parseError) {
        console.error('单元列表解析失败:', parseError)
        ElMessage.error('加载单元列表失败')
      }
    } else {
      ElMessage.error('加载单元列表失败')
    }
  } catch (error) {
    console.error('加载单元列表失败:', error)
    ElMessage.error('加载单元列表失败')
  }
  // 确保总是返回Promise，即使出错也不会阻塞
  return Promise.resolve()
}

void loadUnits

const loadUnitsForClass = async (classId) => {
  try {
    // 根据班级ID获取对应的课程ID
    const classItem = classes.value.find(cls => cls.id === classId)
    if (!classItem || !classItem.courseId) {
      units.value = []
      return
    }

    const headers = {}
    const authHeader = getAuthHeader()
    if (authHeader) headers['Authorization'] = authHeader

    const response = await fetch(`/api/units?courseId=${classItem.courseId}&size=100`, { headers })

    if (response.ok) {
      try {
        const data = await response.json()
        if (data.code === 200) {
          units.value = data.data?.records || []
        } else {
          ElMessage.error(data.message || '加载单元列表失败')
          units.value = []
        }
      } catch (parseError) {
        console.error('单元列表解析失败:', parseError)
        ElMessage.error('加载单元列表失败')
        units.value = []
      }
    } else {
      ElMessage.error('加载单元列表失败')
      units.value = []
    }
  } catch (error) {
    console.error('加载单元列表失败:', error)
    ElMessage.error('加载单元列表失败')
    units.value = []
  }
  return Promise.resolve()
}

const templateHeaders = () => {
  const headers = { 'Content-Type': 'application/json' }
  const authHeader = getAuthHeader()
  if (authHeader) headers.Authorization = authHeader
  return headers
}

const templateConfigFromForm = () => ({
  title: assignmentForm.title,
  assignmentType: assignmentForm.assignmentType,
  articleType: assignmentForm.articleType,
  bookVolume: assignmentForm.bookVolume,
  contentSection: assignmentForm.contentSection,
  unitNum: assignmentForm.unitNum,
  unitId: assignmentForm.unitId,
  textIds: [...assignmentForm.textIds],
  wordIds: [...assignmentForm.wordIds],
  description: assignmentForm.description,
  weight: assignmentForm.weight,
  idealStudyTimeMinutes: assignmentForm.idealStudyTimeMinutes,
  wordGroupCount: assignmentForm.wordGroupCount
})

const loadAssignmentTemplates = async () => {
  try {
    const response = await fetch('/api/teacher/assignment-templates', { headers: templateHeaders() })
    const data = await response.json()
    if (response.ok && data.code === 200) {
      userTemplates.value = Array.isArray(data.data)
        ? data.data.map(template => ({ ...template, key: `custom-${template.id}`, desc: template.description }))
        : []
    }
  } catch (error) {
    console.debug('Failed to load assignment templates', error)
  }
}

const openTemplateManager = async () => {
  templateManagerVisible.value = true
  templateEditorVisible.value = false
  await loadAssignmentTemplates()
}

const startNewTemplate = () => {
  templateDraft.id = null
  templateDraft.name = assignmentForm.title || ''
  templateDraft.description = assignmentForm.description || ''
  templateDraftConfig.value = templateConfigFromForm()
  templateEditorVisible.value = true
}

const saveCurrentAsTemplate = async () => {
  templateDraft.id = null
  templateDraft.name = assignmentForm.title || ''
  templateDraft.description = assignmentForm.description || ''
  templateDraftConfig.value = templateConfigFromForm()
  templateManagerVisible.value = true
  templateEditorVisible.value = true
  await loadAssignmentTemplates()
}

const editTemplate = (tpl) => {
  templateDraft.id = tpl.id
  templateDraft.name = tpl.name || ''
  templateDraft.description = tpl.description || ''
  templateDraftConfig.value = tpl.config || {}
  templateEditorVisible.value = true
}

const submitTemplate = async () => {
  if (!templateDraft.name.trim()) {
    ElMessage.error('Template name is required')
    return
  }
  templateSaving.value = true
  try {
    const editing = Boolean(templateDraft.id)
    const response = await fetch(editing
      ? `/api/teacher/assignment-templates/${templateDraft.id}`
      : '/api/teacher/assignment-templates', {
      method: editing ? 'PUT' : 'POST',
      headers: templateHeaders(),
      body: JSON.stringify({
        name: templateDraft.name.trim(),
        description: templateDraft.description,
        config: templateDraftConfig.value
      })
    })
    const data = await response.json()
    if (!response.ok || data.code !== 200) throw new Error(data.message || 'Failed to save template')
    ElMessage.success('Template saved')
    templateEditorVisible.value = false
    await loadAssignmentTemplates()
  } catch (error) {
    ElMessage.error(error.message || 'Failed to save template')
  } finally {
    templateSaving.value = false
  }
}

const removeTemplate = async (tpl) => {
  try {
    await ElMessageBox.confirm(`Delete template "${tpl.name}"?`, 'Confirm', { type: 'warning' })
    const response = await fetch(`/api/teacher/assignment-templates/${tpl.id}`, {
      method: 'DELETE',
      headers: templateHeaders()
    })
    const data = await response.json()
    if (!response.ok || data.code !== 200) throw new Error(data.message || 'Failed to delete template')
    ElMessage.success('Template deleted')
    await loadAssignmentTemplates()
  } catch (error) {
    if (error !== 'cancel') ElMessage.error(error.message || 'Failed to delete template')
  }
}

const showCreateDialog = () => {
  isEdit.value = false
  dialogVisible.value = true
  resetAssignmentForm()
}

const editAssignment = async (assignment) => {
  isEdit.value = true
  dialogVisible.value = true

  const editClassId = Array.isArray(assignment.classId)
    ? assignment.classId[0] || null
    : assignment.classId

  // 设置表单数据（包含 assignmentType/textIds/wordIds 以便正确展示表单）
  Object.assign(assignmentForm, {
    id: assignment.id,
    title: assignment.title,
    classId: editClassId,
    unitId: assignment.unitId,
    assignmentType: assignment.assignmentType || 'UNIT',
    articleType: assignment.articleType || 'OUTER',
    bookVolume: assignment.bookVolume || null,
    contentSection: null,
    unitNum: assignment.unitNum || null,
    textIds: Array.isArray(assignment.textIds) ? [...assignment.textIds] : [],
    wordIds: Array.isArray(assignment.wordIds) ? [...assignment.wordIds] : [],
    description: assignment.description,
    deadline: assignment.deadline ? new Date(assignment.deadline) : null,
    weight: assignment.weight || 1.0,  // 添加权重
    idealStudyTimeMinutes: assignment.idealStudyTimeMinutes || 60,  // 添加理想学习时长
    wordGroupCount: assignment.wordGroupCount || 1  // 单词组数
  })

  // 加载对应班级的单元列表
  if (editClassId) {
    await loadUnitsForClass(editClassId)
  }

  // 如果是 TEXTS 或 WORDS 类型，加载单元内容以便编辑
  if (assignment.unitId && (assignment.assignmentType === 'TEXTS' || assignment.assignmentType === 'WORDS')) {
    await loadUnitContent(assignment.unitId)
  }
}

const resetAssignmentForm = () => {
  Object.assign(assignmentForm, {
    id: null,
    title: '',
    classId: [],
    unitId: null,
    assignmentType: 'UNIT',
    articleType: 'OUTER',
    bookVolume: null,
    contentSection: null,
    unitNum: null,
    textIds: [],
    wordIds: [],
    description: '',
    deadline: null,
    weight: 1.0,  // 重置权重
    idealStudyTimeMinutes: 60,  // 重置理想学习时长
    wordGroupCount: 1  // 重置单词组数
  })
  unitContent.value = { texts: [], words: [] }
  activeTemplate.value = ''
  resetOuterArticleFilters()
}

// 应用快捷模板
const applyTemplate = async (tpl) => {
  const config = tpl.config || tpl
  activeTemplate.value = tpl.key || tpl.id
  // 填充模板预设值
  assignmentForm.assignmentType = config.assignmentType || 'UNIT'
  assignmentForm.articleType = config.articleType || 'OUTER'
  assignmentForm.idealStudyTimeMinutes = config.idealStudyTimeMinutes || 60
  assignmentForm.title = config.title || tpl.name || ''
  assignmentForm.wordGroupCount = config.wordGroupCount || 1
  assignmentForm.unitId = config.unitId || null
  assignmentForm.description = config.description || ''
  assignmentForm.weight = config.weight || 1
  assignmentForm.bookVolume = config.bookVolume || null
  assignmentForm.contentSection = config.contentSection || null
  assignmentForm.unitNum = config.unitNum || null
  // 清空已选内容
  assignmentForm.textIds = Array.isArray(config.textIds) ? [...config.textIds] : []
  assignmentForm.wordIds = Array.isArray(config.wordIds) ? [...config.wordIds] : []
  // 加载对应内容
  if (config.assignmentType === 'TEXTS') {
    await loadArticles()
    if (Array.isArray(config.textIds)) assignmentForm.textIds = [...config.textIds]
  } else if (config.assignmentType === 'WORDS' && assignmentForm.unitId) {
    await loadUnitContent(assignmentForm.unitId)
    if (!Array.isArray(config.wordIds) || config.wordIds.length === 0) autoSelectWordsByGroup()
  } else {
    unitContent.value = { texts: [], words: [] }
  }
}

// 根据组数自动选取单词（每组15个）
const autoSelectWordsByGroup = () => {
  const wordsPerGroup = 15
  const needCount = assignmentForm.wordGroupCount * wordsPerGroup
  const availableWords = unitContent.value.words || []
  if (availableWords.length === 0) return
  // 自动选取前 needCount 个单词
  const selected = availableWords.slice(0, Math.min(needCount, availableWords.length)).map(w => w.id)
  assignmentForm.wordIds = selected
  // 更新任务描述
  const actualGroups = Math.ceil(selected.length / wordsPerGroup)
  assignmentForm.description = `背诵第1-${actualGroups}组单词，共${selected.length}个，每组不少于15个单词。`
}

// 加载单元内容
const loadUnitContent = async (unitId) => {
  if (!unitId) return

  loadingUnitContent.value = true
  try {
    const headers = {}
    const authHeader = getAuthHeader()
    if (authHeader) headers['Authorization'] = authHeader

    const response = await fetch(`/api/assignments/unit/${unitId}/content`, { headers })

    if (response.ok) {
      try {
        const data = await response.json()
        if (data.code === 200) {
          unitContent.value = data.data || { texts: [], words: [] }
        } else {
          ElMessage.error(data.message || '加载单元内容失败')
          unitContent.value = { texts: [], words: [] }
        }
      } catch (parseError) {
        console.error('单元内容解析失败:', parseError)
        ElMessage.error('加载单元内容失败')
        unitContent.value = { texts: [], words: [] }
      }
    } else {
      ElMessage.error('加载单元内容失败')
      unitContent.value = { texts: [], words: [] }
    }
  } catch (error) {
    console.error('加载单元内容失败:', error)
    ElMessage.error('加载单元内容失败')
    unitContent.value = { texts: [], words: [] }
  } finally {
    loadingUnitContent.value = false
  }
}

// 单元变化事件
const onUnitChange = async (unitId) => {
  if (assignmentForm.assignmentType === 'TEXTS') {
    await loadArticles()
  } else if (unitId && assignmentForm.assignmentType !== 'UNIT') {
    await loadUnitContent(unitId)
    if (assignmentForm.assignmentType === 'WORDS') {
      autoSelectWordsByGroup()
    }
  } else {
    unitContent.value = { texts: [], words: [] }
  }
}

// 任务类型变化事件
const onAssignmentTypeChange = async (assignmentType) => {
  if (assignmentType === 'TEXTS') {
    assignmentForm.unitId = null
    unitContent.value = { texts: [], words: [] }
  } else if (assignmentType !== 'UNIT' && assignmentForm.unitId) {
    await loadUnitContent(assignmentForm.unitId)
  } else {
    unitContent.value = { texts: [], words: [] }
  }

  // 清空已选择的内容
  assignmentForm.textIds = []
  assignmentForm.wordIds = []
  if (assignmentType === 'TEXTS') {
    await loadArticles()
  } else if (assignmentType === 'WORDS') {
    autoSelectWordsByGroup()
  }
}

const onArticleTypeChange = async () => {
  assignmentForm.textIds = []
  assignmentForm.bookVolume = null
  assignmentForm.contentSection = null
  assignmentForm.unitNum = null
  resetOuterArticleFilters()
  await loadArticles()
}

const onBookVolumeChange = async () => {
  assignmentForm.textIds = []
  assignmentForm.contentSection = null
  assignmentForm.unitNum = null
  await loadArticles()
}

const onContentSectionChange = async () => {
  assignmentForm.textIds = []
  await loadArticles()
}

const loadArticles = async () => {
  if (assignmentForm.assignmentType !== 'TEXTS') return
  const params = new URLSearchParams({ articleType: assignmentForm.articleType || 'OUTER' })
  if (assignmentForm.articleType === 'INNER') {
    if (assignmentForm.bookVolume) params.append('bookVolume', assignmentForm.bookVolume)
    if (assignmentForm.contentSection) params.append('contentSection', assignmentForm.contentSection)
    if (assignmentForm.unitNum) params.append('unitNum', assignmentForm.unitNum)
  }
  const headers = {}
  const authHeader = getAuthHeader()
  if (authHeader) headers.Authorization = authHeader
  try {
    const response = await fetch(`/api/assignments/articles?${params.toString()}`, { headers })
    const data = await response.json()
    const texts = data.code === 200 && Array.isArray(data.data) ? data.data : []
    unitContent.value = { ...unitContent.value, texts }
    // Administrator-maintained in-class classifications are selected as a
    // group; extracurricular articles remain manual to avoid selecting a huge pool.
    assignmentForm.textIds = assignmentForm.articleType === 'INNER'
      ? texts.map(text => text.id)
      : []
    if (data.code !== 200) ElMessage.error(data.message || '加载文章失败')
  } catch (error) {
    console.error('加载文章失败:', error)
    unitContent.value = { ...unitContent.value, texts: [] }
    ElMessage.error('加载文章失败')
  }
}

const getTextCharacterCount = (text) => {
  const content = typeof text?.content === 'string' && text.content.length > 0
    ? text.content
    : (typeof text?.markdownContent === 'string' ? text.markdownContent : '')
  return content.length
}

const getTextClassificationLabel = (text) => {
  if (text.articleType !== 'INNER') return '课外读物'
  const details = [text.bookVolume, text.contentSection, text.unitNum ? `Unit ${text.unitNum}` : null]
    .filter(Boolean)
  return ['课内教材', ...details].join(' / ')
}

const saveAssignmentForSelectedClasses = async () => {
  if (!assignmentFormRef.value) return

  await assignmentFormRef.value.validate(async (valid) => {
    if (!valid) return
    saving.value = true
    try {
      const headers = { 'Content-Type': 'application/json' }
      const authHeader = getAuthHeader()
      if (authHeader) headers.Authorization = authHeader
      const selectedClassIds = Array.isArray(assignmentForm.classId)
        ? assignmentForm.classId
        : [assignmentForm.classId]
      const classIds = (isEdit.value ? [assignmentForm.classId] : selectedClassIds)
        .filter(classId => classId !== null && classId !== undefined && classId !== '')
      if (classIds.length === 0) {
        ElMessage.error('请选择班级')
        return
      }
      const isUnitAssignment = assignmentForm.assignmentType === 'UNIT'
      const results = []

      // The API stores one class per assignment, so create one record per selection.
      for (const classId of classIds) {
        const url = isEdit.value && isUnitAssignment
          ? `/api/assignments/${assignmentForm.id}`
          : (isUnitAssignment ? '/api/assignments' : '/api/assignments/create-with-content')
        const method = isEdit.value && isUnitAssignment ? 'PUT' : 'POST'
        const payload = isUnitAssignment
          ? {
              id: isEdit.value ? assignmentForm.id : null,
              title: assignmentForm.title,
              classId,
              unitId: assignmentForm.unitId,
              description: assignmentForm.description,
              deadline: assignmentForm.deadline,
              weight: assignmentForm.weight || 1.0,
              idealStudyTimeMinutes: assignmentForm.idealStudyTimeMinutes || 60
            }
          : {
              title: assignmentForm.title,
              classId,
              unitId: assignmentForm.unitId,
              assignmentType: assignmentForm.assignmentType,
              articleType: assignmentForm.articleType,
              bookVolume: assignmentForm.bookVolume,
              contentSection: assignmentForm.contentSection,
              unitNum: assignmentForm.unitNum,
              textIds: assignmentForm.textIds,
              wordIds: assignmentForm.wordIds,
              description: assignmentForm.description,
              deadline: assignmentForm.deadline,
              weight: assignmentForm.weight || 1.0,
              idealStudyTimeMinutes: assignmentForm.idealStudyTimeMinutes || 60
            }
        const response = await fetch(url, {
          method,
          headers,
          body: JSON.stringify(payload)
        })
        let data = null
        try {
          data = await response.json()
        } catch (error) {
          console.error('Failed to parse assignment response', error)
        }
        results.push({ success: response.ok && data?.code === 200, message: data?.message })
      }

      const failed = results.filter(result => !result.success)
      if (failed.length === 0) {
        ElMessage.success(isEdit.value ? '任务更新成功' : `任务已布置到 ${results.length} 个班级`)
        dialogVisible.value = false
        loadAssignments()
      } else {
        const succeeded = results.length - failed.length
        ElMessage.error(succeeded > 0
          ? `已成功布置 ${succeeded} 个班级，${failed.length} 个班级失败`
          : (failed[0].message || '操作失败'))
      }
    } catch (error) {
      console.error('Failed to save assignment', error)
      ElMessage.error('操作失败')
    } finally {
      saving.value = false
    }
  })
}

const saveAssignment = async () => {
  return saveAssignmentForSelectedClasses()

  /*
  if (!assignmentFormRef.value) return

  await assignmentFormRef.value.validate(async (valid) => {
    if (valid) {
      saving.value = true
      try {
        // 修复：提取token和fetchHeaders到try块开始处，避免在if-else分支中重复声明
        const fetchHeaders = { 'Content-Type': 'application/json' }
        const authHeader = getAuthHeader()
        if (authHeader) fetchHeaders['Authorization'] = authHeader

        // 根据任务类型选择不同的接口
        if (assignmentForm.assignmentType === 'UNIT') {
          // 兼容原有的单元任务接口
          const url = isEdit.value ? `/api/assignments/${assignmentForm.id}` : '/api/assignments'
          const method = isEdit.value ? 'PUT' : 'POST'

          const response = await fetch(url, {
            method,
            headers: fetchHeaders,
            body: JSON.stringify({
              id: assignmentForm.id,
              title: assignmentForm.title,
              classId: assignmentForm.classId,
              unitId: assignmentForm.unitId,
              description: assignmentForm.description,
              deadline: assignmentForm.deadline,
              weight: assignmentForm.weight || 1.0,  // 添加权重
              idealStudyTimeMinutes: assignmentForm.idealStudyTimeMinutes || 60  // 添加理想学习时长
            })
          })

          if (response.ok) {
            try {
              const data = await response.json()
              if (data.code === 200) {
                ElMessage.success(isEdit.value ? '任务更新成功' : '任务布置成功')
                dialogVisible.value = false
                loadAssignments()
              } else {
                ElMessage.error(data.message || '操作失败')
              }
            } catch (parseError) {
              console.error('响应解析失败:', parseError)
              ElMessage.error('操作失败')
            }
          } else {
            ElMessage.error('操作失败')
          }
        } else {
          // 使用新的内容任务接口
          const url = '/api/assignments/create-with-content'

          const response = await fetch(url, {
            method: 'POST',
            headers: fetchHeaders,
            body: JSON.stringify({
              title: assignmentForm.title,
              classId: assignmentForm.classId,
              unitId: assignmentForm.unitId,
              assignmentType: assignmentForm.assignmentType,
              articleType: assignmentForm.articleType,
              bookVolume: assignmentForm.bookVolume,
              contentSection: assignmentForm.contentSection,
              unitNum: assignmentForm.unitNum,
              textIds: assignmentForm.textIds,
              wordIds: assignmentForm.wordIds,
              description: assignmentForm.description,
              deadline: assignmentForm.deadline,
              weight: assignmentForm.weight || 1.0,  // 添加权重
              idealStudyTimeMinutes: assignmentForm.idealStudyTimeMinutes || 60  // 添加理想学习时长
            })
          })

          if (response.ok) {
            try {
              const data = await response.json()
              if (data.code === 200) {
                ElMessage.success('任务布置成功')
                dialogVisible.value = false
                loadAssignments()
              } else {
                ElMessage.error(data.message || '操作失败')
              }
            } catch (parseError) {
              console.error('响应解析失败:', parseError)
              ElMessage.error('操作失败')
            }
          } else {
            ElMessage.error('操作失败')
          }
        }
      } catch (error) {
        console.error('保存任务失败:', error)
        ElMessage.error('操作失败')
      } finally {
        saving.value = false
      }
    }
  })
  */
}

const viewProgress = async (assignment) => {
  try {
    currentAssignment.value = assignment

    const headers = {}
    const authHeader = getAuthHeader()
    if (authHeader) headers['Authorization'] = authHeader

    const response = await fetch(`/api/assignments/${assignment.id}/progress`, { headers })

    if (response.ok) {
      try {
        const data = await response.json()
        if (data.code === 200) {
          const progressData = Array.isArray(data.data) ? data.data : []
          studentProgress.value = progressData

          // 计算完成人数
          const completedCount = progressData.filter(student => student.completed).length
          currentAssignment.value = {
            ...assignment,
            completedCount: completedCount,
            studentCount: progressData.length
          }

          progressDialogVisible.value = true
          startProgressRefresh()
        } else {
          ElMessage.error(data.message || '加载进度信息失败')
        }
      } catch (parseError) {
        console.error('进度信息解析失败:', parseError)
        ElMessage.error('加载进度信息失败')
      }
    } else {
      ElMessage.error('加载进度信息失败')
    }
  } catch (error) {
    console.error('加载进度信息失败:', error)
    ElMessage.error('加载进度信息失败')
  }
}

const refreshCurrentProgress = async () => {
  const assignment = currentAssignment.value
  if (!progressDialogVisible.value || !assignment?.id) return

  try {
    const headers = {}
    const authHeader = getAuthHeader()
    if (authHeader) headers.Authorization = authHeader
    const response = await fetch(`/api/assignments/${assignment.id}/progress`, { headers })
    if (!response.ok) return
    const data = await response.json()
    if (data.code !== 200 || !Array.isArray(data.data)) return

    studentProgress.value = data.data
    currentAssignment.value = {
      ...currentAssignment.value,
      completedCount: data.data.filter(student => student.completed).length,
      studentCount: data.data.length
    }
  } catch (error) {
    console.debug('刷新任务实时进度失败:', error)
  }
}

const stopProgressRefresh = () => {
  if (progressRefreshTimer) {
    clearInterval(progressRefreshTimer)
    progressRefreshTimer = null
  }
}

const startProgressRefresh = () => {
  stopProgressRefresh()
  progressRefreshTimer = setInterval(refreshCurrentProgress, 5000)
}

const normalizeProgress = (value) => {
  const progress = Number(value)
  return Number.isFinite(progress) ? Math.min(100, Math.max(0, Math.round(progress))) : 0
}

const formatStudyDuration = (student) => {
  const rawSeconds = Number(student?.studyTimeSeconds)
  const seconds = Number.isFinite(rawSeconds)
    ? Math.max(0, Math.floor(rawSeconds))
    : Math.max(0, Math.round((Number(student?.studyTime) || 0) * 60))
  const hours = Math.floor(seconds / 3600)
  const minutes = Math.floor((seconds % 3600) / 60)
  const remainingSeconds = seconds % 60
  return hours > 0
    ? `${hours}时${minutes}分${remainingSeconds}秒`
    : `${minutes}分${remainingSeconds}秒`
}

const deleteAssignment = async (assignment) => {
  try {
    await ElMessageBox.confirm(`确认删除任务 "${assignment.title}" 吗？`, '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })

    const headers = {}
    const authHeader = getAuthHeader()
    if (authHeader) headers['Authorization'] = authHeader

    const response = await fetch(`/api/assignments/${assignment.id}`, {
      method: 'DELETE',
      headers
    })

    if (response.ok) {
      try {
        const data = await response.json()
        if (data.code === 200) {
          ElMessage.success('任务删除成功')
          loadAssignments()
        } else {
          ElMessage.error(data.message || '删除失败')
        }
      } catch (parseError) {
        console.error('删除响应解析失败:', parseError)
        ElMessage.error('删除失败')
      }
    } else {
      ElMessage.error('删除失败')
    }
  } catch (error) {
    if (error !== 'cancel') {
      console.error('删除任务失败:', error)
      ElMessage.error('删除失败')
    }
  }
}

const getCompletionType = (completed, total) => {
  if (!total || completed == null) return 'info'
  const rate = (completed / total) * 100
  if (rate >= 80) return 'success'
  if (rate >= 50) return 'warning'
  return 'danger'
}

const getCompletionRate = (assignment) => {
  if (!assignment.studentCount) return 0
  return Math.round(((assignment.completedCount || 0) / assignment.studentCount) * 100)
}

const getProgressColor = (percentage) => {
  if (percentage >= 80) return '#67c23a'
  if (percentage >= 50) return '#e6a23c'
  return '#f56c6c'
}

const getScoreType = (score) => {
  if (score >= 90) return 'success'
  if (score >= 80) return 'warning'
  if (score >= 60) return 'info'
  return 'danger'
}

// 计算任务真实状态
const calculateRealStatus = (deadline) => {
  if (!deadline) return 'active'

  const now = new Date()
  const deadlineDate = new Date(deadline)

  return now > deadlineDate ? 'ended' : 'active'
}

// 获取班级学生数量
const getStudentCountForClass = (classId) => {
  // 从班级列表中获取学生数量
  const classInfo = classes.value.find(cls => cls.id === classId)
  return classInfo ? (classInfo.studentCount || 0) : 0
}

// 任务完成人数缓存
const assignmentProgressCache = new Map()

// 获取任务完成人数
const getCompletedCountForAssignment = async (assignmentId) => {
  try {
    // 检查缓存
    if (assignmentProgressCache.has(assignmentId)) {
      const cached = assignmentProgressCache.get(assignmentId)
      // 缓存5分钟
      if (Date.now() - cached.timestamp < 5 * 60 * 1000) {
        return cached.completedCount
      }
    }

    const headers = {}
    const authHeader = getAuthHeader()
    if (authHeader) headers['Authorization'] = authHeader

    const response = await fetch(`/api/assignments/${assignmentId}/progress`, { headers })

    if (response.ok) {
      try {
        const data = await response.json()
        if (data.code === 200 && Array.isArray(data.data)) {
          // 计算完成人数（进度>=80%）
          const completedCount = data.data.filter(student => student.completed).length

          // 缓存结果
          assignmentProgressCache.set(assignmentId, {
            completedCount: completedCount,
            timestamp: Date.now()
          })

          return completedCount
        }
      } catch (parseError) {
        console.error(`任务${assignmentId}进度解析失败:`, parseError)
      }
    }
  } catch (error) {
    console.error(`获取任务${assignmentId}进度失败:`, error)
  }

  return 0
}

// 批量获取完成人数，控制并发数量
const batchGetCompletionCounts = async (assignmentsData) => {
  const BATCH_SIZE = 3 // 每批处理3个任务，避免过多并发请求
  const MAX_RETRIES = 2 // 最大重试次数

  for (let i = 0; i < assignmentsData.length; i += BATCH_SIZE) {
    const batch = assignmentsData.slice(i, i + BATCH_SIZE)

    await Promise.all(batch.map(async (assignment) => {
      let retries = 0
      let completedCount = 0

      while (retries <= MAX_RETRIES) {
        try {
          completedCount = await getCompletedCountForAssignment(assignment.id)
          assignment.completedCount = completedCount
          break // 成功则跳出重试循环
        } catch (error) {
          retries++
          console.error(`获取任务${assignment.id}完成人数失败(第${retries}次重试):`, error)

          if (retries > MAX_RETRIES) {
            console.error(`任务${assignment.id}完成人数获取失败，使用默认值0`)
            assignment.completedCount = 0
          } else {
            // 重试前等待（注意：此 setTimeout 会自动在 Promise resolve 后清理）
            await new Promise(resolve => {
                setTimeout(resolve, 500 * retries)
              // 如果需要主动清理，可以在组件卸载时调用
            })
          }
        }
      }
    }))

    // 在批次之间添加小延迟，避免服务器压力
    if (i + BATCH_SIZE < assignmentsData.length) {
      await new Promise(resolve => {
        setTimeout(resolve, 100)
        // 如果需要主动清理，可以在组件卸载时调用
      })
    }
  }

  return assignmentsData
}

const formatTime = (time) => {
  if (!time) return ''
  return new Date(time).toLocaleString()
}

// 监听班级选择变化，动态加载对应课程单元
watch(() => assignmentForm.classId, async (newClassId) => {
  const classId = Array.isArray(newClassId) ? newClassId[0] : newClassId
  if (classId) {
    await loadUnitsForClass(classId)
  } else {
    units.value = []
  }
})

watch(progressDialogVisible, (visible) => {
  if (!visible) stopProgressRefresh()
})

// 生命周期
onMounted(async () => {
  // 先加载班级数据，再加载任务数据
  await loadClasses()
  await loadAssignmentTemplates()
  await loadAssignments()
})

onBeforeUnmount(stopProgressRefresh)
</script>

<style scoped>
.assignments-management {
  padding: 20px;
}

.table-wrapper {
  overflow-x: auto;
  -webkit-overflow-scrolling: touch;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.header-actions {
  display: flex;
  gap: 8px;
}

.filter-bar {
  margin-bottom: 20px;
}

/* 快捷模板样式 */
.template-list {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
}

.template-card {
  flex: 1;
  min-width: 140px;
  padding: 16px;
  border: 2px solid #e4e7ed;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.25s;
  text-align: center;
  background: #fff;
}

.template-card:hover {
  border-color: #409eff;
  background: #f5f9ff;
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(64, 158, 255, 0.15);
}

.template-card.active {
  border-color: #409eff;
  background: #ecf5ff;
}

.template-actions {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 10px;
}

.template-manager-toolbar,
.template-manager-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.template-manager-toolbar {
  margin-bottom: 14px;
}

.template-manager-tip,
.template-manager-desc {
  color: #909399;
  font-size: 12px;
}

.template-manager-list {
  border-top: 1px solid #ebeef5;
}

.template-manager-item {
  padding: 12px 0;
  border-bottom: 1px solid #ebeef5;
}

.template-manager-name {
  color: #303133;
  font-weight: 600;
  margin-bottom: 4px;
}

.template-editor {
  margin-top: 18px;
  padding-top: 18px;
  border-top: 1px solid #ebeef5;
}

.template-editor-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}

.template-icon {
  font-size: 28px;
  margin-bottom: 8px;
}

.template-name {
  font-weight: bold;
  font-size: 15px;
  color: #303133;
  margin-bottom: 4px;
}

.template-desc {
  font-size: 12px;
  color: #909399;
}

.template-tip {
  font-size: 12px;
  color: #909399;
  margin-top: 8px;
}

.progress-overview {
  margin-bottom: 20px;
  padding: 20px;
  background-color: #f8f9fa;
  border-radius: 4px;
}

.stats-row {
  display: flex;
  justify-content: space-between;
  margin-bottom: 15px;
}

.stat-item {
  text-align: center;
}

.stat-item .label {
  color: #666;
  font-size: 14px;
}

.stat-item .value {
  font-size: 18px;
  font-weight: bold;
  color: #333;
}

.stat-item .value.completed {
  color: #67c23a;
}

.students-progress h4 {
  margin: 20px 0 10px;
  color: #333;
}

.content-selector {
  max-height: 300px;
  overflow-y: auto;
  border: 1px solid #e4e7ed;
  border-radius: 4px;
  padding: 10px;
}

.outer-article-tools {
  position: sticky;
  top: -10px;
  z-index: 1;
  margin: -10px -10px 10px;
  padding: 10px;
  background: #fff;
  border-bottom: 1px solid #ebeef5;
}

.outer-article-filters {
  display: flex;
  gap: 8px;
}

.outer-article-search {
  flex: 1;
}

.outer-article-difficulty {
  width: 110px;
}

.outer-article-tag-filter {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
  margin-top: 8px;
}

.tag-filter-label {
  color: #606266;
  font-size: 12px;
}

.tag-filter-item {
  cursor: pointer;
}

.outer-article-summary {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 8px;
  color: #909399;
  font-size: 12px;
}

.content-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
  gap: 10px;
}

.content-item {
  margin-bottom: 0 !important;
  padding: 10px;
  border: 1px solid #e4e7ed;
  border-radius: 4px;
  transition: all 0.3s;
}

.content-item:hover {
  border-color: #409eff;
  background-color: #f5f9ff;
}

.content-item.is-checked {
  border-color: #409eff;
  background-color: #f5f9ff;
}

.content-info {
  margin-left: 20px;
}

.content-title {
  font-weight: bold;
  font-size: 14px;
  color: #303133;
  margin-bottom: 5px;
}

.content-meta {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  font-size: 12px;
  color: #909399;
  margin-bottom: 3px;
}

.article-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}

.content-category {
  font-size: 12px;
  color: #409eff;
}

.content-desc {
  font-size: 12px;
  color: #606266;
  line-height: 1.4;
  max-width: 500px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 美化复选框样式 */
.content-item .el-checkbox__input.is-checked .el-checkbox__inner {
  background-color: #409eff;
  border-color: #409eff;
}

/* 滚动条样式 */
.content-selector::-webkit-scrollbar {
  width: 6px;
}

.content-selector::-webkit-scrollbar-track {
  background: #f1f1f1;
  border-radius: 3px;
}

.content-selector::-webkit-scrollbar-thumb {
  background: #c1c1c1;
  border-radius: 3px;
}

.content-selector::-webkit-scrollbar-thumb:hover {
  background: #a8a8a8;
}

@media (max-width: 768px) {
  .assignments-management {
    padding: 12px;
  }

  .filter-bar .el-form {
    display: block;
  }

  .filter-bar .el-form-item {
    margin-right: 0;
    margin-bottom: 12px;
  }

  .outer-article-filters {
    flex-direction: column;
  }

  .outer-article-difficulty {
    width: 100%;
  }

  .content-grid {
    grid-template-columns: 1fr;
  }
}
</style>
