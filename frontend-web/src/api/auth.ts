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

export type AdminUserDto = {
  userId: string
  username: string
  status: string
  roles: string[]
}

export type RoleDto = {
  code: string
  name: string
  permissions: string[]
}

export type AuditLogDto = {
  id: string
  actorId: string | null
  actorUsername: string | null
  targetId: string | null
  targetUsername: string | null
  action: string
  details: string | null
  ipAddress: string | null
  userAgent: string | null
  createdAt: string
}

export type CreateUserRequestDto = {
  username: string
  password?: string
  roles?: string[]
}

export type UpdateUserRolesRequestDto = {
  roles: string[]
}

export type UpdateUserStatusRequestDto = {
  status: string
}

export type ResetPasswordRequestDto = {
  adminPassword?: string
  newPassword?: string
}

export type ChangePasswordRequestDto = {
  oldPassword?: string
  newPassword?: string
}

// Admin APIs

export async function listUsers() {
  const res = await http.get<AdminUserDto[]>('/api/admin/iam/users')
  return res.data
}

export async function createUser(data: CreateUserRequestDto) {
  await http.post('/api/admin/iam/users', data)
}

export async function updateUserRoles(userId: string, roles: string[]) {
  await http.put(`/api/admin/iam/users/${userId}/roles`, { roles })
}

export async function updateUserStatus(userId: string, status: string) {
  await http.put(`/api/admin/iam/users/${userId}/status`, { status })
}

export async function resetPassword(userId: string, data: ResetPasswordRequestDto) {
  await http.post(`/api/admin/iam/users/${userId}/reset-password`, data)
}

export async function deleteUser(userId: string) {
  await http.delete(`/api/admin/iam/users/${userId}`)
}

export async function listAuditLogs(limit = 100) {
  const res = await http.get<AuditLogDto[]>('/api/admin/iam/audit-logs', { params: { limit } })
  return res.data
}

export async function listRoles() {
  const res = await http.get<RoleDto[]>('/api/admin/iam/roles')
  return res.data
}

// Profile APIs

export async function changePassword(data: ChangePasswordRequestDto) {
  await http.post('/api/me/password', data)
}
