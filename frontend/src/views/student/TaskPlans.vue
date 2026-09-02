<template>
  <div class="task-plans">
    <div class="page-back" @click="$router.back()">
      <el-icon><ArrowLeft /></el-icon>
      <span>返回</span>
    </div>
    <div v-if="loading" class="loading-state">
      <el-skeleton :rows="4" animated />
    </div>

    <el-empty v-else-if="plans.length === 0" description="暂无学期目标" />

    <div v-else class="plan-list">
      <div v-for="plan in plans" :key="plan.id" class="plan-card">
        <div class="plan-header">
          <h3>{{ plan.title }}</h3>
          <el-tag size="small" :type="plan.cycleType === 'WEEKLY' ? 'primary' : 'warning'">
            {{ plan.cycleType === 'WEEKLY' ? '按周' : '按月' }}
          </el-tag>
        </div>

        <div class="plan-meta">
          <span>{{ plan.courseName }}</span>
          <span class="separator">|</span>
          <span>{{ plan.semesterStart }} ~ {{ plan.semesterEnd }}</span>
        </div>

        <div class="progress-section">
          <div class="progress-item">
            <div class="progress-label">
              <span>文章阅读</span>
              <span class="progress-text">{{ plan.currentArticles || 0 }} / {{ plan.articleTarget }}</span>
            </div>
            <el-progress
              :percentage="calcPercent(plan.currentArticles, plan.articleTarget)"
              :stroke-width="8"
              color="#409EFF"
            />
          </div>

          <div class="progress-item">
            <div class="progress-label">
              <span>单词背诵 ({{ formatCategory(plan.wordCategory) }})</span>
              <span class="progress-text">{{ plan.currentWords || 0 }} / {{ plan.wordTarget }}</span>
            </div>
            <el-progress
              :percentage="calcPercent(plan.currentWords, plan.wordTarget)"
              :stroke-width="8"
              color="#67C23A"
            />
          </div>

          <div class="progress-item">
            <div class="progress-label">
              <span>真题练习</span>
              <span class="progress-text">{{ plan.currentPapers || 0 }} / {{ plan.paperTarget }}</span>
            </div>
            <el-progress
              :percentage="calcPercent(plan.currentPapers, plan.paperTarget)"
              :stroke-width="8"
              color="#E6A23C"
            />
          </div>
        </div>

        <div class="score-section">
          <div class="score-label">当前得分</div>
          <div class="score-value">{{ plan.currentScore ?? '-' }} <span class="score-total">/ {{ plan.totalScore }}</span></div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ArrowLeft } from '@element-plus/icons-vue'
import { studentTaskPlanApi } from '@/api/taskPlan'

const loading = ref(true)
const plans = ref([])

const calcPercent = (current, target) => {
  if (!target || target <= 0) return 0
  return Math.min(Math.round(((current || 0) / target) * 100), 100)
}

const formatCategory = (cat) => {
  const map = { CET4: '四级', CET6: '六级', GAOKAO: '高考' }
  return map[cat] || cat || '四级'
}

const loadPlans = async () => {
  loading.value = true
  try {
    const res = await studentTaskPlanApi.list()
    if (res.code === 200) {
      plans.value = res.data || []
    }
  } catch (e) {
    console.error('加载学期目标失败', e)
  } finally {
    loading.value = false
  }
}

onMounted(() => loadPlans())
</script>

<style scoped lang="scss">
.task-plans {
  padding: 16px;
}

.page-back {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 14px;
  color: var(--el-color-primary);
  cursor: pointer;
  margin-bottom: 12px;
  &:active { opacity: 0.7; }
}

.loading-state {
  padding: 20px;
}

.plan-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.plan-card {
  background: var(--el-bg-color);
  border-radius: 12px;
  padding: 16px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
}

.plan-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;

  h3 {
    margin: 0;
    font-size: 16px;
    font-weight: 600;
  }
}

.plan-meta {
  font-size: 13px;
  color: var(--el-text-color-secondary);
  margin-bottom: 16px;

  .separator {
    margin: 0 8px;
  }
}

.progress-section {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.progress-item {
  .progress-label {
    display: flex;
    justify-content: space-between;
    font-size: 13px;
    margin-bottom: 4px;
  }

  .progress-text {
    color: var(--el-text-color-secondary);
  }
}

.score-section {
  margin-top: 16px;
  padding-top: 12px;
  border-top: 1px solid var(--el-border-color-lighter);
  display: flex;
  justify-content: space-between;
  align-items: center;

  .score-label {
    font-size: 14px;
    color: var(--el-text-color-regular);
  }

  .score-value {
    font-size: 24px;
    font-weight: 700;
    color: var(--el-color-primary);

    .score-total {
      font-size: 14px;
      font-weight: 400;
      color: var(--el-text-color-secondary);
    }
  }
}
</style>
