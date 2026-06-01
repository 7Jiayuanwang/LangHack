<template>
  <div class="my-profile" v-loading="loading">
    <template v-if="!profile && !loading">
      <el-empty description="暂无学习数据" />
    </template>
    <template v-else-if="profile">
      <div class="section">
        <h3>学习概况</h3>
        <el-row :gutter="12">
          <el-col :span="8">
            <el-statistic title="7天活跃" :value="profile.activeDays7d" suffix="天" />
          </el-col>
          <el-col :span="8">
            <el-statistic title="连续学习" :value="profile.consecutiveDays" suffix="天" />
          </el-col>
          <el-col :span="8">
            <el-statistic title="14天时长" :value="profile.studyMinutes14d" suffix="分钟" />
          </el-col>
        </el-row>
      </div>

      <div class="section" v-if="profile.ability">
        <h3>词汇能力</h3>
        <el-row :gutter="12">
          <el-col :span="8">
            <el-statistic title="总词汇" :value="profile.ability.totalWords" />
          </el-col>
          <el-col :span="8">
            <el-statistic title="已掌握" :value="profile.ability.masteredWords" />
          </el-col>
          <el-col :span="8">
            <el-statistic title="阅读篇数" :value="profile.ability.textsRead" />
          </el-col>
        </el-row>
      </div>

      <div class="section" v-if="profile.targetExamLevel || profile.primaryContentVersion">
        <h3>阅读偏好</h3>
        <div class="pref-info">
          <el-tag v-if="profile.targetExamLevel" type="primary" size="small">
            备考目标: {{ profile.targetExamLevel }}
          </el-tag>
          <span v-if="profile.primaryContentVersion && profile.primaryContentVersion !== 'unknown'" class="version-text">
            主要版本: {{ versionLabel(profile.primaryContentVersion) }}
          </span>
        </div>
      </div>

      <div class="section" v-if="profile.positiveTags && profile.positiveTags.length > 0">
        <h3>学习徽章</h3>
        <div class="badge-list">
          <el-tag v-for="tag in profile.positiveTags" :key="tag.code" type="success" size="default" class="badge-tag">
            {{ tag.displayName }}
          </el-tag>
        </div>
      </div>

      <div class="section" v-if="profile.naturalLanguageSummary">
        <h3>AI 学习总结</h3>
        <div class="summary-text">{{ profile.naturalLanguageSummary }}</div>
      </div>
    </template>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { getMyProfile } from '@/api/learnerProfile'

const loading = ref(false)
const profile = ref(null)

const versionLabel = (v) => {
  const labels = { original: '原文', cet4: 'CET-4', cet6: 'CET-6', academic: '考研', chinese_parallel: '中英对照' }
  return labels[v] || v
}

async function loadProfile() {
  loading.value = true
  try {
    const res = await getMyProfile()
    profile.value = res?.data || null
  } catch (e) {
    console.error('加载画像失败:', e)
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  loadProfile()
})
</script>

<style scoped>
.my-profile {
  padding: 16px;
}
.section {
  margin-bottom: 20px;
}
.section h3 {
  margin: 0 0 12px 0;
  font-size: 15px;
  color: var(--text-primary);
  border-bottom: 1px solid var(--border-light);
  padding-bottom: 8px;
}
.pref-info {
  display: flex;
  align-items: center;
  gap: 12px;
}
.version-text {
  font-size: 13px;
  color: var(--text-secondary);
}
.badge-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.badge-tag {
  font-size: 14px;
}
.summary-text {
  font-size: 14px;
  line-height: 1.6;
  color: var(--text-secondary);
  background: var(--bg-secondary, #f5f7fa);
  border-radius: 8px;
  padding: 12px;
  white-space: pre-wrap;
}
</style>
