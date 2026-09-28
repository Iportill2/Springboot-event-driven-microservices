import { computed, ref } from 'vue'
import type { AuthUser } from '../api/client'
import { clearAuth, getStoredUser, saveAuth } from '../api/client'

// Estado de sesion a nivel de modulo, no dentro del composable: el router guard,
// el layout y las vistas tienen que observar exactamente la misma referencia.
const user = ref<AuthUser | null>(getStoredUser())

export const authenticated = computed(() => !!user.value)
export const isAdmin = computed(() => !!user.value?.roles?.includes('ADMIN'))

/** Guarda la sesion tanto en localStorage como en el estado reactivo. */
export function applyAuth(auth: AuthUser): void {
  saveAuth(auth)
  user.value = auth
}

/** Cierra la sesion. La usa el logout manual y el interceptor de 401. */
export function logout(): void {
  clearAuth()
  user.value = null
}

export function hasAnyRole(roles?: string[]): boolean {
  if (!roles || roles.length === 0) return true
  return roles.some((role) => user.value?.roles?.includes(role))
}

export function useAuth() {
  return { user, authenticated, isAdmin, logout }
}
