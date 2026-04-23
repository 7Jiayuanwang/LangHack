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

    <!-- HiAgent 专有协议配置卡片（改写 + 问答 两套凭证应用级隔离）-->
    <el-card class="hiagent-card" shadow="never">
      <template #header>
        <div class="hiagent-header">
          <div class="hiagent-title">
            <span class="title-text">HiAgent 专有协议配置</span>
            <el-tag size="small" type="warning">两套凭证必须完全不同，不 fallback</el-tag>
          </div>
          <div class="hiagent-header-actions">
            <el-button size="small" @click="loadHiAgentConfig" :loading="hiagentLoading">
              <el-icon><Refresh /></el-icon>
              重新载入
            </el-button>
          </div>
        </div>
      </template>

      <el-alert
        type="info"
        show-icon
        :closable="false"
        style="margin-bottom:12px"
      >
        <template #title>
          <strong>AI 改写</strong> (CET-4/6/学术版/中英对照) 使用 api_key + app_id；
          <strong>AI 问答</strong> (文章划词) 使用 qa_api_key + qa_app_id。
          保存后立即生效（读取自 system_config，60s 内存缓存刷新）。
        </template>
      </el-alert>

      <el-form :model="hiagentForm" label-width="130px" class="hiagent-form">
        <el-form-item label="API 地址">
          <el-input
            v-model="hiagentForm.hiagent_api_url"
            placeholder="如：https://agentbuilder.volces.com/api/v1"
          />
          <div class="field-hint">两套凭证共用的网关地址</div>
        </el-form-item>

        <el-divider content-position="left">
          <span class="divider-tag">改写凭证</span>
        </el-divider>

        <el-form-item label="改写 API Key">
          <el-input
            v-model="hiagentForm.hiagent_api_key"
            :type="showKeys.rewriteKey ? 'text' : 'password'"
            :placeholder="statusMap.hiagent_api_key_configured ? '留空保持原值不变' : '尚未配置，请输入改写 API Key'"
          >
            <template #append>
              <el-button @click="showKeys.rewriteKey = !showKeys.rewriteKey">
                <el-icon><View v-if="!showKeys.rewriteKey" /><Hide v-else /></el-icon>
              </el-button>
            </template>
          </el-input>
          <div class="field-hint">
            <el-tag size="small" :type="statusMap.hiagent_api_key_configured ? 'success' : 'danger'">
              {{ statusMap.hiagent_api_key_configured ? '已配置' : '未配置' }}
            </el-tag>
          </div>
        </el-form-item>

        <el-form-item label="改写 App ID">
          <el-input v-model="hiagentForm.hiagent_app_id" placeholder="改写 App ID（明文）" />
          <div class="field-hint">
            <el-tag size="small" :type="statusMap.hiagent_app_id_configured ? 'success' : 'danger'">
              {{ statusMap.hiagent_app_id_configured ? '已配置' : '未配置' }}
            </el-tag>
          </div>
        </el-form-item>

        <el-divider content-position="left">
          <span class="divider-tag">问答凭证（与改写必须不同）</span>
        </el-divider>

        <el-form-item label="问答 API Key">
          <el-input
            v-model="hiagentForm.hiagent_qa_api_key"
            :type="showKeys.qaKey ? 'text' : 'password'"
            :placeholder="statusMap.hiagent_qa_api_key_configured ? '留空保持原值不变' : '尚未配置，请输入问答 API Key'"
          >
            <template #append>
              <el-button @click="showKeys.qaKey = !showKeys.qaKey">
                <el-icon><View v-if="!showKeys.qaKey" /><Hide v-else /></el-icon>
              </el-button>
            </template>
          </el-input>
          <div class="field-hint">
            <el-tag size="small" :type="statusMap.hiagent_qa_api_key_configured ? 'success' : 'danger'">
              {{ statusMap.hiagent_qa_api_key_configured ? '已配置' : '未配置' }}
            </el-tag>
          </div>
        </el-form-item>

        <el-form-item label="问答 App ID">
          <el-input v-model="hiagentForm.hiagent_qa_app_id" placeholder="问答 App ID（明文）" />
          <div class="field-hint">
            <el-tag size="small" :type="statusMap.hiagent_qa_app_id_configured ? 'success' : 'danger'">
              {{ statusMap.hiagent_qa_app_id_configured ? '已配置' : '未配置' }}
            </el-tag>
          </div>
        </el-form-item>

        <el-form-item>
          <el-button type="primary" @click="saveHiAgentConfig" :loading="hiagentSaving">
            <el-icon><Check /></el-icon>
            保存配置
          </el-button>
          <el-button
            @click="runHiAgentTest('rewrite')"
            :loading="hiagentTesting.rewrite"
            :disabled="!statusMap.rewrite_ready_from_db"
          >
            测试改写
          </el-button>
          <el-button
            @click="runHiAgentTest('qa')"
            :loading="hiagentTesting.qa"
            :disabled="!statusMap.qa_ready_from_db"
          >
            测试问答
          </el-button>
        </el-form-item>
      </el-form>

      <!-- 改写测试结果 -->
      <div v-if="hiagentResults.rewrite" class="test-result" :class="hiagentResults.rewrite.success ? 'is-success' : 'is-error'">
        <span class="test-label">{{ hiagentResults.rewrite.success ? '✓ 改写测试通过' : '✗ 改写测试失败' }}</span>
        <span class="test-detail">{{ hiagentResults.rewrite.success ? hiagentResults.rewrite.preview : hiagentResults.rewrite.error }}</span>
      </div>
      <!-- 问答测试结果 -->
      <div v-if="hiagentResults.qa" class="test-result" :class="hiagentResults.qa.success ? 'is-success' : 'is-error'">
        <span class="test-label">{{ hiagentResults.qa.success ? '✓ 问答测试通过' : '✗ 问答测试失败' }}</span>
        <span class="test-detail">{{ hiagentResults.qa.success ? hiagentResults.qa.preview : hiagentResults.qa.error }}</span>
      </div>
    </el-card>

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
            <el-tag
              :type="p.protocol === 'hiagent' ? 'warning' : ''"
              size="small"
              style="margin-right:8px"
            >
              {{ p.protocol === 'hiagent' ? 'HiAgent' : 'OpenAI' }}
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
      <el-form :model="form" label-width="100px" ref="formRef" :rules="computedRules">
        <el-form-item label="名称" prop="name">
          <el-input v-model="form.name" placeholder="如：SiliconFlow-主" />
        </el-form-item>
        <el-form-item label="服务类型" prop="service">
          <el-select v-model="form.service" style="width:100%">
            <el-option v-for="svc in serviceTypes" :key="svc.value" :label="svc.label" :value="svc.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="协议类型" prop="protocol">
          <el-radio-group v-model="form.protocol" :disabled="!!editingProvider" @change="onProtocolChange">
            <el-radio value="openai_compatible">OpenAI 兼容格式</el-radio>
            <el-radio value="hiagent">HiAgent 专有协议</el-radio>
          </el-radio-group>
          <div class="field-hint" v-if="form.protocol === 'hiagent'">
            使用 create_conversation → chat_query_v2 两步调用，需配置 App ID
          </div>
          <div class="field-hint" v-else>
            标准 /v1/chat/completions 接口，Bearer Token 认证
          </div>
        </el-form-item>
        <el-form-item label="API 地址" prop="apiUrl">
          <el-input
            v-model="form.apiUrl"
            :placeholder="form.protocol === 'hiagent'
              ? 'https://agentbuilder.volces.com/api/v1'
              : 'https://api.siliconflow.cn/v1/chat/completions'"
          />
          <div class="field-hint" v-if="form.protocol === 'hiagent'">
            HiAgent 网关地址，不含 /create_conversation 等路径后缀
          </div>
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
          <div class="field-hint" v-if="form.protocol === 'hiagent'">
            HiAgent 请求头 Apikey 字段值
          </div>
        </el-form-item>
        <el-form-item v-if="form.protocol === 'hiagent'" label="App ID" prop="appId">
          <el-input v-model="form.appId" placeholder="HiAgent App ID（必填）" />
          <div class="field-hint">HiAgent 请求头 Appid / X-App-Id 字段值</div>
        </el-form-item>
        <el-form-item v-else label="App ID">
          <el-input v-model="form.appId" placeholder="仅极少数专有协议商需要，可留空" />
        </el-form-item>
        <el-form-item v-if="form.protocol !== 'hiagent'" label="模型" prop="model">
          <el-input v-model="form.model" placeholder="如：tencent/Hunyuan-MT-7B" />
        </el-form-item>
        <el-form-item v-else label="模型">
          <el-input v-model="form.model" placeholder="由 App 决定，可留空" />
          <div class="field-hint">HiAgent 协议下模型由 App 配置决定，此字段仅作备注</div>
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
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh, Plus, View, Hide, Check } from '@element-plus/icons-vue'
import {
  listAiProviders,
  createAiProvider,
  updateAiProvider,
  deleteAiProvider,
  toggleAiProvider,
  testAiProvider,
  getSystemConfig,
  updateSystemConfig,
  getHiAgentStatus,
  testHiAgentRewrite,
  testHiAgentQa
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
  protocol: 'openai_compatible',
  apiUrl: '',
  apiKey: '',
  appId: '',
  model: '',
  weight: 10,
  timeoutMs: 30000,
  enabledBool: true,
  remark: ''
})

const baseRules = {
  name:    [{ required: true, message: '请输入名称', trigger: 'blur' }],
  service: [{ required: true, message: '请选择服务类型', trigger: 'change' }],
  protocol:[{ required: true, message: '请选择协议类型', trigger: 'change' }],
  apiUrl:  [{ required: true, message: '请输入 API 地址', trigger: 'blur' }]
}

const computedRules = computed(() => {
  const r = { ...baseRules }
  if (form.value.protocol === 'hiagent') {
    r.appId = [{ required: true, message: 'HiAgent 协议必须填写 App ID', trigger: 'blur' }]
  }
  if (form.value.protocol !== 'hiagent') {
    r.model = [{ required: true, message: '请输入模型名称', trigger: 'blur' }]
  }
  return r
})

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
    protocol: 'openai_compatible',
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
    protocol: p.protocol || 'openai_compatible',
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

const onProtocolChange = (val) => {
  if (val === 'hiagent') {
    form.value.appId = form.value.appId || ''
  }
}

const handleSave = async () => {
  await formRef.value?.validate()
  saving.value = true
  try {
    const payload = {
      name:      form.value.name,
      service:   form.value.service,
      protocol:  form.value.protocol,
      apiUrl:    form.value.apiUrl,
      appId:     form.value.appId || null,
      model:     form.value.model || (form.value.protocol === 'hiagent' ? 'hiagent-app' : ''),
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

onMounted(() => {
  loadProviders()
  loadHiAgentConfig()
})

// ==================== HiAgent 凭证配置 ====================

const HIAGENT_KEYS = [
  'hiagent_api_url',
  'hiagent_api_key',
  'hiagent_app_id',
  'hiagent_qa_api_key',
  'hiagent_qa_app_id'
]
const HIAGENT_SECRET_KEYS = new Set(['hiagent_api_key', 'hiagent_qa_api_key'])

const hiagentLoading = ref(false)
const hiagentSaving  = ref(false)
const hiagentTesting = reactive({ rewrite: false, qa: false })
const hiagentResults = reactive({ rewrite: null, qa: null })
const showKeys       = reactive({ rewriteKey: false, qaKey: false })
const statusMap      = reactive({
  hiagent_api_url_configured:    false,
  hiagent_api_key_configured:    false,
  hiagent_app_id_configured:     false,
  hiagent_qa_api_key_configured: false,
  hiagent_qa_app_id_configured:  false,
  rewrite_ready_from_db:         false,
  qa_ready_from_db:              false
})

// 保存原始（脱敏）值，用于判断用户是否修改过密钥字段
const originalMaskedKeys = reactive({
  hiagent_api_key:    '',
  hiagent_qa_api_key: ''
})

const hiagentForm = reactive({
  hiagent_api_url:    '',
  hiagent_api_key:    '',
  hiagent_app_id:     '',
  hiagent_qa_api_key: '',
  hiagent_qa_app_id:  ''
})

const loadHiAgentConfig = async () => {
  hiagentLoading.value = true
  try {
    // 1) 拉 system_config（明文非密钥 + 脱敏密钥）
    const cfgRes = await getSystemConfig('ai_config')
    const configs = cfgRes.data?.data || cfgRes.data || []
    const byKey = {}
    configs.forEach(c => { byKey[c.configKey] = c })

    HIAGENT_KEYS.forEach(k => {
      const val = byKey[k]?.configValue || ''
      if (HIAGENT_SECRET_KEYS.has(k)) {
        hiagentForm[k] = val          // 脱敏值或空
        originalMaskedKeys[k] = val   // 记录原始脱敏值
      } else {
        hiagentForm[k] = val
      }
    })

    // 2) 拉 status（真实"是否已配置"判断）
    const statusRes = await getHiAgentStatus()
    const status = statusRes.data?.data || statusRes.data || {}
    Object.keys(statusMap).forEach(k => {
      if (k in status) statusMap[k] = !!status[k]
    })

    hiagentResults.rewrite = null
    hiagentResults.qa = null
  } catch (e) {
    ElMessage.error('加载 HiAgent 配置失败')
  } finally {
    hiagentLoading.value = false
  }
}

const saveHiAgentConfig = async () => {
  hiagentSaving.value = true
  try {
    const payload = []
    HIAGENT_KEYS.forEach(k => {
      const current = (hiagentForm[k] || '').trim()
      // 密钥字段：值未变（仍为脱敏原值）或为空，不回传，避免覆盖成脱敏串
      if (HIAGENT_SECRET_KEYS.has(k)) {
        if (!current) return
        if (current === originalMaskedKeys[k]) return
        if (current.includes('****')) return
      }
      payload.push({
        configGroup: 'ai_config',
        configKey:   k,
        configValue: current,
        isSecret:    HIAGENT_SECRET_KEYS.has(k) ? 1 : 0
      })
    })

    if (payload.length === 0) {
      ElMessage.info('未检测到修改，无需保存')
      return
    }

    await updateSystemConfig(payload)
    ElMessage.success('HiAgent 配置已保存，后端缓存将在 60s 内刷新')
    await loadHiAgentConfig()
  } catch (e) {
    if (e?.response?.data?.message) {
      ElMessage.error(e.response.data.message)
    } else {
      ElMessage.error('保存失败，请检查字段值')
    }
  } finally {
    hiagentSaving.value = false
  }
}

const runHiAgentTest = async (mode) => {
  hiagentTesting[mode] = true
  hiagentResults[mode] = null
  try {
    const res = mode === 'rewrite'
      ? await testHiAgentRewrite()
      : await testHiAgentQa()
    hiagentResults[mode] = res.data?.data || res.data || {}
  } catch (e) {
    hiagentResults[mode] = { success: false, error: e?.response?.data?.message || '请求失败' }
  } finally {
    hiagentTesting[mode] = false
  }
}
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

.hiagent-card {
  margin-bottom: 16px;
  border: 1px solid var(--el-border-color-light);
}

.hiagent-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
}

.hiagent-title {
  display: flex;
  align-items: center;
  gap: 8px;
}

.title-text {
  font-weight: 600;
  font-size: 15px;
}

.hiagent-form {
  max-width: 620px;
}

.divider-tag {
  font-weight: 600;
  font-size: 13px;
  color: var(--el-text-color-regular);
}
</style>
