<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { api, clearAuth, getStoredUser } from '../api/client'
import ThemeToggle from '../components/ThemeToggle.vue'
import BrandMark from '../components/BrandMark.vue'
import { brand } from '../brand'

interface Product {
  id: number
  name: string
  sku: string
  description?: string
  price: number
}

const router = useRouter()
const user = getStoredUser()
const products = ref<Product[]>([])
const loading = ref(true)
const error = ref('')
const ready = ref(false)

function logout() {
  clearAuth()
  router.push({ name: 'login' })
}

onMounted(async () => {
  requestAnimationFrame(() => {
    requestAnimationFrame(() => (ready.value = true))
  })
  try {
    const res = await api.get('/products')
    products.value = res.data?.data ?? []
  } catch (e: unknown) {
    const err = e as { response?: { data?: { message?: string } } }
    error.value = err?.response?.data?.message ?? 'Error al cargar el catálogo'
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <div :class="['home', { 'is-ready': ready }]">
    <header class="topbar glass">
      <div class="brand">
        <div class="logo">
          <BrandMark :size="24" />
        </div>
        <span class="name">{{ brand.name }}</span>
      </div>

      <div class="user-group">
        <ThemeToggle />
        <div class="user">
          <span class="user-name">{{ user?.username ?? 'invitado' }}</span>
          <span class="roles">{{ user?.roles?.join(', ') }}</span>
        </div>
        <button class="btn btn-ghost" @click="logout">Salir</button>
      </div>
    </header>

    <main class="content">
      <section class="hero">
        <h1 class="reveal" style="--d: 40ms">Hola, {{ user?.username }}</h1>
        <p class="reveal lead" style="--d: 130ms">
          Explora el catálogo. Todo lo que ves aparece en cadena, sin sorpresas.
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
    </main>
  </div>
</template>

<style scoped>
.home {
  position: relative;
  z-index: 1;
}

.topbar {
  position: sticky;
  top: 0;
  z-index: 5;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin: 16px;
  padding: 12px 16px;
  border-radius: 18px;
}

.brand {
  display: flex;
  align-items: center;
  gap: 10px;
}

.logo {
  display: flex;
}

.name {
  font-family: 'Space Grotesk', 'Inter', system-ui, sans-serif;
  font-size: 18px;
  font-weight: 700;
  letter-spacing: -0.02em;
  color: var(--text-h);
}

.user-group {
  display: flex;
  align-items: center;
  gap: 12px;
}

.user {
  display: flex;
  align-items: center;
  gap: 10px;
}

.user-name {
  font-weight: 600;
  color: var(--text-h);
}

.roles {
  color: var(--muted);
  font-size: 12px;
  padding: 3px 10px;
  border: 1px solid var(--glass-border);
  border-radius: 999px;
  background: var(--field-bg);
}

.content {
  max-width: 960px;
  margin: 0 auto;
  padding: 24px 16px 80px;
}

.hero h1 {
  font-size: clamp(30px, 5vw, 44px);
  margin: 12px 0 4px;
  color: var(--text-h);
}

.lead {
  color: var(--muted);
  margin: 0 0 40px;
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

@media (max-width: 560px) {
  .topbar {
    flex-direction: column;
    align-items: flex-start;
  }
}
</style>