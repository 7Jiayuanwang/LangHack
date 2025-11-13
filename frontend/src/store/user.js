import { defineStore } from 'pinia'
import { login, logout } from '@/api/auth'

export const useUserStore = defineStore('user', {
  state: () => ({
    token: localStorage.getItem('token') || '',
    userInfo: JSON.parse(localStorage.getItem('userInfo') || '{}'),
    userRole: localStorage.getItem('userRole') || ''
  }),

  getters: {
    isLoggedIn: (state) => !!state.token,
    isAdmin: (state) => state.userRole === 'ADMIN',
    isTeacher: (state) => state.userRole === 'TEACHER',
    isStudent: (state) => state.userRole === 'STUDENT'
  },

  actions: {
    async login(credentials) {
      try {
        const response = await login(credentials)
        const { token, user } = response.data

        this.token = token
        this.userInfo = user
        this.userRole = user.role

        localStorage.setItem('token', token)
        localStorage.setItem('userInfo', JSON.stringify(user))
        localStorage.setItem('userRole', user.role)

        return Promise.resolve(response)
      } catch (error) {
        return Promise.reject(error)
      }
    },

    async logout() {
      try {
        await logout()
      } catch (error) {
        console.error('Logout error:', error)
      } finally {
        this.token = ''
        this.userInfo = {}
        this.userRole = ''

        localStorage.removeItem('token')
        localStorage.removeItem('userInfo')
        localStorage.removeItem('userRole')
      }
    },

    updateUserInfo(userInfo) {
      this.userInfo = userInfo
      localStorage.setItem('userInfo', JSON.stringify(userInfo))
    }
  }
})