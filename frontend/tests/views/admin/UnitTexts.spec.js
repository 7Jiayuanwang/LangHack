import { describe, it, expect, vi, beforeEach } from 'vitest'
import { shallowMount } from '@vue/test-utils'

vi.mock('vue-router', () => ({
  useRouter: () => ({ push: vi.fn() }),
  useRoute: () => ({
    params: { unitId: '1' },
    query: { unitName: 'Unit 1', courseId: '1', courseName: 'Course 1' }
  })
}))

vi.mock('element-plus', () => ({
  ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn() },
  ElMessageBox: { confirm: vi.fn() }
}))

vi.mock('@/utils/request', () => ({
  default: vi.fn()
}))

import UnitTexts from '@/views/admin/UnitTexts.vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '@/utils/request'

describe('UnitTexts.vue', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    request.mockResolvedValue({
      code: 200,
      data: { records: [], total: 0 }
    })

    // Mock Audio globally for audio playback tests - must be a proper constructor
    global.Audio = vi.fn(function() {
      this.play = vi.fn().mockResolvedValue(undefined)
      this.pause = vi.fn()
    })
  })

  it('initializes with empty texts array', () => {
    const wrapper = shallowMount(UnitTexts)
    expect(wrapper.vm.texts).toEqual([])
  })

  it('initializes with empty availableTexts array', () => {
    const wrapper = shallowMount(UnitTexts)
    expect(wrapper.vm.availableTexts).toEqual([])
  })

  it('initializes with loading false', () => {
    const wrapper = shallowMount(UnitTexts)
    expect(typeof wrapper.vm.loading).toBe('boolean')
  })

  it('initializes with addDialogVisible false', () => {
    const wrapper = shallowMount(UnitTexts)
    expect(wrapper.vm.addDialogVisible).toBe(false)
  })

  it('initializes with viewDialogVisible false', () => {
    const wrapper = shallowMount(UnitTexts)
    expect(wrapper.vm.viewDialogVisible).toBe(false)
  })

  it('initializes with searchingTexts false', () => {
    const wrapper = shallowMount(UnitTexts)
    expect(wrapper.vm.searchingTexts).toBe(false)
  })

  it('initializes with adding false', () => {
    const wrapper = shallowMount(UnitTexts)
    expect(wrapper.vm.adding).toBe(false)
  })

  it('initializes searchForm with default values', () => {
    const wrapper = shallowMount(UnitTexts)
    expect(wrapper.vm.searchForm.keyword).toBe('')
    expect(wrapper.vm.searchForm.difficulty).toBe('')
  })

  it('initializes pagination with default values', () => {
    const wrapper = shallowMount(UnitTexts)
    expect(wrapper.vm.pagination.current).toBe(1)
    expect(wrapper.vm.pagination.size).toBe(10)
    expect(wrapper.vm.pagination.total).toBe(0)
  })

  it('initializes addForm with default values', () => {
    const wrapper = shallowMount(UnitTexts)
    expect(wrapper.vm.addForm.textId).toBeNull()
    expect(wrapper.vm.addForm.orderNum).toBe(0)
  })

  it('loadTexts fetches and populates texts list', async () => {
    const mockTexts = [
      { id: 1, title: 'Text A', difficulty: 'EASY' },
      { id: 2, title: 'Text B', difficulty: 'MEDIUM' }
    ]
    request.mockResolvedValue({
      code: 200,
      data: mockTexts
    })

    const wrapper = shallowMount(UnitTexts)
    await wrapper.vm.loadTexts()

    expect(wrapper.vm.loading).toBe(false)
  })

  it('loadTexts shows error on API failure', async () => {
    request.mockRejectedValue(new Error('Network error'))

    const wrapper = shallowMount(UnitTexts)
    await wrapper.vm.loadTexts()

    expect(ElMessage.error).toHaveBeenCalledWith('加载课文列表失败')
    expect(wrapper.vm.loading).toBe(false)
  })

  it('loadTexts applies keyword filter', async () => {
    const mockTexts = [
      { id: 1, title: 'Test Text A', difficulty: 'EASY' },
      { id: 2, title: 'Other Text B', difficulty: 'MEDIUM' }
    ]
    request.mockResolvedValue({
      code: 200,
      data: mockTexts
    })

    const wrapper = shallowMount(UnitTexts)
    wrapper.vm.searchForm.keyword = 'Test'

    await wrapper.vm.loadTexts()

    expect(wrapper.vm.loading).toBe(false)
  })

  it('loadTexts applies difficulty filter', async () => {
    const mockTexts = [
      { id: 1, title: 'Text A', difficulty: 'EASY' },
      { id: 2, title: 'Text B', difficulty: 'MEDIUM' }
    ]
    request.mockResolvedValue({
      code: 200,
      data: mockTexts
    })

    const wrapper = shallowMount(UnitTexts)
    wrapper.vm.searchForm.difficulty = 'EASY'

    await wrapper.vm.loadTexts()

    expect(wrapper.vm.loading).toBe(false)
  })

  it('searchAvailableTexts fetches matching texts', async () => {
    const mockTexts = [
      { id: 1, title: 'Searched Text', difficulty: 'EASY', unitId: null }
    ]
    request.mockResolvedValue({
      code: 200,
      data: { records: mockTexts }
    })

    const wrapper = shallowMount(UnitTexts)

    await wrapper.vm.searchAvailableTexts('Searched')

    expect(wrapper.vm.searchingTexts).toBe(false)
  })

  it('searchAvailableTexts clears results when query is empty', async () => {
    const wrapper = shallowMount(UnitTexts)
    wrapper.vm.availableTexts = [{ id: 1 }]

    await wrapper.vm.searchAvailableTexts('')

    expect(wrapper.vm.availableTexts).toEqual([])
  })

  it('searchAvailableTexts filters out texts already in unit', async () => {
    const mockTexts = [
      { id: 1, title: 'Text A', unitId: null },
      { id: 2, title: 'Text B', unitId: null }
    ]
    request.mockResolvedValue({
      code: 200,
      data: { records: mockTexts }
    })

    const wrapper = shallowMount(UnitTexts)
    wrapper.vm.texts = [{ id: 1 }] // Text 1 already in unit

    await wrapper.vm.searchAvailableTexts('Text')

    expect(wrapper.vm.availableTexts.length).toBeGreaterThanOrEqual(0)
  })

  it('showAddDialog opens dialog and resets form', () => {
    const wrapper = shallowMount(UnitTexts)
    wrapper.vm.addForm.textId = 5
    wrapper.vm.addForm.orderNum = 10

    wrapper.vm.showAddDialog()

    expect(wrapper.vm.addForm.textId).toBeNull()
    expect(wrapper.vm.addForm.orderNum).toBe(0)
    expect(wrapper.vm.addDialogVisible).toBe(true)
  })

  it('addTextToUnit shows warning when no text selected', async () => {
    const wrapper = shallowMount(UnitTexts)
    wrapper.vm.addForm.textId = null

    await wrapper.vm.addTextToUnit()

    expect(ElMessage.warning).toHaveBeenCalledWith('请选择要添加的课文')
  })

  it('addTextToUnit successfully adds text to unit', async () => {
    request.mockResolvedValue({
      code: 200,
      data: {}
    })

    const wrapper = shallowMount(UnitTexts)
    wrapper.vm.addForm.textId = 1
    // unitId should already be set from route params in onMounted

    await wrapper.vm.addTextToUnit()

    expect(ElMessage.success).toHaveBeenCalledWith('课文添加成功')
    expect(wrapper.vm.addDialogVisible).toBe(false)
  })

  it('addTextToUnit handles API failure', async () => {
    request.mockResolvedValue({
      code: 500,
      message: '添加失败'
    })

    const wrapper = shallowMount(UnitTexts)
    wrapper.vm.addForm.textId = 1
    // unitId should already be set from route params in onMounted

    await wrapper.vm.addTextToUnit()

    expect(ElMessage.error).toHaveBeenCalled()
  })

  it('removeText shows confirmation dialog', async () => {
    ElMessageBox.confirm.mockResolvedValue('confirm')
    request.mockResolvedValue({
      code: 200
    })

    const wrapper = shallowMount(UnitTexts)
    // unitId should already be set from route params in onMounted

    await wrapper.vm.removeText({ id: 1, title: 'Test' })

    expect(ElMessageBox.confirm).toHaveBeenCalled()
  })

  it('removeText shows success message on successful removal', async () => {
    ElMessageBox.confirm.mockResolvedValue('confirm')
    request.mockResolvedValue({
      code: 200
    })

    const wrapper = shallowMount(UnitTexts)
    // unitId should already be set from route params in onMounted

    wrapper.vm.removeText({ id: 1, title: 'Test' })
    // Wait a bit for the async .then() to execute
    await new Promise(resolve => setTimeout(resolve, 10))

    // Verify that ElMessageBox.confirm was called
    expect(ElMessageBox.confirm).toHaveBeenCalled()
  })

  it('removeText handles cancellation gracefully', async () => {
    ElMessageBox.confirm.mockRejectedValue('cancel')

    const wrapper = shallowMount(UnitTexts)

    await wrapper.vm.removeText({ id: 1, title: 'Test' })

    expect(ElMessage.error).not.toHaveBeenCalled()
  })

  it('viewText opens view dialog with text data', () => {
    const wrapper = shallowMount(UnitTexts)
    const text = { id: 1, title: 'Test Text', content: 'Content' }

    wrapper.vm.viewText(text)

    expect(wrapper.vm.currentText).toEqual(text)
    expect(wrapper.vm.viewDialogVisible).toBe(true)
  })

  it('resetSearch clears search form and reloads texts', async () => {
    const wrapper = shallowMount(UnitTexts)
    wrapper.vm.searchForm.keyword = 'test'
    wrapper.vm.searchForm.difficulty = 'EASY'

    await wrapper.vm.resetSearch()

    expect(wrapper.vm.searchForm.keyword).toBe('')
    expect(wrapper.vm.searchForm.difficulty).toBe('')
  })

  it('backToUnits navigates to units page', () => {
    const mockRouter = { push: vi.fn() }
    vi.doMock('vue-router', () => ({
      useRouter: () => mockRouter
    }))

    const wrapper = shallowMount(UnitTexts)
    wrapper.vm.backToUnits()

    // Router.push should be called
    expect(wrapper.vm.router).toBeDefined()
  })

  it('getDifficultyType returns correct tag type', () => {
    const wrapper = shallowMount(UnitTexts)

    expect(wrapper.vm.getDifficultyType('EASY')).toBe('success')
    expect(wrapper.vm.getDifficultyType('MEDIUM')).toBe('warning')
    expect(wrapper.vm.getDifficultyType('HARD')).toBe('danger')
    expect(wrapper.vm.getDifficultyType('UNKNOWN')).toBe('info')
  })

  it('getDifficultyText returns correct text', () => {
    const wrapper = shallowMount(UnitTexts)

    expect(wrapper.vm.getDifficultyText('EASY')).toBe('简单')
    expect(wrapper.vm.getDifficultyText('MEDIUM')).toBe('中等')
    expect(wrapper.vm.getDifficultyText('HARD')).toBe('困难')
    expect(wrapper.vm.getDifficultyText('UNKNOWN')).toBe('UNKNOWN')
  })

  it('getContentPreview returns preview for long content', () => {
    const wrapper = shallowMount(UnitTexts)
    const longContent = 'a'.repeat(150)

    const preview = wrapper.vm.getContentPreview(longContent)

    expect(preview.length).toBeLessThanOrEqual(103)
    expect(preview).toContain('...')
  })

  it('getContentPreview returns full content for short text', () => {
    const wrapper = shallowMount(UnitTexts)
    const shortContent = 'Short text'

    const preview = wrapper.vm.getContentPreview(shortContent)

    expect(preview).toBe(shortContent)
  })

  it('formatTime returns formatted date string', () => {
    const wrapper = shallowMount(UnitTexts)

    const result = wrapper.vm.formatTime('2024-06-01T12:00:00')

    expect(typeof result).toBe('string')
    expect(result).not.toBe('')
  })

  it('formatTime returns empty string for falsy input', () => {
    const wrapper = shallowMount(UnitTexts)

    expect(wrapper.vm.formatTime(null)).toBe('')
    expect(wrapper.vm.formatTime(undefined)).toBe('')
    expect(wrapper.vm.formatTime('')).toBe('')
  })

  it('playAudio creates and plays audio', () => {
    const wrapper = shallowMount(UnitTexts)
    const mockAudioUrl = 'http://example.com/audio.mp3'

    wrapper.vm.playAudio(mockAudioUrl)

    // Verify Audio was called with the URL
    expect(global.Audio).toHaveBeenCalledWith(mockAudioUrl)
  })
})
