import { describe, it, expect, vi, beforeEach } from 'vitest'

const mockRequest = vi.hoisted(() => vi.fn())

vi.mock('@/utils/request', () => ({
  default: mockRequest
}))

import {
  createExercise,
  batchCreateExercises,
  getExercisesByTextId,
  getExerciseCount,
  getExerciseDetail,
  updateExercise,
  deleteExercise,
  deleteAllExercisesByTextId,
  submitAnswer,
  getStudentAnswersByText,
  getStudentAnswersByAssignment,
  getStudentAnswerStats,
  checkTextExerciseCompletion
} from '@/api/exercise'

describe('Exercise API', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockRequest.mockResolvedValue({ code: 200, data: {} })
  })

  describe('createExercise', () => {
    it('calls POST /api/exercises with data', async () => {
      const data = { textId: 1, question: 'What?', exerciseType: 'CHOICE' }
      await createExercise(data)
      expect(mockRequest).toHaveBeenCalledWith({
        url: '/api/exercises',
        method: 'post',
        data
      })
    })
  })

  describe('batchCreateExercises', () => {
    it('calls POST /api/exercises/batch with valid data', async () => {
      const data = {
        textId: 1,
        exercises: [{
          question: 'Q1',
          exerciseType: 'CHOICE',
          orderNum: 1,
          options: [
            { optionKey: 'A', optionContent: 'Opt A', isCorrect: true },
            { optionKey: 'B', optionContent: 'Opt B', isCorrect: false }
          ]
        }]
      }
      await batchCreateExercises(data)
      expect(mockRequest).toHaveBeenCalledWith({
        url: '/api/exercises/batch',
        method: 'post',
        data,
        timeout: 30000
      })
    })

    it('rejects when exercises array is empty', async () => {
      await expect(batchCreateExercises({ exercises: [] }))
        .rejects.toThrow('习题列表不能为空')
    })

    it('rejects when data is null', async () => {
      await expect(batchCreateExercises(null))
        .rejects.toThrow('数据不能为空')
    })
  })

  describe('getExercisesByTextId', () => {
    it('calls GET /api/exercises/text/:textId', async () => {
      await getExercisesByTextId(5)
      expect(mockRequest).toHaveBeenCalledWith({
        url: '/api/exercises/text/5',
        method: 'get'
      })
    })
  })

  describe('getExerciseCount', () => {
    it('calls GET /api/exercises/text/:textId/count', async () => {
      await getExerciseCount(3)
      expect(mockRequest).toHaveBeenCalledWith({
        url: '/api/exercises/text/3/count',
        method: 'get'
      })
    })
  })

  describe('getExerciseDetail', () => {
    it('calls GET /api/exercises/:exerciseId', async () => {
      await getExerciseDetail(10)
      expect(mockRequest).toHaveBeenCalledWith({
        url: '/api/exercises/10',
        method: 'get'
      })
    })
  })

  describe('updateExercise', () => {
    it('calls PUT /api/exercises/:exerciseId with data', async () => {
      const data = { question: 'Updated Q' }
      await updateExercise(7, data)
      expect(mockRequest).toHaveBeenCalledWith({
        url: '/api/exercises/7',
        method: 'put',
        data
      })
    })
  })

  describe('deleteExercise', () => {
    it('calls DELETE /api/exercises/:exerciseId', async () => {
      await deleteExercise(8)
      expect(mockRequest).toHaveBeenCalledWith({
        url: '/api/exercises/8',
        method: 'delete'
      })
    })
  })

  describe('deleteAllExercisesByTextId', () => {
    it('calls DELETE /api/exercises/text/:textId', async () => {
      await deleteAllExercisesByTextId(4)
      expect(mockRequest).toHaveBeenCalledWith({
        url: '/api/exercises/text/4',
        method: 'delete'
      })
    })
  })

  describe('submitAnswer', () => {
    it('calls POST /api/student/exercises/submit with answerContent', async () => {
      const data = { exerciseId: 1, answerContent: 'A' }
      await submitAnswer(data)
      expect(mockRequest).toHaveBeenCalledWith({
        url: '/api/student/exercises/submit',
        method: 'post',
        data,
        timeout: 10000
      })
    })

    it('rejects when exerciseId is missing', async () => {
      await expect(submitAnswer({ answerContent: 'A' }))
        .rejects.toThrow('习题ID不能为空')
    })

    it('rejects when answerContent is missing', async () => {
      await expect(submitAnswer({ exerciseId: 1 }))
        .rejects.toThrow('答案不能为空')
    })

    it('rejects when data is null', async () => {
      await expect(submitAnswer(null))
        .rejects.toThrow('提交数据不能为空')
    })
  })

  describe('getStudentAnswersByText', () => {
    it('calls GET /api/student/exercises/text/:textId', async () => {
      await getStudentAnswersByText(5)
      expect(mockRequest).toHaveBeenCalledWith({
        url: '/api/student/exercises/text/5',
        method: 'get',
        params: {}
      })
    })

    it('passes assignmentId as param when provided', async () => {
      await getStudentAnswersByText(5, 10)
      expect(mockRequest).toHaveBeenCalledWith({
        url: '/api/student/exercises/text/5',
        method: 'get',
        params: { assignmentId: 10 }
      })
    })
  })

  describe('getStudentAnswersByAssignment', () => {
    it('calls GET /api/student/exercises/assignment/:assignmentId', async () => {
      await getStudentAnswersByAssignment(3)
      expect(mockRequest).toHaveBeenCalledWith({
        url: '/api/student/exercises/assignment/3',
        method: 'get'
      })
    })
  })

  describe('getStudentAnswerStats', () => {
    it('calls GET /api/student/exercises/assignment/:id/stats', async () => {
      await getStudentAnswerStats(3)
      expect(mockRequest).toHaveBeenCalledWith({
        url: '/api/student/exercises/assignment/3/stats',
        method: 'get'
      })
    })
  })

  describe('checkTextExerciseCompletion', () => {
    it('calls GET /api/student/exercises/text/:textId/completed', async () => {
      await checkTextExerciseCompletion(2)
      expect(mockRequest).toHaveBeenCalledWith({
        url: '/api/student/exercises/text/2/completed',
        method: 'get',
        params: {}
      })
    })

    it('passes assignmentId when provided', async () => {
      await checkTextExerciseCompletion(2, 7)
      expect(mockRequest).toHaveBeenCalledWith({
        url: '/api/student/exercises/text/2/completed',
        method: 'get',
        params: { assignmentId: 7 }
      })
    })
  })

  describe('error propagation', () => {
    it('propagates network errors', async () => {
      mockRequest.mockRejectedValue(new Error('Network Error'))
      await expect(getExercisesByTextId(1)).rejects.toThrow('Network Error')
    })
  })
})
