import { computed, ref } from 'vue'
import { login as apiLogin, logout as apiLogout, me as apiMe, refresh as apiRefresh, type MeDto } from '../api/auth'
import { clearTokens, getRefreshToken, setTokens } from './token'

const meRef = ref<MeDto | null>(null)
const readyRef = ref(false)

export const auth = {
  me: computed(() => meRef.value),
  ready: computed(() => readyRef.value),
  isLoggedIn: computed(() => !!meRef.value),
  username: computed(() => meRef.value?.username || ''),
  permissions: computed(() => meRef.value?.permissions || []),
  roles: computed(() => meRef.value?.roles || []),
  hasPermission(code: string) {
    return !!meRef.value?.permissions?.includes(code)
  },
  async init() {
    const refreshToken = getRefreshToken()
    if (!refreshToken) {
      readyRef.value = true
      return
    }
    try {
      const tokens = await apiRefresh(refreshToken)
      setTokens(tokens.accessToken, tokens.refreshToken)
      meRef.value = await apiMe()
    } catch {
      clearTokens()
      meRef.value = null
    } finally {
      readyRef.value = true
    }
  },
  async login(username: string, password: string) {
    const tokens = await apiLogin(username, password)
    setTokens(tokens.accessToken, tokens.refreshToken)
    meRef.value = await apiMe()
    return meRef.value
  },
  async logout() {
    const refreshToken = getRefreshToken()
    try {
      if (refreshToken) await apiLogout(refreshToken)
    } catch {
    } finally {
      clearTokens()
      meRef.value = null
    }
  },
  async reloadMe() {
    meRef.value = await apiMe()
    return meRef.value
  },
}

