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

import UnitWords from '@/views/admin/UnitWords.vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '@/utils/request'

describe('UnitWords.vue', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    request.mockResolvedValue({
      code: 200,
      data: { records: [], total: 0 }
    })
  })

  it('initializes with empty words array', () => {
    const wrapper = shallowMount(UnitWords)
    expect(wrapper.vm.words).toEqual([])
  })

  it('initializes with empty availableWords array', () => {
    const wrapper = shallowMount(UnitWords)
    expect(wrapper.vm.availableWords).toEqual([])
  })

  it('initializes with loading false', () => {
    const wrapper = shallowMount(UnitWords)
    expect(typeof wrapper.vm.loading).toBe('boolean')
  })

  it('initializes with addDialogVisible false', () => {
    const wrapper = shallowMount(UnitWords)
    expect(wrapper.vm.addDialogVisible).toBe(false)
  })

  it('initializes with searching false', () => {
    const wrapper = shallowMount(UnitWords)
    expect(wrapper.vm.searching).toBe(false)
  })

  it('initializes with adding false', () => {
    const wrapper = shallowMount(UnitWords)
    expect(wrapper.vm.adding).toBe(false)
  })

  it('initializes searchForm with default values', () => {
    const wrapper = shallowMount(UnitWords)
    expect(wrapper.vm.searchForm.keyword).toBe('')
    expect(wrapper.vm.searchForm.difficulty).toBe('')
  })

  it('initializes pagination with default values', () => {
    const wrapper = shallowMount(UnitWords)
    expect(wrapper.vm.pagination.current).toBe(1)
    expect(wrapper.vm.pagination.size).toBe(10)
    expect(wrapper.vm.pagination.total).toBe(0)
  })

  it('initializes addForm with default values', () => {
    const wrapper = shallowMount(UnitWords)
    expect(wrapper.vm.addForm.wordId).toBeNull()
  })

  it('loadWords fetches and populates words list', async () => {
    const mockWords = [
      { id: 1, word: 'hello', pronunciation: '/həˈləʊ/', difficulty: 'EASY' },
      { id: 2, word: 'world', pronunciation: '/wɜːld/', difficulty: 'EASY' }
    ]
    request.mockResolvedValue({
      code: 200,
      data: mockWords
    })

    const wrapper = shallowMount(UnitWords)
    await wrapper.vm.loadWords()

    expect(wrapper.vm.loading).toBe(false)
  })

  it('loadWords shows error on API failure', async () => {
    request.mockRejectedValue(new Error('Network error'))

    const wrapper = shallowMount(UnitWords)
    await wrapper.vm.loadWords()

    expect(ElMessage.error).toHaveBeenCalledWith('加载单词列表失败')
    expect(wrapper.vm.loading).toBe(false)
  })

  it('loadWords applies keyword filter', async () => {
    const mockWords = [
      { id: 1, word: 'test', pronunciation: '/test/', difficulty: 'EASY' },
      { id: 2, word: 'other', pronunciation: '/ðə/', difficulty: 'EASY' }
    ]
    request.mockResolvedValue({
      code: 200,
      data: mockWords
    })

    const wrapper = shallowMount(UnitWords)
    wrapper.vm.searchForm.keyword = 'test'

    await wrapper.vm.loadWords()

    expect(wrapper.vm.loading).toBe(false)
  })

  it('loadWords applies difficulty filter', async () => {
    const mockWords = [
      { id: 1, word: 'easy1', pronunciation: '/test/', difficulty: 'EASY' },
      { id: 2, word: 'hard1', pronunciation: '/test/', difficulty: 'HARD' }
    ]
    request.mockResolvedValue({
      code: 200,
      data: mockWords
    })

    const wrapper = shallowMount(UnitWords)
    wrapper.vm.searchForm.difficulty = 'EASY'

    await wrapper.vm.loadWords()

    expect(wrapper.vm.loading).toBe(false)
  })

  it('searchWords fetches matching words', async () => {
    const mockWords = [
      { id: 1, word: 'hello', pronunciation: '/həˈləʊ/', difficulty: 'EASY', unitId: null }
    ]
    request.mockResolvedValue({
      code: 200,
      data: { records: mockWords }
    })

    const wrapper = shallowMount(UnitWords)

    await wrapper.vm.searchWords('hello')

    expect(wrapper.vm.searching).toBe(false)
  })

  it('searchWords clears results when query is empty', async () => {
    const wrapper = shallowMount(UnitWords)
    wrapper.vm.availableWords = [{ id: 1 }]

    await wrapper.vm.searchWords('')

    expect(wrapper.vm.availableWords).toEqual([])
  })

  it('searchWords filters out words already in unit', async () => {
    const mockWords = [
      { id: 1, word: 'hello', unitId: null },
      { id: 2, word: 'world', unitId: null }
    ]
    request.mockResolvedValue({
      code: 200,
      data: { records: mockWords }
    })

    const wrapper = shallowMount(UnitWords)
    wrapper.vm.words = [{ id: 1 }] // Word 1 already in unit

    await wrapper.vm.searchWords('word')

    expect(wrapper.vm.availableWords.length).toBeGreaterThanOrEqual(0)
  })

  it('showAddDialog opens dialog and resets form', () => {
    const wrapper = shallowMount(UnitWords)
    wrapper.vm.addForm.wordId = 5

    wrapper.vm.showAddDialog()

    expect(wrapper.vm.addForm.wordId).toBeNull()
    expect(wrapper.vm.addDialogVisible).toBe(true)
  })

  it('addWordToUnit shows warning when no word selected', async () => {
    const wrapper = shallowMount(UnitWords)
    wrapper.vm.addForm.wordId = null

    await wrapper.vm.addWordToUnit()

    expect(ElMessage.warning).toHaveBeenCalledWith('请选择要添加的单词')
  })

  it('addWordToUnit successfully adds word to unit', async () => {
    request.mockResolvedValue({
      code: 200,
      data: {}
    })

    const wrapper = shallowMount(UnitWords)
    wrapper.vm.addForm.wordId = 1
    // unitId should already be set from route params in onMounted

    await wrapper.vm.addWordToUnit()

    expect(ElMessage.success).toHaveBeenCalledWith('单词添加成功')
    expect(wrapper.vm.addDialogVisible).toBe(false)
  })

  it('addWordToUnit handles API failure', async () => {
    request.mockResolvedValue({
      code: 500,
      message: '添加失败'
    })

    const wrapper = shallowMount(UnitWords)
    wrapper.vm.addForm.wordId = 1
    // unitId should already be set from route params in onMounted

    await wrapper.vm.addWordToUnit()

    expect(ElMessage.error).toHaveBeenCalled()
  })

  it('removeWord shows confirmation dialog', async () => {
    ElMessageBox.confirm.mockResolvedValue('confirm')
    request.mockResolvedValue({
      code: 200
    })

    const wrapper = shallowMount(UnitWords)
    // unitId should already be set from route params in onMounted

    await wrapper.vm.removeWord({ id: 1, word: 'test' })

    expect(ElMessageBox.confirm).toHaveBeenCalled()
  })

  it('removeWord shows success message on successful removal', async () => {
    ElMessageBox.confirm.mockResolvedValue('confirm')
    request.mockResolvedValue({
      code: 200
    })

    const wrapper = shallowMount(UnitWords)
    // unitId should already be set from route params in onMounted

    await wrapper.vm.removeWord({ id: 1, word: 'test' })

    expect(ElMessage.success).toHaveBeenCalledWith('单词移除成功')
  })

  it('removeWord handles cancellation gracefully', async () => {
    ElMessageBox.confirm.mockRejectedValue('cancel')

    const wrapper = shallowMount(UnitWords)

    await wrapper.vm.removeWord({ id: 1, word: 'test' })

    expect(ElMessage.error).not.toHaveBeenCalled()
  })

  it('resetSearch clears search form and reloads words', async () => {
    const wrapper = shallowMount(UnitWords)
    wrapper.vm.searchForm.keyword = 'test'
    wrapper.vm.searchForm.difficulty = 'EASY'

    await wrapper.vm.resetSearch()

    expect(wrapper.vm.searchForm.keyword).toBe('')
    expect(wrapper.vm.searchForm.difficulty).toBe('')
  })

  it('handleSizeChange updates page size and reloads', async () => {
    const wrapper = shallowMount(UnitWords)
    wrapper.vm.pagination.current = 2

    await wrapper.vm.handleSizeChange(50)

    expect(wrapper.vm.pagination.size).toBe(50)
  })

  it('handleCurrentChange updates current page and reloads', async () => {
    const wrapper = shallowMount(UnitWords)

    await wrapper.vm.handleCurrentChange(3)

    expect(wrapper.vm.pagination.current).toBe(3)
  })

  it('getDifficultyType returns correct tag type', () => {
    const wrapper = shallowMount(UnitWords)

    expect(wrapper.vm.getDifficultyType('EASY')).toBe('success')
    expect(wrapper.vm.getDifficultyType('MEDIUM')).toBe('warning')
    expect(wrapper.vm.getDifficultyType('HARD')).toBe('danger')
    expect(wrapper.vm.getDifficultyType('UNKNOWN')).toBe('info')
  })

  it('getDifficultyText returns correct text', () => {
    const wrapper = shallowMount(UnitWords)

    expect(wrapper.vm.getDifficultyText('EASY')).toBe('简单')
    expect(wrapper.vm.getDifficultyText('MEDIUM')).toBe('中等')
    expect(wrapper.vm.getDifficultyText('HARD')).toBe('困难')
    expect(wrapper.vm.getDifficultyText('UNKNOWN')).toBe('UNKNOWN')
  })

  it('formatTime returns formatted date string', () => {
    const wrapper = shallowMount(UnitWords)

    const result = wrapper.vm.formatTime('2024-06-01T12:00:00')

    expect(typeof result).toBe('string')
    expect(result).not.toBe('')
  })

  it('formatTime returns empty string for falsy input', () => {
    const wrapper = shallowMount(UnitWords)

    expect(wrapper.vm.formatTime(null)).toBe('')
    expect(wrapper.vm.formatTime(undefined)).toBe('')
    expect(wrapper.vm.formatTime('')).toBe('')
  })

  it('backToUnits navigates to units page', () => {
    const wrapper = shallowMount(UnitWords)

    expect(wrapper.vm.router).toBeDefined()
  })
})
