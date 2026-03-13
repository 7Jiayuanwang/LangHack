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

    // 检测是否在开发环境 - 使用 Vite 的 import.meta.env.MODE
    isDevelopment() {
        // import.meta.env 在 Vite 环境中始终存在，MODE 值为 'development' 或 'production'
        try {
            return import.meta.env.MODE === 'development'
        } catch (e) {
            // 非 Vite 构建环境的降级方案
            return typeof process !== 'undefined' && process.env?.NODE_ENV === 'development'
        }
    },

    // 获取 API 基础 URL
    getApiBaseUrl() {
        // import.meta.env 在 Vite 环境中始终存在
        try {
            const baseUrl = import.meta.env.VITE_API_BASE_URL
            // 如果明确配置了 API 地址，使用该地址
            if (baseUrl) {
                return baseUrl
            }
            // 开发环境使用相对路径 (由 vite.config.js 的 proxy 转发)
            if (this.isDevelopment()) {
                return '/api'
            }
            // 生产环境默认使用相对路径 (同域部署)
            // 如需跨域，请在 .env.production 中配置完整 URL
            return ''
        } catch (e) {
            console.warn('Failed to read VITE_API_BASE_URL:', e)
            return '/api'
        }
    },

    // 获取 Tutor API 基础 URL
    getTutorApiBaseUrl() {
        try {
            return import.meta.env.VITE_TUTOR_API_BASE_URL || '/tutor'
        } catch (e) {
            console.warn('Failed to read VITE_TUTOR_API_BASE_URL:', e)
            return '/tutor'
        }
    },

    // 获取构建类型
    getBuildType() {
        // __IS_MOBILE_BUILD__ 由 vite.config.js 的 define 配置注入
        try {
            return (typeof __IS_MOBILE_BUILD__ !== 'undefined' && __IS_MOBILE_BUILD__) ? 'mobile' : 'web'
        } catch (e) {
            return 'web'
        }
    }
}
