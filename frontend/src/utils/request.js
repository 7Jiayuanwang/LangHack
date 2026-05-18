import axios from 'axios'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/store/user'
import { getAuthHeader } from '@/utils/auth'
import { getSsoStatus, getSsoLogoutUrl } from '@/api/sso'

const request = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '',
  timeout: 60000  // 增加到 60 秒，适配学校数据 API 响应较慢的情况
})

// 防止并发 401 多次触发 logout
let isLoggingOut = false

const handleLogout = async () => {
  if (isLoggingOut) return
  isLoggingOut = true

  // 保存当前页面路径，SSO 重新登录后可跳回
  // 301/302 是重定向，不应保存；login/sso/callback 也不保存避免循环
  const currentPath = window.location.pathname + window.location.search + window.location.hash
  if (
    currentPath !== '/login' &&
    !currentPath.startsWith('/sso/callback') &&
    !currentPath.startsWith('/admin-portal')
  ) {
    try {
      sessionStorage.setItem('sso_redirect_after_login', currentPath)
    } catch (e) {
      console.warn('[SSO] 保存 401 重定向路径失败', e)
    }
  }

  try {
    const userStore = useUserStore()
    // 先清理本地状态（store.logout 内部会先清本地再调 API）
    await userStore.logout()
  } catch (e) {
    console.error('Logout failed:', e)
  } finally {
    isLoggingOut = false
    // SSO 启用时跳转到 CAS 登出页（统一身份认证要求）
    // 优先检查 sessionStorage 缓存的 SSO 状态，避免额外网络请求
    const ssoEnabled = sessionStorage.getItem('sso_enabled') === 'true'
    if (ssoEnabled) {
      getSsoLogoutUrl().then(res => {
        if (res?.code === 200 && res.data?.logoutUrl) {
          window.location.href = res.data.logoutUrl
        } else {
          window.location.replace('/login')
        }
      }).catch(() => {
        window.location.replace('/login')
      })
    } else {
      window.location.replace('/login')
    }
  }
}

// 请求拦截器
request.interceptors.request.use(
  config => {
    try {
      const authHeader = getAuthHeader()
      if (authHeader) {
        config.headers['Authorization'] = authHeader
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
        return Promise.reject({ code: 401, message: '未授权，请重新登录' })
      }

      // 4001: 需要前端确认的业务提示（如"文章未公开，是否同时公开？"），直接放行让调用方处理
      if (res.code === 4001) {
        return res
      }

      // 非 401 的业务错误：仅展示预定义的错误信息，防止后端敏感信息泄露
      const errorMessage = getBusinessErrorMessage(res.code, res.message)
      ElMessage({
        message: errorMessage,
        type: 'error',
        duration: 5 * 1000
      })

      return Promise.reject({ code: res.code, message: errorMessage })
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
      // 如果配置了 skipErrorHandler，则不显示错误提示（用于数据已成功加载的场景）
      if (error.config?.skipErrorHandler) {
        return Promise.reject({ code: 408, message })
      }
    } else if (!error.response && error.request) {
      // No response + request exists = network error or CORS block.
      // CORS blocks appear as network errors because the browser hides the response entirely.
      // 无响应 + 请求已发出 = 网络错误或 CORS 拦截（浏览器完全隐藏被 CORS 拦截的响应）。
      const url = error.config?.url || ''
      const isCrossOrigin = error.config?.baseURL && !error.config.baseURL.startsWith(window.location.origin)
      if (isCrossOrigin || error.message === 'Network Error') {
        message = '网络请求被拒绝，可能是跨域(CORS)配置问题，请联系管理员检查后端 CORS 白名单'
        console.error(
          '[CORS Hint] Request to', url, 'failed with Network Error.',
          'Current origin:', window.location.origin,
          '— Ensure this origin is in the backend CORS config (application-prod.yml or APP_CORS_ALLOWED_ORIGINS env var).'
        )
      } else {
        message = '网络连接失败，请检查网络后重试'
      }
    }
    if (!error.config?.skipErrorHandler) {
      const reqUrl = error.config?.url || 'unknown'
      const status = error.response?.status
      if (status) {
        console.warn(`[HTTP ${status}] ${error.config?.method?.toUpperCase() || 'GET'} ${reqUrl}`, error.response?.data)
        // Map HTTP status codes to friendly messages
        message = getHttpErrorMessage(status, error.response?.data)
      }

      ElMessage({
        message,
        type: 'error',
        duration: 5 * 1000
      })
    }

    // 返回标准化的错误对象，不透传原始 data，防止后端堆栈信息泄露到前端日志
    const errorResponse = {
      code: error.response?.status || 500,
      message: message
    }

    return Promise.reject(errorResponse)
  }
)


/**
 * 将 HTTP 状态码映射为用户友好的错误信息
 * OPS/ADMIN 用户可看到 500 的详细信息（detail 字段）
 */
function getHttpErrorMessage(status, responseData) {
  if (status === 500) {
    try {
      const store = useUserStore()
      const isOps = store.userRole === 'OPS' || store.userRole === 'ADMIN' ||
        (store.elevatedRole === 'OPS' && store.elevationExpiresAt && new Date(store.elevationExpiresAt) > new Date())
      if (isOps && responseData?.detail) {
        return `服务器错误: ${responseData.detail}`
      }
    } catch (e) { /* store not available */ }
    return '服务器内部错误'
  }
  const httpErrorMap = {
    400: '请求参数错误',
    401: '未授权，请重新登录',
    403: '拒绝访问',
    404: '请求地址不存在',
    409: '请求冲突',
  }
  return httpErrorMap[status] || '请求失败，请稍后重试'
}


/**
 * 获取业务错误信息
 * code 500 是 Result.error(msg) 的默认业务错误码，后端已确保消息安全
 * （BusinessException 返回用户友好消息，Exception 返回通用提示）
 * 其他 code 使用预定义映射，防止非预期消息泄露
 * @param {number} code 业务错误码
 * @param {string} backendMessage 后端返回的错误信息
 * @returns {string} 前端显示的错误信息
 */
function getBusinessErrorMessage(code, backendMessage) {
  if (backendMessage) {
    console.debug('[Business Error]', { code, message: backendMessage })
  }

  // code 500: 业务错误（后端 Result.error(msg) 默认 code=500）
  // 后端已确保：BusinessException 消息用户友好、Exception 消息通用
  if (code === 500 && backendMessage) {
    return backendMessage
  }

  // 其他 code: 使用预定义映射
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