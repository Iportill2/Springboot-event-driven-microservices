<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { errorMessage, fetchProfile, getToken } from '../api/client'
import type { UserProfile } from '../api/types'
import { useAuth } from '../composables/useAuth'
import { brandInitials } from '../brand'
import { formatDate } from '../format'

const { user, isAdmin } = useAuth()
const profile = ref<UserProfile | null>(null)
const loading = ref(true)
const error = ref('')
const ready = ref(false)

// El token ya esta en localStorage, asi que leer su 'exp' no expone nada nuevo.
// Sirve para explicar cuando va a caducar la sesion y por que el interceptor
// devuelve al login pasado ese momento.
function sessionExpiry(): string {
  const token = getToken()
  if (!token) return '—'
  const segment = token.split('.')[1]
  if (!segment) return '—'
  try {
    const normalized = segment.replace(/-/g, '+').replace(/_/g, '/')
    const padded = normalized.padEnd(normalized.length + ((4 - (normalized.length % 4)) % 4), '=')
    const payload = JSON.parse(atob(padded)) as { exp?: number }
    return payload.exp ? formatDate(new Date(payload.exp * 1000).toISOString()) : '—'
  } catch {
    return '—'
  }
}

onMounted(async () => {
  requestAnimationFrame(() => {
    requestAnimationFrame(() => (ready.value = true))
  })
  try {
    profile.value = await fetchProfile()
  } catch (e: unknown) {
    error.value = errorMessage(e, 'No se pudo cargar el perfil')
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <div :class="['profile', { 'is-ready': ready }]">
    <div class="page-head">
      <h1 class="reveal" style="--d: 40ms">Mi perfil</h1>
      <p class="reveal" style="--d: 100ms">Datos de la cuenta que tienes en el servicio de usuarios.</p>
    </div>

    <p v-if="loading" class="notice">Cargando…</p>
    <p v-else-if="error" class="notice notice-error">{{ error }}</p>

    <section v-else-if="profile" class="panel glass reveal" style="--d: 160ms">
      <header class="identity">
        <span class="avatar">{{ brandInitials(profile.username) }}</span>
        <div>
          <h2>{{ profile.username }}</h2>
          <div class="tags">
            <span class="badge badge-accent">{{ profile.role }}</span>
            <span v-if="!profile.enabled" class="badge badge-danger">Cuenta deshabilitada</span>
          </div>
        </div>
      </header>

      <dl class="facts">
        <div>
          <dt>Identificador</dt>
          <dd>{{ profile.id }}</dd>
        </div>
        <div>
          <dt>Email</dt>
          <dd>{{ profile.email }}</dd>
        </div>
        <div>
          <dt>Alta</dt>
          <dd>{{ formatDate(profile.createdAt) }}</dd>
        </div>
        <div>
          <dt>Caduca la sesión</dt>
          <dd>{{ sessionExpiry() }}</dd>
        </div>
      </dl>
    </section>

    <section class="panel glass reveal" style="--d: 230ms">
      <h3>Accesos rápidos</h3>
      <div class="quick">
        <RouterLink class="btn btn-ghost" to="/">Catálogo</RouterLink>
        <RouterLink v-if="isAdmin" class="btn btn-ghost" to="/admin/products">Productos</RouterLink>
        <RouterLink v-if="isAdmin" class="btn btn-ghost" to="/admin/users">Usuarios</RouterLink>
      </div>
      <p class="hint">Sesión iniciada como <strong>{{ user?.username }}</strong>.</p>
    </section>
  </div>
</template>

<style scoped>
.profile {
  position: relative;
  z-index: 1;
}

.identity {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 22px;
}

.avatar {
  display: grid;
  place-items: center;
  width: 56px;
  height: 56px;
  border-radius: 18px;
  font-size: 22px;
  font-weight: 700;
  color: #fff;
  background: var(--grad-accent);
  box-shadow: 0 12px 28px -12px rgba(136, 84, 247, 0.7);
  flex: none;
}

.identity h2 {
  margin: 0 0 8px;
  font-size: 22px;
  color: var(--text-h);
}

.tags {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.facts {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: 18px;
  margin: 0;
}

.facts dt {
  font-size: 12px;
  text-transform: uppercase;
  letter-spacing: 0.04em;
  color: var(--muted);
  font-weight: 700;
  margin-bottom: 4px;
}

.facts dd {
  margin: 0;
  color: var(--text-h);
  font-size: 15px;
}

.panel h3 {
  margin: 0 0 14px;
  font-size: 16px;
  color: var(--text-h);
}

.quick {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}

.quick a {
  text-decoration: none;
}

.hint {
  margin: 16px 0 0;
  font-size: 13px;
  color: var(--muted);
}
</style>
