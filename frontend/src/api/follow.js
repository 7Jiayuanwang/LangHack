import request from '@/utils/request'

/**
 * Follow a user
 */
export function followUser(userId) {
  return request({
    url: `/api/user/follow/${userId}`,
    method: 'post'
  })
}

/**
 * Unfollow a user
 */
export function unfollowUser(userId) {
  return request({
    url: `/api/user/follow/${userId}`,
    method: 'delete'
  })
}

/**
 * Check follow status
 */
export function getFollowStatus(userId) {
  return request({
    url: `/api/user/follow/status/${userId}`,
    method: 'get'
  })
}

/**
 * Get follow counts for a user
 */
export function getFollowCounts(userId) {
  return request({
    url: `/api/user/follow/counts/${userId}`,
    method: 'get'
  })
}

/**
 * Get following list (users that the given user follows)
 */
export function getFollowingList(userId, page = 1, size = 20) {
  return request({
    url: `/api/user/follow/following/${userId}`,
    method: 'get',
    params: { page, size }
  })
}

/**
 * Get followers list (users that follow the given user)
 */
export function getFollowersList(userId, page = 1, size = 20) {
  return request({
    url: `/api/user/follow/followers/${userId}`,
    method: 'get',
    params: { page, size }
  })
}
