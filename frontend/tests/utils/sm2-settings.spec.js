import { describe, it, expect, beforeEach } from 'vitest'
import {
 getSettings,
 saveSettings,
 resetSettings,
 buildSM2Options,
 buildOverloadOptions,
 DEFAULT_SETTINGS
} from '@/utils/sm2-settings'

describe('SM-2 用户设置', () => {
 beforeEach(() => {
 localStorage.clear()
 })

 describe('getSettings', () => {
 it('should return defaults when no settings exist', () => {
 const s = getSettings()
 expect(s).toEqual(DEFAULT_SETTINGS)
 expect(s.hardWordMode).toBe(false)
 expect(s.overloadProtection).toBe(true)
 expect(s.maxDailyReview).toBe(50)
 })

 it('should merge saved settings with defaults', () => {
 localStorage.setItem('sm2_user_settings_v1', JSON.stringify({
 hardWordMode: true
 }))
 const s = getSettings()
 expect(s.hardWordMode).toBe(true)
 expect(s.overloadProtection).toBe(true) // 默认值补齐
 })

 it('should handle corrupted JSON gracefully', () => {
 localStorage.setItem('sm2_user_settings_v1', '{invalid json')
 const s = getSettings()
 expect(s).toEqual(DEFAULT_SETTINGS)
 })
 })

 describe('saveSettings', () => {
 it('should persist partial updates', () => {
 saveSettings({ hardWordMode: true })
 const s = getSettings()
 expect(s.hardWordMode).toBe(true)
 expect(s.maxDailyReview).toBe(50) // 保持原值
 })

 it('should return merged settings', () => {
 const result = saveSettings({ hardWordMode: true, maxDailyReview: 30 })
 expect(result.hardWordMode).toBe(true)
 expect(result.maxDailyReview).toBe(30)
 })
 })

 describe('resetSettings', () => {
 it('should clear localStorage and return defaults', () => {
 saveSettings({ hardWordMode: true })
 const result = resetSettings()
 expect(result).toEqual(DEFAULT_SETTINGS)
 expect(localStorage.getItem('sm2_user_settings_v1')).toBe(null)
 })
 })

 describe('buildSM2Options', () => {
 it('should map hardWordMode to sm2 options', () => {
 const s = { hardWordMode: true }
 expect(buildSM2Options(s)).toEqual({ hardWordMode: true })
 })

 it('should read current settings by default', () => {
 saveSettings({ hardWordMode: true })
 expect(buildSM2Options()).toEqual({ hardWordMode: true })
 })
 })

 describe('buildOverloadOptions', () => {
 it('should map all overload fields', () => {
 const s = {
 overloadProtection: true,
 maxDailyReview: 30,
 emergencyThreshold: 50,
 deferDays: 2
 }
 const opts = buildOverloadOptions(s)
 expect(opts.enabled).toBe(true)
 expect(opts.maxDailyReview).toBe(30)
 expect(opts.emergencyThreshold).toBe(50)
 expect(opts.deferDays).toBe(2)
 })
 })
})
