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
 * SSO 回调处理
 */
export function ssoCallback(params) {
  return request({
    url: '/api/auth/sso/callback',
    method: 'post',
    data: params
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
