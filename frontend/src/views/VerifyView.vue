<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { verifyEmail } from '../api/client'
import ThemeToggle from '../components/ThemeToggle.vue'
import BrandMark from '../components/BrandMark.vue'
import { brand } from '../brand'

const route = useRoute()
const ready = ref(false)
const checking = ref(true)
const ok = ref(false)
const verifiedEmail = ref('')
const error = ref('')

onMounted(async () => {
  requestAnimationFrame(() => requestAnimationFrame(() => (ready.value = true)))
  const token = typeof route.query.token === 'string' ? route.query.token : ''
  if (!token) {
    checking.value = false
    error.value = 'Este enlace es incompleto o no es válido.'
    return
  }
  try {
    verifiedEmail.value = await verifyEmail(token)
    ok.value = true
  } catch (e: unknown) {
    const err = e as { response?: { data?: { message?: string } } }
    error.value = err?.response?.data?.message ?? 'No se pudo verificar la cuenta.'
  } finally {
    checking.value = false
  }
})
</script>

<template>
  <main :class="['stage', { ready }]">
    <ThemeToggle class="stage-toggle" />
    <div class="card glass-strong">
      <header class="brand">
        <div class="logo">
          <BrandMark />
        </div>
        <span class="name">{{ brand.name }}</span>
      </header>

      <div v-if="checking" class="body-spinner">
        <span class="spinner"></span>
        <p>Verificando tu cuenta…</p>
      </div>

      <div v-else-if="ok" class="state sent">
        <div class="state-icon ok">✓</div>
        <h1 class="state-title">Cuenta verificada</h1>
        <p class="state-text">
          La cuenta <strong>{{ verifiedEmail }}</strong> ya está activa.
          ¡Bienvenido a {{ brand.name }}!
        </p>
        <RouterLink to="/login" class="btn btn-primary submit-link">Iniciar sesión</RouterLink>
      </div>

      <div v-else class="state">
        <div class="state-icon bad">✕</div>
        <h1 class="state-title">No se pudo verificar</h1>
        <p class="state-text">{{ error }}</p>
        <RouterLink to="/register" class="btn btn-primary submit-link">Crear una cuenta</RouterLink>
      </div>
    </div>
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
  max-width: 420px;
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 30px 28px 24px;
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

.state,
.body-spinner {
  text-align: center;
  align-items: center;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.body-spinner p {
  color: var(--muted);
  font-size: 14px;
  margin: 0;
}

.state-icon {
  width: 64px;
  height: 64px;
  border-radius: 50%;
  display: grid;
  place-items: center;
  font-size: 28px;
  font-weight: 700;
  color: #fff;
  margin: 4px auto 0;
}

.state-icon.ok {
  background: linear-gradient(135deg, var(--accent), var(--accent-2));
  box-shadow: 0 12px 30px -10px var(--accent-2);
}

.state-icon.bad {
  background: linear-gradient(135deg, var(--danger), #f97316);
}

.state-title {
  font-family: 'Space Grotesk', 'Inter', system-ui, sans-serif;
  font-size: 24px;
  font-weight: 700;
  letter-spacing: -0.03em;
  color: var(--text-h);
  margin: 6px 0 0;
}

.state-text {
  color: var(--muted);
  font-size: 14px;
  line-height: 1.55;
  margin: 0;
}

.state-text strong {
  color: var(--text-h);
}

.submit-link {
  margin-top: 6px;
  width: 100%;
  text-align: center;
  text-decoration: none;
}

.spinner {
  width: 34px;
  height: 34px;
  border-radius: 50%;
  border: 3px solid var(--muted);
  border-top-color: var(--accent-2);
  animation: spin 0.8s linear infinite;
  margin-top: 8px;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}
</style>