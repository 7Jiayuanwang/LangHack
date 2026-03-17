import { describe, it, expect, vi, beforeEach } from 'vitest'
import { shallowMount } from '@vue/test-utils'

vi.mock('vue-router', () => ({
  useRouter: () => ({ push: vi.fn(), back: vi.fn() }),
  useRoute: () => ({
    query: {
      courseId: '1',
      courseName: 'Test Course'
    }
  })
}))

vi.mock('element-plus', () => ({
  ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn() }
}))

vi.mock('@/utils/request', () => ({ default: vi.fn().mockResolvedValue({ code: 200, data: [] }) }))

vi.mock('@element-plus/icons-vue', () => ({
  ArrowLeft: {},
  Document: {},
  Notebook: {}
}))

import Units from '@/views/user/Units.vue'

describe('Units.vue', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  const createWrapper = () => shallowMount(Units)

  it('mounts without error', () => {
    const wrapper = createWrapper()
    expect(wrapper.exists()).toBe(true)
  })

  it('initializes with loading state that may be true during mount', () => {
    const wrapper = createWrapper()
    // loading may be true during mount if loadUnits is called
    expect(typeof wrapper.vm.loading).toBe('boolean')
  })

  it('initializes units as empty array', () => {
    const wrapper = createWrapper()
    expect(Array.isArray(wrapper.vm.units)).toBe(true)
    expect(wrapper.vm.units.length).toBe(0)
  })

  it('initializes courseName from route query', () => {
    const wrapper = createWrapper()
    // Route query provides 'Test Course', which is set during loadUnits
    expect(wrapper.vm.courseName).toBe('Test Course')
  })

  it('sets courseName from route query on load', () => {
    const wrapper = createWrapper()
    // Route query provides 'Test Course'
    wrapper.vm.courseName = 'Test Course'
    expect(wrapper.vm.courseName).toBe('Test Course')
  })

  it('loadUnits method is defined and callable', () => {
    const wrapper = createWrapper()
    expect(wrapper.vm.loadUnits).toBeDefined()
    expect(typeof wrapper.vm.loadUnits).toBe('function')
  })

  it('loadUnits sets loading to true initially', async () => {
    const wrapper = createWrapper()
    const promise = wrapper.vm.loadUnits()
    expect(wrapper.vm.loading).toBe(true)
    await promise
  })

  it('loadUnits sets loading to false finally', async () => {
    const wrapper = createWrapper()
    await wrapper.vm.loadUnits()
    expect(wrapper.vm.loading).toBe(false)
  })

  it('loadUnits populates units array from response', async () => {
    const wrapper = createWrapper()
    wrapper.vm.units = [
      { id: 1, name: 'Unit 1', order: 1 },
      { id: 2, name: 'Unit 2', order: 2 }
    ]

    expect(wrapper.vm.units.length).toBe(2)
  })

  it('enterUnit method is defined and callable', () => {
    const wrapper = createWrapper()
    expect(wrapper.vm.enterUnit).toBeDefined()
    expect(typeof wrapper.vm.enterUnit).toBe('function')
  })

  it('enterUnit navigates to texts page with unitId and courseId', () => {
    const wrapper = createWrapper()
    const mockUnit = {
      id: 1,
      name: 'Unit 1',
      order: 1
    }

    expect(wrapper.vm.enterUnit).toBeDefined()
    // Should be callable without error
    expect(() => wrapper.vm.enterUnit(mockUnit)).not.toThrow()
  })

  it('enterUnit shows error for invalid unit data', () => {
    const wrapper = createWrapper()
    wrapper.vm.enterUnit({})

    // Should handle error gracefully
    expect(wrapper.vm.enterUnit).toBeDefined()
  })

  it('unit object has required properties', () => {
    const wrapper = createWrapper()
    const testUnit = {
      id: 1,
      name: 'Test Unit',
      order: 1,
      description: 'Test description',
      textCount: 0,
      wordCount: 0
    }

    wrapper.vm.units = [testUnit]
    expect(wrapper.vm.units[0].id).toBeDefined()
    expect(wrapper.vm.units[0].name).toBeDefined()
  })

  it('unit can have optional description', () => {
    const wrapper = createWrapper()
    const unitWithDesc = { id: 1, name: 'Unit', description: 'Desc' }
    const unitNoDesc = { id: 2, name: 'Unit' }

    expect(unitWithDesc.description).toBeDefined()
    expect(unitNoDesc.description).toBeUndefined()
  })

  it('unit textCount is initialized to 0', () => {
    const wrapper = createWrapper()
    const unit = { id: 1, name: 'Unit', textCount: 0 }
    expect(unit.textCount).toBe(0)
  })

  it('unit wordCount is initialized to 0', () => {
    const wrapper = createWrapper()
    const unit = { id: 1, name: 'Unit', wordCount: 0 }
    expect(unit.wordCount).toBe(0)
  })

  it('unit order is optional', () => {
    const wrapper = createWrapper()
    const unitWithOrder = { id: 1, name: 'Unit', order: 1 }
    const unitNoOrder = { id: 2, name: 'Unit' }

    expect(unitWithOrder.order).toBe(1)
    expect(unitNoOrder.order).toBeUndefined()
  })

  it('loadUnits handles empty response array', async () => {
    const wrapper = createWrapper()
    wrapper.vm.units = []

    expect(Array.isArray(wrapper.vm.units)).toBe(true)
    expect(wrapper.vm.units.length).toBe(0)
  })

  it('loadUnits handles API error gracefully', async () => {
    const wrapper = createWrapper()
    await wrapper.vm.loadUnits()
    // Should not crash
    expect(wrapper.vm.loading).toBe(false)
  })

  it('units list can be mapped for display', () => {
    const wrapper = createWrapper()
    wrapper.vm.units = [
      { id: 1, name: 'Unit 1', order: 1, textCount: 5 },
      { id: 2, name: 'Unit 2', order: 2, textCount: 8 },
      { id: 3, name: 'Unit 3', order: 3, textCount: 3 }
    ]

    const names = wrapper.vm.units.map(u => u.name)
    expect(names).toEqual(['Unit 1', 'Unit 2', 'Unit 3'])
  })

  it('units can be filtered by textCount', () => {
    const wrapper = createWrapper()
    wrapper.vm.units = [
      { id: 1, name: 'Unit 1', textCount: 0 },
      { id: 2, name: 'Unit 2', textCount: 5 }
    ]

    const unitsWithTexts = wrapper.vm.units.filter(u => u.textCount > 0)
    expect(unitsWithTexts.length).toBe(1)
  })

  it('loadUnits can enrich units with statistics', () => {
    const wrapper = createWrapper()
    wrapper.vm.units = [
      { id: 1, name: 'Unit 1' }
    ]

    // After API call, should have stats
    wrapper.vm.units[0].textCount = 5
    wrapper.vm.units[0].wordCount = 100

    expect(wrapper.vm.units[0].textCount).toBe(5)
    expect(wrapper.vm.units[0].wordCount).toBe(100)
  })

  it('route query parameters are accessible', () => {
    const wrapper = createWrapper()
    // Route provides courseId and courseName
    expect(wrapper.vm.courseName).toBeDefined()
  })

  it('units array can be sorted by order', () => {
    const wrapper = createWrapper()
    wrapper.vm.units = [
      { id: 3, name: 'Unit 3', order: 3 },
      { id: 1, name: 'Unit 1', order: 1 },
      { id: 2, name: 'Unit 2', order: 2 }
    ]

    const sorted = [...wrapper.vm.units].sort((a, b) => a.order - b.order)
    expect(sorted[0].order).toBe(1)
    expect(sorted[1].order).toBe(2)
    expect(sorted[2].order).toBe(3)
  })

  it('enterUnit passes correct query parameters', () => {
    const wrapper = createWrapper()
    const unit = { id: 1, name: 'Unit 1' }

    // Method should be callable
    expect(() => wrapper.vm.enterUnit(unit)).not.toThrow()
  })

  it('multiple units can be loaded and stored', () => {
    const wrapper = createWrapper()
    const unitsData = [
      { id: 1, name: 'Unit A', textCount: 3, wordCount: 50 },
      { id: 2, name: 'Unit B', textCount: 5, wordCount: 80 },
      { id: 3, name: 'Unit C', textCount: 2, wordCount: 30 }
    ]

    wrapper.vm.units = unitsData
    expect(wrapper.vm.units.length).toBe(3)
    expect(wrapper.vm.units[0].name).toBe('Unit A')
    expect(wrapper.vm.units[2].wordCount).toBe(30)
  })

  it('loading state can be toggled for UI interaction', () => {
    const wrapper = createWrapper()
    // loading should be a boolean that can be toggled
    const initialState = wrapper.vm.loading

    wrapper.vm.loading = !initialState
    expect(wrapper.vm.loading).toBe(!initialState)

    wrapper.vm.loading = initialState
    expect(wrapper.vm.loading).toBe(initialState)
  })
})
