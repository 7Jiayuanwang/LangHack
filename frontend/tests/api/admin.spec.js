import { describe, it, expect, vi, beforeEach } from 'vitest'

const mockRequest = vi.hoisted(() => vi.fn())

vi.mock('@/utils/request', () => ({
  default: mockRequest
}))

import {
  getUsers,
  createUser,
  updateUser,
  deleteUser,
  getCourses,
  createCourse,
  updateCourse,
  deleteCourse,
  getCourseStats
} from '@/api/admin'

describe('Admin API', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockRequest.mockResolvedValue({ code: 200, data: {} })
  })

  // === User Management ===

  describe('getUsers', () => {
    it('calls GET /api/admin/users with pagination params', async () => {
      const params = { page: 1, size: 10, role: 'STUDENT' }
      await getUsers(params)
      expect(mockRequest).toHaveBeenCalledWith({
        url: '/api/admin/users',
        method: 'get',
        params
      })
    })

    it('calls GET /api/admin/users without params', async () => {
      await getUsers()
      expect(mockRequest).toHaveBeenCalledWith({
        url: '/api/admin/users',
        method: 'get',
        params: undefined
      })
    })
  })

  describe('createUser', () => {
    it('calls POST /api/admin/users with user data', async () => {
      const data = { username: 'newteacher', password: 'pass123', role: 'TEACHER' }
      await createUser(data)
      expect(mockRequest).toHaveBeenCalledWith({
        url: '/api/admin/users',
        method: 'post',
        data
      })
    })
  })

  describe('updateUser', () => {
    it('calls PUT /api/admin/users/:id with updated data', async () => {
      const data = { username: 'updated', role: 'ADMIN' }
      await updateUser(5, data)
      expect(mockRequest).toHaveBeenCalledWith({
        url: '/api/admin/users/5',
        method: 'put',
        data
      })
    })
  })

  describe('deleteUser', () => {
    it('calls DELETE /api/admin/users/:id', async () => {
      await deleteUser(10)
      expect(mockRequest).toHaveBeenCalledWith({
        url: '/api/admin/users/10',
        method: 'delete'
      })
    })
  })

  // === Course Management ===

  describe('getCourses', () => {
    it('calls GET /api/courses with params', async () => {
      const params = { page: 1, size: 20 }
      await getCourses(params)
      expect(mockRequest).toHaveBeenCalledWith({
        url: '/api/courses',
        method: 'get',
        params
      })
    })
  })

  describe('createCourse', () => {
    it('calls POST /api/courses with course data', async () => {
      const data = { name: 'English 101', description: 'Beginner course' }
      await createCourse(data)
      expect(mockRequest).toHaveBeenCalledWith({
        url: '/api/courses',
        method: 'post',
        data
      })
    })
  })

  describe('updateCourse', () => {
    it('calls PUT /api/courses/:id with updated data', async () => {
      const data = { name: 'English 102' }
      await updateCourse(3, data)
      expect(mockRequest).toHaveBeenCalledWith({
        url: '/api/courses/3',
        method: 'put',
        data
      })
    })
  })

  describe('deleteCourse', () => {
    it('calls DELETE /api/courses/:id', async () => {
      await deleteCourse(7)
      expect(mockRequest).toHaveBeenCalledWith({
        url: '/api/courses/7',
        method: 'delete'
      })
    })
  })

  describe('getCourseStats', () => {
    it('calls GET /api/courses/stats', async () => {
      const mockData = { code: 200, data: { totalCourses: 10, totalStudents: 500 } }
      mockRequest.mockResolvedValue(mockData)

      const result = await getCourseStats()

      expect(mockRequest).toHaveBeenCalledWith({
        url: '/api/courses/stats',
        method: 'get'
      })
      expect(result).toEqual(mockData)
    })
  })

  // === Error handling ===

  describe('error propagation', () => {
    it('propagates errors from user operations', async () => {
      mockRequest.mockRejectedValue(new Error('Forbidden'))
      await expect(getUsers()).rejects.toThrow('Forbidden')
    })

    it('propagates errors from course operations', async () => {
      mockRequest.mockRejectedValue(new Error('Not Found'))
      await expect(deleteCourse(999)).rejects.toThrow('Not Found')
    })
  })
})
