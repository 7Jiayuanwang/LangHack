import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import FeatureToggles from '@/views/ops/FeatureToggles.vue'
import { createPinia, setActivePinia } from 'pinia'
import ElementPlus from 'element-plus'

// Mock API
vi.mock('@/api/systemConfig', () => ({
  getSystemConfig: vi.fn(() => Promise.resolve({ code: 200, data: [] })),
  updateSystemConfig: vi.fn(() => Promise.resolve({ code: 200 })),
  listAllTags: vi.fn(() => Promise.resolve({ code: 200, data: ['test-tag', 'beta-users'] })),
  getUsersByTag: vi.fn(() => Promise.resolve({ code: 200, data: [] })),
  deleteTag: vi.fn(() => Promise.resolve({ code: 200 })),
  getUserTags: vi.fn(() => Promise.resolve({ code: 200, data: [] })),
  setUserTags: vi.fn(() => Promise.resolve({ code: 200 })),
  listFeatureOverrides: vi.fn(() => Promise.resolve({ code: 200, data: [] })),
  upsertFeatureOverride: vi.fn(() => Promise.resolve({ code: 200 })),
  deleteFeatureOverride: vi.fn(() => Promise.resolve({ code: 200 })),
  searchUsers: vi.fn(() => Promise.resolve({ code: 200, data: [] }))
}))

// Mock composable
vi.mock('@/composables/useFeatureToggles', () => ({
  useFeatureToggles: () => ({
    load: vi.fn(() => Promise.resolve())
  }),
  invalidateFeatureToggleCache: vi.fn()
}))

describe('FeatureToggles.vue', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('renders correctly', () => {
    const wrapper = mount(FeatureToggles, {
      global: {
        plugins: [ElementPlus]
      }
    })
    expect(wrapper.find('.feature-toggles-page').exists()).toBe(true)
    expect(wrapper.find('h2').text()).toBe('功能开关管理')
  })

  it('has three tabs: global, tags, users', async () => {
    const wrapper = mount(FeatureToggles, {
      global: {
        plugins: [ElementPlus]
      }
    })

    // Wait for component to mount
    await wrapper.vm.$nextTick()
    await new Promise(resolve => setTimeout(resolve, 100))

    const tabPane = wrapper.findAll('.el-tab-pane')
    expect(tabPane.length).toBe(3)
  })

  it('shows add users button in tag members panel', async () => {
    const wrapper = mount(FeatureToggles, {
      global: {
        plugins: [ElementPlus]
      }
    })

    // Switch to tags tab
    wrapper.vm.activeTab = 'tags'
    await wrapper.vm.$nextTick()

    // Select a tag
    await wrapper.vm.selectTag('test-tag')
    await wrapper.vm.$nextTick()

    // Check if add users button exists
    const addButton = wrapper.find('.members-actions button')
    expect(addButton.exists()).toBe(true)
    expect(addButton.text()).toContain('添加用户')
  })

  it('opens add users dialog when button clicked', async () => {
    const wrapper = mount(FeatureToggles, {
      global: {
        plugins: [ElementPlus]
      }
    })

    // Set selected tag
    wrapper.vm.selectedTag = 'test-tag'
    await wrapper.vm.$nextTick()

    // Call openAddUsersDialog
    wrapper.vm.openAddUsersDialog()
    await wrapper.vm.$nextTick()

    expect(wrapper.vm.addUsersDialogVisible).toBe(true)
  })

  it('warns if no tag selected when opening add users dialog', async () => {
    // Mock ElMessage before mounting
    const mockWarning = vi.fn()
    vi.stubGlobal('ElMessage', { warning: mockWarning, success: vi.fn(), error: vi.fn() })

    const wrapper = mount(FeatureToggles, {
      global: {
        plugins: [ElementPlus],
        stubs: {
          ElMessage: true
        }
      }
    })

    // No tag selected
    wrapper.vm.selectedTag = null
    wrapper.vm.openAddUsersDialog()
    await wrapper.vm.$nextTick()

    expect(mockWarning).toHaveBeenCalledWith('请先选择一个标签')
    expect(wrapper.vm.addUsersDialogVisible).toBe(false)

    vi.unstubAllGlobals()
  })
})
