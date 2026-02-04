import { http } from './client'

export type TokenResponseDto = {
  accessToken: string
  refreshToken: string
}

export type MeDto = {
  userId: string
  username: string
  roles: string[]
  permissions: string[]
}

export async function login(username: string, password: string) {
  const res = await http.post<TokenResponseDto>('/api/auth/login', { username, password })
  return res.data
}

export async function refresh(refreshToken: string) {
  const res = await http.post<TokenResponseDto>('/api/auth/refresh', { refreshToken })
  return res.data
}

export async function logout(refreshToken: string) {
  await http.post('/api/auth/logout', { refreshToken })
}

export async function me() {
  const res = await http.get<MeDto>('/api/me')
  return res.data
}
