import { describe, it, expect } from 'vitest'
import { mapSsoError, DEFAULT_CONTACT, SSO_ERROR_THEMES } from '@/utils/ssoErrorMapper'

describe('ssoErrorMapper', () => {
  describe('mapSsoError', () => {
    it('映射 USER_NOT_FOUND 到无权使用提示，主题为 warning', () => {
      const result = mapSsoError({ errorCode: 'USER_NOT_FOUND', rawMessage: '用户不存在' })

      expect(result.mainMessage).toContain('无权使用')
      expect(result.theme).toBe(SSO_ERROR_THEMES.WARNING)
      expect(result.contactPhone).toBe(DEFAULT_CONTACT.contactPhone)
      expect(result.contactDepartment).toBe(DEFAULT_CONTACT.contactDepartment)
      expect(result.rawMessage).toBe('用户不存在')
      expect(result.errorCode).toBe('USER_NOT_FOUND')
    })

    it('映射 PENDING_APPROVAL 为 info 主题', () => {
      const result = mapSsoError({ errorCode: 'PENDING_APPROVAL' })
      expect(result.theme).toBe(SSO_ERROR_THEMES.INFO)
      expect(result.mainMessage).toContain('审批')
    })

    it('映射 AUTH_FAILED 为 error 主题', () => {
      const result = mapSsoError({ errorCode: 'AUTH_FAILED' })
      expect(result.theme).toBe(SSO_ERROR_THEMES.ERROR)
      expect(result.mainMessage).toContain('校验未通过')
    })

    it('映射 CODE_EXPIRED 为 info 主题', () => {
      const result = mapSsoError({ errorCode: 'CODE_EXPIRED' })
      expect(result.theme).toBe(SSO_ERROR_THEMES.INFO)
      expect(result.mainMessage).toContain('过期')
    })

    it('映射 SSO_DISABLED 为 warning 主题', () => {
      const result = mapSsoError({ errorCode: 'SSO_DISABLED' })
      expect(result.theme).toBe(SSO_ERROR_THEMES.WARNING)
      expect(result.mainMessage).toContain('不可用')
    })

    it('映射 MISSING_PARAM 为 error 主题', () => {
      const result = mapSsoError({ errorCode: 'MISSING_PARAM' })
      expect(result.theme).toBe(SSO_ERROR_THEMES.ERROR)
    })

    it('映射 SYSTEM_ERROR 为 error 主题', () => {
      const result = mapSsoError({ errorCode: 'SYSTEM_ERROR' })
      expect(result.theme).toBe(SSO_ERROR_THEMES.ERROR)
    })

    it('未知 errorCode 回退到 UNKNOWN', () => {
      const result = mapSsoError({ errorCode: 'NOT_A_REAL_CODE', rawMessage: 'oops' })
      expect(result.errorCode).toBe('UNKNOWN')
      expect(result.theme).toBe(SSO_ERROR_THEMES.ERROR)
      expect(result.rawMessage).toBe('oops')
    })

    it('缺失 errorCode 时按关键字猜测 USER_NOT_FOUND', () => {
      const result = mapSsoError({ rawMessage: 'SSO 认证失败: 用户不存在' })
      expect(result.errorCode).toBe('USER_NOT_FOUND')
    })

    it('缺失 errorCode 时按关键字猜测 PENDING_APPROVAL', () => {
      const result = mapSsoError({ rawMessage: '账号正在审批中' })
      expect(result.errorCode).toBe('PENDING_APPROVAL')
    })

    it('缺失 errorCode 时按关键字猜测 CODE_EXPIRED', () => {
      const result = mapSsoError({ rawMessage: '授权码已过期' })
      expect(result.errorCode).toBe('CODE_EXPIRED')
    })

    it('缺失 errorCode 时按关键字猜测 AUTH_FAILED', () => {
      const result = mapSsoError({ rawMessage: '票据校验失败' })
      expect(result.errorCode).toBe('AUTH_FAILED')
    })

    it('完全没有输入时返回 UNKNOWN', () => {
      const result = mapSsoError()
      expect(result.errorCode).toBe('UNKNOWN')
      expect(result.contactPhone).toBe(DEFAULT_CONTACT.contactPhone)
      expect(result.rawMessage).toBe('')
    })

    it('始终返回联系方式，无论错误类型如何', () => {
      const cases = [
        'USER_NOT_FOUND',
        'PENDING_APPROVAL',
        'AUTH_FAILED',
        'CODE_EXPIRED',
        'SSO_DISABLED',
        'MISSING_PARAM',
        'SYSTEM_ERROR',
        'UNKNOWN'
      ]

      cases.forEach((code) => {
        const result = mapSsoError({ errorCode: code })
        expect(result.contactPhone).toBe(DEFAULT_CONTACT.contactPhone)
        expect(result.contactDepartment).toBe(DEFAULT_CONTACT.contactDepartment)
        expect(result.mainMessage).toBeTruthy()
      })
    })

    it('errorCode 优先级高于 rawMessage 关键字匹配', () => {
      // rawMessage 含"过期"关键字（指向 CODE_EXPIRED），但 errorCode 是 AUTH_FAILED
      const result = mapSsoError({
        errorCode: 'AUTH_FAILED',
        rawMessage: '票据已过期'
      })
      expect(result.errorCode).toBe('AUTH_FAILED')
    })
  })
})
