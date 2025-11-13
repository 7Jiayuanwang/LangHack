import request from '@/utils/request'

export function getUsers(params) {
  return request({
    url: '/api/admin/users',
    method: 'get',
    params
  })
}

export function createUser(data) {
  return request({
    url: '/api/admin/users',
    method: 'post',
    data
  })
}

export function updateUser(id, data) {
  return request({
    url: `/api/admin/users/${id}`,
    method: 'put',
    data
  })
}

export function deleteUser(id) {
  return request({
    url: `/api/admin/users/${id}`,
    method: 'delete'
  })
}

export function getUserStats() {
  return request({
    url: '/api/admin/stats',
    method: 'get'
  })
}

export function getCourses(params) {
  return request({
    url: '/api/courses',
    method: 'get',
    params
  })
}

export function getCourseList(params) {
  return request({
    url: '/api/courses',
    method: 'get',
    params
  })
}

export function createCourse(data) {
  return request({
    url: '/api/courses',
    method: 'post',
    data
  })
}

export function updateCourse(id, data) {
  return request({
    url: `/api/courses/${id}`,
    method: 'put',
    data
  })
}

export function deleteCourse(id) {
  return request({
    url: `/api/courses/${id}`,
    method: 'delete'
  })
}

export function getCourseStats() {
  return request({
    url: '/api/courses/stats',
    method: 'get'
  })
}
