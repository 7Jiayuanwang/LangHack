import { describe, it, expect, vi, beforeEach } from 'vitest'
import { shallowMount, flushPromises } from '@vue/test-utils'

const { elMessage, elMessageBox, opsApi } = vi.hoisted(() => ({
  elMessage: {
    success: vi.fn(),
    error: vi.fn(),
    warning: vi.fn()
  },
  elMessageBox: {
    confirm: vi.fn()
  },
  opsApi: {
    getBackupList: vi.fn(),
    createBackupApi: vi.fn(),
    createCategoryBackupApi: vi.fn(),
    importBackupApi: vi.fn(),
    getBackupDetail: vi.fn(),
    getBackupPreview: vi.fn(),
    downloadBackupApi: vi.fn(),
    deleteBackupApi: vi.fn(),
    restoreBackupApi: vi.fn(),
    getBackupStats: vi.fn(),
    getBackupConfig: vi.fn(),
    updateBackupConfig: vi.fn(),
    triggerBackupCleanup: vi.fn(),
    getBackupOperationLogs: vi.fn()
  }
}))

vi.mock('element-plus', () => ({
  ElMessage: elMessage,
  ElMessageBox: elMessageBox
}))

vi.mock('@/api/ops', () => opsApi)

import Backups from '@/views/admin/Backups.vue'

describe('Backups.vue', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    opsApi.getBackupList.mockResolvedValue({
      code: 200,
      data: { records: [], total: 0 }
    })
    opsApi.getBackupStats.mockResolvedValue({
      code: 200,
      data: {
        totalCount: 0,
        successCount: 0,
        totalSize: 0,
        lastBackupTime: null,
        missingFileCount: 0,
        backupEnabled: true,
        incrementalEnabled: false
      }
    })
    opsApi.getBackupDetail.mockResolvedValue({
      code: 200,
      data: {
        id: 1,
        filename: 'backup.sql',
        status: 'COMPLETED',
        fileAvailable: true,
        readable: true
      }
    })
    opsApi.getBackupPreview.mockResolvedValue({
      code: 200,
      data: {
        preview: '-- backup\nSELECT 1;',
        truncated: false,
        lineCount: 2
      }
    })
    opsApi.importBackupApi.mockResolvedValue({
      code: 200,
      data: { id: 3, filename: 'imported_backup.sql', backupType: 'IMPORTED' }
    })
  })

  it('treats COMPLETED as successful for display and readability', async () => {
    const wrapper = shallowMount(Backups)
    await flushPromises()

    expect(wrapper.vm.getStatusText('COMPLETED')).toBe('成功')
    expect(wrapper.vm.getStatusType('COMPLETED')).toBe('success')
    expect(wrapper.vm.isSuccessfulBackup({ status: 'COMPLETED' })).toBe(true)
    expect(wrapper.vm.canReadBackup({ status: 'COMPLETED', fileAvailable: true, readable: true })).toBe(true)
  })

  it('loads detail and preview when viewing a backup', async () => {
    const wrapper = shallowMount(Backups)
    await flushPromises()

    await wrapper.vm.handleView({ id: 1 })
    await flushPromises()

    expect(opsApi.getBackupDetail).toHaveBeenCalledWith(1)
    expect(opsApi.getBackupPreview).toHaveBeenCalledWith(1, { maxLines: 200, maxChars: 50000 })
    expect(wrapper.vm.currentDetail.filename).toBe('backup.sql')
    expect(wrapper.vm.backupPreview).toContain('SELECT 1;')
    expect(wrapper.vm.detailDrawerVisible).toBe(true)
  })

  it('imports a backup file through the existing ops API', async () => {
    const wrapper = shallowMount(Backups)
    await flushPromises()

    const file = new File(['SELECT 1;'], 'imported_backup.sql', { type: 'application/sql' })
    wrapper.vm.importForm.file = file
    wrapper.vm.importForm.fileName = file.name
    wrapper.vm.importForm.mode = 'AUTO'
    wrapper.vm.importForm.category = 'school_data'

    await wrapper.vm.confirmImportBackup()
    await flushPromises()

    expect(opsApi.importBackupApi).toHaveBeenCalledTimes(1)
    expect(elMessage.success).toHaveBeenCalledWith('备份文件已导入')
  })
})
