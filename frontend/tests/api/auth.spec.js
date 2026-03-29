import { describe, it, expect, vi, beforeEach } from 'vitest'

// vi.hoisted ensures mockRequest is available when vi.mock factory runs (hoisted)
const mockRequest = vi.hoisted(() => vi.fn())

vi.mock('@/utils/request', () => ({
  default: mockRequest
}))

import { login, register, logout, getUserInfo } from '@/api/auth'

describe('Auth API', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  describe('login', () => {
    it('calls POST /api/auth/login with credentials', async () => {
      const mockResponse = { code: 200, data: { token: 'test-token' } }
      mockRequest.mockResolvedValue(mockResponse)

      const credentials = { username: 'testuser', password: 'testpass' }
      const result = await login(credentials)

      expect(mockRequest).toHaveBeenCalledWith({
        url: '/api/auth/login',
        method: 'post',
        data: credentials
      })
      expect(result).toEqual(mockResponse)
    })

    it('rejects when login fails', async () => {
      mockRequest.mockRejectedValue(new Error('Invalid credentials'))

      await expect(login({ username: 'bad', password: 'bad' }))
        .rejects.toThrow('Invalid credentials')
    })
  })

  describe('register', () => {
    it('calls POST /api/auth/register with user data', async () => {
      const mockResponse = { code: 200, data: { id: 1 } }
      mockRequest.mockResolvedValue(mockResponse)

      const userData = { username: 'newuser', password: 'pass123', email: 'test@test.com' }
      const result = await register(userData)

      expect(mockRequest).toHaveBeenCalledWith({
        url: '/api/auth/register',
        method: 'post',
        data: userData
      })
      expect(result).toEqual(mockResponse)
    })

    it('rejects when registration fails', async () => {
      mockRequest.mockRejectedValue(new Error('Username already exists'))

      await expect(register({ username: 'existing', password: 'pass' }))
        .rejects.toThrow('Username already exists')
    })
  })

  describe('logout', () => {
    it('calls POST /api/auth/logout', async () => {
      const mockResponse = { code: 200, message: 'Logged out' }
      mockRequest.mockResolvedValue(mockResponse)

      const result = await logout()

      expect(mockRequest).toHaveBeenCalledWith({
        url: '/api/auth/logout',
        method: 'post'
      })
      expect(result).toEqual(mockResponse)
    })

    it('sends Authorization header when logout token is provided explicitly', async () => {
      const mockResponse = { code: 200, message: 'Logged out' }
      mockRequest.mockResolvedValue(mockResponse)

      await logout('stale-jwt-token')

      expect(mockRequest).toHaveBeenCalledWith({
        url: '/api/auth/logout',
        method: 'post',
        headers: {
          Authorization: 'Bearer stale-jwt-token'
        }
      })
    })
  })

  describe('getUserInfo', () => {
    it('calls GET /api/auth/userinfo', async () => {
      const mockResponse = { code: 200, data: { id: 1, username: 'testuser', role: 'USER' } }
      mockRequest.mockResolvedValue(mockResponse)

      const result = await getUserInfo()

      expect(mockRequest).toHaveBeenCalledWith({
        url: '/api/auth/userinfo',
        method: 'get'
      })
      expect(result).toEqual(mockResponse)
    })

    it('rejects when not authenticated', async () => {
      mockRequest.mockRejectedValue(new Error('Unauthorized'))

      await expect(getUserInfo()).rejects.toThrow('Unauthorized')
    })
  })

  describe('call count verification', () => {
    it('each function calls request exactly once', async () => {
      mockRequest.mockResolvedValue({ code: 200 })

      await login({ username: 'u', password: 'p' })
      expect(mockRequest).toHaveBeenCalledTimes(1)

      vi.clearAllMocks()
      await register({ username: 'u', password: 'p' })
      expect(mockRequest).toHaveBeenCalledTimes(1)

      vi.clearAllMocks()
      await logout()
      expect(mockRequest).toHaveBeenCalledTimes(1)

      vi.clearAllMocks()
      await getUserInfo()
      expect(mockRequest).toHaveBeenCalledTimes(1)
    })
  })
})
