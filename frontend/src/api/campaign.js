import request from '@/utils/request'

// ========== 管理员端 API ==========

// 活动管理
export const getCampaigns = (params) => request.get('/api/admin/campaigns', { params })
export const getCampaignById = (id) => request.get(`/api/admin/campaigns/${id}`)
export const createCampaign = (data) => request.post('/api/admin/campaigns', data)
export const updateCampaign = (id, data) => request.put(`/api/admin/campaigns/${id}`, data)
export const deleteCampaign = (id) => request.delete(`/api/admin/campaigns/${id}`)
export const startCampaign = (id) => request.put(`/api/admin/campaigns/${id}/start`)
export const endCampaign = (id) => request.put(`/api/admin/campaigns/${id}/end`)

// 获取报名范围选项
export const getColleges = () => request.get('/api/admin/campaigns/enrollment-options/colleges')
export const getGrades = (collegeNames) => {
  const params = collegeNames ? { collegeNames } : {}
  return request.get('/api/admin/campaigns/enrollment-options/grades', { params })
}
export const getClasses = () => request.get('/api/admin/campaigns/enrollment-options/classes')

// 任务管理
export const getTasks = (campaignId) => request.get(`/api/admin/campaigns/${campaignId}/tasks`)
export const createTask = (campaignId, data) => request.post(`/api/admin/campaigns/${campaignId}/tasks`, data)
export const updateTask = (campaignId, taskId, data) => request.put(`/api/admin/campaigns/${campaignId}/tasks/${taskId}`, data)
export const deleteTask = (campaignId, taskId) => request.delete(`/api/admin/campaigns/${campaignId}/tasks/${taskId}`)

// 统计与排名
export const getCampaignOverview = (campaignId) => request.get(`/api/admin/campaigns/${campaignId}/stats/overview`)
export const getIndividualRanking = (campaignId, params) => request.get(`/api/admin/campaigns/${campaignId}/stats/ranking/individual`, { params })
export const getClassRanking = (campaignId) => request.get(`/api/admin/campaigns/${campaignId}/stats/ranking/class`)
export const getCollegeRanking = (campaignId) => request.get(`/api/admin/campaigns/${campaignId}/stats/ranking/college`)
export const collectData = (campaignId) => request.post(`/api/admin/campaigns/${campaignId}/stats/collect`)

// 数据录入与导出
export const recordManualData = (campaignId, data) => request.post(`/api/admin/campaigns/${campaignId}/data/manual`, data)
export const importData = (campaignId, taskId, file) => {
  const formData = new FormData()
  formData.append('file', file)
  return request.post(`/api/admin/campaigns/${campaignId}/data/import`, formData, {
    params: { taskId },
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}
export const exportCampaignData = (campaignId, format) => request.get(`/api/admin/campaigns/${campaignId}/data/export`, { params: { format }, responseType: 'blob' })
export const exportRankingData = (campaignId, dimension, format) => request.get(`/api/admin/campaigns/${campaignId}/data/export-ranking`, { params: { dimension, format }, responseType: 'blob' })

// 报名管理
export const getPendingEnrollments = (campaignId) => request.get(`/api/admin/campaigns/${campaignId}/enrollments/pending`)
export const getEnrolledList = (campaignId) => request.get(`/api/admin/campaigns/${campaignId}/enrollments/enrolled`)
export const getRejectedList = (campaignId) => request.get(`/api/admin/campaigns/${campaignId}/enrollments/rejected`)
export const approveEnrollment = (campaignId, participationId) => request.put(`/api/admin/campaigns/${campaignId}/enrollments/${participationId}/approve`)
export const rejectEnrollment = (campaignId, participationId, reason) => request.put(`/api/admin/campaigns/${campaignId}/enrollments/${participationId}/reject`, { reason })
export const batchEnrollUsers = (campaignId, userIds) => request.post(`/api/admin/campaigns/${campaignId}/enrollments/batch-enroll`, { userIds })

// ========== 学生端 API ==========

// 活动浏览
export const getEnrollableCampaigns = (params) => request.get('/api/student/campaigns', { params })
export const getMyEnrolledCampaigns = (params) => request.get('/api/student/campaigns/enrolled', { params })
export const getStudentCampaignDetail = (id) => request.get(`/api/student/campaigns/${id}`)

// 报名操作
export const enrollCampaign = (id) => request.post(`/api/student/campaigns/${id}/enroll`)
export const withdrawCampaign = (id) => request.delete(`/api/student/campaigns/${id}/withdraw`)

// 进度查询
export const getMyProgress = (id) => request.get(`/api/student/campaigns/${id}/progress`)
export const getMyTaskProgress = (id) => request.get(`/api/student/campaigns/${id}/tasks`)
export const getMyRanking = (id) => request.get(`/api/student/campaigns/${id}/ranking`)
