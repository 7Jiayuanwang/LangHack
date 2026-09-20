import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import AgentMascot from '@/components/AgentMascot.vue'

describe('AgentMascot growth stages', () => {
  it('keeps the plant companion and adds a flower only at the bloom stage', () => {
    const seed = mount(AgentMascot, { props: { stage: 'SEED', size: 62 } })
    expect(seed.classes()).toContain('stage-seed')
    expect(seed.find('.mascot-flower').exists()).toBe(false)

    const bloom = mount(AgentMascot, { props: { stage: 'BLOOM', size: 62 } })
    expect(bloom.classes()).toContain('stage-bloom')
    expect(bloom.find('.mascot-flower').exists()).toBe(true)
    expect(bloom.find('.leaf-crown').exists()).toBe(true)
    expect(bloom.attributes('aria-hidden')).toBe('true')
  })
})
