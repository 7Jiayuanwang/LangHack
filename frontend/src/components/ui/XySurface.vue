<template>
  <section class="xy-surface" :class="{ 'xy-surface--compact': compact }">
    <div v-if="title || $slots.header" class="xy-surface__header">
      <div>
        <h2 v-if="title">{{ title }}</h2>
        <p v-if="description">{{ description }}</p>
      </div>
      <slot name="header" />
    </div>
    <slot />
  </section>
</template>

<script setup>
defineProps({
  title: { type: String, default: '' },
  description: { type: String, default: '' },
  compact: { type: Boolean, default: false },
})
</script>

<style scoped>
.xy-surface {
  min-width: 0;
  padding: 24px;
  border: 1px solid var(--xy-border);
  border-radius: var(--xy-radius-lg);
  background: var(--xy-surface);
  box-shadow: var(--xy-shadow-card);
}
.xy-surface--compact { padding: 18px; border-radius: var(--xy-radius-md); }
.xy-surface__header { display: flex; align-items: start; justify-content: space-between; gap: 16px; margin-bottom: 18px; }
.xy-surface__header h2 { margin: 0; color: var(--xy-ink); font-size: 18px; line-height: 1.35; }
.xy-surface__header p { margin: 5px 0 0; color: var(--xy-ink-soft); font-size: 13px; line-height: 1.5; }
@media (max-width: 640px) { .xy-surface { padding: 18px; } }
</style>
