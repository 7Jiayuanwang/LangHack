import request from '@/utils/request'

export const toggleSentenceFavorite = (textId, sentence) =>
  request({ url: '/api/student/sentence-favorites/toggle', method: 'post', data: { textId, sentence } })

export const getSentenceFavorites = (params) =>
  request({ url: '/api/student/sentence-favorites', method: 'get', params })

export const deleteSentenceFavorite = (id) =>
  request({ url: `/api/student/sentence-favorites/${id}`, method: 'delete' })
