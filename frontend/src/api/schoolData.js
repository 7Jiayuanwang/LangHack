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
