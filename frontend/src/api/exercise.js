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
  // 输入验证
  if (!data || typeof data !== 'object') {
    return Promise.reject(new Error('数据不能为空'))
  }

  if (!Array.isArray(data.exercises) || data.exercises.length === 0) {
    return Promise.reject(new Error('习题列表不能为空'))
  }

  if (data.exercises.length > 100) {
    return Promise.reject(new Error('批量创建习题不能超过100个'))
  }

  // 校验单个习题的必填字段和选项
  for (let i = 0; i < data.exercises.length; i++) {
    const ex = data.exercises[i]
    if (!ex.question || ex.question.trim().length === 0) {
      return Promise.reject(new Error(`第${i + 1}个习题的题目不能为空`))
    }
    if (!ex.exerciseType || ex.exerciseType.trim().length === 0) {
      return Promise.reject(new Error(`第${i + 1}个习题的题型不能为空`))
    }
    if (ex.orderNum === undefined || ex.orderNum === null) {
      return Promise.reject(new Error(`第${i + 1}个习题的序号不能为空`))
    }
    if (ex.question.length > 5000) {
      return Promise.reject(new Error(`第${i + 1}个习题的题目过长，不能超过5000字符`))
    }

    // 校验选项存在性（C2修复）
    if (!Array.isArray(ex.options) || ex.options.length === 0) {
      return Promise.reject(new Error(`第${i + 1}个习题的选项不能为空`))
    }
    if (ex.options.length > 10) {
      return Promise.reject(new Error(`第${i + 1}个习题的选项不能超过10个`))
    }

    // 校验每个选项的必填字段和长度（C2修复）
    for (let j = 0; j < ex.options.length; j++) {
      const opt = ex.options[j]
      if (!opt.optionContent || opt.optionContent.trim().length === 0) {
        return Promise.reject(new Error(`第${i + 1}个习题第${j + 1}个选项内容不能为空`))
      }
      if (!opt.optionKey || opt.optionKey.trim().length === 0) {
        return Promise.reject(new Error(`第${i + 1}个习题第${j + 1}个选项标识不能为空`))
      }
      if (opt.optionContent.length > 2000) {
        return Promise.reject(new Error(`第${i + 1}个习题第${j + 1}个选项过长，不能超过2000字符`))
      }
    }

    // 验证至少有一个正确答案（C2修复）
    const hasTrueAnswer = ex.options.some(opt => opt.isCorrect === true || opt.isCorrect === 1)
    if (!hasTrueAnswer) {
      return Promise.reject(new Error(`第${i + 1}个习题必须至少有一个正确选项`))
    }
  }

  return request({
    url: '/api/exercises/batch',
    method: 'post',
    data,
    timeout: 30000  // 批量操作延长超时时间
  })
}

/**
 * 获取课文的所有习题
 * 权限: ADMIN/TEACHER/STUDENT/USER
 * 注意: 后端会根据用户角色决定是否返回正确答案标记
 * 学生用户返回时已隐藏isCorrect字段，教师/管理员可见
 */
export const getExercisesByTextId = (textId) => {
  return request({
    url: `/api/exercises/text/${textId}`,
    method: 'get'
  })
  // C1修复: 移除前端防御层，依赖后端的角色控制
}

// 获取课文习题数量
export const getExerciseCount = (textId) => {
  return request({
    url: `/api/exercises/text/${textId}/count`,
    method: 'get'
  })
}

/**
 * 获取习题详情
 * 权限: ADMIN/TEACHER/STUDENT/USER
 * 注意: 后端会根据用户角色决定是否返回正确答案标记
 */
export const getExerciseDetail = (exerciseId) => {
  return request({
    url: `/api/exercises/${exerciseId}`,
    method: 'get'
  })
  // C1修复: 移除前端防御层，依赖后端的角色控制
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

/**
 * 学生提交答案
 * 必要字段验证已添加（H1修复）
 */
export const submitAnswer = (data) => {
  // 输入验证
  if (!data || typeof data !== 'object') {
    return Promise.reject(new Error('提交数据不能为空'))
  }
  if (!data.exerciseId) {
    return Promise.reject(new Error('习题ID不能为空'))
  }
  // H1修复: 验证答案字段
  if (data.answerContent === undefined || data.answerContent === null) {
    return Promise.reject(new Error('答案不能为空'))
  }
  if (typeof data.answerContent !== 'string' && typeof data.answerContent !== 'number') {
    return Promise.reject(new Error('答案格式错误'))
  }

  return request({
    url: '/api/student/exercises/submit',
    method: 'post',
    data,
    timeout: 10000  // M3修复: 添加合理超时时间
  })
}

// 批量提交 AI 出题答案（返回对错 + 解析）
// payload: { answers: [{exerciseId, userAnswer, timeSpentSeconds?}], sessionId?, assignmentId? }
export const submitQuizAnswers = (payload) => {
  const data = Array.isArray(payload) ? { answers: payload } : payload
  return request({
    url: '/api/student/exercises/submit-quiz',
    method: 'post',
    data,
    timeout: 15000
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

// M4修复: 添加导出对象方便批量导入
const exerciseApi = {
  createExercise,
  batchCreateExercises,
  getExercisesByTextId,
  getExerciseCount,
  getExerciseDetail,
  updateExercise,
  deleteExercise,
  deleteAllExercisesByTextId,
  submitAnswer,
  submitQuizAnswers,
  getStudentAnswersByText,
  getStudentAnswersByAssignment,
  getStudentAnswerStats,
  checkTextExerciseCompletion
}

export default exerciseApi
