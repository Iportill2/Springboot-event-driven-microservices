<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { errorMessage, fetchUsers } from '../../api/client'
import type { UserProfile } from '../../api/types'
import { formatDate } from '../../format'

const users = ref<UserProfile[]>([])
const loading = ref(true)
const notice = ref<{ text: string; kind: 'ok' | 'error' } | null>(null)
const ready = ref(false)

onMounted(async () => {
  requestAnimationFrame(() => {
    requestAnimationFrame(() => (ready.value = true))
  })
  await load()
})

async function load() {
  try {
    users.value = await fetchUsers()
  } catch (e: unknown) {
    notice.value = { text: errorMessage(e, 'No se pudo cargar el listado de usuarios'), kind: 'error' }
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div :class="['page', { 'is-ready': ready }]">
    <div class="page-head">
      <h1 class="reveal" style="--d: 40ms">Usuarios</h1>
      <p class="reveal" style="--d: 100ms">
        Cuentas registradas en el servicio de usuarios. Solo lectura.
      </p>
    </div>

    <p v-if="notice" :class="['notice', notice.kind === 'error' ? 'notice-error' : 'notice-ok']">
      {{ notice.text }}
    </p>

    <section class="panel glass reveal" style="--d: 160ms">
      <p v-if="loading" class="muted">Cargando…</p>
      <p v-else-if="!users.length" class="muted">No hay usuarios.</p>
      <div v-else class="table-wrap">
        <table class="data-table">
          <thead>
            <tr>
              <th>Id</th>
              <th>Usuario</th>
              <th>Email</th>
              <th>Rol</th>
              <th>Estado</th>
              <th>Alta</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="u in users" :key="u.id">
              <td class="num">{{ u.id }}</td>
              <td>{{ u.username }}</td>
              <td>{{ u.email }}</td>
              <td>
                <span :class="u.role === 'ADMIN' ? 'badge badge-accent' : 'badge'">{{ u.role }}</span>
              </td>
              <td>
                <span :class="u.enabled ? 'badge' : 'badge badge-danger'">
                  {{ u.enabled ? 'Activo' : 'Deshabilitado' }}
                </span>
              </td>
              <td>{{ formatDate(u.createdAt) }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>
  </div>
</template>

<style scoped>
.page {
  position: relative;
  z-index: 1;
}

.muted {
  color: var(--muted);
  margin: 0;
}
</style>
