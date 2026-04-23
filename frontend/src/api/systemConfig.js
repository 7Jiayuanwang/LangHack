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

// ---------- HiAgent 凭证配置与连通测试（OPS）----------

export function getHiAgentStatus() {
  return request({ url: '/api/ops/hiagent/status', method: 'get' })
}

export function testHiAgentRewrite(text) {
  return request({ url: '/api/ops/hiagent/test-rewrite', method: 'post', data: text ? { text } : {} })
}

export function testHiAgentQa(payload) {
  return request({ url: '/api/ops/hiagent/test-qa', method: 'post', data: payload || {} })
}

// 功能开关（公开接口，无需鉴权）
export function getFeatureToggles() {
  return request({
    url: '/api/public/feature-toggles',
    method: 'get'
  })
}

// 个性化功能开关（任意已登录用户：学生/教师/运维/管理员，三层合并）
export function getUserFeatureToggles() {
  return request({
    url: '/api/user/feature-toggles',
    method: 'get'
  })
}

// 旧接口保留兼容：学生端专用，实际已被 getUserFeatureToggles 取代
export function getPersonalFeatureToggles() {
  return request({
    url: '/api/user/feature-toggles',
    method: 'get'
  })
}

// ---------- 标签管理（OPS）----------

export function listAllTags() {
  return request({ url: '/api/ops/user-tags', method: 'get' })
}

export function getUsersByTag(tagName) {
  return request({ url: `/api/ops/user-tags/${tagName}/users`, method: 'get' })
}

export function deleteTag(tagName) {
  return request({ url: `/api/ops/user-tags/${tagName}`, method: 'delete' })
}

export function getUserTags(userId) {
  return request({ url: `/api/ops/users/${userId}/tags`, method: 'get' })
}

export function setUserTags(userId, tags) {
  return request({ url: `/api/ops/users/${userId}/tags`, method: 'put', data: tags })
}

// 搜索用户（OPS 用于标签分配时选人）
export function searchUsers(params) {
  return request({ url: '/api/admin/users', method: 'get', params })
}

// ---------- 覆盖规则管理（OPS）----------

export function listFeatureOverrides(scope, scopeValue) {
  return request({
    url: '/api/ops/feature-overrides',
    method: 'get',
    params: { scope, scopeValue }
  })
}

export function upsertFeatureOverride(override) {
  return request({ url: '/api/ops/feature-overrides', method: 'put', data: override })
}

export function deleteFeatureOverride(scope, scopeValue, featureKey) {
  return request({
    url: '/api/ops/feature-overrides',
    method: 'delete',
    params: { scope, scopeValue, featureKey }
  })
}
