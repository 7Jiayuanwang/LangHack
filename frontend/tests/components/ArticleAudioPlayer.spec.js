import { beforeEach, afterEach, describe, expect, it, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'

const status = vi.fn()
const generate = vi.fn()
const download = vi.fn()
const insightStatus = vi.fn()
const insightGenerate = vi.fn()
const insightDownload = vi.fn()
vi.mock('@/api/articleAudio', () => ({
  getArticleAudioStatus: (...args) => status(...args),
  generateArticleAudio: (...args) => generate(...args),
  downloadArticleAudio: (...args) => download(...args),
  getListeningInsightAudioStatus: (...args) => insightStatus(...args),
  generateListeningInsightAudio: (...args) => insightGenerate(...args),
  downloadListeningInsightAudio: (...args) => insightDownload(...args)
}))

import ArticleAudioPlayer from '@/components/ArticleAudioPlayer.vue'

const props = { textId: 42, version: 'original', enableInsights: false }
const cached = { ready: true, source: 'offline', hash: 'abc', audioUrl: '/api/audio/abc' }

describe('ArticleAudioPlayer', () => {
  beforeEach(() => {
    status.mockReset()
    generate.mockReset()
    download.mockReset()
    insightStatus.mockReset()
    insightGenerate.mockReset()
    insightDownload.mockReset()
    status.mockResolvedValue({ data: { ready: false, source: 'offline' } })
    generate.mockResolvedValue({ data: cached })
    download.mockResolvedValue({ data: new Blob(['RIFF'], { type: 'audio/wav' }) })
    insightStatus.mockResolvedValue({ data: {
      status: 'READY', audioReady: true, hash: 'insight-hash',
      points: [{ expression: 'resilience' }]
    } })
    insightGenerate.mockResolvedValue({ data: {
      status: 'READY', audioReady: true, hash: 'insight-hash',
      points: [{ expression: 'resilience' }]
    } })
    insightDownload.mockResolvedValue({ data: new Blob(['RIFF'], { type: 'audio/wav' }) })
    URL.createObjectURL = vi.fn(() => 'blob:article-audio')
    URL.revokeObjectURL = vi.fn()
    HTMLMediaElement.prototype.play = vi.fn().mockResolvedValue(undefined)
    HTMLMediaElement.prototype.pause = vi.fn()
    HTMLMediaElement.prototype.load = vi.fn()
  })

  afterEach(() => vi.useRealTimers())

  it('generates only when missing, then plays the saved file', async () => {
    const wrapper = mount(ArticleAudioPlayer, { props })
    await flushPromises()
    await wrapper.find('[aria-label="播放音频"]').trigger('click')
    await flushPromises()
    expect(generate).toHaveBeenCalledOnce()
    expect(download).toHaveBeenCalledWith(42, 'original', 'abc', null)
    expect(HTMLMediaElement.prototype.play).toHaveBeenCalledOnce()
    expect(wrapper.text()).toContain('音频已保存')
    await wrapper.find('[aria-label="播放音频"]').trigger('click')
    await flushPromises()
    expect(generate).toHaveBeenCalledOnce()
    wrapper.unmount()
    expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:article-audio')
  })

  it('uses an uploaded original recording without generating another', async () => {
    status.mockResolvedValue({ data: { ready: true, source: 'uploaded', audioUrl: '/api/media/7/download' } })
    const wrapper = mount(ArticleAudioPlayer, { props })
    await flushPromises()
    await wrapper.find('[aria-label="播放音频"]').trigger('click')
    await flushPromises()
    expect(generate).not.toHaveBeenCalled()
    expect(download).not.toHaveBeenCalled()
    expect(wrapper.find('audio').attributes('src')).toBe('/api/media/7/download')
    wrapper.unmount()
  })

  it('sets playback rate and stops after selected playing time', async () => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date('2026-09-20T12:00:00Z'))
    const wrapper = mount(ArticleAudioPlayer, { props })
    await flushPromises()
    const selects = wrapper.findAll('select')
    await selects[0].setValue('1.5')
    expect(wrapper.find('audio').element.playbackRate).toBe(1.5)
    await selects[1].setValue('10')
    await wrapper.find('audio').trigger('play')
    await vi.advanceTimersByTimeAsync(10 * 60 * 1000)
    expect(HTMLMediaElement.prototype.pause).toHaveBeenCalled()
    expect(wrapper.text()).toContain('已自动停止')
    wrapper.unmount()
  })

  it('resets cached playback when switching article versions', async () => {
    status.mockResolvedValue({ data: cached })
    const wrapper = mount(ArticleAudioPlayer, { props })
    await flushPromises()
    await wrapper.find('[aria-label="播放音频"]').trigger('click')
    await flushPromises()
    await wrapper.setProps({ version: 'cet4' })
    await flushPromises()
    expect(status).toHaveBeenLastCalledWith(42, 'cet4', null)
    expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:article-audio')
    wrapper.unmount()
  })

  it('prepares cached knowledge points and continues automatically after the article ends', async () => {
    status.mockResolvedValue({ data: cached })
    URL.createObjectURL = vi.fn()
      .mockReturnValueOnce('blob:article-audio')
      .mockReturnValueOnce('blob:insight-audio')
    const wrapper = mount(ArticleAudioPlayer, { props: { ...props, enableInsights: true } })
    await flushPromises()
    await wrapper.find('[aria-label="播放音频"]').trigger('click')
    await flushPromises()
    await wrapper.find('audio').trigger('play')
    await flushPromises()
    expect(insightStatus).toHaveBeenCalledWith(42, 'original', null)
    expect(insightDownload).toHaveBeenCalledWith(42, 'original', 'insight-hash', null)
    await wrapper.find('audio').trigger('ended')
    await flushPromises()
    expect(wrapper.text()).toContain('听小芽讲知识点')
    expect(wrapper.find('audio').attributes('src')).toBe('blob:insight-audio')
    expect(HTMLMediaElement.prototype.play).toHaveBeenCalledTimes(2)
    wrapper.unmount()
  })

  it('does not continue to insights when sleep mode has stopped playback', async () => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date('2026-09-20T12:00:00Z'))
    status.mockResolvedValue({ data: cached })
    const wrapper = mount(ArticleAudioPlayer, { props: { ...props, enableInsights: true } })
    await flushPromises()
    await wrapper.find('[aria-label="播放音频"]').trigger('click')
    await flushPromises()
    await wrapper.findAll('select')[1].setValue('10')
    await wrapper.find('audio').trigger('play')
    await flushPromises()
    await vi.advanceTimersByTimeAsync(10 * 60 * 1000)
    await wrapper.find('audio').trigger('ended')
    await flushPromises()
    expect(wrapper.text()).toContain('听文章')
    expect(wrapper.text()).not.toContain('听小芽讲知识点')
    wrapper.unmount()
  })

  it('lets the learner cancel knowledge-point continuation while generation is pending', async () => {
    vi.useFakeTimers()
    insightStatus.mockResolvedValueOnce({ data: { status: 'PENDING', audioReady: false } })
    const wrapper = mount(ArticleAudioPlayer, { props: { ...props, enableInsights: true } })
    await flushPromises()
    await wrapper.find('[aria-label="播放音频"]').trigger('click')
    await flushPromises()
    await wrapper.find('audio').trigger('play')
    await flushPromises()
    await wrapper.find('audio').trigger('ended')
    await flushPromises()
    await wrapper.find('[aria-label="取消知识点续播"]').trigger('click')
    await vi.advanceTimersByTimeAsync(2000)
    await flushPromises()
    expect(wrapper.text()).toContain('已取消自动续播')
    expect(wrapper.text()).not.toContain('听小芽讲知识点')
    wrapper.unmount()
  })
})
