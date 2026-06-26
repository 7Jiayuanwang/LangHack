<template>
  <div class="deploy-container">
    <el-alert
      v-if="!supported"
      type="warning"
      :closable="false"
      class="support-alert"
    >
      <template #title>
        {{ unsupportedReason || '当前环境不支持热部署' }}
      </template>
      <span>配置仍可编辑保存，部署到支持热部署的环境后即可生效。</span>
    </el-alert>

    <!-- 配置卡片 -->
    <el-card class="config-card">
      <template #header><span>部署配置</span></template>
      <el-form :model="config" label-width="140px">
        <el-form-item label="启用定时热部署">
          <el-switch v-model="config.enabled" />
        </el-form-item>
        <el-form-item label="Cron 表达式">
          <el-input
            v-model="config.cronExpr"
            placeholder="0 0 2 ? * SUN"
            :disabled="!config.enabled"
            style="width: 240px"
          />
          <div class="cron-hint">{{ cronHint }}</div>
        </el-form-item>
        <el-form-item label="部署分支">
          <el-select v-model="config.gitBranch" style="width: 160px">
            <el-option label="master" value="master" />
            <el-option label="main" value="main" />
            <el-option label="develop" value="develop" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="saving" @click="saveConfig">
            保存配置
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 触发卡片 -->
    <el-card class="trigger-card">
      <template #header><span>手动触发热部署</span></template>

      <el-form :model="deployParams" label-width="140px" :disabled="!supported || running" class="deploy-params">
        <el-form-item label="部署分支">
          <el-select v-model="deployParams.branch" style="width: 200px" allow-create filterable>
            <el-option label="使用配置分支（默认）" value="" />
            <el-option label="master" value="master" />
            <el-option label="main" value="main" />
            <el-option label="develop" value="develop" />
          </el-select>
          <span class="cron-hint">留空则使用上方配置的分支</span>
        </el-form-item>
        <el-form-item label="跳过构建">
          <el-switch v-model="deployParams.skipBuild" />
          <span class="cron-hint">使用现有镜像，不重新构建</span>
        </el-form-item>
        <el-form-item label="跳过拉取">
          <el-switch v-model="deployParams.skipPull" />
          <span class="cron-hint">使用本地当前代码（GitHub 不可达时使用）</span>
        </el-form-item>
        <el-form-item label="演练模式">
          <el-switch v-model="deployParams.dryRun" />
          <span class="cron-hint">仅打印操作，不实际执行</span>
        </el-form-item>
      </el-form>

      <div class="trigger-row">
        <el-button
          type="primary"
          :icon="VideoPlay"
          :loading="running"
          :disabled="running || !supported"
          size="large"
          @click="confirmDeploy"
        >
          {{ running ? '热部署中...' : '启动零停机热部署' }}
        </el-button>
        <div class="status-info">
          <span class="status-label">当前状态：</span>
          <el-tag :type="statusTagType">{{ statusText }}</el-tag>
          <span v-if="lastRunAt" class="last-time">上次执行：{{ lastRunAt }}</span>
        </div>
      </div>

      <div class="deploy-steps" v-if="running || stepsDone">
        <div
          v-for="(step, i) in deploySteps"
          :key="i"
          class="deploy-step"
          :class="step.status"
        >
          <el-icon v-if="step.status === 'running'" class="is-loading"><Loading /></el-icon>
          <el-icon v-else-if="step.status === 'done'"><Check /></el-icon>
          <el-icon v-else-if="step.status === 'error'"><Close /></el-icon>
          <el-icon v-else><Right /></el-icon>
          <span>{{ step.label }}</span>
        </div>
      </div>
    </el-card>

    <!-- 实时日志 -->
    <el-card class="log-card">
      <template #header>
        <div class="log-header">
          <span>实时日志</span>
          <el-button size="small" :icon="Delete" @click="logs = []">清空</el-button>
        </div>
      </template>
      <div ref="logBox" class="log-area">
        <div v-if="logs.length === 0" class="log-placeholder">触发热部署后将在此显示实时日志...</div>
        <div v-for="(line, i) in logs" :key="i" class="log-line" :class="logLineClass(line)">{{ line }}</div>
      </div>
    </el-card>

    <!-- 部署历史 -->
    <el-card class="history-card">
      <template #header>
        <div class="log-header">
          <span>部署历史</span>
          <el-button size="small" :icon="Refresh" @click="loadHistory">刷新</el-button>
        </div>
      </template>
      <el-table :data="deployHistory" size="small" style="width: 100%">
        <el-table-column prop="time" label="时间" width="180" />
        <el-table-column prop="event" label="类型" width="100">
          <template #default="{ row }">
            <el-tag :type="eventTagType(row.event)" size="small">{{ row.event }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="commit" label="Commit" width="90" />
        <el-table-column prop="desc" label="描述" show-overflow-tooltip />
      </el-table>
      <div v-if="deployHistory.length === 0" style="color:#909399;font-size:13px;padding:12px 0">暂无部署记录</div>
    </el-card>
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, onUnmounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Check, Close, Delete, Loading, Refresh, Right, VideoPlay } from '@element-plus/icons-vue'
import { getDeployHistory, getUpdateConfig, getUpdateStatus, saveUpdateConfig } from '@/api/ops'
import { useUserStore } from '@/store/user'
import { environment } from '@/utils/environment'

const supported = ref(true)
const unsupportedReason = ref('')
const saving = ref(false)

const config = ref({
  enabled: false,
  cronExpr: '0 0 2 ? * SUN',
  gitBranch: 'master',
})

const deployParams = ref({ branch: '', skipBuild: false, skipPull: false, dryRun: false })

const running = ref(false)
const logs = ref([])
const logBox = ref(null)
const lastRunStatus = ref('NEVER')
const lastRunAt = ref('')
const stepsDone = ref(false)
const deployHistory = ref([])

let pollTimer = null
let eventSource = null

const DEPLOY_STEPS_LABELS = [
  '拉取最新代码', '构建 green 镜像', '启动 green 后端',
  '健康检查', '切换流量 → green', '停止 blue 后端', '提升 green → blue', '完成',
]
const deploySteps = ref([])

const statusTagType = computed(() =>
  ({ SUCCESS: 'success', FAILED: 'danger', RUNNING: 'warning', NEVER: 'info' })[lastRunStatus.value] || 'info')
const statusText = computed(() =>
  ({ SUCCESS: '成功', FAILED: '失败', RUNNING: '运行中', NEVER: '从未执行' })[lastRunStatus.value] || lastRunStatus.value)

const cronHint = computed(() => {
  const e = config.value.cronExpr?.trim()
  if (!e) return ''
  if (e === '0 0 2 ? * SUN') return '每周日 02:00 执行'
  if (e === '0 0 3 * * ?') return '每天 03:00 执行'
  if (e === '0 0 2 * * ?') return '每天 02:00 执行'
  return 'Spring Cron 表达式（秒 分 时 日 月 周）'
})

async function loadConfig() {
  try {
    const res = await getUpdateConfig()
    if (res?.data) {
      config.value = {
        enabled: !!res.data.enabled,
        cronExpr: res.data.cronExpr || '0 0 2 ? * SUN',
        gitBranch: res.data.gitBranch || 'master',
      }
    }
  } catch { /* keep defaults */ }
}

async function loadStatus() {
  try {
    const res = await getUpdateStatus()
    if (res?.data) {
      lastRunStatus.value = res.data.lastRunStatus || 'NEVER'
      lastRunAt.value = res.data.lastRunAt || ''
      supported.value = res.data.supported !== false
      unsupportedReason.value = res.data.unsupportedReason || ''
      if (lastRunStatus.value === 'RUNNING') running.value = true
      else if (running.value && eventSource === null) running.value = false
    }
  } catch { /* ignore */ }
}

async function loadHistory() {
  try {
    const res = await getDeployHistory(50)
    if (res?.data) deployHistory.value = res.data
  } catch { /* ignore */ }
}

async function saveConfig() {
  if (config.value.enabled && !config.value.cronExpr?.trim()) { ElMessage.warning('请填写 Cron 表达式'); return }
  saving.value = true
  try {
    await saveUpdateConfig(config.value)
    ElMessage.success('配置已保存')
    await loadStatus()
  } catch (err) {
    ElMessage.error(err?.message || '保存失败')
  } finally {
    saving.value = false
  }
}

async function confirmDeploy() {
  if (!supported.value) { ElMessage.warning(unsupportedReason.value || '当前环境不支持热部署'); return }
  const branchLabel = deployParams.value.branch || config.value.gitBranch || '当前分支'
  const dryNote = deployParams.value.dryRun ? '（演练模式，不实际执行）' : ''
  try {
    await ElMessageBox.confirm(
      `确认对分支 "${branchLabel}" 执行零停机热部署${dryNote}？\n\n将构建新镜像并通过 Blue/Green 切换，期间服务不中断。`,
      '确认热部署',
      { confirmButtonText: '确认部署', cancelButtonText: '取消', type: 'warning' }
    )
    startDeploy()
  } catch { /* cancelled */ }
}

function startDeploy() {
  if (eventSource) { eventSource.close(); eventSource = null }
  logs.value = []
  running.value = true
  stepsDone.value = false
  lastRunStatus.value = 'RUNNING'
  deploySteps.value = DEPLOY_STEPS_LABELS.map((label, i) => ({ label, status: i === 0 ? 'running' : 'pending' }))

  const token = useUserStore().token || ''
  const params = new URLSearchParams()
  if (deployParams.value.branch) params.set('branch', deployParams.value.branch)
  if (deployParams.value.skipBuild) params.set('skipBuild', 'true')
  if (deployParams.value.skipPull)  params.set('skipPull', 'true')
  if (deployParams.value.dryRun) params.set('dryRun', 'true')
  if (token) params.set('token', token)
  const url = environment.resolveApiUrl(`/api/ops/update/trigger?${params}`)

  eventSource = new EventSource(url)
  let stepIdx = 0
  const stepKeywords = ['拉取', '构建', '启动 green', '健康检查', '切换流量', '停止 blue', '提升', '完成']

  eventSource.onmessage = (ev) => {
    if (!ev.data) return
    logs.value.push(ev.data)
    nextTick(() => { if (logBox.value) logBox.value.scrollTop = logBox.value.scrollHeight })
    const matched = stepKeywords.findIndex(k => ev.data.includes(k))
    if (matched > stepIdx && matched < deploySteps.value.length) {
      deploySteps.value[stepIdx].status = 'done'
      stepIdx = matched
      deploySteps.value[stepIdx].status = 'running'
    }
  }

  eventSource.addEventListener('done', async (ev) => {
    const data = ev.data || ''
    logs.value.push(data)
    eventSource?.close(); eventSource = null
    running.value = false
    stepsDone.value = true
    const success = data.startsWith('HOT_DEPLOY_SUCCESS')
    deploySteps.value[stepIdx].status = success ? 'done' : 'error'
    lastRunStatus.value = success ? 'SUCCESS' : 'FAILED'
    if (success) ElMessage.success('零停机热部署完成！')
    else ElMessage.error('热部署失败，请查看日志')
    await loadStatus()
  })

  eventSource.onerror = async () => {
    eventSource?.close(); eventSource = null
    if (running.value) {
      logs.value.push('[INFO] 后端正在重启，部署仍在进行中...')
      nextTick(() => { if (logBox.value) logBox.value.scrollTop = logBox.value.scrollHeight })
      startReconnectPoll()
    }
  }
}

let reconnectTimer = null

function startReconnectPoll() {
  if (reconnectTimer) return
  reconnectTimer = setInterval(async () => {
    try {
      const res = await getUpdateStatus()
      if (!res?.data) return
      const st = res.data.lastRunStatus
      if (st && st !== 'RUNNING') {
        clearInterval(reconnectTimer)
        reconnectTimer = null
        running.value = false
        stepsDone.value = true
        lastRunStatus.value = st
        const success = st === 'SUCCESS'
        deploySteps.value.forEach(s => { if (s.status === 'running') s.status = success ? 'done' : 'error' })
        if (success) {
          logs.value.push('[OK] 零停机热部署完成！')
          ElMessage.success('零停机热部署完成！')
        } else {
          logs.value.push('[ERROR] 热部署失败')
          ElMessage.error('热部署失败，请查看日志')
        }
      }
    } catch { /* backend still restarting */ }
  }, 5000)
}

function eventTagType(event) {
  return { SUCCESS: 'success', ROLLBACK: 'warning', ERROR: 'danger', SKIP: 'info', WARN: 'warning' }[event] || 'info'
}

function logLineClass(line) {
  if (line.includes('[ERROR]') || line.includes('FAILED')) return 'log-error'
  if (line.includes('[WARN]') || line.includes('WARNING')) return 'log-warn'
  if (line.includes('[OK]') || line.includes('SUCCESS')) return 'log-ok'
  return ''
}

onMounted(() => {
  loadConfig()
  loadStatus()
  loadHistory()
  pollTimer = setInterval(loadStatus, 5000)
})

onUnmounted(() => {
  if (pollTimer) clearInterval(pollTimer)
  if (reconnectTimer) { clearInterval(reconnectTimer); reconnectTimer = null }
  if (eventSource) { eventSource.close(); eventSource = null }
})
</script>

<style scoped>
.deploy-container { padding: 20px; max-width: 1200px; margin: 0 auto; }
.support-alert { margin-bottom: 20px; border-radius: 12px; }
.config-card, .trigger-card, .log-card, .history-card { margin-bottom: 20px; border-radius: 12px; border: none; box-shadow: 0 4px 12px rgba(0,0,0,0.05); }
.deploy-params { margin-bottom: 16px; }
.cron-hint { margin-top: 6px; font-size: 12px; color: #94a3b8; display: block; }
.trigger-row { display: flex; align-items: center; gap: 24px; flex-wrap: wrap; padding: 8px 0; }
.status-info { display: flex; align-items: center; gap: 12px; background: #f8fafc; padding: 8px 16px; border-radius: 8px; }
.status-label { color: #64748b; font-size: 14px; font-weight: 500; }
.last-time { color: #94a3b8; font-size: 13px; }
.deploy-steps { display: flex; flex-direction: column; gap: 6px; margin-top: 16px; }
.deploy-step { display: flex; align-items: center; gap: 8px; font-size: 13px; color: #64748b; padding: 4px 0; }
.deploy-step.running { color: #197fe6; font-weight: 600; }
.deploy-step.done { color: #22c55e; }
.deploy-step.error { color: #ef4444; }
.deploy-step.pending { color: #cbd5e1; }
.log-header { display: flex; justify-content: space-between; align-items: center; }
.log-area { background: #0f172a; color: #e2e8f0; padding: 20px; border-radius: 8px; font-family: 'JetBrains Mono', 'Fira Code', monospace; font-size: 13px; line-height: 1.7; max-height: 500px; overflow-y: auto; min-height: 120px; box-shadow: inset 0 2px 8px rgba(0,0,0,0.2); }
.log-placeholder { color: #64748b; font-style: italic; text-align: center; padding: 40px 0; }
.log-line { white-space: pre-wrap; word-break: break-all; border-left: 2px solid transparent; padding-left: 8px; margin-bottom: 2px; }
.log-line:hover { background: rgba(255,255,255,0.05); border-left-color: #38bdf8; }
.log-line.log-error { color: #f87171; }
.log-line.log-warn { color: #fbbf24; }
.log-line.log-ok { color: #4ade80; }
@media (max-width: 768px) {
  .deploy-container { padding: 12px; }
  .trigger-row { flex-direction: column; align-items: stretch; gap: 16px; }
  .status-info { justify-content: space-between; width: 100%; }
  .log-area { padding: 12px; font-size: 12px; }
}
</style>
