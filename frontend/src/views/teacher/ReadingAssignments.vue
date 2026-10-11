<template>
  <div class="reading-assignments">
    <section class="page-heading">
      <div>
        <h2>自由阅读作业</h2>
        <p>设置课内、课外完成篇数，学生在对应范围内自由选择文章。</p>
      </div>
      <div class="heading-actions">
        <el-button @click="router.push('/teacher/assignments')">指定课文作业</el-button>
        <el-button type="primary" :loading="saving" @click="publish">发布作业</el-button>
      </div>
    </section>

    <el-form label-position="top" class="assignment-form">
      <div class="form-grid">
        <el-form-item label="作业标题" required>
          <el-input v-model="form.title" maxlength="200" show-word-limit placeholder="例如：第一周阅读练习" />
        </el-form-item>
        <el-form-item label="截止时间">
          <el-date-picker v-model="form.deadline" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" placeholder="可不设置" style="width: 100%" />
        </el-form-item>
      </div>
      <el-form-item label="班级（用于选择学生）">
        <el-select v-model="form.classId" clearable placeholder="选择班级" style="width: 100%" @change="loadStudents">
          <el-option v-for="item in classes" :key="item.id" :label="item.name" :value="item.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="指定学生" required>
        <el-select v-model="form.studentIds" multiple filterable collapse-tags collapse-tags-tooltip :disabled="!form.classId" placeholder="先选择班级，再选择学生" style="width: 100%">
          <el-option v-for="item in students" :key="item.id" :label="studentLabel(item)" :value="item.id" />
        </el-select>
      </el-form-item>
      <div class="form-grid reading-counts">
        <el-form-item label="课内文章" required>
          <el-input-number v-model="form.innerRequiredCount" :min="0" :max="100" />
          <span class="count-unit">篇</span>
        </el-form-item>
        <el-form-item label="课外文章" required>
          <el-input-number v-model="form.outerRequiredCount" :min="0" :max="100" />
          <span class="count-unit">篇</span>
        </el-form-item>
      </div>
      <el-form-item label="作业说明">
        <el-input v-model="form.note" type="textarea" :rows="3" placeholder="可选" />
      </el-form-item>
      <el-alert type="info" :closable="false" show-icon class="rule-tip">
        学生可分别从课内、课外文章范围内自由选择，完成阅读后计入对应篇数；推荐文章同样可以完成。
      </el-alert>
    </el-form>

    <section class="history">
      <h3>已发布</h3>
      <el-table :data="published" v-loading="historyLoading" size="small" empty-text="还没有阅读作业">
        <el-table-column prop="title" label="作业" min-width="180" />
        <el-table-column label="学生" width="100"><template #default="{ row }">{{ row.studentIds?.length || 0 }} 人</template></el-table-column>
        <el-table-column label="阅读要求" width="160"><template #default="{ row }">{{ requirementLabel(row) }}</template></el-table-column>
        <el-table-column prop="deadline" label="截止时间" width="180"><template #default="{ row }">{{ formatDate(row.deadline) }}</template></el-table-column>
      </el-table>
    </section>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'
import request from '@/utils/request'
import { createReadingAssignment, getReadingAssignments, getReadingAssignmentStudents } from '@/api/teacher'

const classes = ref([])
const router = useRouter()
const students = ref([])
const published = ref([])
const historyLoading = ref(false)
const saving = ref(false)
const initialForm = () => ({ title: '', deadline: null, classId: null, studentIds: [], innerRequiredCount: 2, outerRequiredCount: 1, note: '' })
const form = reactive(initialForm())

const studentLabel = item => item.name ? `${item.name} (${item.username})` : item.username
const formatDate = value => value ? new Date(value).toLocaleString('zh-CN', { hour12: false }) : '未设置'
const requirementLabel = row => {
  if (row.articleType === 'BOTH') return `课内 ${row.innerCount} 篇 / 课外 ${row.outerCount} 篇`
  return row.articleType === 'OUTER' ? `课外任选 ${row.requiredCount} 篇` : `课内任选 ${row.requiredCount} 篇`
}

async function loadClasses() {
  const response = await request({ url: '/api/classes', method: 'get', params: { page: 1, size: 100 } })
  classes.value = response?.data?.records || []
}
async function loadStudents() {
  form.studentIds = []
  students.value = []
  if (!form.classId) return
  const response = await getReadingAssignmentStudents(form.classId)
  students.value = response?.data || []
}
async function loadHistory() {
  historyLoading.value = true
  try { published.value = (await getReadingAssignments())?.data || [] } finally { historyLoading.value = false }
}
async function publish() {
  if (!form.title.trim()) return ElMessage.warning('请输入作业标题')
  if (!form.classId) return ElMessage.warning('请选择班级')
  if (!form.studentIds.length) return ElMessage.warning('请选择学生')
  if (form.innerRequiredCount + form.outerRequiredCount < 1) return ElMessage.warning('课内和课外文章至少需要完成 1 篇')
  saving.value = true
  try {
    await createReadingAssignment({ ...form, title: form.title.trim() })
    ElMessage.success('作业已发布')
    Object.assign(form, initialForm())
    students.value = []
    await loadHistory()
  } finally { saving.value = false }
}
onMounted(async () => { await Promise.all([loadClasses(), loadHistory()]) })
</script>

<style scoped lang="scss">
.reading-assignments { max-width: 1120px; margin: 0 auto; padding: 16px; }
.page-heading { display: flex; justify-content: space-between; gap: 16px; align-items: center; margin-bottom: 18px; }
.heading-actions { display: flex; gap: 8px; flex-wrap: wrap; justify-content: flex-end; }
h2, h3, p { margin: 0; } .page-heading p { color: #667085; margin-top: 5px; font-size: 14px; }
.assignment-form, .history { border: 1px solid #dfe4ea; background: #fff; padding: 16px; border-radius: 6px; }
.form-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 16px; }
.reading-counts :deep(.el-form-item__content) { display: flex; align-items: center; gap: 8px; }
.reading-counts :deep(.el-input-number) { width: min(180px, calc(100% - 32px)); }
.count-unit { color: #475467; }
.history { margin-top: 16px; } .history h3 { margin-bottom: 12px; }
.rule-tip { margin-top: 4px; }
@media (max-width: 680px) { .reading-assignments { padding: 12px; } .form-grid { grid-template-columns: 1fr; } .page-heading { align-items: flex-start; flex-direction: column; } .heading-actions { width: 100%; justify-content: flex-start; } }
</style>
