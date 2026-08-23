import request from '@/utils/request'

// ========== 管理员接口 ==========

export function getAdminNotifications() {
  return request({
    url: '/api/admin/notifications',
    method: 'get'
  })
}

export function createNotification(data) {
  return request({
    url: '/api/admin/notifications',
    method: 'post',
    data
  })
}

export function updateNotification(id, data) {
  return request({
    url: '/api/admin/notifications/' + id,
    method: 'put',
    data
  })
}

export function deleteNotification(id) {
  return request({
    url: '/api/admin/notifications/' + id,
    method: 'delete'
  })
}

// ========== 教师/学生接口 ==========

export function getUnreadNotifications() {
  return request({
    url: '/api/notifications/unread',
    method: 'get'
  })
}

export function markNotificationRead(id) {
  return request({
    url: '/api/notifications/' + id + '/read',
    method: 'post'
  })
}
