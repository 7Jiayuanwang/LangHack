import { describe, it, expect, vi, beforeEach } from 'vitest'
import { nextTick } from 'vue'
import { shallowMount } from '@vue/test-utils'

vi.mock('element-plus', () => ({
  ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn() },
  ElMessageBox: { confirm: vi.fn().mockResolvedValue('confirm') }
}))

import Assignments from '@/views/teacher/Assignments.vue'

describe('Teacher Assignments.vue', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.spyOn(Storage.prototype, 'getItem').mockReturnValue('mock-token')
    global.fetch = vi.fn().mockResolvedValue({
      ok: true,
      json: () => Promise.resolve({ code: 200, data: [] })
    })
  })

  const createWrapper = (options = {}) => shallowMount(Assignments, options)

  it('has loading as false', () => {
    const wrapper = createWrapper()
    expect(wrapper.vm.loading).toBe(false)
  })

  it('has empty assignments array', () => {
    const wrapper = createWrapper()
    expect(wrapper.vm.assignments).toEqual([])
  })

  it('has empty classes array', () => {
    const wrapper = createWrapper()
    expect(wrapper.vm.classes).toEqual([])
  })

  it('has dialogVisible as false', () => {
    const wrapper = createWrapper()
    expect(wrapper.vm.dialogVisible).toBe(false)
  })

  it('has progressDialogVisible as false', () => {
    const wrapper = createWrapper()
    expect(wrapper.vm.progressDialogVisible).toBe(false)
  })

  it('has isEdit as false', () => {
    const wrapper = createWrapper()
    expect(wrapper.vm.isEdit).toBe(false)
  })

  it('has saving as false', () => {
    const wrapper = createWrapper()
    expect(wrapper.vm.saving).toBe(false)
  })

  it('has assignmentForm reactive object', () => {
    const wrapper = createWrapper()
    expect(wrapper.vm.assignmentForm).toBeDefined()
    expect(wrapper.vm.assignmentForm.title).toBe('')
  })

  it('has assignmentRules defined', () => {
    const wrapper = createWrapper()
    expect(wrapper.vm.assignmentRules).toBeDefined()
    expect(wrapper.vm.assignmentRules.title).toBeDefined()
  })

  it('allows any whole-minute study duration within the valid range', () => {
    const wrapper = createWrapper()
    const validator = wrapper.vm.assignmentRules.idealStudyTimeMinutes[1].validator
    const callback = vi.fn()

    validator(null, 1, callback)

    expect(callback).toHaveBeenCalledWith()
  })

  it('has filterForm reactive object', () => {
    const wrapper = createWrapper()
    expect(wrapper.vm.filterForm).toBeDefined()
  })

  it('has unitContent reactive object', () => {
    const wrapper = createWrapper()
    expect(wrapper.vm.unitContent).toBeDefined()
    expect(wrapper.vm.unitContent.texts).toEqual([])
  })

  it('has studentProgress as empty array', () => {
    const wrapper = createWrapper()
    expect(wrapper.vm.studentProgress).toEqual([])
  })

  it('uses markdown content when calculating an article character count', () => {
    const wrapper = createWrapper()
    expect(wrapper.vm.getTextCharacterCount({ content: '', markdownContent: '# Reading' })).toBe(9)
  })

  it('prefers plain text content when calculating an article character count', () => {
    const wrapper = createWrapper()
    expect(wrapper.vm.getTextCharacterCount({ content: 'Read', markdownContent: '# Longer markdown' })).toBe(4)
  })

  it('uses iExplore content sections for integrated course textbooks', () => {
    const wrapper = createWrapper()
    wrapper.vm.assignmentForm.bookVolume = '综合教程1'

    expect(wrapper.vm.contentSectionOptions).toEqual(['iExplore1', 'iExplore2'])
  })

  it('uses Understanding China content sections for the Understanding China textbook', () => {
    const wrapper = createWrapper()
    wrapper.vm.assignmentForm.bookVolume = '理解当代中国'

    expect(wrapper.vm.contentSectionOptions).toEqual(['Understanding China', 'Exploring the world'])
  })

  it('filters article options by textbook content section', async () => {
    const wrapper = createWrapper()
    wrapper.vm.assignmentForm.assignmentType = 'TEXTS'
    wrapper.vm.assignmentForm.articleType = 'INNER'
    wrapper.vm.assignmentForm.bookVolume = '综合教程2'
    wrapper.vm.assignmentForm.contentSection = 'iExplore2'

    await wrapper.vm.loadArticles()

    expect(global.fetch).toHaveBeenCalledWith(
      expect.stringContaining('contentSection=iExplore2'),
      expect.any(Object)
    )
  })

  it('does not require a course unit for selected text assignments', async () => {
    const wrapper = createWrapper()
    wrapper.vm.assignmentForm.unitId = 9

    await wrapper.vm.onAssignmentTypeChange('TEXTS')

    expect(wrapper.vm.assignmentForm.unitId).toBeNull()
    expect(wrapper.vm.assignmentRules.unitId[0].validator).toBeTypeOf('function')
  })

  it('renders the markdown article character count in the assignment selector', async () => {
    const wrapper = createWrapper({
      global: {
        stubs: {
          ElCheckboxGroup: { template: '<div><slot /></div>' }
        }
      }
    })
    wrapper.vm.dialogVisible = true
    wrapper.vm.assignmentForm.assignmentType = 'TEXTS'
    wrapper.vm.unitContent.texts = [{
      id: 1,
      title: 'Markdown article',
      difficulty: 'MEDIUM',
      content: '',
      markdownContent: '# Reading'
    }]
    await nextTick()

    expect(wrapper.text()).toContain('字数：9')
  })
})
