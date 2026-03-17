import { describe, it, expect, vi, beforeEach } from 'vitest'
import { shallowMount } from '@vue/test-utils'

const { mockGetUsers, mockCreateUser, mockUpdateUser, mockDeleteUserApi, mockImportUsers } = vi.hoisted(() => ({
  mockGetUsers: vi.fn(),
  mockCreateUser: vi.fn(),
  mockUpdateUser: vi.fn(),
  mockDeleteUserApi: vi.fn(),
  mockImportUsers: vi.fn()
}))

vi.mock('vue-router', () => ({
  useRouter: () => ({ push: vi.fn() }),
  useRoute: () => ({ params: {}, query: {} })
}))

vi.mock('element-plus', () => ({
  ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn() },
  ElMessageBox: { confirm: vi.fn() }
}))

vi.mock('@/api/admin', () => ({
  getUsers: (...args) => mockGetUsers(...args),
  createUser: (...args) => mockCreateUser(...args),
  updateUser: (...args) => mockUpdateUser(...args),
  deleteUser: (...args) => mockDeleteUserApi(...args),
  importUsers: (...args) => mockImportUsers(...args)
}))

import Users from '@/views/admin/Users.vue'
import { ElMessage, ElMessageBox } from 'element-plus'

describe('Users.vue', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockGetUsers.mockResolvedValue({
      data: { records: [], total: 0 }
    })
  })

  it('initializes with empty users array', () => {
    const wrapper = shallowMount(Users)
    expect(wrapper.vm.users).toEqual([])
  })

  it('initializes with dialogVisible false', () => {
    const wrapper = shallowMount(Users)
    expect(wrapper.vm.dialogVisible).toBe(false)
  })

  it('initializes with isEdit false', () => {
    const wrapper = shallowMount(Users)
    expect(wrapper.vm.isEdit).toBe(false)
  })

  it('has loading ref defined', () => {
    const wrapper = shallowMount(Users)
    expect(typeof wrapper.vm.loading).toBe('boolean')
  })

  it('initializes with saving false', () => {
    const wrapper = shallowMount(Users)
    expect(wrapper.vm.saving).toBe(false)
  })

  it('initializes importDialogVisible as false', () => {
    const wrapper = shallowMount(Users)
    expect(wrapper.vm.importDialogVisible).toBe(false)
  })

  it('initializes importing as false', () => {
    const wrapper = shallowMount(Users)
    expect(wrapper.vm.importing).toBe(false)
  })

  it('initializes importFile as null', () => {
    const wrapper = shallowMount(Users)
    expect(wrapper.vm.importFile).toBeNull()
  })

  it('initializes importResult as null', () => {
    const wrapper = shallowMount(Users)
    expect(wrapper.vm.importResult).toBeNull()
  })

  it('initializes userForm with default role STUDENT', () => {
    const wrapper = shallowMount(Users)
    expect(wrapper.vm.userForm.id).toBeNull()
    expect(wrapper.vm.userForm.username).toBe('')
    expect(wrapper.vm.userForm.password).toBe('')
    expect(wrapper.vm.userForm.realName).toBe('')
    expect(wrapper.vm.userForm.email).toBe('')
    expect(wrapper.vm.userForm.role).toBe('STUDENT')
  })

  it('initializes pagination with default values', () => {
    const wrapper = shallowMount(Users)
    expect(wrapper.vm.pagination.current).toBe(1)
    expect(wrapper.vm.pagination.size).toBe(10)
    expect(wrapper.vm.pagination.total).toBe(0)
  })

  it('loadUsers fetches and populates users list', async () => {
    const mockRecords = [
      { id: 1, username: 'admin', role: 'ADMIN' },
      { id: 2, username: 'student1', role: 'STUDENT' }
    ]
    mockGetUsers.mockResolvedValue({
      data: { records: mockRecords, total: 2 }
    })

    const wrapper = shallowMount(Users)
    await wrapper.vm.loadUsers()

    expect(wrapper.vm.users).toEqual(mockRecords)
    expect(wrapper.vm.pagination.total).toBe(2)
    expect(wrapper.vm.loading).toBe(false)
  })

  it('loadUsers shows error message on failure', async () => {
    mockGetUsers.mockRejectedValue(new Error('Network error'))

    const wrapper = shallowMount(Users)
    await wrapper.vm.loadUsers()

    expect(ElMessage.error).toHaveBeenCalledWith('加载用户列表失败')
    expect(wrapper.vm.loading).toBe(false)
  })

  it('showAddDialog sets isEdit false and opens dialog', () => {
    const wrapper = shallowMount(Users)
    wrapper.vm.showAddDialog()

    expect(wrapper.vm.isEdit).toBe(false)
    expect(wrapper.vm.dialogVisible).toBe(true)
  })

  it('editUser sets isEdit true and populates form with user data', () => {
    const wrapper = shallowMount(Users)
    const user = {
      id: 5,
      username: 'testuser',
      realName: 'Test User',
      email: 'test@example.com',
      role: 'TEACHER'
    }

    wrapper.vm.editUser(user)

    expect(wrapper.vm.isEdit).toBe(true)
    expect(wrapper.vm.dialogVisible).toBe(true)
    expect(wrapper.vm.userForm.id).toBe(5)
    expect(wrapper.vm.userForm.username).toBe('testuser')
    expect(wrapper.vm.userForm.password).toBe('')
    expect(wrapper.vm.userForm.realName).toBe('Test User')
    expect(wrapper.vm.userForm.email).toBe('test@example.com')
    expect(wrapper.vm.userForm.role).toBe('TEACHER')
  })

  it('resetUserForm resets all fields to defaults', () => {
    const wrapper = shallowMount(Users)
    wrapper.vm.userForm.id = 10
    wrapper.vm.userForm.username = 'modified'
    wrapper.vm.userForm.role = 'ADMIN'

    wrapper.vm.resetUserForm()

    expect(wrapper.vm.userForm.id).toBeNull()
    expect(wrapper.vm.userForm.username).toBe('')
    expect(wrapper.vm.userForm.password).toBe('')
    expect(wrapper.vm.userForm.realName).toBe('')
    expect(wrapper.vm.userForm.email).toBe('')
    expect(wrapper.vm.userForm.role).toBe('STUDENT')
  })

  it('resetSearch clears search form and resets pagination', async () => {
    const wrapper = shallowMount(Users)
    wrapper.vm.searchForm.role = 'ADMIN'
    wrapper.vm.searchForm.keyword = 'test'
    wrapper.vm.pagination.current = 5

    await wrapper.vm.resetSearch()

    expect(wrapper.vm.searchForm.role).toBe('')
    expect(wrapper.vm.searchForm.keyword).toBe('')
    expect(wrapper.vm.pagination.current).toBe(1)
  })

  it('getRoleType returns correct tag type for each role', () => {
    const wrapper = shallowMount(Users)
    expect(wrapper.vm.getRoleType('ADMIN')).toBe('danger')
    expect(wrapper.vm.getRoleType('TEACHER')).toBe('warning')
    expect(wrapper.vm.getRoleType('STUDENT')).toBe('success')
    expect(wrapper.vm.getRoleType('USER')).toBe('info')
    expect(wrapper.vm.getRoleType('UNKNOWN')).toBe('')
  })

  it('getRoleText returns correct Chinese text for each role', () => {
    const wrapper = shallowMount(Users)
    expect(wrapper.vm.getRoleText('ADMIN')).toBe('管理员')
    expect(wrapper.vm.getRoleText('TEACHER')).toBe('教师')
    expect(wrapper.vm.getRoleText('STUDENT')).toBe('学生')
    expect(wrapper.vm.getRoleText('USER')).toBe('普通用户')
    expect(wrapper.vm.getRoleText('OTHER')).toBe('OTHER')
  })

  it('formatTime returns empty string for falsy value', () => {
    const wrapper = shallowMount(Users)
    expect(wrapper.vm.formatTime(null)).toBe('')
    expect(wrapper.vm.formatTime(undefined)).toBe('')
    expect(wrapper.vm.formatTime('')).toBe('')
  })

  it('formatTime returns a string for valid date', () => {
    const wrapper = shallowMount(Users)
    const result = wrapper.vm.formatTime('2024-06-01T12:00:00')
    expect(typeof result).toBe('string')
    expect(result).toBeTruthy()
  })

  it('deleteUser calls confirm and API on user confirmation', async () => {
    ElMessageBox.confirm.mockResolvedValue('confirm')
    mockDeleteUserApi.mockResolvedValue({ code: 200 })

    const wrapper = shallowMount(Users)
    await wrapper.vm.deleteUser({ id: 2, username: 'testuser' })

    expect(ElMessageBox.confirm).toHaveBeenCalled()
    expect(mockDeleteUserApi).toHaveBeenCalledWith(2)
    expect(ElMessage.success).toHaveBeenCalledWith('用户删除成功')
  })

  it('deleteUser does not show error when user cancels', async () => {
    ElMessageBox.confirm.mockRejectedValue('cancel')

    const wrapper = shallowMount(Users)
    await wrapper.vm.deleteUser({ id: 2, username: 'testuser' })

    expect(ElMessage.error).not.toHaveBeenCalled()
  })

  it('showImportDialog opens import dialog and resets state', () => {
    const wrapper = shallowMount(Users)
    wrapper.vm.importFile = 'some-file'
    wrapper.vm.importResult = { successCount: 1 }

    wrapper.vm.showImportDialog()

    expect(wrapper.vm.importDialogVisible).toBe(true)
    expect(wrapper.vm.importFile).toBeNull()
    expect(wrapper.vm.importResult).toBeNull()
  })

  it('closeImportDialog closes import dialog and resets state', () => {
    const wrapper = shallowMount(Users)
    wrapper.vm.importDialogVisible = true
    wrapper.vm.importFile = 'some-file'
    wrapper.vm.importResult = { successCount: 1 }

    wrapper.vm.closeImportDialog()

    expect(wrapper.vm.importDialogVisible).toBe(false)
    expect(wrapper.vm.importFile).toBeNull()
    expect(wrapper.vm.importResult).toBeNull()
  })

  it('handleFileChange sets importFile and clears importResult', () => {
    const wrapper = shallowMount(Users)
    wrapper.vm.importResult = { successCount: 5 }

    wrapper.vm.handleFileChange({ raw: 'mock-file-object' })

    expect(wrapper.vm.importFile).toBe('mock-file-object')
    expect(wrapper.vm.importResult).toBeNull()
  })

  it('handleExceed shows warning message', () => {
    const wrapper = shallowMount(Users)
    wrapper.vm.handleExceed()

    expect(ElMessage.warning).toHaveBeenCalledWith('只能上传一个文件')
  })

  it('submitImport warns if no file selected', async () => {
    const wrapper = shallowMount(Users)
    wrapper.vm.importFile = null

    await wrapper.vm.submitImport()

    expect(ElMessage.warning).toHaveBeenCalledWith('请先选择要导入的Excel文件')
    expect(mockImportUsers).not.toHaveBeenCalled()
  })

  it('submitImport calls importUsers and sets result on success', async () => {
    const mockResult = { successCount: 10, updateCount: 2, failCount: 0 }
    mockImportUsers.mockResolvedValue({ data: mockResult })

    const wrapper = shallowMount(Users)
    wrapper.vm.importFile = 'mock-file'

    await wrapper.vm.submitImport()

    expect(mockImportUsers).toHaveBeenCalledWith('mock-file')
    expect(wrapper.vm.importResult).toEqual(mockResult)
    expect(ElMessage.success).toHaveBeenCalledWith('导入完成')
    expect(wrapper.vm.importing).toBe(false)
  })

  it('submitImport shows error on failure', async () => {
    mockImportUsers.mockRejectedValue(new Error('Import failed'))

    const wrapper = shallowMount(Users)
    wrapper.vm.importFile = 'mock-file'

    await wrapper.vm.submitImport()

    expect(ElMessage.error).toHaveBeenCalledWith('导入失败，请稍后重试')
    expect(wrapper.vm.importing).toBe(false)
  })

  it('handleDropdownCommand dispatches delete command', async () => {
    ElMessageBox.confirm.mockResolvedValue('confirm')
    mockDeleteUserApi.mockResolvedValue({ code: 200 })

    const wrapper = shallowMount(Users)
    await wrapper.vm.handleDropdownCommand('delete', { id: 3, username: 'user3' })

    expect(ElMessageBox.confirm).toHaveBeenCalled()
  })
})
