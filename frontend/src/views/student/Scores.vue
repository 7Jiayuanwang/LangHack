<template>
  <div class="student-scores">
    <!-- 筛选 -->
    <div class="filter-bar">
      <el-select v-model="examType" placeholder="考试类型" size="small" clearable style="width: 120px">
        <el-option value="CET4" label="CET-4" />
        <el-option value="CET6" label="CET-6" />
      </el-select>
    </div>

    <!-- 成绩列表 -->
    <div v-loading="loading" class="score-list">
      <div v-if="scores.length === 0 && !loading" class="empty-state">
        <el-empty description="暂无等级考试成绩" />
      </div>

      <div v-for="item in scores" :key="item.id" class="score-card">
        <div class="score-header">
          <div class="exam-type-group">
            <span class="exam-type">{{ item.examType }}</span>
            <span v-if="item.examName" class="exam-name">{{ item.examName }}</span>
          </div>
          <el-tag :type="item.isPassed ? 'success' : 'danger'" size="small">
            {{ item.isPassed ? '通过' : '未通过' }}
          </el-tag>
        </div>
        <div class="score-body">
          <div class="score-main">
            <span class="score-value">{{ item.score }}</span>
            <span class="score-unit">分</span>
          </div>
          <div class="score-details">
            <div v-if="item.listeningScore != null" class="detail-item">
              <span class="detail-label">听力</span>
              <span class="detail-value">{{ item.listeningScore }}</span>
            </div>
            <div v-if="item.readingScore != null" class="detail-item">
              <span class="detail-label">阅读</span>
              <span class="detail-value">{{ item.readingScore }}</span>
            </div>
            <div v-if="item.writingScore != null" class="detail-item">
              <span class="detail-label">写作</span>
              <span class="detail-value">{{ item.writingScore }}</span>
            </div>
            <div v-if="item.translationScore != null" class="detail-item">
              <span class="detail-label">翻译</span>
              <span class="detail-value">{{ item.translationScore }}</span>
            </div>
          </div>
        </div>
        <div class="score-footer">
          <span>{{ item.examDate }}</span>
          <span v-if="item.certificateNo">证书号: {{ item.certificateNo }}</span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { getMyLevelExamScores } from '@/api/student'

const loading = ref(false)
const allScores = ref([])
const examType = ref('')

const scores = computed(() => {
  if (!examType.value) return allScores.value
  return allScores.value.filter(s => s.examType === examType.value)
})

async function loadScores() {
  loading.value = true
  try {
    const res = await getMyLevelExamScores()
    allScores.value = res?.data || []
  } catch (e) {
    console.error('获取成绩失败:', e)
    allScores.value = []
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  loadScores()
})
</script>

<style scoped>
.student-scores {
  padding: 12px;
}

.filter-bar {
  margin-bottom: 12px;
}

.empty-state {
  padding: 40px 0;
}

.score-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.score-card {
  background: var(--bg-primary);
  border-radius: 12px;
  padding: 16px;
  border: 1px solid var(--border-light);
}

.score-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 12px;
}

.exam-type-group {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.exam-type {
  font-size: 16px;
  font-weight: 700;
  color: var(--text-primary);
}

.exam-name {
  font-size: 12px;
  color: var(--text-tertiary);
}

.score-body {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.score-main {
  display: flex;
  align-items: baseline;
  gap: 2px;
}

.score-value {
  font-size: 36px;
  font-weight: 800;
  color: var(--primary-color);
}

.score-unit {
  font-size: 14px;
  color: var(--text-tertiary);
}

.score-details {
  display: flex;
  gap: 16px;
}

.detail-item {
  text-align: center;
}

.detail-label {
  display: block;
  font-size: 11px;
  color: var(--text-tertiary);
}

.detail-value {
  display: block;
  font-size: 16px;
  font-weight: 600;
  color: var(--text-secondary);
}

.score-footer {
  display: flex;
  justify-content: space-between;
  margin-top: 12px;
  padding-top: 8px;
  border-top: 1px solid var(--border-light);
  font-size: 12px;
  color: var(--text-tertiary);
}
</style>
