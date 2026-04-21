import request from '@/utils/request'

/**
 * 获取个性化推荐文章（需登录，USER/STUDENT 角色）
 *
 * @param {number} count       期望返回数量
 * @param {Array<number>} excludeIds  需要排除的文章 ID（如本次会话已展示过的）
 */
export function getRecommendedTexts(count = 12, excludeIds = []) {
  const params = { count }
  if (Array.isArray(excludeIds) && excludeIds.length > 0) {
    params.exclude = excludeIds.join(',')
  }
  return request({
    url: '/api/public/texts/recommended',
    method: 'get',
    params
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
