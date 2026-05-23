import request from '@/utils/request'

export const createConversation = (firstQuestion) =>
  request({ url: '/api/student/conversations', method: 'post', data: { firstQuestion } })

export const getConversations = (page = 1, size = 20) =>
  request({ url: '/api/student/conversations', method: 'get', params: { page, size } })

export const getConversationDetail = (id) =>
  request({ url: `/api/student/conversations/${id}`, method: 'get' })

export const deleteConversation = (id) =>
  request({ url: `/api/student/conversations/${id}`, method: 'delete' })

export const shareConversation = (id, options = {}) =>
  request({ url: `/api/student/conversations/${id}/share`, method: 'post', data: options })

export const getShareMeta = (token) =>
  request({ url: `/api/public/shared-conversations/${token}/meta`, method: 'get' })

export const getSharedConversation = (token, password) =>
  request({ url: `/api/public/shared-conversations/${token}`, method: 'post', data: { password } })
