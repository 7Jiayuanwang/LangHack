<template>
  <div class="permission-denied" :class="`theme-${theme}`">
    <!-- 顶部渐变区域 -->
    <div class="header-area">
      <div class="decor-circle c1"></div>
      <div class="decor-circle c2"></div>
      <div class="decor-circle c3"></div>
      <div class="permission-icon" :style="{ background: iconBgColor }">
        <svg v-if="theme === 'info'" width="44" height="44" viewBox="0 0 24 24" fill="none">
          <circle cx="12" cy="12" r="10" stroke="white" stroke-width="2"/>
          <path d="M12 16v-5m0-3h.01" stroke="white" stroke-width="2" stroke-linecap="round"/>
        </svg>
        <svg v-else-if="theme === 'error'" width="44" height="44" viewBox="0 0 24 24" fill="none">
          <circle cx="12" cy="12" r="10" stroke="white" stroke-width="2"/>
          <path d="M15 9l-6 6m0-6l6 6" stroke="white" stroke-width="2" stroke-linecap="round"/>
        </svg>
        <svg v-else width="44" height="44" viewBox="0 0 24 24" fill="none">
          <circle cx="12" cy="12" r="10" stroke="white" stroke-width="2"/>
          <path d="M12 7v5m0 3h.01" stroke="white" stroke-width="2" stroke-linecap="round"/>
        </svg>
      </div>
      <h2 class="permission-title">温馨提示</h2>
    </div>

    <!-- 主体白色区域 -->
    <div class="body-area">
      <div class="permission-message">
        <p class="main-message">{{ mainMessage }}</p>
        <p v-if="subMessage" class="sub-message">{{ subMessage }}</p>
      </div>

      <!-- 联系方式 -->
      <div class="contact-card">
        <div class="contact-label">如有疑问请联系</div>
        <div class="contact-phone">
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none">
            <path d="M22 16.92v3a2 2 0 0 1-2.18 2 19.79 19.79 0 0 1-8.63-3.07 19.5 19.5 0 0 1-6-6 19.79 19.79 0 0 1-3.07-8.67A2 2 0 0 1 4.11 2h3a2 2 0 0 1 2 1.72 12.84 12.84 0 0 0 .7 2.81 2 2 0 0 1-.45 2.11L8.09 9.91a16 16 0 0 0 6 6l1.27-1.27a2 2 0 0 1 2.11-.45 12.84 12.84 0 0 0 2.81-.7A2 2 0 0 1 22 16.92z" :stroke="themeColor" stroke-width="2" stroke-linecap="round"/>
          </svg>
          {{ contactPhone }}
        </div>
        <div class="contact-department">{{ contactDepartment }}</div>
      </div>

      <!-- 操作按钮 -->
      <div class="permission-actions">
        <button @click="goBack" class="btn-back">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none">
            <path d="M19 12H5m7-7l-7 7 7 7" stroke="currentColor" stroke-width="2" stroke-linecap="round"/>
          </svg>
          返回登录
        </button>
        <button @click="retryLogin" class="btn-retry">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none">
            <path d="M1 4v6h6M23 20v-6h-6" stroke="currentColor" stroke-width="2" stroke-linecap="round"/>
            <path d="M20.49 9A9 9 0 0 0 5.64 5.64L1 10m22 4l-4.64 4.36A9 9 0 0 1 3.51 15" stroke="currentColor" stroke-width="2" stroke-linecap="round"/>
          </svg>
          重新登录
        </button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRouter } from 'vue-router'

const router = useRouter()
const props = defineProps({
  mainMessage: {
    type: String,
    default: '对不起，您无权使用LangHack英语学习智能体平台'
  },
  subMessage: {
    type: String,
    default: '您的账号正在审批中或权限不足'
  },
  contactPhone: {
    type: String,
    default: '43019235'
  },
  contactDepartment: {
    type: String,
    default: '计算机与信息工程学院'
  },
  theme: {
    type: String,
    default: 'warning',
    validator: (value) => ['warning', 'info', 'error'].includes(value)
  }
})

const THEME_COLORS = {
  warning: '#ff8c3a',
  info: '#3498db',
  error: '#e74c3c'
}

const themeColor = computed(() => THEME_COLORS[props.theme] || THEME_COLORS.warning)
const iconBgColor = computed(() => {
  const c = themeColor.value
  return c + '18'
})

const emit = defineEmits(['close'])

const goBack = () => {
  emit('close')
  router.push('/login')
}

const retryLogin = () => {
  localStorage.removeItem('token')
  localStorage.removeItem('user')
  router.push('/login')
}
</script>

<style scoped>
.permission-denied {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', 'PingFang SC', 'Microsoft YaHei', sans-serif;
}

/* ===== 顶部渐变区域 ===== */
.header-area {
  position: relative;
  background: linear-gradient(160deg, #667eea 0%, #764ba2 50%, #6a5acd 100%);
  padding: 48px 24px 36px;
  text-align: center;
  overflow: hidden;
  flex-shrink: 0;
}

.decor-circle {
  position: absolute;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.06);
}
.c1 { width: 120px; height: 120px; top: -30px; right: -20px; }
.c2 { width: 80px; height: 80px; bottom: 10px; left: -15px; }
.c3 { width: 50px; height: 50px; top: 20px; left: 40%; }

.permission-icon {
  position: relative;
  z-index: 1;
  width: 80px;
  height: 80px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  margin: 0 auto 16px;
  animation: popIn 0.4s ease;
}

@keyframes popIn {
  from { transform: scale(0.6); opacity: 0; }
  to { transform: scale(1); opacity: 1; }
}

.permission-title {
  position: relative;
  z-index: 1;
  font-size: 22px;
  font-weight: 600;
  color: white;
  letter-spacing: 1px;
}

/* ===== 主体白色区域 ===== */
.body-area {
  flex: 1;
  background: white;
  border-radius: 24px 24px 0 0;
  margin-top: -20px;
  padding: 32px 24px 24px;
  display: flex;
  flex-direction: column;
  animation: slideUp 0.4s ease;
}

@keyframes slideUp {
  from { transform: translateY(30px); opacity: 0; }
  to { transform: translateY(0); opacity: 1; }
}

.permission-message {
  text-align: center;
  margin-bottom: 24px;
}

.main-message {
  font-size: 16px;
  font-weight: 500;
  color: #555;
  line-height: 1.6;
  margin-bottom: 6px;
}

.theme-error .main-message { color: #c0392b; }
.theme-info .main-message { color: #2980b9; }
.theme-warning .main-message { color: #d35400; }

.sub-message {
  font-size: 13px;
  color: #999;
  line-height: 1.5;
}

/* ===== 联系方式 ===== */
.contact-card {
  background: #f8f9fb;
  border-radius: 14px;
  padding: 20px;
  text-align: center;
  margin-bottom: 28px;
}

.contact-label {
  font-size: 12px;
  color: #bbb;
  margin-bottom: 10px;
}

.contact-phone {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  font-size: 24px;
  font-weight: 700;
  color: #2c3e50;
  margin-bottom: 6px;
  letter-spacing: 1.5px;
}

.contact-department {
  font-size: 13px;
  color: #aaa;
}

/* ===== 操作按钮 ===== */
.permission-actions {
  display: flex;
  gap: 10px;
  margin-top: auto;
}

.btn-back,
.btn-retry {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 14px 0;
  border: none;
  border-radius: 12px;
  font-size: 15px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s ease;
}

.btn-back {
  background: #f0f2f5;
  color: #666;
}

.btn-back:active {
  background: #e4e6e9;
}

.btn-retry {
  color: white;
  background: linear-gradient(135deg, #667eea, #764ba2);
  box-shadow: 0 4px 14px rgba(102, 126, 234, 0.3);
}

.btn-retry:active {
  box-shadow: 0 2px 6px rgba(102, 126, 234, 0.2);
  transform: scale(0.97);
}

/* ===== 响应式 ===== */
@media (min-width: 481px) {
  .permission-denied {
    max-width: 420px;
    margin: 0 auto;
    border-radius: 20px;
    overflow: hidden;
    min-height: auto;
    height: 100vh;
  }
}
</style>
