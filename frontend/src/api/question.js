import request from '@/utils/request'

// 获取题目列表
export function getQuestionList(params) {
  return request({
    url: '/api/questions',
    method: 'get',
    params
  })
}

// 获取题目详情
export function getQuestionById(id) {
  return request({
    url: `/api/questions/${id}`,
    method: 'get'
  })
}

// 创建题目
export function createQuestion(data) {
  return request({
    url: '/api/questions',
    method: 'post',
    data
  })
}

// 更新题目
export function updateQuestion(id, data) {
  return request({
    url: `/api/questions/${id}`,
    method: 'put',
    data
  })
}

// 删除题目
export function deleteQuestion(id) {
  return request({
    url: `/api/questions/${id}`,
    method: 'delete'
  })
}

// 批量删除题目
export function batchDeleteQuestions(ids) {
  return request({
    url: '/api/questions/batch',
    method: 'delete',
    data: ids
  })
}

// 根据知识点获取题目
export function getQuestionsByKnowledgePoint(kpId) {
  return request({
    url: `/api/questions/knowledge-point/${kpId}`,
    method: 'get'
  })
}
