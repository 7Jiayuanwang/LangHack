<template>
  <div class="xy-mascot" :style="{ '--xy-mascot-size': `${size}px` }" :aria-hidden="decorative ? 'true' : undefined" :role="decorative ? undefined : 'img'" :aria-label="decorative ? undefined : '小芽，英语学习伙伴'">
    <span class="xy-mascot__leaf xy-mascot__leaf--left"></span>
    <span class="xy-mascot__leaf xy-mascot__leaf--right"></span>
    <span class="xy-mascot__head">
      <span class="xy-mascot__eye xy-mascot__eye--left"></span>
      <span class="xy-mascot__eye xy-mascot__eye--right"></span>
      <span class="xy-mascot__cheek xy-mascot__cheek--left"></span>
      <span class="xy-mascot__cheek xy-mascot__cheek--right"></span>
      <span class="xy-mascot__smile"></span>
    </span>
    <span class="xy-mascot__spark">✦</span>
  </div>
</template>

<script setup>
defineProps({ size: { type: Number, default: 72 }, decorative: { type: Boolean, default: true } })
</script>

<style scoped>
.xy-mascot { position: relative; flex: 0 0 auto; width: var(--xy-mascot-size); height: var(--xy-mascot-size); filter: drop-shadow(0 10px 18px rgba(31, 122, 92, 0.22)); }
.xy-mascot__head { position: absolute; inset: 14% 6% 5%; border: 2px solid rgba(255,255,255,.9); border-radius: 46% 46% 42% 42%; background: linear-gradient(145deg, #f5fff5, #baf2d1 55%, #7bd8b2); box-shadow: inset 0 -8px 14px rgba(24,135,94,.12); }
.xy-mascot__leaf { position: absolute; z-index: 2; top: -17%; width: 21%; height: 48%; background: linear-gradient(160deg, #8ce8b8 0%, #4cc795 45%, #1fa573 100%); border-radius: 50% 50% 42% 42% / 68% 68% 32% 32%; }
.xy-mascot__leaf--left { left: 39%; transform-origin: bottom center; transform: rotate(-13deg); }
.xy-mascot__leaf--right { left: 39%; transform-origin: bottom center; transform: rotate(13deg); }
.xy-mascot__eye { position: absolute; top: 39%; width: 9%; height: 12%; border-radius: 50%; background: #164b3c; box-shadow: inset 1px 1px 0 rgba(255,255,255,.8); }
.xy-mascot__eye--left { left: 29%; }
.xy-mascot__eye--right { right: 29%; }
.xy-mascot__cheek { position: absolute; top: 56%; width: 14%; height: 8%; border-radius: 50%; background: rgba(255,137,154,.42); }
.xy-mascot__cheek--left { left: 15%; }
.xy-mascot__cheek--right { right: 15%; }
.xy-mascot__smile { position: absolute; left: 42%; top: 54%; width: 16%; height: 13%; border-bottom: 2px solid #276b55; border-radius: 0 0 50% 50%; }
.xy-mascot__spark { position: absolute; z-index: 3; top: 8%; right: 0; color: #ffe082; font-size: calc(var(--xy-mascot-size) * .24); animation: xy-sparkle 2.4s ease-in-out infinite; }
@keyframes xy-sparkle { 0%, 100% { transform: scale(.75); opacity: .65; } 50% { transform: scale(1.15) rotate(22deg); opacity: 1; } }
@media (prefers-reduced-motion: reduce) { .xy-mascot__spark { animation: none; } }
</style>
