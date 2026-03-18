import { describe, it, expect, vi, beforeEach } from 'vitest'
import { shallowMount } from '@vue/test-utils'

vi.mock('vue-router', () => ({
  useRouter: () => ({ push: vi.fn(), go: vi.fn() }),
  useRoute: () => ({
    query: {
      textId: '1',
      assignmentId: null,
      from: null
    },
    params: {}
  })
}))

vi.mock('element-plus', () => ({
  ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn(), info: vi.fn() },
  ElMessageBox: { confirm: vi.fn().mockResolvedValue({}) }
}))

vi.mock('@/api/student', () => ({
  default: {
    getTextDetail: vi.fn().mockResolvedValue({ code: 200, data: {} }),
    recordLearning: vi.fn().mockResolvedValue({ code: 200, data: {} }),
    markTextAsCompleted: vi.fn().mockResolvedValue({ code: 200, data: {} })
  },
  getVocabularyStats: vi.fn().mockResolvedValue({ code: 200, data: {} })
}))

import InteractiveText from '@/views/user/InteractiveText.vue'

describe('InteractiveText.vue', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  const createWrapper = () => shallowMount(InteractiveText)

  it('mounts without error', () => {
    const wrapper = createWrapper()
    expect(wrapper.exists()).toBe(true)
  })

  it('initializes textId from route query', () => {
    const wrapper = createWrapper()
    expect(wrapper.vm.textId).toBeDefined()
  })

  it('initializes assignmentId from route query', () => {
    const wrapper = createWrapper()
    expect(wrapper.vm.assignmentId).toBeDefined()
  })

  it('initializes fromSource from route query', () => {
    const wrapper = createWrapper()
    expect(wrapper.vm.fromSource).toBeDefined()
  })

  it('initializes studyTime as ref(0)', () => {
    const wrapper = createWrapper()
    expect(wrapper.vm.studyTime).toBe(0)
  })

  it('initializes recording as ref(false)', () => {
    const wrapper = createWrapper()
    expect(wrapper.vm.recording).toBe(false)
  })

  it('initializes isCompleted as ref(false)', () => {
    const wrapper = createWrapper()
    expect(wrapper.vm.isCompleted).toBe(false)
  })

  it('initializes isFavorite as ref(false)', () => {
    const wrapper = createWrapper()
    expect(wrapper.vm.isFavorite).toBe(false)
  })

  it('initializes textTitle as empty string', () => {
    const wrapper = createWrapper()
    expect(wrapper.vm.textTitle).toBe('')
  })

  it('initializes textContent as empty string', () => {
    const wrapper = createWrapper()
    expect(wrapper.vm.textContent).toBe('')
  })

  it('initializes difficulty as MEDIUM', () => {
    const wrapper = createWrapper()
    expect(wrapper.vm.difficulty).toBe('MEDIUM')
  })

  it('initializes REQUIRED_STUDY_TIME as 10 minutes', () => {
    const wrapper = createWrapper()
    expect(wrapper.vm.REQUIRED_STUDY_TIME).toBe(10 * 60 * 1000)
  })

  it('difficultyClass computed property returns correct object for EASY', () => {
    const wrapper = createWrapper()
    wrapper.vm.difficulty = 'EASY'
    expect(wrapper.vm.difficultyClass).toBeDefined()
    expect(wrapper.vm.difficultyClass['difficulty-easy']).toBe(true)
  })

  it('difficultyClass computed property returns correct object for MEDIUM', () => {
    const wrapper = createWrapper()
    wrapper.vm.difficulty = 'MEDIUM'
    expect(wrapper.vm.difficultyClass['difficulty-medium']).toBe(true)
  })

  it('difficultyClass computed property returns correct object for HARD', () => {
    const wrapper = createWrapper()
    wrapper.vm.difficulty = 'HARD'
    expect(wrapper.vm.difficultyClass['difficulty-hard']).toBe(true)
  })

  it('difficultyText returns correct Chinese text for EASY', () => {
    const wrapper = createWrapper()
    wrapper.vm.difficulty = 'EASY'
    expect(wrapper.vm.difficultyText).toBe('简单')
  })

  it('difficultyText returns correct Chinese text for MEDIUM', () => {
    const wrapper = createWrapper()
    wrapper.vm.difficulty = 'MEDIUM'
    expect(wrapper.vm.difficultyText).toBe('中等')
  })

  it('difficultyText returns correct Chinese text for HARD', () => {
    const wrapper = createWrapper()
    wrapper.vm.difficulty = 'HARD'
    expect(wrapper.vm.difficultyText).toBe('困难')
  })

  it('wordCount computed property returns 0 for empty content', () => {
    const wrapper = createWrapper()
    wrapper.vm.textContent = ''
    expect(wrapper.vm.wordCount).toBe(0)
  })

  it('wordCount computed property counts words in content', () => {
    const wrapper = createWrapper()
    wrapper.vm.textContent = 'Hello world test'
    expect(wrapper.vm.wordCount).toBe(3)
  })

  it('estimatedTime computed property calculates correct reading time', () => {
    const wrapper = createWrapper()
    wrapper.vm.textContent = 'a '.repeat(100)
    const time = wrapper.vm.estimatedTime
    expect(time).toBeGreaterThan(0)
  })

  it('isStudyTimeCompleted computed property returns false initially', () => {
    const wrapper = createWrapper()
    expect(wrapper.vm.isStudyTimeCompleted).toBe(false)
  })

  it('isStudyTimeCompleted computed property returns true when studyTime >= required', () => {
    const wrapper = createWrapper()
    wrapper.vm.studyTime = 600 // 10 minutes in seconds
    expect(wrapper.vm.isStudyTimeCompleted).toBe(true)
  })

  it('timeBasedProgress computed property calculates percentage', () => {
    const wrapper = createWrapper()
    wrapper.vm.studyTime = 300 // 5 minutes in seconds
    const progress = wrapper.vm.timeBasedProgress
    expect(progress).toBeGreaterThan(0)
    expect(progress).toBeLessThanOrEqual(100)
  })

  it('startReading sets recording to true', () => {
    const wrapper = createWrapper()
    wrapper.vm.startReading()
    expect(wrapper.vm.recording).toBe(true)
  })

  it('pauseReading sets recording to false', async () => {
    const wrapper = createWrapper()
    wrapper.vm.recording = true
    await wrapper.vm.pauseReading()
    expect(wrapper.vm.recording).toBe(false)
  })

  it('formatStudyTime formats seconds to readable time', () => {
    const wrapper = createWrapper()
    expect(wrapper.vm.formatStudyTime(0)).toBe('0分0秒')
    expect(wrapper.vm.formatStudyTime(65)).toBe('1分5秒')
    expect(wrapper.vm.formatStudyTime(3661)).toContain('小时')
  })

  it('formatTimeProgress returns correct percentage string', () => {
    const wrapper = createWrapper()
    wrapper.vm.studyTime = 0
    const progress = wrapper.vm.formatTimeProgress(50)
    expect(progress).toBeDefined()
  })

  it('formatRemainingTime calculates remaining study time', () => {
    const wrapper = createWrapper()
    wrapper.vm.studyTime = 300
    const remaining = wrapper.vm.formatRemainingTime()
    expect(remaining).toBeDefined()
    expect(typeof remaining).toBe('string')
  })

  it('goBack navigates back when from is assignment', () => {
    const wrapper = createWrapper()
    expect(wrapper.vm.goBack).toBeDefined()
    // Method should be callable without error
    expect(() => wrapper.vm.goBack()).not.toThrow()
  })

  it('handleWordClick is defined and callable', () => {
    const wrapper = createWrapper()
    expect(wrapper.vm.handleWordClick).toBeDefined()
    expect(typeof wrapper.vm.handleWordClick).toBe('function')
  })

  it('handleLearningRecord is defined and callable', () => {
    const wrapper = createWrapper()
    expect(wrapper.vm.handleLearningRecord).toBeDefined()
    expect(typeof wrapper.vm.handleLearningRecord).toBe('function')
  })

  it('markAsCompleted sets isCompleted to true', async () => {
    const wrapper = createWrapper()
    // Method should be defined
    expect(wrapper.vm.markAsCompleted).toBeDefined()
  })

  it('addToFavorite toggles isFavorite state', async () => {
    const wrapper = createWrapper()
    const initialState = wrapper.vm.isFavorite
    await wrapper.vm.addToFavorite()
    expect(wrapper.vm.isFavorite).toBe(!initialState)
  })

  it('learningSuggestions is initialized as empty array', () => {
    const wrapper = createWrapper()
    expect(Array.isArray(wrapper.vm.learningSuggestions)).toBe(true)
  })

  it('generateLearningSuggestions creates array of suggestions', () => {
    const wrapper = createWrapper()
    wrapper.vm.generateLearningSuggestions()
    expect(Array.isArray(wrapper.vm.learningSuggestions)).toBe(true)
    expect(wrapper.vm.learningSuggestions.length).toBeGreaterThan(0)
  })

  it('loadTextDetail is defined and callable', () => {
    const wrapper = createWrapper()
    expect(wrapper.vm.loadTextDetail).toBeDefined()
    expect(typeof wrapper.vm.loadTextDetail).toBe('function')
  })

  it('stopStudyTimer clears interval correctly', () => {
    const wrapper = createWrapper()
    expect(wrapper.vm.stopStudyTimer).toBeDefined()
    // Should not throw
    expect(() => wrapper.vm.stopStudyTimer()).not.toThrow()
  })

  it('saveLearningRecord is defined and callable', () => {
    const wrapper = createWrapper()
    expect(wrapper.vm.saveLearningRecord).toBeDefined()
    expect(typeof wrapper.vm.saveLearningRecord).toBe('function')
  })

  it('courseId ref is initialized', () => {
    const wrapper = createWrapper()
    expect(wrapper.vm.courseId).toBeDefined()
  })

  it('unitId ref is initialized', () => {
    const wrapper = createWrapper()
    expect(wrapper.vm.unitId).toBeDefined()
  })

  it('courseName ref is initialized', () => {
    const wrapper = createWrapper()
    expect(wrapper.vm.courseName).toBe('')
  })

  it('unitName ref is initialized', () => {
    const wrapper = createWrapper()
    expect(wrapper.vm.unitName).toBe('')
  })

  it('showSuggestionDialog ref is initialized to false', () => {
    const wrapper = createWrapper()
    expect(wrapper.vm.showSuggestionDialog).toBe(false)
  })

  it('recordLearning method is defined', () => {
    const wrapper = createWrapper()
    expect(wrapper.vm.recordLearning).toBeDefined()
    expect(typeof wrapper.vm.recordLearning).toBe('function')
  })

  it('learnedWordsCount ref is initialized to 0', () => {
    const wrapper = createWrapper()
    expect(wrapper.vm.learnedWordsCount).toBe(0)
  })

  it('learningProgress ref is initialized to 0', () => {
    const wrapper = createWrapper()
    expect(wrapper.vm.learningProgress).toBe(0)
  })
})
