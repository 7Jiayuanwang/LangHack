<template>
  <div class="cohort-analysis" v-loading="loading">
    <div class="header-bar">
      <h2>班级画像分析</h2>
      <div class="controls">
        <el-select v-model="selectedClassId" placeholder="选择班级" @change="loadAnalysis" filterable>
          <el-option v-for="cls in classList" :key="cls.id" :label="cls.name" :value="cls.id" />
        </el-select>
      </div>
    </div>

    <template v-if="analysis">
      <el-row :gutter="16" class="overview-row">
        <el-col :span="6">
          <el-card shadow="hover">
            <el-statistic title="班级人数" :value="analysis.totalStudents" />
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card shadow="hover">
            <el-statistic title="平均7天活跃" :value="analysis.avgActiveDays7d.toFixed(1)" suffix="天" />
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card shadow="hover">
            <el-statistic title="平均14天时长" :value="analysis.avgStudyMinutes14d.toFixed(0)" suffix="分钟" />
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card shadow="hover">
            <el-statistic title="平均词汇弱势率" :value="(analysis.avgWeakRatio * 100).toFixed(1)" suffix="%" />
          </el-card>
        </el-col>
      </el-row>

      <el-row :gutter="16" class="section-row">
        <el-col :span="12">
          <el-card header="风险分布">
            <div class="risk-bars">
              <div v-for="(count, level) in analysis.riskDistribution" :key="level" class="risk-bar-item">
                <span class="risk-label">{{ riskLabel(level) }}</span>
                <el-progress
                  :percentage="analysis.totalStudents > 0 ? (count / analysis.totalStudents * 100) : 0"
                  :color="riskColor(level)"
                  :format="() => count + '人'"
                />
              </div>
            </div>
          </el-card>
        </el-col>
        <el-col :span="12">
          <el-card header="标签命中分布">
            <div class="tag-bars">
              <div v-for="(count, tag) in analysis.tagHitDistribution" :key="tag" class="tag-bar-item">
                <span class="tag-label">{{ tagDisplayName(tag) }}</span>
                <el-progress
                  :percentage="analysis.totalStudents > 0 ? (count / analysis.totalStudents * 100) : 0"
                  :format="() => count + '人'"
                />
              </div>
            </div>
          </el-card>
        </el-col>
      </el-row>

      <el-card header="学生分群" class="section-card" v-if="analysis.subgroups && analysis.subgroups.length > 0">
        <el-row :gutter="12">
          <el-col :span="8" v-for="group in analysis.subgroups" :key="group.subgroupName">
            <el-card shadow="hover" class="subgroup-card">
              <template #header>
                <span class="subgroup-name">{{ group.subgroupName }}</span>
                <el-tag size="small" type="info">{{ group.studentCount }}人</el-tag>
              </template>
              <div class="subgroup-stats">
                <p>平均活跃: {{ group.avgActiveDays7d.toFixed(1) }}天/周</p>
                <p v-if="group.avgStudyMinutes14d">时长: {{ group.avgStudyMinutes14d.toFixed(0) }}分钟/14天</p>
                <p v-if="group.avgWeakRatio">弱势率: {{ (group.avgWeakRatio * 100).toFixed(1) }}%</p>
              </div>
              <div class="subgroup-action">{{ group.suggestedAction }}</div>
            </el-card>
          </el-col>
        </el-row>
      </el-card>

      <el-card header="高风险学生" class="section-card" v-if="analysis.topRiskStudents && analysis.topRiskStudents.length > 0">
        <el-table :data="analysis.topRiskStudents" size="small" stripe>
          <el-table-column prop="realName" label="姓名" width="120">
            <template #default="{ row }">{{ row.realName || row.username }}</template>
          </el-table-column>
          <el-table-column prop="activeDays7d" label="7天活跃" width="100">
            <template #default="{ row }">{{ row.activeDays7d }}天</template>
          </el-table-column>
          <el-table-column prop="studyMinutes14d" label="14天时长" width="120">
            <template #default="{ row }">{{ row.studyMinutes14d }}分钟</template>
          </el-table-column>
          <el-table-column label="标签">
            <template #default="{ row }">
              <el-tag v-for="tag in (row.tags || [])" :key="tag.code" size="small"
                :type="tag.tagType === 'risk' ? 'danger' : tag.tagType === 'positive' ? 'success' : 'warning'"
                class="tag-item">
                {{ tag.displayName }}
              </el-tag>
            </template>
          </el-table-column>
        </el-table>
      </el-card>

      <el-card header="AI 队列摘要" class="section-card" v-if="analysis.llmCohortSummary">
        <div class="summary-text">{{ analysis.llmCohortSummary }}</div>
      </el-card>
    </template>

    <el-empty v-else-if="!loading && selectedClassId" description="无分析数据" />
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { getCohortAnalysis } from '@/api/learnerProfile'
import request from '@/utils/request'

const loading = ref(false)
const selectedClassId = ref(null)
const classList = ref([])
const analysis = ref(null)

const tagNameMap = {
  INACTIVE_RISK: '流失风险', VOCAB_WEAK: '词汇弱势', REVIEW_OVERDUE: '复习逾期',
  CONSISTENT_LEARNER: '学习稳定', CONTENT_LEVEL_STUCK: '版本停滞', CONTENT_LEVEL_JUMP: '版本跳跃',
  IMPROVING_TREND: '进步趋势', DECLINING_TREND: '下滑趋势', REVIEW_CHAMPION: '复习达人'
}
const tagDisplayName = (code) => tagNameMap[code] || code
const riskLabel = (level) => ({ LOW: '低风险', MEDIUM: '中风险', HIGH: '高风险' }[level] || level)
const riskColor = (level) => ({ LOW: '#67c23a', MEDIUM: '#e6a23c', HIGH: '#f56c6c' }[level] || '#909399')

async function loadClasses() {
  try {
    const res = await request({ url: '/api/admin/classes', method: 'get', params: { page: 1, pageSize: 100 } })
    classList.value = res?.data?.records || res?.data || []
  } catch (e) {
    console.error('加载班级列表失败:', e)
  }
}

async function loadAnalysis() {
  if (!selectedClassId.value) return
  loading.value = true
  analysis.value = null
  try {
    const res = await getCohortAnalysis(selectedClassId.value)
    analysis.value = res?.data || null
  } catch (e) {
    console.error('加载队列分析失败:', e)
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  loadClasses()
})
</script>

<style scoped>
.cohort-analysis { padding: 20px; }
.header-bar { display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px; }
.header-bar h2 { margin: 0; font-size: 18px; }
.overview-row { margin-bottom: 16px; }
.section-row { margin-bottom: 16px; }
.section-card { margin-bottom: 16px; }
.risk-bar-item, .tag-bar-item { margin-bottom: 8px; display: flex; align-items: center; gap: 8px; }
.risk-label, .tag-label { width: 80px; font-size: 13px; text-align: right; flex-shrink: 0; }
.risk-bar-item .el-progress, .tag-bar-item .el-progress { flex: 1; }
.subgroup-card .subgroup-name { font-weight: 600; margin-right: 8px; }
.subgroup-stats { font-size: 13px; color: var(--text-secondary, #666); line-height: 1.8; }
.subgroup-action { margin-top: 8px; font-size: 13px; color: var(--el-color-primary); background: var(--bg-secondary, #f0f9ff); padding: 8px; border-radius: 4px; }
.tag-item { margin: 2px; }
.summary-text { font-size: 14px; line-height: 1.6; color: var(--text-secondary, #666); white-space: pre-wrap; background: var(--bg-secondary, #f5f7fa); padding: 12px; border-radius: 8px; }
</style>
