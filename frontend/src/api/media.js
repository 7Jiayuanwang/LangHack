import request from '@/utils/request'

export function uploadMedia(file, mediaType) {
  const formData = new FormData()
  formData.append('file', file)
  formData.append('mediaType', mediaType)

  return request({
    url: '/api/media/upload',
    method: 'post',
    data: formData
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
