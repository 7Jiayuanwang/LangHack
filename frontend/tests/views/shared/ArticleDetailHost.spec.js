import { describe, it, expect, beforeEach, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { useUserStore } from '@/store/user'
import ArticleDetailHost from '@/views/shared/ArticleDetailHost.vue'

vi.mock('@/views/shared/MobileLayout.vue', () => ({
  default: {
    name: 'MobileLayout',
    props: ['role'],
    template: '<div class="mobile-layout" :data-role="role"><slot /></div>'
  }
}))

vi.mock('@/views/student/Layout.vue', () => ({
  default: {
    name: 'StudentLayout',
    template: '<div class="student-layout"><slot /></div>'
  }
}))

vi.mock('@/views/user/SimpleTextView.vue', () => ({
  default: {
    name: 'SimpleTextView',
    template: '<div class="simple-text-view">public article</div>'
  }
}))

vi.mock('@/views/student/InteractiveText.vue', () => ({
  default: {
    name: 'InteractiveText',
    template: '<div class="interactive-text-view">interactive article</div>'
  }
}))

describe('ArticleDetailHost.vue', () => {
  let userStore

  beforeEach(() => {
    setActivePinia(createPinia())
    userStore = useUserStore()
    userStore.token = ''
    userStore.userRole = ''
  })

  it('未登录时渲染公开文章详情页且不包 layout', () => {
    const wrapper = mount(ArticleDetailHost)

    expect(wrapper.find('.simple-text-view').exists()).toBe(true)
    expect(wrapper.find('.interactive-text-view').exists()).toBe(false)
    expect(wrapper.find('.mobile-layout').exists()).toBe(false)
    expect(wrapper.find('.student-layout').exists()).toBe(false)
  })

  it('STUDENT 登录时渲染交互阅读页并包 StudentLayout', () => {
    userStore.token = 'test-token'
    userStore.userRole = 'STUDENT'

    const wrapper = mount(ArticleDetailHost)

    expect(wrapper.find('.interactive-text-view').exists()).toBe(true)
    expect(wrapper.find('.simple-text-view').exists()).toBe(false)
    expect(wrapper.find('.student-layout').exists()).toBe(true)
    expect(wrapper.find('.mobile-layout').exists()).toBe(false)
  })

  it('USER 登录时渲染交互阅读页并包 StudentLayout', () => {
    userStore.token = 'test-token'
    userStore.userRole = 'USER'

    const wrapper = mount(ArticleDetailHost)

    expect(wrapper.find('.interactive-text-view').exists()).toBe(true)
    expect(wrapper.find('.simple-text-view').exists()).toBe(false)
    expect(wrapper.find('.student-layout').exists()).toBe(true)
    expect(wrapper.find('.mobile-layout').exists()).toBe(false)
  })

  it('TEACHER 登录时走 MobileLayout', () => {
    userStore.token = 'test-token'
    userStore.userRole = 'TEACHER'

    const wrapper = mount(ArticleDetailHost)

    expect(wrapper.find('.interactive-text-view').exists()).toBe(true)
    expect(wrapper.find('.mobile-layout').exists()).toBe(true)
    expect(wrapper.find('.mobile-layout').attributes('data-role')).toBe('teacher')
  })

  it('ADMIN/OPS 登录时走 StudentLayout', () => {
    const cases = ['ADMIN', 'OPS']

    cases.forEach((role) => {
      userStore.token = 'test-token'
      userStore.userRole = role

      const wrapper = mount(ArticleDetailHost)

      expect(wrapper.find('.interactive-text-view').exists()).toBe(true)
      expect(wrapper.find('.student-layout').exists()).toBe(true)
      expect(wrapper.find('.mobile-layout').exists()).toBe(false)
    })
  })
})
