import { describe, it, expect } from 'vitest'
import { shallowMount } from '@vue/test-utils'

import VersionSelector from '@/components/VersionSelector.vue'

describe('VersionSelector.vue', () => {
  const createWrapper = (props = {}) => {
    return shallowMount(VersionSelector, {
      props: {
        versions: { original: 'Original content', cet4: 'CET4 content' },
        selectedVersion: 'original',
        ...props
      }
    })
  }

  it('renders version tabs when versions available', () => {
    const wrapper = createWrapper()
    expect(wrapper.find('.version-tabs').exists()).toBe(true)
  })

  it('renders correct number of version buttons', () => {
    const wrapper = createWrapper()
    const buttons = wrapper.findAll('.version-tab')
    expect(buttons.length).toBe(2)
  })

  it('marks selected version as active', () => {
    const wrapper = createWrapper()
    const activeBtn = wrapper.find('.version-tab.active')
    expect(activeBtn.exists()).toBe(true)
    expect(activeBtn.text()).toBe('原文')
  })

  it('emits version-change on tab click', async () => {
    const wrapper = createWrapper()
    const buttons = wrapper.findAll('.version-tab')
    await buttons[1].trigger('click')
    expect(wrapper.emitted('version-change')).toBeTruthy()
    expect(wrapper.emitted('version-change')[0]).toEqual(['cet4'])
  })

  it('shows empty state when no versions available', () => {
    const wrapper = createWrapper({ versions: {} })
    expect(wrapper.find('.version-empty').exists()).toBe(true)
    expect(wrapper.find('.version-tabs').exists()).toBe(false)
  })

  it('uses default version labels', () => {
    const wrapper = createWrapper({
      versions: { original: 'text', cet4: 'text', cet6: 'text', academic: 'text', chinese_parallel: 'text' }
    })
    const buttons = wrapper.findAll('.version-tab')
    const labels = buttons.map(b => b.text())
    expect(labels).toContain('原文')
    expect(labels).toContain('CET-4')
    expect(labels).toContain('CET-6')
    expect(labels).toContain('学术版')
    expect(labels).toContain('中英对照')
  })

  it('uses custom version labels when provided', () => {
    const wrapper = createWrapper({
      versions: { original: 'text' },
      versionLabelsMap: { original: 'My Custom Label' }
    })
    const btn = wrapper.find('.version-tab')
    expect(btn.text()).toBe('My Custom Label')
  })

  it('filters out versions with null/undefined content', () => {
    const wrapper = createWrapper({
      versions: { original: 'text', cet4: null, cet6: undefined }
    })
    const buttons = wrapper.findAll('.version-tab')
    expect(buttons.length).toBe(1)
  })

  it('defaults selectedVersion to original', () => {
    const wrapper = shallowMount(VersionSelector, {
      props: { versions: { original: 'text' } }
    })
    expect(wrapper.props('selectedVersion')).toBe('original')
  })
})
