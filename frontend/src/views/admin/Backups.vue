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
              <el-dropdown-item divided disabled>分类备份</el-dropdown-item>
              <el-dropdown-item command="category:system_config">系统配置</el-dropdown-item>
              <el-dropdown-item command="category:user_data">用户数据</el-dropdown-item>
              <el-dropdown-item command="category:business_data">业务数据</el-dropdown-item>
              <el-dropdown-item command="category:school_data">学校数据</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
        <el-button type="primary" plain @click="openImportDialog">
          <el-icon><Upload /></el-icon>
          导入备份
        </el-button>
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
        <div class="stat-value">{{ stats.missingFileCount || 0 }}</div>
        <div class="stat-label">异常文件</div>
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
            <el-option label="导入备份" value="IMPORTED" />
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
            <el-tag :type="getBackupTypeTag(row.backupType)" size="small">
              {{ getBackupTypeText(row.backupType) }}
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
        <el-table-column prop="category" label="分类" width="110">
          <template #default="{ row }">
            <el-tag v-if="row.category" type="info" size="small">
              {{ getCategoryLabel(row.category) }}
            </el-tag>
            <span v-else style="color: var(--text-tertiary, #909399); font-size: 12px;">-</span>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="getStatusType(row.status, row)" size="small">
              {{ getStatusText(row.status, row) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="文件" width="110">
          <template #default="{ row }">
            <el-tag v-if="canReadBackup(row)" type="success" size="small">可读取</el-tag>
            <el-tag v-else-if="isSuccessfulBackup(row)" type="danger" size="small">缺失/损坏</el-tag>
            <span v-else style="color: var(--text-tertiary, #909399); font-size: 12px;">-</span>
          </template>
        </el-table-column>
        <el-table-column prop="createdBy" label="创建者" width="100" show-overflow-tooltip />
        <el-table-column prop="createdTime" label="创建时间" width="170">
          <template #default="{ row }">
            {{ formatTime(row.createdTime) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="280" fixed="right">
          <template #default="{ row }">
            <el-button
              size="small"
              @click="handleView(row)"
            >
              查看
            </el-button>
            <el-button
              v-if="isSuccessfulBackup(row)"
              type="primary"
              size="small"
              :disabled="!canReadBackup(row)"
              @click="handleDownload(row)"
            >
              下载
            </el-button>
            <el-button
              v-if="isSuccessfulBackup(row)"
              type="warning"
              size="small"
              :disabled="!canReadBackup(row)"
              @click="handleRestore(row)"
            >
              恢复
            </el-button>
            <el-button
              v-if="row.status !== 'IN_PROGRESS' || isStaleInProgress(row)"
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
            <p v-if="restoreMode === 'full'">恢复数据库将覆盖当前所有数据，此操作不可撤销！</p>
            <p v-else>增量恢复将导入备份数据，已存在的数据将被保留（跳过重复键冲突）。</p>
            <p>备份文件：<strong>{{ restoreRecord?.filename }}</strong></p>
            <p v-if="restoreRecord?.backupMode === 'INCREMENTAL'" style="color: #E6A23C; font-weight: bold;">
              ⚠ 这是增量备份，请确保已先恢复其基准全量备份（ID: {{ restoreRecord?.baseBackupId || '未知' }}）
            </p>
          </template>
        </el-alert>
        <div class="restore-mode-selector">
          <p style="margin-bottom: 8px; font-weight: 600;">恢复模式：</p>
          <el-radio-group v-model="restoreMode">
            <el-radio label="full">
              <span style="font-weight: 600;">全量恢复</span>
              <span style="color: var(--text-tertiary); font-size: 12px; margin-left: 4px;">（覆盖所有数据）</span>
            </el-radio>
            <el-radio label="incremental">
              <span style="font-weight: 600;">增量恢复</span>
              <span style="color: var(--text-tertiary); font-size: 12px; margin-left: 4px;">（保留新增数据）</span>
            </el-radio>
          </el-radio-group>
        </div>
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

    <!-- 导入备份对话框 -->
    <el-dialog
      v-model="importDialogVisible"
      title="导入备份"
      width="min(560px, 92%)"
      :close-on-click-modal="false"
    >
      <el-form label-width="100px" class="import-form">
        <el-form-item label="备份文件">
          <div class="import-file-field">
            <el-upload
              :auto-upload="false"
              :show-file-list="false"
              accept=".sql,.gz"
              :limit="1"
              @change="handleImportFileChange"
            >
              <el-button>选择文件</el-button>
            </el-upload>
            <div class="selected-file-name">{{ importForm.fileName || '未选择文件' }}</div>
          </div>
        </el-form-item>
        <el-form-item label="导入模式">
          <el-select v-model="importForm.mode" style="width: 180px">
            <el-option label="自动识别" value="AUTO" />
            <el-option label="全量" value="FULL" />
            <el-option label="增量" value="INCREMENTAL" />
          </el-select>
        </el-form-item>
        <el-form-item label="分类">
          <el-select v-model="importForm.category" clearable placeholder="自动识别 / 留空" style="width: 220px">
            <el-option label="系统配置" value="system_config" />
            <el-option label="用户数据" value="user_data" />
            <el-option label="业务数据" value="business_data" />
            <el-option label="学校数据" value="school_data" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="importDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="importing" :disabled="!importForm.file" @click="confirmImportBackup">
          导入
        </el-button>
      </template>
    </el-dialog>

    <!-- 备份详情抽屉 -->
    <el-drawer
      v-model="detailDrawerVisible"
      title="备份详情"
      direction="rtl"
      size="720px"
    >
      <div v-loading="detailLoading" class="backup-detail">
        <div v-if="currentDetail" class="detail-section">
          <div class="detail-grid">
            <div><span>文件名：</span>{{ currentDetail.filename }}</div>
            <div><span>状态：</span>{{ getStatusText(currentDetail.status) }}</div>
            <div><span>模式：</span>{{ currentDetail.backupMode === 'INCREMENTAL' ? '增量' : '全量' }}</div>
            <div><span>类型：</span>{{ getBackupTypeText(currentDetail.backupType) }}</div>
            <div><span>分类：</span>{{ currentDetail.category ? getCategoryLabel(currentDetail.category) : '-' }}</div>
            <div><span>文件大小：</span>{{ formatFileSize(currentDetail.fileSize || 0) }}</div>
            <div><span>创建者：</span>{{ currentDetail.createdBy || '-' }}</div>
            <div><span>创建时间：</span>{{ formatTime(currentDetail.createdTime) }}</div>
            <div><span>完成时间：</span>{{ formatTime(currentDetail.completedTime) }}</div>
            <div><span>文件状态：</span>{{ currentDetail.availabilityMessage || (canReadBackup(currentDetail) ? '文件可读' : '-') }}</div>
            <div v-if="currentDetail.baseBackupId"><span>基准备份ID：</span>{{ currentDetail.baseBackupId }}</div>
            <div v-if="currentDetail.errorMessage" class="detail-error"><span>错误信息：</span>{{ currentDetail.errorMessage }}</div>
          </div>
        </div>

        <div class="detail-section">
          <div class="section-header">
            <strong>内容预览</strong>
            <span v-if="previewMeta.skippedLines > 0" class="preview-hint">
              已跳过前 {{ previewMeta.skippedLines }} 行注释和配置
            </span>
            <span v-if="previewMeta.truncated" class="preview-hint">
              已截断显示（共读取 {{ previewMeta.totalLinesRead }} 行，显示 {{ previewMeta.lineCount }} 行）
            </span>
          </div>
          <el-alert
            v-if="previewError"
            type="warning"
            :closable="false"
            show-icon
            :title="previewError"
          />
          <pre v-else class="preview-block">{{ backupPreview || '暂无预览内容' }}</pre>
        </div>
      </div>
    </el-drawer>

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
          <el-option label="导入备份" value="BACKUP_IMPORT" />
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

    <!-- 备份策略配置抽屉 -->
    <el-drawer
      v-model="configDrawerVisible"
      title="备份策略配置"
      size="560px"
      :close-on-click-modal="false"
    >
      <div class="config-panel">
        <!-- 自动备份总开关 -->
        <div class="config-card">
          <div class="card-header">
            <div class="card-header-left">
              <div class="card-icon auto-icon">
                <el-icon :size="20"><Timer /></el-icon>
              </div>
              <div class="card-title-group">
                <h4>自动备份</h4>
                <p class="card-desc">启用后系统将按设定时间自动执行备份任务</p>
              </div>
            </div>
            <el-switch v-model="configForm.autoBackupEnabled" />
          </div>
        </div>

        <!-- 备份时间设置 -->
        <div class="config-card" v-if="configForm.autoBackupEnabled">
          <div class="card-header">
            <div class="card-header-left">
              <div class="card-icon time-icon">
                <el-icon :size="20"><Clock /></el-icon>
              </div>
              <div class="card-title-group">
                <h4>执行时间</h4>
                <p class="card-desc">建议选择凌晨业务低峰时段</p>
              </div>
            </div>
          </div>
          <div class="card-body">
            <div class="time-picker-row">
              <div class="time-picker-item">
                <label>小时</label>
                <el-select v-model="configForm.backupHour" style="width: 100%">
                  <el-option v-for="h in 24" :key="h-1" :label="String(h-1).padStart(2, '0') + ' 时'" :value="h-1" />
                </el-select>
              </div>
              <span class="time-separator">:</span>
              <div class="time-picker-item">
                <label>分钟</label>
                <el-select v-model="configForm.backupMinute" style="width: 100%">
                  <el-option v-for="m in [0, 15, 30, 45]" :key="m" :label="String(m).padStart(2, '0') + ' 分'" :value="m" />
                </el-select>
              </div>
            </div>
            <div class="time-preview">
              <el-icon><InfoFilled /></el-icon>
              每天 {{ String(configForm.backupHour).padStart(2, '0') }}:{{ String(configForm.backupMinute).padStart(2, '0') }} 自动执行备份
            </div>
          </div>
        </div>

        <!-- 全量备份配置 -->
        <div class="config-card" v-if="configForm.autoBackupEnabled">
          <div class="card-header">
            <div class="card-header-left">
              <div class="card-icon full-icon">
                <el-icon :size="20"><FolderOpened /></el-icon>
              </div>
              <div class="card-title-group">
                <h4>全量备份</h4>
                <p class="card-desc">完整备份数据库所有数据，恢复时无需依赖其他备份</p>
              </div>
            </div>
            <el-tag type="success" size="small" effect="light">始终启用</el-tag>
          </div>
          <div class="card-body">
            <div class="info-banner success">
              <el-icon><InfoFilled /></el-icon>
              <span v-if="!configForm.incrementalEnabled">
                当前模式：<strong>每天</strong>执行一次全量备份
              </span>
              <span v-else>
                当前模式：每 <strong>{{ configForm.fullBackupIntervalDays }}</strong> 天执行一次全量备份，其余时间执行增量备份
              </span>
            </div>
          </div>
        </div>

        <!-- 增量备份配置 -->
        <div class="config-card" v-if="configForm.autoBackupEnabled">
          <div class="card-header">
            <div class="card-header-left">
              <div class="card-icon incr-icon">
                <el-icon :size="20"><CopyDocument /></el-icon>
              </div>
              <div class="card-title-group">
                <h4>增量备份</h4>
                <p class="card-desc">仅备份自上次以来变化的数据，节省存储空间和执行时间</p>
              </div>
            </div>
            <el-switch v-model="configForm.incrementalEnabled" />
          </div>
          <div class="card-body" v-if="configForm.incrementalEnabled">
            <div class="interval-setting">
              <div class="interval-header">
                <label class="interval-label">全量备份周期</label>
                <span class="interval-value">{{ configForm.fullBackupIntervalDays }} 天</span>
              </div>
              <p class="interval-desc">设置多少天执行一次全量备份，中间自动执行增量备份</p>
              <el-slider
                v-model="configForm.fullBackupIntervalDays"
                :min="1"
                :max="30"
                :marks="{ 1: '1', 7: '7', 14: '14', 30: '30' }"
                :step="1"
                style="padding: 0 8px;"
              />
            </div>

            <!-- 备份周期预览 -->
            <div class="schedule-preview">
              <h5>
                <el-icon><InfoFilled /></el-icon>
                未来 7 天备份计划预览
              </h5>
              <div class="preview-days">
                <div
                  v-for="(day, index) in schedulePreview"
                  :key="index"
                  class="preview-day"
                  :class="{ 'is-full': day.isFull, 'is-incremental': !day.isFull }"
                >
                  <div class="day-label">{{ day.label }}</div>
                  <div class="day-dot"></div>
                  <div class="day-type">{{ day.isFull ? '全量' : '增量' }}</div>
                </div>
              </div>
              <div class="preview-legend">
                <span class="legend-item"><span class="legend-dot full-dot"></span> 全量备份</span>
                <span class="legend-item"><span class="legend-dot incr-dot"></span> 增量备份</span>
              </div>
            </div>
          </div>
        </div>

        <!-- 存储策略 -->
        <div class="config-card">
          <div class="card-header">
            <div class="card-header-left">
              <div class="card-icon storage-icon">
                <el-icon :size="20"><Coin /></el-icon>
              </div>
              <div class="card-title-group">
                <h4>存储策略</h4>
                <p class="card-desc">超出限制的旧备份将在清理时自动删除</p>
              </div>
            </div>
          </div>
          <div class="card-body">
            <div class="storage-items">
              <div class="storage-item">
                <div class="storage-label">
                  <span class="label-main">保留天数</span>
                  <span class="label-hint">超过此天数的备份将标记为过期</span>
                </div>
                <el-input-number
                  v-model="configForm.retentionDays"
                  :min="1"
                  :max="365"
                  :step="1"
                  controls-position="right"
                />
              </div>
              <div class="storage-item">
                <div class="storage-label">
                  <span class="label-main">最大备份数</span>
                  <span class="label-hint">超出数量时最早的备份将被优先清理</span>
                </div>
                <el-input-number
                  v-model="configForm.maxCount"
                  :min="1"
                  :max="200"
                  :step="5"
                  controls-position="right"
                />
              </div>
            </div>
          </div>
        </div>

        <!-- 保存按钮 -->
        <div class="config-actions">
          <el-button @click="configDrawerVisible = false">取消</el-button>
          <el-button type="primary" :loading="savingConfig" @click="handleSaveConfig">
            <el-icon><Check /></el-icon>
            保存配置
          </el-button>
        </div>
      </div>
    </el-drawer>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onBeforeUnmount } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Upload, Refresh, Delete, RefreshLeft, Document, Setting, Timer, Clock, FolderOpened, CopyDocument, Coin, InfoFilled, Check } from '@element-plus/icons-vue'
import { normalizeDownloadResponse, extractBlobErrorMessage, triggerBlobDownload } from '@/utils/blobDownload'
import {
  getBackupList,
  createBackupApi,
  createCategoryBackupApi,
  importBackupApi,
  getBackupDetail,
  getBackupPreview,
  downloadBackupApi,
  deleteBackupApi,
  restoreBackupApi,
  getBackupStats as fetchBackupStats,
  getBackupConfig,
  updateBackupConfig,
  triggerBackupCleanup,
  getBackupOperationLogs
} from '@/api/ops'

// State
const backups = ref([])
const stats = ref({})
const loading = ref(false)
const creating = ref(false)
const restoring = ref(false)
const importing = ref(false)
const filterType = ref('')
const filterStatus = ref('')
const restoreDialogVisible = ref(false)
const restoreRecord = ref(null)
const restoreMode = ref('full')
const confirmDbName = ref('')
const importDialogVisible = ref(false)
const detailDrawerVisible = ref(false)
const detailLoading = ref(false)
const currentDetail = ref(null)
const backupPreview = ref('')
const previewError = ref('')
const previewMeta = reactive({
  truncated: false,
  lineCount: 0,
  totalLinesRead: 0,
  skippedLines: 0
})
const importForm = reactive({
  file: null,
  fileName: '',
  mode: 'AUTO',
  category: ''
})
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

// 备份计划预览（未来7天）
const schedulePreview = computed(() => {
  const days = []
  const interval = configForm.fullBackupIntervalDays || 7
  const weekdays = ['日', '一', '二', '三', '四', '五', '六']
  const now = new Date()

  for (let i = 0; i < 7; i++) {
    const date = new Date(now)
    date.setDate(date.getDate() + i)
    days.push({
      label: i === 0 ? '今天' : `周${weekdays[date.getDay()]}`,
      isFull: i % interval === 0
    })
  }
  return days
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

const normalizeBackupStatus = (status) => {
  if (!status) return 'UNKNOWN'
  const normalized = String(status).toUpperCase()
  if (normalized === 'COMPLETED') return 'SUCCESS'
  if (normalized === 'RUNNING') return 'IN_PROGRESS'
  return normalized
}

const isSuccessfulBackup = (row) => normalizeBackupStatus(row?.status) === 'SUCCESS'

const canReadBackup = (row) => isSuccessfulBackup(row) && row?.readable !== false && row?.fileAvailable !== false

// IN_PROGRESS records older than 2 hours are considered stale (container not restarted)
const isStaleInProgress = (row) => {
  if (normalizeBackupStatus(row?.status) !== 'IN_PROGRESS') return false
  if (!row?.createdTime) return false
  const created = Array.isArray(row.createdTime)
    ? new Date(row.createdTime[0], row.createdTime[1] - 1, row.createdTime[2], row.createdTime[3] || 0, row.createdTime[4] || 0)
    : new Date(row.createdTime)
  return Date.now() - created.getTime() > 2 * 60 * 60 * 1000
}

const handleView = async (row) => {
  detailDrawerVisible.value = true
  detailLoading.value = true
  currentDetail.value = null
  backupPreview.value = ''
  previewError.value = ''
  previewMeta.truncated = false
  previewMeta.lineCount = 0
  previewMeta.totalLinesRead = 0
  previewMeta.skippedLines = 0

  try {
    const [detailRes, previewRes] = await Promise.allSettled([
      getBackupDetail(row.id),
      getBackupPreview(row.id, { maxLines: 200, maxChars: 50000 })
    ])

    if (detailRes.status === 'fulfilled' && detailRes.value?.code === 200) {
      currentDetail.value = detailRes.value.data
    } else {
      previewError.value = detailRes.status === 'fulfilled'
        ? (detailRes.value?.message || '读取备份详情失败')
        : (detailRes.reason?.message || '读取备份详情失败')
    }

    if (previewRes.status === 'fulfilled' && previewRes.value?.code === 200) {
      backupPreview.value = previewRes.value.data?.preview || ''
      previewMeta.truncated = previewRes.value.data?.truncated === true
      previewMeta.lineCount = previewRes.value.data?.lineCount || 0
      previewMeta.totalLinesRead = previewRes.value.data?.totalLinesRead || 0
      previewMeta.skippedLines = previewRes.value.data?.skippedLines || 0
    } else {
      previewError.value = previewRes.status === 'fulfilled'
        ? (previewRes.value?.message || '读取备份预览失败')
        : (previewRes.reason?.message || '读取备份预览失败')
    }
  } finally {
    detailLoading.value = false
  }
}

// Create backup
const handleCreateBackup = async (mode) => {
  if (typeof mode === 'string' && mode.startsWith('category:')) {
    const category = mode.slice('category:'.length)
    await handleCreateCategoryBackup(category)
    return
  }
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

const CATEGORY_LABELS = {
  system_config: '系统配置',
  user_data: '用户数据',
  business_data: '业务数据',
  school_data: '学校数据'
}

const getCategoryLabel = (cat) => CATEGORY_LABELS[cat] || cat

const getBackupTypeText = (type) => {
  const map = {
    MANUAL: '手动',
    SCHEDULED: '定时',
    IMPORTED: '导入'
  }
  return map[type] || type || '-'
}

const getBackupTypeTag = (type) => {
  const map = {
    MANUAL: '',
    SCHEDULED: 'success',
    IMPORTED: 'info'
  }
  return map[type] ?? 'info'
}

const openImportDialog = () => {
  importDialogVisible.value = true
  importForm.file = null
  importForm.fileName = ''
  importForm.mode = 'AUTO'
  importForm.category = ''
}

const handleImportFileChange = (file) => {
  importForm.file = file.raw || file
  importForm.fileName = file.name || file.raw?.name || ''
}

const confirmImportBackup = async () => {
  if (!importForm.file) {
    ElMessage.warning('请选择要导入的备份文件')
    return
  }

  const formData = new FormData()
  formData.append('file', importForm.file)
  formData.append('mode', importForm.mode)
  if (importForm.category) {
    formData.append('category', importForm.category)
  }

  importing.value = true
  try {
    const response = await importBackupApi(formData)
    if (response.code === 200) {
      ElMessage.success('备份文件已导入')
      importDialogVisible.value = false
      await loadBackups()
      await loadStats()
    } else {
      ElMessage.error(response.message || '导入备份失败')
    }
  } catch (error) {
    ElMessage.error('导入备份失败')
  } finally {
    importing.value = false
  }
}

const handleCreateCategoryBackup = async (category) => {
  const label = getCategoryLabel(category)
  try {
    await ElMessageBox.confirm(`确认创建「${label}」分类备份？`, '分类备份', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'info'
    })
  } catch {
    return
  }

  creating.value = true
  try {
    const response = await createCategoryBackupApi(category)
    if (response.code === 200) {
      ElMessage.success(`「${label}」分类备份任务已创建，请稍候刷新查看结果`)
      if (pollTimer) clearTimeout(pollTimer)
      pollTimer = setTimeout(() => {
        loadBackups()
        loadStats()
      }, 3000)
    } else {
      ElMessage.error(response.message || '创建分类备份失败')
    }
  } catch (error) {
    ElMessage.error('创建分类备份失败')
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
  restoreMode.value = 'full'
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
      confirmDatabaseName: confirmDbName.value.trim(),
      mode: restoreMode.value
    })
    if (response.code === 200) {
      ElMessage.success(response.data || '数据库恢复成功')
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

const getStatusType = (status, row) => {
  if (normalizeBackupStatus(status) === 'IN_PROGRESS' && row && isStaleInProgress(row)) return 'danger'
  const map = { SUCCESS: 'success', FAILED: 'danger', IN_PROGRESS: 'warning' }
  return map[normalizeBackupStatus(status)] || 'info'
}

const getStatusText = (status, row) => {
  if (normalizeBackupStatus(status) === 'IN_PROGRESS' && row && isStaleInProgress(row)) return '超时'
  const map = { SUCCESS: '成功', FAILED: '失败', IN_PROGRESS: '进行中' }
  const normalized = normalizeBackupStatus(status)
  return map[normalized] || normalized
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
    BACKUP_IMPORT: '导入备份',
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
    BACKUP_IMPORT: 'info',
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

/* 备份管理样式 */
.backups-management {
  max-width: 1400px;
  margin: 0 auto;
  padding: 20px;
}

/* PC端大字号适配 */
@media (min-width: 1200px) {
  .backups-management {
    padding: 30px;
  }
  
  .header h2 {
    font-size: 28px;
  }

  .stat-value {
    font-size: 24px;
  }

  .stat-label {
    font-size: 15px;
  }

  :deep(.el-table) {
    font-size: 16px;
  }

  :deep(.el-table .cell) {
    line-height: 1.6;
    padding: 12px 0;
  }

  .search-bar {
    margin-bottom: 25px;
    padding: 24px;
  }
}

.header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24px;
  gap: 16px;
  flex-wrap: wrap;
}

.header h2 {
  margin: 0;
  color: #1e293b;
  font-weight: 700;
}

.actions {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
}

.stats-cards {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(180px, 1fr));
  gap: 16px;
  margin-bottom: 24px;
}

.stat-card {
  background: white;
  border: 1px solid #e2e8f0;
  border-radius: 16px;
  padding: 20px;
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 6px;
  box-shadow: 0 4px 6px rgba(0, 0, 0, 0.02);
  transition: all 0.3s ease;
}

.stat-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 8px 12px rgba(0, 0, 0, 0.05);
  border-color: #6366f1;
}

.stat-value {
  font-size: 20px;
  font-weight: 800;
  color: #1e293b;
  word-break: break-all;
}

.stat-label {
  font-size: 13px;
  color: #64748b;
  font-weight: 500;
}

.search-bar {
  background: #f8fafc;
  padding: 20px;
  border-radius: 16px;
  margin-bottom: 24px;
  border: 1px solid #e2e8f0;
}

.search-form {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
}

.search-form :deep(.el-form-item) {
  margin-bottom: 0;
  margin-right: 0;
}

.import-form {
  padding-top: 8px;
}

.import-file-field {
  display: flex;
  flex-direction: column;
  gap: 10px;
  align-items: flex-start;
}

.selected-file-name {
  font-size: 13px;
  color: #64748b;
  word-break: break-all;
}

.backups-table {
  background: white;
  border-radius: 16px;
  overflow: hidden;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.05);
  border: 1px solid #e2e8f0;
}

.backup-detail {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.detail-section {
  border: 1px solid #e2e8f0;
  border-radius: 14px;
  padding: 16px;
  background: #fff;
}

.detail-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 12px 16px;
  font-size: 14px;
  color: #334155;
}

.detail-grid span {
  color: #64748b;
}

.detail-error {
  grid-column: 1 / -1;
}

.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

.preview-hint {
  font-size: 12px;
  color: #64748b;
}

.preview-block {
  margin: 0;
  padding: 14px;
  min-height: 220px;
  max-height: 460px;
  overflow: auto;
  background: #0f172a;
  color: #e2e8f0;
  border-radius: 12px;
  font-size: 12px;
  line-height: 1.5;
  white-space: pre-wrap;
  word-break: break-word;
}

:deep(.el-table) {
  --el-table-header-bg-color: #f8fafc;
}

.pagination {
  margin-top: 24px;
  display: flex;
  justify-content: center;
  padding-bottom: 16px;
}

/* 响应式适配 */
@media (max-width: 768px) {
  .backups-management {
    padding: 16px;
  }

  .header {
    flex-direction: column;
    align-items: flex-start;
  }

  .actions {
    width: 100%;
  }

  .actions .el-button,
  .actions .el-dropdown {
    flex: 1;
    min-width: 100px;
  }

  .stats-cards {
    grid-template-columns: repeat(2, 1fr);
  }

  .search-form {
    flex-direction: column;
    align-items: stretch;
  }

  .search-form :deep(.el-form-item__content) {
    width: 100%;
  }

  .search-form :deep(.el-select) {
    width: 100% !important;
  }
}

@media (max-width: 480px) {
  .stats-cards {
    grid-template-columns: 1fr;
  }

  .actions {
    display: grid;
    grid-template-columns: 1fr 1fr;
    gap: 8px;
  }

  .actions .el-dropdown,
  .actions .el-button {
    width: 100%;
    margin-left: 0 !important;
  }
}

/* 配置面板样式 */
.config-panel {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.config-card {
  border: 1px solid #e2e8f0;
  border-radius: 16px;
  padding: 20px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.card-header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.card-icon {
  width: 40px;
  height: 40px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.time-picker-row {
  display: flex;
  align-items: center;
  gap: 12px;
}

.preview-days {
  display: flex;
  justify-content: space-between;
  margin-top: 12px;
}

.preview-day {
  text-align: center;
  flex: 1;
}

.day-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  margin: 4px auto;
}

.is-full .day-dot { background: #10b981; }
.is-incremental .day-dot { background: #f59e0b; }

.restore-mode-selector {
  margin: 16px 0;
  padding: 12px;
  background: var(--bg-secondary);
  border-radius: 8px;
}

.restore-mode-selector .el-radio {
  display: block;
  margin: 8px 0;
}

.confirm-input {
  margin-top: 16px;
}

.confirm-input p {
  margin-bottom: 8px;
  font-weight: 600;
}
</style>
