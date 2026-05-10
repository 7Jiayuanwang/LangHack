import request from '@/utils/request'

/**
 * 获取学校数据配置
 */
export function getSchoolDataConfig() {
  return request({ url: '/api/ops/system-config', method: 'get', params: { group: 'school_data' } })
}

/**
 * 更新学校数据配置
 */
export function updateSchoolDataConfig(configs) {
  return request({ url: '/api/ops/school-data/config', method: 'put', data: configs })
}

/**
 * 测试学校数据源连接
 */
export function testSchoolDataConnection() {
  return request({ url: '/api/ops/school-data/test-connection', method: 'post' })
}

/**
 * 查询学校数据集成配置状态（含 authConfigured / missingKeys / lastTestResult 等）
 */
export function getSchoolDataConfigStatus() {
  return request({ url: '/api/ops/school-data/config-status', method: 'get' })
}

// ==================== 学校数据查询（管理员/运维） ====================

/**
 * 获取学校数据概览（各表记录数）
 */
export function getSchoolDataOverview() {
  return request({ url: '/api/admin/school-data/overview', method: 'get' })
}

/**
 * 查询行政班数据
 */
export function getAdministrativeClasses(params) {
  return request({ url: '/api/admin/school-data/administrative-classes', method: 'get', params })
}

/**
 * 查询课程数据
 */
export function getSchoolCourses(params) {
  return request({ url: '/api/admin/school-data/courses', method: 'get', params })
}

/**
 * 查询教学班数据
 */
export function getTeachingClasses(params) {
  return request({ url: '/api/admin/school-data/teaching-classes', method: 'get', params })
}

/**
 * 查询课程安排数据
 */
export function getCourseSchedules(params) {
  return request({ url: '/api/admin/school-data/course-schedules', method: 'get', params })
}

/**
 * 查询选课记录数据
 */
export function getCourseSelections(params) {
  return request({ url: '/api/admin/school-data/course-selections', method: 'get', params })
}

/**
 * 查询等级考试成绩数据
 */
export function getLevelExamScores(params) {
  return request({ url: '/api/admin/school-data/level-exam-scores', method: 'get', params })
}

/**
 * 查询教学计划数据
 */
export function getTeachingPlans(params) {
  return request({ url: '/api/admin/school-data/teaching-plans', method: 'get', params })
}

/**
 * 查询教学计划课程明细
 */
export function getTeachingPlanCourses(planId) {
  return request({ url: `/api/admin/school-data/teaching-plans/${planId}/courses`, method: 'get' })
}

/**
 * 查询学生课表数据（管理员视角）
 */
export function getStudentSchedules(params) {
  return request({ url: '/api/admin/school-data/student-schedules', method: 'get', params })
}

/**
 * 获取指定数据源的筛选字段元数据
 */
export function getSchoolDataFilterFields(dataSource) {
  return request({ url: `/api/admin/school-data/${dataSource}/filter-fields`, method: 'get' })
}

/**
 * 使用统一 filterGroups 查询学校数据
 */
export function querySchoolData(dataSource, data) {
  return request({ url: `/api/admin/school-data/${dataSource}/query`, method: 'post', data })
}

export function fullSyncSchoolDataFiltered(dataSource, data) {
  return request({ url: `/api/admin/sync/full/${dataSource}/filtered`, method: 'post', data, timeout: 3600000 })
}

export function incrementalSyncSchoolDataFiltered(dataSource, data) {
  return request({ url: `/api/admin/sync/incremental/${dataSource}/filtered`, method: 'post', data, timeout: 3600000 })
}

export function fullSyncAllSchoolData(params) {
  return request({ url: '/api/admin/sync/full-all', method: 'post', params, timeout: 3600000 })
}

export function incrementalSyncAllSchoolData(params) {
  return request({ url: '/api/admin/sync/incremental-all', method: 'post', params, timeout: 3600000 })
}

/**
 * 按学院链式拉取学生/教师/绑定关系
 * POST /api/admin/sync/chain/students-by-college?collegeName=外国语学院&matchMode=eq&persist=true
 */
export function chainFetchStudentsByCollege(params) {
  return request({ url: '/api/admin/sync/chain/students-by-college', method: 'post', params, timeout: 1800000 })
}
