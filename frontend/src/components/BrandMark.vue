<script setup lang="ts">
import { computed } from 'vue'
import { brand, brandInitials } from '../brand'

const props = defineProps<{
  size?: number
}>()

const size = computed(() => `${props.size ?? 26}px`)
const label = computed(() => brandInitials(brand.name))
</script>

<template>
  <span class="brand-mark" :style="{ width: size, height: size }" :title="brand.name">
    <img v-if="brand.logoUrl" class="brand-mark__img" :src="brand.logoUrl" :alt="brand.name" />
    <span v-else class="brand-mark__initial" :style="{ fontSize: `calc(${size} * 0.42)` }">{{ label }}</span>
  </span>
</template>

<style scoped>
.brand-mark {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex: none;
  overflow: hidden;
}

.brand-mark__img {
  width: 100%;
  height: 100%;
  object-fit: contain;
}

.brand-mark__initial {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  height: 100%;
  font-weight: 700;
  color: #fff;
  letter-spacing: 0.02em;
  background: linear-gradient(135deg, var(--brand-a), var(--brand-c));
  border-radius: 30%;
  text-shadow: 0 1px 2px rgba(0, 0, 0, 0.25);
}

:root[data-theme='dark'] .brand-mark__initial {
  background: linear-gradient(135deg, var(--brand-a-dark), var(--brand-c-dark));
}
</style>