// Tipos compartidos con la API. Cada interfaz refleja un record de Java:
//   ApiResponse      -> com.ecommerce.common.api.ApiResponse
//   Product          -> com.ecommerce.product.controller.ProductResponse
//   ProductPayload   -> com.ecommerce.product.controller.ProductRequest
//   UserProfile      -> com.ecommerce.user.controller.UserResponse

export interface ApiResponse<T> {
  success: boolean
  message: string
  data: T
  timestamp: string
}

export interface Product {
  id: number
  name: string
  sku: string
  description: string | null
  price: number
  active: boolean
  createdAt: string | null
  updatedAt: string | null
}

export interface ProductPayload {
  name: string
  sku: string
  description?: string
  price: number
}

export interface UserProfile {
  id: number
  username: string
  email: string
  role: string
  enabled: boolean
  createdAt: string | null
}

/** Ticket opaco para el handshake del WebSocket. Un solo uso y con caducidad. */
export interface WsTicket {
  ticket: string
  expiresInSeconds: number
}
