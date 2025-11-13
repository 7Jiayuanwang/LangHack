import request from '@/utils/request'

// 获取单词列表
export function getWordList(params) {
  return request({
    url: '/api/words',
    method: 'get',
    params
  })
}

// 根据单词查询
export function getWordByWord(word) {
  return request({
    url: `/api/words/word/${word}`,
    method: 'get'
  })
}

// 获取单词详情
export function getWordById(id) {
  return request({
    url: `/api/words/${id}`,
    method: 'get'
  })
}

// 创建单词
export function createWord(data) {
  return request({
    url: '/api/words',
    method: 'post',
    data
  })
}

// 更新单词
export function updateWord(id, data) {
  return request({
    url: `/api/words/${id}`,
    method: 'put',
    data
  })
}

// 删除单词
export function deleteWord(id) {
  return request({
    url: `/api/words/${id}`,
    method: 'delete'
  })
}

// 获取随机单词
export function getRandomWords(count) {
  return request({
    url: '/api/words/random',
    method: 'get',
    params: { count }
  })
}
