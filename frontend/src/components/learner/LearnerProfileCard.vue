<template>
  <div class="learner-profile-card" v-loading="loading">
    <template v-if="error">
      <el-empty description="加载画像失败" />
    </template>
    <template v-else-if="!profile || profile.profileStatus === 'empty'">
      <el-empty description="暂无学习数据" />
    </template>
    <template v-else>
      <!-- 身份信息 -->
      <div class="section" v-if="profile.identity">
        <h4>基本信息</h4>
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="姓名">{{ profile.identity.realName || profile.identity.username }}</el-descriptions-item>
          <el-descriptions-item label="角色">{{ roleLabel(profile.identity.role) }}</el-descriptions-item>
          <el-descriptions-item label="班级" v-if="profile.identity.className">{{ profile.identity.className }}</el-descriptions-item>
          <el-descriptions-item label="学院" v-if="profile.identity.collegeName">{{ profile.identity.collegeName }}</el-descriptions-item>
        </el-descriptions>
      </div>

      <!-- 标签 -->
      <div class="section" v-if="profile.tags && profile.tags.length > 0">
        <h4>学习标签</h4>
        <LearnerRiskTagList :tags="profile.tags" />
      </div>

      <!-- 能力 -->
      <div class="section" v-if="profile.ability">
        <h4>词汇能力</h4>
        <el-row :gutter="12">
          <el-col :span="6">
            <el-statistic title="总词汇" :value="profile.ability.totalWords" />
          </el-col>
          <el-col :span="6">
            <el-statistic title="已掌握" :value="profile.ability.masteredWords" />
          </el-col>
          <el-col :span="6">
            <el-statistic title="学习中" :value="profile.ability.learningWords" />
          </el-col>
          <el-col :span="6">
            <el-statistic title="阅读篇数" :value="profile.ability.textsRead" />
          </el-col>
        </el-row>
      </div>

      <!-- 习惯 -->
      <div class="section" v-if="profile.habit">
        <h4>学习习惯</h4>
        <el-row :gutter="12">
          <el-col :span="6">
            <el-statistic title="7天活跃" :value="profile.habit.activeDays7d" suffix="天" />
          </el-col>
          <el-col :span="6">
            <el-statistic title="连续学习" :value="profile.habit.consecutiveDays" suffix="天" />
          </el-col>
          <el-col :span="6">
            <el-statistic title="14天时长" :value="profile.habit.studyMinutes14d" suffix="分钟" />
          </el-col>
          <el-col :span="6">
            <el-statistic title="逾期复习" :value="profile.habit.overdueReviewCount" />
          </el-col>
        </el-row>
      </div>

      <!-- 版本偏好 -->
      <div class="section" v-if="profile.preference && profile.preference.primaryContentVersion !== 'unknown'">
        <h4>阅读偏好</h4>
        <div class="version-info">
          <el-tag v-if="profile.preference.targetExamLevel" type="primary" size="small">
            备考目标: {{ profile.preference.targetExamLevel }}
          </el-tag>
          <span class="primary-version">主要阅读版本: {{ getVersionLabel(profile.preference.primaryContentVersion) }}</span>
        </div>
        <div class="version-bars" v-if="profile.preference.versionDistribution">
          <div
            v-for="(ratio, version) in profile.preference.versionDistribution"
            :key="version"
            class="version-bar-item"
          >
            <span class="version-label">{{ getVersionLabel(version) }}</span>
            <el-progress :percentage="Math.round(ratio * 100)" :stroke-width="14" />
          </div>
        </div>
      </div>

      <!-- 风险 -->
      <div class="section" v-if="profile.risk">
        <h4>风险评估</h4>
        <el-tag :type="getRiskType(profile.risk.riskLevel)" size="default">
          {{ getRiskLabel(profile.risk.riskLevel) }}
        </el-tag>
        <span v-if="profile.risk.churnRisk" class="churn-warning">流失风险</span>
      </div>
    </template>
  </div>
</template>

<script setup>
import LearnerRiskTagList from './LearnerRiskTagList.vue'

defineProps({
  profile: {
    type: Object,
    default: null
  },
  loading: {
    type: Boolean,
    default: false
  },
  error: {
    type: Boolean,
    default: false
  }
})

const roleLabel = (role) => {
  const labels = { USER: '普通用户', STUDENT: '学生', TEACHER: '教师', ADMIN: '管理员', OPS: '运维' }
  return labels[role] || role
}

const getVersionLabel = (version) => {
  const labels = {
    original: '原文',
    cet4: 'CET-4',
    cet6: 'CET-6',
    academic: '考研',
    chinese_parallel: '中英对照'
  }
  return labels[version] || version
}

const getRiskType = (level) => {
  if (level === 'high') return 'danger'
  if (level === 'medium') return 'warning'
  return 'success'
}

const getRiskLabel = (level) => {
  const labels = { low: '低风险', medium: '中风险', high: '高风险' }
  return labels[level] || level
}
</script>

<style scoped>
.learner-profile-card {
  padding: 16px;
}
.section {
  margin-bottom: 20px;
}
.section h4 {
  margin: 0 0 12px 0;
  font-size: 15px;
  color: #303133;
  border-bottom: 1px solid #ebeef5;
  padding-bottom: 8px;
}
.version-info {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
}
.primary-version {
  font-size: 13px;
  color: #606266;
}
.version-bars {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.version-bar-item {
  display: flex;
  align-items: center;
  gap: 8px;
}
.version-label {
  width: 70px;
  text-align: right;
  font-size: 13px;
  color: #606266;
  flex-shrink: 0;
}
.version-bar-item .el-progress {
  flex: 1;
}
.churn-warning {
  margin-left: 12px;
  color: #f56c6c;
  font-weight: bold;
  font-size: 13px;
}
</style>
