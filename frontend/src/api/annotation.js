import request from '@/utils/request'

export const getAnnotations = (textId) =>
  request({ url: `/api/student/annotations/text/${textId}`, method: 'get' })

export const createAnnotation = (data) =>
  request({ url: '/api/student/annotations', method: 'post', data })

export const deleteAnnotation = (id) =>
  request({ url: `/api/student/annotations/${id}`, method: 'delete' })

export const toggleUpvote = (id) =>
  request({ url: `/api/student/annotations/${id}/upvote`, method: 'post' })
