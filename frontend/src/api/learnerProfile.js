import request from '@/utils/request'

// V1: Profile APIs
export function getLearnerProfile(userId) {
  return request({
    url: `/api/admin/learner-profile/${userId}`,
    method: 'get'
  })
}

export function getClassLearnerProfileSummary(classId) {
  return request({
    url: `/api/admin/learner-profile/class/${classId}`,
    method: 'get'
  })
}

export function getTeacherStudentProfile(studentId) {
  return request({
    url: `/api/teacher/student-profile/${studentId}`,
    method: 'get'
  })
}

// V2: Event tracking
export function trackRecommendationEvent(textId, eventType, source, position) {
  return request({
    url: '/api/recommendation/event',
    method: 'post',
    data: { textId, eventType, source, position }
  })
}

// V2: Snapshot history
export function getSnapshotHistory(userId, lastNDays = 30) {
  return request({
    url: `/api/admin/learner-profile/${userId}/snapshots`,
    method: 'get',
    params: { lastNDays }
  })
}

// V2: Analytics
export function getAnalyticsOverview() {
  return request({
    url: '/api/admin/learner-profile/analytics/overview',
    method: 'get'
  })
}

export function getRecommendationEffectiveness(lastNDays = 30) {
  return request({
    url: '/api/admin/learner-profile/analytics/recommendation-effectiveness',
    method: 'get',
    params: { lastNDays }
  })
}

export function getAnalyticsTrends(lastNDays = 30) {
  return request({
    url: '/api/admin/learner-profile/analytics/trends',
    method: 'get',
    params: { lastNDays }
  })
}

// V2: Tag calibration
export function getTagEffectiveness(lastNDays = 30) {
  return request({
    url: '/api/admin/learner-profile/tags/effectiveness',
    method: 'get',
    params: { lastNDays }
  })
}

export function getRecalibrationProposals() {
  return request({
    url: '/api/admin/learner-profile/tags/proposals',
    method: 'get'
  })
}

export function submitRecalibration(tagCode, newThreshold, reason) {
  return request({
    url: '/api/admin/learner-profile/tags/recalibrate',
    method: 'post',
    data: { tagCode, newThreshold, reason }
  })
}

// V3: Student-facing profile
export function getMyProfile() {
  return request({
    url: '/api/student/profile/me',
    method: 'get'
  })
}

export function getMyProfileSummary() {
  return request({
    url: '/api/student/profile/me/summary',
    method: 'get'
  })
}

// V3: Admin - generate LLM summary
export function generateLearnerSummary(userId) {
  return request({
    url: `/api/admin/learner-profile/${userId}/generate-summary`,
    method: 'post'
  })
}

// V3: Cohort analysis
export function getCohortAnalysis(classId) {
  return request({
    url: `/api/admin/learner-profile/class/${classId}/cohort-analysis`,
    method: 'get'
  })
}

export function compareClasses(classIds) {
  return request({
    url: '/api/admin/learner-profile/class-comparison',
    method: 'get',
    params: { classIds: classIds.join(',') }
  })
}

// V4: Tag definition CRUD
export function listLearnerTags() {
  return request({
    url: '/api/admin/learner-profile/tag-definitions',
    method: 'get'
  })
}

export function createLearnerTag(data) {
  return request({
    url: '/api/admin/learner-profile/tag-definitions',
    method: 'post',
    data
  })
}

export function updateLearnerTag(id, data) {
  return request({
    url: `/api/admin/learner-profile/tag-definitions/${id}`,
    method: 'put',
    data
  })
}

export function deleteLearnerTag(id) {
  return request({
    url: `/api/admin/learner-profile/tag-definitions/${id}`,
    method: 'delete'
  })
}

export function toggleLearnerTag(id, enabled) {
  return request({
    url: `/api/admin/learner-profile/tag-definitions/${id}/enabled`,
    method: 'patch',
    data: { enabled }
  })
}

export function listTagMetrics() {
  return request({
    url: '/api/admin/learner-profile/tag-definitions/metrics',
    method: 'get'
  })
}
