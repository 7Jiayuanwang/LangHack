import { ref, watch, onMounted, onUnmounted } from 'vue'
import { getFeatureToggles, getUserFeatureToggles, getPersonalFeatureToggles } from '@/api/systemConfig'
import featureToggleWS from '@/utils/featureToggleWebSocket'

const CACHE_KEY = 'feature_toggles_cache_v1'
const TTL_MS = 2 * 60 * 1000 // 缩短缓存时间到2分钟，提高响应性

const toggles = ref({})
let loaded = false
let inflight = null
let refreshTimer = null
let changeListeners = new Set()

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
  // WebSocket监听器清理函数
  let wsCleanup = null

  const isEnabled = (key) => {
    if (!key) return true
    const v = toggles.value[key]
    return v === undefined || v === true || v === 'true'
  }

  // 初始化WebSocket连接
  const initWebSocket = () => {
    // 只在浏览器环境中启用WebSocket
    if (typeof window === 'undefined' || !window.WebSocket) {
      return
    }

    const token = window.localStorage?.getItem('token')
    if (!token) {
      return
    }

    // 添加WebSocket事件监听器
    wsCleanup = featureToggleWS.addListener((event) => {
      switch (event.type) {
        case 'connected':
          console.log('Feature toggle WebSocket connected')
          break
          
        case 'feature_toggle_changed':
          console.log('Received real-time feature toggle changes:', event.changes)
          // 立即刷新功能开关配置
          load({ force: true }).catch(error => {
            console.error('Failed to refresh feature toggles after WebSocket notification:', error)
          })
          break
          
        case 'cache_invalidated':
          console.log('Feature toggle cache invalidated:', event.reason)
          // 缓存失效，强制刷新
          invalidate()
          load({ force: true }).catch(error => {
            console.error('Failed to refresh feature toggles after cache invalidation:', error)
          })
          break
          
        case 'disconnected':
          console.log('Feature toggle WebSocket disconnected')
          break
          
        case 'error':
          console.error('Feature toggle WebSocket error:', event.error)
          break
      }
    })

    // 连接WebSocket
    featureToggleWS.connect()
  }

  // 组件挂载时初始化WebSocket
  onMounted(() => {
    // 延迟初始化，避免影响页面加载
    setTimeout(initWebSocket, 1000)
  })

  // 组件卸载时清理WebSocket监听器
  onUnmounted(() => {
    if (wsCleanup) {
      wsCleanup()
      wsCleanup = null
    }
  })

  const load = async ({ personal = false, authenticated, force = false } = {}) => {
    if (!force) {
      const cached = readCache()
      if (cached) {
        const oldToggles = { ...toggles.value }
        toggles.value = cached
        loaded = true
        // 通知监听器开关已更新
        notifyChange(oldToggles, cached)
        return cached
      }
      if (inflight) return inflight
    }
    // 端点选择优先级：
    // 1. authenticated 显式指定（登录→user 端点，未登录→public 端点）
    // 2. 回退到旧的 personal 参数（仅为兼容）
    // 3. 默认走 public 端点
    let fn
    if (typeof authenticated === 'boolean') {
      fn = authenticated ? getUserFeatureToggles : getFeatureToggles
    } else if (personal) {
      fn = getPersonalFeatureToggles
    } else {
      fn = getFeatureToggles
    }
    inflight = (async () => {
      try {
        const res = await fn()
        if (res?.code === 200 && res.data && typeof res.data === 'object') {
          const oldToggles = { ...toggles.value }
          toggles.value = res.data
          writeCache(res.data)
          loaded = true
          // 通知监听器开关已更新
          notifyChange(oldToggles, res.data)
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
    // 清除自动刷新定时器
    if (refreshTimer) {
      clearInterval(refreshTimer)
      refreshTimer = null
    }
  }

  // 启动自动刷新机制
  const startAutoRefresh = ({ personal = false, authenticated, interval = 2 * 60 * 1000 } = {}) => {
    if (refreshTimer) {
      clearInterval(refreshTimer)
    }
    refreshTimer = setInterval(() => {
      load({ personal, authenticated, force: true }).catch(() => {
        // 静默失败，不影响用户体验
      })
    }, interval)
  }

  // 停止自动刷新
  const stopAutoRefresh = () => {
    if (refreshTimer) {
      clearInterval(refreshTimer)
      refreshTimer = null
    }
  }

  // 添加变更监听器
  const onChange = (callback) => {
    changeListeners.add(callback)
    return () => changeListeners.delete(callback) // 返回取消监听的函数
  }

  // 通知变更
  const notifyChange = (oldToggles, newToggles) => {
    const changes = {}
    for (const key in newToggles) {
      if (oldToggles[key] !== newToggles[key]) {
        changes[key] = {
          old: oldToggles[key],
          new: newToggles[key]
        }
      }
    }
    for (const key in oldToggles) {
      if (!(key in newToggles)) {
        changes[key] = {
          old: oldToggles[key],
          new: undefined
        }
      }
    }
    if (Object.keys(changes).length > 0) {
      changeListeners.forEach(callback => {
        try {
          callback(changes)
        } catch (error) {
          console.error('Feature toggle change listener error:', error)
        }
      })
    }
  }

  // 监听特定开关的变更
  const watchToggle = (key, callback) => {
    return onChange((changes) => {
      if (key in changes) {
        callback(changes[key].new, changes[key].old)
      }
    })
  }

  return {
    toggles,
    isEnabled,
    load,
    invalidate,
    isLoaded: () => loaded,
    startAutoRefresh,
    stopAutoRefresh,
    onChange,
    watchToggle,
    // WebSocket相关方法
    connectWebSocket: () => featureToggleWS.connect(),
    disconnectWebSocket: () => featureToggleWS.disconnect(),
    isWebSocketConnected: () => featureToggleWS.isConnected()
  }
}
