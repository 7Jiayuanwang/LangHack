<template>
  <div class="login-container">
    <el-card class="login-card">
      <template #header>
        <div class="login-header">
          <h2>英语学习平台</h2>
          <p>请登录您的账户</p>
        </div>
      </template>

      <el-form
        ref="loginFormRef"
        :model="loginForm"
        :rules="loginRules"
        class="login-form"
      >
        <el-form-item prop="username">
          <el-input
            v-model="loginForm.username"
            placeholder="用户名"
            size="large"
            prefix-icon="User"
          />
        </el-form-item>

        <el-form-item prop="password">
          <el-input
            v-model="loginForm.password"
            type="password"
            placeholder="密码"
            size="large"
            prefix-icon="Lock"
            show-password
            @keyup.enter="handleLogin"
          />
        </el-form-item>

        <el-form-item>
          <el-button
            type="primary"
            size="large"
            class="login-button"
            :loading="loading"
            @click="handleLogin"
          >
            登录
          </el-button>
        </el-form-item>
      </el-form>

      <div class="demo-accounts">
        <p>演示账户：</p>
        <el-button size="small" @click="fillDemo('admin')">管理员</el-button>
        <el-button size="small" @click="fillDemo('teacher')">教师</el-button>
        <el-button size="small" @click="fillDemo('student')">学生</el-button>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/store/user'

const router = useRouter()
const userStore = useUserStore()

const loginFormRef = ref()
const loading = ref(false)

const loginForm = reactive({
  username: '',
  password: ''
})

const loginRules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' }
  ]
}

const handleLogin = async () => {
  if (!loginFormRef.value) return

  await loginFormRef.value.validate(async (valid) => {
    if (valid) {
      loading.value = true
      try {
        await userStore.login(loginForm)
        ElMessage.success('登录成功')

        // 根据角色跳转到对应页面
        switch (userStore.userRole) {
          case 'ADMIN':
            router.push('/admin')
            break
          case 'TEACHER':
            router.push('/teacher')
            break
          case 'STUDENT':
            router.push('/student')
            break
          default:
            router.push('/')
        }
      } catch (error) {
        ElMessage.error(error.message || '登录失败')
      } finally {
        loading.value = false
      }
    }
  })
}

const fillDemo = (role) => {
  switch (role) {
    case 'admin':
      loginForm.username = 'admin'
      loginForm.password = 'admin123'
      break
    case 'teacher':
      loginForm.username = 'teacher'
      loginForm.password = 'teacher123'
      break
    case 'student':
      loginForm.username = 'student'
      loginForm.password = 'student123'
      break
  }
}
</script>

<style scoped>
.login-container {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
}

.login-card {
  width: 400px;
  box-shadow: 0 15px 35px rgba(0, 0, 0, 0.1);
}

.login-header {
  text-align: center;
}

.login-header h2 {
  margin: 0;
  color: #333;
  font-size: 24px;
}

.login-header p {
  margin: 10px 0 0;
  color: #666;
  font-size: 14px;
}

.login-form {
  padding: 20px 0;
}

.login-button {
  width: 100%;
}

.demo-accounts {
  text-align: center;
  padding-top: 20px;
  border-top: 1px solid #eee;
}

.demo-accounts p {
  margin: 0 0 10px;
  color: #666;
  font-size: 14px;
}
</style>