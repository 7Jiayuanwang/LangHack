import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { ElMessage, ElMessageBox } from 'element-plus'
import SsoPendingApprovals from '@/views/admin/SsoPendingApprovals.vue'
import * as ssoAdminApi from '@/api/sso-admin'

// Mock API
vi.mock('@/api/sso-admin', () => ({
  getPendingApprovals: vi.fn(),
  approveUser: vi.fn(),
  rejectUser: vi.fn()
}))

// Mock Element Plus
vi.mock('element-plus', () => ({
  ElMessage: {
    success: vi.fn(),
    error: vi.fn()
  },
  ElMessageBox: {
    confirm: vi.fn()
  }
}))

describe('SsoPendingApprovals.vue', () => {
  let wrapper

  const mockPendingUsers = [
    {
      bindingId: 1,
      userId: 100,
      username: 'teacher001',
      realName: '张三',
      email: 'zhangsan@imau.edu.cn',
      inferredRole: 'TEACHER',
      memberOf: ['ou=教师,ou=用户,dc=imau,dc=edu,dc=cn'],
      createdTime: '2026-04-09T10:30:00'
    },
    {
      bindingId: 2,
      userId: 101,
      username: 'admin001',
      realName: '李四',
      email: 'lisi@imau.edu.cn',
      inferredRole: 'ADMIN',
      memberOf: ['ou=管理员,ou=用户,dc=imau,dc=edu,dc=cn'],
      createdTime: '2026-04-09T11:00:00'
    }
  ]

  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('should load pending users on mount', async () => {
    ssoAdminApi.getPendingApprovals.mockResolvedValue({
      code: 200,
      data: mockPendingUsers
    })

    wrapper = mount(SsoPendingApprovals)
    await flushPromises()

    expect(ssoAdminApi.getPendingApprovals).toHaveBeenCalled()
    expect(wrapper.findAll('.user-card')).toHaveLength(2)
  })

  it('should display user information correctly', async () => {
    ssoAdminApi.getPendingApprovals.mockResolvedValue({
      code: 200,
      data: [mockPendingUsers[0]]
    })

    wrapper = mount(SsoPendingApprovals)
    await flushPromises()

    const userCard = wrapper.find('.user-card')
    expect(userCard.text()).toContain('张三')
    expect(userCard.text()).toContain('teacher001')
    expect(userCard.text()).toContain('zhangsan@imau.edu.cn')
    expect(userCard.text()).toContain('TEACHER')
  })

  it('should filter users by search text', async () => {
    ssoAdminApi.getPendingApprovals.mockResolvedValue({
      code: 200,
      data: mockPendingUsers
    })

    wrapper = mount(SsoPendingApprovals)
    await flushPromises()

    const searchInput = wrapper.find('input[placeholder="搜索用户名或姓名"]')
    await searchInput.setValue('张三')

    expect(wrapper.findAll('.user-card')).toHaveLength(1)
    expect(wrapper.find('.user-card').text()).toContain('张三')
  })

  it('should filter users by role', async () => {
    ssoAdminApi.getPendingApprovals.mockResolvedValue({
      code: 200,
      data: mockPendingUsers
    })

    wrapper = mount(SsoPendingApprovals)
    await flushPromises()

    const roleSelect = wrapper.find('select')
    await roleSelect.setValue('TEACHER')

    expect(wrapper.findAll('.user-card')).toHaveLength(1)
    expect(wrapper.find('.user-card').text()).toContain('TEACHER')
  })

  it('should approve user successfully', async () => {
    ssoAdminApi.getPendingApprovals.mockResolvedValue({
      code: 200,
      data: [mockPendingUsers[0]]
    })
    ssoAdminApi.approveUser.mockResolvedValue({
      code: 200,
      message: '审批成功'
    })
    ElMessageBox.confirm.mockResolvedValue('confirm')

    wrapper = mount(SsoPendingApprovals)
    await flushPromises()

    const approveButton = wrapper.find('.approve')
    await approveButton.trigger('click')
    await flushPromises()

    expect(ElMessageBox.confirm).toHaveBeenCalled()
    expect(ssoAdminApi.approveUser).toHaveBeenCalledWith(1, 'TEACHER')
    expect(ElMessage.success).toHaveBeenCalledWith('审批成功')
    expect(wrapper.findAll('.user-card')).toHaveLength(0)
  })

  it('should reject user successfully', async () => {
    ssoAdminApi.getPendingApprovals.mockResolvedValue({
      code: 200,
      data: [mockPendingUsers[0]]
    })
    ssoAdminApi.rejectUser.mockResolvedValue({
      code: 200,
      message: '已拒绝'
    })
    ElMessageBox.confirm.mockResolvedValue('confirm')

    wrapper = mount(SsoPendingApprovals)
    await flushPromises()

    const rejectButton = wrapper.find('.reject')
    await rejectButton.trigger('click')
    await flushPromises()

    expect(ElMessageBox.confirm).toHaveBeenCalled()
    expect(ssoAdminApi.rejectUser).toHaveBeenCalledWith(1)
    expect(ElMessage.success).toHaveBeenCalledWith('已拒绝')
    expect(wrapper.findAll('.user-card')).toHaveLength(0)
  })

  it('should allow changing final role before approval', async () => {
    ssoAdminApi.getPendingApprovals.mockResolvedValue({
      code: 200,
      data: [mockPendingUsers[0]]
    })
    ssoAdminApi.approveUser.mockResolvedValue({
      code: 200,
      message: '审批成功'
    })
    ElMessageBox.confirm.mockResolvedValue('confirm')

    wrapper = mount(SsoPendingApprovals)
    await flushPromises()

    const roleSelect = wrapper.find('.role-selection select')
    await roleSelect.setValue('ADMIN')

    const approveButton = wrapper.find('.approve')
    await approveButton.trigger('click')
    await flushPromises()

    expect(ssoAdminApi.approveUser).toHaveBeenCalledWith(1, 'ADMIN')
  })

  it('should handle approval cancellation', async () => {
    ssoAdminApi.getPendingApprovals.mockResolvedValue({
      code: 200,
      data: [mockPendingUsers[0]]
    })
    ElMessageBox.confirm.mockRejectedValue('cancel')

    wrapper = mount(SsoPendingApprovals)
    await flushPromises()

    const approveButton = wrapper.find('.approve')
    await approveButton.trigger('click')
    await flushPromises()

    expect(ssoAdminApi.approveUser).not.toHaveBeenCalled()
    expect(wrapper.findAll('.user-card')).toHaveLength(1)
  })

  it('should display empty state when no pending users', async () => {
    ssoAdminApi.getPendingApprovals.mockResolvedValue({
      code: 200,
      data: []
    })

    wrapper = mount(SsoPendingApprovals)
    await flushPromises()

    expect(wrapper.find('.empty').exists()).toBe(true)
    expect(wrapper.find('.empty').text()).toBe('暂无待审批用户')
  })

  it('should handle API errors gracefully', async () => {
    ssoAdminApi.getPendingApprovals.mockRejectedValue(new Error('Network error'))

    wrapper = mount(SsoPendingApprovals)
    await flushPromises()

    expect(ElMessage.error).toHaveBeenCalledWith('加载失败')
  })

  it('should refresh user list when refresh button clicked', async () => {
    ssoAdminApi.getPendingApprovals.mockResolvedValue({
      code: 200,
      data: mockPendingUsers
    })

    wrapper = mount(SsoPendingApprovals)
    await flushPromises()

    ssoAdminApi.getPendingApprovals.mockClear()

    const refreshButton = wrapper.find('.refresh-btn')
    await refreshButton.trigger('click')
    await flushPromises()

    expect(ssoAdminApi.getPendingApprovals).toHaveBeenCalled()
  })

  it('should format date correctly', async () => {
    ssoAdminApi.getPendingApprovals.mockResolvedValue({
      code: 200,
      data: [mockPendingUsers[0]]
    })

    wrapper = mount(SsoPendingApprovals)
    await flushPromises()

    const dateText = wrapper.find('.user-card').text()
    expect(dateText).toContain('2026')
  })
})
