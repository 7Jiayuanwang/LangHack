<template>
  <el-dialog
    v-model="visible"
    title="二次鉴权验证"
    width="360px"
    :close-on-click-modal="false"
    @close="handleClose"
  >
    <div class="elevation-content">
      <p class="elevation-hint">
        您正在以 <strong>{{ currentRoleLabel }}</strong> 身份访问
        <strong>{{ targetRoleLabel }}</strong> 功能区。
        请输入密码进行二次鉴权。
      </p>
      <el-form @submit.prevent="handleElevate">
        <el-form-item>
          <el-input
            v-model="password"
            type="password"
            placeholder="请输入登录密码"
            show-password
            :disabled="loading"
            @keyup.enter="handleElevate"
          />
        </el-form-item>
      </el-form>
    </div>
    <template #footer>
      <el-button @click="handleClose">取消</el-button>
      <el-button type="primary" :loading="loading" @click="handleElevate">
        验证
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, computed } from 'vue'
import { ElMessage } from 'element-plus'
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
.elevation-content {
  padding: 0 4px;
}

.elevation-hint {
  font-size: 14px;
  color: var(--text-secondary, #666);
  margin-bottom: 16px;
  line-height: 1.6;
}
</style>
