import request from '@/utils/request'

// 获取当前教师授课班级
export function getMyTeachingClasses(params) {
  return request({
    url: '/api/teacher/my-courses',
    method: 'get',
    params
  })
}
