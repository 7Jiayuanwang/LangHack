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

// AI 服务商管理
export function listAiProviders(service) {
  return request({
    url: '/api/ops/ai-providers',
    method: 'get',
    params: service ? { service } : {}
  })
}

export function createAiProvider(data) {
  return request({ url: '/api/ops/ai-providers', method: 'post', data })
}

export function updateAiProvider(id, data) {
  return request({ url: `/api/ops/ai-providers/${id}`, method: 'put', data })
}

export function deleteAiProvider(id) {
  return request({ url: `/api/ops/ai-providers/${id}`, method: 'delete' })
}

export function toggleAiProvider(id, enabled) {
  return request({ url: `/api/ops/ai-providers/${id}/enabled`, method: 'patch', data: { enabled } })
}

export function testAiProvider(id, text) {
  return request({ url: `/api/ops/ai-providers/${id}/test`, method: 'post', data: { text } })
}

// 功能开关（公开接口，无需鉴权）
export function getFeatureToggles() {
  return request({
    url: '/api/public/feature-toggles',
    method: 'get'
  })
}
