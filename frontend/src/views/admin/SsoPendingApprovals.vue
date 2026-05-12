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
  max-width: 1400px;
  margin: 0 auto;
}

/* PC端大字号适配 */
@media (min-width: 1200px) {
  .pending-approvals {
    padding: 30px;
  }
  
  h2 {
    font-size: 28px;
    margin-bottom: 24px;
  }

  .filter-section input,
  .filter-section select {
    font-size: 15px;
    padding: 12px 16px;
  }

  .user-info h3 {
    font-size: 20px;
  }

  .user-info p {
    font-size: 15px;
  }

  .role-selection label {
    font-size: 15px;
  }
}

h2 {
  margin-bottom: 20px;
  color: #303133;
  font-weight: 700;
}

.filter-section {
  margin-bottom: 24px;
  display: flex;
  gap: 12px;
  background: #f8fafc;
  padding: 16px;
  border-radius: 12px;
}

.filter-section input,
.filter-section select {
  padding: 8px 12px;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  flex: 1;
  outline: none;
  transition: border-color 0.2s;
}

.filter-section input:focus,
.filter-section select:focus {
  border-color: #409eff;
}

.refresh-btn {
  padding: 8px 20px;
  background: #409eff;
  color: white;
  border: none;
  border-radius: 8px;
  cursor: pointer;
  font-weight: 600;
  transition: all 0.2s;
}

.refresh-btn:hover {
  background: #66b1ff;
  transform: translateY(-1px);
}

.loading,
.empty {
  text-align: center;
  padding: 60px;
  color: #94a3b8;
  font-size: 16px;
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 4px 12px rgba(0,0,0,0.05);
}

.user-list {
  display: grid;
  gap: 20px;
  grid-template-columns: repeat(auto-fill, minmax(500px, 1fr));
}

.user-card {
  border: none;
  border-radius: 12px;
  padding: 24px;
  display: flex;
  justify-content: space-between;
  gap: 24px;
  background: white;
  box-shadow: 0 4px 12px rgba(0,0,0,0.05);
  transition: transform 0.2s, box-shadow 0.2s;
}

.user-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 8px 24px rgba(0,0,0,0.08);
}

.user-info {
  flex: 1;
}

.user-info h3 {
  margin: 0 0 12px 0;
  color: #1e293b;
  font-size: 18px;
}

.user-info p {
  margin: 6px 0;
  color: #64748b;
  font-size: 14px;
}

.role-ADMIN { color: #ef4444; font-weight: 700; }
.role-OPS { color: #f59e0b; font-weight: 700; }
.role-TEACHER { color: #3b82f6; font-weight: 700; }
.role-STUDENT { color: #10b981; font-weight: 600; }
.role-USER { color: #94a3b8; }

.actions {
  display: flex;
  flex-direction: column;
  gap: 16px;
  min-width: 180px;
  padding-left: 20px;
  border-left: 1px solid #f1f5f9;
}

.role-selection {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.role-selection label {
  font-weight: 600;
  color: #475569;
  font-size: 13px;
}

.role-selection select {
  padding: 6px 10px;
  border: 1px solid #e2e8f0;
  border-radius: 6px;
  background: #f8fafc;
}

.action-buttons {
  display: flex;
  gap: 10px;
  margin-top: auto;
}

.action-buttons button {
  flex: 1;
  padding: 10px;
  border: none;
  border-radius: 6px;
  cursor: pointer;
  color: white;
  font-weight: 600;
  transition: opacity 0.2s;
}

.approve {
  background-color: #10b981;
}

.approve:hover {
  opacity: 0.9;
}

.reject {
  background-color: #ef4444;
}

.reject:hover {
  opacity: 0.9;
}

@media (max-width: 768px) {
  .pending-approvals {
    padding: 12px;
  }
  
  .filter-section {
    flex-direction: column;
    padding: 12px;
  }

  .user-list {
    grid-template-columns: 1fr;
  }

  .user-card {
    flex-direction: column;
    padding: 16px;
  }

  .actions {
    border-left: none;
    border-top: 1px solid #f1f5f9;
    padding-left: 0;
    padding-top: 16px;
    min-width: 0;
  }
}
</style>
