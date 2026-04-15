<template>
  <div class="sso-callback">
    <div v-if="loading" class="loading">
      <div class="spinner"></div>
      <p>正在处理登录...</p>
    </div>

    <div v-else-if="error" class="error">
      <h2>登录失败</h2>
      <p>{{ error }}</p>
      <button @click="goToLogin">返回登录</button>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/store/user'
import { exchangeSsoToken } from '@/api/sso'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const loading = ref(true)
const error = ref('')

onMounted(async () => {
  try {
    // 检查后端重定向携带的错误信息
    if (route.query.error) {
      throw new Error(decodeURIComponent(String(route.query.error)))
    }

    // 获取一次性 code（后端回调重定向到此处携带的参数）
    const code = route.query.code
    if (!code) {
      throw new Error('缺少授权码')
    }

    // 用一次性 code 换取 JWT token
    const response = await exchangeSsoToken(code)

    if (response.code !== 200) {
      throw new Error(response.message || '登录失败')
    }

    if (!response.data || !response.data.token) {
      throw new Error('登录返回数据异常')
    }

    const { token } = response.data

    // 保存认证信息
    userStore.token = token

    // 解析 JWT 中的用户信息（角色等）
    // JWT payload 格式: { sub: username, role: "STUDENT", ... }
    try {
      const payload = JSON.parse(atob(token.split('.')[1]))
      userStore.userRole = payload.role
      userStore.userInfo = {
        username: payload.sub,
        role: payload.role
      }
    } catch (e) {
      console.error('解析 token 失败:', e)
    }

    // 根据角色跳转
    const role = userStore.userRole
    switch (role) {
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
        router.push('/user')
    }

  } catch (e) {
    error.value = e.message || '登录失败，请重试'
    loading.value = false
  }
})

const goToLogin = () => {
  router.push('/login')
}
</script>

<style scoped>
.sso-callback {
  display: flex;
  justify-content: center;
  align-items: center;
  height: 100vh;
  background: #f5f5f5;
}

.loading {
  text-align: center;
}

.spinner {
  width: 40px;
  height: 40px;
  border: 4px solid #f3f3f3;
  border-top: 4px solid #3498db;
  border-radius: 50%;
  animation: spin 1s linear infinite;
  margin: 0 auto 20px;
}

@keyframes spin {
  0% { transform: rotate(0deg); }
  100% { transform: rotate(360deg); }
}

.error {
  text-align: center;
  padding: 40px;
  background: white;
  border-radius: 8px;
  box-shadow: 0 2px 10px rgba(0,0,0,0.1);
}

.error h2 {
  color: #e74c3c;
  margin-bottom: 10px;
}

.error button {
  margin-top: 20px;
  padding: 10px 20px;
  background: #3498db;
  color: white;
  border: none;
  border-radius: 4px;
  cursor: pointer;
}

.error button:hover {
  background: #2980b9;
}
</style>
