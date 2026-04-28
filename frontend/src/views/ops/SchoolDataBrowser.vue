<template>
  <div class="school-data-browser">
    <!-- 加载进度条 -->
    <div v-if="loadingProgress > 0 && loadingProgress < 100" class="loading-progress">
      <el-progress :percentage="loadingProgress" :status="loadingStatus" />
      <div class="progress-text">{{ loadingText }}</div>
    </div>

    <!-- 数据概览卡片 -->
    <div class="overview-cards" v-if="overview">
      <div v-for="(count, key) in overview" :key="key" class="overview-card" @click="activeTab = key">
        <div class="card-count">{{ count }}</div>
        <div class="card-label">{{ dataSourceLabels[key] || key }}</div>
      </div>
    </div>

    <!-- 同步操作区 -->
    <div class="sync-controls">
      <el-radio-group v-model="syncMode" size="small">
        <el-radio-button value="full">全量同步</el-radio-button>
        <el-radio-button value="incremental">增量同步</el-radio-button>
      </el-radio-group>
      <el-button size="small" @click="openFilterDialog">设置筛选条件</el-button>
      <el-button type="primary" size="small" :loading="syncing" @click="handleSync">
        同步当前数据源
      </el-button>
      <div v-if="currentFilters.length > 0" class="filter-summary">
        当前筛选: {{ filterSummary }}
      </div>
    </div>

    <!-- 链式拉取卡片 -->
    <div v-if="chainFetchEnabled" class="chain-fetch-card">
      <div class="card-header">
        <span class="card-title">按学院链式拉取</span>
        <span class="card-subtitle">课程 → 教师 → 学生 → 绑定关系</span>
      </div>
      <div class="card-body">
        <div class="input-row">
          <el-input
            v-model="chainCollegeName"
            placeholder="学院名称（如：外国语学院）"
            size="small"
            style="width: 200px"
          />
          <el-select v-model="chainMatchMode" size="small" style="width: 120px">
            <el-option value="eq" label="精确匹配" />
            <el-option value="contains" label="模糊匹配" />
          </el-select>
          <el-button
            type="primary"
            size="small"
            :loading="chainFetching"
            @click="handleChainFetch"
          >
            执行链式拉取
          </el-button>
        </div>
        <div v-if="chainResult" class="result-summary">
          <div class="result-item">
            <span class="result-label">课程数:</span>
            <span class="result-value">{{ chainResult.courseCount || 0 }}</span>
          </div>
          <div class="result-item">
            <span class="result-label">学生数:</span>
            <span class="result-value">{{ chainResult.studentCount || 0 }}</span>
          </div>
          <div class="result-item">
            <span class="result-label">教师数:</span>
            <span class="result-value">{{ chainResult.teacherCount || 0 }}</span>
          </div>
          <div class="result-item">
            <span class="result-label">绑定数:</span>
            <span class="result-value">{{ chainResult.bindingCount || 0 }}</span>
          </div>
          <div v-if="chainResult.upstreamKcbhSupported !== undefined" class="result-item">
            <span class="result-label">KCBH支持:</span>
            <el-tag :type="chainResult.upstreamKcbhSupported ? 'success' : 'warning'" size="small">
              {{ chainResult.upstreamKcbhSupported ? '是' : '否（客户端过滤）' }}
            </el-tag>
          </div>
        </div>
      </div>
    </div>

    <!-- 数据源Tab -->
    <el-tabs v-model="activeTab" @tab-change="handleTabChange">
      <el-tab-pane label="行政班" name="administrative_class">
        <el-table v-loading="loading" :data="tableData" stripe size="small" max-height="500">
          <el-table-column prop="classCode" label="班级编码" width="120" />
          <el-table-column prop="className" label="班级名称" min-width="150" />
          <el-table-column prop="collegeName" label="学院" min-width="120" />
          <el-table-column prop="majorName" label="专业" min-width="120" />
          <el-table-column prop="grade" label="年级" width="80" />
          <el-table-column prop="studentCount" label="人数" width="70" />
          <el-table-column prop="classStatus" label="状态" width="80" />
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="课程" name="school_course">
        <el-table v-loading="loading" :data="tableData" stripe size="small" max-height="500">
          <el-table-column prop="courseCode" label="课程编码" width="120" />
          <el-table-column prop="courseName" label="课程名称" min-width="180" />
          <el-table-column prop="courseNature" label="性质" width="80" />
          <el-table-column prop="courseCategory" label="类别" width="80" />
          <el-table-column prop="credits" label="学分" width="70" />
          <el-table-column prop="totalHours" label="学时" width="70" />
          <el-table-column prop="collegeName" label="学院" min-width="120" />
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="教学班 / 教师授课" name="teaching_class">
        <div class="tab-note">
          <el-icon><InfoFilled /></el-icon>
          <span>官方 9 个数据源中「教师授课」(/api/jssk) 的字段并入教学班表存储，在此 Tab 统一展示。</span>
        </div>
        <el-table v-loading="loading" :data="tableData" stripe size="small" max-height="500">
          <el-table-column prop="classId" label="教学班ID" width="120" />
          <el-table-column prop="courseName" label="课程" min-width="180" />
          <el-table-column prop="teacherName" label="教师" width="100" />
          <el-table-column prop="academicYear" label="学年" width="110" />
          <el-table-column prop="semester" label="学期" width="70" />
          <el-table-column prop="enrolledCount" label="选课人数" width="90" />
          <el-table-column prop="classStatus" label="状态" width="80" />
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="课程安排" name="course_schedule">
        <el-table v-loading="loading" :data="tableData" stripe size="small" max-height="500">
          <el-table-column prop="classId" label="教学班ID" width="120" />
          <el-table-column prop="dayOfWeek" label="星期" width="70" />
          <el-table-column prop="period" label="节次" width="80" />
          <el-table-column prop="weekRange" label="周次" width="100" />
          <el-table-column prop="classLocation" label="教室" min-width="120" />
          <el-table-column prop="academicYear" label="学年" width="110" />
          <el-table-column prop="semester" label="学期" width="70" />
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="选课记录" name="course_selection">
        <el-table v-loading="loading" :data="tableData" stripe size="small" max-height="500">
          <el-table-column prop="studentId" label="学号" width="130" />
          <el-table-column prop="studentName" label="姓名" width="90" />
          <el-table-column prop="courseName" label="课程" min-width="180" />
          <el-table-column prop="teacherName" label="教师" width="90" />
          <el-table-column prop="credits" label="学分" width="70" />
          <el-table-column prop="totalGrade" label="总成绩" width="80" />
          <el-table-column prop="selectionStatus" label="状态" width="80" />
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="等级考试" name="level_exam_scores">
        <el-table v-loading="loading" :data="tableData" stripe size="small" max-height="500">
          <el-table-column prop="studentId" label="学号" width="130" />
          <el-table-column prop="studentName" label="姓名" width="90" />
          <el-table-column prop="examType" label="类型" width="80" />
          <el-table-column prop="score" label="分数" width="70" />
          <el-table-column prop="isPassed" label="是否通过" width="80">
            <template #default="{ row }">
              <el-tag :type="row.isPassed ? 'success' : 'danger'" size="small">
                {{ row.isPassed ? '通过' : '未通过' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="examDate" label="考试日期" width="110" />
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="教学计划" name="teaching_plans">
        <el-table v-loading="loading" :data="tableData" stripe size="small" max-height="500">
          <el-table-column prop="planId" label="计划编号" width="120" />
          <el-table-column prop="planName" label="计划名称" min-width="200" />
          <el-table-column prop="majorName" label="专业" width="120" />
          <el-table-column prop="grade" label="年级" width="80" />
          <el-table-column prop="totalCredits" label="总学分" width="80" />
          <el-table-column prop="planStatus" label="状态" width="80" />
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="学生课表" name="student_schedule">
        <el-table v-loading="loading" :data="tableData" stripe size="small" max-height="500">
          <el-table-column prop="studentId" label="学号" width="130" />
          <el-table-column prop="studentName" label="姓名" width="90" />
          <el-table-column prop="courseName" label="课程" min-width="180" />
          <el-table-column prop="teacherName" label="教师" width="90" />
          <el-table-column prop="dayOfWeek" label="星期" width="70" />
          <el-table-column prop="period" label="节次" width="80" />
          <el-table-column prop="classLocation" label="教室" width="120" />
        </el-table>
      </el-tab-pane>
    </el-tabs>

    <!-- 分页 -->
    <div class="pagination-wrapper" v-if="total > 0">
      <el-pagination
        v-model:current-page="currentPage"
        v-model:page-size="pageSize"
        :total="total"
        :page-sizes="PAGE_SIZE_OPTIONS"
        layout="total, sizes, prev, pager, next"
        size="small"
        @current-change="loadData"
        @size-change="loadData"
      />
    </div>

    <!-- 筛选条件对话框 -->
    <el-dialog v-model="filterDialogVisible" title="设置筛选条件" width="90%" :append-to-body="true">
      <div class="filter-builder">
        <div v-for="(group, groupIndex) in filterGroups" :key="groupIndex" class="filter-group">
          <div class="group-header">
            <span class="group-label">条件组 {{ groupIndex + 1 }}</span>
            <el-radio-group v-model="group.relation" size="small">
              <el-radio-button value="AND">且 (AND)</el-radio-button>
              <el-radio-button value="OR">或 (OR)</el-radio-button>
            </el-radio-group>
            <el-button
              size="small"
              type="danger"
              text
              @click="removeGroup(groupIndex)"
              :disabled="filterGroups.length === 1"
            >
              删除组
            </el-button>
          </div>

          <div v-for="(condition, condIndex) in group.conditions" :key="condIndex" class="filter-condition">
            <el-select v-model="condition.field" placeholder="选择字段" size="small" style="width: 140px" @change="onFieldChange(condition)">
              <el-option
                v-for="field in availableFields"
                :key="field.field"
                :label="field.label"
                :value="field.field"
              />
            </el-select>
            <el-select v-model="condition.operator" placeholder="操作符" size="small" style="width: 100px">
              <el-option
                v-for="operator in availableOperators(condition.field)"
                :key="operator.value"
                :label="operator.label"
                :value="operator.value"
              />
            </el-select>
            <el-input v-model="condition.value" placeholder="值" size="small" style="width: 150px" />
            <el-button
              size="small"
              type="danger"
              text
              @click="removeCondition(groupIndex, condIndex)"
              :disabled="group.conditions.length === 1"
            >
              删除
            </el-button>
          </div>

          <el-button size="small" @click="addCondition(groupIndex)">+ 添加条件</el-button>
        </div>

        <el-button size="small" @click="addGroup">+ 添加条件组</el-button>
      </div>

      <template #footer>
        <el-button @click="filterDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="saveFilters">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { InfoFilled } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import {
  getSchoolDataOverview,
  getSchoolDataFilterFields,
  querySchoolData,
  fullSyncSchoolDataFiltered,
  incrementalSyncSchoolDataFiltered,
  chainFetchStudentsByCollege
} from '@/api/schoolData'
import { getFeatureToggles } from '@/api/systemConfig'

const DEFAULT_PAGE_SIZE = 20

const OPERATOR_LABELS = {
  eq: '等于',
  contains: '包含',
  gte: '大于等于',
  lte: '小于等于',
  in: '在列表中'
}

const dataSourceLabels = {
  administrative_class: '行政班',
  school_course: '课程',
  teaching_class: '教学班 / 教师授课',
  course_schedule: '课程安排',
  course_selection: '选课记录',
  level_exam_scores: '等级考试',
  teaching_plans: '教学计划',
  student_schedule: '学生课表'
}

const activeTab = ref('administrative_class')
const loading = ref(false)
const tableData = ref([])
const total = ref(0)
const currentPage = ref(1)
const pageSize = ref(DEFAULT_PAGE_SIZE)
const overview = ref(null)
const filterFields = ref([])

// 加载进度相关状态
const loadingProgress = ref(0)
const loadingStatus = ref('')
const loadingText = ref('')
let progressTimer = null

// 同步相关状态
const syncMode = ref('full')
const syncing = ref(false)
const filterDialogVisible = ref(false)
const filterGroups = ref([
  {
    relation: 'AND',
    conditions: [
      { field: '', label: '', operator: 'eq', value: '' }
    ]
  }
])
const currentFilters = ref([])

// 链式拉取相关状态
const chainFetchEnabled = ref(false)
const chainFetching = ref(false)
const chainCollegeName = ref('外国语学院')
const chainMatchMode = ref('eq')
const chainResult = ref(null)

const availableFields = computed(() => {
  return filterFields.value
})

function availableOperators(fieldName) {
  const fieldDef = availableFields.value.find(field => field.field === fieldName)
  return (fieldDef?.operators || ['eq']).map(value => ({ value, label: OPERATOR_LABELS[value] || value }))
}

const filterSummary = computed(() => {
  if (currentFilters.value.length === 0) return ''
  const parts = currentFilters.value.map(group => {
    const condStrs = group.conditions
      .filter(c => c.field && c.value)
      .map(c => {
        const capability = c.capabilityLabel ? ` [${c.capabilityLabel}]` : ''
        return `${c.label || c.field} ${c.operator} ${c.value}${capability}`
      })
    return condStrs.length > 0 ? `(${condStrs.join(` ${group.relation} `)})` : ''
  }).filter(s => s)
  return parts.join(' AND ')
})

// 进度条辅助函数
function startProgress(text = '加载中...') {
  loadingProgress.value = 0
  loadingStatus.value = ''
  loadingText.value = text

  if (progressTimer) clearInterval(progressTimer)

  // 模拟进度：前 30 秒快速增长到 80%，之后缓慢增长
  progressTimer = setInterval(() => {
    if (loadingProgress.value < 80) {
      loadingProgress.value += 2.67  // 30秒到80%
    } else if (loadingProgress.value < 95) {
      loadingProgress.value += 0.5   // 缓慢增长
    }
  }, 1000)
}

function completeProgress(success = true) {
  if (progressTimer) {
    clearInterval(progressTimer)
    progressTimer = null
  }

  loadingProgress.value = 100
  loadingStatus.value = success ? 'success' : 'exception'
  loadingText.value = success ? '加载完成' : '加载失败'

  // 2秒后隐藏进度条
  setTimeout(() => {
    loadingProgress.value = 0
    loadingStatus.value = ''
    loadingText.value = ''
  }, 2000)
}

function buildQueryPayload() {
  return {
    page: currentPage.value,
    size: pageSize.value,
    filterGroups: currentFilters.value
  }
}

async function loadFilterFields() {
  try {
    const res = await getSchoolDataFilterFields(activeTab.value)
    filterFields.value = res?.data || []
  } catch (e) {
    console.error('获取筛选字段失败:', e)
    filterFields.value = []
  }
}

async function loadOverview() {
  startProgress('正在加载数据概览...')
  try {
    const res = await getSchoolDataOverview()
    overview.value = res?.data || null
    completeProgress(true)
  } catch (e) {
    console.error('获取数据概览失败:', e)
    // 只有在真正失败时才显示错误（不是超时后成功的情况）
    if (!overview.value) {
      completeProgress(false)
    } else {
      completeProgress(true)  // 数据已加载，视为成功
    }
  }
}

async function loadData() {
  loading.value = true
  startProgress(`正在加载${dataSourceLabels[activeTab.value]}数据...`)
  try {
    const res = await querySchoolData(activeTab.value, buildQueryPayload())
    const pageData = res?.data
    tableData.value = pageData?.records || []
    total.value = pageData?.total || 0
    completeProgress(true)
  } catch (e) {
    console.error('获取数据失败:', e)
    // 只有在真正失败时才显示错误
    if (tableData.value.length === 0) {
      completeProgress(false)
    } else {
      completeProgress(true)  // 数据已加载，视为成功
    }
    tableData.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

async function handleTabChange() {
  currentPage.value = 1
  pageSize.value = DEFAULT_PAGE_SIZE
  currentFilters.value = []
  await loadFilterFields()
  loadData()
}

function openFilterDialog() {
  filterGroups.value = currentFilters.value.length > 0
    ? JSON.parse(JSON.stringify(currentFilters.value))
    : [{ relation: 'AND', conditions: [{ field: '', label: '', operator: 'eq', value: '' }] }]
  filterDialogVisible.value = true
}

function addGroup() {
  filterGroups.value.push({
    relation: 'AND',
    conditions: [{ field: '', label: '', operator: 'eq', value: '' }]
  })
}

function removeGroup(groupIndex) {
  if (filterGroups.value.length > 1) {
    filterGroups.value.splice(groupIndex, 1)
  }
}

function addCondition(groupIndex) {
  filterGroups.value[groupIndex].conditions.push({
    field: '',
    label: '',
    operator: 'eq',
    value: ''
  })
}

function removeCondition(groupIndex, condIndex) {
  const group = filterGroups.value[groupIndex]
  if (group.conditions.length > 1) {
    group.conditions.splice(condIndex, 1)
  }
}

function onFieldChange(condition) {
  const fieldDef = availableFields.value.find(f => f.field === condition.field)
  if (fieldDef) {
    condition.label = fieldDef.label
    condition.operator = fieldDef.operators?.[0] || 'eq'
    condition.capabilityLabel = formatCapability(fieldDef.capabilities)
  }
}

function formatCapability(capabilities = {}) {
  const labels = []
  if (capabilities.listQuery) labels.push('列表')
  if (capabilities.localSyncFilter) labels.push('同步后过滤')
  if (capabilities.upstreamSyncFilter) labels.push('上游过滤')
  return labels.join('/')
}

function saveFilters() {
  const validGroups = filterGroups.value
    .map(group => ({
      relation: group.relation,
      conditions: group.conditions.filter(c => c.field && c.value)
    }))
    .filter(group => group.conditions.length > 0)

  currentFilters.value = validGroups
  currentPage.value = 1
  filterDialogVisible.value = false
  ElMessage.success('筛选条件已保存')
  loadData()
}

async function handleSync() {
  syncing.value = true
  try {
    const payload = {
      triggerType: 'MANUAL',
      filterGroups: currentFilters.value
    }

    const syncFn = syncMode.value === 'full'
      ? fullSyncSchoolDataFiltered
      : incrementalSyncSchoolDataFiltered

    await syncFn(activeTab.value, payload)
    ElMessage.success(`${syncMode.value === 'full' ? '全量' : '增量'}同步成功`)
    await loadOverview()
    await loadData()
  } catch (e) {
    console.error('同步失败:', e)
  } finally {
    syncing.value = false
  }
}

async function handleChainFetch() {
  if (!chainCollegeName.value.trim()) {
    ElMessage.warning('请输入学院名称')
    return
  }

  chainFetching.value = true
  chainResult.value = null
  try {
    const res = await chainFetchStudentsByCollege({
      collegeName: chainCollegeName.value.trim(),
      matchMode: chainMatchMode.value,
      persist: true
    })

    chainResult.value = res?.data || null
    ElMessage.success('链式拉取完成')
    await loadOverview()
  } catch (e) {
    console.error('链式拉取失败:', e)
  } finally {
    chainFetching.value = false
  }
}

async function loadFeatureToggles() {
  try {
    const res = await getFeatureToggles()
    const toggles = res?.data || {}
    chainFetchEnabled.value = toggles['ops.school_data_chain_fetch'] === true || toggles['ops.school_data_chain_fetch'] === 'true'
  } catch (e) {
    console.error('获取功能开关失败:', e)
  }
}

onMounted(async () => {
  await loadFeatureToggles()
  await loadOverview()
  await loadFilterFields()
  await loadData()
})
</script>

<style scoped>
.school-data-browser {
  padding: 12px;
}

.loading-progress {
  position: fixed;
  top: 60px;
  left: 50%;
  transform: translateX(-50%);
  width: 400px;
  background: white;
  padding: 16px 20px;
  border-radius: 8px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
  z-index: 2000;
}

.progress-text {
  margin-top: 8px;
  font-size: 13px;
  color: #606266;
  text-align: center;
}

.overview-cards {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 16px;
}

.overview-card {
  background: var(--bg-primary);
  border: 1px solid var(--border-light);
  border-radius: 8px;
  padding: 12px 16px;
  min-width: 100px;
  cursor: pointer;
  transition: all 0.2s;
  text-align: center;
}

.overview-card:hover {
  border-color: var(--primary-color);
  box-shadow: 0 2px 8px rgba(0, 102, 204, 0.1);
}

.card-count {
  font-size: 24px;
  font-weight: 700;
  color: var(--primary-color);
}

.card-label {
  font-size: 12px;
  color: var(--text-tertiary);
  margin-top: 4px;
}

.sync-controls {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
  padding: 12px;
  background: var(--bg-secondary);
  border-radius: 8px;
  flex-wrap: wrap;
}

.filter-summary {
  font-size: 12px;
  color: var(--text-secondary);
  padding: 4px 8px;
  background: var(--bg-primary);
  border-radius: 4px;
  border: 1px solid var(--border-light);
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.pagination-wrapper {
  display: flex;
  justify-content: flex-end;
  margin-top: 12px;
}

.tab-note {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: var(--text-tertiary, #888);
  background: var(--bg-secondary, #f7f8fa);
  border-left: 3px solid var(--primary-color, #0066cc);
  padding: 8px 12px;
  margin-bottom: 8px;
  border-radius: 4px;
}

.filter-builder {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.filter-group {
  border: 1px solid var(--border-light);
  border-radius: 8px;
  padding: 12px;
  background: var(--bg-secondary);
}

.group-header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
  flex-wrap: wrap;
}

.group-label {
  font-weight: 600;
  color: var(--text-primary);
  font-size: 14px;
}

.filter-condition {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
  flex-wrap: wrap;
}

.chain-fetch-card {
  background: var(--bg-secondary);
  border: 1px solid var(--border-light);
  border-radius: 8px;
  padding: 12px;
  margin-bottom: 12px;
}

.card-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
}

.card-title {
  font-weight: 600;
  font-size: 14px;
  color: var(--text-primary);
}

.card-subtitle {
  font-size: 12px;
  color: var(--text-tertiary);
}

.card-body {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.input-row {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.result-summary {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 8px 12px;
  background: var(--bg-primary);
  border-radius: 4px;
  border: 1px solid var(--border-light);
  flex-wrap: wrap;
}

.result-item {
  display: flex;
  align-items: center;
  gap: 4px;
}

.result-label {
  font-size: 12px;
  color: var(--text-secondary);
}

.result-value {
  font-size: 14px;
  font-weight: 600;
  color: var(--primary-color);
}

</style>
