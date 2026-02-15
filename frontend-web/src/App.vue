<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowDown } from '@element-plus/icons-vue'
import { getInitialTheme, toggleTheme, type ThemeMode } from './theme'
import { auth } from './auth/auth'

const route = useRoute()
const router = useRouter()
const isLoginRoute = computed(() => route.path === '/login')

const theme = ref<ThemeMode>(getInitialTheme())

const active = computed(() => {
  if (route.path.startsWith('/market')) return '/market'
  if (route.path.startsWith('/stocks')) return '/stocks'
  if (route.path.startsWith('/indices')) return '/indices'
  if (route.path.startsWith('/plans')) return '/plans'
  if (route.path.startsWith('/alerts')) return '/alerts'
  if (route.path.startsWith('/analysis')) return '/analysis'
  if (route.path.startsWith('/sync')) return '/sync'
  if (route.path.startsWith('/admin/users')) return '/admin/users'
  return '/'
})

function handleSelect(index: string) {
  router.push(index)
}

function handleToggleTheme() {
  theme.value = toggleTheme()
}

async function handleLogout() {
  await auth.logout()
  if (route.path.startsWith('/sync')) {
    router.push('/')
  }
}
</script>

<template>
  <el-container style="min-height: 100vh">
    <el-header v-if="!isLoginRoute" class="app-header">
      <div class="app-brand">Stock Platform</div>
      <el-menu mode="horizontal" :default-active="active" @select="handleSelect" class="app-menu" ellipsis>
        <el-menu-item index="/">首页</el-menu-item>
        <el-menu-item index="/market">市场</el-menu-item>
        <el-menu-item index="/stocks">股票列表</el-menu-item>
        <el-menu-item v-if="auth.hasPermission('admin.index.write')" index="/indices">指数管理</el-menu-item>
        <el-menu-item v-if="auth.isLoggedIn.value" index="/plans">交易计划</el-menu-item>
          <el-menu-item index="/alerts">告警</el-menu-item>
          <el-menu-item v-if="auth.isLoggedIn.value" index="/analysis">股票分析</el-menu-item>
          <el-menu-item v-if="auth.hasPermission('data.sync.execute')" index="/sync">数据同步</el-menu-item>
          <el-menu-item v-if="auth.hasPermission('iam.manage')" index="/admin/users">用户管理</el-menu-item>
        </el-menu>
        <div style="display: flex; align-items: center; gap: 8px">
          <div v-if="auth.isLoggedIn.value">
            <el-dropdown>
              <span class="el-dropdown-link" style="cursor: pointer; display: flex; align-items: center; gap: 4px">
                {{ auth.username.value }}
                <el-icon class="el-icon--right"><arrow-down /></el-icon>
              </span>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item @click="router.push('/profile')">个人设置</el-dropdown-item>
                  <el-dropdown-item divided @click="handleLogout">退出登录</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </div>
          <el-button v-else size="small" @click="router.push('/login')">登录</el-button>
          
          <el-button class="theme-toggle" size="small" @click="handleToggleTheme">
          {{ theme === 'dark' ? '亮色' : '暗色' }}
        </el-button>
      </div>
    </el-header>
    <el-main :class="['app-main', isLoginRoute ? 'app-main--bare' : '']">
      <router-view />
    </el-main>
  </el-container>
</template>
