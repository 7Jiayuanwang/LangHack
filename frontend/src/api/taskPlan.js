import request from '@/utils/request'

export const adminTaskPlanApi = {
  list(params) {
    return request({ url: '/api/admin/task-plans', method: 'get', params })
  },
  create(data) {
    return request({ url: '/api/admin/task-plans', method: 'post', data })
  },
  getDetail(id) {
    return request({ url: `/api/admin/task-plans/${id}`, method: 'get' })
  },
  update(id, data) {
    return request({ url: `/api/admin/task-plans/${id}`, method: 'put', data })
  },
  publish(id) {
    return request({ url: `/api/admin/task-plans/${id}/publish`, method: 'post' })
  },
  close(id) {
    return request({ url: `/api/admin/task-plans/${id}/close`, method: 'post' })
  },
  getStats(id) {
    return request({ url: `/api/admin/task-plans/${id}/stats`, method: 'get' })
  },
  exportScore(id) {
    return request({ url: `/api/admin/task-plans/${id}/export`, method: 'get', responseType: 'blob' })
  },
  refresh(id) {
    return request({ url: `/api/admin/task-plans/${id}/refresh`, method: 'post' })
  },
  getCourseLabels() {
    return request({ url: '/api/admin/task-plans/course-labels', method: 'get' })
  },
  getClassesByLabel(courseLabel) {
    return request({ url: '/api/admin/task-plans/classes-by-label', method: 'get', params: { courseLabel } })
  }
}

export const teacherTaskPlanApi = {
  list() {
    return request({ url: '/api/teacher/task-plans', method: 'get' })
  },
  updateOverride(planId, classId, data) {
    return request({ url: `/api/teacher/task-plans/${planId}/classes/${classId}`, method: 'put', data })
  },
  optOut(planId, classId) {
    return request({ url: `/api/teacher/task-plans/${planId}/classes/${classId}/opt-out`, method: 'post' })
  },
  optIn(planId, classId) {
    return request({ url: `/api/teacher/task-plans/${planId}/classes/${classId}/opt-in`, method: 'post' })
  },
  getProgress(planId, classId) {
    return request({ url: `/api/teacher/task-plans/${planId}/classes/${classId}/progress`, method: 'get' })
  },
  exportScore(planId, classId) {
    return request({ url: `/api/teacher/task-plans/${planId}/classes/${classId}/export`, method: 'get', responseType: 'blob' })
  }
}

export const studentTaskPlanApi = {
  list() {
    return request({ url: '/api/student/task-plans', method: 'get' })
  }
}
