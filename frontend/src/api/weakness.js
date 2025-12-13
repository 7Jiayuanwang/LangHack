import request from '@/utils/request'

// 触发薄弱点分析 - 不需要传递studentId,后端从Authentication获取
export function analyzeWeakness() {
  return request({
    url: '/api/student/weakness/analyze',
    method: 'post'
  })
}

// 获取薄弱点列表 - 不需要传递studentId,后端从Authentication获取
export function getWeaknessList() {
  return request({
    url: '/api/student/weakness/list',
    method: 'get'
  })
}

// 获取薄弱点详情
export function getWeaknessDetail(knowledgePointId) {
  return request({
    url: `/api/student/weakness/detail/${knowledgePointId}`,
    method: 'get'
  })
}

// 获取针对性练习推荐
export function getTargetedPractice(knowledgePointId) {
  return request({
    url: `/api/student/weakness/practice/${knowledgePointId}`,
    method: 'get'
  })
}

// 生成学习建议 - 不需要传递studentId,后端从Authentication获取
export function generateLearningSuggestions() {
  return request({
    url: '/api/student/suggestions/generate',
    method: 'post'
  })
}

// 获取学习建议列表 - 不需要传递studentId,后端从Authentication获取
export function getLearningSuggestions() {
  return request({
    url: '/api/student/suggestions',
    method: 'get'
  })
}

// 完成建议
export function completeSuggestion(suggestionId) {
  return request({
    url: `/api/student/suggestions/${suggestionId}/complete`,
    method: 'put'
  })
}

// 获取学习建议详情
export function getSuggestionDetail(suggestionId) {
  return request({
    url: `/api/student/suggestions/${suggestionId}`,
    method: 'get'
  })
}

// 导出薄弱点报告
export function exportWeaknessReport(params) {
  return request({
    url: '/api/student/weakness/export',
    method: 'get',
    params,
    responseType: 'blob'
  })
}
