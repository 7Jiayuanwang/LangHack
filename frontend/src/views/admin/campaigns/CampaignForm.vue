<template>
  <div class="campaign-form-container">
    <div class="header">
      <h2>{{ isEdit ? '编辑活动' : '创建活动' }}</h2>
      <el-button @click="handleBack">返回</el-button>
    </div>

    <el-card v-loading="loading">
      <el-form :model="formData" :rules="rules" ref="formRef" label-width="140px">
        <el-divider content-position="left">基本信息</el-divider>

        <el-form-item label="活动标题" prop="title">
          <el-input v-model="formData.title" placeholder="请输入活动标题" maxlength="200" show-word-limit />
        </el-form-item>

        <el-form-item label="活动描述" prop="description">
          <el-input
            v-model="formData.description"
            type="textarea"
            :rows="4"
            placeholder="请输入活动描述"
            maxlength="2000"
            show-word-limit
          />
        </el-form-item>

        <el-form-item label="活动时间" prop="timeRange">
          <el-date-picker
            v-model="formData.timeRange"
            type="datetimerange"
            range-separator="至"
            start-placeholder="开始时间"
            end-placeholder="结束时间"
            format="YYYY-MM-DD HH:mm"
            value-format="YYYY-MM-DD HH:mm:ss"
          />
        </el-form-item>

        <el-form-item label="报名时间" prop="enrollmentTimeRange">
          <el-date-picker
            v-model="formData.enrollmentTimeRange"
            type="datetimerange"
            range-separator="至"
            start-placeholder="报名开始时间"
            end-placeholder="报名结束时间"
            format="YYYY-MM-DD HH:mm"
            value-format="YYYY-MM-DD HH:mm:ss"
          />
          <div style="color: #909399; font-size: 12px; margin-top: 4px;">
            报名时间用于控制学生何时可以报名参加活动，如不设置则默认与活动时间相同
          </div>
        </el-form-item>

        <el-divider content-position="left">完成规则</el-divider>

        <el-form-item label="完成规则" prop="completionRule">
          <el-radio-group v-model="formData.completionRule">
            <el-radio label="ANY">完成任意任务</el-radio>
            <el-radio label="ALL">完成所有任务</el-radio>
            <el-radio label="THRESHOLD">完成指定数量任务</el-radio>
          </el-radio-group>
        </el-form-item>

        <el-form-item
          label="完成任务数"
          prop="completionThreshold"
          v-if="formData.completionRule === 'THRESHOLD'"
        >
          <el-input-number v-model="formData.completionThreshold" :min="1" :max="100" />
        </el-form-item>

        <el-divider content-position="left">报名设置</el-divider>

        <el-form-item label="报名模式" prop="enrollmentMode">
          <el-radio-group v-model="formData.enrollmentMode">
            <el-radio label="AUTO">自动报名（所有人自动参与）</el-radio>
            <el-radio label="MANUAL">自主报名（学生主动报名）</el-radio>
            <el-radio label="INVITE_ONLY">仅邀请（需管理员审核）</el-radio>
          </el-radio-group>
        </el-form-item>

        <el-form-item label="报名范围" prop="enrollmentScope">
          <el-radio-group v-model="formData.enrollmentScope" @change="handleScopeChange">
            <el-radio label="ALL">全部学生</el-radio>
            <el-radio label="COLLEGE">指定学院</el-radio>
            <el-radio label="GRADE">指定年级</el-radio>
            <el-radio label="CLASS">指定班级</el-radio>
          </el-radio-group>
        </el-form-item>

        <el-form-item
          label="选择学院"
          v-if="formData.enrollmentScope === 'COLLEGE'"
          prop="selectedColleges"
        >
          <el-select
            v-model="formData.selectedColleges"
            multiple
            filterable
            placeholder="请选择学院"
            style="width: 100%"
            @change="handleCollegeChange"
          >
            <el-option
              v-for="item in collegeOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-form-item>

        <el-form-item
          label="选择年级"
          v-if="formData.enrollmentScope === 'COLLEGE'"
        >
          <el-select
            v-model="formData.selectedGrades"
            multiple
            filterable
            placeholder="不选则包含选中学院的所有年级"
            style="width: 100%"
          >
            <el-option
              v-for="item in gradeOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
          <div style="color: #909399; font-size: 12px; margin-top: 4px;">
            不选择年级时，默认包含选中学院的所有年级学生
          </div>
        </el-form-item>

        <el-form-item
          label="目标选择"
          v-if="formData.enrollmentScope === 'GRADE' || formData.enrollmentScope === 'CLASS'"
          prop="enrollmentTargetIds"
        >
          <el-select
            v-model="formData.enrollmentTargetIds"
            multiple
            filterable
            :placeholder="getTargetPlaceholder()"
            style="width: 100%"
          >
            <el-option
              v-for="item in targetOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="最大参与人数" prop="maxParticipants">
          <el-input-number v-model="formData.maxParticipants" :min="0" placeholder="0表示不限制" />
          <span class="form-tip">（0 表示不限制）</span>
        </el-form-item>

        <el-divider content-position="left">隐私设置</el-divider>

        <el-form-item label="显示具体分数">
          <el-switch v-model="formData.showScores" />
          <span class="form-tip">关闭后学生端仅显示排名，不显示具体分数</span>
        </el-form-item>

        <el-form-item label="显示排名">
          <el-switch v-model="formData.showRanking" />
          <span class="form-tip">关闭后学生端无法查看排名信息</span>
        </el-form-item>

        <el-divider content-position="left">排名与成绩设置</el-divider>

        <el-form-item label="连续学习加成">
          <el-switch v-model="formData.streakEnabled" />
          <span class="form-tip">启用后学生连续学习可获得积分加成</span>
        </el-form-item>

        <el-form-item label="重置周期（天）" v-if="formData.streakEnabled">
          <el-input-number v-model="formData.streakResetDays" :min="3" :max="30" :step="1" />
          <span class="form-tip">连续天数每隔多少天归零重来（默认7天）</span>
        </el-form-item>

        <el-form-item label="周期起始日期" v-if="formData.streakEnabled">
          <el-date-picker v-model="formData.streakStartDate" type="date" placeholder="选择起始日期" value-format="YYYY-MM-DD" />
          <span class="form-tip">加成周期从哪天开始计算（不填则使用活动开始时间）</span>
        </el-form-item>

        <el-form-item label="加成阶梯配置" v-if="formData.streakEnabled">
          <div class="streak-tiers">
            <div v-for="(tier, index) in formData.streakTiers" :key="index" class="streak-tier-row">
              <span class="tier-label">连续第</span>
              <el-input-number v-model="tier.day" :min="2" :max="30" :step="1" size="small" style="width: 80px" />
              <span class="tier-label">天，加成</span>
              <el-input-number v-model="tier.percent" :min="1" :max="200" :step="5" size="small" style="width: 100px" />
              <span class="tier-label">%</span>
              <el-button type="danger" size="small" text @click="removeStreakTier(index)">删除</el-button>
            </div>
            <el-button type="primary" size="small" @click="addStreakTier">+ 添加阶梯</el-button>
            <div class="form-tip" style="margin-top: 8px">
              例如：连续第2天加成5%，连续第5天加成20%。未设置的天数按上一阶梯值生效。
            </div>
          </div>
        </el-form-item>

        <el-form-item label="班级排名模式">
          <el-radio-group v-model="formData.classRankingMode">
            <el-radio label="WEIGHTED">归一化加权（人均积分 + 参与率）</el-radio>
            <el-radio label="AVG_SCORE">纯人均积分排序</el-radio>
            <el-radio label="TOTAL_SCORE">总积分排序</el-radio>
          </el-radio-group>
        </el-form-item>

        <el-form-item label="人均积分权重" v-if="formData.classRankingMode === 'WEIGHTED'">
          <el-slider v-model="formData.classAvgWeightPercent" :min="10" :max="90" :step="10"
            :format-tooltip="(val) => `人均${val}% : 参与率${100-val}%`" />
          <span class="form-tip">人均积分 {{ formData.classAvgWeightPercent }}% / 参与率 {{ 100 - formData.classAvgWeightPercent }}%</span>
        </el-form-item>

        <el-form-item label="参与率计算方式" v-if="formData.classRankingMode === 'WEIGHTED'">
          <el-radio-group v-model="formData.participationCalcMode">
            <el-radio label="HEAD_COUNT">活跃人数占比（本周至少学1天即算）</el-radio>
            <el-radio label="DENSITY">活跃天数密度（按天计算，区分度更高）</el-radio>
          </el-radio-group>
        </el-form-item>

        <el-divider content-position="left">任务配置</el-divider>

        <el-form-item>
          <el-button type="primary" @click="handleAddTask">添加任务</el-button>
        </el-form-item>

        <el-table :data="formData.tasks" border style="margin-bottom: 20px">
          <el-table-column label="任务名称" min-width="150">
            <template #default="{ row }">
              <el-input v-model="row.taskName" placeholder="任务名称" />
            </template>
          </el-table-column>
          <el-table-column label="任务类型" width="180">
            <template #default="{ row }">
              <el-select v-model="row.taskType" @change="handleTaskTypeChange(row)">
                <el-option label="阅读时长（分钟）" value="READING_TIME" />
                <el-option label="单词学习（个）" value="WORD_LEARNING" />
                <el-option label="文章上传（篇）" value="TEXT_UPLOAD" />
                <el-option label="问题反馈（次）" value="FEEDBACK" />
                <el-option label="AI问答（次）" value="AI_QA" />
                <el-option label="手动录入" value="MANUAL" />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="目标值" width="120">
            <template #default="{ row }">
              <el-input-number v-model="row.targetValue" :min="1" />
            </template>
          </el-table-column>
          <el-table-column label="完成得分" width="100">
            <template #default="{ row }">
              <el-input-number v-model="row.points" :min="1" />
            </template>
          </el-table-column>
          <el-table-column label="可重复" width="90">
            <template #default="{ row }">
              <el-switch v-model="row.isRepeatable" @change="handleRepeatableChange(row)" />
            </template>
          </el-table-column>
          <el-table-column label="总上限" width="110" v-if="formData.tasks.some(t => t.isRepeatable)">
            <template #default="{ row }">
              <el-input-number
                v-model="row.maxCompletions"
                :min="0"
                :disabled="!row.isRepeatable"
                placeholder="无限"
                :controls="false"
                style="width: 90px"
              />
            </template>
          </el-table-column>
          <el-table-column label="日上限" width="110" v-if="formData.tasks.some(t => t.isRepeatable)">
            <template #default="{ row }">
              <el-input-number
                v-model="row.dailyLimit"
                :min="0"
                :disabled="!row.isRepeatable"
                placeholder="无限"
                :controls="false"
                style="width: 90px"
              />
            </template>
          </el-table-column>
          <el-table-column label="必做" width="80">
            <template #default="{ row }">
              <el-switch
                v-model="row.isRequired"
                :disabled="formData.completionRule === 'ALL'"
              />
            </template>
          </el-table-column>
          <el-table-column label="自动采集" width="100">
            <template #default="{ row }">
              <el-switch v-model="row.autoCollect" :disabled="row.taskType === 'MANUAL'" />
            </template>
          </el-table-column>
          <el-table-column label="操作" width="80" fixed="right">
            <template #default="{ $index }">
              <el-button link type="danger" @click="handleRemoveTask($index)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>

        <el-form-item>
          <el-button type="primary" @click="handleSubmit" :loading="submitting">保存</el-button>
          <el-button @click="handleBack">取消</el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getCampaignById, createCampaign, updateCampaign, getTasks, createTask, updateTask, deleteTask, getColleges, getGrades, getClasses } from '@/api/campaign'

const router = useRouter()
const route = useRoute()
const formRef = ref(null)
const loading = ref(false)
const submitting = ref(false)

const isEdit = computed(() => !!route.params.id)

const formData = reactive({
  title: '',
  description: '',
  timeRange: [],
  enrollmentTimeRange: [],
  completionRule: 'THRESHOLD',
  completionThreshold: 1,
  enrollmentMode: 'MANUAL',
  enrollmentScope: 'ALL',
  enrollmentTargetIds: [],
  selectedColleges: [],
  selectedGrades: [],
  maxParticipants: 0,
  showScores: true,
  showRanking: true,
  streakEnabled: true,
  streakMaxMultiplier: 1.30,
  streakResetDays: 7,
  streakStartDate: null,
  streakTiers: [
    { day: 2, percent: 5 },
    { day: 3, percent: 10 },
    { day: 4, percent: 15 },
    { day: 5, percent: 20 },
    { day: 6, percent: 25 },
    { day: 7, percent: 30 }
  ],
  streakConfig: null,
  classRankingMode: 'WEIGHTED',
  classAvgWeightPercent: 60,
  participationCalcMode: 'DENSITY',
  tasks: []
})

const targetOptions = ref([])
const collegeOptions = ref([])
const gradeOptions = ref([])

const rules = {
  title: [{ required: true, message: '请输入活动标题', trigger: 'blur' }],
  timeRange: [{ required: true, message: '请选择活动时间', trigger: 'change' }],
  completionRule: [{ required: true, message: '请选择完成规则', trigger: 'change' }],
  enrollmentMode: [{ required: true, message: '请选择报名模式', trigger: 'change' }],
  enrollmentScope: [{ required: true, message: '请选择报名范围', trigger: 'change' }]
}

const addStreakTier = () => {
  const lastDay = formData.streakTiers.length > 0
    ? formData.streakTiers[formData.streakTiers.length - 1].day + 1
    : 2
  formData.streakTiers.push({ day: lastDay, percent: (lastDay - 1) * 5 })
}

const removeStreakTier = (index) => {
  formData.streakTiers.splice(index, 1)
}

const handleAddTask = () => {
  formData.tasks.push({
    taskName: '',
    taskType: 'READING_TIME',
    taskUnit: 'MINUTES',
    targetValue: 60,
    points: 1,
    isRequired: formData.completionRule === 'ALL',
    autoCollect: true,
    isRepeatable: false,
    maxCompletions: null,
    dailyLimit: null,
    sortOrder: formData.tasks.length
  })
}

// 完成规则切换为 ALL 时，自动将所有任务设为必做；切换为 ANY 时，自动取消必做
watch(() => formData.completionRule, (newRule) => {
  if (newRule === 'ALL') {
    formData.tasks.forEach(t => { t.isRequired = true })
  } else if (newRule === 'ANY') {
    formData.tasks.forEach(t => { t.isRequired = false })
  }
  // THRESHOLD 保持用户自定义
})

const handleRemoveTask = (index) => {
  formData.tasks.splice(index, 1)
}

const handleRepeatableChange = (row) => {
  // 切换为不可重复时，清空上限设置
  if (!row.isRepeatable) {
    row.maxCompletions = null
    row.dailyLimit = null
  }
}

const handleTaskTypeChange = (row) => {
  const typeConfig = {
    READING_TIME: { unit: 'MINUTES', targetValue: 60, autoCollect: true },
    WORD_LEARNING: { unit: 'COUNT', targetValue: 50, autoCollect: true },
    TEXT_UPLOAD: { unit: 'COUNT', targetValue: 1, autoCollect: true },
    FEEDBACK: { unit: 'TIMES', targetValue: 3, autoCollect: true },
    AI_QA: { unit: 'TIMES', targetValue: 10, autoCollect: true },
    MANUAL: { unit: 'COUNT', targetValue: 1, autoCollect: false }
  }
  const config = typeConfig[row.taskType] || {}
  row.taskUnit = config.unit
  row.targetValue = config.targetValue
  row.autoCollect = config.autoCollect
}

const handleScopeChange = () => {
  formData.enrollmentTargetIds = []
  formData.selectedColleges = []
  formData.selectedGrades = []
  loadTargetOptions()
}

const handleCollegeChange = async () => {
  // 学院变化时清空年级选择并重新加载年级选项
  formData.selectedGrades = []
  if (formData.selectedColleges.length > 0) {
    await loadGradeOptions(formData.selectedColleges)
  } else {
    gradeOptions.value = []
  }
}

const loadTargetOptions = async () => {
  try {
    targetOptions.value = []
    collegeOptions.value = []
    gradeOptions.value = []

    if (formData.enrollmentScope === 'COLLEGE') {
      const res = await getColleges()
      if (res.code === 200) {
        collegeOptions.value = res.data
      }
    } else if (formData.enrollmentScope === 'GRADE') {
      const res = await getGrades()
      if (res.code === 200) {
        targetOptions.value = res.data
      }
    } else if (formData.enrollmentScope === 'CLASS') {
      const res = await getClasses()
      if (res.code === 200) {
        targetOptions.value = res.data
      }
    }
  } catch (error) {
    console.error('加载选项失败:', error)
    ElMessage.error('加载选项失败')
  }
}

const loadGradeOptions = async (collegeNames) => {
  try {
    const res = await getGrades(collegeNames.join(','))
    if (res.code === 200) {
      gradeOptions.value = res.data
    }
  } catch (error) {
    console.error('加载年级选项失败:', error)
    ElMessage.error('加载年级选项失败')
  }
}

const getTargetPlaceholder = () => {
  const placeholders = {
    GRADE: '请选择年级',
    CLASS: '请选择班级'
  }
  return placeholders[formData.enrollmentScope] || '请选择'
}

const loadData = async () => {
  if (!isEdit.value) return

  loading.value = true
  try {
    const [campaignRes, tasksRes] = await Promise.all([
      getCampaignById(route.params.id),
      getTasks(route.params.id)
    ])

    if (campaignRes.code === 200) {
      const data = campaignRes.data
      formData.title = data.title
      formData.description = data.description
      formData.timeRange = [data.startTime, data.endTime]
      formData.enrollmentTimeRange = data.enrollmentStartTime && data.enrollmentEndTime
        ? [data.enrollmentStartTime, data.enrollmentEndTime]
        : []
      formData.completionRule = data.completionRule
      formData.completionThreshold = data.completionThreshold
      formData.enrollmentMode = data.enrollmentMode
      formData.enrollmentScope = data.enrollmentScope

      // 兼容新旧格式回显
      if (data.enrollmentTargetIdList && data.enrollmentTargetIdList.length > 0) {
        const firstItem = data.enrollmentTargetIdList[0]
        if (data.enrollmentScope === 'COLLEGE' && typeof firstItem === 'object' && firstItem.collegeIds) {
          // 新格式：{ collegeIds: [...], gradeIds: [...] }
          formData.selectedColleges = firstItem.collegeIds || []
          formData.selectedGrades = firstItem.gradeIds || []
          // 加载选项以便回显
          await loadTargetOptions()
          if (formData.selectedColleges.length > 0) {
            await loadGradeOptions(formData.selectedColleges)
          }
        } else {
          // 旧格式：flat array
          formData.enrollmentTargetIds = data.enrollmentTargetIdList
          await loadTargetOptions()
        }
      }

      formData.maxParticipants = data.maxParticipants || 0
      formData.showScores = data.showScores
      formData.showRanking = data.showRanking
      formData.streakEnabled = data.streakEnabled !== false
      formData.streakMaxMultiplier = data.streakMaxMultiplier || 1.30
      formData.streakResetDays = data.streakResetDays || 7
      formData.streakStartDate = data.streakStartDate || null
      formData.streakConfig = data.streakConfig || null
      // 解析阶梯配置
      if (data.streakConfig) {
        try {
          const tiers = JSON.parse(data.streakConfig)
          formData.streakTiers = tiers.map(t => ({ day: t.day, percent: Math.round((parseFloat(t.multiplier) - 1) * 100) }))
        } catch (e) {
          // 保持默认
        }
      }
      formData.classRankingMode = data.classRankingMode || 'WEIGHTED'
      formData.classAvgWeightPercent = data.classAvgWeight ? Math.round(data.classAvgWeight * 100) : 60
      formData.participationCalcMode = data.participationCalcMode || 'DENSITY'
    }

    if (tasksRes.code === 200) {
      formData.tasks = tasksRes.data || []
    }
  } catch (error) {
    ElMessage.error('加载失败: ' + error.message)
  } finally {
    loading.value = false
  }
}

const handleSubmit = async () => {
  try {
    await formRef.value.validate()

    if (formData.tasks.length === 0) {
      ElMessage.warning('请至少添加一个任务')
      return
    }

    // 前端时间验证
    const now = new Date()
    const startTime = new Date(formData.timeRange[0])
    const endTime = new Date(formData.timeRange[1])

    // 验证活动时间范围
    if (endTime <= startTime) {
      ElMessage.error('活动结束时间必须晚于开始时间')
      return
    }

    // 创建模式：验证活动开始时间不能早于当前时间
    if (!isEdit.value && startTime < now) {
      ElMessage.error('活动开始时间不能早于当前时间')
      return
    }

    // 验证报名时间
    if (formData.enrollmentTimeRange.length > 0) {
      const enrollmentStartTime = new Date(formData.enrollmentTimeRange[0])
      const enrollmentEndTime = new Date(formData.enrollmentTimeRange[1])

      if (enrollmentEndTime <= enrollmentStartTime) {
        ElMessage.error('报名结束时间必须晚于报名开始时间')
        return
      }

      if (enrollmentEndTime > endTime) {
        ElMessage.error('报名结束时间不能晚于活动结束时间')
        return
      }
    }

    submitting.value = true

    // 构造 enrollmentTargetIdList（支持新格式）
    let enrollmentTargetIdList = []
    if (formData.enrollmentScope === 'COLLEGE') {
      // 新格式：{ collegeIds: [...], gradeIds: [...] }
      if (formData.selectedColleges.length > 0) {
        enrollmentTargetIdList = [{
          collegeIds: formData.selectedColleges,
          gradeIds: formData.selectedGrades
        }]
      }
    } else if (formData.enrollmentScope === 'GRADE' || formData.enrollmentScope === 'CLASS') {
      // 旧格式：flat array
      enrollmentTargetIdList = formData.enrollmentTargetIds
    }

    // 1. 先保存活动基本信息
    const campaignData = {
      title: formData.title,
      description: formData.description,
      startTime: formData.timeRange[0],
      endTime: formData.timeRange[1],
      enrollmentStartTime: formData.enrollmentTimeRange.length > 0 ? formData.enrollmentTimeRange[0] : formData.timeRange[0],
      enrollmentEndTime: formData.enrollmentTimeRange.length > 0 ? formData.enrollmentTimeRange[1] : formData.timeRange[1],
      completionRule: formData.completionRule,
      completionThreshold: formData.completionThreshold,
      enrollmentMode: formData.enrollmentMode,
      enrollmentScope: formData.enrollmentScope,
      enrollmentTargetIdList: enrollmentTargetIdList,
      maxParticipants: formData.maxParticipants,
      showScores: formData.showScores,
      showRanking: formData.showRanking,
      streakEnabled: formData.streakEnabled,
      streakMaxMultiplier: formData.streakTiers.length > 0
        ? Math.max(...formData.streakTiers.map(t => 1 + t.percent / 100))
        : formData.streakMaxMultiplier,
      streakResetDays: formData.streakResetDays,
      streakStartDate: formData.streakStartDate || null,
      streakConfig: JSON.stringify(
        formData.streakTiers
          .slice()
          .sort((a, b) => a.day - b.day)
          .map(t => ({ day: t.day, multiplier: (1 + t.percent / 100).toFixed(2) }))
      ),
      classRankingMode: formData.classRankingMode,
      classAvgWeight: formData.classAvgWeightPercent / 100,
      classParticipationWeight: (100 - formData.classAvgWeightPercent) / 100,
      participationCalcMode: formData.participationCalcMode
    }

    const campaignRes = isEdit.value
      ? await updateCampaign(route.params.id, campaignData)
      : await createCampaign(campaignData)

    if (campaignRes.code !== 200) {
      ElMessage.error(campaignRes.message || '保存活动失败')
      return
    }

    const campaignId = isEdit.value ? route.params.id : campaignRes.data.id

    // 2. 保存任务列表
    // 编辑模式：获取现有任务，对比后决定创建/更新/删除
    // 创建模式：直接创建所有任务
    if (isEdit.value) {
      // 获取现有任务
      const existingTasksRes = await getTasks(campaignId)
      const existingTasks = existingTasksRes.data || []
      const existingTaskIds = new Set(existingTasks.map(t => t.id))
      const currentTaskIds = new Set(formData.tasks.filter(t => t.id).map(t => t.id))

      // 删除不再存在的任务
      for (const task of existingTasks) {
        if (!currentTaskIds.has(task.id)) {
          await deleteTask(campaignId, task.id)
        }
      }

      // 更新或创建任务
      for (const task of formData.tasks) {
        if (task.id && existingTaskIds.has(task.id)) {
          // 更新现有任务
          await updateTask(campaignId, task.id, task)
        } else {
          // 创建新任务
          await createTask(campaignId, task)
        }
      }
    } else {
      // 创建模式：创建所有任务
      for (const task of formData.tasks) {
        await createTask(campaignId, task)
      }
    }

    ElMessage.success(isEdit.value ? '更新成功' : '创建成功')
    router.push('/admin/campaigns')
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('操作失败: ' + error.message)
    }
  } finally {
    submitting.value = false
  }
}

const handleBack = () => {
  router.push('/admin/campaigns')
}

onMounted(() => {
  loadData()
})
</script>

<style scoped>
.campaign-form-container {
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

.form-tip {
  margin-left: 10px;
  color: #909399;
  font-size: 12px;
}

.streak-tiers {
  width: 100%;
}

.streak-tier-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}

.tier-label {
  font-size: 13px;
  color: #606266;
  white-space: nowrap;
}

:deep(.el-divider__text) {
  font-weight: bold;
  color: #409eff;
}
</style>
