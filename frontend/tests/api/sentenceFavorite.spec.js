import { beforeEach, describe, expect, it, vi } from 'vitest'

const request = vi.hoisted(() => vi.fn())

vi.mock('@/utils/request', () => ({ default: request }))

import {
  deleteSentenceFavorite,
  getSentenceFavorites,
  toggleSentenceFavorite
} from '@/api/sentenceFavorite'

describe('sentenceFavorite API', () => {
  beforeEach(() => request.mockReset())

  it('toggles a normalized sentence favorite payload', async () => {
    await toggleSentenceFavorite(12, 'A useful sentence.')
    expect(request).toHaveBeenCalledWith({
      url: '/api/student/sentence-favorites/toggle',
      method: 'post',
      data: { textId: 12, sentence: 'A useful sentence.' }
    })
  })

  it('loads a page of sentence favorites', async () => {
    await getSentenceFavorites({ page: 2, size: 5 })
    expect(request).toHaveBeenCalledWith({
      url: '/api/student/sentence-favorites',
      method: 'get',
      params: { page: 2, size: 5 }
    })
  })

  it('deletes a favorite by id', async () => {
    await deleteSentenceFavorite(33)
    expect(request).toHaveBeenCalledWith({
      url: '/api/student/sentence-favorites/33',
      method: 'delete'
    })
  })
})
