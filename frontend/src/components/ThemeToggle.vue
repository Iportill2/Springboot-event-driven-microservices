<script setup lang="ts">
import { useTheme } from '../composables/useTheme'

const { theme, toggle } = useTheme()
</script>

<template>
  <button
    type="button"
    class="theme-toggle"
    :aria-label="theme === 'dark' ? 'Cambiar a modo claro' : 'Cambiar a modo oscuro'"
    :title="theme === 'dark' ? 'Modo claro' : 'Modo oscuro'"
    @click="toggle"
  >
    <Transition name="icon" mode="out-in">
      <svg
        v-if="theme === 'dark'"
        key="sun"
        class="icon"
        viewBox="0 0 24 24"
        fill="none"
        stroke="currentColor"
        stroke-width="2"
        stroke-linecap="round"
      >
        <circle cx="12" cy="12" r="4.2" />
        <path
          d="M12 2.5v2.2M12 19.3v2.2M4.22 4.22l1.56 1.56M18.22 18.22l1.56 1.56M2.5 12h2.2M19.3 12h2.2M4.22 19.78l1.56-1.56M18.22 5.78l1.56-1.56"
        />
      </svg>
      <svg
        v-else
        key="moon"
        class="icon"
        viewBox="0 0 24 24"
        fill="none"
        stroke="currentColor"
        stroke-width="2"
        stroke-linecap="round"
        stroke-linejoin="round"
      >
        <path d="M20.5 14.3A8.5 8.5 0 1 1 9.7 3.5a7 7 0 0 0 10.8 10.8Z" />
      </svg>
    </Transition>
  </button>
</template>

<style scoped>
.theme-toggle {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
  border-radius: 12px;
  border: 1px solid var(--glass-border);
  background: var(--glass-bg);
  backdrop-filter: blur(12px) saturate(140%);
  -webkit-backdrop-filter: blur(12px) saturate(140%);
  color: var(--text-h);
  cursor: pointer;
  padding: 0;
  transition:
    transform 0.2s ease,
    border-color 0.2s ease,
    color 0.2s ease,
    background 0.2s ease;
}

.theme-toggle:hover {
  transform: translateY(-2px) rotate(8deg);
  border-color: var(--accent-2);
  color: var(--accent-2);
}

.icon {
  width: 19px;
  height: 19px;
}

.icon-enter-active,
.icon-leave-active {
  transition: opacity 0.25s ease, transform 0.3s cubic-bezier(0.22, 1, 0.36, 1);
}

.icon-enter-from {
  opacity: 0;
  transform: rotate(-70deg) scale(0.6);
}

.icon-leave-to {
  opacity: 0;
  transform: rotate(70deg) scale(0.6);
}
</style>