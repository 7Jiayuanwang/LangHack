import request from '@/utils/request'

// 获取知识点列表
export function getKnowledgePointList(params) {
  return request({
    url: '/api/knowledge-points',
    method: 'get',
    params
  })
}

// 获取知识点树
export function getKnowledgePointTree(examType) {
  return request({
    url: '/api/knowledge-points/tree',
    method: 'get',
    params: { examType }
  })
}

// 获取知识点详情
export function getKnowledgePointById(id) {
  return request({
    url: `/api/knowledge-points/${id}`,
    method: 'get'
  })
}

// 创建知识点
export function createKnowledgePoint(data) {
  return request({
    url: '/api/knowledge-points',
    method: 'post',
    data
  })
}

// 更新知识点
export function updateKnowledgePoint(id, data) {
  return request({
    url: `/api/knowledge-points/${id}`,
    method: 'put',
    data
  })
}

// 删除知识点
export function deleteKnowledgePoint(id) {
  return request({
    url: `/api/knowledge-points/${id}`,
    method: 'delete'
  })
}
