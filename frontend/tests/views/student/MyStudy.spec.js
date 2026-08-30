import { flushPromises, shallowMount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const mocks = vi.hoisted(() => ({
  routerPush: vi.fn(),
  getFavoriteArticles: vi.fn(),
  getSentenceFavorites: vi.fn(),
  deleteSentenceFavorite: vi.fn()
}))

vi.mock('vue-router', () => ({
  useRouter: () => ({ push: mocks.routerPush })
}))

vi.mock('element-plus', () => ({
  ElMessage: { success: vi.fn(), error: vi.fn() },
  ElMessageBox: { confirm: vi.fn().mockResolvedValue('confirm') }
}))

vi.mock('@/api/student', () => ({
  getFavoriteArticles: mocks.getFavoriteArticles
}))

vi.mock('@/api/sentenceFavorite', () => ({
  getSentenceFavorites: mocks.getSentenceFavorites,
  deleteSentenceFavorite: mocks.deleteSentenceFavorite
}))

vi.mock('@/composables/useFeatureToggles', () => ({
  useFeatureToggles: () => ({
    toggles: { value: {} },
    load: vi.fn().mockResolvedValue({}),
    isLoaded: () => true
  })
}))

import Favorites from '@/views/student/Favorites.vue'

describe('Favorites', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mocks.getFavoriteArticles.mockResolvedValue({ code: 200, data: { records: [], total: 0 } })
    mocks.getSentenceFavorites.mockResolvedValue({
      code: 200,
      data: {
        records: [{
          id: 7,
          textId: 12,
          sentence: 'A useful sentence.',
          sourceTitle: 'Reading One',
          createdTime: '2026-08-30 10:00:00'
        }],
        total: 1
      }
    })
  })

  it('loads paged sentence favorites and links back to the source article', async () => {
    const wrapper = shallowMount(Favorites)
    await flushPromises()

    await wrapper.findAll('.tab-button')[1].trigger('click')
    await flushPromises()

    expect(mocks.getSentenceFavorites).toHaveBeenCalledWith({ page: 1, size: 10 })
    expect(wrapper.text()).toContain('A useful sentence.')
    expect(wrapper.text()).toContain('Reading One')

    await wrapper.find('.source-link').trigger('click')
    expect(mocks.routerPush).toHaveBeenCalledWith({
      path: '/article/12',
      query: { from: 'favorites' }
    })
  })
})
