import { describe, it, expect, vi, beforeEach } from 'vitest'
import { shallowMount } from '@vue/test-utils'

import ExerciseBuilder from '@/components/ExerciseBuilder.vue'

describe('ExerciseBuilder.vue', () => {
  let wrapper

  const createWrapper = (props = {}) => {
    return shallowMount(ExerciseBuilder, {
      props: {
        modelValue: [],
        ...props
      },
      global: {
        stubs: {
          'el-switch': { template: '<input type="checkbox" />', props: ['modelValue'] },
          'el-button': { template: '<button @click="$emit(\'click\')"><slot /></button>' },
          'el-select': { template: '<select><slot /></select>', props: ['modelValue'] },
          'el-option': { template: '<option />' },
          'el-input': { template: '<input />', props: ['modelValue'] },
          'el-form-item': { template: '<div><slot /></div>' },
          'el-radio': { template: '<input type="radio" />' },
          'el-tag': { template: '<span><slot /></span>' }
        }
      }
    })
  }

  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('renders exercise builder container', () => {
    wrapper = createWrapper()
    expect(wrapper.find('.exercise-builder').exists()).toBe(true)
  })

  it('hasExercises is false initially with empty modelValue', () => {
    wrapper = createWrapper()
    expect(wrapper.vm.hasExercises).toBe(false)
  })

  it('hasExercises is true when modelValue has exercises', () => {
    wrapper = createWrapper({
      modelValue: [{ question: 'Q1', exerciseType: 'MULTIPLE_CHOICE', options: [] }]
    })
    expect(wrapper.vm.hasExercises).toBe(true)
  })

  it('exercises initializes from modelValue via deep copy', () => {
    const input = [{ question: 'Q1', exerciseType: 'FILL_BLANK', correctAnswer: 'A' }]
    wrapper = createWrapper({ modelValue: input })
    expect(wrapper.vm.exercises.length).toBe(1)
    expect(wrapper.vm.exercises[0].question).toBe('Q1')
    // Deep copy check
    expect(wrapper.vm.exercises[0]).not.toBe(input[0])
  })

  it('addExercise adds a new exercise with defaults', () => {
    wrapper = createWrapper()
    wrapper.vm.hasExercises = true
    wrapper.vm.addExercise()
    expect(wrapper.vm.exercises.length).toBe(1)
    expect(wrapper.vm.exercises[0].exerciseType).toBe('MULTIPLE_CHOICE')
    expect(wrapper.vm.exercises[0].question).toBe('')
    expect(wrapper.vm.exercises[0].orderNum).toBe(1)
  })

  it('addExercise creates MULTIPLE_CHOICE with 4 default options', () => {
    wrapper = createWrapper()
    wrapper.vm.hasExercises = true
    wrapper.vm.addExercise()
    expect(wrapper.vm.exercises[0].options.length).toBe(4)
    expect(wrapper.vm.exercises[0].options[0].optionKey).toBe('A')
    expect(wrapper.vm.exercises[0].options[3].optionKey).toBe('D')
  })

  it('removeExercise removes by index and reorders', () => {
    wrapper = createWrapper()
    wrapper.vm.hasExercises = true
    wrapper.vm.addExercise()
    wrapper.vm.addExercise()
    expect(wrapper.vm.exercises.length).toBe(2)
    wrapper.vm.removeExercise(0)
    expect(wrapper.vm.exercises.length).toBe(1)
    expect(wrapper.vm.exercises[0].orderNum).toBe(1)
  })

  it('clearAllExercises empties the list', () => {
    wrapper = createWrapper()
    wrapper.vm.hasExercises = true
    wrapper.vm.addExercise()
    wrapper.vm.addExercise()
    wrapper.vm.clearAllExercises()
    expect(wrapper.vm.exercises.length).toBe(0)
  })

  it('handleTypeChange resets options for MULTIPLE_CHOICE', () => {
    wrapper = createWrapper()
    wrapper.vm.hasExercises = true
    wrapper.vm.addExercise()
    wrapper.vm.exercises[0].exerciseType = 'MULTIPLE_CHOICE'
    wrapper.vm.handleTypeChange(0)
    expect(wrapper.vm.exercises[0].options.length).toBe(4)
  })

  it('handleTypeChange sets TRUE_FALSE options', () => {
    wrapper = createWrapper()
    wrapper.vm.hasExercises = true
    wrapper.vm.addExercise()
    wrapper.vm.exercises[0].exerciseType = 'TRUE_FALSE'
    wrapper.vm.handleTypeChange(0)
    expect(wrapper.vm.exercises[0].options.length).toBe(2)
  })

  it('handleTypeChange clears options for FILL_BLANK', () => {
    wrapper = createWrapper()
    wrapper.vm.hasExercises = true
    wrapper.vm.addExercise()
    wrapper.vm.exercises[0].exerciseType = 'FILL_BLANK'
    wrapper.vm.handleTypeChange(0)
    expect(wrapper.vm.exercises[0].options.length).toBe(0)
  })

  it('handleTypeChange clears options for SHORT_ANSWER', () => {
    wrapper = createWrapper()
    wrapper.vm.hasExercises = true
    wrapper.vm.addExercise()
    wrapper.vm.exercises[0].exerciseType = 'SHORT_ANSWER'
    wrapper.vm.handleTypeChange(0)
    expect(wrapper.vm.exercises[0].options.length).toBe(0)
  })

  it('handleExerciseToggle off clears exercises', () => {
    wrapper = createWrapper()
    wrapper.vm.hasExercises = true
    wrapper.vm.addExercise()
    wrapper.vm.handleExerciseToggle(false)
    expect(wrapper.vm.exercises.length).toBe(0)
  })

  it('handleExerciseToggle on adds first exercise', () => {
    wrapper = createWrapper()
    wrapper.vm.handleExerciseToggle(true)
    expect(wrapper.vm.exercises.length).toBe(1)
  })

  it('emits update:modelValue when exercises change', async () => {
    wrapper = createWrapper()
    wrapper.vm.hasExercises = true
    wrapper.vm.addExercise()
    await wrapper.vm.$nextTick()
    expect(wrapper.emitted('update:modelValue')).toBeTruthy()
  })

  it('multiple addExercise calls increment orderNum', () => {
    wrapper = createWrapper()
    wrapper.vm.hasExercises = true
    wrapper.vm.addExercise()
    wrapper.vm.addExercise()
    wrapper.vm.addExercise()
    expect(wrapper.vm.exercises[0].orderNum).toBe(1)
    expect(wrapper.vm.exercises[1].orderNum).toBe(2)
    expect(wrapper.vm.exercises[2].orderNum).toBe(3)
  })
})
