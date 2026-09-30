<template>
  <div class="task-plans-page">
    <XyPageHeader title="学期学习计划" eyebrow="LEARNING PLANS" description="为课程与班级设定清晰、可追踪的学习目标。" variant="compact">
      <template #actions>
        <el-select v-model="filterStatus" placeholder="状态筛选" clearable aria-label="按状态筛选计划" @change="loadPlans" class="status-filter">
          <el-option label="草稿" value="DRAFT" />
          <el-option label="进行中" value="ACTIVE" />
          <el-option label="已关闭" value="CLOSED" />
        </el-select>
        <el-button type="primary" @click="openCreateDialog">创建计划</el-button>
      </template>
    </XyPageHeader>

    <XySurface compact class="plans-surface" title="计划列表" description="查看发布状态、目标与班级覆盖情况。">
    <el-table :data="plans" v-loading="loading" stripe empty-text="暂无计划，点击右上角创建" style="width: 100%">
      <el-table-column prop="title" label="计划名称" min-width="140" />
      <el-table-column prop="courseName" label="课程" width="120" />
      <el-table-column label="周期" width="70">
        <template #default="{ row }">{{ row.cycleType === 'WEEKLY' ? '周' : '月' }}</template>
      </el-table-column>
      <el-table-column label="目标概览" min-width="220">
        <template #default="{ row }">
          <span :style="row.articleEnabled === 0 ? 'text-decoration:line-through;color:#999' : ''">{{ row.articleTarget }}篇</span> /
          <span :style="row.wordEnabled === 0 ? 'text-decoration:line-through;color:#999' : ''">{{ row.wordTarget }}词({{ formatCat(row.wordCategory) }})</span> /
          <span :style="row.paperEnabled === 0 ? 'text-decoration:line-through;color:#999' : ''">{{ row.paperTarget }}套</span>
        </template>
      </el-table-column>
      <el-table-column label="学期" width="180">
        <template #default="{ row }">{{ row.semesterStart }} ~ {{ row.semesterEnd }}</template>
      </el-table-column>
      <el-table-column prop="classCount" label="班级" width="60" />
      <el-table-column label="状态" width="80">
        <template #default="{ row }">
          <el-tag :type="statusType(row.status)" size="small">{{ statusText(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="260" fixed="right">
        <template #default="{ row }">
          <el-button size="small" @click="viewStats(row)">统计</el-button>
          <el-button v-if="row.status === 'DRAFT'" size="small" @click="editPlan(row)">编辑</el-button>
          <el-button v-if="row.status === 'DRAFT'" size="small" type="success" @click="publishPlan(row)">发布</el-button>
          <el-button v-if="row.status === 'ACTIVE'" size="small" type="warning" @click="closePlan(row)">关闭</el-button>
          <el-button size="small" type="primary" @click="exportPlan(row)">导出</el-button>
        </template>
      </el-table-column>
    </el-table>
    </XySurface>

    <!-- 创建/编辑对话框 -->
    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑计划' : '创建计划'" width="600px" destroy-on-close class="task-plan-dialog">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="计划名称" prop="title">
          <el-input v-model="form.title" placeholder="如：2026-2027第一学期默认任务" />
        </el-form-item>
        <el-form-item label="关联课程" prop="courseLabel">
          <el-select v-model="form.courseLabel" placeholder="选择课程" style="width: 100%" filterable @change="onCourseChange">
            <el-option v-for="label in courseLabels" :key="label" :label="label" :value="label" />
          </el-select>
        </el-form-item>
        <el-form-item label="周期类型" prop="cycleType">
          <el-radio-group v-model="form.cycleType">
            <el-radio value="WEEKLY">按周</el-radio>
            <el-radio value="MONTHLY">按月</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="学期时间" prop="semesterRange">
          <el-date-picker v-model="form.semesterRange" type="daterange" start-placeholder="开始" end-placeholder="结束" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-divider content-position="left">目标设定</el-divider>
        <el-form-item label="文章目标">
          <el-checkbox v-model="form.articleEnabled" :true-value="1" :false-value="0" style="margin-right: 8px" />
          <el-input-number v-model="form.articleTarget" :min="0" :max="100" :disabled="!form.articleEnabled" />
          <span style="margin-left:8px">篇/{{ form.cycleType === 'WEEKLY' ? '周' : '月' }}</span>
        </el-form-item>
        <el-form-item label="单词目标">
          <el-checkbox v-model="form.wordEnabled" :true-value="1" :false-value="0" style="margin-right: 8px" />
          <el-input-number v-model="form.wordTarget" :min="0" :max="1000" :disabled="!form.wordEnabled" />
          <span style="margin-left:8px">个/{{ form.cycleType === 'WEEKLY' ? '周' : '月' }}</span>
          <el-select v-model="form.wordCategory" style="width: 100px; margin-left: 12px;" :disabled="!form.wordEnabled">
            <el-option label="四级" value="CET4" />
            <el-option label="六级" value="CET6" />
            <el-option label="高考" value="GAOKAO" />
          </el-select>
        </el-form-item>
        <el-form-item label="真题目标">
          <el-checkbox v-model="form.paperEnabled" :true-value="1" :false-value="0" style="margin-right: 8px" />
          <el-input-number v-model="form.paperTarget" :min="0" :max="50" :disabled="!form.paperEnabled" />
          <span style="margin-left:8px">套/{{ form.cycleType === 'WEEKLY' ? '周' : '月' }}</span>
        </el-form-item>
        <el-divider content-position="left">权重与评分</el-divider>
        <el-form-item label="文章权重">
          <el-input-number v-model="form.articleWeight" :min="0" :max="100" /> %
        </el-form-item>
        <el-form-item label="单词权重">
          <el-input-number v-model="form.wordWeight" :min="0" :max="100" /> %
        </el-form-item>
        <el-form-item label="真题权重">
          <el-input-number v-model="form.paperWeight" :min="0" :max="100" /> %
        </el-form-item>
        <el-form-item>
          <el-tag v-if="weightSum !== 100" type="danger">权重之和 = {{ weightSum }}，应为 100</el-tag>
          <el-tag v-else type="success">权重之和 = 100</el-tag>
        </el-form-item>
        <el-form-item label="满分值">
          <el-input-number v-model="form.totalScore" :min="1" :max="200" />
        </el-form-item>
        <el-divider content-position="left">选择班级</el-divider>
        <el-form-item label="班级" prop="classIds">
          <div style="margin-bottom: 8px;">
            <el-checkbox :model-value="isAllSelected" :indeterminate="isIndeterminate" @change="toggleSelectAll">全选</el-checkbox>
            <span style="color:#999; margin-left: 8px; font-size: 12px;">已选 {{ form.classIds.length }} / {{ courseClasses.length }}</span>
          </div>
          <el-checkbox-group v-model="form.classIds">
            <el-checkbox v-for="cls in courseClasses" :key="cls.id" :value="cls.id" :label="cls.id">
              {{ cls.name }} <span style="color:#999">({{ cls.teacherName || '未分配' }}{{ cls.courseSeq ? ' · ' + cls.courseSeq : '' }})</span>
            </el-checkbox>
          </el-checkbox-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitForm">{{ isEdit ? '保存' : '创建' }}</el-button>
      </template>
    </el-dialog>

    <!-- 统计对话框 -->
    <el-dialog v-model="statsVisible" title="完成统计" width="700px" destroy-on-close>
      <div v-loading="statsLoading">
        <div v-if="stats" class="stats-overview">
          <div class="stat-card"><strong>{{ stats.totalStudents }}</strong><span>学生总数</span></div>
          <div class="stat-card"><strong>{{ stats.avgArticles }}</strong><span>平均文章</span></div>
          <div class="stat-card"><strong>{{ stats.avgWords }}</strong><span>平均单词</span></div>
          <div class="stat-card"><strong>{{ stats.avgPapers }}</strong><span>平均真题</span></div>
        </div>
        <el-table v-if="stats && stats.classDetails" :data="stats.classDetails" stripe>
          <el-table-column prop="className" label="班级" />
          <el-table-column prop="studentCount" label="学生数" width="80" />
          <el-table-column prop="avgArticleRate" label="文章完成率" width="100">
            <template #default="{ row }">{{ row.avgArticleRate }}%</template>
          </el-table-column>
          <el-table-column prop="avgWordRate" label="单词完成率" width="100">
            <template #default="{ row }">{{ row.avgWordRate }}%</template>
          </el-table-column>
          <el-table-column prop="avgPaperRate" label="真题完成率" width="100">
            <template #default="{ row }">{{ row.avgPaperRate }}%</template>
          </el-table-column>
        </el-table>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { adminTaskPlanApi } from '@/api/taskPlan'
import XyPageHeader from '@/components/ui/XyPageHeader.vue'
import XySurface from '@/components/ui/XySurface.vue'

const loading = ref(false)
const plans = ref([])
const filterStatus = ref('')
const dialogVisible = ref(false)
const isEdit = ref(false)
const submitting = ref(false)
const formRef = ref(null)
const courseLabels = ref([])
const courseClasses = ref([])
const statsVisible = ref(false)
const statsLoading = ref(false)
const stats = ref(null)

const form = reactive({
  id: null,
  title: '',
  courseLabel: '',
  cycleType: 'WEEKLY',
  semesterRange: [],
  articleTarget: 3,
  wordTarget: 50,
  wordCategory: 'CET4',
  paperTarget: 1,
  articleEnabled: 1,
  wordEnabled: 1,
  paperEnabled: 1,
  articleWeight: 40,
  wordWeight: 30,
  paperWeight: 30,
  totalScore: 100,
  classIds: []
})

const weightSum = computed(() => (form.articleWeight || 0) + (form.wordWeight || 0) + (form.paperWeight || 0))
const isAllSelected = computed(() => courseClasses.value.length > 0 && form.classIds.length === courseClasses.value.length)
const isIndeterminate = computed(() => form.classIds.length > 0 && form.classIds.length < courseClasses.value.length)
const toggleSelectAll = (val) => {
  form.classIds = val ? courseClasses.value.map(c => c.id) : []
}

const rules = {
  title: [{ required: true, message: '请输入计划名称', trigger: 'blur' }],
  courseLabel: [{ required: true, message: '请选择课程', trigger: 'change' }],
  cycleType: [{ required: true, message: '请选择周期类型', trigger: 'change' }],
  semesterRange: [{ required: true, message: '请选择学期时间', trigger: 'change' }],
  classIds: [{ type: 'array', min: 1, required: true, message: '请至少选择一个班级', trigger: 'change' }]
}

const formatCat = (cat) => ({ CET4: '四级', CET6: '六级', GAOKAO: '高考' })[cat] || cat
const statusType = (s) => ({ DRAFT: 'info', ACTIVE: 'success', CLOSED: 'warning' })[s] || 'info'
const statusText = (s) => ({ DRAFT: '草稿', ACTIVE: '进行中', CLOSED: '已关闭' })[s] || s

const loadPlans = async () => {
  loading.value = true
  try {
    const params = {}
    if (filterStatus.value) params.status = filterStatus.value
    const res = await adminTaskPlanApi.list(params)
    if (res.code === 200) plans.value = res.data || []
  } catch (e) {
    console.error(e)
  } finally {
    loading.value = false
  }
}

const loadCourses = async () => {
  try {
    const res = await adminTaskPlanApi.getCourseLabels()
    if (res.code === 200) courseLabels.value = res.data || []
  } catch (e) {
    console.error(e)
  }
}

const onCourseChange = async (label) => {
  form.classIds = []
  if (!label) { courseClasses.value = []; return }
  try {
    const res = await adminTaskPlanApi.getClassesByLabel(label)
    if (res.code === 200) courseClasses.value = res.data || []
    else courseClasses.value = []
  } catch (e) {
    courseClasses.value = []
  }
}

const resetForm = () => {
  Object.assign(form, {
    id: null, title: '', courseLabel: '', cycleType: 'WEEKLY', semesterRange: [],
    articleTarget: 3, wordTarget: 50, wordCategory: 'CET4', paperTarget: 1,
    articleEnabled: 1, wordEnabled: 1, paperEnabled: 1,
    articleWeight: 40, wordWeight: 30, paperWeight: 30, totalScore: 100, classIds: []
  })
  courseClasses.value = []
}

const openCreateDialog = () => {
  resetForm()
  isEdit.value = false
  dialogVisible.value = true
}

const editPlan = async (row) => {
  isEdit.value = true
  try {
    const res = await adminTaskPlanApi.getDetail(row.id)
    if (res.code === 200) {
      const d = res.data
      const label = d.courseName || ''
      Object.assign(form, {
        id: d.id, title: d.title, courseLabel: label, cycleType: d.cycleType,
        semesterRange: [d.semesterStart, d.semesterEnd],
        articleTarget: d.articleTarget, wordTarget: d.wordTarget, wordCategory: d.wordCategory,
        paperTarget: d.paperTarget,
        articleEnabled: d.articleEnabled ?? 1, wordEnabled: d.wordEnabled ?? 1, paperEnabled: d.paperEnabled ?? 1,
        articleWeight: d.articleWeight, wordWeight: d.wordWeight,
        paperWeight: d.paperWeight, totalScore: d.totalScore,
        classIds: (d.classes || []).map(c => c.classId)
      })
      if (label) await onCourseChange(label)
      form.classIds = (d.classes || []).map(c => c.classId)
    }
  } catch (e) {
    ElMessage.error('获取详情失败')
  }
  dialogVisible.value = true
}

const submitForm = async () => {
  if (!formRef.value) return
  try { await formRef.value.validate() } catch { return }
  if (weightSum.value !== 100) {
    ElMessage.warning('权重之和必须等于 100')
    return
  }
  submitting.value = true
  try {
    const data = {
      title: form.title, courseId: 999, courseLabel: form.courseLabel, cycleType: form.cycleType,
      semesterStart: form.semesterRange[0], semesterEnd: form.semesterRange[1],
      articleTarget: form.articleTarget, wordTarget: form.wordTarget, wordCategory: form.wordCategory,
      paperTarget: form.paperTarget,
      articleEnabled: form.articleEnabled, wordEnabled: form.wordEnabled, paperEnabled: form.paperEnabled,
      articleWeight: form.articleWeight, wordWeight: form.wordWeight,
      paperWeight: form.paperWeight, totalScore: form.totalScore, classIds: form.classIds
    }
    const res = isEdit.value
      ? await adminTaskPlanApi.update(form.id, data)
      : await adminTaskPlanApi.create(data)
    if (res.code === 200) {
      ElMessage.success(isEdit.value ? '更新成功' : '创建成功')
      dialogVisible.value = false
      loadPlans()
    } else {
      ElMessage.error(res.message || '操作失败')
    }
  } catch (e) {
    console.error('submitForm error:', e)
    ElMessage.error('操作失败')
  } finally {
    submitting.value = false
  }
}

const publishPlan = async (row) => {
  await ElMessageBox.confirm('确认发布？发布后不可编辑基础设置。', '发布确认')
  try {
    const res = await adminTaskPlanApi.publish(row.id)
    if (res.code === 200) { ElMessage.success('已发布'); loadPlans() }
    else ElMessage.error(res.message || '发布失败')
  } catch (e) { /* cancelled */ }
}

const closePlan = async (row) => {
  await ElMessageBox.confirm('确认关闭此计划？', '关闭确认')
  try {
    const res = await adminTaskPlanApi.close(row.id)
    if (res.code === 200) { ElMessage.success('已关闭'); loadPlans() }
    else ElMessage.error(res.message || '关闭失败')
  } catch (e) { /* cancelled */ }
}

const viewStats = async (row) => {
  statsVisible.value = true
  statsLoading.value = true
  try {
    const res = await adminTaskPlanApi.getStats(row.id)
    if (res.code === 200) stats.value = res.data
  } catch (e) {
    ElMessage.error('获取统计失败')
  } finally {
    statsLoading.value = false
  }
}

const exportPlan = async (row) => {
  try {
    const res = await adminTaskPlanApi.exportScore(row.id)
    const url = window.URL.createObjectURL(new Blob([res]))
    const a = document.createElement('a')
    a.href = url
    a.download = `${row.title}_成绩.xlsx`
    a.click()
    window.URL.revokeObjectURL(url)
  } catch (e) {
    ElMessage.error('导出失败')
  }
}

onMounted(() => {
  loadPlans()
  loadCourses()
})
</script>

<style scoped lang="scss">
.task-plans-page {
  display: grid;
  gap: 18px;
  max-width: 1440px;
  margin: 0 auto;
  padding: 8px;
}
.status-filter { width: 132px; }
.plans-surface { overflow: hidden; }
.plans-surface :deep(.el-table th.el-table__cell) { font-weight: 700; }
.plans-surface :deep(.el-table .el-button + .el-button) { margin-left: 5px; }
.stats-overview {
  display: flex;
  gap: 16px;
  margin-bottom: 16px;
  .stat-card {
    flex: 1;
    text-align: center;
    padding: 12px;
    background: var(--el-fill-color-light);
    border-radius: var(--xy-radius-md);
    strong { display: block; font-size: 24px; color: var(--el-color-primary); }
    span { font-size: 12px; color: var(--el-text-color-secondary); }
  }
}
@media (max-width: 640px) {
  .task-plans-page { padding: 0; gap: 12px; }
  .status-filter { flex: 1; min-width: 120px; }
}
</style>

<style lang="scss">
.task-plan-dialog {
  .el-dialog {
    display: flex;
    flex-direction: column;
    max-height: 80vh;
  }
  .el-dialog__body {
    flex: 1;
    min-height: 0;
    overflow-y: auto;
  }
}
</style>
