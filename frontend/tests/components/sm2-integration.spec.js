import { describe, it, expect, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import SM2SettingsDrawer from '@/components/vocabulary/SM2SettingsDrawer.vue'
import SM2AnalyticsDashboard from '@/components/vocabulary/SM2AnalyticsDashboard.vue'
import SM2ComparisonPanel from '@/components/vocabulary/SM2ComparisonPanel.vue'

// Mock element-plus icons - explicit list (must be inside factory due to hoisting)
vi.mock('@element-plus/icons-vue', () => {
 const MockIcon = { name: 'MockIcon', template: '<i class="mock-icon"></i>' }
 return {
 Collection: MockIcon,
 Aim: MockIcon,
 TrendCharts: MockIcon,
 AlarmClock: MockIcon,
 WarningFilled: MockIcon,
 CircleClose: MockIcon,
 SuccessFilled: MockIcon,
 InfoFilled: MockIcon,
 Download: MockIcon,
 Refresh: MockIcon,
 Loading: MockIcon,
 DataAnalysis: MockIcon,
 VideoPlay: MockIcon,
 Trophy: MockIcon,
 Sunny: MockIcon,
 Lightning: MockIcon,
 SetUp: MockIcon
 }
})

// Mock element-plus
vi.mock('element-plus', () => ({
 ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn(), info: vi.fn() }
}))

describe('SM2 设置抽屉 (Smoke)', () => {
 it('should render without crashing', () => {
 const wrapper = mount(SM2SettingsDrawer, {
 props: { modelValue: true }
 })
 expect(wrapper.exists()).toBe(true)
 expect(wrapper.find('.settings-drawer').exists()).toBe(true)
 })

 it('should show mode banner', () => {
 const wrapper = mount(SM2SettingsDrawer, {
 props: { modelValue: true }
 })
 expect(wrapper.find('.mode-banner').exists()).toBe(true)
 })

 it('should have 3 main setting groups', () => {
 const wrapper = mount(SM2SettingsDrawer, {
 props: { modelValue: true }
 })
 const groups = wrapper.findAll('.setting-group')
 expect(groups.length).toBe(3)
 })
})

describe('SM2 数据监控面板 (Smoke)', () => {
 const mockWords = [
 { word: 'test1', easinessFactor: 2.5, interval: 5, repetition: 2, nextReviewTime: new Date().toISOString() },
 { word: 'test2', easinessFactor: 1.5, interval: 1, repetition: 0, nextReviewTime: new Date(Date.now() - 86400000).toISOString() }
 ]

 it('should render without crashing', () => {
 const wrapper = mount(SM2AnalyticsDashboard, {
 props: { words: mockWords }
 })
 expect(wrapper.exists()).toBe(true)
 })

 it('should display overview cards', async () => {
 const wrapper = mount(SM2AnalyticsDashboard, {
 props: { words: mockWords }
 })
 // Wait for report to be generated in onMounted
 await new Promise(resolve => setTimeout(resolve, 50))
 expect(wrapper.findAll('.overview-card').length).toBe(4)
 })
})

describe('SM2 模式对比面板 (Smoke)', () => {
 it('should render without words input', () => {
 const wrapper = mount(SM2ComparisonPanel, {
 props: { words: [] }
 })
 expect(wrapper.exists()).toBe(true)
 expect(wrapper.find('.ab-intro').exists()).toBe(true)
 })

 it('should render with sample words', () => {
 const words = [
 { word: 'a', masteryLevel: 'LEARNING', easinessFactor: 2.5, reviewCount: 1 }
 ]
 const wrapper = mount(SM2ComparisonPanel, {
 props: { words }
 })
 expect(wrapper.exists()).toBe(true)
 })
})
