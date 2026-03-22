import request from '@/utils/request'

export function getUsers(params) {
  return request({
    url: '/api/admin/users',
    method: 'get',
    params
  })
}

export function createUser(data) {
  return request({
    url: '/api/admin/users',
    method: 'post',
    data
  })
}

export function updateUser(id, data) {
  return request({
    url: `/api/admin/users/${id}`,
    method: 'put',
    data
  })
}

export function deleteUser(id) {
  return request({
    url: `/api/admin/users/${id}`,
    method: 'delete'
  })
}

export function importUsers(file) {
  // 文件类型白名单
  const allowedTypes = ['text/csv', 'application/vnd.ms-excel',
    'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet']
  const maxSize = 10 * 1024 * 1024 // 10MB

  // 输入验证
  if (!file) {
    return Promise.reject(new Error('文件不能为空'))
  }

  if (!allowedTypes.includes(file.type)) {
    return Promise.reject(new Error('仅支持 CSV 和 Excel 文件'))
  }

  if (file.size > maxSize) {
    return Promise.reject(new Error('文件大小不能超过 10MB'))
  }

  // 验证文件扩展名（防止 MIME 类型伪造）
  const ext = file.name.split('.').pop().toLowerCase()
  if (!['csv', 'xls', 'xlsx'].includes(ext)) {
    return Promise.reject(new Error('不支持的文件格式'))
  }

  const formData = new FormData()
  formData.append('file', file)
  return request({
    url: '/api/admin/users/import',
    method: 'post',
    data: formData,
    timeout: 60000  // 批量导入超时时间延长到 60 秒
  })
}

export function getUserStats() {
  return request({
    url: '/api/admin/stats',
    method: 'get'
  })
}

export function getCourses(params) {
  return request({
    url: '/api/courses',
    method: 'get',
    params
  })
}

export function getCourseById(id) {
  return request({
    url: `/api/courses/${id}`,
    method: 'get'
  })
}

export function createCourse(data) {
  return request({
    url: '/api/courses',
    method: 'post',
    data
  })
}

export function updateCourse(id, data) {
  return request({
    url: `/api/courses/${id}`,
    method: 'put',
    data
  })
}

export function deleteCourse(id) {
  return request({
    url: `/api/courses/${id}`,
    method: 'delete'
  })
}

export function getCourseStats() {
  return request({
    url: '/api/courses/stats',
    method: 'get'
  })
}

// ========== 课文管理相关 ==========

export function getTexts(params) {
  return request({
    url: '/api/admin/texts',
    method: 'get',
    params
  })
}

export function getTextStats() {
  return request({
    url: '/api/admin/texts/stats',
    method: 'get'
  })
}

export function createText(data) {
  return request({
    url: '/api/admin/texts',
    method: 'post',
    data
  })
}

export function updateText(id, data) {
  return request({
    url: `/api/admin/texts/${id}`,
    method: 'put',
    data
  })
}

export function deleteText(id) {
  return request({
    url: `/api/admin/texts/${id}`,
    method: 'delete'
  })
}

export function toggleTextFeatured(id) {
  return request({
    url: `/api/admin/texts/${id}/featured`,
    method: 'put'
  })
}

export function getTextDetail(textId) {
  return request({
    url: `/api/admin/texts/${textId}`,
    method: 'get'
  })
}

export function getTextsByUnitId(unitId) {
  return request({
    url: `/api/admin/units/${unitId}/texts`,
    method: 'get'
  })
}

export function addTextToUnit(unitId, textId) {
  return request({
    url: `/api/admin/units/${unitId}/texts/${textId}`,
    method: 'post'
  })
}

export function removeTextFromUnit(unitId, textId) {
  return request({
    url: `/api/admin/units/${unitId}/texts/${textId}`,
    method: 'delete'
  })
}

// ========== 单元管理相关 ==========

export function getUnitsByCourseId(courseId) {
  return request({
    url: `/api/admin/courses/${courseId}/units`,
    method: 'get'
  })
}

// ========== 班级管理相关 ==========

export function getClasses(params) {
  return request({
    url: '/api/classes',
    method: 'get',
    params
  })
}

// ========== 数据备份管理相关 ==========

export function getBackupList(params) {
  return request({
    url: '/api/admin/backup/list',
    method: 'get',
    params
  })
}

export function createBackupApi(mode = 'FULL') {
  return request({
    url: '/api/admin/backup/create',
    method: 'post',
    params: { mode }
  })
}

export function getBackupDetail(id) {
  return request({
    url: `/api/admin/backup/${id}`,
    method: 'get'
  })
}

export function downloadBackupApi(id) {
  return request({
    url: `/api/admin/backup/${id}/download`,
    method: 'get',
    responseType: 'blob'
  })
}

export function deleteBackupApi(id) {
  return request({
    url: `/api/admin/backup/${id}`,
    method: 'delete'
  })
}

export function restoreBackupApi(id, data) {
  return request({
    url: `/api/admin/backup/${id}/restore`,
    method: 'post',
    data
  })
}

export function getBackupStats() {
  return request({
    url: '/api/admin/backup/stats',
    method: 'get'
  })
}

export function getBackupConfig() {
  return request({
    url: '/api/admin/backup/config',
    method: 'get'
  })
}

export function updateBackupConfig(data) {
  return request({
    url: '/api/admin/backup/config',
    method: 'put',
    data
  })
}

export function triggerBackupCleanup() {
  return request({
    url: '/api/admin/backup/cleanup',
    method: 'post'
  })
}

export function getBackupOperationLogs(params) {
  return request({
    url: '/api/admin/backup/logs',
    method: 'get',
    params
  })
}