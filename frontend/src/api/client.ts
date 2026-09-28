import axios from 'axios'
import type { AxiosRequestConfig, AxiosResponse } from 'axios'
import type {
  ApiResponse,
  Product,
  ProductPayload,
  UserProfile,
  WsTicket,
} from './types'

const TOKEN_KEY = 'ed_token'
const USER_KEY = 'ed_user'

export interface AuthUser {
  token: string
  username: string
  userId: number
  roles: string[]
}

export function getToken(): string | null {
  return localStorage.getItem(TOKEN_KEY)
}

export function getStoredUser(): AuthUser | null {
  const raw = localStorage.getItem(USER_KEY)
  if (!raw) return null
  try {
    return JSON.parse(raw) as AuthUser
  } catch {
    return null
  }
}

export function saveAuth(auth: AuthUser): void {
  localStorage.setItem(TOKEN_KEY, auth.token)
  localStorage.setItem(USER_KEY, JSON.stringify(auth))
}

export function clearAuth(): void {
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem(USER_KEY)
}

export const api = axios.create({
  baseURL: '/api',
  headers: { 'Content-Type': 'application/json' },
})

api.interceptors.request.use((config) => {
  const token = getToken()
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

// El JWT caduca mientras la sesion sigue "viva" en localStorage. Sin esto, cada
// peticion devuelve 401 pero la interfaz continua mostrando al usuario como
// autenticado. El handler lo registra main.ts, que es donde vive el router.
type UnauthorizedHandler = () => void
let unauthorizedHandler: UnauthorizedHandler | null = null

export function onUnauthorized(handler: UnauthorizedHandler): void {
  unauthorizedHandler = handler
}

api.interceptors.response.use(
  (response) => response,
  (error: unknown) => {
    // Solo importa si ya teniamos token: un 401 sin sesion es un login
    // fallido y no debe cerrar nada.
    if (axios.isAxiosError(error) && error.response?.status === 401 && getToken()) {
      unauthorizedHandler?.()
    }
    return Promise.reject(error)
  },
)

/** Desenvuelve el sobre ApiResponse y devuelve solo `data`. */
async function unwrap<T>(request: Promise<AxiosResponse<ApiResponse<T>>>): Promise<T> {
  const res = await request
  return res.data.data
}

function get<T>(url: string, config?: AxiosRequestConfig): Promise<T> {
  return unwrap(api.get<ApiResponse<T>>(url, config))
}

function post<T>(url: string, body?: unknown): Promise<T> {
  return unwrap(api.post<ApiResponse<T>>(url, body))
}

function put<T>(url: string, body?: unknown): Promise<T> {
  return unwrap(api.put<ApiResponse<T>>(url, body))
}

function del<T>(url: string): Promise<T> {
  return unwrap(api.delete<ApiResponse<T>>(url))
}

/** Extrae el mensaje de error del backend con un texto por defecto. */
export function errorMessage(err: unknown, fallback: string): string {
  if (axios.isAxiosError(err)) {
    const body = err.response?.data as { message?: string } | undefined
    if (body?.message) return body.message
  }
  return fallback
}

/* ---------- Auth ---------- */

export function login(username: string, password: string): Promise<AuthUser> {
  return post<AuthUser>('/users/login', { username, password })
}

export interface RegisterPayload {
  username: string
  email: string
  password: string
  fullName?: string
  phone?: string
  newsletter?: boolean
}

export function register(payload: RegisterPayload): Promise<void> {
  return post<void>('/users/register', payload)
}

export function verifyEmail(token: string): Promise<string> {
  return get<string>('/users/verify', { params: { token } })
}

export function resendVerification(email: string): Promise<void> {
  return post<void>('/users/resend-verification', { email })
}

export function fetchProfile(): Promise<UserProfile> {
  return get<UserProfile>('/users/me')
}

export function fetchUsers(): Promise<UserProfile[]> {
  return get<UserProfile[]>('/users')
}

/* ---------- Productos ---------- */

export function fetchProducts(): Promise<Product[]> {
  return get<Product[]>('/products')
}

export function createProduct(payload: ProductPayload): Promise<Product> {
  return post<Product>('/products', payload)
}

export function updateProduct(id: number, payload: ProductPayload): Promise<Product> {
  return put<Product>(`/products/${id}`, payload)
}

export function deleteProduct(id: number): Promise<void> {
  return del<void>(`/products/${id}`)
}

/* ---------- Chat ---------- */

/**
 * Pide un ticket de un solo uso. Va con el Bearer de siempre, asi que lo envia
 * axios sin problema: el truco es que despues el WebSocket se abre sin cabeceras,
 * solo con el ticket en la URL.
 */
export function requestWsTicket(): Promise<WsTicket> {
  return post<WsTicket>('/chat/ws-ticket')
}
