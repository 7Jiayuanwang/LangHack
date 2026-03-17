import { describe, it, expect, vi, beforeEach } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'

// Mock element-plus before import
vi.mock('element-plus', () => ({
  ElMessage: vi.fn()
}))

describe('Request Utility', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('exports a function (axios instance)', async () => {
    const { default: request } = await import('@/utils/request')
    expect(typeof request).toBe('function')
  })

  it('has interceptors configured', async () => {
    const { default: request } = await import('@/utils/request')
    expect(request.interceptors).toBeDefined()
    expect(request.interceptors.request).toBeDefined()
    expect(request.interceptors.response).toBeDefined()
  })

  it('has request interceptor handlers', async () => {
    const { default: request } = await import('@/utils/request')
    expect(request.interceptors.request.handlers.length).toBeGreaterThan(0)
  })

  it('has response interceptor handlers', async () => {
    const { default: request } = await import('@/utils/request')
    expect(request.interceptors.response.handlers.length).toBeGreaterThan(0)
  })

  it('has default timeout of 15000ms', async () => {
    const { default: request } = await import('@/utils/request')
    expect(request.defaults.timeout).toBe(15000)
  })

  it('request interceptor adds Bearer token when available', async () => {
    const { useUserStore } = await import('@/store/user')
    const { default: request } = await import('@/utils/request')

    const store = useUserStore()
    store.token = 'test-jwt-token'

    const handler = request.interceptors.request.handlers[0]
    const config = { headers: {} }
    const result = handler.fulfilled(config)
    expect(result.headers['Authorization']).toBe('Bearer test-jwt-token')
  })

  it('request interceptor skips Authorization when no token', async () => {
    const { useUserStore } = await import('@/store/user')
    const { default: request } = await import('@/utils/request')

    const store = useUserStore()
    store.token = ''

    const handler = request.interceptors.request.handlers[0]
    const config = { headers: {} }
    const result = handler.fulfilled(config)
    expect(result.headers['Authorization']).toBeUndefined()
  })

  it('response interceptor returns data for code 200', async () => {
    const { default: request } = await import('@/utils/request')
    const handler = request.interceptors.response.handlers[0]

    const response = {
      data: { code: 200, data: { id: 1 } },
      config: {}
    }

    const result = handler.fulfilled(response)
    expect(result).toEqual({ code: 200, data: { id: 1 } })
  })

  it('response interceptor passes through blob responses', async () => {
    const { default: request } = await import('@/utils/request')
    const handler = request.interceptors.response.handlers[0]

    const response = {
      data: new Blob(['test']),
      config: { responseType: 'blob' }
    }

    const result = handler.fulfilled(response)
    expect(result).toBe(response)
  })
})
