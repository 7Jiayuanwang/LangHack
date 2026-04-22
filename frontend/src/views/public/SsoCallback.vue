<template>
  <div class="sso-callback">
    <div v-if="loading" class="loading">
      <div class="spinner"></div>
      <p>正在处理登录...</p>
    </div>

    <!-- 任何错误统一以温馨提示展示，始终包含主管部门联系方式 -->
    <PermissionDenied
      v-else-if="errorInfo"
      :main-message="errorInfo.mainMessage"
      :sub-message="errorInfo.subMessage"
      :contact-phone="errorInfo.contactPhone"
      :contact-department="errorInfo.contactDepartment"
      :theme="errorInfo.theme"
    />
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/store/user'
import { exchangeSsoToken } from '@/api/sso'
import { setToken } from '@/utils/auth'
import PermissionDenied from '@/components/PermissionDenied.vue'
import { mapSsoError } from '@/utils/ssoErrorMapper'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const loading = ref(true)
const errorInfo = ref(null)

function showError({ errorCode, rawMessage }) {
  errorInfo.value = mapSsoError({ errorCode, rawMessage })
  // 详细错误只写入浏览器控制台供开发者排错，不在 UI 展示
  if (rawMessage) {
    console.error('[SSO] 登录失败:', { errorCode: errorInfo.value.errorCode, rawMessage })
  }
  loading.value = false
}

onMounted(async () => {
  try {
    // 1. 后端重定向携带的错误信息（handleCallback 异常路径）
    if (route.query.error || route.query.errorCode) {
      const rawMessage = route.query.error
        ? decodeURIComponent(String(route.query.error))
        : ''
      showError({
        errorCode: route.query.errorCode ? String(route.query.errorCode) : '',
        rawMessage
      })
      return
    }

    // 2. 必须携带一次性 code
    const code = route.query.code
    if (!code) {
      showError({ errorCode: 'MISSING_PARAM', rawMessage: '缺少授权码' })
      return
    }

    // 3. 用一次性 code 换取 JWT token
    let response
    try {
      response = await exchangeSsoToken(code)
    } catch (err) {
      const extractedCode = err?.response?.data?.data?.errorCode || err?.code
      const extractedMsg = err?.response?.data?.message || err?.message
      showError({
        errorCode: extractedCode || 'SYSTEM_ERROR',
        rawMessage: extractedMsg || '网络异常，无法完成登录'
      })
      return
    }

    if (!response || response.code !== 200) {
      // 后端返回业务错误，优先使用结构化 errorCode
      showError({
        errorCode: response?.data?.errorCode || '',
        rawMessage: response?.message || '登录失败'
      })
      return
    }

    if (!response.data || !response.data.token) {
      showError({
        errorCode: 'SYSTEM_ERROR',
        rawMessage: '登录响应缺少凭证数据'
      })
      return
    }

    const { token, user: serverUser } = response.data

    // 优先使用后端返回的用户信息（完整），否则从 JWT 解析（兜底）
    let jwtRole = ''
    let jwtUserInfo = {}
    if (serverUser && serverUser.role) {
      jwtRole = serverUser.role
      jwtUserInfo = serverUser
    } else {
      try {
        const payload = JSON.parse(atob(token.split('.')[1]))
        jwtRole = payload.role || ''
        jwtUserInfo = {
          username: payload.sub,
          role: payload.role
        }
      } catch (e) {
        console.error('解析 token 失败:', e)
      }
    }

    const validRoles = ['ADMIN', 'TEACHER', 'STUDENT', 'USER', 'OPS']
    if (!jwtRole || !validRoles.includes(jwtRole)) {
      showError({
        errorCode: 'SYSTEM_ERROR',
        rawMessage: '登录响应角色异常: ' + jwtRole
      })
      return
    }

    // 保存认证信息到 Pinia + localStorage（与 store/user.js login() 保持一致）
    userStore.token = token
    userStore.userRole = jwtRole
    userStore.userInfo = jwtUserInfo
    setToken(token)
    localStorage.setItem('userInfo', JSON.stringify(jwtUserInfo))
    localStorage.setItem('userRole', jwtRole)

    // 根据角色跳转
    switch (jwtRole) {
      case 'ADMIN':
        router.push('/admin')
        break
      case 'OPS':
        router.push('/ops')
        break
      case 'TEACHER':
        router.push('/teacher')
        break
      case 'STUDENT':
        router.push('/student')
        break
      default:
        router.push('/student') // USER 等其他角色在校园环境中进入学生端
    }
  } catch (e) {
    showError({
      errorCode: 'SYSTEM_ERROR',
      rawMessage: e?.message || '登录失败，请重试'
    })
  }
})
</script>

<style scoped>
.sso-callback {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 100vh;
  background: #f5f5f5;
}

.loading {
  text-align: center;
}

.spinner {
  width: 44px;
  height: 44px;
  border: 3px solid rgba(255, 255, 255, 0.25);
  border-top: 3px solid white;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
  margin: 0 auto 16px;
}

p {
  color: rgba(255, 255, 255, 0.85);
  font-size: 15px;
  letter-spacing: 0.5px;
}

@keyframes spin {
  0% { transform: rotate(0deg); }
  100% { transform: rotate(360deg); }
}
</style>
