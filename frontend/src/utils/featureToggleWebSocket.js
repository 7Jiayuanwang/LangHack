/**
 * 功能开关变更实时通知WebSocket服务
 * 当运维人员在功能开关管理页面修改配置时，实时通知所有在线用户
 */

class FeatureToggleWebSocket {
  constructor() {
    this.ws = null
    this.reconnectTimer = null
    this.reconnectAttempts = 0
    this.maxReconnectAttempts = 5
    this.reconnectDelay = 2000 // 2秒
    this.heartbeatTimer = null
    this.heartbeatInterval = 30000 // 30秒心跳
    this.listeners = new Set()
    this.isConnecting = false
    this.url = this.getWebSocketUrl()
  }

  getWebSocketUrl() {
    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
    const host = window.location.host
    return `${protocol}//${host}/api/ws/feature-toggles`
  }

  connect() {
    if (this.isConnecting || (this.ws && this.ws.readyState === WebSocket.OPEN)) {
      return
    }

    this.isConnecting = true
    console.log('Connecting to feature toggle WebSocket:', this.url)

    try {
      this.ws = new WebSocket(this.url)
      
      this.ws.onopen = () => {
        console.log('Feature toggle WebSocket connected')
        this.isConnecting = false
        this.reconnectAttempts = 0
        this.startHeartbeat()
        
        // 通知监听器连接已建立
        this.notifyListeners({ type: 'connected' })
      }

      this.ws.onmessage = (event) => {
        try {
          const data = JSON.parse(event.data)
          this.handleMessage(data)
        } catch (error) {
          console.error('Failed to parse WebSocket message:', error, event.data)
        }
      }

      this.ws.onclose = (event) => {
        console.log('Feature toggle WebSocket disconnected:', event.code, event.reason)
        this.isConnecting = false
        this.stopHeartbeat()
        
        // 通知监听器连接已断开
        this.notifyListeners({ type: 'disconnected', code: event.code, reason: event.reason })

        // 自动重连
        if (this.reconnectAttempts < this.maxReconnectAttempts) {
          this.scheduleReconnect()
        }
      }

      this.ws.onerror = (error) => {
        console.error('Feature toggle WebSocket error:', error)
        this.isConnecting = false
        
        // 通知监听器发生错误
        this.notifyListeners({ type: 'error', error })
      }

    } catch (error) {
      console.error('Failed to create WebSocket connection:', error)
      this.isConnecting = false
      this.scheduleReconnect()
    }
  }

  disconnect() {
    this.clearReconnectTimer()
    this.stopHeartbeat()
    
    if (this.ws) {
      this.ws.close(1000, 'User disconnected')
      this.ws = null
    }
    
    this.listeners.clear()
  }

  scheduleReconnect() {
    this.clearReconnectTimer()
    
    const delay = this.reconnectDelay * Math.pow(2, this.reconnectAttempts) // 指数退避
    console.log(`Scheduling reconnect attempt ${this.reconnectAttempts + 1} in ${delay}ms`)
    
    this.reconnectTimer = setTimeout(() => {
      this.reconnectAttempts++
      this.connect()
    }, delay)
  }

  clearReconnectTimer() {
    if (this.reconnectTimer) {
      clearTimeout(this.reconnectTimer)
      this.reconnectTimer = null
    }
  }

  startHeartbeat() {
    this.stopHeartbeat()
    this.heartbeatTimer = setInterval(() => {
      if (this.ws && this.ws.readyState === WebSocket.OPEN) {
        this.ws.send(JSON.stringify({ type: 'ping' }))
      }
    }, this.heartbeatInterval)
  }

  stopHeartbeat() {
    if (this.heartbeatTimer) {
      clearInterval(this.heartbeatTimer)
      this.heartbeatTimer = null
    }
  }

  handleMessage(data) {
    console.log('Received feature toggle update:', data)
    
    switch (data.type) {
      case 'pong':
        // 心跳响应，无需处理
        break
        
      case 'feature_toggle_changed':
        // 功能开关变更通知
        this.notifyListeners({
          type: 'feature_toggle_changed',
          changes: data.changes,
          timestamp: data.timestamp
        })
        break
        
      case 'cache_invalidated':
        // 缓存失效通知
        this.notifyListeners({
          type: 'cache_invalidated',
          reason: data.reason,
          timestamp: data.timestamp
        })
        break
        
      default:
        console.warn('Unknown WebSocket message type:', data.type)
    }
  }

  // 添加事件监听器
  addListener(callback) {
    this.listeners.add(callback)
    return () => this.listeners.delete(callback) // 返回取消监听的函数
  }

  // 通知所有监听器
  notifyListeners(event) {
    this.listeners.forEach(callback => {
      try {
        callback(event)
      } catch (error) {
        console.error('Error in WebSocket listener:', error)
      }
    })
  }

  // 获取连接状态
  getReadyState() {
    return this.ws ? this.ws.readyState : WebSocket.CLOSED
  }

  // 检查是否已连接
  isConnected() {
    return this.ws && this.ws.readyState === WebSocket.OPEN
  }
}

// 创建单例实例
const featureToggleWS = new FeatureToggleWebSocket()

export default featureToggleWS
