<template>
  <div class="sso-callback">
    <div v-if="loading" class="loading loading-container">
      <div class="loader-content">
        <div class="loader-visual">
          <div class="spinner orbit"></div>
          <div class="core">
            <svg width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/>
            </svg>
          </div>
        </div>
        <h2 class="loader-title">正在安全校验</h2>
        <p class="loader-subtitle">请稍候，我们正在验证您的身份凭证</p>
        <div class="loader-status">
          <span class="status-dot"></span>
          正在与统一身份认证平台通信...
        </div>
      </div>
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
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #0f172a;
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', 'PingFang SC', 'Microsoft YaHei', sans-serif;
}

.loading-container {
  width: 100%;
  max-width: 400px;
  padding: 40px;
  text-align: center;
  animation: fadeIn 0.8s ease-out;
}

@keyframes fadeIn {
  from { opacity: 0; transform: translateY(10px); }
  to { opacity: 1; transform: translateY(0); }
}

.loader-visual {
  position: relative;
  width: 120px;
  height: 120px;
  margin: 0 auto 32px;
}

.orbit {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  border: 2px solid rgba(99, 102, 241, 0.1);
  border-top-color: #6366f1;
  border-radius: 50%;
  animation: spin 1.5s cubic-bezier(0.5, 0, 0.5, 1) infinite;
}

.core {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  width: 64px;
  height: 64px;
  background: rgba(99, 102, 241, 0.1);
  border-radius: 20px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #6366f1;
  box-shadow: 0 0 30px rgba(99, 102, 241, 0.2);
}

.loader-title {
  color: white;
  font-size: 24px;
  font-weight: 700;
  margin: 0 0 12px;
  letter-spacing: 1px;
}

.loader-subtitle {
  color: rgba(255, 255, 255, 0.5);
  font-size: 15px;
  margin: 0 0 40px;
  line-height: 1.6;
}

.loader-status {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  background: rgba(255, 255, 255, 0.05);
  padding: 10px 20px;
  border-radius: 100px;
  color: rgba(255, 255, 255, 0.7);
  font-size: 14px;
}

.status-dot {
  width: 6px;
  height: 6px;
  background: #6366f1;
  border-radius: 50%;
  animation: pulse 1.5s infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

@keyframes pulse {
  0%, 100% { transform: scale(1); opacity: 1; }
  50% { transform: scale(1.5); opacity: 0.5; }
}
</style>
