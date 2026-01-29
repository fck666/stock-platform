import axios from 'axios'

const apiBaseUrl = import.meta.env.VITE_API_BASE_URL || ''

export const http = axios.create({
  baseURL: apiBaseUrl,
  timeout: 60_000,
})

function getOrCreateProfileKey() {
  if (typeof window === 'undefined') return null
  try {
    const k = window.localStorage.getItem('stock_platform_profile_key')
    if (k && k.trim()) return k.trim()
    const next =
      (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function')
        ? crypto.randomUUID()
        : `p_${Math.random().toString(36).slice(2)}_${Date.now()}`
    window.localStorage.setItem('stock_platform_profile_key', next)
    return next
  } catch {
    return null
  }
}

http.interceptors.request.use((config) => {
  const key = getOrCreateProfileKey()
  if (key) {
    config.headers = config.headers || {}
    ;(config.headers as any)['X-Profile-Key'] = key
  }
  return config
})
