<template>
  <div class="deploy-container">
    <el-alert
      v-if="!supported"
      :title="unsupportedReason || '当前环境不支持自动部署'"
      type="warning"
      :closable="false"
      class="support-alert"
    />

    <el-tabs v-model="activeTab" class="deploy-tabs">
      <!-- ══════════════════ 标签一：系统更新 ══════════════════ -->
      <el-tab-pane label="系统更新" name="update">
        <el-card class="config-card">
          <template #header><span>更新配置</span></template>
          <el-form :model="config" label-width="140px" :disabled="!supported">
            <el-form-item label="启用定时更新">
              <el-switch v-model="config.enabled" />
            </el-form-item>
            <el-form-item label="Cron 表达式">
              <el-input
                v-model="config.cronExpr"
                placeholder="0 0 3 * * ?"
                :disabled="!config.enabled || !supported"
                style="width: 240px"
              />
              <div class="cron-hint">{{ cronHint }}</div>
            </el-form-item>
            <el-form-item label="更新分支">
              <el-select v-model="config.gitBranch" style="width: 160px" :disabled="!supported">
                <el-option label="main" value="main" />
                <el-option label="master" value="master" />
                <el-option label="develop" value="develop" />
              </el-select>
            </el-form-item>
            <el-form-item label="更新前自动备份">
              <el-switch v-model="config.backupBeforeUpdate" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :loading="saving" :disabled="!supported" @click="saveConfig">
                保存配置
              </el-button>
            </el-form-item>
          </el-form>
        </el-card>

        <el-card class="trigger-card">
          <template #header><span>手动触发更新</span></template>
          <div class="trigger-row">
            <el-button
              type="danger"
              :icon="VideoPlay"
              :loading="running"
              :disabled="running || !supported"
              @click="confirmTrigger"
            >
              立即触发更新
            </el-button>
            <div class="status-info">
              <span class="status-label">当前状态：</span>
              <el-tag :type="statusTagType">{{ statusText }}</el-tag>
              <span v-if="lastRunAt" class="last-time">上次执行：{{ lastRunAt }}</span>
            </div>
          </div>
        </el-card>

        <el-card class="log-card">
          <template #header>
            <div class="log-header">
              <span>实时日志</span>
              <el-button size="small" :icon="Delete" @click="logs = []">清空</el-button>
            </div>
          </template>
          <div ref="logBox" class="log-area">
            <div v-if="logs.length === 0" class="log-placeholder">暂无日志，触发更新后将在此显示输出...</div>
            <div v-for="(line, i) in logs" :key="i" class="log-line">{{ line }}</div>
          </div>
        </el-card>

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
      </el-tab-pane>

      <!-- ══════════════════ 标签二：零停机热部署 ══════════════════ -->
      <el-tab-pane label="零停机热部署" name="hot-deploy">
        <el-alert type="info" :closable="false" style="margin-bottom:16px">
          <template #title>
            Blue/Green 无中断部署 — 构建新镜像后切换流量，旧实例保持运行直到新实例健康。仅适用于 Docker 生产环境。
          </template>
        </el-alert>

        <el-card class="config-card">
          <template #header><span>热部署配置</span></template>
          <el-form :model="config" label-width="140px" :disabled="!supported">
            <el-form-item label="启用定时热部署">
              <el-switch v-model="config.hotDeployEnabled" />
            </el-form-item>
            <el-form-item label="Cron 表达式">
              <el-input
                v-model="config.hotDeployCronExpr"
                placeholder="0 0 2 ? * SUN"
                :disabled="!config.hotDeployEnabled || !supported"
                style="width: 240px"
              />
              <div class="cron-hint">{{ hotCronHint }}</div>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :loading="saving" :disabled="!supported" @click="saveConfig">
                保存配置
              </el-button>
            </el-form-item>
          </el-form>
        </el-card>

        <el-card class="config-card">
          <template #header><span>部署参数</span></template>
          <el-form :model="hotConfig" label-width="140px" :disabled="!supported">
            <el-form-item label="部署分支">
              <el-select v-model="hotConfig.branch" style="width: 200px" allow-create filterable>
                <el-option label="当前分支（默认）" value="" />
                <el-option label="master" value="master" />
                <el-option label="main" value="main" />
                <el-option label="develop" value="develop" />
              </el-select>
              <span class="cron-hint">留空则使用服务器当前所在分支</span>
            </el-form-item>
            <el-form-item label="跳过构建">
              <el-switch v-model="hotConfig.skipBuild" />
              <span class="cron-hint">使用现有镜像，不重新构建</span>
            </el-form-item>
            <el-form-item label="演练模式">
              <el-switch v-model="hotConfig.dryRun" />
              <span class="cron-hint">仅打印操作，不实际执行</span>
            </el-form-item>
          </el-form>
        </el-card>

        <el-card class="trigger-card">
          <template #header><span>触发热部署</span></template>
          <div class="trigger-row">
            <el-button
              type="primary"
              :icon="VideoPlay"
              :loading="hotRunning"
              :disabled="hotRunning || !supported"
              @click="confirmHotDeploy"
              size="large"
            >
              {{ hotRunning ? '热部署中...' : '启动零停机热部署' }}
            </el-button>
            <div class="status-info">
              <span class="status-label">当前状态：</span>
              <el-tag :type="hotStatusTagType">{{ hotStatusText }}</el-tag>
              <span v-if="hotLastRunAt" class="last-time">上次执行：{{ hotLastRunAt }}</span>
            </div>
          </div>

          <div class="deploy-steps" v-if="hotRunning || hotStepsDone">
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

        <el-card class="log-card">
          <template #header>
            <div class="log-header">
              <span>热部署日志</span>
              <el-button size="small" :icon="Delete" @click="hotLogs = []">清空</el-button>
            </div>
          </template>
          <div ref="hotLogBox" class="log-area">
            <div v-if="hotLogs.length === 0" class="log-placeholder">触发热部署后将在此显示实时日志...</div>
            <div v-for="(line, i) in hotLogs" :key="i" class="log-line" :class="hotLogLineClass(line)">{{ line }}</div>
          </div>
        </el-card>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, onUnmounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Check, Close, Delete, Loading, Refresh, Right, VideoPlay } from '@element-plus/icons-vue'
import { getDeployHistory, getUpdateConfig, getUpdateStatus, saveUpdateConfig } from '@/api/ops'
import { useUserStore } from '@/store/user'
import { environment } from '@/utils/environment'

const activeTab = ref('update')

const supported = ref(true)
const unsupportedReason = ref('')
const saving = ref(false)

const config = ref({
  enabled: false,
  cronExpr: '0 0 3 * * ?',
  gitBranch: 'master',
  backupBeforeUpdate: true,
  hotDeployEnabled: false,
  hotDeployCronExpr: '0 0 2 ? * SUN',
})

const running = ref(false)
const logs = ref([])
const logBox = ref(null)
const lastRunStatus = ref('NEVER')
const lastRunAt = ref('')
const deployHistory = ref([])
let pollTimer = null
let eventSource = null

const hotRunning = ref(false)
const hotLogs = ref([])
const hotLogBox = ref(null)
const hotLastRunStatus = ref('NEVER')
const hotLastRunAt = ref('')
const hotStepsDone = ref(false)
let hotEventSource = null
const hotConfig = ref({ branch: '', skipBuild: false, dryRun: false })

const DEPLOY_STEPS_LABELS = [
  '拉取最新代码', '构建 green 镜像', '启动 green 后端',
  '健康检查', '切换流量 → green', '停止 blue 后端', '提升 green → blue', '完成',
]
const deploySteps = ref([])

const statusTagType = computed(() =>
  ({ SUCCESS: 'success', FAILED: 'danger', RUNNING: 'warning', NEVER: 'info' })[lastRunStatus.value] || 'info')
const statusText = computed(() =>
  ({ SUCCESS: '成功', FAILED: '失败', RUNNING: '运行中', NEVER: '从未执行' })[lastRunStatus.value] || lastRunStatus.value)
const hotStatusTagType = computed(() =>
  ({ SUCCESS: 'success', FAILED: 'danger', RUNNING: 'warning', NEVER: 'info' })[hotLastRunStatus.value] || 'info')
const hotStatusText = computed(() =>
  ({ SUCCESS: '成功', FAILED: '失败', RUNNING: '运行中', NEVER: '从未执行' })[hotLastRunStatus.value] || hotLastRunStatus.value)
const cronHint = computed(() => {
  const e = config.value.cronExpr?.trim()
  if (!e) return ''
  if (e === '0 0 3 * * ?') return '每天 03:00 执行'
  if (e === '0 0 2 * * ?') return '每天 02:00 执行'
  if (e === '0 0 2 ? * SUN') return '每周日 02:00 执行'
  return 'Spring Cron 表达式（秒 分 时 日 月 周）'
})
const hotCronHint = computed(() => {
  const e = config.value.hotDeployCronExpr?.trim()
  if (!e) return ''
  if (e === '0 0 2 ? * SUN') return '每周日 02:00 执行'
  if (e === '0 0 3 * * ?') return '每天 03:00 执行'
  return 'Spring Cron 表达式（秒 分 时 日 月 周）'
})

async function loadConfig() {
  try {
    const res = await getUpdateConfig()
    if (res?.data) {
      config.value = {
        enabled: !!res.data.enabled,
        cronExpr: res.data.cronExpr || '0 0 3 * * ?',
        gitBranch: res.data.gitBranch || 'master',
        backupBeforeUpdate: res.data.backupBeforeUpdate !== false,
        hotDeployEnabled: !!res.data.hotDeployEnabled,
        hotDeployCronExpr: res.data.hotDeployCronExpr || '0 0 2 ? * SUN',
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
      hotLastRunStatus.value = res.data.hotDeployLastRunStatus || 'NEVER'
      hotLastRunAt.value = res.data.hotDeployLastRunAt || ''
      if (lastRunStatus.value === 'RUNNING') running.value = true
      else if (running.value && eventSource === null) running.value = false
      if (hotLastRunStatus.value === 'RUNNING') hotRunning.value = true
      else if (hotRunning.value && hotEventSource === null) hotRunning.value = false
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
  if (!supported.value) { ElMessage.warning(unsupportedReason.value || '当前环境不支持自动部署'); return }
  if (config.value.enabled && !config.value.cronExpr?.trim()) { ElMessage.warning('请填写更新 Cron 表达式'); return }
  if (config.value.hotDeployEnabled && !config.value.hotDeployCronExpr?.trim()) { ElMessage.warning('请填写热部署 Cron 表达式'); return }
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

async function confirmTrigger() {
  if (!supported.value) { ElMessage.warning(unsupportedReason.value || '当前环境不支持系统自更新'); return }
  try {
    await ElMessageBox.confirm(
      '将立即拉取远程仓库并重新构建 Docker 环境，期间服务可能短暂中断，确定继续？',
      '确认触发更新',
      { confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning' }
    )
    triggerUpdate()
  } catch { /* cancelled */ }
}

function triggerUpdate() {
  if (eventSource) { eventSource.close(); eventSource = null }
  logs.value = []
  running.value = true
  lastRunStatus.value = 'RUNNING'
  const token = useUserStore().token || ''
  const url = environment.resolveApiUrl('/api/ops/update/trigger')
  eventSource = new EventSource(`${url}${token ? `?token=${encodeURIComponent(token)}` : ''}`)
  eventSource.onmessage = (ev) => {
    if (!ev.data) return
    logs.value.push(ev.data)
    nextTick(() => { if (logBox.value) logBox.value.scrollTop = logBox.value.scrollHeight })
  }
  eventSource.addEventListener('done', async (ev) => {
    logs.value.push(ev.data || '')
    eventSource?.close(); eventSource = null
    running.value = false
    lastRunStatus.value = (ev.data || '').startsWith('UPDATE_SUCCESS') ? 'SUCCESS' : 'FAILED'
    await loadStatus()
  })
  eventSource.onerror = async () => {
    eventSource?.close(); eventSource = null
    running.value = false
    await loadStatus()
  }
}

async function confirmHotDeploy() {
  if (!supported.value) { ElMessage.warning(unsupportedReason.value || '当前环境不支持热部署'); return }
  const branchLabel = hotConfig.value.branch || '当前分支'
  const dryNote = hotConfig.value.dryRun ? '（演练模式，不实际执行）' : ''
  try {
    await ElMessageBox.confirm(
      `确认对分支 "${branchLabel}" 执行零停机热部署${dryNote}？\n\n将构建新镜像并通过 Blue/Green 切换，期间服务不中断。`,
      '确认热部署',
      { confirmButtonText: '确认部署', cancelButtonText: '取消', type: 'warning' }
    )
    startHotDeploy()
  } catch { /* cancelled */ }
}

function startHotDeploy() {
  if (hotEventSource) { hotEventSource.close(); hotEventSource = null }
  hotLogs.value = []
  hotRunning.value = true
  hotStepsDone.value = false
  hotLastRunStatus.value = 'RUNNING'
  deploySteps.value = DEPLOY_STEPS_LABELS.map((label, i) => ({ label, status: i === 0 ? 'running' : 'pending' }))

  const token = useUserStore().token || ''
  const params = new URLSearchParams()
  if (hotConfig.value.branch) params.set('branch', hotConfig.value.branch)
  if (hotConfig.value.skipBuild) params.set('skipBuild', 'true')
  if (hotConfig.value.dryRun) params.set('dryRun', 'true')
  if (token) params.set('token', token)
  const url = environment.resolveApiUrl(`/api/ops/update/hot-deploy/trigger?${params}`)

  hotEventSource = new EventSource(url)
  let stepIdx = 0
  const stepKeywords = ['拉取', '构建', '启动 green', '健康检查', '切换流量', '停止 blue', '提升', '完成']

  hotEventSource.onmessage = (ev) => {
    if (!ev.data) return
    hotLogs.value.push(ev.data)
    nextTick(() => { if (hotLogBox.value) hotLogBox.value.scrollTop = hotLogBox.value.scrollHeight })
    const matched = stepKeywords.findIndex(k => ev.data.includes(k))
    if (matched > stepIdx && matched < deploySteps.value.length) {
      deploySteps.value[stepIdx].status = 'done'
      stepIdx = matched
      deploySteps.value[stepIdx].status = 'running'
    }
  }

  hotEventSource.addEventListener('done', async (ev) => {
    const data = ev.data || ''
    hotLogs.value.push(data)
    hotEventSource?.close(); hotEventSource = null
    hotRunning.value = false
    hotStepsDone.value = true
    const success = data.startsWith('HOT_DEPLOY_SUCCESS')
    deploySteps.value[stepIdx].status = success ? 'done' : 'error'
    hotLastRunStatus.value = success ? 'SUCCESS' : 'FAILED'
    if (success) ElMessage.success('零停机热部署完成！')
    else ElMessage.error('热部署失败，请查看日志')
    await loadStatus()
  })

  hotEventSource.onerror = async () => {
    hotEventSource?.close(); hotEventSource = null
    hotRunning.value = false
    if (stepIdx < deploySteps.value.length) deploySteps.value[stepIdx].status = 'error'
    await loadStatus()
  }
}

function eventTagType(event) {
  return { SUCCESS: 'success', ROLLBACK: 'warning', ERROR: 'danger', SKIP: 'info', WARN: 'warning' }[event] || 'info'
}

function hotLogLineClass(line) {
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
  if (eventSource) { eventSource.close(); eventSource = null }
  if (hotEventSource) { hotEventSource.close(); hotEventSource = null }
})
</script>

<style scoped>
.deploy-container { padding: 20px; max-width: 1200px; margin: 0 auto; }
.support-alert { margin-bottom: 20px; border-radius: 12px; }
.config-card, .trigger-card, .log-card, .history-card { margin-bottom: 20px; border-radius: 12px; border: none; box-shadow: 0 4px 12px rgba(0,0,0,0.05); }
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
