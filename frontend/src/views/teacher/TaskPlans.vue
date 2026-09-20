<template>
  <div class="teacher-task-plans">
    <XyPageHeader class="plans-intro" title="班级学习计划" eyebrow="CLASS GOALS" description="在统一目标的基础上，为不同班级调整合适的节奏。" variant="compact" />
    <div v-if="loading" class="loading-state">
      <el-skeleton :rows="4" animated />
    </div>

    <el-empty v-else-if="plans.length === 0" description="暂无默认任务计划" />

    <div v-else class="plan-list">
      <div v-for="plan in plans" :key="plan.id" class="plan-card">
        <div class="plan-header">
          <span class="plan-name">{{ plan.title }}</span>
          <el-tag size="small" :type="plan.cycleType === 'WEEKLY' ? 'primary' : 'warning'">{{ cycleLabel(plan.cycleType) }}</el-tag>
          <span class="plan-course">{{ plan.courseName }}</span>
          <span v-if="plan.creatorName" class="plan-creator">创建人：{{ plan.creatorName }}</span>
        </div>
        <div class="plan-info">
          <div class="info-row"><span>学期：{{ plan.semesterStart }} ~ {{ plan.semesterEnd }}</span></div>
          <div class="info-row">
            <span :class="{ disabled: plan.articleEnabled === 0 }">文章{{ plan.articleTarget }}篇</span> /
            <span :class="{ disabled: plan.wordEnabled === 0 }">单词{{ plan.wordTarget }}词({{ formatCat(plan.wordCategory) }})</span> /
            <span :class="{ disabled: plan.paperEnabled === 0 }">真题{{ plan.paperTarget }}套</span>
          </div>
          <div class="info-row">
            <span>权重：文章{{ plan.articleWeight }}% / 单词{{ plan.wordWeight }}% / 真题{{ plan.paperWeight }}%</span>
            <span style="margin-left: 16px">满分：{{ plan.totalScore }}</span>
          </div>
        </div>
        <div class="plan-classes">
          <div v-for="cls in (plan.classes || [])" :key="cls.classId" class="class-section">
            <div class="class-header">
              <strong>{{ cls.className }}{{ cls.courseSeq ? ' (' + cls.courseSeq + ')' : '' }}</strong>
              <div class="class-actions">
                <el-button v-if="!cls.optedOut" size="small" @click="editOverride(plan, cls)">修改目标</el-button>
                <el-button v-if="!cls.optedOut" size="small" type="danger" plain @click="optOut(plan, cls)">退出计划</el-button>
                <el-button v-else size="small" type="success" plain @click="optIn(plan, cls)">重新加入</el-button>
                <el-button size="small" type="primary" plain @click="viewProgress(plan, cls)">学生进度</el-button>
                <el-button size="small" @click="exportClass(plan, cls)">导出</el-button>
              </div>
            </div>
            <div v-if="cls.optedOut" class="opted-out-badge"><el-tag type="info">已退出</el-tag></div>
            <div v-else class="class-targets">
              <el-tag v-if="cls.cycleTypeOverride" type="warning">周期：{{ cycleLabel(cls.cycleTypeOverride) }}</el-tag>
              <el-tag v-if="cls.semesterStartOverride">学期：{{ cls.semesterStartOverride }}~{{ cls.semesterEndOverride }}</el-tag>
              <el-tag :class="{ 'tag-disabled': (cls.articleEnabledOverride ?? plan.articleEnabled) === 0 }">文章：{{ cls.articleTargetOverride ?? plan.articleTarget }}篇</el-tag>
              <el-tag :class="{ 'tag-disabled': (cls.wordEnabledOverride ?? plan.wordEnabled) === 0 }">单词：{{ cls.wordTargetOverride ?? plan.wordTarget }}个 ({{ formatCat(cls.wordCategoryOverride || plan.wordCategory) }})</el-tag>
              <el-tag :class="{ 'tag-disabled': (cls.paperEnabledOverride ?? plan.paperEnabled) === 0 }">真题：{{ cls.paperTargetOverride ?? plan.paperTarget }}套</el-tag>
              <el-tag type="info">权重：{{ cls.articleWeightOverride ?? plan.articleWeight }}/{{ cls.wordWeightOverride ?? plan.wordWeight }}/{{ cls.paperWeightOverride ?? plan.paperWeight }}</el-tag>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 修改目标对话框 -->
    <el-dialog v-model="overrideVisible" title="修改班级目标" width="540px" destroy-on-close class="override-dialog">
      <el-form label-width="90px">
        <el-divider content-position="left">周期与学期</el-divider>
        <el-form-item label="周期类型">
          <el-radio-group v-model="overrideForm.cycleType">
            <el-radio value="WEEKLY">按周</el-radio>
            <el-radio value="MONTHLY">按月</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="学期时间">
          <el-date-picker v-model="overrideForm.semesterRange" type="daterange" start-placeholder="开始" end-placeholder="结束" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-divider content-position="left">目标设定</el-divider>
        <el-form-item label="文章目标">
          <el-checkbox v-model="overrideForm.articleEnabled" :true-value="1" :false-value="0" style="margin-right: 8px" />
          <el-input-number v-model="overrideForm.articleTarget" :min="0" :max="100" :disabled="overrideForm.articleEnabled === 0" />
          <span style="margin-left: 8px">篇</span>
        </el-form-item>
        <el-form-item label="单词目标">
          <el-checkbox v-model="overrideForm.wordEnabled" :true-value="1" :false-value="0" style="margin-right: 8px" />
          <el-input-number v-model="overrideForm.wordTarget" :min="0" :max="1000" :disabled="overrideForm.wordEnabled === 0" />
          <span style="margin-left: 8px">个</span>
          <el-select v-model="overrideForm.wordCategory" style="width: 100px; margin-left: 12px;" :disabled="overrideForm.wordEnabled === 0">
            <el-option label="四级" value="CET4" />
            <el-option label="六级" value="CET6" />
            <el-option label="高考" value="GAOKAO" />
          </el-select>
        </el-form-item>
        <el-form-item label="真题目标">
          <el-checkbox v-model="overrideForm.paperEnabled" :true-value="1" :false-value="0" style="margin-right: 8px" />
          <el-input-number v-model="overrideForm.paperTarget" :min="0" :max="50" :disabled="overrideForm.paperEnabled === 0" />
          <span style="margin-left: 8px">套</span>
        </el-form-item>
        <el-divider content-position="left">权重与评分</el-divider>
        <el-form-item label="文章权重">
          <el-input-number v-model="overrideForm.articleWeight" :min="0" :max="100" /> %
        </el-form-item>
        <el-form-item label="单词权重">
          <el-input-number v-model="overrideForm.wordWeight" :min="0" :max="100" /> %
        </el-form-item>
        <el-form-item label="真题权重">
          <el-input-number v-model="overrideForm.paperWeight" :min="0" :max="100" /> %
        </el-form-item>
        <el-form-item>
          <el-tag v-if="weightSum !== 100" type="danger">权重之和 = {{ weightSum }}，应为 100</el-tag>
          <el-tag v-else type="success">权重之和 = 100</el-tag>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="overrideVisible = false">取消</el-button>
        <el-button type="primary" :loading="overrideSubmitting" @click="submitOverride">保存</el-button>
      </template>
    </el-dialog>

    <!-- 学生进度对话框 -->
    <el-dialog v-model="progressVisible" title="学生完成进度" width="700px" destroy-on-close class="progress-dialog" :close-on-click-modal="false">
      <el-table :data="progressData" v-loading="progressLoading" stripe height="400" style="width: 100%">
        <el-table-column prop="studentName" label="姓名" min-width="120" />
        <el-table-column prop="articlesCompleted" label="文章" min-width="80" align="center" />
        <el-table-column prop="wordsCompleted" label="单词" min-width="80" align="center" />
        <el-table-column prop="papersCompleted" label="真题" min-width="80" align="center" />
        <el-table-column prop="score" label="得分" min-width="80" align="center" />
      </el-table>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { teacherTaskPlanApi } from '@/api/taskPlan'
import XyPageHeader from '@/components/ui/XyPageHeader.vue'

const loading = ref(false)
const plans = ref([])
const overrideVisible = ref(false)
const overrideSubmitting = ref(false)
const overrideContext = reactive({ planId: null, classId: null })
const overrideForm = reactive({
  cycleType: null, semesterRange: null,
  articleTarget: 0, wordTarget: 0, wordCategory: 'CET4', paperTarget: 0,
  articleEnabled: null, wordEnabled: null, paperEnabled: null,
  articleWeight: 40, wordWeight: 30, paperWeight: 30
})
const progressVisible = ref(false)
const progressLoading = ref(false)
const progressData = ref([])

const formatCat = (cat) => ({ CET4: '四级', CET6: '六级', GAOKAO: '高考' })[cat] || cat
const weightSum = computed(() => (overrideForm.articleWeight || 0) + (overrideForm.wordWeight || 0) + (overrideForm.paperWeight || 0))
const cycleLabel = (ct) => ct === 'WEEKLY' ? '按周' : '按月'

const loadPlans = async () => {
  loading.value = true
  try {
    const res = await teacherTaskPlanApi.list()
    if (res.code === 200) plans.value = res.data || []
  } catch (e) { console.error(e) } finally { loading.value = false }
}

const editOverride = (plan, cls) => {
  overrideContext.planId = plan.id
  overrideContext.classId = cls.classId
  overrideForm.cycleType = cls.cycleTypeOverride || plan.cycleType
  overrideForm.semesterRange = (cls.semesterStartOverride && cls.semesterEndOverride)
    ? [cls.semesterStartOverride, cls.semesterEndOverride]
    : (plan.semesterStart && plan.semesterEnd ? [plan.semesterStart, plan.semesterEnd] : null)
  overrideForm.articleTarget = cls.articleTargetOverride ?? plan.articleTarget
  overrideForm.wordTarget = cls.wordTargetOverride ?? plan.wordTarget
  overrideForm.wordCategory = cls.wordCategoryOverride || plan.wordCategory
  overrideForm.paperTarget = cls.paperTargetOverride ?? plan.paperTarget
  overrideForm.articleEnabled = cls.articleEnabledOverride ?? plan.articleEnabled ?? 1
  overrideForm.wordEnabled = cls.wordEnabledOverride ?? plan.wordEnabled ?? 1
  overrideForm.paperEnabled = cls.paperEnabledOverride ?? plan.paperEnabled ?? 1
  overrideForm.articleWeight = cls.articleWeightOverride ?? plan.articleWeight
  overrideForm.wordWeight = cls.wordWeightOverride ?? plan.wordWeight
  overrideForm.paperWeight = cls.paperWeightOverride ?? plan.paperWeight
  overrideVisible.value = true
}

const submitOverride = async () => {
  const wSum = (overrideForm.articleWeight || 0) + (overrideForm.wordWeight || 0) + (overrideForm.paperWeight || 0)
  if (wSum !== 100) { ElMessage.warning('权重之和必须等于 100，当前为 ' + wSum); return }
  overrideSubmitting.value = true
  try {
    const data = {
      articleTargetOverride: overrideForm.articleTarget,
      wordTargetOverride: overrideForm.wordTarget,
      wordCategoryOverride: overrideForm.wordCategory,
      paperTargetOverride: overrideForm.paperTarget,
      articleWeightOverride: overrideForm.articleWeight,
      wordWeightOverride: overrideForm.wordWeight,
      paperWeightOverride: overrideForm.paperWeight,
      cycleTypeOverride: overrideForm.cycleType || null,
      semesterStartOverride: overrideForm.semesterRange?.[0] || null,
      semesterEndOverride: overrideForm.semesterRange?.[1] || null,
      articleEnabledOverride: overrideForm.articleEnabled,
      wordEnabledOverride: overrideForm.wordEnabled,
      paperEnabledOverride: overrideForm.paperEnabled
    }
    const res = await teacherTaskPlanApi.updateOverride(overrideContext.planId, overrideContext.classId, data)
    if (res.code === 200) { ElMessage.success('已保存'); overrideVisible.value = false; loadPlans() }
    else ElMessage.error(res.message || '保存失败')
  } catch (e) { ElMessage.error('保存失败') } finally { overrideSubmitting.value = false }
}

const optOut = async (plan, cls) => {
  try {
    await ElMessageBox.confirm('退出后该班级不再参与此计划，确认？', '退出确认')
    const res = await teacherTaskPlanApi.optOut(plan.id, cls.classId)
    if (res.code === 200) { ElMessage.success('已退出'); loadPlans() }
    else ElMessage.error(res.message || '操作失败')
  } catch (e) { /* cancelled */ }
}

const optIn = async (plan, cls) => {
  try {
    const res = await teacherTaskPlanApi.optIn(plan.id, cls.classId)
    if (res.code === 200) { ElMessage.success('已重新加入'); loadPlans() }
    else ElMessage.error(res.message || '操作失败')
  } catch (e) { ElMessage.error('操作失败') }
}

const viewProgress = async (plan, cls) => {
  progressVisible.value = true
  progressLoading.value = true
  try {
    const res = await teacherTaskPlanApi.getProgress(plan.id, cls.classId)
    if (res.code === 200) progressData.value = res.data || []
  } catch (e) { ElMessage.error('获取进度失败') } finally { progressLoading.value = false }
}

const exportClass = async (plan, cls) => {
  try {
    const res = await teacherTaskPlanApi.exportScore(plan.id, cls.classId)
    const url = window.URL.createObjectURL(new Blob([res.data], { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' }))
    const a = document.createElement('a')
    a.href = url
    a.download = `${plan.title}_${cls.className}_成绩.xlsx`
    a.click()
    window.URL.revokeObjectURL(url)
  } catch (e) { ElMessage.error('导出失败') }
}

onMounted(() => loadPlans())
</script>

<style scoped lang="scss">
.teacher-task-plans { max-width: 1300px; margin: 0 auto; padding: 16px; }
.plans-intro { margin-bottom: 18px; }
.loading-state { padding: 20px; }
.plan-card {
  border: 1px solid var(--xy-border);
  border-radius: var(--xy-radius-md);
  padding: 20px;
  background: var(--xy-surface);
  box-shadow: var(--xy-shadow-card);
  margin-bottom: 16px;
}
.plan-header {
  display: flex; align-items: center; gap: 8px; margin-bottom: 12px; flex-wrap: wrap;
  .plan-name { font-weight: 600; font-size: 16px; }
  .plan-course { color: var(--el-text-color-secondary); font-size: 13px; }
  .plan-creator { color: var(--el-text-color-secondary); font-size: 12px; margin-left: auto; }
}
.plan-info {
  background: var(--xy-surface-tint); border-radius: var(--xy-radius-sm); padding: 10px 14px; margin-bottom: 12px;
  .info-row { font-size: 13px; color: var(--el-text-color-secondary); line-height: 1.8; }
  .disabled { text-decoration: line-through; opacity: 0.5; }
}
.class-section { padding: 12px; margin-bottom: 8px; background: var(--xy-canvas); border: 1px solid var(--xy-border); border-radius: var(--xy-radius-sm); }
.class-header { display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 8px; margin-bottom: 8px; }
.class-targets { display: flex; gap: 8px; flex-wrap: wrap; }
.tag-disabled { text-decoration: line-through; opacity: 0.5; }
.opted-out-badge { margin-top: 4px; }
</style>

<style lang="scss">
.override-dialog {
  .el-dialog { display: flex; flex-direction: column; max-height: 80vh; }
  .el-dialog__body { flex: 1; min-height: 0; overflow-y: auto; }
}
.progress-dialog {
  .el-dialog {
    display: flex;
    flex-direction: column;
    margin: 0 !important;
    position: absolute;
    top: 50%;
    left: 50%;
    transform: translate(-50%, -50%);
    max-height: 80vh;
  }
  .el-dialog__body {
    flex: 1;
    min-height: 0;
    overflow: hidden;
    padding: 12px 20px;
  }
}
</style>
