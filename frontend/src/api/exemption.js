import request from '@/utils/request'

/**
 * 教师端大学英语免修/免考审批
 * 规则依据《内蒙古农业大学关于大学英语课程免修、免考实施细则（试行）》
 */

/** 待审批/已审批数量统计（首页角标用） */
export function getExemptionStats() {
  return request({ url: '/api/teacher/exemption/stats', method: 'get' })
}

/** 申请列表 status: PENDING | APPROVED | REJECTED | ''(全部) */
export function listExemptionApplications(params) {
  return request({ url: '/api/teacher/exemption/applications', method: 'get', params })
}

export function getExemptionDetail(id) {
  return request({ url: `/api/teacher/exemption/applications/${id}`, method: 'get' })
}

export function approveExemption(id, comment) {
  return request({ url: `/api/teacher/exemption/applications/${id}/approve`, method: 'post', data: { comment } })
}

export function rejectExemption(id, comment) {
  return request({ url: `/api/teacher/exemption/applications/${id}/reject`, method: 'post', data: { comment } })
}
