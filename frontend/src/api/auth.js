import request from '@/utils/request'

export function login(data) {
  return request({
    url: '/api/auth/login',
    method: 'post',
    data
  })
}

export function register(data) {
  return request({
    url: '/api/auth/register',
    method: 'post',
    data
  })
}

export function logout(token, refreshToken) {
  const normalizedToken = typeof token === 'string'
    ? token.replace(/^Bearer\s+/i, '').trim()
    : ''

  const config = {
    url: '/api/auth/logout',
    method: 'post'
  }

  if (normalizedToken) {
    config.headers = {
      Authorization: `Bearer ${normalizedToken}`
    }
  }

  // 如果提供了 refreshToken，放入请求 body（后端会一并黑名单）
  if (refreshToken) {
    config.data = { refreshToken }
  }

  return request(config)
}

export function getUserInfo() {
  return request({
    url: '/api/auth/userinfo',
    method: 'get'
  })
}

export function refreshToken(refreshToken) {
  return request({
    url: '/api/auth/refresh',
    method: 'post',
    data: { refreshToken },
    skipErrorHandler: true
  })
}
