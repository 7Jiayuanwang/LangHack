import { describe, it, expect, vi, beforeEach } from 'vitest'

const mockRequest = vi.hoisted(() => vi.fn())

vi.mock('@/utils/request', () => ({
  default: mockRequest
}))

import {
  getStudentDashboard,
  getTodayLearningStats,
  getStudentCourses,
  getCourseUnits,
  getUnitContent,
  getTextDetail,
  getWordDetail,
  addToVocabulary,
  getVocabularyList,
  updateMasteryLevel,
  startStudySession,
  submitStudyResult,
  getStudentAssignments,
  startAssignment,
  getLearningOverview,
  getLearningRecords,
  getMyClasses,
  searchClasses,
  joinClass,
  leaveClass,
  getWordLearningStats,
  getCourseLearningStats,
  getStudyTimeStats,
  getProgressReport,
  getAchievementStats,
  getLearningRanking,
  exportLearningData,
  getWordStudyStats
} from '@/api/student'

describe('Student API', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockRequest.mockResolvedValue({ code: 200, data: {} })
  })

  // === Dashboard & Stats ===

  describe('getStudentDashboard', () => {
    it('calls GET /api/student/learning/dashboard', async () => {
      await getStudentDashboard()
      expect(mockRequest).toHaveBeenCalledWith({
        url: '/api/student/learning/dashboard',
        method: 'get'
      })
    })
  })

  describe('getTodayLearningStats', () => {
    it('calls GET /api/student/stats/today', async () => {
      await getTodayLearningStats()
      expect(mockRequest).toHaveBeenCalledWith({
        url: '/api/student/stats/today',
        method: 'get'
      })
    })
  })

  // === Course & Unit ===

  describe('getStudentCourses', () => {
    it('calls GET /api/student/learning/courses', async () => {
      await getStudentCourses()
      expect(mockRequest).toHaveBeenCalledWith({
        url: '/api/student/learning/courses',
        method: 'get'
      })
    })
  })

  describe('getCourseUnits', () => {
    it('calls GET /api/student/learning/courses/:courseId/units with courseId', async () => {
      await getCourseUnits(42)
      expect(mockRequest).toHaveBeenCalledWith({
        url: '/api/student/learning/courses/42/units',
        method: 'get'
      })
    })
  })

  describe('getUnitContent', () => {
    it('calls GET /api/student/learning/units/:unitId/content with unitId', async () => {
      await getUnitContent(7)
      expect(mockRequest).toHaveBeenCalledWith({
        url: '/api/student/learning/units/7/content',
        method: 'get'
      })
    })
  })

  // === Text & Word Detail ===

  describe('getTextDetail', () => {
    it('calls GET /api/student/learning/texts/:textId with textId', async () => {
      await getTextDetail(99)
      expect(mockRequest).toHaveBeenCalledWith({
        url: '/api/student/learning/texts/99',
        method: 'get'
      })
    })
  })

  describe('getWordDetail', () => {
    it('calls GET /api/student/words/:wordId/detail with wordId', async () => {
      await getWordDetail(55)
      expect(mockRequest).toHaveBeenCalledWith({
        url: '/api/student/words/55/detail',
        method: 'get'
      })
    })
  })

  // === Vocabulary ===

  describe('addToVocabulary', () => {
    it('calls POST /api/student/learning/vocabulary/add with word data', async () => {
      const data = { wordId: 10, note: 'hard word' }
      await addToVocabulary(data)
      expect(mockRequest).toHaveBeenCalledWith({
        url: '/api/student/learning/vocabulary/add',
        method: 'post',
        data
      })
    })
  })

  describe('getVocabularyList', () => {
    it('calls GET /api/student/words/vocabulary with params', async () => {
      const params = { page: 1, size: 20 }
      await getVocabularyList(params)
      expect(mockRequest).toHaveBeenCalledWith({
        url: '/api/student/words/vocabulary',
        method: 'get',
        params
      })
    })
  })

  describe('updateMasteryLevel', () => {
    it('calls PUT /api/student/words/vocabulary/:wordId/mastery with data', async () => {
      const data = { level: 3 }
      await updateMasteryLevel(25, data)
      expect(mockRequest).toHaveBeenCalledWith({
        url: '/api/student/words/vocabulary/25/mastery',
        method: 'put',
        data
      })
    })
  })

  // === Study Session ===

  describe('startStudySession', () => {
    it('calls POST /api/student/words/study/session with unitId', async () => {
      const unitId = 5
      await startStudySession(unitId)
      expect(mockRequest).toHaveBeenCalledWith({
        url: '/api/student/words/study/session',
        method: 'post',
        data: { unitId: 5 }
      })
    })
  })

  describe('submitStudyResult', () => {
    it('calls POST /api/student/words/study/submit with result data', async () => {
      const data = { wordId: 5, correct: true }
      await submitStudyResult(data)
      expect(mockRequest).toHaveBeenCalledWith({
        url: '/api/student/words/study/submit',
        method: 'post',
        data
      })
    })
  })

  // === Assignments ===

  describe('getStudentAssignments', () => {
    it('calls GET /api/student/assignments with params', async () => {
      const params = { status: 'pending', page: 1 }
      await getStudentAssignments(params)
      expect(mockRequest).toHaveBeenCalledWith({
        url: '/api/student/assignments',
        method: 'get',
        params
      })
    })
  })

  describe('startAssignment', () => {
    it('calls POST /api/student/assignments/:assignmentId/start', async () => {
      await startAssignment(101)
      expect(mockRequest).toHaveBeenCalledWith({
        url: '/api/student/assignments/101/start',
        method: 'post'
      })
    })
  })

  // === Learning Stats ===

  describe('getLearningOverview', () => {
    it('calls GET /api/student/stats/overview with params', async () => {
      const params = { period: 'week' }
      await getLearningOverview(params)
      expect(mockRequest).toHaveBeenCalledWith({
        url: '/api/student/stats/overview',
        method: 'get',
        params
      })
    })
  })

  describe('getLearningRecords', () => {
    it('calls GET /api/student/stats/records with params', async () => {
      const params = { page: 1, size: 10 }
      await getLearningRecords(params)
      expect(mockRequest).toHaveBeenCalledWith({
        url: '/api/student/stats/records',
        method: 'get',
        params
      })
    })
  })

  // === Classes ===

  describe('getMyClasses', () => {
    it('calls GET /api/classes/student/me', async () => {
      await getMyClasses()
      expect(mockRequest).toHaveBeenCalledWith({
        url: '/api/classes/student/me',
        method: 'get'
      })
    })
  })

  describe('searchClasses', () => {
    it('calls GET /api/classes/search with params', async () => {
      const params = { keyword: 'english' }
      await searchClasses(params)
      expect(mockRequest).toHaveBeenCalledWith({
        url: '/api/classes/search',
        method: 'get',
        params
      })
    })
  })

  describe('joinClass', () => {
    it('calls POST /api/classes/join with class data', async () => {
      const data = { classCode: 'ABC123' }
      await joinClass(data)
      expect(mockRequest).toHaveBeenCalledWith({
        url: '/api/classes/join',
        method: 'post',
        data
      })
    })
  })

  describe('leaveClass', () => {
    it('calls POST /api/classes/leave with classId in body', async () => {
      await leaveClass(15)
      expect(mockRequest).toHaveBeenCalledWith({
        url: '/api/classes/leave',
        method: 'post',
        data: { classId: 15 }
      })
    })
  })

  // === Extended Stats ===

  describe('getWordLearningStats', () => {
    it('calls GET /api/student/stats/words with params', async () => {
      const params = { period: 'month' }
      await getWordLearningStats(params)
      expect(mockRequest).toHaveBeenCalledWith({
        url: '/api/student/stats/words',
        method: 'get',
        params
      })
    })
  })

  describe('getCourseLearningStats', () => {
    it('calls GET /api/student/stats/courses with params', async () => {
      const params = { courseId: 1 }
      await getCourseLearningStats(params)
      expect(mockRequest).toHaveBeenCalledWith({
        url: '/api/student/stats/courses',
        method: 'get',
        params
      })
    })
  })

  describe('getStudyTimeStats', () => {
    it('calls GET /api/student/stats/study-time with params', async () => {
      const params = { startDate: '2026-01-01', endDate: '2026-03-15' }
      await getStudyTimeStats(params)
      expect(mockRequest).toHaveBeenCalledWith({
        url: '/api/student/stats/study-time',
        method: 'get',
        params
      })
    })
  })

  describe('getProgressReport', () => {
    it('calls GET /api/student/stats/progress-report with params', async () => {
      const params = { courseId: 2 }
      await getProgressReport(params)
      expect(mockRequest).toHaveBeenCalledWith({
        url: '/api/student/stats/progress-report',
        method: 'get',
        params
      })
    })
  })

  describe('getAchievementStats', () => {
    it('calls GET /api/student/stats/achievements', async () => {
      await getAchievementStats()
      expect(mockRequest).toHaveBeenCalledWith({
        url: '/api/student/stats/achievements',
        method: 'get'
      })
    })
  })

  describe('getLearningRanking', () => {
    it('calls GET /api/student/stats/ranking with params', async () => {
      const params = { type: 'weekly' }
      await getLearningRanking(params)
      expect(mockRequest).toHaveBeenCalledWith({
        url: '/api/student/stats/ranking',
        method: 'get',
        params
      })
    })
  })

  describe('exportLearningData', () => {
    it('calls GET /api/student/stats/export with params', async () => {
      const params = { format: 'csv' }
      await exportLearningData(params)
      expect(mockRequest).toHaveBeenCalledWith({
        url: '/api/student/stats/export',
        method: 'get',
        params
      })
    })
  })

  describe('getWordStudyStats', () => {
    it('calls GET /api/student/words/study/stats with params', async () => {
      const params = { period: 'week' }
      await getWordStudyStats(params)
      expect(mockRequest).toHaveBeenCalledWith({
        url: '/api/student/words/study/stats',
        method: 'get',
        params
      })
    })
  })

  // === Error handling ===

  describe('error propagation', () => {
    it('propagates network errors from request', async () => {
      mockRequest.mockRejectedValue(new Error('Network Error'))
      await expect(getStudentDashboard()).rejects.toThrow('Network Error')
    })

    it('propagates server errors from request', async () => {
      mockRequest.mockRejectedValue(new Error('Internal Server Error'))
      await expect(getStudentCourses()).rejects.toThrow('Internal Server Error')
    })
  })
})
