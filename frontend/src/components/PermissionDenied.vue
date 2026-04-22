<template>
  <div class="permission-denied" :class="`theme-${theme}`">
    <div class="content-wrapper">
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

      <div class="body-area">
        <div class="permission-message">
          <p class="main-message">{{ mainMessage }}</p>
          <p v-if="subMessage" class="sub-message">{{ subMessage }}</p>
        </div>

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
  align-items: stretch;
  justify-content: stretch;
  background: linear-gradient(160deg, #667eea 0%, #764ba2 50%, #6a5acd 100%);
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', 'PingFang SC', 'Microsoft YaHei', sans-serif;
  padding: 0;
  box-sizing: border-box;
}

.content-wrapper {
  width: 100%;
  min-height: 100vh;
  display: grid;
  grid-template-columns: minmax(320px, 42vw) minmax(420px, 1fr);
  animation: fadeInUp 0.5s ease;
}

@keyframes fadeInUp {
  from { opacity: 0; transform: translateY(24px); }
  to { opacity: 1; transform: translateY(0); }
}

.header-area {
  position: relative;
  background: linear-gradient(160deg, rgba(255,255,255,0.12) 0%, rgba(255,255,255,0.05) 100%);
  padding: 56px 40px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  text-align: center;
  overflow: hidden;
}

.decor-circle {
  position: absolute;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.08);
}
.c1 { width: 220px; height: 220px; top: -40px; right: -40px; }
.c2 { width: 140px; height: 140px; bottom: 40px; left: -30px; }
.c3 { width: 88px; height: 88px; top: 72px; left: 18%; }

.permission-icon {
  position: relative;
  z-index: 1;
  width: 88px;
  height: 88px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  margin: 0 auto 20px;
  animation: popIn 0.4s ease;
}

@keyframes popIn {
  from { transform: scale(0.6); opacity: 0; }
  to { transform: scale(1); opacity: 1; }
}

.permission-title {
  position: relative;
  z-index: 1;
  font-size: clamp(28px, 3vw, 40px);
  font-weight: 700;
  color: white;
  letter-spacing: 2px;
  margin: 0;
}

.body-area {
  background: rgba(255, 255, 255, 0.96);
  backdrop-filter: blur(6px);
  padding: 56px clamp(24px, 4vw, 72px);
  display: flex;
  flex-direction: column;
  justify-content: center;
}

.permission-message {
  text-align: left;
  margin-bottom: 28px;
  max-width: 720px;
}

.main-message {
  font-size: clamp(28px, 2.6vw, 40px);
  font-weight: 700;
  color: #555;
  line-height: 1.35;
  margin: 0 0 12px;
}

.theme-error .main-message { color: #c0392b; }
.theme-info .main-message { color: #2980b9; }
.theme-warning .main-message { color: #d35400; }

.sub-message {
  font-size: clamp(16px, 1.2vw, 18px);
  color: #6b7280;
  line-height: 1.7;
  margin: 0;
}

.contact-card {
  background: #f8f9fb;
  border-radius: 18px;
  padding: 28px 32px;
  text-align: left;
  margin-bottom: 32px;
  max-width: 720px;
}

.contact-label {
  font-size: 14px;
  color: #9ca3af;
  margin-bottom: 12px;
}

.contact-phone {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: clamp(28px, 2.4vw, 40px);
  font-weight: 800;
  color: #2c3e50;
  margin-bottom: 8px;
  letter-spacing: 2px;
}

.contact-department {
  font-size: 16px;
  color: #6b7280;
}

.permission-actions {
  display: flex;
  gap: 14px;
  max-width: 720px;
}

.btn-back,
.btn-retry {
  flex: 1;
  min-height: 56px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 0 20px;
  border: none;
  border-radius: 14px;
  font-size: 16px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s ease;
}

.btn-back {
  background: #f0f2f5;
  color: #666;
}

.btn-back:hover {
  background: #e4e6e9;
}

.btn-back:active {
  background: #dce0e4;
}

.btn-retry {
  color: white;
  background: linear-gradient(135deg, #667eea, #764ba2);
  box-shadow: 0 4px 14px rgba(102, 126, 234, 0.3);
}

.btn-retry:hover {
  box-shadow: 0 6px 18px rgba(102, 126, 234, 0.4);
  transform: translateY(-1px);
}

.btn-retry:active {
  box-shadow: 0 2px 6px rgba(102, 126, 234, 0.2);
  transform: scale(0.97);
}

@media (max-width: 900px) {
  .content-wrapper {
    grid-template-columns: 1fr;
  }

  .header-area {
    padding: 48px 24px 36px;
    min-height: 34vh;
  }

  .body-area {
    padding: 32px 24px 28px;
  }

  .permission-message,
  .contact-card,
  .permission-actions {
    max-width: none;
  }

  .permission-message,
  .contact-card {
    text-align: center;
  }

  .contact-phone {
    justify-content: center;
  }
}

@media (max-width: 520px) {
  .permission-title {
    font-size: 22px;
    letter-spacing: 1px;
  }

  .main-message {
    font-size: 16px;
    font-weight: 500;
    line-height: 1.6;
    margin-bottom: 6px;
  }

  .sub-message {
    font-size: 13px;
    line-height: 1.5;
  }

  .contact-card {
    border-radius: 14px;
    padding: 20px;
    margin-bottom: 28px;
  }

  .contact-label {
    font-size: 12px;
    margin-bottom: 10px;
  }

  .contact-phone {
    font-size: 24px;
    margin-bottom: 6px;
    letter-spacing: 1.5px;
  }

  .contact-department {
    font-size: 13px;
    color: #aaa;
  }

  .permission-actions {
    flex-direction: row;
    gap: 10px;
  }

  .btn-back,
  .btn-retry {
    min-height: 48px;
    font-size: 15px;
    font-weight: 500;
    border-radius: 12px;
  }
}
</style>
