<template>
  <div class="ai-providers">
    <div class="header">
      <h2>AI 服务管理</h2>
      <el-button @click="loadConfig" :loading="loading">
        <el-icon><Refresh /></el-icon>
        刷新
      </el-button>
    </div>

    <!-- 翻译服务配置 -->
    <el-card class="config-card">
      <template #header>
        <div class="card-header">
          <span class="card-title">翻译服务 (ai_translate)</span>
          <el-tag :type="isEnabled ? 'success' : 'info'" size="small">
            {{ isEnabled ? '已启用' : '已禁用' }}
          </el-tag>
        </div>
      </template>

      <el-form label-position="top" v-loading="loading">
        <el-form-item label="API 地址">
          <el-input v-model="form.api_url" placeholder="https://api.siliconflow.cn/v1/chat/completions" />
        </el-form-item>

        <el-form-item label="API Key">
          <el-input
            v-model="form.api_key"
            :type="showApiKey ? 'text' : 'password'"
            placeholder="输入新的 API Key（留空保持不变）"
          >
            <template #append>
              <el-button @click="showApiKey = !showApiKey">
                <el-icon><View v-if="!showApiKey" /><Hide v-else /></el-icon>
              </el-button>
            </template>
          </el-input>
          <div class="field-hint" v-if="maskedApiKey">当前: {{ maskedApiKey }}</div>
        </el-form-item>

        <el-form-item label="模型名称">
          <el-input v-model="form.model" placeholder="tencent/Hunyuan-MT-7B" />
        </el-form-item>

        <el-form-item label="超时时间 (ms)">
          <el-input-number v-model="form.timeout" :min="5000" :max="120000" :step="5000" />
        </el-form-item>

        <el-form-item label="启用">
          <el-switch v-model="form.enabled" />
        </el-form-item>

        <div class="form-actions">
          <el-button type="warning" @click="handleTest" :loading="testing">
            测试连接
          </el-button>
          <el-button type="primary" @click="handleSave" :loading="saving">
            保存配置
          </el-button>
        </div>
      </el-form>

      <!-- 测试结果 -->
      <div v-if="testResult" class="test-result" :class="{ success: testResult.success, error: !testResult.success }">
        <div class="test-label">{{ testResult.success ? '测试成功' : '测试失败' }}</div>
        <div v-if="testResult.success" class="test-detail">
          <div>输入: {{ testResult.input }}</div>
          <div>输出: {{ testResult.output }}</div>
        </div>
        <div v-else class="test-detail">
          <div>错误: {{ testResult.error }}</div>
        </div>
      </div>
    </el-card>

    <!-- 预留配置分组 -->
    <el-card class="config-card placeholder-card">
      <template #header>
        <div class="card-header">
          <span class="card-title">文章生成 (ai_article)</span>
          <el-tag type="info" size="small">待接入</el-tag>
        </div>
      </template>
      <div class="placeholder-text">该功能使用 OpenRouter 服务，已在 application.yml 中配置</div>
    </el-card>

    <el-card class="config-card placeholder-card">
      <template #header>
        <div class="card-header">
          <span class="card-title">AI 对话 (ai_chat)</span>
          <el-tag type="info" size="small">待接入</el-tag>
        </div>
      </template>
      <div class="placeholder-text">该功能使用 HiAgent 服务，已在 application.yml 中配置</div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh, View, Hide } from '@element-plus/icons-vue'
import { getSystemConfig, updateSystemConfig, testTranslate } from '@/api/systemConfig'

const loading = ref(false)
const saving = ref(false)
const testing = ref(false)
const showApiKey = ref(false)
const testResult = ref(null)
const maskedApiKey = ref('')

const form = ref({
  api_url: '',
  api_key: '',
  model: '',
  timeout: 30000,
  enabled: true
})

// Original values from server (to detect what changed)
const originalValues = ref({})

const isEnabled = computed(() => form.value.enabled)

const loadConfig = async () => {
  loading.value = true
  try {
    const res = await getSystemConfig('ai_translate')
    const configs = res.data?.data || res.data || []
    if (!Array.isArray(configs)) return

    const configMap = {}
    for (const item of configs) {
      configMap[item.configKey] = item
    }

    form.value.api_url = configMap.api_url?.configValue || ''
    form.value.model = configMap.model?.configValue || ''
    form.value.timeout = parseInt(configMap.timeout?.configValue || '30000', 10)
    form.value.enabled = configMap.enabled?.configValue !== 'false'

    // Store masked API key for display, don't fill the input
    maskedApiKey.value = configMap.api_key?.configValue || ''
    form.value.api_key = ''

    // Store original values
    originalValues.value = {
      api_url: form.value.api_url,
      model: form.value.model,
      timeout: form.value.timeout,
      enabled: form.value.enabled
    }
  } catch (e) {
    ElMessage.error('加载配置失败')
  } finally {
    loading.value = false
  }
}

const handleSave = async () => {
  saving.value = true
  try {
    const configs = [
      { configGroup: 'ai_translate', configKey: 'api_url', configValue: form.value.api_url },
      { configGroup: 'ai_translate', configKey: 'model', configValue: form.value.model },
      { configGroup: 'ai_translate', configKey: 'timeout', configValue: String(form.value.timeout) },
      { configGroup: 'ai_translate', configKey: 'enabled', configValue: String(form.value.enabled) }
    ]

    // Only update api_key if user actually entered a new value
    if (form.value.api_key) {
      configs.push({ configGroup: 'ai_translate', configKey: 'api_key', configValue: form.value.api_key })
    }

    await updateSystemConfig(configs)
    ElMessage.success('配置保存成功')
    await loadConfig()
  } catch (e) {
    ElMessage.error('保存配置失败')
  } finally {
    saving.value = false
  }
}

const handleTest = async () => {
  // 先保存当前配置，确保 DB 中是最新值
  try {
    await handleSave()
  } catch {
    testResult.value = { success: false, error: '保存配置失败，无法测试' }
    return
  }

  testing.value = true
  testResult.value = null
  try {
    const res = await testTranslate('Hello, world!')
    const data = res.data?.data || res.data
    testResult.value = data
  } catch (e) {
    testResult.value = { success: false, error: '请求失败' }
  } finally {
    testing.value = false
  }
}

onMounted(() => {
  loadConfig()
})
</script>

<style scoped>
.ai-providers {
  max-width: 800px;
  margin: 0 auto;
}

.header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.header h2 {
  margin: 0;
  font-size: 20px;
  color: var(--text-primary);
}

.config-card {
  margin-bottom: 16px;
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.card-title {
  font-weight: 600;
  font-size: 16px;
}

.field-hint {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  margin-top: 4px;
}

.form-actions {
  display: flex;
  gap: 12px;
  justify-content: flex-end;
  margin-top: 20px;
  padding-top: 16px;
  border-top: 1px solid var(--el-border-color-lighter);
}

.test-result {
  margin-top: 16px;
  padding: 12px 16px;
  border-radius: 8px;
  font-size: 14px;
}

.test-result.success {
  background: #f0f9eb;
  border: 1px solid #e1f3d8;
}

.test-result.error {
  background: #fef0f0;
  border: 1px solid #fde2e2;
}

.test-label {
  font-weight: 600;
  margin-bottom: 8px;
}

.test-result.success .test-label {
  color: #67c23a;
}

.test-result.error .test-label {
  color: #f56c6c;
}

.test-detail {
  color: var(--el-text-color-regular);
  line-height: 1.6;
  word-break: break-all;
}

.placeholder-card :deep(.el-card__body) {
  padding: 20px;
}

.placeholder-text {
  color: var(--el-text-color-secondary);
  font-size: 14px;
  text-align: center;
  padding: 16px 0;
}
</style>
