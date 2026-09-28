import { ref } from 'vue'
import { requestWsTicket } from '../api/client'
import type { WsTicket } from '../api/types'

export type SocketStatus = 'idle' | 'connecting' | 'open' | 'closed'

export interface ChatPeer {
  userId: number
  username: string
}

// Un unico socket por pestana y a nivel de modulo. Si el estado viviera dentro
// de un composable, el layout y la vista de chat montarían cada uno el suyo y
// habría dos conexiones del mismo usuario.
let socket: WebSocket | null = null
let opening = false

const status = ref<SocketStatus>('idle')
const lastFrame = ref('')
const identity = ref<ChatPeer | null>(null)

function buildUrl(ticket: string): string {
  const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
  return `${protocol}//${window.location.host}/api/chat/ws?ticket=${encodeURIComponent(ticket)}`
}

/**
 * Abre el canal del chat. Se dispara al iniciar sesión y no al montar la vista,
 * para que el socket exista aunque el usuario no llegue a abrir el chat.
 *
 * Cada intento pide un ticket nuevo: son de un solo uso, así que reutilizar la
 * URL de una conexión anterior fallaría en silencio y parecería un problema de red.
 */
export async function startSession(): Promise<void> {
  if (socket || opening) return
  opening = true
  status.value = 'connecting'
  try {
    const { ticket }: WsTicket = await requestWsTicket()
    const ws = new WebSocket(buildUrl(ticket))
    socket = ws

    ws.onopen = () => {
      status.value = 'open'
    }

    ws.onmessage = (event: MessageEvent<string>) => {
      lastFrame.value = event.data
      try {
        const frame = JSON.parse(event.data) as { type?: string; payload?: unknown }
        if (frame.type === 'connected') {
          identity.value = frame.payload as ChatPeer
        }
      } catch {
        // Un frame que no es JSON no debe tumbar la sesion.
      }
    }

    ws.onerror = () => {
      // El cierre real siempre llega despues por onclose; aqui no se toca estado.
    }

    ws.onclose = () => {
      socket = null
      opening = false
      status.value = 'closed'
      identity.value = null
    }
  } catch {
    // Si el ticket no llega, el login no debe quedar bloqueado por el chat.
    status.value = 'closed'
  } finally {
    opening = false
  }
}

export function stopSession(): void {
  opening = false
  if (!socket) return
  const current = socket
  socket = null
  current.close(1000, 'logout')
}

export function send(frame: unknown): boolean {
  if (!socket || socket.readyState !== WebSocket.OPEN) return false
  socket.send(JSON.stringify(frame))
  return true
}

export function useChatSocket() {
  return { status, lastFrame, identity, startSession, stopSession, send }
}
