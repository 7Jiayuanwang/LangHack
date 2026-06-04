<template>
  <div class="risk-tag-list">
    <el-tag
      v-for="tag in tags"
      :key="tag.code"
      :type="getTagType(tag.tagType)"
      size="small"
      class="tag-item"
      :title="tag.actionHint"
    >
      {{ tag.displayName }}
    </el-tag>
  </div>
</template>

<script setup>
defineProps({
  tags: {
    type: Array,
    default: () => []
  }
})

const getTagType = (tagType) => {
  const typeMap = {
    risk: 'danger',
    ability: 'warning',
    habit: 'info',
    preference: '',
    positive: 'success'
  }
  return typeMap[tagType] || 'info'
}
</script>

<style scoped>
.risk-tag-list {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
.tag-item {
  cursor: default;
}
</style>
