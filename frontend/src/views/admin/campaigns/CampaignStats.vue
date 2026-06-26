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
              v-model="individualFilters.classId"
              placeholder="按班级筛选"
              clearable
              filterable
              style="width: 200px; margin-right: 10px"
              @change="loadIndividualRanking"
            >
              <el-option
                v-for="cls in classList"
                :key="cls.id"
                :label="cls.name"
                :value="cls.id"
              />
            </el-select>
            <el-select
              v-if="showCollegeFilter"
              v-model="individualFilters.collegeId"
              placeholder="按学院筛选"
              clearable
              filterable
              style="width: 200px; margin-right: 10px"
              @change="loadIndividualRanking"
            >
              <el-option
                v-for="college in collegeList"
                :key="college.id"
                :label="college.name"
                :value="college.id"
              />
            </el-select>
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
  exportRankingData
} from '@/api/campaign'

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
  classId: null,
  collegeId: null
})

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
    if (individualFilters.classId) {
      params.classId = individualFilters.classId
    }
    if (individualFilters.collegeId) {
      params.collegeId = individualFilters.collegeId
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
  const classMap = new Map()
  const collegeMap = new Map()

  records.forEach(record => {
    if (record.classId && record.className) {
      classMap.set(record.classId, record.className)
    }
    if (record.collegeId && record.collegeName) {
      collegeMap.set(record.collegeId, record.collegeName)
    }
  })

  classList.value = Array.from(classMap.entries()).map(([id, name]) => ({ id, name }))
  collegeList.value = Array.from(collegeMap.entries()).map(([id, name]) => ({ id, name }))
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
    const res = await exportRankingData(route.params.id, dimension, 'excel')

    // 创建下载链接
    const blob = new Blob([res], {
      type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet'
    })
    const url = window.URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = `${dimension}_ranking_${Date.now()}.xlsx`
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    window.URL.revokeObjectURL(url)

    ElMessage.success('导出成功')
  } catch (error) {
    ElMessage.error('导出失败: ' + error.message)
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
  // 假设总任务数从overview获取
  const totalTasks = overview.totalTasks || 10
  return Math.round((row.completedTasks / totalTasks) * 100)
}

const getProgressColor = (row) => {
  const progress = calculateProgress(row)
  if (progress >= 100) return '#67c23a'
  if (progress >= 50) return '#409eff'
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
