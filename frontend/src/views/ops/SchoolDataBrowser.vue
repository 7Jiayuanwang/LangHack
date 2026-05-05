<template>
  <div class="school-data-browser">
    <!-- 加载进度条 -->
    <div v-if="loadingProgress > 0 && loadingProgress < 100" class="loading-progress-overlay">
      <div class="progress-card">
        <el-progress type="circle" :percentage="Math.round(loadingProgress)" :status="loadingStatus" :width="80" />
        <div class="progress-info">
          <div class="progress-title">数据加载中</div>
          <div class="progress-text">{{ loadingText }}</div>
        </div>
      </div>
    </div>

    <!-- 数据概览卡片 -->
    <div class="stats-grid" v-if="overview">
      <div 
        v-for="(count, key) in overview" 
        :key="key" 
        class="stat-card" 
        :class="{ active: activeTab === key }"
        @click="activeTab = key"
      >
        <div class="stat-icon-bg">
          <el-icon><DataLine /></el-icon>
        </div>
        <div class="stat-content">
          <div class="stat-value">{{ count.toLocaleString() }}</div>
          <div class="stat-label">{{ dataSourceLabels[key] || key }}</div>
        </div>
        <div class="stat-indicator"></div>
      </div>
    </div>

    <!-- 同步操作区 -->
    <div class="sync-controls">
      <el-radio-group v-model="syncMode" size="small">
        <el-radio-button value="full">全量同步</el-radio-button>
        <el-radio-button value="incremental">增量同步</el-radio-button>
      </el-radio-group>
      <el-checkbox v-if="activeTab === 'teacher_courses'" v-model="syncRelated" size="small" style="margin-left: 8px">
        联合同步相关数据源
      </el-checkbox>
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

    <!-- 数据导出按钮 -->
    <div class="export-controls" v-if="tableData.length > 0">
      <el-button @click="handleExport" :loading="exporting" type="success" size="small">
        <el-icon><Download /></el-icon> 导出当前数据 ({{ tableData.length }} 条)
      </el-button>
      <el-button @click="handleExportAll" :loading="exportingAll" type="primary" size="small">
        <el-icon><Download /></el-icon> 导出全部数据 ({{ total }} 条)
      </el-button>
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

      <el-tab-pane label="教学班 / 教师授课" name="teacher_courses">
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
import { InfoFilled, DataLine, Refresh, Filter, Connection, ArrowRight, Download } from '@element-plus/icons-vue'
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
  teacher_courses: '教学班 / 教师授课',
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
const syncRelated = ref(true)
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

// 导出相关状态
const exporting = ref(false)
const exportingAll = ref(false)

// 分页选项
const PAGE_SIZE_OPTIONS = [10, 20, 50, 100, 200]

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

    // 教学班 / 教师授课：根据用户选择决定是否联合同步
    if (activeTab.value === 'teacher_courses' && syncRelated.value) {
      await syncFn('teaching_class', payload)
      await syncFn('teacher_courses', payload)
      ElMessage.success(`${syncMode.value === 'full' ? '全量' : '增量'}同步完成（teaching_class + teacher_courses）`)
    } else {
      await syncFn(activeTab.value, payload)
      ElMessage.success(`${syncMode.value === 'full' ? '全量' : '增量'}同步成功`)
    }

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

// 导出当前页数据为 CSV
async function handleExport() {
  if (!tableData.value.length) {
    ElMessage.warning('没有可导出的数据')
    return
  }

  exporting.value = true
  try {
    const csvContent = generateCSV(tableData.value)
    downloadCSV(csvContent, `${dataSourceLabels[activeTab.value]}_当前页_${new Date().toISOString().slice(0, 10)}.csv`)
    ElMessage.success(`成功导出 ${tableData.value.length} 条数据`)
  } catch (error) {
    console.error('导出失败:', error)
    ElMessage.error('导出失败')
  } finally {
    exporting.value = false
  }
}

// 导出全部数据为 CSV
async function handleExportAll() {
  if (total.value === 0) {
    ElMessage.warning('没有可导出的数据')
    return
  }

  exportingAll.value = true
  try {
    // 分批获取所有数据
    const allData = []
    const batchSize = 100
    const totalPages = Math.ceil(total.value / batchSize)

    ElMessage.info(`开始导出全部 ${total.value} 条数据，共 ${totalPages} 页...`)

    for (let page = 1; page <= totalPages; page++) {
      const res = await querySchoolData(activeTab.value, {
        page: page,
        size: batchSize,
        filterGroups: currentFilters.value
      })

      if (res?.code === 200 && res.data?.records) {
        allData.push(...res.data.records)
      }

      // 显示进度
      if (page % 5 === 0 || page === totalPages) {
        ElMessage.info(`已加载 ${allData.length} / ${total.value} 条数据...`)
      }
    }

    const csvContent = generateCSV(allData)
    downloadCSV(csvContent, `${dataSourceLabels[activeTab.value]}_全部数据_${new Date().toISOString().slice(0, 10)}.csv`)
    ElMessage.success(`成功导出 ${allData.length} 条数据`)
  } catch (error) {
    console.error('导出失败:', error)
    ElMessage.error('导出失败')
  } finally {
    exportingAll.value = false
  }
}

// 生成 CSV 内容
function generateCSV(data) {
  if (!data || data.length === 0) {
    return ''
  }

  // 获取所有列名
  const columns = Object.keys(data[0]).filter(key => key !== 'deleted')

  // 列名映射
  const columnLabels = {
    id: 'ID',
    classCode: '班级代码',
    className: '班级名称',
    collegeCode: '学院代码',
    collegeName: '学院名称',
    majorCode: '专业代码',
    majorName: '专业名称',
    grade: '年级',
    studentCount: '学生人数',
    classStatus: '班级状态',
    courseCode: '课程代码',
    courseName: '课程名称',
    courseNature: '课程性质',
    courseCategory: '课程类别',
    credits: '学分',
    totalHours: '总学时',
    hours: '学时',
    classId: '教学班ID',
    teacherName: '教师姓名',
    teacherCode: '教师工号',
    academicYear: '学年',
    semester: '学期',
    enrolledCount: '选课人数',
    dayOfWeek: '星期',
    period: '节次',
    weekRange: '周次',
    classLocation: '上课地点',
    location: '地点',
    studentId: '学号',
    studentName: '学生姓名',
    totalGrade: '总成绩',
    selectionStatus: '选课状态',
    examType: '考试类型',
    score: '成绩',
    isPassed: '是否通过',
    examDate: '考试日期',
    planId: '计划编号',
    planName: '计划名称',
    totalCredits: '总学分',
    planStatus: '计划状态',
    updatedAt: '更新时间',
    createdAt: '创建时间'
  }

  // 生成表头
  const headers = columns.map(col => columnLabels[col] || col).join(',')

  // 生成数据行
  const rows = data.map(row => {
    return columns.map(col => {
      let value = row[col]

      // 格式化特殊字段
      if (col === 'semester') {
        value = value === 1 ? '秋' : value === 2 ? '春' : value
      } else if (col === 'dayOfWeek') {
        value = ['日', '一', '二', '三', '四', '五', '六'][value] || value
      } else if (col === 'isPassed') {
        value = value ? '通过' : '未通过'
      }

      // 处理 null/undefined
      if (value === null || value === undefined) {
        value = ''
      }

      // 转换为字符串
      value = String(value)

      // 处理包含逗号、引号或换行的值
      if (value.includes(',') || value.includes('"') || value.includes('\n')) {
        value = `"${value.replace(/"/g, '""')}"`
      }

      return value
    }).join(',')
  })

  return [headers, ...rows].join('\n')
}

// 下载 CSV 文件
function downloadCSV(csvContent, filename) {
  // 添加 BOM 以支持中文
  const BOM = '﻿'
  const blob = new Blob([BOM + csvContent], { type: 'text/csv;charset=utf-8;' })

  // 创建下载链接
  const link = document.createElement('a')
  const url = URL.createObjectURL(blob)

  link.setAttribute('href', url)
  link.setAttribute('download', filename)
  link.style.visibility = 'hidden'
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  URL.revokeObjectURL(url)
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
  max-width: 1200px;
  margin: 0 auto;
  padding: 24px;
}

.loading-progress-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(15, 23, 42, 0.6);
  backdrop-filter: blur(4px);
  z-index: 3000;
  display: flex;
  align-items: center;
  justify-content: center;
}

.progress-card {
  background: white;
  padding: 24px;
  border-radius: 24px;
  display: flex;
  align-items: center;
  gap: 16px;
  box-shadow: 0 20px 50px rgba(0, 0, 0, 0.2);
  width: 90%;
  max-width: 360px;
}

@media (max-width: 480px) {
  .school-data-browser {
    padding: 12px;
  }

  .stats-grid {
    grid-template-columns: repeat(2, 1fr);
    gap: 12px;
  }

  .stat-card {
    padding: 12px;
    flex-direction: column;
    text-align: center;
    gap: 8px;
  }

  .stat-icon-bg {
    width: 36px;
    height: 36px;
    font-size: 16px;
  }

  .stat-value {
    font-size: 18px;
  }

  .stat-label {
    font-size: 11px;
  }

  .sync-controls {
    padding: 12px;
    flex-direction: column;
    align-items: stretch;
  }

  .chain-fetch-card {
    padding: 16px;
  }

  .input-row {
    flex-direction: column;
    align-items: stretch;
  }

  .input-row :deep(.el-input),
  .input-row :deep(.el-select),
  .input-row .el-button {
    width: 100% !important;
  }
}

.progress-title {
  font-size: 18px;
  font-weight: 700;
  color: #1e293b;
  margin-bottom: 4px;
}

.progress-text {
  font-size: 14px;
  color: #64748b;
}

.stats-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
  gap: 16px;
  margin-bottom: 32px;
}

.stat-card {
  background: white;
  border: 1px solid #e2e8f0;
  border-radius: 20px;
  padding: 20px;
  display: flex;
  align-items: center;
  gap: 16px;
  cursor: pointer;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  position: relative;
  overflow: hidden;
}

.stat-card:hover {
  transform: translateY(-4px);
  box-shadow: 0 12px 24px rgba(0, 0, 0, 0.05);
  border-color: #4f46e5;
}

.stat-card.active {
  background: #f8fafc;
  border-color: #4f46e5;
  box-shadow: inset 0 2px 4px rgba(79, 70, 229, 0.05);
}

.stat-icon-bg {
  width: 48px;
  height: 48px;
  background: #f1f5f9;
  color: #64748b;
  border-radius: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 20px;
  transition: all 0.3s;
}

.stat-card:hover .stat-icon-bg,
.stat-card.active .stat-icon-bg {
  background: #eef2ff;
  color: #4f46e5;
}

.stat-value {
  font-size: 22px;
  font-weight: 800;
  color: #1e293b;
  line-height: 1;
}

.stat-label {
  font-size: 13px;
  font-weight: 600;
  color: #64748b;
  margin-top: 4px;
}

.stat-indicator {
  position: absolute;
  bottom: 0;
  left: 0;
  width: 100%;
  height: 4px;
  background: #4f46e5;
  transform: scaleX(0);
  transition: transform 0.3s;
}

.stat-card.active .stat-indicator {
  transform: scaleX(1);
}

.sync-controls {
  background: white;
  border: 1px solid #e2e8f0;
  border-radius: 20px;
  padding: 16px 24px;
  margin-bottom: 24px;
  display: flex;
  align-items: center;
  gap: 16px;
  flex-wrap: wrap;
  box-shadow: 0 4px 6px rgba(0, 0, 0, 0.02);
}

.export-controls {
  background: white;
  border: 1px solid #e2e8f0;
  border-radius: 20px;
  padding: 16px 24px;
  margin-bottom: 24px;
  display: flex;
  align-items: center;
  gap: 16px;
  flex-wrap: wrap;
  box-shadow: 0 4px 6px rgba(0, 0, 0, 0.02);
}

.filter-summary {
  font-size: 13px;
  color: #64748b;
  padding: 8px 12px;
  background: #f8fafc;
  border-radius: 8px;
  border: 1px solid #e2e8f0;
}

.chain-fetch-card {
  background: linear-gradient(135deg, #4f46e5 0%, #7c3aed 100%);
  border-radius: 24px;
  padding: 24px;
  margin-bottom: 32px;
  color: white;
  box-shadow: 0 10px 25px rgba(79, 70, 229, 0.2);
}

.card-title {
  font-size: 18px;
  font-weight: 800;
  letter-spacing: 0.5px;
}

.card-subtitle {
  font-size: 13px;
  opacity: 0.8;
}

.result-summary {
  background: rgba(255, 255, 255, 0.1);
  backdrop-filter: blur(10px);
  border-radius: 16px;
  border: 1px solid rgba(255, 255, 255, 0.1);
  margin-top: 16px;
}

.result-value {
  color: white;
  font-weight: 800;
}

:deep(.el-tabs__item) {
  font-weight: 600;
  font-size: 15px;
}

:deep(.el-table) {
  border-radius: 16px;
  overflow: hidden;
  box-shadow: 0 10px 30px rgba(0, 0, 0, 0.03);
}

.pagination-wrapper {
  margin-top: 24px;
  background: white;
  padding: 16px;
  border-radius: 16px;
  border: 1px solid #e2e8f0;
  display: flex;
  justify-content: center;
}

.filter-builder {
  max-height: 60vh;
  overflow-y: auto;
  padding: 8px;
}

.filter-group {
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 16px;
  padding: 20px;
  margin-bottom: 16px;
}

.group-header {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 16px;
  padding-bottom: 12px;
  border-bottom: 2px solid #e2e8f0;
}

.group-label {
  font-weight: 700;
  color: #334155;
  font-size: 15px;
}

.filter-condition {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
  padding: 12px;
  background: white;
  border-radius: 12px;
  border: 1px solid #e2e8f0;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.card-body {
  padding: 0;
}

.input-row {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.result-summary {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  padding: 16px;
}

.result-item {
  display: flex;
  align-items: center;
  gap: 8px;
}

.result-label {
  font-size: 13px;
  opacity: 0.9;
}

.result-value {
  font-size: 16px;
  font-weight: 700;
}

.tab-note {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 16px;
  background: #eff6ff;
  border: 1px solid #bfdbfe;
  border-radius: 12px;
  margin-bottom: 16px;
  font-size: 13px;
  color: #1e40af;
}

</style>
