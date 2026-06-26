import request from '@/utils/request'

export const getQaUsageStats = () =>
  request({ url: '/api/student/ai-qa/usage/stats', method: 'get' })
