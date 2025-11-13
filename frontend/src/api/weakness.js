import request from '@/utils/request'

// 触发薄弱点分析
export function analyzeWeakness(data) {
  return request({
    url: '/api/student/weakness/analyze',
    method: 'post',
    data
  })
}

// 获取薄弱点列表
export function getWeaknessList(params) {
  return request({
    url: '/api/student/weakness/list',
    method: 'get',
    params
  })
}

// 获取薄弱点详情
export function getWeaknessDetail(knowledgePointId) {
  return request({
    url: `/api/student/weakness/detail/${knowledgePointId}`,
    method: 'get'
  })
}

// 获取薄弱点统计图表数据
export function getWeaknessChartData(params) {
  return request({
    url: '/api/student/weakness/chart',
    method: 'get',
    params
  })
}

// 获取针对性练习推荐
export function getTargetedPractice(knowledgePointId) {
  return request({
    url: `/api/student/weakness/practice/${knowledgePointId}`,
    method: 'get'
  })
}

// 生成学习建议
export function generateLearningSuggestions(data) {
  return request({
    url: '/api/student/suggestions/generate',
    method: 'post',
    data
  })
}

// 获取学习建议列表
export function getLearningSuggestions(params) {
  return request({
    url: '/api/student/suggestions/list',
    method: 'get',
    params
  })
}

// 完成建议
export function completeSuggestion(suggestionId) {
  return request({
    url: `/api/student/suggestions/${suggestionId}/complete`,
    method: 'post'
  })
}

// 忽略建议
export function ignoreSuggestion(suggestionId) {
  return request({
    url: `/api/student/suggestions/${suggestionId}/ignore`,
    method: 'post'
  })
}

// 获取学习建议详情
export function getSuggestionDetail(suggestionId) {
  return request({
    url: `/api/student/suggestions/${suggestionId}`,
    method: 'get'
  })
}

// 更新建议进度
export function updateSuggestionProgress(suggestionId, data) {
  return request({
    url: `/api/student/suggestions/${suggestionId}/progress`,
    method: 'put',
    data
  })
}

// 获取薄弱点改善趋势
export function getImprovementTrend(params) {
  return request({
    url: '/api/student/weakness/trend',
    method: 'get',
    params
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
