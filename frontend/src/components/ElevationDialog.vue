<template>
  <el-dialog
    v-model="visible"
    title="安全提权验证"
    width="400px"
    :close-on-click-modal="false"
    append-to-body
    class="elevation-dialog"
    @close="handleClose"
  >
    <div class="elevation-body">
      <div class="security-header">
        <div class="shield-icon">
          <svg width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5">
            <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/>
          </svg>
        </div>
        <h3>身份二次确认</h3>
      </div>

      <p class="elevation-hint">
        检测到敏感操作。您正在从 <strong>{{ currentRoleLabel }}</strong> 提权至 
        <el-tag type="danger" size="small" effect="dark" class="role-tag">{{ targetRoleLabel }}</el-tag> 权限。
        请确认您的管理员身份。
      </p>

      <el-form @submit.prevent="handleElevate" label-position="top">
        <el-form-item label="管理员密码">
          <el-input
            v-model="password"
            type="password"
            placeholder="请输入您的登录密码"
            show-password
            :disabled="loading"
            class="security-input"
            @keyup.enter="handleElevate"
          >
            <template #prefix>
              <el-icon><Lock /></el-icon>
            </template>
          </el-input>
        </el-form-item>
      </el-form>
      
      <div class="security-footer-hint">
        <el-icon><InfoFilled /></el-icon>
        验证成功后，提权状态将在 30 分钟后自动失效
      </div>
    </div>
    <template #footer>
      <div class="dialog-footer">
        <el-button @click="handleClose" plain>取消</el-button>
        <el-button type="primary" :loading="loading" class="btn-elevate" @click="handleElevate">
          确认提权
        </el-button>
      </div>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { Lock, InfoFilled } from '@element-plus/icons-vue'
import { useUserStore } from '@/store/user'
import { elevate } from '@/api/ops'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  targetRole: { type: String, required: true }
})

const emit = defineEmits(['update:modelValue', 'success'])

const userStore = useUserStore()
const password = ref('')
const loading = ref(false)

const visible = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val)
})

const roleLabels = {
  ADMIN: '管理员',
  OPS: '运维',
  TEACHER: '教师',
  STUDENT: '学生',
  USER: '用户'
}

const currentRoleLabel = computed(() => roleLabels[userStore.userRole] || userStore.userRole)
const targetRoleLabel = computed(() => roleLabels[props.targetRole] || props.targetRole)

const handleElevate = async () => {
  if (!password.value) {
    ElMessage.warning('请输入密码')
    return
  }
  loading.value = true
  try {
    const res = await elevate({
      password: password.value,
      targetRole: props.targetRole
    })
    // 后端返回 Result{code, message, data: {elevatedRole, expiresAt}}
    // axios 拦截器返回整个 Result 对象，所以真正的数据在 res.data 中
    const result = res.data?.elevatedRole ? res.data : (res.data?.data || res)
    userStore.setElevation(result.elevatedRole, result.expiresAt)
    ElMessage.success('提权成功')
    emit('success')
    visible.value = false
  } catch (error) {
    // error message already shown by request interceptor
  } finally {
    loading.value = false
    password.value = ''
  }
}

const handleClose = () => {
  password.value = ''
  visible.value = false
}
</script>

<style scoped>
.elevation-body {
  padding: 8px 12px;
}

.security-header {
  display: flex;
  flex-direction: column;
  align-items: center;
  margin-bottom: 24px;
}

.shield-icon {
  width: 64px;
  height: 64px;
  background: #fef2f2;
  color: #ef4444;
  border-radius: 20px;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 12px;
  box-shadow: 0 8px 16px rgba(239, 68, 68, 0.1);
}

.security-header h3 {
  margin: 0;
  font-size: 18px;
  font-weight: 700;
  color: #1e293b;
}

.elevation-hint {
  font-size: 14px;
  color: #64748b;
  margin-bottom: 24px;
  line-height: 1.6;
  background: #f8fafc;
  padding: 12px 16px;
  border-radius: 12px;
  border: 1px solid #f1f5f9;
}

.role-tag {
  font-weight: 700;
  margin: 0 2px;
}

.security-input :deep(.el-input__wrapper) {
  padding: 8px 12px;
  border-radius: 10px;
}

.security-footer-hint {
  margin-top: 16px;
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: #94a3b8;
}

.dialog-footer {
  display: flex;
  gap: 12px;
  justify-content: flex-end;
}

.btn-elevate {
  padding-left: 24px;
  padding-right: 24px;
  font-weight: 600;
  border-radius: 10px;
}

:deep(.elevation-dialog) {
  border-radius: 24px;
  overflow: hidden;
}

:deep(.elevation-dialog .el-dialog__header) {
  margin-right: 0;
  padding-bottom: 0;
}
</style>
