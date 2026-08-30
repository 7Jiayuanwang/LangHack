import { describe, it, expect } from 'vitest'
import { shallowMount } from '@vue/test-utils'
import SelectionMenu from '@/components/SelectionMenu.vue'

describe('SelectionMenu.vue', () => {
  const mountMenu = () => shallowMount(SelectionMenu, { props: { visible: true, position: { x: 200, y: 100 } } })

  it('shows only underline and more trigger initially', () => {
    const wrapper = mountMenu()
    expect(wrapper.findAll('.menu-item')).toHaveLength(1)
    expect(wrapper.find('.more-trigger').exists()).toBe(true)
  })

  it('opens secondary actions from the triangle trigger', async () => {
    const wrapper = mountMenu()
    await wrapper.find('.more-trigger').trigger('click')
    expect(wrapper.text()).toContain('翻译')
    expect(wrapper.text()).toContain('收藏例句')
  })

  it('emits the unified mark action immediately', async () => {
    const wrapper = mountMenu()
    await wrapper.find('.menu-item').trigger('click')
    expect(wrapper.emitted('mark')).toHaveLength(1)
  })

  it('emits favorite sentence from expanded actions', async () => {
    const wrapper = mountMenu()
    await wrapper.find('.more-trigger').trigger('click')
    await wrapper.findAll('.menu-item')[3].trigger('click')
    expect(wrapper.emitted('favorite-sentence')).toHaveLength(1)
  })

  it('uses the custom mark label', () => {
    const wrapper = shallowMount(SelectionMenu, {
      props: { visible: true, position: { x: 200, y: 100 }, markLabel: '编辑标注' }
    })
    expect(wrapper.find('.menu-item').text()).toContain('编辑标注')
  })

  it('collapses when the selection changes while the menu remains visible', async () => {
    const wrapper = mountMenu()
    await wrapper.find('.more-trigger').trigger('click')
    await wrapper.setProps({ selectionText: 'A different sentence' })
    expect(wrapper.findAll('.menu-item')).toHaveLength(1)
  })

  it('collapses when clicking outside the menu', async () => {
    const wrapper = mountMenu()
    await wrapper.find('.more-trigger').trigger('click')
    document.body.dispatchEvent(new PointerEvent('pointerdown', { bubbles: true }))
    await wrapper.vm.$nextTick()
    expect(wrapper.findAll('.menu-item')).toHaveLength(1)
  })

  it('collapses when the browser selection changes', async () => {
    const wrapper = mountMenu()
    await wrapper.find('.more-trigger').trigger('click')
    document.dispatchEvent(new Event('selectionchange'))
    await wrapper.vm.$nextTick()
    expect(wrapper.findAll('.menu-item')).toHaveLength(1)
  })
})
