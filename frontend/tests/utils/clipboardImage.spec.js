import { describe, expect, it } from 'vitest'
import { getClipboardImageFile } from '@/utils/clipboardImage'

describe('getClipboardImageFile', () => {
  it('returns null for a text-only clipboard', () => {
    const event = {
      clipboardData: {
        items: [{ kind: 'string', type: 'text/plain' }]
      }
    }

    expect(getClipboardImageFile(event)).toBeNull()
  })

  it('converts the first clipboard image into a named File', () => {
    const blob = new Blob(['image'], { type: 'image/png' })
    const event = {
      clipboardData: {
        items: [{ kind: 'file', type: 'image/png', getAsFile: () => blob }]
      }
    }

    const file = getClipboardImageFile(event)

    expect(file).toBeInstanceOf(File)
    expect(file.type).toBe('image/png')
    expect(file.name).toMatch(/^pasted-cover-\d+\.png$/)
  })

  it('ignores malformed clipboard entries', () => {
    const event = {
      clipboardData: {
        items: [
          { kind: 'file', getAsFile: () => new Blob(['x'], { type: 'image/png' }) },
          { kind: 'file', type: 'image/png', getAsFile: () => null }
        ]
      }
    }

    expect(getClipboardImageFile(event)).toBeNull()
  })
})
