import { mount, config } from '@vue/test-utils'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import InteractiveText from '@/views/student/InteractiveText.vue'
import { createRouter, createMemoryHistory } from 'vue-router'
import { createPinia, setActivePinia } from 'pinia'

// Mock implementation of studentApi
vi.mock('@/api/student', () => ({
    default: {
        getTextDetail: vi.fn(),
        startReading: vi.fn(),
        recordLearning: vi.fn(),
        getUnderlines: vi.fn().mockResolvedValue({ code: 200, data: [] }),
        createUnderline: vi.fn().mockResolvedValue({ code: 200 }),
        getVocabularyStats: vi.fn().mockResolvedValue({ code: 200, data: { total: 100 } }),
    },
    getTextDetail: vi.fn(),
    startReading: vi.fn(),
    recordLearning: vi.fn(),
    getUnderlines: vi.fn().mockResolvedValue({ code: 200, data: [] }),
    createUnderline: vi.fn().mockResolvedValue({ code: 200 }),
    getVocabularyStats: vi.fn().mockResolvedValue({ code: 200, data: { total: 100 } }),
}))

// Mock Element Plus components
config.global.stubs = {
    ElIcon: true,
    ArrowLeft: true,
    Star: true,
    StarFilled: true,
    Clock: true,
    Tickets: true,
    CircleCheck: true,
    MagicStick: true,
    Checked: true,
    Loading: true,
    VideoPlay: true,
    InfoFilled: true,
    View: true,
    CopyDocument: true,
    EditPen: true,
    Share: true,
    ChatLineRound: true,
    SelectionMenu: true,
    ElTooltip: { template: '<div><slot /></div>' },
    ElButton: { template: '<button><slot /></button>' },
    ElDialog: { template: '<div><slot /></div>' },
    ElResult: true,
    ElDivider: true
}

describe('InteractiveText.vue', () => {
    let router

    beforeEach(() => {
        setActivePinia(createPinia())
        router = createRouter({
            history: createMemoryHistory(),
            routes: [{ path: '/student/text-learning', component: InteractiveText }]
        })
    })

    it('renders correctly', async () => {
        // Basic render test
        const wrapper = mount(InteractiveText, {
            global: {
                plugins: [router]
            }
        })

        expect(wrapper.exists()).toBe(true)
        // Check if SelectionMenu component exists (even if stubbed)
        expect(wrapper.findComponent({ name: 'SelectionMenu' }).exists()).toBe(true)
    })

    it('shows header title', async () => {
        // Mock getTextDetail return value
        const mockData = {
            title: 'Test Article Title',
            content: 'Test content',
            difficulty: 'MEDIUM',
            contentType: 'TEXT'
        }

        // We need to access the mocked module to change return value
        const studentApi = await import('@/api/student')
        studentApi.default.getTextDetail.mockResolvedValue({
            code: 200,
            data: mockData
        })

        const wrapper = mount(InteractiveText, {
            global: {
                plugins: [router]
            }
        })

        // Wait for async calls
        await new Promise(resolve => setTimeout(resolve, 100))
        await wrapper.vm.$nextTick()

        expect(wrapper.text()).toContain('Test Article Title')
    })
})
