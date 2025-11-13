<template>
  <div class="texts-management">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>课文管理</span>
        </div>
      </template>

      <div class="content">
        <p>课文管理页面正在加载...</p>
        <p>当前状态：{{ status }}</p>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'

const status = ref('初始化中')

onMounted(() => {
  status.value = '页面加载成功！'
  ElMessage.success('课文管理页面已打开')
})
</script>

<style scoped>
.texts-management {
  padding: 20px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.content {
  padding: 20px;
  text-align: center;
}
</style>
