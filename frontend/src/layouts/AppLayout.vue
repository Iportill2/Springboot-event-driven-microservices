<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink, useRouter } from 'vue-router'
import BrandMark from '../components/BrandMark.vue'
import ThemeToggle from '../components/ThemeToggle.vue'
import { brand } from '../brand'
import { useAuth } from '../composables/useAuth'
import { visibleItems } from '../nav'

const router = useRouter()
const { user, authenticated, logout } = useAuth()

const items = computed(() =>
  visibleItems({
    authenticated: authenticated.value,
    roles: user.value?.roles ?? [],
  }),
)

// Separador antes del primer item restringido por rol, para que el bloque de
// administracion se lea como otra seccion y no como una continuacion.
const dividerBefore = computed(() => items.value.findIndex((item) => item.roles?.length))

function onLogout() {
  logout()
  void router.push({ name: 'login' })
}
</script>

<template>
  <div class="shell">
    <header class="topbar glass">
      <RouterLink to="/" class="brand">
        <span class="logo"><BrandMark :size="24" /></span>
        <span class="name">{{ brand.name }}</span>
      </RouterLink>

      <nav class="nav" aria-label="Principal">
        <template v-for="(item, i) in items" :key="item.label">
          <span v-if="i === dividerBefore" class="nav-divider" aria-hidden="true"></span>
          <span v-if="item.soon" class="nav-item is-soon" aria-disabled="true" :title="`${item.label}: próximamente`">
            {{ item.label }}
            <span class="soon-badge">próximamente</span>
          </span>
          <RouterLink v-else-if="item.to" :to="item.to" class="nav-item">
            {{ item.label }}
          </RouterLink>
        </template>
      </nav>

      <div class="user-group">
        <ThemeToggle />
        <template v-if="authenticated">
          <div class="user">
            <span class="user-name">{{ user?.username }}</span>
            <span v-if="user?.roles?.length" class="roles">{{ user.roles.join(', ') }}</span>
          </div>
          <button type="button" class="btn btn-ghost" @click="onLogout">Salir</button>
        </template>
        <template v-else>
          <RouterLink to="/login" class="btn btn-ghost">Entrar</RouterLink>
          <RouterLink to="/register" class="btn btn-primary">Crear cuenta</RouterLink>
        </template>
      </div>
    </header>

    <main class="content">
      <slot />
    </main>
  </div>
</template>

<style scoped>
.shell {
  position: relative;
  z-index: 1;
}

.topbar {
  position: sticky;
  top: 0;
  z-index: 5;
  display: flex;
  align-items: center;
  gap: 18px;
  margin: 16px;
  padding: 10px 14px;
  border-radius: 18px;
}

.brand {
  display: flex;
  align-items: center;
  gap: 10px;
  text-decoration: none;
  flex: none;
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

.nav {
  display: flex;
  align-items: center;
  gap: 2px;
  flex: 1;
  min-width: 0;
  overflow-x: auto;
  scrollbar-width: none;
}

.nav::-webkit-scrollbar {
  display: none;
}

.nav-divider {
  width: 1px;
  height: 20px;
  margin: 0 8px;
  background: var(--glass-border);
  flex: none;
}

.nav-item {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 8px 12px;
  border-radius: 10px;
  font-size: 14px;
  font-weight: 600;
  color: var(--muted);
  text-decoration: none;
  white-space: nowrap;
  cursor: pointer;
  transition:
    color 0.2s ease,
    background 0.2s ease;
}

.nav-item:hover {
  color: var(--text-h);
  background: var(--field-bg);
}

/* Solo el enlace exacto: '/' como prefijo dejaria el Catalogo activo en todo. */
.nav-item.router-link-exact-active {
  color: var(--text-h);
  background: var(--field-bg);
}

.nav-item.is-soon {
  color: var(--muted);
  opacity: 0.55;
  cursor: default;
}

.nav-item.is-soon:hover {
  color: var(--muted);
  background: transparent;
}

.soon-badge {
  font-size: 10px;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.04em;
  padding: 2px 6px;
  border-radius: 999px;
  color: var(--accent-2);
  background: var(--field-bg);
  border: 1px solid var(--glass-border);
}

.user-group {
  display: flex;
  align-items: center;
  gap: 10px;
  flex: none;
}

.user {
  display: flex;
  align-items: center;
  gap: 8px;
}

.user-name {
  font-weight: 600;
  color: var(--text-h);
  font-size: 14px;
}

.roles {
  color: var(--muted);
  font-size: 11px;
  padding: 3px 9px;
  border: 1px solid var(--glass-border);
  border-radius: 999px;
  background: var(--field-bg);
  white-space: nowrap;
}

.btn-ghost,
.btn-primary {
  text-decoration: none;
  font-size: 14px;
  padding: 9px 14px;
}

.content {
  max-width: 1120px;
  margin: 0 auto;
  padding: 20px 16px 80px;
}

@media (max-width: 900px) {
  .topbar {
    flex-wrap: wrap;
  }

  .nav {
    order: 3;
    width: 100%;
  }
}
</style>
