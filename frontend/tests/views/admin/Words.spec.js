import { describe, it, expect, vi, beforeEach } from 'vitest'
import { shallowMount } from '@vue/test-utils'

const { mockRequest } = vi.hoisted(() => ({
  mockRequest: vi.fn()
}))

vi.mock('vue-router', () => ({
  useRouter: () => ({ push: vi.fn() }),
  useRoute: () => ({ params: {}, query: {} })
}))

vi.mock('element-plus', () => ({
  ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn() },
  ElMessageBox: { confirm: vi.fn() }
}))

vi.mock('@/utils/request', () => ({
  default: (...args) => mockRequest(...args)
}))

import Words from '@/views/admin/Words.vue'
import { ElMessage, ElMessageBox } from 'element-plus'

describe('Words.vue', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockRequest.mockResolvedValue({ code: 200, data: { records: [], total: 0 } })
  })

  it('initializes with empty words array', () => {
    const wrapper = shallowMount(Words)
    expect(wrapper.vm.words).toEqual([])
  })

  it('initializes with empty stats object', () => {
    const wrapper = shallowMount(Words)
    expect(wrapper.vm.stats).toEqual({})
  })

  it('has loading ref defined', () => {
    const wrapper = shallowMount(Words)
    expect(typeof wrapper.vm.loading).toBe('boolean')
  })

  it('initializes with dialogVisible false', () => {
    const wrapper = shallowMount(Words)
    expect(wrapper.vm.dialogVisible).toBe(false)
  })

  it('initializes with isEdit false', () => {
    const wrapper = shallowMount(Words)
    expect(wrapper.vm.isEdit).toBe(false)
  })

  it('initializes with saving false', () => {
    const wrapper = shallowMount(Words)
    expect(wrapper.vm.saving).toBe(false)
  })

  it('initializes pagination with size 20', () => {
    const wrapper = shallowMount(Words)
    expect(wrapper.vm.pagination.current).toBe(1)
    expect(wrapper.vm.pagination.size).toBe(20)
    expect(wrapper.vm.pagination.total).toBe(0)
  })

  it('initializes wordForm with default values', () => {
    const wrapper = shallowMount(Words)
    expect(wrapper.vm.wordForm.word).toBe('')
    expect(wrapper.vm.wordForm.pronunciation).toBe('')
    expect(wrapper.vm.wordForm.definition).toBe('')
    expect(wrapper.vm.wordForm.partOfSpeech).toBe('noun')
    expect(wrapper.vm.wordForm.difficulty).toBe('EASY')
    expect(wrapper.vm.wordForm.exampleSentence).toBe('')
    expect(wrapper.vm.wordForm.audioUrl).toBe('')
  })

  it('loadWords fetches and populates words list', async () => {
    const mockRecords = [
      { id: 1, word: 'hello', definition: 'greeting' },
      { id: 2, word: 'world', definition: 'the earth' }
    ]
    mockRequest.mockResolvedValue({
      code: 200,
      data: { records: mockRecords, total: 2 }
    })

    const wrapper = shallowMount(Words)
    await wrapper.vm.loadWords()

    expect(wrapper.vm.words).toEqual(mockRecords)
    expect(wrapper.vm.pagination.total).toBe(2)
    expect(wrapper.vm.loading).toBe(false)
  })

  it('loadWords shows error message on failure', async () => {
    mockRequest.mockRejectedValue(new Error('Network error'))

    const wrapper = shallowMount(Words)
    await wrapper.vm.loadWords()

    expect(ElMessage.error).toHaveBeenCalledWith('加载单词列表失败')
    expect(wrapper.vm.loading).toBe(false)
  })

  it('loadStats fetches and sets stats', async () => {
    mockRequest.mockResolvedValue({
      code: 200,
      data: { totalEasy: 10, totalMedium: 20, totalHard: 5, totalWords: 35 }
    })

    const wrapper = shallowMount(Words)
    await wrapper.vm.loadStats()

    expect(wrapper.vm.stats).toEqual({
      totalEasy: 10, totalMedium: 20, totalHard: 5, totalWords: 35
    })
  })

  it('loadStats handles error gracefully', async () => {
    mockRequest.mockRejectedValue(new Error('Stats error'))

    const wrapper = shallowMount(Words)
    await wrapper.vm.loadStats()

    // Should not throw, stats stays empty
    expect(wrapper.vm.stats).toBeDefined()
  })

  it('handleSearch resets pagination to page 1 and loads words', async () => {
    const wrapper = shallowMount(Words)
    wrapper.vm.pagination.current = 5

    await wrapper.vm.handleSearch()

    expect(wrapper.vm.pagination.current).toBe(1)
  })

  it('resetSearch clears search form and resets pagination', async () => {
    const wrapper = shallowMount(Words)
    wrapper.vm.searchForm.keyword = 'test'
    wrapper.vm.searchForm.difficulty = 'HARD'
    wrapper.vm.pagination.current = 3

    await wrapper.vm.resetSearch()

    expect(wrapper.vm.searchForm.keyword).toBe('')
    expect(wrapper.vm.searchForm.difficulty).toBe('')
    expect(wrapper.vm.pagination.current).toBe(1)
  })

  it('handleSizeChange updates pagination size and resets page', async () => {
    const wrapper = shallowMount(Words)
    wrapper.vm.pagination.current = 3

    await wrapper.vm.handleSizeChange(50)

    expect(wrapper.vm.pagination.size).toBe(50)
    expect(wrapper.vm.pagination.current).toBe(1)
  })

  it('handleCurrentChange updates current page', async () => {
    const wrapper = shallowMount(Words)

    await wrapper.vm.handleCurrentChange(7)

    expect(wrapper.vm.pagination.current).toBe(7)
  })

  it('showAddDialog sets isEdit to false and opens dialog', () => {
    const wrapper = shallowMount(Words)
    wrapper.vm.showAddDialog()

    expect(wrapper.vm.isEdit).toBe(false)
    expect(wrapper.vm.dialogVisible).toBe(true)
  })

  it('editWord sets isEdit to true and populates form', () => {
    const wrapper = shallowMount(Words)
    const word = {
      id: 10,
      word: 'example',
      pronunciation: '/ɪɡˈzæmpəl/',
      definition: 'a thing illustrating a rule',
      partOfSpeech: 'noun',
      difficulty: 'MEDIUM',
      exampleSentence: 'This is an example.',
      audioUrl: 'http://example.com/audio.mp3'
    }

    wrapper.vm.editWord(word)

    expect(wrapper.vm.isEdit).toBe(true)
    expect(wrapper.vm.dialogVisible).toBe(true)
    expect(wrapper.vm.wordForm.word).toBe('example')
    expect(wrapper.vm.wordForm.pronunciation).toBe('/ɪɡˈzæmpəl/')
    expect(wrapper.vm.wordForm.definition).toBe('a thing illustrating a rule')
    expect(wrapper.vm.wordForm.partOfSpeech).toBe('noun')
    expect(wrapper.vm.wordForm.difficulty).toBe('MEDIUM')
    expect(wrapper.vm.wordForm.exampleSentence).toBe('This is an example.')
    expect(wrapper.vm.wordForm.audioUrl).toBe('http://example.com/audio.mp3')
  })

  it('editWord fills default values for missing fields', () => {
    const wrapper = shallowMount(Words)
    const word = { id: 1, word: 'test' }

    wrapper.vm.editWord(word)

    expect(wrapper.vm.wordForm.pronunciation).toBe('')
    expect(wrapper.vm.wordForm.definition).toBe('')
    expect(wrapper.vm.wordForm.partOfSpeech).toBe('noun')
    expect(wrapper.vm.wordForm.difficulty).toBe('EASY')
    expect(wrapper.vm.wordForm.exampleSentence).toBe('')
    expect(wrapper.vm.wordForm.audioUrl).toBe('')
  })

  it('resetForm resets wordForm to initial values', () => {
    const wrapper = shallowMount(Words)
    wrapper.vm.wordForm.word = 'modified'
    wrapper.vm.wordForm.difficulty = 'HARD'
    wrapper.vm.wordForm.id = 99

    wrapper.vm.resetForm()

    expect(wrapper.vm.wordForm.id).toBeUndefined()
    expect(wrapper.vm.wordForm.word).toBe('')
    expect(wrapper.vm.wordForm.pronunciation).toBe('')
    expect(wrapper.vm.wordForm.definition).toBe('')
    expect(wrapper.vm.wordForm.partOfSpeech).toBe('noun')
    expect(wrapper.vm.wordForm.difficulty).toBe('EASY')
    expect(wrapper.vm.wordForm.exampleSentence).toBe('')
    expect(wrapper.vm.wordForm.audioUrl).toBe('')
  })

  it('getDifficultyType returns correct types', () => {
    const wrapper = shallowMount(Words)
    expect(wrapper.vm.getDifficultyType('EASY')).toBe('success')
    expect(wrapper.vm.getDifficultyType('MEDIUM')).toBe('warning')
    expect(wrapper.vm.getDifficultyType('HARD')).toBe('danger')
    expect(wrapper.vm.getDifficultyType('UNKNOWN')).toBe('info')
  })

  it('getDifficultyText returns correct Chinese text', () => {
    const wrapper = shallowMount(Words)
    expect(wrapper.vm.getDifficultyText('EASY')).toBe('简单')
    expect(wrapper.vm.getDifficultyText('MEDIUM')).toBe('中等')
    expect(wrapper.vm.getDifficultyText('HARD')).toBe('困难')
    expect(wrapper.vm.getDifficultyText('OTHER')).toBe('OTHER')
  })

  it('getPartOfSpeechType returns correct tag types', () => {
    const wrapper = shallowMount(Words)
    expect(wrapper.vm.getPartOfSpeechType('noun')).toBe('')
    expect(wrapper.vm.getPartOfSpeechType('verb')).toBe('success')
    expect(wrapper.vm.getPartOfSpeechType('adjective')).toBe('warning')
    expect(wrapper.vm.getPartOfSpeechType('adverb')).toBe('info')
    expect(wrapper.vm.getPartOfSpeechType('pronoun')).toBe('primary')
    expect(wrapper.vm.getPartOfSpeechType('preposition')).toBe('danger')
    expect(wrapper.vm.getPartOfSpeechType('conjunction')).toBe('warning')
    expect(wrapper.vm.getPartOfSpeechType('interjection')).toBe('success')
    expect(wrapper.vm.getPartOfSpeechType('UNKNOWN')).toBe('')
  })

  it('getPartOfSpeechText returns correct Chinese text', () => {
    const wrapper = shallowMount(Words)
    expect(wrapper.vm.getPartOfSpeechText('noun')).toBe('名词')
    expect(wrapper.vm.getPartOfSpeechText('verb')).toBe('动词')
    expect(wrapper.vm.getPartOfSpeechText('adjective')).toBe('形容词')
    expect(wrapper.vm.getPartOfSpeechText('adverb')).toBe('副词')
    expect(wrapper.vm.getPartOfSpeechText('unknown')).toBe('unknown')
  })

  it('formatTime returns empty string for falsy value', () => {
    const wrapper = shallowMount(Words)
    expect(wrapper.vm.formatTime(null)).toBe('')
    expect(wrapper.vm.formatTime(undefined)).toBe('')
    expect(wrapper.vm.formatTime('')).toBe('')
  })

  it('formatTime returns a formatted string for valid date', () => {
    const wrapper = shallowMount(Words)
    const result = wrapper.vm.formatTime('2024-01-15T10:30:00')
    expect(typeof result).toBe('string')
    expect(result).toBeTruthy()
  })

  it('deleteWord calls ElMessageBox.confirm', async () => {
    ElMessageBox.confirm.mockResolvedValue('confirm')
    mockRequest.mockResolvedValue({ code: 200 })

    const wrapper = shallowMount(Words)
    // deleteWord uses .then() chain, so we await the wrapper.vm call
    wrapper.vm.deleteWord({ id: 1, word: 'test' })

    expect(ElMessageBox.confirm).toHaveBeenCalled()
  })

  it('playAudio does nothing for empty audioUrl', () => {
    const wrapper = shallowMount(Words)
    // Should not throw
    wrapper.vm.playAudio('')
    wrapper.vm.playAudio(null)
  })
})
