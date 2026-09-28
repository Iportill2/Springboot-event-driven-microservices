import { ref } from 'vue'

export type Theme = 'light' | 'dark'

const THEME_KEY = 'ed_theme'

function systemTheme(): Theme {
  return window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light'
}

function storedTheme(): Theme | null {
  const value = localStorage.getItem(THEME_KEY)
  return value === 'light' || value === 'dark' ? value : null
}

function apply(theme: Theme): void {
  const root = document.documentElement
  root.dataset.theme = theme
  root.style.colorScheme = theme
}

const theme = ref<Theme>(storedTheme() ?? systemTheme())

apply(theme.value)

export function useTheme() {
  function set(next: Theme): void {
    theme.value = next
    localStorage.setItem(THEME_KEY, next)
    apply(next)
  }

  function toggle(): void {
    set(theme.value === 'dark' ? 'light' : 'dark')
  }

  return { theme, set, toggle }
}