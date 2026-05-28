<template>
  <div class="tag-management" v-loading="loading">
    <div class="header-bar">
      <h2>标签管理</h2>
      <el-button type="primary" @click="openCreateDialog">新增标签</el-button>
    </div>

    <el-table :data="tags" stripe size="small" class="tag-table">
      <el-table-column prop="code" label="编码" width="180" />
      <el-table-column prop="displayName" label="名称" width="120" />
      <el-table-column prop="tagType" label="类型" width="100">
        <template #default="{ row }">
          <el-tag :type="tagTypeColor(row.tagType)" size="small">{{ tagTypeLabel(row.tagType) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="priority" label="优先级" width="80" />
      <el-table-column label="规则条件" min-width="200">
        <template #default="{ row }">
          <span class="rule-preview">{{ formatRulePreview(row.ruleDefinition) }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="actionHint" label="行动建议" min-width="160" />
      <el-table-column label="启用" width="80">
        <template #default="{ row }">
          <el-switch v-model="row.enabled" @change="handleToggle(row)" />
        </template>
      </el-table-column>
      <el-table-column label="操作" width="140" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" size="small" @click="openEditDialog(row)">编辑</el-button>
          <el-button link type="danger" size="small" @click="handleDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- Create/Edit Dialog -->
    <el-dialog
      v-model="dialogVisible"
      :title="isEdit ? '编辑标签' : '新增标签'"
      width="640px"
      destroy-on-close
    >
      <el-form :model="form" label-width="100px" ref="formRef">
        <el-form-item label="编码" required>
          <el-input v-model="form.code" placeholder="例如 MY_CUSTOM_TAG" :disabled="isEdit" />
        </el-form-item>
        <el-form-item label="名称" required>
          <el-input v-model="form.displayName" placeholder="中文显示名" />
        </el-form-item>
        <el-form-item label="类型" required>
          <el-select v-model="form.tagType" placeholder="选择类型">
            <el-option label="风险" value="risk" />
            <el-option label="正面" value="positive" />
            <el-option label="能力" value="ability" />
            <el-option label="习惯" value="habit" />
            <el-option label="偏好" value="preference" />
          </el-select>
        </el-form-item>
        <el-form-item label="优先级">
          <el-input-number v-model="form.priority" :min="1" :max="999" />
        </el-form-item>
        <el-form-item label="行动建议">
          <el-input v-model="form.actionHint" placeholder="触发标签后的建议" />
        </el-form-item>

        <el-divider content-position="left">规则条件</el-divider>

        <el-form-item label="组合方式">
          <el-radio-group v-model="ruleCombiner">
            <el-radio label="AND">全部满足 (AND)</el-radio>
            <el-radio label="OR">任一满足 (OR)</el-radio>
          </el-radio-group>
        </el-form-item>

        <div v-for="(cond, idx) in ruleConditions" :key="idx" class="condition-row">
          <el-select v-model="cond.source" placeholder="来源" style="width: 100px">
            <el-option label="指标" value="metric" />
            <el-option label="标签" value="tag" />
          </el-select>

          <template v-if="cond.source === 'metric'">
            <el-select v-model="cond.field" placeholder="选择指标" filterable style="width: 160px">
              <el-option
                v-for="(label, key) in metricsMap"
                :key="key"
                :label="label"
                :value="key"
              />
            </el-select>
            <el-select v-model="cond.operator" placeholder="操作符" style="width: 100px">
              <el-option label="<" value="LT" />
              <el-option label="<=" value="LTE" />
              <el-option label=">" value="GT" />
              <el-option label=">=" value="GTE" />
              <el-option label="=" value="EQ" />
            </el-select>
            <el-input-number v-model="cond.value" :precision="2" controls-position="right" style="width: 120px" />
          </template>

          <template v-else>
            <el-input v-model="cond.code" placeholder="标签编码" style="width: 160px" />
            <el-select v-model="cond.operator" placeholder="操作符" style="width: 120px">
              <el-option label="已匹配" value="MATCHED" />
              <el-option label="未匹配" value="NOT_MATCHED" />
            </el-select>
          </template>

          <el-button link type="danger" @click="ruleConditions.splice(idx, 1)">删除</el-button>
        </div>

        <el-button class="add-condition-btn" @click="addCondition">+ 添加条件</el-button>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSubmit" :loading="submitting">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  listLearnerTags, createLearnerTag, updateLearnerTag,
  deleteLearnerTag, toggleLearnerTag, listTagMetrics
} from '@/api/learnerProfile'

const loading = ref(false)
const submitting = ref(false)
const tags = ref([])
const metricsMap = ref({})
const dialogVisible = ref(false)
const isEdit = ref(false)
const editId = ref(null)

const form = ref({ code: '', displayName: '', tagType: 'risk', priority: 100, actionHint: '' })
const ruleCombiner = ref('AND')
const ruleConditions = ref([])

const TAG_TYPE_MAP = {
  risk: '风险', positive: '正面', ability: '能力', habit: '习惯', preference: '偏好'
}
const TAG_TYPE_COLOR = {
  risk: 'danger', positive: 'success', ability: 'warning', habit: 'info', preference: 'primary'
}
const tagTypeLabel = (t) => TAG_TYPE_MAP[t] || t
const tagTypeColor = (t) => TAG_TYPE_COLOR[t] || ''

const OP_LABELS = { LT: '<', LTE: '<=', GT: '>', GTE: '>=', EQ: '=' }

function formatRulePreview(json) {
  try {
    const rule = JSON.parse(json)
    const parts = (rule.conditions || []).map(c => {
      if (c.source === 'metric') {
        const metricName = metricsMap.value[c.field] || c.field
        return `${metricName} ${OP_LABELS[c.operator] || c.operator} ${c.value}`
      }
      return `${c.code} ${c.operator === 'NOT_MATCHED' ? '未匹配' : '已匹配'}`
    })
    return parts.join(rule.combiner === 'OR' ? ' 或 ' : ' 且 ')
  } catch {
    return json
  }
}

function buildRuleJson() {
  return JSON.stringify({
    conditions: ruleConditions.value.map(c => {
      if (c.source === 'metric') {
        return { source: 'metric', field: c.field, operator: c.operator, value: c.value }
      }
      return { source: 'tag', code: c.code, operator: c.operator }
    }),
    combiner: ruleCombiner.value
  })
}

function parseRuleJson(json) {
  try {
    const rule = JSON.parse(json)
    ruleCombiner.value = rule.combiner || 'AND'
    ruleConditions.value = (rule.conditions || []).map(c => {
      if (c.source === 'metric') {
        return { source: 'metric', field: c.field || '', operator: c.operator || 'GTE', value: c.value ?? 0 }
      }
      return { source: 'tag', code: c.code || '', operator: c.operator || 'MATCHED', field: '', value: 0 }
    })
  } catch {
    ruleCombiner.value = 'AND'
    ruleConditions.value = []
  }
}

function addCondition() {
  ruleConditions.value.push({ source: 'metric', field: '', operator: 'GTE', value: 0, code: '' })
}

async function loadData() {
  loading.value = true
  try {
    const [tagsRes, metricsRes] = await Promise.all([listLearnerTags(), listTagMetrics()])
    tags.value = tagsRes?.data || []
    metricsMap.value = metricsRes?.data || {}
  } catch (e) {
    ElMessage.error('加载数据失败')
    console.error(e)
  } finally {
    loading.value = false
  }
}

function openCreateDialog() {
  isEdit.value = false
  editId.value = null
  form.value = { code: '', displayName: '', tagType: 'risk', priority: 100, actionHint: '' }
  ruleCombiner.value = 'AND'
  ruleConditions.value = [{ source: 'metric', field: '', operator: 'GTE', value: 0, code: '' }]
  dialogVisible.value = true
}

function openEditDialog(row) {
  isEdit.value = true
  editId.value = row.id
  form.value = {
    code: row.code,
    displayName: row.displayName,
    tagType: row.tagType,
    priority: row.priority,
    actionHint: row.actionHint || ''
  }
  parseRuleJson(row.ruleDefinition)
  dialogVisible.value = true
}

async function handleSubmit() {
  if (!form.value.code || !form.value.displayName) {
    ElMessage.warning('编码和名称为必填项')
    return
  }
  if (ruleConditions.value.length === 0) {
    ElMessage.warning('请至少添加一个条件')
    return
  }

  const data = {
    ...form.value,
    ruleDefinition: buildRuleJson()
  }

  submitting.value = true
  try {
    if (isEdit.value) {
      await updateLearnerTag(editId.value, data)
      ElMessage.success('更新成功')
    } else {
      await createLearnerTag(data)
      ElMessage.success('创建成功')
    }
    dialogVisible.value = false
    await loadData()
  } catch (e) {
    ElMessage.error(e?.message || '操作失败')
  } finally {
    submitting.value = false
  }
}

async function handleToggle(row) {
  try {
    await toggleLearnerTag(row.id, row.enabled)
    ElMessage.success(row.enabled ? '已启用' : '已禁用')
  } catch (e) {
    row.enabled = !row.enabled
    ElMessage.error('切换失败')
  }
}

async function handleDelete(row) {
  try {
    await ElMessageBox.confirm(`确定删除标签「${row.displayName}」？`, '确认删除', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消'
    })
    await deleteLearnerTag(row.id)
    ElMessage.success('删除成功')
    await loadData()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error('删除失败')
  }
}

onMounted(() => {
  loadData()
})
</script>

<style scoped>
.tag-management { padding: 20px; }
.header-bar { display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px; }
.header-bar h2 { margin: 0; font-size: 18px; }
.tag-table { width: 100%; }
.rule-preview { font-size: 12px; color: #606266; }
.condition-row { display: flex; align-items: center; gap: 8px; margin-bottom: 8px; flex-wrap: wrap; }
.add-condition-btn { width: 100%; margin-top: 4px; border-style: dashed; }
</style>
