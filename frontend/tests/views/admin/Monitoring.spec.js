import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { shallowMount } from '@vue/test-utils'

// vi.hoisted ensures mocks are available when vi.mock factory runs (hoisted)
const mockMonitoringApi = vi.hoisted(() => ({
  getRealTimeMetrics: vi.fn(),
  getSystemMetrics: vi.fn(),
  getHourlyStats: vi.fn(),
  getDailyStats: vi.fn(),
  getTopPages: vi.fn(),
  getAiCallsRealtime: vi.fn(),
  getAiCallsDaily: vi.fn(),
  getAiCallsRecent: vi.fn()
}))

const mockGetToken = vi.hoisted(() => vi.fn())

vi.mock('@/api/monitoring', () => mockMonitoringApi)

vi.mock('@/utils/auth', () => ({
  getToken: mockGetToken
}))

vi.mock('vue-echarts', () => ({
  default: { template: '<div class="v-chart-stub" />', props: ['option', 'autoresize'] }
}))

vi.mock('echarts/core', () => ({
  use: vi.fn()
}))

vi.mock('echarts/renderers', () => ({
  CanvasRenderer: {}
}))

vi.mock('echarts/charts', () => ({
  LineChart: {},
  BarChart: {}
}))

vi.mock('echarts/components', () => ({
  TitleComponent: {},
  TooltipComponent: {},
  LegendComponent: {},
  GridComponent: {},
  DataZoomComponent: {}
}))

vi.mock('element-plus', () => ({
  ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn() }
}))

import Monitoring from '@/views/admin/Monitoring.vue'

// Mock EventSource
class MockEventSource {
  constructor(url) {
    this.url = url
    this.onmessage = null
    this.onerror = null
    this.readyState = 0
    MockEventSource.instances.push(this)
  }
  close() {
    this.readyState = 2
    this._closed = true
  }
}
MockEventSource.instances = []

describe('Monitoring.vue', () => {
  let wrapper

  beforeEach(() => {
    vi.clearAllMocks()
    MockEventSource.instances = []
    global.EventSource = MockEventSource

    // Default: all API calls resolve with empty success
    mockMonitoringApi.getRealTimeMetrics.mockResolvedValue({ code: 200, data: {} })
    mockMonitoringApi.getSystemMetrics.mockResolvedValue({ code: 200, data: {} })
    mockMonitoringApi.getHourlyStats.mockResolvedValue({ code: 200, data: [] })
    mockMonitoringApi.getDailyStats.mockResolvedValue({ code: 200, data: [] })
    mockMonitoringApi.getTopPages.mockResolvedValue({ code: 200, data: [] })
    mockMonitoringApi.getAiCallsRealtime.mockResolvedValue({ code: 200, data: {} })
    mockMonitoringApi.getAiCallsDaily.mockResolvedValue({ code: 200, data: [] })
    mockMonitoringApi.getAiCallsRecent.mockResolvedValue({ code: 200, data: [] })

    // Default: getToken returns a valid token so SSE connects
    mockGetToken.mockReturnValue('test-token')
  })

  afterEach(() => {
    if (wrapper) {
      wrapper.unmount()
      wrapper = null
    }
    vi.useRealTimers()
    delete global.EventSource
  })

  const mountComponent = () => {
    wrapper = shallowMount(Monitoring, {
      global: {
        stubs: {
          'v-chart': { template: '<div class="v-chart-stub" />', props: ['option', 'autoresize'] },
          'el-radio-button': { template: '<label><slot /></label>', props: ['label'] }
        }
      }
    })
    return wrapper
  }

  describe('rendering', () => {
    it('renders without errors', () => {
      const wrapper = mountComponent()
      expect(wrapper.find('.monitoring-dashboard').exists()).toBe(true)
    })

    it('renders stat cards section', () => {
      const wrapper = mountComponent()
      expect(wrapper.find('.stats-cards').exists()).toBe(true)
      const statCards = wrapper.findAll('.stat-card')
      expect(statCards.length).toBe(7)
    })

    it('renders health bars section', () => {
      const wrapper = mountComponent()
      expect(wrapper.find('.health-bars').exists()).toBe(true)
      const healthItems = wrapper.findAll('.health-item')
      expect(healthItems.length).toBe(5)
    })

    it('renders AI service cards section when data is loaded', async () => {
      mockMonitoringApi.getAiCallsRealtime.mockResolvedValue({
        code: 200,
        data: {
          HIAGENT: { totalCalls: 10, successRate: 95, avgResponseTimeMs: 200 },
          OPENROUTER: { totalCalls: 5, successRate: 100, avgResponseTimeMs: 150 }
        }
      })

      const wrapper = mountComponent()
      // Wait for onMounted + refreshAll to complete
      await vi.dynamicImportSettled()
      await wrapper.vm.$nextTick()
      // Allow all promises to flush
      await new Promise(resolve => setTimeout(resolve, 0))
      await wrapper.vm.$nextTick()

      expect(wrapper.find('.ai-service-cards').exists()).toBe(true)
      const aiCards = wrapper.findAll('.ai-card')
      expect(aiCards.length).toBe(2)
    })
  })

  describe('top pages normalization', () => {
    it('normalizes legacy path fields into uri rows', async () => {
      mockMonitoringApi.getTopPages.mockResolvedValue({
        code: 200,
        data: [
          { path: '/courses', visit_count: 12 },
          { uri: '/texts', count: 8 }
        ]
      })

      const wrapper = mountComponent()
      await new Promise(resolve => setTimeout(resolve, 0))
      await wrapper.vm.$nextTick()

      expect(wrapper.vm.topPages).toEqual([
        { path: '/courses', visit_count: 12, uri: '/courses', count: 12 },
        { uri: '/texts', count: 8 }
      ])
    })
  })

  describe('formatBytes', () => {
    it('returns "0 B" for 0', () => {
      const wrapper = mountComponent()
      expect(wrapper.vm.formatBytes(0)).toBe('0 B')
    })

    it('returns "0 B" for null/undefined', () => {
      const wrapper = mountComponent()
      expect(wrapper.vm.formatBytes(null)).toBe('0 B')
      expect(wrapper.vm.formatBytes(undefined)).toBe('0 B')
    })

    it('returns "0 B" for negative values', () => {
      const wrapper = mountComponent()
      expect(wrapper.vm.formatBytes(-100)).toBe('0 B')
    })

    it('formats bytes correctly', () => {
      const wrapper = mountComponent()
      expect(wrapper.vm.formatBytes(500)).toBe('500 B')
    })

    it('formats kilobytes correctly', () => {
      const wrapper = mountComponent()
      expect(wrapper.vm.formatBytes(1024)).toBe('1.0 KB')
    })

    it('formats megabytes correctly', () => {
      const wrapper = mountComponent()
      expect(wrapper.vm.formatBytes(1048576)).toBe('1.0 MB')
    })

    it('formats gigabytes correctly', () => {
      const wrapper = mountComponent()
      expect(wrapper.vm.formatBytes(1073741824)).toBe('1.0 GB')
    })

    it('formats fractional values correctly', () => {
      const wrapper = mountComponent()
      expect(wrapper.vm.formatBytes(1536)).toBe('1.5 KB')
    })
  })

  describe('formatUptime', () => {
    it('returns "-" for null', () => {
      const wrapper = mountComponent()
      expect(wrapper.vm.formatUptime(null)).toBe('-')
    })

    it('returns "-" for 0', () => {
      const wrapper = mountComponent()
      expect(wrapper.vm.formatUptime(0)).toBe('-')
    })

    it('returns "-" for undefined', () => {
      const wrapper = mountComponent()
      expect(wrapper.vm.formatUptime(undefined)).toBe('-')
    })

    it('formats 1 hour correctly', () => {
      const wrapper = mountComponent()
      expect(wrapper.vm.formatUptime(3600000)).toBe('1h 0m')
    })

    it('formats hours and minutes correctly', () => {
      const wrapper = mountComponent()
      // 2h 30m = 9,000,000ms
      expect(wrapper.vm.formatUptime(9000000)).toBe('2h 30m')
    })

    it('formats 25 hours as days and hours', () => {
      const wrapper = mountComponent()
      // 25h = 90,000,000ms
      expect(wrapper.vm.formatUptime(90000000)).toBe('1d 1h')
    })

    it('formats large durations correctly', () => {
      const wrapper = mountComponent()
      // 48h = 172,800,000ms
      expect(wrapper.vm.formatUptime(172800000)).toBe('2d 0h')
    })

    it('formats minutes only correctly', () => {
      const wrapper = mountComponent()
      // 30 minutes = 1,800,000ms
      expect(wrapper.vm.formatUptime(1800000)).toBe('0h 30m')
    })
  })

  describe('formatServiceName', () => {
    it('maps HIAGENT to HiAgent', () => {
      const wrapper = mountComponent()
      expect(wrapper.vm.formatServiceName('HIAGENT')).toBe('HiAgent')
    })

    it('maps OPENROUTER to OpenRouter', () => {
      const wrapper = mountComponent()
      expect(wrapper.vm.formatServiceName('OPENROUTER')).toBe('OpenRouter')
    })

    it('maps TENCENT_TRANSLATE to Chinese name', () => {
      const wrapper = mountComponent()
      expect(wrapper.vm.formatServiceName('TENCENT_TRANSLATE')).toBe('腾讯翻译')
    })

    it('maps TENCENT_ASR to Chinese name', () => {
      const wrapper = mountComponent()
      expect(wrapper.vm.formatServiceName('TENCENT_ASR')).toBe('腾讯ASR')
    })

    it('maps BILIBILI to Bilibili', () => {
      const wrapper = mountComponent()
      expect(wrapper.vm.formatServiceName('BILIBILI')).toBe('Bilibili')
    })

    it('returns unknown names as-is', () => {
      const wrapper = mountComponent()
      expect(wrapper.vm.formatServiceName('UNKNOWN_SERVICE')).toBe('UNKNOWN_SERVICE')
    })
  })

  describe('getProgressColor', () => {
    it('returns red for >= 90', () => {
      const wrapper = mountComponent()
      expect(wrapper.vm.getProgressColor(90)).toBe('#F56C6C')
      expect(wrapper.vm.getProgressColor(95)).toBe('#F56C6C')
      expect(wrapper.vm.getProgressColor(100)).toBe('#F56C6C')
    })

    it('returns yellow/orange for >= 70 and < 90', () => {
      const wrapper = mountComponent()
      expect(wrapper.vm.getProgressColor(70)).toBe('#E6A23C')
      expect(wrapper.vm.getProgressColor(85)).toBe('#E6A23C')
      expect(wrapper.vm.getProgressColor(89)).toBe('#E6A23C')
    })

    it('returns green for < 70', () => {
      const wrapper = mountComponent()
      expect(wrapper.vm.getProgressColor(0)).toBe('#67C23A')
      expect(wrapper.vm.getProgressColor(50)).toBe('#67C23A')
      expect(wrapper.vm.getProgressColor(69)).toBe('#67C23A')
    })
  })

  describe('getServiceStatusClass', () => {
    it('returns status-green for null rate', () => {
      const wrapper = mountComponent()
      expect(wrapper.vm.getServiceStatusClass(null)).toBe('status-green')
    })

    it('returns status-green for rate >= 90', () => {
      const wrapper = mountComponent()
      expect(wrapper.vm.getServiceStatusClass(90)).toBe('status-green')
      expect(wrapper.vm.getServiceStatusClass(100)).toBe('status-green')
    })

    it('returns status-yellow for rate >= 70 and < 90', () => {
      const wrapper = mountComponent()
      expect(wrapper.vm.getServiceStatusClass(70)).toBe('status-yellow')
      expect(wrapper.vm.getServiceStatusClass(85)).toBe('status-yellow')
      expect(wrapper.vm.getServiceStatusClass(89)).toBe('status-yellow')
    })

    it('returns status-red for rate < 70', () => {
      const wrapper = mountComponent()
      expect(wrapper.vm.getServiceStatusClass(0)).toBe('status-red')
      expect(wrapper.vm.getServiceStatusClass(50)).toBe('status-red')
      expect(wrapper.vm.getServiceStatusClass(69)).toBe('status-red')
    })
  })

  describe('SSE EventSource', () => {
    it('creates EventSource on mount when token is available', () => {
      mockGetToken.mockReturnValue('my-token')
      mountComponent()
      expect(MockEventSource.instances.length).toBe(1)
      expect(MockEventSource.instances[0].url).toContain('/api/ops/monitoring/realtime/stream')
      expect(MockEventSource.instances[0].url).toContain('token=my-token')
    })

    it('does not create EventSource when token is empty', () => {
      mockGetToken.mockReturnValue('')
      mountComponent()
      expect(MockEventSource.instances.length).toBe(0)
    })

    it('does not create EventSource when token is falsy', () => {
      mockGetToken.mockReturnValue(null)
      mountComponent()
      expect(MockEventSource.instances.length).toBe(0)
    })

    it('closes EventSource on unmount', () => {
      mockGetToken.mockReturnValue('test-token')
      const wrapper = mountComponent()

      const es = MockEventSource.instances[0]
      expect(es._closed).toBeUndefined()

      wrapper.unmount()

      expect(es._closed).toBe(true)
    })

    it('does not reconnect after component unmounts', async () => {
      vi.useFakeTimers()
      mockGetToken.mockReturnValue('test-token')
      mountComponent()

      const es = MockEventSource.instances[0]
      expect(es).toBeDefined()

      es.onerror?.()
      wrapper.unmount()
      wrapper = null

      vi.advanceTimersByTime(5000)
      await Promise.resolve()

      expect(MockEventSource.instances.length).toBe(1)
    })
  })

  describe('lifecycle', () => {
    it('calls refreshAll on mount which loads all data', async () => {
      mountComponent()

      // Wait for all async operations to complete
      await new Promise(resolve => setTimeout(resolve, 0))

      // refreshAll should trigger all these API calls
      expect(mockMonitoringApi.getHourlyStats).toHaveBeenCalled()
      expect(mockMonitoringApi.getTopPages).toHaveBeenCalled()
      expect(mockMonitoringApi.getAiCallsRealtime).toHaveBeenCalled()
      expect(mockMonitoringApi.getAiCallsDaily).toHaveBeenCalled()
      expect(mockMonitoringApi.getAiCallsRecent).toHaveBeenCalled()
    })
  })
})
