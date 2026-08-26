import { describe, it, expect, vi, beforeEach } from 'vitest'

const mockRequest = vi.hoisted(() => vi.fn())

vi.mock('@/utils/request', () => ({
  default: mockRequest
}))

import { uploadMedia, getMediaDetail, downloadMedia, deleteMedia } from '@/api/media'

describe('Media API', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockRequest.mockResolvedValue({ code: 200, data: {} })
  })

  describe('uploadMedia', () => {
    it('calls POST /api/media/upload with FormData', async () => {
      const file = new File(['test'], 'test.jpg', { type: 'image/jpeg' })
      await uploadMedia(file, 'IMAGE')

      expect(mockRequest).toHaveBeenCalledTimes(1)
      const callArgs = mockRequest.mock.calls[0][0]
      expect(callArgs.url).toBe('/api/media/upload')
      expect(callArgs.method).toBe('post')
      expect(callArgs.data).toBeInstanceOf(FormData)
      expect(callArgs.timeout).toBe(600000)
    })

    it('rejects for invalid media type', async () => {
      const file = new File(['test'], 'test.jpg', { type: 'image/jpeg' })
      await expect(uploadMedia(file, 'AUDIO'))
        .rejects.toThrow('不支持的文件类型')
    })

    it('rejects when mediaType is missing', async () => {
      const file = new File(['test'], 'test.jpg', { type: 'image/jpeg' })
      await expect(uploadMedia(file, ''))
        .rejects.toThrow('媒体类型不能为空')
    })

    it('rejects for unsupported file type', async () => {
      const file = new File(['test'], 'test.exe', { type: 'application/octet-stream' })
      await expect(uploadMedia(file, 'IMAGE'))
        .rejects.toThrow('不支持的文件类型')
    })
  })

  describe('getMediaDetail', () => {
    it('calls GET /api/media/:id', async () => {
      await getMediaDetail(5)
      expect(mockRequest).toHaveBeenCalledWith({
        url: '/api/media/5',
        method: 'get'
      })
    })

    it('rejects for invalid ID', async () => {
      await expect(getMediaDetail('abc'))
        .rejects.toThrow('媒体 ID 必须是有效的正整数')
    })

    it('rejects for null ID', async () => {
      await expect(getMediaDetail(null))
        .rejects.toThrow('媒体 ID 无效')
    })

    it('accepts string numeric ID', async () => {
      await getMediaDetail('123')
      expect(mockRequest).toHaveBeenCalledWith({
        url: '/api/media/123',
        method: 'get'
      })
    })
  })

  describe('downloadMedia', () => {
    it('calls GET /api/media/:id/download with blob responseType', async () => {
      await downloadMedia(10)
      expect(mockRequest).toHaveBeenCalledWith({
        url: '/api/media/10/download',
        method: 'get',
        responseType: 'blob'
      })
    })

    it('rejects for invalid ID', async () => {
      await expect(downloadMedia('abc'))
        .rejects.toThrow('媒体 ID 必须是有效的正整数')
    })
  })

  describe('deleteMedia', () => {
    it('calls DELETE /api/media/:id', async () => {
      await deleteMedia(8)
      expect(mockRequest).toHaveBeenCalledWith({
        url: '/api/media/8',
        method: 'delete'
      })
    })

    it('rejects for invalid ID', async () => {
      await expect(deleteMedia(null))
        .rejects.toThrow('媒体 ID 无效')
    })
  })

  describe('error propagation', () => {
    it('propagates network errors', async () => {
      mockRequest.mockRejectedValue(new Error('Server Error'))
      await expect(downloadMedia(1)).rejects.toThrow('Server Error')
    })
  })
})
