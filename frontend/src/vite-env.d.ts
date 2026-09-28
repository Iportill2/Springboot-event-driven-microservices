/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_APP_NAME?: string
  readonly VITE_PRIMARY_COLOR?: string
  readonly VITE_SECONDARY_COLOR?: string
  readonly VITE_ACCENT_COLOR?: string
  readonly VITE_PRIMARY_COLOR_DARK?: string
  readonly VITE_SECONDARY_COLOR_DARK?: string
  readonly VITE_ACCENT_COLOR_DARK?: string
  readonly VITE_FAVICON_URL?: string
  readonly VITE_LOGO_URL?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}