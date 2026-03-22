// 监控 API 已迁移至 ops.js，此文件为向后兼容的重导出
export {
  getRealTimeMetrics,
  getSystemMetrics,
  getHourlyStats,
  getDailyStats,
  getTopPages,
  getAiCallsRealtime,
  getAiCallsDaily,
  getAiCallsRecent
} from './ops'
