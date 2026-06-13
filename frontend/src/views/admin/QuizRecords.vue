<template>
  <div class="quiz-records-management">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>做题管理</span>
        </div>
      </template>

      <!-- 筛选栏 -->
      <div class="filter-bar">
        <el-form :inline="true" :model="filterForm">
          <el-form-item label="学生ID">
            <el-input v-model="filterForm.studentId" placeholder="输入学生ID" clearable style="width: 140px" />
          </el-form-item>
          <el-form-item label="课文ID">
            <el-input v-model="filterForm.textId" placeholder="输入课文ID" clearable style="width: 140px" />
          </el-form-item>
          <el-form-item label="时间范围">
            <el-date-picker
              v-model="filterForm.dateRange"
              type="datetimerange"
              range-separator="至"
              start-placeholder="开始时间"
              end-placeholder="结束时间"
              value-format="YYYY-MM-DDTHH:mm:ss"
              style="width: 360px"
            />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" @click="loadAttempts">查询</el-button>
          </el-form-item>
        </el-form>
      </div>

      <!-- 做题记录列表 -->
      <el-table v-loading="loading" :data="attempts" stripe>
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="studentId" label="学生ID" width="100" />
        <el-table-column label="学生姓名" width="120">
          <template #default="scope">{{ scope.row.studentRealName || scope.row.studentUsername || '-' }}</template>
        </el-table-column>
        <el-table-column prop="textId" label="课文ID" width="100" />
        <el-table-column prop="textTitle" label="课文标题" min-width="160" show-overflow-tooltip />
        <el-table-column label="题数" width="80">
          <template #default="scope">{{ scope.row.totalQuestions ?? scope.row.questionCount ?? '-' }}</template>
        </el-table-column>
        <el-table-column prop="correctCount" label="正确数" width="80" />
        <el-table-column label="正确率" width="100">
          <template #default="scope">
            <el-tag :type="getAccuracyType(scope.row)">
              {{ getAccuracyText(scope.row) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="totalTimeSeconds" label="用时(秒)" width="100" />
        <el-table-column prop="submittedTime" label="做题时间" width="180">
          <template #default="scope">
            {{ formatTime(scope.row.submittedTime || scope.row.createdAt) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="scope">
            <el-button size="small" type="primary" link @click="viewDetail(scope.row)">
              查看详情
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
      <div class="pagination" v-if="total > 0">
        <el-pagination
          v-model:current-page="pagination.current"
          v-model:page-size="pagination.size"
          :page-sizes="[20, 50, 100]"
          :total="total"
          layout="total, sizes, prev, pager, next"
          @size-change="loadAttempts"
          @current-change="loadAttempts"
        />
      </div>
    </el-card>

    <!-- 详情弹窗 -->
    <el-dialog v-model="detailVisible" title="做题详情" width="min(700px, 95vw)">
      <div v-loading="detailLoading">
        <div v-if="detail" class="detail-content">
          <div class="detail-summary">
            <el-descriptions :column="2" border size="small">
              <el-descriptions-item label="学生">{{ detail.attempt?.studentRealName || detail.attempt?.studentUsername || detail.attempt?.studentId }}</el-descriptions-item>
              <el-descriptions-item label="课文">{{ detail.attempt?.textTitle || detail.attempt?.textId }}</el-descriptions-item>
              <el-descriptions-item label="题数">{{ detail.attempt?.totalQuestions ?? detail.attempt?.questionCount }}</el-descriptions-item>
              <el-descriptions-item label="正确数">{{ detail.attempt?.correctCount }}</el-descriptions-item>
              <el-descriptions-item label="用时">{{ detail.attempt?.totalTimeSeconds }}秒</el-descriptions-item>
              <el-descriptions-item label="时间">{{ formatTime(detail.attempt?.submittedTime || detail.attempt?.createdAt) }}</el-descriptions-item>
            </el-descriptions>
          </div>

          <div v-if="detail.records?.length" class="detail-records">
            <div v-for="(record, idx) in detail.records" :key="idx" class="record-item" :class="{ 'is-wrong': !record.isCorrect }">
              <div class="record-header">
                <span class="record-number">{{ idx + 1 }}</span>
                <span class="record-type">{{ record.questionType || '选择题' }}</span>
                <el-tag :type="record.isCorrect === 1 || record.isCorrect === true ? 'success' : 'danger'" size="small">
                  {{ record.isCorrect === 1 || record.isCorrect === true ? '正确' : '错误' }}
                </el-tag>
                <span class="record-time" v-if="record.timeSpentSeconds">用时 {{ record.timeSpentSeconds }}s</span>
              </div>
              <div class="record-question">{{ record.questionSnapshot }}</div>
              <div class="record-options">
                <div v-for="opt in parseOptions(record.optionsSnapshot)" :key="opt.key" class="record-option" :class="{
                  'is-correct': opt.key === record.correctAnswer,
                  'is-selected-wrong': opt.key === record.userAnswer && !record.isCorrect
                }">
                  <span class="opt-key">{{ opt.key }}</span>
                  <span class="opt-content">{{ opt.content }}</span>
                  <el-icon v-if="opt.key === record.correctAnswer" class="correct-icon"><CircleCheck /></el-icon>
                  <el-icon v-if="opt.key === record.userAnswer && !record.isCorrect" class="wrong-icon"><CircleClose /></el-icon>
                </div>
              </div>
              <div class="record-answer">
                作答：{{ record.userAnswer || '未作答' }} 正确答案：{{ record.correctAnswer }}
              </div>
              <div class="record-analysis" v-if="record.analysisSnapshot">
                <strong>解析：</strong>{{ record.analysisSnapshot }}
              </div>
            </div>
          </div>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { CircleCheck, CircleClose } from '@element-plus/icons-vue'
import { getQuizAttempts, getQuizAttemptDetail } from '@/api/admin'

const loading = ref(false)
const attempts = ref([])
const total = ref(0)
const pagination = reactive({ current: 1, size: 20 })
const filterForm = reactive({ studentId: '', textId: '', dateRange: null })

const detailVisible = ref(false)
const detailLoading = ref(false)
const detail = ref(null)

const loadAttempts = async () => {
  loading.value = true
  try {
    const params = {
      page: pagination.current,
      pageSize: pagination.size,
    }
    if (filterForm.studentId) params.studentId = filterForm.studentId
    if (filterForm.textId) params.textId = filterForm.textId
    if (filterForm.dateRange?.length === 2) {
      params.startTime = filterForm.dateRange[0]
      params.endTime = filterForm.dateRange[1]
    }
    const resp = await getQuizAttempts(params)
    if (resp.code === 200) {
      attempts.value = resp.data?.list || resp.data?.records || []
      total.value = resp.data?.total || 0
    } else {
      ElMessage.error(resp.message || '查询失败')
    }
  } catch (e) {
    ElMessage.error('查询失败：' + (e.message || '网络异常'))
  } finally {
    loading.value = false
  }
}

const viewDetail = async (row) => {
  detailVisible.value = true
  detailLoading.value = true
  detail.value = null
  try {
    const resp = await getQuizAttemptDetail(row.studentId, row.textId)
    if (resp.code === 200) {
      detail.value = resp.data
    } else {
      ElMessage.error(resp.message || '查询详情失败')
    }
  } catch (e) {
    ElMessage.error('查询详情失败：' + (e.message || '网络异常'))
  } finally {
    detailLoading.value = false
  }
}

const getAccuracyType = (row) => {
  const total = row.totalQuestions ?? row.questionCount ?? 0
  if (!total) return 'info'
  const rate = row.correctCount / total
  if (rate >= 0.8) return 'success'
  if (rate >= 0.6) return 'warning'
  return 'danger'
}

const getAccuracyText = (row) => {
  const total = row.totalQuestions ?? row.questionCount ?? 0
  if (!total) return '-'
  return Math.round((row.correctCount / total) * 100) + '%'
}

const formatTime = (t) => {
  if (!t) return '-'
  return t.replace('T', ' ').substring(0, 19)
}

const parseOptions = (snapshot) => {
  if (!snapshot) return []
  try {
    const obj = typeof snapshot === 'string' ? JSON.parse(snapshot) : snapshot
    // 数组格式: [{key, content, isCorrect}, ...]
    if (Array.isArray(obj)) {
      return obj.map(item => ({ key: item.key || item.optionKey, content: item.content || item.optionContent }))
    }
    // 对象格式: {A: "...", B: "..."}
    return Object.entries(obj).map(([key, content]) => ({ key, content }))
  } catch {
    return []
  }
}

onMounted(loadAttempts)
</script>

<style scoped>
.quiz-records-management {
  padding: 16px;
}
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.filter-bar {
  margin-bottom: 16px;
}
.pagination {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}
.detail-summary {
  margin-bottom: 16px;
}
.detail-records {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.record-item {
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  padding: 12px;
}
.record-item.is-wrong {
  border-color: var(--el-color-danger-light-5);
  background: var(--el-color-danger-light-9);
}
.record-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}
.record-number {
  font-weight: 600;
  color: var(--el-color-primary);
}
.record-type {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
.record-time {
  margin-left: auto;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
.record-question {
  margin-bottom: 8px;
  line-height: 1.6;
}
.record-options {
  display: flex;
  flex-direction: column;
  gap: 4px;
  margin-bottom: 8px;
}
.record-option {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 4px 8px;
  border-radius: 4px;
}
.record-option.is-correct {
  background: var(--el-color-success-light-9);
  color: var(--el-color-success);
}
.record-option.is-selected-wrong {
  background: var(--el-color-danger-light-9);
  color: var(--el-color-danger);
}
.opt-key {
  font-weight: 600;
  min-width: 20px;
}
.correct-icon {
  color: var(--el-color-success);
  margin-left: auto;
}
.wrong-icon {
  color: var(--el-color-danger);
  margin-left: auto;
}
.record-answer {
  font-size: 13px;
  color: var(--el-text-color-secondary);
  margin-bottom: 4px;
}
.record-analysis {
  font-size: 13px;
  color: var(--el-text-color-secondary);
  margin-top: 4px;
  padding: 8px;
  background: var(--el-fill-color-lighter);
  border-radius: 4px;
}
</style>
