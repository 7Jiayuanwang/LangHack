import { describe, expect, it, vi } from 'vitest'
import { createSseParser } from '@/utils/sse'

describe('createSseParser', () => {
  it('parses platform delta and done events across chunk boundaries', () => {
    const onEvent = vi.fn()
    const parser = createSseParser(onEvent)

    parser.feed('event: delta\r\ndata: {"content":"hel')
    parser.feed('lo"}\r\n\r\nevent: done\ndata: {}\n\n')
    parser.end()

    expect(onEvent.mock.calls.map(([event]) => event)).toEqual([
      { event: 'delta', data: { content: 'hello' } },
      { event: 'done', data: {} }
    ])
  })

  it('supports multi-line and plain-text data', () => {
    const events = []
    const parser = createSseParser(event => events.push(event))

    parser.feed('event: delta\ndata: first\ndata: second')
    parser.end()

    expect(events).toEqual([{ event: 'delta', data: 'first\nsecond' }])
  })
})
