// SSO 登录混入（用于在 Login.vue 中添加 SSO 功能）
import { ref, onMounted } from 'vue'
import { getSsoStatus, getSsoLoginUrl } from '@/api/sso'

export function useSsoLogin() {
  const ssoEnabled = ref(false)
  const ssoLoading = ref(false)

  onMounted(async () => {
    try {
      const response = await getSsoStatus()
      if (response.code === 200) {
        ssoEnabled.value = response.data.enabled
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

  return {
    ssoEnabled,
    ssoLoading,
    handleSsoLogin
  }
}
