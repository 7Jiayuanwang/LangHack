import request from '@/utils/request'

/**
 * 获取个性化推荐文章（需登录，USER/STUDENT 角色）
 */
export function getRecommendedTexts(count = 12) {
  return request({
    url: '/api/public/texts/recommended',
    method: 'get',
    params: { count }
  })
}

/**
 * 获取精选文章（公开）
 */
export function getFeaturedTexts(count = 10) {
  return request({
    url: '/api/public/texts/featured',
    method: 'get',
    params: { count }
  })
}
