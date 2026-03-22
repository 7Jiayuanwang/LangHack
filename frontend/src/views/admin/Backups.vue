<template>
  <div class="backups-management">
    <div class="header">
      <h2>数据备份</h2>
      <div class="actions">
        <el-dropdown split-button type="primary" :loading="creating" :disabled="creating" @click="handleCreateBackup('FULL')" @command="handleCreateBackup">
          <el-icon><Upload /></el-icon>
          全量备份
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="FULL">全量备份</el-dropdown-item>
              <el-dropdown-item command="INCREMENTAL">增量备份</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
        <el-button @click="loadBackups">
          <el-icon><Refresh /></el-icon>
          刷新
        </el-button>
        <el-button type="warning" @click="handleCleanup">
          <el-icon><Delete /></el-icon>
          清理过期
        </el-button>
        <el-button type="info" @click="openLogsDrawer">
          <el-icon><Document /></el-icon>
          操作日志
        </el-button>
        <el-button type="success" @click="openConfigDrawer">
          <el-icon><Setting /></el-icon>
          配置
        </el-button>
      </div>
    </div>

    <!-- 统计卡片 -->
    <div class="stats-cards">
      <div class="stat-card">
        <div class="stat-value">{{ stats.totalCount || 0 }}</div>
        <div class="stat-label">备份总数</div>
      </div>
      <div class="stat-card">
        <div class="stat-value">{{ stats.successCount || 0 }}</div>
        <div class="stat-label">成功备份</div>
      </div>
      <div class="stat-card">
        <div class="stat-value">{{ formatFileSize(stats.totalSize || 0) }}</div>
        <div class="stat-label">占用空间</div>
      </div>
      <div class="stat-card">
        <div class="stat-value">{{ formatLastTime(stats.lastBackupTime) }}</div>
        <div class="stat-label">最近备份</div>
      </div>
      <div class="stat-card">
        <div class="stat-value">
          <el-tag :type="stats.backupEnabled ? 'success' : 'danger'" size="small">
            {{ stats.backupEnabled ? '已启用' : '已禁用' }}
          </el-tag>
        </div>
        <div class="stat-label">自动备份</div>
      </div>
      <div class="stat-card">
        <div class="stat-value">
          <el-tag :type="stats.incrementalEnabled ? 'success' : 'info'" size="small">
            {{ stats.incrementalEnabled ? '已启用' : '未启用' }}
          </el-tag>
        </div>
        <div class="stat-label">增量备份</div>
      </div>
    </div>

    <!-- 筛选栏 -->
    <div class="search-bar">
      <el-form :inline="true" class="search-form">
        <el-form-item label="备份类型">
          <el-select v-model="filterType" placeholder="全部类型" clearable style="width: 130px" @change="handleFilter">
            <el-option label="手动备份" value="MANUAL" />
            <el-option label="定时备份" value="SCHEDULED" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="filterStatus" placeholder="全部状态" clearable style="width: 130px" @change="handleFilter">
            <el-option label="进行中" value="IN_PROGRESS" />
            <el-option label="成功" value="SUCCESS" />
            <el-option label="失败" value="FAILED" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button @click="resetFilter">
            <el-icon><RefreshLeft /></el-icon>
            重置
          </el-button>
        </el-form-item>
      </el-form>
    </div>

    <!-- 备份列表 -->
    <div class="backups-table">
      <el-table
        v-loading="loading"
        :data="backups"
        stripe
        border
        style="width: 100%"
        empty-text="暂无备份记录"
      >
        <el-table-column prop="filename" label="文件名" min-width="220" show-overflow-tooltip />
        <el-table-column prop="fileSize" label="大小" width="100">
          <template #default="{ row }">
            {{ formatFileSize(row.fileSize || 0) }}
          </template>
        </el-table-column>
        <el-table-column prop="backupType" label="类型" width="100">
          <template #default="{ row }">
            <el-tag :type="row.backupType === 'MANUAL' ? '' : 'success'" size="small">
              {{ row.backupType === 'MANUAL' ? '手动' : '定时' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="backupMode" label="模式" width="100">
          <template #default="{ row }">
            <el-tag :type="row.backupMode === 'INCREMENTAL' ? 'warning' : 'primary'" size="small">
              {{ row.backupMode === 'INCREMENTAL' ? '增量' : '全量' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="getStatusType(row.status)" size="small">
              {{ getStatusText(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdBy" label="创建者" width="100" show-overflow-tooltip />
        <el-table-column prop="createdTime" label="创建时间" width="170">
          <template #default="{ row }">
            {{ formatTime(row.createdTime) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="row.status === 'SUCCESS'"
              type="primary"
              size="small"
              @click="handleDownload(row)"
            >
              下载
            </el-button>
            <el-button
              v-if="row.status === 'SUCCESS'"
              type="warning"
              size="small"
              @click="handleRestore(row)"
            >
              恢复
            </el-button>
            <el-button
              v-if="row.status !== 'IN_PROGRESS'"
              type="danger"
              size="small"
              @click="handleDelete(row)"
            >
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
      <div class="pagination">
        <el-pagination
          v-model:current-page="pagination.current"
          v-model:page-size="pagination.size"
          :page-sizes="[10, 20, 50]"
          :total="pagination.total"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="handleSizeChange"
          @current-change="handleCurrentChange"
        />
      </div>
    </div>

    <!-- 恢复确认对话框 -->
    <el-dialog
      v-model="restoreDialogVisible"
      title="恢复数据库"
      width="min(500px, 90%)"
      :close-on-click-modal="false"
    >
      <div class="restore-warning">
        <el-alert
          title="危险操作"
          type="error"
          :closable="false"
          show-icon
        >
          <template #default>
            <p>恢复数据库将覆盖当前所有数据，此操作不可撤销！</p>
            <p>备份文件：<strong>{{ restoreRecord?.filename }}</strong></p>
            <p v-if="restoreRecord?.backupMode === 'INCREMENTAL'" style="color: #E6A23C; font-weight: bold;">
              ⚠ 这是增量备份，请确保已先恢复其基准全量备份（ID: {{ restoreRecord?.baseBackupId || '未知' }}）
            </p>
          </template>
        </el-alert>
        <div class="confirm-input">
          <p>请输入数据库名称以确认恢复操作：</p>
          <el-input
            v-model="confirmDbName"
            placeholder="请输入数据库名称"
            @keyup.enter="confirmRestore"
          />
        </div>
      </div>
      <template #footer>
        <el-button @click="restoreDialogVisible = false">取消</el-button>
        <el-button
          type="danger"
          :loading="restoring"
          :disabled="!confirmDbName.trim()"
          @click="confirmRestore"
        >
          确认恢复
        </el-button>
      </template>
    </el-dialog>

    <!-- 操作日志抽屉 -->
    <el-drawer
      v-model="logsDrawerVisible"
      title="操作日志"
      direction="rtl"
      size="700px"
    >
      <div class="logs-filter">
        <el-select v-model="logFilterType" placeholder="全部类型" clearable style="width: 160px" @change="handleLogFilter">
          <el-option label="创建备份" value="BACKUP_CREATE" />
          <el-option label="删除备份" value="BACKUP_DELETE" />
          <el-option label="恢复备份" value="BACKUP_RESTORE" />
          <el-option label="下载备份" value="BACKUP_DOWNLOAD" />
          <el-option label="清理备份" value="BACKUP_CLEANUP" />
          <el-option label="定时备份" value="BACKUP_SCHEDULED" />
          <el-option label="中断恢复" value="BACKUP_INTERRUPTED" />
        </el-select>
        <el-button @click="logFilterType = ''; handleLogFilter()">重置</el-button>
      </div>
      <el-table
        v-loading="logsLoading"
        :data="operationLogs"
        stripe
        border
        style="width: 100%"
        empty-text="暂无操作日志"
      >
        <el-table-column prop="operator" label="操作人" width="90" />
        <el-table-column prop="operationType" label="操作类型" width="120">
          <template #default="{ row }">
            <el-tag :type="getLogTypeTag(row.operationType)" size="small">
              {{ getLogTypeText(row.operationType) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="targetName" label="目标文件" min-width="160" show-overflow-tooltip />
        <el-table-column prop="detail" label="详情" min-width="140" show-overflow-tooltip />
        <el-table-column prop="status" label="结果" width="80">
          <template #default="{ row }">
            <el-tag :type="row.status === 'SUCCESS' ? 'success' : 'danger'" size="small">
              {{ row.status === 'SUCCESS' ? '成功' : '失败' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="ipAddress" label="IP" width="120" show-overflow-tooltip />
        <el-table-column prop="createdTime" label="时间" width="160">
          <template #default="{ row }">
            {{ formatTime(row.createdTime) }}
          </template>
        </el-table-column>
      </el-table>
      <div class="pagination" style="margin-top: 12px;">
        <el-pagination
          v-model:current-page="logPagination.current"
          v-model:page-size="logPagination.size"
          :page-sizes="[10, 20, 50]"
          :total="logPagination.total"
          layout="total, sizes, prev, pager, next"
          @size-change="handleLogSizeChange"
          @current-change="handleLogPageChange"
        />
      </div>
    </el-drawer>

    <!-- 备份配置抽屉 -->
    <el-drawer
      v-model="configDrawerVisible"
      title="备份配置"
      size="400px"
    >
      <el-form :model="configForm" label-position="top">
        <el-form-item label="自动备份">
          <el-switch v-model="configForm.autoBackupEnabled" />
        </el-form-item>

        <el-form-item label="备份时间">
          <div style="display: flex; gap: 10px;">
            <el-select v-model="configForm.backupHour" placeholder="小时" style="width: 50%;">
              <el-option v-for="h in 24" :key="h-1" :label="(h-1) + '时'" :value="h-1" />
            </el-select>
            <el-select v-model="configForm.backupMinute" placeholder="分钟" style="width: 50%;">
              <el-option v-for="m in [0, 15, 30, 45]" :key="m" :label="m + '分'" :value="m" />
            </el-select>
          </div>
        </el-form-item>

        <el-form-item label="增量备份">
          <el-switch v-model="configForm.incrementalEnabled" />
        </el-form-item>

        <el-form-item v-if="configForm.incrementalEnabled" label="全量备份间隔(天)">
          <el-input-number v-model="configForm.fullBackupIntervalDays" :min="1" :max="30" />
        </el-form-item>

        <el-form-item label="保留天数">
          <el-input-number v-model="configForm.retentionDays" :min="1" :max="365" />
        </el-form-item>

        <el-form-item label="最大备份数">
          <el-input-number v-model="configForm.maxCount" :min="1" :max="200" />
        </el-form-item>

        <el-form-item>
          <el-button type="primary" :loading="savingConfig" @click="handleSaveConfig">保存配置</el-button>
        </el-form-item>
      </el-form>
    </el-drawer>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, onBeforeUnmount } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Upload, Refresh, Delete, RefreshLeft, Document, Setting } from '@element-plus/icons-vue'
import {
  getBackupList,
  createBackupApi,
  downloadBackupApi,
  deleteBackupApi,
  restoreBackupApi,
  getBackupStats as fetchBackupStats,
  getBackupConfig,
  updateBackupConfig,
  triggerBackupCleanup,
  getBackupOperationLogs
} from '@/api/admin'

// State
const backups = ref([])
const stats = ref({})
const loading = ref(false)
const creating = ref(false)
const restoring = ref(false)
const filterType = ref('')
const filterStatus = ref('')
const restoreDialogVisible = ref(false)
const restoreRecord = ref(null)
const confirmDbName = ref('')
let pollTimer = null

// Config drawer state
const configDrawerVisible = ref(false)
const savingConfig = ref(false)
const configForm = reactive({
  autoBackupEnabled: true,
  backupHour: 2,
  backupMinute: 0,
  incrementalEnabled: false,
  fullBackupIntervalDays: 7,
  retentionDays: 30,
  maxCount: 50
})

const pagination = reactive({
  current: 1,
  size: 10,
  total: 0
})

// Load data
const loadBackups = async () => {
  loading.value = true
  try {
    const params = {
      page: pagination.current,
      size: pagination.size
    }
    if (filterType.value) params.backupType = filterType.value
    if (filterStatus.value) params.status = filterStatus.value

    const response = await getBackupList(params)
    if (response.code === 200) {
      backups.value = response.data?.records || []
      pagination.total = typeof response.data?.total === 'number' ? response.data.total : 0
    }
  } catch (error) {
    ElMessage.error('加载备份列表失败')
  } finally {
    loading.value = false
  }
}

const loadStats = async () => {
  try {
    const response = await fetchBackupStats()
    if (response.code === 200) {
      stats.value = (response.data && typeof response.data === 'object') ? response.data : {}
    }
  } catch (error) {
    console.error('Load backup stats error:', error)
    stats.value = {}
  }
}

// Create backup
const handleCreateBackup = async (mode) => {
  if (!['FULL', 'INCREMENTAL'].includes(mode)) return
  const modeLabel = mode === 'INCREMENTAL' ? '增量' : '全量'
  try {
    await ElMessageBox.confirm(`确认创建${modeLabel}数据库备份？`, `${modeLabel}备份`, {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'info'
    })
  } catch {
    return
  }

  creating.value = true
  try {
    const response = await createBackupApi(mode)
    if (response.code === 200) {
      ElMessage.success(`${modeLabel}备份任务已创建，请稍候刷新查看结果`)
      if (pollTimer) clearTimeout(pollTimer)
      pollTimer = setTimeout(() => {
        loadBackups()
        loadStats()
      }, 3000)
    } else {
      ElMessage.error(response.message || '创建备份失败')
    }
  } catch (error) {
    ElMessage.error('创建备份失败')
  } finally {
    creating.value = false
  }
}

// Download backup
const handleDownload = async (row) => {
  try {
    const response = await downloadBackupApi(row.id)
    const blobData = response.data || response
    const blob = blobData instanceof Blob
      ? blobData
      : new Blob([blobData], { type: 'application/octet-stream' })

    // Check if the "blob" is actually a JSON error response
    if (blob.type && blob.type.includes('application/json')) {
      try {
        const text = await blob.text()
        const json = JSON.parse(text)
        ElMessage.error(json.message || '下载备份文件失败')
      } catch {
        ElMessage.error('下载备份文件失败')
      }
      return
    }
    if (blob.size === 0) {
      ElMessage.error('下载备份文件失败：文件为空')
      return
    }

    const url = window.URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = row.filename || `backup-${row.id}.sql`
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    window.URL.revokeObjectURL(url)
    ElMessage.success('下载开始')
  } catch (error) {
    console.error('Download backup failed:', error)
    ElMessage.error('下载备份文件失败')
  }
}

// Delete backup
const handleDelete = async (row) => {
  try {
    await ElMessageBox.confirm(
      `确认删除备份 "${row.filename}"？此操作将同时删除备份文件，不可恢复。`,
      '删除备份',
      {
        confirmButtonText: '确定删除',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )
  } catch {
    return
  }

  try {
    const response = await deleteBackupApi(row.id)
    if (response.code === 200) {
      ElMessage.success('备份已删除')
      loadBackups()
      loadStats()
    } else {
      ElMessage.error(response.message || '删除失败')
    }
  } catch (error) {
    ElMessage.error('删除备份失败')
  }
}

// Restore backup
const handleRestore = (row) => {
  restoreRecord.value = row
  confirmDbName.value = ''
  restoreDialogVisible.value = true
}

const confirmRestore = async () => {
  if (!confirmDbName.value.trim()) {
    ElMessage.warning('请输入数据库名称')
    return
  }

  restoring.value = true
  try {
    const response = await restoreBackupApi(restoreRecord.value.id, {
      confirmDatabaseName: confirmDbName.value.trim()
    })
    if (response.code === 200) {
      ElMessage.success('数据库恢复成功')
      restoreDialogVisible.value = false
      loadBackups()
      loadStats()
    } else {
      ElMessage.error(response.message || '恢复失败')
    }
  } catch (error) {
    ElMessage.error('恢复数据库失败')
  } finally {
    restoring.value = false
  }
}

// Cleanup
const handleCleanup = async () => {
  try {
    await ElMessageBox.confirm('确认清理过期备份？将删除超过保留期限或超出数量限制的备份。', '清理备份', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
  } catch {
    return
  }

  try {
    const response = await triggerBackupCleanup()
    if (response.code === 200) {
      ElMessage.success(response.message || '清理完成')
      loadBackups()
      loadStats()
    } else {
      ElMessage.error(response.message || '清理失败')
    }
  } catch (error) {
    ElMessage.error('清理备份失败')
  }
}

// Filters
const handleFilter = () => {
  pagination.current = 1
  loadBackups()
}

const resetFilter = () => {
  filterType.value = ''
  filterStatus.value = ''
  pagination.current = 1
  loadBackups()
}

// Pagination
const handleSizeChange = (size) => {
  pagination.size = size
  pagination.current = 1
  loadBackups()
}

const handleCurrentChange = (page) => {
  pagination.current = page
  loadBackups()
}

// Formatters
const formatFileSize = (bytes) => {
  if (!bytes || bytes === 0) return '0 B'
  const units = ['B', 'KB', 'MB', 'GB']
  let i = 0
  let size = bytes
  while (size >= 1024 && i < units.length - 1) {
    size /= 1024
    i++
  }
  return size.toFixed(i === 0 ? 0 : 1) + ' ' + units[i]
}

const formatTime = (time) => {
  if (!time) return '-'
  if (Array.isArray(time)) {
    const [y = 0, m = 1, d = 1, h = 0, min = 0, s = 0] = time
    return `${y}-${String(m).padStart(2, '0')}-${String(d).padStart(2, '0')} ${String(h).padStart(2, '0')}:${String(min).padStart(2, '0')}:${String(s || 0).padStart(2, '0')}`
  }
  return String(time).replace('T', ' ').substring(0, 19)
}

const formatLastTime = (time) => {
  if (!time) return '暂无'
  const formatted = formatTime(time)
  if (formatted === '-') return '暂无'
  return formatted.substring(5, 16)
}

const getStatusType = (status) => {
  const map = { SUCCESS: 'success', FAILED: 'danger', IN_PROGRESS: 'warning' }
  return map[status] || 'info'
}

const getStatusText = (status) => {
  const map = { SUCCESS: '成功', FAILED: '失败', IN_PROGRESS: '进行中' }
  return map[status] || status
}

// Operation Logs
const logsDrawerVisible = ref(false)
const operationLogs = ref([])
const logsLoading = ref(false)
const logFilterType = ref('')
const logPagination = reactive({
  current: 1,
  size: 10,
  total: 0
})

const openLogsDrawer = () => {
  logsDrawerVisible.value = true
  logFilterType.value = ''
  logPagination.current = 1
  loadOperationLogs()
}

const loadOperationLogs = async () => {
  logsLoading.value = true
  try {
    const params = {
      page: logPagination.current,
      size: logPagination.size
    }
    if (logFilterType.value) params.operationType = logFilterType.value
    const response = await getBackupOperationLogs(params)
    if (response.code === 200) {
      operationLogs.value = response.data?.records || []
      logPagination.total = typeof response.data?.total === 'number' ? response.data.total : 0
    }
  } catch (error) {
    ElMessage.error('加载操作日志失败')
  } finally {
    logsLoading.value = false
  }
}

const handleLogFilter = () => {
  logPagination.current = 1
  loadOperationLogs()
}

const handleLogSizeChange = (size) => {
  logPagination.size = size
  logPagination.current = 1
  loadOperationLogs()
}

const handleLogPageChange = (page) => {
  logPagination.current = page
  loadOperationLogs()
}

const getLogTypeText = (type) => {
  const map = {
    BACKUP_CREATE: '创建备份',
    BACKUP_DELETE: '删除备份',
    BACKUP_RESTORE: '恢复备份',
    BACKUP_DOWNLOAD: '下载备份',
    BACKUP_CLEANUP: '清理备份',
    BACKUP_SCHEDULED: '定时备份',
    BACKUP_INTERRUPTED: '中断恢复'
  }
  return map[type] || type
}

const getLogTypeTag = (type) => {
  const map = {
    BACKUP_CREATE: 'success',
    BACKUP_DELETE: 'danger',
    BACKUP_RESTORE: 'warning',
    BACKUP_DOWNLOAD: '',
    BACKUP_CLEANUP: 'info',
    BACKUP_SCHEDULED: 'success',
    BACKUP_INTERRUPTED: 'danger'
  }
  return map[type] || 'info'
}

// Config drawer
const openConfigDrawer = async () => {
  try {
    const response = await getBackupConfig()
    if (response.code === 200 && response.data) {
      const d = response.data
      configForm.autoBackupEnabled = Boolean(d.autoBackupEnabled ?? d.enabled)
      configForm.backupHour = d.backupHour ?? 2
      configForm.backupMinute = d.backupMinute ?? 0
      configForm.incrementalEnabled = Boolean(d.incrementalEnabled)
      configForm.fullBackupIntervalDays = d.fullBackupIntervalDays ?? 7
      configForm.retentionDays = d.retentionDays ?? 30
      configForm.maxCount = d.maxCount ?? 50
    }
  } catch {
    ElMessage.error('加载配置失败')
  }
  configDrawerVisible.value = true
}

const handleSaveConfig = async () => {
  savingConfig.value = true
  try {
    const response = await updateBackupConfig(configForm)
    if (response.code === 200) {
      ElMessage.success('备份配置已保存')
      configDrawerVisible.value = false
      loadStats()
    } else {
      ElMessage.error(response.message || '保存配置失败')
    }
  } catch {
    ElMessage.error('保存配置失败')
  } finally {
    savingConfig.value = false
  }
}

// Init
onMounted(() => {
  loadBackups()
  loadStats()
})

onBeforeUnmount(() => {
  if (pollTimer) {
    clearTimeout(pollTimer)
    pollTimer = null
  }
})
</script>

<style scoped>
.backups-management {
  padding: 0;
}

.header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
  flex-wrap: wrap;
  gap: 12px;
}

.header h2 {
  margin: 0;
  font-size: 20px;
  color: var(--text-primary);
}

.actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.stats-cards {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(120px, 1fr));
  gap: 12px;
  margin-bottom: 16px;
}

.stat-card {
  background: var(--surface-color, #fff);
  border-radius: 12px;
  padding: 16px;
  text-align: center;
  border: 1px solid var(--border-light, #ebeef5);
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
}

.stat-value {
  font-size: 20px;
  font-weight: 700;
  color: var(--accent-color, #409eff);
  margin-bottom: 4px;
  word-break: break-all;
}

.stat-label {
  font-size: 12px;
  color: var(--text-tertiary, #909399);
}

.search-bar {
  margin-bottom: 16px;
  background: var(--surface-color, #fff);
  padding: 12px 16px;
  border-radius: 8px;
  border: 1px solid var(--border-light, #ebeef5);
}

.search-form {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.backups-table {
  background: var(--surface-color, #fff);
  border-radius: 8px;
  overflow: hidden;
  border: 1px solid var(--border-light, #ebeef5);
}

.pagination {
  display: flex;
  justify-content: center;
  padding: 16px;
}

.restore-warning {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.confirm-input {
  margin-top: 8px;
}

.confirm-input p {
  margin-bottom: 8px;
  font-weight: 500;
  color: var(--text-primary, #303133);
}

@media (max-width: 768px) {
  .stats-cards {
    grid-template-columns: repeat(2, 1fr);
  }

  .header {
    flex-direction: column;
    align-items: flex-start;
  }
}

.logs-filter {
  display: flex;
  gap: 8px;
  margin-bottom: 12px;
}
</style>
