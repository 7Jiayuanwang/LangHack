import request from '@/utils/request'

// 获取平台统计数据
export function getPlatformStats() {
  return request({
    url: '/api/admin/stats/platform',
    method: 'get'
  })
}

// 获取用户统计数据
export function getUserStats() {
  return request({
    url: '/api/admin/stats/users',
    method: 'get'
  })
}

// 获取学习数据统计
export function getLearningStats(params) {
  return request({
    url: '/api/admin/stats/learning',
    method: 'get',
    params
  })
}

// 获取题库统计
export function getQuestionStats() {
  return request({
    url: '/api/admin/stats/questions',
    method: 'get'
  })
}
