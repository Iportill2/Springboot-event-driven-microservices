<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useChatSocket } from '../composables/useChatSocket'
import { formatDate } from '../format'

const { status, lastFrame, identity, startSession, send } = useChatSocket()
const ready = ref(false)
const reconnecting = ref(false)

const LABEL: Record<string, string> = {
  idle: 'Sin iniciar',
  connecting: 'Conectando',
  open: 'Conectado',
  closed: 'Desconectado',
}

function ping() {
  send({ type: 'ping' })
}

function echo() {
  send({ type: 'echo', payload: { mensaje: 'Hola desde el navegador', enviado: formatDate(new Date().toISOString()) } })
}

async function reconnect() {
  reconnecting.value = true
  try {
    await startSession()
  } finally {
    reconnecting.value = false
  }
}

onMounted(() => {
  requestAnimationFrame(() => {
    requestAnimationFrame(() => (ready.value = true))
  })
})
</script>

<template>
  <div :class="['page', { 'is-ready': ready }]">
    <div class="page-head">
      <h1 class="reveal" style="--d: 40ms">Chat</h1>
      <p class="reveal" style="--d: 100ms">
        Canal WebSocket entre usuarios. El socket se abre al iniciar sesión y se cierra al salir.
      </p>
    </div>

    <section class="panel glass reveal" style="--d: 160ms">
      <div class="state">
        <span :class="['badge', status === 'open' ? 'badge-ok' : 'badge-danger']">
          {{ LABEL[status] ?? status }}
        </span>
        <span v-if="identity" class="muted">
          {{ identity.username }} · id {{ identity.userId }}
        </span>
        <span v-else class="muted">Sin identidad de socket</span>
      </div>

      <p class="hint">
        La identidad se resuelve con un ticket de un solo uso emitido por
        <code>POST /api/chat/ws-ticket</code>, porque el navegador no puede enviar el
        <code>Authorization</code> en un handshake. Por eso reconectar pide un ticket nuevo.
      </p>

      <div class="form-actions">
        <button type="button" class="btn btn-primary btn-sm" :disabled="status !== 'open'" @click="ping">
          Enviar ping
        </button>
        <button type="button" class="btn btn-ghost btn-sm" :disabled="status !== 'open'" @click="echo">
          Enviar eco
        </button>
        <button
          type="button"
          class="btn btn-ghost btn-sm"
          :disabled="reconnecting || status === 'open' || status === 'connecting'"
          @click="reconnect"
        >
          {{ reconnecting ? 'Conectando…' : 'Reconectar' }}
        </button>
      </div>
    </section>

    <section class="panel glass reveal" style="--d: 230ms">
      <h2>Último frame recibido</h2>
      <pre v-if="lastFrame" class="frame">{{ lastFrame }}</pre>
      <p v-else class="muted">Todavía no ha llegado nada.</p>
    </section>

    <section class="panel glass reveal" style="--d: 300ms">
      <h2>Pendiente de la siguiente iteración</h2>
      <ul class="todo">
        <li>Enviar mensajes entre dos usuarios y enrutarlos a la sesión del destinatario</li>
        <li>Persistir en MongoDB con <code>conversationId</code> derivado</li>
        <li>Historial por REST y lista de conversaciones</li>
        <li>Presencia con <code>presence:&lt;userId&gt;</code> en Redis</li>
        <li>Reconexión automática, heartbeat y cierre entre pestañas</li>
      </ul>
    </section>
  </div>
</template>

<style scoped>
.page {
  position: relative;
  z-index: 1;
}

.state {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.badge-ok {
  color: var(--accent-2);
}

.hint {
  margin: 16px 0 0;
  font-size: 13px;
  color: var(--muted);
  line-height: 1.6;
}

.hint code,
.todo code {
  font-size: 12px;
  color: var(--accent-2);
  background: var(--field-bg);
  border: 1px solid var(--glass-border);
  padding: 2px 6px;
  border-radius: 6px;
}

.panel h2 {
  margin: 0 0 14px;
  font-size: 16px;
  color: var(--text-h);
}

.frame {
  margin: 0;
  padding: 14px;
  border-radius: 12px;
  background: var(--field-bg);
  border: 1px solid var(--glass-border);
  color: var(--text-h);
  font-size: 13px;
  white-space: pre-wrap;
  word-break: break-word;
}

.todo {
  margin: 0;
  padding-left: 20px;
  color: var(--muted);
  font-size: 14px;
  line-height: 1.9;
}

.muted {
  color: var(--muted);
  margin: 0;
  font-size: 14px;
}
</style>
