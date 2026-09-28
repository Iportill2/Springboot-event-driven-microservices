import axios from 'axios'

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

export async function login(username: string, password: string): Promise<AuthUser> {
  const res = await api.post('/users/login', { username, password })
  const data = res.data?.data as AuthUser
  saveAuth(data)
  return data
}

export interface RegisterPayload {
  username: string
  email: string
  password: string
  fullName?: string
  phone?: string
  newsletter?: boolean
}

export async function register(payload: RegisterPayload): Promise<void> {
  await api.post('/users/register', payload)
}

export async function verifyEmail(token: string): Promise<string> {
  const res = await api.get('/users/verify', { params: { token } })
  return res.data?.data as string
}

export async function resendVerification(email: string): Promise<void> {
  await api.post('/users/resend-verification', { email })
}