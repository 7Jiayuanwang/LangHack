<template>
  <div class="update-container">
    <el-alert
      v-if="!supported"
      :title="unsupportedReason || '当前环境不支持系统自更新'"
      type="warning"
      :closable="false"
      class="support-alert"
    />

    <el-card class="config-card">
      <template #header>
        <span>更新配置</span>
      </template>
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
          <el-select
            v-model="config.gitBranch"
            style="width: 160px"
            :disabled="!supported"
          >
            <el-option label="main" value="main" />
            <el-option label="master" value="master" />
            <el-option label="develop" value="develop" />
          </el-select>
        </el-form-item>
        <el-form-item label="更新前自动备份">
          <el-switch v-model="config.backupBeforeUpdate" />
        </el-form-item>
        <el-form-item>
          <el-button
            type="primary"
            :loading="saving"
            :disabled="!supported"
            @click="saveConfig"
          >
            保存配置
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card class="trigger-card">
      <template #header>
        <span>手动触发更新</span>
      </template>
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
          <span v-if="lastRunAt" class="last-time"
            >上次执行：{{ lastRunAt }}</span
          >
        </div>
      </div>
    </el-card>

    <el-card class="log-card">
      <template #header>
        <div class="log-header">
          <span>实时日志</span>
          <el-button size="small" :icon="Delete" @click="clearLogs"
            >清空</el-button
          >
        </div>
      </template>
      <div ref="logBox" class="log-area">
        <div v-if="logs.length === 0" class="log-placeholder">
          暂无日志，触发更新后将在此显示输出...
        </div>
        <div v-for="(line, index) in logs" :key="index" class="log-line">
          {{ line }}
        </div>
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
      <div v-if="deployHistory.length === 0" style="color:#909399;font-size:13px;padding:12px 0">
        暂无部署记录
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, onUnmounted, ref } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import { Delete, Refresh, VideoPlay } from "@element-plus/icons-vue";
import { getDeployHistory, getUpdateConfig, getUpdateStatus, saveUpdateConfig } from "@/api/ops";
import { useUserStore } from "@/store/user";
import { environment } from "@/utils/environment";

const config = ref({
  enabled: false,
  cronExpr: "0 0 3 * * ?",
  gitBranch: "master",
  backupBeforeUpdate: true,
});

const saving = ref(false);
const running = ref(false);
const logs = ref([]);
const logBox = ref(null);
const lastRunStatus = ref("NEVER");
const lastRunAt = ref("");
const supported = ref(true);
const unsupportedReason = ref("");

let pollTimer = null;
let eventSource = null;

const deployHistory = ref([]);

const statusTagType = computed(
  () =>
    ({
      SUCCESS: "success",
      FAILED: "danger",
      RUNNING: "warning",
      NEVER: "info",
    })[lastRunStatus.value] || "info",
);

const statusText = computed(
  () =>
    ({
      SUCCESS: "成功",
      FAILED: "失败",
      RUNNING: "运行中",
      NEVER: "从未执行",
    })[lastRunStatus.value] || lastRunStatus.value,
);

const cronHint = computed(() => {
  const expr = config.value.cronExpr?.trim();
  if (!expr) return "";
  if (expr === "0 0 3 * * ?") return "每天 03:00 执行";
  if (expr === "0 0 2 * * ?") return "每天 02:00 执行";
  if (expr === "0 0 2 ? * SUN") return "每周日 02:00 执行";
  return "Spring Cron 表达式（秒 分 时 日 月 周）";
});

async function loadConfig() {
  try {
    const response = await getUpdateConfig();
    if (response?.data) {
      config.value = {
        enabled: !!response.data.enabled,
        cronExpr: response.data.cronExpr || "0 0 3 * * ?",
        gitBranch: response.data.gitBranch || "master",
        backupBeforeUpdate: response.data.backupBeforeUpdate !== false,
      };
    }
  } catch {
    // Keep defaults.
  }
}

async function loadStatus() {
  try {
    const response = await getUpdateStatus();
    if (response?.data) {
      lastRunStatus.value = response.data.lastRunStatus || "NEVER";
      lastRunAt.value = response.data.lastRunAt || "";
      supported.value = response.data.supported !== false;
      unsupportedReason.value = response.data.unsupportedReason || "";
      if (lastRunStatus.value === "RUNNING") {
        running.value = true;
      } else if (running.value && eventSource === null) {
        running.value = false;
      }
    }
  } catch {
    // Ignore transient polling failures.
  }
}

async function saveConfig() {
  if (!supported.value) {
    ElMessage.warning(unsupportedReason.value || "当前环境不支持系统自更新");
    return;
  }
  if (config.value.enabled && !config.value.cronExpr?.trim()) {
    ElMessage.warning("请填写 Cron 表达式");
    return;
  }

  saving.value = true;
  try {
    await saveUpdateConfig(config.value);
    ElMessage.success("配置已保存");
    await loadStatus();
  } catch (error) {
    ElMessage.error(error?.message || "保存失败");
  } finally {
    saving.value = false;
  }
}

async function confirmTrigger() {
  if (!supported.value) {
    ElMessage.warning(unsupportedReason.value || "当前环境不支持系统自更新");
    return;
  }

  try {
    await ElMessageBox.confirm(
      "将立即拉取远程仓库并重新构建 Docker 环境，期间服务可能短暂中断，确定继续？",
      "确认触发更新",
      {
        confirmButtonText: "确定",
        cancelButtonText: "取消",
        type: "warning",
      },
    );
    triggerUpdate();
  } catch {
    // User cancelled.
  }
}

function triggerUpdate() {
  if (eventSource) {
    eventSource.close();
    eventSource = null;
  }

  logs.value = [];
  running.value = true;
  lastRunStatus.value = "RUNNING";

  const userStore = useUserStore();
  const token = userStore.token || "";
  const streamUrl = environment.resolveApiUrl("/api/ops/update/trigger");
  eventSource = new EventSource(
    `${streamUrl}${token ? `?token=${encodeURIComponent(token)}` : ""}`,
  );

  eventSource.onmessage = (event) => {
    if (!event.data) return;
    logs.value.push(event.data);
    nextTick(() => {
      if (logBox.value) {
        logBox.value.scrollTop = logBox.value.scrollHeight;
      }
    });
  };

  eventSource.addEventListener("done", async (event) => {
    const data = event.data || "";
    logs.value.push(data);
    eventSource?.close();
    eventSource = null;
    running.value = false;
    lastRunStatus.value = data.startsWith("UPDATE_SUCCESS")
      ? "SUCCESS"
      : "FAILED";
    await loadStatus();
  });

  eventSource.onerror = async () => {
    eventSource?.close();
    eventSource = null;
    running.value = false;
    await loadStatus();
  };
}

function clearLogs() {
  logs.value = [];
}

async function loadHistory() {
  try {
    const response = await getDeployHistory(50);
    if (response?.data) {
      deployHistory.value = response.data;
    }
  } catch {
    // Ignore transient failures.
  }
}

function eventTagType(event) {
  return { SUCCESS: 'success', ROLLBACK: 'warning', ERROR: 'danger', SKIP: 'info' }[event] || 'info';
}

onMounted(() => {
  loadConfig();
  loadStatus();
  loadHistory();
  pollTimer = setInterval(loadStatus, 5000);
});

onUnmounted(() => {
  if (pollTimer) clearInterval(pollTimer);
  if (eventSource) {
    eventSource.close();
    eventSource = null;
  }
});
</script>

<style scoped>
.update-container {
  padding: 20px;
}

.support-alert,
.config-card,
.trigger-card,
.log-card {
  margin-bottom: 20px;
}

.cron-hint {
  margin-top: 4px;
  font-size: 12px;
  color: #909399;
}

.trigger-row {
  display: flex;
  align-items: center;
  gap: 20px;
  flex-wrap: wrap;
}

.status-info {
  display: flex;
  align-items: center;
  gap: 8px;
}

.status-label {
  color: #606266;
  font-size: 14px;
}

.last-time {
  color: #909399;
  font-size: 13px;
}

.log-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.log-area {
  background: #1e1e1e;
  color: #d4d4d4;
  padding: 12px;
  border-radius: 4px;
  font-family: "Courier New", Courier, monospace;
  font-size: 13px;
  line-height: 1.6;
  max-height: 400px;
  overflow-y: auto;
  min-height: 80px;
}

.log-placeholder {
  color: #606060;
  font-style: italic;
}

.log-line {
  white-space: pre-wrap;
  word-break: break-all;
}
</style>
