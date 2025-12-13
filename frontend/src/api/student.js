import request from '@/utils/request'

// 获取学生仪表板数据
export function getStudentDashboard() {
  return request({
    url: '/api/student/learning/dashboard',
    method: 'get'
  })
}

// 获取今日学习统计
export function getTodayLearningStats() {
  return request({
    url: '/api/student/stats/today',
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
export function getTextDetail(textId) {
  return request({
    url: `/api/student/learning/texts/${textId}`,
    method: 'get'
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
  return request({
    url: '/api/student/words/vocabulary',
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

// 开始单词学习会话
export function startStudySession(data) {
  return request({
    url: '/api/student/words/study/session',
    method: 'post',
    data
  })
}

// 提交单词学习结果
export function submitStudyResult(data) {
  return request({
    url: '/api/student/words/study/submit',
    method: 'post',
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

// 获取单词学习统计
export function getWordStudyStats(params) {
  return request({
    url: '/api/student/words/study/stats',
    method: 'get',
    params
  })
}