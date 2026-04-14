import request from '@/utils/request'

/**
 * 获取待审批用户列表
 */
export function getPendingApprovals() {
  return request({
    url: '/api/admin/sso/pending-approvals',
    method: 'get'
  })
}

/**
 * 审批用户
 */
export function approveUser(bindingId, approvedRole) {
  return request({
    url: '/api/admin/sso/approve-user',
    method: 'post',
    data: { bindingId, approvedRole }
  })
}

/**
 * 拒绝用户
 */
export function rejectUser(bindingId) {
  return request({
    url: '/api/admin/sso/reject-user',
    method: 'post',
    data: { bindingId }
  })
}

/**
 * 获取所有 SSO 绑定
 */
export function getSsoBindings(provider) {
  return request({
    url: '/api/admin/sso/bindings',
    method: 'get',
    params: { provider }
  })
}

/**
 * 获取 SSO 统计信息
 */
export function getSsoStats() {
  return request({
    url: '/api/admin/sso/stats',
    method: 'get'
  })
}
