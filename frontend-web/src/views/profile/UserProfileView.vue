<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import PageHeader from '../../components/PageHeader.vue'
import { changePassword } from '../../api/auth'
import { auth } from '../../auth/auth'

const form = ref({
  oldPassword: '',
  newPassword: '',
  confirmPassword: ''
})
const loading = ref(false)

async function handleSubmit() {
  if (!form.value.oldPassword || !form.value.newPassword) {
    ElMessage.warning('请填写完整')
    return
  }
  if (form.value.newPassword !== form.value.confirmPassword) {
    ElMessage.warning('两次输入密码不一致')
    return
  }
  
  loading.value = true
  try {
    await changePassword({
      oldPassword: form.value.oldPassword,
      newPassword: form.value.newPassword
    })
    ElMessage.success('密码修改成功，请重新登录')
    await auth.logout()
    window.location.reload()
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || e.message || '修改失败')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <el-space direction="vertical" class="page" :size="16" fill>
    <PageHeader title="个人设置" subtitle="管理您的账户信息" />

    <el-card shadow="never" style="max-width: 600px">
      <template #header>
        <div style="font-weight: 700">修改密码</div>
      </template>
      <el-form label-position="top" :model="form">
        <el-form-item label="当前密码">
          <el-input v-model="form.oldPassword" type="password" show-password />
        </el-form-item>
        <el-form-item label="新密码">
          <el-input v-model="form.newPassword" type="password" show-password />
        </el-form-item>
        <el-form-item label="确认新密码">
          <el-input v-model="form.confirmPassword" type="password" show-password />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="loading" @click="handleSubmit">确认修改</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never" style="max-width: 600px; margin-top: 16px">
      <template #header>
        <div style="font-weight: 700">当前会话</div>
      </template>
      <div style="font-size: 14px; color: #666">
        <p>当前登录账号：{{ auth.username.value }}</p>
        <p>角色：{{ auth.roles.value.join(', ') }}</p>
      </div>
    </el-card>
  </el-space>
</template>
