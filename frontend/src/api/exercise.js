import request from '@/utils/request'

/**
 * 习题管理API
 */

// 创建单个习题
export const createExercise = (data) => {
  return request({
    url: '/api/exercises',
    method: 'post',
    data
  })
}

// 批量创建习题
export const batchCreateExercises = (data) => {
  return request({
    url: '/api/exercises/batch',
    method: 'post',
    data
  })
}

// 获取课文的所有习题
export const getExercisesByTextId = (textId) => {
  return request({
    url: `/api/exercises/text/${textId}`,
    method: 'get'
  })
}

// 获取课文习题数量
export const getExerciseCount = (textId) => {
  return request({
    url: `/api/exercises/text/${textId}/count`,
    method: 'get'
  })
}

// 获取习题详情
export const getExerciseDetail = (exerciseId) => {
  return request({
    url: `/api/exercises/${exerciseId}`,
    method: 'get'
  })
}

// 更新习题
export const updateExercise = (exerciseId, data) => {
  return request({
    url: `/api/exercises/${exerciseId}`,
    method: 'put',
    data
  })
}

// 删除习题
export const deleteExercise = (exerciseId) => {
  return request({
    url: `/api/exercises/${exerciseId}`,
    method: 'delete'
  })
}

// 删除课文的所有习题
export const deleteAllExercisesByTextId = (textId) => {
  return request({
    url: `/api/exercises/text/${textId}`,
    method: 'delete'
  })
}

// 学生提交答案
export const submitAnswer = (data) => {
  return request({
    url: '/api/student/exercises/submit',
    method: 'post',
    data
  })
}

// 获取学生的课文答题记录
export const getStudentAnswersByText = (textId, assignmentId) => {
  const params = assignmentId ? { assignmentId } : {}
  return request({
    url: `/api/student/exercises/text/${textId}`,
    method: 'get',
    params
  })
}

// 获取学生的任务答题记录
export const getStudentAnswersByAssignment = (assignmentId) => {
  return request({
    url: `/api/student/exercises/assignment/${assignmentId}`,
    method: 'get'
  })
}

// 获取学生的任务答题统计
export const getStudentAnswerStats = (assignmentId) => {
  return request({
    url: `/api/student/exercises/assignment/${assignmentId}/stats`,
    method: 'get'
  })
}

// 检查课文习题完成状态
export const checkTextExerciseCompletion = (textId, assignmentId) => {
  const params = assignmentId ? { assignmentId } : {}
  return request({
    url: `/api/student/exercises/text/${textId}/completed`,
    method: 'get',
    params
  })
}