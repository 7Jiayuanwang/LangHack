import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import XyPageHeader from '@/components/ui/XyPageHeader.vue'
import XySurface from '@/components/ui/XySurface.vue'
import XyMascot from '@/components/ui/XyMascot.vue'

describe('小芽设计系统基础组件', () => {
  it('页眉支持不同密度和操作区', () => {
    const wrapper = mount(XyPageHeader, {
      props: { title: '学习任务', eyebrow: 'LEARNING', description: '今日计划', variant: 'compact' },
      slots: { actions: '<button type="button">创建</button>' },
    })

    expect(wrapper.find('h1').text()).toBe('学习任务')
    expect(wrapper.text()).toContain('今日计划')
    expect(wrapper.classes()).toContain('xy-page-header--compact')
    expect(wrapper.find('button').text()).toBe('创建')
  })

  it('内容容器保留标题和自定义内容', () => {
    const wrapper = mount(XySurface, {
      props: { title: '计划列表', compact: true },
      slots: { default: '<p>一条计划</p>' },
    })

    expect(wrapper.find('h2').text()).toBe('计划列表')
    expect(wrapper.text()).toContain('一条计划')
    expect(wrapper.classes()).toContain('xy-surface--compact')
  })

  it('装饰性小芽不占用屏幕阅读器的焦点', () => {
    const decorative = mount(XyMascot)
    expect(decorative.attributes('aria-hidden')).toBe('true')
    expect(decorative.attributes('role')).toBeUndefined()

    const meaningful = mount(XyMascot, { props: { decorative: false, size: 90 } })
    expect(meaningful.attributes('role')).toBe('img')
    expect(meaningful.attributes('aria-label')).toContain('小芽')
    expect(meaningful.attributes('style')).toContain('90px')
  })
})
