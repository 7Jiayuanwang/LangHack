import request from '@/utils/request'

// 获取单元列表（带分页）
export function getUnits(params) {
  return request({
    url: '/api/units',
    method: 'get',
    params
  })
}

// 根据课程ID获取单元列表
export function getUnitsByCourse(courseId) {
  return request({
    url: `/api/units/course/${courseId}`,
    method: 'get'
  })
}

// 获取单元详情
export function getUnitById(id) {
  return request({
    url: `/api/units/${id}`,
    method: 'get'
  })
}

// 创建单元
export function createUnit(data) {
  return request({
    url: '/api/units',
    method: 'post',
    data
  })
}

// 更新单元
export function updateUnit(id, data) {
  return request({
    url: `/api/units/${id}`,
    method: 'put',
    data
  })
}

// 删除单元
export function deleteUnit(id) {
  return request({
    url: `/api/units/${id}`,
    method: 'delete'
  })
}

// 批量删除单元
export function batchDeleteUnits(ids) {
  return request({
    url: '/api/units/batch',
    method: 'delete',
    data: ids
  })
}
