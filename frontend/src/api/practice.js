import request from '@/utils/request'

// 获取练习题目列表
export function getPracticeQuestions(params) {
  return request({
    url: '/api/student/practice/questions',
    method: 'get',
    params
  })
}

// 开始练习会话
export function startPracticeSession(data) {
  return request({
    url: '/api/student/practice/session/start',
    method: 'post',
    data
  })
}

// 获取练习题目详情
export function getQuestionDetail(questionId) {
  return request({
    url: `/api/student/practice/questions/${questionId}`,
    method: 'get'
  })
}

// 提交答案
export function submitAnswer(data) {
  return request({
    url: '/api/student/practice/submit',
    method: 'post',
    data
  })
}

// 获取答案解析
export function getAnswerAnalysis(questionId) {
  return request({
    url: `/api/student/practice/questions/${questionId}/analysis`,
    method: 'get'
  })
}

// 完成练习会话
export function completePracticeSession(sessionId, data) {
  return request({
    url: `/api/student/practice/session/${sessionId}/complete`,
    method: 'post',
    data
  })
}

// 获取练习历史记录
export function getPracticeHistory(params) {
  return request({
    url: '/api/student/practice/history',
    method: 'get',
    params
  })
}

// 获取练习统计
export function getPracticeStats(params) {
  return request({
    url: '/api/student/practice/stats',
    method: 'get',
    params
  })
}

// 获取推荐练习题目
export function getRecommendedQuestions(params) {
  return request({
    url: '/api/student/practice/recommended',
    method: 'get',
    params
  })
}

// 收藏题目
export function favoriteQuestion(questionId) {
  return request({
    url: `/api/student/practice/questions/${questionId}/favorite`,
    method: 'post'
  })
}

// 取消收藏题目
export function unfavoriteQuestion(questionId) {
  return request({
    url: `/api/student/practice/questions/${questionId}/favorite`,
    method: 'delete'
  })
}

// 标记难题
export function markDifficult(questionId) {
  return request({
    url: `/api/student/practice/questions/${questionId}/difficult`,
    method: 'post'
  })
}

// 获取错题集
export function getWrongQuestions(params) {
  return request({
    url: '/api/student/practice/wrong-questions',
    method: 'get',
    params
  })
}
