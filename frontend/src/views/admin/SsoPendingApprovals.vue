<template>
  <div class="pending-approvals">
    <h2>SSO 用户权限确认</h2>

    <div class="filter-section">
      <input v-model="searchText" placeholder="搜索用户名或姓名" />
      <select v-model="roleFilter">
        <option value="">全部角色</option>
        <option value="ADMIN">管理员</option>
        <option value="OPS">运维</option>
        <option value="TEACHER">教师</option>
        <option value="STUDENT">学生</option>
        <option value="USER">普通用户</option>
      </select>
      <button @click="loadPendingUsers" class="refresh-btn">刷新</button>
    </div>

    <div v-if="loading" class="loading">加载中...</div>

    <div v-else-if="filteredUsers.length === 0" class="empty">
      暂无待审批用户
    </div>

    <div v-else class="user-list">
      <div v-for="user in filteredUsers" :key="user.bindingId" class="user-card">
        <div class="user-info">
          <h3>{{ user.realName }} ({{ user.username }})</h3>
          <p><strong>邮箱：</strong>{{ user.email || '未提供' }}</p>
          <p><strong>推断角色：</strong><span :class="'role-' + user.inferredRole">{{ user.inferredRole }}</span></p>
          <p><strong>组织归属：</strong>{{ user.memberOf.join(', ') || '未提供' }}</p>
          <p><strong>注册时间：</strong>{{ formatDate(user.createdTime) }}</p>
        </div>

        <div class="actions">
          <div class="role-selection">
            <label>最终角色：</label>
            <select v-model="user.finalRole">
              <option :value="user.inferredRole">保持推断角色 ({{ user.inferredRole }})</option>
              <option value="STUDENT">学生</option>
              <option value="TEACHER">教师</option>
              <option value="USER">普通用户</option>
              <option value="ADMIN">管理员</option>
              <option value="OPS">运维</option>
            </select>
          </div>

          <div class="action-buttons">
            <button @click="handleApprove(user)" class="approve">确认</button>
            <button @click="handleReject(user)" class="reject">拒绝</button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getPendingApprovals, approveUser, rejectUser } from '@/api/sso-admin'

const searchText = ref('')
const roleFilter = ref('')
const users = ref([])
const loading = ref(false)

const filteredUsers = computed(() => {
  return users.value.filter(user => {
    const matchesSearch = !searchText.value ||
      user.username.toLowerCase().includes(searchText.value.toLowerCase()) ||
      (user.realName && user.realName.toLowerCase().includes(searchText.value.toLowerCase()))

    const matchesRole = !roleFilter.value || user.inferredRole === roleFilter.value

    return matchesSearch && matchesRole
  })
})

onMounted(() => {
  loadPendingUsers()
})

const loadPendingUsers = async () => {
  loading.value = true
  try {
    const response = await getPendingApprovals()
    if (response.code === 200) {
      users.value = response.data.map(user => ({
        ...user,
        finalRole: user.inferredRole || 'USER'
      }))
    } else {
      ElMessage.error(response.message || '加载失败')
    }
  } catch (error) {
    console.error('加载待审批用户失败:', error)
    ElMessage.error('加载失败')
  } finally {
    loading.value = false
  }
}

const handleApprove = async (user) => {
  try {
    await ElMessageBox.confirm(
      `确认将用户 ${user.username} 的角色设置为 ${user.finalRole}？`,
      '确认审批',
      {
        confirmButtonText: '确认',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )

    const response = await approveUser(user.bindingId, user.finalRole)
    if (response.code === 200) {
      ElMessage.success('审批成功')
      users.value = users.value.filter(u => u.bindingId !== user.bindingId)
    } else {
      ElMessage.error(response.message || '审批失败')
    }
  } catch (error) {
    if (error !== 'cancel') {
      console.error('审批失败:', error)
      ElMessage.error('审批失败')
    }
  }
}

const handleReject = async (user) => {
  try {
    await ElMessageBox.confirm(
      `确定要拒绝用户 ${user.username} 吗？此操作将删除该用户。`,
      '确认拒绝',
      {
        confirmButtonText: '确认',
        cancelButtonText: '取消',
        type: 'error'
      }
    )

    const response = await rejectUser(user.bindingId)
    if (response.code === 200) {
      ElMessage.success('已拒绝')
      users.value = users.value.filter(u => u.bindingId !== user.bindingId)
    } else {
      ElMessage.error(response.message || '拒绝失败')
    }
  } catch (error) {
    if (error !== 'cancel') {
      console.error('拒绝失败:', error)
      ElMessage.error('拒绝失败')
    }
  }
}

const formatDate = (dateString) => {
  if (!dateString) return '未知'
  return new Date(dateString).toLocaleString('zh-CN')
}
</script>

<style scoped>
.pending-approvals {
  padding: 20px;
}

h2 {
  margin-bottom: 20px;
  color: #333;
}

.filter-section {
  margin-bottom: 20px;
  display: flex;
  gap: 10px;
}

.filter-section input,
.filter-section select {
  padding: 8px;
  border: 1px solid #ddd;
  border-radius: 4px;
  flex: 1;
}

.refresh-btn {
  padding: 8px 16px;
  background: #409eff;
  color: white;
  border: none;
  border-radius: 4px;
  cursor: pointer;
}

.refresh-btn:hover {
  background: #66b1ff;
}

.loading,
.empty {
  text-align: center;
  padding: 40px;
  color: #999;
}

.user-list {
  display: grid;
  gap: 20px;
}

.user-card {
  border: 1px solid #ddd;
  border-radius: 8px;
  padding: 20px;
  display: grid;
  grid-template-columns: 1fr auto;
  gap: 20px;
  background: white;
}

.user-info h3 {
  margin: 0 0 10px 0;
  color: #333;
}

.user-info p {
  margin: 5px 0;
  color: #666;
}

.role-ADMIN { color: #e74c3c; font-weight: bold; }
.role-OPS { color: #f39c12; font-weight: bold; }
.role-TEACHER { color: #3498db; font-weight: bold; }
.role-STUDENT { color: #27ae60; }
.role-USER { color: #95a5a6; }

.actions {
  display: flex;
  flex-direction: column;
  gap: 10px;
  min-width: 200px;
}

.role-selection {
  display: flex;
  flex-direction: column;
  gap: 5px;
}

.role-selection label {
  font-weight: bold;
  color: #666;
}

.role-selection select {
  padding: 5px;
  border: 1px solid #ddd;
  border-radius: 4px;
}

.action-buttons {
  display: flex;
  gap: 10px;
}

.action-buttons button {
  flex: 1;
  padding: 8px;
  border: none;
  border-radius: 4px;
  cursor: pointer;
  color: white;
  font-weight: bold;
}

.approve {
  background-color: #27ae60;
}

.approve:hover {
  background-color: #229954;
}

.reject {
  background-color: #e74c3c;
}

.reject:hover {
  background-color: #c0392b;
}
</style>
