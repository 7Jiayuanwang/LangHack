import request from '@/utils/request'

const endpoint = textId => `/api/student/learning/texts/${textId}/audio`
const params = (version, assignmentId) => ({
  version,
  ...(assignmentId ? { assignmentId } : {})
})

export function getArticleAudioStatus(textId, version, assignmentId) {
  return request({ url: endpoint(textId), method: 'get', params: params(version, assignmentId), skipErrorHandler: true })
}

export function generateArticleAudio(textId, version, assignmentId) {
  return request({
    url: endpoint(textId), method: 'post', params: params(version, assignmentId), timeout: 200000,
    skipErrorHandler: true
  })
}

export function downloadArticleAudio(textId, version, hash, assignmentId) {
  return request({
    url: `${endpoint(textId)}/file`, method: 'get',
    params: { ...params(version, assignmentId), hash },
    responseType: 'blob', timeout: 120000, skipErrorHandler: true
  })
}

const insightEndpoint = textId => `${endpoint(textId)}/insights`

export function getListeningInsightAudioStatus(textId, version, assignmentId) {
  return request({
    url: insightEndpoint(textId), method: 'get', params: params(version, assignmentId),
    skipErrorHandler: true
  })
}

export function generateListeningInsightAudio(textId, version, assignmentId) {
  return request({
    url: insightEndpoint(textId), method: 'post', params: params(version, assignmentId),
    timeout: 200000, skipErrorHandler: true
  })
}

export function downloadListeningInsightAudio(textId, version, hash, assignmentId) {
  return request({
    url: `${insightEndpoint(textId)}/file`, method: 'get',
    params: { ...params(version, assignmentId), hash },
    responseType: 'blob', timeout: 120000, skipErrorHandler: true
  })
}
