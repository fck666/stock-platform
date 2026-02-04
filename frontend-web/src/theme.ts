export type ThemeMode = 'light' | 'dark'

const STORAGE_KEY = 'stock_platform_theme_mode'

function getSystemTheme(): ThemeMode {
  if (typeof window === 'undefined') return 'light'
  return window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light'
}

export function getStoredTheme(): ThemeMode | null {
  try {
    const v = localStorage.getItem(STORAGE_KEY)
    if (v === 'light' || v === 'dark') return v
    return null
  } catch {
    return null
  }
}

export function getInitialTheme(): ThemeMode {
  return getStoredTheme() ?? getSystemTheme()
}

export function applyTheme(mode: ThemeMode) {
  const root = document.documentElement
  root.classList.toggle('dark', mode === 'dark')
  root.style.colorScheme = mode
  try {
    localStorage.setItem(STORAGE_KEY, mode)
  } catch {}
  try {
    window.dispatchEvent(new CustomEvent('themechange', { detail: { mode } }))
  } catch {}
}

export function initTheme() {
  if (typeof document === 'undefined') return
  applyTheme(getInitialTheme())
}

export function toggleTheme(): ThemeMode {
  const next: ThemeMode = document.documentElement.classList.contains('dark') ? 'light' : 'dark'
  applyTheme(next)
  return next
}
