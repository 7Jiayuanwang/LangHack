import { ref } from 'vue'
import { getFeatureToggles, getPersonalFeatureToggles } from '@/api/systemConfig'

const CACHE_KEY = 'feature_toggles_cache_v1'
const TTL_MS = 5 * 60 * 1000

const toggles = ref({})
let loaded = false
let inflight = null

const readCache = () => {
  try {
    const raw = sessionStorage.getItem(CACHE_KEY)
    if (!raw) return null
    const { data, ts } = JSON.parse(raw)
    if (Date.now() - ts < TTL_MS) return data
  } catch {
    // ignore malformed cache
  }
  return null
}

const writeCache = (data) => {
  try {
    sessionStorage.setItem(CACHE_KEY, JSON.stringify({ data, ts: Date.now() }))
  } catch {
    // sessionStorage quota / privacy mode
  }
}

export function useFeatureToggles() {
  const isEnabled = (key) => {
    if (!key) return true
    const v = toggles.value[key]
    return v === undefined || v === true || v === 'true'
  }

  const load = async ({ personal = false, force = false } = {}) => {
    if (!force) {
      const cached = readCache()
      if (cached) {
        toggles.value = cached
        loaded = true
        return cached
      }
      if (inflight) return inflight
    }
    const fn = personal ? getPersonalFeatureToggles : getFeatureToggles
    inflight = (async () => {
      try {
        const res = await fn()
        if (res?.code === 200 && res.data && typeof res.data === 'object') {
          toggles.value = res.data
          writeCache(res.data)
          loaded = true
          return res.data
        }
      } catch {
        // degraded mode: all features enabled
      } finally {
        inflight = null
      }
      return toggles.value
    })()
    return inflight
  }

  const invalidate = () => {
    try { sessionStorage.removeItem(CACHE_KEY) } catch { /* noop */ }
    loaded = false
    inflight = null
  }

  return {
    toggles,
    isEnabled,
    load,
    invalidate,
    isLoaded: () => loaded
  }
}
