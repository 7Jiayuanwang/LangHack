<template>
  <div class="school-data-config">
    <div class="page-header">
      <h2>学校数据配置</h2>
      <el-button @click="loadConfig" :loading="loading">
        <el-icon><Refresh /></el-icon> 刷新
      </el-button>
    </div>

    <div v-if="loading" class="loading-state">
      <el-icon class="is-loading" :size="32"><Loading /></el-icon>
      <p>加载中...</p>
    </div>

    <template v-else>
      <el-collapse v-model="activePanels" class="config-collapse">

        <!-- Panel 1: 数据源配置 -->
        <el-collapse-item title="数据源配置" name="source">
          <el-form label-width="130px" size="default">
            <el-form-item label="数据源类型">
              <el-select v-model="form.source" style="width: 200px">
                <el-option label="API 接口" value="api" />
                <el-option label="ETL 数据库" value="etl" />
              </el-select>
              <span class="form-hint">切换后保存即生效，无需重启</span>
            </el-form-item>
          </el-form>

          <!-- API 模式配置 -->
          <template v-if="form.source === 'api'">
            <el-divider content-position="left">API 接口配置</el-divider>
            <el-form label-width="130px" size="default" :model="form" :rules="apiRules" ref="apiFormRef">
              <el-form-item label="Base URL" prop="api_base_url">
                <el-input v-model="form.api_base_url" placeholder="https://api.imau.edu.cn" />
              </el-form-item>
              <el-form-item label="App ID">
                <el-input v-model="form.api_app_id" placeholder="学校数据平台分配的应用 ID" />
              </el-form-item>
              <el-form-item label="App Secret">
                <el-input v-model="form.api_app_secret" type="password" show-password placeholder="应用 Secret (用于签名认证)" />
                <div class="form-subhint">
                  签名公式: sign = Base64(MD5(appId + appSecret + timestamp))
                </div>
              </el-form-item>
              <el-form-item label="连接超时(ms)">
                <el-input-number v-model="form.api_connect_timeout" :min="1000" :max="60000" :step="1000" />
              </el-form-item>
              <el-form-item label="读取超时(ms)">
                <el-input-number v-model="form.api_read_timeout" :min="5000" :max="120000" :step="5000" />
              </el-form-item>
              <el-form-item label="重试次数">
                <el-input-number v-model="form.api_retry_attempts" :min="0" :max="10" />
              </el-form-item>
            </el-form>
          </template>

          <!-- ETL 模式配置 -->
          <template v-if="form.source === 'etl'">
            <el-divider content-position="left">ETL 数据库配置</el-divider>
            <el-form label-width="130px" size="default" :model="form" :rules="etlRules" ref="etlFormRef">
              <el-form-item label="JDBC URL" prop="etl_jdbc_url">
                <el-input v-model="form.etl_jdbc_url" placeholder="jdbc:mysql://host:3306/school_db" />
              </el-form-item>
              <el-form-item label="用户名">
                <el-input v-model="form.etl_username" placeholder="数据库用户名" />
              </el-form-item>
              <el-form-item label="密码">
                <el-input v-model="form.etl_password" type="password" show-password placeholder="数据库密码" />
              </el-form-item>
              <el-form-item label="连接超时(ms)">
                <el-input-number v-model="form.etl_connection_timeout" :min="5000" :max="120000" :step="5000" />
              </el-form-item>
              <el-form-item label="查询超时(ms)">
                <el-input-number v-model="form.etl_query_timeout" :min="10000" :max="300000" :step="10000" />
              </el-form-item>
            </el-form>
          </template>
        </el-collapse-item>

        <!-- Panel 2: 同步调度 -->
        <el-collapse-item title="同步调度" name="sync">
          <el-form label-width="130px" size="default">
            <el-form-item label="启用同步">
              <el-switch
                v-model="form.sync_enabled"
                active-text="启用"
                inactive-text="关闭"
              />
            </el-form-item>
            <el-form-item label="全量同步 Cron">
              <el-input v-model="form.sync_full_cron" placeholder="0 0 2 * * ?">
                <template #append>
                  <el-tooltip content="每天凌晨2点执行全量同步" placement="top">
                    <el-icon><QuestionFilled /></el-icon>
                  </el-tooltip>
                </template>
              </el-input>
            </el-form-item>
            <el-form-item label="增量同步 Cron">
              <el-input v-model="form.sync_incremental_cron" placeholder="0 0 * * * ?">
                <template #append>
                  <el-tooltip content="每小时执行增量同步" placement="top">
                    <el-icon><QuestionFilled /></el-icon>
                  </el-tooltip>
                </template>
              </el-input>
            </el-form-item>
            <el-form-item label="数据校验 Cron">
              <el-input v-model="form.sync_validation_cron" placeholder="0 0 4 * * ?">
                <template #append>
                  <el-tooltip content="每天凌晨4点执行数据校验" placement="top">
                    <el-icon><QuestionFilled /></el-icon>
                  </el-tooltip>
                </template>
              </el-input>
            </el-form-item>
            <el-form-item label="批量大小">
              <el-input-number v-model="form.sync_batch_size" :min="100" :max="5000" :step="100" />
            </el-form-item>
            <el-form-item label="超时(分钟)">
              <el-input-number v-model="form.sync_timeout_minutes" :min="5" :max="480" :step="5" />
            </el-form-item>
          </el-form>
          <div class="cron-hint">
            <el-icon><InfoFilled /></el-icon>
            <span>Cron 格式: 秒 分 时 日 月 周 (如 <code>0 0 2 * * ?</code> = 每天2:00)</span>
          </div>
        </el-collapse-item>

        <!-- Panel 3: 操作 -->
        <el-collapse-item title="操作" name="actions">
          <div class="action-row">
            <el-button
              type="warning"
              :loading="testing"
              @click="handleTestConnection"
            >
              <el-icon><Connection /></el-icon> 测试连接
            </el-button>
            <el-button
              type="primary"
              :loading="saving"
              @click="handleSave"
            >
              <el-icon><Check /></el-icon> 保存配置
            </el-button>
          </div>
          <div v-if="testResult !== null" class="test-result" :class="testResult ? 'test-success' : 'test-fail'">
            <el-icon><component :is="testResult ? 'CircleCheckFilled' : 'CircleCloseFilled'" /></el-icon>
            <span>连接{{ testResult ? '成功' : '失败' }}<template v-if="testSource"> ({{ testSource }})</template></span>
          </div>
        </el-collapse-item>

      </el-collapse>
    </template>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import {
  Refresh, Loading, QuestionFilled, InfoFilled,
  Connection, Check, CircleCheckFilled, CircleCloseFilled
} from '@element-plus/icons-vue'
import {
  getSchoolDataConfig,
  updateSchoolDataConfig,
  testSchoolDataConnection
} from '@/api/schoolData'

const loading = ref(false)
const saving = ref(false)
const testing = ref(false)
const testResult = ref(null)
const testSource = ref('')
const activePanels = ref(['source', 'sync', 'actions'])
const apiFormRef = ref(null)
const etlFormRef = ref(null)

const apiRules = {
  api_base_url: [
    { required: true, message: '请输入API Base URL', trigger: 'blur' },
    { pattern: /^https?:\/\/.+/, message: 'URL必须以http://或https://开头', trigger: 'blur' }
  ]
}

const etlRules = {
  etl_jdbc_url: [
    { required: true, message: '请输入JDBC URL', trigger: 'blur' },
    { pattern: /^jdbc:mysql:\/\/.+/, message: 'JDBC URL必须以jdbc:mysql://开头', trigger: 'blur' }
  ]
}

const form = reactive({
  source: 'api',
  api_base_url: '',
  api_app_id: '',
  api_app_secret: '',
  api_connect_timeout: 5000,
  api_read_timeout: 30000,
  api_retry_attempts: 3,
  etl_jdbc_url: '',
  etl_username: '',
  etl_password: '',
  etl_connection_timeout: 30000,
  etl_query_timeout: 60000,
  sync_enabled: false,
  sync_full_cron: '0 0 2 * * ?',
  sync_incremental_cron: '0 0 * * * ?',
  sync_validation_cron: '0 0 4 * * ?',
  sync_batch_size: 500,
  sync_timeout_minutes: 60
})

// 原始配置列表（用于获取 id 等字段）
const rawConfigs = ref([])

const loadConfig = async () => {
  loading.value = true
  try {
    const res = await getSchoolDataConfig()
    if (res?.code === 200 && Array.isArray(res.data)) {
      rawConfigs.value = res.data
      for (const item of res.data) {
        const key = item.configKey
        if (key in form) {
          // 数值型字段
          if (['api_connect_timeout', 'api_read_timeout', 'api_retry_attempts',
               'etl_connection_timeout', 'etl_query_timeout',
               'sync_batch_size', 'sync_timeout_minutes'].includes(key)) {
            form[key] = parseInt(item.configValue) || 0
          } else if (key === 'sync_enabled') {
            form[key] = item.configValue === 'true' || item.configValue === '1'
          } else {
            form[key] = item.configValue || ''
          }
        }
      }
    }
  } catch {
    ElMessage.error('加载配置失败')
  } finally {
    loading.value = false
  }
}

const handleSave = async () => {
  // 表单验证
  if (form.source === 'api' && apiFormRef.value) {
    try { await apiFormRef.value.validate() } catch { return }
  }
  if (form.source === 'etl' && etlFormRef.value) {
    try { await etlFormRef.value.validate() } catch { return }
  }

  saving.value = true
  try {
    // 构建 config 列表，保留原始 id/configGroup/isSecret
    const configs = rawConfigs.value.map(item => {
      const key = item.configKey
      let value
      if (key === 'sync_enabled') {
        value = form[key] ? 'true' : 'false'
      } else if (['api_connect_timeout', 'api_read_timeout', 'api_retry_attempts',
                   'etl_connection_timeout', 'etl_query_timeout',
                   'sync_batch_size', 'sync_timeout_minutes'].includes(key)) {
        value = String(form[key])
      } else {
        value = String(form[key] ?? '')
      }
      return {
        ...item,
        configValue: value
      }
    })
    const res = await updateSchoolDataConfig(configs)
    if (res?.code === 200) {
      ElMessage.success('配置已保存')
      await loadConfig()
    } else {
      ElMessage.error(res?.message || '保存失败')
    }
  } catch {
    ElMessage.error('保存配置失败')
  } finally {
    saving.value = false
  }
}

const handleTestConnection = async () => {
  testing.value = true
  testResult.value = null
  testSource.value = ''
  try {
    const res = await testSchoolDataConnection()
    if (res?.code === 200 && res.data) {
      testResult.value = res.data.success
      testSource.value = res.data.source || ''
    } else {
      testResult.value = false
    }
  } catch {
    testResult.value = false
  } finally {
    testing.value = false
  }
}

onMounted(loadConfig)
</script>

<style scoped>
.school-data-config {
  max-width: 900px;
  margin: 0 auto;
  padding: 24px;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.page-header h2 {
  margin: 0;
  font-size: 20px;
  font-weight: 700;
}

.loading-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 32px;
  color: #94a3b8;
}

.config-collapse {
  background: #fff;
  border-radius: 12px;
  border: 1px solid #e2e8f0;
}

.form-hint {
  margin-left: 12px;
  font-size: 12px;
  color: #94a3b8;
}

.form-subhint {
  margin-top: 4px;
  font-size: 12px;
  color: #64748b;
  font-family: ui-monospace, monospace;
  line-height: 1.4;
}

.cron-hint {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 10px 14px;
  background: #f0f9ff;
  border-radius: 8px;
  font-size: 13px;
  color: #0369a1;
  margin-top: 8px;
}

.cron-hint code {
  background: #e0f2fe;
  padding: 1px 5px;
  border-radius: 3px;
  font-size: 12px;
}

.action-row {
  display: flex;
  gap: 12px;
  padding: 4px 0;
}

.test-result {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 12px;
  padding: 10px 14px;
  border-radius: 8px;
  font-size: 14px;
  font-weight: 500;
}

.test-success {
  background: #f0fdf4;
  color: #16a34a;
}

.test-fail {
  background: #fef2f2;
  color: #dc2626;
}
</style>
