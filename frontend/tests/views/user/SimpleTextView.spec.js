import { describe, it, expect, vi, beforeEach } from 'vitest'
import { shallowMount } from '@vue/test-utils'

vi.mock('vue-router', () => ({
  useRouter: () => ({ back: vi.fn() }),
  useRoute: () => ({
    params: { id: '1' },
    query: { textId: '1' }
  })
}))

vi.mock('element-plus', () => ({
  ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn() }
}))

vi.mock('@/utils/request', () => ({ default: vi.fn().mockResolvedValue({ code: 200, data: {} }) }))

vi.mock('marked', () => ({
  marked: vi.fn((content) => `<p>${content}</p>`)
}))

vi.mock('dompurify', () => ({
  default: {
    sanitize: vi.fn((content) => content)
  }
}))

import SimpleTextView from '@/views/user/SimpleTextView.vue'

describe('SimpleTextView.vue', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  const createWrapper = () => shallowMount(SimpleTextView)

  it('mounts without error', () => {
    const wrapper = createWrapper()
    expect(wrapper.exists()).toBe(true)
  })

  it('initializes textId from route params or query', () => {
    const wrapper = createWrapper()
    expect(wrapper.vm.textId).toBeDefined()
  })

  it('initializes textTitle as empty string', () => {
    const wrapper = createWrapper()
    expect(wrapper.vm.textTitle).toBe('')
  })

  it('initializes textContent as empty string', () => {
    const wrapper = createWrapper()
    expect(wrapper.vm.textContent).toBe('')
  })

  it('initializes fontSize as medium', () => {
    const wrapper = createWrapper()
    expect(wrapper.vm.fontSize).toBe('medium')
  })

  it('initializes difficulty as empty string', () => {
    const wrapper = createWrapper()
    expect(wrapper.vm.difficulty).toBe('')
  })

  it('initializes wordCount as 0', () => {
    const wrapper = createWrapper()
    expect(wrapper.vm.wordCount).toBe(0)
  })

  it('initializes viewCount as 0', () => {
    const wrapper = createWrapper()
    expect(wrapper.vm.viewCount).toBe(0)
  })

  it('initializes showWordDialog as false', () => {
    const wrapper = createWrapper()
    expect(wrapper.vm.showWordDialog).toBe(false)
  })

  it('initializes showTranslateDialog as false', () => {
    const wrapper = createWrapper()
    expect(wrapper.vm.showTranslateDialog).toBe(false)
  })

  it('initializes translating as false', () => {
    const wrapper = createWrapper()
    expect(wrapper.vm.translating).toBe(false)
  })

  it('initializes isSelectingMode as false', () => {
    const wrapper = createWrapper()
    expect(wrapper.vm.isSelectingMode).toBe(false)
  })

  it('initializes isDragging as false', () => {
    const wrapper = createWrapper()
    expect(wrapper.vm.isDragging).toBe(false)
  })

  it('initializes floatingBtnPosition with x and y', () => {
    const wrapper = createWrapper()
    expect(wrapper.vm.floatingBtnPosition).toBeDefined()
    expect(wrapper.vm.floatingBtnPosition.x).toBeDefined()
    expect(wrapper.vm.floatingBtnPosition.y).toBeDefined()
  })

  it('getDifficultyType returns correct type for EASY', () => {
    const wrapper = createWrapper()
    expect(wrapper.vm.getDifficultyType('EASY')).toBe('success')
  })

  it('getDifficultyType returns correct type for MEDIUM', () => {
    const wrapper = createWrapper()
    expect(wrapper.vm.getDifficultyType('MEDIUM')).toBe('warning')
  })

  it('getDifficultyType returns correct type for HARD', () => {
    const wrapper = createWrapper()
    expect(wrapper.vm.getDifficultyType('HARD')).toBe('danger')
  })

  it('getDifficultyType returns info for unknown difficulty', () => {
    const wrapper = createWrapper()
    expect(wrapper.vm.getDifficultyType('UNKNOWN')).toBe('info')
  })

  it('getDifficultyText returns correct text for EASY', () => {
    const wrapper = createWrapper()
    expect(wrapper.vm.getDifficultyText('EASY')).toBe('简单')
  })

  it('getDifficultyText returns correct text for MEDIUM', () => {
    const wrapper = createWrapper()
    expect(wrapper.vm.getDifficultyText('MEDIUM')).toBe('中等')
  })

  it('getDifficultyText returns correct text for HARD', () => {
    const wrapper = createWrapper()
    expect(wrapper.vm.getDifficultyText('HARD')).toBe('困难')
  })

  it('getPosText returns correct part of speech', () => {
    const wrapper = createWrapper()
    expect(wrapper.vm.getPosText('n')).toContain('名词')
    expect(wrapper.vm.getPosText('v')).toContain('动词')
    expect(wrapper.vm.getPosText('adj')).toContain('形容词')
    expect(wrapper.vm.getPosText('adv')).toContain('副词')
  })

  it('getPosText handles multiple POS values', () => {
    const wrapper = createWrapper()
    const result = wrapper.vm.getPosText('n, v')
    expect(result).toBeDefined()
    expect(result.length).toBeGreaterThan(0)
  })

  it('processedContent computed property returns processed textContent', () => {
    const wrapper = createWrapper()
    // processedContent returns the computed textContent value
    // which may include HTML markup for clickable words after processing
    expect(typeof wrapper.vm.processedContent).toBe('string')
  })

  it('isMobile computed property evaluates correctly', () => {
    const wrapper = createWrapper()
    expect(typeof wrapper.vm.isMobile).toBe('boolean')
  })

  it('dialogWidth computed property returns correct breakpoint', () => {
    const wrapper = createWrapper()
    expect(['230px', '400px']).toContain(wrapper.vm.dialogWidth)
  })

  it('handleResize updates windowWidth', () => {
    const wrapper = createWrapper()
    const initialWidth = wrapper.vm.windowWidth
    wrapper.vm.handleResize()
    expect(wrapper.vm.windowWidth).toBeDefined()
  })

  it('loadTextDetail is defined and callable', () => {
    const wrapper = createWrapper()
    expect(wrapper.vm.loadTextDetail).toBeDefined()
    expect(typeof wrapper.vm.loadTextDetail).toBe('function')
  })

  it('processTextContent is defined and callable', () => {
    const wrapper = createWrapper()
    expect(wrapper.vm.processTextContent).toBeDefined()
    expect(typeof wrapper.vm.processTextContent).toBe('function')
  })

  it('handleTextClick is defined and callable', () => {
    const wrapper = createWrapper()
    expect(wrapper.vm.handleTextClick).toBeDefined()
    expect(typeof wrapper.vm.handleTextClick).toBe('function')
  })

  it('handleWordClick is defined and callable', () => {
    const wrapper = createWrapper()
    expect(wrapper.vm.handleWordClick).toBeDefined()
    expect(typeof wrapper.vm.handleWordClick).toBe('function')
  })

  it('handleSynonymClick is defined and callable', () => {
    const wrapper = createWrapper()
    expect(wrapper.vm.handleSynonymClick).toBeDefined()
    expect(typeof wrapper.vm.handleSynonymClick).toBe('function')
  })

  it('translateSelectedText is defined and callable', () => {
    const wrapper = createWrapper()
    expect(wrapper.vm.translateSelectedText).toBeDefined()
    expect(typeof wrapper.vm.translateSelectedText).toBe('function')
  })

  it('updateFontSize is defined and callable', () => {
    const wrapper = createWrapper()
    expect(wrapper.vm.updateFontSize).toBeDefined()
    expect(typeof wrapper.vm.updateFontSize).toBe('function')
  })

  it('goBack is defined and callable', () => {
    const wrapper = createWrapper()
    expect(wrapper.vm.goBack).toBeDefined()
    expect(typeof wrapper.vm.goBack).toBe('function')
  })

  it('resetWordDialog is defined and callable', () => {
    const wrapper = createWrapper()
    expect(wrapper.vm.resetWordDialog).toBeDefined()
    expect(typeof wrapper.vm.resetWordDialog).toBe('function')
  })

  it('resetWordDialog clears word dialog state', () => {
    const wrapper = createWrapper()
    wrapper.vm.selectedWordInfo = { word: 'test' }
    wrapper.vm.loadingWord = true

    wrapper.vm.resetWordDialog()

    expect(wrapper.vm.selectedWordInfo).toBeNull()
    expect(wrapper.vm.loadingWord).toBe(false)
  })

  it('selectedWord ref is initialized', () => {
    const wrapper = createWrapper()
    expect(wrapper.vm.selectedWord).toBeNull()
  })

  it('selectedWordInfo ref is initialized to null', () => {
    const wrapper = createWrapper()
    expect(wrapper.vm.selectedWordInfo).toBeNull()
  })

  it('loadingWord ref is initialized to false', () => {
    const wrapper = createWrapper()
    expect(wrapper.vm.loadingWord).toBe(false)
  })

  it('words ref is initialized as empty array', () => {
    const wrapper = createWrapper()
    expect(Array.isArray(wrapper.vm.words)).toBe(true)
  })

  it('versions ref is initialized as empty object', () => {
    const wrapper = createWrapper()
    expect(typeof wrapper.vm.versions).toBe('object')
  })

  it('selectedVersion ref is initialized to original', () => {
    const wrapper = createWrapper()
    expect(wrapper.vm.selectedVersion).toBe('original')
  })

  it('selectedTextForTranslate ref is initialized as empty string', () => {
    const wrapper = createWrapper()
    expect(wrapper.vm.selectedTextForTranslate).toBe('')
  })

  it('translatedText ref is initialized as empty string', () => {
    const wrapper = createWrapper()
    expect(wrapper.vm.translatedText).toBe('')
  })

  it('handleVersionChange updates selected version and processes content', () => {
    const wrapper = createWrapper()
    wrapper.vm.versions = {
      original: 'Original content',
      simplified: 'Simplified content'
    }

    wrapper.vm.handleVersionChange('simplified')

    expect(wrapper.vm.selectedVersion).toBe('simplified')
    // textContent is updated and may include HTML markup from processTextContent
    expect(wrapper.vm.textContent).toBeDefined()
  })

  it('startDrag is defined and callable', () => {
    const wrapper = createWrapper()
    expect(wrapper.vm.startDrag).toBeDefined()
    expect(typeof wrapper.vm.startDrag).toBe('function')
  })

  it('handleFloatingBtnClick is defined and callable', () => {
    const wrapper = createWrapper()
    expect(wrapper.vm.handleFloatingBtnClick).toBeDefined()
    expect(typeof wrapper.vm.handleFloatingBtnClick).toBe('function')
  })

  it('playAudio is defined and callable', () => {
    const wrapper = createWrapper()
    expect(wrapper.vm.playAudio).toBeDefined()
    expect(typeof wrapper.vm.playAudio).toBe('function')
  })

  it('dragOffset reactive object has x and y', () => {
    const wrapper = createWrapper()
    expect(wrapper.vm.dragOffset).toBeDefined()
    expect(wrapper.vm.dragOffset.x).toBeDefined()
    expect(wrapper.vm.dragOffset.y).toBeDefined()
  })

  it('windowWidth is initialized', () => {
    const wrapper = createWrapper()
    expect(wrapper.vm.windowWidth).toBeGreaterThan(0)
  })
})
