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

export function logout(token) {
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

  return request(config)
}

export function getUserInfo() {
  return request({
    url: '/api/auth/userinfo',
    method: 'get'
  })
}
