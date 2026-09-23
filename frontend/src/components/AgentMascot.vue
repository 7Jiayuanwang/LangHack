<template>
  <div class="agent-mascot" :class="stageClass" :style="mascotStyle" aria-hidden="true">
    <span class="mascot-leaf leaf-left"></span>
    <span class="mascot-leaf leaf-right"></span>
    <span v-if="stage === 'LEAF' || stage === 'BLOOM'" class="mascot-leaf leaf-crown"></span>
    <span v-if="stage === 'BLOOM'" class="mascot-flower"></span>
    <div class="mascot-head">
      <span class="mascot-eye eye-left"></span>
      <span class="mascot-eye eye-right"></span>
      <span class="mascot-cheek cheek-left"></span>
      <span class="mascot-cheek cheek-right"></span>
      <span class="mascot-smile"></span>
    </div>
    <span class="mascot-spark">✦</span>
  </div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  size: { type: Number, default: 72 },
  stage: { type: String, default: 'SEED' }
})

const stageClass = computed(() => ['SEED', 'SPROUT', 'LEAF', 'BLOOM'].includes(props.stage)
  ? `stage-${props.stage.toLowerCase()}` : 'stage-seed')

const mascotStyle = computed(() => ({
  width: `${props.size}px`,
  height: `${props.size}px`,
  '--mascot-size': `${props.size}px`
}))
</script>

<style scoped>
.agent-mascot {
  position: relative;
  flex: 0 0 auto;
  filter: drop-shadow(0 10px 18px rgba(31, 122, 92, 0.22));
}

.mascot-head {
  position: absolute;
  inset: 14% 6% 5%;
  border-radius: 46% 46% 42% 42%;
  background: linear-gradient(145deg, #f5fff5 0%, #baf2d1 55%, #7bd8b2 100%);
  border: max(2px, 0.035em) solid rgba(255, 255, 255, 0.88);
  box-shadow: inset 0 -8px 14px rgba(24, 135, 94, 0.12);
}

.mascot-leaf {
  position: absolute;
  z-index: 2;
  top: 0;
  width: 31%;
  height: 34%;
  background: linear-gradient(145deg, #6ed6a5, #24a978);
}

.stage-sprout .mascot-head { background: linear-gradient(145deg, #f5fff5, #a5eec6 55%, #68cda3); }
.leaf-crown { left: 46%; top: -7%; width: 25%; height: 29%; border-radius: 100% 5% 100% 10%; transform: rotate(28deg); }
.mascot-flower { position: absolute; z-index: 4; left: 48%; top: -10%; width: 14%; height: 14%; border: 2px solid #fff3c6; border-radius: 50%; background: #ffc96f; box-shadow: calc(var(--mascot-size) * -.1) 0 #f9a5ae, calc(var(--mascot-size) * .1) 0 #f9a5ae, 0 calc(var(--mascot-size) * -.1) #f9a5ae, 0 calc(var(--mascot-size) * .1) #f9a5ae; }

.leaf-left {
  left: 27%;
  border-radius: 100% 5% 100% 10%;
  transform: rotate(-28deg);
}

.leaf-right {
  right: 24%;
  border-radius: 5% 100% 10% 100%;
  transform: rotate(25deg) scale(0.82);
}

.mascot-eye {
  position: absolute;
  top: 39%;
  width: 9%;
  height: 12%;
  border-radius: 50%;
  background: #164b3c;
  box-shadow: inset 1px 1px 0 rgba(255, 255, 255, 0.8);
}

.eye-left { left: 29%; }
.eye-right { right: 29%; }

.mascot-cheek {
  position: absolute;
  top: 56%;
  width: 14%;
  height: 8%;
  border-radius: 50%;
  background: rgba(255, 137, 154, 0.42);
}

.cheek-left { left: 15%; }
.cheek-right { right: 15%; }

.mascot-smile {
  position: absolute;
  left: 42%;
  top: 54%;
  width: 16%;
  height: 13%;
  border-bottom: max(2px, 0.035em) solid #276b55;
  border-radius: 0 0 50% 50%;
}

.mascot-spark {
  position: absolute;
  z-index: 3;
  top: 8%;
  right: 0;
  color: #ffe082;
  font-size: calc(var(--mascot-size) * 0.24);
  animation: sparkle 2.4s ease-in-out infinite;
}

@keyframes sparkle {
  0%, 100% { transform: scale(0.75) rotate(0); opacity: 0.65; }
  50% { transform: scale(1.15) rotate(22deg); opacity: 1; }
}

@media (prefers-reduced-motion: reduce) {
  .mascot-spark { animation: none; }
}
</style>
