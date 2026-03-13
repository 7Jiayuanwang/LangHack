import { defineStore } from 'pinia'
import { login, logout } from '@/api/auth'

// 验证 userInfo 结构的有效性
function validateUserInfo(userInfo) {
  if (!userInfo || typeof userInfo !== 'object' || Array.isArray(userInfo)) {
    return null
  }
  // 确保必要字段存在
  if (!userInfo.id && !userInfo.username) {
    return null
  }
  return userInfo
}

// 从 localStorage 安全加载并验证 userInfo
function loadUserInfoFromStorage() {
  try {
    const stored = localStorage.getItem('userInfo')
    if (!stored) return {}
    
    const parsed = JSON.parse(stored)
    const validated = validateUserInfo(parsed)
    
    if (!validated) {
      // 破损数据：移除并返回空对象
      localStorage.removeItem('userInfo')
      return {}
    }
    
    return validated
  } catch (e) {
    console.error('Failed to parse userInfo from localStorage:', e)
    try {
      localStorage.removeItem('userInfo')
    } catch (storageError) {
      console.warn('Failed to clear corrupted userInfo:', storageError)
    }
    return {}
  }
}

// 验证角色值的有效性
function validateRole(role) {
  const validRoles = ['ADMIN', 'TEACHER', 'STUDENT', 'USER']
  if (!role || typeof role !== 'string') {
    return ''
  }
  return validRoles.includes(role) ? role : ''
}

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
    userInfo: loadUserInfoFromStorage(),
    userRole: (() => {
      try {
        const role = localStorage.getItem('userRole') || ''
        return validateRole(role)
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

        // 验证角色值有效性
        const validatedRole = validateRole(user.role)
        if (!validatedRole) {
          console.warn('Invalid role returned from server:', user.role)
          return Promise.reject(new Error('登录响应角色异常'))
        }

        // 先更新 Pinia state
        const prevState = {
          token: this.token,
          userInfo: { ...this.userInfo },
          userRole: this.userRole
        }

        this.token = token
        this.userInfo = user
        this.userRole = validatedRole

        // 尝试持久化到 localStorage，失败时回滚
        try {
          localStorage.setItem('token', token)
          localStorage.setItem('userInfo', JSON.stringify(user))
          localStorage.setItem('userRole', validatedRole)
        } catch (storageError) {
          console.error('Failed to persist login state:', storageError)
          // 回滚 Pinia state 以保持一致性
          this.token = prevState.token
          this.userInfo = prevState.userInfo
          this.userRole = prevState.userRole
          return Promise.reject(new Error('登录状态持久化失败，请检查存储空间'))
        }

        return Promise.resolve(response.data)
      } catch (error) {
        return Promise.reject(error)
      }
    },

    async logout() {
      let logoutApiError = null
      
      try {
        await logout()
      } catch (error) {
        console.error('Logout API error:', error)
        logoutApiError = error
      }

      // 清理本地状态（API 失败时仍继续清理）
      this.token = ''
      this.userInfo = {}
      this.userRole = ''

      // 清理 localStorage
      const storageErrors = []
      try {
        localStorage.removeItem('token')
      } catch (e) {
        console.warn('Failed to remove token from localStorage:', e)
        storageErrors.push(e)
      }

      try {
        localStorage.removeItem('userInfo')
      } catch (e) {
        console.warn('Failed to remove userInfo from localStorage:', e)
        storageErrors.push(e)
      }

      try {
        localStorage.removeItem('userRole')
      } catch (e) {
        console.warn('Failed to remove userRole from localStorage:', e)
        storageErrors.push(e)
      }

      // 返回 promise：优先返回 logout API 错误，但本地状态已清理
      if (logoutApiError) {
        return Promise.reject(logoutApiError)
      }
      
      if (storageErrors.length > 0) {
        return Promise.reject(new Error('登出时清理存储失败'))
      }

      return Promise.resolve()
    },

    updateUserInfo(userInfo) {
      if (!userInfo || typeof userInfo !== 'object' || Array.isArray(userInfo)) {
        console.error('Invalid userInfo provided to updateUserInfo:', userInfo)
        return Promise.reject(new Error('Invalid userInfo'))
      }

      // 保存前置状态用于回滚
      const prevUserInfo = { ...this.userInfo }
      const prevUserRole = this.userRole

      // 更新 Pinia state
      const mergedInfo = { ...this.userInfo, ...userInfo }
      this.userInfo = mergedInfo

      // 如果提供了新的 role，验证并更新
      if (userInfo.role) {
        const validatedRole = validateRole(userInfo.role)
        if (!validatedRole) {
          console.error('Invalid role in updateUserInfo:', userInfo.role)
          // 回滚
          this.userInfo = prevUserInfo
          return Promise.reject(new Error('Invalid role'))
        }
        this.userRole = validatedRole
      }

      // 尝试持久化，失败时回滚
      try {
        localStorage.setItem('userInfo', JSON.stringify(this.userInfo))
        if (userInfo.role) {
          localStorage.setItem('userRole', this.userRole)
        }
      } catch (storageError) {
        console.error('Failed to persist userInfo:', storageError)
        // 回滚 state
        this.userInfo = prevUserInfo
        this.userRole = prevUserRole
        return Promise.reject(new Error('用户信息保存失败，请检查存储空间'))
      }

      return Promise.resolve(this.userInfo)
    }
  }
})
