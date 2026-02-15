<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageHeader from '../../components/PageHeader.vue'
import { 
  listUsers, createUser, updateUserRoles, updateUserStatus, resetPassword, deleteUser, listAuditLogs, listRoles,
  type AdminUserDto, type AuditLogDto, type RoleDto
} from '../../api/auth'
import { auth } from '../../auth/auth'

const activeTab = ref('users')
const users = ref<AdminUserDto[]>([])
const roles = ref<RoleDto[]>([])
const auditLogs = ref<AuditLogDto[]>([])
const loading = ref(false)
const auditLoading = ref(false)

// Dialog states
const createDialogVisible = ref(false)
const roleDialogVisible = ref(false)
const passwordDialogVisible = ref(false)

const createForm = ref({ username: '', password: '', roles: [] as string[] })
const selectedUser = ref<AdminUserDto | null>(null)
const selectedRoles = ref<string[]>([])
const newPassword = ref('')
const adminPassword = ref('')

const isSuperAdmin = computed(() => auth.roles.value.includes('super_admin'))

// Filter roles: Non-super-admins cannot see/assign 'super_admin' or 'admin' roles
const availableRoles = computed(() => {
  if (isSuperAdmin.value) return roles.value
  return roles.value.filter(r => r.code !== 'super_admin' && r.code !== 'admin')
})

async function loadData() {
  loading.value = true
  try {
    const [u, r] = await Promise.all([listUsers(), listRoles()])
    // Filter users: If not super_admin, maybe we should filter out super_admins?
    // The backend prevents managing them, but viewing might be allowed.
    // Let's rely on backend check for modification, but maybe visual hint?
    users.value = u
    roles.value = r
  } catch (e: any) {
    ElMessage.error(e.message || '加载失败')
  } finally {
    loading.value = false
  }
}

async function loadAudits() {
  auditLoading.value = true
  try {
    auditLogs.value = await listAuditLogs(100)
  } catch (e: any) {
    ElMessage.error(e.message || '加载审计日志失败')
  } finally {
    auditLoading.value = false
  }
}

function handleTabClick(tab: any) {
  if (tab?.paneName === 'audits') loadAudits()
}

async function handleCreate() {
  if (!createForm.value.username || !createForm.value.password) {
    ElMessage.warning('请填写用户名和密码')
    return
  }
  try {
    await createUser(createForm.value)
    ElMessage.success('创建成功')
    createDialogVisible.value = false
    createForm.value = { username: '', password: '', roles: [] }
    loadData()
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || e.message || '创建失败')
  }
}

function openRoleDialog(user: AdminUserDto) {
  selectedUser.value = user
  selectedRoles.value = [...user.roles]
  roleDialogVisible.value = true
}

async function handleUpdateRoles() {
  if (!selectedUser.value) return
  try {
    await updateUserRoles(selectedUser.value.userId, selectedRoles.value)
    ElMessage.success('角色更新成功')
    roleDialogVisible.value = false
    loadData()
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || e.message || '更新失败')
  }
}

async function handleToggleStatus(user: AdminUserDto) {
  const newStatus = user.status === 'active' ? 'disabled' : 'active'
  const action = newStatus === 'active' ? '启用' : '禁用'
  try {
    await ElMessageBox.confirm(`确定要${action}用户 ${user.username} 吗？`, '提示', {
      type: 'warning'
    })
    await updateUserStatus(user.userId, newStatus)
    ElMessage.success(`${action}成功`)
    loadData()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error('操作失败')
  }
}

function openPasswordDialog(user: AdminUserDto) {
  selectedUser.value = user
  newPassword.value = ''
  adminPassword.value = ''
  passwordDialogVisible.value = true
}

async function handleResetPassword() {
  if (!selectedUser.value || !adminPassword.value || !newPassword.value) {
    ElMessage.warning('请填写管理员密码和新密码')
    return
  }
  try {
    await resetPassword(selectedUser.value.userId, {
      adminPassword: adminPassword.value,
      newPassword: newPassword.value,
    })
    ElMessage.success('密码重置成功')
    passwordDialogVisible.value = false
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || e.message || '重置失败')
  }
}

async function handleDelete(user: AdminUserDto) {
  try {
    await ElMessageBox.confirm(`确定要删除用户 ${user.username} 吗？此操作不可恢复！`, '危险', {
      type: 'error',
      confirmButtonText: '确认删除',
      cancelButtonText: '取消'
    })
    await deleteUser(user.userId)
    ElMessage.success('删除成功')
    loadData()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error('操作失败')
  }
}

function roleName(code: string) {
  const r = roles.value.find(x => x.code === code)
  return r ? r.name : code
}

onMounted(() => {
  loadData()
  if (isSuperAdmin.value) {
    loadAudits()
  }
})
</script>

<template>
  <el-space direction="vertical" class="page" :size="16" fill>
    <PageHeader title="用户管理" subtitle="管理系统用户、角色与权限" />

    <el-tabs v-model="activeTab" type="card" @tab-click="handleTabClick">
      <el-tab-pane label="用户列表" name="users">
        <el-card shadow="never">
          <div style="margin-bottom: 16px">
            <el-button type="primary" @click="createDialogVisible = true">新增用户</el-button>
            <el-button @click="loadData">刷新</el-button>
          </div>
          <el-table :data="users" v-loading="loading" border style="width: 100%">
            <el-table-column prop="username" label="用户名" width="180" />
            <el-table-column label="角色" min-width="200">
              <template #default="{ row }">
                <el-tag v-for="r in row.roles" :key="r" style="margin-right: 4px" size="small">
                  {{ roleName(r) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="status" label="状态" width="100">
              <template #default="{ row }">
                <el-tag :type="row.status === 'active' ? 'success' : 'danger'">
                  {{ row.status === 'active' ? '正常' : '禁用' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="320" fixed="right">
              <template #default="{ row }">
                <el-button size="small" @click="openRoleDialog(row)">角色</el-button>
                <el-button size="small" type="warning" @click="openPasswordDialog(row)">重置密码</el-button>
                <el-button 
                  size="small" 
                  :type="row.status === 'active' ? 'danger' : 'success'" 
                  @click="handleToggleStatus(row)"
                >
                  {{ row.status === 'active' ? '禁用' : '启用' }}
                </el-button>
                <el-button size="small" type="danger" plain @click="handleDelete(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-tab-pane>

      <el-tab-pane label="审计日志" name="audits" v-if="isSuperAdmin">
        <el-card shadow="never">
          <div style="margin-bottom: 16px">
            <el-button @click="loadAudits">刷新</el-button>
          </div>
          <el-table :data="auditLogs" v-loading="auditLoading" border style="width: 100%" height="600">
            <el-table-column prop="createdAt" label="时间" width="180">
              <template #default="{ row }">
                {{ new Date(row.createdAt).toLocaleString() }}
              </template>
            </el-table-column>
            <el-table-column prop="actorUsername" label="操作人" width="150" />
            <el-table-column prop="action" label="动作" width="150" />
            <el-table-column prop="targetUsername" label="目标对象" width="150" />
            <el-table-column prop="details" label="详情" show-overflow-tooltip />
            <el-table-column prop="ipAddress" label="IP" width="140" />
          </el-table>
        </el-card>
      </el-tab-pane>
    </el-tabs>

    <!-- Create User Dialog -->
    <el-dialog v-model="createDialogVisible" title="新增用户" width="500px">
      <el-form :model="createForm" label-width="80px">
        <el-form-item label="用户名">
          <el-input v-model="createForm.username" />
        </el-form-item>
        <el-form-item label="密码">
          <el-input v-model="createForm.password" type="password" show-password />
        </el-form-item>
        <el-form-item label="角色">
          <el-select v-model="createForm.roles" multiple placeholder="选择角色">
            <el-option v-for="r in availableRoles" :key="r.code" :label="r.name" :value="r.code" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="createDialogVisible = false">取消</el-button>
          <el-button type="primary" @click="handleCreate">确定</el-button>
        </span>
      </template>
    </el-dialog>

    <!-- Role Dialog -->
    <el-dialog v-model="roleDialogVisible" title="分配角色" width="500px">
      <div style="margin-bottom: 16px">用户：{{ selectedUser?.username }}</div>
      <el-checkbox-group v-model="selectedRoles">
        <el-checkbox v-for="r in availableRoles" :key="r.code" :value="r.code">{{ r.name }}</el-checkbox>
      </el-checkbox-group>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="roleDialogVisible = false">取消</el-button>
          <el-button type="primary" @click="handleUpdateRoles">保存</el-button>
        </span>
      </template>
    </el-dialog>

    <!-- Password Dialog -->
    <el-dialog v-model="passwordDialogVisible" title="重置密码" width="420px">
      <el-form label-position="top">
        <el-form-item label="用户">
          <el-input :model-value="selectedUser?.username || ''" disabled />
        </el-form-item>
        <el-form-item label="管理员密码">
          <el-input v-model="adminPassword" type="password" show-password />
        </el-form-item>
        <el-form-item label="新密码">
          <el-input v-model="newPassword" type="password" show-password />
        </el-form-item>
      </el-form>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="passwordDialogVisible = false">取消</el-button>
          <el-button type="primary" @click="handleResetPassword">确定</el-button>
        </span>
      </template>
    </el-dialog>
  </el-space>
</template>
