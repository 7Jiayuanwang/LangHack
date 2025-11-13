import request from '@/utils/request'

// 获取课文列表
export function getTextList(params) {
  return request({
    url: '/api/texts',
    method: 'get',
    params
  })
}

// 根据课程ID获取课文
export function getTextsByCourse(courseId) {
  return request({
    url: `/api/texts/course/${courseId}`,
    method: 'get'
  })
}

// 根据单元ID获取课文
export function getTextsByUnit(unitId) {
  return request({
    url: `/api/texts/unit/${unitId}`,
    method: 'get'
  })
}

// 获取单个课文
export function getTextById(id) {
  return request({
    url: `/api/texts/${id}`,
    method: 'get'
  })
}

// 创建课文
export function createText(data) {
  return request({
    url: '/api/texts',
    method: 'post',
    data
  })
}

// 更新课文
export function updateText(id, data) {
  return request({
    url: `/api/texts/${id}`,
    method: 'put',
    data
  })
}

// 删除课文
export function deleteText(id) {
  return request({
    url: `/api/texts/${id}`,
    method: 'delete'
  })
}

// 导入Word文档
export function importTextFromWord(formData) {
  return request({
    url: '/api/texts/import',
    method: 'post',
    data: formData,
    headers: {
      'Content-Type': 'multipart/form-data'
    }
  })
}

// 获取课文统计
export function getTextStats() {
  return request({
    url: '/api/texts/stats',
    method: 'get'
  })
}
