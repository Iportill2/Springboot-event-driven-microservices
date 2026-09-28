<script setup lang="ts">
import { RouterView } from 'vue-router'
</script>

<template>
  <div class="app-root">
    <div class="bg" aria-hidden="true">
      <div class="blob blob-1"></div>
      <div class="blob blob-2"></div>
      <div class="blob blob-3"></div>
      <div class="bg-grid"></div>
    </div>

    <RouterView v-slot="{ Component }">
      <Transition name="view" mode="out-in">
        <component :is="Component" />
      </Transition>
    </RouterView>
  </div>
</template>

<style scoped>
.app-root {
  position: relative;
  min-height: 100vh;
  z-index: 0;
}

.bg {
  position: fixed;
  inset: 0;
  z-index: -1;
  overflow: hidden;
  background:
    radial-gradient(120% 90% at 15% 0%, var(--blob-1), transparent 55%),
    radial-gradient(110% 90% at 100% 0%, var(--blob-2), transparent 55%),
    radial-gradient(120% 120% at 50% 120%, var(--blob-3), transparent 60%),
    var(--bg-base);
}

:root[data-theme='dark'] .bg {
  background:
    radial-gradient(120% 90% at 15% 0%, var(--blob-1), transparent 55%),
    radial-gradient(110% 90% at 100% 0%, var(--blob-2), transparent 55%),
    radial-gradient(120% 120% at 50% 120%, var(--blob-3), transparent 60%),
    var(--bg-base);
}

.blob {
  position: absolute;
  border-radius: 50%;
  filter: blur(90px);
  will-change: transform;
  mix-blend-mode: normal;
}

.blob-1 {
  width: 52vmax;
  height: 52vmax;
  top: -16vmax;
  left: -12vmax;
  background: var(--blob-1);
  animation: drift-1 26s ease-in-out infinite alternate;
}

.blob-2 {
  width: 44vmax;
  height: 44vmax;
  top: 8vmax;
  right: -16vmax;
  background: var(--blob-2);
  animation: drift-2 30s ease-in-out infinite alternate;
}

.blob-3 {
  width: 46vmax;
  height: 46vmax;
  bottom: -18vmax;
  left: 12vmax;
  background: var(--blob-3);
  animation: drift-3 34s ease-in-out infinite alternate;
}

.bg-grid {
  position: absolute;
  inset: 0;
  background-image: radial-gradient(circle, var(--grid-dot) 1px, transparent 1px);
  background-size: 26px 26px;
  mask-image: radial-gradient(80% 70% at 50% 30%, #000 30%, transparent 100%);
  -webkit-mask-image: radial-gradient(80% 70% at 50% 30%, #000 30%, transparent 100%);
}

@keyframes drift-1 {
  from {
    transform: translate(0, 0) scale(1);
  }
  to {
    transform: translate(18vmax, 12vmax) scale(1.15);
  }
}

@keyframes drift-2 {
  from {
    transform: translate(0, 0) scale(1.1);
  }
  to {
    transform: translate(-16vmax, -8vmax) scale(0.95);
  }
}

@keyframes drift-3 {
  from {
    transform: translate(0, 0) scale(0.95);
  }
  to {
    transform: translate(-14vmax, -10vmax) scale(1.2);
  }
}
</style>