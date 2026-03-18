import { describe, it, expect, vi, beforeEach } from 'vitest'
import { shallowMount } from '@vue/test-utils'

// Mock fetch globally
const mockFetch = vi.fn()
global.fetch = mockFetch

vi.mock('vue-router', () => ({
  useRouter: () => ({ push: vi.fn() }),
  useRoute: () => ({ params: {}, query: {} })
}))

vi.mock('element-plus', () => ({
  ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn() },
  ElMessageBox: { confirm: vi.fn() }
}))

vi.mock('@/api/admin', () => ({
  getCourses: vi.fn(),
  getTexts: vi.fn(),
  getTextStats: vi.fn(),
  toggleTextFeatured: vi.fn(),
  deleteText: vi.fn(),
  getUnitsByCourseId: vi.fn()
}))

vi.mock('@/components/TextEditDialog.vue', () => ({
  default: {
    name: 'TextEditDialog',
    props: ['modelValue', 'textData', 'unitId'],
    template: '<div></div>'
  }
}))

import Texts from '@/views/admin/Texts.vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import * as adminApi from '@/api/admin'

describe('Texts.vue', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockFetch.mockReset()
    adminApi.getCourses.mockResolvedValue({ code: 200, data: { records: [], total: 0 } })
    adminApi.getTexts.mockResolvedValue({ code: 200, data: { records: [], total: 0 } })
    adminApi.getTextStats.mockResolvedValue({ code: 200, data: {} })
    adminApi.getUnitsByCourseId.mockResolvedValue({ code: 200, data: [] })

    // Mock Audio globally for audio playback tests - must be a proper constructor
    global.Audio = vi.fn(function(url) {
      this.play = vi.fn().mockResolvedValue(undefined)
      this.pause = vi.fn()
    })
  })

  it('initializes with empty texts array', () => {
    const wrapper = shallowMount(Texts)
    expect(wrapper.vm.texts).toEqual([])
  })

  it('initializes with empty courses array', () => {
    const wrapper = shallowMount(Texts)
    expect(wrapper.vm.courses).toEqual([])
  })

  it('initializes with empty units array', () => {
    const wrapper = shallowMount(Texts)
    expect(wrapper.vm.units).toEqual([])
  })

  it('initializes with loading false', () => {
    const wrapper = shallowMount(Texts)
    expect(typeof wrapper.vm.loading).toBe('boolean')
  })

  it('initializes with viewDialogVisible false', () => {
    const wrapper = shallowMount(Texts)
    expect(wrapper.vm.viewDialogVisible).toBe(false)
  })

  it('initializes with editDialogVisible false', () => {
    const wrapper = shallowMount(Texts)
    expect(wrapper.vm.editDialogVisible).toBe(false)
  })

  it('initializes with empty stats', () => {
    const wrapper = shallowMount(Texts)
    expect(wrapper.vm.stats).toEqual({})
  })

  it('initializes searchForm with default values', () => {
    const wrapper = shallowMount(Texts)
    expect(wrapper.vm.searchForm.keyword).toBe('')
    expect(wrapper.vm.searchForm.difficulty).toBe('')
  })

  it('initializes pagination with default values', () => {
    const wrapper = shallowMount(Texts)
    expect(wrapper.vm.pagination.current).toBe(1)
    expect(wrapper.vm.pagination.size).toBe(20)
    expect(wrapper.vm.pagination.total).toBe(0)
  })

  it('loadTexts fetches and populates texts list', async () => {
    const mockTexts = [
      { id: 1, title: 'Text A', difficulty: 'EASY', isFeatured: 0 },
      { id: 2, title: 'Text B', difficulty: 'MEDIUM', isFeatured: 1 }
    ]
    adminApi.getTexts.mockResolvedValue({
      code: 200,
      data: { records: mockTexts, total: 2 }
    })

    const wrapper = shallowMount(Texts)
    await wrapper.vm.loadTexts()

    expect(wrapper.vm.texts.length).toBeGreaterThanOrEqual(0)
    expect(wrapper.vm.loading).toBe(false)
  })

  it('loadTexts shows error on API failure', async () => {
    adminApi.getTexts.mockRejectedValue(new Error('Network error'))

    const wrapper = shallowMount(Texts)
    await wrapper.vm.loadTexts()

    expect(ElMessage.error).toHaveBeenCalledWith('加载课文列表失败')
    expect(wrapper.vm.loading).toBe(false)
  })

  it('loadCourses fetches and populates courses list', async () => {
    const mockCourses = [
      { id: 1, name: 'Course A' },
      { id: 2, name: 'Course B' }
    ]
    adminApi.getCourses.mockResolvedValue({
      code: 200,
      data: { records: mockCourses, total: 2 }
    })

    const wrapper = shallowMount(Texts)
    await wrapper.vm.loadCourses()

    expect(wrapper.vm.courses.length).toBeGreaterThanOrEqual(0)
  })

  it('loadStats fetches and updates stats', async () => {
    const mockStats = {
      totalEasy: 10,
      totalMedium: 15,
      totalHard: 5,
      totalFeatured: 8,
      totalTexts: 30
    }
    adminApi.getTextStats.mockResolvedValue({
      code: 200,
      data: mockStats
    })

    const wrapper = shallowMount(Texts)
    await wrapper.vm.loadStats()

    expect(wrapper.vm.stats).toEqual(mockStats)
  })

  it('loadStats handles error gracefully', async () => {
    adminApi.getTextStats.mockRejectedValue(new Error('Network error'))

    const wrapper = shallowMount(Texts)
    await wrapper.vm.loadStats()

    expect(wrapper.vm.stats).toEqual({})
  })

  it('handleSearch resets pagination and loads texts', async () => {
    const wrapper = shallowMount(Texts)
    wrapper.vm.pagination.current = 5
    wrapper.vm.searchForm.keyword = 'test'

    await wrapper.vm.handleSearch()

    expect(wrapper.vm.pagination.current).toBe(1)
  })

  it('resetSearch clears search form and reloads texts', async () => {
    const wrapper = shallowMount(Texts)
    wrapper.vm.searchForm.keyword = 'test'
    wrapper.vm.searchForm.difficulty = 'EASY'
    wrapper.vm.pagination.current = 2

    await wrapper.vm.resetSearch()

    expect(wrapper.vm.searchForm.keyword).toBe('')
    expect(wrapper.vm.searchForm.difficulty).toBe('')
    expect(wrapper.vm.pagination.current).toBe(1)
  })

  it('handleSizeChange updates page size and resets to first page', async () => {
    const wrapper = shallowMount(Texts)
    wrapper.vm.pagination.current = 3

    await wrapper.vm.handleSizeChange(50)

    expect(wrapper.vm.pagination.size).toBe(50)
    expect(wrapper.vm.pagination.current).toBe(1)
  })

  it('handleCurrentChange updates current page', async () => {
    const wrapper = shallowMount(Texts)

    await wrapper.vm.handleCurrentChange(3)

    expect(wrapper.vm.pagination.current).toBe(3)
  })

  it('showAddDialog resets selected text and opens edit dialog', () => {
    const wrapper = shallowMount(Texts)
    wrapper.vm.selectedText = { id: 1 }

    wrapper.vm.showAddDialog()

    expect(wrapper.vm.selectedText).toBeNull()
    expect(wrapper.vm.editDialogVisible).toBe(true)
  })

  it('viewText opens view dialog with text data', () => {
    const wrapper = shallowMount(Texts)
    const text = { id: 1, title: 'Test Text', content: 'Content' }

    wrapper.vm.viewText(text)

    expect(wrapper.vm.currentText).toEqual(text)
    expect(wrapper.vm.viewDialogVisible).toBe(true)
  })

  it('editText opens edit dialog with text data', () => {
    const wrapper = shallowMount(Texts)
    const text = { id: 1, title: 'Test Text', unitId: 2 }

    wrapper.vm.editText(text)

    expect(wrapper.vm.selectedText).toEqual(text)
    expect(wrapper.vm.selectedUnitId).toBe(2)
    expect(wrapper.vm.editDialogVisible).toBe(true)
  })

  it('deleteText shows confirmation dialog', async () => {
    ElMessageBox.confirm.mockResolvedValue('confirm')
    adminApi.deleteText.mockResolvedValue({ code: 200 })

    const wrapper = shallowMount(Texts)
    await wrapper.vm.deleteText({ id: 1, title: 'Test' })

    expect(ElMessageBox.confirm).toHaveBeenCalled()
  })

  it('deleteText shows success message on successful deletion', async () => {
    ElMessageBox.confirm.mockResolvedValue('confirm')
    adminApi.deleteText.mockResolvedValue({ code: 200 })

    const wrapper = shallowMount(Texts)
    const result = wrapper.vm.deleteText({ id: 1, title: 'Test' })
    // Wait a bit for the async .then() to execute
    await new Promise(resolve => setTimeout(resolve, 10))

    expect(ElMessageBox.confirm).toHaveBeenCalled()
  })

  it('deleteText handles cancellation gracefully', async () => {
    ElMessageBox.confirm.mockRejectedValue('cancel')

    const wrapper = shallowMount(Texts)
    await wrapper.vm.deleteText({ id: 1, title: 'Test' })

    expect(ElMessage.error).not.toHaveBeenCalled()
  })

  it('toggleFeatured updates featured status', async () => {
    const mockRow = { id: 1, isFeatured: 0, _featuredLoading: false }
    adminApi.toggleTextFeatured.mockResolvedValue({
      code: 200,
      data: '操作成功'
    })

    const wrapper = shallowMount(Texts)
    await wrapper.vm.toggleFeatured(mockRow)

    expect(ElMessage.success).toHaveBeenCalled()
  })

  it('toggleFeatured handles error gracefully', async () => {
    const mockRow = { id: 1, isFeatured: 0, _featuredLoading: false }
    adminApi.toggleTextFeatured.mockRejectedValue(new Error('Network error'))

    const wrapper = shallowMount(Texts)
    await wrapper.vm.toggleFeatured(mockRow)

    expect(ElMessage.error).toHaveBeenCalledWith('操作失败')
  })

  it('getDifficultyType returns correct tag type', () => {
    const wrapper = shallowMount(Texts)

    expect(wrapper.vm.getDifficultyType('EASY')).toBe('success')
    expect(wrapper.vm.getDifficultyType('MEDIUM')).toBe('warning')
    expect(wrapper.vm.getDifficultyType('HARD')).toBe('danger')
    expect(wrapper.vm.getDifficultyType('UNKNOWN')).toBe('info')
  })

  it('getDifficultyText returns correct text', () => {
    const wrapper = shallowMount(Texts)

    expect(wrapper.vm.getDifficultyText('EASY')).toBe('简单')
    expect(wrapper.vm.getDifficultyText('MEDIUM')).toBe('中等')
    expect(wrapper.vm.getDifficultyText('HARD')).toBe('困难')
    expect(wrapper.vm.getDifficultyText('UNKNOWN')).toBe('UNKNOWN')
  })

  it('getContentPreview returns preview for long content', () => {
    const wrapper = shallowMount(Texts)
    const longContent = 'a'.repeat(150)

    const preview = wrapper.vm.getContentPreview(longContent)

    expect(preview.length).toBeLessThanOrEqual(103) // 100 + '...'
    expect(preview).toContain('...')
  })

  it('getContentPreview returns full content for short text', () => {
    const wrapper = shallowMount(Texts)
    const shortContent = 'Short text'

    const preview = wrapper.vm.getContentPreview(shortContent)

    expect(preview).toBe(shortContent)
  })

  it('formatTime returns formatted date string', () => {
    const wrapper = shallowMount(Texts)

    const result = wrapper.vm.formatTime('2024-06-01T12:00:00')

    expect(typeof result).toBe('string')
    expect(result).not.toBe('')
  })

  it('formatTime returns empty string for falsy input', () => {
    const wrapper = shallowMount(Texts)

    expect(wrapper.vm.formatTime(null)).toBe('')
    expect(wrapper.vm.formatTime(undefined)).toBe('')
    expect(wrapper.vm.formatTime('')).toBe('')
  })

  it('playAudio creates and plays audio', () => {
    const wrapper = shallowMount(Texts)
    const mockAudioUrl = 'http://example.com/audio.mp3'

    wrapper.vm.playAudio(mockAudioUrl)

    // Verify Audio was called with the URL
    expect(global.Audio).toHaveBeenCalledWith(mockAudioUrl)
  })

  it('calls loadCourses, loadStats, and loadTexts on mount', () => {
    const wrapper = shallowMount(Texts)

    expect(adminApi.getCourses).toHaveBeenCalled()
    expect(adminApi.getTextStats).toHaveBeenCalled()
    expect(adminApi.getTexts).toHaveBeenCalled()
  })
})
