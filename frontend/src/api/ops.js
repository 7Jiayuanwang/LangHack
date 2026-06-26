import request from "@/utils/request";

// ========== 数据备份管理 ==========

export function getBackupList(params) {
  return request({
    url: "/api/ops/backup/list",
    method: "get",
    params,
  });
}

export function createBackupApi(mode = "FULL") {
  return request({
    url: "/api/ops/backup/create",
    method: "post",
    params: { mode },
  });
}

export function importBackupApi(data) {
  return request({
    url: "/api/ops/backup/import",
    method: "post",
    data,
    timeout: 3600000,  // 1 小时，支持大备份文件上传
  });
}

export function getBackupDetail(id) {
  return request({
    url: `/api/ops/backup/${id}`,
    method: "get",
  });
}

export function getBackupPreview(id, params) {
  return request({
    url: `/api/ops/backup/${id}/preview`,
    method: "get",
    params,
  });
}

export function getDownloadToken(id) {
  return request({
    url: `/api/ops/backup/${id}/download-token`,
    method: "post",
  })
}

export function downloadBackupApi(id, onDownloadProgress) {
  return request({
    url: `/api/ops/backup/${id}/download`,
    method: "get",
    responseType: "blob",
    onDownloadProgress,
    timeout: 3600000,  // 1 小时，支持大备份文件下载
  });
}

export function deleteBackupApi(id) {
  return request({
    url: `/api/ops/backup/${id}`,
    method: "delete",
  });
}

export function restoreBackupApi(id, data) {
  return request({
    url: `/api/ops/backup/${id}/restore`,
    method: "post",
    data,
    timeout: 3600000,  // 1 小时，支持大备份文件恢复
  });
}

export function getBackupStats() {
  return request({
    url: "/api/ops/backup/stats",
    method: "get",
  });
}

export function getBackupConfig() {
  return request({
    url: "/api/ops/backup/config",
    method: "get",
  });
}

export function updateBackupConfig(data) {
  return request({
    url: "/api/ops/backup/config",
    method: "put",
    data,
  });
}

export function triggerBackupCleanup() {
  return request({
    url: "/api/ops/backup/cleanup",
    method: "post",
  });
}

export function getBackupOperationLogs(params) {
  return request({
    url: "/api/ops/backup/logs",
    method: "get",
    params,
  });
}

export function createCategoryBackupApi(category) {
  return request({
    url: "/api/ops/backup/category",
    method: "post",
    params: { category },
  });
}

// ========== 系统监控 ==========

export function getRealTimeMetrics() {
  return request({
    url: "/api/ops/monitoring/realtime",
    method: "get",
  });
}

export function getSystemMetrics() {
  return request({
    url: "/api/ops/monitoring/system",
    method: "get",
  });
}

export function getHourlyStats(hours = 24) {
  return request({
    url: "/api/ops/monitoring/hourly",
    method: "get",
    params: { hours },
  });
}

export function getDailyStats(days = 30) {
  return request({
    url: "/api/ops/monitoring/daily",
    method: "get",
    params: { days },
  });
}

export function getTopPages(date) {
  return request({
    url: "/api/ops/monitoring/top-pages",
    method: "get",
    params: date ? { date } : {},
  });
}

export function getAiCallsRealtime() {
  return request({
    url: "/api/ops/monitoring/ai-calls/realtime",
    method: "get",
  });
}

export function getAiCallsDaily(days = 30) {
  return request({
    url: "/api/ops/monitoring/ai-calls/daily",
    method: "get",
    params: { days },
  });
}

export function getAiCallsRecent(limit = 50) {
  return request({
    url: "/api/ops/monitoring/ai-calls/recent",
    method: "get",
    params: { limit },
  });
}

export function getApplicationLogs(params = {}) {
  return request({
    url: "/api/ops/monitoring/app-logs",
    method: "get",
    params,
  });
}

export function downloadApplicationLogs(params = {}) {
  return request({
    url: "/api/ops/monitoring/app-logs/download",
    method: "get",
    params,
    responseType: "blob",
  });
}

// ========== 数据运维 ==========

export function cleanupData() {
  return request({
    url: "/api/ops/cleanup-data",
    method: "post",
  });
}

export function getDataStats() {
  return request({
    url: "/api/ops/data-stats",
    method: "get",
  });
}

// ========== 系统更新 ==========

export function getUpdateConfig() {
  return request({
    url: "/api/ops/update/config",
    method: "get",
  });
}

export function saveUpdateConfig(data) {
  return request({
    url: "/api/ops/update/config",
    method: "post",
    data,
  });
}

export function getUpdateStatus() {
  return request({
    url: "/api/ops/update/status",
    method: "get",
  });
}

export function getDeployHistory(limit = 50) {
  return request({
    url: "/api/ops/update/deploy-history",
    method: "get",
    params: { limit },
  });
}
// 热部署触发用原生 EventSource，不走 axios
// 调用方式：new EventSource(`/api/ops/update/trigger?branch=...&skipBuild=...&dryRun=...&token=...`)

// ========== 提权 ==========

export function elevate(data) {
  return request({
    url: "/api/auth/elevate",
    method: "post",
    data,
  });
}

export function revokeElevation() {
  return request({
    url: "/api/auth/revoke-elevation",
    method: "post",
  });
}

export function getDatabasePoolMetrics() {
  return request({ url: '/api/ops/monitoring/db-pool', method: 'get' })
}

export function getTopUserRequests(limit = 20) {
  return request({ url: '/api/ops/monitoring/user-requests/top', method: 'get', params: { limit } })
}

// ========== 访问日志可视化 ==========

export function getAccessLogs(params) {
  return request({ url: '/api/ops/monitoring/access-logs', method: 'get', params })
}

export function getAccessLogStats(params) {
  return request({ url: '/api/ops/monitoring/access-logs/stats', method: 'get', params })
}

export function getAccessTrend(params) {
  return request({ url: '/api/ops/monitoring/access-logs/trend', method: 'get', params })
}

export function getResponseTimeDistribution(params) {
  return request({ url: '/api/ops/monitoring/access-logs/response-time-distribution', method: 'get', params })
}

export function getLoginStats(params) {
  return request({ url: '/api/ops/monitoring/access-logs/login-stats', method: 'get', params })
}

export function downloadAccessLogs(params) {
  return request({
    url: '/api/ops/monitoring/access-logs/download',
    method: 'get',
    params,
    responseType: 'blob'
  })
}

export function downloadLoginStats(params) {
  return request({
    url: '/api/ops/monitoring/access-logs/login-stats/download',
    method: 'get',
    params,
    responseType: 'blob'
  })
}
