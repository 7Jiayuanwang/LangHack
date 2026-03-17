import { describe, it, expect, vi, beforeEach } from 'vitest'
import { shallowMount } from '@vue/test-utils'

vi.mock('vue-router', () => ({
  useRouter: () => ({ push: vi.fn() }),
  useRoute: () => ({ path: '/articles' })
}))

vi.mock('element-plus', () => ({
  ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn() }
}))

import Articles from '@/views/public/Articles.vue'

describe('Articles.vue (Public Articles)', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    // Mock fetch globally
    global.fetch = vi.fn().mockResolvedValue({
      ok: true,
      json: async () => ({ code: 200, data: [] })
    })
  })

  const createWrapper = () => shallowMount(Articles, {
    global: {
      mocks: {
        $route: { path: '/articles' }
      }
    }
  })

  it('mounts without error', () => {
    const wrapper = createWrapper()
    expect(wrapper.exists()).toBe(true)
  })

  it('initializes loading as true during initial fetch', () => {
    const wrapper = createWrapper()
    // loading starts as true to show loading skeleton while fetching
    expect(wrapper.vm.loading).toBe(true)
  })

  it('initializes articles as empty array', () => {
    const wrapper = createWrapper()
    expect(Array.isArray(wrapper.vm.articles)).toBe(true)
    expect(wrapper.vm.articles.length).toBe(0)
  })

  it('initializes selectedDifficulty as empty string', () => {
    const wrapper = createWrapper()
    expect(wrapper.vm.selectedDifficulty).toBe('')
  })

  it('has difficulties array with all options', () => {
    const wrapper = createWrapper()
    expect(wrapper.vm.difficulties).toBeDefined()
    expect(wrapper.vm.difficulties.length).toBe(4)
  })

  it('has coverGradients array with multiple gradients', () => {
    const wrapper = createWrapper()
    expect(Array.isArray(wrapper.vm.coverGradients)).toBe(true)
    expect(wrapper.vm.coverGradients.length).toBeGreaterThan(0)
  })

  it('getRandomGradient returns a valid gradient string', () => {
    const wrapper = createWrapper()
    const gradient = wrapper.vm.getRandomGradient()
    expect(typeof gradient).toBe('string')
    expect(gradient).toContain('gradient')
  })

  it('selectDifficulty updates selectedDifficulty', () => {
    const wrapper = createWrapper()
    wrapper.vm.selectDifficulty('EASY')
    expect(wrapper.vm.selectedDifficulty).toBe('EASY')

    wrapper.vm.selectDifficulty('HARD')
    expect(wrapper.vm.selectedDifficulty).toBe('HARD')
  })

  it('loadArticles method is defined and callable', () => {
    const wrapper = createWrapper()
    expect(wrapper.vm.loadArticles).toBeDefined()
    expect(typeof wrapper.vm.loadArticles).toBe('function')
  })

  it('readArticle method is defined and callable', () => {
    const wrapper = createWrapper()
    expect(wrapper.vm.readArticle).toBeDefined()
    expect(typeof wrapper.vm.readArticle).toBe('function')
  })

  it('getSnippet truncates long content', () => {
    const wrapper = createWrapper()
    const longContent = 'a'.repeat(200)
    const snippet = wrapper.vm.getSnippet(longContent)

    expect(snippet.length).toBeLessThanOrEqual(123)
  })

  it('getSnippet removes newlines', () => {
    const wrapper = createWrapper()
    const contentWithNewlines = 'Line1\n\nLine2\rLine3'
    const snippet = wrapper.vm.getSnippet(contentWithNewlines)

    expect(snippet).not.toContain('\n')
  })

  it('getSnippet returns empty string for invalid input', () => {
    const wrapper = createWrapper()
    expect(wrapper.vm.getSnippet(null)).toBe('')
    expect(wrapper.vm.getSnippet(undefined)).toBe('')
    expect(wrapper.vm.getSnippet('')).toBe('')
  })

  it('handleLogin method is defined and callable', () => {
    const wrapper = createWrapper()
    expect(wrapper.vm.handleLogin).toBeDefined()
    expect(() => wrapper.vm.handleLogin()).not.toThrow()
  })

  it('articles array can contain multiple items', () => {
    const wrapper = createWrapper()
    wrapper.vm.articles = [
      { id: 1, title: 'Article 1' },
      { id: 2, title: 'Article 2' },
      { id: 3, title: 'Article 3' }
    ]

    expect(wrapper.vm.articles.length).toBe(3)
  })

  it('selectedDifficulty can be cleared', () => {
    const wrapper = createWrapper()
    wrapper.vm.selectedDifficulty = 'EASY'
    expect(wrapper.vm.selectedDifficulty).toBe('EASY')

    wrapper.vm.selectedDifficulty = ''
    expect(wrapper.vm.selectedDifficulty).toBe('')
  })

  it('articles can be mapped for display', () => {
    const wrapper = createWrapper()
    wrapper.vm.articles = [
      { id: 1, title: 'A1' },
      { id: 2, title: 'A2' },
      { id: 3, title: 'A3' }
    ]

    const titles = wrapper.vm.articles.map(a => a.title)
    expect(titles).toEqual(['A1', 'A2', 'A3'])
  })
})
