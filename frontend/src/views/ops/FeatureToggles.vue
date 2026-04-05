<template>
  <div class="feature-toggles-page">
    <div class="page-header">
      <h2>功能开关管理</h2>
      <p class="subtitle">控制学生端页面的可见性，关闭的功能将从导航中隐藏</p>
    </div>

    <div v-if="loading" class="loading-state">
      <el-icon class="is-loading" :size="32"><Loading /></el-icon>
      <p>加载中...</p>
    </div>

    <div v-else class="toggles-list">
      <div
        v-for="toggle in toggles"
        :key="toggle.configKey"
        class="toggle-card"
      >
        <div class="toggle-info">
          <h3 class="toggle-name">{{ toggle.description }}</h3>
          <span class="toggle-key">{{ toggle.configKey }}</span>
        </div>
        <el-switch
          v-model="toggle.enabled"
          @change="(val) => handleToggle(toggle, val)"
          :loading="toggle.saving"
          active-text="启用"
          inactive-text="关闭"
        />
      </div>
    </div>

    <div class="save-hint" v-if="!loading">
      <el-icon><InfoFilled /></el-icon>
      <span>开关状态即时生效，学生刷新页面后生效</span>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Loading, InfoFilled } from '@element-plus/icons-vue'
import { getSystemConfig, updateSystemConfig } from '@/api/systemConfig'

const loading = ref(true)
const toggles = ref([])

const loadToggles = async () => {
  loading.value = true
  try {
    const res = await getSystemConfig('feature_toggle')
    if (res?.code === 200 && Array.isArray(res.data)) {
      toggles.value = res.data.map(item => ({
        ...item,
        enabled: item.configValue === 'true',
        saving: false
      }))
    }
  } catch (e) {
    console.error('加载功能开关失败:', e)
    ElMessage.error('加载功能开关失败')
  } finally {
    loading.value = false
  }
}

const handleToggle = async (toggle, enabled) => {
  toggle.saving = true
  try {
    await updateSystemConfig([{
      ...toggle,
      configValue: enabled ? 'true' : 'false'
    }])
    ElMessage.success(`${toggle.description} 已${enabled ? '启用' : '关闭'}`)
  } catch (e) {
    console.error('更新功能开关失败:', e)
    ElMessage.error('更新失败')
    toggle.enabled = !enabled  // revert
  } finally {
    toggle.saving = false
  }
}

onMounted(loadToggles)
</script>

<style scoped>
.feature-toggles-page {
  max-width: 800px;
  margin: 0 auto;
  padding: 24px;
}

.page-header {
  margin-bottom: 24px;
}

.page-header h2 {
  margin: 0 0 8px;
  font-size: 20px;
  font-weight: 700;
}

.subtitle {
  margin: 0;
  font-size: 14px;
  color: #64748b;
}

.toggles-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.toggle-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  padding: 16px 20px;
  transition: box-shadow 0.2s;
}

.toggle-card:hover {
  box-shadow: 0 2px 8px rgba(0,0,0,0.04);
}

.toggle-info {
  flex: 1;
}

.toggle-name {
  margin: 0 0 4px;
  font-size: 15px;
  font-weight: 600;
  color: #0f172a;
}

.toggle-key {
  font-size: 12px;
  color: #94a3b8;
  font-family: monospace;
}

.save-hint {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 20px;
  padding: 12px 16px;
  background: #f0f9ff;
  border-radius: 10px;
  font-size: 13px;
  color: #0369a1;
}

.loading-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 40px;
  color: #94a3b8;
}
</style>
