<template>
  <div class="ai-providers">
    <div class="page-header">
      <h2>AI 服务管理</h2>
      <el-button @click="loadProviders" :loading="loading">
        <el-icon><Refresh /></el-icon>
        刷新
      </el-button>
    </div>

    <!-- 服务类型 Tab -->
    <el-tabs v-model="activeService" @tab-change="onServiceChange">
      <el-tab-pane
        v-for="svc in serviceTypes"
        :key="svc.value"
        :label="svc.label"
        :name="svc.value"
      />
    </el-tabs>

    <!-- 工具栏 -->
    <div class="toolbar">
      <el-tag type="info" size="small">
        {{ currentProviders.length }} 个服务商 /
        {{ currentProviders.filter(p => p.enabled === 1).length }} 个启用
      </el-tag>
      <el-button type="primary" size="small" @click="openCreateDialog">
        <el-icon><Plus /></el-icon>
        添加服务商
      </el-button>
    </div>

    <!-- 服务商列表 -->
    <div v-loading="loading" class="provider-list">
      <el-empty v-if="!loading && currentProviders.length === 0" description="暂无服务商，点击「添加服务商」创建" />

      <el-card
        v-for="p in currentProviders"
        :key="p.id"
        class="provider-card"
        :class="{ disabled: p.enabled !== 1 }"
      >
        <div class="provider-header">
          <div class="provider-name">
            <el-tag :type="p.enabled === 1 ? 'success' : 'info'" size="small" style="margin-right:8px">
              {{ p.enabled === 1 ? '启用' : '禁用' }}
            </el-tag>
            <span class="name-text">{{ p.name }}</span>
          </div>
          <div class="provider-actions">
            <el-switch
              :model-value="p.enabled === 1"
              @change="val => toggleProvider(p, val)"
              size="small"
            />
            <el-button size="small" @click="openTestPanel(p)" :loading="testingId === p.id">测试</el-button>
            <el-button size="small" type="primary" @click="openEditDialog(p)">编辑</el-button>
            <el-button size="small" type="danger" @click="confirmDelete(p)">删除</el-button>
          </div>
        </div>

        <div class="provider-body">
          <div class="meta-row">
            <span class="meta-label">API 地址</span>
            <span class="meta-value meta-url">{{ p.apiUrl }}</span>
          </div>
          <div class="meta-row">
            <span class="meta-label">模型</span>
            <span class="meta-value">{{ p.model || '—' }}</span>
          </div>
          <div class="meta-row">
            <span class="meta-label">API Key</span>
            <span class="meta-value meta-key">{{ p.apiKey }}</span>
          </div>
          <div v-if="p.appId" class="meta-row">
            <span class="meta-label">App ID</span>
            <span class="meta-value meta-key">{{ p.appId }}</span>
          </div>
          <div class="meta-row">
            <span class="meta-label">权重</span>
            <span class="meta-value">
              <el-tag size="small" type="warning">{{ p.weight }}</el-tag>
              <span class="weight-hint">（按权重比例负载均衡）</span>
            </span>
          </div>
          <div class="meta-row">
            <span class="meta-label">超时</span>
            <span class="meta-value">{{ p.timeoutMs }} ms</span>
          </div>
          <div v-if="p.remark" class="meta-row">
            <span class="meta-label">备注</span>
            <span class="meta-value">{{ p.remark }}</span>
          </div>
        </div>

        <!-- 内联测试结果 -->
        <div v-if="testResults[p.id]" class="test-result" :class="testResults[p.id].success ? 'is-success' : 'is-error'">
          <span class="test-label">{{ testResults[p.id].success ? '✓ 测试通过' : '✗ 测试失败' }}</span>
          <span class="test-detail" v-if="testResults[p.id].success">{{ testResults[p.id].output }}</span>
          <span class="test-detail" v-else>{{ testResults[p.id].error }}</span>
        </div>
      </el-card>
    </div>

    <!-- 负载均衡说明 -->
    <el-alert
      v-if="currentProviders.filter(p => p.enabled === 1).length > 1"
      type="info"
      show-icon
      :closable="false"
      style="margin-top:12px"
    >
      <template #title>
        当前启用 {{ currentProviders.filter(p => p.enabled === 1).length }} 个服务商，按权重比例负载均衡：{{ weightDistribution }}
      </template>
    </el-alert>

    <!-- 新增 / 编辑对话框 -->
    <el-dialog
      v-model="dialogVisible"
      :title="editingProvider ? '编辑服务商' : '添加服务商'"
      width="560px"
      :close-on-click-modal="false"
    >
      <el-form :model="form" label-width="100px" ref="formRef" :rules="rules">
        <el-form-item label="名称" prop="name">
          <el-input v-model="form.name" placeholder="如：SiliconFlow-主" />
        </el-form-item>
        <el-form-item label="服务类型" prop="service">
          <el-select v-model="form.service" style="width:100%">
            <el-option v-for="svc in serviceTypes" :key="svc.value" :label="svc.label" :value="svc.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="API 地址" prop="apiUrl">
          <el-input v-model="form.apiUrl" placeholder="https://api.siliconflow.cn/v1/chat/completions" />
        </el-form-item>
        <el-form-item label="API Key">
          <el-input
            v-model="form.apiKey"
            :type="showKey ? 'text' : 'password'"
            :placeholder="editingProvider ? '留空保持原值不变' : '请输入 API Key'"
          >
            <template #append>
              <el-button @click="showKey = !showKey">
                <el-icon><View v-if="!showKey" /><Hide v-else /></el-icon>
              </el-button>
            </template>
          </el-input>
        </el-form-item>
        <el-form-item label="App ID">
          <el-input v-model="form.appId" placeholder="HiAgent 等服务商必填，其他可留空" />
          <div class="field-hint">HiAgent Q&A 功能必须填写，其他服务商可留空</div>
        </el-form-item>
        <el-form-item label="模型">
          <el-input v-model="form.model" placeholder="如：tencent/Hunyuan-MT-7B" />
        </el-form-item>
        <el-form-item label="权重">
          <el-input-number v-model="form.weight" :min="1" :max="100" style="width:100%" />
          <div class="field-hint">权重越大，分配到该服务商的请求越多</div>
        </el-form-item>
        <el-form-item label="超时 (ms)">
          <el-input-number v-model="form.timeoutMs" :min="5000" :max="120000" :step="5000" style="width:100%" />
        </el-form-item>
        <el-form-item label="启用">
          <el-switch v-model="form.enabledBool" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSave" :loading="saving">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh, Plus, View, Hide } from '@element-plus/icons-vue'
import {
  listAiProviders,
  createAiProvider,
  updateAiProvider,
  deleteAiProvider,
  toggleAiProvider,
  testAiProvider
} from '@/api/systemConfig'

const serviceTypes = [
  { value: 'translate', label: '翻译服务 (translate)' },
  { value: 'article',   label: '文章生成 (article)' },
  { value: 'chat',      label: 'AI 对话 (chat)' }
]

const loading       = ref(false)
const saving        = ref(false)
const testingId     = ref(null)
const activeService = ref('translate')
const allProviders  = ref([])
const testResults   = ref({})

const dialogVisible   = ref(false)
const editingProvider = ref(null)
const showKey         = ref(false)
const formRef         = ref(null)

const form = ref({
  name: '',
  service: 'translate',
  apiUrl: '',
  apiKey: '',
  appId: '',
  model: '',
  weight: 10,
  timeoutMs: 30000,
  enabledBool: true,
  remark: ''
})

const rules = {
  name:    [{ required: true, message: '请输入名称', trigger: 'blur' }],
  service: [{ required: true, message: '请选择服务类型', trigger: 'change' }],
  apiUrl:  [{ required: true, message: '请输入 API 地址', trigger: 'blur' }]
}

const currentProviders = computed(() =>
  allProviders.value.filter(p => p.service === activeService.value)
)

const weightDistribution = computed(() => {
  const enabled = currentProviders.value.filter(p => p.enabled === 1)
  const total = enabled.reduce((s, p) => s + (p.weight || 1), 0)
  return enabled.map(p => `${p.name} ${Math.round((p.weight || 1) / total * 100)}%`).join(' / ')
})

const loadProviders = async () => {
  loading.value = true
  try {
    const res = await listAiProviders()
    allProviders.value = res.data?.data || res.data || []
  } catch {
    ElMessage.error('加载服务商列表失败')
  } finally {
    loading.value = false
  }
}

const onServiceChange = () => {
  testResults.value = {}
}

const openCreateDialog = () => {
  editingProvider.value = null
  showKey.value = false
  form.value = {
    name: '',
    service: activeService.value,
    apiUrl: '',
    apiKey: '',
    appId: '',
    model: '',
    weight: 10,
    timeoutMs: 30000,
    enabledBool: true,
    remark: ''
  }
  dialogVisible.value = true
}

const openEditDialog = (p) => {
  editingProvider.value = p
  showKey.value = false
  form.value = {
    name: p.name,
    service: p.service,
    apiUrl: p.apiUrl,
    apiKey: '',
    appId: p.appId || '',
    model: p.model || '',
    weight: p.weight ?? 10,
    timeoutMs: p.timeoutMs ?? 30000,
    enabledBool: p.enabled === 1,
    remark: p.remark || ''
  }
  dialogVisible.value = true
}

const handleSave = async () => {
  await formRef.value?.validate()
  saving.value = true
  try {
    const payload = {
      name:      form.value.name,
      service:   form.value.service,
      apiUrl:    form.value.apiUrl,
      appId:     form.value.appId || null,
      model:     form.value.model,
      weight:    form.value.weight,
      timeoutMs: form.value.timeoutMs,
      enabled:   form.value.enabledBool ? 1 : 0,
      remark:    form.value.remark
    }
    if (form.value.apiKey) payload.apiKey = form.value.apiKey

    if (editingProvider.value) {
      await updateAiProvider(editingProvider.value.id, payload)
      ElMessage.success('更新成功')
    } else {
      await createAiProvider(payload)
      ElMessage.success('创建成功')
    }
    dialogVisible.value = false
    await loadProviders()
  } catch (e) {
    if (e?.message) ElMessage.error(e.message)
  } finally {
    saving.value = false
  }
}

const toggleProvider = async (p, val) => {
  try {
    await toggleAiProvider(p.id, val ? 1 : 0)
    p.enabled = val ? 1 : 0
    ElMessage.success(val ? '已启用' : '已禁用')
  } catch {
    ElMessage.error('操作失败')
  }
}

const confirmDelete = async (p) => {
  try {
    await ElMessageBox.confirm(`确定删除服务商「${p.name}」？`, '确认删除', {
      type: 'warning',
      confirmButtonText: '删除',
      confirmButtonClass: 'el-button--danger'
    })
    await deleteAiProvider(p.id)
    ElMessage.success('已删除')
    await loadProviders()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error('删除失败')
  }
}

const openTestPanel = async (p) => {
  testingId.value = p.id
  testResults.value = { ...testResults.value, [p.id]: null }
  try {
    const res = await testAiProvider(p.id, 'Hello, world!')
    testResults.value = { ...testResults.value, [p.id]: res.data?.data || res.data }
  } catch {
    testResults.value = { ...testResults.value, [p.id]: { success: false, error: '请求失败' } }
  } finally {
    testingId.value = null
  }
}

onMounted(loadProviders)
</script>

<style scoped>
.ai-providers {
  max-width: 900px;
  margin: 0 auto;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.page-header h2 {
  margin: 0;
  font-size: 20px;
  color: var(--text-primary);
}

.toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin: 12px 0;
}

.provider-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.provider-card {
  transition: opacity 0.2s;
}

.provider-card.disabled {
  opacity: 0.55;
}

.provider-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.provider-name {
  display: flex;
  align-items: center;
}

.name-text {
  font-weight: 600;
  font-size: 15px;
}

.provider-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.provider-body {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 6px 24px;
}

.meta-row {
  display: flex;
  align-items: baseline;
  gap: 8px;
  font-size: 13px;
}

.meta-label {
  color: var(--el-text-color-secondary);
  white-space: nowrap;
  min-width: 56px;
}

.meta-value {
  color: var(--el-text-color-primary);
  word-break: break-all;
}

.meta-url {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.meta-key {
  font-family: monospace;
  font-size: 12px;
}

.weight-hint {
  font-size: 11px;
  color: var(--el-text-color-placeholder);
  margin-left: 4px;
}

.test-result {
  margin-top: 12px;
  padding: 8px 12px;
  border-radius: 6px;
  font-size: 13px;
  display: flex;
  gap: 12px;
  align-items: baseline;
}

.test-result.is-success {
  background: #f0f9eb;
  border: 1px solid #e1f3d8;
}

.test-result.is-error {
  background: #fef0f0;
  border: 1px solid #fde2e2;
}

.test-label {
  font-weight: 600;
  white-space: nowrap;
}

.test-result.is-success .test-label { color: #67c23a; }
.test-result.is-error   .test-label { color: #f56c6c; }

.test-detail {
  word-break: break-all;
  color: var(--el-text-color-regular);
}

.field-hint {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  margin-top: 4px;
}
</style>
