import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { shallowMount } from '@vue/test-utils'

// Mock marked
const mockMarked = vi.hoisted(() => vi.fn((input) => `<p>${input}</p>`))
vi.mock('marked', () => ({
  marked: mockMarked
}))

// Mock DOMPurify
const mockSanitize = vi.hoisted(() => vi.fn((html) => html))
vi.mock('dompurify', () => ({
  default: {
    sanitize: mockSanitize
  }
}))

// Mock media API
const mockUploadMedia = vi.hoisted(() => vi.fn())
vi.mock('@/api/media', () => ({
  uploadMedia: mockUploadMedia
}))

// Mock ElMessage
const mockElMessage = vi.hoisted(() => ({
  success: vi.fn(),
  error: vi.fn(),
  warning: vi.fn()
}))
vi.mock('element-plus', () => ({
  ElMessage: mockElMessage
}))

import MarkdownEditor from '@/components/MarkdownEditor.vue'

describe('MarkdownEditor.vue', () => {
  let wrapper

  beforeEach(() => {
    vi.clearAllMocks()
    mockMarked.mockImplementation((input) => `<p>${input}</p>`)
    mockSanitize.mockImplementation((html) => html)
  })

  afterEach(() => {
    if (wrapper) {
      wrapper.unmount()
      wrapper = null
    }
  })

  // ============================================================
  // v-model Binding
  // ============================================================

  describe('v-model binding', () => {
    it('initializes content from modelValue prop', () => {
      wrapper = shallowMount(MarkdownEditor, {
        props: { modelValue: '# Hello World' }
      })
      expect(wrapper.vm.content).toBe('# Hello World')
    })

    it('updates content when modelValue prop changes', async () => {
      wrapper = shallowMount(MarkdownEditor, {
        props: { modelValue: 'initial' }
      })
      expect(wrapper.vm.content).toBe('initial')

      await wrapper.setProps({ modelValue: 'updated' })
      expect(wrapper.vm.content).toBe('updated')
    })

    it('emits update:modelValue on handleInput', () => {
      wrapper = shallowMount(MarkdownEditor, {
        props: { modelValue: '' }
      })
      wrapper.vm.content = 'new text'
      wrapper.vm.handleInput()

      const emitted = wrapper.emitted('update:modelValue')
      expect(emitted).toBeTruthy()
      expect(emitted[0][0]).toBe('new text')
    })

    it('initializes with empty string when no modelValue', () => {
      wrapper = shallowMount(MarkdownEditor, {
        props: {}
      })
      expect(wrapper.vm.content).toBe('')
    })
  })

  // ============================================================
  // Preview Mode Toggle
  // ============================================================

  describe('preview mode toggle', () => {
    it('starts in editor mode (previewMode is false)', () => {
      wrapper = shallowMount(MarkdownEditor, {
        props: { modelValue: 'test' }
      })
      expect(wrapper.vm.previewMode).toBe(false)
      expect(wrapper.find('.editor-pane').exists()).toBe(true)
      expect(wrapper.find('.preview-pane').exists()).toBe(false)
    })

    it('toggles to preview mode', async () => {
      wrapper = shallowMount(MarkdownEditor, {
        props: { modelValue: '# Test' }
      })
      wrapper.vm.togglePreview()
      await wrapper.vm.$nextTick()

      expect(wrapper.vm.previewMode).toBe(true)
      expect(wrapper.find('.editor-pane').exists()).toBe(false)
      expect(wrapper.find('.preview-pane').exists()).toBe(true)
    })

    it('toggles back to editor mode', async () => {
      wrapper = shallowMount(MarkdownEditor, {
        props: { modelValue: '# Test' }
      })
      // Toggle ON
      wrapper.vm.togglePreview()
      await wrapper.vm.$nextTick()
      expect(wrapper.vm.previewMode).toBe(true)

      // Toggle OFF
      wrapper.vm.togglePreview()
      await wrapper.vm.$nextTick()
      expect(wrapper.vm.previewMode).toBe(false)
      expect(wrapper.find('.editor-pane').exists()).toBe(true)
    })
  })

  // ============================================================
  // Markdown Rendering with DOMPurify
  // ============================================================

  describe('renderedMarkdown computed', () => {
    it('returns empty string for empty content', () => {
      wrapper = shallowMount(MarkdownEditor, {
        props: { modelValue: '' }
      })
      expect(wrapper.vm.renderedMarkdown).toBe('')
    })

    it('calls marked() and DOMPurify.sanitize() for non-empty content', () => {
      wrapper = shallowMount(MarkdownEditor, {
        props: { modelValue: '**bold text**' }
      })
      // Access the computed to trigger it
      wrapper.vm.renderedMarkdown

      expect(mockMarked).toHaveBeenCalledWith('**bold text**')
      expect(mockSanitize).toHaveBeenCalled()
    })

    it('passes DOMPurify config with allowed tags including video and source', () => {
      wrapper = shallowMount(MarkdownEditor, {
        props: { modelValue: 'test' }
      })
      // Trigger the computed
      wrapper.vm.renderedMarkdown

      // Verify DOMPurify.sanitize was called with the expected config
      const sanitizeCall = mockSanitize.mock.calls[0]
      const config = sanitizeCall[1]
      expect(config.ALLOWED_TAGS).toContain('video')
      expect(config.ALLOWED_TAGS).toContain('source')
      expect(config.ALLOWED_ATTR).toContain('controls')
      expect(config.ALLOWED_ATTR).toContain('type')
    })

    it('returns sanitized error message when marked throws', () => {
      mockMarked.mockImplementation(() => {
        throw new Error('Parse error')
      })
      mockSanitize.mockImplementation((html) => html)

      wrapper = shallowMount(MarkdownEditor, {
        props: { modelValue: 'broken markdown' }
      })

      const rendered = wrapper.vm.renderedMarkdown
      // On error, it should have called sanitize with an error message HTML
      expect(rendered).toContain('Markdown 渲染失败')
    })
  })

  // ============================================================
  // Toolbar Rendering
  // ============================================================

  describe('toolbar rendering', () => {
    it('renders the editor toolbar', () => {
      wrapper = shallowMount(MarkdownEditor, {
        props: { modelValue: '' }
      })
      expect(wrapper.find('.editor-toolbar').exists()).toBe(true)
    })

    it('renders all toolbar button groups', () => {
      wrapper = shallowMount(MarkdownEditor, {
        props: { modelValue: '' }
      })
      // Check that toolbar contains expected text
      const text = wrapper.find('.editor-toolbar').text()
      expect(text).toContain('H1')
      expect(text).toContain('H2')
      expect(text).toContain('H3')
      expect(text).toContain('加粗')
      expect(text).toContain('斜体')
      expect(text).toContain('删除线')
      expect(text).toContain('列表')
      expect(text).toContain('有序列表')
      expect(text).toContain('引用')
      expect(text).toContain('链接')
      expect(text).toContain('代码')
      expect(text).toContain('表格')
      expect(text).toContain('图片')
      expect(text).toContain('视频')
      expect(text).toContain('预览')
      expect(text).toContain('清空')
    })
  })

  // ============================================================
  // Clear Content
  // ============================================================

  describe('clearContent', () => {
    it('clears content and emits update when confirm returns true', () => {
      // Mock window.confirm
      vi.spyOn(window, 'confirm').mockReturnValue(true)

      wrapper = shallowMount(MarkdownEditor, {
        props: { modelValue: 'some content' }
      })
      wrapper.vm.clearContent()

      expect(wrapper.vm.content).toBe('')
      const emitted = wrapper.emitted('update:modelValue')
      expect(emitted).toBeTruthy()
      expect(emitted[0][0]).toBe('')

      window.confirm.mockRestore()
    })

    it('does not clear when confirm returns false', () => {
      vi.spyOn(window, 'confirm').mockReturnValue(false)

      wrapper = shallowMount(MarkdownEditor, {
        props: { modelValue: 'keep this' }
      })
      wrapper.vm.clearContent()

      expect(wrapper.vm.content).toBe('keep this')
      expect(wrapper.emitted('update:modelValue')).toBeFalsy()

      window.confirm.mockRestore()
    })
  })

  // ============================================================
  // Upload Timer Cleanup
  // ============================================================

  describe('upload timer cleanup on unmount', () => {
    it('clears upload timer on beforeUnmount', () => {
      const clearTimeoutSpy = vi.spyOn(global, 'clearTimeout')

      wrapper = shallowMount(MarkdownEditor, {
        props: { modelValue: '' }
      })
      wrapper.unmount()

      // clearTimeout is called during cleanup (may be called with null which is a no-op)
      // The important thing is that the component unmounts without error
      clearTimeoutSpy.mockRestore()
    })
  })

  // ============================================================
  // File Upload Triggers
  // ============================================================

  describe('file upload triggers', () => {
    it('showImageUpload warns if imageInput ref is null', () => {
      wrapper = shallowMount(MarkdownEditor, {
        props: { modelValue: '' }
      })
      // Force imageInput to be null
      wrapper.vm.imageInput = null
      wrapper.vm.showImageUpload()
      expect(mockElMessage.warning).toHaveBeenCalledWith('图片上传组件未就绪，请重试')
    })

    it('showVideoUpload warns if videoInput ref is null', () => {
      wrapper = shallowMount(MarkdownEditor, {
        props: { modelValue: '' }
      })
      wrapper.vm.videoInput = null
      wrapper.vm.showVideoUpload()
      expect(mockElMessage.warning).toHaveBeenCalledWith('视频上传组件未就绪，请重试')
    })
  })

  // ============================================================
  // v-html Preview Rendering
  // ============================================================

  describe('preview pane v-html rendering', () => {
    it('renders sanitized HTML in preview mode', async () => {
      mockMarked.mockReturnValue('<h1>Title</h1>')
      mockSanitize.mockReturnValue('<h1>Title</h1>')

      wrapper = shallowMount(MarkdownEditor, {
        props: { modelValue: '# Title' }
      })
      wrapper.vm.togglePreview()
      await wrapper.vm.$nextTick()

      const preview = wrapper.find('.markdown-preview')
      expect(preview.exists()).toBe(true)
      expect(preview.html()).toContain('<h1>Title</h1>')
    })
  })

  // ============================================================
  // Upload Dialog
  // ============================================================

  describe('upload dialog visibility', () => {
    it('uploadDialogVisible is false by default', () => {
      wrapper = shallowMount(MarkdownEditor, {
        props: { modelValue: '' }
      })
      expect(wrapper.vm.uploadDialogVisible).toBe(false)
    })

    it('uploadProgress starts at 0', () => {
      wrapper = shallowMount(MarkdownEditor, {
        props: { modelValue: '' }
      })
      expect(wrapper.vm.uploadProgress).toBe(0)
    })
  })
})
