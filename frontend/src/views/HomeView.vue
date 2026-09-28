<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { errorMessage, fetchProducts } from '../api/client'
import type { Product } from '../api/types'
import { useAuth } from '../composables/useAuth'

const { user, authenticated, isAdmin } = useAuth()
const products = ref<Product[]>([])
const loading = ref(true)
const error = ref('')
const ready = ref(false)

onMounted(async () => {
  requestAnimationFrame(() => {
    requestAnimationFrame(() => (ready.value = true))
  })
  try {
    products.value = await fetchProducts()
  } catch (e: unknown) {
    error.value = errorMessage(e, 'Error al cargar el catálogo')
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <div :class="['home', { 'is-ready': ready }]">
    <section class="hero">
      <h1 class="reveal" style="--d: 40ms">
        {{ authenticated ? `Hola, ${user?.username}` : 'Catálogo' }}
      </h1>
      <p class="reveal lead" style="--d: 130ms">
        Explora el catálogo. Todo lo que ves aparece en cadena, sin sorpresas.
      </p>
      <p v-if="!authenticated" class="reveal guest-note" style="--d: 190ms">
        Estás navegando como invitado.
        <RouterLink to="/login">Inicia sesión</RouterLink> para ver tu perfil y el chat.
      </p>
      <p v-else-if="isAdmin" class="reveal guest-note" style="--d: 190ms">
        <RouterLink to="/admin/products">Gestiona el catálogo</RouterLink> o
        <RouterLink to="/admin/users">consulta los usuarios</RouterLink>.
      </p>
    </section>

    <section class="catalog">
      <div class="catalog-head reveal" style="--d: 220ms">
        <h2>Catálogo</h2>
        <span v-if="!loading && !error" class="count"
          >{{ products.length }} producto{{ products.length === 1 ? '' : 's' }}</span
        >
      </div>

      <p v-if="loading" class="muted">Cargando productos…</p>
      <p v-else-if="error" class="error">{{ error }}</p>
      <p v-else-if="!products.length" class="muted">No hay productos.</p>

      <TransitionGroup v-else name="card" tag="div" class="grid">
        <article
          v-for="(p, i) in products"
          :key="p.id"
          class="product glass"
          :style="{ '--d': `${i * 70}ms` }"
        >
          <div class="price-pill">
            <span>{{ p.price.toFixed(2) }} €</span>
          </div>
          <h3>{{ p.name }}</h3>
          <p>{{ p.description }}</p>
          <code>{{ p.sku }}</code>
        </article>
      </TransitionGroup>
    </section>
  </div>
</template>

<style scoped>
.home {
  position: relative;
  z-index: 1;
}

.hero h1 {
  font-size: clamp(30px, 5vw, 44px);
  margin: 12px 0 4px;
  color: var(--text-h);
}

.lead {
  color: var(--muted);
  margin: 0 0 8px;
}

.guest-note {
  color: var(--muted);
  font-size: 14px;
  margin: 0 0 32px;
}

.guest-note a {
  color: var(--accent-2);
  font-weight: 600;
  text-decoration: none;
}

.guest-note a:hover {
  text-decoration: underline;
}

.catalog-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  margin-bottom: 16px;
}

.catalog-head h2 {
  margin: 0;
  color: var(--text-h);
}

.count {
  color: var(--muted);
  font-size: 13px;
}

.grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(230px, 1fr));
  gap: 16px;
}

.product {
  position: relative;
  border-radius: 18px;
  padding: 18px;
  transition:
    transform 0.25s cubic-bezier(0.22, 1, 0.36, 1),
    box-shadow 0.25s ease,
    border-color 0.25s ease;
}

.product:hover {
  transform: translateY(-5px);
  border-color: var(--accent-2);
  box-shadow: 0 22px 44px -18px rgba(136, 84, 247, 0.55);
}

.price-pill {
  position: absolute;
  top: 14px;
  right: 14px;
  padding: 4px 10px;
  border-radius: 999px;
  font-size: 13px;
  font-weight: 700;
  color: #fff;
  background: var(--grad-accent);
  box-shadow: 0 8px 20px -8px rgba(136, 84, 247, 0.7);
}

.product h3 {
  margin: 20px 0 4px;
  padding-right: 84px;
  color: var(--text-h);
}

.product p {
  color: var(--muted);
  font-size: 14px;
  margin: 0 0 12px;
  min-height: 1.4em;
}

.product code {
  font-size: 12px;
  color: var(--accent-2);
  background: var(--field-bg);
  border: 1px solid var(--glass-border);
  padding: 3px 8px;
  border-radius: 8px;
}

.muted {
  color: var(--muted);
}

.error {
  color: var(--danger);
}

/* Entrada en cadena de las tarjetas */
.card-enter-active {
  transition:
    opacity 0.5s ease var(--d, 0ms),
    transform 0.6s cubic-bezier(0.22, 1, 0.36, 1) var(--d, 0ms);
}

.card-enter-from {
  opacity: 0;
  transform: translateY(26px) scale(0.97);
}
</style>
