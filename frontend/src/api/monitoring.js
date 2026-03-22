import request from '@/utils/request'

// ========== 访问监控 ==========

export function getRealTimeMetrics() {
  return request({
    url: '/api/admin/monitoring/realtime',
    method: 'get'
  })
}

export function getSystemMetrics() {
  return request({
    url: '/api/admin/monitoring/system',
    method: 'get'
  })
}

export function getHourlyStats(hours = 24) {
  return request({
    url: '/api/admin/monitoring/hourly',
    method: 'get',
    params: { hours }
  })
}

export function getDailyStats(days = 30) {
  return request({
    url: '/api/admin/monitoring/daily',
    method: 'get',
    params: { days }
  })
}

export function getTopPages(date) {
  return request({
    url: '/api/admin/monitoring/top-pages',
    method: 'get',
    params: date ? { date } : {}
  })
}

// ========== AI调用监控 ==========

export function getAiCallsRealtime() {
  return request({
    url: '/api/admin/monitoring/ai-calls/realtime',
    method: 'get'
  })
}

export function getAiCallsDaily(days = 30) {
  return request({
    url: '/api/admin/monitoring/ai-calls/daily',
    method: 'get',
    params: { days }
  })
}

export function getAiCallsRecent(limit = 50) {
  return request({
    url: '/api/admin/monitoring/ai-calls/recent',
    method: 'get',
    params: { limit }
  })
}
