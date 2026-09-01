import request from '@/utils/request'

export const getLearningAgentContext = () =>
  request({ url: '/api/student/agent/context', method: 'get' })

export const getLearningAgentContentRecommendations = (count = 6) =>
  request({
    url: '/api/student/agent/content-recommendations',
    method: 'get',
    params: { count }
  })

export const saveLearningAgentOnboarding = (data) =>
  request({ url: '/api/student/agent/onboarding', method: 'put', data })

export const addLearningAgentMemory = (data) =>
  request({ url: '/api/student/agent/memories', method: 'post', data })

export const deleteLearningAgentMemory = (memoryId) =>
  request({ url: `/api/student/agent/memories/${memoryId}`, method: 'delete' })
