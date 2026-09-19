import request from '@/utils/request'

export const getLearningAgentContext = () =>
  request({ url: '/api/student/agent/context', method: 'get' })

export const getLearningAgentContentRecommendations = (count = 6) =>
  request({
    url: '/api/student/agent/content-recommendations',
    method: 'get',
    params: { count }
  })

export const getLearningAgentStudyLoop = () =>
  request({ url: '/api/student/agent/study-loop', method: 'get' })

export const getArticleLearningInsights = (textId) =>
  request({ url: `/api/student/agent/article-insights/${textId}`, method: 'get' })

export const getUnreadArticleInsights = () =>
  request({ url: '/api/student/agent/article-insights/unread', method: 'get' })

export const markArticleInsightRead = (messageId) =>
  request({ url: `/api/student/agent/article-insights/messages/${messageId}/read`, method: 'post' })

// Only invoked after the student explicitly submits a visible draft for feedback.
export const requestWritingFeedback = (draft) =>
  request({
    url: '/api/student/ai-qa/chat',
    method: 'post',
    data: {
      scene: 'LEARNING_AGENT',
      userQuestion: '这是我自己写的英语作文。请先指出两个具体优点，再指出最重要的两个可改进处，并给出简短修改示例。不要编造未提供的内容。',
      selectedText: draft
    }
  })

export const saveLearningAgentOnboarding = (data) =>
  request({ url: '/api/student/agent/onboarding', method: 'put', data })

export const addLearningAgentMemory = (data) =>
  request({ url: '/api/student/agent/memories', method: 'post', data })

export const deleteLearningAgentMemory = (memoryId) =>
  request({ url: `/api/student/agent/memories/${memoryId}`, method: 'delete' })
