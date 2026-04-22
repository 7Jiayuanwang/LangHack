/**
 * SSO 错误映射
 *
 * 将后端返回的 errorCode（见 backend/src/main/java/com/english/sso/exception/SsoErrorCode.java）
 * 转换为带联系方式的友好提示对象，统一由 PermissionDenied 组件展示。
 *
 * 设计原则：
 * 1. 每个错误都展示联系方式（统一身份认证对接方的要求）
 * 2. 不同错误用不同的 theme 区分图标/配色，避免所有错误看起来都像"权限不足"
 * 3. 保留后端原始 message 作为 rawMessage，便于排错
 */

// 主管部门联系方式（统一身份认证对接要求）
export const DEFAULT_CONTACT = Object.freeze({
  contactPhone: '43019235',
  contactDepartment: '计算机与信息工程学院'
})

/**
 * 所有可能的错误主题（对应 PermissionDenied.vue 的 theme prop）
 * - warning: 橙色 — 账号/权限类问题
 * - info: 蓝色 — 审批中/会话过期等非故障
 * - error: 红色 — 认证失败/系统故障
 */
export const SSO_ERROR_THEMES = Object.freeze({
  WARNING: 'warning',
  INFO: 'info',
  ERROR: 'error'
})

/**
 * 错误码 → 文案映射表
 *
 * 后端 errorCode 未识别时回退到 UNKNOWN。
 */
const ERROR_MAP = {
  USER_NOT_FOUND: {
    mainMessage: '对不起，您无权使用Langhack英语学习智能体平台。',
    subMessage: '您的账号尚未在本系统开通使用权限',
    theme: SSO_ERROR_THEMES.WARNING
  },
  PENDING_APPROVAL: {
    mainMessage: '您的账号正在审批中',
    subMessage: '请等待管理员审批后重新登录，如长时间未审批可联系主管部门',
    theme: SSO_ERROR_THEMES.INFO
  },
  AUTH_FAILED: {
    mainMessage: '统一身份认证校验未通过',
    subMessage: '登录票据无效或已被使用，请返回登录页重试',
    theme: SSO_ERROR_THEMES.ERROR
  },
  CODE_EXPIRED: {
    mainMessage: '登录会话已过期',
    subMessage: '授权码仅在 5 分钟内有效，请重新发起登录',
    theme: SSO_ERROR_THEMES.INFO
  },
  SSO_DISABLED: {
    mainMessage: '统一身份认证暂时不可用',
    subMessage: '系统正在维护中，请稍后重试或联系主管部门',
    theme: SSO_ERROR_THEMES.WARNING
  },
  MISSING_PARAM: {
    mainMessage: '登录请求异常',
    subMessage: '缺少必要的认证参数，请通过正规入口（登录页面）发起登录',
    theme: SSO_ERROR_THEMES.ERROR
  },
  SYSTEM_ERROR: {
    mainMessage: '对不起，您无权使用Langhack英语学习智能体平台。',
    subMessage: '',
    theme: SSO_ERROR_THEMES.ERROR
  },
  UNKNOWN: {
    mainMessage: '登录失败',
    subMessage: '发生未预期的错误，请联系主管部门协助排查',
    theme: SSO_ERROR_THEMES.ERROR
  }
}

/**
 * 关键字回退匹配表（当 errorCode 缺失时按 message 关键字猜测）
 *
 * 用于兼容：
 * 1. 老版本后端返回仅 message 没有 errorCode
 * 2. 非 SSO 链路透出的业务异常（例如 GlobalExceptionHandler 包装后的 message）
 */
const KEYWORD_FALLBACKS = [
  { keyword: '无权使用', code: 'USER_NOT_FOUND' },
  { keyword: '用户不存在', code: 'USER_NOT_FOUND' },
  { keyword: '审批', code: 'PENDING_APPROVAL' },
  { keyword: '已过期', code: 'CODE_EXPIRED' },
  { keyword: '无效', code: 'CODE_EXPIRED' },
  { keyword: '缺少', code: 'MISSING_PARAM' },
  { keyword: '未启用', code: 'SSO_DISABLED' },
  { keyword: '认证失败', code: 'AUTH_FAILED' },
  { keyword: '票据', code: 'AUTH_FAILED' }
]

/**
 * 根据 errorCode 和 rawMessage 计算展示内容
 *
 * @param {Object} input
 * @param {string} [input.errorCode] 后端返回的错误码（优先）
 * @param {string} [input.rawMessage] 原始错误消息（用于关键字回退和展示）
 * @returns {{
 *   mainMessage: string,
 *   subMessage: string,
 *   contactPhone: string,
 *   contactDepartment: string,
 *   theme: string,
 *   rawMessage: string,
 *   errorCode: string
 * }}
 */
export function mapSsoError({ errorCode, rawMessage } = {}) {
  const normalized = normalizeErrorCode(errorCode, rawMessage)
  const template = ERROR_MAP[normalized] || ERROR_MAP.UNKNOWN

  return {
    errorCode: normalized,
    mainMessage: template.mainMessage,
    subMessage: template.subMessage,
    theme: template.theme,
    rawMessage: rawMessage || '',
    ...DEFAULT_CONTACT
  }
}

function normalizeErrorCode(errorCode, rawMessage) {
  if (errorCode && ERROR_MAP[errorCode]) {
    return errorCode
  }
  if (rawMessage) {
    const hit = KEYWORD_FALLBACKS.find((entry) => rawMessage.includes(entry.keyword))
    if (hit) return hit.code
  }
  return 'UNKNOWN'
}
