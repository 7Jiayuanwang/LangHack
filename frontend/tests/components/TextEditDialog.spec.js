import { describe, it, expect, vi, beforeEach } from 'vitest'
import { shallowMount } from '@vue/test-utils'

vi.mock('@/api/admin', () => ({
  createText: vi.fn(),
  updateText: vi.fn(),
  getCourses: vi.fn(),
  getUnitsByCourseId: vi.fn()
}))

const mockUploadMedia = vi.hoisted(() => vi.fn())
vi.mock('@/api/media', () => ({
  uploadMedia: mockUploadMedia
}))

vi.mock('element-plus', () => ({
  ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn() }
}))

import TextEditDialog from '@/components/TextEditDialog.vue'
import { createText, updateText, getCourses } from '@/api/admin'

describe('TextEditDialog.vue', () => {
  let wrapper

  const defaultProps = {
    modelValue: true,
    textData: null,
    unitId: null
  }

  beforeEach(() => {
    vi.clearAllMocks()
    mockUploadMedia.mockReset()
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

  it('submits the selected in-class article classification', async () => {
    createText.mockResolvedValue({ data: { code: 200 } })
    wrapper = createWrapper()
    wrapper.vm.formRef = { validate: vi.fn((cb) => cb(true)) }
    wrapper.vm.formData.title = 'In-class text'
    wrapper.vm.formData.content = 'Some content'
    wrapper.vm.formData.articleType = 'INNER'
    wrapper.vm.formData.bookVolume = '综合教程1'
    wrapper.vm.formData.contentSection = 'iExplore2'
    wrapper.vm.formData.unitNum = 3

    await wrapper.vm.handleSubmit()

    expect(createText).toHaveBeenCalledWith(expect.objectContaining({
      articleType: 'INNER',
      bookVolume: '综合教程1',
      contentSection: 'iExplore2',
      unitNum: 3
    }))
  })

  it('switches content choices for Understanding Contemporary China', async () => {
    wrapper = createWrapper()
    wrapper.vm.formData.bookVolume = '理解当代中国'
    await wrapper.vm.$nextTick()

    expect(wrapper.vm.contentSectionOptions).toEqual(['Understanding China', 'Exploring the world'])

    wrapper.vm.formData.bookVolume = '综合教程2'
    await wrapper.vm.$nextTick()
    expect(wrapper.vm.contentSectionOptions).toEqual(['iExplore1', 'iExplore2'])
  })

  it('restores an existing in-class classification when reopened for editing', async () => {
    wrapper = createWrapper({
      modelValue: false,
      textData: {
        id: 8,
        title: 'China',
        content: 'Content',
        contentType: 'TEXT',
        articleType: 'INNER',
        bookVolume: '理解当代中国',
        contentSection: 'Understanding China',
        unitNum: 2
      }
    })

    await wrapper.setProps({ modelValue: true })

    expect(wrapper.vm.formData.articleType).toBe('INNER')
    expect(wrapper.vm.formData.bookVolume).toBe('理解当代中国')
    expect(wrapper.vm.formData.contentSection).toBe('Understanding China')
    expect(wrapper.vm.formData.unitNum).toBe(2)
  })

  it('does not submit an in-class text without a content classification', async () => {
    wrapper = createWrapper()
    wrapper.vm.formRef = { validate: vi.fn((cb) => cb(true)) }
    wrapper.vm.formData.title = 'Incomplete text'
    wrapper.vm.formData.content = 'Some content'
    wrapper.vm.formData.articleType = 'INNER'
    wrapper.vm.formData.bookVolume = '综合教程1'
    wrapper.vm.formData.contentSection = ''

    await wrapper.vm.handleSubmit()

    expect(createText).not.toHaveBeenCalled()
  })

  it('submits all selected unit ids and allows an empty selection', async () => {
    createText.mockResolvedValue({ data: { code: 200 } })
    wrapper = createWrapper()
    wrapper.vm.formRef = { validate: vi.fn((cb) => cb(true)) }
    wrapper.vm.formData.title = 'Multi-unit text'
    wrapper.vm.formData.content = 'Some content'
    wrapper.vm.formData.unitIds = [3, 7]
    await wrapper.vm.handleSubmit()
    expect(createText).toHaveBeenCalledWith(expect.objectContaining({ unitIds: [3, 7], unitId: 3 }))

    createText.mockClear()
    wrapper.vm.formData.unitIds = []
    await wrapper.vm.handleSubmit()
    expect(createText).toHaveBeenCalledWith(expect.objectContaining({ unitIds: [], unitId: null }))
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

  it('uploads a pasted image as the article cover while open', async () => {
    mockUploadMedia.mockResolvedValue({ code: 200, data: { mediaId: 42 } })
    wrapper = createWrapper()
    const blob = new Blob(['image'], { type: 'image/png' })
    const event = {
      preventDefault: vi.fn(),
      clipboardData: {
        items: [{ kind: 'file', type: 'image/png', getAsFile: () => blob }]
      }
    }

    await wrapper.vm.handleClipboardImagePaste(event)

    expect(event.preventDefault).toHaveBeenCalledOnce()
    expect(mockUploadMedia).toHaveBeenCalledWith(expect.any(File), 'IMAGE')
    expect(wrapper.vm.formData.coverUrl).toBe('/api/media/42/download')
  })

  it('does not intercept a paste without an image', async () => {
    wrapper = createWrapper()
    const event = {
      preventDefault: vi.fn(),
      clipboardData: { items: [{ kind: 'string', type: 'text/plain' }] }
    }

    await wrapper.vm.handleClipboardImagePaste(event)

    expect(event.preventDefault).not.toHaveBeenCalled()
    expect(mockUploadMedia).not.toHaveBeenCalled()
  })

  it('rejects an oversized pasted image before uploading', async () => {
    wrapper = createWrapper()
    const oversized = new File([new Uint8Array(10 * 1024 * 1024 + 1)], 'large.png', { type: 'image/png' })
    const event = {
      preventDefault: vi.fn(),
      clipboardData: {
        items: [{ kind: 'file', type: 'image/png', getAsFile: () => oversized }]
      }
    }

    await wrapper.vm.handleClipboardImagePaste(event)

    expect(event.preventDefault).toHaveBeenCalledOnce()
    expect(mockUploadMedia).not.toHaveBeenCalled()
  })
})
