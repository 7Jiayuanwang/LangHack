import request from '@/utils/request'

/**
 * 获取 SSO 登录 URL
 */
export function getSsoLoginUrl() {
  return request({
    url: '/api/auth/sso/login-url',
    method: 'get'
  })
}

/**
 * 用一次性 code 换取 JWT token
 * 后端回调流程: CAS 302 → /api/auth/sso/callback(GET) → Redis存code → 302 → /sso/callback?code=xxx
 * 前端拿到 code 后调用此接口换取 token
 */
export function exchangeSsoToken(code) {
  return request({
    url: '/api/auth/sso/token',
    method: 'post',
    data: { code },
    skipErrorHandler: true
  })
}

/**
 * 获取 SSO 状态
 */
export function getSsoStatus() {
  return request({
    url: '/api/auth/sso/status',
    method: 'get',
    skipErrorHandler: true
  })
}

/**
 * 获取 SSO 登出 URL（用于退出登录后跳转到 CAS 登出页）
 */
export function getSsoLogoutUrl() {
  return request({
    url: '/api/auth/sso/logout-url',
    method: 'get',
    skipErrorHandler: true
  })
}
