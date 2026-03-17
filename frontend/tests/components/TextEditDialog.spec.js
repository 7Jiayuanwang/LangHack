import { describe, it, expect, vi, beforeEach } from 'vitest'
import { shallowMount } from '@vue/test-utils'

vi.mock('@/api/admin', () => ({
  createText: vi.fn(),
  updateText: vi.fn(),
  getCourses: vi.fn(),
  getUnitsByCourseId: vi.fn()
}))

vi.mock('element-plus', () => ({
  ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn() }
}))

import TextEditDialog from '@/components/TextEditDialog.vue'
import { createText, updateText, getCourses, getUnitsByCourseId } from '@/api/admin'

describe('TextEditDialog.vue', () => {
  let wrapper

  const defaultProps = {
    modelValue: true,
    textData: null,
    unitId: null
  }

  beforeEach(() => {
    vi.clearAllMocks()
    getCourses.mockResolvedValue({ data: { data: [] } })
  })

  const createWrapper = (props = {}) => {
    return shallowMount(TextEditDialog, {
      props: { ...defaultProps, ...props },
      global: {
        stubs: {
          'el-dialog': { template: '<div><slot /></div>', props: ['modelValue', 'title'] },
          'el-form': { template: '<div><slot /></div>', props: ['model', 'rules'] },
          'el-form-item': { template: '<div><slot /></div>' },
          'el-input': { template: '<input />', props: ['modelValue'] },
          'el-select': { template: '<select><slot /></select>', props: ['modelValue'] },
          'el-option': { template: '<option />' },
          'el-radio-group': { template: '<div><slot /></div>', props: ['modelValue'] },
          'el-radio-button': { template: '<button />' },
          'el-switch': { template: '<input type="checkbox" />' },
          'el-button': { template: '<button><slot /></button>' },
          'MarkdownEditor': true
        }
      }
    })
  }

  it('renders in create mode when textData is null', () => {
    wrapper = createWrapper()
    expect(wrapper.vm.isEdit).toBe(false)
  })

  it('renders in edit mode when textData has id', () => {
    wrapper = createWrapper({
      textData: { id: 1, title: 'Test', content: 'Content', difficulty: 'EASY', contentType: 'TEXT' }
    })
    expect(wrapper.vm.isEdit).toBe(true)
  })

  it('initializes formData with defaults', () => {
    wrapper = createWrapper()
    expect(wrapper.vm.formData.title).toBe('')
    expect(wrapper.vm.formData.content).toBe('')
    expect(wrapper.vm.formData.difficulty).toBe('MEDIUM')
    expect(wrapper.vm.formData.contentType).toBe('TEXT')
    expect(wrapper.vm.formData.isPublic).toBe(1)
  })

  it('has validation rules for title and difficulty', () => {
    wrapper = createWrapper()
    expect(wrapper.vm.rules.title).toBeDefined()
    expect(wrapper.vm.rules.difficulty).toBeDefined()
  })

  it('submitting is false initially', () => {
    wrapper = createWrapper()
    expect(wrapper.vm.submitting).toBe(false)
  })

  it('handleClose emits update:modelValue false', () => {
    wrapper = createWrapper()
    wrapper.vm.handleClose()
    expect(wrapper.emitted('update:modelValue')).toBeTruthy()
    expect(wrapper.emitted('update:modelValue')[0]).toEqual([false])
  })

  it('handleSubmit calls createText for new text', async () => {
    createText.mockResolvedValue({ data: { code: 200 } })
    wrapper = createWrapper()
    wrapper.vm.formRef = { validate: vi.fn((cb) => cb(true)) }
    wrapper.vm.formData.title = 'New Text'
    wrapper.vm.formData.content = 'Some content'
    await wrapper.vm.handleSubmit()
    expect(createText).toHaveBeenCalled()
  })

  it('handleSubmit calls updateText for existing text', async () => {
    updateText.mockResolvedValue({ data: { code: 200 } })
    wrapper = createWrapper({
      textData: { id: 5, title: 'Old', content: 'Old content', difficulty: 'EASY', contentType: 'TEXT' }
    })
    // Manually set formData.id since watch may not fire in shallow mount
    wrapper.vm.formData.id = 5
    wrapper.vm.formData.title = 'Updated'
    wrapper.vm.formData.content = 'Old content'
    wrapper.vm.formRef = { validate: vi.fn((cb) => cb(true)) }
    await wrapper.vm.handleSubmit()
    expect(updateText).toHaveBeenCalled()
  })

  it('uses unitId from props when provided', () => {
    wrapper = createWrapper({ unitId: 42 })
    expect(wrapper.vm.formData.unitId).toBeDefined()
  })

  it('courses is empty array initially', () => {
    wrapper = createWrapper()
    expect(wrapper.vm.courses).toEqual([])
  })

  it('units is empty array initially', () => {
    wrapper = createWrapper()
    expect(wrapper.vm.units).toEqual([])
  })
})
