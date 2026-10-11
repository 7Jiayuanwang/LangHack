import request from '@/utils/request'

// 文件大小限制 (字节)
const MAX_IMAGE_SIZE = 10 * 1024 * 1024  // 10 MB
const MAX_VIDEO_SIZE = 512 * 1024 * 1024  // 512 MB

// 允许的文件类型（兼容浏览器对 .mp3 返回 audio/mpeg / audio/mp3 / 空字符串三种情况）
const ALLOWED_IMAGE_TYPES = ['image/jpeg', 'image/png', 'image/gif', 'image/webp']
const ALLOWED_VIDEO_TYPES = ['video/mp4', 'video/webm', 'video/ogg']
const ALLOWED_AUDIO_TYPES = ['audio/mpeg', 'audio/mp3', 'audio/mpga', 'audio/wav', 'audio/wave', 'audio/x-wav', 'audio/ogg', 'audio/webm', 'audio/mp4', 'audio/x-m4a', 'audio/aac']
// 扩展名白名单，作为 file.type 缺失或非标准时的兜底判断
const ALLOWED_AUDIO_EXTS = ['mp3', 'wav', 'ogg', 'webm', 'mp4', 'm4a', 'aac']
const ALLOWED_DOCUMENT_TYPES = ['application/pdf']

/**
 * 验证文件是否满足上传要求
 * @param {File} file - 文件对象
 * @param {string} mediaType - 媒体类型 ('IMAGE' 或 'VIDEO')
 * @returns {string|null} 验证错误信息，通过验证返回 null
 */
function validateFile(file, mediaType) {
  // 检查文件是否存在
  if (!file || !(file instanceof File)) {
    return '文件对象无效'
  }

  // 检查文件大小
  const maxSize = mediaType === 'IMAGE' || mediaType === 'DOCUMENT' ? MAX_IMAGE_SIZE : MAX_VIDEO_SIZE
  if (file.size === 0) {
    return '文件不能为空'
  }
  if (file.size > maxSize) {
    const limitMB = maxSize / (1024 * 1024)
    return `文件大小不能超过 ${limitMB}MB (当前: ${(file.size / (1024 * 1024)).toFixed(2)}MB)`
  }

  // 检查文件类型
  const allowedTypes = mediaType === 'IMAGE' ? ALLOWED_IMAGE_TYPES : mediaType === 'AUDIO' ? ALLOWED_AUDIO_TYPES : mediaType === 'DOCUMENT' ? ALLOWED_DOCUMENT_TYPES : ALLOWED_VIDEO_TYPES
  // file.type 在浏览器/系统/扩展名差异下可能为空或非标准值；若 audio 类型先按扩展名兜底
  if (mediaType === 'AUDIO' && (!file.type || !allowedTypes.includes(file.type))) {
    const ext = (file.name.split('.').pop() || '').toLowerCase()
    if (!ALLOWED_AUDIO_EXTS.includes(ext)) {
      const typeList = allowedTypes.join(', ')
      return `不支持的文件类型，仅支持: ${typeList}`
    }
  } else if (!allowedTypes.includes(file.type)) {
    const typeList = allowedTypes.join(', ')
    return `不支持的文件类型，仅支持: ${typeList}`
  }

  return null
}

/**
 * 上传媒体文件
 * @param {File} file - 文件对象
 * @param {string} mediaType - 媒体类型 ('IMAGE' 或 'VIDEO')
 * @returns {Promise} 上传结果
 */
export function uploadMedia(file, mediaType) {
  // 参数类型检查
  if (!mediaType || typeof mediaType !== 'string') {
    return Promise.reject(new Error('媒体类型不能为空'))
  }

  // 验证 mediaType 值
  if (!['IMAGE', 'VIDEO', 'AUDIO', 'DOCUMENT'].includes(mediaType)) {
    return Promise.reject(new Error('媒体类型只能是 IMAGE、VIDEO、AUDIO 或 DOCUMENT'))
  }

  // 前端文件验证
  const validationError = validateFile(file, mediaType)
  if (validationError) {
    return Promise.reject(new Error(validationError))
  }

  const formData = new FormData()
  formData.append('file', file)
  formData.append('mediaType', mediaType)

  return request({
    url: '/api/media/upload',
    method: 'post',
    data: formData,
    timeout: 600000,  // 文件上传超时时间延长到 600 秒 (10 分钟)
    headers: {
      // 显式移除 Content-Type 头，让浏览器自动设置 multipart/form-data 及 boundary
      'Content-Type': undefined
    }
  })
}

/**
 * 获取媒体详情
 * @param {number|string} mediaId - 媒体 ID
 * @returns {Promise} 媒体详情
 */
export function getMediaDetail(mediaId) {
  // 验证 mediaId 是否有效
  if (!mediaId || (typeof mediaId !== 'number' && typeof mediaId !== 'string')) {
    return Promise.reject(new Error('媒体 ID 无效'))
  }

  const id = typeof mediaId === 'string' ? mediaId.trim() : mediaId
  if (!id || (typeof id === 'string' && !/^\d+$/.test(id))) {
    return Promise.reject(new Error('媒体 ID 必须是有效的正整数'))
  }

  return request({
    url: `/api/media/${id}`,
    method: 'get'
  })
}

/**
 * 下载媒体文件
 * @param {number|string} mediaId - 媒体 ID
 * @returns {Promise} 文件内容
 */
export function downloadMedia(mediaId) {
  // 验证 mediaId 是否有效
  if (!mediaId || (typeof mediaId !== 'number' && typeof mediaId !== 'string')) {
    return Promise.reject(new Error('媒体 ID 无效'))
  }

  const id = typeof mediaId === 'string' ? mediaId.trim() : mediaId
  if (!id || (typeof id === 'string' && !/^\d+$/.test(id))) {
    return Promise.reject(new Error('媒体 ID 必须是有效的正整数'))
  }

  return request({
    url: `/api/media/${id}/download`,
    method: 'get',
    responseType: 'blob'
  })
}

/**
 * 删除媒体
 * @param {number|string} mediaId - 媒体 ID
 * @returns {Promise} 删除结果
 */
export function deleteMedia(mediaId) {
  // 验证 mediaId 是否有效
  if (!mediaId || (typeof mediaId !== 'number' && typeof mediaId !== 'string')) {
    return Promise.reject(new Error('媒体 ID 无效'))
  }

  const id = typeof mediaId === 'string' ? mediaId.trim() : mediaId
  if (!id || (typeof id === 'string' && !/^\d+$/.test(id))) {
    return Promise.reject(new Error('媒体 ID 必须是有效的正整数'))
  }

  return request({
    url: `/api/media/${id}`,
    method: 'delete'
  })
}

/**
 * 重试视频转码（管理员）
 * @param {number|string} mediaId
 */
export function retryTranscode(mediaId) {
  if (!mediaId || (typeof mediaId !== 'number' && typeof mediaId !== 'string')) {
    return Promise.reject(new Error('媒体 ID 无效'))
  }
  const id = typeof mediaId === 'string' ? mediaId.trim() : mediaId
  if (!id || (typeof id === 'string' && !/^\d+$/.test(id))) {
    return Promise.reject(new Error('媒体 ID 必须是有效的正整数'))
  }
  return request({
    url: `/api/media/${id}/retry-transcode`,
    method: 'post'
  })
}
