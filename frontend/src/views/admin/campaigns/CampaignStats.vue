<template>
  <div class="campaign-stats-container">
    <div class="header">
      <h2>活动统计</h2>
      <div class="actions">
        <el-button @click="handleBack">返回</el-button>
        <el-button type="primary" @click="handleCollectData" :loading="collecting">
          手动采集数据
        </el-button>
      </div>
    </div>

    <el-card class="overview-card">
      <template #header>
        <span>活动概览</span>
      </template>
      <el-row :gutter="20">
        <el-col :span="6">
          <div class="stat-item">
            <div class="stat-value">{{ overview.totalParticipants || 0 }}</div>
            <div class="stat-label">总参与人数</div>
          </div>
        </el-col>
        <el-col :span="6">
          <div class="stat-item">
            <div class="stat-value">{{ overview.completedParticipants || 0 }}</div>
            <div class="stat-label">已完成人数</div>
          </div>
        </el-col>
        <el-col :span="6">
          <div class="stat-item">
            <div class="stat-value">{{ overview.inProgressParticipants || 0 }}</div>
            <div class="stat-label">进行中人数</div>
          </div>
        </el-col>
        <el-col :span="6">
          <div class="stat-item">
            <div class="stat-value">{{ overview.completionRate || '0%' }}</div>
            <div class="stat-label">完成率</div>
          </div>
        </el-col>
      </el-row>
    </el-card>

    <el-card class="ranking-card">
      <el-tabs v-model="activeTab" @tab-change="handleTabChange">
        <el-tab-pane label="个人排名" name="individual">
          <div class="tab-actions">
            <el-select
              v-model="individualFilters.className"
              placeholder="按班级筛选"
              clearable
              filterable
              style="width: 200px; margin-right: 10px"
              @change="loadIndividualRanking"
            >
              <el-option
                v-for="cls in classList"
                :key="cls"
                :label="cls"
                :value="cls"
              />
            </el-select>
            <el-select
              v-if="showCollegeFilter"
              v-model="individualFilters.collegeName"
              placeholder="按学院筛选"
              clearable
              filterable
              style="width: 200px; margin-right: 10px"
              @change="loadIndividualRanking"
            >
              <el-option
                v-for="college in collegeList"
                :key="college"
                :label="college"
                :value="college"
              />
            </el-select>
            <el-date-picker
              v-model="individualWeek"
              type="week"
              format="YYYY 第 ww 周"
              placeholder="按周筛选"
              value-format="YYYY-MM-DD"
              clearable
              size="default"
              style="width: 180px; margin-right: 10px"
              @change="loadIndividualRanking"
            />
            <el-button type="success" @click="handleExport('individual')">导出排名</el-button>
          </div>
          <el-table :data="individualRanking" v-loading="loading" stripe>
            <el-table-column label="排名" width="80">
              <template #default="{ $index }">
                <span class="rank-badge" :class="getRankClass($index + 1)">
                  {{ $index + 1 }}
                </span>
              </template>
            </el-table-column>
            <el-table-column prop="userName" label="姓名" width="120" />
            <el-table-column prop="className" label="班级" min-width="150" />
            <el-table-column prop="collegeName" label="学院" min-width="150" />
            <el-table-column prop="totalScore" label="总得分" width="100" sortable />
            <el-table-column prop="completedTasks" label="完成任务数" width="120" sortable />
            <el-table-column label="完成进度" width="150">
              <template #default="{ row }">
                <el-progress
                  :percentage="calculateProgress(row)"
                  :color="getProgressColor(row)"
                />
              </template>
            </el-table-column>
            <el-table-column label="状态" width="100">
              <template #default="{ row }">
                <el-tag :type="getCompletionStatusType(row.completionStatus)">
                  {{ getCompletionStatusLabel(row.completionStatus) }}
                </el-tag>
              </template>
            </el-table-column>
          </el-table>
          <el-pagination
            v-model:current-page="individualPagination.current"
            v-model:page-size="individualPagination.size"
            :total="individualPagination.total"
            :page-sizes="[20, 50, 100]"
            layout="total, sizes, prev, pager, next, jumper"
            @size-change="loadIndividualRanking"
            @current-change="loadIndividualRanking"
            class="pagination"
          />
        </el-tab-pane>

        <el-tab-pane label="班级排名" name="class">
          <div class="tab-actions">
            <el-button type="success" @click="handleExport('class')">导出排名</el-button>
          </div>
          <el-table :data="classRanking" v-loading="loading" stripe>
            <el-table-column label="排名" width="80">
              <template #default="{ $index }">
                <span class="rank-badge" :class="getRankClass($index + 1)">
                  {{ $index + 1 }}
                </span>
              </template>
            </el-table-column>
            <el-table-column prop="className" label="班级" min-width="200" />
            <el-table-column prop="collegeName" label="学院" min-width="200" />
            <el-table-column prop="totalScore" label="总得分" width="120" sortable />
            <el-table-column prop="participantCount" label="参与人数" width="120" />
            <el-table-column prop="avgScore" label="平均分" width="100" sortable />
            <el-table-column prop="completionRate" label="完成率" width="100">
              <template #default="{ row }">
                {{ row.completionRate }}%
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <el-tab-pane label="学院排名" name="college">
          <div class="tab-actions">
            <el-button type="success" @click="handleExport('college')">导出排名</el-button>
          </div>
          <el-table :data="collegeRanking" v-loading="loading" stripe>
            <el-table-column label="排名" width="80">
              <template #default="{ $index }">
                <span class="rank-badge" :class="getRankClass($index + 1)">
                  {{ $index + 1 }}
                </span>
              </template>
            </el-table-column>
            <el-table-column prop="collegeName" label="学院" min-width="300" />
            <el-table-column prop="totalScore" label="总得分" width="120" sortable />
            <el-table-column prop="participantCount" label="参与人数" width="120" />
            <el-table-column prop="avgScore" label="平均分" width="100" sortable />
            <el-table-column prop="completionRate" label="完成率" width="100">
              <template #default="{ row }">
                {{ row.completionRate }}%
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <el-tab-pane label="每周优秀" name="weeklyAwards">
          <div class="tab-actions">
            <el-date-picker
              v-model="awardsWeek"
              type="week"
              format="YYYY 第 ww 周"
              placeholder="选择周"
              value-format="YYYY-MM-DD"
              clearable
              size="default"
              style="width: 180px"
              @change="loadWeeklyAwards"
            />
          </div>
          <div v-if="weeklyAwards.topIndividuals && weeklyAwards.topIndividuals.length" class="awards-container">
            <div class="award-panel">
              <h4>🏅 优秀个人</h4>
              <el-table :data="weeklyAwards.topIndividuals" stripe>
                <el-table-column label="排名" width="80">
                  <template #default="{ row }">
                    <span class="rank-badge" :class="getRankClass(row.rank)">{{ row.rank }}</span>
                  </template>
                </el-table-column>
                <el-table-column prop="userName" label="姓名" min-width="150" />
                <el-table-column prop="className" label="班级" min-width="200" />
                <el-table-column prop="score" label="本周得分" width="120" sortable />
              </el-table>
            </div>
            <div class="award-panel" style="margin-top:20px">
              <h4>🏆 优秀班级</h4>
              <el-table :data="weeklyAwards.topClasses" stripe>
                <el-table-column label="排名" width="80">
                  <template #default="{ row }">
                    <span class="rank-badge" :class="getRankClass(row.rank)">{{ row.rank }}</span>
                  </template>
                </el-table-column>
                <el-table-column prop="className" label="班级" min-width="250" />
                <el-table-column prop="collegeName" label="学院" min-width="200" />
                <el-table-column prop="score" label="本周得分" width="120" sortable />
              </el-table>
            </div>
          </div>
          <el-empty v-else description="暂无本周数据" />
        </el-tab-pane>
      </el-tabs>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  getCampaignOverview,
  getIndividualRanking,
  getClassRanking,
  getCollegeRanking,
  collectData,
  exportRankingData,
  getAdminWeeklyAwards
} from '@/api/campaign'
import {
  normalizeDownloadResponse,
  extractBlobErrorMessage,
  triggerBlobDownload
} from '@/utils/blobDownload'

const router = useRouter()
const route = useRoute()
const loading = ref(false)
const collecting = ref(false)
const activeTab = ref('individual')

const overview = reactive({
  totalParticipants: 0,
  completedParticipants: 0,
  inProgressParticipants: 0,
  completionRate: '0%'
})

const individualRanking = ref([])
const classRanking = ref([])
const collegeRanking = ref([])
const classList = ref([])
const collegeList = ref([])

const individualFilters = reactive({
  className: null,
  collegeName: null
})

const individualWeek = ref(null)

// 周选择器可能回传周日；库表 week_start 为周一，必须归一
const toMonday = (input) => {
  let y, m, day
  if (typeof input === 'string' && /^\d{4}-\d{2}-\d{2}/.test(input)) {
    ;[y, m, day] = input.slice(0, 10).split('-').map(Number)
  } else {
    const d = input ? new Date(input) : new Date()
    if (Number.isNaN(d.getTime())) return null
    y = d.getFullYear()
    m = d.getMonth() + 1
    day = d.getDate()
  }
  const utc = new Date(Date.UTC(y, m - 1, day, 12, 0, 0))
  const dow = utc.getUTCDay()
  const diff = dow === 0 ? -6 : 1 - dow
  utc.setUTCDate(utc.getUTCDate() + diff)
  const yy = utc.getUTCFullYear()
  const mm = String(utc.getUTCMonth() + 1).padStart(2, '0')
  const dd = String(utc.getUTCDate()).padStart(2, '0')
  return `${yy}-${mm}-${dd}`
}

// 周优秀
const awardsWeek = ref(toMonday())
const weeklyAwards = ref({})

const loadWeeklyAwards = async () => {
  try {
    const params = {}
    if (awardsWeek.value) {
      awardsWeek.value = toMonday(awardsWeek.value)
      params.weekStart = awardsWeek.value
    }
    const res = await getAdminWeeklyAwards(route.params.id, params)
    if (res.code === 200) {
      weeklyAwards.value = res.data || {}
    }
  } catch (e) {
    console.error('加载周优秀失败', e)
  }
}

// 是否显示学院筛选（单学院活动不显示）
const showCollegeFilter = computed(() => {
  return collegeList.value.length > 1
})

const individualPagination = reactive({
  current: 1,
  size: 20,
  total: 0
})

const loadOverview = async () => {
  try {
    const res = await getCampaignOverview(route.params.id)
    if (res.code === 200) {
      Object.assign(overview, res.data)
    }
  } catch (error) {
    console.error('加载概览失败:', error)
  }
}

const loadIndividualRanking = async () => {
  loading.value = true
  try {
    const params = {
      current: individualPagination.current,
      size: individualPagination.size
    }

    // 添加筛选参数
    if (individualFilters.className) {
      params.className = individualFilters.className
    }
    if (individualFilters.collegeName) {
      params.collegeName = individualFilters.collegeName
    }
    if (individualWeek.value) {
      individualWeek.value = toMonday(individualWeek.value)
      params.weekStart = individualWeek.value
    }

    const res = await getIndividualRanking(route.params.id, params)
    if (res.code === 200) {
      individualRanking.value = res.data.records || []
      individualPagination.total = res.data.total || 0

      // 提取班级和学院列表用于筛选
      if (!classList.value.length || !collegeList.value.length) {
        extractFiltersFromData(res.data.records || [])
      }
    } else {
      ElMessage.error(res.message || '加载失败')
    }
  } catch (error) {
    ElMessage.error('加载失败: ' + error.message)
  } finally {
    loading.value = false
  }
}

// 从数据中提取班级和学院列表
const extractFiltersFromData = (records) => {
  const classSet = new Set()
  const collegeSet = new Set()

  records.forEach(record => {
    if (record.className) {
      classSet.add(record.className)
    }
    if (record.collegeName) {
      collegeSet.add(record.collegeName)
    }
  })

  classList.value = Array.from(classSet)
  collegeList.value = Array.from(collegeSet)
}

const loadClassRanking = async () => {
  loading.value = true
  try {
    const res = await getClassRanking(route.params.id)
    if (res.code === 200) {
      classRanking.value = res.data || []
    } else {
      ElMessage.error(res.message || '加载失败')
    }
  } catch (error) {
    ElMessage.error('加载失败: ' + error.message)
  } finally {
    loading.value = false
  }
}

const loadCollegeRanking = async () => {
  loading.value = true
  try {
    const res = await getCollegeRanking(route.params.id)
    if (res.code === 200) {
      collegeRanking.value = res.data || []
    } else {
      ElMessage.error(res.message || '加载失败')
    }
  } catch (error) {
    ElMessage.error('加载失败: ' + error.message)
  } finally {
    loading.value = false
  }
}

const handleTabChange = (tab) => {
  if (tab === 'individual' && individualRanking.value.length === 0) {
    loadIndividualRanking()
  } else if (tab === 'class' && classRanking.value.length === 0) {
    loadClassRanking()
  } else if (tab === 'college' && collegeRanking.value.length === 0) {
    loadCollegeRanking()
  } else if (tab === 'weeklyAwards' && !weeklyAwards.value.topIndividuals) {
    loadWeeklyAwards()
  }
}

const handleCollectData = async () => {
  collecting.value = true
  try {
    const res = await collectData(route.params.id)
    if (res.code === 200) {
      ElMessage.success('数据采集完成')
      loadOverview()
      if (activeTab.value === 'individual') {
        loadIndividualRanking()
      } else if (activeTab.value === 'class') {
        loadClassRanking()
      } else if (activeTab.value === 'college') {
        loadCollegeRanking()
      }
    } else {
      ElMessage.error(res.message || '采集失败')
    }
  } catch (error) {
    ElMessage.error('采集失败: ' + error.message)
  } finally {
    collecting.value = false
  }
}

const handleExport = async (dimension) => {
  try {
    const raw = await exportRankingData(route.params.id, dimension, 'excel')

    // 兼容拦截器返回的完整 axios response 对象或裸 Blob 两种情况
    const dimLabel = { individual: '个人排名', class: '班级排名', college: '学院排名' }[dimension] || '排名'
    const normalized = await normalizeDownloadResponse(
      raw,
      `活动_${dimLabel}_${Date.now()}.xlsx`,
      'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet'
    )

    // 后端出错时可能返回 JSON 错误体（content-type: application/json）
    if (normalized.contentType && normalized.contentType.includes('application/json')) {
      const errMsg = await extractBlobErrorMessage(normalized.blob, '导出失败')
      ElMessage.error(errMsg)
      return
    }

    triggerBlobDownload(normalized.blob, normalized.filename)
    ElMessage.success('导出成功')
  } catch (error) {
    ElMessage.error('导出失败: ' + (error?.message || error))
  }
}

const handleBack = () => {
  router.push(`/admin/campaigns/${route.params.id}`)
}

const getRankClass = (rank) => {
  if (rank === 1) return 'rank-gold'
  if (rank === 2) return 'rank-silver'
  if (rank === 3) return 'rank-bronze'
  return ''
}

const calculateProgress = (row) => {
  const requiredCount = overview.requiredTaskCount || 0
  const completionRule = overview.completionRule || 'ALL'
  const threshold = overview.completionThreshold || 0
  const totalTasks = overview.taskCount || 0
  const completedTasks = row.completedTasks || 0

  // 有必做任务：进度 = 已完成任务数 / 必做任务数（必做全完成即100%）
  if (requiredCount > 0) {
    return Math.min(Math.round((completedTasks / requiredCount) * 100), 100)
  }

  // 完成任意一个即算完成：用状态映射
  if (completionRule === 'ANY') {
    const statusMap = { COMPLETED: 100, IN_PROGRESS: 50, NOT_STARTED: 0 }
    return statusMap[row.completionStatus] ?? 0
  }

  // 达到阈值模式：进度 = 已完成 / 阈值
  if (completionRule === 'THRESHOLD' && threshold > 0) {
    return Math.min(Math.round((completedTasks / threshold) * 100), 100)
  }

  // 全部完成模式（或兜底）：进度 = 已完成 / 总任务数
  if (totalTasks > 0) {
    return Math.min(Math.round((completedTasks / totalTasks) * 100), 100)
  }

  return 0
}

const getProgressColor = (row) => {
  const status = row.completionStatus
  if (status === 'COMPLETED') return '#67c23a'
  if (status === 'IN_PROGRESS') return '#409eff'
  return '#e6a23c'
}

const getCompletionStatusType = (status) => {
  const types = {
    NOT_STARTED: 'info',
    IN_PROGRESS: 'warning',
    COMPLETED: 'success'
  }
  return types[status] || 'info'
}

const getCompletionStatusLabel = (status) => {
  const labels = {
    NOT_STARTED: '未开始',
    IN_PROGRESS: '进行中',
    COMPLETED: '已完成'
  }
  return labels[status] || status
}

onMounted(() => {
  loadOverview()
  loadIndividualRanking()
})
</script>

<style scoped>
.campaign-stats-container {
  padding: 20px;
}

.header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.header h2 {
  margin: 0;
}

.actions {
  display: flex;
  gap: 10px;
}

.overview-card {
  margin-bottom: 20px;
}

.stat-item {
  text-align: center;
  padding: 20px;
  background: #f5f7fa;
  border-radius: 4px;
}

.stat-value {
  font-size: 32px;
  font-weight: bold;
  color: #409eff;
  margin-bottom: 10px;
}

.stat-label {
  font-size: 14px;
  color: #909399;
}

.ranking-card {
  min-height: 500px;
}

.tab-actions {
  margin-bottom: 15px;
  text-align: right;
}

.rank-badge {
  display: inline-block;
  width: 32px;
  height: 32px;
  line-height: 32px;
  text-align: center;
  border-radius: 50%;
  font-weight: bold;
  background: #f5f7fa;
  color: #606266;
}

.rank-badge.rank-gold {
  background: linear-gradient(135deg, #ffd700 0%, #ffed4e 100%);
  color: #fff;
  box-shadow: 0 2px 8px rgba(255, 215, 0, 0.4);
}

.rank-badge.rank-silver {
  background: linear-gradient(135deg, #c0c0c0 0%, #e8e8e8 100%);
  color: #fff;
  box-shadow: 0 2px 8px rgba(192, 192, 192, 0.4);
}

.rank-badge.rank-bronze {
  background: linear-gradient(135deg, #cd7f32 0%, #e6a055 100%);
  color: #fff;
  box-shadow: 0 2px 8px rgba(205, 127, 50, 0.4);
}

.pagination {
  margin-top: 20px;
  display: flex;
  justify-content: flex-end;
}
</style>
