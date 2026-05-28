<template>
  <div class="hot-deploy-page">
    <el-alert
      type="warning"
      :closable="false"
      style="margin-bottom: 20px"
    >
      <template #title>
        零停机热更新 — 仅适用于 Docker 生产环境。本地开发请使用 <code>hot-update-v2.sh</code>。
      </template>
    </el-alert>

    <!-- 当前状态 -->
    <el-card class="status-card">
      <template #header>
        <div class="card-header">
          <span>部署状态</span>
          <el-button size="small" :icon="Refresh" @click="loadStatus" :loading="statusLoading">刷新</el-button>
        </div>
      </template>
      <div class="status-grid">
        <div class="status-item">
          <span class="status-label">Blue 后端</span>
          <el-tag :type="blueHealthy ? 'success' : 'danger'" size="small">
            {{ blueHealthy ? '运行中' : '未运行' }}
          </el-tag>
          <span class="status-port">:{{ BLUE_PORT }}</span>
        </div>
        <div class="status-item">
          <span class="status-label">Green 后端</span>
          <el-tag :type="greenHealthy ? 'success' : 'info'" size="small">
            {{ greenHealthy ? '运行中' : '未运行' }}
          </el-tag>
          <span class="status-port">:{{ GREEN_PORT }}</span>
        </div>
        <div class="status-item">
          <span class="status-label">当前版本</span>
          <code class="commit-hash">{{ currentCommit || '—' }}</code>
        </div>
        <div class="status-item">
          <span class="status-label">上次部署</span>
          <span class="last-deploy">{{ lastDeployTime || '—' }}</span>
        </div>
      </div>
    </el-card>

    <!-- 部署配置 -->
    <el-card class="config-card">
      <template #header><span>部署配置</span></template>
      <el-form :model="deployConfig" label-width="120px">
        <el-form-item label="部署分支">
          <el-select v-model="deployConfig.branch" style="width: 200px" allow-create filterable>
            <el-option label="当前分支（默认）" value="" />
            <el-option label="master" value="master" />
            <el-option label="main" value="main" />
            <el-option label="develop" value="develop" />
          </el-select>
          <span class="form-hint">留空则使用服务器当前所在分支</span>
        </el-form-item>
        <el-form-item label="跳过构建">
          <el-switch v-model="deployConfig.skipBuild" />
          <span class="form-hint">使用现有镜像，不重新构建</span>
        </el-form-item>
        <el-form-item label="演练模式">
          <el-switch v-model="deployConfig.dryRun" />
          <span class="form-hint">仅打印操作，不实际执行</span>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 触发部署 -->
    <el-card class="trigger-card">
      <template #header><span>触发部署</span></template>
      <div class="trigger-row">
        <el-button
          type="primary"
          :icon="VideoPlay"
          :loading="deploying"
          :disabled="deploying"
          @click="confirmDeploy"
          size="large"
        >
          {{ deploying ? '部署中...' : '启动零停机热更新' }}
        </el-button>
        <el-button
          v-if="deploying"
          type="danger"
          plain
          @click="cancelDeploy"
        >
          取消
        </el-button>
      </div>

      <div class="deploy-steps" v-if="deploying || deploySteps.length > 0">
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
        <div class="card-header">
          <span>部署日志</span>
          <el-button size="small" :icon="Delete" @click="logs = []">清空</el-button>
        </div>
      </template>
      <div ref="logBox" class="log-area">
        <div v-if="logs.length === 0" class="log-placeholder">
          触发部署后将在此显示实时日志...
        </div>
        <div v-for="(line, i) in logs" :key="i" class="log-line" :class="logLineClass(line)">
          {{ line }}
        </div>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, nextTick, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh, VideoPlay, Delete, Loading, Check, Close, Right } from '@element-plus/icons-vue'
import request from '@/utils/request'

const BLUE_PORT = 8080
const GREEN_PORT = 8081

const statusLoading = ref(false)
const blueHealthy = ref(false)
const greenHealthy = ref(false)
const currentCommit = ref('')
const lastDeployTime = ref('')

const deploying = ref(false)
const logs = ref([])
const logBox = ref(null)

const deployConfig = ref({
  branch: '',
  skipBuild: false,
  dryRun: false
})

const DEPLOY_STEPS = [
  '拉取最新代码',
  '构建 green 镜像',
  '启动 green 后端',
  '健康检查',
  '切换流量 → green',
  '停止 blue 后端',
  '提升 green → blue',
  '完成'
]

const deploySteps = ref([])

const loadStatus = async () => {
  statusLoading.value = true
  try {
    const res = await request({ url: '/api/ops/hot-deploy/status', method: 'get', skipErrorHandler: true })
    if (res?.code === 200) {
      blueHealthy.value = res.data?.blueHealthy ?? false
      greenHealthy.value = res.data?.greenHealthy ?? false
      currentCommit.value = res.data?.currentCommit ?? ''
      lastDeployTime.value = res.data?.lastDeployTime ?? ''
    }
  } catch (e) {
    // status endpoint may not exist in dev — silently ignore
  } finally {
    statusLoading.value = false
  }
}

const confirmDeploy = async () => {
  const branchLabel = deployConfig.value.branch || '当前分支'
  const dryRunNote = deployConfig.value.dryRun ? '（演练模式，不实际执行）' : ''
  await ElMessageBox.confirm(
    `确认对分支 "${branchLabel}" 执行零停机热更新${dryRunNote}？\n\n此操作将重新构建并滚动替换后端服务。`,
    '确认部署',
    { type: 'warning', confirmButtonText: '确认部署', cancelButtonText: '取消' }
  )
  startDeploy()
}

const startDeploy = async () => {
  deploying.value = true
  logs.value = []
  deploySteps.value = DEPLOY_STEPS.map((label, i) => ({
    label,
    status: i === 0 ? 'running' : 'pending'
  }))

  try {
    const res = await request({
      url: '/api/ops/hot-deploy/trigger',
      method: 'post',
      data: {
        branch: deployConfig.value.branch,
        skipBuild: deployConfig.value.skipBuild,
        dryRun: deployConfig.value.dryRun
      }
    })

    if (res?.code === 200) {
      // Poll for logs via SSE or polling
      pollDeployLogs(res.data?.deployId)
    } else {
      addLog(`[ERROR] ${res?.message || '触发失败'}`)
      deploying.value = false
      markStepError(0)
    }
  } catch (e) {
    addLog('[ERROR] 请求失败，请检查后端服务是否运行')
    addLog('[INFO] 提示：可直接在服务器上运行 scripts/hot-deploy.sh 脚本')
    deploying.value = false
    markStepError(0)
  }
}

let pollTimer = null

const pollDeployLogs = (deployId) => {
  if (!deployId) {
    deploying.value = false
    return
  }
  let stepIdx = 0
  pollTimer = setInterval(async () => {
    try {
      const res = await request({
        url: `/api/ops/hot-deploy/logs/${deployId}`,
        method: 'get'
      })
      if (res?.code === 200) {
        const { lines, currentStep, status } = res.data || {}
        if (lines?.length) {
          lines.forEach(l => addLog(l))
        }
        if (currentStep !== undefined && currentStep !== stepIdx) {
          markStepDone(stepIdx)
          stepIdx = currentStep
          if (stepIdx < deploySteps.value.length) {
            deploySteps.value[stepIdx].status = 'running'
          }
        }
        if (status === 'SUCCESS') {
          markStepDone(stepIdx)
          deploying.value = false
          clearInterval(pollTimer)
          ElMessage.success('零停机热更新完成！')
          loadStatus()
        } else if (status === 'FAILED') {
          markStepError(stepIdx)
          deploying.value = false
          clearInterval(pollTimer)
          ElMessage.error('部署失败，请查看日志')
        }
      }
    } catch (e) {
      // ignore poll errors
    }
  }, 2000)
}

const cancelDeploy = () => {
  clearInterval(pollTimer)
  deploying.value = false
  addLog('[WARN] 用户取消了部署监控（后台进程可能仍在运行）')
}

const addLog = (line) => {
  logs.value.push(line)
  nextTick(() => {
    if (logBox.value) logBox.value.scrollTop = logBox.value.scrollHeight
  })
}

const markStepDone = (idx) => {
  if (idx < deploySteps.value.length) deploySteps.value[idx].status = 'done'
}
const markStepError = (idx) => {
  if (idx < deploySteps.value.length) deploySteps.value[idx].status = 'error'
}

const logLineClass = (line) => {
  if (line.includes('[ERROR]') || line.includes('✗')) return 'log-error'
  if (line.includes('[WARN]') || line.includes('⚠')) return 'log-warn'
  if (line.includes('[OK]') || line.includes('✓')) return 'log-ok'
  return ''
}

onMounted(loadStatus)
</script>

<style scoped>
.hot-deploy-page { padding: 24px; max-width: 900px; margin: 0 auto; display: flex; flex-direction: column; gap: 16px; }

.card-header { display: flex; align-items: center; justify-content: space-between; }

.status-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(180px, 1fr)); gap: 16px; }
.status-item { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.status-label { font-size: 13px; color: #64748b; min-width: 70px; }
.status-port { font-size: 12px; color: #94a3b8; }
.commit-hash { font-size: 12px; background: #f1f5f9; padding: 2px 6px; border-radius: 4px; }
.last-deploy { font-size: 13px; color: #475569; }

.form-hint { font-size: 12px; color: #94a3b8; margin-left: 8px; }

.trigger-row { display: flex; align-items: center; gap: 12px; margin-bottom: 16px; }

.deploy-steps { display: flex; flex-direction: column; gap: 6px; margin-top: 12px; }
.deploy-step { display: flex; align-items: center; gap: 8px; font-size: 13px; color: #64748b; padding: 4px 0; }
.deploy-step.running { color: #197fe6; font-weight: 600; }
.deploy-step.done { color: #22c55e; }
.deploy-step.error { color: #ef4444; }
.deploy-step.pending { color: #cbd5e1; }

.log-area {
  background: #0f172a;
  border-radius: 8px;
  padding: 12px 16px;
  height: 300px;
  overflow-y: auto;
  font-family: 'Consolas', 'Monaco', monospace;
  font-size: 12px;
  line-height: 1.6;
}
.log-placeholder { color: #475569; }
.log-line { color: #e2e8f0; white-space: pre-wrap; word-break: break-all; }
.log-line.log-error { color: #f87171; }
.log-line.log-warn { color: #fbbf24; }
.log-line.log-ok { color: #4ade80; }
</style>
