<template>
  <div class="admin-login">
    <!-- 状态栏占位 -->
    <div class="status-bar"></div>

    <div class="admin-container">
      <!-- 背景 -->
      <div class="admin-background">
        <div class="bg-gradient"></div>
      </div>

      <!-- 内容 -->
      <div class="admin-content">
        <!-- 品牌 -->
        <div class="admin-brand">
          <div class="admin-logo">
            <div class="logo-inner">
              <div class="logo-icon">🔐</div>
              <div class="logo-text">Admin Portal</div>
            </div>
          </div>
          <p class="admin-subtitle">管理员专用入口</p>
        </div>

        <!-- 登录卡片 -->
        <div class="admin-card">
          <div class="card-header">
            <h2>管理员登录</h2>
            <p class="warning-text">⚠️ 此入口仅供管理员和运维人员使用</p>
          </div>

          <form @submit.prevent="handleLogin" class="admin-form">
            <!-- 身份选择 -->
            <div class="identity-selector">
              <button
                type="button"
                class="identity-btn"
                :class="{ active: loginIdentity === 'admin' }"
                @click="loginIdentity = 'admin'"
              >
                👑 管理员
              </button>
              <button
                type="button"
                class="identity-btn"
                :class="{ active: loginIdentity === 'ops' }"
                @click="loginIdentity = 'ops'"
              >
                🔧 运维
              </button>
            </div>

            <div class="form-field">
              <div class="input-group">
                <input
                  v-model="loginForm.username"
                  type="text"
                  :placeholder="identityPlaceholder"
                  class="form-input"
                  required
                  autocomplete="username"
                />
                <div class="input-icon">
                  <svg
                    width="20"
                    height="20"
                    viewBox="0 0 24 24"
                    fill="none"
                    stroke="currentColor"
                    stroke-width="2"
                  >
                    <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2" />
                    <circle cx="12" cy="7" r="4" />
                  </svg>
                </div>
              </div>
            </div>

            <div class="form-field">
              <div class="input-group">
                <input
                  v-model="loginForm.password"
                  :type="showPassword ? 'text' : 'password'"
                  placeholder="密码"
                  autocomplete="current-password"
                  class="form-input"
                  required
                />
                <div class="input-icon">
                  <svg
                    width="20"
                    height="20"
                    viewBox="0 0 24 24"
                    fill="none"
                    stroke="currentColor"
                    stroke-width="2"
                  >
                    <rect x="3" y="11" width="18" height="11" rx="2" ry="2" />
                    <path d="M7 11V7a5 5 0 0 1 10 0v4" />
                  </svg>
                </div>
                <button
                  type="button"
                  class="password-toggle"
                  @click="showPassword = !showPassword"
                >
                  <svg
                    v-if="showPassword"
                    width="18"
                    height="18"
                    viewBox="0 0 24 24"
                    fill="none"
                    stroke="currentColor"
                    stroke-width="2"
                  >
                    <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z" />
                    <circle cx="12" cy="12" r="3" />
                  </svg>
                  <svg
                    v-else
                    width="18"
                    height="18"
                    viewBox="0 0 24 24"
                    fill="none"
                    stroke="currentColor"
                    stroke-width="2"
                  >
                    <path
                      d="M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19m-6.72-1.07a3 3 0 1 1-4.24-4.24"
                    />
                    <line x1="1" y1="1" x2="23" y2="23" />
                  </svg>
                </button>
              </div>
            </div>

            <button
              type="submit"
              class="submit-button"
              :disabled="loginLoading"
            >
              <span v-if="!loginLoading">登录</span>
              <div v-else class="loading-spinner">
                <svg
                  width="18"
                  height="18"
                  viewBox="0 0 24 24"
                  fill="none"
                  stroke="white"
                  stroke-width="2"
                >
                  <path d="M21 12a9 9 0 1 1-6.219-8.56" />
                </svg>
              </div>
            </button>
          </form>

          <div class="card-footer">
            <p class="footer-text">
              普通用户请使用
              <a href="/login" class="link">统一身份认证</a>
              登录
            </p>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from "vue";
import { useRouter, useRoute } from "vue-router";
import { ElMessage } from "element-plus";
import { useUserStore } from "@/store/user";

const router = useRouter();
const route = useRoute();
const userStore = useUserStore();

const loginLoading = ref(false);
const showPassword = ref(false);
const loginIdentity = ref("admin");

const identityPlaceholder = computed(
  () =>
    ({
      admin: "管理员账号",
      ops: "运维账号",
    })[loginIdentity.value]
);

const loginForm = reactive({
  username: "",
  password: "",
});

// 验证叩门令牌
onMounted(() => {
  const token = route.query.token;
  const timestamp = route.query.t;

  // 简单的叩门验证：检查 token 和时间戳
  if (!token || !timestamp) {
    ElMessage.error("访问被拒绝：缺少有效的访问令牌");
    setTimeout(() => {
      router.push("/login");
    }, 2000);
    return;
  }

  // 验证时间戳（5分钟有效期）
  const now = Date.now();
  const tokenTime = parseInt(timestamp);
  if (now - tokenTime > 5 * 60 * 1000) {
    ElMessage.error("访问被拒绝：令牌已过期");
    setTimeout(() => {
      router.push("/login");
    }, 2000);
    return;
  }

  // 验证 token（简单的哈希验证）
  const expectedToken = generateToken(timestamp);
  if (token !== expectedToken) {
    ElMessage.error("访问被拒绝：令牌无效");
    setTimeout(() => {
      router.push("/login");
    }, 2000);
  }
});

// 生成令牌（与后端保持一致的算法）
function generateToken(timestamp) {
  // 简单的哈希算法：timestamp + secret
  const secret = import.meta.env.VITE_ADMIN_LOGIN_SECRET || "default-secret";
  const str = timestamp + secret;
  let hash = 0;
  for (let i = 0; i < str.length; i++) {
    const char = str.charCodeAt(i);
    hash = (hash << 5) - hash + char;
    hash = hash & hash;
  }
  return Math.abs(hash).toString(36);
}

const handleLogin = async () => {
  if (!loginForm.username || !loginForm.password) {
    ElMessage.warning(`请填写${identityPlaceholder.value}和密码`);
    return;
  }

  loginLoading.value = true;
  try {
    await userStore.login(loginForm);
    ElMessage.success("登录成功");

    // 验证角色权限
    if (!["ADMIN", "OPS"].includes(userStore.userRole)) {
      ElMessage.error("权限不足：此入口仅供管理员和运维使用");
      userStore.logout();
      return;
    }

    switch (userStore.userRole) {
      case "ADMIN":
        router.push("/admin");
        break;
      case "OPS":
        router.push("/ops");
        break;
      default:
        ElMessage.error("权限不足");
        userStore.logout();
    }
  } catch (error) {
    let errorMsg = "登录失败，请重试";
    if (typeof error === "string") {
      errorMsg = error;
    } else if (error?.message) {
      errorMsg = error.message;
    } else if (error?.data?.message) {
      errorMsg = error.data.message;
    }
    ElMessage.error(errorMsg);
  } finally {
    loginLoading.value = false;
  }
};
</script>

<style scoped>
.admin-login {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  font-family:
    -apple-system, BlinkMacSystemFont, "Segoe UI", "PingFang SC",
    "Microsoft YaHei", sans-serif;
  position: relative;
  overflow: hidden;
}

.status-bar {
  height: env(safe-area-inset-top, 0);
  background: transparent;
}

.admin-background {
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
  background: linear-gradient(135deg, #1a1a2e 0%, #16213e 50%, #0f3460 100%);
  opacity: 0.95;
}

.admin-container {
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

.admin-brand {
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

.admin-logo {
  display: inline-block;
  margin-bottom: 20px;
}

.logo-inner {
  background: rgba(255, 255, 255, 0.1);
  backdrop-filter: blur(20px);
  border: 1px solid rgba(255, 255, 255, 0.2);
  border-radius: 24px;
  padding: 12px 20px;
  display: flex;
  align-items: center;
  gap: 8px;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.3);
}

.logo-icon {
  font-size: 24px;
}

.logo-text {
  font-size: 18px;
  font-weight: 700;
  color: white;
  letter-spacing: -0.5px;
}

.admin-subtitle {
  font-size: 14px;
  color: rgba(255, 255, 255, 0.7);
  margin: 0;
  letter-spacing: 2px;
  font-weight: 500;
}

.admin-card {
  background: rgba(255, 255, 255, 0.95);
  backdrop-filter: blur(20px);
  border: 1px solid rgba(255, 255, 255, 0.3);
  border-radius: 24px;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.4);
  overflow: hidden;
  width: 100%;
  animation: fadeInUp 0.8s ease 0.2s;
  animation-fill-mode: both;
}

.card-header {
  padding: 32px 24px 24px 24px;
  text-align: center;
  border-bottom: 1px solid rgba(0, 0, 0, 0.05);
}

.card-header h2 {
  margin: 0 0 12px 0;
  font-size: 24px;
  font-weight: 700;
  color: #1a1a2e;
}

.warning-text {
  margin: 0;
  font-size: 13px;
  color: #e74c3c;
  font-weight: 500;
}

.admin-form {
  padding: 24px;
}

.identity-selector {
  display: flex;
  gap: 8px;
  margin-bottom: 20px;
}

.identity-btn {
  flex: 1;
  padding: 10px 8px;
  border: 1.5px solid rgba(0, 0, 0, 0.1);
  border-radius: 8px;
  background: rgba(0, 0, 0, 0.02);
  color: rgba(0, 0, 0, 0.5);
  font-size: 14px;
  cursor: pointer;
  transition: all 0.3s;
  font-weight: 600;
}

.identity-btn:hover {
  border-color: rgba(231, 76, 60, 0.4);
  background: rgba(231, 76, 60, 0.05);
  color: rgba(0, 0, 0, 0.7);
}

.identity-btn.active {
  border-color: #e74c3c;
  background: rgba(231, 76, 60, 0.1);
  color: #e74c3c;
}

.form-field {
  margin-bottom: 16px;
}

.input-group {
  position: relative;
  display: flex;
  align-items: center;
}

.form-input {
  width: 100%;
  padding: 14px 16px 14px 48px;
  background: rgba(0, 0, 0, 0.02);
  border: 1px solid rgba(0, 0, 0, 0.1);
  border-radius: 12px;
  font-size: 15px;
  color: #1a1a2e;
  transition: all 0.3s ease;
  outline: none;
  font-weight: 500;
}

.form-input:focus {
  border-color: #e74c3c;
  box-shadow: 0 0 0 3px rgba(231, 76, 60, 0.1);
  background: white;
}

.form-input::placeholder {
  color: rgba(0, 0, 0, 0.3);
  font-weight: 400;
}

.input-icon {
  position: absolute;
  left: 16px;
  color: rgba(0, 0, 0, 0.3);
  z-index: 1;
  transition: color 0.3s ease;
}

.form-input:focus + .input-icon {
  color: #e74c3c;
}

.password-toggle {
  position: absolute;
  right: 12px;
  background: none;
  border: none;
  color: rgba(0, 0, 0, 0.3);
  cursor: pointer;
  padding: 4px;
  border-radius: 6px;
  transition: all 0.3s ease;
  display: flex;
  align-items: center;
  justify-content: center;
}

.password-toggle:hover {
  color: rgba(0, 0, 0, 0.6);
  background: rgba(0, 0, 0, 0.05);
}

.submit-button {
  width: 100%;
  padding: 16px;
  background: linear-gradient(135deg, #e74c3c 0%, #c0392b 100%);
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
  box-shadow: 0 4px 12px rgba(231, 76, 60, 0.3);
}

.submit-button:hover:not(:disabled) {
  transform: translateY(-2px);
  box-shadow: 0 8px 24px rgba(231, 76, 60, 0.4);
}

.submit-button:active:not(:disabled) {
  transform: translateY(-1px);
}

.submit-button:disabled {
  background: rgba(0, 0, 0, 0.2);
  color: rgba(0, 0, 0, 0.4);
  cursor: not-allowed;
  box-shadow: none;
}

.loading-spinner svg {
  animation: spin 1s linear infinite;
}

@keyframes spin {
  from {
    transform: rotate(0deg);
  }
  to {
    transform: rotate(360deg);
  }
}

.card-footer {
  padding: 20px 24px;
  text-align: center;
  border-top: 1px solid rgba(0, 0, 0, 0.05);
  background: rgba(0, 0, 0, 0.02);
}

.footer-text {
  margin: 0;
  font-size: 13px;
  color: rgba(0, 0, 0, 0.5);
}

.link {
  color: #e74c3c;
  text-decoration: none;
  font-weight: 600;
  transition: color 0.3s;
}

.link:hover {
  color: #c0392b;
  text-decoration: underline;
}

/* 响应式设计 */
@media (max-width: 480px) {
  .admin-container {
    padding: 20px 16px;
  }

  .admin-card {
    border-radius: 20px;
  }

  .card-header {
    padding: 24px 20px 20px 20px;
  }

  .card-header h2 {
    font-size: 20px;
  }

  .admin-form {
    padding: 20px;
  }

  .form-input {
    padding: 14px 16px 14px 44px;
    font-size: 16px;
  }
}

/* 触摸优化 */
.identity-btn,
.password-toggle,
.submit-button {
  -webkit-tap-highlight-color: transparent;
  touch-action: manipulation;
}
</style>
