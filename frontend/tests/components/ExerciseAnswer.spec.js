import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { shallowMount } from '@vue/test-utils'

// Mock request module
const mockRequest = vi.hoisted(() => vi.fn())
vi.mock('@/utils/request', () => ({
  default: mockRequest
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

import ExerciseAnswer from '@/components/ExerciseAnswer.vue'

// Helper: create a standard multiple-choice exercise
const createExercise = (overrides = {}) => ({
  id: 1,
  question: 'What is the past tense of "go"?',
  exerciseType: 'MULTIPLE_CHOICE',
  options: [
    { id: 1, optionKey: 'A', optionContent: 'goed', isCorrect: 0 },
    { id: 2, optionKey: 'B', optionContent: 'went', isCorrect: 1 },
    { id: 3, optionKey: 'C', optionContent: 'gone', isCorrect: 0 },
    { id: 4, optionKey: 'D', optionContent: 'going', isCorrect: 0 }
  ],
  explanation: 'The past tense of "go" is "went".',
  ...overrides
})

/**
 * Helper: mount the component and then set it to "answered" state.
 *
 * The component has a `watch(answered, ..., { immediate: true })` that calls
 * `stopTimer()` — but `stopTimer` is declared after the watch in source code.
 * When `answered: true` is passed at mount time, the immediate watcher fires
 * before `stopTimer` is initialized, causing a ReferenceError.
 *
 * The workaround is to mount with `answered: false` and then flip `isAnswered`
 * directly on the VM after mount.
 */
const mountAnswered = (extraProps = {}) => {
  const { answered, ...rest } = extraProps
  const w = shallowMount(ExerciseAnswer, {
    props: { exercise: createExercise(), ...rest }
  })
  if (answered !== false) {
    w.vm.isAnswered = true
  }
  return w
}

describe('ExerciseAnswer.vue', () => {
  let wrapper

  beforeEach(() => {
    vi.clearAllMocks()
    vi.useFakeTimers()
    mockRequest.mockResolvedValue({ code: 200, data: { isCorrect: true, message: '回答正确' } })
  })

  afterEach(() => {
    vi.useRealTimers()
    if (wrapper) {
      wrapper.unmount()
      wrapper = null
    }
  })

  // ============================================================
  // Rendering
  // ============================================================

  describe('rendering', () => {
    it('renders the exercise question', () => {
      const exercise = createExercise()
      wrapper = shallowMount(ExerciseAnswer, {
        props: { exercise }
      })
      expect(wrapper.text()).toContain('What is the past tense of "go"?')
    })

    it('renders exercise type tag', () => {
      const exercise = createExercise()
      wrapper = shallowMount(ExerciseAnswer, {
        props: { exercise }
      })
      // The el-tag stub renders as <span>, check the type text is present
      expect(wrapper.text()).toContain('选择题')
    })

    it('renders all options for multiple choice', () => {
      const exercise = createExercise()
      wrapper = shallowMount(ExerciseAnswer, {
        props: { exercise }
      })
      expect(wrapper.text()).toContain('goed')
      expect(wrapper.text()).toContain('went')
      expect(wrapper.text()).toContain('gone')
      expect(wrapper.text()).toContain('going')
    })

    it('renders textarea for FILL_BLANK type', () => {
      const exercise = createExercise({ exerciseType: 'FILL_BLANK', options: [] })
      wrapper = shallowMount(ExerciseAnswer, {
        props: { exercise }
      })
      expect(wrapper.find('.answer-input-container').exists()).toBe(true)
    })

    it('renders textarea for SHORT_ANSWER type', () => {
      const exercise = createExercise({ exerciseType: 'SHORT_ANSWER', options: [] })
      wrapper = shallowMount(ExerciseAnswer, {
        props: { exercise }
      })
      expect(wrapper.find('.answer-input-container').exists()).toBe(true)
    })

    it('renders TRUE_FALSE as radio group', () => {
      const exercise = createExercise({
        exerciseType: 'TRUE_FALSE',
        options: [
          { id: 1, optionKey: 'A', optionContent: 'True', isCorrect: 1 },
          { id: 2, optionKey: 'B', optionContent: 'False', isCorrect: 0 }
        ]
      })
      wrapper = shallowMount(ExerciseAnswer, {
        props: { exercise }
      })
      expect(wrapper.find('.options-container').exists()).toBe(true)
    })

    it('renders submit button when not answered', () => {
      wrapper = shallowMount(ExerciseAnswer, {
        props: { exercise: createExercise() }
      })
      expect(wrapper.find('.answer-actions').exists()).toBe(true)
    })

    it('hides submit button when answered', async () => {
      wrapper = mountAnswered({ answered: true })
      await wrapper.vm.$nextTick()
      expect(wrapper.find('.answer-actions').exists()).toBe(false)
    })
  })

  // ============================================================
  // Answer Feedback Display
  // ============================================================

  describe('answer feedback', () => {
    it('shows feedback section when answered', async () => {
      wrapper = mountAnswered({ answered: true, correct: true })
      wrapper.vm.isCorrect = true
      await wrapper.vm.$nextTick()
      expect(wrapper.find('.answer-feedback').exists()).toBe(true)
    })

    it('hides feedback section when not answered', () => {
      wrapper = shallowMount(ExerciseAnswer, {
        props: { exercise: createExercise(), answered: false }
      })
      expect(wrapper.find('.answer-feedback').exists()).toBe(false)
    })

    it('shows explanation when answered and explanation exists', async () => {
      wrapper = shallowMount(ExerciseAnswer, {
        props: {
          exercise: createExercise({ explanation: 'Test explanation' })
        }
      })
      wrapper.vm.isAnswered = true
      await wrapper.vm.$nextTick()
      expect(wrapper.find('.explanation').exists()).toBe(true)
      expect(wrapper.text()).toContain('Test explanation')
    })

    it('hides explanation when exercise has no explanation', async () => {
      wrapper = shallowMount(ExerciseAnswer, {
        props: {
          exercise: createExercise({ explanation: null })
        }
      })
      wrapper.vm.isAnswered = true
      await wrapper.vm.$nextTick()
      expect(wrapper.find('.explanation').exists()).toBe(false)
    })
  })

  // ============================================================
  // Option Classes (Correct/Incorrect)
  // ============================================================

  describe('option class styling', () => {
    it('adds correct class to the correct option after answering', async () => {
      const exercise = createExercise()
      wrapper = shallowMount(ExerciseAnswer, {
        props: { exercise, initialAnswer: 'A' }
      })
      wrapper.vm.isAnswered = true
      await wrapper.vm.$nextTick()

      const optionItems = wrapper.findAll('.option-item')
      // Option B (index 1) is correct
      const correctOption = optionItems[1]
      expect(correctOption.classes()).toContain('correct')
    })

    it('adds incorrect class to the user selected wrong option after answering', async () => {
      const exercise = createExercise()
      wrapper = shallowMount(ExerciseAnswer, {
        props: { exercise, initialAnswer: 'A' }
      })
      wrapper.vm.isAnswered = true
      await wrapper.vm.$nextTick()

      const optionItems = wrapper.findAll('.option-item')
      // Option A (index 0) was user's wrong choice
      const userOption = optionItems[0]
      expect(userOption.classes()).toContain('incorrect')
    })

    it('does not add correct/incorrect classes when not answered', () => {
      wrapper = shallowMount(ExerciseAnswer, {
        props: { exercise: createExercise() }
      })
      const optionItems = wrapper.findAll('.option-item')
      optionItems.forEach(item => {
        expect(item.classes()).not.toContain('correct')
        expect(item.classes()).not.toContain('incorrect')
      })
    })
  })

  // ============================================================
  // Submit Answer
  // ============================================================

  describe('submitAnswer', () => {
    it('calls API with correct data on submit', async () => {
      const exercise = createExercise()
      wrapper = shallowMount(ExerciseAnswer, {
        props: { exercise }
      })

      // Set user answer
      wrapper.vm.userAnswer = 'B'
      await wrapper.vm.submitAnswer()

      expect(mockRequest).toHaveBeenCalledWith({
        url: '/api/student/exercises/submit',
        method: 'post',
        data: expect.objectContaining({
          exerciseId: 1,
          answerContent: 'B'
        })
      })
    })

    it('includes assignmentId when provided', async () => {
      wrapper = shallowMount(ExerciseAnswer, {
        props: { exercise: createExercise(), assignmentId: 42 }
      })
      wrapper.vm.userAnswer = 'B'
      await wrapper.vm.submitAnswer()

      expect(mockRequest).toHaveBeenCalledWith({
        url: '/api/student/exercises/submit',
        method: 'post',
        data: expect.objectContaining({
          assignmentId: 42
        })
      })
    })

    it('emits answer-submit event on successful submission', async () => {
      wrapper = shallowMount(ExerciseAnswer, {
        props: { exercise: createExercise() }
      })
      wrapper.vm.userAnswer = 'B'
      await wrapper.vm.submitAnswer()

      const emitted = wrapper.emitted('answer-submit')
      expect(emitted).toBeTruthy()
      expect(emitted[0][0]).toMatchObject({
        exerciseId: 1,
        answer: 'B',
        isCorrect: true
      })
    })

    it('shows error message on API failure', async () => {
      mockRequest.mockRejectedValue(new Error('Network Error'))
      wrapper = shallowMount(ExerciseAnswer, {
        props: { exercise: createExercise() }
      })
      wrapper.vm.userAnswer = 'B'
      await wrapper.vm.submitAnswer()

      expect(mockElMessage.error).toHaveBeenCalledWith('提交失败，请重试')
    })

    it('does not submit when canSubmit is false (empty answer)', async () => {
      wrapper = shallowMount(ExerciseAnswer, {
        props: { exercise: createExercise() }
      })
      // userAnswer is '' by default
      await wrapper.vm.submitAnswer()
      expect(mockElMessage.warning).toHaveBeenCalledWith('请先完成答题')
      expect(mockRequest).not.toHaveBeenCalled()
    })

    it('does not submit when exercise.id is missing', async () => {
      wrapper = shallowMount(ExerciseAnswer, {
        props: { exercise: { id: null, question: 'test', exerciseType: 'MULTIPLE_CHOICE', options: [] } }
      })
      wrapper.vm.userAnswer = 'A'
      await wrapper.vm.submitAnswer()
      expect(mockElMessage.error).toHaveBeenCalledWith('题目信息不完整')
      expect(mockRequest).not.toHaveBeenCalled()
    })

    it('increments attemptCount on each submission', async () => {
      wrapper = shallowMount(ExerciseAnswer, {
        props: { exercise: createExercise() }
      })
      expect(wrapper.vm.attemptCount).toBe(0)

      wrapper.vm.userAnswer = 'B'
      await wrapper.vm.submitAnswer()
      expect(wrapper.vm.attemptCount).toBe(1)
    })

    it('sets isAnswered to true after successful API response', async () => {
      wrapper = shallowMount(ExerciseAnswer, {
        props: { exercise: createExercise() }
      })
      expect(wrapper.vm.isAnswered).toBe(false)

      wrapper.vm.userAnswer = 'B'
      await wrapper.vm.submitAnswer()
      expect(wrapper.vm.isAnswered).toBe(true)
    })

    it('handles API response with isCorrect false', async () => {
      mockRequest.mockResolvedValue({ code: 200, data: { isCorrect: false, message: '回答错误' } })
      wrapper = shallowMount(ExerciseAnswer, {
        props: { exercise: createExercise() }
      })
      wrapper.vm.userAnswer = 'A'
      await wrapper.vm.submitAnswer()

      expect(wrapper.vm.isCorrect).toBe(false)
      expect(wrapper.vm.isAnswered).toBe(true)
    })

    it('shows error message when API returns non-200 code', async () => {
      mockRequest.mockResolvedValue({ code: 400, message: '请求无效' })
      wrapper = shallowMount(ExerciseAnswer, {
        props: { exercise: createExercise() }
      })
      wrapper.vm.userAnswer = 'A'
      await wrapper.vm.submitAnswer()

      expect(mockElMessage.error).toHaveBeenCalledWith('请求无效')
    })
  })

  // ============================================================
  // answer-change Emit
  // ============================================================

  describe('answer-change emit', () => {
    it('emits answer-change when handleAnswerChange is called', () => {
      const exercise = createExercise()
      wrapper = shallowMount(ExerciseAnswer, {
        props: { exercise }
      })
      wrapper.vm.userAnswer = 'C'
      wrapper.vm.handleAnswerChange()

      const emitted = wrapper.emitted('answer-change')
      expect(emitted).toBeTruthy()
      expect(emitted[0][0]).toEqual({
        exerciseId: 1,
        answer: 'C'
      })
    })
  })

  // ============================================================
  // Helper Methods
  // ============================================================

  describe('helper methods', () => {
    it('getExerciseTypeText returns correct Chinese text', () => {
      wrapper = shallowMount(ExerciseAnswer, {
        props: { exercise: createExercise() }
      })
      expect(wrapper.vm.getExerciseTypeText('MULTIPLE_CHOICE')).toBe('选择题')
      expect(wrapper.vm.getExerciseTypeText('TRUE_FALSE')).toBe('判断题')
      expect(wrapper.vm.getExerciseTypeText('FILL_BLANK')).toBe('填空题')
      expect(wrapper.vm.getExerciseTypeText('SHORT_ANSWER')).toBe('简答题')
      expect(wrapper.vm.getExerciseTypeText('UNKNOWN')).toBe('UNKNOWN')
    })

    it('getDifficultyType returns correct tag type', () => {
      wrapper = shallowMount(ExerciseAnswer, {
        props: { exercise: createExercise() }
      })
      expect(wrapper.vm.getDifficultyType('MULTIPLE_CHOICE')).toBe('primary')
      expect(wrapper.vm.getDifficultyType('TRUE_FALSE')).toBe('warning')
      expect(wrapper.vm.getDifficultyType('FILL_BLANK')).toBe('info')
      expect(wrapper.vm.getDifficultyType('SHORT_ANSWER')).toBe('success')
      expect(wrapper.vm.getDifficultyType('UNKNOWN')).toBe('info')
    })

    it('formatTime formats seconds correctly', () => {
      wrapper = shallowMount(ExerciseAnswer, {
        props: { exercise: createExercise() }
      })
      expect(wrapper.vm.formatTime(30)).toBe('30秒')
      expect(wrapper.vm.formatTime(90)).toBe('1分30秒')
      expect(wrapper.vm.formatTime(0)).toBe('0秒')
      expect(wrapper.vm.formatTime(60)).toBe('1分0秒')
      expect(wrapper.vm.formatTime(125)).toBe('2分5秒')
    })
  })

  // ============================================================
  // Timer Lifecycle
  // ============================================================

  describe('timer lifecycle', () => {
    it('clears timer on unmount', () => {
      wrapper = shallowMount(ExerciseAnswer, {
        props: { exercise: createExercise() }
      })
      const clearIntervalSpy = vi.spyOn(global, 'clearInterval')
      wrapper.unmount()
      // clearInterval should have been called during cleanup
      expect(clearIntervalSpy).toHaveBeenCalled()
      clearIntervalSpy.mockRestore()
    })

    it('starts timer on mount when not answered', () => {
      const setIntervalSpy = vi.spyOn(global, 'setInterval')
      wrapper = shallowMount(ExerciseAnswer, {
        props: { exercise: createExercise() }
      })
      expect(setIntervalSpy).toHaveBeenCalled()
      setIntervalSpy.mockRestore()
    })
  })

  // ============================================================
  // canSubmit computed
  // ============================================================

  describe('canSubmit computed', () => {
    it('returns false for empty answer on multiple choice', () => {
      wrapper = shallowMount(ExerciseAnswer, {
        props: { exercise: createExercise() }
      })
      expect(wrapper.vm.canSubmit).toBe(false)
    })

    it('returns true when a choice is selected', () => {
      wrapper = shallowMount(ExerciseAnswer, {
        props: { exercise: createExercise() }
      })
      wrapper.vm.userAnswer = 'A'
      expect(wrapper.vm.canSubmit).toBe(true)
    })

    it('returns false for blank text in FILL_BLANK', () => {
      wrapper = shallowMount(ExerciseAnswer, {
        props: { exercise: createExercise({ exerciseType: 'FILL_BLANK' }) }
      })
      wrapper.vm.userAnswer = '   '
      expect(wrapper.vm.canSubmit).toBe(false)
    })

    it('returns true for non-blank text in FILL_BLANK', () => {
      wrapper = shallowMount(ExerciseAnswer, {
        props: { exercise: createExercise({ exerciseType: 'FILL_BLANK' }) }
      })
      wrapper.vm.userAnswer = 'went'
      expect(wrapper.vm.canSubmit).toBe(true)
    })
  })

  // ============================================================
  // Prop Watchers
  // ============================================================

  describe('prop watchers', () => {
    it('updates userAnswer when initialAnswer prop changes', async () => {
      wrapper = shallowMount(ExerciseAnswer, {
        props: { exercise: createExercise(), initialAnswer: 'A' }
      })
      expect(wrapper.vm.userAnswer).toBe('A')

      await wrapper.setProps({ initialAnswer: 'B' })
      expect(wrapper.vm.userAnswer).toBe('B')
    })

    it('updates isAnswered when answered prop changes from false to true', async () => {
      wrapper = shallowMount(ExerciseAnswer, {
        props: { exercise: createExercise(), answered: false }
      })
      expect(wrapper.vm.isAnswered).toBe(false)

      await wrapper.setProps({ answered: true })
      expect(wrapper.vm.isAnswered).toBe(true)
    })

    it('updates isCorrect when correct prop changes', async () => {
      wrapper = shallowMount(ExerciseAnswer, {
        props: { exercise: createExercise(), correct: false }
      })
      expect(wrapper.vm.isCorrect).toBe(false)

      await wrapper.setProps({ correct: true })
      expect(wrapper.vm.isCorrect).toBe(true)
    })
  })
})
