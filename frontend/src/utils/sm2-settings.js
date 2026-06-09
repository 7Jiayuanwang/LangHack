/**
 * SM-2 用户设置管理
 *
 * 持久化用户对困难词模式、过载保护、复习策略的选择
 * 存储到 localStorage（与生词本 progress 保持解耦，方便清除与迁移）
 */

const STORAGE_KEY = 'sm2_user_settings_v1'

const DEFAULT_SETTINGS = {
 hardWordMode: false, // 困难词加强模式（EF 下限 1.0）
 overloadProtection: true, // 过载保护（自动分批）
 maxDailyReview: 50, // 单日最大复习量
 emergencyThreshold: 75, // 紧急过载阈值
 deferDays: 1, // 低优先级单词延期天数
 showAnalytics: false, // 是否在学习页面显示数据监控面板
 enableAbTest: false, // 是否启用 A/B 对比模式（用于分析）
 abTestVariant: 'standard' // A/B 测试变体
}

/**
 * 读取用户设置
 * @returns {Object} 设置对象（始终包含全部字段）
 */
export function getSettings() {
 try {
 const raw = localStorage.getItem(STORAGE_KEY)
 if (!raw) return cloneDefault()
 const parsed = JSON.parse(raw)
 return Object.assign(cloneDefault(), parsed)
 } catch (e) {
 console.warn('[sm2-settings] 读取失败，使用默认设置:', e)
 return cloneDefault()
 }
}

function cloneDefault() {
 return JSON.parse(JSON.stringify(DEFAULT_SETTINGS))
}

/**
 * 保存用户设置
 * @param {Object} patch - 要更新的字段（部分更新）
 * @returns {Object} 合并后的完整设置
 */
export function saveSettings(patch = {}) {
 const current = getSettings()
 const next = Object.assign({}, current, patch)
 try {
 localStorage.setItem(STORAGE_KEY, JSON.stringify(next))
 } catch (e) {
 console.error('[sm2-settings] 保存失败:', e)
 }
 return next
}

/**
 * 重置为默认设置
 */
export function resetSettings() {
 try {
 localStorage.removeItem(STORAGE_KEY)
 } catch (e) {
 console.error('[sm2-settings] 重置失败:', e)
 }
 return cloneDefault()
}

/**
 * 计算当前设置下 SM-2 调用选项
 */
export function buildSM2Options(settings = getSettings()) {
 return {
 hardWordMode: settings.hardWordMode
 }
}

/**
 * 计算当前设置下过载保护调用选项
 */
export function buildOverloadOptions(settings = getSettings()) {
 return {
 enabled: settings.overloadProtection,
 maxDailyReview: settings.maxDailyReview,
 emergencyThreshold: settings.emergencyThreshold,
 deferDays: settings.deferDays
 }
}

export { DEFAULT_SETTINGS }
