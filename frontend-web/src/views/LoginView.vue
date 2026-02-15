<script setup lang="ts">
/**
 * LoginView
 * 
 * Simple login form using username/password.
 * Interacts with the `auth` module to obtain and store JWT tokens.
 * Redirects to the previous page (query.next) or home upon success.
 */
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import PageHeader from '../components/PageHeader.vue'
import { auth } from '../auth/auth'

const router = useRouter()
const route = useRoute()

const username = ref('')
const password = ref('')
const loading = ref(false)

/**
 * Submits credentials to the backend.
 * On success: shows message, redirects to `next` or home.
 * On failure: displays error toast.
 */
async function handleLogin() {
  loading.value = true
  try {
    await auth.login(username.value.trim(), password.value)
    ElMessage.success('登录成功')
    const next = (route.query.next as string) || '/'
    router.replace(next)
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message ?? e?.message ?? '登录失败')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <el-space direction="vertical" class="page" :size="16" fill>
    <PageHeader title="登录" subtitle="使用账号密码登录以访问管理功能" />

    <el-card shadow="never" style="border-radius: 12px; max-width: 520px">
      <el-form label-position="top" @submit.prevent="handleLogin">
        <el-form-item label="账号">
          <el-input v-model="username" autocomplete="username" />
        </el-form-item>
        <el-form-item label="密码">
          <el-input v-model="password" type="password" show-password autocomplete="current-password" />
        </el-form-item>
        <el-space>
          <el-button type="primary" :loading="loading" @click="handleLogin">登录</el-button>
          <el-button @click="router.push('/')">返回首页</el-button>
        </el-space>
      </el-form>
    </el-card>

    <el-alert
      v-if="auth.isLoggedIn"
      title="当前已登录"
      type="success"
      :closable="false"
      show-icon
    >
      <template #default>
        账号：{{ auth.username }}
      </template>
    </el-alert>
  </el-space>
</template>
