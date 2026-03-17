import { config } from '@vue/test-utils'
import { vi } from 'vitest'

// Global stub Element Plus components
config.global.stubs = {
  ElButton: { template: '<button><slot /></button>' },
  ElInput: { template: '<input />' },
  ElForm: { template: '<form><slot /></form>' },
  ElFormItem: { template: '<div><slot /></div>' },
  ElTable: { template: '<table><slot /></table>' },
  ElTableColumn: true,
  ElPagination: { template: '<div />' },
  ElDialog: { template: '<div v-if="modelValue"><slot /></div>', props: ['modelValue'] },
  ElSelect: { template: '<select><slot /></select>' },
  ElOption: { template: '<option />' },
  ElMessage: true,
  ElMessageBox: true,
  ElTooltip: { template: '<div><slot /></div>' },
  ElCard: { template: '<div><slot /></div>' },
  ElTag: { template: '<span><slot /></span>' },
  ElIcon: true,
  ElUpload: { template: '<div><slot /></div>' },
  ElResult: true,
  ElDivider: true,
  ElSwitch: true,
  ElRadioGroup: { template: '<div><slot /></div>' },
  ElRadio: { template: '<label><slot /></label>' },
  ElCheckbox: { template: '<label><slot /></label>' },
  ElDropdown: { template: '<div><slot /></div>' },
  ElDropdownMenu: { template: '<div><slot /></div>' },
  ElDropdownItem: { template: '<div><slot /></div>' },
  ElTabs: { template: '<div><slot /></div>' },
  ElTabPane: { template: '<div><slot /></div>' },
  ElProgress: { template: '<div />' },
  ElEmpty: { template: '<div />' },
  ElLoading: true,
}

// Stub all @element-plus/icons-vue
config.global.stubs['ArrowLeft'] = true
config.global.stubs['Star'] = true
config.global.stubs['StarFilled'] = true
config.global.stubs['Clock'] = true
config.global.stubs['Tickets'] = true
config.global.stubs['CircleCheck'] = true
config.global.stubs['Loading'] = true
config.global.stubs['VideoPlay'] = true
config.global.stubs['InfoFilled'] = true
config.global.stubs['View'] = true
config.global.stubs['CopyDocument'] = true
config.global.stubs['EditPen'] = true
config.global.stubs['Share'] = true
config.global.stubs['Delete'] = true
config.global.stubs['Plus'] = true
config.global.stubs['Search'] = true
config.global.stubs['Upload'] = true
config.global.stubs['Download'] = true
config.global.stubs['Refresh'] = true
config.global.stubs['Setting'] = true
config.global.stubs['User'] = true
config.global.stubs['MagicStick'] = true
config.global.stubs['Checked'] = true
config.global.stubs['ChatLineRound'] = true
config.global.stubs['SelectionMenu'] = true

// Mock window.matchMedia
Object.defineProperty(window, 'matchMedia', {
  writable: true,
  value: vi.fn().mockImplementation(query => ({
    matches: false,
    media: query,
    onchange: null,
    addListener: vi.fn(),
    removeListener: vi.fn(),
    addEventListener: vi.fn(),
    removeEventListener: vi.fn(),
    dispatchEvent: vi.fn(),
  })),
})

// Mock IntersectionObserver
global.IntersectionObserver = class IntersectionObserver {
  constructor() {}
  observe() { return null }
  unobserve() { return null }
  disconnect() { return null }
}

// Mock localStorage
const localStorageMock = (() => {
  let store = {}
  return {
    getItem: vi.fn(key => store[key] ?? null),
    setItem: vi.fn((key, value) => { store[key] = String(value) }),
    removeItem: vi.fn(key => { delete store[key] }),
    clear: vi.fn(() => { store = {} }),
    get length() { return Object.keys(store).length },
    key: vi.fn(i => Object.keys(store)[i] ?? null),
  }
})()
Object.defineProperty(window, 'localStorage', { value: localStorageMock })

// Mock URL.createObjectURL
global.URL.createObjectURL = vi.fn(() => 'blob:mock-url')
global.URL.revokeObjectURL = vi.fn()
