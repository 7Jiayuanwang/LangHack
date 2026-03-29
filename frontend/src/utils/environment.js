export const environment = {
  isMobile() {
    if (typeof navigator === 'undefined' || !navigator.userAgent) {
      return false
    }

    return /Android|webOS|iPhone|iPad|iPod|BlackBerry|IEMobile|Opera Mini/i.test(
      navigator.userAgent,
    )
  },

  isCapacitor() {
    return typeof window !== 'undefined' && window.Capacitor !== undefined
  },

  isDevelopment() {
    try {
      return import.meta.env.MODE === 'development'
    } catch (e) {
      return typeof process !== 'undefined' && process.env?.NODE_ENV === 'development'
    }
  },

  normalizeBaseUrl(baseUrl) {
    if (typeof baseUrl !== 'string') {
      return ''
    }

    const trimmed = baseUrl.trim()
    if (!trimmed) {
      return ''
    }

    return trimmed.replace(/\/+$/, '')
  },

  getConfiguredApiBaseUrl() {
    try {
      return this.normalizeBaseUrl(import.meta.env.VITE_API_BASE_URL || '')
    } catch (e) {
      console.warn('Failed to read VITE_API_BASE_URL:', e)
      return ''
    }
  },

  getApiBaseUrl() {
    return this.getConfiguredApiBaseUrl()
  },

  resolveApiUrl(url, configuredBaseUrl = this.getConfiguredApiBaseUrl()) {
    if (typeof url !== 'string' || !url.trim()) {
      return url
    }

    const rawUrl = url.trim()
    const normalizedBaseUrl = this.normalizeBaseUrl(configuredBaseUrl)

    if (rawUrl.startsWith('/api')) {
      return this.joinApiUrl(normalizedBaseUrl, rawUrl)
    }

    if (!this.isAbsoluteUrl(rawUrl)) {
      return rawUrl
    }

    try {
      const parsed = new URL(rawUrl)
      if (!parsed.pathname.startsWith('/api')) {
        return rawUrl
      }

      if (!normalizedBaseUrl) {
        return rawUrl
      }

      const parsedBase = new URL(normalizedBaseUrl, window.location.origin)
      if (parsed.origin === parsedBase.origin) {
        return rawUrl
      }

      if (parsed.origin !== window.location.origin) {
        return rawUrl
      }

      return this.joinApiUrl(
        normalizedBaseUrl,
        parsed.pathname + parsed.search + parsed.hash,
      )
    } catch (e) {
      console.warn('Failed to resolve API URL:', e)
      return rawUrl
    }
  },

  isApiRequestUrl(url, configuredBaseUrl = this.getConfiguredApiBaseUrl()) {
    if (typeof url !== 'string' || !url.trim()) {
      return false
    }

    const rawUrl = url.trim()
    if (rawUrl.startsWith('/api')) {
      return true
    }

    try {
      const parsed = new URL(rawUrl, window.location.origin)
      if (!parsed.pathname.startsWith('/api')) {
        return false
      }

      if (parsed.origin === window.location.origin) {
        return true
      }

      const normalizedBaseUrl = this.normalizeBaseUrl(configuredBaseUrl)
      if (!normalizedBaseUrl) {
        return false
      }

      return parsed.origin === new URL(normalizedBaseUrl, window.location.origin).origin
    } catch (e) {
      console.warn('Failed to inspect API URL:', e)
      return false
    }
  },

  getTutorApiBaseUrl() {
    try {
      return import.meta.env.VITE_TUTOR_API_BASE_URL || '/tutor'
    } catch (e) {
      console.warn('Failed to read VITE_TUTOR_API_BASE_URL:', e)
      return '/tutor'
    }
  },

  getBuildType() {
    try {
      return (typeof __IS_MOBILE_BUILD__ !== 'undefined' && __IS_MOBILE_BUILD__) ? 'mobile' : 'web'
    } catch (e) {
      return 'web'
    }
  },

  isAbsoluteUrl(url) {
    return /^[a-zA-Z][a-zA-Z\d+\-.]*:/.test(url)
  },

  joinApiUrl(baseUrl, path) {
    if (!baseUrl) {
      return path
    }

    if (baseUrl.endsWith('/api') && path === '/api') {
      return baseUrl
    }

    if (baseUrl.endsWith('/api') && path.startsWith('/api/')) {
      return `${baseUrl}${path.substring(4)}`
    }

    return `${baseUrl}${path}`
  },
}
