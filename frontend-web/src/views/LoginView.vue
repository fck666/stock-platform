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
  <div class="login-page">
    <div class="login-shell">
      <div class="login-brand">Stock Platform</div>
      <div class="login-subtitle">登录后可使用选股器、交易计划、分析等功能</div>

      <el-card shadow="never" class="login-card">
      <el-form label-position="top" @submit.prevent="handleLogin">
        <el-form-item label="账号">
          <el-input v-model="username" autocomplete="username" />
        </el-form-item>
        <el-form-item label="密码">
          <el-input v-model="password" type="password" show-password autocomplete="current-password" />
        </el-form-item>
        <el-space wrap>
          <el-button type="primary" :loading="loading" @click="handleLogin">登录</el-button>
          <el-button @click="router.push('/')">返回首页</el-button>
        </el-space>
      </el-form>
      </el-card>

      <el-alert
        v-if="auth.ready.value && auth.isLoggedIn.value"
        title="当前已登录"
        type="success"
        :closable="false"
        show-icon
        class="login-alert"
      >
        <template #default>账号：{{ auth.username.value }}</template>
      </el-alert>
    </div>
  </div>
</template>

<style scoped>
.login-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 32px 16px;
  background:
    radial-gradient(900px 520px at 72% 24%, rgba(91, 143, 249, 0.18), transparent 60%),
    radial-gradient(980px 620px at 28% 78%, rgba(38, 166, 154, 0.12), transparent 58%),
    url('/stock-platform-background.jpg');
  background-size: auto, auto, cover;
  background-position: center, center, center;
  background-repeat: no-repeat;
}

.login-shell {
  width: 100%;
  max-width: 420px;
}

.login-brand {
  font-weight: 900;
  font-size: 22px;
  letter-spacing: 0.3px;
  color: rgba(255, 255, 255, 0.92);
}

.login-subtitle {
  margin-top: 6px;
  margin-bottom: 14px;
  font-size: 13px;
  color: rgba(255, 255, 255, 0.72);
}

.login-card {
  width: 100%;
  backdrop-filter: blur(10px);
  background: rgba(255, 255, 255, 0.86);
}

:global(html.dark) .login-card {
  background: rgba(15, 23, 42, 0.72);
}

.login-alert {
  margin-top: 12px;
}
</style>
