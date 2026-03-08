<template>
  <div class="version-selector">
    <div class="version-tabs">
      <button
        v-for="version in availableVersions"
        :key="version.key"
        :class="['version-tab', { active: selectedVersion === version.key }]"
        @click="$emit('version-change', version.key)"
      >
        {{ version.label }}
      </button>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  versions: {
    type: Object,
    required: true
  },
  selectedVersion: {
    type: String,
    default: 'original'
  }
})

defineEmits(['version-change'])

const versionLabels = {
  original: '原文',
  cet4: 'CET-4',
  cet6: 'CET-6',
  academic: '学术版',
  chinese_parallel: '中英对照'
}

const availableVersions = computed(() => {
  return Object.keys(props.versions || {})
    .filter(key => props.versions[key])
    .map(key => ({
      key,
      label: versionLabels[key] || key
    }))
})
</script>

<style scoped>
.version-selector {
  margin-bottom: 20px;
}

.version-tabs {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  background: white;
  padding: 12px;
  border-radius: 12px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
}

.version-tab {
  padding: 8px 16px;
  border: 1px solid #e0e0e0;
  border-radius: 8px;
  background: white;
  color: #666;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.3s ease;
  outline: none;
}

.version-tab:hover {
  border-color: #409eff;
  color: #409eff;
  transform: translateY(-1px);
  box-shadow: 0 2px 8px rgba(64, 158, 255, 0.15);
}

.version-tab.active {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  border-color: #667eea;
  color: white;
  box-shadow: 0 4px 12px rgba(102, 126, 234, 0.3);
}

.version-tab.active:hover {
  transform: translateY(-2px);
  box-shadow: 0 6px 16px rgba(102, 126, 234, 0.4);
}

@media (max-width: 768px) {
  .version-tabs {
    padding: 10px;
    gap: 6px;
  }

  .version-tab {
    padding: 6px 12px;
    font-size: 13px;
  }
}
</style>
