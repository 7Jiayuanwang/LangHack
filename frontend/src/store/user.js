import { defineStore } from 'pinia'
import { login, logout } from '@/api/auth'

export const useUserStore = defineStore('user', {
  state: () => ({
    token: (() => {
      try {
        return localStorage.getItem('token') || ''
      } catch (e) {
        console.error('Failed to load token from localStorage:', e)
        return ''
      }
    })(),
    userInfo: (() => {
      try {
        const stored = localStorage.getItem('userInfo')
        return stored ? JSON.parse(stored) : {}
      } catch (e) {
        console.error('Failed to parse userInfo from localStorage:', e)
        localStorage.removeItem('userInfo')
        return {}
      }
    })(),
    userRole: (() => {
      try {
        return localStorage.getItem('userRole') || ''
      } catch (e) {
        console.error('Failed to load userRole from localStorage:', e)
        return ''
      }
    })()
  }),

  getters: {
    isLoggedIn: (state) => !!state.token,
    isAdmin: (state) => state.userRole === 'ADMIN',
    isTeacher: (state) => state.userRole === 'TEACHER',
    isStudent: (state) => state.userRole === 'STUDENT',
    isUser: (state) => state.userRole === 'USER'
  },

  actions: {
    async login(credentials) {
      try {
        const response = await login(credentials)
        const { token, user } = response.data || {}

        if (!token || !user) {
          return Promise.reject(new Error('登录响应数据异常'))
        }

        this.token = token
        this.userInfo = user
        this.userRole = user.role

        // localStorage 操作异常处理
        try {
          localStorage.setItem('token', token)
          localStorage.setItem('userInfo', JSON.stringify(user))
          localStorage.setItem('userRole', user.role)
        } catch (storageError) {
          console.warn('Failed to save to localStorage:', storageError)
          // 继续，因为 Pinia store 中的状态已更新
        }

        return Promise.resolve(response.data)
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

        // localStorage 操作异常处理
        try {
          localStorage.removeItem('token')
          localStorage.removeItem('userInfo')
          localStorage.removeItem('userRole')
        } catch (storageError) {
          console.warn('Failed to remove from localStorage:', storageError)
        }
      }
    },

    updateUserInfo(userInfo) {
      this.userInfo = userInfo
      if (userInfo.role) {
        this.userRole = userInfo.role
        try {
          localStorage.setItem('userRole', userInfo.role)
        } catch (storageError) {
          console.warn('Failed to save userRole to localStorage:', storageError)
        }
      }
      try {
        localStorage.setItem('userInfo', JSON.stringify(userInfo))
      } catch (storageError) {
        console.warn('Failed to save userInfo to localStorage:', storageError)
      }
      return Promise.resolve(userInfo)
    }
  }
})