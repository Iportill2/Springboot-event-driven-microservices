<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { login } from '../api/client'
import ThemeToggle from '../components/ThemeToggle.vue'
import BrandMark from '../components/BrandMark.vue'
import { brand } from '../brand'

const router = useRouter()
const username = ref('')
const password = ref('')
const error = ref('')
const loading = ref(false)
const ready = ref(false)
const leaving = ref(false)

function exitToHome() {
  leaving.value = true
  window.setTimeout(() => router.push({ name: 'home' }), 720)
}

async function onSubmit() {
  if (leaving.value) return
  error.value = ''
  if (!username.value.trim() || !password.value) {
    error.value = 'Introduce usuario y contraseña'
    return
  }
  loading.value = true
  try {
    await login(username.value.trim(), password.value)
    exitToHome()
  } catch (e: unknown) {
    const err = e as { response?: { data?: { message?: string } } }
    error.value = err?.response?.data?.message ?? 'No se pudo iniciar sesión'
  } finally {
    loading.value = false
  }
}









onMounted(() => {
  requestAnimationFrame(() => {
    requestAnimationFrame(() => (ready.value = true))
  })
})
</script>

<template>
  <main :class="['stage', { ready, leaving }]">
    <ThemeToggle class="stage-toggle" />
    <form class="card glass-strong" @submit.prevent="onSubmit">
      <header
        class="brand"
        style="--d0: 0ms; --d1: 0ms; --tx: -150px; --ty: 24px; --rot: -6deg"
      >
        <div class="logo">
          <BrandMark />
        </div>
        <span class="name">{{ brand.name }}</span>
      </header>

      <p
        class="subtitle"
        style="--d0: 80ms; --d1: 70ms; --tx: 190px; --ty: -12px; --rot: 4deg"
      >
        Acceso al panel
      </p>

      <div
        class="field-row"
        style="--d0: 160ms; --d1: 130ms; --tx: -220px; --ty: 16px; --rot: -3deg"
      >
        <label for="username">Usuario</label>
        <input
          id="username"
          v-model="username"
          class="field"
          type="text"
          autocomplete="username"
          placeholder="admin"
          :disabled="loading"
        />
      </div>

      <div
        class="field-row"
        style="--d0: 250ms; --d1: 200ms; --tx: 220px; --ty: -16px; --rot: 3deg"
      >
        <label for="password">Contraseña</label>
        <input
          id="password"
          v-model="password"
          class="field"
          type="password"
          autocomplete="current-password"
          placeholder="••••••••"
          :disabled="loading"
        />
      </div>

      <p v-if="error" class="error reveal-in">{{ error }}</p>

      <button
        type="submit"
        class="btn btn-primary submit"
        :disabled="loading"
        style="--d0: 350ms; --d1: 260ms; --tx: 0px; --ty: 190px; --rot: 2deg"
      >
        {{ loading ? 'Entrando…' : 'Entrar' }}
      </button>

      <p
        class="hint"
        style="--d0: 440ms; --d1: 320ms; --tx: 0px; --ty: -150px; --rot: -2deg"
      >
        Demo: admin / admin123
      </p>

      <p
        class="hint link-hint"
        style="--d0: 520ms; --d1: 380ms; --tx: 0px; --ty: -150px; --rot: -2deg"
      >
        ¿No tienes cuenta? <RouterLink to="/register">Regístrate</RouterLink>
      </p>
    </form>
  </main>
</template>

<style scoped>
.stage {
  min-height: 100svh;
  display: grid;
  place-items: center;
  padding: 24px;
  position: relative;
  z-index: 1;
}

.stage-toggle {
  position: fixed;
  top: 18px;
  right: 18px;
  z-index: 3;
}

.card {
  width: 100%;
  max-width: 360px;
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 30px 28px 26px;
  border-radius: 22px;
  opacity: 0;
  transform: translateY(18px) scale(0.98);
}

.ready .card {
  transition:
    opacity 0.5s ease,
    transform 0.6s cubic-bezier(0.22, 1, 0.36, 1);
  opacity: 1;
  transform: none;
}

/* Entrada escalonada de cada elemento */
.card > * {
  opacity: 0;
  transform: translateY(16px);
}

.ready .card > * {
  transition:
    opacity 0.5s ease var(--d0, 0ms),
    transform 0.55s cubic-bezier(0.22, 1, 0.36, 1) var(--d0, 0ms),
    filter 0.5s ease var(--d0, 0ms);
  opacity: 1;
  transform: none;
}

/* Salida: cada elemento vuela hacia su borde */
.leaving .card,
.leaving .card > * {
  filter: blur(10px);
}

.leaving .card > * {
  transition:
    opacity 0.45s ease var(--d1, 0ms),
    transform 0.65s cubic-bezier(0.4, 0, 0.2, 1) var(--d1, 0ms),
    filter 0.5s ease var(--d1, 0ms);
  opacity: 0;
  transform: translate3d(var(--tx, 0px), var(--ty, 0px), 0) rotate(var(--rot, 0deg))
    scale(0.92);
}

.leaving .card {
  transition: opacity 0.5s ease 0.45s, transform 0.7s ease 0.45s;
  opacity: 0;
  transform: scale(0.94);
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
  font-size: 26px;
  font-weight: 700;
  letter-spacing: -0.03em;
  color: var(--text-h);
}

.subtitle {
  margin: 0 0 6px;
  color: var(--muted);
}

.field-row {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.field-row label {
  font-size: 13px;
  font-weight: 600;
  color: var(--text-h);
}

.error {
  color: var(--danger);
  font-size: 13px;
  margin: 0;
}

.submit {
  margin-top: 6px;
  width: 100%;
}

.hint {
  color: var(--muted);
  font-size: 12px;
  text-align: center;
  margin: 0;
}

.link-hint {
  margin-top: 4px;
}

.link-hint a {
  color: var(--accent-2);
  text-decoration: none;
  font-weight: 600;
}

.link-hint a:hover {
  text-decoration: underline;
}
</style>