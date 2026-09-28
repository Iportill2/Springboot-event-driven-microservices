export interface BrandColors {
  primary: string
  secondary: string
  accent: string
}

export interface Brand {
  name: string
  colors: BrandColors
  colorsDark: BrandColors
  faviconUrl: string
  logoUrl: string
}

export const brand: Brand = {
  name: import.meta.env.VITE_APP_NAME || 'E-Commerce',
  colors: {
    primary: import.meta.env.VITE_PRIMARY_COLOR || '#6366f1',
    secondary: import.meta.env.VITE_SECONDARY_COLOR || '#a855f7',
    accent: import.meta.env.VITE_ACCENT_COLOR || '#ec4899',
  },
  colorsDark: {
    primary: import.meta.env.VITE_PRIMARY_COLOR_DARK || '#818cf8',
    secondary: import.meta.env.VITE_SECONDARY_COLOR_DARK || '#c084fc',
    accent: import.meta.env.VITE_ACCENT_COLOR_DARK || '#f472b6',
  },
  faviconUrl: import.meta.env.VITE_FAVICON_URL || '/favicon.svg',
  logoUrl: import.meta.env.VITE_LOGO_URL || '',
}

export function brandInitials(name: string): string {
  const words = name.trim().split(/\s+/).filter(Boolean)
  if (words.length === 0) return 'E'
  if (words.length === 1) return words[0][0].toUpperCase()
  return (words[0][0] + words[1][0]).toUpperCase()
}

export function applyBrand() {
  const root = document.documentElement
  const { colors, colorsDark } = brand
  root.style.setProperty('--brand-a', colors.primary)
  root.style.setProperty('--brand-b', colors.secondary)
  root.style.setProperty('--brand-c', colors.accent)
  root.style.setProperty('--brand-a-dark', colorsDark.primary)
  root.style.setProperty('--brand-b-dark', colorsDark.secondary)
  root.style.setProperty('--brand-c-dark', colorsDark.accent)
  document.title = brand.name
}