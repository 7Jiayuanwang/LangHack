<template>
  <span class="size-input">
    <el-input-number
      v-model="numVal"
      :min="1"
      :max="9999"
      :step="1"
      controls-position="right"
      style="width:110px"
      :placeholder="placeholder"
      @change="emitChange"
    />
    <el-select
      v-model="unitVal"
      style="width:80px;margin-left:6px"
      @change="emitChange"
    >
      <el-option v-for="u in units" :key="u" :label="u" :value="u" />
    </el-select>
  </span>
</template>

<script setup>
import { ref, watch } from 'vue'

const props = defineProps({
  modelValue: { type: String, default: '' },
  placeholder: { type: String, default: '继承全局' }
})
const emit = defineEmits(['update:modelValue'])

const units = ['KB', 'MB', 'GB']

const parse = (v) => {
  if (!v) return { num: null, unit: 'MB' }
  const m = String(v).toUpperCase().match(/^(\d+(?:\.\d+)?)(KB|MB|GB)$/)
  return m ? { num: parseFloat(m[1]), unit: m[2] } : { num: null, unit: 'MB' }
}

const { num, unit } = parse(props.modelValue)
const numVal  = ref(num)
const unitVal = ref(unit)

const emitChange = () => {
  if (numVal.value == null) {
    emit('update:modelValue', '')
    return
  }
  emit('update:modelValue', `${numVal.value}${unitVal.value}`)
}

watch(() => props.modelValue, (v) => {
  const { num: n, unit: u } = parse(v)
  numVal.value = n
  unitVal.value = u
})
</script>

<style scoped>
.size-input { display: inline-flex; align-items: center; }
</style>
