<template>
  <div class="permission-denied" :class="`theme-${theme}`">
    <div class="permission-container">
      <!-- 温馨提示图标 -->
      <div class="permission-icon">
        <svg v-if="theme === 'info'" width="64" height="64" viewBox="0 0 24 24" fill="none">
          <circle cx="12" cy="12" r="10" :stroke="iconColor" stroke-width="2"/>
          <path d="M12 16v-5m0-3h.01" :stroke="iconColor" stroke-width="2" stroke-linecap="round"/>
        </svg>
        <svg v-else-if="theme === 'error'" width="64" height="64" viewBox="0 0 24 24" fill="none">
          <circle cx="12" cy="12" r="10" :stroke="iconColor" stroke-width="2"/>
          <path d="M15 9l-6 6m0-6l6 6" :stroke="iconColor" stroke-width="2" stroke-linecap="round"/>
        </svg>
        <svg v-else width="64" height="64" viewBox="0 0 24 24" fill="none">
          <circle cx="12" cy="12" r="10" :stroke="iconColor" stroke-width="2"/>
          <path d="M12 7v5m0 3h.01" :stroke="iconColor" stroke-width="2" stroke-linecap="round"/>
        </svg>
      </div>

      <!-- 温馨提示标题 -->
      <h2 class="permission-title">温馨提示</h2>

      <!-- 权限拒绝信息 -->
      <div class="permission-message">
        <p class="main-message">{{ mainMessage }}</p>
        <p v-if="subMessage" class="sub-message">{{ subMessage }}</p>
      </div>

      <!-- 联系方式 -->
      <div class="contact-info">
        <div class="contact-header">
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none">
            <path d="M22 16.92v3a2 2 0 0 1-2.18 2 19.79 19.79 0 0 1-8.63-3.07 19.5 19.5 0 0 1-6-6 19.79 19.79 0 0 1-3.07-8.67A2 2 0 0 1 4.11 2h3a2 2 0 0 1 2 1.72 12.84 12.84 0 0 0 .7 2.81 2 2 0 0 1-.45 2.11L8.09 9.91a16 16 0 0 0 6 6l1.27-1.27a2 2 0 0 1 2.11-.45 12.84 12.84 0 0 0 2.81-.7A2 2 0 0 1 22 16.92z" stroke="#666" stroke-width="2" stroke-linecap="round"/>
          </svg>
          <span>如有疑问请致电</span>
        </div>
        <div class="contact-phone">{{ contactPhone }}</div>
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
  // 主要拒绝信息
  mainMessage: {
    type: String,
    default: '对不起，您无权使用LangHack英语学习智能体平台'
  },
  // 次要拒绝信息
  subMessage: {
    type: String,
    default: '您的账号正在审批中或权限不足'
  },
  // 联系电话
  contactPhone: {
    type: String,
    default: '43019235'
  },
  // 联系部门
  contactDepartment: {
    type: String,
    default: '计算机与信息工程学院'
  },
  // 主题：warning | info | error
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

const iconColor = computed(() => THEME_COLORS[props.theme] || THEME_COLORS.warning)

const emit = defineEmits(['close'])

// 返回登录页
const goBack = () => {
  emit('close')
  router.push('/login')
}

// 重新登录
const retryLogin = () => {
  // 清除可能的本地存储
  localStorage.removeItem('token')
  localStorage.removeItem('user')
  router.push('/login')
}
</script>

<style scoped>
.permission-denied {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  padding: 20px;
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', 'PingFang SC', 'Microsoft YaHei', sans-serif;
}

.permission-container {
  background: white;
  border-radius: 16px;
  padding: 48px;
  max-width: 480px;
  width: 100%;
  text-align: center;
  box-shadow: 0 20px 40px rgba(0, 0, 0, 0.1);
  animation: fadeInUp 0.6s ease;
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

.permission-icon {
  margin-bottom: 24px;
  display: flex;
  justify-content: center;
}

.permission-title {
  font-size: 24px;
  font-weight: 600;
  color: #2c3e50;
  margin-bottom: 16px;
  letter-spacing: -0.5px;
}

.permission-message {
  margin-bottom: 32px;
}

.main-message {
  font-size: 18px;
  font-weight: 500;
  color: #e74c3c;
  margin-bottom: 8px;
  line-height: 1.4;
}

.theme-info .main-message {
  color: #3498db;
}

.theme-warning .main-message {
  color: #f0884a;
}

.sub-message {
  font-size: 14px;
  color: #666;
  line-height: 1.5;
}

.contact-info {
  background: #f8f9fa;
  border-radius: 12px;
  padding: 24px;
  margin-bottom: 32px;
  border: 1px solid #e9ecef;
}

.contact-header {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  margin-bottom: 12px;
  font-size: 14px;
  color: #666;
  font-weight: 500;
}

.contact-phone {
  font-size: 24px;
  font-weight: 700;
  color: #2c3e50;
  margin-bottom: 8px;
  letter-spacing: 1px;
}

.contact-department {
  font-size: 14px;
  color: #666;
  font-style: italic;
}

.permission-actions {
  display: flex;
  gap: 12px;
  justify-content: center;
  flex-wrap: wrap;
}

.btn-back,
.btn-retry {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 24px;
  border: none;
  border-radius: 8px;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.3s ease;
  text-decoration: none;
}

.btn-back {
  background: #ecf0f1;
  color: #2c3e50;
}

.btn-back:hover {
  background: #d5dbdd;
  transform: translateY(-1px);
}

.btn-retry {
  background: #3498db;
  color: white;
}

.btn-retry:hover {
  background: #2980b9;
  transform: translateY(-1px);
  box-shadow: 0 4px 12px rgba(52, 152, 219, 0.3);
}

/* 响应式设计 */
@media (max-width: 480px) {
  .permission-container {
    padding: 32px 24px;
    margin: 16px;
  }
  
  .permission-title {
    font-size: 20px;
  }
  
  .main-message {
    font-size: 16px;
  }
  
  .contact-phone {
    font-size: 20px;
  }
  
  .permission-actions {
    flex-direction: column;
  }
  
  .btn-back,
  .btn-retry {
    width: 100%;
    justify-content: center;
  }
}
</style>
