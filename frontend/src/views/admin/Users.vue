<template>
  <div class="users-management">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>用户管理</span>
          <div class="header-buttons">
            <el-button type="success" @click="showImportDialog">批量导入</el-button>
            <el-button type="primary" @click="showAddDialog">添加用户</el-button>
          </div>
        </div>
      </template>

      <!-- 搜索栏 -->
      <div class="search-bar">
        <el-form :inline="true" :model="searchForm" class="search-form">
          <el-form-item label="角色">
            <el-select v-model="searchForm.role" placeholder="请选择" clearable>
              <el-option label="管理员" value="ADMIN" />
              <el-option label="教师" value="TEACHER" />
              <el-option label="学生" value="STUDENT" />
              <el-option label="普通用户" value="USER" />
            </el-select>
          </el-form-item>
          <el-form-item label="关键词">
            <el-input v-model="searchForm.keyword" placeholder="用户名/姓名/邮箱" clearable />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" @click="loadUsers">搜索</el-button>
            <el-button @click="resetSearch">重置</el-button>
          </el-form-item>
        </el-form>
      </div>

      <!-- 用户列表 -->
      <div class="table-wrapper">
      <el-table v-loading="loading" :data="users" stripe>
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="username" label="用户名" />
        <el-table-column prop="realName" label="真实姓名" />
        <el-table-column prop="email" label="邮箱" />
        <el-table-column prop="role" label="角色">
          <template #default="scope">
            <el-tag :type="getRoleType(scope.row.role)">
              {{ getRoleText(scope.row.role) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdTime" label="创建时间" width="180">
          <template #default="scope">
            {{ formatTime(scope.row.createdTime) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="scope">
            <div class="action-buttons">
              <el-button 
                size="small" 
                type="primary" 
                :icon="Edit" 
                @click="editUser(scope.row)"
                title="编辑用户"
              >
                编辑
              </el-button>
              <el-dropdown trigger="click" @command="(cmd) => handleDropdownCommand(cmd, scope.row)">
                <el-button size="small" :icon="MoreFilled" circle />
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item 
                      :icon="Delete" 
                      command="delete" 
                      :disabled="scope.row.id === 1"
                    >
                      删除用户
                    </el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </div>
          </template>
        </el-table-column>
      </el-table>
      </div>

      <!-- 分页 -->
      <div class="pagination">
        <el-pagination
          v-model:current-page="pagination.current"
          v-model:page-size="pagination.size"
          :total="pagination.total"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="loadUsers"
          @current-change="loadUsers"
        />
      </div>
    </el-card>

    <!-- 添加/编辑用户对话框 -->
    <el-dialog
      v-model="dialogVisible"
      :title="isEdit ? '编辑用户' : '添加用户'"
      width="min(500px, 90%)"
      @close="resetUserForm"
    >
      <el-form
        ref="userFormRef"
        :model="userForm"
        :rules="userRules"
        label-width="80px"
      >
        <el-form-item label="用户名" prop="username">
          <el-input v-model="userForm.username" :disabled="isEdit" />
        </el-form-item>
        <el-form-item label="密码" prop="password" v-if="!isEdit">
          <el-input v-model="userForm.password" type="password" />
        </el-form-item>
        <el-form-item label="新密码" prop="password" v-if="isEdit">
          <el-input v-model="userForm.password" type="password" placeholder="留空则不修改" />
        </el-form-item>
        <el-form-item label="真实姓名" prop="realName">
          <el-input v-model="userForm.realName" />
        </el-form-item>
        <el-form-item label="邮箱" prop="email">
          <el-input v-model="userForm.email" />
        </el-form-item>
        <el-form-item label="角色" prop="role">
          <el-select v-model="userForm.role" style="width: 100%">
            <el-option label="管理员" value="ADMIN" />
            <el-option label="教师" value="TEACHER" />
            <el-option label="学生" value="STUDENT" />
            <el-option label="普通用户" value="USER" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="dialogVisible = false">取消</el-button>
          <el-button type="primary" @click="saveUser" :loading="saving">确定</el-button>
        </span>
      </template>
    </el-dialog>

    <!-- 批量导入对话框 -->
    <el-dialog
      v-model="importDialogVisible"
      title="批量导入用户"
      width="min(600px, 90%)"
    >
      <el-alert
        title="导入说明"
        type="info"
        :closable="false"
        style="margin-bottom: 20px"
      >
        <div style="line-height: 1.8">
          <p>1. 支持 .xls 和 .xlsx 格式的Excel文件</p>
          <p>2. Excel格式要求: <strong>序号 | 班级 | 学号 | 姓名</strong></p>
          <p>3. 第1、2行为标题行,数据从第3行开始</p>
          <p>4. 学号作为用户名,默认密码为 <strong>123456</strong></p>
          <p>5. 如果用户名已存在,将更新用户信息</p>
          <p>6. <strong style="color: #409EFF;">如果班级不存在，将自动创建新班级</strong></p>
          <p>7. <strong style="color: #409EFF;">用户会自动加入对应班级</strong></p>
        </div>
      </el-alert>

      <el-upload
        ref="uploadRef"
        :auto-upload="false"
        :limit="1"
        :on-change="handleFileChange"
        :on-exceed="handleExceed"
        accept=".xls,.xlsx"
        drag
      >
        <el-icon class="el-icon--upload"><upload-filled /></el-icon>
        <div class="el-upload__text">
          将文件拖到此处,或<em>点击选择文件</em>
        </div>
        <template #tip>
          <div class="el-upload__tip">
            只能上传 .xls/.xlsx 文件
          </div>
        </template>
      </el-upload>

      <div v-if="importResult" class="import-result" style="margin-top: 20px">
        <el-alert
          :title="'导入完成'"
          :type="importResult.failCount > 0 || importResult.classJoinFailedCount > 0 ? 'warning' : 'success'"
          :closable="false"
        >
          <div style="line-height: 1.8">
            <!-- 用户统计 -->
            <p><strong>用户导入结果：</strong></p>
            <p>成功创建: <strong>{{ importResult.successCount }}</strong> 个用户</p>
            <p>成功更新: <strong>{{ importResult.updateCount }}</strong> 个用户</p>
            <p v-if="importResult.failCount > 0">失败: <strong style="color: red">{{ importResult.failCount }}</strong> 个</p>

            <!-- 新增：班级统计 -->
            <el-divider />
            <p><strong>班级操作结果：</strong></p>
            <p v-if="importResult.classCreatedCount > 0">
              新建班级: <strong style="color: #67C23A">{{ importResult.classCreatedCount }}</strong> 个
              <span v-if="importResult.createdClassNames && importResult.createdClassNames.length > 0">
                ({{ importResult.createdClassNames.join(', ') }})
              </span>
            </p>
            <p>成功加入班级: <strong>{{ importResult.classJoinedCount }}</strong> 个用户</p>
            <p v-if="importResult.classJoinFailedCount > 0">
              加入班级失败: <strong style="color: #E6A23C">{{ importResult.classJoinFailedCount }}</strong> 个
            </p>

            <!-- 错误详情 -->
            <div v-if="importResult.errors && importResult.errors.length > 0">
              <el-divider />
              <p style="color: #E6A23C; font-weight: bold">错误详情:</p>
              <div style="max-height: 200px; overflow-y: auto">
                <p v-for="(error, index) in importResult.errors" :key="index" style="color: #E6A23C; margin: 2px 0">
                  {{ error }}
                </p>
              </div>
            </div>
          </div>
        </el-alert>
      </div>

      <template #footer>
        <span class="dialog-footer">
          <el-button @click="closeImportDialog">取消</el-button>
          <el-button type="primary" @click="submitImport" :loading="importing">开始导入</el-button>
        </span>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Edit, MoreFilled, Delete, UploadFilled } from '@element-plus/icons-vue'
import { getUsers, createUser, updateUser, deleteUser as deleteUserApi, importUsers } from '@/api/admin'

// 响应式数据
const users = ref([])
const dialogVisible = ref(false)
const isEdit = ref(false)
const saving = ref(false)
const loading = ref(false)
const userFormRef = ref()

// 批量导入相关
const importDialogVisible = ref(false)
const importing = ref(false)
const uploadRef = ref()
const importFile = ref(null)
const importResult = ref(null)

// 搜索表单
const searchForm = reactive({
  role: '',
  keyword: ''
})

// 分页
const pagination = reactive({
  current: 1,
  size: 10,
  total: 0
})

// 用户表单
const userForm = reactive({
  id: null,
  username: '',
  password: '',
  realName: '',
  email: '',
  role: 'STUDENT'
})

// 表单验证规则
const userRules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 3, max: 20, message: '用户名长度在 3 到 20 个字符', trigger: 'blur' }
  ],
  password: [
    {
      required: true,
      message: '请输入密码',
      trigger: 'blur',
      validator: (rule, value, callback) => {
        if (!isEdit.value && !value) {
          callback(new Error('请输入密码'))
        } else if (value && value.length < 6) {
          callback(new Error('密码长度不能少于 6 个字符'))
        } else {
          callback()
        }
      }
    }
  ],
  realName: [
    { required: true, message: '请输入真实姓名', trigger: 'blur' }
  ],
  email: [
    { type: 'email', message: '请输入正确的邮箱地址', trigger: 'blur' }
  ],
  role: [
    { required: true, message: '请选择角色', trigger: 'change' }
  ]
}

// 方法
const loadUsers = async () => {
  loading.value = true
  try {
    const params = {
      page: pagination.current,
      size: pagination.size,
      role: searchForm.role || undefined,
      keyword: searchForm.keyword || undefined
    }
    const response = await getUsers(params)
    users.value = Array.isArray(response.data?.records) ? response.data.records : []
    pagination.total = response.data?.total || 0
  } catch (error) {
    ElMessage.error('加载用户列表失败')
  } finally {
    loading.value = false
  }
}

const resetSearch = () => {
  searchForm.role = ''
  searchForm.keyword = ''
  pagination.current = 1
  loadUsers()
}

const showAddDialog = () => {
  isEdit.value = false
  dialogVisible.value = true
  resetUserForm()
}

const editUser = (user) => {
  isEdit.value = true
  userFormRef.value?.clearValidate()
  dialogVisible.value = true
  Object.assign(userForm, {
    id: user.id,
    username: user.username,
    password: '',
    realName: user.realName,
    email: user.email,
    role: user.role
  })
}

const resetUserForm = () => {
  Object.assign(userForm, {
    id: null,
    username: '',
    password: '',
    realName: '',
    email: '',
    role: 'STUDENT'
  })
}

const saveUser = async () => {
  if (!userFormRef.value) return

  await userFormRef.value.validate(async (valid) => {
    if (valid) {
      saving.value = true
      try {
        if (isEdit.value) {
          await updateUser(userForm.id, userForm)
          ElMessage.success('用户更新成功')
        } else {
          await createUser(userForm)
          ElMessage.success('用户创建成功')
        }
        dialogVisible.value = false
        loadUsers()
      } catch (error) {
        ElMessage.error('操作失败，请稍后重试')
      } finally {
        saving.value = false
      }
    }
  })
}

// 处理下拉菜单命令
const handleDropdownCommand = (command, row) => {
  switch (command) {
    case 'delete':
      deleteUser(row)
      break
  }
}

const deleteUser = async (user) => {
  try {
    await ElMessageBox.confirm(`确认删除用户 "${user.username}" 吗？`, '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })

    await deleteUserApi(user.id)
    ElMessage.success('用户删除成功')
    loadUsers()
  } catch (error) {
    if (error !== 'cancel' && error?.action !== 'cancel') {
      ElMessage.error('删除失败，请稍后重试')
    }
  }
}

const getRoleType = (role) => {
  const types = {
    'ADMIN': 'danger',
    'TEACHER': 'warning',
    'STUDENT': 'success',
    'USER': 'info'
  }
  return types[role] || ''
}

const getRoleText = (role) => {
  const texts = {
    'ADMIN': '管理员',
    'TEACHER': '教师',
    'STUDENT': '学生',
    'USER': '普通用户'
  }
  return texts[role] || role
}

const formatTime = (time) => {
  if (!time) return ''
  return new Date(time).toLocaleString()
}

// 批量导入相关方法
const showImportDialog = () => {
  importDialogVisible.value = true
  importFile.value = null
  importResult.value = null
  if (uploadRef.value) {
    uploadRef.value.clearFiles()
  }
}

const closeImportDialog = () => {
  importDialogVisible.value = false
  importFile.value = null
  importResult.value = null
  if (uploadRef.value) {
    uploadRef.value.clearFiles()
  }
}

const handleFileChange = (file) => {
  importFile.value = file.raw
  importResult.value = null
}

const handleExceed = () => {
  ElMessage.warning('只能上传一个文件')
}

const submitImport = async () => {
  if (!importFile.value) {
    ElMessage.warning('请先选择要导入的Excel文件')
    return
  }

  importing.value = true
  importResult.value = null

  try {
    const response = await importUsers(importFile.value)
    importResult.value = response.data

    ElMessage.success('导入完成')

    // 刷新用户列表
    loadUsers()
  } catch (error) {
    ElMessage.error('导入失败，请稍后重试')
  } finally {
    importing.value = false
  }
}

// 生命周期
onMounted(() => {
  loadUsers()
})
</script>

<style scoped>
.header-buttons {
  display: flex;
  gap: 10px;
}

.action-buttons {
  display: flex;
  align-items: center;
  gap: 6px;
  justify-content: flex-start;
}

.action-buttons .el-button {
  margin: 0;
  padding: 5px 12px;
  font-size: 13px;
  border-radius: 4px;
  transition: all 0.2s ease;
}

.action-buttons .el-button:hover {
  transform: translateY(-1px);
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
}

.action-buttons .el-button--circle {
  width: 28px;
  height: 28px;
  padding: 0;
  display: flex;
  align-items: center;
  justify-content: center;
}

.action-buttons .el-dropdown {
  margin-left: 2px;
}

.users-management {
  padding: 20px;
}

.table-wrapper {
  overflow-x: auto;
  -webkit-overflow-scrolling: touch;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.search-bar {
  margin-bottom: 20px;
}

.pagination {
  margin-top: 20px;
  text-align: right;
}

@media (max-width: 768px) {
  .users-management {
    padding: 12px;
  }

  .search-bar .el-form {
    display: block;
  }

  .search-bar .el-form-item {
    margin-right: 0;
    margin-bottom: 12px;
  }

  .header-buttons {
    flex-direction: column;
    gap: 8px;
  }
}
</style>