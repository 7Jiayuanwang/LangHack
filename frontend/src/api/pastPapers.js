import request from '@/utils/request'

export const getSharedPastPapers = () => request({ url: '/api/past-papers', method: 'get' })
export const submitPastPaperAttempt = (attempt) => request({
  url: '/api/past-papers/attempts',
  method: 'post',
  data: attempt,
  timeout: 600000
})
export const createSharedPastPaper = (paper) => request({ url: '/api/past-papers', method: 'post', data: paper })
export const deleteSharedPastPaper = (id) => request({ url: `/api/past-papers/${id}`, method: 'delete' })
export const uploadPastPaperAudio = (id, file) => {
  const data = new FormData()
  data.append('file', file)
  return request({
    url: `/api/past-papers/${id}/audio`,
    method: 'post',
    data,
    timeout: 600000,
    headers: { 'Content-Type': undefined }
  })
}

/** 作文 AI 判分的提示词（走小芽 agent 的问答通道，userQuestion 限 500 字以内） */
export const WRITING_SCORE_INSTRUCTION = [
  '你是大学英语四六级/考研英语作文阅卷老师。请依据题目要求给学生作文评分并给出针对性反馈。',
  '只输出 JSON，不要输出任何多余文字。格式：',
  '{"total":总分(满分15),"dimensions":{"content":{"score":0-5,"comment":"内容相关与充实度"},',
  '"structure":{"score":0-5,"comment":"结构连贯与衔接"},"language":{"score":0-5,"comment":"语言准确与多样"}},',
  '"wordCount":词数,"strengths":["具体优点1","具体优点2"],',
  '"improvements":["具体问题1","具体问题2"],"revisedSample":"挑一句最需要改的句子给出修改后示例"}',
  '评分标准：内容与题目要求的相关性、篇章结构、语言准确性各占 5 分。评价必须基于学生实际写出的内容，不得编造。'
].join('\n')

/**
 * 请求 AI 作文判分。
 * selectedText 传学生作文（后端限 2000 字），articleContext 传题目要求。
 */
export const requestWritingScore = (essay, promptText) =>
  request({
    url: '/api/student/ai-qa/chat',
    method: 'post',
    data: {
      scene: 'LEARNING_AGENT',
      userQuestion: WRITING_SCORE_INSTRUCTION,
      selectedText: essay,
      articleContext: promptText ? `作文题目要求：\n${promptText}` : undefined
    },
    timeout: 120000
  })
