// SSO 登录混入（用于在 Login.vue 中添加 SSO 功能）
import { ref, onMounted } from 'vue'
import { getSsoStatus, getSsoLoginUrl, getSsoLogoutUrl } from '@/api/sso'

export function useSsoLogin() {
  const ssoEnabled = ref(false)
  const ssoLoading = ref(false)

  onMounted(async () => {
    try {
      const response = await getSsoStatus()
      if (response.code === 200) {
        ssoEnabled.value = response.data.enabled
        // 缓存到 sessionStorage，供 request.js 等 non-Vue 上下文读取
        sessionStorage.setItem('sso_enabled', ssoEnabled.value ? 'true' : 'false')
      }
    } catch (error) {
      console.log('SSO 未启用或检查失败')
      ssoEnabled.value = false
    }
  })

  const handleSsoLogin = async () => {
    ssoLoading.value = true
    try {
      const response = await getSsoLoginUrl()
      if (response.code === 200) {
        // 跳转到 SSO 登录页面
        window.location.href = response.data.loginUrl
      } else {
        alert(response.message || '获取登录链接失败')
      }
    } catch (error) {
      console.error('SSO 登录失败:', error)
      alert('获取登录链接失败')
    } finally {
      ssoLoading.value = false
    }
  }

  /**
   * SSO 登出：获取 CAS logout URL 并跳转
   * 统一身份认证要求：退出后必须跳转到 CAS 登出页，
   * 否则用户在 CAS 侧仍处于登录状态，再次 SSO 登录会自动跳回。
   *
   * @param {string} fallbackUrl - SSO 未启用时的回退 URL（如 /login）
   */
  const ssoLogout = async (fallbackUrl = '/login') => {
    try {
      if (!ssoEnabled.value) {
        window.location.href = fallbackUrl
        return
      }
      const response = await getSsoLogoutUrl()
      if (response.code === 200 && response.data.logoutUrl) {
        window.location.href = response.data.logoutUrl
      } else {
        window.location.href = fallbackUrl
      }
    } catch (error) {
      console.error('获取 SSO 登出 URL 失败:', error)
      window.location.href = fallbackUrl
    }
  }

  return {
    ssoEnabled,
    ssoLoading,
    handleSsoLogin,
    ssoLogout
  }
}
