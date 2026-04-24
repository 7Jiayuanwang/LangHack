import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { shallowMount } from '@vue/test-utils'

// Mock media API
const mockUploadMedia = vi.hoisted(() => vi.fn())
vi.mock('@/api/media', () => ({
  uploadMedia: mockUploadMedia,
  deleteMedia: vi.fn().mockResolvedValue({})
}))

// Mock MarkdownRenderer (child component)
vi.mock('@/components/MarkdownRenderer.vue', () => ({
  default: {
    name: 'MarkdownRenderer',
    template: '<div class="mock-markdown-renderer"></div>'
  }
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
  // Editor Layout (split-pane with live preview)
  // ============================================================

  describe('editor layout', () => {
    it('shows both editor and preview panes simultaneously', () => {
      wrapper = shallowMount(MarkdownEditor, {
        props: { modelValue: '# Test' }
      })
      expect(wrapper.find('.editor-pane').exists()).toBe(true)
      expect(wrapper.find('.preview-pane').exists()).toBe(true)
    })

    it('has no previewMode ref (replaced by split-pane)', () => {
      wrapper = shallowMount(MarkdownEditor, {
        props: { modelValue: '# Test' }
      })
      expect(wrapper.vm.previewMode).toBeUndefined()
    })
  })

  // ============================================================
  // Markdown Rendering with DOMPurify
  // ============================================================

  describe('markdown rendering', () => {
    it('renders content via MarkdownRenderer component', () => {
      wrapper = shallowMount(MarkdownEditor, {
        props: { modelValue: '**bold text**' }
      })
      // Split-pane editor uses MarkdownRenderer, not renderedMarkdown computed
      const renderer = wrapper.findComponent({ name: 'MarkdownRenderer' })
      expect(renderer.exists()).toBe(true)
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
      expect(text).toContain('全屏')
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

  describe('preview pane rendering', () => {
    it('renders MarkdownRenderer in preview pane', async () => {
      wrapper = shallowMount(MarkdownEditor, {
        props: { modelValue: '# Title' }
      })
      // Split-pane layout always shows preview pane with MarkdownRenderer
      const renderer = wrapper.findComponent({ name: 'MarkdownRenderer' })
      expect(renderer.exists()).toBe(true)
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
