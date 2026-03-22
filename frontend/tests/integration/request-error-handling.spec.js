import { describe, it, expect, beforeEach, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'

// Mock element-plus
vi.mock('element-plus', () => ({
  ElMessage: vi.fn()
}))

import { ElMessage } from 'element-plus'

describe('Request Error Handling Integration', () => {
  let request
  let responseHandler

  beforeEach(async () => {
    setActivePinia(createPinia())
    vi.clearAllMocks()

    // Dynamically import to get fresh module
    const module = await import('@/utils/request')
    request = module.default
    responseHandler = request.interceptors.response.handlers[0]
  })

  describe('Response interceptor - success path', () => {
    it('returns data for code 200 response', () => {
      const response = {
        data: { code: 200, data: { items: [1, 2, 3] } },
        config: {}
      }

      const result = responseHandler.fulfilled(response)
      expect(result).toEqual({ code: 200, data: { items: [1, 2, 3] } })
    })

    it('passes through blob responses without processing', () => {
      const blobData = new Blob(['test content'])
      const response = {
        data: blobData,
        config: { responseType: 'blob' }
      }

      const result = responseHandler.fulfilled(response)
      expect(result).toBe(response)
    })

    it('passes through arraybuffer responses without processing', () => {
      const response = {
        data: new ArrayBuffer(10),
        config: { responseType: 'arraybuffer' }
      }

      const result = responseHandler.fulfilled(response)
      expect(result).toBe(response)
    })

    it('returns raw data when code field is missing', () => {
      const response = {
        data: { someField: 'value' },
        config: {}
      }

      const result = responseHandler.fulfilled(response)
      expect(result).toEqual({ someField: 'value' })
    })

    it('returns raw data when response.data is null', () => {
      const response = { data: null, config: {} }

      const result = responseHandler.fulfilled(response)
      expect(result).toBeNull()
    })
  })

  describe('Response interceptor - business error path', () => {
    it('rejects with error for non-200 business code', async () => {
      const response = {
        data: { code: 500, message: '服务器错误' },
        config: {}
      }

      await expect(Promise.resolve().then(() => responseHandler.fulfilled(response)))
        .rejects.toThrow()

      expect(ElMessage).toHaveBeenCalledWith(
        expect.objectContaining({
          type: 'error',
          duration: 5000
        })
      )
    })

    it('shows error message for 403 business code', async () => {
      const response = {
        data: { code: 403, message: 'Forbidden' },
        config: {}
      }

      await expect(Promise.resolve().then(() => responseHandler.fulfilled(response)))
        .rejects.toThrow('拒绝访问')

      expect(ElMessage).toHaveBeenCalledWith(
        expect.objectContaining({
          message: '拒绝访问',
          type: 'error'
        })
      )
    })

    it('shows error message for 404 business code', async () => {
      const response = {
        data: { code: 404, message: 'Not Found' },
        config: {}
      }

      await expect(Promise.resolve().then(() => responseHandler.fulfilled(response)))
        .rejects.toThrow('请求地址不存在')

      expect(ElMessage).toHaveBeenCalledWith(
        expect.objectContaining({
          message: '请求地址不存在',
          type: 'error'
        })
      )
    })

    it('uses generic message for unknown business codes', async () => {
      const response = {
        data: { code: 999, message: 'Internal SQL error: SELECT * FROM...' },
        config: {}
      }

      await expect(Promise.resolve().then(() => responseHandler.fulfilled(response)))
        .rejects.toThrow('操作失败，请稍后重试')

      // Should NOT expose the raw backend message (SQL leak prevention)
      expect(ElMessage).toHaveBeenCalledWith(
        expect.objectContaining({
          message: '操作失败，请稍后重试'
        })
      )
    })
  })

  describe('Response interceptor - HTTP error path', () => {
    it('handles timeout error with friendly message', async () => {
      const error = {
        code: 'ECONNABORTED',
        message: 'timeout of 15000ms exceeded',
        request: {}
      }

      await expect(responseHandler.rejected(error)).rejects.toBeDefined()

      expect(ElMessage).toHaveBeenCalledWith(
        expect.objectContaining({
          message: '请求超时，请检查网络连接后重试',
          type: 'error'
        })
      )
    })

    it('handles network disconnection (Network Error triggers CORS hint)', async () => {
      const error = {
        message: 'Network Error',
        request: {}
        // no response — network failure; "Network Error" triggers CORS hint path
      }

      await expect(responseHandler.rejected(error)).rejects.toBeDefined()

      expect(ElMessage).toHaveBeenCalledWith(
        expect.objectContaining({
          message: '网络请求被拒绝，可能是跨域(CORS)配置问题，请联系管理员检查后端 CORS 白名单',
          type: 'error'
        })
      )
    })

    it('handles non-Network-Error disconnection with generic message', async () => {
      const error = {
        message: 'Failed to fetch',
        request: {},
        config: { baseURL: window.location.origin, url: '/api/test' }
        // no response, but message is not 'Network Error' and same-origin
      }

      await expect(responseHandler.rejected(error)).rejects.toBeDefined()

      expect(ElMessage).toHaveBeenCalledWith(
        expect.objectContaining({
          message: '网络连接失败，请检查网络后重试',
          type: 'error'
        })
      )
    })

    it('handles 403 HTTP status', async () => {
      const error = {
        message: 'Request failed with status code 403',
        response: { status: 403 }
      }

      await expect(responseHandler.rejected(error)).rejects.toBeDefined()

      expect(ElMessage).toHaveBeenCalledWith(
        expect.objectContaining({
          message: '拒绝访问'
        })
      )
    })

    it('handles 404 HTTP status', async () => {
      const error = {
        message: 'Request failed with status code 404',
        response: { status: 404 }
      }

      await expect(responseHandler.rejected(error)).rejects.toBeDefined()

      expect(ElMessage).toHaveBeenCalledWith(
        expect.objectContaining({
          message: '请求地址不存在'
        })
      )
    })

    it('handles 500 HTTP status', async () => {
      const error = {
        message: 'Request failed with status code 500',
        response: { status: 500 }
      }

      await expect(responseHandler.rejected(error)).rejects.toBeDefined()

      expect(ElMessage).toHaveBeenCalledWith(
        expect.objectContaining({
          message: '服务器内部错误'
        })
      )
    })

    it('handles 500 with detail for OPS user', async () => {
      const { useUserStore } = await import('@/store/user')
      const store = useUserStore()
      store.userRole = 'OPS'

      const error = {
        message: 'Request failed with status code 500',
        response: {
          status: 500,
          data: { detail: 'NullPointerException at UserService.java:42' }
        }
      }

      await expect(responseHandler.rejected(error)).rejects.toBeDefined()

      expect(ElMessage).toHaveBeenCalledWith(
        expect.objectContaining({
          message: '服务器错误: NullPointerException at UserService.java:42'
        })
      )
    })

    it('handles 500 with detail for elevated OPS user', async () => {
      const { useUserStore } = await import('@/store/user')
      const store = useUserStore()
      store.userRole = 'ADMIN'
      store.elevatedRole = 'OPS'
      store.elevationExpiresAt = new Date(Date.now() + 3600000).toISOString() // 1h from now

      const error = {
        message: 'Request failed with status code 500',
        response: {
          status: 500,
          data: { detail: 'DB connection refused' }
        }
      }

      await expect(responseHandler.rejected(error)).rejects.toBeDefined()

      expect(ElMessage).toHaveBeenCalledWith(
        expect.objectContaining({
          message: '服务器错误: DB connection refused'
        })
      )
    })

    it('handles 500 without detail for non-OPS user', async () => {
      const { useUserStore } = await import('@/store/user')
      const store = useUserStore()
      store.userRole = 'STUDENT'

      const error = {
        message: 'Request failed with status code 500',
        response: {
          status: 500,
          data: { detail: 'Internal stack trace info' }
        }
      }

      await expect(responseHandler.rejected(error)).rejects.toBeDefined()

      expect(ElMessage).toHaveBeenCalledWith(
        expect.objectContaining({
          message: '服务器内部错误'
        })
      )
    })

    it('handles unknown HTTP status with generic message', async () => {
      const error = {
        message: 'Request failed',
        response: { status: 502 }
      }

      await expect(responseHandler.rejected(error)).rejects.toBeDefined()

      expect(ElMessage).toHaveBeenCalledWith(
        expect.objectContaining({
          message: '请求失败，请稍后重试'
        })
      )
    })

    it('returns standardized error object without raw data', async () => {
      const error = {
        message: 'Request failed',
        response: { status: 500, data: { stackTrace: 'java.lang.Exception...' } }
      }

      try {
        await responseHandler.rejected(error)
      } catch (e) {
        // Error should NOT contain the raw stackTrace
        expect(JSON.stringify(e)).not.toContain('stackTrace')
        expect(JSON.stringify(e)).not.toContain('java.lang')
      }
    })
  })

  describe('Request interceptor - token handling', () => {
    it('adds Bearer token from store', async () => {
      // getAuthHeader() reads from localStorage, so we must persist there too
      localStorage.setItem('token', 'valid-jwt-token')

      const requestHandler = request.interceptors.request.handlers[0]
      const config = { headers: {} }
      const result = requestHandler.fulfilled(config)

      expect(result.headers['Authorization']).toBe('Bearer valid-jwt-token')
    })

    it('skips Authorization for empty token', async () => {
      localStorage.removeItem('token')

      const requestHandler = request.interceptors.request.handlers[0]
      const config = { headers: {} }
      const result = requestHandler.fulfilled(config)

      expect(result.headers['Authorization']).toBeUndefined()
    })

    it('strips duplicate Bearer prefix', async () => {
      localStorage.setItem('token', 'Bearer already-prefixed-token')

      const requestHandler = request.interceptors.request.handlers[0]
      const config = { headers: {} }
      const result = requestHandler.fulfilled(config)

      expect(result.headers['Authorization']).toBe('Bearer already-prefixed-token')
      // Should NOT be "Bearer Bearer already-prefixed-token"
      expect(result.headers['Authorization']).not.toContain('Bearer Bearer')
    })

    it('skips "undefined" and "null" string tokens', async () => {
      const requestHandler = request.interceptors.request.handlers[0]

      // Test "undefined" string
      localStorage.setItem('token', 'undefined')
      let config = { headers: {} }
      let result = requestHandler.fulfilled(config)
      expect(result.headers['Authorization']).toBeUndefined()

      // Test "null" string
      localStorage.setItem('token', 'null')
      config = { headers: {} }
      result = requestHandler.fulfilled(config)
      expect(result.headers['Authorization']).toBeUndefined()
    })

    it('trims whitespace from token', async () => {
      localStorage.setItem('token', '  jwt-token-with-spaces  ')

      const requestHandler = request.interceptors.request.handlers[0]
      const config = { headers: {} }
      const result = requestHandler.fulfilled(config)

      expect(result.headers['Authorization']).toBe('Bearer jwt-token-with-spaces')
    })
  })
})
