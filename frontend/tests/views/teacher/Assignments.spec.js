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

  it('creates one assignment for each selected class', async () => {
    const wrapper = createWrapper()
    wrapper.vm.assignmentForm.title = 'Shared assignment'
    wrapper.vm.assignmentForm.classId = [11, 22]
    wrapper.vm.assignmentForm.unitId = 7
    wrapper.vm.assignmentForm.deadline = new Date(Date.now() + 3600000)
    wrapper.vm.assignmentFormRef = {
      validate: async (callback) => callback(true)
    }
    global.fetch.mockReset().mockResolvedValue({
      ok: true,
      json: () => Promise.resolve({ code: 200, data: { records: [] } })
    })

    await wrapper.vm.saveAssignment()

    const createRequests = global.fetch.mock.calls.filter(([url, options]) =>
      url === '/api/assignments' && options?.method === 'POST'
    )
    expect(createRequests).toHaveLength(2)
    expect(JSON.parse(createRequests[0][1].body).classId).toBe(11)
    expect(JSON.parse(createRequests[1][1].body).classId).toBe(22)
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

  it('automatically selects matching in-class articles', async () => {
    const wrapper = createWrapper()
    wrapper.vm.assignmentForm.assignmentType = 'TEXTS'
    wrapper.vm.assignmentForm.articleType = 'INNER'
    global.fetch.mockResolvedValueOnce({
      ok: true,
      json: () => Promise.resolve({ code: 200, data: [{ id: 4 }, { id: 9 }] })
    })

    await wrapper.vm.loadArticles()

    expect(wrapper.vm.assignmentForm.textIds).toEqual([4, 9])
  })

  it('does not auto-select extracurricular articles', async () => {
    const wrapper = createWrapper()
    wrapper.vm.assignmentForm.assignmentType = 'TEXTS'
    wrapper.vm.assignmentForm.articleType = 'OUTER'
    global.fetch.mockResolvedValueOnce({
      ok: true,
      json: () => Promise.resolve({ code: 200, data: [{ id: 4 }, { id: 9 }] })
    })

    await wrapper.vm.loadArticles()

    expect(wrapper.vm.assignmentForm.textIds).toEqual([])
  })

  it('filters extracurricular articles by keyword, difficulty, and tags', async () => {
    const wrapper = createWrapper()
    wrapper.vm.assignmentForm.assignmentType = 'TEXTS'
    wrapper.vm.assignmentForm.articleType = 'OUTER'
    wrapper.vm.unitContent.texts = [
      { id: 1, title: 'Climate change', difficulty: 'HARD', tags: 'environment, science', content: 'one two' },
      { id: 2, title: 'Study habits', difficulty: 'EASY', tags: ['education'], content: 'one two' }
    ]

    wrapper.vm.outerArticleTag = 'environment'
    expect(wrapper.vm.filteredArticleTexts.map(text => text.id)).toEqual([1])
    wrapper.vm.outerArticleTag = ''
    wrapper.vm.outerArticleDifficulty = 'EASY'
    expect(wrapper.vm.filteredArticleTexts.map(text => text.id)).toEqual([2])
    wrapper.vm.outerArticleDifficulty = ''
    wrapper.vm.outerArticleKeyword = 'science'
    expect(wrapper.vm.filteredArticleTexts.map(text => text.id)).toEqual([1])
  })

  it('selects all currently visible extracurricular articles without dropping hidden selections', () => {
    const wrapper = createWrapper()
    wrapper.vm.assignmentForm.assignmentType = 'TEXTS'
    wrapper.vm.assignmentForm.articleType = 'OUTER'
    wrapper.vm.unitContent.texts = [
      { id: 1, title: 'First', tags: 'news' },
      { id: 2, title: 'Second', tags: 'science' }
    ]
    wrapper.vm.assignmentForm.textIds = [2]
    wrapper.vm.outerArticleTag = 'news'

    wrapper.vm.toggleAllFilteredArticles()

    expect(wrapper.vm.assignmentForm.textIds).toEqual([2, 1])
  })

  it('estimates at least one minute for short articles', () => {
    const wrapper = createWrapper()
    expect(wrapper.vm.getEstimatedReadingMinutes({ content: 'short article' })).toBe(1)
    expect(wrapper.vm.getTextTags({ tags: 'news， science;reading' })).toEqual(['news', 'science', 'reading'])
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
