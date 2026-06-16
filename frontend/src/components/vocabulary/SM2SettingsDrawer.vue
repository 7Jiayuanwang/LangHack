<template>
 <el-drawer
 v-model="visible"
 title="SM-2 复习设置"
 direction="rtl"
 size="min(420px, 92vw)"
 :close-on-click-modal="false"
 >
 <div class="settings-drawer">
 <!-- 当前模式展示 -->
 <div class="mode-banner" :class="modeClass">
 <div class="mode-icon">
 <el-icon><component :is="modeIcon" /></el-icon>
 </div>
 <div class="mode-content">
 <div class="mode-title">{{ modeTitle }}</div>
 <div class="mode-desc">{{ modeDesc }}</div>
 </div>
 </div>

 <!-- 困难词加强模式 -->
 <div class="setting-group">
 <div class="setting-row">
 <div class="setting-info">
 <div class="setting-label">困难词加强模式</div>
 <div class="setting-hint">对反复遗忘的单词启用更激进的复习频率</div>
 </div>
 <el-switch
 v-model="settings.hardWordMode"
 @change="onChange"
 active-color="#f56c6c"
 />
 </div>
 <div class="setting-detail" v-if="settings.hardWordMode">
 <el-alert type="warning" :closable="false" show-icon>
 <template #title>
 EF 下限将从 1.3 降至 1.0，困难单词复习间隔会明显缩短
 </template>
 </el-alert>
 </div>
 </div>

 <!-- 过载保护 -->
 <div class="setting-group">
 <div class="setting-row">
 <div class="setting-info">
 <div class="setting-label">过载保护</div>
 <div class="setting-hint">到期单词过多时自动分批</div>
 </div>
 <el-switch
 v-model="settings.overloadProtection"
 @change="onChange"
 active-color="#67c23a"
 />
 </div>
 <div v-if="settings.overloadProtection" class="setting-detail">
 <div class="setting-sub">
 <label>单日最大复习量</label>
 <el-input-number
 v-model="settings.maxDailyReview"
 :min="10"
 :max="200"
 :step="10"
 size="small"
 @change="onChange"
 />
 <span class="unit">个</span>
 </div>
 <div class="setting-sub">
 <label>紧急阈值</label>
 <el-input-number
 v-model="settings.emergencyThreshold"
 :min="20"
 :max="300"
 :step="5"
 size="small"
 @change="onChange"
 />
 <span class="unit">个（超过后容量降至 80%）</span>
 </div>
 </div>
 </div>

 <!-- 数据监控 -->
 <div class="setting-group">
 <div class="setting-row">
 <div class="setting-info">
 <div class="setting-label">显示数据监控面板</div>
 <div class="setting-hint">在学习页查看 EF 分布 / 保持率 / 模式对比等指标</div>
 </div>
 <el-switch
 v-model="settings.showAnalytics"
 @change="onChange"
 active-color="#409eff"
 />
 </div>
 </div>

 <!-- 操作按钮 -->
 <div class="drawer-footer">
 <el-button @click="handleReset" plain>恢复默认</el-button>
 <el-button type="primary" @click="handleClose">完成</el-button>
 </div>
 </div>
 </el-drawer>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { Sunny, Lightning, SetUp } from '@element-plus/icons-vue'
import { getSettings, saveSettings, resetSettings } from '@/utils/sm2-settings'

const props = defineProps({
 modelValue: { type: Boolean, default: false }
})
const emit = defineEmits(['update:modelValue', 'changed'])

const visible = ref(props.modelValue)
watch(() => props.modelValue, v => { visible.value = v })
watch(visible, v => emit('update:modelValue', v))

const settings = ref(getSettings())

const onChange = () => {
 const saved = saveSettings(settings.value)
 settings.value = Object.assign({}, saved)
 emit('changed', saved)
}

const handleReset = () => {
 const defaults = resetSettings()
 settings.value = Object.assign({}, defaults)
 emit('changed', defaults)
}

const handleClose = () => { visible.value = false }

const modeClass = computed(() => {
 if (settings.value.hardWordMode && settings.value.overloadProtection) return 'mode-hybrid'
 if (settings.value.hardWordMode) return 'mode-hard'
 if (settings.value.overloadProtection) return 'mode-overload'
 return 'mode-standard'
})

const modeIcon = computed(() => {
 if (settings.value.hardWordMode) return Lightning
 return settings.value.overloadProtection ? SetUp : Sunny
})

const modeTitle = computed(() => {
 if (settings.value.hardWordMode && settings.value.overloadProtection) return '混合模式'
 if (settings.value.hardWordMode) return '困难词加强模式'
 if (settings.value.overloadProtection) return '过载保护模式'
 return '标准 SM-2'
})

const modeDesc = computed(() => {
 if (settings.value.hardWordMode && settings.value.overloadProtection) return 'EF 下限 1.0 + 智能分批'
 if (settings.value.hardWordMode) return 'EF 下限 1.0，困难词复习频率提高'
 if (settings.value.overloadProtection) return `单日最多 ${settings.value.maxDailyReview} 个，超限自动分批`
 return 'EF 下限 1.3，标准间隔算法'
})
</script>

<style scoped lang="scss">
.settings-drawer {
 padding: 0 8px;
}

.mode-banner {
 display: flex;
 align-items: center;
 gap: 16px;
 padding: 16px;
 border-radius: 12px;
 margin-bottom: 20px;
 background: linear-gradient(135deg, #f0f9ff 0%, #e0f2fe 100%);
 border: 1px solid #bae6fd;
 transition: all 0.3s;

 &.mode-hard {
 background: linear-gradient(135deg, #fef2f2 0%, #fee2e2 100%);
 border-color: #fecaca;
 }

 &.mode-overload {
 background: linear-gradient(135deg, #f0fdf4 0%, #dcfce7 100%);
 border-color: #bbf7d0;
 }

 &.mode-hybrid {
 background: linear-gradient(135deg, #fefce8 0%, #fef3c7 100%);
 border-color: #fde68a;
 }
}

.mode-icon {
 width: 48px;
 height: 48px;
 border-radius: 12px;
 background: rgba(255, 255, 255, 0.7);
 display: flex;
 align-items: center;
 justify-content: center;
 font-size: 24px;
 color: #303133;
 flex-shrink: 0;
}

.mode-content {
 flex: 1;
 min-width: 0;
}

.mode-title {
 font-size: 16px;
 font-weight: 600;
 color: #303133;
 margin-bottom: 4px;
}

.mode-desc {
 font-size: 13px;
 color: #606266;
}

.setting-group {
 padding: 16px 0;
 border-bottom: 1px solid #ebeef5;

 &:last-of-type {
 border-bottom: none;
 }

 &.ab-group {
 background: #fafafa;
 margin: 8px -8px 0;
 padding: 16px;
 border-radius: 8px;
 border-bottom: none;
 }
}

.setting-row {
 display: flex;
 justify-content: space-between;
 align-items: center;
 gap: 12px;
}

.setting-info {
 flex: 1;
 min-width: 0;
}

.setting-label {
 font-size: 14px;
 font-weight: 500;
 color: #303133;
 margin-bottom: 4px;
 display: flex;
 align-items: center;
 gap: 8px;
}

.setting-hint {
 font-size: 12px;
 color: #909399;
 line-height: 1.4;
}

.setting-detail {
 margin-top: 12px;
 padding-left: 0;
}

.setting-sub {
 display: flex;
 align-items: center;
 gap: 12px;
 margin-top: 12px;

 label {
 font-size: 13px;
 color: #606266;
 min-width: 90px;
 }

 .unit {
 font-size: 12px;
 color: #909399;
 }
}

.drawer-footer {
 display: flex;
 justify-content: space-between;
 gap: 12px;
 padding: 20px 0 8px;
 border-top: 1px solid #ebeef5;
 margin-top: 24px;
 position: sticky;
 bottom: 0;
 background: white;
}
</style>
