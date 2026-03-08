// 环境检测工具
export const environment = {
    // 检测是否在移动端
    isMobile() {
        return /Android|webOS|iPhone|iPad|iPod|BlackBerry|IEMobile|Opera Mini/i.test(
            navigator.userAgent
        )
    },

    // 检测是否在 Capacitor 环境中
    isCapacitor() {
        return typeof window !== 'undefined' && window.Capacitor !== undefined
    },

    // 检测是否在开发环境
    isDevelopment() {
        return process.env.NODE_ENV === 'development'
    },

    // 获取 API 基础 URL
    getApiBaseUrl() {
        return import.meta.env.VITE_API_BASE_URL || '/api'
    },

    // 获取构建类型
    getBuildType() {
        return __IS_MOBILE_BUILD__ ? 'mobile' : 'web'
    }
}