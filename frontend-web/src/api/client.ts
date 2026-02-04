import axios from 'axios'
import { clearTokens, getAccessToken, getRefreshToken, setTokens } from '../auth/token'

const apiBaseUrl = import.meta.env.VITE_API_BASE_URL || ''

export const http = axios.create({
  baseURL: apiBaseUrl,
  timeout: 60_000,
})

const raw = axios.create({
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
  const token = getAccessToken()
  if (token) {
    config.headers = config.headers || {}
    ;(config.headers as any)['Authorization'] = `Bearer ${token}`
  }
  return config
})

http.interceptors.response.use(
  (res) => res,
  async (error) => {
    const status = error?.response?.status
    const original = error?.config
    if (status === 401 && original && !original.__retried) {
      const refreshToken = getRefreshToken()
      if (refreshToken) {
        original.__retried = true
        try {
          const r = await raw.post('/api/auth/refresh', { refreshToken })
          const accessToken = r.data?.accessToken
          const nextRefresh = r.data?.refreshToken
          if (accessToken && nextRefresh) {
            setTokens(accessToken, nextRefresh)
            original.headers = original.headers || {}
            original.headers['Authorization'] = `Bearer ${accessToken}`
            return http.request(original)
          }
        } catch {
          clearTokens()
        }
      }
    }
    return Promise.reject(error)
  },
)
