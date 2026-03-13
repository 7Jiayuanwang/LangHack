import axios from 'axios'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/store/user'

const request = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '',
  timeout: 15000
})

// 防止并发 401 多次触发 logout
let isLoggingOut = false

const handleLogout = () => {
  if (isLoggingOut) return
  isLoggingOut = true
  try {
    const userStore = useUserStore()
    userStore.logout()
    window.location.reload()
  } catch (e) {
    console.error('Logout failed:', e)
  } finally {
    // 确保状态标志在任何情况下都被重置
    // 注: window.location.reload() 会刷新页面，导致此行可能不执行
    // 但为了健壮性，仍需添加 finally 块
    isLoggingOut = false
  }
}

// 请求拦截器
request.interceptors.request.use(
  config => {
    try {
      const userStore = useUserStore()
      let token = userStore.token || ''

      // 类型检查与 trim
      if (typeof token === 'string') {
        token = token.trim()
      }

      // 严格检查 token 有效性，防止发送 "Bearer undefined" 或 "Bearer null"
      if (token && token !== 'undefined' && token !== 'null') {
        // 清理 token 中可能已有的 Bearer 前缀，防止重复
        const cleanToken = token.replace(/^Bearer\s+/i, '')
        if (cleanToken && cleanToken !== 'undefined' && cleanToken !== 'null') {
          config.headers['Authorization'] = `Bearer ${cleanToken}`
        }
      }
    } catch (e) {
      console.error('Token processing error:', e)
    }
    return config
  },
  error => {
    console.error('Request error:', error)
    return Promise.reject(error)
  }
)

// 响应拦截器
request.interceptors.response.use(
  response => {
    // 二进制/Blob 响应（如文件下载）直接返回，不做 JSON 解析
    if (response.config.responseType === 'blob' || response.config.responseType === 'arraybuffer') {
      return response
    }

    const res = response.data

    if (!res || res.code === undefined) {
      return res
    }

    if (res.code !== 200) {
      // 401: 未授权，token过期等（只处理一次，由 handleLogout 统一管理）
      if (res.code === 401) {
        handleLogout()
        return Promise.reject(new Error('未授权，请重新登录'))
      }

      // 非 401 的业务错误：仅展示预定义的错误信息，防止后端敏感信息泄露
      const errorMessage = getBusinessErrorMessage(res.code, res.message)
      ElMessage({
        message: errorMessage,
        type: 'error',
        duration: 5 * 1000
      })

      return Promise.reject(new Error(errorMessage))
    } else {
      return res
    }
  },
  error => {
    console.error('Response error:', error)

    let message = error.message
    // 超时 / 网络断连的友好提示
    if (error.code === 'ECONNABORTED' || (error.message && error.message.includes('timeout'))) {
      message = '请求超时，请检查网络连接后重试'
    } else if (!error.response && error.request) {
      message = '网络连接失败，请检查网络后重试'
    }
    if (error.response) {
      switch (error.response.status) {
        case 401:
          message = '未授权，请重新登录'
          handleLogout()
          break
        case 403:
          message = '拒绝访问'
          break
        case 404:
          message = '请求地址不存在'
          break
        case 500:
          message = '服务器内部错误'
          break
        default:
          message = '请求失败，请稍后重试'
      }
    }

    ElMessage({
      message,
      type: 'error',
      duration: 5 * 1000
    })

    // 返回标准化的错误对象，不透传原始 data，防止后端堆栈信息泄露到前端日志
    const errorResponse = {
      code: error.response?.status || 500,
      message: message
    }

    return Promise.reject(errorResponse)
  }
)


/**
 * 获取业务错误信息
 * 仅返回预定义的错误消息，防止后端敏感信息（如堆栈、SQL等）泄露到前端
 * @param {number} code 业务错误码
 * @param {string} backendMessage 后端原始错误信息（仅用于日志）
 * @returns {string} 前端显示的错误信息
 */
function getBusinessErrorMessage(code, backendMessage) {
  // 记录原始错误信息到控制台（用于开发调试）
  if (backendMessage) {
    console.debug('[Business Error]', { code, message: backendMessage })
  }

  // 仅返回预定义的通用错误信息
  const errorMap = {
    400: '请求参数错误',
    403: '拒绝访问',
    404: '请求地址不存在',
    409: '请求冲突',
    500: '服务器错误，请稍后重试'
  }

  return errorMap[code] || '操作失败，请稍后重试'
}

export default request