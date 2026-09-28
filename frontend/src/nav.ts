// El menu vive aqui como dato, no como markup, para poder anadir, quitar o
// reordenar entradas sin tocar el componente del layout.
//
// Regla: un item con `to` debe tener un endpoint detras. Los items con
// `soon: true` son entradas de roadmap y se muestran apagadas.

export interface NavItem {
  label: string
  /** Ruta destino. Ausente en los items de roadmap. */
  to?: string
  /** Entrada planificada: visible pero no navegable. */
  soon?: boolean
  /** Solo visible con sesion iniciada. */
  auth?: boolean
  /** Solo visible si el usuario tiene alguno de estos roles. */
  roles?: string[]
}

export const NAV_ITEMS: NavItem[] = [
  { label: 'Catálogo', to: '/' },
  { label: 'Mi perfil', to: '/profile', auth: true },
  { label: 'Chat', to: '/chat', auth: true },
  { label: 'Pedidos', soon: true, auth: true },
  { label: 'Carrito', soon: true, auth: true },
  { label: 'Asistente IA', soon: true, auth: true },
  { label: 'Productos', to: '/admin/products', auth: true, roles: ['ADMIN'] },
  { label: 'Usuarios', to: '/admin/users', auth: true, roles: ['ADMIN'] },
]

export interface NavContext {
  authenticated: boolean
  roles: string[]
}

export function visibleItems({ authenticated, roles }: NavContext): NavItem[] {
  return NAV_ITEMS.filter((item) => {
    if (item.auth && !authenticated) return false
    if (item.roles && !item.roles.some((role) => roles.includes(role))) return false
    return true
  })
}
