import request from '@/utils/request'

// ========== 管理端视频 API ==========

export function getAdminVideos(params) {
  return request({
    url: '/api/admin/videos',
    method: 'get',
    params
  })
}

export function getAdminVideoStats() {
  return request({
    url: '/api/admin/videos/stats',
    method: 'get'
  })
}

export function getAdminVideoDetail(id) {
  return request({
    url: `/api/admin/videos/${id}`,
    method: 'get'
  })
}

export function createVideo(data) {
  return request({
    url: '/api/admin/videos',
    method: 'post',
    data
  })
}

export function updateVideo(id, data) {
  return request({
    url: `/api/admin/videos/${id}`,
    method: 'put',
    data
  })
}

export function deleteAdminVideo(id) {
  return request({
    url: `/api/admin/videos/${id}`,
    method: 'delete'
  })
}

export function toggleVideoFeatured(id, featured) {
  return request({
    url: `/api/admin/videos/${id}/featured`,
    method: 'put',
    params: { featured }
  })
}

export function setVideoFeaturedPriority(id, priority) {
  return request({
    url: `/api/admin/videos/${id}/featured-priority`,
    method: 'put',
    params: { priority }
  })
}

// ========== 用户端视频 API ==========

export function uploadUserVideo(data) {
  return request({
    url: '/api/user/videos/upload',
    method: 'post',
    data
  })
}

export function getMyVideoUploads() {
  return request({
    url: '/api/user/videos/my-uploads',
    method: 'get'
  })
}

export function getUserVideoDetail(id) {
  return request({
    url: `/api/user/videos/${id}`,
    method: 'get'
  })
}

export function updateUserVideo(id, data) {
  return request({
    url: `/api/user/videos/${id}`,
    method: 'put',
    data
  })
}

export function deleteUserVideo(id) {
  return request({
    url: `/api/user/videos/${id}`,
    method: 'delete'
  })
}

export function likeVideo(id) {
  return request({
    url: `/api/user/videos/${id}/like`,
    method: 'post'
  })
}

// ========== 公开视频 API ==========

export function getFeaturedVideos(params) {
  return request({
    url: '/api/public/videos/featured',
    method: 'get',
    params
  })
}

export function getRandomVideos(params) {
  return request({
    url: '/api/public/videos/random',
    method: 'get',
    params
  })
}

export function getPublicVideoDetail(id) {
  return request({
    url: `/api/public/videos/${id}`,
    method: 'get'
  })
}

export function getPublicVideoStats() {
  return request({
    url: '/api/public/videos/stats',
    method: 'get'
  })
}
