import request from '@/utils/request'

export function getMyTeachingClasses(params) {
  return request({ url: '/api/teacher/my-courses', method: 'get', params })
}

export function getMyStudents(params) {
  return request({ url: '/api/teacher/my-students', method: 'get', params })
}

export function getMySchedule(params) {
  return request({ url: '/api/teacher/my-schedule', method: 'get', params })
}

export function getMyCourseSelections(params) {
  return request({ url: '/api/teacher/my-course-selections', method: 'get', params })
}

export function getStudentScores(studentId) {
  return request({ url: `/api/teacher/student/${studentId}/scores`, method: 'get' })
}

export function getTeacherStats() {
  return request({ url: '/api/teacher/stats', method: 'get' })
}

export function getClassLearningOverview(classId) {
  return request({
    url: `/api/teacher/classes/${encodeURIComponent(classId)}/learning-overview`,
    method: 'get',
    skipErrorHandler: true
  })
}

export function getMyPlatformClasses(page = 1) {
  return request({ url: '/api/classes', method: 'get', params: { page, size: 100 }, skipErrorHandler: true })
}
