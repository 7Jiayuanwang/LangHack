import request from '@/utils/request'

export function getSystemConfig(group) {
  return request({
    url: '/api/ops/system-config',
    method: 'get',
    params: { group }
  })
}

export function updateSystemConfig(configs) {
  return request({
    url: '/api/ops/system-config',
    method: 'put',
    data: configs
  })
}

export function testTranslate(text) {
  return request({
    url: '/api/ops/system-config/test-translate',
    method: 'post',
    data: { text }
  })
}
