import request from '@/utils/request'

/** Personalized or exploratory article feed; returns metadata, never full bodies. */
export function getArticleFeed(params = {}) {
  return request({ url: '/api/feed/articles', method: 'get', params })
}

export function recordArticleFeedEvent(data) {
  return request({ url: '/api/feed/events', method: 'post', data })
}

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

/**
 * 搜索所有已审核公开文章（不限精选状态）
 * @param {string} keyword  搜索关键词
 * @param {number} page     页码
 * @param {number} size     每页数量
 * @param {string} difficulty 难度过滤（可选）
 */
export function searchArticles(keyword, page = 1, size = 20, difficulty = '') {
  const params = { page, size }
  if (keyword) params.keyword = keyword
  if (difficulty) params.difficulty = difficulty
  return request({
    url: '/api/public/texts/search',
    method: 'get',
    params
  })
}
