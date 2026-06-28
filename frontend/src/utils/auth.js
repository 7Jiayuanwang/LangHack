/**
 * Token validation and localStorage auth utilities.
 *
 * Centralizes all token read/write/clear logic so that every call site
 * benefits from the same "undefined"/"null"/empty-string guard.
 */

const TOKEN_KEY = 'token'
const REFRESH_TOKEN_KEY = 'refreshToken'
const USER_INFO_KEY = 'userInfo'
const USER_ROLE_KEY = 'userRole'

/**
 * Returns true only when `value` is a non-empty string that is NOT
 * the literal words "undefined" or "null".
 */
export function isValidToken(value) {
  if (typeof value !== 'string') return false
  const trimmed = value.trim()
  if (!trimmed) return false
  if (trimmed === 'undefined' || trimmed === 'null') return false
  return true
}

/**
 * Read the token from localStorage with full validation.
 * Returns the trimmed token string, or '' if invalid / missing.
 */
export function getToken() {
  try {
    const raw = localStorage.getItem(TOKEN_KEY)
    if (raw !== null && isValidToken(raw)) {
      return raw.trim()
    }
    // If we read an invalid sentinel, clean it up immediately.
    if (raw !== null) {
      localStorage.removeItem(TOKEN_KEY)
    }
    return ''
  } catch (e) {
    console.error('Failed to read token from localStorage:', e)
    return ''
  }
}

/**
 * Persist a token to localStorage.
 * Refuses to store falsy / "undefined" / "null" values.
 * Returns true on success, false if the value was rejected or storage failed.
 */
export function setToken(value) {
  if (!isValidToken(value)) {
    console.warn('Attempted to store invalid token:', value)
    return false
  }
  try {
    localStorage.setItem(TOKEN_KEY, value.trim())
    return true
  } catch (e) {
    console.error('Failed to write token to localStorage:', e)
    return false
  }
}

/**
 * Remove the token from localStorage.
 */
export function removeToken() {
  try {
    localStorage.removeItem(TOKEN_KEY)
  } catch (e) {
    console.warn('Failed to remove token from localStorage:', e)
  }
}

/**
 * Read the refresh token from localStorage with full validation.
 * Returns the trimmed token string, or '' if invalid / missing.
 */
export function getRefreshToken() {
  try {
    const raw = localStorage.getItem(REFRESH_TOKEN_KEY)
    if (raw !== null && isValidToken(raw)) {
      return raw.trim()
    }
    if (raw !== null) {
      localStorage.removeItem(REFRESH_TOKEN_KEY)
    }
    return ''
  } catch (e) {
    console.error('Failed to read refresh token from localStorage:', e)
    return ''
  }
}

/**
 * Persist a refresh token to localStorage.
 * Refuses to store falsy / "undefined" / "null" values.
 * Returns true on success, false if the value was rejected or storage failed.
 */
export function setRefreshToken(value) {
  if (!isValidToken(value)) {
    console.warn('Attempted to store invalid refresh token:', value)
    return false
  }
  try {
    localStorage.setItem(REFRESH_TOKEN_KEY, value.trim())
    return true
  } catch (e) {
    console.error('Failed to write refresh token to localStorage:', e)
    return false
  }
}

/**
 * Remove the refresh token from localStorage.
 */
export function removeRefreshToken() {
  try {
    localStorage.removeItem(REFRESH_TOKEN_KEY)
  } catch (e) {
    console.warn('Failed to remove refresh token from localStorage:', e)
  }
}

/**
 * Remove ALL auth-related keys from localStorage in one shot.
 */
export function clearAuthStorage() {
  const keys = [TOKEN_KEY, REFRESH_TOKEN_KEY, USER_INFO_KEY, USER_ROLE_KEY]
  for (const key of keys) {
    try {
      localStorage.removeItem(key)
    } catch (e) {
      console.warn(`Failed to remove ${key} from localStorage:`, e)
    }
  }
}

/**
 * Build the Authorization header value for a valid token,
 * or return null if the token is invalid.
 * Usage:  const auth = getAuthHeader(); if (auth) headers['Authorization'] = auth;
 */
export function getAuthHeader() {
  const token = getToken()
  if (!token) return null
  // Strip any existing "Bearer " prefix to prevent duplication
  const clean = token.replace(/^Bearer\s+/i, '')
  if (!isValidToken(clean)) return null
  return `Bearer ${clean}`
}

/**
 * Check if user is logged in (has valid token)
 */
export function isLoggedIn() {
  return !!getToken()
}

let proactiveRefreshTimer = null

/**
 * 主动刷新 access token：在过期前 10 分钟触发刷新。
 *
 * @param {number} expiresInMs - access token 有效期（毫秒），通常为 7200000（2小时）
 *
 * 调用时机：
 * - 登录成功后（密码登录 / SSO 回调）
 * - 401 被动刷新成功后
 * - 前端 setTokens action 触发时
 */
export function startProactiveRefresh(expiresInMs) {
  stopProactiveRefresh()

  // 提前 10 分钟刷新（600000ms），但最少等待 1 分钟
  const delay = Math.max(expiresInMs - 600000, 60000)

  proactiveRefreshTimer = setTimeout(async () => {
    const refreshToken = getRefreshToken()
    if (!refreshToken) {
      console.warn('No refresh token available for proactive refresh')
      return
    }

    try {
      const { refreshAccessTokenShared } = await import('@/utils/request')
      const data = await refreshAccessTokenShared(refreshToken)
      if (data?.accessToken) {
        startProactiveRefresh(data.expiresIn || 7200000)
        console.log('Proactive token refresh succeeded')
      }
    } catch (error) {
      console.error('Proactive token refresh failed:', error)
      // 失败不重试，等待 401 被动刷新或用户重新登录
    }
  }, delay)
}

/**
 * 停止主动刷新定时器（登出 / 新登录时调用）。
 */
export function stopProactiveRefresh() {
  if (proactiveRefreshTimer !== null) {
    clearTimeout(proactiveRefreshTimer)
    proactiveRefreshTimer = null
  }
}

/**
 * 应用启动时主动刷新一次 refresh token，实现「每次进入页面重置 7 天有效期」。
 * - 无 refresh token（未登录）静默跳过
 * - 网络/后端异常静默忽略，不阻塞应用启动
 */
export async function refreshOnAppStart() {
  const refreshToken = getRefreshToken()
  if (!refreshToken) return
  try {
    // 复用 request.js 的去重锁，避免与 handle401 并发触发「刚黑名单的 token 再次提交」
    const { refreshAccessTokenShared } = await import('@/utils/request')
    const data = await refreshAccessTokenShared(refreshToken)
    if (data?.accessToken) {
      startProactiveRefresh(data.expiresIn || 7200000)
      console.log('Token refreshed on app start')
    }
  } catch (e) {
    console.warn('Token refresh on app start failed (silently ignored):', e)
  }
}
