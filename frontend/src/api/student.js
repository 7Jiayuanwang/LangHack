import request from '@/utils/request'

// 获取学生仪表板数据
export function getStudentDashboard() {
  return request({
    url: '/api/student/learning/dashboard',
    method: 'get'
  })
}


// 获取学生课程列表
export function getStudentCourses() {
  return request({
    url: '/api/student/learning/courses',
    method: 'get'
  })
}

// 获取课程下的单元列表
export function getCourseUnits(courseId) {
  return request({
    url: `/api/student/learning/courses/${courseId}/units`,
    method: 'get'
  })
}

// 获取单元详细内容（课文和单词）
export function getUnitContent(unitId) {
  return request({
    url: `/api/student/learning/units/${unitId}/content`,
    method: 'get'
  })
}

// 获取课文详细内容
export function getTextDetail(params) {
  // 如果传入的是数字，保持原有逻辑
  if (typeof params === 'number') {
    // BUG FIX: 添加textId范围检查
    if (params <= 0) {
      return Promise.reject(new Error('textId 必须大于0'))
    }
    return request({
      url: `/api/student/learning/texts/${params}`,
      method: 'get'
    })
  }

  // 如果传入的是对象，使用查询参数
  const { textId, assignmentId } = params || {}

  // 防御：textId 必须存在且为有效值
  if (!textId || (typeof textId !== 'number' && typeof textId !== 'string')) {
    return Promise.reject(new Error('textId 不能为空或类型错误'))
  }

  
  // BUG FIX: textId范围检查
  const textIdNum = typeof textId === 'string' ? parseInt(textId, 10) : textId
  if (isNaN(textIdNum) || textIdNum <= 0) {
    return Promise.reject(new Error('textId 必须是有效的正整数'))
  }
  return request({
    url: `/api/student/learning/texts/${textId}`,
    method: 'get',
    params: assignmentId ? { assignmentId } : {}
  })
}

// 获取单词详细信息
export function getWordDetail(wordId) {
  return request({
    url: `/api/student/words/${wordId}/detail`,
    method: 'get'
  })
}

// 添加单词到生词本
export function addToVocabulary(data) {
  if (!data || typeof data !== 'object') {
    return Promise.reject(new Error('数据不能为空'))
  }
  return request({
    url: '/api/student/learning/vocabulary/add',
    method: 'post',
    data
  })
}

// 获取生词本列表
export function getVocabularyList(params) {
  return request({
    url: '/api/student/words/vocabulary',
    method: 'get',
    params
  }).then(response => {
    // 防御：null 响应检查在前
    if (!response) {
      return { data: { records: [], total: 0 } }
    }

    // 防御：确保响应中 data 字段是数组或具有 records 属性
    if (response && typeof response === 'object' && response.data) {
      if (Array.isArray(response.data)) {
        response.data = { records: response.data, total: response.data.length }
      } else if (!response.data.records) {
        response.data.records = []
      }
    } else if (response && typeof response === 'object') {
      response = { ...response, data: { records: [], total: 0 } }
    } else {
      response = { data: { records: [], total: 0 } }
    }
    return response
  }).catch(error => {
    // 错误时返回空数据，防止页面崩溃
    console.error('getVocabularyList error:', error)
    return { data: { records: [], total: 0 } }
  })
}

// 更新生词掌握程度
export function updateMasteryLevel(wordId, data) {
  return request({
    url: `/api/student/words/vocabulary/${wordId}/mastery`,
    method: 'put',
    data
  })
}


// 获取学生任务列表
export function getStudentAssignments(params) {
  return request({
    url: '/api/student/assignments',
    method: 'get',
    params
  })
}

// 开始执行任务
export function startAssignment(assignmentId) {
  return request({
    url: `/api/student/assignments/${assignmentId}/start`,
    method: 'post'
  })
}

// 获取学习统计数据
export function getLearningOverview(params) {
  return request({
    url: '/api/student/stats/overview',
    method: 'get',
    params
  })
}

// 获取详细学习记录
export function getLearningRecords(params) {
  return request({
    url: '/api/student/stats/records',
    method: 'get',
    params
  })
}

// 获取我的班级列表
export function getMyClasses() {
  return request({
    url: '/api/classes/student/me',
    method: 'get'
  })
}

// 查找班级
export function searchClasses(params) {
  return request({
    url: '/api/classes/search',
    method: 'get',
    params
  })
}

// 加入班级
export function joinClass(data) {
  return request({
    url: '/api/classes/join',
    method: 'post',
    data
  })
}

// 退出班级
export function leaveClass(classId) {
  return request({
    url: '/api/classes/leave',
    method: 'post',
    data: { classId }
  })
}

// 获取单词学习统计
export function getWordLearningStats(params) {
  return request({
    url: '/api/student/stats/words',
    method: 'get',
    params
  })
}

// 获取课程学习统计
export function getCourseLearningStats(params) {
  return request({
    url: '/api/student/stats/courses',
    method: 'get',
    params
  })
}

// 获取学习时长统计
export function getStudyTimeStats(params) {
  return request({
    url: '/api/student/stats/study-time',
    method: 'get',
    params
  })
}

// 获取进度报告
export function getProgressReport(params) {
  return request({
    url: '/api/student/stats/progress-report',
    method: 'get',
    params
  })
}

// 获取成就统计
export function getAchievementStats() {
  return request({
    url: '/api/student/stats/achievements',
    method: 'get'
  })
}

// 获取学习排名
export function getLearningRanking(params) {
  return request({
    url: '/api/student/stats/ranking',
    method: 'get',
    params
  })
}

// 导出学习数据
export function exportLearningData(params) {
  return request({
    url: '/api/student/stats/export',
    method: 'get',
    params
  })
}

// 批量操作生词本
export function batchOperateVocabulary(data) {
  return request({
    url: '/api/student/words/vocabulary/batch',
    method: 'post',
    data
  })
}

// 获取单词学习统计
export function getWordStudyStats(params) {
  return request({
    url: '/api/student/words/study/stats',
    method: 'get',
    params
  })
}

// 获取今日学习统计
export function getTodayLearningStats() {
  return request({
    url: '/api/student/stats/today',
    method: 'get'
  })
}

// 开始学习会话
export function startStudySession(unitId) {
  return request({
    url: '/api/student/words/study/session',
    method: 'post',
    data: { unitId }
  })
}

// 提交学习结果
export function submitStudyResult(data) {
  return request({
    url: '/api/student/words/study/submit',
    method: 'post',
    data
  })
}

// 获取MCQ测验干扰项（从ECDICT词库生成）
export function getQuizDistractors(word, count = 3) {
  return request({
    url: '/api/student/words/quiz-options',
    method: 'get',
    params: { word, count }
  })
}

// 获取MCQ完整选项数据（正确答案 + 干扰项，均来自ECDICT词库）
export function getQuizData(word, count = 3) {
  return request({
    url: '/api/student/words/quiz-data',
    method: 'get',
    params: { word, count }
  })
}

// ==================== 新增的生词本和WordNet相关API ====================

// 获取生词本（更新版API）
export function getVocabulary(params) {
  return request({
    url: '/api/student/learning/vocabulary',
    method: 'get',
    params
  }).then(response => {
    // 防御：确保响应中 data 字段是数组或具有 records 属性（与 getVocabularyList 保持一致）
    if (response && typeof response === 'object' && response.data) {
      if (Array.isArray(response.data)) {
        response.data = { records: response.data, total: response.data.length }
      } else if (!response.data.records) {
        response.data.records = []
      }
    } else if (response && typeof response === 'object') {
      response = { ...response, data: { records: [], total: 0 } }
    } else {
      response = { data: { records: [], total: 0 } }
    }
    return response
  }).catch(error => {
    console.error('getVocabulary error:', error)
    return { data: { records: [], total: 0 } }
  })
}

// 检查单词是否在生词本中
export function checkVocabularyStatus(word) {
  // 输入验证
  if (!word || typeof word !== 'string' || word.trim().length === 0) {
    return Promise.reject(new Error('单词不能为空'))
  }

  return request({
    url: '/api/student/learning/vocabulary/check',
    method: 'get',
    params: { word: word.trim() }
  })
}

// 通过单词添加到生词本（支持WordNet）
export function addWordToVocabulary(data) {
  return request({
    url: '/api/student/learning/vocabulary',
    method: 'post',
    data
  })
}

// 从生词本移除单词
export function removeFromVocabulary(wordId) {
  return request({
    url: `/api/student/learning/vocabulary/${wordId}`,
    method: 'delete'
  })
}

// 更新掌握程度
export function updateVocabularyMastery(wordId, data) {
  return request({
    url: `/api/student/learning/vocabulary/${wordId}/mastery`,
    method: 'put',
    data
  })
}

// 获取生词本统计
export function getVocabularyStats() {
  return request({
    url: '/api/student/learning/vocabulary/stats',
    method: 'get'
  })
}

// ==================== 学习记录相关API ====================

// 记录课文学习
export function recordTextView(data) {
  return request({
    url: '/api/student/learning/record/text-view',
    method: 'post',
    data
  })
}

// 记录单词学习
export function recordWordView(data) {
  return request({
    url: '/api/student/learning/record/word-view',
    method: 'post',
    data
  })
}

// 通用学习记录接口
export function recordLearning(data) {
  return request({
    url: '/api/student/learning/record/learning',
    method: 'post',
    data
  })
}

// 标记课文为完成
export function markTextAsCompleted(textId, assignmentId = null) {
  return request({
    url: `/api/student/learning/texts/${textId}/complete`,
    method: 'post',
    params: assignmentId ? { assignmentId } : {}
  })
}

// 开始阅读课文并创建会话记录
export function startReading(textId, assignmentId = null) {
  return request({
    url: `/api/student/learning/texts/${textId}/start`,
    method: 'post',
    params: assignmentId ? { assignmentId } : {}
  })
}

// 完成阅读课文会话
export function completeReading(recordId) {
  return request({
    url: `/api/student/learning/records/${recordId}/complete`,
    method: 'post'
  })
}

// ==================== 学生课表相关API ====================

// 获取当前学生课表
export function getStudentSchedule(params) {
  return request({
    url: '/api/student/schedule',
    method: 'get',
    params
  })
}

// 从学校API刷新课表数据
export function refreshStudentSchedule(params) {
  return request({
    url: '/api/student/schedule',
    method: 'get',
    params: { ...params, refresh: true }
  })
}

// 获取当前学生等级考试成绩
export function getMyScores(params) {
  return request({
    url: '/api/student/scores',
    method: 'get',
    params
  })
}

// 获取当前学生选课记录
export function getMyCourseSelections(params) {
  return request({
    url: '/api/student/course-selections',
    method: 'get',
    params
  })
}

// ==================== 文章收藏相关API ====================

// 收藏/取消收藏文章
export function toggleArticleFavorite(textId) {
  return request({
    url: `/api/student/learning/favorites/${textId}`,
    method: 'post'
  })
}

// 检查文章是否已收藏
export function checkArticleFavorite(textId) {
  return request({
    url: `/api/student/learning/favorites/${textId}/check`,
    method: 'get'
  })
}

// 获取收藏文章列表
export function getFavoriteArticles(params) {
  return request({
    url: '/api/student/learning/favorites',
    method: 'get',
    params
  })
}

// 获取阅读记录列表
export function getReadingHistory(params) {
  return request({
    url: '/api/student/learning/reading-history',
    method: 'get',
    params
  })
}

// 默认导出所有API方法
export function createUnderline(data) {
  return request({
    url: '/api/underlines',
    method: 'post',
    data
  })
}

export function getUnderlines(textId) {
  return request({
    url: `/api/underlines/text/${textId}`,
    method: 'get'
  })
}

export function deleteUnderline(underlineId) {
  return request({
    url: `/api/underlines/${underlineId}`,
    method: 'delete'
  })
}

const studentApi = {
  getStudentDashboard,
  getStudentCourses,
  getCourseUnits,
  getUnitContent,
  getTextDetail,
  getWordDetail,
  addToVocabulary,
  getVocabularyList,
  updateMasteryLevel,
  getStudentAssignments,
  startAssignment,
  getLearningOverview,
  getLearningRecords,
  getMyClasses,
  searchClasses,
  joinClass,
  leaveClass,
  getWordLearningStats,
  getCourseLearningStats,
  getStudyTimeStats,
  getProgressReport,
  getAchievementStats,
  getLearningRanking,
  exportLearningData,
  batchOperateVocabulary,
  getWordStudyStats,
  getTodayLearningStats,
  startStudySession,
  submitStudyResult,
  getQuizDistractors,
  // 新增的API
  getVocabulary,
  checkVocabularyStatus,
  addWordToVocabulary,
  removeFromVocabulary,
  updateVocabularyMastery,
  getVocabularyStats,
  recordTextView,
  recordWordView,
  recordLearning,
  markTextAsCompleted,
  startReading,
  completeReading,
  createUnderline,
  getUnderlines,
  deleteUnderline,
  // 收藏和阅读记录
  toggleArticleFavorite,
  checkArticleFavorite,
  getFavoriteArticles,
  getReadingHistory,
  // 学生课表
  getStudentSchedule,
  refreshStudentSchedule,
  // 学生成绩和选课
  getMyScores,
  getMyCourseSelections
}

export default studentApi

// ==================== WordNet相关API ====================

// 获取单词详细信息（WordNet增强版）
export function getWordNetInfo(word) {
  // 输入验证
  if (!word || typeof word !== 'string' || word.trim().length === 0) {
    return Promise.reject(new Error('单词不能为空'))
  }

  // BUG FIX: 对trim后的值进行长度检查
  const trimmedWord = word.trim()
  if (trimmedWord.length > 100) {
    return Promise.reject(new Error('单词长度不能超过 100'))
  }

  return request({
    url: `/api/wordnet/word/${encodeURIComponent(trimmedWord)}`,
    method: 'get'
  })
}

// 获取文本中的可点击单词
export function getClickableWordsFromText(text) {
  // 输入验证
  if (!text || typeof text !== 'string' || text.trim().length === 0) {
    return Promise.reject(new Error('文本不能为空'))
  }
  if (text.length > 50000) {
    return Promise.reject(new Error('文本长度不能超过 50000'))
  }

  return request({
    url: '/api/wordnet/text/words',
    method: 'post',
    data: { text },
    timeout: 30000  // 文本处理可能较慢，延长超时时间
  })
}

// 批量获取单词信息
export function getBatchWordInfo(words) {
  // 输入验证
  if (!Array.isArray(words) || words.length === 0) {
    return Promise.reject(new Error('单词数组不能为空'))
  }
  if (words.length > 200) {
    return Promise.reject(new Error('单词数量不能超过 200'))
  }

  return request({
    url: '/api/wordnet/words/batch',
    method: 'post',
    data: {
      words: words.slice(0, 200)  // 强制截断防止超限
    },
    timeout: 30000
  })
}

// 获取WordNet服务状态
export function getWordNetStatus() {
  return request({
    url: '/api/wordnet/status',
    method: 'get'
  })
}