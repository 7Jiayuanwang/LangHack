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
import { ssoCallback } from '@/api/sso'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const loading = ref(true)
const error = ref('')

onMounted(async () => {
  try {
    // 获取回调参数（CAS 使用 ticket，OAuth 2.0 使用 code）
    const params = {}
    if (route.query.ticket) {
      params.ticket = route.query.ticket
    } else if (route.query.code) {
      params.code = route.query.code
      params.state = route.query.state
    } else {
      throw new Error('缺少认证参数')
    }

    // 调用后端处理回调
    const response = await ssoCallback(params)

    if (response.code !== 200) {
      throw new Error(response.message || '登录失败')
    }

    const { token, user } = response.data

    // 保存认证信息
    userStore.token = token
    userStore.userInfo = user
    userStore.userRole = user.role

    // 根据角色跳转
    switch (user.role) {
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
