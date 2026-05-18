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
    <div v-loading="loading" class="provider-grid">
      <el-empty v-if="!loading && currentProviders.length === 0" description="暂无服务商，点击「添加服务商」创建" />

      <div
        v-for="p in currentProviders"
        :key="p.id"
        class="provider-glass-card"
        :class="{ disabled: p.enabled !== 1 }"
      >
        <div class="card-glow"></div>
        <div class="provider-header">
          <div class="provider-info-main">
            <div class="protocol-badge" :class="p.protocol">
              {{ p.protocol === 'hiagent' ? 'Hi' : 'AI' }}
            </div>
            <div class="name-area">
              <span class="name-text">{{ p.name }}</span>
              <span class="model-text">{{ p.model || '自动模型' }}</span>
            </div>
          </div>
          <div class="provider-status">
            <el-switch
              :model-value="p.enabled === 1"
              @change="val => toggleProvider(p, val)"
              size="small"
              inline-prompt
              active-text="ON"
              inactive-text="OFF"
            />
          </div>
        </div>

        <div class="provider-body">
          <div class="meta-item">
            <el-icon><Link /></el-icon>
            <span class="meta-val url-val">{{ p.apiUrl }}</span>
          </div>
          <div class="meta-item">
            <el-icon><Key /></el-icon>
            <span class="meta-val key-val">{{ p.apiKey }}</span>
          </div>
          <div class="meta-stats">
            <div class="stat-pill">
              <span class="stat-label">权重</span>
              <span class="stat-val">{{ p.weight }}</span>
            </div>
            <div class="stat-pill">
              <span class="stat-label">超时</span>
              <span class="stat-val">{{ p.timeoutMs }}ms</span>
            </div>
          </div>
        </div>

        <div class="provider-footer">
          <div class="footer-actions">
            <el-button link type="primary" @click="openTestPanel(p)" :loading="testingId === p.id">
              <el-icon><VideoPlay /></el-icon> 测试
            </el-button>
            <el-button link type="primary" @click="openEditDialog(p)">
              <el-icon><Edit /></el-icon> 编辑
            </el-button>
            <el-button link type="danger" @click="confirmDelete(p)">
              <el-icon><Delete /></el-icon> 删除
            </el-button>
          </div>
        </div>

        <!-- 内联测试结果 -->
        <transition name="el-zoom-in-top">
          <div v-if="testResults[p.id]" class="test-result-overlay" :class="testResults[p.id].success ? 'is-success' : 'is-error'">
            <div class="res-header">
              <el-icon><component :is="testResults[p.id].success ? 'CircleCheck' : 'CircleClose'" /></el-icon>
              <span>{{ testResults[p.id].success ? '测试通过' : '测试失败' }}</span>
              <el-icon class="close-res" @click="testResults[p.id] = null"><Close /></el-icon>
            </div>
            <div class="res-content">{{ testResults[p.id].success ? testResults[p.id].output : testResults[p.id].error }}</div>
          </div>
        </transition>
      </div>
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
import { Refresh, Plus, View, Hide, Check, Link, Key, VideoPlay, Edit, Delete, CircleCheck, CircleClose, Close } from '@element-plus/icons-vue'
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
  max-width: 1400px;
  margin: 0 auto;
  padding: 20px;
}

/* PC端大字号适配 */
@media (min-width: 1200px) {
  .ai-providers {
    padding: 30px;
  }
  
  .page-header h2 {
    font-size: 28px;
  }

  :deep(.el-tabs__item) {
    font-size: 16px;
    height: 48px;
  }

  .name-text {
    font-size: 18px;
  }

  .meta-item {
    font-size: 14px;
  }

  .stat-pill {
    font-size: 13px;
    padding: 6px 12px;
  }
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24px;
}

.hiagent-card {
  border-radius: 20px;
  border: 1px solid #e2e8f0;
  box-shadow: 0 4px 6px rgba(0, 0, 0, 0.02);
  margin-bottom: 32px;
}

.provider-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  gap: 20px;
  margin-top: 24px;
}

.provider-glass-card {
  background: white;
  border: 1px solid #e2e8f0;
  border-radius: 24px;
  padding: 20px;
  position: relative;
  overflow: hidden;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  display: flex;
  flex-direction: column;
}

@media (max-width: 480px) {
  .ai-providers {
    padding: 16px;
  }

  .provider-grid {
    grid-template-columns: 1fr;
    gap: 16px;
  }

  .provider-glass-card {
    padding: 16px;
  }

  .meta-stats {
    flex-direction: column;
    gap: 8px;
  }

  .stat-pill {
    width: 100%;
    justify-content: space-between;
  }
}

.provider-glass-card:hover {
  transform: translateY(-4px);
  border-color: #4f46e5;
  box-shadow: 0 20px 40px rgba(79, 70, 229, 0.08);
}

.card-glow {
  position: absolute;
  top: -50px;
  right: -50px;
  width: 100px;
  height: 100px;
  background: radial-gradient(circle, rgba(79, 70, 229, 0.1) 0%, transparent 70%);
  pointer-events: none;
}

.provider-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 20px;
}

.provider-info-main {
  display: flex;
  gap: 12px;
}

.protocol-badge {
  width: 40px;
  height: 40px;
  border-radius: 12px;
  background: #f1f5f9;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 800;
  font-size: 14px;
  color: #64748b;
}

.protocol-badge.hiagent {
  background: #eef2ff;
  color: #4f46e5;
}

.name-area {
  display: flex;
  flex-direction: column;
}

.name-text {
  font-weight: 700;
  font-size: 16px;
  color: #1e293b;
}

.model-text {
  font-size: 12px;
  color: #94a3b8;
  margin-top: 2px;
}

.provider-body {
  flex: 1;
}

.meta-item {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
  color: #64748b;
  font-size: 13px;
}

.meta-val {
  word-break: break-all;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.url-val { color: #94a3b8; font-size: 12px; }
.key-val { font-family: ui-monospace, monospace; }

.meta-stats {
  display: flex;
  gap: 12px;
  margin-top: 16px;
}

.stat-pill {
  background: #f8fafc;
  padding: 4px 10px;
  border-radius: 100px;
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
}

.stat-label { color: #94a3b8; }
.stat-val { font-weight: 700; color: #475569; }

.provider-footer {
  margin-top: 20px;
  padding-top: 16px;
  border-top: 1px solid #f1f5f9;
}

.footer-actions {
  display: flex;
  justify-content: space-between;
}

.test-result-overlay {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  background: rgba(255, 255, 255, 0.98);
  backdrop-filter: blur(4px);
  z-index: 10;
  padding: 24px;
  display: flex;
  flex-direction: column;
}

.res-header {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 700;
  margin-bottom: 12px;
}

.is-success .res-header { color: #10b981; }
.is-error .res-header { color: #ef4444; }

.close-res {
  margin-left: auto;
  cursor: pointer;
  color: #94a3b8;
}

.res-content {
  font-size: 13px;
  line-height: 1.6;
  color: #475569;
  overflow-y: auto;
  font-family: ui-monospace, monospace;
}

.provider-glass-card.disabled {
  opacity: 0.6;
  filter: grayscale(0.5);
}
</style>
