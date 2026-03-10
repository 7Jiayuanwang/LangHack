// 环境检测工具
export const environment = {
    // 检测是否在移动端
    isMobile() {
        // SSR 环境防御: navigator 可能不存在
        if (typeof navigator === 'undefined' || !navigator.userAgent) {
            return false
        }
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
        // process.env 在某些构建配置下可能不存在
        return typeof process !== 'undefined' && process.env?.NODE_ENV === 'development'
    },

    // 获取 API 基础 URL
    getApiBaseUrl() {
        // import.meta.env 在非 Vite 环境可能不存在
        const viteEnv = typeof import.meta !== 'undefined' ? import.meta.env : {}
        return viteEnv.VITE_API_BASE_URL || '/api'
    },

    // 获取构建类型
    getBuildType() {
        // __IS_MOBILE_BUILD__ 全局变量可能未定义
        return (typeof __IS_MOBILE_BUILD__ !== 'undefined' && __IS_MOBILE_BUILD__) ? 'mobile' : 'web'
    }
}