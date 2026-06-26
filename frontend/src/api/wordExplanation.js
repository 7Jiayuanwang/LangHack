import request from '@/utils/request'

/**
 * 获取单词 AI 详细介绍
 * @param {string} word - 单词
 * @param {boolean} regenerate - 是否强制重新生成
 */
export function getWordExplanation(word, regenerate = false) {
  return request({
    url: '/api/student/word-explanation/get',
    method: 'post',
    data: {
      word,
      regenerate
    }
  })
}
