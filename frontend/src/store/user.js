import { defineStore } from "pinia";
import { login, logout } from "@/api/auth";
import {
  isValidToken,
  getToken,
  setToken,
  getRefreshToken,
  setRefreshToken,
  removeRefreshToken,
  clearAuthStorage,
  startProactiveRefresh,
  stopProactiveRefresh,
} from "@/utils/auth";

// 验证 userInfo 结构的有效性
function validateUserInfo(userInfo) {
  if (!userInfo || typeof userInfo !== "object" || Array.isArray(userInfo)) {
    return null;
  }
  // 确保必要字段存在
  if (!userInfo.id && !userInfo.username) {
    return null;
  }
  return userInfo;
}

// 从 localStorage 安全加载并验证 userInfo
function loadUserInfoFromStorage() {
  try {
    const stored = localStorage.getItem("userInfo");
    if (!stored) return {};

    const parsed = JSON.parse(stored);
    const validated = validateUserInfo(parsed);

    if (!validated) {
      // 破损数据：移除并返回空对象
      localStorage.removeItem("userInfo");
      return {};
    }

    return validated;
  } catch (e) {
    console.error("Failed to parse userInfo from localStorage:", e);
    try {
      localStorage.removeItem("userInfo");
    } catch (storageError) {
      console.warn("Failed to clear corrupted userInfo:", storageError);
    }
    return {};
  }
}

// 验证角色值的有效性
function validateRole(role) {
  const validRoles = ["ADMIN", "TEACHER", "STUDENT", "USER", "OPS"];
  if (!role || typeof role !== "string") {
    return "";
  }
  return validRoles.includes(role) ? role : "";
}

export const useUserStore = defineStore("user", {
  state: () => ({
    token: getToken(),
    refreshToken: getRefreshToken(),
    userInfo: loadUserInfoFromStorage(),
    userRole: (() => {
      try {
        const role = localStorage.getItem("userRole") || "";
        return validateRole(role);
      } catch (e) {
        console.error("Failed to load userRole from localStorage:", e);
        return "";
      }
    })(),
    elevatedRole: (() => {
      try {
        return sessionStorage.getItem("elevatedRole") || null;
      } catch (e) {
        return null;
      }
    })(),
    elevationExpiresAt: (() => {
      try {
        return sessionStorage.getItem("elevationExpiresAt") || null;
      } catch (e) {
        return null;
      }
    })(),
  }),

  getters: {
    isLoggedIn: (state) => isValidToken(state.token),
    isAdmin: (state) => state.userRole === "ADMIN",
    isTeacher: (state) => state.userRole === "TEACHER",
    isStudent: (state) => state.userRole === "STUDENT",
    isUser: (state) => state.userRole === "USER",
    isOps: (state) => state.userRole === "OPS",
    hasOpsAccess: (state) => {
      if (state.userRole === "ADMIN") return true;
      if (state.userRole === "OPS") return true;
      if (state.elevatedRole === "OPS" && state.elevationExpiresAt) {
        return new Date(state.elevationExpiresAt) > new Date();
      }
      return false;
    },
    hasAdminAccess: (state) => {
      if (state.userRole === "ADMIN") return true;
      if (state.elevatedRole === "ADMIN" && state.elevationExpiresAt) {
        return new Date(state.elevationExpiresAt) > new Date();
      }
      return false;
    },
  },

  actions: {
    async login(credentials) {
      try {
        const response = await login(credentials);
        const { token, accessToken, refreshToken, user, expiresIn } = response.data || {};

        // 向后兼容：优先使用新字段 accessToken，缺失时回退到旧 token
        const finalAccessToken = accessToken || token;
        const finalRefreshToken = refreshToken;

        if (!finalAccessToken || !user) {
          return Promise.reject(new Error("登录响应数据异常"));
        }

        // 验证 token 不是 "undefined"/"null"/空字符串
        if (!isValidToken(finalAccessToken)) {
          console.error("Server returned invalid token:", typeof finalAccessToken, finalAccessToken);
          return Promise.reject(new Error("登录响应 Token 异常"));
        }

        // 验证角色值有效性
        const validatedRole = validateRole(user.role);
        if (!validatedRole) {
          console.warn("Invalid role returned from server:", user.role);
          return Promise.reject(new Error("登录响应角色异常"));
        }

        // 先更新 Pinia state
        const prevState = {
          token: this.token,
          refreshToken: this.refreshToken,
          userInfo: { ...this.userInfo },
          userRole: this.userRole,
        };

        this.token = finalAccessToken;
        this.refreshToken = finalRefreshToken || '';
        this.userInfo = user;
        this.userRole = validatedRole;

        // 尝试持久化到 localStorage，失败时回滚
        try {
          if (!setToken(finalAccessToken)) {
            throw new Error("Token validation failed during persist");
          }
          if (finalRefreshToken && !setRefreshToken(finalRefreshToken)) {
            throw new Error("Refresh token validation failed during persist");
          }
          localStorage.setItem("userInfo", JSON.stringify(user));
          localStorage.setItem("userRole", validatedRole);
        } catch (storageError) {
          console.error("Failed to persist login state:", storageError);
          // 回滚 Pinia state 以保持一致性
          this.token = prevState.token;
          this.refreshToken = prevState.refreshToken;
          this.userInfo = prevState.userInfo;
          this.userRole = prevState.userRole;
          return Promise.reject(
            new Error("登录状态持久化失败，请检查存储空间"),
          );
        }

        // 启动主动刷新（access token 过期前 10 分钟自动刷新）
        if (finalRefreshToken && expiresIn) {
          startProactiveRefresh(expiresIn);
        }

        return Promise.resolve(response.data);
      } catch (error) {
        return Promise.reject(error);
      }
    },

    async logout() {
      const tokenToRevoke = this.token
      const refreshTokenToRevoke = this.refreshToken

      // 停止主动刷新定时器
      stopProactiveRefresh()

      // 先清理本地状态，确保即使 API 调用失败或延迟，Token 也不会残留
      this.token = "";
      this.refreshToken = "";
      this.userInfo = {};
      this.userRole = "";
      this.elevatedRole = null;
      this.elevationExpiresAt = null;

      // 清理 localStorage（集中清理所有认证相关 key）
      clearAuthStorage();

      // 清理 sessionStorage 中的提权状态
      try {
        sessionStorage.removeItem("elevatedRole");
        sessionStorage.removeItem("elevationExpiresAt");
      } catch (e) {
        console.warn("Failed to clear elevation from sessionStorage:", e);
      }

      // 再调用后端 logout API（失败不影响本地已清理的状态）
      try {
        await logout(tokenToRevoke, refreshTokenToRevoke);
      } catch (error) {
        console.error("Logout API error (local state already cleared):", error);
      }
    },

    updateUserInfo(userInfo) {
      if (
        !userInfo ||
        typeof userInfo !== "object" ||
        Array.isArray(userInfo)
      ) {
        console.error("Invalid userInfo provided to updateUserInfo:", userInfo);
        return Promise.reject(new Error("Invalid userInfo"));
      }

      // 保存前置状态用于回滚
      const prevUserInfo = { ...this.userInfo };
      const prevUserRole = this.userRole;

      // 更新 Pinia state
      const mergedInfo = { ...this.userInfo, ...userInfo };
      this.userInfo = mergedInfo;

      // 如果提供了新的 role，验证并更新
      if (userInfo.role) {
        const validatedRole = validateRole(userInfo.role);
        if (!validatedRole) {
          console.error("Invalid role in updateUserInfo:", userInfo.role);
          // 回滚
          this.userInfo = prevUserInfo;
          return Promise.reject(new Error("Invalid role"));
        }
        this.userRole = validatedRole;
      }

      // 尝试持久化，失败时回滚
      try {
        localStorage.setItem("userInfo", JSON.stringify(this.userInfo));
        if (userInfo.role) {
          localStorage.setItem("userRole", this.userRole);
        }
      } catch (storageError) {
        console.error("Failed to persist userInfo:", storageError);
        // 回滚 state
        this.userInfo = prevUserInfo;
        this.userRole = prevUserRole;
        return Promise.reject(new Error("用户信息保存失败，请检查存储空间"));
      }

      return Promise.resolve(this.userInfo);
    },

    setElevation(elevatedRole, expiresAt) {
      this.elevatedRole = elevatedRole;
      this.elevationExpiresAt = expiresAt;
      try {
        sessionStorage.setItem("elevatedRole", elevatedRole);
        sessionStorage.setItem("elevationExpiresAt", expiresAt);
      } catch (e) {
        console.error("Failed to persist elevation state:", e);
      }
      // 启动过期定时器，确保 getter 在提权过期后自动刷新
      this._startElevationTimer();
    },

    _startElevationTimer() {
      if (this._elevationTimer) {
        clearInterval(this._elevationTimer);
        this._elevationTimer = null;
      }
      if (!this.elevationExpiresAt) return;
      this._elevationTimer = setInterval(() => {
        if (
          this.elevationExpiresAt &&
          new Date(this.elevationExpiresAt) <= new Date()
        ) {
          this.clearElevation();
        }
      }, 60000); // 每分钟检查一次
    },

    clearElevation() {
      this.elevatedRole = null;
      this.elevationExpiresAt = null;
      if (this._elevationTimer) {
        clearInterval(this._elevationTimer);
        this._elevationTimer = null;
      }
      try {
        sessionStorage.removeItem("elevatedRole");
        sessionStorage.removeItem("elevationExpiresAt");
      } catch (e) {
        console.error("Failed to clear elevation state:", e);
      }
    },

    /**
     * 原子替换双 token（401 自动刷新后调用）。
     * 同时重排主动刷新定时器。
     */
    setTokens({ accessToken, refreshToken, expiresIn }) {
      if (!isValidToken(accessToken)) {
        console.error('setTokens received invalid accessToken:', accessToken)
        return
      }

      this.token = accessToken
      if (refreshToken) {
        this.refreshToken = refreshToken
      }

      try {
        setToken(accessToken)
        if (refreshToken) {
          setRefreshToken(refreshToken)
        }
      } catch (e) {
        console.error('Failed to persist tokens in setTokens:', e)
      }

      // 重排主动刷新定时器
      if (this.refreshToken && expiresIn) {
        startProactiveRefresh(expiresIn)
      }
    },
  },
});
