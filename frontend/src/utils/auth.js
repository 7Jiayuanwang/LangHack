/**
 * Token validation and localStorage auth utilities.
 *
 * Centralizes all token read/write/clear logic so that every call site
 * benefits from the same "undefined"/"null"/empty-string guard.
 */

const TOKEN_KEY = 'token'
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
 * Remove ALL auth-related keys from localStorage in one shot.
 */
export function clearAuthStorage() {
  const keys = [TOKEN_KEY, USER_INFO_KEY, USER_ROLE_KEY]
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
