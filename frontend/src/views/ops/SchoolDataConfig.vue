<template>
  <div class="school-data-config">
    <div class="page-header">
      <div class="header-left">
        <h2>学校数据集成配置</h2>
        <p class="subtitle">管理学校数据源 API 或 ETL 数据库连接与同步任务</p>
      </div>
      <el-button @click="loadAll" :loading="loading" plain round>
        <el-icon><Refresh /></el-icon> 刷新状态
      </el-button>
    </div>

    <!-- 配置状态警告横幅 -->
    <div class="banners-area">
      <el-alert
        v-if="showAuthWarning"
        type="error"
        :closable="false"
        show-icon
        class="status-banner premium-alert"
      >
        <template #title>
          <span class="banner-title">学校 API 认证未配置</span>
        </template>
        <template #default>
          <div class="banner-content">
            <p>数据同步当前全部失败。缺失项:
              <el-tag
                v-for="k in status.missingKeys"
                :key="k"
                type="danger"
                size="small"
                effect="dark"
                class="missing-tag"
              >{{ k }}</el-tag>
            </p>
            <div class="banner-hint">
              请在下方 "API 接口配置" 区填入 <strong>App ID</strong> 与 <strong>App Secret</strong>，保存后点击"测试连接"验证。
            </div>
          </div>
        </template>
      </el-alert>
      <el-alert
        v-else-if="showEtlWarning"
        type="error"
        :closable="false"
        show-icon
        class="status-banner premium-alert"
      >
        <template #title>
          <span class="banner-title">ETL 数据库配置未完成</span>
        </template>
        <template #default>
          <div class="banner-content">
            <p>JDBC 直连当前不可用。缺失项:
              <el-tag
                v-for="k in status.missingKeys"
                :key="k"
                type="danger"
                size="small"
                effect="dark"
                class="missing-tag"
              >{{ k }}</el-tag>
            </p>
          </div>
        </template>
      </el-alert>
      <el-alert
        v-else-if="status.authConfigured || status.etlConfigured"
        type="success"
        :closable="false"
        show-icon
        class="status-banner premium-alert success-alert"
      >
        <template #title>
          <span class="banner-title">{{ status.source === 'api' ? 'REST API' : 'JDBC ETL' }} 模式已就绪</span>
        </template>
        <template #default>
          <p>集成配置已完成，同步服务正常运行中。您可以点击下方按钮测试实时连接。</p>
        </template>
      </el-alert>
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

            <!-- 只读的数据源端点列表（9 个 URL 由代码内置，不可编辑） -->
            <div class="endpoints-panel">
              <div class="endpoints-title">
                <el-icon><InfoFilled /></el-icon>
                <span>数据源端点（代码内置，不可编辑）</span>
                <el-tag size="small" type="info">共 {{ status.endpoints?.length || 0 }} 个</el-tag>
              </div>
              <el-table
                :data="status.endpoints || []"
                size="small"
                class="endpoints-table"
                :show-header="true"
                stripe
              >
                <el-table-column prop="dataSource" label="数据源" width="180">
                  <template #default="{ row }">
                    <code class="ds-name">{{ row.dataSource }}</code>
                  </template>
                </el-table-column>
                <el-table-column prop="url" label="完整 URL">
                  <template #default="{ row }">
                    <span class="ds-url">{{ row.url }}</span>
                  </template>
                </el-table-column>
              </el-table>
              <div class="endpoints-hint">
                每个数据源使用独立完整 URL，无需配置 Base URL。如需变更需在 <code>SchoolDataProperties.java</code> 中修改。
              </div>
            </div>

            <el-form label-width="130px" size="default" :model="form" :rules="apiRules" ref="apiFormRef">
              <el-form-item label="App ID" :class="{ 'field-missing': isMissing('api_app_id') }">
                <el-input v-model="form.api_app_id" placeholder="学校数据平台分配的应用 ID" />
                <div v-if="isMissing('api_app_id')" class="field-warning">必填 — 当前未配置，同步将失败</div>
              </el-form-item>
              <el-form-item label="App Secret" :class="{ 'field-missing': isMissing('api_app_secret') }">
                <el-input v-model="form.api_app_secret" type="password" show-password placeholder="应用 Secret (用于签名认证)" />
                <div class="form-subhint">
                  签名公式: sign = Base64(hex(MD5(appId + appSecret + timestamp)).getBytes(UTF-8))
                </div>
                <div v-if="isMissing('api_app_secret')" class="field-warning">必填 — 当前未配置，同步将失败</div>
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
              @click="router.push('/ops/school-data-browser')"
            >
              前往高级浏览/同步
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

          <div class="quick-actions-panel">
            <div class="quick-actions-header">
              <strong>快速获取学校数据</strong>
              <span>浏览分页限制不影响这里的同步拉取。</span>
            </div>

            <el-alert
              v-if="!canRunQuickActions"
              type="warning"
              :closable="false"
              show-icon
              class="quick-actions-alert"
              title="请先保存配置并测试连接成功后，再执行快速同步。"
            />

            <div class="quick-actions-grid">
              <div class="quick-card">
                <div class="quick-card-title">单数据源快速同步</div>
                <div class="quick-card-form">
                  <el-select v-model="quickSyncForm.dataSource" size="small" style="width: 220px">
                    <el-option
                      v-for="item in QUICK_DATA_SOURCES"
                      :key="item.value"
                      :label="item.label"
                      :value="item.value"
                    />
                  </el-select>
                  <el-radio-group v-model="quickSyncForm.mode" size="small">
                    <el-radio-button label="incremental">增量</el-radio-button>
                    <el-radio-button label="full">全量</el-radio-button>
                  </el-radio-group>
                  <el-button type="primary" size="small" :loading="quickSyncing" @click="handleQuickSync">
                    同步当前数据源
                  </el-button>
                </div>
                <div class="quick-card-actions">
                  <el-button size="small" :loading="quickSyncing" @click="handleQuickSyncAll('incremental')">增量同步全部</el-button>
                  <el-button size="small" :loading="quickSyncing" @click="handleQuickSyncAll('full')">全量同步全部</el-button>
                </div>
              </div>

              <div class="quick-card">
                <div class="quick-card-title">按学院快速拉取</div>
                <div class="quick-card-form">
                  <el-input v-model="chainFetchForm.collegeName" size="small" placeholder="学院名称（如：外国语学院）" style="width: 220px" />
                  <el-select v-model="chainFetchForm.matchMode" size="small" style="width: 120px">
                    <el-option value="eq" label="精确匹配" />
                    <el-option value="contains" label="模糊匹配" />
                  </el-select>
                  <el-button type="primary" size="small" :loading="chainFetching" :disabled="!chainFetchEnabled" @click="handleQuickChainFetch">
                    执行链式拉取
                  </el-button>
                </div>
              </div>
            </div>

            <div v-if="quickResult" class="quick-result-card">
              <div class="quick-result-title">同步结果摘要</div>
              <div class="quick-result-grid">
                <div><span>范围：</span>{{ quickResult.scopeLabel }}</div>
                <div><span>模式：</span>{{ quickResult.mode === 'full' ? '全量' : '增量' }}</div>
                <div><span>状态：</span>{{ quickResult.status }}</div>
                <div><span>完整性：</span>{{ quickResult.paginationComplete === true ? '完整' : quickResult.paginationComplete === false ? '可能截断' : '未知' }}</div>
                <div><span>总数：</span>{{ quickResult.totalCount }}</div>
                <div><span>成功：</span>{{ quickResult.successCount }}</div>
                <div><span>失败：</span>{{ quickResult.failCount }}</div>
                <div><span>跳过：</span>{{ quickResult.skipCount }}</div>
                <div v-if="quickResult.fetchedRowCount !== null"><span>上游拉取：</span>{{ quickResult.fetchedRowCount }}</div>
                <div v-if="quickResult.paginationPagesFetched !== null"><span>拉取页数：</span>{{ quickResult.paginationPagesFetched }}</div>
                <div v-if="quickResult.paginationLastSkip !== null"><span>最后 skip：</span>{{ quickResult.paginationLastSkip }}</div>
                <div v-if="quickResult.paginationPageSize !== null"><span>pageSize：</span>{{ quickResult.paginationPageSize }}</div>
                <div v-if="quickResult.paginationStopReason"><span>停止原因：</span>{{ quickResult.paginationStopReason }}</div>
                <div v-if="quickResult.durationMs !== null"><span>耗时：</span>{{ quickResult.durationMs }} ms</div>
              </div>
              <div v-if="quickResult.errorMessage" class="quick-result-error">{{ quickResult.errorMessage }}</div>
            </div>

            <div v-if="chainResult" class="quick-result-card">
              <div class="quick-result-title">链式拉取结果</div>
              <div class="quick-result-grid">
                <div><span>状态：</span>{{ chainStatusText(chainResult.status) }}</div>
                <div><span>学院：</span>{{ chainResult.collegeName }}</div>
                <div><span>匹配：</span>{{ chainResult.matchMode }}</div>
                <div><span>课程数：</span>{{ chainResult.courseCount || 0 }}</div>
                <div><span>学生数：</span>{{ chainResult.studentCount || 0 }}</div>
                <div><span>教师数：</span>{{ chainResult.teacherCount || 0 }}</div>
                <div><span>绑定数：</span>{{ chainResult.bindingCount || 0 }}</div>
                <div v-if="chainResult.upstreamKcbhSupported !== undefined"><span>KCBH支持：</span>{{ chainResult.upstreamKcbhSupported ? '是' : '否（客户端过滤）' }}</div>
              </div>
              <div v-if="chainResult.errorMessage" class="quick-result-error">{{ chainResult.errorMessage }}</div>
              <div v-if="chainErrors(chainResult).length > 0" class="quick-result-issues">
                <div class="quick-result-issues-title">错误</div>
                <div v-for="item in chainErrors(chainResult)" :key="`error-${item}`" class="quick-result-issue-item">{{ item }}</div>
              </div>
              <div v-if="chainWarnings(chainResult).length > 0" class="quick-result-issues warnings">
                <div class="quick-result-issues-title">警告</div>
                <div v-for="item in chainWarnings(chainResult)" :key="`warning-${item}`" class="quick-result-issue-item">{{ item }}</div>
              </div>
              <div v-if="chainStepEntries(chainResult).length > 0" class="quick-result-steps">
                <div v-for="[stepName, stats] in chainStepEntries(chainResult)" :key="stepName" class="quick-result-step">
                  <strong>{{ chainStepLabel(stepName) }}</strong>
                  <span>{{ formatChainStepStats(stats) }}</span>
                </div>
              </div>
              <div v-if="chainResult.status !== 'FAILED'" class="quick-result-actions">
                <el-button size="small" @click="openChainResultInBrowser">查看本地数据</el-button>
              </div>
            </div>
          </div>
        </el-collapse-item>

      </el-collapse>
    </template>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useFeatureToggles } from '@/composables/useFeatureToggles'
import {
  Refresh, Loading, QuestionFilled, InfoFilled,
  Connection, Check, CircleCheckFilled, CircleCloseFilled
} from '@element-plus/icons-vue'
import {
  getSchoolDataConfig,
  updateSchoolDataConfig,
  testSchoolDataConnection,
  getSchoolDataConfigStatus,
  fullSyncSchoolDataFiltered,
  incrementalSyncSchoolDataFiltered,
  fullSyncAllSchoolData,
  incrementalSyncAllSchoolData,
  chainFetchStudentsByCollege
} from '@/api/schoolData'

const QUICK_DATA_SOURCES = [
  { label: '行政班', value: 'administrative_class' },
  { label: '课程', value: 'school_course' },
  { label: '教学班 / 教师授课', value: 'teaching_class' },
  { label: '课程安排', value: 'course_schedule' },
  { label: '选课记录', value: 'course_selection' },
  { label: '等级考试', value: 'level_exam_scores' },
  { label: '教学计划', value: 'teaching_plans' },
  { label: '学生课表', value: 'student_schedule' }
]

const loading = ref(false)
const router = useRouter()
const saving = ref(false)
const testing = ref(false)
const testResult = ref(null)
const testSource = ref('')
const activePanels = ref(['source', 'sync', 'actions'])
const apiFormRef = ref(null)
const etlFormRef = ref(null)

const status = reactive({
  source: 'api',
  apiConfigured: false,
  authConfigured: false,
  etlConfigured: false,
  syncEnabled: false,
  missingKeys: [],
  endpoints: [],
  lastTestResult: null
})

const showAuthWarning = computed(
  () => status.source === 'api' && !status.authConfigured
)
const showEtlWarning = computed(
  () => status.source === 'etl' && !status.etlConfigured
)

const isMissing = (key) => Array.isArray(status.missingKeys) && status.missingKeys.includes(key)

const apiRules = {}

const etlRules = {
  etl_jdbc_url: [
    { required: true, message: '请输入JDBC URL', trigger: 'blur' },
    { pattern: /^jdbc:mysql:\/\/.+/, message: 'JDBC URL必须以jdbc:mysql://开头', trigger: 'blur' }
  ]
}

const form = reactive({
  source: 'api',
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

const quickSyncing = ref(false)
const chainFetching = ref(false)
const quickSyncForm = reactive({
  dataSource: 'school_course',
  mode: 'incremental'
})
const chainFetchForm = reactive({
  collegeName: '',
  matchMode: 'eq'
})
const quickResult = ref(null)
const chainResult = ref(null)
const { isEnabled: isFeatureEnabled, load: loadFeatureToggles } = useFeatureToggles()
const chainFetchEnabled = computed(() => isFeatureEnabled('ops.school_data_chain_fetch'))

const CHAIN_STATUS_LABELS = {
  SUCCESS: '成功',
  PARTIAL: '部分异常',
  FAILED: '失败'
}

const CHAIN_STEP_LABELS = {
  schoolCourse: '课程阶段',
  teacherCourses: '授课阶段',
  courseSelection: '选课阶段',
  persist: '落库阶段'
}

const canRunQuickActions = computed(() => {
  if (form.source === 'api') {
    return status.authConfigured
  }
  return status.etlConfigured
})

// 不参与前端表单的配置 key（由代码/系统维护，避免保存时被空值覆盖）
const READ_ONLY_KEYS = ['api_base_url']

// 原始配置列表（用于获取 id 等字段）
const rawConfigs = ref([])

const loadStatus = async () => {
  try {
    const res = await getSchoolDataConfigStatus()
    if (res?.code === 200 && res.data) {
      Object.assign(status, res.data)
    }
  } catch {
    // 静默降级，banner 不显示
  }
}

const loadConfig = async () => {
  loading.value = true
  try {
    const res = await getSchoolDataConfig()
    if (res?.code === 200 && Array.isArray(res.data)) {
      rawConfigs.value = res.data
      for (const item of res.data) {
        const key = item.configKey
        if (key in form) {
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

const loadAll = async () => {
  await Promise.all([loadConfig(), loadStatus()])
}

const handleSave = async () => {
  if (form.source === 'api' && apiFormRef.value) {
    try { await apiFormRef.value.validate() } catch { return }
  }
  if (form.source === 'etl' && etlFormRef.value) {
    try { await etlFormRef.value.validate() } catch { return }
  }

  saving.value = true
  try {
    const configs = rawConfigs.value
      .filter(item => !READ_ONLY_KEYS.includes(item.configKey))
      .map(item => {
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
        return { ...item, configValue: value }
      })
    const res = await updateSchoolDataConfig(configs)
    if (res?.code === 200) {
      ElMessage.success('配置已保存，正在自动测试连接...')
      await loadAll()
      // 保存成功后自动测试连接，确保端到端链路可用
      if (status.authConfigured || status.etlConfigured) {
        await autoTestAfterSave()
      }
    } else {
      ElMessage.error(res?.message || '保存失败')
    }
  } catch {
    ElMessage.error('保存配置失败')
  } finally {
    saving.value = false
  }
}

const autoTestAfterSave = async () => {
  testing.value = true
  testResult.value = null
  try {
    const res = await testSchoolDataConnection()
    if (res?.code === 200 && res.data) {
      testResult.value = res.data.success
      testSource.value = res.data.source || ''
      if (res.data.success) {
        ElMessage.success('✅ 学校数据源连接测试成功，同步链路可用')
      } else {
        ElMessage.warning('⚠️ 连接测试失败，请检查 App ID/Secret 是否正确、学校 API 是否可达')
      }
    } else {
      testResult.value = false
    }
    await loadStatus()
  } catch {
    testResult.value = false
    ElMessage.warning('⚠️ 连接测试异常，请查看后端日志')
  } finally {
    testing.value = false
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
    await loadStatus()
  } catch {
    testResult.value = false
  } finally {
    testing.value = false
  }
}

const toQuickSummary = (syncLog, mode, scopeLabel) => {
  if (!syncLog) {
    return null
  }
  return {
    scopeLabel,
    mode,
    totalCount: syncLog.totalCount ?? 0,
    successCount: syncLog.successCount ?? 0,
    failCount: syncLog.failCount ?? 0,
    skipCount: syncLog.skipCount ?? 0,
    status: syncLog.status || 'UNKNOWN',
    durationMs: syncLog.durationMs ?? null,
    errorMessage: syncLog.errorMessage || '',
    paginationComplete: syncLog.paginationComplete,
    paginationStopReason: syncLog.paginationStopReason || '',
    paginationPagesFetched: syncLog.paginationPagesFetched ?? null,
    paginationLastSkip: syncLog.paginationLastSkip ?? null,
    paginationPageSize: syncLog.paginationPageSize ?? null,
    fetchedRowCount: syncLog.fetchedRowCount ?? null
  }
}

const handleQuickSync = async () => {
  if (!canRunQuickActions.value) {
    ElMessage.warning('请先完成配置并测试连接')
    return
  }
  quickSyncing.value = true
  quickResult.value = null

  let loadingInstance = ElMessage({
    message: `正在同步 ${quickSyncForm.dataSource}，请稍候...`,
    type: 'info',
    duration: 0,
    showClose: false,
  })

  try {
    const syncFn = quickSyncForm.mode === 'full'
      ? fullSyncSchoolDataFiltered
      : incrementalSyncSchoolDataFiltered
    const res = await syncFn(quickSyncForm.dataSource, { triggerType: 'MANUAL', filterGroups: [] })
    if (res?.code === 200) {
      quickResult.value = toQuickSummary(res.data, quickSyncForm.mode, quickSyncForm.dataSource)
      ElMessage.success('学校数据同步完成')
    } else {
      throw new Error(res?.message || '学校数据同步失败')
    }
  } catch (error) {
    quickResult.value = {
      scopeLabel: quickSyncForm.dataSource,
      mode: quickSyncForm.mode,
      totalCount: 0,
      successCount: 0,
      failCount: 0,
      skipCount: 0,
      status: 'FAILED',
      durationMs: null,
      errorMessage: error.message || '学校数据同步失败'
    }
    ElMessage.error(quickResult.value.errorMessage)
  } finally {
    if (loadingInstance) {
      loadingInstance.close()
    }
    quickSyncing.value = false
  }
}

const handleQuickSyncAll = async (mode) => {
  if (!canRunQuickActions.value) {
    ElMessage.warning('请先完成配置并测试连接')
    return
  }
  quickSyncing.value = true
  quickResult.value = null

  let loadingInstance = ElMessage({
    message: `正在${mode === 'full' ? '全量' : '增量'}同步全部数据源，请稍候...`,
    type: 'info',
    duration: 0,
    showClose: false,
  })

  try {
    const syncFn = mode === 'full' ? fullSyncAllSchoolData : incrementalSyncAllSchoolData
    const res = await syncFn({ triggerType: 'MANUAL' })
    if (res?.code === 200 && Array.isArray(res.data)) {
      const totalCount = res.data.reduce((sum, item) => sum + (item.totalCount || 0), 0)
      const successCount = res.data.reduce((sum, item) => sum + (item.successCount || 0), 0)
      const failCount = res.data.reduce((sum, item) => sum + (item.failCount || 0), 0)
      const skipCount = res.data.reduce((sum, item) => sum + (item.skipCount || 0), 0)
      quickResult.value = {
        scopeLabel: '全部数据源',
        mode,
        totalCount,
        successCount,
        failCount,
        skipCount,
        status: res.data.some(item => item.status === 'FAILED') ? 'PARTIAL' : 'SUCCESS',
        durationMs: null,
        errorMessage: ''
      }
      ElMessage.success('全部数据源同步完成')
    } else {
      throw new Error(res?.message || '全部数据源同步失败')
    }
  } catch (error) {
    quickResult.value = {
      scopeLabel: '全部数据源',
      mode,
      totalCount: 0,
      successCount: 0,
      failCount: 0,
      skipCount: 0,
      status: 'FAILED',
      durationMs: null,
      errorMessage: error.message || '全部数据源同步失败'
    }
    ElMessage.error(quickResult.value.errorMessage)
  } finally {
    if (loadingInstance) {
      loadingInstance.close()
    }
    quickSyncing.value = false
  }
}

const handleQuickChainFetch = async () => {
  if (!canRunQuickActions.value) {
    ElMessage.warning('请先完成配置并测试连接')
    return
  }
  if (!chainFetchEnabled.value) {
    ElMessage.warning('按学院链式拉取功能已关闭')
    return
  }
  if (!chainFetchForm.collegeName.trim()) {
    ElMessage.warning('请输入学院名称')
    return
  }
  chainFetching.value = true
  chainResult.value = null

  let loadingInstance = ElMessage({
    message: `正在按学院链式拉取数据（${chainFetchForm.collegeName}），请稍候...`,
    type: 'info',
    duration: 0,
    showClose: false,
  })

  try {
    const res = await chainFetchStudentsByCollege({
      collegeName: chainFetchForm.collegeName.trim(),
      matchMode: chainFetchForm.matchMode,
      persist: true
    })
    if (res?.code === 200 && res.data) {
      chainResult.value = res.data
      if (chainResult.value?.status === 'FAILED') {
        ElMessage.error(chainResult.value.errorMessage || '按学院链式拉取失败，请查看诊断信息')
      } else if (chainResult.value?.status === 'PARTIAL') {
        ElMessage.warning(chainResult.value.errorMessage || '按学院链式拉取已完成，但存在异常')
      } else {
        ElMessage.success('按学院链式拉取完成')
      }
    } else {
      throw new Error(res?.message || '按学院链式拉取失败')
    }
  } catch (error) {
    chainResult.value = { errorMessage: error.message || '按学院链式拉取失败' }
    ElMessage.error(chainResult.value.errorMessage)
  } finally {
    if (loadingInstance) {
      loadingInstance.close()
    }
    chainFetching.value = false
  }
}

const chainStatusText = (status) => CHAIN_STATUS_LABELS[status] || '未知'

const chainErrors = (result) => (Array.isArray(result?.errors) ? result.errors : [])

const chainWarnings = (result) => (Array.isArray(result?.warnings) ? result.warnings : [])

const chainStepEntries = (result) => Object.entries(result?.stepStats || {})

const chainStepLabel = (stepName) => CHAIN_STEP_LABELS[stepName] || stepName

const formatChainStepStats = (stats) => {
  if (!stats || typeof stats !== 'object') return ''
  return Object.entries(stats)
    .filter(([, value]) => value !== null && value !== undefined && value !== '' && (!Array.isArray(value) || value.length > 0))
    .map(([key, value]) => `${key}=${Array.isArray(value) ? value.join(',') : value}`)
    .join(' | ')
}

const resolveChainResultTab = (result) => {
  if ((result?.studentCount || 0) > 0) return 'school_student'
  if ((result?.teacherCount || 0) > 0) return 'school_teacher'
  if ((result?.bindingCount || 0) > 0) return 'teacher_student_binding'
  if ((result?.courseCount || 0) > 0) return 'school_course'
  return 'school_student'
}

const openChainResultInBrowser = () => {
  const result = chainResult.value
  if (!result || result.status === 'FAILED') {
    return
  }
  router.push({
    path: '/ops/school-data-browser',
    query: {
      tab: resolveChainResultTab(result),
      collegeName: result.collegeName || chainFetchForm.collegeName.trim()
    }
  })
}

onMounted(async () => {
  try {
    await loadFeatureToggles({ authenticated: true })
  } catch {
    // ignore toggle preload failure; route guard/cache will retry
  }
  await loadAll()
})
</script>

<style scoped>
.school-data-config {
  max-width: 1400px;
  margin: 0 auto;
  padding: 24px;
}

/* PC端大字号适配 */
@media (min-width: 1200px) {
  .school-data-config {
    padding: 30px;
  }
  
  :deep(.el-collapse-item__header) {
    font-size: 18px;
    height: 56px;
  }

  .quick-actions-header strong {
    font-size: 20px;
  }

  .quick-card-title,
  .quick-result-title {
    font-size: 15px;
  }

  :deep(.el-form-item__label) {
    font-size: 15px;
  }

  :deep(.el-table) {
    font-size: 14px;
  }
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 24px;
}

.header-left h2 {
  margin: 0 0 4px;
  font-size: 24px;
  font-weight: 800;
  color: #1e293b;
  letter-spacing: -0.5px;
}

.subtitle {
  margin: 0;
  font-size: 14px;
  color: #64748b;
}

.banners-area {
  margin-bottom: 24px;
}

.premium-alert {
  border-radius: 16px;
  padding: 16px 20px;
  border: 1px solid rgba(239, 68, 68, 0.1);
  box-shadow: 0 4px 12px rgba(239, 68, 68, 0.05);
}

.success-alert {
  border-color: rgba(34, 197, 94, 0.1);
  box-shadow: 0 4px 12px rgba(34, 197, 94, 0.05);
}

.banner-title {
  font-weight: 700;
  font-size: 15px;
}

.banner-content p {
  margin: 8px 0;
  font-size: 14px;
}

.banner-hint {
  font-size: 13px;
  opacity: 0.9;
}

.config-collapse {
  background: white;
  border-radius: 20px;
  border: 1px solid #e2e8f0;
  overflow: hidden;
  box-shadow: 0 10px 25px rgba(0, 0, 0, 0.02);
}

:deep(.el-collapse-item__header) {
  padding: 16px 24px;
  font-size: 16px;
  font-weight: 700;
  color: #334155;
  background: white;
  transition: all 0.2s;
}

:deep(.el-collapse-item__header.is-active) {
  background: #f8fafc;
  color: #4f46e5;
}

:deep(.el-collapse-item__content) {
  padding: 24px;
  background: white;
}

.form-hint {
  margin-left: 12px;
  font-size: 12px;
  color: #94a3b8;
  font-weight: normal;
}

.endpoints-panel {
  background: #f1f5f9;
  border-radius: 16px;
  padding: 20px;
  margin-bottom: 24px;
}

.endpoints-title {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 16px;
  font-weight: 700;
  color: #475569;
}

.endpoints-table {
  border-radius: 12px;
  overflow: hidden;
  box-shadow: 0 4px 6px rgba(0,0,0,0.02);
}

.ds-name {
  font-family: ui-monospace, monospace;
  font-weight: 600;
  color: #4f46e5;
  background: #eef2ff;
  padding: 2px 6px;
  border-radius: 4px;
}

.ds-url {
  font-size: 12px;
  color: #64748b;
}

.action-row {
  display: flex;
  gap: 16px;
  padding: 8px 0;
  border-bottom: 1px solid #f1f5f9;
  margin-bottom: 24px;
}

.quick-actions-panel {
  background: #f8fafc;
  border: 1px dashed #cbd5e1;
  border-radius: 20px;
  padding: 24px;
}

.quick-actions-header strong {
  font-size: 16px;
  color: #1e293b;
}

.quick-actions-grid {
  grid-template-columns: repeat(auto-fit, minmax(300px, 1fr));
  gap: 20px;
  margin-top: 20px;
}

.quick-card {
  border-radius: 16px;
  border: 1px solid #e2e8f0;
  box-shadow: 0 4px 6px rgba(0,0,0,0.02);
  transition: transform 0.2s;
}

.quick-card:hover {
  transform: translateY(-2px);
  border-color: #4f46e5;
}

.quick-result-card {
  border-radius: 16px;
  border-left: 4px solid #4f46e5;
  box-shadow: 0 10px 20px rgba(0,0,0,0.04);
}

.quick-card-title,
.quick-result-title {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  font-weight: 600;
  color: #334155;
  margin-bottom: 8px;
}

.quick-result-actions {
  margin-top: 12px;
}

.quick-result-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: 10px 16px;
  font-size: 13px;
  color: #334155;
}

.quick-result-error {
  margin-top: 12px;
  padding: 12px 14px;
  border-radius: 12px;
  background: #fef2f2;
  border: 1px solid #fecaca;
  color: #991b1b;
  font-size: 13px;
  line-height: 1.5;
}

.quick-result-issues {
  margin-top: 12px;
  padding: 12px 14px;
  border-radius: 12px;
  background: #eff6ff;
  border: 1px solid #bfdbfe;
  color: #1e3a8a;
}

.quick-result-issues.warnings {
  background: #fff7ed;
  border-color: #fdba74;
  color: #9a3412;
}

.quick-result-issues-title {
  font-size: 12px;
  font-weight: 700;
  margin-bottom: 8px;
}

.quick-result-issue-item {
  font-size: 12px;
  line-height: 1.5;
}

.quick-result-issue-item + .quick-result-issue-item {
  margin-top: 6px;
}

.quick-result-steps {
  margin-top: 12px;
  display: grid;
  gap: 10px;
}

.quick-result-step {
  display: grid;
  gap: 6px;
  padding: 12px 14px;
  border-radius: 12px;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  font-size: 12px;
  color: #334155;
}

.quick-result-step strong {
  font-size: 12px;
  color: #0f172a;
}

.endpoints-table {
  background: #fff;
  border-radius: 6px;
  font-size: 12px;
}

.ds-name {
  background: #eff6ff;
  color: #1d4ed8;
  padding: 1px 6px;
  border-radius: 3px;
  font-family: ui-monospace, monospace;
  font-size: 12px;
}

.ds-url {
  font-family: ui-monospace, monospace;
  font-size: 12px;
  color: #475569;
  word-break: break-all;
}

.endpoints-hint {
  margin-top: 8px;
  font-size: 12px;
  color: #64748b;
  line-height: 1.5;
}

.endpoints-hint code {
  background: #e2e8f0;
  padding: 1px 5px;
  border-radius: 3px;
  font-size: 11px;
}

@media (max-width: 768px) {
  .school-data-config {
    padding: 16px;
  }

  .config-collapse :deep(.el-collapse-item__header) {
    padding: 12px 16px;
  }

  .config-collapse :deep(.el-collapse-item__content) {
    padding: 16px;
  }

  .action-row {
    flex-direction: column;
  }

  .action-row .el-button {
    width: 100%;
    margin-left: 0 !important;
  }

  .quick-actions-panel {
    padding: 16px;
  }

  .quick-actions-grid {
    grid-template-columns: 1fr;
  }

  .quick-card-form {
    flex-direction: column;
    align-items: stretch;
  }

  .quick-card-form :deep(.el-select),
  .quick-card-form :deep(.el-input),
  .quick-card-form .el-button {
    width: 100% !important;
  }
}
</style>
