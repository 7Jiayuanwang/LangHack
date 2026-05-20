import request from '@/utils/request'

export function generateAiQuiz(textId, count = 5, regenerate = false) {
  return request({
    url: '/api/student/ai-quiz/generate',
    method: 'post',
    data: { textId, count, regenerate },
    timeout: 180000  // AI 出题需要较长时间，超时设为 3 分钟
  })
}
