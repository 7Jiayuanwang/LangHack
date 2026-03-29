import { describe, it, expect, afterEach, vi } from 'vitest'
import { environment } from '@/utils/environment'

describe('Environment Utilities', () => {
  afterEach(() => {
    vi.restoreAllMocks()
    if (window.Capacitor) {
      delete window.Capacitor
    }
  })

  describe('isMobile', () => {
    it('returns true for iPhone user agent', () => {
      vi.stubGlobal('navigator', {
        userAgent: 'Mozilla/5.0 (iPhone; CPU iPhone OS 15_0 like Mac OS X)',
      })

      expect(environment.isMobile()).toBe(true)
    })

    it('returns true for Android user agent', () => {
      vi.stubGlobal('navigator', {
        userAgent: 'Mozilla/5.0 (Linux; Android 12; Pixel 5)',
      })

      expect(environment.isMobile()).toBe(true)
    })

    it('returns false for desktop user agent', () => {
      vi.stubGlobal('navigator', {
        userAgent: 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36',
      })

      expect(environment.isMobile()).toBe(false)
    })
  })

  describe('isCapacitor', () => {
    it('returns true when window.Capacitor exists', () => {
      window.Capacitor = { isNativePlatform: () => true }

      expect(environment.isCapacitor()).toBe(true)
    })

    it('returns false when window.Capacitor is not defined', () => {
      delete window.Capacitor

      expect(environment.isCapacitor()).toBe(false)
    })
  })

  describe('normalizeBaseUrl', () => {
    it('trims whitespace and trailing slash', () => {
      expect(environment.normalizeBaseUrl(' https://api.example.com/ ')).toBe(
        'https://api.example.com',
      )
    })

    it('returns empty string for invalid input', () => {
      expect(environment.normalizeBaseUrl(null)).toBe('')
      expect(environment.normalizeBaseUrl('   ')).toBe('')
    })
  })

  describe('resolveApiUrl', () => {
    it('keeps relative API path when no base is configured', () => {
      expect(environment.resolveApiUrl('/api/auth/login', '')).toBe('/api/auth/login')
    })

    it('prepends configured base for relative API path', () => {
      expect(
        environment.resolveApiUrl('/api/auth/login', 'https://api.example.com'),
      ).toBe('https://api.example.com/api/auth/login')
    })

    it('avoids duplicating /api when configured base already ends with /api', () => {
      expect(
        environment.resolveApiUrl('/api/auth/login', 'https://api.example.com/api'),
      ).toBe('https://api.example.com/api/auth/login')
    })

    it('rewrites same-origin absolute API URL to configured API origin', () => {
      expect(
        environment.resolveApiUrl(
          'http://localhost:3000/api/student/assignments',
          'https://api.example.com',
        ),
      ).toBe('https://api.example.com/api/student/assignments')
    })

    it('keeps non-API URL unchanged', () => {
      expect(
        environment.resolveApiUrl(
          'https://cdn.example.com/assets/logo.png',
          'https://api.example.com',
        ),
      ).toBe('https://cdn.example.com/assets/logo.png')
    })

    it('keeps third-party API URL unchanged', () => {
      expect(
        environment.resolveApiUrl(
          'https://thirdparty.example.com/api/student/assignments',
          'https://api.example.com',
        ),
      ).toBe('https://thirdparty.example.com/api/student/assignments')
    })
  })

  describe('isApiRequestUrl', () => {
    it('recognizes relative API path', () => {
      expect(environment.isApiRequestUrl('/api/auth/login', '')).toBe(true)
    })

    it('recognizes configured absolute API URL', () => {
      expect(
        environment.isApiRequestUrl(
          'https://api.example.com/api/auth/login',
          'https://api.example.com',
        ),
      ).toBe(true)
    })

    it('rejects non-API URL', () => {
      expect(
        environment.isApiRequestUrl('https://cdn.example.com/assets/logo.png', ''),
      ).toBe(false)
    })

    it('rejects unrelated third-party API URL', () => {
      expect(
        environment.isApiRequestUrl(
          'https://thirdparty.example.com/api/auth/login',
          'https://api.example.com',
        ),
      ).toBe(false)
    })
  })
})
