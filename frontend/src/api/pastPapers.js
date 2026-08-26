import request from '@/utils/request'

export const getSharedPastPapers = () => request({ url: '/api/past-papers', method: 'get' })
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
