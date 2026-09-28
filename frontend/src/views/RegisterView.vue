<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { register, resendVerification, type RegisterPayload } from '../api/client'
import ThemeToggle from '../components/ThemeToggle.vue'
import BrandMark from '../components/BrandMark.vue'
import { brand } from '../brand'

const router = useRouter()
const username = ref('')
const email = ref('')
const fullName = ref('')
const phone = ref('')
const password = ref('')
const confirm = ref('')
const newsletter = ref(false)
const terms = ref(false)
const error = ref('')
const loading = ref(false)
const ready = ref(false)
const leaving = ref(false)
const sent = ref(false)
const resending = ref(false)
const resendMsg = ref('')

function validate(): string[] {
  const problems: string[] = []
  if (username.value.trim().length < 3) problems.push('El usuario debe tener al menos 3 caracteres')
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email.value.trim())) problems.push('Introduce un email válido')
  if (password.value.length < 8) problems.push('La contraseña debe tener al menos 8 caracteres')
  if (confirm.value !== password.value) problems.push('Las contraseñas no coinciden')
  if (phone.value.trim() && !/^[+0-9()\- ]+$/.test(phone.value.trim())) problems.push('El teléfono solo puede contener números, +, - y espacios')
  if (!terms.value) problems.push('Debes aceptar los términos y la política de privacidad')
  return problems
}

async function onSubmit() {
  if (leaving.value) return
  error.value = ''
  const problems = validate()
  if (problems.length) {
    error.value = problems.join('. ')
    return
  }
  loading.value = true
  try {
    const payload: RegisterPayload = {
      username: username.value.trim(),
      email: email.value.trim(),
      password: password.value,
      newsletter: newsletter.value,
    }
    if (fullName.value.trim()) payload.fullName = fullName.value.trim()
    if (phone.value.trim()) payload.phone = phone.value.trim()
    await register(payload)
    sent.value = true
  } catch (e: unknown) {
    const err = e as { response?: { data?: { message?: string } } }
    error.value = err?.response?.data?.message ?? 'No se pudo crear la cuenta'
  } finally {
    loading.value = false
  }
}

function goHome() {
  leaving.value = true
  window.setTimeout(() => router.push({ name: 'home' }), 650)
}

async function onResend() {
  if (resending.value) return
  resending.value = true
  resendMsg.value = ''
  try {
    await resendVerification(email.value.trim())
    resendMsg.value = 'Correo reenviado. Revisa tu bandeja de entrada.'
  } catch {
    resendMsg.value = 'No se pudo reenviar. Inténtalo de nuevo en unos segundos.'
  } finally {
    resending.value = false
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
    <form class="card glass-strong" v-if="!sent" @submit.prevent="onSubmit">
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
        style="--d0: 70ms; --d1: 60ms; --tx: 190px; --ty: -12px; --rot: 4deg"
      >
        Crea tu cuenta
      </p>

      <div
        class="field-row"
        style="--d0: 140ms; --d1: 120ms; --tx: -220px; --ty: 16px; --rot: -3deg"
      >
        <label for="username">Usuario</label>
        <input
          id="username"
          v-model="username"
          class="field"
          type="text"
          autocomplete="username"
          placeholder="Min. 3 caracteres"
          :disabled="loading"
        />
      </div>

      <div
        class="field-row"
        style="--d0: 210ms; --d1: 180ms; --tx: 220px; --ty: -16px; --rot: 3deg"
      >
        <label for="email">Email</label>
        <input
          id="email"
          v-model="email"
          class="field"
          type="email"
          autocomplete="email"
          placeholder="tucorreo@ejemplo.com"
          :disabled="loading"
        />
      </div>

      <div
        class="two-col"
        style="--d0: 280ms; --d1: 240ms; --tx: -220px; --ty: 16px; --rot: -3deg"
      >
        <div class="field-row">
          <label for="fullName">Nombre completo</label>
          <input
            id="fullName"
            v-model="fullName"
            class="field"
            type="text"
            autocomplete="name"
            placeholder="Opcional"
            :disabled="loading"
          />
        </div>

        <div class="field-row">
          <label for="phone">Teléfono</label>
          <input
            id="phone"
            v-model="phone"
            class="field"
            type="tel"
            autocomplete="tel"
            placeholder="Opcional"
            :disabled="loading"
          />
        </div>
      </div>

      <div
        class="two-col"
        style="--d0: 350ms; --d1: 300ms; --tx: 220px; --ty: -16px; --rot: 3deg"
      >
        <div class="field-row">
          <label for="password">Contraseña</label>
          <input
            id="password"
            v-model="password"
            class="field"
            type="password"
            autocomplete="new-password"
            placeholder="Min. 8 caracteres"
            :disabled="loading"
          />
        </div>

        <div class="field-row">
          <label for="confirm">Confirmar contraseña</label>
          <input
            id="confirm"
            v-model="confirm"
            class="field"
            type="password"
            autocomplete="new-password"
            placeholder="Repítela"
            :disabled="loading"
          />
        </div>
      </div>

      <div
        class="checks"
        style="--d0: 420ms; --d1: 360ms; --tx: 0px; --ty: 180px; --rot: -2deg"
      >
        <label class="check-row">
          <input v-model="terms" type="checkbox" :disabled="loading" />
          <span>He leído y acepto los términos y la política de privacidad.</span>
        </label>
        <label class="check-row">
          <input v-model="newsletter" type="checkbox" :disabled="loading" />
          <span>Quiero recibir ofertas y novedades.</span>
        </label>
      </div>

      <p v-if="error" class="error reveal-in">{{ error }}</p>

      <button
        type="submit"
        class="btn btn-primary submit"
        :disabled="loading"
        style="--d0: 500ms; --d1: 420ms; --tx: 0px; --ty: 190px; --rot: 2deg"
      >
        {{ loading ? 'Creando cuenta…' : 'Registrarme' }}
      </button>

      <p
        class="hint"
        style="--d0: 570ms; --d1: 480ms; --tx: 0px; --ty: -150px; --rot: -2deg"
      >
        <RouterLink to="/login">¿Ya tienes cuenta? Entrar</RouterLink>
      </p>
    </form>

    <div
      v-else
      class="card glass-strong sent"
      style="--d0: 0ms; --d1: 0ms; --tx: 0px; --ty: 0px; --rot: 0deg"
    >
      <div class="sent-icon">✓</div>
      <h1 class="sent-title">Revisa tu correo</h1>
      <p class="sent-text">
        Hemos enviado un enlace de verificación a
        <strong>{{ email }}</strong>. Púlsalo para activar tu cuenta y poder
        iniciar sesión.
      </p>
      <button
        type="button"
        class="btn btn-primary submit"
        :disabled="resending"
        @click="onResend"
      >
        {{ resending ? 'Reenviando…' : 'Reenviar correo' }}
      </button>
      <p v-if="resendMsg" class="resend-msg">{{ resendMsg }}</p>
      <button type="button" class="btn btn-ghost submit" @click="goHome">
        Volver a la tienda
      </button>
      <p class="hint">
        <RouterLink to="/login">Ir a iniciar sesión</RouterLink>
      </p>
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
  margin: 0 0 4px;
  color: var(--muted);
}

.two-col {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
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

.checks {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-top: 2px;
}

.check-row {
  display: flex;
  align-items: flex-start;
  gap: 9px;
  font-size: 13px;
  line-height: 1.4;
  color: var(--text-h);
  cursor: pointer;
}

.check-row input {
  width: 16px;
  height: 16px;
  margin-top: 1px;
  accent-color: var(--accent-2);
  cursor: pointer;
  flex: none;
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
  font-size: 13px;
  text-align: center;
  margin: 0;
}

.hint a {
  color: var(--accent-2);
  text-decoration: none;
  font-weight: 600;
}

.hint a:hover {
  text-decoration: underline;
}

.sent {
  text-align: center;
  align-items: center;
}

.sent-icon {
  width: 64px;
  height: 64px;
  border-radius: 50%;
  display: grid;
  place-items: center;
  font-size: 30px;
  font-weight: 700;
  color: #fff;
  background: linear-gradient(135deg, var(--accent), var(--accent-2));
  box-shadow: 0 12px 30px -10px var(--accent-2);
  margin: 4px auto 0;
}

.sent-title {
  font-family: 'Space Grotesk', 'Inter', system-ui, sans-serif;
  font-size: 24px;
  font-weight: 700;
  letter-spacing: -0.03em;
  color: var(--text-h);
  margin: 10px 0 0;
}

.sent-text {
  color: var(--muted);
  font-size: 14px;
  line-height: 1.55;
  margin: 0;
}

.sent-text strong {
  color: var(--text-h);
}

.resend-msg {
  color: var(--accent-2);
  font-size: 13px;
  margin: -4px 0 0;
}

@media (max-width: 480px) {
  .two-col {
    grid-template-columns: 1fr;
  }
}
</style>