<template>
  <div class="trend-login">
    <!-- 状态栏占位 -->
    <div class="status-bar"></div>

    <div class="trend-container">
      <!-- 时尚背景 -->
      <div class="trend-background">
        <div class="bg-gradient"></div>
        <div class="bg-pattern">
          <div class="bg-shape shape-1"></div>
          <div class="bg-shape shape-2"></div>
          <div class="bg-shape shape-3"></div>
          <div class="bg-shape shape-4"></div>
        </div>
      </div>

      <!-- 时尚内容 -->
      
      <div class="trend-content">
        <!-- 时尚品牌 -->
        <div class="trend-brand">
          <div class="trend-logo">
            <div class="logo-inner">
              <div class="logo-icon">📚</div>
              <div class="logo-text">EngLish</div>
            </div>
          </div>
            <p class="trend-subtitle">Trend · Learn · Share · Grow</p>
        </div>

        <!-- 时尚登录卡片 -->
        <div class="trend-card">
          <!-- 时尚标签切换 -->
          <div class="trend-tabs">
            <div class="tab-indicator" :style="tabIndicatorStyle"></div>
            <button
              class="trend-tab"
              :class="{ active: activeTab === 'login' }"
              @click="switchTab('login')"
            >
              登录
            </button>
            <button
              class="trend-tab"
              :class="{ active: activeTab === 'register' }"
              @click="switchTab('register')"
            >
              注册
            </button>
          </div>

          <!-- 登录表单 -->
          <div class="trend-form-container" v-show="activeTab === 'login'">
            <form @submit.prevent="handleLogin" class="trend-form">
              <div class="trend-field">
                <div class="trend-input-group">
                  <input
                    v-model="loginForm.username"
                    type="text"
                    placeholder="用户名"
                    class="trend-input"
                    required
                  />
                  <div class="input-icon">
                    <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                      <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/>
                      <circle cx="12" cy="7" r="4"/>
                    </svg>
                  </div>
                </div>
              </div>

              <div class="trend-field">
                <div class="trend-input-group">
                  <input
                    v-model="loginForm.password"
                    :type="showPassword ? 'text' : 'password'"
                    placeholder="密码"
                    autocomplete="current-password"
                    class="trend-input"
                    required
                  />
                  <div class="input-icon">
                    <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                      <rect x="3" y="11" width="18" height="11" rx="2" ry="2"/>
                      <path d="M7 11V7a5 5 0 0 1 10 0v4"/>
                    </svg>
                  </div>
                  <button type="button" class="password-toggle" @click="showPassword = !showPassword">
                    <svg v-if="showPassword" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                      <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/>
                      <circle cx="12" cy="12" r="3"/>
                    </svg>
                    <svg v-else width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                      <path d="M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19m-6.72-1.07a3 3 0 1 1-4.24-4.24"/>
                      <line x1="1" y1="1" x2="23" y2="23"/>
                    </svg>
                  </button>
                </div>
              </div>

              <button type="submit" class="trend-button" :disabled="loginLoading">
                <span v-if="!loginLoading">立即登录</span>
                <div v-else class="loading-spinner">
                  <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="white" stroke-width="2">
                    <path d="M21 12a9 9 0 1 1-6.219-8.56"/>
                  </svg>
                </div>
              </button>
            </form>

            <!-- 时尚演示账户 -->
            <div class="trend-demo">
              <div class="demo-header">
                <h3 class="demo-title">🎯 快速体验</h3>
              </div>
              <div class="demo-buttons">
                <div class="demo-btn" @click="fillDemo('admin')">
                  <div class="btn-avatar admin">👑</div>
                  <div class="btn-text">管理员</div>
                </div>
                <div class="demo-btn" @click="fillDemo('teacher')">
                  <div class="btn-avatar teacher">👨‍🏫</div>
                  <div class="btn-text">教师</div>
                </div>
                <div class="demo-btn" @click="fillDemo('student')">
                  <div class="btn-avatar student">🎓</div>
                  <div class="btn-text">学生</div>
                </div>
              </div>
            </div>
          </div>

          <!-- 注册表单 -->
          <div class="trend-form-container" v-show="activeTab === 'register'">
            <form @submit.prevent="handleRegister" class="trend-form">
              <div class="trend-field">
                <div class="trend-input-group">
                  <input
                    v-model="registerForm.username"
                    type="text"
                    placeholder="用户名 (3-20字符)"
                    class="trend-input"
                    required
                    minlength="3"
                    maxlength="20"
                  />
                  <div class="input-icon">
                    <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                      <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/>
                      <circle cx="12" cy="7" r="4"/>
                    </svg>
                  </div>
                </div>
              </div>

              <div class="trend-field">
                <div class="trend-input-group">
                  <input
                    v-model="registerForm.password"
                    :type="showRegPassword ? 'text' : 'password'"
                    placeholder="密码 (至少6字符)"
                    class="trend-input"
                    required
                    minlength="6"
                  />
                  <div class="input-icon">
                    <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                      <rect x="3" y="11" width="18" height="11" rx="2" ry="2"/>
                      <path d="M7 11V7a5 5 0 0 1 10 0v4"/>
                    </svg>
                  </div>
                  <button type="button" class="password-toggle" @click="showRegPassword = !showRegPassword">
                    <svg v-if="showRegPassword" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                      <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/>
                      <circle cx="12" cy="12" r="3"/>
                    </svg>
                    <svg v-else width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                      <path d="M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19m-6.72-1.07a3 3 0 1 1-4.24-4.24"/>
                      <line x1="1" y1="1" x2="23" y2="23"/>
                    </svg>
                  </button>
                </div>
              </div>

              <div class="trend-field">
                <div class="trend-input-group">
                  <input
                    v-model="registerForm.confirmPassword"
                    :type="showRegConfirmPassword ? 'text' : 'password'"
                    placeholder="确认密码"
                    class="trend-input"
                    required
                  />
                  <div class="input-icon">
                    <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                      <rect x="3" y="11" width="18" height="11" rx="2" ry="2"/>
                      <path d="M7 11V7a5 5 0 0 1 10 0v4"/>
                    </svg>
                  </div>
                  <button type="button" class="password-toggle" @click="showRegConfirmPassword = !showRegConfirmPassword">
                    <svg v-if="showRegConfirmPassword" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                      <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/>
                      <circle cx="12" cy="12" r="3"/>
                    </svg>
                    <svg v-else width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                      <path d="M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19m-6.72-1.07a3 3 0 1 1-4.24-4.24"/>
                      <line x1="1" y1="1" x2="23" y2="23"/>
                    </svg>
                  </button>
                </div>
              </div>

              <div class="trend-field">
                <div class="trend-input-group">
                  <input
                    v-model="registerForm.realName"
                    type="text"
                    placeholder="真实姓名"
                    class="trend-input"
                    required
                    minlength="2"
                    maxlength="50"
                  />
                  <div class="input-icon">
                    <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                      <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/>
                      <circle cx="12" cy="7" r="4"/>
                    </svg>
                  </div>
                </div>
              </div>

              <div class="trend-field">
                <div class="trend-input-group">
                  <input
                    v-model="registerForm.email"
                    type="email"
                    placeholder="邮箱 (可选)"
                    class="trend-input"
                  />
                  <div class="input-icon">
                    <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                      <path d="M4 4h16c1.1 0 2 .9 2 2v12c0 1.1-.9 2-2 2H4c-1.1 0-2-.9-2-2V6c0-1.1.9-2 2-2z"/>
                      <polyline points="22,6 12,13 2,6"/>
                    </svg>
                  </div>
                </div>
              </div>

              <button type="submit" class="trend-button" :disabled="registerLoading">
                <span v-if="!registerLoading">立即注册</span>
                <div v-else class="loading-spinner">
                  <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="white" stroke-width="2">
                    <path d="M21 12a9 9 0 1 1-6.219-8.56"/>
                  </svg>
                </div>
              </button>

              <!-- 注册福利 -->
              <div class="trend-benefits">
                <div class="benefit-header">
                  <span class="benefit-icon">✨</span>
                  <span>注册即享特权</span>
                </div>
                <div class="benefit-list">
                  <div class="benefit-item">
                    <span class="benefit-emoji">🎯</span>
                    <span>学习所有系统课程</span>
                  </div>
                  <div class="benefit-item">
                    <span class="benefit-emoji">📚</span>
                    <span>上传分享学习资料</span>
                  </div>
                  <div class="benefit-item">
                    <span class="benefit-emoji">👥</span>
                    <span>与学习者社区互动</span>
                  </div>
                  <div class="benefit-item">
                    <span class="benefit-emoji">📊</span>
                    <span>个性化学习追踪</span>
                  </div>
                </div>
              </div>
            </form>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/store/user'
import request from '@/utils/request'

const router = useRouter()
const userStore = useUserStore()

const loginLoading = ref(false)
const registerLoading = ref(false)
const activeTab = ref('login')
const showPassword = ref(false)
const showRegPassword = ref(false)
const showRegConfirmPassword = ref(false)

const loginForm = reactive({
  username: '',
  password: ''
})

const registerForm = reactive({
  username: '',
  password: '',
  confirmPassword: '',
  email: '',
  realName: ''
})

const tabIndicatorStyle = computed(() => {
  return {
    transform: activeTab.value === 'login' ? 'translateX(0)' : 'translateX(100%)'
  }
})

const switchTab = (tab) => {
  activeTab.value = tab
}

const handleLogin = async () => {
  if (!loginForm.username || !loginForm.password) {
    ElMessage.warning('请填写用户名和密码')
    return
  }

  loginLoading.value = true
  try {
    await userStore.login(loginForm)
    ElMessage.success('登录成功')

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
      case 'USER':
        router.push('/user')
        break
      default:
        router.push('/')
    }
  } catch (error) {
    let errorMsg = '登录失败，请重试'
    if (typeof error === 'string') {
      errorMsg = error
    } else if (error?.message) {
      errorMsg = error.message
    } else if (error?.data?.message) {
      errorMsg = error.data.message
    }
    ElMessage.error(errorMsg)
  } finally {
    loginLoading.value = false
  }
}

const handleRegister = async () => {
  if (!registerForm.username || !registerForm.password || !registerForm.confirmPassword || !registerForm.realName) {
    ElMessage.warning('请填写必填字段')
    return
  }

  if (registerForm.username.length < 3 || registerForm.username.length > 20) {
    ElMessage.warning('用户名长度应为3-20个字符')
    return
  }

  if (registerForm.password.length < 6) {
    ElMessage.warning('密码长度不能少于6个字符')
    return
  }

  if (registerForm.password !== registerForm.confirmPassword) {
    ElMessage.warning('两次输入的密码不一致')
    return
  }

  registerLoading.value = true
  try {
    const response = await request({
      url: '/api/auth/register',
      method: 'post',
      data: registerForm
    })

    if (response.code === 200) {
      ElMessage.success('注册成功！请登录')
      activeTab.value = 'login'
      loginForm.username = registerForm.username
      loginForm.password = registerForm.password
      Object.assign(registerForm, {
        username: '',
        password: '',
        confirmPassword: '',
        email: '',
        realName: ''
      })
    } else {
      ElMessage.error(response.message || '注册失败')
    }
  } catch (error) {
    let errorMsg = '注册失败，请重试'
    if (typeof error === 'string') {
      errorMsg = error
    } else if (error?.message) {
      errorMsg = error.message
    } else if (error?.data?.message) {
      errorMsg = error.data.message
    }
    ElMessage.error(errorMsg)
  } finally {
    registerLoading.value = false
  }
}

const fillDemo = (role) => {
  switch (role) {
    case 'admin':
      loginForm.username = 'admin'
      loginForm.password = 'admin123'
      break
    case 'teacher':
      loginForm.username = 'teacher'
      loginForm.password = 'admin123'
      break
    case 'student':
      loginForm.username = 'student'
      loginForm.password = 'admin123'
      break
  }
  const roleNames = { admin: '管理员', teacher: '教师', student: '学生' }
  ElMessage.info(`已填充${roleNames[role]}演示账号`)
}
</script>

<style scoped>
/* 时尚登录页面 */
.trend-login {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', 'PingFang SC', 'Microsoft YaHei', sans-serif;
  position: relative;
  overflow: hidden;
}

/* 状态栏 */
.status-bar {
  height: env(safe-area-inset-top, 0);
  background: transparent;
}

/* 时尚背景 */
.trend-background {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: -1;
}

.bg-gradient {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: var(--gradient-hero);
  opacity: 0.95;
}

.bg-pattern {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  overflow: hidden;
}

.bg-shape {
  position: absolute;
  border-radius: 50%;
  background: linear-gradient(45deg, var(--primary-color), var(--secondary-color));
  opacity: 0.1;
  animation: float 15s ease-in-out infinite;
}

.shape-1 {
  width: 200px;
  height: 200px;
  top: -50px;
  right: -50px;
  animation-delay: 0s;
}

.shape-2 {
  width: 150px;
  height: 150px;
  bottom: 100px;
  left: -50px;
  animation-delay: 5s;
}

.shape-3 {
  width: 100px;
  height: 100px;
  top: 50%;
  left: 10%;
  animation-delay: 10s;
}

.shape-4 {
  width: 80px;
  height: 80px;
  top: 20%;
  right: 15%;
  animation-delay: 7s;
}

@keyframes float {
  0%, 100% { transform: translateY(0px) rotate(0deg); }
  25% { transform: translateY(-20px) rotate(90deg); }
  50% { transform: translateY(0px) rotate(180deg); }
  75% { transform: translateY(20px) rotate(270deg); }
}

/* 时尚容器 */
.trend-container {
  flex: 1;
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  padding: 24px;
  max-width: 420px;
  width: 100%;
  margin: 0 auto;
}

/* 时尚品牌 */
.trend-brand {
  text-align: center;
  margin-bottom: 32px;
  animation: fadeInUp 0.8s ease;
}

@keyframes fadeInUp {
  from {
    opacity: 0;
    transform: translateY(30px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.trend-logo {
  display: inline-block;
  margin-bottom: 20px;
}

.logo-inner {
  background: var(--glass-bg);
  backdrop-filter: blur(20px);
  border: 1px solid var(--glass-border);
  border-radius: 24px;
  padding: 12px 20px;
  display: flex;
  align-items: center;
  gap: 8px;
  box-shadow: var(--shadow-lg);
}

.logo-icon {
  font-size: 24px;
  animation: bounce 2s ease-in-out infinite;
}

@keyframes bounce {
  0%, 20%, 50%, 80%, 100% { transform: translateY(0); }
  40% { transform: translateY(-5px); }
  60% { transform: translateY(-3px); }
}

.logo-text {
  font-size: 18px;
  font-weight: 700;
  color: var(--text-primary);
  letter-spacing: -0.5px;
  background: var(--gradient-brand);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

.trend-title {
  font-size: 28px;
  font-weight: 700;
  color: white;
  margin: 0 0 8px 0;
  letter-spacing: -1px;
  text-shadow: 0 2px 8px rgba(0, 0, 0, 0.3);
}

.trend-subtitle {
  font-size: 14px;
  color: rgba(255, 255, 255, 0.8);
  margin: 0;
  letter-spacing: 2px;
  font-weight: 500;
}

/* 时尚卡片 */
.trend-card {
  background: var(--bg-modal);
  backdrop-filter: blur(20px);
  border: 1px solid var(--glass-border);
  border-radius: 24px;
  box-shadow: var(--shadow-xl);
  overflow: hidden;
  width: 100%;
  animation: fadeInUp 0.8s ease 0.2s;
}

/* 时尚标签切换 */
.trend-tabs {
  position: relative;
  display: flex;
  background: var(--bg-tertiary);
  margin: 24px 24px 0 24px;
  border-radius: 16px;
  padding: 4px;
}

.tab-indicator {
  position: absolute;
  top: 4px;
  left: 4px;
  width: calc(50% - 4px);
  height: calc(100% - 8px);
  background: var(--bg-primary);
  border-radius: 12px;
  box-shadow: var(--shadow-sm);
  transition: transform 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}

.trend-tab {
  flex: 1;
  padding: 12px;
  background: none;
  border: none;
  border-radius: 12px;
  font-size: 15px;
  font-weight: 600;
  color: var(--text-tertiary);
  cursor: pointer;
  transition: all 0.3s ease;
  z-index: 1;
  position: relative;
}

.trend-tab.active {
  color: var(--primary-color);
}

/* 时尚表单容器 */
.trend-form-container {
  padding: 24px 24px 32px 24px;
}

.trend-form {
  margin-bottom: 20px;
}

/* 时尚字段 */
.trend-field {
  margin-bottom: 16px;
}

.trend-input-group {
  position: relative;
  display: flex;
  align-items: center;
}

.trend-input {
  width: 100%;
  padding: 14px 16px 14px 48px;
  background: var(--bg-primary);
  border: 1px solid var(--border-light);
  border-radius: 12px;
  font-size: 15px;
  color: var(--text-primary);
  transition: all 0.3s ease;
  outline: none;
  font-weight: 500;
}

.trend-input:focus {
  border-color: var(--primary-color);
  box-shadow: 0 0 0 3px rgba(0, 102, 204, 0.1);
  background: var(--bg-primary);
}

.trend-input::placeholder {
  color: var(--text-placeholder);
  font-weight: 400;
}

.input-icon {
  position: absolute;
  left: 16px;
  color: var(--text-tertiary);
  z-index: 1;
  transition: color 0.3s ease;
}

.trend-input:focus + .input-icon {
  color: var(--primary-color);
}

.password-toggle {
  position: absolute;
  right: 12px;
  background: none;
  border: none;
  color: var(--text-tertiary);
  cursor: pointer;
  padding: 4px;
  border-radius: 6px;
  transition: all 0.3s ease;
  display: flex;
  align-items: center;
  justify-content: center;
}

.password-toggle:hover {
  color: var(--text-secondary);
  background: var(--bg-tertiary);
}

/* 时尚按钮 */
.trend-button {
  width: 100%;
  padding: 16px;
  background: var(--gradient-brand);
  color: white;
  border: none;
  border-radius: 12px;
  font-size: 16px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  letter-spacing: 0.5px;
  margin-top: 8px;
  box-shadow: var(--shadow-brand);
}

.trend-button:hover:not(:disabled) {
  transform: translateY(-2px);
  box-shadow: 0 8px 24px rgba(0, 102, 204, 0.25);
}

.trend-button:active:not(:disabled) {
  transform: translateY(-1px);
}

.trend-button:disabled {
  background: var(--border-color);
  color: var(--text-tertiary);
  cursor: not-allowed;
  box-shadow: none;
}

.loading-spinner svg {
  animation: spin 1s linear infinite;
}

@keyframes spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

/* 时尚演示区域 */
.trend-demo {
  margin-top: 24px;
  padding-top: 24px;
  border-top: 1px solid var(--separator-color);
}

.demo-header {
  margin-bottom: 16px;
}

.demo-title {
  font-size: 16px;
  font-weight: 700;
  color: var(--text-primary);
  margin: 0 0 8px 0;
  text-align: center;
}

.scroll-hint {
  text-align: center;
  font-size: 12px;
  color: var(--text-quaternary);
  animation: pulse 2s ease-in-out infinite;
}

@keyframes pulse {
  0%, 100% { opacity: 0.5; }
  50% { opacity: 1; }
}

.demo-buttons {
  display: flex;
  gap: 20px;
  justify-content: center;
  align-items: center;
  width: 100%;
  padding: 20px 0;
}

.demo-btn {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  transition: all 0.3s ease;
  padding: 12px;
  border-radius: 20px;
  background: var(--bg-primary);
  border: 1px solid var(--border-light);
  box-shadow: var(--shadow-sm);
  min-width: 80px;
}

.demo-btn:hover {
  transform: translateY(-2px);
  box-shadow: var(--shadow-md);
  border-color: var(--primary-color);
  background: var(--bg-tertiary);
}

.btn-avatar {
  width: 50px;
  height: 50px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 24px;
  color: white;
  font-weight: 600;
  position: relative;
  background: linear-gradient(135deg, var(--primary-color), var(--secondary-color));
}

.btn-avatar::before {
  content: '';
  position: absolute;
  top: -2px;
  left: -2px;
  right: -2px;
  bottom: -2px;
  border-radius: 50%;
  background: linear-gradient(45deg, var(--primary-color), var(--secondary-color));
  z-index: -1;
}

.btn-avatar.admin {
  background: var(--error-color);
}

.btn-avatar.admin::before {
  background: var(--error-color);
}

.btn-avatar.teacher {
  background: var(--warning-color);
}

.btn-avatar.teacher::before {
  background: var(--warning-color);
}

.btn-avatar.student {
  background: var(--success-color);
}

.btn-avatar.student::before {
  background: var(--success-color);
}

.btn-text {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-primary);
  text-align: center;
}

.demo-card:hover {
  border-color: var(--primary-color);
  transform: translateY(-4px);
  box-shadow: var(--shadow-lg);
  background: var(--bg-tertiary);
}

.demo-avatar {
  width: 48px;
  height: 48px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 20px;
  flex-shrink: 0;
  color: white;
  font-weight: 600;
  position: relative;
}

.demo-avatar::before {
  content: '';
  position: absolute;
  top: -2px;
  left: -2px;
  right: -2px;
  bottom: -2px;
  border-radius: 50%;
  background: linear-gradient(45deg, var(--primary-color), var(--secondary-color));
  z-index: -1;
}

.demo-avatar.admin::before {
  background: var(--error-color);
}

.demo-avatar.teacher::before {
  background: var(--warning-color);
}

.demo-avatar.student::before {
  background: var(--success-color);
}

.demo-info {
  flex: 1;
}

.demo-info h4 {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-primary);
  margin: 0 0 2px 0;
}

.demo-info p {
  font-size: 12px;
  color: var(--text-tertiary);
  margin: 0 0 4px 0;
}

.demo-credentials {
  font-size: 11px;
  color: var(--text-quaternary);
  font-family: 'Courier New', monospace;
  background: var(--bg-tertiary);
  padding: 2px 6px;
  border-radius: 4px;
  display: inline-block;
}

/* 时尚福利 */
.trend-benefits {
  margin-top: 20px;
  padding: 16px;
  background: var(--bg-tertiary);
  border-radius: 12px;
  border: 1px solid var(--border-light);
}

.benefit-header {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  font-weight: 600;
  color: var(--text-primary);
  margin-bottom: 12px;
}

.benefit-icon {
  font-size: 16px;
  animation: sparkle 2s ease-in-out infinite;
}

@keyframes sparkle {
  0%, 100% { transform: scale(1); }
  50% { transform: scale(1.2); }
}

.benefit-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.benefit-item {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: var(--text-secondary);
  background: var(--bg-primary);
  padding: 6px 10px;
  border-radius: 8px;
  border: 1px solid var(--border-light);
}

.benefit-emoji {
  font-size: 14px;
}

/* 响应式设计 */
@media (max-width: 480px) {
  .trend-container {
    padding: 20px 16px;
  }

  .trend-card {
    border-radius: 20px;
  }

  .trend-form-container {
    padding: 20px 16px 24px 16px;
  }

  .trend-input {
    padding: 14px 16px 14px 44px;
    font-size: 16px;
  }

  .trend-title {
    font-size: 24px;
  }

  .trend-subtitle {
    font-size: 13px;
  }

  /* 手机端调整为垂直堆叠确保所有卡片可见 */
  .demo-cards {
    flex-direction: column;
    gap: 10px;
  }

  .demo-card {
    padding: 12px;
    min-width: auto;
    max-width: none;
  }

  .demo-avatar {
    width: 36px;
    height: 36px;
    font-size: 16px;
  }

  .demo-info h4 {
    font-size: 14px;
  }

  .demo-info p {
    font-size: 12px;
  }

  .demo-credentials {
    font-size: 11px;
  }

  .benefit-list {
    flex-direction: column;
    gap: 6px;
  }

  .benefit-item {
    justify-content: flex-start;
  }
}

@media (max-width: 360px) {
  .trend-container {
    padding: 16px 12px;
  }

  .trend-form-container {
    padding: 18px 14px 22px 14px;
  }

  .trend-input {
    padding: 12px 14px 12px 42px;
  }

  .demo-card {
    min-width: 220px;
    padding: 12px;
  }

  .demo-avatar {
    width: 40px;
    height: 40px;
    font-size: 16px;
  }

  .demo-info h4 {
    font-size: 13px;
  }

  .demo-info p {
    font-size: 11px;
  }

  .demo-credentials {
    font-size: 10px;
  }
}

/* 横屏适配 */
@media (max-height: 700px) and (orientation: landscape) {
  .trend-container {
    justify-content: flex-start;
    padding-top: 60px;
  }

  .trend-brand {
    margin-bottom: 20px;
  }

  .trend-logo {
    margin-bottom: 12px;
  }

  .logo-inner {
    padding: 8px 16px;
  }

  .logo-icon {
    font-size: 20px;
  }

  .logo-text {
    font-size: 16px;
  }

  .trend-title {
    font-size: 22px;
  }

  .trend-subtitle {
    font-size: 12px;
  }

  .trend-card {
    max-width: 500px;
  }
}

/* 触摸优化 */
.trend-tab,
.password-toggle,
.trend-button,
.demo-card {
  -webkit-tap-highlight-color: transparent;
  touch-action: manipulation;
}
</style>