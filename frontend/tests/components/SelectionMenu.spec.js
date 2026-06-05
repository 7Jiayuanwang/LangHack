import { describe, it, expect, beforeEach, afterEach } from 'vitest'
import { shallowMount } from '@vue/test-utils'

import SelectionMenu from '@/components/SelectionMenu.vue'

describe('SelectionMenu.vue', () => {
  let wrapper

  afterEach(() => {
    if (wrapper) {
      wrapper.unmount()
      wrapper = null
    }
  })

  // ============================================================
  // Visibility
  // ============================================================

  describe('visibility', () => {
    it('renders when visible is true', () => {
      wrapper = shallowMount(SelectionMenu, {
        props: { visible: true, position: { x: 100, y: 100 } }
      })
      expect(wrapper.find('.selection-menu').exists()).toBe(true)
    })

    it('does not render when visible is false', () => {
      wrapper = shallowMount(SelectionMenu, {
        props: { visible: false, position: { x: 100, y: 100 } }
      })
      expect(wrapper.find('.selection-menu').exists()).toBe(false)
    })
  })

  // ============================================================
  // Menu Items Rendering
  // ============================================================

  describe('menu items rendering', () => {
    beforeEach(() => {
      wrapper = shallowMount(SelectionMenu, {
        props: { visible: true, position: { x: 200, y: 200 } }
      })
    })

    it('renders five menu items', () => {
      const items = wrapper.findAll('.menu-item')
      expect(items.length).toBe(5)
    })

    it('renders copy menu item', () => {
      expect(wrapper.text()).toContain('复制')
    })

    it('renders underline menu item with default label', () => {
      expect(wrapper.text()).toContain('划线')
    })

    it('renders translate menu item', () => {
      const translateItem = wrapper.findAll('.menu-item')[2]
      expect(translateItem.text()).toContain('翻译')
    })

    it('renders annotate menu item', () => {
      const annotateItem = wrapper.findAll('.menu-item')[3]
      expect(annotateItem.text()).toContain('批注')
    })

    it('renders AI QA menu item', () => {
      expect(wrapper.text()).toContain('AI 问答')
    })
  })

  // ============================================================
  // Custom Underline Label
  // ============================================================

  describe('custom underline label', () => {
    it('uses custom underlineLabel prop', () => {
      wrapper = shallowMount(SelectionMenu, {
        props: { visible: true, position: { x: 100, y: 100 }, underlineLabel: '取消划线' }
      })
      expect(wrapper.text()).toContain('取消划线')
    })
  })

  // ============================================================
  // Click Events
  // ============================================================

  describe('click events', () => {
    beforeEach(() => {
      wrapper = shallowMount(SelectionMenu, {
        props: { visible: true, position: { x: 200, y: 200 } }
      })
    })

    it('emits "copy" when copy menu item is clicked', async () => {
      const copyItem = wrapper.findAll('.menu-item')[0]
      await copyItem.trigger('click')
      expect(wrapper.emitted('copy')).toBeTruthy()
      expect(wrapper.emitted('copy').length).toBe(1)
    })

    it('emits "underline" when underline menu item is clicked', async () => {
      const underlineItem = wrapper.findAll('.menu-item')[1]
      await underlineItem.trigger('click')
      expect(wrapper.emitted('underline')).toBeTruthy()
      expect(wrapper.emitted('underline').length).toBe(1)
    })

    it('emits "translate" when translate menu item is clicked', async () => {
      const translateItem = wrapper.findAll('.menu-item')[2]
      await translateItem.trigger('click')
      expect(wrapper.emitted('translate')).toBeTruthy()
      expect(wrapper.emitted('translate').length).toBe(1)
    })

    it('emits "ai-qa" when AI QA menu item is clicked', async () => {
      const aiQaItem = wrapper.findAll('.menu-item')[4]
      await aiQaItem.trigger('click')
      expect(wrapper.emitted('ai-qa')).toBeTruthy()
      expect(wrapper.emitted('ai-qa').length).toBe(1)
    })

    it('emits "annotate" when annotate menu item is clicked', async () => {
      const annotateItem = wrapper.findAll('.menu-item')[3]
      await annotateItem.trigger('click')
      expect(wrapper.emitted('annotate')).toBeTruthy()
      expect(wrapper.emitted('annotate').length).toBe(1)
    })
  })

  // ============================================================
  // Position / menuStyle Computed
  // ============================================================

  describe('menuStyle computed (position)', () => {
    it('sets left and top based on position prop', () => {
      wrapper = shallowMount(SelectionMenu, {
        props: { visible: true, position: { x: 300, y: 150 } }
      })
      const menu = wrapper.find('.selection-menu')
      // x=300, viewportWidth=1024 (jsdom default), MENU_HALF_WIDTH=110, SAFE_MARGIN=8
      // clampedX = Math.min(Math.max(300, 118), 906) = 300
      expect(menu.element.style.left).toBe('300px')
      expect(menu.element.style.top).toBe('150px')
    })

    it('clamps x to prevent menu from overflowing left boundary', () => {
      wrapper = shallowMount(SelectionMenu, {
        props: { visible: true, position: { x: 10, y: 50 } }
      })
      const menu = wrapper.find('.selection-menu')
      // x=10, MENU_HALF_WIDTH=110, SAFE_MARGIN=8 -> clamped to 118
      expect(menu.element.style.left).toBe('118px')
    })

    it('clamps x to prevent menu from overflowing right boundary', () => {
      // window.innerWidth in jsdom is 1024
      wrapper = shallowMount(SelectionMenu, {
        props: { visible: true, position: { x: 1020, y: 50 } }
      })
      const menu = wrapper.find('.selection-menu')
      // viewportWidth=1024, maxRight = 1024 - 110 - 8 = 906
      // clamped to 906
      expect(menu.element.style.left).toBe('906px')
    })

    it('handles default position {x:0, y:0}', () => {
      wrapper = shallowMount(SelectionMenu, {
        props: { visible: true }
      })
      const menu = wrapper.find('.selection-menu')
      // x=0 -> clamped to MENU_HALF_WIDTH + SAFE_MARGIN = 118
      expect(menu.element.style.left).toBe('118px')
      expect(menu.element.style.top).toBe('0px')
    })

    it('handles null-safe position access with nullish coalescing', () => {
      wrapper = shallowMount(SelectionMenu, {
        props: { visible: true, position: {} }
      })
      const menu = wrapper.find('.selection-menu')
      // position.x is undefined -> x=0 via ?? 0
      expect(menu.element.style.left).toBe('118px')
      expect(menu.element.style.top).toBe('0px')
    })
  })

  // ============================================================
  // Touch Events
  // ============================================================

  describe('touch events', () => {
    beforeEach(() => {
      wrapper = shallowMount(SelectionMenu, {
        props: { visible: true, position: { x: 200, y: 200 } }
      })
    })

    it('emits "copy" on touchend of copy item', async () => {
      const copyItem = wrapper.findAll('.menu-item')[0]
      await copyItem.trigger('touchend')
      expect(wrapper.emitted('copy')).toBeTruthy()
    })

    it('emits "underline" on touchend of underline item', async () => {
      const underlineItem = wrapper.findAll('.menu-item')[1]
      await underlineItem.trigger('touchend')
      expect(wrapper.emitted('underline')).toBeTruthy()
    })

    it('emits "translate" on touchend of translate item', async () => {
      const translateItem = wrapper.findAll('.menu-item')[2]
      await translateItem.trigger('touchend')
      expect(wrapper.emitted('translate')).toBeTruthy()
    })

    it('emits "ai-qa" on touchend of AI QA item', async () => {
      const aiQaItem = wrapper.findAll('.menu-item')[4]
      await aiQaItem.trigger('touchend')
      expect(wrapper.emitted('ai-qa')).toBeTruthy()
    })
  })
})
