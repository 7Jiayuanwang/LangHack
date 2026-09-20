<template>
  <header class="xy-page-header" :class="`xy-page-header--${variant}`">
    <div class="xy-page-header__copy">
      <p v-if="eyebrow" class="xy-page-header__eyebrow">{{ eyebrow }}</p>
      <h1>{{ title }}</h1>
      <p v-if="description" class="xy-page-header__description">{{ description }}</p>
      <slot name="details" />
    </div>
    <div v-if="$slots.visual" class="xy-page-header__visual"><slot name="visual" /></div>
    <div v-if="$slots.actions" class="xy-page-header__actions"><slot name="actions" /></div>
  </header>
</template>

<script setup>
defineProps({
  title: { type: String, required: true },
  eyebrow: { type: String, default: '' },
  description: { type: String, default: '' },
  variant: { type: String, default: 'default', validator: (value) => ['default', 'hero', 'compact'].includes(value) },
})
</script>

<style scoped>
.xy-page-header {
  display: flex;
  align-items: center;
  gap: 20px;
  min-width: 0;
  padding: 24px;
  border: 1px solid var(--xy-border);
  border-radius: var(--xy-radius-lg);
  background: var(--xy-surface);
  box-shadow: var(--xy-shadow-card);
}

.xy-page-header--hero {
  position: relative;
  overflow: hidden;
  min-height: 176px;
  border-color: rgba(23, 110, 81, 0.12);
  background: linear-gradient(115deg, #e7f8ee, #f9fffb 72%);
}

.xy-page-header--hero::after {
  content: '';
  position: absolute;
  right: -34px;
  bottom: -86px;
  width: 224px;
  height: 224px;
  border: 1px solid rgba(38, 153, 112, 0.13);
  border-radius: 50%;
  pointer-events: none;
}

.xy-page-header--compact { padding: 18px 22px; }
.xy-page-header__copy { flex: 1; min-width: 0; position: relative; z-index: 1; }
.xy-page-header__eyebrow {
  margin: 0 0 6px;
  color: var(--xy-brand-600);
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 0.12em;
  text-transform: uppercase;
}
.xy-page-header h1 { margin: 0; color: var(--xy-ink); font-size: clamp(22px, 3vw, 32px); line-height: 1.25; letter-spacing: -0.03em; }
.xy-page-header--compact h1 { font-size: 22px; }
.xy-page-header__description { margin: 8px 0 0; color: var(--xy-ink-muted); font-size: 14px; line-height: 1.6; }
.xy-page-header__visual, .xy-page-header__actions { position: relative; z-index: 1; flex: 0 0 auto; }
.xy-page-header__actions { display: flex; flex-wrap: wrap; align-items: center; gap: 10px; }

@media (max-width: 640px) {
  .xy-page-header { padding: 20px; }
  .xy-page-header--compact { flex-wrap: wrap; padding: 18px; }
  .xy-page-header__actions { width: 100%; }
  .xy-page-header--hero { min-height: 148px; }
}
</style>
