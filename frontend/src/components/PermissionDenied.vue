<template>
  <div class="permission-denied" :class="`theme-${theme}`">
    <div class="content-wrapper">
      <div class="header-area">
        <div class="decor-shapes">
          <div class="shape s1"></div>
          <div class="shape s2"></div>
          <div class="shape s3"></div>
        </div>
        <div class="permission-icon-wrapper">
          <div class="permission-icon" :style="{ background: iconBgColor }">
            <svg v-if="theme === 'info'" class="animated-icon" width="48" height="48" viewBox="0 0 24 24" fill="none">
              <circle cx="12" cy="12" r="10" stroke="white" stroke-width="2" class="path-draw"/>
              <path d="M12 16v-5m0-3h.01" stroke="white" stroke-width="2" stroke-linecap="round" class="path-draw"/>
            </svg>
            <svg v-else-if="theme === 'error'" class="animated-icon" width="48" height="48" viewBox="0 0 24 24" fill="none">
              <circle cx="12" cy="12" r="10" stroke="white" stroke-width="2" class="path-draw"/>
              <path d="M15 9l-6 6m0-6l6 6" stroke="white" stroke-width="2" stroke-linecap="round" class="path-draw"/>
            </svg>
            <svg v-else class="animated-icon" width="48" height="48" viewBox="0 0 24 24" fill="none">
              <circle cx="12" cy="12" r="10" stroke="white" stroke-width="2" class="path-draw"/>
              <path d="M12 7v5m0 3h.01" stroke="white" stroke-width="2" stroke-linecap="round" class="path-draw"/>
            </svg>
          </div>
          <div class="icon-pulse"></div>
        </div>
        <h2 class="permission-title">温馨提示：</h2>
        <div class="title-underline"></div>
      </div>

      <div class="body-area">
        <div class="permission-message">
          <h1 class="main-message">{{ mainMessage }}</h1>
          <p v-if="subMessage" class="sub-message">{{ subMessage }}</p>
        </div>

        <div class="contact-card">
          <div class="contact-header">
            <div class="contact-dot"></div>
            <span class="contact-label">如有疑问请致电：</span>
          </div>
          <div class="contact-main">
            <div class="phone-wrapper">
              <div class="phone-icon-bg">
                <svg width="20" height="20" viewBox="0 0 24 24" fill="none">
                  <path d="M22 16.92v3a2 2 0 0 1-2.18 2 19.79 19.79 0 0 1-8.63-3.07 19.5 19.5 0 0 1-6-6 19.79 19.79 0 0 1-3.07-8.67A2 2 0 0 1 4.11 2h3a2 2 0 0 1 2 1.72 12.84 12.84 0 0 0 .7 2.81 2 2 0 0 1-.45 2.11L8.09 9.91a16 16 0 0 0 6 6l1.27-1.27a2 2 0 0 1 2.11-.45 12.84 12.84 0 0 0 2.81-.7A2 2 0 0 1 22 16.92z" stroke="currentColor" stroke-width="2" stroke-linecap="round"/>
                </svg>
              </div>
              <span class="contact-phone-full">
                <span class="contact-department">{{ contactDepartment }}</span>
                <span class="contact-phone">{{ contactPhone }}</span>
              </span>
            </div>
          </div>
        </div>

        <div class="permission-actions">
          <button @click="goBack" class="btn-back">
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none">
              <path d="M19 12H5m7-7l-7 7 7 7" stroke="currentColor" stroke-width="2" stroke-linecap="round"/>
            </svg>
            返回登录
          </button>
          <button @click="retryLogin" class="btn-retry">
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none">
              <path d="M1 4v6h6M23 20v-6h-6" stroke="currentColor" stroke-width="2" stroke-linecap="round"/>
              <path d="M20.49 9A9 9 0 0 0 5.64 5.64L1 10m22 4l-4.64 4.36A9 9 0 0 1 3.51 15" stroke="currentColor" stroke-width="2" stroke-linecap="round"/>
            </svg>
            重新尝试
          </button>
        </div>
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
    default: '对不起，您无权使用LangHack英语学习智能体平台。'
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
  align-items: stretch;
  justify-content: stretch;
  background: #f8fafc;
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', 'PingFang SC', 'Microsoft YaHei', sans-serif;
  padding: 0;
  box-sizing: border-box;
}

.content-wrapper {
  width: 100%;
  min-height: 100vh;
  display: grid;
  grid-template-columns: minmax(340px, 40vw) 1fr;
  animation: slideIn 0.6s cubic-bezier(0.16, 1, 0.3, 1);
}

@keyframes slideIn {
  from { opacity: 0; transform: translateX(-20px); }
  to { opacity: 1; transform: translateX(0); }
}

.header-area {
  position: relative;
  background: linear-gradient(135deg, #4f46e5 0%, #7c3aed 100%);
  padding: 60px 40px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  text-align: center;
  overflow: hidden;
  box-shadow: 10px 0 30px rgba(0, 0, 0, 0.05);
  z-index: 10;
}

.theme-error .header-area { background: linear-gradient(135deg, #ef4444 0%, #b91c1c 100%); }
.theme-info .header-area { background: linear-gradient(135deg, #3b82f6 0%, var(--xy-brand-700) 100%); }
.theme-warning .header-area { background: linear-gradient(135deg, #f59e0b 0%, #d97706 100%); }

.decor-shapes {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  overflow: hidden;
  pointer-events: none;
}

.shape {
  position: absolute;
  background: rgba(255, 255, 255, 0.1);
  border-radius: 50%;
  filter: blur(40px);
}

.s1 { width: 300px; height: 300px; top: -100px; right: -50px; }
.s2 { width: 200px; height: 200px; bottom: -50px; left: -50px; }
.s3 { width: 150px; height: 150px; top: 20%; left: 10%; }

.permission-icon-wrapper {
  position: relative;
  margin-bottom: 24px;
}

.permission-icon {
  position: relative;
  z-index: 2;
  width: 100px;
  height: 100px;
  border-radius: 30px;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 10px 25px rgba(0, 0, 0, 0.2);
  transform: rotate(-5deg);
  animation: iconFloat 4s ease-in-out infinite;
}

@keyframes iconFloat {
  0%, 100% { transform: translateY(0) rotate(-5deg); }
  50% { transform: translateY(-10px) rotate(5deg); }
}

.icon-pulse {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  width: 100px;
  height: 100px;
  background: rgba(255, 255, 255, 0.2);
  border-radius: 30px;
  z-index: 1;
  animation: pulse 2s infinite;
}

@keyframes pulse {
  0% { transform: translate(-50%, -50%) scale(1); opacity: 0.6; }
  100% { transform: translate(-50%, -50%) scale(1.5); opacity: 0; }
}

.animated-icon .path-draw {
  stroke-dasharray: 60;
  stroke-dashoffset: 60;
  animation: draw 1.5s forwards ease-in-out;
}

@keyframes draw {
  to { stroke-dashoffset: 0; }
}

.permission-title {
  position: relative;
  z-index: 2;
  font-size: 32px;
  font-weight: 800;
  color: white;
  letter-spacing: 4px;
  margin: 0;
  text-shadow: 0 2px 10px rgba(0,0,0,0.1);
}

.title-underline {
  width: 40px;
  height: 4px;
  background: rgba(255, 255, 255, 0.4);
  margin: 12px auto 0;
  border-radius: 2px;
}

.body-area {
  background: white;
  padding: 60px clamp(40px, 6vw, 100px);
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: flex-start;
  position: relative;
}

.permission-message {
  margin-bottom: 40px;
  width: 100%;
}

.main-message {
  font-size: clamp(24px, 3.2vw, 44px);
  font-weight: 800;
  color: #1e293b;
  line-height: 1.25;
  margin: 0 0 20px;
  letter-spacing: -0.5px;
}

.sub-message {
  font-size: clamp(16px, 1.4vw, 20px);
  color: #64748b;
  line-height: 1.6;
  margin: 0;
  font-weight: 400;
}

.contact-card {
  background: #f1f5f9;
  border-radius: 24px;
  padding: 32px;
  width: 100%;
  max-width: 600px;
  margin-bottom: 48px;
  transition: all 0.3s ease;
  border: 1px solid #e2e8f0;
}

.contact-card:hover {
  transform: translateY(-4px);
  box-shadow: 0 12px 24px rgba(0, 0, 0, 0.04);
  background: #f8fafc;
}

.contact-header {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 16px;
}

.contact-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #4f46e5;
}

.theme-error .contact-dot { background: #ef4444; }
.theme-warning .contact-dot { background: #f59e0b; }

.contact-label {
  font-size: 14px;
  font-weight: 600;
  color: #94a3b8;
  text-transform: uppercase;
  letter-spacing: 1px;
}

.contact-main {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  gap: 20px;
}

.phone-wrapper {
  display: flex;
  align-items: center;
  gap: 12px;
}

.phone-icon-bg {
  width: 40px;
  height: 40px;
  background: white;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #4f46e5;
  box-shadow: 0 4px 6px rgba(0,0,0,0.02);
}

.theme-error .phone-icon-bg { color: #ef4444; }
.theme-warning .phone-icon-bg { color: #f59e0b; }

.contact-phone-full {
  font-size: clamp(20px, 2.2vw, 36px);
  font-weight: 800;
  color: #0f172a;
  letter-spacing: 0.5px;
}

.permission-actions {
  display: flex;
  gap: 20px;
  width: 100%;
  max-width: 600px;
}

.btn-back,
.btn-retry {
  flex: 1;
  height: 64px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  border: none;
  border-radius: 18px;
  font-size: 18px;
  font-weight: 700;
  cursor: pointer;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}

.btn-back {
  background: white;
  color: #475569;
  border: 2px solid #e2e8f0;
}

.btn-back:hover {
  background: #f1f5f9;
  border-color: #cbd5e1;
  transform: translateY(-2px);
}

.btn-retry {
  color: white;
  background: #0f172a;
  box-shadow: 0 10px 20px rgba(15, 23, 42, 0.15);
}

.btn-retry:hover {
  background: #1e293b;
  transform: translateY(-2px);
  box-shadow: 0 15px 30px rgba(15, 23, 42, 0.2);
}

.btn-retry:active {
  transform: scale(0.98);
}

@media (max-width: 1024px) {
  .content-wrapper {
    grid-template-columns: 1fr;
  }

  .header-area {
    padding: 60px 20px;
    min-height: auto;
  }

  .body-area {
    padding: 48px 24px;
    align-items: center;
    text-align: center;
  }

  .contact-main {
    justify-content: center;
  }

  .btn-retry {
    background: #4f46e5;
  }
}

@media (max-width: 480px) {
  .permission-title {
    font-size: 26px;
    letter-spacing: 2px;
  }

  .main-message {
    font-size: 24px;
  }

  .contact-card {
    padding: 24px;
  }

  .contact-phone {
    font-size: 32px;
  }

  .permission-actions {
    flex-direction: column;
  }

  .btn-back, .btn-retry {
    height: 56px;
    font-size: 16px;
  }
}
</style>
