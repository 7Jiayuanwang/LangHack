import request from '@/utils/request'

/**
 * 查询单词的完整联想信息（同义词 + 衍生词 + 常用搭配 + 词根词缀）
 */
export function getWordAssociations(word) {
  return request({
    url: '/api/student/word-associations',
    method: 'get',
    params: { word }
  })
}

/**
 * 单词搜索建议（自动补全）
 */
export function searchWordSuggestions(keyword) {
  return request({
    url: '/api/public/words/search',
    method: 'get',
    params: { word: keyword }
  })
}

/**
 * 仅查询同义词
 */
export function getSynonyms(word) {
  return request({
    url: '/api/student/word-associations/synonyms',
    method: 'get',
    params: { word }
  })
}

/**
 * 仅查询衍生词
 */
export function getDerivatives(word) {
  return request({
    url: '/api/student/word-associations/derivatives',
    method: 'get',
    params: { word }
  })
}

/**
 * 仅查询常用搭配
 */
export function getCollocations(word) {
  return request({
    url: '/api/student/word-associations/collocations',
    method: 'get',
    params: { word }
  })
}
