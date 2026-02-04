const ACCESS_KEY = 'stock_platform_access_token'
const REFRESH_KEY = 'stock_platform_refresh_token'

export function getAccessToken() {
  if (typeof window === 'undefined') return null
  try {
    const v = window.localStorage.getItem(ACCESS_KEY)
    return v && v.trim() ? v.trim() : null
  } catch {
    return null
  }
}

export function getRefreshToken() {
  if (typeof window === 'undefined') return null
  try {
    const v = window.localStorage.getItem(REFRESH_KEY)
    return v && v.trim() ? v.trim() : null
  } catch {
    return null
  }
}

export function setTokens(accessToken: string, refreshToken: string) {
  if (typeof window === 'undefined') return
  try {
    window.localStorage.setItem(ACCESS_KEY, accessToken)
    window.localStorage.setItem(REFRESH_KEY, refreshToken)
  } catch {
  }
}

export function clearTokens() {
  if (typeof window === 'undefined') return
  try {
    window.localStorage.removeItem(ACCESS_KEY)
    window.localStorage.removeItem(REFRESH_KEY)
  } catch {
  }
}
