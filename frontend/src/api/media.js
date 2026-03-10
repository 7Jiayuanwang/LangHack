import request from '@/utils/request'

export function uploadMedia(file, mediaType) {
  // 输入验证
  if (!file) {
    return Promise.reject(new Error('文件不能为空'))
  }
  if (!mediaType) {
    return Promise.reject(new Error('媒体类型不能为空'))
  }

  const formData = new FormData()
  formData.append('file', file)
  formData.append('mediaType', mediaType)

  return request({
    url: '/api/media/upload',
    method: 'post',
    data: formData,
    timeout: 60000  // 文件上传超时时间延长到 60 秒
  })
}

export function getMediaDetail(mediaId) {
  return request({
    url: `/api/media/${mediaId}`,
    method: 'get'
  })
}

export function deleteMedia(mediaId) {
  return request({
    url: `/api/media/${mediaId}`,
    method: 'delete'
  })
}
