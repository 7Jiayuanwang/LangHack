import { describe, it, expect, vi, beforeEach } from 'vitest'

// vi.hoisted ensures mockRequest is available when vi.mock factory runs (hoisted)
const mockRequest = vi.hoisted(() => vi.fn())

vi.mock('@/utils/request', () => ({
  default: mockRequest
}))

import {
  getRealTimeMetrics, getSystemMetrics, getHourlyStats, getDailyStats,
  getTopPages, getAiCallsRealtime, getAiCallsDaily, getAiCallsRecent
} from '@/api/monitoring'

describe('Monitoring API', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  describe('getRealTimeMetrics', () => {
    it('calls GET /api/ops/monitoring/realtime', async () => {
      const mockResponse = { code: 200, data: { activeUsers: 5 } }
      mockRequest.mockResolvedValue(mockResponse)

      const result = await getRealTimeMetrics()

      expect(mockRequest).toHaveBeenCalledWith({
        url: '/api/ops/monitoring/realtime',
        method: 'get'
      })
      expect(result).toEqual(mockResponse)
    })

    it('rejects when request fails', async () => {
      mockRequest.mockRejectedValue(new Error('Unauthorized'))

      await expect(getRealTimeMetrics()).rejects.toThrow('Unauthorized')
    })
  })

  describe('getSystemMetrics', () => {
    it('calls GET /api/ops/monitoring/system', async () => {
      const mockResponse = { code: 200, data: { uptimeMs: 3600000 } }
      mockRequest.mockResolvedValue(mockResponse)

      const result = await getSystemMetrics()

      expect(mockRequest).toHaveBeenCalledWith({
        url: '/api/ops/monitoring/system',
        method: 'get'
      })
      expect(result).toEqual(mockResponse)
    })

    it('rejects when request fails', async () => {
      mockRequest.mockRejectedValue(new Error('Server error'))

      await expect(getSystemMetrics()).rejects.toThrow('Server error')
    })
  })

  describe('getHourlyStats', () => {
    it('calls GET /api/ops/monitoring/hourly with params { hours: 24 }', async () => {
      const mockResponse = { code: 200, data: [] }
      mockRequest.mockResolvedValue(mockResponse)

      const result = await getHourlyStats(24)

      expect(mockRequest).toHaveBeenCalledWith({
        url: '/api/ops/monitoring/hourly',
        method: 'get',
        params: { hours: 24 }
      })
      expect(result).toEqual(mockResponse)
    })

    it('uses default hours=24 when no argument provided', async () => {
      mockRequest.mockResolvedValue({ code: 200, data: [] })

      await getHourlyStats()

      expect(mockRequest).toHaveBeenCalledWith({
        url: '/api/ops/monitoring/hourly',
        method: 'get',
        params: { hours: 24 }
      })
    })

    it('rejects when request fails', async () => {
      mockRequest.mockRejectedValue(new Error('Timeout'))

      await expect(getHourlyStats(24)).rejects.toThrow('Timeout')
    })
  })

  describe('getDailyStats', () => {
    it('calls GET /api/ops/monitoring/daily with params { days: 30 }', async () => {
      const mockResponse = { code: 200, data: [] }
      mockRequest.mockResolvedValue(mockResponse)

      const result = await getDailyStats(30)

      expect(mockRequest).toHaveBeenCalledWith({
        url: '/api/ops/monitoring/daily',
        method: 'get',
        params: { days: 30 }
      })
      expect(result).toEqual(mockResponse)
    })

    it('uses default days=30 when no argument provided', async () => {
      mockRequest.mockResolvedValue({ code: 200, data: [] })

      await getDailyStats()

      expect(mockRequest).toHaveBeenCalledWith({
        url: '/api/ops/monitoring/daily',
        method: 'get',
        params: { days: 30 }
      })
    })

    it('rejects when request fails', async () => {
      mockRequest.mockRejectedValue(new Error('Network error'))

      await expect(getDailyStats(30)).rejects.toThrow('Network error')
    })
  })

  describe('getTopPages', () => {
    it('calls GET /api/ops/monitoring/top-pages with empty params when no date', async () => {
      const mockResponse = { code: 200, data: [{ uri: '/api/texts', count: 100 }] }
      mockRequest.mockResolvedValue(mockResponse)

      const result = await getTopPages()

      expect(mockRequest).toHaveBeenCalledWith({
        url: '/api/ops/monitoring/top-pages',
        method: 'get',
        params: {}
      })
      expect(result).toEqual(mockResponse)
    })

    it('calls GET with params { date } when date is provided', async () => {
      const mockResponse = { code: 200, data: [] }
      mockRequest.mockResolvedValue(mockResponse)

      const result = await getTopPages('2026-03-22')

      expect(mockRequest).toHaveBeenCalledWith({
        url: '/api/ops/monitoring/top-pages',
        method: 'get',
        params: { date: '2026-03-22' }
      })
      expect(result).toEqual(mockResponse)
    })

    it('rejects when request fails', async () => {
      mockRequest.mockRejectedValue(new Error('Forbidden'))

      await expect(getTopPages()).rejects.toThrow('Forbidden')
    })
  })

  describe('getAiCallsRealtime', () => {
    it('calls GET /api/ops/monitoring/ai-calls/realtime', async () => {
      const mockResponse = { code: 200, data: { HIAGENT: { totalCalls: 10 } } }
      mockRequest.mockResolvedValue(mockResponse)

      const result = await getAiCallsRealtime()

      expect(mockRequest).toHaveBeenCalledWith({
        url: '/api/ops/monitoring/ai-calls/realtime',
        method: 'get'
      })
      expect(result).toEqual(mockResponse)
    })

    it('rejects when request fails', async () => {
      mockRequest.mockRejectedValue(new Error('Unauthorized'))

      await expect(getAiCallsRealtime()).rejects.toThrow('Unauthorized')
    })
  })

  describe('getAiCallsDaily', () => {
    it('calls GET /api/ops/monitoring/ai-calls/daily with params { days: 30 }', async () => {
      const mockResponse = { code: 200, data: [] }
      mockRequest.mockResolvedValue(mockResponse)

      const result = await getAiCallsDaily(30)

      expect(mockRequest).toHaveBeenCalledWith({
        url: '/api/ops/monitoring/ai-calls/daily',
        method: 'get',
        params: { days: 30 }
      })
      expect(result).toEqual(mockResponse)
    })

    it('uses default days=30 when no argument provided', async () => {
      mockRequest.mockResolvedValue({ code: 200, data: [] })

      await getAiCallsDaily()

      expect(mockRequest).toHaveBeenCalledWith({
        url: '/api/ops/monitoring/ai-calls/daily',
        method: 'get',
        params: { days: 30 }
      })
    })

    it('rejects when request fails', async () => {
      mockRequest.mockRejectedValue(new Error('Server error'))

      await expect(getAiCallsDaily(30)).rejects.toThrow('Server error')
    })
  })

  describe('getAiCallsRecent', () => {
    it('calls GET /api/ops/monitoring/ai-calls/recent with params { limit: 50 }', async () => {
      const mockResponse = { code: 200, data: [{ serviceName: 'HIAGENT', success: true }] }
      mockRequest.mockResolvedValue(mockResponse)

      const result = await getAiCallsRecent(50)

      expect(mockRequest).toHaveBeenCalledWith({
        url: '/api/ops/monitoring/ai-calls/recent',
        method: 'get',
        params: { limit: 50 }
      })
      expect(result).toEqual(mockResponse)
    })

    it('uses default limit=50 when no argument provided', async () => {
      mockRequest.mockResolvedValue({ code: 200, data: [] })

      await getAiCallsRecent()

      expect(mockRequest).toHaveBeenCalledWith({
        url: '/api/ops/monitoring/ai-calls/recent',
        method: 'get',
        params: { limit: 50 }
      })
    })

    it('rejects when request fails', async () => {
      mockRequest.mockRejectedValue(new Error('Timeout'))

      await expect(getAiCallsRecent(50)).rejects.toThrow('Timeout')
    })
  })

  describe('call count verification', () => {
    it('each function calls request exactly once', async () => {
      mockRequest.mockResolvedValue({ code: 200 })

      await getRealTimeMetrics()
      expect(mockRequest).toHaveBeenCalledTimes(1)

      vi.clearAllMocks()
      await getSystemMetrics()
      expect(mockRequest).toHaveBeenCalledTimes(1)

      vi.clearAllMocks()
      await getHourlyStats(24)
      expect(mockRequest).toHaveBeenCalledTimes(1)

      vi.clearAllMocks()
      await getDailyStats(30)
      expect(mockRequest).toHaveBeenCalledTimes(1)

      vi.clearAllMocks()
      await getTopPages()
      expect(mockRequest).toHaveBeenCalledTimes(1)

      vi.clearAllMocks()
      await getAiCallsRealtime()
      expect(mockRequest).toHaveBeenCalledTimes(1)

      vi.clearAllMocks()
      await getAiCallsDaily(30)
      expect(mockRequest).toHaveBeenCalledTimes(1)

      vi.clearAllMocks()
      await getAiCallsRecent(50)
      expect(mockRequest).toHaveBeenCalledTimes(1)
    })
  })
})
